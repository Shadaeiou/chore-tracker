# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class * {
    static <fields>;
    *** Companion;
}
-keep class com.chore.tracker.data.** { *; }
