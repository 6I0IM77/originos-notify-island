package com.joelau.notifyisland;

/** Uses OEM distance/angle thresholds, with symmetric horizontal dismissal. */
final class IslandSwipePolicy {
    private boolean cancelled;
    void event(int action,int pointers){
        if(action==0)cancelled=false;
        if(pointers>1||action==3||action==4)cancelled=true;
    }
    boolean cancelled(){return cancelled;}
    boolean horizontal(float dx,float dy,int distance,int angle){
        return !cancelled&&Float.isFinite(dx)&&Float.isFinite(dy)&&distance>0
            &&Math.abs(dx)>=distance
            &&Math.toDegrees(Math.atan2(Math.abs(dy),Math.abs(dx)))<=angle;
    }
}
