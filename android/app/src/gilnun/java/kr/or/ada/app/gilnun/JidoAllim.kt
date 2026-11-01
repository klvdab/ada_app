// 지도 알림 — 2.88.0 (261101-A76, 이사장님 지시 2026-11-01 「할 수 있는거라면 다 넣고 싶어 … 알아서 해」, 아이폰 2.124.0 JidoAllim.swift 와 같음)
// 오픈스트리트맵(협회 서버 jido.php, 전국을 나스에 미리 받아 둠)에서 차·버스·기차·지하철 안에서 쓸모 있는 것을 알려 드림.
//   ① 다리 — 들어가기 전에 「곧 한강대교입니다. 한강을 건넙니다. 길이는 약 1.1킬로미터입니다」, 다 건너면 「다리를 건넜습니다」.
//      찻길에서는 찻길 다리만, 기차·지하철에서는 기찻길 다리만. 이름도 물길도 없는 300미터 아래 다리는 말하지 않음.
//      그 다리 길 위를 같은 방향으로 갈 때만 알림 — 옆길·다리 밑을 지날 때 잘못 말하지 않게.
//   ② 고속도로 곁 — 휴게소·졸음쉼터·나들목·분기점·요금소를 앞에 닿기 전에 한 번. 초속 14미터(시속 50킬로미터) 넘게 달릴 때만.
//   ③ 고장 경계 — 시·군·구가 바뀌면 「경기도 여주시에 들어섰습니다」 한 번.
// 설정 탭 말하기 설정의 「지도 알림」으로 끄고 켬(처음 켜짐).
package kr.or.ada.app.gilnun

import java.util.Locale

object JidoAllim {
    private class Dari(val ireum: String, val gil: String, val cheol: Boolean, val m: Double, val mul: String, val goga: Boolean, val pt: List<Pair<Double, Double>>)
    private class Sim(val t: String, val n: String, val la: Double, val lo: Double)

    var kyeojim = false
        private set
    private var dari: List<Dari> = emptyList()
    private var sim: List<Sim> = emptyList()
    private var jari: Pair<Double, Double>? = null
    private var batneun = false
    private var batTtae = 0L
    private val han = HashMap<String, Long>()
    private var deunKey: String? = null
    private var deunD: Dari? = null
    private var deunTtae = 0L
    private var head: Double? = null
    private var headFrom: Pair<Double, Double>? = null
    private var gogang = ""
    private var gogangTtae = 0L
    private var gogangMutneun = false

    fun kkeut() {
        if (!kyeojim) return
        kyeojim = false
        dari = emptyList(); sim = emptyList(); jari = null; han.clear(); deunKey = null; deunD = null; head = null; headFrom = null
        gogang = ""; gogangTtae = 0L
    }

    /** AnnaeEngine.chaAnnae 가 자리마다 부름. cheol = 기차·지하철 */
    fun wichi(w: Jari, cheol: Boolean) {
        if (!AnnaeSeoljeong.jidoOn || w.jjimjak) return
        kyeojim = true
        bangBoda(w)
        val j = jari
        if ((dari.isEmpty() && sim.isEmpty()) || j == null || geori(j.first, j.second, w.lat, w.lon) > 1500) batgi(w.lat, w.lon)
        dariSalpigi(w, cheol)
        if (!cheol) simSalpigi(w)
        gogangSalpigi(w)
    }

    private fun mal(t: String) {
        Sori.mal(t, MalGeup.ANNAE)
        Girok.namgi("jido_mal", mapOf("mal" to t))
    }
    private fun geori(a1: Double, o1: Double, a2: Double, o2: Double) = Wichi.geori(a1, o1, a2, o2)
    private fun bangwi(a1: Double, o1: Double, a2: Double, o2: Double) = Jeomjido.bangwi(a1, o1, a2, o2)
    private fun gakCha(a: Double, b: Double): Double { val d = Math.abs(a - b) % 360; return if (d > 180) 360 - d else d }
    private fun f5(v: Double) = String.format(Locale.US, "%.5f", v)

    private fun bangBoda(w: Jari) {
        if (w.jinhaeng >= 0 && w.sokdo >= 3) { head = w.jinhaeng; headFrom = Pair(w.lat, w.lon); return }
        val f = headFrom
        if (f == null) { headFrom = Pair(w.lat, w.lon); return }
        if (geori(f.first, f.second, w.lat, w.lon) >= 20) { head = bangwi(f.first, f.second, w.lat, w.lon); headFrom = Pair(w.lat, w.lon) }
    }

    private fun batgi(la: Double, lo: Double) {
        val now = System.currentTimeMillis()
        if (batneun || now - batTtae < 20_000) return
        batneun = true; batTtae = now
        Tongsin.json("jido.php", mapOf("gal" to "cha", "lat" to f5(la), "lon" to f5(lo), "ban" to "3000"), 20000) { o ->
            batneun = false
            if (o == null) return@json
            val l = ArrayList<Dari>()
            val rows = o.optJSONArray("dari")
            if (rows != null) for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val pa = r.optJSONArray("p") ?: continue
                val pt = ArrayList<Pair<Double, Double>>()
                for (k in 0 until pa.length()) { val p = pa.optJSONArray(k) ?: continue; if (p.length() >= 2) pt.add(Pair(p.optDouble(0), p.optDouble(1))) }
                if (pt.size < 2) continue
                l.add(Dari(r.optString("n"), r.optString("g"), r.optString("k") == "cheol", Math.max(r.optDouble("m", 0.0), r.optDouble("mm", 0.0)), r.optString("mul"), r.optInt("v", 0) == 1, pt))
            }
            val s = ArrayList<Sim>()
            val sa = o.optJSONArray("sim")
            if (sa != null) for (i in 0 until sa.length()) {
                val r = sa.optJSONObject(i) ?: continue
                if (!r.has("a") || !r.has("o")) continue
                s.add(Sim(r.optString("t"), r.optString("n"), r.optDouble("a"), r.optDouble("o")))
            }
            dari = l; sim = s; jari = Pair(la, lo)
        }
    }

    // ① 다리

    private fun tomak(la: Double, lo: Double, p1: Pair<Double, Double>, p2: Pair<Double, Double>): Double {
        val K = 111320.0; val co = Math.cos(la * Math.PI / 180)
        val x = (lo - p1.second) * K * co; val y = (la - p1.first) * K
        val bx = (p2.second - p1.second) * K * co; val by = (p2.first - p1.first) * K
        val nn = bx * bx + by * by
        val t = if (nn > 0) Math.max(0.0, Math.min(1.0, (x * bx + y * by) / nn)) else 0.0
        val dx = x - bx * t; val dy = y - by * t
        return Math.sqrt(dx * dx + dy * dy)
    }
    private fun julBang(la: Double, lo: Double, pt: List<Pair<Double, Double>>): Pair<Double, Double> {
        var m = 1e9; var b = 0.0
        for (i in 1 until pt.size) {
            val g = tomak(la, lo, pt[i - 1], pt[i])
            if (g < m) { m = g; b = bangwi(pt[i - 1].first, pt[i - 1].second, pt[i].first, pt[i].second) }
        }
        return Pair(m, b)
    }
    fun giriMal(m: Double): String {
        if (m >= 1000) { val k = Math.round(m / 100) / 10.0; return "길이는 약 ${if (k == Math.floor(k)) k.toInt().toString() else String.format(Locale.US, "%.1f", k)}킬로미터입니다." }
        return "길이는 약 ${Math.max(10L, Math.round(m / 10) * 10)}미터입니다."
    }
    /** 받침이 있으면 「은」, 없으면 「는」 */
    fun eun(s: String): String {
        val c = s.lastOrNull() ?: return s
        if (c.code < 0xAC00 || c.code > 0xD7A3) return s + "은"
        return s + if ((c.code - 0xAC00) % 28 == 0) "는" else "은"
    }
    /** 받침이 있으면 「을」, 없으면 「를」 */
    fun eul(s: String): String {
        val c = s.lastOrNull() ?: return s
        if (c.code < 0xAC00 || c.code > 0xD7A3) return s + "을"
        return s + if ((c.code - 0xAC00) % 28 == 0) "를" else "을"
    }
    private fun malHalMan(d: Dari): Boolean = when {
        d.ireum.isNotEmpty() -> d.m >= 30
        d.mul.isNotEmpty() -> d.m >= 20
        else -> d.m >= 300
    }

    private fun dariSalpigi(w: Jari, cheol: Boolean) {
        if (dari.isEmpty()) return
        val s = Math.max(0.0, w.sokdo)
        val munteok = if (s < 3) 80.0 else Math.min(1500.0, Math.max(150.0, s * 15))
        class Ap(val d: Dari, val de: Double, val key: String)
        val ap = ArrayList<Ap>()
        for (r in dari) {
            if (r.cheol != cheol || !malHalMan(r)) continue
            val (jul, jb) = julBang(w.lat, w.lon, r.pt)
            val a = r.pt[0]; val b = r.pt[r.pt.size - 1]
            val da = geori(w.lat, w.lon, a.first, a.second); val db = geori(w.lat, w.lon, b.first, b.second)
            val mun = if (da <= db) a else b; val de = Math.min(da, db)
            val key = "${r.ireum}|${r.mul}|${String.format(Locale.US, "%.4f,%.4f", mun.first, mun.second)}"
            if (jul < 20) {
                val h = head
                if (h != null && gakCha(h, jb) > 40 && gakCha(h, jb + 180) > 40) continue
                val dk = deunKey; val dd = deunD
                if (dk == null || dd == null || (dk != key && (r.ireum.isEmpty() || dd.ireum != r.ireum))) { deunKey = key; deunD = r; deunTtae = System.currentTimeMillis() }
                continue
            }
            val h = head ?: continue
            if (gakCha(h, bangwi(w.lat, w.lon, mun.first, mun.second)) >= 60) continue
            val an = if (da <= db) r.pt[1] else r.pt[r.pt.size - 2]
            val anjjok = bangwi(mun.first, mun.second, an.first, an.second)
            if (gakCha(h, anjjok) > 40) continue
            val yeop = de * Math.sin(gakCha(bangwi(w.lat, w.lon, mun.first, mun.second), anjjok) * Math.PI / 180)
            if (yeop > 35) continue
            if (de > munteok) continue
            ap.add(Ap(r, de, key))
        }
        val dd = deunD
        if (dd != null) {
            val (jul, _) = julBang(w.lat, w.lon, dd.pt)
            if (jul > 60) {
                deunKey = null; deunD = null
                if (System.currentTimeMillis() - deunTtae > 4000 && dd.m >= 60) {
                    mal(if (dd.mul.isEmpty()) "다리를 건넜습니다." else "${eul(dd.mul)} 건넜습니다.")
                }
            }
        }
        if (ap.isEmpty()) return
        ap.sortBy { it.de }
        val ga = ap[0]
        if (han.containsKey(ga.key)) return
        val now = System.currentTimeMillis()
        // 섬·이음매로 끊긴 한 다리를 두 번 알리지 않게 — 같은 이름은 3분 안에 다시 말하지 않음
        val ti = han["ireum|" + ga.d.ireum]
        if (ga.d.ireum.isNotEmpty() && ti != null && now - ti < 180_000) { han[ga.key] = now; return }
        if (ga.d.ireum.isNotEmpty()) han["ireum|" + ga.d.ireum] = now
        for (k in ap) if (k.d.ireum == ga.d.ireum && k.d.mul == ga.d.mul && k.de - ga.de < 200) han[k.key] = now   // 오가는 두 다리는 하나로
        han[ga.key] = now
        mal(dariMal(ga.d.ireum, ga.d.mul, ga.d.m, cheol, ga.d.goga))
    }

    fun dariMal(ireum: String, mul: String, m: Double, cheol: Boolean, goga: Boolean): String {
        var t = when {
            ireum.isNotEmpty() -> "곧 ${ireum}입니다."
            mul.isNotEmpty() -> if (cheol) "곧 철교입니다." else "곧 다리입니다."
            else -> if (goga) "곧 긴 고가 다리입니다." else if (cheol) "곧 긴 철교입니다." else "곧 긴 다리입니다."
        }
        if (mul.isNotEmpty()) t += " ${eul(mul)} 건넙니다."
        return t + " " + giriMal(m)
    }

    // ② 고속도로 곁 — 휴게소·졸음쉼터·나들목·분기점·요금소

    fun simIreum(t: String, n0: String): String {
        var n = n0.trim()
        fun bakkum(a: String, b: String) { if (n.uppercase(Locale.ROOT).endsWith(a)) n = n.dropLast(a.length).trim() + " " + b }
        when (t) {
            "hyu" -> if (!n.contains("휴게소")) n += " 휴게소"
            "swim" -> if (n.isEmpty()) n = "졸음쉼터" else if (!n.contains("쉼터") && !n.contains("휴게")) n += " 쉼터"
            "nadeul" -> {
                bakkum("IC", "나들목"); bakkum("JCT", "분기점"); bakkum("JC", "분기점")
                if (!n.contains("나들목") && !n.contains("분기점")) n += " 나들목"
            }
            "yogeum" -> {
                bakkum("TG", "요금소")
                if (!n.contains("요금소") && !n.contains("톨게이트")) n += " 요금소"
            }
        }
        return n
    }

    private fun simSalpigi(w: Jari) {
        val h = head ?: return
        if (w.sokdo < 14 || sim.isEmpty()) return
        val s = w.sokdo
        val now = System.currentTimeMillis()
        var ga: Sim? = null; var gaDe = 0.0; var gaKey = ""
        for (p in sim) {
            val de = geori(w.lat, w.lon, p.la, p.lo)
            val apm = if (p.t == "hyu" || p.t == "swim") Math.min(2000.0, Math.max(1000.0, s * 60)) else Math.min(1500.0, Math.max(600.0, s * 30))
            if (de > apm || de < 60) continue
            if (gakCha(h, bangwi(w.lat, w.lon, p.la, p.lo)) > 25) continue
            val key = p.t + "|" + simIreum(p.t, p.n)
            val t = han[key]
            if (t != null && now - t < 900_000) continue
            if (ga == null || de < gaDe) { ga = p; gaDe = de; gaKey = key }
        }
        val p = ga ?: return
        han[gaKey] = now
        val ireum = simIreum(p.t, p.n)
        val gm = if (gaDe >= 1000) { val k = Math.round(gaDe / 100) / 10.0; (if (k == Math.floor(k)) k.toInt().toString() else String.format(Locale.US, "%.1f", k)) + "킬로미터" } else "${Math.round(gaDe / 100) * 100}미터"
        if (p.t == "hyu" || p.t == "swim") mal("$gm 앞에 ${ireum}가 있습니다.") else mal("곧 ${ireum}입니다.")
    }

    // ③ 고장 경계

    private fun gogangSalpigi(w: Jari) {
        val now = System.currentTimeMillis()
        if (gogangMutneun || now - gogangTtae < 45_000) return
        gogangMutneun = true; gogangTtae = now
        Tongsin.json("chatta.php", mapOf("a" to "gil", "lat" to f5(w.lat), "lon" to f5(w.lon))) { o ->
            gogangMutneun = false
            val ireum = gogangIreum(o?.optString("si")?.trim() ?: "", o?.optString("gu")?.trim() ?: "")
            if (ireum.isEmpty()) return@json
            if (gogang.isNotEmpty() && ireum != gogang) mal("${ireum}에 들어섰습니다.")
            gogang = ireum
        }
    }
    /** 「서울특별시」·「경기도」 + 「강남구」·「여주시」 → 「서울 강남구」, 「경기도 여주시」 */
    fun gogangIreum(si: String, gu: String): String {
        // 카카오는 「서울」·「경기」·「충북」처럼 줄여 줌 — 말로 들을 때 알기 쉬운 이름으로
        val jul = mapOf("경기" to "경기도", "강원" to "강원도", "강원특별자치도" to "강원도", "충북" to "충청북도", "충남" to "충청남도",
            "전북" to "전라북도", "전북특별자치도" to "전라북도", "전남" to "전라남도", "경북" to "경상북도", "경남" to "경상남도",
            "제주" to "제주도", "제주특별자치도" to "제주도", "서울특별시" to "서울", "부산광역시" to "부산", "대구광역시" to "대구",
            "인천광역시" to "인천", "광주광역시" to "광주", "대전광역시" to "대전", "울산광역시" to "울산", "세종특별자치시" to "세종")
        val a = jul[si] ?: si
        if (gu.isEmpty()) return a
        if (a.isEmpty()) return gu
        return if (a == gu) a else "$a $gu"
    }
}
