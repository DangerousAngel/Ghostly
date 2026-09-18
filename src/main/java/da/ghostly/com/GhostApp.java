package da.ghostly.com;

import android.app.Application;
import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.WebStorage;
import java.io.File;

/**
 * GhostApp
 * Ghostly Application controller that ensures strict, zero-trace memory-only operations.
 */
public class GhostApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        purgeGhostTraces();
    }

    /**
     * Purges all lingering web traces, cookies, and cached data on app start or shutdown.
     */
    public void purgeGhostTraces() {
        try {
            // Completely disable and remove cookies
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cookieManager.removeAllCookies(null);
                cookieManager.removeSessionCookies(null);
                cookieManager.flush();
            } else {
                cookieManager.removeAllCookie();
                cookieManager.removeSessionCookie();
            }

            // Clear HTML5 web storage & geolocations
            WebStorage.getInstance().deleteAllData();
            GeolocationPermissions.getInstance().clearAll();

            // Clear app cache directory
            clearCacheDir(getCacheDir());
            File externalCache = getExternalCacheDir();
            if (externalCache != null) {
                clearCacheDir(externalCache);
            }
        } catch (Exception ignored) {
        }
    }

    private void clearCacheDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.isDirectory()) {
                        clearCacheDir(child);
                    }
                    child.delete();
                }
            }
        }
    }
}
