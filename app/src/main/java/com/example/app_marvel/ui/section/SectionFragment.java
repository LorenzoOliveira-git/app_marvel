package com.example.app_marvel.ui.section;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.app_marvel.MarvelApplication;
import com.example.app_marvel.data.model.AppFeature;
import com.example.app_marvel.databinding.FragmentSectionBinding;
import com.example.app_marvel.ui.common.AppViewModelFactory;
import com.example.app_marvel.ui.common.FeatureResources;
import com.example.app_marvel.ui.navigation.AppNavigator;

/** Destinos ainda não integrados compartilham somente o aviso; cada um mantém sua própria pilha. */
public final class SectionFragment extends Fragment {
    private FragmentSectionBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSectionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        AppFeature feature = AppFeature.valueOf(requireArguments().getString("feature", ""));
        MarvelApplication application = (MarvelApplication) requireActivity().getApplication();
        SectionViewModel viewModel = new ViewModelProvider(this,
                new AppViewModelFactory(application.getContainer().getFeatures(), feature))
                .get(SectionViewModel.class);
        viewModel.getState().observe(getViewLifecycleOwner(), state ->
                binding.stateView.render(state.getStatus(),
                        getString(FeatureResources.unavailableMessage(feature)), null));
        binding.returnHome.setOnClickListener(clicked ->
                ((AppNavigator) requireActivity()).openFeature(AppFeature.HOME));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
