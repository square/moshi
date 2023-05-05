import me.champeau.gradle.japicmp.JapicmpTask

plugins {
  `java-library`
  id("me.champeau.gradle.japicmp")
}

val baseline = configurations.create("baseline")
val latest = configurations.create("latest")

dependencies {
  baseline("com.squareup.moshi:moshi:1.14.0") {
    isTransitive = false
    version {
      strictly("1.14.0")
    }
  }
  latest(project(":moshi"))
}

val japicmp = tasks.register<JapicmpTask>("japicmp") {
  dependsOn("jar")
  oldClasspath.from(baseline)
  newClasspath.from(latest)
  onlyBinaryIncompatibleModified.set(true)
  failOnModification.set(true)
  txtOutputFile.set(layout.buildDirectory.file("reports/japi.txt"))
  ignoreMissingClasses.set(true)
  includeSynthetic.set(true)
  classExcludes.addAll(
    "com.squareup.moshi.AdapterMethodsFactory", // Internal.
    "com.squareup.moshi.ClassJsonAdapter", // Internal.
    "com.squareup.moshi.RecordJsonAdapter\$ComponentBinding", // Package-private
    "com.squareup.moshi.StandardJsonAdapters", // Package-private
    "com.squareup.moshi.internal.NonNullJsonAdapter", // Internal.
    "com.squareup.moshi.internal.NullSafeJsonAdapter", // Internal.
    "com.squareup.moshi.internal.Util\$GenericArrayTypeImpl", // Internal.
    "com.squareup.moshi.internal.Util\$ParameterizedTypeImpl", // Internal.
    "com.squareup.moshi.internal.Util\$WildcardTypeImpl", // Internal.
  )
  methodExcludes.addAll(
    "com.squareup.moshi.JsonAdapter#indent(java.lang.String)", // Was unintentionally open before
    "com.squareup.moshi.internal.Util#hasNullable(java.lang.annotation.Annotation[])",
    "com.squareup.moshi.internal.Util#jsonAnnotations(java.lang.annotation.Annotation[])",
    "com.squareup.moshi.internal.Util#jsonAnnotations(java.lang.reflect.AnnotatedElement)",
    "com.squareup.moshi.internal.Util#jsonName(java.lang.String, com.squareup.moshi.Json)",
    "com.squareup.moshi.internal.Util#jsonName(java.lang.String, java.lang.reflect.AnnotatedElement)",
    "com.squareup.moshi.internal.Util#resolve(java.lang.reflect.Type, java.lang.Class, java.lang.reflect.Type)",
    "com.squareup.moshi.internal.Util#typeAnnotatedWithAnnotations(java.lang.reflect.Type, java.util.Set)",
  )
  fieldExcludes.addAll(
    "com.squareup.moshi.CollectionJsonAdapter#FACTORY", // False-positive, class is not public anyway
    "com.squareup.moshi.MapJsonAdapter#FACTORY", // Class is not public
    "com.squareup.moshi.ArrayJsonAdapter#FACTORY", // Class is not public
  )
}

tasks.named("check").configure {
  dependsOn(japicmp)
}
