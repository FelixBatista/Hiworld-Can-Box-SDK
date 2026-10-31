package com.sleeperculture.teyes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Stateful 5AA5/AA55 box framing. One instance per connection, reset on reconnect.
 * Feed only module7/code1019; arrays may contain partial or multiple packets.
 * No CAN arbitration IDs are implied by the packet type.
 */
public final class CanboxStream {
    private final byte[] buffer=new byte[260];
    private int size;
    private long badChecksums, discardedBytes;
    public long badChecksums(){return badChecksums;}
    public long discardedBytes(){return discardedBytes;}
    public void reset(){size=0;badChecksums=0;discardedBytes=0;}
    public List<byte[]> feed(int[] input) {
        List<byte[]> packets=new ArrayList<>();
        if(input==null)return packets;
        for(int value:input) {
            buffer[size++]=(byte)value;
            while(size>=2) {
                boolean header=(buffer[0]==0x5a && (buffer[1]&255)==0xa5)||((buffer[0]&255)==0xaa && buffer[1]==0x55);
                if(!header){drop(1);discardedBytes++;continue;}
                if(size<3)break;
                int length=(buffer[2]&255)+5;
                if(size<length)break;
                int sum=-1;for(int i=2;i<length-1;i++)sum+=buffer[i]&255;
                if((sum&255)!=(buffer[length-1]&255)){badChecksums++;discardedBytes++;drop(1);continue;}
                packets.add(Arrays.copyOf(buffer,length));drop(length);
            }
        }
        return packets;
    }
    private void drop(int n){System.arraycopy(buffer,n,buffer,0,size-n);size-=n;}
}
