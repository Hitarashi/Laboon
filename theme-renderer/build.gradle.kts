plugins {
    id("com.android.library")
    alias(libs.plugins.compose.compiler)
}

version = "1.0.0"

android {
    namespace = "org.shilpo.laboon.theme.renderer"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":theme-contract"))
    api(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
}
