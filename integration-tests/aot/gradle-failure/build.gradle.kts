plugins {
    java
    id("io.github.minh124199.viet-template") version "1.3.0"
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("io.github.minh124199:viet-template-api:1.3.0")
    implementation("io.github.minh124199:viet-template-runtime:1.3.0")
    implementation("io.github.minh124199:viet-template-vtl-interpreter:1.3.0")
}
