package com.sleeperculture.teyes;

import java.util.HashMap;
import java.util.Map;

/** Pure Java semantic layer over SYU callbacks. No Android or vehicle writes.
 * Values are OEM representations, not independently calibrated measurements.
 * One instance per connection; reset when reconnecting to avoid cached offsets.
 */
public final class Peugeot508Decoder {
    public static final class Profile {
        public final int id, protocol, variant;
        public final String name;
        public Profile(int id) {
            this.id=id;protocol=id&65535;variant=(id>>>16)&65535;
            switch(id) {
                case 2949372:name="HIWORLD Peugeot 508 2010–2018 Low";break;
                case 3014908:name="HIWORLD Peugeot 508 2010–2018 High";break;
                case 3080444:name="HIWORLD Peugeot 508 2019–2024";break;
                case 3211603:name="Raise Peugeot 508 2010";break;
                case 196947:name="Raise Peugeot 508L 2019";break;
                case 186:name="Raise Peugeot 508 legacy";break;
                case 786684:name="HIWORLD Peugeot 508 legacy AMP";break;
                default:name="Perfil não catalogado";
            }
        }
        public String warningFor2014() {
            return id==3080444||id==196947 ? "Perfil catalogado para 2019+. Conferir configuração do 508 2014; não alterada pelo app." : "";
        }
    }
    public static final class Signal {
        public final int module,code,raw;
        public final String name,quality,unit;
        public final Object value;
        Signal(int module,int code,int raw,String name,Object value,String unit,String quality) {
            this.module=module;this.code=code;this.raw=raw;this.name=name;this.value=value;this.unit=unit;this.quality=quality;
        }
        @Override public String toString(){return name+" = "+value+(unit.isEmpty()?"":" "+unit)+" ["+quality+"]";}
    }
    private Profile profile;
    private final Map<Integer,Integer> values=new HashMap<>();
    public Profile profile(){return profile;}
    public void reset(){profile=null;values.clear();}
    public Signal accept(int module,int code,int[] ints) {
        if(ints==null||ints.length==0)return null;
        int raw=ints[0];
        if(module==7 && code==1000) {
            if(profile==null||profile.id!=raw)values.clear();
            profile=new Profile(raw);
            return signal(module,code,raw,"profile",profile.name,"","database");
        }
        if(module==0 && code==4)return signal(module,code,raw,"illumination",raw,"","oem_state_not_headlight_type");
        if(module==0 && code==60 || module==10 && code==6 || module==7 && code==1053)
            return signal(module,code,raw,"steering_key_candidate",raw,"","unvalidated_key_mapping");
        if(module!=7)return null;
        if(code==1031||code==1032)return signal(module,code,raw,code==1031?"speed_candidate":"rpm_candidate",raw,"","support_and_scale_unverified");
        if(profile==null||profile.protocol!=252)return null;
        values.put(code,raw);
        String[] doors={"hood","front_left_door","front_right_door","rear_left_door","rear_right_door","trunk"};
        if(code>=0 && code<doors.length)return signal(module,code,raw,doors[code],raw,"","oem_state");
        if(code==27||code==28) {
            String name=code==27?"left_temperature_setting":"right_temperature_setting";
            if(raw<0) return signal(module,code,raw,name,raw==-1?"NO":raw==-2?"LOW":raw==-3?"HI":"UNKNOWN","","oem_sentinel");
            Integer offset=values.get(242);
            return signal(module,code,raw,name,offset==null?null:(raw+offset*10)/10.0,offset==null?"":"OEM display degrees",offset==null?"missing_offset_242":"oem_ui_formula");
        }
        return null;
    }
    private static Signal signal(int m,int c,int r,String n,Object v,String u,String q){return new Signal(m,c,r,n,v,u,q);}
}
