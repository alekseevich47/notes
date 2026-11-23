# sherpa-onnx JNI. R8 must keep the Java classes that register native methods
# and the config objects passed into them. Stripping these crashes the first
# T-one or GigaAM call in a minified release even when model files exist.
# Missing or corrupt models are handled in Kotlin and must not reach this layer.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers,includedescriptorclasses class com.k2fsa.sherpa.onnx.** {
    native <methods>;
}
-keepclasseswithmembernames class com.k2fsa.sherpa.onnx.** {
    native <methods>;
}
-dontwarn com.k2fsa.sherpa.onnx.**
-keep class com.alekseevich47.notes.data.local.** { *; }
-keep @kotlinx.serialization.Serializable class com.alekseevich47.notes.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn javax.annotation.**
-dontwarn okhttp3.**
-dontwarn org.conscrypt.**

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
