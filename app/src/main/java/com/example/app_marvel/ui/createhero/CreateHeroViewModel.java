package com.example.app_marvel.ui.createhero;

import android.os.Bundle;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.example.app_marvel.data.catalog.CatalogDescriptions;
import com.example.app_marvel.data.catalog.CatalogModels.Reference;
import com.example.app_marvel.data.catalog.MarvelRepository;
import com.example.app_marvel.data.translation.TranslationRepository;
import com.example.app_marvel.ui.common.UiState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import com.example.app_marvel.data.herodraft.HeroDraftRepository;

/** Rascunho de sessão. IDs e nomes da fonte são separados dos rótulos traduzidos. */
public final class CreateHeroViewModel extends ViewModel {
    public static final class Choice {
        public final int id;
        public final String sourceName, label;
        public Choice(int id, String sourceName, String label) {
            this.id = id; this.sourceName = sourceName; this.label = label;
        }
        Bundle bundle() {
            Bundle value = new Bundle(); value.putInt("id", id);
            value.putString("source", sourceName); value.putString("label", label); return value;
        }
        static Choice from(Bundle value) {
            return value == null ? null : new Choice(value.getInt("id"), value.getString("source", ""), value.getString("label", ""));
        }
    }
    private final MarvelRepository repository;
    private final TranslationRepository translations;
    private final SavedStateHandle saved;
    private final HeroDraftRepository drafts;
    public enum DraftStatus { IDLE, SAVING, SAVED, AUTH, INVALID, CONFIGURATION, NETWORK, ERROR }
    private final MutableLiveData<DraftStatus> draftStatus = new MutableLiveData<>(DraftStatus.IDLE);
    private String savedAccount = "";
    private final MutableLiveData<UiState<List<Choice>>> origins = new MutableLiveData<>(UiState.empty());
    private final MutableLiveData<UiState<List<Choice>>> powers = new MutableLiveData<>(UiState.empty());
    private final MutableLiveData<Integer> selectionVersion = new MutableLiveData<>(0);
    private final List<Choice> loadedPowers = new ArrayList<>();
    private int nextOffset, total, originGeneration, powerGeneration;
    private boolean hasMore;

    public CreateHeroViewModel(MarvelRepository repository, TranslationRepository translations, SavedStateHandle saved) {
        this(repository, translations, saved, null);
    }
    public CreateHeroViewModel(MarvelRepository repository, TranslationRepository translations, SavedStateHandle saved, HeroDraftRepository drafts) {
        this.repository = repository; this.translations = translations; this.saved = saved; this.drafts = drafts;
    }
    public LiveData<DraftStatus> draftStatus() { return draftStatus; }
    public boolean localDraftsAvailable() { return drafts != null && drafts.available(); }
    public boolean saving() { return draftStatus.getValue() == DraftStatus.SAVING; }
    public void accountChanged() {
        if (draftStatus.getValue() == DraftStatus.SAVED && !savedAccount.equals(drafts.account())) draftStatus.setValue(DraftStatus.IDLE);
    }
    public void saveDraft() {
        if (saving()) return;
        if (!missing().isEmpty()) { draftStatus.setValue(DraftStatus.INVALID); return; }
        if (!localDraftsAvailable()) { draftStatus.setValue(DraftStatus.CONFIGURATION); return; }
        if (drafts.account().isEmpty()) { draftStatus.setValue(DraftStatus.AUTH); return; }
        String id = saved.get("draftId");
        if (id == null) { id = UUID.randomUUID().toString(); saved.set("draftId", id); }
        Map<String, Object> data = new HashMap<>();
        data.put("draftId", id); data.put("heroName", text("heroName")); data.put("realName", text("realName")); data.put("description", text("description"));
        data.put("birthday", text("birthday")); data.put("originId", origin().id);
        List<Integer> ids = new ArrayList<>(); for (Choice choice : selectedPowers()) ids.add(choice.id); data.put("powerIds", ids);
        String account = drafts.account(); draftStatus.setValue(DraftStatus.SAVING);
        drafts.save(data, failure -> {
            savedAccount = account;
            draftStatus.setValue(failure == null ? DraftStatus.SAVED : DraftStatus.valueOf(failure == HeroDraftRepository.Failure.UNKNOWN ? "ERROR" : failure.name()));
        });
    }
    private void edited() { if (!saving() && draftStatus.getValue() != DraftStatus.IDLE) draftStatus.setValue(DraftStatus.IDLE); }
    public LiveData<Integer> stepState() { return saved.getLiveData("step", 0); }
    public LiveData<Integer> selectionState() { return selectionVersion; }
    public LiveData<UiState<List<Choice>>> origins() { return origins; }
    public LiveData<UiState<List<Choice>>> powers() { return powers; }
    public int step() { Integer value = saved.get("step"); return value == null ? 0 : value; }
    public void step(int value) { saved.set("step", Math.max(0, Math.min(2, value))); }
    public String text(String key) { String value = saved.get(key); return value == null ? "" : value; }
    public void text(String key, String value) { if (saving()) return; if (!value.equals(text(key))) edited(); saved.set(key, value); }
    public Choice origin() { return Choice.from(saved.get("origin")); }
    public void origin(Choice value) { if (saving()) return; edited(); saved.set("origin", value.bundle()); changed(); }
    public List<Choice> selectedPowers() {
        ArrayList<Bundle> values = saved.get("selectedPowers"); List<Choice> result = new ArrayList<>();
        if (values != null) for (Bundle value : values) result.add(Choice.from(value));
        return Collections.unmodifiableList(result);
    }
    public boolean selected(int id) { for (Choice value : selectedPowers()) if (value.id == id) return true; return false; }
    public void power(Choice value, boolean selected) {
        if (saving()) return; edited();
        ArrayList<Bundle> values = new ArrayList<>();
        for (Choice old : selectedPowers()) if (old.id != value.id) values.add(old.bundle());
        if (selected) values.add(value.bundle()); saved.set("selectedPowers", values); changed();
    }
    private void changed() { selectionVersion.setValue(selectionVersion.getValue() + 1); }
    public List<String> missing() {
        List<String> fields = new ArrayList<>();
        for (String field : new String[]{"heroName", "realName", "description"}) if (text(field).trim().isEmpty()) fields.add(field);
        if (origin() == null) fields.add("origin"); if (selectedPowers().isEmpty()) fields.add("powers"); return fields;
    }
    public boolean identityValid() { return !text("heroName").trim().isEmpty() && !text("realName").trim().isEmpty(); }
    public boolean hasMore() { return hasMore; }
    public int loadedCount() { return loadedPowers.size(); }
    public int total() { return total; }
    public void loadChoices() { loadOrigins(); if (powers.getValue().getStatus() == UiState.Status.EMPTY && loadedPowers.isEmpty()) loadPowers(); }
    public void loadOrigins() {
        if (origins.getValue().getStatus() == UiState.Status.CONTENT || origins.getValue().getStatus() == UiState.Status.LOADING) return;
        int stamp = ++originGeneration; origins.setValue(UiState.loading());
        repository.origins(result -> {
            if (stamp != originGeneration) return;
            if (result.failure != null) { origins.setValue(UiState.error()); return; }
            if (result.data.isEmpty()) { origins.setValue(UiState.empty()); return; }
            List<Choice> choices = new ArrayList<>(); int[] remaining = {result.data.size()}; boolean[] failed = {false};
            for (Reference ref : result.data) translations.translate("origin:" + ref.id, "name", ref.name, translated -> {
                if (stamp != originGeneration) return;
                if (translated.getFailure() == null) choices.add(new Choice(ref.id, ref.name, translated.getText())); else failed[0] = true;
                if (--remaining[0] == 0) {
                    choices.sort(Comparator.comparing(choice -> choice.label));
                    origins.setValue(failed[0] ? UiState.error() : UiState.content(Collections.unmodifiableList(choices)));
                }
            });
        });
    }
    /** Mais só incorpora um lote completo: falha de tradução permite repetir o mesmo offset. */
    public void loadPowers() {
        if (powers.getValue().getStatus() == UiState.Status.LOADING || (!loadedPowers.isEmpty() && !hasMore)) return;
        int stamp = ++powerGeneration; powers.setValue(UiState.loading());
        repository.powers(nextOffset, result -> {
            if (stamp != powerGeneration) return;
            if (result.failure != null) { powers.setValue(UiState.error()); return; }
            if (result.data.items.isEmpty()) { hasMore = false; powers.setValue(loadedPowers.isEmpty() ? UiState.empty() : UiState.content(snapshot())); return; }
            List<Choice> batch = new ArrayList<>(); int[] remaining = {result.data.items.size()}; boolean[] failed = {false};
            for (Reference ref : result.data.items) CatalogDescriptions.translatePower(translations, ref, translated -> {
                if (stamp != powerGeneration) return;
                if (translated.getFailure() == null) batch.add(new Choice(ref.id, ref.name, translated.getText())); else failed[0] = true;
                if (--remaining[0] == 0) {
                    if (failed[0]) { powers.setValue(UiState.error()); return; }
                    // Ordenação da fonte conserva a paginação; rótulos são apenas apresentação.
                    for (Reference source : result.data.items) for (Choice choice : batch) if (choice.id == source.id) {
                        boolean exists = false; for (Choice old : loadedPowers) if (old.id == choice.id) exists = true;
                        if (!exists) loadedPowers.add(choice);
                    }
                    nextOffset = result.data.nextOffset; total = result.data.total; hasMore = result.data.hasMore;
                    powers.setValue(UiState.content(snapshot()));
                }
            });
        });
    }
    public List<Choice> loadedPowers() { return snapshot(); }
    private List<Choice> snapshot() { return Collections.unmodifiableList(new ArrayList<>(loadedPowers)); }
    @Override protected void onCleared() { originGeneration++; powerGeneration++; }
}
