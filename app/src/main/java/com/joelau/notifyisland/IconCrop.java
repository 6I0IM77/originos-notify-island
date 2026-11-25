package com.joelau.notifyisland;

/** Largest opaque rectangle inside a masked legacy icon; no invented border color. */
final class IconCrop {
    record Bounds(int left,int top,int right,int bottom){}
    static Bounds opaqueRectangle(int[] pixels,int width,int height){
        if(width<=0||height<=0||pixels.length!=width*height)throw new IllegalArgumentException("Invalid icon dimensions");
        int[] histogram=new int[width],stack=new int[width+1];int area=0;
        Bounds best=new Bounds(0,0,width,height);
        for(int y=0;y<height;y++){
            for(int x=0;x<width;x++)histogram[x]=(pixels[y*width+x]>>>24)>=240?histogram[x]+1:0;
            int depth=0;
            for(int x=0;x<=width;x++){
                int h=x==width?0:histogram[x];
                while(depth>0&&histogram[stack[depth-1]]>h){
                    int column=stack[--depth],left=depth==0?0:stack[depth-1]+1;
                    int nextArea=(x-left)*histogram[column];
                    if(nextArea>area){area=nextArea;best=new Bounds(left,y-histogram[column]+1,x,y+1);}
                }
                stack[depth++]=x;
            }
        }
        return trimUniformFrame(pixels,width,best);
    }
    private static Bounds trimUniformFrame(int[] pixels,int stride,Bounds outer){
        int background=pixels[outer.top()*stride+outer.left()];
        if((background>>>24)<240)return outer;
        int w=outer.right()-outer.left(),h=outer.bottom()-outer.top();
        if(frameMatches(pixels,stride,outer,background,false)<.95f)return outer;
        int left=outer.right(),top=outer.bottom(),right=outer.left(),bottom=outer.top();
        for(int y=outer.top();y<outer.bottom();y++)for(int x=outer.left();x<outer.right();x++){
            if(!similar(pixels[y*stride+x],background)){
                left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x+1);bottom=Math.max(bottom,y+1);
            }
        }
        // Strip only an enclosing uniform frame around another substantial rectangular
        // artwork, not the intentional white background around a sparse app symbol.
        if(left<=outer.left()||top<=outer.top()||right>=outer.right()||bottom>=outer.bottom())return outer;
        if((right-left)*(bottom-top)<w*h*.45f)return outer;
        Bounds inner=new Bounds(left,top,right,bottom);
        return frameMatches(pixels,stride,inner,background,true)>=.90f?inner:outer;
    }
    private static boolean similar(int a,int b){
        return Math.abs((a>>>24)-(b>>>24))<=12&&Math.abs(((a>>>16)&255)-((b>>>16)&255))<=18
            &&Math.abs(((a>>>8)&255)-((b>>>8)&255))<=18&&Math.abs((a&255)-(b&255))<=18;
    }
    private static float frameMatches(int[] pixels,int stride,Bounds b,int color,boolean different){
        int matches=0,total=0;
        for(int x=b.left();x<b.right();x++)for(int y:new int[]{b.top(),b.bottom()-1}){
            if(similar(pixels[y*stride+x],color)!=different)matches++;total++;
        }
        for(int y=b.top();y<b.bottom();y++)for(int x:new int[]{b.left(),b.right()-1}){
            if(similar(pixels[y*stride+x],color)!=different)matches++;total++;
        }
        return (float)matches/total;
    }
}
