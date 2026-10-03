package com.example.app_marvel;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.example.app_marvel.data.model.AppFeature;
import com.example.app_marvel.databinding.ActivityMainBinding;
import com.example.app_marvel.ui.common.FeatureResources;
import com.example.app_marvel.ui.navigation.AppNavigator;
import com.google.android.material.navigation.NavigationBarView;

public final class MainActivity extends AppCompatActivity implements AppNavigator {
    private ActivityMainBinding binding;
    private NavController navController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setAccessibilityHeading(binding.screenTitle, true);
        applySafeInsets();

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host);
        if (host == null) {
            throw new IllegalStateException("NavHost não encontrado");
        }
        navController = host.getNavController();
        binding.bottomNavigation.setItemActiveIndicatorEnabled(false);
        boolean showLabels = getResources().getBoolean(R.bool.navigation_labels_visible)
                && getResources().getConfiguration().fontScale <= 1.3f;
        binding.bottomNavigation.setLabelVisibilityMode(showLabels
                ? NavigationBarView.LABEL_VISIBILITY_LABELED
                : NavigationBarView.LABEL_VISIBILITY_UNLABELED);
        preserveIconGeometry();
        // NavigationUI salva/restaura as pilhas dos destinos superiores.
        NavigationUI.setupWithNavController(binding.bottomNavigation, navController);
        navController.addOnDestinationChangedListener((controller, destination, arguments) ->
                binding.screenTitle.setText(destination.getLabel()));
    }

    private void applySafeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(safe.left, safe.top, safe.right, Math.max(safe.bottom, keyboard.bottom));
            // Evita aplicar novamente os insets na barra Material e nos Fragments.
            return WindowInsetsCompat.CONSUMED;
        });
        WindowInsetsControllerCompat controller = ViewCompat.getWindowInsetsController(binding.getRoot());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false);
            controller.setAppearanceLightNavigationBars(false);
        }
        ViewCompat.requestApplyInsets(binding.getRoot());
    }

    private void preserveIconGeometry() {
        int[] widths = {25, 35, 35, 21, 25};
        int slot = getResources().getDimensionPixelSize(R.dimen.navigation_icon_slot);
        for (int index = 0; index < binding.bottomNavigation.getMenu().size(); index++) {
            MenuItem item = binding.bottomNavigation.getMenu().getItem(index);
            Drawable icon = item.getIcon();
            if (icon == null) {
                continue;
            }
            LayerDrawable layers = new LayerDrawable(new Drawable[]{
                    new ColorDrawable(Color.TRANSPARENT), icon});
            layers.setLayerSize(0, slot, slot);
            layers.setLayerSize(1, dp(widths[index]), dp(25));
            layers.setLayerGravity(0, Gravity.CENTER);
            layers.setLayerGravity(1, Gravity.CENTER);
            item.setIcon(layers);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void openFeature(AppFeature feature) {
        binding.bottomNavigation.setSelectedItemId(FeatureResources.destination(feature));
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp() || super.onSupportNavigateUp();
    }
}
