package com.example.app_marvel.data.catalog;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** SQLite armazena só metadados; índices de vários MiB não atravessam um CursorWindow. */
final class CatalogCache extends SQLiteOpenHelper {
    static final class Entry {
        final JSONObject payload; final long savedAt;
        Entry(JSONObject payload, long savedAt) { this.payload = payload; this.savedAt = savedAt; }
    }
    private final File directory;
    CatalogCache(Context context) {
        super(context, "comicvine-cache.db", null, 2);
        directory = new File(context.getFilesDir(), "comicvine-responses");
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE responses (request TEXT PRIMARY KEY, filename TEXT NOT NULL, saved_at INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Somente respostas públicas recuperáveis; nenhuma tradução, conta ou credencial é removida.
        db.execSQL("DROP TABLE IF EXISTS responses"); onCreate(db);
    }
    Entry get(String request) {
        String filename; long saved;
        try (Cursor cursor = getReadableDatabase().query("responses", new String[]{"filename", "saved_at"},
                "request = ?", new String[]{request}, null, null, null)) {
            if (!cursor.moveToFirst()) return null;
            filename = cursor.getString(0); saved = cursor.getLong(1);
        }
        if (!filename.matches("[a-f0-9]{64}\\.json")) return null;
        try (FileInputStream input = new AtomicFile(new File(directory, filename)).openRead(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + count > 8_000_000) return null;
                bytes.write(buffer, 0, count);
            }
            return new Entry(new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8)), saved);
        } catch (Exception corruptOrRemoved) { return null; }
    }
    void put(String request, JSONObject payload) {
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Cache directory unavailable");
        StringBuilder filename = new StringBuilder();
        try {
            for (byte value : MessageDigest.getInstance("SHA-256").digest(request.getBytes(StandardCharsets.UTF_8)))
                filename.append(String.format(Locale.ROOT, "%02x", value & 255));
        } catch (Exception unavailable) { throw new IllegalStateException("Cache identity unavailable"); }
        filename.append(".json"); AtomicFile file = new AtomicFile(new File(directory, filename.toString()));
        FileOutputStream output = null;
        try { output = file.startWrite(); output.write(payload.toString().getBytes(StandardCharsets.UTF_8)); file.finishWrite(output); }
        catch (IOException failed) { if (output != null) file.failWrite(output); throw new IllegalStateException("Cache write failed"); }
        ContentValues values = new ContentValues(); values.put("request", request); values.put("filename", filename.toString());
        values.put("saved_at", System.currentTimeMillis()); SQLiteDatabase db = getWritableDatabase();
        if (db.insertWithOnConflict("responses", null, values, SQLiteDatabase.CONFLICT_REPLACE) < 0) throw new IllegalStateException("Cache metadata failed");
        List<String> obsolete = new ArrayList<>();
        try (Cursor cursor = db.rawQuery("SELECT filename FROM responses ORDER BY saved_at DESC LIMIT -1 OFFSET 300", null)) {
            while (cursor.moveToNext()) obsolete.add(cursor.getString(0));
        }
        db.execSQL("DELETE FROM responses WHERE request IN (SELECT request FROM responses ORDER BY saved_at DESC LIMIT -1 OFFSET 300)");
        for (String name : obsolete) if (name.matches("[a-f0-9]{64}\\.json")) new AtomicFile(new File(directory, name)).delete();
    }
}
