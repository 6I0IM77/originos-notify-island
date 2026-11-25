package com.joelau.notifyisland;
import android.app.Service;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import io.github.libxposed.api.XposedModule;

public final class ModuleMain extends XposedModule {
    static final String TAG="NotifyIsland";
    private final Set<String> startedPackages=ConcurrentHashMap.newKeySet();
    @Override public void onPackageReady(PackageReadyParam p) {
        String packageName=p.getPackageName();
        if((!packageName.equals(Wire.SYSUI) && !packageName.equals(Wire.PLUGIN)) || !startedPackages.add(packageName))return;
        try {
            info("Loading 0.1.32 in "+packageName+"; profile="+OriginProfile.current()+"; SDK="+android.os.Build.VERSION.SDK_INT+"; build="+android.os.Build.DISPLAY);
            SharedPreferences prefs=getRemotePreferences(Wire.PREFS);
            if(packageName.equals(Wire.SYSUI)) {
                SourceBridge source=new SourceBridge(this,prefs);
                source.install(p.getClassLoader());
                Class<?> appClass=Class.forName(p.getApplicationInfo().className,false,p.getClassLoader());
                hook(appClass.getMethod("onCreate")).intercept(chain->{
                    Object result=chain.proceed();
                    try{source.attach((Context)chain.getThisObject());}catch(Throwable t){error("Source startup",t);}
                    return result;
                });
            } else {
                IslandHost host=new IslandHost(this,prefs,p.getClassLoader());
                Class<?> service=Class.forName("com.vivo.island.IslandService",false,p.getClassLoader());
                hook(service.getDeclaredMethod("onCreate")).intercept(chain->{
                    Object result=chain.proceed();
                    try{host.attach((Service)chain.getThisObject());}catch(Throwable t){error("Island startup",t);}
                    return result;
                });
            }
        }catch(Throwable t){error("Unsupported system interface",t);}
    }
    // libxposed's log() goes to the framework's own module log, not logcat, which left us
    // debugging blind. Write straight to logcat so a phone-side capture shows the call state.
    void info(String s){Log.i(TAG,s);}
    void error(String s,Throwable t){Log.e(TAG,s,t);}
}

