package io.github.flymeisland.diagnostic;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;

final class ExpansionTiming implements TimeInterpolator {
    private static final long DURATION_MS = 320L;

    static boolean applyIfOpening(ValueAnimator animator, long duration,
            float width, float height, float startWidth, float startHeight,
            float cardAlpha, float pillAlpha, float secondaryAlpha, boolean callCard) {
        boolean grows = width > startWidth + .5f || height > startHeight + .5f;
        boolean opensCard = cardAlpha > 0f || secondaryAlpha > 0f || callCard;
        if (duration < DURATION_MS || pillAlpha != 0f || !grows || !opensCard) {
            return false;
        }
        animator.setInterpolator(new ExpansionTiming());
        animator.setDuration(DURATION_MS);
        return true;
    }

    @Override public float getInterpolation(float time) {
        return smoothStep(time + .5f * time * (1f - time));
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
