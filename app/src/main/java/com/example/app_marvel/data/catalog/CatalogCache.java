package com.example.app_marvel.data.catalog;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import org.json.JSONException;
import org.json.JSONObject;

/** Respostas públicas, sem chave ou parâmetros de autenticação. Usado somente na thread de armazenamento. */
final class CatalogCache extends SQLiteOpenHelper {
    static final class Entry {
        final JSONObject payload;
        final long savedAt;
        Entry(JSONObject payload, long savedAt) { this.payload = payload; this.savedAt = savedAt; }
    }
    CatalogCache(Context context) { super(context, "comicvine-cache.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE responses (request TEXT PRIMARY KEY, payload TEXT NOT NULL, saved_at INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }
    Entry get(String request) {
        try (Cursor cursor = getReadableDatabase().query("responses", new String[]{"payload", "saved_at"},
                "request = ?", new String[]{request}, null, null, null)) {
            if (!cursor.moveToFirst()) return null;
            try { return new Entry(new JSONObject(cursor.getString(0)), cursor.getLong(1)); }
            catch (JSONException malformed) { return null; }
        }
    }
    void put(String request, JSONObject payload) {
        ContentValues values = new ContentValues();
        values.put("request", request); values.put("payload", payload.toString());
        values.put("saved_at", System.currentTimeMillis());
        SQLiteDatabase db = getWritableDatabase();
        if (db.insertWithOnConflict("responses", null, values, SQLiteDatabase.CONFLICT_REPLACE) < 0)
            throw new android.database.sqlite.SQLiteException("Cache write failed");
        // Limite aproximado de páginas públicas; índices recentes continuam entre os mais novos.
        db.execSQL("DELETE FROM responses WHERE request IN (SELECT request FROM responses ORDER BY saved_at DESC LIMIT -1 OFFSET 300)");
    }
}
