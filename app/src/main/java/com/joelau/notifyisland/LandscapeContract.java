package com.joelau.notifyisland;

import java.lang.reflect.*;
import java.util.HashMap;
import java.util.function.Predicate;

/**
 * Landscape/status-bar visibility contract, verified against the OS5, OS6 and OS7 plugins.
 *
 * Native gates:
 * <ul>
 *   <li>Data layer {@code AtomIslandsDataController.handleAtomIslandReceiversData}: a bean enters
 *       {@code mToShow} when {@code mStatusBarShowing || bean.forceShowIsland() || ...}.</li>
 *   <li>Window layer {@code IslandWindowControllerImpl.applyVisible}: the window shows when
 *       {@code (mForceIslandVisible || mStatusBarVisible || mPortrait) && mIslandDataExist && !mEnterAod}.</li>
 * </ul>
 * Our own islands follow the status bar exactly like OEM islands: while the bar is hidden they are
 * kept out of the to-show list and never force the window; once the bar appears the native gates
 * admit them through the normal island logic. Bar state is read from the native callbacks
 * ({@code mStatusBarShowing} / {@code mStatusBarVisible}); where those are absent on a profile,
 * the shade/QS expand flags are used as a fallback proxy.
 */
final class LandscapeContract {
    final Method filter,forceShow,applyVisible;
    final Field portrait,forceVisible,panelVisible,qsCenterVisible;
    private final Field barVisibleState;
    private final Method barShowingGetter;
    private final ThreadLocal<Boolean> filtering=new ThreadLocal<>();
    interface Invocation { Object call()throws Throwable; }
    LandscapeContract(OriginProfile profile,Class<?> data,Class<?> bean,Class<?> window,Class<?> state)throws ReflectiveOperationException{
        filter=data.getMethod("handleAtomIslandReceiversData",HashMap.class);
        forceShow=bean.getMethod("forceShowIsland");
        applyVisible=switch(profile){
            case OS5 -> window.getDeclaredMethod("applyVisible",state);
            case OS6 -> window.getDeclaredMethod("applyVisible",state,String.class);
            case OS7 -> window.getDeclaredMethod("applyVisible",state,String.class);
            case UNKNOWN -> throw new NoSuchMethodException("Unknown landscape adapter");
        };
        portrait=state.getField("mPortrait");forceVisible=state.getField("mForceIslandVisible");
        // Landscape natives only reveal the island together with a shade that is actually pulled
        // down, so the module must not hold the force flag while both panels are collapsed.
        panelVisible=state.getField("mNotificationPanelVisible");qsCenterVisible=state.getField("mQsCenterVisible");
        barVisibleState=optionalField(state,"mStatusBarVisible");
        Method getter=null;
        try{getter=data.getMethod("isStatusBarShowing");}catch(NoSuchMethodException ignored){}
        barShowingGetter=getter;
        if(filter.getReturnType()!=void.class||forceShow.getReturnType()!=boolean.class||applyVisible.getReturnType()!=void.class
                ||portrait.getType()!=boolean.class||forceVisible.getType()!=boolean.class
                ||!Modifier.isStatic(portrait.getModifiers())||!Modifier.isStatic(forceVisible.getModifiers())
                ||!staticFlag(panelVisible)||!staticFlag(qsCenterVisible))
            throw new NoSuchMethodException("Unsupported landscape contract: "+profile);
        applyVisible.setAccessible(true);
    }
    private static boolean staticFlag(Field f){
        return f.getType()==boolean.class&&Modifier.isStatic(f.getModifiers());
    }
    private static Field optionalField(Class<?> state,String name){
        try{
            Field f=state.getField(name);
            if(f.getType()==boolean.class&&Modifier.isStatic(f.getModifiers())){f.setAccessible(true);return f;}
        }catch(NoSuchFieldException ignored){}
        return null;
    }
    boolean shadeOpen()throws IllegalAccessException{
        return panelVisible.getBoolean(null)||qsCenterVisible.getBoolean(null);
    }
    /** Data controller's cached status-bar state; falls back to the shade/QS proxy. */
    boolean dataBarShown(Object dataInstance)throws ReflectiveOperationException{
        return barShowingGetter!=null?(boolean)barShowingGetter.invoke(dataInstance):shadeOpen();
    }
    /** Window controller's cached status-bar state; falls back to the shade/QS proxy. */
    boolean windowBarShown()throws IllegalAccessException{
        return barVisibleState!=null?barVisibleState.getBoolean(null):shadeOpen();
    }
    Object filtering(Invocation action)throws Throwable{
        Boolean previous=filtering.get();filtering.set(true);
        try{return action.call();}finally{if(previous==null)filtering.remove();else filtering.set(previous);}
    }
    /** During the data filter, our bean may bypass the gate only while the bar is shown. */
    boolean allowDuringFilter(Object bean,Predicate<Object> ours,Object dataInstance)throws ReflectiveOperationException{
        return Boolean.TRUE.equals(filtering.get())&&!portrait.getBoolean(null)&&ours.test(bean)&&dataBarShown(dataInstance);
    }
    Object visible(boolean ownSelected,Invocation action)throws Throwable{
        if(!ownSelected||portrait.getBoolean(null))return action.call();
        boolean barShown=windowBarShown();
        boolean original=forceVisible.getBoolean(null);
        if(barShown){
            // Bar open: raise the force state for this pass, like the OEM force-show path, then
            // restore it so it cannot leak to another island.
            forceVisible.setBoolean(null,true);
            try{return action.call();}finally{forceVisible.setBoolean(null,original);}
        }
        // Bar hidden: drop any leftover force before the native gate evaluates, so our own island
        // cannot stay mounted over a landscape app; restore after the pass.
        if(original)forceVisible.setBoolean(null,false);
        try{return action.call();}finally{forceVisible.setBoolean(null,original);}
    }
}
