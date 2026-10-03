package com.example.app_marvel.ui.common;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.example.app_marvel.data.model.AppFeature;
import com.example.app_marvel.data.repository.FeatureRepository;
import com.example.app_marvel.ui.home.HomeViewModel;
import com.example.app_marvel.ui.section.SectionViewModel;

public final class AppViewModelFactory implements ViewModelProvider.Factory {
    private final FeatureRepository repository;
    private final AppFeature feature;

    public AppViewModelFactory(FeatureRepository repository, AppFeature feature) {
        this.repository = repository;
        this.feature = feature;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass == HomeViewModel.class) {
            return modelClass.cast(new HomeViewModel(repository));
        }
        if (modelClass == SectionViewModel.class) {
            return modelClass.cast(new SectionViewModel(repository, feature));
        }
        throw new IllegalArgumentException("ViewModel não registrado: " + modelClass.getName());
    }
}
