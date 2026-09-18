package da.ghostly.com;

import android.content.Context;
import android.graphics.Bitmap;
import java.util.UUID;

/**
 * GhostTab
 * Model representing an ephemeral, in-memory browser tab session.
 */
public class GhostTab {

    private final String id;
    private final GhostWebView webView;
    private String title;
    private String url;
    private Bitmap favicon;

    public GhostTab(Context context, GhostSettings settings) {
        this.id = UUID.randomUUID().toString();
        this.webView = new GhostWebView(context);
        this.webView.applyPreferences(settings);
        this.title = "New Ghost Tab";
        this.url = "";
        this.favicon = null;
    }

    public String getId() {
        return id;
    }

    public GhostWebView getWebView() {
        return webView;
    }

    public String getTitle() {
        return (title != null && !title.isEmpty()) ? title : "Ghost Tab";
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return (url != null) ? url : "";
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Bitmap getFavicon() {
        return favicon;
    }

    public void setFavicon(Bitmap favicon) {
        this.favicon = favicon;
    }

    public boolean isHome() {
        return url == null || url.isEmpty() || url.equalsIgnoreCase("about:blank");
    }

    /**
     * Completely destroys the WebView and releases all volatile memory.
     */
    public void destroy() {
        if (webView != null) {
            webView.destroy();
        }
        if (favicon != null && !favicon.isRecycled()) {
            favicon.recycle();
            favicon = null;
        }
    }
}
