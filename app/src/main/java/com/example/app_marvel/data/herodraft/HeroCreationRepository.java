package com.example.app_marvel.data.herodraft;

import com.example.app_marvel.data.firebase.FirebaseServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Source;
import com.google.firebase.functions.FirebaseFunctionsException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Sessão real, estados confirmados pelo servidor e nenhuma repetição automática de geração. */
public final class HeroCreationRepository {
    public enum Failure { AUTH, CONFIGURATION, INVALID, LIMIT, NETWORK, STATE, UNKNOWN }
    public interface Callback<T> { void complete(T value, Failure failure); }
    public static final class Job {
        public final String id, draftId, state;
        public final boolean generatedImage;
        public final Map<String, Object> snapshot;
        Job(DocumentSnapshot document) {
            id = document.getId(); draftId = document.getString("draftId"); state = document.getString("state");
            generatedImage = Boolean.TRUE.equals(document.getBoolean("generatedImage"));
            Object value = document.get("snapshot");
            snapshot = new HashMap<>();
            if (value instanceof Map) for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet())
                if (entry.getKey() instanceof String) snapshot.put((String) entry.getKey(), entry.getValue());
        }
    }
    private final FirebaseServices services;
    public HeroCreationRepository(FirebaseServices services) { this.services = services; }
    public boolean available() { return services.isLocal(); }
    public String account() { return services.auth() == null || services.auth().getCurrentUser() == null ? "" : services.auth().getCurrentUser().getUid(); }
    public Runnable accountListener(Runnable changed) {
        if (services.auth() == null) return () -> { };
        FirebaseAuth.AuthStateListener listener = auth -> changed.run(); services.auth().addAuthStateListener(listener);
        return () -> services.auth().removeAuthStateListener(listener);
    }
    private boolean ready(Callback<?> callback) {
        if (!available()) { callback.complete(null, Failure.CONFIGURATION); return false; }
        if (account().isEmpty()) { callback.complete(null, Failure.AUTH); return false; } return true;
    }
    private Job verified(DocumentSnapshot document, String uid) {
        if (!uid.equals(account()) || !document.exists() || !uid.equals(document.getString("uid"))
                || document.getMetadata().isFromCache() || document.getString("state") == null
                || document.getString("draftId") == null) throw new IllegalStateException();
        return new Job(document);
    }
    private void read(String uid, String id, Callback<Job> callback) {
        services.firestore().document("users/" + uid + "/heroCreationJobs/" + id).get(Source.SERVER).addOnCompleteListener(task -> {
            if (!uid.equals(account())) { callback.complete(null, Failure.AUTH); return; }
            if (!task.isSuccessful()) { callback.complete(null, failure(task.getException())); return; }
            try { callback.complete(verified(task.getResult(), uid), null); }
            catch (RuntimeException invalid) { callback.complete(null, Failure.STATE); }
        });
    }
    public void refresh(String id, Callback<Job> callback) { if (ready(callback)) read(account(),id,callback); }
    public void prepare(String draftId, String operationId, Callback<Job> callback) {
        if (!ready(callback)) return; String uid = account();
        services.firestore().document("users/" + uid + "/heroDrafts/" + draftId).get(Source.SERVER).addOnCompleteListener(task -> {
            if (!uid.equals(account())) { callback.complete(null, Failure.AUTH); return; }
            if (!task.isSuccessful()) { callback.complete(null, failure(task.getException())); return; }
            DocumentSnapshot document = task.getResult(); String hash = document.getString("contentHash");
            if (!document.exists() || !uid.equals(document.getString("uid")) || hash == null) { callback.complete(null, Failure.STATE); return; }
            Map<String, Object> data = new HashMap<>(); data.put("draftId", draftId); data.put("operationId", operationId); data.put("contentHash", hash);
            call("prepareHeroCreation", data, uid, callback);
        });
    }
    public void execute(String id, Callback<Job> callback) {
        Map<String, Object> data = new HashMap<>(); data.put("operationId", id); data.put("confirmPaidGeneration", true);
        if (ready(callback)) call("executeHeroCreation", data, account(), callback);
    }
    public void resume(String id, Callback<Job> callback) {
        Map<String, Object> data = new HashMap<>(); data.put("operationId", id);
        if (ready(callback)) call("resumeHeroCreation", data, account(), callback);
    }
    public void retry(String previous, String id, Callback<Job> callback) {
        Map<String, Object> data = new HashMap<>(); data.put("previousOperationId", previous); data.put("operationId", id); data.put("confirmNewPaidAttempt", true);
        if (ready(callback)) call("retryHeroGeneration", data, account(), callback);
    }
    private void call(String name, Map<String, Object> data, String uid, Callback<Job> callback) {
        var callable = services.functions().getHttpsCallable(name);
        callable.setTimeout(550, TimeUnit.SECONDS);
        callable.call(data).addOnCompleteListener(task -> {
            if (!uid.equals(account())) { callback.complete(null, Failure.AUTH); return; }
            if (!task.isSuccessful()) {
                Failure cause = failure(task.getException());
                callback.complete(null, "retryHeroGeneration".equals(name) && cause == Failure.CONFIGURATION ? Failure.STATE : cause); return;
            }
            Object value = task.getResult().getData();
            if (!(value instanceof Map) || !uid.equals(((Map<?, ?>) value).get("uid"))
                    || !(((Map<?, ?>) value).get("operationId") instanceof String)) { callback.complete(null, Failure.STATE); return; }
            String id = (String) ((Map<?, ?>) value).get("operationId");
            // A preparação pode deduplicar para outro UUID; outras operações devem preservar a identidade pedida.
            if (!"prepareHeroCreation".equals(name) && !id.equals(data.get("operationId"))) { callback.complete(null, Failure.STATE); return; }
            read(uid, id, callback);
        });
    }
    public ListenerRegistration observe(String id, Callback<Job> callback) {
        if (!ready(callback)) return null; String uid = account();
        return services.firestore().document("users/" + uid + "/heroCreationJobs/" + id).addSnapshotListener(MetadataChanges.INCLUDE, (doc,error) -> {
            if (!uid.equals(account())) { callback.complete(null, Failure.AUTH); return; }
            if (error != null) { callback.complete(null, failure(error)); return; }
            if (doc == null || doc.getMetadata().isFromCache()) { callback.complete(null, Failure.NETWORK); return; }
            try { callback.complete(verified(doc, uid), null); } catch (RuntimeException invalid) { callback.complete(null, Failure.STATE); }
        });
    }
    public void latest(Callback<Job> callback) {
        if (!ready(callback)) return; String uid = account();
        services.firestore().collection("users/" + uid + "/heroCreationJobs").orderBy("createdAt", Query.Direction.DESCENDING).limit(1).get(Source.SERVER).addOnCompleteListener(task -> {
            if (!uid.equals(account())) { callback.complete(null, Failure.AUTH); return; }
            if (!task.isSuccessful()) { callback.complete(null, failure(task.getException())); return; }
            if (task.getResult().isEmpty()) { callback.complete(null, null); return; }
            try { callback.complete(verified(task.getResult().getDocuments().get(0), uid), null); }
            catch (RuntimeException invalid) { callback.complete(null, Failure.STATE); }
        });
    }
    public void hero(String id, Callback<Map<String, Object>> callback) {
        if (!ready(callback)) return; String uid = account();
        services.firestore().document("users/" + uid + "/heroes/" + id).get(Source.SERVER).addOnCompleteListener(task -> {
            if (!uid.equals(account())) { callback.complete(null, Failure.AUTH); return; }
            if (!task.isSuccessful()) { callback.complete(null, failure(task.getException())); return; }
            var doc = task.getResult();
            callback.complete(doc.exists() && uid.equals(doc.getString("uid")) && id.equals(doc.getString("operationId")) ? doc.getData() : null,
                doc.exists() && uid.equals(doc.getString("uid")) && id.equals(doc.getString("operationId")) ? null : Failure.STATE);
        });
    }
    public void imageUrl(String id, Callback<String> callback) {
        if (!ready(callback)) return; String uid = account();
        Map<String, Object> data = new HashMap<>(); data.put("operationId", id);
        services.functions().getHttpsCallable("heroImageUrl").call(data).addOnCompleteListener(task -> {
            if (!uid.equals(account())) { callback.complete(null, Failure.AUTH); return; }
            if (!task.isSuccessful()) { callback.complete(null, failure(task.getException())); return; }
            Object value = task.getResult().getData();
            if (!(value instanceof Map) || !(((Map<?, ?>) value).get("url") instanceof String)
                || !(((Map<?, ?>) value).get("expiresAt") instanceof Number)) { callback.complete(null, Failure.STATE); return; }
            long expires = ((Number) ((Map<?, ?>) value).get("expiresAt")).longValue(), now = System.currentTimeMillis() / 1000;
            callback.complete(expires > now && expires <= now + 600 ? (String) ((Map<?, ?>) value).get("url") : null,
                expires > now && expires <= now + 600 ? null : Failure.STATE);
        });
    }
    private Failure failure(Exception error) {
        if (error instanceof FirebaseFunctionsException) switch (((FirebaseFunctionsException) error).getCode()) {
            case UNAUTHENTICATED: case PERMISSION_DENIED: return Failure.AUTH;
            case INVALID_ARGUMENT: return Failure.INVALID;
            case RESOURCE_EXHAUSTED: return Failure.LIMIT;
            case FAILED_PRECONDITION: return Failure.CONFIGURATION;
            case UNAVAILABLE: case DEADLINE_EXCEEDED: return Failure.NETWORK;
            case ABORTED: case ALREADY_EXISTS: case NOT_FOUND: return Failure.STATE;
            default: return Failure.UNKNOWN;
        }
        if (error instanceof com.google.firebase.FirebaseNetworkException || error instanceof java.io.IOException
            || error instanceof com.google.firebase.firestore.FirebaseFirestoreException) return Failure.NETWORK;
        return Failure.UNKNOWN;
    }
}
