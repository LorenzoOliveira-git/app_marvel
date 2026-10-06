package com.example.app_marvel.ui.components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;
import com.example.app_marvel.R;

/** Imagens públicas da ComicVine; cache limitado e sem chave da API nas requisições. */
public final class ComicVineImages {
    private final File directory;
    private final Object diskLock = new Object();
    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> memory = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getByteCount(); }
    };
    public ComicVineImages(Context context) { directory = new File(context.getCacheDir(), "comicvine-images"); }
    public void showProfilePhoto(ImageView view, String url) {
        view.setTag(url); view.setImageResource(R.drawable.ic_profile_avatar);
        Uri uri = Uri.parse(url);
        // Foto fornecida pela sessão Firebase. Sem envio da chave ComicVine ou tokens de conta.
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) return;
        loadInto(view, url);
    }
    public void show(ImageView view, String url) {
        view.setTag(url);
        view.setImageResource(R.drawable.ic_image_placeholder);
        if (com.example.app_marvel.BuildConfig.DESIGN_PREVIEW) {
            int[] colors = {0xffB7C7D6, 0xffC9B7AA, 0xffBCCBB7, 0xffC8B9CF, 0xffD7C99E, 0xffB3C9C5};
            view.setImageDrawable(new android.graphics.drawable.ColorDrawable(colors[Math.floorMod(url.hashCode(), colors.length)]));
            return;
        }
        Uri uri = Uri.parse(url);
        if (!"https".equals(uri.getScheme()) || !"comicvine.gamespot.com".equals(uri.getHost())
                || uri.getPath() == null || !uri.getPath().startsWith("/a/uploads/")
                || uri.getQuery() != null || uri.getUserInfo() != null) return;
        loadInto(view, url);
    }
    private void loadInto(ImageView view, String url) {
        Bitmap saved = memory.get(url);
        if (saved != null) { view.setImageBitmap(saved); return; }
        WeakReference<ImageView> target = new WeakReference<>(view);
        workers.execute(() -> {
            Bitmap bitmap = load(url);
            if (bitmap != null) memory.put(url, bitmap);
            main.post(() -> {
                ImageView image = target.get();
                if (bitmap != null && image != null && url.equals(image.getTag())) image.setImageBitmap(bitmap);
            });
        });
    }
    private Bitmap load(String url) {
        HttpsURLConnection connection = null;
        try {
            if (!directory.exists() && !directory.mkdirs() && !directory.exists()) return null;
            StringBuilder name = new StringBuilder();
            for (byte value : MessageDigest.getInstance("SHA-256").digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                name.append(String.format(Locale.ROOT, "%02x", value & 255));
            File file = new File(directory, name + ".image");
            byte[] bytes;
            synchronized (diskLock) {
                if (file.isFile()) {
                    bytes = read(new FileInputStream(file));
                    Bitmap cached = decode(bytes);
                    if (cached != null) return cached;
                    if (!file.delete()) return null;
                }
            }
            {
                connection = (HttpsURLConnection) new URL(url).openConnection();
                connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(12_000); connection.setReadTimeout(20_000);
                connection.setRequestProperty("User-Agent", "SuaMarvel-Android/1.0 (personal non-commercial app)");
                if (connection.getResponseCode() != 200) return null;
                bytes = read(connection.getInputStream());
                Bitmap test = decode(bytes);
                if (test == null) return null;
                synchronized (diskLock) {
                    android.util.AtomicFile atomic = new android.util.AtomicFile(file);
                    FileOutputStream output = null;
                    try { output = atomic.startWrite(); output.write(bytes); atomic.finishWrite(output); }
                    catch (IOException failed) { if (output != null) atomic.failWrite(output); }
                    File[] files = directory.listFiles((dir, child) -> child.endsWith(".image"));
                    if (files != null && files.length > 80) {
                        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
                        for (int i = 0; i < files.length - 80; i++) if (!files[i].delete()) break;
                    }
                }
                return test;
            }
        } catch (Exception unavailable) { return null; }
        finally { if (connection != null) connection.disconnect(); }
    }
    private byte[] read(InputStream stream) throws IOException {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > 2_000_000) throw new IOException("Image limit");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }
    private Bitmap decode(byte[] bytes) {
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > 20_000 || bounds.outHeight > 20_000) return null;
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = 1;
        while (bounds.outWidth / options.inSampleSize > 960 || bounds.outHeight / options.inSampleSize > 960) options.inSampleSize *= 2;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }
}
