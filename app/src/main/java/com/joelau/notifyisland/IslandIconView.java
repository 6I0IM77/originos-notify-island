package com.joelau.notifyisland;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Icon owns the final draw, avoiding themed ImageView padding and bitmap density transforms. */
final class IslandIconView extends View {
    private final Drawable artwork;
    IslandIconView(Context context,String pkg)throws android.content.pm.PackageManager.NameNotFoundException{
        super(context);artwork=IslandIcons.drawable(context,pkg);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    IslandIconView(Context context,android.graphics.Bitmap portrait){
        super(context);artwork=IslandIcons.portrait(portrait);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);artwork.setBounds(0,0,getWidth(),getHeight());artwork.draw(canvas);
    }
    String diagnostics(){return "view="+getClass().getName()+"; bounds="+getWidth()+"x"+getHeight()+"; drawable="+artwork.getClass().getName()+"; "+IslandIcons.diagnostics();}
}
