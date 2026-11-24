package io.github.flymeisland.diagnostic;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.icu.text.BreakIterator;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.LinearInterpolator;

public final class CameraSafeTickerView extends View {
    private static final int TEXT_SIZE_SP = 11;
    private static final float SCROLL_SPEED_DP_PER_SECOND = 30f;
    private static final long START_PAUSE_MS = 1000L;
    private static final long END_PAUSE_MS = 1500L;

    private final TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final Rect localHole = new Rect();
    private final Rect rowHole = new Rect();
    private final Rect lane = new Rect();
    private final Rect firstLane = new Rect();
    private final Rect secondLane = new Rect();
    private final int[] screenPosition = new int[2];
    private final Runnable startScroll = this::beginScroll;
    private final Runnable showPreview = () -> {
        showScrollEnd = false;
        invalidate();
    };
    private final ViewTreeObserver.OnPreDrawListener cameraFrame = () -> {
        if (this.fullLayout != null && getWindowVisibility() == VISIBLE) {
            syncCameraCutout();
        }
        return true;
    };

    private ViewTreeObserver observer;
    private String text = "";
    private long revision = Long.MIN_VALUE;
    private StaticLayout fullLayout;
    private StaticLayout firstLayout;
    private StaticLayout secondLayout;
    private ValueAnimator animator;
    private float textWidth;
    private float textOffset;
    private boolean geometryReliable;
    private boolean scrollEnabled = true;
    private boolean compactActive;
    private boolean scrollFinished;
    private boolean showScrollEnd;

    public CameraSafeTickerView(Context context) {
        super(context);
        paint.setColor(0xffffffff);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextSize(TEXT_SIZE_SP * getResources().getDisplayMetrics().scaledDensity);
        setWillNotDraw(false);
        setVisibility(INVISIBLE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setMessage(String message, long identity) {
        String value = message == null ? "" : message;
        if (text.equals(value) && revision == identity) {
            return;
        }
        text = value;
        revision = identity;
        scrollFinished = false;
        setContentDescription(value);
        textWidth = Layout.getDesiredWidth(value, paint);
        fullLayout = singleLine(value, (int) Math.ceil(textWidth + paint.getTextSize() * 2f), false);
        refreshLayout(true);
    }

    public void clearMessage() {
        setMessage("", Long.MIN_VALUE);
    }

    public void setScrollEnabled(boolean enabled) {
        if (scrollEnabled != enabled) {
            scrollEnabled = enabled;
            rebuildPreview();
        }
    }

    public void setCompactActive(boolean active) {
        if (compactActive != active) {
            compactActive = active;
            resetScroll();
        }
    }

    public boolean canRenderMessage() {
        return geometryReliable && firstLayout != null;
    }

    public boolean hasStaticFallback() {
        return firstLayout != null;
    }

    public boolean hasReliableGeometry() {
        return geometryReliable;
    }

    public int getCameraCutoutCount() {
        return rowHole.isEmpty() ? 0 : 1;
    }

    final void syncCameraCutout() {
        refreshLayout(false);
    }

    private void refreshLayout(boolean force) {
        localHole.setEmpty();
        if (fullLayout != null && CameraTextAvoid.local(this, localHole, screenPosition)) {
            float top = CameraCutoutBounds.centeredTextTop(fullLayout, getHeight());
            if (!CameraTextAvoid.rowBounds(localHole,
                    top + fullLayout.getLineTop(0), top + fullLayout.getLineBottom(0))
                    || !localHole.intersect(0, 0, getWidth(), getHeight())) {
                localHole.setEmpty();
            } else {
                // Vertical motion inside the same overlapping row leaves its lanes intact.
                localHole.top = 0;
                localHole.bottom = 1;
            }
        } else {
            localHole.setEmpty();
        }
        if (force || !geometryReliable || !rowHole.equals(localHole)) {
            geometryReliable = true;
            rowHole.set(localHole);
            rebuildPreview();
        }
    }

    private void rebuildPreview() {
        boolean leftToRight = fullLayout == null || fullLayout.getParagraphDirection(0) > 0;
        CameraCutoutBounds.textLane(lane, 0, getWidth(), rowHole, textWidth, leftToRight);
        firstLane.set(lane);
        firstLayout = singleLine(text, lane.width(), true);
        secondLayout = null;
        if (!rowHole.isEmpty() && fullLayout != null && textWidth > lane.width()) {
            splitPreview(leftToRight);
        }
        resetScroll();
    }

    private void splitPreview(boolean leftToRight) {
        int leftWidth = rowHole.left;
        int rightWidth = getWidth() - rowHole.right;
        int firstWidth = leftToRight ? leftWidth : rightWidth;
        int secondWidth = leftToRight ? rightWidth : leftWidth;
        if (firstWidth <= 0 || secondWidth <= 0) {
            return;
        }

        int end = paint.breakText(text, true, firstWidth, null);
        BreakIterator characters = BreakIterator.getCharacterInstance();
        characters.setText(text);
        if (!characters.isBoundary(end)) {
            end = characters.preceding(end);
        }
        if (end <= 0 || end >= text.length()) {
            return;
        }
        String first = text.substring(0, end);
        String second = text.substring(end);
        if (Layout.getDesiredWidth(first, paint) > firstWidth
                || (scrollEnabled && Layout.getDesiredWidth(second, paint) > secondWidth)) {
            return;
        }
        StaticLayout prefix = singleLine(first, firstWidth, false);
        StaticLayout suffix = singleLine(second, secondWidth, !scrollEnabled);
        if (prefix == null || suffix == null) {
            return;
        }

        if (leftToRight) {
            firstLane.set(0, 0, rowHole.left, 1);
            secondLane.set(rowHole.right, 0, getWidth(), 1);
        } else {
            firstLane.set(rowHole.right, 0, getWidth(), 1);
            secondLane.set(0, 0, rowHole.left, 1);
        }
        firstLayout = prefix;
        secondLayout = suffix;
    }

    private StaticLayout singleLine(String value, int width, boolean ellipsize) {
        if (width <= 0 || value.isEmpty()) {
            return null;
        }
        if (ellipsize && TextUtils.ellipsize(value, paint, width, TextUtils.TruncateAt.END).length() == 0) {
            return null;
        }
        StaticLayout.Builder builder = StaticLayout.Builder.obtain(value, 0, value.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setIncludePad(false)
                .setMaxLines(1);
        if (ellipsize) {
            builder.setEllipsize(TextUtils.TruncateAt.END);
        }
        return builder.build();
    }

    private void resetScroll() {
        stopScroll();
        if (canScroll()) {
            postDelayed(startScroll, START_PAUSE_MS);
        }
        invalidate();
    }

    private boolean canScroll() {
        return compactActive && scrollEnabled && !scrollFinished && isShown()
                && fullLayout != null && secondLayout == null && lane.width() > 0
                && textWidth - lane.width() > .5f && ValueAnimator.areAnimatorsEnabled();
    }

    private void beginScroll() {
        if (!canScroll()) {
            return;
        }
        float distance = textWidth - lane.width();
        boolean leftToRight = fullLayout.getParagraphDirection(0) > 0;
        float start = leftToRight ? 0f : -distance;
        float end = leftToRight ? -distance : 0f;
        float density = Math.max(.5f, getResources().getDisplayMetrics().density);
        ValueAnimator slide = ValueAnimator.ofFloat(start, end);
        slide.setDuration(Math.max(600L, Math.round(distance * 1000f / (density * SCROLL_SPEED_DP_PER_SECOND))));
        slide.setInterpolator(new LinearInterpolator());
        slide.addUpdateListener(frame -> {
            textOffset = (float) frame.getAnimatedValue();
            invalidate();
        });
        slide.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                animator = null;
                scrollFinished = true;
                showScrollEnd = true;
                invalidate();
                postDelayed(showPreview, END_PAUSE_MS);
            }
        });
        animator = slide;
        textOffset = start;
        slide.start();
    }

    private void stopScroll() {
        removeCallbacks(startScroll);
        removeCallbacks(showPreview);
        if (animator != null) {
            animator.removeAllListeners();
            animator.removeAllUpdateListeners();
            animator.cancel();
            animator = null;
        }
        showScrollEnd = false;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (fullLayout != null && (animator != null || showScrollEnd)) {
            drawInLane(canvas, fullLayout, lane, textOffset - fullLayout.getLineLeft(0));
        } else if (firstLayout != null) {
            drawInLane(canvas, firstLayout, firstLane, 0f);
            if (secondLayout != null) {
                drawInLane(canvas, secondLayout, secondLane, 0f);
            }
        }
    }

    private void drawInLane(Canvas canvas, StaticLayout label, Rect bounds, float offset) {
        int saved = canvas.save();
        canvas.clipRect(bounds.left, 0, bounds.right, getHeight());
        canvas.translate(bounds.left + offset, CameraCutoutBounds.centeredTextTop(label, getHeight()));
        label.draw(canvas);
        canvas.restoreToCount(saved);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        observer = getViewTreeObserver();
        observer.addOnPreDrawListener(cameraFrame);
    }

    @Override protected void onDetachedFromWindow() {
        stopScroll();
        if (observer != null && observer.isAlive()) {
            observer.removeOnPreDrawListener(cameraFrame);
        }
        observer = null;
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        refreshLayout(true);
    }

    @Override protected void onVisibilityChanged(View view, int visibility) {
        super.onVisibilityChanged(view, visibility);
        if (fullLayout == null) {
            return;
        }
        if (visibility == VISIBLE) {
            resetScroll();
        } else {
            stopScroll();
        }
    }
}
