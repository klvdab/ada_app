// 안드로이드 자봉 — 나눔 마당과 함께하기를 앱 화면으로 (2.8.1, 빌드 261006-A3, 이사장님 승인 2026-10-06)
// 전에는 웹 자봉으로 열었음. 아이폰 자봉(길눈 부품 MulnanumView·HamkkeView)과 같은 내용, 같은 나스 창고(mulnanum.php).
package kr.or.ada.app.jabong

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.InputType
import kr.or.ada.app.gilnun.Girok
import kr.or.ada.app.gilnun.MalGeup
import kr.or.ada.app.gilnun.NnHamkkeHwamyeon
import kr.or.ada.app.gilnun.NnMulGeul
import kr.or.ada.app.gilnun.NnMulnanum
import kr.or.ada.app.gilnun.Sori
import java.util.Calendar

/** 나눔 마당 — 드립니다, 찾습니다, 글 올리기 */
class JbMulnanumHwamyeon : JbHwamyeon("나눔 마당") {
    private var ara = false
    override fun chaeugi(t: JabongActivity) {
        t.geul("쓰지 않는 물건을 주고받는 곳입니다.", true)
        t.danchu("드립니다 — 나눠 주실 물건 보기") { t.yeolgi(JbMulMokHwamyeon("deurim")) }
        t.danchu("찾습니다 — 필요하신 분들 보기") { t.yeolgi(JbMulMokHwamyeon("chatgi")) }
        t.danchu("글 올리기") { t.yeolgi(JbMulSseugiHwamyeon()) }
        t.pyeolchigi("알아 두실 것", ara) { ara = !ara }
        if (ara) for (j in ARA) t.geul(j)
    }

    companion object {
        val ARA = listOf(
            "시각장애인과 자원봉사자가 함께 쓰는 곳입니다. 누구나 드리실 수 있고 누구나 찾으실 수 있습니다.",
            "협회는 물건을 갖지 않습니다. 드리는 분과 찾는 분을 잇기만 합니다. 주고받는 일은 두 분이 직접 하십니다.",
            "연락 받으실 방법은 이 마당에 그대로 드러납니다. 남에게 알려도 괜찮은 번호만 적어 주십시오.",
            "글은 여든 날이 지나면 저절로 내려갑니다. 다 나누셨으면 글 아래 다 나눴습니다를 눌러 주십시오.",
            "물건값을 주고받는 곳이 아닙니다. 파실 물건은 올리지 말아 주십시오."
        )
    }
}

/** 드립니다·찾습니다 목록 — 다섯 개씩 */
class JbMulMokHwamyeon(private val jong: String) : JbHwamyeon(if (jong == "deurim") "드립니다" else "찾습니다") {
    private var mok: List<NnMulGeul>? = null
    private var mot = false
    private var bureuneun = false
    private var sijak = 0
    private val ireum = if (jong == "deurim") "드립니다" else "찾습니다"

    companion object { var dasiBulreo = false }

    override fun chaeugi(t: JabongActivity) {
        if (dasiBulreo) { dasiBulreo = false; mok = null; sijak = 0 }
        val l = mok
        if (l == null) {
            if (mot) t.danchu("불러오지 못했습니다 — 다시 불러오기") { mot = false; bulreogi(t) }
            else { t.geul("${ireum}을 불러오고 있습니다.", true); if (!bureuneun) bulreogi(t) }
            return
        }
        if (l.isEmpty()) { t.geul("${ireum}에 아직 올라온 글이 없습니다. 첫 글을 올려 주시면 다음 분께서 바로 보십니다.", true); return }
        val kkeut = minOf(sijak + 5, l.size)
        for (g in l.subList(sijak, kkeut)) t.danchu(g.julMal) { t.yeolgi(JbMulHanGeonHwamyeon(g, ireum)) }
        if (kkeut < l.size) t.danchu("더 보기") { sijak = kkeut; t.dasiGeurigi() }
        if (sijak > 0) t.danchu("이전 보기") { sijak = maxOf(0, sijak - 5); t.dasiGeurigi() }
    }

    private fun bulreogi(t: JabongActivity) {
        bureuneun = true
        NnMulnanum.mok(jong) { r ->
            t.runOnUiThread {
                bureuneun = false
                if (r == null) { mot = true; Sori.mal("불러오지 못했습니다. 다시 해 보십시오.") }
                else { mok = r; if (r.isNotEmpty()) Sori.mal("$ireum ${r.size}건입니다.", MalGeup.JEONGBO) }
                t.dasiGeurigi()
            }
        }
    }
}

/** 나눔 마당 한 건 — 자세히, 전화 걸기(연락처 듣기), 다 나눴습니다 */
class JbMulHanGeonHwamyeon(private val g: NnMulGeul, ireum: String) : JbHwamyeon(ireum) {
    override fun chaeugi(t: JabongActivity) {
        t.kadeu(g.jaseMal)
        val b = g.jeonhwa
        if (b != null) t.danchu("전화 걸기 — ${g.yeon}") {
            try { t.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$b"))) } catch (_: Exception) { Sori.mal("연락처는 ${g.yeon}입니다.") }
        } else t.danchu("연락처 듣기") { Sori.mal("연락처는 ${g.yeon}입니다.") }
        t.danchu("다 나눴습니다 — 이 글 내리기") {
            t.hwagin("이 글을 내릴까요?", "내리기") {
                NnMulnanum.naerigi(g.id) { ok ->
                    t.runOnUiThread {
                        if (ok) { Baksu.chigi(); Sori.mal("글을 내렸습니다. 나눠 주셔서 고맙습니다!"); JbMulMokHwamyeon.dasiBulreo = true; t.dwiro() }
                        else Sori.mal("내리지 못했습니다. 통신을 확인해 주십시오.")
                    }
                }
            }
        }
    }
}

/** 나눔 마당 글 올리기 */
class JbMulSseugiHwamyeon : JbHwamyeon("글 올리기") {
    private var jong = ""
    private var mul = ""
    private var mal = ""
    private var got = ""
    private var nugu = "자봉 ${JabongNae.beonho}"
    private var yeon = ""
    private var olineun = false

    override fun chaeugi(t: JabongActivity) {
        t.danchu(if (jong == "deurim") "드립니다 — 골랐음" else "드립니다") { goreugi(t, "deurim") }
        t.danchu(if (jong == "chatgi") "찾습니다 — 골랐음" else "찾습니다") { goreugi(t, "chatgi") }
        t.ipryeok("무엇입니까", mul, InputType.TYPE_CLASS_TEXT) { mul = it }
        t.ipryeok("한마디", mal, InputType.TYPE_CLASS_TEXT) { mal = it }
        t.ipryeok("어느 지역", got, InputType.TYPE_CLASS_TEXT) { got = it }
        t.ipryeok("이름 또는 별명", nugu, InputType.TYPE_CLASS_TEXT) { nugu = it }
        t.ipryeok("연락 받으실 방법", yeon, InputType.TYPE_CLASS_TEXT) { yeon = it }
        t.danchu(if (olineun) "올리는 중입니다" else "올리기") { olligi(t) }
        t.danchu("그만두기") { Sori.mal("그만두었습니다."); t.dwiro() }
    }

    private fun goreugi(t: JabongActivity, j: String) {
        jong = j
        Sori.mal(if (j == "deurim") "드립니다로 정했습니다. 무엇인지 적어 주십시오." else "찾습니다로 정했습니다. 무엇인지 적어 주십시오.")
        t.dasiGeurigi()
    }

    private fun olligi(t: JabongActivity) {
        if (olineun) return
        if (jong.isEmpty()) { Sori.mal("드립니다인지 찾습니다인지 골라 주십시오."); return }
        val m = mul.trim(); val y = yeon.trim()
        if (m.isEmpty()) { Sori.mal("무엇인지 적어 주십시오."); return }
        if (y.isEmpty()) { Sori.mal("연락 받으실 방법을 적어 주십시오."); return }
        olineun = true
        t.dasiGeurigi()
        NnMulnanum.olligi(jong, m, mal.trim(), got.trim(), nugu.trim(), y) { e ->
            t.runOnUiThread {
                olineun = false
                if (e != null) { Sori.mal(e); t.dasiGeurigi() }
                else { Baksu.chigi(); Sori.mal("올렸습니다. 고맙습니다."); JbMulMokHwamyeon.dasiBulreo = true; t.dwiro() }
            }
        }
    }
}

/** 함께하기 — 자원봉사 교육과 제도 안내(길눈과 같은 글, 마당 제목을 두드리면 펼쳐짐) */
class JbHamkkeHwamyeon : JbHwamyeon("함께하기") {
    private val yeollin = HashSet<Int>()

    override fun chaeugi(t: JabongActivity) {
        t.geul("자원봉사 교육과 제도 안내입니다. 마당 제목을 두드리시면 펼쳐집니다.", true)
        NnHamkkeHwamyeon.MADANG.forEachIndexed { i, (jemok, juldeul) ->
            val on = i in yeollin
            t.danchu(if (on) "$jemok 접기" else "$jemok 펼치기") {
                if (on) yeollin.remove(i) else yeollin.add(i)
                t.dasiGeurigi()
            }
            if (on) for (j in juldeul) {
                if (j.startsWith("#")) t.keunGeul(j.substring(1), 20f) else t.geul(j, true)
            }
        }
        val d = t.getSharedPreferences("jabong", Context.MODE_PRIVATE)
        val nal = d.getString("jb.hamkkeDone", "") ?: ""
        if (nal.isNotEmpty()) t.geul("${nal}에 다 읽으신 것으로 남아 있습니다.")
        t.danchu("이 안내를 다 읽었습니다") {
            val c = Calendar.getInstance()
            val n = "${c.get(Calendar.YEAR)}년 ${c.get(Calendar.MONTH) + 1}월 ${c.get(Calendar.DAY_OF_MONTH)}일"
            d.edit().putString("jb.hamkkeDone", n).apply()
            Girok.namgi("hamkke_done")
            Baksu.chigi()
            Sori.mal("다 읽으신 것으로 남겼습니다. 고맙습니다!")
            t.dasiGeurigi()
        }
    }
}
