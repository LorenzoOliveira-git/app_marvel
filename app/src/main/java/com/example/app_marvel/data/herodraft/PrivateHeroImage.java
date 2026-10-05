package com.example.app_marvel.data.herodraft;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;

/** Download assinado em memória: sem URL/cache em disco, logs ou envio de tokens Firebase. */
public final class PrivateHeroImage {
    public interface Callback { void complete(Bitmap bitmap); }
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    public void load(String url, Callback callback) {
        worker.execute(() -> {
            Bitmap bitmap = null; HttpsURLConnection connection = null;
            try {
                URL address = new URL(url);
                if (!"https".equals(address.getProtocol()) || !"api.cloudinary.com".equals(address.getHost())
                    || address.getUserInfo() != null || (address.getPort() != -1 && address.getPort() != 443)
                    || !address.getPath().matches("/v1_1/[a-z0-9-]+/image/download")) throw new java.io.IOException();
                connection = (HttpsURLConnection) address.openConnection();
                connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
                connection.setConnectTimeout(15000); connection.setReadTimeout(30000);
                if (connection.getResponseCode() != 200) throw new java.io.IOException();
                try (var input = connection.getInputStream(); var output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (output.size() + count > 16 * 1024 * 1024) throw new java.io.IOException();
                        output.write(buffer, 0, count);
                    }
                    byte[] bytes = output.toByteArray();
                    BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                    if (bounds.outWidth != 1024 || bounds.outHeight != 1536) throw new java.io.IOException();
                    BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = 2;
                    bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                }
            } catch (Exception unavailable) { bitmap = null; }
            finally { if (connection != null) connection.disconnect(); }
            Bitmap result = bitmap; main.post(() -> callback.complete(result));
        });
    }
    public void close() { worker.shutdownNow(); }
}
