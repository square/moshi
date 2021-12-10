pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

rootProject.name = "moshi-root"
include(":moshi")
include(":moshi:japicmp")
include(":moshi:records-tests")
include(":moshi-adapters")
include(":moshi-adapters:japicmp")
include(":examples")
include(":moshi-kotlin")
include(":moshi-kotlin-codegen")
include(":moshi-kotlin-tests")
include(":moshi-kotlin-tests:codegen-only")
include(":moshi-kotlin-tests:extra-moshi-test-module")

enableFeaturePreview("VERSION_CATALOGS")
