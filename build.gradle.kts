plugins {
    id("com.android.application") version "7.4.2" apply false
    id("com.android.library") version "7.4.2" apply false
    id("org.jetbrains.kotlin.android") version "1.8.22" apply false
}

// Values expected by the legacy Groovy build scripts inside BlackBox.
extra["compileSdkVersion"] = 30
extra["buildToolsVersion"] = "30.0.3"
extra["targetSdkVersion"] = 28
extra["versionName"] = "1.0.0"
extra["versionCode"] = 1
extra["xVersion"] = "1.1.0"
extra["blackReflection"] = "1.1.2"
