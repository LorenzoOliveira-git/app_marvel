package com.example.app_marvel.ui.common;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import com.example.app_marvel.R;
import com.example.app_marvel.data.catalog.MarvelRepository;

public final class ComicVineNavigation {
    private ComicVineNavigation() { }
    public static void open(Context context, String url) {
        if (MarvelRepository.website(url).isEmpty()) return;
        try { context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (ActivityNotFoundException unavailable) { Toast.makeText(context, R.string.catalog_link_failure, Toast.LENGTH_SHORT).show(); }
    }
}
