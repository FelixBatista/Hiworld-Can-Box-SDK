package com.sleeperculture.teyes;

/** Static catalog metadata. This describes what a decoder may emit; it is not a live value. */
public final class SignalDefinition {
    public final String category,name,unit,source,note,scope;
    public final VehicleSignal.Confidence confidence;
    /** True when the current Binder-based Probe can observe this signal. */
    public final boolean observableByBinderProbe;

    public SignalDefinition(String category,String name,String unit,String source,String note,String scope,
                            VehicleSignal.Confidence confidence,boolean observableByBinderProbe) {
        this.category=category;this.name=name;this.unit=unit;this.source=source;this.note=note;this.scope=scope;
        this.confidence=confidence;this.observableByBinderProbe=observableByBinderProbe;
    }
}
