package com.joelau.notifyisland;

import java.util.List;

public final class RetiredIslandGateCheck {
    private static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    public static void main(String[] args){
        RetiredIslandGate gate=new RetiredIslandGate("module:old");
        check(!gate.suppressTransitions(),"launch preparation must not interrupt the live shell");
        check(!gate.releaseFor(List.of()),"empty preparation revealed source");
        check(!gate.removalObserved(),"focus change mistaken for confirmed deletion");
        check(!gate.releaseFor(List.of("module:old")),"pending source update revealed source");
        gate.beginDeletion();
        check(gate.suppressTransitions(),"deletion must bypass native retirement transitions");
        check(!gate.releaseFor(List.of()),"deletion revealed the empty container");
        check(gate.removalObserved(),"deletion completion was not recorded");
        // Model arbitrary delayed no-data/focus callbacks, well beyond any animation timeout.
        for(int i=0;i<10000;i++){
            check(!gate.releaseFor(List.of()),"delayed empty update resurrected source");
            check(!gate.releaseFor(List.of("module:old")),"stale native list resurrected source");
            check(gate.suppressTransitions(),"delayed callback enabled exit animation");
        }
        check(gate.releaseFor(List.of("native:timer")),"native timer remained hidden");
        check(gate.releaseFor(List.of("module:old","native:music")),"mixed native content remained hidden");
        check(gate.releaseFor(List.of("module:new")),"replacement message remained hidden");
        RetiredIslandGate next=new RetiredIslandGate("module:new");
        check(!next.suppressTransitions()&&!next.removalObserved(),"new transaction inherited old deletion state");
        System.out.println("PASS: retirement phases, delayed empty/stale updates, native/mixed/replacement release and transaction isolation");
    }
}
