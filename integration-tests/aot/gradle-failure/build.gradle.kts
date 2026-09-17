plugins {
    java
    id("io.github.minh124199.viet-template") version "0.2.1"
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("io.github.minh124199:viet-template-api:0.2.1")
    implementation("io.github.minh124199:viet-template-runtime:0.2.1")
    implementation("io.github.minh124199:viet-template-vtl-interpreter:0.2.1")
}
