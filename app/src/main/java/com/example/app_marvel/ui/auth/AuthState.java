package com.example.app_marvel.ui.auth;

import com.example.app_marvel.data.auth.AuthRepository;

public final class AuthState {
    public enum Status { READY, LOADING, ERROR, SUCCESS, UNAVAILABLE }
    public final Status status;
    public final AuthRepository.Failure failure;
    public final boolean nameSaved;
    public AuthState(Status status, AuthRepository.Failure failure, boolean nameSaved) {
        this.status = status; this.failure = failure; this.nameSaved = nameSaved;
    }
}
