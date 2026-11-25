package com.joelau.notifyisland;

import java.lang.reflect.Method;
import java.util.function.Predicate;

/** Bypass only the landscape/status-bar gate, within the original native calls. */
final class NativeLandscape {
    private final LandscapeContract contract;
    private final Method instance,main,side,card;
    NativeLandscape(OriginProfile profile,ClassLoader loader)throws ReflectiveOperationException{
        Class<?> data=type(loader,"data.AtomIslandsDataController"),bean=type(loader,"data.AtomIslandsBean");
        contract=new LandscapeContract(profile,data,bean,type(loader,"controller.IslandWindowControllerImpl"),type(loader,"controller.IslandWindowState"));
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
            // This getter is also read by AtomIslandsInfo. Outside filtering it MUST
            // retain its original value, or the OEM 3s/5s force-show timer is started.
            if(contract.allowDuringFilter(chain.getThisObject(),ours))return true;
            return chain.proceed();
        });
        module.hook(contract.applyVisible).intercept(chain->{
            if(contract.portrait.getBoolean(null))return chain.proceed();
            Object display=instance.invoke(null);
            // Native data-exists and AOD checks, animations and window/input cleanup
            // still execute. No force flag survives this call or leaks to another island.
            return contract.visible(ours.test(main.invoke(display))||ours.test(side.invoke(display))||ours.test(card.invoke(display)),chain::proceed);
        });
    }
}
