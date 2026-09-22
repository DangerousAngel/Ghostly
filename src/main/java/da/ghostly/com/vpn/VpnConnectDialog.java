package da.ghostly.com.vpn;

import android.app.Activity;
import android.app.Dialog;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import da.ghostly.com.R;

/**
 * VpnConnectDialog
 * Authentication modal with remembered / pre-filled credentials for VPN connection.
 */
public class VpnConnectDialog {

    public interface Callback {
        void onConnectInitiated(GhostVpnProfile profile);
    }

    public static void show(Activity activity, GhostVpnProfile profile, Callback callback) {
        Dialog dialog = new Dialog(activity, R.style.GhostDialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_vpn_connect, null);
        dialog.setContentView(view);

        Window window = dialog.getWindow();
        if (window != null) {
            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            int targetWidth = (int) (dm.widthPixels * 0.92f);
            int maxWidth = (int) (480 * dm.density);
            if (targetWidth > maxWidth) targetWidth = maxWidth;
            window.setLayout(targetWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvAuthVpnName = view.findViewById(R.id.tvAuthVpnName);
        TextView tvAuthVpnDetails = view.findViewById(R.id.tvAuthVpnDetails);
        EditText etVpnUsername = view.findViewById(R.id.etVpnUsername);
        EditText etVpnPassword = view.findViewById(R.id.etVpnPassword);
        CheckBox cbRememberCredentials = view.findViewById(R.id.cbRememberCredentials);
        Button btnCancel = view.findViewById(R.id.btnCancelConnect);
        Button btnConnect = view.findViewById(R.id.btnConfirmConnect);

        tvAuthVpnName.setText(profile.getName());
        tvAuthVpnDetails.setText(profile.getType() + " • " + profile.getServerAddress());

        // PRE-FILL credentials from last time user filled them
        String lastUser = profile.getLastUsername();
        String lastPass = profile.getLastPassword();
        if (lastUser != null && !lastUser.isEmpty()) {
            etVpnUsername.setText(lastUser);
        }
        if (lastPass != null && !lastPass.isEmpty()) {
            etVpnPassword.setText(lastPass);
        }
        cbRememberCredentials.setChecked(profile.isRememberCredentials());

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConnect.setOnClickListener(v -> {
            String username = etVpnUsername.getText().toString().trim();
            String password = etVpnPassword.getText().toString();
            boolean remember = cbRememberCredentials.isChecked();

            dialog.dismiss();

            // Initiate isolated VPN tunnel connection
            GhostVpnManager.getInstance(activity).connect(activity, profile, username, password, remember);

            if (callback != null) {
                callback.onConnectInitiated(profile);
            }
        });

        dialog.show();
    }
}
