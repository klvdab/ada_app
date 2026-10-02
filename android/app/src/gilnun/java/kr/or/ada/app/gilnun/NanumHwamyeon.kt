// 안드로이드 길눈 — 나눔 탭 화면(2.7.0 묶음 b6_nanum, 아이폰 길눈 NanumView.swift 2.9.0과 같은 차례, 같은 말)
// 단추 셋: 나눔 마당, 걸음 나눔과 게시판, 길 부탁하기. 웹 길눈과 같은 자료 창고를 씁니다.
// 목록은 다섯씩, 아래에 더 보기와 이전 보기. 목록이 새로 나오면 커서를 첫 결과 줄로. 줄에 번호는 붙이지 않음.
// 아이폰의 "위아래로 쓸어 고르기"(보이스오버 동작)는 톡백 동작 메뉴의 사용자 동작과 길게 누르기로 같은 일을 함.
package kr.or.ada.app.gilnun

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.widget.doAfterTextChanged
import java.util.Calendar

// MARK: 함께 쓰는 조각

/** 다섯씩 보여 주기 — 다섯 줄 아래에 더 보기, 그 아래에 이전 보기. 넘기면 커서를 새 첫 줄로 */
internal class NnMok5 {
    var bu = 0
    var chojeomHal = false

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

/** 펼치기 — 단추 글자를 아이폰과 같이(펼치기 뒤에 덧말이 붙는 것도) */
internal fun nnPyeolchigi(t: GilnunActivity, pyeolGeul: String, jeopGeul: String, pyeolchim: Boolean, toggle: () -> Unit) {
    t.danchu(if (pyeolchim) jeopGeul else pyeolGeul) { toggle(); t.dasiGeurigi() }
}

/** 알아 두실 것 — 펼치기 안에 한 줄씩 */
internal fun nnAraDul(t: GilnunActivity, pyeolchim: Boolean, juldeul: List<String>, toggle: () -> Unit) {
    t.pyeolchigi("알아 두실 것", pyeolchim, toggle)
    if (pyeolchim) for (j in juldeul) t.geul(j)
}

/** 길게 누르기와 톡백 동작 메뉴에 같은 일을 붙임(아이폰 accessibilityAction·contextMenu) */
internal fun nnDongjak(t: GilnunActivity, v: View, dongjak: List<Pair<String, () -> Unit>>) {
    for ((ireum, f) in dongjak) {
        ViewCompat.addAccessibilityAction(v, ireum) { _, _ -> f(); true }
    }
    v.setOnLongClickListener {
        AlertDialog.Builder(t)
            .setItems(dongjak.map { it.first }.toTypedArray()) { _, i -> dongjak.getOrNull(i)?.second?.invoke() }
            .setNegativeButton("그만두기", null)
            .show()
        true
    }
}

/** 지울까요 같은 물음 — 아이폰 confirmationDialog */
internal fun nnMureum(t: GilnunActivity, mureum: String, ye: String, f: () -> Unit) {
    AlertDialog.Builder(t)
        .setTitle(mureum)
        .setPositiveButton(ye) { _, _ -> f() }
        .setNegativeButton("그만두기", null)
        .show()
}

/** 글 칸 — 담아 둔 값을 넣고, 바뀌면 받아 둠 */
internal fun nnKan(t: GilnunActivity, ireum: String, gap: String, sutja: Boolean = false, yeoreoJul: Boolean = false, bakkwim: (String) -> Unit): EditText {
    val e = t.ipryeok(ireum, false)
    if (sutja) e.inputType = InputType.TYPE_CLASS_NUMBER
    if (yeoreoJul) {
        e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        e.setMinLines(3)
        e.setMaxLines(8)
    }
    e.setText(gap)
    e.doAfterTextChanged { bakkwim(it?.toString() ?: "") }
    return e
}

internal fun nnBoinda(t: GilnunActivity, h: Hwamyeon) = !t.isFinishing && t.wiHwamyeon === h

internal fun nnJeonhwa(t: GilnunActivity, beonho: String) {
    try {
        t.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$beonho")))
    } catch (e: Exception) {
        Sori.mal("전화 화면을 열지 못했습니다. 번호는 $beonho 입니다.")
    }
}

// MARK: 나눔 탭 첫 화면

class NanumCheotHwamyeon : Hwamyeon("나눔") {
    override fun chaeugi(t: GilnunActivity) {
        t.danchu("나눔 마당 — 쓰지 않는 물건 주고받기") { t.yeolgi(NnMulnanumHwamyeon()) }
        t.danchu("걸음 나눔과 게시판") { t.yeolgi(NnGeoreumNanumHwamyeon()) }
        t.danchu("길 부탁하기 — 그려 주었으면 하는 길 남기기") { t.yeolgi(NnButakHwamyeon()) }
    }
}

// MARK: 나눔 마당

class NnMulnanumHwamyeon : Hwamyeon("나눔 마당") {
    private var ara = false
    override fun chaeugi(t: GilnunActivity) {
        t.geul("쓰지 않는 물건을 주고받는 곳입니다.")
        t.danchu("드립니다 — 나눠 주실 물건 보기") { t.yeolgi(NnMulMokHwamyeon("deurim")) }
        t.danchu("찾습니다 — 필요하신 분들 보기") { t.yeolgi(NnMulMokHwamyeon("chatgi")) }
        t.danchu("글 올리기") { t.yeolgi(NnMulSseugiHwamyeon()) }
        nnAraDul(t, ara, listOf(
            "시각장애인과 자원봉사자가 함께 쓰는 곳입니다. 누구나 드리실 수 있고 누구나 찾으실 수 있습니다.",
            "협회는 물건을 갖지 않습니다. 드리는 분과 찾는 분을 잇기만 합니다. 주고받는 일은 두 분이 직접 하십니다.",
            "연락 받으실 방법은 이 마당에 그대로 드러납니다. 남에게 알려도 괜찮은 번호만 적어 주십시오.",
            "글은 여든 날이 지나면 저절로 내려갑니다. 다 나누셨으면 글 아래 다 나눴습니다를 눌러 주십시오.",
            "물건값을 주고받는 곳이 아닙니다. 파실 물건은 올리지 말아 주십시오."
        )) { ara = !ara }
    }
}

class NnMulMokHwamyeon(private val jong: String) : Hwamyeon(if (jong == "deurim") "드립니다" else "찾습니다") {
    private val ireum = if (jong == "deurim") "드립니다" else "찾습니다"
    private var mok: List<NnMulGeul>? = null
    private var mot = false
    private var batneun = false
    private val m5 = NnMok5()

    override fun chaeugi(t: GilnunActivity) {
        val l = mok
        if (l != null && l.isNotEmpty()) {
            m5.geurigi(t, l) { g ->
                val b = t.danchu(g.julMal) { Sori.mal(g.jaseMal) }
                nnDongjak(t, b, listOf(
                    (if (g.jeonhwa != null) "전화 걸기" else "연락처 듣기") to { yeonrak(t, g) },
                    "다 나눴습니다 — 이 글 내리기" to { naerigi(t, g) }
                ))
                b
            }
            t.geul("이름을 두드리시면 자세히 읽어 드립니다. 전화와 글 내리기는 이름을 길게 누르시거나 톡백 동작 메뉴에서 고르십시오.")
        } else if (l != null) {
            t.geul("${ireum}에 아직 올라온 글이 없습니다. 첫 글을 올려 주시면 다음 분께서 바로 보십니다.", true)
        } else if (mot) {
            t.danchu("불러오지 못했습니다 — 다시 불러오기") { bulreogi(t) }
        } else {
            t.geul("${ireum}을 불러오고 있습니다.", true)
            if (!batneun) bulreogi(t)
        }
    }

    private fun yeonrak(t: GilnunActivity, g: NnMulGeul) {
        val b = g.jeonhwa
        if (b != null) nnJeonhwa(t, b) else Sori.mal("연락처는 ${g.yeon}입니다.")
    }

    private fun naerigi(t: GilnunActivity, g: NnMulGeul) {
        nnMureum(t, "이 글을 내릴까요?", "내리기") {
            NnMulnanum.naerigi(g.id) {
                Sori.mal("글을 내렸습니다.")
                mok = null
                bulreogi(t)
            }
        }
    }

    private fun bulreogi(t: GilnunActivity) {
        mot = false
        batneun = true
        NnMulnanum.mok(jong) { r ->
            batneun = false
            if (r != null) {
                mok = r
                m5.saeMok()
                if (r.isNotEmpty()) Sori.mal("$ireum ${r.size}건입니다.", MalGeup.JEONGBO)
            } else {
                mot = true
                Sori.mal("불러오지 못했습니다. 다시 해 보십시오.")
            }
            if (nnBoinda(t, this)) t.dasiGeurigi()
        }
    }
}

class NnMulSseugiHwamyeon : Hwamyeon("글 올리기") {
    private var jong = ""
    private var mul = ""
    private var mal = ""
    private var got = ""
    private var nugu: String? = null
    private var yeon = ""
    private var olineun = false

    override fun chaeugi(t: GilnunActivity) {
        if (nugu == null) nugu = NnGeoreumNanum.byeol(t)
        val bd = t.danchu("") { }
        val bc = t.danchu("") { }
        fun goreumGeul() {
            bd.text = if (jong == "deurim") "드립니다 — 골랐음" else "드립니다"
            bc.text = if (jong == "chatgi") "찾습니다 — 골랐음" else "찾습니다"
            if (Build.VERSION.SDK_INT >= 30) {
                bd.stateDescription = if (jong == "deurim") "선택됨" else null
                bc.stateDescription = if (jong == "chatgi") "선택됨" else null
            }
        }
        goreumGeul()
        bd.setOnClickListener { jong = "deurim"; goreumGeul(); Sori.mal("드립니다로 정했습니다. 무엇인지 적어 주십시오.") }
        bc.setOnClickListener { jong = "chatgi"; goreumGeul(); Sori.mal("찾습니다로 정했습니다. 무엇인지 적어 주십시오.") }
        nnKan(t, "무엇입니까", mul) { mul = it }
        nnKan(t, "한마디", mal) { mal = it }
        nnKan(t, "어느 지역", got) { got = it }
        nnKan(t, "이름 또는 별명", nugu ?: "") { nugu = it }
        nnKan(t, "연락 받으실 방법", yeon) { yeon = it }
        val ob = t.danchu(if (olineun) "올리는 중입니다" else "올리기") { }
        ob.setOnClickListener { olligi(t, ob) }
        t.danchu("그만두기") { Sori.mal("그만두었습니다."); t.dwiro() }
    }

    private fun olligi(t: GilnunActivity, ob: TextView) {
        if (olineun) return
        if (jong.isEmpty()) { Sori.mal("드립니다인지 찾습니다인지 골라 주십시오."); return }
        val m = mul.trim()
        val y = yeon.trim()
        if (m.isEmpty()) { Sori.mal("무엇인지 적어 주십시오."); return }
        if (y.isEmpty()) { Sori.mal("연락 받으실 방법을 적어 주십시오."); return }
        olineun = true
        ob.text = "올리는 중입니다"
        NnMulnanum.olligi(jong, m, mal.trim(), got.trim(), (nugu ?: "").trim(), y) { e ->
            olineun = false
            if (nnBoinda(t, this)) ob.text = "올리기"
            if (e != null) { Sori.mal(e); return@olligi }
            Sori.mal("올렸습니다. 고맙습니다.")
            if (nnBoinda(t, this)) t.dwiro()
        }
    }
}

// MARK: 걸음 나눔과 게시판

class NnGeoreumNanumHwamyeon : Hwamyeon("걸음 나눔과 게시판") {
    override fun chaeugi(t: GilnunActivity) {
        t.geul("읽고 적는 자리입니다.")
        t.danchu("걸음 나눔 — 걸어 보고 한마디 적기") { t.yeolgi(NnNanumGeulHwamyeon()) }
        t.geul("시각장애인과 자원봉사자가 같은 자리에 적습니다. 별명만 적으시면 되고 실명은 받지 않습니다.")
        t.danchu("함께하기 — 자원봉사자와 제도를 알아보기") { t.yeolgi(NnHamkkeHwamyeon()) }
        t.geul("자원봉사 요령과 봉사시간, 관련 제도와 법, 후원과 광고 원칙까지 한자리에 담았습니다.")
    }
}

class NnNanumGeulHwamyeon : Hwamyeon("걸음 나눔") {
    private var mok: List<NnNanumGeul> = emptyList()
    private var modu = 0
    private var bu = 0
    private var mot = false
    private var badeum = false
    private var batneun = false
    private var gaengsinHal = true    // 처음과, 글을 보고 돌아왔을 때 다시 받음(아이폰 onAppear)
    private var chojeomHal = false
    private var jeokgi = false
    private var byeol: String? = null
    private var geul = ""
    private var jam = ""
    private var olineun = false

    override fun chaeugi(t: GilnunActivity) {
        if (byeol == null) byeol = NnGeoreumNanum.byeol(t)
        t.pyeolchigi("한마디 적기", jeokgi) { jeokgi = !jeokgi }
        if (jeokgi) {
            t.geul("걸으신 이야기, 그려 주신 이야기, 이 프로그램에 바라시는 것을 자유롭게 적는 자리입니다. 전화번호나 주소는 적지 마십시오.")
            nnKan(t, "별명 (본명을 적지 않으셔도 됩니다)", byeol ?: "") { byeol = it }
            nnKan(t, "하실 말씀", geul, yeoreoJul = true) { geul = it }
            nnKan(t, "지울 때 쓸 네 자리 숫자", jam, sutja = true) { jam = it }
            t.geul("이 네 자리를 기억해 두시면 나중에 그 글을 가리실 수 있습니다. 비밀번호가 아니니 쉬운 숫자로 하셔도 됩니다.")
                .setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            val ob = t.danchu(if (olineun) "올리는 중입니다" else "올리기") { }
            ob.setOnClickListener { olligi(t, ob) }
        }
        if (mok.isEmpty()) {
            t.geul(if (mot) "글을 불러오지 못했습니다." else if (badeum) "적힌 글이 없습니다." else "불러오는 중입니다.", true)
        } else {
            var cheot: View? = null
            for (g in mok) {
                val b = t.danchu(g.meori + ". " + g.geul + (if (g.dat.isEmpty()) "" else " · 댓글 ${g.dat.size}개")) {
                    gaengsinHal = true
                    t.yeolgi(NnNanumSangseHwamyeon(g))
                }
                if (cheot == null) cheot = b
            }
            if (bu + 5 < modu) t.danchu("더 보기") { bu += 5; bulreogi(t) }
            if (bu > 0) t.danchu("이전 보기") { bu = maxOf(0, bu - 5); bulreogi(t) }
            val c = cheot
            if (chojeomHal && c != null) { chojeomHal = false; t.chojeomOmgigi(c) }
        }
        if (gaengsinHal && !batneun) { gaengsinHal = false; bulreogi(t) }
    }

    private fun bulreogi(t: GilnunActivity) {
        mot = false
        batneun = true
        NnGeoreumNanum.mok(bu) { r ->
            batneun = false
            badeum = true
            if (r == null) {
                mot = true
                Sori.mal("글을 불러오지 못했습니다.")
            } else {
                mok = r.first
                modu = r.second
                chojeomHal = true
            }
            if (nnBoinda(t, this)) t.dasiGeurigi()
        }
    }

    private fun olligi(t: GilnunActivity, ob: TextView) {
        if (olineun) return
        val b = (byeol ?: "").trim()
        val g = geul.trim()
        val j = jam.filter { it.isDigit() }
        if (b.isEmpty()) { Sori.mal("별명을 적어 주십시오."); return }
        if (g.isEmpty()) { Sori.mal("하실 말씀을 적어 주십시오."); return }
        if (j.length != 4) { Sori.mal("지울 때 쓸 네 자리 숫자를 적어 주십시오."); return }
        olineun = true
        ob.text = "올리는 중입니다"
        Sori.mal("올리는 중입니다.", MalGeup.JEONGBO)
        NnGeoreumNanum.sseugi(b, g, j) { e ->
            olineun = false
            if (e != null) {
                if (nnBoinda(t, this)) ob.text = "올리기"
                Sori.mal(e)
                return@sseugi
            }
            NnGeoreumNanum.byeolNoki(t, b)
            geul = ""
            Sori.mal("올렸습니다. 고맙습니다.")
            bu = 0
            bulreogi(t)
            if (nnBoinda(t, this)) t.dasiGeurigi()
        }
    }
}

class NnNanumSangseHwamyeon(private val g: NnNanumGeul) : Hwamyeon("걸음 나눔 글") {
    private var byeol: String? = null
    private var mal = ""
    private var jam = ""
    private var garimJam = ""
    private var olineun = false
    private var datPyeol = false
    private var garimPyeol = false
    private var cheotBoim = true

    override fun chaeugi(t: GilnunActivity) {
        if (byeol == null) byeol = NnGeoreumNanum.byeol(t)
        t.geul(g.meori).setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        val bon = t.geul(g.geul, true)
        if (cheotBoim) { cheotBoim = false; t.chojeomJul(bon) }   // 매번 글로 커서(아이폰 2.12.1)
        if (g.dat.isEmpty()) {
            t.geul("댓글이 없습니다.")
        } else {
            nnMeori(t, "댓글 ${g.dat.size}개")
            for (d in g.dat) t.geul("${if (d.nugu == "길눈") "길눈" else d.byeol + " · " + d.nugu} · ${d.nal} — ${d.geul}")
        }
        t.pyeolchigi("댓글 달기", datPyeol) { datPyeol = !datPyeol }
        if (datPyeol) {
            nnKan(t, "댓글 다실 분 별명", byeol ?: "") { byeol = it }
            nnKan(t, "댓글 내용", mal, yeoreoJul = true) { mal = it }
            nnKan(t, "댓글 지울 때 쓸 네 자리 숫자", jam, sutja = true) { jam = it }
            val ob = t.danchu(if (olineun) "올리는 중입니다" else "이 댓글 올리기") { }
            ob.setOnClickListener { datOlligi(t, ob) }
        }
        nnPyeolchigi(t, "이 글 가리기 펼치기 — 네 자리 숫자", "이 글 가리기 접기", garimPyeol) { garimPyeol = !garimPyeol }
        if (garimPyeol) {
            nnKan(t, "적으실 때 넣으신 네 자리 숫자", garimJam, sutja = true) { garimJam = it }
            t.danchu("이 글 가리기") { garigi(t) }
        }
    }

    private fun datOlligi(t: GilnunActivity, ob: TextView) {
        if (olineun) return
        val b = (byeol ?: "").trim()
        val m = mal.trim()
        val j = jam.filter { it.isDigit() }
        if (b.isEmpty()) { Sori.mal("별명을 적어 주십시오."); return }
        if (m.isEmpty()) { Sori.mal("하실 말씀을 적어 주십시오."); return }
        if (j.length != 4) { Sori.mal("지울 때 쓸 네 자리 숫자를 적어 주십시오."); return }
        olineun = true
        ob.text = "올리는 중입니다"
        NnGeoreumNanum.dat(g.id, b, m, j) { e ->
            olineun = false
            if (e != null) {
                if (nnBoinda(t, this)) ob.text = "이 댓글 올리기"
                Sori.mal(e)
                return@dat
            }
            NnGeoreumNanum.byeolNoki(t, b)
            Sori.mal("댓글을 올렸습니다. 고맙습니다.")
            if (nnBoinda(t, this)) t.dwiro()
        }
    }

    private fun garigi(t: GilnunActivity) {
        val j = garimJam.filter { it.isDigit() }
        if (j.isEmpty()) { Sori.mal("적으실 때 넣으신 네 자리 숫자를 적어 주십시오."); return }
        NnGeoreumNanum.garigi(g.id, j) { ok, m ->
            if (m.isNotEmpty()) Sori.mal(m)
            if (ok && nnBoinda(t, this)) t.dwiro()
        }
    }
}

/** 제목 줄(톡백 제목으로 읽힘) */
internal fun nnMeori(t: GilnunActivity, geul: String): TextView {
    val v = t.geul(geul, true)
    v.setTypeface(v.typeface, android.graphics.Typeface.BOLD)
    if (Build.VERSION.SDK_INT >= 28) v.isAccessibilityHeading = true
    return v
}

/** 함께하기 — 웹 길눈 hamkkehagi.html 의 글을 그대로(마당 제목을 두드리면 펼쳐짐) */
class NnHamkkeHwamyeon : Hwamyeon("함께하기") {
    private val yeollin = HashSet<Int>()

    override fun chaeugi(t: GilnunActivity) {
        t.geul("자원봉사 교육과 제도 안내입니다. 마당 제목을 두드리시면 펼쳐집니다.")
        MADANG.forEachIndexed { i, (jemok, juldeul) ->
            val on = i in yeollin
            t.danchu(if (on) "$jemok 접기" else "$jemok 펼치기") {
                if (on) yeollin.remove(i) else yeollin.add(i)
                t.dasiGeurigi()
            }
            if (on) for (j in juldeul) {
                if (j.startsWith("#")) nnMeori(t, j.substring(1)) else t.geul(j)
            }
        }
        val d = NnJeojang.d(t)
        val dg = t.geul(daIlgeunMal(d.getString("nn.hamkkeDone", "") ?: ""))
        dg.visibility = if ((d.getString("nn.hamkkeDone", "") ?: "").isEmpty()) View.GONE else View.VISIBLE
        t.danchu("이 안내를 다 읽었습니다") {
            val c = Calendar.getInstance()
            val nal = "${c.get(Calendar.YEAR)}년 ${c.get(Calendar.MONTH) + 1}월 ${c.get(Calendar.DAY_OF_MONTH)}일"
            d.edit().putString("nn.hamkkeDone", nal).apply()
            Girok.namgi("hamkke_done")
            Sori.mal("다 읽으신 것으로 남겼습니다.")
            dg.text = daIlgeunMal(nal)
            dg.visibility = View.VISIBLE
        }
    }

    private fun daIlgeunMal(nal: String) = "${nal}에 다 읽으신 것으로 남아 있습니다."

    companion object {
        val MADANG: List<Pair<String, List<String>>> = listOf(
            "하나. 왜 이 일을 하는가" to listOf(
                "세상에는 세상을 보고 살 수 없는 사람이 있습니다. 그분들에게 필요한 것은 동정이 아니라 눈입니다.",
                "길눈(시각장애인 점지도 서비스)은 자원봉사자가 길을 걸어 점으로 이어 둔 점지도를 시각장애인이 따라 걷게 하는 일입니다.",
                "점지도는 어느 한 사람을 위한 것이 아닙니다. 국민 모두가 저마다 또 하나의 눈이 되어 주는 일입니다. 한 사람이 자기 동네 한 길을 걸어 그려 두면, 그 길은 그 동네 시각장애인 모두의 길이 됩니다. 한 번 그린 길은 사라지지 않고 남습니다. 그것이 이 일의 힘입니다.",
                "사단법인 한국시각장애인현장영상해설협회는 2015년부터 현장영상해설사를 길러 왔고, 전국 마흔세 곳 지방자치단체에 현장영상해설 조례를 세웠습니다. 점지도는 그 위에 올리는 다음 걸음입니다."
            ),
            "둘. 무엇을 하는 일인가" to listOf(
                "#길 그리기",
                "앱을 켜고 평소대로 걸으면 걸음과 방향과 자리가 담깁니다. 걷다가 계단이나 횡단보도, 턱을 만나면 그 자리에서 단추를 눌러 남깁니다. 다 걸으면 어디에서 어디까지인지 적어 올립니다.",
                "#도움 연결",
                "길을 걷던 분이 막히면 도움 청하기를 누릅니다. 그러면 카메라가 켜지고, 받은 사람이 그 화면을 보며 말로 짚어 드립니다. 몇십 초면 끝나는 일이 그분에게는 그날의 길을 열어 줍니다.",
                "#내 길",
                "시각장애인 본인도 자기 집 앞 길을 손수 그려 둘 수 있습니다. 자기만 아는 것을 자기 말로 남기는 자리입니다."
            ),
            "셋. 시각장애인에게 말하는 법 — 가장 중요한 대목" to listOf(
                "이것만 익히셔도 절반은 하신 것입니다. 협회가 십 년 넘게 가르쳐 온 현장영상해설의 기본입니다.",
                "이것, 저것, 여기, 저기라고 하지 않습니다. 보이지 않는 사람에게는 아무 말도 아닙니다.",
                "방향은 시계 방향으로 말합니다. 그분이 보고 계신 쪽이 열두 시입니다. 오른쪽 뒤는 네다섯 시, 왼쪽은 아홉 시입니다.",
                "거리는 눈대중으로 말하지 말고 걸음이나 미터로 말합니다. 조금, 좀 더 가서는 거리가 아닙니다.",
                "전체를 먼저, 그다음 가운데, 그다음 둘레 차례로 말합니다. 눈은 한꺼번에 보지만 귀는 차례로 듣습니다.",
                "만질 수 있는 것은 만질 수 있다고 알려 드립니다. 손잡이, 난간, 점자블록, 벽. 이것이 눈을 대신합니다.",
                "위험한 것을 먼저 말합니다. 앞에 뭐가 예쁘다는 말보다 왼쪽 두 걸음 앞에 자전거가 세워져 있습니다가 먼저입니다.",
                "모르면 모른다고 합니다. 짐작으로 말한 한마디가 사람을 다치게 합니다.",
                "안내하며 걸을 때는 그분의 팔을 끌지 않습니다. 팔꿈치 위쪽을 잡으시게 하고 반걸음 앞에서 걷습니다."
            ),
            "넷. 길 그리기 요령" to listOf(
                "실제로 걸은 길만 올립니다. 차나 자전거로 지나간 것은 길이 되지 않습니다.",
                "사람이 실제로 걷는 자리로 걷습니다. 지름길이나 위험한 자리로 걸으면 그대로 남습니다.",
                "표시는 그 자리에 닿았을 때 누릅니다. 지나고 나서 누르면 몇 걸음씩 어긋납니다.",
                "커피를 사거나 화장실에 가실 때는 잠깐 멈춤을 누릅니다. 그동안은 담기지 않습니다.",
                "같은 길을 여러 사람이 걸을수록 그 길은 정확해집니다. 이미 있는 길이라고 그리지 않으실 까닭이 없습니다.",
                "보폭은 처음 한 번만 재 두시면 됩니다. 시각장애인은 걸음이 짧으므로, 거리는 미터로 담고 걷는 분의 보폭으로 다시 걸음 수를 냅니다.",
                "걸으면서 화면을 보지 마십시오. 봉사자가 다치면 아무것도 남지 않습니다."
            ),
            "다섯. 영상으로 도울 때" to listOf(
                "먼저 이름을 밝히고, 지금 무엇이 보이는지 한 문장으로 말합니다.",
                "화면이 흔들리거나 어두우면 그렇다고 말씀드리고 카메라를 어느 쪽으로 돌려 달라 청합니다.",
                "길을 건너는 순간에는 말을 아낍니다. 소리를 들으셔야 합니다.",
                "차가 오거나 위험하면 곧바로 멈추시라고 말합니다. 설명은 그다음입니다.",
                "화면에 남의 얼굴이나 서류가 보여도 그것을 말하거나 남기지 않습니다.",
                "영상은 저장하지 않습니다. 끝나면 사라집니다."
            ),
            "여섯. 자원봉사자 운영 — 어떻게 굴러가는가" to listOf(
                "처음 한 번만 등록하시면 됩니다. 기기가 기억하므로 다음부터는 아무것도 묻지 않습니다. 폰을 바꾸셨을 때만 다시 하십니다.",
                "도움 연결은 당번으로 돌아갑니다. 부름이 오면 그 시각 당번에게 먼저 울리고, 못 받으면 대기 중인 분들께 넓히고, 그래도 없으면 문자와 협회로 이어집니다.",
                "이용자가 미리 등록해 둔 가족이 있으면 가족에게 먼저 울립니다. 가족이 없거나 비장애인 가족이 없는 분은 해설사와 자봉이 받습니다.",
                "받으실 수 없는 때는 대기를 꺼 두시면 됩니다. 못 받는 것이 잘못이 아니라, 못 받을 때 켜 두는 것이 문제입니다.",
                "몇몇 분께 몰리지 않게 최근에 많이 받으신 분은 뒤로 미룹니다.",
                "실적은 처음부터 쌓입니다. 자원봉사 인정이 필요 없다 하신 분께도 쌓아 두었다가, 뒷날 필요해지시면 그때부터 꺼내 쓰실 수 있습니다."
            ),
            "일곱. 봉사시간과 자원봉사증" to listOf(
                "봉사한 것이 기록으로 남아야 오래갑니다. 두 갈래로 준비하고 있습니다.",
                "#협회 증서",
                "협회가 내는 봉사 확인서와 수료증입니다. 위조할 수 없는 디지털 증서(국제표준 오픈배지) 방식으로 내어, 어느 나라 어디에서든 진짜인지 확인할 수 있게 합니다.",
                "#국가 봉사시간(1365)",
                "협회가 1365 자원봉사포털에 활동처로 등록하면 봉사시간이 국가 실적으로 인정됩니다. 지금 등록을 준비하고 있습니다. 등록이 끝나면 이 자리에 방법을 적어 두겠습니다.",
                "정직하게 짚어 둡니다. 국가 봉사시간은 활동이 끝난 뒤 정해진 기간 안에 올리는 것이 원칙이라, 아주 오래된 실적은 소급이 어려울 수 있습니다.",
                "#시간은 어떻게 세는가",
                "길 그리기는 실제로 걸은 시간으로 셉니다. 잠깐 멈춤 동안은 세지 않습니다.",
                "도움 연결은 이어진 시간으로 셉니다.",
                "같은 자리를 여러 번 그리는 것은 인정합니다. 겹칠수록 길이 정확해지기 때문입니다. 다만 같은 사람이 같은 날 같은 자리를 되풀이한 것은 한 번으로 봅니다.",
                "실제로 걷지 않은 자취, 차로 지나간 자취는 인정하지 않습니다. 이 기준이 허술하면 지도도 믿을 수 없게 됩니다."
            ),
            "여덟. 관련 제도와 법" to listOf(
                "자원봉사활동 기본법 — 자원봉사는 대가를 바라지 않는 활동이며, 국가와 지방자치단체가 이를 지원하도록 정하고 있습니다. 봉사실적 인정과 자원봉사센터가 여기에서 나옵니다.",
                "장애인차별금지법과 장애인복지법 — 시각장애인의 이동과 정보 접근은 베푸는 것이 아니라 보장해야 하는 권리입니다.",
                "현장영상해설 조례 — 전국 마흔세 곳 지방자치단체가 조례를 두고 있습니다. 이 조례가 지자체 예산의 근거가 됩니다.",
                "위치정보의 보호 및 이용 등에 관한 법률 — 남의 위치를 모아 안내에 쓰는 일은 신고 대상이 될 수 있습니다. 비영리라고 예외가 아닙니다. 협회가 확인해 갖추고 있습니다.",
                "개인정보 보호법 — 협회는 주민등록번호와 상세주소, 건강정보를 받지 않습니다. 걸어 만든 자취에는 이름이 붙지 않고 번호만 붙습니다.",
                "시각장애인이 안내받은 이력은 서버에 남기지 않습니다. 어느 병원에 언제 갔는지가 남으면 안 되기 때문입니다."
            ),
            "아홉. 후원과 광고에 대한 우리 원칙" to listOf(
                "이 일이 자리를 잡으면 후원도 광고도 붙습니다. 그때 흔들리지 않도록 원칙을 먼저 못박아 둡니다.",
                "시각장애인이 길 안내를 받는 동안에는 어떤 광고도 소리로 내보내지 않습니다. 주의가 흩어지면 다치는 일입니다.",
                "구간 후원은 그 길을 고르실 때 한 번만 읽어 드립니다. 걷는 도중에는 읽지 않습니다.",
                "봉사자가 걸어 만든 자취를 후원이 붙은 길에 쓰거나 밖에 내줄 때는 미리 그 뜻을 여쭙고, 안 된다 하시면 쓰지 않습니다.",
                "기부금과 광고 수입은 따로 회계를 갈라 담고, 어디에 얼마를 썼는지 공개합니다.",
                "후원금은 길을 늘리는 데 씁니다. 어느 구간에 얼마가 쓰였는지 숫자로 보여 드립니다."
            ),
            "열. 함께하시려면" to listOf(
                "길 그리기는 지금 바로 하실 수 있습니다. 첫 화면 자원봉사자단에서 길 그리기 시작을 누르십시오.",
                "도움 연결로 받으시려면 대기 화면을 홈 화면에 얹어 두십시오. 화면을 닫아 두셔도 부름이 옵니다.",
                "이 안내를 끝까지 읽으신 것을 아래 단추로 남겨 두시면, 수료와 봉사증 자리가 열릴 때 그대로 이어집니다."
            )
        )
    }
}

// MARK: 길 부탁하기

class NnButakHwamyeon : Hwamyeon("길 부탁하기") {
    private var ara = false
    override fun chaeugi(t: GilnunActivity) {
        t.geul("그려 주었으면 하는 길을 남겨 두시면 길눈님이 걸어 드립니다.")
        t.danchu("새로 부탁하기") { t.yeolgi(NnButakSseugiHwamyeon()) }
        t.danchu("부탁해 둔 길 보기") { t.yeolgi(NnButakMokHwamyeon()) }
        nnAraDul(t, ara, listOf(
            "부탁하신 길은 자원봉사자의 오늘 걸을 길 맨 앞에 놓입니다. 사람이 실제로 기다리는 길이기 때문입니다.",
            "다 그려지면 알려 드립니다. 더 필요 없어지면 부탁을 내리셔도 됩니다."
        )) { ara = !ara }
    }
}

class NnButakSseugiHwamyeon : Hwamyeon("새로 부탁하기") {
    private var chulMal = ""
    private var mokMal = ""
    private var chul: NnGot? = null
    private var mok: NnGot? = null
    private var hubo: List<NnGot> = emptyList()
    private var huboChul = true
    private var ttae = ""
    private var mal = ""
    private var nugu: String? = null
    private var olineun = false
    private var dwiChojeom = 0          // 다시 그린 뒤 커서를 둘 칸 — 1 어디까지, 2 언제쯤
    private val m5 = NnMok5()

    override fun chaeugi(t: GilnunActivity) {
        if (nugu == null) nugu = NnGeoreumNanum.byeol(t)
        val ce = nnKan(t, "어디서 — 적고 엔터", chulMal) { chulMal = it }
        chatgiKan(ce) { chatgi(t, chulMal, true) }
        t.danchu("지금 내 자리를 출발지로") {
            val w = Wichi.jigeum
            if (w == null) { Sori.mal("지금 자리를 잡는 중입니다. 잠시 뒤에 다시 눌러 주십시오."); return@danchu }
            chul = NnGot("지금 내 자리", "", w.lat, w.lon)
            chulMal = "지금 내 자리"
            ce.setText(chulMal)
            Sori.mal("출발지를 지금 내 자리로 정했습니다. 이제 어디까지인지 적어 주십시오.")
        }
        val me = nnKan(t, "어디까지 — 적고 엔터", mokMal) { mokMal = it }
        chatgiKan(me) { chatgi(t, mokMal, false) }
        if (hubo.isNotEmpty()) {
            nnMeori(t, if (huboChul) "출발지 — 비슷한 곳" else "목적지 — 비슷한 곳")
            m5.geurigi(t, hubo) { j ->
                t.danchu(j.ireum + (if (j.juso.isEmpty()) "" else " — " + j.juso)) { goreugi(t, j) }
            }
        }
        val te = nnKan(t, "언제쯤 가셔야 합니까", ttae) { ttae = it }
        nnKan(t, "한마디", mal) { mal = it }
        nnKan(t, "별명 — 실명은 적지 마십시오. 비워 두셔도 됩니다", nugu ?: "") { nugu = it }
        val ob = t.danchu(if (olineun) "올리는 중입니다" else "부탁 올리기") { }
        ob.setOnClickListener { olligi(t, ob) }
        t.danchu("그만두기") { Sori.mal("그만두었습니다."); t.dwiro() }
        when (dwiChojeom) {
            1 -> t.chojeomOmgigi(me)
            2 -> t.chojeomOmgigi(te)
        }
        dwiChojeom = 0
    }

    private fun chatgiKan(e: EditText, f: () -> Unit) {
        e.imeOptions = EditorInfo.IME_ACTION_SEARCH
        e.setSingleLine(true)
        e.setOnEditorActionListener { _, id, ev ->
            if (id == EditorInfo.IME_ACTION_SEARCH || id == EditorInfo.IME_ACTION_DONE ||
                (ev != null && ev.keyCode == android.view.KeyEvent.KEYCODE_ENTER && ev.action == android.view.KeyEvent.ACTION_DOWN)) {
                f(); true
            } else false
        }
    }

    private fun chatgi(t: GilnunActivity, q0: String, c: Boolean) {
        val q = q0.trim()
        if (q.isEmpty()) { Sori.mal("이름을 적어 주십시오."); return }
        Sori.mal("$q${NnTossi.eul(q)} 찾고 있습니다.", MalGeup.JEONGBO)
        NnGilButak.gotChatgi(q) { r ->
            if (r == null) { Sori.mal("찾는 중에 막혔습니다."); return@gotChatgi }
            if (r.isEmpty()) { Sori.mal("그런 이름의 곳을 찾지 못했습니다. 달리 적어 보십시오."); return@gotChatgi }
            huboChul = c
            hubo = r
            m5.saeMok()
            Sori.mal("비슷한 곳 ${r.size}곳입니다. 고르실 곳을 두드리십시오.", MalGeup.JEONGBO)
            if (nnBoinda(t, this)) t.dasiGeurigi()
        }
    }

    private fun goreugi(t: GilnunActivity, j: NnGot) {
        if (huboChul) {
            chul = j
            chulMal = j.ireum
            Sori.mal("출발지를 ${j.ireum}${NnTossi.ro(j.ireum)} 정했습니다. 이제 어디까지인지 적어 주십시오.")
            dwiChojeom = 1
        } else {
            mok = j
            mokMal = j.ireum
            Sori.mal("목적지를 ${j.ireum}${NnTossi.ro(j.ireum)} 정했습니다. 언제쯤 가셔야 하는지 적어 주십시오.")
            dwiChojeom = 2
        }
        hubo = emptyList()
        t.dasiGeurigi()
    }

    private fun olligi(t: GilnunActivity, ob: TextView) {
        if (olineun) return
        val s = chulMal.trim()
        val m = mokMal.trim()
        if (s.isEmpty()) { Sori.mal("어디서 출발하시는지 적어 주십시오."); return }
        if (m.isEmpty()) { Sori.mal("어디까지 가시는지 적어 주십시오."); return }
        olineun = true
        ob.text = "올리는 중입니다"
        val c = if (chul?.ireum == s) chul else null
        val mk = if (mok?.ireum == m) mok else null
        NnGilButak.olligi(c, s, mk, m, ttae.trim(), mal.trim(), (nugu ?: "").trim()) { e ->
            olineun = false
            if (e != null) {
                if (nnBoinda(t, this)) ob.text = "부탁 올리기"
                Sori.mal(e)
                return@olligi
            }
            Sori.mal("부탁을 올렸습니다. 자원봉사자의 오늘 걸을 길 맨 앞에 놓입니다. 다 그려지면 알려 드리겠습니다.")
            if (nnBoinda(t, this)) t.dwiro()
        }
    }
}

class NnButakMokHwamyeon : Hwamyeon("부탁해 둔 길") {
    private var mok: List<NnButak>? = null
    private var mot = false
    private var batneun = false
    private var gaengsinHal = true    // 처음과, 부탁을 보고 돌아왔을 때 다시 받음(아이폰 onAppear)
    private val m5 = NnMok5()

    override fun chaeugi(t: GilnunActivity) {
        val l = mok
        if (l != null && l.isNotEmpty()) {
            m5.geurigi(t, l) { b ->
                t.danchu(b.julMal) { gaengsinHal = true; t.yeolgi(NnButakSangseHwamyeon(b)) }
            }
        } else if (l != null) {
            t.geul("아직 부탁해 둔 길이 없습니다. 첫 부탁을 남겨 주시면 길눈님이 걸어 드립니다.", true)
        } else if (mot) {
            t.danchu("불러오지 못했습니다 — 다시 불러오기") { bulreogi(t) }
        } else {
            t.geul("부탁해 둔 길을 불러오고 있습니다.", true)
        }
        if (gaengsinHal && !batneun) { gaengsinHal = false; bulreogi(t) }
    }

    private fun bulreogi(t: GilnunActivity) {
        mot = false
        batneun = true
        NnGilButak.mok { r ->
            batneun = false
            if (r != null) {
                val ap = mok
                mok = r
                if (ap == null || ap.map { it.id } != r.map { it.id }) m5.saeMok()
            } else {
                mot = true
                Sori.mal("불러오지 못했습니다.")
            }
            if (nnBoinda(t, this)) t.dasiGeurigi()
        }
    }
}

class NnButakSangseHwamyeon(private val b: NnButak) : Hwamyeon("부탁해 둔 길") {
    private var datMal = ""
    private var datNugu: String? = null
    private var datPyeol = false
    private var cheotBoim = true

    override fun chaeugi(t: GilnunActivity) {
        if (datNugu == null) datNugu = NnGeoreumNanum.byeol(t)
        val bon = t.geul(b.jaseMal, true)
        if (cheotBoim) { cheotBoim = false; t.chojeomJul(bon) }   // 매번 글로 커서(아이폰 2.12.1)
        if (b.doen && b.gil.isNotEmpty()) {
            // 아이폰 2.10.0 그려진 점지도를 따라 걷기
            t.danchu("그려졌습니다 — 이 길로 걷기, 점지도 따라 걷기") {
                Girok.namgi("butak_geotgi")
                JeomEngine.bulleoGeotgi(t, b.gil, false)
                t.cheotHwamyeonEuro(JeomGeotgiHwamyeon())
            }
        }
        val wa = wiseongAnnae
        if (b.doen && b.mlat != 0.0 && b.mlon != 0.0 && wa != null) {
            t.danchu("그곳까지 안내 — 위성으로 방향 따라") { wa(t, b.min, b.mlat, b.mlon) }
        }
        for (d in b.daetgeul) {
            val v = t.danchu("댓글 : ${d.mal}" + (if (d.nugu.isEmpty()) "" else " — ${d.nugu}")) { Sori.mal(d.mal) }
            nnDongjak(t, v, listOf("이 댓글 지우기" to {
                nnMureum(t, "이 댓글을 지울까요?", "지우기") { hagi(t, "daetjiugi", listOf("did" to d.id), "댓글을 지웠습니다.") }
            }))
        }
        t.pyeolchigi("댓글 달기", datPyeol) { datPyeol = !datPyeol }
        if (datPyeol) {
            nnKan(t, "한마디", datMal) { datMal = it }
            nnKan(t, "별명 — 실명은 적지 마십시오. 안 적으셔도 됩니다", datNugu ?: "") { datNugu = it }
            t.danchu("댓글 올리기") {
                val m = datMal.trim()
                if (m.isEmpty()) { Sori.mal("한마디를 적어 주십시오."); return@danchu }
                hagi(t, "daet", listOf("mal" to m, "nugu" to (datNugu ?: "").trim()), "댓글을 달았습니다.")
            }
        }
        if (!b.doen) t.danchu("다 그렸습니다 표시하기") { hagi(t, "doen", emptyList(), "다 그려진 것으로 표시했습니다.") }
        t.danchu("이 부탁 내리기") { nnMureum(t, "이 부탁을 내릴까요?", "내리기") { hagi(t, "gam", emptyList(), "부탁을 내렸습니다.") } }
    }

    private fun hagi(t: GilnunActivity, a: String, deo: List<Pair<String, String>>, mal: String) {
        NnGilButak.hagi(a, b.id, deo) { ok ->
            Sori.mal(if (ok) mal else "하지 못했습니다. 통신을 확인해 주십시오.")
            if (ok && nnBoinda(t, this)) t.dwiro()
        }
    }

    companion object {
        /** 위성으로 그곳까지 걷는 안내(아이폰 gotEuroGagi) — 걸어갈까요를 옮기는 묶음이 채움. 비어 있으면 단추를 내지 않음 */
        var wiseongAnnae: ((GilnunActivity, String, Double, Double) -> Unit)? = null
    }
}
