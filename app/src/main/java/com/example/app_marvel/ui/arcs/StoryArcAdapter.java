package com.example.app_marvel.ui.arcs;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.app_marvel.R;
import com.example.app_marvel.data.catalog.CatalogModels.StoryArc;
import com.example.app_marvel.databinding.ItemStoryArcBinding;
import com.example.app_marvel.ui.common.ComicVineNavigation;
import com.example.app_marvel.ui.components.ComicVineImages;

final class StoryArcAdapter extends ListAdapter<StoryArc,StoryArcAdapter.Holder> {
    private final ComicVineImages images;
    StoryArcAdapter(ComicVineImages images) {
        super(new DiffUtil.ItemCallback<StoryArc>() {
            @Override public boolean areItemsTheSame(@NonNull StoryArc a,@NonNull StoryArc b) { return a.id==b.id; }
            @Override public boolean areContentsTheSame(@NonNull StoryArc a,@NonNull StoryArc b) { return a.name.equals(b.name)&&a.imageUrl.equals(b.imageUrl)&&a.siteUrl.equals(b.siteUrl); }
        });
        this.images=images;setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
    }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type) {
        return new Holder(ItemStoryArcBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder,int position) {
        StoryArc arc=getItem(position);var binding=holder.binding;
        binding.arcName.setText(arc.name);ViewCompat.setAccessibilityHeading(binding.arcName,true);
        binding.arcImage.setVisibility(arc.imageUrl.isEmpty() ? View.GONE:View.VISIBLE);
        if (!arc.imageUrl.isEmpty()) images.show(binding.arcImage,arc.imageUrl);
        binding.arcSource.setVisibility(arc.siteUrl.isEmpty() ? View.GONE:View.VISIBLE);
        binding.arcSource.setContentDescription(binding.getRoot().getContext().getString(R.string.arcs_source_named,arc.name));
        binding.arcSource.setOnClickListener(v -> ComicVineNavigation.open(v.getContext(),arc.siteUrl));
    }
    static final class Holder extends RecyclerView.ViewHolder {
        final ItemStoryArcBinding binding;
        Holder(ItemStoryArcBinding binding) { super(binding.getRoot());this.binding=binding; }
    }
}
