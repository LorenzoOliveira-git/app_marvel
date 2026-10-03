package com.example.app_marvel.data.repository;

import com.example.app_marvel.data.model.AppFeature;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Disponibilidade real do bloco 1. Não contém mocks de API ou dados de usuário. */
public final class LocalFeatureRepository implements FeatureRepository {
    private final List<AppFeature> sections = Collections.unmodifiableList(Arrays.asList(
            AppFeature.CHARACTERS, AppFeature.STORIES, AppFeature.CREATE_HERO, AppFeature.PROFILE));

    @Override
    public List<AppFeature> getHomeSections() {
        return sections;
    }

    @Override
    public boolean isAvailable(AppFeature feature) {
        return Objects.requireNonNull(feature) == AppFeature.HOME;
    }
}
