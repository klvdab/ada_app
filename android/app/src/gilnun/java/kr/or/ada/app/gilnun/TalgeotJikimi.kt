// 2.36.0 (261011-A1, 이사장님 승인 2026-10-11 「1」) — 아이폰 TalgeotJikimi.swift(2.64.0)와 같음
// 탈것 지키미 — 배(선박) 알아보기, 배 안 지금 자리 알리기, 기차·배에서 내리셨는지 한 번 여쭙기
// 배: 물 위(서버 jeom.php a=jimyeong 이 카카오 대신 오픈스트리트맵으로 답함)가 1분 간격으로 두 번 잇달아 맞고, 항구에서 떠났거나 시속 10~70킬로미터로 2분 넘게 고르게 가면 배로 보고 지킴
// 배 안 지금 자리: 어느 바다 앞인지, 떠난 항구에서 몇 킬로미터, 목적지까지 몇 킬로미터와 몇 분, 가까운 섬·등대가 몇 시 방향 몇 킬로미터
// 내리셨는지: 기차·배 지킴 중 역·항구 가까이에서 2분 넘게 천천히 걸으며 100미터 넘게 멀어지면 한 번 여쭘(스스로 바꾸지 않음)
package kr.or.ada.app.gilnun

import org.json.JSONObject

object TalgeotJikimi {
    val hanggu = listOf(
        Triple("인천항 연안여객터미널", 37.4541, 126.59853),
        Triple("인천항 국제여객터미널", 37.4237, 126.6053),
        Triple("방아머리선착장", 37.29636, 126.57442),
        Triple("대천연안여객선터미널", 36.32716, 126.51154),
        Triple("군산항 연안여객터미널", 35.97797, 126.62926),
        Triple("격포항", 35.62043, 126.47001),
        Triple("목포연안여객선터미널", 34.78202, 126.38428),
        Triple("목포국제여객터미널", 34.78108, 126.38196),
        Triple("진도항", 34.37408, 126.13434),
        Triple("완도항여객선터미널", 34.31672, 126.76133),
        Triple("땅끝항", 34.29827, 126.53036),
        Triple("녹동항여객선터미널", 34.52294, 127.14437),
        Triple("여수연안여객선터미널", 34.73847, 127.73261),
        Triple("삼천포신항 여객선터미널", 34.92698, 128.08838),
        Triple("통영항여객선터미널", 34.83985, 128.42024),
        Triple("저구항", 34.73075, 128.60607),
        Triple("옥포항", 34.89067, 128.69852),
        Triple("장승포항", 34.86604, 128.72456),
        Triple("부산항연안여객터미널", 35.10288, 129.0415),
        Triple("부산항국제여객터미널", 35.11738, 129.04911),
        Triple("포항여객선터미널", 36.05153, 129.37883),
        Triple("묵호항여객선터미널", 37.5503, 129.1125),
        Triple("강릉항여객터미널", 37.77283, 128.95284),
        Triple("후포항", 36.67821, 129.45795),
        Triple("도동항", 37.48153, 130.90881),
        Triple("사동항", 37.46123, 130.87891),
        Triple("저동항", 37.49622, 130.90997),
        Triple("제주항연안여객터미널", 33.51959, 126.535),
        Triple("제주항국제여객터미널", 33.52605, 126.54365),
        Triple("성산포항종합여객터미널", 33.47342, 126.93369),
        Triple("모슬포항", 33.21655, 126.24966),
        Triple("신양항", 33.94457, 126.32887),
        Triple("우도 천진항", 33.49263, 126.95115),
        Triple("외포리선착장", 37.70044, 126.38093),
        Triple("삼목선착장", 37.49951, 126.45244),
        Triple("용기포항", 37.95438, 124.73688),
        Triple("덕적도 진리선착장", 37.22716, 126.15504),
        Triple("송공항", 34.84814, 126.22693),
        Triple("남강선착장", 34.80241, 126.11992),
        Triple("홍도항", 34.68261, 125.19318),
        Triple("청산도항", 34.18125, 126.85425),
        Triple("거문도항", 34.0275, 127.30887),
        Triple("욕지도 여객선터미널", 34.63489, 128.26686),
        Triple("제승당 선착장", 34.79422, 128.47188),
    )

    fun gakkaunHang(lat: Double, lon: Double, banGyeong: Double = 1500.0): Pair<String, Double>? {
        var choe: Pair<String, Double>? = null
        for ((n, la, lo) in hanggu) {
            val d = Wichi.geori(lat, lon, la, lo)
            if (d <= banGyeong && d < (choe?.second ?: Double.MAX_VALUE)) choe = n to d
        }
        return choe
    }

    /** 내리셨는지 여쭙는 중 — 화면에 「아직 타고 있습니다」 단추 */
    var naerimMureum = false
        private set
    /** 화면이 바뀌어야 할 때(여쭙기 시작·끝) */
    var byeonhwa: (() -> Unit)? = null

    private var sijakham = false
    private val yj get() = YeojeongEngine

    private var cheonCheonJari: Jari? = null
    private var umjigSijak = 0L
    private var umjigSijakJari: Jari? = null
    private var mulSalpimTtae = 0L
    private var mulSalpimJung = false
    private var mulYeonsok = 0
    private var jeonJikim: Talgeot? = null
    var tteonanHang: String? = null
        private set
    private var tteonanJari: Pair<Double, Double>? = null
    private var jariMalTtae = 0L
    private var seomSalpimTtae = 0L
    private val malhanSeom = HashSet<String>()
    private var gotDochakMal = false
    private val sokdoGirok = ArrayList<Pair<Long, Double>>()
    private var neurinSijak = 0L
    private var neurinJari: Jari? = null
    private var ibeonMureum = false

    fun sijak() {
        if (sijakham) return
        sijakham = true
        Wichi.deutgi { w -> wichiBatda(w) }
    }

    private fun jikimBoda() {
        val jt = yj.jikimTalgeot
        if (jt == jeonJikim) return
        jeonJikim = jt
        if (jt == null) {
            if (naerimMureum) { naerimMureum = false; byeonhwa?.invoke() }
            ibeonMureum = false; neurinSijak = 0L
            tteonanHang = null; tteonanJari = null
        } else if (jt == Talgeot.SEONBAK) {
            val w = umjigSijakJari ?: cheonCheonJari ?: Wichi.jigeum
            if (w != null) {
                tteonanJari = w.lat to w.lon
                tteonanHang = gakkaunHang(w.lat, w.lon, 3000.0)?.first
            }
            jariMalTtae = System.currentTimeMillis()
            gotDochakMal = false
            malhanSeom.clear()
        }
    }

    private fun wichiBatda(w: Jari) {
        jikimBoda()
        if (w.ochae > 50) return
        val kmh = w.sokdo * 3.6
        val now = System.currentTimeMillis()
        sokdoGirok.add(now to maxOf(0.0, w.sokdo))
        sokdoGirok.removeAll { now - it.first > 600_000 }
        baeBoda(w, kmh, now)
        naerimBoda(w, kmh, now)
    }

    private fun baeBoda(w: Jari, kmh: Double, now: Long) {
        val y = yj.jigeum
        if (y == null || yj.jikimJung || w.georeumChu) { umjigSijak = 0L; mulYeonsok = 0; return }
        if (y.barojabeum && y.talgeot != Talgeot.SEONBAK) return
        if (kmh < 6) { cheonCheonJari = w; umjigSijak = 0L; umjigSijakJari = null; mulYeonsok = 0; return }
        if (kmh < 10 || kmh > 70) { if (kmh > 70) { umjigSijak = 0L; mulYeonsok = 0 }; return }
        if (umjigSijak == 0L) { umjigSijak = now; umjigSijakJari = cheonCheonJari ?: w }
        if (now - mulSalpimTtae < 60_000 || mulSalpimJung) return
        mulSalpimTtae = now
        mulSalpimJung = true
        Tongsin.json("jeom.php", mapOf("a" to "jimyeong", "lat" to f6(w.lat), "lon" to f6(w.lon))) { o ->
            mulSalpimJung = false
            if (o == null) return@json
            mulYeonsok = if (o.optString("eodi", "") == "osm") mulYeonsok + 1 else 0
            val s = umjigSijak
            val sj = umjigSijakJari
            if (mulYeonsok < 2 || s == 0L || sj == null) return@json
            val hang = gakkaunHang(sj.lat, sj.lon)
            val goreuge = now - s >= 120_000
            if (hang == null && !goreuge) return@json
            Girok.namgi("bae_gamji", mapOf("hang" to (hang?.first ?: ""), "mul" to mulYeonsok))
            yj.jikimSijak(Talgeot.SEONBAK, if (hang != null) "${hang.first}에서 떠나 물 위" else "물 위를 고르게 감")
        }
    }

    private fun naerimBoda(w: Jari, kmh: Double, now: Long) {
        val jt = yj.jikimTalgeot ?: return
        val y = yj.jigeum ?: return
        if (y.danggye != Danggye.TANEUN_JUNG) return
        if (kmh > 15) {
            neurinSijak = 0L; neurinJari = null; ibeonMureum = false
            if (naerimMureum) { naerimMureum = false; byeonhwa?.invoke() }
            return
        }
        if (kmh >= 7 || ibeonMureum) return
        val got: String? = if (jt == Talgeot.GICHA) GichaYeok.gakkaun(w.lat, w.lon, 700.0)?.let { it.first + "역" }
            else gakkaunHang(w.lat, w.lon)?.first ?: (if (Wichi.geori(w.lat, w.lon, y.mokjeok.lat, y.mokjeok.lon) <= 1500) y.mokjeok.ireum else null)
        if (got == null) { neurinSijak = 0L; neurinJari = null; return }
        if (neurinSijak == 0L) { neurinSijak = now; neurinJari = w; return }
        val nj = neurinJari ?: return
        if (now - neurinSijak < 120_000 || TalgeotGamji.jigeumUmjigim != "걸음" || Wichi.geori(w.lat, w.lon, nj.lat, nj.lon) < 100) return
        ibeonMureum = true
        naerimMureum = true
        byeonhwa?.invoke()
        Girok.namgi("naerim_mureum", mapOf("tal" to jt.raw, "got" to got))
        Sori.mal("${got}에 닿은 뒤 걸어서 멀어지고 계십니다. ${jt.ireum}에서 내리셨습니까? 맨 위 내렸습니다 단추나, 그 아래 아직 타고 있습니다 단추를 눌러 주십시오.")
    }

    /** 「아직 타고 있습니다」 — 이번 정차에서는 다시 묻지 않음 */
    fun ajikTago() {
        naerimMureum = false
        ibeonMureum = true
        byeonhwa?.invoke()
        Girok.namgi("naerim_dap", mapOf("tago" to true))
        Sori.mal("${yj.talgeot.ireum} 안 안내를 이어 갑니다.")
    }

    /** 배 안 안내 — AnnaeEngine 이 타고 가는 중에 부름 */
    fun baeAnnae(w: Jari, d: Double, y: Yeojeong) {
        val now = System.currentTimeMillis()
        if (d <= 1000 && !gotDochakMal) {
            gotDochakMal = true
            Sori.mal("곧 ${y.mokjeok.ireum}에 닿습니다. 내리실 준비를 하십시오. 내리시면 맨 위 내렸습니다 단추를 눌러 주십시오.")
            return
        }
        val gap = AnnaeSeoljeong.baeGap
        if (gap > 0 && now - jariMalTtae >= gap * 60_000L) {
            jariMalTtae = now
            jariMal(w, d, y, false)
            return
        }
        if (now - seomSalpimTtae >= 60_000) {
            seomSalpimTtae = now
            jariMal(w, d, y, true)
        }
    }

    /** 「지금 어떻게 가고 있습니까」·「지금 내 자리」를 배 안에서 물으셨을 때 */
    fun jigeumBaeJari() {
        val w = Wichi.jigeum
        val y = yj.jigeum
        if (w == null || y == null) { Sori.mal("아직 위치를 잡는 중입니다. 잡히면 다시 눌러 주십시오."); return }
        jariMalTtae = System.currentTimeMillis()
        jariMal(w, Wichi.geori(w.lat, w.lon, y.mokjeok.lat, y.mokjeok.lon), y, false)
    }

    private val pyeonggyunSokdo: Double
        get() {
            val v = sokdoGirok.map { it.second }.filter { it > 2 }
            return if (v.size < 5) 8.0 else v.average()
        }

    private fun f6(v: Double) = String.format(java.util.Locale.US, "%.6f", v)

    private fun jariMal(w: Jari, d: Double, y: Yeojeong, seomMan: Boolean) {
        val la = f6(w.lat); val lo = f6(w.lon)
        val apjjok = if (w.banghyang >= 0 && w.sokdo > 1.5) w.banghyang else Wichi.hapBang
        val gyeol = arrayOfNulls<JSONObject>(3)
        var nameun = 3
        val kkeut = {
            nameun -= 1
            if (nameun == 0) jariMalMandeulgi(w, d, y, seomMan, apjjok, gyeol[0], gyeol[1], gyeol[2])
        }
        Tongsin.json("jeom.php", mapOf("a" to "jimyeong", "lat" to la, "lon" to lo)) { o -> gyeol[0] = o; kkeut() }
        Tongsin.json("jeom.php", mapOf("a" to "jangso", "q" to "섬", "lat" to la, "lon" to lo)) { o -> gyeol[1] = o; kkeut() }
        Tongsin.json("jeom.php", mapOf("a" to "jangso", "q" to "등대", "lat" to la, "lon" to lo)) { o -> gyeol[2] = o; kkeut() }
    }

    private fun jariMalMandeulgi(w: Jari, d: Double, y: Yeojeong, seomMan: Boolean, apjjok: Double, o: JSONObject?, s: JSONObject?, g: JSONObject?) {
        var gakkaun: Triple<String, Double, Double>? = null   // 이름, 거리, 방위
        for (src in listOf(s, g)) {
            val rows = src?.optJSONArray("rows") ?: continue
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val n = r.optString("ireum", "")
                val a = Jeomjido.su(r, "lat") ?: continue
                val b = Jeomjido.su(r, "lon") ?: continue
                if (n.isEmpty()) continue
                val m = Wichi.geori(w.lat, w.lon, a, b)
                if (m <= 5000 && m < (gakkaun?.second ?: Double.MAX_VALUE)) gakkaun = Triple(n, m, Jeomjido.bangwi(w.lat, w.lon, a, b))
            }
        }
        var seomMal = ""
        val gk = gakkaun
        if (gk != null) {
            val si = if (apjjok >= 0) Jeomjido.sigyeBanghyang(apjjok, gk.third) else 0
            seomMal = (if (si == 0) "" else "${si}시 방향 ") + "${Annae.geoMal(gk.second)}에 ${gk.first}${iGa(gk.first)} 있습니다."
            if (seomMan) {
                if (gk.second > 2000 || malhanSeom.contains(gk.first)) return
                malhanSeom.add(gk.first)
                Sori.mal(seomMal, MalGeup.JEONGBO)
                return
            }
        }
        if (seomMan) return
        var mal = ""
        if (o != null) {
            val juso = o.optString("juso", "")
            if (o.optString("eodi", "") == "osm") {
                val jogak = juso.split(",").map { it.trim() }
                val sg = jogak.firstOrNull { it.endsWith("시") || it.endsWith("군") }
                if (sg != null) mal = "지금 $sg 앞바다입니다. "
                else if (jogak.isNotEmpty() && jogak[0].isNotEmpty()) mal = "지금 ${jogak[0]} 앞바다입니다. "
            } else if (juso.isNotEmpty()) mal = "지금 $juso 가까이입니다. "
        }
        val h = tteonanHang
        val t = tteonanJari
        if (h != null && t != null) mal += "${h}에서 ${Annae.geoMal(Wichi.geori(t.first, t.second, w.lat, w.lon))} 나왔고, "
        val bun = maxOf(1, Math.round(d * 1.15 / pyeonggyunSokdo / 60).toInt())
        val bunMal = if (bun < 60) "${bun}분" else (if (bun % 60 == 0) "${bun / 60}시간" else "${bun / 60}시간 ${bun % 60}분")
        mal += "${y.mokjeok.ireum}까지 ${Annae.geoMal(d)}, 약 $bunMal 남았습니다."
        if (seomMal.isNotEmpty()) mal += " " + seomMal
        if (gk != null && gk.second <= 2000) malhanSeom.add(gk.first)
        Sori.mal(mal)
    }

    /** 받침이 있으면 「이」, 없으면 「가」 */
    fun iGa(s: String): String {
        val c = s.lastOrNull() ?: return "이"
        if (c.code < 0xAC00 || c.code > 0xD7A3) return "이"
        return if ((c.code - 0xAC00) % 28 == 0) "가" else "이"
    }
}
