협회 앱 (아이폰 + 애플워치) 설계도 — 1.0.0판, 빌드 260923-1

무엇인가
  나스의 웹(길눈·배리어프리방송국·더뷰 신청·마실·시지각)을 앱 안에 그대로 담고,
  웹이 못 하는 손발을 앱이 붙입니다.
    - 위치 계속 받기(폰이 잠겨도)      App/LocationService.swift
    - 알림(잠긴 폰·워치로)              App/NotificationService.swift
    - 진동 무늬(왼쪽·오른쪽·도착)        App/HapticService.swift
    - 기기 단추 받기(워치·이어폰·리모컨) App/RemoteCommandService.swift
    - 폰↔워치 잇기                      App/WatchLink.swift, Watch/WatchModel.swift
    - 웹과 앱 사이 다리                  App/WebBridge.swift, App/app_bridge.js
  워치 앱은 단추 셋(다음 갈림길·내 자리·마지막 안내)과 진동만 둡니다. Watch/WatchView.swift

앱이 여는 첫 주소
  https://lvd.ada.or.kr/app/   (web/index.html 을 나스 /test/app/ 에 둠)
  이 주소 안의 내용은 나스에서 고치면 심사 없이 바로 반영됩니다.

웹이 앱을 부르는 법 (gigi.js 등에서)
  window.adaApp 이 있으면 앱 안입니다.
  window.adaApp.bureugi("wichi", {on:true, always:true})   위치 계속 받기
  window.adaApp.bureugi("allim", {title:"길눈", body:"…", url:"…"})
  window.adaApp.bureugi("jindong", {mu:"left"|"right"|"arrive"|"short"|"long"})
  window.adaApp.bureugi("mal", {t:"마지막 안내 글"})       워치로
  window.adaApp.bureugi("daeum", {t:"다음 갈림길 글"})     워치로
  window.adaApp.bureugi("jaesaeng", {title:"…", on:true})  기기 단추 받기
  window.adaApp.bureugi("malhagi", {t:"…"})                앱 음성
  window.adaApp.bureugi("dwiro")                           뒤로(앱 밖으로 안 나감)
  window.adaApp.deutgi("wichi", function(d){ d.lat, d.lon, d.acc, d.head, d.speed })
  window.adaApp.deutgi("gigi",  function(d){ d.danchu: "play"|"next"|"prev" })
  window.adaApp.deutgi("watch", function(d){ d.what: "mal"|"daeum"|"jari" })
  window.adaApp.deutgi("allimTap", function(d){ d.url })

짓는 법 (맥 없이)
  깃허브 저장소 klvdab/ada_app 에 이 폴더를 올려 두었습니다.
  Actions 화면에서 app-jitgi 를 「Run workflow」로 돌리면 빌린 맥(Xcode 26)에서
  아이콘을 만들고(tools/make_icon.py), XcodeGen 으로 프로젝트를 만들고,
  앱스토어 커넥트 열쇠(관리자 권한)로 서명해 테스트플라이트에 올립니다.
  비밀값 넷: ASC_KEY_ID, ASC_ISSUER_ID, ASC_KEY_P8, TEAM_ID

번들 ID
  아이폰 kr.or.ada.app / 워치 kr.or.ada.app.watchkitapp (2026-09-23 등록함)
  앱스토어 커넥트 앱 번호 6815082495, 관리번호(SKU) KLVDAB-APP-2026

앱 이름
  지금은 "현장영상해설"(아이폰), "길눈"(워치)로 두었고, 이사장님이 정하시면 project.yml 의 CFBundleDisplayName 과
  앱스토어 커넥트의 이름을 바꿉니다.

아이콘
  짙은 파랑 바탕에 흰 글자 LVD, 아래에 노란 줄. 짓기 때마다 tools/make_icon.py 가 1024 크기로 만듭니다.

기록
  260909-1  처음 만듦. 아이폰 앱·워치 앱·자동 빌드·앱 대문·개인정보 처리방침 초안.
            (/test/gilnun_app 의 9/6 초안(Capacitor 방식)은 그대로 두고 이 설계도로 대신함)
  260923-1  1.0.0판. 이사장님 승인(2026-09-23).
            - 짓는 맥을 macos-15 로, Xcode 26 을 골라 짓게 함(애플이 2026년 4월부터 iOS 26 SDK 로 지은 앱만 받음)
            - 앱 아이콘 넣음(tools/make_icon.py, 아이폰·워치)
            - 판번호 1.0.0, 빌드 번호가 실제로 앱에 들어가게 Info.plist 에 이어 줌, SWIFT_VERSION 5.0 으로 바로잡음
            - 짓기는 손수 돌리기로(올릴 때마다 저절로 돌지 않게), 짓기 기록(log)을 결과로 남김
            - 깃허브 저장소 klvdab/ada_app(공개) 만듦, 앱 아이디 둘 등록, 앱스토어 커넥트에 앱 자리 만듦
