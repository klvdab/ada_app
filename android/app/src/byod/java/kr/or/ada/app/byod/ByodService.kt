// BYOD 방송 — 방송 일꾼 (1.0.0판, 빌드 261003-B1, 이사장님 승인 2026-10-03)
// 화면을 잠가도, 오래 켜 두어도 멈추지 않게 앞에서 도는 일꾼(포그라운드 서비스)으로 돕니다.
// 알림 줄에는 "BYOD 방송 중" 하나만 둡니다. 듣는 분 수는 바뀔 때만 고쳐 씁니다.
// 1.2.0 (261014-B9, 이사장님 승인 「1」, 방송클) 플린트 2 방식 — 이 기기는 소리 한 줄기만 공유기로 보내고, 나눠 보내기는 공유기 안 서버가 함(Flint.kt). 태블릿 서버 방식은 예비로 남김
package kr.or.ada.app.byod

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import java.net.Inet4Address
import java.net.NetworkInterface

@SuppressLint("MissingPermission")
class ByodService : Service() {

    companion object {
        const val CHANNEL = "byod_bangsong"
        const val NOTI_ID = 4455

        fun kyeogi(ctx: Context) {
            val i = Intent(ctx, ByodService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun kkeugi(ctx: Context) {
            ctx.stopService(Intent(ctx, ByodService::class.java))
        }

        // 태블릿의 듣기 주소 — 랜(유선)이 있으면 그것, 없으면 와이파이
        fun jusoChatgi(): String {
            var wlan: String? = null
            var lan: String? = null
            var gita: String? = null
            try {
                for (ni in NetworkInterface.getNetworkInterfaces()) {
                    if (!ni.isUp || ni.isLoopback) continue
                    for (a in ni.inetAddresses) {
                        if (a !is Inet4Address || a.isLoopbackAddress) continue
                        val ip = a.hostAddress ?: continue
                        val nm = ni.name.lowercase()
                        when {
                            nm.startsWith("eth") -> lan = ip
                            nm.startsWith("wlan") -> wlan = ip
                            !nm.startsWith("rmnet") && !nm.startsWith("tun") && !nm.startsWith("dummy") -> if (gita == null) gita = ip
                        }
                    }
                }
            } catch (_: Exception) { }
            val ip = lan ?: wlan ?: gita ?: return ""
            return "http://$ip:${Bang.PORT}/"
        }
    }

    private var seobeo: Seobeo? = null
    private var bonaegi: Flint.Bonaegi? = null   // 1.2.0 플린트 2 방식
    private var sori: Sori? = null
    private var jamgeum: PowerManager.WakeLock? = null
    private var wifiJamgeum: WifiManager.WifiLock? = null
    private var majimakSu = -1
    private val gwan: () -> Unit = { allimGochigi() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        chaeneol()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = allim("방송을 준비하고 있습니다.")
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                startForeground(NOTI_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTI_ID, n)
            }
        } catch (e: Exception) {
            // 안드로이드가 뒤에서 마이크 일꾼을 켜지 못하게 막은 때 — 화면에서 다시 시작해야 함
            Bang.jalmot = "방송 일꾼을 켜지 못했습니다. LVD 송출 시작 단추를 다시 누르십시오."
            Bang.kyeojim = false
            Bang.allyeo()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Bang.kyeojim) sijak()
        songchulH.removeCallbacks(songchulAllim); songchulH.postDelayed(songchulAllim, 1000)   // 1.3.1
        return START_STICKY
    }

    private fun sijak() {
        Bang.jalmot = ""
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            jamgeum = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "byod:bangsong").also { it.setReferenceCounted(false); it.acquire() }
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val mode = if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
            wifiJamgeum = wm.createWifiLock(mode, "byod:wifi").also { it.setReferenceCounted(false); it.acquire() }
        } catch (_: Exception) { }

        if (Flint.flintInga(this)) { flintSijak(); return }   // 1.2.0
        val s = Seobeo(this)
        try {
            s.sijak()
        } catch (e: Exception) {
            Bang.jalmot = "방송 문(8080)을 열지 못했습니다. 태블릿을 다시 켠 뒤 해 보십시오."
            Bang.kyeojim = false
            Bang.allyeo()
            stopSelf()
            return
        }
        seobeo = s
        Bang.juso = jusoChatgi()
        Bang.keu = true
        Bang.kyeojim = true
        Bang.sigakTtae = System.currentTimeMillis()
        val so = Sori(this) { b -> s.ppurida(b); Inteonet.neoki(b) }   // 1.5.0 인터넷 방송에도 같은 소리
        so.sijak()
        sori = so
        Bang.gwanchal.add(gwan)
        inteonetSijak()
        Bang.allyeo()
    }

    // 1.2.0 플린트 2 방식 — 소리 받기는 그대로, 받은 소리를 공유기로 보냄
    private fun flintSijak() {
        val b = Flint.Bonaegi(this)
        b.sijak()
        bonaegi = b
        Bang.juso = Flint.JUSO
        Bang.keu = true
        Bang.kyeojim = true
        Bang.sigakTtae = System.currentTimeMillis()
        val so = Sori(this) { x -> b.neoki(x); Inteonet.neoki(x) }   // 1.5.0 인터넷 방송에도 같은 소리
        so.sijak()
        sori = so
        Bang.gwanchal.add(gwan)
        inteonetSijak()
        Bang.allyeo()
    }

    /** 1.5.0 현장 방송과 함께 인터넷 방송 — 제목은 행사 이름(비면 날짜와 시각), 현장영상해설사 이름을 함께 */
    private fun inteonetSijak() {
        val p = getSharedPreferences("byod", Context.MODE_PRIVATE)
        val jm = (p.getString("haengsa_jemok", "") ?: "").trim().ifEmpty { "현장영상해설 방송 " + java.text.SimpleDateFormat("M월 d일 a h시 m분", java.util.Locale.KOREA).format(java.util.Date()) }
        Inteonet.sijak(this, jm, (p.getString("haeseolsa", "") ?: "").trim())
    }

    // 1.3.1 (261015-B11) 같은 폰의 LVD 방송(듣는 앱)에 「지금 송출 중」을 알림 — 스피커로 틀어 마이크로 되돌아 들어가지 않게.
    // 같은 협회 서명을 가진 앱만 받음(kr.or.ada.permission.SONGCHUL). 듣는 앱이 늦게 켜져도 알도록 20초마다 다시 알림.
    private val songchulH = android.os.Handler(android.os.Looper.getMainLooper())
    private val songchulAllim = object : Runnable {
        override fun run() { songchulBonaegi(Bang.kyeojim); if (Bang.kyeojim) songchulH.postDelayed(this, 20_000) }
    }
    private fun songchulBonaegi(on: Boolean) {
        try {
            sendBroadcast(Intent("kr.or.ada.songchul.SANGTAE").setPackage("kr.or.ada.bfb").putExtra("bangsong", on), "kr.or.ada.permission.SONGCHUL")
        } catch (_: Exception) { }
    }

    override fun onDestroy() {
        songchulH.removeCallbacks(songchulAllim)
        songchulBonaegi(false)
        Bang.gwanchal.remove(gwan)
        Bang.keu = false
        Bang.kyeojim = false
        Inteonet.meomchugi()   // 1.5.0
        try { sori?.meomchugi() } catch (_: Exception) { }
        try { seobeo?.meomchugi() } catch (_: Exception) { }
        val flintDolatda = bonaegi != null
        try { bonaegi?.meomchugi() } catch (_: Exception) { }
        bonaegi = null
        if (flintDolatda) Haengsa.sijak(this, true)   // 1.3.0 행사 마감·녹음과 결과보고 받기·배프에 올리기
        sori = null
        seobeo = null
        try { jamgeum?.release() } catch (_: Exception) { }
        try { wifiJamgeum?.release() } catch (_: Exception) { }
        Bang.deutnunSu = 0
        Bang.allyeo()
        super.onDestroy()
    }

    private fun chaeneol() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(CHANNEL, "LVD 송출", NotificationManager.IMPORTANCE_LOW)
            ch.setSound(null, null)
            ch.enableVibration(false)
            nm.createNotificationChannel(ch)
        }
    }

    private fun allim(geul: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, ByodActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setContentTitle("LVD 송출 중")
            .setContentText(geul)
            .setSmallIcon(applicationInfo.icon)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    // 듣는 분 수가 바뀔 때만 알림 글을 고쳐 씀
    private fun allimGochigi() {
        val su = Bang.deutnunSu
        if (su == majimakSu) return
        majimakSu = su
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTI_ID, allim("듣는 분 ${su}명"))
        } catch (_: Exception) { }
    }
}
