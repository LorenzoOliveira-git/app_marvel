package com.example.app_marvel.di;

import android.content.Context;
import com.example.app_marvel.data.firebase.FirebaseServices;
import com.example.app_marvel.data.auth.AuthRepository;
import com.example.app_marvel.data.auth.FirebaseAuthRepository;
import com.example.app_marvel.data.comicvine.ComicVineClient;
import com.example.app_marvel.data.translation.MlKitTranslationRepository;
import com.example.app_marvel.data.translation.TranslationRepository;
import com.example.app_marvel.data.repository.FeatureRepository;
import com.example.app_marvel.data.repository.LocalFeatureRepository;
import com.example.app_marvel.data.catalog.MarvelRepository;
import com.example.app_marvel.ui.components.ComicVineImages;

/** Dependências compartilhadas. Serviços remotos serão adicionados em seus próprios blocos. */
public final class AppContainer {
    private final FeatureRepository features = new LocalFeatureRepository();

    private final FirebaseServices firebase;
    private final AuthRepository auth;
    private final com.example.app_marvel.data.herodraft.HeroDraftRepository heroDrafts;
    private final com.example.app_marvel.data.herodraft.HeroCreationRepository heroCreations;
    private final com.example.app_marvel.data.herodraft.MyHeroesRepository myHeroes;
    private final TranslationRepository translations;
    private final ComicVineClient comicVine;
    private final MarvelRepository catalog;
    private final ComicVineImages images;
    public AppContainer(Context context) {
        firebase = new FirebaseServices(context);
        auth = new FirebaseAuthRepository(firebase);
        heroDrafts = new com.example.app_marvel.data.herodraft.HeroDraftRepository(firebase);
        heroCreations = new com.example.app_marvel.data.herodraft.HeroCreationRepository(firebase);
        myHeroes = new com.example.app_marvel.data.herodraft.MyHeroesRepository(firebase);
        translations = new MlKitTranslationRepository(context);
        comicVine = new ComicVineClient(context);
        catalog = new MarvelRepository(context, comicVine);
        images = new ComicVineImages(context.getApplicationContext());
    }
    public com.example.app_marvel.data.herodraft.HeroDraftRepository getHeroDrafts() { return heroDrafts; }
    public com.example.app_marvel.data.herodraft.HeroCreationRepository getHeroCreations() { return heroCreations; }
    public com.example.app_marvel.data.herodraft.MyHeroesRepository getMyHeroes() { return myHeroes; }
    public FirebaseServices getFirebase() { return firebase; }
    public AuthRepository getAuth() { return auth; }
    public TranslationRepository getTranslations() { return translations; }
    public ComicVineClient getComicVine() { return comicVine; }
    public MarvelRepository getCatalog() { return catalog; }
    public ComicVineImages getImages() { return images; }

    public FeatureRepository getFeatures() {
        return features;
    }
}
