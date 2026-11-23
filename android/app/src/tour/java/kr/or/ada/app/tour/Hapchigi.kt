// LVD투어스튜디오 안드로이드 — 저장 겹침 합치기 (1.4.3, 빌드 261123-T9, 2026-11-23 새방클, 이사장님 승인 「1」)
// 웹·앱이 같은 코스를 함께 고치다 저장이 겹치면, 예전에는 나중에 저장한 쪽이 코스를 통째로 덮었습니다.
// 이제는 마지막으로 맞춘 것(기준)과 비교해, 이쪽에서 실제로 고친 칸만 서버 최신 것에 얹습니다.
// 다른 쪽에서 고친 칸은 그대로 살리고, 같은 칸을 양쪽에서 고쳤을 때만 이쪽 것으로 맞춥니다(앞 것은 판 되돌리기에 남음).
// 웹 lvdts_sae143.js 의 hap()·아이폰 Hapchigi.swift 와 같은 규칙입니다.
//   목록(지점·사진·메모 등)은 id(없으면 at, 그것도 없으면 f)로 짝을 지어 합칩니다.
package kr.or.ada.app.tour

import org.json.JSONArray
import org.json.JSONObject

object Hapchigi {
    /** 같은 칸을 양쪽에서 고쳐 이쪽 것으로 맞춘 수 */
    var gyeopchim = 0

    private val EOPSEUM = Any()
    private fun norm(v: Any?): Any? = if (v == null || v == JSONObject.NULL) null else v

    fun gata(a: Any?, b: Any?): Boolean {
        val x = norm(a); val y = norm(b)
        if (x == null || y == null) return x == null && y == null
        if (x is JSONObject && y is JSONObject) {
            if (x.length() != y.length()) return false
            val it = x.keys(); while (it.hasNext()) { val k = it.next(); if (!y.has(k) || !gata(x.opt(k), y.opt(k))) return false }
            return true
        }
        if (x is JSONArray && y is JSONArray) {
            if (x.length() != y.length()) return false
            for (i in 0 until x.length()) if (!gata(x.opt(i), y.opt(i))) return false
            return true
        }
        if (x is Number && y is Number) return x.toDouble() == y.toDouble()
        return x == y || x.toString() == y.toString()
    }

    /** 기준 b, 이쪽 m, 서버 t → 합친 것. 칸이 없으면 EOPSEUM */
    fun hap(b: Any?, m: Any?, t: Any?): Any? {
        if (gata(m, b)) return t
        if (gata(t, b)) return m
        if (gata(m, t)) return m
        val bb = norm(b); val mm = norm(m); val tt = norm(t)
        if (mm is JSONObject && tt is JSONObject) return hapGaek(bb as? JSONObject, mm, tt)
        if (mm is JSONArray && tt is JSONArray) {
            val r = hapMok(bb as? JSONArray, mm, tt)
            if (r != null) return r
        }
        gyeopchim++
        return m
    }

    private fun hapGaek(b: JSONObject?, m: JSONObject, t: JSONObject): JSONObject {
        val r = JSONObject()
        val kis = LinkedHashSet<String>()
        t.keys().forEach { kis.add(it) }; m.keys().forEach { kis.add(it) }
        for (k in kis) {
            val bv: Any? = if (b != null && b.has(k)) b.opt(k) else EOPSEUM
            val mv: Any? = if (m.has(k)) m.opt(k) else EOPSEUM
            val tv: Any? = if (t.has(k)) t.opt(k) else EOPSEUM
            val v = hapKan(bv, mv, tv)
            if (v !== EOPSEUM) r.put(k, v ?: JSONObject.NULL)
        }
        return r
    }

    private fun hapKan(b: Any?, m: Any?, t: Any?): Any? {
        val bE = b === EOPSEUM; val mE = m === EOPSEUM; val tE = t === EOPSEUM
        if (mE && tE) return EOPSEUM
        if (mE) return if (!bE && gata(t, b)) EOPSEUM else t          // 이쪽에서 지움 — 저쪽이 안 고쳤으면 지움
        if (tE) return if (!bE && gata(m, b)) EOPSEUM else m          // 저쪽에서 지움 — 이쪽이 안 고쳤으면 지움
        return hap(if (bE) null else b, m, t)
    }

    private fun yeolsoe(o: Any?): String? {
        val j = norm(o) as? JSONObject ?: return null
        for (k in listOf("id", "at", "f")) { val v = j.geul(k); if (v.isNotEmpty()) return "$k:$v" }
        return null
    }

    /** 짝을 지을 수 있는 목록만 합침. 못 하면 null */
    private fun hapMok(b: JSONArray?, m: JSONArray, t: JSONArray): JSONArray? {
        fun pyo(a: JSONArray?): LinkedHashMap<String, Any?>? {
            val p = LinkedHashMap<String, Any?>()
            if (a == null) return p
            for (i in 0 until a.length()) { val k = yeolsoe(a.opt(i)) ?: return null; if (p.containsKey(k)) return null; p[k] = a.opt(i) }
            return p
        }
        val pb = pyo(b) ?: return null; val pm = pyo(m) ?: return null; val pt = pyo(t) ?: return null
        if (pm.isEmpty() && pt.isEmpty()) return JSONArray()
        // 차례: 이쪽에서 차례를 바꿨으면 이쪽 차례, 아니면 저쪽 차례. 한쪽에만 새로 생긴 것은 뒤에 붙임
        val chare = if (pm.keys.filter { pb.containsKey(it) } != pb.keys.filter { pm.containsKey(it) }) pm.keys.toList() + pt.keys.filter { !pm.containsKey(it) }
                    else pt.keys.toList() + pm.keys.filter { !pt.containsKey(it) }
        val r = JSONArray()
        for (k in chare) {
            val v = hapKan(if (pb.containsKey(k)) pb[k] else EOPSEUM, if (pm.containsKey(k)) pm[k] else EOPSEUM, if (pt.containsKey(k)) pt[k] else EOPSEUM)
            if (v !== EOPSEUM) r.put(v)
        }
        return r
    }

    /** 코스 셋을 합쳐 돌려줌. 기준이 없으면(폰에 남은 것을 올린 때 등) 이쪽 것 그대로 */
    fun kos(gijun: String, ijjok: JSONObject, seobeo: JSONObject): JSONObject {
        gyeopchim = 0
        if (gijun.isEmpty()) return ijjok
        val r = hap(JSONObject(gijun), ijjok, seobeo)
        return (r as? JSONObject) ?: ijjok
    }
}
