# Catalog 0.5 — scope and uncertainty

## Contract

`VehicleDecoder.acceptBinder(module, code, ints, timeMs)` returns a list of `VehicleSignal`. Use one instance per connection and reset on reconnect. Profile 1000 clears caches and incomplete frames. Fields that were not received do not appear; `value=null` means unavailable. Metadata 242/172 may re-emit an already received temperature/consumption value: it retains the original measurement timestamp and does not simulate new data. Metadata cache lasts until reset/profile change. `receivedAtMs` is the reception time, not a physical sensor timestamp; an initial callback may come from the OEM cache. `isStale(now, limit)` lets the app define expiry.

`VehicleDecoder.catalog()` describes every known Binder signal before reception. `PsaPhysicalCanExperimental.catalog()` separately describes 51 external physical-CAN signals. `SignalDefinition.observableByBinderProbe=false` prevents an interface from presenting those external signals as telemetry available in the Probe. `psa_field_N` is a model for not-yet-named codes; upon receiving a real code, the decoder preserves its numeric name.

Confidence:

- `OWNER_CONFIRMED`: RPM and steering response were confirmed by the owner; steering remains raw.
- `OEM_STATIC`: interpretation reconstructed from supplied code, without guaranteeing support/calibration in this vehicle.
- `ESTIMATED`: deliberately presented hypothesis; the UI must visibly label it as an estimate.
- `EXTERNAL_UNVERIFIED`: public mapping from another implementation, unvalidated on this vehicle.
- `RAW_UNKNOWN`: no safe physical interpretation.

## Available transport: Teyes Binder

| Fields | Source | Unit / confidence / limitation |
|---|---|---|
| `rpm`, `steering_raw` | `5AA5` type `1A`, 13/14 and 10/11 BE | RPM confirmed; steering is signed-16 raw, not degrees |
| `speed_kmh_estimated` | type `1A`, 7/8 BE divided by 10 | `ESTIMATED`; owner's remembered range is compatible, not synchronized calibration |
| `box1a_byte4,5,6,9,12,15` | unassigned bytes | `RAW_UNKNOWN`; no invented gear or headlamp names |
| `gps_speed_kmh` | Main 0/101 | km/h, `OEM_STATIC`; neither CAN speed nor proof of GPS fix |
| `illumination` | Main 0/4 | raw state, not high/low beam/DRL |
| `key_candidate_0_60,10_6,7_1053` | Main/Steer/CAN | `ESTIMATED`; may depend on learning mode |
| `profile` | CAN 7/1000 | profile catalog, not physical box identification |
| `oem_speed_callback_candidate,oem_rpm_callback_candidate` | CAN 7/1031,1032 | `ESTIMATED`, raw; not received in tests, scale not assumed |
| `hood,front_left/right_door,rear_left/right_door,trunk` | CAN 7/0–5 | raw OEM states; do not normalize unverified polarity |
| `hvac_power,ac,recirculation,auto` | 7/10,11,12,13 | `OEM_STATIC`, raw states |
| `rear_defrost,front_defrost` | 7/16,65 | `OEM_STATIC` |
| `left_air_distribution18/19/20,right_air_distribution22/23/24` | 7/18–20,22–24 | OEM bits; physical distribution names are not imposed |
| `left_fan_raw,hvac_mode_raw` | 7/21,51 | OEM level/mode; neither airflow nor percentage |
| `hvac_max_ac_candidate,hvac_aqs,hvac_dual` | 7/53,54,55 | 53 estimated; the others `OEM_STATIC` |
| `left/right_temperature_setting` | 7/27,28 + offset 242 | OEM display degrees; do not assume °C; −1 NO, −2 LOW, −3 HI; missing offset => `null` |
| `consumption_current,trip1,trip2` | 7/98,100,103 + unit 172 | raw/10; 172=2 km/L, 3 mpg, otherwise L/100 km according to UI; 65535=>`null`; missing unit=>`null` |
| `distance_display99_raw,trip1_distance_display_raw,trip1_secondary_display_raw,trip2_secondary_display_raw,trip2_secondary2_display_raw,trip2_distance_display_raw` | 7/99,101,102,104,105,106 | `RAW_UNKNOWN`: UI representation documented; exact scale/function not calibrated |
| `psa107_unknown,psa192_unknown` | 7/107,192 | do not adopt conflicting OEM constant names |
| `panel_brightness_candidate` | 7/197 | `ESTIMATED`; brightness candidate, not high beam |
| `consumption_unit_code,temperature_offset` | 7/172,242 | OEM metadata |
| `psa_field_N` | other 7/0–257 | `RAW_UNKNOWN`; preservation, not semantic mapping |

PSA Binder mappings are limited to protocol 252; 1A frames are limited to observed profiles 3014908/3080444. Other variants do not inherit the 1A interpretation. The legacy `Peugeot508Decoder` API remains available, but `VehicleDecoder` is recommended. `SyuCanClient` also subscribes to Main 101. It sends no commands.

## Separate input: experimental physical CAN

`PsaPhysicalCanExperimental.decode("AEE2010", 0x128, payload8, timeMs)` requires exactly 8 bytes and explicit architecture. Do **not** pass 1019 or type `0x1A` here: they are not arbitration IDs. No physical CAN hardware/transport is implemented.

51 fields derive from public PSACANBridge definitions: DRL, turn signals, fogs, high/low/position lights; gear codes, transmission selection/mode/recommendation; parking brake/inhibition, brake-pedal indication; passenger airbag, child lock, STOP/service warnings; suspension, ESP active/disabled; door warnings, diesel preheat; front/rear belts; low fuel, SCR; clutch/engine/ready and other explicitly named flags. All are `EXTERNAL_UNVERIFIED` and raw, including multi-bit codes. A dashboard indicator is not necessarily the physical state of an actuator (for example, `foot_on_brake_indicator` and `stop_warning`).

High beam is byte 0 bit 5; low beam is byte 0 bit 6. This is a fact of the external AEE2010 mapping and **does not** prove those bits exist in the current HiWorld flow. Names/offsets are in `PsaPhysicalCanExperimental.java` and the pinned source in `ONLINE-SOURCES.md`. Tests verify the implementation, not the vehicle.
