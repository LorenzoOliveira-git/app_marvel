package com.example.app_marvel.ui.components;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.app_marvel.R;
import com.example.app_marvel.data.catalog.CatalogModels.Issue;
import com.example.app_marvel.databinding.ItemAppearanceBinding;
import com.example.app_marvel.ui.common.ComicVineNavigation;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class AppearanceAdapter extends RecyclerView.Adapter<AppearanceAdapter.Holder> {
    private final ComicVineImages images;
    private List<Issue> items = Collections.emptyList();
    private boolean continues;
    private java.util.function.IntConsumer open;
    public void openWith(java.util.function.IntConsumer listener) { open = listener; }
    public AppearanceAdapter(ComicVineImages images) { this.images = images; setHasStableIds(true); }
    public void submit(List<Issue> items, boolean continues) {
        if (this.items.equals(items) && this.continues == continues) return;
        this.items = items; this.continues = continues; notifyDataSetChanged();
    }
    @Override public int getItemCount() { return items.size(); }
    @Override public long getItemId(int position) { return items.get(position).id; }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        var binding=ItemAppearanceBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false);
        var config=parent.getResources().getConfiguration();
        if(config.screenWidthDp<360||config.fontScale>=1.3f){
            binding.appearanceContent.setOrientation(android.widget.LinearLayout.VERTICAL);
            var metadata=new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            metadata.topMargin=Math.round(8*parent.getResources().getDisplayMetrics().density);
            binding.appearanceMetadata.setLayoutParams(metadata);
        }
        return new Holder(binding);
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        Issue issue = items.get(position); var view = holder.view;
        view.appearanceTitle.setText(issue.title); ViewCompat.setAccessibilityHeading(view.appearanceTitle, true);
        view.appearanceDate.setText("");
        try {
            SimpleDateFormat original = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT); original.setLenient(false);
            String date = new SimpleDateFormat("dd MMM yyyy", new Locale("pt", "BR")).format(original.parse(issue.publicationDate));
            view.appearanceDate.setText(view.getRoot().getContext().getString(R.string.history_cover_date, date));
        } catch (Exception invalidDate) { /* Seção opcional: nenhum ano é presumido. */ }
        view.appearanceDate.setVisibility(view.appearanceDate.getText().length() > 0 ? View.VISIBLE : View.GONE);
        view.timelineLine.setVisibility(position + 1 < items.size() || continues ? View.VISIBLE : View.INVISIBLE);
        view.appearanceOpen.setVisibility(open == null ? View.GONE : View.VISIBLE);
        view.appearanceOpen.setContentDescription(view.getRoot().getContext().getString(R.string.issue_open_named,issue.title));
        view.appearanceOpen.setText(R.string.issue_open);
        view.appearanceOpen.setOnClickListener(v -> { if (open != null) open.accept(issue.id); });
        images.show(view.appearanceImage, issue.imageUrl);
    }
    static final class Holder extends RecyclerView.ViewHolder {
        final ItemAppearanceBinding view;
        Holder(ItemAppearanceBinding view) { super(view.getRoot()); this.view = view; }
    }
}
