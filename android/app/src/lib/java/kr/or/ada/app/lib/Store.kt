// AI점자도서관 안드로이드 — 내 서재와 설정 보관 (0.4.4판, 빌드 261008-L8 — 책갈피 지우기, 불러왔는지 살피기) (0.2.0, 빌드 261002-L1) — 아이폰 Lib/Store.swift 와 같은 일
package kr.or.ada.app.lib

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ReadRec(val i: Int, var t: String, val kind: String, var pos: Double, var modu: Int, var at: Long, var done: Boolean) {
    val wichiMal: String get() = if (kind == "geul") "${pos.toInt() + 1}번째 문단" else { val s = pos.toInt(); "${s / 60}분 ${s % 60}초" }
}
data class Mark(val i: Int, val t: String, val kind: String, val pos: Double, val at: Long) {
    val id: String get() = "$i-$pos"
    val wichiMal: String get() = if (kind == "geul") "${pos.toInt() + 1}번째 문단" else { val s = pos.toInt(); "${s / 60}분 ${s % 60}초" }
}

object Store {
    lateinit var ctx: Context
    /** 0.4.4 서비스가 먼저 깨어날 때 — 불러왔는지 */
    fun itna() = this::ctx.isInitialized
    private val p get() = ctx.getSharedPreferences("lib_store", Context.MODE_PRIVATE)
    val reads = mutableListOf<ReadRec>()
    val marks = mutableListOf<Mark>()
    val rates = floatArrayOf(0.8f, 1.0f, 1.2f, 1.4f, 1.7f)
    val rateNames = arrayOf("아주 느리게", "보통", "조금 빠르게", "빠르게", "아주 빠르게")
    var rateIndex: Int get() = p.getInt("rate", 1); set(v) { p.edit().putInt("rate", v).apply() }
    var voice: Int get() = p.getInt("voice", 0); set(v) { p.edit().putInt("voice", v).apply() }
    /** 앱이 스스로 내는 안내 말(톡백 알림) 켜기·끄기 */
    var speechOn: Boolean get() = p.getBoolean("speech", true); set(v) { p.edit().putBoolean("speech", v).apply() }
    val rate get() = rates[rateIndex.coerceIn(0, rates.size - 1)]

    fun load(c: Context) {
        ctx = c.applicationContext
        reads.clear(); marks.clear()
        runCatching {
            val a = JSONArray(p.getString("reads", "[]"))
            for (k in 0 until a.length()) { val o = a.getJSONObject(k); reads += ReadRec(o.getInt("i"), o.optString("t"), o.optString("kind", "geul"), o.optDouble("pos"), o.optInt("modu"), o.optLong("at"), o.optBoolean("done")) }
            val m = JSONArray(p.getString("marks", "[]"))
            for (k in 0 until m.length()) { val o = m.getJSONObject(k); marks += Mark(o.getInt("i"), o.optString("t"), o.optString("kind", "geul"), o.optDouble("pos"), o.optLong("at")) }
        }
    }
    fun save() {
        val a = JSONArray(); for (r in reads) a.put(JSONObject().put("i", r.i).put("t", r.t).put("kind", r.kind).put("pos", r.pos).put("modu", r.modu).put("at", r.at).put("done", r.done))
        val m = JSONArray(); for (k in marks) m.put(JSONObject().put("i", k.i).put("t", k.t).put("kind", k.kind).put("pos", k.pos).put("at", k.at))
        p.edit().putString("reads", a.toString()).putString("marks", m.toString()).apply()
    }
    val last: ReadRec? get() = reading.firstOrNull()
    val reading: List<ReadRec> get() = reads.filter { !it.done }.sortedByDescending { it.at }
    val finished: List<ReadRec> get() = reads.filter { it.done }.sortedByDescending { it.at }
    fun rec(i: Int) = reads.firstOrNull { it.i == i }

    /** 읽기 시작한 책은 저절로 내 서재에 */
    fun remember(i: Int, t: String, kind: String, pos: Double, modu: Int, done: Boolean = false) {
        val r = rec(i)
        if (r != null) {
            r.pos = pos; r.modu = modu; r.at = System.currentTimeMillis(); r.t = t
            if (done) r.done = true else if (r.done && pos < (modu - 1).coerceAtLeast(0)) r.done = false
        } else reads += ReadRec(i, t, kind, pos, modu, System.currentTimeMillis(), done)
        save()
    }
    fun addMark(i: Int, t: String, kind: String, pos: Double) {
        if (marks.none { it.i == i && kotlin.math.abs(it.pos - pos) < 0.5 }) { marks += Mark(i, t, kind, pos, System.currentTimeMillis()); save() }
    }
    /** 0.4.4 책갈피 지우기 */
    fun removeMark(m: Mark) { marks.removeAll { it.id == m.id }; save() }
    fun marksOf(i: Int) = marks.filter { it.i == i }.sortedBy { it.pos }
    /** 내 서재에서 지우기 — 되돌리기를 위해 돌려줌 */
    fun jiugi(i: Int): Pair<ReadRec, List<Mark>>? {
        val r = rec(i) ?: return null
        val mk = marks.filter { it.i == i }
        reads.removeAll { it.i == i }; marks.removeAll { it.i == i }; save()
        return r to mk
    }
    fun doedollrigi(r: ReadRec, mk: List<Mark>) {
        if (reads.none { it.i == r.i }) reads += r
        for (m in mk) if (marks.none { it.id == m.id }) marks += m
        save()
    }
}
