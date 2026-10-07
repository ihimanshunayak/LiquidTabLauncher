# Add project specific ProGuard rules here.
# For more details, see https://developer.android.com/guide/developing/tools/proguard.html

# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ihimanshunayak.liquidtab.**$$serializer { *; }
-keepclassmembers class com.ihimanshunayak.liquidtab.** {
    *** Companion;
}
-keepclasseswithmembers class com.ihimanshunayak.liquidtab.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Media3 session client resolves controllers by class name.
-keep class androidx.media3.session.** { *; }
