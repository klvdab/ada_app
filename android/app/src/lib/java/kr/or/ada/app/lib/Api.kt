// AI점자도서관 안드로이드 — 나스 창구 부르기 (0.2.0, 빌드 261002-L1) — 아이폰 Lib/API.swift 와 같은 약속
package kr.or.ada.app.lib

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

data class Gal(val g: String, val n: Int, val dan: String)
data class Item(val i: Int?, val j: String?, val t: String, val g: String?)
data class ListResp(val modu: Int, val o: Int, val items: List<Item>, val ttl: String?)
data class BookResp(val i: Int, val t: String, val g: String, val meg: Double, val nal: String, val kind: String)

class Makhim : Exception()   // 403 — 회원 열쇠가 막힘

object Api {
    const val BASE = "https://lvd.ada.or.kr/nas/"
    const val UA = "Mozilla/5.0 (Linux; Android) AIJeomjaLib/0.2"
    const val PER = 15
    lateinit var ctx: Context
    val key: String get() = Hoewon.yeolsoe(ctx)

    fun url(file: String, q: Map<String, String>): String {
        val sb = StringBuilder(BASE + file + "?k=" + enc(key))
        for ((k, v) in q.toSortedMap()) sb.append("&").append(k).append("=").append(enc(v))
        return sb.toString()
    }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun open(u: String, timeout: Int = 30000): HttpURLConnection {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = timeout
        c.setRequestProperty("User-Agent", UA)
        c.useCaches = false
        return c
    }
    private fun bytes(c: HttpURLConnection): ByteArray {
        if (c.responseCode == 403) { Hoewon.ilheo(ctx); throw Makhim() }
        val s = if (c.responseCode in 200..299) c.inputStream else c.errorStream ?: c.inputStream
        return s.use { it.readBytes() }
    }
    fun getJson(file: String, q: Map<String, String>): JSONObject = JSONObject(String(bytes(open(url(file, q))), Charsets.UTF_8))

    fun gal(): List<Gal> {
        val a = getJson("doseo.php", mapOf("m" to "j_gal")).optJSONArray("gal") ?: return emptyList()
        return (0 until a.length()).map { val o = a.getJSONObject(it); Gal(o.optString("g"), o.optInt("n"), o.optString("dan")) }
    }
    private fun listResp(j: JSONObject, off: Int): ListResp {
        val a = j.optJSONArray("items")
        val items = if (a == null) emptyList() else (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Item(if (o.has("i") && !o.isNull("i")) o.optInt("i") else null, o.optString("j").ifEmpty { null }, o.optString("t"), o.optString("g").ifEmpty { null })
        }
        return ListResp(j.optInt("modu", items.size), j.optInt("o", off), items, j.optString("ttl").ifEmpty { null })
    }
    fun list(g: String, o: Int) = listResp(getJson("doseo.php", mapOf("m" to "j_list", "g" to g, "o" to "$o")), o)
    fun jakbon(j: String, o: Int) = listResp(getJson("doseo.php", mapOf("m" to "j_jakbon", "j" to j, "o" to "$o")), o)
    fun find(s: String, o: Int) = listResp(getJson("doseo.php", mapOf("m" to "j_find", "s" to s, "o" to "$o")), o)
    fun book(i: Int): BookResp {
        val j = getJson("doseo.php", mapOf("m" to "j_book", "i" to "$i"))
        return BookResp(j.optInt("i", i), j.optString("t"), j.optString("g"), j.optDouble("meg", 0.0), j.optString("nal"), j.optString("kind", "geul"))
    }
    /** 글자책 한 쪽(60문단) */
    fun gul(i: Int, o: Int): Triple<Int, List<String>, String> {
        val j = getJson("dokseo.php", mapOf("m" to "gul", "i" to "$i", "o" to "$o"))
        val a = j.optJSONArray("mun")
        val mun = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }
        return Triple(j.optInt("modu", 0), mun, j.optString("ttl"))
    }
    /** 목소리 굽기 요청 — 굽힌 것이면 ok=true */
    fun yocheong(text: String, voice: Int): Pair<Boolean, String> {
        val c = open(url("dokseo.php", mapOf("m" to "yocheong")))
        val b = "lib" + UUID.randomUUID()
        c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$b")
        val body = ByteArrayOutputStream()
        for ((k, v) in listOf("t" to text, "v" to "$voice")) body.write("--$b\r\nContent-Disposition: form-data; name=\"$k\"\r\n\r\n$v\r\n".toByteArray(Charsets.UTF_8))
        body.write("--$b--\r\n".toByteArray())
        c.outputStream.use { it.write(body.toByteArray()) }
        val j = JSONObject(String(bytes(c), Charsets.UTF_8))
        return j.optBoolean("ok") to j.optString("h")
    }
    /** 구운 소리 받기 — 아직이면 null */
    fun sori(h: String): ByteArray? {
        val c = open(url("dokseo.php", mapOf("m" to "sori", "h" to h)))
        if (c.responseCode != 200) return null
        if (!(c.contentType ?: "").contains("audio")) return null
        val d = c.inputStream.use { it.readBytes() }
        return if (d.size > 100) d else null
    }
    fun mediaUrl(i: Int) = url("dokseo.php", mapOf("m" to "media", "i" to "$i"))
}

/** 회원 등록과 회원 열쇠 — 아이폰 Hoewon.swift 와 같은 나스 창구(hoewon.php a=deungrok) */
object Hoewon {
    private fun p(c: Context) = c.getSharedPreferences("lib_hoewon", Context.MODE_PRIVATE)
    fun yeolsoe(c: Context) = p(c).getString("tk", "") ?: ""
    fun ireum(c: Context) = p(c).getString("ireum", "") ?: ""
    fun deungrokdoem(c: Context) = yeolsoe(c).isNotEmpty()
    fun ilheo(c: Context) { p(c).edit().remove("tk").apply() }
    /** 성공하면 null, 실패하면 알릴 말 */
    fun deungrok(c: Context, ireum: String, jeonhwa: String): String? {
        val ir = ireum.trim(); val jh = jeonhwa.filter { it.isDigit() }
        return try {
            val con = URL(Api.BASE + "hoewon.php?a=deungrok").openConnection() as HttpURLConnection
            val b = "hw" + UUID.randomUUID()
            con.requestMethod = "POST"; con.doOutput = true; con.connectTimeout = 15000; con.readTimeout = 30000
            con.setRequestProperty("User-Agent", Api.UA)
            con.setRequestProperty("Content-Type", "multipart/form-data; boundary=$b")
            val body = StringBuilder()
            for ((k, v) in listOf("ireum" to ir, "jeonhwa" to jh)) body.append("--$b\r\nContent-Disposition: form-data; name=\"$k\"\r\n\r\n$v\r\n")
            body.append("--$b--\r\n")
            con.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val s = (if (con.responseCode in 200..299) con.inputStream else con.errorStream).use { String(it.readBytes(), Charsets.UTF_8) }
            val j = JSONObject(s)
            val tk = j.optString("tk")
            if (!j.optBoolean("ok") || tk.isEmpty()) j.optString("error").ifEmpty { "회원 등록을 하지 못했습니다. 잠시 뒤 다시 해 주십시오." }
            else { p(c).edit().putString("tk", tk).putString("ireum", ir).apply(); null }
        } catch (e: Exception) { "도서관에 닿지 못했습니다. 인터넷을 확인한 뒤 다시 등록을 눌러 주십시오." }
    }
}
