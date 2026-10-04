package com.example.app_marvel.ui.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import com.example.app_marvel.data.auth.AuthRepository;
import com.example.app_marvel.data.auth.AuthSession;

public final class AccountViewModel extends ViewModel {
    private final AuthRepository repository;
    public AccountViewModel(AuthRepository repository) { this.repository = repository; }
    public LiveData<AuthSession> getSession() { return repository.getSession(); }
    public void signOut() { repository.signOut(); }
}
