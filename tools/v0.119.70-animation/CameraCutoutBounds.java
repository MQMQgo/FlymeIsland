package io.github.flymeisland.diagnostic;

import android.graphics.Rect;
import android.os.Build;
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
                List<Rect> displayBounds = cutout.getBoundingRects();
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

    static boolean intersectsViewport(Rect bounds, int width, int height) {
        return width > 0 && height > 0 && !bounds.isEmpty()
                && bounds.left < width && bounds.right > 0
                && bounds.top < height && bounds.bottom > 0;
    }
}
