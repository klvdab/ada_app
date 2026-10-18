// 안드로이드 자봉 — 동영상 강의 차례 (2.27.0, 빌드 261018-A23, 이사장님 승인 2026-10-18 「3」, 아이폰 자봉 2.26.0 과 같음)
// 강의 차례와 조회수는 길눈과 함께 쓰는 부품(kr.or.ada.app.gilnun.Gangjwa)이 맡음 — 자봉이면 강좌 번호 jabong_a.
// 틀기는 길눈과 같은 재생기(DongyeongActivity, 자봉 앱에는 받기 문 없이 실음): 편 이름, 다음 편, 이름 없는 조회수
package kr.or.ada.app.jabong

import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import kr.or.ada.app.gilnun.Gangjwa

/** 동영상 강의 차례 — 다섯 편씩 */
class JbGangjwaHwamyeon : JbHwamyeon("동영상 강의") {
    private var sijak = 0
    private var cheotJulRo = false
    private var bureum = false

    override fun chaeugi(t: JabongActivity) {
        Gangjwa.damunGeot(t)
        if (!bureum) {
            bureum = true
            Gangjwa.batgi(t) { if (t.wiHwamyeon === this) t.dasiGeurigi() }
        }
        t.geul("자봉 사용법 동영상 강의입니다. 편 이름을 누르시면 앱 안에서 바로 틀어 드립니다.")
        val l = Gangjwa.pyeon
        if (l.isEmpty()) {
            t.geul(if (Gangjwa.badeum) "강의 차례를 받지 못했습니다. 인터넷이 이어진 뒤 이 화면을 다시 열어 주십시오." else "강의 차례를 받는 중입니다.")
            return
        }
        if (sijak >= l.size) sijak = 0
        val kkeut = minOf(sijak + 5, l.size)
        var cheot: View? = null
        for (n in sijak until kkeut) {
            val p = l[n]
            val b = t.danchu(p.title) { Gangjwa.teulgi(t, p) }
            if (cheot == null) cheot = b
        }
        if (kkeut < l.size) t.danchu("더 보기") { sijak += 5; cheotJulRo = true; t.dasiGeurigi() }
        if (sijak > 0) t.danchu("이전 보기") { sijak = maxOf(0, sijak - 5); cheotJulRo = true; t.dasiGeurigi() }
        val c = cheot
        if (cheotJulRo && c != null) {
            cheotJulRo = false
            c.post { c.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null) }
        }
    }
}
