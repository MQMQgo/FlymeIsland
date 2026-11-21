package io.github.flymeisland.diagnostic;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;

final class ExpansionTiming implements TimeInterpolator {
    private static final long MIN_OPENING_MS = 260L;
    private static final long DURATION_MS = 340L;
    private static final long COMPACT_DURATION_MS = 270L;
    private static final long MUSIC_ARRIVAL_MS = 285L;
    private static final float WIDTH_PEAK_PROGRESS = .70f;
    private static final float HEIGHT_PEAK_PROGRESS = .82f;
    private static final float WIDTH_RECOIL_PROGRESS = .91f;
    private final int widthLimit;
    private final float heightLimit;
    private final boolean compactArrival;
    private final boolean musicArrival;

    private ExpansionTiming(int widthLimit, float heightLimit,
            boolean compactArrival, boolean musicArrival) {
        this.widthLimit = widthLimit;
        this.heightLimit = heightLimit;
        this.compactArrival = compactArrival;
        this.musicArrival = musicArrival;
    }

    static boolean applyIfOpening(ValueAnimator animator, long duration,
            float width, float height, float startWidth, float startHeight,
            float cardAlpha, float pillAlpha, float secondaryAlpha, boolean callCard,
            int widthLimit, float heightLimit, String visibleKey) {
        boolean grows = width > startWidth + .5f || height > startHeight + .5f;
        boolean opensCard = duration >= MIN_OPENING_MS && pillAlpha == 0f
                && (cardAlpha > 0f || secondaryAlpha > 0f || callCard);
        boolean compactArrival = duration == 240L && pillAlpha == 1f
                && Math.abs(startWidth - startHeight) <= .5f && width > startWidth + .5f;
        if (!grows || (!opensCard && !compactArrival)) {
            return false;
        }
        boolean musicArrival = compactArrival && "island:media".equals(visibleKey);
        animator.setInterpolator(new ExpansionTiming(widthLimit, heightLimit, compactArrival, musicArrival));
        animator.setDuration(musicArrival ? MUSIC_ARRIVAL_MS
                : compactArrival ? COMPACT_DURATION_MS : DURATION_MS);
        return true;
    }

    @Override public float getInterpolation(float time) {
        return smoothStep(time + .5f * time * (1f - time));
    }

    static float frameSize(ValueAnimator animator, float start, float target,
            float progress, boolean vertical) {
        TimeInterpolator interpolator = animator.getInterpolator();
        float travel = target - start;
        if (!(interpolator instanceof ExpansionTiming)) {
            return start + travel * progress;
        }
        ExpansionTiming timing = (ExpansionTiming) interpolator;
        boolean musicHeight = timing.musicArrival && vertical && Math.abs(travel) <= .5f;
        if (travel <= .5f && !musicHeight) {
            return start + travel * progress;
        }
        float limit = vertical ? timing.heightLimit : timing.widthLimit;
        float stretch = travel * (vertical ? .16f : .08f);
        float cap = target * (vertical ? .12f : .05f);
        if (musicHeight) {
            stretch = target * .16f;
            cap = stretch;
        } else if (timing.musicArrival && !vertical) {
            stretch = travel * .16f;
            cap = target * .10f;
        }
        float overshoot = Math.max(0f, Math.min(Math.min(stretch, cap), limit - target));
        float peakProgress = vertical ? HEIGHT_PEAK_PROGRESS : WIDTH_PEAK_PROGRESS;
        if (progress < peakProgress) {
            float approach = progress / peakProgress;
            // Width stretches first while height eases into the later peak.
            float easedApproach = vertical ? smoothStep(approach)
                    : approach + approach * approach * (1f - approach);
            return start + (travel + overshoot) * easedApproach;
        }
        if (vertical) {
            float settle = (progress - peakProgress) / (1f - peakProgress);
            return target + overshoot * (1f - smoothStep(settle));
        }
        // Recoil inward so the stretch is visible even at the width limit.
        float recoil = Math.min(travel * .15f, target * (timing.musicArrival ? .075f : .055f));
        if (progress < WIDTH_RECOIL_PROGRESS) {
            float settle = (progress - peakProgress) / (WIDTH_RECOIL_PROGRESS - peakProgress);
            return target + overshoot - (overshoot + recoil) * smoothStep(settle);
        }
        float settle = (progress - WIDTH_RECOIL_PROGRESS) / (1f - WIDTH_RECOIL_PROGRESS);
        return target - recoil * (1f - smoothStep(settle));
    }

    static float contentProgress(ValueAnimator animator, float progress, float targetAlpha) {
        TimeInterpolator interpolator = animator.getInterpolator();
        if (!(interpolator instanceof ExpansionTiming) || ((ExpansionTiming) interpolator).compactArrival) {
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
