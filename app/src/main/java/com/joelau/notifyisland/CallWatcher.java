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
    /** The OEM dialer already owns a native call island; a second one would duplicate it. */
    private static final Set<String> NATIVE_CALL=Set.of("android","com.android.systemui","com.android.incallui",
            "com.android.server.telecom","com.android.phone","com.android.dialer","com.google.android.dialer",
            "com.vivo.phone","com.vivo.telecom","com.vivo.incallui","com.joelau.notifyisland");
    /** Idle polling is cheap; an active call is polled fast so the answer moment is tight. */
    private static final long IDLE_MS=1200L, LIVE_MS=300L;
    private static final long HANGOVER_MS=1000L;
    private final IslandHost host;
    private final Handler main=new Handler(Looper.getMainLooper());
    private AudioManager audio;
    private String activePackage;
    private boolean connectedNow,callConnected,micNow;
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
            // The app's own call notification is the ground truth about the phase: while it
            // still asks the peer to pick up, the audio client must not start the timer even
            // if the app already grabbed the voice-communication channel for the ringback.
            boolean connected=pkg!=null&&(micNow||answeredNoticeAny(pkg))&&!ringingNotice(pkg);
            long now=SystemClock.elapsedRealtime();
            if(pkg==null){
                // Time-based instead of poll counts: the live poll is fast, so two misses would
                // end the island on a brief speaker/mute blip.
                if(activePackage!=null&&now-lastSeen>=HANGOVER_MS)end();
            }else if(!pkg.equals(activePackage)){
                begin(pkg,connected);
            }else{
                lastSeen=now;
                if(connected&&!callConnected){
                    // The peer just picked up: start counting from this poll, not from the dial.
                    callConnected=true;
                    host.module.info("Call answered; timer starts now");
                    host.island().markCallConnected();
                }
            }
        }catch(Throwable t){host.module.error("Track call state",t);}
        main.postDelayed(this::poll,activePackage==null?IDLE_MS:LIVE_MS);
    }
    private void end(){
        String pkg=activePackage;
        activePackage=null;dismissed=false;started=false;
        try{host.island().destroyCallIsland();}catch(Throwable t){host.module.error("Hide call island",t);}
        host.module.info("Call island ended for "+pkg);
    }

    private void begin(String pkg,boolean connected){
        activePackage=pkg;lastSeen=SystemClock.elapsedRealtime();dismissed=false;connectedNow=connected;callConnected=connected;
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
        try{host.island().showCallIsland(pkg,connected?SystemClock.elapsedRealtime()+settle:0L,name,avatar);}
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
     * A call notification can arrive after the pill is already up (QQ posts it when the call
     * connects). Re-read the peer from it in place instead of waiting for the next call.
     */
    void onCallEntry(String pkg){
        if(activePackage==null||!pkg.equals(activePackage))return;
        EntryStore.Entry<Intent> entry=bestEntry(pkg);
        if(entry==null)return;
        if(!callConnected&&answeredNotice(entry)&&!ringingNotice(pkg)){
            callConnected=true;
            host.module.info("Call answered per notification; timer starts now");
            try{host.island().markCallConnected();}
            catch(Throwable t){host.module.error("Start call timer",t);}
        }
        String name=callerName(entry,pkg);
        if(name.equals(appLabel(pkg)))return;
        try{host.island().updateCallIsland(pkg,name,avatarOf(entry));}
        catch(Throwable t){host.module.error("Update call island from notification",t);}
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
    private boolean ringingNotice(String pkg){
        try{
            for(EntryStore.Entry<Intent> entry:host.store.entriesFor(pkg)){
                if(entry.payload.getIntExtra("call",0)==0)continue;
                String text=text(entry);
                if(text.contains("等待对方")||text.contains("正在呼叫")||text.contains("邀请对方")
                        ||text.contains("等待接听")||text.contains("正在等待"))return true;
            }
        }catch(Throwable ignored){}
        return false;
    }
    private boolean answeredNoticeAny(String pkg){
        try{
            for(EntryStore.Entry<Intent> entry:host.store.entriesFor(pkg))
                if(entry.payload.getIntExtra("call",0)!=0&&answeredNotice(entry))return true;
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
            "语音通话","视频通话","通话中","通话","来电","正在等待对方接受邀请","正在等待对方接听","正在等待"};
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
    private String detect(){
        if(audio==null)return null;
        // Rings, dial tones and connected audio share this family of usages. Plain
        // VOICE_COMMUNICATION (and the app's own mic capture) only exists once the call is
        // answered; SIGNALLING alone is the ringing/dialling phase.
        micNow=false;connectedNow=false;
        String signalling=null,dialling=null;
        List<AudioPlaybackConfiguration> playback=null;
        try{playback=audio.getActivePlaybackConfigurations();}catch(SecurityException ignored){}
        if(playback!=null)for(AudioPlaybackConfiguration config:playback){
            AudioAttributes attributes=config.getAudioAttributes();
            if(attributes==null)continue;
            int usage=attributes.getUsage();
            if(usage!=AudioAttributes.USAGE_VOICE_COMMUNICATION&&usage!=AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)continue;
            String pkg=ownerPackage(config);
            if(!accepted(pkg))continue;
            // QQ keeps a VOICE_COMMUNICATION playback client from the moment the call is dialled
            // (ringback), so playback alone can never prove the peer picked up.
            if(usage==AudioAttributes.USAGE_VOICE_COMMUNICATION){if(dialling==null)dialling=pkg;}
            else if(signalling==null)signalling=pkg;
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
