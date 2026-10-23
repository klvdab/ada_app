// LVD 송출 안드로이드 — 이용 기록 (1.8.0판, 빌드 261023-B16, 새방클, 이사장님 지시 2026-10-23 — LVD 수업 Girok 과 같은 자리·같은 모양)
// 방송을 켜고 끈 때(방식·듣기 주소), 함께 나가는 인터넷 방송 상태가 바뀔 때, 1분마다 BYOD 방송 듣는 분 수, 듣는 앱이 이 폰을 찾을 수 있는지를 남김.
// 행사장 와이파이에 인터넷이 없어도 폰에 쌓아 두었다가 인터넷이 이어지면 보냄.
// 남기는 것: 앱 판, 안드로이드 판·기종, 폰마다 뽑은 구별 번호(8자리), 일의 이름과 상태 말. 이름·전화번호·위치·해설 소리는 남기지 않음.
package kr.or.ada.app.byod

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors

object Girok {
    private const val URL_ = "https://lvd.ada.or.kr/jeom/applog.php"
    private val jamgeum = Any()
    private var jul = JSONArray()
    private var bonaeneun = false
    private var pail: File? = null
    private var dev = ""
    private var sijakDoem = false
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newSingleThreadExecutor()

    fun sijak(ctx: Context) {
        if (sijakDoem) return
        sijakDoem = true
        val sp = ctx.getSharedPreferences("byod_girok", Context.MODE_PRIVATE)
        dev = sp.getString("dev", null) ?: UUID.randomUUID().toString().take(8).uppercase().also { sp.edit().putString("dev", it).apply() }
        pail = File(ctx.filesDir, "byod_girok_jul.json")
        try { pail?.takeIf { it.exists() }?.let { synchronized(jamgeum) { jul = JSONArray(it.readText()) } } } catch (e: Exception) {}
        namgi("byod_kyeojim")
        var beon = 0
        val r = object : Runnable {
            override fun run() { bonaegi(); beon += 1; if (beon % 3 == 0) jeojang(); main.postDelayed(this, 30000) }
        }
        main.postDelayed(r, 3000)
    }

    fun namgi(e: String, d: Map<String, Any?> = emptyMap()) {
        val o = JSONObject()
        o.put("t", System.currentTimeMillis() / 1000.0)
        o.put("e", e)
        for ((k, v) in d) o.put(if (k == "t" || k == "e") k + "_" else k, v ?: JSONObject.NULL)
        synchronized(jamgeum) {
            jul.put(o)
            while (jul.length() > 1000) jul.remove(0)
        }
    }

    fun jeojang() {
        val t = synchronized(jamgeum) { jul.toString() }
        pool.execute { try { pail?.writeText(t) } catch (e: Exception) {} }
    }

    fun bonaegi() {
        val mukeum = JSONArray()
        synchronized(jamgeum) {
            if (bonaeneun || jul.length() == 0 || dev.isEmpty()) return
            for (i in 0 until minOf(100, jul.length())) mukeum.put(jul.get(i))
            bonaeneun = true
        }
        val body = JSONObject()
        body.put("dev", dev)
        body.put("app", "byod-android ${kr.or.ada.app.BuildConfig.VERSION_NAME}(${kr.or.ada.app.BuildConfig.VERSION_CODE})")
        body.put("ios", "android " + Build.VERSION.RELEASE + " " + Build.MODEL)
        body.put("ev", mukeum)
        pool.execute {
            var ok = false
            try {
                val c = URL(URL_).openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.doOutput = true
                c.connectTimeout = 15000; c.readTimeout = 15000
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                ok = c.responseCode == 200
                c.disconnect()
            } catch (e: Exception) {}
            synchronized(jamgeum) {
                bonaeneun = false
                if (ok) repeat(minOf(mukeum.length(), jul.length())) { jul.remove(0) }
            }
            if (ok) jeojang()
        }
    }
}
