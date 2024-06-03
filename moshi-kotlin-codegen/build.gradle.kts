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
    freeCompilerArgs.addAll(
      "-opt-in=com.squareup.kotlinpoet.metadata.KotlinPoetMetadataPreview",
      "-opt-in=com.squareup.moshi.kotlin.codegen.api.InternalMoshiCodegenApi",
    )
  }
}

tasks.compileTestKotlin {
  compilerOptions {
    freeCompilerArgs.add("-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
  }
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
  testImplementation(libs.kotlinpoet.metadata)
  testImplementation(libs.kotlinpoet.ksp)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.kotlinCompileTesting)
}

configure<MavenPublishBaseExtension> {
  configure(KotlinJvm(javadocJar = None()))
}
