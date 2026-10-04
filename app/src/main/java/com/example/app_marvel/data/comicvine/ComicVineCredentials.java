package com.example.app_marvel.data.comicvine;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Provisionamento por aparelho, fora do APK, git, backup e mensagens de diagnóstico. */
public final class ComicVineCredentials {
    private final File keyFile;
    public ComicVineCredentials(Context context) {
        keyFile = new File(context.getApplicationContext().getNoBackupFilesDir(), "comicvine-api-key");
    }
    String read() throws IOException {
        if (!keyFile.isFile() || keyFile.length() > 512) throw new IOException("Credencial indisponível");
        byte[] bytes = new byte[512];
        try (FileInputStream input = new FileInputStream(keyFile)) {
            int total = 0, count;
            while (total < bytes.length && (count = input.read(bytes, total, bytes.length - total)) != -1) {
                total += count;
            }
            String key = new String(bytes, 0, total, StandardCharsets.UTF_8).trim();
            if (!key.matches("[A-Za-z0-9_-]{16,256}")) throw new IOException("Credencial inválida");
            return key;
        } finally { java.util.Arrays.fill(bytes, (byte) 0); }
    }
}
