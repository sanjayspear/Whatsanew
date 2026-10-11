# MediaPipe LLM Inference is driven from JNI; keep its Java surface and protobuf messages.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**

# Tink pulls optional annotations / providers.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**

# Ktor / OkHttp optional dependencies.
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# kotlinx.serialization ships its own consumer rules; keep our @Serializable companions anyway.
-keepclassmembers @kotlinx.serialization.Serializable class dev.pulse.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.pulse.**$$serializer { *; }
