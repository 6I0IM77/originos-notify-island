package com.joelau.notifyisland;

/** Marketing version first; ro.vivo.os.version is 15/16/17, not 5/6/7. */
enum OriginProfile {
    OS5, OS6, OS7, UNKNOWN;
    static OriginProfile parse(String display,String internal){
        if(display!=null){
            String normalized=display.trim().toLowerCase(java.util.Locale.ROOT);
            for(int version=5;version<=7;version++)
                if(normalized.matches("originos\\s*"+version+"(?:\\D.*)?"))return values()[version-5];
        }
        if(internal!=null){
            if(internal.matches("15(?:\\..*)?"))return OS5;
            if(internal.matches("16(?:\\..*)?"))return OS6;
            if(internal.matches("17(?:\\..*)?"))return OS7;
        }
        return UNKNOWN;
    }
    static OriginProfile current(){
        return parse(property("ro.vivo.os.build.display.id"),property("ro.vivo.os.version"));
    }
    private static String property(String key){
        try{return (String)Class.forName("android.os.SystemProperties").getMethod("get",String.class).invoke(null,key);}
        catch(ReflectiveOperationException|RuntimeException ignored){return "";}
    }
}
