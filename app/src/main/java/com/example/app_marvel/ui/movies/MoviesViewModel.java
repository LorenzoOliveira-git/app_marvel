package com.example.app_marvel.ui.movies;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.example.app_marvel.data.catalog.CatalogModels.Movie;
import com.example.app_marvel.data.catalog.MarvelRepository;
import com.example.app_marvel.ui.common.UiState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MoviesViewModel extends ViewModel {
    private final MarvelRepository repository;
    private final SavedStateHandle saved;
    private final MutableLiveData<UiState<List<Movie>>> state = new MutableLiveData<>();
    private final MutableLiveData<UiState<List<Movie>>> featured = new MutableLiveData<>();
    private final MutableLiveData<Movie> selected = new MutableLiveData<>();
    private final MutableLiveData<Boolean> hasMore = new MutableLiveData<>(false), loadingMore = new MutableLiveData<>(false), moreError = new MutableLiveData<>(false);
    private final Map<Integer, Movie> accumulated = new LinkedHashMap<>();
    private int cursor;
    private int generation, featuredGeneration;
    public MoviesViewModel(MarvelRepository repository, SavedStateHandle saved) {
        this.repository = repository; this.saved = saved; reload(); loadFeatured();
    }
    public LiveData<UiState<List<Movie>>> state() { return state; }
    public LiveData<UiState<List<Movie>>> featured() { return featured; }
    public LiveData<Movie> selected() { return selected; }
    public LiveData<Boolean> hasMore() { return hasMore; }
    public LiveData<Boolean> loadingMore() { return loadingMore; }
    public LiveData<Boolean> moreError() { return moreError; }
    public String query() { String value=saved.get("query"); return value==null ? "":value; }
    public void query(String value) { if (query().equals(value)) return; saved.set("query",value); saved.set("selected",0); reload(); }
    public boolean descending() { return Boolean.TRUE.equals(saved.get("descending")); }
    public void order(boolean value) { if (value==descending()) return; saved.set("descending",value); saved.set("selected",0); reload(); }
    public void loadFeatured() {
        int stamp = ++featuredGeneration; featured.setValue(UiState.loading());
        repository.featuredMovie(result -> {
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
        repository.movies(query(), descending(), cursor, result -> {
            if (stamp != generation) return;
            loadingMore.setValue(false);
            if (result.failure != null) { if (append) moreError.setValue(true); else state.setValue(UiState.error()); return; }
            cursor = result.data.nextOffset; hasMore.setValue(result.data.hasMore);
            for (Movie movie : result.data.items) accumulated.putIfAbsent(movie.id, movie);
            List<Movie> items = Collections.unmodifiableList(new ArrayList<>(accumulated.values()));
            state.setValue(items.isEmpty() ? UiState.empty() : UiState.content(items));
            if (selected.getValue() == null && !items.isEmpty()) {
                Integer expected = saved.get("selected"); int position = 0;
                if (expected != null) for (int i = 0; i < items.size(); i++) if (items.get(i).id == expected) position = i;
                select(position);
            }
        });
    }
    public int position() {
        Movie item = selected.getValue(); int i = 0;
        for (Movie current : accumulated.values()) { if (item != null && current.id == item.id) return i; i++; } return 0;
    }
    public void select(int position) {
        List<Movie> items = new ArrayList<>(accumulated.values());
        if (position < 0 || position >= items.size()) return;
        Movie item = items.get(position); if (selected.getValue() != null && selected.getValue().id == item.id) return;
        saved.set("selected", item.id); selected.setValue(item);
    }
    @Override protected void onCleared() { generation++; featuredGeneration++; }
}
