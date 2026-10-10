# Keep kotlinx.serialization generated serializers (referenced reflectively by the plugin).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.fefw.planner.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.fefw.planner.**$$serializer { *; }
-keep class com.fefw.planner.data.** { *; }
