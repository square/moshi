plugins {
  kotlin("jvm")
  kotlin("kapt")
}

dependencies {
  kapt(project(":moshi-kotlin-codegen"))
  compileOnly(libs.jsr305)
  implementation(project(":moshi"))
  implementation(project(":moshi-adapters"))
}
