// 드라이빙 모드 — 2.97.0 (261121-A85, 대클, 이사장님 승인 2026-11-21 「1,2,3」, 아이폰 Deuraibing.swift 2.132.0과 같음)
// 차를 모는 분을 위한 내비게이션이 아니라, 타고 가시는 분이 갈 길을 미리 알고 확인하시도록.
//   ① 길 미리 듣기 ② 다른 길 견주기 — 길은 협회 서버 gilmiri.php(카카오 길찾기). 운전을 시키는 말은 하지 않음.
package kr.or.ada.app.gilnun

import java.util.Locale

object Deuraibing {
    private fun f6(v: Double) = String.format(Locale.US, "%.6f", v)

    /** 길을 받아 할 말을 만듦 — gyeon 이면 다른 길 견주기만 */
    fun mal(mokLat: Double, mokLon: Double, mok: String, gyeon: Boolean, kkeut: (String) -> Unit) {
        val w = Wichi.jigeum
        if (w == null) { kkeut("지금 자리를 알 수 없어 길을 미리 받지 못했습니다."); return }
        Tongsin.json("gilmiri.php", mapOf("la1" to f6(w.lat), "lo1" to f6(w.lon), "la2" to f6(mokLat), "lo2" to f6(mokLon)), 15000) { o ->
            if (o == null || !o.optBoolean("ok")) {
                Girok.namgi("deuraibing_mot")
                kkeut("지금은 ${mok}까지 가는 차 길을 받지 못했습니다. 조금 뒤에 다시 말씀해 주십시오.")
                return@json
            }
            val m = o.optString("mal"); val g = o.optString("gyeon")
            val t = if (gyeon) (if (g.isEmpty()) "${mok}까지는 다른 길도 지금 길과 거의 같습니다. $m" else g) else "${mok}까지 $m" + (if (g.isEmpty()) "" else " $g")
            Girok.namgi("deuraibing_mal", mapOf("mal" to t))
            kkeut(t)
        }
    }

    /** 「길 미리 들려 줘」, 「가는 길 미리 알려 줘」, 「어느 길로 가」 */
    fun miriMal(z: String): Boolean =
        (z.contains("길") && z.contains("미리") && !z.contains("걸어")) || z.contains("어느길로가") || z.contains("어떤길로가") || z.contains("무슨길로가")
    /** 「다른 길 알려 줘」, 「길 견줘 줘」, 「빠른 길 알려 줘」 */
    fun gyeonMal(z: String): Boolean =
        (z.contains("다른길") && (z.contains("알려") || z.contains("있어") || z.contains("견줘") || z.contains("비교"))) || z.contains("길견줘") || z.contains("길비교") ||
            z.contains("빠른길알려") || z.contains("짧은길알려") || z.contains("돌아가는길")
}
