package com.joelau.notifyisland;

/** One stored value makes every optional display mode mutually exclusive. */
enum MessageDisplay {
    CONTENT, AVATAR, SENDER_ONLY, APP_ONLY;
    static final String PREF="message_display";
    static MessageDisplay parse(String value){
        try{return valueOf(value);}catch(IllegalArgumentException|NullPointerException ignored){return CONTENT;}
    }
    String label(){
        return switch(this){case AVATAR->"头像";case SENDER_ONLY->"仅发送人";case APP_ONLY->"仅应用名";default->"默认";};
    }
    static final String SENDER_AVATAR="sender_only_avatar";
    static final String APP_AVATAR="app_only_avatar";
    boolean usesAvatar(boolean senderAvatar){return this==AVATAR||(this==SENDER_ONLY&&senderAvatar);}
    boolean usesAvatar(boolean senderAvatar,boolean appAvatar){return usesAvatar(senderAvatar)||(this==APP_ONLY&&appAvatar);}
    boolean iconOnlyLeft(){return this==SENDER_ONLY||this==APP_ONLY;}
    String rightText(String body,String sender){
        return this==SENDER_ONLY?(sender==null||sender.isBlank()?"应用":sender):body;
    }
    String rightText(String body,String sender,String app){
        return this==APP_ONLY?(app==null||app.isBlank()?"应用":app):rightText(body,sender);
    }
}

