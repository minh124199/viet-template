pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "viet-template"

include("viet-template-api")
include("viet-template-runtime")
include("viet-template-language-vtl")
include("viet-template-vtl-interpreter")
include("viet-template-spring")
include("viet-template-spring-security")
include("viet-template-spring-boot-autoconfigure")
include("viet-template-spring-boot-starter")
include("viet-template-tck")
include("viet-template-benchmarks")
include("viet-template-maven-plugin")
include("viet-template-gradle-plugin")
include("viet-template-quarkus")
include("viet-template-quarkus-deployment")

