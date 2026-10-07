package com.example.app_marvel.ui.components;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.app_marvel.R;
import com.example.app_marvel.data.catalog.CatalogModels.RelatedItem;
import com.example.app_marvel.databinding.ItemRelatedCharacterBinding;
import java.util.Collections;
import java.util.List;

public final class RelatedCharacterAdapter extends RecyclerView.Adapter<RelatedCharacterAdapter.Holder> {
    public interface Listener { void open(RelatedItem item); }
    private final ComicVineImages images;
    private final Listener listener;
    private final boolean team;
    private List<RelatedItem> items = Collections.emptyList();
    public RelatedCharacterAdapter(ComicVineImages images, boolean team, Listener listener) {
        this.images = images; this.team = team; this.listener = listener; setHasStableIds(true);
    }
    public void submit(List<RelatedItem> value) { if (items.equals(value)) return; items = value; notifyDataSetChanged(); }
    @Override public long getItemId(int position) { return items.get(position).id; }
    @Override public int getItemCount() { return items.size(); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        ItemRelatedCharacterBinding binding = ItemRelatedCharacterBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        if (parent.getResources().getConfiguration().fontScale > 1.3f) {
            ViewGroup.LayoutParams params = binding.getRoot().getLayoutParams();
            params.width = Math.round(216 * parent.getResources().getDisplayMetrics().density); binding.getRoot().setLayoutParams(params);
        }
        return new Holder(binding);
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        RelatedItem item = items.get(position); var view = holder.binding;
        view.relatedName.setText(item.name); images.show(view.relatedImage, item.imageUrl);
        view.relatedMore.setVisibility(team ? View.GONE : View.VISIBLE);
        view.relatedMore.setText(R.string.catalog_character_more);
        view.relatedMore.setContentDescription(view.relatedMore.getText() + ": " + item.name);
        view.relatedMore.setEnabled(!team);
        view.relatedMore.setOnClickListener(v -> listener.open(item));
    }
    @Override public void onViewRecycled(@NonNull Holder holder) { images.show(holder.binding.relatedImage, ""); super.onViewRecycled(holder); }
    static final class Holder extends RecyclerView.ViewHolder {
        final ItemRelatedCharacterBinding binding;
        Holder(ItemRelatedCharacterBinding binding) { super(binding.getRoot()); this.binding = binding; }
    }
}
