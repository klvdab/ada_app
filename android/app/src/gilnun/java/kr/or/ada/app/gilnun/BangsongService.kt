// 안드로이드 길눈 — 알림 칸의 길눈 방송(b5_bangsong, 미디어3 MediaSessionService 포그라운드 서비스)
// 음악·라디오·TV 소리가 화면이 꺼져도, 다른 탭·다른 앱으로 가셔도 끊기지 않게 살려 둡니다(아이폰 소리 자리 .playback 과 같은 뜻).
// 재생기는 Bangsong 엔진이 쥐고, 이 서비스는 그 재생기에 미디어 세션을 씌워 알림 칸·잠금 화면·이어폰 단추·워치 미디어 조절에 내어 줍니다.
//   ① 방송이 있는 동안(멈춰 두신 동안도) 포그라운드로 머묾 — 길눈 말이나 말로 하기 때문에 잠깐 멈췄다 다시 틀 때 막히지 않게
//   ② 그만 듣기를 하시면 내려가고 알림도 사라짐
//   ③ 알림을 누르시면 길눈 화면으로
package kr.or.ada.app.gilnun

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@OptIn(UnstableApi::class)
class BangsongService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        jigeum = this
        val p = Bangsong.sessionPlayer
        if (p == null) { stopSelf(); return }
        val b = MediaSession.Builder(this, p).setId("gilnun_bangsong")
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            b.setSessionActivity(PendingIntent.getActivity(this, 5, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        val s = b.build()
        session = s
        addSession(s)   // 이어 붙는 조절기가 없어도 알림 칸에 뜨게
        Girok.namgi("bangsong_service", mapOf("on" to true))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** 방송이 있는 동안은 멈춰 두셔도 포그라운드로 머묾(기사 읽기는 길눈 말소리로 하므로 빼고) */
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val meomum = Bangsong.itda && Bangsong.jong != BangsongJong.GISA
        super.onUpdateNotification(session, startInForegroundRequired || meomum)
    }

    /** 최근 앱에서 길눈을 밀어 닫으셔도 방송이 있으면 이어 틂, 없으면 내려감 */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!Bangsong.itda) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        session?.let {
            try { removeSession(it) } catch (e: Exception) {}
            it.release()   // 재생기는 엔진 것이라 놓지 않음
        }
        session = null
        if (jigeum === this) jigeum = null
        Girok.namgi("bangsong_service", mapOf("on" to false))
        super.onDestroy()
    }

    companion object {
        @Volatile var jigeum: BangsongService? = null
            private set

        /** 방송을 틀기 바로 앞에 — 아직 없으면 켬(앱이 앞에 있을 때 부르므로 막히지 않음) */
        fun kyeogi(c: Context) {
            if (jigeum != null) return
            try {
                c.startService(Intent(c, BangsongService::class.java))
            } catch (e: Exception) {
                Girok.namgi("bangsong_oryu", mapOf("dan" to "service", "e" to (e.message ?: "")))
            }
        }

        /** 그만 듣기 — 내려감(알림도 사라짐) */
        fun kkeugi() {
            val s = jigeum ?: return
            jigeum = null   // 내려가는 틈에 곧바로 새로 트시면 새로 켜지게
            try { s.stopSelf() } catch (e: Exception) {}
        }
    }
}
