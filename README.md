# HiWorld CAN Box SDK — Peugeot 508

Library for building applications that read data from HiWorld CAN Boxes through the Teyes/SYU services exposed by the head unit. The project focuses on the **SDK and decoding layer**, rather than a launcher. Reference vehicle: Peugeot 508 SW 2014, without an amplifier; firmware MS25.1202/2125120210.

## Extended API 0.5.1

Use `VehicleDecoder`: it exposes candidates with `confidence`, `source`, `unit`, `receivedAtMs`, and `note`. See the [complete catalog](docs/SIGNAL-CATALOG.md) and the [online sources](docs/ONLINE-SOURCES.md). `speed_kmh_estimated` uses raw/10 as a hypothesis and is **not calibrated speed**. High and low beam fields belong to the separate experimental physical-CAN decoder and are not currently accessible through the Teyes connection.

```java
final VehicleDecoder decoder = new VehicleDecoder();
// Inside SyuCanClient.Listener.onEvent:
for (VehicleSignal s : decoder.acceptBinder(e.module, e.code, e.ints, e.timeMs)) {
    // s.value may be null. Preserve confidence/source and label estimates in the UI.
    // s.isStale(nowMs, 2000) uses the expiration limit chosen by the app.
}
// Create or reset the decoder when reconnecting.
```

Core 0.5.1 combines framing, the catalog, and decoding while keeping transport separate. Probe 0.5.2 uses the same `VehicleDecoder` and provides a compact live catalog screen whose scroll position stays stable during updates. The lower-level examples below remain available.

## Structure and design

- `core/`: Android-free Java 8 framing for 5AA5/AA55, checksum handling, and decoders. It can be tested on a laptop.
- `android/`: Binder transport through `SyuCanClient`, reusable in Android applications or an Android module for a Flutter app.
- `probe/`: diagnostic app 0.5.2 for capture, export, and compact live catalog inspection.
- `tools/`: JSONL analysis and test tools; `tests/`: decoder regression tests.
- `docs/`: evidence, architecture decisions, current status, and next steps.

**Current integration:** include the sources from `core/src` and `android/src` in the consuming Android module. Alternatively, compile the core JAR and use it together with `SyuCanClient.java`. The Probe does not need to run alongside your application. Maven/AAR publication, a central service, and a command API are not available yet.

```java
final CanboxStream stream = new CanboxStream();
final SyuCanClient reader = new SyuCanClient(context, new SyuCanClient.Listener() {
    public void onStatus(String message) { /* record and display disconnects */ }
    public void onEvent(SyuCanClient.Event e) {
        if (e.module == 7 && e.code == 1019 && e.profile != null) {
            for (byte[] packet : stream.feed(e.ints)) {
                PsaTelemetry sample = PsaTelemetry.decode(e.profile, packet);
                if (sample != null) {
                    // sample.rpm: confirmed by the owner.
                    // sample.steeringRaw: steering movement confirmed; degrees are not calibrated.
                    // Use e.timeMs/e.elapsedNs as the reception time of the last fragment.
                }
            }
        }
    }
}, true);
reader.connect(); // main thread; call close() when finished
```

Add `<queries><package android:name="com.syu.ms"/></queries>` to the manifest. Do not copy the OEM `sharedUserId`. The listener runs on a worker thread, so dispatch UI work to the main thread. Create a new client/parser/decoder for each connection and reset the parser when the profile changes. Do not mix streams from different devices or connections. For Flutter, adapt the listener through an EventChannel; that adapter is not implemented yet.

## Current validation

The owner confirmed RPM and steering movement on 2026-10-31. The raw decoder is restricted to the observed profiles 3014908 (2010–2018 High) and 3080444 (2019–2024), using type 0x1A. Do not generalize these mappings to other CAN boxes. Evidence and limits are documented in `docs/STATUS.md`.
General illumination changes have been observed; CAN speed is exposed as an estimate. High beam remains unknown in the Teyes data flow. GPS Main 0/101 is a separate source. HVAC, door, and trip fields are exposed with confidence levels and explicit scales. The absence of an event is not the value zero. Applications should mark old or disconnected data and show its source and unit without mixing GPS and CAN speed.

## Build and test

Requirements: JDK 9 or newer (emitting Java 8 bytecode) and Python 3, with no additional Python packages:

```text
python tools/check.py
```

This generates `dist/hiworld-can-box-sdk-0.5.1.jar`. The checks cover callback separation, profiles, temperatures, fragmentation at every point, concatenation, invalid checksums, initial sentinels, negative steering values, and basic catalog completeness. The complete Probe package can be rebuilt on Linux with JDK 21 and an `ANDROID_SDK_ROOT` containing platform 35 and build-tools 35.0.0:

```text
bash build-android.sh
```

The script produces Probe 0.5.2. Signing keys remain outside the repository in `HOME/.local/share/teyes-probe-signing`; use the same key to preserve upgrades of existing installations.

## Continuous releases

Every push to `main` builds a versioned SDK JAR and signed Probe APK, then attaches both to a GitHub Release. The one-time signing-key setup is described in [Automated releases](docs/RELEASES.md).

Full captures, OEM APKs, firmware, decompiled sources, and credentials do not belong in Git. Only a minimal, non-identifying observed fixture is included in the tests. Historical reports preserve the hypotheses recorded at the time; **`docs/STATUS.md` is the current state**, including later owner validation. The repository is private, and this project does not grant permission to redistribute OEM material.
