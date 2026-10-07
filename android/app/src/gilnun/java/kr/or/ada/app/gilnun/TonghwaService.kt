// 안드로이드 길눈 2.25.0(빌드 261007-A13, 대장클, 이사장님 지시 「꼼꼼하게 다른 기능들도 점검해 줘」) — 긴급통화 중 알림 칸 서비스
// 안드로이드 11부터는 화면이 꺼지거나(잠금 단추) 길눈이 뒤로 가면 마이크가 무음이 되고 카메라가 끊김 → 상대가 목소리를 못 들음.
// 통화하는 동안만 마이크·카메라 종류의 앞 서비스를 켜 두어 잠가도 통화가 이어지게 함(아이폰은 background audio 로 이미 이어짐).
package kr.or.ada.app.gilnun

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

class TonghwaService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("gilnun_tonghwa", "길눈 긴급통화", NotificationManager.IMPORTANCE_LOW))
        val yeolgi = PendingIntent.getActivity(this, 7, Intent(this, GilnunActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "gilnun_tonghwa") else Notification.Builder(this)
        val n = b.setContentTitle("길눈 긴급통화")
            .setContentText("통화 중입니다. 화면을 잠가도 이어집니다.")
            .setSmallIcon(android.R.drawable.sym_call_outgoing)
            .setContentIntent(yeolgi)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                var t = 0
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) t = t or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) t = t or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                if (t == 0) { stopSelf(); return START_NOT_STICKY }
                startForeground(11, n, t)
            } else startForeground(11, n)
            Girok.namgi("tonghwa_service", mapOf("on" to true))
        } catch (e: Exception) {
            Girok.namgi("tonghwa_service", mapOf("ok" to false, "e" to (e.message ?: "").take(80)))
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        jigeum = false
        super.onDestroy()
    }

    companion object {
        @Volatile private var jigeum = false

        /** 통화가 이어지는 동안 — 길눈이 앞에 있을 때 부름 */
        fun kyeogi(c: Context) {
            if (jigeum) return
            jigeum = true
            try {
                val i = Intent(c, TonghwaService::class.java)
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
            } catch (e: Exception) {
                jigeum = false
                Girok.namgi("tonghwa_service", mapOf("ok" to false, "kyeogi" to (e.message ?: "").take(80)))
            }
        }

        fun kkeugi(c: Context) {
            if (!jigeum) return
            jigeum = false
            try { c.stopService(Intent(c, TonghwaService::class.java)) } catch (e: Exception) {}
        }
    }
}
