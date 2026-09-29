package da.ghostly.com.vpn;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * GhostVpnManager
 * Central coordinator for in-app isolated VPN profiles, credential retention, and service controls.
 */
public class GhostVpnManager {

    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;
    public static final int STATE_ERROR = 3;

    public static final int REQUEST_VPN_PREPARE = 9182;

    private static final String PREF_NAME = "ghost_vpn_prefs";
    private static final String KEY_PROFILES = "profiles_json";
    private static final String KEY_ACTIVE_PROFILE_ID = "active_profile_id";
    private static final String KEY_DEFAULTS_SEEDED = "defaults_seeded_v1";

    private static GhostVpnManager instance;

    public interface StateListener {
        void onVpnStateChanged(int state, GhostVpnProfile activeProfile);
    }

    private final Context appContext;
    private final SharedPreferences prefs;
    private final List<StateListener> listeners = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private int currentState = STATE_DISCONNECTED;
    private GhostVpnProfile activeProfile = null;
    private GhostVpnProfile pendingProfile = null;
    private String pendingUsername = "";
    private String pendingPassword = "";

    public static synchronized GhostVpnManager getInstance(Context context) {
        if (instance == null) {
            instance = new GhostVpnManager(context.getApplicationContext());
        }
        return instance;
    }

    private GhostVpnManager(Context context) {
        this.appContext = context;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        seedDefaultProfiles();
    }

    /**
     * Seeds 2 default VPN Gate profiles on first launch.
     * VPN Gate credentials: username=vpn, password=vpn, PSK=vpn
     */
    private void seedDefaultProfiles() {
        if (prefs.getBoolean(KEY_DEFAULTS_SEEDED, false)) return;
        prefs.edit().putBoolean(KEY_DEFAULTS_SEEDED, true).apply();

        GhostVpnProfile p1 = new GhostVpnProfile(
                "VPN Gate (Japan #1)",
                GhostVpnProfile.TYPE_L2TP_IPSEC_PSK,
                "219.100.37.201",
                "",
                true,
                "vpn"
        );
        p1.setLastUsername("vpn");
        p1.setLastPassword("vpn");
        p1.setRememberCredentials(true);

        GhostVpnProfile p2 = new GhostVpnProfile(
                "VPN Gate (Japan #2)",
                GhostVpnProfile.TYPE_L2TP_IPSEC_PSK,
                "219.100.37.176",
                "",
                true,
                "vpn"
        );
        p2.setLastUsername("vpn");
        p2.setLastPassword("vpn");
        p2.setRememberCredentials(true);

        List<GhostVpnProfile> profiles = getProfiles();
        profiles.add(p1);
        profiles.add(p2);
        saveProfiles(profiles);
    }

    /**
     * Opens Android System VPN Settings for L2TP/IPSec configuration.
     */
    public static void launchSystemVpnSettings(android.app.Activity activity) {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_VPN_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(fallback);
            } catch (Exception ignored) {
            }
        }
    }

    public synchronized List<GhostVpnProfile> getProfiles() {
        List<GhostVpnProfile> list = new ArrayList<>();
        String jsonStr = prefs.getString(KEY_PROFILES, "[]");
        try {
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                GhostVpnProfile p = GhostVpnProfile.fromJson(obj);
                if (p != null) {
                    list.add(p);
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public synchronized void saveProfiles(List<GhostVpnProfile> list) {
        try {
            JSONArray arr = new JSONArray();
            for (GhostVpnProfile p : list) {
                arr.put(p.toJson());
            }
            prefs.edit().putString(KEY_PROFILES, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public synchronized void addOrUpdateProfile(GhostVpnProfile profile) {
        List<GhostVpnProfile> profiles = getProfiles();
        boolean found = false;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).getId().equals(profile.getId())) {
                profiles.set(i, profile);
                found = true;
                break;
            }
        }
        if (!found) {
            profiles.add(profile);
        }
        saveProfiles(profiles);
    }

    public synchronized void deleteProfile(String profileId) {
        if (activeProfile != null && activeProfile.getId().equals(profileId)) {
            stopVpn();
        }
        List<GhostVpnProfile> profiles = getProfiles();
        List<GhostVpnProfile> updated = new ArrayList<>();
        for (GhostVpnProfile p : profiles) {
            if (!p.getId().equals(profileId)) {
                updated.add(p);
            }
        }
        saveProfiles(updated);
    }

    public synchronized GhostVpnProfile getProfileById(String id) {
        if (id == null) return null;
        for (GhostVpnProfile p : getProfiles()) {
            if (p.getId().equals(id)) return p;
        }
        return null;
    }

    public synchronized void updateCredentials(String profileId, String username, String password, boolean remember) {
        GhostVpnProfile profile = getProfileById(profileId);
        if (profile != null) {
            profile.setRememberCredentials(remember);
            if (remember) {
                profile.setLastUsername(username);
                profile.setLastPassword(password);
            } else {
                profile.setLastUsername("");
                profile.setLastPassword("");
            }
            addOrUpdateProfile(profile);
        }
    }

    public int getCurrentState() {
        return currentState;
    }

    public GhostVpnProfile getActiveProfile() {
        return activeProfile;
    }

    public boolean isVpnActive() {
        return currentState == STATE_CONNECTED;
    }

    public void addListener(StateListener listener) {
        synchronized (listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
        }
    }

    public void removeListener(StateListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    public void notifyState(int state, GhostVpnProfile profile) {
        this.currentState = state;
        this.activeProfile = (state == STATE_CONNECTED || state == STATE_CONNECTING) ? profile : null;

        mainHandler.post(() -> {
            synchronized (listeners) {
                for (StateListener l : listeners) {
                    l.onVpnStateChanged(state, activeProfile);
                }
            }
        });
    }

    public boolean connect(Activity activity, GhostVpnProfile profile, String username, String password, boolean remember) {
        updateCredentials(profile.getId(), username, password, remember);

        Intent prepareIntent = VpnService.prepare(activity);
        if (prepareIntent != null) {
            // Android requires user confirmation to start a VPN
            this.pendingProfile = profile;
            this.pendingUsername = username;
            this.pendingPassword = password;
            activity.startActivityForResult(prepareIntent, REQUEST_VPN_PREPARE);
            return false;
        } else {
            // Permission already granted -> launch Connecting Dialog
            VpnConnectingDialog.show(activity, profile, username, password, null);
            return true;
        }
    }

    public void onPrepareResult(Activity activity, int resultCode) {
        if (resultCode == Activity.RESULT_OK && pendingProfile != null) {
            if (activity != null) {
                VpnConnectingDialog.show(activity, pendingProfile, pendingUsername, pendingPassword, null);
            } else {
                startServiceInternal(pendingProfile, pendingUsername, pendingPassword);
            }
        } else {
            notifyState(STATE_DISCONNECTED, null);
        }
        pendingProfile = null;
        pendingUsername = "";
        pendingPassword = "";
    }

    public void onPrepareResult(int resultCode) {
        onPrepareResult(null, resultCode);
    }

    public void startServiceInternal(GhostVpnProfile profile, String username, String password) {
        startServiceInternal(profile, username, password, null);
    }

    public void startServiceInternal(GhostVpnProfile profile, String username, String password, VpnProtocolClient.HandshakeResult result) {
        notifyState(STATE_CONNECTING, profile);

        Intent intent = new Intent(appContext, GhostVpnService.class);
        intent.setAction(GhostVpnService.ACTION_CONNECT);
        intent.putExtra(GhostVpnService.EXTRA_PROFILE_ID, profile.getId());
        intent.putExtra(GhostVpnService.EXTRA_USERNAME, username);
        intent.putExtra(GhostVpnService.EXTRA_PASSWORD, password);

        if (result != null) {
            intent.putExtra(GhostVpnService.EXTRA_SERVER_IP, result.serverIp);
            intent.putExtra(GhostVpnService.EXTRA_SERVER_PORT, result.serverPort);
            intent.putExtra(GhostVpnService.EXTRA_CLIENT_IP, result.assignedClientIp);
            intent.putExtra(GhostVpnService.EXTRA_DNS_SERVER, result.dnsServer);
            intent.putExtra(GhostVpnService.EXTRA_TUNNEL_ID, result.tunnelId);
            intent.putExtra(GhostVpnService.EXTRA_SESSION_ID, result.sessionId);
            intent.putExtra(GhostVpnService.EXTRA_IS_L2TP, result.isL2tp);
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent);
        } else {
            appContext.startService(intent);
        }
    }

    public void stopVpn() {
        notifyState(STATE_DISCONNECTED, null);
        Intent intent = new Intent(appContext, GhostVpnService.class);
        intent.setAction(GhostVpnService.ACTION_DISCONNECT);
        appContext.startService(intent);
    }
}
