package da.ghostly.com;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;

/**
 * SettingsDialog
 * Dark-mode modal dialog for managing Ghostly's privacy settings.
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
