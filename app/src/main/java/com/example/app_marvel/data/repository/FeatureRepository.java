package com.example.app_marvel.data.repository;

import com.example.app_marvel.data.model.AppFeature;
import java.util.List;

public interface FeatureRepository {
    List<AppFeature> getHomeSections();
    boolean isAvailable(AppFeature feature);
}
