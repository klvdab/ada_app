// 안드로이드 길눈 — 길 찾기 탭 새 첫 화면과 목적지 화면들(묶음 b1, 아이폰 GilChatgi.swift 와 같은 차례·같은 말)
// 화면 원칙(아이폰과 같음): 여정이 있으면 지금 차례 한 줄에 "지금 무슨 차례인지"와 그 차례의 단추를 한 자리에.
//   2.7.0 통합 결정: 말로 하기는 2.4.0에 승인된 대로 맨 위 첫 줄(읽지 않은 긴급 공지가 있을 때만 그 한 줄이 위에), 긴급통화서비스는 그 바로 아래(2.6.0 자리)
//   그다음 긴급성 순 — ③ 하던 일 멈추기(무엇이든 진행 중일 때만) ④ 지금 차례(점지도로 걸을까요 / 따라 걷는 중 / 여정 / 목적지 찾기 칸·즐겨찾기·지금 내 자리)
//   ⑤ 그 밖에 펼치기(음향신호기, 가까운 점지도, 되짚어 나가기, QR 찾기, 말로 그린 길, 음성유도기와 승강기, 문 찾기(관리자), 기초 시험)
// 맨 위에는 읽지 않은 긴급 공지(GongjiEngine, 있을 때만). 말로 그린 길을 걷는 중·되짚어 나가기 중이면 지금 차례 위에 그 한 줄(다른 묶음의 화면으로 감)
// 안내는 화면이 아니라 안내 엔진(AnnaeEngine)이 맡으므로, 화면을 떠나도, 음악을 틀어도, 폰이 잠겨도 이어집니다.
// 화면은 차례가 바뀔 때만 다시 그림(남은 거리처럼 자주 바뀌는 숫자는 띄우지 않음 — 톡백이 쉬지 않고 떠들지 않게). 차례가 바뀌면 커서를 그 차례의 첫 단추로
// 목록은 다섯씩, 아래에 더 보기와 이전 보기. 줄에 번호 없음. 결과가 나오면 커서를 첫 결과 줄로
// 기존 GilChatgiCheot 의 줄(말로 하기·긴급통화·지금 내 자리·점지도 따라 걷기·음향신호기)은 모두 이 화면에 그대로 있음(자리만 아이폰 차례로)
package kr.or.ada.app.gilnun

import android.content.Context
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.widget.doAfterTextChanged

/** 다섯씩 보여 주는 목록(아이폰 Mokrok5) — 첫 결과 줄을 돌려줌. 더 보기·이전 보기를 누르면 omgigi(새 시작) */
fun <T> yjMokrok5(t: GilnunActivity, l: List<T>, sijak0: Int, julGeul: (T) -> String, nureum: (T) -> Unit, omgigi: (Int) -> Unit): Button? {
    if (l.isEmpty()) return null
    val sijak = if (sijak0 >= l.size) ((l.size - 1) / 5) * 5 else maxOf(0, sijak0)
    val kkeut = minOf(sijak + 5, l.size)
    var cheot: Button? = null
    for (n in sijak until kkeut) {
        val x = l[n]
        val b = t.danchu(julGeul(x)) { nureum(x) }
        if (cheot == null) cheot = b
    }
    if (kkeut < l.size) t.danchu("더 보기") { omgigi(sijak + 5) }
    if (sijak > 0) t.danchu("이전 보기") { omgigi(maxOf(0, sijak - 5)) }
    return cheot
}

// MARK: 길 찾기 탭 첫 화면

class GilChatgiSae : Hwamyeon("길 찾기") {
    private var mal = ""
    private var yeojeongPyeol = false
    private var ttaraPyeol = false
    private var geuBakkPyeol = false
    private var geurinGiun = ""
    private var cheot: View? = null

    override fun chaeugi(t: GilnunActivity) {
        cheot = null
        geurinGiun = giun()
        val jm = JeomEngine
        val y = YeojeongEngine.jigeum
        val mu = JeomEngine.muleum

        // ⓪ 읽지 않은 긴급 공지 — 있을 때만 맨 위(알림 묶음)
        GongjiEngine.gingeupJul(t)
        // ① 말로 하기 — 큰 단추, 맨 위 첫 줄(2.4.0 대표님 승인 자리를 지킴 — 통합 결정). 글자는 그 자리에서만 바꿈(화면을 다시 그리지 않음)
        val mb = t.danchu(MalHagi.danchuGeul) { MalHagi.dudeurim() }
        mb.minHeight = t.dp(96)
        mb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        mb.contentDescription = "말로 하기 — 누르고 말씀하십시오"
        val dv = t.geul(malDapGeul())
        dv.visibility = if (MalHagi.dapMal.isEmpty()) View.GONE else View.VISIBLE
        MalHagi.byeonhwa = {
            if (t.wiHwamyeon === this) {
                mb.text = MalHagi.danchuGeul
                dv.text = malDapGeul()
                dv.visibility = if (MalHagi.dapMal.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        // ② 긴급통화서비스 — 말로 하기 바로 아래(2.6.0 승인 자리, 아이폰도 말로 하기 바로 다음). 요청 중·통화 중이면 통화 화면으로 가는 단추(글자만 그 자리에서 바꿈)
        val gb = t.danchu(ginGeupGeul()) { t.yeolgi(GinGeupHwamyeon()) }
        GinGeup.byeonhwa = {
            if (t.wiHwamyeon === this) {
                val n = ginGeupGeul()
                if (gb.text.toString() != n) gb.text = n
            }
        }

        // ③ 하던 일 멈추기 — 접지 않고(이사장님 승인 1)
        // 2.20.0 무엇이 진행 중이든 아니든 늘 맨 위에(이사장님 승인 2026-10-06, 아이폰 2.52.0과 같음)
        t.danchu("하던 일 멈추기 — 안내를 모두 멈추고 새 목적지 찾기") { AnnaeEngine.haneunIlMeomchum() }
        if (!Seoljeong.bopokJaem && !Seoljeong.cheotAnnae) {
            // 2.20.0 처음 안내는 첫 화면을 바꾸지 않고 한 줄로
            t.danchu("처음 안내 — 흰지팡이 당부와 보폭 재기, 처음 한 번") { t.yeolgi(CheotAnnaeHwamyeon()) }
        }
        // 말로 그린 길을 걷는 중이거나 되짚어 나가기 중이면 지금 차례 위에(속 화면을 떠나도 찾기 쉽게)
        if (MalgilEngine.geotneun) {
            t.danchu("말로 그린 길 — 따라 걷는 중, ${MalgilEngine.jemok}") { t.yeolgi(MalgilHwamyeon()) }
        }
        if (DoeEngine.sangtae != DoeEngine.Sangtae.SWIM) {
            t.danchu(if (DoeEngine.sangtae == DoeEngine.Sangtae.GIEOK) "되짚어 나가기 — 길을 기억하는 중, 나가실 때 누르십시오" else "되짚어 나가기 — 나가는 길 안내 중") {
                t.yeolgi(DoeHwamyeon())
            }
        }

        // ④ 지금 차례
        when {
            mu != null -> {
                cheot = t.danchu("점지도로 걷기 — 권합니다. ${mu.julMal}") { JeomEngine.muleumDap(true) }
                t.danchu("위성으로 걷기 — ${mu.mok.ireum}까지 방향 따라") { JeomEngine.muleumDap(false) }
            }
            jm.bulleoneun -> {
                cheot = t.danchu("점지도를 불러오는 중 — 걷는 화면으로") { t.yeolgi(JeomGeotgiHwamyeon()) }
            }
            jm.gil != null -> ttaraPan(t)
            y != null -> yeojeongPan(t, y)
            else -> {
                val e = t.ipryeok("어디로 가실까요 — 이름이나 주소를 넣고 엔터", false)
                e.setText(mal)
                e.imeOptions = EditorInfo.IME_ACTION_SEARCH
                e.setSingleLine(true)
                e.doAfterTextChanged { mal = it?.toString() ?: "" }
                e.setOnEditorActionListener { v, actionId, ev ->
                    val enter = actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE ||
                        (ev != null && ev.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN)
                    if (!enter) return@setOnEditorActionListener false
                    val q = v.text.toString().trim()
                    if (q.isNotEmpty()) {
                        try {
                            (t.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(v.windowToken, 0)
                        } catch (x: Exception) {}
                        t.yeolgi(ChatgiGyeolgwaHwamyeon(q))
                    }
                    true
                }
                t.danchu("즐겨찾기 — 자주 가는 곳") { t.yeolgi(JeulgyeoHwamyeon()) }
                t.danchu("지금 내 자리 듣기") { AnnaeEngine.jigeumJari() }
            }
        }

        // ⑤ 그 밖에 펼치기
        t.pyeolchigi("그 밖에", geuBakkPyeol) { geuBakkPyeol = !geuBakkPyeol }
        if (geuBakkPyeol) {
            t.danchu("주변 신호기 살피기 — 있는지, 블루투스로 울릴 수 있는지") { t.sinhogiHagi { SinhogiEngine.juByeonSalpigi() } }   // 2.13.0
            t.danchu("음향신호기 울리기 — 신호 안내") { t.sinhogiHagi { SinhogiEngine.ulligi(2) } }
            t.danchu("음향신호기 울리기 — 위치 안내") { t.sinhogiHagi { SinhogiEngine.ulligi(1) } }
            t.danchu(if (SinhogiEngine.chatneunJung) "음향신호기 찾기 멈추기" else "음향신호기 찾기 — 가까워질수록 소리가 빨라집니다") {
                if (SinhogiEngine.chatneunJung) {
                    SinhogiEngine.chatgiKkeugi("음향신호기 찾기를 멈췄습니다.")
                    t.dasiGeurigi()
                } else {
                    t.sinhogiHagi {
                        SinhogiEngine.chatgiKyeogi()
                        Sori.mal("음향신호기 찾기를 켭니다. 가까워질수록 소리가 빨라집니다. 다시 누르시면 멈춥니다.")
                        if (t.wiHwamyeon === this) t.dasiGeurigi()
                    }
                }
            }
            t.danchu("가까운 점지도 — 골라서 따라 걷기") { t.yeolgi(JeomMokrokHwamyeon()) }
            t.danchu("되짚어 나가기 — 들어온 길로 혼자 나오기") { t.yeolgi(DoeHwamyeon()) }
            t.danchu("QR 찾기 — 카메라로 둘레의 QR 저절로 찾기") { t.yeolgi(QrChatgiHwamyeon()) }
            t.danchu("말로 그린 길 — 몸으로 익힌 실내 길을 손대지 않고 따라 걷기") { t.yeolgi(MalgilHwamyeon()) }
            t.danchu("음성유도기와 승강기 — 가까운 역의 음성유도기, 엘리베이터") { t.yeolgi(YudoHwamyeon()) }
            // 2.7.0 b3 문 찾기 — 안드로이드는 관리자 시험 중(관리자 열쇠가 있는 폰에서만)
            if (KameraGwanli.boim(t)) t.danchu("문 찾기 — 관리자 시험 중, 문 둘레 글자로 문 찾기") { t.yeolgi(MunChatgiHwamyeon()) }
            t.danchu("기초 시험") { t.yeolgi(GichoSiheom()) }
        }

        // 차례가 바뀔 때만 다시 그림(톡백 커서가 흔들리지 않게)
        val dasi: () -> Unit = { if (t.wiHwamyeon === this && giun() != geurinGiun) t.dasiGeurigi() }
        YeojeongEngine.byeonhwa = dasi
        JeomEngine.byeonhwa = dasi

        // 지금 차례가 있으면 커서를 그 차례의 첫 단추로(화면을 처음 그릴 때)
        cheot?.let { t.chojeomJul(it) }
    }

    private var boinGiun = ""

    override fun boilttae(t: GilnunActivity) {
        // 차례가 바뀌어 다시 그렸으면 커서를 그 차례의 첫 단추로(아이폰 accessibilityFocused 와 같음)
        if (boinGiun.isNotEmpty() && boinGiun != geurinGiun) cheot?.let { t.chojeomOmgigi(it) }
        boinGiun = geurinGiun
    }

    /** 화면 얼개를 정하는 것들 — 이것이 바뀔 때만 다시 그림 */
    private fun giun(): String {
        val jm = JeomEngine
        val y = YeojeongEngine.jigeum
        val sb = StringBuilder()
        sb.append(if (JeomEngine.muleum != null) "M" else "-")
        sb.append(if (jm.bulleoneun) "B" else "-")
        sb.append(if (MalgilEngine.geotneun) "L" else "-").append(DoeEngine.sangtae.name)
        sb.append(if (jm.gil != null) (if (jm.dochakHam) "D" else (if (jm.dwit) "R" else "G")) else "-")
        if (y != null) {
            sb.append(y.danggye.raw).append('|').append(y.mokjeok.ireum).append('|').append(YeojeongEngine.talgeot.raw)
            y.jiha?.let { sb.append("|j").append(it.ipguDochak).append(it.kkeutnam).append(it.to) }
            y.beoseu?.let { sb.append("|b").append(it.dochak).append(it.jeongryujang.ireum) }
        }
        sb.append(JeomPan.giun())   // 2.7.0 b2 — 문이 여럿이 되거나 함께 시험 번호가 생기면 다시 그림
        return sb.toString()
    }

    /** 점지도 따라 걷는 동안(아이폰 TtaraPan — 안드로이드에 있는 것만) */
    private fun ttaraPan(t: GilnunActivity) {
        val jm = JeomEngine
        if (jm.dochakHam) {
            cheot = t.danchu("목적지에 닿았습니다 — 되돌아가기, 이 길을 거꾸로 걷기") { jm.doedoragagi() }
            t.danchu("따라 걷기 마치기 — 여정 끝내기") { jeomGeuman() }
            return
        }
        cheot = t.danchu((if (jm.dwit) "되돌아가는 중" else "점지도 따라 걷는 중") + " — 지금 어디입니까") { jm.jigeumEodiDeutgi() }
        t.danchu("다음에 무엇이 있습니까") { Sori.mal(jm.daeumMuotMal()) }
        JeomPan.ttaraChuga(t)        // 2.7.0 b2 — 다른 문으로(문이 여럿일 때), 여기 문제 있어요, 도움 청하기
        t.pyeolchigi("따라 걷기 다른 할 일", ttaraPyeol) { ttaraPyeol = !ttaraPyeol }
        if (ttaraPyeol) {
            t.danchu("지금 내 자리 듣기") { AnnaeEngine.jigeumJari() }
            t.danchu("걷는 화면 — 방금 한 말 다시 듣기") { t.yeolgi(JeomGeotgiHwamyeon()) }
            JeomPan.ttaraPyeolChuga(t)   // 2.7.0 b2 — 여기 걸렸어요, 이 길목은 어떻게 생겼습니까, 가까운 버스 정류장, 함께 시험
            t.danchu("그만 걷기 — 따라 걷기와 여정을 마칩니다") { jeomGeuman() }
        }
    }

    /** 따라 걷기를 마침 — JeomEngine.geuman 이 잇는 자리(yeojeongKkeut)로 여정도 함께 끝냄(아이폰과 같음) */
    private fun jeomGeuman() {
        JeomEngine.geuman()
    }

    /** 여정 머리 — 지금 차례 한 줄과 그 차례의 큰 단추를 한 자리에(아이폰 YeojeongPan) */
    private fun yeojeongPan(t: GilnunActivity, yj: Yeojeong) {
        val a = AnnaeEngine
        val mok = yj.mokjeok.ireum
        val b = yj.beoseu
        val g = yj.jiha
        if (b != null && yj.danggye == Danggye.TANEUN_GOT_KKAJI) {
            if (b.dochak) {
                cheot = t.danchu("${b.jeongryujang.ireum} 정류장에 닿았습니다 — 버스에 탔습니다, 버스 안 안내") { a.beoseuTatda() }
                t.danchu("오는 버스 듣기") { Beoseu.douchak(b.jeongryujang) { m -> Sori.mal(m) } }
            } else {
                cheot = t.danchu("${b.jeongryujang.ireum} 정류장까지 걸어가는 중 — 지금 어떻게 가고 있습니까") { a.hyeonhwang() }
                t.danchu("버스에 탔습니다 — 버스 안 안내") { a.beoseuTatda() }
            }
        } else if (g != null && (yj.danggye == Danggye.TANEUN_GOT_KKAJI || yj.danggye == Danggye.TANEUN_JUNG)) {
            if (yj.danggye == Danggye.TANEUN_GOT_KKAJI && !g.ipguDochak) {
                cheot = t.danchu("${g.ipgu.ireum}까지 걸어가는 중 — 지금 어떻게 가고 있습니까") { a.hyeonhwang() }
                t.danchu("열차에 탔습니다 — 지나는 역 알려 주기") { JihacheolEngine.tatda(false) }
            } else if (yj.danggye == Danggye.TANEUN_GOT_KKAJI) {
                cheot = t.danchu("${g.ipgu.ireum}에 닿았습니다 — 열차에 탔습니다, 지나는 역 알려 주기") { JihacheolEngine.tatda(false) }
            } else if (g.kkeutnam) {
                cheot = t.danchu("${g.to}역에 닿았습니다 — 밖으로 나왔습니다, 남은 길 걸어서 안내") { a.naeryeotda() }
            } else {
                cheot = t.danchu("${g.to}역까지 지하철로 가는 중 — 몇 정거장 남았습니까") { a.hyeonhwang() }
            }
        } else {
            when (yj.danggye) {
                Danggye.NAM_EUN_GIL ->
                    cheot = t.danchu("${mok}까지 걸어가는 중 — 지금 어떻게 가고 있습니까") { a.hyeonhwang() }
                Danggye.TANEUN_JUNG -> {
                    cheot = t.danchu("${mok}까지 ${YeojeongEngine.talgeot.ireum}로 가는 중 — 내렸습니다, 남은 길 걸어서 안내") { a.naeryeotda() }
                    t.danchu("지금 어떻게 가고 있습니까") { a.hyeonhwang() }
                }
                Danggye.DOCHAK ->
                    cheot = t.danchu("${mok}에 도착했습니다 — 여정 끝내기") { a.kkeut() }
                else -> {
                    cheot = t.danchu("${mok}까지 — 걸어가기, 걷는 안내 시작") {
                        // 맞는 점지도가 있으면 한 번 여쭘
                        a.georeoGagiBoda(yj.mokjeok.jangso) { q -> if (q != null) Sori.mal(q) }
                    }
                    t.danchu("차에 탔습니다 — 차 안 안내 시작") { a.chaTatda() }
                }
            }
        }
        if (yj.danggye != Danggye.DOCHAK) {
            t.pyeolchigi("여정 다른 할 일", yeojeongPyeol) { yeojeongPyeol = !yeojeongPyeol }
            if (yeojeongPyeol) {
                if (yj.danggye == Danggye.NAM_EUN_GIL) {
                    t.danchu("차에 탔습니다 — 차 안 안내로") { a.chaTatda() }
                }
                if (g != null && yj.danggye == Danggye.TANEUN_JUNG) {
                    t.danchu("밖으로 나왔습니다 — 남은 길 걸어서 안내") { a.naeryeotda() }
                }
                if (g == null && yj.danggye == Danggye.TANEUN_JUNG) {
                    t.danchu("탈것 바로잡기 — 지금 ${YeojeongEngine.talgeot.ireum}로 알고 있습니다") { t.yeolgi(TalgeotHwamyeon()) }
                }
                t.danchu("지금 내 자리 듣기") { a.jigeumJari() }
                t.danchu("여정 끝내기 — 목적지를 바꾸실 때도") { a.kkeut() }
            }
        }
    }

    private fun ginGeupGeul() = when (GinGeup.sangtae) {
        GinGeupSangtae.EOPSEUM -> "긴급통화서비스 — 화상통화 요청"
        GinGeupSangtae.YOCHEONG -> "긴급통화 요청 중 — 통화 화면으로"
        else -> "긴급통화 중 — 통화 화면으로"
    }

    private fun malDapGeul() = "들은 말 — ${MalHagi.deureunMal}. 길눈 — ${MalHagi.dapMal}"
}

// MARK: 찾은 곳 목록

/** 찾은 곳 목록 — 결과만 남기고 커서를 첫 줄로 */
class ChatgiGyeolgwaHwamyeon(private val mal: String) : Hwamyeon("$mal 찾기") {
    private var gyeolgwa: List<Jangso>? = null
    private var mothbadeum = false
    private var chatneun = false
    private var sijak = 0
    private var t0: GilnunActivity? = null
    private var cheot: View? = null
    private var chojeomHal = false

    override fun chaeugi(t: GilnunActivity) {
        t0 = t
        cheot = null
        if (gyeolgwa == null && !mothbadeum && !chatneun) chatgi()
        val g = gyeolgwa
        if (g != null) {
            if (g.isEmpty()) {
                cheot = t.geul("$mal — 찾지 못했습니다. 뒤로 가셔서 달리 적어 보십시오.", true)
            } else {
                cheot = yjMokrok5(t, g, sijak, { j -> "${j.ireum} — ${j.juso}${geoMal(j)}" }, { j -> t.yeolgi(MokjeokHwamyeon(j)) }) { s ->
                    sijak = s; chojeomHal = true; t.dasiGeurigi()
                }
            }
        } else if (mothbadeum) {
            cheot = t.danchu("찾기를 받지 못했습니다. 통신이 끊겼을 수 있습니다 — 다시 찾기") { chatgi(); t.dasiGeurigi() }
        } else {
            t.geul("$mal 찾는 중입니다.", true)
        }
    }

    override fun boilttae(t: GilnunActivity) {
        if (!chojeomHal) return
        chojeomHal = false
        cheot?.let { t.chojeomOmgigi(it) }
    }

    private fun geoMal(j: Jangso): String {
        val w = Wichi.jigeum ?: return ""
        return ", " + Annae.geoMal(Wichi.geori(w.lat, w.lon, j.lat, j.lon))
    }

    private fun chatgi() {
        mothbadeum = false
        chatneun = true
        Chatgi.jangso(mal) { r ->
            chatneun = false
            if (r != null) { gyeolgwa = r; sijak = 0 } else mothbadeum = true
            val t = t0 ?: return@jangso
            chojeomHal = true
            if (t.wiHwamyeon === this) t.dasiGeurigi()
        }
    }
}

// MARK: 정한 곳

/** 정한 곳 — 걸어가기, 차로 가기, 지하철로, 버스로, 즐겨찾기에 담기 */
class MokjeokHwamyeon(private val j: Jangso) : Hwamyeon(j.ireum) {
    private var damam = false

    override fun chaeugi(t: GilnunActivity) {
        t.danchu("${j.ireum}${geoMal()} — 걸어가기, 걷는 안내 시작") {
            Jeulgyeo.sseum(j)
            // 맞는 점지도가 있으면 "점지도로 걸을까요, 위성으로 걸을까요" 한 번 여쭘(점지도를 먼저 권함)
            AnnaeEngine.georeoGagiBoda(j) { q -> if (q != null) Sori.mal(q) }
            t.cheotHwamyeonEuro(null)
        }
        t.danchu("차로 가기 — 차에 탔거나 곧 탑니다, 차 안 안내 시작") {
            Jeulgyeo.sseum(j)
            AnnaeEngine.chaTagi(j)
            t.cheotHwamyeonEuro(null)
        }
        t.danchu("지하철로 가기 — 가까운 역, 갈아타기, 나갈 출구를 찾아 드립니다") { t.yeolgi(JihacheolGilHwamyeon(j)) }
        t.danchu("버스로 가기 — 가까운 정류장과 오는 버스") { t.yeolgi(BeoseuChatgiHwamyeon(j)) }
        if (damam || Jeulgyeo.itna(j)) {
            t.geul("즐겨찾기에 담겨 있습니다.", true)
        } else {
            var b: Button? = null
            b = t.danchu("즐겨찾기에 담기") {
                if (damam) return@danchu
                Jeulgyeo.damgi(j)
                damam = true
                b?.text = "즐겨찾기에 담겨 있습니다."   // 그 자리에서 글자만 바꿈(커서가 흔들리지 않게)
                Sori.mal("즐겨찾기에 담았습니다.")
            }
        }
        t.geul("주소 — ${j.juso}", true)
    }

    private fun geoMal(): String {
        val w = Wichi.jigeum ?: return ""
        return ", 여기서 " + Annae.geoMal(Wichi.geori(w.lat, w.lon, j.lat, j.lon))
    }
}

// MARK: 즐겨찾기

/** 즐겨찾기 — 줄에는 이름 하나. 지우기는 톡백 동작(동작 메뉴의 즐겨찾기에서 지우기)이나 길게 누르기로 */
class JeulgyeoHwamyeon : Hwamyeon("즐겨찾기") {
    private var sijak = 0
    private var pyeol = false
    private var ireum = ""
    private var allim = ""
    private var chojeomHal = false
    private var cheot: View? = null

    override fun chaeugi(t: GilnunActivity) {
        cheot = null
        val l = Jeulgyeo.mokrok
        if (l.isEmpty()) {
            t.geul("아직 담은 곳이 없습니다. 목적지를 찾아 고르신 뒤 즐겨찾기에 담기를 누르시면 여기에 모입니다. 아래 펼치기에서 지금 자리를 담으실 수도 있습니다.", true)
        } else {
            val sj = if (sijak >= l.size) ((l.size - 1) / 5) * 5 else sijak
            sijak = sj
            val kkeut = minOf(sj + 5, l.size)
            for (n in sj until kkeut) {
                val h = l[n]
                val b = t.danchu("${h.ireum} — ${h.juso}") { t.yeolgi(MokjeokHwamyeon(h.jangso)) }
                if (cheot == null) cheot = b
                ViewCompat.addAccessibilityAction(b, "즐겨찾기에서 지우기") { _, _ -> jiugi(t, h); true }
                b.setOnLongClickListener { v ->
                    val pm = PopupMenu(t, v)
                    pm.menu.add("즐겨찾기에서 지우기")
                    pm.setOnMenuItemClickListener { jiugi(t, h); true }
                    pm.show()
                    true
                }
            }
            if (kkeut < l.size) t.danchu("더 보기") { sijak = sj + 5; chojeomHal = true; t.dasiGeurigi() }
            if (sj > 0) t.danchu("이전 보기") { sijak = maxOf(0, sj - 5); chojeomHal = true; t.dasiGeurigi() }
        }
        t.pyeolchigi("지금 자리를 즐겨찾기에 담기", pyeol) { pyeol = !pyeol }
        if (pyeol) {
            val e = t.ipryeok("이름 — 예: 집, 사무실", false)
            e.setText(ireum)
            e.doAfterTextChanged { ireum = it?.toString() ?: "" }
            var av: TextView? = null
            t.danchu("지금 자리를 이 이름으로 담기") { jigeumDamgi(t, e, av) }
            val avv = t.geul(allim)
            avv.visibility = if (allim.isEmpty()) View.GONE else View.VISIBLE
            av = avv
        }
    }

    override fun boilttae(t: GilnunActivity) {
        if (!chojeomHal) return
        chojeomHal = false
        cheot?.let { t.chojeomOmgigi(it) }
    }

    private fun jiugi(t: GilnunActivity, h: JeulgyeoHang) {
        Jeulgyeo.jiugi(h)
        Sori.mal("${h.ireum}, 즐겨찾기에서 지웠습니다.")
        chojeomHal = true
        if (t.wiHwamyeon === this) t.dasiGeurigi()
    }

    private fun allimHagi(av: TextView?, m: String) {
        allim = m
        av?.text = m
        av?.visibility = View.VISIBLE
        Sori.mal(m)
    }

    private fun jigeumDamgi(t: GilnunActivity, e: android.widget.EditText, av: TextView?) {
        val nm = e.text.toString().trim()
        if (nm.isEmpty()) { allimHagi(av, "이름을 먼저 적어 주십시오."); return }
        val w = Wichi.jigeum
        if (w == null) { allimHagi(av, "아직 위치를 잡는 중입니다."); return }
        if (w.ochae > 30) {
            allimHagi(av, "지금은 위성이 흐려 자리가 ${w.ochae.toInt()}미터쯤 어긋날 수 있습니다. 밖으로 나가 다시 담아 주십시오.")
            return
        }
        allim = "주소를 알아보는 중입니다."
        av?.text = allim
        av?.visibility = View.VISIBLE
        Chatgi.juso(w.lat, w.lon) { juso ->
            if (juso == null) {
                allimHagi(av, "주소를 받지 못했습니다. 통신을 확인하신 뒤 다시 눌러 주십시오.")
                return@juso
            }
            if (Jeulgyeo.damgi(Jangso(nm, juso, w.lat, w.lon))) {
                ireum = ""
                allim = "$nm — $juso, 즐겨찾기에 담았습니다."
                Sori.mal(allim)
                if (t.wiHwamyeon === this) t.dasiGeurigi()   // 목록에 새 줄이 생기므로 다시 그림
            } else {
                allimHagi(av, "같은 이름이나 같은 자리가 이미 담겨 있습니다.")
            }
        }
    }
}
