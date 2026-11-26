package com.joelau.notifyisland;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.View;
import android.view.ViewOutlineProvider;

/** Equal-aspect rounded-square icons. Never stretch the desktop artwork. */
final class IslandIcons {
    private static float roundRatio=.30f;
    private static String styleSource="fallback 6/20";
    private static boolean lastNormalized;
    static String diagnostics(){return "0.1.33; radiusRatio="+roundRatio+"; source="+styleSource+"; normalized="+lastNormalized;}
    static void useNativeStyle(Context context,ClassLoader loader)throws ReflectiveOperationException{
        Context nativeContext=new NativeDensity(loader).fix(context);
        Class<?> dimen=Class.forName("com.vivo.island.R$dimen",false,loader);
        int radiusId=dimen.getField("atom_is1and_left_or_right_image_round_size").getInt(null);
        int widthId=dimen.getField("atom_is1and_left_or_right_image_width").getInt(null);
        float radius=nativeContext.getResources().getDimensionPixelOffset(radiusId);
        float width=nativeContext.getResources().getDimensionPixelOffset(widthId);
        if(width<=0||radius<=0||radius>width/2)throw new IllegalStateException("Invalid native icon outline dimensions");
        roundRatio=radius/width;
        styleSource="native island dimensions "+radius+"/"+width;
    }
    static void clipView(View icon){
        icon.setOutlineProvider(new ViewOutlineProvider(){
            @Override public void getOutline(View view,Outline outline){
                outline.setRoundRect(0,0,view.getWidth(),view.getHeight(),Math.min(view.getWidth(),view.getHeight())*roundRatio);
            }
        });
        icon.setClipToOutline(true);
    }
    static int widthForHeight(int height){return height;}
    static Drawable drawable(Context context,String pkg)throws PackageManager.NameNotFoundException{
        return new RoundedArtwork(bitmap(context,pkg));
    }
    static Drawable portrait(Bitmap bitmap){return new RoundedArtwork(bitmap);}
    /** Draw the shape at its final bounds, independent of BitmapDrawable density/outline state. */
    private static final class RoundedArtwork extends Drawable {
        private final Bitmap bitmap;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final BitmapShader shader;
        private final Matrix matrix=new Matrix();
        RoundedArtwork(Bitmap source){bitmap=source;shader=new BitmapShader(source,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);paint.setShader(shader);}
        @Override public void draw(Canvas canvas){
            Rect bounds=getBounds();if(bounds.isEmpty())return;
            float side=Math.min(bounds.width(),bounds.height());
            float left=bounds.exactCenterX()-side/2,top=bounds.exactCenterY()-side/2;
            matrix.setScale(side/bitmap.getWidth(),side/bitmap.getHeight());matrix.postTranslate(left,top);shader.setLocalMatrix(matrix);
            canvas.drawRoundRect(left,top,left+side,top+side,side*roundRatio,side*roundRatio,paint);
        }
        @Override public int getIntrinsicWidth(){return bitmap.getWidth();}
        @Override public int getIntrinsicHeight(){return bitmap.getHeight();}
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
        @Override public int getAlpha(){return paint.getAlpha();}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    static Icon icon(Context context,String pkg)throws PackageManager.NameNotFoundException{
        return Icon.createWithBitmap(bitmap(context,pkg));
    }
    private static Bitmap bitmap(Context context,String pkg)throws PackageManager.NameNotFoundException{
        Drawable artwork=DesktopIcons.load(context,pkg);
        int size=Math.max(96,Math.min(256,Math.round(48*context.getResources().getDisplayMetrics().density)));
        Bitmap source;
        if(artwork instanceof AdaptiveIconDrawable adaptive){
            source=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(source);
            // Render adaptive layers without their desktop mask. Keep Android's normal
            // extra-inset geometry so the symbol does not grow relative to its background.
            int inset=Math.round(size*AdaptiveIconDrawable.getExtraInsetFraction());
            for(Drawable layer:new Drawable[]{adaptive.getBackground(),adaptive.getForeground()})if(layer!=null){
                layer.setBounds(-inset,-inset,size+inset,size+inset);layer.draw(canvas);
            }
        }else source=DesktopIcons.bitmap(context,pkg);
        // Launcher icons can already be masked and carry a transparent border. A round mask
        // applied to the outer tile then only cuts that invisible border, leaving the visible
        // square content untouched. Normalize a centered, mostly-opaque content box first.
        Rect content=contentBounds(source);
        boolean normalized=shouldNormalize(content,size,size);
        Bitmap drawn=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        if(normalized){
            // Preserve aspect and fill the tile; the source is an app icon, so this only
            // removes transparent padding and does not stretch the artwork.
            float scale=Math.max((float)size/content.width(),(float)size/content.height());
            float width=content.width()*scale,height=content.height()*scale;
            RectF destination=new RectF((size-width)/2,(size-height)/2,(size+width)/2,(size+height)/2);
            new Canvas(drawn).drawBitmap(source,content,destination,paint);
        }else{
            new Canvas(drawn).drawBitmap(source,0,0,paint);
        }
        Bitmap tile=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        Paint mask=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        mask.setShader(new BitmapShader(drawn,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP));
        new Canvas(tile).drawRoundRect(0,0,size,size,size*roundRatio,size*roundRatio,mask);
        drawn.recycle();
        source.recycle();
        lastNormalized=normalized;
        return tile;
    }
    private static Rect contentBounds(Bitmap bitmap){
        int width=bitmap.getWidth(),height=bitmap.getHeight(),left=width,top=height,right=-1,bottom=-1;
        int[] row=new int[width];
        for(int y=0;y<height;y++){
            bitmap.getPixels(row,0,width,0,y,width,1);
            for(int x=0;x<width;x++){
                if((row[x]>>>24)<8)continue;
                if(x<left)left=x;if(x>right)right=x;
                if(y<top)top=y;if(y>bottom)bottom=y;
            }
        }
        return right<0?null:new Rect(left,top,right+1,bottom+1);
    }
    private static boolean shouldNormalize(Rect content,int width,int height){
        if(content==null||content.width()<=0||content.height()<=0)return false;
        float coverage=(float)content.width()*content.height()/(width*height);
        if(coverage<.55f)return false;
        return Math.abs(content.exactCenterX()-width/2f)<=width*.08f
                &&Math.abs(content.exactCenterY()-height/2f)<=height*.08f;
    }
}

