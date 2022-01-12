import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  kotlin("kapt")
}

dependencies {
  kapt(project(":moshi-kotlin-codegen"))
  compileOnly(libs.jsr305)
  implementation(project(":moshi"))
  implementation(project(":moshi-adapters"))
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    freeCompilerArgs += listOf(
      "-Xopt-in=kotlin.ExperimentalStdlibApi"
    )
  }
}
