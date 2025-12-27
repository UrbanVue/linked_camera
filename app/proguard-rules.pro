# ProGuard rules for Linked Camera
# Keep all app classes to prevent functionality loss

# Keep all classes in the app package
-keep class com.linkedcamera.app.** { *; }

# Keep AndroidX classes
-keep class androidx.** { *; }
-dontwarn androidx.**

# Keep preference classes
-keepclassmembers class * extends androidx.preference.Preference {
    public <init>(android.content.Context, android.util.AttributeSet);
}

# Keep parcelable classes
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep R class
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Suppress warnings for missing classes
-dontwarn org.jetbrains.annotations.**
-dontwarn kotlin.**
