package com.example.app_marvel.data.herodraft;

import com.example.app_marvel.data.firebase.FirebaseServices;
import com.google.firebase.functions.FirebaseFunctionsException;
import com.google.firebase.firestore.Source;
import java.util.Map;

/** Rascunho validado e confirmado no servidor; não representa um herói concluído. */
public final class HeroDraftRepository {
    public enum Failure { AUTH, INVALID, CONFIGURATION, NETWORK, UNKNOWN }
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
        services.functions().getHttpsCallable("saveHeroDraft").call(data).continueWithTask(called -> {
            if (!called.isSuccessful()) throw called.getException();
            if (!uid.equals(account())) throw new IllegalStateException("A conta mudou durante o salvamento.");
            Map<?, ?> response = (Map<?, ?>) called.getResult().getData();
            if (!uid.equals(response.get("uid")) || !id.equals(response.get("draftId")) || !"draft".equals(response.get("state")) || !Boolean.FALSE.equals(response.get("generatedImage")))
                throw new IllegalStateException("Resposta de rascunho inválida.");
            return services.firestore().document("users/" + uid + "/heroDrafts/" + id).get(Source.SERVER);
        }).addOnCompleteListener(read -> {
            if (!uid.equals(account())) { callback.complete(Failure.AUTH); return; }
            if (!read.isSuccessful()) { callback.complete(failure(read.getException())); return; }
            var document = read.getResult();
            callback.complete(document.exists() && uid.equals(document.getString("uid")) && "draft".equals(document.getString("state"))
                    && Boolean.FALSE.equals(document.getBoolean("generatedImage")) ? null : Failure.UNKNOWN);
        });
    }
    private Failure failure(Exception error) {
        if (error instanceof FirebaseFunctionsException) {
            switch (((FirebaseFunctionsException) error).getCode()) {
                case UNAUTHENTICATED: case PERMISSION_DENIED: return Failure.AUTH;
                case INVALID_ARGUMENT: return Failure.INVALID;
                case FAILED_PRECONDITION: return Failure.CONFIGURATION;
                case UNAVAILABLE: case DEADLINE_EXCEEDED: return Failure.NETWORK;
                default: return Failure.UNKNOWN;
            }
        }
        return error instanceof com.google.firebase.FirebaseNetworkException ? Failure.NETWORK : Failure.UNKNOWN;
    }
}
