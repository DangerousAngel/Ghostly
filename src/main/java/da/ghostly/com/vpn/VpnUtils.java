package da.ghostly.com.vpn;

import android.util.Log;
import java.net.InetAddress;

/**
 * VpnUtils
 * Network utilities for host sanitization, direct IPv4 octet parsing, and custom port extraction.
 */
public class VpnUtils {

    private static final String TAG = "VpnUtils";

    /**
     * Sanitizes server address string by stripping schemes, paths, ports, and whitespace.
     */
    public static String cleanHost(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.startsWith("http://")) s = s.substring(7);
        if (s.startsWith("https://")) s = s.substring(8);
        int slash = s.indexOf('/');
        if (slash != -1) s = s.substring(0, slash);
        int colon = s.indexOf(':');
        if (colon != -1) s = s.substring(0, colon);
        return s.trim();
    }

    /**
     * Extracts port from address string if specified (e.g. 219.100.37.123:1701 -> 1701).
     */
    public static int extractPort(String raw, int defaultPort) {
        if (raw == null) return defaultPort;
        String s = raw.trim();
        if (s.startsWith("http://")) s = s.substring(7);
        if (s.startsWith("https://")) s = s.substring(8);
        int slash = s.indexOf('/');
        if (slash != -1) s = s.substring(0, slash);
        int colon = s.indexOf(':');
        if (colon != -1) {
            try {
                int port = Integer.parseInt(s.substring(colon + 1).trim());
                if (port > 0 && port <= 65535) return port;
            } catch (Exception ignored) {
            }
        }
        return defaultPort;
    }

    /**
     * Parses IPv4 address directly into InetAddress (bypassing DNS lookup completely).
     * Falls back to DNS lookup only if the address is a genuine domain name.
     */
    public static InetAddress parseOrResolve(String raw) {
        String clean = cleanHost(raw);
        if (clean.isEmpty()) return null;

        // Fast IPv4 octet parsing (avoids DNS queries and "No address associated with hostname" errors)
        String[] parts = clean.split("\\.");
        if (parts.length == 4) {
            try {
                byte[] ip = new byte[4];
                for (int i = 0; i < 4; i++) {
                    int b = Integer.parseInt(parts[i].trim());
                    if (b < 0 || b > 255) throw new NumberFormatException();
                    ip[i] = (byte) b;
                }
                return InetAddress.getByAddress(clean, ip);
            } catch (Exception ignored) {
            }
        }

        // Domain name: resolve via system DNS
        try {
            return InetAddress.getByName(clean);
        } catch (Exception e) {
            Log.w(TAG, "DNS lookup failed for " + clean + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Extracts a custom port number from the IP Identifier field if the user entered
     * either a port number directly (e.g. "500", "4500", "1701") or "id:port" format.
     */
    public static int extractCustomPortFromId(String ipIdentifier, int defaultPort) {
        if (ipIdentifier == null || ipIdentifier.trim().isEmpty()) {
            return defaultPort;
        }
        String s = ipIdentifier.trim();
        // Direct port number (e.g. "500", "4500", "1701", "1194")
        try {
            int p = Integer.parseInt(s);
            if (p > 0 && p <= 65535) {
                return p;
            }
        } catch (NumberFormatException ignored) {
        }
        // Host/ID with ":port" suffix
        int colon = s.lastIndexOf(':');
        if (colon != -1 && colon + 1 < s.length()) {
            try {
                int p = Integer.parseInt(s.substring(colon + 1).trim());
                if (p > 0 && p <= 65535) {
                    return p;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultPort;
    }
}

