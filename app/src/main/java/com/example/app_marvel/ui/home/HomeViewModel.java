package com.example.app_marvel.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.app_marvel.data.model.AppFeature;
import com.example.app_marvel.data.repository.FeatureRepository;
import com.example.app_marvel.ui.common.UiState;
import java.util.List;

public final class HomeViewModel extends ViewModel {
    private final MutableLiveData<UiState<List<AppFeature>>> state;

    public HomeViewModel(FeatureRepository repository) {
        state = new MutableLiveData<>(UiState.content(repository.getHomeSections()));
    }

    public LiveData<UiState<List<AppFeature>>> getState() {
        return state;
    }
}
