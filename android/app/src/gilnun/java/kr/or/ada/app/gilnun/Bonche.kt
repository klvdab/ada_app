// 안드로이드 길눈 — 몸통: 판번호, 설정, 기록, 나스 통신 (2.0.0, 빌드 261001-A1, 대표님 승인)
// 아이폰 길눈(Gilnun 폴더)과 같은 설계도로, 웹 껍데기 없이 앱 속에서 돕니다.
package kr.or.ada.app.gilnun

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.Executors

/** 판번호와 고친 기록 — 고칠 때마다 맨 위에 더함 */
object Pan {
    const val pan = "2.0.0"
    const val bild = "261001-A1"

    class Gochim(val pan: String, val bild: String, val nal: String, val naeyong: List<String>)

    val girok = listOf(
        Gochim("2.0.0", "261001-A1", "2026-10-01", listOf(
            "안드로이드 길눈을 속까지 앱으로 새로 지음(대표님 승인) — 웹을 띄우던 껍데기 1.2.1을 버리고 아이폰 길눈과 같은 설계도로 기초부터",
            "기초판: 위치(위성과 걸음으로 이어 셈), 방향, 걸음, 길눈 말소리, 기록, 나스 통신을 앱 속에서. 화면이 꺼져도 위치가 이어 돌게 알림 칸에 길눈이 떠 있음",
            "탭 다섯(길 찾기, 둘러보기, 음악·방송, 나눔, 설정)은 속 화면에도 늘 보임. 속 화면의 뒤로 단추는 위에 하나, 폰의 뒤로 동작도 앞 화면으로. 화면이 바뀌면 커서를 첫 줄로",
            "지금 내 자리 다시 듣기, 말소리 켜기 끄기, 말 빠르기 다섯 칸, 내 보폭 재기, 새로고침, 기초 시험, 판 기록, 도움말"
        ))
    )
}

/** 설정 — 폰에 담아 둠 */
object Seoljeong {
    private lateinit var d: SharedPreferences
    fun sijak(ctx: Context) { d = ctx.getSharedPreferences("gilnun", Context.MODE_PRIVATE) }

    var malKyeojim: Boolean
        get() = d.getBoolean("malKyeojim", true)
        set(v) { d.edit().putBoolean("malKyeojim", v).apply() }
    /** 말 빠르기 0 아주 느리게 ~ 4 아주 빠르게 */
    var bbareugiDan: Int
        get() = d.getInt("bbareugiDan", 2)
        set(v) { d.edit().putInt("bbareugiDan", v.coerceIn(0, 4)).apply() }
    /** 보폭(미터) — 재지 않았으면 0.65 로 셈 */
    var bopok: Double
        get() = d.getFloat("bopok", 0.65f).toDouble()
        set(v) { d.edit().putFloat("bopok", v.toFloat()).putBoolean("bopokJaem", true).apply() }
    val bopokJaem: Boolean get() = d.getBoolean("bopokJaem", false)
    val dev: String
        get() {
            val v = d.getString("dev", null)
            if (v != null) return v
            val n = UUID.randomUUID().toString().take(8).uppercase()
            d.edit().putString("dev", n).apply()
            return n
        }
    fun georeumGijun(nal: String): Int = if (d.getString("georeumNal", "") == nal) d.getInt("georeumGijun", -1) else -1
    fun georeumGijunNoki(nal: String, v: Int) { d.edit().putString("georeumNal", nal).putInt("georeumGijun", v).apply() }

    val bbareugiIreum = listOf("아주 느리게", "느리게", "보통", "빠르게", "아주 빠르게")
}

/** 나스 통신 — https://lvd.ada.or.kr/jeom/… 로 가는 통로 하나 */
object Tongsin {
    private val il = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    @Volatile var yeongyeol = true
        private set

    fun juso(pail: String, q: Map<String, String>): String {
        val bon = if (pail.startsWith("/")) "https://lvd.ada.or.kr$pail" else "https://lvd.ada.or.kr/jeom/$pail"
        if (q.isEmpty()) return bon
        return bon + "?" + q.entries.joinToString("&") { URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8") }
    }

    /** JSON 하나 받기 — 결과는 화면 줄(main)에서. 못 받으면 null */
    fun json(pail: String, q: Map<String, String>, handO: Int = 8000, kkeut: (JSONObject?) -> Unit) {
        il.execute {
            var o: JSONObject? = null
            try {
                val c = URL(juso(pail, q)).openConnection() as HttpURLConnection
                c.connectTimeout = handO
                c.readTimeout = handO
                c.setRequestProperty("Accept", "application/json")
                if (c.responseCode == 200) {
                    val t = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    o = JSONObject(t)
                }
                c.disconnect()
                yeongyeol = true
            } catch (e: Exception) {
                yeongyeol = false
            }
            val r = o
            main.post { kkeut(r) }
        }
    }

    /** JSON 보내기(POST) — 되었는가만 알림 */
    fun bonaegi(url: String, bonmun: String, kkeut: (Boolean) -> Unit) {
        il.execute {
            var ok = false
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.doOutput = true
                c.connectTimeout = 15000
                c.readTimeout = 15000
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(bonmun.toByteArray(Charsets.UTF_8)) }
                ok = c.responseCode == 200
                c.disconnect()
                yeongyeol = true
            } catch (e: Exception) {
                yeongyeol = false
            }
            kkeut(ok)
        }
    }
}

/** 기록 — 폰이 겪은 일을 나스(applog.php)에 남김. 말씀하신 내용이나 걸으신 길은 남기지 않음. 통신이 끊기면 쌓아 두었다가 보냄 */
object Girok {
    private val jamgeum = Any()
    private var jul = JSONArray()
    private var bonaeneun = false
    private var pail: File? = null
    private val main = Handler(Looper.getMainLooper())
    private const val URL_ = "https://lvd.ada.or.kr/jeom/applog.php"

    fun sijak(ctx: Context) {
        pail = File(ctx.filesDir, "girok_jul.json")
        try { pail?.takeIf { it.exists() }?.let { jul = JSONArray(it.readText()) } } catch (e: Exception) {}
        val r = object : Runnable {
            override fun run() { bonaegi(); main.postDelayed(this, 20000) }
        }
        main.postDelayed(r, 5000)
    }

    fun namgi(e: String, d: Map<String, Any?> = emptyMap()) {
        val o = JSONObject()
        o.put("t", System.currentTimeMillis() / 1000.0)
        o.put("e", e)
        for ((k, v) in d) o.put(k, v ?: JSONObject.NULL)
        synchronized(jamgeum) {
            jul.put(o)
            while (jul.length() > 2000) jul.remove(0)
        }
    }

    fun jeojang() {
        val t = synchronized(jamgeum) { jul.toString() }
        try { pail?.writeText(t) } catch (e: Exception) {}
    }

    fun bonaegi() {
        val mukeum = JSONArray()
        synchronized(jamgeum) {
            if (bonaeneun || jul.length() == 0) return
            for (i in 0 until minOf(100, jul.length())) mukeum.put(jul.get(i))
            bonaeneun = true
        }
        val body = JSONObject()
        body.put("dev", Seoljeong.dev)
        body.put("app", "gilnun-android ${Pan.pan}(${Pan.bild})")
        body.put("ios", "android " + Build.VERSION.RELEASE + " " + Build.MODEL)
        body.put("ev", mukeum)
        Tongsin.bonaegi(URL_, body.toString()) { ok ->
            synchronized(jamgeum) {
                bonaeneun = false
                if (ok) {
                    repeat(minOf(mukeum.length(), jul.length())) { jul.remove(0) }
                }
            }
            if (ok) jeojang()
        }
    }
}
