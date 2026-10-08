// AI점자도서관 안드로이드 — 독서기 소리를 살려 두는 서비스 (0.4.4판, 빌드 261008-L8, 도서관 창 클, 이사장님 승인 「1」)
// 아이폰처럼 화면을 끄거나 다른 앱으로 가셔도 책을 계속 읽게 합니다(미디어3 MediaSessionService 포그라운드 서비스).
// 재생기는 독서기(Dokseo)가 쥐고, 이 서비스는 그 재생기에 미디어 세션을 씌워 알림 칸·잠금 화면·이어폰 단추에 내어 줍니다.
//   ① 읽는 동안 포그라운드로 머묾 — 폰이 앱을 멈추지 않음
//   ② 이어폰 단추: 한 번 누르면 멈춤과 읽기, 다음과 이전은 글자책이면 다음 문단·앞 문단, 소리책이면 30초 앞뒤
//   ③ 알림을 누르시면 도서관 화면으로
// 길눈의 BangsongService 와 같은 짜임(대장클이 만든 것을 본뜸).
package kr.or.ada.app.lib

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@OptIn(UnstableApi::class)
class DokseoService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        jigeum = this
        if (!Store.itna()) Store.load(applicationContext)
        Api.ctx = applicationContext
        val p = Dokseo.sessionPlayer(applicationContext)
        val b = MediaSession.Builder(this, p).setId("lib_dokseo")
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            b.setSessionActivity(PendingIntent.getActivity(this, 7, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        val s = b.build()
        session = s
        addSession(s)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** 최근 앱에서 도서관을 밀어 닫으셔도 읽는 중이면 이어 읽고, 아니면 내려감 */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!Dokseo.playing) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        session?.let {
            try { removeSession(it) } catch (e: Exception) {}
            it.release()   // 재생기는 독서기 것이라 놓지 않음
        }
        session = null
        if (jigeum === this) jigeum = null
        super.onDestroy()
    }

    companion object {
        @Volatile var jigeum: DokseoService? = null
            private set

        /** 읽기를 시작하기 바로 앞에 — 아직 없으면 켬(앱이 앞에 있을 때 부르므로 막히지 않음) */
        fun kyeogi(c: Context) {
            if (jigeum != null) return
            try { c.startService(Intent(c, DokseoService::class.java)) } catch (e: Exception) {}
        }
    }
}

/** 미디어 세션이 받는 단추(이어폰·알림 칸·잠금 화면)를 독서기의 뜻으로 바꿈 */
@OptIn(UnstableApi::class)
internal class DokseoSessionPlayer(p: Player) : ForwardingPlayer(p) {
    override fun play() { if (!Dokseo.playing) Dokseo.play() }
    override fun pause() { if (Dokseo.playing) Dokseo.pause() }
    override fun setPlayWhenReady(playWhenReady: Boolean) { if (playWhenReady) play() else pause() }
    override fun seekToNext() { Dokseo.next() }
    override fun seekToNextMediaItem() { Dokseo.next() }
    override fun seekToPrevious() { Dokseo.prev() }
    override fun seekToPreviousMediaItem() { Dokseo.prev() }

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .addAll(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()

    override fun isCommandAvailable(command: Int): Boolean = when (command) {
        Player.COMMAND_PLAY_PAUSE, Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
        else -> super.isCommandAvailable(command)
    }
}
