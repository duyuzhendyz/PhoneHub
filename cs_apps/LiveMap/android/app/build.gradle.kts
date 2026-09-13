// LiveMap · 手机端（Kotlin）：实时位置 → POST 给电脑端 map_server.py
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.phonehub.livemap"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.phonehub.livemap"   // 独立包名：与 PhoneHub 主 app 共存，互不影响
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    lint {
        abortOnError = false
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // 测试工程不开混淆，方便直接看堆栈
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDirs("src/main/java")
            res.srcDirs("src/main/res")
        }
    }
}

// 刻意**零第三方依赖**：只用平台 API（Notification.Builder / LocationManager / HttpURLConnection）。
// 这样本机离线也能编（Gradle 缓存里没有 androidx 的传递依赖，如 lifecycle-runtime:2.3.1）。
