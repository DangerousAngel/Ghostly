package da.ghostly.com;

import android.graphics.Bitmap;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

/**
 * GhostWebChromeClient
 * Handles page title, favicon, and loading progress updates.
 */
public class GhostWebChromeClient extends WebChromeClient {

    public interface Callback {
        void onProgressChanged(WebView view, int newProgress);
        void onReceivedTitle(WebView view, String title);
        void onReceivedIcon(WebView view, Bitmap icon);
    }

    private final Callback callback;

    public GhostWebChromeClient(Callback callback) {
        this.callback = callback;
    }

    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        super.onProgressChanged(view, newProgress);
        if (callback != null) {
            callback.onProgressChanged(view, newProgress);
        }
    }

    @Override
    public void onReceivedTitle(WebView view, String title) {
        super.onReceivedTitle(view, title);
        if (callback != null) {
            callback.onReceivedTitle(view, title);
        }
    }

    @Override
    public void onReceivedIcon(WebView view, Bitmap icon) {
        super.onReceivedIcon(view, icon);
        if (callback != null) {
            callback.onReceivedIcon(view, icon);
        }
    }
}
