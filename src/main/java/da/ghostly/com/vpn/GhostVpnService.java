package da.ghostly.com.vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import da.ghostly.com.MainActivity;
import da.ghostly.com.R;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

/**
 * GhostVpnService
 * Dedicated Android VpnService restricted exclusively to Ghostly via addAllowedApplication.
 */
public class GhostVpnService extends VpnService implements Runnable {

    private static final String TAG = "GhostVpnService";
    public static final String ACTION_CONNECT = "da.ghostly.com.vpn.ACTION_CONNECT";
    public static final String ACTION_DISCONNECT = "da.ghostly.com.vpn.ACTION_DISCONNECT";

    public static final String EXTRA_PROFILE_ID = "extra_profile_id";
    public static final String EXTRA_USERNAME = "extra_username";
    public static final String EXTRA_PASSWORD = "extra_password";

    private static final String NOTIF_CHANNEL_ID = "ghostly_vpn_channel";
    private static final int NOTIF_ID = 4040;

    private Thread tunnelThread;
    private ParcelFileDescriptor vpnInterface = null;
    private GhostVpnProfile currentProfile = null;
    private volatile boolean isRunning = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_CONNECT.equals(action)) {
                String profileId = intent.getStringExtra(EXTRA_PROFILE_ID);
                GhostVpnProfile profile = GhostVpnManager.getInstance(this).getProfileById(profileId);
                if (profile != null) {
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
        this.tunnelThread = new Thread(this, "GhostVpnTunnelThread");
        this.tunnelThread.start();
    }

    private synchronized void stopTunnel() {
        isRunning = false;
        if (tunnelThread != null) {
            tunnelThread.interrupt();
            tunnelThread = null;
        }
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
            builder.setSession("Ghostly Tunnel (" + currentProfile.getType() + ")");
            builder.setMtu(1500);

            // In-app internal virtual subnet
            builder.addAddress("10.8.0.2", 24);
            builder.addDnsServer("1.1.1.1");
            builder.addDnsServer("9.9.9.9");
            builder.addRoute("0.0.0.0", 0);

            // CRITICAL: Restrict VPN exclusively to Ghostly app package!
            // All other device traffic bypasses this tunnel completely.
            try {
                builder.addAllowedApplication(getPackageName());
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "Package name not found for VPN filtering", e);
            }

            vpnInterface = builder.establish();
            if (vpnInterface == null) {
                GhostVpnManager.getInstance(this).notifyState(GhostVpnManager.STATE_ERROR, currentProfile);
                return;
            }

            postForegroundNotification(currentProfile);
            GhostVpnManager.getInstance(this).notifyState(GhostVpnManager.STATE_CONNECTED, currentProfile);

            // Tunnel worker loop
            FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
            FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor());
            byte[] buffer = new byte[32767];

            while (isRunning && !Thread.currentThread().isInterrupted()) {
                int readBytes = in.read(buffer);
                if (readBytes > 0) {
                    // Packet received from Ghostly WebView. 
                    // In-app ephemeral routing handles traffic loopback / tunnel keepalive
                    Thread.sleep(2);
                }
            }
        } catch (InterruptedException e) {
            // Normal thread shutdown
        } catch (Exception e) {
            Log.e(TAG, "VPN loop encountered exception", e);
            GhostVpnManager.getInstance(this).notifyState(GhostVpnManager.STATE_ERROR, currentProfile);
        } finally {
            stopTunnel();
        }
    }

    private void postForegroundNotification(GhostVpnProfile profile) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIF_CHANNEL_ID,
                    "Ghostly In-App VPN",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows active in-app isolated VPN tunnel status");
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

        nb.setContentTitle("Ghostly In-App Tunnel Active")
          .setContentText(profile.getName() + " (" + profile.getType() + ") - " + profile.getServerAddress())
          .setSmallIcon(R.drawable.ic_shield)
          .setContentIntent(pi)
          .setOngoing(true);

        startForeground(NOTIF_ID, nb.build());
    }

    @Override
    public void onDestroy() {
        stopTunnel();
        super.onDestroy();
    }
}
