description = "Viet Template Spring Boot Auto-Configuration"

dependencies {
    api(project(":viet-template-api"))
    api(project(":viet-template-runtime"))
    api(project(":viet-template-spring"))
    api(libs.spring.boot)
    api(libs.spring.boot.autoconfigure)

    annotationProcessor(libs.spring.boot.configuration.processor)
    compileOnly(libs.spring.boot.configuration.processor)
    compileOnly(libs.jakarta.servlet.api)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.web)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-processing")
}
