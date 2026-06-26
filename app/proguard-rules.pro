-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses,EnclosingMethod

# App data classes serialized by Gson — names MUST be preserved or JSON read/write breaks in
# release builds only (R8 obfuscation). Covers Company/Distributor/Shop/RecceEntry/MediaItem
# (data.model) AND AppSettings, AppProfile, LocalStore.Db (data.local).
-keep class com.receegpsstamp.data.model.** { *; }
-keep class com.receegpsstamp.data.local.** { *; }
# ProjectBundle — the .rgsproj (export/import) wrapper; keys must stay stable across versions.
-keep class com.receegpsstamp.data.transfer.** { *; }
-keep class com.receegpsstamp.data.sync.SyncStatus { *; }
-keep class com.receegpsstamp.data.export.ImportedShop { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Google API Client / Drive
-keep class com.google.api.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.http.** { *; }
-dontwarn com.google.api.client.**
-dontwarn com.google.common.**
-dontwarn org.apache.http.**
-dontwarn com.sun.net.httpserver.**
-dontwarn javax.servlet.**

# Gson (used by google-http-client-gson)
-keep class com.google.gson.** { *; }
-keepclassmembers class * { @com.google.gson.annotations.SerializedName <fields>; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Hilt / Dagger
-dontwarn dagger.internal.codegen.**
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# WorkManager + HiltWorker
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker { public <init>(android.content.Context, androidx.work.WorkerParameters); }
-keep class androidx.hilt.work.** { *; }

# CameraX
-dontwarn androidx.camera.**

# Coroutines
-dontwarn kotlinx.coroutines.**

# Play Services
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# PdfBox-Android (PDF report generation) — keep its classes (reflection: fonts/filters) and silence
# warnings for desktop-only APIs (java.awt / javax.*) that the lib references but never uses on Android.
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-dontwarn java.awt.**
-dontwarn javax.**
-dontwarn org.bouncycastle.**
