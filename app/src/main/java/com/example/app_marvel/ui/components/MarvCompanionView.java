package com.example.app_marvel.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import com.example.app_marvel.R;

/** Six illustrated poses, crossfades and short, lifecycle-aware motion accents. */
public final class MarvCompanionView extends View {
    public enum Pose { IDLE, WELCOME, ATTENTIVE, THINKING, CELEBRATE, REASSURE }
    private static Bitmap atlas;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect source = new Rect();
    private final Rect visible = new Rect();
    private final RectF destination = new RectF();
    private Pose pose = Pose.IDLE, previous = Pose.IDLE;
    private ValueAnimator animator;
    private float progress = 1f, phase;
    private boolean resumed, inViewport, observing;
    private LifecycleOwner owner;
    private final DefaultLifecycleObserver lifecycle = new DefaultLifecycleObserver() {
        @Override public void onResume(@NonNull LifecycleOwner owner) { resumed = true; updateVisibility(); }
        @Override public void onPause(@NonNull LifecycleOwner owner) { resumed = false; stop(); inViewport = false; }
        @Override public void onDestroy(@NonNull LifecycleOwner owner) { stop(); owner.getLifecycle().removeObserver(this); MarvCompanionView.this.owner = null; }
    };
    private final ViewTreeObserver.OnScrollChangedListener scroll = this::updateVisibility;
    private final ViewTreeObserver.OnGlobalLayoutListener layout = this::updateVisibility;
    private final ContentObserver motionSetting = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange) { if (!motionAllowed()) stop(); }
    };

    public MarvCompanionView(Context context) { this(context, null); }
    public MarvCompanionView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        if (atlas == null) atlas = BitmapFactory.decodeResource(getResources(), R.drawable.marv_companion_atlas);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    public void bindLifecycle(LifecycleOwner value) {
        if (owner != null) owner.getLifecycle().removeObserver(lifecycle);
        owner = value;
        owner.getLifecycle().addObserver(lifecycle);
    }
    public void setPose(Pose value) {
        if (pose == value) return;
        previous = pose; pose = value;
        if (resumed && inViewport && motionAllowed()) play(); else stop();
        invalidate();
    }
    public void react() { if (resumed && inViewport && motionAllowed()) play(); }
    private boolean motionAllowed() {
        return Settings.Global.getFloat(getContext().getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
    }
    private void updateVisibility() {
        boolean shown = resumed && isAttachedToWindow() && getWindowVisibility() == VISIBLE && isShown() && getGlobalVisibleRect(visible);
        if (shown == inViewport) return;
        inViewport = shown;
        if (shown && motionAllowed()) play(); else stop();
    }
    private void play() {
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(pose == Pose.CELEBRATE ? 1800 : 3000);
        animator.setInterpolator(new android.view.animation.LinearInterpolator());
        animator.addUpdateListener(value -> {
            float fraction = (float) value.getAnimatedValue();
            progress = Math.min(1f, fraction * 12f);
            phase = fraction;
            invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (!cancelled && pose == Pose.WELCOME) setPose(Pose.IDLE);
            }
        });
        animator.start();
    }
    private void stop() {
        if (animator != null) { animator.cancel(); animator = null; }
        progress = 1f; phase = 0f; invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (atlas == null) return;
        float side = Math.min(getWidth() - getPaddingLeft() - getPaddingRight(), getHeight() - getPaddingTop() - getPaddingBottom()) * .92f;
        float cx = getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight()) / 2f;
        float cy = getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom()) / 2f;
        destination.set(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2);
        float wave = (float) Math.sin(phase * Math.PI * 4) * (float) Math.sin(phase * Math.PI);
        float lift = pose == Pose.CELEBRATE ? -Math.abs(wave) * side * .055f : wave * side * .018f;
        canvas.save();
        canvas.translate(0, lift);
        canvas.rotate(wave * (pose == Pose.WELCOME ? 4f : pose == Pose.THINKING ? 2f : 1f), cx, cy);
        if (progress < 1f) drawPose(canvas, previous, 1f - progress);
        drawPose(canvas, pose, progress);
        canvas.restore();
    }
    private void drawPose(Canvas canvas, Pose value, float opacity) {
        int width = atlas.getWidth() / 3, height = atlas.getHeight() / 2, index = value.ordinal();
        source.set(index % 3 * width, index / 3 * height, (index % 3 + 1) * width, (index / 3 + 1) * height);
        paint.setAlpha(Math.round(255 * opacity));
        canvas.drawBitmap(atlas, source, destination, paint);
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnScrollChangedListener(scroll);
        getViewTreeObserver().addOnGlobalLayoutListener(layout);
        getContext().getContentResolver().registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, motionSetting);
        observing = true;
        updateVisibility();
    }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        // View's constructor may call this before the fields are initialized.
        if (visible != null) updateVisibility();
    }
    @Override protected void onDetachedFromWindow() {
        stop(); inViewport = false;
        if (getViewTreeObserver().isAlive()) {
            getViewTreeObserver().removeOnScrollChangedListener(scroll);
            getViewTreeObserver().removeOnGlobalLayoutListener(layout);
        }
        if (observing) getContext().getContentResolver().unregisterContentObserver(motionSetting);
        observing = false;
        super.onDetachedFromWindow();
    }
}
