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
public final class SeriesCheckActivity extends Activity {
    private final JSONObject report=new JSONObject();
    private final Set<Integer> observed=new HashSet<>();
    private MarvelRepository repository;
    private String check;
    private int publisher;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);check=getIntent().getStringExtra("check");
        if (!("series".equals(check)||"series-cache".equals(check))) { finish();return; }
        repository=((MarvelApplication)getApplication()).getContainer().getCatalog();
        repository.featuredSeries(featured -> {
            if (featured.failure!=null || featured.data.id!=1 || !"Agents of S.H.I.E.L.D.".equals(featured.data.title)) { fail("featured");return; }
            if (featured.data.publisherId!=31 || !featured.data.startYear.equals("2013") || featured.data.episodeCount!=136) { fail("featured_metadata");return; }
            publisher=featured.data.publisherId;put("featured",serialize(featured.data));
            repository.series("",false,0,first -> {
                if (!page("first",first,true)) return;
                if (first.data.items.size()<2 || !first.data.hasMore) { fail("first_paging");return; }
                repository.series("",false,first.data.nextOffset,next -> {
                    if (!page("next",next,true) || next.data.nextOffset<=first.data.nextOffset) { fail("cursor");return; }
                    repository.series("",true,0,descending -> {
                        if (!page("descending",descending,false)) return;
                        repository.series("aGeNtS",false,0,search -> {
                            if (!page("search",search,false) || search.data.items.isEmpty() || search.data.hasMore) { fail("search");return; }
                            for (var item:search.data.items) if (!item.title.toLowerCase(Locale.ROOT).contains("agents")) { fail("search_title");return; }
                            repository.series("Batman",false,0,foreign -> {
                                if (foreign.failure!=null || !foreign.data.items.isEmpty() || foreign.data.hasMore) { fail("foreign");return; }
                                put("foreign_excluded",true);
                                repository.series("marv-no-such-series-918237",false,0,empty -> {
                                    if (empty.failure!=null || !empty.data.items.isEmpty() || empty.data.hasMore) { fail("empty");return; }
                                    put("empty_verified",true);
                                    repository.series("Agents,publisher:10",false,0,invalid -> {
                                        if (invalid.failure==null) { fail("filter_injection");return; }
                                        repository.series("",false,-1,cursor -> {
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
    private boolean page(String name,CatalogModels.Result<CatalogModels.SeriesPage> result,boolean unique) {
        if (result.failure!=null) { fail(name);return false; }
        JSONArray rows=new JSONArray();
        for (var item:result.data.items) {
            if (item.publisherId!=publisher || item.id<=0 || !item.siteUrl.endsWith("/4075-"+item.id+"/") || (unique && !observed.add(item.id))) { fail(name+"_identity");return false; }
            rows.put(serialize(item));
        }
        put(name,rows);put(name+"_offset",result.data.nextOffset);put(name+"_more",result.data.hasMore);return true;
    }
    private JSONObject serialize(CatalogModels.Series item) {
        JSONObject row=new JSONObject();try { row.put("id",item.id).put("name",item.title).put("publisher_id",item.publisherId)
            .put("start_year",item.startYear).put("episode_count",item.episodeCount).put("image_url",item.imageUrl).put("site_url",item.siteUrl); } catch(Exception ignored) { }return row;
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
