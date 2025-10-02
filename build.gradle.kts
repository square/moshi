import com.diffplug.gradle.spotless.JavaExtension
import com.google.devtools.ksp.gradle.KspTaskJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
  dependencies {
    val kotlinVersion =
      System.getenv("MOSHI_KOTLIN_VERSION")
        ?: libs.versions.kotlin.get()
    val kspVersion =
      System.getenv("MOSHI_KSP_VERSION")
        ?: libs.versions.ksp.get()
    classpath(kotlin("gradle-plugin", version = kotlinVersion))
    classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:$kspVersion")
  }
}

plugins {
  alias(libs.plugins.mavenPublish) apply false
  alias(libs.plugins.dokka)
  alias(libs.plugins.spotless)
  alias(libs.plugins.japicmp) apply false
  alias(libs.plugins.ksp) apply false
}

allprojects {
  group = "com.squareup.moshi"
  version = "2.0.0-SNAPSHOT"

  repositories {
    mavenCentral()
  }
}

spotless {
  format("misc") {
    target("*.md", ".gitignore")
    trimTrailingWhitespace()
    leadingTabsToSpaces(2)
    endWithNewline()
  }
  val configureCommonJavaFormat: JavaExtension.() -> Unit = {
    googleJavaFormat(libs.googleJavaFormat.get().version)
  }
  java {
    configureCommonJavaFormat()
    target("**/*.java")
    targetExclude("**/build/**")
  }
  kotlin {
    ktlint(libs.ktlint.get().version).editorConfigOverride(
      mapOf(
        "ktlint_standard_filename" to "disabled",
        // Making something an expression body should be a choice around readability.
        "ktlint_standard_function-expression-body" to "disabled",
      ),
    )
    target("**/*.kt")
    trimTrailingWhitespace()
    endWithNewline()
    targetExclude("**/Dependencies.kt", "**/build/**")
  }
  kotlinGradle {
    ktlint(libs.ktlint.get().version)
    target("**/*.gradle.kts")
    trimTrailingWhitespace()
    endWithNewline()
  }
}

subprojects {
  // Apply with "java" instead of just "java-library" so kotlin projects get it too
  pluginManager.withPlugin("java") {
    configure<JavaPluginExtension> {
      toolchain {
        languageVersion.set(libs.versions.jdk.map(JavaLanguageVersion::of))
      }
    }
    if (project.name != "records-tests") {
      tasks.withType<JavaCompile>().configureEach {
        options.release.set(8)
      }
    }
  }

  pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    tasks.withType<KotlinCompile>().configureEach {
      val isKsp1Task = this is KspTaskJvm
      compilerOptions {
        progressiveMode.set(!isKsp1Task)
        jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvmTarget.get()))
      }
    }

    configure<KotlinProjectExtension> {
      if (project.name != "examples") {
        explicitApi()
      }
    }
  }
}

dependencies {
  dokka(project(":moshi"))
  dokka(project(":moshi-adapters"))
  dokka(project(":moshi-kotlin"))
  dokka(project(":moshi-kotlin-codegen"))
}

dokka {
  dokkaPublications.html {
    outputDirectory.set(layout.projectDirectory.dir("docs/2.x"))
  }
}

subprojects {
  plugins.withId("org.jetbrains.dokka") {
    configure<DokkaExtension> {
      basePublicationsDirectory.set(layout.buildDirectory.dir("dokkaDir"))
      dokkaSourceSets.configureEach {
        skipDeprecated.set(true)
        reportUndocumented.set(true)
        jdkVersion.set(8)
        perPackageOption {
          matchingRegex.set("com\\.squareup\\.moshi\\.internal.*")
          suppress.set(true)
        }
        externalDocumentationLinks.register("Okio") {
          packageListUrl("https://square.github.io/okio/3.x/okio/okio/package-list")
          url("https://square.github.io/okio/3.x/okio")
        }
        sourceLink {
          localDirectory.set(layout.projectDirectory.dir("src"))
          val relPath =
            rootProject.isolated.projectDirectory.asFile
              .toPath()
              .relativize(projectDir.toPath())
          remoteUrl("https://github.com/square/moshi/tree/main/$relPath/src")
          remoteLineSuffix.set("#L")
        }
      }
    }
  }

  plugins.withId("com.vanniktech.maven.publish.base") {
    configure<MavenPublishBaseExtension> {
      publishToMavenCentral(automaticRelease = true)
      signAllPublications()
      pom {
        description.set("A modern JSON API for Android and Java")
        name.set(project.name)
        url.set("https://github.com/square/moshi/")
        licenses {
          license {
            name.set("The Apache Software License, Version 2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            distribution.set("repo")
          }
        }
        scm {
          url.set("https://github.com/square/moshi/")
          connection.set("scm:git:git://github.com/square/moshi.git")
          developerConnection.set("scm:git:ssh://git@github.com/square/moshi.git")
        }
        developers {
          developer {
            id.set("square")
            name.set("Square, Inc.")
          }
        }
      }
    }
  }
}
