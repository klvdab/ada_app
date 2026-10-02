// 안드로이드 길눈 — 카메라 눈 관리자 시험(묶음 b3_kamera)
// 아이폰은 관리자 시험 중인 기능(발 앞 계단·턱 알림)을 "나스 음악 열쇠가 있는 폰"(Yeolsoe eumakTk)에서만 보여 줍니다(TeokAllim.boim).
// 안드로이드도 같은 열쇠로 가림 — 폰에 담은 eumakTk(설정 꾸러미 "gilnun")가 있으면 관리자.
// 음악·방송 묶음이 같은 자리에 열쇠를 담으면 그대로 이어지고, 아직 없으면 이 화면에서 열쇠를 넣음(아이폰 yeolsoeNeoki 와 같은 나스 약속, 정해진 열쇠로 들어가기만).
package kr.or.ada.app.gilnun

import android.content.Context
import android.text.InputType

object KameraGwanli {
    /** 관리자 시험 중인 카메라 눈이 보이는 폰인가 */
    fun boim(c: Context): Boolean {
        return Yeolsoe.eumakTk(c).isNotEmpty()   // 2.7.0 통합 — 나스 음악 열쇠는 한 도우미로
    }

    /** 열쇠 넣기 — 되면 null, 안 되면 까닭(화면 줄에서) */
    fun yeolsoeNeoki(c: Context, pw: String, kkeut: (String?) -> Unit) {
        val app = c.applicationContext
        Tongsin.json("/jeom/eumak.php", mapOf("a" to "sangtae")) { s ->
            if (s == null) { kkeut("자료 창고에 닿지 못했습니다. 통신을 확인해 주십시오."); return@json }
            if (!s.optBoolean("jeonghaessna", false)) { kkeut("나스에 열쇠가 아직 정해지지 않았습니다."); return@json }
            Tongsin.json("/jeom/eumak.php", mapOf("a" to "deulgi", "pw" to pw)) { k ->
                val tk = k?.optString("tk", "") ?: ""
                if (k == null || !k.optBoolean("ok", false) || tk.isEmpty()) {
                    val e = k?.optString("error", "") ?: ""
                    kkeut(if (e.isNotEmpty()) e else "들어가지 못했습니다. 열쇠를 다시 넣어 주십시오.")
                    return@json
                }
                Yeolsoe.eumakTkNoki(app, tk)   // 2.7.0 통합 — 음악·방송과 같은 열쇠 자리
                Girok.namgi("kamera_gwanli", mapOf("ok" to true))
                kkeut(null)
            }
        }
    }
}

/** 관리자 시험 — 카메라 눈(설정 탭 더 보기) */
class KameraGwanliHwamyeon : Hwamyeon("관리자 시험 — 카메라 눈") {
    override fun chaeugi(t: GilnunActivity) {
        if (KameraGwanli.boim(t)) {
            t.danchu("문 찾기 — 관리자 시험 중, 문 둘레 글자로 문 찾기") { t.yeolgi(MunChatgiHwamyeon()) }
            t.danchu("발 앞 계단·턱 알림 — 관리자 시험 중, 구글 AR 깊이") { t.yeolgi(TeokAllimHwamyeon()) }
            t.geul("오래 시험한 뒤 대표님 승인으로 모든 분께 엽니다.")
            return
        }
        val e = t.ipryeok("관리자 열쇠 — 나스 음악 열쇠", false)
        e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        t.danchu("들어가기") {
            val pw = e.text.toString().trim()
            if (pw.isEmpty()) { Sori.mal("관리자 열쇠를 넣어 주십시오."); return@danchu }
            Sori.mal("확인하는 중입니다.", MalGeup.JEONGBO)
            KameraGwanli.yeolsoeNeoki(t, pw) { oryu ->
                if (oryu != null) { Sori.mal(oryu); return@yeolsoeNeoki }
                Sori.mal("관리자 시험 기능을 엽니다.")
                if (t.wiHwamyeon === this) t.dasiGeurigi()
            }
        }
    }
}
