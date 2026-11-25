package com.joelau.notifyisland;

import android.app.AlertDialog;
import android.content.pm.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=android.app.Application.class)
public class AppPickerTest {
    private void install(String pkg,String label){
        ApplicationInfo app=new ApplicationInfo();app.packageName=pkg;app.nonLocalizedLabel=label;app.enabled=true;
        PackageInfo info=new PackageInfo();info.packageName=pkg;info.applicationInfo=app;
        shadowOf(RuntimeEnvironment.getApplication().getPackageManager()).installPackage(info);
    }
    private AlertDialog open(MainActivity activity)throws Exception{
        Method method=MainActivity.class.getDeclaredMethod("selectApps");method.setAccessible(true);method.invoke(activity);
        Field field=MainActivity.class.getDeclaredField("worker");field.setAccessible(true);
        ((ExecutorService)field.get(activity)).submit(()->{}).get(10,java.util.concurrent.TimeUnit.SECONDS);
        shadowOf(Looper.getMainLooper()).idle();
        AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.isShowing());return dialog;
    }
    private <T extends View>T find(View root,Class<T> type){
        if(type.isInstance(root))return type.cast(root);
        if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){T found=find(group.getChildAt(i),type);if(found!=null)return found;}
        return null;
    }
    private Button button(View root,String text){
        if(root instanceof Button b&&text.contentEquals(b.getText()))return b;
        if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){Button found=button(group.getChildAt(i),text);if(found!=null)return found;}
        return null;
    }
    private List<String> order(ListView list){
        ArrayList<String> result=new ArrayList<>();
        for(int i=0;i<list.getAdapter().getCount();i++)result.add(((ApplicationInfo)list.getAdapter().getItem(i)).packageName);
        return result;
    }
    @Test public void checksStayInPlaceAndCompletedChoicesPinOnlyOnNextOpen()throws Exception{
        install("test.alpha","AAA Alpha");install("test.beta","BBB Beta");install("test.zulu","ZZZ Zulu");
        var prefs=RuntimeEnvironment.getApplication().getSharedPreferences("local_settings",0);
        prefs.edit().clear().putStringSet("scope_packages",Set.of("test.beta")).commit();
        var controller=Robolectric.buildActivity(MainActivity.class).create();MainActivity activity=controller.get();
        AlertDialog dialog=open(activity);ListView list=find(dialog.getWindow().getDecorView(),ListView.class);
        List<String> initial=order(list);assertEquals("test.beta",initial.get(0));
        list.performItemClick(null,initial.indexOf("test.zulu"),0);
        assertEquals(initial,order(list));assertEquals(Set.of("test.beta"),prefs.getStringSet("scope_packages",Set.of()));
        list.performItemClick(null,initial.indexOf("test.beta"),0);assertEquals(initial,order(list));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle();
        assertEquals(Set.of("test.zulu"),prefs.getStringSet("scope_packages",Set.of()));
        dialog=open(activity);list=find(dialog.getWindow().getDecorView(),ListView.class);
        assertEquals("test.zulu",order(list).get(0));dialog.cancel();controller.destroy();
    }
    @Test public void bulkEditsAndSearchKeepOpeningOrderAndCancelDiscardsDraft()throws Exception{
        install("test.alpha","AAA Alpha");install("test.beta","BBB Beta");install("test.zulu","ZZZ Zulu");
        var prefs=RuntimeEnvironment.getApplication().getSharedPreferences("local_settings",0);
        prefs.edit().clear().putStringSet("scope_packages",Set.of("test.zulu")).commit();
        var controller=Robolectric.buildActivity(MainActivity.class).create();
        AlertDialog dialog=open(controller.get());View root=dialog.getWindow().getDecorView();ListView list=find(root,ListView.class);
        List<String> initial=order(list);button(root,"反选").performClick();assertEquals(initial,order(list));
        button(root,"全选").performClick();assertEquals(initial,order(list));
        EditText search=find(root,EditText.class);search.setText("test.");
        List<String> filtered=order(list);assertEquals(initial.stream().filter(p->p.startsWith("test.")).toList(),filtered);
        button(root,"反选").performClick();assertEquals(filtered,order(list));
        search.setText("");assertEquals(initial,order(list));dialog.cancel();
        assertEquals(Set.of("test.zulu"),prefs.getStringSet("scope_packages",Set.of()));controller.destroy();
    }
}
