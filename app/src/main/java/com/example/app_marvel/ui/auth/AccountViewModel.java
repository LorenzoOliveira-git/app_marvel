package com.example.app_marvel.ui.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.example.app_marvel.data.auth.AuthRepository;
import com.example.app_marvel.data.auth.AuthSession;

public final class AccountViewModel extends ViewModel {
    public static final class EditState {
        public final boolean editing,busy,saved;
        public final AuthRepository.Failure error;
        EditState(boolean editing,boolean busy,boolean saved,AuthRepository.Failure error){this.editing=editing;this.busy=busy;this.saved=saved;this.error=error;}
    }
    private final AuthRepository repository;
    private final SavedStateHandle fields;
    private final MutableLiveData<EditState> edit=new MutableLiveData<>(new EditState(false,false,false,null));
    private final Observer<AuthSession> accountObserver=this::accountChanged;
    private String uid="";
    private int epoch;
    private boolean initialized;
    public AccountViewModel(AuthRepository repository,SavedStateHandle fields){this.repository=repository;this.fields=fields;repository.getSession().observeForever(accountObserver);}
    public LiveData<AuthSession> getSession(){return repository.getSession();}
    public LiveData<EditState> edit(){return edit;}
    public String name(){String value=fields.get("profileName");return value==null?"":value;}
    public void name(String value){if(edit.getValue().editing&&!edit.getValue().busy)fields.set("profileName",value);}
    private void accountChanged(AuthSession session){
        String next=session.getUid();if(initialized&&uid.equals(next))return;
        initialized=true;uid=next;epoch++;
        if(uid.isEmpty()||!uid.equals(fields.get("profileOwner"))){fields.remove("profileName");fields.remove("profileEditing");fields.remove("profileOwner");}
        edit.setValue(new EditState(!uid.isEmpty()&&Boolean.TRUE.equals(fields.get("profileEditing")),false,false,null));
    }
    public void startEdit(){if(uid.isEmpty()||edit.getValue().busy)return;fields.set("profileName",repository.getSession().getValue().getName());fields.set("profileOwner",uid);fields.set("profileEditing",true);edit.setValue(new EditState(true,false,false,null));}
    public void cancel(){if(edit.getValue().busy)return;fields.remove("profileName");fields.remove("profileEditing");fields.remove("profileOwner");edit.setValue(new EditState(false,false,false,null));}
    public void save(){
        if(uid.isEmpty()||!edit.getValue().editing||edit.getValue().busy)return;
        String value=name().trim();if(value.isEmpty()||value.length()>100){edit.setValue(new EditState(true,false,false,AuthRepository.Failure.INVALID));return;}
        int stamp=epoch;edit.setValue(new EditState(true,true,false,null));
        repository.updateName(value,(failure,confirmed)->{if(stamp!=epoch)return;if(failure!=null||!confirmed){edit.setValue(new EditState(true,false,false,failure==null?AuthRepository.Failure.UNKNOWN:failure));return;}
            fields.remove("profileName");fields.remove("profileEditing");fields.remove("profileOwner");edit.setValue(new EditState(false,false,true,null));});
    }
    public void signOut(){if(!edit.getValue().busy)repository.signOut();}
    @Override protected void onCleared(){epoch++;repository.getSession().removeObserver(accountObserver);}
}
