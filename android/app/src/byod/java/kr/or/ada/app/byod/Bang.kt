// BYOD 방송 — 방송 상태 한곳 (1.0.0판, 빌드 261003-B1, 이사장님 승인 2026-10-03)
// 화면(ByodActivity)과 방송 일꾼(ByodService)이 함께 보는 상태입니다.
package kr.or.ada.app.byod

import java.util.concurrent.CopyOnWriteArrayList

object Bang {
    const val PAN = "1.3.1"   // 1.3.1(261015-B11) 이름을 LVD 송출로 바꿈(듣는 앱 LVD 방송과 나눔), 현장영상해설사에게 전화가 오면 듣는 분께 「잠시 기다려 주십시오」를 들려 드리고 통화가 끝나면 저절로 이어 보냄(방송클, 이사장님 승인 「1」). 1.3.0(261014-B10) 행사 이름·현장영상해설사 칸, 공유기가 녹음(1분 조각)과 행사 기록을 남기고 방송을 멈추면 앱이 받아 배프 다시듣기에 올림, 지난 행사 기록(방송클, 이사장님 승인 「1」). 1.2.0(261014-B9) 플린트 2 방식 — 방송 서버를 공유기 안에, 이 앱은 해설 소리만 보냄·플린트 2 준비하기·와이파이 태그 한 장·와이파이 이름 기억(방송클, 이사장님 승인 「1」). 1.1.6(261012-B8) 스티커를 엔에프시 태그로 부름·듣기 주소 바로잡기와 확인·엔에프시 태그 쓴 뒤 다시 읽어 확인·A4 인쇄용 한 장·듣기 화면 단추에 커서(방송클, 이사장님 승인). 1.1.5(261009-B7) 현장 점검 단추·멈춘 폰 10초 안에 빼고 세기·엔에프시 태그 주소 기억과 견주기·소리 크기 재기·듣는 화면 안드로이드 안내(방송클, 이사장님 승인). 1.1.4(261007-B6) 실제로 받는 소리 장치 알림·사운드카드 안 잡힐 때 할 일·어떤 장치든 꽂고 빼면 다시 고름·기기 점검 판단과 결과 복사(방송클). 1.1.3(261007-B5) 기기 점검. 1.1.2(261007-B4) 새 판 알림과 업데이트(대장클, 이사장님 지시)
    const val BILD = "261015-B11"
    const val PORT = 8080

    @Volatile var kyeojim = false          // 방송 일꾼이 돌고 있는가
    @Volatile var keu = false              // 소리를 내보내는 중인가(노트북판의 큐)
    @Volatile var sojae = "아직 정하지 않음"   // 소리 받는 곳 이름
    @Volatile var juso = ""                // 듣기 주소(http://…:8080/)
    @Volatile var jalmot = ""              // 잘못된 일(없으면 빈 글)
    @Volatile var tonghwaJung = false      // 1.3.1 현장영상해설사가 통화 중인가(듣는 분께 기다림 안내를 보냄)
    @Volatile var deutnunSu = 0            // 지금 듣는 분 수
    @Volatile var sigakTtae = 0L           // 방송 시작 시각
    @Volatile var sorikeugi = -99          // 1.1.5 들어오는 소리 크기(최근 3초 가장 큰 값, dB, 0이 가장 큼)
    @Volatile var sorikeugiTtae = 0L       // 1.1.5 소리 크기를 잰 때

    data class Yocheong(val ttae: Long, val beonho: String, val jongryu: String)
    val yocheong = CopyOnWriteArrayList<Yocheong>()   // 듣는 분의 도움 요청(최근 것 뒤)
    @Volatile var yocheongSeq = 0

    // 화면이 바뀐 것을 알도록 부르는 곳
    val gwanchal = CopyOnWriteArrayList<() -> Unit>()
    fun allyeo() { for (f in gwanchal) try { f() } catch (_: Exception) { } }

    fun jongryuMal(k: String): String = when (k) {
        "dowum" -> "도움 요청"
        "gungeum" -> "궁금함"
        "kkeunkim" -> "소리가 끊김"
        else -> k
    }
}
