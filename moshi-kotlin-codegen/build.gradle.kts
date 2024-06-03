import com.vanniktech.maven.publish.JavadocJar.None
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  id("com.google.devtools.ksp")
  id("com.vanniktech.maven.publish.base")
}

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions {
    optIn.add("com.squareup.moshi.kotlin.codegen.api.InternalMoshiCodegenApi")
  }
}

tasks.compileTestKotlin {
  compilerOptions {
    optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
  }
}

tasks.test {
  // KSP2 needs more memory to run until 1.0.21
  minHeapSize = "2048m"
  maxHeapSize = "2048m"
  // Disable the annoying GradleWorkerMain apps that pop up while running
  jvmArgs("-Djava.awt.headless=true")
}

dependencies {
  implementation(project(":moshi"))
  api(libs.kotlinpoet)
  implementation(libs.kotlinpoet.ksp)
  implementation(libs.guava)
  implementation(libs.asm)

  implementation(libs.autoService)
  ksp(libs.autoService.ksp)

  // KSP deps
  compileOnly(libs.ksp)
  compileOnly(libs.ksp.api)
  compileOnly(libs.kotlin.compilerEmbeddable)
  // Always force the latest KSP version to match the one we're compiling against
  testImplementation(libs.ksp)
  testImplementation(libs.ksp.api)
  testImplementation(libs.kotlin.compilerEmbeddable)
  testImplementation(libs.kotlin.annotationProcessingEmbeddable)
  testImplementation(libs.kotlinCompileTesting.ksp)

  // Copy these again as they're not automatically included since they're shaded
  testImplementation(project(":moshi"))
  testImplementation(kotlin("reflect"))
  testImplementation(libs.kotlinpoet.ksp)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.kotlinCompileTesting)
}

configure<MavenPublishBaseExtension> {
  configure(KotlinJvm(javadocJar = None()))
}
