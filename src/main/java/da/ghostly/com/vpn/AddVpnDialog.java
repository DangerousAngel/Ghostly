package da.ghostly.com.vpn;

import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
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
import java.net.InetAddress;

/**
 * AddVpnDialog
 * Modal dialog for creating or editing a VPN configuration with live IP/Port identification.
 */
public class AddVpnDialog {

    public interface Callback {
        void onProfileSaved(GhostVpnProfile profile);
    }

    public static void show(Context context, Callback callback) {
        showEdit(context, null, callback);
    }

    public static void showEdit(Context context, GhostVpnProfile existingProfile, Callback callback) {
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

        TextView tvTitle = view.findViewById(R.id.tvAddVpnTitle);
        EditText etVpnName = view.findViewById(R.id.etVpnName);
        Spinner spVpnType = view.findViewById(R.id.spVpnType);
        EditText etServerAddress = view.findViewById(R.id.etServerAddress);
        TextView tvServerIpStatus = view.findViewById(R.id.tvServerIpStatus);
        EditText etIpIdentifier = view.findViewById(R.id.etIpIdentifier);
        View layoutPsk = view.findViewById(R.id.layoutPsk);
        EditText etIpsecPsk = view.findViewById(R.id.etIpsecPsk);
        CheckBox cbPppEncryption = view.findViewById(R.id.cbPppEncryption);

        Button btnCancel = view.findViewById(R.id.btnCancelAddVpn);
        Button btnSave = view.findViewById(R.id.btnSaveVpn);

        // Setup Spinner Adapter
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, GhostVpnProfile.SUPPORTED_TYPES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spVpnType.setAdapter(adapter);

        final Handler mainHandler = new Handler(Looper.getMainLooper());
        final Runnable updateIpIdentifierStatus = () -> {
            if (tvServerIpStatus == null) return;
            String rawServer = etServerAddress.getText().toString().trim();
            String ipIdStr = etIpIdentifier != null ? etIpIdentifier.getText().toString().trim() : "";
            String selectedType = spVpnType.getSelectedItem() != null ? spVpnType.getSelectedItem().toString() : GhostVpnProfile.TYPE_L2TP_IPSEC_PSK;
            int defaultPort = selectedType.contains("PPTP") ? 1723 : 1701;
            int detectedPort = VpnUtils.extractPort(rawServer, VpnUtils.extractCustomPortFromId(ipIdStr, defaultPort));
            String cleanHost = VpnUtils.cleanHost(rawServer);

            if (cleanHost.isEmpty()) {
                tvServerIpStatus.setText("IP IDENTIFIER: AUTO-DETECT • PORT: " + detectedPort + " / MULTI-PORT");
                return;
            }

            tvServerIpStatus.setText("IP IDENTIFIER: " + cleanHost + " • PORT: " + detectedPort + (ipIdStr.isEmpty() ? "" : " • ID: " + ipIdStr));

            // If it's a domain name, asynchronously resolve its IPv4 to display under Server Address
            String[] parts = cleanHost.split("\\.");
            boolean isLiteralIp = (parts.length == 4 && cleanHost.matches("\\d+\\.\\d+\\.\\d+\\.\\d+"));
            if (!isLiteralIp && cleanHost.contains(".")) {
                final String hostSnapshot = cleanHost;
                final int portSnapshot = detectedPort;
                final String idSnapshot = ipIdStr;
                new Thread(() -> {
                    InetAddress resolved = VpnUtils.parseOrResolve(hostSnapshot);
                    if (resolved != null) {
                        final String ip = resolved.getHostAddress();
                        mainHandler.post(() -> {
                            String currentClean = VpnUtils.cleanHost(etServerAddress.getText().toString().trim());
                            if (hostSnapshot.equals(currentClean)) {
                                tvServerIpStatus.setText("IP IDENTIFIER: " + ip + " (" + hostSnapshot + ") • PORT: " + portSnapshot + (idSnapshot.isEmpty() ? "" : " • ID: " + idSnapshot));
                            }
                        });
                    }
                }).start();
            }
        };

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                updateIpIdentifierStatus.run();
            }
        };

        etServerAddress.addTextChangedListener(watcher);
        if (etIpIdentifier != null) {
            etIpIdentifier.addTextChangedListener(watcher);
        }

        spVpnType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                String selected = GhostVpnProfile.SUPPORTED_TYPES[position];
                if (selected.contains("PSK")) {
                    layoutPsk.setVisibility(View.VISIBLE);
                } else {
                    layoutPsk.setVisibility(View.GONE);
                }
                updateIpIdentifierStatus.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Pre-fill fields if editing an existing VPN profile
        if (existingProfile != null) {
            if (tvTitle != null) {
                tvTitle.setText(R.string.edit_vpn_title);
            }
            etVpnName.setText(existingProfile.getName());
            etServerAddress.setText(existingProfile.getServerAddress());
            if (etIpIdentifier != null) {
                etIpIdentifier.setText(existingProfile.getIpIdentifier());
            }
            etIpsecPsk.setText(existingProfile.getIpsecPsk());
            cbPppEncryption.setChecked(existingProfile.isPppEncryption());

            for (int i = 0; i < GhostVpnProfile.SUPPORTED_TYPES.length; i++) {
                if (GhostVpnProfile.SUPPORTED_TYPES[i].equals(existingProfile.getType())) {
                    spVpnType.setSelection(i);
                    break;
                }
            }
            updateIpIdentifierStatus.run();
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String name = etVpnName.getText().toString().trim();
            String server = etServerAddress.getText().toString().trim();
            String ipId = etIpIdentifier != null ? etIpIdentifier.getText().toString().trim() : "";
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

            GhostVpnProfile profileToSave;
            if (existingProfile != null) {
                existingProfile.setName(name);
                existingProfile.setType(type);
                existingProfile.setServerAddress(server);
                existingProfile.setIpIdentifier(ipId);
                existingProfile.setPppEncryption(ppp);
                existingProfile.setIpsecPsk(psk);
                profileToSave = existingProfile;
            } else {
                profileToSave = new GhostVpnProfile(name, type, server, ipId, ppp, psk);
            }

            GhostVpnManager.getInstance(context).addOrUpdateProfile(profileToSave);

            String toastMsg = (existingProfile != null)
                    ? "VPN Profile Updated: " + name
                    : "VPN Profile Created: " + name;
            Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show();
            dialog.dismiss();

            if (callback != null) {
                callback.onProfileSaved(profileToSave);
            }
        });

        dialog.show();
    }
}
