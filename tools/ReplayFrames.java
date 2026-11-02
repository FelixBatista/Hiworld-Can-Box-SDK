import com.sleeperculture.teyes.CanboxStream;
import java.io.*;
/** stdin: timestamp<TAB>comma-separated signed/unsigned bytes; stdout: validated packets. */
public final class ReplayFrames {
    public static void main(String[] args) throws Exception {
        CanboxStream parser=new CanboxStream();
        try(BufferedReader in=new BufferedReader(new InputStreamReader(System.in,"UTF-8"))) {
            String line;
            while((line=in.readLine())!=null) {
                if(line.equals("RESET")){parser.reset();continue;}
                String[] parts=line.split("\t",-1), fields=parts[1].split(",");
                int[] bytes=new int[parts[1].isEmpty()?0:fields.length];
                for(int i=0;i<bytes.length;i++)bytes[i]=Integer.parseInt(fields[i]);
                for(byte[] packet:parser.feed(bytes)) {
                    StringBuilder result=new StringBuilder(parts[0]).append('\t');
                    for(byte b:packet)result.append(String.format("%02x",b&255));
                    System.out.println(result);
                }
            }
        }
        System.err.println("badChecksums="+parser.badChecksums()+" discardedBytes="+parser.discardedBytes());
    }
}
