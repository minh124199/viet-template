description = "Viet Template Technology Compatibility Kit and Architectural Guardrails"

dependencies {
    implementation(project(":viet-template-api"))
    implementation(project(":viet-template-runtime"))
    implementation(project(":viet-template-language-vtl"))
    implementation(project(":viet-template-vtl-interpreter"))

    testImplementation(project(":viet-template-api"))
    testImplementation(project(":viet-template-runtime"))
    testImplementation(project(":viet-template-language-vtl"))
    testImplementation(project(":viet-template-vtl-interpreter"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.velocity.engine.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
