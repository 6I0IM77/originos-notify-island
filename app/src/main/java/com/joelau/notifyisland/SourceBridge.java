package com.joelau.notifyisland;
import android.app.Notification;
import android.app.PendingIntent;
import java.util.ArrayList;
import java.util.List;
import android.content.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.*;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class SourceBridge {
    private final ModuleMain module; private final SharedPreferences prefs;
    private final OriginProfile profile=OriginProfile.current();
    private final java.util.concurrent.ExecutorService collector=java.util.concurrent.Executors.newSingleThreadExecutor();
    private Context context; private volatile NotificationListenerService listener;
    private final Set<String> callLogged=ConcurrentHashMap.newKeySet();
    private final Set<String> tracked=ConcurrentHashMap.newKeySet();
    private final Set<String> playbackLogged=ConcurrentHashMap.newKeySet();
    /** Packages that post a plain ongoing notification while a video is playing. */
    private static final Set<String> VIDEO_PACKAGES=new java.util.HashSet<>(java.util.Arrays.asList(
            "com.ss.android.ugc.aweme","com.ss.android.ugc.aweme.lite","com.ss.android.ugc.live",
            "com.smile.gifmaker","com.kuaishou.nebula","tv.danmaku.bili","com.google.android.youtube"));
    private final Handler main=new Handler(Looper.getMainLooper());
    private final DeliveryQueue<Intent> pending=new DeliveryQueue<>();
    private NativeConnection connection;
    private long wakeUntil;
    private boolean wakeErrorLogged;
    private volatile long hostReadyAt;
    private long probeAt;
    private final Runnable pump=this::pump;
    private final SharedPreferences.OnSharedPreferenceChangeListener settings;
    SourceBridge(ModuleMain m,SharedPreferences p){module=m;prefs=p;settings=(changed,key)->main.post(()->{
        if(prefs.getBoolean("enabled",true))requestWake();else{pending.filter(false,Wire.DEFAULTS,SystemClock.elapsedRealtime());main.removeCallbacks(pump);wakeUntil=0;}
    });}
    void install(ClassLoader cl)throws Exception{
        connection=new NativeConnection(cl);
        Class<?> cls=Class.forName("com.android.systemui.statusbar.NotificationListener",false,cl);
        int count=0;
        for(Method method:cls.getDeclaredMethods()){
            if((method.getName().equals("onNotificationPosted")||method.getName().equals("onNotificationRemoved"))
                    &&method.getParameterCount()>0&&method.getParameterTypes()[0]==StatusBarNotification.class){
                boolean posted=method.getName().equals("onNotificationPosted");
                module.hook(method).intercept(chain->{
                    Object result=chain.proceed();
                    try{
                        listener=(NotificationListenerService)chain.getThisObject();
                        if(context==null)attach(listener.getApplicationContext());
                        StatusBarNotification s=(StatusBarNotification)chain.getArg(0);
                        if(s!=null&&(!posted||eligible(s)))collector.execute(()->{
                            try{if(posted)post(s);else removed(s.getKey());}
                            catch(Throwable t){module.error("Collect notification",t);}
                        });
                    }catch(Throwable t){module.error("Notification bridge",t);}
                    return result;
                });count++;
            }
        }
        if(count==0)throw new NoSuchMethodException("Notification callbacks");
        // A changed optional filter must not prevent notification collection from starting.
        try{HeadsUpFilter.install(module,cl,this::suppressHeadsUp);}
        catch(Throwable t){module.error("Heads-up filtering unavailable; retaining banners",t);}
        module.info("Notification callbacks installed: "+count);
    }
    private boolean suppressHeadsUp(StatusBarNotification s){
        if(!eligible(s)||hostReadyAt<=0||SystemClock.elapsedRealtime()-hostReadyAt>=25000)return false;
        Notification n=s.getNotification();
        // Preserve the existing OS6 policy, including full-screen events and group summaries.
        return n.fullScreenIntent==null&&hasContent(n);
    }
    synchronized void attach(Context c){
        if(context!=null)return;context=c.getApplicationContext();
        Wire.register(context,new BroadcastReceiver(){public void onReceive(Context c,Intent i){
            Wire.pong(c,i,prefs,"source",listener!=null?"通知采集已连接":"钩子已加载，等待通知回调");
            if(Wire.validPing(i,prefs))requestWake();
        }},Wire.PING,null);
        Wire.register(context,new BroadcastReceiver(){public void onReceive(Context c,Intent i){
            if(i.hasExtra("ready"))hostReadyAt=i.getBooleanExtra("ready",false)?SystemClock.elapsedRealtime():0;
            if(i.hasExtra("delivery"))pending.acknowledge(i.getStringExtra("key"),i.getLongExtra("delivery",-1));
        }},Wire.ACK,Wire.PRIVILEGED_SENDER);
        Wire.register(context,new BroadcastReceiver(){public void onReceive(Context c,Intent i){
            String key=i.getStringExtra("key");
            if(!"cancel".equals(i.getStringExtra("op"))||key==null||!tracked.contains(key))return;
            try{if(listener!=null)listener.cancelNotification(key);}catch(Throwable t){module.error("Cancel notification",t);}
        }},Wire.COMMAND,Wire.PRIVILEGED_SENDER);
        Wire.register(context,new BroadcastReceiver(){public void onReceive(Context c,Intent i){
            if(!"call_peer".equals(i.getStringExtra("op")))return;
            String pkg=i.getStringExtra("package");
            PendingIntent reply=i.getParcelableExtra("reply");
            if(reply==null||pkg==null||listener==null)return;
            try{
                StatusBarNotification[] all=listener.getActiveNotifications();
                StatusBarNotification chosen=null;
                if(all!=null)for(StatusBarNotification sbn:all){
                    if(!pkg.equals(sbn.getPackageName()))continue;
                    Notification n=sbn.getNotification();
                    if(n==null||(n.flags&Notification.FLAG_GROUP_SUMMARY)!=0)continue;
                    boolean callish=Notification.CATEGORY_CALL.equals(n.category)||mentionsCall(n);
                    if(chosen==null||(callish&&!mentionsCall(chosen.getNotification()))
                            ||(callish==mentionsCall(chosen.getNotification())&&sbn.getPostTime()>chosen.getPostTime()))chosen=sbn;
                }
                Intent out=new Intent(Wire.PEER_REPLY).setPackage(Wire.SYSUI).putExtra("package",pkg);
                if(chosen!=null){
                    Notification n=chosen.getNotification();
                    String name=peerName(n,pkg);
                    if(name!=null)out.putExtra("name",name);
                    try{
                        byte[] avatar=SenderAvatar.extract(context,pkg,n,profile);
                        if(avatar!=null)out.putExtra("avatar",avatar);
                    }catch(RuntimeException ex){module.error("Call avatar unavailable",ex);}
                    module.info("Call peer from active notification: pkg="+pkg+" name="+name+" time="+chosen.getPostTime());
                }else module.info("Call peer: no active notification for "+pkg);
                reply.send(context,0,out);
            }catch(Throwable t){module.error("Resolve call peer",t);}
        }},Wire.PEER,Wire.PRIVILEGED_SENDER);
        module.info("SystemUI bridge ready; avatar profile="+profile);
        prefs.registerOnSharedPreferenceChangeListener(settings);
        main.post(this::requestWake);
    }
    private void requestWake(){
        if(!prefs.getBoolean("enabled",true))return;
        wakeUntil=SystemClock.elapsedRealtime()+DeliveryQueue.TTL;main.removeCallbacks(pump);pump();
    }
    private void pump(){
        main.removeCallbacks(pump);
        long now=SystemClock.elapsedRealtime();
        pending.filter(prefs.getBoolean("enabled",true),prefs.getStringSet("scope_packages",Wire.DEFAULTS),now);
        if(!prefs.getBoolean("enabled",true))return;
        boolean bound=false;
        try{bound=connection.ensureBound();}catch(Throwable t){if(!wakeErrorLogged){wakeErrorLogged=true;module.error("Native island binding unavailable",t);}}
        // ServiceConnection is asynchronous. Resend until the host's receiver acknowledges each item.
        if(bound){
            if(now-probeAt>=10000||probeAt==0){probeAt=now;context.sendBroadcast(new Intent(Wire.EVENT).setPackage(Wire.PLUGIN).putExtra("op","probe"));}
            for(DeliveryQueue.Item<Intent> item:pending.snapshot())context.sendBroadcast(item.value);
        }else hostReadyAt=0;
        main.postDelayed(pump,!pending.isEmpty()||(!bound&&now<wakeUntil)?500:10000);
    }
    private boolean eligible(StatusBarNotification s){
        if(s==null||!prefs.getBoolean("enabled",true))return false;
        Notification n=s.getNotification();
        if(n==null||(n.flags&Notification.FLAG_GROUP_SUMMARY)!=0)return false;
        // A VoIP call keeps an ongoing notification that also carries a media session, so the
        // playback filter would swallow the only place the peer name lives. Calls always get in,
        // even when the package is outside the message scope: the call island has its own switch.
        if(callLike(n)){
            if(callLogged.add(s.getPackageName()))
                module.info("Call notification captured: pkg="+s.getPackageName()+" category="+n.category+" scope="
                        +prefs.getStringSet("scope_packages",Wire.DEFAULTS).contains(s.getPackageName()));
            return true;
        }
        if(!prefs.getStringSet("scope_packages",Wire.DEFAULTS).contains(s.getPackageName()))return false;
        // Playback already has its own island; a second copy carrying the video title would only
        // fight with it. Chat content always wins, whatever the template says.
        return !playback(s,n);
    }
    private boolean callLike(Notification n){        if(n==null)return false;
        if(Notification.CATEGORY_CALL.equals(n.category))return true;
        Bundle x=n.extras;
        if(x==null)return false;
        // QQ words its calls without the word 通话: "正在呼叫我…" when a peer rings you,
        // "等待对方接听…" on the caller side. Those only count when the notification is shaped
        // like a call (ongoing, full screen, or carrying answer/reject buttons), so an ordinary
        // chat message that happens to contain 呼叫 stays a message.
        boolean callShaped=(n.flags&Notification.FLAG_ONGOING_EVENT)!=0||n.fullScreenIntent!=null||callButtons(n);
        for(String k:new String[]{Notification.EXTRA_TITLE,Notification.EXTRA_CONVERSATION_TITLE,
                Notification.EXTRA_TEXT,Notification.EXTRA_BIG_TEXT,Notification.EXTRA_SUB_TEXT,
                Notification.EXTRA_INFO_TEXT}){
            CharSequence value=x.getCharSequence(k);
            if(value==null)continue;
            String text=value.toString();
            if(text.contains("通话")||text.contains("邀请")||text.contains("来电"))return true;
            // These phrases only ever appear on a call notification, so they need no extra shape.
            if(text.contains("正在呼叫我")||text.contains("呼叫你")||text.contains("等待对方接听")
                    ||text.contains("正在等待对方"))return true;
            if(callShaped&&text.contains("正在呼叫"))return true;
        }
        return false;
    }
    private boolean callButtons(Notification n){
        if(n.actions==null)return false;
        for(Notification.Action action:n.actions){
            CharSequence title=action.title;
            if(title!=null&&(title.toString().contains("接听")||title.toString().contains("拒绝")
                    ||title.toString().contains("挂断")))return true;
        }
        return false;
    }

    /**
     * True only for the notification a media player actually uses while it plays: a media template,
     * a media session token, the transport category, or, for the video apps, an ongoing notification
     * without a progress bar. A bare progress bar is never treated as playback, so downloads, installs
     * and exports stay ordinary notifications, and grouped message notifications always reach the island.
     */
    private boolean playback(StatusBarNotification s,Notification n){
        Bundle x=n.extras;
        String template=x==null?"":String.valueOf(x.getCharSequence(Notification.EXTRA_TEMPLATE,""));
        boolean chat=template.contains("MessagingStyle")||(x!=null&&x.containsKey(Notification.EXTRA_MESSAGES));
        if(chat)return false;
        boolean mediaTemplate=template.contains("MediaStyle");
        boolean mediaSession=x!=null&&(x.containsKey(Notification.EXTRA_MEDIA_SESSION)||x.containsKey("android.mediaSession")||x.containsKey("media_session"));
        boolean transport=Notification.CATEGORY_TRANSPORT.equals(n.category);
        boolean progress=x!=null&&(x.containsKey(Notification.EXTRA_PROGRESS)||x.containsKey(Notification.EXTRA_PROGRESS_MAX));
        boolean ongoing=(n.flags&(Notification.FLAG_ONGOING_EVENT|Notification.FLAG_FOREGROUND_SERVICE))!=0;
        boolean videoOngoing=ongoing&&!progress&&VIDEO_PACKAGES.contains(s.getPackageName());
        boolean hit=mediaTemplate||mediaSession||transport||videoOngoing;
        if(hit&&playbackLogged.add(s.getPackageName()))
            module.info("Playback notification skipped: pkg="+s.getPackageName()+" template="+template
                +" category="+n.category+" flags=0x"+Integer.toHexString(n.flags)
                +" media="+mediaTemplate+"/"+mediaSession+" transport="+transport+" progress="+progress);
        return hit;
    }
    private boolean hasContent(Notification n){
        Bundle x=n.extras;if(x==null)return false;
        for(String k:new String[]{Notification.EXTRA_TITLE,Notification.EXTRA_CONVERSATION_TITLE,Notification.EXTRA_TEXT,Notification.EXTRA_BIG_TEXT})if(!bounded(x.getCharSequence(k),1).isEmpty())return true;
        android.os.Parcelable[] messages=x.getParcelableArray(Notification.EXTRA_MESSAGES);return messages!=null&&messages.length>0;
    }
    private void post(StatusBarNotification s){
        if(context==null||!eligible(s))return;
        Notification n=s.getNotification();if(n==null||(n.flags&Notification.FLAG_GROUP_SUMMARY)!=0)return;
        Bundle x=n.extras==null?Bundle.EMPTY:n.extras;
        String title=stripAggregate(bounded(x.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE,x.getCharSequence(Notification.EXTRA_TITLE)),160));
        String body=bounded(x.getCharSequence(Notification.EXTRA_BIG_TEXT,x.getCharSequence(Notification.EXTRA_TEXT)),4000);
        int number=Math.max(1,n.number);String fingerprint=s.getPostTime()+"\n"+title+"\n"+body;
        android.os.Parcelable[] messages=x.getParcelableArray(Notification.EXTRA_MESSAGES);
        if(messages!=null&&messages.length>0){
            number=Math.max(number,messages.length);
            if(messages[messages.length-1] instanceof Bundle){
                Bundle b=(Bundle)messages[messages.length-1];body=bounded(b.getCharSequence("text"),4000);
                android.app.Person person=b.getParcelable("sender_person",android.app.Person.class);
                String sender=stripAggregate(bounded(person==null?b.getCharSequence("sender"):person.getName(),160));
                if(!sender.isEmpty())title=sender;
                fingerprint=b.getLong("time")+"\n"+sender+"\n"+body;
            }
        }
        if(body.isEmpty()&&title.isEmpty())return;
        String shortcut=n.getShortcutId();
        String conversation=s.getUserId()+":"+s.getPackageName()+":"+(shortcut==null?s.getKey():shortcut);
        String label=s.getPackageName();
        try{label=String.valueOf(context.getPackageManager().getApplicationLabel(context.getPackageManager().getApplicationInfo(s.getPackageName(),0)));}catch(Exception ignored){}
        Intent out=new Intent(Wire.EVENT).setPackage(Wire.PLUGIN).putExtra("op","post")
            .putExtra("key",s.getKey()).putExtra("package",s.getPackageName()).putExtra("user",s.getUserId())
            .putExtra("conversation",conversation).putExtra("title",title).putExtra("body",body)
            .putExtra("label",label).putExtra("number",number).putExtra("fingerprint",fingerprint)
            .putExtra("postTime",s.getPostTime()).putExtra("click",n.contentIntent)
            .putExtra("call",callLike(n)?1:0).putExtra("call_type",callType(x));
        // Voice-call notifications often keep the peer name in the sub text, info text or the
        // expanded lines while the title only repeats the application name.
        StringBuilder alt=new StringBuilder();
        for(CharSequence candidate:new CharSequence[]{x.getCharSequence(Notification.EXTRA_TITLE),
                x.getCharSequence(Notification.EXTRA_SUB_TEXT),x.getCharSequence(Notification.EXTRA_INFO_TEXT)})
            if(candidate!=null&&candidate.length()>0)alt.append(candidate).append('\u0001');
        CharSequence[] lines=x.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if(lines!=null)for(CharSequence line:lines)if(line!=null&&line.length()>0)alt.append(line).append('\u0001');
        if(alt.length()>0)out.putExtra("alt",bounded(alt,600));
        try{
            byte[] avatar=SenderAvatar.extract(context,s.getPackageName(),n,profile);
            if(avatar!=null)out.putExtra(SenderAvatar.EXTRA,avatar);
        }catch(RuntimeException ex){module.error("Sender avatar unavailable; retaining app icon",ex);}
        tracked.add(s.getKey());main.post(()->{
            DeliveryQueue.Item<Intent> item=pending.put(s.getKey(),s.getPackageName(),SystemClock.elapsedRealtime(),out);
            out.putExtra("delivery",item.id);requestWake();
        });
    }
    /**
     * Android 12 call style stores the call phase in the notification extras: 1 for an
     * unanswered incoming call, 2 for an ongoing one. Apps that use it let us tell a ring from
     * a live call without guessing from text.
     */
    @SuppressWarnings("deprecation")
    private static int callType(Bundle x){
        if(x==null)return 0;
        try{return x.getInt("android.callType",0);}catch(Throwable ignored){return 0;}
    }
    private boolean mentionsCall(Notification n){
        if(n==null||n.extras==null)return false;
        Bundle x=n.extras;
        for(String k:new String[]{Notification.EXTRA_TITLE,Notification.EXTRA_TEXT,Notification.EXTRA_BIG_TEXT,
                Notification.EXTRA_SUB_TEXT,Notification.EXTRA_INFO_TEXT,Notification.EXTRA_CONVERSATION_TITLE}){
            CharSequence value=x.getCharSequence(k);
            if(value!=null){
                String text=value.toString();
                if(text.contains("通话")||text.contains("邀请")||text.contains("来电")
                        ||text.contains("呼叫我")||text.contains("呼叫你")||text.contains("等待对方接听"))return true;
            }
        }
        return false;
    }
    /** Peer name of a call notification, stripped of the boilerplate around it. */
    private String peerName(Notification n,String pkg){
        if(n==null||n.extras==null)return null;
        Bundle x=n.extras;
        List<CharSequence> candidates=new ArrayList<>();
        for(String k:new String[]{Notification.EXTRA_CONVERSATION_TITLE,Notification.EXTRA_TITLE,
                Notification.EXTRA_SUB_TEXT,Notification.EXTRA_INFO_TEXT,Notification.EXTRA_TEXT,Notification.EXTRA_BIG_TEXT}){
            CharSequence value=x.getCharSequence(k);
            if(value!=null&&value.length()>0)candidates.add(value);
        }
        CharSequence[] lines=x.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if(lines!=null)for(CharSequence line:lines)if(line!=null&&line.length()>0)candidates.add(line);
        String label=pkg;
        try{label=String.valueOf(context.getPackageManager().getApplicationLabel(context.getPackageManager().getApplicationInfo(pkg,0)));}catch(Exception ignored){}
        for(CharSequence candidate:candidates){
            String text=candidate.toString().trim();
            if(text.isEmpty()||text.equals(label))continue;
            for(String token:CALL_BOILER)text=text.replace(token,"");
            text=text.trim();
            while(!text.isEmpty()&&"与和·-—:：()/,".contains(text.substring(0,1)))text=text.substring(1).trim();
            while(!text.isEmpty()&&"与和·-—:：()/,".contains(text.substring(text.length()-1)))text=text.substring(0,text.length()-1).trim();
            if(text.isEmpty()||text.equals(label)||text.equals("微信")||text.equals("QQ"))continue;
            return text.length()>18?text.substring(0,18):text;
        }
        return null;
    }
    private static final String[] CALL_BOILER={"语音通话中","视频通话中","正在通话中","邀请你语音通话","邀请你视频通话",
            "邀请你通话","向您发起通话","发起通话","的语音通话","的视频通话","语音通话","视频通话","通话中","通话","来电",
            "正在呼叫我","正在呼叫你","等待对方接听","正在等待对方接听","正在等待","正在呼叫"};
    private void removed(String key){if(tracked.remove(key)&&context!=null)main.post(()->{pending.remove(key);context.sendBroadcast(new Intent(Wire.EVENT).setPackage(Wire.PLUGIN).putExtra("op","remove").putExtra("key",key));});}
    static String bounded(CharSequence s,int max){if(s==null)return "";String t=s.toString();return t.length()>max?t.substring(0,max):t;}
    // Drop the aggregated "（N条新消息）" / "(N条新消息)" suffix so the island keeps the contact name only.
    private static String stripAggregate(CharSequence s){
        if(s==null)return "";
        String t=s.toString().trim();
        t=t.replaceAll("\\s*[（(]\\s*\\d+\\s*条?\\s*新?消息\\s*[）)]\\s*$","").trim();
        return t;
    }
}
