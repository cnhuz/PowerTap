plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.stwpower.powertap"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.stwpower.powertap"
        minSdk = 25
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // RenderScript配置
        renderscriptTargetApi = 21
        renderscriptSupportModeEnabled = true
    }

    signingConfigs {
        create("system") {
            // 系统证书配置 - 请替换为你的实际证书路径和密码
             storeFile = file("SC20-NEW.keystore")
             storePassword = "stw2024"
             keyAlias = "platform"
             keyPassword = "stw2024"
            // 临时使用debug证书，实际部署时请使用系统证书
//            storeFile = file("debug.keystore")
//            storePassword = "android"
//            keyAlias = "androiddebugkey"
//            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            // 使用系统签名配置
            signingConfig = signingConfigs.getByName("system")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 使用系统签名配置
            signingConfig = signingConfigs.getByName("system")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // QR Code generation (保留，因为AppPaymentActivity需要)
    implementation("com.google.zxing:core:3.5.2")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // stripe
    implementation("com.stripe:stripeterminal:2.23.4")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}