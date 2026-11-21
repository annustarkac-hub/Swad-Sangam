# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.swadsangam.restaurant.**$$serializer { *; }
-keepclassmembers class com.swadsangam.restaurant.** { *** Companion; }
-keepclasseswithmembers class com.swadsangam.restaurant.** { kotlinx.serialization.KSerializer serializer(...); }

# Retrofit
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keep interface com.swadsangam.restaurant.data.remote.SupabaseApi { *; }
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Crashlytics readable stack traces
-keepattributes SourceFile, LineNumberTable
-keep public class * extends java.lang.Exception
