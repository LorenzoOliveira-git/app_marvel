package com.example.app_marvel.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.example.app_marvel.R;
import com.example.app_marvel.data.model.AppFeature;
import com.example.app_marvel.databinding.ComponentFeatureCardBinding;
import com.example.app_marvel.ui.common.FeatureResources;
import com.google.android.material.card.MaterialCardView;

public final class FeatureCardView extends MaterialCardView {
    private final ComponentFeatureCardBinding binding;

    public FeatureCardView(Context context) {
        this(context, null);
    }

    public FeatureCardView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, com.google.android.material.R.attr.materialCardViewStyle);
    }

    public FeatureCardView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        binding = ComponentFeatureCardBinding.inflate(LayoutInflater.from(context), this, true);
        setCardBackgroundColor(ContextCompat.getColor(context, R.color.arquivo_papel));
        setRadius(0);
        setCardElevation(0);
        setStrokeWidth(0);
        setStrokeColor(ContextCompat.getColor(context, R.color.marvel_border));
        setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.touch_min));
        setClickable(true);
        setFocusable(true);
    }

    public void bind(AppFeature feature, OnClickListener onClick) {
        String title = getContext().getString(FeatureResources.title(feature));
        String description = getContext().getString(FeatureResources.description(feature));
        binding.title.setText(title);
        binding.description.setText(description);
        binding.icon.setImageResource(FeatureResources.icon(feature));
        binding.icon.setImageTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.arquivo_tinta)));
        setContentDescription(getContext().getString(R.string.open_section_description, title, description));
        setOnClickListener(onClick);
    }
}
