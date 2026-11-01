// 걸을 때 지도 알림 — 2.88.0 (261101-A76, 이사장님 지시 2026-11-01, 아이폰 2.123.0 JidoGeot.swift 와 같음) — 시험 중
// 오픈스트리트맵(협회 서버 jido.php gal=geot)에서 걸을 때 쓸모 있는 것을 알려 드림.
//   길: 계단(오르는지 내리는지·계단 수·손잡이 쪽)·육교·지하도·경사로·보도 없는 길
//   건너기: 횡단보도(신호등·음향신호기·점자블록·턱 높이·가운데 섬)
//   시설: 엘리베이터·지하철 출입구·화장실·버스 정류장(지붕·의자)·건물 출입구, 「자세히」이면 음수대·의자도
// 방향은 늘 시계 방향, 뒤에 있는 것은 말하지 않고, 한 번 알린 것은 다시 말하지 않음. 말과 말 사이는 3초 넘게.
// 설정 탭 말하기 설정의 「걸을 때 지도 알림(시험 중)」을 켠 폰에서만 돎(처음 꺼짐).
package kr.or.ada.app.gilnun

import org.json.JSONObject
import java.util.Locale

object JidoGeot {
    private class Seon(val t: String, val n: String, val i: String, val su: Int, val son: String, val m: Double, val pt: List<Pair<Double, Double>>)
    private class Jeom(val t: String, val n: String, val la: Double, val lo: Double, val o: JSONObject)

    private var seon: List<Seon> = emptyList()
    private var jeom: List<Jeom> = emptyList()
    private var jari: Pair<Double, Double>? = null
    private var batneun = false
    private var batTtae = 0L
    private var deo = false
    private val han = HashSet<String>()
    private var malTtae = 0L
    private var head: Double? = null
    private var headFrom: Pair<Double, Double>? = null

    fun kkeut() { seon = emptyList(); jeom = emptyList(); jari = null; han.clear(); deo = false; head = null; headFrom = null }

    /** 걸을 때 자리마다 부름. bang = 앞쪽(도), 모르면 null */
    fun wichi(la: Double, lo: Double, bang: Double?) {
        if (!AnnaeSeoljeong.jidoGeotOn) return
        val f = headFrom
        if (bang != null) { head = bang; headFrom = Pair(la, lo) }
        else if (f != null) { if (Wichi.geori(f.first, f.second, la, lo) >= 6) { head = Jeomjido.bangwi(f.first, f.second, la, lo); headFrom = Pair(la, lo) } }
        else headFrom = Pair(la, lo)
        val j = jari
        if ((seon.isEmpty() && jeom.isEmpty()) || deo || j == null || Wichi.geori(j.first, j.second, la, lo) > 150) batgi(la, lo)
        salpigi(la, lo)
    }

    private fun f5(v: Double) = String.format(Locale.US, "%.5f", v)

    private fun batgi(la: Double, lo: Double) {
        val now = System.currentTimeMillis()
        if (batneun || now - batTtae < 20_000) return
        batneun = true; batTtae = now
        Tongsin.json("jido.php", mapOf("gal" to "geot", "lat" to f5(la), "lon" to f5(lo), "ban" to "300"), 50000) { o ->
            batneun = false
            if (o == null) return@json
            val ls = ArrayList<Seon>()
            val sa = o.optJSONArray("seon")
            if (sa != null) for (i in 0 until sa.length()) {
                val r = sa.optJSONObject(i) ?: continue
                val pa = r.optJSONArray("p") ?: continue
                val pt = ArrayList<Pair<Double, Double>>()
                for (k in 0 until pa.length()) { val p = pa.optJSONArray(k) ?: continue; if (p.length() >= 2) pt.add(Pair(p.optDouble(0), p.optDouble(1))) }
                if (pt.size < 2) continue
                ls.add(Seon(r.optString("t"), r.optString("n"), r.optString("i"), r.optInt("su", 0), r.optString("son"), r.optDouble("m", 0.0), pt))
            }
            val lj = ArrayList<Jeom>()
            val ja = o.optJSONArray("jeom")
            if (ja != null) for (i in 0 until ja.length()) {
                val r = ja.optJSONObject(i) ?: continue
                if (!r.has("a") || !r.has("o")) continue
                lj.add(Jeom(r.optString("t"), r.optString("n"), r.optDouble("a"), r.optDouble("o"), r))
            }
            seon = ls; jeom = lj; deo = o.optInt("deo", 0) == 1; jari = Pair(la, lo)
        }
    }

    private fun chai(a: Double, b: Double): Double { var x = (a - b) % 360; if (x > 180) x -= 360; if (x < -180) x += 360; return x }
    fun sigyeSi(rel: Double): Int { var s = Math.round(rel / 30).toInt(); s = ((s % 12) + 12) % 12; return if (s == 0) 12 else s }
    private fun geoMal(d: Double) = if (d < 3) "바로 앞" else "${Math.max(1L, Math.round(d / 5) * 5)}미터"

    private fun tomak(la: Double, lo: Double, p1: Pair<Double, Double>, p2: Pair<Double, Double>): Pair<Double, Pair<Double, Double>> {
        val K = 111320.0; val co = Math.cos(la * Math.PI / 180)
        val x = (lo - p1.second) * K * co; val y = (la - p1.first) * K
        val bx = (p2.second - p1.second) * K * co; val by = (p2.first - p1.first) * K
        val nn = bx * bx + by * by
        val t = if (nn > 0) Math.max(0.0, Math.min(1.0, (x * bx + y * by) / nn)) else 0.0
        val dx = x - bx * t; val dy = y - by * t
        return Pair(Math.sqrt(dx * dx + dy * dy), Pair(p1.first + (p2.first - p1.first) * t, p1.second + (p2.second - p1.second) * t))
    }
    private fun gakkaun(la: Double, lo: Double, pt: List<Pair<Double, Double>>): Pair<Double, Pair<Double, Double>> {
        var best = Pair(1e9, pt[0])
        for (i in 1 until pt.size) { val r = tomak(la, lo, pt[i - 1], pt[i]); if (r.first < best.first) best = r }
        return best
    }

    private class Hubo(val d: Double, val key: String, val mal: String)

    private fun salpigi(la: Double, lo: Double) {
        if (System.currentTimeMillis() - malTtae < 3000) return
        val jasehi = AnnaeSeoljeong.malSang >= 2
        val hubo = ArrayList<Hubo>()
        for (s in seon) {
            val key = "s|${s.t}|${s.pt[0].first},${s.pt[0].second}"
            if (han.contains(key)) continue
            val (d, p) = gakkaun(la, lo, s.pt)
            if (s.t == "bodo_eopseum") { if (d <= 8) hubo.add(Hubo(d, key, "보도가 없는 길입니다. 차를 조심하십시오.")); continue }
            if (d > 20) continue
            var si = 0
            val h = head
            if (h != null && d >= 3) {
                val rel = chai(Jeomjido.bangwi(la, lo, p.first, p.second), h)
                if (Math.abs(rel) > 60) continue
                si = sigyeSi(rel)
            }
            val ap = if (si > 0) "${si}시 방향 ${geoMal(d)} 앞에 " else "앞에 "
            val a = s.pt[0]; val b = s.pt[s.pt.size - 1]
            val apeuro = Wichi.geori(la, lo, a.first, a.second) <= Wichi.geori(la, lo, b.first, b.second)
            when (s.t) {
                "gyedan" -> {
                    val oreum = when (s.i) { "up" -> if (apeuro) "오르는 " else "내려가는 "; "down" -> if (apeuro) "내려가는 " else "오르는 "; else -> "" }
                    var t = ap + oreum + "계단입니다."
                    if (s.su > 0) t += " 계단은 ${s.su}개입니다."
                    t += sonMal(s.son, apeuro)
                    hubo.add(Hubo(d, key, t))
                }
                "yukgyo" -> hubo.add(Hubo(d, key, ap + (if (s.n.isEmpty()) "" else "${s.n} ") + "육교입니다." + (if (s.m >= 15) " 길이는 약 ${Math.round(s.m / 5) * 5}미터입니다." else "")))
                "jihado" -> hubo.add(Hubo(d, key, ap + (if (s.n.isEmpty()) "" else "${s.n} ") + "지하도입니다."))
                "gyeongsa" -> {
                    val oreum = when (s.i) { "up" -> if (apeuro) "오르막" else "내리막"; "down" -> if (apeuro) "내리막" else "오르막"; else -> "비탈" }
                    hubo.add(Hubo(d, key, ap + oreum + " 경사로입니다."))
                }
            }
        }
        for (j in jeom) {
            val key = "j|${j.t}|${j.la},${j.lo}"
            if (han.contains(key)) continue
            val d = Wichi.geori(la, lo, j.la, j.lo)
            if (d > (if (j.t == "geonneol") 15.0 else 25.0)) continue
            var si = 0
            val h = head
            if (h != null && d >= 3) {
                val rel = chai(Jeomjido.bangwi(la, lo, j.la, j.lo), h)
                if (Math.abs(rel) > (if (j.t == "geonneol") 50.0 else 110.0)) continue   // 뒤에 있는 것은 말하지 않음
                si = sigyeSi(rel)
            }
            val ap = if (si > 0) "${si}시 방향 ${geoMal(d)}에 " else "가까이 "
            val t = jeomMal(j.t, j.n, j.o, jasehi) ?: continue
            hubo.add(Hubo(d, key, ap + t))
        }
        val g = hubo.minByOrNull { it.d } ?: return
        han.add(g.key)
        malTtae = System.currentTimeMillis()
        Sori.mal(g.mal, MalGeup.JEONGBO)
        Girok.namgi("jido_geot_mal", mapOf("mal" to g.mal))
    }

    /** 손잡이 — 그린 쪽 기준 왼쪽·오른쪽을 걷는 쪽으로 돌려 시계 방향으로 */
    fun sonMal(son: String, apeuro: Boolean): String {
        if (son.isEmpty()) return ""
        val oen = son.contains("handrail:left"); val oreun = son.contains("handrail:right"); val gaunde = son.contains("handrail:center")
        if ((oen && oreun) || son == "handrail") return " 손잡이는 양쪽에 있습니다."
        val t = ArrayList<String>()
        if (oen) t.add(if (apeuro) "9시 방향" else "3시 방향")
        if (oreun) t.add(if (apeuro) "3시 방향" else "9시 방향")
        if (gaunde) t.add("가운데")
        return if (t.isEmpty()) "" else " 손잡이는 ${t.joinToString("과 ")}에 있습니다."
    }

    fun jeomMal(t: String, n: String, o: JSONObject, jasehi: Boolean): String? {
        fun su(k: String) = o.optInt(k, 0)
        return when (t) {
            "geonneol" -> {
                // 2.88.0 (261102, 이사장님 승인 「1」) 40미터 안 공식 교차로 이름(국토교통부 표준노드링크)을 붙임
                val gy = o.optString("gyo").trim()
                var m = if (gy.isEmpty()) "횡단보도입니다." else "$gy 횡단보도입니다."
                if (su("sinho") == 1) m += " 신호등이 있습니다." else if (su("sinho") == -1) m += " 신호등이 없습니다."
                if (su("sori") == 1) m += " 음향신호기가 있습니다." else if (su("sori") == -1) m += " 음향신호기는 없습니다."
                if (su("jeomja") == 1) m += " 점자블록이 있습니다."
                when (o.optString("teok")) { "lowered" -> m += " 턱이 낮습니다."; "raised" -> m += " 턱이 높습니다."; "flush" -> m += " 턱이 없습니다." }
                if (su("seom") == 1) m += " 가운데에 쉬어 가는 섬이 있습니다."
                m
            }
            "elev" -> "엘리베이터가 있습니다."
            "chulgu" -> {
                val r = o.optString("r")
                var m = (if (n.isEmpty()) "지하철" else n) + (if (r.isEmpty()) " 출입구" else " ${r}번 출구") + "입니다."
                if (su("elev") == 1) m += " 엘리베이터로 오르내리는 출구입니다."
                m
            }
            "hwajangsil" -> if (su("jang") == 1) "장애인 화장실이 있는 화장실입니다." else "화장실이 있습니다."
            "jeongryu" -> {
                var m = (if (n.isEmpty()) "버스 정류장" else "$n 버스 정류장") + "입니다."
                if (su("jibung") == 1 && su("uija") == 1) m += " 지붕과 의자가 있습니다."
                else if (su("jibung") == 1) m += " 지붕이 있습니다." else if (su("uija") == 1) m += " 의자가 있습니다."
                if (su("jeomja") == 1) m += " 점자블록이 있습니다."
                m
            }
            "mun" -> {
                val mj = o.optString("mj")
                if (mj == "emergency" || mj == "exit") null else {
                    var m = (if (n.isEmpty()) "" else "$n ") + (if (mj == "main") "주출입구" else if (mj == "service") "뒷문" else "출입구") + "입니다."
                    when (o.optString("door")) { "automatic" -> m += " 자동문입니다."; "revolving" -> m += " 회전문입니다."; "sliding" -> m += " 미닫이문입니다."; "hinged" -> m += " 여닫이문입니다." }
                    if (su("jang") == 1) m += " 휠체어로 드나들 수 있습니다."
                    m
                }
            }
            "eumsu" -> if (jasehi) "음수대가 있습니다." else null
            "swim" -> if (jasehi) "앉아 쉴 의자가 있습니다." else null
            else -> null
        }
    }
}
