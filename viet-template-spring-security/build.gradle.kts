description = "Viet Template Spring Security Integration"

dependencies {
    api(project(":viet-template-api"))
    api(project(":viet-template-spring"))
    api(libs.spring.security.core)
    api(libs.spring.security.web)
    compileOnly(libs.jakarta.servlet.api)

    testImplementation(project(":viet-template-vtl-interpreter"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.spring.test)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.jakarta.servlet.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
