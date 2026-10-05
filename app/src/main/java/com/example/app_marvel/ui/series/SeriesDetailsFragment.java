package com.example.app_marvel.ui.series;

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
import com.example.app_marvel.databinding.FragmentSeriesDetailsBinding;
import com.example.app_marvel.ui.common.ComicVineNavigation;
import com.example.app_marvel.ui.common.UiState;
import com.example.app_marvel.ui.components.RelatedCharacterAdapter;
import com.example.app_marvel.databinding.ComponentDetailsRelationsBinding;
import com.google.android.material.button.MaterialButton;
import android.widget.LinearLayout;
import com.example.app_marvel.ui.components.MarvStateView;

public final class SeriesDetailsFragment extends Fragment {
    private FragmentSeriesDetailsBinding binding;
    private SeriesDetailsViewModel model;
    private boolean restoring;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup parent,@Nullable Bundle saved) {
        binding=FragmentSeriesDetailsBinding.inflate(inflater,parent,false);return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle saved) {
        var container=((MarvelApplication) requireActivity().getApplication()).getContainer();
        model=new ViewModelProvider(this,new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type,@NonNull CreationExtras extras) {
                if (type!=SeriesDetailsViewModel.class) throw new IllegalArgumentException("ViewModel não registrado");
                return type.cast(new SeriesDetailsViewModel(container.getCatalog(),container.getTranslations(),SavedStateHandleSupport.createSavedStateHandle(extras)));
            }
        }).get(SeriesDetailsViewModel.class);
        int restore=model.scroll();restoring=true;
        binding.seriesDetailsScroll.setOnScrollChangeListener((androidx.core.widget.NestedScrollView.OnScrollChangeListener)(v,x,y,oldX,oldY) -> { if (!restoring) model.scroll(y); });
        for (View heading:new View[]{binding.seriesDetailsHeading,binding.seriesDeckHeading,binding.seriesDescriptionHeading,binding.seriesCharactersSection.relationHeading,binding.seriesEpisodesHeading}) ViewCompat.setAccessibilityHeading(heading,true);
        model.detail().observe(getViewLifecycleOwner(),state -> {
            binding.seriesDetailsState.render(state.getStatus(),model::reload);
            binding.seriesDetailsContent.setVisibility(state.getStatus()==UiState.Status.CONTENT ? View.VISIBLE:View.GONE);
            if (state.getStatus()!=UiState.Status.CONTENT) return;
            var item=state.getData();binding.seriesDetailsHeading.setText(item.series.title);
            binding.seriesDetailsImage.setVisibility(item.series.imageUrl.isEmpty() ? View.GONE:View.VISIBLE);
            if (!item.series.imageUrl.isEmpty()) container.getImages().show(binding.seriesDetailsImage,item.series.imageUrl);
            text(binding.seriesDetailsYear,item.series.startYear.isEmpty() ? "":getString(R.string.series_year,item.series.startYear));
            text(binding.seriesDetailsCount,item.series.episodeCount>=0 ? getResources().getQuantityString(R.plurals.series_episodes,item.series.episodeCount,item.series.episodeCount):"");
            binding.seriesDetailsPublisher.setText(R.string.series_publisher);
            text(binding.seriesFirstEpisode,item.firstEpisode==null ? "":getString(R.string.series_first_episode,episodeLabel(item.firstEpisode)));
            text(binding.seriesLastEpisode,item.lastEpisode==null ? "":getString(R.string.series_last_episode,episodeLabel(item.lastEpisode)));
            binding.seriesDetailsSource.setVisibility(item.series.siteUrl.isEmpty() ? View.GONE:View.VISIBLE);
            binding.seriesDetailsSource.setOnClickListener(v -> ComicVineNavigation.open(requireContext(),item.series.siteUrl));
        });
        model.deck().observe(getViewLifecycleOwner(),state -> description(state,binding.seriesDeckSection,binding.seriesDeckState,binding.seriesDeck,"deck"));
        model.description().observe(getViewLifecycleOwner(),state -> {
            description(state,binding.seriesDescriptionSection,binding.seriesDescriptionState,binding.seriesDescription,"description");
            binding.seriesReadDescription.setVisibility(state.getStatus()==UiState.Status.EMPTY ? View.VISIBLE:View.GONE);
        });
        binding.seriesReadDescription.setOnClickListener(v -> model.translate("description"));
        relations("characters",binding.seriesCharactersSection,R.string.series_characters,R.string.issue_more_characters);
        model.episodes.observe(getViewLifecycleOwner(),state -> {
            binding.seriesEpisodesSection.setVisibility(state.getStatus()==UiState.Status.UNAVAILABLE ? View.GONE:View.VISIBLE);
            binding.seriesEpisodesState.render(state.getStatus(),() -> model.loadEpisodes(false));
            binding.seriesEpisodeList.setVisibility(state.getStatus()==UiState.Status.CONTENT ? View.VISIBLE:View.GONE);
            binding.seriesLoadedEpisodes.setVisibility(state.getStatus()==UiState.Status.CONTENT ? View.VISIBLE:View.GONE);
            if(state.getStatus()==UiState.Status.CONTENT) {
                binding.seriesEpisodeList.removeAllViews();
                var detail=model.detail().getValue().getData();
                binding.seriesLoadedEpisodes.setText(getString(R.string.series_loaded_episodes,state.getData().size(),detail.episodes.size()));
                for(var episode:state.getData()) episode(episode);
            }
        });
        Runnable paging=() -> {
            boolean loading=Boolean.TRUE.equals(model.episodesLoading.getValue());
            binding.seriesEpisodesProgress.setVisibility(loading ? View.VISIBLE:View.GONE);
            binding.seriesEpisodesMore.setVisibility(Boolean.TRUE.equals(model.episodesMore.getValue()) ? View.VISIBLE:View.GONE);
            binding.seriesEpisodesMore.setEnabled(!loading);
            binding.seriesEpisodesMore.setText(Boolean.TRUE.equals(model.episodesError.getValue()) ? R.string.catalog_retry:R.string.series_more_episodes);
        };
        model.episodesMore.observe(getViewLifecycleOwner(),v -> paging.run());model.episodesLoading.observe(getViewLifecycleOwner(),v -> paging.run());model.episodesError.observe(getViewLifecycleOwner(),v -> paging.run());
        binding.seriesEpisodesMore.setOnClickListener(v -> model.loadEpisodes(true));
        view.post(() -> { if (binding!=null) { ((MainActivity) requireActivity()).updateContentInsets();binding.seriesDetailsScroll.scrollTo(0,restore);restoring=false; } });
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
    private String episodeLabel(com.example.app_marvel.data.catalog.CatalogModels.Episode item) { return item.number.isEmpty() ? item.name:item.number+" · "+item.name; }
    private void episode(com.example.app_marvel.data.catalog.CatalogModels.Episode item) {
        var card=new com.google.android.material.card.MaterialCardView(requireContext());
        card.setRadius(dp(16));card.setCardElevation(0);card.setStrokeWidth(0);card.setCardBackgroundColor(requireContext().getColor(R.color.marvel_surface_raised));
        LinearLayout.LayoutParams margin=new LinearLayout.LayoutParams(-1,-2);margin.topMargin=dp(16);binding.seriesEpisodeList.addView(card,margin);
        LinearLayout content=new LinearLayout(requireContext());content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),dp(16),dp(16),dp(16));card.addView(content);
        TextView name=new TextView(requireContext());name.setTextAppearance(R.style.TextAppearance_Marvel_CatalogHeading);name.setTextSize(26);name.setText(item.name);ViewCompat.setAccessibilityHeading(name,true);content.addView(name,new LinearLayout.LayoutParams(-1,-2));
        if(!item.number.isEmpty()) episodeText(content,getString(R.string.series_episode_number,item.number));
        if(!item.airDate.isEmpty()) {
            String date=java.time.LocalDate.parse(item.airDate).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu",new java.util.Locale("pt","BR")));
            episodeText(content,getString(R.string.series_episode_air_date,date));
        }
        if(!item.siteUrl.isEmpty()) {
            MaterialButton link=(MaterialButton)getLayoutInflater().inflate(R.layout.component_issue_credit,content,false);
            link.setText(R.string.details_external);link.setContentDescription(getString(R.string.series_episode_external,item.name));link.setSingleLine(false);link.setMinHeight(dp(48));
            link.setOnClickListener(v -> ComicVineNavigation.open(requireContext(),item.siteUrl));content.addView(link,new LinearLayout.LayoutParams(-1,-2));
        }
    }
    private void episodeText(LinearLayout parent,String value) {
        TextView text=new TextView(requireContext());text.setTextAppearance(R.style.TextAppearance_Marvel_Caption);text.setText(value);
        LinearLayout.LayoutParams margin=new LinearLayout.LayoutParams(-1,-2);margin.topMargin=dp(8);parent.addView(text,margin);
    }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private void text(TextView label,String value) { label.setText(value);label.setVisibility(value.isEmpty() ? View.GONE:View.VISIBLE); }
    @Override public void onDestroyView() {
        if (binding!=null) { if (!restoring) model.scroll(binding.seriesDetailsScroll.getScrollY());binding.seriesCharactersSection.relationList.setAdapter(null); }
        super.onDestroyView();binding=null;
    }
}
