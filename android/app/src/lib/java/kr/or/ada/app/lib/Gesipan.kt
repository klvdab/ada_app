// 0.8.4 (261019-L23) 게시판 — 공지·묻고 답하기·후기·제작 요청·불편 신고(서버 gesipan.php, 아이폰 Lib/Gesipan.swift·웹 연구실 0.3.6 과 같은 약속)
// 쓰는 일은 POST(서버가 GET 으로는 받지 않음). 지우기는 서버가 가려 둠(실제로 지우지 않음).
package kr.or.ada.app.lib

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class GsPan(val b: String, val t: String, val su: Int, val sae: Int, val sseugi: Boolean)
data class GsDap(val nal: String, val naeyong: String)
data class GsGeul(val id: String, val beon: Int, val jemok: String, val nal: String, val ireum: String, val bimil: Boolean, val nae: Boolean,
                  val dapSu: Int, val dangye: String, val dangyeT: String, val gojeong: Boolean, val naeyong: String, val dap: List<GsDap>) {
    val julMal: String get() {
        var s = "${beon}번. " + (if (gojeong) "고정, " else "") + jemok + ", $ireum, $nal"
        if (bimil) s += ", 비밀글"
        if (dangyeT.isNotEmpty()) s += ", $dangyeT"
        if (dapSu > 0) s += ", 답 ${dapSu}개"
        return s
    }
}

object GsApi {
    private fun geul(o: JSONObject): GsGeul {
        val da = o.optJSONArray("dap")
        val dap = if (da == null) emptyList() else (0 until da.length()).map { val d = da.getJSONObject(it); GsDap(d.optString("nal"), d.optString("naeyong")) }
        return GsGeul(o.optString("id"), o.optInt("beon"), o.optString("jemok"), o.optString("nal"), o.optString("ireum"), o.optBoolean("bimil"), o.optBoolean("nae"),
            o.optInt("dap_su"), o.optString("dangye"), o.optString("dangye_t"), o.optBoolean("gojeong"), o.optString("naeyong"), dap)
    }
    /** 게시판 다섯과 운영자인지 */
    fun pan(): Pair<List<GsPan>, Boolean> {
        val j = Api.getJson("gesipan.php", mapOf("a" to "pan"))
        val a = j.optJSONArray("pan") ?: return Pair(emptyList(), false)
        return Pair((0 until a.length()).map { val o = a.getJSONObject(it); GsPan(o.optString("b"), o.optString("t"), o.optInt("su"), o.optInt("sae"), o.optBoolean("sseugi")) }, j.optBoolean("unyeong"))
    }
    /** 목록(modu, 글들, 막힌 말) */
    fun mok(b: String, o: Int): Triple<Int, List<GsGeul>, String> {
        val j = Api.getJson("gesipan.php", mapOf("a" to "mok", "b" to b, "o" to "$o", "n" to "${Api.PER}"))
        if (!j.optBoolean("ok")) return Triple(0, emptyList(), j.optString("msg", "목록을 가져오지 못했습니다."))
        val a = j.optJSONArray("mok")
        return Triple(j.optInt("modu"), if (a == null) emptyList() else (0 until a.length()).map { geul(a.getJSONObject(it)) }, "")
    }
    /** 글 하나(글, 운영자인지, 막힌 말) */
    fun bogi(b: String, id: String): Triple<GsGeul?, Boolean, String> {
        val j = Api.getJson("gesipan.php", mapOf("a" to "bogi", "b" to b, "id" to id))
        if (!j.optBoolean("ok")) return Triple(null, false, j.optString("msg", "글을 찾을 수 없습니다."))
        return Triple(geul(j.getJSONObject("geul")), j.optBoolean("unyeong"), "")
    }
    /** 쓰기·답·단계·지우기 — (됐나, 알릴 말) */
    fun bonae(q: Map<String, String>): Pair<Boolean, String> = try {
        val all = q + ("k" to Api.key)
        val body = all.entries.joinToString("&") { URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8") }.toByteArray(Charsets.UTF_8)
        val c = URL(Api.BASE + "gesipan.php").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 15000; c.readTimeout = 30000; c.useCaches = false
        c.setRequestProperty("User-Agent", Api.UA); c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
        c.outputStream.use { it.write(body) }
        val s = (if (c.responseCode in 200..299) c.inputStream else c.errorStream ?: c.inputStream).use { String(it.readBytes(), Charsets.UTF_8) }
        val j = JSONObject(s); Pair(j.optBoolean("ok"), j.optString("msg"))
    } catch (e: Exception) { Pair(false, "보내지 못했습니다. 인터넷을 확인해 주십시오.") }
}
