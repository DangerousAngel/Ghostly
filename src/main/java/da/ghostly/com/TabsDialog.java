package da.ghostly.com;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

/**
 * TabsDialog
 * Dark-mode modal dialog for managing and switching between open Ghost tabs.
 */
public class TabsDialog {

    public interface Callback {
        void onTabSelected(int index);
        void onTabClosed(int index);
        void onNewTabRequested();
        void onCloseAllTabsRequested();
    }

    public static void show(Context context, List<GhostTab> tabs, int activeIndex, Callback callback) {
        Dialog dialog = new Dialog(context, R.style.GhostDialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_tabs, null);
        dialog.setContentView(dialogView);

        LinearLayout tabsContainer = dialogView.findViewById(R.id.tabsListContainer);
        Button closeAllBtn = dialogView.findViewById(R.id.closeAllTabsBtn);
        Button newTabBtn = dialogView.findViewById(R.id.dialogNewTabBtn);
        Button closeBtn = dialogView.findViewById(R.id.dialogTabsCloseBtn);

        LayoutInflater inflater = LayoutInflater.from(context);

        for (int i = 0; i < tabs.size(); i++) {
            final int index = i;
            GhostTab tab = tabs.get(i);

            View card = inflater.inflate(R.layout.item_tab_card, tabsContainer, false);
            TextView tvTitle = card.findViewById(R.id.tabTitle);
            TextView tvUrl = card.findViewById(R.id.tabUrl);
            ImageView ivFavicon = card.findViewById(R.id.tabFavicon);
            ImageButton btnClose = card.findViewById(R.id.tabCloseBtn);

            tvTitle.setText(tab.getTitle());
            tvUrl.setText(tab.isHome() ? "Ghost Home" : tab.getUrl());

            if (tab.getFavicon() != null && !tab.getFavicon().isRecycled()) {
                ivFavicon.setImageBitmap(tab.getFavicon());
            } else {
                ivFavicon.setImageResource(R.drawable.ic_ghost_logo);
            }

            // Highlight active tab
            if (i == activeIndex) {
                card.setBackgroundResource(R.drawable.bg_url_bar);
            }

            card.setOnClickListener(v -> {
                dialog.dismiss();
                if (callback != null) callback.onTabSelected(index);
            });

            btnClose.setOnClickListener(v -> {
                dialog.dismiss();
                if (callback != null) callback.onTabClosed(index);
            });

            tabsContainer.addView(card);
        }

        closeAllBtn.setOnClickListener(v -> {
            dialog.dismiss();
            if (callback != null) callback.onCloseAllTabsRequested();
        });

        newTabBtn.setOnClickListener(v -> {
            dialog.dismiss();
            if (callback != null) callback.onNewTabRequested();
        });

        closeBtn.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}
