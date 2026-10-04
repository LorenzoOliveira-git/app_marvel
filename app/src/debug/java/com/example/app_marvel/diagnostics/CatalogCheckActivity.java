package com.example.app_marvel.diagnostics;

import android.app.Activity;
import android.os.Bundle;
import com.example.app_marvel.MarvelApplication;
import com.example.app_marvel.data.catalog.CatalogModels;
import com.example.app_marvel.data.catalog.MarvelRepository;
import com.example.app_marvel.data.catalog.CatalogDescriptions;
import com.example.app_marvel.data.translation.TranslationRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Exercício ADB do repositório real. Não substitui respostas e não existe no release. */
public final class CatalogCheckActivity extends Activity {
    private MarvelRepository repository;
    private TranslationRepository translations;
    private final JSONObject report = new JSONObject();
    private int owner, featuredId;
    private CatalogModels.CharacterDetails details;
    private String check;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        check = getIntent().getStringExtra("check");
        if (!"catalog".equals(check) && !"catalog-cache".equals(check)) { finish(); return; }
        var container = ((MarvelApplication) getApplication()).getContainer();
        repository = container.getCatalog(); translations = container.getTranslations();
        if ("catalog".equals(check)) awaitInternet(0); else featured();
    }
    private void awaitInternet(int attempt) {
        if (isFinishing() || isDestroyed()) return;
        android.net.ConnectivityManager connectivity = getSystemService(android.net.ConnectivityManager.class);
        android.net.NetworkCapabilities capabilities = connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        if (capabilities != null && capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            put("internet_validated", true); featured();
        } else if (attempt >= 60) fail("internet_unavailable");
        else new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> awaitInternet(attempt + 1), 500);
    }
    private void featured() {
        repository.featured(result -> {
            if (result.failure != null) { put("catalog_failure", result.failure.name()); fail("featured"); return; }
            owner = result.data.publisherId; featuredId = result.data.id; put("publisher_id", owner); put("featured", character(result.data));
            CatalogDescriptions.translate(translations, result.data, translated -> {
                if (translated.getFailure() != null) { fail("translation"); return; }
                put("translated_deck", translated.getText()); put("translation_from_cache", translated.isFromCache()); recent();
            });
        });
    }
    private void recent() {
        repository.recent(result -> {
            if (result.failure != null || result.data.isEmpty()) {
                put("recent_failure", result.failure == null ? "EMPTY" : result.failure.name());
                put("device_date", new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(new java.util.Date()));
                fail("recent"); return;
            }
            JSONArray rows = new JSONArray();
            for (var issue : result.data) {
                JSONObject row = new JSONObject();
                try { row.put("id", issue.id).put("volume_id", issue.volumeId).put("store_date", issue.publicationDate).put("title", issue.title); }
                catch (Exception ignored) { fail("serialize"); return; }
                rows.put(row);
            }
            put("issues", rows); initial();
        });
    }
    private void initial() {
        repository.characters("", 0, 0, 0, 0, result -> {
            if (!page("first", result, 0, 0)) return;
            put("next_offset", result.data.nextOffset); put("has_more", result.data.hasMore);
            repository.characters("", 0, 0, 0, result.data.nextOffset, next -> {
                if (!page("next", next, 0, 0)) return;
                repository.characters("Spider-Man", 0, 0, 0, 0, search -> {
                    if (!page("search", search, 0, 0)) return;
                    origins();
                });
            });
        });
    }
    private void origins() {
        repository.origins(result -> {
            if (result.failure != null) { fail("origins"); return; }
            int human = 0; for (var item : result.data) if ("Human".equals(item.name)) human = item.id;
            if (human == 0) { fail("human_not_resolved"); return; }
            int origin = human;
            repository.characters("", origin, 2, 0, 0, filtered -> {
                if (!page("origin_gender", filtered, origin, 2)) return;
                teams();
            });
        });
    }
    private void teams() {
        repository.teams(result -> {
            if (result.failure != null) { fail("teams"); return; }
            int selected = 0; for (var team : result.data) if ("Avengers".equals(team.name)) selected = team.id;
            if (selected == 0) { fail("avengers_not_resolved"); return; }
            put("team_id", selected);
            repository.characters("", 0, 0, selected, 0, team -> {
                if (!page("team", team, 0, 0)) return;
                loadDetails();
            });
        });
    }
    private void loadDetails() {
        repository.details(featuredId, result -> {
            if (result.failure != null || result.data.character.id != featuredId || result.data.character.publisherId != owner) { fail("details"); return; }
            details = result.data;
            put("details", character(details.character)); put("appearance_count", details.appearanceCount);
            if (details.powers.isEmpty()) { fail("powers_missing"); return; }
            JSONArray rows = new JSONArray(); int[] remaining = {details.powers.size()}; boolean[] failed = {false}, cached = {true};
            for (var power : details.powers) CatalogDescriptions.translatePower(translations, power, translated -> {
                if (failed[0]) return;
                if (translated.getFailure() != null) { failed[0] = true; fail("power_translation"); return; }
                cached[0] &= translated.isFromCache();
                try { rows.put(new JSONObject().put("id", power.id).put("original", power.name).put("translated", translated.getText())); }
                catch (Exception ignored) { failed[0] = true; fail("serialize_powers"); return; }
                if (--remaining[0] == 0) {
                    java.util.List<JSONObject> sorted = new java.util.ArrayList<>();
                    for (int i = 0; i < rows.length(); i++) sorted.add(rows.optJSONObject(i));
                    sorted.sort(java.util.Comparator.comparingInt(row -> row.optInt("id")));
                    put("powers", new JSONArray(sorted)); put("powers_from_cache", cached[0]); put("alias_count", details.character.aliases.isEmpty() ? 0 : details.character.aliases.split("[\\r\\n]+").length); relations("teams");
                }
            });
        });
    }
    private void relations(String kind) {
        repository.relations(details, kind, 0, result -> {
            if (!relationPage("details_" + kind, result)) return;
            if (kind.equals("teams")) relations("friends");
            else if (kind.equals("friends")) {
                put("friends_has_more", result.data.hasMore);
                if (!result.data.hasMore) { fail("friends_pagination_missing"); return; }
                repository.relations(details, kind, result.data.nextOffset, next -> {
                    if (relationPage("details_friends_next", next)) relations("enemies");
                });
            } else repository.firstAppearance(details, first -> {
                if (first.failure != null || first.data.size() != 1 || details.firstAppearance == null || first.data.get(0).id != details.firstAppearance.id) { fail("first_appearance"); return; }
                var issue = first.data.get(0);
                try { put("first_appearance", new JSONObject().put("id", issue.id).put("volume_id", issue.volumeId).put("title", issue.title).put("cover_date", issue.publicationDate)); }
                catch (Exception ignored) { fail("serialize_first"); return; }
                repository.details(0, invalid -> {
                    if (invalid.failure == null) { fail("invalid_character_accepted"); return; }
                    put("invalid_character_rejected", true);
                    // ID de Lightning Lad/DC observado na sondagem real anterior, apenas diagnóstico debug.
                    repository.details(1253, foreign -> {
                        if (foreign.failure == null) { fail("foreign_publisher_accepted"); return; }
                        put("foreign_publisher_rejected", true); put("success", true); save();
                    });
                });
            });
        });
    }
    private boolean relationPage(String name, CatalogModels.Result<CatalogModels.RelationPage> result) {
        if (result.failure != null || result.data.items.isEmpty()) { fail(name); return false; }
        JSONArray rows = new JSONArray();
        for (var item : result.data.items) {
            if (item.publisherId != owner) { fail(name + "_publisher"); return false; }
            try { rows.put(new JSONObject().put("id", item.id).put("name", item.name).put("publisher_id", item.publisherId)); }
            catch (Exception ignored) { fail("serialize_related"); return false; }
        }
        put(name, rows); return true;
    }
    private boolean page(String label, CatalogModels.Result<CatalogModels.Page> result, int origin, int gender) {
        if (result.failure != null || result.data.characters.isEmpty()) { fail(label); return false; }
        JSONArray rows = new JSONArray();
        for (var item : result.data.characters) {
            if (item.publisherId != owner || (origin > 0 && item.originId != origin) || (gender > 0 && item.gender != gender)) { fail(label + "_relation"); return false; }
            rows.put(character(item));
        }
        put(label, rows); return true;
    }
    private JSONObject character(CatalogModels.Character item) {
        JSONObject row = new JSONObject();
        try { row.put("id", item.id).put("name", item.name).put("publisher_id", item.publisherId).put("origin_id", item.originId).put("gender", item.gender).put("image", item.imageUrl); }
        catch (Exception ignored) { }
        return row;
    }
    private void put(String key, Object value) { try { report.put(key, value); } catch (Exception ignored) { } }
    private void fail(String stage) { put("success", false); put("failed_stage", stage); save(); }
    private void save() {
        try {
            File directory = new File(getFilesDir(), "diagnostics");
            if (!directory.exists() && !directory.mkdirs()) return;
            File temporary = new File(directory, check + ".tmp"), destination = new File(directory, check + ".json");
            try (FileOutputStream stream = new FileOutputStream(temporary)) { stream.write(report.toString(2).getBytes(StandardCharsets.UTF_8)); }
            if (!temporary.renameTo(destination)) temporary.delete();
        } catch (Exception ignored) { }
        finally { finish(); }
    }
}
