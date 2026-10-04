package com.example.app_marvel.data.comicvine;

import android.content.ContentValues;
import android.content.Context;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Reserva persistente por recurso/aparelho. Não representa a cota global de uma chave compartilhada. */
final class ComicVineRateLimiter extends SQLiteOpenHelper {
    ComicVineRateLimiter(Context context) { super(context, "comicvine-usage.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE calls (resource TEXT NOT NULL, attempted_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX calls_resource_time ON calls(resource, attempted_at)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Migração de uso não definida");
    }
    boolean reserve(String resource) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        db.beginTransaction();
        try {
            db.delete("calls", "attempted_at <= ?", new String[]{Long.toString(now - 3_600_000L)});
            if (DatabaseUtils.queryNumEntries(db, "calls", "resource = ?", new String[]{resource}) >= 180) {
                db.setTransactionSuccessful(); return false;
            }
            ContentValues values = new ContentValues();
            values.put("resource", resource); values.put("attempted_at", now);
            if (db.insert("calls", null, values) == -1) throw new IllegalStateException("Reserva não salva");
            db.setTransactionSuccessful(); return true;
        } finally { db.endTransaction(); }
    }
}
