package com.joelau.notifyisland;

public final class IslandSwipePolicyCheck {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        for(int distance:new int[]{24,66,84}){
            IslandSwipePolicy swipe=new IslandSwipePolicy();swipe.event(0,1);
            check(swipe.horizontal(distance,0,distance,15),"right threshold");
            check(swipe.horizontal(-distance,0,distance,15),"left threshold");
            check(!swipe.horizontal(distance-1,0,distance,15),"short swipe springs back");
            check(!swipe.horizontal(distance,distance,distance,15),"diagonal stays native");
            check(!swipe.horizontal(0,distance,distance,15),"vertical stays native");
            swipe.event(2,2);swipe.event(1,1);
            check(!swipe.horizontal(distance,0,distance,15),"second finger cancels until next DOWN");
            swipe.event(0,1);check(swipe.horizontal(distance,0,distance,15),"fresh gesture");
            swipe.event(3,1);check(swipe.cancelled(),"CANCEL cannot dismiss");
            check(!swipe.horizontal(-distance,0,distance,15),"cancel left");
            swipe.event(0,1);check(!swipe.horizontal(Float.NaN,0,distance,15),"invalid coordinate");
            float y=(float)(distance*Math.tan(Math.toRadians(12)));
            check(swipe.horizontal(distance,y,distance,15),"portrait native angle");
            check(!swipe.horizontal(distance,y,distance,10),"landscape native angle");
        }
        EntryStore<String> store=new EntryStore<>();
        var old=store.post("message","chat","thread","one",1,"first",0);
        check(store.expire(old.key,old.generation),"swipe hides active generation");
        check(store.active()==null&&store.unread("chat")==1,"preserves unread after swipe");
        check(store.post("message","chat","thread","one",1,"replay",1)==null,"duplicate cannot reopen");
        var next=store.post("message","chat","thread","two",1,"fresh",2);
        check(next!=null&&store.active()==next,"new content reopens");
        check(!store.expire(old.key,old.generation)&&store.active()==next,"late swipe cannot hide replacement");
        System.out.println("PASS: left/right native thresholds, angles, cancel, multi-touch, unread, duplicate suppression and replacement isolation");
    }
}
