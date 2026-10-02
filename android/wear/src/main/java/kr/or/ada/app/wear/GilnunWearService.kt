// 갤럭시 워치 길눈 — 폰 길눈에서 오는 것을 받는 곳 (2.5.0판, 빌드 261002-A8, 대표님 지시)
// 워치 길눈 화면이 닫혀 있어도 구글 플레이 서비스가 깨워 넘겨 줌(아이폰 WCSessionDelegate 와 같은 자리).
//   메시지 /gilnun/allim(방향 진동·가리키기·음향신호기 결과·걷는 중) · /gilnun/dap(부탁의 답), 데이터 항목 /gilnun/sangtae(마지막 안내·다음 갈림길·걷는 중)
// 받은 것은 화면 줄(main)로 넘겨 WatchModel 이 다룸
package kr.or.ada.app.wear

import android.os.Handler
import android.os.Looper
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

class GilnunWearService : WearableListenerService() {
    private val main = Handler(Looper.getMainLooper())

    override fun onMessageReceived(e: MessageEvent) {
        val path = e.path
        if (path != WatchModel.P_ALLIM && path != WatchModel.P_DAP) return
        val o = try { JSONObject(String(e.data, Charsets.UTF_8)) } catch (x: Exception) { return }
        val c = applicationContext
        main.post {
            WatchModel.sijak(c)
            WatchModel.messageBatda(path, o)
        }
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
