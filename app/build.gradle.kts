import java.util.*

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
}

fun String.execute(currentWorkingDir: File = file("./")): String {
    return providers.exec {
        isIgnoreExitValue = true
        workingDir = currentWorkingDir
        commandLine = split("\\s".toRegex())
    }.standardOutput.asText.get().trim()
}

android {
    namespace = "org.lyaaz.fuckclip"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.lyaaz.fuckclip"
        minSdk = 34
        targetSdk = 36
        versionCode = "git rev-list HEAD --count".execute().toInt()
        versionName = "git describe --tag --always".execute().removePrefix("v")
        resourceConfigurations += "en"
        vectorDrawables.useSupportLibrary = true
    }
    signingConfigs {
        create("release") {
            val properties = Properties().apply {
                load(rootProject.file("signing.properties").reader())
            }
            storeFile = rootProject.file(properties.getProperty("storeFilePath"))
            storePassword = properties.getProperty("storePassword")
            keyPassword = properties.getProperty("keyPassword")
            keyAlias = properties.getProperty("keyAlias")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
}
dependencies {
    compileOnly("io.github.libxposed:api:101.0.1")
    implementation("io.github.libxposed:service:101.0.0")
    implementation("com.google.android.material:material:1.12.0")

    // compose
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)

    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose")

    // Android Studio Preview support
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
