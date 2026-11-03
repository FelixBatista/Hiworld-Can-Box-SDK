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
import com.sleeperculture.teyes.PsaPhysicalCanExperimental;
import com.sleeperculture.teyes.SignalDefinition;
import com.sleeperculture.teyes.SyuCanClient;
import com.sleeperculture.teyes.VehicleDecoder;
import com.sleeperculture.teyes.VehicleSignal;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class MainActivity extends Activity implements SyuCanClient.Listener {
    private static final String APP_VERSION="0.5.2";
    private final VehicleDecoder decoder=new VehicleDecoder();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Object lock=new Object();
    private final Deque<String> recent=new ArrayDeque<>();
    private final Map<String,String> latestSignals=new TreeMap<>();
    private final Map<String,VehicleSignal> decodedSignals=new TreeMap<>();
    private File capture;
    private long bytes, events, omitted, queueDropped;
    private static final long LIMIT=16L*1024*1024;
    private TextView status, data, catalogData;
    private ScrollView catalogScroll;
    private android.view.View mainScreen, catalogScreen;
    private boolean catalogVisible;
    private CheckBox receivedOnly;
    private String lastStatus="Pronto. Instale na central Teyes que contém com.syu.ms.";
    private SyuCanClient client;
    private Button connect;
    private CheckBox rawEnabled;
    private final Runnable refresh=new Runnable() {
        @Override public void run() {
            if(catalogVisible) {
                refreshCatalogText(true);
                ui.postDelayed(this,1000);
                return;
            }
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
                JSONObject meta=new JSONObject();meta.put("type","session");meta.put("appVersion",APP_VERSION);
                meta.put("timeMs",System.currentTimeMillis());meta.put("synthetic",false);
                meta.put("manufacturer",android.os.Build.MANUFACTURER);meta.put("model",android.os.Build.MODEL);
                meta.put("androidSdk",android.os.Build.VERSION.SDK_INT);meta.put("androidBuild",android.os.Build.DISPLAY);
                out.write((meta+"\n").getBytes(StandardCharsets.UTF_8));
            } catch(Exception e) { /* Displayed on the first write as capture_error. */ }
        }
        bytes=capture.length();
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(20,16,20,12);
        TextView title=new TextView(this);title.setText("Teyes CAN Probe · leitura Binder");title.setTextSize(22);root.addView(title);
        TextView hint=new TextView(this);hint.setText("508 SW 2014 sem amp: leitura passiva. Velocidade CAN continua estimada; farol alto do catálogo externo não está disponível no transporte Binder.");root.addView(hint);
        rawEnabled=new CheckBox(this);rawEnabled.setText("Capturar payloads MCU/CAN box em tempo real");rawEnabled.setChecked(true);root.addView(rawEnabled);
        LinearLayout buttons=new LinearLayout(this);
        connect=new Button(this);connect.setText("Conectar");connect.setOnClickListener(v -> start());buttons.addView(connect);
        Button stop=new Button(this);stop.setText("Parar");stop.setOnClickListener(v -> stop());buttons.addView(stop);
        Button export=new Button(this);export.setText("Exportar JSONL");export.setOnClickListener(v -> export());buttons.addView(export);root.addView(buttons);
        Button catalog=new Button(this);catalog.setText("Abrir catálogo completo");catalog.setOnClickListener(v -> showCatalog());root.addView(catalog);
        status=new TextView(this);status.setText(lastStatus);root.addView(status);
        ScrollView scroll=new ScrollView(this);data=new TextView(this);data.setTextIsSelectable(true);data.setTextSize(14);scroll.addView(data);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        mainScreen=root;

        LinearLayout catalogRoot=new LinearLayout(this);catalogRoot.setOrientation(LinearLayout.VERTICAL);catalogRoot.setPadding(20,16,20,12);
        TextView catalogTitle=new TextView(this);catalogTitle.setText("Catálogo completo do SDK");catalogTitle.setTextSize(22);catalogRoot.addView(catalogTitle);
        TextView catalogHint=new TextView(this);catalogHint.setText("Lista compacta atualizada ao vivo. Valor zero é mostrado como zero; ‘não recebido’ significa ausência de callback.");catalogRoot.addView(catalogHint);
        LinearLayout catalogControls=new LinearLayout(this);
        Button back=new Button(this);back.setText("Voltar");back.setOnClickListener(v -> showMain());catalogControls.addView(back);
        receivedOnly=new CheckBox(this);receivedOnly.setText("Só recebidos");receivedOnly.setChecked(false);receivedOnly.setOnCheckedChangeListener((button,checked) -> refreshCatalogText(false));catalogControls.addView(receivedOnly);
        catalogRoot.addView(catalogControls);
        catalogScroll=new ScrollView(this);catalogScroll.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        catalogData=new TextView(this);catalogData.setFocusable(false);catalogData.setTextIsSelectable(false);catalogData.setTextSize(14);catalogScroll.addView(catalogData);catalogRoot.addView(catalogScroll,new LinearLayout.LayoutParams(-1,0,1));
        catalogScreen=catalogRoot;
        setContentView(mainScreen);
    }
    @Override public void onStart() {super.onStart();ui.post(refresh);}
    private void start() {
        if (client!=null) return;
        try {
            JSONObject config=new JSONObject();config.put("type","reader_configuration");config.put("appVersion",APP_VERSION);
            config.put("timeMs",System.currentTimeMillis());config.put("captureMcuPayloads",rawEnabled.isChecked());
            config.put("vehicle","Peugeot 508 SW 2014");config.put("amplifier","none_user_reported");write(config.toString());
        }catch(Exception e){status.setText("Falha ao registrar configuração: "+e);return;}
        decoder.reset();
        synchronized(lock){decodedSignals.clear();}
        client=new SyuCanClient(this,this,rawEnabled.isChecked());connect.setEnabled(false);rawEnabled.setEnabled(false);client.connect();
    }
    private void stop() {
        if(client!=null) {SyuCanClient c=client;client=null;c.close();queueDropped+=c.droppedEvents();}
        lastStatus="Leitura interrompida. A captura foi preservada.";
        status.setText(lastStatus);
        connect.setEnabled(true);rawEnabled.setEnabled(true);
    }
    @Override public void onStop() {stop();ui.removeCallbacks(refresh);super.onStop();}
    @Override public void onStatus(String message) {
        JSONObject obj=new JSONObject();
        try {obj.put("type","status");obj.put("timeMs",System.currentTimeMillis());obj.put("message",message);write(obj.toString());}
        catch(Exception ignored) {}
        ui.post(() -> {lastStatus=message;status.setText(message);});
    }
    @Override public void onEvent(SyuCanClient.Event e) {
        try {
            JSONObject obj=new JSONObject();obj.put("type","update");obj.put("synthetic",false);obj.put("module",e.module);
            obj.put("timeMs",e.timeMs);obj.put("elapsedNs",e.elapsedNs);obj.put("code",e.code);
            obj.put("profile",e.profile==null ? JSONObject.NULL : e.profile);
            obj.put("ints",e.ints==null ? JSONObject.NULL : new JSONArray(e.ints));
            obj.put("floats",e.floats==null ? JSONObject.NULL : new JSONArray(e.floats));
            obj.put("strings",e.strings==null ? JSONObject.NULL : new JSONArray(e.strings));
            List<VehicleSignal> decoded=decoder.acceptBinder(e.module,e.code,e.ints,e.timeMs);
            if(!decoded.isEmpty()) {
                JSONArray semanticList=new JSONArray();
                for(VehicleSignal signal:decoded) {
                    JSONObject semantic=new JSONObject();semantic.put("name",signal.name);semantic.put("value",signal.value==null?JSONObject.NULL:signal.value);
                    semantic.put("raw",signal.raw);semantic.put("unit",signal.unit);semantic.put("source",signal.source);
                    semantic.put("confidence",signal.confidence.name());semantic.put("note",signal.note);semantic.put("receivedAtMs",signal.receivedAtMs);
                    semanticList.put(semantic);
                }
                obj.put("decodedSignals",semanticList);
            }
            if(e.module==7 && e.code==1019 && e.ints!=null) {
                int[] unsigned=new int[e.ints.length];for(int i=0;i<unsigned.length;i++)unsigned[i]=e.ints[i]&255;
                obj.put("payloadUnsigned",new JSONArray(unsigned));obj.put("payloadLayer","mcu_canbox");
                obj.put("snapshotCandidate",e.ints.length==1 && e.ints[0]==0);
            }
            write(obj.toString());
            synchronized(lock) {
                events++;
                for(VehicleSignal signal:decoded)decodedSignals.put(signal.name,signal);
                String signal="["+e.module+":"+e.code+"] "+label(e.module,e.code)+" ints="+Arrays.toString(e.ints);
                if((e.module==0 && (e.code==4||e.code==60))||(e.module==10 && e.code==6)||
                   (e.module==7 && (e.code==1000||e.code==1031||e.code==1032||e.code==1053))) latestSignals.put(e.module+":"+e.code,signal);
                recent.addFirst(signal+" floats="+Arrays.toString(e.floats)+" strings="+Arrays.toString(e.strings));
                while(recent.size()>60) recent.removeLast();
            }
        } catch(Exception ex) {ui.post(() -> status.setText("capture_error: "+ex));}
    }

    private void showCatalog() {
        catalogVisible=true;
        refreshCatalogText(false);
        setContentView(catalogScreen);
    }
    private void showMain() {
        catalogVisible=false;
        setContentView(mainScreen);
    }
    @Override public void onBackPressed() {
        if(catalogVisible)showMain();else super.onBackPressed();
    }
    private String renderCatalog() {
        Map<String,VehicleSignal> values;
        synchronized(lock){values=new TreeMap<>(decodedSignals);}
        boolean only=receivedOnly!=null&&receivedOnly.isChecked();
        StringBuilder out=new StringBuilder();
        out.append(client==null?"DESCONECTADO — valores abaixo são históricos\n":"CONECTADO — atualização ao vivo\n");
        out.append(lastStatus).append("\n");
        out.append("Recebidos: ").append(values.size()).append(" sinais decodificados\n\n");
        List<SignalDefinition> definitions=new java.util.ArrayList<>();
        definitions.addAll(VehicleDecoder.catalog());definitions.addAll(PsaPhysicalCanExperimental.catalog());
        Set<String> defined=new HashSet<>();String category="";
        for(SignalDefinition definition:definitions) {
            defined.add(definition.name);VehicleSignal value=values.get(definition.name);
            if(only&&value==null)continue;
            if(!category.equals(definition.category)) {category=definition.category;out.append("\n== ").append(category.toUpperCase()).append(" ==\n");}
            appendCatalogRow(out,definition,value);
        }
        for(VehicleSignal value:values.values())if(!defined.contains(value.name)) {
            if(!category.equals("Campos brutos recebidos")){category="Campos brutos recebidos";out.append("\n== CAMPOS BRUTOS RECEBIDOS ==\n");}
            SignalDefinition definition=new SignalDefinition(category,value.name,value.unit,value.source,value.note,"perfil ativo",value.confidence,true);
            appendCatalogRow(out,definition,value);
        }
        return out.toString();
    }
    private void refreshCatalogText(boolean preserveScroll) {
        if(catalogData==null||catalogScroll==null)return;
        final int scrollY=preserveScroll?catalogScroll.getScrollY():0;
        String text=renderCatalog();
        if(text.contentEquals(catalogData.getText()))return;
        catalogData.setText(text);
        if(preserveScroll)catalogScroll.post(() -> catalogScroll.scrollTo(0,scrollY));
        else catalogScroll.scrollTo(0,0);
    }
    private static void appendCatalogRow(StringBuilder out,SignalDefinition definition,VehicleSignal value) {
        out.append(definition.name).append(": ");
        if(value==null)out.append(definition.observableByBinderProbe?"não recebido":"indisponível neste Probe Binder");
        else if(value.value==null)out.append("indisponível");
        else out.append(value.value).append(value.unit.isEmpty()?"":" "+value.unit);
        out.append('\n');
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
