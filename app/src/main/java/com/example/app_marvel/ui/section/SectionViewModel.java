package com.example.app_marvel.ui.section;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.app_marvel.data.model.AppFeature;
import com.example.app_marvel.data.repository.FeatureRepository;
import com.example.app_marvel.ui.common.UiState;

public final class SectionViewModel extends ViewModel {
    private final MutableLiveData<UiState<AppFeature>> state;

    public SectionViewModel(FeatureRepository repository, AppFeature feature) {
        state = new MutableLiveData<>(repository.isAvailable(feature)
                ? UiState.content(feature) : UiState.unavailable());
    }

    public LiveData<UiState<AppFeature>> getState() {
        return state;
    }
}
