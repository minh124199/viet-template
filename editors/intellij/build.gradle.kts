import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "io.github.minh124199"
version = "1.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.2.4")
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.3")
}

intellijPlatform {
    pluginConfiguration {
        id = "io.github.minh124199.viet-template-intellij"
        name = "Viet Template"
        version = "1.1.0"
        vendor {
            name = "Viet Template"
            email = "minh124199@users.noreply.github.com"
            url = "https://github.com/minh124199/viet-template"
        }
        ideaVersion {
            sinceBuild = "242"
            untilBuild = "251.*"
        }
    }
    buildSearchableOptions = false
}

val lspModules = listOf(
    "viet-template-api",
    "viet-template-runtime",
    "viet-template-language-vtl",
    "viet-template-vtl-interpreter"
)

val bundleLspServer = tasks.register<Jar>("bundleLspServer") {
    archiveFileName.set("viet-template-lsp.jar")
    destinationDirectory.set(layout.buildDirectory.dir("server"))
    manifest {
        attributes["Main-Class"] = "io.github.minh124199.viettemplate.lsp.VietTemplateLanguageServer"
        attributes["Created-By"] = "viet-template-intellij-bundle"
    }

    val repoRoot = rootDir.parentFile.parentFile
    for (moduleName in lspModules) {
        val moduleDir = File(repoRoot, moduleName)
        val gradleClasses = File(moduleDir, "build/classes/java/main")
        val gradleResources = File(moduleDir, "build/resources/main")
        val mavenClasses = File(moduleDir, "target/classes")
        val gradleLibs = File(moduleDir, "build/libs")
        val mavenTarget = File(moduleDir, "target")

        if (gradleClasses.isDirectory) {
            from(gradleClasses)
        }
        if (gradleResources.isDirectory) {
            from(gradleResources)
        }
        if (mavenClasses.isDirectory) {
            from(mavenClasses)
        }
        if (!gradleClasses.isDirectory && !mavenClasses.isDirectory) {
            val jarFile = gradleLibs.listFiles()?.firstOrNull { it.name.startsWith(moduleName) && it.name.endsWith(".jar") && !it.name.endsWith("-sources.jar") && !it.name.endsWith("-javadoc.jar") }
                ?: mavenTarget.listFiles()?.firstOrNull { it.name.startsWith(moduleName) && it.name.endsWith(".jar") && !it.name.endsWith("-sources.jar") && !it.name.endsWith("-javadoc.jar") }
            if (jarFile != null && jarFile.isFile) {
                from(zipTree(jarFile))
            }
        }
    }

    // Safety fallback: if no module classes found, check vscode bundled jar
    val vscodeJar = File(repoRoot, "editors/vscode/server/viet-template-lsp.jar")
    if (vscodeJar.isFile) {
        from(zipTree(vscodeJar))
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(bundleLspServer)
    from(bundleLspServer.map { it.archiveFile }) {
        into("server")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
