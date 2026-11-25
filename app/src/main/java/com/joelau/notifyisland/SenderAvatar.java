package com.joelau.notifyisland;

import android.app.Notification;
import android.app.Person;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.os.Parcelable;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

/** Resolve images in SystemUI, where notification URI grants exist. Send bounded pixels,
 * never an app-owned URI or resource id, to the separately running island plugin. */
final class SenderAvatar {
    static final String EXTRA="sender_avatar";
    static final int SIDE=96,MAX_BYTES=64*1024;
    static byte[] extract(Context systemContext,String pkg,Notification notification,OriginProfile profile){
        Bundle extras=notification.extras==null?Bundle.EMPTY:notification.extras;
        Context appContext=systemContext;
        try{appContext=systemContext.createPackageContext(pkg,Context.CONTEXT_RESTRICTED);}catch(Exception ignored){}
        // All three releases can publish MessagingStyle. Only the newest message owns
        // the displayed sender; never borrow an earlier group participant's portrait.
        byte[] portrait=personAvatar(systemContext,appContext,newestPerson(extras));
        if(portrait!=null)return portrait;
        // Legacy QQ/WeChat notifications often expose a large icon instead of a Person.
        // OS5 retains the extras Bitmap path; OS6/7 prefer the public Icon accessor.
        switch(profile){
            case OS5 -> {
                portrait=legacyAvatar(systemContext,appContext,extras);
                if(portrait==null)portrait=iconAvatar(systemContext,appContext,largeIcon(notification));
            }
            case OS6 -> {
                portrait=iconAvatar(systemContext,appContext,largeIcon(notification));
                if(portrait==null)portrait=legacyAvatar(systemContext,appContext,extras);
            }
            case OS7 -> {
                // OS7 still uses Android's Person/Icon contract; no private avatar view ids.
                portrait=iconAvatar(systemContext,appContext,largeIcon(notification));
                if(portrait==null)portrait=legacyAvatar(systemContext,appContext,extras);
            }
            default -> {
                portrait=iconAvatar(systemContext,appContext,largeIcon(notification));
                if(portrait==null)portrait=legacyAvatar(systemContext,appContext,extras);
            }
        }
        return portrait;
    }
    private static Person newestPerson(Bundle extras){
        try{
            Parcelable[] messages=extras.getParcelableArray(Notification.EXTRA_MESSAGES);
            if(messages!=null&&messages.length>0&&messages[messages.length-1] instanceof Bundle last)
                return last.getParcelable("sender_person",Person.class);
            // Single-sender legacy Person metadata is useful only when its name matches
            // the notification title. EXTRA_MESSAGING_PERSON is the current user, not sender.
            CharSequence title=extras.getCharSequence(Notification.EXTRA_TITLE);
            ArrayList<Person> people=extras.getParcelableArrayList(Notification.EXTRA_PEOPLE_LIST,Person.class);
            if(title!=null&&people!=null)for(Person person:people)
                if(person!=null&&person.getName()!=null&&title.toString().contentEquals(person.getName()))return person;
        }catch(RuntimeException ignored){}
        return null;
    }
    private static Icon largeIcon(Notification n){try{return n.getLargeIcon();}catch(RuntimeException ignored){return null;}}
    private static byte[] personAvatar(Context system,Context app,Person person){
        return person==null?null:iconAvatar(system,app,person.getIcon());
    }
    private static byte[] legacyAvatar(Context system,Context app,Bundle extras){
        try{
            Parcelable value=extras.getParcelable(Notification.EXTRA_LARGE_ICON);
            if(value instanceof Bitmap bitmap)return encode(new BitmapDrawable(system.getResources(),bitmap));
            if(value instanceof Icon icon)return iconAvatar(system,app,icon);
        }catch(RuntimeException ignored){}
        return null;
    }
    private static byte[] iconAvatar(Context system,Context app,Icon icon){
        if(icon==null)return null;
        // Resolve resource icons against the source application and URI icons with the
        // listener's identity. Either failure must leave notification delivery working.
        for(Context context:new Context[]{icon.getType()==Icon.TYPE_RESOURCE?app:system,app}){
            try{byte[] bytes=encode(icon.loadDrawable(context));if(bytes!=null)return bytes;}
            catch(RuntimeException ignored){}
        }
        return null;
    }
    private static byte[] encode(Drawable drawable){
        if(drawable==null)return null;
        int width=drawable.getIntrinsicWidth(),height=drawable.getIntrinsicHeight();
        if(width<=0||height<=0)return null;
        Bitmap tile=Bitmap.createBitmap(SIDE,SIDE,Bitmap.Config.ARGB_8888);
        Rect previous=new Rect(drawable.getBounds());
        try{
            float scale=Math.max((float)SIDE/width,(float)SIDE/height);
            int w=Math.round(width*scale),h=Math.round(height*scale);
            drawable.setBounds((SIDE-w)/2,(SIDE-h)/2,(SIDE+w)/2,(SIDE+h)/2);
            drawable.draw(new Canvas(tile));
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            if(!tile.compress(Bitmap.CompressFormat.PNG,100,out)||out.size()>MAX_BYTES)return null;
            return out.toByteArray();
        }finally{drawable.setBounds(previous);tile.recycle();}
    }
    static Bitmap decode(byte[] bytes){
        if(bytes==null||bytes.length==0||bytes.length>MAX_BYTES)return null;
        try{
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
            if(bounds.outWidth<=0||bounds.outHeight<=0||bounds.outWidth>SIDE||bounds.outHeight>SIDE)return null;
            return BitmapFactory.decodeByteArray(bytes,0,bytes.length);
        }catch(RuntimeException ignored){return null;}
    }
}
