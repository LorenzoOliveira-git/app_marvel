package com.example.app_marvel.ui.splash;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import com.example.app_marvel.R;

/** A short paper-and-panels handoff from the system splash to the ready destination. */
public final class ComicSplashView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final Bitmap logo;
    private final Typeface brandFont;
    private final int paper, leaf, parchment, ink, red;
    private ValueAnimator animator;
    private Runnable completion;
    private float progress;

    public ComicSplashView(Context context) { this(context, null); }
    public ComicSplashView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paper = ContextCompat.getColor(context, R.color.arquivo_papel);
        leaf = ContextCompat.getColor(context, R.color.arquivo_folha);
        parchment = ContextCompat.getColor(context, R.color.arquivo_pergaminho);
        ink = ContextCompat.getColor(context, R.color.arquivo_tinta);
        red = ContextCompat.getColor(context, R.color.arquivo_vermelho);
        logo = BitmapFactory.decodeResource(getResources(), R.drawable.logo_marvel);
        brandFont = ResourcesCompat.getFont(context, R.font.bebas_neue_regular);
        setOnClickListener(view -> finishNow());
    }

    public void play(Runnable onComplete) {
        completion = onComplete;
        if (!animationsEnabled()) { finishNow(); return; }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1450L);
        animator.addUpdateListener(value -> {
            progress = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (animator == animation) finishNow();
            }
        });
        animator.start();
    }

    public void finishNow() {
        ValueAnimator running = animator;
        animator = null;
        if (running != null) running.cancel();
        progress = 1f;
        invalidate();
        Runnable done = completion;
        completion = null;
        if (done != null) done.run();
    }

    private boolean animationsEnabled() {
        if (Build.VERSION.SDK_INT >= 26) return ValueAnimator.areAnimatorsEnabled();
        return Settings.Global.getFloat(getContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
    }

    @Override protected void onDetachedFromWindow() {
        finishNow();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float leave = ease(unit((progress - .70f) / .30f));
        float right = w * (1f - leave);
        if (right <= 0) return;
        canvas.save();
        canvas.clipRect(0, 0, right, h);
        color(paper, 255, Paint.Style.FILL, 0);
        canvas.drawRect(0, 0, w, h, paint);
        drawSymbols(canvas, w, h);
        drawPanel(canvas, w, h);
        canvas.restore();

        if (leave > 0f && leave < 1f) {
            float fold = Math.min(dp(28), right);
            path.reset();
            path.moveTo(right - fold, 0);
            path.lineTo(right, 0);
            path.lineTo(right, fold * 1.4f);
            path.close();
            color(parchment, 255, Paint.Style.FILL, 0);
            canvas.drawPath(path, paint);
            color(ink, 45, Paint.Style.STROKE, dp(1));
            canvas.drawLine(right - dp(1), 0, right - dp(1), h, paint);
        }
    }

    private void drawSymbols(Canvas canvas, float w, float h) {
        float appear = ease(unit(progress / .25f));
        float drift = dp(10) * (1f - appear);
        float[][] points = { {.14f,.13f}, {.50f,.12f}, {.84f,.15f},
                {.10f,.43f}, {.90f,.43f}, {.16f,.82f}, {.50f,.86f}, {.84f,.80f} };
        for (int i = 0; i < points.length; i++) {
            canvas.save();
            canvas.translate(points[i][0] * w, points[i][1] * h + (i % 2 == 0 ? drift : -drift));
            canvas.scale(dp(1), dp(1));
            color(ink, Math.round(42 * (.35f + .65f * appear)), Paint.Style.STROKE, 1.5f);
            paint.setStrokeJoin(Paint.Join.ROUND);
            drawSymbol(canvas, i);
            canvas.restore();
        }
    }

    private void drawSymbol(Canvas canvas, int icon) {
        path.reset();
        switch (icon) {
            case 0: // comic book
                path.moveTo(0, -10); path.quadTo(-7, -14, -15, -10);
                path.lineTo(-15, 11); path.quadTo(-6, 7, 0, 12);
                path.quadTo(6, 7, 15, 11); path.lineTo(15, -10);
                path.quadTo(7, -14, 0, -10); path.lineTo(0, 12);
                canvas.drawPath(path, paint); break;
            case 1: // film strip
                rect.set(-14, -11, 14, 11); canvas.drawRoundRect(rect, 2, 2, paint);
                canvas.drawLine(-8, -11, -8, 11, paint);
                canvas.drawLine(8, -11, 8, 11, paint);
                for (int y = -7; y <= 7; y += 7) {
                    canvas.drawCircle(-11, y, 1, paint); canvas.drawCircle(11, y, 1, paint);
                }
                break;
            case 2: // hero mask
                path.moveTo(-14, -5); path.quadTo(0, -13, 14, -5);
                path.lineTo(11, 8); path.quadTo(0, 15, -11, 8); path.close();
                canvas.drawPath(path, paint);
                canvas.drawOval(-10, -2, -3, 2, paint);
                canvas.drawOval(3, -2, 10, 2, paint); break;
            case 3: // dialogue
                rect.set(-14, -11, 14, 7); canvas.drawRoundRect(rect, 5, 5, paint);
                path.moveTo(-5, 7); path.lineTo(-8, 13); path.lineTo(1, 7);
                canvas.drawPath(path, paint); break;
            case 4: // story arc
                path.moveTo(-15, 9); path.cubicTo(-8, -15, 5, -16, 15, 6);
                canvas.drawPath(path, paint);
                canvas.drawCircle(-15, 9, 3, paint); canvas.drawCircle(15, 6, 3, paint); break;
            case 5: // comic star
                for (int i = 0; i < 10; i++) {
                    double angle = -Math.PI / 2 + i * Math.PI / 5;
                    float radius = i % 2 == 0 ? 14 : 6;
                    float x = (float) Math.cos(angle) * radius;
                    float y = (float) Math.sin(angle) * radius;
                    if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                }
                path.close(); canvas.drawPath(path, paint); break;
            case 6: // issue ticket
                rect.set(-15, -10, 15, 10); canvas.drawRoundRect(rect, 2, 2, paint);
                canvas.drawLine(5, -10, 5, 10, paint);
                canvas.drawLine(-10, -3, 0, -3, paint);
                canvas.drawLine(-10, 3, -3, 3, paint); break;
            default: // cinematic spark
                canvas.drawCircle(0, 0, 5, paint);
                canvas.drawLine(-15, 0, -9, 0, paint); canvas.drawLine(9, 0, 15, 0, paint);
                canvas.drawLine(0, -15, 0, -9, paint); canvas.drawLine(0, 9, 0, 15, paint);
        }
    }

    private void drawPanel(Canvas canvas, float w, float h) {
        float enter = ease(unit((progress - .12f) / .36f));
        float left = Math.max(dp(22), w * .105f);
        float top = h * .29f, bottom = h * .71f;
        rect.set(left, top, w - left, bottom);
        color(leaf, Math.round(247 * enter), Paint.Style.FILL, 0);
        canvas.drawRoundRect(rect, dp(5), dp(5), paint);
        color(ink, Math.round(80 * enter), Paint.Style.STROKE, dp(1));
        canvas.drawRoundRect(rect, dp(5), dp(5), paint);
        color(red, Math.round(255 * enter), Paint.Style.FILL, 0);
        canvas.drawRect(left, top, left + dp(5), bottom, paint);
        canvas.drawRect(left + dp(15), bottom - dp(19), left + dp(60), bottom - dp(16), paint);

        float brand = ease(unit((progress - .25f) / .25f));
        float cx = w / 2f, cy = (top + bottom) / 2f;
        float scale = .94f + .06f * brand;
        canvas.save();
        canvas.scale(scale, scale, cx, cy);
        color(ink, Math.round(255 * brand), Paint.Style.FILL, 0);
        paint.setTypeface(brandFont);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(31));
        canvas.drawText(getResources().getString(R.string.brand_sua).toUpperCase(java.util.Locale.ROOT),
                cx, cy - dp(49), paint);
        if (logo != null) {
            float logoWidth = Math.min(w * .55f, dp(208));
            float logoHeight = logoWidth * logo.getHeight() / logo.getWidth();
            rect.set(cx - logoWidth / 2f, cy - dp(33), cx + logoWidth / 2f,
                    cy - dp(33) + logoHeight);
            paint.setAlpha(Math.round(255 * brand));
            canvas.drawBitmap(logo, null, rect, paint);
        }
        canvas.restore();
    }

    private void color(int value, int alpha, Paint.Style style, float stroke) {
        paint.setColor(value);
        paint.setAlpha(alpha);
        paint.setStyle(style);
        paint.setStrokeWidth(stroke);
    }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private static float unit(float value) { return Math.max(0f, Math.min(1f, value)); }
    private static float ease(float value) { return value * value * (3f - 2f * value); }
}
