package com.joelau.notifyisland;

import android.content.SharedPreferences;

/**
 * 显示样式拆成左右两个互不影响的选项：
 * 左侧 = 联系人头像 / 应用图标，右侧 = 联系人名字 / 消息详细内容。
 * 右侧选“消息内容”时，联系人名字自动放到左侧。
 */
final class MessageDisplay {
    static final String LEFT_PREF="left_display";
    static final String RIGHT_PREF="right_display";
    static final String LEFT_AVATAR="avatar",LEFT_ICON="icon";
    static final String RIGHT_NAME="name",RIGHT_CONTENT="content";

    /* 旧版四种互斥模式的存档键，只用于迁移和历史调用点。 */
    static final String PREF="message_display";
    static final String SENDER_AVATAR="sender_only_avatar";
    static final String APP_AVATAR="app_only_avatar";
    static final MessageDisplay CONTENT=new MessageDisplay(true,true);
    static final MessageDisplay AVATAR=new MessageDisplay(true,true);
    static final MessageDisplay SENDER_ONLY=new MessageDisplay(false,false);
    static final MessageDisplay APP_ONLY=new MessageDisplay(false,false);

    final boolean avatarLeft;   // true=联系人头像，false=应用图标
    final boolean contentRight; // true=消息详细内容，false=联系人名字

    private MessageDisplay(boolean avatarLeft,boolean contentRight){this.avatarLeft=avatarLeft;this.contentRight=contentRight;}

    static MessageDisplay read(SharedPreferences p){
        String left=p.getString(LEFT_PREF,null),right=p.getString(RIGHT_PREF,null);
        if(left==null&&right==null)return fromLegacy(p);
        return new MessageDisplay(LEFT_AVATAR.equals(left),right==null||!RIGHT_NAME.equals(right));
    }

    static MessageDisplay parse(String value){
        if(LEFT_AVATAR.equals(value)||LEFT_ICON.equals(value)||RIGHT_NAME.equals(value)||RIGHT_CONTENT.equals(value))
            return new MessageDisplay(LEFT_AVATAR.equals(value),!RIGHT_NAME.equals(value));
        if("AVATAR".equals(value))return AVATAR;
        if("SENDER_ONLY".equals(value))return SENDER_ONLY;
        if("APP_ONLY".equals(value))return APP_ONLY;
        return CONTENT;
    }

    private static MessageDisplay fromLegacy(SharedPreferences p){
        String old=p.getString(PREF,"CONTENT");
        if("AVATAR".equals(old))return new MessageDisplay(true,true);
        if("SENDER_ONLY".equals(old))return new MessageDisplay(p.getBoolean(SENDER_AVATAR,false),false);
        if("APP_ONLY".equals(old))return new MessageDisplay(p.getBoolean(APP_AVATAR,false),false);
        return new MessageDisplay(false,true);
    }

    String label(){return avatarLeft?"联系人头像":"应用图标";}

    boolean usesAvatar(boolean senderAvatar){return avatarLeft;}
    boolean usesAvatar(boolean senderAvatar,boolean appAvatar){return avatarLeft;}

    /** 右侧显示联系人名字时，左侧只留图标，避免名字重复。 */
    boolean iconOnlyLeft(){return !contentRight;}

    String rightText(String body,String sender){return rightText(body,sender,null);}
    String rightText(String body,String sender,String app){
        if(contentRight&&body!=null&&!body.isBlank())return body;
        return name(sender,app);
    }

    String name(String sender,String app){
        if(sender!=null&&!sender.isBlank())return sender;
        if(app!=null&&!app.isBlank())return app;
        return "应用";
    }
}
