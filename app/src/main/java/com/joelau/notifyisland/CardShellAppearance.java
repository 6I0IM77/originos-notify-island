package com.joelau.notifyisland;

/** Compute one opaque fill, rather than stacking translucent full-window render layers. */
final class CardShellAppearance {
    static int fillColor(int splashColor,float fraction){
        float p=CardMorphGeometry.clamp(fraction);
        // Black card to the application's solid splash color, always opaque.
        int r=Math.round(((splashColor>>>16)&255)*p);
        int g=Math.round(((splashColor>>>8)&255)*p);
        int b=Math.round((splashColor&255)*p);
        return 0xff000000|(r<<16)|(g<<8)|b;
    }
    static int alpha(float amount){return Math.round(255*CardMorphGeometry.clamp(amount));}
}
