import com.vanniktech.maven.publish.JavadocJar.None
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation.Companion.MAIN_COMPILATION_NAME
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  id("com.vanniktech.maven.publish.base")
  id("org.jetbrains.dokka")
}

kotlin.target {
  val main = compilations.getByName(MAIN_COMPILATION_NAME)
  val java16 =
    compilations.create("java16") {
      associateWith(main)
      defaultSourceSet.kotlin.srcDir("src/main/java16")
      compileJavaTaskProvider.configure {
        options.release = 16
      }
      compileTaskProvider.configure {
        (compilerOptions as KotlinJvmCompilerOptions).jvmTarget = JvmTarget.JVM_16
      }
    }

  // Package our actual RecordJsonAdapter from java16 sources in and denote it as an MRJAR
  tasks.named<Jar>(artifactsTaskName) {
    from(java16.output) {
      into("META-INF/versions/16")
      exclude("META-INF")
    }
    manifest {
      attributes("Multi-Release" to "true")
    }
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
  configure(KotlinJvm(javadocJar = None()))
}
