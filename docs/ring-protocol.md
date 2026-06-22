# Ring Protocol Reference

> Sources: [sakshambhutani.xyz/hacking/2_hacking/](https://sakshambhutani.xyz/hacking/2_hacking/),
> [saksham2001/Smart-Ring-Protocol](https://github.com/saksham2001/Smart-Ring-Protocol/),
> [tahnok/colmi_r02_client](https://tahnok.github.io/colmi_r02_client/)

## Transport

| Property | Jring (56ff) | Colmi R02 |
|---|---|---|
| Advertised name | `SMART_RING` | `SMART_RING` / `R02` / `R11` |
| Service UUID | `0x56FF` | Nordic-UART (`6E40xxx`) or V2 |
| Write char | `0x33F3` | Varies by firmware variant |
| Notify char | `0x33F4` | Varies |
| Frame size | Fixed 20 bytes | 16 bytes (padded to 15 + checksum) |
| Encryption | None | None |

## Jring (56ff) Command Set

Every packet is exactly 20 bytes. Byte 0 is the command ID, bytes 1-19 are payload (zero-padded). Multi-byte integers are little-endian.

| Cmd | Direction | Description |
|---|---|---|
| `0x01` | write | Time sync: u32le epoch + i8 timezone offset |
| `0x02` | write | Request activity query → triggers `0x03`/`0x13` |
| `0x03` | notify | Current activity: steps(u32), distance(u32), calories(u32) |
| `0x04` | write | Find ring (buzz) |
| `0x0B` | notify | Battery percentage |
| `0x0C` | write/notify | Status query → response embeds ring MAC |
| `0x10` | write | Request sleep/history → triggers `0x11` |
| `0x11` | notify | Sleep timeline: 15× 1-min stage samples per packet |
| `0x13` | notify | Activity summary (companion to `0x03`) |
| `0x14` | write/notify | Start live HR stream; BPM at byte 5 |
| `0x15` | write | Stop HR measurement |
| `0x16` | write | Request stored measurement history |
| `0x19` | write | Configure auto-HR window + cadence |
| `0x1A` | write | Set daily step goal (u32le) |
| `0x21` | write | Set locale string, e.g. `en-US` |
| `0x23` | write/notify | Start/stop SpO₂ measurement |
| `0x24` | notify | SpO₂ result: percentage at byte 4 (valid range 80-100) |
| `0x27` | notify | HR measurement complete |
| `0x28` | notify | SpO₂ measurement complete |
| `0x48` | write | App identifier |

Battery uses the standard BLE Battery Service (`0x180F`/`0x2A19`), not the custom protocol.

## Data Frequency — Critical Understanding

**The ring only sends data in response to commands. There is no continuous push.**

| Data Type | How It Arrives | When |
|---|---|---|
| **Activity** (steps, cal, dist) | `0x03` notification | Only when app sends `0x02` query |
| **Live HR** | `0x14` notifications | ~1/sec during active measurement (~12s warm-up) |
| **SpO₂** | `0x24` notification | Spot check: `0x23` start → 25-40s → result |
| **Sleep** | `0x11` bulk notifications | Only when app sends `0x10` query |
| **History** | `0x16` bulk stream | Only when app sends `0x16` query |
| **Battery** | Standard BLE read | App reads Battery Service characteristic |

**What the app's "2-second refresh" means:** The app polls the local Room database every 2-5 seconds. This does NOT mean the ring sends data every 2 seconds. The ring only sends data when the app explicitly queries it (startup sync, pull-to-refresh, sync button).

## SpO₂ Data Source

SpO₂ comes **directly from the ring**, not calculated by the app:
- **Jring**: Spot measurement only (`0x23` start → `0x24` result). No history SpO₂ storage.
- **Colmi**: History sync sends hourly min/max pairs. App displays midpoint `(min + max) / 2`. A consistent 97% is normal — healthy SpO₂ is 95-100% with minimal fluctuation.

## Firmware Version

- **Jring (56ff)**: Does NOT expose the standard BLE Device Information Service (`0x180A`). Firmware version cannot be read.
- **Colmi**: May expose DIS. If available, firmware revision string characteristic (`0x2A26`) is read on connect.
- Neither ring sends firmware version in its protocol packets.

## Sleep Stage Decoding (Experimental)

The `0x11` sleep timeline uses these stage markers:
- `0x00` = Awake
- `0x28` = Light sleep
- `0x63` = Deep sleep

**No REM stage is decoded.** The app labels sleep as experimental: light/deep/awake only. Awake time may read as zero on some firmware versions.

## References

- Full protocol notes: [github.com/saksham2001/Smart-Ring-Protocol](https://github.com/saksham2001/Smart-Ring-Protocol/blob/main/Protocol.md)
- Lab notes from reverse engineering: [lab-notes.md](https://github.com/saksham2001/Smart-Ring-Protocol/blob/main/lab-notes.md)
- Python CLI for testing: [smart_ring_cli.py](https://github.com/saksham2001/Smart-Ring-Protocol/blob/main/smart_ring_cli.py)
- Colmi R02 client: [tahnok/colmi_r02_client](https://tahnok.github.io/colmi_r02_client/)
