plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.wordflip"
    buildFeatures {
        compose = true
        buildConfig = true
    }
    defaultConfig {
        applicationId = "com.wordflip"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            // 真机 USB 调试：先执行 adb reverse tcp:8080 tcp:8080，再 installDebug
            buildConfigField("String", "API_BASE_URL", "\"http://127.0.0.1:8080/api/v1/\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "API_BASE_URL", "\"http://127.0.0.1:8080/api/v1/\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(project(":core-ui"))
    implementation(project(":core-model"))
    implementation(project(":core-network"))
    implementation(project(":core-image"))
    implementation(project(":feature-auth"))
    implementation(project(":feature-today"))
    implementation(project(":feature-books"))
    implementation(project(":feature-groups"))
    implementation(project(":feature-study"))
    implementation(project(":feature-quiz"))
    implementation(project(":feature-stats"))
    implementation(project(":feature-settings"))
    implementation(project(":feature-snapshot"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.coil.compose)
    // Kotlin 2.2+ 使用 KSP 替代 kapt 处理 Hilt 注解
    ksp(libs.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling.preview)
}
