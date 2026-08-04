# Ktor uses service loading for engines. Keep service descriptors and engine classes.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**

# Preserve source and line metadata so uploaded R8 mappings can deobfuscate crash reports.
-keepattributes SourceFile,LineNumberTable
