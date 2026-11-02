package com.sleeperculture.teyes;
import java.util.*;
import static com.sleeperculture.teyes.VehicleSignal.Confidence.EXTERNAL_UNVERIFIED;

/** Separate physical-CAN input, NOT Binder fields or HiWorld command IDs.
 * Mapping facts: morcibacsi/PSACANBridge@4e644d9 CAN_128_2010.h.
 * Requires caller to explicitly identify AEE2010; not validated on this owner's bus.
 */
public final class PsaPhysicalCanExperimental {
    public static List<VehicleSignal> decode(String architecture,int arbitrationId,byte[] data,long timeMs) {
        List<VehicleSignal> result=new ArrayList<>();
        if(!"AEE2010".equals(architecture)||arbitrationId!=0x128||data==null||data.length!=8)return result;
        String[][] bitNames={
            {"drl","left_indicator","right_indicator","rear_fog","front_fog","high_beam","low_beam","parking_lights"},
            {"gear_display_blinking",null,null,null,null,null,null,null},
            {null,null,null,null,null,null,null,"recommended_gear_blinking"},
            {"parking_brake_inhibited","parking_brake",null,null,"passenger_airbag_active","child_lock","stop_warning","service_warning"},
            {"suspension_status","esp_active","esp_disabled","cafr_indicator","operation_indicator_blinking","operation_indicator","door_open","diesel_preheat"},
            {"rear_left_seatbelt_warning",null,null,"passenger_seatbelt_blinking","passenger_seatbelt_warning","driver_seatbelt_blinking","driver_seatbelt_warning","fuel_low"},
            {"passenger_protection","warning_active","cmb_active","rear_right_seatbelt_blinking","rear_right_seatbelt_warning","rear_center_seatbelt_blinking","rear_center_seatbelt_warning","rear_left_seatbelt_blinking"},
            {"personalization_change","color_change","ready_lamp","minc_blinking",null,null,null,null}
        };
        for(int b=0;b<8;b++)for(int bit=0;bit<8;bit++)if(bitNames[b][bit]!=null)add(result,bitNames[b][bit],(data[b]>>>bit)&1,b+":"+bit,timeMs);
        add(result,"gear_position_code",(data[1]>>>4)&15,"1:4-7",timeMs);
        add(result,"gear_in_drive_code",(data[1]>>>1)&7,"1:1-3",timeMs);
        add(result,"gearbox_selection_code",data[2]&3,"2:0-1",timeMs);
        add(result,"recommended_gear_direction_code",(data[2]>>>2)&3,"2:2-3",timeMs);
        add(result,"gearbox_mode_code",(data[2]>>>4)&7,"2:4-6",timeMs);
        add(result,"foot_on_brake_indicator",(data[3]>>>2)&3,"3:2-3",timeMs);
        add(result,"scr_indicator",(data[5]>>>1)&3,"5:1-2",timeMs);
        add(result,"foot_on_clutch_indicator",(data[7]>>>4)&3,"7:4-5",timeMs);
        add(result,"engine_indicator",(data[7]>>>6)&3,"7:6-7",timeMs);
        return result;
    }
    private static void add(List<VehicleSignal> out,String name,int raw,String offset,long time) {
        out.add(new VehicleSignal(name,raw,raw,"raw","physical_can:AEE2010:128:"+offset,"External mapping; not live Teyes telemetry, not vehicle-validated",-1,time,EXTERNAL_UNVERIFIED));
    }
}
