package com.example.app_marvel.data.herodraft;

import com.example.app_marvel.data.firebase.FirebaseServices;
import com.google.firebase.functions.FirebaseFunctionsException;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.FirebaseFirestoreException;
import android.util.Log;
import com.example.app_marvel.BuildConfig;
import java.util.Map;

/** Rascunho validado e confirmado no servidor; não representa um herói concluído. */
public final class HeroDraftRepository {
    public enum Failure { AUTH, INVALID, CONFIGURATION, NETWORK, CATALOG, FUNCTION_UNAVAILABLE, FIRESTORE_NETWORK, FIRESTORE_PERMISSION, UNKNOWN }
    public interface Callback { void complete(Failure failure); }
    private final FirebaseServices services;
    public HeroDraftRepository(FirebaseServices services) { this.services = services; }
    public boolean available() { return services.isLocal(); }
    public String account() {
        return services.auth() == null || services.auth().getCurrentUser() == null ? "" : services.auth().getCurrentUser().getUid();
    }
    public void save(Map<String, Object> data, Callback callback) {
        if (!available()) { callback.complete(Failure.CONFIGURATION); return; }
        String uid = account();
        if (uid.isEmpty()) { callback.complete(Failure.AUTH); return; }
        String id = (String) data.get("draftId");
        services.functions().getHttpsCallable("saveHeroDraft").call(data).addOnCompleteListener(called -> {
            if (!uid.equals(account())) { callback.complete(Failure.AUTH); return; }
            if (!called.isSuccessful()) { callback.complete(failure(called.getException(), "functions")); return; }
            Object value = called.getResult().getData();
            if (!(value instanceof Map)) { callback.complete(Failure.UNKNOWN); return; }
            Map<?, ?> response = (Map<?, ?>) value;
            if (!uid.equals(response.get("uid")) || !id.equals(response.get("draftId"))
                    || !"draft".equals(response.get("state")) || !Boolean.FALSE.equals(response.get("generatedImage"))) {
                callback.complete(Failure.UNKNOWN); return;
            }
            services.firestore().document("users/" + uid + "/heroDrafts/" + id).get(Source.SERVER).addOnCompleteListener(read -> {
                if (!uid.equals(account())) { callback.complete(Failure.AUTH); return; }
                if (!read.isSuccessful()) { callback.complete(failure(read.getException(), "firestore")); return; }
                var document = read.getResult();
                callback.complete(document.exists() && !document.getMetadata().isFromCache()
                        && uid.equals(document.getString("uid")) && "draft".equals(document.getString("state"))
                        && Boolean.FALSE.equals(document.getBoolean("generatedImage")) ? null : Failure.UNKNOWN);
            });
        });
    }

    private Failure failure(Exception error, String stage) {
        // Apenas etapa e código: não registra dados do personagem, conta, token ou mensagem bruta.
        String code = error instanceof FirebaseFunctionsException ? ((FirebaseFunctionsException) error).getCode().name()
                : error instanceof FirebaseFirestoreException ? ((FirebaseFirestoreException) error).getCode().name() : "UNKNOWN";
        if (BuildConfig.DEBUG) Log.w("HeroDraftSave", "stage=" + stage + " code=" + code);
        if (error instanceof FirebaseFirestoreException) {
            switch (((FirebaseFirestoreException) error).getCode()) {
                case UNAUTHENTICATED: return Failure.AUTH;
                case PERMISSION_DENIED: return Failure.FIRESTORE_PERMISSION;
                case UNAVAILABLE: case DEADLINE_EXCEEDED: return Failure.FIRESTORE_NETWORK;
                default: return Failure.UNKNOWN;
            }
        }
        if (error instanceof FirebaseFunctionsException) {
            switch (((FirebaseFunctionsException) error).getCode()) {
                case UNAUTHENTICATED: case PERMISSION_DENIED: return Failure.AUTH;
                case INVALID_ARGUMENT: return Failure.INVALID;
                case FAILED_PRECONDITION: return Failure.CONFIGURATION;
                case NOT_FOUND: return Failure.FUNCTION_UNAVAILABLE;
                case UNAVAILABLE:
                    Object details = ((FirebaseFunctionsException) error).getDetails();
                    if (details instanceof Map && "comicvine-unavailable".equals(((Map<?, ?>) details).get("reason"))) return Failure.CATALOG;
                    return Failure.NETWORK;
                case DEADLINE_EXCEEDED: return Failure.NETWORK;
                default: return Failure.UNKNOWN;
            }
        }
        return error instanceof com.google.firebase.FirebaseNetworkException ? Failure.NETWORK : Failure.UNKNOWN;
    }
}
