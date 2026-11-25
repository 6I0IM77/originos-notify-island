package com.joelau.notifyisland;

import android.content.Context;
import android.app.Activity;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=android.app.Application.class)
public class CardTouchDispatchTest {
    /** Reduced OEM parent model, based on the supplied OS5/OS7 CapsuleSwipeHelper.
     * The test begins inside the visible expanded card, below the status bar.
     * We retain the direction gate, arming MOVE, intercepting MOVE and terminal reset.
     * Android ViewGroup itself performs dispatch, child cancellation and disallow handling. */
    static class OemParent extends FrameLayout {
        final boolean os7;
        final int slop;
        boolean armed;
        float downX,downY;
        int interceptions;
        CardPullGesture modulePull;
        int nativeResets;
        OemParent(Context ctx,boolean os7){super(ctx);this.os7=os7;slop=ViewConfiguration.get(ctx).getScaledTouchSlop();}
        @Override public boolean onInterceptTouchEvent(MotionEvent e){
            int action=e.getActionMasked();
            // Production hook returns false before the ROM helper runs, and clears
            // only an armed expanded-card drag. Reuse the production policy here.
            if(modulePull!=null&&modulePull.reserveNative(action,e.getPointerCount(),e.getRawX(),e.getRawY())){
                if(armed){armed=false;nativeResets++;}
                return false;
            }
            if(action==MotionEvent.ACTION_DOWN){armed=false;downX=e.getRawX();downY=e.getRawY();return false;}
            float dx=e.getRawX()-downX,dy=e.getRawY()-downY;
            if(armed){interceptions++;if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)armed=false;return true;}
            boolean threshold=os7?(Math.abs(dx)>=slop||Math.abs(dy)>=slop):(Math.abs(dx)>=slop||dy<=-slop);
            double angle=Math.toDegrees(Math.atan2(Math.abs(dy),Math.abs(dx)));
            if(action==MotionEvent.ACTION_MOVE&&threshold&&(os7||dy<=0||angle<=15))armed=true;
            return false;
        }
        @Override public boolean onTouchEvent(MotionEvent e){return true;}
    }
    static class Rig {
        final OemParent parent;
        final AuditedCard card;
        final float density;
        long time=100;
        Rig(boolean os7){
            Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
            Context ctx=activity;density=ctx.getResources().getDisplayMetrics().density;
            parent=new OemParent(ctx,os7);card=new AuditedCard(ctx);
            parent.modulePull=card.pull;
            parent.addView(card,new FrameLayout.LayoutParams(-1,-1));
            activity.setContentView(parent);
            parent.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY));
            parent.layout(0,0,1000,1000);
        }
        void event(int action,float dx,float dy){
            MotionEvent e=MotionEvent.obtain(100,time+=16,action,100+dx*density,100+dy*density,0);
            parent.dispatchTouchEvent(e);e.recycle();
        }
        void straightPull(){
            event(MotionEvent.ACTION_DOWN,0,0);
            event(MotionEvent.ACTION_MOVE,1,12);
            event(MotionEvent.ACTION_MOVE,2,24);
            event(MotionEvent.ACTION_MOVE,3,48);
            event(MotionEvent.ACTION_UP,3,48);
            shadowOf(Looper.getMainLooper()).idle();
        }
    }
    @Test public void os7StraightPullKeepsEventsThroughSingleLaunch(){
        Rig r=new Rig(true);r.straightPull();
        assertEquals(0,r.parent.interceptions);
        assertTrue(r.card.motion.drags>0);
        assertEquals(1,r.card.host.launches);
    }
    @Test public void os5StraightPullReachesTheSameLaunchCallback(){
        Rig r=new Rig(false);r.straightPull();
        assertEquals(0,r.parent.interceptions);
        assertTrue(r.card.motion.drags>0);
        assertEquals(1,r.card.host.launches);
    }
    @Test public void os5InitiallySidewaysThenDownwardResetsArmedNativeDrag(){
        Rig r=new Rig(false);
        float sideways=r.parent.slop/r.density+4;
        r.event(MotionEvent.ACTION_DOWN,0,0);
        r.event(MotionEvent.ACTION_MOVE,sideways,2);
        r.event(MotionEvent.ACTION_MOVE,sideways+2,35);
        r.event(MotionEvent.ACTION_MOVE,sideways+3,60);
        r.event(MotionEvent.ACTION_UP,sideways+3,60);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0,r.parent.interceptions);
        assertEquals(1,r.parent.nativeResets);
        assertFalse(r.parent.armed);
        assertEquals(1,r.card.host.launches);
    }
    @Test public void noModuleHookStillReproducesOldOs7Cancellation(){
        Rig r=new Rig(true);
        r.parent.modulePull=null;
        r.straightPull();
        assertTrue(r.parent.interceptions>0);
        assertTrue(r.card.motion.returns>0);
        assertEquals(0,r.card.host.launches);
    }
    @Test public void idleScrollableBodyStillAllowsStraightPullOnOs5(){
        Rig r=new Rig(false);
        ScrollView scroll=new ScrollView(r.card.getContext());
        TextView body=new TextView(r.card.getContext());body.setText("Long message\n".repeat(100));body.setClickable(true);
        scroll.addView(body);r.card.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        r.parent.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY));
        r.parent.layout(0,0,1000,1000);
        r.straightPull();
        assertEquals(1,r.card.host.launches);
    }
    @Test public void explicitCancelNeverLaunches(){
        Rig r=new Rig(false);
        r.event(MotionEvent.ACTION_DOWN,0,0);r.event(MotionEvent.ACTION_MOVE,0,12);r.event(MotionEvent.ACTION_MOVE,0,48);
        r.event(MotionEvent.ACTION_CANCEL,0,48);shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0,r.card.host.launches);assertTrue(r.card.motion.returns>0);
    }
    @Test public void subThresholdPullReturnsWithoutLaunch(){
        Rig r=new Rig(false);
        r.event(MotionEvent.ACTION_DOWN,0,0);r.event(MotionEvent.ACTION_MOVE,0,12);r.event(MotionEvent.ACTION_MOVE,0,24);
        r.event(MotionEvent.ACTION_UP,0,24);shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0,r.card.host.launches);assertTrue(r.card.motion.returns>0);
    }
    @Test public void upwardSwipeRemainsNativeOnBothSystems(){
        for(boolean os7:new boolean[]{false,true}){
            Rig r=new Rig(os7);
            r.event(MotionEvent.ACTION_DOWN,0,0);r.event(MotionEvent.ACTION_MOVE,0,-24);
            r.event(MotionEvent.ACTION_MOVE,0,-48);r.event(MotionEvent.ACTION_UP,0,-64);
            shadowOf(Looper.getMainLooper()).idle();
            assertTrue(r.parent.interceptions>0);assertEquals(0,r.card.host.launches);
            assertFalse(r.card.pull.reserveNative(MotionEvent.ACTION_MOVE,1,100,200));
        }
    }
    @Test public void horizontalSwipeRemainsNativeOnBothSystems(){
        for(boolean os7:new boolean[]{false,true}){
            Rig r=new Rig(os7);
            r.event(MotionEvent.ACTION_DOWN,0,0);r.event(MotionEvent.ACTION_MOVE,24,0);
            r.event(MotionEvent.ACTION_MOVE,48,0);r.event(MotionEvent.ACTION_UP,80,0);
            shadowOf(Looper.getMainLooper()).idle();
            assertTrue(r.parent.interceptions>0);assertEquals(0,r.card.host.launches);
        }
    }
    @Test public void replyButtonTapWorksAndEndsChildOwnedSequence(){
        Rig r=new Rig(true);int[] taps={0};
        Button button=new Button(r.card.getContext());button.setOnClickListener(v->taps[0]++);
        r.card.addView(button,new LinearLayout.LayoutParams(-1,-1));
        r.parent.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY));
        r.parent.layout(0,0,1000,1000);
        r.event(MotionEvent.ACTION_DOWN,0,0);r.event(MotionEvent.ACTION_UP,0,0);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1,taps[0]);assertEquals(0,r.card.host.launches);
        assertFalse(r.card.pull.reserveNative(MotionEvent.ACTION_MOVE,1,100,250));
    }
    @Test public void pullFromButtonCancelsClickAndCommitsOnlySmallWindow(){
        Rig r=new Rig(true);int[] taps={0};
        Button button=new Button(r.card.getContext());button.setOnClickListener(v->taps[0]++);
        r.card.addView(button,new LinearLayout.LayoutParams(-1,-1));
        r.parent.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY));
        r.parent.layout(0,0,1000,1000);r.straightPull();
        assertEquals(0,taps[0]);assertEquals(1,r.card.host.launches);
    }
    @Test public void reversedPullDoesNotHandBackToNativeOrLaunch(){
        Rig r=new Rig(true);
        r.event(MotionEvent.ACTION_DOWN,0,0);r.event(MotionEvent.ACTION_MOVE,0,24);r.event(MotionEvent.ACTION_MOVE,0,48);
        r.event(MotionEvent.ACTION_MOVE,0,-24);r.event(MotionEvent.ACTION_UP,0,-40);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0,r.parent.interceptions);assertEquals(0,r.card.host.launches);assertTrue(r.card.motion.returns>0);
        assertFalse(r.card.pull.reserveNative(MotionEvent.ACTION_MOVE,1,100,250));
    }
    @Test public void nextSequenceIsCleanAfterCancelAndCanLaunch(){
        Rig r=new Rig(true);
        r.event(MotionEvent.ACTION_DOWN,0,0);r.event(MotionEvent.ACTION_MOVE,0,24);r.event(MotionEvent.ACTION_CANCEL,0,24);
        assertFalse(r.card.pull.reserveNative(MotionEvent.ACTION_MOVE,1,100,250));
        r.straightPull();assertEquals(1,r.card.host.launches);
    }
    @Test public void multiTouchKeepsNativeBlockedButCancelsCommitUntilSequenceEnds(){
        CardPullGesture g=new CardPullGesture(32,64,8);
        assertFalse(g.reserveNative(2,1,0,50));
        g.begin(0,0);assertTrue(g.reserveNative(2,1,0,20));
        assertTrue(g.reserveNative(5,2,0,24));assertFalse(g.dragging());
        assertTrue(g.reserveNative(2,1,0,60));assertFalse(g.finish(0,60));
        g.endSequence();assertFalse(g.reserveNative(2,1,0,80));
        g.begin(0,0);assertTrue(g.reserveNative(2,1,0,20));assertTrue(g.finish(0,40));g.endSequence();
    }
    @Test public void detachmentOrReplacementEndsReservation(){
        Rig r=new Rig(true);
        r.event(MotionEvent.ACTION_DOWN,0,0);r.event(MotionEvent.ACTION_MOVE,0,24);
        // NativeIsland.onDetachedFromWindow and replacement onAttachedToWindow both call this.
        r.card.pull.endSequence();
        assertFalse(r.card.pull.reserveNative(MotionEvent.ACTION_UP,1,100,250));
        assertFalse(r.card.pull.finish(100,250));
    }
    @Test public void actualMultiTouchDispatchNeverClicksOrLaunches(){
        Rig r=new Rig(true);int[] taps={0};r.card.setOnClickListener(v->taps[0]++);
        r.event(MotionEvent.ACTION_DOWN,0,0);
        MotionEvent.PointerProperties[] properties={new MotionEvent.PointerProperties(),new MotionEvent.PointerProperties()};
        MotionEvent.PointerCoords[] coords={new MotionEvent.PointerCoords(),new MotionEvent.PointerCoords()};
        for(int i=0;i<2;i++){properties[i].id=i;properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i].x=100+i*30;coords[i].y=100;coords[i].pressure=1;coords[i].size=1;}
        MotionEvent multi=MotionEvent.obtain(100,r.time+=16,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,properties,coords,0,0,1,1,0,0,0,0);
        r.parent.dispatchTouchEvent(multi);multi.recycle();
        r.event(MotionEvent.ACTION_MOVE,0,48);r.event(MotionEvent.ACTION_UP,0,48);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0,taps[0]);assertEquals(0,r.card.host.launches);
        assertFalse(r.card.pull.reserveNative(2,1,100,250));
    }
    @Test public void scrollingLongBodyUpwardsStillWorks(){
        Rig r=new Rig(true);
        ScrollView scroll=new ScrollView(r.card.getContext());
        TextView body=new TextView(r.card.getContext());body.setText("Long message\n".repeat(100));body.setClickable(true);
        scroll.addView(body);r.card.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        r.parent.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY));
        r.parent.layout(0,0,1000,1000);
        r.event(MotionEvent.ACTION_DOWN,0,0);r.event(MotionEvent.ACTION_MOVE,0,-24);
        r.event(MotionEvent.ACTION_MOVE,0,-48);r.event(MotionEvent.ACTION_UP,0,-64);
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue("body should scroll",scroll.getScrollY()>0);assertEquals(0,r.card.host.launches);
    }
}
