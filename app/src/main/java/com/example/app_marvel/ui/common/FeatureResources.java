package com.example.app_marvel.ui.common;

import com.example.app_marvel.R;
import com.example.app_marvel.data.model.AppFeature;

/** Tradução de modelos para recursos fica na camada de interface. */
public final class FeatureResources {
    private FeatureResources() {}

    public static int title(AppFeature feature) {
        switch (feature) {
            case CHARACTERS: return R.string.nav_characters;
            case STORIES: return R.string.nav_stories;
            case CREATE_HERO: return R.string.nav_create;
            case PROFILE: return R.string.nav_profile;
            case HOME: return R.string.nav_home;
            default: throw new IllegalArgumentException("Destino desconhecido");
        }
    }

    public static int description(AppFeature feature) {
        switch (feature) {
            case CHARACTERS: return R.string.home_characters_description;
            case STORIES: return R.string.home_stories_description;
            case CREATE_HERO: return R.string.home_create_description;
            case PROFILE: return R.string.home_profile_description;
            default: throw new IllegalArgumentException("Destino sem card");
        }
    }

    public static int icon(AppFeature feature) {
        switch (feature) {
            case CHARACTERS: return R.drawable.ic_characters;
            case STORIES: return R.drawable.ic_stories;
            case CREATE_HERO: return R.drawable.ic_create;
            case PROFILE: return R.drawable.ic_profile;
            case HOME: return R.drawable.ic_home;
            default: throw new IllegalArgumentException("Destino desconhecido");
        }
    }

    public static int destination(AppFeature feature) {
        switch (feature) {
            case CHARACTERS: return R.id.charactersFragment;
            case STORIES: return R.id.storiesFragment;
            case CREATE_HERO: return R.id.createHeroFragment;
            case PROFILE: return R.id.profileFragment;
            case HOME: return R.id.homeFragment;
            default: throw new IllegalArgumentException("Destino desconhecido");
        }
    }
}
