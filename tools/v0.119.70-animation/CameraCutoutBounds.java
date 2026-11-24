package io.github.flymeisland.diagnostic;

import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.text.Layout;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class CameraCutoutBounds {
    private CameraCutoutBounds() {}

    static List<Rect> onScreen(View root, WindowInsets insets, int windowX, int windowY) {
        // A SystemUI overlay can receive clipped or empty window cutout insets.
        Display display = root.getDisplay();
        if (Build.VERSION.SDK_INT >= 29 && display != null) {
            DisplayCutout cutout = display.getCutout();
            if (cutout != null) {
                List<Rect> displayBounds = displayBounds(cutout);
                if (!displayBounds.isEmpty()) {
                    return displayBounds;
                }
            }
        }
        if (insets == null) {
            return Collections.emptyList();
        }

        DisplayCutout cutout = insets.getDisplayCutout();
        List<Rect> bounds = cutout == null ? Collections.emptyList() : cutout.getBoundingRects();
        if (bounds.isEmpty() && Build.VERSION.SDK_INT >= 35) {
            bounds = insets.getBoundingRects(WindowInsets.Type.displayCutout());
        }
        if (bounds.isEmpty() || (windowX == 0 && windowY == 0)) {
            return bounds;
        }

        List<Rect> screenBounds = new ArrayList<>(bounds.size());
        for (Rect windowBound : bounds) {
            if (windowBound == null) {
                continue;
            }
            Rect screenBound = new Rect(windowBound);
            screenBound.offset(windowX, windowY);
            screenBounds.add(screenBound);
        }
        return screenBounds;
    }

    private static List<Rect> displayBounds(DisplayCutout cutout) {
        List<Rect> bounds = cutout.getBoundingRects();
        if (Build.VERSION.SDK_INT < 31 || bounds.isEmpty()) {
            return bounds;
        }
        Path outline = cutout.getCutoutPath();
        if (outline == null || outline.isEmpty()) {
            return bounds;
        }

        // The rectangle can cover more than the physical opening. Use the portion
        // of the display-coordinate outline inside each reported cutout.
        List<Rect> exact = new ArrayList<>(bounds.size());
        Path region = new Path();
        RectF enclosing = new RectF();
        for (Rect bound : bounds) {
            region.rewind();
            region.addRect(bound.left, bound.top, bound.right, bound.bottom, Path.Direction.CW);
            if (region.op(outline, Path.Op.INTERSECT) && !region.isEmpty()) {
                region.computeBounds(enclosing, true);
                Rect tightened = new Rect();
                enclosing.roundOut(tightened);
                exact.add(tightened.intersect(bound) ? tightened : bound);
            } else {
                exact.add(bound);
            }
        }
        return exact;
    }

    static boolean intersectsViewport(Rect bounds, int width, int height) {
        return width > 0 && height > 0 && !bounds.isEmpty()
                && bounds.left < width && bounds.right > 0
                && bounds.top < height && bounds.bottom > 0;
    }

    static float centeredTextTop(Layout layout, int viewHeight) {
        return Math.max(0f, (viewHeight - layout.getHeight()) * .5f);
    }

    static void textLane(Rect lane, int left, int right, Rect hole,
            float textWidth, boolean preferRight) {
        right = Math.max(left, right);
        if (hole.isEmpty() || hole.right <= left || hole.left >= right) {
            lane.set(left, 0, right, 1);
            return;
        }

        int leftEnd = Math.max(left, hole.left);
        int rightStart = Math.min(right, hole.right);
        int leftWidth = leftEnd - left;
        int rightWidth = right - rightStart;
        boolean fitsLeft = textWidth <= leftWidth;
        boolean fitsRight = textWidth <= rightWidth;
        boolean useRight;
        if (fitsLeft || fitsRight) {
            useRight = fitsRight && (preferRight || !fitsLeft);
        } else {
            useRight = rightWidth > leftWidth || (rightWidth == leftWidth && preferRight);
        }
        lane.set(useRight ? rightStart : left, 0, useRight ? right : leftEnd, 1);
    }
}
