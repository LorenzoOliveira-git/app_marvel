package com.example.app_marvel.ui.myheroes;

import android.graphics.Bitmap;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.example.app_marvel.data.herodraft.HeroCreationRepository;
import com.example.app_marvel.data.herodraft.MyHeroesRepository;
import com.example.app_marvel.data.herodraft.PrivateHeroImage;
import com.google.firebase.firestore.DocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public final class MyHeroesViewModel extends ViewModel {
    public static final class State {
        public final List<MyHeroesRepository.Hero> rows;
        public final MyHeroesRepository.Hero selected;
        public final boolean busy, more, saved;
        public final MyHeroesRepository.Failure error;
        State(List<MyHeroesRepository.Hero> rows,MyHeroesRepository.Hero selected,boolean busy,boolean more,boolean saved,MyHeroesRepository.Failure error) {
            this.rows=new ArrayList<>(rows);this.selected=selected;this.busy=busy;this.more=more;this.saved=saved;this.error=error;
        }
    }
    private final MyHeroesRepository repository;
    private final HeroCreationRepository creations;
    private final SavedStateHandle fields;
    private final PrivateHeroImage loader=new PrivateHeroImage();
    private final PrivateHeroImage previews=new PrivateHeroImage();
    private final android.util.LruCache<String,Bitmap> thumbnails=new android.util.LruCache<String,Bitmap>(8*1024*1024){
        @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getByteCount();}
    };
    private final java.util.Set<String> requestedPreviews=new java.util.HashSet<>();
    private final MutableLiveData<Integer> previewChanged=new MutableLiveData<>(0);
    private int previewEpoch;
    public LiveData<Integer> previewChanged(){return previewChanged;}
    public Bitmap thumbnail(String id){return thumbnails.get(id);}
    public void preview(String id){
        if(account.isEmpty()||selected!=null||thumbnails.get(id)!=null||!requestedPreviews.add(id))return;
        int stamp=previewEpoch;String uid=account;
        creations.imageUrl(id,(url,failure)->{
            if(stamp!=previewEpoch||!uid.equals(repository.account()))return;
            if(failure!=null)return;
            previews.load(url,bitmap->{
                if(stamp!=previewEpoch||!uid.equals(repository.account()))return;
                if(bitmap!=null){requestedPreviews.remove(id);thumbnails.put(id,Bitmap.createScaledBitmap(bitmap,144,216,true));previewChanged.setValue(previewChanged.getValue()+1);}
            });
        });
    }
    private final MutableLiveData<State> state=new MutableLiveData<>();
    private final MutableLiveData<Bitmap> image=new MutableLiveData<>();
    private final MutableLiveData<Boolean> imageFailed=new MutableLiveData<>(false);
    private final List<MyHeroesRepository.Hero> rows=new ArrayList<>();
    private Runnable removeAuth;
    private DocumentSnapshot cursor;
    private String account="";
    private int epoch, imageEpoch;
    private boolean busy, more, saved, initialized;
    private MyHeroesRepository.Hero selected;
    private MyHeroesRepository.Failure error;
    public MyHeroesViewModel(MyHeroesRepository repository,HeroCreationRepository creations,SavedStateHandle fields) {
        this.repository=repository;this.creations=creations;this.fields=fields;
        emit();removeAuth=creations.accountListener(this::accountChanged);
    }
    public LiveData<State> state(){return state;}
    public LiveData<Bitmap> image(){return image;}
    public LiveData<Boolean> imageFailed(){return imageFailed;}
    public String field(String key){String value=fields.get(key);return value==null?"":value;}
    public void field(String key,String value){if(selected!=null&&!busy){fields.set(key,value);if(saved){saved=false;emit();}}}
    private void emit(){state.setValue(new State(rows,selected,busy,more,saved,error));}
    private void accountChanged() {
        String uid=repository.account();if(initialized&&uid.equals(account))return;
        if(initialized&&!uid.equals(account))fields.remove("requestedHero");
        initialized=true;account=uid;epoch++;imageEpoch++;previewEpoch++;thumbnails.evictAll();requestedPreviews.clear();rows.clear();cursor=null;selected=null;busy=false;more=false;saved=false;error=null;image.setValue(null);imageFailed.setValue(false);
        String owner=fields.get("owner");String id=fields.get("selectedId");
        if(!uid.equals(owner)){clearFields();fields.remove("openedArgument");id=null;}
        emit();
        if(uid.isEmpty()){error=MyHeroesRepository.Failure.AUTH;emit();return;}
        String requested=fields.get("requestedHero");
        if(requested!=null&&!requested.equals(fields.get("openedArgument"))){openHero(requested);return;}
        if(id!=null)restore(id);else refresh();
    }
    public void openHero(String id) {
        if (id == null || id.isEmpty()) return;
        fields.set("requestedHero",id);
        if(!initialized)return;
        if(id.equals(fields.get("openedArgument")))return;
        fields.set("openedArgument", id);
        if (!id.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}") || account.isEmpty()) {
            error = MyHeroesRepository.Failure.UNAVAILABLE; emit(); return;
        }
        epoch++; imageEpoch++; busy=false; selected=null; image.setValue(null); imageFailed.setValue(false);clearFields();
        fields.set("owner",account); fields.set("selectedId",id);fields.set("directPending",true);
        int stamp=epoch;busy=true;error=null;emit();
        repository.read(id,(hero,failure)->{if(stamp!=epoch)return;busy=false;error=failure;selected=hero;if(hero!=null){fill(hero);fields.remove("directPending");loadImage();}emit();});
    }
    public boolean pendingHero() { return selected == null && fields.get("selectedId") != null; }
    private void clearFields(){for(String key:new String[]{"owner","selectedId","directPending","revision","heroName","realName","description"})fields.remove(key);}
    public void refresh(){if(busy||selected!=null)return;String id=fields.get("selectedId");if(id!=null){restore(id);return;}requestedPreviews.clear();load(false);}
    public void more(){if(!busy&&more&&selected==null)load(true);}
    private void load(boolean append){
        int stamp=epoch;boolean reconnect=error==MyHeroesRepository.Failure.NETWORK;busy=true;error=null;saved=false;emit();
        repository.page(append?cursor:null,reconnect,(page,failure)->{
            if(stamp!=epoch)return;busy=false;error=failure;
            if(page!=null){if(!append)rows.clear();for(var hero:page.heroes)if(rows.stream().noneMatch(existing->existing.id.equals(hero.id)))rows.add(hero);cursor=page.cursor;more=page.more;}
            emit();
        });
    }
    public void select(MyHeroesRepository.Hero hero){
        if(busy)return;epoch++;selected=hero;error=null;saved=false;fields.set("owner",account);fields.set("selectedId",hero.id);fill(hero);emit();loadImage();
    }
    private void fill(MyHeroesRepository.Hero hero){fields.set("revision",hero.revision);fields.set("heroName",hero.name);fields.set("realName",hero.realName);fields.set("description",hero.description);}
    private void restore(String id){
        int stamp=epoch;busy=true;emit();repository.read(id,(hero,failure)->{if(stamp!=epoch)return;busy=false;error=failure;selected=hero;Long revision=fields.get("revision");if(hero!=null&&Boolean.TRUE.equals(fields.get("directPending"))){fill(hero);fields.remove("directPending");}else if(hero!=null&&(revision==null||revision!=hero.revision))error=MyHeroesRepository.Failure.CONFLICT;emit();if(hero!=null)loadImage();});
    }
    public void reloadSelected(){if(busy||selected==null)return;int stamp=epoch;String id=selected.id;busy=true;error=null;emit();repository.read(id,(hero,failure)->{if(stamp!=epoch)return;busy=false;error=failure;if(hero!=null){selected=hero;fill(hero);saved=false;}emit();});}
    public void save(){
        if(busy||selected==null||error==MyHeroesRepository.Failure.CONFLICT)return;
        String name=field("heroName").trim(),real=field("realName").trim(),description=field("description").trim();
        if(name.isEmpty()||name.length()>100||real.isEmpty()||real.length()>100||description.isEmpty()||description.length()>2000){error=MyHeroesRepository.Failure.INVALID;saved=false;emit();return;}
        int stamp=epoch;busy=true;error=null;saved=false;emit();repository.update(selected,name,real,description,(hero,failure)->{if(stamp!=epoch)return;busy=false;error=failure;if(hero!=null){selected=hero;fill(hero);saved=true;for(int i=0;i<rows.size();i++)if(rows.get(i).id.equals(hero.id))rows.set(i,hero);}emit();});
    }
    public void closeEditor(){if(busy)return;epoch++;imageEpoch++;selected=null;clearFields();image.setValue(null);imageFailed.setValue(false);saved=false;error=null;emit();refresh();}
    public void loadImage(){
        if(selected==null)return;int stamp=++imageEpoch;String uid=account,id=selected.id;imageFailed.setValue(false);
        creations.imageUrl(id,(url,failure)->{if(stamp!=imageEpoch||!uid.equals(repository.account()))return;if(failure!=null){imageFailed.setValue(true);return;}
            loader.load(url,bitmap->{if(stamp!=imageEpoch||!uid.equals(repository.account()))return;image.setValue(bitmap);imageFailed.setValue(bitmap==null);});});
    }
    @Override protected void onCleared(){epoch++;imageEpoch++;if(removeAuth!=null)removeAuth.run();loader.close();previews.close();thumbnails.evictAll();image.setValue(null);}
}
