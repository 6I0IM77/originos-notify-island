package com.joelau.notifyisland;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;

/** Native rendering shared where verified; OS5 wire/build/launch has its own contract. */
final class NativeIsland {
    private final boolean originOs5;
    private final OriginProfile profile;
    private final NativeLandscape landscape;
    private final IslandHost host; private final ClassLoader cl; private final Context ctx;
    private final Class<?> beanClass,builderClass,receiverClass,loaderClass;
    private final Object receiver,nativeLoader;
    private final Field parentContainer,centerCutout,capsuleHeight;
    private final Method displayInstance,mainIsland,dataInstance,handleClick;
    private CardMotion cardMotion;
    private CardMotion collapsedMotion;
    // Long-press from the collapsed pill skips the custom source->window silhouette;
    // the pill morph reads as a half-size white block before the freeform fills in.
    private boolean directSmallLaunch;
    private CardPullGesture cardPull;
    private final Field materialContainer,transitionAnimator;
    private final Method materialAppearance;
    private final Object cardViewTag=new Object();
    // The capsule currently showing our expanded card, and the entry it belongs to. Used to
    // notice the card folding back into the small pill so the normal auto-hide can be re-armed.
    private View cardCapsule;
    private EntryStore.Entry<Intent> openCard;
    // Guards the collapse detection: the capsule also reports a near-zero amount while it is
    // still growing into the card, so only a 1 -> 0 transition counts as a fold-back.
    private boolean cardVisible;
    private final Map<Object,IslandSwipePolicy> swipeSessions=new WeakHashMap<>();
    private java.lang.ref.WeakReference<IslandIconView> capsuleIcon=new java.lang.ref.WeakReference<>(null);
    void dumpIconDiagnostics(){
        IslandIconView icon=capsuleIcon.get();
        String detail=icon==null?IslandIcons.diagnostics()+"; capsule icon not attached":icon.diagnostics();
        try(java.io.FileWriter file=new java.io.FileWriter(new java.io.File(ctx.getCacheDir(),"notify-icon-diagnostics.txt"))){file.write(detail);}
        catch(Exception ex){host.module.error("Write icon diagnostics",ex);}
        android.util.Log.i("NotifyIsland",detail);
    }
    private HandoffCleanup cleanup;
    private final class HandoffCleanup {
        final View container;final Object animator;final int visibility;
        final CardMotion motion;final RetiredIslandGate gate;
        boolean endingTransition,skipLogged;
        HandoffCleanup(View v,Object a,CardMotion m,String id){container=v;animator=a;motion=m;gate=new RetiredIslandGate(id);visibility=v.getVisibility();}
    }
    NativeIsland(IslandHost h,ClassLoader l,Context c)throws Exception{
        host=h;cl=l;ctx=c;
        beanClass=type("data.AtomIslandsBean");builderClass=type("data.AtomIslandsBean$Builder");
        OriginProfile requested=OriginProfile.current();
        boolean os5Contract=Os5IslandContract.matches(builderClass,type("IslandServiceListener"));
        originOs5=requested==OriginProfile.OS5||(requested==OriginProfile.UNKNOWN&&os5Contract);
        if(originOs5&&!os5Contract)throw new NoSuchMethodException("OriginOS5 island contract mismatch");
        NativeDensity density=new NativeDensity(cl);
        profile=originOs5?OriginProfile.OS5:density.originOs7?OriginProfile.OS7:OriginProfile.OS6;
        if(requested!=OriginProfile.UNKNOWN&&requested!=profile)throw new NoSuchMethodException("OriginOS profile and island interfaces disagree: "+requested+" / "+profile);
        landscape=new NativeLandscape(profile,cl);
        receiverClass=type("data.AtomIslandsDataReceiver");loaderClass=type("data.NewSuperXLoader");
        receiver=receiverClass.getMethod("getInstance").invoke(null);
        nativeLoader=loaderClass.getDeclaredConstructor().newInstance();
        receiverClass.getMethod("notifyAtomIsland",builderClass);receiverClass.getMethod("deleteAtomIsland",String.class);

        builderClass.getMethod("buildId",String.class);builderClass.getMethod("buildBusinessLoader",type("data.AtomIslandLoader"));
        // Verify every required signature before installing hooks or publishing an entry.
        for(String name:new String[]{"buildBusinessKey","buildSourcePackage","buildLandingInfoPkn"})builderClass.getMethod(name,String.class);
        if(!originOs5)builderClass.getMethod("buildNotificationId",String.class);
        for(String name:new String[]{"buildUserId","buildAtomIslandsType","buildPriority","buildLeftTemplate","buildRightTemplate","buildIslandClick","buildTemplateCard","buildShowTime","buildNewNode"})builderClass.getMethod(name,int.class);
        for(String name:new String[]{"buildSupportKeyguardShowing","buildSystemApp","buildScrollRemove","buildSupportNewNode","buildForceShow"})builderClass.getMethod(name,boolean.class);
        if(!originOs5)builderClass.getMethod("buildPermanent",boolean.class);
        for(String name:new String[]{"buildLeftContent","buildRightContent"})builderClass.getMethod(name,CharSequence.class);
        builderClass.getMethod("buildLeftIcon",Icon.class);builderClass.getMethod("buildClickIslandResp",PendingIntent.class);builderClass.getMethod("buildTimeout",long.class);
        beanClass.getMethod("getId");
        if(!originOs5)type("utils.IslandUtils").getMethod("isClickable",beanClass);
        type("utils.IslandUtils").getMethod("startPendingIntent",Context.class,PendingIntent.class);
        loaderClass.getMethod("generateCardView",type("view.CapsuleContentView"),beanClass);
        type("view.CapsuleContentView").getMethod("setContentView",View.class);
        for(String name:new String[]{"generateLeftIslandView","generateRightIslandView"})loaderClass.getMethod(name,type("view.CapsuleContentView"),beanClass);
        parentContainer=type("view.CapsuleContentView").getDeclaredField("mParentContainer");parentContainer.setAccessible(true);
        centerCutout=type("view.DynamicCapsuleContainer").getDeclaredField("mCenterCutout");centerCutout.setAccessible(true);
        capsuleHeight=type("view.DynamicCapsuleContainer").getDeclaredField("mCapsuleHeight");capsuleHeight.setAccessible(true);
        displayInstance=type("data.AtomIslandDisplayController").getMethod("getInstance");
        mainIsland=type("data.AtomIslandDisplayController").getMethod("getMainIsland");
        dataInstance=type("data.AtomIslandsDataController").getMethod("getInstance");
        handleClick=type("data.AtomIslandsDataController").getMethod("handleIslandClickEvent",int.class);
        receiverClass.getMethod("removeAnimationMessage",beanClass);
        beanClass.getMethod("setTimeout",long.class);
        type("utils.IslandNotifyHelper").getMethod("getInstance");
        type("utils.IslandNotifyHelper").getMethod("getIslandServiceListener");
        if(!originOs5)type("IslandServiceListener").getMethod("startPendingIntent",PendingIntent.class,int.class);
        Class<?> animatorType=type("animation.ContainerAnimatorCommon");
        materialAppearance=animatorType.getMethod("updateBlurAppearance",boolean.class);
        materialContainer=type("animation.AbsContainerAnimator").getDeclaredField("mContainer");materialContainer.setAccessible(true);
        type("view.CapsuleContentView").getMethod("setVisibleAmount",float.class);
        animatorType.getMethod("updateCapsuleBounds");animatorType.getMethod("isAnimationRunning");
        type("data.AtomIslandDisplayController").getMethod("getSideIsland");
        type("data.AtomIslandDisplayController").getMethod("updateRealToShow",ArrayList.class);
        type("state.IslandAmbientState").getMethod("disableNextAnimation");
        animatorType.getMethod("drawBounds",android.graphics.Canvas.class);
        animatorType.getMethod("animateToTransition");
        transitionAnimator=animatorType.getDeclaredField("mIslandTransitionAnimator");transitionAnimator.setAccessible(true);
    }
    private Class<?> type(String name)throws ClassNotFoundException{return Class.forName("com.vivo.island."+name,false,cl);}
    private Object call(Object o,String method,Class<?> t,Object value)throws Exception{return o.getClass().getMethod(method,t).invoke(o,value);}
    private Object read(Object o,String method)throws Exception{return o.getClass().getMethod(method).invoke(o);}
    private boolean ours(Object bean){try{return host.available()&&bean!=null&&String.valueOf(read(bean,"getId")).startsWith(Wire.PREFIX);}catch(Exception ignored){return false;}}
    void install()throws Exception{
        host.module.info("Native island profile="+profile+"; sender avatar and right-aligned details enabled");
        try{IslandIcons.useNativeStyle(ctx,cl);host.module.info("Icon outline: native island dimensions loaded; density adapter="+new NativeDensity(cl).profile());}
        catch(Exception ex){host.module.error("Native icon dimensions unavailable; using 6/20 fallback",ex);}
        installSwipeSupport();
        landscape.install(host.module,this::ours);
        // Restore BEFORE native state listeners see fresh content, including non-module islands.
        host.module.hook(type("data.AtomIslandDisplayController").getMethod("updateRealToShow",ArrayList.class)).intercept(chain->{
            HandoffCleanup transaction=cleanup;
            ArrayList<?> islands=(ArrayList<?>)chain.getArg(0);
            if(transaction!=null){
                try{
                    List<String> ids=new ArrayList<>();
                    for(Object bean:islands)ids.add(String.valueOf(read(bean,"getId")));
                    if(transaction.gate.releaseFor(ids))endHandoffCleanup(transaction,"new native content");
                }catch(Exception ex){endHandoffCleanup(transaction,"data inspection failed");host.module.error("Inspect handoff replacement",ex);}
            }
            describeIslands("enter",islands);
            Object result=chain.proceed();
            try{
                Object display=displayInstance.invoke(null);
                Object main=read(display,"getMainIsland"),side=read(display,"getSideIsland");
                host.module.info("Multi-island after: main="+beanId(main)+" side="+beanId(side));
            }catch(Throwable t){host.module.error("Describe islands",t);}
            return result;
        });
        // This flag is consumed by the state controller AND the window controller. Skipping
        // only the bounds spring still allows state-1 transitions and delayed window dismissal.
        host.module.hook(type("state.IslandAmbientState").getMethod("disableNextAnimation")).intercept(chain->{
            HandoffCleanup transaction=retiring();
            if(transaction!=null&&transaction.gate.suppressTransitions()){
                if(!transaction.skipLogged){transaction.skipLogged=true;host.module.info("Handoff: native retirement transition disabled");}
                return true;
            }
            return chain.proceed();
        });
        host.module.hook(type("animation.ContainerAnimatorCommon").getMethod("drawBounds",android.graphics.Canvas.class)).intercept(chain->{
            HandoffCleanup transaction=retiring();
            // Native callbacks may change View visibility. Never redraw the retired black
            // capsule even then; native layout, state updates and input cleanup still run.
            if(transaction!=null&&transaction.animator==chain.getThisObject())return null;
            return chain.proceed();
        });
        host.module.hook(type("animation.ContainerAnimatorCommon").getMethod("animateToTransition")).intercept(chain->{
            Object result=chain.proceed();
            HandoffCleanup transaction=retiring();
            if(transaction!=null&&transaction.animator==chain.getThisObject()&&transaction.gate.suppressTransitions())finishNativeTransition(transaction);
            return result;
        });
        host.module.hook(type("animation.ContainerAnimatorCommon").getMethod("updateCapsuleBounds")).intercept(chain->{
            HandoffCleanup transaction=retiring();
            if(transaction!=null&&transaction.animator==chain.getThisObject()&&transaction.gate.suppressTransitions()){
                chain.getThisObject().getClass().getMethod("requestDisableAnimForNextFrame").invoke(chain.getThisObject());
            }
            // Always run native layout, state-none callbacks and touch-region cleanup.
            return chain.proceed();
        });
        // OEM material blur belongs to the RenderNode, independent of View alpha. Suppress it
        // only on the container currently covered by our shell, never on unrelated native cards.
        host.module.hook(materialAppearance).intercept(chain->{
            Object container=materialContainer.get(chain.getThisObject());
            if(suppressNativeMaterial(container))return chain.proceed(new Object[]{false});
            return chain.proceed();
        });
        host.module.hook(type("view.CapsuleContentView").getMethod("setVisibleAmount",float.class)).intercept(chain->{
            Object result=chain.proceed();
            Object capsule=chain.getThisObject();
            // Folding the expanded card back to the small pill: the card's long hold no longer
            // applies, so re-arm the ordinary auto-hide or the pill would linger.
            Object amount=chain.getArg(0);
            if(capsule==cardCapsule&&amount instanceof Float){
                float visible=(Float)amount;
                if(visible>=0.9f)cardVisible=true;
                else if(visible<=0.02f&&cardVisible){cardVisible=false;foldedBack();}
            }
            if(suppressNativeMaterial(parentContainer.get(capsule))){
                View content=(View)read(capsule,"getContentView");
                if(content!=null&&content.getTag()==cardViewTag)content.setRenderEffect(null);
            }
            return result;
        });
        Class<?> utils=type("utils.IslandUtils");
        if(!originOs5)host.module.hook(utils.getMethod("isClickable",beanClass)).intercept(chain->{
            if(ours(chain.getArg(0)))return true;
            return chain.proceed();
        });
        host.module.hook(utils.getMethod("startPendingIntent",Context.class,PendingIntent.class)).intercept(chain->{
            EntryStore.Entry<Intent> e=host.store.active();
            if(e!=null&&Objects.equals(chain.getArg(1),e.payload.getParcelableExtra("click"))){host.launch(e,false);return null;}
            return chain.proceed();
        });
        Method card=loaderClass.getMethod("generateCardView",type("view.CapsuleContentView"),beanClass);
        host.module.hook(card).intercept(chain->{
            if(!ours(chain.getArg(1)))return chain.proceed();
            String id=String.valueOf(read(chain.getArg(1),"getId"));
            // The call pill opens the app on tap and has no detail card; let the native card
            // builder handle a downward drag instead of showing an empty capsule.
            if(id.equals(Wire.PREFIX+CALL_KEY))return chain.proceed();
            EntryStore.Entry<Intent> e=host.store.get(id.substring(Wire.PREFIX.length()));if(e==null)return null;
            try{ViewGroup capsule=(ViewGroup)chain.getArg(0);View content=card(e);
                capsule.getClass().getMethod("setContentView",View.class).invoke(capsule,content);
                cardCapsule=capsule;openCard=e;cardVisible=false;
            }catch(Throwable t){host.module.error("Render card",t);}
            return null;
        });
        // The native capsule swaps its content view when the card folds back to the small pill;
        // that is the moment the card's long hold stops being valid.
        host.module.hook(type("view.CapsuleContentView").getMethod("setContentView",View.class)).intercept(chain->{
            Object capsule=chain.getThisObject();
            Object content=chain.getArg(0);
            Object result=chain.proceed();
            if(capsule==cardCapsule&&content instanceof View&&((View)content).getTag()!=cardViewTag)foldedBack();
            return result;
        });
        for(boolean left:new boolean[]{true,false})host.module.hook(loaderClass.getMethod(left?"generateLeftIslandView":"generateRightIslandView",type("view.CapsuleContentView"),beanClass)).intercept(chain->{
            Object bean=chain.getArg(1);if(!ours(bean))return chain.proceed();
            try{
                String id=String.valueOf(read(bean,"getId"));
                if(id.equals(Wire.PREFIX+CALL_KEY)){
                    ViewGroup capsule=(ViewGroup)chain.getArg(0);
                    View callContent=callHalf(capsule,left);
                    capsule.getClass().getMethod("setContentView",View.class).invoke(capsule,callContent);
                    return null;
                }
                EntryStore.Entry<Intent> e=host.store.get(id.substring(Wire.PREFIX.length()));
                if(e==null)return null;
                ViewGroup capsule=(ViewGroup)chain.getArg(0);
                View content=half(capsule,bean,e,left);
                capsule.getClass().getMethod("setContentView",View.class).invoke(capsule,content);
                return null;
            }catch(Throwable t){host.renderFailed(t);return chain.proceed();}
        });
        // Dual state (main + left side circle): AtomMainIslandLeftTemplate pins the left half to
        // atom_is1and_min_left_width_half_in_dual (~96dp), so a short message kept the pill at
        // roughly half screen width with a dead gap next to the portrait. Single state uses the
        // much smaller atom_is1and_min_width_half, which is why the same text looked compact
        // there. Drop that dual floor for the module island only and let the measured content
        // drive ContainerAnimatorCommon's state-3 bounds; the side circle keeps its own bounds.
        host.module.hook(type("template.AtomMainIslandLeftTemplate").getMethod("updateMinimumWidth")).intercept(chain->{
            Object result=chain.proceed();
            try{
                Object display=displayInstance.invoke(null);
                Object main=read(display,"getMainIsland");
                if(main!=null&&ours(main)&&read(display,"getSideIsland")!=null){
                    ((View)chain.getThisObject()).setMinimumWidth(0);
                    if(!dualWidthLogged){dualWidthLogged=true;host.module.info("Dual island: left half width floor dropped for module content");}
                }
            }catch(Throwable t){host.module.error("Relax dual half width",t);}
            return result;
        });
        // Single-island state (2) sizes BOTH sides from max(left, right):
        // singleBounds = cutout/2 + max(...). The half that needs less then keeps the surplus
        // as dead black space, so a two-word message pill looked stretched with a wide gap
        // between the portrait and the text. Native state 1/3 already use asymmetric bounds
        // (cutout/2 + measured width per side) while transitioning, so mirroring that for our
        // own resting pill keeps the native shape, springs, touch regions and side circle intact.
        try{
            Class<?> boundaryType=type("widget.CapsuleBoundary");
            Field boundsLeft=boundaryType.getField("left"),boundsRight=boundaryType.getField("right");
            // mMainBounds / mCenterCutout live on AbsContainerAnimator, mCapContentLeft/Right too;
            // walking the superclass chain keeps this working if the OEM moves a field again.
            Class<?> animatorType=type("animation.ContainerAnimatorCommon");
            Field mainBounds=fieldIn(animatorType,"mMainBounds");
            Field animatorCutout=fieldIn(animatorType,"mCenterCutout");
            Field capLeft=fieldIn(animatorType,"mCapContentLeft");
            Field capRight=fieldIn(animatorType,"mCapContentRight");
            for(Field field:new Field[]{mainBounds,animatorCutout,capLeft,capRight})field.setAccessible(true);
            Method boundsMethod=fieldOwner(animatorType,"calculateBounds").getDeclaredMethod("calculateBounds",boundaryType,boundaryType,int.class);
            boundsMethod.setAccessible(true);
            host.module.hook(boundsMethod).intercept(chain->{
                Object result=chain.proceed();
                try{
                    if(((Number)chain.getArg(2)).intValue()!=2)return result;
                    Object animator=chain.getThisObject();
                    View leftContent=(View)capLeft.get(animator);
                    View rightContent=(View)capRight.get(animator);
                    if(leftContent==null||rightContent==null)return result;
                    int leftWidth=leftContent.getWidth(),rightWidth=rightContent.getWidth();
                    if(leftWidth<=0||rightWidth<=0)return result;
                    if(!carriesModuleContent(leftContent))return result;
                    float cutout=animatorCutout.getFloat(animator);
                    float half=cutout/2f;
                    Object target=chain.getArg(0);
                    boundsLeft.setFloat(target,half+leftWidth);
                    boundsRight.setFloat(target,half+rightWidth);
                    String size="left="+leftWidth+" right="+rightWidth+" cutout="+cutout+" => "+boundsLeft.getFloat(target)+"/"+boundsRight.getFloat(target);
                    if(!size.equals(tightWidthLog)){tightWidthLog=size;host.module.info("Single island: bounds follow content "+size);}
                }catch(Throwable t){host.module.error("Tight single island bounds",t);}
                return result;
            });
        }catch(Throwable t){host.module.error("Single island width hook unavailable",t);}
    }
    private static final String CALL_KEY="call";
    private static final long LIVE_POLL_MS=300L;
    /**
     * Call apps start their on-screen clock a moment after the accept, once the audio path is
     * established; counting from the accept itself made the island read about half a second
     * ahead of the app. Start the island clock half a second after the accept instead.
     */
    private static final int CALL_SETTLE_DEFAULT_MS=300;
    /** Delay between the accept and the first visible second, tunable from the settings page. */
    private long callSettle(){return Math.max(0,Math.min(800,host.prefs.getInt("call_settle_ms",CALL_SETTLE_DEFAULT_MS)));}
    private String callPackage,callName;
    private android.graphics.Bitmap callAvatar;
    private long callStarted;
    private boolean callSession;
    private boolean callTicking;
    /** The app gave no observable pickup moment, so the right half reads 通话中 without a clock. */
    private boolean callUntimed;
    private TextView callLabel;
    private int callGeneration;
    void showCallIsland(String pkg,long startedAt,String peer,android.graphics.Bitmap avatar)throws Exception{
        callPackage=pkg;callStarted=startedAt;callName=peer==null||peer.isBlank()?"通话":peer;callAvatar=avatar;callSession=true;
        notifyCallIsland();
        startCallTicker();
        host.module.info("Call island shown pkg="+pkg+" name="+callName+" avatar="+(avatar!=null));
    }
    /**
     * Apps that join their audio channel at dial time never expose the pickup, so a clock started
     * from any visible signal would count the ringback. Show 通话中 for the whole call instead.
     */
    void showCallIslandUnclocked(String pkg,String peer,android.graphics.Bitmap avatar)throws Exception{
        callUntimed=true;
        showCallIsland(pkg,0L,peer,avatar);
        host.module.info("Call island untimed pkg="+pkg);
    }
    /** The peer name/avatar arrived after the pill was already up: swap the content in place. */
    void updateCallIsland(String pkg,String peer,android.graphics.Bitmap avatar)throws Exception{
        if(!callSession||!pkg.equals(callPackage))return;
        boolean changed=false;
        if(peer!=null&&!peer.isBlank()&&!peer.equals(callName)){callName=peer;changed=true;}
        if(avatar!=null){callAvatar=avatar;changed=true;}
        if(!changed)return;
        notifyCallIsland();
        host.module.info("Call island updated name="+callName+" avatar="+(callAvatar!=null));
    }
    private void notifyCallIsland()throws Exception{
        String pkg=callPackage;
        Object b=builderClass.getDeclaredConstructor().newInstance();
        call(b,"buildId",String.class,Wire.PREFIX+CALL_KEY);
        if(!originOs5)call(b,"buildNotificationId",String.class,Wire.PREFIX+CALL_KEY);
        call(b,"buildBusinessKey",String.class,"BUSINESS_NEW_SUPER_X");
        call(b,"buildBusinessLoader",type("data.AtomIslandLoader"),nativeLoader);
        call(b,"buildSourcePackage",String.class,pkg);
        call(b,"buildLandingInfoPkn",String.class,"");
        call(b,"buildUserId",int.class,0);
        call(b,"buildAtomIslandsType",int.class,2);
        call(b,"buildPriority",int.class,3);
        // INCALL keeps the call in the same ranking group as messages, so a newer message takes
        // the main slot and squeezes the call into the left circle, exactly like the OEM phone.
        call(b,"buildScene",String.class,"INCALL");
        call(b,"buildLeftTemplate",int.class,1);
        call(b,"buildRightTemplate",int.class,4);
        call(b,"buildLeftIcon",Icon.class,appIcon(pkg));
        call(b,"buildLeftContent",CharSequence.class,"");
        call(b,"buildRightContent",CharSequence.class,callDuration());
        // 1 is the native "tap fires ClickIslandResp" mode. With 0 the tap only asks the
        // controller to re-aim the main island, so the intent was never sent and the pill did
        // nothing at all. The call pill has no detail card, so a tap must open the call page.
        call(b,"buildIslandClick",int.class,1);
        PendingIntent open=callClickIntent(pkg);
        if(open!=null)call(b,"buildClickIslandResp",PendingIntent.class,open);
        call(b,"buildTemplateCard",int.class,6);
        call(b,"buildTimeout",long.class,Long.MAX_VALUE);
        if(!originOs5)call(b,"buildPermanent",boolean.class,true);
        call(b,"buildShowTime",int.class,Integer.MAX_VALUE);
        call(b,"buildSupportKeyguardShowing",boolean.class,false);
        call(b,"buildSystemApp",boolean.class,false);
        call(b,"buildScrollRemove",boolean.class,true);
        call(b,"buildSupportNewNode",boolean.class,true);
        call(b,"buildNewNode",int.class,++callGeneration);
        call(b,"buildForceShow",boolean.class,false);
        receiverClass.getMethod("notifyAtomIsland",builderClass).invoke(receiver,b);
    }
    void destroyCallIsland()throws Exception{
        if(!callSession)return;
        callSession=false;callTicking=false;callLabel=null;callPackage=null;callUntimed=false;
        receiverClass.getMethod("deleteAtomIsland",String.class).invoke(receiver,Wire.PREFIX+CALL_KEY);
    }
    private void startCallTicker(){
        if(callTicking)return;
        callTicking=true;
        host.main.post(this::callTick);
    }
    private void callTick(){
        if(!callSession){callTicking=false;return;}
        TextView label=callLabel;
        if(label!=null)label.setText(callDuration());
        // 250ms refresh keeps the visible second within a quarter of a second of the real one.
        host.main.postDelayed(this::callTick,250L);
    }
    /**
     * Nothing on the right until the peer answers: an outgoing call counts from the pickup,
     * not from the dial, so the dialling phase shows 正在呼叫 instead of a growing timer.
     */
    private String callDuration(){
        if(callUntimed)return "通话中";
        if(callStarted>0){
            long seconds=Math.max(0,(SystemClock.elapsedRealtime()-callStarted)/1000L);
            return callClock(seconds);
        }
        return "正在呼叫";
    }
    /** The peer answered; re-render once so the width shrinks to the timer it now owns. */
    void markCallConnected()throws Exception{
        if(!callSession||callUntimed||callStarted>0)return;
        callStarted=SystemClock.elapsedRealtime()+callSettle();
        notifyCallIsland();
        host.module.info("Call island counting from answer");
    }
    private static String callClock(long seconds){
        long hours=seconds/3600,minutes=(seconds%3600)/60,rest=seconds%60;
        if(hours>0)return hours+":"+(minutes<10?"0":"")+minutes+":"+(rest<10?"0":"")+rest;
        return (minutes<10?"0":"")+minutes+":"+(rest<10?"0":"")+rest;
    }
    /**
     * Tapping a call pill must land on the app's own call page, not its home screen. The call
     * notification carries the intent that does exactly that (it is what the shade uses), so
     * reuse it and only fall back to opening the app itself when no call notification exists.
     */
    private PendingIntent callClickIntent(String pkg){
        try{
            EntryStore.Entry<Intent> e=host.store.latestCall(pkg);
            PendingIntent pi=e==null?null:e.payload.getParcelableExtra("click");
            if(pi!=null)return pi;
        }catch(Throwable ignored){}
        return callLaunchIntent(pkg);
    }
    private PendingIntent callLaunchIntent(String pkg){
        try{
            Intent launch=ctx.getPackageManager().getLaunchIntentForPackage(pkg);
            if(launch==null)return null;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return PendingIntent.getActivity(ctx,0,launch,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        }catch(Throwable ignored){return null;}
    }
    /**
     * Left half carries the peer portrait and name, right half only the running clock. Name on
     * the left is what removes the dead gap a portrait-only half used to leave, because the
     * single-island state sizes both halves from the wider side.
     */
    private View callHalf(ViewGroup capsule,boolean left)throws Exception{
        Object container=parentContainer.get(capsule);
        int height=nativeDimension(capsuleHeight,container);
        int cutout=nativeDimension(centerCutout,container);
        android.util.DisplayMetrics metrics=capsule.getResources().getDisplayMetrics();
        int screen=Math.min(metrics.widthPixels,metrics.heightPixels);
        String pkg=callPackage==null?"android":callPackage;
        String name=callName==null?"通话":callName;
        android.graphics.Bitmap avatar=callAvatar;
        String duration=callDuration();
        String measured=callUntimed||duration.equals("正在呼叫")?duration:"00:00";
        LinearLayout row=MessageViews.half(ctx,
                ()->MessageViews.adaptiveHalfWidth(ctx,screen,cutout,height,name,measured,MessageDisplay.CONTENT,null,false,left),
                ()->height,left,pkg,name,duration,avatar,MessageDisplay.CONTENT,null);
        if(!left&&row.getChildCount()>0){
            View last=row.getChildAt(row.getChildCount()-1);
            if(last instanceof TextView){callLabel=(TextView)last;callLabel.setText(callDuration());}
        }
        // The row swallows the tap before the native capsule's own click handler can fire,
        // so this listener is the only place that can decide where a tap lands.
        row.setOnClickListener(v->{
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            host.openCall(pkg);
        });
        row.setOnLongClickListener(v->false);
        return row;
    }
    private String tightWidthLog;
    private static Class<?> fieldOwner(Class<?> type,String name){
        Class<?> cursor=type;
        while(cursor!=null){
            try{cursor.getDeclaredField(name);return cursor;}catch(NoSuchFieldException ignored){cursor=cursor.getSuperclass();}
        }
        throw new IllegalStateException("No field "+name+" on "+type.getName());
    }
    private static Field fieldIn(Class<?> type,String name){
        try{return fieldOwner(type,name).getDeclaredField(name);}catch(NoSuchFieldException ex){throw new IllegalStateException(ex);}
    }
    /** True when the native capsule half contains a module row (it always starts with our portrait). */
    private static boolean carriesModuleContent(View half){
        if(half instanceof IslandIconView)return true;
        if(!(half instanceof ViewGroup))return false;
        ViewGroup group=(ViewGroup)half;
        if(group.getChildCount()>2)return false;
        for(int i=0;i<group.getChildCount();i++)if(carriesModuleContent(group.getChildAt(i)))return true;
        return false;
    }
    private String lastIslandSignature;
    private boolean dualWidthLogged;
    private static final java.util.HashMap<String,String> LAST_PILL_SIZE=new java.util.HashMap<>();
    private String beanId(Object bean){if(bean==null)return "null";try{return String.valueOf(read(bean,"getId"));}catch(Exception ignored){return "?";}}
    /** Read-only dump of the show list, so the system's own grouping rules can be read off the log. */
    private void describeIslands(String where,ArrayList<?> islands){
        try{
            StringBuilder signature=new StringBuilder();
            for(Object bean:islands)signature.append(beanId(bean)).append('|');
            if(signature.toString().equals(lastIslandSignature))return;
            lastIslandSignature=signature.toString();
            StringBuilder out=new StringBuilder("Multi-island "+where+": n="+islands.size());
            for(int i=0;i<islands.size();i++)out.append('\n').append("  [").append(i).append("] ").append(describe(islands.get(i)));
            host.module.info(out.toString());
        }catch(Throwable t){host.module.error("Describe islands",t);}
    }
    private String describe(Object bean){
        StringBuilder text=new StringBuilder(beanId(bean));
        String[] names={"getAtomIslandsType","getPriority","getBusinessKey","getLeftTemplate","getRightTemplate","getTemplateCard","getShowTime","getNewNode","getSourcePackage"};
        for(String name:names){
            try{Object value=bean.getClass().getMethod(name).invoke(bean);text.append(' ').append(name.substring(3)).append('=').append(value);}
            catch(Exception ignored){}
        }
        return text.toString();
    }

    private Field swipeField(Class<?> type,String name)throws Exception{
        Field field=type.getDeclaredField(name);field.setAccessible(true);return field;
    }
    private void installSwipeSupport()throws Exception{
        Class<?> helper=type("view.CapsuleSwipeHelper");
        Field touched=swipeField(helper,"mTouchedBean"),drag=swipeField(helper,"mIsDragging");
        Field dx=swipeField(helper,"mRealDistanceX"),dy=swipeField(helper,"mRealDistanceY");
        Field slop=swipeField(helper,"mSlideAwaySlop"),angle=swipeField(helper,"sSwipeAngle");
        Method dismiss=helper.getDeclaredMethod("shouldDismissOnActionUp");dismiss.setAccessible(true);
        Method dragOut=helper.getDeclaredMethod("shouldDragOutOnActionUp");dragOut.setAccessible(true);
        Method swipe=helper.getMethod("handleSwipe",MotionEvent.class);
        Method removed=type("data.AtomIslandsDataController").getMethod("handleSlideRemove",int.class,beanClass);
        host.module.hook(swipe).intercept(chain->{
            MotionEvent ev=(MotionEvent)chain.getArg(0);
            IslandSwipePolicy session=swipeSessions.computeIfAbsent(chain.getThisObject(),ignored->new IslandSwipePolicy());
            session.event(ev.getActionMasked(),ev.getPointerCount());
            CardPullGesture pull=cardPull;
            if(pull!=null&&pull.reserveNative(ev.getActionMasked(),ev.getPointerCount(),ev.getRawX(),ev.getRawY())){
                // Only our attached card can begin this sequence. Reserve its downward
                // MOVE before the OEM parent can cancel the child (OS7 DRAG_EXPANDED).
                // An initial sideways MOVE may have armed kind 3 without drawing yet.
                // If native dragging had actually intercepted, the child CANCEL would
                // already have ended this sequence, so we never reset an active native swipe.
                if(drag.getInt(chain.getThisObject())==3){drag.setInt(chain.getThisObject(),0);touched.set(chain.getThisObject(),null);}
                return false;
            }
            return chain.proceed();
        });
        host.module.hook(dismiss).intercept(chain->{
            Object object=chain.getThisObject();
            if(!ours(touched.get(object)))return chain.proceed();
            int kind=drag.getInt(object);
            if(kind!=1&&kind!=2)return chain.proceed(); // Expanded card keeps its native gesture.
            IslandSwipePolicy session=swipeSessions.get(object);
            if(session==null||session.cancelled())return 0;
            if(session.horizontal(dx.getFloat(object),dy.getFloat(object),slop.getInt(object),angle.getInt(null)))return 1;
            return chain.proceed();
        });
        host.module.hook(dragOut).intercept(chain->{
            // Outward swipes dismiss module islands, rather than recalling an old node.
            if(ours(touched.get(chain.getThisObject())))return false;
            return chain.proceed();
        });
        host.module.hook(removed).intercept(chain->{
            Object bean=chain.getArg(1);int kind=(Integer)chain.getArg(0);
            EntryStore.Entry<Intent> entry=null;
            boolean callSwiped=false;
            if((kind==1||kind==2)&&ours(bean)){
                String id=String.valueOf(read(bean,"getId"));
                callSwiped=(id.equals(Wire.PREFIX+CALL_KEY));
                if(!callSwiped)entry=host.store.get(id.substring(Wire.PREFIX.length()));
            }
            Object result=chain.proceed();
            if(entry!=null)host.swipedAway(entry);
            if(callSwiped)host.callSwipeAway();
            return result;
        });
    }
    private boolean suppressNativeMaterial(Object container){
        return container!=null&&((cardMotion!=null&&cardMotion.covers(container))||(cleanup!=null&&cleanup.container==container));
    }
    private View half(ViewGroup capsule,Object bean,EntryStore.Entry<Intent> e,boolean left)throws Exception{
        Object container=parentContainer.get(capsule);
        android.graphics.Bitmap portrait=usesPortrait()?avatar(e):null;
        LinearLayout row=MessageViews.half(ctx,()->halfWidth(capsule,container,e,left),()->nativeDimension(capsuleHeight,container),left,e.pkg,sender(e),rightText(e),portrait,displayMode(),
                host.prefs.getBoolean("show_count",true)?host.store.unread(e.pkg):null);
        if(left)capsuleIcon=new java.lang.ref.WeakReference<>((IslandIconView)row.getChildAt(0));
        final boolean isLeft=left;
        capsule.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,orr,ob)->{
            ViewGroup parent=v.getParent() instanceof ViewGroup?(ViewGroup)v.getParent():null;
            String size="capsule="+v.getWidth()+" parent="+(parent==null?-1:parent.getWidth())+" row="+row.getWidth();
            String key=isLeft?"L":"R";
            if(!size.equals(LAST_PILL_SIZE.get(key))){LAST_PILL_SIZE.put(key,size);
                host.module.info("Pill width "+(isLeft?"left":"right")+": "+size);}
        });
        row.setOnClickListener(v->{
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            // Collapsed tap expands the card; the expanded card itself launches the app.
            if(!expandCard(bean,e))host.launch(e,false);
        });
        row.setOnLongClickListener(new View.OnLongClickListener(){
          @Override public boolean onLongClickUseDefaultHapticFeedback(View v){return false;}
          @Override public boolean onLongClick(View v){
            v.performHapticFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE);
            // Long press on the collapsed pill opens the freeform window directly; do not
            // run the pill->window silhouette (it grows as a half-size white block first).
            directSmallLaunch=true;
            host.launch(e,true);
            return true;
          }
        });
        // Reuse the same island-to-freeform morph as the card pull-down, so a long
        // press on the collapsed pill animates into the small window instead of jumping.
        if(collapsedMotion!=null){collapsedMotion.dispose();collapsedMotion=null;}
        Drawable rowIcon=null;try{rowIcon=appIcon(e.pkg).loadDrawable(ctx).mutate();}catch(Exception ignored){}
        collapsedMotion=new CardMotion(row,rowIcon,splashBackground(e.pkg),this::hideNativeMaterial);
        collapsedMotion.prepare();
        // The icon-sampled tint belongs to the expanded card only; the collapsed pill keeps the
        // native surface, so a small island never looks tinted.
        return row;
    }
    CardMotion currentRowMotion(){return collapsedMotion;}
    boolean consumeDirectSmall(){boolean v=directSmallLaunch;directSmallLaunch=false;return v;}
    /** The expanded card went away (folded back to the pill or replaced): drop its long hold. */
    private void foldedBack(){
        EntryStore.Entry<Intent> e=openCard;
        openCard=null;cardCapsule=null;cardVisible=false;
        if(e==null)return;
        try{host.rearm(e);}catch(Throwable t){host.module.error("Rearm auto hide",t);}
    }
    private boolean expandCard(Object bean,EntryStore.Entry<Intent> e){
        try{
            Object current=mainIsland.invoke(displayInstance.invoke(null));
            if(!Objects.equals(read(current,"getId"),read(bean,"getId")))return false;
            receiverClass.getMethod("removeAnimationMessage",beanClass).invoke(receiver,current);
            beanClass.getMethod("setTimeout",long.class).invoke(current,System.currentTimeMillis()+120000L);
            host.hold(e);handleClick.invoke(dataInstance.invoke(null),6);
            return true;
        }catch(Throwable t){host.module.error("Expand message card",t);return false;}
    }
    private int nativeDimension(Field field,Object container){
        try{return field.getInt(container);}catch(IllegalAccessException ex){throw new IllegalStateException(ex);}
    }
    private int halfWidth(View capsule,Object container,EntryStore.Entry<Intent> e,boolean left){
        android.util.DisplayMetrics metrics=capsule.getResources().getDisplayMetrics();
        int screen=Math.min(metrics.widthPixels,metrics.heightPixels);
        int height=nativeDimension(capsuleHeight,container),cutout=nativeDimension(centerCutout,container);
        return MessageViews.adaptiveHalfWidth(ctx,screen,cutout,height,sender(e),rightText(e),displayMode(),
                host.prefs.getBoolean("show_count",true)?host.store.unread(e.pkg):null,host.prefs.getBoolean("wide",false),left);
    }
    private String sender(EntryStore.Entry<Intent> e){String s=e.payload.getStringExtra("title");return s==null||s.isEmpty()?e.payload.getStringExtra("label"):s;}
    private MessageDisplay displayMode(){return MessageDisplay.read(host.prefs);}
    private boolean usesPortrait(){return displayMode().avatarLeft;}
    private String rightText(EntryStore.Entry<Intent> e){return displayMode().rightText(e.payload.getStringExtra("body"),sender(e),e.payload.getStringExtra("label"));}
    private android.graphics.Bitmap avatar(EntryStore.Entry<Intent> e){return SenderAvatar.decode(e.payload.getByteArrayExtra(SenderAvatar.EXTRA));}
    /** Monotonic node token: every re-render looks like fresh content to the native controller. */
    private int nodeSeq;
    private int nextNode(long generation){
        int base=(int)Math.min(Integer.MAX_VALUE,generation);
        nodeSeq=Math.max(nodeSeq+1,base);
        return nodeSeq;
    }
    /** Settings changed while a pill is up: rebuild it in place with the new options. */
    void refreshCurrent(int unread)throws Exception{
        EntryStore.Entry<Intent> e=host.store.active();
        if(e==null)return;
        show(e,unread);
    }
    void show(EntryStore.Entry<Intent> e,int unread)throws Exception{
        if(cleanup!=null)endHandoffCleanup(cleanup,"new module content");
        cardCapsule=null;openCard=null;cardVisible=false;
        Object b=builderClass.getDeclaredConstructor().newInstance();
        Intent p=e.payload;boolean permanent=host.prefs.getBoolean("permanent",false);
        call(b,"buildId",String.class,Wire.PREFIX+e.key);
        if(!originOs5)call(b,"buildNotificationId",String.class,Wire.PREFIX+e.key);
        call(b,"buildBusinessKey",String.class,"BUSINESS_NEW_SUPER_X");
        call(b,"buildBusinessLoader",type("data.AtomIslandLoader"),nativeLoader);
        call(b,"buildSourcePackage",String.class,e.pkg);
        // Do not tie visibility to foreground-package transitions (A12).
        call(b,"buildLandingInfoPkn",String.class,"");
        call(b,"buildUserId",int.class,p.getIntExtra("user",0));
        // Official grouping: AtomIslandsInfo.updateInfoFromToShowList keeps a second island (and
        // AtomIslandsDataController.calculateToShowWithoutFilter copies it) only when neither bean
        // is ATOM_ISLAND_SINGLE(1). Type 1 is exclusive, so it evicted the native music/nav/call
        // island instead of squeezing it; Super X notification beans use COMBINATION(2).
        call(b,"buildAtomIslandsType",int.class,2);
        call(b,"buildPriority",int.class,3);
        // AtomIslandsComparator ranks the call/message scene group above music and navigation,
        // so a fresh message takes the main slot while the running realtime island folds left.
        call(b,"buildScene",String.class,"MMS");
        call(b,"buildLeftTemplate",int.class,1);
        call(b,"buildRightTemplate",int.class,4);
        android.graphics.Bitmap portrait=usesPortrait()?avatar(e):null;
        android.graphics.drawable.Icon icon=portrait==null?appIcon(e.pkg):Icon.createWithBitmap(portrait);
        call(b,"buildLeftIcon",Icon.class,icon);
        String title=p.getStringExtra("title");if(title==null||title.isEmpty())title=p.getStringExtra("label");
        MessageDisplay style=displayMode();
        // 右侧显示消息内容时，联系人名字放在左侧；右侧显示名字时左侧只留图标。
        call(b,"buildLeftContent",CharSequence.class,style.iconOnlyLeft()?"":title);
        call(b,"buildRightContent",CharSequence.class,rightText(e));
        call(b,"buildIslandClick",int.class,0);
        call(b,"buildClickIslandResp",PendingIntent.class,p.getParcelableExtra("click"));
        call(b,"buildTemplateCard",int.class,6);
        call(b,"buildTimeout",long.class,permanent?Long.MAX_VALUE:System.currentTimeMillis()+Math.max(3,Math.min(30,host.prefs.getInt("show_seconds",8)))*1000L);
        // OS5 schedules expiry from timeout alone; Long.MAX_VALUE is used above.
        if(!originOs5)call(b,"buildPermanent",boolean.class,permanent);
        call(b,"buildShowTime",int.class,permanent?Integer.MAX_VALUE:Math.max(3,Math.min(30,host.prefs.getInt("show_seconds",8))));
        call(b,"buildSupportKeyguardShowing",boolean.class,false);
        call(b,"buildSystemApp",boolean.class,false);
        call(b,"buildScrollRemove",boolean.class,true);
        call(b,"buildSupportNewNode",boolean.class,true);
        call(b,"buildNewNode",int.class,nextNode(e.generation));
        call(b,"buildForceShow",boolean.class,false);
        receiverClass.getMethod("notifyAtomIsland",builderClass).invoke(receiver,b);
    }
    private Icon appIcon(String pkg)throws Exception{
        return IslandIcons.icon(ctx,pkg);
    }
    void remove(String key)throws Exception{
        if(openCard!=null&&openCard.key.equals(key)){openCard=null;cardCapsule=null;}
        receiverClass.getMethod("deleteAtomIsland",String.class).invoke(receiver,Wire.PREFIX+key);
    }    void launch(PendingIntent pi,boolean small)throws Exception{
        if(small)throw new IllegalStateException("Use the native multiwindow handoff");
        if(originOs5){Os5IslandContract.launch(ctx,pi);return;}
        Object helper=type("utils.IslandNotifyHelper").getMethod("getInstance").invoke(null);
        Object listener=read(helper,"getIslandServiceListener");if(listener==null)throw new IllegalStateException("SystemUI not connected");
        type("IslandServiceListener").getMethod("startPendingIntent",PendingIntent.class,int.class).invoke(listener,pi,3);
    }
    Intent smallWindowRequest(EntryStore.Entry<Intent> e)throws Exception{
        PendingIntent pi=e.payload.getParcelableExtra("click");
        if(pi==null||!pi.isActivity())throw new UnsupportedOperationException("No activity PendingIntent");
        if(((KeyguardManager)ctx.getSystemService(Context.KEYGUARD_SERVICE)).isKeyguardLocked())throw new UnsupportedOperationException("Device locked");
        Class<?> manager=Class.forName("vivo.app.vivofreeform.VivoFreeformManager",false,cl);
        Object mgr=manager.getMethod("getInstance").invoke(null);
        if(!Boolean.TRUE.equals(manager.getMethod("isVivoFreeFormSupportsApp",String.class).invoke(mgr,e.pkg)))throw new UnsupportedOperationException("Application does not support freeform");
        Intent original=(Intent)PendingIntent.class.getMethod("getIntent").invoke(pi);
        if(original==null)throw new UnsupportedOperationException("Cannot resolve notification intent");
        // Same request used by this ROM's StatusBarNotificationActivityStarter.startFreeFormMode.
        // The system service owns bounds, task transitions and input focus; never force mode 106.
        Intent request=new Intent("com.vivo.smartmultiwindow.manager_smartmultiwindow_state").setPackage("com.vivo.smartmultiwindow")
            .putExtra("source_package_name",Wire.SYSUI).putExtra("entrance_way","notification_pull_down")
            .putExtra("component_name",original.getComponent()).putExtra("pending_intent",pi)
            .putExtra("mirror_type",e.payload.getIntExtra("user",0)==0?0:1);
        android.content.pm.ResolveInfo resolved=ctx.getPackageManager().resolveService(request,android.content.pm.PackageManager.MATCH_SYSTEM_ONLY);
        if(resolved==null||resolved.serviceInfo==null)throw new UnsupportedOperationException("Native multiwindow service unavailable");
        String permission=resolved.serviceInfo.permission;
        if(permission!=null&&ctx.checkSelfPermission(permission)!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new SecurityException("Native multiwindow permission unavailable");
        request.setComponent(new ComponentName(resolved.serviceInfo.packageName,resolved.serviceInfo.name));
        return request;
    }
    void beginCardHandoff(CardMotion motion,String key)throws Exception{
        if(cleanup!=null)endHandoffCleanup(cleanup,"replacement handoff");
        View container=motion==null?null:motion.coveredContainer();
        Object helper=type("utils.IslandNotifyHelper").getMethod("getInstance").invoke(null);
        Object animator=read(helper,"getAbsContainerAnimator");
        if(container==null&&animator!=null)container=(View)materialContainer.get(animator);
        String id=Wire.PREFIX+key;
        if(container==null||animator==null||hasOtherIsland(id))return;
        HandoffCleanup transaction=new HandoffCleanup(container,animator,motion,id);
        cleanup=transaction;
        // Keep the source in layout: the captured shell already covers it (container
        // alpha 0 in CardMotion.capture). Forcing INVISIBLE here drops the children and
        // forces a capsule relayout, which reads as a flash before the freeform window draws.
        hideNativeMaterial();
        host.module.info("Handoff: source drawing retired before native launch");
    }
    void abortCardHandoff(CardMotion motion,String key){
        HandoffCleanup transaction=cleanup;
        if(transaction!=null&&transaction.motion==motion&&transaction.gate.id.equals(Wire.PREFIX+key))endHandoffCleanup(transaction,"launch rejected");
    }
    void finishCardHandoff(CardMotion motion,String key,Runnable remove)throws Exception{
        HandoffCleanup transaction=retiring();
        if(transaction==null){beginCardHandoff(motion,key);transaction=cleanup;}
        final HandoffCleanup retiring=transaction;
        if(retiring!=null&&(retiring.motion!=motion||!retiring.gate.id.equals(Wire.PREFIX+key)))return;
        try{
            if(retiring!=null){
                retiring.gate.beginDeletion();
                retiring.animator.getClass().getMethod("requestDisableAnimForNextFrame").invoke(retiring.animator);
                // Finish an already running 200ms transition with its native completion
                // callbacks, before deleting. Cancellation alone leaves the state at 1.
                finishNativeTransition(retiring);
            }
            remove.run();
        }catch(RuntimeException|ReflectiveOperationException ex){
            if(retiring!=null)endHandoffCleanup(retiring,"deletion failed");throw ex;
        }
        finally{
            if(motion!=null)motion.reset();
            if(retiring!=null)host.main.postDelayed(()->verifyHandoffRemoval(retiring),1200);
        }
    }
    private boolean hasOtherIsland(String retiredId){
        try{
            Object display=displayInstance.invoke(null);
            for(String name:new String[]{"getMainIsland","getSideIsland","getCardIsland"}){
                Object bean=read(display,name);if(bean!=null&&!retiredId.equals(String.valueOf(read(bean,"getId"))))return true;
            }
            return false;
        }catch(Exception ex){return true;}
    }
    private HandoffCleanup retiring(){
        HandoffCleanup transaction=cleanup;
        if(transaction!=null&&hasOtherIsland(transaction.gate.id)){endHandoffCleanup(transaction,"replacement selected");return null;}
        return transaction;
    }
    private void finishNativeTransition(HandoffCleanup transaction)throws Exception{
        if(transaction.endingTransition)return;
        transaction.endingTransition=true;
        try{
            android.animation.ValueAnimator running=(android.animation.ValueAnimator)transitionAnimator.get(transaction.animator);
            if(running!=null&&running.isStarted())running.end();
        }finally{transaction.endingTransition=false;}
    }
    private void verifyHandoffRemoval(HandoffCleanup transaction){
        if(cleanup!=transaction)return;
        try{
            Object display=displayInstance.invoke(null);
            for(String name:new String[]{"getMainIsland","getSideIsland","getCardIsland"}){
                Object bean=read(display,name);
                if(bean!=null&&(!transaction.gate.removalObserved()||!transaction.gate.id.equals(String.valueOf(read(bean,"getId"))))){
                    endHandoffCleanup(transaction,"removal did not settle");return;
                }
            }
            // Empty is a valid final state, not a reason to reveal the old container. There
            // is no frame polling or timeout resurrection; only new content releases it.
            host.module.info("Handoff: removed source remains retired; input/window restored");
        }catch(Exception ex){endHandoffCleanup(transaction,"removal inspection failed");host.module.error("Verify handoff removal",ex);}
    }
    private void endHandoffCleanup(HandoffCleanup transaction,String reason){
        if(cleanup!=transaction)return;cleanup=null;
        transaction.container.setVisibility(transaction.visibility);transaction.container.invalidate();
        host.module.info("Handoff: retirement released ("+reason+")");
    }
    private void hideNativeMaterial(){
        try{
            Object helper=type("utils.IslandNotifyHelper").getMethod("getInstance").invoke(null);
            Object animator=read(helper,"getAbsContainerAnimator");
            if(animator!=null&&materialAppearance.getDeclaringClass().isInstance(animator))materialAppearance.invoke(animator,false);
        }catch(Exception ex){host.module.error("Clear covered native material",ex);}
    }
    boolean windowReadyForLaunch()throws Exception{
        Object state=type("state.DynamicCapsuleStateController").getMethod("getInstance").invoke(null);
        int value=((Number)read(state,"getState")).intValue();
        return value!=1&&value!=4;
    }
    void startSmallWindow(Intent request){
        if(ctx.startService(request)==null)throw new IllegalStateException("Multiwindow service rejected request");
    }
    CardMotion currentCardMotion(){return cardMotion;}
    void animateSmallWindow(CardMotion motion,Runnable launch,Runnable complete){
        if(motion==null){launch.run();host.main.postDelayed(complete,300);return;}
        motion.commit(launch,complete);
    }
    void resetCardMotion(){if(cardMotion!=null)cardMotion.reset();}
    void returnCardMotion(){if(cardMotion!=null)cardMotion.springBack();}
    private View card(EntryStore.Entry<Intent> e){
        int fg=Color.WHITE;
        Drawable morphIcon=null;try{morphIcon=appIcon(e.pkg).loadDrawable(ctx).mutate();}catch(Exception ignored){}
        final Drawable launchIcon=morphIcon;
        final int launchBackground=splashBackground(e.pkg);
        final ScrollView[] cardScroll=new ScrollView[1];
        LinearLayout box=new LinearLayout(ctx){
            final CardPullGesture pull=new CardPullGesture(dp(CardPullGesture.COMMIT_DISTANCE_DP),dp(64),ViewConfiguration.get(ctx).getScaledTouchSlop());
            final CardMotion motion=new CardMotion(this,launchIcon,launchBackground,NativeIsland.this::hideNativeMaterial);
            boolean settling,cancelTouch;float downX,downY;
            @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(cardMotion!=null&&cardMotion!=motion)cardMotion.reset();if(cardPull!=null&&cardPull!=pull)cardPull.endSequence();cardPull=pull;cardMotion=motion;motion.prepare();}
            @Override protected void onDetachedFromWindow(){pull.endSequence();if(cardPull==pull)cardPull=null;motion.dispose();if(cardMotion==motion)cardMotion=null;super.onDetachedFromWindow();}
            @Override public boolean dispatchTouchEvent(MotionEvent ev){
                int action=ev.getActionMasked();
                if(action==MotionEvent.ACTION_DOWN){cancelTouch=false;pull.begin(ev.getRawX(),ev.getRawY());}
                try{
                    if(ev.getPointerCount()>1&&!cancelTouch){
                        cancelTouch=true;pull.reserveNative(action,ev.getPointerCount(),ev.getRawX(),ev.getRawY());
                        // Cancel the current child click as well as the pull. Keep this
                        // sequence reserved until its real UP/CANCEL, so the native parent
                        // cannot turn the remaining finger into a swipe or a launch.
                        MotionEvent cancel=MotionEvent.obtain(ev);cancel.setAction(MotionEvent.ACTION_CANCEL);
                        try{super.dispatchTouchEvent(cancel);}finally{cancel.recycle();}
                    }
                    return cancelTouch||super.dispatchTouchEvent(ev);
                }
                finally{if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){pull.endSequence();cancelTouch=false;}}
            }
            @Override public boolean onInterceptTouchEvent(MotionEvent ev){
                if(settling)return true;
                int action=ev.getActionMasked();
                if(action==MotionEvent.ACTION_DOWN){downX=ev.getRawX();downY=ev.getRawY();pull.begin(ev.getRawX(),ev.getRawY());}
                if(ev.getPointerCount()>1||action==MotionEvent.ACTION_CANCEL)pull.cancel();
                // An overflowing message body scrolls first; reserve the pull-down small
                // window gesture only once the body cannot scroll in that direction.
                ScrollView bodyScroll=cardScroll[0];
                if(bodyScroll!=null&&action==MotionEvent.ACTION_MOVE&&bodyScroll.canScrollVertically(ev.getRawY()>downY?1:-1)){pull.cancel();return false;}
                if(action==MotionEvent.ACTION_MOVE&&pull.move(ev.getRawX(),ev.getRawY()))return true;
                return super.onInterceptTouchEvent(ev);
            }
            @Override public boolean onTouchEvent(MotionEvent ev){
                int action=ev.getActionMasked();
                if(settling)return true;
                if(action==MotionEvent.ACTION_DOWN){downX=ev.getRawX();downY=ev.getRawY();pull.begin(ev.getRawX(),ev.getRawY());}
                if(ev.getPointerCount()>1||action==MotionEvent.ACTION_CANCEL){boolean consumed=pull.dragging();pull.cancel();motion.springBack();return consumed||super.onTouchEvent(ev);}
                ScrollView bodyScroll=cardScroll[0];
                boolean bodyScrolling=bodyScroll!=null&&action==MotionEvent.ACTION_MOVE&&bodyScroll.canScrollVertically(ev.getRawY()>downY?1:-1);
                if(bodyScrolling)pull.cancel();
                if(action==MotionEvent.ACTION_MOVE&&!bodyScrolling){pull.move(ev.getRawX(),ev.getRawY());if(pull.dragging())motion.drag(pull.deltaX(ev.getRawX()),pull.deltaY(ev.getRawY()));}
                if(pull.dragging()){
                    if(action==MotionEvent.ACTION_UP){
                        if(pull.finish(ev.getRawX(),ev.getRawY())){
                            performHapticFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE);settling=true;
                            host.main.post(()->{host.launch(e,true);settling=false;});
                        }else motion.springBack();
                    }
                    return true;
                }
                if(action==MotionEvent.ACTION_UP)pull.cancel();
                return super.onTouchEvent(ev);
            }
            @Override public boolean performClick(){return super.performClick();}
            @Override protected void onMeasure(int widthSpec,int heightSpec){
                // The OEM window includes the physical camera area. Keep the whole header
                // below it, even when WindowInsets are delivered after this card is created.
                WindowInsets insets=getRootWindowInsets();
                DisplayCutout cutout=insets==null?null:insets.getDisplayCutout();
                // Symmetric vertical breathing room: the top inset matches the bottom one,
                // and only grows when the camera cutout actually sits over the card.
                int safeTop=dp(16);
                if(cutout!=null){
                    View root=getRootView();int[] origin=new int[2];root.getLocationOnScreen(origin);
                    safeTop=Math.max(safeTop,cutout.getSafeInsetTop()+dp(4)-origin[1]-root.getPaddingTop());
                }
                if(getPaddingTop()!=safeTop)setPadding(dp(24),safeTop,dp(24),dp(6));
                super.onMeasure(widthSpec,heightSpec);
            }
        };box.setTag(cardViewTag);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(24),dp(16),dp(24),dp(6));
        // Width/position/corners remain owned by the same native capsule as music.
        // Height now hugs the actual content instead of inheriting the tall music card
        // height, which left a large dead gap between the message text and the buttons.
        box.setLayoutParams(new FrameLayout.LayoutParams(-1,-2));
        android.graphics.Bitmap portrait=usesPortrait()?avatar(e):null;
        // Radial glow sampled from the contact's avatar, falling back to the app icon when the
        // notification carries no sender image; the collapsed pill keeps the native surface.
        android.graphics.Bitmap tintSource=portrait!=null?portrait:avatar(e);
        int[] tint=IslandTint.of(ctx,e.pkg,tintSource);
        if(tint!=null&&tint.length>=3){
            host.module.info("Card tint "+e.pkg+" "
                    +String.format("#%06X #%06X #%06X",tint[0]&0xffffff,tint[1]&0xffffff,tint[2]&0xffffff));
        }
        // The card carries the three hues sampled from the portrait, spread as one broad wash with
        // soft pools on top; the collapsed pill keeps the native surface.
        box.setBackground(IslandTint.card(tint,dp(32)));
        LinearLayout header=MessageViews.header(ctx,e.pkg,sender(e),portrait,fg);
        box.addView(header,new LinearLayout.LayoutParams(-1,dp(28)));
        TextView title=(TextView)header.getChildAt(1);
        TextView body=text(e.payload.getStringExtra("body"),14,0xffc9cbd0);
        body.setPadding(0,dp(3),0,dp(3));
        // Wrap the full message across multiple lines instead of clipping it to one line.
        body.setSingleLine(false);
        body.setEllipsize(TextUtils.TruncateAt.END);
        body.setMaxLines(8);
        body.setGravity(Gravity.START|Gravity.TOP);
        body.setLineSpacing(dp(2),1f);
        body.setIncludeFontPadding(false);
        ScrollView scroll=new ScrollView(ctx);scroll.setFillViewport(false);scroll.setMinimumHeight(0);
        scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
        cardScroll[0]=scroll;
        // The message block only takes the lines it needs, and caps at about six lines so a
        // long message scrolls instead of pushing the buttons off the card.
        final int messageCap=dp(120);
        FrameLayout messageArea=new FrameLayout(ctx){
            @Override protected void onMeasure(int widthSpec,int heightSpec){
                super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(messageCap,MeasureSpec.AT_MOST));
            }
        };
        messageArea.addView(scroll,new FrameLayout.LayoutParams(-1,-2));
        // Equal breathing room above and below the message body: its gap to the header's
        // avatar/name and its gap to the "小窗回复" button row must match.
        LinearLayout.LayoutParams messageParams=new LinearLayout.LayoutParams(-1,-2);
        messageParams.topMargin=dp(10);messageParams.bottomMargin=dp(10);
        box.addView(messageArea,messageParams);
        View.OnClickListener open=v->{v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);host.launch(e,false);};
        title.setOnClickListener(open);body.setOnClickListener(open);box.setOnClickListener(open);
        LinearLayout buttons=new LinearLayout(ctx);box.addView(buttons);
        Button reply=cardButton("小窗回复");buttons.addView(reply,new LinearLayout.LayoutParams(0,dp(34),1));
        // WeChat and several other notification sources expose no RemoteInput.
        // Their supported reply route is the OEM freeform window, so use the
        // same verified handoff as the card's downward gesture.
        reply.setContentDescription("小窗回复");
        reply.setOnClickListener(v->host.launch(e,true));
        header.setOnClickListener(open);
        LinearLayout.LayoutParams closeParams=new LinearLayout.LayoutParams(0,dp(34),1);closeParams.leftMargin=dp(10);
        Button close=cardButton("关闭");buttons.addView(close,closeParams);close.setOnClickListener(v->host.close(e));
        // Push the drag handle down: leave a clear gap below the buttons and keep only a
        // small inset under the line, so it reads as a bottom affordance, not a button edge.
        LinearLayout.LayoutParams handleParams=new LinearLayout.LayoutParams(-1,dp(14));
        handleParams.topMargin=dp(10);
        FrameLayout handleArea=new FrameLayout(ctx);box.addView(handleArea,handleParams);
        View handle=new View(ctx);GradientDrawable line=new GradientDrawable();line.setColor(0xff8b8d92);line.setCornerRadius(dp(2));handle.setBackground(line);
        handleArea.addView(handle,new FrameLayout.LayoutParams(dp(38),dp(3),Gravity.CENTER));
        handleArea.setContentDescription("下拉以小窗打开");handleArea.setOnClickListener(v->Toast.makeText(ctx,"向下拖动卡片，以小窗打开",Toast.LENGTH_SHORT).show());
        return box;
    }
    private int musicCardHeight(){
        if(originOs5)return Os5IslandContract.cardHeight(ctx,cl);
        try{
            Object manager=type("music.RemoteMusicManager").getMethod("getInstance").invoke(null);
            Field field=manager.getClass().getDeclaredField("mMusicWidget");field.setAccessible(true);
            View music=(View)field.get(manager);
            if(music!=null&&music.isLaidOut()){
                // Read only: never create/reparent a music widget or register media listeners.
                int height=music.getHeight();
                if(height>dp(100)&&height<dp(240))return height;
            }
        }catch(Exception ex){host.module.info("Music card measured size unavailable; using verified native resource");}
        try{
            NativeDensity density=new NativeDensity(cl);
            if(density.originOs7){
                Class<?> dimen=type("R$dimen");
                int id=dimen.getField("is1and_music_card_height").getInt(null);
                int height=density.fix(ctx).getResources().getDimensionPixelSize(id);
                if(height>dp(80)&&height<dp(260))return height;
            }
        }catch(Exception ex){host.module.info("OS7 native card height unavailable; using music resource fallback");}
        try{
            Context music=ctx.createPackageContext("com.vivo.musicwidgetmix",0);
            Context fixed=new NativeDensity(cl).fix(music);
            // OriginOS 6 IslandMusicWidget.reLayoutView uses 157dp on this phone.
            int id=fixed.getResources().getIdentifier("dp_157","dimen","com.vivo.musicwidgetmix");
            if(id!=0)return fixed.getResources().getDimensionPixelSize(id);
        }catch(Exception ex){host.module.info("Music card resource unavailable; using OriginOS 6 phone baseline");}
        return dp(157);
    }
    private Button cardButton(String label){
        Button button=new Button(ctx);button.setText(label);button.setTextSize(14);button.setTextColor(Color.WHITE);button.setAllCaps(false);
        button.setMaxLines(1);button.setHorizontallyScrolling(false);button.setGravity(Gravity.CENTER);
        button.setAutoSizeTextTypeUniformWithConfiguration(12,14,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        button.setPadding(dp(12),0,dp(12),0);button.setMinHeight(0);button.setMinimumHeight(0);
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xff292d34);bg.setCornerRadius(dp(19));
        button.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x44ffffff),bg,null));return button;
    }
    private int splashBackground(String pkg){
        int fallback=(ctx.getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES?0xff202124:Color.WHITE;
        try{
            Context app=ctx.createPackageContext(pkg,0);
            android.content.res.Resources.Theme theme=app.getResources().newTheme();
            int style=ctx.getPackageManager().getApplicationInfo(pkg,0).theme;
            if(style==0)return fallback;theme.applyStyle(style,true);
            android.util.TypedValue value=new android.util.TypedValue();
            for(int attr:new int[]{android.R.attr.windowSplashScreenBackground,android.R.attr.windowBackground}){
                if(theme.resolveAttribute(attr,value,true)&&value.type>=android.util.TypedValue.TYPE_FIRST_COLOR_INT&&value.type<=android.util.TypedValue.TYPE_LAST_COLOR_INT&&Color.alpha(value.data)==255)return value.data;
            }
        }catch(Exception ignored){}
        return fallback;
    }
    private TextView text(String value,int size,int color){TextView v=new TextView(ctx);v.setText(value);v.setTextSize(size);v.setTextColor(color);return v;}
    private int dp(int n){return Math.round(n*ctx.getResources().getDisplayMetrics().density);}
}

