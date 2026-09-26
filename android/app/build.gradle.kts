// 협회 안드로이드 앱 — 앱 설정 (1.0.0판, 빌드 260923-1)
// 한 프로젝트에 세 앱을 담습니다(갈래=flavor).
//   gilnun  길눈 (kr.or.ada.app)     대문 https://lvd.ada.or.kr/app/
//   jabong  자봉 (kr.or.ada.jabong)  대문 https://lvd.ada.or.kr/jabong/
//   bfb     배프 (kr.or.ada.bfb)     대문 https://lvd.ada.or.kr/bfb/
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keyProps = Properties()
val keyFile = rootProject.file("keystore.properties")
if (keyFile.exists()) {
    keyProps.load(FileInputStream(keyFile))
}

android {
    namespace = "kr.or.ada.app"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        targetSdk = 35
        versionCode = (System.getenv("BUILD_NUMBER") ?: "1").toInt()
        versionName = "1.1.0"
    }

    signingConfigs {
        create("olligi") {
            if (keyFile.exists()) {
                storeFile = rootProject.file(keyProps.getProperty("storeFile"))
                storePassword = keyProps.getProperty("storePassword")
                keyAlias = keyProps.getProperty("keyAlias")
                keyPassword = keyProps.getProperty("keyPassword")
            }
        }
    }

    flavorDimensions += "ap"
    productFlavors {
        create("gilnun") {
            dimension = "ap"
            applicationId = "kr.or.ada.app"
            resValue("string", "app_name", "길눈")
            buildConfigField("String", "ADA_HOME", "\"https://lvd.ada.or.kr/jeom/jeom.html\"")   // 260926-3 길눈 첫 화면으로 곧바로
        }
        create("jabong") {
            dimension = "ap"
            applicationId = "kr.or.ada.jabong"
            resValue("string", "app_name", "자봉")
            buildConfigField("String", "ADA_HOME", "\"https://lvd.ada.or.kr/jabong/\"")
        }
        create("bfb") {
            dimension = "ap"
            applicationId = "kr.or.ada.bfb"
            resValue("string", "app_name", "배프")
            buildConfigField("String", "ADA_HOME", "\"https://lvd.ada.or.kr/bfb/\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keyFile.exists()) {
                signingConfig = signingConfigs.getByName("olligi")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.webkit:webkit:1.12.1")   // 260926-1 다리를 문서 맨 처음에 심기
}
