package com.example.app_marvel.ui.details;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
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
import com.example.app_marvel.data.catalog.CatalogModels.Reference;
import com.example.app_marvel.databinding.ComponentDetailsRelationsBinding;
import com.example.app_marvel.databinding.FragmentCharacterDetailsBinding;
import com.example.app_marvel.di.AppContainer;
import com.example.app_marvel.ui.common.ComicVineNavigation;
import com.example.app_marvel.ui.common.UiState;
import com.example.app_marvel.ui.components.RelatedCharacterAdapter;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

public final class CharacterDetailsFragment extends Fragment {
    private FragmentCharacterDetailsBinding binding;
    private CharacterDetailsViewModel model;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle saved) {
        binding = FragmentCharacterDetailsBinding.inflate(inflater, parent, false); return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        view.post(() -> { if (binding != null) ((MainActivity) requireActivity()).updateContentInsets(); });
        for (View heading : new View[]{binding.detailsName, binding.descriptionHeading, binding.powersHeading, binding.firstHeading})
            ViewCompat.setAccessibilityHeading(heading, true);
        if (getResources().getConfiguration().screenWidthDp < 360 || getResources().getConfiguration().fontScale > 1.3f) {
            binding.appearanceRow.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(-1, -2); first.bottomMargin = dp(16);
            binding.firstSection.setLayoutParams(first); binding.countSection.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        }
        AppContainer container = ((MarvelApplication) requireActivity().getApplication()).getContainer();
        model = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type, @NonNull CreationExtras extras) {
                if (type != CharacterDetailsViewModel.class) throw new IllegalArgumentException("ViewModel não registrado");
                return type.cast(new CharacterDetailsViewModel(container.getCatalog(), container.getTranslations(), SavedStateHandleSupport.createSavedStateHandle(extras)));
            }
        }).get(CharacterDetailsViewModel.class);
        model.getDetail().observe(getViewLifecycleOwner(), state -> {
            binding.detailsState.render(state.getStatus(), model::reload);
            binding.detailsContent.setVisibility(state.getStatus() == UiState.Status.CONTENT ? View.VISIBLE : View.GONE);
            if (state.getStatus() != UiState.Status.CONTENT) return;
            var details = state.getData(); var item = details.character;
            binding.detailsName.setText(item.name); container.getImages().show(binding.detailsImage, item.imageUrl);
            binding.detailsHistory.setOnClickListener(v -> ((MainActivity) requireActivity()).openCharacterHistory(item.id));
            binding.detailsRealName.setText(item.realName.isEmpty() ? "" : getString(R.string.catalog_real_name, item.realName));
            binding.detailsRealName.setVisibility(item.realName.isEmpty() ? View.GONE : View.VISIBLE);
            binding.countSection.setVisibility(details.appearanceCount < 0 ? View.GONE : View.VISIBLE);
            if (details.appearanceCount >= 0) binding.appearanceCount.setText(getResources().getQuantityString(R.plurals.details_appearances,
                    details.appearanceCount == 1 ? 1 : 2, NumberFormat.getIntegerInstance(new Locale("pt", "BR")).format(details.appearanceCount)));
            binding.detailsSource.setVisibility(item.siteUrl.isEmpty() ? View.GONE : View.VISIBLE);
            binding.detailsSource.setOnClickListener(v -> ComicVineNavigation.open(requireContext(), item.siteUrl));
        });
        model.getDescription().observe(getViewLifecycleOwner(), state -> {
            binding.descriptionSection.setVisibility(state.getStatus() == UiState.Status.UNAVAILABLE ? View.GONE : View.VISIBLE);
            binding.descriptionState.render(state.getStatus(), model::translate);
            binding.detailsDescription.setText(state.getStatus() == UiState.Status.CONTENT ? state.getData() : "");
            binding.detailsDescription.setVisibility(state.getStatus() == UiState.Status.CONTENT ? View.VISIBLE : View.GONE);
        });
        model.getOrigin().observe(getViewLifecycleOwner(), state -> {
            binding.detailsOrigin.setText(state.getStatus() == UiState.Status.CONTENT ? getString(R.string.catalog_origin_value, state.getData()) : "");
            binding.detailsOrigin.setVisibility(state.getStatus() == UiState.Status.CONTENT ? View.VISIBLE : View.GONE);
            binding.originProgress.setVisibility(state.getStatus() == UiState.Status.LOADING ? View.VISIBLE : View.GONE);
            binding.originRetry.setVisibility(state.getStatus() == UiState.Status.ERROR ? View.VISIBLE : View.GONE);
        });
        binding.originRetry.setOnClickListener(v -> model.translate());
        model.getPowers().observe(getViewLifecycleOwner(), state -> {
            binding.powersSection.setVisibility(state.getStatus() == UiState.Status.UNAVAILABLE ? View.GONE : View.VISIBLE);
            binding.powersState.render(state.getStatus(), model::translate); binding.powerLabels.removeAllViews();
            if (state.getStatus() == UiState.Status.CONTENT) for (Reference ref : state.getData()) {
                TextView label = new TextView(requireContext()); label.setText(ref.name);
                label.setTextColor(getResources().getColor(R.color.marvel_text, null)); label.setTextSize(20);
                label.setTypeface(ResourcesCompat.getFont(requireContext(), R.font.bebas_neue_regular));
                label.setPadding(dp(16), dp(10), dp(16), dp(10)); label.setBackgroundResource(R.drawable.background_power);
                label.setMaxWidth(getResources().getDisplayMetrics().widthPixels - 2 * getResources().getDimensionPixelSize(R.dimen.screen_padding));
                binding.powerLabels.addView(label, new ViewGroup.LayoutParams(-2, -2));
            }
        });
        model.getFirst().observe(getViewLifecycleOwner(), state -> {
            boolean content = state.getStatus() == UiState.Status.CONTENT;
            binding.firstSection.setVisibility(state.getStatus() == UiState.Status.UNAVAILABLE ? View.GONE : View.VISIBLE);
            binding.firstState.render(state.getStatus(), model::loadFirst);
            binding.firstTitle.setVisibility(content ? View.VISIBLE : View.GONE);
            binding.firstDate.setVisibility(View.GONE); binding.firstMore.setVisibility(View.GONE);
            if (!content) return;
            var issue = state.getData().get(0); binding.firstTitle.setText(issue.title);
            try {
                SimpleDateFormat source = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT); source.setLenient(false);
                String date = new SimpleDateFormat("dd/MM/yyyy", new Locale("pt", "BR")).format(source.parse(issue.publicationDate));
                binding.firstDate.setText(getString(R.string.details_cover_date, date)); binding.firstDate.setVisibility(View.VISIBLE);
            } catch (Exception invalidDate) { binding.firstDate.setText(""); }
            binding.firstMore.setVisibility(View.VISIBLE); binding.firstMore.setText(R.string.issue_open); binding.firstMore.setContentDescription(getString(R.string.issue_open_named,issue.title));
            binding.firstMore.setOnClickListener(v -> ((MainActivity) requireActivity()).openIssue(issue.id));
        });
        relations(container, "teams", binding.teamsSection, R.string.details_teams, R.string.details_more_teams);
        relations(container, "friends", binding.friendsSection, R.string.details_friends, R.string.details_more_friends);
        relations(container, "enemies", binding.enemiesSection, R.string.details_enemies, R.string.details_more_enemies);
    }
    private void relations(AppContainer container, String kind, ComponentDetailsRelationsBinding section, int heading, int more) {
        boolean team = kind.equals("teams"); var group = model.group(kind);
        section.relationHeading.setText(heading); ViewCompat.setAccessibilityHeading(section.relationHeading, true);
        RelatedCharacterAdapter adapter = new RelatedCharacterAdapter(container.getImages(), team, item -> {
            if (team) ComicVineNavigation.open(requireContext(), item.siteUrl);
            else ((MainActivity) requireActivity()).openCharacter(item.id);
        });
        section.relationList.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        section.relationList.setAdapter(adapter);
        group.state.observe(getViewLifecycleOwner(), state -> {
            section.getRoot().setVisibility(state.getStatus() == UiState.Status.UNAVAILABLE ? View.GONE : View.VISIBLE);
            section.relationState.render(state.getStatus(), () -> model.loadRelations(kind, false));
            if (state.getStatus() == UiState.Status.EMPTY) section.relationState.emptyMessage(R.string.details_relation_empty, R.string.details_relation_next);
            section.relationList.setVisibility(state.getStatus() == UiState.Status.CONTENT ? View.VISIBLE : View.GONE);
            if (state.getStatus() == UiState.Status.CONTENT) adapter.submit(state.getData());
        });
        Runnable paging = () -> {
            boolean loading = Boolean.TRUE.equals(group.loadingMore.getValue());
            section.relationProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
            section.relationMore.setVisibility(Boolean.TRUE.equals(group.hasMore.getValue()) ? View.VISIBLE : View.GONE);
            section.relationMore.setEnabled(!loading); section.relationMore.setText(Boolean.TRUE.equals(group.moreError.getValue()) ? R.string.catalog_retry : more);
        };
        group.hasMore.observe(getViewLifecycleOwner(), value -> paging.run());
        group.loadingMore.observe(getViewLifecycleOwner(), value -> paging.run());
        group.moreError.observe(getViewLifecycleOwner(), value -> paging.run());
        section.relationMore.setOnClickListener(v -> model.loadRelations(kind, true));
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override public void onDestroyView() {
        binding.teamsSection.relationList.setAdapter(null); binding.friendsSection.relationList.setAdapter(null); binding.enemiesSection.relationList.setAdapter(null);
        super.onDestroyView(); binding = null;
    }
}
