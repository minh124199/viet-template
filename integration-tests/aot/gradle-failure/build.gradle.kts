plugins {
    java
    id("io.github.minh124199.viet-template") version "1.2.1-SNAPSHOT"
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("io.github.minh124199:viet-template-api:1.2.1-SNAPSHOT")
    implementation("io.github.minh124199:viet-template-runtime:1.2.1-SNAPSHOT")
    implementation("io.github.minh124199:viet-template-vtl-interpreter:1.2.1-SNAPSHOT")
}
