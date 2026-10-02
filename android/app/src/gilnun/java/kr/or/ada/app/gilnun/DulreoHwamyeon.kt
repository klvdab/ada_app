// 안드로이드 길눈 — 둘러보기 탭의 속 화면(묶음 b4_dulreo, 아이폰 DulreoView.swift 2.7.0 과 같은 차례, 같은 말)
// 둘레 찾기(밥집 갈래·대피소), 찾은 곳 목록, 가 볼 곳·축제·무장애 여행, 고장 이야기, 어디서 어디로 미리 들어 보기, 마실.
// 목록은 다섯 개씩, 번호 없이, 나오면 커서를 첫 줄로. 이름을 두드리면 그곳까지 안내.
// 아이폰의 "위아래로 쓸어 고르기"(보이스오버 동작)는 톡백 동작 메뉴의 사용자 동작과 길게 누르기로 같은 일을 함.
package kr.or.ada.app.gilnun

import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.widget.doAfterTextChanged

// MARK: 함께 쓰는 조각(이 묶음 안에서만)

/** 다섯씩 보여 주기 — 다섯 줄 아래에 더 보기, 그 아래에 이전 보기. 넘기거나 새 목록이면 커서를 첫 줄로 */
class DrMok5 {
    var bu = 0
    private var chojeomHal = false

    fun <T> geurigi(t: GilnunActivity, l: List<T>, jul: (T) -> View) {
        if (l.isEmpty()) { bu = 0; return }
        if (bu >= l.size) bu = ((l.size - 1) / 5) * 5
        var cheot: View? = null
        for (x in l.subList(bu, minOf(bu + 5, l.size))) {
            val v = jul(x)
            if (cheot == null) cheot = v
        }
        if (bu + 5 < l.size) t.danchu("더 보기") { bu += 5; chojeomHal = true; t.dasiGeurigi() }
        if (bu > 0) t.danchu("이전 보기") { bu = maxOf(0, bu - 5); chojeomHal = true; t.dasiGeurigi() }
        val c = cheot
        if (chojeomHal && c != null) { chojeomHal = false; t.chojeomOmgigi(c) }
    }

    /** 새 목록을 받았을 때 — 처음 다섯으로, 커서는 첫 줄로 */
    fun saeMok() { bu = 0; chojeomHal = true }
}

/** 길게 누르기와 톡백 동작 메뉴에 같은 일을 붙임(아이폰 accessibilityAction·contextMenu) */
fun drDongjak(t: GilnunActivity, v: View, dongjak: List<Pair<String, () -> Unit>>) {
    for ((ireum, f) in dongjak) {
        ViewCompat.addAccessibilityAction(v, ireum) { _, _ -> f(); true }
    }
    v.isLongClickable = true
    v.setOnLongClickListener {
        AlertDialog.Builder(t)
            .setItems(dongjak.map { it.first }.toTypedArray()) { _, i -> dongjak.getOrNull(i)?.second?.invoke() }
            .setNegativeButton("그만두기", null)
            .show()
        true
    }
}

/** 받침에 따라 을/를 */
fun drEul(w: String): String {
    val c = w.trim().lastOrNull() ?: return "을"
    if (c in '가'..'힣') return if ((c - '가') % 28 != 0) "을" else "를"
    return "을"
}

/** 엔터(찾기)를 누르면 f — 입력 칸 한 줄 */
fun drEnter(e: EditText, f: () -> Unit) {
    e.imeOptions = EditorInfo.IME_ACTION_SEARCH
    e.setSingleLine(true)
    e.setOnEditorActionListener { _, id, ev ->
        val enter = ev != null && ev.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN
        if (id == EditorInfo.IME_ACTION_SEARCH || id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_ACTION_GO || enter) { f(); true } else false
    }
}

/** 고른 곳으로 — 2킬로미터 안이면 곧바로 걷는 안내, 멀면 길 찾기 탭의 "어떻게 가실지" 화면(아이폰 gotEuroGagi) */
fun gotEuroGagi(t: GilnunActivity, j: Jangso) {
    Jeulgyeo.sseum(j)
    var meolda = false
    Wichi.jigeum?.let { w -> meolda = Wichi.geori(w.lat, w.lon, j.lat, j.lon) > 2000 }
    if (meolda) {
        t.cheotHwamyeonEuro(MokjeokHwamyeon(j))
        Sori.mal("${j.ireum}. 걸어가시기에는 먼 곳이라 어떻게 가실지 고르시는 화면을 엽니다.")
    } else {
        // 맞는 점지도가 있으면 한 번 여쭘(점지도를 먼저 권함)
        AnnaeEngine.georeoGagiBoda(j) { q -> if (q != null) Sori.mal(q) }
        t.cheotHwamyeonEuro(null)
    }
    Girok.namgi("dulreo_gagi", mapOf("meolda" to meolda))
}

// MARK: 둘레 찾기

class DulleHwamyeon : Hwamyeon("둘레 찾기") {
    private var geuBakk = false
    override fun chaeugi(t: GilnunActivity) {
        for (j in DrDulleJong.modu.filter { it.geot }) t.danchu(j.ireum) { t.yeolgi(jariro(j)) }
        t.pyeolchigi("그 밖에", geuBakk) { geuBakk = !geuBakk }
        if (geuBakk) for (j in DrDulleJong.modu.filter { !it.geot }) t.danchu(j.ireum) { t.yeolgi(jariro(j)) }
    }

    private fun jariro(j: DrDulleJong): Hwamyeon = when (j.id) {
        "bapjip" -> BapjipHwamyeon()
        "daepiso" -> DaepisoHwamyeon()
        else -> GotMokrokHwamyeon(DrGotCheo.Dulle(j))
    }
}

class BapjipHwamyeon : Hwamyeon("식당") {
    private var galae: List<DrBapjipGalae>? = null
    private var mot = false
    private var batneun = false
    private val m5 = DrMok5()

    override fun chaeugi(t: GilnunActivity) {
        if (galae == null && !mot && !batneun) bureogi(t)
        val l = galae
        if (l != null) {
            m5.geurigi(t, l) { g -> t.danchu(g.ireum) { t.yeolgi(GotMokrokHwamyeon(DrGotCheo.Bapjip(g))) } }
        } else if (mot) {
            t.danchu("갈래를 받지 못했습니다 — 다시 받기") { bureogi(t) }
        } else {
            t.geul("밥집 갈래를 받는 중입니다.", true)
        }
    }

    private fun bureogi(t: GilnunActivity) {
        mot = false
        batneun = true
        Dulreo.bapjipGalae { r ->
            batneun = false
            if (r != null) { galae = r; m5.saeMok() } else mot = true
            if (t.wiHwamyeon === this) t.dasiGeurigi()
        }
    }
}

class DaepisoHwamyeon : Hwamyeon("대피소") {
    override fun chaeugi(t: GilnunActivity) {
        for ((ir, nm) in DrDulleJong.daepi) t.danchu(ir) { t.yeolgi(GotMokrokHwamyeon(DrGotCheo.Daepiso(ir, nm))) }
    }
}

/** 목록을 어디서 받아 오는가(아이폰 GotCheo) */
sealed class DrGotCheo {
    class Dulle(val j: DrDulleJong) : DrGotCheo()
    class Bapjip(val g: DrBapjipGalae) : DrGotCheo()
    class Daepiso(val ireum: String, val natmal: String) : DrGotCheo()
    class Gabol(val a: String) : DrGotCheo()

    val jemok: String
        get() = when (this) {
            is Dulle -> j.jjalbeun
            is Bapjip -> g.ireum
            is Daepiso -> ireum
            is Gabol -> gabolIreum(a)
        }

    companion object {
        fun gabolIreum(a: String): String = when (a) {
            "chukje" -> "축제"
            "mujangae" -> "무장애 여행 정보"
            else -> "가 볼 곳"
        }
    }
}

/** 찾은 곳 목록 — 다섯 개씩, 이름을 두드리면 그곳으로, 전화와 주소는 같은 줄의 동작(길게 누르기·톡백 동작 메뉴) */
class GotMokrokHwamyeon(private val cheo: DrGotCheo) : Hwamyeon(cheo.jemok) {
    /** 가는 김에 — gabol, chukje, mujangae */
    constructor(a: String) : this(DrGotCheo.Gabol(a))

    private var mokrok: List<DrGot>? = null
    private var mot = ""
    private var chatneun = false
    private var cheoeum = true
    private val m5 = DrMok5()

    override fun chaeugi(t: GilnunActivity) {
        if (cheoeum) { cheoeum = false; chatgi(t, false) }
        val l = mokrok
        if (l != null && l.isNotEmpty()) {
            m5.geurigi(t, l) { g ->
                val b = t.danchu(g.julMal) { gotEuroGagi(t, g.jangso) }
                drDongjak(t, b, listOf(
                    (if (g.jeonhwa.isNotEmpty()) "전화 걸기 — ${g.jeonhwa}" else "전화 걸기") to { jeonhwa(t, g) },
                    "주소 듣기" to { Sori.mal(if (g.juso.isEmpty()) "주소가 없습니다." else g.juso) }
                ))
                b
            }
            t.geul("이름을 두드리시면 그곳까지 안내합니다. 전화와 주소는 이름을 길게 누르시거나 톡백 동작 메뉴에서 고르십시오.")
        } else if (mot.isNotEmpty()) {
            t.geul(mot, true)
            t.danchu("다시 찾기") { chatgi(t) }
        } else {
            t.geul("${cheo.jemok}${drEul(cheo.jemok)} 찾는 중입니다.", true)
        }
    }

    private fun jeonhwa(t: GilnunActivity, g: DrGot) {
        val b = g.jeonhwa.filter { it.isDigit() }
        if (b.isEmpty()) { Sori.mal("이곳은 전화번호가 없습니다."); return }
        try {
            t.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$b")))
        } catch (e: Exception) {
            Sori.mal("전화 화면을 열지 못했습니다. 번호는 ${g.jeonhwa} 입니다.")
        }
    }

    /** geurigi — 그리는 중(chaeugi)에 부를 때는 거짓(곧바로 다시 그리지 않음) */
    private fun chatgi(t: GilnunActivity, geurigi: Boolean = true) {
        if (chatneun) return
        mot = ""
        if (Wichi.jigeum == null) {
            mot = "아직 위치를 잡는 중입니다. 잠시 뒤 다시 찾기를 눌러 주십시오."
            Sori.mal(mot)
            if (geurigi && t.wiHwamyeon === this) t.dasiGeurigi()
            return
        }
        chatneun = true
        val kkeut: (List<DrGot>?) -> Unit = { r ->
            chatneun = false
            if (r == null) {
                mot = "찾지 못했습니다. 통신이 끊겼을 수 있습니다."
                Sori.mal(mot)
            } else if (r.isEmpty()) {
                val c = cheo
                mot = if (c is DrGotCheo.Daepiso)
                    "${c.ireum} 자료를 아직 받아 오지 못했습니다. 행정안전부 대피소 자료가 들어오는 대로 이 자리에서 알려 드립니다. 급하실 때는 가까운 주민센터나 학교 운동장으로 가시고, 둘레 찾기의 공공기관에서 주민센터를 찾으실 수 있습니다."
                else "가까이에서 ${cheo.jemok}${drEul(cheo.jemok)} 찾지 못했습니다."
                Sori.mal(mot)
            } else {
                mokrok = r
                m5.saeMok()
                Sori.mal("${cheo.jemok} ${r.size}곳을 가까운 차례로 찾았습니다.", MalGeup.JEONGBO)
                Girok.namgi("dulreo_mokrok", mapOf("cheo" to cheo.jemok, "su" to r.size))
            }
            if (t.wiHwamyeon === this) t.dasiGeurigi()
        }
        when (val c = cheo) {
            is DrGotCheo.Dulle -> Dulreo.dulle(c.j, kkeut)
            is DrGotCheo.Bapjip -> Dulreo.bapjip(c.g.id, kkeut)
            is DrGotCheo.Daepiso -> Dulreo.daepiso(c.natmal, kkeut)
            is DrGotCheo.Gabol -> Dulreo.gabol(c.a, kkeut)
        }
    }
}

// MARK: 고장 이야기

class GojangHwamyeon : Hwamyeon("고장 이야기") {
    private var mal = ""
    private var batneun = false
    private var malChojeom = false

    override fun chaeugi(t: GilnunActivity) {
        t.danchu(if (batneun) "알아보는 중입니다" else "지금 이 고장 이야기 듣기") { deutgi(t) }
        if (mal.isNotEmpty()) {
            val v = t.geul(mal, true)
            if (malChojeom) { malChojeom = false; t.chojeomOmgigi(v) }
        }
        lateinit var jb: Button
        jb = t.danchu(jadongGeul()) {
            DulreoSeol.gojangJadong = !DulreoSeol.gojangJadong
            jb.text = jadongGeul()
            jb.announceForAccessibility(if (DulreoSeol.gojangJadong) "차 안에서 고장이 바뀌면 들려 드립니다." else "차 안에서 고장이 바뀌어도 들려 드리지 않습니다.")
        }
    }

    private fun jadongGeul() = "차 안에서 고장이 바뀌면 들려 주기 — 지금 " + (if (DulreoSeol.gojangJadong) "켜짐, 누르면 꺼짐" else "꺼짐, 누르면 켜짐")

    private fun deutgi(t: GilnunActivity) {
        if (batneun) return
        if (Wichi.jigeum == null) { Sori.mal("아직 위치를 잡는 중입니다."); return }
        batneun = true
        if (t.wiHwamyeon === this) t.dasiGeurigi()
        Dulreo.gojang { r ->
            batneun = false
            mal = r?.first ?: "나스에 닿지 못했습니다. 통신을 확인해 주십시오."
            if (mal.isEmpty()) mal = "이 고장 이야기를 알아내지 못했습니다."
            Sori.mal(mal)
            malChojeom = true
            if (t.wiHwamyeon === this) t.dasiGeurigi()
        }
    }
}

// MARK: 어디서 어디로 — 미리 들어 보기

class MiriHwamyeon : Hwamyeon("어디서 어디로") {
    private var chulMal = ""
    private var mokMal = ""
    private var chul: Jangso? = null
    private var mok: Jangso? = null
    private var hubo: List<Jangso> = emptyList()
    private var huboChul = false
    private var geul = ""
    private var batneun = false
    private var chulPyeol = false
    private var geulChojeom = false
    private val m5 = DrMok5()

    override fun chaeugi(t: GilnunActivity) {
        if (hubo.isNotEmpty()) {
            // 결과만 남김(앞 내용은 감춤) — 커서는 첫 결과 줄로
            t.geul(if (huboChul) "출발지를 고르십시오." else "목적지를 고르십시오.", true)
            m5.geurigi(t, hubo) { j ->
                t.danchu(j.ireum + (if (j.juso.isEmpty()) "" else " — " + j.juso)) { goreugi(t, j) }
            }
            t.danchu("고르지 않고 돌아가기") { hubo = emptyList(); t.dasiGeurigi() }
            return
        }
        val me = t.ipryeok("가실 곳 — 이름이나 주소를 넣고 엔터", false)
        me.setText(mokMal)
        me.doAfterTextChanged { mokMal = it?.toString() ?: "" }
        drEnter(me) { chatgi(t, mokMal, false) }
        val m = mok
        if (m != null) t.danchu(if (batneun) "미리 들어 보는 중입니다" else "${m.ireum} — 미리 들어 보기") { deutgi(t) }
        if (geul.isNotEmpty()) {
            val gv = t.geul(geul, true)
            if (geulChojeom) { geulChojeom = false; t.chojeomOmgigi(gv) }
            t.danchu("다시 듣기") { Sori.mal(geul) }
            if (chul == null && m != null) t.danchu("이 길로 가기 — ${m.ireum}") { gotEuroGagi(t, m) }
        }
        val chulIr = chul?.ireum ?: "지금 내 자리"
        t.danchu(if (chulPyeol) "출발지 바꾸기 접기" else "출발지 바꾸기 — 지금은 $chulIr, 펼치기") { chulPyeol = !chulPyeol; t.dasiGeurigi() }
        if (chulPyeol) {
            val ce = t.ipryeok("출발지 — 이름이나 주소를 넣고 엔터", false)
            ce.setText(chulMal)
            ce.doAfterTextChanged { chulMal = it?.toString() ?: "" }
            drEnter(ce) { chatgi(t, chulMal, true) }
            if (chul != null) t.danchu("출발지를 지금 내 자리로") {
                chul = null
                geul = ""
                Sori.mal("출발지를 지금 내 자리로 바꿨습니다.", MalGeup.JEONGBO)
                t.dasiGeurigi()
            }
        }
    }

    private fun chatgi(t: GilnunActivity, q0: String, c: Boolean) {
        val q = q0.trim()
        if (q.isEmpty()) return
        Chatgi.jangso(q) { r ->
            if (r == null || r.isEmpty()) {
                Sori.mal(if (r == null) "찾지 못했습니다. 통신을 확인해 주십시오." else "$q${drEul(q)} 찾지 못했습니다. 다른 이름으로 찾아 보십시오.")
                return@jangso
            }
            huboChul = c
            hubo = r
            m5.saeMok()
            if (t.wiHwamyeon === this) t.dasiGeurigi()
        }
    }

    private fun goreugi(t: GilnunActivity, j: Jangso) {
        if (huboChul) chul = j else mok = j
        hubo = emptyList()
        geul = ""
        if (huboChul) chulPyeol = false
        if (t.wiHwamyeon === this) t.dasiGeurigi()
        if (mok != null) deutgi(t)
    }

    private fun deutgi(t: GilnunActivity) {
        val m = mok ?: return
        if (batneun) return
        val x = chul
        val c: Jangso
        if (x != null) {
            c = x
        } else {
            val w = Wichi.jigeum
            if (w == null) { Sori.mal("아직 위치를 잡는 중입니다. 출발지를 바꾸셔도 됩니다."); return }
            c = Jangso("지금 내 자리", "", w.lat, w.lon)
        }
        batneun = true
        if (t.wiHwamyeon === this) t.dasiGeurigi()
        Sori.mal("미리 들어 보는 중입니다. 잠시만 기다려 주십시오.", MalGeup.JEONGBO)
        Dulreo.miri(c, chul == null, m) { s ->
            batneun = false
            geul = s ?: "미리 들어 보기를 셈하지 못했습니다. 다시 해 주십시오."
            Sori.mal(geul)
            geulChojeom = true
            Girok.namgi("miri")
            if (t.wiHwamyeon === this) t.dasiGeurigi()
        }
    }
}

// MARK: 마실

class MasilHwamyeon : Hwamyeon("마실") {
    private var mot = false
    private var cheoeum = true
    private val m5 = DrMok5()

    override fun chaeugi(t: GilnunActivity) {
        if (cheoeum && Masil.gojang.isEmpty()) { cheoeum = false; bureogi(t, false) }
        cheoeum = false
        val l = Masil.gojang
        if (l.isNotEmpty()) {
            m5.geurigi(t, l) { g -> t.danchu(g.ireum) { t.yeolgi(MasilGoHwamyeon(g.id, if (g.jjalb.isEmpty()) g.ireum else g.jjalb)) } }
            t.geul("앉은자리에서 다녀오는 여행입니다. 가는 길과 그 고장의 것을 차례로 들으시고, 가끔은 겪는 일도 만나십니다.")
        } else if (mot) {
            t.danchu("마실 이야기를 받지 못했습니다 — 다시 받기") { bureogi(t) }
        } else {
            t.geul("마실 이야기를 받는 중입니다.", true)
        }
    }

    private fun bureogi(t: GilnunActivity, geurigi: Boolean = true) {
        mot = false
        if (geurigi && t.wiHwamyeon === this) t.dasiGeurigi()
        Masil.bureogi(t) { ok ->
            mot = !ok
            if (ok) m5.saeMok()
            if (t.wiHwamyeon === this) t.dasiGeurigi()
        }
    }
}

class MasilGoHwamyeon(private val id: String, jemok: String) : Hwamyeon(jemok) {
    private var na = 0
    private var jog = ""
    private var quizDap: String? = null
    private var cheoeum = true
    private var chojeomHal = false

    private val go: MasilGojang? get() = Masil.gojang.firstOrNull { it.id == id }

    override fun chaeugi(t: GilnunActivity) {
        val g = go
        if (g == null) {
            t.geul("이 마실 이야기를 찾지 못했습니다.", true)
            return
        }
        if (cheoeum) {
            // 처음 열 때(아이폰 onAppear) — 고장 소개와 첫 마디
            cheoeum = false
            Sori.mal("${g.ireum}. ${g.han}. 떠납니다.")
            madiMalSok()
        }
        var chojeom: View? = null
        if (na >= g.madi.size) {
            chojeom = t.geul("오늘 ${if (g.jjalb.isEmpty()) g.ireum else g.jjalb}${drEul(if (g.jjalb.isEmpty()) g.ireum else g.jjalb)} 다녀오셨습니다. 같은 곳을 다시 떠나셔도 만나시는 일이 달라집니다.", true)
            t.danchu("한 번 더 떠나기") { na = 0; madiMal(t) }
        } else {
            val md = g.madi[na]
            if (md.quiz) {
                val d = quizDap
                if (d != null) {
                    chojeom = t.geul(d, true)
                    daeumDanchu(t, g)
                } else {
                    chojeom = t.geul("여쭈어 봅니다. ${md.mut}", true)
                    for ((i, s) in md.bogi.withIndex()) {
                        t.danchu(s) {
                            val dd = if (i == md.dap) "맞았습니다. " + md.matda else "이렇습니다. " + md.teulida
                            quizDap = dd
                            Sori.mal(dd)
                            chojeomHal = true
                            t.dasiGeurigi()
                        }
                    }
                }
            } else {
                chojeom = t.geul(md.t + ". " + md.mal + (if (jog.isEmpty()) "" else " $jog"), true)
                daeumDanchu(t, g)
            }
        }
        val c = chojeom
        if (chojeomHal && c != null) { chojeomHal = false; t.chojeomJul(c); t.chojeomOmgigi(c) }
    }

    private fun daeumDanchu(t: GilnunActivity, g: MasilGojang) {
        val kkeutin = na >= g.madi.size - 1
        t.danchu(if (kkeutin) "다녀왔습니다 — 마무리" else "다음으로 (${na + 2}번째 마디)") {
            na += 1
            madiMal(t)
        }
        t.geul("모두 ${g.madi.size}마디 가운데 ${na + 1}번째입니다.")
    }

    private fun madiMal(t: GilnunActivity) {
        madiMalSok()
        if (t.wiHwamyeon === this) t.dasiGeurigi()
    }

    /** 이 마디를 말함(다시 그리지는 않음) — 다음에 그릴 때 커서를 마디 글로 */
    private fun madiMalSok() {
        quizDap = null
        jog = ""
        val g = go ?: return
        if (na >= g.madi.size) {
            Sori.mal("다녀오셨습니다.")
            Girok.namgi("masil_kkeut", mapOf("id" to id))
        } else {
            val md = g.madi[na]
            if (md.quiz) {
                Sori.mal("여쭈어 봅니다. " + md.mut)
            } else {
                jog = Masil.hanjogak(md.gyeokda)
                Sori.mal(md.mal + (if (jog.isEmpty()) "" else " $jog"))
            }
        }
        chojeomHal = true
    }
}

// MARK: 말로 하기에서(MalHagi 가 부름 — 아이폰 MalHagi 2.7.0 둘러보기 줄과 같은 말)

object DulreoMal {
    /**
     * 둘러보기 말 — 처리했으면 참. z 는 띄어쓰기를 뺀 말.
     * gojangMal/masilMal/sajinMal 은 나스 알아듣기 사전(s.itda(alts, "gojang"|"masil"|"sajin"))의 결과를 MalHagi 가 넘겨줌
     */
    fun myeongryeong(z: String, gojangMal: Boolean, masilMal: Boolean, sajinMal: Boolean, dap: (String, Boolean) -> Unit): Boolean {
        val a = Kamera.hwalseongEotgi() ?: MalHagi.hwalseong?.get()
        // 카메라 눈(아이폰 2.17.0~2.23.0 — b3 이 옮긴 빛·사람·글자·문·QR 은 b3 줄에서)
        if (z.contains("무슨색") || z.contains("색깔") || z.contains("색알려") || z.contains("색이뭐") || z.contains("지폐") || z.contains("얼마짜리")) {
            dap("지폐와 색깔 알아보기를 엽니다.", false)
            a?.let { KameraNun.dulreoYeolgi(it, JipyeSaekHwamyeon()) }
            return true
        }
        if (z.contains("바코드") || z.contains("상품뭐") || z.contains("무슨상품") || z.contains("상품이름") || z.contains("상품읽")) {
            dap("상품 바코드 읽기를 엽니다.", false)
            a?.let { KameraNun.dulreoYeolgi(it, SangpumHwamyeon()) }
            return true
        }
        if (z.contains("이게뭐") || z.contains("이거뭐") || z.contains("뭐가보여") || z.contains("무엇이보여") || z.contains("한마디설명")) {
            dap("한마디 설명을 엽니다.", false)
            a?.let { KameraNun.dulreoYeolgi(it, HanmadiHwamyeon()) }
            return true
        }
        if (z.contains("가리키") || z.contains("가리킨")) {
            dap("가리키고 말하기를 엽니다.", false)
            a?.let { KameraNun.dulreoYeolgi(it, GarikiHwamyeon()) }
            return true
        }
        // 2.7.0 둘러보기 — 고장 이야기, 마실, 사진 읽어 주기, 안면인식, 축제, 둘레 찾기
        if (gojangMal && !(z.contains("노래") || z.contains("음악"))) {
            Dulreo.gojang { r ->
                if (r == null || r.first.isEmpty()) dap("이 고장 이야기를 받지 못했습니다. 통신과 위치를 확인해 주십시오.", false)
                else dap(r.first, false)
            }
            return true
        }
        if (masilMal) { dap("마실을 엽니다. 떠나실 고장을 고르십시오.", false); a?.let { KameraNun.dulreoYeolgi(it, MasilHwamyeon()) }; return true }
        if (sajinMal) { dap("사진 읽어 주기를 엽니다. 사진 찍어 읽어 주기 단추를 두드리십시오.", false); a?.let { KameraNun.dulreoYeolgi(it, SajinHwamyeon()) }; return true }
        if (z.contains("안면") || z.contains("얼굴인식") || z.contains("누가있")) {
            dap("안면인식을 엽니다.", false)
            if (a != null) {
                KameraNun.dulreoYeolgi(a, AnmyeonHwamyeon())
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val h = a.wiHwamyeon
                    if (h is AnmyeonHwamyeon) h.malroKyeogi(a)
                }, 1500)
            }
            return true
        }
        if (z.contains("축제")) {
            Dulreo.gabol("chukje") { r ->
                when {
                    r == null -> dap("축제 소식을 받지 못했습니다. 통신과 위치를 확인해 주십시오.", false)
                    r.isEmpty() -> dap("10킬로미터 안에 지금 알려진 축제가 없습니다.", false)
                    else -> dap("가까운 축제 ${minOf(3, r.size)}곳입니다. " + r.take(3).joinToString(" ") { it.julMal } + " 더 들으시려면 둘러보기 탭의 가는 김에에서 축제를 여십시오.", false)
                }
            }
            return true
        }
        val jong = DrDulleJong.malEseo(z)
        if (jong != null && (listOf("근처", "가까운", "가까이", "주변", "제일가까", "찾아", "어디").any { z.contains(it) } || z.length <= 6)) {
            val nm = jong.jjalbeun
            val ieo: (List<DrGot>?) -> Unit = { r ->
                when {
                    r == null -> dap("$nm${drEul(nm)} 찾지 못했습니다. 통신과 위치를 확인해 주십시오.", false)
                    r.isEmpty() -> dap("1킬로미터 안에 $nm${drI(nm)} 없습니다.", false)
                    else -> {
                        // 아이폰은 가까운 세 곳을 하나씩 여쭈어 곧장 안내 — 안드로이드는 세 곳을 들려 드리고 목록 화면을 엶(이름을 두드리면 안내)
                        dap("가까운 $nm ${minOf(3, r.size)}곳입니다. " + r.take(3).joinToString(" ") { it.julMal + "." } + " 둘러보기 탭에 목록을 열었습니다. 가실 곳 이름을 두드리시면 안내합니다.", false)
                        a?.let { KameraNun.dulreoYeolgi(it, GotMokrokHwamyeon(DrGotCheo.Dulle(if (jong.id == "sikdang") DrDulleJong("", "식당", "음식점", true) else jong))) }
                    }
                }
            }
            if (Wichi.jigeum == null) { dap("아직 위치를 잡는 중입니다. 잠시 뒤 다시 말씀해 주십시오.", false); return true }
            if (jong.id == "sikdang") {
                Dulreo.dulle(jong) { r ->
                    if (r == null || r.isNotEmpty()) ieo(r)
                    else Dulreo.dulle(DrDulleJong("", "식당", "음식점", true), ieo)
                }
            } else {
                Dulreo.dulle(jong, ieo)
            }
            return true
        }
        return false
    }

    private fun drI(w: String): String {
        val c = w.trim().lastOrNull() ?: return "이"
        if (c in '가'..'힣') return if ((c - '가') % 28 != 0) "이" else "가"
        return "이"
    }

    /** 모든 카메라 눈과 함께 이 묶음의 눈도 끔(그만·하던 일 멈춰) — Kamera.modukkeugi 가 이미 모두 끄므로 따로 부를 일은 없음 */
    fun geuman() = Kamera.modukkeugi(true)
}
