// 안드로이드 길눈 — 폰 ↔ 갤럭시 워치(2.5.0, 빌드 261002-A8, 대표님 지시: 아이폰 길눈의 애플워치 이음을 안드로이드·갤럭시 워치에도)
// 아이폰 WatchLink.swift(2.6.0~2.37.0)와 같은 뜻, 같은 말. 애플의 WatchConnectivity 대신 구글 웨어러블 데이터 층(Wearable Data Layer)을 씁니다.
//   폰 → 워치 상태(데이터 항목 /gilnun/sangtae — 아이폰 applicationContext 와 같음): mal 마지막 안내, daeum 다음 갈림길, ttae, geotneun 점지도 따라 걷는 중, watchBeonho
//   폰 → 워치 알림(메시지 /gilnun/allim — 지금 이어져 있을 때만): jindong 방향 진동(left·right·arrive), gariki 가야 할 쪽(음수면 없음), sinhogiMal·sinhogiOk 음향신호기 결과, geotneun
//   워치 → 폰 부탁(메시지 /gilnun/yocheong, JSON {what, id, cmd, n}): mal | daeum | jari | sinhogi(cmd 1·2) | malhagiSijak | momBang | watchGeoreum(n)
//   폰 → 워치 답(메시지 /gilnun/dap, 부탁의 what·id 를 그대로 실어 보냄)
//   워치 → 폰 지팡이 떨림 기록(데이터 항목 /gilnun/tteollim/<때>, 자산 "bin" + pyo·cho·son·hz) — 폰이 받아 파일로 옮기고 항목을 지운 뒤
//     협회 나스(jeom/tteollim.php, 연구용)로 아이폰과 똑같이 올림. 못 올리면 들고 있다가 다음에(10분마다 다시)
//   폰이 곁에 없을 때 워치가 받을 수 있게 마지막 안내를 나스(watch.php)에도 남김(1.5초 모아서) — 워치 번호는 폰 설정에
// 워치 앱(:wear 모듈)은 같은 앱 번호(kr.or.ada.app)와 같은 서명이어야 서로 이어집니다.
// 구글 플레이 서비스나 워치가 없으면 아무 일도 하지 않습니다(모든 부름을 try 로 감쌈).
package kr.or.ada.app.gilnun

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

object WatchLink {
    const val P_SANGTAE = "/gilnun/sangtae"
    const val P_ALLIM = "/gilnun/allim"
    const val P_YOCHEONG = "/gilnun/yocheong"
    const val P_DAP = "/gilnun/dap"
    const val P_TTEOLLIM = "/gilnun/tteollim"

    private val main = Handler(Looper.getMainLooper())
    private val il = Executors.newSingleThreadExecutor()
    private var ctx: Context? = null
    private val last = HashMap<String, Any>()
    private var sangtaeYeyak: Runnable? = null
    private var seobeoYeyak: Runnable? = null
    private var nodes: List<Node> = emptyList()
    private var nodeTtae = 0L

    // MARK: 세우기

    fun sijak(c: Context) {
        if (ctx != null) return
        val a = c.applicationContext
        ctx = a
        last["watchBeonho"] = Seoljeong.watchBeonho
        try {
            Wearable.getMessageClient(a).addListener(messageDeutgi)
            Wearable.getDataClient(a).addListener(dataDeutgi)
        } catch (e: Exception) {
            Girok.namgi("watch_oryu", mapOf("e" to (e.message ?: "")))
        }
        nodeChatgi()
        sangtaeOlligi()
        tteollimChatgi()   // 워치가 보내 둔 지팡이 떨림 기록이 남아 있으면
        tteollimOlligi()   // 지난번에 못 올린 기록
    }

    /** 이어진 워치 — 10초마다 새로 물음 */
    private fun nodeChatgi(dwi: (() -> Unit)? = null) {
        val c = ctx ?: return
        try {
            Wearable.getNodeClient(c).connectedNodes
                .addOnSuccessListener { l -> nodes = l; nodeTtae = System.currentTimeMillis(); dwi?.invoke() }
                .addOnFailureListener { nodes = emptyList(); nodeTtae = System.currentTimeMillis() }
        } catch (e: Exception) {}
    }

    /** 이어진 워치마다 메시지 하나 — 지금 이어진 워치가 없으면 버림(지난 진동이 늦게 울리지 않게) */
    private fun bonaegi(path: String, o: JSONObject, nodeId: String? = null) {
        val c = ctx ?: return
        val b = o.toString().toByteArray(Charsets.UTF_8)
        val ssogi: () -> Unit = {
            try {
                val mc = Wearable.getMessageClient(c)
                if (nodeId != null) {
                    mc.sendMessage(nodeId, path, b)
                } else {
                    for (n in nodes) mc.sendMessage(n.id, path, b)
                }
            } catch (e: Exception) {}
            Unit
        }
        if (nodeId == null && System.currentTimeMillis() - nodeTtae > 10000) nodeChatgi { ssogi() } else ssogi()
    }

    /** 폰 상태를 데이터 항목으로 — 워치가 잠깐 떨어져 있어도 이어지면 받음(0.3초 모아서) */
    private fun sangtaeOlligi() {
        sangtaeYeyak?.let { main.removeCallbacks(it) }
        val r = Runnable {
            val c = ctx ?: return@Runnable
            try {
                val p = PutDataMapRequest.create(P_SANGTAE)
                p.dataMap.putString("mal", (last["mal"] as? String) ?: "")
                p.dataMap.putString("daeum", (last["daeum"] as? String) ?: "")
                p.dataMap.putDouble("ttae", (last["ttae"] as? Double) ?: 0.0)
                p.dataMap.putBoolean("geotneun", (last["geotneun"] as? Boolean) ?: false)
                p.dataMap.putString("watchBeonho", (last["watchBeonho"] as? String) ?: "")
                Wearable.getDataClient(c).putDataItem(p.asPutDataRequest().setUrgent())
            } catch (e: Exception) {}
        }
        sangtaeYeyak = r
        main.postDelayed(r, 300)
    }

    private fun sangtaeJson(): JSONObject {
        val o = JSONObject()
        for ((k, v) in last) o.put(k, v)
        return o
    }

    // MARK: 폰 → 워치

    /** 길눈이 한 말 — 워치에 넘기고 나스에도 남김(1.5초 모아서). Sori.mal 이 부름 */
    fun malBonae(t: String) {
        if (ctx == null) return
        main.post {
            last["mal"] = t
            if (JeomEngine.georeoJung) last["daeum"] = JeomEngine.daeumMuotMal()
            last["ttae"] = System.currentTimeMillis() / 1000.0
            sangtaeOlligi()
            seobeoYeyak?.let { main.removeCallbacks(it) }
            val r = Runnable { seobeoNamgi() }
            seobeoYeyak = r
            main.postDelayed(r, 1500)
        }
    }

    private fun seobeoNamgi() {
        val k = Seoljeong.watchBeonho
        val mal = (last["mal"] as? String) ?: ""
        val daeum = (last["daeum"] as? String) ?: ""
        il.execute {
            if (mal.isNotEmpty()) namgiGet(k, "mal", mal)
            if (daeum.isNotEmpty()) namgiGet(k, "daeum", daeum)
        }
    }

    private fun namgiGet(k: String, what: String, t: String) {
        try {
            val q = "a=nam&k=" + URLEncoder.encode(k, "UTF-8") + "&what=" + what + "&t=" + URLEncoder.encode(t, "UTF-8")
            val c = URL("https://lvd.ada.or.kr/jeom/watch.php?$q").openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.responseCode
            c.disconnect()
        } catch (e: Exception) {}
    }

    /** 점지도 따라 걷기 시작·그만 — 워치가 깨어 있기와 걸음 세기를 켜고 끔 */
    fun geotgiAllim(on: Boolean) {
        if (ctx == null) return
        main.post {
            if ((last["geotneun"] as? Boolean) == on) return@post
            last["geotneun"] = on
            sangtaeOlligi()
            bonaegi(P_ALLIM, JSONObject().put("geotneun", on))
        }
    }

    /** 손목 가리키기 — 가야 할 쪽(진북 기준 도)을 워치에. null 이면 쉼 */
    fun garikiBonae(b: Double?) {
        if (ctx == null) return
        bonaegi(P_ALLIM, JSONObject().put("gariki", b ?: -1.0))
    }

    /** 방향 진동 — left 짧게 두 번 · right 길게 한 번 · arrive 도착 */
    fun jindongBonae(mu: String) {
        if (ctx == null) return
        bonaegi(P_ALLIM, JSONObject().put("jindong", mu))
    }

    /** 음향신호기 결과 */
    fun sinhogiDap(ok: Boolean, mal: String) {
        if (ctx == null) return
        bonaegi(P_ALLIM, JSONObject().put("sinhogiMal", mal).put("sinhogiOk", ok))
    }

    // MARK: 워치 → 폰

    private val messageDeutgi = MessageClient.OnMessageReceivedListener { e: MessageEvent ->
        if (e.path != P_YOCHEONG) return@OnMessageReceivedListener
        val m = try { JSONObject(String(e.data, Charsets.UTF_8)) } catch (x: Exception) { return@OnMessageReceivedListener }
        val src = e.sourceNodeId
        main.post { yocheong(m, src) }
    }

    private fun yocheong(m: JSONObject, src: String) {
        val what = m.optString("what", "")
        if (what == "watchGeoreum") {
            // 워치가 센 걸음 — 폰이 걸음을 못 셀 때(가방 속 등) 점지도 따라 걷기가 이것으로 이어 감
            JeomEngine.watchGeoreum(m.optInt("n", 0))
            return
        }
        Girok.namgi("watch", mapOf("what" to what))
        val dap = { o: JSONObject -> bonaegi(P_DAP, o.put("what", what).put("id", m.optLong("id", 0)), src) }
        when (what) {
            "sinhogi" -> {
                val c = m.optInt("cmd", 1).coerceIn(1, 3)
                dap(JSONObject().put("sinhogiMal", "음향신호기를 찾는 중입니다."))
                SinhogiEngine.ulligi(c)
            }
            "malhagiSijak" -> {
                // 워치의 말로 하기 — 폰 길눈이 말로 하기 단추를 누른 것처럼 듣기를 엶
                MalHagi.dudeurim()
                dap(JSONObject().put("dapMal", "폰 길눈이 듣고 있습니다. 말씀하십시오."))
            }
            "momBang" -> dap(JSONObject().put("momBang", JeomEngine.momBang ?: -1.0))
            "jari" -> GilChatgiCheot.jariMunjang { s -> dap(JSONObject().put("jari", s)) }
            "daeum" -> {
                val o = sangtaeJson()
                o.put("daeum", if (JeomEngine.georeoJung) JeomEngine.daeumMuotMal() else "")
                dap(o)
            }
            else -> dap(sangtaeJson())
        }
    }

    // MARK: 지팡이 떨림 기록 — 워치에서 받아 나스로

    private var tteollimOlineun = false
    private var dasiYeyak: Runnable? = null

    private val tteollimHam: File?
        get() {
            val c = ctx ?: return null
            val d = File(c.filesDir, "tteollim")
            if (!d.exists()) d.mkdirs()
            return d
        }

    private val dataDeutgi = DataClient.OnDataChangedListener { buf: DataEventBuffer ->
        val l = ArrayList<DataItem>()
        try {
            for (ev in buf) {
                if (ev.type == DataEvent.TYPE_CHANGED && ev.dataItem.uri.path?.startsWith("$P_TTEOLLIM/") == true) l.add(ev.dataItem.freeze())
            }
        } catch (e: Exception) {}
        for (it in l) tteollimBatgi(it)
    }

    /** 폰 길눈이 꺼져 있는 동안 워치가 보낸 기록 — 켤 때 찾아 받음 */
    private fun tteollimChatgi() {
        val c = ctx ?: return
        try {
            val u = Uri.Builder().scheme("wear").authority("*").path(P_TTEOLLIM).build()
            Wearable.getDataClient(c).getDataItems(u, DataClient.FILTER_PREFIX).addOnSuccessListener { b ->
                val l = ArrayList<DataItem>()
                try { for (it in b) l.add(it.freeze()) } catch (e: Exception) {}
                b.release()
                for (it in l) tteollimBatgi(it)
            }
        } catch (e: Exception) {}
    }

    private fun tteollimBatgi(item: DataItem) {
        val c = ctx ?: return
        val ham = tteollimHam ?: return
        val sp = c.getSharedPreferences("gilnun_watchlink", Context.MODE_PRIVATE)
        val uri = item.uri.toString()
        val batun = sp.getString("batun", "") ?: ""
        if (batun.split("\n").contains(uri)) {
            // 이미 받은 기록 — 항목만 다시 지움(두 번 올리지 않게)
            try { Wearable.getDataClient(c).deleteDataItems(item.uri) } catch (e: Exception) {}
            return
        }
        try {
            val dm = DataMapItem.fromDataItem(item).dataMap
            val asset = dm.getAsset("bin") ?: return
            val pyo = (dm.getString("pyo") ?: "모름").ifEmpty { "모름" }.replace("_", " ")
            val cho = dm.getInt("cho", 0)
            val hz = dm.getInt("hz", 100)
            val son = dm.getString("son") ?: ""
            val dc = Wearable.getDataClient(c)
            dc.getFdForAsset(asset).addOnSuccessListener { r ->
                il.execute {
                    // 이름에 바닥·길이를 담아 둠 — 아이폰과 같은 꼴: 때_hz_초_손_바닥.bin
                    val ireum = "${System.currentTimeMillis() / 1000}_${hz}_${cho}_${son}_$pyo.bin"
                    var ok = false
                    try {
                        r.inputStream.use { inp -> File(ham, ireum).outputStream().use { inp.copyTo(it) } }
                        ok = true
                    } catch (e: Exception) {}
                    main.post {
                        if (ok) {
                            val l = (sp.getString("batun", "") ?: "").split("\n").filter { it.isNotEmpty() }.takeLast(199) + uri
                            sp.edit().putString("batun", l.joinToString("\n")).apply()
                            try { dc.deleteDataItems(item.uri) } catch (e: Exception) {}
                            Girok.namgi("tteollim_batda", mapOf("pyo" to pyo, "cho" to cho))
                            tteollimOlligi()
                        }
                    }
                }
            }
        } catch (e: Exception) {}
    }

    /** 들고 있는 떨림 기록을 차례로 나스에 올림 — 하나라도 못 올리면 멈추고 10분 뒤 다시 */
    fun tteollimOlligi() {
        main.post {
            if (tteollimOlineun) return@post
            val ham = tteollimHam ?: return@post
            val fs = (ham.listFiles() ?: emptyArray()).filter { it.name.endsWith(".bin") }.sortedBy { it.name }
            if (fs.isEmpty()) return@post
            tteollimOlineun = true
            val pan = "${Pan.pan}(${Pan.bild})-android"
            il.execute {
                var motham = false
                for (f in fs) {
                    val p = f.name.removeSuffix(".bin").split("_", limit = 5)
                    if (p.size != 5) { f.delete(); continue }
                    val ok = try {
                        val q = listOf("k" to Seoljeong.watchBeonho, "ttae" to p[0], "hz" to p[1], "cho" to p[2], "son" to p[3], "pyo" to p[4], "pan" to pan)
                            .joinToString("&") { it.first + "=" + URLEncoder.encode(it.second, "UTF-8") }
                        val c = URL("https://lvd.ada.or.kr/jeom/tteollim.php?$q").openConnection() as HttpURLConnection
                        c.requestMethod = "POST"
                        c.doOutput = true
                        c.connectTimeout = 15000
                        c.readTimeout = 90000
                        c.setRequestProperty("Content-Type", "application/octet-stream")
                        val d = f.readBytes()
                        c.setFixedLengthStreamingMode(d.size)
                        c.outputStream.use { it.write(d) }
                        val code = c.responseCode
                        val body = if (code == 200) c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } else ""
                        c.disconnect()
                        code == 200 && body.contains("\"ok\":true")
                    } catch (e: Exception) { false }
                    if (!ok) { motham = true; break }
                    f.delete()
                    Girok.namgi("tteollim_olim", mapOf("pyo" to p[4], "cho" to p[2]))
                }
                main.post {
                    tteollimOlineun = false
                    if (motham) {
                        dasiYeyak?.let { main.removeCallbacks(it) }
                        val r = Runnable { tteollimOlligi() }
                        dasiYeyak = r
                        main.postDelayed(r, 600_000)
                    }
                }
            }
        }
    }

    /** 기록·도움말에 쓰는 지금 이어진 워치 수 */
    val watchSu: Int get() = nodes.size
}
