package com.example.app_marvel.data.translation;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Operações assíncronas; uma instância por processo, sem prender a View de um Fragment. */
public final class MlKitTranslationRepository implements TranslationRepository, AutoCloseable {
    private final TranslationCache cache;
    private final ExecutorService storage = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, List<Callback>> pending = new HashMap<>();
    private Translator translator;
    private boolean closed;

    public MlKitTranslationRepository(Context context) {
        cache = new TranslationCache(context.getApplicationContext());
    }

    @Override public void translate(String entityId, String field, String original, Callback callback) {
        if (entityId == null || entityId.trim().isEmpty() || field == null || field.trim().isEmpty()
                || original == null || original.trim().isEmpty() || original.length() > 100_000) {
            main.post(() -> callback.complete(Result.failed(Failure.INVALID_INPUT))); return;
        }
        String hash = sha256(original);
        // Separação e hash evitam colisões entre IDs/campos que contenham separadores.
        String key = sha256(entityId + "\u0000" + field + "\u0000" + hash + "\u0000mlkit-17.0.3-en-pt-plain-v1");
        main.post(() -> {
            if (closed) { callback.complete(Result.failed(Failure.TRANSLATION)); return; }
            List<Callback> listeners = pending.get(key);
            if (listeners != null) { listeners.add(callback); return; }
            listeners = new ArrayList<>(); listeners.add(callback); pending.put(key, listeners);
            storage.execute(() -> {
                try {
                    String saved = cache.find(key);
                    main.post(() -> {
                        if (!pending.containsKey(key) || closed) return;
                        if (saved != null) finish(key, Result.content(saved, true));
                        else translateMissing(key, entityId, field, hash, original);
                    });
                } catch (RuntimeException error) {
                    main.post(() -> finish(key, Result.failed(Failure.LOCAL_STORAGE)));
                }
            });
        });
    }

    private void translateMissing(String key, String entity, String field, String hash, String original) {
        if (translator == null) {
            translator = Translation.getClient(new TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.ENGLISH)
                    .setTargetLanguage(TranslateLanguage.PORTUGUESE).build());
        }
        Translator client = translator;
        client.downloadModelIfNeeded(new DownloadConditions.Builder().requireWifi().build())
                .addOnSuccessListener(ignored -> {
                    if (closed || !pending.containsKey(key)) return;
                    translatePart(client, key, entity, field, hash, original, 0, new StringBuilder());
                })
                .addOnFailureListener(ignored -> finish(key, Result.failed(Failure.MODEL_DOWNLOAD)));
    }

    private void translatePart(Translator client, String key, String entity, String field, String hash,
                               String original, int start, StringBuilder result) {
        // Textos longos são divididos em fronteiras de parágrafos/palavras, preservando separadores.
        int end = Math.min(start + 4000, original.length());
        if (end < original.length()) {
            int boundary = original.lastIndexOf('\n', end - 1);
            if (boundary <= start + 2000) boundary = original.lastIndexOf(' ', end - 1);
            if (boundary > start + 2000) end = boundary;
            else if (Character.isHighSurrogate(original.charAt(end - 1))) end--;
        }
        final int partEnd = end;
        client.translate(original.substring(start, end))
                .addOnSuccessListener(text -> {
                    if (closed || !pending.containsKey(key)) return;
                    result.append(text);
                    int next = partEnd;
                    while (next < original.length() && Character.isWhitespace(original.charAt(next))) {
                        result.append(original.charAt(next++));
                    }
                    if (next < original.length()) {
                        translatePart(client, key, entity, field, hash, original, next, result); return;
                    }
                    String translated = result.toString();
                    if (translated.trim().isEmpty()) {
                        finish(key, Result.failed(Failure.TRANSLATION)); return;
                    }
                    storage.execute(() -> {
                        try {
                            cache.save(key, entity, field, hash, original, translated);
                            main.post(() -> finish(key, Result.content(translated, false)));
                        } catch (RuntimeException error) {
                            main.post(() -> finish(key, Result.failed(Failure.LOCAL_STORAGE)));
                        }
                    });
                })
                .addOnFailureListener(ignored -> finish(key, Result.failed(Failure.TRANSLATION)));
    }

    private void finish(String key, Result result) {
        List<Callback> listeners = pending.remove(key);
        if (listeners != null) for (Callback listener : listeners) listener.complete(result);
    }

    private static String sha256(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    @Override public void close() {
        main.post(() -> {
            if (closed) return;
            closed = true;
            List<Callback> listeners = new ArrayList<>();
            for (List<Callback> group : pending.values()) listeners.addAll(group);
            pending.clear();
            if (translator != null) translator.close();
            storage.execute(cache::close);
            storage.shutdown();
            for (Callback listener : listeners) listener.complete(Result.failed(Failure.TRANSLATION));
        });
    }
}
