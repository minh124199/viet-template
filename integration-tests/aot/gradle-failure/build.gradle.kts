plugins {
    java
    id("io.github.minh124199.viet-template") version "1.0.0-RC2"
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("io.github.minh124199:viet-template-api:1.0.0-RC2")
    implementation("io.github.minh124199:viet-template-runtime:1.0.0-RC2")
    implementation("io.github.minh124199:viet-template-vtl-interpreter:1.0.0-RC2")
}
