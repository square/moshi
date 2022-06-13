import Build_gradle.TestMode.KAPT
import Build_gradle.TestMode.KSP
import Build_gradle.TestMode.REFLECT
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  kotlin("kapt") apply false
  id("com.google.devtools.ksp") apply false
}

enum class TestMode {
  REFLECT, KAPT, KSP
}

val testMode = findProperty("kotlinTestMode")?.toString()
  ?.let(TestMode::valueOf)
  ?: KSP

when (testMode) {
  REFLECT -> {
    // Default to KSP. This is a CI-only thing
    apply(plugin = "com.google.devtools.ksp")
  }
  KAPT -> {
    apply(plugin = "org.jetbrains.kotlin.kapt")
  }
  KSP -> {
    apply(plugin = "com.google.devtools.ksp")
  }
}

tasks.withType<Test>().configureEach {
  // ExtendsPlatformClassWithProtectedField tests a case where we set a protected ByteArrayOutputStream.buf field
  jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED")
}

val useWError = findProperty("kotlinLanguageVersion")?.toString()
  ?.startsWith("1.5")
  ?: false
tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    allWarningsAsErrors = useWError
    @Suppress("SuspiciousCollectionReassignment")
    freeCompilerArgs += listOf(
      "-opt-in=kotlin.ExperimentalStdlibApi"
    )
  }
}

dependencies {
  when (testMode) {
    REFLECT -> {
      // Default to KSP in this case, this is a CI-only thing
      "kspTest"(project(":moshi-kotlin-codegen"))
    }
    KAPT -> {
      "kaptTest"(project(":moshi-kotlin-codegen"))
    }
    KSP -> {
      "kspTest"(project(":moshi-kotlin-codegen"))
    }
  }
  testImplementation(project(":moshi"))
  testImplementation(project(":moshi-kotlin"))
  testImplementation(project(":moshi-kotlin-tests:extra-moshi-test-module"))
  testImplementation(kotlin("reflect"))
  testImplementation(libs.junit)
  testImplementation(libs.assertj)
  testImplementation(libs.truth)
}
