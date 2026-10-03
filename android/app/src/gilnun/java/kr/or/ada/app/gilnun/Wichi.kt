// 안드로이드 길눈 — 위치 엔진(2.1.0, 빌드 261001-A3 — 몸 센서를 함께, 대표님 지시)
// 2.1.0 길눈 앱에서는 몸 센서(MomSensor)를 늘 켜 둠 — 폰 걸음 센서가 몇 초씩 몰아서 알려 주는 늦음을 몸 센서 걸음으로 메우고,
//   방향은 자이로 합성 방향으로(쇠붙이·건물 옆에서도 틀어지지 않음). 자봉 앱에서는 켜지 않음(자봉은 그리기 때만 따로 켬).
// 위성·방향(폰 방향 센서)·걸음(폰 걸음 센서)을 한 곳에서 받습니다. 화면들은 위성에 직접 붙지 않고 여기서만 받습니다.
// 점지도의 바탕은 걸음 — 위성이 6초 넘게 끊기거나 오차가 25미터를 넘으면 걸음 수 × 보폭 × 방향으로 자리를 이어 셈(아이폰과 같음).
// 화면이 꺼져도 이어 돌도록 WichiService(알림 칸의 길눈)가 붙들어 둡니다.
package kr.or.ada.app.gilnun

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Jari(
    val lat: Double,
    val lon: Double,
    val ochae: Double,      // 미터
    val banghyang: Double,  // 북쪽 기준 도, 모르면 -1
    val sokdo: Double,      // 초속 미터
    val ttae: Long,
    val georeumChu: Boolean // 위성이 흐려 걸음으로 이어 셈한 자리
)

object Wichi : SensorEventListener, LocationListener {
    private var ctx: Context? = null
    private var lm: LocationManager? = null
    private var sm: SensorManager? = null
    private val main = Handler(Looper.getMainLooper())
    private var dolgo = false

    var jigeum: Jari? = null
        private set
    var batunSu = 0
        private set
    private var majimakWiseong = 0L
    /** 2.9.0 마지막으로 위성이 잡힌 때(탈것 알아채기가 씀) */
    val majimakWiseongTtae: Long get() = majimakWiseong
    /** 폰 방향(북쪽 기준 도), 모르면 -1 */
    var nachimban = -1.0
        private set
    /** 앱이 켜진 뒤 센 걸음 */
    var georeumSu = 0
        private set
    /** 오늘 걸음 */
    var oneulGeoreum = 0
        private set
    private var georeumCheot = -1
    /** 2.1.0 몸 센서로 걸음 늦음 메우기 */
    var momBbareum = false
        private set
    private var manboNujeok = 0
    private var momManboTtae: Int? = null
    private var iegoGijun = 0
    private val deutneun = ArrayList<(Jari) -> Unit>()

    fun deutgi(f: (Jari) -> Unit) { deutneun.add(f) }

    val heorakItda: Boolean
        get() = ctx?.let { ContextCompat.checkSelfPermission(it, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED } == true
    val georeumHeorak: Boolean
        get() = Build.VERSION.SDK_INT < 29 || ctx?.let { ContextCompat.checkSelfPermission(it, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED } == true

    fun sijak(c: Context) {
        ctx = c.applicationContext
        if (dolgo) { wiseongDolligi(); return }
        dolgo = true
        lm = c.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        sm = c.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sm?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        georeumDolligi()
        if (c.packageName == "kr.or.ada.app") momKyeogi(c)
        wiseongDolligi()
        val r = object : Runnable { override fun run() { iegoSem(); main.postDelayed(this, 1000) } }
        main.postDelayed(r, 1000)
    }

    /** 허락을 받은 뒤 다시 부르면 위성·걸음을 엶 */
    @SuppressLint("MissingPermission")
    fun wiseongDolligi() {
        val m = lm ?: return
        if (!heorakItda) return
        try {
            m.removeUpdates(this)
            if (m.isProviderEnabled(LocationManager.GPS_PROVIDER))
                m.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
            if (m.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                m.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 0f, this, Looper.getMainLooper())
            Girok.namgi("wichi_yeolgi", mapOf("gps" to m.isProviderEnabled(LocationManager.GPS_PROVIDER)))
        } catch (e: Exception) {
            Girok.namgi("wichi_oryu", mapOf("e" to (e.message ?: "")))
        }
        georeumDolligi()
    }

    /** 2.1.0 길눈 앱 — 몸 센서를 켜고 걸음마다 받음 */
    private fun momKyeogi(c: Context) {
        MomSensor.gilnunGeoreum = { momGeoreumNal() }
        if (!MomSensor.dollyeo) MomSensor.kyeogi(c)
        momBbareum = true
        Girok.namgi("momsensor", mapOf("on" to true))
    }

    /** 몸 센서가 한 걸음을 잡을 때마다 — 폰 걸음 센서 값 + 그 뒤 몸 센서가 센 걸음(뒤로 줄지 않게) */
    private fun momGeoreumNal() {
        if (!momBbareum || !MomSensor.dollyeo) return
        val ms = MomSensor.georeumSu
        if (momManboTtae == null) { momManboTtae = ms - 1; manboNujeok = georeumSu }
        val bbareun = manboNujeok + (ms - (momManboTtae ?: ms))
        if (bbareun > georeumSu) georeumSu = bbareun
    }

    /** 2.1.0 방향 — 몸 센서를 쓰는 동안은 자이로 합성 방향, 아니면 폰 방향 센서 */
    val hapBang: Double
        get() = if (momBbareum && MomSensor.dollyeo) (MomSensor.hapseong ?: nachimban) else nachimban

    private var georeumDolgo = false
    private fun georeumDolligi() {
        if (georeumDolgo || !georeumHeorak) return
        val s = sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        georeumDolgo = sm?.registerListener(this, s, SensorManager.SENSOR_DELAY_UI) == true
    }

    // MARK: 위성

    override fun onLocationChanged(l: Location) {
        if (System.currentTimeMillis() - l.time > 10000) return
        batunSu += 1
        val heurim = !l.hasAccuracy() || l.accuracy > 25
        val now = System.currentTimeMillis()
        if (l.provider == LocationManager.NETWORK_PROVIDER && majimakWiseong > 0 && now - majimakWiseong < 6000) return
        if (!heurim) majimakWiseong = now
        val j = jigeum
        if (heurim && j != null && j.georeumChu && now - j.ttae < 10000) return
        if (majimakWiseong == 0L) majimakWiseong = now
        val bang = if (l.hasBearing() && l.speed > 1.5f) l.bearing.toDouble() else hapBang
        iegoGijun = georeumSu
        naegi(Jari(l.latitude, l.longitude, if (l.hasAccuracy()) l.accuracy.toDouble() else 99.0, bang, l.speed.toDouble(), now, false))
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("옛 안드로이드")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private fun naegi(j: Jari) {
        jigeum = j
        for (f in deutneun) f(j)
    }

    // MARK: 방향·걸음

    private val rot = FloatArray(9)
    private val ori = FloatArray(3)

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, ori)
                var d = Math.toDegrees(ori[0].toDouble())
                if (d < 0) d += 360.0
                nachimban = d
                MomSensor.nachimbanNeogi(d)   // 2.1.0 자이로 합성 방향이 나침반 쪽으로 천천히 맞춰지게
            }
            Sensor.TYPE_STEP_COUNTER -> {
                val n = e.values[0].toInt()   // 폰을 켠 뒤 센 걸음
                if (georeumCheot < 0) georeumCheot = n
                val m = n - georeumCheot
                manboNujeok = m
                momManboTtae = if (momBbareum && MomSensor.dollyeo) MomSensor.georeumSu else null
                if (!momBbareum || m > georeumSu) georeumSu = m
                val nal = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
                var g = Seoljeong.georeumGijun(nal)
                if (g < 0 || g > n) { g = n; Seoljeong.georeumGijunNoki(nal, n) }
                oneulGeoreum = n - g
            }
        }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    /** 1초마다 — 위성이 끊기거나 흐리면 걸음으로 자리를 이어 셈 */
    private fun iegoSem() {
        val j = jigeum ?: return
        val now = System.currentTimeMillis()
        val kkeunkim = majimakWiseong == 0L || now - majimakWiseong > 6000
        if (!kkeunkim && j.ochae <= 25) return
        val sae = georeumSu - iegoGijun
        val bang = hapBang
        if (sae <= 0 || bang < 0) return
        val geori = sae * Seoljeong.bopok
        val (la, lo) = olgida(j.lat, j.lon, geori, bang)
        iegoGijun = georeumSu
        naegi(Jari(la, lo, j.ochae + geori * 0.1, bang, geori, now, true))
    }

    // MARK: 셈

    fun geori(a1: Double, o1: Double, a2: Double, o2: Double): Double {
        val r = 6371000.0
        val p = Math.PI / 180
        val dla = (a2 - a1) * p
        val dlo = (o2 - o1) * p
        val x = sin(dla / 2) * sin(dla / 2) + cos(a1 * p) * cos(a2 * p) * sin(dlo / 2) * sin(dlo / 2)
        return 2 * r * atan2(sqrt(x), sqrt(1 - x))
    }

    fun olgida(la: Double, lo: Double, m: Double, deg: Double): Pair<Double, Double> {
        val p = Math.PI / 180
        val dla = m * cos(deg * p) / 111320.0
        val dlo = m * sin(deg * p) / (111320.0 * cos(la * p))
        return Pair(la + dla, lo + dlo)
    }

    /** 북쪽 기준 도를 말로 */
    fun bangwiMal(d: Double): String {
        if (d < 0) return "방향을 아직 모릅니다"
        val i = (((d + 22.5) % 360) / 45).toInt()
        return listOf("북쪽", "북동쪽", "동쪽", "남동쪽", "남쪽", "남서쪽", "서쪽", "북서쪽")[i]
    }
}

/** 화면이 꺼져도 위치를 붙들어 두는 알림 칸의 길눈 */
class WichiService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Seoljeong.sijak(this)   // 앱 없이 다시 살아난 때를 위해(보폭·설정)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("gilnun", "길눈 안내", NotificationManager.IMPORTANCE_LOW))
        }
        val yeolgi = PendingIntent.getActivity(
            this, 0, Intent(this, GilnunActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "gilnun") else Notification.Builder(this)
        val n = b.setContentTitle("길눈")
            .setContentText("화면이 꺼져도 길눈이 위치를 이어 봅니다")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(yeolgi)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(1, n)
            Girok.namgi("wichi_service", mapOf("ok" to true))
        } catch (e: Exception) {
            Girok.namgi("wichi_service", mapOf("ok" to false, "e" to (e.message ?: "")))
            stopSelf()
            return START_NOT_STICKY
        }
        Wichi.sijak(this)
        return START_STICKY
    }

    companion object {
        fun kyeogi(c: Context) {
            if (!Wichi.heorakItda) return
            try {
                val i = Intent(c, WichiService::class.java)
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
            } catch (e: Exception) {
                Girok.namgi("wichi_service", mapOf("ok" to false, "kyeogi" to (e.message ?: "")))
            }
        }
    }
}
