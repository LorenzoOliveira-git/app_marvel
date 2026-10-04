package com.example.app_marvel.ui.characters;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
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
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.example.app_marvel.MainActivity;
import com.example.app_marvel.MarvelApplication;
import com.example.app_marvel.R;
import com.example.app_marvel.databinding.FragmentCharactersBinding;
import com.example.app_marvel.di.AppContainer;
import com.example.app_marvel.ui.characters.CharactersViewModel.Choice;
import com.example.app_marvel.ui.common.UiState;
import com.example.app_marvel.ui.components.CharacterPortraitAdapter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class CharactersFragment extends Fragment {
    private FragmentCharactersBinding binding;
    private CharactersViewModel model;
    private CharacterPortraitAdapter portraits;
    private final PagerSnapHelper snap = new PagerSnapHelper();
    private AlertDialog dialog;
    private String requestedFilter;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle saved) {
        binding = FragmentCharactersBinding.inflate(inflater, parent, false); return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        view.post(() -> { if (binding != null) ((MainActivity) requireActivity()).updateContentInsets(); });
        ViewCompat.setAccessibilityHeading(binding.characterName, true);
        AppContainer container = ((MarvelApplication) requireActivity().getApplication()).getContainer();
        model = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type, @NonNull CreationExtras extras) {
                if (type != CharactersViewModel.class) throw new IllegalArgumentException("ViewModel não registrado");
                return type.cast(new CharactersViewModel(container.getCatalog(), container.getTranslations(), SavedStateHandleSupport.createSavedStateHandle(extras)));
            }
        }).get(CharactersViewModel.class);
        LinearLayoutManager layout = new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false);
        binding.portraits.setLayoutManager(layout);
        portraits = new CharacterPortraitAdapter(container.getImages(), position -> move(position));
        binding.portraits.setAdapter(portraits); snap.attachToRecyclerView(binding.portraits);
        binding.portraits.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l > 0 && r - l != or - ol) geometry(r - l);
        });
        binding.portraits.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView recycler, int dx, int dy) { portraits.updateFocus(recycler); }
            @Override public void onScrollStateChanged(@NonNull RecyclerView recycler, int state) {
                if (state == RecyclerView.SCROLL_STATE_IDLE) {
                    View focused = snap.findSnapView(layout);
                    if (focused != null) model.select(layout.getPosition(focused));
                    renderSelection();
                }
            }
        });
        binding.searchName.setText(model.query());
        binding.searchName.addTextChangedListener(watcher(text -> model.query(text)));
        binding.searchName.setOnEditorActionListener((v, action, event) -> {
            ((InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(), 0);
            v.clearFocus(); return true;
        });
        binding.originFilter.setOnClickListener(v -> requestChoices("origin"));
        binding.teamFilter.setOnClickListener(v -> requestChoices("team"));
        binding.genderFilter.setOnClickListener(v -> choiceDialog("gender", Arrays.asList(new Choice(1, getString(R.string.catalog_male)), new Choice(2, getString(R.string.catalog_female)))));
        binding.clearFilters.setOnClickListener(v -> { model.clearFilters(); renderFilters(); });
        binding.loadMore.setOnClickListener(v -> model.more());
        binding.previousCharacter.setOnClickListener(v -> move(model.selectedPosition() - 1));
        binding.nextCharacter.setOnClickListener(v -> move(model.selectedPosition() + 1));
        binding.characterMore.setOnClickListener(v -> {
            var item = model.getSelected().getValue(); if (item != null) ((MainActivity) requireActivity()).openCharacter(item.id);
        });
        binding.retryOrigin.setOnClickListener(v -> model.retryOrigin());
        model.getState().observe(getViewLifecycleOwner(), state -> {
            binding.catalogState.render(state.getStatus(), model::reload);
            if (state.getStatus() == UiState.Status.EMPTY) binding.catalogState.emptyMessage(R.string.catalog_selection_empty_title, R.string.catalog_selection_empty_body);
            binding.catalogContent.setVisibility(state.getStatus() == UiState.Status.CONTENT ? View.VISIBLE : View.GONE);
            if (state.getStatus() == UiState.Status.CONTENT) {
                portraits.submit(state.getData());
                binding.portraits.post(() -> { if (binding != null) { portraits.updateFocus(binding.portraits); renderSelection(); } });
            } else portraits.submit(java.util.Collections.emptyList());
        });
        model.getSelected().observe(getViewLifecycleOwner(), item -> {
            renderSelection();
            if (item != null && binding.portraits.getScrollState() == RecyclerView.SCROLL_STATE_IDLE) {
                View focused = snap.findSnapView(layout);
                if (focused == null || layout.getPosition(focused) != model.selectedPosition()) layout.scrollToPositionWithOffset(model.selectedPosition(), 0);
                binding.portraits.post(() -> { if (binding != null) portraits.updateFocus(binding.portraits); });
            }
        });
        model.getSelectedOrigin().observe(getViewLifecycleOwner(), state -> renderSelection());
        model.getHasMore().observe(getViewLifecycleOwner(), value -> renderPaging());
        model.getLoadingMore().observe(getViewLifecycleOwner(), value -> renderPaging());
        model.getMoreError().observe(getViewLifecycleOwner(), value -> renderPaging());
        model.getOrigins().observe(getViewLifecycleOwner(), state -> options("origin", state));
        model.getTeams().observe(getViewLifecycleOwner(), state -> options("team", state));
        renderFilters();
    }
    private void geometry(int width) {
        int cardWidth = Math.min(dp(280), Math.round(width * .56f));
        int cardHeight = Math.round(cardWidth * 1.52f);
        portraits.setGeometry(cardWidth, cardHeight);
        int itemWidth = cardWidth + dp(16), side = Math.max(0, (width - itemWidth) / 2);
        binding.portraits.setPadding(side, 0, side, 0);
        ViewGroup.LayoutParams params = binding.portraits.getLayoutParams(); params.height = cardHeight + dp(16); binding.portraits.setLayoutParams(params);
        ((LinearLayoutManager) binding.portraits.getLayoutManager()).scrollToPositionWithOffset(model.selectedPosition(), 0);
        binding.portraits.post(() -> { if (binding != null) portraits.updateFocus(binding.portraits); });
    }
    private void move(int position) {
        if (position < 0 || position >= portraits.getItemCount()) return;
        binding.portraits.smoothScrollToPosition(position);
    }
    private void renderSelection() {
        var item = model.getSelected().getValue(); if (item == null) return;
        binding.characterName.setText(item.name);
        UiState<String> origin = model.getSelectedOrigin().getValue();
        String label = origin.getStatus() == UiState.Status.CONTENT ? origin.getData() : "";
        binding.characterMeta.setText(item.realName + (item.realName.isEmpty() || label.isEmpty() ? "" : " — ") + label);
        binding.characterMeta.setVisibility(item.realName.isEmpty() && label.isEmpty() ? View.GONE : View.VISIBLE);
        binding.retryOrigin.setVisibility(origin.getStatus() == UiState.Status.ERROR ? View.VISIBLE : View.GONE);
        binding.characterMore.setEnabled(item.id > 0);
        int position = model.selectedPosition();
        binding.selectionCount.setText(getString(R.string.catalog_count, position + 1, portraits.getItemCount()));
        binding.previousCharacter.setEnabled(position > 0); binding.nextCharacter.setEnabled(position + 1 < portraits.getItemCount());
    }
    private void renderPaging() {
        boolean loading = Boolean.TRUE.equals(model.getLoadingMore().getValue());
        binding.pageProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.loadMore.setVisibility(Boolean.TRUE.equals(model.getHasMore().getValue()) ? View.VISIBLE : View.GONE);
        binding.loadMore.setEnabled(!loading);
        binding.loadMore.setText(Boolean.TRUE.equals(model.getMoreError().getValue()) ? R.string.catalog_retry : R.string.catalog_load_more);
    }
    private void renderFilters() {
        binding.originFilter.setText(model.filterLabel("origin", getString(R.string.catalog_origin)));
        binding.teamFilter.setText(model.filterLabel("team", getString(R.string.catalog_teams)));
        binding.genderFilter.setText(model.filterLabel("gender", getString(R.string.catalog_profile)));
        binding.clearFilters.setVisibility(model.filter("origin") + model.filter("team") + model.filter("gender") == 0 ? View.GONE : View.VISIBLE);
    }
    private void requestChoices(String name) {
        requestedFilter = name;
        if (name.equals("origin")) { model.loadOrigins(); options(name, model.getOrigins().getValue()); }
        else { model.loadTeams(); options(name, model.getTeams().getValue()); }
    }
    private void options(String name, UiState<List<Choice>> state) {
        if (!name.equals(requestedFilter) || binding == null) return;
        closeDialog(false);
        if (state.getStatus() == UiState.Status.CONTENT) { requestedFilter = null; choiceDialog(name, state.getData()); return; }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext()).setTitle(title(name)).setNegativeButton(R.string.catalog_cancel, (d, which) -> requestedFilter = null);
        if (state.getStatus() == UiState.Status.LOADING) {
            ProgressBar progress = new ProgressBar(requireContext());
            LinearLayout box = new LinearLayout(requireContext()); box.setPadding(dp(24), dp(16), dp(24), dp(16)); box.setGravity(android.view.Gravity.CENTER);
            box.addView(progress, new LinearLayout.LayoutParams(dp(40), dp(40))); builder.setView(box);
        } else builder.setMessage(R.string.catalog_dialog_error).setPositiveButton(R.string.catalog_retry, (d, which) -> requestChoices(name));
        dialog = builder.create(); dialog.setOnCancelListener(d -> requestedFilter = null); dialog.show();
    }
    private int title(String name) { return name.equals("origin") ? R.string.catalog_origin : name.equals("team") ? R.string.catalog_teams : R.string.catalog_gender; }
    private void choiceDialog(String name, List<Choice> values) {
        closeDialog(true);
        List<Choice> choices = new ArrayList<>(); choices.add(new Choice(0, getString(R.string.catalog_all))); choices.addAll(values);
        List<Choice> visible = new ArrayList<>(choices);
        LinearLayout box = new LinearLayout(requireContext()); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(16), 0, dp(16), 0);
        EditText search = new EditText(requireContext()); search.setSingleLine(); search.setMinHeight(dp(48)); search.setHint(R.string.catalog_search_team);
        if (name.equals("team")) box.addView(search, new LinearLayout.LayoutParams(-1, -2));
        ListView list = new ListView(requireContext()); list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_single_choice, labels(visible)); list.setAdapter(adapter);
        box.addView(list, new LinearLayout.LayoutParams(-1, dp(300)));
        dialog = new MaterialAlertDialogBuilder(requireContext()).setTitle(title(name)).setView(box).setNegativeButton(R.string.catalog_cancel, null).create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            Choice chosen = visible.get(position); model.filter(name, chosen.id, chosen.label); renderFilters(); closeDialog(true);
        });
        search.addTextChangedListener(watcher(value -> {
            visible.clear(); String query = normalized(value);
            for (Choice choice : choices) if (choice.id == 0 || normalized(choice.label).contains(query)) visible.add(choice);
            adapter.clear(); adapter.addAll(labels(visible)); adapter.notifyDataSetChanged(); markChecked(list, visible, name);
        }));
        dialog.show(); markChecked(list, visible, name);
    }
    private void markChecked(ListView list, List<Choice> choices, String name) {
        list.clearChoices(); for (int i = 0; i < choices.size(); i++) if (choices.get(i).id == model.filter(name)) list.setItemChecked(i, true);
    }
    private List<String> labels(List<Choice> choices) { List<String> labels = new ArrayList<>(); for (Choice choice : choices) labels.add(choice.label); return labels; }
    private String normalized(String value) { return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT); }
    private void closeDialog(boolean clear) {
        if (clear) requestedFilter = null;
        if (dialog != null) { dialog.dismiss(); dialog = null; }
    }
    private TextWatcher watcher(java.util.function.Consumer<String> changed) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { changed.accept(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        };
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override public void onDestroyView() {
        closeDialog(true); snap.attachToRecyclerView(null); binding.portraits.setAdapter(null);
        super.onDestroyView(); binding = null; portraits = null;
    }
}
