package da.ghostly.com.vpn;

import android.app.Dialog;
import android.content.Context;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import da.ghostly.com.R;

/**
 * AddVpnDialog
 * Modal dialog for configuring a new in-app isolated VPN connection.
 */
public class AddVpnDialog {

    public interface Callback {
        void onProfileSaved(GhostVpnProfile profile);
    }

    public static void show(Context context, Callback callback) {
        Dialog dialog = new Dialog(context, R.style.GhostDialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_add_vpn, null);
        dialog.setContentView(view);

        // Apply responsive window layout
        Window window = dialog.getWindow();
        if (window != null) {
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            int targetWidth = (int) (dm.widthPixels * 0.92f);
            int maxWidth = (int) (520 * dm.density);
            if (targetWidth > maxWidth) targetWidth = maxWidth;
            window.setLayout(targetWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        EditText etVpnName = view.findViewById(R.id.etVpnName);
        Spinner spVpnType = view.findViewById(R.id.spVpnType);
        EditText etServerAddress = view.findViewById(R.id.etServerAddress);
        View layoutPsk = view.findViewById(R.id.layoutPsk);
        EditText etIpsecPsk = view.findViewById(R.id.etIpsecPsk);
        CheckBox cbPppEncryption = view.findViewById(R.id.cbPppEncryption);

        Button btnCancel = view.findViewById(R.id.btnCancelAddVpn);
        Button btnSave = view.findViewById(R.id.btnSaveVpn);

        // Setup Spinner Adapter
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, GhostVpnProfile.SUPPORTED_TYPES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spVpnType.setAdapter(adapter);

        spVpnType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                String selected = GhostVpnProfile.SUPPORTED_TYPES[position];
                if (selected.contains("PSK")) {
                    layoutPsk.setVisibility(View.VISIBLE);
                } else {
                    layoutPsk.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String name = etVpnName.getText().toString().trim();
            String server = etServerAddress.getText().toString().trim();
            String type = spVpnType.getSelectedItem().toString();
            boolean ppp = cbPppEncryption.isChecked();
            String psk = etIpsecPsk.getText().toString().trim();

            if (name.isEmpty()) {
                etVpnName.setError("VPN Name is required");
                etVpnName.requestFocus();
                return;
            }

            if (server.isEmpty()) {
                etServerAddress.setError("Server Address is required");
                etServerAddress.requestFocus();
                return;
            }

            GhostVpnProfile profile = new GhostVpnProfile(name, type, server, ppp, psk);
            GhostVpnManager.getInstance(context).addOrUpdateProfile(profile);

            Toast.makeText(context, "VPN Profile Created: " + name, Toast.LENGTH_SHORT).show();
            dialog.dismiss();

            if (callback != null) {
                callback.onProfileSaved(profile);
            }
        });

        dialog.show();
    }
}
