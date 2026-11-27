package com.joelau.notifyisland;

public final class OriginProfileCheck {
    static void check(String marketing,String internal,OriginProfile expected){
        if(OriginProfile.parse(marketing,internal)!=expected)throw new AssertionError(marketing+" / "+internal);
    }
    public static void main(String[] args){
        check("OriginOS 5","15.0",OriginProfile.OS5);
        check("OriginOS 6","16.0",OriginProfile.OS6);
        check("OriginOS 7","17.1",OriginProfile.OS7);
        check("OriginOS 6","15.0",OriginProfile.OS6);
        check("originos7.1","",OriginProfile.OS7);
        check("","15.0",OriginProfile.OS5);
        check("","16.0",OriginProfile.OS6);
        check("","17.0",OriginProfile.OS7);
        check("Funtouch","5.0",OriginProfile.UNKNOWN);
        check("OriginOS 50","",OriginProfile.UNKNOWN);
        check("OriginOS 8","18.0",OriginProfile.UNKNOWN);
        check(null,null,OriginProfile.UNKNOWN);
        System.out.println("PASS: three OriginOS branches, marketing priority, internal 15/16/17 mapping, unknown version");
    }
}
