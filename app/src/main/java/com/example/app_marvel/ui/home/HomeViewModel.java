package com.example.app_marvel.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.app_marvel.data.catalog.CatalogModels.Character;
import com.example.app_marvel.data.catalog.CatalogModels.Issue;
import com.example.app_marvel.data.catalog.MarvelRepository;
import com.example.app_marvel.data.translation.TranslationRepository;
import com.example.app_marvel.ui.common.UiState;
import java.util.List;

public final class HomeViewModel extends ViewModel {
    private final MarvelRepository repository;
    private final TranslationRepository translations;
    private final MutableLiveData<UiState<Character>> featured = new MutableLiveData<>(UiState.loading());
    private final MutableLiveData<UiState<List<Issue>>> recent = new MutableLiveData<>(UiState.loading());
    private final MutableLiveData<UiState<String>> fact = new MutableLiveData<>(UiState.loading());
    private final MutableLiveData<UiState<String>> origin = new MutableLiveData<>(UiState.loading());
    private boolean characterPending, issuesPending;
    private int generation;

    public HomeViewModel(MarvelRepository repository, TranslationRepository translations) {
        this.repository = repository; this.translations = translations;
        loadFeatured(); loadRecent();
    }
    public LiveData<UiState<Character>> getFeatured() { return featured; }
    public LiveData<UiState<List<Issue>>> getRecent() { return recent; }
    public LiveData<UiState<String>> getFact() { return fact; }
    public LiveData<UiState<String>> getOrigin() { return origin; }
    public void loadFeatured() {
        if (characterPending) return;
        characterPending = true; featured.setValue(UiState.loading());
        repository.featured(result -> {
            characterPending = false;
            featured.setValue(result.failure == null ? UiState.content(result.data) : UiState.error());
            if (result.failure == null) translate();
        });
    }
    public void loadRecent() {
        if (issuesPending) return;
        issuesPending = true; recent.setValue(UiState.loading());
        repository.recent(result -> {
            issuesPending = false;
            recent.setValue(result.failure != null ? UiState.error() : result.data.isEmpty() ? UiState.empty() : UiState.content(result.data));
        });
    }
    public void translate() {
        UiState<Character> current = featured.getValue();
        if (current == null || current.getStatus() != UiState.Status.CONTENT) return;
        Character item = current.getData(); int stamp = ++generation;
        String original = MarvelRepository.plain(item.originalDeck);
        fact.setValue(original.isEmpty() ? UiState.empty() : UiState.loading());
        if (!original.isEmpty()) translations.translate("character:" + item.id, "deck", original, result -> {
            if (stamp == generation) fact.setValue(result.getFailure() == null ? UiState.content(result.getText()) : UiState.error());
        });
        origin.setValue(item.origin.isEmpty() ? UiState.empty() : UiState.loading());
        if (!item.origin.isEmpty()) translations.translate("origin:" + item.originId, "name", item.origin, result -> {
            if (stamp == generation) origin.setValue(result.getFailure() == null ? UiState.content(result.getText()) : UiState.error());
        });
    }
    @Override protected void onCleared() { generation++; }
}
