import me.champeau.gradle.japicmp.JapicmpTask

plugins {
  `java-library`
  id("me.champeau.gradle.japicmp")
}

val baseline = configurations.create("baseline")
val latest = configurations.create("latest")

dependencies {
  baseline("com.squareup.moshi:moshi-adapters:1.12.0") {
    isTransitive = false
    isForce = true
  }
  latest(project(":moshi-adapters"))
}

val japicmp = tasks.register<JapicmpTask>("japicmp") {
  dependsOn("jar")
  oldClasspath = baseline
  newClasspath = latest
  isOnlyBinaryIncompatibleModified = true
  isFailOnModification = true
  txtOutputFile = file("$buildDir/reports/japi.txt")
  isIgnoreMissingClasses = true
  isIncludeSynthetic = true
}

tasks.named("check").configure {
  dependsOn(japicmp)
}
