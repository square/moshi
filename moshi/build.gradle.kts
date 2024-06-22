import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  id("com.vanniktech.maven.publish.base")
  id("org.jetbrains.dokka")
}

val mainSourceSet by sourceSets.named("main")
val java16: SourceSet by sourceSets.creating {
  java {
    srcDir("src/main/java16")
  }
}

// We use newer JDKs but target 16 for maximum compatibility
val service = project.extensions.getByType<JavaToolchainService>()
val customLauncher =
  service.launcherFor {
    languageVersion.set(libs.versions.jdk.map(JavaLanguageVersion::of))
  }

tasks.named<JavaCompile>("compileJava16Java") {
  options.release.set(16)
}

tasks.named<KotlinCompile>("compileJava16Kotlin") {
  kotlinJavaToolchain.toolchain.use(customLauncher)
  compilerOptions.jvmTarget.set(JvmTarget.JVM_16)
}

// Grant our java16 sources access to internal APIs in the main source set
kotlin.target.compilations.run {
  getByName("java16")
    .associateWith(getByName(KotlinCompilation.MAIN_COMPILATION_NAME))
}

// Package our actual RecordJsonAdapter from java16 sources in and denote it as an MRJAR
tasks.named<Jar>("jar") {
  from(java16.output) {
    into("META-INF/versions/16")
  }
  manifest {
    attributes("Multi-Release" to "true")
  }
}

configurations {
  "java16Implementation" {
    extendsFrom(api.get())
    extendsFrom(implementation.get())
  }
}

tasks.withType<Test>().configureEach {
  // ExtendsPlatformClassWithProtectedField tests a case where we set a protected ByteArrayOutputStream.buf field
  jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED")
}

tasks
  .withType<KotlinCompile>()
  .configureEach {
    compilerOptions {
      freeCompilerArgs.addAll(
        "-opt-in=kotlin.contracts.ExperimentalContracts",
        "-Xjvm-default=all",
      )
      if (name.contains("test", true)) {
        freeCompilerArgs.add("-opt-in=kotlin.ExperimentalStdlibApi")
      }
    }
  }

dependencies {
  // So the j16 source set can "see" main Moshi sources
  "java16Implementation"(mainSourceSet.output)
  compileOnly(libs.jsr305)
  api(libs.okio)

  testCompileOnly(libs.jsr305)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
}

tasks.withType<Jar>().configureEach {
  manifest {
    attributes("Automatic-Module-Name" to "com.squareup.moshi")
  }
}

configure<MavenPublishBaseExtension> {
  configure(KotlinJvm(javadocJar = Dokka("dokkaGfm")))
}
