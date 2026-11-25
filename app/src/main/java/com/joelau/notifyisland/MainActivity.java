package com.joelau.notifyisland;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import io.github.libxposed.service.XposedService;

public final class MainActivity extends Activity implements App.Listener {
    private SharedPreferences prefs;
    private LinearLayout content,timer;
    private TextView status,sourceStatus,islandStatus,selectedLabel,timeLabel,enabledTitle;
    private View moduleDot,sourceDot,islandDot;
    private SeekBar time;
    private final MessageDisplay[] modes={MessageDisplay.CONTENT,MessageDisplay.AVATAR,MessageDisplay.SENDER_ONLY,MessageDisplay.APP_ONLY};
    private TextView[] modeChips;
    private SeekBar delay;
    private TextView delayLabel;
    private LinearLayout senderAvatarOptions,appAvatarOptions;
    private boolean updatingDisplay;
    private XposedService service;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private String source="等待系统钩子响应",island="等待系统钩子响应";
    private boolean visible,dark,savingCoffee;
    private int background,surface,foreground,secondary,border,accent,tint;
    private long sourceReplyAt,islandReplyAt;
    private final Runnable poll=this::ping;
    private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        String role=i.getStringExtra("role"),detail=i.getStringExtra("detail");
        if(detail==null)return;
        if("source".equals(role)){source=detail;sourceReplyAt=SystemClock.elapsedRealtime();}
        if("island".equals(role)){island=detail;islandReplyAt=SystemClock.elapsedRealtime();}updateStatus();
    }};
    @Override public void onCreate(Bundle b){
        requestWindowFeature(Window.FEATURE_NO_TITLE);super.onCreate(b);if(getActionBar()!=null)getActionBar().hide();
        getWindow().setDecorFitsSystemWindows(false);prefs=getSharedPreferences("local_settings",0);build();
    }
    @Override public void onStart(){
        super.onStart();visible=true;source="正在检测通知采集…";island="正在检测原子岛服务…";sourceReplyAt=0;islandReplyAt=0;
        registerReceiver(receiver,new IntentFilter(Wire.ID+".STATUS"),Context.RECEIVER_NOT_EXPORTED);App.add(this);
    }
    @Override public void onStop(){visible=false;App.remove(this);unregisterReceiver(receiver);handler.removeCallbacksAndMessages(null);super.onStop();}
    @Override public void onDestroy(){worker.shutdownNow();super.onDestroy();}
    @Override public void changed(XposedService s){runOnUiThread(()->{
        if(!visible)return;service=s;
        if(s!=null){
            SharedPreferences remote=s.getRemotePreferences(Wire.PREFS);
            if(!remote.contains("initialized")){
                remote.edit().putBoolean("enabled",prefs.getBoolean("enabled",true)).putBoolean("show_count",prefs.getBoolean("show_count",true))
                    .putBoolean("wide",prefs.getBoolean("wide",false)).putBoolean("permanent",prefs.getBoolean("permanent",false))
                    .putBoolean(MessageDisplay.SENDER_AVATAR,prefs.getBoolean(MessageDisplay.SENDER_AVATAR,false))
                    .putBoolean(MessageDisplay.APP_AVATAR,prefs.getBoolean(MessageDisplay.APP_AVATAR,false))
                    .putString(MessageDisplay.PREF,prefs.getString(MessageDisplay.PREF,MessageDisplay.CONTENT.name()))
                    .putBoolean("call_island",prefs.getBoolean("call_island",true))
                    .putBoolean("call_contact_avatar",prefs.getBoolean("call_contact_avatar",true))
                    .putBoolean("call_island_all",prefs.getBoolean("call_island_all",true))
                    .putInt("call_settle_ms",prefs.getInt("call_settle_ms",300))
                    .putInt("show_seconds",prefs.getInt("show_seconds",8)).putStringSet("scope_packages",new HashSet<>(prefs.getStringSet("scope_packages",Wire.DEFAULTS)))
                    .putString("bridge_token",UUID.randomUUID().toString()).putBoolean("initialized",true).apply();
            }
            prefs=remote;build();
        }else{source="LSP 未连接，等待通知采集";island="LSP 未连接，等待显示服务";}
        handler.removeCallbacks(poll);if(s!=null)ping();updateStatus();
    });}

    private void build(){
        dark=(getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
        background=dark?0xff101216:0xfff5f6fa;surface=dark?0xff1c2027:Color.WHITE;foreground=dark?0xfff2f4f8:0xff1d2433;
        secondary=dark?0xffa0a9b9:0xff697386;border=dark?0xff2b313b:0xffedf0f5;accent=dark?0xffa9baff:0xff5267c9;tint=dark?0xff293249:0xffedf1ff;
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);scroll.setBackgroundColor(background);
        content=column();content.setPadding(dp(20),dp(22),dp(20),dp(28));scroll.addView(content);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});scroll.requestApplyInsets();
        WindowInsetsController controller=getWindow().getInsetsController();
        if(controller!=null)controller.setSystemBarsAppearance(dark?0:WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);

        LinearLayout heading=row();heading.setPadding(dp(2),0,dp(2),dp(20));LinearLayout titles=column();heading.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        titles.addView(text("通知上岛",29,foreground,true));TextView subtitle=text("重要消息，一眼即达",13,secondary,false);subtitle.setPadding(0,dp(6),0,0);titles.addView(subtitle);
        FrameLayout logo=new FrameLayout(this);logo.setBackground(shape(tint,18,0));ImageView mark=new ImageView(this);mark.setImageResource(R.drawable.ic_launcher);mark.setClipToOutline(true);mark.setBackground(shape(tint,12,0));
        logo.addView(mark,new FrameLayout.LayoutParams(dp(36),dp(36),Gravity.CENTER));heading.addView(logo,new LinearLayout.LayoutParams(dp(56),dp(56)));content.addView(heading);

        LinearLayout enable=panel();enable.setBackground(shape(tint,24,0));LinearLayout enableRow=row();enableRow.setPadding(dp(18),dp(20),dp(18),dp(20));LinearLayout enableText=column();
        enabledTitle=text("",18,foreground,true);enableText.addView(enabledTitle);TextView enableNote=text("让选中应用的消息显示在原子岛",12,secondary,false);enableNote.setPadding(0,dp(6),dp(12),0);enableText.addView(enableNote);
        enableRow.addView(enableText,new LinearLayout.LayoutParams(0,-2,1));Switch master=toggle("启用通知上岛",prefs.getBoolean("enabled",true));enableRow.addView(master);
        enabledTitle.setText(master.isChecked()?"通知上岛已开启":"通知上岛已关闭");master.setOnCheckedChangeListener((v,checked)->{prefs.edit().putBoolean("enabled",checked).apply();enabledTitle.setText(checked?"通知上岛已开启":"通知上岛已关闭");reload();});
        enable.addView(enableRow);enableRow.setOnClickListener(v->master.toggle());

        LinearLayout stateHeading=section("运行状态");TextView refresh=text("刷新",12,accent,true);refresh.setMinHeight(dp(48));refresh.setGravity(Gravity.CENTER);refresh.setPadding(dp(12),0,dp(2),0);refresh.setBackground(ripple(Color.TRANSPARENT,10));
        refresh.setOnClickListener(v->{if(service!=null){source="正在检测通知采集…";island="正在检测原子岛服务…";updateStatus();ping();}else Toast.makeText(this,"请先在 LSPosed 中启用模块并重启",Toast.LENGTH_SHORT).show();});stateHeading.addView(refresh);
        LinearLayout states=panel();moduleDot=new View(this);sourceDot=new View(this);islandDot=new View(this);
        status=statusRow(states,"模块",moduleDot);divider(states,18);sourceStatus=statusRow(states,"采集",sourceDot);divider(states,18);islandStatus=statusRow(states,"显示",islandDot);

        section("消息岛 · 内容","决定胶囊里显示什么、显示多少");

        LinearLayout display=panel();
        LinearLayout apps=settingRow(display,"apps","选择应用",null);
        LinearLayout appLines=(LinearLayout)apps.getChildAt(1);selectedLabel=text("",12,secondary,false);selectedLabel.setPadding(0,dp(5),0,0);appLines.addView(selectedLabel);updateSelected();
        apps.addView(glyphView("chevron",secondary,18));apps.setBackground(ripple(Color.TRANSPARENT,22));apps.setOnClickListener(v->selectApps());divider(display,64);

        LinearLayout modeBlock=column();modeBlock.setPadding(dp(18),dp(16),dp(18),dp(14));display.addView(modeBlock);
        LinearLayout modeHead=column();modeHead.addView(text("显示样式",15,foreground,true));
        TextView modeNote=text("四种样式互斥，同一时刻只生效一种",12,secondary,false);modeNote.setPadding(0,dp(5),0,0);modeHead.addView(modeNote);modeBlock.addView(modeHead);
        LinearLayout chips=row();chips.setPadding(0,dp(12),0,0);modeChips=new TextView[modes.length];
        for(int i=0;i<modes.length;i++){
            TextView chip=text(modes[i].label(),13,secondary,true);chip.setGravity(Gravity.CENTER);chip.setPadding(dp(2),dp(10),dp(2),dp(10));chip.setMinHeight(dp(40));
            chip.setBackground(ripple(Color.TRANSPARENT,14));chip.setContentDescription("显示样式："+modes[i].label());
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(i>0)lp.leftMargin=dp(8);chips.addView(chip,lp);modeChips[i]=chip;
            final MessageDisplay target=modes[i];chip.setOnClickListener(v->setDisplayMode(target));
        }
        modeBlock.addView(chips);
        senderAvatarOptions=column();senderAvatarOptions.setPadding(dp(30),0,0,0);display.addView(senderAvatarOptions);
        addSwitch(senderAvatarOptions,"avatar",MessageDisplay.SENDER_AVATAR,"发送人头像","左侧显示发送人头像，无头像时退回应用图标；右侧仍是发送人名称和未读数量",false);
        appAvatarOptions=column();appAvatarOptions.setPadding(dp(30),0,0,0);display.addView(appAvatarOptions);
        addSwitch(appAvatarOptions,"avatar",MessageDisplay.APP_AVATAR,"应用图标换成头像","左半的发送人头像优先于应用图标；右侧仍是应用名称和未读数量",false);
        setDisplayMode(displayMode());
        divider(display,64);
        addSwitch(display,"count","show_count","未读数量","在消息岛右侧显示消息数量",true);divider(display,64);
        addSwitch(display,"wide","wide","扩展显示","使用固定的扩展胶囊尺寸，关闭后按内容自动适应；超出可用宽度的文字滚动显示",false);

        section("通话岛 · 实时状态","微信、QQ 等第三方语音通话的岛内显示");
        LinearLayout callPanel=panel();
        addSwitch(callPanel,"call","call_island","通话上岛","语音或视频通话时，左侧显示联系人与头像、右侧实时显示通话时长，通话结束自动撤岛",true);divider(callPanel,64);
        addSwitch(callPanel,"avatar","call_contact_avatar","显示联系人头像","优先使用通话通知带出的联系人头像，取不到时退回应用图标；关闭后始终显示应用图标",true);divider(callPanel,64);
        addSwitch(callPanel,"call","call_island_all","所有应用通话上岛","除系统电话（已有官方通话岛）外，任何占用语音通话通道的应用都会上岛；关闭后仅微信、QQ、TIM 上岛",true);divider(callPanel,18);
        LinearLayout delayBlock=column();delayBlock.setPadding(dp(18),dp(18),dp(18),dp(17));callPanel.addView(delayBlock);
        LinearLayout delayHead=row();delayHead.addView(text("计时延迟",15,foreground,true),new LinearLayout.LayoutParams(0,-2,1));
        delayLabel=text("",13,accent,true);delayLabel.setPadding(dp(11),dp(5),dp(11),dp(5));delayLabel.setBackground(shape(tint,10,0));delayHead.addView(delayLabel);delayBlock.addView(delayHead);
        TextView delayNote=text("对方接听后延迟多久才开始计时。通话应用自己的计时通常晚于接听动作，调大这个值可让两端显示对齐",12,secondary,false);
        delayNote.setLineSpacing(dp(2),1);delayNote.setPadding(0,dp(6),0,0);delayBlock.addView(delayNote);
        delay=new SeekBar(this);delay.setMax(16);delay.setProgress(Math.max(0,Math.min(16,prefs.getInt("call_settle_ms",300)/50)));delay.setProgressTintList(ColorStateList.valueOf(accent));delay.setThumbTintList(ColorStateList.valueOf(accent));delay.setProgressBackgroundTintList(ColorStateList.valueOf(border));delay.setContentDescription("通话计时延迟，0 至 800 毫秒");
        LinearLayout.LayoutParams delayParams=new LinearLayout.LayoutParams(-1,dp(48));delayParams.topMargin=dp(6);delayBlock.addView(delay,delayParams);
        delayLabel.setText(getString(R.string.millis_value,delay.getProgress()*50));
        LinearLayout delayEnds=row();delayEnds.addView(text("0 毫秒",11,secondary,false),new LinearLayout.LayoutParams(0,-2,1));delayEnds.addView(text("800 毫秒",11,secondary,false));delayBlock.addView(delayEnds);
        delay.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int n,boolean user){delayLabel.setText(getString(R.string.millis_value,n*50));}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){prefs.edit().putInt("call_settle_ms",s.getProgress()*50).apply();reload();}});

        section("停留与消失","消息岛出现之后怎么收起");
        LinearLayout duration=panel();
        addSwitch(duration,"pin","permanent","消息常驻","保留至点击、关闭或原通知移除",false);divider(duration,18);
        timer=column();timer.setPadding(dp(18),dp(18),dp(18),dp(17));duration.addView(timer);LinearLayout timeHeading=row();timeHeading.addView(text("停留时间",15,foreground,true),new LinearLayout.LayoutParams(0,-2,1));
        timeLabel=text("",13,accent,true);timeLabel.setPadding(dp(11),dp(5),dp(11),dp(5));timeLabel.setBackground(shape(tint,10,0));timeHeading.addView(timeLabel);timer.addView(timeHeading);
        time=new SeekBar(this);time.setMax(27);time.setProgress(Math.max(0,Math.min(27,prefs.getInt("show_seconds",8)-3)));time.setProgressTintList(ColorStateList.valueOf(accent));time.setThumbTintList(ColorStateList.valueOf(accent));time.setProgressBackgroundTintList(ColorStateList.valueOf(border));time.setContentDescription("消息停留时间，3 至 30 秒");
        LinearLayout.LayoutParams sliderParams=new LinearLayout.LayoutParams(-1,dp(48));sliderParams.topMargin=dp(6);timer.addView(time,sliderParams);timeLabel.setText(getString(R.string.seconds_value,time.getProgress()+3));
        LinearLayout ends=row();ends.addView(text("3 秒",11,secondary,false),new LinearLayout.LayoutParams(0,-2,1));ends.addView(text("30 秒",11,secondary,false));timer.addView(ends);updateTimer();
        time.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int n,boolean user){timeLabel.setText(getString(R.string.seconds_value,n+3));}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){prefs.edit().putInt("show_seconds",s.getProgress()+3).apply();reload();}});

        LinearLayout privacy=row();privacy.setGravity(Gravity.TOP);privacy.setPadding(dp(6),dp(22),dp(6),dp(17));privacy.addView(glyphView("shield",secondary,16));
        LinearLayout notes=column();
        notes.addView(text("仅在本机处理通知",12,secondary,true));
        TextView setup=text("首次启用\n在LSPosed勾选系统界面与系统界面组件，重启手机。",11,secondary,false);setup.setLineSpacing(dp(4),1);setup.setPadding(0,dp(10),0,0);notes.addView(setup);
        TextView details=text("查看消息详情\n长按消息岛，展开消息详情卡片；下拉卡片，以小窗打开应用。",11,secondary,false);details.setLineSpacing(dp(4),1);details.setPadding(0,dp(10),0,0);notes.addView(details);
        LinearLayout.LayoutParams noteParams=new LinearLayout.LayoutParams(0,-2,1);noteParams.leftMargin=dp(9);privacy.addView(notes,noteParams);content.addView(privacy);
        Button support=new Button(this);support.setText("支持我一下，请我喝杯咖啡吧~");support.setTextSize(14);support.setTextColor(accent);support.setTypeface(null,Typeface.BOLD);support.setAllCaps(false);support.setStateListAnimator(null);support.setGravity(Gravity.CENTER);support.setMinHeight(dp(58));support.setPadding(dp(14),dp(12),dp(14),dp(12));support.setBackground(ripple(tint,20));
        support.setCompoundDrawablesRelativeWithIntrinsicBounds(new UiGlyph("coffee",accent,dp(22)),null,null,null);support.setCompoundDrawablePadding(dp(8));support.setOnClickListener(v->showCoffee());content.addView(support,new LinearLayout.LayoutParams(-1,-2));updateStatus();
    }
    /** Preferences are stored in the module process; ask it to repaint the current pill. */
    private void reload(){
        try{sendBroadcast(new Intent(Wire.RELOAD).setPackage(Wire.SYSUI).putExtra("op","reload"));}catch(Throwable ignored){}
    }
    private void updateTimer(){if(time!=null){boolean enabled=!prefs.getBoolean("permanent",false);time.setEnabled(enabled);timer.setAlpha(enabled?1:.42f);}}
    private MessageDisplay displayMode(){return MessageDisplay.parse(prefs.getString(MessageDisplay.PREF,MessageDisplay.CONTENT.name()));}
    private void setDisplayMode(MessageDisplay mode){
        if(updatingDisplay)return;
        if(!mode.name().equals(prefs.getString(MessageDisplay.PREF,MessageDisplay.CONTENT.name())))
            prefs.edit().putString(MessageDisplay.PREF,mode.name()).apply();
        updatingDisplay=true;
        try{
            if(modeChips!=null)for(int i=0;i<modeChips.length;i++){
                boolean on=modes[i]==mode;TextView chip=modeChips[i];
                chip.setTextColor(on?accent:secondary);
                chip.setBackground(on?shape(tint,14,accent):shape(background,14,border));
            }
            if(appAvatarOptions!=null)appAvatarOptions.setVisibility(mode==MessageDisplay.APP_ONLY?View.VISIBLE:View.GONE);
            if(senderAvatarOptions!=null)senderAvatarOptions.setVisibility(mode==MessageDisplay.SENDER_ONLY?View.VISIBLE:View.GONE);
        }finally{updatingDisplay=false;}
    }
    private void updateStatus(){
        if(status==null)return;status.setText(service==null?"LSP 未连接":"LSP 已连接");sourceStatus.setText(source);islandStatus.setText(island);
        moduleDot.setBackground(shape(service==null?secondary:0xff46ad88,5,0));sourceDot.setBackground(shape(source.contains("已连接")?0xff46ad88:secondary,5,0));islandDot.setBackground(shape(island.contains("已连接")?0xff46ad88:secondary,5,0));
    }
    private void ping(){
        if(service==null||!visible)return;handler.removeCallbacks(poll);Intent base=new Intent(Wire.ID+".STATUS").setPackage(getPackageName());PendingIntent reply=PendingIntent.getBroadcast(this,47,base,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);
        String token=prefs.getString("bridge_token","");long sentAt=SystemClock.elapsedRealtime();for(String pkg:new String[]{Wire.SYSUI,Wire.PLUGIN})sendBroadcast(new Intent(Wire.PING).setPackage(pkg).addFlags(Intent.FLAG_RECEIVER_FOREGROUND).putExtra("token",token).putExtra("reply",reply));
        handler.postDelayed(()->{if(!visible||service==null)return;if(sourceReplyAt<sentAt)source="通知采集暂未响应，请检查系统界面作用域";if(islandReplyAt<sentAt)island="原子岛服务暂未响应，可能尚未启动或已空闲退出";updateStatus();},4000);handler.postDelayed(poll,5000);
    }
    private LinearLayout section(String title){
        LinearLayout line=column();line.setPadding(dp(4),dp(20),dp(4),dp(9));line.addView(text(title,13,foreground,true));content.addView(line);return line;
    }
    private LinearLayout section(String title,String caption){
        LinearLayout line=column();line.setPadding(dp(4),dp(20),dp(4),dp(9));line.addView(text(title,13,foreground,true));
        TextView note=text(caption,11,secondary,false);note.setPadding(0,dp(4),0,0);line.addView(note);content.addView(line);return line;
    }
    private LinearLayout panel(){LinearLayout panel=column();panel.setBackground(shape(surface,24,0));content.addView(panel,new LinearLayout.LayoutParams(-1,-2));return panel;}
    private TextView statusRow(LinearLayout parent,String label,View dot){
        LinearLayout row=row();row.setPadding(dp(18),dp(13),dp(18),dp(13));row.addView(dot,new LinearLayout.LayoutParams(dp(7),dp(7)));TextView title=text(label,12,secondary,false);title.setPadding(dp(10),0,dp(14),0);row.addView(title);
        TextView value=text("",12,foreground,false);value.setGravity(Gravity.END);row.addView(value,new LinearLayout.LayoutParams(0,-2,1));parent.addView(row);return value;
    }
    private LinearLayout settingRow(LinearLayout parent,String glyph,String title,String detail){
        LinearLayout row=row();row.setPadding(dp(18),dp(17),dp(16),dp(17));row.setMinimumHeight(dp(78));FrameLayout icon=new FrameLayout(this);icon.setBackground(shape(tint,12,0));icon.addView(glyphView(glyph,accent,21),new FrameLayout.LayoutParams(dp(21),dp(21),Gravity.CENTER));row.addView(icon,new LinearLayout.LayoutParams(dp(34),dp(34)));
        LinearLayout lines=column();lines.setPadding(dp(12),0,dp(12),0);row.addView(lines,new LinearLayout.LayoutParams(0,-2,1));lines.addView(text(title,15,foreground,true));
        if(detail!=null){TextView description=text(detail,12,secondary,false);description.setPadding(0,dp(5),0,0);description.setLineSpacing(dp(2),1);lines.addView(description);}parent.addView(row);return row;
    }
    private void addSwitch(LinearLayout parent,String glyph,String key,String title,String detail,boolean def){
        LinearLayout row=settingRow(parent,glyph,title,detail);Switch control=toggle(title,prefs.getBoolean(key,def));row.addView(control);control.setOnCheckedChangeListener((v,checked)->{prefs.edit().putBoolean(key,checked).apply();if(key.equals("permanent"))updateTimer();reload();});row.setOnClickListener(v->control.toggle());
    }
    private Switch toggle(String description,boolean checked){
        Switch control=new Switch(this);control.setChecked(checked);control.setContentDescription(description);control.setMinHeight(dp(48));control.setMinWidth(dp(48));
        // Keep the original platform drawable dimensions and insets from 0.1.12. Only
        // tint changes; replacing intrinsic track/thumb sizes enlarges Android Switch.
        control.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{Color.WHITE,dark?0xffb6becb:Color.WHITE}));
        control.setTrackTintMode(PorterDuff.Mode.SRC_IN);
        control.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{dark?0xff765be5:0xff6044cf,dark?0xff3d4654:0xffc9d0de}));return control;
    }
    private void divider(LinearLayout parent,int inset){View line=new View(this);line.setBackgroundColor(border);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.leftMargin=dp(inset);lp.rightMargin=dp(18);parent.addView(line,lp);}
    private void updateSelected(){if(selectedLabel!=null)selectedLabel.setText(getString(R.string.selected_apps,prefs.getStringSet("scope_packages",Wire.DEFAULTS).size()));}

    private void selectApps(){
        LinearLayout box=column();box.setPadding(dp(22),dp(8),dp(22),0);EditText search=new EditText(this);search.setSingleLine();search.setTextSize(14);search.setTextColor(foreground);search.setHintTextColor(secondary);search.setHint("搜索应用名称或包名");search.setPadding(dp(14),0,dp(14),0);search.setBackground(shape(background,14,0));box.addView(search,new LinearLayout.LayoutParams(-1,dp(48)));
        LinearLayout actions=row();actions.setPadding(0,dp(6),0,0);TextView resultCount=text("",11,secondary,false);actions.addView(resultCount,new LinearLayout.LayoutParams(0,-2,1));
        Button selectAll=selectionButton("全选"),inverse=selectionButton("反选");selectAll.setEnabled(false);inverse.setEnabled(false);actions.addView(selectAll);actions.addView(inverse);box.addView(actions);
        TextView loading=text("正在读取应用列表…",13,secondary,false);loading.setPadding(0,dp(12),0,0);box.addView(loading);ListView list=new ListView(this);list.setDivider(null);list.setClipToPadding(false);list.setPadding(0,dp(8),0,0);
        int listHeight=Math.max(150,Math.min(390,Math.round(getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density)-350));box.addView(list,new LinearLayout.LayoutParams(-1,dp(listHeight)));
        final AppSelection.Draft[] draft={null};
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("选择应用").setView(box)
            .setNegativeButton("取消",null).setPositiveButton("完成",(d,which)->{
                if(draft[0]!=null){prefs.edit().putStringSet("scope_packages",draft[0].snapshot()).apply();updateSelected();reload();}
            }).create();
        dialog.setCanceledOnTouchOutside(true);dialog.setOnDismissListener(d->{if(draft[0]!=null)draft[0].discard();});
        dialog.show();styleDialog(dialog);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        worker.execute(()->{
            PackageManager pm=getPackageManager();Set<String> launchers=new HashSet<>();
            for(ResolveInfo entry:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0))if(entry.activityInfo!=null)launchers.add(entry.activityInfo.packageName);
            ArrayList<ApplicationInfo> all=new ArrayList<>();Set<String> eligible=new HashSet<>();
            for(ApplicationInfo a:pm.getInstalledApplications(0))if(AppSelection.include((a.flags&ApplicationInfo.FLAG_SYSTEM)!=0,(a.flags&ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)!=0,launchers.contains(a.packageName),a.enabled)){
                all.add(a);eligible.add(a.packageName);
            }
            Set<String> selected=new HashSet<>();Map<String,String> names=new HashMap<>();for(ApplicationInfo a:all)names.put(a.packageName,a.loadLabel(pm).toString());
            Comparator<ApplicationInfo> order=Comparator.<ApplicationInfo,Boolean>comparing(a->!selected.contains(a.packageName)).thenComparing(a->names.get(a.packageName),java.text.Collator.getInstance());all.sort(order);
            runOnUiThread(()->{
                if(isDestroyed()||!dialog.isShowing())return;loading.setVisibility(View.GONE);
                draft[0]=new AppSelection.Draft(prefs.getStringSet("scope_packages",Wire.DEFAULTS),eligible);
                selected.addAll(draft[0].snapshot());dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                all.sort(order);ArrayList<ApplicationInfo> shown=new ArrayList<>(all);
                BaseAdapter adapter=new BaseAdapter(){
                    public int getCount(){return shown.size();}public Object getItem(int p){return shown.get(p);}public long getItemId(int p){return p;}
                    public View getView(int p,View old,ViewGroup parent){
                        ApplicationInfo a=shown.get(p);LinearLayout row=row();row.setPadding(0,dp(10),0,dp(10));ImageView icon=new ImageView(MainActivity.this);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        try{icon.setImageDrawable(DesktopIcons.load(MainActivity.this,a.packageName));}catch(PackageManager.NameNotFoundException ignored){icon.setImageDrawable(a.loadIcon(getPackageManager()));}row.addView(icon,new LinearLayout.LayoutParams(dp(38),dp(38)));
                        LinearLayout lines=column();lines.setPadding(dp(12),0,dp(8),0);row.addView(lines,new LinearLayout.LayoutParams(0,-2,1));TextView name=text(names.get(a.packageName),14,foreground,true);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);lines.addView(name);
                        TextView pkg=text(a.packageName,10,secondary,false);pkg.setSingleLine();pkg.setEllipsize(TextUtils.TruncateAt.MIDDLE);pkg.setPadding(0,dp(4),0,0);lines.addView(pkg);CheckBox cb=new CheckBox(MainActivity.this);cb.setButtonTintList(ColorStateList.valueOf(accent));cb.setChecked(selected.contains(a.packageName));cb.setClickable(false);cb.setFocusable(false);row.addView(cb);return row;
                    }
                };list.setAdapter(adapter);
                // Sort once from saved choices above; editing changes checks,
                // never row order. A completed selection is sorted on reopening.
                Runnable refreshList=()->{adapter.notifyDataSetChanged();resultCount.setText(getString(R.string.app_search_results,shown.size()));selectAll.setEnabled(!shown.isEmpty());inverse.setEnabled(!shown.isEmpty());};
                java.util.function.Consumer<Boolean> bulkEdit=flip->{
                    ArrayList<String> keys=new ArrayList<>();for(ApplicationInfo a:shown)keys.add(a.packageName);
                    draft[0].bulk(keys,flip);selected.clear();selected.addAll(draft[0].snapshot());refreshList.run();
                };
                selectAll.setOnClickListener(v->bulkEdit.accept(false));inverse.setOnClickListener(v->bulkEdit.accept(true));refreshList.run();
                list.setOnItemClickListener((p,v,pos,id)->{
                    String pkg=shown.get(pos).packageName;draft[0].bulk(Collections.singletonList(pkg),true);
                    selected.clear();selected.addAll(draft[0].snapshot());refreshList.run();
                });
                search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int af){}public void onTextChanged(CharSequence s,int st,int before,int count){String q=s.toString().trim().toLowerCase(Locale.ROOT);shown.clear();for(ApplicationInfo a:all)if(names.get(a.packageName).toLowerCase(Locale.ROOT).contains(q)||a.packageName.toLowerCase(Locale.ROOT).contains(q))shown.add(a);refreshList.run();}public void afterTextChanged(Editable e){}});
                if(search.length()>0){String query=search.getText().toString();search.setText("");search.setText(query);search.setSelection(query.length());}
            });
        });
    }
    private Button selectionButton(String title){Button b=new Button(this);b.setText(title);b.setTextSize(12);b.setTextColor(new ColorStateList(new int[][]{new int[]{android.R.attr.state_enabled},new int[]{}},new int[]{accent,secondary}));b.setAllCaps(false);b.setMinWidth(dp(56));b.setMinimumWidth(dp(56));b.setMinHeight(dp(48));b.setPadding(dp(10),0,dp(10),0);b.setBackground(ripple(Color.TRANSPARENT,12));b.setStateListAnimator(null);return b;}
    private void showCoffee(){
        LinearLayout box=column();box.setPadding(dp(24),dp(8),dp(24),dp(4));TextView message=text("谢谢你，让通知上岛继续变好。",13,secondary,false);message.setGravity(Gravity.CENTER);message.setPadding(0,0,0,dp(18));box.addView(message);
        ImageView qr=new ImageView(this);qr.setImageResource(R.drawable.coffee_qr);qr.setAdjustViewBounds(true);qr.setScaleType(ImageView.ScaleType.FIT_CENTER);qr.setContentDescription("支持开发者的收款二维码，长按保存到相册");
        qr.setOnLongClickListener(v->{saveCoffeeQr();return true;});box.addView(qr,new LinearLayout.LayoutParams(-1,-2));
        TextView hint=text("长按二维码，保存到相册",12,secondary,false);hint.setGravity(Gravity.CENTER);hint.setPadding(0,dp(14),0,0);box.addView(hint);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("感谢你的支持").setView(box).setPositiveButton("关闭",null).create();dialog.show();styleDialog(dialog);
    }
    private void saveCoffeeQr(){
        if(savingCoffee)return;savingCoffee=true;
        Context application=getApplicationContext();
        worker.execute(()->{
            String result;
            try{CoffeeQrSaver.save(application);result="二维码已保存到相册 · Pictures/通知上岛";}
            catch(Exception ex){result="保存失败，请检查手机剩余空间后重试";}
            final String message=result;
            runOnUiThread(()->{savingCoffee=false;Toast.makeText(application,message,Toast.LENGTH_LONG).show();});
        });
    }
    private void styleDialog(AlertDialog dialog){Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(shape(surface,28,0));window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(36),dp(420)),-2);}dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accent);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(secondary);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView text(String value,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setIncludeFontPadding(false);if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    private GradientDrawable shape(int color,int radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private Drawable ripple(int color,int radius){return new RippleDrawable(ColorStateList.valueOf(dark?0x20ffffff:0x125267c9),shape(color,radius,0),shape(Color.WHITE,radius,0));}
    private View glyphView(String name,int color,int size){ImageView view=new ImageView(this);view.setImageDrawable(new UiGlyph(name,color,dp(size)));view.setLayoutParams(new LinearLayout.LayoutParams(dp(size),dp(size)));view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return view;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}

    /** Small line icons drawn locally; no downloaded icon font or UI dependency. */
    private static final class UiGlyph extends Drawable {
        private final String name;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final int size;
        UiGlyph(String name,int color,int size){this.name=name;this.size=size;paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.7f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);setBounds(0,0,size,size);}
        @Override public void draw(Canvas canvas){
            Rect b=getBounds();int save=canvas.save();canvas.translate(b.left,b.top);canvas.scale(b.width()/24f,b.height()/24f);Path p=new Path();
            switch(name){
                case "apps"->{for(int y:new int[]{4,14})for(int x:new int[]{4,14})canvas.drawRoundRect(x,y,x+6,y+6,1.5f,1.5f,paint);}
                case "count"->{canvas.drawRoundRect(3,5,21,19,4,4,paint);canvas.drawLine(8,10,16,10,paint);canvas.drawLine(8,14,12,14,paint);}
                case "avatar"->{canvas.drawRoundRect(3,3,21,21,5,5,paint);canvas.drawCircle(12,9,3,paint);canvas.drawArc(6,13,18,23,200,140,false,paint);}
                case "sender"->{canvas.drawCircle(8,8,3,paint);canvas.drawArc(2,13,14,23,190,160,false,paint);canvas.drawLine(16,8,22,8,paint);canvas.drawLine(16,13,22,13,paint);canvas.drawLine(16,18,20,18,paint);}
                case "wide"->{canvas.drawRoundRect(2,6,22,18,4,4,paint);canvas.drawLine(7,12,17,12,paint);p.moveTo(9,10);p.lineTo(7,12);p.lineTo(9,14);p.moveTo(15,10);p.lineTo(17,12);p.lineTo(15,14);canvas.drawPath(p,paint);}
                case "pin"->{p.moveTo(8,3);p.lineTo(16,3);p.lineTo(15,10);p.lineTo(18,14);p.lineTo(6,14);p.lineTo(9,10);p.close();p.moveTo(12,14);p.lineTo(12,22);canvas.drawPath(p,paint);}
                case "call"->{p.moveTo(6,3);p.lineTo(10,3);p.lineTo(11,8);p.lineTo(8,9);p.quadTo(9,15,15,16);p.lineTo(16,13);p.lineTo(21,14);p.lineTo(21,18);p.quadTo(21,21,18,21);p.quadTo(7,20,3,9);p.quadTo(2,5,6,3);canvas.drawPath(p,paint);}
                case "chevron"->{p.moveTo(9,5);p.lineTo(15,12);p.lineTo(9,19);canvas.drawPath(p,paint);}
                case "shield"->{p.moveTo(12,2);p.lineTo(20,5);p.lineTo(19,14);p.quadTo(17,19,12,22);p.quadTo(7,19,5,14);p.lineTo(4,5);p.close();canvas.drawPath(p,paint);p.reset();p.moveTo(8,12);p.lineTo(11,15);p.lineTo(16,9);canvas.drawPath(p,paint);}
                case "coffee"->{p.moveTo(4,8);p.lineTo(18,8);p.lineTo(18,14);p.quadTo(18,19,13,19);p.lineTo(9,19);p.quadTo(4,19,4,14);p.close();p.moveTo(18,9);p.cubicTo(24,8,24,15,18,15);p.moveTo(3,22);p.lineTo(20,22);p.moveTo(8,2);p.lineTo(8,4);p.moveTo(13,2);p.lineTo(13,4);canvas.drawPath(p,paint);}
                default->{}
            }canvas.restoreToCount(save);
        }
        @Override public int getIntrinsicWidth(){return size;}@Override public int getIntrinsicHeight(){return size;}
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}@Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}

