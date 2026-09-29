package da.ghostly.com.vpn;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import da.ghostly.com.R;

/**
 * SystemVpnSetupDialog
 * Shows VPN configuration details and guides the user to Android System VPN Settings
 * for L2TP/IPSec PSK connections that require the platform's native VPN client.
 */
public class SystemVpnSetupDialog {

    public static void show(Activity activity, GhostVpnProfile profile, String username, String password) {
        Dialog dialog = new Dialog(activity, R.style.GhostDialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_system_vpn_setup, null);
        dialog.setContentView(view);

        Window window = dialog.getWindow();
        if (window != null) {
            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            int targetWidth = (int) (dm.widthPixels * 0.94f);
            int maxWidth = (int) (520 * dm.density);
            if (targetWidth > maxWidth) targetWidth = maxWidth;
            int targetHeight = (int) (dm.heightPixels * 0.85f);
            window.setLayout(targetWidth, targetHeight);
        }

        TextView tvName = view.findViewById(R.id.tvSetupProfileName);
        TextView tvType = view.findViewById(R.id.tvSetupType);
        TextView tvServer = view.findViewById(R.id.tvSetupServer);
        TextView tvPsk = view.findViewById(R.id.tvSetupPsk);
        TextView tvUsername = view.findViewById(R.id.tvSetupUsername);
        TextView tvPassword = view.findViewById(R.id.tvSetupPassword);
        View layoutPsk = view.findViewById(R.id.layoutSetupPsk);
        Button btnCopy = view.findViewById(R.id.btnCopyAllDetails);
        Button btnOpenSettings = view.findViewById(R.id.btnOpenSystemVpn);
        Button btnClose = view.findViewById(R.id.btnCloseSetup);

        String name = profile.getName();
        String type = profile.getType();
        String server = profile.getServerAddress();
        String psk = profile.getIpsecPsk();
        String user = (username != null && !username.isEmpty()) ? username : profile.getLastUsername();
        String pass = (password != null && !password.isEmpty()) ? password : profile.getLastPassword();

        tvName.setText(name);
        tvType.setText(type);
        tvServer.setText(server);

        if (psk != null && !psk.isEmpty()) {
            tvPsk.setText(psk);
            layoutPsk.setVisibility(View.VISIBLE);
        } else {
            layoutPsk.setVisibility(View.GONE);
        }

        tvUsername.setText(user != null && !user.isEmpty() ? user : "(not set)");
        tvPassword.setText(pass != null && !pass.isEmpty() ? pass : "(not set)");

        btnCopy.setOnClickListener(v -> {
            StringBuilder sb = new StringBuilder();
            sb.append("VPN Name: ").append(name).append("\n");
            sb.append("Type: ").append(type).append("\n");
            sb.append("Server: ").append(server).append("\n");
            if (psk != null && !psk.isEmpty()) {
                sb.append("IPSec PSK: ").append(psk).append("\n");
            }
            sb.append("Username: ").append(user != null ? user : "").append("\n");
            sb.append("Password: ").append(pass != null ? pass : "").append("\n");

            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("VPN Details", sb.toString()));
                Toast.makeText(activity, "VPN details copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });

        btnOpenSettings.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Settings.ACTION_VPN_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
            } catch (Exception e) {
                try {
                    Intent fallback = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(fallback);
                } catch (Exception ex) {
                    Toast.makeText(activity, "Unable to open system VPN settings. Navigate manually: Settings > Network > VPN", Toast.LENGTH_LONG).show();
                }
            }
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}
