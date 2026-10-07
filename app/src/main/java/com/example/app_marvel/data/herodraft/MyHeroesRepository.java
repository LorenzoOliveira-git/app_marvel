package com.example.app_marvel.data.herodraft;

import com.example.app_marvel.data.firebase.FirebaseServices;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Source;
import com.google.firebase.functions.FirebaseFunctionsException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Coleção privada paginada, confirmada pelo servidor; escrita textual exclusiva do backend. */
public final class MyHeroesRepository {
    public enum Failure { AUTH, UNAVAILABLE, INVALID, CONFLICT, NETWORK }
    public interface Callback<T> { void complete(T value, Failure error); }
    public static final class Hero {
        public final String id, name, realName, description;
        public final long revision;
        Hero(DocumentSnapshot doc) {
            id = doc.getId(); name = doc.getString("heroName"); realName = doc.getString("realName"); description = doc.getString("description");
            Long value = doc.getLong("textRevision"); revision = value == null ? 0 : value;
        }
    }
    public static final class Page {
        public final List<Hero> heroes;
        public final DocumentSnapshot cursor;
        public final boolean more;
        Page(List<Hero> heroes, DocumentSnapshot cursor, boolean more) { this.heroes=heroes;this.cursor=cursor;this.more=more; }
    }
    private final FirebaseServices services;
    public MyHeroesRepository(FirebaseServices services) { this.services=services; }
    public String account() { return services.auth()==null || services.auth().getCurrentUser()==null ? "" : services.auth().getCurrentUser().getUid(); }
    private boolean ready(Callback<?> callback) {
        if (!services.isLocal()) { callback.complete(null,Failure.UNAVAILABLE); return false; }
        if (account().isEmpty()) { callback.complete(null,Failure.AUTH); return false; } return true;
    }
    private Hero verified(DocumentSnapshot doc,String uid) {
        if (!doc.exists() || doc.getMetadata().isFromCache() || !uid.equals(doc.getString("uid")) || !doc.getId().equals(doc.getString("operationId"))
            || doc.getString("heroName")==null || doc.getString("realName")==null || doc.getString("description")==null) throw new IllegalStateException();
        return new Hero(doc);
    }
    public void page(DocumentSnapshot cursor,boolean reconnect,Callback<Page> callback) {
        if (!ready(callback)) return; String uid=account();
        if (reconnect) {
            // Ação explícita após erro: reabre o transporte em vez de esperar o backoff offline.
            services.firestore().disableNetwork().continueWithTask(done -> services.firestore().enableNetwork()).addOnCompleteListener(task -> {
                if (!uid.equals(account())) { callback.complete(null,Failure.AUTH); return; }
                if (!task.isSuccessful()) { callback.complete(null,Failure.NETWORK); return; }
                page(cursor,false,callback);
            });
            return;
        }
        Query query=services.firestore().collection("users/"+uid+"/heroes").orderBy("createdAt",Query.Direction.DESCENDING).orderBy(com.google.firebase.firestore.FieldPath.documentId(),Query.Direction.DESCENDING).limit(21);
        if (cursor!=null) query=query.startAfter(cursor);
        query.get(Source.SERVER).addOnCompleteListener(task -> {
            if (!uid.equals(account())) { callback.complete(null,Failure.AUTH); return; }
            if (!task.isSuccessful()) { callback.complete(null,error(task.getException())); return; }
            try {
                var docs=task.getResult().getDocuments(); List<Hero> rows=new ArrayList<>();
                for (int i=0;i<Math.min(20,docs.size());i++) rows.add(verified(docs.get(i),uid));
                callback.complete(new Page(rows,rows.isEmpty()?null:docs.get(rows.size()-1),docs.size()>20),null);
            } catch (RuntimeException invalid) { callback.complete(null,Failure.UNAVAILABLE); }
        });
    }
    public void read(String id,Callback<Hero> callback) {
        if (!ready(callback)) return; String uid=account();
        services.firestore().document("users/"+uid+"/heroes/"+id).get(Source.SERVER).addOnCompleteListener(task -> {
            if (!uid.equals(account())) {callback.complete(null,Failure.AUTH);return;}
            if (!task.isSuccessful()) {callback.complete(null,error(task.getException()));return;}
            try {callback.complete(verified(task.getResult(),uid),null);}catch(RuntimeException invalid){callback.complete(null,Failure.UNAVAILABLE);}
        });
    }
    public void update(Hero hero,String name,String realName,String description,Callback<Hero> callback) {
        if (!ready(callback)) return;String uid=account(); Map<String,Object> data=new HashMap<>();
        data.put("heroId",hero.id);data.put("revision",hero.revision);data.put("heroName",name);data.put("realName",realName);data.put("description",description);
        services.functions().getHttpsCallable("updateHeroText").call(data).addOnCompleteListener(task -> {
            if (!uid.equals(account())) { callback.complete(null,Failure.AUTH);return; }
            if (!task.isSuccessful()) { callback.complete(null,error(task.getException()));return; }
            Object result=task.getResult().getData();
            if (!(result instanceof Map) || !uid.equals(((Map<?,?>)result).get("uid")) || !hero.id.equals(((Map<?,?>)result).get("heroId"))) {callback.complete(null,Failure.UNAVAILABLE);return;}
            services.firestore().document("users/"+uid+"/heroes/"+hero.id).get(Source.SERVER).addOnCompleteListener(read -> {
                if (!uid.equals(account())) {callback.complete(null,Failure.AUTH);return;}
                if (!read.isSuccessful()) {callback.complete(null,error(read.getException()));return;}
                try { callback.complete(verified(read.getResult(),uid),null); } catch(RuntimeException invalid) {callback.complete(null,Failure.UNAVAILABLE);}
            });
        });
    }
    public void regenerate(Hero hero,String attemptId,Callback<Hero> callback){
        if(!ready(callback))return;String uid=account();Map<String,Object> data=new HashMap<>();
        data.put("heroId",hero.id);data.put("attemptId",attemptId);data.put("revision",hero.revision);data.put("confirmPaidGeneration",true);
        services.functions().getHttpsCallable("regenerateHeroImage").call(data).addOnCompleteListener(task->{
            if(!uid.equals(account())){callback.complete(null,Failure.AUTH);return;}
            if(!task.isSuccessful()){callback.complete(null,error(task.getException()));return;}
            Object result=task.getResult().getData();
            if(!(result instanceof Map)||!"completed".equals(((Map<?,?>)result).get("state"))){callback.complete(null,Failure.UNAVAILABLE);return;}
            read(hero.id,callback);
        });
    }
    private Failure error(Exception exception) {
        if(exception instanceof FirebaseFunctionsException) switch(((FirebaseFunctionsException)exception).getCode()) {
            case UNAUTHENTICATED: case PERMISSION_DENIED:return Failure.AUTH;
            case INVALID_ARGUMENT:return Failure.INVALID;
            case ABORTED:return Failure.CONFLICT;
            case FAILED_PRECONDITION: case NOT_FOUND:return Failure.UNAVAILABLE;
            default:return Failure.NETWORK;
        }
        return Failure.NETWORK;
    }
}
