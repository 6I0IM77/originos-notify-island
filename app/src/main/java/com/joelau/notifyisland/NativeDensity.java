package com.joelau.notifyisland;

import android.content.Context;
import android.content.res.Configuration;
import java.lang.reflect.Method;

/** OS6 retains both overloads; OS7 retains only the three-argument entry point. */
final class NativeDensity {
    private final Object instance;
    private final Method method;
    final boolean originOs7;
    NativeDensity(ClassLoader loader)throws ReflectiveOperationException{
        Class<?> fix=Class.forName("com.vivo.systemuiplugin.superx.common.utils.FixSuperXContextUtils",false,loader);
        instance=fix.getMethod("getInstance").invoke(null);
        // Choose each known system explicitly. Capability fallback is only for unknown
        // marketing properties, rather than treating OS6/7 as interchangeable.
        method=switch(OriginProfile.current()){
            case OS5 -> fix.getMethod("getFixedDensityContext4Config",Context.class,Configuration.class);
            case OS6 -> fix.getMethod("getFixedDensityContext4Config",Context.class,Configuration.class);
            case OS7 -> fix.getMethod("getFixedDensityContext4Config",Context.class,Configuration.class,boolean.class);
            case UNKNOWN -> DensityContract.resolve(fix,Context.class,Configuration.class);
        };
        if(method.getReturnType()!=Context.class)throw new NoSuchMethodException("Unsupported density context result");
        originOs7=method.getParameterCount()==3;
    }
    Context fix(Context context)throws ReflectiveOperationException{
        Configuration config=context.getResources().getConfiguration();
        // OS6 keeps its original call. OS7 follows CapsuleFactory/BigCardLoadUtils (true).
        return (Context)(originOs7?method.invoke(instance,context,new Configuration(config),true):method.invoke(instance,context,config));
    }
    String profile(){return OriginProfile.current()==OriginProfile.OS5?"OriginOS5":originOs7?"OriginOS7":"OriginOS6";}
}
