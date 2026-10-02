// 갤럭시 워치 길눈 — 워치 앱 설정 (2.6.0판, 빌드 261002-A9 — 긴급통화 단추, 대표님 지시. 처음 2.5.0판 261002-A8: 아이폰 길눈의 애플워치 앱을 갤럭시 워치에도)
// 웨어 OS 앱. 폰 길눈(app 모듈 gilnun 갈래)과 웨어러블 데이터 층으로 이어지려면 앱 번호(kr.or.ada.app)와 서명이 같아야 합니다.
// 서명은 app 모듈과 같은 열쇠집(뿌리의 keystore.properties)을 씁니다. 없으면 서명 없이 시험용으로만.
// 기대는 것은 셋뿐: 웨어러블 데이터 층, 워치 화면 위 "진행 중" 표시(wear-ongoing), androidx core
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
    namespace = "kr.or.ada.app.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "kr.or.ada.app"   // 폰 길눈과 같아야 이어짐
        minSdk = 30
        targetSdk = 35
        versionCode = (System.getenv("BUILD_NUMBER") ?: "1").toInt()
        versionName = "2.6.0"   // 261002-A9 긴급통화 단추(두 번 눌러 확인, 대표님 지시)
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

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keyFile.exists()) {
                signingConfig = signingConfigs.getByName("olligi")
            }
        }
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
    implementation("androidx.wear:wear-ongoing:1.0.0")
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
}
