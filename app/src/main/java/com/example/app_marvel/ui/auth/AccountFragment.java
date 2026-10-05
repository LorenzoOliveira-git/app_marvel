package com.example.app_marvel.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.SavedStateHandleSupport;
import androidx.lifecycle.viewmodel.CreationExtras;
import androidx.lifecycle.ViewModelProvider;
import com.example.app_marvel.MainActivity;
import com.example.app_marvel.MarvelApplication;
import com.example.app_marvel.R;
import com.example.app_marvel.data.auth.AuthRepository;
import com.example.app_marvel.databinding.FragmentAccountBinding;

/** Conta Firebase e acesso à coleção privada. */
public final class AccountFragment extends Fragment {
    private FragmentAccountBinding binding;
    private AccountViewModel model;
    private boolean syncing;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle saved) {
        binding = FragmentAccountBinding.inflate(inflater, parent, false); return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        super.onViewCreated(view, saved);
        AuthRepository repository = ((MarvelApplication) requireActivity().getApplication()).getContainer().getAuth();
        model = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type, @NonNull CreationExtras extras) {
                if (type != AccountViewModel.class) throw new IllegalArgumentException("ViewModel não registrado");
                return type.cast(new AccountViewModel(repository, SavedStateHandleSupport.createSavedStateHandle(extras)));
            }
        }).get(AccountViewModel.class);
        ViewCompat.setAccessibilityHeading(binding.heading, true);
        ViewCompat.setAccessibilityHeading(binding.profileNameLabel, true);
        binding.profileEditName.setOnClickListener(v -> model.startEdit());
        binding.profileSaveName.setOnClickListener(v -> model.save());
        binding.profileCancelName.setOnClickListener(v -> cancelEdit());
        binding.profileName.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            public void onTextChanged(CharSequence s,int start,int before,int count) { if (!syncing) model.name(s.toString()); }
            public void afterTextChanged(android.text.Editable text) { }
        });
        model.edit().observe(getViewLifecycleOwner(), this::renderEdit);
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                var state = model.edit().getValue(); if (state.busy) return;
                if (state.editing) cancelEdit();
                else { setEnabled(false); requireActivity().getOnBackPressedDispatcher().onBackPressed(); setEnabled(true); }
            }
        });
        binding.accountMyHeroes.setOnClickListener(v -> androidx.navigation.fragment.NavHostFragment.findNavController(this).navigate(R.id.myHeroesFragment));
        model.getSession().observe(getViewLifecycleOwner(), session -> {
            boolean signed = session.isAuthenticated();
            binding.accountMyHeroes.setVisibility(signed ? View.VISIBLE : View.GONE);
            binding.heading.setText(signed ? R.string.account_title : R.string.account_signed_out);
            binding.name.setText(session.getName()); binding.email.setText(session.getEmail());
            binding.name.setVisibility(signed && !session.getName().isEmpty() ? View.VISIBLE : View.GONE);
            binding.email.setVisibility(signed ? View.VISIBLE : View.GONE);
            binding.message.setVisibility(signed ? View.GONE : View.VISIBLE);
            binding.action.setText(signed ? R.string.account_sign_out : R.string.auth_sign_in);
            renderEdit(model.edit().getValue());
            binding.action.setOnClickListener(v -> {
                if (signed) { model.signOut(); ((MainActivity) requireActivity()).leaveAccount(); }
                else ((MainActivity) requireActivity()).openLogin();
            });
        });
        view.post(() -> { if (binding != null) ((MainActivity) requireActivity()).updateContentInsets(); });
    }
    private void cancelEdit() {
        if (model.edit().getValue().busy) return;
        if (model.name().equals(model.getSession().getValue().getName())) { model.cancel(); return; }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.my_heroes_discard_title).setMessage(R.string.my_heroes_discard_body)
            .setPositiveButton(R.string.my_heroes_discard, (dialog,which) -> model.cancel()).setNegativeButton(R.string.catalog_cancel, null).show();
    }
    private void renderEdit(AccountViewModel.EditState state) {
        boolean signed = model.getSession().getValue().isAuthenticated();
        binding.profileEditor.setVisibility(signed && state.editing ? View.VISIBLE : View.GONE);
        binding.profileEditName.setVisibility(signed && !state.editing ? View.VISIBLE : View.GONE);
        binding.profileName.setEnabled(!state.busy); binding.profileSaveName.setEnabled(!state.busy); binding.profileCancelName.setEnabled(!state.busy);
        binding.accountMyHeroes.setEnabled(!state.busy); binding.action.setEnabled(!state.busy);
        binding.profileProgress.setVisibility(signed && state.busy ? View.VISIBLE : View.GONE);
        int message = state.busy ? R.string.profile_name_saving : state.saved ? R.string.profile_name_saved : 0;
        if (state.error != null) switch (state.error) {
            case INVALID: message = R.string.profile_name_invalid; break;
            case CREDENTIALS: message = R.string.profile_name_auth; break;
            case UNAVAILABLE: message = R.string.profile_name_unavailable; break;
            default: message = R.string.profile_name_network;
        }
        binding.profileStatus.setVisibility(signed && message != 0 ? View.VISIBLE : View.GONE);
        if (message != 0) binding.profileStatus.setText(message);
        if (state.editing && (binding.profileName.getText() == null || !binding.profileName.getText().toString().equals(model.name()))) {
            syncing = true; binding.profileName.setText(model.name()); syncing = false;
        }
    }
    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
