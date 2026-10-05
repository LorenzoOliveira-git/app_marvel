package com.example.app_marvel.ui.myheroes;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
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
import com.example.app_marvel.data.model.AppFeature;
import com.example.app_marvel.databinding.FragmentMyHeroesBinding;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

public final class MyHeroesFragment extends Fragment {
    private FragmentMyHeroesBinding binding;
    private MyHeroesViewModel model;
    private boolean syncing;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup parent,@Nullable Bundle saved){binding=FragmentMyHeroesBinding.inflate(inflater,parent,false);return binding.getRoot();}
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle saved){
        var container=((MarvelApplication)requireActivity().getApplication()).getContainer();
        model=new ViewModelProvider(this,new ViewModelProvider.Factory(){
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type,@NonNull CreationExtras extras){
                if(type!=MyHeroesViewModel.class)throw new IllegalArgumentException("ViewModel não registrado");
                return type.cast(new MyHeroesViewModel(container.getMyHeroes(),container.getHeroCreations(),SavedStateHandleSupport.createSavedStateHandle(extras)));
            }
        }).get(MyHeroesViewModel.class);
        model.openHero(getArguments()==null ? "" : getArguments().getString("heroId", ""));
        ViewCompat.setAccessibilityHeading(binding.collectionHeading,true);
        field(binding.editHeroName,"heroName");field(binding.editRealName,"realName");field(binding.editDescription,"description");
        binding.collectionRefresh.setOnClickListener(v->model.refresh());binding.collectionMore.setOnClickListener(v->model.more());binding.collectionSave.setOnClickListener(v->model.save());
        binding.collectionCreate.setOnClickListener(v->((MainActivity)requireActivity()).openFeature(AppFeature.CREATE_HERO));
        binding.collectionClose.setOnClickListener(v->discard(model::closeEditor));binding.collectionReload.setOnClickListener(v->discard(model::reloadSelected));
        binding.collectionLeavePending.setOnClickListener(v->model.closeEditor());
        binding.collectionImageRetry.setOnClickListener(v->model.loadImage());
        model.state().observe(getViewLifecycleOwner(),this::render);
        model.image().observe(getViewLifecycleOwner(),bitmap->{binding.collectionImage.setImageBitmap(bitmap);binding.collectionImage.setVisibility(bitmap==null?View.GONE:View.VISIBLE);});
        model.imageFailed().observe(getViewLifecycleOwner(),failed->binding.collectionImageRetry.setVisibility(Boolean.TRUE.equals(failed)?View.VISIBLE:View.GONE));
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),new OnBackPressedCallback(true){
            @Override public void handleOnBackPressed(){var current=model.state().getValue();if(current.busy)return;if(current.selected!=null)discard(model::closeEditor);else{setEnabled(false);requireActivity().getOnBackPressedDispatcher().onBackPressed();setEnabled(true);}}
        });
        view.post(()->{if(binding!=null)((MainActivity)requireActivity()).updateContentInsets();});
    }
    private void field(TextInputEditText input,String key){input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(!syncing)model.field(key,s.toString());}public void afterTextChanged(Editable value){}});}
    private void discard(Runnable action){
        var current=model.state().getValue();if(current.busy)return;
        boolean edited=current.selected!=null&&(!model.field("heroName").equals(current.selected.name)||!model.field("realName").equals(current.selected.realName)||!model.field("description").equals(current.selected.description));
        if(!edited){action.run();return;}
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.my_heroes_discard_title).setMessage(R.string.my_heroes_discard_body)
            .setPositiveButton(R.string.my_heroes_discard,(dialog,which)->action.run()).setNegativeButton(R.string.catalog_cancel,null).show();
    }
    private void render(MyHeroesViewModel.State state){
        boolean editor=state.selected!=null;boolean pending=model.pendingHero();
        binding.collectionEditor.setVisibility(editor?View.VISIBLE:View.GONE);binding.collectionList.setVisibility(editor?View.GONE:View.VISIBLE);
        binding.collectionIntro.setVisibility(editor?View.GONE:View.VISIBLE);
        binding.collectionProgress.setVisibility(state.busy?View.VISIBLE:View.GONE);
        int message=state.busy?R.string.my_heroes_loading:state.saved?R.string.my_heroes_saved:!editor&&state.rows.isEmpty()?R.string.my_heroes_empty:0;
        if(state.error!=null)switch(state.error){case AUTH:message=R.string.my_heroes_auth;break;case INVALID:message=R.string.my_heroes_invalid;break;case CONFLICT:message=R.string.my_heroes_conflict;break;case UNAVAILABLE:message=pending?R.string.hero_access_unavailable:R.string.my_heroes_unavailable;break;default:message=R.string.my_heroes_error;}
        binding.collectionStatus.setVisibility(message==0?View.GONE:View.VISIBLE);if(message!=0)binding.collectionStatus.setText(message);
        binding.collectionRefresh.setVisibility(editor?View.GONE:View.VISIBLE);binding.collectionRefresh.setEnabled(!state.busy);
        binding.collectionMore.setVisibility(!editor&&state.more?View.VISIBLE:View.GONE);binding.collectionMore.setEnabled(!state.busy);
        binding.collectionCreate.setVisibility(!editor?View.VISIBLE:View.GONE);binding.collectionCreate.setEnabled(!state.busy);
        binding.collectionSave.setEnabled(!state.busy&&state.error!=com.example.app_marvel.data.herodraft.MyHeroesRepository.Failure.CONFLICT);binding.collectionClose.setEnabled(!state.busy);binding.collectionReload.setEnabled(!state.busy);
        binding.collectionReload.setVisibility(state.error!=null?View.VISIBLE:View.GONE);
        binding.collectionLeavePending.setVisibility(pending&&!state.busy?View.VISIBLE:View.GONE);
        for(var input:new TextInputEditText[]{binding.editHeroName,binding.editRealName,binding.editDescription})input.setEnabled(!state.busy);
        if(editor){syncing=true;set(binding.editHeroName,model.field("heroName"));set(binding.editRealName,model.field("realName"));set(binding.editDescription,model.field("description"));syncing=false;}
        binding.collectionList.removeAllViews();
        if(!editor)for(var hero:state.rows){
            MaterialButton row=new MaterialButton(new android.view.ContextThemeWrapper(requireContext(),R.style.Widget_Marvel_Button_Secondary));
            row.setText(hero.name+"\n"+hero.realName);row.setSingleLine(false);row.setAllCaps(false);row.setEnabled(!state.busy);
            var params=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);params.topMargin=getResources().getDimensionPixelSize(R.dimen.space_md);row.setLayoutParams(params);
            row.setOnClickListener(v->model.select(hero));binding.collectionList.addView(row);
        }
    }
    private void set(TextInputEditText input,String value){if(input.getText()==null||!input.getText().toString().equals(value))input.setText(value);}
    @Override public void onDestroyView(){super.onDestroyView();binding=null;}
}
