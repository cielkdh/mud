plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation(project(":core:content"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit4)
}

