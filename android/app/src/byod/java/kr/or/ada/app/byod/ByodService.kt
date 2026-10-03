// BYOD 방송 — 방송 일꾼 (1.0.0판, 빌드 261003-B1, 이사장님 승인 2026-10-03)
// 화면을 잠가도, 오래 켜 두어도 멈추지 않게 앞에서 도는 일꾼(포그라운드 서비스)으로 돕니다.
// 알림 줄에는 "BYOD 방송 중" 하나만 둡니다. 듣는 분 수는 바뀔 때만 고쳐 씁니다.
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
            Bang.jalmot = "방송 일꾼을 켜지 못했습니다. BYOD 방송 시작 단추를 다시 누르십시오."
            Bang.kyeojim = false
            Bang.allyeo()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Bang.kyeojim) sijak()
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
        val so = Sori(this) { b -> s.ppurida(b) }
        so.sijak()
        sori = so
        Bang.gwanchal.add(gwan)
        Bang.allyeo()
    }

    override fun onDestroy() {
        Bang.gwanchal.remove(gwan)
        Bang.keu = false
        Bang.kyeojim = false
        try { sori?.meomchugi() } catch (_: Exception) { }
        try { seobeo?.meomchugi() } catch (_: Exception) { }
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
            val ch = NotificationChannel(CHANNEL, "BYOD 방송", NotificationManager.IMPORTANCE_LOW)
            ch.setSound(null, null)
            ch.enableVibration(false)
            nm.createNotificationChannel(ch)
        }
    }

    private fun allim(geul: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, ByodActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setContentTitle("BYOD 방송 중")
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
