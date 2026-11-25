package com.joelau.notifyisland;

import android.os.IInterface;
import android.os.SystemClock;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/** Uses the plugin instance already loaded by SystemUI, so its own callback is registered. */
final class NativeConnection {
    private final ClassLoader systemLoader;
    private Object proxy;
    private Class<?> proxyType;
    private boolean initialized;
    private long nextBindAt;
    NativeConnection(ClassLoader loader){systemLoader=loader;}
    boolean ensureBound()throws Exception{
        if(proxy==null){
            Class<?> dependency=Class.forName("com.android.systemui.Dependency",false,systemLoader);
            Class<?> injectorType=Class.forName("com.vivo.systemui.plugin.VivoStatusBarIsLandInjector",false,systemLoader);
            Object injector;
            boolean os5=OriginProfile.current()==OriginProfile.OS5;
            if(OriginProfile.current()==OriginProfile.UNKNOWN)
                os5=!java.lang.reflect.Modifier.isPublic(dependency.getDeclaredField("sDependency").getModifiers());
            if(os5){
                // OS5 exposes Dependency.get(Class); its registry and inner getter are private.
                injector=dependency.getMethod("get",Class.class).invoke(null,injectorType);
            }else{
                Object registry=dependency.getField("sDependency").get(null);
                if(registry==null)return false;
                injector=dependency.getMethod("getDependencyInner",Object.class).invoke(registry,injectorType);
            }
            Object plugin=injectorType.getField("mIStatusBarIsland").get(injector);
            if(plugin==null)return false;
            // createPackageContext would create a second plugin with uninitialized dependencies.
            proxyType=Class.forName("com.vivo.systemuiplugin.systemui.islands.IslandServiceProxy",false,plugin.getClass().getClassLoader());
            for(String name:new String[]{"bindIslandService","getMIBinder","getMManager","getMBindSuccess","getMBindCount","init"})proxyType.getMethod(name);
            proxy=proxyType.getField("INSTANCE").get(null);
        }
        if(!initialized){invoke("init");initialized=true;}
        Object remote=invoke("getMIBinder");
        if(remote instanceof IInterface&&((IInterface)remote).asBinder().isBinderAlive()&&invoke("getMManager")!=null)return true;
        AtomicBoolean binding=(AtomicBoolean)invoke("getMBindSuccess");
        long now=SystemClock.elapsedRealtime();
        if(!binding.get()&&now>=nextBindAt&&((Number)invoke("getMBindCount")).intValue()<8){
            nextBindAt=now+5000;
            invoke("bindIslandService");
        }
        return false;
    }
    private Object invoke(String name)throws Exception{return proxyType.getMethod(name).invoke(proxy);}
}
