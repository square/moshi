import me.champeau.gradle.japicmp.JapicmpTask

plugins {
  `java-library`
  id("me.champeau.gradle.japicmp")
}

val baseline = configurations.create("baseline")
val latest = configurations.create("latest")

dependencies {
  baseline("com.squareup.moshi:moshi:1.15.2") {
    isTransitive = false
    version {
      strictly("1.14.0")
    }
  }
  latest(project(":moshi"))
}

val japicmp =
  tasks.register<JapicmpTask>("japicmp") {
    dependsOn("jar")
    oldClasspath.from(baseline)
    newClasspath.from(latest)
    onlyBinaryIncompatibleModified.set(true)
    failOnModification.set(true)
    txtOutputFile.set(layout.buildDirectory.file("reports/japi.txt"))
    ignoreMissingClasses.set(true)
    includeSynthetic.set(true)
    classExcludes.addAll(
      // Internal.
      "com.squareup.moshi.AdapterMethodsFactory",
      "com.squareup.moshi.ClassJsonAdapter",
      "com.squareup.moshi.internal.NonNullJsonAdapter",
      "com.squareup.moshi.internal.NullSafeJsonAdapter",
      "com.squareup.moshi.internal.Util\$GenericArrayTypeImpl",
      "com.squareup.moshi.internal.Util\$ParameterizedTypeImpl",
      "com.squareup.moshi.internal.Util\$WildcardTypeImpl",
      // Package-private
      "com.squareup.moshi.RecordJsonAdapter\$ComponentBinding",
      "com.squareup.moshi.StandardJsonAdapters",
    )
    methodExcludes.addAll(
      // Was unintentionally open before
      "com.squareup.moshi.JsonAdapter#indent(java.lang.String)",
      "com.squareup.moshi.internal.Util#hasNullable(java.lang.annotation.Annotation[])",
      "com.squareup.moshi.internal.Util#jsonAnnotations(java.lang.annotation.Annotation[])",
      "com.squareup.moshi.internal.Util#jsonAnnotations(java.lang.reflect.AnnotatedElement)",
      "com.squareup.moshi.internal.Util#jsonName(java.lang.String, com.squareup.moshi.Json)",
      "com.squareup.moshi.internal.Util#jsonName(java.lang.String, java.lang.reflect.AnnotatedElement)",
      "com.squareup.moshi.internal.Util#resolve(java.lang.reflect.Type, java.lang.Class, java.lang.reflect.Type)",
      "com.squareup.moshi.internal.Util#typeAnnotatedWithAnnotations(java.lang.reflect.Type, java.util.Set)",
    )
    fieldExcludes.addAll(
      // False-positive, class is not public anyway
      "com.squareup.moshi.CollectionJsonAdapter#FACTORY",
      // Class is not public
      "com.squareup.moshi.MapJsonAdapter#FACTORY",
      // Class is not public
      "com.squareup.moshi.ArrayJsonAdapter#FACTORY",
    )
  }

tasks.named("check").configure {
  dependsOn(japicmp)
}
