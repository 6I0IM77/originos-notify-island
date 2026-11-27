package com.joelau.notifyisland;

import java.util.HashMap;

public final class LandscapeContractCheck {
    public static class State {
        public static boolean mPortrait, mForceIslandVisible;
        public static boolean mNotificationPanelVisible, mQsCenterVisible, mStatusBarVisible;
    }
    public static class Data {
        static boolean barShowing;
        public void handleAtomIslandReceiversData(HashMap<String,Bean> map){}
        public boolean isStatusBarShowing(){return barShowing;}
    }
    public static class Bean { public boolean forceShowIsland(){return false;} }
    public static class Window5 { private void applyVisible(State state){} }
    public static class Window6 { private void applyVisible(State state,String reason){} }
    public static class Window7 { private void applyVisible(State state,String reason){} }
    public static class WrongWindow { private boolean applyVisible(State state,String reason){return false;} }
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static void reject(OriginProfile profile,Class<?> window)throws Exception{
        try{new LandscapeContract(profile,Data.class,Bean.class,window,State.class);throw new AssertionError("accepted incompatible "+profile);}
        catch(NoSuchMethodException expected){}
    }
    public static void main(String[] args)throws Throwable{
        OriginProfile[] profiles={OriginProfile.OS5,OriginProfile.OS6,OriginProfile.OS7};
        Class<?>[] windows={Window5.class,Window6.class,Window7.class};
        Object own=new Bean(),other=new Bean();
        Data data=new Data();
        for(int i=0;i<profiles.length;i++){
            LandscapeContract c=new LandscapeContract(profiles[i],Data.class,Bean.class,windows[i],State.class);
            check(c.applyVisible.getParameterCount()==(i==0?1:2),"wrong window signature");
            State.mPortrait=false;State.mForceIslandVisible=false;
            State.mStatusBarVisible=false;State.mNotificationPanelVisible=false;State.mQsCenterVisible=false;
            Data.barShowing=false;

            // Data filter: bar hidden keeps our own bean out of the to-show list.
            check(!c.allowDuringFilter(own,b->b==own,data),"hidden filter started force-show timer");
            Data.barShowing=true;
            c.filtering(()->{
                check(c.allowDuringFilter(own,b->b==own,data),"fullscreen module filtered out");
                check(!c.allowDuringFilter(other,b->b==own,data),"other island bypassed native filter");
                c.filtering(()->null);check(c.allowDuringFilter(own,b->b==own,data),"nested filter lost scope");
                return null;
            });
            check(!c.allowDuringFilter(own,b->b==own,data),"filter scope leaked");
            Data.barShowing=false;
            c.filtering(()->{check(!c.allowDuringFilter(own,b->b==own,data),"bar hidden bean bypassed filter");return null;});
            try{c.filtering(()->{throw new Exception("expected");});}catch(Exception expected){}
            check(!c.allowDuringFilter(own,b->b==own,data),"failed filter scope leaked");

            // Window visibility: bar shown raises force for this pass; bar hidden clears a stale force.
            State.mStatusBarVisible=true;
            c.visible(true,()->{check(State.mForceIslandVisible,"bar open module invisible");return null;});
            check(!State.mForceIslandVisible,"force flag leaked after module");
            State.mStatusBarVisible=false;
            State.mForceIslandVisible=true;
            c.visible(true,()->{check(!State.mForceIslandVisible,"bar hidden module stayed forced");return null;});
            check(State.mForceIslandVisible,"native force flag lost after hidden pass");
            State.mForceIslandVisible=false;
            c.visible(false,()->{check(!State.mForceIslandVisible,"replacement native island forced visible");return null;});
            State.mStatusBarVisible=true;
            try{c.visible(true,()->{throw new Exception("expected");});}catch(Exception expected){}
            check(!State.mForceIslandVisible,"failed native call leaked force flag");
            State.mForceIslandVisible=true;State.mStatusBarVisible=true;c.visible(true,()->null);
            check(State.mForceIslandVisible,"native force flag lost");State.mForceIslandVisible=false;
            State.mPortrait=true;
            c.filtering(()->{check(!c.allowDuringFilter(own,b->b==own,data),"portrait filter changed");return null;});
            c.visible(true,()->{check(!State.mForceIslandVisible,"portrait visibility changed");return null;});
            State.mPortrait=false;State.mStatusBarVisible=false;
        }
        reject(OriginProfile.OS5,Window6.class);reject(OriginProfile.OS6,Window5.class);
        reject(OriginProfile.OS7,Window5.class);reject(OriginProfile.OS7,WrongWindow.class);reject(OriginProfile.UNKNOWN,Window7.class);
        System.out.println("PASS: OS5/6/7 landscape contracts; bar-gated filter and window overrides; rotation, replacement, nested/exception cleanup, native force retained");
    }
}
