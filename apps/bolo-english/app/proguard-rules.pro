# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.bolo.english.** {
    *** Companion;
}
-keepclasseswithmembers class com.bolo.english.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.bolo.english.**$$serializer { *; }
