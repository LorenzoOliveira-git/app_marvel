package com.example.app_marvel.ui.components;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;
import com.example.app_marvel.data.catalog.CatalogModels;
import com.example.app_marvel.databinding.ItemCatalogCoverBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public final class IssueCoverAdapter extends RecyclerView.Adapter<IssueCoverAdapter.Holder> {
    private final ComicVineImages images;
    private final IntConsumer select;
    private final List<CatalogModels.Issue> items = new ArrayList<>();
    public IssueCoverAdapter(ComicVineImages images, IntConsumer select) {
        this.images = images; this.select = select; setHasStableIds(true);
        setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
    }
    public void submit(List<CatalogModels.Issue> data) {
        if (items.equals(data)) return;
        items.clear(); items.addAll(data); notifyDataSetChanged();
    }
    public CatalogModels.Issue item(int index) { return items.get(index); }
    @Override public long getItemId(int position) { return items.get(position).id; }
    @Override public int getItemCount() { return items.size(); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(ItemCatalogCoverBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        var item = items.get(position);
        holder.binding.coverTitle.setText(item.title);
        String meta = item.volume;
        holder.binding.coverMeta.setText(meta);
        holder.binding.coverMeta.setVisibility(meta.isEmpty() ? View.GONE : View.VISIBLE);
        ConstraintLayout.LayoutParams picture = (ConstraintLayout.LayoutParams) holder.binding.coverImage.getLayoutParams();
        picture.dimensionRatio = "2:3"; holder.binding.coverImage.setLayoutParams(picture);
        holder.binding.coverImage.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        holder.itemView.setContentDescription(item.title);
        holder.itemView.setOnClickListener(view -> {
            int index = holder.getBindingAdapterPosition(); if (index != RecyclerView.NO_POSITION) select.accept(index);
        });
        images.show(holder.binding.coverImage, item.imageUrl);
    }
    public static final class Holder extends RecyclerView.ViewHolder {
        final ItemCatalogCoverBinding binding;
        Holder(ItemCatalogCoverBinding binding) { super(binding.getRoot()); this.binding = binding; }
    }
}
