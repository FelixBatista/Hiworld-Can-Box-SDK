package com.sleeperculture.teyes;
import java.util.*;
import static com.sleeperculture.teyes.VehicleSignal.Confidence.*;

/** Per-connection decoder. Call on the single transport worker thread; reset on reconnect.
 * acceptBinder returns changed/new interpretations, not a full vehicle snapshot.
 */
public final class VehicleDecoder {
    private Integer profile;
    private final Map<Integer,Integer> cache=new HashMap<>();
    private final Map<Integer,Long> timestamps=new HashMap<>();
    private final CanboxStream stream=new CanboxStream();
    private static final Map<Integer,String> FIELDS=new LinkedHashMap<>();
    static {
        int[] codes={0,1,2,3,4,5,10,11,12,13,16,18,19,20,21,22,23,24,51,53,54,55,65,99,101,102,104,105,106,107,172,192,197,242};
        String[] names={"hood","front_left_door","front_right_door","rear_left_door","rear_right_door","trunk","hvac_power","hvac_ac","hvac_recirculation","hvac_auto","rear_defrost","left_air_distribution18","left_air_distribution19","left_air_distribution20","left_fan_raw","right_air_distribution22","right_air_distribution23","right_air_distribution24","hvac_mode_raw","hvac_max_ac_candidate","hvac_aqs","hvac_dual","front_defrost","distance_display99_raw","trip1_distance_display_raw","trip1_secondary_display_raw","trip2_secondary_display_raw","trip2_secondary2_display_raw","trip2_distance_display_raw","psa107_unknown","consumption_unit_code","psa192_unknown","panel_brightness_candidate","temperature_offset"};
        for(int i=0;i<codes.length;i++)FIELDS.put(codes[i],names[i]);
    }
    public static Map<Integer,String> binderCatalog(){return Collections.unmodifiableMap(FIELDS);}
    public void reset(){profile=null;cache.clear();timestamps.clear();stream.reset();}
    private void add(List<VehicleSignal> out,String name,Object value,int raw,String unit,String source,String note,long time,VehicleSignal.Confidence confidence) {
        out.add(new VehicleSignal(name,value,raw,unit,source,note,profile==null?-1:profile,time,confidence));
    }
    public List<VehicleSignal> acceptBinder(int module,int code,int[] data,long timeMs) {
        List<VehicleSignal> out=new ArrayList<>();
        if(data==null||data.length==0)return out;
        int raw=data[0];String src="binder:"+module+":"+code;
        if(module==7 && code==1000){if(profile==null||profile!=raw){cache.clear();timestamps.clear();stream.reset();}profile=raw;
            add(out,"profile",new Peugeot508Decoder.Profile(raw).name,raw,"",src,"Profile selection, not physical box identity",timeMs,OEM_STATIC);return out;}
        if(module==0 && (code==4||code==101)) {
            add(out,code==4?"illumination":"gps_speed_kmh",raw,raw,code==4?"raw":"km/h",src,code==4?"Not high/low beam":"GPS, not CAN speed; validity/fix not supplied by callback",timeMs,OEM_STATIC);return out;
        }
        if((module==0&&code==60)||(module==10&&code==6)||(module==7&&code==1053)) {
            add(out,"key_candidate_"+module+"_"+code,raw,raw,"raw",src,"May depend on learning mode; key mapping unvalidated",timeMs,ESTIMATED);return out;
        }
        if(module!=7)return out;
        if(code==1031||code==1032) {
            add(out,code==1031?"oem_speed_callback_candidate":"oem_rpm_callback_candidate",raw,raw,"raw",src,"Not received on tested PSA profiles; scale/support unverified",timeMs,ESTIMATED);return out;
        }
        if(code==1019) {
            if(profile==null)return out;
            for(byte[] packet:stream.feed(data)) {
                PsaTelemetry t=PsaTelemetry.decode(profile,packet);
                if(t==null)continue;
                add(out,"rpm",t.rpm,t.rpm,"rpm","box:1a:13-14","Owner verified; only observed profiles",timeMs,OWNER_CONFIRMED);
                add(out,"steering_raw",t.steeringRaw,t.steeringRaw,"raw","box:1a:10-11","Movement verified; degrees/sign convention uncalibrated",timeMs,OWNER_CONFIRMED);
                add(out,"speed_kmh_estimated",t.speedKmhEstimated,t.speedRaw,"km/h","box:1a:7-8","raw/10 hypothesis; owner approximate 50–80 agrees with54.1–76.0, not synchronized calibration",timeMs,ESTIMATED);
                // Preserve unassigned payload bytes without inventing their meaning.
                for(int i:new int[]{4,5,6,9,12,15})add(out,"box1a_byte"+i,packet[i]&255,packet[i]&255,"raw","box:1a:"+i,"Unmapped byte",timeMs,RAW_UNKNOWN);
            }
            return out;
        }
        if(profile==null || (profile&65535)!=252)return out;
        if(code<0||code>257)return out;
        cache.put(code,raw);timestamps.put(code,timeMs);
        if(code==27||code==28){temperature(out,code,timeMs);return out;}
        if(code==98||code==100||code==103){consumption(out,code,timeMs);return out;}
        String name=FIELDS.get(code);
        if(name==null)name="psa_field_"+code;
        VehicleSignal.Confidence quality=name.startsWith("psa")||name.startsWith("trip")||name.equals("distance_display99_raw")?RAW_UNKNOWN:name.endsWith("candidate")?ESTIMATED:OEM_STATIC;
        add(out,name,raw,raw,"raw",src,"OEM field/state; no inferred boolean polarity or physical scale",timeMs,quality);
        // Metadata may arrive after the measured field: emit corrected interpretations too.
        if(code==242){temperature(out,27,timeMs);temperature(out,28,timeMs);}
        if(code==172)for(int c:new int[]{98,100,103})consumption(out,c,timeMs);
        return out;
    }
    private void temperature(List<VehicleSignal> out,int code,long time) {
        Integer raw=cache.get(code);if(raw==null)return;time=timestamps.get(code);
        Object value=null;String unit="OEM display degrees",note="Offset242 missing";
        if(raw<0){value=raw==-1?"NO":raw==-2?"LOW":raw==-3?"HI":null;unit="";note="OEM sentinel";}
        else if(cache.containsKey(242)){value=(raw+cache.get(242)*10)/10.0;note="OEM display formula; not assumed Celsius";}
        add(out,code==27?"left_temperature_setting":"right_temperature_setting",value,raw,unit,"binder:7:"+code,note+"; metadata may re-render cached value, not a fresh sensor reading",time,OEM_STATIC);
    }
    private void consumption(List<VehicleSignal> out,int code,long time) {
        int raw=cache.get(code)==null?-1:cache.get(code);if(raw==-1)return;time=timestamps.get(code);
        Integer mode=cache.get(172);String unit=mode==null?"":mode==2?"km/L":mode==3?"mpg":"L/100km";
        Object value=mode==null||raw==65535||raw<0?null:raw/10.0;
        add(out,code==98?"consumption_current":code==100?"consumption_trip1":"consumption_trip2",value,raw,unit,"binder:7:"+code,"OEM UI formula;65535 unavailable; metadata may re-render cached reading",time,OEM_STATIC);
    }
}
