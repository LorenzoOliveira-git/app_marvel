package com.example.app_marvel.data.translation;

/** Tradução de campos descritivos. IDs, nomes próprios e relações ficam fora deste serviço. */
public interface TranslationRepository {
    enum Failure { INVALID_INPUT, MODEL_DOWNLOAD, TRANSLATION, LOCAL_STORAGE }

    final class Result {
        private final String text;
        private final Failure failure;
        private final boolean fromCache;

        private Result(String text, Failure failure, boolean fromCache) {
            this.text = text; this.failure = failure; this.fromCache = fromCache;
        }
        public static Result content(String text, boolean fromCache) {
            return new Result(text, null, fromCache);
        }
        public static Result failed(Failure failure) { return new Result(null, failure, false); }
        public String getText() { return text; }
        public Failure getFailure() { return failure; }
        public boolean isFromCache() { return fromCache; }
    }

    interface Callback { void complete(Result result); }

    /** original é texto simples e seguro; a tela conserva o HTML original em sua fonte de dados. */
    void translate(String entityId, String field, String original, Callback callback);
}
