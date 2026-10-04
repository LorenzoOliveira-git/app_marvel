package com.example.app_marvel.data.auth;

import android.content.Context;

public final class AuthRepositoryProvider {
    private AuthRepositoryProvider() { }
    public static AuthRepository create(Context context) {
        return new PreviewAuthRepository(context);
    }
}
