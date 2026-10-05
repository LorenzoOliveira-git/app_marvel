package com.example.app_marvel.ui.components;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.app_marvel.data.catalog.CatalogModels.Movie;
import com.example.app_marvel.databinding.ItemFeaturedMovieBinding;
import com.example.app_marvel.ui.common.ComicVineNavigation;
import com.example.app_marvel.R;
import java.util.Collections;
import java.util.List;

public final class MovieFeaturedAdapter extends RecyclerView.Adapter<MovieFeaturedAdapter.Holder> {
    private final ComicVineImages images;
    private List<Movie> items = Collections.emptyList();
    private int width;
    public MovieFeaturedAdapter(ComicVineImages images) { this.images=images; setHasStableIds(true); }
    public void submit(List<Movie> value) { if (items.equals(value)) return; items = value; notifyDataSetChanged(); }
    public void width(int value) { if (width != value) { width = value; notifyDataSetChanged(); } }
    @Override public long getItemId(int position) { return items.get(position).id; }
    @Override public int getItemCount() { return items.size(); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        ItemFeaturedMovieBinding binding = ItemFeaturedMovieBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        if (parent.getResources().getConfiguration().screenWidthDp < 360 || parent.getResources().getConfiguration().fontScale > 1.3f) {
            binding.movieRow.setOrientation(android.widget.LinearLayout.VERTICAL);
            android.widget.LinearLayout.LayoutParams image = (android.widget.LinearLayout.LayoutParams) binding.movieImage.getLayoutParams();
            image.gravity = android.view.Gravity.CENTER_HORIZONTAL; binding.movieImage.setLayoutParams(image);
            binding.movieDetails.setLayoutParams(new android.widget.LinearLayout.LayoutParams(-1, -2));
        }
        return new Holder(binding);
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        Movie item = items.get(position); ItemFeaturedMovieBinding view = holder.binding;
        ViewGroup.LayoutParams params = view.getRoot().getLayoutParams();
        params.width = width > 0 ? width : ViewGroup.LayoutParams.MATCH_PARENT; view.getRoot().setLayoutParams(params);
        view.movieTitle.setText(item.title);
        view.movieVolume.setVisibility(View.GONE);
        view.movieDate.setText(item.runtime>0 ? view.getRoot().getContext().getString(R.string.movies_runtime,item.runtime):"");
        view.movieDate.setVisibility(item.runtime>0 ? View.VISIBLE:View.GONE);
        view.movieMore.setText(R.string.history_external);
        view.movieMore.setContentDescription(view.getRoot().getContext().getString(R.string.movies_external_named,item.title));
        view.movieMore.setEnabled(!item.siteUrl.isEmpty());
        view.movieMore.setOnClickListener(clicked -> ComicVineNavigation.open(clicked.getContext(),item.siteUrl));
        images.show(view.movieImage, item.imageUrl);
    }
    static final class Holder extends RecyclerView.ViewHolder {
        final ItemFeaturedMovieBinding binding;
        Holder(ItemFeaturedMovieBinding binding) { super(binding.getRoot()); this.binding = binding; }
    }
}
