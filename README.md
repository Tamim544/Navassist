# NavAssist

**Assistive navigation app for visually impaired users** — Arduino + Android Studio project.

## Demo: 1 April 2026

---

## Project Structure

```
navassist.ino          ← Arduino firmware (HC-SR04 + LDR + HC-05 Bluetooth)
circuit_diagram.svg    ← Wiring diagram (Arduino Uno, HC-SR04, LDR, HC-05)
app/                   ← Android Studio project (minSdk 26, Kotlin)
```

---

## Hardware

| Component         | Purpose                            | Connection          |
|-------------------|------------------------------------|---------------------|
| Arduino Uno       | Microcontroller                    | USB / 9V battery    |
| HC-SR04           | Ultrasonic distance sensor (0–4 m) | TRIG=D9, ECHO=D10   |
| LDR               | Ambient light detection            | A0 (voltage divider)|
| HC-05             | Bluetooth SPP to Android           | SoftwareSerial D2/D3|

### Arduino code

- Reads HC-SR04 every **200 ms** → distance in cm (clamped 0–400)
- Reads LDR raw value (0–1023): `<200` = dark, `200–600` = dim, `>600` = bright
- Sends newline-terminated JSON: `{"d":45,"l":312}\n`
- Mirrors output to USB Serial Monitor at 9600 baud for debugging

---

## Android App

### Screens

| Screen      | Description                                                  |
|-------------|--------------------------------------------------------------|
| Dashboard   | Live distance gauge, LDR light bar, ARC activity, BT status |
| History     | RecyclerView of stored readings (Room DB)                    |
| Settings    | Select HC-05 device, alert threshold, TTS speed, vibration  |

### Key Features

**Bluetooth HC-05 Connection**
- Scans paired devices via `BluetoothAdapter`
- Connects via `BluetoothSocket` with SPP UUID `00001101-0000-1000-8000-00805F9B34FB`
- Background reader thread parses newline-delimited JSON
- Auto-reconnects on disconnect

**Room Database** (`SensorReading` entity)
- Stores: timestamp, distanceCm, ldrValue, lightLevel, activity, accelerometer XYZ, phoneLightLux, proximity

**Built-in Smartphone Sensors**
- `TYPE_ACCELEROMETER` — movement detection
- `TYPE_LIGHT` — ambient light cross-check
- `TYPE_PROXIMITY` — face proximity

**Activity Recognition Chain (ARC)** — 6 steps
1. **Data Collection** — readings every 200 ms
2. **Preprocessing** — sliding window (10 samples), min-max normalisation
3. **Segmentation** — threshold-based change detection
4. **Feature Extraction** — mean distance, LDR variance, accel magnitude
5. **Classification** — rule-based: *Walking / Stationary / Near obstacle / Dark environment / Entering room*
6. **Post-processing** — majority vote over last 5 labels (smoothing)

**Accessibility Features** (for visually impaired users)
- `TextToSpeech` API: *"Obstacle 30cm ahead"*, *"Low light detected, use caution"*
- Haptic vibration: faster pattern = closer obstacle
- High-contrast dark UI with large text (≥16 sp)
- Full TalkBack `contentDescription` on all interactive elements

**Offline Execution**
- All ARC logic runs locally on-device
- Room DB stores full history
- Foreground Service keeps BT connection alive in background

---

## Critical Observations

### Advantages of HC-SR04 + LDR
- Low cost (<₹100 each), low power, real-time readings
- No privacy concerns (no camera/microphone)
- Simple Arduino integration, works in complete darkness

### Limitations
- **HC-SR04**: narrow 15° beam angle misses side obstacles; unreliable on soft/angled surfaces; 4 m max range
- **LDR**: slow response time; cannot identify light source type; affected by sensor placement
- Neither detects moving objects nor classifies obstacle type

---

## Submission Checklist

- ✅ `navassist.ino` — Arduino firmware
- ✅ `circuit_diagram.svg` — Wiring diagram
- ✅ `app/` — Android Studio project
