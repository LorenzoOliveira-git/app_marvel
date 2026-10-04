package com.example.app_marvel.ui.auth;

import java.util.LinkedHashMap;
import java.util.Map;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.example.app_marvel.data.auth.AuthRepository;

/** Email/nome restauráveis. Senhas existem somente em memória durante a edição. */
public final class AuthViewModel extends ViewModel {
    private final AuthRepository repository;
    private final SavedStateHandle saved;
    private final MutableLiveData<AuthState> state = new MutableLiveData<>();
    private String password = "", confirmation = "";
    public AuthViewModel(AuthRepository repository, SavedStateHandle saved) {
        this.repository = repository; this.saved = saved;
        state.setValue(new AuthState(AuthState.Status.READY, null, true));
    }
    public LiveData<AuthState> getState() { return state; }
    public String getEmail() { String s = saved.get("email"); return s == null ? "" : s; }
    public String getName() { String s = saved.get("name"); return s == null ? "" : s; }
    public String getPassword() { return password; }
    public String getConfirmation() { return confirmation; }
    public void setEmail(String s) { saved.set("email", s); }
    public void setName(String s) { saved.set("name", s); }
    public void setPassword(String s) { password = s; }
    public void setConfirmation(String s) { confirmation = s; }
    public enum Field { NAME, EMAIL, PASSWORD, CONFIRMATION }
    public enum Validation { REQUIRED, EMAIL, SHORT_PASSWORD, CONFIRMATION }
    public Map<Field, Validation> validate(boolean register) {
        Map<Field, Validation> errors = new LinkedHashMap<>();
        if (register && getName().trim().isEmpty()) errors.put(Field.NAME, Validation.REQUIRED);
        if (getEmail().trim().isEmpty()) errors.put(Field.EMAIL, Validation.REQUIRED);
        else if (!getEmail().trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) errors.put(Field.EMAIL, Validation.EMAIL);
        if (password.isEmpty()) errors.put(Field.PASSWORD, Validation.REQUIRED);
        else if (register && password.length() < 6) errors.put(Field.PASSWORD, Validation.SHORT_PASSWORD);
        if (register && !password.equals(confirmation)) errors.put(Field.CONFIRMATION, Validation.CONFIRMATION);
        return errors;
    }
    public void submit(boolean register) {
        if (!validate(register).isEmpty()) return;
        if (state.getValue().status == AuthState.Status.LOADING) return;
        state.setValue(new AuthState(AuthState.Status.LOADING, null, true));
        AuthRepository.Callback done = (failure, nameSaved) -> {
            if (failure == null) { password = ""; confirmation = ""; }
            state.setValue(new AuthState(failure == null ? AuthState.Status.SUCCESS : AuthState.Status.ERROR,
                    failure, nameSaved));
        };
        if (register) repository.register(getName().trim(), getEmail().trim(), password, done);
        else repository.signIn(getEmail().trim(), password, done);
    }
    public void consumeSuccess() { state.setValue(new AuthState(AuthState.Status.READY, null, true)); }
    @Override protected void onCleared() { password = ""; confirmation = ""; }
}
