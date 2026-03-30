# Add project specific ProGuard rules here.
# Keep Room entities and DAOs
-keep class com.navassist.app.database.** { *; }
-keep class com.navassist.app.bluetooth.BluetoothService$ArduinoPacket { *; }

# Keep Gson serialization
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
