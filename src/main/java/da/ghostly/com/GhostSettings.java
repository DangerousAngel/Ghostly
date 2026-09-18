package da.ghostly.com;

import android.content.Context;
import android.content.SharedPreferences;
import java.net.URLEncoder;

/**
 * GhostSettings
 * Manages Ghostly privacy configurations and search engine selections.
 */
public class GhostSettings {

    private static final String PREF_NAME = "ghostly_prefs";

    public static final String KEY_SEARCH_ENGINE = "search_engine";
    public static final String KEY_BLOCK_COOKIES = "block_cookies";
    public static final String KEY_BLOCK_SESSIONS = "block_sessions";
    public static final String KEY_BLOCK_CACHE = "block_cache";
    public static final String KEY_DNT_HEADERS = "dnt_headers";
    public static final String KEY_AUTO_PURGE_EXIT = "auto_purge_exit";
    public static final String KEY_BLOCK_JS = "block_js";
    public static final String KEY_BLOCK_IMAGES = "block_images";
    public static final String KEY_DESKTOP_MODE = "desktop_mode";

    public static final String ENGINE_DUCKDUCKGO = "duckduckgo";
    public static final String ENGINE_STARTPAGE = "startpage";
    public static final String ENGINE_GOOGLE = "google";
    public static final String ENGINE_BING = "bing";

    private final SharedPreferences prefs;

    public GhostSettings(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public String getSearchEngine() {
        return prefs.getString(KEY_SEARCH_ENGINE, ENGINE_DUCKDUCKGO);
    }

    public void setSearchEngine(String engine) {
        prefs.edit().putString(KEY_SEARCH_ENGINE, engine).apply();
    }

    // Advanced Ghost Privacy Toggles
    public boolean isBlockCookies() {
        return prefs.getBoolean(KEY_BLOCK_COOKIES, true);
    }

    public void setBlockCookies(boolean block) {
        prefs.edit().putBoolean(KEY_BLOCK_COOKIES, block).apply();
    }

    public boolean isBlockSessions() {
        return prefs.getBoolean(KEY_BLOCK_SESSIONS, true);
    }

    public void setBlockSessions(boolean block) {
        prefs.edit().putBoolean(KEY_BLOCK_SESSIONS, block).apply();
    }

    public boolean isBlockCache() {
        return prefs.getBoolean(KEY_BLOCK_CACHE, true);
    }

    public void setBlockCache(boolean block) {
        prefs.edit().putBoolean(KEY_BLOCK_CACHE, block).apply();
    }

    public boolean isDntHeaders() {
        return prefs.getBoolean(KEY_DNT_HEADERS, true);
    }

    public void setDntHeaders(boolean enabled) {
        prefs.edit().putBoolean(KEY_DNT_HEADERS, enabled).apply();
    }

    public boolean isAutoPurgeExit() {
        return prefs.getBoolean(KEY_AUTO_PURGE_EXIT, true);
    }

    public void setAutoPurgeExit(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_PURGE_EXIT, enabled).apply();
    }

    // Content Controls
    public boolean isBlockJs() {
        return prefs.getBoolean(KEY_BLOCK_JS, false);
    }

    public void setBlockJs(boolean block) {
        prefs.edit().putBoolean(KEY_BLOCK_JS, block).apply();
    }

    public boolean isBlockImages() {
        return prefs.getBoolean(KEY_BLOCK_IMAGES, false);
    }

    public void setBlockImages(boolean block) {
        prefs.edit().putBoolean(KEY_BLOCK_IMAGES, block).apply();
    }

    public boolean isDesktopMode() {
        return prefs.getBoolean(KEY_DESKTOP_MODE, false);
    }

    public void setDesktopMode(boolean desktop) {
        prefs.edit().putBoolean(KEY_DESKTOP_MODE, desktop).apply();
    }

    /**
     * Constructs search URL for a given query based on the selected search engine.
     */
    public String buildSearchUrl(String query) {
        try {
            String encodedQuery = URLEncoder.encode(query.trim(), "UTF-8");
            String engine = getSearchEngine();
            switch (engine) {
                case ENGINE_STARTPAGE:
                    return "https://www.startpage.com/sp/search?query=" + encodedQuery;
                case ENGINE_GOOGLE:
                    return "https://www.google.com/search?q=" + encodedQuery;
                case ENGINE_BING:
                    return "https://www.bing.com/search?q=" + encodedQuery;
                case ENGINE_DUCKDUCKGO:
                default:
                    return "https://duckduckgo.com/?q=" + encodedQuery;
            }
        } catch (Exception e) {
            return "https://duckduckgo.com/?q=" + query;
        }
    }
}
