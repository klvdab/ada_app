// 안드로이드 길눈 — 나눔 탭의 알맹이(2.7.0 묶음 b6_nanum, 아이폰 길눈 Nanum.swift 2.9.0과 같은 설계도)
// 나눔 마당(mulnanum.php), 걸음 나눔(nanum.php), 길 부탁하기(butak.php) — 웹 길눈·아이폰 길눈과 같은 자료 창고, 나스는 그대로.
// 국가지점번호·흔들기는 다른 묶음(b2)에서 옮김. 여기서는 나스에 묻는 길(NanumNas)과 자료 꼴만 둠.
package kr.or.ada.app.gilnun

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors

// MARK: 나스에 묻기 — 값은 글자·숫자 밖을 모두 퍼센트로(아이폰 Nas 와 같음, 끝에 _=밀리초를 붙여 묵은 답을 막음)

object NanumNas {
    private val il = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    private fun pyo(v: String): String =
        URLEncoder.encode(v, "UTF-8").replace("+", "%20").replace("*", "%2A").replace("%7E", "~")

    fun juso(pail: String, q: List<Pair<String, String>>): String {
        val s = (q + ("_" to System.currentTimeMillis().toString())).joinToString("&") { it.first + "=" + pyo(it.second) }
        return "https://lvd.ada.or.kr/jeom/$pail?$s"
    }

    /** 결과 — ok: 200 을 받고 JSON 으로 읽힘, o: 받은 것(못 받으면 null) */
    class Dap(val o: JSONObject?, val dahun: Boolean)

    /** 화면 줄이 아닌 곳(일꾼·뒤 실)에서 곧바로 묻기 — 화면 줄에서 부르지 말 것 */
    fun getDongi(pail: String, q: List<Pair<String, String>>, handO: Int = 20000): Dap {
        return try {
            val c = URL(juso(pail, q)).openConnection() as HttpURLConnection
            c.connectTimeout = handO
            c.readTimeout = handO
            c.useCaches = false
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("Cache-Control", "no-cache")
            val code = c.responseCode
            val o = if (code == 200) {
                val t = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                try { JSONObject(t) } catch (e: Exception) { null }
            } else null
            c.disconnect()
            Dap(o, o != null)
        } catch (e: Exception) {
            Dap(null, false)
        }
    }

    fun postDongi(pail: String, q: List<Pair<String, String>>, bonmun: JSONObject, handO: Int = 20000): JSONObject? {
        return try {
            val c = URL(juso(pail, q)).openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = handO
            c.readTimeout = handO
            c.useCaches = false
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(bonmun.toString().toByteArray(Charsets.UTF_8)) }
            val o = if (c.responseCode == 200) {
                val t = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                try { JSONObject(t) } catch (e: Exception) { null }
            } else null
            c.disconnect()
            o
        } catch (e: Exception) {
            null
        }
    }

    /** GET — 결과는 화면 줄에서. 못 받으면 null */
    fun get(pail: String, q: List<Pair<String, String>>, kkeut: (JSONObject?) -> Unit) {
        il.execute {
            val r = getDongi(pail, q).o
            main.post { kkeut(r) }
        }
    }

    /** JSON 을 POST — 결과는 화면 줄에서. 못 받으면 null */
    fun postJson(pail: String, q: List<Pair<String, String>>, bonmun: JSONObject, kkeut: (JSONObject?) -> Unit) {
        il.execute {
            val r = postDongi(pail, q, bonmun)
            main.post { kkeut(r) }
        }
    }

    /** 뒤 실에서 할 일(여기서 점검처럼 차례로 묻는 일) */
    fun dwiSil(f: () -> Unit) { il.execute(f) }

    /** 글자로 — 글자면 그대로, 숫자면 숫자 글자(3.0 은 "3"), 없으면 빈 글 */
    fun gul(o: JSONObject?, k: String): String {
        if (o == null || !o.has(k) || o.isNull(k)) return ""
        return when (val v = o.opt(k)) {
            is String -> v
            is Int, is Long -> v.toString()
            is Number -> {
                val d = v.toDouble()
                if (d == Math.floor(d) && !d.isInfinite() && Math.abs(d) < 1e15) d.toLong().toString() else d.toString()
            }
            is Boolean -> if (v) "1" else "0"
            else -> ""
        }
    }

    /** 숫자로 — 숫자 글자도 읽음(아이폰 Chatgi.su) */
    fun su(o: JSONObject?, k: String): Double? {
        if (o == null || !o.has(k) || o.isNull(k)) return null
        return when (val v = o.opt(k)) {
            is Number -> v.toDouble()
            is String -> v.trim().toDoubleOrNull()
            else -> null
        }
    }

    fun cham(o: JSONObject?, k: String): Boolean = o?.optBoolean(k, false) ?: false

    /** 초 단위 때 → "M월 d일" */
    fun nal(t: Double?): String {
        if (t == null || t <= 0) return ""
        val c = Calendar.getInstance()
        c.timeInMillis = (t * 1000).toLong()
        return "${c.get(Calendar.MONTH) + 1}월 ${c.get(Calendar.DAY_OF_MONTH)}일"
    }

    fun julDeul(o: JSONObject?, k: String): List<JSONObject> {
        val a: JSONArray = o?.optJSONArray(k) ?: return emptyList()
        val l = ArrayList<JSONObject>()
        for (i in 0 until a.length()) a.optJSONObject(i)?.let { l.add(it) }
        return l
    }

    fun sosu6(v: Double): String = String.format(Locale.US, "%.6f", v)
}

/** 나눔 쪽 폰 안 담기 — 길눈 설정과 같은 꾸러미(gilnun), 이름 앞에 nn. */
internal object NnJeojang {
    @Volatile private var d: SharedPreferences? = null
    fun d(c: Context): SharedPreferences =
        d ?: c.applicationContext.getSharedPreferences("gilnun", Context.MODE_PRIVATE).also { d = it }
}

// MARK: 나눔 마당

data class NnMulGeul(val id: String, val mul: String, val got: String, val mal: String, val nugu: String, val yeon: String, val nal: String) {
    val julMal: String get() = mul + (if (got.isEmpty()) "" else " — $got") + (if (mal.isEmpty()) "" else ". $mal") + " · " + nal
    val jaseMal: String get() = "$mul. $mal 올리신 분은 " + (if (nugu.isEmpty()) "이름을 밝히지 않으셨습니다" else nugu) + ". 연락은 $yeon."
    val jeonhwa: String?
        get() {
            val b = yeon.filter { it.isDigit() || it == '+' }
            return if (b.length >= 8) b else null
        }
}

object NnMulnanum {
    fun mok(jong: String, kkeut: (List<NnMulGeul>?) -> Unit) {
        NanumNas.get("mulnanum.php", listOf("a" to "list", "jong" to jong)) { j ->
            if (j == null) { kkeut(null); return@get }
            kkeut(NanumNas.julDeul(j, "rows").map { x ->
                NnMulGeul(NanumNas.gul(x, "id"), NanumNas.gul(x, "mul"), NanumNas.gul(x, "got"), NanumNas.gul(x, "mal"),
                    NanumNas.gul(x, "nugu"), NanumNas.gul(x, "yeon"), NanumNas.nal(NanumNas.su(x, "ttae")))
            })
        }
    }

    fun naerigi(id: String, kkeut: (Boolean) -> Unit) {
        NanumNas.get("mulnanum.php", listOf("a" to "gam", "id" to id)) { j -> kkeut(j != null) }
    }

    /** 올리기 — null 이면 올림, 아니면 까닭 */
    fun olligi(jong: String, mul: String, mal: String, got: String, nugu: String, yeon: String, kkeut: (String?) -> Unit) {
        NanumNas.get("mulnanum.php", listOf("a" to "put", "jong" to jong, "mul" to mul, "mal" to mal, "got" to got, "nugu" to nugu, "yeon" to yeon)) { j ->
            if (NanumNas.cham(j, "ok")) kkeut(null)
            else kkeut(NanumNas.gul(j, "error").ifEmpty { "올리지 못했습니다. 다시 해 보십시오." })
        }
    }
}

// MARK: 걸음 나눔

data class NnNanumDat(val byeol: String, val nugu: String, val nal: String, val geul: String)

data class NnNanumGeul(val id: String, val byeol: String, val nugu: String, val nal: String, val geul: String, val dat: List<NnNanumDat>) {
    val meori: String get() = "$byeol · $nugu · $nal"
}

object NnGeoreumNanum {
    fun byeol(c: Context): String = NnJeojang.d(c).getString("nn.nanumByeol", "") ?: ""
    fun byeolNoki(c: Context, v: String) { NnJeojang.d(c).edit().putString("nn.nanumByeol", v).apply() }

    private fun nugu(m: String) = if (m == "jabong") "자원봉사자" else "시각장애인"

    /** 다섯 개씩 — (글, 모두 몇 개). 못 받으면 null */
    fun mok(bu: Int, kkeut: (Pair<List<NnNanumGeul>, Int>?) -> Unit) {
        NanumNas.get("nanum.php", listOf("a" to "list", "bu" to bu.toString(), "myeot" to "5", "bo" to "iyong")) { j ->
            if (j == null || !NanumNas.cham(j, "ok")) { kkeut(null); return@get }
            val ls = NanumNas.julDeul(j, "rows").map { x ->
                val d = NanumNas.julDeul(x, "dat").map { dd ->
                    val m = NanumNas.gul(dd, "mun")
                    NnNanumDat(NanumNas.gul(dd, "byeol"), if (m == "kl") "길눈" else nugu(m), NanumNas.gul(dd, "nal"), NanumNas.gul(dd, "geul"))
                }
                NnNanumGeul(NanumNas.gul(x, "id"), NanumNas.gul(x, "byeol"), nugu(NanumNas.gul(x, "mun")), NanumNas.gul(x, "nal"), NanumNas.gul(x, "geul"), d)
            }
            kkeut(ls to (NanumNas.su(j, "modu")?.toInt() ?: ls.size))
        }
    }

    fun sseugi(byeol: String, geul: String, jam: String, kkeut: (String?) -> Unit) {
        val b = JSONObject().put("byeol", byeol).put("geul", geul).put("jam", jam).put("mun", "iyong")
        NanumNas.postJson("nanum.php", listOf("a" to "sseugi"), b) { j ->
            if (NanumNas.cham(j, "ok")) kkeut(null) else kkeut(NanumNas.gul(j, "msg").ifEmpty { "올리지 못했습니다." })
        }
    }

    fun dat(id: String, byeol: String, geul: String, jam: String, kkeut: (String?) -> Unit) {
        val b = JSONObject().put("id", id).put("byeol", byeol).put("geul", geul).put("jam", jam).put("mun", "iyong")
        NanumNas.postJson("nanum.php", listOf("a" to "dat"), b) { j ->
            if (NanumNas.cham(j, "ok")) kkeut(null) else kkeut(NanumNas.gul(j, "msg").ifEmpty { "올리지 못했습니다." })
        }
    }

    /** 가리기 — (됨, 알림 말) */
    fun garigi(id: String, jam: String, kkeut: (Boolean, String) -> Unit) {
        NanumNas.get("nanum.php", listOf("a" to "jiugi", "id" to id, "jam" to jam)) { j ->
            if (j == null) kkeut(false, "닿지 못했습니다.") else kkeut(NanumNas.cham(j, "ok"), NanumNas.gul(j, "msg"))
        }
    }
}

// MARK: 길 부탁하기

data class NnButakDat(val id: String, val mal: String, val nugu: String)

data class NnButak(
    val id: String, val sin: String, val min: String, val mlat: Double, val mlon: Double, val ttae: String, val mal: String,
    val nugu: String, val geori: Double, val eonje: String, val doen: Boolean, val gil: String, val daetgeul: List<NnButakDat>
) {
    val julMal: String
        get() {
            var t = "${sin}에서 ${min}까지"
            if (geori > 0) t += " · 약 " + (if (geori >= 1000) String.format(Locale.US, "%.1f킬로미터", geori / 1000) else "${geori.toInt()}미터")
            if (doen) t += if (gil.isEmpty()) " · 다 그려졌습니다" else " · 길눈님이 그려 주셨습니다"
            return "$t · $eonje"
        }
    val jaseMal: String
        get() {
            var t = "${sin}에서 ${min}까지. "
            if (ttae.isNotEmpty()) t += "언제쯤 : $ttae. "
            if (mal.isNotEmpty()) t += "$mal. "
            if (nugu.isNotEmpty()) t += "부탁하신 분 별명은 $nugu. "
            return t + "댓글 ${daetgeul.size}개."
        }
}

/** 이름으로 찾은 곳(아이폰 Jangso 와 같은 꼴) */
data class NnGot(val ireum: String, val juso: String, val lat: Double, val lon: Double)

object NnGilButak {
    fun mok(kkeut: (List<NnButak>?) -> Unit) {
        val q = arrayListOf("a" to "list")
        Wichi.jigeum?.let { w -> q.add("lat" to NanumNas.sosu6(w.lat)); q.add("lon" to NanumNas.sosu6(w.lon)) }
        NanumNas.get("butak.php", q) { j ->
            if (j == null) { kkeut(null); return@get }
            kkeut(NanumNas.julDeul(j, "rows").map { x ->
                val d = NanumNas.julDeul(x, "daetgeul").map { NnButakDat(NanumNas.gul(it, "id"), NanumNas.gul(it, "mal"), NanumNas.gul(it, "nugu")) }
                NnButak(NanumNas.gul(x, "id"), NanumNas.gul(x, "sin"), NanumNas.gul(x, "min"),
                    NanumNas.su(x, "mlat") ?: 0.0, NanumNas.su(x, "mlon") ?: 0.0,
                    NanumNas.gul(x, "ttae"), NanumNas.gul(x, "mal"), NanumNas.gul(x, "nugu"),
                    NanumNas.su(x, "geori") ?: 0.0, NanumNas.nal(NanumNas.su(x, "eonje")),
                    NanumNas.cham(x, "doen"), NanumNas.gul(x, "gil"), d)
            })
        }
    }

    fun olligi(chul: NnGot?, chulMal: String, mok: NnGot?, mokMal: String, ttae: String, mal: String, nugu: String, kkeut: (String?) -> Unit) {
        val q = arrayListOf("a" to "put", "sin" to (chul?.ireum ?: chulMal))
        if (chul != null) { q.add("slat" to chul.lat.toString()); q.add("slon" to chul.lon.toString()) }
        q.add("min" to (mok?.ireum ?: mokMal))
        if (mok != null) { q.add("mlat" to mok.lat.toString()); q.add("mlon" to mok.lon.toString()) }
        q.add("ttae" to ttae); q.add("mal" to mal); q.add("nugu" to nugu)
        NanumNas.get("butak.php", q) { j ->
            if (NanumNas.cham(j, "ok")) kkeut(null) else kkeut(NanumNas.gul(j, "error").ifEmpty { "올리지 못했습니다." })
        }
    }

    fun hagi(a: String, id: String, deo: List<Pair<String, String>> = emptyList(), kkeut: (Boolean) -> Unit) {
        NanumNas.get("butak.php", listOf("a" to a, "id" to id) + deo) { j -> kkeut(j != null) }
    }

    /** 이름이나 주소로 곳 찾기(jeom.php a=jangso, 가까운 곳부터) — 못 받으면 null, 없으면 빈 목록 */
    fun gotChatgi(mal: String, kkeut: (List<NnGot>?) -> Unit) {
        val q = arrayListOf("a" to "jangso", "q" to mal)
        Wichi.jigeum?.let { w -> q.add("lat" to NanumNas.sosu6(w.lat)); q.add("lon" to NanumNas.sosu6(w.lon)) }
        NanumNas.get("jeom.php", q) { o ->
            if (o == null) { kkeut(null); return@get }
            kkeut(NanumNas.julDeul(o, "rows").mapNotNull { r ->
                val la = NanumNas.su(r, "lat")
                val lo = NanumNas.su(r, "lon")
                if (la == null || lo == null) null else NnGot(NanumNas.gul(r, "ireum"), NanumNas.gul(r, "juso"), la, lo)
            })
        }
    }
}

/** 토씨 — 받침이면 앞, 아니면 뒤(ㄹ 받침의 "로"까지) */
internal object NnTossi {
    private fun batchim(w: String): Int {
        val ch = w.trimEnd().lastOrNull() ?: return 0
        val c = ch.code - 0xAC00
        if (c < 0 || c >= 11172) return if (ch.isDigit()) (if (ch in "013678") 1 else 0) else 0
        return c % 28
    }
    fun eul(w: String) = if (batchim(w) != 0) "을" else "를"
    fun ro(w: String): String { val b = batchim(w); return if (b == 0 || b == 8) "로" else "으로" }
    fun eun(w: String) = if (batchim(w) != 0) "은" else "는"
}
