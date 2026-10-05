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

/** Respostas reais; IDs e termos de amostra ficam somente em debug. */
public final class ArcsCheckActivity extends Activity {
    private final JSONObject report=new JSONObject();
    private final Set<Integer> observed=new HashSet<>();
    private MarvelRepository repository;
    private String check;
    private int publisher;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);check=getIntent().getStringExtra("check");
        if (!("arcs".equals(check)||"arcs-cache".equals(check))) { finish();return; }
        repository=((MarvelApplication) getApplication()).getContainer().getCatalog();
        repository.arcs("",false,0,first -> {
            if (!page("first",first,false,true)) return;
            repository.arcs("",false,first.data.nextOffset,next -> {
                if (!page("next",next,false,true)) return;
                String last=first.data.items.get(first.data.items.size()-1).name.toLowerCase(Locale.ROOT);
                if (last.compareTo(next.data.items.get(0).name.toLowerCase(Locale.ROOT))>0 || next.data.nextOffset<=first.data.nextOffset) { fail("page_order");return; }
                repository.arcs("",true,0,descending -> {
                    if (!page("descending",descending,true,false)) return;
                    repository.arcs("cIvIl WaR",false,0,search -> {
                        if (!page("search",search,false,false)) return;
                        for (var arc:search.data.items) if (!arc.name.toLowerCase(Locale.ROOT).contains("civil war")) { fail("search_name");return; }
                        if (search.data.items.stream().noneMatch(arc -> arc.id==40615)) { fail("civil_war_identity");return; }
                        repository.arcs("The Killing Joke",false,0,foreign -> {
                            if (foreign.failure!=null||!foreign.data.items.isEmpty()||foreign.data.hasMore) { fail("foreign_arc");return; }
                            put("foreign_arc_excluded",true);
                            repository.arcs("marv-no-such-arc-918237",false,0,empty -> {
                                if (empty.failure!=null||!empty.data.items.isEmpty()||empty.data.hasMore) { fail("empty_search");return; }
                                put("empty_search_verified",true);
                                repository.arcs("",false,-1,invalid -> {
                                    if (invalid.failure==null) { fail("invalid_cursor");return; }
                                    put("invalid_cursor_rejected",true);put("success",true);save();
                                });
                            });
                        });
                    });
                });
            });
        });
    }
    private boolean page(String name,CatalogModels.Result<CatalogModels.ArcsPage> result,boolean descending,boolean unique) {
        if (result.failure!=null||result.data.items.isEmpty()) { fail(name);return false; }
        JSONArray rows=new JSONArray();String previous=null;
        for (var arc:result.data.items) {
            if (publisher==0) publisher=arc.publisherId;
            String current=arc.name.toLowerCase(Locale.ROOT);
            if (arc.publisherId!=publisher || arc.id<=0 || (unique && !observed.add(arc.id))
                    || (previous!=null && (descending ? current.compareTo(previous)>0:current.compareTo(previous)<0))) { fail(name+"_identity_order");return false; }
            previous=current;
            try { rows.put(new JSONObject().put("id",arc.id).put("name",arc.name).put("publisher_id",arc.publisherId).put("image_url",arc.imageUrl).put("site_url",arc.siteUrl)); }
            catch (Exception error) { fail("serialize");return false; }
        }
        put(name,rows);put(name+"_offset",result.data.nextOffset);put(name+"_more",result.data.hasMore);return true;
    }
    private void put(String name,Object value) { try { report.put(name,value); } catch (Exception ignored) { } }
    private void fail(String stage) { put("success",false);put("failed_stage",stage);save(); }
    private void save() {
        try {
            File directory=new File(getFilesDir(),"diagnostics");if (!directory.exists()&&!directory.mkdirs()) return;
            File temporary=new File(directory,check+".tmp");
            try (FileOutputStream stream=new FileOutputStream(temporary)) { stream.write(report.toString(2).getBytes(StandardCharsets.UTF_8)); }
            if (!temporary.renameTo(new File(directory,check+".json"))) temporary.delete();
        } catch (Exception ignored) { } finally { finish(); }
    }
}
