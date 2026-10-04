package com.example.app_marvel.diagnostics;

import android.app.Activity;
import android.os.Bundle;
import com.example.app_marvel.MarvelApplication;
import com.example.app_marvel.data.translation.TranslationRepository;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Verificação real do SDK e cache via ADB. Ausente do APK release e dos fluxos do produto. */
public final class DataCheckActivity extends Activity {
    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        String check = getIntent().getStringExtra("check");
        if (!"translation".equals(check) && !"cache".equals(check)) { finish(); return; }
        TranslationRepository repository = ((MarvelApplication) getApplication()).getContainer().getTranslations();
        // Texto de orientação usado apenas para verificar o tradutor; não é conteúdo/mocks da ComicVine.
        String source = "Welcome! Explore stories and characters.";
        repository.translate("diagnostic:ui", "intro", source, result -> {
            try {
                JSONObject report = new JSONObject();
                report.put("check", check); report.put("original", source);
                report.put("success", result.getFailure() == null);
                report.put("from_cache", result.isFromCache());
                report.put("translated", result.getText());
                report.put("failure", result.getFailure() == null ? JSONObject.NULL : result.getFailure().name());
                File directory = new File(getFilesDir(), "diagnostics");
                if (!directory.isDirectory() && !directory.mkdirs()) return;
                File destination = new File(directory, check + ".json");
                File temporary = new File(directory, check + ".tmp");
                try (FileOutputStream output = new FileOutputStream(temporary)) {
                    output.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
                }
                if (!temporary.renameTo(destination)) temporary.delete();
            } catch (Exception ignored) {
                // Falha vira timeout no roteiro; não imprime dados de serviços nem exceções.
            } finally { finish(); }
        });
    }
}
