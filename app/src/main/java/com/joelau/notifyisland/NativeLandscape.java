package com.joelau.notifyisland;

import java.lang.reflect.Method;
import java.util.function.Predicate;

/**
 * Landscape adaptation for our own islands. The data filter, the bean force-show getter and the
 * window visibility pass follow the native status-bar state together: hidden bar keeps our
 * islands out, shown bar lets the native gates admit them through the normal island logic. If any
 * plugin signature does not match, the hook falls back to the native call.
 */
final class NativeLandscape {
    private final LandscapeContract contract;
    private final Method instance,main,side,card;
    private final Object dataInstance;
    // Only used to keep the diagnostic log to one line per shade transition.
    private boolean holdLogged;
    NativeLandscape(OriginProfile profile,ClassLoader loader)throws ReflectiveOperationException{
        Class<?> data=type(loader,"data.AtomIslandsDataController"),bean=type(loader,"data.AtomIslandsBean");
        contract=new LandscapeContract(profile,data,bean,type(loader,"controller.IslandWindowControllerImpl"),type(loader,"controller.IslandWindowState"));
        dataInstance=data.getMethod("getInstance").invoke(null);
        Class<?> display=type(loader,"data.AtomIslandDisplayController");
        instance=display.getMethod("getInstance");main=display.getMethod("getMainIsland");
        side=display.getMethod("getSideIsland");card=display.getMethod("getCardIsland");
    }
    private static Class<?> type(ClassLoader loader,String name)throws ClassNotFoundException{
        return Class.forName("com.vivo.island."+name,false,loader);
    }
    void install(ModuleMain module,Predicate<Object> ours){
        module.hook(contract.filter).intercept(chain->{
            return contract.filtering(chain::proceed);
        });
        module.hook(contract.forceShow).intercept(chain->{
            try{
                // This getter is also read by AtomIslandsInfo. Outside filtering it MUST
                // retain its original value, or the OEM 3s/5s force-show timer is started.
                if(contract.allowDuringFilter(chain.getThisObject(),ours,dataInstance))return true;
            }catch(Throwable t){module.error("Landscape filter fallback to native",t);}
            return chain.proceed();
        });
        module.hook(contract.applyVisible).intercept(chain->{
            try{
                if(contract.portrait.getBoolean(null))return chain.proceed();
                Object display=instance.invoke(null);
                boolean own=ours.test(main.invoke(display))||ours.test(side.invoke(display))||ours.test(card.invoke(display));
                // In landscape the island is a shade child, exactly like the OEM ones: it follows the
                // pulled-down shade instead of popping up on the fullscreen surface by itself.
                boolean bar=contract.windowBarShown();
                if(own&&bar!=holdLogged){
                    holdLogged=bar;
                    module.info(bar?"Landscape: bar open, island follows":"Landscape: island waits for the bar");
                }
                // Native data-exists and AOD checks, animations and window/input cleanup
                // still execute. No force flag survives this call or leaks to another island.
                return contract.visible(own,chain::proceed);
            }catch(Throwable t){
                module.error("Landscape visibility fallback to native",t);
                return chain.proceed();
            }
        });
    }
}
