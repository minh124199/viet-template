plugins { java }

val consumerVersion = providers.gradleProperty("consumerVersion").get()
dependencies {
    implementation("io.github.minh124199:viet-template-vtl-interpreter:$consumerVersion")
}

tasks.register("resolveCentralRuntime") {
    doLast { configurations.runtimeClasspath.get().resolve() }
}
tasks.named("check") { dependsOn("resolveCentralRuntime") }
