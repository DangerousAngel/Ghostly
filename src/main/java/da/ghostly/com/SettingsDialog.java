package da.ghostly.com;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import da.ghostly.com.vpn.AddVpnDialog;
import da.ghostly.com.vpn.GhostVpnManager;
import da.ghostly.com.vpn.GhostVpnProfile;
import da.ghostly.com.vpn.VpnListDialog;

/**
 * SettingsDialog
 * Dark-mode modal dialog for managing Ghostly's privacy settings with responsive layout and VPN controls.
 */
public class SettingsDialog {

    public interface Callback {
        void onSettingsChanged();
        void onInstantPurgeRequested();
    }

    public static void show(Context context, GhostSettings settings, Callback callback) {
        Dialog dialog = new Dialog(context, R.style.GhostDialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_settings, null);
        dialog.setContentView(view);

        // Search engine radio buttons
        RadioGroup searchEngineGroup = view.findViewById(R.id.searchEngineRadioGroup);
        RadioButton rbDuckDuckGo = view.findViewById(R.id.rbDuckDuckGo);
        RadioButton rbStartpage = view.findViewById(R.id.rbStartpage);
        RadioButton rbGoogle = view.findViewById(R.id.rbGoogle);
        RadioButton rbBing = view.findViewById(R.id.rbBing);

        String currentEngine = settings.getSearchEngine();
        switch (currentEngine) {
            case GhostSettings.ENGINE_STARTPAGE:
                rbStartpage.setChecked(true);
                break;
            case GhostSettings.ENGINE_GOOGLE:
                rbGoogle.setChecked(true);
                break;
            case GhostSettings.ENGINE_BING:
                rbBing.setChecked(true);
                break;
            case GhostSettings.ENGINE_DUCKDUCKGO:
            default:
                rbDuckDuckGo.setChecked(true);
                break;
        }

        searchEngineGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbStartpage) {
                settings.setSearchEngine(GhostSettings.ENGINE_STARTPAGE);
            } else if (checkedId == R.id.rbGoogle) {
                settings.setSearchEngine(GhostSettings.ENGINE_GOOGLE);
            } else if (checkedId == R.id.rbBing) {
                settings.setSearchEngine(GhostSettings.ENGINE_BING);
            } else {
                settings.setSearchEngine(GhostSettings.ENGINE_DUCKDUCKGO);
            }
            if (callback != null) callback.onSettingsChanged();
        });

        // Advanced Privacy Switches
        Switch switchBlockCookies = view.findViewById(R.id.switchBlockCookies);
        Switch switchBlockSessions = view.findViewById(R.id.switchBlockSessions);
        Switch switchBlockCache = view.findViewById(R.id.switchBlockCache);
        Switch switchDntHeaders = view.findViewById(R.id.switchDntHeaders);
        Switch switchAutoPurgeExit = view.findViewById(R.id.switchAutoPurgeExit);

        switchBlockCookies.setChecked(settings.isBlockCookies());
        switchBlockSessions.setChecked(settings.isBlockSessions());
        switchBlockCache.setChecked(settings.isBlockCache());
        switchDntHeaders.setChecked(settings.isDntHeaders());
        switchAutoPurgeExit.setChecked(settings.isAutoPurgeExit());

        switchBlockCookies.setOnCheckedChangeListener((btn, isChecked) -> {
            settings.setBlockCookies(isChecked);
            if (callback != null) callback.onSettingsChanged();
        });

        switchBlockSessions.setOnCheckedChangeListener((btn, isChecked) -> {
            settings.setBlockSessions(isChecked);
            if (callback != null) callback.onSettingsChanged();
        });

        switchBlockCache.setOnCheckedChangeListener((btn, isChecked) -> {
            settings.setBlockCache(isChecked);
            if (callback != null) callback.onSettingsChanged();
        });

        switchDntHeaders.setOnCheckedChangeListener((btn, isChecked) -> {
            settings.setDntHeaders(isChecked);
            if (callback != null) callback.onSettingsChanged();
        });

        switchAutoPurgeExit.setOnCheckedChangeListener((btn, isChecked) -> {
            settings.setAutoPurgeExit(isChecked);
            if (callback != null) callback.onSettingsChanged();
        });

        // Content Controls
        Switch switchBlockJs = view.findViewById(R.id.switchBlockJs);
        Switch switchBlockImages = view.findViewById(R.id.switchBlockImages);
        Switch switchDesktopMode = view.findViewById(R.id.switchDesktopMode);

        switchBlockJs.setChecked(settings.isBlockJs());
        switchBlockImages.setChecked(settings.isBlockImages());
        switchDesktopMode.setChecked(settings.isDesktopMode());

        switchBlockJs.setOnCheckedChangeListener((btn, isChecked) -> {
            settings.setBlockJs(isChecked);
            if (callback != null) callback.onSettingsChanged();
        });

        switchBlockImages.setOnCheckedChangeListener((btn, isChecked) -> {
            settings.setBlockImages(isChecked);
            if (callback != null) callback.onSettingsChanged();
        });

        switchDesktopMode.setOnCheckedChangeListener((btn, isChecked) -> {
            settings.setDesktopMode(isChecked);
            if (callback != null) callback.onSettingsChanged();
        });

        // Window responsiveness (adapts to small phones, landscape, tablets)
        Window window = dialog.getWindow();
        if (window != null) {
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            int screenWidth = dm.widthPixels;
            int screenHeight = dm.heightPixels;
            int targetWidth = (int) (screenWidth * 0.94f);
            int maxWidthPx = (int) (540 * dm.density);
            if (targetWidth > maxWidthPx) {
                targetWidth = maxWidthPx;
            }
            int targetHeight = (int) (screenHeight * 0.86f);
            window.setLayout(targetWidth, targetHeight);
        }

        // In-App Isolated VPN Controls
        final TextView tvSettingsVpnStatus = view.findViewById(R.id.tvSettingsVpnStatus);
        final TextView tvSettingsVpnBadge = view.findViewById(R.id.tvSettingsVpnBadge);
        Button btnSettingsAddVpn = view.findViewById(R.id.btnSettingsAddVpn);
        Button btnSettingsManageVpn = view.findViewById(R.id.btnSettingsManageVpn);

        final GhostVpnManager vpnManager = GhostVpnManager.getInstance(context);

        final Runnable refreshVpnStatus = () -> {
            int state = vpnManager.getCurrentState();
            GhostVpnProfile active = vpnManager.getActiveProfile();
            if (state == GhostVpnManager.STATE_CONNECTED && active != null) {
                if (tvSettingsVpnStatus != null) tvSettingsVpnStatus.setText(active.getName() + " (" + active.getType() + ")");
                if (tvSettingsVpnBadge != null) {
                    tvSettingsVpnBadge.setText("ACTIVE");
                    tvSettingsVpnBadge.setBackgroundResource(R.drawable.bg_vpn_badge);
                    tvSettingsVpnBadge.setTextColor(0xFF000000);
                }
            } else if (state == GhostVpnManager.STATE_CONNECTING) {
                if (tvSettingsVpnStatus != null) tvSettingsVpnStatus.setText("Tunnel connecting...");
                if (tvSettingsVpnBadge != null) {
                    tvSettingsVpnBadge.setText("CONNECTING");
                    tvSettingsVpnBadge.setBackgroundResource(R.drawable.bg_terminal_console);
                    tvSettingsVpnBadge.setTextColor(0xFFFFFFFF);
                }
            } else {
                if (tvSettingsVpnStatus != null) tvSettingsVpnStatus.setText("Isolated Tunnel: Disabled");
                if (tvSettingsVpnBadge != null) {
                    tvSettingsVpnBadge.setText("INACTIVE");
                    tvSettingsVpnBadge.setBackgroundResource(R.drawable.bg_terminal_console);
                    tvSettingsVpnBadge.setTextColor(0xFF888888);
                }
            }
        };

        refreshVpnStatus.run();

        if (btnSettingsAddVpn != null) {
            btnSettingsAddVpn.setOnClickListener(v -> {
                AddVpnDialog.show(context, profile -> refreshVpnStatus.run());
            });
        }

        if (btnSettingsManageVpn != null) {
            btnSettingsManageVpn.setOnClickListener(v -> {
                if (context instanceof Activity) {
                    VpnListDialog.show((Activity) context);
                }
            });
        }

        // Instant Purge Button (Square Container)
        Button btnInstantPurge = view.findViewById(R.id.btnInstantPurge);
        btnInstantPurge.setOnClickListener(v -> {
            dialog.dismiss();
            if (callback != null) callback.onInstantPurgeRequested();
        });

        // Author Links (Created by DangerousAngel)
        View.OnClickListener openAuthorUrlListener = v -> {
            dialog.dismiss();
            String authorUrl = context.getString(R.string.created_by_url);
            if (context instanceof MainActivity) {
                ((MainActivity) context).loadUrl(authorUrl);
            }
        };

        View tvCreatedBy = view.findViewById(R.id.tvCreatedBy);
        if (tvCreatedBy != null) {
            tvCreatedBy.setOnClickListener(openAuthorUrlListener);
        }

        View tvAuthorLink = view.findViewById(R.id.tvAuthorLink);
        if (tvAuthorLink != null) {
            tvAuthorLink.setOnClickListener(openAuthorUrlListener);
        }

        // Close Button
        Button btnClose = view.findViewById(R.id.btnSettingsClose);
        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}
