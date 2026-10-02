// 갤럭시 워치 길눈 — 걷는 동안 깨어 있기 (2.5.0판, 빌드 261002-A8, 대표님 지시: 아이폰 2.35.0 WKExtendedRuntimeSession 을 갤럭시 워치로)
// 앞에 도는 일(foreground service, 몸 활동 갈래) + 워치 화면 위 "진행 중" 표시(OngoingActivity) + 잠깐 깨움(wake lock)
//   폰이 점지도 따라 걷기를 시작하면(또는 손목의 걷는 동안 깨어 있기) — 손목을 내려 화면이 꺼져도 워치 길눈이 멈추지 않고,
//   워치 걸음 센서(팔 흔들림)의 누계를 폰에 보냄(WatchModel.georeumBonae)
//   지팡이 떨림 기록 중에도 켜 둠(걸음 세기 없이)
// 진행 중 표시를 두드리면 워치 길눈 화면이 열림. 화면이 꺼진 채로 안드로이드가 열기를 막으면 화면을 여실 때 다시 엶(WatchModel.hwamyeonDolawa)
package kr.or.ada.app.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.wear.ongoing.OngoingActivity

class GeotgiService : Service(), SensorEventListener {
    companion object {
        private const val CH = "gilnun_geotgi"
        private const val NID = 7
        @Volatile var dolgo = false
            private set

        /** WatchModel 의 georeumOn·bojangOn 에 맞춰 켜고 끔 */
        fun gaengsin(c: Context) {
            val on = WatchModel.georeumOn || WatchModel.bojangOn
            val i = Intent(c, GeotgiService::class.java)
            if (on) {
                try { c.startForegroundService(i) } catch (e: Exception) { WatchModel.kkaeeoMotham() }
            } else if (dolgo) {
                try { c.stopService(i) } catch (e: Exception) {}
            }
        }
    }

    private var wl: PowerManager.WakeLock? = null
    private var sm: SensorManager? = null
    private var georeumDeutneun = false
    private var gijun = -1f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            ServiceCompat.startForeground(this, NID, allim(), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } catch (e: Exception) {
            // 몸 활동 허락이 없거나 안드로이드가 막음
            stopSelf()
            WatchModel.kkaeeoMotham()
            return START_NOT_STICKY
        }
        if (!dolgo) {
            dolgo = true
            try {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gilnun:geotgi").apply { acquire(3 * 60 * 60 * 1000L) }
            } catch (e: Exception) {}
            WatchModel.kkaeeoBakkwim(true)
        }
        if (!WatchModel.georeumOn && !WatchModel.bojangOn) { stopSelf(); return START_NOT_STICKY }
        if (WatchModel.georeumOn && !georeumDeutneun) georeumKyeogi()
        else if (!WatchModel.georeumOn && georeumDeutneun) georeumKkeugi()
        return START_NOT_STICKY
    }

    private fun georeumKyeogi() {
        val m = getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val s = m.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        sm = m
        gijun = -1f
        WatchModel.georeumSaero()
        georeumDeutneun = try { m.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL) } catch (e: Exception) { false }
    }

    private fun georeumKkeugi() {
        try { sm?.unregisterListener(this) } catch (e: Exception) {}
        georeumDeutneun = false
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (e.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val v = e.values[0]
        if (gijun < 0) gijun = v
        WatchModel.georeumBonae((v - gijun).toInt())
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    override fun onDestroy() {
        georeumKkeugi()
        try { wl?.takeIf { it.isHeld }?.release() } catch (e: Exception) {}
        wl = null
        dolgo = false
        WatchModel.kkaeeoBakkwim(false)
        super.onDestroy()
    }

    private fun allim(): android.app.Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CH) == null) {
            nm.createNotificationChannel(NotificationChannel(CH, "걷는 동안 깨어 있기", NotificationManager.IMPORTANCE_LOW))
        }
        val pi = PendingIntent.getActivity(this, 0, Intent(this, WatchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("길눈")
            .setContentText(if (WatchModel.georeumOn) "걷는 동안 깨어 있습니다" else "지팡이 떨림을 기록하는 중입니다")
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setOngoing(true)
            .setContentIntent(pi)
        try {
            OngoingActivity.Builder(applicationContext, NID, b)
                .setStaticIcon(R.drawable.ic_launcher)
                .setTouchIntent(pi)
                .build()
                .apply(applicationContext)
        } catch (e: Exception) {}
        return b.build()
    }
}
