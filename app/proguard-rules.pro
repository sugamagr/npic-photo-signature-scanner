# --- Base rules ---
-dontobfuscate
-keepattributes *Annotation*, InnerClasses, SourceFile, LineNumberTable

# --- Kotlinx Serialization ---
# Keep companion objects that Serializer relies on
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Room ---
-keep class androidx.room.** { *; }
-keep @androidx.room.* class * { *; }

# --- Coroutines: internal classes referenced via reflection ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# --- Compose ---
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.tooling.** { *; }
