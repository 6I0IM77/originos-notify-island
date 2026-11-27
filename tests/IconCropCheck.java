package com.joelau.notifyisland;

import java.util.Arrays;

public final class IconCropCheck {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        int n=100;int[] pixels=new int[n*n];Arrays.fill(pixels,0xff00bb66);
        check(IconCrop.opaqueRectangle(pixels,n,n).equals(new IconCrop.Bounds(0,0,n,n)),"solid artwork cropped");
        Arrays.fill(pixels,0xffffffff);
        for(int y=8;y<92;y++)for(int x=8;x<92;x++)pixels[y*n+x]=0xff00bb66;
        check(IconCrop.opaqueRectangle(pixels,n,n).equals(new IconCrop.Bounds(8,8,92,92)),"baked-in white frame retained");
        Arrays.fill(pixels,0);
        for(int y=0;y<n;y++)for(int x=0;x<n;x++)if(Math.hypot(x-49.5,y-49.5)<49)pixels[y*n+x]=0xff00bb66;
        IconCrop.Bounds circle=IconCrop.opaqueRectangle(pixels,n,n);
        check((circle.right()-circle.left())*(circle.bottom()-circle.top())>4500,"circle unnecessarily tiny");
        for(int y=circle.top();y<circle.bottom();y++)for(int x=circle.left();x<circle.right();x++)check((pixels[y*n+x]>>>24)>=240,"masked transparent corner retained");
        Arrays.fill(pixels,0xffffffff);
        for(int y=15;y<85;y++)for(int x=15;x<85;x++)if(Math.hypot(x-49.5,y-49.5)<32)pixels[y*n+x]=0xff202020;
        check(IconCrop.opaqueRectangle(pixels,n,n).equals(new IconCrop.Bounds(0,0,n,n)),"intentional white background around sparse symbol cropped");
        Arrays.fill(pixels,0);check(IconCrop.opaqueRectangle(pixels,n,n).equals(new IconCrop.Bounds(0,0,n,n)),"empty artwork invalid bounds");
        System.out.println("PASS: solid artwork, white-frame removal, masked opaque rectangle, intended white background and empty fallback");
    }
}
