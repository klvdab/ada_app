// LVD투어스튜디오 안드로이드 — 코스와 지점 (1.0.0, 빌드 261012-T1) — 아이폰 Tour/Kos.swift 와 같은 규칙
// 웹 투어스튜디오 1.12판과 같은 자료를 그대로 읽고 씁니다. 앱이 모르는 칸도 지우지 않고 그대로 돌려 보냅니다.
// 지점 경계 규칙(작성원칙 통합본 7번): 앞 지점의 현장영상해설을 마치고 출발하는 자리부터 다음 지점이 시작됩니다.
package kr.or.ada.app.tour

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

const val RULE_TXT = "지점 경계 규칙 — 앞 지점의 현장영상해설을 마치고 출발하는 자리부터 다음 지점이 시작됩니다. 오프닝 지점은 첫 만남 자리부터, 클로징 지점은 마지막 지점을 떠나는 자리부터 해산 자리까지입니다."

fun JSONObject.geul(k: String): String = if (isNull(k) || !has(k)) "" else opt(k)?.toString() ?: ""
fun JSONObject.su(k: String): Double? {
    if (!has(k) || isNull(k)) return null
    return when (val v = opt(k)) { is Number -> v.toDouble(); is String -> v.trim().toDoubleOrNull(); else -> null }
}

class Spot(val d: JSONObject) {
    val id: String get() = d.geul("id")
    val kind: String get() = d.geul("kind")
    fun s(k: String) = d.geul(k)
    fun n(k: String) = d.su(k)
    var name: String
        get() = s("name")
        set(v) { d.put("name", v) }
    val shots: JSONArray get() = d.optJSONArray("shots") ?: JSONArray().also { d.put("shots", it) }
    val lat: Double? get() = n("lat")
    val lng: Double? get() = n("lng") ?: n("lon")
}

class Kos(val d: JSONObject) {
    val spots = mutableListOf<Spot>()
    var openSpot: Spot
    var closeSpot: Spot

    init {
        val a = d.optJSONArray("spots") ?: JSONArray()
        for (i in 0 until a.length()) a.optJSONObject(i)?.let { spots.add(Spot(it)) }
        openSpot = Spot(d.optJSONObject("openSpot") ?: biun("오프닝"))
        closeSpot = Spot(d.optJSONObject("closeSpot") ?: biun("클로징"))
        ensureOC()
        if (d.su("rangeV") != 1.0) migrateRange()
    }

    fun json(): JSONObject {
        syncMove()
        d.put("spots", JSONArray().apply { spots.forEach { put(it.d) } })
        d.put("openSpot", openSpot.d)
        d.put("closeSpot", closeSpot.d)
        return d
    }

    val id: String get() = d.geul("id")
    var name: String
        get() = d.geul("name")
        set(v) { d.put("name", v) }
    val isGyobon: Boolean get() = id == "GYOBON" || d.optBoolean("gyobon", false)
    fun s(k: String) = d.geul(k)

    private fun biun(nm: String) = JSONObject().put("name", nm).put("stay", 0).put("walkIn", 0).put("to", "").put("access", "")
        .put("touch", "").put("safe", "").put("gpos", "").put("shots", JSONArray())

    fun ensureOC() {
        openSpot.d.put("id", "sOpen").put("kind", "open")
        closeSpot.d.put("id", "sClose").put("kind", "close")
        if (!openSpot.d.has("shots")) openSpot.d.put("shots", JSONArray())
        if (!closeSpot.d.has("shots")) closeSpot.d.put("shots", JSONArray())
    }
    val allSpots: List<Spot> get() = listOf(openSpot) + spots + listOf(closeSpot)
    fun spot(id: String) = allSpots.firstOrNull { it.id == id }

    fun spotLab(s: Spot?): String = when {
        s == null -> ""
        s.kind == "open" -> "오프닝 지점"
        s.kind == "close" -> "클로징 지점"
        else -> "${spots.indexOf(s) + 1}지점"
    }
    fun spotFull(s: Spot?): String = if (s == null) "" else if (s.kind.isEmpty()) spotLab(s) + " " + s.name else spotLab(s)
    fun spotPrev(s: Spot): Spot? { val a = allSpots; val i = a.indexOf(s); return if (i > 0) a[i - 1] else null }
    fun spotNext(s: Spot): Spot? { val a = allSpots; val i = a.indexOf(s); return if (i >= 0 && i < a.size - 1) a[i + 1] else null }

    /** 어디서부터 — 이 지점이 시작되는 자리 */
    fun spotFrom(s: Spot): String {
        if (s.kind == "open") {
            val m = d.geul("meet").trim()
            return "첫 만남 자리" + if (m.isEmpty()) " (코스 정보에서 집합 장소를 적어 주십시오)" else " — $m"
        }
        val p = spotPrev(s) ?: return ""
        val pt = p.s("to").trim()
        return spotFull(p) + "의 현장영상해설을 마치고 출발하는 자리" + if (pt.isEmpty()) " (" + spotLab(p) + "의 어디까지를 아직 적지 않았습니다)" else " — $pt"
    }
    fun spotToName(s: Spot) = if (s.kind == "close") "어디까지 — 해산하는 자리" else "어디까지 — 이 지점 현장영상해설을 마치고 출발하는 자리"

    fun spotText(s: Spot): String = when (s.kind) { "open" -> d.geul("opening"); "close" -> d.geul("closing"); else -> s.s("text") }
    fun setSpotText(s: Spot, v: String) { when (s.kind) { "open" -> d.put("opening", v); "close" -> d.put("closing", v); else -> s.d.put("text", v) } }
    fun readSec(t: String): Int { val cps = (d.su("cps") ?: 330.0) / 60; return (t.count { !it.isWhitespace() } / cps).roundToInt() }

    fun stayOf(s: Spot): Double {
        val st = s.n("stay") ?: 0.0
        if (s.kind.isNotEmpty() && st <= 0) return max(1.0, ceil(readSec(spotText(s)) / 60.0))
        return st
    }
    fun spotMinTxt(s: Spot): String {
        val w = (s.n("walkIn") ?: 0.0).toInt(); val st = stayOf(s).roundToInt()
        return fmtMin((w + st).toDouble()) + " (이동 ${w}분, 정지 현장영상해설 ${st}분)"
    }
    fun courseMin(): Double = allSpots.sumOf { (it.n("walkIn") ?: 0.0) + stayOf(it) }

    fun syncMove() {
        val a = allSpots
        for (k in a.indices) {
            val nx = a.getOrNull(k + 1)
            a[k].d.put("move", nx?.n("walkIn") ?: 0.0)
            val di = nx?.n("distIn"); if (di != null && di > 0) a[k].d.put("dist", di)
        }
    }
    fun migrateRange() {
        val a = allSpots
        for (k in a.size - 1 downTo 1) {
            val pv = a[k - 1]
            if (!a[k].d.has("walkIn") || a[k].d.isNull("walkIn")) a[k].d.put("walkIn", max(0.0, pv.n("move") ?: 0.0))
            val di = pv.n("dist"); if (di != null && di > 0 && (a[k].n("distIn") ?: 0.0) == 0.0) a[k].d.put("distIn", di)
        }
        if (!openSpot.d.has("walkIn") || openSpot.d.isNull("walkIn")) openSpot.d.put("walkIn", 0)
        d.put("rangeV", 1)
    }

    fun posOf(s: Spot?): Pair<Double, Double>? {
        s ?: return null
        val la = s.lat; val lo = s.lng
        if (la != null && lo != null) return la to lo
        if (s.kind == "open") { val p = d.optJSONObject("pos"); val a = p?.su("la"); val b = p?.su("lo"); if (a != null && b != null) return a to b }
        return null
    }
    fun walkMinutes(m: Double): Int { val sp = d.su("walk")?.takeIf { it > 0 } ?: 40.0; return max(1, (m / sp).roundToInt() + 1) }

    fun newSpotId(): String { var n = 1; while (spots.any { it.id == "s$n" }) n++; return "s$n" }
    fun addSpot(after: Spot?, name: String): Spot {
        val s = Spot(JSONObject().put("id", newSpotId()).put("name", name.ifEmpty { "이름 없는 지점" }).put("stay", 5).put("walkIn", 0).put("move", 0)
            .put("to", "").put("text", "").put("access", "").put("touch", "").put("safe", "").put("gpos", "").put("shots", JSONArray()))
        val idx = when {
            after == null || after.kind == "open" -> 0
            after.kind == "close" -> spots.size
            else -> (spots.indexOf(after).takeIf { it >= 0 } ?: (spots.size - 1)) + 1
        }
        spots.add(minOf(idx, spots.size), s)
        return s
    }
    fun moveSpot(s: Spot, by: Int): Boolean {
        val i = spots.indexOf(s); val j = i + by
        if (i < 0 || j < 0 || j >= spots.size) return false
        spots[i] = spots[j]; spots[j] = s; return true
    }
}

fun fmtMin(m: Double): String { val n = m.roundToInt(); return if (n < 60) "${n}분" else "${n / 60}시간 ${n % 60}분" }

fun metersBetween(a: Pair<Double, Double>?, b: Pair<Double, Double>?): Double? {
    if (a == null || b == null) return null
    val r = Math.PI / 180; val R = 6371000.0
    val dLat = (b.first - a.first) * r; val dLng = (b.second - a.second) * r
    val h = sin(dLat / 2) * sin(dLat / 2) + cos(a.first * r) * cos(b.first * r) * sin(dLng / 2) * sin(dLng / 2)
    return Math.round(2 * R * atan2(sqrt(h), sqrt(1 - h))).toDouble()
}

/** 원칙 점검(작성원칙 통합본 16개 가운데 앱이 지점마다 살필 수 있는 것) — 아이폰과 같은 줄 */
object Jeomgeom {
    private val siGakJeonje = listOf("보이", "보시", "보세요", "보입니다", "보면", "보듯")
    private val sangdae = listOf("이쪽", "저쪽", "여기 보이", "앞쪽에 보이")
    private val bang = Regex("[0-9]{1,2}시 ?방향")
    fun lines(c: Kos): List<String> {
        val L = mutableListOf<String>()
        fun gat(f: (Spot) -> Boolean) = c.allSpots.filter(f).joinToString(", ") { c.spotLab(it) }
        if (c.s("meet").isBlank()) L.add("집합 장소(첫 만남 자리)를 아직 적지 않았습니다(1번).")
        if (c.s("opening").isBlank()) L.add("오프닝 지점 대본이 비어 있습니다(2번).")
        gat { it.s("to").isBlank() }.let { if (it.isNotEmpty()) L.add("어디까지를 안 적은 지점(7번): $it") }
        gat { it.kind.isEmpty() && (it.n("stay") ?: 0.0) <= 0 }.let { if (it.isNotEmpty()) L.add("코스별 소요시간이 없는 지점(4번): $it") }
        gat { it.kind.isEmpty() && it.s("gpos").isBlank() }.let { if (it.isNotEmpty()) L.add("현장영상해설사 위치를 안 적은 지점(8번): $it") }
        gat { c.spotText(it).isBlank() }.let { if (it.isNotEmpty()) L.add("대본이 빈 지점: $it") }
        gat { val t = c.spotText(it); t.isNotEmpty() && !bang.containsMatchIn(t) }.let { if (it.isNotEmpty()) L.add("시계 방향으로 짚지 않은 지점(11번): $it") }
        gat { s -> sangdae.any { c.spotText(s).contains(it) } }.let { if (it.isNotEmpty()) L.add("이쪽·저쪽 같은 말이 든 지점(11번): $it") }
        gat { c.spotText(it).contains("분면") }.let { if (it.isNotEmpty()) L.add("분면이라는 말이 든 지점(9번): $it") }
        gat { c.spotText(it).contains("더듬") || it.s("touch").contains("더듬") }.let { if (it.isNotEmpty()) L.add("더듬다가 든 지점(13번): $it") }
        gat { s -> siGakJeonje.any { c.spotText(s).contains(it) } }.let { if (it.isNotEmpty()) L.add("시각 전제 표현이 든 지점(12번): $it") }
        if (c.s("closing").isBlank()) L.add("클로징 지점 대본이 비어 있습니다(10번).")
        return L
    }
}
