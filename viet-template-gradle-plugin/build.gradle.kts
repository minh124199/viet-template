plugins {
    `java-gradle-plugin`
    `maven-publish`
    signing
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

publishing {
    repositories {
        maven {
            name = "rcRepository"
            url = uri(rootProject.layout.buildDirectory.dir("rc-repository"))
        }
    }

    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set(project.name)
            description.set(provider { project.description })
            url.set("https://github.com/minh124199/viet-template/tree/main/${project.name}")
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
                connection.set("scm:git:https://github.com/minh124199/viet-template.git")
                developerConnection.set("scm:git:ssh://git@github.com/minh124199/viet-template.git")
                url.set("https://github.com/minh124199/viet-template")
            }
        }
    }
}

signing {
    val signingKey = findProperty("signingKey") as String? ?: System.getenv("SIGNING_KEY")
    val signingPassword = findProperty("signingPassword") as String? ?: System.getenv("SIGNING_PASSWORD")
    val hasKey = !signingKey.isNullOrBlank()
    isRequired = hasKey
    if (hasKey) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["pluginMaven"])
        sign(publishing.publications["vietTemplatePluginMarkerMaven"])
    }
}

