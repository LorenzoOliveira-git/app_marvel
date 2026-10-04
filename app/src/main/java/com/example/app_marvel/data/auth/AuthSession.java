package com.example.app_marvel.data.auth;

/** Identidade devolvida pelo repositório da variante; nunca contém senha ou token. */
public final class AuthSession {
    private final boolean configured;
    private final String uid, name, email;
    public AuthSession(boolean configured, String uid, String name, String email) {
        this.configured = configured; this.uid = uid; this.name = name; this.email = email;
    }
    public boolean isConfigured() { return configured; }
    public boolean isAuthenticated() { return uid != null; }
    public String getName() { return name == null ? "" : name; }
    public String getEmail() { return email == null ? "" : email; }
}
