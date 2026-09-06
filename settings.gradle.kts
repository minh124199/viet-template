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
include("viet-template-tck")
