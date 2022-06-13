import com.github.jengelman.gradle.plugins.shadow.tasks.ConfigureShadowRelocation
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import com.vanniktech.maven.publish.JavadocJar.None
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  id("com.google.devtools.ksp")
  id("com.vanniktech.maven.publish.base")
  alias(libs.plugins.mavenShadow)
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    @Suppress("SuspiciousCollectionReassignment")
    freeCompilerArgs += listOf(
      "-opt-in=kotlin.RequiresOptIn",
      "-opt-in=com.squareup.kotlinpoet.metadata.KotlinPoetMetadataPreview",
      "-opt-in=com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview",
      "-opt-in=com.squareup.moshi.kotlin.codegen.api.InternalMoshiCodegenApi",
    )
  }
}

// --add-opens for kapt to work. KGP covers this for us but local JVMs in tests do not
tasks.withType<Test>().configureEach {
  jvmArgs(
    "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
  )
}

val shade: Configuration = configurations.maybeCreate("compileShaded")
configurations.getByName("compileOnly").extendsFrom(shade)
dependencies {
  implementation(project(":moshi"))
  shade(libs.kotlinxMetadata) {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
  }
  api(libs.kotlinpoet)
  shade(libs.kotlinpoet.metadata) {
    exclude(group = "org.jetbrains.kotlin")
    exclude(group = "com.squareup", module = "kotlinpoet")
    exclude(group = "com.google.guava")
  }
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

val relocateShadowJar = tasks.register<ConfigureShadowRelocation>("relocateShadowJar") {
  target = tasks.shadowJar.get()
}

val shadowJar = tasks.shadowJar.apply {
  configure {
    dependsOn(relocateShadowJar)
    archiveClassifier.set("")
    configurations = listOf(shade)
    relocate("com.squareup.kotlinpoet.metadata", "com.squareup.moshi.kotlinpoet.metadata")
    relocate(
      "com.squareup.kotlinpoet.classinspector",
      "com.squareup.moshi.kotlinpoet.classinspector"
    )
    relocate("kotlinx.metadata", "com.squareup.moshi.kotlinx.metadata")
    transformers.add(ServiceFileTransformer())
  }
}

artifacts {
  runtimeOnly(shadowJar)
  archives(shadowJar)
}

configure<MavenPublishBaseExtension> {
  configure(KotlinJvm(javadocJar = None()))
}
