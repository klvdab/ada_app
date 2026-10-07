// 안드로이드 자봉 — 몸통과 나스 통신 (2.3.0, 빌드 261002-J1, 대표님 지시: 아이폰 자봉과 안드로이드 자봉이 똑같이)
// 아이폰 JabongApp.swift 의 JabongBonche 와 같은 일: 길눈 부품 가운데 자봉에 필요한 것만 세우고, 1분마다 저절로 저장.
// 화면(JabongActivity)이 없이 알림 칸의 자봉(긴급통화 받기·점지도 그리기)만 살아난 때에도 같은 차례로 세웁니다.
package kr.or.ada.app.jabong

import android.content.Context
import android.os.Handler
import android.os.Looper
import kr.or.ada.app.gilnun.Girok
import kr.or.ada.app.gilnun.Seoljeong
import kr.or.ada.app.gilnun.Sori
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

object JabongBonche {
    private val main = Handler(Looper.getMainLooper())
    private var sijakham = false

    /** 앱을 켤 때 한 번 — 여러 번 불러도 한 번만 세움 */
    fun sijak(c: Context) {
        if (sijakham) return
        sijakham = true
        val a = c.applicationContext
        Seoljeong.sijak(a)
        Girok.sijak(a)
        Sori.sijak(a)
        // 위치(Wichi)는 화면(JabongActivity)과 점지도 그리기(JabongService)가 켬 — 긴급통화만 기다리는 동안 위성·방향 센서를 돌리지 않게(배터리)
        JabongNae.sijak(a)
        JabongNae.gyoyukBonaegi()   // 2.10.0 못 보낸 교육 기록을 다시
        Geurigi.junbi(a)
        JabongDaegi.sijak(a)   // 2.1.0 긴급통화 받기 — 켜 두셨으면 알림 칸의 자봉이 기다림을 이어 감
        Girok.namgi("jabong_app_sijak", mapOf("pan" to JabongPan.pan, "bild" to JabongPan.bild, "beonho" to JabongNae.beonho, "android" to true))
        // 현장에서는 늘 의외의 일이 생깁니다 — 1분마다 저절로 저장
        val r = object : Runnable { override fun run() { jeojang(); main.postDelayed(this, 60000) } }
        main.postDelayed(r, 60000)
    }

    /** 기록과 그리던 길을 저장 — 앱이 뒤로 갈 때·1분마다 */
    fun jeojang() {
        Girok.jeojang()
        Geurigi.jeojang()
    }
}

/** 자봉의 나스 통신 — 아이폰 Tongsin.getSae·post 와 같은 꼴. "/"로 시작하면 나스 뿌리에서, 아니면 /jeom/ 아래에서. 결과는 화면 줄(main)에서 */
object JbTongsin {
    private val il = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    fun juso(pail: String, q: Map<String, String>): String {
        val bon = if (pail.startsWith("http")) pail else if (pail.startsWith("/")) "https://lvd.ada.or.kr$pail" else "https://lvd.ada.or.kr/jeom/$pail"
        if (q.isEmpty()) return bon
        val ieum = if (bon.contains("?")) "&" else "?"
        return bon + ieum + q.entries.joinToString("&") { URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8") }
    }

    /** 일하는 줄에서만 부름 — 200 이면 본문, 아니면 null */
    fun getText(pail: String, q: Map<String, String>, handO: Int = 10000): String? = try {
        val c = URL(juso(pail, q)).openConnection() as HttpURLConnection
        c.useCaches = false
        c.connectTimeout = handO
        c.readTimeout = handO
        c.setRequestProperty("Accept", "application/json")
        val t = if (c.responseCode == 200) c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } else null
        c.disconnect()
        t
    } catch (e: Exception) { null }

    /** 일하는 줄에서만 부름 — JSON 본문을 POST, 200 이면 본문 */
    fun postText(pail: String, q: Map<String, String>, bonmun: String, handO: Int = 15000): String? = try {
        val c = URL(juso(pail, q)).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.useCaches = false
        c.connectTimeout = handO
        c.readTimeout = handO
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(bonmun.toByteArray(Charsets.UTF_8)) }
        val t = if (c.responseCode == 200) c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } else null
        c.disconnect()
        t
    } catch (e: Exception) { null }

    private fun json(t: String?): JSONObject? = try { if (t == null) null else JSONObject(t) } catch (e: Exception) { null }

    /** GET 해서 JSON 하나 — 못 받으면 null */
    fun getJson(pail: String, q: Map<String, String>, kkeut: (JSONObject?) -> Unit) {
        il.execute {
            val o = json(getText(pail, q))
            main.post { kkeut(o) }
        }
    }

    /** POST 해서 JSON 하나 — 못 받으면 null */
    fun postJson(pail: String, q: Map<String, String>, bonmun: String, kkeut: ((JSONObject?) -> Unit)? = null) {
        il.execute {
            val o = json(postText(pail, q, bonmun))
            if (kkeut != null) main.post { kkeut(o) }
        }
    }

    /** 좌표를 주소로 — 아이폰 Chatgi.juso(jeom.php a=jimyeong). 없으면 null */
    fun juso(lat: Double, lon: Double, kkeut: (String?) -> Unit) {
        getJson("jeom.php", mapOf("a" to "jimyeong", "lat" to String.format(java.util.Locale.US, "%.6f", lat), "lon" to String.format(java.util.Locale.US, "%.6f", lon))) { o ->
            val j = o?.optString("juso", "") ?: ""
            kkeut(j.ifEmpty { null })
        }
    }

    /** 지금 걷는 길 이름과 동네 — 아이폰 Chatgi.gil(chatta.php a=gil). 못 받으면 null */
    fun gil(lat: Double, lon: Double, kkeut: (Pair<String, String>?) -> Unit) {
        getJson("chatta.php", mapOf("a" to "gil", "lat" to String.format(java.util.Locale.US, "%.5f", lat), "lon" to String.format(java.util.Locale.US, "%.5f", lon))) { o ->
            if (o == null) kkeut(null) else kkeut(Pair(o.optString("gil", ""), o.optString("dong", "")))
        }
    }
}
