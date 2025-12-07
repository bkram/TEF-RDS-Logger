# RDS Control Protocols (developer guide)

Reference for the TEF6686_ESP32 firmware that runs on the ESP32 tuner and exposes TCP 7373 + USB serial.

## Overview

- **Transport**: TCP 7373 (Wi‑Fi) or USB serial (115200 baud).
- **Handshake**: send `?F\n` (or `*F\n`) to enter RDS Spy mode.
- **Tuning command**: `<freq10>*F\n` where `<freq10>` is frequency in 10 kHz units (e.g., `10170*F`).
- **RDS stream**: RDS Spy text `G:\r\nAAAA BBBB CCCC DDDD\r\n\r\n` (errors as `----`; RESET marker available).
- **Extra commands**: XDRGTK USB/TCP (`x`, `s`, `S...`, logbook `l/L`); EQ/IMS info via `Gxy` in XDRGTK mode.
- **Auth**: None in RDS Spy; XDRGTK mode can be passworded.

## TEF6686_ESP32 commands (TCP 7373 / USB serial)

| Command | Direction | Explanation |
| --- | --- | --- |
| `?F\n` or `*F\n` | → TEF | Enter RDS Spy mode (required before other commands on TCP/USB). |
| `<freq10>*F\n` | → TEF | Tune FM; `<freq10>` in 10 kHz units (e.g., `10170*F\n` = 101.70 MHz). Switches to FM, clears RDS. |
| `G:\r\nAAAA BBBB CCCC DDDD\r\n\r\n` | TEF → | RDS Spy frame (hex blocks; `----` for CRC errors). Reset marker: `G:\r\nRESET-------\r\n\r\n`. |
| `x\n` | → TEF (USB/XDRGTK) | Enter XDRGTK USB mode; reply `OK\nT<freq10>\nGxy\n` (freq*10, EQ/IMS bits). |
| `s\n` | → TEF (USB/XDRGTK) | Capability dump: returns `r:`, `v:`, `m:`, `s:`, `o:`, `a:`, `f:`, then presets. |
| `S<pos>,<freq>,<bw>,<ms>,<pi>,<ps>\n` | → TEF (USB/XDRGTK) | Write preset; reply `S:<errorMask>` (bit 7 marks success). |
| `l\n` / `L\n` | → TEF (USB/XDRGTK) | Stream logbook CSV. |
| XDRGTK TCP auth | ⇄ | Salted password; after auth, TEF sends `o1,0` and `Gxy`, accepts XDRGTK commands (not fully enumerated in source). |

### Mode: Wi‑Fi TCP 7373 (RDS Spy)
- **Connect**: TCP socket to port 7373.
- **Handshake**: send `?F\n` (or `*F\n`) once to enter RDS Spy mode.
- **Tune (client → TEF)**: `<freq10>*F\n` where `<freq10>` is frequency in 10 kHz units. Examples: `10170*F\n` = 101.70 MHz; `09870*F\n` = 98.70 MHz. Switches to FM band, clears RDS, updates display.
- **RDS frames (TEF → client)**: `G:\r\nAAAA BBBB CCCC DDDD\r\n\r\n` (errors as `----`). Reset marker: `G:\r\nRESET-------\r\n\r\n`.

### Mode: USB serial (RDS Spy and XDRGTK)
- **Baud**: typically 115200 8N1.
- **RDS Spy over USB**: same as TCP — send `?F`/`*F`, then tune with `<freq10>*F\n`.
- **Enter XDRGTK USB**: send `x\n`; reply `OK\nT<freq10>\nGxy\n` (freq*10, EQ/IMS bits).
- **Status dump**: `s\n` → replies multi-line keys: `r:` (result), `v:` (version), `m:` (#presets), `s:` (preset sentinel), `o:` (converter offset), `a:` (LW/SW range), `f:` (FM min/max), then preset lines `<index>,<freq>,<band>,<bw>,<ms>,<pi>,<ps>`.
- **Preset write**: `S<pos>,<freq>,<bw>,<ms>,<pi>,<ps>\n` → writes EEPROM; replies `S:<errorMask>` (bit 7 set on success, lower bits indicate validation errors).
- **Logbook dump**: `l\n` or `L\n` → streams CSV logbook.
- **XDRGTK TCP (alt mode)**: with salted password handshake; after auth, TEF sends `o1,0` and `Gxy`, accepts XDRGTK commands (not fully listed in source) and outputs RDS in “R” framed format. Not used by TEF RDS Logger today.

## Python examples

### TEF6686_ESP32 over TCP 7373
```python
import socket

def tune_tef(host: str, mhz: float):
    freq10 = int(round(mhz * 100))  # 10 kHz units
    with socket.create_connection((host, 7373), timeout=5) as s:
        s.sendall(b'?F\n')  # enter RDS Spy mode
        s.sendall(f"{freq10:05d}*F\n".encode())
        data = s.recv(1024)  # read one RDS frame
        print(data.decode(errors='ignore'))

tune_tef('192.168.1.50', 101.7)
```

Use this single file as the canonical protocol reference.
