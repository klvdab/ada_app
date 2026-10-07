// 안드로이드 길눈 2.27.0(빌드 261007-A15, 대장클, 이사장님 허락 2026-10-07 22:45) — 차 안 길 맞춤
// 웹 길눈 chamat.js 1.0(빌드 261007-3)을 앱으로 옮김(이사장님 지시: 차가 제대로 가는지 알려 주고, 목적지 근처에서
// 「어떤 길, 어떤 건물에서 몇 시 방향으로 몇 미터」를 알려 드려 기사님께 말씀하실 수 있게).
//   안내 정도(말하기 설정 「차 안 안내 정도」): 1 간단 / 2 보통(처음 값) / 3 자세히.
//   길 벗어남 알림과 내리는 곳 안내는 간단에서도 나옴(이사장님 승인).
//   방향은 늘 차가 가는 쪽을 12시로 삼아 시계 방향으로 말함.
//   길(경로)은 협회 서버 gilmat.php(카카오 길찾기)에서 받음. 못 받으면 조용히 기존 남은 거리 안내만 나감.
//   승용차·택시(탈것 「차」)에서만 씀 — 버스·기차·고속버스는 세워 달라 할 수 없으므로 뺌.
package kr.or.ada.app.gilnun

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object ChaMat {
    private class Gil(
        val pts: List<DoubleArray>,
        val gil: List<String>,
        val guides: List<JSONObject>,
        val mun: JSONObject?,
        val mokGil: String,
        val mokGeonmul: String
    )

    private var gil: Gil? = null
    private var mokLat = 0.0
    private var mokLon = 0.0
    private var mokIreum = ""
    private var itda = false
    private var banEum = 0L
    private var bulTime = 0L
    private var bulSijak = 0L
    private var bulMal = 0L
    private var meolSijak = 0L
    private var meolGeori = 0.0
    private var meolMal = 0L
    private var seoSijak = 0L
    private var seoMal = 0L
    private val gdMal = HashMap<Int, String>()
    private var hacha500 = false
    private var hacha100 = false
    private var batneunJung = false
    private var chaBang = -1.0   // 차가 마지막으로 달리던 쪽(멈춰도 이 쪽을 12시로)

    /** 안내 정도 1 간단 · 2 보통 · 3 자세히 */
    var jeongdo: Int
        get() = AnnaeSeoljeong.chaJeongdo.coerceIn(1, 3)
        set(v) { AnnaeSeoljeong.chaJeongdo = v.coerceIn(1, 3) }
    val ireum: Array<String> = arrayOf("", "간단", "보통", "자세히")

    fun gatEun(lat: Double, lon: Double): Boolean = itda && abs(mokLat - lat) < 1e-7 && abs(mokLon - lon) < 1e-7

    fun sijak(lat: Double, lon: Double, ir: String) {
        mokLat = lat; mokLon = lon; mokIreum = ir; itda = true
        gil = null; bulTime = 0L; bulMal = 0L; bulSijak = 0L; hacha500 = false; hacha100 = false
        gdMal.clear(); meolSijak = 0L; seoSijak = 0L; batneunJung = false
    }

    fun kkeut() { itda = false; gil = null; chaBang = -1.0 }

    // MARK: 말 거리
    private fun dist(a1: Double, o1: Double, a2: Double, o2: Double) = Wichi.geori(a1, o1, a2, o2)
    private fun bearing(a1: Double, o1: Double, a2: Double, o2: Double) = Jeomjido.bangwi(a1, o1, a2, o2)
    private fun sigye(head: Double, b: Double): String {
        if (head < 0) return ""
        return "${Jeomjido.sigyeBanghyang(head, b)}시 방향"
    }
    private fun mMal(m: Double): String {
        if (m >= 1000) {
            val k = (m / 100).roundToInt() / 10.0
            return if (k == Math.floor(k)) "${k.toInt()}킬로미터" else "${k}킬로미터"
        }
        if (m >= 100) return "${(m / 50).roundToInt() * 50}미터"
        return "${max(10, (m / 10).roundToInt() * 10)}미터"
    }
    /** 받침에 따라 조사 고르기 */
    private fun bat(w: String): Int {
        if (w.isEmpty()) return 0
        val c = w.last()
        if (c < '가' || c > '힣') return if (c.isDigit()) 1 else 0
        return (c - '가') % 28
    }
    private fun eun(w: String) = w + (if (bat(w) != 0) "은" else "는")
    private fun eul(w: String) = w + (if (bat(w) != 0) "을" else "를")
    private fun ro(w: String): String { val b = bat(w); return w + (if (b != 0 && b != 8) "으로" else "로") }
    private fun wichi(head: Double, la: Double, lo: Double, pa: Double, po: Double, m: Double): String {
        val b = sigye(head, bearing(la, lo, pa, po))
        return (if (b.isNotEmpty()) "$b " else "") + mMal(m)
    }
    private fun mal(x: String, kkok: Boolean = false, malHagi: (String) -> Unit) {
        val t = System.currentTimeMillis()
        if (!kkok && t - banEum < 6000) return
        banEum = t
        malHagi(x)
    }
    private fun mokNm() = if (mokIreum.isEmpty()) "목적지" else mokIreum

    // MARK: 길 위치
    /** 점에서 길(선분들)까지 가장 가까운 거리와 그 자리의 순번 */
    private fun gilGeori(g: Gil, la: Double, lo: Double): Pair<Double, Int> {
        val p = g.pts
        var best = 1e9
        var bi = 0
        for (i in 0 until p.size - 1) {
            val ax = (p[i][1] - lo) * 88000; val ay = (p[i][0] - la) * 111000
            val bx = (p[i + 1][1] - lo) * 88000; val by = (p[i + 1][0] - la) * 111000
            val dx = bx - ax; val dy = by - ay; val l = dx * dx + dy * dy
            val tt = if (l > 0) max(0.0, min(1.0, -(ax * dx + ay * dy) / l)) else 0.0
            val cx = ax + tt * dx; val cy = ay + tt * dy
            val d = sqrt(cx * cx + cy * cy)
            if (d < best) { best = d; bi = i }
        }
        return Pair(best, bi)
    }
    /** 길을 따라 남은 거리 */
    private fun namGil(g: Gil, i: Int, la: Double, lo: Double): Double {
        val p = g.pts
        var s = 0.0
        if (i + 1 < p.size) s += dist(la, lo, p[i + 1][0], p[i + 1][1])
        for (k in i + 1 until p.size - 1) s += dist(p[k][0], p[k][1], p[k + 1][0], p[k + 1][1])
        return s
    }

    private fun gilBatgi(la: Double, lo: Double, saero: Boolean = false) {
        if (!itda || batneunJung) return
        val t = System.currentTimeMillis()
        if (t - bulTime < 45000 && !saero) return
        bulTime = t
        batneunJung = true
        val q = mapOf(
            "la1" to String.format(java.util.Locale.US, "%.5f", la), "lo1" to String.format(java.util.Locale.US, "%.5f", lo),
            "la2" to String.format(java.util.Locale.US, "%.5f", mokLat), "lo2" to String.format(java.util.Locale.US, "%.5f", mokLon)
        )
        val mok0 = Pair(mokLat, mokLon)
        Tongsin.json("gilmat.php", q, 10000) { o ->
            batneunJung = false
            if (o == null || !o.optBoolean("ok") || !itda || mok0 != Pair(mokLat, mokLon)) return@json
            val pa = o.optJSONArray("pts") ?: return@json
            val pts = ArrayList<DoubleArray>()
            for (i in 0 until pa.length()) { val a = pa.optJSONArray(i) ?: continue; pts.add(doubleArrayOf(a.optDouble(0), a.optDouble(1))) }
            if (pts.size < 2) return@json
            gil = Gil(pts, strs(o.optJSONArray("gil")), objs(o.optJSONArray("guides")), o.optJSONObject("mun"),
                o.optString("mokGil", ""), o.optString("mokGeonmul", ""))
            gdMal.clear()
            Girok.namgi("chamat_gil", mapOf("m" to o.optInt("meter")))
        }
    }
    private fun strs(a: JSONArray?): List<String> { val l = ArrayList<String>(); if (a != null) for (i in 0 until a.length()) l.add(a.optString(i, "")); return l }
    private fun objs(a: JSONArray?): List<JSONObject> { val l = ArrayList<JSONObject>(); if (a != null) for (i in 0 until a.length()) a.optJSONObject(i)?.let { l.add(it) }; return l }

    /** 꺾는 곳에서 몇 시 방향으로 꺾는지 — 길 모양(들어오는 쪽과 나가는 쪽)으로 셈 */
    private fun kkeokBang(g: Gil, ga: Double, go: Double): String {
        val p = g.pts
        var bi = 0
        var bd = 1e9
        for (i in p.indices) { val d = dist(ga, go, p[i][0], p[i][1]); if (d < bd) { bd = d; bi = i } }
        var a: DoubleArray? = null
        var b: DoubleArray? = null
        for (j in bi - 1 downTo 0) if (dist(p[j][0], p[j][1], ga, go) >= 25) { a = p[j]; break }
        for (j in bi + 1 until p.size) if (dist(p[j][0], p[j][1], ga, go) >= 25) { b = p[j]; break }
        if (a == null || b == null) return ""
        return sigye(bearing(a[0], a[1], ga, go), bearing(ga, go, b[0], b[1]))
    }

    private fun munJari(g: Gil): Pair<Double, Double> {
        val m = g.mun
        if (m != null && m.has("lat") && m.has("lon")) return Pair(m.optDouble("lat"), m.optDouble("lon"))
        return Pair(mokLat, mokLon)
    }

    /** 목적지 근처 내리는 곳 안내 한 덩어리 */
    private fun hachaMal(g: Gil, la: Double, lo: Double, head: Double, gi: Int): String {
        val jigeumGil = g.gil.getOrNull(gi) ?: ""
        val namM = namGil(g, gi, la, lo)
        val mp = munJari(g)
        val bang = sigye(head, bearing(la, lo, mp.first, mp.second))
        val s = ArrayList<String>()
        if (jigeumGil.isNotEmpty()) s.add("지금 ${eul(jigeumGil)} 달리고 있습니다.")
        val nm = mokNm()
        val gilBit = if (g.mokGil.isNotEmpty() && g.mokGil != jigeumGil) " ${ro(g.mokGil)} 들어가" else " 이 길을 따라"
        s.add("${eun(nm)}$gilBit 약 ${mMal(namM)} 가면 있습니다.")
        if (bang.isNotEmpty()) s.add("지금 자리에서 보면 ${bang}입니다.")
        // 마지막 꺾는 곳이 아직 앞에 있으면 함께 말함
        val k = g.guides.size - 1
        if (k >= 0 && !gdMal.containsKey(k)) {
            val gd = g.guides[k]
            val ga = gd.optDouble("lat"); val go = gd.optDouble("lon")
            val dg = dist(la, lo, ga, go)
            val bb = kkeokBang(g, ga, go)
            if (dg < namM && bb.isNotEmpty() && bb != "12시 방향") {
                gdMal[k] = "h"
                val gm = gd.optString("geonmul", "")
                s.add("약 ${mMal(dg)} 앞 " + (if (gm.isNotEmpty()) "$gm 앞 " else "") + "꺾는 곳에서 ${ro(bb)} 꺾어야 합니다.")
            }
        }
        if (g.mokGeonmul.isNotEmpty() && !g.mokGeonmul.contains(nm) && !nm.contains(g.mokGeonmul)) s.add("건물 이름은 ${g.mokGeonmul}입니다.")
        val munGil = g.mun?.optString("gil", "") ?: ""
        if (munGil.isNotEmpty()) s.add("문은 $munGil 쪽에 있습니다.")
        else if (g.mokGil.isNotEmpty()) s.add("건물은 ${g.mokGil}에 붙어 있습니다.")
        return s.joinToString(" ")
    }

    /** 차 안 안내가 위치를 받을 때마다 부름(AnnaeEngine.chaAnnae). 말했으면 참 */
    fun salpim(w: Jari, malHagi: (String) -> Unit): Boolean {
        if (!itda) return false
        val la = w.lat; val lo = w.lon
        val sokM = w.sokdo
        if (w.banghyang >= 0 && sokM > 1.5) chaBang = w.banghyang
        val head = if (chaBang >= 0) chaBang else -1.0
        val lv = jeongdo
        val t = System.currentTimeMillis()
        val dMok = dist(la, lo, mokLat, mokLon)
        val g = gil
        if (g == null) gilBatgi(la, lo)

        if (g != null) {
            val (gd0, gi) = gilGeori(g, la, lo)
            val namM = namGil(g, gi, la, lo)
            // 내리는 곳 안내 — 모든 단계
            if (!hacha500 && namM <= 600 && dMok > 120) { hacha500 = true; mal(hachaMal(g, la, lo, head, gi), true, malHagi); return true }
            if (!hacha100 && (namM <= 100 || dMok <= 80)) {
                hacha100 = true
                val mp = munJari(g)
                val bang = sigye(head, bearing(la, lo, mp.first, mp.second))
                mal("곧 " + (if (bang.isNotEmpty()) "${bang}에 " else "") + mokNm() + "입니다. 여기서 세워 달라고 하십시오.", true, malHagi)
                return true
            }
            // 길 벗어남 — 모든 단계. 100미터 넘게 15초
            if (gd0 > 100 && sokM > 2) {
                if (bulSijak == 0L) bulSijak = t
                if (t - bulSijak > 15000 && t - bulMal > 60000) {
                    bulMal = t
                    bulSijak = 0L
                    mal("길에서 벗어났습니다. ${eun(mokNm())} 지금 ${wichi(head, la, lo, mokLat, mokLon, dMok)}입니다.", true, malHagi)
                    gilBatgi(la, lo, true)
                    return true
                }
            } else bulSijak = 0L
            // 꺾을 곳 미리 알림 — 보통은 마지막 꺾는 곳만, 자세히는 꺾는 곳마다
            val gds = g.guides
            for (k in gds.indices) {
                if (lv < 2) break
                val majimak = k == gds.size - 1
                if (lv == 2 && !majimak) continue
                val ga = gds[k].optDouble("lat"); val go = gds[k].optDouble("lon")
                val dg = dist(la, lo, ga, go)
                val ap = max(120.0, min(300.0, (if (sokM > 0) sokM else 10.0) * 12))
                val st = gdMal[k]
                if (dg < ap && dg > 25 && (st == null || st == "h") && t - banEum >= 6000) {
                    val bb = kkeokBang(g, ga, go)
                    gdMal[k] = "1"
                    if (bb.isNotEmpty() && bb != "12시 방향") {
                        val gm = gds[k].optString("geonmul", "")
                        val ir = gds[k].optString("ireum", "")
                        val ap2 = if (gm.isNotEmpty()) "$gm 앞 " else if (ir.isNotEmpty()) "$ir 쪽 " else ""
                        mal("다음 ${ap2}꺾는 곳에서 ${ro(bb)} 꺾어야 " + (if (majimak) "목적지 쪽입니다." else "길대로 갑니다."), false, malHagi)
                        return true
                    }
                }
                if (lv == 3 && dg <= 25 && gdMal[k] == "1" && gd0 < 40) { gdMal[k] = "2"; mal("길대로 가고 있습니다.", false, malHagi); return true }
            }
        }

        // 멀어짐 — 보통 이상. 30초 넘게 거리가 200미터 넘게 늘면
        if (lv >= 2) {
            if (meolSijak == 0L || dMok < meolGeori) { meolSijak = t; meolGeori = dMok }
            else if (t - meolSijak > 30000 && dMok - meolGeori > 200 && t - meolMal > 90000) {
                meolMal = t; meolSijak = t; meolGeori = dMok
                mal("목적지에서 멀어지고 있습니다. ${eun(mokNm())} 지금 ${wichi(head, la, lo, mokLat, mokLon, dMok)}입니다.", true, malHagi)
                return true
            }
        }

        // 오래 서 있음 — 자세히만. 꺾는 곳 40미터 안(신호 대기)은 빼고 1분
        if (lv == 3) {
            if (sokM < 1) {
                if (seoSijak == 0L) seoSijak = t
                val sagori = g?.guides?.any { dist(la, lo, it.optDouble("lat"), it.optDouble("lon")) < 40 } ?: false
                if (!sagori && t - seoSijak > 60000 && t - seoMal > 120000) {
                    seoMal = t
                    mal("${((t - seoSijak) / 60000.0).roundToInt()}분째 서 있습니다. ${eun(mokNm())} ${wichi(head, la, lo, mokLat, mokLon, dMok)}입니다.", false, malHagi)
                    return true
                }
            } else seoSijak = 0L
        }
        return false
    }
}
