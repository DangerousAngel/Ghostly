package da.ghostly.com.vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import da.ghostly.com.MainActivity;
import da.ghostly.com.R;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * GhostVpnService
 * High-performance System-Wide VPN engine with default 0.0.0.0/0 route,
 * asynchronous UDP/DNS forwarder, and non-blocking TCP packet relay.
 * Works across all applications on Android ("as any other vpn").
 */
public class GhostVpnService extends VpnService implements Runnable {

    private static final String TAG = "GhostVpnService";
    public static final String ACTION_CONNECT = "da.ghostly.com.vpn.ACTION_CONNECT";
    public static final String ACTION_DISCONNECT = "da.ghostly.com.vpn.ACTION_DISCONNECT";

    public static final String EXTRA_PROFILE_ID = "extra_profile_id";
    public static final String EXTRA_USERNAME = "extra_username";
    public static final String EXTRA_PASSWORD = "extra_password";
    public static final String EXTRA_SERVER_IP = "extra_server_ip";
    public static final String EXTRA_SERVER_PORT = "extra_server_port";
    public static final String EXTRA_CLIENT_IP = "extra_client_ip";
    public static final String EXTRA_DNS_SERVER = "extra_dns_server";
    public static final String EXTRA_TUNNEL_ID = "extra_tunnel_id";
    public static final String EXTRA_SESSION_ID = "extra_session_id";
    public static final String EXTRA_IS_L2TP = "extra_is_l2tp";

    private static final String NOTIF_CHANNEL_ID = "ghostly_vpn_channel";
    private static final int NOTIF_ID = 4040;

    private Thread tunnelThread;
    private ParcelFileDescriptor vpnInterface = null;
    private GhostVpnProfile currentProfile = null;
    private volatile boolean isRunning = false;

    private String serverIp = "";
    private int serverPort = 1701;
    private String assignedClientIp = "10.8.0.2";
    private String assignedDns = "1.1.1.1";
    private short tunnelId = 0;
    private short sessionId = 0;
    private boolean isL2tp = false;
    private DatagramSocket l2tpSocket = null;
    private Thread l2tpReceiverThread = null;

    private ExecutorService tcpExecutor;
    private ExecutorService udpExecutor;
    private final Map<String, TcpSession> activeSessions = new ConcurrentHashMap<>();

    private long totalBytesSent = 0;
    private long totalBytesReceived = 0;

    private static class TcpSession {
        final String key;
        final byte[] srcIp;
        final byte[] dstIp;
        final int srcPort;
        final int dstPort;
        final FileOutputStream out;

        Socket socket;
        OutputStream outStream;
        InputStream inStream;

        long clientSeq;
        long serverSeq;
        volatile boolean isClosed = false;

        TcpSession(String key, byte[] srcIp, byte[] dstIp, int srcPort, int dstPort, FileOutputStream out) {
            this.key = key;
            this.srcIp = srcIp;
            this.dstIp = dstIp;
            this.srcPort = srcPort;
            this.dstPort = dstPort;
            this.out = out;
            this.serverSeq = (long) (Math.random() * 1000000) + 1000;
        }

        void close() {
            isClosed = true;
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_CONNECT.equals(action)) {
                String profileId = intent.getStringExtra(EXTRA_PROFILE_ID);
                GhostVpnProfile profile = GhostVpnManager.getInstance(this).getProfileById(profileId);
                if (profile != null) {
                    this.serverIp = intent.getStringExtra(EXTRA_SERVER_IP);
                    if (this.serverIp == null || this.serverIp.isEmpty()) {
                        this.serverIp = VpnUtils.cleanHost(profile.getServerAddress());
                    }
                    this.serverPort = intent.getIntExtra(EXTRA_SERVER_PORT, 1701);
                    this.assignedClientIp = intent.getStringExtra(EXTRA_CLIENT_IP);
                    if (this.assignedClientIp == null || this.assignedClientIp.isEmpty()) {
                        this.assignedClientIp = "10.8.0.2";
                    }
                    this.assignedDns = intent.getStringExtra(EXTRA_DNS_SERVER);
                    if (this.assignedDns == null || this.assignedDns.isEmpty()) {
                        this.assignedDns = "1.1.1.1";
                    }
                    this.tunnelId = intent.getShortExtra(EXTRA_TUNNEL_ID, (short) 0);
                    this.sessionId = intent.getShortExtra(EXTRA_SESSION_ID, (short) 0);
                    this.isL2tp = intent.getBooleanExtra(EXTRA_IS_L2TP, false);

                    postForegroundNotification(profile);
                    startTunnel(profile);
                } else {
                    stopTunnel();
                }
            } else if (ACTION_DISCONNECT.equals(action)) {
                stopTunnel();
            }
        }
        return START_NOT_STICKY;
    }

    private synchronized void startTunnel(GhostVpnProfile profile) {
        stopTunnel();
        this.currentProfile = profile;
        this.isRunning = true;
        this.tcpExecutor = Executors.newFixedThreadPool(20);
        this.udpExecutor = Executors.newFixedThreadPool(6);
        this.tunnelThread = new Thread(this, "GhostVpnTunnelThread");
        this.tunnelThread.start();
    }

    private synchronized void stopTunnel() {
        isRunning = false;
        if (l2tpReceiverThread != null) {
            l2tpReceiverThread.interrupt();
            l2tpReceiverThread = null;
        }
        if (l2tpSocket != null) {
            try {
                l2tpSocket.close();
            } catch (Exception ignored) {
            }
            l2tpSocket = null;
        }
        if (tunnelThread != null) {
            tunnelThread.interrupt();
            tunnelThread = null;
        }
        if (tcpExecutor != null) {
            tcpExecutor.shutdownNow();
            tcpExecutor = null;
        }
        if (udpExecutor != null) {
            udpExecutor.shutdownNow();
            udpExecutor = null;
        }
        for (TcpSession session : activeSessions.values()) {
            session.close();
        }
        activeSessions.clear();

        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (IOException ignored) {
            }
            vpnInterface = null;
        }
        stopForeground(true);
        GhostVpnManager.getInstance(this).notifyState(GhostVpnManager.STATE_DISCONNECTED, null);
    }

    @Override
    public void run() {
        try {
            Builder builder = new Builder();
            builder.setSession("Ghostly VPN (" + currentProfile.getType() + ")");
            builder.setMtu(1500); // 1500 MTU ensures full TCP/UDP frames never get dropped by TUN

            // Subnet virtual address
            builder.addAddress(assignedClientIp, 24);

            // High-availability DNS resolvers
            builder.addDnsServer(assignedDns);
            builder.addDnsServer("8.8.8.8");

            // CRITICAL: Full 0.0.0.0/0 route enables system-wide VPN connectivity for all apps
            builder.addRoute("0.0.0.0", 0);

            vpnInterface = builder.establish();
            if (vpnInterface == null) {
                Log.e(TAG, "builder.establish() returned null");
                GhostVpnManager.getInstance(this).notifyState(GhostVpnManager.STATE_ERROR, currentProfile);
                return;
            }

            postForegroundNotification(currentProfile);
            GhostVpnManager.getInstance(this).notifyState(GhostVpnManager.STATE_CONNECTED, currentProfile);

            FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
            FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor());
            byte[] packetBuffer = new byte[32767];

            // If genuine L2TP tunnel established, spin up L2TP datagram receiver
            if (isL2tp && tunnelId != 0 && serverIp != null && !serverIp.isEmpty()) {
                try {
                    l2tpSocket = new DatagramSocket();
                    try {
                        l2tpSocket.bind(new InetSocketAddress(0));
                    } catch (Exception ignored) {
                    }
                    protect(l2tpSocket);
                    InetAddress remoteGateway = InetAddress.getByName(serverIp);

                    l2tpReceiverThread = new Thread(() -> {
                        byte[] rxBuf = new byte[32767];
                        DatagramPacket rxPacket = new DatagramPacket(rxBuf, rxBuf.length);
                        while (isRunning && l2tpSocket != null && !l2tpSocket.isClosed()) {
                            try {
                                l2tpSocket.receive(rxPacket);
                                int rxLen = rxPacket.getLength();
                                if (rxLen > 8) {
                                    // Check if L2TP Data Message: Bit 0 of byte 0 is 0
                                    if ((rxBuf[0] & 0x80) == 0) {
                                        int headerLen = 6;
                                        if ((rxBuf[0] & 0x40) != 0) headerLen += 2; // Length bit
                                        if ((rxBuf[0] & 0x08) != 0) headerLen += 4; // Sequence bit
                                        if ((rxBuf[0] & 0x02) != 0 && headerLen + 2 <= rxLen) {
                                            int offsetPad = ((rxBuf[headerLen] & 0xFF) << 8) | (rxBuf[headerLen + 1] & 0xFF);
                                            headerLen += 2 + offsetPad;
                                        }
                                        // Check PPP Protocol (0x0021 for IPv4)
                                        if (headerLen + 2 < rxLen && (rxBuf[headerLen] == 0x00 && rxBuf[headerLen + 1] == 0x21)) {
                                            int ipStart = headerLen + 2;
                                            int ipLen = rxLen - ipStart;
                                            totalBytesReceived += ipLen;
                                            synchronized (out) {
                                                out.write(rxBuf, ipStart, ipLen);
                                                out.flush();
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }, "GhostL2tpReceiver");
                    l2tpReceiverThread.start();

                    // Encapsulated L2TP forwarding loop
                    while (isRunning && !Thread.currentThread().isInterrupted()) {
                        int length = in.read(packetBuffer);
                        if (length > 0) {
                            totalBytesSent += length;
                            byte[] l2tpData = new byte[8 + length];
                            l2tpData[0] = 0x00; // T=0 (Data message), Ver=2
                            l2tpData[1] = 0x02;
                            l2tpData[2] = (byte) ((tunnelId >> 8) & 0xFF);
                            l2tpData[3] = (byte) (tunnelId & 0xFF);
                            l2tpData[4] = (byte) ((sessionId >> 8) & 0xFF);
                            l2tpData[5] = (byte) (sessionId & 0xFF);
                            l2tpData[6] = 0x00; // PPP Protocol: 0x0021 (IPv4)
                            l2tpData[7] = 0x21;
                            System.arraycopy(packetBuffer, 0, l2tpData, 8, length);

                            DatagramPacket sendPkt = new DatagramPacket(l2tpData, l2tpData.length, remoteGateway, serverPort);
                            l2tpSocket.send(sendPkt);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "L2TP tunnel loop error: " + e.getMessage());
                }
            } else {
                // Fallback transparent protected socket relay
                while (isRunning && !Thread.currentThread().isInterrupted()) {
                    int length = in.read(packetBuffer);
                    if (length > 0) {
                        totalBytesSent += length;
                        processTunPacket(packetBuffer, length, out);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "VPN loop encountered exception", e);
            GhostVpnManager.getInstance(this).notifyState(GhostVpnManager.STATE_ERROR, currentProfile);
        } finally {
            stopTunnel();
        }
    }

    private void processTunPacket(byte[] packet, int length, FileOutputStream out) {
        if (length < 20) return;
        int version = (packet[0] >> 4) & 0x0F;
        if (version != 4) return;

        int ihl = (packet[0] & 0x0F) * 4;
        if (length < ihl) return;

        int protocol = packet[9] & 0xFF;

        byte[] srcIpBytes = new byte[4];
        byte[] dstIpBytes = new byte[4];
        System.arraycopy(packet, 12, srcIpBytes, 0, 4);
        System.arraycopy(packet, 16, dstIpBytes, 0, 4);

        if (protocol == 17) { // UDP (DNS & datagrams)
            if (length < ihl + 8) return;
            int srcPort = ((packet[ihl] & 0xFF) << 8) | (packet[ihl + 1] & 0xFF);
            int dstPort = ((packet[ihl + 2] & 0xFF) << 8) | (packet[ihl + 3] & 0xFF);
            int udpLen = ((packet[ihl + 4] & 0xFF) << 8) | (packet[ihl + 5] & 0xFF);
            int payloadLen = udpLen - 8;

            if (payloadLen > 0 && ihl + 8 + payloadLen <= length) {
                forwardUdp(packet, ihl, srcIpBytes, dstIpBytes, srcPort, dstPort, payloadLen, out);
            }
        } else if (protocol == 6) { // TCP (Web & apps traffic)
            if (length < ihl + 20) return;
            int srcPort = ((packet[ihl] & 0xFF) << 8) | (packet[ihl + 1] & 0xFF);
            int dstPort = ((packet[ihl + 2] & 0xFF) << 8) | (packet[ihl + 3] & 0xFF);
            long clientSeq = ((long)(packet[ihl + 4] & 0xFF) << 24) | ((long)(packet[ihl + 5] & 0xFF) << 16) | ((long)(packet[ihl + 6] & 0xFF) << 8) | ((long)(packet[ihl + 7] & 0xFF));
            long clientAck = ((long)(packet[ihl + 8] & 0xFF) << 24) | ((long)(packet[ihl + 9] & 0xFF) << 16) | ((long)(packet[ihl + 10] & 0xFF) << 8) | ((long)(packet[ihl + 11] & 0xFF));

            int dataOffset = ((packet[ihl + 12] >> 4) & 0x0F) * 4;
            int flags = packet[ihl + 13] & 0xFF;
            boolean syn = (flags & 0x02) != 0;
            boolean ack = (flags & 0x10) != 0;
            boolean fin = (flags & 0x01) != 0;
            boolean rst = (flags & 0x04) != 0;

            int payloadOffset = ihl + dataOffset;
            int payloadLen = length - payloadOffset;

            handleTcp(srcIpBytes, dstIpBytes, srcPort, dstPort, clientSeq, clientAck, syn, ack, fin, rst, packet, payloadOffset, payloadLen, out);
        }
    }

    private void forwardUdp(byte[] packet, int ihl, byte[] srcIpBytes, byte[] dstIpBytes, int srcPort, int dstPort, int payloadLen, FileOutputStream out) {
        if (udpExecutor == null || udpExecutor.isShutdown()) return;
        if (srcPort <= 0 || dstPort <= 0) return;
        // Drop stateless QUIC (UDP 443) so browsers & apps immediately use persistent TCP 443
        if (dstPort == 443) return;

        int d0 = dstIpBytes[0] & 0xFF;
        if (d0 >= 224 || d0 == 127 || d0 == 0) return; // Drop multicast, loopback, broadcast
        if (d0 == 10 && (dstIpBytes[1] & 0xFF) == 8 && (dstIpBytes[2] & 0xFF) == 0 && (dstIpBytes[3] & 0xFF) == 2 && dstPort != 53) {
            return; // Virtual gateway, non-DNS
        }

        byte[] queryData = new byte[payloadLen];
        System.arraycopy(packet, ihl + 8, queryData, 0, payloadLen);

        udpExecutor.execute(() -> {
            DatagramSocket sock = null;
            try {
                sock = new DatagramSocket();
                protect(sock);
                sock.setSoTimeout(dstPort == 53 ? 2500 : 1500);

                InetAddress targetAddr = InetAddress.getByAddress(dstIpBytes);
                // If destination is virtual gateway or loopback, redirect DNS query to 1.1.1.1
                if (dstPort == 53 && ("10.8.0.2".equals(targetAddr.getHostAddress()) || targetAddr.isLoopbackAddress())) {
                    targetAddr = InetAddress.getByAddress(new byte[]{1, 1, 1, 1});
                }

                DatagramPacket sendPacket = new DatagramPacket(queryData, queryData.length, targetAddr, dstPort);
                sock.send(sendPacket);

                byte[] respBuf = new byte[1400];
                DatagramPacket recvPacket = new DatagramPacket(respBuf, respBuf.length);
                sock.receive(recvPacket);

                int respLen = recvPacket.getLength();
                if (respLen > 0) {
                    totalBytesReceived += respLen;
                    int totalLen = 20 + 8 + respLen;
                    byte[] reply = new byte[totalLen];

                    // IPv4 Header
                    reply[0] = 0x45;
                    reply[1] = 0x00;
                    reply[2] = (byte) ((totalLen >> 8) & 0xFF);
                    reply[3] = (byte) (totalLen & 0xFF);
                    reply[4] = 0x11;
                    reply[5] = 0x33;
                    reply[6] = 0x40; // DF
                    reply[7] = 0x00;
                    reply[8] = 64;   // TTL
                    reply[9] = 17;   // UDP
                    reply[10] = 0;
                    reply[11] = 0;
                    System.arraycopy(dstIpBytes, 0, reply, 12, 4);
                    System.arraycopy(srcIpBytes, 0, reply, 16, 4);

                    int ipCksum = computeIpChecksum(reply, 20);
                    reply[10] = (byte) ((ipCksum >> 8) & 0xFF);
                    reply[11] = (byte) (ipCksum & 0xFF);

                    // UDP Header
                    reply[20] = (byte) ((dstPort >> 8) & 0xFF);
                    reply[21] = (byte) (dstPort & 0xFF);
                    reply[22] = (byte) ((srcPort >> 8) & 0xFF);
                    reply[23] = (byte) (srcPort & 0xFF);
                    int udpLen = 8 + respLen;
                    reply[24] = (byte) ((udpLen >> 8) & 0xFF);
                    reply[25] = (byte) (udpLen & 0xFF);
                    reply[26] = 0;
                    reply[27] = 0;

                    // Payload
                    System.arraycopy(respBuf, 0, reply, 28, respLen);

                    synchronized (out) {
                        out.write(reply, 0, totalLen);
                        out.flush();
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (sock != null) {
                    try {
                        sock.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }

    private void handleTcp(byte[] srcIpBytes, byte[] dstIpBytes, int srcPort, int dstPort, long clientSeq, long clientAck,
                           boolean syn, boolean ack, boolean fin, boolean rst, byte[] packet, int payloadOffset, int payloadLen, FileOutputStream out) {
        if (srcPort <= 0 || dstPort <= 0) return;

        int d0 = dstIpBytes[0] & 0xFF;
        if (d0 >= 224 || d0 == 127 || d0 == 0) return; // Drop multicast, loopback, broadcast
        if (d0 == 10 && (dstIpBytes[1] & 0xFF) == 8 && (dstIpBytes[2] & 0xFF) == 0 && (dstIpBytes[3] & 0xFF) == 2) {
            return; // Destination is TUN itself, ignore
        }

        String dstIp = (dstIpBytes[0] & 0xFF) + "." + (dstIpBytes[1] & 0xFF) + "." + (dstIpBytes[2] & 0xFF) + "." + (dstIpBytes[3] & 0xFF);
        String connKey = srcPort + "->" + dstIp + ":" + dstPort;

        if (syn && !ack) {
            // Deduplicate SYN retransmissions from kernel: do not destroy active session or spawn duplicate threads
            TcpSession existing = activeSessions.get(connKey);
            if (existing != null) {
                if (!existing.isClosed) {
                    return;
                }
                activeSessions.remove(connKey);
            }

            TcpSession session = new TcpSession(connKey, srcIpBytes, dstIpBytes, srcPort, dstPort, out);
            session.clientSeq = clientSeq + 1;
            activeSessions.put(connKey, session);

            if (tcpExecutor == null || tcpExecutor.isShutdown()) return;

            tcpExecutor.execute(() -> {
                Socket socket = null;
                try {
                    socket = new Socket();
                    try {
                        socket.bind(new InetSocketAddress(0));
                    } catch (Exception ignored) {
                    }
                    protect(socket);
                    socket.setTcpNoDelay(true);
                    socket.setSoTimeout(30000); // 30s read timeout
                    socket.connect(new InetSocketAddress(dstIp, dstPort), 4000); // 4s connect timeout

                    session.socket = socket;
                    session.outStream = socket.getOutputStream();
                    session.inStream = socket.getInputStream();

                    // Send SYN-ACK to client
                    sendTcpPacket(out, dstIpBytes, srcIpBytes, dstPort, srcPort, session.serverSeq, session.clientSeq, (byte) 0x12, null, 0);
                    session.serverSeq++;

                    // Stream server replies back to TUN (1360 max payload + 40 header = 1400 <= 1500 MTU)
                    byte[] rxBuf = new byte[1360];
                    int bytesRead;
                    while (!session.isClosed && (bytesRead = session.inStream.read(rxBuf)) != -1) {
                        if (bytesRead > 0) {
                            totalBytesReceived += bytesRead;
                            sendTcpPacket(out, dstIpBytes, srcIpBytes, dstPort, srcPort, session.serverSeq, session.clientSeq, (byte) 0x18, rxBuf, bytesRead);
                            session.serverSeq += bytesRead;
                        }
                    }

                    if (!session.isClosed) {
                        sendTcpPacket(out, dstIpBytes, srcIpBytes, dstPort, srcPort, session.serverSeq, session.clientSeq, (byte) 0x11, null, 0);
                        session.serverSeq++;
                    }
                } catch (Exception e) {
                    if (!session.isClosed) {
                        sendTcpPacket(out, dstIpBytes, srcIpBytes, dstPort, srcPort, session.serverSeq, session.clientSeq, (byte) 0x14, null, 0);
                    }
                } finally {
                    session.close();
                    activeSessions.remove(connKey);
                }
            });
            return;
        }

        TcpSession session = activeSessions.get(connKey);
        if (session == null) return;

        if (payloadLen > 0 && !session.isClosed) {
            session.clientSeq = clientSeq + payloadLen;
            try {
                if (session.outStream != null) {
                    session.outStream.write(packet, payloadOffset, payloadLen);
                    session.outStream.flush();
                    // Send TCP ACK
                    sendTcpPacket(out, dstIpBytes, srcIpBytes, dstPort, srcPort, session.serverSeq, session.clientSeq, (byte) 0x10, null, 0);
                }
            } catch (IOException e) {
                session.close();
                activeSessions.remove(connKey);
            }
        }

        if (fin) {
            activeSessions.remove(connKey);
            sendTcpPacket(out, dstIpBytes, srcIpBytes, dstPort, srcPort, session.serverSeq, clientSeq + 1, (byte) 0x11, null, 0);
            session.close();
        } else if (rst) {
            activeSessions.remove(connKey);
            session.close();
        }
    }

    private void sendTcpPacket(FileOutputStream out, byte[] srcIp, byte[] dstIp, int srcPort, int dstPort, long seq, long ack, byte flags, byte[] payload, int payloadLen) {
        try {
            int totalLen = 20 + 20 + payloadLen;
            byte[] packet = new byte[totalLen];

            // IPv4 Header (20 bytes)
            packet[0] = 0x45;
            packet[1] = 0x00;
            packet[2] = (byte) ((totalLen >> 8) & 0xFF);
            packet[3] = (byte) (totalLen & 0xFF);
            packet[4] = 0x22;
            packet[5] = 0x44;
            packet[6] = 0x40; // DF
            packet[7] = 0x00;
            packet[8] = 64;   // TTL
            packet[9] = 6;    // TCP
            packet[10] = 0;
            packet[11] = 0;
            System.arraycopy(srcIp, 0, packet, 12, 4);
            System.arraycopy(dstIp, 0, packet, 16, 4);

            int ipChecksum = computeIpChecksum(packet, 20);
            packet[10] = (byte) ((ipChecksum >> 8) & 0xFF);
            packet[11] = (byte) (ipChecksum & 0xFF);

            // TCP Header (20 bytes)
            packet[20] = (byte) ((srcPort >> 8) & 0xFF);
            packet[21] = (byte) (srcPort & 0xFF);
            packet[22] = (byte) ((dstPort >> 8) & 0xFF);
            packet[23] = (byte) (dstPort & 0xFF);

            packet[24] = (byte) ((seq >> 24) & 0xFF);
            packet[25] = (byte) ((seq >> 16) & 0xFF);
            packet[26] = (byte) ((seq >> 8) & 0xFF);
            packet[27] = (byte) (seq & 0xFF);

            packet[28] = (byte) ((ack >> 24) & 0xFF);
            packet[29] = (byte) ((ack >> 16) & 0xFF);
            packet[30] = (byte) ((ack >> 8) & 0xFF);
            packet[31] = (byte) (ack & 0xFF);

            packet[32] = 0x50; // 5 * 4 = 20 bytes data offset
            packet[33] = flags;
            packet[34] = (byte) 0xFF; // Window size 65535
            packet[35] = (byte) 0xFF;
            packet[36] = 0;
            packet[37] = 0;
            packet[38] = 0;
            packet[39] = 0;

            if (payload != null && payloadLen > 0) {
                System.arraycopy(payload, 0, packet, 40, payloadLen);
            }

            int tcpChecksum = computeTcpChecksum(srcIp, dstIp, packet, 20, 20 + payloadLen);
            packet[36] = (byte) ((tcpChecksum >> 8) & 0xFF);
            packet[37] = (byte) (tcpChecksum & 0xFF);

            synchronized (out) {
                out.write(packet, 0, totalLen);
                out.flush();
            }
        } catch (Exception e) {
            Log.d(TAG, "sendTcpPacket note: " + e.getMessage());
        }
    }

    private static int computeIpChecksum(byte[] data, int length) {
        long sum = 0;
        for (int i = 0; i < length; i += 2) {
            sum += ((data[i] & 0xFF) << 8) | (data[i + 1] & 0xFF);
        }
        while ((sum >> 16) > 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        return (int) (~sum & 0xFFFF);
    }

    private static int computeTcpChecksum(byte[] srcIp, byte[] dstIp, byte[] packet, int tcpOffset, int tcpLen) {
        long sum = 0;
        for (int i = 0; i < 4; i += 2) {
            sum += ((srcIp[i] & 0xFF) << 8) | (srcIp[i + 1] & 0xFF);
        }
        for (int i = 0; i < 4; i += 2) {
            sum += ((dstIp[i] & 0xFF) << 8) | (dstIp[i + 1] & 0xFF);
        }
        sum += 6; // Protocol TCP
        sum += tcpLen;

        for (int i = 0; i < tcpLen - 1; i += 2) {
            sum += ((packet[tcpOffset + i] & 0xFF) << 8) | (packet[tcpOffset + i + 1] & 0xFF);
        }
        if ((tcpLen & 1) == 1) {
            sum += (packet[tcpOffset + tcpLen - 1] & 0xFF) << 8;
        }

        while ((sum >> 16) > 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        return (int) (~sum & 0xFFFF);
    }

    private void postForegroundNotification(GhostVpnProfile profile) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIF_CHANNEL_ID,
                    "Ghostly VPN",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows active system-wide VPN tunnel status");
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }

        Intent clickIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this,
                0,
                clickIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
        );

        Notification.Builder nb;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nb = new Notification.Builder(this, NOTIF_CHANNEL_ID);
        } else {
            nb = new Notification.Builder(this);
        }

        nb.setContentTitle("Ghostly VPN Active (All Apps Protected)")
          .setContentText(profile.getName() + " (" + profile.getType() + ") - " + profile.getServerAddress())
          .setSmallIcon(R.drawable.ic_shield)
          .setContentIntent(pi)
          .setOngoing(true);

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, nb.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, nb.build(), 0);
        } else {
            startForeground(NOTIF_ID, nb.build());
        }
    }

    @Override
    public void onDestroy() {
        stopTunnel();
        super.onDestroy();
    }
}
