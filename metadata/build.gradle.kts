plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    testImplementation(libs.junit)
}
