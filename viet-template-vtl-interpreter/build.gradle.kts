description = "Viet Template VTL Reference Interpreter and Runtime Semantics"

dependencies {
    api(project(":viet-template-api"))
    api(project(":viet-template-runtime"))
    api(project(":viet-template-language-vtl"))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
