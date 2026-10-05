package com.example.app_marvel;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.widget.NestedScrollView;
import androidx.core.splashscreen.SplashScreen;
import androidx.navigation.NavGraph;
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
import com.example.app_marvel.data.auth.AuthSession;
import com.example.app_marvel.databinding.ActivityMainBinding;
import com.example.app_marvel.ui.common.FeatureResources;
import com.example.app_marvel.ui.navigation.AppNavigator;
import com.google.android.material.navigation.NavigationBarView;

public final class MainActivity extends AppCompatActivity implements AppNavigator {
    private ActivityMainBinding binding;
    private NavController navController;
    private NavHostFragment host;
    private int bottomSafe, keyboardBottom;
    private final Runnable contentInsetsUpdater = this::updateContentInsets;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        if (android.os.Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setAccessibilityHeading(binding.screenTitle, true);
        ViewCompat.setAccessibilityHeading(binding.expandedScreenTitle, true);
        ViewCompat.setAccessibilityHeading(binding.userName, true);
        applySafeInsets();

        host = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host);
        if (host == null) {
            throw new IllegalStateException("NavHost não encontrado");
        }
        navController = host.getNavController();
        boolean authenticated = ((MarvelApplication) getApplication()).getContainer().getAuth()
                .getSession().getValue().isAuthenticated();
        if (savedInstanceState == null && authenticated) resetGraph(R.id.homeFragment);
        else if (savedInstanceState != null && navController.getCurrentDestination() != null
                && !isAuthDestination(navController.getCurrentDestination().getId())) {
            navController.getGraph().setStartDestination(R.id.homeFragment);
        }
        binding.headerBack.setOnClickListener(view -> {
            if (navController.getCurrentDestination() != null && (navController.getCurrentDestination().getId() == R.id.characterDetailsFragment
                    || navController.getCurrentDestination().getId() == R.id.characterHistoryFragment
                    || navController.getCurrentDestination().getId() == R.id.arcDetailsFragment
                    || navController.getCurrentDestination().getId() == R.id.arcsFragment
                    || navController.getCurrentDestination().getId() == R.id.movieDetailsFragment
                    || navController.getCurrentDestination().getId() == R.id.moviesFragment
                    || navController.getCurrentDestination().getId() == R.id.comicsFragment
                    || navController.getCurrentDestination().getId() == R.id.issueDetailsFragment))
                navController.navigateUp();
            else openFeature(AppFeature.HOME);
        });
        ((MarvelApplication) getApplication()).getContainer().getAuth().getSession().observe(this, this::renderUserHeader);
        binding.bottomNavigation.setItemActiveIndicatorEnabled(false);
        boolean showLabels = getResources().getBoolean(R.bool.navigation_labels_visible)
                && getResources().getConfiguration().fontScale <= 1.3f;
        binding.bottomNavigation.setLabelVisibilityMode(showLabels
                ? NavigationBarView.LABEL_VISIBILITY_LABELED
                : NavigationBarView.LABEL_VISIBILITY_UNLABELED);
        preserveIconGeometry();
        // NavigationUI salva/restaura as pilhas dos destinos superiores.
        NavigationUI.setupWithNavController(binding.bottomNavigation, navController);
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            binding.screenTitle.setText(destination.getLabel());
            boolean details = destination.getId() == R.id.characterDetailsFragment;
            boolean history = destination.getId() == R.id.movieDetailsFragment || destination.getId() == R.id.moviesFragment || destination.getId() == R.id.arcDetailsFragment || destination.getId() == R.id.arcsFragment || destination.getId() == R.id.characterHistoryFragment || destination.getId() == R.id.comicsFragment || destination.getId() == R.id.issueDetailsFragment;
            boolean catalog = destination.getId() == R.id.charactersFragment || details || history || destination.getId() == R.id.storiesFragment;
            if (details) binding.bottomNavigation.getMenu().findItem(R.id.charactersFragment).setChecked(true);
            if (history) binding.bottomNavigation.getMenu().findItem(R.id.storiesFragment).setChecked(true);
            binding.headerBack.setContentDescription(getString(details || history ? R.string.details_back : R.string.catalog_back));
            boolean home = destination.getId() == R.id.homeFragment;
            boolean expanded = catalog && getResources().getConfiguration().fontScale > 1.3f;
            binding.titleHeader.setVisibility(home || expanded ? View.GONE : View.VISIBLE);
            binding.headerExpandedSpacer.setVisibility(expanded ? View.VISIBLE : View.GONE);
            binding.expandedScreenTitle.setVisibility(expanded ? View.VISIBLE : View.GONE);
            binding.expandedScreenTitle.setText(destination.getLabel());
            binding.userHeader.setVisibility(home ? View.VISIBLE : View.GONE);
            binding.headerBack.setVisibility(catalog ? View.VISIBLE : View.GONE);
            binding.brandCaption.setVisibility(catalog ? View.GONE : View.VISIBLE);
            binding.screenTitle.setTextAppearance(catalog ? R.style.TextAppearance_Marvel_CatalogHeader : R.style.TextAppearance_Marvel_Title);
            binding.screenTitle.setGravity(catalog ? Gravity.CENTER : Gravity.START);
            boolean form = isAuthDestination(destination.getId());
            binding.bottomNavigation.setVisibility(form ? View.GONE : View.VISIBLE);
            binding.mainHeader.setVisibility(form ? View.GONE : View.VISIBLE);
            binding.authHeader.setVisibility(form ? View.VISIBLE : View.GONE);
            binding.navHost.post(contentInsetsUpdater);
        });
        binding.bottomNavigation.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateContentInsets());
    }

    private void renderUserHeader(AuthSession session) {
        boolean signed = session.isAuthenticated();
        binding.userName.setText(!signed ? getString(R.string.home_guest_name)
                : session.getName().isEmpty() ? getString(R.string.home_account_name) : session.getName());
        binding.userEmail.setText(session.getEmail());
        binding.userEmail.setVisibility(signed && !session.getEmail().isEmpty() ? View.VISIBLE : View.GONE);
        ((MarvelApplication) getApplication()).getContainer().getImages()
                .showProfilePhoto(binding.userAvatar, session.getPhotoUrl());
    }

    private void applySafeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            // O painel continua por trás do menu e da barra do sistema.
            view.setPadding(safe.left, safe.top, safe.right, 0);
            bottomSafe = safe.bottom; keyboardBottom = keyboard.bottom;
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) binding.bottomNavigation.getLayoutParams();
            int margin = getResources().getDimensionPixelSize(R.dimen.space_sm) + bottomSafe;
            if (params.bottomMargin != margin) { params.bottomMargin = margin; binding.bottomNavigation.setLayoutParams(params); }
            updateContentInsets();
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

    private boolean isAuthDestination(int id) { return id == R.id.loginFragment || id == R.id.registerFragment; }

    public void updateContentInsets() {
        if (isFinishing() || isDestroyed() || host == null || !host.isAdded()
                || host.getChildFragmentManager().getPrimaryNavigationFragment() == null) return;
        View content = host.getChildFragmentManager().getPrimaryNavigationFragment().getView();
        if (!(content instanceof NestedScrollView)) return;
        int bottom = Math.max(bottomSafe, keyboardBottom);
        if (binding.bottomNavigation.getVisibility() == View.VISIBLE) {
            bottom = Math.max(bottom, binding.bottomNavigation.getHeight() + bottomSafe
                    + 2 * getResources().getDimensionPixelSize(R.dimen.space_sm));
        }
        content.setPadding(content.getPaddingLeft(), content.getPaddingTop(), content.getPaddingRight(), bottom);
        ((NestedScrollView) content).setClipToPadding(false);
    }

    public void openCharacter(int characterId) {
        if (characterId <= 0) return;
        hideKeyboard();
        Bundle arguments = new Bundle(); arguments.putInt("characterId", characterId);
        navController.navigate(R.id.characterDetailsFragment, arguments);
    }
    public void openIssue(int issueId) {
        if (issueId <= 0) return;
        hideKeyboard(); Bundle arguments = new Bundle(); arguments.putInt("issueId",issueId);
        navController.navigate(R.id.issueDetailsFragment,arguments);
    }
    public void openArc(int arcId) {
        if (arcId<=0) return;
        hideKeyboard(); Bundle arguments=new Bundle(); arguments.putInt("arcId",arcId);
        navController.navigate(R.id.arcDetailsFragment,arguments);
    }
    public void openArcs() { hideKeyboard(); navController.navigate(R.id.arcsFragment); }
    public void openMovie(int movieId) { if (movieId<=0) return;hideKeyboard();Bundle args=new Bundle();args.putInt("movieId",movieId);navController.navigate(R.id.movieDetailsFragment,args); }
    public void openMovies() { hideKeyboard(); navController.navigate(R.id.moviesFragment); }
    public void openComics() { hideKeyboard(); navController.navigate(R.id.comicsFragment); }
    public void openCharacterHistory(int characterId) {
        if (characterId <= 0) return;
        hideKeyboard();
        Bundle arguments = new Bundle(); arguments.putInt("characterId", characterId);
        navController.navigate(R.id.characterHistoryFragment, arguments);
    }

    public void enterHome() { hideKeyboard(); resetGraph(R.id.homeFragment); }
    public void openLogin() { hideKeyboard(); navController.navigate(R.id.loginFragment); }
    public void leaveAccount() {
        hideKeyboard(); resetGraph(R.id.loginFragment);
    }
    private void resetGraph(int destination) {
        NavGraph graph = navController.getNavInflater().inflate(R.navigation.main_graph);
        graph.setStartDestination(destination); navController.setGraph(graph);
    }
    private void hideKeyboard() {
        WindowInsetsControllerCompat controller = ViewCompat.getWindowInsetsController(binding.getRoot());
        if (controller != null) controller.hide(WindowInsetsCompat.Type.ime());
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
    protected void onDestroy() {
        if (binding != null) binding.navHost.removeCallbacks(contentInsetsUpdater);
        host = null;
        super.onDestroy();
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
