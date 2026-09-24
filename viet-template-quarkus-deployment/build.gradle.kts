description = "Viet Template Quarkus Extension Deployment and AOT Build Tooling"

dependencies {
    implementation(project(":viet-template-quarkus"))
    implementation(project(":viet-template-vtl-interpreter"))

    implementation(platform(libs.quarkus.bom))
    implementation(libs.quarkus.core.deployment)
    implementation(libs.quarkus.arc.deployment)

    annotationProcessor(platform(libs.quarkus.bom))
    annotationProcessor(libs.quarkus.extension.processor)

    testImplementation(platform(libs.quarkus.bom))
    testImplementation(libs.quarkus.junit.internal)
    testImplementation(libs.quarkus.security.deployment)
    testImplementation(libs.quarkus.qute)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(
        listOf(
            "-Xlint:-processing",
            "-AgenerateDoc=false"
        )
    )
}

tasks.test {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}
