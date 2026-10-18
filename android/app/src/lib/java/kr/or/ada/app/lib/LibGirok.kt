// AI점자도서관 안드로이드 — 이용 기록 (0.6.3판, 빌드 261014-L15, 도서관 창 클, 이사장님 승인 2026-10-13 「1」·2026-10-14 「그래」)
// 앱이 겪은 일(켜짐, 책 열기, 소리를 오래 기다림, 소리를 못 만듦, 글자를 못 가져옴)을 나스(jeom/applog.php → /volume1/lvt_data/applog/날짜.txt)에 남김.
// 길눈 앱의 기록(Girok)과 같은 자리·같은 모양 — 아침 보고에서 모든 앱을 함께 봄. 아이폰 Lib/LibGirok.swift 와 짝.
// 남기는 것: 앱 판·빌드, 안드로이드 판·폰 모델, 폰마다 뽑은 구별 번호(8자리, 이름·전화와 잇지 않음), 일의 이름과 걸린 초, 글자책·소리책 갈래.
// 남기지 않는 것: 이름, 전화번호, 책 이름, 책 내용, 위치. 통신이 끊기면 폰에 쌓아 두었다가 이어지면 보냄.
package kr.or.ada.app.lib

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

object LibGirok {
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
        LibIyong.sijak(ctx)   // 0.8.2 이용 장부
        val sp = ctx.getSharedPreferences("lib_girok", Context.MODE_PRIVATE)
        dev = sp.getString("dev", null) ?: UUID.randomUUID().toString().take(8).uppercase().also { sp.edit().putString("dev", it).apply() }
        pail = File(ctx.filesDir, "lib_girok_jul.json")
        try { pail?.takeIf { it.exists() }?.let { synchronized(jamgeum) { jul = JSONArray(it.readText()) } } } catch (e: Exception) {}
        namgi("lib_kyeojim")
        var beon = 0
        val r = object : Runnable {
            override fun run() { bonaegi(); LibIyong.bonaegi(); beon += 1; if (beon % 3 == 0) { jeojang(); LibIyong.jeojang() }; main.postDelayed(this, 20000) }   // 0.8.2 이용 장부도 같은 때
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
            while (jul.length() > 2000) jul.remove(0)
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
        body.put("app", "lib-android ${kr.or.ada.app.BuildConfig.VERSION_NAME}(${kr.or.ada.app.BuildConfig.VERSION_CODE})")
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
