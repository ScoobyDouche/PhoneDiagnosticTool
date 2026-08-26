# Phone Diagnostic Tool — release ProGuard / R8 rules

-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# App models used in UI / export
-keep class com.phonediagnostic.data.** { *; }
