// 안드로이드 길눈 — 음악·방송 탭 화면들(b5_bangsong, 아이폰 BangsongView.swift 2.8.0~2.34.0과 같은 차례, 같은 말)
// 첫 화면은 단추 다섯(길 위의 음악, 라디오 듣기, TV 보기, 지금 세상 이야기, 동영상 틀기). 무엇이 나오고 있으면 맨 위에 "그만 듣기" 한 줄.
// 소리는 Bangsong 엔진이 쥐고 있어 화면을 옮겨도 끊기지 않습니다.
// 화면 원칙: 엔진 형편이 바뀌어도 보이는 글이 달라질 때만 다시 그림(톡백이 쉬지 않고 떠들지 않게). 누르신 단추에 커서를 되돌림.
//   목록은 다섯씩, 아래에 더 보기와 이전 보기. 줄에 번호 없음. 넘기면 커서를 첫 결과 줄로
//   TV 영상은 버리지 않음 — 영상 끄기를 켜실 때만 화면에서 뺌(대표님 원칙)
// 기사 화면을 떠나시면(목록으로·뒤로·다른 탭) 기사 읽기를 멈춤. 폰을 잠그시는 것은 떠나는 것이 아님(아이폰 2.12.1)
package kr.or.ada.app.gilnun

import android.text.InputType
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.annotation.OptIn
import androidx.core.widget.doAfterTextChanged
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.lang.ref.WeakReference

/** 음악·방송 화면의 바탕 — 엔진이 바뀌면 보이는 모양이 달라졌을 때만 다시 그리고, 누르신 단추에 커서를 되돌림 */
abstract class BangsongBada(jemok: String) : Hwamyeon(jemok) {
    private var geurinMoyang = ""
    private var nulleunKi: String? = null
    private val kiJul = HashMap<String, View>()

    /** 지금 엔진 형편으로 그릴 모양(글자들) — 이것이 달라질 때만 다시 그림 */
    protected abstract fun moyang(): String
    protected abstract fun geurigi(t: GilnunActivity)

    final override fun chaeugi(t: GilnunActivity) {
        kiJul.clear()
        geurinMoyang = moyang()
        Bangsong.byeonhwa = {
            if (t.wiHwamyeon === this && !t.isFinishing && moyang() != geurinMoyang) {
                // 톡백 커서가 머물던 줄을 기억했다가 다시 그린 뒤 그 줄로(곡이 저절로 바뀌어도 커서가 튀지 않게)
                if (nulleunKi == null) {
                    for ((k, v) in kiJul) {
                        val f = try { v.createAccessibilityNodeInfo().isAccessibilityFocused } catch (e: Exception) { false }
                        if (f) { nulleunKi = k; break }
                    }
                }
                t.dasiGeurigi()
            }
        }
        geurigi(t)
    }

    override fun boilttae(t: GilnunActivity) {
        val k = nulleunKi ?: return
        nulleunKi = null
        kiJul[k]?.let { t.chojeomOmgigi(it) }
    }

    /** 단추 — ki 는 글자가 바뀌어도 같은 자리(누른 뒤 다시 그려도 커서가 그 단추로 돌아옴) */
    protected fun danchu(t: GilnunActivity, ki: String, geul: String, f: () -> Unit): Button {
        val b = t.danchu(geul) { nulleunKi = ki; f() }
        kiJul[ki] = b
        return b
    }

    /** 다음 그림에서 커서를 둘 줄 */
    protected fun chojeomDul(ki: String) { nulleunKi = ki }

    /** 단추가 아닌 줄(제목 등)도 커서를 둘 자리로 */
    protected fun kiDeungrok(ki: String, v: View) { kiJul[ki] = v }

    /** 목록 다섯씩 — 줄 번호 없음, 아래에 더 보기와 이전 보기. 넘기면 커서를 첫 결과 줄로 */
    protected fun <T> mokrok5(t: GilnunActivity, ki: String, l: List<T>, sijak0: Int, bakkum: (Int) -> Unit, julMal: (T) -> String, nureum: (Int, T) -> Unit): View? {
        if (l.isEmpty()) return null
        val sijak = if (sijak0 >= l.size || sijak0 < 0) 0 else sijak0
        val kkeut = minOf(sijak + 5, l.size)
        var cheot: View? = null
        for (n in sijak until kkeut) {
            val x = l[n]
            val b = danchu(t, "${ki}_$n", julMal(x)) { nureum(n, x) }
            if (cheot == null) cheot = b
        }
        if (kkeut < l.size) t.danchu("더 보기") { bakkum(sijak + 5); nulleunKi = "${ki}_${sijak + 5}"; t.dasiGeurigi() }
        if (sijak > 0) t.danchu("이전 보기") { val s = maxOf(0, sijak - 5); bakkum(s); nulleunKi = "${ki}_$s"; t.dasiGeurigi() }
        return cheot
    }

    /** 찾기 칸 — 자판의 찾기(엔터)로도 함 */
    protected fun chatgiKan(t: GilnunActivity, ansae: String, geul: String, bakkum: (String) -> Unit, chatgi: () -> Unit): EditText {
        val e = t.ipryeok(ansae, false)
        e.setText(geul)
        e.imeOptions = EditorInfo.IME_ACTION_SEARCH
        e.doAfterTextChanged { bakkum(it?.toString() ?: "") }
        e.setOnEditorActionListener { _, id, ev ->
            val enter = id == EditorInfo.IME_ACTION_SEARCH || id == EditorInfo.IME_ACTION_GO || id == EditorInfo.IME_ACTION_DONE ||
                (ev != null && ev.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN)
            if (enter) chatgi()
            enter
        }
        return e
    }
}

/** 고르기 — 갈래·테마처럼 하나를 고르면 앞 화면으로 */
private class BsGoreugi(jemok: String, private val l: List<String>, private val jigeum: String, private val gorum: (String) -> Unit) : BangsongBada(jemok) {
    private var sijak = 0
    private var cheoum = true
    override fun moyang() = ""
    override fun geurigi(t: GilnunActivity) {
        val i = l.indexOf(jigeum)
        if (cheoum && i >= 0) sijak = (i / 5) * 5   // 처음 열 때 지금 고른 것이 있는 다섯으로
        cheoum = false
        val cheot = mokrok5(t, "g", l, sijak, { sijak = it }, { if (it == jigeum) "$it, 지금 고른 것" else it }) { _, x ->
            gorum(x)
            t.dwiro()
        }
        if (cheot != null) t.chojeomJul(cheot) else t.geul("고를 것이 없습니다.", true)
    }
}

// MARK: 첫 화면

class BangsongCheot : BangsongBada("음악·방송") {
    override fun moyang() = "${Bangsong.itda}|${Bangsong.jemok}|${Bangsong.jeonhwaDwi}|${Bangsong.meomchum}"
    override fun geurigi(t: GilnunActivity) {
        val b = Bangsong
        // 2.30.0 통화로 멈춘 방송 — 맨 위 한 줄, 누르면 이어 들음(이사장님 승인 2026-10-09)
        if (b.itda && b.jeonhwaDwi) {
            val ie = danchu(t, "ieoDeutgi", "방송 이어 듣기 — ${b.jemok.ifEmpty { b.jong.ireum }}") { b.ieoDeutgi() }
            t.chojeomJul(ie)
        }
        if (b.itda) {
            // 지금 나오는 것 — 안내와 단추를 한 줄에
            val g = danchu(t, "geuman", "지금 나오는 것 — ${b.jemok.ifEmpty { "연결 중" }}. 그만 듣기") { b.geuman() }
            t.chojeomJul(g)
            danchu(t, "eumak", "길 위의 음악") { t.yeolgi(EumakHwamyeon()) }
        } else {
            val e = danchu(t, "eumak", "길 위의 음악") { t.yeolgi(EumakHwamyeon()) }
            t.chojeomJul(e)
        }
        danchu(t, "radio", "라디오 듣기") { t.yeolgi(ChaeneolHwamyeon("radio")) }
        danchu(t, "tv", "TV 보기") { t.yeolgi(ChaeneolHwamyeon("tv")) }
        danchu(t, "sesang", "지금 세상 이야기") { t.yeolgi(SesangHwamyeon()) }
        danchu(t, "dongyeong", "동영상 틀기") { t.yeolgi(DongyeongHwamyeon()) }   // 2.34.0
        // 2.30.0 통화 뒤 저절로 이어 듣기(처음 꺼짐)
        danchu(t, "tonghwaDwi", "통화 뒤 방송 저절로 이어 듣기 — 지금 " + (if (BangsongSeol.tonghwaDwiIeum) "켜짐, 누르면 꺼짐" else "꺼짐, 누르면 켜짐")) {
            BangsongSeol.tonghwaDwiIeum = !BangsongSeol.tonghwaDwiIeum
            Sori.mal(if (BangsongSeol.tonghwaDwiIeum) "통화가 끝나면 방송을 저절로 이어 드립니다." else "통화가 끝나도 방송을 저절로 틀지 않습니다. 맨 위 방송 이어 듣기를 누르시면 이어집니다.")
            t.dasiGeurigi()
        }
    }
}

// MARK: 길 위의 음악

class EumakHwamyeon : BangsongBada("길 위의 음악") {
    private var pw = ""
    private var galrae = "가요"
    private var tema = ""
    private var temaDeul: List<String> = emptyList()
    private var gojangQ = ""
    private var deureoganeun = false
    private var junbiHam = false
    private var mokPyeol = false
    private var mokSijak = 0
    private var nugunaPyeol = false
    private var gibunPyeol = false

    override fun moyang(): String {
        val b = Bangsong
        return listOf(b.jong.ireum, b.jemok, b.nugunaPyosi, b.meomchum, b.eumakDeureom, b.galraeDeul.size, b.jadoKyeojim, b.jadoJul,
            b.cheoumIra, b.gokMok.size, b.gokMok.firstOrNull()?.f ?: "", temaDeul.size).joinToString("|")
    }

    override fun geurigi(t: GilnunActivity) {
        val b = Bangsong
        if (!junbiHam) {
            junbiHam = true
            b.eumakJunbi {
                if (b.eumakDeureom) b.temaDeul(galrae) { l -> temaDeul = l; Bangsong.byeonhwa?.invoke() }
            }
        }
        if (b.jong == BangsongJong.EUMAK || b.jong == BangsongJong.NUGUNA) {
            t.geul("지금 곡 — ${b.jemok}", true)
            if (b.jong == BangsongJong.NUGUNA && b.nugunaPyosi.isNotEmpty()) t.geul(b.nugunaPyosi)
            danchu(t, "daeum", "다음 곡") { b.daeum() }
            if (b.jong == BangsongJong.EUMAK) danchu(t, "ijeon", "이전 곡") { b.ijeon() }
            danchu(t, "meomchum", if (b.meomchum) "이어서 틀기" else "멈춤") { b.meomchumTogeul() }
            danchu(t, "geuman", "그만 듣기") { b.geuman() }
        }
        if (b.eumakDeureom) {
            if (b.galraeDeul.isNotEmpty() && !b.galraeDeul.contains(galrae)) galrae = b.galraeDeul.first()
            danchu(t, "galrae", "갈래 — $galrae. 누르면 고르기") {
                t.yeolgi(BsGoreugi("갈래 고르기", b.galraeDeul, galrae) { g ->
                    if (g != galrae) {
                        galrae = g
                        tema = ""
                        temaDeul = emptyList()
                        b.temaDeul(g) { l -> temaDeul = l; Bangsong.byeonhwa?.invoke() }
                    }
                })
            }
            danchu(t, "tema", "테마 — ${tema.ifEmpty { "이 갈래 통째로" }}. 누르면 고르기") {
                t.yeolgi(BsGoreugi("테마 고르기", listOf("이 갈래 통째로") + temaDeul, tema.ifEmpty { "이 갈래 통째로" }) { x ->
                    tema = if (x == "이 갈래 통째로") "" else x
                })
            }
            danchu(t, "galraeTeulgi", "$galrae${if (tema.isEmpty()) "" else " $tema"} 이어서 틀기") { b.galraeTeulgi(galrae, tema) }
        }
        // 2.19.0 고장 노래는 열쇠 없이 모든 분께 — 모든 시각장애인에게 드리는 선물
        danchu(t, "jado", if (b.jadoKyeojim) "지나는 고장 노래 저절로 틀기 그만" else "지나는 고장 노래 저절로 틀기") {
            if (b.jadoKyeojim) b.jadoKkeugi() else b.jadoKyeogi()
        }
        if (b.jadoJul.isNotEmpty()) t.geul(b.jadoJul)
        // 2.12.7 차에 타면 저절로
        danchu(t, "gojangNorae", "차에 타면 지나는 고장 노래 저절로 틀기 — 지금 " + (if (BangsongSeol.gojangNorae) "켜짐, 누르면 꺼짐" else "꺼짐, 누르면 켜짐")) {
            BangsongSeol.gojangNorae = !BangsongSeol.gojangNorae
            Sori.mal(if (BangsongSeol.gojangNorae) "차에 타면 지나는 고장 노래를 저절로 틀어 드립니다." else "차에 타도 고장 노래를 저절로 틀지 않습니다.")
            t.dasiGeurigi()
        }
        chatgiKan(t, "고장 이름으로 찾기 — 시·군 이름", gojangQ, { gojangQ = it }) { gojangChatgi() }
        danchu(t, "gojangChatgi", "그 고장 노래 틀기") { gojangChatgi() }
        if (b.gokMok.isNotEmpty()) {
            danchu(t, "mokPyeol", if (mokPyeol) "지금 목록 보기 접기" else "지금 목록 보기 펼치기") { mokPyeol = !mokPyeol; t.dasiGeurigi() }
            if (mokPyeol) {
                mokrok5(t, "gok", b.gokMok, mokSijak, { mokSijak = it }, { it.ireum }) { i, _ -> b.gokTeulgi(i) }
            }
        }
        if (!b.eumakDeureom) {
            // 음악 전체(갈래·테마, 가수와 곡 찾기)는 열쇠를 넣으신 분만
            t.geul(if (b.cheoumIra) "처음이십니다. 나스 음악에 쓰실 열쇠를 여섯 자 넘게 정해 주십시오."
                else "지나는 고장 노래는 열쇠 없이 누구나 들으십니다. 갈래 틀기와 가수·곡 찾기 같은 나스 음악 전체는 열쇠를 넣으신 분만 쓰실 수 있습니다. 한 번 넣으시면 폰에 담아 두어 다시 묻지 않습니다.")
            val e = t.ipryeok("나스 음악 열쇠", false)
            e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            e.setText(pw)
            e.imeOptions = EditorInfo.IME_ACTION_GO
            e.doAfterTextChanged { pw = it?.toString() ?: "" }
            e.setOnEditorActionListener { _, id, ev ->
                val enter = id == EditorInfo.IME_ACTION_GO || id == EditorInfo.IME_ACTION_DONE ||
                    (ev != null && ev.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN)
                if (enter) deureogagi(t)
                enter
            }
            danchu(t, "deureogagi", if (deureoganeun) "들어가는 중입니다" else "들어가기") { deureogagi(t) }
        }
        danchu(t, "nuguna", if (nugunaPyeol) "누구나 음악 접기" else "누구나 음악 펼치기 — 공유마당 곡") { nugunaPyeol = !nugunaPyeol; t.dasiGeurigi() }
        if (nugunaPyeol) {
            danchu(t, "jan", "잔잔한 음악 틀기") { b.nugunaTeulgi("jan", "잔잔한 음악을 틉니다.") }
            danchu(t, "bal", "밝은 음악 틀기") { b.nugunaTeulgi("bal", "밝은 음악을 틉니다.") }
            danchu(t, "seokkeo", "모두 섞어 틀기") { b.nugunaTeulgi("", "누구나 음악을 섞어 틉니다.") }
        }
        // 2.13.0 기분과 날씨에 맞춰 틀기(열쇠가 없으면 누구나 음악으로)
        danchu(t, "gibun", if (gibunPyeol) "기분과 날씨로 틀기 접기" else "기분과 날씨로 틀기 펼치기") { gibunPyeol = !gibunPyeol; t.dasiGeurigi() }
        if (gibunPyeol) {
            for (g in BangsongGibun.values()) {
                danchu(t, "gibun_" + g.name, g.danchu) {
                    b.gibunTeulgi(g) { m -> if (m.isNotEmpty()) Sori.mal(m, MalGeup.JEONGBO) }
                }
            }
        }
    }

    private fun deureogagi(t: GilnunActivity) {
        val p = pw.trim()
        if (p.isEmpty() || deureoganeun) {
            if (p.isEmpty()) Sori.mal("열쇠를 넣어 주십시오.")
            return
        }
        deureoganeun = true
        t.dasiGeurigi()
        Bangsong.yeolsoeNeoki(p) { e ->
            pw = ""
            if (e != null) {
                deureoganeun = false
                Sori.mal(e)
                if (t.wiHwamyeon === this) t.dasiGeurigi()
            } else {
                val b = Bangsong
                if (b.galraeDeul.isNotEmpty() && !b.galraeDeul.contains(galrae)) galrae = b.galraeDeul.first()
                b.temaDeul(galrae) { l ->
                    deureoganeun = false
                    temaDeul = l
                    Sori.mal("들어오셨습니다. 갈래와 테마를 고르고 이어서 틀기를 누르십시오.")
                    chojeomDul("galrae")
                    if (t.wiHwamyeon === this) t.dasiGeurigi()
                }
            }
        }
    }

    private fun gojangChatgi() {
        val q = gojangQ.trim()
        if (q.isEmpty()) { Sori.mal("고장 이름을 적어 주십시오."); return }
        Bangsong.gojangChatgi(q)
    }
}

// MARK: 라디오·TV

class ChaeneolHwamyeon(private val kind: String) : BangsongBada(if (kind == "tv") "TV 보기" else "라디오 듣기") {
    private var mot = false
    private var batneun = false
    private var sijak = 0

    private val nawa: Boolean
        get() = (kind == "tv" && Bangsong.jong == BangsongJong.TV) || (kind == "radio" && Bangsong.jong == BangsongJong.RADIO)

    override fun moyang(): String {
        val b = Bangsong
        return listOf(nawa, b.jemok, b.sangtaeMal, b.meomchum, b.yeongsangKkeum, b.chaeneolNote, b.kindChaeneol(kind).size, mot, batneun).joinToString("|")
    }

    override fun geurigi(t: GilnunActivity) {
        val b = Bangsong
        if (b.chaeneolDeul.isEmpty() && !batneun && !mot) batgi()
        if (nawa) {
            t.geul("지금 — ${b.jemok}" + (if (b.sangtaeMal.isEmpty()) "" else ". " + b.sangtaeMal), true)
            if (kind == "tv" && !b.yeongsangKkeum) tvHwamyeon(t)
            danchu(t, "meomchum", if (b.meomchum) "다시 틀기" else "멈춤") { b.meomchumTogeul() }
            danchu(t, "geuman", if (kind == "tv") "TV 그만 보기" else "라디오 그만 듣기") { b.geuman() }
            if (kind == "tv" && b.chaeneolNote.isNotEmpty()) t.geul(b.chaeneolNote)
        }
        if (kind == "tv") {
            danchu(t, "yeongsang", "영상 끄기 — 소리만 듣고 데이터 아끼기. 지금 " + (if (b.yeongsangKkeum) "켜짐(소리만), 누르면 영상 나옴" else "꺼짐(영상 나옴), 누르면 소리만")) {
                b.yeongsangKkeum = !b.yeongsangKkeum
                Sori.mal(if (b.yeongsangKkeum) "영상을 끄고 소리만 듣습니다. 데이터를 아낍니다." else "영상을 다시 켭니다.", MalGeup.JEONGBO)
                t.dasiGeurigi()
            }
        }
        val ls = b.kindChaeneol(kind)
        if (ls.isEmpty()) {
            t.geul(if (mot) "채널 목록을 받지 못했습니다." else "채널 목록을 받는 중입니다.", true)
            if (mot) danchu(t, "dasiBatgi", "다시 받기") { batgi(); t.dasiGeurigi() }
        } else {
            mokrok5(t, "ch", ls, sijak, { sijak = it }, { it.julMal }) { _, c -> b.chaeneolTeulgi(c) }
        }
    }

    /** TV 화면 — 화면에서 빠지면 재생기를 떼어 소리만 이어지게 */
    @OptIn(UnstableApi::class)
    private fun tvHwamyeon(t: GilnunActivity) {
        val p = Bangsong.exo ?: return
        val jari = t.geul("TV 화면")
        val bumo = jari.parent as? ViewGroup ?: return
        val idx = bumo.indexOfChild(jari)
        bumo.removeView(jari)
        val pv = PlayerView(t)
        pv.useController = false
        pv.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        pv.setShutterBackgroundColor(android.graphics.Color.BLACK)
        pv.setBackgroundColor(android.graphics.Color.BLACK)
        pv.contentDescription = "TV 화면"
        pv.isFocusable = true
        pv.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        pv.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { pv.player = Bangsong.exo }
            override fun onViewDetachedFromWindow(v: View) { pv.player = null }
        })
        val nolbi = t.resources.displayMetrics.widthPixels - t.dp(32)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, nolbi * 9 / 16)
        lp.topMargin = t.dp(10)
        bumo.addView(pv, idx, lp)
        pv.player = p
    }

    private fun batgi() {
        mot = false
        batneun = true
        Bangsong.chaeneolBatgi {
            batneun = false
            mot = Bangsong.chaeneolDeul.isEmpty()
            Bangsong.byeonhwa?.invoke()
        }
    }
}

// MARK: 지금 세상 이야기

class SesangHwamyeon : BangsongBada("지금 세상 이야기") {
    private var q = ""
    private var batneun = false
    private var sijak = 0
    private var moksoriPyeol = false

    override fun moyang() = "${Bangsong.jong == BangsongJong.GISA}|${Bangsong.jemok}"

    override fun geurigi(t: GilnunActivity) {
        val b = Bangsong
        if (b.jong == BangsongJong.GISA) {
            danchu(t, "ilkneun", "읽고 있는 기사로 — ${b.jemok}") { t.yeolgi(GisaHwamyeon()) }
        }
        chatgiKan(t, "기사 찾기 — 찾을 말", q, { q = it }) { chatgi(t) }
        danchu(t, "chatgi", "찾기") { chatgi(t) }
        mokrok5(t, "garae", b.garae, sijak, { sijak = it }, { it.second }) { _, g -> yeolgi(t, g.first, g.second, false) }
        danchu(t, "sae", "소식 새로 받아 오기") { yeolgi(t, "all", "두루 소식", true) }
        danchu(t, "moksori", if (moksoriPyeol) "목소리 고르기 접기" else "목소리 고르기 펼치기") { moksoriPyeol = !moksoriPyeol; t.dasiGeurigi() }
        if (moksoriPyeol) {
            danchu(t, "daeumMoksori", "읽어 줄 목소리 — ${b.moksoriIreum()}. 누르면 다음 목소리") {
                b.daeumMoksori()
                t.dasiGeurigi()
            }
            danchu(t, "bbareugi", "말하는 빠르기 — ${Seoljeong.bbareugiIreum[BangsongSeol.gisaBbareugiDan]}. 누르면 다음 빠르기") {
                BangsongSeol.gisaBbareugiDan = (BangsongSeol.gisaBbareugiDan + 1) % Seoljeong.bbareugiIreum.size
                b.moksoriDeureoboki()
                t.dasiGeurigi()
            }
            danchu(t, "deureoboki", "이 목소리로 들어 보기") { b.moksoriDeureoboki() }
        }
    }

    private fun yeolgi(t: GilnunActivity, g: String, nm: String, sae: Boolean) {
        if (batneun) return
        batneun = true
        Sori.mal("$nm 받아 오는 중입니다.", MalGeup.JEONGBO)
        Bangsong.gisaBatgi(g, sae) { ls ->
            batneun = false
            if (ls == null) { Sori.mal("받아 오지 못했습니다. 잠시 뒤 다시 해 주십시오."); return@gisaBatgi }
            if (ls.isEmpty()) { Sori.mal("지금 ${nm}에는 새 기사가 없습니다."); return@gisaBatgi }
            if (t.wiHwamyeon === this) t.yeolgi(GisaMokHwamyeon(nm))
            Sori.mal("$nm, 최신 순으로 ${ls.size}건입니다. 기사를 고르시면 원문을 읽어 드립니다.", MalGeup.JEONGBO)
        }
    }

    private fun chatgi(t: GilnunActivity) {
        val w = q.trim()
        if (w.isEmpty()) { Sori.mal("찾을 말을 적어 주십시오."); return }
        if (batneun) return
        batneun = true
        Sori.mal("$w${MalHagi.eul(w)} 찾는 중입니다.", MalGeup.JEONGBO)
        Bangsong.gisaChatgi(w) { ls ->
            batneun = false
            if (ls == null) { Sori.mal("찾지 못했습니다. 통신을 확인해 주십시오."); return@gisaChatgi }
            if (ls.isEmpty()) { Sori.mal("$w${bsI(w)} 든 기사가 없습니다."); return@gisaChatgi }
            if (t.wiHwamyeon === this) t.yeolgi(GisaMokHwamyeon("찾은 기사 — $w"))
            Sori.mal("$w, ${ls.size}건을 찾았습니다.", MalGeup.JEONGBO)
        }
    }
}

class GisaMokHwamyeon(jemok: String) : BangsongBada(jemok) {
    private var sijak = 0
    override fun moyang() = "${Bangsong.gisaMok.size}|${Bangsong.gisaMok.firstOrNull()?.juso ?: ""}"
    override fun geurigi(t: GilnunActivity) {
        val b = Bangsong
        val cheot = mokrok5(t, "gisa", b.gisaMok, sijak, { sijak = it }, { it.julMal }) { i, _ ->
            b.gisaYeolgi(i)
            t.yeolgi(GisaHwamyeon())
        }
        if (cheot != null) t.chojeomJul(cheot) else t.geul("기사가 없습니다.", true)
    }
}

class GisaHwamyeon : BangsongBada("기사") {
    override fun moyang(): String {
        val b = Bangsong
        return listOf(b.gisaI, b.gisaMok.size, b.gisaMeori, b.gisaBon.size, b.jong.ireum, b.gisaIlkneun, b.meomchum, Seoljeong.malKyeojim).joinToString("|")
    }

    override fun geurigi(t: GilnunActivity) {
        val b = Bangsong
        // 2.12.1 기사 화면이 떠 있는가 — 떠나시면 엔진이 읽기를 멈춤
        val a = WeakReference(t)
        Bangsong.gisaHwamyeonBoim = {
            val x = a.get()
            x != null && !x.isFinishing && x.wiHwamyeon === this
        }
        if (b.gisaI >= 0 && b.gisaI < b.gisaMok.size) {
            val j = t.geul(b.gisaMok[b.gisaI].jemok, true)
            j.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f)
            if (android.os.Build.VERSION.SDK_INT >= 28) j.isAccessibilityHeading = true
            t.chojeomJul(j)
            kiDeungrok("jemok", j)
            t.geul(b.gisaMeori)
            if (Seoljeong.malKyeojim && b.jong == BangsongJong.GISA && b.gisaIlkneun) {
                danchu(t, "meomchum", if (b.meomchum) "이어 읽기" else "읽기 멈춤") { b.meomchumTogeul() }
            }
            danchu(t, "daeum", "다음 기사") {
                if (b.gisaI + 1 < b.gisaMok.size) chojeomDul("jemok")   // 새 기사 제목으로(아이폰 onChange gisaI)
                b.gisaYeolgi(b.gisaI + 1)
            }
            if (Seoljeong.malKyeojim) {
                danchu(t, "cheoeum", "처음부터 다시 읽기") {
                    if (b.jong == BangsongJong.GISA) b.gisaCheoeumButeo() else b.gisaYeolgi(b.gisaI)
                }
            }
            danchu(t, "mok", "기사 목록으로") { t.dwiro() }
            for (p in b.gisaBon) t.geul(p, true)
        } else {
            t.geul("읽고 있는 기사가 없습니다.", true)
        }
    }
}
