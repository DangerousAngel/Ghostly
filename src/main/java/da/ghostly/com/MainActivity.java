package da.ghostly.com;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * MainActivity
 * Flagship controller for Ghostly browser with pure monochrome dark mode and strict ghost privacy.
 */
public class MainActivity extends Activity {

    private final List<GhostTab> tabs = new ArrayList<>();
    private int activeTabIndex = -1;
    private GhostSettings settings;

    // Top Bar UI
    private ImageButton shieldBtn;
    private ImageView lockIcon;
    private EditText urlEditText;
    private ImageButton refreshOrStopBtn;
    private ProgressBar progressBar;

    // Content UI
    private FrameLayout tabContainer;
    private View homeLayout;

    // Find In Page UI
    private View findInPageBar;
    private EditText findQueryEditText;
    private TextView findMatchesText;
    private ImageButton findPrevBtn;
    private ImageButton findNextBtn;
    private ImageButton findCloseBtn;

    // Bottom Bar UI
    private ImageButton backBtn;
    private ImageButton forwardBtn;
    private ImageButton panicWipeBtn;
    private View tabsBtnLayout;
    private TextView tabsCountBadge;
    private ImageButton settingsBtn;

    private boolean isPageLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settings = new GhostSettings(this);
        initViews();
        setupListeners();

        // Handle incoming VIEW intent (e.g. opening link from another app)
        String initialUrl = null;
        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri data = intent.getData();
            if (data != null) {
                initialUrl = data.toString();
            }
        }

        // Start with initial tab
        addNewTab(initialUrl);
    }

    private void initViews() {
        shieldBtn = findViewById(R.id.shieldBtn);
        lockIcon = findViewById(R.id.lockIcon);
        urlEditText = findViewById(R.id.urlEditText);
        refreshOrStopBtn = findViewById(R.id.refreshOrStopBtn);
        progressBar = findViewById(R.id.progressBar);

        tabContainer = findViewById(R.id.tabContainer);
        homeLayout = findViewById(R.id.homeLayout);

        findInPageBar = findViewById(R.id.findInPageBar);
        findQueryEditText = findViewById(R.id.findQueryEditText);
        findMatchesText = findViewById(R.id.findMatchesText);
        findPrevBtn = findViewById(R.id.findPrevBtn);
        findNextBtn = findViewById(R.id.findNextBtn);
        findCloseBtn = findViewById(R.id.findCloseBtn);

        backBtn = findViewById(R.id.backBtn);
        forwardBtn = findViewById(R.id.forwardBtn);
        panicWipeBtn = findViewById(R.id.panicWipeBtn);
        tabsBtnLayout = findViewById(R.id.tabsBtnLayout);
        tabsCountBadge = findViewById(R.id.tabsCountBadge);
        settingsBtn = findViewById(R.id.settingsBtn);
    }

    private void setupListeners() {
        // Shield Info Dialog
        shieldBtn.setOnClickListener(v -> showShieldInfoDialog());

        // Address Bar Action
        urlEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                loadEnteredInput(urlEditText.getText().toString());
                hideKeyboard();
                return true;
            }
            return false;
        });

        // Refresh / Stop
        refreshOrStopBtn.setOnClickListener(v -> {
            GhostTab activeTab = getActiveTab();
            if (activeTab != null) {
                if (isPageLoading) {
                    activeTab.getWebView().stopLoading();
                } else {
                    activeTab.getWebView().reload();
                }
            }
        });

        // Navigation Buttons
        backBtn.setOnClickListener(v -> {
            GhostTab activeTab = getActiveTab();
            if (activeTab != null && activeTab.getWebView().canGoBack()) {
                activeTab.getWebView().goBack();
            } else {
                showHomeScreen();
            }
        });

        forwardBtn.setOnClickListener(v -> {
            GhostTab activeTab = getActiveTab();
            if (activeTab != null && activeTab.getWebView().canGoForward()) {
                activeTab.getWebView().goForward();
            }
        });

        // Ghost Panic / Instant Wipe
        panicWipeBtn.setOnClickListener(v -> confirmAndPurgeData());

        // Tabs Switcher
        tabsBtnLayout.setOnClickListener(v -> showTabsDialog());

        // Settings Dialog
        settingsBtn.setOnClickListener(v -> showSettingsDialog());

        // Home Screen Shortcuts
        setupHomeScreenShortcuts();

        // Find In Page Listeners
        setupFindInPageListeners();
    }

    private void setupHomeScreenShortcuts() {
        View shortcutDuckDuckGo = findViewById(R.id.shortcutDuckDuckGo);
        if (shortcutDuckDuckGo != null) {
            shortcutDuckDuckGo.setOnClickListener(v -> loadUrlInActiveTab("https://duckduckgo.com"));
        }

        View shortcutYoutube = findViewById(R.id.shortcutYoutube);
        if (shortcutYoutube != null) {
            shortcutYoutube.setOnClickListener(v -> loadUrlInActiveTab("https://www.youtube.com"));
        }

        View shortcutWikipedia = findViewById(R.id.shortcutWikipedia);
        if (shortcutWikipedia != null) {
            shortcutWikipedia.setOnClickListener(v -> loadUrlInActiveTab("https://www.wikipedia.org"));
        }

        View shortcutPrivacyGuides = findViewById(R.id.shortcutPrivacyGuides);
        if (shortcutPrivacyGuides != null) {
            shortcutPrivacyGuides.setOnClickListener(v -> loadUrlInActiveTab("https://www.privacyguides.org"));
        }
    }

    private void setupFindInPageListeners() {
        findCloseBtn.setOnClickListener(v -> {
            findInPageBar.setVisibility(View.GONE);
            GhostTab activeTab = getActiveTab();
            if (activeTab != null) {
                activeTab.getWebView().clearMatches();
            }
            hideKeyboard();
        });

        findQueryEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                GhostTab activeTab = getActiveTab();
                if (activeTab != null) {
                    if (s.length() > 0) {
                        activeTab.getWebView().findAllAsync(s.toString());
                    } else {
                        activeTab.getWebView().clearMatches();
                        findMatchesText.setText("");
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        findPrevBtn.setOnClickListener(v -> {
            GhostTab activeTab = getActiveTab();
            if (activeTab != null) {
                activeTab.getWebView().findNext(false);
            }
        });

        findNextBtn.setOnClickListener(v -> {
            GhostTab activeTab = getActiveTab();
            if (activeTab != null) {
                activeTab.getWebView().findNext(true);
            }
        });
    }

    // ==========================================
    // Multi-Tab Management
    // ==========================================

    public void addNewTab(String url) {
        final GhostTab tab = new GhostTab(this, settings);
        final GhostWebView webView = tab.getWebView();

        webView.setWebViewClient(new GhostWebClient(this, new GhostWebClient.Callback() {
            @Override
            public void onPageStarted(WebView view, String pageUrl, Bitmap favicon) {
                if (tab == getActiveTab()) {
                    isPageLoading = true;
                    progressBar.setVisibility(View.VISIBLE);
                    refreshOrStopBtn.setImageResource(R.drawable.ic_close);
                    updateLockIcon(pageUrl);
                    urlEditText.setText(pageUrl);
                    homeLayout.setVisibility(View.GONE);
                }
                tab.setUrl(pageUrl);
            }

            @Override
            public void onPageFinished(WebView view, String pageUrl) {
                if (tab == getActiveTab()) {
                    isPageLoading = false;
                    progressBar.setVisibility(View.GONE);
                    refreshOrStopBtn.setImageResource(R.drawable.ic_refresh);
                    updateLockIcon(pageUrl);
                    updateNavigationButtons();
                }
                tab.setUrl(pageUrl);
            }

            @Override
            public void onUrlChanged(String newUrl) {
                tab.setUrl(newUrl);
                if (tab == getActiveTab()) {
                    urlEditText.setText(newUrl);
                    updateLockIcon(newUrl);
                }
            }
        }));

        webView.setWebChromeClient(new GhostWebChromeClient(new GhostWebChromeClient.Callback() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (tab == getActiveTab()) {
                    progressBar.setProgress(newProgress);
                    if (newProgress >= 100) {
                        progressBar.setVisibility(View.GONE);
                        isPageLoading = false;
                        refreshOrStopBtn.setImageResource(R.drawable.ic_refresh);
                    }
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                tab.setTitle(title);
            }

            @Override
            public void onReceivedIcon(WebView view, Bitmap icon) {
                tab.setFavicon(icon);
            }
        }));

        // Context menu for links and images
        registerForContextMenu(webView);

        // Add to view container (initially invisible)
        webView.setVisibility(View.GONE);
        tabContainer.addView(webView);

        tabs.add(tab);
        switchToTab(tabs.size() - 1);

        if (url != null && !url.isEmpty()) {
            loadUrlInActiveTab(url);
        } else {
            showHomeScreen();
        }
    }

    public void switchToTab(int index) {
        if (index < 0 || index >= tabs.size()) return;

        activeTabIndex = index;
        GhostTab activeTab = tabs.get(index);

        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).getWebView().setVisibility(i == index ? View.VISIBLE : View.GONE);
        }

        tabsCountBadge.setText(String.valueOf(tabs.size()));

        if (activeTab.isHome()) {
            showHomeScreen();
        } else {
            homeLayout.setVisibility(View.GONE);
            urlEditText.setText(activeTab.getUrl());
            updateLockIcon(activeTab.getUrl());
        }

        updateNavigationButtons();
    }

    public void closeTab(int index) {
        if (index < 0 || index >= tabs.size()) return;

        GhostTab tabToClose = tabs.remove(index);
        tabContainer.removeView(tabToClose.getWebView());
        tabToClose.destroy();

        if (tabs.isEmpty()) {
            addNewTab(null);
        } else {
            int newActive = Math.min(activeTabIndex, tabs.size() - 1);
            switchToTab(newActive);
        }
    }

    public void closeAllTabs() {
        for (GhostTab tab : tabs) {
            tabContainer.removeView(tab.getWebView());
            tab.destroy();
        }
        tabs.clear();
        addNewTab(null);
    }

    private GhostTab getActiveTab() {
        if (activeTabIndex >= 0 && activeTabIndex < tabs.size()) {
            return tabs.get(activeTabIndex);
        }
        return null;
    }

    // ==========================================
    // Navigation & URL Loading
    // ==========================================

    private void loadEnteredInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            showHomeScreen();
            return;
        }

        String query = input.trim();
        if (query.startsWith("http://") || query.startsWith("https://") || query.startsWith("file://") || query.startsWith("about:")) {
            loadUrlInActiveTab(query);
        } else if (query.contains(".") && !query.contains(" ")) {
            loadUrlInActiveTab("https://" + query);
        } else {
            String searchUrl = settings.buildSearchUrl(query);
            loadUrlInActiveTab(searchUrl);
        }
    }

    public void loadUrl(String url) {
        loadUrlInActiveTab(url);
    }

    private void loadUrlInActiveTab(String url) {
        GhostTab tab = getActiveTab();
        if (tab != null) {
            homeLayout.setVisibility(View.GONE);
            tab.getWebView().setVisibility(View.VISIBLE);
            tab.getWebView().loadUrlWithPrivacy(url);
            urlEditText.setText(url);
        }
    }

    private void showHomeScreen() {
        GhostTab tab = getActiveTab();
        if (tab != null) {
            tab.setUrl("");
            tab.setTitle("Ghost Home");
            tab.getWebView().setVisibility(View.GONE);
        }
        homeLayout.setVisibility(View.VISIBLE);
        urlEditText.setText("");
        lockIcon.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        refreshOrStopBtn.setImageResource(R.drawable.ic_refresh);
        updateNavigationButtons();
    }

    private void updateLockIcon(String url) {
        if (url != null && url.startsWith("https://")) {
            lockIcon.setVisibility(View.VISIBLE);
        } else {
            lockIcon.setVisibility(View.GONE);
        }
    }

    private void updateNavigationButtons() {
        GhostTab tab = getActiveTab();
        if (tab != null && tab.getWebView() != null) {
            backBtn.setEnabled(tab.getWebView().canGoBack() || !tab.isHome());
            forwardBtn.setEnabled(tab.getWebView().canGoForward());
        }
    }

    // ==========================================
    // Ghost Panic / Instant Wipe Routine
    // ==========================================

    private void confirmAndPurgeData() {
        new AlertDialog.Builder(this, R.style.GhostDialog)
                .setTitle(R.string.panic_wipe)
                .setMessage(R.string.panic_wipe_confirm)
                .setIcon(R.drawable.ic_burn)
                .setPositiveButton(R.string.purge, (dialog, which) -> executeGhostPurge())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public void executeGhostPurge() {
        // 1. Destroy all active tabs
        for (GhostTab tab : tabs) {
            tabContainer.removeView(tab.getWebView());
            tab.destroy();
        }
        tabs.clear();

        // 2. Cookie Eradication
        try {
            CookieManager cookieManager = CookieManager.getInstance();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cookieManager.removeAllCookies(null);
                cookieManager.removeSessionCookies(null);
                cookieManager.flush();
            } else {
                cookieManager.removeAllCookie();
                cookieManager.removeSessionCookie();
            }
        } catch (Exception ignored) {}

        // 3. Web Storage & Geolocation Eradication
        try {
            WebStorage.getInstance().deleteAllData();
            GeolocationPermissions.getInstance().clearAll();
        } catch (Exception ignored) {}

        // 4. File Cache Eradication
        clearCacheFolder(getCacheDir());
        File externalCache = getExternalCacheDir();
        if (externalCache != null) {
            clearCacheFolder(externalCache);
        }

        // 5. Reset to clean home tab
        addNewTab(null);

        Toast.makeText(this, R.string.purged_success, Toast.LENGTH_SHORT).show();
    }

    private void clearCacheFolder(File dir) {
        if (dir != null && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        clearCacheFolder(file);
                    }
                    file.delete();
                }
            }
        }
    }

    // ==========================================
    // Dialogs & Menus
    // ==========================================

    private void showShieldInfoDialog() {
        String cookiesStatus = settings.isBlockCookies() ? "BLOCKED" : "SESSION ONLY";
        String sessionsStatus = settings.isBlockSessions() ? "BLOCKED" : "ALLOWED";
        String cacheStatus = settings.isBlockCache() ? "NO-CACHE (RAM ONLY)" : "CACHE ACTIVE";
        String dntStatus = settings.isDntHeaders() ? "ACTIVE (DNT: 1, Sec-GPC: 1)" : "OFF";

        new AlertDialog.Builder(this, R.style.GhostDialog)
                .setTitle(R.string.ghost_mode)
                .setMessage("Ghostly Active Shields:\n\n" +
                        "• Cookies: " + cookiesStatus + "\n" +
                        "• DOM Storage / Sessions: " + sessionsStatus + "\n" +
                        "• Disk Cache: " + cacheStatus + "\n" +
                        "• Privacy Headers: " + dntStatus + "\n" +
                        "• Browsing History: NEVER RECORDED\n" +
                        "• Tab Lifetimes: EPHEMERAL VOLATILE MEMORY")
                .setIcon(R.drawable.ic_shield)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void showTabsDialog() {
        TabsDialog.show(this, tabs, activeTabIndex, new TabsDialog.Callback() {
            @Override
            public void onTabSelected(int index) {
                switchToTab(index);
            }

            @Override
            public void onTabClosed(int index) {
                closeTab(index);
            }

            @Override
            public void onNewTabRequested() {
                addNewTab(null);
            }

            @Override
            public void onCloseAllTabsRequested() {
                closeAllTabs();
            }
        });
    }

    private void showSettingsDialog() {
        SettingsDialog.show(this, settings, new SettingsDialog.Callback() {
            @Override
            public void onSettingsChanged() {
                for (GhostTab tab : tabs) {
                    tab.getWebView().applyPreferences(settings);
                }
            }

            @Override
            public void onInstantPurgeRequested() {
                executeGhostPurge();
            }
        });
    }

    // ==========================================
    // Context Menu (Long Click on Links/Images)
    // ==========================================

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        if (v instanceof WebView) {
            WebView.HitTestResult result = ((WebView) v).getHitTestResult();
            if (result == null) return;

            final String extra = result.getExtra();
            int type = result.getType();

            if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                menu.setHeaderTitle(extra != null ? extra : "Link Options");
                menu.add(0, 1, 0, R.string.open_new_tab).setOnMenuItemClickListener(item -> {
                    addNewTab(extra);
                    return true;
                });
                menu.add(0, 2, 0, R.string.copy_link).setOnMenuItemClickListener(item -> {
                    copyToClipboard(extra);
                    return true;
                });
                menu.add(0, 3, 0, R.string.share_link).setOnMenuItemClickListener(item -> {
                    shareUrl(extra);
                    return true;
                });
            } else if (type == WebView.HitTestResult.IMAGE_TYPE) {
                menu.setHeaderTitle("Image");
                menu.add(0, 4, 0, R.string.download_image).setOnMenuItemClickListener(item -> {
                    downloadFile(extra);
                    return true;
                });
            }
        }
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("URL", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, R.string.link_copied, Toast.LENGTH_SHORT).show();
    }

    private void shareUrl(String url) {
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, url);
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, "Share"));
    }

    private void downloadFile(String url) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, Uri.parse(url).getLastPathSegment());
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(request);
                Toast.makeText(this, "Downloading...", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Download failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ==========================================
    // Back Navigation & Keyboards
    // ==========================================

    @Override
    public void onBackPressed() {
        if (findInPageBar.getVisibility() == View.VISIBLE) {
            findInPageBar.setVisibility(View.GONE);
            GhostTab activeTab = getActiveTab();
            if (activeTab != null) activeTab.getWebView().clearMatches();
            return;
        }

        GhostTab activeTab = getActiveTab();
        if (activeTab != null && activeTab.getWebView().canGoBack()) {
            activeTab.getWebView().goBack();
            return;
        }

        if (activeTab != null && !activeTab.isHome()) {
            showHomeScreen();
            return;
        }

        if (tabs.size() > 1) {
            closeTab(activeTabIndex);
            return;
        }

        super.onBackPressed();
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isFinishing() || (settings != null && settings.isAutoPurgeExit())) {
            ((GhostApp) getApplication()).purgeGhostTraces();
        }
    }

    @Override
    protected void onDestroy() {
        for (GhostTab tab : tabs) {
            tab.destroy();
        }
        tabs.clear();

        // Final cold purge on exit
        ((GhostApp) getApplication()).purgeGhostTraces();
        super.onDestroy();
    }
}
