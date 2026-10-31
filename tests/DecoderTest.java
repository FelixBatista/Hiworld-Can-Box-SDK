import com.sleeperculture.teyes.Peugeot508Decoder;
public class DecoderTest {
    static void check(boolean b){if(!b)throw new AssertionError();}
    public static void main(String[] args) {
        Peugeot508Decoder d=new Peugeot508Decoder();
        check(d.accept(7,27,new int[]{215})==null);
        d.accept(7,1000,new int[]{3080444});
        check(d.profile().protocol==252 && d.profile().variant==47);
        check(!d.profile().warningFor2014().isEmpty());
        check(d.accept(7,27,new int[]{215}).value==null);
        d.accept(7,242,new int[]{1});
        check(d.accept(7,27,new int[]{215}).value.equals(22.5));
        check(d.accept(7,27,new int[]{-2}).value.equals("LOW"));
        check(d.accept(0,4,new int[]{0}).name.equals("illumination"));
        check(d.accept(7,4,new int[]{0}).name.equals("rear_right_door"));
        check(d.accept(7,123,new int[]{1})==null);
        check(d.accept(7,161,new int[]{42})==null);
        check(d.accept(7,1031,new int[]{50}).unit.isEmpty());
        d.accept(7,1000,new int[]{2949372});
        check(d.accept(7,27,new int[]{215}).value==null);
        d.reset();check(d.profile()==null);
        System.out.println("Decoder regression checks passed");
    }
}
