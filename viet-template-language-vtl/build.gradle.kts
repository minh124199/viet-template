description = "Viet Template VTL Language Frontend and Compatibility Contracts"

dependencies {
    api(project(":viet-template-api"))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
