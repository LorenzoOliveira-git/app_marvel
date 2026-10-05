package com.example.app_marvel.ui.series;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateHandleSupport;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.example.app_marvel.MainActivity;
import com.example.app_marvel.MarvelApplication;
import com.example.app_marvel.R;
import com.example.app_marvel.databinding.FragmentSeriesBinding;
import com.example.app_marvel.ui.common.ComicVineNavigation;
import com.example.app_marvel.ui.common.UiState;
import com.example.app_marvel.ui.components.SeriesCoverAdapter;
import com.example.app_marvel.ui.components.SeriesFeaturedAdapter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class SeriesFragment extends Fragment {
    private FragmentSeriesBinding binding;
    private SeriesViewModel model;
    private SeriesCoverAdapter covers;
    private final PagerSnapHelper snap = new PagerSnapHelper();
    private AlertDialog dialog;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle saved) {
        binding = FragmentSeriesBinding.inflate(inflater, parent, false); return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        view.post(() -> { if (binding != null) ((MainActivity) requireActivity()).updateContentInsets(); });
        ViewCompat.setAccessibilityHeading(binding.seriesFeaturedHeading, true);
        ViewCompat.setAccessibilityHeading(binding.seriesTitle, true);
        var container = ((MarvelApplication) requireActivity().getApplication()).getContainer();
        model = new ViewModelProvider(requireActivity(), new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type, @NonNull CreationExtras extras) {
                if (type != SeriesViewModel.class) throw new IllegalArgumentException("ViewModel não registrado");
                return type.cast(new SeriesViewModel(container.getCatalog(), SavedStateHandleSupport.createSavedStateHandle(extras)));
            }
        }).get(SeriesViewModel.class);
        SeriesFeaturedAdapter highlight = new SeriesFeaturedAdapter(container.getImages());
        binding.seriesFeaturedList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.seriesFeaturedList.setAdapter(highlight);
        model.featured().observe(getViewLifecycleOwner(), state -> {
            binding.seriesFeaturedState.render(state.getStatus(), model::loadFeatured);
            binding.seriesFeaturedList.setVisibility(state.getStatus() == UiState.Status.CONTENT ? View.VISIBLE : View.GONE);
            if (state.getStatus() == UiState.Status.CONTENT) highlight.submit(state.getData());
        });
        LinearLayoutManager layout = new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false);
        binding.covers.setLayoutManager(layout); covers = new SeriesCoverAdapter(container.getImages(), this::move);
        binding.covers.setAdapter(covers); snap.attachToRecyclerView(binding.covers);
        binding.covers.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> { if (r-l > 0 && r-l != or-ol) geometry(r-l); });
        binding.covers.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView recycler, int dx, int dy) { covers.updateFocus(recycler); }
            @Override public void onScrollStateChanged(@NonNull RecyclerView recycler, int state) {
                if (state == RecyclerView.SCROLL_STATE_IDLE) { View focused = snap.findSnapView(layout); if (focused != null) model.select(layout.getPosition(focused)); }
            }
        });
        binding.seriesAz.setOnClickListener(v -> { model.order(false); filters(); });
        binding.seriesZa.setOnClickListener(v -> { model.order(true); filters(); });
        binding.seriesSearch.setOnClickListener(v -> search());
        binding.previousSeries.setOnClickListener(v -> move(model.position()-1));
        binding.nextSeries.setOnClickListener(v -> move(model.position()+1));
        binding.loadMore.setOnClickListener(v -> model.more());
        binding.seriesMore.setOnClickListener(v -> { var item = model.selected().getValue(); if (item != null) ComicVineNavigation.open(requireContext(),item.siteUrl); });
        model.state().observe(getViewLifecycleOwner(), state -> {
            binding.seriesState.render(state.getStatus(), model::reload);
            if (state.getStatus() == UiState.Status.EMPTY) binding.seriesState.emptyMessage(R.string.series_empty_title, R.string.series_empty_body);
            binding.seriesContent.setVisibility(state.getStatus() == UiState.Status.CONTENT ? View.VISIBLE : View.GONE);
            if (state.getStatus() == UiState.Status.CONTENT) {
                covers.submit(state.getData());
                selection();
                binding.covers.post(() -> { if (binding != null) ((LinearLayoutManager) binding.covers.getLayoutManager()).scrollToPositionWithOffset(model.position(), 0); });
            }
        });
        model.selected().observe(getViewLifecycleOwner(), item -> selection());
        model.hasMore().observe(getViewLifecycleOwner(), value -> paging());
        model.loadingMore().observe(getViewLifecycleOwner(), value -> paging());
        model.moreError().observe(getViewLifecycleOwner(), value -> paging());
        filters();
    }
    private void geometry(int width) {
        int cardWidth = Math.min(dp(280), Math.round(width*.60f)), cardHeight = Math.round(cardWidth*1.47f);
        covers.setGeometry(cardWidth, cardHeight);
        int side = Math.max(0, (width-cardWidth-dp(16))/2); binding.covers.setPadding(side,0,side,0);
        ViewGroup.LayoutParams params = binding.covers.getLayoutParams(); params.height = cardHeight+dp(16); binding.covers.setLayoutParams(params);
        ((LinearLayoutManager) binding.covers.getLayoutManager()).scrollToPositionWithOffset(model.position(),0);
        binding.covers.post(() -> { if (binding != null) covers.updateFocus(binding.covers); });
    }
    private void move(int position) { if (position >= 0 && position < covers.getItemCount()) binding.covers.smoothScrollToPosition(position); }
    private void selection() {
        var item = model.selected().getValue(); if (item == null) return;
        binding.seriesTitle.setText(item.title);
        binding.seriesYear.setText(item.startYear.isEmpty() ? "":getString(R.string.series_year,item.startYear));
        binding.seriesYear.setVisibility(item.startYear.isEmpty() ? View.GONE:View.VISIBLE);
        binding.seriesEpisodes.setText(item.episodeCount>=0 ? getResources().getQuantityString(R.plurals.series_episodes,item.episodeCount,item.episodeCount):"");
        binding.seriesEpisodes.setVisibility(item.episodeCount>=0 ? View.VISIBLE:View.GONE);
        binding.seriesMore.setText(R.string.history_external);binding.seriesMore.setEnabled(!item.siteUrl.isEmpty());
        binding.seriesMore.setContentDescription(getString(R.string.series_external_named,item.title));
        int position = model.position(); binding.selectionCount.setText(getString(R.string.catalog_count,position+1,covers.getItemCount()));
        binding.previousSeries.setEnabled(position > 0); binding.nextSeries.setEnabled(position+1 < covers.getItemCount());
    }
    private void filters() {
        binding.seriesAz.setSelected(!model.descending()); binding.seriesZa.setSelected(model.descending());
        ViewCompat.setStateDescription(binding.seriesAz, !model.descending() ? getString(R.string.series_order_selected) : null);
        ViewCompat.setStateDescription(binding.seriesZa, model.descending() ? getString(R.string.series_order_selected) : null);
        binding.seriesAz.setStrokeWidth(dp(!model.descending() ? 2 : 1)); binding.seriesZa.setStrokeWidth(dp(model.descending() ? 2 : 1));
        binding.selectedQuery.setVisibility(model.query().isEmpty() ? View.GONE:View.VISIBLE);
        binding.selectedQuery.setText(getString(R.string.series_query,model.query()));
    }
    private void paging() {
        boolean loading = Boolean.TRUE.equals(model.loadingMore().getValue()); binding.pageProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.loadMore.setVisibility(Boolean.TRUE.equals(model.hasMore().getValue()) ? View.VISIBLE : View.GONE);
        binding.loadMore.setEnabled(!loading); binding.loadMore.setText(Boolean.TRUE.equals(model.moreError().getValue()) ? R.string.catalog_retry : R.string.catalog_load_more);
    }
    private void search() {
        EditText input=new EditText(requireContext()); input.setId(R.id.series_search_input); input.setSingleLine();
        input.setMinHeight(dp(48)); input.setHint(R.string.series_search_hint); input.setText(model.query());
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
        LinearLayout box=new LinearLayout(requireContext()); box.setPadding(dp(20),dp(8),dp(20),dp(8));
        box.addView(input,new LinearLayout.LayoutParams(-1,-2));
        dialog=new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.series_search).setView(box)
            .setNegativeButton(R.string.catalog_cancel,null).setNeutralButton(R.string.series_clear,(d,w)->{model.query(""); filters();})
            .setPositiveButton(R.string.series_search,null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value=input.getText().toString().trim();
            if (value.matches(".*[,|:].*")) { input.setError(getString(R.string.series_query_invalid)); return; }
            model.query(value); filters(); closeDialog();
        })); dialog.show();
    }
    private void closeDialog() { if (dialog != null) { dialog.dismiss(); dialog = null; } }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    @Override public void onDestroyView() {
        closeDialog(); snap.attachToRecyclerView(null); binding.covers.setAdapter(null); binding.seriesFeaturedList.setAdapter(null);
        super.onDestroyView(); binding = null; covers = null;
    }
}
