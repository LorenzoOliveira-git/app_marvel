package com.example.app_marvel.ui.arcs;

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

public final class ArcDetailsViewModel extends ViewModel {
    private final MarvelRepository repository;
    private final TranslationRepository translations;
    private final SavedStateHandle saved;
    private final int arcId;
    private final MutableLiveData<UiState<ArcDetails>> detail=new MutableLiveData<>(UiState.loading());
    private final MutableLiveData<UiState<String>> deck=new MutableLiveData<>(UiState.unavailable()), description=new MutableLiveData<>(UiState.unavailable());
    private final MutableLiveData<UiState<List<Issue>>> issues=new MutableLiveData<>(UiState.unavailable());
    private final MutableLiveData<Boolean> hasMore=new MutableLiveData<>(false),loading=new MutableLiveData<>(false),error=new MutableLiveData<>(false);
    private final Map<Integer,Issue> accumulated=new LinkedHashMap<>();
    private int generation,offset,deckVersion,descriptionVersion;
    private boolean pending,issuesPending;
    public ArcDetailsViewModel(MarvelRepository repository,TranslationRepository translations,SavedStateHandle saved) {
        this.repository=repository;this.translations=translations;this.saved=saved;
        Integer id=saved.get("arcId");arcId=id==null ? 0:id;reload();
    }
    public LiveData<UiState<ArcDetails>> detail() { return detail; }
    public LiveData<UiState<String>> deck() { return deck; }
    public LiveData<UiState<String>> description() { return description; }
    public LiveData<UiState<List<Issue>>> issues() { return issues; }
    public LiveData<Boolean> hasMore() { return hasMore; }
    public LiveData<Boolean> loading() { return loading; }
    public LiveData<Boolean> error() { return error; }
    public int scroll() { Integer value=saved.get("scroll");return value==null ? 0:value; }
    public void scroll(int value) { saved.set("scroll",value); }
    private ArcDetails current() { return detail.getValue().getStatus()==UiState.Status.CONTENT ? detail.getValue().getData():null; }
    public void reload() {
        if (pending) return;
        int stamp=++generation;pending=true;issuesPending=false;offset=0;accumulated.clear();scroll(0);
        detail.setValue(UiState.loading());deck.setValue(UiState.unavailable());description.setValue(UiState.unavailable());
        issues.setValue(UiState.unavailable());hasMore.setValue(false);loading.setValue(false);error.setValue(false);
        repository.arcDetails(arcId,result -> {
            if (stamp!=generation) return;
            pending=false;detail.setValue(result.failure==null ? UiState.content(result.data):UiState.error());
            if (result.failure!=null) return;
            description.setValue(MarvelRepository.plain(result.data.originalDescription).isEmpty() ? UiState.unavailable():UiState.empty());
            translate("deck");load(false);
        });
    }
    public void translate(String field) {
        ArcDetails item=current();if (item==null) return;
        boolean summary=field.equals("deck");var target=summary ? deck:description;
        if (target.getValue().getStatus()==UiState.Status.LOADING || target.getValue().getStatus()==UiState.Status.CONTENT) return;
        if (MarvelRepository.plain(summary ? item.originalDeck:item.originalDescription).isEmpty()) { target.setValue(UiState.unavailable());return; }
        int stamp=generation,version=summary ? ++deckVersion:++descriptionVersion;target.setValue(UiState.loading());
        CatalogDescriptions.translateArc(translations,item,field,result -> {
            if (stamp!=generation || version!=(summary ? deckVersion:descriptionVersion)) return;
            target.setValue(result.getFailure()==null ? UiState.content(result.getText()):UiState.error());
        });
    }
    public void load(boolean append) {
        ArcDetails item=current();
        if (item==null || issuesPending || (append && !Boolean.TRUE.equals(hasMore.getValue()))) return;
        if (item.issues.isEmpty()) { issues.setValue(UiState.unavailable());return; }
        int stamp=generation;issuesPending=true;error.setValue(false);
        if (append) loading.setValue(true);else issues.setValue(UiState.loading());
        repository.arcIssues(item,offset,result -> {
            if (stamp!=generation) return;
            issuesPending=false;loading.setValue(false);
            if (result.failure!=null) { if (append) error.setValue(true);else issues.setValue(UiState.error());return; }
            offset=result.data.nextOffset;hasMore.setValue(result.data.hasMore);
            for (Issue issue:result.data.items) accumulated.putIfAbsent(issue.id,issue);
            issues.setValue(accumulated.isEmpty() ? result.data.hasMore ? UiState.empty():UiState.unavailable()
                    :UiState.content(Collections.unmodifiableList(new ArrayList<>(accumulated.values()))));
        });
    }
    @Override protected void onCleared() { generation++; }
}
