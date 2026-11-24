// AI점자도서관 안드로이드 — 폰과 컴퓨터 기록 함께 쓰기 (0.8.9, 빌드 261124-L32, 도클 — 대클 설계 261124, 이사장님 지시 2026-11-24 19시 19분) — 아이폰 Lib/Dongi.swift 와 같은 일
// 같은 아이디(같은 전화번호)면 폰 앱과 컴퓨터 웹(ailib.ada.or.kr)이 이어 읽기·다 읽은 책·책갈피를 함께 씀.
// 서버 창구 nas/dongi.php — a=nae 로 이 폰 기록과 지운 표시를 보내면 서버가 합친 결과(최근 것이 이김)를 돌려줌. 시각(at)은 1970년부터 밀리초.
// 로그인(회원 열쇠)이 없거나 설정에서 끄면 아무것도 보내지 않고 폰에만 둠. 인터넷이 안 되어도 폰 저장은 그대로. 403 이 와도 로그인을 풀지 않음.
package kr.or.ada.app.lib

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object Dongi {
    private val main = Handler(Looper.getMainLooper())
    private val ex = Executors.newSingleThreadExecutor()
    private var jung = false
    private var dasi = false
    private var yeyak = false

    private fun kyeo(): Boolean = Store.itna() && Store.dongi && runCatching { Api.key }.getOrDefault("").isNotEmpty()

    /** 저장할 때마다 — 3초 모아서 보냄(읽는 동안 자꾸 미뤄지지 않게, 이미 모으는 중이면 그대로) */
    fun moeum() {
        main.post {
            if (yeyak || !kyeo()) return@post
            yeyak = true
            main.postDelayed({ yeyak = false; matchugi() }, 3000)
        }
    }

    /** 이 폰 기록과 지운 표시를 보내고, 서버가 합친 결과를 받아 이 폰에 반영 — 켤 때·돌아올 때·저장 뒤 */
    fun matchugi() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { matchugi() }; return }
        if (!kyeo()) return
        if (jung) { dasi = true; return }
        jung = true
        val rs = JSONArray(); val mk = JSONArray()
        for (r in Store.reads) rs.put(JSONObject().put("i", r.i).put("t", r.t).put("kind", r.kind).put("pos", r.pos).put("modu", r.modu).put("at", r.at).put("done", r.done))
        for ((i, at) in Store.delR) rs.put(JSONObject().put("i", i).put("del", 1).put("at", at))
        for (m in Store.marks) mk.put(JSONObject().put("i", m.i).put("t", m.t).put("kind", m.kind).put("pos", m.pos).put("at", m.at))
        for (d in Store.delM.values) mk.put(JSONObject().put("i", d.i).put("pos", d.pos).put("del", 1).put("at", d.at))
        val body = JSONObject().put("reads", rs).put("marks", mk).toString().toByteArray(Charsets.UTF_8)
        val bonaenR = HashMap(Store.delR); val bonaenM = HashMap(Store.delM)
        val u = Api.url("dongi.php", mapOf("a" to "nae"))
        ex.execute {
            val j: JSONObject? = runCatching {
                val c = URL(u).openConnection() as HttpURLConnection
                c.connectTimeout = 15000; c.readTimeout = 20000; c.useCaches = false
                c.requestMethod = "POST"; c.doOutput = true
                c.setRequestProperty("User-Agent", Api.UA); c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body) }
                if (c.responseCode != 200) null else JSONObject(c.inputStream.bufferedReader().use { it.readText() }).takeIf { it.optBoolean("ok") }
            }.getOrNull()
            main.post {
                if (j != null) runCatching { hapchigi(j, bonaenR, bonaenM) }
                jung = false
                if (dasi) { dasi = false; moeum() }
            }
        }
    }

    private fun hapchigi(j: JSONObject, bonaenR: Map<Int, Long>, bonaenM: Map<String, DelMark>) {
        // 보낸 지운 표시는 서버에 남았으니 덜어 냄(그새 새로 지운 것은 남김)
        for ((i, at) in bonaenR) if (Store.delR[i] == at) Store.delR.remove(i)
        for ((k, v) in bonaenM) if (Store.delM[k]?.at == v.at) Store.delM.remove(k)
        val ra = j.optJSONArray("reads") ?: JSONArray()
        for (n in 0 until ra.length()) {
            val x = ra.optJSONObject(n) ?: continue
            if (!x.has("i")) continue
            val i = x.optInt("i"); val at = x.optLong("at")
            val l = Store.reads.indexOfFirst { it.i == i }
            if (l >= 0 && Store.reads[l].at >= at) continue
            if ((Store.delR[i] ?: -1L) >= at) continue
            if (x.optInt("del") == 1) { if (l >= 0) Store.reads.removeAt(l); continue }
            val rec = ReadRec(i, x.optString("t"), x.optString("kind", "geul"), x.optDouble("pos", 0.0), x.optInt("modu"), at, x.optBoolean("done"))
            if (l >= 0) Store.reads[l] = rec else Store.reads += rec
        }
        val ma = j.optJSONArray("marks") ?: JSONArray()
        for (n in 0 until ma.length()) {
            val x = ma.optJSONObject(n) ?: continue
            if (!x.has("i")) continue
            val i = x.optInt("i"); val at = x.optLong("at"); val pos = x.optDouble("pos", 0.0); val key = Store.markKey(i, pos)
            val l = Store.marks.indexOfFirst { Store.markKey(it.i, it.pos) == key }
            if (l >= 0 && Store.marks[l].at >= at) continue
            if ((Store.delM[key]?.at ?: -1L) >= at) continue
            if (x.optInt("del") == 1) { if (l >= 0) Store.marks.removeAt(l); continue }
            val m = Mark(i, x.optString("t"), x.optString("kind", "geul"), pos, at)
            if (l >= 0) Store.marks[l] = m else Store.marks += m
        }
        if (Store.reads.size > 300) { val keep = Store.reads.sortedByDescending { it.at }.take(300); Store.reads.clear(); Store.reads.addAll(keep) }
        Store.save(dongiDo = false)
    }
}
