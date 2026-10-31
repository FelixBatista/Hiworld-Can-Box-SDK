package com.sleeperculture.teyes;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Independent implementation of the observed SYU Binder wire protocol.
 * No module cmd(), hardware get(), broadcasts, device files or CAN frame writer.
 * Call connect/close on the Android main thread; listener methods run on worker.
 */
public final class SyuCanClient implements AutoCloseable {
    public static final String TOOLKIT = "com.syu.ipc.IRemoteToolkit";
    public static final String MODULE = "com.syu.ipc.IRemoteModule";
    public static final String CALLBACK = "com.syu.ipc.IModuleCallback";
    private static final int GET_MODULE = 1, REGISTER = 3, UNREGISTER = 4;

    public interface Listener {
        void onStatus(String message);
        void onEvent(Event event);
    }
    public static final class Event {
        public final long timeMs;
        public final long elapsedNs;
        public final int code;
        public final int module;
        public final int[] ints;
        public final float[] floats;
        public final String[] strings;
        public final Integer profile;
        public Event(int module, int code, int[] ints, float[] floats, String[] strings, Integer profile, long timeMs, long elapsedNs) {
            this.code=code; this.ints=ints; this.floats=floats; this.strings=strings; this.profile=profile;
            this.module=module;
            this.timeMs=timeMs; this.elapsedNs=elapsedNs;
        }
    }

    private final Context context;
    private final Listener listener;
    private final boolean captureMcuPayloads;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong dropped = new AtomicLong();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(2048), r -> new Thread(r,"SyuCanReader"),
            (r,e) -> { dropped.incrementAndGet(); }) {
        @Override protected void terminated() {
            // Cleanup cannot be rejected by the bounded event queue.
            // terminated() can be invoked by shutdown() on the Android UI thread.
            new Thread(() -> release(),"SyuCanCleanup").start();
            super.terminated();
        }
    };
    // Accessed by worker, then by cleanup only after the worker has terminated.
    private final Map<Integer,Set<Integer>> subscribed = new HashMap<>();
    private final Map<Integer,CallbackBinder> callbacks = new HashMap<>();
    private final Map<Integer,IBinder> modules = new HashMap<>();
    private Integer profile; // worker thread only
    private boolean bound; // main thread only
    private final Runnable timeout = () -> {
        if (active.get()) {
            status("Sem resposta do serviço em 10 s. Desconecte e tente novamente.");
        }
    };

    public SyuCanClient(Context context, Listener listener) {
        this(context,listener,false);
    }
    public SyuCanClient(Context context, Listener listener, boolean captureMcuPayloads) {
        this.context=context.getApplicationContext(); this.listener=listener; this.captureMcuPayloads=captureMcuPayloads;
    }
    public long droppedEvents() { return dropped.get(); }

    public void connect() {
        if (closed.get()) throw new IllegalStateException("Create a new client after close()");
        if (!active.compareAndSet(false,true)) return;
        Intent intent = new Intent("com.syu.ms.toolkit");
        intent.setComponent(new ComponentName("com.syu.ms","app.ToolkitService"));
        try {
            android.content.pm.PackageInfo info=context.getPackageManager().getPackageInfo("com.syu.ms",0);
            status("MS instalado: "+info.versionName+" ("+info.versionCode+")");
            bound=context.bindService(intent,connection,Context.BIND_AUTO_CREATE);
            status(bound ? "Aguardando com.syu.ms/app.ToolkitService…" : "Serviço com.syu.ms ausente ou indisponível.");
            if (bound) main.postDelayed(timeout,10000); else active.set(false);
        } catch (Exception e) {
            active.set(false); status("Falha ao vincular: "+e);
        }
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            main.removeCallbacks(timeout);
            worker.execute(() -> {
                if (!active.get()) return;
                try {
                    if (!TOOLKIT.equals(service.getInterfaceDescriptor())) throw new RemoteException("Toolkit descriptor diferente");
                    if (!openModule(service,7)) throw new RemoteException("Módulo CAN 7 ausente");
                    // OEM ConnectionCanbus subscribes to 1000 first. Its callback identifies the profile.
                    subscribe(7,1000);
                    for (int code=0;code<97;code++) subscribe(7,code);
                    for (int code : new int[]{1001,1002,1003,1007,1031,1032,1053}) subscribe(7,code);
                    try {
                        if(openModule(service,0)) {subscribe(0,4);subscribe(0,60);}
                    }catch(RemoteException e){status("Módulo Main indisponível: "+e);}
                    try {
                        if(openModule(service,10)) subscribe(10,6);
                    }catch(RemoteException e){status("Módulo Steer indisponível: "+e);}
                    if(captureMcuPayloads) subscribe(7,1019);
                    status("Binder conectado; MCU bruto "+(captureMcuPayloads?"ativado":"desativado")+". Nenhum comando de atuação enviado.");
                } catch (Exception e) { status("Falha IPC: "+e); }
            });
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            main.removeCallbacks(timeout);
            worker.execute(() -> { modules.clear(); callbacks.clear(); subscribed.clear(); profile=null; status("Serviço desconectou. Reconecte manualmente."); });
        }
        @Override public void onNullBinding(ComponentName name) {
            main.removeCallbacks(timeout); status("Serviço retornou Binder nulo.");
        }
        @Override public void onBindingDied(ComponentName name) {
            main.removeCallbacks(timeout); status("Vínculo do serviço expirou. Reconecte manualmente.");
        }
    };

    private static IBinder getRemoteModule(IBinder toolkit,int moduleId) throws RemoteException {
        Parcel data=Parcel.obtain(), reply=Parcel.obtain();
        try {
            data.writeInterfaceToken(TOOLKIT); data.writeInt(moduleId);
            if (!toolkit.transact(GET_MODULE,data,reply,0)) throw new RemoteException("getRemoteModule não reconhecido");
            reply.readException(); return reply.readStrongBinder();
        } finally { reply.recycle(); data.recycle(); }
    }

    private boolean openModule(IBinder toolkit,int moduleId) throws RemoteException {
        IBinder binder=getRemoteModule(toolkit,moduleId);
        if(binder==null) {status("Módulo "+moduleId+" ausente.");return false;}
        if(!MODULE.equals(binder.getInterfaceDescriptor())) throw new RemoteException("Descriptor diferente: módulo "+moduleId);
        modules.put(moduleId,binder);subscribed.put(moduleId,new HashSet<Integer>());
        callbacks.put(moduleId,new CallbackBinder(moduleId));return true;
    }

    private void subscribe(int moduleId,int code) throws RemoteException {
        IBinder module=modules.get(moduleId);
        Set<Integer> codes=subscribed.get(moduleId);
        CallbackBinder callback=callbacks.get(moduleId);
        if (module==null || codes.contains(code)) return;
        Parcel data=Parcel.obtain();
        try {
            data.writeInterfaceToken(MODULE);
            data.writeStrongBinder(callback); data.writeInt(code); data.writeInt(1);
            if (!module.transact(REGISTER,data,null,IBinder.FLAG_ONEWAY)) throw new RemoteException("register não reconhecido");
            codes.add(code);
        } finally { data.recycle(); }
    }

    private void release() {
        for(Map.Entry<Integer,Set<Integer>> entry:subscribed.entrySet()) {
            IBinder module=modules.get(entry.getKey());
            CallbackBinder callback=callbacks.get(entry.getKey());
            if(module==null)continue;
            for (Integer code : entry.getValue()) {
                Parcel data=Parcel.obtain();
                try {
                    data.writeInterfaceToken(MODULE); data.writeStrongBinder(callback); data.writeInt(code);
                    module.transact(UNREGISTER,data,null,IBinder.FLAG_ONEWAY);
                } catch (RemoteException ignored) { /* Service may already have died. */ }
                finally { data.recycle(); }
            }
        }
        modules.clear();callbacks.clear();subscribed.clear();profile=null;
    }

    private final class CallbackBinder extends Binder implements IInterface {
        private final int moduleId;
        CallbackBinder(int moduleId) { this.moduleId=moduleId;attachInterface(this,CALLBACK); }
        @Override public IBinder asBinder() { return this; }
        @Override protected boolean onTransact(int transaction, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (transaction==IBinder.INTERFACE_TRANSACTION) { if (reply!=null) reply.writeString(CALLBACK); return true; }
            if (transaction!=1) return super.onTransact(transaction,data,reply,flags);
            data.enforceInterface(CALLBACK);
            final int code=data.readInt();
            final int[] ints=data.createIntArray();
            final float[] floats=data.createFloatArray();
            final String[] strings=data.createStringArray();
            final long timeMs=System.currentTimeMillis(), elapsedNs=SystemClock.elapsedRealtimeNanos();
            if (active.get()) worker.execute(() -> {
                if (!active.get()) return;
                if (moduleId==7 && code==1000 && ints!=null && ints.length>0) {
                    profile=ints[0];
                    int protocol=profile&0xffff, variant=(profile>>>16)&0xffff;
                    status("Perfil "+profile+"; protocolo "+protocol+"; variante "+variant);
                    // Only this family's extended subscription range was corroborated in both APKs.
                    if (protocol==252) {
                        try { for (int id=97;id<258;id++) subscribe(7,id); }
                        catch (RemoteException e) { status("Assinatura PSA incompleta: "+e); }
                    }
                }
                listener.onEvent(new Event(moduleId,code,ints,floats,strings,profile,timeMs,elapsedNs));
            });
            return true;
        }
    }
    private void status(String message) {
        if (!worker.isShutdown()) worker.execute(() -> { if(!closed.get()) listener.onStatus(message); });
    }
    @Override public void close() {
        if (!closed.compareAndSet(false,true)) return;
        active.set(false); main.removeCallbacks(timeout);
        if (bound) {
            try { context.unbindService(connection); }
            catch (IllegalArgumentException ignored) { /* Already unbound by Android. */ }
            bound=false;
        }
        // Queued callbacks after stopping are intentionally discarded and counted.
        java.util.List<Runnable> pending=new java.util.ArrayList<>();
        worker.getQueue().drainTo(pending); dropped.addAndGet(pending.size());
        worker.shutdown(); // release runs in terminated(), after the in-flight listener returns
    }
}
