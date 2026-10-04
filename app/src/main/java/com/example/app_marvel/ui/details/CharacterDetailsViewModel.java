package com.example.app_marvel.ui.details;

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

/** Cada subseção pode falhar e tentar novamente sem apagar a identidade já carregada. */
public final class CharacterDetailsViewModel extends ViewModel {
    public static final class Group {
        public final MutableLiveData<UiState<List<RelatedItem>>> state = new MutableLiveData<>(UiState.unavailable());
        public final MutableLiveData<Boolean> hasMore = new MutableLiveData<>(false);
        public final MutableLiveData<Boolean> loadingMore = new MutableLiveData<>(false);
        public final MutableLiveData<Boolean> moreError = new MutableLiveData<>(false);
        private final Map<Integer, RelatedItem> accumulated = new LinkedHashMap<>();
        private int offset;
        private boolean pending;
    }
    private final MarvelRepository repository;
    private final TranslationRepository translations;
    private final int characterId;
    private final MutableLiveData<UiState<CharacterDetails>> detail = new MutableLiveData<>(UiState.loading());
    private final MutableLiveData<UiState<String>> description = new MutableLiveData<>(UiState.unavailable());
    private final MutableLiveData<UiState<String>> origin = new MutableLiveData<>(UiState.unavailable());
    private final MutableLiveData<UiState<List<Reference>>> powers = new MutableLiveData<>(UiState.unavailable());
    private final MutableLiveData<UiState<List<Issue>>> first = new MutableLiveData<>(UiState.unavailable());
    private final Map<String, Group> groups = new LinkedHashMap<>();
    private int generation, translationGeneration;
    private boolean pending, firstPending;
    public CharacterDetailsViewModel(MarvelRepository repository, TranslationRepository translations, SavedStateHandle saved) {
        this.repository = repository; this.translations = translations;
        Integer id = saved.get("characterId"); characterId = id == null ? 0 : id;
        for (String kind : new String[]{"teams", "friends", "enemies"}) groups.put(kind, new Group());
        reload();
    }
    public LiveData<UiState<CharacterDetails>> getDetail() { return detail; }
    public LiveData<UiState<String>> getDescription() { return description; }
    public LiveData<UiState<String>> getOrigin() { return origin; }
    public LiveData<UiState<List<Reference>>> getPowers() { return powers; }
    public LiveData<UiState<List<Issue>>> getFirst() { return first; }
    public Group group(String kind) { return groups.get(kind); }
    private CharacterDetails current() {
        UiState<CharacterDetails> state = detail.getValue(); return state.getStatus() == UiState.Status.CONTENT ? state.getData() : null;
    }
    public void reload() {
        if (pending) return;
        int stamp = ++generation; translationGeneration++; pending = true; firstPending = false;
        detail.setValue(UiState.loading());
        description.setValue(UiState.unavailable()); origin.setValue(UiState.unavailable()); powers.setValue(UiState.unavailable()); first.setValue(UiState.unavailable());
        for (Group group : groups.values()) {
            group.pending = false; group.offset = 0; group.accumulated.clear(); group.state.setValue(UiState.unavailable());
            group.hasMore.setValue(false); group.loadingMore.setValue(false); group.moreError.setValue(false);
        }
        repository.details(characterId, result -> {
            if (stamp != generation) return;
            pending = false;
            detail.setValue(result.failure == null ? UiState.content(result.data) : UiState.error());
            if (result.failure != null) return;
            translate(); loadFirst();
            for (String kind : groups.keySet()) loadRelations(kind, false);
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
        powers.setValue(item.powers.isEmpty() ? UiState.unavailable() : UiState.loading());
        if (item.powers.isEmpty()) return;
        List<Reference> translated = new ArrayList<>(Collections.nCopies(item.powers.size(), null));
        int[] remaining = {item.powers.size()}; boolean[] failed = {false};
        for (int i = 0; i < item.powers.size(); i++) {
            int position = i; Reference power = item.powers.get(i);
            translations.translate("power:" + power.id, "name", power.name, result -> {
                if (stamp != translationGeneration || failed[0]) return;
                if (result.getFailure() != null) { failed[0] = true; powers.setValue(UiState.error()); return; }
                translated.set(position, new Reference(power.id, result.getText(), power.path));
                if (--remaining[0] == 0) powers.setValue(UiState.content(Collections.unmodifiableList(translated)));
            });
        }
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
    public void loadRelations(String kind, boolean append) {
        CharacterDetails item = current(); Group group = groups.get(kind);
        if (item == null || group == null || group.pending || (append && !Boolean.TRUE.equals(group.hasMore.getValue()))) return;
        if (item.relations(kind).isEmpty()) { group.state.setValue(UiState.unavailable()); return; }
        int stamp = generation; group.pending = true; group.moreError.setValue(false);
        if (append) group.loadingMore.setValue(true); else group.state.setValue(UiState.loading());
        repository.relations(item, kind, group.offset, result -> {
            if (stamp != generation) return;
            group.pending = false; group.loadingMore.setValue(false);
            if (result.failure != null) {
                if (append) group.moreError.setValue(true); else group.state.setValue(UiState.error()); return;
            }
            group.offset = result.data.nextOffset; group.hasMore.setValue(result.data.hasMore);
            for (RelatedItem related : result.data.items) group.accumulated.put(related.id, related);
            group.state.setValue(group.accumulated.isEmpty()
                    ? result.data.hasMore ? UiState.empty() : UiState.unavailable()
                    : UiState.content(Collections.unmodifiableList(new ArrayList<>(group.accumulated.values()))));
        });
    }
    @Override protected void onCleared() { generation++; translationGeneration++; }
}
