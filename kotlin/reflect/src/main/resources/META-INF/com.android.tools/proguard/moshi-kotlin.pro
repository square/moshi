# When editing this file, update the following files as well:
# - META-INF/com.android.tools/r8-from-1.6.0/moshi-kotlin.pro
# - META-INF/com.android.tools/r8-upto-1.6.0/moshi-kotlin.pro
# - META-INF/proguard/moshi-kotlin.pro
# Keep Metadata annotations so they can be parsed at runtime.
-keep class kotlin.Metadata { *; }

# Keep default constructor marker name for lookup in signatures.
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker

# Keep implementations of service loaded interfaces
-keep interface com.squareup.moshi.kotlin.kotlinx.metadata.impl.extensions.MetadataExtensions
-keep class * implements com.squareup.moshi.kotlin.kotlinx.metadata.impl.extensions.MetadataExtensions { public protected *; }

# Keep generic signatures and annotations at runtime.
-keepattributes Signature,RuntimeVisible*Annotations