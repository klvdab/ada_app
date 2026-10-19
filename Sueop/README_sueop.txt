LVD 수업 아이폰 (kr.or.ada.sueop) — 0.1.0판, 빌드 261020-SI1 (방클, 이사장님 승인 2026-10-20 「단독으로 가자」)

무엇인가
  강사님이 노트북의 「수업 이끌기」(https://lvd.ada.or.kr/sueop/ikkeulgi.html)에서 넘기는 자료를
  교육생 폰에서 그대로 보는 앱. 웹 「수업 보기」(sueop/index.html 0.2판)의 기능을 모두 담음.
서버
  https://lvd.ada.or.kr/sueop/sueop.php?a=bogi&bang=네자리 를 1.5초마다 살핌. 그림은 sueop/bang/네자리/p장.jpg, 프로그램 화면은 hw.jpg
파일
  SueopApp.swift   시작·첫 화면·수업 화면·크게 보기·도움말 화면
  Bang.swift       수업 방 살피기(바뀐 것만 알림·그림 받기)
  Doumal.swift     도움말(안드로이드 src/sueop Doumal.kt 와 같은 글)
  Girok.swift      이용 기록(jeom/applog.php)
  SueopOllim.swift 새 판 알림(sihum/pan.json 의 sueop.ios)
  HyeophoeLogo.swift 협회 로고
짓기
  app-jitgi 를 eoneu=sueop 로. 앱스토어 커넥트에 kr.or.ada.sueop 앱 자리가 먼저 있어야 올라감.
