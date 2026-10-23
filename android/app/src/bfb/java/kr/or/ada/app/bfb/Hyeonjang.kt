// LVD 방송(듣는 앱) — 행사장 와이파이 저절로 붙기와 현장 찾기 (2.0.0판, 빌드 261015-L1, 방송클, 이사장님 승인 「1」)
// 2.4.0 (261023-L5, 새방클, 이사장님 지시 2026-10-23 「서둘러야 해」) 아이피타임 방식 BYOD 방송도 찾아 들음 —
//   아이피타임 방식에서는 보내는 폰(LVD 송출)이 방송 서버라 자리(주소)가 공유기가 나눠 주는 대로 매번 달라짐.
//   지금까지는 플린트 2 자리(192.168.8.2:7880)만 찾아 아이피타임 방식 방송을 한 번도 찾지 못했음(10월 22일 시험, 듣는 분 0명).
//   이제 ①지난번에 찾은 자리 ②플린트 2 자리 ③지금 붙은 와이파이 안에서 8080번 문을 연 LVD 송출 폰을 차례로 찾음.
//   와이파이 안 찾기는 30초에 한 번만 함(배터리·망을 아낌). 찾은 자리는 기억해 다음에 먼저 씀.
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
import android.net.NetworkCapabilities
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object Hyeonjang {
    const val SSID = "현장영상해설방송"
    const val IP = "192.168.8.2"
    const val WEB = 7880
    const val CHANNEL_DOCHAK = "lvd_dochak"
    const val SONGCHUL_MUN = 8080              // 2.4.0 아이피타임 방식 — LVD 송출 폰이 여는 문
    private const val CHATGI_GANGYEOK = 30_000L

    /** 2.4.0 지금 듣는 방송 자리 — 「http://주소:문」 */
    @Volatile var jari = "http://$IP:$WEB"
    @Volatile private var chatgiTtae = 0L

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

    /** 행사장 망 — 주소가 192.168.8.0~11.255 인 망(플린트 2), 없으면 2.4.0 지금 붙은 와이파이(아이피타임 방식) */
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
            @Suppress("DEPRECATION")
            for (n in cm.allNetworks) {
                val nc = cm.getNetworkCapabilities(n) ?: continue
                if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && wifiJuso(cm, n) != null) return n
            }
        } catch (_: Exception) { }
        return null
    }

    /** 2.4.0 그 와이파이에서 이 폰의 IPv4 주소(사설 주소만) */
    private fun wifiJuso(cm: ConnectivityManager, n: Network): String? {
        val lp = cm.getLinkProperties(n) ?: return null
        for (la in lp.linkAddresses) {
            val a = la.address
            if (a !is Inet4Address) continue
            val h = a.hostAddress ?: continue
            val p = h.split(".").mapNotNull { it.toIntOrNull() }
            if (p.size != 4) continue
            if (p[0] == 10 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && p[1] in 16..31)) return h
        }
        return null
    }

    private fun mutgiJari(n: Network, base: String, gil: String, ttae: Int): String? = try {
        val k = n.openConnection(URL(base + gil)) as HttpURLConnection
        k.connectTimeout = ttae; k.readTimeout = ttae; k.useCaches = false
        val t = if (k.responseCode == 200) k.inputStream.bufferedReader().readText() else null
        k.disconnect(); t
    } catch (_: Exception) { null }

    /** 행사장 방송에 묻기 — 행사장 망으로만, 지금 찾아 둔 자리로 */
    fun mutgi(c: Context, gil: String, ttae: Int = 3000): String? {
        val n = mang(c) ?: return null
        return mutgiJari(n, jari, gil, ttae)
    }

    /** 2.4.0 지금 자리의 듣기 줄기 주소 */
    fun stream(): URL = URL("$jari/stream")

    private fun hyeonjangInga(t: String?): String? {
        if (t == null) return null
        return try {
            val j = JSONObject(t)
            if (!j.optBoolean("hyeonjang")) null else j.optString("jemok", "")
        } catch (_: Exception) { null }
    }

    /** 행사장인가 — 현장 방송이 답하면 행사 이름(없으면 빈 글), 아니면 null.
     *  2.4.0 지금 자리 → 기억한 자리 → 플린트 2 자리 → 와이파이 안 8080번 문(30초에 한 번) 차례로 찾음 */
    fun salpigi(c: Context): String? {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = mang(c) ?: return null
        val gieok = pref(c).getString("hyeonjang_jari", "") ?: ""
        val hubo = LinkedHashSet<String>()
        hubo.add(jari); if (gieok.isNotEmpty()) hubo.add(gieok); hubo.add("http://$IP:$WEB")
        for (b in hubo) {
            val jm = hyeonjangInga(mutgiJari(n, b, "/status", 1500))
            if (jm != null) { chajeum(c, b); return jm }
        }
        val now = System.currentTimeMillis()
        if (now - chatgiTtae < CHATGI_GANGYEOK) return null
        chatgiTtae = now
        val nae = wifiJuso(cm, n) ?: return null
        val ap = nae.substring(0, nae.lastIndexOf('.') + 1)
        val chajeun = AtomicReference<Pair<String, String>?>(null)
        val pool = Executors.newFixedThreadPool(48)
        try {
            for (i in 1..254) {
                val ip = ap + i
                if (ip == nae) continue
                pool.execute {
                    if (chajeun.get() != null) return@execute
                    val b = "http://$ip:$SONGCHUL_MUN"
                    val jm = hyeonjangInga(mutgiJari(n, b, "/status", 900))
                    if (jm != null) chajeun.compareAndSet(null, Pair(b, jm))
                }
            }
            pool.shutdown()
            pool.awaitTermination(12, TimeUnit.SECONDS)
        } catch (_: Exception) { } finally { pool.shutdownNow() }
        val r = chajeun.get() ?: return null
        chajeum(c, r.first)
        return r.second
    }

    private fun chajeum(c: Context, b: String) {
        jari = b
        pref(c).edit().putString("hyeonjang_jari", b).apply()
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
