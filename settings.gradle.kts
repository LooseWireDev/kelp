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

// Composite Light SDK checkout. Override with -Ptide.sdkPath=... when the SDK
// lives elsewhere (e.g. CI), defaults to the adjacent checkout.
val lightSdkPath = providers.gradleProperty("tide.sdkPath").getOrElse("../light-sdk")

includeBuild(lightSdkPath) {
    dependencySubstitution {
        substitute(module("com.thelightphone:ui")).using(project(":sdk:ui"))
        substitute(module("com.thelightphone:client")).using(project(":sdk:client"))
        substitute(module("com.thelightphone:server")).using(project(":sdk:server"))
        substitute(module("com.thelightphone:shared")).using(project(":sdk:shared"))
    }
}
