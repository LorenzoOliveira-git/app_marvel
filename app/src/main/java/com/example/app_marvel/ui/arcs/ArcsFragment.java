package com.example.app_marvel.ui.arcs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.SavedStateHandleSupport;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.app_marvel.MainActivity;
import com.example.app_marvel.MarvelApplication;
import com.example.app_marvel.R;
import com.example.app_marvel.databinding.FragmentArcsBinding;
import com.example.app_marvel.ui.common.UiState;

public final class ArcsFragment extends Fragment {
    private FragmentArcsBinding binding;
    private ArcsViewModel model;
    private boolean restored;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup parent,@Nullable Bundle state) {
        binding=FragmentArcsBinding.inflate(inflater,parent,false);return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state) {
        restored=false;
        view.post(() -> { if (binding!=null) ((MainActivity) requireActivity()).updateContentInsets(); });
        ViewCompat.setAccessibilityHeading(binding.arcsHeading,true);
        var container=((MarvelApplication) requireActivity().getApplication()).getContainer();
        model=new ViewModelProvider(requireActivity(),new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type,@NonNull CreationExtras extras) {
                if (type!=ArcsViewModel.class) throw new IllegalArgumentException("ViewModel não registrado");
                return type.cast(new ArcsViewModel(container.getCatalog(),SavedStateHandleSupport.createSavedStateHandle(extras)));
            }
        }).get(ArcsViewModel.class);
        StoryArcAdapter adapter=new StoryArcAdapter(container.getImages(),id -> ((MainActivity) requireActivity()).openArc(id));
        binding.arcsList.setLayoutManager(new LinearLayoutManager(requireContext()));binding.arcsList.setAdapter(adapter);
        binding.arcSearch.setText(model.draft());
        binding.arcSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            public void onTextChanged(CharSequence s,int start,int before,int count) { model.draft(s.toString()); }
            public void afterTextChanged(Editable value) { }
        });
        Runnable search=() -> { model.search(binding.arcSearch.getText().toString());hideKeyboard();binding.arcsScroll.scrollTo(0,0); };
        binding.arcsSearchButton.setOnClickListener(v -> search.run());
        binding.arcSearch.setOnEditorActionListener((v,action,event) -> { if (action==EditorInfo.IME_ACTION_SEARCH) { search.run();return true; }return false; });
        binding.arcsClear.setOnClickListener(v -> { model.clear();binding.arcSearch.setText("");hideKeyboard();binding.arcsScroll.scrollTo(0,0); });
        binding.arcsAscending.setOnClickListener(v -> { model.order(false);binding.arcsScroll.scrollTo(0,0); });
        binding.arcsDescending.setOnClickListener(v -> { model.order(true);binding.arcsScroll.scrollTo(0,0); });
        model.state().observe(getViewLifecycleOwner(),value -> {
            binding.arcsState.render(value.getStatus(),model::reload);
            if (value.getStatus()==UiState.Status.EMPTY) binding.arcsState.emptyMessage(R.string.arcs_empty_title,R.string.arcs_empty_body);
            boolean content=value.getStatus()==UiState.Status.CONTENT;
            binding.arcsList.setVisibility(content ? View.VISIBLE:View.GONE);
            binding.arcsCount.setVisibility(content ? View.VISIBLE:View.GONE);
            binding.arcsClear.setVisibility(model.query().isEmpty() ? View.GONE:View.VISIBLE);
            binding.arcsAscending.setEnabled(model.descending());binding.arcsDescending.setEnabled(!model.descending());
            binding.arcsOrderLabel.setText(model.descending() ? R.string.arcs_order_descending:R.string.arcs_order_ascending);
            if (!content) { adapter.submitList(java.util.Collections.emptyList());return; }
            binding.arcsCount.setText(getString(R.string.arcs_loaded,value.getData().size()));
            adapter.submitList(value.getData(),() -> {
                if (binding!=null && !restored) { restored=true;binding.arcsScroll.post(() -> { if (binding!=null) binding.arcsScroll.scrollTo(0,model.scroll()); }); }
            });
        });
        Runnable paging=() -> {
            boolean loading=Boolean.TRUE.equals(model.loadingMore().getValue()),error=Boolean.TRUE.equals(model.moreError().getValue());
            binding.arcsProgress.setVisibility(loading ? View.VISIBLE:View.GONE);
            binding.arcsMore.setVisibility(Boolean.TRUE.equals(model.hasMore().getValue()) ? View.VISIBLE:View.GONE);
            binding.arcsMore.setEnabled(!loading);binding.arcsMore.setText(error ? R.string.catalog_retry:R.string.catalog_load_more);
            binding.arcsPageError.setVisibility(error ? View.VISIBLE:View.GONE);
        };
        model.hasMore().observe(getViewLifecycleOwner(),v -> paging.run());model.loadingMore().observe(getViewLifecycleOwner(),v -> paging.run());model.moreError().observe(getViewLifecycleOwner(),v -> paging.run());
        binding.arcsMore.setOnClickListener(v -> model.more());
    }
    private void hideKeyboard() {
        binding.arcSearch.clearFocus();
        ((android.view.inputmethod.InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(binding.getRoot().getWindowToken(),0);
    }
    @Override public void onDestroyView() { model.scroll(binding.arcsScroll.getScrollY());binding.arcsList.setAdapter(null);super.onDestroyView();binding=null; }
}
