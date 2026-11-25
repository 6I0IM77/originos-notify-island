package com.joelau.notifyisland;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.LinearLayout;

/** Generated fixture: event methods below are copied verbatim from NativeIsland.card().
 * Only OEM rendering/service dependencies are replaced by counters. This is not an OEM runtime. */
class AuditedCard extends LinearLayout {
    final CardPullGesture pull;
    final AuditMotion motion=new AuditMotion();
    final AuditHost host=new AuditHost();
    final Object e=new Object();
    boolean settling,cancelTouch;
    AuditedCard(Context ctx) {
        super(ctx);
        float density=ctx.getResources().getDisplayMetrics().density;
        pull=new CardPullGesture(Math.round(CardPullGesture.COMMIT_DISTANCE_DP*density), Math.round(64*density), ViewConfiguration.get(ctx).getScaledTouchSlop());
        setClickable(true);
    }
    static class AuditMotion {
        int drags,returns;
        void drag(float x,float y){drags++;}
        void springBack(){returns++;}
    }
    static class AuditHost {
        final Handler main=new Handler(Looper.getMainLooper());
        int launches;
        void launch(Object entry,boolean small){if(small)launches++;}
    }
            @Override public boolean dispatchTouchEvent(MotionEvent ev){
                int action=ev.getActionMasked();
                if(action==MotionEvent.ACTION_DOWN){cancelTouch=false;pull.begin(ev.getRawX(),ev.getRawY());}
                try{
                    if(ev.getPointerCount()>1&&!cancelTouch){
                        cancelTouch=true;pull.reserveNative(action,ev.getPointerCount(),ev.getRawX(),ev.getRawY());
                        // Cancel the current child click as well as the pull. Keep this
                        // sequence reserved until its real UP/CANCEL, so the native parent
                        // cannot turn the remaining finger into a swipe or a launch.
                        MotionEvent cancel=MotionEvent.obtain(ev);cancel.setAction(MotionEvent.ACTION_CANCEL);
                        try{super.dispatchTouchEvent(cancel);}finally{cancel.recycle();}
                    }
                    return cancelTouch||super.dispatchTouchEvent(ev);
                }
                finally{if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){pull.endSequence();cancelTouch=false;}}
            }
            @Override public boolean onInterceptTouchEvent(MotionEvent ev){
                if(settling)return true;
                if(ev.getActionMasked()==MotionEvent.ACTION_DOWN)pull.begin(ev.getRawX(),ev.getRawY());
                if(ev.getPointerCount()>1||ev.getActionMasked()==MotionEvent.ACTION_CANCEL)pull.cancel();
                if(ev.getActionMasked()==MotionEvent.ACTION_MOVE&&pull.move(ev.getRawX(),ev.getRawY()))return true;
                return super.onInterceptTouchEvent(ev);
            }
            @Override public boolean onTouchEvent(MotionEvent ev){
                int action=ev.getActionMasked();
                if(settling)return true;
                if(action==MotionEvent.ACTION_DOWN)pull.begin(ev.getRawX(),ev.getRawY());
                if(ev.getPointerCount()>1||action==MotionEvent.ACTION_CANCEL){boolean consumed=pull.dragging();pull.cancel();motion.springBack();return consumed||super.onTouchEvent(ev);}
                if(action==MotionEvent.ACTION_MOVE){pull.move(ev.getRawX(),ev.getRawY());if(pull.dragging())motion.drag(pull.deltaX(ev.getRawX()),pull.deltaY(ev.getRawY()));}
                if(pull.dragging()){
                    if(action==MotionEvent.ACTION_UP){
                        if(pull.finish(ev.getRawX(),ev.getRawY())){
                            performHapticFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE);settling=true;
                            host.main.post(()->{host.launch(e,true);settling=false;});
                        }else motion.springBack();
                    }
                    return true;
                }
                if(action==MotionEvent.ACTION_UP)pull.cancel();
                return super.onTouchEvent(ev);
            }
            @Override public boolean performClick(){return super.performClick();}

}
