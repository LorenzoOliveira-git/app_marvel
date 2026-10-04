package com.example.app_marvel.ui.history;

import android.view.View;
import android.widget.LinearLayout;
import androidx.core.view.ViewCompat;
import com.example.app_marvel.R;
import com.example.app_marvel.data.catalog.CatalogModels.Character;
import com.example.app_marvel.databinding.ComponentHistoryIdentityBinding;
import com.example.app_marvel.ui.common.UiState;
import com.example.app_marvel.ui.components.ComicVineImages;

/** Identidade compartilhada pelas duas telas de histórias. */
final class HistoryIdentityBinder {
    private final ComponentHistoryIdentityBinding view;
    private final ComicVineImages images;
    HistoryIdentityBinder(ComponentHistoryIdentityBinding view, ComicVineImages images) {
        this.view = view; this.images = images;
        ViewCompat.setAccessibilityHeading(view.identityName, true);
        var configuration = view.getRoot().getResources().getConfiguration();
        if (configuration.screenWidthDp < 360 || configuration.fontScale > 1.3f) {
            view.getRoot().setOrientation(LinearLayout.VERTICAL);
            view.identityText.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams image = new LinearLayout.LayoutParams(-1, Math.round(290 * view.getRoot().getResources().getDisplayMetrics().density));
            image.topMargin = Math.round(16 * view.getRoot().getResources().getDisplayMetrics().density);
            view.identityImageCard.setLayoutParams(image);
        }
    }
    void character(Character item) {
        view.identityName.setText(item.name);
        view.identityRealName.setText(item.realName.isEmpty() ? "" : view.getRoot().getContext().getString(R.string.catalog_real_name, item.realName));
        view.identityRealName.setVisibility(item.realName.isEmpty() ? View.GONE : View.VISIBLE);
        images.show(view.identityImage, item.imageUrl);
    }
    void description(UiState<String> state, Runnable retry) {
        view.identityDescriptionState.render(state.getStatus(), retry);
        view.identityDescription.setText(state.getStatus() == UiState.Status.CONTENT ? state.getData() : "");
        view.identityDescription.setVisibility(state.getStatus() == UiState.Status.CONTENT ? View.VISIBLE : View.GONE);
    }
    void origin(UiState<String> state, Runnable retry) {
        view.identityOrigin.setText(state.getStatus() == UiState.Status.CONTENT ? view.getRoot().getContext().getString(R.string.catalog_origin_value, state.getData()) : "");
        view.identityOrigin.setVisibility(state.getStatus() == UiState.Status.CONTENT ? View.VISIBLE : View.GONE);
        view.identityOriginProgress.setVisibility(state.getStatus() == UiState.Status.LOADING ? View.VISIBLE : View.GONE);
        view.identityOriginRetry.setVisibility(state.getStatus() == UiState.Status.ERROR ? View.VISIBLE : View.GONE);
        view.identityOriginRetry.setOnClickListener(v -> retry.run());
    }
}
