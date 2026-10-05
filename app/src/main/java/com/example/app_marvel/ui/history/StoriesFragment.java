package com.example.app_marvel.ui.history;

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
import com.example.app_marvel.databinding.FragmentStoriesBinding;
import com.example.app_marvel.ui.common.UiState;
import com.example.app_marvel.ui.components.RecentIssueAdapter;
import com.example.app_marvel.ui.home.HomeViewModel;

public final class StoriesFragment extends Fragment {
    private FragmentStoriesBinding binding;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle saved) {
        binding = FragmentStoriesBinding.inflate(inflater, parent, false); return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        view.post(() -> { if (binding != null) ((MainActivity) requireActivity()).updateContentInsets(); });
        for (View heading : new View[]{binding.historyFollowHeading, binding.historyDiscoverHeading, binding.historyRecentHeading}) ViewCompat.setAccessibilityHeading(heading, true);
        var container = ((MarvelApplication) requireActivity().getApplication()).getContainer();
        binding.openComics.setOnClickListener(v -> ((MainActivity) requireActivity()).openComics());
        binding.openSeries.setOnClickListener(v -> ((MainActivity) requireActivity()).openSeries());
        binding.openMovies.setOnClickListener(v -> ((MainActivity) requireActivity()).openMovies());
        binding.openArcs.setOnClickListener(v -> ((MainActivity) requireActivity()).openArcs());
        HomeViewModel model = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type) {
                if (type != HomeViewModel.class) throw new IllegalArgumentException("ViewModel não registrado");
                return type.cast(new HomeViewModel(container.getCatalog(), container.getTranslations()));
            }
        }).get(HomeViewModel.class);
        HistoryIdentityBinder identity = new HistoryIdentityBinder(binding.featuredIdentity, container.getImages());
        model.getFeatured().observe(getViewLifecycleOwner(), state -> {
            binding.storiesState.render(state.getStatus(), model::loadFeatured);
            binding.featuredIdentity.getRoot().setVisibility(state.getStatus() == UiState.Status.CONTENT ? View.VISIBLE : View.GONE);
            if (state.getStatus() != UiState.Status.CONTENT) return;
            identity.character(state.getData());
            binding.featuredIdentity.identityOpen.setOnClickListener(v -> ((MainActivity) requireActivity()).openCharacterHistory(state.getData().id));
        });
        model.getFact().observe(getViewLifecycleOwner(), state -> identity.description(state, model::translate));
        model.getOrigin().observe(getViewLifecycleOwner(), state -> identity.origin(state, model::translate));
        RecentIssueAdapter issues = new RecentIssueAdapter(container.getImages(), false, true);
        issues.openWith(id -> ((MainActivity) requireActivity()).openIssue(id));
        binding.storiesRecentList.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.storiesRecentList.setAdapter(issues);
        binding.storiesRecentList.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> { if (r - l > 0 && r - l != or - ol) issues.width(r - l); });
        model.getRecent().observe(getViewLifecycleOwner(), state -> {
            binding.storiesRecentState.render(state.getStatus(), model::loadRecent);
            if (state.getStatus() == UiState.Status.EMPTY) binding.storiesRecentState.emptyMessage(R.string.catalog_selection_empty_title, R.string.catalog_recent_empty_body);
            binding.storiesRecentList.setVisibility(state.getStatus() == UiState.Status.CONTENT ? View.VISIBLE : View.GONE);
            if (state.getStatus() == UiState.Status.CONTENT) issues.submit(state.getData());
        });
    }
    @Override public void onDestroyView() { binding.storiesRecentList.setAdapter(null); super.onDestroyView(); binding = null; }
}
