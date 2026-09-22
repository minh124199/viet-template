plugins {
    java
    id("io.github.minh124199.viet-template") version "0.3.0-SNAPSHOT"
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("io.github.minh124199:viet-template-api:0.3.0-SNAPSHOT")
    implementation("io.github.minh124199:viet-template-runtime:0.3.0-SNAPSHOT")
    implementation("io.github.minh124199:viet-template-vtl-interpreter:0.3.0-SNAPSHOT")
}
