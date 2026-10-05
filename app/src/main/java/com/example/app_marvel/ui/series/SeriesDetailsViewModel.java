package com.example.app_marvel.ui.series;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.example.app_marvel.data.catalog.CatalogDescriptions;
import com.example.app_marvel.data.catalog.CatalogModels.*;
import com.example.app_marvel.data.catalog.MarvelRepository;
import com.example.app_marvel.data.translation.TranslationRepository;
import com.example.app_marvel.ui.common.UiState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SeriesDetailsViewModel extends ViewModel {
    public static final class Group {
        public final MutableLiveData<UiState<List<RelatedItem>>> state = new MutableLiveData<>(UiState.unavailable());
        public final MutableLiveData<Boolean> hasMore = new MutableLiveData<>(false), loading = new MutableLiveData<>(false), error = new MutableLiveData<>(false);
        private final Map<Integer,RelatedItem> items = new LinkedHashMap<>();
        private int offset;
        private boolean pending;
    }
    private final MarvelRepository repository;
    private final TranslationRepository translations;
    private final int seriesId;
    private final SavedStateHandle saved;
    private final MutableLiveData<UiState<SeriesDetails>> detail = new MutableLiveData<>(UiState.loading());
    private final MutableLiveData<UiState<String>> deck = new MutableLiveData<>(UiState.unavailable()), description = new MutableLiveData<>(UiState.unavailable());
    private final Map<String,Group> groups = new LinkedHashMap<>();
    private final Map<String,Integer> translationVersions = new LinkedHashMap<>();
    public final MutableLiveData<UiState<List<Episode>>> episodes=new MutableLiveData<>(UiState.unavailable());
    public final MutableLiveData<Boolean> episodesMore=new MutableLiveData<>(false),episodesLoading=new MutableLiveData<>(false),episodesError=new MutableLiveData<>(false);
    private final Map<Integer,Episode> episodeItems=new LinkedHashMap<>();
    private int episodeOffset;
    private boolean episodesPending;
    private int generation;
    private boolean pending;
    public SeriesDetailsViewModel(MarvelRepository repository, TranslationRepository translations, SavedStateHandle saved) {
        this.repository = repository; this.translations = translations;this.saved=saved;
        Integer id = saved.get("seriesId"); seriesId = id == null ? 0 : id;
        groups.put("characters",new Group()); reload();
    }
    public LiveData<UiState<SeriesDetails>> detail() { return detail; }
    public LiveData<UiState<String>> deck() { return deck; }
    public LiveData<UiState<String>> description() { return description; }
    public Group group(String kind) { return groups.get(kind); }
    public int scroll() { Integer value=saved.get("scroll");return value==null ? 0:value; }
    public void scroll(int value) { saved.set("scroll",value); }
    private SeriesDetails current() { return detail.getValue().getStatus() == UiState.Status.CONTENT ? detail.getValue().getData() : null; }
    public void reload() {
        if (pending) return;
        int stamp = ++generation; pending = true;
        detail.setValue(UiState.loading()); deck.setValue(UiState.unavailable()); description.setValue(UiState.unavailable()); translationVersions.clear();
        episodeOffset=0;episodesPending=false;episodeItems.clear();episodes.setValue(UiState.unavailable());episodesMore.setValue(false);episodesLoading.setValue(false);episodesError.setValue(false);
        for (Group group : groups.values()) {
            group.offset = 0; group.pending = false; group.items.clear(); group.state.setValue(UiState.unavailable());
            group.hasMore.setValue(false); group.loading.setValue(false); group.error.setValue(false);
        }
        repository.seriesDetails(seriesId,result -> {
            if (stamp != generation) return;
            pending = false; detail.setValue(result.failure == null ? UiState.content(result.data) : UiState.error());
            if (result.failure != null) return;
            description.setValue(MarvelRepository.plain(result.data.originalDescription).isEmpty() ? UiState.unavailable():UiState.empty());
            translate("deck");load("characters",false);loadEpisodes(false);
        });
    }
    public void translate(String field) {
        SeriesDetails item = current(); if (item == null) return;
        MutableLiveData<UiState<String>> target = field.equals("deck") ? deck : description;
        String source = MarvelRepository.plain(field.equals("deck") ? item.originalDeck : item.originalDescription);
        if (source.isEmpty()) { target.setValue(UiState.unavailable()); return; }
        if (target.getValue().getStatus() == UiState.Status.LOADING || target.getValue().getStatus() == UiState.Status.CONTENT) return;
        int stamp = generation, version = translationVersions.getOrDefault(field,0)+1;
        translationVersions.put(field,version); target.setValue(UiState.loading());
        CatalogDescriptions.translateSeries(translations,item,field,result -> {
            if (stamp != generation || translationVersions.getOrDefault(field,0) != version) return;
            target.setValue(result.getFailure() == null ? UiState.content(result.getText()) : UiState.error());
        });
    }
    public void load(String kind, boolean append) {
        SeriesDetails item = current(); Group group = groups.get(kind);
        if (item == null || group == null || group.pending || (append && !Boolean.TRUE.equals(group.hasMore.getValue()))) return;
        if (item.characters.isEmpty()) { group.state.setValue(UiState.unavailable()); return; }
        int stamp = generation; group.pending = true; group.error.setValue(false);
        if (append) group.loading.setValue(true); else group.state.setValue(UiState.loading());
        repository.seriesCharacters(item,group.offset,result -> {
            if (stamp != generation) return;
            group.pending = false; group.loading.setValue(false);
            if (result.failure != null) { if (append) group.error.setValue(true); else group.state.setValue(UiState.error()); return; }
            group.offset = result.data.nextOffset; group.hasMore.setValue(result.data.hasMore);
            for (RelatedItem related : result.data.items) group.items.putIfAbsent(related.id,related);
            group.state.setValue(group.items.isEmpty() ? result.data.hasMore ? UiState.empty() : UiState.unavailable()
                    : UiState.content(Collections.unmodifiableList(new ArrayList<>(group.items.values()))));
        });
    }
    public void loadEpisodes(boolean append) {
        SeriesDetails item=current();if(item==null || episodesPending || (append && !Boolean.TRUE.equals(episodesMore.getValue()))) return;
        if(item.episodes.isEmpty()) { episodes.setValue(UiState.unavailable());return; }
        int stamp=generation;episodesPending=true;episodesError.setValue(false);
        if(append) episodesLoading.setValue(true);else episodes.setValue(UiState.loading());
        repository.seriesEpisodes(item,episodeOffset,result -> {
            if(stamp!=generation) return;episodesPending=false;episodesLoading.setValue(false);
            if(result.failure!=null) { if(append) episodesError.setValue(true);else episodes.setValue(UiState.error());return; }
            episodeOffset=result.data.nextOffset;episodesMore.setValue(result.data.hasMore);
            for(Episode episode:result.data.items) episodeItems.putIfAbsent(episode.id,episode);
            episodes.setValue(episodeItems.isEmpty() ? UiState.unavailable():UiState.content(Collections.unmodifiableList(new ArrayList<>(episodeItems.values()))));
        });
    }
    @Override protected void onCleared() { generation++; }
}
