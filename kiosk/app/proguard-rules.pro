# 1059b: release currently ships with minify OFF; these rules keep it safe if R8 is ever enabled
# (kotlinx.serialization models must survive or login/face JSON fails only in release).
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.satcop.smartvisitor.kiosk.**$$serializer { *; }
-keepclassmembers class com.satcop.smartvisitor.kiosk.** { *** Companion; }
-keepclasseswithmembers class com.satcop.smartvisitor.kiosk.** { kotlinx.serialization.KSerializer serializer(...); }
-keep @kotlinx.serialization.Serializable class com.satcop.smartvisitor.kiosk.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
