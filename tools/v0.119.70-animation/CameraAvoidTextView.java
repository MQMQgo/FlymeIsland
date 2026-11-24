package io.github.flymeisland.diagnostic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.text.Layout;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.TextView;

final class CameraAvoidTextView extends TextView {
    private final Rect hole = new Rect();
    private final Rect lane = new Rect();
    private final int[] screenPosition = new int[2];
    private final ViewTreeObserver.OnPreDrawListener cameraFrame = () -> {
        if (isShown() && getAlpha() > 0f) {
            updatePaddingForCamera();
        }
        return true;
    };

    private ViewTreeObserver observer;
    private int baseLeft;
    private int baseRight;
    private int leftInset;
    private int rightInset;
    private float contentScrollOffset;
    private boolean avoidanceFrozen;

    CameraAvoidTextView(Context context) {
        super(context);
        baseLeft = getPaddingLeft();
        baseRight = getPaddingRight();
    }

    @Override public void setPadding(int left, int top, int right, int bottom) {
        baseLeft = left;
        baseRight = right;
        super.setPadding(left + leftInset, top, right + rightInset, bottom);
    }

    int cameraReservedWidth() {
        return leftInset + rightInset;
    }

    void freezeAvoidance(boolean freeze) {
        if (freeze && !avoidanceFrozen) {
            updatePaddingForCamera();
        }
        avoidanceFrozen = freeze;
        if (!freeze) {
            updatePaddingForCamera();
        }
    }

    void refreshAvoidance() {
        avoidanceFrozen = false;
        updatePaddingForCamera();
        invalidate();
    }

    void setContentScrollOffset(float offset) {
        float next = Float.isFinite(offset) ? offset : 0f;
        if (contentScrollOffset != next) {
            contentScrollOffset = next;
            invalidate();
        }
    }

    private void updatePaddingForCamera() {
        if (avoidanceFrozen) {
            return;
        }
        Layout layout = getLayout();
        if (layout == null || layout.getLineCount() == 0) {
            return;
        }
        int viewportRight = Math.max(baseLeft, visibleWidth() - baseRight);
        if (CameraTextAvoid.local(this, hole, screenPosition)) {
            float lineOrigin = getBaseline() - layout.getLineBaseline(0) - getScrollY();
            if (!CameraTextAvoid.rowBounds(hole, lineOrigin + layout.getLineTop(0),
                    lineOrigin + layout.getLineBottom(layout.getLineCount() - 1))) {
                hole.setEmpty();
            }
        } else {
            hole.setEmpty();
        }

        int gravity = Gravity.getAbsoluteGravity(getGravity(), getLayoutDirection());
        boolean preferRight = (gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.RIGHT;
        float width = hole.isEmpty() ? 0f : Layout.getDesiredWidth(getText(), getPaint());
        CameraCutoutBounds.textLane(lane, baseLeft, viewportRight, hole, width, preferRight);
        int newLeft = lane.left - baseLeft;
        int newRight = viewportRight - lane.right;
        if (newLeft != leftInset || newRight != rightInset) {
            leftInset = newLeft;
            rightInset = newRight;
            super.setPadding(baseLeft + leftInset, getPaddingTop(),
                    baseRight + rightInset, getPaddingBottom());
        }
    }

    private int visibleWidth() {
        // A scrolling message body may be measured wider than its clipped parent.
        ViewParent parent = getParent();
        if (parent instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) parent;
            if (group.getClipChildren() && group.getWidth() > 0) {
                int rightPadding = group.getClipToPadding() ? group.getPaddingRight() : 0;
                int right = group.getWidth() + group.getScrollX() - rightPadding;
                return Math.max(0, Math.min(getWidth(), right - (int) Math.ceil(getX())));
            }
        }
        return getWidth();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (cameraReservedWidth() == 0 && contentScrollOffset == 0f) {
            super.onDraw(canvas);
            return;
        }
        int saved = canvas.save();
        int scrollX = getScrollX();
        int scrollY = getScrollY();
        canvas.clipRect(scrollX + getCompoundPaddingLeft(), scrollY,
                scrollX + visibleWidth() - getCompoundPaddingRight(), scrollY + getHeight());
        canvas.translate(contentScrollOffset, 0f);
        super.onDraw(canvas);
        canvas.restoreToCount(saved);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        CameraTextAvoid.attach(this);
        observer = getViewTreeObserver();
        observer.addOnPreDrawListener(cameraFrame);
    }

    @Override protected void onDetachedFromWindow() {
        if (observer != null && observer.isAlive()) {
            observer.removeOnPreDrawListener(cameraFrame);
        }
        observer = null;
        avoidanceFrozen = false;
        CameraTextAvoid.detach(this);
        super.onDetachedFromWindow();
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updatePaddingForCamera();
    }
}
