plugins {
    java
    id("io.github.minh124199.viet-template") version "0.2.1-SNAPSHOT"
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("io.github.minh124199:viet-template-api:0.2.1-SNAPSHOT")
    implementation("io.github.minh124199:viet-template-runtime:0.2.1-SNAPSHOT")
    implementation("io.github.minh124199:viet-template-vtl-interpreter:0.2.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test {
    useJUnitPlatform()
}
