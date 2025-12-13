import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import org.gradle.kotlin.dsl.support.uppercaseFirstChar

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.prateekmahendrakar.metadatawiper"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.prateekmahendrakar.metadatawiper"
        minSdk = 26
        targetSdk = 36
        versionCode = 101
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    applicationVariants.configureEach {
        // rename the output APK file
        outputs.configureEach {
            (this as? ApkVariantOutputImpl)?.outputFileName =
                "${rootProject.name}_${versionName}_${buildType.name}.apk"
        }

        // rename the output AAB file
        tasks.named(
            "sign${flavorName.uppercaseFirstChar()}${buildType.name.uppercaseFirstChar()}Bundle",
            com.android.build.gradle.internal.tasks.FinalizeBundleTask::class.java
        ) {
            val file = finalBundleFile.asFile.get()
            val finalFile =
                File(
                    file.parentFile,
                    "${rootProject.name}_${versionName}_${buildType.name}.aab"
                )
            finalBundleFile.set(finalFile)
        }
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.exifinterface)
    implementation(libs.coil.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}