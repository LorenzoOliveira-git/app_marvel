package com.example.app_marvel.diagnostics;

import android.app.Activity;
import android.os.Bundle;
import com.example.app_marvel.MarvelApplication;
import com.example.app_marvel.data.firebase.FirebaseServices;
import com.google.firebase.storage.StorageMetadata;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.io.FileOutputStream;
import java.util.Map;

/** Integração com SDKs reais, disponível somente na variante debug. */
public final class FirebaseLocalCheckActivity extends Activity {
    private FirebaseServices services;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        services = ((MarvelApplication) getApplication()).getContainer().getFirebase();
        if (!services.isLocal()) { report(false, "Ative -PfirebaseEmulators=true"); return; }
        String email = "android-" + System.currentTimeMillis() + "@example.test";
        String password = "local-emulator-only-928374";
        services.auth().createUserWithEmailAndPassword(email, password)
                .continueWithTask(created -> {
                    if (!created.isSuccessful()) throw created.getException();
                    services.auth().signOut();
                    return services.auth().signInWithEmailAndPassword(email, password);
                }).continueWithTask(signed -> {
                    if (!signed.isSuccessful()) throw signed.getException();
                    return services.functions().getHttpsCallable("localSessionCheck").call();
                }).continueWithTask(called -> {
                    if (!called.isSuccessful()) throw called.getException();
                    Map<?, ?> result = (Map<?, ?>) called.getResult().getData();
                    String uid = services.auth().getCurrentUser().getUid();
                    if (!uid.equals(result.get("uid")) || !"emulator".equals(result.get("environment")))
                        throw new IllegalStateException("Resposta da função não corresponde à sessão local.");
                    return services.firestore().document("users/" + uid + "/localChecks/session").get(com.google.firebase.firestore.Source.SERVER);
                }).continueWithTask(read -> {
                    if (!read.isSuccessful()) throw read.getException();
                    String uid = services.auth().getCurrentUser().getUid();
                    if (!uid.equals(read.getResult().getString("uid"))) throw new IllegalStateException("Documento incorreto.");
                    return services.storage().getReference("localChecks/" + uid + "/android.txt").putBytes("local".getBytes(StandardCharsets.UTF_8), new StorageMetadata.Builder().setContentType("text/plain").build());
                }).continueWithTask(upload -> {
                    if (!upload.isSuccessful()) throw upload.getException();
                    return upload.getResult().getStorage().getBytes(1024);
                }).continueWithTask(download -> {
                    if (!download.isSuccessful()) throw download.getException();
                    if (!"local".equals(new String(download.getResult(), StandardCharsets.UTF_8))) throw new IllegalStateException("Arquivo incorreto.");
                    return services.storage().getReference("localChecks/" + services.auth().getCurrentUser().getUid() + "/android.txt").delete();
                }).addOnCompleteListener(done -> {
                    services.auth().signOut();
                    report(done.isSuccessful(), done.isSuccessful() ? "SDKs Android conectados aos quatro emuladores." : String.valueOf(done.getException()));
                });
    }
    private void report(boolean success, String message) {
        try {
            File directory = new File(getFilesDir(), "diagnostics");
            if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Diretório indisponível.");
            JSONObject json = new JSONObject().put("success", success).put("local", services.isLocal()).put("message", message).put("generatedImage", false);
            try (FileOutputStream output = new FileOutputStream(new File(directory, "firebase-local.json"))) {
                output.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception error) { android.util.Log.e("FirebaseLocalCheck", "Falha ao salvar resultado", error); }
        finish();
    }
}
