package com.example.app_marvel.diagnostics;

import android.app.Activity;
import android.os.Bundle;
import androidx.lifecycle.SavedStateHandle;
import com.example.app_marvel.MarvelApplication;
import com.example.app_marvel.ui.common.UiState;
import com.example.app_marvel.ui.createhero.CreateHeroViewModel;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

/** Verificação de integração do ViewModel com API/tradutor reais, somente debug. */
public final class HeroFormCheckActivity extends Activity {
    private CreateHeroViewModel model;
    private boolean finished, second;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        var container = ((MarvelApplication) getApplication()).getContainer();
        model = new CreateHeroViewModel(container.getCatalog(), container.getTranslations(), new SavedStateHandle());
        model.origins().observeForever(value -> inspect());
        model.powers().observeForever(value -> inspect());
        model.loadChoices();
    }
    private void inspect() {
        if (finished) return;
        var origins = model.origins().getValue(); var powers = model.powers().getValue();
        if (origins.getStatus() == UiState.Status.ERROR || powers.getStatus() == UiState.Status.ERROR) { write(false); return; }
        if (origins.getStatus() != UiState.Status.CONTENT || powers.getStatus() != UiState.Status.CONTENT) return;
        if ("hero-form-draft".equals(getIntent().getStringExtra("check"))) {
            write(!origins.getData().isEmpty() && model.loadedCount() == 20); return;
        }
        if (!second) { second = true; model.loadPowers(); return; }
        write(origins.getData().size() > 0 && model.loadedCount() == 40 && model.total() >= model.loadedCount() && model.hasMore());
    }
    private void write(boolean success) {
        finished = true;
        try {
            JSONObject report = new JSONObject(); report.put("success", success);
            if (success) {
                report.put("origins", choices(model.origins().getValue().getData()));
                report.put("powers", choices(model.loadedPowers())); report.put("total_powers", model.total());
                report.put("source", "ComicVine auxiliary catalogs, not publisher membership");
            }
            File folder = new File(getFilesDir(), "diagnostics"); if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException();
            String name = getIntent().getStringExtra("check");
            if (!"hero-form-cache".equals(name) && !"hero-form-draft".equals(name)) name = "hero-form";
            try (var output = new java.io.FileOutputStream(new File(folder, name + ".json"))) { output.write(report.toString(2).getBytes(StandardCharsets.UTF_8)); }
        } catch (Exception failure) { android.util.Log.e("HeroFormCheck", "Falha ao registrar integração."); }
        finish();
    }
    private JSONArray choices(java.util.List<CreateHeroViewModel.Choice> values) throws Exception {
        JSONArray rows = new JSONArray(); Set<Integer> ids = new HashSet<>();
        for (var choice : values) {
            if (choice.id <= 0 || choice.sourceName.isEmpty() || choice.label.isEmpty() || !ids.add(choice.id)) throw new IllegalStateException();
            rows.put(new JSONObject().put("id", choice.id).put("original", choice.sourceName).put("label", choice.label));
        }
        return rows;
    }
}
