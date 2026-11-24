package io.github.flymeisland.diagnostic;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
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
import java.util.Arrays;

public final class CameraSafeTickerView extends View {
    private static final int TEXT_SIZE_SP = 11;
    private static final float CAMERA_CLEARANCE_DP = 2f;
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
    private float[] characterEdges;
    private float textWidth;
    private float scrollPosition;
    private float scrollDistance;
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
        characterEdges = null;
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
        readCameraColumn();
        if (force || !geometryReliable || !rowHole.equals(localHole)) {
            geometryReliable = true;
            rowHole.set(localHole);
            rebuildPreview();
        }
    }

    private void readCameraColumn() {
        if (fullLayout == null || !CameraTextAvoid.local(this, localHole, screenPosition)) {
            localHole.setEmpty();
            return;
        }
        float top = CameraCutoutBounds.centeredTextTop(fullLayout, getHeight());
        if (top + fullLayout.getLineTop(0) >= localHole.bottom
                || top + fullLayout.getLineBottom(0) <= localHole.top) {
            localHole.setEmpty();
            return;
        }
        // Reserve the full width for the whole character, even at a round edge.
        int clearance = (int) Math.ceil(CAMERA_CLEARANCE_DP * getResources().getDisplayMetrics().density);
        int left = Math.max(0, localHole.left - clearance);
        int right = Math.min(getWidth(), localHole.right + clearance);
        localHole.set(left, 0, right, 1);
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
        scrollDistance = measureScrollDistance();
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
        while (end > 0 && Layout.getDesiredWidth(text, 0, end, paint) > firstWidth) {
            end = characters.preceding(end);
        }
        if (end <= 0 || end >= text.length()) {
            return;
        }
        String first = text.substring(0, end);
        String second = text.substring(end);
        StaticLayout prefix = singleLine(first, firstWidth, false);
        StaticLayout suffix = singleLine(second, secondWidth, true);
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

    private float measureScrollDistance() {
        if (!scrollEnabled || fullLayout == null || lane.width() <= 0) {
            return 0f;
        }
        if (secondLayout == null) {
            return Math.max(0f, textWidth - lane.width());
        }
        // Stop at the first whole-character boundary that lets the tail fit.
        float tailStart = Math.max(0f, textWidth - secondLane.width());
        return Math.max(0f, characterEdge(tailStart, true) - firstLane.width());
    }

    private float characterEdge(float position, boolean roundUp) {
        if (characterEdges == null) {
            characterEdges = measureCharacterEdges();
        }
        int index = Arrays.binarySearch(characterEdges, position);
        if (index < 0) {
            index = -index - (roundUp ? 1 : 2);
        }
        return characterEdges[Math.max(0, Math.min(index, characterEdges.length - 1))];
    }

    private float[] measureCharacterEdges() {
        BreakIterator characters = BreakIterator.getCharacterInstance();
        characters.setText(text);
        Path selection = new Path();
        RectF bounds = new RectF();
        float[] edges = new float[text.length() + 2];
        int count = 1;
        boolean leftToRight = fullLayout.getParagraphDirection(0) > 0;
        float lineLeft = fullLayout.getLineLeft(0);
        float lineRight = lineLeft + textWidth;
        for (int start = characters.first(), end = characters.next();
                end != BreakIterator.DONE; start = end, end = characters.next()) {
            fullLayout.getSelectionPath(start, end, selection);
            if (!selection.isEmpty()) {
                selection.computeBounds(bounds, true);
                // A layout selection gives the visual bounds even inside a bidi run.
                float edge = leftToRight ? bounds.right - lineLeft : lineRight - bounds.left;
                edges[count++] = Math.max(0f, Math.min(textWidth, edge));
            }
        }
        edges[count++] = textWidth;
        Arrays.sort(edges, 0, count);
        return Arrays.copyOf(edges, count);
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
                && scrollDistance > .5f && ValueAnimator.areAnimatorsEnabled();
    }

    private void beginScroll() {
        if (!canScroll()) {
            return;
        }
        float density = Math.max(.5f, getResources().getDisplayMetrics().density);
        ValueAnimator slide = ValueAnimator.ofFloat(0f, scrollDistance);
        slide.setDuration(Math.max(600L, Math.round(scrollDistance * 1000f
                / (density * SCROLL_SPEED_DP_PER_SECOND))));
        slide.setInterpolator(new LinearInterpolator());
        slide.addUpdateListener(frame -> {
            scrollPosition = (float) frame.getAnimatedValue();
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
        scrollPosition = 0f;
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
            drawMarquee(canvas);
        } else if (firstLayout != null) {
            drawText(canvas, firstLayout, firstLane.left, firstLane.left, firstLane.right);
            if (secondLayout != null) {
                float start = fullLayout.getParagraphDirection(0) > 0
                        ? secondLane.left - secondLayout.getLineLeft(0)
                        : secondLane.right - secondLayout.getLineRight(0);
                drawText(canvas, secondLayout, start, secondLane.left, secondLane.right);
            }
        }
    }

    private void drawMarquee(Canvas canvas) {
        boolean leftToRight = fullLayout.getParagraphDirection(0) > 0;
        float lineLeft = fullLayout.getLineLeft(0);
        if (secondLayout == null) {
            float offset = leftToRight ? -scrollPosition : scrollPosition - scrollDistance;
            drawText(canvas, fullLayout, lane.left + offset - lineLeft, lane.left, lane.right);
            return;
        }

        // Lay out the two visible fragments at a whole-character seam. A character
        // moves to the first lane only when there is room for all of it there.
        float seam = scrollPosition + firstLane.width();
        float split = characterEdge(seam + Math.ulp(seam), false);
        float inset = continuationInset(seam, split);
        if (leftToRight) {
            float firstStart = firstLane.left - scrollPosition;
            drawText(canvas, fullLayout, firstStart - lineLeft, firstLane.left,
                    Math.min(firstLane.right, firstStart + split));
            float secondStart = secondLane.left + inset;
            drawText(canvas, fullLayout, secondStart - split - lineLeft, secondStart, secondLane.right);
        } else {
            float firstEnd = firstLane.right + scrollPosition;
            drawText(canvas, fullLayout, firstEnd - textWidth - lineLeft,
                    Math.max(firstLane.left, firstEnd - split), firstLane.right);
            float secondEnd = secondLane.right - inset;
            drawText(canvas, fullLayout, secondEnd - (textWidth - split) - lineLeft,
                    secondLane.left, secondEnd);
        }
    }

    private float continuationInset(float seam, float split) {
        float next = characterEdge(Math.nextUp(split), true);
        if (next <= split) {
            return 0f;
        }
        // Ease the space released by the character that just changed lanes.
        float previous = characterEdge(Math.nextDown(split), false);
        float fraction = (next - Math.max(seam, split)) / (next - split);
        float inset = (split - previous) * Math.max(0f, fraction);
        float spareWidth = Math.max(0f, secondLane.width() - (next - split));
        return Math.max(0f, Math.min(inset, Math.min(spareWidth, scrollDistance - scrollPosition)));
    }

    private void drawText(Canvas canvas, StaticLayout label, float x, float left, float right) {
        if (right <= left) {
            return;
        }
        int saved = canvas.save();
        canvas.clipRect(left, 0, right, getHeight());
        canvas.translate(x, CameraCutoutBounds.centeredTextTop(label, getHeight()));
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
