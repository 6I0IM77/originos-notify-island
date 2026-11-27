package com.joelau.notifyisland;

public final class CardPullGestureCheck {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        CardPullGesture g=new CardPullGesture(48,64);
        g.begin(100,100);check(!g.move(100,140),"short pull intercepted");check(!g.finish(100,140),"short pull launched");
        g.begin(100,100);check(g.move(104,170),"valid pull not intercepted");check(g.finish(105,180),"valid pull not committed");check(!g.finish(105,180),"UP delivered twice launched twice");
        g.begin(100,100);g.move(100,180);g.cancel();check(!g.finish(100,180),"canceled gesture launched");
        g.begin(100,100);g.move(100,180);check(!g.finish(100,120),"reversed pull launched");
        g.begin(100,100);check(!g.move(180,170),"horizontal drag intercepted");check(!g.finish(180,170),"horizontal drag launched");
        g.begin(100,100);g.move(100,180);g.cancel();check(!g.move(100,210),"multi-touch cancellation restarted");
        g.begin(100,100);check(!g.finish(100,100),"tap launched small window");
        CardPullGesture responsive=new CardPullGesture(64,64,8);
        responsive.begin(100,100);check(responsive.move(102,112),"drag waits for full launch threshold");
        check(responsive.deltaY(112)==12&&responsive.deltaX(102)==2,"raw drag delta wrong");
        check(!responsive.finish(102,112),"short tracking drag opened window");
        responsive.begin(100,100);responsive.move(100,170);check(responsive.finish(100,180),"long tracking drag rejected");
        for(float density:new float[]{1,2.75f,3.5f}){
            CardPullGesture light=new CardPullGesture(CardPullGesture.COMMIT_DISTANCE_DP*density,64*density,8*density);
            light.begin(0,0);check(!light.move(0,6*density),"touch jitter began pull");check(!light.finish(0,6*density),"touch jitter launched");
            light.begin(0,0);check(light.move(2*density,12*density),"short pull did not track");check(!light.finish(2*density,24*density),"sub-threshold pull launched");
            light.begin(0,0);light.move(0,31*density);check(!light.finish(0,32*density),"threshold boundary launched early");
            light.begin(0,0);light.move(2*density,20*density);check(light.finish(3*density,33*density),"light pull still needs the former 64dp distance");
            check(!light.finish(3*density,33*density),"light pull committed twice");
            light.begin(0,0);check(!light.move(50*density,35*density),"mostly horizontal drag intercepted");check(!light.finish(50*density,35*density),"diagonal drag launched");
            light.begin(0,0);light.move(0,40*density);check(!light.finish(50*density,35*density),"horizontal ending launched after vertical start");
            light.begin(0,0);light.move(0,40*density);check(!light.finish(0,18*density),"reverse below threshold launched");
            light.begin(0,0);light.move(0,40*density);light.cancel();check(!light.finish(0,50*density),"cancellation ignored at lower threshold");
        }
        System.out.println("PASS: pull threshold, single commit, cancel, reversal, horizontal drag, multi-touch cancellation, tap");
    }
}
