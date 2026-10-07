// 안드로이드 길눈 — 점지도 자료(2.2.0, 빌드 261002-A4, 대표님 지시: 점지도 따라 걷기를 안드로이드에도)
// 아이폰 길눈 Jeomjido.swift 를 그대로 옮겼습니다. 웹 길눈의 점지도 창고(jeom.php)를 함께 씁니다.
//   a=find&lat&lon  가까운 길부터      a=get&id  한 길의 점과 표시
// 되돌아가는 길(dwit) — 점을 거꾸로, 표시 이름도 거꾸로(오른쪽 꺾임 ↔ 왼쪽 꺾임 등),
//   계단과 건널목은 시작과 끝을 짝지어 바꿈(거꾸로 걸으면 끝이 시작이 됨). 아이폰 2.12.0 과 같은 셈.
// 2.7.0(묶음 b2 점지도 마저, 대표님 지시 — 아이폰 Jeomjido.swift 2.10.0~2.15.0과 같은 나스 약속)
//   a=ieum  협회가 이어 둔 길(여러 점지도를 차례로)   a=jimyeong  좌표를 곳 이름으로   a=save  점지도에 올리기
//   a=bopok  보폭 잰 것 남기기   jeom_db.php a=georim  여기 문제 있어요   jaegi.php  걸음 오차 재기
//   나만의 점지도(nae_)는 폰 안에서(NaeGil.kt), 내 문은 NaeMun(NaeGil.kt)
//   matneunGil — 걸어가실 곳에 맞는 점지도(시작 50미터 안, 끝이 목적지 80미터 안), 없으면 이어진 길
package kr.or.ada.app.gilnun

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
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
    val marks: List<JeomPyo>,
    val nae: Boolean = false,       // 2.7.0 나만의 점지도(폰 안)
    val matgim: Boolean = false,    // 2.7.0 잠금말을 걸어 협회 서버에 맡겨 둠
    val ollim: Boolean = false      // 2.7.0 모두가 쓰도록 점지도에 올림
) {
    /** 2.7.0 아이폰 JSONEncoder(JeomGil Codable)와 같은 꼴 — 맡기기(잠가 보냄)와 폰 안 담기에 씀. 없는 값은 넣지 않음 */
    fun json(): JSONObject {
        val o = JSONObject()
        o.put("id", id); o.put("title", title); o.put("from", from); o.put("to", to)
        o.put("who", who); o.put("made", made); o.put("dist", dist)
        val pa = JSONArray()
        for (p in pts) {
            val d = JSONObject()
            d.put("lat", p.lat); d.put("lon", p.lon)
            p.acc?.let { d.put("acc", it) }; p.h?.let { d.put("h", it) }; p.t?.let { d.put("t", it) }
            pa.put(d)
        }
        o.put("pts", pa)
        val ma = JSONArray()
        for (m in marks) {
            val d = JSONObject()
            m.lat?.let { d.put("lat", it) }; m.lon?.let { d.put("lon", it) }
            m.name?.let { d.put("name", it) }; m.kind?.let { d.put("kind", it) }
            m.cnt?.let { d.put("cnt", it) }; m.mal?.let { d.put("mal", it) }
            m.t?.let { d.put("t", it) }; m.acc?.let { d.put("acc", it) }; m.dist?.let { d.put("dist", it) }
            ma.put(d)
        }
        o.put("marks", ma)
        o.put("nae", nae); o.put("matgim", matgim); o.put("ollim", ollim)
        return o
    }

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
            "왼쪽으로 꺾임" to "3시 방향으로 꺾임", "오른쪽으로 꺾임" to "9시 방향으로 꺾임",
        // 2.21.0 시계 방향 꺾임 — 거꾸로 걸으면 거울처럼(2시 ↔ 10시)
        "1시 방향으로 꺾임" to "11시 방향으로 꺾임", "2시 방향으로 꺾임" to "10시 방향으로 꺾임", "3시 방향으로 꺾임" to "9시 방향으로 꺾임",
        "4시 방향으로 꺾임" to "8시 방향으로 꺾임", "5시 방향으로 꺾임" to "7시 방향으로 꺾임", "7시 방향으로 꺾임" to "5시 방향으로 꺾임",
        "8시 방향으로 꺾임" to "4시 방향으로 꺾임", "9시 방향으로 꺾임" to "3시 방향으로 꺾임", "10시 방향으로 꺾임" to "2시 방향으로 꺾임",
        "11시 방향으로 꺾임" to "1시 방향으로 꺾임",
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
                Jeomjido.gul(o, "who"), Jeomjido.gul(o, "made"), Jeomjido.su(o, "dist") ?: 0.0, pts, marks,
                o.optBoolean("nae", false), o.optBoolean("matgim", false), o.optBoolean("ollim", false)
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

/** 2.7.0 걸을 길 한 구간(여러 점지도를 이어 걸을 때 구간이 여럿) — 아이폰 JeomGugan */
data class JeomGugan(val id: String, val dwit: Boolean) {
    fun json(): JSONObject = JSONObject().put("id", id).put("dwit", dwit)
    companion object {
        fun batgi(o: JSONObject?): JeomGugan? {
            if (o == null) return null
            val id = Jeomjido.gul(o, "id")
            return if (id.isEmpty()) null else JeomGugan(id, o.optBoolean("dwit", false))
        }
    }
}

/** 2.7.0 이어진 길 한 줄 — 아이폰 JeomIeum */
data class JeomIeum(val from: String, val to: String, val dist: Double, val gugan: List<JeomGugan>) {
    val id: String get() = gugan.joinToString(",") { it.id + (if (it.dwit) "|r" else "") }
    val julMal: String get() = "${from}에서 ${to}까지 — 길 ${gugan.size}개 이어서, ${Jeomjido.geoMal(dist)}"
}

/** 2.7.0 걸어가실 곳(아이폰 Jangso 와 같은 네 값 — 다른 묶음의 Jangso 에 기대지 않으려고 따로 둠) */
data class JeomMokjeok(val ireum: String, val juso: String, val lat: Double, val lon: Double) {
    fun json(): JSONObject = JSONObject().put("ireum", ireum).put("juso", juso).put("lat", lat).put("lon", lon)
    companion object {
        fun batgi(o: JSONObject?): JeomMokjeok? {
            if (o == null) return null
            val la = Jeomjido.su(o, "lat") ?: return null
            val lo = Jeomjido.su(o, "lon") ?: return null
            return JeomMokjeok(Jeomjido.gul(o, "ireum"), Jeomjido.gul(o, "juso"), la, lo)
        }
    }
}

/** 2.7.0 점지도로 걸을지 여쭐 때 — 아이폰 JeomMuleum(안드로이드 b1 Annae.kt 의 한 점지도짜리 JeomMuleum 과 이름이 겹치지 않게 따로) */
data class JeomYeojjum(val mok: JeomMokjeok, val julMal: String, val gugan: List<JeomGugan>)

/** 2.7.0 걸음 오차 재기 목록 한 줄 */
data class JaegiJul(val id: Int, val mal: String)

object Jeomjido {
    /** 2.7.0 여기 문제 있어요 — 여섯 가지(아이폰과 같은 말) */
    val MUNJE = listOf("점자블록이 없어졌습니다", "공사 중입니다", "무엇인가 길을 막고 있습니다",
        "소리 안내가 나지 않습니다", "바닥이 파였거나 턱이 생겼습니다", "그 밖의 문제")

    private val il = Executors.newFixedThreadPool(2)
    private val mainH = android.os.Handler(android.os.Looper.getMainLooper())

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

    /** 한 길 불러오기 — 못 받으면 null. 2.7.0 나만의 점지도(nae_)는 폰 안에서 */
    fun bulleoogi(id: String, kkeut: (JeomGil?) -> Unit) {
        if (id.startsWith("nae_")) { val g = NaeGil.chatgi(id); mainH.post { kkeut(g) }; return }
        Tongsin.json("jeom.php", mapOf("a" to "get", "id" to id), 20000) { o ->
            kkeut(o?.let { JeomGil.batgi(it) })
        }
    }

    // MARK: 2.7.0 걸어가실 곳에 맞는 점지도(아이폰 matneunGil)

    /** 시작이 가까이(50미터 안) 있고 끝이 목적지 가까이(80미터 안)인 길. 거꾸로 걸어도 맞으면 되돌아가기로.
     *  한 점지도로 닿지 않으면 협회가 이어 둔 길로. 결과는 (여쭐 이름, 구간들) — 없으면 null */
    fun matneunGil(meLa: Double, meLo: Double, mok: JeomMokjeok, kkeut: (Pair<String, List<JeomGugan>>?) -> Unit) {
        gakkaun(meLa, meLo) { r0 ->
            val r = r0 ?: run { kkeut(null); return@gakkaun }
            class Best(val m: JeomMok, val dw: Boolean, val d: Double)
            var best: Best? = null
            for (m in r) {
                val eo = if (m.near >= 0) m.near else Wichi.geori(meLa, meLo, m.slat, m.slon)
                if (eo > 50) continue
                val ap = Wichi.geori(mok.lat, mok.lon, m.elat, m.elon)
                val dw = Wichi.geori(mok.lat, mok.lon, m.slat, m.slon)
                if (ap <= 80 && (best == null || ap < best.d)) best = Best(m, false, ap)
                if (dw <= 80 && (best == null || dw < best!!.d)) best = Best(m, true, dw)
            }
            for (g in NaeGil.mokrok) {
                if (g.pts.size < 3) continue
                val s = g.pts.first()
                val e = g.pts.last()
                val jari = g.pts.minOfOrNull { Wichi.geori(meLa, meLo, it.lat, it.lon) } ?: 1e9
                if (jari > 50) continue
                val ap = Wichi.geori(mok.lat, mok.lon, e.lat, e.lon)
                val dw = Wichi.geori(mok.lat, mok.lon, s.lat, s.lon)
                val jm = JeomMok(g.id, g.title, g.from, g.to, "", g.dist, jari, g.marks.size, s.lat, s.lon, e.lat, e.lon)
                if (ap <= 80 && (best == null || ap < best.d)) best = Best(jm, false, ap)
                if (dw <= 80 && (best == null || dw < best!!.d)) best = Best(jm, true, dw)
            }
            val b = best
            if (b != null) {
                val g = b.m
                val nm = if (b.dw) "${g.to}에서 ${g.from}까지 되돌아가는 점지도" else (if (g.title.isEmpty()) "${g.from}에서 ${g.to}까지 점지도" else "${g.title} 점지도")
                kkeut(Pair(nm, listOf(JeomGugan(g.id, b.dw))))
                return@gakkaun
            }
            // 한 점지도로 닿지 않으면 이어진 길로 — 첫 구간이 가까이, 마지막 구간 끝이 목적지 가까이
            ieumMok { ie ->
                if (ie == null) { kkeut(null); return@ieumMok }
                val bm = HashMap<String, JeomMok>()
                for (m in r) if (!bm.containsKey(m.id)) bm[m.id] = m
                ieumGoreugi(ie, 0, bm, meLa, meLo, mok, 0, null, kkeut)
            }
        }
    }

    /** 이어진 길을 하나씩 살핌 — 마지막 구간이 가까운 길 목록에 없으면 불러와 끝을 봄(세 번까지, 아이폰 2.12.0) */
    private fun ieumGoreugi(ie: List<JeomIeum>, i: Int, bm: Map<String, JeomMok>, meLa: Double, meLo: Double, mok: JeomMokjeok,
                            bulleon: Int, bi: Pair<JeomIeum, Double>?, kkeut: (Pair<String, List<JeomGugan>>?) -> Unit) {
        if (i >= ie.size) {
            val x = bi?.first ?: run { kkeut(null); return }
            kkeut(Pair("${x.from}에서 ${x.to}까지 이어진 점지도, 길 ${x.gugan.size}개", x.gugan))
            return
        }
        val x = ie[i]
        val c = x.gugan.firstOrNull()
        val k = x.gugan.lastOrNull()
        val cm = c?.let { bm[it.id] }
        if (c == null || k == null || cm == null) { ieumGoreugi(ie, i + 1, bm, meLa, meLo, mok, bulleon, bi, kkeut); return }
        val eo = if (cm.near >= 0) cm.near else Wichi.geori(meLa, meLo, if (c.dwit) cm.elat else cm.slat, if (c.dwit) cm.elon else cm.slon)
        if (eo > 50) { ieumGoreugi(ie, i + 1, bm, meLa, meLo, mok, bulleon, bi, kkeut); return }
        fun boda(kp: Pair<Double, Double>?, bul: Int) {
            var b2 = bi
            if (kp != null) {
                val kkeutD = Wichi.geori(mok.lat, mok.lon, kp.first, kp.second)
                if (kkeutD <= 80 && (b2 == null || kkeutD < b2.second)) b2 = Pair(x, kkeutD)
            }
            ieumGoreugi(ie, i + 1, bm, meLa, meLo, mok, bul, b2, kkeut)
        }
        val km = bm[k.id]
        if (km != null) {
            boda(if (k.dwit) Pair(km.slat, km.slon) else Pair(km.elat, km.elon), bulleon)
        } else if (bulleon < 3) {
            bulleoogi(k.id) { kg ->
                val s = kg?.pts?.firstOrNull()
                val e = kg?.pts?.lastOrNull()
                boda(if (s == null || e == null) null else (if (k.dwit) Pair(s.lat, s.lon) else Pair(e.lat, e.lon)), bulleon + 1)
            }
        } else {
            boda(null, bulleon)
        }
    }

    /** 협회가 이어 둔 길(점지도 여러 개를 차례로) — jeom.php a=ieum. 못 받으면 null */
    fun ieumMok(kkeut: (List<JeomIeum>?) -> Unit) {
        Tongsin.json("jeom.php", mapOf("a" to "ieum"), 20000) { o ->
            if (o == null) { kkeut(null); return@json }
            val out = ArrayList<JeomIeum>()
            val rows = o.optJSONArray("rows")
            if (rows != null) for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val g = ArrayList<JeomGugan>()
                val ga = r.optJSONArray("gil")
                if (ga != null) for (j in 0 until ga.length()) {
                    val x = ga.optJSONObject(j) ?: continue
                    val id = gul(x, "id")
                    if (id.isNotEmpty()) g.add(JeomGugan(id, (su(x, "rev") ?: 0.0) > 0))
                }
                if (g.size < 2) continue
                out.add(JeomIeum(gul(r, "from"), gul(r, "to"), su(r, "dist") ?: 0.0, g))
            }
            kkeut(out)
        }
    }

    /** 좌표를 곳 이름으로(가까운 곳, 주소) — 못 받으면 빈칸 */
    fun jimyeong(la: Double, lo: Double, kkeut: (String) -> Unit) {
        Tongsin.json("jeom.php", mapOf("a" to "jimyeong") + jari(la, lo), 20000) { o ->
            if (o == null || !o.optBoolean("ok", false)) { kkeut(""); return@json }
            for (k in listOf("gakkaun", "juso", "mal")) {
                val v = gul(o, k).trim()
                if (v.isNotEmpty()) { kkeut(v); return@json }
            }
            kkeut("")
        }
    }

    /** 모두가 쓰도록 점지도에 올리기 — (됨, 끝 이름 또는 알림 말) */
    fun olligi(g: JeomGil, kkeut: (Boolean, String) -> Unit) {
        val e = g.pts.lastOrNull()
        if (g.pts.size < 5 || e == null) { kkeut(false, "자리가 적어 올릴 수 없습니다."); return }
        jimyeong(e.lat, e.lon) { k0 ->
            val nm = if (g.title.isEmpty()) "내 길" else g.title
            val kk = if (k0.isEmpty()) "$nm 끝" else k0
            val pts = JSONArray()
            for (p in g.pts) {
                val d = JSONObject().put("lat", p.lat).put("lon", p.lon)
                p.acc?.let { d.put("acc", it) }; p.h?.let { d.put("h", it) }; p.t?.let { d.put("t", it) }
                pts.put(d)
            }
            val marks = JSONArray()
            for (m in g.marks) {
                val d = JSONObject().put("name", m.name ?: m.ireum).put("mal", m.mal ?: "")
                m.lat?.let { d.put("lat", it) }; m.lon?.let { d.put("lon", it) }
                m.t?.let { d.put("t", it) }; m.acc?.let { d.put("acc", it) }
                marks.put(d)
            }
            val body = JSONObject().put("title", "$nm 에서 $kk 까지").put("from", nm).put("to", kk).put("who", "본인")
                .put("who_kind", "sigak").put("dist", g.dist.toInt()).put("secs", 0).put("steps", 0).put("stride", 0)
                .put("pts", pts).put("marks", marks)
            postJson("jeom.php", mapOf("a" to "save"), body) { o ->
                if (o == null) { kkeut(false, "보내지 못했습니다. 잠시 뒤 다시 눌러 주십시오."); return@postJson }
                if (o.optBoolean("ok", false)) kkeut(true, kk) else kkeut(false, "보내지 못했습니다. " + gul(o, "msg"))
            }
        }
    }

    /** 보폭 잰 것을 나스에 남김(웹과 같은 자리) */
    fun bopokNamgigi(m: Double, n: Int, s: Double, mode: String) {
        Tongsin.json("jeom.php", mapOf("a" to "bopok", "m" to String.format(Locale.US, "%.1f", m), "n" to n.toString(),
            "s" to String.format(Locale.US, "%.3f", s), "mo" to mode)) { }
    }

    /** 여기 문제 있어요 — 알릴 말을 돌려줌 */
    fun munje(k: String, mal: String, w: Jari?, kkeut: (String) -> Unit) {
        var q = mapOf("a" to "georim", "kind" to k, "mal" to mal, "who_kind" to "sigak")
        q = if (w != null) q + jari(w.lat, w.lon) else q + mapOf("lat" to "", "lon" to "")
        Tongsin.json("jeom_db.php", q, 20000) { o ->
            if (o == null) { kkeut("적어 두지 못했습니다. 통신을 확인해 주십시오."); return@json }
            val dama = (su(o, "dama") ?: 1.0).toInt()
            kkeut("적어 두었습니다. $k. 이 자리에서 ${max(1, dama)}번째입니다.")
        }
    }

    /** 걸음 오차 재기 결과 담기(jaegi.php a=damgi, 글 꼴로 보냄) */
    fun jaegiDamgi(mal: List<Pair<String, String>>, kkeut: (String) -> Unit) {
        il.execute {
            var r = "담지 못했습니다. 통신을 확인해 주십시오."
            try {
                val c = URL("https://lvd.ada.or.kr/jeom/jaegi.php?a=damgi").openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.doOutput = true
                c.connectTimeout = 20000
                c.readTimeout = 20000
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                val b = (mal + listOf("nuga" to "")).joinToString("&") { it.first + "=" + URLEncoder.encode(it.second, "UTF-8").replace("+", "%20") }
                c.outputStream.use { it.write(b.toByteArray(Charsets.UTF_8)) }
                if (c.responseCode == 200) {
                    val o = JSONObject(c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                    val m = gul(o, "msg")
                    r = if (m.isEmpty()) "담았습니다." else m
                }
                c.disconnect()
            } catch (e: Exception) {
                Girok.namgi("jaegi_oryu", mapOf("e" to (e.message ?: "")))
            }
            mainH.post { kkeut(r) }
        }
    }

    /** 걸음 오차 재기 목록 — 못 받으면 null */
    fun jaegiMokrok(kkeut: (List<JaegiJul>?) -> Unit) {
        Tongsin.json("jaegi.php", mapOf("a" to "mokrok"), 20000) { o ->
            if (o == null) { kkeut(null); return@json }
            val out = ArrayList<JaegiJul>()
            val rows = o.optJSONArray("rows")
            if (rows != null) for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val sil = su(r, "sil") ?: 0.0
                val chu = su(r, "chujeong") ?: 0.0
                val og = if (sil > 0) abs(chu - sil) / sil * 100 else 0.0
                var nal = ""
                val t = su(r, "at")
                if (t != null && t > 0) {
                    val c = Calendar.getInstance()
                    c.timeInMillis = (t * 1000).toLong()
                    nal = "${c.get(Calendar.MONTH) + 1}월 ${c.get(Calendar.DAY_OF_MONTH)}일 · "
                }
                val m = nal + "${gul(r, "sokdo")} · 지팡이 ${gul(r, "jipangi")} · ${(su(r, "geoleum") ?: 0.0).toInt()}걸음 · 오차 " +
                    String.format(Locale.US, "%.1f", og) + "퍼센트"
                out.add(JaegiJul(i, m))
            }
            kkeut(out)
        }
    }

    /** JSON 보내고 JSON 받기(아이폰 Nas.postJson) — 결과는 화면 줄에서, 못 받으면 null */
    fun postJson(pail: String, q: Map<String, String>, body: JSONObject, kkeut: (JSONObject?) -> Unit) {
        il.execute {
            var o: JSONObject? = null
            try {
                val c = URL(Tongsin.juso(pail, q)).openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.doOutput = true
                c.connectTimeout = 20000
                c.readTimeout = 20000
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("Accept", "application/json")
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                if (c.responseCode == 200) o = JSONObject(c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                c.disconnect()
            } catch (e: Exception) {
                o = null
            }
            val r = o
            mainH.post { kkeut(r) }
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
