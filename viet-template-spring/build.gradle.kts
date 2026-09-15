description = "Viet Template Spring Framework and Spring MVC Integration"

dependencies {
    api(project(":viet-template-api"))
    api(project(":viet-template-runtime"))
    api(libs.spring.context)
    api(libs.spring.webmvc)
    compileOnly(libs.jakarta.servlet.api)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.spring.test)
    testImplementation(libs.jakarta.servlet.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
