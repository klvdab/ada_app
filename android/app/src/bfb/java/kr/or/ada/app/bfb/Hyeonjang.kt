// LVD 방송(듣는 앱) — 행사장 와이파이 저절로 붙기와 현장 찾기 (2.0.0판, 빌드 261015-L1, 방송클, 이사장님 승인 「1」)
// 1. 처음 한 번 허락을 받아 행사장 와이파이(현장영상해설방송, 비밀번호 없음)를 폰에 「저절로 붙을 와이파이」로 등록합니다.
//    그 뒤로는 행사장 범위 안에 들기만 하면 폰이 저절로 붙습니다(안드로이드가 정하는 일 — 집·회사 와이파이가 더 가까우면 그쪽에 남을 수 있음).
// 2. 붙어 있는 망 가운데 주소가 192.168.8~11 인 망을 찾아, 그 망으로만 현장 방송(192.168.8.2:7880)에 묻습니다.
//    인터넷(카카오톡·전화 앱)은 모바일 데이터로 그대로 쓰게 두고, 듣기만 행사장 와이파이로 받습니다.
// 3. 행사장에 들어서면(이 앱이 등록한 와이파이에 붙으면) 안드로이드가 이 앱에 알려 줍니다 — 알림 한 줄로 「두드리면 들립니다」.
package kr.or.ada.app.bfb

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Hyeonjang {
    const val SSID = "현장영상해설방송"
    const val IP = "192.168.8.2"
    const val WEB = 7880
    const val CHANNEL_DOCHAK = "lvd_dochak"

    private fun pref(c: Context) = c.getSharedPreferences("lvd_bangsong", Context.MODE_PRIVATE)

    /** 저절로 붙기를 이미 등록했는가 */
    fun deungrokDoem(c: Context) = pref(c).getBoolean("wifi_deungrok", false)

    /** 행사장 와이파이를 「저절로 붙을 와이파이」로 등록 — 안드로이드 10 이상. 결과 말을 돌려줌 */
    fun deungrok(c: Context): String {
        if (Build.VERSION.SDK_INT < 29) {
            pref(c).edit().putBoolean("wifi_deungrok", true).apply()
            return "이 폰은 안드로이드 판이 낮아 저절로 붙기를 등록할 수 없습니다. 행사장에서 와이파이 " + SSID + " 를 한 번 골라 붙여 주시면 폰이 기억합니다."
        }
        return try {
            val wm = c.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val s = WifiNetworkSuggestion.Builder()
                .setSsid(SSID)
                .setIsAppInteractionRequired(true)   // 붙은 뒤 이 앱에 「도착」을 알려 받기 위함
                .build()
            try { wm.removeNetworkSuggestions(listOf(s)) } catch (_: Exception) { }
            val r = wm.addNetworkSuggestions(listOf(s))
            if (r == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS || r == WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE) {
                pref(c).edit().putBoolean("wifi_deungrok", true).apply()
                "행사장 와이파이 저절로 붙기를 등록했습니다. 폰 알림 줄에 와이파이 제안을 허용할지 묻는 알림이 오면 허용을 눌러 주십시오."
            } else "저절로 붙기를 등록하지 못했습니다. 잠시 뒤 더 보기의 행사장 와이파이 저절로 붙기를 다시 눌러 주십시오. (" + r + ")"
        } catch (e: Exception) {
            "저절로 붙기를 등록하지 못했습니다. " + (e.message ?: "")
        }
    }

    /** 행사장 망 — 주소가 192.168.8.0~11.255 인 망(와이파이) */
    fun mang(c: Context): Network? {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            @Suppress("DEPRECATION")
            for (n in cm.allNetworks) {
                val lp = cm.getLinkProperties(n) ?: continue
                for (la in lp.linkAddresses) {
                    val a = la.address.hostAddress ?: continue
                    if (a.startsWith("192.168.8.") || a.startsWith("192.168.9.") || a.startsWith("192.168.10.") || a.startsWith("192.168.11.")) return n
                }
            }
        } catch (_: Exception) { }
        return null
    }

    /** 행사장 방송에 묻기 — 행사장 망으로만 */
    fun mutgi(c: Context, gil: String, ttae: Int = 3000): String? = try {
        val n = mang(c) ?: throw IllegalStateException("망 없음")
        val u = URL("http://$IP:$WEB$gil")
        val k = n.openConnection(u) as HttpURLConnection
        k.connectTimeout = ttae; k.readTimeout = ttae; k.useCaches = false
        val t = if (k.responseCode == 200) k.inputStream.bufferedReader().readText() else null
        k.disconnect(); t
    } catch (_: Exception) { null }

    /** 행사장인가 — 현장 방송이 답하면 행사 이름(없으면 빈 글), 아니면 null */
    fun salpigi(c: Context): String? {
        val t = mutgi(c, "/status") ?: return null
        return try {
            val j = JSONObject(t)
            if (!j.optBoolean("hyeonjang")) null else j.optString("jemok", "")
        } catch (_: Exception) { null }
    }

    /** 도움 요청 — 현장영상해설사 방송 앱에 바로 뜸. 성공하면 참 */
    fun yocheong(c: Context, jongryu: String): Boolean = mutgi(c, "/yocheong?k=$jongryu&n=") != null

    /** 「행사장에 도착」 알림 — 두드리면 앱이 열리며 곧바로 들림 */
    fun dochakAllim(c: Context) {
        try {
            if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL_DOCHAK, "행사장 도착", NotificationManager.IMPORTANCE_HIGH))
            val i = Intent(c, BangsongActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("baro_deutgi", true)
            val pi = PendingIntent.getActivity(c, 11, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(c, CHANNEL_DOCHAK)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("행사장에 오셨습니다")
                .setContentText("두드리면 현장영상해설이 들립니다.")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(11, n)
        } catch (_: Exception) { }
    }
}

/** 등록한 행사장 와이파이에 붙으면 안드로이드가 알려 줌 — 듣기를 곧바로 켜 보고, 안 되면 알림 한 줄 */
class DochakReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION) return
        if (!c.getSharedPreferences("lvd_bangsong", Context.MODE_PRIVATE).getBoolean("jeoljeolo_deutgi", true)) { Hyeonjang.dochakAllim(c); return }
        try { DeutService.kyeogi(c) } catch (_: Exception) { Hyeonjang.dochakAllim(c) }
    }
}
