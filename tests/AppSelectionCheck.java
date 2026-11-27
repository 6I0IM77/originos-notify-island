package com.joelau.notifyisland;

import java.util.*;

public final class AppSelectionCheck {
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    public static void main(String[] args){
        check(!AppSelection.include(true,false,false,true),"headless system component included");
        check(!AppSelection.include(false,true,false,true),"updated system component included");
        check(AppSelection.include(true,false,true,true),"system launcher app excluded");
        check(AppSelection.include(false,true,true,true),"updated system launcher app excluded");
        check(AppSelection.include(false,false,false,true),"downloaded nonlauncher app excluded");
        check(!AppSelection.include(false,false,true,false),"disabled app included");
        Set<String> original=Set.of("wechat","qq","system.component");
        Set<String> selected=AppSelection.retainEligible(original,List.of("wechat","qq","mail"));
        check(selected.equals(Set.of("wechat","qq")),"old hidden selection retained");
        Set<String> all=AppSelection.bulk(selected,List.of("mail","calendar"),false);
        check(all.equals(Set.of("wechat","qq","mail","calendar")),"search all changed outside selection");
        check(selected.equals(Set.of("wechat","qq")),"bulk edit mutated source");
        Set<String> flipped=AppSelection.bulk(all,List.of("qq","mail","browser"),true);
        check(flipped.equals(Set.of("wechat","calendar","browser")),"search inversion wrong");
        check(AppSelection.bulk(flipped,List.of("qq","mail","browser"),true).equals(all),"double inversion not reversible");
        check(AppSelection.bulk(selected,List.of(),true).equals(selected),"empty results modified selection");
        check(AppSelection.bulk(Set.of("qq"),List.of("qq"),true).isEmpty(),"inverse cannot clear last result");
        check(AppSelection.bulk(Set.of(),List.of("qq"),false).equals(Set.of("qq")),"all cannot recover empty selection");
        Set<String> saved=new HashSet<>(Set.of("wechat","qq","system.component"));
        AppSelection.Draft cancel=new AppSelection.Draft(saved,List.of("wechat","qq","mail"));
        cancel.bulk(List.of("wechat","mail"),true);cancel.discard();
        check(saved.equals(Set.of("wechat","qq","system.component")),"cancel modified saved preferences");
        for(String exit:List.of("back","outside","cancel button")){
            AppSelection.Draft abandoned=new AppSelection.Draft(saved,List.of("wechat","qq","mail"));
            abandoned.bulk(List.of("mail"),false);abandoned.discard();
            check(saved.equals(Set.of("wechat","qq","system.component")),exit+" leaked selection");
        }
        AppSelection.Draft complete=new AppSelection.Draft(saved,List.of("wechat","qq","mail"));
        complete.bulk(List.of("wechat","mail"),true);Set<String> committed=complete.snapshot();complete.discard();
        check(committed.equals(Set.of("qq","mail")),"completed draft lost selection or retained system component");
        check(saved.equals(Set.of("wechat","qq","system.component")),"draft changed original before commit");
        try{complete.snapshot();throw new AssertionError("closed draft reused");}catch(IllegalStateException expected){}
        System.out.println("PASS: system component filtering, system/downloaded apps, stale hidden selection removal, search-scoped bulk edits, double inversion and empty selection recovery");
    }
}
