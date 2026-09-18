package da.ghostly.com;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * GhostWebClient
 * Custom WebViewClient with zero-cookie enforcement and safe SSL error management.
 */
public class GhostWebClient extends WebViewClient {

    public interface Callback {
        void onPageStarted(WebView view, String url, Bitmap favicon);
        void onPageFinished(WebView view, String url);
        void onUrlChanged(String url);
    }

    private final Context context;
    private final Callback callback;

    public GhostWebClient(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        if (url == null) return false;

        // Keep standard web traffic in GhostWebView
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("about:") || url.startsWith("file:")) {
            return false;
        }

        // Handle external protocols (mailto, tel, intent, sms)
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception ignored) {
            return true;
        }
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);

        // Immediate cookie neutralization
        try {
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cookieManager.removeAllCookies(null);
                cookieManager.flush();
            }
        } catch (Exception ignored) {
        }

        if (callback != null) {
            callback.onPageStarted(view, url, favicon);
            callback.onUrlChanged(url);
        }
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);

        // Re-flush and eradicate any cookies received in headers
        try {
            CookieManager cookieManager = CookieManager.getInstance();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cookieManager.removeAllCookies(null);
                cookieManager.flush();
            }
        } catch (Exception ignored) {
        }

        if (callback != null) {
            callback.onPageFinished(view, url);
            callback.onUrlChanged(url);
        }
    }

    @Override
    public void onReceivedSslError(WebView view, final SslErrorHandler handler, SslError error) {
        new AlertDialog.Builder(context, R.style.GhostDialog)
                .setTitle("SSL Warning")
                .setMessage(R.string.ssl_warning)
                .setPositiveButton("Proceed", (dialog, which) -> handler.proceed())
                .setNegativeButton("Cancel", (dialog, which) -> handler.cancel())
                .setCancelable(false)
                .show();
    }
}
