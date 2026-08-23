pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JitPack for TIDAL SDK dependencies
        maven {
            name = "JitPack"
            url = uri("https://jitpack.io")
        }
    }
}

rootProject.name = "tide"

include(":app")
include(":protocol")
include(":server")

includeBuild("../light-sdk") {
    dependencySubstitution {
        substitute(module("com.thelightphone:sdk-ui")).using(project(":sdk:ui"))
        substitute(module("com.thelightphone:sdk-client")).using(project(":sdk:client"))
        substitute(module("com.thelightphone:sdk-server")).using(project(":sdk:server"))
        substitute(module("com.thelightphone:sdk-shared")).using(project(":sdk:shared"))
    }
}
