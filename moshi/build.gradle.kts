import com.vanniktech.maven.publish.JavadocJar.Javadoc
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.jvm.tasks.Jar
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

// We use JDK 17 for latest but target 16 for maximum compatibility
val service = project.extensions.getByType<JavaToolchainService>()
val customLauncher = service.launcherFor {
  languageVersion.set(JavaLanguageVersion.of(17))
}

tasks.named<KotlinCompile>("compileJava16Kotlin") {
  kotlinJavaToolchain.toolchain.use(customLauncher)
  kotlinOptions.jvmTarget = "16"
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

tasks.withType<KotlinCompile>()
  .configureEach {
    kotlinOptions {
      val toAdd = mutableListOf("-Xopt-in=kotlin.RequiresOptIn", "-Xopt-in=kotlin.contracts.ExperimentalContracts")
      if (name.contains("test", true)) {
        toAdd += "-Xopt-in=kotlin.ExperimentalStdlibApi"
      }
      @Suppress("SuspiciousCollectionReassignment") // It's not suspicious
      freeCompilerArgs += toAdd
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
  configure(KotlinJvm(javadocJar = Javadoc()))
}
