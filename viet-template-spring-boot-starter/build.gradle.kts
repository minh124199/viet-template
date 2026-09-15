description = "Viet Template Spring Boot Starter"

dependencies {
    api(project(":viet-template-spring"))
    api(project(":viet-template-spring-boot-autoconfigure"))
    api(project(":viet-template-vtl-interpreter"))
    api(libs.spring.boot.starter)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
