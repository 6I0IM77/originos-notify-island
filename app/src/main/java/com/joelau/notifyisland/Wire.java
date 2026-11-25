package com.joelau.notifyisland;
import android.app.PendingIntent;
import android.content.*;
import java.util.Set;

final class Wire {
    static final String ID="com.joelau.notifyisland",SYSUI="com.android.systemui",PLUGIN="com.vivo.systemuiplugin";
    static final String PREFS="notify_island",PREFIX=ID+":entry:";
    static final String EVENT=ID+".EVENT",COMMAND=ID+".COMMAND",PING=ID+".PING";
    static final String ACK=ID+".ACK";
    static final String PEER=ID+".PEER",PEER_REPLY=ID+".PEER_REPLY";
    /** Sent by the settings app after a preference write so the pill re-renders immediately. */
    static final String RELOAD=ID+".RELOAD";
    static final String PRIVILEGED_SENDER="android.permission.STATUS_BAR_SERVICE";
    static final Set<String> DEFAULTS=Set.of("com.tencent.mm","com.tencent.mobileqq");
    static void register(Context c,BroadcastReceiver r,String action,String permission){
        c.registerReceiver(r,new IntentFilter(action),permission,null,Context.RECEIVER_EXPORTED);
    }
    static boolean validPing(Intent i,SharedPreferences p){
        String token=p.getString("bridge_token","");
        if(token.isEmpty() || !token.equals(i.getStringExtra("token")))return false;
        PendingIntent reply=i.getParcelableExtra("reply");
        return reply!=null&&ID.equals(reply.getCreatorPackage());
    }
    static void pong(Context c,Intent i,SharedPreferences p,String role,String detail){
        if(!validPing(i,p))return;
        PendingIntent reply=i.getParcelableExtra("reply");
        try{reply.send(c,0,new Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND).putExtra("role",role).putExtra("detail",detail));}catch(PendingIntent.CanceledException ignored){}
    }
}
