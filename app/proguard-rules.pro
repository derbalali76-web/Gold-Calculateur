# Google Mobile Ads, UMP and Play Billing ship their own consumer rules.
# Keep line numbers for readable crash reports, but never log sensitive data (see Logger usage).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Strip all android.util.Log debug/verbose calls from release builds (no sensitive info in logs).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
