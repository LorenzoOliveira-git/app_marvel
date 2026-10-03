package com.example.app_marvel.di;

import com.example.app_marvel.data.repository.FeatureRepository;
import com.example.app_marvel.data.repository.LocalFeatureRepository;

/** Dependências compartilhadas. Serviços remotos serão adicionados em seus próprios blocos. */
public final class AppContainer {
    private final FeatureRepository features = new LocalFeatureRepository();

    public FeatureRepository getFeatures() {
        return features;
    }
}
