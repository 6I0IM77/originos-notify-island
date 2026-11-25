package com.joelau.notifyisland;

/** Geometry only: the shell changes aspect ratio, while its contents keep their own size. */
final class CardMorphGeometry {
    record Shape(float left,float top,float width,float height,float radius) {
        float centerX(){return left+width/2;}
        float centerY(){return top+height/2;}
    }
    static float clamp(float v){return Math.max(0,Math.min(1,v));}
    static float smooth(float v){v=clamp(v);return v*v*(3-2*v);}
    static float mix(float a,float b,float p){return a+(b-a)*p;}
    static Shape blend(Shape a,Shape b,float p){
        return new Shape(mix(a.left,b.left,p),mix(a.top,b.top,p),mix(a.width,b.width,p),mix(a.height,b.height,p),mix(a.radius,b.radius,p));
    }
    static Shape drag(Shape source,float dx,float dy,float density){
        dy=Math.max(0,dy);
        float p=clamp(dy/(64*density));
        float w=source.width*(1-.035f*p);
        return new Shape(source.centerX()-w/2+dx*.10f,source.top+dy*.82f/(1+dy/(1100*density)),w,source.height*(1+.16f*p),source.radius);
    }
    /** Fit fallback and OEM bounds into the display; never draw a negative or offscreen shell. */
    static Shape target(float left,float top,float width,float height,float screenWidth,float screenHeight,float radius){
        float w=Math.max(1,Math.min(width,screenWidth));
        float h=Math.max(1,Math.min(height,screenHeight));
        return new Shape(Math.max(0,Math.min(left,screenWidth-w)),Math.max(0,Math.min(top,screenHeight-h)),w,h,Math.min(radius,Math.min(w,h)/2));
    }
}
