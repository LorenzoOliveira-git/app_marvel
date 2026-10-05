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
public final class MovieDetailsCheckActivity extends Activity {
    private final JSONObject report=new JSONObject();
    private MarvelRepository repository;
    private String check;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);check=getIntent().getStringExtra("check");
        if (!("movie-detail".equals(check)||"movie-detail-cache".equals(check))) { finish();return; }
        repository=((MarvelApplication)getApplication()).getContainer().getCatalog();
        repository.movieDetails(17,result -> {
            if (result.failure!=null) { fail("detail");return; }
            MovieDetails detail=result.data;
            if (detail.movie.id!=17 || detail.movie.publisherId!=31 || !detail.movie.title.equals("Iron Man") || detail.movie.runtime!=126
                    || !detail.rating.equals("PG-13") || detail.studios.isEmpty() || detail.producers.isEmpty() || detail.writers.isEmpty()) { fail("identity");return; }
            put("movie_id",detail.movie.id);put("name",detail.movie.title);put("runtime",detail.movie.runtime);put("rating",detail.rating);put("site_url",detail.movie.siteUrl);
            put("character_references",detail.characters.size());put("team_references",detail.teams.size());
            put("studios",credits(detail.studios));put("producers",credits(detail.producers));put("writers",credits(detail.writers));
            var translations=((MarvelApplication)getApplication()).getContainer().getTranslations();
            CatalogDescriptions.translateMovie(translations,detail,"deck",summary -> {
                if (summary.getFailure()!=null || !summary.getText().contains("Robert Downey Jr") || !summary.getText().contains("Iron Man")) { fail("summary_names");return; }
                put("summary",summary.getText());
                CatalogDescriptions.translateMovie(translations,detail,"description",description -> {
                    if (description.getFailure()!=null || description.getText().contains("<p>") || !description.getText().contains("Tony Stark")) { fail("description_names");return; }
                    put("description",description.getText());put("description_cached",description.isFromCache());
                    repository.movieRelations(detail,"characters",0,characters -> {
                        if (!page("characters",detail,characters)) return;
                        if (characters.data.items.stream().noneMatch(c -> c.id==1455)) { fail("iron_man_profile");return; }
                        repository.movieRelations(detail,"characters",characters.data.nextOffset,last -> {
                            if (last.failure!=null || !last.data.items.isEmpty() || last.data.hasMore) { fail("character_cursor");return; }
                            put("characters_end_verified",true);
                            repository.movieRelations(detail,"teams",0,teams -> {
                                if (!page("teams",detail,teams)) return;
                                repository.movieDetails(4,foreign -> {
                                    if (foreign.failure==null) { fail("foreign_movie");return; }
                                    put("foreign_movie_rejected",true);
                                    repository.movieDetails(1552,other -> {
                                        if (other.failure!=null || !other.data.movie.title.equals("Ant-Man")) { fail("second_movie");return; }
                                        put("second_movie_name",other.data.movie.title);put("second_movie_runtime",other.data.movie.runtime);
                                        put("second_movie_distributor",other.data.distributor);
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
    private JSONArray credits(java.util.List<Credit> items) {
        JSONArray rows=new JSONArray();for (Credit c:items) try { rows.put(new JSONObject().put("id",c.reference.id).put("name",c.reference.name).put("site_url",c.siteUrl)); } catch(Exception ignored) { }return rows;
    }
    private boolean page(String label,MovieDetails detail,Result<RelationPage> result) {
        if (result.failure!=null || result.data.items.isEmpty()) { fail(label);return false; }
        Set<Integer> unique=new HashSet<>(),references=new HashSet<>();for (Reference ref:detail.relations(label)) references.add(ref.id);
        JSONArray rows=new JSONArray();
        for (RelatedItem item:result.data.items) {
            if (item.publisherId!=detail.movie.publisherId || !unique.add(item.id) || !references.contains(item.id)) { fail(label+"_identity");return false; }
            try { rows.put(new JSONObject().put("id",item.id).put("name",item.name).put("publisher_id",item.publisherId)); } catch(Exception error) { fail("serialize");return false; }
        }
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
