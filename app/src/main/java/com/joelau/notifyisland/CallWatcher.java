package com.joelau.notifyisland;

import android.content.Context;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.AudioRecordingConfiguration;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.*;

/**
 * Third-party voice calls (WeChat / QQ) never go through Telecom, so the system phone island
 * cannot see them. SystemUI holds MODIFY_AUDIO_ROUTING and QUERY_AUDIO_STATE, so the module
 * reads the live audio clients instead: a call that is up holds a VOICE_COMMUNICATION playback
 * (or recording) client owned by one of those packages. Duration runs from the first detection
 * until the client disappears for two consecutive polls, which ignores the short blips when the
 * user switches to speaker, mutes or moves to a headset.
 */
final class CallWatcher {
    /** Reliable, known-good peers; used when "every app" is switched off. */
    private static final Set<String> PACKAGES=new LinkedHashSet<>(Arrays.asList(
            "com.tencent.mm","com.tencent.mobileqq","com.tencent.tim"));
    /**
     * Only these apps expose the pickup moment (their own call notification changes wording, or
     * their microphone opens after the ringback). Every other app joins its audio channel at dial
     * time, so a clock started from any system signal would read the ringback as talking. Those
     * show 通话中 instead of a wrong timer.
     */
    private static final Set<String> TIMED=new LinkedHashSet<>(Arrays.asList(
            "com.tencent.mm","com.tencent.mobileqq","com.tencent.tim"));
    /**
     * The OEM dialer already owns a native call island; a second one would duplicate it. The
     * SystemUI plugin itself also opens a voice-communication client while the island animates,
     * and it outranks the caller in the config list, which is how a bogus "系统界面组件 通话中"
     * pill used to replace the real one.
     */
    private static final Set<String> NATIVE_CALL=Set.of("android","com.android.systemui","com.android.incallui",
            "com.android.server.telecom","com.android.phone","com.android.dialer","com.google.android.dialer",
            "com.vivo.phone","com.vivo.telecom","com.vivo.incallui","com.vivo.systemuiplugin","com.vivo.systemui",
            "com.joelau.notifyisland");
    /** Idle polling is cheap; an active call is polled fast so the answer moment is tight. */
    private static final long IDLE_MS=1200L, LIVE_MS=300L;
    private static final long HANGOVER_MS=1000L;
    /** A ringing call has no audio client yet; its own notification keeps it alive. */
    private static final long NOTICE_HOLD_MS=45000L, NOTICE_GONE_MS=2000L;
    /** Once the notification says the call is up, it may outlive every audio client we can see. */
    private static final long NOTICE_ANSWERED_MS=600000L;
    /** How far back a call notification may sit to still describe this call. */
    private static final long NOTICE_FRESH_MS=60000L, ANSWER_FRESH_MS=3000L;
    /**
     * WeChat opens its call page, and the voice channel with it, on both the dial and the ring,
     * so its own notification wording is not what the user wants the pill to follow: its call is
     * timed straight from the moment the call page exists, like the first version did.
     */
    private static final Set<String> RAW_CLOCK=Set.of("com.tencent.mm");
    private final IslandHost host;
    private final Handler main=new Handler(Looper.getMainLooper());
    private AudioManager audio;
    private String activePackage;
    private boolean connectedNow,callConnected,micNow;
    /** The pill was raised by the call notification alone, before any audio client existed. */
    private boolean noticeOnly;
    private boolean noticeAnswered;
    private long noticeGoneAt;
    private long sessionStart;
    private boolean ringLogged;
    /** Last package/mic pair reported to logcat, so only real transitions are logged. */
    private String debugPkg;
    private boolean debugMic;
    private long lastSeen;
    private boolean dismissed;
    private boolean running;
    private boolean started;

    CallWatcher(IslandHost host){this.host=host;}

    void start(){
        if(running||!host.available())return;
        running=true;
        try{
            audio=(AudioManager)host.context().getSystemService(Context.AUDIO_SERVICE);
            if(audio==null){running=false;return;}
            main.post(this::poll);
        }catch(Throwable t){running=false;host.module.error("Call watcher unavailable",t);}
    }

    /** The user swiped the call island away: keep it off until this call really ends. */
    void dismissed(){
        dismissed=true;
        if(activePackage!=null)try{host.island().destroyCallIsland();}catch(Throwable ignored){}
    }

    private void poll(){
        if(!running)return;
        String pkg=null;
        try{pkg=detect();}catch(Throwable t){host.module.error("Detect call audio",t);}
        try{
            long now=SystemClock.elapsedRealtime();
            // QQ tells the phase in its own notification ("等待对方接听" until the peer picks up),
            // so the clock starts on the wording change, not on the dial. WeChat is excluded:
            // it grabs the voice channel the moment the call page opens, which is what the pill
            // has always counted from.
            long since=activePackage!=null?sessionStart-ANSWER_FRESH_MS:now-ANSWER_FRESH_MS;
            boolean ringing=pkg!=null&&!RAW_CLOCK.contains(pkg)
                    &&ringingNotice(pkg,activePackage!=null?sessionStart-NOTICE_FRESH_MS:now-NOTICE_FRESH_MS);
            if(pkg!=null&&(!pkg.equals(debugPkg)||micNow!=debugMic)){
                debugPkg=pkg;debugMic=micNow;
                host.module.info("Call state pkg="+pkg+" mic="+micNow+" playback="+playbackSummary());
            }
            if(ringing&&!ringLogged){ringLogged=true;host.module.info("Call ringing pkg="+pkg+" mic="+micNow);}
            // WeChat keeps "通话中" in its call notification from the moment the call page
            // opens, so for it only real voice capture proves the peer is on the line (that is
            // the behaviour it always had). QQ's notification wording is trustworthy and flips
            // at the pickup, so it keeps starting the clock on the wording change.
            boolean answered=pkg!=null&&!RAW_CLOCK.contains(pkg)&&answeredNoticeAny(pkg,since);
            boolean connected=pkg!=null&&(micNow||answered)&&!ringing;
            if(pkg==null){
                // Time-based instead of poll counts: the live poll is fast, so two misses would
                // end the island on a brief speaker/mute blip. A call that only exists as a
                // notification (ringing inbound, no audio client yet) gets the ring window.
                long limit=noticeOnly?(noticeGoneAt>0?NOTICE_GONE_MS
                        :(noticeAnswered?NOTICE_ANSWERED_MS:NOTICE_HOLD_MS)):HANGOVER_MS;
                if(activePackage!=null&&now-lastSeen>=limit)end();
            }else if(!pkg.equals(activePackage)){
                begin(pkg,connected);
            }else{
                lastSeen=now;
                if(noticeOnly){noticeOnly=false;noticeGoneAt=0;}
                if(connected&&!callConnected){
                    // The peer just picked up: start counting from this poll, not from the dial.
                    callConnected=true;
                    if(TIMED.contains(pkg)){
                        host.module.info("Call answered; timer starts now");
                        host.island().markCallConnected();
                    }
                }
            }
        }catch(Throwable t){host.module.error("Track call state",t);}
        main.postDelayed(this::poll,activePackage==null?IDLE_MS:LIVE_MS);
    }
    private void end(){
        String pkg=activePackage;
        activePackage=null;dismissed=false;started=false;noticeOnly=false;noticeAnswered=false;noticeGoneAt=0;
        sessionStart=0;ringLogged=false;
        try{host.island().destroyCallIsland();}catch(Throwable t){host.module.error("Hide call island",t);}
        host.module.info("Call island ended for "+pkg);
    }

    private void begin(String pkg,boolean connected){
        activePackage=pkg;lastSeen=SystemClock.elapsedRealtime();sessionStart=lastSeen;ringLogged=false;
        dismissed=false;connectedNow=connected;callConnected=connected;
        if(!enabled()){
            host.module.info("Call island disabled; "+pkg+" detected only");
            return;
        }
        EntryStore.Entry<Intent> entry=bestEntry(pkg);
        String name=callerName(entry,pkg);
        android.graphics.Bitmap avatar=avatarOf(entry);
        host.module.info("Call island pkg="+pkg+" name="+name+" avatar="+(avatar!=null)
                +" phase="+(connected?"connected":"dialing")+" mic="+micNow);
        long settle=Math.max(0,Math.min(800,host.prefs.getInt("call_settle_ms",300)));
        try{
            if(TIMED.contains(pkg))host.island().showCallIsland(pkg,connected?SystemClock.elapsedRealtime()+settle:0L,name,avatar);
            else host.island().showCallIslandUnclocked(pkg,name,avatar);
        }
        catch(Throwable t){host.module.error("Show call island",t);}
        askPeer(pkg);
    }

    /**
     * The collector can read the live notification even when our own filter dropped it (a call
     * notification that carries a media session looks like playback to us), so ask it directly
     * and upgrade the pill once the answer arrives.
     */
    private void askPeer(String pkg){
        try{
            Context context=host.context();
            PendingIntent reply=PendingIntent.getBroadcast(context,91,
                    new Intent(Wire.PEER_REPLY).setPackage(Wire.SYSUI),
                    PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);
            context.sendBroadcast(new Intent(Wire.PEER).setPackage(Wire.ID)
                    .putExtra("op","call_peer").putExtra("package",pkg).putExtra("reply",reply));
        }catch(Throwable t){host.module.error("Ask call peer",t);}
    }

    /** Reply from the collector; ignored unless the same call is still on screen. */
    void peer(String pkg,String name,byte[] avatar){
        if(activePackage==null||!pkg.equals(activePackage))return;
        if(name==null||name.isBlank())return;
        android.graphics.Bitmap bitmap=avatar==null?null:SenderAvatar.decode(avatar);
        try{host.island().updateCallIsland(pkg,name,bitmap);}
        catch(Throwable t){host.module.error("Update call island",t);}
    }

    /**
     * The app's own call notification, forwarded by the host with its raw text. Both directions
     * announce the call through this pill: an inbound ring has no audio client until answered, and
     * the caller side also posts "等待对方接听" before any usable client exists. Whichever comes
     * first raises "正在呼叫"; the audio path then takes over the clock.
     */
    void onCallNotice(String pkg,String title,String body,String alt,byte[] avatarBytes){
        if(!enabled()||!accepted(pkg))return;
        String text=(title==null?"":title)+"\n"+(body==null?"":body)+"\n"+(alt==null?"":alt);
        // A finished call leaves a notification behind ("未接来电", "通话结束"): never a live pill.
        if(text.contains("未接")||text.contains("已取消")||text.contains("通话结束")
                ||text.contains("已结束")||text.contains("拒接"))return;
        boolean answered=text.contains("通话中")||text.contains("已接通")||text.contains("正在语音通话")
                ||text.contains("正在视频通话");
        String name=noticeName(title,body,alt,pkg);
        android.graphics.Bitmap avatar=SenderAvatar.decode(avatarBytes);
        // WeChat's call notification says "通话中" the moment the call page opens, so for it the
        // wording is not an answer signal at all: only the voice capture may start the clock.
        if(answered&&!RAW_CLOCK.contains(pkg))noticeAnswered=true;
        if(activePackage==null){
            startFromNotice(pkg,name,avatar);
            return;
        }
        if(!pkg.equals(activePackage))return;
        lastSeen=SystemClock.elapsedRealtime();
        try{
            if(name!=null&&!name.isBlank())host.island().updateCallIsland(pkg,name,avatar);
            if(answered&&!RAW_CLOCK.contains(pkg)&&!callConnected&&TIMED.contains(pkg)){
                callConnected=true;
                host.module.info("Call answered per notification; timer starts now");
                host.island().markCallConnected();
            }
        }catch(Throwable t){host.module.error("Update call island from notification",t);}
    }

    /** A ringing call that never reached the audio path disappears with its notification. */
    void onCallNoticeRemoved(String pkg){
        if(activePackage==null||!pkg.equals(activePackage)||!noticeOnly)return;
        noticeGoneAt=SystemClock.elapsedRealtime();lastSeen=noticeGoneAt;
        host.module.info("Call notification gone for "+pkg);
    }

    private void startFromNotice(String pkg,String name,android.graphics.Bitmap avatar){
        activePackage=pkg;lastSeen=SystemClock.elapsedRealtime();sessionStart=lastSeen;ringLogged=false;
        dismissed=false;
        noticeOnly=true;noticeAnswered=false;noticeGoneAt=0;connectedNow=false;callConnected=false;
        host.module.info("Call island from notification pkg="+pkg+" name="+name+" avatar="+(avatar!=null));
        try{
            if(TIMED.contains(pkg))host.island().showCallIsland(pkg,0L,name,avatar);
            else host.island().showCallIslandUnclocked(pkg,name,avatar);
        }catch(Throwable t){host.module.error("Show incoming call island",t);}
        askPeer(pkg);
    }

    /** Peer name read straight from the notification text, without the call boilerplate. */
    private String noticeName(String title,String body,String alt,String pkg){
        String label=appLabel(pkg);
        String name=clean(title,label);
        if(name!=null)return name;
        name=clean(body,label);
        if(name!=null)return name;
        if(alt!=null)for(String piece:alt.split("\u0001")){
            String candidate=clean(piece,label);
            if(candidate!=null)return candidate;
        }
        return label;
    }

    /**
     * Picks the stored notification that most likely belongs to this call: a call/invite wording
     * first, then the newest notification of that package (the peer of a call is usually the
     * contact the user was just chatting with).
     */
    private EntryStore.Entry<Intent> bestEntry(String pkg){
        List<EntryStore.Entry<Intent>> entries;
        try{entries=host.store.entriesFor(pkg);}catch(Throwable t){return null;}
        EntryStore.Entry<Intent> fallback=null;
        for(EntryStore.Entry<Intent> entry:entries){
            if(fallback==null||entry.generation>fallback.generation)fallback=entry;
            if(entry.payload.getIntExtra("call",0)==1)return entry;
            String joined=text(entry);
            if(joined.contains("通话")||joined.contains("邀请")||joined.contains("来电")||joined.contains("发起"))
                if(callerName(entry,pkg).length()>1&&!callerName(entry,pkg).equals(appLabel(pkg)))return entry;
        }
        return fallback;
    }

    /** The call notification still says "waiting for the peer", so the call is not up yet. */
    private boolean ringingNotice(String pkg,long since){
        try{
            for(EntryStore.Entry<Intent> entry:host.store.entriesFor(pkg)){
                if(entry.payload.getIntExtra("call",0)==0||entry.receivedAt<since)continue;
                String text=text(entry);
                if(text.contains("等待对方")||text.contains("正在呼叫")||text.contains("邀请对方")
                        ||text.contains("等待接听")||text.contains("正在等待")||text.contains("邀请你")
                        ||text.contains("请求与你")||text.contains("向你发起"))return true;
            }
        }catch(Throwable ignored){}
        return false;
    }
    private boolean answeredNoticeAny(String pkg,long since){
        try{
            for(EntryStore.Entry<Intent> entry:host.store.entriesFor(pkg))
                if(entry.payload.getIntExtra("call",0)!=0&&entry.receivedAt>=since&&answeredNotice(entry))return true;
        }catch(Throwable ignored){}
        return false;
    }
    private boolean answeredNotice(EntryStore.Entry<Intent> entry){
        String text=text(entry);
        if(text.contains("等待对方")||text.contains("正在呼叫")||text.contains("邀请")||text.contains("正在等待"))
            return false;
        return text.contains("通话中")||text.contains("已接通")||text.contains("正在语音通话")
                ||text.contains("正在视频通话");
    }
    private String text(EntryStore.Entry<Intent> entry){
        if(entry==null)return "";
        String title=entry.payload.getStringExtra("title"),body=entry.payload.getStringExtra("body"),
                alt=entry.payload.getStringExtra("alt");
        return (title==null?"":title)+(body==null?"":body)+(alt==null?"":alt);
    }

    private android.graphics.Bitmap avatarOf(EntryStore.Entry<Intent> entry){
        if(entry==null||!host.prefs.getBoolean("call_contact_avatar",true))return null;
        return SenderAvatar.decode(entry.payload.getByteArrayExtra(SenderAvatar.EXTRA));
    }

    private String callerName(EntryStore.Entry<Intent> entry,String pkg){
        String label=appLabel(pkg);
        if(entry==null)return label;
        String title=clean(entry.payload.getStringExtra("title"),label);
        if(title!=null)return title;
        String body=clean(entry.payload.getStringExtra("body"),label);
        if(body!=null)return body;
        String alt=entry.payload.getStringExtra("alt");
        if(alt!=null)for(String piece:alt.split("\u0001")){
            String name=clean(piece,label);
            if(name!=null)return name;
        }
        return label;
    }
    private static final String[] BOILER={"语音通话中","视频通话中","正在通话中","正在语音通话","正在视频通话",
            "邀请你语音通话","邀请你视频通话","邀请你通话","向您发起通话","发起通话","的语音通话","的视频通话",
            "语音通话","视频通话","通话中","通话","来电","正在等待对方接受邀请","正在等待对方接听","正在等待",
            "正在呼叫我","正在呼叫你","等待对方接听","正在呼叫"};
    private String clean(String raw,String label){
        if(raw==null)return null;
        String text=raw.trim();
        if(text.isEmpty())return null;
        if(text.equals(label))return null;
        for(String token:BOILER)text=text.replace(token,"");
        text=text.trim();
        while(!text.isEmpty()&&"与和·-—:：()/,".contains(text.substring(0,1)))text=text.substring(1).trim();
        while(!text.isEmpty()&&"与和·-—:：()/,".contains(text.substring(text.length()-1)))text=text.substring(0,text.length()-1).trim();
        if(text.isEmpty()||text.equals(label)||text.equals("微信")||text.equals("QQ"))return null;
        return text.length()>18?text.substring(0,18):text;
    }
    private String appLabel(String pkg){
        try{
            PackageManager pm=host.context().getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg,0)));
        }catch(Throwable ignored){return pkg.equals("com.tencent.mobileqq")?"QQ":"微信";}
    }
    private boolean enabled(){
        return host.prefs.getBoolean("enabled",true)&&host.prefs.getBoolean("call_island",true);
    }

    /** Package currently owning a voice-communication audio client, or null. */
    /** Usages and sources currently open for the accepted call packages, for device triage. */
    private String playbackSummary(){
        StringBuilder out=new StringBuilder();
        try{
            java.util.List<AudioPlaybackConfiguration> playback=audio.getActivePlaybackConfigurations();
            if(playback!=null)for(AudioPlaybackConfiguration config:playback){
                AudioAttributes attributes=config.getAudioAttributes();
                if(attributes==null)continue;
                int usage=attributes.getUsage();
                if(usage!=AudioAttributes.USAGE_VOICE_COMMUNICATION
                        &&usage!=AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)continue;
                String pkg=ownerPackage(config);
                if(accepted(pkg))out.append(" play:").append(pkg).append('/').append(usage);
            }
        }catch(Throwable ignored){}
        try{
            java.util.List<AudioRecordingConfiguration> recording=audio.getActiveRecordingConfigurations();
            if(recording!=null)for(AudioRecordingConfiguration config:recording){
                String pkg=ownerPackage(config);
                if(accepted(pkg))out.append(" rec:").append(pkg).append('/').append(config.getAudioSource());
            }
        }catch(Throwable ignored){}
        return out.length()==0?"none":out.toString();
    }

    private String detect(){
        if(audio==null)return null;
        // Rings, dial tones and connected audio share this family of usages. Plain
        // VOICE_COMMUNICATION (and the app's own mic capture) only exists once the call is
        // answered; SIGNALLING alone is the ringing/dialling phase.
        micNow=false;connectedNow=false;
        String signalling=null,dialling=null,timed=null;
        List<AudioPlaybackConfiguration> playback=null;
        try{playback=audio.getActivePlaybackConfigurations();}catch(SecurityException ignored){}
        if(playback!=null)for(AudioPlaybackConfiguration config:playback){
            AudioAttributes attributes=config.getAudioAttributes();
            if(attributes==null)continue;
            int usage=attributes.getUsage();
            if(usage!=AudioAttributes.USAGE_VOICE_COMMUNICATION
                    &&usage!=AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)continue;
            String pkg=ownerPackage(config);
            if(!accepted(pkg))continue;
            // QQ keeps a VOICE_COMMUNICATION playback client from the moment the call is dialled
            // (ringback), so playback alone can never prove the peer picked up.
            if(TIMED.contains(pkg)){if(timed==null)timed=pkg;}
            else if(dialling==null)dialling=pkg;
        }
        List<AudioRecordingConfiguration> recording=null;
        try{recording=audio.getActiveRecordingConfigurations();}catch(SecurityException ignored){}
        if(recording!=null)for(AudioRecordingConfiguration config:recording){
            int source=config.getAudioSource();
            // Voice calls record through VOICE_COMMUNICATION; a plain MIC session is a
            // voice message or a recorder and must not raise the call island.
            if(source!=MediaRecorder.AudioSource.VOICE_COMMUNICATION)continue;
            String pkg=ownerPackage(config);
            if(accepted(pkg)){micNow=true;connectedNow=true;return pkg;}
        }
        if(timed!=null)return timed;
        return dialling!=null?dialling:signalling;
    }

    /**
     * AudioPlaybackConfiguration exposes the owner only through hidden getters, and AudioRecording
     * Configuration has none at all, so read them reflectively and fall back to the process name.
     */
    private String ownerPackage(Object config){
        try{
            java.lang.reflect.Method uid=config.getClass().getMethod("getClientUid");
            String pkg=packageOf(((Number)uid.invoke(config)).intValue());
            if(pkg!=null)return pkg;
        }catch(Throwable ignored){}
        try{
            java.lang.reflect.Method pid=config.getClass().getMethod("getClientPid");
            String name=processName(((Number)pid.invoke(config)).intValue());
            if(name==null)return null;
            for(String candidate:PACKAGES)if(name.startsWith(candidate))return candidate;
            // The process name of an app's audio client is its package, optionally with a
            // ":remote" suffix. That is enough to name any third-party call app.
            int colon=name.indexOf(':');
            String pkg=colon>0?name.substring(0,colon):name;
            if(accepted(pkg))return pkg;
        }catch(Throwable ignored){}
        return null;
    }
    /**
     * Any app that opens a voice-communication client may raise its own call island, unless it is
     * the OEM dialer (which already has one) or the user limited the island to WeChat/QQ.
     */
    private boolean accepted(String pkg){
        if(pkg==null||pkg.isEmpty())return false;
        if(NATIVE_CALL.contains(pkg))return false;
        if(pkg.contains("incallui")||pkg.contains(".dialer")||pkg.contains("telecom"))return false;
        if(host.prefs.getBoolean("call_island_all",true))return true;
        return PACKAGES.contains(pkg);
    }
    private String processName(int pid){
        try{
            String cmd=java.nio.file.Files.readString(java.nio.file.Paths.get("/proc/"+pid+"/cmdline")).trim();
            return cmd.isEmpty()?null:cmd;
        }catch(Throwable ignored){return null;}
    }

    private String packageOf(int uid){
        try{
            String[] packages=host.context().getPackageManager().getPackagesForUid(uid);
            if(packages==null)return null;
            for(String name:packages)if(PACKAGES.contains(name))return name;
            for(String name:packages)if(accepted(name))return name;
        }catch(Throwable ignored){}
        return null;
    }
}
