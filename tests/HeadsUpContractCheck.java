package com.joelau.notifyisland;

public final class HeadsUpContractCheck {
    public static final class Entry {}
    public static final class Decision {
        public final boolean isValid;
        public final String reason;
        public Decision(boolean value,String reason){isValid=value;this.reason=reason;}
    }
    public static final class Legacy {
        public boolean checkHeadsUp(Entry entry,boolean log){return true;}
    }
    public static final class Modern {
        public Decision makeAndLogHeadsUpDecision(Entry entry){return new Decision(true,"native");}
        public Decision makeUnloggedHeadsUpDecision(Entry entry){return new Decision(true,"native");}
    }
    public static final class WrongLegacy {
        public Object checkHeadsUp(Entry entry,boolean log){return Boolean.TRUE;}
    }
    public interface Os5Decision {boolean getShouldInterrupt();}
    public static final class Os5DecisionImpl implements Os5Decision {
        private final boolean value;
        public Os5DecisionImpl(boolean flag,String reason){value=flag;}
        public boolean getShouldInterrupt(){return value;}
    }
    public static final class InvertedOs5Decision implements Os5Decision {
        private final boolean value;
        public InvertedOs5Decision(boolean flag,String reason){value=!flag;}
        public boolean getShouldInterrupt(){return value;}
    }
    public static final class Os5Modern {
        public Os5Decision makeAndLogHeadsUpDecision(Entry entry){return new Os5DecisionImpl(true,"native");}
        public Os5Decision makeUnloggedHeadsUpDecision(Entry entry){return new Os5DecisionImpl(true,"native");}
    }
    public static final class Incomplete {
        public Decision makeAndLogHeadsUpDecision(Entry entry){return new Decision(true,"native");}
    }
    public static final class InvertedDecision {
        public final boolean isValid;
        public InvertedDecision(boolean value,String reason){isValid=!value;}
    }
    public static final class Inverted {
        public InvertedDecision makeAndLogHeadsUpDecision(Entry entry){return null;}
        public InvertedDecision makeUnloggedHeadsUpDecision(Entry entry){return null;}
    }
    interface Checked {void run()throws Exception;}
    static void check(boolean condition){if(!condition)throw new AssertionError();}
    static void rejects(Checked action)throws Exception{
        try{action.run();throw new AssertionError("Unsupported contract accepted");}
        catch(ReflectiveOperationException expected){}
    }
    public static void main(String[] args)throws Exception{
        HeadsUpContract legacy=HeadsUpContract.legacy(Legacy.class,Entry.class);
        check(legacy.methods.length==1);
        check(legacy.suppressResult().equals(false));
        check((boolean)legacy.methods[0].invoke(new Legacy(),new Entry(),false));
        HeadsUpContract modern=HeadsUpContract.modern(Modern.class,Entry.class);
        check(modern.methods.length==2);
        Decision first=(Decision)modern.suppressResult();
        Decision second=(Decision)modern.suppressResult();
        check(!first.isValid&&!second.isValid&&first!=second);
        check(first.reason.contains("NotifyIsland"));
        for(var method:modern.methods)check(((Decision)method.invoke(new Modern(),new Entry())).isValid);
        rejects(()->HeadsUpContract.legacy(WrongLegacy.class,Entry.class));
        rejects(()->HeadsUpContract.modern(Incomplete.class,Entry.class));
        rejects(()->HeadsUpContract.modern(Inverted.class,Entry.class));
        HeadsUpContract os5=HeadsUpContract.os5Modern(Os5Modern.class,Entry.class,Os5DecisionImpl.class);
        check(os5.methods.length==2&&!((Os5Decision)os5.suppressResult()).getShouldInterrupt());
        for(var method:os5.methods)check(((Os5Decision)method.invoke(new Os5Modern(),new Entry())).getShouldInterrupt());
        rejects(()->HeadsUpContract.os5Modern(Os5Modern.class,Entry.class,InvertedOs5Decision.class));
        rejects(()->HeadsUpContract.os5Modern(Os5Modern.class,Entry.class,Decision.class));
        System.out.println("PASS: OS5 legacy/refactored decisions, OS6 boolean, OS7 two decision paths, native decisions retained and incompatible semantics rejected");
    }
}
