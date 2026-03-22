# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Or more broadly if you have multiple components:
-keep class com.prateekmahendrakar.metadatawiper.** extends android.provider.DocumentsProvider { *; }

# ---- DocumentsContract / SAF (Storage Access Framework) ----
-keep class android.provider.DocumentsContract { *; }
-keep class android.provider.DocumentsContract$* { *; }

# ---- ActivityResultContracts (file picker launcher) ----
-keep class androidx.activity.result.contract.ActivityResultContracts$* { *; }

# ---- ExifInterface (accessed reflectively for tag names) ----
-keep class androidx.exifinterface.media.ExifInterface { *; }
-keepclassmembers class androidx.exifinterface.media.ExifInterface { *; }

# ---- Coil (already has consumer rules bundled, but just in case) ----
-keep class coil.** { *; }

# ---- Kotlin IO (createTempFile etc.) ----
-keep class kotlin.io.path.** { *; }

# Theme enum — valueOf() is called in SettingsViewModel.init to restore saved theme from SharedPreferences.
# Without this, Theme.valueOf("Dark") crashes at runtime because R8 removes the static fields.
-keepclassmembers enum com.prateekmahendrakar.metadatawiper.model.Theme {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public static final ** *;
}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

#-printusage build/outputs/r8-removed.txt