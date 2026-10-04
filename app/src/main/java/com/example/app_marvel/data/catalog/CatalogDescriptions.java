package com.example.app_marvel.data.catalog;

import com.example.app_marvel.data.translation.TranslationRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Nomes próprios conhecidos não entram no tradutor; o resumo original permanece no modelo. */
public final class CatalogDescriptions {
    private CatalogDescriptions() { }
    /** Contexto evita interpretar um rótulo curto de poder como verbo/nome comum isolado. */
    public static void translatePower(TranslationRepository repository, CatalogModels.Reference power, TranslationRepository.Callback callback) {
        repository.translate("power:" + power.id, "name-context-v1", "Superpower: " + power.name, result -> {
            if (result.getFailure() != null) { callback.complete(result); return; }
            String text = result.getText(); int colon = text.indexOf(':');
            if (colon < 0 || colon > 32 || text.substring(colon + 1).trim().isEmpty()) {
                callback.complete(TranslationRepository.Result.failed(TranslationRepository.Failure.TRANSLATION)); return;
            }
            callback.complete(TranslationRepository.Result.content(text.substring(colon + 1).trim(), result.isFromCache()));
        });
    }
    public static void translate(TranslationRepository repository, CatalogModels.Character item, TranslationRepository.Callback callback) {
        String source = MarvelRepository.plain(item.originalDeck);
        Set<String> known = new LinkedHashSet<>();
        if (!item.name.isEmpty()) known.add(item.name);
        if (!item.realName.isEmpty()) known.add(item.realName);
        for (String alias : item.aliases.split("[\\r\\n]+")) if (!alias.trim().isEmpty()) known.add(alias.trim());
        List<String> names = new ArrayList<>(known); names.sort(Comparator.comparingInt(String::length).reversed());
        names.replaceAll(Pattern::quote);
        Matcher matches = Pattern.compile(String.join("|", names), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(source);
        List<String> parts = new ArrayList<>(); List<Boolean> protectedPart = new ArrayList<>(); int cursor = 0;
        while (matches.find()) {
            parts.add(source.substring(cursor, matches.start())); protectedPart.add(false);
            parts.add(matches.group()); protectedPart.add(true); cursor = matches.end();
        }
        parts.add(source.substring(cursor)); protectedPart.add(false);
        String[] translated = new String[parts.size()]; int[] remaining = {0}; boolean[] fromCache = {true}; boolean[] failed = {false};
        for (int i = 0; i < parts.size(); i++) {
            if (protectedPart.get(i) || parts.get(i).trim().isEmpty()) translated[i] = parts.get(i);
            else remaining[0]++;
        }
        if (remaining[0] == 0) { callback.complete(TranslationRepository.Result.content(source, true)); return; }
        for (int i = 0; i < parts.size(); i++) {
            if (translated[i] != null) continue;
            int position = i; String part = parts.get(i), text = part.trim();
            int start = part.indexOf(text), end = start + text.length();
            repository.translate("character:" + item.id, "deck-proper-names-v3:" + i, text, result -> {
                if (failed[0]) return;
                if (result.getFailure() != null) { failed[0] = true; callback.complete(TranslationRepository.Result.failed(result.getFailure())); return; }
                translated[position] = part.substring(0, start) + result.getText().trim() + part.substring(end);
                fromCache[0] &= result.isFromCache();
                if (--remaining[0] == 0) callback.complete(TranslationRepository.Result.content(String.join("", Arrays.asList(translated)), fromCache[0]));
            });
        }
    }
}
