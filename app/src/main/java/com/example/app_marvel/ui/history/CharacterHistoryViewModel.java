package com.example.app_marvel.ui.history;

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

public final class CharacterHistoryViewModel extends ViewModel {
    private final MarvelRepository repository;
    private final TranslationRepository translations;
    private final int characterId;
    private final MutableLiveData<UiState<CharacterDetails>> detail = new MutableLiveData<>(UiState.loading());
    private final MutableLiveData<UiState<String>> description = new MutableLiveData<>(UiState.unavailable());
    private final MutableLiveData<UiState<String>> origin = new MutableLiveData<>(UiState.unavailable());
    private final MutableLiveData<UiState<List<Issue>>> first = new MutableLiveData<>(UiState.unavailable());
    private final MutableLiveData<UiState<List<Issue>>> appearances = new MutableLiveData<>(UiState.unavailable());
    private final MutableLiveData<Boolean> hasMore = new MutableLiveData<>(false), loadingMore = new MutableLiveData<>(false), moreError = new MutableLiveData<>(false);
    private final Map<Integer, Issue> accumulated = new LinkedHashMap<>();
    private AppearanceIndex index;
    private int generation, translationGeneration, offset;
    private boolean pending, firstPending, pagePending;
    public CharacterHistoryViewModel(MarvelRepository repository, TranslationRepository translations, SavedStateHandle saved) {
        this.repository = repository; this.translations = translations;
        Integer id = saved.get("characterId"); characterId = id == null ? 0 : id;
        reload();
    }
    public LiveData<UiState<CharacterDetails>> getDetail() { return detail; }
    public LiveData<UiState<String>> getDescription() { return description; }
    public LiveData<UiState<String>> getOrigin() { return origin; }
    public LiveData<UiState<List<Issue>>> getFirst() { return first; }
    public LiveData<UiState<List<Issue>>> getAppearances() { return appearances; }
    public LiveData<Boolean> getHasMore() { return hasMore; }
    public LiveData<Boolean> getLoadingMore() { return loadingMore; }
    public LiveData<Boolean> getMoreError() { return moreError; }
    private CharacterDetails current() {
        UiState<CharacterDetails> state = detail.getValue(); return state.getStatus() == UiState.Status.CONTENT ? state.getData() : null;
    }
    public void reload() {
        if (pending) return;
        int stamp = ++generation; translationGeneration++; pending = true; firstPending = false; pagePending = false;
        index = null; offset = 0; accumulated.clear(); detail.setValue(UiState.loading());
        description.setValue(UiState.unavailable()); origin.setValue(UiState.unavailable()); first.setValue(UiState.unavailable()); appearances.setValue(UiState.unavailable());
        hasMore.setValue(false); loadingMore.setValue(false); moreError.setValue(false);
        repository.details(characterId, result -> {
            if (stamp != generation) return;
            pending = false; detail.setValue(result.failure == null ? UiState.content(result.data) : UiState.error());
            if (result.failure == null) { translate(); loadFirst(); loadAppearances(false); }
        });
    }
    public void translate() {
        CharacterDetails item = current(); if (item == null) return;
        int stamp = ++translationGeneration;
        boolean deck = !MarvelRepository.plain(item.character.originalDeck).isEmpty();
        description.setValue(deck ? UiState.loading() : UiState.unavailable());
        if (deck) CatalogDescriptions.translate(translations, item.character, result -> {
            if (stamp == translationGeneration) description.setValue(result.getFailure() == null ? UiState.content(result.getText()) : UiState.error());
        });
        origin.setValue(item.character.origin.isEmpty() ? UiState.unavailable() : UiState.loading());
        if (!item.character.origin.isEmpty()) translations.translate("origin:" + item.character.originId, "name", item.character.origin, result -> {
            if (stamp == translationGeneration) origin.setValue(result.getFailure() == null ? UiState.content(result.getText()) : UiState.error());
        });
    }
    public void loadFirst() {
        CharacterDetails item = current(); if (item == null || firstPending) return;
        if (item.firstAppearance == null) { first.setValue(UiState.unavailable()); return; }
        int stamp = generation; firstPending = true; first.setValue(UiState.loading());
        repository.firstAppearance(item, result -> {
            if (stamp != generation) return;
            firstPending = false;
            first.setValue(result.failure != null ? UiState.error() : result.data.isEmpty() ? UiState.unavailable() : UiState.content(result.data));
        });
    }
    public void loadAppearances(boolean append) {
        CharacterDetails item = current();
        if (item == null || pagePending || (append && !Boolean.TRUE.equals(hasMore.getValue()))) return;
        int stamp = generation; pagePending = true; moreError.setValue(false);
        if (append) loadingMore.setValue(true); else appearances.setValue(UiState.loading());
        if (index != null) { page(stamp, append); return; }
        repository.appearanceIndex(item, result -> {
            if (stamp != generation) return;
            if (result.failure != null) { failed(append); return; }
            index = result.data; page(stamp, append);
        });
    }
    private void failed(boolean append) {
        pagePending = false; loadingMore.setValue(false);
        if (append) moreError.setValue(true); else appearances.setValue(UiState.error());
    }
    private void page(int stamp, boolean append) {
        repository.appearances(index, offset, result -> {
            if (stamp != generation) return;
            if (result.failure != null) { failed(append); return; }
            pagePending = false; loadingMore.setValue(false); offset = result.data.nextOffset; hasMore.setValue(result.data.hasMore);
            for (Issue issue : result.data.items) accumulated.putIfAbsent(issue.id, issue);
            appearances.setValue(accumulated.isEmpty() ? UiState.empty() : UiState.content(Collections.unmodifiableList(new ArrayList<>(accumulated.values()))));
        });
    }
    @Override protected void onCleared() { generation++; translationGeneration++; }
}
