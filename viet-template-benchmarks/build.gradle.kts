description = "Viet Template Dedicated JMH Benchmark and Profiling Suites"

dependencies {
    implementation(project(":viet-template-api"))
    implementation(project(":viet-template-runtime"))
    implementation(project(":viet-template-language-vtl"))
    implementation(project(":viet-template-vtl-interpreter"))

    implementation(libs.jmh.core)
    annotationProcessor(libs.jmh.generator.annprocess)

    testImplementation(project(":viet-template-api"))
    testImplementation(project(":viet-template-runtime"))
    testImplementation(project(":viet-template-language-vtl"))
    testImplementation(project(":viet-template-vtl-interpreter"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.register<JavaExec>("jmh") {
    group = "benchmark"
    description = "Runs JMH benchmarks using org.openjdk.jmh.Main"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.openjdk.jmh.Main")

    val jmhArgs = project.findProperty("jmhArgs") as String?
    if (!jmhArgs.isNullOrBlank()) {
        args(jmhArgs.split("\\s+".toRegex()))
    }

    jvmArgs(
        "-server",
        "-Xms2g",
        "-Xmx2g",
        "-XX:+AlwaysPreTouch",
        "-XX:+UseG1GC"
    )
}

val benchmarkJar = tasks.register<Jar>("benchmarkJar") {
    group = "benchmark"
    description = "Packages a self-contained executable JMH benchmark JAR"
    archiveFileName.set("benchmarks.jar")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(configurations.runtimeClasspath)

    manifest {
        attributes["Main-Class"] = "org.openjdk.jmh.Main"
    }

    from(sourceSets["main"].output)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) }
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
}
