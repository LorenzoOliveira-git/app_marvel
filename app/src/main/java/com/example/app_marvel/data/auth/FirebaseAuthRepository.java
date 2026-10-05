package com.example.app_marvel.data.auth;

import com.example.app_marvel.data.firebase.FirebaseServices;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

public final class FirebaseAuthRepository implements AuthRepository {
    private final FirebaseAuth auth;
    private final MutableLiveData<AuthSession> session = new MutableLiveData<>();
    public FirebaseAuthRepository(FirebaseServices services) {
        auth = services.auth();
        publish();
        if (auth != null) auth.addAuthStateListener(ignored -> publish());
    }
    private void publish() {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        session.setValue(new AuthSession(auth != null, user == null ? null : user.getUid(),
                user == null ? null : user.getDisplayName(), user == null ? null : user.getEmail(),
                user == null || user.getPhotoUrl() == null ? null : user.getPhotoUrl().toString()));
    }
    @Override public LiveData<AuthSession> getSession() { return session; }
    @Override public void signIn(String email, String password, Callback callback) {
        if (auth == null) { callback.complete(Failure.UNAVAILABLE, false); return; }
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            publish(); callback.complete(task.isSuccessful() ? null : failure(task.getException()), true);
        });
    }
    @Override public void register(String name, String email, String password, Callback callback) {
        if (auth == null) { callback.complete(Failure.UNAVAILABLE, false); return; }
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) { callback.complete(failure(task.getException()), false); return; }
            FirebaseUser user = task.getResult().getUser();
            if (user == null) { callback.complete(Failure.UNKNOWN, false); return; }
            user.updateProfile(new UserProfileChangeRequest.Builder().setDisplayName(name).build())
                    .addOnCompleteListener(profile -> {
                        // A conta já existe. Falha no nome não deve repetir cadastro/gerar conta duplicada.
                        publish(); callback.complete(null, profile.isSuccessful());
                    });
        });
    }
    @Override public void updateName(String name, Callback callback) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty() || normalized.length() > 100) { callback.complete(Failure.INVALID, false); return; }
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        if (user == null) { callback.complete(Failure.CREDENTIALS, false); return; }
        String uid = user.getUid();
        user.updateProfile(new UserProfileChangeRequest.Builder().setDisplayName(normalized).build()).addOnCompleteListener(updated -> {
            if (auth.getCurrentUser() == null || !uid.equals(auth.getCurrentUser().getUid())) { callback.complete(Failure.CREDENTIALS, false); return; }
            if (!updated.isSuccessful()) { callback.complete(failure(updated.getException()), false); return; }
            user.reload().addOnCompleteListener(read -> {
                if (auth.getCurrentUser() == null || !uid.equals(auth.getCurrentUser().getUid())) { callback.complete(Failure.CREDENTIALS, false); return; }
                if (!read.isSuccessful()) { callback.complete(failure(read.getException()), false); return; }
                boolean confirmed = normalized.equals(auth.getCurrentUser().getDisplayName());
                if (confirmed) publish();
                callback.complete(confirmed ? null : Failure.UNKNOWN, confirmed);
            });
        });
    }
    @Override public void signOut() { if (auth != null) auth.signOut(); publish(); }
    private Failure failure(Exception error) {
        if (error instanceof FirebaseNetworkException) return Failure.NETWORK;
        if (error instanceof FirebaseTooManyRequestsException) return Failure.RATE_LIMIT;
        if (error instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) error).getErrorCode();
            switch (code) {
                case "ERROR_EMAIL_ALREADY_IN_USE": return Failure.EMAIL_IN_USE;
                case "ERROR_WEAK_PASSWORD": return Failure.WEAK_PASSWORD;
                case "ERROR_INVALID_CREDENTIAL": case "ERROR_WRONG_PASSWORD":
                case "ERROR_USER_NOT_FOUND": case "ERROR_INVALID_EMAIL": return Failure.CREDENTIALS;
                case "ERROR_OPERATION_NOT_ALLOWED": case "ERROR_INVALID_API_KEY":
                case "ERROR_APP_NOT_AUTHORIZED": return Failure.UNAVAILABLE;
                default: return Failure.UNKNOWN;
            }
        }
        return Failure.UNKNOWN;
    }
}
