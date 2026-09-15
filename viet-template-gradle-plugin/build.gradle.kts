plugins {
    `java-gradle-plugin`
    `maven-publish`
}

description = "Official build-time AOT template compiler for Viet Template"

gradlePlugin {
    plugins {
        create("vietTemplate") {
            id = "io.github.minh124199.viet-template"
            implementationClass = "io.github.minh124199.viettemplate.tooling.gradle.VietTemplatePlugin"
            displayName = "Viet Template AOT Plugin"
            description = "Official build-time AOT template compiler for Viet Template"
        }
    }
}

dependencies {
    implementation(project(":viet-template-vtl-interpreter"))
    compileOnly(gradleApi())

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(gradleTestKit())
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
