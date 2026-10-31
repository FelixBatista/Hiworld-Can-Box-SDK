import com.sleeperculture.teyes.*;
import java.util.*;
public class StreamTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    // Minimal observed packet from the owner's capture: rpm820, steering20.
    static final int[] SAMPLE={90,165,12,26,41,1,0,0,0,4,0,20,0,3,52,0,158};
    public static void main(String[] args) {
        for(int split=0;split<=SAMPLE.length;split++) {
            CanboxStream s=new CanboxStream();List<byte[]> result=new ArrayList<>();
            result.addAll(s.feed(Arrays.copyOfRange(SAMPLE,0,split)));
            result.addAll(s.feed(Arrays.copyOfRange(SAMPLE,split,SAMPLE.length)));
            check(result.size()==1);
            PsaTelemetry t=PsaTelemetry.decode(3014908,result.get(0));
            check(t!=null && t.rpm==820 && t.steeringRaw==20);
            check(PsaTelemetry.decode(2949372,result.get(0))==null);
        }
        int[] multiple=new int[SAMPLE.length*2];System.arraycopy(SAMPLE,0,multiple,0,17);System.arraycopy(SAMPLE,0,multiple,17,17);
        check(new CanboxStream().feed(multiple).size()==2);
        int[] bad=SAMPLE.clone();bad[16]^=1;CanboxStream s=new CanboxStream();check(s.feed(bad).isEmpty());check(s.badChecksums()==1);check(s.feed(SAMPLE).size()==1);
        s.reset();check(s.feed(new int[]{0}).isEmpty());check(s.feed(SAMPLE).size()==1);
        int[] neg=SAMPLE.clone();neg[10]=255;neg[11]=234;int sum=-1;for(int i=2;i<16;i++)sum+=neg[i];neg[16]=sum&255;
        byte[] packet=new CanboxStream().feed(neg).get(0);check(PsaTelemetry.decode(3080444,packet).steeringRaw==-22);
        check(PsaTelemetry.decode(3014908,new byte[0])==null);
        System.out.println("Stream/telemetry regression tests passed");
    }
}
