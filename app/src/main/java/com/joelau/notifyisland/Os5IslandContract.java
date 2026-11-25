package com.joelau.notifyisland;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/** OriginOS 5 has no notificationId/permanent builders or launch Binder callback. */
final class Os5IslandContract {
    static boolean matches(Class<?> builder,Class<?> listener){
        return !has(builder,"buildNotificationId",String.class)
            &&!has(builder,"buildPermanent",boolean.class)
            &&!has(listener,"startPendingIntent",PendingIntent.class,int.class);
    }
    private static boolean has(Class<?> owner,String name,Class<?>... args){
        try{owner.getMethod(name,args);return true;}catch(NoSuchMethodException ignored){return false;}
    }
    static int cardHeight(Context context,ClassLoader loader){
        // OS5 RemoteMusicManager forcibly lays out music at 126dp. That measured
        // widget cannot fit our header/body/actions: use the content-safe resource.
        try{
            Context music=context.createPackageContext("com.vivo.musicwidgetmix",0);
            Context fixed=new NativeDensity(loader).fix(music);
            int id=fixed.getResources().getIdentifier("dp_157","dimen","com.vivo.musicwidgetmix");
            if(id!=0)return fixed.getResources().getDimensionPixelSize(id);
        }catch(Exception ignored){}
        return Math.round(157*context.getResources().getDisplayMetrics().density);
    }
    static void launch(Context context,PendingIntent pending)throws PendingIntent.CanceledException{
        // Same path as OS5 IslandUtils.startPendingIntent, but propagate failure so
        // IslandHost retains the message instead of losing it after a swallowed error.
        ActivityOptions options=ActivityOptions.makeBasic();
        options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        pending.send(context,0,new Intent().putExtra("superx.intent.triggerLocation",2),null,null,null,options.toBundle());
    }
}
