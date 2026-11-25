package com.joelau.notifyisland;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Reflective contracts observed in the supplied OS6 and OS7 SystemUI builds. */
final class HeadsUpContract {
    final Method[] methods;
    private final Constructor<?> decisionConstructor;

    private HeadsUpContract(Method[] methods,Constructor<?> constructor){
        this.methods=methods;decisionConstructor=constructor;
    }
    static HeadsUpContract legacy(Class<?> policy,Class<?> entry)throws ReflectiveOperationException{
        Method method=policy.getMethod("checkHeadsUp",entry,boolean.class);
        if(method.getReturnType()!=boolean.class)throw new NoSuchMethodException("checkHeadsUp must return boolean");
        return new HeadsUpContract(new Method[]{method},null);
    }
    static HeadsUpContract modern(Class<?> policy,Class<?> entry)throws ReflectiveOperationException{
        Method logged=policy.getMethod("makeAndLogHeadsUpDecision",entry);
        Method unlogged=policy.getMethod("makeUnloggedHeadsUpDecision",entry);
        Class<?> decision=logged.getReturnType();
        if(decision!=unlogged.getReturnType()||decision.getField("isValid").getType()!=boolean.class)
            throw new NoSuchMethodException("Unsupported heads-up decision result");
        Constructor<?> constructor=decision.getConstructor(boolean.class,String.class);
        // Validate the meaning of both constructor values before installing either hook.
        if(decision.getField("isValid").getBoolean(constructor.newInstance(false,"NotifyIsland"))
                ||!decision.getField("isValid").getBoolean(constructor.newInstance(true,"NotifyIsland")))
            throw new NoSuchMethodException("Unsupported heads-up decision semantics");
        return new HeadsUpContract(new Method[]{logged,unlogged},constructor);
    }
    static HeadsUpContract os5Modern(Class<?> policy,Class<?> entry,Class<?> implementation)throws ReflectiveOperationException{
        Method logged=policy.getMethod("makeAndLogHeadsUpDecision",entry);
        Method unlogged=policy.getMethod("makeUnloggedHeadsUpDecision",entry);
        Class<?> decision=logged.getReturnType();
        if(decision!=unlogged.getReturnType()||!decision.isAssignableFrom(implementation))
            throw new NoSuchMethodException("Unsupported OS5 decision implementation");
        Method shouldInterrupt=decision.getMethod("getShouldInterrupt");
        if(shouldInterrupt.getReturnType()!=boolean.class)throw new NoSuchMethodException("Unsupported OS5 decision flag");
        Constructor<?> constructor=implementation.getConstructor(boolean.class,String.class);
        if(!Boolean.FALSE.equals(shouldInterrupt.invoke(constructor.newInstance(false,"NotifyIsland")))
                ||!Boolean.TRUE.equals(shouldInterrupt.invoke(constructor.newInstance(true,"NotifyIsland"))))
            throw new NoSuchMethodException("Unsupported OS5 decision semantics");
        return new HeadsUpContract(new Method[]{logged,unlogged},constructor);
    }
    Object suppressResult()throws ReflectiveOperationException{
        return decisionConstructor==null?Boolean.FALSE:decisionConstructor.newInstance(false,"NotifyIsland: notification routed to island");
    }
}
