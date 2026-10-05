package com.example.app_marvel.ui.components;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.app_marvel.data.catalog.CatalogModels;
import com.example.app_marvel.databinding.ItemCharacterPortraitBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public final class MovieCoverAdapter extends RecyclerView.Adapter<MovieCoverAdapter.Holder> {
    private final ComicVineImages images;
    private final IntConsumer select;
    private final List<CatalogModels.Movie> items = new ArrayList<>();
    private int cardWidth = 240, cardHeight = 366;
    public MovieCoverAdapter(ComicVineImages images, IntConsumer select) {
        this.images = images; this.select = select; setHasStableIds(true);
        setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
    }
    public void setGeometry(int width, int height) {
        if (cardWidth == width && cardHeight == height) return;
        cardWidth = width; cardHeight = height; notifyItemRangeChanged(0, items.size());
    }
    public void submit(List<CatalogModels.Movie> data) {
        if (items.equals(data)) return;
        items.clear(); items.addAll(data); notifyDataSetChanged();
    }
    public CatalogModels.Movie item(int index) { return items.get(index); }
    @Override public long getItemId(int position) { return items.get(position).id; }
    @Override public int getItemCount() { return items.size(); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(ItemCharacterPortraitBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        CatalogModels.Movie item = items.get(position);
        ViewGroup.LayoutParams params = holder.itemView.getLayoutParams(); params.width = cardWidth + dp(holder.itemView, 16);
        holder.itemView.setLayoutParams(params);
        FrameLayout.LayoutParams picture = (FrameLayout.LayoutParams) holder.binding.portraitCard.getLayoutParams();
        picture.height = cardHeight; holder.binding.portraitCard.setLayoutParams(picture);
        holder.itemView.setContentDescription(item.title);
        holder.itemView.setOnClickListener(view -> {
            int index = holder.getBindingAdapterPosition(); if (index != RecyclerView.NO_POSITION) select.accept(index);
        });
        holder.binding.portrait.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        images.show(holder.binding.portrait, item.imageUrl);
    }
    public void updateFocus(RecyclerView recycler) {
        float center = recycler.getWidth() / 2f;
        for (int i = 0; i < recycler.getChildCount(); i++) {
            View child = recycler.getChildAt(i);
            Holder holder = (Holder) recycler.getChildViewHolder(child);
            float distance = Math.min(1f, Math.abs(center - (child.getLeft() + child.getRight()) / 2f) / cardWidth);
            int height = Math.round(cardHeight * (1f - .26f * distance));
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) holder.binding.portraitCard.getLayoutParams();
            if (params.height != height) { params.height = height; holder.binding.portraitCard.setLayoutParams(params); }
        }
    }
    private static int dp(View view, int value) { return Math.round(value * view.getResources().getDisplayMetrics().density); }
    public static final class Holder extends RecyclerView.ViewHolder {
        final ItemCharacterPortraitBinding binding;
        Holder(ItemCharacterPortraitBinding binding) { super(binding.getRoot()); this.binding = binding; }
    }
}
