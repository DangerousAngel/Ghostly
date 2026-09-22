package da.ghostly.com.vpn;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

/**
 * GhostVpnProfile
 * Immutable & serializable model for in-app isolated VPN configurations.
 */
public class GhostVpnProfile {

    public static final String TYPE_L2TP_IPSEC_PSK = "L2TP/IPSec PSK";
    public static final String TYPE_L2TP_IPSEC_RSA = "L2TP/IPSec RSA";
    public static final String TYPE_PPTP = "PPTP";
    public static final String TYPE_IPSEC_XAUTH_PSK = "IPSec Xauth PSK";
    public static final String TYPE_IPSEC_XAUTH_RSA = "IPSec Xauth RSA";
    public static final String TYPE_IKEV2_IPSEC = "IKEv2/IPsec";

    public static final String[] SUPPORTED_TYPES = new String[] {
            TYPE_L2TP_IPSEC_PSK,
            TYPE_L2TP_IPSEC_RSA,
            TYPE_PPTP,
            TYPE_IPSEC_XAUTH_PSK,
            TYPE_IPSEC_XAUTH_RSA,
            TYPE_IKEV2_IPSEC
    };

    private String id;
    private String name;
    private String type;
    private String serverAddress;
    private boolean pppEncryption;
    private String ipsecPsk;
    private String lastUsername;
    private String lastPassword;
    private boolean rememberCredentials;
    private long createdAt;

    public GhostVpnProfile() {
        this.id = UUID.randomUUID().toString();
        this.name = "";
        this.type = TYPE_L2TP_IPSEC_PSK;
        this.serverAddress = "";
        this.pppEncryption = true;
        this.ipsecPsk = "";
        this.lastUsername = "";
        this.lastPassword = "";
        this.rememberCredentials = true;
        this.createdAt = System.currentTimeMillis();
    }

    public GhostVpnProfile(String name, String type, String serverAddress, boolean pppEncryption, String ipsecPsk) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.type = (type != null && !type.isEmpty()) ? type : TYPE_L2TP_IPSEC_PSK;
        this.serverAddress = serverAddress;
        this.pppEncryption = pppEncryption;
        this.ipsecPsk = (ipsecPsk != null) ? ipsecPsk : "";
        this.lastUsername = "";
        this.lastPassword = "";
        this.rememberCredentials = true;
        this.createdAt = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return (name != null && !name.isEmpty()) ? name : "Ghostly Tunnel";
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return (type != null && !type.isEmpty()) ? type : TYPE_L2TP_IPSEC_PSK;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getServerAddress() {
        return (serverAddress != null) ? serverAddress : "";
    }

    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public boolean isPppEncryption() {
        return pppEncryption;
    }

    public void setPppEncryption(boolean pppEncryption) {
        this.pppEncryption = pppEncryption;
    }

    public String getIpsecPsk() {
        return (ipsecPsk != null) ? ipsecPsk : "";
    }

    public void setIpsecPsk(String ipsecPsk) {
        this.ipsecPsk = ipsecPsk;
    }

    public String getLastUsername() {
        return (lastUsername != null) ? lastUsername : "";
    }

    public void setLastUsername(String lastUsername) {
        this.lastUsername = lastUsername;
    }

    public String getLastPassword() {
        return (lastPassword != null) ? lastPassword : "";
    }

    public void setLastPassword(String lastPassword) {
        this.lastPassword = lastPassword;
    }

    public boolean isRememberCredentials() {
        return rememberCredentials;
    }

    public void setRememberCredentials(boolean rememberCredentials) {
        this.rememberCredentials = rememberCredentials;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("name", name);
        obj.put("type", type);
        obj.put("serverAddress", serverAddress);
        obj.put("pppEncryption", pppEncryption);
        obj.put("ipsecPsk", ipsecPsk);
        obj.put("lastUsername", lastUsername);
        obj.put("lastPassword", lastPassword);
        obj.put("rememberCredentials", rememberCredentials);
        obj.put("createdAt", createdAt);
        return obj;
    }

    public static GhostVpnProfile fromJson(JSONObject obj) {
        if (obj == null) return null;
        GhostVpnProfile profile = new GhostVpnProfile();
        profile.setId(obj.optString("id", UUID.randomUUID().toString()));
        profile.setName(obj.optString("name", "Ghostly Tunnel"));
        profile.setType(obj.optString("type", TYPE_L2TP_IPSEC_PSK));
        profile.setServerAddress(obj.optString("serverAddress", ""));
        profile.setPppEncryption(obj.optBoolean("pppEncryption", true));
        profile.setIpsecPsk(obj.optString("ipsecPsk", ""));
        profile.setLastUsername(obj.optString("lastUsername", ""));
        profile.setLastPassword(obj.optString("lastPassword", ""));
        profile.setRememberCredentials(obj.optBoolean("rememberCredentials", true));
        profile.setCreatedAt(obj.optLong("createdAt", System.currentTimeMillis()));
        return profile;
    }
}
