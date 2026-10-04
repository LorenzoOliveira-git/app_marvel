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
    }

    public void render(UiState.Status status, @Nullable Runnable retry) {
        // Sempre limpar o callback anterior antes de alterar o estado.
        binding.retryButton.setOnClickListener(null);
        binding.retryButton.setVisibility(GONE);
        binding.progress.setVisibility(status == UiState.Status.LOADING ? VISIBLE : GONE);
        setVisibility(status == UiState.Status.CONTENT || status == UiState.Status.UNAVAILABLE ? GONE : VISIBLE);
        if (status == UiState.Status.CONTENT || status == UiState.Status.UNAVAILABLE) {
            return;
        }
        int title;
        String body;
        int mascot = R.drawable.marv_thinking;
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
        binding.mascot.setImageResource(mascot);
        binding.statusTitle.setText(title);
        binding.statusBody.setText(body);
    }

}
