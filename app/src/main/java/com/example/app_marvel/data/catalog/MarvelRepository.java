package com.example.app_marvel.data.catalog;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import com.example.app_marvel.data.comicvine.ComicVineClient;
import com.example.app_marvel.data.catalog.CatalogModels.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Contrato validado com respostas reais. Relações são verificadas antes da exibição. */
public final class MarvelRepository {
    private static final long DAY = 86_400_000L;
    private static final String CHARACTER_FIELDS = "id,name,real_name,publisher,origin,gender,image,deck,site_detail_url";
    private static final String ISSUE_FIELDS = "id,name,issue_number,volume,image,store_date,site_detail_url";
    private final ComicVineClient client;
    private final CatalogCache cache;
    private final ExecutorService storage = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, List<Callback<JSONObject>>> pending = new HashMap<>();
    private static final class Publisher {
        final int id;
        final String path;
        Publisher(int id, String path) { this.id = id; this.path = path; }
    }

    public MarvelRepository(Context context, ComicVineClient client) {
        this.client = client; cache = new CatalogCache(context.getApplicationContext());
    }

    private static Map<String, String> params(String... pairs) {
        Map<String, String> result = new TreeMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i], pairs[i + 1]);
        return result;
    }

    private void request(String path, Map<String, String> params, long ttl, Callback<JSONObject> callback) {
        storage.execute(() -> {
            String key = path + new TreeMap<>(params);
            if (pending.containsKey(key)) { pending.get(key).add(callback); return; }
            CatalogCache.Entry entry;
            try { entry = cache.get(key); }
            catch (RuntimeException unavailable) { callback.complete(Result.failed(Failure.STORAGE)); return; }
            if (entry != null && System.currentTimeMillis() - entry.savedAt < ttl) {
                callback.complete(Result.success(entry.payload)); return;
            }
            pending.put(key, new ArrayList<>(Collections.singletonList(callback)));
            client.get(path, params, response -> storage.execute(() -> {
                Result<JSONObject> result;
                JSONObject payload = response.getPayload();
                if (response.getFailure() == null && payload != null && payload.optInt("status_code") == 1) {
                    try { cache.put(key, payload); result = Result.success(payload); }
                    catch (RuntimeException unavailable) { result = Result.failed(Failure.STORAGE); }
                } else if (entry != null) result = Result.success(entry.payload);
                else result = Result.failed(response.getFailure() == null ? Failure.DATA : Failure.CONNECTION);
                List<Callback<JSONObject>> callbacks = pending.remove(key);
                for (Callback<JSONObject> listener : callbacks) listener.complete(result);
            }));
        });
    }

    private <T> void deliver(Callback<T> callback, Result<T> result) { main.post(() -> callback.complete(result)); }
    private static String text(JSONObject object, String field) {
        return object == null || object.isNull(field) ? "" : object.optString(field, "").trim();
    }
    public static String plain(String html) {
        return Html.fromHtml(html.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>", ""), Html.FROM_HTML_MODE_LEGACY)
                .toString().trim();
    }
    public static String resourcePath(String url, String resource) {
        Uri uri = Uri.parse(url);
        String path = uri.getPath();
        return "https".equals(uri.getScheme()) && "comicvine.gamespot.com".equals(uri.getHost()) && path != null
                && path.matches("/api/" + resource + "/[0-9]+-[0-9]+/") ? path.substring(5) : null;
    }
    public static String website(String url) {
        Uri uri = Uri.parse(url);
        return "https".equals(uri.getScheme()) && "comicvine.gamespot.com".equals(uri.getHost())
                && uri.getQuery() == null && uri.getUserInfo() == null ? url : "";
    }
    private static String image(JSONObject object) {
        JSONObject image = object.optJSONObject("image");
        return image == null ? "" : text(image, "medium_url");
    }
    private void publisher(Callback<Publisher> callback) {
        request("publishers/", params("filter", "name:Marvel", "limit", "100", "field_list", "id,name,api_detail_url"), DAY, result -> {
            if (result.failure != null) { callback.complete(Result.failed(result.failure)); return; }
            JSONArray rows = result.data.optJSONArray("results");
            JSONObject selected = null;
            if (rows != null) for (int i = 0; i < rows.length(); i++) {
                JSONObject item = rows.optJSONObject(i);
                if (item != null && "Marvel".equals(text(item, "name"))) {
                    if (selected != null) { callback.complete(Result.failed(Failure.DATA)); return; }
                    selected = item;
                }
            }
            int id = selected == null ? 0 : selected.optInt("id");
            String path = selected == null ? null : resourcePath(text(selected, "api_detail_url"), "publisher");
            if (id <= 0 || path == null || !path.endsWith("-" + id + "/")) {
                callback.complete(Result.failed(Failure.DATA)); return;
            }
            Publisher identity = new Publisher(id, path);
            request(path, params("field_list", "id,name"), DAY, detail -> {
                JSONObject row = detail.data == null ? null : detail.data.optJSONObject("results");
                callback.complete(row != null && row.optInt("id") == id && "Marvel".equals(text(row, "name"))
                        ? Result.success(identity) : Result.failed(detail.failure == null ? Failure.DATA : detail.failure));
            });
        });
    }

    private void index(Publisher publisher, String field, Callback<List<Reference>> callback) {
        request(publisher.path, params("field_list", "id,name," + field), DAY, result -> {
            JSONObject row = result.data == null ? null : result.data.optJSONObject("results");
            JSONArray refs = row == null ? null : row.optJSONArray(field);
            if (refs == null || row.optInt("id") != publisher.id || !"Marvel".equals(text(row, "name"))) {
                callback.complete(Result.failed(result.failure == null ? Failure.DATA : result.failure)); return;
            }
            String resource = field.equals("characters") ? "character" : field.equals("teams") ? "team" : "volume";
            Map<Integer, Reference> unique = new HashMap<>();
            for (int i = 0; i < refs.length(); i++) {
                JSONObject ref = refs.optJSONObject(i);
                if (ref == null) continue;
                int id = ref.optInt("id"); String name = text(ref, "name");
                String path = resourcePath(text(ref, "api_detail_url"), resource);
                if (id > 0 && !name.isEmpty() && path != null && path.endsWith("-" + id + "/"))
                    unique.put(id, new Reference(id, name, path));
            }
            List<Reference> ordered = new ArrayList<>(unique.values());
            ordered.sort(Comparator.comparing((Reference ref) -> ref.name.toLowerCase(Locale.ROOT)).thenComparingInt(ref -> ref.id));
            callback.complete(Result.success(ordered));
        });
    }

    private static CatalogModels.Character character(JSONObject row, int publisherId) {
        JSONObject pub = row.optJSONObject("publisher"), origin = row.optJSONObject("origin");
        if (pub == null || pub.optInt("id") != publisherId || row.optInt("id") <= 0 || text(row, "name").isEmpty()) return null;
        return new CatalogModels.Character(row.optInt("id"), publisherId, text(row, "name"), text(row, "real_name"),
                origin == null ? 0 : origin.optInt("id"), text(origin, "name"), row.optInt("gender"),
                text(row, "deck"), image(row), website(text(row, "site_detail_url")));
    }

    public void featured(Callback<CatalogModels.Character> callback) {
        publisher(pub -> {
            if (pub.failure != null) { deliver(callback, Result.failed(pub.failure)); return; }
            request("characters/", params("filter", "name:Spider-Man", "limit", "100", "field_list", CHARACTER_FIELDS), DAY, result -> {
                JSONArray rows = result.data == null ? null : result.data.optJSONArray("results");
                CatalogModels.Character selected = null;
                if (rows != null) for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.optJSONObject(i);
                    CatalogModels.Character item = row == null ? null : character(row, pub.data.id);
                    if (item != null && item.name.equals("Spider-Man")) {
                        if (selected != null) { deliver(callback, Result.failed(Failure.DATA)); return; }
                        selected = item;
                    }
                }
                deliver(callback, selected == null ? Result.failed(result.failure == null ? Failure.DATA : result.failure) : Result.success(selected));
            });
        });
    }

    public void origins(Callback<List<Reference>> callback) {
        request("origins/", params("limit", "100", "field_list", "id,name"), DAY, result -> {
            JSONArray rows = result.data == null ? null : result.data.optJSONArray("results");
            if (rows == null) { deliver(callback, Result.failed(result.failure == null ? Failure.DATA : result.failure)); return; }
            List<Reference> refs = new ArrayList<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row != null && row.optInt("id") > 0 && !text(row, "name").isEmpty()) refs.add(new Reference(row.optInt("id"), text(row, "name"), ""));
            }
            deliver(callback, Result.success(refs));
        });
    }

    public void teams(Callback<List<Reference>> callback) {
        publisher(pub -> {
            if (pub.failure != null) { deliver(callback, Result.failed(pub.failure)); return; }
            index(pub.data, "teams", result -> deliver(callback, result));
        });
    }

    public void characters(String query, int originId, int gender, int teamId, int offset, Callback<Page> callback) {
        String search = Normalizer.normalize(query, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
        publisher(pub -> {
            if (pub.failure != null) { deliver(callback, Result.failed(pub.failure)); return; }
            index(pub.data, "characters", result -> {
                if (result.failure != null) { deliver(callback, Result.failed(result.failure)); return; }
                List<Reference> refs = new ArrayList<>();
                for (Reference ref : result.data) {
                    String name = Normalizer.normalize(ref.name, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
                    if (name.contains(search)) refs.add(ref);
                }
                if (search.isEmpty()) refs.sort(Comparator.comparingInt((Reference ref) -> ref.name.equals("Spider-Man") ? 0 : 1)
                        .thenComparing(ref -> ref.name.toLowerCase(Locale.ROOT)).thenComparingInt(ref -> ref.id));
                if (teamId == 0) {
                    page(pub.data, refs, originId, gender, Math.max(0, offset), 0, new ArrayList<>(), callback); return;
                }
                index(pub.data, "teams", teams -> {
                    if (teams.failure != null) { deliver(callback, Result.failed(teams.failure)); return; }
                    Reference selected = null;
                    for (Reference team : teams.data) if (team.id == teamId) selected = team;
                    if (selected == null) { deliver(callback, Result.failed(Failure.DATA)); return; }
                    request(selected.path, params("field_list", "id,name,publisher,characters"), DAY, membership -> {
                        JSONObject team = membership.data == null ? null : membership.data.optJSONObject("results");
                        JSONObject owner = team == null ? null : team.optJSONObject("publisher");
                        JSONArray characters = team == null ? null : team.optJSONArray("characters");
                        if (team == null || team.optInt("id") != teamId || owner == null || owner.optInt("id") != pub.data.id || characters == null) {
                            deliver(callback, Result.failed(membership.failure == null ? Failure.DATA : membership.failure)); return;
                        }
                        Set<Integer> members = new HashSet<>();
                        for (int i = 0; i < characters.length(); i++) {
                            JSONObject member = characters.optJSONObject(i);
                            if (member != null && member.optInt("id") > 0) members.add(member.optInt("id"));
                        }
                        refs.removeIf(ref -> !members.contains(ref.id));
                        page(pub.data, refs, originId, gender, Math.max(0, offset), 0, new ArrayList<>(), callback);
                    });
                });
            });
        });
    }

    private void page(Publisher publisher, List<Reference> refs, int origin, int gender, int offset, int attempt,
                      List<CatalogModels.Character> matches, Callback<Page> callback) {
        if (offset >= refs.size() || attempt >= 3 || matches.size() >= 12) {
            deliver(callback, Result.success(new Page(matches, offset, offset < refs.size()))); return;
        }
        List<Reference> selected = refs.subList(offset, Math.min(offset + 32, refs.size()));
        StringBuilder ids = new StringBuilder(); Set<Integer> requested = new HashSet<>();
        for (Reference ref : selected) { if (ids.length() > 0) ids.append('|'); ids.append(ref.id); requested.add(ref.id); }
        request("characters/", params("filter", "id:" + ids, "limit", "100", "field_list", CHARACTER_FIELDS), DAY, result -> {
            JSONArray rows = result.data == null ? null : result.data.optJSONArray("results");
            if (rows == null) { deliver(callback, Result.failed(result.failure == null ? Failure.DATA : result.failure)); return; }
            Map<Integer, CatalogModels.Character> parsed = new HashMap<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null || !requested.contains(row.optInt("id"))) { deliver(callback, Result.failed(Failure.DATA)); return; }
                CatalogModels.Character item = character(row, publisher.id);
                if (item != null && (origin == 0 || item.originId == origin) && (gender == 0 || item.gender == gender)) parsed.put(item.id, item);
            }
            for (Reference ref : selected) if (parsed.containsKey(ref.id)) matches.add(parsed.get(ref.id));
            page(publisher, refs, origin, gender, offset + selected.size(), attempt + 1, matches, callback);
        });
    }

    public void recent(Callback<List<Issue>> callback) {
        publisher(pub -> {
            if (pub.failure != null) { deliver(callback, Result.failed(pub.failure)); return; }
            index(pub.data, "volumes", index -> {
                if (index.failure != null) { deliver(callback, Result.failed(index.failure)); return; }
                Set<Integer> volumes = new HashSet<>(); for (Reference ref : index.data) volumes.add(ref.id);
                String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
                issues(volumes, today, 0, new LinkedHashMap<>(), callback);
            });
        });
    }

    private void issues(Set<Integer> volumes, String today, int page, Map<Integer, Issue> matches, Callback<List<Issue>> callback) {
        if (page >= 5 || matches.size() >= 6) {
            List<Issue> result = new ArrayList<>(matches.values());
            result.sort(Comparator.comparing((Issue issue) -> issue.publicationDate).reversed().thenComparingInt(issue -> issue.id));
            deliver(callback, Result.success(result.subList(0, Math.min(6, result.size())))); return;
        }
        request("issues/", params("limit", "100", "offset", String.valueOf(page * 100), "sort", "store_date:desc",
                "filter", "store_date:1900-01-01|" + today, "field_list", ISSUE_FIELDS), 900_000L, result -> {
            JSONArray rows = result.data == null ? null : result.data.optJSONArray("results");
            if (rows == null) {
                if (!matches.isEmpty()) { issues(volumes, today, 5, matches, callback); return; }
                deliver(callback, Result.failed(result.failure == null ? Failure.DATA : result.failure)); return;
            }
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                JSONObject volume = row.optJSONObject("volume");
                String date = text(row, "store_date");
                if (volume == null || !volumes.contains(volume.optInt("id")) || !date.matches("\\d{4}-\\d{2}-\\d{2}") || date.compareTo(today) > 0) continue;
                int id = row.optInt("id"); String name = text(volume, "name"), number = text(row, "issue_number");
                if (id > 0 && !name.isEmpty()) matches.put(id, new Issue(id, volume.optInt("id"),
                        name + (number.isEmpty() ? "" : " #" + number), name, date, image(row), website(text(row, "site_detail_url"))));
            }
            boolean exhausted = rows.length() == 0 || (page + 1) * 100 >= result.data.optInt("number_of_total_results");
            issues(volumes, today, exhausted ? 5 : page + 1, matches, callback);
        });
    }
}
