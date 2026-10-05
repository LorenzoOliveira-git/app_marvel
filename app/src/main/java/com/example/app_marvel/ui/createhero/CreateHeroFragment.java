package com.example.app_marvel.ui.createhero;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.graphics.Rect;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateHandleSupport;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;
import com.example.app_marvel.MainActivity;
import com.example.app_marvel.MarvelApplication;
import com.example.app_marvel.R;
import com.example.app_marvel.databinding.FragmentCreateHeroBinding;
import com.example.app_marvel.ui.common.UiState;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public final class CreateHeroFragment extends Fragment {
    private FragmentCreateHeroBinding binding;
    private CreateHeroViewModel model;
    private OnBackPressedCallback back;
    private boolean synchronizing;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
        binding = FragmentCreateHeroBinding.inflate(inflater, parent, false); return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        var container = ((MarvelApplication) requireActivity().getApplication()).getContainer();
        model = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type, @NonNull CreationExtras extras) {
                if (type != CreateHeroViewModel.class) throw new IllegalArgumentException("ViewModel não registrado");
                return type.cast(new CreateHeroViewModel(container.getCatalog(), container.getTranslations(), SavedStateHandleSupport.createSavedStateHandle(extras)));
            }
        }).get(CreateHeroViewModel.class);
        field(binding.heroName, binding.heroNameInput, "heroName");
        field(binding.heroRealName, binding.heroRealNameInput, "realName");
        field(binding.heroDescription, binding.heroDescriptionInput, "description");
        for (View heading : new View[]{binding.heroHeading, binding.heroNameLabel, binding.heroRealNameLabel, binding.heroOriginLabel, binding.heroPowersLabel, binding.heroDescriptionLabel}) ViewCompat.setAccessibilityHeading(heading, true);
        binding.heroOriginError.setVisibility(View.GONE); binding.heroPowerError.setVisibility(View.GONE);
        binding.heroBirthday.setOnClickListener(v -> chooseDate());
        binding.heroClearBirthday.setOnClickListener(v -> { model.text("birthday", ""); birthday(); }); birthday();
        binding.heroOrigin.setOnClickListener(v -> chooseOrigin());
        binding.heroOriginRetry.setOnClickListener(v -> model.loadOrigins());
        binding.heroPowerMore.setOnClickListener(v -> model.loadPowers());
        binding.heroNext.setOnClickListener(v -> next());
        binding.heroPrevious.setOnClickListener(v -> model.step(model.step() == 2 ? 0 : model.step() - 1));
        back = new OnBackPressedCallback(model.step() > 0) {
            @Override public void handleOnBackPressed() { model.step(model.step() - 1); }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), back);
        model.stepState().observe(getViewLifecycleOwner(), value -> renderStep());
        model.selectionState().observe(getViewLifecycleOwner(), value -> selections());
        model.origins().observe(getViewLifecycleOwner(), value -> catalogs());
        model.powers().observe(getViewLifecycleOwner(), value -> { powers(); catalogs(); });
        view.post(() -> { if (binding != null) ((MainActivity) requireActivity()).updateContentInsets(); });
    }
    private void field(TextInputEditText edit, TextInputLayout input, String key) {
        edit.setText(model.text(key));
        edit.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            public void onTextChanged(CharSequence text, int start, int before, int count) { model.text(key, text.toString()); input.setError(null); }
            public void afterTextChanged(Editable text) { }
        });
    }
    private void renderStep() {
        int step = model.step(); back.setEnabled(step > 0);
        binding.heroStep.setText(getString(R.string.hero_step, step + 1));
        int[] titles = {R.string.hero_identity_title, R.string.hero_abilities_title, R.string.hero_review_title};
        int[] guides = {R.string.hero_identity_guide, R.string.hero_abilities_guide, R.string.hero_review_guide};
        binding.heroHeading.setText(titles[step]); binding.heroGuide.setText(guides[step]);
        binding.heroMarv.setImageResource(step == 1 ? R.drawable.marv_thinking : R.drawable.marv_welcome);
        binding.heroIdentity.setVisibility(step == 0 ? View.VISIBLE : View.GONE);
        binding.heroAbilities.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
        binding.heroReviewSection.setVisibility(step == 2 ? View.VISIBLE : View.GONE);
        binding.heroNext.setVisibility(step == 2 ? View.GONE : View.VISIBLE);
        binding.heroNext.setText(step == 0 ? R.string.hero_next : R.string.hero_review);
        binding.heroPrevious.setVisibility(step > 0 ? View.VISIBLE : View.GONE);
        binding.heroPrevious.setText(step == 2 ? R.string.hero_edit : R.string.hero_previous);
        if (step == 1) model.loadChoices();
        if (step == 2) review();
        binding.heroScroll.post(() -> { if (binding != null) binding.heroScroll.smoothScrollTo(0, 0); });
        ViewCompat.setAccessibilityPaneTitle(binding.getRoot(), getString(titles[step]));
    }
    private void next() {
        if (!model.identityValid()) {
            model.step(0);
            if (model.text("heroName").trim().isEmpty()) binding.heroNameInput.setError(getString(R.string.hero_required));
            if (model.text("realName").trim().isEmpty()) binding.heroRealNameInput.setError(getString(R.string.hero_required));
            (model.text("heroName").trim().isEmpty() ? binding.heroName : binding.heroRealName).requestFocus(); return;
        }
        if (model.step() == 1) {
            List<String> missing = model.missing();
            binding.heroOriginError.setVisibility(missing.contains("origin") ? View.VISIBLE : View.GONE);
            binding.heroPowerError.setVisibility(missing.contains("powers") ? View.VISIBLE : View.GONE);
            binding.heroDescriptionInput.setError(missing.contains("description") ? getString(R.string.hero_required) : null);
            if (!missing.isEmpty()) {
                View target = missing.contains("origin") ? binding.heroOrigin : missing.contains("powers") ? binding.heroPowersLabel : binding.heroDescription;
                target.requestFocus(); target.requestRectangleOnScreen(new Rect(0, 0, target.getWidth(), target.getHeight()), false); return;
            }
        }
        InputMethodManager keyboard = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(binding.getRoot().getWindowToken(), 0);
        model.step(model.step() + 1);
    }
    private void chooseDate() {
        Calendar date = Calendar.getInstance();
        if (!model.text("birthday").isEmpty()) {
            String[] parts = model.text("birthday").split("-"); date.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
        }
        new DatePickerDialog(requireContext(), (picker, year, month, day) -> {
            model.text("birthday", String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day)); birthday();
        }, date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH)).show();
    }
    private String displayDate() {
        String value = model.text("birthday"); if (value.isEmpty()) return getString(R.string.hero_no_birthday);
        String[] parts = value.split("-"); return parts[2] + "/" + parts[1] + "/" + parts[0];
    }
    private void birthday() {
        binding.heroBirthday.setText(model.text("birthday").isEmpty() ? getString(R.string.hero_choose_date) : displayDate());
        binding.heroClearBirthday.setVisibility(model.text("birthday").isEmpty() ? View.GONE : View.VISIBLE);
    }
    private void chooseOrigin() {
        UiState<List<CreateHeroViewModel.Choice>> state = model.origins().getValue();
        if (state.getStatus() != UiState.Status.CONTENT) return;
        List<CreateHeroViewModel.Choice> choices = state.getData(); String[] labels = new String[choices.size()]; int selected = -1;
        for (int i = 0; i < choices.size(); i++) { labels[i] = choices.get(i).label; if (model.origin() != null && choices.get(i).id == model.origin().id) selected = i; }
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.hero_origin).setSingleChoiceItems(labels, selected, (dialog, index) -> {
            model.origin(choices.get(index)); binding.heroOriginError.setVisibility(View.GONE); dialog.dismiss();
        }).setNegativeButton(android.R.string.cancel, null).show();
    }
    private void selections() {
        var origin = model.origin(); binding.heroOrigin.setText(origin == null ? getString(R.string.hero_choose_origin) : origin.label);
        binding.heroSelectedCount.setText(getString(R.string.hero_selected_powers, model.selectedPowers().size()));
        binding.heroSelectedList.removeAllViews();
        for (var power : model.selectedPowers()) {
            MaterialButton remove = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            remove.setText(getString(R.string.hero_remove_power, power.label)); remove.setSingleLine(false); remove.setMinHeight(dp(48));
            remove.setOnClickListener(v -> model.power(power, false)); binding.heroSelectedList.addView(remove);
        }
        synchronizing = true;
        for (int i = 0; i < binding.heroPowerList.getChildCount(); i++) {
            MaterialCheckBox checkbox = (MaterialCheckBox) binding.heroPowerList.getChildAt(i);
            checkbox.setChecked(model.selected((Integer) checkbox.getTag()));
        }
        synchronizing = false;
        if (!model.selectedPowers().isEmpty()) binding.heroPowerError.setVisibility(View.GONE);
    }
    private void powers() {
        binding.heroPowerList.removeAllViews();
        for (var power : model.loadedPowers()) {
            MaterialCheckBox checkbox = new MaterialCheckBox(requireContext()); checkbox.setTag(power.id);
            checkbox.setText(power.label); checkbox.setTextAppearance(R.style.TextAppearance_Marvel_Body);
            checkbox.setTextColor(getResources().getColor(R.color.marvel_text, requireContext().getTheme()));
            checkbox.setMinHeight(dp(48)); checkbox.setSingleLine(false); checkbox.setChecked(model.selected(power.id));
            checkbox.setOnCheckedChangeListener((button, checked) -> { if (!synchronizing) model.power(power, checked); });
            binding.heroPowerList.addView(checkbox, new android.widget.LinearLayout.LayoutParams(-1, -2));
        }
    }
    private void catalogs() {
        var origin = model.origins().getValue().getStatus(); var power = model.powers().getValue().getStatus();
        catalogText(binding.heroOriginStatus, origin, R.string.hero_loading_origins);
        binding.heroOrigin.setEnabled(origin == UiState.Status.CONTENT);
        binding.heroOriginRetry.setVisibility(origin == UiState.Status.ERROR || origin == UiState.Status.EMPTY ? View.VISIBLE : View.GONE);
        catalogText(binding.heroPowerStatus, power, R.string.hero_loading_powers);
        if (power == UiState.Status.CONTENT) binding.heroPowerStatus.setText(getResources().getQuantityString(R.plurals.hero_loaded_powers, model.total(), model.loadedCount(), model.total()));
        boolean busy = origin == UiState.Status.LOADING || power == UiState.Status.LOADING;
        binding.heroCatalogProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        binding.heroPowerMore.setVisibility(model.hasMore() || power == UiState.Status.ERROR || power == UiState.Status.EMPTY ? View.VISIBLE : View.GONE);
        binding.heroPowerMore.setEnabled(power != UiState.Status.LOADING);
        binding.heroPowerMore.setText(power == UiState.Status.ERROR || power == UiState.Status.EMPTY ? R.string.catalog_retry : R.string.hero_more_powers);
    }
    private void catalogText(TextView view, UiState.Status status, int loading) {
        view.setVisibility(status == UiState.Status.CONTENT && loading == R.string.hero_loading_origins ? View.GONE : View.VISIBLE);
        view.setText(status == UiState.Status.ERROR ? R.string.hero_catalog_error : status == UiState.Status.EMPTY ? R.string.hero_catalog_empty : loading);
        ViewCompat.setAccessibilityLiveRegion(view, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);
    }
    private void review() {
        StringBuilder summary = new StringBuilder();
        row(summary, R.string.hero_name, model.text("heroName").trim()); row(summary, R.string.hero_real_name, model.text("realName").trim());
        row(summary, R.string.hero_birthday, displayDate()); row(summary, R.string.hero_origin, model.origin().label);
        StringBuilder powers = new StringBuilder(); for (var power : model.selectedPowers()) { if (powers.length() > 0) powers.append(", "); powers.append(power.label); }
        row(summary, R.string.hero_powers, powers.toString()); row(summary, R.string.hero_description, model.text("description").trim());
        binding.heroReviewData.setText(summary.toString());
    }
    private void row(StringBuilder output, int label, String value) {
        if (output.length() > 0) output.append("\n\n"); output.append(getString(R.string.hero_review_item, getString(label), value));
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
