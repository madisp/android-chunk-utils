plugins {
  java
  alias(libs.plugins.maven.publish)
}

dependencies {
  annotationProcessor(libs.autovalue.compiler)
  implementation(libs.autovalue.annotations)
  implementation(libs.guava)
  implementation(libs.jb.annotations)
}

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}
