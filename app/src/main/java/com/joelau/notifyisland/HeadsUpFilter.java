package com.joelau.notifyisland;

import android.service.notification.StatusBarNotification;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

final class HeadsUpFilter {
    static void install(ModuleMain module,ClassLoader loader,Predicate<StatusBarNotification> suppress)throws ReflectiveOperationException{
        Class<?> entry=Class.forName("com.android.systemui.statusbar.notification.collection.NotificationEntry",false,loader);
        boolean os5=OriginProfile.current()==OriginProfile.OS5;
        if(OriginProfile.current()==OriginProfile.UNKNOWN)
            os5=!java.lang.reflect.Modifier.isPublic(entry.getDeclaredField("mSbn").getModifiers());
        final Method sbnGetter=os5?entry.getMethod("getSbn"):null;
        final Field sbn=os5?null:entry.getField("mSbn");
        if((os5?sbnGetter.getReturnType():sbn.getType())!=StatusBarNotification.class)throw new NoSuchFieldException("Unsupported notification accessor");
        HeadsUpContract selected;
        String profile;
        try{
            Class<?> legacy=Class.forName("com.android.systemui.statusbar.notification.interruption.NotificationInterruptStateProviderImpl",false,loader);
            selected=HeadsUpContract.legacy(legacy,entry);profile=os5?"OriginOS5":"OriginOS6";
        }catch(ClassNotFoundException|NoSuchMethodException legacyUnavailable){
            Class<?> modern=Class.forName("com.android.systemui.statusbar.notification.interruption.VisualInterruptionDecisionProviderImpl",false,loader);
            selected=HeadsUpContract.modern(modern,entry);profile="OriginOS7";
        }
        installContract(module,suppress,sbnGetter,sbn,selected,profile);
        if(os5){
            // OS5 ships both implementations, selected by an OEM/AOSP feature flag.
            try{
                Class<?> modern=Class.forName("com.android.systemui.statusbar.notification.interruption.VisualInterruptionDecisionProviderImpl",false,loader);
                Class<?> decision=Class.forName(modern.getName()+"$DecisionImpl",false,loader);
                installContract(module,suppress,sbnGetter,sbn,HeadsUpContract.os5Modern(modern,entry,decision),"OriginOS5 refactor");
            }catch(ReflectiveOperationException ex){module.error("OS5 optional refactored heads-up filter unavailable; legacy filter remains",ex);}
        }
    }
    private static void installContract(ModuleMain module,Predicate<StatusBarNotification> suppress,Method sbnGetter,Field sbn,HeadsUpContract contract,String name){
        AtomicBoolean matched=new AtomicBoolean(),failed=new AtomicBoolean();
        for(Method method:contract.methods)module.hook(method).intercept(chain->{
            try{
                StatusBarNotification notification=(StatusBarNotification)(sbnGetter!=null?sbnGetter.invoke(chain.getArg(0)):sbn.get(chain.getArg(0)));
                if(suppress.test(notification)){
                    Object result=contract.suppressResult();
                    if(matched.compareAndSet(false,true))module.info("Heads-up suppressed via "+name+" (first match)");
                    return result;
                }
            }catch(Throwable t){
                if(failed.compareAndSet(false,true))module.error("Heads-up evaluation failed; retaining banners",t);
            }
            return chain.proceed();
        });
        module.info("Duplicate heads-up filter installed: "+name+"; hooks="+contract.methods.length);
    }
}
