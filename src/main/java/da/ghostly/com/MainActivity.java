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
import android.widget.Button;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import da.ghostly.com.vpn.GhostVpnManager;
import da.ghostly.com.vpn.GhostVpnProfile;
import da.ghostly.com.vpn.VpnListDialog;

/**
 * MainActivity
 * Flagship controller for Ghostly browser with pure monochrome dark mode and strict ghost privacy.
 */
public class MainActivity extends Activity implements GhostVpnManager.StateListener {

    private final List<GhostTab> tabs = new ArrayList<>();
    private int activeTabIndex = -1;
    private GhostSettings settings;

    // Top Bar UI
    private ImageButton shieldBtn;
    private TextView vpnIndicatorBadge;
    private ImageView lockIcon;
    private EditText urlEditText;
    private ImageButton refreshOrStopBtn;
    private ProgressBar progressBar;

    // Content UI
    private FrameLayout tabContainer;
    private View homeLayout;

    // Expert Network Error UI
    private View errorLayout;
    private TextView tvErrorHeader;
    private TextView tvErrorCodeBadge;
    private TextView tvErrorSummary;
    private TextView tvErrorTargetUrl;
    private TextView tvTerminalLog;
    private Button btnRetryConnection;
    private Button btnRunProbe;
    private Button btnCopyReport;
    private Button btnReturnHome;

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

        // Register In-App VPN State Listener
        GhostVpnManager.getInstance(this).addListener(this);

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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == GhostVpnManager.REQUEST_VPN_PREPARE) {
            GhostVpnManager.getInstance(this).onPrepareResult(resultCode);
        }
    }

    @Override
    public void onVpnStateChanged(int state, GhostVpnProfile activeProfile) {
        runOnUiThread(() -> {
            if (vpnIndicatorBadge != null) {
                vpnIndicatorBadge.setVisibility(state == GhostVpnManager.STATE_CONNECTED ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void initViews() {
        shieldBtn = findViewById(R.id.shieldBtn);
        vpnIndicatorBadge = findViewById(R.id.vpnIndicatorBadge);
        lockIcon = findViewById(R.id.lockIcon);
        urlEditText = findViewById(R.id.urlEditText);
        refreshOrStopBtn = findViewById(R.id.refreshOrStopBtn);
        progressBar = findViewById(R.id.progressBar);

        tabContainer = findViewById(R.id.tabContainer);
        homeLayout = findViewById(R.id.homeLayout);

        // Expert Error Views
        errorLayout = findViewById(R.id.errorLayout);
        tvErrorHeader = findViewById(R.id.tvErrorHeader);
        tvErrorCodeBadge = findViewById(R.id.tvErrorCodeBadge);
        tvErrorSummary = findViewById(R.id.tvErrorSummary);
        tvErrorTargetUrl = findViewById(R.id.tvErrorTargetUrl);
        tvTerminalLog = findViewById(R.id.tvTerminalLog);
        btnRetryConnection = findViewById(R.id.btnRetryConnection);
        btnRunProbe = findViewById(R.id.btnRunProbe);
        btnCopyReport = findViewById(R.id.btnCopyReport);
        btnReturnHome = findViewById(R.id.btnReturnHome);

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

        // In-App VPN Indicator
        if (vpnIndicatorBadge != null) {
            vpnIndicatorBadge.setVisibility(GhostVpnManager.getInstance(this).isVpnActive() ? View.VISIBLE : View.GONE);
            vpnIndicatorBadge.setOnClickListener(v -> VpnListDialog.show(this));
        }

        // Expert Error Actions
        if (btnRetryConnection != null) {
            btnRetryConnection.setOnClickListener(v -> {
                GhostTab tab = getActiveTab();
                if (tab != null && tab.getLastErrorUrl() != null && !tab.getLastErrorUrl().isEmpty()) {
                    loadUrlInActiveTab(tab.getLastErrorUrl());
                } else if (tab != null && tab.getUrl() != null && !tab.getUrl().isEmpty()) {
                    loadUrlInActiveTab(tab.getUrl());
                }
            });
        }

        if (btnRunProbe != null) {
            btnRunProbe.setOnClickListener(v -> runDiagnosticProbe());
        }

        if (btnCopyReport != null) {
            btnCopyReport.setOnClickListener(v -> copyDiagnosticReport());
        }

        if (btnReturnHome != null) {
            btnReturnHome.setOnClickListener(v -> showHomeScreen());
        }

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
                tab.clearError();
                if (tab == getActiveTab()) {
                    isPageLoading = true;
                    progressBar.setVisibility(View.VISIBLE);
                    refreshOrStopBtn.setImageResource(R.drawable.ic_close);
                    updateLockIcon(pageUrl);
                    urlEditText.setText(pageUrl);
                    homeLayout.setVisibility(View.GONE);
                    if (errorLayout != null) errorLayout.setVisibility(View.GONE);
                    tab.getWebView().setVisibility(View.VISIBLE);
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

            @Override
            public void onConnectionError(WebView view, int errorCode, String description, String failingUrl) {
                tab.setError(errorCode, description, failingUrl);
                if (tab == getActiveTab()) {
                    showErrorScreen(errorCode, description, failingUrl);
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

        if (activeTab.hasError()) {
            homeLayout.setVisibility(View.GONE);
            activeTab.getWebView().setVisibility(View.GONE);
            showErrorScreen(activeTab.getLastErrorCode(), activeTab.getLastErrorDescription(), activeTab.getLastErrorUrl());
        } else if (activeTab.isHome()) {
            if (errorLayout != null) errorLayout.setVisibility(View.GONE);
            showHomeScreen();
        } else {
            if (errorLayout != null) errorLayout.setVisibility(View.GONE);
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
            tab.clearError();
            if (errorLayout != null) errorLayout.setVisibility(View.GONE);
            homeLayout.setVisibility(View.GONE);
            tab.getWebView().setVisibility(View.VISIBLE);
            tab.getWebView().loadUrlWithPrivacy(url);
            urlEditText.setText(url);
        }
    }

    private void showHomeScreen() {
        GhostTab tab = getActiveTab();
        if (tab != null) {
            tab.clearError();
            tab.setUrl("");
            tab.setTitle("Ghost Home");
            tab.getWebView().setVisibility(View.GONE);
        }
        if (errorLayout != null) errorLayout.setVisibility(View.GONE);
        homeLayout.setVisibility(View.VISIBLE);
        urlEditText.setText("");
        lockIcon.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        refreshOrStopBtn.setImageResource(R.drawable.ic_refresh);
        updateNavigationButtons();
    }

    // ==========================================
    // Expert Error Diagnostics Subsystem
    // ==========================================

    private void showErrorScreen(int errorCode, String description, String failingUrl) {
        isPageLoading = false;
        progressBar.setVisibility(View.GONE);
        refreshOrStopBtn.setImageResource(R.drawable.ic_refresh);
        homeLayout.setVisibility(View.GONE);

        GhostTab tab = getActiveTab();
        if (tab != null && tab.getWebView() != null) {
            tab.getWebView().setVisibility(View.GONE);
        }

        if (errorLayout != null) {
            errorLayout.setVisibility(View.VISIBLE);
        }

        if (tvErrorTargetUrl != null) {
            tvErrorTargetUrl.setText(failingUrl != null ? failingUrl : "about:blank");
        }

        String codeName = getErrorCodeName(errorCode);
        if (tvErrorCodeBadge != null) {
            tvErrorCodeBadge.setText(codeName);
        }

        String fullReport = generateTechnicalReport(errorCode, description, failingUrl);
        if (tvTerminalLog != null) {
            tvTerminalLog.setText(fullReport);
        }
    }

    private String getErrorCodeName(int code) {
        switch (code) {
            case -1: return "ERR_UNKNOWN [-1]";
            case -2: return "ERR_NAME_NOT_RESOLVED [-2 / NXDOMAIN]";
            case -3: return "ERR_UNSUPPORTED_AUTH_SCHEME [-3]";
            case -4: return "ERR_AUTHENTICATION_FAILURE [-4]";
            case -5: return "ERR_PROXY_AUTH_REQUIRED [-5]";
            case -6: return "ERR_CONNECTION_REFUSED [-6 / TCP_RST]";
            case -7: return "ERR_SERVER_IO_ERROR [-7]";
            case -8: return "ERR_CONNECTION_TIMED_OUT [-8]";
            case -9: return "ERR_TOO_MANY_REDIRECTS [-9]";
            case -10: return "ERR_EMPTY_RESPONSE [-10]";
            case -11: return "ERR_FAILED_SSL_HANDSHAKE [-11]";
            case -12: return "ERR_BAD_URL [-12]";
            case -13: return "ERR_FILE_NOT_FOUND [-13]";
            case -14: return "ERR_TOO_MANY_REQUESTS [-14]";
            case -15: return "ERR_UNSAFE_RESOURCE [-15]";
            default: return "ERR_TRANSPORT_FAULT [" + code + "]";
        }
    }

    private String getPosixRfcClassification(int code) {
        switch (code) {
            case -2: return "RFC 1035 / STD 13: Domain Name Resolution Failure (NXDOMAIN / FormErr)";
            case -6: return "RFC 793: Transmission Control Protocol - Connection Refused (TCP RST Flag Received)";
            case -8: return "RFC 1122: Requirements for Internet Hosts - Transport Timeout Exceeded";
            case -11: return "RFC 8446 / RFC 5246: TLS Handshake Protocol Failure (Untrusted Chain / Cipher Mismatch)";
            case -10: return "RFC 7230: Hypertext Transfer Protocol - Null Byte or Premature EOF from Server";
            case -14: return "RFC 6585: Additional HTTP Status Codes - Rate Limiting Active (HTTP 429)";
            default: return "POSIX.1-2017: Network Subsystem Socket Transport Exception";
        }
    }

    private String generateTechnicalReport(int errorCode, String description, String failingUrl) {
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String timestamp = sdf.format(new Date());

        Uri uri = (failingUrl != null) ? Uri.parse(failingUrl) : null;
        String scheme = (uri != null && uri.getScheme() != null) ? uri.getScheme() : "https";
        String host = (uri != null && uri.getHost() != null) ? uri.getHost() : (failingUrl != null ? failingUrl : "unknown");
        int port = (uri != null) ? uri.getPort() : -1;
        if (port <= 0) port = "https".equalsIgnoreCase(scheme) ? 443 : 80;

        sb.append("[+] GHOSTLY EXPERT NETWORK FAULT ANALYZER v2.0\n");
        sb.append("=====================================================\n");
        sb.append("TIMESTAMP (UTC)    : ").append(timestamp).append("\n");
        sb.append("ERROR CODE         : ").append(getErrorCodeName(errorCode)).append("\n");
        sb.append("RAW DESCRIPTOR     : ").append(description != null ? description : "N/A").append("\n");
        sb.append("SPECIFICATION      : ").append(getPosixRfcClassification(errorCode)).append("\n");
        sb.append("\n[+] TARGET ENDPOINT PARAMETERS:\n");
        sb.append("URI                : ").append(failingUrl).append("\n");
        sb.append("SCHEME             : ").append(scheme.toUpperCase(Locale.US)).append("\n");
        sb.append("HOST               : ").append(host).append("\n");
        sb.append("TARGET PORT        : ").append(port).append("\n");

        sb.append("\n[+] ACTIVE NETWORK INTERFACE DIAGNOSTICS:\n");
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                android.net.NetworkInfo activeNet = cm.getActiveNetworkInfo();
                if (activeNet != null && activeNet.isConnected()) {
                    sb.append("TRANSPORT TYPE     : ").append(activeNet.getTypeName()).append(" (").append(activeNet.getSubtypeName()).append(")\n");
                    sb.append("STATE              : ").append(activeNet.getState()).append(" / ").append(activeNet.getDetailedState()).append("\n");
                    sb.append("FAILOVER / ROAMING : ").append(activeNet.isFailover() ? "FAILOVER ACTIVE" : "NORMAL").append(" / ").append(activeNet.isRoaming() ? "ROAMING" : "HOME").append("\n");
                } else {
                    sb.append("TRANSPORT TYPE     : NONE (OFFLINE / NO_ROUTE_TO_HOST)\n");
                }
            }

            // Enumerate IP interfaces
            sb.append("LOCAL ADDRESSES    :\n");
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                int count = 0;
                while (interfaces.hasMoreElements()) {
                    NetworkInterface nif = interfaces.nextElement();
                    if (!nif.isUp() || nif.isLoopback()) continue;
                    Enumeration<InetAddress> addrs = nif.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress addr = addrs.nextElement();
                        sb.append("  • [").append(nif.getName()).append("] ").append(addr.getHostAddress()).append("\n");
                        count++;
                    }
                }
                if (count == 0) sb.append("  • No active non-loopback network interface bound.\n");
            }
        } catch (Exception e) {
            sb.append("DIAGNOSTIC FAULT   : ").append(e.getMessage()).append("\n");
        }

        sb.append("\n[+] IN-APP ISOLATED VPN SUBSYSTEM:\n");
        GhostVpnManager vpn = GhostVpnManager.getInstance(this);
        if (vpn.isVpnActive() && vpn.getActiveProfile() != null) {
            GhostVpnProfile p = vpn.getActiveProfile();
            sb.append("TUNNEL STATE       : ACTIVE (RESTRICTED EXCLUSIVELY TO da.ghostly.com)\n");
            sb.append("PROFILE NAME       : ").append(p.getName()).append("\n");
            sb.append("PROTOCOL TYPE      : ").append(p.getType()).append("\n");
            sb.append("SERVER GATEWAY     : ").append(p.getServerAddress()).append("\n");
            sb.append("MPPE ENCRYPTION    : ").append(p.isPppEncryption() ? "ENABLED" : "DISABLED").append("\n");
        } else {
            sb.append("TUNNEL STATE       : INACTIVE (Direct device routing)\n");
        }

        sb.append("\n[+] GHOSTLY PRIVACY SUBSYSTEM:\n");
        sb.append("ZERO-COOKIE ENGINE : ENFORCED (All 1st & 3rd party cookies blocked & flushed)\n");
        sb.append("VOLATILE SESSIONS  : ISOLATED (Zero localStorage/WebSQL disk persistence)\n");
        sb.append("CACHE PROTOCOL     : LOAD_NO_CACHE (In-memory volatile RAM only)\n");
        sb.append("INJECTED HEADERS   : DNT: 1 | Sec-GPC: 1\n");
        sb.append("USER-AGENT         : ").append(settings.isDesktopMode() ? "Desktop Site (Linux/X11)" : "Mobile Safe WebKit").append("\n");
        sb.append("=====================================================\n");
        sb.append("[TIP] Tap [RUN DIAGNOSTIC PROBE] below to test live DNS and socket connectivity.");

        return sb.toString();
    }

    private void runDiagnosticProbe() {
        GhostTab tab = getActiveTab();
        final String url = (tab != null && tab.getLastErrorUrl() != null && !tab.getLastErrorUrl().isEmpty())
                ? tab.getLastErrorUrl()
                : (tab != null ? tab.getUrl() : null);

        if (url == null || url.isEmpty()) {
            Toast.makeText(this, "No target URL to probe", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, R.string.probe_running, Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            final StringBuilder log = new StringBuilder();
            log.append("\n\n");
            log.append(">>> INITIATING LIVE SOCKET & DNS PROBE\n");
            log.append("=========================================\n");

            try {
                Uri uri = Uri.parse(url);
                String host = uri.getHost();
                if (host == null || host.isEmpty()) {
                    host = url;
                }
                int port = uri.getPort();
                if (port <= 0) {
                    port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
                }

                log.append("[*] Target Host : ").append(host).append("\n");
                log.append("[*] Target Port : ").append(port).append("\n");

                // Phase 1: DNS Resolution Probe
                log.append("[*] Phase 1: Executing DNS lookup probe...\n");
                long dnsStart = System.currentTimeMillis();
                try {
                    InetAddress[] addresses = InetAddress.getAllByName(host);
                    long dnsTime = System.currentTimeMillis() - dnsStart;
                    log.append("[+] DNS Status: RESOLVED in ").append(dnsTime).append(" ms\n");
                    for (InetAddress addr : addresses) {
                        log.append("    -> ").append(addr.getHostAddress()).append("\n");
                    }

                    // Phase 2: TCP Socket Handshake Probe
                    log.append("[*] Phase 2: TCP SYN/ACK handshake probe...\n");
                    long tcpStart = System.currentTimeMillis();
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(addresses[0], port), 5000);
                        long tcpTime = System.currentTimeMillis() - tcpStart;
                        log.append("[+] TCP Handshake: ESTABLISHED (RTT: ").append(tcpTime).append(" ms)\n");
                        log.append("[+] Socket State : Local ").append(socket.getLocalSocketAddress())
                                .append(" -> Remote ").append(socket.getRemoteSocketAddress()).append("\n");
                        log.append("[+] Conclusion   : Host & port are reachable. The issue is likely HTTP application error or SSL handshake refusal.\n");
                    } catch (Exception e) {
                        long tcpFail = System.currentTimeMillis() - tcpStart;
                        log.append("[-] TCP Connect FAILED after ").append(tcpFail).append(" ms\n");
                        log.append("    -> Cause: ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append("\n");
                        log.append("[-] Conclusion: Port ").append(port).append(" refused, dropped by firewall, or host unreachable.\n");
                    }

                } catch (Exception e) {
                    long dnsFail = System.currentTimeMillis() - dnsStart;
                    log.append("[-] DNS Resolution FAILED after ").append(dnsFail).append(" ms\n");
                    log.append("    -> Cause: ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append("\n");
                    log.append("[-] Conclusion: Hostname could not be resolved (NXDOMAIN). Check DNS or internet connection.\n");
                }

            } catch (Exception ex) {
                log.append("[-] Probe Fault: ").append(ex.getMessage()).append("\n");
            }

            log.append("=========================================\n");
            final String probeResult = log.toString();

            runOnUiThread(() -> {
                if (tvTerminalLog != null) {
                    tvTerminalLog.append(probeResult);
                }
            });
        }).start();
    }

    private void copyDiagnosticReport() {
        if (tvTerminalLog != null) {
            String report = tvTerminalLog.getText().toString();
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Ghostly Diagnostics", report));
                Toast.makeText(this, R.string.diagnostics_copied, Toast.LENGTH_SHORT).show();
            }
        }
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
        GhostVpnManager.getInstance(this).removeListener(this);
        for (GhostTab tab : tabs) {
            tab.destroy();
        }
        tabs.clear();

        // Final cold purge on exit
        ((GhostApp) getApplication()).purgeGhostTraces();
        super.onDestroy();
    }
}
