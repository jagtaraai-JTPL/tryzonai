# Retrofit
-keepattributes Signature, InnerClasses, AnnotationDefault
-keepclassmembers class retrofit2.BuiltInConverters$ToStringConverter { *; }
-dontnote retrofit2.Platform
-dontnote retrofit2.Platform$Java8
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}

# Gson
-keep class com.jagtarapvtltd.tryzonai.models.** { *; }
-keep class com.jagtarapvtltd.tryzonai.network.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# Coil
-keep class coil.** { *; }
-dontwarn coil.**
