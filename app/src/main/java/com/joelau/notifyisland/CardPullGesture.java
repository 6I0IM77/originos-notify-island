package com.joelau.notifyisland;

/** A canceled, reversed or multi-touch pull must never open an application. */
final class CardPullGesture {
    static final int COMMIT_DISTANCE_DP=32;
    private final float distance,horizontalLimit,slop;
    private float x,y;
    private boolean tracking,dragging,sequence,nativeReserved;
    CardPullGesture(float d,float h){this(d,h,d);}
    CardPullGesture(float d,float h,float s){distance=d;horizontalLimit=h;slop=Math.min(d,s);}
    void begin(float startX,float startY){x=startX;y=startY;sequence=true;tracking=true;dragging=false;nativeReserved=false;}
    /** Called before the OEM parent processes MOVE. Use the same decision as the card,
     * then retain ownership through UP/CANCEL even if the pull reverses or is canceled. */
    boolean reserveNative(int action,int pointers,float currentX,float currentY){
        if(!sequence||action==0)return false;
        if(pointers>1){cancel();nativeReserved=true;}
        if(action==2&&move(currentX,currentY))nativeReserved=true;
        return nativeReserved;
    }
    /** The dispatcher, rather than a child click target, owns sequence cleanup. */
    void endSequence(){cancel();sequence=false;nativeReserved=false;}
    boolean move(float currentX,float currentY){
        float dx=Math.abs(currentX-x),dy=currentY-y;
        if(tracking&&dy>slop&&dx<horizontalLimit&&dy>dx)dragging=true;
        return dragging;
    }
    boolean dragging(){return dragging;}
    float deltaY(float currentY){return Math.max(0,currentY-y);}
    float deltaX(float currentX){return currentX-x;}
    boolean finish(float endX,float endY){
        float dx=Math.abs(endX-x),dy=endY-y;
        boolean commit=tracking&&dragging&&dy>distance&&dx<horizontalLimit&&dy>dx;
        cancel();return commit;
    }
    void cancel(){tracking=false;dragging=false;}
}
