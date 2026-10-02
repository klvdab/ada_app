// 협회 안드로이드 앱 — 앱 설정 (1.6.0판, 빌드 261002-B1 — 길눈 2.7.0 아이폰 길눈 나머지 기능 통합, 대표님 지시 「안드로이드에서도 이 원칙 지켜서 동일하게」)
// 1.6.0판(261002-B1) 길눈 2.7.0 — 카메라 눈(CameraX·ML Kit·ARCore·미디어파이프), 음악·방송(미디어3), 긴급 공지 뒤 일꾼(WorkManager),
//   현장영상해설 받기 앱 안 웹 화면(맞춤 탭), 마실 자바스크립트 칸(javascriptengine). 자봉 갈래도 src/gilnun 을 싣으므로 세 갈래 모두에 넣음
// 1.5.0판(261002-A9) 화상통화(WebRTC) 부품 stream-webrtc-android(org.webrtc) 를 더함 — 길눈 긴급통화서비스. 옛 org.webrtc:google-webrtc 는 끊겨 쓰지 않음
//   자봉 갈래도 src/gilnun 을 함께 싣으므로 세 갈래 모두에 넣음(자봉·배프는 쓰지 않음)
// 1.4.0판(261002-A8) 구글 웨어러블 데이터 층(play-services-wearable)을 더함 — 폰 길눈 ↔ 갤럭시 워치 길눈(:wear 모듈, 같은 앱 번호 kr.or.ada.app)
//   자봉 갈래도 src/gilnun 을 함께 싣으므로 세 갈래 모두에 넣음(자봉·배프는 쓰지 않음)
// 1.3.0판(261001-A3) 길눈 2.1.0 몸 센서를 길눈에도
// 1.2.0판(261001-A2) 자봉 갈래 점지도 그리기 속까지 앱, 몸 센서 극대화
// 1.1.0판(261001-A1) 길눈 갈래 속까지 앱
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
        versionName = "1.2.1"
        // 261002-A10 화상통화 부품(WebRTC)이 폰 종류별 부품을 넷 다 실어 APK 가 47메가바이트가 되던 것을 줄임 —
        //   요즘 폰(64비트)과 옛 폰(32비트) 두 가지만 실음. 플레이 스토어(AAB)는 폰마다 맞는 것만 따로 내려 보냄
        ndk { abiFilters += listOf("arm64-v8a") }   // 261002-B1 64비트 폰만(2016년 뒤 거의 모든 폰) — 카메라 눈 부품이 커서 APK 크기를 줄임
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
            versionName = "2.7.0"   // 261002-B1 아이폰 길눈의 나머지 기능을 한꺼번에(묶음 b1~b6 — 목적지·여정·안내·지하철·버스, 점지도 마저, 카메라 눈, 둘러보기, 음악·방송·동영상, 나눔·알림·설정, 대표님 지시 「안드로이드에서도 이 원칙 지켜서 동일하게」). 261002-A9 긴급통화서비스를 안드로이드 길눈에도(GinGeup·GinGeupHwamyeon — 가족·지인·자원봉사자·현장영상해설사 화상통화, 나스 rel.php·턴 서버, 대표님 지시). 261002-A8 갤럭시 워치 길눈과 잇기(WatchLink — 안내·진동·걸음·손목 가리키기·지팡이 떨림 기록, 대표님 지시). 261002-A7 말로 하기를 안드로이드 길눈에도(MalHagi — 받아쓰기·나스 알아듣기 사전·점지도 찾기, 이어폰 단추 길게, 대표님 지시). 261002-A6 음향신호기를 안드로이드 길눈에도(SinhogiEngine — 자동으로 잡기·손으로 울리기·찾기, 대표님 지시). 261002-A4 점지도 따라 걷기를 안드로이드에도(JeomEngine·Jeomjido·Eum·JeomHwamyeon, 대표님 지시). 261001-A3 몸 센서를 길눈에도(MomSensor.kt 를 src/gilnun 으로). 261001-A1 길눈은 속까지 앱(GilnunActivity, src/gilnun)으로 새로 지음 — 자봉·배프는 웹 판 그대로
            buildConfigField("String", "ADA_HOME", "\"https://lvd.ada.or.kr/jeom/jeom.html\"")   // 260926-3 길눈 첫 화면으로 곧바로
        }
        create("jabong") {
            dimension = "ap"
            applicationId = "kr.or.ada.jabong"
            resValue("string", "app_name", "자봉")
            versionName = "2.3.0"   // 261002-J1 아이폰 자봉 2.3.0과 똑같이 — 처음 등록·탭 넷·긴급통화 받기(JabongDaegi·JabongTonghwa, 알림 칸의 자봉이 나스 부름을 살핌)·점지도 그리기 마저(주소·말로 표시·그린 길 목록), 대표님 지시. 261001-A2 점지도 그리기를 속까지 앱(JabongActivity, src/jabong) — 몸 센서 극대화
            buildConfigField("String", "ADA_HOME", "\"https://lvd.ada.or.kr/jabong/\"")
        }
        create("bfb") {
            dimension = "ap"
            applicationId = "kr.or.ada.bfb"
            resValue("string", "app_name", "배프")
            buildConfigField("String", "ADA_HOME", "\"https://lvd.ada.or.kr/bfb/\"")
        }
    }

    // 261001-A2 자봉 갈래도 길눈 부품(말소리·위치·설정·기록·나스 통신)을 함께 씀 — 아이폰에서 Gilnun 폴더를 함께 싣는 것과 같음
    sourceSets {
        getByName("jabong") {
            java.srcDirs("src/jabong/java", "src/gilnun/java")
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

    // 261002-B1 APK 속 기계어 부품(.so)을 눌러 담아 받는 파일 크기를 줄임(깔 때 풀림)
    packaging {
        jniLibs { useLegacyPackaging = true }
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
    implementation("com.google.android.gms:play-services-wearable:18.1.0")   // 261002-A8 갤럭시 워치 길눈과 잇기
    implementation("io.getstream:stream-webrtc-android:1.3.8")   // 261002-A9 길눈 긴급통화서비스 화상통화(org.webrtc)
    implementation("androidx.activity:activity-ktx:1.9.3")       // 261002-B1 내 서류 보관함 사진 고르기(PickVisualMedia) — appcompat 이 끌어오는 것보다 분명히

    // 261002-B1 카메라 눈(QR 찾기·사람 감지·즉석 글자 읽기·빛 알아보기·가리키고 말하기·상품 바코드·지폐와 색깔·한마디 설명·안면인식·사진 읽어 주기,
    //   관리자 시험 문 찾기·발 앞 턱) — 폰 안에서만, 모델은 앱 안에(가리키고 말하기 손 마디 모델만 처음 한 번 받음)
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("androidx.annotation:annotation-experimental:1.4.1")        // Camera2Interop·미디어3 OptIn 표시
    implementation("com.google.android.gms:play-services-mlkit-text-recognition-korean:16.0.1")   // 모델은 구글 플레이 서비스가 받아 둠(APK 작게)          // 즉석 글자 읽기·간판·문 글자·지폐(한글+영어)
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")                 // QR 찾기·상품 바코드
    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")             // 사람 감지
    implementation("com.google.android.gms:play-services-mlkit-face-detection:17.1.0")                   // 안면인식, 한마디 설명의 사람 수
    implementation("com.google.android.gms:play-services-mlkit-image-labeling:16.0.8")                   // 한마디 설명
    implementation("com.google.mediapipe:tasks-vision:0.10.14")                // 가리키고 말하기 — 손 마디(검지 끝)
    implementation("com.google.ar:core:1.44.0")                                // 발 앞 계단·턱 알림(관리자 시험) — AR 없어도 앱은 깔림(매니페스트 optional)

    // 261002-B1 음악·방송(길 위의 음악·라디오·TV·지금 세상 이야기)과 동영상 틀기 — 미디어3 1.4.1(compileSdk 34 이상)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")   // 라디오·TV 생방송(HLS m3u8)
    implementation("androidx.media3:media3-session:1.4.1")         // 알림 칸·잠금 화면·이어폰 단추(미디어 세션 서비스)
    implementation("androidx.media3:media3-ui:1.4.1")              // TV 화면·동영상 화면(PlayerView)

    implementation("androidx.javascriptengine:javascriptengine:1.0.0")   // 261002-B1 마실 — 나스 masil_*.js 를 웹뷰 없이(안드로이드 8 이상, 아래는 웹뷰)
    implementation("androidx.work:work-runtime-ktx:2.9.1")               // 261002-B1 길눈 긴급 공지 뒤 일꾼(GongjiWorker)
    implementation("androidx.browser:browser:1.8.0")                     // 261002-B1 현장영상해설 받기 — 앱 안 웹 화면(크롬 맞춤 탭)
}
