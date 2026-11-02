package com.sleeperculture.teyes;

/** Read-only decoder scoped to observed HIWORLD PSA profiles252/46 and252/47.
 * RPM confirmed by owner against vehicle on2026-10-31; steering response confirmed,
 * but steering degrees/sign calibration remains unknown. Not generalized to other boxes.
 */
public final class PsaTelemetry {
    public final int profile, rpm, steeringRaw;
    public final int speedRaw;
    public final double speedKmhEstimated;
    public final String speedValidation="estimated_raw_div10_not_synchronized";
    /** Legacy metadata applies to rpm/steering only; speedValidation is separate. */
    public final String validation="rpm_and_steering_owner_confirmed_2026_10_31";
    private PsaTelemetry(int p,int r,int s,int v){profile=p;rpm=r;steeringRaw=s;speedRaw=v;speedKmhEstimated=v/10.0;}
    public static PsaTelemetry decode(int profile,byte[] packet) {
        if(profile!=3014908 && profile!=3080444)return null;
        if(packet==null || packet.length!=17 || (packet[0]&255)!=90 || (packet[1]&255)!=165 || (packet[2]&255)!=12 || (packet[3]&255)!=26)return null;
        int sum=-1;for(int i=2;i<16;i++)sum+=packet[i]&255;
        if((sum&255)!=(packet[16]&255))return null;
        int rpm=((packet[13]&255)<<8)|(packet[14]&255);
        int steering=(short)(((packet[10]&255)<<8)|(packet[11]&255));
        return new PsaTelemetry(profile,rpm,steering,((packet[7]&255)<<8)|(packet[8]&255));
    }
}
