// AI점자도서관 안드로이드 — 곱씹어 읽기와 사전 보관·창구 (0.6.0판, 빌드 261012-L12, 도서관 창 클) — 아이폰 Lib/Gomssip.swift 와 같은 일
// 중요 표시(문단·메모), 내 낱말장(찾아본 낱말을 그 문장과 함께), 사전 찾기(서버 사전 일꾼), 책 속에서 찾기
package kr.or.ada.app.lib

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Gomssip {
    data class Jungyo(val i: Int, val t: String, val pos: Int, val geul: String, var memo: String, val at: Long)
    data class Nanmal(val w: String, val ddeut: String, val chulcheo: String, val munjang: String, val chaek: String, val at: Long)
    data class Item(val t: String, val ddeut: String, val chulcheo: String)

    val jungyo = mutableListOf<Jungyo>()
    val nanmal = mutableListOf<Nanmal>()
    private var bulleo = false
    private val p get() = Store.ctx.getSharedPreferences("lib_gomssip", Context.MODE_PRIVATE)

    fun load() {
        if (bulleo || !Store.itna()) return
        bulleo = true
        runCatching {
            val a = JSONArray(p.getString("jungyo", "[]"))
            for (k in 0 until a.length()) { val o = a.getJSONObject(k); jungyo += Jungyo(o.getInt("i"), o.optString("t"), o.optInt("pos"), o.optString("geul"), o.optString("memo"), o.optLong("at")) }
            val b = JSONArray(p.getString("nanmal", "[]"))
            for (k in 0 until b.length()) { val o = b.getJSONObject(k); nanmal += Nanmal(o.optString("w"), o.optString("ddeut"), o.optString("chulcheo"), o.optString("munjang"), o.optString("chaek"), o.optLong("at")) }
        }
    }
    fun save() {
        val a = JSONArray(); for (j in jungyo) a.put(JSONObject().put("i", j.i).put("t", j.t).put("pos", j.pos).put("geul", j.geul).put("memo", j.memo).put("at", j.at))
        val b = JSONArray(); for (n in nanmal) b.put(JSONObject().put("w", n.w).put("ddeut", n.ddeut).put("chulcheo", n.chulcheo).put("munjang", n.munjang).put("chaek", n.chaek).put("at", n.at))
        p.edit().putString("jungyo", a.toString()).putString("nanmal", b.toString()).apply()
    }
    fun jungyoSun(): List<Jungyo> { load(); return jungyo.sortedByDescending { it.at } }
    fun nanmalSun(): List<Nanmal> { load(); return nanmal.sortedByDescending { it.at } }
    fun pyosi(i: Int, t: String, pos: Int, geul: String): Boolean {
        load()
        if (jungyo.any { it.i == i && it.pos == pos }) return false
        jungyo += Jungyo(i, t, pos, geul.take(300), "", System.currentTimeMillis()); save(); return true
    }
    fun memo(i: Int, pos: Int, m: String) { load(); jungyo.firstOrNull { it.i == i && it.pos == pos }?.let { it.memo = m; save() } }
    fun memoOf(i: Int, pos: Int): String { load(); return jungyo.firstOrNull { it.i == i && it.pos == pos }?.memo ?: "" }
    fun jiugi(j: Jungyo) { load(); jungyo.removeAll { it.i == j.i && it.pos == j.pos }; save() }
    fun damgi(w: String, it: Item, munjang: String, chaek: String) {
        load(); nanmal.removeAll { n -> n.w == w && n.chaek == chaek }
        nanmal += Nanmal(w, it.ddeut, it.chulcheo, munjang.take(200), chaek, System.currentTimeMillis()); save()
    }
    fun jiugi(n: Nanmal) { load(); nanmal.removeAll { it.w == n.w && it.chaek == n.chaek }; save() }

    /** 사전 찾기 — 결과와, 없을 때 알림 말 */
    fun chatgi(q: String): Pair<List<Item>, String> {
        val j = Api.getJson("sajeon.php", mapOf("a" to "chatgi", "q" to q))
        val a = j.optJSONArray("items")
        val it = if (a == null) emptyList() else (0 until a.length()).map { k -> val o = a.getJSONObject(k); Item(o.optString("t"), o.optString("ddeut"), o.optString("chulcheo")) }
        return Pair(it, if (it.isEmpty()) j.optString("msg").ifEmpty { "사전에 없는 낱말입니다." } else "")
    }
    /** 책 속에서 찾기 — (모두 몇 곳, 자리와 앞뒤 글, 알림 말) */
    fun sok(i: Int, q: String): Triple<Int, List<Pair<Int, String>>, String> {
        val j = Api.getJson("dokseo.php", mapOf("m" to "sokchatgi", "i" to "$i", "q" to q, "gp" to "4"))
        val a = j.optJSONArray("hits")
        val h = if (a == null) emptyList() else (0 until a.length()).map { k -> val o = a.getJSONObject(k); Pair(o.optInt("o"), o.optString("t")) }
        return Triple(j.optInt("modu", h.size), h, j.optString("msg"))
    }
    /** 문단 글을 낱말로 — 앞뒤 문장부호를 떼고, 같은 낱말은 한 번만 */
    fun nanmalDeul(t: String): List<String> {
        val seen = HashSet<String>(); val out = ArrayList<String>()
        for (raw in t.split(Regex("\\s+"))) {
            val w = raw.trim { !it.isLetterOrDigit() }
            if (w.isEmpty() || !seen.add(w)) continue
            out += w
        }
        return out
    }
}
