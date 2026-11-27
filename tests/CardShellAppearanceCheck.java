package com.joelau.notifyisland;

public final class CardShellAppearanceCheck {
    private static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    public static void main(String[] args){
        for(int target:new int[]{0xffededed,0xffffffff,0xff202124,0xff1234ab}){
            check(CardShellAppearance.fillColor(target,0)==0xff000000,"source fill changed");
            check(CardShellAppearance.fillColor(target,1)==target,"target fill changed");
            int[] previous={0,0,0};
            for(int i=0;i<=1000;i++){
                int color=CardShellAppearance.fillColor(target,i/1000f);
                check((color>>>24)==255,"drag background became translucent");
                for(int channel=0;channel<3;channel++){
                    int shift=channel*8,value=(color>>>shift)&255,limit=(target>>>shift)&255;
                    check(value>=previous[channel]&&value<=limit,"fill overshot or flashed brighter than splash color");
                    previous[channel]=value;
                }
            }
            // Subpixel movement changes geometry, never the settled fill or its opacity.
            for(int frame=0;frame<10000;frame++)check(CardShellAppearance.fillColor(target,1)==target,"held drag fill flickered");
        }
        check(CardShellAppearance.alpha(-1)==0&&CardShellAppearance.alpha(2)==255,"opacity out of bounds");
        System.out.println("PASS: opaque monotonic shell fill, no color overshoot, stable held-drag appearance, bounded opacity");
    }
}
