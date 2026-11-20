# BlackBox menggunakan refleksi dan JNI; jangan obfuscate API engine.
-keep class top.niunaijun.blackbox.** { *; }
-keep class black.** { *; }
-keep class top.canyie.pine.** { *; }
-keepclasseswithmembers class * {
    native <methods>;
}
