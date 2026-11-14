# Desktop release (`packageReleaseMsi` etc.): ProGuard shrinks only (no obfuscation or
# optimization, see build.gradle.kts), on top of Compose's default rules. Unlike R8 on Android it
# does not read the libraries' own rules, so libraries that load classes by name, through
# ServiceLoader or from native code are kept whole here.

# --- Our code: kotlinx.serialization (MCP server, settings) reaches parts of it by name, and the
# on-device helper jar is read as a resource next to these classes. ---
-keep class io.github.raghavsatyadev.androidworld.** { *; }

# --- Native code calls back into these (JNI). ---
-keep class org.sqlite.** { *; }
-keep class org.jetbrains.skiko.** { *; }
-keep class org.jetbrains.skia.** { *; }

# --- Shrinking drops interfaces that default methods call through (VerifyError in JobSupport). ---
-keep class kotlinx.coroutines.** { *; }

# --- Loaded by name or through ServiceLoader. ---
-keep class org.jcodec.** { *; }
-keep class kotlinx.datetime.** { *; }

# --- kotlinx.serialization: serializers are found through the companion's serializer(). ---
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, RuntimeVisibleAnnotations
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static ** Companion;
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$$serializer { *; }
-keep class kotlinx.serialization.** { *; }

# --- Optional parts of libraries that the app never reaches. ---
-dontwarn org.sqlite.**
-dontwarn org.jcodec.**
-dontwarn com.android.ddmlib.**
-dontwarn javax.annotation.**
-dontwarn org.graalvm.**
-dontwarn com.oracle.svm.**

# --- Embedded emulator screen (gRPC): providers come through ServiceLoader, Netty and protobuf
# reach their classes by reflection, and the generated messages are parsed by reflection. ---
-keep class io.grpc.** { *; }
-keep class com.google.protobuf.** { *; }
-keep class com.android.emulator.control.** { *; }
-keep class com.google.common.** { *; }
-dontwarn io.grpc.netty.shaded.**
-dontwarn com.google.common.**
-dontwarn com.google.protobuf.**
-dontwarn java.lang.invoke.**
