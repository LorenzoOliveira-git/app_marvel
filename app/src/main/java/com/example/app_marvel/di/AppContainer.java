package com.example.app_marvel.di;

import android.content.Context;
import com.example.app_marvel.data.auth.AuthRepository;
import com.example.app_marvel.data.auth.FirebaseAuthRepository;
import com.example.app_marvel.data.repository.FeatureRepository;
import com.example.app_marvel.data.repository.LocalFeatureRepository;

/** Dependências compartilhadas. Serviços remotos serão adicionados em seus próprios blocos. */
public final class AppContainer {
    private final FeatureRepository features = new LocalFeatureRepository();

    private final AuthRepository auth;
    public AppContainer(Context context) { auth = new FirebaseAuthRepository(context); }
    public AuthRepository getAuth() { return auth; }

    public FeatureRepository getFeatures() {
        return features;
    }
}
