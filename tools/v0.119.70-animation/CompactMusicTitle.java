package io.github.flymeisland.diagnostic;

import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextUtils;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

final class CompactMusicTitle extends TextView {
    private static final String TAG = "flyme-island-fork-music-title";

    private CompactMusicTitle(FrameLayout slot) {
        super(slot.getContext());
        setTag(TAG);
        setSingleLine(true);
        setEllipsize(TextUtils.TruncateAt.MARQUEE);
        setMarqueeRepeatLimit(-1);
        setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        setTextColor(Color.WHITE);
        setTextSize(11f);
        setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        int inset = dp(slot, 2);
        setPadding(inset, 0, inset, 0);
        setVisibility(INVISIBLE);
        slot.addView(this, new FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.LEFT));
    }

    static void setTrack(FrameLayout slot, String title) {
        if (slot == null) return;
        CompactMusicTitle view = find(slot);
        if (view == null) view = new CompactMusicTitle(slot);
        String text = title == null || title.trim().isEmpty() ? "正在播放" : title.trim();
        if (!TextUtils.equals(view.getText(), text)) view.setText(text);
    }

    static void hide(FrameLayout slot) {
        CompactMusicTitle view = find(slot);
        if (view == null) return;
        view.setSelected(false);
        view.setVisibility(INVISIBLE);
    }

    static void place(FrameLayout slot, View root, Rect configuredCutout, boolean scroll) {
        CompactMusicTitle view = find(slot);
        if (view == null || root == null) return;
        int width = slot.getWidth();
        int height = slot.getHeight();
        if (width <= 0 || height <= 0) {
            hide(slot);
            return;
        }

        int[] slotPosition = new int[2];
        int[] rootPosition = new int[2];
        slot.getLocationOnScreen(slotPosition);
        root.getLocationOnScreen(rootPosition);
        int padding = dp(slot, 3);

        WindowInsets insets = root.getRootWindowInsets();
        List<Rect> cutouts = Collections.emptyList();
        boolean windowCoordinates = false;
        if (insets != null && Build.VERSION.SDK_INT >= 35) {
            cutouts = insets.getBoundingRects(WindowInsets.Type.displayCutout());
            windowCoordinates = !cutouts.isEmpty();
        }
        if (insets != null && cutouts.isEmpty()) {
            DisplayCutout cutout = insets.getDisplayCutout();
            if (cutout != null) cutouts = cutout.getBoundingRects();
        }

        int blockedLeft = Integer.MAX_VALUE;
        int blockedRight = Integer.MIN_VALUE;
        for (Rect cutout : cutouts) {
            if (cutout.isEmpty()) continue;
            int y = (windowCoordinates ? rootPosition[1] : 0) - slotPosition[1];
            if (cutout.bottom + y + padding <= 0 || cutout.top + y - padding >= height) continue;
            int x = (windowCoordinates ? rootPosition[0] : 0) - slotPosition[0];
            blockedLeft = Math.min(blockedLeft, cutout.left + x - padding);
            blockedRight = Math.max(blockedRight, cutout.right + x + padding);
        }
        if (blockedLeft >= blockedRight) {
            int center = rootPosition[0] + root.getWidth() / 2 - slotPosition[0];
            int reserve = dp(slot, 22);
            blockedLeft = center - reserve;
            blockedRight = center + reserve;
        }
        if (!configuredCutout.isEmpty()) {
            blockedLeft = Math.min(blockedLeft, configuredCutout.left - padding);
            blockedRight = Math.max(blockedRight, configuredCutout.right + padding);
        }

        int[] lane = safeLane(width, blockedLeft, blockedRight);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
        if (params.leftMargin != lane[0] || params.width != lane[1]) {
            params.leftMargin = lane[0];
            params.width = lane[1];
            view.setLayoutParams(params);
        }
        boolean textFitsLane = lane[1] > view.getPaddingLeft() + view.getPaddingRight();
        view.setVisibility(textFitsLane ? VISIBLE : INVISIBLE);
        view.setSelected(textFitsLane && scroll);
    }

    static int[] safeLane(int width, int blockedLeft, int blockedRight) {
        if (blockedLeft >= width || blockedRight <= 0 || blockedLeft >= blockedRight) {
            return new int[]{0, width};
        }
        int leftWidth = Math.max(0, blockedLeft);
        int rightStart = Math.min(width, blockedRight);
        int rightWidth = width - rightStart;
        return leftWidth >= rightWidth ? new int[]{0, leftWidth} : new int[]{rightStart, rightWidth};
    }

    private static CompactMusicTitle find(FrameLayout slot) {
        View view = slot == null ? null : slot.findViewWithTag(TAG);
        return view instanceof CompactMusicTitle ? (CompactMusicTitle) view : null;
    }

    private static int dp(View view, int dp) {
        return Math.round(dp * view.getResources().getDisplayMetrics().density);
    }
}
