// 안드로이드 길눈 — 점지도 따라 걷기 화면(2.2.0, 빌드 261002-A4, 대표님 지시: 점지도 따라 걷기를 안드로이드에도)
// 아이폰 JeomView.swift 의 가까운 점지도(GakkaunJeomView)와 따라 걷는 동안의 단추(TtaraPan)를 GilnunActivity 화면 틀로 옮겼습니다.
//   가까운 점지도 — 다섯씩, 아래에 더 보기와 이전 보기. 줄에 번호 없음. 목록이 나오면 커서를 첫 결과 줄로
//   길 고르기 — 이 길 따라 걷기, 거꾸로 걷기(되돌아가기)
//   걷는 화면 — 지금 어디쯤인지 듣기, 방금 한 말 다시 듣기, 그만 걷기. 도착하면 안내와 되돌아가기 단추를 한 자리에
// 자주 바뀌는 남은 거리 같은 숫자는 화면에 늘 띄우지 않음(화면낭독기가 쉬지 않고 떠들지 않게) — 물으실 때 말로 알려 드림
// 2.7.0(묶음 b2 점지도 마저, 대표님 지시 — 아이폰 JeomView 2.10.0~2.15.0과 같이)
//   가까운 점지도 아래 "이어서 갈 수 있는 곳 펼치기"(여러 점지도 이어 걷기, 다섯씩)
//   걷는 화면 — 다른 문으로(문이 여럿일 때), 다음에 무엇이 있습니까, 여기 문제 있어요, 도움 청하기,
//     따라 걷기 다른 할 일 펼치기(여기 걸렸어요, 지금 내 자리 듣기, 이 길목은 어떻게 생겼습니까, 가까운 버스 정류장, 함께 시험, 그만 걷기)
//   길 찾기 탭 맨 위 줄들(JeomPan.wiJul) — 점지도로 걸을까요 여쭘, 따라 걷는 중, 되짚어 나가기·말로 그린 길 한 줄
//   여기 문제 있어요(MunjeHwamyeon) — 여섯 가지 가운데 고르고, 한마디는 적어도 되고 안 적어도 됨
package kr.or.ada.app.gilnun

import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.core.widget.doAfterTextChanged

/** 가까운 점지도 — 다섯씩 */
class JeomMokrokHwamyeon : Hwamyeon("가까운 점지도") {
    private var mok: List<JeomMok>? = null
    private var mot = false
    private var batneun = false
    private var sijak = 0
    private var chojeomHal = false
    private var cheotGyeolgwa: View? = null
    private var t0: GilnunActivity? = null
    private val main = Handler(Looper.getMainLooper())
    // 2.7.0 이어서 갈 수 있는 곳
    private var ieumPyeol = false
    private var ieum: List<JeomIeum>? = null
    private var ieumBatneun = false
    private var ieumSijak = 0

    override fun chaeugi(t: GilnunActivity) {
        t0 = t
        cheotGyeolgwa = null
        val m = mok
        if (m == null && !mot && !batneun) batgi()
        if (m != null) {
            if (m.isEmpty()) {
                cheotGyeolgwa = t.geul("올라온 점지도가 없습니다. 자봉이 그려 올리면 여기에 나옵니다.")
            } else {
                val kkeut = minOf(sijak + 5, m.size)
                for (n in sijak until kkeut) {
                    val j = m[n]
                    val b = t.danchu(j.julMal) { t.yeolgi(JeomGilGoreugi(j)) }
                    if (n == sijak) cheotGyeolgwa = b
                }
                if (kkeut < m.size) t.danchu("더 보기") { sijak += 5; chojeomHal = true; t.dasiGeurigi() }
                if (sijak > 0) t.danchu("이전 보기") { sijak = maxOf(0, sijak - 5); chojeomHal = true; t.dasiGeurigi() }
                t.geul("누르시면 그 길을 따라 걸을지, 거꾸로 걸을지 고르실 수 있습니다.")
            }
        } else if (mot) {
            cheotGyeolgwa = t.danchu("목록을 받지 못했습니다. 통신이나 위치를 확인하신 뒤 — 다시 받기") { mot = false; batgi(); t.dasiGeurigi() }
        } else {
            t.geul("가까운 점지도를 찾는 중입니다.")
        }
        // 2.7.0 여러 점지도를 이어 걷기 — 한 구간을 마치면 저절로 다음 구간
        val pb = t.danchu(if (ieumPyeol) "이어서 갈 수 있는 곳 접기" else "이어서 갈 수 있는 곳 펼치기 — 점지도 여러 개를 이어 걷기") {
            ieumPyeol = !ieumPyeol
            if (ieumPyeol && ieum == null) ieumBatgi()
            chojeomHal = ieumPyeol
            t.dasiGeurigi()
        }
        if (ieumPyeol) {
            val ie = ieum
            if (ie == null) {
                val g = t.geul("이어진 길을 찾는 중입니다.")
                if (chojeomHal) cheotGyeolgwa = g
            } else if (ie.isEmpty()) {
                val g = t.geul("이어 둔 길이 없습니다.")
                if (chojeomHal) cheotGyeolgwa = g
            } else {
                val kkeut = minOf(ieumSijak + 5, ie.size)
                for (n in ieumSijak until kkeut) {
                    val x = ie[n]
                    val b = t.danchu(x.julMal) {
                        JeomEngine.ieumGeotgi(t, x.gugan, null)
                        t.tabGo(0)
                        t.yeolgi(JeomGeotgiHwamyeon())
                    }
                    if (n == ieumSijak && chojeomHal) cheotGyeolgwa = b
                }
                if (kkeut < ie.size) t.danchu("이어진 길 더 보기") { ieumSijak += 5; chojeomHal = true; t.dasiGeurigi() }
                if (ieumSijak > 0) t.danchu("이어진 길 이전 보기") { ieumSijak = maxOf(0, ieumSijak - 5); chojeomHal = true; t.dasiGeurigi() }
            }
        } else if (chojeomHal && cheotGyeolgwa == null) {
            cheotGyeolgwa = pb
        }
    }

    override fun boilttae(t: GilnunActivity) {
        if (!chojeomHal) return
        chojeomHal = false
        cheotGyeolgwa?.let { t.chojeomOmgigi(it) }
    }

    private fun batgi() {
        batneun = true
        val w = Wichi.jigeum
        if (w == null) {
            // 위치를 아직 못 받았으면 3초 기다려 한 번 더(아이폰과 같음)
            main.postDelayed({
                val w2 = Wichi.jigeum
                if (w2 != null) batgiSok(w2.lat, w2.lon)
                else { batneun = false; mot = true; Sori.mal("아직 위치를 잡는 중입니다."); dasi() }
            }, 3000)
            return
        }
        batgiSok(w.lat, w.lon)
    }

    private fun batgiSok(la: Double, lo: Double) {
        Jeomjido.gakkaun(la, lo) { r ->
            batneun = false
            if (r != null) {
                mok = r.sortedBy { if (it.near < 0) 1e9 else it.near }
                sijak = 0
            } else {
                mot = true
            }
            dasi()
        }
    }

    private fun ieumBatgi() {
        if (ieumBatneun) return
        ieumBatneun = true
        Jeomjido.ieumMok { r ->
            ieumBatneun = false
            ieum = r ?: emptyList()
            ieumSijak = 0
            if (ieumPyeol) dasi()
        }
    }

    /** 이 화면이 아직 보이면 다시 그리고 첫 결과 줄로 */
    private fun dasi() {
        val t = t0 ?: return
        chojeomHal = true
        if (t.wiHwamyeon === this) t.dasiGeurigi()
    }
}

/** 길 고르기 — 이 길 따라 걷기, 거꾸로 걷기 */
class JeomGilGoreugi(private val j: JeomMok) : Hwamyeon(j.ireum) {
    override fun chaeugi(t: GilnunActivity) {
        t.danchu("이 길 따라 걷기 — ${j.from}에서 ${j.to}까지") { geotgi(t, false) }
        t.danchu("거꾸로 걷기(되돌아가기) — ${j.to}에서 ${j.from}까지") { geotgi(t, true) }
        t.geul(j.julMal)
    }

    private fun geotgi(t: GilnunActivity, dw: Boolean) {
        JeomEngine.bulleoGeotgi(t, j.id, dw)
        t.tabGo(0)   // 길 찾기 첫 화면으로 돌린 뒤 걷는 화면을 엶 — 걷는 화면에서 뒤로 하면 길 찾기 첫 화면
        t.yeolgi(JeomGeotgiHwamyeon())
    }
}

/** 걷는 화면 — 따라 걷는 동안과 도착한 뒤 */
class JeomGeotgiHwamyeon : Hwamyeon("점지도 따라 걷기") {
    private var cheot: View? = null
    private var boinDochak = false
    private var deoPyeol = false   // 2.7.0 따라 걷기 다른 할 일

    override fun chaeugi(t: GilnunActivity) {
        JeomEngine.byeonhwa = { if (t.wiHwamyeon === this) t.dasiGeurigi() }
        cheot = null
        val e = JeomEngine
        when {
            e.bulleoneun -> {
                cheot = t.geul(if (e.ieum.isNotEmpty() && e.ieumIdx > 0) "다음 구간을 불러오는 중입니다." else "길을 불러오는 중입니다.")
                t.danchu("그만 걷기 — 불러오기를 그만둡니다") { e.geuman(); t.dwiro() }
            }
            e.gil == null -> {
                cheot = t.danchu("가까운 점지도에서 길 고르기") { t.yeolgi(JeomMokrokHwamyeon()) }
            }
            e.dochakHam -> {
                // 안내와 단추를 한 자리에 — 도착 안내를 들은 그 자리에서 곧바로 되돌아가기
                cheot = t.danchu("목적지에 닿았습니다 — 되돌아가기, 이 길을 거꾸로 걷기") { e.doedoragagi() }
                t.danchu("따라 걷기 마치기") { e.geuman(); t.dwiro() }
                t.danchu("방금 한 말 다시 듣기") { Sori.dasiDeutgi() }
            }
            else -> {
                cheot = t.danchu("지금 어디쯤인지 듣기 — " + (if (e.dwit) "되돌아가는 중" else "따라 걷는 중")) { e.jigeumEodiDeutgi() }
                t.danchu("다음에 무엇이 있습니까") { e.daeumMuot() }
                t.danchu("방금 한 말 다시 듣기") { Sori.dasiDeutgi() }
                JeomPan.ttaraChuga(t)   // 다른 문으로, 여기 문제 있어요, 도움 청하기
                t.danchu(if (deoPyeol) "따라 걷기 다른 할 일 접기" else "따라 걷기 다른 할 일 펼치기") { deoPyeol = !deoPyeol; t.dasiGeurigi() }
                if (deoPyeol) {
                    t.danchu("지금 내 자리 듣기") { JeomPan.jariMal() }
                    JeomPan.ttaraPyeolChuga(t)   // 여기 걸렸어요, 길목, 버스 정류장, 함께 시험
                    t.danchu("그만 걷기 — 따라 걷기를 마칩니다") { e.geuman(); t.dwiro() }
                }
            }
        }
    }

    override fun boilttae(t: GilnunActivity) {
        // 도착하거나 걷기를 다시 시작해 단추가 바뀌면 커서를 첫 줄로
        val d = JeomEngine.dochakHam
        if (d != boinDochak) {
            boinDochak = d
            cheot?.let { t.chojeomOmgigi(it) }
        }
    }
}

/** 2.7.0 여기 문제 있어요 — 여섯 가지 가운데 고르고, 한마디는 적어도 되고 안 적어도 됨(jeom_db.php a=georim) */
class MunjeHwamyeon : Hwamyeon("여기 문제 있어요") {
    private var hanmadi = ""
    private var bonaeneun = false

    override fun chaeugi(t: GilnunActivity) {
        val e = t.ipryeok("한마디 더 — 적지 않으셔도 됩니다", false)
        e.setText(hanmadi)
        e.doAfterTextChanged { hanmadi = it?.toString() ?: "" }
        for (k in Jeomjido.MUNJE) {
            t.danchu(k) {
                if (bonaeneun) return@danchu
                bonaeneun = true
                val w = Wichi.jigeum
                Sori.mal("적는 중입니다.", MalGeup.JEONGBO)
                Jeomjido.munje(k, hanmadi.trim(), w) { r ->
                    bonaeneun = false
                    Sori.mal(r)
                    if (t.wiHwamyeon === this) t.dwiro()
                }
            }
        }
    }
}

/** 2.7.0 길 찾기 탭 첫 화면에 넣는 점지도 줄들 — 길 찾기 첫 화면(GilChatgiCheot 또는 b1 의 GilChatgiSae)이 부름.
 *  아이폰 길 찾기 탭 맨 위(JeomMuleumPan·TtaraPan)와 그 밖에 펼치기의 같은 줄, 같은 말 */
object JeomPan {
    /** JeomEngine.georeoGagiBoda 로 여쭌 점지도(여러 점지도 이어 걷기까지) — 여쭌 것이 없으면 아무것도 넣지 않음. 첫 단추를 돌려줌 */
    fun muleumJul(t: GilnunActivity): View? {
        val m = JeomEngine.muleum ?: return null
        val b = t.danchu("점지도로 걷기 — 권합니다. ${m.julMal}") { JeomEngine.muleumDap(true); t.yeolgi(JeomGeotgiHwamyeon()) }
        t.danchu("위성으로 걷기 — ${m.mok.ireum}까지 방향 따라") { JeomEngine.muleumDap(false); t.dasiGeurigi() }
        return b
    }

    /** 따라 걷는 동안 "다음에 무엇이 있습니까" 바로 아래 — 다른 문으로(문이 여럿일 때), 여기 문제 있어요, 도움 청하기 */
    fun ttaraChuga(t: GilnunActivity) {
        val e = JeomEngine
        if (e.munOn && e.munSu > 1) t.danchu("다른 문으로 — 문이 ${e.munSu}곳 있습니다") { e.dareunMun() }
        t.danchu("여기 문제 있어요 — 점자블록 없어짐, 공사 등 알리기") { t.yeolgi(MunjeHwamyeon()) }
        t.danchu("도움 청하기 — 긴급통화서비스") { t.yeolgi(GinGeupHwamyeon()) }
    }

    /** 따라 걷기 다른 할 일 펼치기 안 — 여기 걸렸어요, 이 길목은 어떻게 생겼습니까, 가까운 버스 정류장, 함께 시험 */
    fun ttaraPyeolChuga(t: GilnunActivity) {
        val e = JeomEngine
        t.danchu("여기 걸렸어요 — 다음 분께 알려 주기") { e.geollimNamgigi() }
        t.danchu("이 길목은 어떻게 생겼습니까") { e.gilmok() }
        t.danchu("가까운 버스 정류장") { e.beoseuJeongryujang() }
        val hb = e.hamkkeBunho
        t.danchu(if (hb != null) "함께 시험 끝내기 — 번호 " + hb.toList().joinToString(" ") else "함께 시험 번호 받기 — 곁의 자봉과 함께 시험") { e.hamkkeNureum() }
    }

    /** 되짚어 나가는 중·길을 기억하는 중이거나 말로 그린 길을 걷는 중이면 맨 위 한 줄 — 첫 단추를 돌려줌 */
    fun wiJul(t: GilnunActivity): View? {
        var cheot: View? = null
        if (DoeEngine.sangtae != DoeEngine.Sangtae.SWIM) {
            cheot = t.danchu("되짚어 나가기 — ${DoeEngine.jul}") { t.yeolgi(DoeHwamyeon()) }
        }
        if (MalgilEngine.geotneun) {
            val b = t.danchu("말로 그린 길 — ${MalgilEngine.jul}") { t.yeolgi(MalgilHwamyeon()) }
            if (cheot == null) cheot = b
        }
        return cheot
    }

    /** 길 찾기 탭 "그 밖에 펼치기" 안 — 되짚어 나가기, 말로 그린 길, 음성유도기와 승강기 */
    fun geuBakke(t: GilnunActivity) {
        t.danchu("되짚어 나가기 — 들어온 길로 혼자 나오기") { t.yeolgi(DoeHwamyeon()) }
        t.danchu("말로 그린 길 — 실내 길 따라 걷기") { t.yeolgi(MalgilHwamyeon()) }
        t.danchu("음성유도기와 승강기 — 가까운 지하철역") { t.yeolgi(YudoHwamyeon()) }
    }

    /** 지금 내 자리 — 길 찾기 첫 화면의 지금 내 자리와 같은 말을 내는 곳으로 바꿔 채울 수 있음(b1 이면 AnnaeEngine.jigeumJari) */
    var jariMalHagi: (() -> Unit)? = null

    fun jariMal() {
        val f = jariMalHagi
        if (f != null) { f(); return }
        val j = Wichi.jigeum
        if (!Wichi.heorakItda) { Sori.mal("위치 허락이 없어 자리를 알 수 없습니다. 폰 설정의 앱, 길눈, 권한에서 위치를 허용해 주십시오."); return }
        if (j == null) { Sori.mal("아직 위치를 받지 못했습니다. 하늘이 트인 곳에서 잠시 기다려 주십시오."); return }
        Sori.mal("자리를 찾는 중입니다.", MalGeup.JEONGBO)
        Jeomjido.jimyeong(j.lat, j.lon) { juso ->
            var m = if (juso.isNotEmpty()) "지금 $juso 근처입니다." else "주소를 받지 못했습니다."
            m += if (j.georeumChu) " 위성이 흐려 걸음으로 이어 셉니다." else if (j.ochae <= 15) " 위성이 잘 잡혀 있습니다." else " 위성 오차가 ${j.ochae.toInt()}미터입니다."
            Sori.mal(m)
        }
    }

    /** 화면 얼개 — 이것이 바뀌면 길 찾기 첫 화면을 다시 그려야 함(b1 GilChatgiSae.giun 에 덧붙임) */
    fun giun(): String {
        val e = JeomEngine
        return (if (e.muleum != null) "m" else "-") + (if (e.munOn) "o${e.munSu}" else "-") + (e.hamkkeBunho ?: "-") +
            DoeEngine.sangtae.name + (if (MalgilEngine.geotneun) "w" else "-")
    }
}
