package com.joelau.notifyisland;

import android.animation.*;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.view.animation.PathInterpolator;
import java.lang.reflect.Method;

/** A drawable in the existing island window. It never creates an input window or launches a task. */
final class CardMotion {
    private final View card;
    private final Drawable icon;
    private final int splashColor;
    private final Runnable hideNativeMaterial;
    private ViewGroup container;
    private View root;
    private float nativeAlpha,density,content=1,splash,opacity=1;
    private boolean captured,disposed,committing;
    private ValueAnimator animator;
    private Rect cachedTarget;
    private Bitmap snapshot;
    private Shell shell;
    private CardMorphGeometry.Shape source,current;
    private WindowManager windowManager;
    private int originalHeight,temporaryHeight,originalTouchFlag;
    private boolean windowChanged;
    CardMotion(View content,Drawable appIcon,int background,Runnable hideMaterial){card=content;icon=appIcon;splashColor=background;hideNativeMaterial=hideMaterial;}
    boolean covers(Object view){return captured&&container==view;}
    View coveredContainer(){return captured?container:null;}
    void prepare(){if(!disposed)card.post(()->{if(card.isAttachedToWindow())cachedTarget=launchBounds();});}
    private boolean capture(){
        if(disposed||!card.isAttachedToWindow())return false;
        if(captured)return true;
        for(ViewParent p=card.getParent();p instanceof View;p=p.getParent())if(p.getClass().getName().equals("com.vivo.island.view.DynamicCapsuleContainer")){container=(ViewGroup)p;break;}
        if(container==null||card.getWidth()==0||card.getHeight()==0)return false;
        root=card.getRootView();density=card.getResources().getDisplayMetrics().density;
        int[] origin=new int[2],location=new int[2];root.getLocationOnScreen(origin);card.getLocationOnScreen(location);
        source=new CardMorphGeometry.Shape(location[0]-origin[0],location[1]-origin[1],card.getWidth(),card.getHeight(),32*density);
        current=source;nativeAlpha=container.getAlpha();
        try{
            snapshot=Bitmap.createBitmap(card.getWidth(),card.getHeight(),Bitmap.Config.ARGB_8888);
            card.draw(new Canvas(snapshot));
            shell=new Shell();shell.setBounds(0,0,root.getWidth(),root.getHeight());
            root.getOverlay().add(shell);captured=true;container.setAlpha(0);hideNativeMaterial.run();return true;
        }catch(RuntimeException|OutOfMemoryError ex){reset();return false;}
    }
    void drag(float dx,float dy){
        if(committing||!capture())return;
        cancelAnimator();
        current=CardMorphGeometry.drag(source,dx,dy,density);
        float p=CardMorphGeometry.clamp(dy/(64*density));
        content=1-CardMorphGeometry.smooth(p/.85f);splash=CardMorphGeometry.smooth(p*.85f);
        redraw();
    }
    void springBack(){
        if(!captured||disposed||committing)return;
        cancelAnimator();
        CardMorphGeometry.Shape from=current;float oldContent=content,oldSplash=splash;
        ValueAnimator running=ValueAnimator.ofFloat(0,1);animator=running;
        running.setDuration(420);
        running.setInterpolator(t->(float)(1-Math.exp(-7*t)*(Math.cos(10*t)+.7*Math.sin(10*t))));
        running.addUpdateListener(a->{
            float p=(float)a.getAnimatedValue();current=CardMorphGeometry.blend(from,source,p);
            content=CardMorphGeometry.clamp(CardMorphGeometry.mix(oldContent,1,p));splash=CardMorphGeometry.clamp(CardMorphGeometry.mix(oldSplash,0,p));redraw();
        });
        finishListener(running,this::reset);running.start();
    }
    void commit(Runnable launch,Runnable complete){
        if(!capture()){
            // No captured silhouette: start the task now, but retire the source on a
            // short delay so the island does not vanish in the same frame the window pops.
            launch.run();
            card.postDelayed(complete,300);
            return;
        }
        cancelAnimator();committing=true;
        CardMorphGeometry.Shape from=current,target=target();
        // One relayout lets the shell grow beyond the native card's 400dp window. The existing
        // window is untouchable during handoff; no focus flags or native touch regions change.
        extendWindow(target.top()+target.height()+8*density);
        float oldContent=content,oldSplash=splash;
        PathInterpolator curve=new PathInterpolator(.28f,.4f,.2f,1);
        boolean[] sent={false};
        ValueAnimator running=ValueAnimator.ofFloat(0,1);animator=running;
        running.setDuration(760);running.setInterpolator(t->t);
        running.addUpdateListener(a->{
            float elapsed=a.getCurrentPlayTime();
            float p=curve.getInterpolation(CardMorphGeometry.clamp(elapsed/350));
            current=CardMorphGeometry.blend(from,target,p);
            content=oldContent*(1-CardMorphGeometry.smooth(elapsed/100));
            splash=CardMorphGeometry.mix(oldSplash,1,CardMorphGeometry.smooth(elapsed/180));
            // Hold the splash silhouette opaque well past the service handoff, then fade
            // only once the native freeform window has had time to compose underneath.
            opacity=1-CardMorphGeometry.smooth((elapsed-520)/220);
            redraw();
            if(!sent[0]&&elapsed>=280){sent[0]=true;launch.run();}
        });
        finishListener(running,()->{
            if(!sent[0]){sent[0]=true;launch.run();} // system animation duration scale = 0
            complete.run();
        });running.start();
    }
    private CardMorphGeometry.Shape target(){
        int[] origin=new int[2];root.getLocationOnScreen(origin);
        android.util.DisplayMetrics display=card.getResources().getDisplayMetrics();
        float screenWidth=root.getWidth(),screenHeight=display.heightPixels-origin[1];
        Rect bounds=cachedTarget;
        float w=source.width()*.80f,h=Math.min(screenHeight*.62f,w*1.65f);
        float left=source.centerX()-w/2,top=Math.max(current.top(),screenHeight*.17f);
        if(bounds!=null&&!bounds.isEmpty()){
            left=bounds.left-origin[0];top=bounds.top-origin[1];w=bounds.width();h=bounds.height();
        }
        return CardMorphGeometry.target(left,top,w,h,screenWidth,screenHeight,22*density);
    }
    private void extendWindow(float bottom){
        if(!(root.getLayoutParams() instanceof WindowManager.LayoutParams lp))return;
        windowManager=(WindowManager)card.getContext().getSystemService(Context.WINDOW_SERVICE);
        originalHeight=lp.height;originalTouchFlag=lp.flags&WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        temporaryHeight=Math.max(root.getHeight(),(int)Math.ceil(bottom));
        lp.height=temporaryHeight;lp.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;windowChanged=true;
        try{windowManager.updateViewLayout(root,lp);}catch(RuntimeException ignored){restoreWindow();}
    }
    private void restoreWindow(){
        if(!windowChanged)return;windowChanged=false;
        if(!(root.getLayoutParams() instanceof WindowManager.LayoutParams lp))return;
        // A native state update may have already replaced our temporary height.
        if(lp.height==temporaryHeight)lp.height=originalHeight;
        lp.flags=(lp.flags&~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)|originalTouchFlag;
        if(root.isAttachedToWindow())try{windowManager.updateViewLayout(root,lp);}catch(RuntimeException ignored){}
    }
    private void redraw(){
        if(shell!=null){shell.setBounds(0,0,root.getWidth(),Math.max(root.getHeight(),temporaryHeight));shell.invalidateSelf();}
    }
    private void cancelAnimator(){if(animator!=null){ValueAnimator previous=animator;animator=null;previous.cancel();}}
    private void finishListener(ValueAnimator running,Runnable end){
        running.addListener(new AnimatorListenerAdapter(){
            boolean canceled;
            @Override public void onAnimationCancel(Animator a){canceled=true;}
            @Override public void onAnimationEnd(Animator a){
                if(animator==running)animator=null;
                if(!canceled&&!disposed&&card.isAttachedToWindow())end.run();
            }
        });
    }
    private Rect launchBounds(){
        try{
            Class<?> global=Class.forName("android.view.WindowManagerGlobal");
            Object service=global.getMethod("getWindowManagerService").invoke(null);
            Method method=Class.forName("android.view.IWindowManager").getMethod("getFreeformBoundsForNotificationPull",boolean.class,int.class);
            Rect rect=(Rect)method.invoke(service,false,0);
            return rect==null||rect.width()<2||rect.height()<2?null:new Rect(rect);
        }catch(ReflectiveOperationException|RuntimeException ignored){return null;}
    }
    void reset(){
        cancelAnimator();restoreWindow();
        if(shell!=null&&root!=null)root.getOverlay().remove(shell);
        shell=null;
        if(captured&&container!=null)container.setAlpha(nativeAlpha);
        if(container!=null)container.invalidate();
        if(snapshot!=null){snapshot.recycle();snapshot=null;}
        captured=false;committing=false;content=opacity=1;splash=0;temporaryHeight=0;
    }
    void dispose(){reset();disposed=true;}
    private final class Shell extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final RectF rect=new RectF();
        private final Path clip=new Path();
        @Override public void draw(Canvas canvas){
            if(current==null||snapshot==null)return;
            rect.set(current.left(),current.top(),current.left()+current.width(),current.top()+current.height());
            // One AA rounded fill: no glass/material shader, stroke, path-clipped rectangle
            // or changing offscreen layer bounds. Only the final native handoff fades it.
            paint.setColor(CardShellAppearance.fillColor(splashColor,splash));
            paint.setAlpha(CardShellAppearance.alpha(opacity));
            canvas.drawRoundRect(rect,current.radius(),current.radius(),paint);
            if(content>0){
                // Clip just the retiring snapshot. Once content is gone no clipping or
                // intermediate alpha layer is used at all, even during a long held drag.
                int save=canvas.save();
                clip.rewind();clip.addRoundRect(rect,current.radius(),current.radius(),Path.Direction.CW);canvas.clipPath(clip);
                paint.setAlpha(CardShellAppearance.alpha(content*opacity));
                canvas.drawBitmap(snapshot,current.centerX()-snapshot.getWidth()/2f,current.top(),paint);
                canvas.restoreToCount(save);
            }
            if(icon!=null&&content<1){
                float size=28*density;int cx=Math.round(current.centerX()),cy=Math.round(current.centerY());
                icon.setBounds(Math.round(cx-size/2),Math.round(cy-size/2),Math.round(cx+size/2),Math.round(cy+size/2));
                icon.setAlpha(CardShellAppearance.alpha((1-content)*opacity));icon.draw(canvas);
            }
        }
        @Override public void setAlpha(int alpha){}
        @Override public void setColorFilter(ColorFilter filter){}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
