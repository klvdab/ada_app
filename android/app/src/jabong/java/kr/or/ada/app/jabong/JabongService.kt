// 안드로이드 자봉 — 화면이 꺼져도 점지도 그리기를 붙들어 두는 알림 칸의 자봉 (2.3.0, 빌드 261001-A2에 만듦, 261002-J1 에 JabongActivity.kt 에서 떼어 옮김)
// 위치와 몸 센서가 잠들지 않게 — 아이폰은 위치 바탕 실행이 같은 일을 함.
package kr.or.ada.app.jabong

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import kr.or.ada.app.gilnun.Girok
import kr.or.ada.app.gilnun.Wichi

class JabongService : Service() {
    private var jamsoe: PowerManager.WakeLock? = null
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        JabongBonche.sijak(this)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("jabong", "자봉 점지도 그리기", NotificationManager.IMPORTANCE_LOW))
        val yeolgi = PendingIntent.getActivity(this, 0, Intent(this, JabongActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "jabong") else Notification.Builder(this)
        val n = b.setContentTitle("자봉").setContentText("점지도를 그리는 중입니다. 화면이 꺼져도 이어 그립니다")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentIntent(yeolgi).setOngoing(true).build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) else startForeground(2, n)
        } catch (e: Exception) {
            Girok.namgi("jb_service", mapOf("ok" to false, "e" to (e.message ?: "")))
            stopSelf(); return START_NOT_STICKY
        }
        if (jamsoe == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            jamsoe = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jabong:geurigi").apply { acquire(3 * 60 * 60 * 1000L) }
        }
        Wichi.sijak(this)
        return START_STICKY
    }

    override fun onDestroy() {
        try { jamsoe?.takeIf { it.isHeld }?.release() } catch (e: Exception) {}
        jamsoe = null
        super.onDestroy()
    }

    companion object {
        fun kyeogi(c: Context) {
            if (!Wichi.heorakItda) return
            try {
                val i = Intent(c, JabongService::class.java)
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
            } catch (e: Exception) { Girok.namgi("jb_service", mapOf("ok" to false, "kyeogi" to (e.message ?: ""))) }
        }
        fun kkeugi(c: Context?) { c?.let { try { it.stopService(Intent(it, JabongService::class.java)) } catch (e: Exception) {} } }
    }
}
