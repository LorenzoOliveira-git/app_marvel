package com.example.app_marvel.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import com.example.app_marvel.R;
import com.example.app_marvel.databinding.ComponentMarvStateBinding;
import com.example.app_marvel.ui.common.UiState;

/** Feedback de estado. Retentativa aparece somente se uma operação real a fornecer. */
public final class MarvStateView extends LinearLayout {
    private final ComponentMarvStateBinding binding;
    private boolean mascotEnabled = true, loadingSquare, textOnlySkeleton;

    public MarvStateView(Context context) {
        this(context, null);
    }

    public MarvStateView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MarvStateView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        binding = ComponentMarvStateBinding.inflate(LayoutInflater.from(context), this, true);
        ViewCompat.setAccessibilityHeading(binding.statusTitle, true);
        var appearance=context.obtainStyledAttributes(attrs,R.styleable.MarvStateView,defStyleAttr,0);
        boolean inline=appearance.getBoolean(R.styleable.MarvStateView_inlineState,false);
        textOnlySkeleton=appearance.getBoolean(R.styleable.MarvStateView_textOnlySkeleton,false);
        appearance.recycle();
        if(inline){
            mascotEnabled=false;binding.mascot.setVisibility(GONE);
            binding.getRoot().setPadding(0,dp(16),0,dp(16));
            binding.statusTitle.setGravity(android.view.Gravity.START);
            var title=(LinearLayout.LayoutParams)binding.statusTitle.getLayoutParams();title.topMargin=0;binding.statusTitle.setLayoutParams(title);
            binding.statusBody.setGravity(android.view.Gravity.START);
            androidx.core.widget.TextViewCompat.setTextAppearance(binding.statusTitle,R.style.TextAppearance_Marvel_BodyStrong);
            var action=(LinearLayout.LayoutParams)binding.retryButton.getLayoutParams();
            action.width=LayoutParams.WRAP_CONTENT;action.gravity=android.view.Gravity.START;action.topMargin=dp(8);
            binding.retryButton.setLayoutParams(action);
            binding.retryButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            binding.retryButton.setTextColor(androidx.core.content.ContextCompat.getColor(context,R.color.arquivo_vermelho_texto));
        }
        updateSkeletonSize(0);
    }

    public void setMascotEnabled(boolean enabled) {
        mascotEnabled = enabled;
        if (!enabled) binding.mascot.setVisibility(GONE);
    }

    public void setLoadingSquare(boolean square) {
        loadingSquare = square;
        updateSkeletonSize(getWidth());
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (binding != null) updateSkeletonSize(width);
    }

    private void updateSkeletonSize(int width) {
        binding.skeletonThumb.setVisibility(textOnlySkeleton ? GONE : VISIBLE);
        binding.skeleton.setOrientation(loadingSquare ? VERTICAL : HORIZONTAL);
        LinearLayout.LayoutParams thumb = new LinearLayout.LayoutParams(
                loadingSquare ? LayoutParams.MATCH_PARENT : dp(80),
                loadingSquare ? Math.max(dp(120), width - binding.getRoot().getPaddingLeft() - binding.getRoot().getPaddingRight()) : dp(120));
        binding.skeletonThumb.setLayoutParams(thumb);
        LinearLayout.LayoutParams lines = new LinearLayout.LayoutParams(
                (loadingSquare || textOnlySkeleton) ? LayoutParams.MATCH_PARENT : 0, LayoutParams.WRAP_CONTENT,
                (loadingSquare || textOnlySkeleton) ? 0 : 1);
        lines.topMargin = loadingSquare ? dp(16) : 0;
        lines.setMarginStart(loadingSquare || textOnlySkeleton ? 0 : dp(16));
        binding.skeletonLines.setLayoutParams(lines);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    public void searchEmpty(String query, Runnable reset) {
        if (!query.trim().isEmpty()) binding.statusTitle.setText(getContext().getString(
                R.string.arquivo_no_results, query.length() > 40 ? query.substring(0, 40) + "…" : query));
        binding.retryButton.setText(R.string.catalog_clear_filters);
        binding.retryButton.setVisibility(VISIBLE);
        binding.retryButton.setOnClickListener(view -> reset.run());
    }

    public void emptyMessage(int title, int body) {
        binding.statusTitle.setText(title); binding.statusBody.setText(body);
    }

    public void render(UiState.Status status, @Nullable Runnable retry) {
        // Sempre limpar o callback anterior antes de alterar o estado.
        binding.retryButton.setOnClickListener(null);
        binding.retryButton.setVisibility(GONE);
        binding.retryButton.setText(R.string.catalog_retry);
        // Esqueletos estáticos evitam cintilação e respeitam movimento reduzido.
        binding.progress.setVisibility(GONE);
        binding.skeleton.setVisibility(status == UiState.Status.LOADING ? VISIBLE : GONE);
        binding.statusTitle.setVisibility(status == UiState.Status.LOADING ? GONE : VISIBLE);
        setVisibility(status == UiState.Status.CONTENT || status == UiState.Status.UNAVAILABLE ? GONE : VISIBLE);
        if (status == UiState.Status.CONTENT || status == UiState.Status.UNAVAILABLE) {
            return;
        }
        int title;
        String body;
        binding.mascot.setVisibility(mascotEnabled && status != UiState.Status.LOADING ? VISIBLE : GONE);
        int icon = R.drawable.marv_thinking;
        switch (status) {
            case LOADING:
                title = R.string.state_loading_title;
                body = getContext().getString(R.string.state_loading_body);
                break;
            case EMPTY:
                title = R.string.state_empty_title;
                body = getContext().getString(R.string.state_empty_body);
                break;
            case ERROR:
                icon = R.drawable.marv_welcome;
                title = R.string.state_error_title;
                body = getContext().getString(R.string.state_error_body);
                if (retry != null) {
                    binding.retryButton.setVisibility(VISIBLE);
                    binding.retryButton.setOnClickListener(view -> retry.run());
                }
                break;
            default:
                throw new IllegalArgumentException("Estado desconhecido");
        }
        binding.mascot.setImageResource(icon);
        binding.statusTitle.setText(title);
        binding.statusBody.setText(body);
    }

}
