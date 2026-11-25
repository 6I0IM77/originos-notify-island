package com.joelau.notifyisland;
import android.app.*;
import android.content.*;
import android.os.*;
import java.util.*;

final class IslandHost {
    final ModuleMain module; final SharedPreferences prefs;
    final CallWatcher calls=new CallWatcher(this);
    final Handler main=new Handler(Looper.getMainLooper());
    final EntryStore<Intent> store=new EntryStore<>();
    private final ClassLoader loader;
    private Context context; private NativeIsland nativeIsland;
    private String visibleKey; private boolean failed; private long alarmVersion;
    private boolean launchingSmall;
    private final SharedPreferences.OnSharedPreferenceChangeListener settings=(p,k)->main.post(this::settingsChanged);
    IslandHost(ModuleMain m,SharedPreferences p,ClassLoader cl){module=m;prefs=p;loader=cl;}
    boolean available(){return nativeIsland!=null&&!failed;}
    void attach(Service service){
        if(context!=null)return;context=service.getApplicationContext();
        try{nativeIsland=new NativeIsland(this,loader,context);nativeIsland.install();}
        catch(Throwable t){failed=true;module.error("Native island capability mismatch",t);}
        Wire.register(context,new BroadcastReceiver(){public void onReceive(Context c,Intent i){
            Wire.pong(c,i,prefs,"island",failed?"当前系统接口不匹配，已安全停用":"原子岛显示服务已连接 · 0.1.32 · "+OriginProfile.current());
        }},Wire.PING,null);
        if(failed)return;
        Wire.register(context,new BroadcastReceiver(){public void onReceive(Context c,Intent i){
            String pkg=i.getStringExtra("package");
            if(pkg==null)return;
            calls.peer(pkg,i.getStringExtra("name"),i.getByteArrayExtra("avatar"));
        }},Wire.PEER_REPLY,null);
        Wire.register(context,new BroadcastReceiver(){public void onReceive(Context c,Intent i){
            if(!"reload".equals(i.getStringExtra("op")))return;
            try{module.info("Settings reload requested");nativeIsland.refreshCurrent(store.active()==null?0:store.unread(store.active().pkg));}
            catch(Throwable t){module.error("Reload island",t);}
        }},Wire.RELOAD,null);
        Wire.register(context,new BroadcastReceiver(){public void onReceive(Context c,Intent i){
            if("diagnose_icon".equals(i.getStringExtra("op"))){nativeIsland.dumpIconDiagnostics();return;}
            if("probe".equals(i.getStringExtra("op"))){
                c.sendBroadcast(new Intent(Wire.ACK).setPackage(Wire.SYSUI).putExtra("ready",available()));return;
            }
            try{receive(i);}catch(Throwable t){fail(t);}
            if(i.hasExtra("delivery"))c.sendBroadcast(new Intent(Wire.ACK).setPackage(Wire.SYSUI).addFlags(Intent.FLAG_RECEIVER_FOREGROUND).putExtra("ready",available()).putExtra("key",i.getStringExtra("key")).putExtra("delivery",i.getLongExtra("delivery",-1)));
        }},Wire.EVENT,Wire.PRIVILEGED_SENDER);
        prefs.registerOnSharedPreferenceChangeListener(settings);
        module.info("Native island adapter ready");
        calls.start();
    }
    private void receive(Intent i)throws Exception{
        if(failed)return;
        String key=i.getStringExtra("key");if(key==null||key.length()>1024)return;
        if("remove".equals(i.getStringExtra("op"))){
            EntryStore.Entry<Intent> gone=store.get(key);
            if(store.remove(key))hide();
            // The call notification went away: a ringing call that never reached the audio path
            // has to disappear with it instead of lingering on the island.
            if(gone!=null&&gone.call)calls.onCallNoticeRemoved(gone.pkg);
            return;
        }
        String pkg=i.getStringExtra("package");
        if(!prefs.getBoolean("enabled",true))return;
        boolean call=i.getIntExtra("call",0)!=0;
        // A call notification may come from a package outside the message scope: it only feeds the
        // call island, which has its own switch.
        if(!call&&!prefs.getStringSet("scope_packages",Wire.DEFAULTS).contains(pkg))return;
        EntryStore.Entry<Intent> old=store.get(key);
        EntryStore.Entry<Intent> e=store.post(key,pkg,i.getStringExtra("conversation"),i.getStringExtra("fingerprint"),
                i.getIntExtra("number",1),i,SystemClock.elapsedRealtime(),call);
        if(e==null&&old!=null&&!Arrays.equals(old.payload.getByteArrayExtra(SenderAvatar.EXTRA),i.getByteArrayExtra(SenderAvatar.EXTRA))){
            EntryStore.Entry<Intent> refreshed=store.refresh(key,i);
            if(!call&&store.active()==refreshed)e=refreshed;
        }
        // A VoIP call notification is captured only so the call island can read its peer name,
        // portrait and phase; it must not appear as a second message pill or a badge.
        if(call){
            calls.onCallNotice(pkg,i.getStringExtra("title"),i.getStringExtra("body"),
                    i.getStringExtra("alt"),i.getByteArrayExtra(SenderAvatar.EXTRA));
            return;
        }
        if(e==null)return;
        show(e);
    }
    private void show(EntryStore.Entry<Intent> e)throws Exception{
        if(visibleKey!=null&&!visibleKey.equals(e.key))nativeIsland.remove(visibleKey);
        nativeIsland.show(e,store.unread(e.pkg));visibleKey=e.key;schedule(e);
    }
    private void schedule(EntryStore.Entry<Intent> e){
        long alarm=++alarmVersion;
        if(prefs.getBoolean("permanent",false))return;
        long delay=Math.max(0,e.receivedAt+Math.max(3,Math.min(30,prefs.getInt("show_seconds",8)))*1000L-SystemClock.elapsedRealtime());
        main.postDelayed(()->{
            if(alarm==alarmVersion&&!prefs.getBoolean("permanent",false)&&store.expire(e.key,e.generation))hide();
        },delay);
    }
    void hold(EntryStore.Entry<Intent> e){
        long alarm=++alarmVersion;
        if(prefs.getBoolean("permanent",false))return;
        main.postDelayed(()->{if(alarm==alarmVersion&&store.expire(e.key,e.generation))hide();},120000L);
    }
    /**
     * Re-arm the ordinary auto-hide with a fresh full window. Used when the expanded card folds
     * back into the small pill: the long hold that kept the card open must not outlive it, which
     * used to leave the small island sitting on screen for two minutes.
     */
    void rearm(EntryStore.Entry<Intent> e){
        long alarm=++alarmVersion;
        if(prefs.getBoolean("permanent",false))return;
        long delay=Math.max(3,Math.min(30,prefs.getInt("show_seconds",8)))*1000L;
        main.postDelayed(()->{
            if(alarm==alarmVersion&&!prefs.getBoolean("permanent",false)&&store.expire(e.key,e.generation))hide();
        },delay);
    }
    private void settingsChanged(){
        if(failed)return;
        if(!prefs.getBoolean("enabled",true)){store.clear();hide();return;}
        store.filter(prefs.getStringSet("scope_packages",Wire.DEFAULTS));
        EntryStore.Entry<Intent> e=store.active();
        if(e==null)hide();else try{show(e);}catch(Throwable t){fail(t);}
    }
    void hide(){
        ++alarmVersion;
        if(nativeIsland!=null){nativeIsland.resetCardMotion();}
        if(visibleKey!=null){try{nativeIsland.remove(visibleKey);}catch(Throwable t){module.error("Remove island",t);}visibleKey=null;}
    }
    /**
     * A tap on the call pill should land on the app's live call page, including when the call
     * runs in a floating window. The call notification's own intent is the most faithful route;
     * when the app has withdrawn it, raising the task that holds the call is the next best thing.
     */
    void openCall(String pkg){
        PendingIntent pi=null;
        try{
            EntryStore.Entry<Intent> e=store.latestCall(pkg);
            if(e!=null)pi=e.payload.getParcelableExtra("click");
        }catch(Throwable t){module.error("Read call intent",t);}
        module.info("Call tap pkg="+pkg+" noticeIntent="+(pi!=null));
        if(pi!=null)try{
            nativeIsland.launch(pi,false);
            module.info("Call tap through the call notification");
            return;
        }catch(Throwable t){module.error("Call tap intent",t);}
        if(bringCallTaskToFront(pkg))return;
        try{
            Intent launch=context.getPackageManager().getLaunchIntentForPackage(pkg);
            if(launch!=null){
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launch);
                module.info("Call tap opened the app");
            }
        }catch(Throwable t){module.error("Call tap launch",t);}
    }

    /** REAL_GET_TASKS and REORDER_TASKS are granted to the island service, so this works. */
    private boolean bringCallTaskToFront(String pkg){
        try{
            ActivityManager am=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
            if(am==null)return false;
            java.util.List<ActivityManager.RunningTaskInfo> tasks=am.getRunningTasks(20);
            if(tasks==null)return false;
            for(ActivityManager.RunningTaskInfo task:tasks){
                android.content.ComponentName top=task.topActivity;
                if(top==null||!pkg.equals(top.getPackageName()))continue;
                java.lang.reflect.Method move=ActivityManager.class.getMethod("moveTaskToFront",int.class,int.class);
                move.invoke(am,task.id,0);
                module.info("Call tap raised task "+task.id+" top="+top.flattenToShortString());
                return true;
            }
            module.info("Call tap found no task for "+pkg+" among "+tasks.size());
        }catch(Throwable t){module.error("Raise call task",t);}
        return false;
    }

    void launch(EntryStore.Entry<Intent> e,boolean small){
        if(small){launchSmall(e);return;}
        PendingIntent pi=e.payload.getParcelableExtra("click");
        if(pi==null){android.widget.Toast.makeText(context,"此通知没有可打开的入口",android.widget.Toast.LENGTH_SHORT).show();return;}
        try{
            nativeIsland.launch(pi,small); // native SystemUI route handles keyguard and background launch privileges
            dismiss(e.key);
        }catch(Throwable t){module.error("Open notification",t);android.widget.Toast.makeText(context,small?"当前应用不支持小窗":"打开失败",android.widget.Toast.LENGTH_SHORT).show();}
    }
    private boolean current(EntryStore.Entry<Intent> e){
        EntryStore.Entry<Intent> active=store.active();
        return available()&&prefs.getBoolean("enabled",true)&&active!=null&&active.generation==e.generation&&active.key.equals(e.key);
    }
    private void launchSmall(EntryStore.Entry<Intent> e){
        final boolean direct=nativeIsland.consumeDirectSmall();
        if(launchingSmall||!current(e))return;
        final Intent request;
        try{request=nativeIsland.smallWindowRequest(e);}
        catch(Throwable t){nativeIsland.returnCardMotion();smallFailure(t);return;}
        launchingSmall=true;
        // Own this exact visual transaction: a late watchdog must never reset a replacement card.
        CardMotion baseMotion=direct?null:nativeIsland.currentCardMotion();
        final CardMotion motion=baseMotion!=null?baseMotion:nativeIsland.currentRowMotion();
        final boolean[] waiting={true};
        final boolean[] accepted={false},error={false};
        try{nativeIsland.animateSmallWindow(motion,()->{
                if(!waiting[0]||!current(e)||error[0])return;
                try{
                    nativeIsland.beginCardHandoff(motion,e.key);
                    nativeIsland.startSmallWindow(request);accepted[0]=true;
                    module.info("Native multiwindow handoff overlaps card transition");
                }catch(Throwable t){nativeIsland.abortCardHandoff(motion,e.key);error[0]=true;smallFailure(t);}
            },()->{
                if(!waiting[0])return;waiting[0]=false;launchingSmall=false;
                if(accepted[0]){
                    // The original UP has finished and the native task has already been requested.
                    // Remove the island even if the application changed focus during its transition.
                    if(current(e)){
                        try{nativeIsland.finishCardHandoff(motion,e.key,()->dismiss(e.key));}catch(Throwable t){nativeIsland.abortCardHandoff(motion,e.key);if(motion!=null)motion.reset();module.error("Finish card handoff",t);}
                    }else if(motion!=null)motion.reset();
                }else{
                    if(motion!=null)motion.reset();
                    if(current(e))try{show(e);hold(e);}catch(Throwable t){fail(t);}
                }
            });
        }catch(Throwable t){waiting[0]=false;if(motion!=null)motion.reset();launchingSmall=false;smallFailure(t);return;}
        // Notification replacement or native view detachment may cancel the visual transition.
        main.postDelayed(()->{
            if(!waiting[0])return;waiting[0]=false;
            if(accepted[0]&&current(e)){
                try{nativeIsland.finishCardHandoff(motion,e.key,()->dismiss(e.key));}catch(Throwable t){nativeIsland.abortCardHandoff(motion,e.key);if(motion!=null)motion.reset();module.error("Handoff cleanup",t);}
            }else if(motion!=null)motion.reset();
            launchingSmall=false;
        },2200);
    }
    private void smallFailure(Throwable t){
        module.error("Native multiwindow handoff failed",t);
        android.widget.Toast.makeText(context,"暂时无法打开小窗，请点击卡片进入应用",android.widget.Toast.LENGTH_SHORT).show();
    }
    Context context(){return context;}
    NativeIsland island(){return nativeIsland;}
    void callSwipeAway(){calls.dismissed();}
    void dismiss(String key){store.dismissConversation(key);hide();}
    void swipedAway(EntryStore.Entry<Intent> entry){
        // Native controller owns the dismissal animation. Only expire this generation;
        // keep fingerprints and unread counts, and never cancel the notification.
        if(entry!=null&&Objects.equals(visibleKey,entry.key)&&store.expire(entry.key,entry.generation)){
            ++alarmVersion;visibleKey=null;
            
            module.info("Island dismissed by native swipe");
        }
    }
    void close(EntryStore.Entry<Intent> e){
        context.sendBroadcast(new Intent(Wire.COMMAND).setPackage(Wire.SYSUI).putExtra("op","cancel").putExtra("key",e.key));
        store.remove(e.key);hide();
    }
    private void fail(Throwable t){failed=true;hide();store.clear();module.error("Adapter disabled after failure",t);}
    void renderFailed(Throwable t){main.post(()->fail(t));}
}

