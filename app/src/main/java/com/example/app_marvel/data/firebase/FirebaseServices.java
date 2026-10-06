package com.example.app_marvel.data.firebase;

import android.content.Context;
import com.example.app_marvel.BuildConfig;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.storage.FirebaseStorage;

/** Perfil local explícito e isolado; endpoints são aplicados antes de usar os SDKs. */
public final class FirebaseServices {
    private final FirebaseApp app;
    private final FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private FirebaseFunctions functions;
    private FirebaseStorage storage;
    public FirebaseServices(Context context) {
        if (BuildConfig.DESIGN_PREVIEW) {
            app = null; auth = null; // Prévia sem conta, geração paga ou serviços remotos.
        } else if (isLocal()) {
            // Identificadores públicos de um projeto demo, sem recursos no Google Cloud.
            FirebaseApp local;
            try { local = FirebaseApp.getInstance("marvel-local"); }
            catch (IllegalStateException absent) {
                local = FirebaseApp.initializeApp(context.getApplicationContext(), new FirebaseOptions.Builder()
                        .setProjectId("demo-marvel-local")
                        .setApplicationId("1:1234567890:android:abcdef1234567890")
                        // Chave fictícia com formato aceito pelo SDK; não pertence a um projeto real.
                        .setApiKey("AIzaSy" + "000000000000000000000000000000000")
                        .setStorageBucket("demo-marvel-local.appspot.com").build(), "marvel-local");
            }
            app = local; auth = FirebaseAuth.getInstance(app);
            auth.useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, 9099);
        } else {
            app = FirebaseApp.initializeApp(context.getApplicationContext());
            auth = app == null ? null : FirebaseAuth.getInstance(app);
        }
    }
    public boolean isLocal() { return BuildConfig.DEBUG && BuildConfig.FIREBASE_EMULATORS; }
    public FirebaseAuth auth() { return auth; }
    public FirebaseFirestore firestore() {
        if (app == null) return null;
        if (firestore == null) {
            firestore = FirebaseFirestore.getInstance(app);
            if (isLocal()) {
                firestore.setFirestoreSettings(new com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                        .setLocalCacheSettings(com.google.firebase.firestore.MemoryCacheSettings.newBuilder().build()).build());
                firestore.useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, 8080);
            }
        }
        return firestore;
    }
    public FirebaseFunctions functions() {
        if (app == null) return null;
        if (functions == null) {
            functions = FirebaseFunctions.getInstance(app, "us-central1");
            if (isLocal()) functions.useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, 5001);
        }
        return functions;
    }
    public FirebaseStorage storage() {
        if (app == null) return null;
        if (storage == null) {
            storage = FirebaseStorage.getInstance(app);
            if (isLocal()) storage.useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, 9199);
        }
        return storage;
    }
}
