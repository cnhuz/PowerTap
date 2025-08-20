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
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // RenderScript配置
        renderscriptTargetApi = 21
        renderscriptSupportModeEnabled = true
    }

    signingConfigs {
        create("rk3288") {
            storeFile = file("RK3288.keystore")
            storePassword = "stw2024"
            keyAlias = "platform"
            keyPassword = "stw2024"
        }
        create("rk3288new") {
            storeFile = file("RK3288-NEW.keystore")
            storePassword = "stw2024"
            keyAlias = "platform"
            keyPassword = "stw2024"
        }
        create("sc20") {
            storeFile = file("SC20.keystore")
            storePassword = "stw2024"
            keyAlias = "platform"
            keyPassword = "stw2024"
        }
        create("sc20new") {
            storeFile = file("SC20-NEW.keystore")
            storePassword = "stw2024"
            keyAlias = "platform"
            keyPassword = "stw2024"
        }
    }

    flavorDimensions += "device"

    productFlavors {
        create("rk3288") {
            dimension = "device"
            versionNameSuffix = "-RK3288"
            signingConfig = signingConfigs.getByName("rk3288")
        }
        create("rk3288new") {
            dimension = "device"
            versionNameSuffix = "-RK3288NEW"
            signingConfig = signingConfigs.getByName("rk3288new")
        }
        create("sc20") {
            dimension = "device"
            versionNameSuffix = "-SC20"
            signingConfig = signingConfigs.getByName("sc20")
        }
        create("sc20new") {
            dimension = "device"
            versionNameSuffix = "-SC20NEW"
            signingConfig = signingConfigs.getByName("sc20new")
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            isDebuggable = true
        }
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // 确保每个variant使用正确的签名配置，并将APK输出到指定目录
    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            // 使用variant.versionName，它已经包含了flavor和buildType的后缀
            val fileName = "PowerTap-${variant.versionName}.apk"
            output.outputFileName = fileName
            
            // 将APK复制到项目根目录的apk文件夹中
            variant.assembleProvider.get().doLast {
                copy {
                    from(output.outputFile)
                    into("${rootDir}/apk")
                    rename { fileName }
                }
            }
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

    //日志
    implementation("org.slf4j:slf4j-log4j12:1.7.24")
    implementation("de.mindpipe.android:android-logging-log4j:1.0.3")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}