description = "Viet Template Low-Allocation Runtime and Output Primitives"

dependencies {
    api(project(":viet-template-api"))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
