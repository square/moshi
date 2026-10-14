# JSR 305 annotations are for embedding nullability information.
-dontwarn javax.annotation.**

-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}

-keep @com.squareup.moshi.JsonQualifier @interface *

# Enum field names are used by the integrated EnumJsonAdapter.
# values() is synthesized by the Kotlin compiler and is used by EnumJsonAdapter indirectly
# Annotate enums with @JsonClass(generateAdapter = false) to use them with Moshi.
-keepclassmembers @com.squareup.moshi.JsonClass class * extends java.lang.Enum {
    <fields>;
    **[] values();
}

# Keep helper method to avoid R8 optimisation that would keep all Kotlin Metadata when unwanted
-keepclassmembers class com.squareup.moshi.internal.Util {
    private static java.lang.String getKotlinMetadataClassName();
}

# Keep ToJson/FromJson-annotated methods
-keepclassmembers class * {
  @com.squareup.moshi.FromJson <methods>;
  @com.squareup.moshi.ToJson <methods>;
}

# R8 full mode retains a generic signature only for types a keep rule matches. Matching
# JsonAdapter keeps JsonAdapter<T> on @ToJson/@FromJson methods that already reference it, so
# Method.getGenericParameterTypes() stays a ParameterizedType and bind() can read the delegate
# type argument. A raw JsonAdapter has no type argument to recover.
# allowshrinking still allows unused members to be removed, so this does not pin code into the
# app. allowobfuscation still allows the class to be renamed; parametersAreJsonAdapters compares
# rawType to JsonAdapter, and R8 rewrites both the library reference and the signature.
# The rule names this single class, with no member wildcard and no com.squareup.moshi.**
# pattern, so subclasses and other Moshi types stay unmatched. allowoptimization is omitted so
# the rule matches R8 full-mode signature guidance and Retrofit's retrofit2.Response rule.
-keep,allowobfuscation,allowshrinking class com.squareup.moshi.JsonAdapter
