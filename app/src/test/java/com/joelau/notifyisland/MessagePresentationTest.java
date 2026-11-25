package com.joelau.notifyisland;

import android.app.*;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Icon;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=android.app.Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MessagePresentationTest {
    private Context context(){return RuntimeEnvironment.getApplication();}
    private Bitmap bitmap(int color){Bitmap bitmap=Bitmap.createBitmap(140,100,Bitmap.Config.ARGB_8888);bitmap.eraseColor(color);return bitmap;}
    private Notification plain(){Notification notification=new Notification();notification.extras=new Bundle();return notification;}
    private Notification message(Icon portrait,Icon large){
        Notification notification=plain();
        Person sender=new Person.Builder().setName("Alice").setIcon(portrait).build();
        Bundle message=new Bundle();message.putParcelable("sender_person",sender);message.putCharSequence("text","Hello");
        notification.extras.putParcelableArray(Notification.EXTRA_MESSAGES,new Parcelable[]{message});
        if(large!=null)notification.extras.putParcelable(Notification.EXTRA_LARGE_ICON,large);
        return notification;
    }
    private void assertColor(int expected,byte[] bytes){
        assertNotNull(bytes);assertTrue(bytes.length<=SenderAvatar.MAX_BYTES);
        Bitmap decoded=SenderAvatar.decode(bytes);assertNotNull(decoded);
        assertEquals(SenderAvatar.SIDE,decoded.getWidth());assertEquals(SenderAvatar.SIDE,decoded.getHeight());
        assertEquals(expected,decoded.getPixel(48,48));
    }
    @Test public void latestMessagePersonWinsOverLargeIconOnEverySystem(){
        Notification notification=message(Icon.createWithBitmap(bitmap(Color.RED)),Icon.createWithBitmap(bitmap(Color.BLUE)));
        for(OriginProfile profile:new OriginProfile[]{OriginProfile.OS5,OriginProfile.OS6,OriginProfile.OS7})
            assertColor(Color.RED,SenderAvatar.extract(context(),context().getPackageName(),notification,profile));
    }
    @Test public void legacyBitmapAndModernLargeIconWorkOnEverySystem(){
        for(OriginProfile profile:new OriginProfile[]{OriginProfile.OS5,OriginProfile.OS6,OriginProfile.OS7}){
            Notification legacy=plain();legacy.extras.putParcelable(Notification.EXTRA_LARGE_ICON,bitmap(Color.GREEN));
            assertColor(Color.GREEN,SenderAvatar.extract(context(),context().getPackageName(),legacy,profile));
            Notification modern=new Notification.Builder(context(),"test").setLargeIcon(Icon.createWithBitmap(bitmap(Color.BLUE))).build();
            assertColor(Color.BLUE,SenderAvatar.extract(context(),context().getPackageName(),modern,profile));
            assertNull(SenderAvatar.extract(context(),context().getPackageName(),plain(),profile));
        }
    }
    @Test public void unavailableUriFallsBackAndNeverUsesSelfPortrait(){
        Notification notification=message(Icon.createWithContentUri("content://missing.example/avatar"),Icon.createWithBitmap(bitmap(Color.GREEN)));
        notification.extras.putParcelable(Notification.EXTRA_MESSAGING_PERSON,new Person.Builder().setName("Me").setIcon(Icon.createWithBitmap(bitmap(Color.RED))).build());
        for(OriginProfile profile:new OriginProfile[]{OriginProfile.OS5,OriginProfile.OS6,OriginProfile.OS7})
            assertColor(Color.GREEN,SenderAvatar.extract(context(),context().getPackageName(),notification,profile));
        notification.extras.remove(Notification.EXTRA_MESSAGES);notification.extras.remove(Notification.EXTRA_LARGE_ICON);
        assertNull(SenderAvatar.extract(context(),context().getPackageName(),notification,OriginProfile.OS7));
    }
    @Test public void groupNeverBorrowsPreviousParticipantsAvatar(){
        Notification notification=message(Icon.createWithBitmap(bitmap(Color.RED)),null);
        Bundle first=(Bundle)notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES)[0];
        Bundle latest=new Bundle();latest.putParcelable("sender_person",new Person.Builder().setName("Bob").build());
        notification.extras.putParcelableArray(Notification.EXTRA_MESSAGES,new Parcelable[]{first,latest});
        assertNull(SenderAvatar.extract(context(),context().getPackageName(),notification,OriginProfile.OS6));
    }
    @Test public void malformedAndOversizedWirePayloadsAreIgnored(){
        assertNull(SenderAvatar.decode(new byte[]{1,2,3}));
        assertNull(SenderAvatar.decode(new byte[SenderAvatar.MAX_BYTES+1]));
        Bitmap large=Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888);
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();large.compress(Bitmap.CompressFormat.PNG,100,bytes);
        assertNull(SenderAvatar.decode(bytes.toByteArray()));
    }
    private LinearLayout right(String message,MessageDisplay mode,Integer unread)throws Exception{
        return MessageViews.half(context(),310,70,false,"unused","Alice",message,null,mode,unread);
    }
    private void layout(View row){
        row.measure(View.MeasureSpec.makeMeasureSpec(310,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(70,View.MeasureSpec.EXACTLY));
        row.layout(0,0,310,70);
    }
    @Test public void shortDetailsAlignRightAndUnreadStaysOutsideMarquee()throws Exception{
        for(Integer unread:new Integer[]{null,1,99,1000}){
            LinearLayout row=right("07:19",MessageDisplay.CONTENT,unread);layout(row);
            TextView body=(TextView)row.getChildAt(0);
            assertEquals(Gravity.RIGHT,Gravity.getAbsoluteGravity(body.getGravity(),body.getLayoutDirection())&Gravity.HORIZONTAL_GRAVITY_MASK);
            assertTrue(body.getWidth()>0);
            assertTrue(body.getLayout().getLineLeft(0)>0); // Actual short text starts at the right.
            assertEquals(unread==null?1:2,row.getChildCount());
            if(unread!=null){TextView count=(TextView)row.getChildAt(1);assertEquals(MessageViews.unreadLabel(unread),count.getText().toString());assertTrue(body.getRight()<=count.getLeft());assertTrue(count.getWidth()>0);}
        }
        LinearLayout longRow=right("长消息内容".repeat(100),MessageDisplay.CONTENT,27);layout(longRow);
        assertEquals(2,longRow.getChildCount());assertEquals("27",((TextView)longRow.getChildAt(1)).getText().toString());
        assertTrue(longRow.getChildAt(0).getRight()<=longRow.getChildAt(1).getLeft());
    }
    @Test public void portraitIsRoundedAndFixedOutsideText()throws Exception{
        LinearLayout row=MessageViews.half(context(),310,70,true,"unused","Alice","Hello",bitmap(Color.RED),MessageDisplay.AVATAR,null);layout(row);
        assertTrue(row.getChildAt(0) instanceof IslandIconView);assertTrue(row.getChildAt(1) instanceof TextView);
        assertEquals(row.getChildAt(0).getWidth(),row.getChildAt(0).getHeight());
        Bitmap rendered=Bitmap.createBitmap(310,70,Bitmap.Config.ARGB_8888);row.draw(new Canvas(rendered));
        View portrait=row.getChildAt(0);assertEquals(0,Color.alpha(rendered.getPixel(portrait.getLeft(),portrait.getTop())));
        assertEquals(Color.RED,rendered.getPixel(portrait.getLeft()+portrait.getWidth()/2,portrait.getTop()+portrait.getHeight()/2));
    }
    @Test public void senderOnlyUsesAppLabelAndPreservesUnread()throws Exception{
        for(String label:new String[]{"微信","QQ","抖音"}){
            String right=MessageDisplay.SENDER_ONLY.rightText("private body",label);assertEquals(label,right);
            LinearLayout row=right(right,MessageDisplay.SENDER_ONLY,3);layout(row);
            assertEquals(label,((TextView)row.getChildAt(0)).getText().toString());
            assertEquals("3",((TextView)row.getChildAt(1)).getText().toString());
        }
        assertEquals(MessageDisplay.CONTENT,MessageDisplay.parse("bad"));
        assertEquals(MessageDisplay.CONTENT,MessageDisplay.parse(null));
        assertFalse(MessageDisplay.CONTENT.usesAvatar(false));assertTrue(MessageDisplay.SENDER_ONLY.usesAvatar(true));assertTrue(MessageDisplay.AVATAR.usesAvatar(false));
    }
    @Test public void cardPortraitIsAtHeaderEndAndAbsentWithoutMetadata(){
        for(Bitmap portrait:new Bitmap[]{null,bitmap(Color.RED)}){
            LinearLayout header=MessageViews.header(context(),context().getPackageName(),"很长的发送人名称".repeat(10),portrait,Color.WHITE);
            int height=Math.round(28*context().getResources().getDisplayMetrics().density);
            header.measure(View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
            header.layout(0,0,900,height);
            assertEquals(portrait==null?2:3,header.getChildCount());
            assertTrue(header.getChildAt(0) instanceof IslandIconView);
            if(portrait!=null){View avatar=header.getChildAt(2);assertEquals(900,avatar.getRight());assertEquals(header.getChildAt(0).getTop(),avatar.getTop());assertTrue(header.getChildAt(1).getRight()<=avatar.getLeft());}
        }
    }
    @Test public void missingPortraitUsesPermanentAppIconInCapsule()throws Exception{
        for(MessageDisplay mode:new MessageDisplay[]{MessageDisplay.AVATAR,MessageDisplay.SENDER_ONLY}){
            LinearLayout row=MessageViews.half(context(),310,70,true,context().getPackageName(),"Alice","微信消息",null,mode,2);layout(row);
            assertEquals(mode.iconOnlyLeft()?1:2,row.getChildCount());assertTrue(row.getChildAt(0) instanceof IslandIconView);
        }
    }
    private FrameLayout nativeCapsule(int maxWidth,int maxHeight){
        return new FrameLayout(context()){
            @Override protected void onMeasure(int w,int h){
                super.onMeasure(View.MeasureSpec.makeMeasureSpec(maxWidth,View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(maxHeight,View.MeasureSpec.EXACTLY));
            }
        };
    }
    private void measureCapsule(FrameLayout capsule){
        capsule.measure(View.MeasureSpec.makeMeasureSpec(2800,View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(120,View.MeasureSpec.AT_MOST));
        capsule.layout(0,0,capsule.getMeasuredWidth(),capsule.getMeasuredHeight());
    }
    private void contained(ViewGroup row){
        assertTrue(row.getWidth()>0);
        for(int i=0;i<row.getChildCount();i++){
            View child=row.getChildAt(i);assertTrue(child.getWidth()>0);
            assertTrue(child.getLeft()>=row.getPaddingLeft());
            assertTrue(child.getRight()<=row.getWidth()-row.getPaddingRight());
            assertTrue(child.getTop()>=0);assertTrue(child.getBottom()<=row.getHeight());
            if(i>0)assertTrue(row.getChildAt(i-1).getRight()<=child.getLeft());
        }
    }
    @Test public void landscapeRowsRespectNativeCapsEvenWithOversizedPreferredWidth()throws Exception{
        for(MessageDisplay mode:MessageDisplay.values())for(int maxWidth:new int[]{160,240,360})for(boolean left:new boolean[]{true,false}){
            LinearLayout row=MessageViews.half(context(),900,70,left,context().getPackageName(),"很长的发送者名称".repeat(10),mode.rightText("消息正文".repeat(100),"Alice","微信"),bitmap(Color.RED),mode,9999);
            assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT,row.getLayoutParams().width);
            FrameLayout capsule=nativeCapsule(maxWidth,70);capsule.addView(row);measureCapsule(capsule);
            assertEquals(maxWidth,row.getWidth());assertEquals(0,row.getLeft());contained(row);
            if(!left){assertEquals("99+",((TextView)row.getChildAt(1)).getText().toString());assertTrue(row.getChildAt(0).getWidth()>0);}
        }
    }
    @Test public void existingRowsReflowOnRotationAndDensityChange()throws Exception{
        int[] preferred={310},height={70};
        for(boolean left:new boolean[]{true,false}){
            LinearLayout row=MessageViews.half(context(),()->preferred[0],()->height[0],left,context().getPackageName(),"Alice很长的名称","横屏消息正文".repeat(40),bitmap(Color.RED),MessageDisplay.CONTENT,27);
            FrameLayout capsule=nativeCapsule(240,100);capsule.addView(row);
            for(int[] dimensions:new int[][]{{310,70},{200,60},{230,80},{310,70}}){
                preferred[0]=dimensions[0];height[0]=dimensions[1];row.requestLayout();capsule.requestLayout();measureCapsule(capsule);
                assertEquals(Math.min(240,preferred[0]),row.getWidth());assertEquals(height[0],row.getHeight());contained(row);
                assertEquals(height[0]*.40f,((TextView)row.getChildAt(left?1:0)).getTextSize(),.01f);
                if(left)assertEquals(Math.round(height[0]*.60f),row.getChildAt(0).getWidth());
            }
        }
    }
    private Switch findSwitch(View view,String description){
        if(view instanceof Switch control&&description.contentEquals(control.getContentDescription()))return control;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){Switch control=findSwitch(group.getChildAt(i),description);if(control!=null)return control;}
        return null;
    }
    @Test public void homeSwitchesAreMutuallyExclusiveAndSurviveRecreation(){
        context().getSharedPreferences("local_settings",0).edit().clear().commit();
        var controller=Robolectric.buildActivity(MainActivity.class).create();MainActivity activity=controller.get();
        Switch avatar=findSwitch(activity.getWindow().getDecorView(),"显示发送人头像");
        Switch sender=findSwitch(activity.getWindow().getDecorView(),"仅显示发送人");
        assertNotNull(avatar);assertNotNull(sender);assertFalse(avatar.isChecked());assertFalse(sender.isChecked());
        avatar.setChecked(true);assertTrue(avatar.isChecked());assertFalse(sender.isChecked());
        sender.setChecked(true);assertTrue(sender.isChecked());assertFalse(avatar.isChecked());
        assertEquals(MessageDisplay.SENDER_ONLY.name(),activity.getSharedPreferences("local_settings",0).getString(MessageDisplay.PREF,""));
        controller.destroy();controller=Robolectric.buildActivity(MainActivity.class).create();activity=controller.get();
        sender=findSwitch(activity.getWindow().getDecorView(),"仅显示发送人");assertTrue(sender.isChecked());
        sender.setChecked(false);assertEquals(MessageDisplay.CONTENT.name(),activity.getSharedPreferences("local_settings",0).getString(MessageDisplay.PREF,""));
        controller.destroy();
    }
    @Test public void senderAvatarChildExpandsAndRetainsChoice(){
        context().getSharedPreferences("local_settings",0).edit().clear().commit();
        var controller=Robolectric.buildActivity(MainActivity.class).create();MainActivity activity=controller.get();
        View root=activity.getWindow().getDecorView();
        Switch child=findSwitch(root,"显示头像"),sender=findSwitch(root,"仅显示发送人"),avatar=findSwitch(root,"显示发送人头像");
        View options=(View)child.getParent().getParent();assertEquals(View.GONE,options.getVisibility());
        sender.setChecked(true);assertEquals(View.VISIBLE,options.getVisibility());child.setChecked(true);
        avatar.setChecked(true);assertEquals(View.GONE,options.getVisibility());assertFalse(sender.isChecked());
        sender.setChecked(true);assertTrue(child.isChecked());assertFalse(avatar.isChecked());
        controller.destroy();controller=Robolectric.buildActivity(MainActivity.class).create();activity=controller.get();
        assertTrue(findSwitch(activity.getWindow().getDecorView(),"显示头像").isChecked());controller.destroy();
    }
    @Test public void automaticWidthHasNoSwitchAndWideAndPermanentSurviveUpgrade(){
        context().getSharedPreferences("local_settings",0).edit().clear().putBoolean("adaptive_width",false).putBoolean("resident_island",true).commit();
        var controller=Robolectric.buildActivity(MainActivity.class).create();MainActivity activity=controller.get();View root=activity.getWindow().getDecorView();
        assertNull(findSwitch(root,"自适应显示"));assertNull(findSwitch(root,"原子岛常驻"));
        findSwitch(root,"扩展显示").setChecked(true);findSwitch(root,"消息常驻").setChecked(true);
        controller.destroy();controller=Robolectric.buildActivity(MainActivity.class).create();root=controller.get().getWindow().getDecorView();
        assertTrue(findSwitch(root,"扩展显示").isChecked());assertTrue(findSwitch(root,"消息常驻").isChecked());
        assertNull(findSwitch(root,"自适应显示"));controller.destroy();
    }
    @Test public void senderOnlyLayoutsHaveOneNameAndFixedIconsAndUnread()throws Exception{
        for(boolean showAvatar:new boolean[]{false,true})for(Bitmap portrait:new Bitmap[]{null,bitmap(Color.RED)})for(int max:new int[]{160,310}){
            LinearLayout left=MessageViews.half(context(),()->310,()->70,true,context().getPackageName(),"Alice","Alice",showAvatar?portrait:null,MessageDisplay.SENDER_ONLY,27);
            FrameLayout capsule=nativeCapsule(max,70);capsule.addView(left);measureCapsule(capsule);assertEquals(1,left.getChildCount());contained(left);
            LinearLayout right=MessageViews.half(context(),()->310,()->70,false,context().getPackageName(),"Alice","Alice",showAvatar?portrait:null,MessageDisplay.SENDER_ONLY,27);
            capsule=nativeCapsule(max,70);capsule.addView(right);measureCapsule(capsule);contained(right);
            assertEquals(2,right.getChildCount());
            assertEquals("Alice",((TextView)right.getChildAt(0)).getText().toString());
            assertEquals("27",((TextView)right.getChildAt(1)).getText().toString());
            for(int i=0;i<right.getChildCount();i++)assertFalse(right.getChildAt(i) instanceof IslandIconView);
        }
        assertFalse(MessageDisplay.SENDER_ONLY.usesAvatar(false));
    }
    @Test public void adaptiveWidthHasOnlyAnUpperCapAndFitsBothSides()throws Exception{
        for(MessageDisplay mode:MessageDisplay.values())for(int screen:new int[]{1080,1260}){
            int previous=0,distinct=0;java.util.Set<Integer> widths=new java.util.HashSet<>();
            for(int length=0;length<80;length++){
                String sender="A".repeat(length+1),body="b".repeat(length/2);String right=mode.rightText(body,sender,"A".repeat(length+1));
                int width=MessageViews.adaptiveHalfWidth(context(),screen,100,70,sender,right,mode,9999);
                int min=MessageViews.halfWidth(screen,100,70,false),max=MessageViews.halfWidth(screen,100,70,true);
                assertTrue(width>0&&width<=max);assertTrue(width>=previous);previous=width;widths.add(width);
                if(width>min&&width<max)distinct++;
                for(boolean left:new boolean[]{false,true}){
                    LinearLayout row=MessageViews.half(context(),()->width,()->70,left,context().getPackageName(),sender,right,null,mode,9999);
                    row.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(70,View.MeasureSpec.EXACTLY));row.layout(0,0,width,70);
                    contained(row);
                    for(int i=0;i<row.getChildCount();i++)if(row.getChildAt(i) instanceof TextView text){
                        if(width<max)assertTrue(text.getWidth()+1>=android.text.Layout.getDesiredWidth(text.getText(),text.getPaint()));
                        if(text.getEllipsize()==android.text.TextUtils.TruncateAt.MARQUEE){
                            // Selection is enabled only after the dwell, so do not assert it synchronously.
                        }
                    }
                }
            }
            assertTrue(distinct>2);assertTrue(widths.size()>4);
            assertEquals(MessageViews.halfWidth(screen,100,70,true),previous);
        }
    }
    @Test public void shortSenderShrinksBelowDefaultAndHeightWithoutClipping()throws Exception{
        for(String sender:new String[]{"A","微信","Evolution"})for(Integer unread:new Integer[]{null,1,9999}){
            int width=MessageViews.adaptiveHalfWidth(context(),1260,100,70,sender,sender,MessageDisplay.SENDER_ONLY,unread);
            assertTrue(width<MessageViews.halfWidth(1260,100,70,false));
            if(sender.equals("A")&&unread==null)assertTrue(width<70);
            for(boolean left:new boolean[]{false,true}){
                LinearLayout row=MessageViews.half(context(),width,70,left,context().getPackageName(),sender,sender,bitmap(Color.RED),MessageDisplay.SENDER_ONLY,unread);
                FrameLayout capsule=nativeCapsule(900,70);capsule.addView(row);measureCapsule(capsule);
                assertEquals(width,row.getWidth());contained(row);
                if(left)assertEquals(1,row.getChildCount());
                else{
                    assertEquals(unread==null?1:2,row.getChildCount());
                    TextView name=(TextView)row.getChildAt(0);
                    assertEquals(sender,name.getText().toString());
                    assertTrue(name.getWidth()+1>=android.text.Layout.getDesiredWidth(sender,name.getPaint()));
                    if(unread!=null)assertEquals(MessageViews.unreadLabel(unread),((TextView)row.getChildAt(1)).getText().toString());
                }
            }
        }
    }
    @Test public void adaptiveRowsShrinkAgainAfterContentAndDensityChanges()throws Exception{
        int[] height={70},screen={1260};String[] sender={"微信"};
        for(boolean left:new boolean[]{true,false}){
            java.util.function.IntSupplier width=()->MessageViews.adaptiveHalfWidth(context(),screen[0],100,height[0],sender[0],sender[0],MessageDisplay.SENDER_ONLY,27);
            LinearLayout row=MessageViews.half(context(),width,()->height[0],left,context().getPackageName(),sender[0],sender[0],null,MessageDisplay.SENDER_ONLY,27);
            FrameLayout capsule=nativeCapsule(220,100);capsule.addView(row);
            for(int[] dimensions:new int[][]{{70,1260,1},{60,1080,1},{80,1260,40},{70,1260,1}}){
                height[0]=dimensions[0];screen[0]=dimensions[1];sender[0]="微信".repeat(dimensions[2]);
                if(!left)((TextView)row.getChildAt(0)).setText(sender[0]);
                row.requestLayout();capsule.requestLayout();measureCapsule(capsule);
                assertEquals(Math.min(220,width.getAsInt()),row.getWidth());contained(row);
                if(dimensions[2]==1)assertTrue(row.getWidth()<MessageViews.halfWidth(screen[0],100,height[0],false));
                if(!left)assertEquals("27",((TextView)row.getChildAt(1)).getText().toString());
            }
        }
    }
    @Test public void appOnlyUsesAppNameAndMutuallyExclusiveModesKeepTheirAvatarChoices()throws Exception{
        context().getSharedPreferences("local_settings",0).edit().clear().commit();
        var controller=Robolectric.buildActivity(MainActivity.class).create();View root=controller.get().getWindow().getDecorView();
        Switch app=findSwitch(root,"仅显示应用名称"),sender=findSwitch(root,"仅显示发送人"),avatar=findSwitch(root,"显示发送人头像");
        android.view.ViewGroup display=(android.view.ViewGroup)app.getParent().getParent();
        View options=display.getChildAt(display.indexOfChild((View)app.getParent())+1);
        Switch appAvatar=findSwitch(options,"显示头像"),senderAvatar=findSwitch(root,"显示头像");
        assertEquals(View.GONE,options.getVisibility());
        app.setChecked(true);assertFalse(sender.isChecked());assertFalse(avatar.isChecked());
        assertEquals(View.VISIBLE,options.getVisibility());appAvatar.setChecked(true);assertFalse(senderAvatar.isChecked());
        assertEquals(MessageDisplay.APP_ONLY.name(),controller.get().getSharedPreferences("local_settings",0).getString(MessageDisplay.PREF,""));
        for(String label:new String[]{"微信","QQ","抖音"}){
            assertEquals(label,MessageDisplay.APP_ONLY.rightText("秘密正文","Alice",label));
            int width=MessageViews.adaptiveHalfWidth(context(),1260,100,70,"Alice",label,MessageDisplay.APP_ONLY,3,false);
            for(Bitmap portrait:new Bitmap[]{null,bitmap(Color.RED)}){
                LinearLayout left=MessageViews.half(context(),width,70,true,context().getPackageName(),"Alice",label,portrait,MessageDisplay.APP_ONLY,3);
                assertEquals(1,left.getChildCount());assertTrue(left.getChildAt(0) instanceof IslandIconView);
                LinearLayout right=MessageViews.half(context(),width,70,false,context().getPackageName(),"Alice",label,portrait,MessageDisplay.APP_ONLY,3);
                assertEquals(2,right.getChildCount());assertEquals(label,((TextView)right.getChildAt(0)).getText().toString());
            }
        }
        sender.setChecked(true);assertFalse(app.isChecked());assertEquals(View.GONE,options.getVisibility());
        avatar.setChecked(true);assertFalse(sender.isChecked());assertFalse(app.isChecked());
        app.setChecked(true);assertFalse(avatar.isChecked());assertTrue(appAvatar.isChecked());
        assertEquals(View.VISIBLE,options.getVisibility());
        assertFalse(MessageDisplay.APP_ONLY.usesAvatar(true,false));assertTrue(MessageDisplay.APP_ONLY.usesAvatar(false,true));
        assertFalse(MessageDisplay.SENDER_ONLY.usesAvatar(false,true));controller.destroy();
        controller=Robolectric.buildActivity(MainActivity.class).create();root=controller.get().getWindow().getDecorView();
        assertTrue(findSwitch(root,"仅显示应用名称").isChecked());
        assertTrue(controller.get().getSharedPreferences("local_settings",0).getBoolean(MessageDisplay.APP_AVATAR,false));
        assertFalse(findSwitch(root,"显示头像").isChecked());controller.destroy();
    }
    @Test public void unreadBadgeIsSmallWhiteTextOnRoundedGrayAndCapsAt99()throws Exception{
        for(int unread:new int[]{1,3,28,99,100,9999}){
            LinearLayout row=right("Evolution",MessageDisplay.CONTENT,unread);layout(row);
            TextView body=(TextView)row.getChildAt(0),badge=(TextView)row.getChildAt(1);
            assertTrue(badge instanceof MessageViews.UnreadBadge);assertEquals(Color.WHITE,badge.getCurrentTextColor());
            assertEquals(MessageViews.unreadLabel(unread),badge.getText().toString());assertTrue(badge.getTextSize()<body.getTextSize());
            assertTrue(badge.getHeight()<row.getHeight());assertTrue(body.getRight()<badge.getLeft());
            Bitmap pixels=Bitmap.createBitmap(310,70,Bitmap.Config.ARGB_8888);row.draw(new Canvas(pixels));
            assertEquals(0xff333333,pixels.getPixel(badge.getLeft()+2,badge.getTop()+badge.getHeight()/2));
            assertEquals(0,Color.alpha(pixels.getPixel(badge.getLeft(),badge.getTop())));
        }
    }
    @Test public void adaptiveShrinksAndExpandedStaysFixed(){
        for(boolean wide:new boolean[]{false,true}){
            int shortWidth=MessageViews.adaptiveHalfWidth(context(),1260,100,70,"A","QQ",MessageDisplay.APP_ONLY,1,wide);
            if(wide)assertEquals(MessageViews.fixedIslandWidth(1260,100,70),shortWidth);
            else assertTrue(shortWidth<MessageViews.halfWidth(1260,100,70,false));
            int longWidth=MessageViews.adaptiveHalfWidth(context(),1260,100,70,"A","长正文".repeat(100),MessageDisplay.CONTENT,99,wide);
            if(wide)assertEquals(MessageViews.fixedIslandWidth(1260,100,70),longWidth);
            else assertEquals(MessageViews.halfWidth(1260,100,70,true),longWidth);
        }
    }
    @Test public void fittingSenderNameDoesNotStartMarqueeWhenRightSideIsShort()throws Exception{
        String sender="tinklar7",right="???";
        int width=MessageViews.adaptiveHalfWidth(context(),1260,100,70,sender,right,MessageDisplay.CONTENT,null,false);
        LinearLayout left=MessageViews.half(context(),width,70,true,context().getPackageName(),sender,right,null,MessageDisplay.CONTENT,null);
        left.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(70,View.MeasureSpec.EXACTLY));
        left.layout(0,0,width,70);
        TextView title=(TextView)left.getChildAt(1);
        assertTrue(title.getWidth()+1>=android.text.Layout.getDesiredWidth(sender,title.getPaint()));
        assertFalse(title.isSelected());
    }
    @Test public void senderCanExpandBeyondCompactLimitAndBothHalvesUseSameWidth()throws Exception{
        for(int height:new int[]{70,112})for(boolean wide:new boolean[]{false,true}){
            String sender="tinklar7";String right="???";
            TextView probe=new TextView(context());probe.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,height*.40f);
            int compact=MessageViews.halfWidth(1260,100,height,false),max=MessageViews.halfWidth(1260,100,height,true);
            while(Math.round(height*.30f)+Math.round(height*.60f)+Math.round(height*.12f)+android.text.Layout.getDesiredWidth(sender,probe.getPaint())<=compact)sender+="7";
            int width=MessageViews.adaptiveHalfWidth(context(),1260,100,height,sender,right,MessageDisplay.CONTENT,null,wide);
            assertTrue(width<=max);if(!wide)assertTrue(width>compact);
            for(boolean left:new boolean[]{true,false}){
                LinearLayout row=MessageViews.half(context(),width,height,left,context().getPackageName(),sender,right,null,MessageDisplay.CONTENT,null);
                row.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));row.layout(0,0,width,height);
                assertEquals(width,row.getWidth());contained(row);
                TextView text=(TextView)row.getChildAt(left?1:0);
                assertTrue(text.getWidth()+1>=android.text.Layout.getDesiredWidth(text.getText(),text.getPaint()));assertFalse(text.isSelected());
            }
        }
    }
    @Test public void rightSideSenderAndApplicationNamesIncludeBadgeBeforeApplyingCeiling()throws Exception{
        boolean expandedBeyondOldLimit=false;
        for(MessageDisplay mode:new MessageDisplay[]{MessageDisplay.SENDER_ONLY,MessageDisplay.APP_ONLY})
        for(int screen:new int[]{1080,1260})for(int height:new int[]{70,112})
        for(Integer unread:new Integer[]{null,1,27,9999})
        for(String name:new String[]{"QQ","Evolution","企业工作台","Long Application Name".repeat(8)}){
            String right=mode.rightText("正文","Evolution",name);
            if(mode==MessageDisplay.SENDER_ONLY)right=mode.rightText("正文",name,"QQ");
            int width=MessageViews.adaptiveHalfWidth(context(),screen,100,height,name,right,mode,unread,false);
            int maximum=MessageViews.halfWidth(screen,100,height,true);
            LinearLayout row=MessageViews.half(context(),width,height,false,context().getPackageName(),name,right,null,mode,unread);
            row.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));row.layout(0,0,width,height);
            TextView text=(TextView)row.getChildAt(0);assertEquals(name,text.getText().toString());contained(row);
            int nameWidth=(int)Math.ceil(android.text.Layout.getDesiredWidth(name,text.getPaint()))+1;
            int end=Math.round(height*.30f)+nameWidth;
            if(unread!=null){
                TextView badge=(TextView)row.getChildAt(1);assertEquals(MessageViews.unreadLabel(unread),badge.getText().toString());
                assertTrue(text.getRight()<badge.getLeft());end+=Math.round(height*.12f)+badge.getMeasuredWidth();
            }
            assertEquals(Math.min(maximum,Math.max(Math.round(height*.30f)+Math.round(height*.60f),end)),width);
            if(end<=maximum){assertTrue(text.getWidth()+1>=nameWidth);assertFalse(text.isSelected());}
            else{assertEquals(maximum,width);}
            expandedBeyondOldLimit|=width>MessageViews.halfWidth(screen,100,height,false);
        }
        assertTrue(expandedBeyondOldLimit);
    }
    @Test public void expandedSizeOverridesAdaptationInEveryDisplayMode()throws Exception{
        for(MessageDisplay mode:MessageDisplay.values())for(int height:new int[]{70,112})
        for(Integer unread:new Integer[]{null,1,9999})for(String name:new String[]{"QQ","Evolution","长名称".repeat(80)}){
            String right=mode.rightText(name,name,name);int screen=1260,cutout=100;
            int width=MessageViews.adaptiveHalfWidth(context(),screen,cutout,height,name,right,mode,unread,true);
            assertEquals(MessageViews.fixedIslandWidth(screen,cutout,height),width);
            for(boolean left:new boolean[]{true,false}){
                LinearLayout row=MessageViews.half(context(),width,height,left,context().getPackageName(),name,right,null,mode,unread);
                FrameLayout capsule=nativeCapsule(width,height);capsule.addView(row);measureCapsule(capsule);
                assertEquals(width,row.getWidth());contained(row);
            }
        }
    }
    @Test public void switchingExpandedOffShrinksExistingRowsAndRecomputesAfterRotation()throws Exception{
        boolean[] expanded={true};int[] screen={1260};int height=112;
        java.util.function.IntSupplier width=()->MessageViews.adaptiveHalfWidth(context(),screen[0],100,height,"Alice","QQ",MessageDisplay.APP_ONLY,1,expanded[0]);
        LinearLayout row=MessageViews.half(context(),width,()->height,false,context().getPackageName(),"Alice","QQ",null,MessageDisplay.APP_ONLY,1);
        FrameLayout capsule=nativeCapsule(600,height);capsule.addView(row);measureCapsule(capsule);
        int fixed=row.getWidth();assertEquals(MessageViews.fixedIslandWidth(1260,100,height),fixed);
        expanded[0]=false;row.requestLayout();capsule.requestLayout();measureCapsule(capsule);assertTrue(row.getWidth()<fixed);
        expanded[0]=true;screen[0]=1080;row.requestLayout();capsule.requestLayout();measureCapsule(capsule);
        assertEquals(MessageViews.fixedIslandWidth(1080,100,height),row.getWidth());contained(row);
    }
}
