plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.imsi.mud.image"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation(project(":core:content"))
    api(libs.coil.compose)
    testImplementation(libs.junit4)
}
