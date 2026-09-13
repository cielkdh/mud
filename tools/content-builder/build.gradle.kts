plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass = "com.imsi.mud.content.builder.BuilderCliKt"
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation(project(":core:content"))
    implementation(libs.sqlite.jdbc)
    implementation(libs.jna.platform)
    implementation(libs.webp.imageio)
    testImplementation(libs.junit4)
}

tasks.withType<Test>().configureEach {
    doFirst {
        systemProperty("phase1.testRuntimeClasspath", sourceSets["test"].runtimeClasspath.asPath)
    }
}
