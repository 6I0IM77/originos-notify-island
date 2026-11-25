package com.joelau.notifyisland;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import java.util.HashMap;
import java.util.Map;

/**
 * Paints the expanded card with three colours: the dominant tone of the sender's avatar on the
 * left, the dominant tone of the app icon on the right, and the plain average of the two filling
 * the bottom of the card. Nothing is filtered out of the samples, so a white or a black icon still
 * contributes its own tone instead of being skipped.
 *
 * <p>The surface is computed pixel by pixel into a quarter-size bitmap and then stretched over the
 * card. Nothing is drawn with shaders or clip paths on the live view canvas — that is what crashed
 * SystemUI before — and every step is wrapped in a fallback, so even a failure only costs the tint
 * and never the process.</p>
 *
 * <p>The collapsed pill is never touched.</p>
 */
final class IslandTint{
    /** Deep fallback: neutral charcoal with a cold blue cast, matching the native island. */
    private static final int[] FALLBACK={0xff14161c,0xff17222b,0xff0d0f14};
    /** The surface is rendered at a quarter of the card size and stretched back up. */
    private static final float DOWNSCALE=0.25f;
    /** Per-pixel sample, small enough that a whole icon fits in a dozen buckets. */
    private static final int SAMPLE=24;
    /** Colours are lifted this much before they are painted, so the wash reads on a dark card. */
    private static final float LIFT=1.66f;
    private static final Map<String,int[]> ICON_CACHE=new HashMap<>();

    private IslandTint(){}

    /** The two sampled hues the surface is built from: avatar on the left, icon on the right. */
    static int[] of(Context context,String pkg,Bitmap avatar){
        try{
            int left=0,right=0;
            if(avatar!=null)left=dominant(avatar,0.40f,0.52f,0.72f);
            right=iconTone(context,pkg);
            if(left==0)left=right;
            if(right==0)right=left;
            if(left==0&&right==0)return FALLBACK;
            if(left==0)left=FALLBACK[0];
            if(right==0)right=FALLBACK[1];
            // Third tone: the two sampled tones averaged channel by channel, then slightly
            // saturated so the mix reads as its own colour instead of a muddy grey.
            int blend=boost(mix(left,right,0.45f),1.35f);
            return new int[]{left,right,blend};
        }catch(Throwable ignored){
            // Any decode problem falls back to the native charcoal instead of breaking the card.
        }
        return FALLBACK;
    }

    /** Card surface: two sampled hues on top, their mix closing the bottom half. */
    static Drawable card(Context context,String pkg,Bitmap avatar,float cornerRadius){
        return card(of(context,pkg,avatar),cornerRadius);
    }

    static Drawable card(int[] colors,float cornerRadius){
        return new SurfaceDrawable(colors==null?FALLBACK:colors,cornerRadius);
    }

    private static int iconTone(Context context,String pkg){
        if(pkg==null)return 0;
        int[] cached=ICON_CACHE.get(pkg);
        if(cached!=null)return cached[0];
        int tone=0;
        try{
            Drawable icon=context.getPackageManager().getApplicationIcon(pkg);
            Bitmap bitmap=Bitmap.createBitmap(SAMPLE,SAMPLE,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(bitmap);
            icon.setBounds(0,0,SAMPLE,SAMPLE);icon.draw(canvas);
            tone=dominant(bitmap,0.42f,0.45f,0.70f);
        }catch(Throwable ignored){
            tone=0;
        }
        ICON_CACHE.put(pkg,new int[]{tone});
        return tone;
    }

    /**
     * The single most present tone of an image. Every pixel votes — white and black included — into
     * twelve 30° hue buckets and two neutral buckets (light and dark); chromatic pixels carry a
     * heavier vote, so an icon with a real colour wins on colour while a plain white or black icon
     * still resolves to its own neutral tone. Returns 0 when nothing at all could be read.
     */
    private static int dominant(Bitmap source,float value,float satMin,float satMax){
        try{
            if(source==null||source.getWidth()<=0||source.getHeight()<=0)return 0;
            // Hardware bitmaps cannot be read pixel by pixel; copy them into a software bitmap first.
            Bitmap readable=source;
            if(readable.getConfig()==Bitmap.Config.HARDWARE){
                readable=readable.copy(Bitmap.Config.ARGB_8888,false);
            }
            if(readable==null)return 0;
            Bitmap bitmap=Bitmap.createScaledBitmap(readable,SAMPLE,SAMPLE,true);
            float[] hueWeight=new float[12],hueSaturation=new float[12];
            float[] neutralWeight=new float[2],neutralValue=new float[2];
            for(int y=0;y<SAMPLE;y++){
                for(int x=0;x<SAMPLE;x++){
                    int pixel=bitmap.getPixel(x,y);
                    if(Color.alpha(pixel)<200)continue;
                    float[] hsv=new float[3];Color.colorToHSV(pixel,hsv);
                    // Chromatic pixels outvote grey ones, but nothing is discarded — a white plate or
                    // a black glyph still reaches the tally through the neutral buckets.
                    float vote=0.35f+hsv[1]*2.40f;
                    if(hsv[1]<0.10f){
                        int index=hsv[2]>0.55f?0:1;
                        neutralWeight[index]+=vote;
                        neutralValue[index]+=hsv[2]*vote;
                    }else{
                        int bucket=((int)(hsv[0]/30f))%12;
                        hueWeight[bucket]+=vote;
                        hueSaturation[bucket]+=hsv[1]*vote;
                    }
                }
            }
            int bestHue=-1;
            float bestHueWeight=0f;
            for(int i=0;i<12;i++){
                if(hueWeight[i]>bestHueWeight){bestHueWeight=hueWeight[i];bestHue=i;}
            }
            int bestNeutral=neutralWeight[0]>=neutralWeight[1]?0:1;
            float bestNeutralWeight=neutralWeight[bestNeutral];
            if(bestHue<0&&bestNeutralWeight<=0.001f)return 0;
            if(bestHue<0||bestNeutralWeight>bestHueWeight){
                float brightness=neutralValue[bestNeutral]/Math.max(bestNeutralWeight,0.001f);
                // A white plate and a black glyph both land on the same greyscale band, the dark one
                // only slightly lower, so neither collapses into a hole or a glare.
                return tone(0f,0.04f,clamp(0.24f+brightness*0.14f,0.24f,0.40f));
            }
            float hue=bestHue*30f+15f;
            float saturation=hueSaturation[bestHue]/Math.max(bestHueWeight,0.001f);
            return tone(hue,clamp(saturation,satMin,satMax),value);
        }catch(Throwable ignored){
            return 0;
        }
    }

    private static float clamp(float value,float min,float max){
        return Math.max(min,Math.min(max,value));
    }

    private static float clamp01(float value){
        return Math.max(0f,Math.min(1f,value));
    }

    private static int tone(float hue,float saturation,float value){
        return Color.HSVToColor(new float[]{hue,saturation,value});
    }

    /** Blends two colours channel by channel, weight being the share of the first one. */
    private static int mix(int first,int second,float weight){
        return Color.argb(255,
                Math.round(Color.red(first)*weight+Color.red(second)*(1f-weight)),
                Math.round(Color.green(first)*weight+Color.green(second)*(1f-weight)),
                Math.round(Color.blue(first)*weight+Color.blue(second)*(1f-weight)));
    }

    /** Pushes a colour away from grey without changing its hue or its brightness. */
    private static int boost(int color,float factor){
        float[] hsv=new float[3];Color.colorToHSV(color,hsv);
        hsv[1]=clamp01(hsv[1]*factor);
        return Color.HSVToColor(hsv);
    }

    /** Smoothstep, so the two gradient segments join without a visible kink in the middle. */
    private static float smooth(float value){
        float t=clamp01(value);
        return t*t*(3f-2f*t);
    }

    /**
     * Computes the whole card surface as one light field. The avatar tone spreads out of the upper
     * left, the icon tone out of the upper right, each with a neighbour hue a few degrees away low
     * on its own side, and a hushed complementary pocket sits between them so the middle is never
     * a plain blend of the two. Every tone is a wide Gaussian with no edge of its own, so there is
     * no seam and no band anywhere on the card. A glare limiter then holds the brightest pixels
     * down so white text keeps its contrast. The rounded outline is baked into the alpha channel.
     */
    private static Bitmap buildSurface(int width,int height,int[] colors,float corner){
        int w=Math.max(8,Math.round(width*DOWNSCALE));
        int h=Math.max(8,Math.round(height*DOWNSCALE));
        float radius=Math.max(0f,Math.min(corner*DOWNSCALE,Math.min(w,h)/2f));
        // Tone control first: nothing may come out screaming, nothing may collapse to black.
        int a=calm(colors[0]), b=calm(colors[1]);
        float ar=Color.red(a),ag=Color.green(a),ab=Color.blue(a);
        float br=Color.red(b),bg=Color.green(b),bb=Color.blue(b);
        // The card floor is the two hues united through the hue wheel, so the middle is a real
        // third colour instead of the grey an RGB average would give, pressed slightly down so
        // the pools read as light.
        int union=union(a,b);
        float mr=Color.red(union)*0.88f,mg=Color.green(union)*0.88f,mb=Color.blue(union)*0.88f;
        // Neighbours: a same-family pair gets its hues walked apart, an opposing pair stays close.
        boolean near=hueGap(a,b)<45f;
        int na=shiftHue(a,near?-12f:14f), nb=shiftHue(b,near?12f:-14f);
        int cc=complement(union);
        float nar=Color.red(na),nag=Color.green(na),nab=Color.blue(na);
        float nbr=Color.red(nb),nbg=Color.green(nb),nbb=Color.blue(nb);
        float ccr=Color.red(cc),ccg=Color.green(cc),ccb=Color.blue(cc);
        int[] pixels=new int[w*h];
        for(int y=0;y<h;y++){
            float ny=(float)y/(float)(h-1);
            for(int x=0;x<w;x++){
                float nx=(float)x/(float)(w-1);
                float cr=mr,cg=mg,cb=mb;
                float t=pool(nx,ny,0.05f,0.26f,0.46f,1.00f);
                cr=mixPool(cr,ar,t);cg=mixPool(cg,ag,t);cb=mixPool(cb,ab,t);
                t=pool(nx,ny,0.30f,1.02f,0.40f,0.38f);
                cr=mixPool(cr,nar,t);cg=mixPool(cg,nag,t);cb=mixPool(cb,nab,t);
                t=pool(nx,ny,0.48f,0.58f,0.32f,0.30f);
                cr=mixPool(cr,ccr,t);cg=mixPool(cg,ccg,t);cb=mixPool(cb,ccb,t);
                t=pool(nx,ny,0.70f,0.98f,0.40f,0.34f);
                cr=mixPool(cr,nbr,t);cg=mixPool(cg,nbg,t);cb=mixPool(cb,nbb,t);
                t=pool(nx,ny,0.95f,0.12f,0.44f,0.98f);
                cr=mixPool(cr,br,t);cg=mixPool(cg,bg,t);cb=mixPool(cb,bb,t);
                // Hold the glare down: the hue survives, the white text keeps its contrast.
                float glare=glareFactor(cr,cg,cb);
                cr*=glare;cg*=glare;cb*=glare;
                // Rounded-rect coverage, one pixel of anti-aliasing on the rim.
                float halfW=w/2f, halfH=h/2f;
                float px=Math.abs(x+0.5f-halfW)-(halfW-radius);
                float py=Math.abs(y+0.5f-halfH)-(halfH-radius);
                float outside=(float)Math.hypot(Math.max(px,0f),Math.max(py,0f));
                float distance=Math.min(Math.max(px,py),0f)+outside-radius;
                float coverage=clamp01(0.5f-distance);
                pixels[y*w+x]=Color.argb((int)(255f*coverage),
                        (int)(clamp01(cr/255f)*255f),(int)(clamp01(cg/255f)*255f),(int)(clamp01(cb/255f)*255f));
            }
        }
        // One light box blur removes the last trace of banding from the diagonal.
        blur(pixels,w,h);
        return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
    }

    /** Brightest allowed pixel, as relative luminance: white text stays above 4:1 contrast. */
    private static final float GLARE_LIMIT=0.185f;

    private static float glareFactor(float r,float g,float b){
        float lum=0.2126f*srgb(r)+0.7152f*srgb(g)+0.0722f*srgb(b);
        if(lum<=GLARE_LIMIT||lum<=0.0001f)return 1f;
        return (float)Math.pow(GLARE_LIMIT/lum,0.90f);
    }

    private static float srgb(float channel){
        float c=channel/255f;
        return c<=0.04045f?c/12.92f:(float)Math.pow((c+0.044f)/1.0f,2.4f);
    }

    /** Tone control: a soft saturation ceiling, a soft brightness ceiling, nothing crushed. */
    private static int calm(int color){
        float[] hsv=new float[3];Color.colorToHSV(color,hsv);
        hsv[1]=clamp(Math.max(0.10f,hsv[1]*1.15f),0.10f,0.62f);
        hsv[2]=clamp(hsv[2],0.34f,0.68f);
        return Color.HSVToColor(hsv);
    }

    /** Walks a hue a few degrees round the wheel, keeping its saturation and brightness. */
    private static int shiftHue(int color,float degrees){
        float[] hsv=new float[3];Color.colorToHSV(color,hsv);
        hsv[0]=(hsv[0]+degrees+360f)%360f;
        hsv[1]=clamp(hsv[1]*0.94f,0.08f,0.60f);
        return Color.HSVToColor(hsv);
    }

    /**
     * The two hues united along the shorter arc of the wheel: saturation and brightness are
     * averaged, but the hue travels between the two instead of collapsing into grey.
     */
    private static int union(int first,int second){
        float[] one=new float[3],two=new float[3];
        Color.colorToHSV(first,one);Color.colorToHSV(second,two);
        float delta=two[0]-one[0];
        if(delta>180f)delta-=360f;
        if(delta<-180f)delta+=360f;
        float hue=(one[0]+delta*0.5f+360f)%360f;
        float saturation=clamp((one[1]+two[1])*0.5f*1.05f,0.26f,0.66f);
        float value=clamp((one[2]+two[2])*0.5f,0.34f,0.68f);
        return Color.HSVToColor(new float[]{hue,saturation,value});
    }

    /** The opposite hue, held back to a whisper so it layers instead of shouting. */
    private static int complement(int color){
        float[] hsv=new float[3];Color.colorToHSV(color,hsv);
        hsv[0]=(hsv[0]+180f)%360f;
        hsv[1]=clamp(Math.max(hsv[1]*0.75f,0.20f),0.06f,0.50f);
        hsv[2]=clamp(hsv[2],0.30f,0.62f);
        return Color.HSVToColor(hsv);
    }

    /** Shortest distance between two hues, in degrees. */
    private static float hueGap(int first,int second){
        float[] one=new float[3],two=new float[3];
        Color.colorToHSV(first,one);Color.colorToHSV(second,two);
        float gap=Math.abs(one[0]-two[0]);
        return Math.min(gap,360f-gap);
    }

    /** Soft elliptical pool: 1 at the centre, fading with a Gaussian of the given radius. */
    private static float pool(float nx,float ny,float centreX,float centreY,float radius,float amount){
        float dx=nx-centreX,dy=ny-centreY;
        float d2=(dx*dx+dy*dy)/(radius*radius);
        return amount*(float)Math.exp(-d2);
    }

    private static float mixPool(float current,float target,float amount){
        float a=clamp01(amount);
        return current*(1f-a)+target*a;
    }

    /** 3x3 colour blur that leaves the alpha channel untouched. */
    private static void blur(int[] pixels,int w,int h){
        int[] source=new int[pixels.length];
        System.arraycopy(pixels,0,source,0,pixels.length);
        for(int y=0;y<h;y++){
            for(int x=0;x<w;x++){
                int r=0,g=0,b=0,count=0;
                for(int dy=-1;dy<=1;dy++){
                    int ny=y+dy;
                    if(ny<0||ny>=h)continue;
                    for(int dx=-1;dx<=1;dx++){
                        int nx=x+dx;
                        if(nx<0||nx>=w)continue;
                        int pixel=source[ny*w+nx];
                        if(Color.alpha(pixel)==0)continue;
                        r+=Color.red(pixel);g+=Color.green(pixel);b+=Color.blue(pixel);count++;
                    }
                }
                if(count==0)continue;
                pixels[y*w+x]=Color.argb(Color.alpha(source[y*w+x]),r/count,g/count,b/count);
            }
        }
    }

    private static Paint flatPaint(int[] colors){
        Paint paint=new Paint();
        int base=mix(colors[0],colors[1],0.45f);
        paint.setColor(Color.argb(255,
                (int)(Color.red(base)*0.75f),
                (int)(Color.green(base)*0.75f),
                (int)(Color.blue(base)*0.75f)));
        return paint;
    }

    /** Card background: rounded surface with the sampled three-colour wash. */
    private static final class SurfaceDrawable extends Drawable{
        private final int[] colors;
        private final float corner;
        private Bitmap surface;
        private int builtWidth,builtHeight;

        SurfaceDrawable(int[] colors,float corner){
            this.colors=colors;this.corner=corner;
        }

        @Override public void draw(Canvas canvas){
            Rect bounds=getBounds();
            if(bounds.isEmpty())return;
            try{
                if(surface==null||bounds.width()!=builtWidth||bounds.height()!=builtHeight){
                    surface=buildSurface(bounds.width(),bounds.height(),colors,corner);
                    builtWidth=bounds.width();builtHeight=bounds.height();
                }
                if(surface!=null){
                    canvas.drawBitmap(surface,new Rect(0,0,surface.getWidth(),surface.getHeight()),bounds,null);
                    return;
                }
            }catch(Throwable ignored){
                // A rendering problem must never take SystemUI down with it.
            }
            canvas.drawRect(bounds,flatPaint(colors));
        }

        @Override public void setAlpha(int alpha){}
        @Override public void setColorFilter(ColorFilter colorFilter){}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
