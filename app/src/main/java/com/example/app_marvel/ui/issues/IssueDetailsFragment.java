package com.example.app_marvel.ui.issues;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
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
import com.example.app_marvel.data.catalog.CatalogModels.Credit;
import com.example.app_marvel.databinding.ComponentDetailsRelationsBinding;
import com.example.app_marvel.databinding.FragmentIssueDetailsBinding;
import com.example.app_marvel.ui.common.ComicVineNavigation;
import com.example.app_marvel.ui.common.UiState;
import com.example.app_marvel.ui.components.MarvStateView;
import com.example.app_marvel.ui.components.RelatedCharacterAdapter;
import com.google.android.material.button.MaterialButton;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class IssueDetailsFragment extends Fragment {
    private FragmentIssueDetailsBinding binding;
    private IssueDetailsViewModel model;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle saved) {
        binding = FragmentIssueDetailsBinding.inflate(inflater,parent,false); return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        view.post(() -> { if (binding != null) ((MainActivity) requireActivity()).updateContentInsets(); });
        for (View heading : new View[]{binding.issueHeading,binding.issueDeckHeading,binding.issueDescriptionHeading})
            ViewCompat.setAccessibilityHeading(heading,true);
        var container = ((MarvelApplication) requireActivity().getApplication()).getContainer();
        model = new ViewModelProvider(this,new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type, @NonNull CreationExtras extras) {
                if (type != IssueDetailsViewModel.class) throw new IllegalArgumentException("ViewModel não registrado");
                return type.cast(new IssueDetailsViewModel(container.getCatalog(),container.getTranslations(),SavedStateHandleSupport.createSavedStateHandle(extras)));
            }
        }).get(IssueDetailsViewModel.class);
        model.detail().observe(getViewLifecycleOwner(),state -> {
            binding.issueState.render(state.getStatus(),model::reload);
            binding.issueContent.setVisibility(state.getStatus() == UiState.Status.CONTENT ? View.VISIBLE : View.GONE);
            if (state.getStatus() != UiState.Status.CONTENT) return;
            var detail = state.getData(); var issue = detail.issue;
            binding.issueHeading.setText(issue.title); container.getImages().show(binding.issueCover,issue.imageUrl);
            binding.issueName.setText(detail.name); binding.issueName.setVisibility(detail.name.isEmpty() ? View.GONE : View.VISIBLE);
            binding.issueVolume.setText(getString(R.string.comics_volume_meta,issue.volume));
            date(binding.issueStoreDate,issue.publicationDate,R.string.comics_store_date);
            date(binding.issueCoverDate,detail.coverDate,R.string.history_cover_date);
            binding.issueSource.setVisibility(issue.siteUrl.isEmpty() ? View.GONE : View.VISIBLE);
            binding.issueSource.setOnClickListener(v -> ComicVineNavigation.open(requireContext(),issue.siteUrl));
            binding.issueVolumeSource.setVisibility(detail.volumeSiteUrl.isEmpty() ? View.GONE : View.VISIBLE);
            binding.issueVolumeSource.setOnClickListener(v -> ComicVineNavigation.open(requireContext(),detail.volumeSiteUrl));
            binding.issueCredits.removeAllViews();
            credits(R.string.issue_creators,detail.creators,true); credits(R.string.issue_arcs,detail.arcs,false);
            credits(R.string.issue_locations,detail.locations,false); credits(R.string.issue_objects,detail.objects,false);
            credits(R.string.issue_concepts,detail.concepts,false);
        });
        model.deck().observe(getViewLifecycleOwner(),state -> description(state,binding.issueDeckSection,binding.issueDeckState,binding.issueDeck,"deck"));
        model.description().observe(getViewLifecycleOwner(),state -> description(state,binding.issueDescriptionSection,binding.issueDescriptionState,binding.issueDescription,"description"));
        relations("characters",binding.issueCharactersSection,R.string.issue_characters,R.string.issue_more_characters);
        relations("teams",binding.issueTeamsSection,R.string.issue_teams,R.string.issue_more_teams);
    }
    private void date(TextView label,String original,int format) {
        label.setText("");
        if (original.matches("\\d{4}-\\d{2}-\\d{2}")) try {
            SimpleDateFormat source = new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT); source.setLenient(false);
            String date = new SimpleDateFormat("dd MMM yyyy",new Locale("pt","BR")).format(source.parse(original));
            label.setText(getString(format,date));
        } catch (Exception invalid) { /* Datas opcionais não são presumidas. */ }
        label.setVisibility(label.getText().length() == 0 ? View.GONE : View.VISIBLE);
    }
    private void description(UiState<String> state,View section,MarvStateView feedback,TextView label,String field) {
        section.setVisibility(state.getStatus() == UiState.Status.UNAVAILABLE ? View.GONE : View.VISIBLE);
        feedback.render(state.getStatus(),() -> model.translate(field));
        label.setText(state.getStatus() == UiState.Status.CONTENT ? state.getData() : "");
        label.setVisibility(state.getStatus() == UiState.Status.CONTENT ? View.VISIBLE : View.GONE);
    }
    private void relations(String kind,ComponentDetailsRelationsBinding section,int heading,int more) {
        boolean team = kind.equals("teams"); var group = model.group(kind);
        var images = ((MarvelApplication) requireActivity().getApplication()).getContainer().getImages();
        RelatedCharacterAdapter adapter = new RelatedCharacterAdapter(images,team,item -> {
            if (team) ComicVineNavigation.open(requireContext(),item.siteUrl);
            else ((MainActivity) requireActivity()).openCharacter(item.id);
        });
        section.relationHeading.setText(heading); ViewCompat.setAccessibilityHeading(section.relationHeading,true);
        section.relationList.setLayoutManager(new LinearLayoutManager(requireContext(),LinearLayoutManager.HORIZONTAL,false));
        section.relationList.setAdapter(adapter);
        group.state.observe(getViewLifecycleOwner(),state -> {
            section.getRoot().setVisibility(state.getStatus() == UiState.Status.UNAVAILABLE ? View.GONE : View.VISIBLE);
            section.relationState.render(state.getStatus(),() -> model.load(kind,false));
            if (state.getStatus() == UiState.Status.EMPTY) section.relationState.emptyMessage(R.string.details_relation_empty,R.string.details_relation_next);
            section.relationList.setVisibility(state.getStatus() == UiState.Status.CONTENT ? View.VISIBLE : View.GONE);
            if (state.getStatus() == UiState.Status.CONTENT) adapter.submit(state.getData());
        });
        Runnable paging = () -> {
            boolean loading = Boolean.TRUE.equals(group.loading.getValue());
            section.relationProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
            section.relationMore.setVisibility(Boolean.TRUE.equals(group.hasMore.getValue()) ? View.VISIBLE : View.GONE);
            section.relationMore.setEnabled(!loading); section.relationMore.setText(Boolean.TRUE.equals(group.error.getValue()) ? R.string.catalog_retry : more);
        };
        group.hasMore.observe(getViewLifecycleOwner(),v -> paging.run()); group.loading.observe(getViewLifecycleOwner(),v -> paging.run()); group.error.observe(getViewLifecycleOwner(),v -> paging.run());
        section.relationMore.setOnClickListener(v -> model.load(kind,true));
    }
    private void credits(int heading,List<Credit> items,boolean creators) {
        if (items.isEmpty()) return;
        TextView title = new TextView(requireContext()); title.setTextAppearance(R.style.TextAppearance_Marvel_CatalogHeading); title.setText(heading);
        ViewCompat.setAccessibilityHeading(title,true);
        LinearLayout.LayoutParams margin = new LinearLayout.LayoutParams(-1,-2); margin.topMargin = dp(40); margin.bottomMargin = dp(16);
        binding.issueCredits.addView(title,margin);
        for (Credit credit : items) {
            String label = credit.reference.name;
            String roles = creators ? roles(credit.role) : "";
            if (!roles.isEmpty()) label = getString(R.string.issue_creator_role,label,roles);
            if (credit.siteUrl.isEmpty()) {
                TextView name = new TextView(requireContext()); name.setTextAppearance(R.style.TextAppearance_Marvel_Body); name.setText(label);
                name.setTextColor(getResources().getColor(R.color.marvel_text,null)); binding.issueCredits.addView(name,new LinearLayout.LayoutParams(-1,-2));
            } else {
                MaterialButton button = (MaterialButton) getLayoutInflater().inflate(R.layout.component_issue_credit,binding.issueCredits,false);
                button.setTextAppearance(R.style.TextAppearance_Marvel_CatalogFilter); button.setText(getString(R.string.issue_credit_external,label));
                button.setTextColor(getResources().getColor(R.color.marvel_text,null)); button.setAllCaps(false); button.setSingleLine(false);
                button.setMinHeight(dp(48)); button.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);
                button.setOnClickListener(v -> ComicVineNavigation.open(requireContext(),credit.siteUrl));
                binding.issueCredits.addView(button,new LinearLayout.LayoutParams(-1,-2));
            }
        }
    }
    private String roles(String original) {
        List<String> result = new ArrayList<>();
        for (String token : original.split(",")) {
            int resource;
            switch (token.trim().toLowerCase(Locale.ROOT)) {
                case "artist": resource = R.string.issue_role_artist; break;
                case "cover": resource = R.string.issue_role_cover; break;
                case "writer": resource = R.string.issue_role_writer; break;
                case "inker": resource = R.string.issue_role_inker; break;
                case "penciler": resource = R.string.issue_role_penciler; break;
                case "letterer": resource = R.string.issue_role_letterer; break;
                case "colorist": resource = R.string.issue_role_colorist; break;
                case "editor": resource = R.string.issue_role_editor; break;
                default: continue;
            }
            result.add(getString(resource));
        }
        return String.join(", ",result);
    }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    @Override public void onDestroyView() {
        binding.issueCharactersSection.relationList.setAdapter(null); binding.issueTeamsSection.relationList.setAdapter(null);
        super.onDestroyView(); binding = null;
    }
}
