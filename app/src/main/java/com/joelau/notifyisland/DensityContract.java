package com.joelau.notifyisland;

import java.lang.reflect.Method;

/** OS6 has BOTH overloads; OS7 removed the two-argument entry point. */
final class DensityContract {
    static Method resolve(Class<?> owner,Class<?> context,Class<?> configuration)throws NoSuchMethodException{
        Method selected;
        try{
            // Preserve the exact 0.1.20 call whenever it is available.
            selected=owner.getMethod("getFixedDensityContext4Config",context,configuration);
        }catch(NoSuchMethodException legacyUnavailable){
            selected=owner.getMethod("getFixedDensityContext4Config",context,configuration,boolean.class);
        }
        if(selected.getReturnType()!=context)throw new NoSuchMethodException("Unsupported density context result");
        return selected;
    }
}
