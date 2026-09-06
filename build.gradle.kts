plugins {
    base
    alias(libs.plugins.spotless) apply false
}

allprojects {
    group = "io.github.minh124199"
    version = "0.1.1-SNAPSHOT"
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "com.diffplug.spotless")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
        withJavadocJar()
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(17)
        options.compilerArgs.addAll(
            listOf(
                "-parameters",
                "-Xlint:all",
                "-Werror",
                "-Xpkginfo:always"
            )
        )
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
        systemProperties(
            System.getProperties()
                .filter { it.key.toString().startsWith("viet.") }
                .mapKeys { it.key.toString() }
        )
    }

    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).apply {
            encoding = "UTF-8"
            addStringOption("Xdoclint:none", "-quiet")
        }
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            target("src/*/java/**/*.java")
            googleJavaFormat("1.24.0").reflowLongStrings()
            formatAnnotations()
        }
    }

    // Publication configuration for production modules.
    // NOTE: Apache Maven is the single authoritative release publisher for Maven Central.
    // Gradle publication is maintained for local installation (publishToMavenLocal),
    // POM metadata verification, and dual-build parity validation.
    // The test module (:viet-template-tck) intentionally defines zero publications.
    if (project.name != "viet-template-tck") {
        apply(plugin = "maven-publish")
        apply(plugin = "signing")

        configure<PublishingExtension> {
            publications {
                create<MavenPublication>("mavenJava") {
                    from(components["java"])
                    pom {
                        name.set(project.name)
                        description.set(provider { project.description })
                        url.set("https://github.com/minh124199/viet-template")
                        licenses {
                            license {
                                name.set("Apache License, Version 2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                                distribution.set("repo")
                            }
                        }
                        developers {
                            developer {
                                id.set("minh124199")
                                name.set("Minh Nguyen")
                                url.set("https://github.com/minh124199")
                            }
                        }
                        scm {
                            connection.set("scm:git:git://github.com/minh124199/viet-template.git")
                            developerConnection.set("scm:git:ssh://github.com:minh124199/viet-template.git")
                            url.set("https://github.com/minh124199/viet-template")
                        }
                    }
                }
            }
        }

        configure<SigningExtension> {
            val signingKey = findProperty("signingKey") as String? ?: System.getenv("SIGNING_KEY")
            val signingPassword = findProperty("signingPassword") as String? ?: System.getenv("SIGNING_PASSWORD")
            val hasKey = !signingKey.isNullOrBlank()
            isRequired = hasKey
            if (hasKey) {
                useInMemoryPgpKeys(signingKey, signingPassword)
                sign(extensions.getByType<PublishingExtension>().publications["mavenJava"])
            }
        }
    }
}
