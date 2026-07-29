# AirPods Control ProGuard Rules

# Keep AACP protocol classes (reflection-free, but keep for safety)
-keep class com.airpods.control.core.aacp.** { *; }

# Keep data classes used with Gson/serialization
-keep class com.airpods.control.core.data.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Bluetooth
-keep class android.bluetooth.** { *; }

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
