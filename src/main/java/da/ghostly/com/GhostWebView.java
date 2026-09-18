package da.ghostly.com;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * GhostWebView
 * Specialized WebView enforcing strict Zero-Cookie, Zero-Session, and In-Memory Isolation.
 */
public class GhostWebView extends WebView {

    private static final String DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
    private String defaultUserAgent;
    private GhostSettings ghostSettings;

    public GhostWebView(Context context) {
        super(context);
        initGhostSettings();
    }

    private void initGhostSettings() {
        // Pure pitch black background to eliminate white flashes
        setBackgroundColor(Color.parseColor("#000000"));

        WebSettings settings = getSettings();
        defaultUserAgent = settings.getUserAgentString();

        // Viewport and zooming
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportMultipleWindows(false);
        settings.setSaveFormData(false);
        settings.setSavePassword(false);
        settings.setGeolocationEnabled(false);

        // Force Dark Mode if supported (Android Q / API 29+)
        enableDarkModeIfSupported(settings);
    }

    private void enableDarkModeIfSupported(WebSettings settings) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                Method method = settings.getClass().getMethod("setForceDark", int.class);
                // WebSettings.FORCE_DARK_ON = 2
                method.invoke(settings, 2);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Updates privacy settings based on user preferences.
     */
    public void applyPreferences(GhostSettings ghostSettings) {
        this.ghostSettings = ghostSettings;
        WebSettings settings = getSettings();

        // 1. Cookie Policy
        try {
            CookieManager cookieManager = CookieManager.getInstance();
            boolean blockCookies = ghostSettings.isBlockCookies();
            cookieManager.setAcceptCookie(!blockCookies);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cookieManager.setAcceptThirdPartyCookies(this, !blockCookies);
            }
            if (blockCookies) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    cookieManager.removeAllCookies(null);
                    cookieManager.flush();
                } else {
                    cookieManager.removeAllCookie();
                }
            }
        } catch (Exception ignored) {
        }

        // 2. Session & Storage Policy
        boolean blockSessions = ghostSettings.isBlockSessions();
        settings.setDomStorageEnabled(!blockSessions);
        settings.setDatabaseEnabled(!blockSessions);

        // 3. Cache Policy
        if (ghostSettings.isBlockCache()) {
            settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        } else {
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        }

        // 4. Content Controls
        settings.setJavaScriptEnabled(!ghostSettings.isBlockJs());
        settings.setBlockNetworkImage(ghostSettings.isBlockImages());

        // 5. Desktop UA
        if (ghostSettings.isDesktopMode()) {
            settings.setUserAgentString(DESKTOP_USER_AGENT);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
        } else {
            settings.setUserAgentString(defaultUserAgent);
        }
    }

    /**
     * Loads URL with DNT (Do Not Track) and Sec-GPC (Global Privacy Control) headers if enabled.
     */
    public void loadUrlWithPrivacy(String url) {
        if (ghostSettings != null && ghostSettings.isDntHeaders()) {
            Map<String, String> headers = new HashMap<>();
            headers.put("DNT", "1");
            headers.put("Sec-GPC", "1");
            loadUrl(url, headers);
        } else {
            loadUrl(url);
        }
    }

    @Override
    public void destroy() {
        try {
            stopLoading();
            loadUrl("about:blank");
            clearHistory();
            clearFormData();
            clearCache(true);
            clearSslPreferences();
            removeAllViews();
        } catch (Exception ignored) {
        }
        super.destroy();
    }
}
