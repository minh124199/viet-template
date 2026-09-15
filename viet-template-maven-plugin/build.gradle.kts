description = "Official build-time AOT template compiler Maven plugin for Viet Template"

dependencies {
    implementation(project(":viet-template-vtl-interpreter"))
    compileOnly("org.apache.maven:maven-plugin-api:3.9.9")
    compileOnly("org.apache.maven:maven-core:3.9.9")
    compileOnly("org.apache.maven.plugin-tools:maven-plugin-annotations:3.13.0")

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation("org.apache.maven:maven-plugin-api:3.9.9")
    testImplementation("org.apache.maven:maven-core:3.9.9")
    testImplementation("org.apache.maven.plugin-tools:maven-plugin-annotations:3.13.0")
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
