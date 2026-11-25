package com.joelau.notifyisland;

import java.util.List;
import java.util.Objects;

/** Content-owned retirement: elapsed time never makes a deleted island drawable again. */
final class RetiredIslandGate {
    final String id;
    private boolean deleting,removed;
    RetiredIslandGate(String id){this.id=Objects.requireNonNull(id);}
    void beginDeletion(){deleting=true;}
    boolean suppressTransitions(){return deleting;}
    boolean removalObserved(){return removed;}
    boolean releaseFor(List<String> ids){
        for(String incoming:ids)if(!id.equals(incoming))return true;
        if(ids.isEmpty()){
            if(deleting)removed=true;
            return false;
        }
        // A queued OEM update can still contain the deleted ID. A real new module
        // notification explicitly releases retirement in NativeIsland.show(), even
        // when it reuses this same ID. The native list alone cannot prove it is new.
        return false;
    }
}
