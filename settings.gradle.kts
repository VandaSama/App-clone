pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        jcenter()
        maven("https://jitpack.io")
        // Fallback untuk beberapa dependency lama BlackBox yang dahulu dipublikasikan di JCenter.
        maven("https://jcenter.bintray.com")
    }
}

rootProject.name = "MultiProfilFB"

include(":app")
include(":Bcore")
include(":Bcore:black-fake")
include(":Bcore:black-hook")
include(":Bcore:pine-core")
include(":Bcore:pine-xposed")
include(":Bcore:pine-xposed-res")
include(":android-mirror")

// The GitHub Actions workflow checks the BlackBox source out here.
project(":Bcore").projectDir = file("vendor/BlackBox/Bcore")
project(":Bcore:black-fake").projectDir = file("vendor/BlackBox/Bcore/black-fake")
project(":Bcore:black-hook").projectDir = file("vendor/BlackBox/Bcore/black-hook")
project(":Bcore:pine-core").projectDir = file("vendor/BlackBox/Bcore/pine-core")
project(":Bcore:pine-xposed").projectDir = file("vendor/BlackBox/Bcore/pine-xposed")
project(":Bcore:pine-xposed-res").projectDir = file("vendor/BlackBox/Bcore/pine-xposed-res")
project(":android-mirror").projectDir = file("vendor/BlackBox/android-mirror")
