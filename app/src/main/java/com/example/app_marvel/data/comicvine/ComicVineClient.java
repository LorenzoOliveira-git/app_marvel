package com.example.app_marvel.data.comicvine;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;

/** Transporte direto. Mapeamento de entidades/filtros pertence ao repositório após validação real. */
public final class ComicVineClient {
    public enum Failure { INVALID_REQUEST, CREDENTIAL_REQUIRED, NETWORK, HTTP, RATE_LIMIT, RESPONSE, LOCAL_STORAGE }
    public static final class Result {
        private final JSONObject payload;
        private final Failure failure;
        private final int httpStatus;
        private Result(JSONObject payload, Failure failure, int httpStatus) {
            this.payload = payload; this.failure = failure; this.httpStatus = httpStatus;
        }
        public JSONObject getPayload() { return payload; }
        public Failure getFailure() { return failure; }
        public int getHttpStatus() { return httpStatus; }
    }
    public interface Callback { void complete(Result result); }
    private static final Set<String> PARAMETERS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("offset", "limit", "field_list", "filter", "sort")));
    private final Context context;
    private final ComicVineCredentials credentials;
    private final ComicVineRateLimiter rateLimiter;
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private long nextRequestAt;
    private final boolean debug;

    public ComicVineClient(Context context) {
        this.context = context.getApplicationContext();
        debug = (context.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        credentials = new ComicVineCredentials(context);
        rateLimiter = new ComicVineRateLimiter(context.getApplicationContext());
    }

    public void get(String resourcePath, Map<String, String> parameters, Callback callback) {
        Map<String, String> query = parameters == null ? Collections.emptyMap() : new HashMap<>(parameters);
        if (resourcePath == null || !resourcePath.matches("[a-z_]+/(?:[0-9]+-[0-9]+/)?")
                || !validParameters(query)) {
            deliver(callback, new Result(null, Failure.INVALID_REQUEST, 0)); return;
        }
        network.execute(() -> execute(resourcePath, query, callback));
    }

    private boolean validParameters(Map<String, String> query) {
        for (Map.Entry<String, String> entry : query.entrySet()) {
            if (!PARAMETERS.contains(entry.getKey()) || entry.getValue() == null
                    || entry.getValue().length() > 2048) return false;
        }
        try {
            int limit = Integer.parseInt(query.getOrDefault("limit", "20"));
            int offset = Integer.parseInt(query.getOrDefault("offset", "0"));
            return limit > 0 && limit <= 100 && offset >= 0;
        } catch (NumberFormatException invalid) { return false; }
    }

    private void execute(String path, Map<String, String> parameters, Callback callback) {
        execute(path, parameters, callback, 0);
    }
    private void execute(String path, Map<String, String> parameters, Callback callback, int attempt) {
        if (com.example.app_marvel.BuildConfig.DESIGN_PREVIEW) {
            try { deliver(callback, new Result(PreviewCatalog.read(context, path, parameters), null, 200)); }
            catch (IOException | JSONException invalid) { deliver(callback, new Result(null, Failure.RESPONSE, 0)); }
            return; // Nunca consulta credenciais, rede ou limite da API nesta variante.
        }
        String key;
        try { key = credentials.read(); }
        catch (IOException absent) { deliver(callback, new Result(null, Failure.CREDENTIAL_REQUIRED, 0)); return; }
        try {
            if (!rateLimiter.reserve(path.substring(0, path.indexOf('/')))) {
                deliver(callback, new Result(null, Failure.RATE_LIMIT, 0)); return;
            }
        } catch (RuntimeException storageError) {
            deliver(callback, new Result(null, Failure.LOCAL_STORAGE, 0)); return;
        }
        HttpsURLConnection connection = null;
        try {
            // Sequencial, com intervalo mínimo; nenhuma espera acontece na thread da interface.
            long delay = nextRequestAt - android.os.SystemClock.elapsedRealtime();
            if (delay > 0) Thread.sleep(delay);
            nextRequestAt = android.os.SystemClock.elapsedRealtime() + 1500;
            Uri.Builder uri = Uri.parse("https://comicvine.gamespot.com/api/" + path).buildUpon()
                    .appendQueryParameter("api_key", key).appendQueryParameter("format", "json");
            for (Map.Entry<String, String> parameter : parameters.entrySet()) {
                uri.appendQueryParameter(parameter.getKey(), parameter.getValue());
            }
            connection = (HttpsURLConnection) new URL(uri.build().toString()).openConnection();
            connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
            connection.setConnectTimeout(12_000); connection.setReadTimeout(20_000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "SuaMarvel-Android/1.0 (personal non-commercial app)");
            int status = connection.getResponseCode();
            if (status != 200) {
                if (attempt == 0 && (status == 408 || status == 502 || status == 503 || status == 504)) {
                    network.execute(() -> execute(path, parameters, callback, 1)); return;
                }
                // Corpos/URLs/exceções de rede não chegam ao log ou à interface.
                deliver(callback, new Result(null, status == 429 ? Failure.RATE_LIMIT : Failure.HTTP, status)); return;
            }
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                boolean appearanceIndex = path.startsWith("character/")
                        && "id,name,publisher,issue_credits".equals(parameters.get("field_list"));
                int maxBytes = path.startsWith("publisher/") || path.startsWith("team/") || appearanceIndex
                        ? 8_000_000 : 2_000_000;
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > maxBytes) {
                        deliver(callback, new Result(null, Failure.RESPONSE, status)); return;
                    }
                    output.write(buffer, 0, count);
                }
                String body = new String(output.toByteArray(), StandardCharsets.UTF_8).replace(key, "[redacted]");
                deliver(callback, new Result(new JSONObject(body), null, status));
            }
        } catch (JSONException malformed) {
            deliver(callback, new Result(null, Failure.RESPONSE, 200));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); deliver(callback, new Result(null, Failure.NETWORK, 0));
        } catch (IOException failure) {
            if (attempt == 0) network.execute(() -> execute(path, parameters, callback, 1));
            else deliver(callback, new Result(null, Failure.NETWORK, 0));
        } finally { if (connection != null) connection.disconnect(); }
    }

    private void deliver(Callback callback, Result result) {
        if (debug && result.failure != null) android.util.Log.w("ComicVine", "Falha: " + result.failure.name() + "; HTTP=" + result.httpStatus);
        main.post(() -> callback.complete(result));
    }
}
