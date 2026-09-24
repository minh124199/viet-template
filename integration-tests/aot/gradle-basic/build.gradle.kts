plugins {
    java
    id("io.github.minh124199.viet-template") version "1.0.0-RC1"
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("io.github.minh124199:viet-template-api:1.0.0-RC1")
    implementation("io.github.minh124199:viet-template-runtime:1.0.0-RC1")
    implementation("io.github.minh124199:viet-template-vtl-interpreter:1.0.0-RC1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test {
    useJUnitPlatform()
}
