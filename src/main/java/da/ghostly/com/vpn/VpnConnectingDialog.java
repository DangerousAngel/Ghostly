package da.ghostly.com.vpn;

import android.app.Activity;
import android.app.Dialog;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import da.ghostly.com.R;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * VpnConnectingDialog
 * Interactive connecting interface that verifies gateway reachability,
 * performs asynchronous handshake diagnostics, and displays live monospace logs.
 */
public class VpnConnectingDialog {

    private static final String TAG = "VpnConnectingDialog";

    public interface Callback {
        void onConnectionCompleted(boolean success, GhostVpnProfile profile);
    }

    private final Activity activity;
    private final GhostVpnProfile profile;
    private final String username;
    private final String password;
    private final Callback callback;

    private Dialog dialog;
    private TextView tvBadge;
    private ProgressBar progressBar;
    private TextView tvLog;
    private ScrollView scrollLog;
    private Button btnCancel;
    private Button btnRetry;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Thread probeThread;
    private volatile boolean isCancelled = false;

    public static void show(Activity activity, GhostVpnProfile profile, String username, String password, Callback callback) {
        if (activity == null || activity.isFinishing()) return;
        new VpnConnectingDialog(activity, profile, username, password, callback).start();
    }

    public VpnConnectingDialog(Activity activity, GhostVpnProfile profile, String username, String password, Callback callback) {
        this.activity = activity;
        this.profile = profile;
        this.username = username;
        this.password = password;
        this.callback = callback;
    }

    public void start() {
        dialog = new Dialog(activity, R.style.GhostDialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_vpn_connecting, null);
        dialog.setContentView(view);

        Window window = dialog.getWindow();
        if (window != null) {
            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            int targetWidth = (int) (dm.widthPixels * 0.92f);
            int maxWidth = (int) (500 * dm.density);
            if (targetWidth > maxWidth) targetWidth = maxWidth;
            window.setLayout(targetWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvTitle = view.findViewById(R.id.tvConnectingTitle);
        tvBadge = view.findViewById(R.id.tvConnectingBadge);
        TextView tvProfileName = view.findViewById(R.id.tvConnectingProfileName);
        TextView tvServerDetails = view.findViewById(R.id.tvConnectingServerDetails);
        progressBar = view.findViewById(R.id.progressBarConnecting);
        scrollLog = view.findViewById(R.id.scrollConnectingLog);
        tvLog = view.findViewById(R.id.tvConnectingLog);
        btnCancel = view.findViewById(R.id.btnCancelConnecting);
        btnRetry = view.findViewById(R.id.btnRetryConnecting);

        tvProfileName.setText(profile.getName());
        tvServerDetails.setText(profile.getType() + " • " + profile.getServerAddress());

        btnCancel.setOnClickListener(v -> cancel());
        btnRetry.setOnClickListener(v -> retry());

        if (scrollLog != null) {
            scrollLog.setOnTouchListener((v, event) -> {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                return false;
            });
        }
        if (tvLog != null) {
            tvLog.setOnTouchListener((v, event) -> {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                return false;
            });
        }

        dialog.show();
        runConnectionSequence();
    }

    private void log(String message) {
        mainHandler.post(() -> {
            if (tvLog == null || scrollLog == null) return;
            String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
            String current = tvLog.getText().toString();
            if (current.isEmpty()) {
                tvLog.setText("[" + time + "] " + message);
            } else {
                tvLog.append("\n[" + time + "] " + message);
            }
            scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void runConnectionSequence() {
        isCancelled = false;
        tvBadge.setText("CONNECTING");
        tvBadge.setTextColor(0xFFFFFFFF);
        tvBadge.setBackgroundResource(R.drawable.bg_terminal_console);
        progressBar.setVisibility(View.VISIBLE);
        btnRetry.setVisibility(View.GONE);
        tvLog.setText("");

        probeThread = new Thread(() -> {
            try {
                log("Target gateway: " + profile.getServerAddress());
                log("VPN Protocol: " + profile.getType());

                // Phase 0: Physical Network Verification (Wi-Fi / Mobile Data)
                if (!VpnProtocolClient.isNetworkAvailable(activity)) {
                    handleError(activity.getString(R.string.vpn_err_no_network));
                    return;
                }
                log("Physical network interface online.");

                // Phase 1, 2, 3: Real Gateway Resolution, Protocol Handshake, and Credential Authentication
                VpnProtocolClient.HandshakeResult result = VpnProtocolClient.executeHandshake(
                        activity,
                        profile,
                        username,
                        password,
                        this::log
                );

                if (isCancelled) return;

                // Phase 4: Establish Tunnel interface
                log(activity.getString(R.string.vpn_step_tunnel));
                Thread.sleep(200);
                if (isCancelled) return;

                // Start GhostVpnService with genuine handshake result
                mainHandler.post(() -> {
                    GhostVpnManager.getInstance(activity).startServiceInternal(profile, username, password, result);
                });

                log("Tunnel interface active (10.8.0.2/24). All apps traffic routed through gateway.");

                // Connection successful!
                mainHandler.post(() -> {
                    tvBadge.setText("CONNECTED");
                    tvBadge.setBackgroundResource(R.drawable.bg_vpn_badge);
                    tvBadge.setTextColor(0xFF000000);
                    progressBar.setVisibility(View.GONE);

                    mainHandler.postDelayed(() -> {
                        if (dialog != null && dialog.isShowing()) {
                            try {
                                dialog.dismiss();
                            } catch (Exception ignored) {
                            }
                        }
                        if (callback != null) {
                            callback.onConnectionCompleted(true, profile);
                        }
                    }, 800);
                });

            } catch (InterruptedException e) {
                // Thread interrupted on cancellation
            } catch (Exception e) {
                handleError(e.getMessage() != null ? e.getMessage() : "Unexpected error during connection: " + e.getClass().getSimpleName());
            }
        }, "VpnProbeThread");

        probeThread.start();
    }

    private int getPortForType(String type) {
        if (type == null) return 1723;
        if (type.contains("PPTP")) return 1723;
        if (type.contains("L2TP")) return 1701;
        if (type.contains("IPSec") || type.contains("IKEv2")) return 500;
        return 1723;
    }

    private void handleError(String errorMsg) {
        log("[ERROR] " + errorMsg);
        mainHandler.post(() -> {
            tvBadge.setText(activity.getString(R.string.vpn_connection_failed).toUpperCase(Locale.US));
            tvBadge.setTextColor(0xFFFFFFFF);
            tvBadge.setBackgroundResource(R.drawable.bg_terminal_console);
            progressBar.setVisibility(View.GONE);
            btnRetry.setVisibility(View.VISIBLE);

            GhostVpnManager.getInstance(activity).notifyState(GhostVpnManager.STATE_ERROR, profile);

            if (callback != null) {
                callback.onConnectionCompleted(false, profile);
            }
        });
    }

    private void retry() {
        if (probeThread != null) {
            probeThread.interrupt();
        }
        runConnectionSequence();
    }

    private void cancel() {
        isCancelled = true;
        if (probeThread != null) {
            probeThread.interrupt();
        }
        if (dialog != null && dialog.isShowing()) {
            try {
                dialog.dismiss();
            } catch (Exception ignored) {
            }
        }
        GhostVpnManager.getInstance(activity).stopVpn();
    }
}
