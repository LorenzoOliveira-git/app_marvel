package com.example.app_marvel.ui.movies;

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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.app_marvel.MainActivity;
import com.example.app_marvel.MarvelApplication;
import com.example.app_marvel.R;
import com.example.app_marvel.databinding.FragmentMoviesBinding;
import com.example.app_marvel.ui.common.ComicVineNavigation;
import com.example.app_marvel.ui.common.UiState;
import com.example.app_marvel.ui.components.MovieCoverAdapter;
import com.example.app_marvel.ui.components.MovieFeaturedAdapter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class MoviesFragment extends Fragment {
    private FragmentMoviesBinding binding;
    private MoviesViewModel model;
    private MovieCoverAdapter covers;
    private AlertDialog dialog;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle saved) {
        binding = FragmentMoviesBinding.inflate(inflater, parent, false); return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        view.post(() -> { if (binding != null) ((MainActivity) requireActivity()).updateContentInsets(); });
        ViewCompat.setAccessibilityHeading(binding.moviesFeaturedHeading, true);
        var container = ((MarvelApplication) requireActivity().getApplication()).getContainer();
        model = new ViewModelProvider(requireActivity(), new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type, @NonNull CreationExtras extras) {
                if (type != MoviesViewModel.class) throw new IllegalArgumentException("ViewModel não registrado");
                return type.cast(new MoviesViewModel(container.getCatalog(), SavedStateHandleSupport.createSavedStateHandle(extras)));
            }
        }).get(MoviesViewModel.class);
        MovieFeaturedAdapter highlight = new MovieFeaturedAdapter(container.getImages());
        highlight.openWith(id -> ((MainActivity) requireActivity()).openMovie(id));
        binding.moviesFeaturedList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.moviesFeaturedList.setAdapter(highlight);
        model.featured().observe(getViewLifecycleOwner(), state -> {
            binding.moviesFeaturedState.render(state.getStatus(), model::loadFeatured);
            binding.moviesFeaturedList.setVisibility(state.getStatus() == UiState.Status.CONTENT ? View.VISIBLE : View.GONE);
            if (state.getStatus() == UiState.Status.CONTENT) highlight.submit(state.getData());
        });
        GridLayoutManager layout = new GridLayoutManager(requireContext(), 2);
        binding.covers.setLayoutManager(layout); covers = new MovieCoverAdapter(container.getImages(), position -> { model.select(position); ((MainActivity) requireActivity()).openMovie(covers.item(position).id); });
        binding.covers.setAdapter(covers);
        binding.covers.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> { if (r-l > 0 && r-l != or-ol) geometry(r-l); });

        binding.moviesAz.setOnClickListener(v -> { model.order(false); filters(); });
        binding.moviesZa.setOnClickListener(v -> { model.order(true); filters(); });
        binding.moviesSearch.setOnClickListener(v -> search());
        binding.loadMore.setOnClickListener(v -> model.more());
        model.state().observe(getViewLifecycleOwner(), state -> {
            binding.moviesState.render(state.getStatus(), model::reload);
            if (state.getStatus() == UiState.Status.EMPTY) binding.moviesState.emptyMessage(R.string.movies_empty_title, R.string.movies_empty_body);
            binding.moviesContent.setVisibility(state.getStatus() == UiState.Status.CONTENT ? View.VISIBLE : View.GONE);
            if (state.getStatus() == UiState.Status.CONTENT) {
                covers.submit(state.getData());
                selection();
            }
        });
        model.selected().observe(getViewLifecycleOwner(), item -> selection());
        model.hasMore().observe(getViewLifecycleOwner(), value -> paging());
        model.loadingMore().observe(getViewLifecycleOwner(), value -> paging());
        model.moreError().observe(getViewLifecycleOwner(), value -> paging());
        filters();
    }
    private void geometry(int width) {
        ((GridLayoutManager) binding.covers.getLayoutManager()).setSpanCount(
                getResources().getConfiguration().screenWidthDp >= 360 && getResources().getConfiguration().fontScale < 1.3f ? 2 : 1);
    }
    private void selection() {
        binding.selectionCount.setText(getString(R.string.arquivo_loaded_count, covers.getItemCount()));
    }
    private void filters() {
        binding.moviesAz.setSelected(!model.descending()); binding.moviesZa.setSelected(model.descending());
        ViewCompat.setStateDescription(binding.moviesAz, !model.descending() ? getString(R.string.movies_order_selected) : null);
        ViewCompat.setStateDescription(binding.moviesZa, model.descending() ? getString(R.string.movies_order_selected) : null);
        binding.moviesAz.setStrokeWidth(dp(!model.descending() ? 2 : 1)); binding.moviesZa.setStrokeWidth(dp(model.descending() ? 2 : 1));
        binding.selectedQuery.setVisibility(model.query().isEmpty() ? View.GONE:View.VISIBLE);
        binding.selectedQuery.setText(getString(R.string.movies_query,model.query()));
    }
    private void paging() {
        boolean loading = Boolean.TRUE.equals(model.loadingMore().getValue()); binding.pageProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.loadMore.setVisibility(Boolean.TRUE.equals(model.hasMore().getValue()) ? View.VISIBLE : View.GONE);
        binding.loadMore.setEnabled(!loading); binding.loadMore.setText(Boolean.TRUE.equals(model.moreError().getValue()) ? R.string.catalog_retry : R.string.catalog_load_more);
    }
    private void search() {
        EditText input=new EditText(requireContext()); input.setId(R.id.movies_search_input); input.setSingleLine();
        input.setMinHeight(dp(48)); input.setHint(R.string.movies_search_hint); input.setText(model.query());
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
        LinearLayout box=new LinearLayout(requireContext()); box.setPadding(dp(20),dp(8),dp(20),dp(8));
        box.addView(input,new LinearLayout.LayoutParams(-1,-2));
        dialog=new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.movies_search).setView(box)
            .setNegativeButton(R.string.catalog_cancel,null).setNeutralButton(R.string.movies_clear,(d,w)->{model.query(""); filters();})
            .setPositiveButton(R.string.movies_search,null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value=input.getText().toString().trim();
            if (value.matches(".*[,|:].*")) { input.setError(getString(R.string.movies_query_invalid)); return; }
            model.query(value); filters(); closeDialog();
        })); dialog.show();
    }
    private void closeDialog() { if (dialog != null) { dialog.dismiss(); dialog = null; } }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    @Override public void onDestroyView() {
        closeDialog(); binding.covers.setAdapter(null); binding.moviesFeaturedList.setAdapter(null);
        super.onDestroyView(); binding = null; covers = null;
    }
}
