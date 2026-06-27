# Smart Ring Hardware Reference

> Compiled from project documentation, web research, product pages, and teardowns.
> Last updated: 2026-06-25

## Supported Rings — Hardware Specs

|  | 56ff / Jring | Colmi R02/R03/etc | Colmi R10 | Colmi R12 | Colmi R11 |
|---|---:|---:|---:|---:|---:|
| **SoC** | Renesas DA14531 | Realtek RTL8762 | RTL8762 ESF | Realtek RTL8762 | Realtek AB2026 |
| **Architecture** | ARM Cortex-M0 | ARM | ARM | ARM | ARM |
| **Bluetooth** | BLE 5.x | BLE 5.0 | BLE 5.0 | BLE 5.0 | BLE 5.0 |
| **PPG sensor** | Unknown (HR/SpO₂) | Unknown | Vcare VC30F | Vcare VC30F | Vcare VC30F |
| **PPG LEDs** | Unknown | Unknown | Red + green (dual) | Red + green (dual) | Red + green (dual) |
| **Accelerometer** | Yes | Unknown | STK8321 | ST LIS2DOC | STK8321 |
| **Skin temperature** | ❌ | Unknown | ✅ | ✅ | ✅ |
| **Battery** | Unknown | Varies | 17 mAh | 15–18 mAh | 15–18 mAh¹ |
| **Battery life** | Unknown | Varies | ~4–7 days | ~4–7 days | ~4–7 days |
| **Charging case** | No | No | ✅ 200 mAh | No | ✅ 200 mAh |
| **Display** | No | No | No | ✅ Yes | No |
| **Waterproof** | Varies by seller | IP68 / 3ATM | 5ATM | IP68 + 1ATM | IP68 + 5ATM |
| **Weight** | Unknown | Unknown | Unknown | ~4 g | Unknown |
| **Price** | $7–12 | $15–25 | $15–25 | ~$30 | ~$15–25 |
| **Protocol** | Custom 56ff | Nordic-UART QRing | Nordic-UART QRing | Nordic-UART QRing | Nordic-UART QRing² |
| **Frame size** | Fixed 20 bytes | 16 bytes (checksum) | 16 bytes (checksum) | 16 bytes (checksum) | 16 bytes (checksum) |
| **Encryption** | None (cleartext) | None | None | None | None |
| **FW OTA** | ✅ Renesas SUOTA | ✅ BLE OTA (no sign) | ⚠️ Unknown | ⚠️ Unknown | ⚠️ Unknown |
| **Custom firmware** | ✅ (SR08 ref) | ✅ (RF03 ref) | ❓ | ❓ | ❓ |
| **PulseLoop support** | ✅ | ✅ | ✅ | ✅ | ✅ |

¹ 15 mAh for sizes 8–9, 18 mAh for sizes 10–13.
² Works with the QRing app; also has a companion "Da Rings" app. Matched by Colmi driver.

## Supported Rings — Capabilities

| Capability | 56ff / Jring | Colmi R02/etc | Colmi R10 | Colmi R12 | Colmi R11 |
|---|---:|---:|---:|---:|---:|
| Heart rate — spot | ✅ | ✅ | ✅ | ✅ | ✅ |
| Heart rate — history | ✅ | ✅ | ✅ | ✅ | ✅ |
| Heart rate — live | ✅ | ✅ | ✅ | ✅ | ✅ |
| SpO₂ — history | ✅ | ✅ | ✅ | ✅ | ✅ |
| SpO₂ — spot | ✅ | ❌¹ | ❌¹ | ❌¹ | ❌¹ |
| Steps / distance / calories | ✅ | ✅ | ✅ | ✅ | ✅ |
| Sleep (light/deep/awake) | ✅ | ✅ | ✅ | ✅ | ✅ |
| REM sleep | ❌ | ✅ | ✅ | ✅ | ✅ |
| Blood pressure | ✅² | ❌ | ❌ | ❌ | ❌ |
| Blood sugar | ✅³ | ❌ | ❌ | ❌ | ❌ |
| HRV | ✅ | ✅ | ✅ | ✅ | ✅ |
| Stress | ✅ | ✅ | ✅ | ✅ | ✅ |
| Fatigue | ✅ | ✅ | ✅ | ✅ | ✅ |
| Skin temperature | ❌ | ✅ | ✅ | ✅ | ✅ |
| Battery level | ✅ | ✅ | ✅ | ✅ | ✅ |
| Find device | ✅ | ✅ | ✅ | ✅ | ✅ |
| Continuous background sync | ❌ | ✅ | ✅ | ✅ | ✅ |
| FW update via app | ✅ | ✅ | ⚠️ | ⚠️ | ⚠️ |

¹ Colmi family has no on-demand SpO₂ reading; SpO₂ is all-day background only.
² Direct PPG sensor reading, no user profile required.
³ Profile-derived estimate from sex/age/height/weight, not a real glucometer reading.

## Not Supported by PulseLoop

| Ring | Reason |
|---|---|
| **SIMSONLAB LA380-YJ** | Unknown protocol (PHY6222 SoC), no reverse engineering |
| **Oura Gen 3/4** | Encrypted BLE, proprietary protocol, subscription required |
| **Ultrahuman Ring Air** | Not yet implemented (protocol is documented) |
| **RingConn Gen 2** | No public protocol, no reverse engineering |

---

## Platform Overview

Multiple hardware platforms span from $7 commodity rings to $350 premium devices:

### Budget / Commodity Rings

| Platform | SoC | Protocol | App | Price | Hackable |
|---|---|---|---|---|---|
| **56ff / Jring** | Renesas DA14531 | Custom 56ff (SXR KeepFit SDK) | Jring / KeepFit | $7–12 | ✅ App + FW |
| **Colmi / Yawell (QRing)** | Realtek RTL8762 family | Nordic-UART (QRing) | QRing | $15–30 | ✅ App (R02 FW too) |
| **Colmi R11** | Realtek AB2026 | Nordic-UART QRing | QRing / Da Rings | ~$15–25 | ✅ App (untested) |
| **SIMSONLAB** | Phyplus PHY6222 | Unknown | SIMSONLAB app | ~$10–20 | ❌ |

### Premium Rings

| Platform | SoC | Protocol | App | Price | Subscription | Hackable |
|---|---|---|---|---|---|---|
| **Oura Gen 4** | Nordic nRF52840 | Encrypted proprietary | Oura app | $349 | **$5.99/mo required** | ❌ |
| **Ultrahuman Ring Air** | nRF52840 + STM32G0 | Documented (Gadgetbridge) | Ultrahuman | $349 | ❌ None | ✅ App protocol |
| **RingConn Gen 2 Air** | Unknown (Nordic likely) | Proprietary | RingConn | $199 | ❌ None | ❌ |

---

## 1. 56ff / Jring Platform

### Manufacturer

- **keeprapid.com** (ShenXinRui / 深新锐) — Chinese OEM
- White-labels under brands: **Jring, KeepFit, JYouPro, RWfit, Tag**
- SDK: SXR KeepFit SDK (`com.sxr.sdk.ble.keepfit`)

### Hardware

| Component | Detail |
|---|---|
| **SoC** | Renesas DA14531 (Dialog DA145XX family) |
| **SoC architecture** | ARM Cortex-M0 |
| **PPG sensor** | Unknown (PPG HR/SpO₂, no skin temperature) |
| **Accelerometer** | Yes (steps, sleep stages, activity) |
| **Memory** | Unknown |
| **Weight** | Unknown |
| **Waterproof** | Unknown (varies by seller) |

### Protocol

| Property | Value |
|---|---|
| **Service UUID** | `000056ff-0000-1000-8000-00805f9b34fb` |
| **Write characteristic** | `000033f3-...` |
| **Notify characteristic** | `000033f4-...` |
| **Additional chars** | `0x33F5`, `0x33F6` (purpose unknown) |
| **Secondary service** | `0x57FF` |
| **SUOTA service** | `0000fef5-...` (firmware OTA) |
| **Frame size** | Fixed 20 bytes |
| **Encryption** | None (cleartext) |
| **Standard BLE services** | DIS (`0x180A`), Heart Rate Service (`0x180D`) |

### Capabilities

- Heart rate (BPM) — spot + live stream + history
- SpO₂ (%) — spot + history
- Steps / distance / calories
- Sleep stages: awake, light, deep (no REM)
- Blood pressure: systolic + diastolic (via `0x23`/`0x24` combined measurement) — **direct sensor reading, no user profile required**
- Blood sugar (profile-derived estimate, not real glucometer) — **requires user profile** (sex/age/height/weight via `0x02` `CMD_SET_USER_INFO`); changing the profile changes the value
- Stress (0–100)
- Fatigue (0–100)
- HRV (ms)
- Battery level
- Find device

### What the 56ff ring CANNOT do

- REM sleep detection
- Body temperature (no skin temperature sensor — official app shows 0°C/32°F placeholder)
- Continuous streaming (command-response only, ~20s idle timeout)

### Known models

- **SR08** — open-source reference hardware ([atc1441/ATC_SR08_Ring](https://github.com/atc1441/ATC_SR08_Ring))
- Generic "SMART_RING" — sold on AliExpress for $7–12

### Firmware

- Firmware check: `http://download.keeprapid.com/apps/smartband/jring/autoupdater/{device_id}/update.json`
- Binary download: `http://download.keeprapid.com:8181/docs/jring/an_{p1}_{p2}1`
- Flashing: Renesas SUOTA (`0xFEF5` service) to DA14531
- Version format: `"003A002AV138"` (status hex + V + version number)
- **All API traffic is HTTP (not HTTPS) — unencrypted**

### Background behavior

- Idle timeout: ~20 seconds of inactivity
- Keepalive: `0x3A` ping/pong
- Binding: `0x4B` custom protocol (NOT OS `createBond()`)
- Official app runs periodic background sync

---

## 2. Colmi / Yawell — QRing Platform

### Manufacturer

- **Shenzhen Yawell Intelligent Technology Co., Ltd.** (est. 2016, Shenzhen)
- 3 factories, 16 assembly lines, 500+ workers, 100+ R&D engineers
- Largest smart ring factory in South China (5,000 m², established 2024)
- 150K+ monthly smart ring shipments
- OEM/ODM for Lenovo, Nokia, Skyworth, Noise, Titan, Fire Boltt
- First smart ring launched: 2023
- Official app: **QRing** (by Yawell)
- **Colmi** (Shenzhen Colmi Technology Co., Ltd.) is the most popular licensed brand selling Yawell's QRing rings
- Website: [yawellfit.com](https://www.yawellfit.com/), [colmi.com](https://www.colmi.com/)

### Protocol

| Property | Value |
|---|---|
| **BLE family** | Nordic-UART (`6e40fff0` / `de5bf728`) |
| **App** | QRing |
| **Encryption** | None |

### Models — QRing platform

| Model | CPU | Bluetooth | Battery | Waterproof | Display | Sensors | Notes |
|---|---|---|---|---|---|---|---|
| **R02** | Realtek RTL8762 | BLE 5.0 | Varies | IP68/3ATM | No | Unknown | Entry-level, "highly supported" per Gadgetbridge |
| **R03** | Realtek RTL8762 | BLE 5.0 | Varies | Unknown | No | Unknown | |
| **R06** | Realtek RTL8762 | BLE 5.0 | Varies | Unknown | No | Unknown | |
| **R07** | Realtek RTL8762 | BLE 5.0 | Varies | Unknown | No | Unknown | |
| **R09** | Realtek RTL8762 | BLE 5.0 | Varies | Unknown | No | Unknown | |
| **R10** | RTL8762 ESF | BLE 5.0 | 17 mAh | 5ATM | No | Vcare VC30F + STK8321 | Charging case: 200 mAh |
| **R12** | Realtek RTL8762 | BLE 5.0 | 15/18 mAh | IP68 + 1ATM | Yes | Vcare VC30F + ST LIS2DOC | Newest (2025), 4g weight |

### Yawell-branded QRing models

- R05, R10, R11, H59 — all use the same QRing protocol

### Colmi R11 — QRing-Compatible with Fidget Shell

The Colmi R11 uses a Realtek AB2026 SoC rather than the RTL8762 found in other QRing models,
but speaks the same Nordic-UART QRing protocol. It pairs with both the **Da Rings** app and the
**QRing** app.

| Component | Detail |
|---|---|
| **CPU** | Realtek AB2026 |
| **Bluetooth** | BLE 5.0 |
| **PPG sensor** | Vcare VC30F (red + green dual LED) |
| **Accelerometer** | STK8321 (3-axis MEMS) |
| **Battery** | 15 mAh (sizes 8–9) / 18 mAh (sizes 10–13) |
| **Charging case** | 200 mAh |
| **Waterproof** | IP68 + 5ATM |
| **Build** | Stainless steel casing with fidget-spinner outer shell |
| **Apps** | Da Rings or QRing (Android 5.1+ / iOS 12.0+) |

PulseLoop matches R11 rings via the `R11C?_[0-9A-F]{4}$` pattern in the Colmi QRing driver.
Capabilities should match the R10 (same VC30F + STK8321 sensor pair).

---

## 3. Colmi/Yawell — Capabilities

### Sensor details: Vcare VC30F

The VC30F is the PPG bio-sensor used in R10 and R12:

- **Red + green LED emitters** — dual wavelength for HR and SpO₂
- **Integrated photodiode** — detects reflected light with ambient light rejection
- **Analog front-end (AFE)** — filters and amplifies raw signal
- **Digital controller** — outputs processed pulse data
- Available on JLCPCB's parts library (traceable component)
- Real-world accuracy: within 1 BPM of medical-grade BP monitor (per R12 review)

### Sensor details: ST LIS2DOC (R12) / STK8321 (R10)

3-axis MEMS accelerometer for:
- Step counting and gesture detection
- Wear detection (wake on motion)
- Raw acceleration data for sleep and activity algorithms

### Capabilities per model

| Capability | R10 | R12 | R11 | Other QRing¹ |
|---|---|---|---|---|
| **Heart rate — spot** | ✅ | ✅ | ✅ | 🧪 |
| **Heart rate — history** | ✅ | ✅ | ✅ | 🧪 |
| **Heart rate — live** | ✅ | ✅ | ✅ | 🧪 |
| **SpO₂ — history** | ✅ | ✅ | ✅ | 🧪 |
| **SpO₂ — spot** | —² | —² | —² | —² |
| **Steps / distance / calories** | ✅ | ✅ | ✅ | 🧪 |
| **Sleep stages** (light/deep/awake) | ✅ | ✅ | ✅ | 🧪 |
| **REM sleep** | ✅ | ✅ | ✅ | 🧪 |
| **HRV** | ✅ | ✅ | ✅ | 🧪 |
| **Stress** | ✅ | ✅ | ✅ | 🧪 |
| **Body temperature** | ✅ | ✅ | ✅ | 🧪 |
| **Battery level** | ✅ | ✅ | ✅ | 🧪 |
| **Find device** | ✅ | ✅ | ✅ | 🧪 |
| **Blood pressure** | ❌ | ❌ | ❌ | ❌ |
| **Blood sugar** | ❌ | ❌ | ❌ | ❌ |

¹ R02, R03, R06, R07, R09 + Yawell R05, R10, R11, H59
² Colmi family has no on-demand SpO₂ reading; SpO₂ is all-day background only
³ Colmi has no blood pressure or blood sugar support. Its `userPreferences` (gender/age/height/weight) is for general health metric tuning only — not for BP/BS computation.

### What the Colmi family CAN do (that 56ff cannot)

- REM sleep detection
- Body temperature (skin temperature sensor)
- HRV
- Stress scoring
- Continuous background sync (autonomous notifications while worn)

---

## 4. SIMSONLAB Platform

### Manufacturer

- **SHARE AUDIO HONG KONG LIMITED** (developer of SIMSONLAB app)
- Product appears on Shein, AliExpress, TikTok
- Model: **LA380-YJ** (2025)

### Hardware

| Component | Detail |
|---|---|
| **CPU** | Phyplus PHY6222 |
| **SoC architecture** | ARM Cortex-M0 32-bit |
| **Bluetooth** | BLE 5.1 |
| **Memory** | 512 KB built-in, 64 KB SRAM, 128 KB–8 MB flash |
| **HR sensor** | HX3602 |
| **Battery** | 15 mAh (magnetic charging) |
| **Standby** | 10–15 days |
| **Weight** | ~5 g |
| **Material** | Stainless steel outer, epoxy resin body |
| **Waterproof** | IP68 |
| **App** | SIMSONLAB app (iOS + Android) |

### Protocol

- **Unknown** — completely different from both 56ff and QRing
- Custom BLE protocol (likely)
- Not compatible with PulseLoop's existing drivers

### HX3602 Sensor

- Generic/low-cost heart rate sensor
- Used across many cheap Chinese wearables (SIMSONLAB rings, NKX19 watches, DaintyDelight smartwatches)
- No standalone public datasheet found
- LED configuration unknown (likely single wavelength vs VC30F's dual red+green)
- No independent accuracy testing available

---

## 5. Oura-Class Premium Rings

These rings compete with Oura on hardware quality but without the subscription lock-in.

### Oura Ring (Gen 3 / Gen 4)

| Component | Gen 3 (2021) | Gen 4 (2024) |
|---|---|---|
| **SoC** | Nordic nRF52840 (Cortex-M4F, 64 MHz, 1 MB flash, 256 KB RAM) | Nordic nRF52840 |
| **PPG** | 2× green LED + red/IR multi-chip LED + 2× photodiodes | 2 clusters × green/red/IR LEDs + 3× photodiodes, 18-path multi-wavelength |
| **Temperature** | NTC thermistor (indirect) | NTC thermistor (indirect) |
| **Accelerometer** | 3-axis | 3-axis |
| **Battery** | 16 mAh (Grepow YE160723G) | 26 mAh |
| **Battery life** | 4–7 days | Up to 8 days |
| **Battery management** | TI BQ25120A | Unknown |
| **Charging** | Wireless inductive | Wireless inductive |
| **Price** | $299+ (discontinued) | $349 + **$5.99/mo subscription required** |
| **BLE** | Encrypted, proprietary | Encrypted, proprietary |
| **Open docs** | ❌ Completely closed | ❌ Completely closed |

### Ultrahuman Ring Air

| Component | Detail |
|---|---|
| **BLE SoC** | Nordic nRF52840 (Cortex-M4F, 64 MHz, 1 MB flash, 256 KB RAM, BLE 5.0) |
| **Coprocessor** | STM32G0 (STMicro) — dedicated sensor DSP |
| **Sensors** | PPG (HR, HRV, SpO₂), skin temperature, 3-axis accelerometer |
| **Battery** | ~4–6 days |
| **Price** | $349, **no subscription** |
| **Open docs** | ✅ BLE protocol fully documented by Gadgetbridge |

**Dual-MCU architecture:** The nRF52840 handles BLE + main processing, while the STM32G0 coprocessor runs sensor data processing and power management — arguably more capable than Oura's single-MCU design.

**BLE Protocol (Gadgetbridge):**
- Device name: `UH_XXXXXXXXXXXXXXXX`
- Device State service: `86f61000-f706-58a0-95b2-1fb9261e4dc7` — battery level, charging state, temperature
- Command service: `86f65000-f706-58a0-95b2-1fb9261e4dc7` — opcodes for set time, get recordings, airplane mode, reset, power saving
- All opcodes and payload formats documented

### RingConn Gen 2 / Gen 2 Air

| Component | Detail |
|---|---|
| **Sensors** | PPG (HR, HRV, SpO₂), skin temperature, 3-axis accelerometer |
| **Battery** | 10+ days (class-leading) |
| **Price** | Gen 2: $299 / Gen 2 Air: **$199**, **no subscription** |
| **Open docs** | ❌ No known reverse engineering or public protocol docs |

### Premium Ring Comparison

| | Oura Gen 4 | Ultrahuman Air | RingConn G2 Air |
|---|---|---|---|
| **SoC** | nRF52840 | nRF52840 + STM32G0 | Unknown (likely Nordic) |
| **Architecture** | Cortex-M4F | Cortex-M4F + Cortex-M0 | Unknown |
| **PPG** | Custom 18-path | Multi-LED | Multi-LED |
| **Temperature** | NTC thermistor | ✅ Skin temp | ✅ Skin temp |
| **Battery** | 8 days | 4–6 days | 10+ days |
| **Subscription** | **$5.99/mo required** | ❌ None | ❌ None |
| **Price** | $349 + sub | $349 | $199 |
| **Protocol open** | ❌ | ✅ (Gadgetbridge) | ❌ |
| **Custom firmware** | ❌ | ❌ (nRF locked) | ❌ |

---

## 6. Hackability & Open Documentation

A breakdown of which rings can be used with custom software or firmware.

### 🏆 Full-Stack Hackable: Colmi R02 / R03 / R06

Per Hackaday's deep-dive by Aaron Christophel, the Colmi R02 is the most hacker-friendly ring:

| What | Detail |
|---|---|
| **Custom firmware** | Flashable via BLE OTA — **no signing, no encryption** |
| **Debug interface** | SWD pads accessible (scrape epoxy to expose) |
| **MCU** | BXMicro chip, 512 KB flash, 200 KB RAM |
| **SDK** | [BXMicro SDK3](https://gitee.com/BXMicro/SDK3) |
| **Reference FW** | [atc1441/ATC_RF03_Ring](https://github.com/atc1441/ATC_RF03_Ring) |
| **App protocol** | Documented in PulseLoop + Gadgetbridge |
| **Price** | $15–25 |

The manufacturer publishes firmware update images with no authenticity checks — upload whatever you want over BLE. Combined with SWD debugging, this is the closest thing to an open-source smart ring in production.

### 🥈 Protocol-Documented: Ultrahuman Ring Air

- ✅ Full BLE protocol documented on [Gadgetbridge](https://gadgetbridge.org/internals/specifics/ultrahuman-protocol/)
- ✅ Every service UUID, opcode, and payload layout is public
- ✅ You can write a custom app that talks directly to the ring — no vendor app needed
- ❌ Custom firmware unlikely — nRF52840 typically has readback protection enabled
- **Price:** $349, no subscription

### 🥈 Protocol-Documented: 56ff / Jring

- ✅ Protocol fully reverse-engineered (PulseLoop's `docs/ring-protocol.md`)
- ✅ Open-source firmware skeleton: [atc1441/ATC_SR08_Ring](https://github.com/atc1441/ATC_SR08_Ring)
- ✅ Official SDK protocol doc: `深新锐蓝牙协议v3.0.docx` (keeprapid/krwatch)
- ✅ Cleartext BLE, no encryption
- ✅ Renesas SUOTA for firmware OTA
- **Price:** $7–12

### 🥉 Protocol-Documented: Colmi/Yawell QRing family

- ✅ BLE protocol reverse-engineered (PulseLoop + Gadgetbridge)
- ✅ Nordic-UART based, unencrypted
- ✅ Custom app possible (PulseLoop already does it)
- ⚠️ Custom firmware: confirmed possible on R02/R03 (BXMicro); unknown for R10/R12 (Realtek RTL8762)
- **Price:** $15–30

### Open-Source DIY Platforms

| Project | Detail |
|---|---|
| **[Open Ring](https://github.com/stawiski/open-ring)** | Open-source hardware + firmware reference design |
| **KuoQuo's smart ring dev board** | nRF-based I2C sensor platform, designed for firmware hacking |
| **[ATC_SR08_Ring](https://github.com/atc1441/ATC_SR08_Ring)** | Open-source firmware for 56ff/Jring hardware |
| **[ATC_RF03_Ring](https://github.com/atc1441/ATC_RF03_Ring)** | Open-source firmware for Colmi R02/R03 hardware |
| **[ringverse/protocol](https://github.com/ringverse/protocol)** | Community reverse engineering of smart ring protocols |

### ❌ Fully Locked Down

| Ring | Reason |
|---|---|
| **Oura (all generations)** | Encrypted BLE, proprietary protocol, no public docs, subscription-gated features |
| **RingConn Gen 2 / Air** | No known reverse engineering, no public protocol docs |
| **SIMSONLAB LA380-YJ** | Unknown protocol, no documentation found |

### Hackability Summary

| Ring | Custom App | Custom Firmware | Price |
|---|---|---|---|
| **Colmi R02/R03** | ✅ PulseLoop, Gadgetbridge | ✅ OTA, SWD, SDK | $15–25 |
| **56ff / Jring** | ✅ PulseLoop, Gadgetbridge | ✅ SUOTA, open-source FW | $7–12 |
| **Colmi R10/R12** | ✅ PulseLoop, Gadgetbridge | ⚠️ Unknown (Realtek locked?) | $15–30 |
| **Ultrahuman Ring Air** | ✅ Gadgetbridge protocol | ❌ nRF locked | $349 |
| **RingConn Gen 2** | ❌ No public protocol | ❌ | $199–299 |
| **Oura Ring** | ❌ Encrypted BLE | ❌ | $349 + sub |
| **SIMSONLAB** | ❌ Unknown protocol | ❌ | $10–20 |

---

## 7. Quick Comparison

### Sensor quality

| | VC30F (Colmi R10/R12) | HX3602 (SIMSONLAB) | Unknown PPG (56ff) |
|---|---|---|---|
| **LEDs** | Red + green (dual) | Unknown | Unknown |
| **SpO₂ capable** | ✅ (red LED) | Unknown | ✅ |
| **Datasheet** | Public (JLCPCB) | None found | None found |
| **Verified accuracy** | ±1 BPM vs medical device | No testing found | No testing found |

### Chipset comparison

| | DA14531 (56ff) | RTL8762 (Colmi QRing) | AB2026 (Colmi R11) | PHY6222 (SIMSONLAB) |
|---|---|---|---|---|
| **Vendor** | Renesas | Realtek | Realtek | Phyplus |
| **Architecture** | ARM Cortex-M0 | ARM | ARM | ARM Cortex-M0 |
| **Bluetooth** | BLE 5.x | BLE 5.0 | BLE 5.2 | BLE 5.1 |
| **Memory** | Unknown | Unknown | Unknown | 512 KB built-in |
| **Known from** | Jring, KeepFit, RWfit, Tag | Colmi R02–R10, R12, Yawell | Colmi R11 | SIMSONLAB LA380-YJ, various watches |

### Which to choose?

| If you want... | Pick |
|---|---|
| **Cheapest possible** ($7–12) | 56ff / Jring |
| **Most sensors** (temp, HRV, REM, stress) | Colmi R10 or R12 ($15–30) |
| **Best battery** | Colmi R10 (17 mAh + 200 mAh case, no display) |
| **On-ring display** | Colmi R12 |
| **Best waterproofing** | Colmi R10 or R11 (5ATM) |
| **Best HR accuracy** | Colmi R10/R12 (VC30F sensor, verified accuracy) |
| **Works with PulseLoop today** | 56ff Jring or Colmi QRing family |

---

## 8. References

- **PulseLoop protocol docs**: `docs/ring-protocol.md`, `docs/protocol-discoveries.md`, `docs/keepfit-protocol-complete.md`
- **Gadgetbridge Yawell/Colmi page**: [gadgetbridge.org/gadgets/wearables/yawell/](https://gadgetbridge.org/gadgets/wearables/yawell/)
- **Gadgetbridge KeepFit PR**: [#5326](https://codeberg.org/Freeyourgadget/Gadgetbridge/pulls/5326)
- **Open-source ring firmware**: [atc1441/ATC_SR08_Ring](https://github.com/atc1441/ATC_SR08_Ring)
- **Official KeepFit SDK/protocol**: [keeprapid/krwatch](https://github.com/keeprapid/krwatch)
- **Ring reverse engineering**: [ringverse/protocol](https://github.com/ringverse/protocol)
- **Colmi R12 review** (Walter Shillington, Medium, 2026-03-05)
- **Colmi R10 review** (ShaunChng.com)
- **SIMSONLAB LA380-YJ user manual** (device.report)
- **PHY6222 datasheet** (Phyplus Technologies)
- **Yawell company profile**: [yawellfit.com](https://www.yawellfit.com/p/about.html)
- **Colmi official**: [colmi.com](https://www.colmi.com/), [colmi.info](https://www.colmi.info/)
- **Ultrahuman Protocol (Gadgetbridge)**: [gadgetbridge.org/internals/specifics/ultrahuman-protocol/](https://gadgetbridge.org/internals/specifics/ultrahuman-protocol/)
- **Hackaday — Hackable Smart Ring**: [New Part Day: A Hackable Smart Ring](https://hackaday.com/2024/06/16/new-part-day-a-hackable-smart-ring/)
- **ATC_RF03_Ring (custom FW for Colmi R02)**: [github.com/atc1441/ATC_RF03_Ring](https://github.com/atc1441/ATC_RF03_Ring)
- **Open Ring (open-source HW/FW)**: [github.com/stawiski/open-ring](https://github.com/stawiski/open-ring)
- **Ultrahuman Ring Air teardown**: [makingstudio.blog](https://makingstudio.blog/2024/09/10/ultrahuman-ring-air-teardown/)
- **Oura Ring teardown (Becky Stern)**: [beckystern.com](https://beckystern.com/2022/04/17/oura-ring-teardown-gen-3-and-gen-2/)
- **Oura Ring 4 deep-dive (EDN)**: [edn.com](https://www.edn.com/the-oura-ring-4-does-one-more-deliver-much-if-any-more/)
- **Wareable best smart rings 2026**: [wareable.com](https://www.wareable.com/fashion/best-smart-rings-1340)
