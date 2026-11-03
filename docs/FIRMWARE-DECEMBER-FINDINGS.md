# CC2 PLUS-EN 2025.12.2 firmware investigation

## Identity, execution, and coverage

The supplied ZIP has SHA-256 `4a7a8b91fcecf95128a9295a2c8b96d6d0c991accc4519177cd90a6bb2543254`, matching the copy already stored on the server. Originals were preserved.
`AllAppUpdate.bin` is a ZIP containing encrypted members. An updater-embedded credential was validated against a package member without running the updater; the credential is not included here. Extraction was selective and used fixed names. `6315_1.zip` was inventoried (system/product/vendor/socko, boot, and radio components); partition images were neither expanded nor mounted.

Offline analysis ran in the existing container, without network access, with 2 CPUs/8 GiB. No OEM APK was executed and no command was sent to the vehicle. Python, unzip, and JADX CLI performed extraction/decompilation; this is not evidence produced by REA. No production stack, service, or head-unit configuration changed.

Extracted MS: 25.1202/2125120210, exactly the version reported in the logs; matching the version is not proof of the installed APK hash. SHA-256: `94a96a51a56367e18817fe66e76a84424116cb97861bf553c1938fcde2c4d969`.
Canbus: versionCode 2125112518. `cc2plus.apk` is `ru.teyes.update` (the updater), not a launcher.
MS, Canbus, US, CarUI, cc2plus, com.syu.launcher, Launcher3, and launchers 4/6/8 were decompiled. MS ended with 31 errors and US with 2; CarUI/Launcher3 also reported 3, and logs were retained. Exit 0 from protected launchers does not mean their logic was recovered.

Server root: `/srv/development/firmware-lab/workspace/teyes-508-research/firmware-dec`. Paths below are relative to `decompiled/`.

## Feature map

| Feature | Confirmed source | Result |
|---|---|---|
| PSA-252 HVAC overlay | CAN 7 codes 10–96 | UI and parser path confirmed |
| Doors / hood / trunk | CAN 7 codes 0–5 | Callback 252 configures `DoorHelper` |
| Consumption / trips | CAN 7 codes 98–106, 172 | PSA pages confirmed; do not confuse them with current speed |
| GPS speed | Main 0/101 | Publication confirmed in December MS |
| CAN speed | CAN 7/1031 | No publication found in the inspected 252 path; no callback in tests |
| Raw RPM / steering | `5AA5` type `0x1A` | Capture candidates; units are not corroborated by an OEM decoder |
| Illumination | Main 0/4 | General state observed; does not distinguish high beam |
| High beam | Undefined | No specific decoder confirmed |
| Launcher 4/6/8 speedometer | Protected logic | GPS/CAN source not established |

## Air-conditioning overlay — confirmed in December

`com.syu.canbus/sources/com/syu/module/canbus/Callback_0252_WC2_PSAALL.java:185–198` configures `DoorHelper` 0–5, creates `Air_0252_WC2_PSAALL`, and registers `AirHelper.SHOW_AND_REFRESH` for 10–96. `out()` at 202+ removes callbacks.
`com.syu.canbus/sources/com/syu/ui/air/Air_0252_WC2_PSAALL.java:37–77` reads 13/55/11/53/12/65/16/18/19/20/51/21; 95 reads AQS 54; 99–118 read temperatures 27/28 and offset 242. Temperature is `(raw+offset*10)/10`; −1 is NO, −2 is LOW, and −3 is HI. The overlay uses the same Binder data available to an independent client.
`com.syu.ms/sources/j0/h8.java:795–815` receives type `0x31`. First byte: power 10=bit6; 53=bit5; 42=bit4; auto 13=bit3; dual 55=bit2; AC 11=bit0. Second: recirculation 12=bit4, AQS 54=bit3. Third: rear defrost 16=bit5, front defrost 65=bit4. Distribution modes 18/19/20 and 22/23/24 are calculated; fan 21 is at 900 and temperatures 27/28 at 905+. Do not assume every bit is supported by the vehicle.

## Vehicle Info / PSA information

`PSAOilMilePage1Acti.java:42–44` registers 98/99/172; 56+ renders decimal consumption from 98 with unit 172 (2 km/l, 3 mpg, otherwise L/100 km); 78+ renders distance 99. Page 2: 65+ consumption 100, 84+ field 102 displayed as KM, 98+ distance 101. Page 3 uses 103/105/106. These are OEM representations, not labels inferred from legacy constant names. MS `h8:1295–1313` populates 98–106 for types `0x13`/`0x14`/`0x15`.
`PSAFuncLightActi` handles settings sent by commands 14/16/17/26. Its existence does not confirm the current high-beam state. A text search limited to PSA consumers for high-beam/远光 found no explicit path.

## GPS and launcher

`com.syu.ms/sources/n1/i.java:346–348` uses `Location.getSpeed()*3.6 -> t0.d.u0`. `t0/d.java:3958–3962` stores W1 and publishes Main 101. That is GPS, not CAN speed.
`com.syu.launcher` is a launcher selector: `MainActivity.java:58–66` identifies 4/6/8; 162+ changes `persist.launcher.packagename`. It is not the speedometer consumer.
Launchers 4/6/8 yield `R.java`, kdp/AR, and `com/kiwivm/security/StubApplication`. In launcher 4, `StubApplication:128–133` loads a native library and 167/170 call native `attachBaseContext`/`onCreate`. A `car_speed` resource exists, but its consumer does not occur in the recovered DEX. Do not claim GPS speed merely by finding its publisher. Following this path requires deeper analysis of the native loader/protection or observation of the actually installed launcher at runtime. No ADB/runtime access to the head unit was established in this session.

## Raw messages and limitations

MS `j0/ht.java:2228–2229` selects h8 for 252. `h8.V2:707` neither handles type 26 nor literally publishes 1031/1032; `ct.V2:60` is empty. `z/i.java:792–804` passes MCU `0xE3` to the parser and may copy 1019; 809–824 copies 1019 for MCU `0x10` without calling h8. Callback 1019 has no explicit external-route identity.
`k0/o.java:j:523+` handles the `5AA5`/`AA55` flow, length `byte2+5`, and checksum `(sum bytes 2 through penultimate − 1)&255` at 545–550/585–594. Type `0xF0` extracts an ASCII identifier and publishes 1051 at 554–555/599–600. It corroborates framing inferred from captures but provides no RPM/steering semantics.
The existing captures had an RPM candidate in bytes 13/14 big-endian (857–2897 and 819–4955) and a signed steering candidate in bytes 10/11 (−226..220 in the second test). Do not promote candidates to calibrated measures. Do not conclude that changing profile enabled them: type `0x1A` already exists in both.

## Grounded next work

Reuse proven HVAC/door/trip fields; add GPS explicitly as GPS rather than silently substituting it for CAN. Implement `5AA5` stream reconstruction with validation before decoding `0x1A`. Keep high beam, CAN speed, and steering/RPM units as gaps. Investigate the launchers' native loader to determine the speedometer consumer. No new Probe version was made at this step.

Rollback: the analysis only creates `firmware-dec` and investigation scripts; there is no operational change to revert. Large files remain in the lab's restricted workspace, not production volumes. Do not delete captures or previous applications.
