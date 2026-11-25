package com.joelau.notifyisland;
import java.util.*;

/** Pure state: a duplicate callback never adds unread messages or extends a deadline. */
final class EntryStore<T> {
    static final class Entry<T> {
        final String key,pkg,conversation; final T payload; final long generation,receivedAt;
        final String fingerprint; final int count;
        Entry(String k,String p,String c,String f,int n,T v,long g,long at){key=k;pkg=p;conversation=c;fingerprint=f;count=n;payload=v;generation=g;receivedAt=at;}
    }
    private final LinkedHashMap<String,Entry<T>> entries=new LinkedHashMap<>();
    private long sequence; private String active;
    Entry<T> post(String key,String pkg,String conversation,String fingerprint,int reported,T value,long now){
        Entry<T> old=entries.get(key);
        if(old!=null&&old.fingerprint.equals(fingerprint)&&old.count>=reported)return null;
        int count=Math.max(1,reported);
        if(old!=null)count=Math.max(count,old.count+(old.fingerprint.equals(fingerprint)?0:1));
        Entry<T> next=new Entry<>(key,pkg,conversation,fingerprint,count,value,++sequence,now);
        entries.put(key,next);active=key;
        while(entries.size()>128){String first=entries.keySet().iterator().next();entries.remove(first);}
        return next;
    }
    Entry<T> active(){return entries.get(active);}
    /** Every stored entry of one package, oldest first. */
    List<Entry<T>> entriesFor(String pkg){
        List<Entry<T>> list=new ArrayList<>();
        for(Entry<T> e:entries.values())if(e.pkg.equals(pkg))list.add(e);
        return list;
    }
    /** Newest stored entry from one package, used to name a third-party call island. */
    Entry<T> latestFor(String pkg){
        Entry<T> last=null;
        for(Entry<T> e:entries.values())if(e.pkg.equals(pkg))last=e;
        return last;
    }
    boolean select(String key){if(!entries.containsKey(key))return false;active=key;return true;}
    /** Metadata-only changes keep unread counts, expiry and swipe-dismissed state. */
    Entry<T> refresh(String key,T value){
        Entry<T> old=entries.get(key);if(old==null)return null;
        Entry<T> next=new Entry<>(old.key,old.pkg,old.conversation,old.fingerprint,old.count,value,old.generation,old.receivedAt);
        entries.put(key,next);return next;
    }
    Entry<T> get(String key){return entries.get(key);}
    int unread(String pkg){int n=0;for(Entry<T> e:entries.values())if(e.pkg.equals(pkg))n+=e.count;return n;}
    boolean remove(String key){entries.remove(key);if(Objects.equals(active,key)){active=null;return true;}return false;}
    boolean expire(String key,long generation){Entry<T> e=entries.get(key);if(e==null||e.generation!=generation||!Objects.equals(active,key))return false;active=null;return true;}
    void hide(){active=null;}
    void dismissConversation(String key){Entry<T> e=entries.get(key);if(e==null)return;entries.values().removeIf(v->v.conversation.equals(e.conversation));active=null;}
    void filter(Set<String> selected){entries.values().removeIf(e->!selected.contains(e.pkg));if(!entries.containsKey(active))active=null;}
    void clear(){entries.clear();active=null;}
}
