/*
 * NavAssist — Assistive Navigation for Visually Impaired Users
 * Arduino code for HC-SR04 ultrasonic sensor + LDR + HC-05 Bluetooth module
 *
 * Wiring:
 *   HC-SR04 TRIG -> Pin 9
 *   HC-SR04 ECHO -> Pin 10
 *   LDR          -> A0 (voltage divider: LDR + 10kΩ to GND)
 *   HC-05 TX     -> Pin 2 (SoftwareSerial RX)
 *   HC-05 RX     -> Pin 3 (SoftwareSerial TX, via 1kΩ+2kΩ voltage divider)
 */

#include <SoftwareSerial.h>

// ── Pin Definitions ──────────────────────────────────────────────
#define TRIG_PIN    9
#define ECHO_PIN    10
#define LDR_PIN     A0

// HC-05 connected on pins 2 (RX) and 3 (TX) at 9600 baud
SoftwareSerial btSerial(2, 3);

// ── Timing ───────────────────────────────────────────────────────
const unsigned long SEND_INTERVAL_MS = 200;  // 5 Hz update rate
unsigned long lastSendTime = 0;

// ── Setup ─────────────────────────────────────────────────────────
void setup() {
  Serial.begin(9600);       // USB debug monitor
  btSerial.begin(9600);     // HC-05 Bluetooth

  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);
  digitalWrite(TRIG_PIN, LOW);

  Serial.println("NavAssist ready. TRIG=9 ECHO=10 LDR=A0");
}

// ── Main Loop ─────────────────────────────────────────────────────
void loop() {
  unsigned long now = millis();
  if (now - lastSendTime >= SEND_INTERVAL_MS) {
    lastSendTime = now;

    int distanceCm = readDistance();
    int ldrRaw     = readLDR();

    // Send JSON over Bluetooth (newline-terminated for Android parser)
    String json = "{\"d\":" + String(distanceCm) + ",\"l\":" + String(ldrRaw) + "}\n";
    btSerial.print(json);

    // Mirror to USB Serial for debugging
    Serial.print(json);
  }
}

// ── HC-SR04 Distance Reading ──────────────────────────────────────
// Returns distance in cm, clamped to 0–400.
int readDistance() {
  // Ensure TRIG is low before pulse
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);

  // Send 10µs trigger pulse
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  // Measure ECHO pulse width (timeout = 30000µs ≈ 510 cm)
  long duration = pulseIn(ECHO_PIN, HIGH, 30000);

  if (duration == 0) {
    return 400;  // Out of range — return max distance
  }

  // Convert to cm: speed of sound = 0.034 cm/µs, divide by 2 (round trip)
  int distance = (int)(duration * 0.034 / 2);

  // Clamp to valid range
  if (distance < 0)   distance = 0;
  if (distance > 400) distance = 400;

  return distance;
}

// ── LDR Light Level Reading ───────────────────────────────────────
// Returns raw ADC value 0–1023.
// Light level categories (used by Android app):
//   < 200  → dark
//   200–600 → dim
//   > 600  → bright
int readLDR() {
  return analogRead(LDR_PIN);
}
