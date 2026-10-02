// 안드로이드 길눈 — 버스로 가기와 탈것 바로잡기(묶음 b1, 아이폰 BeoseuView.swift 와 같은 말·같은 나스 약속)
// 가까운 정류장, 오는 버스와 몇 분 뒤인지(저상 여부), 정류장까지 걷는 안내, 탄 뒤 차 안 안내.
// 탈것 바로잡기 — 길눈이 빠르기로 알아챈 탈것이 틀리면 이용자가 바로잡음(바로잡은 것이 가장 앞섬, 2026-09-26 이사장님).
// 나스 자료 창고: beoseu.php a=gakkaun(가까운 정류장), a=douchak(도착 시간, 서울시 정류소정보)
package kr.or.ada.app.gilnun

import android.view.View
import android.widget.TextView

object Beoseu {
    /** 가까운 정류장 — 못 받으면 null */
    fun gakkaun(lat: Double, lon: Double, kkeut: (List<Jeongryujang>?) -> Unit) {
        Tongsin.json("beoseu.php", mapOf("a" to "gakkaun", "lat" to Chatgi.f6(lat), "lon" to Chatgi.f6(lon))) { o ->
            if (o == null) { kkeut(null); return@json }
            val out = ArrayList<Jeongryujang>()
            val l = o.optJSONArray("jrs")
            if (l != null) for (i in 0 until l.length()) {
                val r = l.optJSONObject(i) ?: continue
                val la = Jeomjido.su(r, "lat") ?: continue
                val lo = Jeomjido.su(r, "lon") ?: continue
                val no = Jeomjido.geulOrNull(r, "seoul_no") ?: Jeomjido.geulOrNull(r, "beonho") ?: ""
                val nm = Jeomjido.geulOrNull(r, "nm") ?: Jeomjido.geulOrNull(r, "name") ?: ""
                out.add(Jeongryujang(no, nm, la, lo))
            }
            kkeut(out)
        }
    }

    /** 오는 버스 — 말로 된 한 덩이(못 받으면 까닭) */
    fun douchak(j: Jeongryujang, kkeut: (String) -> Unit) {
        val q = hashMapOf("a" to "douchak", "lat" to Chatgi.f6(j.lat), "lon" to Chatgi.f6(j.lon), "nm" to j.ireum)
        if (j.no.isNotEmpty()) q["no"] = j.no
        Tongsin.json("beoseu.php", q, 10000) { o ->
            if (o == null) { kkeut("오는 버스를 받지 못했습니다. 통신이 끊겼을 수 있습니다."); return@json }
            val m = Jeomjido.gul(o, "mal")
            if (o.optBoolean("ok", false) && m.isNotEmpty()) { kkeut(m); return@json }
            kkeut(Jeomjido.geulOrNull(o, "msg") ?: "이 정류장의 도착 정보를 받지 못했습니다.")
        }
    }

    /** 목록 한 줄 — 이름, 시계 방향과 거리, 정류장 번호(한 자씩) */
    fun julMal(j: Jeongryujang): String {
        var m = j.ireum + " — " + sigyeMal(j)
        if (j.no.isNotEmpty()) m += ", 정류장 번호 " + j.no.toCharArray().joinToString(" ")
        return m
    }

    fun sigyeMal(j: Jeongryujang): String {
        val w = Wichi.jigeum ?: return ""
        val d = Wichi.geori(w.lat, w.lon, j.lat, j.lon)
        val apjjok = Wichi.hapBang
        val s = if (apjjok >= 0) Jeomjido.sigyeBanghyang(apjjok, Jeomjido.bangwi(w.lat, w.lon, j.lat, j.lon)) else 0
        return (if (s == 0) "" else "${s}시 방향 ") + Annae.geoMal(d)
    }
}

/** 가까운 정류장 목록 — 다섯씩 */
class BeoseuChatgiHwamyeon(private val mok: Jangso) : Hwamyeon("버스로 가기") {
    private var mokrok: List<Jeongryujang>? = null
    private var mot = false
    private var batneun = false
    private var sijak = 0
    private var t0: GilnunActivity? = null
    private var cheot: View? = null
    private var chojeomHal = false

    override fun chaeugi(t: GilnunActivity) {
        t0 = t
        cheot = null
        if (mokrok == null && !mot && !batneun) chatgi()
        val l = mokrok
        if (l != null) {
            if (l.isEmpty()) {
                cheot = t.geul("가까운 정류장을 찾지 못했습니다. 뒤로 가셔서 다른 길을 고르십시오.", true)
            } else {
                cheot = yjMokrok5(t, l, sijak, { Beoseu.julMal(it) }, { t.yeolgi(JeongryujangHwamyeon(mok, it)) }) { s ->
                    sijak = s; chojeomHal = true; t.dasiGeurigi()
                }
            }
        } else if (mot) {
            cheot = t.danchu("정류장을 받지 못했습니다. 통신이 끊겼을 수 있습니다 — 다시 찾기") { mot = false; chatgi(); t.dasiGeurigi() }
        } else {
            t.geul("가까운 정류장을 찾는 중입니다.", true)
        }
    }

    override fun boilttae(t: GilnunActivity) {
        if (!chojeomHal) return
        chojeomHal = false
        cheot?.let { t.chojeomOmgigi(it) }
    }

    private fun chatgi() {
        mot = false
        val w = Wichi.jigeum
        if (w == null) {
            mot = true
            Sori.mal("아직 위치를 잡는 중입니다. 잠시 뒤 다시 찾기를 눌러 주십시오.")
            return
        }
        batneun = true
        Beoseu.gakkaun(w.lat, w.lon) { r ->
            batneun = false
            if (r != null) { mokrok = r; sijak = 0 } else mot = true
            val t = t0 ?: return@gakkaun
            chojeomHal = true
            if (t.wiHwamyeon === this) t.dasiGeurigi()
        }
    }
}

/** 정류장 하나 — 걸어가기, 이미 탔습니다, 오는 버스 */
class JeongryujangHwamyeon(private val mok: Jangso, private val j: Jeongryujang) : Hwamyeon(j.ireum) {
    private var douchak = ""
    private var bureun = false
    private var t0: GilnunActivity? = null
    private var geulJul: TextView? = null

    override fun chaeugi(t: GilnunActivity) {
        t0 = t
        if (!bureun) { bureun = true; deutgi() }
        t.danchu("${j.ireum}까지 걸어가기 — 정류장까지 걷는 안내 시작") {
            Jeulgyeo.sseum(mok)
            AnnaeEngine.beoseuGagi(mok, j)
            t.cheotHwamyeonEuro(null)
        }
        t.danchu("이미 버스에 탔습니다 — 차 안 안내 시작") {
            Jeulgyeo.sseum(mok)
            AnnaeEngine.beoseuGagi(mok, j)
            AnnaeEngine.beoseuTatda()
            t.cheotHwamyeonEuro(null)
        }
        t.danchu("오는 버스 다시 듣기") { deutgi() }
        geulJul = t.geul(if (douchak.isEmpty()) "오는 버스를 알아보는 중입니다." else douchak, true)
    }

    private fun deutgi() {
        Beoseu.douchak(j) { m ->
            douchak = m
            Sori.mal(m, MalGeup.JEONGBO)
            // 화면을 다시 그리지 않고 그 줄의 글자만 바꿈(톡백 커서가 흔들리지 않게)
            val t = t0 ?: return@douchak
            if (t.wiHwamyeon === this) geulJul?.text = m
        }
    }
}

/** 탈것 바로잡기 — 바로잡은 것이 가장 앞섭니다 */
class TalgeotHwamyeon : Hwamyeon("지금 타신 것") {
    override fun chaeugi(t: GilnunActivity) {
        for (tg in listOf(Talgeot.CHA, Talgeot.BEOSEU, Talgeot.GICHA, Talgeot.GOSOKBEOSEU)) {
            t.danchu("${tg.ireum}입니다") {
                AnnaeEngine.talgeotBarojapgi(tg)
                t.dwiro()
            }
        }
    }
}
