package com.example.app_marvel.ui.characters;

import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.example.app_marvel.data.catalog.CatalogModels.Character;
import com.example.app_marvel.data.catalog.MarvelRepository;
import com.example.app_marvel.data.translation.TranslationRepository;
import com.example.app_marvel.ui.common.UiState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class CharactersViewModel extends ViewModel {
    public static final class Choice {
        public final int id; public final String label;
        public Choice(int id, String label) { this.id = id; this.label = label; }
    }
    private final MarvelRepository repository;
    private final TranslationRepository translations;
    private final SavedStateHandle saved;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MutableLiveData<UiState<List<Character>>> state = new MutableLiveData<>();
    private final MutableLiveData<Character> selected = new MutableLiveData<>();
    private final MutableLiveData<UiState<String>> selectedOrigin = new MutableLiveData<>(UiState.empty());
    private final MutableLiveData<UiState<List<Choice>>> origins = new MutableLiveData<>(UiState.empty());
    private final MutableLiveData<UiState<List<Choice>>> teams = new MutableLiveData<>(UiState.empty());
    private final MutableLiveData<Boolean> loadingMore = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> moreError = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> hasMore = new MutableLiveData<>(false);
    private final List<Character> accumulated = new ArrayList<>();
    private final Runnable search = this::reload;
    private int nextOffset, generation, originGeneration;

    public CharactersViewModel(MarvelRepository repository, TranslationRepository translations, SavedStateHandle saved) {
        this.repository = repository; this.translations = translations; this.saved = saved; reload();
    }
    public LiveData<UiState<List<Character>>> getState() { return state; }
    public LiveData<Character> getSelected() { return selected; }
    public LiveData<UiState<String>> getSelectedOrigin() { return selectedOrigin; }
    public LiveData<UiState<List<Choice>>> getOrigins() { return origins; }
    public LiveData<UiState<List<Choice>>> getTeams() { return teams; }
    public LiveData<Boolean> getLoadingMore() { return loadingMore; }
    public LiveData<Boolean> getMoreError() { return moreError; }
    public LiveData<Boolean> getHasMore() { return hasMore; }
    public String query() { String value = saved.get("query"); return value == null ? "" : value; }
    public int filter(String name) { Integer value = saved.get(name); return value == null ? 0 : value; }
    public String filterLabel(String name, String fallback) { String value = saved.get(name + "_label"); return value == null ? fallback : value; }
    public void query(String value) {
        if (value.equals(query())) return;
        saved.set("query", value); saved.set("selected", 0); generation++;
        hasMore.setValue(false); loadingMore.setValue(false);
        handler.removeCallbacks(search); handler.postDelayed(search, 400);
    }
    public void filter(String name, int id, String label) {
        saved.set(name, id); saved.set(name + "_label", id == 0 ? null : label); saved.set("selected", 0); reload();
    }
    public void clearFilters() {
        for (String name : new String[]{"origin", "team", "gender"}) { saved.set(name, 0); saved.set(name + "_label", null); }
        saved.set("selected", 0); reload();
    }
    public void reload() {
        handler.removeCallbacks(search); generation++; nextOffset = 0; accumulated.clear(); selected.setValue(null);
        selectedOrigin.setValue(UiState.empty()); originGeneration++;
        state.setValue(UiState.loading()); loadingMore.setValue(false); moreError.setValue(false); hasMore.setValue(false); load(false);
    }
    public void more() {
        if (!Boolean.TRUE.equals(loadingMore.getValue()) && Boolean.TRUE.equals(hasMore.getValue())) load(true);
    }
    private void load(boolean append) {
        int stamp = generation;
        if (append) { loadingMore.setValue(true); moreError.setValue(false); }
        repository.characters(query(), filter("origin"), filter("gender"), filter("team"), nextOffset, result -> {
            if (stamp != generation) return;
            loadingMore.setValue(false);
            if (result.failure != null) {
                if (append) moreError.setValue(true); else state.setValue(UiState.error());
                return;
            }
            nextOffset = result.data.nextOffset; hasMore.setValue(result.data.hasMore);
            for (Character item : result.data.characters) {
                boolean exists = false; for (Character old : accumulated) if (old.id == item.id) { exists = true; break; }
                if (!exists) accumulated.add(item);
            }
            state.setValue(accumulated.isEmpty() ? UiState.empty() : UiState.content(Collections.unmodifiableList(new ArrayList<>(accumulated))));
            if (selected.getValue() == null && !accumulated.isEmpty()) {
                Integer expected = saved.get("selected"); int position = 0;
                if (expected != null) for (int i = 0; i < accumulated.size(); i++) if (accumulated.get(i).id == expected) position = i;
                select(position);
            }
        });
    }
    public int selectedPosition() {
        Character item = selected.getValue();
        if (item != null) for (int i = 0; i < accumulated.size(); i++) if (accumulated.get(i).id == item.id) return i;
        return 0;
    }
    public void select(int position) {
        if (position < 0 || position >= accumulated.size()) return;
        Character item = accumulated.get(position), current = selected.getValue();
        if (current != null && current.id == item.id) return;
        saved.set("selected", item.id); selected.setValue(item); int stamp = ++originGeneration;
        selectedOrigin.setValue(item.origin.isEmpty() ? UiState.empty() : UiState.loading());
        if (!item.origin.isEmpty()) translations.translate("origin:" + item.originId, "name", item.origin, result -> {
            if (stamp == originGeneration) selectedOrigin.setValue(result.getFailure() == null ? UiState.content(result.getText()) : UiState.error());
        });
    }
    public void retryOrigin() {
        Character item = selected.getValue(); if (item == null) return;
        int position = selectedPosition(); selected.setValue(null); select(position);
    }
    public void loadOrigins() {
        if (origins.getValue().getStatus() == UiState.Status.LOADING || origins.getValue().getStatus() == UiState.Status.CONTENT) return;
        origins.setValue(UiState.loading());
        repository.origins(result -> {
            if (result.failure != null) { origins.setValue(UiState.error()); return; }
            List<Choice> choices = new ArrayList<>(); int[] remaining = {result.data.size()}; boolean[] failure = {false};
            if (remaining[0] == 0) { origins.setValue(UiState.empty()); return; }
            result.data.forEach(ref -> translations.translate("origin:" + ref.id, "name", ref.name, translated -> {
                if (translated.getFailure() == null) choices.add(new Choice(ref.id, translated.getText())); else failure[0] = true;
                if (--remaining[0] == 0) {
                    choices.sort(Comparator.comparing(choice -> choice.label));
                    origins.setValue(failure[0] ? UiState.error() : UiState.content(choices));
                }
            }));
        });
    }
    public void loadTeams() {
        if (teams.getValue().getStatus() == UiState.Status.LOADING || teams.getValue().getStatus() == UiState.Status.CONTENT) return;
        teams.setValue(UiState.loading());
        repository.teams(result -> {
            if (result.failure != null) { teams.setValue(UiState.error()); return; }
            List<Choice> choices = new ArrayList<>(); result.data.forEach(ref -> choices.add(new Choice(ref.id, ref.name)));
            teams.setValue(choices.isEmpty() ? UiState.empty() : UiState.content(choices));
        });
    }
    @Override protected void onCleared() { generation++; originGeneration++; handler.removeCallbacks(search); }
}
