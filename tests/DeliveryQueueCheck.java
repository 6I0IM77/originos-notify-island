package com.joelau.notifyisland;
import java.util.Set;
public final class DeliveryQueueCheck {
    private static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    public static void main(String[] args){
        DeliveryQueue<String> q=new DeliveryQueue<>();
        var first=q.put("a","chat",0,"first before service starts");
        check(q.snapshot().size()==1,"first message must survive asynchronous binding");
        var latest=q.put("a","chat",1,"updated while binding");
        q.acknowledge("a",first.id);
        check(q.snapshot().get(0).value.equals("updated while binding"),"old ACK must not drop a newer update");
        q.acknowledge("a",latest.id);check(q.isEmpty(),"ACK stops retries");
        q.put("a","chat",2,"cancelled");q.remove("a");check(q.isEmpty(),"cancelled-before-ready notification must not replay");
        q.put("a","chat",3,"expired");q.filter(true,Set.of("chat"),3+DeliveryQueue.TTL);check(q.isEmpty(),"do not replay stale notifications");
        q.put("a","chat",5,"deselected");q.filter(true,Set.of("other"),6);check(q.isEmpty(),"selection changes filter queued messages");
        q.put("a","chat",7,"disabled");q.filter(false,Set.of("chat"),8);check(q.isEmpty(),"disable clears pending notifications");
        for(int i=0;i<70;i++)q.put("k"+i,"chat",9,"message");
        check(q.snapshot().size()==64&&q.snapshot().get(0).key.equals("k6"),"bounded queue drops oldest entries");
        System.out.println("PASS: cold-start queue, update ACK race, cancel-before-ready, expiry, app filtering, disable, bounded memory");
    }
}
