package com.example.app_marvel.ui.auth;

import androidx.annotation.NonNull;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.SavedStateHandleSupport;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;
import com.example.app_marvel.data.auth.AuthRepository;

public final class AuthViewModelFactory implements ViewModelProvider.Factory {
    private final AuthRepository repository;
    public AuthViewModelFactory(AuthRepository repository) { this.repository = repository; }
    @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type, @NonNull CreationExtras extras) {
        if (type != AuthViewModel.class) throw new IllegalArgumentException("ViewModel não registrado");
        SavedStateHandle state = SavedStateHandleSupport.createSavedStateHandle(extras);
        return type.cast(new AuthViewModel(repository, state));
    }
}
