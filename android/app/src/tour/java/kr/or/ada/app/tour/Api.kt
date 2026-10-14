// LVD투어스튜디오 안드로이드 — 나스 창구 부르기 (1.0.0, 빌드 261012-T1) — 아이폰 Tour/TourAPI.swift 와 같음
// 웹 투어스튜디오와 같은 창구(lvdts_api.php)를 그대로 씁니다. 들어오기 표(통행증)만 폰에 두고 비밀번호는 간직하지 않습니다.
// 모든 부름은 뒤 일꾼(스레드)에서 합니다 — 화면 줄에서 부르지 마십시오.
package kr.or.ada.app.tour

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Api {
    const val BASE = "https://lvd.ada.or.kr/lvdts/lvdts_api.php"
    private const val UA = "Mozilla/5.0 (Linux; Android) LVDTourStudio/1.0"
    lateinit var ctx: Context
    var naga: ((String) -> Unit)? = null   // 들어오기 표가 끝났을 때(화면이 채움)

    var token: String
        get() = ctx.getSharedPreferences("tour", Context.MODE_PRIVATE).getString("token", "") ?: ""
        set(v) { ctx.getSharedPreferences("tour", Context.MODE_PRIVATE).edit().putString("token", v).apply() }

    fun call(action: String, body: JSONObject = JSONObject(), timeoutMs: Int = 40000, tk: String? = null): JSONObject {
        body.put("action", action)
        return try {
            val c = URL(BASE).openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.useCaches = false
            c.connectTimeout = 15000; c.readTimeout = timeoutMs
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("User-Agent", UA)
            val pyo = tk ?: token
            if (pyo.isNotEmpty()) c.setRequestProperty("X-LVDTS-Token", pyo)
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val st = if (c.responseCode in 200..299) c.inputStream else (c.errorStream ?: c.inputStream)
            val t = st.bufferedReader(Charsets.UTF_8).readText()
            c.disconnect()
            val j = try { JSONObject(t) } catch (e: Exception) { JSONObject().put("ok", false).put("error", "서버가 이상한 대답을 했습니다: " + t.take(120)) }
            if (j.optBoolean("needLogin", false) && action != "logout") naga?.invoke("들어오기 표가 끝났습니다. 다시 들어와 주십시오.")
            j
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("error", "나스에 닿지 못했습니다. 인터넷을 확인해 주십시오.").put("offline", true)
        }
    }
    fun ok(j: JSONObject) = j.optBoolean("ok", false)
    fun err(j: JSONObject) = j.geul("error").ifEmpty { "잘 되지 않았습니다." }

    // 사진 보기 주소는 표(make_ticket)로 엶 — 웹과 같음
    private var ticket = ""; private var ticketAt = 0L
    fun needTicket(): String {
        if (ticket.isNotEmpty() && System.currentTimeMillis() - ticketAt < 5_400_000L) return ticket
        val j = call("make_ticket")
        if (ok(j)) { ticket = j.geul("tk"); ticketAt = System.currentTimeMillis() }
        return ticket
    }
    fun fileUrl(kind: String, course: String, f: String, tk: String): String =
        Uri.parse(BASE).buildUpon().appendQueryParameter("action", kind).appendQueryParameter("c", course)
            .appendQueryParameter("f", f).appendQueryParameter("tk", tk).build().toString()
}
