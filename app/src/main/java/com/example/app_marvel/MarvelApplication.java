package com.example.app_marvel;

import android.app.Application;
import com.example.app_marvel.di.AppContainer;

public final class MarvelApplication extends Application {
    private AppContainer container;

    @Override
    public void onCreate() {
        super.onCreate();
        container = new AppContainer(this);
    }

    public AppContainer getContainer() {
        return container;
    }
}
