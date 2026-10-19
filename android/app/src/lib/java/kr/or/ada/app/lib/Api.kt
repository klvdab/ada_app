// 0.8.3 (261019-L22) 회원 등급 — 잠긴 책 알림(jamgeum), 소리책·영상은 미리 물음, 내 등급 묻기, 장애 확인 서류 사진 올리기(이사장님 승인 2026-10-19 11시 50분 「1」)
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
data class Item(val i: Int?, val j: String?, val t: String, val g: String?, val k: String? = null)   // 0.7.2 k: 엔터테인먼트 작품이 영상(yeongsang)인지 소리(sori)인지
data class ListResp(val modu: Int, val o: Int, val items: List<Item>, val ttl: String?)
/** 0.8.0 (261018-L19) 이어 듣기 — 다음(앞) 곡·회 */
data class Daeum(val ok: Boolean, val i: Int?, val t: String, val k: String, val aelbeom: String, val saeAelbeom: Boolean, val msg: String?)
data class BookResp(val i: Int, val t: String, val g: String, val meg: Double, val nal: String, val kind: String, val sogae: String = "", val jieun: String = "", val chulpan: String = "")   // 0.4.2 — 책 소개

class Makhim : Exception()   // 403 — 회원 열쇠가 막힘

object Api {
    const val BASE = "https://lvd.ada.or.kr/nas/"
    const val UA = "Mozilla/5.0 (Linux; Android) AIJeomjaLib/0.2"
    var PER = 15   // 0.3.0 — 설정의 목록 줄 수(5~30)
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
            Item(if (o.has("i") && !o.isNull("i")) o.optInt("i") else null, o.optString("j").ifEmpty { null }, o.optString("t"), o.optString("g").ifEmpty { null }, o.optString("k").ifEmpty { null })
        }
        return ListResp(j.optInt("modu", items.size), j.optInt("o", off), items, j.optString("ttl").ifEmpty { null })
    }
    fun list(g: String, o: Int) = listResp(getJson("doseo.php", mapOf("m" to "j_list", "g" to g, "o" to "$o", "n" to "$PER")), o)
    fun jakbon(j: String, o: Int) = listResp(getJson("doseo.php", mapOf("m" to "j_jakbon", "j" to j, "o" to "$o", "n" to "$PER")), o)
    fun find(s: String, o: Int) = listResp(getJson("doseo.php", mapOf("m" to "j_find", "s" to s, "o" to "$o", "n" to "$PER")), o)
    fun book(i: Int): BookResp {
        val j = getJson("doseo.php", mapOf("m" to "j_book", "i" to "$i"))
        return BookResp(j.optInt("i", i), j.optString("t"), j.optString("g"), j.optDouble("meg", 0.0), j.optString("nal"), j.optString("kind", "geul"), j.optString("sogae"), j.optString("jieun"), j.optString("chulpan"))
    }
    /** 글자책 한 쪽(60문단) */
    // 0.6.1 (261012) 한자음 읽기 — 쪽마다 서버가 준 원문(won)과 한자음 읽기 대상 책인지(hjgal)를 담아 둠
    val hjWon = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
    val hjGal = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    fun gul(i: Int, o: Int): Triple<Int, List<String>, String> {
        val j = getJson("dokseo.php", mapOf("m" to "gul", "i" to "$i", "o" to "$o", "gp" to "4", "hj" to Store.hanjaeum.toString()))   // 0.6.1 한자음 읽기(hj)
        val a = j.optJSONArray("mun")
        val mun = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }
        if (j.optInt("jamgeum", 0) == 1) jamMal["$i"] = j.optString("msg").ifEmpty { "인증 회원만 들을 수 있는 책입니다." }   // 0.8.3 잠긴 책
        val w = j.optJSONArray("won"); hjWon["$i:$o"] = if (w == null) emptyList() else (0 until w.length()).map { w.optString(it) }; hjGal["$i:$o"] = j.optBoolean("hjgal", false)   // 0.6.1
        return Triple(j.optInt("modu", 0), mun, j.optString("ttl"))
    }
    /** 0.5.2 (261012) 글 판 4 — 옛 판 문단 자리(여럿)를 새 판 자리로. 인터넷이 안 되면 null, 책이 없으면 빈 목록 */
    fun jari(i: Int, o: List<Int>): List<Int>? = runCatching {
        val j = getJson("dokseo.php", mapOf("m" to "jari", "i" to "$i", "o" to o.joinToString(",")))
        if (!j.optBoolean("ok")) emptyList() else { val a = j.getJSONArray("jari"); (0 until a.length()).map { a.getInt(it) } }
    }.getOrNull()
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
        apSu = j.optInt("ap", 0)   // 0.6.3 이 문단 앞에 기다리는 문단 수
        return j.optBoolean("ok") to j.optString("h")
    }
    @Volatile var apSu = 0
    /** 0.8.3 잠긴 글자책 — 책 번호마다 서버가 준 알림 말 */
    val jamMal = java.util.concurrent.ConcurrentHashMap<String, String>()
    /** 0.8.3 소리책·영상이 이 회원에게 잠겼는지 미리 물음. 잠겼으면 알릴 말, 아니면 null. 403 이어도 회원 등록을 지우지 않음 */
    fun jamgeumBogi(i: Int): String? = runCatching {
        val c = open(mediaUrl(i), 15000)
        c.setRequestProperty("Range", "bytes=0-1")
        val code = c.responseCode
        if (code != 403) { c.disconnect(); return@runCatching null }
        val t = (c.errorStream ?: return@runCatching null).use { String(it.readBytes(), Charsets.UTF_8) }
        val m = JSONObject(t).optString("msg")
        if (m.contains("인증 회원")) m else null   // 열쇠가 막힌 403 은 잠김이 아님
    }.getOrNull()
    /** 구운 소리 받기 — 아직이면 null */
    fun sori(h: String): ByteArray? {
        val c = open(url("dokseo.php", mapOf("m" to "sori", "h" to h)))
        if (c.responseCode != 200) return null
        if (!(c.contentType ?: "").contains("audio")) return null
        val d = c.inputStream.use { it.readBytes() }
        return if (d.size > 100) d else null
    }
    // 0.3.0 — 세 겹의 문: 문마다 서가(키, 이름, 책 수)
    fun mun(h: String): Map<String, List<Triple<String, String, Int>>> {
        val j = getJson("doseo.php", mapOf("m" to "j_mun", "h" to h))
        val out = HashMap<String, List<Triple<String, String, Int>>>()
        val a = j.optJSONArray("mun") ?: return out
        for (x in 0 until a.length()) {
            val m = a.getJSONObject(x); val s = m.optJSONArray("seoga"); val l = ArrayList<Triple<String, String, Int>>()
            if (s != null) for (y in 0 until s.length()) { val g = s.getJSONObject(y); l.add(Triple(g.optString("k"), g.optString("t"), g.optInt("n"))) }
            out[m.optString("k")] = l
        }
        return out
    }
    fun seoga(mun: String, k: String, h: String, o: Int) = listResp(getJson("doseo.php", mapOf("m" to "j_seoga", "mun" to mun, "sk" to k, "h" to h, "o" to "$o", "n" to "$PER")), o)
    // 0.3.0 — 내려받기: 파일을 통째로 받아 저장(멈추면 지움)
    fun naeryeo(u: String, to: java.io.File, meomchum: () -> Boolean): Boolean {
        val con = open(u, 900000)
        if (con.responseCode != 200) { con.disconnect(); return false }
        val tmp = java.io.File(to.path + ".part")
        con.inputStream.use { inp -> tmp.outputStream().use { out -> val buf = ByteArray(65536); while (true) { if (meomchum()) { break }; val n = inp.read(buf); if (n < 0) break; out.write(buf, 0, n) } } }
        con.disconnect()
        if (meomchum() || tmp.length() < 1000) { tmp.delete(); return false }
        return tmp.renameTo(to)
    }
    fun mediaUrl(i: Int) = if (isEnt(i)) url("enter.php", mapOf("m" to "media", "i" to "$i")) else url("dokseo.php", mapOf("m" to "media", "i" to "$i"))   // 0.7.0 엔터테인먼트 작품은 enter.php 에서

    // 0.7.0 (261014-L16) 엔터테인먼트 — 나스 「AI문화관」의 공개 갈래(지금은 화면해설 작품). 작품 번호는 9000001부터라 도서관 책 번호와 겹치지 않음
    const val ENT_BASE = 9_000_000
    fun isEnt(i: Int) = i >= ENT_BASE
    fun entGal(): List<Gal> {
        val a = getJson("enter.php", mapOf("m" to "j_gal")).optJSONArray("gal") ?: return emptyList()
        return (0 until a.length()).map { val o = a.getJSONObject(it); Gal(o.optString("g"), o.optInt("n"), o.optString("dan")) }
    }
    /** 0.8.0 bang: ap 앨범·작품 안(여러 장은 이어서), gal 갈래 끝까지, seokgi 섞어 듣기. dwi 이면 앞 것 */
    fun daeum(i: Int, bang: String, dwi: Boolean): Daeum {
        val j = getJson("enter.php", mapOf("m" to "daeum", "i" to "$i", "bang" to bang, "dwi" to if (dwi) "1" else ""))
        return Daeum(j.optBoolean("ok"), if (j.has("i") && !j.isNull("i")) j.getInt("i") else null, j.optString("t"), j.optString("k"), j.optString("aelbeom"), j.optBoolean("sae_aelbeom"), j.optString("msg").ifEmpty { null })
    }
    fun entList(j: String, o: Int) = listResp(getJson("enter.php", mapOf("m" to "j_list", "j" to j, "o" to "$o", "n" to "$PER")), o)
    fun entFind(s: String, o: Int) = listResp(getJson("enter.php", mapOf("m" to "j_find", "s" to s, "o" to "$o", "n" to "$PER")), o)
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
            else { p(c).edit().putString("tk", tk).putString("ireum", ir).putBoolean("bogang", j.optBoolean("bogang")).apply(); null }
        } catch (e: Exception) { "도서관에 닿지 못했습니다. 인터넷을 확인한 뒤 다시 등록을 눌러 주십시오." }
    }
    // ── 0.8.2 (261019-L21) 실적용 회원 정보 보강 — 장애 유형·지역·연령대·약관 동의(이사장님 승인 2026-10-18 「1」). 값은 서버 hoewon.php hw_bogang_mok 과 같아야 함
    val JANGAE = listOf("시각장애(전맹)", "시각장애(저시력)", "그 밖의 독서장애", "장애 없음(가족·봉사자 등)", "밝히지 않음")
    val JIYEOK = listOf("서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주", "해외", "밝히지 않음")
    val NAI = listOf("10대 이하", "20대", "30대", "40대", "50대", "60대", "70대", "80대 이상", "밝히지 않음")
    const val YAKGWAN = "AI점자도서관 이용 약관: 저작권이 있는 책은 저작권법 제33조에 따라 시각장애인 등 회원 본인만 듣고, 복사하거나 남에게 넘기지 않습니다. 협회는 이름과 전화번호를 회원 관리에만 쓰고, 이용 기록은 이름 없이 숫자로만 도서관 실적에 씁니다."
    fun bogangDoem(c: Context) = p(c).getBoolean("bogang", false)
    // ── 0.8.3 (261019-L22) 회원 등급 — ilban 일반, injeung 인증, bongsa 봉사, unyeong 운영자, choego 최고관리자
    fun deung(c: Context) = p(c).getString("deung", "") ?: ""
    fun deungT(c: Context) = p(c).getString("deungT", "") ?: ""
    fun jeung(c: Context) = p(c).getString("jeung", "") ?: ""
    /** 일반 회원은 외국 도서(저작권이 끝난 책)만 듣고, 장애 확인을 받으면 모든 책을 들음 */
    fun ilban(c: Context) = deung(c) == "ilban"
    const val JEUNG_MAL = "장애인 복지카드나 장애인증명서를 사진으로 찍어 올려 주십시오. 협회가 확인하면 인증 회원이 되어 모든 책과 엔터테인먼트 작품을 들으실 수 있습니다. 올리신 사진은 장애 확인에만 쓰고, 운영하는 사람만 봅니다."
    fun jeungIreum(j: String) = when (j) { "daegi" -> "올리신 서류를 협회가 확인하고 있습니다"; "seungin" -> "장애 확인을 마쳤습니다"; "georeo" -> "서류를 다시 올려 주셔야 합니다"; else -> "아직 서류를 올리지 않으셨습니다" }
    /** 내 회원 등급을 서버에 물음(켤 때, 설정을 열 때). 다른 스레드에서 부를 것 */
    fun deungHwakin(c: Context) {
        if (!deungrokdoem(c)) return
        try {
            val con = URL(Api.BASE + "hoewon.php?a=sangtae&tk=" + URLEncoder.encode(yeolsoe(c), "UTF-8")).openConnection() as HttpURLConnection
            con.connectTimeout = 15000; con.readTimeout = 15000; con.setRequestProperty("User-Agent", Api.UA)
            if (con.responseCode !in 200..299) return
            val j = JSONObject(con.inputStream.use { String(it.readBytes(), Charsets.UTF_8) })
            if (j.optBoolean("ok")) p(c).edit().putString("deung", j.optString("deung", "ilban")).putString("deungT", j.optString("deung_t")).putString("jeung", j.optString("jeung")).apply()
        } catch (e: Exception) {}
    }
    /** 장애 확인 서류(사진) 올리기. 첫째가 null 이면 성공이고 둘째가 알릴 말 */
    fun jeungOllim(c: Context, jpg: ByteArray): Pair<String?, String?> = try {
        val con = URL(Api.BASE + "hoewon.php?a=jeungmyeong").openConnection() as HttpURLConnection
        val b = "hw" + UUID.randomUUID()
        con.requestMethod = "POST"; con.doOutput = true; con.connectTimeout = 15000; con.readTimeout = 90000
        con.setRequestProperty("User-Agent", Api.UA)
        con.setRequestProperty("Content-Type", "multipart/form-data; boundary=$b")
        val body = ByteArrayOutputStream()
        body.write("--$b\r\nContent-Disposition: form-data; name=\"tk\"\r\n\r\n${yeolsoe(c)}\r\n".toByteArray(Charsets.UTF_8))
        body.write("--$b\r\nContent-Disposition: form-data; name=\"f\"; filename=\"jeung.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray(Charsets.UTF_8))
        body.write(jpg)
        body.write("\r\n--$b--\r\n".toByteArray(Charsets.UTF_8))
        con.outputStream.use { it.write(body.toByteArray()) }
        val j = JSONObject((if (con.responseCode in 200..299) con.inputStream else con.errorStream).use { String(it.readBytes(), Charsets.UTF_8) })
        if (j.optBoolean("ok")) { deungHwakin(c); null to j.optString("mal").ifEmpty { "올렸습니다. 협회가 확인하면 인증 회원이 됩니다." } }
        else j.optString("error").ifEmpty { "올리지 못했습니다. 잠시 뒤 다시 해 주십시오." } to null
    } catch (e: Exception) { "도서관에 닿지 못했습니다. 인터넷을 확인한 뒤 다시 해 주십시오." to null }
    /** 켤 때 서버에 보강했는지 물음(다른 폰에서 이미 했으면 다시 묻지 않음) */
    fun bogangHwakin(c: Context) {
        if (!deungrokdoem(c) || bogangDoem(c)) return
        try {
            val con = URL(Api.BASE + "hoewon.php?a=sangtae&tk=" + URLEncoder.encode(yeolsoe(c), "UTF-8")).openConnection() as HttpURLConnection
            con.connectTimeout = 15000; con.readTimeout = 15000; con.setRequestProperty("User-Agent", Api.UA)
            val j = JSONObject(con.inputStream.use { String(it.readBytes(), Charsets.UTF_8) })
            if (j.optBoolean("ok") && j.optBoolean("bogang")) p(c).edit().putBoolean("bogang", true).apply()
        } catch (e: Exception) {}
    }
    /** 성공하면 null, 실패하면 알릴 말 */
    fun bogang(c: Context, jangae: String, jiyeok: String, nai: String): String? = try {
        val con = URL(Api.BASE + "hoewon.php?a=bogang").openConnection() as HttpURLConnection
        val b = "hw" + UUID.randomUUID()
        con.requestMethod = "POST"; con.doOutput = true; con.connectTimeout = 15000; con.readTimeout = 30000
        con.setRequestProperty("User-Agent", Api.UA)
        con.setRequestProperty("Content-Type", "multipart/form-data; boundary=$b")
        val body = StringBuilder()
        for ((k, v) in listOf("tk" to yeolsoe(c), "jangae" to jangae, "jiyeok" to jiyeok, "nai" to nai, "dongui" to "1")) body.append("--$b\r\nContent-Disposition: form-data; name=\"$k\"\r\n\r\n$v\r\n")
        body.append("--$b--\r\n")
        con.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val j = JSONObject((if (con.responseCode in 200..299) con.inputStream else con.errorStream).use { String(it.readBytes(), Charsets.UTF_8) })
        if (j.optBoolean("ok")) { p(c).edit().putBoolean("bogang", true).apply(); null } else j.optString("error").ifEmpty { "보내지 못했습니다. 잠시 뒤 다시 해 주십시오." }
    } catch (e: Exception) { "도서관에 닿지 못했습니다. 인터넷을 확인한 뒤 다시 눌러 주십시오." }
}
