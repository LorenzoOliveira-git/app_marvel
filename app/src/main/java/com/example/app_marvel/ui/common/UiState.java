package com.example.app_marvel.ui.common;

import java.util.Objects;

/** Estado imutável. UNAVAILABLE é distinto de lista vazia ou falha de rede. */
public final class UiState<T> {
    public enum Status { LOADING, EMPTY, ERROR, CONTENT, UNAVAILABLE }

    private final Status status;
    private final T data;

    private UiState(Status status, T data) {
        this.status = status;
        this.data = data;
    }

    public static <T> UiState<T> content(T data) {
        return new UiState<>(Status.CONTENT, Objects.requireNonNull(data));
    }

    public static <T> UiState<T> loading() {
        return new UiState<>(Status.LOADING, null);
    }

    public static <T> UiState<T> empty() {
        return new UiState<>(Status.EMPTY, null);
    }

    public static <T> UiState<T> error() {
        return new UiState<>(Status.ERROR, null);
    }

    public static <T> UiState<T> unavailable() {
        return new UiState<>(Status.UNAVAILABLE, null);
    }

    public Status getStatus() {
        return status;
    }

    /** Retorna dados somente no estado CONTENT. */
    public T getData() {
        return data;
    }
}
