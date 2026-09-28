plugins {
    java
    id("io.github.minh124199.viet-template") version "1.1.0-SNAPSHOT"
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("io.github.minh124199:viet-template-api:1.1.0-SNAPSHOT")
    implementation("io.github.minh124199:viet-template-runtime:1.1.0-SNAPSHOT")
    implementation("io.github.minh124199:viet-template-vtl-interpreter:1.1.0-SNAPSHOT")
}
