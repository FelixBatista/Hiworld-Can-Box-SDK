# Status and continuity — updated 2026-11-03

Direct transport investigation on 2026-11-04 traced MCU serial-port selection and profile 252's parser in MS; the physical box-to-MCU connection and independent access remain unverified. See [DIRECT-TRANSPORT-INVESTIGATION.md](DIRECT-TRANSPORT-INVESTIGATION.md).

**Current status:** Core 0.5.1 / Probe 0.5.2. `VehicleDecoder` exposes estimated speed (raw/10), HVAC/doors/consumption, and unknown raw fields. `VehicleSignal` carries unit, source, timestamp, and confidence. The Probe catalog shows only name and value on one line and preserves scroll position during updates; complete metadata remains in the SDK and JSONL. Signals never received, unavailable signals, and zeros remain distinct. A separate experimental decoder includes 51 AEE2010 physical-CAN fields, including lights; they appear as unavailable in the catalog because they are not connected to the Binder transport. See `SIGNAL-CATALOG.md` and `ONLINE-SOURCES.md`.

Update 2026-11-03: the `Release SDK and Probe` workflow prepares a JAR and APK for every commit on `main` and publishes a GitHub Release with a unique tag. It requires the `HIWORLD_PROBE_SIGNING_P12_BASE64` and `HIWORLD_PROBE_SIGNING_PASSWORD` secrets, containing the key already used by the installed Probe; details are in [RELEASES.md](RELEASES.md). Without those secrets, the workflow fails before publishing.

Update 2026-11-02: the urban capture is analyzed in [DRIVE-20261102.md](DRIVE-20261102.md). RPM was 1216–1704 and raw steering −18..16. A new speed candidate is type `0x1A` bytes 7/8 BE, raw 541–760, and zero in earlier trials. Its scale is not validated and it remains outside the speed API in that historical report. Replay now uses the Java parser directly. Nine checksum failures were observed and are not confused with application queue loss.

## Decision

The owner's 2026-11-02 feedback was an approximate remembered speed of 50–80 km/h, compatible with raw/10 = 54.1–76.0 km/h. The scale is plausible but remains unvalidated without a simultaneous reference. No additional information about stops was provided. The API remains unchanged.

The SDK is embeddable: pure Java core + Android transport + separate Probe. An installed central service is deferred until there is a demonstrated need to share a connection among several apps. MQTT, server, and network are not required. A launcher is only a possible consumer and is outside the current scope.

## Signals

| Signal | Status | Source |
|---|---|---|
| RPM | Owner-confirmed, 2026-10-31 | `5AA5` type 1A bytes 13/14 BE; profiles 252/46 and 47 |
| Steering | Owner-confirmed movement; physical degrees/direction not calibrated | type 1A bytes 10/11 signed-16 BE |
| Illumination | Switching observed | Main 0/4; not equivalent to high beam |
| HVAC/doors | OEM parser/UI traced | CAN 7 fields 0–5, 10–96, 242 |
| Consumption/trips | OEM UI/parser traced | CAN 7 fields 98–106, 172 |
| GPS speed | Static publication confirmed | Main 0/101 |
| CAN speed / high beam / keys | Not fully decoded/validated | Further research required |

The two original JSONLs are cumulative. Never count the second as an entirely new session. New sessions: profile 3080444 = 722 callbacks / 41.3 s; 3014908 = 700 callbacks / 35.8 s. No drops were reported. The owner later confirmed RPM/steering fields; this supersedes historical reports that still call them candidates.

## Technical limits

1019 is an MCU/CAN-box flow, not a sniffer for all CAN IDs. The parser accepts `5AA5`/`AA55` headers; the PSA decoder accepts only `5AA5`. Maximum packet size is 260 bytes. Partial frames wait for future bytes; disconnect/profile change requires reset. The checksum validates framing, not all physical data. There is no certified automotive precision/latency. Subscribing to 1019 activates an OEM copy flag that may persist after unregister. No actuation commands are implemented.

## Ordered next steps

1. Validate Probe 0.5.2 on the head unit: stable scrolling, compact catalog, live values, reconnect update, and export; preserve the prior capture.
2. Collect a speed reference synchronized with `speed_kmh_estimated` and `gps_speed_kmh`; keep the two sources separate.
3. Investigate a real physical-CAN transport before using AEE2010 fields, including high beam; do not interpret 1019 as an arbitration ID.
4. Calibrate steering in degrees/direction and record box/firmware/profile per test.
5. Package an Android AAR and Kotlin/Flutter example when the API stabilizes. Maintain versioned releases, a changelog, and a compatibility matrix.

## Workflow

Every discovery must record origin (capture/OEM code/human validation), profile and version, offsets/units/sentinels, and limitations. Implement it in core, add a minimal-fixture test, validate it, then update Probe/examples. New apps import a pinned SDK version and do not duplicate decoders. Unit/name changes require a changelog and consumer review. Before expanding profiles, obtain profile-specific evidence.

Original files remain in the existing lab; documents reference historical paths for traceability, but builds do not depend on them. December firmware SHA-256: `4a7a8b91fcecf95128a9295a2c8b96d6d0c991accc4519177cd90a6bb2543254`. MS APK SHA-256: `94a96a51a56367e18817fe66e76a84424116cb97861bf553c1938fcde2c4d969`. No repeat decompilation is needed.
