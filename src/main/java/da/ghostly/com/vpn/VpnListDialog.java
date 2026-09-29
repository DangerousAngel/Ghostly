package da.ghostly.com.vpn;

import android.app.Activity;
import android.app.Dialog;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import da.ghostly.com.R;
import java.util.List;

/**
 * VpnListDialog
 * Modal list of in-app isolated VPN profiles with live status, connect/disconnect, and delete controls.
 */
public class VpnListDialog implements GhostVpnManager.StateListener {

    private final Activity activity;
    private final Dialog dialog;
    private final LinearLayout profilesContainer;
    private final TextView tvEmptyState;
    private final GhostVpnManager vpnManager;

    public static void show(Activity activity) {
        new VpnListDialog(activity).show();
    }

    public VpnListDialog(Activity activity) {
        this.activity = activity;
        this.vpnManager = GhostVpnManager.getInstance(activity);

        this.dialog = new Dialog(activity, R.style.GhostDialog);
        this.dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_vpn_list, null);
        this.dialog.setContentView(view);

        Window window = dialog.getWindow();
        if (window != null) {
            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            int targetWidth = (int) (dm.widthPixels * 0.94f);
            int maxWidth = (int) (540 * dm.density);
            if (targetWidth > maxWidth) targetWidth = maxWidth;
            int targetHeight = (int) (dm.heightPixels * 0.82f);
            window.setLayout(targetWidth, targetHeight);
        }

        this.profilesContainer = view.findViewById(R.id.profilesContainer);
        this.tvEmptyState = view.findViewById(R.id.tvEmptyState);

        Button btnHeaderAddVpn = view.findViewById(R.id.btnHeaderAddVpn);
        btnHeaderAddVpn.setOnClickListener(v -> {
            AddVpnDialog.show(activity, profile -> renderProfiles());
        });

        Button btnClose = view.findViewById(R.id.btnCloseVpnList);
        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.setOnDismissListener(d -> vpnManager.removeListener(this));
        vpnManager.addListener(this);
    }

    public void show() {
        renderProfiles();
        dialog.show();
    }

    private void renderProfiles() {
        profilesContainer.removeAllViews();
        List<GhostVpnProfile> profiles = vpnManager.getProfiles();

        if (profiles.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            return;
        } else {
            tvEmptyState.setVisibility(View.GONE);
        }

        LayoutInflater inflater = LayoutInflater.from(activity);
        GhostVpnProfile activeProfile = vpnManager.getActiveProfile();
        int state = vpnManager.getCurrentState();

        for (final GhostVpnProfile p : profiles) {
            View itemView = inflater.inflate(R.layout.item_vpn_profile, profilesContainer, false);

            TextView tvName = itemView.findViewById(R.id.tvProfileName);
            TextView tvStatus = itemView.findViewById(R.id.tvProfileStatus);
            TextView tvType = itemView.findViewById(R.id.tvProfileType);
            TextView tvServer = itemView.findViewById(R.id.tvProfileServer);
            TextView tvEncryption = itemView.findViewById(R.id.tvProfileEncryption);
            ImageButton btnEdit = itemView.findViewById(R.id.btnEditProfile);
            ImageButton btnDelete = itemView.findViewById(R.id.btnDeleteProfile);
            Button btnConnectToggle = itemView.findViewById(R.id.btnConnectToggle);

            tvName.setText(p.getName());
            tvType.setText(p.getType());

            String serverDisplay = p.getServerAddress();
            if (p.getIpIdentifier() != null && !p.getIpIdentifier().trim().isEmpty()) {
                serverDisplay = serverDisplay + " [ID: " + p.getIpIdentifier().trim() + "]";
            }
            tvServer.setText(serverDisplay);
            tvEncryption.setText(p.isPppEncryption() ? "PPP Encryption (MPPE): Enabled" : "PPP Encryption: Disabled");

            boolean isCurrentActive = (activeProfile != null && activeProfile.getId().equals(p.getId()));

            if (isCurrentActive) {
                if (state == GhostVpnManager.STATE_CONNECTED) {
                    tvStatus.setText("CONNECTED");
                    tvStatus.setTextColor(0xFFFFFFFF);
                    tvStatus.setBackgroundResource(R.drawable.bg_vpn_badge);
                    tvStatus.setTextColor(0xFF000000); // Inverse B&W badge
                    btnConnectToggle.setText(R.string.disconnect);
                    btnConnectToggle.setBackgroundResource(R.drawable.bg_button_dark);
                } else if (state == GhostVpnManager.STATE_CONNECTING) {
                    tvStatus.setText("CONNECTING...");
                    tvStatus.setTextColor(0xFFFFFFFF);
                    btnConnectToggle.setText("Connecting...");
                    btnConnectToggle.setEnabled(false);
                }
            } else {
                tvStatus.setText("DISCONNECTED");
                tvStatus.setTextColor(0xFF666666);
                btnConnectToggle.setText(R.string.connect);
                btnConnectToggle.setBackgroundResource(R.drawable.bg_square_purge_btn);
            }

            btnConnectToggle.setOnClickListener(v -> {
                if (isCurrentActive && state == GhostVpnManager.STATE_CONNECTED) {
                    vpnManager.stopVpn();
                    renderProfiles();
                } else {
                    VpnConnectDialog.show(activity, p, connectedProfile -> renderProfiles());
                }
            });

            if (btnEdit != null) {
                btnEdit.setOnClickListener(v -> {
                    AddVpnDialog.showEdit(activity, p, updatedProfile -> renderProfiles());
                });
            }

            btnDelete.setOnClickListener(v -> {
                vpnManager.deleteProfile(p.getId());
                renderProfiles();
            });

            profilesContainer.addView(itemView);
        }
    }

    @Override
    public void onVpnStateChanged(int state, GhostVpnProfile activeProfile) {
        activity.runOnUiThread(this::renderProfiles);
    }
}
