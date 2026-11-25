package com.joelau.notifyisland;
import java.util.*;

/** Bounded, in-memory first-message protection while the native service starts. */
final class DeliveryQueue<T> {
    static final long TTL=20000;
    static final class Item<T>{
        final String key,pkg;final long id,at;final T value;
        Item(String k,String p,long n,long time,T v){key=k;pkg=p;id=n;at=time;value=v;}
    }
    private final LinkedHashMap<String,Item<T>> items=new LinkedHashMap<>();
    private long sequence;
    Item<T> put(String key,String pkg,long now,T value){
        Item<T> item=new Item<>(key,pkg,++sequence,now,value);items.remove(key);items.put(key,item);
        while(items.size()>64)items.remove(items.keySet().iterator().next());
        return item;
    }
    void acknowledge(String key,long id){Item<T> current=items.get(key);if(current!=null&&current.id==id)items.remove(key);}
    void remove(String key){items.remove(key);}
    void filter(boolean enabled,Set<String> selected,long now){items.values().removeIf(i->!enabled||!selected.contains(i.pkg)||now-i.at>=TTL);}
    List<Item<T>> snapshot(){return new ArrayList<>(items.values());}
    boolean isEmpty(){return items.isEmpty();}
}
