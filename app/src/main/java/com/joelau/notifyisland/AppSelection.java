package com.joelau.notifyisland;

import java.util.*;

/** Bulk edits apply to visible results; selections outside a search remain unchanged. */
final class AppSelection {
    static final class Draft {
        private final Set<String> selected;
        private boolean discarded;
        Draft(Set<String> saved,Collection<String> eligible){selected=retainEligible(saved,eligible);}
        void bulk(Collection<String> shown,boolean inverse){
            if(discarded)throw new IllegalStateException("Selection dialog closed");
            Set<String> next=AppSelection.bulk(selected,shown,inverse);selected.clear();selected.addAll(next);
        }
        Set<String> snapshot(){if(discarded)throw new IllegalStateException("Selection dialog closed");return new HashSet<>(selected);}
        void discard(){discarded=true;selected.clear();}
    }
    static boolean include(boolean system,boolean updatedSystem,boolean launcher,boolean enabled){
        return enabled&&(!(system||updatedSystem)||launcher);
    }
    static Set<String> retainEligible(Set<String> selected,Collection<String> eligible){
        Set<String> result=new HashSet<>(selected);result.retainAll(eligible);return result;
    }
    static Set<String> bulk(Set<String> selected,Collection<String> shown,boolean inverse){
        Set<String> result=new HashSet<>(selected);
        for(String pkg:shown){if(!inverse)result.add(pkg);else if(!result.remove(pkg))result.add(pkg);}
        return result;
    }
}
