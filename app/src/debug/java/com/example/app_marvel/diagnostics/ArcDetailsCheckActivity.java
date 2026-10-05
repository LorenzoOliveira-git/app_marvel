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
import java.util.HashSet;
import java.util.Set;

/** Integração real em debug. Nenhum dado de amostra entra no catálogo de produção. */
public final class ArcDetailsCheckActivity extends Activity {
    private final JSONObject report=new JSONObject();
    private final Set<Integer> observed=new HashSet<>();
    private MarvelRepository repository;
    private String check;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);check=getIntent().getStringExtra("check");
        if (!("arc-detail".equals(check)||"arc-detail-cache".equals(check))) { finish();return; }
        repository=((MarvelApplication)getApplication()).getContainer().getCatalog();
        repository.arcDetails(40615,result -> {
            if (result.failure!=null) { fail("detail");return; }
            ArcDetails detail=result.data;
            if (detail.arc.id!=40615 || detail.arc.publisherId!=31 || !detail.arc.name.equals("\"Avengers\" Civil War") || detail.issues.size()<24
                    || MarvelRepository.plain(detail.originalDescription).isEmpty()) { fail("identity");return; }
            put("arc_id",detail.arc.id);put("name",detail.arc.name);put("reference_count",detail.issues.size());put("site_url",detail.arc.siteUrl);
            var translations=((MarvelApplication)getApplication()).getContainer().getTranslations();
            CatalogDescriptions.translateArc(translations,detail,"deck",summary -> {
                if (summary.getFailure()!=null || summary.getText().isEmpty()) { fail("summary");return; }
                put("summary",summary.getText());
                CatalogDescriptions.translateArc(translations,detail,"description",description -> {
                    if (description.getFailure()!=null || description.getText().contains("<p>") || !description.getText().contains("Captain America")) { fail("description_names");return; }
                    put("description",description.getText());put("description_cached",description.isFromCache());
                    repository.arcIssues(detail,0,first -> {
                        if (!page("first",detail,0,first)) return;
                        repository.arcIssues(detail,first.data.nextOffset,next -> {
                            if (!page("next",detail,first.data.nextOffset,next)) return;
                            repository.arcIssues(detail,-1,invalid -> {
                                if (invalid.failure==null) { fail("invalid_cursor");return; }
                                put("invalid_cursor_rejected",true);
                                repository.arcDetails(40503,foreign -> {
                                    if (foreign.failure==null) { fail("foreign_arc");return; }
                                    put("foreign_arc_rejected",true);
                                    repository.arcDetails(41147,other -> {
                                        if (other.failure!=null || other.data.arc.id!=41147) { fail("second_arc");return; }
                                        put("second_arc_name",other.data.arc.name);put("second_arc_references",other.data.issues.size());
                                        put("second_arc_has_summary",!MarvelRepository.plain(other.data.originalDeck).isEmpty());
                                        put("second_arc_has_description",!MarvelRepository.plain(other.data.originalDescription).isEmpty());
                                        put("success",true);save();
                                    });
                                });
                            });
                        });
                    });
                });
            });
        });
    }
    private boolean page(String label,ArcDetails detail,int start,Result<AppearancePage> result) {
        if (result.failure!=null || result.data.items.isEmpty() || result.data.nextOffset<=start) { fail(label);return false; }
        JSONArray rows=new JSONArray();int position=start;
        for (Issue issue:result.data.items) {
            while (position<result.data.nextOffset && detail.issues.get(position).id!=issue.id) position++;
            if (position>=result.data.nextOffset || !observed.add(issue.id) || issue.volumeId<=0 || issue.title.isEmpty()) { fail(label+"_sequence");return false; }
            position++;
            try { rows.put(new JSONObject().put("id",issue.id).put("title",issue.title).put("volume_id",issue.volumeId).put("cover_date",issue.publicationDate)); }
            catch (Exception error) { fail("serialize");return false; }
        }
        put(label,rows);put(label+"_offset",result.data.nextOffset);put(label+"_more",result.data.hasMore);return true;
    }
    private void put(String name,Object value) { try { report.put(name,value); } catch (Exception ignored) { } }
    private void fail(String stage) { put("success",false);put("failed_stage",stage);save(); }
    private void save() {
        try {
            File dir=new File(getFilesDir(),"diagnostics");if (!dir.exists()&&!dir.mkdirs()) return;
            File temporary=new File(dir,check+".tmp");
            try (FileOutputStream stream=new FileOutputStream(temporary)) { stream.write(report.toString(2).getBytes(StandardCharsets.UTF_8)); }
            if (!temporary.renameTo(new File(dir,check+".json"))) temporary.delete();
        } catch (Exception ignored) { } finally { finish(); }
    }
}
