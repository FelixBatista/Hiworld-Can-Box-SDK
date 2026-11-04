# Direct transport investigation — 2026-11-04

This is static analysis of the December firmware, not a test on the head unit. Paths are relative to the decompiled MS and Canbus APKs described in `FIRMWARE-DECEMBER-FINDINGS.md`.

- MS `app/App.java:318–335` initializes the MCU serial connection at 38400 or 115200 baud, depending on platform conditions. The active condition for the reference unit is not identified.
- MS `z/k.java:281–333` selects `/dev/ttyS0`, `/dev/ttyS1`, `/dev/ttyS2`, `/dev/ttyS3`, or `/dev/ttyMbx3` according to platform and starts the MCU reader with `z.i`. These are alternatives for different platforms, not a verified direct HiWorld port.
- MS `n1/e0.java:25–64` calls `JniSerial.open`, `setup`, and `read`; `com/syu/jni/JniSerial.java` loads `libjni_serial`. Setup requests 8 data bits, parity argument 78, and one stop bit. The MCU connection carries other head-unit functions too; concurrent access behavior is unknown.
- MS `z/i.java:792–824` processes outer MCU message types `0xE3` and `0x10`. The former is passed to the active CAN parser and can also be copied to callback 1019; the latter can be copied to 1019 without the same parser call. Callback 1019 alone does not distinguish these routes.
- MS `j0/ht.java:2228–2229` selects `j0.h8` for protocol 252. `h8.V2` interprets payload types and publishes fields. The Canbus APK's `Callback_0252_WC2_PSAALL` consumes published fields for its UI. Thus the firmware does contain compatibility logic: the selected profile determines which interpretation the MS service uses.

Open questions: the physical connection between HiWorld and the MCU; the selected serial device and baud on the reference unit; whether MCU firmware filters/transforms box messages; port permissions and safe concurrent observation; and whether the box offers an independently accessible interface. No port was opened and no vehicle command sent in this investigation. Do not infer physical CAN arbitration IDs from these MCU messages.
