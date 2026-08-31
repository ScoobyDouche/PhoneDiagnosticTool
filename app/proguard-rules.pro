# Phone Diagnostic Tool — release ProGuard / R8 rules

-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

# The app falls back to an exception's simple name when it has no message, and
# surfaces that in the UI and the diagnostic log. Without this, release builds
# report obfuscated names like "a" instead of "SocketTimeoutException".
-keepnames class * extends java.lang.Throwable

# App models are read reflectively by nothing, but are the shape of the exported
# report; keeping them keeps the JSON/text export field names stable.
-keep class com.phonediagnostic.data.** { *; }

# Compose ships its own consumer rules in the AndroidX artifacts. A blanket
# "-keep class androidx.compose.** { *; }" was pinning the single largest chunk
# of the APK and defeating shrinking entirely, so it is deliberately not here.
