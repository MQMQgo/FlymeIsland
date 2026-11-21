package io.github.flymeisland.diagnostic;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.widget.ImageView;

final class CapsuleIconMask {
    private CapsuleIconMask() {}

    static void setPackageIcon(ImageView view, Context context, String packageName) {
        PackageManager packages = context.getPackageManager();
        Drawable icon = null;
        if (packageName != null && !packageName.isEmpty()) {
            try {
                icon = packages.getApplicationIcon(packageName);
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
                // The source app can be removed while its notice is still active.
            }
        }
        if (icon == null) {
            icon = packages.getDefaultActivityIcon();
        }
        if (!(icon instanceof AdaptiveIconDrawable)) {
            // Keep legacy icons at their original size inside the expanded adaptive layer.
            float extra = AdaptiveIconDrawable.getExtraInsetFraction();
            float inset = extra / (1f + 2f * extra);
            icon = new AdaptiveIconDrawable(new ColorDrawable(Color.TRANSPARENT),
                    new InsetDrawable(icon, inset));
        }
        view.setClipToOutline(false);
        view.setBackground(null);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setImageDrawable(icon);
    }

    static void setContactPhoto(ImageView view, Bitmap photo) {
        restoreContactFrame(view);
        view.setImageBitmap(photo);
    }

    static void restoreContactFrame(ImageView view) {
        GradientDrawable background = photoBackground(view, 0xff282828);
        background.setShape(GradientDrawable.OVAL);
    }

    static void setLyricArtwork(ImageView view, Bitmap artwork, int cornerRadius) {
        GradientDrawable background = photoBackground(view, 0xff343434);
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(Math.min(cornerRadius, view.getLayoutParams().width * .5f));
        view.setImageBitmap(artwork);
    }

    private static GradientDrawable photoBackground(ImageView view, int color) {
        Drawable current = view.getBackground();
        GradientDrawable background;
        if (current instanceof GradientDrawable) {
            background = (GradientDrawable) current;
        } else {
            background = new GradientDrawable();
            view.setBackground(background);
        }
        background.setColor(color);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setClipToOutline(true);
        return background;
    }
}
