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
import java.util.Locale;
import java.util.Set;

/** Integração real, sem dados substitutos; atividade exclusivamente debug. */
public final class MoviesCheckActivity extends Activity {
    private final JSONObject report=new JSONObject();
    private final Set<Integer> observed=new HashSet<>();
    private MarvelRepository repository;
    private String check;
    private int publisher;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);check=getIntent().getStringExtra("check");
        if (!("movies".equals(check)||"movies-cache".equals(check))) { finish();return; }
        repository=((MarvelApplication)getApplication()).getContainer().getCatalog();
        repository.featuredMovie(featured -> {
            if (featured.failure!=null || featured.data.id!=17 || !"Iron Man".equals(featured.data.title)) { fail("featured");return; }
            publisher=featured.data.publisherId;put("featured",serialize(featured.data));
            repository.movies("",false,0,first -> {
                if (!page("first",first,true)) return;
                repository.movies("",false,first.data.nextOffset,next -> {
                    if (!page("next",next,true) || next.data.nextOffset<=first.data.nextOffset) { fail("cursor");return; }
                    repository.movies("",true,0,descending -> {
                        if (!page("descending",descending,false)) return;
                        repository.movies("iRoN mAn",false,0,search -> {
                            if (!page("search",search,false) || search.data.items.size()!=3 || search.data.hasMore) { fail("search");return; }
                            for (var item:search.data.items) if (!item.title.toLowerCase(Locale.ROOT).contains("iron man")) { fail("search_title");return; }
                            repository.movies("Batman",false,0,foreign -> {
                                if (foreign.failure!=null || !foreign.data.items.isEmpty() || foreign.data.hasMore) { fail("foreign");return; }
                                put("foreign_excluded",true);
                                repository.movies("marv-no-such-film-918237",false,0,empty -> {
                                    if (empty.failure!=null || !empty.data.items.isEmpty() || empty.data.hasMore) { fail("empty");return; }
                                    put("empty_verified",true);
                                    repository.movies("Iron Man,studios:10",false,0,invalid -> {
                                        if (invalid.failure==null) { fail("filter_injection");return; }
                                        repository.movies("",false,-1,cursor -> {
                                            if (cursor.failure==null) { fail("invalid_cursor");return; }
                                            put("invalid_inputs_rejected",true);put("success",true);save();
                                        });
                                    });
                                });
                            });
                        });
                    });
                });
            });
        });
    }
    private boolean page(String name,CatalogModels.Result<CatalogModels.MoviesPage> result,boolean unique) {
        if (result.failure!=null) { fail(name);return false; }
        JSONArray rows=new JSONArray();
        for (var item:result.data.items) {
            if (item.publisherId!=publisher || item.id<=0 || (unique && !observed.add(item.id))) { fail(name+"_identity");return false; }
            rows.put(serialize(item));
        }
        put(name,rows);put(name+"_offset",result.data.nextOffset);put(name+"_more",result.data.hasMore);return true;
    }
    private JSONObject serialize(CatalogModels.Movie item) {
        JSONObject row=new JSONObject();try { row.put("id",item.id).put("name",item.title).put("publisher_id",item.publisherId)
            .put("runtime",item.runtime).put("image_url",item.imageUrl).put("site_url",item.siteUrl); } catch(Exception ignored) { }return row;
    }
    private void put(String key,Object value) { try { report.put(key,value); } catch(Exception ignored) { } }
    private void fail(String stage) { put("success",false);put("failed_stage",stage);save(); }
    private void save() {
        try {
            File directory=new File(getFilesDir(),"diagnostics");if (!directory.exists()&&!directory.mkdirs()) return;
            File temporary=new File(directory,check+".tmp");
            try(FileOutputStream stream=new FileOutputStream(temporary)) { stream.write(report.toString(2).getBytes(StandardCharsets.UTF_8)); }
            if (!temporary.renameTo(new File(directory,check+".json"))) temporary.delete();
        } catch(Exception ignored) { } finally { finish(); }
    }
}
