package com.example.app_marvel.data.comicvine;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Lê apenas o catálogo local da variante designPreview; fixtures não entram no APK normal. */
final class PreviewCatalog {
    private static JSONObject catalog;
    private PreviewCatalog() { }
    static synchronized JSONObject read(Context context, String path, Map<String,String> params)
            throws IOException, JSONException {
        if (!com.example.app_marvel.BuildConfig.DESIGN_PREVIEW) throw new IOException("Preview only");
        if (catalog == null) {
            try (InputStream input = context.getAssets().open("catalog-preview.json")) {
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                catalog = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
            }
        }
        String resource = path.split("/")[0];
        String collection;
        switch (resource) {
            case "publisher": collection = "publishers"; break;
            case "character": collection = "characters"; break;
            case "team": collection = "teams"; break;
            case "volume": collection = "volumes"; break;
            case "issue": collection = "issues"; break;
            case "story_arc": collection = "story_arcs"; break;
            case "movie": collection = "movies"; break;
            case "series": collection = "series_list"; break;
            case "episode": collection = "episodes"; break;
            default: collection = resource;
        }
        JSONArray source = catalog.optJSONArray(collection);
        if (source == null) throw new JSONException("Unsupported sample resource");
        List<JSONObject> rows = new ArrayList<>();
        String[] parts = path.split("/");
        int detailId = parts.length > 1 ? Integer.parseInt(parts[1].substring(parts[1].indexOf('-')+1)) : 0;
        for (int i = 0; i < source.length(); i++) {
            JSONObject row = source.getJSONObject(i);
            if (detailId > 0 ? row.optInt("id") == detailId : matches(row, params.getOrDefault("filter", ""))) rows.add(row);
        }
        String[] sort = params.getOrDefault("sort", "id:asc").split(":");
        Comparator<JSONObject> order = sort[0].equals("id")
                ? Comparator.comparingInt(row -> row.optInt("id"))
                : Comparator.comparing(row -> row.optString(sort[0]).toLowerCase(Locale.ROOT));
        if (sort.length > 1 && sort[1].equals("desc")) order = order.reversed();
        rows.sort(order);
        JSONObject response = new JSONObject().put("status_code",1).put("error","OK")
                .put("number_of_total_results",rows.size());
        if (detailId > 0) {
            if (rows.size() != 1) throw new JSONException("Unknown sample detail");
            return response.put("results",rows.get(0)).put("number_of_page_results",1);
        }
        int offset = Integer.parseInt(params.getOrDefault("offset","0"));
        int limit = Integer.parseInt(params.getOrDefault("limit","20"));
        JSONArray page = new JSONArray();
        for (int i=offset; i < rows.size() && i < offset+limit; i++) page.put(rows.get(i));
        return response.put("results",page).put("number_of_page_results",page.length());
    }
    private static boolean matches(JSONObject row, String filter) {
        for (String clause : filter.split(",")) {
            int separator = clause.indexOf(':'); if (separator < 0) continue;
            String field = clause.substring(0,separator), value = clause.substring(separator+1);
            if (field.equals("name")) {
                if (!row.optString("name").toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT))) return false;
            } else if (field.equals("store_date")) {
                String[] range = value.split("\\|"); String date = row.optString(field);
                if (date.compareTo(range[0]) < 0 || date.compareTo(range[range.length-1]) > 0) return false;
            } else {
                JSONObject reference = row.optJSONObject(field);
                String actual = reference == null ? row.optString(field) : String.valueOf(reference.optInt("id"));
                if (!Arrays.asList(value.split("\\|")).contains(actual)) return false;
            }
        }
        return true;
    }
}
