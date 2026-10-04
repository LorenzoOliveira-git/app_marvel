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
    public AppearanceAdapter(ComicVineImages images) { this.images = images; setHasStableIds(true); }
    public void submit(List<Issue> items, boolean continues) {
        if (this.items.equals(items) && this.continues == continues) return;
        this.items = items; this.continues = continues; notifyDataSetChanged();
    }
    @Override public int getItemCount() { return items.size(); }
    @Override public long getItemId(int position) { return items.get(position).id; }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(ItemAppearanceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
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
        view.appearanceOpen.setVisibility(issue.siteUrl.isEmpty() ? View.GONE : View.VISIBLE);
        view.appearanceOpen.setContentDescription(view.getRoot().getContext().getString(R.string.history_external_issue, issue.title));
        view.appearanceOpen.setOnClickListener(v -> ComicVineNavigation.open(v.getContext(), issue.siteUrl));
        images.show(view.appearanceImage, issue.imageUrl);
    }
    static final class Holder extends RecyclerView.ViewHolder {
        final ItemAppearanceBinding view;
        Holder(ItemAppearanceBinding view) { super(view.getRoot()); this.view = view; }
    }
}
