package com.example.app_marvel.ui.series;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.example.app_marvel.data.catalog.CatalogModels.Series;
import com.example.app_marvel.data.catalog.MarvelRepository;
import com.example.app_marvel.ui.common.UiState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SeriesViewModel extends ViewModel {
    private final MarvelRepository repository;
    private final SavedStateHandle saved;
    private final MutableLiveData<UiState<List<Series>>> state = new MutableLiveData<>();
    private final MutableLiveData<UiState<List<Series>>> featured = new MutableLiveData<>();
    private final MutableLiveData<Series> selected = new MutableLiveData<>();
    private final MutableLiveData<Boolean> hasMore = new MutableLiveData<>(false), loadingMore = new MutableLiveData<>(false), moreError = new MutableLiveData<>(false);
    private final Map<Integer, Series> accumulated = new LinkedHashMap<>();
    private int cursor;
    private int generation, featuredGeneration;
    public SeriesViewModel(MarvelRepository repository, SavedStateHandle saved) {
        this.repository = repository; this.saved = saved; reload(); loadFeatured();
    }
    public LiveData<UiState<List<Series>>> state() { return state; }
    public LiveData<UiState<List<Series>>> featured() { return featured; }
    public LiveData<Series> selected() { return selected; }
    public LiveData<Boolean> hasMore() { return hasMore; }
    public LiveData<Boolean> loadingMore() { return loadingMore; }
    public LiveData<Boolean> moreError() { return moreError; }
    public String query() { String value=saved.get("query"); return value==null ? "":value; }
    public void query(String value) { if (query().equals(value)) return; saved.set("query",value); saved.set("selected",0); reload(); }
    public boolean descending() { return Boolean.TRUE.equals(saved.get("descending")); }
    public void order(boolean value) { if (value==descending()) return; saved.set("descending",value); saved.set("selected",0); reload(); }
    public void loadFeatured() {
        int stamp = ++featuredGeneration; featured.setValue(UiState.loading());
        repository.featuredSeries(result -> {
            if (stamp != featuredGeneration) return;
            featured.setValue(result.failure != null ? UiState.error() : UiState.content(Collections.singletonList(result.data)));
        });
    }
    public void reload() {
        generation++; cursor = 0; accumulated.clear(); selected.setValue(null);
        state.setValue(UiState.loading()); hasMore.setValue(false); loadingMore.setValue(false); moreError.setValue(false); load(false);
    }
    public void more() { if (Boolean.TRUE.equals(hasMore.getValue()) && !Boolean.TRUE.equals(loadingMore.getValue())) load(true); }
    private void load(boolean append) {
        int stamp = generation; if (append) { loadingMore.setValue(true); moreError.setValue(false); }
        repository.series(query(), descending(), cursor, result -> {
            if (stamp != generation) return;
            loadingMore.setValue(false);
            if (result.failure != null) { if (append) moreError.setValue(true); else state.setValue(UiState.error()); return; }
            cursor = result.data.nextOffset; hasMore.setValue(result.data.hasMore);
            for (Series item : result.data.items) accumulated.putIfAbsent(item.id, item);
            List<Series> items = Collections.unmodifiableList(new ArrayList<>(accumulated.values()));
            state.setValue(items.isEmpty() ? UiState.empty() : UiState.content(items));
            if (selected.getValue() == null && !items.isEmpty()) {
                Integer expected = saved.get("selected"); int position = 0;
                if (expected != null) for (int i = 0; i < items.size(); i++) if (items.get(i).id == expected) position = i;
                select(position);
            }
        });
    }
    public int position() {
        Series item = selected.getValue(); int i = 0;
        for (Series current : accumulated.values()) { if (item != null && current.id == item.id) return i; i++; } return 0;
    }
    public void select(int position) {
        List<Series> items = new ArrayList<>(accumulated.values());
        if (position < 0 || position >= items.size()) return;
        Series item = items.get(position); if (selected.getValue() != null && selected.getValue().id == item.id) return;
        saved.set("selected", item.id); selected.setValue(item);
    }
    @Override protected void onCleared() { generation++; featuredGeneration++; }
}
