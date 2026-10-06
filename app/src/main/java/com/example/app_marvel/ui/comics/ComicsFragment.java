package com.example.app_marvel.ui.comics;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
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
import com.example.app_marvel.data.catalog.CatalogModels.Reference;
import com.example.app_marvel.databinding.FragmentComicsBinding;
import com.example.app_marvel.ui.common.ComicVineNavigation;
import com.example.app_marvel.ui.common.UiState;
import com.example.app_marvel.ui.components.IssueCoverAdapter;
import com.example.app_marvel.ui.components.RecentIssueAdapter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ComicsFragment extends Fragment {
    private FragmentComicsBinding binding;
    private ComicsViewModel model;
    private IssueCoverAdapter covers;
    private AlertDialog dialog;
    private com.google.android.material.bottomsheet.BottomSheetDialog sheet;
    private boolean choosing;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle saved) {
        binding = FragmentComicsBinding.inflate(inflater, parent, false); return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        view.post(() -> { if (binding != null) ((MainActivity) requireActivity()).updateContentInsets(); });
        ViewCompat.setAccessibilityHeading(binding.comicsFeaturedHeading, true);
        var container = ((MarvelApplication) requireActivity().getApplication()).getContainer();
        model = new ViewModelProvider(requireActivity(), new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type, @NonNull CreationExtras extras) {
                if (type != ComicsViewModel.class) throw new IllegalArgumentException("ViewModel não registrado");
                return type.cast(new ComicsViewModel(container.getCatalog(), SavedStateHandleSupport.createSavedStateHandle(extras)));
            }
        }).get(ComicsViewModel.class);
        RecentIssueAdapter highlight = new RecentIssueAdapter(container.getImages(), false, true, true);
        highlight.openWith(id -> ((MainActivity) requireActivity()).openIssue(id));
        binding.comicsFeaturedList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.comicsFeaturedList.setAdapter(highlight);
        model.featured().observe(getViewLifecycleOwner(), state -> {
            binding.comicsFeaturedState.render(state.getStatus(), model::loadFeatured);
            binding.comicsFeaturedList.setVisibility(state.getStatus() == UiState.Status.CONTENT ? View.VISIBLE : View.GONE);
            if (state.getStatus() == UiState.Status.CONTENT) highlight.submit(state.getData());
        });
        GridLayoutManager layout = new GridLayoutManager(requireContext(), 2);
        binding.covers.setLayoutManager(layout); covers = new IssueCoverAdapter(container.getImages(), position -> { model.select(position); ((MainActivity) requireActivity()).openIssue(covers.item(position).id); });
        binding.covers.setAdapter(covers);
        binding.covers.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> { if (r-l > 0 && r-l != or-ol) geometry(r-l); });

        binding.comicsRecent.setOnClickListener(v -> { model.order(false); filters(); });
        binding.comicsOldest.setOnClickListener(v -> { model.order(true); filters(); });
        binding.comicsVolume.setOnClickListener(v -> { choosing = true; model.loadVolumes(); options(model.volumes().getValue()); });
        binding.loadMore.setOnClickListener(v -> model.more());
        model.state().observe(getViewLifecycleOwner(), state -> {
            binding.comicsState.render(state.getStatus(), model::reload);
            if (state.getStatus() == UiState.Status.EMPTY) binding.comicsState.emptyMessage(R.string.comics_empty_title, R.string.comics_empty_body);
            binding.comicsContent.setVisibility(state.getStatus() == UiState.Status.CONTENT ? View.VISIBLE : View.GONE);
            if (state.getStatus() == UiState.Status.CONTENT) {
                covers.submit(state.getData());
                selection();
            }
        });
        model.selected().observe(getViewLifecycleOwner(), item -> selection());
        model.hasMore().observe(getViewLifecycleOwner(), value -> paging());
        model.loadingMore().observe(getViewLifecycleOwner(), value -> paging());
        model.moreError().observe(getViewLifecycleOwner(), value -> paging());
        model.volumes().observe(getViewLifecycleOwner(), this::options);
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
        binding.comicsVolume.setSelected(model.volumeId() != 0);
        binding.comicsRecent.setSelected(!model.oldest()); binding.comicsOldest.setSelected(model.oldest());
        ViewCompat.setStateDescription(binding.comicsRecent, !model.oldest() ? getString(R.string.comics_order_selected) : null);
        ViewCompat.setStateDescription(binding.comicsOldest, model.oldest() ? getString(R.string.comics_order_selected) : null);
        binding.comicsRecent.setStrokeWidth(dp(!model.oldest() ? 2 : 1)); binding.comicsOldest.setStrokeWidth(dp(model.oldest() ? 2 : 1));
        binding.selectedVolume.setVisibility(model.volumeId() == 0 ? View.GONE : View.VISIBLE);
        binding.selectedVolume.setText(getString(R.string.comics_volume_meta,model.volumeLabel()));
        binding.comicsVolume.setContentDescription(model.volumeId() == 0 ? getString(R.string.comics_volume)
                : getString(R.string.comics_volume_meta,model.volumeLabel()));
    }
    private void paging() {
        boolean loading = Boolean.TRUE.equals(model.loadingMore().getValue()); binding.pageProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.loadMore.setVisibility(Boolean.TRUE.equals(model.hasMore().getValue()) ? View.VISIBLE : View.GONE);
        binding.loadMore.setEnabled(!loading); binding.loadMore.setText(Boolean.TRUE.equals(model.moreError().getValue()) ? R.string.catalog_retry : R.string.catalog_load_more);
    }
    private void options(UiState<List<Reference>> state) {
        if (!choosing || binding == null) return; closeDialog();
        if (state.getStatus() == UiState.Status.CONTENT) { choices(state.getData()); return; }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.comics_volume)
                .setNegativeButton(R.string.catalog_cancel,(d,which) -> choosing = false);
        if (state.getStatus() == UiState.Status.LOADING) {
            LinearLayout box = new LinearLayout(requireContext()); box.setPadding(dp(24),dp(16),dp(24),dp(16)); box.setGravity(android.view.Gravity.CENTER);
            box.addView(new ProgressBar(requireContext()),new LinearLayout.LayoutParams(dp(40),dp(40))); builder.setView(box);
        } else builder.setMessage(R.string.catalog_dialog_error).setPositiveButton(R.string.catalog_retry,(d,which) -> { model.loadVolumes(); options(model.volumes().getValue()); });
        dialog = builder.create(); dialog.setOnCancelListener(d -> choosing = false); dialog.show();
    }
    private void choices(List<Reference> choices) {
        Reference[] draft = { new Reference(model.volumeId(), model.volumeLabel(), "") };
        List<Reference> visible = new ArrayList<>();
        LinearLayout box = new LinearLayout(requireContext()); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(16),0,dp(16),0);
        EditText search = new EditText(requireContext()); search.setId(R.id.comics_volume_search); search.setSingleLine(); search.setMinHeight(dp(48)); search.setHint(R.string.comics_search);
        box.addView(search,new LinearLayout.LayoutParams(-1,-2));
        ListView list = new ListView(requireContext()); list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),android.R.layout.simple_list_item_single_choice,new ArrayList<>()); list.setAdapter(adapter);
        box.addView(list,new LinearLayout.LayoutParams(-1,dp(300)));
        java.util.function.Consumer<String> filter = value -> {
            model.volumeQuery(value); String query = normalized(value); visible.clear(); visible.add(new Reference(0,getString(R.string.catalog_all),""));
            for (Reference ref : choices) if (normalized(ref.name).contains(query)) visible.add(ref);
            List<String> labels = new ArrayList<>(); for (Reference ref : visible) labels.add(ref.name);
            adapter.clear(); adapter.addAll(labels); adapter.notifyDataSetChanged(); list.clearChoices();
            for (int i = 0; i < visible.size(); i++) if (visible.get(i).id == draft[0].id) list.setItemChecked(i,true);
        };
        search.setText(model.volumeQuery()); filter.accept(model.volumeQuery());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) { filter.accept(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
        list.setOnItemClickListener((parent,view,position,id) -> draft[0] = visible.get(position));
        sheet = com.example.app_marvel.ui.components.FilterSheet.show(requireContext(),
                "Filtros · " + getString(R.string.comics_volume), box,
                () -> { model.volume(draft[0]); filters(); choosing = false; },
                () -> { draft[0] = new Reference(0, getString(R.string.catalog_all), ""); search.setText(""); filter.accept(""); });

    }
    private String normalized(String value) { return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT); }
    private void closeDialog() { if (dialog != null) { dialog.dismiss(); dialog = null; } if (sheet != null) { sheet.dismiss(); sheet = null; } }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    @Override public void onDestroyView() {
        choosing = false; closeDialog(); binding.covers.setAdapter(null); binding.comicsFeaturedList.setAdapter(null);
        super.onDestroyView(); binding = null; covers = null;
    }
}
