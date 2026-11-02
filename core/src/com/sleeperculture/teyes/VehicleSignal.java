package com.sleeperculture.teyes;

/** A value plus its provenance. null value means unavailable, never implicit zero. */
public final class VehicleSignal {
    public enum Confidence { OWNER_CONFIRMED, OEM_STATIC, ESTIMATED, EXTERNAL_UNVERIFIED, RAW_UNKNOWN }
    public final String name,unit,source,note;
    public final Object value;
    public final int raw,profile;
    public final long receivedAtMs;
    public final Confidence confidence;
    public VehicleSignal(String n,Object v,int r,String u,String s,String note,int p,long t,Confidence c) {
        name=n;value=v;raw=r;unit=u;source=s;this.note=note;profile=p;receivedAtMs=t;confidence=c;
    }
    public boolean available(){return value!=null;}
    public boolean isStale(long nowMs,long maximumAgeMs){return nowMs<receivedAtMs || nowMs-receivedAtMs>maximumAgeMs;}
}
