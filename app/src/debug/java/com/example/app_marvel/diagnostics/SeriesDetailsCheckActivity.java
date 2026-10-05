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

/** Integração com respostas reais; amostras restritas à variante debug. */
public final class SeriesDetailsCheckActivity extends Activity {
    private final JSONObject report=new JSONObject();
    private MarvelRepository repository;
    private String check;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);check=getIntent().getStringExtra("check");
        if (!("series-detail".equals(check)||"series-detail-cache".equals(check))) { finish();return; }
        repository=((MarvelApplication)getApplication()).getContainer().getCatalog();
        repository.seriesDetails(1,result -> {
            if(result.failure!=null) { fail("detail");return; }
            SeriesDetails detail=result.data;
            if(detail.series.publisherId!=31 || !detail.series.title.equals("Agents of S.H.I.E.L.D.") || detail.episodes.size()!=136 || detail.firstEpisode==null || detail.firstEpisode.id!=1 || detail.lastEpisode==null || detail.lastEpisode.id!=32766) { fail("identity");return; }
            put("name",detail.series.title);put("episode_references",detail.episodes.size());put("first_id",detail.firstEpisode.id);put("last_id",detail.lastEpisode.id);
            repository.seriesEpisodes(detail,0,first -> {
                if(!episodes("first",first,12)) return;
                if(first.data.items.get(0).id!=1 || !first.data.items.get(0).number.equals("101") || !first.data.items.get(0).airDate.equals("2013-09-24")) { fail("pilot");return; }
                repository.seriesEpisodes(detail,first.data.nextOffset,next -> {
                    if(!episodes("next",next,12)) return;
                    Set<Integer> ids=new HashSet<>();for(Episode e:first.data.items) ids.add(e.id);for(Episode e:next.data.items) if(!ids.add(e.id)) { fail("duplicate");return; }
                    repository.seriesEpisodes(detail,detail.episodes.size(),end -> {
                        if(end.failure!=null || !end.data.items.isEmpty() || end.data.hasMore) { fail("end");return; }
                        repository.seriesCharacters(detail,0,characters -> {
                            if(characters.failure!=null || characters.data.items.isEmpty() || characters.data.items.stream().anyMatch(c -> c.publisherId!=31)) { fail("characters");return; }
                            JSONArray names=new JSONArray();for(RelatedItem c:characters.data.items) { JSONObject row=new JSONObject();try { row.put("id",c.id).put("name",c.name); } catch(Exception ignored) {} names.put(row); }put("characters",names);
                            repository.seriesDetails(331,foreign -> {
                                if(foreign.failure==null) { fail("foreign");return; }put("foreign_rejected",true);
                                repository.seriesDetails(-1,invalid -> {
                                    if(invalid.failure==null) { fail("invalid");return; }put("invalid_rejected",true);
                                    repository.seriesDetails(1315,agatha -> {
                                        if(agatha.failure!=null || agatha.data.series.publisherId!=31 || agatha.data.episodes.size()!=9) { fail("second_series");return; }
                                        put("second_name",agatha.data.series.title);put("second_references",agatha.data.episodes.size());
                                        var translations=((MarvelApplication)getApplication()).getContainer().getTranslations();
                                        CatalogDescriptions.translateSeries(translations,agatha.data,"description",description -> {
                                            if(description.getFailure()!=null || !description.getText().contains("Agatha Harkness") || description.getText().contains("<p>")) { fail("translation");return; }
                                            put("description",description.getText());put("description_cached",description.isFromCache());put("success",true);save();
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
    private boolean episodes(String label,Result<EpisodePage> result,int count) {
        if(result.failure!=null || result.data.items.size()!=count) { fail(label);return false; }
        JSONArray rows=new JSONArray();for(Episode e:result.data.items) try { rows.put(new JSONObject().put("id",e.id).put("name",e.name).put("number",e.number).put("air_date",e.airDate).put("site_url",e.siteUrl)); } catch(Exception error) { fail("serialize");return false; }
        put(label,rows);put(label+"_offset",result.data.nextOffset);put(label+"_more",result.data.hasMore);return true;
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
