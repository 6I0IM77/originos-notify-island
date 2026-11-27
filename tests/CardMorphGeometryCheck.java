package com.joelau.notifyisland;

public final class CardMorphGeometryCheck {
    private static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    private static void near(float a,float b){check(Math.abs(a-b)<.01f,"discontinuous endpoint: "+a+" != "+b);}
    public static void main(String[] args){
        for(float density:new float[]{1,2.75f,3.5f}){
            CardMorphGeometry.Shape source=new CardMorphGeometry.Shape(30*density,20*density,300*density,160*density,32*density);
            CardMorphGeometry.Shape drag=CardMorphGeometry.drag(source,0,0,density);
            check(source.equals(drag),"zero drag changed rest geometry");
            float previousTop=source.top();
            CardMorphGeometry.Shape shortPull=CardMorphGeometry.drag(source,0,32*density,density);
            check((shortPull.top()-source.top())/(32*density)>.79f,"short drag feels too resistant");
            for(int distance=0;distance<1200;distance++){
                drag=CardMorphGeometry.drag(source,0,distance*density,density);
                check(drag.top()>=previousTop,"resistance reversed drag");previousTop=drag.top();
                near(drag.centerX(),source.centerX());
                check(drag.width()>0&&drag.height()>0,"invalid drag shape");
                check(drag.top()-source.top()<=distance*density,"drag got ahead of finger");
            }
            CardMorphGeometry.Shape target=CardMorphGeometry.target(-50,250*density,280*density,900*density,360*density,800*density,22*density);
            check(target.left()>=0&&target.top()>=0&&target.left()+target.width()<=360*density&&target.top()+target.height()<=800*density,"target escaped display");
            check(CardMorphGeometry.blend(drag,target,0).equals(drag),"release jumped from drag geometry");
            CardMorphGeometry.Shape end=CardMorphGeometry.blend(drag,target,1);
            near(end.left(),target.left());near(end.top(),target.top());near(end.width(),target.width());near(end.height(),target.height());
            for(int i=0;i<=350;i++){
                CardMorphGeometry.Shape sample=CardMorphGeometry.blend(drag,target,i/350f);
                check(sample.width()>0&&sample.height()>0,"morph inverted bounds");
                check(sample.radius()*2<=Math.min(sample.width(),sample.height()),"corner exceeded silhouette");
            }
        }
        check(CardMorphGeometry.smooth(-1)==0&&CardMorphGeometry.smooth(2)==1,"fade extrapolated");
        System.out.println("PASS: morph continuity, density scaling, resistance, display fit, positive bounds and corner limits");
    }
}
