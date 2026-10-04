package com.example.app_marvel.data.translation;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Cache local compartilhado por telas. Não contém contas, senhas ou chaves de API. */
final class TranslationCache extends SQLiteOpenHelper {
    TranslationCache(Context context) { super(context, "translations.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE translations (cache_key TEXT PRIMARY KEY, entity_id TEXT NOT NULL, "
                + "field TEXT NOT NULL, source_hash TEXT NOT NULL, source_text TEXT NOT NULL, "
                + "target_language TEXT NOT NULL, translated_text TEXT NOT NULL, "
                + "provider TEXT NOT NULL, created_at INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Migração de cache não definida");
    }
    String find(String key) {
        try (Cursor cursor = getReadableDatabase().query("translations", new String[]{"translated_text"},
                "cache_key = ?", new String[]{key}, null, null, null)) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }
    void save(String key, String entity, String field, String hash, String original, String translated) {
        ContentValues values = new ContentValues();
        values.put("cache_key", key); values.put("entity_id", entity); values.put("field", field);
        values.put("source_hash", hash); values.put("source_text", original);
        values.put("target_language", "pt"); values.put("translated_text", translated);
        values.put("provider", "ML Kit 17.0.3 / en-pt / plain-text-v1");
        values.put("created_at", System.currentTimeMillis());
        long result = getWritableDatabase().insertWithOnConflict("translations", null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
        if (result == -1) throw new IllegalStateException("Falha ao salvar tradução");
    }
}
