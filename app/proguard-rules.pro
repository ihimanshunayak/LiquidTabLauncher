# Add project specific ProGuard rules here.
# For more details, see https://developer.android.com/guide/developing/tools/proguard.html

# ── Optimization without renaming ─────────────────────────────────────────────
#
# R8 is on for what it does to Compose's speed, not for size, and the release
# build is expected to keep every class name it was written with: a launcher's
# crash reports and stack traces have to be readable without a mapping file in
# hand, and the persisted-workspace rules below only make sense if names are
# stable in the first place. The AGP defaults do not include this — obfuscation
# is on unless it is switched off — so it is stated here rather than assumed.
-dontobfuscate

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

# ── The persisted workspace ───────────────────────────────────────────────────
#
# This is a data format, not just code. [com.ihimanshunayak.liquidtab.data.Workspace]
# is written to SharedPreferences as JSON and read back by a later build, so its
# class and member names are part of what a user's saved layout means. R8 is free
# to rename plain data classes, and a rename here would not fail a build — it
# would silently fail to decode a layout the user had already arranged, which is
# the one bug this launcher most needs not to have.
#
# The serializers are already kept above; this keeps the shapes they describe,
# including the sealed [WorkspaceItem] subtypes that the JSON discriminator
# ("app", "folder", "widget") resolves to.
-keep class com.ihimanshunayak.liquidtab.data.Workspace { *; }
-keep class com.ihimanshunayak.liquidtab.data.WorkspacePage { *; }
-keep class com.ihimanshunayak.liquidtab.data.WorkspaceItem { *; }
-keep class com.ihimanshunayak.liquidtab.data.WorkspaceItem$* { *; }
-keep class com.ihimanshunayak.liquidtab.data.AppRef { *; }
-keep class com.ihimanshunayak.liquidtab.data.FolderRef { *; }
-keep class com.ihimanshunayak.liquidtab.data.WidgetKind { *; }

# Enum constant names are read through `name`, which reflection-free code cannot
# be sure survives enum unboxing. Widget kinds are written to disk by name.
-keepclassmembers enum com.ihimanshunayak.liquidtab.data.WidgetKind {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
