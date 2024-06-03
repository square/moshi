import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  alias(libs.plugins.ksp)
}

dependencies {
  ksp(project(":moshi-kotlin-codegen"))
  compileOnly(libs.jsr305)
  implementation(project(":moshi"))
  implementation(project(":moshi-adapters"))
}

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions {
    freeCompilerArgs.add(
      "-opt-in=kotlin.ExperimentalStdlibApi",
    )
  }
}
