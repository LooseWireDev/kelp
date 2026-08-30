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
        substitute(module("com.thelightphone:ui")).using(project(":sdk:ui"))
        substitute(module("com.thelightphone:client")).using(project(":sdk:client"))
        substitute(module("com.thelightphone:server")).using(project(":sdk:server"))
        substitute(module("com.thelightphone:shared")).using(project(":sdk:shared"))
    }
}
