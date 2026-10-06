package com.example.app_marvel.ui.movies;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.example.app_marvel.databinding.FragmentMovieDetailsBinding;
import com.example.app_marvel.ui.common.ComicVineNavigation;
import com.example.app_marvel.ui.common.UiState;
import com.example.app_marvel.ui.components.RelatedCharacterAdapter;
import com.example.app_marvel.databinding.ComponentDetailsRelationsBinding;
import com.example.app_marvel.data.catalog.CatalogModels.Credit;
import com.google.android.material.button.MaterialButton;
import android.widget.LinearLayout;
import java.util.List;
import com.example.app_marvel.ui.components.MarvStateView;

public final class MovieDetailsFragment extends Fragment {
    private FragmentMovieDetailsBinding binding;
    private MovieDetailsViewModel model;
    private boolean restoring;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup parent,@Nullable Bundle saved) {
        binding=FragmentMovieDetailsBinding.inflate(inflater,parent,false);return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle saved) {
        var container=((MarvelApplication) requireActivity().getApplication()).getContainer();
        model=new ViewModelProvider(this,new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type,@NonNull CreationExtras extras) {
                if (type!=MovieDetailsViewModel.class) throw new IllegalArgumentException("ViewModel não registrado");
                return type.cast(new MovieDetailsViewModel(container.getCatalog(),container.getTranslations(),SavedStateHandleSupport.createSavedStateHandle(extras)));
            }
        }).get(MovieDetailsViewModel.class);
        int restore=model.scroll();restoring=true;
        binding.movieDetailsScroll.setOnScrollChangeListener((androidx.core.widget.NestedScrollView.OnScrollChangeListener)(v,x,y,oldX,oldY) -> { if (!restoring) model.scroll(y); });
        for (View heading:new View[]{binding.movieDetailsHeading,binding.movieDeckHeading,binding.movieDescriptionHeading,binding.movieCharactersSection.relationHeading,binding.movieTeamsSection.relationHeading}) ViewCompat.setAccessibilityHeading(heading,true);
        model.detail().observe(getViewLifecycleOwner(),state -> {
            binding.movieDetailsState.render(state.getStatus(),model::reload);
            binding.movieDetailsContent.setVisibility(state.getStatus()==UiState.Status.CONTENT ? View.VISIBLE:View.GONE);
            if (state.getStatus()!=UiState.Status.CONTENT) return;
            var item=state.getData();binding.movieDetailsHeading.setText(item.movie.title);
            binding.movieDetailsImage.setVisibility(item.movie.imageUrl.isEmpty() ? View.GONE:View.VISIBLE);
            if (!item.movie.imageUrl.isEmpty()) container.getImages().show(binding.movieDetailsImage,item.movie.imageUrl);
            text(binding.movieDetailsRuntime,item.movie.runtime>0 ? getString(R.string.movies_runtime,item.movie.runtime):"");
            text(binding.movieDetailsRating,item.rating.isEmpty() ? "":getString(R.string.movie_rating,item.rating));
            text(binding.movieDetailsDistributor,item.distributor.isEmpty() ? "":getString(R.string.movie_distributor,item.distributor));
            binding.movieCredits.removeAllViews();
            credits(R.string.movie_studios,item.studios);credits(R.string.movie_producers,item.producers);credits(R.string.movie_writers,item.writers);
            credits(R.string.issue_locations,item.locations);credits(R.string.issue_objects,item.objects);credits(R.string.issue_concepts,item.concepts);
            binding.movieDetailsSource.setVisibility(item.movie.siteUrl.isEmpty() ? View.GONE:View.VISIBLE);
            binding.movieDetailsSource.setOnClickListener(v -> ComicVineNavigation.open(requireContext(),item.movie.siteUrl));
        });
        model.deck().observe(getViewLifecycleOwner(),state -> description(state,binding.movieDeckSection,binding.movieDeckState,binding.movieDeck,"deck"));
        model.description().observe(getViewLifecycleOwner(),state -> {
            description(state,binding.movieDescriptionSection,binding.movieDescriptionState,binding.movieDescription,"description");
            binding.movieReadDescription.setVisibility(state.getStatus()==UiState.Status.EMPTY ? View.VISIBLE:View.GONE);
        });
        binding.movieReadDescription.setOnClickListener(v -> model.translate("description"));
        relations("characters",binding.movieCharactersSection,R.string.movie_characters,R.string.issue_more_characters);
        relations("teams",binding.movieTeamsSection,R.string.movie_teams,R.string.issue_more_teams);
        view.post(() -> { if (binding!=null) { ((MainActivity) requireActivity()).updateContentInsets();binding.movieDetailsScroll.scrollTo(0,restore);restoring=false; } });
    }
    private void description(UiState<String> state,View section,MarvStateView feedback,TextView text,String field) {
        section.setVisibility(state.getStatus()==UiState.Status.UNAVAILABLE ? View.GONE:View.VISIBLE);
        feedback.setVisibility(state.getStatus()==UiState.Status.EMPTY ? View.GONE:View.VISIBLE);
        if (state.getStatus()!=UiState.Status.EMPTY) feedback.render(state.getStatus(),() -> model.translate(field));
        text.setText(state.getStatus()==UiState.Status.CONTENT ? state.getData():"");
        text.setVisibility(state.getStatus()==UiState.Status.CONTENT ? View.VISIBLE:View.GONE);
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
    private void credits(int heading,List<Credit> items) {
        if (items.isEmpty()) return;
        TextView title=new TextView(requireContext());title.setTextAppearance(R.style.TextAppearance_Marvel_CatalogHeading);title.setText(heading);
        ViewCompat.setAccessibilityHeading(title,true);LinearLayout.LayoutParams margin=new LinearLayout.LayoutParams(-1,-2);margin.topMargin=dp(32);margin.bottomMargin=dp(16);
        binding.movieCredits.addView(title,margin);
        for (Credit credit:items) {
            if (credit.siteUrl.isEmpty()) {
                TextView name=new TextView(requireContext());name.setTextAppearance(R.style.TextAppearance_Marvel_Body);name.setText(credit.reference.name);
                name.setTextColor(requireContext().getColor(R.color.marvel_text));binding.movieCredits.addView(name,new LinearLayout.LayoutParams(-1,-2));
            } else {
                MaterialButton button=(MaterialButton)getLayoutInflater().inflate(R.layout.component_issue_credit,binding.movieCredits,false);
                button.setTextAppearance(R.style.TextAppearance_Marvel_CatalogFilter);button.setText(getString(R.string.issue_credit_external,credit.reference.name));
                button.setContentDescription(getString(R.string.movie_credit_external,credit.reference.name));button.setSingleLine(false);button.setMinHeight(dp(52));
                button.setOnClickListener(v -> ComicVineNavigation.open(requireContext(),credit.siteUrl));binding.movieCredits.addView(button,new LinearLayout.LayoutParams(-1,-2));
            }
        }
    }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private void text(TextView label,String value) { label.setText(value);label.setVisibility(value.isEmpty() ? View.GONE:View.VISIBLE); }
    @Override public void onDestroyView() {
        if (binding!=null) { if (!restoring) model.scroll(binding.movieDetailsScroll.getScrollY());binding.movieCharactersSection.relationList.setAdapter(null);binding.movieTeamsSection.relationList.setAdapter(null); }
        super.onDestroyView();binding=null;
    }
}
