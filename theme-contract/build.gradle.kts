plugins {
    id("com.android.library")
}

version = "1.0.0"

android {
    namespace = "org.shilpo.laboon.theme.contract"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation(libs.junit)
}
