package com.example.app_marvel.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.app_marvel.MainActivity;
import com.example.app_marvel.MarvelApplication;
import com.example.app_marvel.R;
import com.example.app_marvel.data.model.AppFeature;
import com.example.app_marvel.databinding.FragmentHomeBinding;
import com.example.app_marvel.di.AppContainer;
import com.example.app_marvel.ui.common.UiState;
import com.example.app_marvel.ui.components.FeatureCardView;
import com.example.app_marvel.ui.components.RecentIssueAdapter;
import com.example.app_marvel.ui.navigation.AppNavigator;

public final class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle saved) {
        binding = FragmentHomeBinding.inflate(inflater, container, false); return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        view.post(() -> { if (binding != null) ((MainActivity) requireActivity()).updateContentInsets(); });
        for (View heading : new View[]{binding.recentHeading, binding.featuredHeading, binding.factHeading, binding.exploreHeading}) ViewCompat.setAccessibilityHeading(heading, true);
        if (getResources().getConfiguration().screenWidthDp < 360 || getResources().getConfiguration().fontScale > 1.3f) {
            binding.featuredContent.setOrientation(android.widget.LinearLayout.VERTICAL);
            binding.featuredImageCard.setLayoutParams(new android.widget.LinearLayout.LayoutParams(-1, dp(314)));
            android.widget.LinearLayout.LayoutParams details = new android.widget.LinearLayout.LayoutParams(-1, -2);
            details.topMargin = dp(16); binding.featuredDetails.setLayoutParams(details);
        }
        AppContainer container = ((MarvelApplication) requireActivity().getApplication()).getContainer();
        HomeViewModel model = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type) {
                if (type != HomeViewModel.class) throw new IllegalArgumentException("ViewModel não registrado");
                return type.cast(new HomeViewModel(container.getCatalog(), container.getTranslations()));
            }
        }).get(HomeViewModel.class);
        RecentIssueAdapter issues = new RecentIssueAdapter(container.getImages());
        binding.recentList.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.recentList.setAdapter(issues);
        binding.recentList.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> { if (r - l > 0 && r - l != or - ol) issues.width(r - l - dp(8)); });
        model.getRecent().observe(getViewLifecycleOwner(), state -> {
            binding.recentState.render(state.getStatus(), model::loadRecent);
            if (state.getStatus() == UiState.Status.EMPTY) binding.recentState.emptyMessage(R.string.catalog_selection_empty_title, R.string.catalog_recent_empty_body);
            binding.recentList.setVisibility(state.getStatus() == UiState.Status.CONTENT ? View.VISIBLE : View.GONE);
            if (state.getStatus() == UiState.Status.CONTENT) issues.submit(state.getData());
        });
        model.getFeatured().observe(getViewLifecycleOwner(), state -> {
            binding.featuredState.render(state.getStatus(), model::loadFeatured);
            binding.featuredContent.setVisibility(state.getStatus() == UiState.Status.CONTENT ? View.VISIBLE : View.GONE);
            if (state.getStatus() != UiState.Status.CONTENT) return;
            var item = state.getData();
            binding.featuredName.setText(item.name);
            binding.featuredRealName.setText(item.realName.isEmpty() ? "" : getString(R.string.catalog_real_name, item.realName));
            binding.featuredRealName.setVisibility(item.realName.isEmpty() ? View.GONE : View.VISIBLE);
            binding.featuredMore.setEnabled(item.id > 0);
            binding.featuredMore.setOnClickListener(clicked -> ((MainActivity) requireActivity()).openCharacter(item.id));
            container.getImages().show(binding.featuredImage, item.imageUrl);
        });
        model.getFact().observe(getViewLifecycleOwner(), state -> {
            binding.factState.render(state.getStatus(), model::translate);
            binding.featuredDescription.setText(""); binding.factText.setText("");
            binding.factHeader.setVisibility(state.getStatus() == UiState.Status.CONTENT ? View.VISIBLE : View.GONE);
            if (state.getStatus() == UiState.Status.CONTENT) {
                // Dois trechos do mesmo resumo factual; nenhum fato ou tradução oficial é inventado.
                String text = state.getData(); int sentence = text.indexOf(". ");
                binding.featuredDescription.setText(sentence < 0 ? text : text.substring(0, sentence + 1));
                String remainder = sentence < 0 ? "" : text.substring(sentence + 2).trim();
                int nextSentence = remainder.indexOf(". ");
                binding.factText.setText(nextSentence < 0 ? remainder : remainder.substring(0, nextSentence + 1));
                binding.factHeader.setVisibility(sentence < 0 ? View.GONE : View.VISIBLE);
            }
        });
        binding.featuredRetryOrigin.setOnClickListener(v -> model.translate());
        model.getOrigin().observe(getViewLifecycleOwner(), state -> {
            binding.featuredRetryOrigin.setVisibility(state.getStatus() == UiState.Status.ERROR ? View.VISIBLE : View.GONE);
            binding.featuredOrigin.setText(state.getStatus() == UiState.Status.CONTENT ? getString(R.string.catalog_origin_value, state.getData()) : "");
        });
        for (AppFeature feature : container.getFeatures().getHomeSections()) cardFor(feature).bind(feature, clicked -> ((AppNavigator) requireActivity()).openFeature(feature));
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private FeatureCardView cardFor(AppFeature feature) {
        switch (feature) {
            case CHARACTERS: return binding.charactersCard;
            case STORIES: return binding.storiesCard;
            case CREATE_HERO: return binding.createCard;
            case PROFILE: return binding.profileCard;
            default: throw new IllegalArgumentException("Destino sem card");
        }
    }
    @Override public void onDestroyView() { binding.recentList.setAdapter(null); super.onDestroyView(); binding = null; }
}
