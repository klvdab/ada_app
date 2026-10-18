// AI점자도서관 안드로이드 — 이용 장부 (0.8.2판, 빌드 261019-L21, 도클 — 이사장님 승인 2026-10-18 23시 49분 「1」)
// 책·작품을 연 것(yeol), 들은 시간(deut, 1분마다 초), 끝까지 들은 것(kkeut)을 협회 서버(nas/iyong.php)에 남깁니다. 아이폰·웹과 같은 모양.
// 회원 열쇠로 보내면 서버가 이름·전화 대신 가명 번호로만 적습니다. 운영실 통계에는 숫자만 나갑니다.
// 통신이 끊기면 폰에 쌓아 두었다가(앱을 껐다 켜도 남음) 이어지면 보냅니다. 보내는 때는 이용 기록(LibGirok)과 같은 20초마다.
package kr.or.ada.app.lib

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object LibIyong {
    private const val URL_ = "https://lvd.ada.or.kr/nas/iyong.php"
    private val jamgeum = Any()
    private var jul = JSONArray()
    private var bonaeneun = false
    private var pail: File? = null
    private var ctx: Context? = null
    private val pool = Executors.newSingleThreadExecutor()

    fun sijak(c: Context) {
        if (ctx != null) return
        ctx = c.applicationContext
        pail = File(c.filesDir, "lib_iyong_jul.json")
        try { pail?.takeIf { it.exists() }?.let { synchronized(jamgeum) { jul = JSONArray(it.readText()) } } } catch (e: Exception) {}
    }

    fun namgi(e: String, i: Int, k: String, s: Int = 0) {
        if (i < 0) return
        val o = JSONObject()
        o.put("t", System.currentTimeMillis() / 1000)
        o.put("e", e); o.put("i", i); o.put("k", k)
        if (s > 0) o.put("s", s)
        synchronized(jamgeum) {
            jul.put(o)
            while (jul.length() > 5000) jul.remove(0)
        }
    }

    fun jeojang() {
        val t = synchronized(jamgeum) { jul.toString() }
        pool.execute { try { pail?.writeText(t) } catch (e: Exception) {} }
    }

    fun bonaegi() {
        val c = ctx ?: return
        val tk = Hoewon.yeolsoe(c)
        val mukeum = JSONArray()
        synchronized(jamgeum) {
            if (bonaeneun || jul.length() == 0 || tk.isEmpty()) return
            for (n in 0 until minOf(200, jul.length())) mukeum.put(jul.get(n))
            bonaeneun = true
        }
        val body = JSONObject()
        body.put("tk", tk); body.put("gi", "android")
        body.put("app", "lib-android ${kr.or.ada.app.BuildConfig.VERSION_NAME}(${kr.or.ada.app.BuildConfig.VERSION_CODE})")
        body.put("ev", mukeum)
        pool.execute {
            var code = 0
            try {
                val con = URL(URL_).openConnection() as HttpURLConnection
                con.requestMethod = "POST"; con.doOutput = true
                con.connectTimeout = 15000; con.readTimeout = 15000
                con.setRequestProperty("Content-Type", "application/json")
                con.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                code = con.responseCode
                con.disconnect()
            } catch (e: Exception) {}
            // 200 받음, 400·413 은 다시 보내도 안 되므로 버림. 403(열쇠 없음)·통신 끊김은 남겨 두었다가 다시
            val ok = code == 200 || code == 400 || code == 413
            synchronized(jamgeum) {
                bonaeneun = false
                if (ok) repeat(minOf(mukeum.length(), jul.length())) { jul.remove(0) }
            }
            if (ok) jeojang()
        }
    }
}
