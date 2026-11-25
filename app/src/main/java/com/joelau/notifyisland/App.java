package com.joelau.notifyisland;

import android.app.Application;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
import java.util.concurrent.CopyOnWriteArraySet;

public final class App extends Application implements XposedServiceHelper.OnServiceListener {
    private static XposedService service;
    private static final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();
    public interface Listener { void changed(XposedService s); }
    public static void add(Listener l) { listeners.add(l); l.changed(service); }
    public static void remove(Listener l) { listeners.remove(l); }
    @Override public void onCreate() { super.onCreate(); XposedServiceHelper.registerListener(this); }
    @Override public void onServiceBind(XposedService s) { service = s; for (Listener l:listeners) l.changed(s); }
    @Override public void onServiceDied(XposedService s) { service = null; for (Listener l:listeners) l.changed(null); }
}
