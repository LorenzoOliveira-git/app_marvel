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

public final class CharacterPortraitAdapter extends RecyclerView.Adapter<CharacterPortraitAdapter.Holder> {
    private final ComicVineImages images;
    private final IntConsumer select;
    private final List<CatalogModels.Character> items = new ArrayList<>();
    private int cardWidth;
    public CharacterPortraitAdapter(ComicVineImages images, IntConsumer select) {
        this.images = images; this.select = select; setHasStableIds(true);
        setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
    }
    public void submit(List<CatalogModels.Character> data) {
        if (items.equals(data)) return;
        items.clear(); items.addAll(data); notifyDataSetChanged();
    }
    public CatalogModels.Character item(int index) { return items.get(index); }
    public void cardWidth(int width) { if (cardWidth != width) { cardWidth = width; notifyDataSetChanged(); } }
    @Override public long getItemId(int position) { return items.get(position).id; }
    @Override public int getItemCount() { return items.size(); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(ItemCatalogCoverBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        var item = items.get(position);
        if (cardWidth > 0) holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT));
        holder.binding.coverTitle.setText(item.name);
        String meta = item.realName.isEmpty() ? holder.itemView.getContext().getString(
                com.example.app_marvel.R.string.arquivo_not_informed) : item.realName;
        holder.binding.coverMeta.setText(meta);
        holder.binding.coverMeta.setVisibility(meta.isEmpty() ? View.GONE : View.VISIBLE);
        ConstraintLayout.LayoutParams picture = (ConstraintLayout.LayoutParams) holder.binding.coverImage.getLayoutParams();
        picture.dimensionRatio = "2:3"; holder.binding.coverImage.setLayoutParams(picture);
        holder.binding.coverImage.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        holder.itemView.setContentDescription(holder.itemView.getContext().getString(
                com.example.app_marvel.R.string.arquivo_character_named, item.name));
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
