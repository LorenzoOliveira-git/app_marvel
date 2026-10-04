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
import androidx.lifecycle.ViewModelProvider;
import com.example.app_marvel.MainActivity;
import com.example.app_marvel.MarvelApplication;
import com.example.app_marvel.R;
import com.example.app_marvel.data.auth.AuthRepository;
import com.example.app_marvel.databinding.FragmentAccountBinding;

/** Identidade da sessão; coleção/avatares serão acrescentados no bloco de perfil. */
public final class AccountFragment extends Fragment {
    private FragmentAccountBinding binding;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle saved) {
        binding = FragmentAccountBinding.inflate(inflater, parent, false); return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        super.onViewCreated(view, saved);
        AuthRepository repository = ((MarvelApplication) requireActivity().getApplication()).getContainer().getAuth();
        AccountViewModel model = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type) {
                if (type != AccountViewModel.class) throw new IllegalArgumentException("ViewModel não registrado");
                return type.cast(new AccountViewModel(repository));
            }
        }).get(AccountViewModel.class);
        ViewCompat.setAccessibilityHeading(binding.heading, true);
        model.getSession().observe(getViewLifecycleOwner(), session -> {
            boolean signed = session.isAuthenticated();
            binding.heading.setText(signed ? R.string.account_title : R.string.account_signed_out);
            binding.name.setText(session.getName()); binding.email.setText(session.getEmail());
            binding.name.setVisibility(signed && !session.getName().isEmpty() ? View.VISIBLE : View.GONE);
            binding.email.setVisibility(signed ? View.VISIBLE : View.GONE);
            binding.message.setVisibility(signed ? View.GONE : View.VISIBLE);
            binding.action.setText(signed ? R.string.account_sign_out : R.string.auth_sign_in);
            binding.action.setOnClickListener(v -> {
                if (signed) { model.signOut(); ((MainActivity) requireActivity()).leaveAccount(); }
                else ((MainActivity) requireActivity()).openLogin();
            });
        });
        view.post(() -> { if (binding != null) ((MainActivity) requireActivity()).updateContentInsets(); });
    }
    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
