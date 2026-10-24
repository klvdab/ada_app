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
    // 1.6.0 (261019-B14) 방송 지킴이 — 해설 소리가 6초 넘게 안 들어오면 3초에 한 번 조용한 알림 소리와 떨림
    @Volatile private var soriTtae = 0L
    private var jikim: Jikim? = null
    private fun jikimSijak() {
        soriTtae = System.currentTimeMillis()
        jikim?.meomchugi()
        // 1.9.0 (261024-B17) 피디 화면에서 해설을 멈춰 두었을 때(/cue?v=0, 대기 음악이 나감)는 소리가 안 들어오는 것이 맞으므로 울리지 않음
        jikim = Jikim(this, { Bang.kyeojim && Bang.keu && System.currentTimeMillis() - soriTtae > 6000 }) { m ->
            if (m) Bang.jalmot = "해설 소리가 들어오지 않습니다. 마이크와 소리 받는 곳을 살펴 주십시오. 방송은 켜져 있습니다."
            else if (Bang.jalmot.startsWith("해설 소리가 들어오지")) Bang.jalmot = ""
            Bang.allyeo()
        }.also { it.sijak() }
    }
    private val gwan: () -> Unit = { allimGochigi() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        chaeneol()
        Girok.sijak(applicationContext)   // 1.8.0
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = allim("방송을 준비하고 있습니다.")
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                // 1.9.0 (261024-B17) 무대 영상 녹화 — 켜 두었고 카메라 허락이 있으면 카메라 종류도 함께(화면을 잠가도 카메라가 이어지게).
                // 카메라 종류를 붙이지 못하면 마이크만으로 방송을 그대로 켬(녹화만 못 함)
                val kam = MudaeNokhwa.kyeojim(this) && MudaeNokhwa.heorak(this)
                if (kam) {
                    try {
                        startForeground(NOTI_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
                        MudaeNokhwa.ilkkunMotham = false
                    } catch (e: Exception) {
                        MudaeNokhwa.ilkkunMotham = true
                        startForeground(NOTI_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                    }
                } else {
                    MudaeNokhwa.ilkkunMotham = false
                    startForeground(NOTI_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                }
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
        wifiJabgi()   // 1.8.1 (261024-B16a) 인터넷 없는 공유기 와이파이를 폰이 버리지 않게 붙잡아 둠
        Bang.juso = jusoChatgi()
        Bang.keu = true
        Bang.kyeojim = true
        Bang.sigakTtae = System.currentTimeMillis()
        // 1.7.0 (261021-B15) 아이피타임 방식 — 이 기기가 행사 녹음을 함(플린트 2 방식은 공유기가 녹음)
        run {
            val p = getSharedPreferences("byod", Context.MODE_PRIVATE)
            GigiNokeum.sijak(this, (p.getString("haengsa_jemok", "") ?: "").trim(), (p.getString("haeseolsa", "") ?: "").trim())
        }
        MudaeNokhwa.sijak(this, "iptime", GigiNokeum.sid)   // 1.9.0 (261024-B17) 무대 영상 녹화 — 켜 두셨을 때만, 이 행사(sid) 이름으로 짝지음
        val so = Sori(this) { b -> soriTtae = System.currentTimeMillis(); s.ppurida(b); GigiNokeum.sseugi(b); Inteonet.neoki(b) }   // 1.5.0 인터넷 방송에도 같은 소리, 1.7.0 녹음
        so.sijak()
        sori = so
        jikimSijak()   // 1.6.0
        Bang.gwanchal.add(gwan)
        inteonetSijak()
        Bang.allyeo()
        girokSijak("iptime")   // 1.8.0
    }

    // 1.8.0 (261023-B16) 이용 기록과, 듣는 앱이 이 폰을 찾을 수 있는지 10초마다 스스로 확인(아이피타임 방식)
    @Volatile private var girokDolgo = false
    @Volatile private var girokSeq = 0
    private fun girokSijak(bangsik: String) {
        Girok.namgi("bang_sijak", mapOf("bangsik" to bangsik, "juso" to Bang.juso, "inteonet_hamkke" to Inteonet.hamkke(this), "jeonhwa" to (Inteonet.jeonhwa(this).length >= 9)))
        Bang.mundapTtae = 0L
        girokDolgo = true
        val nae = ++girokSeq
        Thread({
            var cho = 0
            while (girokDolgo && nae == girokSeq) {
                if (bangsik == "iptime") {
                    // 1.8.1 (261024-B16a, 이사장님 지시 「고쳐 지금 당장」) 10월 24일 상암 — 송출 폰이 아이피타임에 붙고 2분 뒤 모바일 데이터로 넘어가
                    //   주소가 192.0.0.4 로 바뀌어 듣는 분 0명. 방송 중 주소가 공유기 주소(192.168…)에서 벗어나면 바로 크게 알리고, 돌아오면 주소를 고침
                    val jj = jusoChatgi()
                    val host = jj.removePrefix("http://").substringBefore(":")
                    if (jj.isNotEmpty() && host.startsWith("192.168.")) {
                        if (Bang.jalmot.startsWith("공유기에서 떨어졌습니다")) { Bang.jalmot = ""; Bang.allyeo() }
                        if (jj != Bang.juso) { Bang.juso = jj; Bang.allyeo() }
                    } else if (!Bang.jalmot.startsWith("공유기에서 떨어졌습니다")) {
                        Bang.jalmot = "공유기에서 떨어졌습니다. 이 폰이 모바일 데이터로 넘어가 듣는 분이 방송을 찾지 못합니다. 모바일 데이터를 끄고 아이피타임 와이파이에 다시 붙이십시오. 인터넷 없음이라고 물으면 연결 유지를 누르십시오."
                        Bang.allyeo()
                        Girok.namgi("gongyugi_tteoreojim", mapOf("juso" to jj))
                        try {
                            val v = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                            @Suppress("DEPRECATION")
                            if (Build.VERSION.SDK_INT >= 26) v.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 600, 300, 600, 300, 600), -1)) else v.vibrate(longArrayOf(0, 600, 300, 600, 300, 600), -1)
                        } catch (_: Exception) { }
                    }
                    val ok = try {
                        val k = java.net.URL(Bang.juso.trimEnd('/') + "/status").openConnection() as java.net.HttpURLConnection
                        k.connectTimeout = 2000; k.readTimeout = 2000; k.useCaches = false
                        val r = k.responseCode == 200 && k.inputStream.bufferedReader().readText().contains("\"hyeonjang\":true")
                        k.disconnect(); r
                    } catch (_: Exception) { false }
                    val jeon = System.currentTimeMillis() - Bang.mundapTtae < 25_000
                    if (ok) Bang.mundapTtae = System.currentTimeMillis()
                    if (cho == 0 || ok != jeon) Girok.namgi("mundap", mapOf("ok" to ok, "juso" to Bang.juso))
                }
                if (cho % (if (bangsik == "iptime") 12 else 6) == 0 && cho > 0) Girok.namgi("deutneun", mapOf("n" to Bang.deutnunSu))
                cho++
                try { Thread.sleep(if (bangsik == "iptime") 5_000 else 10_000) } catch (_: InterruptedException) { }
            }
        }, "byod-girok").apply { isDaemon = true }.start()
    }

    // 1.8.1 (261024-B16a) 인터넷 없는 와이파이 붙잡아 두기 — 안드로이드는 인터넷이 안 되는 와이파이를 끊고 모바일 데이터로 옮겨 가는데,
    //   이 앱이 「인터넷 없어도 되는 와이파이」를 청해 들고 있으면 그 와이파이를 끊지 않음. 방송을 멈추면 놓음
    private var wifiCb: android.net.ConnectivityManager.NetworkCallback? = null
    private fun wifiJabgi() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val req = android.net.NetworkRequest.Builder()
                .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            val cb = object : android.net.ConnectivityManager.NetworkCallback() { }
            cm.requestNetwork(req, cb)
            wifiCb = cb
        } catch (_: Exception) { }
    }
    private fun wifiNoki() {
        val cb = wifiCb ?: return
        wifiCb = null
        try { (getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).unregisterNetworkCallback(cb) } catch (_: Exception) { }
    }

    // 1.2.0 플린트 2 방식 — 소리 받기는 그대로, 받은 소리를 공유기로 보냄
    private fun flintSijak() {
        Flint.haengsaJigeum = ""   // 1.9.0 (261024-B17) 무대 영상 녹화 — 이번 방송의 행사 이름은 공유기가 새로 알려 줌
        val b = Flint.Bonaegi(this)
        b.sijak()
        bonaegi = b
        Bang.juso = Flint.JUSO
        Bang.keu = true
        Bang.kyeojim = true
        Bang.sigakTtae = System.currentTimeMillis()
        MudaeNokhwa.sijak(this, "flint", "")   // 1.9.0 (261024-B17) 무대 영상 녹화 — 켜 두셨을 때만
        val so = Sori(this) { x -> soriTtae = System.currentTimeMillis(); b.neoki(x); Inteonet.neoki(x) }   // 1.5.0 인터넷 방송에도 같은 소리
        so.sijak()
        sori = so
        jikimSijak()   // 1.6.0
        Bang.gwanchal.add(gwan)
        inteonetSijak()
        Bang.allyeo()
        girokSijak("flint")   // 1.8.0
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
        try { jikim?.meomchugi() } catch (_: Exception) { }; jikim = null   // 1.6.0
        songchulBonaegi(false)
        MudaeNokhwa.kkeugi()   // 1.9.0 (261024-B17) 무대 영상 녹화 — 마지막 조각을 마무리한 뒤 행사 올리기로(5초까지 기다림)
        girokDolgo = false; Girok.namgi("bang_kkeut", mapOf("deutneun" to Bang.deutnunSu)); Girok.bonaegi()   // 1.8.0
        Bang.gwanchal.remove(gwan)
        Bang.keu = false
        Bang.kyeojim = false
        Inteonet.meomchugi()   // 1.5.0
        wifiNoki()   // 1.8.1
        try { sori?.meomchugi() } catch (_: Exception) { }
        try { seobeo?.meomchugi() } catch (_: Exception) { }
        val flintDolatda = bonaegi != null
        try { bonaegi?.meomchugi() } catch (_: Exception) { }
        bonaegi = null
        if (flintDolatda) Haengsa.sijak(this, true)   // 1.3.0 행사 마감·녹음과 결과보고 받기·배프에 올리기
        if (GigiNokeum.sid.isNotEmpty()) {   // 1.7.0 아이피타임 방식 녹음 마감 — 긴 녹음을 잇는 일이라 화면 줄기를 붙잡지 않게 따로
            val app = applicationContext
            Thread({ if (GigiNokeum.maegam().isNotEmpty()) { while (Haengsa.dolgo) Thread.sleep(500); Haengsa.sijak(app, false) }; HyeonjangSeobeo.galOlligiDwi(app) }, "byod-maegam").start()   // 1.9.0 갈래 2·3 녹음도 나스에
        }
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
