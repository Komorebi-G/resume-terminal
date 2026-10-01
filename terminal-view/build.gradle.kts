/* Modified for Resume Terminal (personal Moke fork), 2026-10-01.
 * Original copyright and licenses retained; see COPYRIGHT.md. */
// Vendored from termux/termux-app · terminal-view (Apache-2.0), with mobile input and rendering changes.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.termux.view"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("proguard-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(project(":terminal-emulator"))
    implementation(libs.androidx.annotation)
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.14.1")
}
