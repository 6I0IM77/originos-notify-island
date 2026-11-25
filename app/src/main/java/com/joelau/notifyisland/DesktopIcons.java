package com.joelau.notifyisland;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;

/** Use the launcher entry's system-loaded icon, preserving its mask and transparent outline. */
final class DesktopIcons {
    static Drawable load(Context context,String pkg)throws PackageManager.NameNotFoundException{
        PackageManager pm=context.getPackageManager();
        Intent launch=pm.getLaunchIntentForPackage(pkg);
        if(launch!=null&&launch.getComponent()!=null){
            try{return pm.getActivityIcon(launch.getComponent()).mutate();}
            catch(PackageManager.NameNotFoundException ignored){}
        }
        return pm.getApplicationIcon(pkg).mutate();
    }
    static Icon icon(Context context,String pkg)throws PackageManager.NameNotFoundException{
        return Icon.createWithBitmap(bitmap(context,pkg));
    }
    static Bitmap bitmap(Context context,String pkg)throws PackageManager.NameNotFoundException{
        Drawable drawable=load(context,pkg);
        // Do not wrap a pre-masked icon in createWithAdaptiveBitmap: that applies another
        // mask. Draw the OS drawable (including AdaptiveIconDrawable's own mask) into ARGB.
        int size=Math.max(96,Math.min(256,Math.round(48*context.getResources().getDisplayMetrics().density)));
        Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        int width=drawable.getIntrinsicWidth(),height=drawable.getIntrinsicHeight();
        if(width<=0||height<=0){width=size;height=size;}
        float scale=Math.min((float)size/width,(float)size/height);
        int w=Math.max(1,Math.round(width*scale)),h=Math.max(1,Math.round(height*scale));
        Rect old=new Rect(drawable.getBounds());
        drawable.setBounds((size-w)/2,(size-h)/2,(size+w)/2,(size+h)/2);
        drawable.draw(new Canvas(bitmap));drawable.setBounds(old);
        return bitmap;
    }
}
