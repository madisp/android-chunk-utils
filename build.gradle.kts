plugins {
  java
}

dependencies {
  annotationProcessor(libs.autovalue.compiler)
  implementation(libs.autovalue.annotations)
  implementation(libs.guava)
  implementation(libs.findbugs.jsr305)
}

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}
