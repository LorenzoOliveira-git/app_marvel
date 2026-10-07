package com.example.app_marvel.ui.arcs;

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
import com.example.app_marvel.databinding.FragmentArcDetailsBinding;
import com.example.app_marvel.ui.common.ComicVineNavigation;
import com.example.app_marvel.ui.common.UiState;
import com.example.app_marvel.ui.components.AppearanceAdapter;
import com.example.app_marvel.ui.components.MarvStateView;

public final class ArcDetailsFragment extends Fragment {
    private FragmentArcDetailsBinding binding;
    private ArcDetailsViewModel model;
    private boolean restoring;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup parent,@Nullable Bundle saved) {
        binding=FragmentArcDetailsBinding.inflate(inflater,parent,false);return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle saved) {
        var container=((MarvelApplication) requireActivity().getApplication()).getContainer();
        model=new ViewModelProvider(this,new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type,@NonNull CreationExtras extras) {
                if (type!=ArcDetailsViewModel.class) throw new IllegalArgumentException("ViewModel não registrado");
                return type.cast(new ArcDetailsViewModel(container.getCatalog(),container.getTranslations(),SavedStateHandleSupport.createSavedStateHandle(extras)));
            }
        }).get(ArcDetailsViewModel.class);
        int restore=model.scroll();restoring=true;
        binding.arcDetailsScroll.setOnScrollChangeListener((androidx.core.widget.NestedScrollView.OnScrollChangeListener)(v,x,y,oldX,oldY) -> { if (!restoring) model.scroll(y); });
        for (View heading:new View[]{binding.arcDetailsHeading,binding.arcDeckHeading,binding.arcDescriptionHeading,binding.arcIssuesHeading}) ViewCompat.setAccessibilityHeading(heading,true);
        AppearanceAdapter adapter=new AppearanceAdapter(container.getImages());adapter.openWith(id -> ((MainActivity) requireActivity()).openIssue(id));
        binding.arcIssuesList.setLayoutManager(new LinearLayoutManager(requireContext()));binding.arcIssuesList.setNestedScrollingEnabled(false);binding.arcIssuesList.setAdapter(adapter);
        model.detail().observe(getViewLifecycleOwner(),state -> {
            binding.arcDetailsState.render(state.getStatus(),model::reload);
            binding.arcDetailsContent.setVisibility(state.getStatus()==UiState.Status.CONTENT ? View.VISIBLE:View.GONE);
            if (state.getStatus()!=UiState.Status.CONTENT) return;
            var item=state.getData();binding.arcDetailsHeading.setText(item.arc.name);
            binding.arcDetailsImage.setVisibility(item.arc.imageUrl.isEmpty() ? View.GONE:View.VISIBLE);
            if (!item.arc.imageUrl.isEmpty()) container.getImages().show(binding.arcDetailsImage,item.arc.imageUrl);
            binding.arcReferenceCount.setText(getString(R.string.arc_reference_count,item.issues.size()));
            binding.arcReferenceCount.setVisibility(item.issues.isEmpty() ? View.GONE:View.VISIBLE);
        });
        model.deck().observe(getViewLifecycleOwner(),state -> description(state,binding.arcDeckSection,binding.arcDeckState,binding.arcDeck,"deck"));
        model.description().observe(getViewLifecycleOwner(),state -> {
            description(state,binding.arcDescriptionSection,binding.arcDescriptionState,binding.arcDescription,"description");
            binding.arcReadDescription.setVisibility(state.getStatus()==UiState.Status.EMPTY ? View.VISIBLE:View.GONE);
        });
        binding.arcReadDescription.setOnClickListener(v -> model.translate("description"));
        model.issues().observe(getViewLifecycleOwner(),state -> {
            binding.arcIssuesSection.setVisibility(state.getStatus()==UiState.Status.UNAVAILABLE ? View.GONE:View.VISIBLE);
            binding.arcIssuesState.render(state.getStatus(),() -> model.load(false));
            if (state.getStatus()==UiState.Status.EMPTY) binding.arcIssuesState.emptyMessage(R.string.details_relation_empty,R.string.details_relation_next);
            binding.arcIssuesList.setVisibility(state.getStatus()==UiState.Status.CONTENT ? View.VISIBLE:View.GONE);
            if (state.getStatus()==UiState.Status.CONTENT) adapter.submit(state.getData(),Boolean.TRUE.equals(model.hasMore().getValue()));
        });
        Runnable paging=() -> {
            boolean loading=Boolean.TRUE.equals(model.loading().getValue());
            binding.arcIssuesProgress.setVisibility(loading ? View.VISIBLE:View.GONE);
            binding.arcIssuesMore.setVisibility(Boolean.TRUE.equals(model.hasMore().getValue()) ? View.VISIBLE:View.GONE);
            binding.arcIssuesMore.setEnabled(!loading);binding.arcIssuesMore.setText(Boolean.TRUE.equals(model.error().getValue()) ? R.string.catalog_retry:R.string.arc_more_issues);
        };
        model.hasMore().observe(getViewLifecycleOwner(),v -> paging.run());model.loading().observe(getViewLifecycleOwner(),v -> paging.run());model.error().observe(getViewLifecycleOwner(),v -> paging.run());
        binding.arcIssuesMore.setOnClickListener(v -> model.load(true));
        view.post(() -> { if (binding!=null) { ((MainActivity) requireActivity()).updateContentInsets();binding.arcDetailsScroll.scrollTo(0,restore);restoring=false; } });
    }
    private void description(UiState<String> state,View section,MarvStateView feedback,TextView text,String field) {
        section.setVisibility(state.getStatus()==UiState.Status.UNAVAILABLE ? View.GONE:View.VISIBLE);
        feedback.setVisibility(state.getStatus()==UiState.Status.EMPTY ? View.GONE:View.VISIBLE);
        if (state.getStatus()!=UiState.Status.EMPTY) feedback.render(state.getStatus(),() -> model.translate(field));
        text.setText(state.getStatus()==UiState.Status.CONTENT ? state.getData():"");
        text.setVisibility(state.getStatus()==UiState.Status.CONTENT ? View.VISIBLE:View.GONE);
    }
    @Override public void onDestroyView() {
        if (binding!=null) { if (!restoring) model.scroll(binding.arcDetailsScroll.getScrollY());binding.arcIssuesList.setAdapter(null); }
        super.onDestroyView();binding=null;
    }
}
