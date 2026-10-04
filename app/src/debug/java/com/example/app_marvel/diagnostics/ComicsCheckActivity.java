package com.example.app_marvel.diagnostics;

import android.app.Activity;
import android.os.Bundle;
import com.example.app_marvel.MarvelApplication;
import com.example.app_marvel.data.catalog.CatalogModels;
import com.example.app_marvel.data.catalog.MarvelRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Diagnóstico real via ADB, disponível somente na variante debug. */
public final class ComicsCheckActivity extends Activity {
    private final JSONObject report = new JSONObject();
    private final Set<Integer> volumes = new HashSet<>();
    private MarvelRepository repository;
    private String check;
    private int sampleVolume;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); check = getIntent().getStringExtra("check");
        if (!"comics".equals(check) && !"comics-cache".equals(check)) { finish(); return; }
        repository = ((MarvelApplication) getApplication()).getContainer().getCatalog();
        repository.volumes(result -> {
            if (result.failure != null || result.data.isEmpty()) { fail("volumes"); return; }
            int count = 0;
            for (var ref : result.data) {
                volumes.add(ref.id);
                if (ref.name.equals("Marvel Rivals Infinity Comic")) { sampleVolume = ref.id; count++; }
            }
            if (count != 1) { fail("sample_volume_identity"); return; }
            put("volume_count",volumes.size()); put("sample_volume",sampleVolume); put("sample_name","Marvel Rivals Infinity Comic");
            repository.comics(0,false,0,first -> {
                if (!page("first",first,0,false)) return;
                repository.comics(0,false,first.data.nextOffset,next -> {
                    if (!page("next",next,0,false)) return;
                    scoped();
                });
            });
        });
    }
    private void scoped() {
        repository.comics(sampleVolume,false,0,first -> {
            if (!page("scoped",first,sampleVolume,false)) return;
            repository.comics(sampleVolume,false,first.data.nextOffset,next -> {
                if (!page("scoped_next",next,sampleVolume,false)) return;
                repository.comics(sampleVolume,true,0,old -> {
                    if (!page("oldest",old,sampleVolume,true)) return;
                    repository.comics(Integer.MAX_VALUE,false,0,invalid -> {
                        if (volumes.contains(Integer.MAX_VALUE) || invalid.failure == null) { fail("foreign_volume"); return; }
                        put("foreign_volume_rejected",true); put("success",true); save();
                    });
                });
            });
        });
    }
    private boolean page(String name, CatalogModels.Result<CatalogModels.ComicsPage> result, int scope, boolean oldest) {
        if (result.failure != null || result.data.items.isEmpty()) { fail(name); return false; }
        JSONArray rows = new JSONArray(); String previous = null;
        for (var issue : result.data.items) {
            if (!volumes.contains(issue.volumeId) || (scope != 0 && issue.volumeId != scope)
                    || (previous != null && (oldest ? issue.publicationDate.compareTo(previous)<0 : issue.publicationDate.compareTo(previous)>0))) {
                fail(name+"_relationship_order"); return false;
            }
            previous = issue.publicationDate;
            try { rows.put(new JSONObject().put("id",issue.id).put("volume_id",issue.volumeId).put("title",issue.title)
                    .put("store_date",issue.publicationDate).put("site_url",issue.siteUrl).put("image_url",issue.imageUrl)); }
            catch (Exception ignored) { fail("serialize"); return false; }
        }
        put(name,rows); put(name+"_offset",result.data.nextOffset); put(name+"_more",result.data.hasMore); return true;
    }
    private void put(String name,Object value) { try { report.put(name,value); } catch (Exception ignored) { } }
    private void fail(String stage) { put("success",false); put("failed_stage",stage); save(); }
    private void save() {
        try {
            File directory = new File(getFilesDir(),"diagnostics"); if (!directory.exists() && !directory.mkdirs()) return;
            File temporary = new File(directory,check+".tmp"), destination = new File(directory,check+".json");
            try (FileOutputStream stream = new FileOutputStream(temporary)) { stream.write(report.toString(2).getBytes(StandardCharsets.UTF_8)); }
            if (!temporary.renameTo(destination)) temporary.delete();
        } catch (Exception ignored) { } finally { finish(); }
    }
}
