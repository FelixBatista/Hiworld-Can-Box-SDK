import com.sleeperculture.teyes.*;
import java.util.*;
public class VehicleDecoderTest {
    static void check(boolean b){if(!b)throw new AssertionError();}
    static VehicleSignal find(List<VehicleSignal> list,String name){for(VehicleSignal s:list)if(s.name.equals(name))return s;throw new AssertionError(name);}
    public static void main(String[] args) {
        VehicleDecoder d=new VehicleDecoder();
        check(d.acceptBinder(7,11,new int[]{1},1).isEmpty());
        d.acceptBinder(7,1000,new int[]{3014908},1);
        check(find(d.acceptBinder(7,11,new int[]{1},2),"hvac_ac").raw==1);
        check(!find(d.acceptBinder(7,27,new int[]{220},3),"left_temperature_setting").available());
        VehicleSignal temp=find(d.acceptBinder(7,242,new int[]{1},10),"left_temperature_setting");
        check(temp.value.equals(23.0));check(temp.receivedAtMs==3);check(temp.isStale(100,50));
        check(!find(d.acceptBinder(7,98,new int[]{65535},11),"consumption_current").available());
        d.acceptBinder(7,172,new int[]{1},12);
        check(find(d.acceptBinder(7,98,new int[]{87},13),"consumption_current").value.equals(8.7));
        check(find(d.acceptBinder(7,123,new int[]{1},14),"psa_field_123").confidence==VehicleSignal.Confidence.RAW_UNKNOWN);
        check(find(d.acceptBinder(0,101,new int[]{60},15),"gps_speed_kmh").unit.equals("km/h"));
        int[] packet={90,165,12,26,41,1,0,2,88,4,0,20,0,3,52,0,0};int sum=-1;for(int i=2;i<16;i++)sum+=packet[i];packet[16]=sum&255;
        VehicleSignal speed=find(d.acceptBinder(7,1019,packet,16),"speed_kmh_estimated");
        check(speed.value.equals(60.0));check(speed.confidence==VehicleSignal.Confidence.ESTIMATED);
        d.acceptBinder(7,1000,new int[]{3211603},17);check(d.acceptBinder(7,1019,packet,18).isEmpty());check(d.acceptBinder(7,27,new int[]{220},19).isEmpty());
        byte[] can={0x60,0,0,2,0,0,0,0};
        List<VehicleSignal> physical=PsaPhysicalCanExperimental.decode("AEE2010",0x128,can,20);
        check(find(physical,"high_beam").raw==1);check(find(physical,"low_beam").raw==1);check(find(physical,"parking_brake").raw==1);
        check(find(physical,"high_beam").confidence==VehicleSignal.Confidence.EXTERNAL_UNVERIFIED);
        check(PsaPhysicalCanExperimental.decode("AEE2004",0x128,can,20).isEmpty());
        check(PsaPhysicalCanExperimental.decode("AEE2010",0x1a,can,20).isEmpty());
        check(PsaPhysicalCanExperimental.decode("AEE2010",0x128,new byte[7],20).isEmpty());
        check(PsaPhysicalCanExperimental.catalog().size()==51);
        Set<String> catalogNames=new HashSet<>();
        for(SignalDefinition definition:VehicleDecoder.catalog()) {
            check(catalogNames.add(definition.name));check(definition.observableByBinderProbe);
        }
        for(SignalDefinition definition:PsaPhysicalCanExperimental.catalog()) {
            check(catalogNames.add(definition.name));check(!definition.observableByBinderProbe);
        }
        check(catalogNames.contains("rpm"));check(catalogNames.contains("psa_field_N"));check(catalogNames.contains("high_beam"));
        d.reset();check(d.acceptBinder(7,11,new int[]{1},21).isEmpty());
        System.out.println("Vehicle decoder and isolated physical-CAN tests passed ("+physical.size()+" experimental fields)");
    }
}
