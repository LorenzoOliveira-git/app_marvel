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
    private static final String CHARACTER_FIELDS = "id,name,real_name,publisher,origin,gender,image,deck,site_detail_url,aliases";
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
            String resource = field.equals("characters") ? "character" : field.equals("teams") ? "team" : field.equals("story_arcs") ? "story_arc" : "volume";
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
                text(row, "deck"), image(row), website(text(row, "site_detail_url")), text(row, "aliases"));
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

    private static List<Reference> references(JSONArray rows, String resource) {
        Map<Integer, Reference> unique = new LinkedHashMap<>();
        if (rows != null) for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i); if (row == null) continue;
            int id = row.optInt("id"); String path = resourcePath(text(row, "api_detail_url"), resource);
            if (id > 0 && path != null && path.endsWith("-" + id + "/") && !text(row, "name").isEmpty())
                unique.put(id, new Reference(id, text(row, "name"), path));
        }
        return new ArrayList<>(unique.values());
    }

    public void details(int characterId, Callback<CharacterDetails> callback) {
        if (characterId <= 0) { deliver(callback, Result.failed(Failure.DATA)); return; }
        publisher(pub -> {
            if (pub.failure != null) { deliver(callback, Result.failed(pub.failure)); return; }
            index(pub.data, "characters", indexed -> {
                if (indexed.failure != null) { deliver(callback, Result.failed(indexed.failure)); return; }
                Reference selected = null;
                for (Reference ref : indexed.data) if (ref.id == characterId) selected = ref;
                if (selected == null) { deliver(callback, Result.failed(Failure.DATA)); return; }
                String fields = CHARACTER_FIELDS + ",powers,teams,character_friends,character_enemies,first_appeared_in_issue,count_of_issue_appearances";
                request(selected.path, params("field_list", fields), DAY, result -> {
                    JSONObject row = result.data == null ? null : result.data.optJSONObject("results");
                    CatalogModels.Character item = row == null ? null : character(row, pub.data.id);
                    if (item == null || item.id != characterId) {
                        deliver(callback, Result.failed(result.failure == null ? Failure.DATA : result.failure)); return;
                    }
                    JSONObject first = row.optJSONObject("first_appeared_in_issue");
                    Reference firstRef = null;
                    if (first != null) {
                        int id = first.optInt("id");
                        // A API real usa o alias first_appeared_in_issue no vínculo; a consulta usa issues/id.
                        String path = resourcePath(text(first, "api_detail_url"), "first_appeared_in_issue");
                        if (path == null) path = resourcePath(text(first, "api_detail_url"), "issue");
                        if (id > 0 && path != null && path.endsWith("-" + id + "/")) firstRef = new Reference(id, text(first, "name"), path);
                    }
                    deliver(callback, Result.success(new CharacterDetails(item,
                            row.has("count_of_issue_appearances") && !row.isNull("count_of_issue_appearances") ? row.optInt("count_of_issue_appearances", -1) : -1,
                            firstRef, references(row.optJSONArray("powers"), "power"), references(row.optJSONArray("teams"), "team"),
                            references(row.optJSONArray("character_friends"), "character"), references(row.optJSONArray("character_enemies"), "character"))));
                });
            });
        });
    }

    public void relations(CharacterDetails details, String kind, int offset, Callback<RelationPage> callback) {
        List<Reference> refs = details.relations(kind);
        if (refs.isEmpty()) { deliver(callback, Result.success(new RelationPage(Collections.emptyList(), 0, false))); return; }
        publisher(pub -> {
            if (pub.failure != null) { deliver(callback, Result.failed(pub.failure)); return; }
            if (pub.data.id != details.character.publisherId) { deliver(callback, Result.failed(Failure.DATA)); return; }
            index(pub.data, kind.equals("teams") ? "teams" : "characters", indexed -> {
                if (indexed.failure != null) { deliver(callback, Result.failed(indexed.failure)); return; }
                Map<Integer, Reference> canonical = new HashMap<>();
                for (Reference ref : indexed.data) canonical.put(ref.id, ref);
                List<Reference> verified = new ArrayList<>();
                for (Reference ref : refs) {
                    Reference known = canonical.get(ref.id);
                    if (known != null && known.path.equals(ref.path)) verified.add(known);
                }
                relationPage(pub.data, kind, verified, Math.max(0, offset), 0, new ArrayList<>(), callback);
            });
        });
    }
    public void arcs(String query, boolean descending, int offset, Callback<ArcsPage> callback) {
        if (offset < 0 || query == null || query.length() > 200) { deliver(callback,Result.failed(Failure.DATA)); return; }
        String search=searchText(query);
        publisher(pub -> {
            if (pub.failure != null) { deliver(callback,Result.failed(pub.failure)); return; }
            index(pub.data,"story_arcs",indexed -> {
                if (indexed.failure != null) { deliver(callback,Result.failed(indexed.failure)); return; }
                List<Reference> refs=new ArrayList<>();
                for (Reference ref:indexed.data) if (searchText(ref.name).contains(search)) refs.add(ref);
                if (descending) Collections.reverse(refs);
                arcsPage(pub.data,refs,offset,0,new ArrayList<>(),callback);
            });
        });
    }
    private static String searchText(String value) {
        return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).trim();
    }
    private void arcsPage(Publisher publisher,List<Reference> refs,int offset,int attempt,List<StoryArc> items,Callback<ArcsPage> callback) {
        if (offset >= refs.size() || attempt >= 3 || !items.isEmpty()) {
            deliver(callback,Result.success(new ArcsPage(items,offset,offset < refs.size()))); return;
        }
        List<Reference> batch=refs.subList(offset,Math.min(offset+12,refs.size()));
        Map<Integer,Reference> requested=new HashMap<>(); StringBuilder ids=new StringBuilder();
        for (Reference ref:batch) { requested.put(ref.id,ref); if (ids.length()>0) ids.append('|'); ids.append(ref.id); }
        request("story_arcs/",params("filter","id:"+ids,"limit","100","field_list","id,name,api_detail_url,publisher,image,site_detail_url"),DAY,result -> {
            JSONArray rows=result.data==null ? null:result.data.optJSONArray("results");
            if (rows==null) { deliver(callback,Result.failed(result.failure==null ? Failure.DATA:result.failure)); return; }
            Map<Integer,StoryArc> parsed=new HashMap<>(); Set<Integer> received=new HashSet<>();
            for (int i=0;i<rows.length();i++) {
                JSONObject row=rows.optJSONObject(i); if (row==null) { deliver(callback,Result.failed(Failure.DATA)); return; }
                int id=row.optInt("id"); Reference known=requested.get(id);
                if (known==null || !received.add(id) || !known.path.equals(resourcePath(text(row,"api_detail_url"),"story_arc"))) {
                    deliver(callback,Result.failed(Failure.DATA)); return;
                }
                JSONObject owner=row.optJSONObject("publisher");
                if (owner==null || owner.optInt("id")!=publisher.id || !publisher.path.equals(resourcePath(text(owner,"api_detail_url"),"publisher"))) continue;
                if (!known.name.equals(text(row,"name"))) { deliver(callback,Result.failed(Failure.DATA)); return; }
                String url=website(text(row,"site_detail_url"));
                if (!url.isEmpty() && !Uri.parse(url).getPath().endsWith("/"+known.path.split("/")[1]+"/")) url="";
                parsed.put(id,new StoryArc(id,publisher.id,text(row,"name"),image(row),url));
            }
            if (received.size()!=requested.size()) { deliver(callback,Result.failed(Failure.DATA)); return; }
            for (Reference ref:batch) if (parsed.containsKey(ref.id)) items.add(parsed.get(ref.id));
            arcsPage(publisher,refs,offset+batch.size(),attempt+1,items,callback);
        });
    }

    public void arcDetails(int arcId, Callback<ArcDetails> callback) {
        if (arcId<=0) { deliver(callback,Result.failed(Failure.DATA)); return; }
        publisher(pub -> {
            if (pub.failure!=null) { deliver(callback,Result.failed(pub.failure)); return; }
            index(pub.data,"story_arcs",indexed -> {
                if (indexed.failure!=null) { deliver(callback,Result.failed(indexed.failure)); return; }
                Reference known=null;
                for (Reference ref:indexed.data) if (ref.id==arcId) { known=ref; break; }
                if (known==null) { deliver(callback,Result.failed(Failure.DATA)); return; }
                Reference canonical=known;
                request(canonical.path,params("field_list","id,name,api_detail_url,publisher,image,site_detail_url,aliases,deck,description,issues"),DAY,result -> {
                    JSONObject row=result.data==null ? null:result.data.optJSONObject("results");
                    JSONObject owner=row==null ? null:row.optJSONObject("publisher");
                    if (row==null || row.optInt("id")!=arcId || !canonical.path.equals(resourcePath(text(row,"api_detail_url"),"story_arc"))
                            || text(row,"name").isEmpty() || owner==null || owner.optInt("id")!=pub.data.id
                            || !pub.data.path.equals(resourcePath(text(owner,"api_detail_url"),"publisher"))) {
                        deliver(callback,Result.failed(result.failure==null ? Failure.DATA:result.failure)); return;
                    }
                    // Nomes de edições podem ser nulos; identidade exige ID + caminho, não título.
                    Map<Integer,Reference> refs=new LinkedHashMap<>(); JSONArray issues=row.optJSONArray("issues");
                    if (issues!=null) for (int i=0;i<issues.length();i++) {
                        JSONObject issue=issues.optJSONObject(i); if (issue==null) { deliver(callback,Result.failed(Failure.DATA)); return; }
                        int id=issue.optInt("id"); String path=resourcePath(text(issue,"api_detail_url"),"issue");
                        if (id<=0 || path==null || !path.endsWith("-"+id+"/")) { deliver(callback,Result.failed(Failure.DATA)); return; }
                        refs.putIfAbsent(id,new Reference(id,text(issue,"name"),path));
                    }
                    String url=website(text(row,"site_detail_url"));
                    if (!url.endsWith("/4045-"+arcId+"/")) url="";
                    StoryArc arc=new StoryArc(arcId,pub.data.id,text(row,"name"),image(row),url);
                    deliver(callback,Result.success(new ArcDetails(arc,text(row,"aliases"),text(row,"deck"),text(row,"description"),new ArrayList<>(refs.values()))));
                });
            });
        });
    }
    public void arcIssues(ArcDetails details,int offset,Callback<AppearancePage> callback) {
        if (offset<0 || offset>details.issues.size()) { deliver(callback,Result.failed(Failure.DATA)); return; }
        publisher(pub -> {
            if (pub.failure!=null) { deliver(callback,Result.failed(pub.failure)); return; }
            if (details.arc.publisherId!=pub.data.id) { deliver(callback,Result.failed(Failure.DATA)); return; }
            index(pub.data,"volumes",indexed -> {
                if (indexed.failure!=null) { deliver(callback,Result.failed(indexed.failure)); return; }
                Map<Integer,Reference> volumes=new HashMap<>(); for (Reference ref:indexed.data) volumes.put(ref.id,ref);
                arcIssuePage(pub.data,details.issues,volumes,offset,0,callback);
            });
        });
    }
    private void arcIssuePage(Publisher publisher,List<Reference> refs,Map<Integer,Reference> volumes,int offset,int attempt,Callback<AppearancePage> callback) {
        if (offset>=refs.size() || attempt>=3) { deliver(callback,Result.success(new AppearancePage(Collections.emptyList(),offset,offset<refs.size()))); return; }
        List<Reference> batch=refs.subList(offset,Math.min(offset+12,refs.size()));
        Map<Integer,Reference> requested=new LinkedHashMap<>(); StringBuilder ids=new StringBuilder();
        for (Reference ref:batch) { if (ids.length()>0) ids.append('|'); ids.append(ref.id); requested.put(ref.id,ref); }
        request("issues/",params("filter","id:"+ids,"limit","100","field_list","id,api_detail_url,issue_number,volume,image,cover_date,site_detail_url"),DAY,result -> {
            JSONArray rows=result.data==null ? null:result.data.optJSONArray("results");
            if (rows==null) { deliver(callback,Result.failed(result.failure==null ? Failure.DATA:result.failure)); return; }
            Map<Integer,JSONObject> found=new HashMap<>(); Map<Integer,Reference> owners=new LinkedHashMap<>();
            for (int i=0;i<rows.length();i++) {
                JSONObject row=rows.optJSONObject(i); int id=row==null ? 0:row.optInt("id"); Reference ref=requested.get(id);
                if (ref==null || found.containsKey(id) || !ref.path.equals(resourcePath(text(row,"api_detail_url"),"issue"))) { deliver(callback,Result.failed(Failure.DATA)); return; }
                found.put(id,row); JSONObject volume=row.optJSONObject("volume"); Reference canonical=volume==null ? null:volumes.get(volume.optInt("id"));
                if (canonical!=null && canonical.path.equals(resourcePath(text(volume,"api_detail_url"),"volume"))) owners.put(canonical.id,canonical);
            }
            if (!found.keySet().equals(requested.keySet())) { deliver(callback,Result.failed(Failure.DATA)); return; }
            if (owners.isEmpty()) { arcIssuePage(publisher,refs,volumes,offset+batch.size(),attempt+1,callback); return; }
            StringBuilder volumeIds=new StringBuilder(); for (int id:owners.keySet()) { if (volumeIds.length()>0) volumeIds.append('|'); volumeIds.append(id); }
            request("volumes/",params("filter","id:"+volumeIds,"limit","100","field_list","id,name,api_detail_url,publisher"),DAY,owned -> {
                JSONArray volumeRows=owned.data==null ? null:owned.data.optJSONArray("results");
                if (volumeRows==null) { deliver(callback,Result.failed(owned.failure==null ? Failure.DATA:owned.failure)); return; }
                Map<Integer,String> eligible=new HashMap<>(); Set<Integer> received=new HashSet<>();
                for (int i=0;i<volumeRows.length();i++) {
                    JSONObject volume=volumeRows.optJSONObject(i); int id=volume==null ? 0:volume.optInt("id"); Reference known=owners.get(id);
                    if (known==null || !received.add(id) || !known.path.equals(resourcePath(text(volume,"api_detail_url"),"volume"))) { deliver(callback,Result.failed(Failure.DATA)); return; }
                    JSONObject owner=volume.optJSONObject("publisher");
                    if (owner!=null && owner.optInt("id")==publisher.id && publisher.path.equals(resourcePath(text(owner,"api_detail_url"),"publisher")) && !text(volume,"name").isEmpty()) eligible.put(id,text(volume,"name"));
                }
                if (!received.equals(owners.keySet())) { deliver(callback,Result.failed(Failure.DATA)); return; }
                List<Issue> items=new ArrayList<>();
                for (Reference ref:batch) {
                    JSONObject row=found.get(ref.id),volume=row.optJSONObject("volume"); int volumeId=volume==null ? 0:volume.optInt("id"); String name=eligible.get(volumeId);
                    if (name==null) continue;
                    String number=text(row,"issue_number"),url=website(text(row,"site_detail_url")); if (!url.endsWith("/4000-"+ref.id+"/")) url="";
                    items.add(new Issue(ref.id,volumeId,name+(number.isEmpty() ? "":" #"+number),name,text(row,"cover_date"),image(row),url));
                }
                int next=offset+batch.size();
                if (items.isEmpty()) arcIssuePage(publisher,refs,volumes,next,attempt+1,callback);
                else deliver(callback,Result.success(new AppearancePage(items,next,next<refs.size())));
            });
        });
    }

    public void issueRelations(IssueDetails details, String kind, int offset, Callback<RelationPage> callback) {
        if (offset < 0 || !(kind.equals("characters") || kind.equals("teams"))) {
            deliver(callback, Result.failed(Failure.DATA)); return;
        }
        publisher(pub -> {
            if (pub.failure != null) { deliver(callback, Result.failed(pub.failure)); return; }
            if (pub.data.id != details.publisherId) { deliver(callback, Result.failed(Failure.DATA)); return; }
            index(pub.data, kind, indexed -> {
                if (indexed.failure != null) { deliver(callback, Result.failed(indexed.failure)); return; }
                Map<Integer, Reference> canonical = new HashMap<>();
                for (Reference ref : indexed.data) canonical.put(ref.id, ref);
                List<Reference> verified = new ArrayList<>();
                for (Reference ref : details.relations(kind)) {
                    Reference known = canonical.get(ref.id);
                    if (known != null && known.path.equals(ref.path)) verified.add(known);
                }
                relationPage(pub.data, kind, verified, offset, 0, new ArrayList<>(), callback);
            });
        });
    }

    public void issueDetails(int issueId, Callback<IssueDetails> callback) {
        if (issueId <= 0) { deliver(callback, Result.failed(Failure.DATA)); return; }
        publisher(pub -> {
            if (pub.failure != null) { deliver(callback, Result.failed(pub.failure)); return; }
            index(pub.data, "volumes", indexed -> {
                if (indexed.failure != null) { deliver(callback, Result.failed(indexed.failure)); return; }
                Map<Integer, Reference> canonical = new HashMap<>();
                for (Reference ref : indexed.data) canonical.put(ref.id, ref);
                request("issues/", params("filter", "id:" + issueId, "limit", "100", "field_list", "id,api_detail_url,volume"), DAY, lookup -> {
                    JSONArray rows = lookup.data == null ? null : lookup.data.optJSONArray("results");
                    JSONObject ref = rows == null || rows.length() != 1 ? null : rows.optJSONObject(0);
                    String path = ref == null ? null : resourcePath(text(ref, "api_detail_url"), "issue");
                    JSONObject sourceVolume = ref == null ? null : ref.optJSONObject("volume");
                    if (ref == null || ref.optInt("id") != issueId || path == null || !path.endsWith("-" + issueId + "/")
                            || sourceVolume == null || !canonical.containsKey(sourceVolume.optInt("id"))) {
                        deliver(callback, Result.failed(lookup.failure == null ? Failure.DATA : lookup.failure)); return;
                    }
                    String fields = ISSUE_FIELDS + ",api_detail_url,cover_date,deck,description,character_credits,team_credits,person_credits,story_arc_credits,location_credits,object_credits,concept_credits";
                    request(path, params("field_list", fields), DAY, result -> {
                        JSONObject row = result.data == null ? null : result.data.optJSONObject("results");
                        JSONObject volume = row == null ? null : row.optJSONObject("volume");
                        Reference known = volume == null ? null : canonical.get(volume.optInt("id"));
                        if (row == null || row.optInt("id") != issueId || !path.equals(resourcePath(text(row,"api_detail_url"),"issue"))
                                || known == null || known.id != sourceVolume.optInt("id")
                                || !known.path.equals(resourcePath(text(volume,"api_detail_url"),"volume"))) {
                            deliver(callback, Result.failed(result.failure == null ? Failure.DATA : result.failure)); return;
                        }
                        request(known.path, params("field_list", "id,name,publisher,site_detail_url"), DAY, owner -> {
                            JSONObject volumeRow = owner.data == null ? null : owner.data.optJSONObject("results");
                            JSONObject publisher = volumeRow == null ? null : volumeRow.optJSONObject("publisher");
                            if (volumeRow == null || volumeRow.optInt("id") != known.id || text(volumeRow,"name").isEmpty()
                                    || publisher == null || publisher.optInt("id") != pub.data.id) {
                                deliver(callback, Result.failed(owner.failure == null ? Failure.DATA : owner.failure)); return;
                            }
                            String name = text(volumeRow,"name"), number = text(row,"issue_number");
                            Issue item = new Issue(issueId, known.id, name + (number.isEmpty() ? "" : " #" + number), name,
                                    text(row,"store_date"), image(row), website(text(row,"site_detail_url")));
                            deliver(callback, Result.success(new IssueDetails(item, pub.data.id, text(row,"name"), text(row,"cover_date"),
                                    text(row,"deck"), text(row,"description"), website(text(volumeRow,"site_detail_url")),
                                    references(row.optJSONArray("character_credits"),"character"), references(row.optJSONArray("team_credits"),"team"),
                                    credits(row.optJSONArray("person_credits"),"person"), credits(row.optJSONArray("story_arc_credits"),"story_arc"),
                                    credits(row.optJSONArray("location_credits"),"location"), credits(row.optJSONArray("object_credits"),"object"),
                                    credits(row.optJSONArray("concept_credits"),"concept"))));
                        });
                    });
                });
            });
        });
    }
    private static List<Credit> credits(JSONArray rows, String resource) {
        Map<Integer, Credit> result = new LinkedHashMap<>();
        if (rows != null) for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i); if (row == null) continue;
            int id = row.optInt("id"); String path = resourcePath(text(row,"api_detail_url"),resource), name = text(row,"name");
            String url = website(text(row,"site_detail_url"));
            if (id <= 0 || name.isEmpty() || path == null || !path.endsWith("-" + id + "/")) continue;
            if (!url.isEmpty() && !Uri.parse(url).getPath().endsWith("/" + path.split("/")[1] + "/")) url = "";
            result.put(id,new Credit(new Reference(id,name,path),text(row,"role"),url));
        }
        return new ArrayList<>(result.values());
    }

    private void relationPage(Publisher publisher, String kind, List<Reference> refs, int offset, int attempt,
            List<RelatedItem> items, Callback<RelationPage> callback) {
        if (offset >= refs.size() || attempt >= 3 || !items.isEmpty()) {
            deliver(callback, Result.success(new RelationPage(items, offset, offset < refs.size()))); return;
        }
        List<Reference> batch = refs.subList(offset, Math.min(offset + 24, refs.size()));
        StringBuilder ids = new StringBuilder(); Set<Integer> requested = new HashSet<>();
        for (Reference ref : batch) { if (ids.length() > 0) ids.append('|'); ids.append(ref.id); requested.add(ref.id); }
        request(kind.equals("teams") ? "teams/" : "characters/", params("filter", "id:" + ids, "limit", "100",
                "field_list", "id,name,publisher,image,site_detail_url"), DAY, result -> {
            JSONArray rows = result.data == null ? null : result.data.optJSONArray("results");
            if (rows == null) { deliver(callback, Result.failed(result.failure == null ? Failure.DATA : result.failure)); return; }
            Map<Integer, RelatedItem> parsed = new HashMap<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i); if (row == null) continue;
                int id = row.optInt("id");
                if (!requested.contains(id)) { deliver(callback, Result.failed(Failure.DATA)); return; }
                JSONObject owner = row.optJSONObject("publisher");
                if (owner != null && owner.optInt("id") == publisher.id && !text(row, "name").isEmpty())
                    parsed.put(id, new RelatedItem(id, publisher.id, text(row, "name"), image(row), website(text(row, "site_detail_url"))));
            }
            for (Reference ref : batch) if (parsed.containsKey(ref.id)) items.add(parsed.get(ref.id));
            relationPage(publisher, kind, refs, offset + batch.size(), attempt + 1, items, callback);
        });
    }

    public void firstAppearance(CharacterDetails details, Callback<List<Issue>> callback) {
        if (details.firstAppearance == null) { deliver(callback, Result.success(Collections.emptyList())); return; }
        publisher(pub -> {
            if (pub.failure != null) { deliver(callback, Result.failed(pub.failure)); return; }
            if (pub.data.id != details.character.publisherId) { deliver(callback, Result.failed(Failure.DATA)); return; }
            index(pub.data, "volumes", indexed -> {
                if (indexed.failure != null) { deliver(callback, Result.failed(indexed.failure)); return; }
                Set<Integer> volumes = new HashSet<>(); for (Reference ref : indexed.data) volumes.add(ref.id);
                request("issues/", params("filter", "id:" + details.firstAppearance.id, "limit", "100",
                        "field_list", "id,name,issue_number,volume,image,cover_date,site_detail_url"), DAY, result -> {
                    JSONArray rows = result.data == null ? null : result.data.optJSONArray("results");
                    if (rows == null) { deliver(callback, Result.failed(result.failure == null ? Failure.DATA : result.failure)); return; }
                    List<Issue> found = new ArrayList<>();
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject row = rows.optJSONObject(i); if (row == null || row.optInt("id") != details.firstAppearance.id) continue;
                        JSONObject volume = row.optJSONObject("volume");
                        if (volume == null || !volumes.contains(volume.optInt("id")) || text(volume, "name").isEmpty()) continue;
                        String number = text(row, "issue_number"), name = text(volume, "name");
                        found.add(new Issue(row.optInt("id"), volume.optInt("id"), name + (number.isEmpty() ? "" : " #" + number),
                                name, text(row, "cover_date"), image(row), website(text(row, "site_detail_url"))));
                    }
                    deliver(callback, Result.success(found));
                });
            });
        });
    }

    /** Somente sob demanda na tela de história; o filtro issues/characters é ignorado pela API. */
    public void appearanceIndex(CharacterDetails details, Callback<AppearanceIndex> callback) {
        publisher(pub -> {
            if (pub.failure != null) { deliver(callback, Result.failed(pub.failure)); return; }
            if (pub.data.id != details.character.publisherId) { deliver(callback, Result.failed(Failure.DATA)); return; }
            index(pub.data, "characters", indexed -> {
                if (indexed.failure != null) { deliver(callback, Result.failed(indexed.failure)); return; }
                Reference selected = null;
                for (Reference ref : indexed.data) if (ref.id == details.character.id) selected = ref;
                if (selected == null) { deliver(callback, Result.failed(Failure.DATA)); return; }
                request(selected.path, params("field_list", "id,name,publisher,issue_credits"), DAY, result -> {
                    JSONObject row = result.data == null ? null : result.data.optJSONObject("results");
                    JSONObject owner = row == null ? null : row.optJSONObject("publisher");
                    JSONArray refs = row == null ? null : row.optJSONArray("issue_credits");
                    if (row == null || row.optInt("id") != details.character.id || !details.character.name.equals(text(row, "name"))
                            || owner == null || owner.optInt("id") != pub.data.id || refs == null) {
                        deliver(callback, Result.failed(result.failure == null ? Failure.DATA : result.failure)); return;
                    }
                    Map<Integer, Reference> unique = new LinkedHashMap<>();
                    for (int i = 0; i < refs.length(); i++) {
                        JSONObject ref = refs.optJSONObject(i); if (ref == null) continue;
                        int id = ref.optInt("id"); String path = resourcePath(text(ref, "api_detail_url"), "issue");
                        // Os nomes de muitos vínculos são nulos; o título é obtido da edição/volume.
                        if (id > 0 && path != null && path.endsWith("-" + id + "/"))
                            unique.putIfAbsent(id, new Reference(id, text(ref, "name"), path));
                    }
                    deliver(callback, Result.success(new AppearanceIndex(details.character.id, pub.data.id, new ArrayList<>(unique.values()))));
                });
            });
        });
    }
    public void appearances(AppearanceIndex appearanceIndex, int offset, Callback<AppearancePage> callback) {
        publisher(pub -> {
            if (pub.failure != null) { deliver(callback, Result.failed(pub.failure)); return; }
            if (pub.data.id != appearanceIndex.publisherId) { deliver(callback, Result.failed(Failure.DATA)); return; }
            index(pub.data, "volumes", indexed -> {
                if (indexed.failure != null) { deliver(callback, Result.failed(indexed.failure)); return; }
                Map<Integer, Reference> volumes = new HashMap<>(); for (Reference ref : indexed.data) volumes.put(ref.id, ref);
                appearancePage(appearanceIndex.issues, volumes, Math.max(0, offset), 0, new ArrayList<>(), callback);
            });
        });
    }
    private void appearancePage(List<Reference> refs, Map<Integer, Reference> volumes, int offset, int attempt,
            List<Issue> matches, Callback<AppearancePage> callback) {
        if (offset >= refs.size() || attempt >= 3 || !matches.isEmpty()) {
            deliver(callback, Result.success(new AppearancePage(matches, offset, offset < refs.size()))); return;
        }
        List<Reference> batch = refs.subList(offset, Math.min(offset + 12, refs.size()));
        StringBuilder ids = new StringBuilder(); Map<Integer, Reference> requested = new LinkedHashMap<>();
        for (Reference ref : batch) { if (ids.length() > 0) ids.append('|'); ids.append(ref.id); requested.put(ref.id, ref); }
        request("issues/", params("filter", "id:" + ids, "limit", "100", "field_list",
                "id,api_detail_url,issue_number,volume,image,cover_date,site_detail_url"), DAY, result -> {
            JSONArray rows = result.data == null ? null : result.data.optJSONArray("results");
            if (rows == null) { deliver(callback, Result.failed(result.failure == null ? Failure.DATA : result.failure)); return; }
            Map<Integer, Issue> parsed = new HashMap<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i); if (row == null) continue;
                int id = row.optInt("id"); Reference ref = requested.get(id);
                if (ref == null || !ref.path.equals(resourcePath(text(row, "api_detail_url"), "issue"))) {
                    deliver(callback, Result.failed(Failure.DATA)); return;
                }
                JSONObject volume = row.optJSONObject("volume"); Reference canonical = volume == null ? null : volumes.get(volume.optInt("id"));
                if (canonical == null || !canonical.path.equals(resourcePath(text(volume, "api_detail_url"), "volume")) || text(volume, "name").isEmpty()) continue;
                String name = text(volume, "name"), number = text(row, "issue_number");
                parsed.put(id, new Issue(id, canonical.id, name + (number.isEmpty() ? "" : " #" + number), name,
                        text(row, "cover_date"), image(row), website(text(row, "site_detail_url"))));
            }
            // Preserva a sequência do vínculo do personagem; não inventa ordem cronológica/leitura.
            for (Reference ref : batch) if (parsed.containsKey(ref.id)) matches.add(parsed.get(ref.id));
            appearancePage(refs, volumes, offset + batch.size(), attempt + 1, matches, callback);
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

    public void volumes(Callback<List<Reference>> callback) {
        publisher(pub -> {
            if (pub.failure != null) { deliver(callback, Result.failed(pub.failure)); return; }
            index(pub.data, "volumes", result -> deliver(callback, result));
        });
    }

    private static final String SERIES_FIELDS="id,name,api_detail_url,image,site_detail_url,publisher,start_year,count_of_episodes";
    private static Series series(JSONObject row,Publisher publisher) {
        if (row==null || row.optInt("id")<=0 || text(row,"name").isEmpty()) return null;
        JSONObject owner=row.optJSONObject("publisher");String path=resourcePath(text(row,"api_detail_url"),"series");int id=row.optInt("id");
        if (owner==null || owner.optInt("id")!=publisher.id || !"Marvel".equals(text(owner,"name"))
                || !publisher.path.equals(resourcePath(text(owner,"api_detail_url"),"publisher")) || path==null || !path.equals("series/4075-"+id+"/")) return null;
        String year=text(row,"start_year");if (!year.matches("[12][0-9]{3}")) year="";
        int count=row.has("count_of_episodes") && !row.isNull("count_of_episodes") ? row.optInt("count_of_episodes",-1):-1;
        String site=website(text(row,"site_detail_url"));
        if (!site.isEmpty() && !Uri.parse(site).getPath().endsWith("/4075-"+id+"/")) site="";
        return new Series(id,publisher.id,text(row,"name"),year,Math.max(-1,count),image(row),site);
    }
    public void featuredSeries(Callback<Series> callback) {
        publisher(pub -> {
            if (pub.failure!=null) { deliver(callback,Result.failed(pub.failure));return; }
            request("series_list/",params("filter","name:Agents of S.H.I.E.L.D.","field_list",SERIES_FIELDS,"limit","100"),DAY,result -> {
                JSONArray rows=result.data==null ? null:result.data.optJSONArray("results");Series found=null;
                if (rows!=null) for (int i=0;i<rows.length();i++) {
                    Series item=series(rows.optJSONObject(i),pub.data);
                    if (item!=null && item.id==1 && item.title.equals("Agents of S.H.I.E.L.D.")) { found=item;break; }
                }
                deliver(callback,found==null ? Result.failed(result.failure==null ? Failure.DATA:result.failure):Result.success(found));
            });
        });
    }
    public void series(String query,boolean descending,int offset,Callback<SeriesPage> callback) {
        String value=query.trim();
        if (offset<0 || value.length()>80 || value.matches(".*[,|:].*")) { deliver(callback,Result.failed(Failure.DATA));return; }
        publisher(pub -> {
            if (pub.failure!=null) { deliver(callback,Result.failed(pub.failure));return; }
            seriesBatch(pub.data,value,descending,offset,0,new LinkedHashMap<>(),callback);
        });
    }
    private void seriesBatch(Publisher publisher,String query,boolean descending,int offset,int batches,Map<Integer,Series> found,Callback<SeriesPage> callback) {
        Map<String,String> parameters=params("field_list",SERIES_FIELDS,"limit","100","offset",String.valueOf(offset),"sort","name:"+(descending ? "desc":"asc"));
        if (!query.isEmpty()) parameters.put("filter","name:"+query);
        request("series_list/",parameters,DAY,result -> {
            if (result.failure!=null) { deliver(callback,Result.failed(result.failure));return; }
            JSONArray rows=result.data.optJSONArray("results");int total=result.data.optInt("number_of_total_results",-1);
            if (rows==null || total<0 || rows.length()>100 || (rows.length()==0 && offset<total)) { deliver(callback,Result.failed(Failure.DATA));return; }
            for (int i=0;i<rows.length();i++) {
                Series item=series(rows.optJSONObject(i),publisher);
                if (item!=null && item.title.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) found.putIfAbsent(item.id,item);
            }
            int next=offset+rows.length();boolean more=next<total;
            // No máximo 3 lotes por ação; editora conferida em cada referência, sem detalhes por item.
            if (more && found.size()<12 && batches<2) seriesBatch(publisher,query,descending,next,batches+1,found,callback);
            else deliver(callback,Result.success(new SeriesPage(new ArrayList<>(found.values()),next,more)));
        });
    }

    public void seriesDetails(int id,Callback<SeriesDetails> callback) {
        if (id<=0) { deliver(callback,Result.failed(Failure.DATA));return; }
        publisher(pub -> {
            if (pub.failure!=null) { deliver(callback,Result.failed(pub.failure));return; }
            request("series/4075-"+id+"/",params("field_list",SERIES_FIELDS+",deck,description,characters,episodes,first_episode,last_episode"),DAY,result -> {
                JSONObject row=result.data==null ? null:result.data.optJSONObject("results");Series item=series(row,pub.data);
                if (item==null || item.id!=id) { deliver(callback,Result.failed(result.failure==null ? Failure.DATA:result.failure));return; }
                Map<Integer,Episode> unique=new LinkedHashMap<>();JSONArray refs=row.optJSONArray("episodes");
                if (refs!=null) for (int i=0;i<refs.length();i++) { Episode e=episode(refs.optJSONObject(i));if(e!=null) unique.putIfAbsent(e.id,e); }
                List<Episode> episodes=new ArrayList<>(unique.values());
                // Ordenação da numeração integral recebida; jamais extrai temporada desse código.
                episodes.sort((a,b) -> {
                    if (a.number.matches("[0-9]+") && b.number.matches("[0-9]+")) {
                        int compare=new java.math.BigInteger(a.number).compareTo(new java.math.BigInteger(b.number));if(compare!=0) return compare;
                    } else { int compare=a.number.compareToIgnoreCase(b.number);if(compare!=0) return compare; }
                    return Integer.compare(a.id,b.id);
                });
                Episode first=episode(row.optJSONObject("first_episode")),last=episode(row.optJSONObject("last_episode"));
                if(first!=null) first=unique.get(first.id);if(last!=null) last=unique.get(last.id);
                deliver(callback,Result.success(new SeriesDetails(item,text(row,"deck"),text(row,"description"),references(row.optJSONArray("characters"),"character"),episodes,first,last)));
            });
        });
    }
    private static Episode episode(JSONObject row) {
        if(row==null) return null;int id=row.optInt("id");String path=resourcePath(text(row,"api_detail_url"),"episode"),name=text(row,"name");
        if(id<=0 || name.isEmpty() || !("episode/4070-"+id+"/").equals(path)) return null;
        String site=website(text(row,"site_detail_url"));if(!site.isEmpty() && !Uri.parse(site).getPath().endsWith("/4070-"+id+"/")) site="";
        String date=text(row,"air_date");try { if(!date.isEmpty()) java.time.LocalDate.parse(date); } catch(java.time.DateTimeException invalid) { date=""; }
        return new Episode(id,name,text(row,"episode_number"),date,site,path);
    }
    public void seriesEpisodes(SeriesDetails detail,int offset,Callback<EpisodePage> callback) {
        if(offset<0 || detail.series.publisherId!=31) { deliver(callback,Result.failed(Failure.DATA));return; }
        List<Episode> refs=detail.episodes;int end=Math.min(refs.size(),offset+12);
        if(offset>=refs.size()) { deliver(callback,Result.success(new EpisodePage(Collections.emptyList(),refs.size(),false)));return; }
        Map<Integer,Episode> expected=new LinkedHashMap<>();List<String> ids=new ArrayList<>();
        for(Episode e:refs.subList(offset,end)) { expected.put(e.id,e);ids.add(String.valueOf(e.id)); }
        request("episodes/",params("filter","id:"+String.join("|",ids),"field_list","id,name,episode_number,air_date,series,api_detail_url,site_detail_url","limit","100"),DAY,result -> {
            if(result.failure!=null) { deliver(callback,Result.failed(result.failure));return; }
            JSONArray rows=result.data.optJSONArray("results");Map<Integer,Episode> found=new LinkedHashMap<>();
            if(rows==null || result.data.optInt("number_of_total_results",-1)!=expected.size() || rows.length()!=expected.size()) { deliver(callback,Result.failed(Failure.DATA));return; }
            for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.optJSONObject(i);Episode item=episode(row);JSONObject owner=row==null ? null:row.optJSONObject("series");
                Episode ref=item==null ? null:expected.get(item.id);
                if(ref==null || !ref.path.equals(item.path) || owner==null || owner.optInt("id")!=detail.series.id
                        || !("series/4075-"+detail.series.id+"/").equals(resourcePath(text(owner,"api_detail_url"),"series")) || found.putIfAbsent(item.id,item)!=null) {
                    deliver(callback,Result.failed(Failure.DATA));return;
                }
            }
            List<Episode> ordered=new ArrayList<>();for(int id:expected.keySet()) ordered.add(found.get(id));
            deliver(callback,Result.success(new EpisodePage(ordered,end,end<refs.size())));
        });
    }
    public void seriesCharacters(SeriesDetails details,int offset,Callback<RelationPage> callback) {
        if(offset<0) { deliver(callback,Result.failed(Failure.DATA));return; }
        publisher(pub -> {
            if(pub.failure!=null) { deliver(callback,Result.failed(pub.failure));return; }
            if(pub.data.id!=details.series.publisherId) { deliver(callback,Result.failed(Failure.DATA));return; }
            index(pub.data,"characters",indexed -> {
                if(indexed.failure!=null) { deliver(callback,Result.failed(indexed.failure));return; }
                Map<Integer,Reference> known=new HashMap<>();for(Reference ref:indexed.data) known.put(ref.id,ref);
                List<Reference> verified=new ArrayList<>();for(Reference ref:details.characters) { Reference canonical=known.get(ref.id);if(canonical!=null && canonical.path.equals(ref.path)) verified.add(canonical); }
                relationPage(pub.data,"characters",verified,offset,0,new ArrayList<>(),callback);
            });
        });
    }

    private static final String MOVIE_FIELDS = "id,name,image,site_detail_url,runtime,studios";
    /** Studios usa referências 4010 de editoras; o endpoint studio é inexistente. */
    private static Movie movie(JSONObject row, Publisher publisher) {
        if (row==null || row.optInt("id")<=0 || text(row,"name").isEmpty()) return null;
        JSONArray studios=row.optJSONArray("studios"); boolean verified=false;
        if (studios!=null) for (int i=0;i<studios.length();i++) {
            JSONObject studio=studios.optJSONObject(i);
            if (studio!=null && studio.optInt("id")==publisher.id && "Marvel".equals(text(studio,"name"))) {
                String path=resourcePath(text(studio,"api_detail_url"),"studio");
                verified=path!=null && path.equals(publisher.path.replace("publisher/","studio/"));
                if (verified) break;
            }
        }
        return verified ? new Movie(row.optInt("id"),publisher.id,text(row,"name"),Math.max(0,row.optInt("runtime")),
                image(row),website(text(row,"site_detail_url"))) : null;
    }
    public void movieDetails(int movieId,Callback<MovieDetails> callback) {
        if (movieId<=0) { deliver(callback,Result.failed(Failure.DATA));return; }
        publisher(pub -> {
            if (pub.failure!=null) { deliver(callback,Result.failed(pub.failure));return; }
            request("movies/",params("filter","id:"+movieId,"field_list",MOVIE_FIELDS+",api_detail_url","limit","1"),DAY,lookup -> {
                JSONArray rows=lookup.data==null ? null:lookup.data.optJSONArray("results");
                JSONObject ref=rows==null || rows.length()!=1 ? null:rows.optJSONObject(0);
                Movie verified=movie(ref,pub.data);
                String path=ref==null ? null:resourcePath(text(ref,"api_detail_url"),"movie");
                if (verified==null || verified.id!=movieId || path==null || !path.endsWith("-"+movieId+"/")) {
                    deliver(callback,Result.failed(lookup.failure==null ? Failure.DATA:lookup.failure));return;
                }
                String fields=MOVIE_FIELDS+",api_detail_url,deck,description,rating,distributor,characters,teams,producers,writers,locations,objects,concepts";
                request(path,params("field_list",fields),DAY,result -> {
                    JSONObject row=result.data==null ? null:result.data.optJSONObject("results");Movie item=movie(row,pub.data);
                    if (item==null || item.id!=movieId || !path.equals(resourcePath(text(row,"api_detail_url"),"movie"))) {
                        deliver(callback,Result.failed(result.failure==null ? Failure.DATA:result.failure));return;
                    }
                    String distributor=row.opt("distributor") instanceof String ? plain(text(row,"distributor")):"";
                    deliver(callback,Result.success(new MovieDetails(item,text(row,"rating"),distributor,text(row,"deck"),text(row,"description"),
                        references(row.optJSONArray("characters"),"character"),references(row.optJSONArray("teams"),"team"),
                        movieCredits(row.optJSONArray("studios"),"studio","publisher"),movieCredits(row.optJSONArray("producers"),"producer","person"),
                        movieCredits(row.optJSONArray("writers"),"writer","person"),credits(row.optJSONArray("locations"),"location"),
                        credits(row.optJSONArray("objects"),"object"),credits(row.optJSONArray("concepts"),"concept"))));
                });
            });
        });
    }
    private static List<Credit> movieCredits(JSONArray rows,String alias,String canonical) {
        List<Credit> result=new ArrayList<>();Set<Integer> seen=new HashSet<>();
        if (rows!=null) for (int i=0;i<rows.length();i++) {
            JSONObject row=rows.optJSONObject(i);if (row==null) continue;
            int id=row.optInt("id");String path=resourcePath(text(row,"api_detail_url"),alias),name=text(row,"name");
            if (id<=0 || name.isEmpty() || path==null || !path.endsWith("-"+id+"/") || !seen.add(id)) continue;
            String site=website(text(row,"site_detail_url"));
            if (!site.isEmpty() && !Uri.parse(site).getPath().endsWith("/"+path.split("/")[1]+"/")) site="";
            result.add(new Credit(new Reference(id,name,path.replace(alias+"/",canonical+"/")),"",site));
        }
        return result;
    }
    public void movieRelations(MovieDetails details,String kind,int offset,Callback<RelationPage> callback) {
        if (offset<0 || !(kind.equals("characters") || kind.equals("teams"))) { deliver(callback,Result.failed(Failure.DATA));return; }
        publisher(pub -> {
            if (pub.failure!=null) { deliver(callback,Result.failed(pub.failure));return; }
            if (pub.data.id!=details.movie.publisherId) { deliver(callback,Result.failed(Failure.DATA));return; }
            index(pub.data,kind,indexed -> {
                if (indexed.failure!=null) { deliver(callback,Result.failed(indexed.failure));return; }
                Map<Integer,Reference> canonical=new HashMap<>();for (Reference ref:indexed.data) canonical.put(ref.id,ref);
                List<Reference> verified=new ArrayList<>();
                for (Reference ref:details.relations(kind)) {
                    Reference known=canonical.get(ref.id);if (known!=null && known.path.equals(ref.path)) verified.add(known);
                }
                relationPage(pub.data,kind,verified,offset,0,new ArrayList<>(),callback);
            });
        });
    }

    public void featuredMovie(Callback<Movie> callback) {
        publisher(pub -> {
            if (pub.failure!=null) { deliver(callback,Result.failed(pub.failure)); return; }
            request("movies/",params("filter","id:17","field_list",MOVIE_FIELDS,"limit","1"),DAY,result -> {
                JSONArray rows=result.data==null ? null:result.data.optJSONArray("results");
                Movie item=rows==null || rows.length()!=1 ? null:movie(rows.optJSONObject(0),pub.data);
                deliver(callback,item!=null && item.id==17 ? Result.success(item):Result.failed(result.failure==null ? Failure.DATA:result.failure));
            });
        });
    }
    public void movies(String query,boolean descending,int offset,Callback<MoviesPage> callback) {
        String value=query.trim();
        if (offset<0 || value.length()>80 || value.matches(".*[,|:].*")) { deliver(callback,Result.failed(Failure.DATA)); return; }
        publisher(pub -> {
            if (pub.failure!=null) { deliver(callback,Result.failed(pub.failure)); return; }
            movieBatch(pub.data,value,descending,offset,0,new LinkedHashMap<>(),callback);
        });
    }
    private void movieBatch(Publisher publisher,String query,boolean descending,int offset,int batches,
            Map<Integer,Movie> found,Callback<MoviesPage> callback) {
        Map<String,String> parameters=params("field_list",MOVIE_FIELDS,"limit","100","offset",String.valueOf(offset),"sort","name:"+(descending ? "desc":"asc"));
        if (!query.isEmpty()) parameters.put("filter","name:"+query);
        request("movies/",parameters,DAY,result -> {
            if (result.failure!=null) { deliver(callback,Result.failed(result.failure)); return; }
            JSONArray rows=result.data.optJSONArray("results"); int total=result.data.optInt("number_of_total_results",-1);
            if (rows==null || total<0 || rows.length()>100 || (rows.length()==0 && offset<total)) {
                deliver(callback,Result.failed(Failure.DATA)); return;
            }
            for (int i=0;i<rows.length();i++) {
                Movie item=movie(rows.optJSONObject(i),publisher);
                if (item!=null && item.title.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) found.putIfAbsent(item.id,item);
            }
            int next=offset+rows.length(); boolean more=next<total;
            // No máximo três lotes por ação; nenhum detalhe por filme e nenhuma lista global indiscriminada.
            if (more && found.size()<12 && batches<2) movieBatch(publisher,query,descending,next,batches+1,found,callback);
            else deliver(callback,Result.success(new MoviesPage(new ArrayList<>(found.values()),next,more)));
        });
    }

    public void comics(int volumeId, boolean oldest, ComicsCursor cursor, Callback<ComicsPage> callback) {
        if (volumeId < 0 || cursor == null || cursor.offset < -1 || cursor.examined < 0
                || (!cursor.date.isEmpty() && !cursor.date.matches("\\d{4}-\\d{2}-\\d{2}"))) {
            deliver(callback, Result.failed(Failure.DATA)); return;
        }
        publisher(pub -> {
            if (pub.failure != null) { deliver(callback, Result.failed(pub.failure)); return; }
            index(pub.data, "volumes", indexed -> {
                if (indexed.failure != null) { deliver(callback, Result.failed(indexed.failure)); return; }
                Map<Integer, Reference> volumes = new HashMap<>();
                for (Reference ref : indexed.data) volumes.put(ref.id, ref);
                if (volumeId != 0 && !volumes.containsKey(volumeId)) { deliver(callback, Result.failed(Failure.DATA)); return; }
                String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
                comicsPage(volumes, volumeId, oldest, today, cursor, 0, callback);
            });
        });
    }
    private void comicsPage(Map<Integer, Reference> volumes, int scope, boolean oldest, String today,
            ComicsCursor cursor, int attempt, Callback<ComicsPage> callback) {
        if (cursor.offset >= 0) { comicsBucket(volumes, scope, oldest, today, cursor, attempt, new ArrayList<>(), callback); return; }
        String lower = oldest && !cursor.date.isEmpty() ? cursor.date : "1900-01-01";
        String upper = !oldest && !cursor.date.isEmpty() ? cursor.date : today;
        if (lower.compareTo(upper) > 0) { deliver(callback, Result.success(new ComicsPage(Collections.emptyList(), cursor, false))); return; }
        int limit = scope == 0 ? 100 : 12;
        request("issues/", params("filter", comicsFilter(scope, lower, upper), "sort", "store_date:" + (oldest ? "asc" : "desc"),
                "limit", String.valueOf(limit), "field_list", ISSUE_FIELDS + ",api_detail_url"), DAY, result -> {
            JSONArray rows = result.data == null ? null : result.data.optJSONArray("results");
            if (rows == null) { deliver(callback, Result.failed(result.failure == null ? Failure.DATA : result.failure)); return; }
            if (rows.length() == 0) { deliver(callback, Result.success(new ComicsPage(Collections.emptyList(), cursor, false))); return; }
            try {
                JSONObject last = rows.getJSONObject(rows.length()-1); String boundary = text(last, "store_date"), previous = null;
                List<Issue> complete = new ArrayList<>(); Set<Integer> ids = new HashSet<>(); int committed = 0;
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i); String date = text(row, "store_date");
                    Issue item = comic(row, volumes, scope, lower, upper);
                    if (!ids.add(row.optInt("id")) || (previous != null && (oldest ? date.compareTo(previous) < 0 : date.compareTo(previous) > 0)))
                        throw new IllegalArgumentException("Ordem inválida");
                    previous = date;
                    // A última data pode ter sido cortada pela API: nenhuma referência parcial dela é consumida.
                    if (!date.equals(boundary)) { committed++; if (item != null) complete.add(item); }
                }
                Comparator<Issue> order = Comparator.comparing((Issue item) -> item.publicationDate).thenComparingInt(item -> item.id);
                complete.sort(oldest ? order : order.reversed());
                comicsBucket(volumes, scope, oldest, today, new ComicsCursor(boundary, 0, cursor.examined + committed), attempt, complete, callback);
            } catch (Exception invalid) { deliver(callback, Result.failed(Failure.DATA)); }
        });
    }
    private void comicsBucket(Map<Integer, Reference> volumes, int scope, boolean oldest, String today,
            ComicsCursor cursor, int attempt, List<Issue> items, Callback<ComicsPage> callback) {
        int limit = scope == 0 ? 100 : 12;
        request("issues/", params("filter", comicsFilter(scope, cursor.date, cursor.date), "sort", "id:" + (oldest ? "asc" : "desc"),
                "offset", String.valueOf(cursor.offset), "limit", String.valueOf(limit), "field_list", ISSUE_FIELDS + ",api_detail_url"), DAY, result -> {
            JSONArray rows = result.data == null ? null : result.data.optJSONArray("results");
            if (rows == null || !result.data.has("number_of_total_results")) {
                deliver(callback, Result.failed(result.failure == null ? Failure.DATA : result.failure)); return;
            }
            try {
                int previous = -1;
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i); int id = row.optInt("id");
                    Issue item = comic(row, volumes, scope, cursor.date, cursor.date);
                    if (previous >= 0 && (oldest ? id <= previous : id >= previous)) throw new IllegalArgumentException("Ordem inválida");
                    previous = id; if (item != null) items.add(item);
                }
                int examined = cursor.examined + rows.length(), nextOffset = cursor.offset + rows.length();
                boolean sameDay = rows.length() > 0 && nextOffset < result.data.optInt("number_of_total_results");
                ComicsCursor next;
                boolean more;
                if (sameDay) { next = new ComicsCursor(cursor.date, nextOffset, examined); more = true; }
                else {
                    SimpleDateFormat dates = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT); dates.setLenient(false);
                    Calendar date = Calendar.getInstance(); date.setTime(dates.parse(cursor.date)); date.add(Calendar.DAY_OF_MONTH, oldest ? 1 : -1);
                    String bound = dates.format(date.getTime()); next = new ComicsCursor(bound, -1, examined);
                    more = bound.compareTo("1900-01-01") >= 0 && bound.compareTo(today) <= 0;
                }
                if (items.isEmpty() && more && attempt < 2) comicsPage(volumes, scope, oldest, today, next, attempt + 1, callback);
                else deliver(callback, Result.success(new ComicsPage(items, next, more)));
            } catch (Exception invalid) { deliver(callback, Result.failed(Failure.DATA)); }
        });
    }
    private static String comicsFilter(int scope, String lower, String upper) {
        return (scope == 0 ? "" : "volume:" + scope + ",") + "store_date:" + lower + "|" + upper;
    }
    private static Issue comic(JSONObject row, Map<Integer, Reference> volumes, int scope, String lower, String upper) {
        int id = row.optInt("id"); String path = resourcePath(text(row, "api_detail_url"), "issue"), date = text(row, "store_date");
        JSONObject volume = row.optJSONObject("volume");
        if (id <= 0 || path == null || !path.endsWith("-" + id + "/") || !date.matches("\\d{4}-\\d{2}-\\d{2}")
                || date.compareTo(lower) < 0 || date.compareTo(upper) > 0
                || (scope != 0 && (volume == null || volume.optInt("id") != scope))) throw new IllegalArgumentException("Edição inválida");
        Reference known = volume == null ? null : volumes.get(volume.optInt("id"));
        if (known == null || !known.path.equals(resourcePath(text(volume, "api_detail_url"), "volume")) || text(volume, "name").isEmpty()) return null;
        String name = text(volume, "name"), number = text(row, "issue_number");
        return new Issue(id, known.id, name + (number.isEmpty() ? "" : " #" + number), name, date, image(row), website(text(row, "site_detail_url")));
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
