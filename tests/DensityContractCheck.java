package com.joelau.notifyisland;

public final class DensityContractCheck {
    public static final class Context {}
    public static final class Configuration {}
    public static final class Os6 {
        int legacyCalls,modernCalls;
        Configuration observed;
        public Context getFixedDensityContext4Config(Context c,Configuration cfg){legacyCalls++;observed=cfg;return c;}
        public Context getFixedDensityContext4Config(Context c,Configuration cfg,boolean flag){modernCalls++;return c;}
    }
    public static final class Os7 {
        boolean observedFlag;
        public Context getFixedDensityContext4Config(Context c,Configuration cfg,boolean flag){observedFlag=flag;return c;}
    }
    public static final class Unsupported {}
    public static final class WrongResult {
        public Object getFixedDensityContext4Config(Context c,Configuration cfg){return c;}
    }
    static void check(boolean value){if(!value)throw new AssertionError();}
    static void rejects(Class<?> type)throws Exception{
        try{DensityContract.resolve(type,Context.class,Configuration.class);throw new AssertionError("Unsupported contract accepted");}
        catch(NoSuchMethodException expected){}
    }
    public static void main(String[] args)throws Exception{
        Context context=new Context();Configuration config=new Configuration();
        Os6 os6=new Os6();
        var oldMethod=DensityContract.resolve(Os6.class,Context.class,Configuration.class);
        check(oldMethod.getParameterCount()==2);
        check(oldMethod.invoke(os6,context,config)==context);
        check(os6.legacyCalls==1&&os6.modernCalls==0&&os6.observed==config);
        Os7 os7=new Os7();
        var newMethod=DensityContract.resolve(Os7.class,Context.class,Configuration.class);
        check(newMethod.getParameterCount()==3);
        check(newMethod.invoke(os7,context,config,true)==context&&os7.observedFlag);
        rejects(Unsupported.class);rejects(WrongResult.class);
        System.out.println("PASS: OS6 with BOTH overloads stays legacy; OS7-only three-argument branch; incompatible interfaces rejected");
    }
}
