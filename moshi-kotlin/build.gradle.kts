import org.gradle.jvm.tasks.Jar

plugins {
  kotlin("jvm")
  id("com.vanniktech.maven.publish")
  id("org.jetbrains.dokka")
}

dependencies {
  api(project(":moshi"))
  implementation(libs.kotlin.metadata)

  testImplementation(kotlin("test"))
  testImplementation(libs.junit)
  testImplementation(libs.truth)
}

tasks.withType<Jar>().configureEach {
  manifest { attributes("Automatic-Module-Name" to "com.squareup.moshi.kotlin") }
}
