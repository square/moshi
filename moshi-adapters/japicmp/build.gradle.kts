import me.champeau.gradle.japicmp.JapicmpTask

plugins {
  `java-library`
  id("me.champeau.gradle.japicmp")
}

val baseline = configurations.create("baseline")
val latest = configurations.create("latest")

dependencies {
  baseline("com.squareup.moshi:moshi-adapters:1.15.2") {
    isTransitive = false
    version {
      strictly("1.14.0")
    }
  }
  latest(project(":moshi-adapters"))
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
  }

tasks.named("check").configure {
  dependsOn(japicmp)
}
