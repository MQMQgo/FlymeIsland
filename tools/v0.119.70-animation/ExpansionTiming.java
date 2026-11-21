package io.github.flymeisland.diagnostic;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;

final class ExpansionTiming implements TimeInterpolator {
    private static final long MIN_OPENING_MS = 320L;
    private static final long DURATION_MS = 340L;
    private static final float WIDTH_PEAK_PROGRESS = .80f;
    private static final float HEIGHT_PEAK_PROGRESS = .90f;
    private final int widthLimit;
    private final float heightLimit;

    private ExpansionTiming(int widthLimit, float heightLimit) {
        this.widthLimit = widthLimit;
        this.heightLimit = heightLimit;
    }

    static boolean applyIfOpening(ValueAnimator animator, long duration,
            float width, float height, float startWidth, float startHeight,
            float cardAlpha, float pillAlpha, float secondaryAlpha, boolean callCard,
            int widthLimit, float heightLimit) {
        boolean grows = width > startWidth + .5f || height > startHeight + .5f;
        boolean opensCard = cardAlpha > 0f || secondaryAlpha > 0f || callCard;
        if (duration < MIN_OPENING_MS || pillAlpha != 0f || !grows || !opensCard) {
            return false;
        }
        animator.setInterpolator(new ExpansionTiming(widthLimit, heightLimit));
        animator.setDuration(DURATION_MS);
        return true;
    }

    @Override public float getInterpolation(float time) {
        return smoothStep(time + .5f * time * (1f - time));
    }

    static float frameSize(ValueAnimator animator, float start, float target,
            float progress, boolean vertical) {
        TimeInterpolator interpolator = animator.getInterpolator();
        float travel = target - start;
        if (!(interpolator instanceof ExpansionTiming) || travel <= .5f) {
            return start + travel * progress;
        }
        ExpansionTiming timing = (ExpansionTiming) interpolator;
        float limit = vertical ? timing.heightLimit : timing.widthLimit;
        float stretch = travel * (vertical ? .065f : .055f);
        float cap = target * (vertical ? .05f : .04f);
        float overshoot = Math.max(0f, Math.min(Math.min(stretch, cap), limit - target));
        float peakProgress = vertical ? HEIGHT_PEAK_PROGRESS : WIDTH_PEAK_PROGRESS;
        if (progress < peakProgress) {
            float approach = progress / peakProgress;
            // Width stretches first while height eases into the later peak.
            float easedApproach = vertical ? smoothStep(approach)
                    : approach + approach * approach * (1f - approach);
            return start + (travel + overshoot) * easedApproach;
        }
        float settle = (progress - peakProgress) / (1f - peakProgress);
        return target + overshoot * (1f - smoothStep(settle));
    }

    static float contentProgress(ValueAnimator animator, float progress, float targetAlpha) {
        if (!(animator.getInterpolator() instanceof ExpansionTiming)) {
            return progress;
        }
        float start = targetAlpha > 0f ? .18f : .04f;
        float end = targetAlpha > 0f ? .90f : .55f;
        float fraction = Math.max(0f, Math.min(1f, (progress - start) / (end - start)));
        return smoothStep(fraction);
    }

    private static float smoothStep(float time) {
        return time * time * (3f - 2f * time);
    }
}
