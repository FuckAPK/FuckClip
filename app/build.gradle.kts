plugins {
    id("fuck.android.application")
    id("fuck.compose")
    id("fuck.xposed.modern")
}

android {
    namespace = "org.lyaaz.fuckclip"
    defaultConfig {
        minSdk = 34
    }
}

dependencies {
    implementation(project(":ui"))
    implementation(libs.material)
}
