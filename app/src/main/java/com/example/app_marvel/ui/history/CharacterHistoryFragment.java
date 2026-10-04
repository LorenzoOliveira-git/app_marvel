package com.example.app_marvel.ui.history;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateHandleSupport;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.app_marvel.MainActivity;
import com.example.app_marvel.MarvelApplication;
import com.example.app_marvel.R;
import com.example.app_marvel.databinding.FragmentCharacterHistoryBinding;
import com.example.app_marvel.ui.common.UiState;
import com.example.app_marvel.ui.components.AppearanceAdapter;
import com.example.app_marvel.ui.components.RecentIssueAdapter;

public final class CharacterHistoryFragment extends Fragment {
    private FragmentCharacterHistoryBinding binding;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle saved) {
        binding = FragmentCharacterHistoryBinding.inflate(inflater, parent, false); return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        view.post(() -> { if (binding != null) ((MainActivity) requireActivity()).updateContentInsets(); });
        ViewCompat.setAccessibilityHeading(binding.historyFirstHeading, true); ViewCompat.setAccessibilityHeading(binding.historyTimelineHeading, true);
        var container = ((MarvelApplication) requireActivity().getApplication()).getContainer();
        CharacterHistoryViewModel model = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type, @NonNull CreationExtras extras) {
                if (type != CharacterHistoryViewModel.class) throw new IllegalArgumentException("ViewModel não registrado");
                return type.cast(new CharacterHistoryViewModel(container.getCatalog(), container.getTranslations(), SavedStateHandleSupport.createSavedStateHandle(extras)));
            }
        }).get(CharacterHistoryViewModel.class);
        HistoryIdentityBinder identity = new HistoryIdentityBinder(binding.characterIdentity, container.getImages());
        binding.characterIdentity.identityOpen.setText(R.string.history_profile);
        model.getDetail().observe(getViewLifecycleOwner(), state -> {
            binding.historyState.render(state.getStatus(), model::reload);
            binding.historyContent.setVisibility(state.getStatus() == UiState.Status.CONTENT ? View.VISIBLE : View.GONE);
            if (state.getStatus() != UiState.Status.CONTENT) return;
            identity.character(state.getData().character);
            binding.characterIdentity.identityOpen.setOnClickListener(v -> ((MainActivity) requireActivity()).openCharacter(state.getData().character.id));
        });
        model.getDescription().observe(getViewLifecycleOwner(), state -> identity.description(state, model::translate));
        model.getOrigin().observe(getViewLifecycleOwner(), state -> identity.origin(state, model::translate));
        RecentIssueAdapter first = new RecentIssueAdapter(container.getImages(), true, true);
        first.openWith(id -> ((MainActivity) requireActivity()).openIssue(id));
        binding.historyFirstList.setLayoutManager(new LinearLayoutManager(requireContext())); binding.historyFirstList.setAdapter(first);
        model.getFirst().observe(getViewLifecycleOwner(), state -> {
            binding.historyFirstSection.setVisibility(state.getStatus() == UiState.Status.UNAVAILABLE ? View.GONE : View.VISIBLE);
            binding.historyFirstState.render(state.getStatus(), model::loadFirst);
            binding.historyFirstList.setVisibility(state.getStatus() == UiState.Status.CONTENT ? View.VISIBLE : View.GONE);
            if (state.getStatus() == UiState.Status.CONTENT) first.submit(state.getData());
        });
        AppearanceAdapter timeline = new AppearanceAdapter(container.getImages());
        timeline.openWith(id -> ((MainActivity) requireActivity()).openIssue(id));
        binding.historyTimelineList.setLayoutManager(new LinearLayoutManager(requireContext())); binding.historyTimelineList.setAdapter(timeline);
        model.getAppearances().observe(getViewLifecycleOwner(), state -> {
            binding.historyTimelineState.render(state.getStatus(), () -> model.loadAppearances(false));
            if (state.getStatus() == UiState.Status.EMPTY) binding.historyTimelineState.emptyMessage(R.string.history_empty_title,
                    Boolean.TRUE.equals(model.getHasMore().getValue()) ? R.string.history_next_body : R.string.history_empty_body);
            binding.historyTimelineList.setVisibility(state.getStatus() == UiState.Status.CONTENT ? View.VISIBLE : View.GONE);
            if (state.getStatus() == UiState.Status.CONTENT) timeline.submit(state.getData(), Boolean.TRUE.equals(model.getHasMore().getValue()));
        });
        Runnable paging = () -> {
            boolean loading = Boolean.TRUE.equals(model.getLoadingMore().getValue());
            binding.historyPageProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
            binding.historyPageMore.setVisibility(Boolean.TRUE.equals(model.getHasMore().getValue()) ? View.VISIBLE : View.GONE);
            binding.historyPageMore.setEnabled(!loading);
            binding.historyPageMore.setText(Boolean.TRUE.equals(model.getMoreError().getValue()) ? R.string.catalog_retry : R.string.history_more);
        };
        model.getHasMore().observe(getViewLifecycleOwner(), value -> paging.run());
        model.getLoadingMore().observe(getViewLifecycleOwner(), value -> paging.run());
        model.getMoreError().observe(getViewLifecycleOwner(), value -> paging.run());
        binding.historyPageMore.setOnClickListener(v -> model.loadAppearances(true));
    }
    @Override public void onDestroyView() { binding.historyFirstList.setAdapter(null); binding.historyTimelineList.setAdapter(null); super.onDestroyView(); binding = null; }
}
