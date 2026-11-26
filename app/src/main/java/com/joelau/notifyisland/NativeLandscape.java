package com.joelau.notifyisland;

import java.util.function.Predicate;

/** Keeps the ROM's native landscape/status-bar visibility policy. */
final class NativeLandscape {
    NativeLandscape(OriginProfile profile,ClassLoader loader){
        // No early interface resolution: this compatibility layer intentionally installs no hooks.
    }
    void install(ModuleMain module,Predicate<Object> ours){
        // AtomIslandsDataController requires mStatusBarShowing (or forceShowIsland), and
        // IslandWindowControllerImpl requires mStatusBarVisible (or mForceIslandVisible).
        // Overriding either one makes the island/card appear in the middle of a landscape app
        // before the user pulls down the status bar. The native onStatusBarShowing(true) path
        // republishes pending beans after the pull-down, so no compatibility override is needed.
    }
}
