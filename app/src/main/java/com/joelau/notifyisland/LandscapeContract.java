package com.joelau.notifyisland;

import java.lang.reflect.*;
import java.util.HashMap;
import java.util.function.Predicate;

/** Signatures verified separately against the OS5, OS6 and OS7 plugins. */
final class LandscapeContract {
    final Method filter,forceShow,applyVisible;
    final Field portrait,forceVisible;
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
        if(filter.getReturnType()!=void.class||forceShow.getReturnType()!=boolean.class||applyVisible.getReturnType()!=void.class
                ||portrait.getType()!=boolean.class||forceVisible.getType()!=boolean.class
                ||!Modifier.isStatic(portrait.getModifiers())||!Modifier.isStatic(forceVisible.getModifiers()))
            throw new NoSuchMethodException("Unsupported landscape contract: "+profile);
        applyVisible.setAccessible(true);
    }
    Object filtering(Invocation action)throws Throwable{
        Boolean previous=filtering.get();filtering.set(true);
        try{return action.call();}finally{if(previous==null)filtering.remove();else filtering.set(previous);}
    }
    boolean allowDuringFilter(Object bean,Predicate<Object> ours)throws IllegalAccessException{
        return Boolean.TRUE.equals(filtering.get())&&!portrait.getBoolean(null)&&ours.test(bean);
    }
    Object visible(boolean ownSelected,Invocation action)throws Throwable{
        if(!ownSelected||portrait.getBoolean(null))return action.call();
        boolean original=forceVisible.getBoolean(null);forceVisible.setBoolean(null,true);
        try{return action.call();}finally{forceVisible.setBoolean(null,original);}
    }
}
