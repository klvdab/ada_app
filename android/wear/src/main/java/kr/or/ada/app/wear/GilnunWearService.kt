// 갤럭시 워치 길눈 — 폰 길눈에서 오는 것을 받는 곳 (2.5.0판, 빌드 261002-A8, 대표님 지시)
// 워치 길눈 화면이 닫혀 있어도 구글 플레이 서비스가 깨워 넘겨 줌(아이폰 WCSessionDelegate 와 같은 자리).
//   메시지 /gilnun/allim(방향 진동·가리키기·음향신호기 결과·걷는 중) · /gilnun/dap(부탁의 답), 데이터 항목 /gilnun/sangtae(마지막 안내·다음 갈림길·걷는 중)
// 받은 것은 화면 줄(main)로 넘겨 WatchModel 이 다룸
package kr.or.ada.app.wear

import android.os.Handler
import android.os.Looper
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import java.io.File
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

class GilnunWearService : WearableListenerService() {
    companion object {
        const val P_DONGYEONG = "/gilnun/dongyeong"
        const val P_DONGYEONG_JUSO = "/gilnun/dongyeong_juso"
        @Volatile private var badneun: File? = null
    }
    private val main = Handler(Looper.getMainLooper())

    override fun onMessageReceived(e: MessageEvent) {
        val path = e.path
        // 2.7.0 폰 길눈이 보낸 동영상 주소 — 워치에서 틂
        if (path == P_DONGYEONG_JUSO) {
            val u = try { JSONObject(String(e.data, Charsets.UTF_8)).optString("u", "") } catch (x: Exception) { "" }
            if (u.startsWith("http")) dongyeongYeolgi(u)
            return
        }
        if (path != WatchModel.P_ALLIM && path != WatchModel.P_DAP) return
        val o = try { JSONObject(String(e.data, Charsets.UTF_8)) } catch (x: Exception) { return }
        val c = applicationContext
        main.post {
            WatchModel.sijak(c)
            WatchModel.messageBatda(path, o)
        }
    }

    // MARK: 2.7.0 동영상(아이폰 애플워치 동영상 틀기와 같은 뜻) — 폰이 채널로 보낸 파일을 받아 다 받으면 틂

    override fun onChannelOpened(ch: ChannelClient.Channel) {
        if (ch.path != P_DONGYEONG) return
        val d = File(filesDir, "dongyeong")
        if (!d.exists()) d.mkdirs()
        d.listFiles()?.forEach { it.delete() }   // 워치 자리가 좁아 받은 동영상은 한 개만 둠
        val f = File(d, "batun_${System.currentTimeMillis()}.mp4")
        badneun = f
        try { Wearable.getChannelClient(this).receiveFile(ch, Uri.fromFile(f), false) } catch (x: Exception) {}
    }

    override fun onInputClosed(ch: ChannelClient.Channel, closeReason: Int, appSpecificErrorCode: Int) {
        if (ch.path != P_DONGYEONG) return
        val f = badneun ?: return
        badneun = null
        if (closeReason == ChannelClient.ChannelCallback.CLOSE_REASON_NORMAL && f.exists() && f.length() > 0) dongyeongYeolgi(f.absolutePath)
        else f.delete()
    }

    /** 동영상 화면을 엶 — 워치가 뒤에 있어 바로 못 열면 알림을 띄워 두드려 열게 */
    private fun dongyeongYeolgi(juso: String) {
        val i = Intent(this, DongyeongWatchActivity::class.java).putExtra(DongyeongWatchActivity.JUSO, juso)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel("dongyeong", "동영상", NotificationManager.IMPORTANCE_HIGH))
            val pi = PendingIntent.getActivity(this, 27, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = Notification.Builder(this, "dongyeong")
                .setContentTitle("길눈 동영상")
                .setContentText("폰에서 보낸 동영상 — 두드리면 틉니다")
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(DongyeongWatchActivity.ALLIM_BEON, n)
        } catch (x: Exception) {}
        try { startActivity(i) } catch (x: Exception) {}
    }

    override fun onDataChanged(buf: DataEventBuffer) {
        val l = ArrayList<JSONObject>()
        try {
            for (ev in buf) {
                if (ev.type != DataEvent.TYPE_CHANGED || ev.dataItem.uri.path != WatchModel.P_SANGTAE) continue
                l.add(WatchModel.dataMapJson(DataMapItem.fromDataItem(ev.dataItem).dataMap))
            }
        } catch (x: Exception) {}
        if (l.isEmpty()) return
        val c = applicationContext
        main.post {
            WatchModel.sijak(c)
            for (o in l) WatchModel.sangtaeBatda(o, true)
        }
    }
}
