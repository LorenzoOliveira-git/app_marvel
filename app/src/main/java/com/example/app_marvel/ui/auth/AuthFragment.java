package com.example.app_marvel.ui.auth;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.example.app_marvel.MainActivity;
import com.example.app_marvel.MarvelApplication;
import com.example.app_marvel.R;
import com.example.app_marvel.data.auth.AuthRepository;
import com.example.app_marvel.databinding.FragmentAuthBinding;
import com.google.android.material.textfield.TextInputLayout;
import java.util.Map;
import java.util.function.Consumer;
import androidx.fragment.app.Fragment;

public final class AuthFragment extends Fragment {
    private FragmentAuthBinding binding;
    private AuthViewModel model;
    private boolean register;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle saved) {
        binding = FragmentAuthBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        super.onViewCreated(view, saved);
        register = requireArguments().getBoolean("register", false);
        AuthRepository repo = ((MarvelApplication) requireActivity().getApplication()).getContainer().getAuth();
        model = new ViewModelProvider(this, new AuthViewModelFactory(repo)).get(AuthViewModel.class);
        ViewCompat.setAccessibilityHeading(binding.title, true);
        binding.title.setText(register ? R.string.auth_register_title : R.string.auth_login_title);
        binding.submit.setText(register ? R.string.auth_sign_up : R.string.auth_sign_in);
        binding.switchForm.setText(register ? R.string.auth_go_login : R.string.auth_go_register);
        binding.nameLayout.setVisibility(register ? View.VISIBLE : View.GONE);
        binding.nameLabel.setVisibility(register ? View.VISIBLE : View.GONE);
        binding.confirmationLayout.setVisibility(register ? View.VISIBLE : View.GONE);
        binding.confirmationLabel.setVisibility(register ? View.VISIBLE : View.GONE);
        binding.passwordLayout.setHelperText(register ? getString(R.string.auth_password_help) : null);
        if (register) {
            android.widget.LinearLayout.LayoutParams emailParams = (android.widget.LinearLayout.LayoutParams) binding.emailLabel.getLayoutParams();
            emailParams.topMargin = getResources().getDimensionPixelSize(R.dimen.space_xl);
            binding.emailLabel.setLayoutParams(emailParams);
            android.widget.LinearLayout.LayoutParams passwordParams = (android.widget.LinearLayout.LayoutParams) binding.passwordLabel.getLayoutParams();
            passwordParams.topMargin = getResources().getDimensionPixelSize(R.dimen.space_xl);
            binding.passwordLabel.setLayoutParams(passwordParams);
        }
        binding.password.setImeOptions(register ? EditorInfo.IME_ACTION_NEXT : EditorInfo.IME_ACTION_DONE);
        if (android.os.Build.VERSION.SDK_INT >= 26) binding.password.setAutofillHints(register ? "newPassword" : "password");
        binding.email.setText(model.getEmail()); binding.name.setText(model.getName());
        binding.password.setText(model.getPassword()); binding.confirmation.setText(model.getConfirmation());
        watch(binding.email, model::setEmail); watch(binding.name, model::setName);
        watch(binding.password, model::setPassword); watch(binding.confirmation, model::setConfirmation);
        binding.submit.setOnClickListener(v -> submit());
        (register ? binding.confirmation : binding.password).setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE) { submit(); return true; }
            return false;
        });
        binding.switchForm.setOnClickListener(v -> {
            model.setPassword(""); model.setConfirmation("");
            if (register) NavHostFragment.findNavController(this).popBackStack();
            else {
                Bundle args = new Bundle(); args.putBoolean("returnToHero", requireArguments().getBoolean("returnToHero", false));
                NavHostFragment.findNavController(this).navigate(R.id.registerFragment, args);
            }
        });
        binding.browse.setOnClickListener(v -> {
            model.setPassword(""); model.setConfirmation("");
            ((MainActivity) requireActivity()).enterHome();
        });
        model.getState().observe(getViewLifecycleOwner(), this::render);
        view.post(() -> { if (binding != null) ((MainActivity) requireActivity()).updateContentInsets(); });
    }
    private void watch(EditText field, Consumer<String> save) {
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { save.accept(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
    }
    private void submit() {
        if (model.getState().getValue().status == AuthState.Status.LOADING) return;
        binding.nameLayout.setError(null); binding.emailLayout.setError(null);
        binding.passwordLayout.setError(null); binding.confirmationLayout.setError(null);
        Map<AuthViewModel.Field, AuthViewModel.Validation> errors = model.validate(register);
        TextInputLayout first = null;
        for (Map.Entry<AuthViewModel.Field, AuthViewModel.Validation> error : errors.entrySet()) {
            TextInputLayout layout;
            switch (error.getKey()) {
                case NAME: layout = binding.nameLayout; break;
                case EMAIL: layout = binding.emailLayout; break;
                case PASSWORD: layout = binding.passwordLayout; break;
                case CONFIRMATION: layout = binding.confirmationLayout; break;
                default: throw new IllegalArgumentException("Campo desconhecido");
            }
            int text;
            switch (error.getValue()) {
                case EMAIL: text = R.string.auth_error_email; break;
                case SHORT_PASSWORD: text = R.string.auth_error_short_password; break;
                case CONFIRMATION: text = R.string.auth_error_confirmation; break;
                default: text = R.string.auth_error_required;
            }
            layout.setError(getString(text)); if (first == null) first = layout;
        }
        if (first != null) { first.getEditText().requestFocus(); return; }
        model.submit(register);
    }
    private void render(AuthState state) {
        boolean pending = state.status == AuthState.Status.LOADING;
        binding.progress.setVisibility(pending ? View.VISIBLE : View.GONE);
        binding.submit.setEnabled(!pending);
        binding.switchForm.setEnabled(!pending); binding.browse.setEnabled(!pending);
        binding.email.setEnabled(!pending); binding.name.setEnabled(!pending);
        binding.password.setEnabled(!pending); binding.confirmation.setEnabled(!pending);
        binding.marv.setImageResource(pending || state.status == AuthState.Status.ERROR
                ? R.drawable.marv_thinking : R.drawable.marv_welcome);
        int message = 0;
        if (pending) message = register ? R.string.auth_loading_register : R.string.auth_loading_login;
        else if (state.status == AuthState.Status.ERROR) message = errorMessage(state.failure);
        binding.status.setVisibility(message == 0 ? View.GONE : View.VISIBLE);
        if (message != 0) binding.status.setText(message);
        if (state.status == AuthState.Status.SUCCESS) {
            if (!state.nameSaved) Toast.makeText(requireContext(), R.string.auth_name_not_saved, Toast.LENGTH_LONG).show();
            binding.password.setText(""); binding.confirmation.setText("");
            model.consumeSuccess(); ((MainActivity) requireActivity()).enterHome();
        }
    }
    private int errorMessage(AuthRepository.Failure failure) {
        if (failure == null) return R.string.auth_error_unknown;
        switch (failure) {
            case NETWORK: return R.string.auth_error_network;
            case CREDENTIALS: return R.string.auth_error_credentials;
            case EMAIL_IN_USE: return R.string.auth_error_email_used;
            case WEAK_PASSWORD: return R.string.auth_error_weak;
            case RATE_LIMIT: return R.string.auth_error_rate;
            case UNAVAILABLE: return R.string.auth_error_unknown;
            default: return R.string.auth_error_unknown;
        }
    }
    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
