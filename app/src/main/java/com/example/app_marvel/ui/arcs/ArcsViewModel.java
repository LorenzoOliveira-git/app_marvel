package com.example.app_marvel.ui.arcs;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.example.app_marvel.data.catalog.CatalogModels.StoryArc;
import com.example.app_marvel.data.catalog.MarvelRepository;
import com.example.app_marvel.ui.common.UiState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArcsViewModel extends ViewModel {
    private final MarvelRepository repository;
    private final SavedStateHandle saved;
    private final MutableLiveData<UiState<List<StoryArc>>> state=new MutableLiveData<>(UiState.loading());
    private final MutableLiveData<Boolean> hasMore=new MutableLiveData<>(false),loadingMore=new MutableLiveData<>(false),moreError=new MutableLiveData<>(false);
    private final Map<Integer,StoryArc> accumulated=new LinkedHashMap<>();
    private int generation,offset;
    private boolean pending;
    public ArcsViewModel(MarvelRepository repository,SavedStateHandle saved) { this.repository=repository;this.saved=saved;reload(); }
    public LiveData<UiState<List<StoryArc>>> state() { return state; }
    public LiveData<Boolean> hasMore() { return hasMore; }
    public LiveData<Boolean> loadingMore() { return loadingMore; }
    public LiveData<Boolean> moreError() { return moreError; }
    public String query() { String value=saved.get("query");return value==null ? "":value; }
    public String draft() { String value=saved.get("draft");return value==null ? query():value; }
    public void draft(String value) { saved.set("draft",value); }
    public boolean descending() { return Boolean.TRUE.equals(saved.get("descending")); }
    public int scroll() { Integer value=saved.get("scroll");return value==null ? 0:value; }
    public void scroll(int value) { saved.set("scroll",value); }
    public void search(String value) {
        String clean=value.trim();draft(value);if (clean.equals(query())) return;
        saved.set("query",clean);scroll(0);reload();
    }
    public void order(boolean value) { if (value==descending()) return;saved.set("descending",value);scroll(0);reload(); }
    public void clear() { saved.set("query","");saved.set("draft","");scroll(0);reload(); }
    public void reload() {
        generation++;pending=false;offset=0;accumulated.clear();hasMore.setValue(false);loadingMore.setValue(false);moreError.setValue(false);state.setValue(UiState.loading());load(false);
    }
    public void more() { if (!pending && Boolean.TRUE.equals(hasMore.getValue())) load(true); }
    private void load(boolean append) {
        if (pending) return;pending=true;int stamp=generation;
        if (append) { loadingMore.setValue(true);moreError.setValue(false); }
        repository.arcs(query(),descending(),offset,result -> {
            if (stamp!=generation) return;pending=false;loadingMore.setValue(false);
            if (result.failure!=null) { if (append) moreError.setValue(true);else state.setValue(UiState.error());return; }
            offset=result.data.nextOffset;hasMore.setValue(result.data.hasMore);
            for (StoryArc arc:result.data.items) accumulated.putIfAbsent(arc.id,arc);
            state.setValue(accumulated.isEmpty() ? UiState.empty():UiState.content(Collections.unmodifiableList(new ArrayList<>(accumulated.values()))));
        });
    }
    @Override protected void onCleared() { generation++; }
}
