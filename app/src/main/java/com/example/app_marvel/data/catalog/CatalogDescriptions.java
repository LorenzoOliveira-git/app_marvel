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
        repository.translate("power:" + power.id, "name-context-reviewed-v2", "Superpower: " + power.name, result -> {
            if (result.getFailure() != null) { callback.complete(result); return; }
            String text = result.getText(); int colon = text.indexOf(':');
            if (colon < 0 || colon > 32 || text.substring(colon + 1).trim().isEmpty()) {
                callback.complete(TranslationRepository.Result.failed(TranslationRepository.Failure.TRANSLATION)); return;
            }
            String label = text.substring(colon + 1).trim();
            // Traduções revisadas de rótulos genéricos, com ID e texto original observados.
            // Não são nomes oficiais localizados nem fatos adicionados ao personagem.
            if (power.id == 35 && power.name.equals("Gadgets")) label = "Dispositivos";
            if (power.id == 38 && power.name.equals("Siphon Abilities")) label = "Absorção de habilidades";
            if (power.id == 54 && power.name.equals("Wall Clinger")) label = "Aderência a paredes";
            if (power.id == 138 && power.name.equals("Webslinger")) label = "Lançamento de teias";
            callback.complete(TranslationRepository.Result.content(label, result.isFromCache()));
        });
    }
    public static void translate(TranslationRepository repository, CatalogModels.Character item, TranslationRepository.Callback callback) {
        String source = MarvelRepository.plain(item.originalDeck);
        Set<String> known = new LinkedHashSet<>();
        if (!item.name.isEmpty()) known.add(item.name);
        if (!item.realName.isEmpty()) known.add(item.realName);
        for (String alias : item.aliases.split("[\\r\\n]+")) if (!alias.trim().isEmpty()) known.add(alias.trim());
        translateProtected(repository, "character:" + item.id, "deck-proper-names-v4", source, known, callback);
    }
    public static void translateIssue(TranslationRepository repository, CatalogModels.IssueDetails item, String field,
            TranslationRepository.Callback callback) {
        String html = field.equals("deck") ? item.originalDeck : item.originalDescription;
        Set<String> known = new LinkedHashSet<>();
        known.add(item.issue.volume); known.add(item.issue.title);
        for (CatalogModels.Reference ref : item.characters) known.add(ref.name);
        for (CatalogModels.Reference ref : item.teams) known.add(ref.name);
        for (List<CatalogModels.Credit> credits : Arrays.asList(item.creators,item.arcs,item.locations,item.objects,item.concepts))
            for (CatalogModels.Credit credit : credits) known.add(credit.reference.name);
        // Os nomes de links vêm do próprio campo original, sem aliases inventados.
        Matcher anchors = Pattern.compile("(?is)<a\\b[^>]*>(.*?)</a>").matcher(html);
        while (anchors.find()) { String name = MarvelRepository.plain(anchors.group(1)); if (!name.isEmpty()) known.add(name); }
        translateProtected(repository, "issue:" + item.issue.id, field + "-proper-names-v1", MarvelRepository.plain(html), known, callback);
    }
    private static void translateProtected(TranslationRepository repository, String entity, String field, String source,
            Set<String> known, TranslationRepository.Callback callback) {
        known.removeIf(String::isEmpty);
        if (known.isEmpty()) { repository.translate(entity,field,source,callback); return; }
        List<String> names = new ArrayList<>(known); names.sort(Comparator.comparingInt(String::length).reversed());
        names.replaceAll(Pattern::quote);
        Matcher matches = Pattern.compile("(?<![\\p{L}\\p{N}])(?:" + String.join("|", names) + ")(?![\\p{L}\\p{N}])").matcher(source);
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
            repository.translate(entity, field + ":" + i, text, result -> {
                if (failed[0]) return;
                if (result.getFailure() != null) { failed[0] = true; callback.complete(TranslationRepository.Result.failed(result.getFailure())); return; }
                translated[position] = part.substring(0, start) + result.getText().trim() + part.substring(end);
                fromCache[0] &= result.isFromCache();
                if (--remaining[0] == 0) callback.complete(TranslationRepository.Result.content(String.join("", Arrays.asList(translated)), fromCache[0]));
            });
        }
    }
}
