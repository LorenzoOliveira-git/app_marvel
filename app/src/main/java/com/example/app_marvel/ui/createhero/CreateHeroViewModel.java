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
import com.example.app_marvel.data.herodraft.HeroCreationRepository;
import com.example.app_marvel.data.herodraft.PrivateHeroImage;
import com.google.firebase.firestore.ListenerRegistration;
import android.graphics.Bitmap;

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
    private final HeroCreationRepository creations;
    private final PrivateHeroImage privateImages = new PrivateHeroImage();
    private Runnable removeAccountListener = () -> { };
    private ListenerRegistration creationListener;
    private final MutableLiveData<HeroCreationRepository.Job> creation = new MutableLiveData<>();
    private final MutableLiveData<HeroCreationRepository.Failure> creationFailure = new MutableLiveData<>();
    private final MutableLiveData<Boolean> creationBusy = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> confirmation = new MutableLiveData<>(false);
    private final MutableLiveData<Map<String,Object>> hero = new MutableLiveData<>();
    private final MutableLiveData<Bitmap> image = new MutableLiveData<>();
    private final MutableLiveData<Boolean> imageFailure = new MutableLiveData<>(false);
    private int creationEpoch;
    private String creationAccount = "", readingHero = "";
    private boolean restoring, retryConfirmation, accountInitialized;
    public enum DraftStatus { IDLE, SAVING, SAVED, AUTH, INVALID, CONFIGURATION, NETWORK, CATALOG, FUNCTION_UNAVAILABLE, FIRESTORE_NETWORK, FIRESTORE_PERMISSION, ERROR }
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
        this(repository, translations, saved, drafts, null);
    }
    public CreateHeroViewModel(MarvelRepository repository, TranslationRepository translations, SavedStateHandle saved, HeroDraftRepository drafts, HeroCreationRepository creations) {
        this.repository = repository; this.translations = translations; this.saved = saved; this.drafts = drafts; this.creations = creations;
        if (creations != null) removeAccountListener = creations.accountListener(this::accountChanged);
    }
    public LiveData<HeroCreationRepository.Job> creation() { return creation; }
    public LiveData<HeroCreationRepository.Failure> creationFailure() { return creationFailure; }
    public LiveData<Boolean> creationBusy() { return creationBusy; }
    public LiveData<Boolean> confirmation() { return confirmation; }
    public LiveData<Map<String,Object>> hero() { return hero; }
    public LiveData<Bitmap> image() { return image; }
    public LiveData<Boolean> imageFailure() { return imageFailure; }
    public boolean creating() { return Boolean.TRUE.equals(creationBusy.getValue()); }
    public boolean locked() { var job = creation.getValue(); return creating() || restoring || (job != null && !"prepared".equals(job.state) && !"superseded".equals(job.state)); }
    public boolean activeCreation() { var job = creation.getValue(); return job != null && java.util.Arrays.asList("generating","storing","image_stored","uploading","saving").contains(job.state); }
    public boolean newAttempt() { var job = creation.getValue(); return job != null && java.util.Arrays.asList("generation_failed","generation_unknown","image_expired").contains(job.state); }
    public boolean retryConfirmation() { return retryConfirmation; }
    public LiveData<DraftStatus> draftStatus() { return draftStatus; }
    public boolean localDraftsAvailable() { return drafts != null && drafts.available(); }
    public boolean saving() { return draftStatus.getValue() == DraftStatus.SAVING; }
    public void accountChanged() {
        if (drafts != null && draftStatus.getValue() == DraftStatus.SAVED && !savedAccount.equals(drafts.account())) draftStatus.setValue(DraftStatus.IDLE);
        if (creations == null || !creations.available()) return;
        String account = creations.account();
        if (accountInitialized && account.equals(creationAccount)) return;
        accountInitialized = true;
        boolean privateForm = !creationAccount.isEmpty() && creation.getValue() != null;
        detachCreation(); creationAccount = account; creationBusy.setValue(false); confirmation.setValue(false);
        if (draftStatus.getValue() == DraftStatus.SAVING) draftStatus.setValue(DraftStatus.IDLE);
        creation.setValue(null); creationFailure.setValue(null); hero.setValue(null); image.setValue(null); imageFailure.setValue(false); readingHero = ""; restoring = false;
        String storedOwner = saved.get("creationAccount");
        if (privateForm || (storedOwner != null && !account.equals(storedOwner))) {
            clearBinding(); clearForm();
        }
        if (account.isEmpty()) return;
        String id = saved.get("creationOperation");
        if (account.equals(saved.get("creationAccount")) && id != null) observeCreation(id);
        else if (text("heroName").isEmpty() && text("realName").isEmpty()) {
            int stamp = creationEpoch; creations.latest((job,failure) -> {
                if (stamp != creationEpoch || !account.equals(creations.account())) return;
                if (job != null && !"superseded".equals(job.state)) { accept(job); observeCreation(job.id); }
                // Falha de recuperação não sobrescreve um formulário novo nem dispara geração.
            });
        }
    }
    public void saveDraft() {
        saveDraft(false);
    }
    private void saveDraft(boolean prepare) {
        if (saving() || locked()) return;
        if (!missing().isEmpty()) { draftStatus.setValue(DraftStatus.INVALID); return; }
        if (!localDraftsAvailable()) { draftStatus.setValue(DraftStatus.CONFIGURATION); return; }
        if (drafts.account().isEmpty()) { draftStatus.setValue(DraftStatus.AUTH); return; }
        String id = saved.get("draftId");
        if (id == null) { id = UUID.randomUUID().toString(); saved.set("draftId", id); }
        Map<String, Object> data = new HashMap<>();
        data.put("draftId", id); data.put("heroName", text("heroName")); data.put("realName", text("realName")); data.put("description", text("description"));
        data.put("birthday", text("birthday")); data.put("originId", origin().id);
        List<Integer> ids = new ArrayList<>(); for (Choice choice : selectedPowers()) ids.add(choice.id); data.put("powerIds", ids);
        String account = drafts.account(); int stamp = creationEpoch; draftStatus.setValue(DraftStatus.SAVING);
        if (prepare) creationBusy.setValue(true);
        drafts.save(data, failure -> {
            if (stamp != creationEpoch) return;
            savedAccount = account;
            draftStatus.setValue(failure == null ? DraftStatus.SAVED : DraftStatus.valueOf(failure == HeroDraftRepository.Failure.UNKNOWN ? "ERROR" : failure.name()));
            if (prepare) {
                if (failure != null) { creationBusy.setValue(false); return; }
                String candidate = saved.get("creationCandidate");
                if (candidate == null) { candidate = UUID.randomUUID().toString(); saved.set("creationCandidate", candidate); }
                creations.prepare((String) data.get("draftId"), candidate, (job,error) -> {
                    if (stamp != creationEpoch) return;
                    creationBusy.setValue(false); creationFailure.setValue(error);
                    if (job != null) { accept(job); observeCreation(job.id); if ("prepared".equals(job.state)) { retryConfirmation = false; confirmation.setValue(true); } }
                });
            }
        });
    }
    public void create() {
        if (creations == null || !creations.available()) return;
        if (!missing().isEmpty()) { creationFailure.setValue(HeroCreationRepository.Failure.INVALID); return; }
        var job = creation.getValue();
        if (job != null && "prepared".equals(job.state) && !creating()) { retryConfirmation = false; confirmation.setValue(true); }
        else saveDraft(true);
    }
    public void requestNewAttempt() { if (newAttempt() && !creating()) { retryConfirmation = true; confirmation.setValue(true); } }
    public void cancelConfirmation() { confirmation.setValue(false); }
    public void confirmPaidAttempt() {
        if (!Boolean.TRUE.equals(confirmation.getValue()) || creating() || creations == null || !creationAccount.equals(creations.account())) return;
        confirmation.setValue(false); var job = creation.getValue(); if (job == null) return;
        int stamp = creationEpoch; creationBusy.setValue(true); creationFailure.setValue(null);
        if (retryConfirmation) {
            String candidate = saved.get("retryCandidate");
            if (candidate == null) { candidate = UUID.randomUUID().toString(); saved.set("retryCandidate", candidate); }
            creations.retry(job.id, candidate, (next,failure) -> {
                if (stamp != creationEpoch) return;
                if (failure != null) { creationBusy.setValue(false); creationFailure.setValue(failure); return; }
                accept(next); observeCreation(next.id); execute(next.id,stamp);
            });
        } else execute(job.id,stamp);
    }
    private void execute(String id, int stamp) {
        creations.execute(id, (job,failure) -> {
            if (stamp != creationEpoch) return;
            creationBusy.setValue(false); creationFailure.setValue(failure);
            if (job != null) accept(job);
        });
    }
    public void resumeCreation() {
        var job = creation.getValue(); if (job == null || creating()) return;
        if (origin() == null || selectedPowers().isEmpty()) { restoreForm(job); return; }
        int stamp = creationEpoch; creationBusy.setValue(true); creationFailure.setValue(null);
        HeroCreationRepository.Callback<HeroCreationRepository.Job> callback = (value,failure) -> {
            if (stamp != creationEpoch) return;
            creationBusy.setValue(false); creationFailure.setValue(failure); if (value != null) accept(value);
        };
        if ("prepared".equals(job.state)) creations.refresh(job.id,callback); else creations.resume(job.id,callback);
    }
    private void observeCreation(String id) {
        if (creationListener != null) creationListener.remove();
        int stamp = creationEpoch;
        creationListener = creations.observe(id, (job,failure) -> {
            if (stamp != creationEpoch) return;
            if (job != null) { if (creationFailure.getValue() == HeroCreationRepository.Failure.NETWORK) creationFailure.setValue(null); accept(job); }
            else if (failure != null && !creating()) creationFailure.setValue(failure);
        });
    }
    private void accept(HeroCreationRepository.Job job) {
        saved.set("creationAccount", creationAccount); saved.set("creationOperation", job.id); saved.set("draftId", job.draftId);
        if (text("heroName").isEmpty() && job.snapshot.get("heroName") instanceof String) restoreForm(job);
        creation.setValue(job);
        if ("completed".equals(job.state) && job.generatedImage && !job.id.equals(readingHero)) {
            readingHero = job.id; int stamp = creationEpoch;
            creations.hero(job.id, (value,failure) -> {
                if (stamp != creationEpoch) return;
                if (failure != null) { readingHero = ""; creationFailure.setValue(failure); return; }
                hero.setValue(value); loadImage();
            });
        }
    }
    private void restoreForm(HeroCreationRepository.Job job) {
        int stamp = creationEpoch; restoring = true; creationBusy.setValue(true);
        for (String field : new String[]{"heroName","realName","description","birthday"}) saved.set(field, job.snapshot.get(field) instanceof String ? job.snapshot.get(field) : "");
        List<Map<?,?>> choices = new ArrayList<>();
        if (job.snapshot.get("origin") instanceof Map) choices.add((Map<?,?>) job.snapshot.get("origin"));
        if (job.snapshot.get("powers") instanceof List) for (Object value : (List<?>) job.snapshot.get("powers")) if (value instanceof Map) choices.add((Map<?,?>) value);
        if (choices.size() < 2) { restoring = false; creationBusy.setValue(false); creationFailure.setValue(HeroCreationRepository.Failure.STATE); step(2); return; }
        ArrayList<Bundle> selected = new ArrayList<>(); int[] remaining = {choices.size()}; boolean[] failed = {false};
        for (int index = 0; index < choices.size(); index++) {
            var choice = choices.get(index); boolean origin = index == 0;
            if (!(choice.get("id") instanceof Number) || !(choice.get("name") instanceof String)) { restoring = false; creationBusy.setValue(false); creationFailure.setValue(HeroCreationRepository.Failure.STATE); step(2); return; }
            int id = ((Number) choice.get("id")).intValue(); String name = (String) choice.get("name");
            translations.translate((origin ? "origin:" : "power:") + id, "name", name, translated -> {
                if (stamp != creationEpoch) return;
                if (translated.getFailure() != null) failed[0] = true;
                else if (origin) saved.set("origin", new Choice(id,name,translated.getText()).bundle());
                else selected.add(new Choice(id,name,translated.getText()).bundle());
                if (--remaining[0] == 0) {
                    restoring = false; creationBusy.setValue(false);
                    if (failed[0]) { creationFailure.setValue(HeroCreationRepository.Failure.NETWORK); step(2); return; }
                    saved.set("selectedPowers", selected); changed(); step(2);
                }
            });
        }
    }
    public void loadImage() {
        var job = creation.getValue(); if (job == null || hero.getValue() == null) return;
        int stamp = creationEpoch; String account = creationAccount; imageFailure.setValue(false);
        creations.imageUrl(job.id, (url,failure) -> {
            if (stamp != creationEpoch || !account.equals(creations.account())) return;
            if (failure != null) { imageFailure.setValue(true); return; }
            privateImages.load(url,bitmap -> {
                if (stamp != creationEpoch || !account.equals(creations.account())) return;
                image.setValue(bitmap); imageFailure.setValue(bitmap == null);
            });
        });
    }
    public void newHero() { if (creating()) return; detachCreation(); clearBinding(); clearForm(); creation.setValue(null); creationFailure.setValue(null); hero.setValue(null); image.setValue(null); imageFailure.setValue(false); readingHero = ""; }
    private void detachCreation() { creationEpoch++; if (creationListener != null) { creationListener.remove(); creationListener = null; } }
    private void clearBinding() { for (String key : new String[]{"creationAccount","creationOperation","creationCandidate","retryCandidate"}) saved.remove(key); }
    private void clearForm() {
        for (String key : new String[]{"heroName","realName","description","birthday","origin","selectedPowers","draftId"}) saved.remove(key);
        draftStatus.setValue(DraftStatus.IDLE); step(0); changed();
    }
    private void edited() {
        if (!saving() && draftStatus.getValue() != DraftStatus.IDLE) draftStatus.setValue(DraftStatus.IDLE);
        if (creation.getValue() != null && "prepared".equals(creation.getValue().state)) { detachCreation(); clearBinding(); creation.setValue(null); creationFailure.setValue(null); }
    }
    public LiveData<Integer> stepState() { return saved.getLiveData("step", 0); }
    public LiveData<Integer> selectionState() { return selectionVersion; }
    public LiveData<UiState<List<Choice>>> origins() { return origins; }
    public LiveData<UiState<List<Choice>>> powers() { return powers; }
    public int step() { Integer value = saved.get("step"); return value == null ? 0 : value; }
    public void step(int value) { saved.set("step", Math.max(0, Math.min(2, value))); }
    public String text(String key) { String value = saved.get(key); return value == null ? "" : value; }
    public void text(String key, String value) { if (saving() || locked()) return; if (!value.equals(text(key))) edited(); saved.set(key, value); }
    public Choice origin() { return Choice.from(saved.get("origin")); }
    public void origin(Choice value) { if (saving() || locked()) return; edited(); saved.set("origin", value.bundle()); changed(); }
    public List<Choice> selectedPowers() {
        ArrayList<Bundle> values = saved.get("selectedPowers"); List<Choice> result = new ArrayList<>();
        if (values != null) for (Bundle value : values) result.add(Choice.from(value));
        return Collections.unmodifiableList(result);
    }
    public boolean selected(int id) { for (Choice value : selectedPowers()) if (value.id == id) return true; return false; }
    public void power(Choice value, boolean selected) {
        if (saving() || locked()) return; edited();
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
    @Override protected void onCleared() { originGeneration++; powerGeneration++; detachCreation(); removeAccountListener.run(); privateImages.close(); image.setValue(null); }
}
