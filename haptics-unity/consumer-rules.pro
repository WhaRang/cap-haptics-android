# Shipped inside the AAR and applied to whatever app consumes it.
#
# The bridge is only ever reached by name, from C# through JNI, so R8 has no way to see that
# anything calls it -- left alone it would strip or rename the whole class and every method on
# it. The failure looks like `NoSuchMethodError` at runtime in release builds only, which is
# an unpleasant thing to discover after shipping.
-keep class com.cap.haptics.unity.HapticsBridge {
    public *;
    public static ** getInstance();
}

# Enum wire ids are read reflectively by nothing, but keeping the names makes logcat and
# getLastError() legible in a minified build, which is where they matter most.
-keepclassmembers enum com.cap.haptics.core.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
