package com.example.app_marvel.ui.components;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.app_marvel.data.catalog.CatalogModels.Issue;
import com.example.app_marvel.databinding.ItemRecentIssueBinding;
import com.example.app_marvel.ui.common.ComicVineNavigation;
import com.example.app_marvel.R;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class RecentIssueAdapter extends RecyclerView.Adapter<RecentIssueAdapter.Holder> {
    private final ComicVineImages images;
    private List<Issue> items = Collections.emptyList();
    private int width;
    private java.util.function.IntConsumer open;
    public void openWith(java.util.function.IntConsumer listener) { open = listener; }
    private final boolean coverDates, explicitExternal, showVolume;
    public RecentIssueAdapter(ComicVineImages images) { this(images, false, false); }
    public RecentIssueAdapter(ComicVineImages images, boolean coverDates, boolean explicitExternal) {
        this(images, coverDates, explicitExternal, false);
    }
    public RecentIssueAdapter(ComicVineImages images, boolean coverDates, boolean explicitExternal, boolean showVolume) {
        this.images = images; this.coverDates = coverDates; this.explicitExternal = explicitExternal; this.showVolume = showVolume; setHasStableIds(true);
    }
    public void submit(List<Issue> value) { if (items.equals(value)) return; items = value; notifyDataSetChanged(); }
    public void width(int value) { if (width != value) { width = value; notifyDataSetChanged(); } }
    @Override public long getItemId(int position) { return items.get(position).id; }
    @Override public int getItemCount() { return items.size(); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        ItemRecentIssueBinding binding = ItemRecentIssueBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        if (parent.getResources().getConfiguration().screenWidthDp < 360 || parent.getResources().getConfiguration().fontScale > 1.3f) {
            binding.issueRow.setOrientation(android.widget.LinearLayout.VERTICAL);
            android.widget.LinearLayout.LayoutParams image = (android.widget.LinearLayout.LayoutParams) binding.issueImage.getLayoutParams();
            image.gravity = android.view.Gravity.CENTER_HORIZONTAL; binding.issueImage.setLayoutParams(image);
            binding.issueDetails.setLayoutParams(new android.widget.LinearLayout.LayoutParams(-1, -2));
        }
        return new Holder(binding);
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        Issue item = items.get(position); ItemRecentIssueBinding view = holder.binding;
        ViewGroup.LayoutParams params = view.getRoot().getLayoutParams();
        params.width = width > 0 ? width : ViewGroup.LayoutParams.MATCH_PARENT; view.getRoot().setLayoutParams(params);
        view.issueTitle.setText(item.title);
        view.issueVolume.setVisibility(showVolume ? View.VISIBLE : View.GONE);
        if (showVolume) view.issueVolume.setText(view.getRoot().getContext().getString(R.string.comics_volume_meta, item.volume));
        try {
            SimpleDateFormat source = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT); source.setLenient(false);
            String date = new SimpleDateFormat("dd MMM yyyy", new Locale("pt", "BR")).format(source.parse(item.publicationDate));
            view.issueDate.setText(coverDates ? view.getRoot().getContext().getString(R.string.history_cover_date, date) : date);
        } catch (Exception invalidDate) { view.issueDate.setText(""); }
        view.issueDate.setVisibility(view.issueDate.getText().length() > 0 ? View.VISIBLE : View.GONE);
        if (open != null) {
            view.issueMore.setText(R.string.issue_open);
            view.issueMore.setContentDescription(view.getRoot().getContext().getString(R.string.issue_open_named,item.title));
        } else if (explicitExternal) {
            view.issueMore.setText(R.string.history_external);
            view.issueMore.setContentDescription(view.getRoot().getContext().getString(R.string.history_external_issue, item.title));
        }
        view.issueMore.setEnabled(open != null || !item.siteUrl.isEmpty());
        view.issueMore.setOnClickListener(clicked -> { if (open != null) open.accept(item.id); else ComicVineNavigation.open(clicked.getContext(),item.siteUrl); });
        images.show(view.issueImage, item.imageUrl);
    }
    static final class Holder extends RecyclerView.ViewHolder {
        final ItemRecentIssueBinding binding;
        Holder(ItemRecentIssueBinding binding) { super(binding.getRoot()); this.binding = binding; }
    }
}
