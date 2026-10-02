// 안드로이드 길눈 — 점지도 따라 걷기 화면(2.2.0, 빌드 261002-A4, 대표님 지시: 점지도 따라 걷기를 안드로이드에도)
// 아이폰 JeomView.swift 의 가까운 점지도(GakkaunJeomView)와 따라 걷는 동안의 단추(TtaraPan)를 GilnunActivity 화면 틀로 옮겼습니다.
//   가까운 점지도 — 다섯씩, 아래에 더 보기와 이전 보기. 줄에 번호 없음. 목록이 나오면 커서를 첫 결과 줄로
//   길 고르기 — 이 길 따라 걷기, 거꾸로 걷기(되돌아가기)
//   걷는 화면 — 지금 어디쯤인지 듣기, 방금 한 말 다시 듣기, 그만 걷기. 도착하면 안내와 되돌아가기 단추를 한 자리에
// 자주 바뀌는 남은 거리 같은 숫자는 화면에 늘 띄우지 않음(화면낭독기가 쉬지 않고 떠들지 않게) — 물으실 때 말로 알려 드림
// TODO(아이폰 GakkaunJeomView 이어서 갈 수 있는 곳) 여러 점지도 이어 걷기 목록은 다음 판
package kr.or.ada.app.gilnun

import android.os.Handler
import android.os.Looper
import android.view.View

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

    override fun chaeugi(t: GilnunActivity) {
        JeomEngine.byeonhwa = { if (t.wiHwamyeon === this) t.dasiGeurigi() }
        cheot = null
        val e = JeomEngine
        when {
            e.bulleoneun -> {
                cheot = t.geul("길을 불러오는 중입니다.")
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
                t.danchu("방금 한 말 다시 듣기") { Sori.dasiDeutgi() }
                t.danchu("그만 걷기 — 따라 걷기를 마칩니다") { e.geuman(); t.dwiro() }
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
