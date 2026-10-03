package com.example.app_marvel.data.auth;

import androidx.lifecycle.LiveData;

public interface AuthRepository {
    enum Failure { NETWORK, CREDENTIALS, EMAIL_IN_USE, WEAK_PASSWORD, RATE_LIMIT, UNAVAILABLE, UNKNOWN }
    interface Callback { void complete(Failure failure, boolean nameSaved); }
    LiveData<AuthSession> getSession();
    void signIn(String email, String password, Callback callback);
    void register(String name, String email, String password, Callback callback);
    void signOut();
}
