package com.example.app_marvel.diagnostics;

import android.app.Activity;
import android.os.Bundle;
import com.example.app_marvel.MarvelApplication;
import com.example.app_marvel.data.catalog.CatalogDescriptions;
import com.example.app_marvel.data.catalog.CatalogModels.*;
import com.example.app_marvel.data.catalog.MarvelRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Diagnóstico nativo de fontes reais. IDs de amostra só existem em debug. */
public final class IssueCheckActivity extends Activity {
    private final JSONObject report = new JSONObject();
    private MarvelRepository repository;
    private String check;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); check = getIntent().getStringExtra("check");
        if (!("issue".equals(check) || "issue-cache".equals(check))) { finish(); return; }
        repository = ((MarvelApplication) getApplication()).getContainer().getCatalog();
        repository.issueDetails(105342,result -> {
            if (result.failure != null) { fail("historical_detail"); return; }
            var detail = result.data;
            if (detail.issue.id != 105342 || detail.issue.volumeId != 5533 || !detail.issue.title.equals("Amazing Fantasy #15")
                    || MarvelRepository.plain(detail.originalDescription).isEmpty() || detail.creators.isEmpty()) { fail("historical_identity"); return; }
            put("historical",data(detail));
            repository.issueRelations(detail,"characters",0,characters -> {
                if (characters.failure != null || characters.data.items.isEmpty()) { fail("characters"); return; }
                JSONArray rows = new JSONArray();
                for (var item : characters.data.items) {
                    if (item.publisherId != detail.publisherId || detail.characters.stream().noneMatch(ref -> ref.id == item.id)) { fail("character_relationship"); return; }
                    try { rows.put(new JSONObject().put("id",item.id).put("name",item.name).put("publisher_id",item.publisherId)); }
                    catch (Exception error) { fail("serialize"); return; }
                }
                put("characters",rows); put("characters_more",characters.data.hasMore);
                repository.issueRelations(detail,"teams",0,teams -> {
                    if (teams.failure != null) { fail("teams"); return; }
                    JSONArray verified = new JSONArray();
                    for (var team : teams.data.items) {
                        if (team.id == 57219 || team.publisherId != detail.publisherId) { fail("foreign_team"); return; }
                        verified.put(team.id);
                    }
                    put("teams",verified); put("foreign_team_rejected",true);
                    var translations = ((MarvelApplication) getApplication()).getContainer().getTranslations();
                    CatalogDescriptions.translateIssue(translations,detail,"description",translated -> {
                        if (translated.getFailure() != null || translated.getText().trim().isEmpty() || translated.getText().contains("<p>")) { fail("description_translation"); return; }
                        if (!translated.getText().contains("Spider-Man")) { fail("proper_name"); return; }
                        put("description",translated.getText()); put("translation_from_cache",translated.isFromCache()); recent();
                    });
                });
            });
        });
    }
    private void recent() {
        repository.issueDetails(1195913,result -> {
            if (result.failure != null) { fail("recent_detail"); return; }
            var detail = result.data;
            if (detail.issue.id != 1195913) { fail("recent_identity"); return; }
            put("recent",data(detail));
            repository.issueDetails(6,foreign -> {
                if (foreign.failure == null) { fail("foreign_issue"); return; }
                put("foreign_issue_rejected",true);
                repository.issueDetails(-1,invalid -> {
                    if (invalid.failure == null) { fail("invalid_issue"); return; }
                    put("invalid_issue_rejected",true); put("success",true); save();
                });
            });
        });
    }
    private JSONObject data(IssueDetails detail) {
        JSONObject value = new JSONObject();
        try {
            value.put("id",detail.issue.id).put("volume_id",detail.issue.volumeId).put("title",detail.issue.title).put("publisher_id",detail.publisherId)
                    .put("store_date",detail.issue.publicationDate).put("cover_date",detail.coverDate).put("name",detail.name)
                    .put("original_deck",detail.originalDeck).put("original_description",detail.originalDescription).put("image_url",detail.issue.imageUrl)
                    .put("site_url",detail.issue.siteUrl).put("creators",credits(detail.creators)).put("arcs",credits(detail.arcs))
                    .put("locations",credits(detail.locations)).put("objects",credits(detail.objects)).put("concepts",credits(detail.concepts));
        } catch (Exception error) { fail("serialize"); }
        return value;
    }
    private JSONArray credits(java.util.List<Credit> items) throws Exception {
        JSONArray result = new JSONArray();
        for (Credit item : items) result.put(new JSONObject().put("id",item.reference.id).put("name",item.reference.name).put("role",item.role).put("path",item.reference.path).put("site_url",item.siteUrl));
        return result;
    }
    private void put(String field,Object value) { try { report.put(field,value); } catch (Exception ignored) { } }
    private void fail(String stage) { put("success",false); put("failed_stage",stage); save(); }
    private void save() {
        try {
            File directory = new File(getFilesDir(),"diagnostics"); if (!directory.exists() && !directory.mkdirs()) return;
            File temporary = new File(directory,check+".tmp");
            try (FileOutputStream stream = new FileOutputStream(temporary)) { stream.write(report.toString(2).getBytes(StandardCharsets.UTF_8)); }
            if (!temporary.renameTo(new File(directory,check+".json"))) temporary.delete();
        } catch (Exception ignored) { } finally { finish(); }
    }
}
