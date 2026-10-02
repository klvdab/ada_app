// 안드로이드 길눈 — 점지도 자료(2.2.0, 빌드 261002-A4, 대표님 지시: 점지도 따라 걷기를 안드로이드에도)
// 아이폰 길눈 Jeomjido.swift 를 그대로 옮겼습니다. 웹 길눈의 점지도 창고(jeom.php)를 함께 씁니다.
//   a=find&lat&lon  가까운 길부터      a=get&id  한 길의 점과 표시
// 되돌아가는 길(dwit) — 점을 거꾸로, 표시 이름도 거꾸로(오른쪽 꺾임 ↔ 왼쪽 꺾임 등),
//   계단과 건널목은 시작과 끝을 짝지어 바꿈(거꾸로 걸으면 끝이 시작이 됨). 아이폰 2.12.0 과 같은 셈.
// TODO(아이폰 NaeGil·NaeMun): 나만의 점지도(nae_)와 내 문은 다음 판에 옮김 — 지금은 협회 점지도만 불러옵니다.
// TODO(아이폰 Jeomjido.ieumMok·matneunGil): 여러 점지도 이어 걷기, 목적지에 맞는 점지도 찾기는 다음 판.
package kr.or.ada.app.gilnun

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/** 점지도의 점 하나 */
data class JeomJeom(val lat: Double, val lon: Double, val acc: Double?, val h: Double?, val t: Double?)

/** 점지도의 표시 하나(계단, 건널목, 꺾임 등) */
data class JeomPyo(
    var lat: Double?,
    var lon: Double?,
    var name: String?,
    var kind: String?,
    var cnt: Int?,
    var mal: String?,
    var t: Double?,
    var acc: Double?,
    var dist: Double?
) {
    val ireum: String
        get() {
            val n = (name ?: "").trim()
            if (n.isNotEmpty()) return n
            val k = (kind ?: "").trim()
            return if (k.isEmpty()) "표시" else k
        }
}

/** 점지도 한 길 */
data class JeomGil(
    val id: String,
    val title: String,
    val from: String,
    val to: String,
    val who: String,
    val made: String,
    val dist: Double,
    val pts: List<JeomJeom>,
    val marks: List<JeomPyo>
) {
    /** 되돌아가는 길 — 점을 거꾸로, 표시 이름도 거꾸로(오른쪽 꺾임 ↔ 왼쪽 꺾임 등) */
    fun dwit(): JeomGil {
        val out = ArrayList<JeomPyo>()
        for (m in marks) {
            val x = m.copy()
            m.name?.let { x.name = DWIT[it] ?: it }
            m.kind?.let { x.kind = DWIT[it] ?: it }
            out.add(x)
        }
        // 2.12.0(아이폰) 계단과 건널목은 시작과 끝을 짝지어 바꿈 — 거꾸로 걸으면 끝이 시작이 됨
        fun jari(m: JeomPyo): Int {
            val la = m.lat
            val lo = m.lon
            if (la == null || lo == null || la == 0.0) return -1
            var b = -1
            var bd = 1e9
            for ((k, p) in pts.withIndex()) {
                val d = Wichi.geori(la, lo, p.lat, p.lon)
                if (d < bd) { bd = d; b = k }
            }
            return b
        }
        val ix = marks.map { jari(it) }
        fun gyedan(n: String) = n.contains("계단")
        fun geonneol(n: String) = n.contains("횡단보도") || n.contains("건널목")
        val sseun = HashSet<Int>()
        for ((n, m) in marks.withIndex()) {
            val nm = m.ireum
            val gy = gyedan(nm)
            val gn = geonneol(nm)
            if (!(gy || gn) || nm.contains("끝") || ix[n] < 0) continue
            var e: Int? = null
            for ((k, q) in marks.withIndex()) {
                if (k == n || sseun.contains(k) || ix[k] < ix[n] || !q.ireum.contains("끝")) continue
                val qn = q.ireum
                if (!(if (gy) gyedan(qn) else geonneol(qn))) continue
                val ee = e
                if (ee == null || ix[k] < ix[ee]) e = k
            }
            val ek = e ?: continue
            sseun.add(ek); sseun.add(n)
            if (gy) {
                out[ek].name = DWIT[nm] ?: nm
                out[ek].cnt = m.cnt
                out[n].name = "계단 끝"
                out[n].cnt = null
            } else {
                out[ek].name = nm
                out[ek].dist = m.dist
                out[n].name = marks[ek].ireum
            }
        }
        // 짝이 없는 건널목 끝은 거꾸로 걸으면 건너기 시작
        for ((n, m) in marks.withIndex()) {
            if (sseun.contains(n) || !geonneol(m.ireum) || !m.ireum.contains("끝")) continue
            out[n].name = m.ireum.replace("끝", "시작")
        }
        return copy(pts = pts.reversed(), marks = out.reversed(), from = to, to = from)
    }

    companion object {
        val DWIT: Map<String, String> = mapOf(
            "왼쪽으로 꺾임" to "오른쪽으로 꺾임", "오른쪽으로 꺾임" to "왼쪽으로 꺾임",
            "올라가는 계단 시작" to "내려가는 계단 시작", "내려가는 계단 시작" to "올라가는 계단 시작",
            "오름턱" to "내림턱", "내림턱" to "오름턱",
            "횡단보도 건너기 시작" to "횡단보도 건너기 끝", "횡단보도 건너기 끝" to "횡단보도 건너기 시작",
            "엘리베이터 올라감" to "엘리베이터 내려감", "엘리베이터 내려감" to "엘리베이터 올라감"
        )

        fun batgi(o: JSONObject): JeomGil {
            val pts = ArrayList<JeomJeom>()
            val pa: JSONArray? = o.optJSONArray("pts")
            if (pa != null) for (i in 0 until pa.length()) {
                val p = pa.optJSONObject(i) ?: continue
                val la = Jeomjido.su(p, "lat") ?: continue
                val lo = Jeomjido.su(p, "lon") ?: continue
                if (la == 0.0 || lo == 0.0) continue
                pts.add(JeomJeom(la, lo, Jeomjido.su(p, "acc"), Jeomjido.su(p, "h"), Jeomjido.su(p, "t")))
            }
            val marks = ArrayList<JeomPyo>()
            val ma: JSONArray? = o.optJSONArray("marks")
            if (ma != null) for (i in 0 until ma.length()) {
                val m = ma.optJSONObject(i) ?: continue
                marks.add(JeomPyo(
                    Jeomjido.su(m, "lat"), Jeomjido.su(m, "lon"),
                    Jeomjido.geulOrNull(m, "name"), Jeomjido.geulOrNull(m, "kind"),
                    Jeomjido.su(m, "cnt")?.toInt(), Jeomjido.geulOrNull(m, "mal"),
                    Jeomjido.su(m, "t"), Jeomjido.su(m, "acc"), Jeomjido.su(m, "dist")
                ))
            }
            return JeomGil(
                Jeomjido.gul(o, "id"), Jeomjido.gul(o, "title"), Jeomjido.gul(o, "from"), Jeomjido.gul(o, "to"),
                Jeomjido.gul(o, "who"), Jeomjido.gul(o, "made"), Jeomjido.su(o, "dist") ?: 0.0, pts, marks
            )
        }
    }
}

/** 점지도 목록의 한 줄(가까운 길부터) */
data class JeomMok(
    val id: String,
    val title: String,
    val from: String,
    val to: String,
    val who: String,
    val dist: Double,
    val near: Double,
    val pyo: Int,
    val slat: Double, val slon: Double, val elat: Double, val elon: Double
) {
    /** 화면 제목에 쓰는 이름 */
    val ireum: String get() = if (title.isEmpty()) "${from}에서 ${to}까지" else title

    /** 목록 줄 말 — 아이폰 julMal 과 같음 */
    val julMal: String
        get() {
            var m = ireum
            m += ", ${Jeomjido.geoMal(dist)}"
            if (near >= 0) m += if (near < 15) ", 지금 이 길 위" else ", 여기서 ${Jeomjido.geoMal(near)}"
            if (pyo > 0) m += ", 표시 ${pyo}개"
            if (who.isNotEmpty()) m += ", 그린 분 $who"
            return m
        }
}

object Jeomjido {
    fun jari(la: Double, lo: Double): Map<String, String> =
        mapOf("lat" to String.format(Locale.US, "%.7f", la), "lon" to String.format(Locale.US, "%.7f", lo))

    /** 가까운 점지도부터(내 자리 기준) — 못 받으면 null */
    fun gakkaun(la: Double, lo: Double, kkeut: (List<JeomMok>?) -> Unit) {
        Tongsin.json("jeom.php", mapOf("a" to "find") + jari(la, lo)) { o ->
            if (o == null) { kkeut(null); return@json }
            val out = ArrayList<JeomMok>()
            val rows = o.optJSONArray("rows")
            if (rows != null) for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val id = gul(r, "id")
                if (id.isEmpty()) continue
                out.add(JeomMok(
                    id, gul(r, "title"), gul(r, "from"), gul(r, "to"), gul(r, "who"),
                    su(r, "dist") ?: 0.0, su(r, "near") ?: -1.0, (su(r, "marks") ?: 0.0).toInt(),
                    su(r, "slat") ?: 0.0, su(r, "slon") ?: 0.0, su(r, "elat") ?: 0.0, su(r, "elon") ?: 0.0
                ))
            }
            kkeut(out)
        }
    }

    /** 한 길 불러오기 — 못 받으면 null. TODO(아이폰 NaeGil): 나만의 점지도(nae_)는 다음 판 */
    fun bulleoogi(id: String, kkeut: (JeomGil?) -> Unit) {
        if (id.startsWith("nae_")) { kkeut(null); return }
        Tongsin.json("jeom.php", mapOf("a" to "get", "id" to id)) { o ->
            kkeut(o?.let { JeomGil.batgi(it) })
        }
    }

    // MARK: 셈과 말(아이폰 Chatgi.su, Nas.gul, Annae.geoMal, WichiEngine.bangwi)

    /** 숫자든 글자든 수로(아이폰 Chatgi.su) */
    fun su(o: JSONObject, k: String): Double? {
        if (o.isNull(k)) return null
        return when (val v = o.opt(k)) {
            is Number -> v.toDouble()
            is String -> v.trim().toDoubleOrNull()
            else -> null
        }
    }

    /** 글자로(아이폰 Nas.gul) — 없으면 빈칸 */
    fun gul(o: JSONObject, k: String): String {
        if (o.isNull(k)) return ""
        return when (val v = o.opt(k)) {
            is String -> v
            is Number -> v.toString()
            else -> ""
        }
    }

    fun geulOrNull(o: JSONObject, k: String): String? = if (o.isNull(k)) null else (o.opt(k) as? String)

    /** 반올림(아이폰 rounded 와 같이 0.5는 0에서 먼 쪽으로) */
    fun bannol(x: Double): Double = if (x >= 0) floor(x + 0.5) else -floor(-x + 0.5)

    /** 거리를 말로 — 5미터, 120미터, 1.2킬로미터(아이폰 Annae.geoMal) */
    fun geoMal(d: Double): String {
        if (d >= 995) {
            val k = bannol(d / 100) / 10
            return if (k == bannol(k)) "${k.toInt()}킬로미터" else String.format(Locale.US, "%.1f킬로미터", k)
        }
        if (d >= 100) return "${bannol(d / 10).toInt() * 10}미터"
        return "${max(1, bannol(d / 5).toInt() * 5)}미터"
    }

    /** 앞 자리에서 뒤 자리를 보는 방위(북쪽 0도, 시계 방향) */
    fun bangwi(a1: Double, o1: Double, a2: Double, o2: Double): Double {
        val p = Math.PI / 180
        val y = sin((o2 - o1) * p) * cos(a2 * p)
        val x = cos(a1 * p) * sin(a2 * p) - sin(a1 * p) * cos(a2 * p) * cos((o2 - o1) * p)
        val b = atan2(y, x) / p
        return (b + 360) % 360
    }

    /** 내가 보는 쪽을 12시로 할 때 목표가 몇 시 방향인지(1~12) */
    fun sigyeBanghyang(jeongmyeon: Double, mokpyo: Double): Int {
        var d = (mokpyo - jeongmyeon) % 360
        if (d < 0) d += 360
        val s = bannol(d / 30).toInt() % 12
        return if (s == 0) 12 else s
    }
}
