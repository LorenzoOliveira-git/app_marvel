package com.example.app_marvel.data.auth;

/** Somente identidade devolvida pelo provedor; nunca contém senha ou token. */
public final class AuthSession {
    private final boolean configured;
    private final String uid, name, email, photoUrl;
    public AuthSession(boolean configured, String uid, String name, String email) {
        this(configured, uid, name, email, null);
    }
    public AuthSession(boolean configured, String uid, String name, String email, String photoUrl) {
        this.configured = configured; this.uid = uid; this.name = name; this.email = email; this.photoUrl = photoUrl;
    }
    public boolean isConfigured() { return configured; }
    public boolean isAuthenticated() { return uid != null; }
    public String getUid() { return uid == null ? "" : uid; }
    public String getName() { return name == null ? "" : name; }
    public String getEmail() { return email == null ? "" : email; }
    public String getPhotoUrl() { return photoUrl == null ? "" : photoUrl; }
}
