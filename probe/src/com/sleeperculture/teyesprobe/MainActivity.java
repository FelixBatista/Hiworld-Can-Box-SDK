package com.sleeperculture.teyesprobe;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.sleeperculture.teyes.SyuCanClient;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity implements SyuCanClient.Listener {
    private final com.sleeperculture.teyes.Peugeot508Decoder decoder=new com.sleeperculture.teyes.Peugeot508Decoder();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Object lock=new Object();
    private final Deque<String> recent=new ArrayDeque<>();
    private final Map<String,String> latestSignals=new TreeMap<>();
    private File capture;
    private long bytes, events, omitted, queueDropped;
    private static final long LIMIT=16L*1024*1024;
    private TextView status, data;
    private SyuCanClient client;
    private Button connect;
    private CheckBox rawEnabled;
    private final Runnable refresh=new Runnable() {
        @Override public void run() {
            StringBuilder text=new StringBuilder();
            synchronized(lock) {
                text.append("Eventos nesta abertura: ").append(events).append(" | arquivo: ").append(bytes).append(" bytes\n");
                text.append("Omitidos (limite/erro): ").append(omitted).append(" | fila: ")
                    .append(queueDropped+(client==null ? 0 : client.droppedEvents())).append("\n\n");
                text.append("Sinais acompanhados (valores brutos)\n");
                for(String value:latestSignals.values())text.append(value).append('\n');
                text.append("\nEventos recentes\n");
                for (String line:recent) text.append(line).append('\n');
            }
            data.setText(text.toString()); ui.postDelayed(this,750);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        capture=new File(getFilesDir(),"teyes-can-session.jsonl");
        // The same private file survives process/Activity recreation, including the document picker.
        if(!capture.exists()) {
            try (FileOutputStream out=new FileOutputStream(capture,true)) {
                JSONObject meta=new JSONObject();meta.put("type","session");meta.put("appVersion","0.3.0");
                meta.put("timeMs",System.currentTimeMillis());meta.put("synthetic",false);
                meta.put("manufacturer",android.os.Build.MANUFACTURER);meta.put("model",android.os.Build.MODEL);
                meta.put("androidSdk",android.os.Build.VERSION.SDK_INT);meta.put("androidBuild",android.os.Build.DISPLAY);
                out.write((meta+"\n").getBytes(StandardCharsets.UTF_8));
            } catch(Exception e) { /* Displayed on the first write as capture_error. */ }
        }
        bytes=capture.length();
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(20,16,20,12);
        TextView title=new TextView(this);title.setText("Teyes CAN Probe · leitura Binder");title.setTextSize(22);root.addView(title);
        TextView hint=new TextView(this);hint.setText("508 SW 2014 sem amp: teclas, iluminação e velocidade/RPM candidatos. Payload MCU não contém necessariamente IDs CAN. Leitura só enquanto esta tela estiver aberta.");root.addView(hint);
        rawEnabled=new CheckBox(this);rawEnabled.setText("Capturar payloads MCU/CAN box em tempo real");rawEnabled.setChecked(true);root.addView(rawEnabled);
        LinearLayout buttons=new LinearLayout(this);
        connect=new Button(this);connect.setText("Conectar");connect.setOnClickListener(v -> start());buttons.addView(connect);
        Button stop=new Button(this);stop.setText("Parar");stop.setOnClickListener(v -> stop());buttons.addView(stop);
        Button export=new Button(this);export.setText("Exportar JSONL");export.setOnClickListener(v -> export());buttons.addView(export);root.addView(buttons);
        status=new TextView(this);status.setText("Pronto. Instale na central Teyes que contém com.syu.ms.");root.addView(status);
        ScrollView scroll=new ScrollView(this);data=new TextView(this);data.setTextIsSelectable(true);data.setTextSize(14);scroll.addView(data);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }
    @Override public void onStart() {super.onStart();ui.post(refresh);}
    private void start() {
        if (client!=null) return;
        try {
            JSONObject config=new JSONObject();config.put("type","reader_configuration");config.put("appVersion","0.3.0");
            config.put("timeMs",System.currentTimeMillis());config.put("captureMcuPayloads",rawEnabled.isChecked());
            config.put("vehicle","Peugeot 508 SW 2014");config.put("amplifier","none_user_reported");write(config.toString());
        }catch(Exception e){status.setText("Falha ao registrar configuração: "+e);return;}
        client=new SyuCanClient(this,this,rawEnabled.isChecked());connect.setEnabled(false);rawEnabled.setEnabled(false);client.connect();
    }
    private void stop() {
        if(client!=null) {SyuCanClient c=client;client=null;c.close();queueDropped+=c.droppedEvents();}
        status.setText("Leitura interrompida. A captura foi preservada.");
        connect.setEnabled(true);rawEnabled.setEnabled(true);
    }
    @Override public void onStop() {stop();ui.removeCallbacks(refresh);super.onStop();}
    @Override public void onStatus(String message) {
        JSONObject obj=new JSONObject();
        try {obj.put("type","status");obj.put("timeMs",System.currentTimeMillis());obj.put("message",message);write(obj.toString());}
        catch(Exception ignored) {}
        ui.post(() -> status.setText(message));
    }
    @Override public void onEvent(SyuCanClient.Event e) {
        try {
            if(e.module==7 && e.code==1000 && e.ints!=null && e.ints.length>0) {
                com.sleeperculture.teyes.Peugeot508Decoder.Profile p=new com.sleeperculture.teyes.Peugeot508Decoder.Profile(e.ints[0]);
                synchronized(lock){latestSignals.put("profile_name",p.name+" — "+p.warningFor2014());}
            }
            JSONObject obj=new JSONObject();obj.put("type","update");obj.put("synthetic",false);obj.put("module",e.module);
            obj.put("timeMs",e.timeMs);obj.put("elapsedNs",e.elapsedNs);obj.put("code",e.code);
            obj.put("profile",e.profile==null ? JSONObject.NULL : e.profile);
            obj.put("ints",e.ints==null ? JSONObject.NULL : new JSONArray(e.ints));
            obj.put("floats",e.floats==null ? JSONObject.NULL : new JSONArray(e.floats));
            obj.put("strings",e.strings==null ? JSONObject.NULL : new JSONArray(e.strings));
            com.sleeperculture.teyes.Peugeot508Decoder.Signal decoded=decoder.accept(e.module,e.code,e.ints);
            if(decoded!=null) {
                JSONObject semantic=new JSONObject();semantic.put("name",decoded.name);semantic.put("value",decoded.value==null?JSONObject.NULL:decoded.value);
                semantic.put("unit",decoded.unit);semantic.put("quality",decoded.quality);obj.put("decoded",semantic);
            }
            if(e.module==7 && e.code==1019 && e.ints!=null) {
                int[] unsigned=new int[e.ints.length];for(int i=0;i<unsigned.length;i++)unsigned[i]=e.ints[i]&255;
                obj.put("payloadUnsigned",new JSONArray(unsigned));obj.put("payloadLayer","mcu_canbox");
                obj.put("snapshotCandidate",e.ints.length==1 && e.ints[0]==0);
            }
            write(obj.toString());
            synchronized(lock) {
                events++;
                String signal="["+e.module+":"+e.code+"] "+label(e.module,e.code)+" ints="+Arrays.toString(e.ints);
                if((e.module==0 && (e.code==4||e.code==60))||(e.module==10 && e.code==6)||
                   (e.module==7 && (e.code==1000||e.code==1031||e.code==1032||e.code==1053))) latestSignals.put(e.module+":"+e.code,signal);
                recent.addFirst(signal+" floats="+Arrays.toString(e.floats)+" strings="+Arrays.toString(e.strings));
                while(recent.size()>60) recent.removeLast();
            }
        } catch(Exception ex) {ui.post(() -> status.setText("capture_error: "+ex));}
    }
    private void write(String line) throws Exception {
        byte[] buffer=(line+"\n").getBytes(StandardCharsets.UTF_8);
        synchronized(lock) {
            if(bytes+buffer.length>LIMIT) {omitted++;return;}
            try(FileOutputStream out=new FileOutputStream(capture,true)) {out.write(buffer);bytes+=buffer.length;}
            catch(Exception ex) {omitted++;throw ex;}
        }
    }
    private static String label(int module,int code) {
        if(module==0 && code==4)return "iluminação/lamplet (não distingue farol alto/baixo)";
        if(module==0 && code==60)return "tecla direcional MCU (raw)";
        if(module==10 && code==6)return "tecla MCU/Steer (raw; pode ser aprendizagem)";
        if(module!=7)return "campo bruto";
        switch(code) {
            case 0:return "capô (campo OEM)";case 1:return "porta dianteira esquerda";case 2:return "porta dianteira direita";
            case 3:return "porta traseira esquerda";case 4:return "porta traseira direita";case 5:return "porta-malas";
            case 10:return "climatização power";case 11:return "A/C";case 12:return "ciclo do ar (raw)";case 13:return "auto";
            case 16:return "desembaçador traseiro";case 21:return "ventilador esquerdo (raw)";
            case 27:return "temperatura esquerda (raw)";case 28:return "temperatura direita (raw)";
            case 1000:return "perfil CAN completo";case 1001:return "janela A/C";case 1002:return "janela portas";
            case 1003:return "volante à direita";case 1007:return "existência A/C (raw)";case 1037:return "metadado perfil (raw)";
            case 1019:return "payload MCU/CAN box (snapshot {0} não prova frame)";
            case 1031:return "velocidade CAN candidata (sem suporte/unidade confirmados)";
            case 1032:return "RPM candidato (sem suporte/unidade confirmados)";
            case 1053:return "tecla CAN (pode depender de modo aprendizagem)";
            default:return "campo bruto: sem interpretação validada";
        }
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if(client!=null) {
            try {
                JSONObject item=new JSONObject();item.put("type","android_key");item.put("synthetic",false);
                item.put("timeMs",System.currentTimeMillis());item.put("eventTimeMs",event.getEventTime());
                item.put("keyCode",event.getKeyCode());item.put("keyName",KeyEvent.keyCodeToString(event.getKeyCode()));
                item.put("action",event.getAction());item.put("repeatCount",event.getRepeatCount());
                item.put("scope","focused_activity");write(item.toString());
                synchronized(lock){latestSignals.put("android_key","Android em foco: "+KeyEvent.keyCodeToString(event.getKeyCode())+" ação="+event.getAction());}
            }catch(Exception e){status.setText("Falha na captura de tecla: "+e);}
        }
        return super.dispatchKeyEvent(event);
    }
    private void export() {
        stop();
        if(android.os.Build.VERSION.SDK_INT<=28 && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},43);
            return;
        }
        saveDownloads();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==43) {
            if(results.length>0 && results[0]==android.content.pm.PackageManager.PERMISSION_GRANTED)saveDownloads();
            else status.setText("Permissão negada. Captura preservada; permita armazenamento e toque Exportar novamente.");
        }
    }
    private void saveDownloads() {
        status.setText("Exportando para Download/TeyesProbe...");
        new Thread(() -> {
            Uri pending=null;
            File partial=null;
            try {
                String name="teyes-can-"+System.currentTimeMillis()+"-"+java.util.UUID.randomUUID().toString().substring(0,8)+".jsonl";
                String destination;
                if(android.os.Build.VERSION.SDK_INT>=29) {
                    android.content.ContentValues values=new android.content.ContentValues();
                    values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,name);
                    values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"application/octet-stream");
                    values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,"Download/TeyesProbe");
                    values.put(android.provider.MediaStore.MediaColumns.IS_PENDING,1);
                    pending=getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
                    if(pending==null)throw new java.io.IOException("Download provider indisponível");
                    try(OutputStream out=getContentResolver().openOutputStream(pending,"w")){copyCapture(out);}
                    values.clear();values.put(android.provider.MediaStore.MediaColumns.IS_PENDING,0);
                    if(getContentResolver().update(pending,values,null,null)!=1)throw new java.io.IOException("Falha ao publicar arquivo");
                    pending=null;
                    destination="Download/TeyesProbe/"+name;
                } else {
                    File directory=new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),"TeyesProbe");
                    if(!directory.isDirectory()&&!directory.mkdirs())throw new java.io.IOException("Não foi possível criar "+directory);
                    File target=new File(directory,name);partial=new File(directory,name+".partial");
                    if(!partial.createNewFile())throw new java.io.IOException("Arquivo temporário já existe");
                    try(FileOutputStream out=new FileOutputStream(partial)){copyCapture(out);out.getFD().sync();}
                    if(!partial.renameTo(target))throw new java.io.IOException("Falha ao finalizar arquivo");
                    partial=null;destination=target.getAbsolutePath();
                }
                String message="Exportado: "+destination+". A captura original foi preservada.";
                ui.post(() -> {status.setText(message);new android.app.AlertDialog.Builder(this).setTitle("Captura exportada").setMessage(message).setPositiveButton("OK",null).show();});
            } catch(Exception e) {
                if(pending!=null)try{getContentResolver().delete(pending,null,null);}catch(Exception ignored){}
                if(partial!=null)partial.delete();
                ui.post(() -> status.setText("Falha ao salvar em Download: "+e+". Captura original preservada."));
            }
        },"TeyesExport").start();
    }
    private void copyCapture(OutputStream out) throws Exception {
        if(out==null)throw new java.io.IOException("Destino indisponível");
        synchronized(lock) {
            try(FileInputStream in=new FileInputStream(capture)) {
                byte[] buf=new byte[16384];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
            }
            JSONObject summary=new JSONObject();summary.put("type","capture_summary");summary.put("events",events);
            summary.put("omitted",omitted);summary.put("queueDropped",queueDropped);
            summary.put("counterScope","current_activity_instance");summary.put("fileBytes",capture.length());
            out.write((summary+"\n").getBytes(StandardCharsets.UTF_8));
        }
    }
}
