package com.example.app_marvel.data.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/** Demonstração autorizada pelo usuário, compilada somente no APK preview. Sem contas remotas. */
public final class PreviewAuthRepository implements AuthRepository {
    private final SharedPreferences preferences;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MutableLiveData<AuthSession> session = new MutableLiveData<>();

    public PreviewAuthRepository(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences("preview_session", Context.MODE_PRIVATE);
        publish();
    }

    private void publish() {
        String email = preferences.getString("email", null);
        session.setValue(new AuthSession(true, email == null ? null : "preview-user",
                preferences.getString("name", ""), email));
    }

    @Override public LiveData<AuthSession> getSession() { return session; }

    @Override public void signIn(String email, String password, Callback callback) {
        String name = email.equalsIgnoreCase(preferences.getString("registered_email", ""))
                ? preferences.getString("registered_name", "Explorador Marvel") : "Explorador Marvel";
        openSession(name, email, callback);
    }

    @Override public void register(String name, String email, String password, Callback callback) {
        preferences.edit().putString("registered_name", name).putString("registered_email", email).apply();
        openSession(name, email, callback);
    }

    private void openSession(String name, String email, Callback callback) {
        // Senhas não são armazenadas; as validações de formulário continuam na ViewModel.
        handler.postDelayed(() -> {
            preferences.edit().putString("name", name).putString("email", email).apply();
            publish();
            callback.complete(null, true);
        }, 600);
    }

    @Override public void signOut() {
        preferences.edit().remove("name").remove("email").apply();
        publish();
    }
}
