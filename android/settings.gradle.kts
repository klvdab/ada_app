// 협회 안드로이드 앱 — 설정 (1.1.0판, 빌드 261002-A8 — 갤럭시 워치 길눈 :wear 모듈 더함, 대표님 지시)
// 1.0.0판(260923-1) 처음
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "AdaApp"
include(":app")
include(":wear")   // 261002-A8 갤럭시 워치 길눈(웨어 OS) — 앱 번호는 폰 길눈과 같은 kr.or.ada.app
