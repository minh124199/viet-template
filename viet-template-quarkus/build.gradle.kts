description = "Viet Template Quarkus Extension Runtime Integration"

dependencies {
    api(project(":viet-template-api"))
    api(project(":viet-template-runtime"))
    api(project(":viet-template-vtl-interpreter"))

    implementation(platform(libs.quarkus.bom))
    implementation(libs.quarkus.core)
    implementation(libs.quarkus.arc)
    compileOnly(libs.quarkus.security)
    testImplementation(libs.quarkus.security)

    annotationProcessor(platform(libs.quarkus.bom))
    annotationProcessor(libs.quarkus.extension.processor)

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
