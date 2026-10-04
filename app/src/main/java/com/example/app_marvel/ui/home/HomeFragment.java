package com.example.app_marvel.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;
import com.example.app_marvel.MarvelApplication;
import com.example.app_marvel.data.model.AppFeature;
import com.example.app_marvel.databinding.FragmentHomeBinding;
import com.example.app_marvel.ui.common.AppViewModelFactory;
import com.example.app_marvel.ui.common.UiState;
import com.example.app_marvel.ui.components.FeatureCardView;
import com.example.app_marvel.ui.navigation.AppNavigator;

public final class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.post(() -> { if (binding != null) ((com.example.app_marvel.MainActivity) requireActivity()).updateContentInsets(); });
        ViewCompat.setAccessibilityHeading(binding.homeHeading, true);
        ViewCompat.setAccessibilityHeading(binding.marvHeading, true);
        ViewCompat.setAccessibilityHeading(binding.exploreHeading, true);
        MarvelApplication application = (MarvelApplication) requireActivity().getApplication();
        HomeViewModel viewModel = new ViewModelProvider(this,
                new AppViewModelFactory(application.getContainer().getFeatures(), AppFeature.HOME))
                .get(HomeViewModel.class);
        viewModel.getState().observe(getViewLifecycleOwner(), state -> {
            if (state.getStatus() != UiState.Status.CONTENT) {
                return;
            }
            for (AppFeature feature : state.getData()) {
                cardFor(feature).bind(feature,
                        clicked -> ((AppNavigator) requireActivity()).openFeature(feature));
            }
        });
    }

    private FeatureCardView cardFor(AppFeature feature) {
        switch (feature) {
            case CHARACTERS: return binding.charactersCard;
            case STORIES: return binding.storiesCard;
            case CREATE_HERO: return binding.createCard;
            case PROFILE: return binding.profileCard;
            default: throw new IllegalArgumentException("Destino sem card");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
