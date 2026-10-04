package com.example.app_marvel.ui.comics;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.example.app_marvel.data.catalog.CatalogModels.Issue;
import com.example.app_marvel.data.catalog.CatalogModels.ComicsCursor;
import com.example.app_marvel.data.catalog.CatalogModels.Reference;
import com.example.app_marvel.data.catalog.MarvelRepository;
import com.example.app_marvel.ui.common.UiState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ComicsViewModel extends ViewModel {
    private final MarvelRepository repository;
    private final SavedStateHandle saved;
    private final MutableLiveData<UiState<List<Issue>>> state = new MutableLiveData<>();
    private final MutableLiveData<UiState<List<Issue>>> featured = new MutableLiveData<>();
    private final MutableLiveData<UiState<List<Reference>>> volumes = new MutableLiveData<>(UiState.empty());
    private final MutableLiveData<Issue> selected = new MutableLiveData<>();
    private final MutableLiveData<Boolean> hasMore = new MutableLiveData<>(false), loadingMore = new MutableLiveData<>(false), moreError = new MutableLiveData<>(false);
    private final Map<Integer, Issue> accumulated = new LinkedHashMap<>();
    private ComicsCursor cursor = ComicsCursor.start();
    private int generation, featuredGeneration;
    public ComicsViewModel(MarvelRepository repository, SavedStateHandle saved) {
        this.repository = repository; this.saved = saved; reload(); loadFeatured();
    }
    public LiveData<UiState<List<Issue>>> state() { return state; }
    public LiveData<UiState<List<Issue>>> featured() { return featured; }
    public LiveData<UiState<List<Reference>>> volumes() { return volumes; }
    public LiveData<Issue> selected() { return selected; }
    public LiveData<Boolean> hasMore() { return hasMore; }
    public LiveData<Boolean> loadingMore() { return loadingMore; }
    public LiveData<Boolean> moreError() { return moreError; }
    public int volumeId() { Integer id = saved.get("volume"); return id == null ? 0 : id; }
    public String volumeLabel() { String label = saved.get("volume_label"); return label == null ? "" : label; }
    public String volumeQuery() { String value = saved.get("volume_query"); return value == null ? "" : value; }
    public void volumeQuery(String value) { saved.set("volume_query", value); }
    public boolean oldest() { return Boolean.TRUE.equals(saved.get("oldest")); }
    public void order(boolean value) { if (value == oldest()) return; saved.set("oldest", value); saved.set("selected", 0); reload(); }
    public void volume(Reference value) {
        saved.set("volume", value.id); saved.set("volume_label", value.id == 0 ? null : value.name); saved.set("selected", 0); reload();
    }
    public void loadFeatured() {
        int stamp = ++featuredGeneration; featured.setValue(UiState.loading());
        repository.recent(result -> {
            if (stamp != featuredGeneration) return;
            featured.setValue(result.failure != null ? UiState.error() : result.data.isEmpty() ? UiState.empty()
                    : UiState.content(Collections.singletonList(result.data.get(0))));
        });
    }
    public void loadVolumes() {
        UiState.Status status = volumes.getValue().getStatus();
        if (status == UiState.Status.LOADING || status == UiState.Status.CONTENT) return;
        volumes.setValue(UiState.loading()); repository.volumes(result -> volumes.setValue(result.failure != null ? UiState.error()
                : result.data.isEmpty() ? UiState.empty() : UiState.content(result.data)));
    }
    public void reload() {
        generation++; cursor = ComicsCursor.start(); accumulated.clear(); selected.setValue(null);
        state.setValue(UiState.loading()); hasMore.setValue(false); loadingMore.setValue(false); moreError.setValue(false); load(false);
    }
    public void more() { if (Boolean.TRUE.equals(hasMore.getValue()) && !Boolean.TRUE.equals(loadingMore.getValue())) load(true); }
    private void load(boolean append) {
        int stamp = generation; if (append) { loadingMore.setValue(true); moreError.setValue(false); }
        repository.comics(volumeId(), oldest(), cursor, result -> {
            if (stamp != generation) return;
            loadingMore.setValue(false);
            if (result.failure != null) { if (append) moreError.setValue(true); else state.setValue(UiState.error()); return; }
            cursor = result.data.nextCursor; hasMore.setValue(result.data.hasMore);
            for (Issue issue : result.data.items) accumulated.putIfAbsent(issue.id, issue);
            List<Issue> items = Collections.unmodifiableList(new ArrayList<>(accumulated.values()));
            state.setValue(items.isEmpty() ? UiState.empty() : UiState.content(items));
            if (selected.getValue() == null && !items.isEmpty()) {
                Integer expected = saved.get("selected"); int position = 0;
                if (expected != null) for (int i = 0; i < items.size(); i++) if (items.get(i).id == expected) position = i;
                select(position);
            }
        });
    }
    public int position() {
        Issue item = selected.getValue(); int i = 0;
        for (Issue current : accumulated.values()) { if (item != null && current.id == item.id) return i; i++; } return 0;
    }
    public void select(int position) {
        List<Issue> items = new ArrayList<>(accumulated.values());
        if (position < 0 || position >= items.size()) return;
        Issue item = items.get(position); if (selected.getValue() != null && selected.getValue().id == item.id) return;
        saved.set("selected", item.id); selected.setValue(item);
    }
    @Override protected void onCleared() { generation++; featuredGeneration++; }
}
