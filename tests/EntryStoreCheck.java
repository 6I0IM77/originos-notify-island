package com.joelau.notifyisland;
public final class EntryStoreCheck {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        EntryStore<String> s=new EntryStore<>();
        var first=s.post("k1","a","c1","f1",1,"first",0);
        check(s.unread("a")==1,"first count");
        check(s.post("k1","a","c1","f1",1,"duplicate",1)==null,"duplicate callback");
        check(s.unread("a")==1,"duplicate not counted");
        var avatar=s.refresh("k1","avatar now available");
        check(s.active()==avatar&&s.unread("a")==1,"avatar metadata refresh retains active and count");
        check(avatar.generation==first.generation&&avatar.receivedAt==first.receivedAt,"avatar does not extend expiry");
        var next=s.post("k1","a","c1","f2",1,"latest",2);
        check(s.active().payload.equals("latest"),"latest message");
        check(s.unread("a")==2,"updated message counted");
        check(!s.expire("k1",first.generation),"old timer cannot erase update");
        s.post("k2","a","c1","f3",1,"conversation next",3);
        check(s.unread("a")==3,"distinct keys counted");
        s.dismissConversation("k2");
        check(s.unread("a")==0 && s.active()==null,"dismiss clears conversation");
        var reported=s.post("k3","a","c2","f4",5,"reported",4);
        check(s.unread("a")==5,"reported unread preserved");
        check(s.expire("k3",reported.generation),"current timeout");
        check(s.active()==null && s.unread("a")==5,"expiry hides but preserves unread");
        s.refresh("k3","avatar arrived after dismissal");
        check(s.active()==null&&s.unread("a")==5,"avatar never resurrects dismissed island");
        check(s.refresh("missing","avatar")==null,"late avatar cannot recreate removed notification");
        s.post("k4","b","c3","f5",1,"other",5);
        s.filter(java.util.Set.of("a"));
        check(s.active()==null && s.unread("b")==0,"deselected app removed");
        s.clear();check(s.unread("a")==0,"master disable clears all");
        System.out.println("PASS: duplicate callbacks, current updates, stale timers, conversation dismissal, expiry, app selection, master disable");
    }
}
