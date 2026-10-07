// 갤럭시 워치 길눈 — 워치 쪽 두뇌 (2.5.0판, 빌드 261002-A8, 대표님 지시: 아이폰 길눈 워치 WatchModel.swift 2.6.0~2.37.1 을 갤럭시 워치로)
// 폰에서 받은 마지막 안내·다음 갈림길을 간직하고, 폰이 없으면 서버(watch.php)에 직접 묻습니다.
//   말하기: 톡백이 켜져 있고 화면이 켜져 있으면 톡백이 읽게(아이폰 2.37.1 보이스오버와 같은 뜻 — 워치 목소리가 톡백에 묻히지 않게), 아니면 워치 목소리
//   진동 무늬: 왼쪽 짧게 두 번, 오른쪽 길게 한 번, 도착 세 번(아이폰과 같음)
//   워치 단추(앱이 쓸 수 있는 옆 단추가 있는 워치만): 1.5초 안에 누른 횟수 — 1 다음 갈림길, 2 내 자리, 3 말로 하기(아이폰 두 번 집기와 같은 셈)
//   걷는 동안 깨어 있기(아이폰 2.35.0): 폰이 점지도 따라 걷기를 시작하면 앞에 도는 일(GeotgiService)을 열어 손목을 내려도 멈추지 않고,
//     워치 걸음 센서(팔 흔들림)의 누계를 폰에 보냄. 폰이 가방 속이라 걸음을 못 셀 때 폰이 이것으로 이어 감
// 폰과는 웨어러블 데이터 층 — 경로는 폰 WatchLink.kt 와 같음(/gilnun/sangtae·allim·yocheong·dap·tteollim)
// 2.6.0판(빌드 261002-A9, 대표님 지시) 긴급통화 — 화면에서 두 번 눌러 확인하면 폰에 gingeup 부탁. 폰이 누구에게 요청하는지 답(dapMal)을 읽어 드림
package kr.or.ada.app.wear

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors

object WatchModel {
    const val P_SANGTAE = "/gilnun/sangtae"
    const val P_ALLIM = "/gilnun/allim"
    const val P_YOCHEONG = "/gilnun/yocheong"
    const val P_DAP = "/gilnun/dap"
    const val P_TTEOLLIM = "/gilnun/tteollim"
    private const val WURL = "https://lvd.ada.or.kr/jeom/watch.php"
    const val MOT_MAL = "폰의 길눈과 이어져 있지 않습니다. 폰에서 길눈을 열어 주십시오."

    private val main = Handler(Looper.getMainLooper())
    private val il = Executors.newSingleThreadExecutor()
    private var ctx: Context? = null
    private lateinit var d: SharedPreferences
    private var tts: TextToSpeech? = null
    private var ttsJunbi = false

    var mal = "아직 받은 안내가 없습니다."
        private set
    var daeum = ""
        private set
    var jari = ""
        private set
    var dapMal = ""
        private set
    /** 걷는 동안 깨어 있기가 돌고 있는가(GeotgiService 가 알림) */
    var kkaeeoItda = false
        private set
    /** 화면이 채움 — 상태가 바뀌면 글자만 바꿈 */
    var byeonhwa: (() -> Unit)? = null

    /** 폰이 알린 걷는 중 — 바뀔 때만 따름 */
    private var ponGeotneun = false
    /** 걸음 세기를 켜 달라(폰이 걷는 중이거나 손으로 켬) */
    var georeumOn = false
        private set
    /** 지팡이 떨림 기록 중 — 손목을 내려도 멈추지 않게(걸음 세기는 건드리지 않음) */
    var bojangOn = false
        private set
    private var majimakBonaen = -1

    private var nodes: List<Node> = emptyList()
    private var nodeTtae = 0L

    private class Butak(val dap: (JSONObject) -> Unit, val motham: (Boolean) -> Unit, val han: Runnable)
    private val butakdeul = HashMap<Long, Butak>()
    private var butakBeon = System.currentTimeMillis()

    private var jipgiSu = 0
    private val jipgiKkeutR = Runnable { jipgiKkeut() }

    // MARK: 세우기

    fun sijak(c: Context) {
        if (ctx != null) return
        val a = c.applicationContext
        ctx = a
        d = a.getSharedPreferences("gilnun_watch", Context.MODE_PRIVATE)
        SonmokGariki.sijak(a)
        JipangiTteollim.sijak(a)
        tts = TextToSpeech(a) { st ->
            if (st == TextToSpeech.SUCCESS) {
                tts?.language = Locale.KOREAN
                ttsJunbi = true
            }
        }
        nodeChatgi()
        sangtaeChatgi()
        main.postDelayed({ JipangiTteollim.namuenGeotBonaegi() }, 3000)   // 지난번에 못 보낸 떨림 기록
    }

    val prefs: SharedPreferences get() = d

    /** 지팡이 쥔 손 — "oreun" 오른쪽 / "oen" 왼쪽 / "" 아직 */
    var jipangiSon: String
        get() = d.getString("jipangiSon", "") ?: ""
        set(v) { d.edit().putString("jipangiSon", v).apply() }
    /** 워치 찬 손목 — "oreun" / "oen" / "" */
    var watchSonmok: String
        get() = d.getString("watchSonmok", "") ?: ""
        set(v) { d.edit().putString("watchSonmok", v).apply() }

    fun sonMal(v: String) = if (v == "oen") "왼" else "오른"

    // MARK: 말하기와 진동

    private fun tokbaekKyeojim(): Boolean {
        val c = ctx ?: return false
        val am = c.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        return am.isEnabled && am.isTouchExplorationEnabled
    }

    private fun hwamyeonKyeojim(): Boolean {
        val c = ctx ?: return false
        return (c.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive == true
    }

    /** 말하기 + 진동 */
    fun speak(t: String, jindong: String = "click") {
        haptic(jindong)
        if (tokbaekKyeojim() && hwamyeonKyeojim()) {
            tts?.stop()
            // 진동·초점 옮김과 겹치지 않게 아주 잠깐 뒤에
            main.postDelayed({ allyeo(t) }, 300)
            return
        }
        val tt = tts
        if (tt == null || !ttsJunbi) { allyeo(t); return }
        tt.speak(t, TextToSpeech.QUEUE_FLUSH, null, "w${System.currentTimeMillis()}")
    }

    /** 톡백으로 알림 — 길눈 화면이 떠 있으면 그 화면으로, 아니면 알림 이벤트로 */
    @Suppress("DEPRECATION")
    private fun allyeo(t: String) {
        val v = WatchActivity.boineun?.get()?.window?.decorView
        if (v != null) { v.announceForAccessibility(t); return }
        val c = ctx ?: return
        val am = c.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return
        if (!am.isEnabled) return
        try {
            val e = AccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT)
            e.packageName = c.packageName
            e.className = WatchModel::class.java.name
            e.text.add(t)
            am.sendAccessibilityEvent(e)
        } catch (x: Exception) {}
    }

    /** 진동 — click start success failure notification retry maja(가리키기 맞음, 굵게) left right arrive long */
    @Suppress("DEPRECATION")
    fun haptic(kind: String) {
        val c = ctx ?: return
        if (kind == "none") return
        try {
            val v: Vibrator = (if (Build.VERSION.SDK_INT >= 31)
                (c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            else c.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator) ?: return
            if (kind == "maja") {
                v.vibrate(VibrationEffect.createOneShot(90, if (v.hasAmplitudeControl()) 255 else VibrationEffect.DEFAULT_AMPLITUDE))
                return
            }
            val gil = when (kind) {
                "left" -> longArrayOf(0, 120, 130, 120)
                "right" -> longArrayOf(0, 600)
                "arrive" -> longArrayOf(0, 150, 150, 150, 150, 150)
                "start" -> longArrayOf(0, 70)
                "success" -> longArrayOf(0, 80, 100, 80)
                "failure" -> longArrayOf(0, 300, 150, 300)
                "notification" -> longArrayOf(0, 120, 120, 120)
                "retry" -> longArrayOf(0, 200, 100, 200, 100, 200)
                "long" -> longArrayOf(0, 450)
                else -> longArrayOf(0, 40)
            }
            v.vibrate(VibrationEffect.createWaveform(gil, -1))
        } catch (e: Exception) {}
    }

    /** 방향 진동 — 폰이 보낸 무늬(left·right·arrive). 그 밖은 짧게 한 번(아이폰과 같음) */
    fun jindongHagi(mu: String) {
        haptic(when (mu) { "left", "right", "arrive" -> mu; else -> "click" })
    }

    // MARK: 단추

    fun malDeutgi() = askPhoneMal("mal")
    fun daeumDeutgi() = askPhoneMal("daeum")

    /** 워치 단추 — 1.5초 안에 잇달아 누른 횟수로 나눔. 누를 때마다 한 번 짧게 떨고, 세 번이면 곧바로 */
    fun jipgi() {
        jipgiSu += 1
        haptic("click")
        main.removeCallbacks(jipgiKkeutR)
        if (jipgiSu >= 3) { jipgiKkeut(); return }
        main.postDelayed(jipgiKkeutR, 1500)
    }

    private fun jipgiKkeut() {
        val n = jipgiSu
        jipgiSu = 0
        main.removeCallbacks(jipgiKkeutR)
        when (n) {
            0 -> {}
            1 -> daeumDeutgi()
            2 -> jariDeutgi()
            else -> malSijak()
        }
    }

    /** 말로 하기 — 폰 길눈이 말로 하기 듣기를 엶(폰의 마이크나 이어폰으로 말씀하시면 됨) */
    fun malSijak() {
        askPhone("malhagiSijak", null, { eopseum ->
            speak(if (eopseum) MOT_MAL else "폰에 요청을 보내지 못했습니다. 다시 해 주십시오.", "failure")
        }) { r ->
            haptic("start")
            dapMal = r.optString("dapMal", "")
            byeonhwa?.invoke()
        }
    }

    /** 2.6.0 긴급통화 — 폰 길눈이 화상통화를 요청(마지막으로 요청하신 가족·지인 한 분, 없으면 자원봉사자). 폰이 곁에 없으면 알려 드림 */
    fun gingeup() {
        haptic("start")
        askPhone("gingeup", null, { eopseum ->
            speak(if (eopseum) MOT_MAL else "폰에 긴급통화 요청을 보내지 못했습니다. 다시 눌러 주십시오.", "failure")
        }) { r ->
            dapMal = r.optString("dapMal", "")
            byeonhwa?.invoke()
            speak(dapMal.ifEmpty { "폰 길눈이 긴급통화를 요청합니다." }, "success")
        }
    }

    /** 음향신호기 — 폰이 블루투스로 가까운 음향신호기를 울림(1 위치안내 / 2 신호안내) */
    fun sinhogi(cmd: Int) {
        haptic("start")
        askPhone("sinhogi", JSONObject().put("cmd", cmd), { eopseum ->
            speak(if (eopseum) MOT_MAL else "폰에 요청을 보내지 못했습니다. 다시 눌러 주십시오.", "failure")
        }) { r -> apply(r) }
    }

    /** 내 자리 — 폰 길눈에 묻고(길 찾기 첫 화면의 지금 내 자리와 같은 말), 폰이 없으면 워치 위성으로 서버에 물음 */
    fun jariDeutgi() {
        speak("내 자리를 찾는 중입니다.", "start")
        askPhone("jari", null, { _ -> watchWichiJari() }) { r ->
            val s = r.optString("jari", "")
            if (s.isEmpty()) { watchWichiJari(); return@askPhone }
            jari = s
            byeonhwa?.invoke()
            speak(s, "success")
        }
    }

    @SuppressLint("MissingPermission")
    private fun watchWichiJari() {
        val c = ctx ?: return
        val mot = "위치를 잡지 못했습니다. 하늘이 보이는 곳에서 다시 눌러 주십시오."
        if (c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            speak("폰의 길눈과 이어져 있지 않고, 워치에 위치 허락이 없어 자리를 알 수 없습니다.", "failure"); return
        }
        val lm = c.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: run { speak(mot, "failure"); return }
        val pv = when {
            Build.VERSION.SDK_INT >= 31 && lm.isProviderEnabled(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        } ?: run { speak(mot, "failure"); return }
        try {
            lm.getCurrentLocation(pv, CancellationSignal(), il) { l ->
                if (l == null) { main.post { speak(mot, "failure") }; return@getCurrentLocation }
                val s = textGet("$WURL?a=jari&lat=${l.latitude}&lon=${l.longitude}") ?: "자리를 알아내지 못했습니다."
                main.post { jari = s; byeonhwa?.invoke(); speak(s, "success") }
            }
        } catch (e: Exception) { speak(mot, "failure") }
    }

    /** 폰에 부탁 — 폰이 곁에 있으면 폰의 길눈이 답하고, 없으면 서버에서 마지막 것을 받아 옴 */
    private fun askPhoneMal(what: String) {
        askPhone(what, null, { _ -> fetchServer(what) }) { r ->
            apply(r)
            if (what == "daeum") speak(daeum.ifEmpty { "다음 갈림길 안내가 아직 없습니다." })
            else speak(mal.ifEmpty { "아직 받은 안내가 없습니다." })
        }
    }

    /** 폰이 곁에 없을 때 — 나스에 남은 마지막 것을 받아 말함, 그것도 안 되면 그렇다고 알려 드림 */
    private fun fetchServer(what: String) {
        val k = d.getString("watchBeonho", "") ?: ""
        if (k.isEmpty()) { speak(MOT_MAL, "failure"); return }
        il.execute {
            val s = textGet("$WURL?a=deut&k=" + URLEncoder.encode(k, "UTF-8") + "&what=$what")?.trim() ?: ""
            main.post {
                if (s.isEmpty()) { speak(MOT_MAL, "failure"); return@post }
                if (what == "mal") mal = s else daeum = s
                byeonhwa?.invoke()
                speak(s)
            }
        }
    }

    private fun textGet(u: String): String? = try {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        val t = if (c.responseCode == 200) c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } else null
        c.disconnect()
        t
    } catch (e: Exception) { null }

    /** 가리키기 방향 맞추기 — 폰 길눈에게 지금 몸이 향한 방향을 물음 */
    fun momBangMureum(dap: (Double?) -> Unit) {
        askPhone("momBang", null, { _ -> dap(null) }) { r ->
            val b = r.optDouble("momBang", -1.0)
            dap(if (b >= 0) b else null)
        }
    }

    // MARK: 폰과 주고받기

    private fun nodeChatgi(dwi: ((List<Node>?) -> Unit)? = null) {
        val c = ctx ?: run { dwi?.invoke(null); return }
        try {
            Wearable.getNodeClient(c).connectedNodes
                .addOnSuccessListener { l -> nodes = l; nodeTtae = System.currentTimeMillis(); dwi?.invoke(l) }
                .addOnFailureListener { nodes = emptyList(); nodeTtae = System.currentTimeMillis(); dwi?.invoke(null) }
        } catch (e: Exception) { dwi?.invoke(null) }
    }

    /** 폰에 부탁 하나 — 4초 안에 답이 없으면 motham(false), 이어진 폰이 없으면 motham(true) */
    private fun askPhone(what: String, deo: JSONObject?, motham: (Boolean) -> Unit, dap: (JSONObject) -> Unit) {
        val c = ctx ?: run { motham(true); return }
        nodeChatgi { l ->
            main.post {
                if (l.isNullOrEmpty()) { motham(true); return@post }
                butakBeon += 1
                val id = butakBeon
                val o = deo ?: JSONObject()
                o.put("what", what).put("id", id)
                val han = Runnable { butakdeul.remove(id)?.let { it.motham(false) } }
                butakdeul[id] = Butak(dap, motham, han)
                main.postDelayed(han, 4000)
                val b = o.toString().toByteArray(Charsets.UTF_8)
                try {
                    val mc = Wearable.getMessageClient(c)
                    for (n in l) mc.sendMessage(n.id, P_YOCHEONG, b)
                } catch (e: Exception) {
                    main.removeCallbacks(han)
                    butakdeul.remove(id)
                    motham(false)
                }
            }
        }
    }

    /** 답 없이 보냄(걸음) — 지금 이어진 폰에만 */
    private fun bonaegi(o: JSONObject) {
        val c = ctx ?: return
        val b = o.toString().toByteArray(Charsets.UTF_8)
        val ssogi: (List<Node>?) -> Unit = { l ->
            try {
                val mc = Wearable.getMessageClient(c)
                for (n in l ?: emptyList()) mc.sendMessage(n.id, P_YOCHEONG, b)
            } catch (e: Exception) {}
            Unit
        }
        if (System.currentTimeMillis() - nodeTtae > 10000) nodeChatgi { l -> ssogi(l) } else ssogi(nodes)
    }

    /** 폰 → 워치 메시지(GilnunWearService 가 화면 줄로 넘김) */
    fun messageBatda(path: String, o: JSONObject) {
        when (path) {
            P_ALLIM -> apply(o)
            P_DAP -> {
                val b = butakdeul.remove(o.optLong("id", -1)) ?: return
                main.removeCallbacks(b.han)
                b.dap(o)
            }
        }
    }

    /** 폰 → 워치 상태(데이터 항목). 새 안내가 왔으면 한 번 떪 */
    fun sangtaeBatda(o: JSONObject, saeGeot: Boolean) {
        val ap = mal
        apply(o)
        if (saeGeot && mal != ap && o.optString("mal", "").isNotEmpty()) haptic("notification")
    }

    fun dataMapJson(m: DataMap): JSONObject = JSONObject()
        .put("mal", m.getString("mal") ?: "")
        .put("daeum", m.getString("daeum") ?: "")
        .put("ttae", m.getDouble("ttae", 0.0))
        .put("geotneun", m.getBoolean("geotneun", false))
        .put("watchBeonho", m.getString("watchBeonho") ?: "")

    /** 워치 길눈이 켜질 때·화면에 다시 뜰 때 — 폰이 남겨 둔 상태를 받음 */
    fun sangtaeChatgi() {
        val c = ctx ?: return
        try {
            val u = Uri.Builder().scheme("wear").authority("*").path(P_SANGTAE).build()
            Wearable.getDataClient(c).getDataItems(u).addOnSuccessListener { b ->
                val l = ArrayList<JSONObject>()
                try { for (it in b) l.add(dataMapJson(DataMapItem.fromDataItem(it).dataMap)) } catch (e: Exception) {}
                b.release()
                for (o in l) sangtaeBatda(o, false)
            }
        } catch (e: Exception) {}
    }

    private fun apply(r: JSONObject) {
        if (r.has("geotneun")) {
            val g = r.optBoolean("geotneun", false)
            if (g != ponGeotneun) {
                ponGeotneun = g
                if (g) geotgiKyeogi() else { geotgiKkeugi(); SonmokGariki.mokBatda(null) }
            }
        }
        // 손목 가리키기 — 폰이 보낸 가야 할 쪽(음수면 없음)
        if (r.has("gariki")) { val b = r.optDouble("gariki", -1.0); SonmokGariki.mokBatda(if (b >= 0) b else null) }
        r.optString("mal", "").let { if (it.isNotEmpty()) mal = it }
        if (r.has("daeum")) daeum = r.optString("daeum", "")
        r.optString("watchBeonho", "").let { if (it.isNotEmpty()) d.edit().putString("watchBeonho", it).apply() }
        r.optString("jindong", "").let { if (it.isNotEmpty()) jindongHagi(it) }
        val s = r.optString("sinhogiMal", "")
        if (s.isNotEmpty()) {
            val j = if (!r.has("sinhogiOk")) "click" else if (r.optBoolean("sinhogiOk", true)) "success" else "failure"
            speak(s, j)
        }
        byeonhwa?.invoke()
    }

    // MARK: 걷는 동안 깨어 있기

    /** 폰이 걷기를 시작했다고 알리거나, 손목에서 "걷는 동안 깨어 있기"를 누르면 */
    fun geotgiKyeogi() {
        georeumOn = true
        majimakBonaen = -1
        ctx?.let { GeotgiService.gaengsin(it) }
    }

    fun geotgiKkeugi() {
        georeumOn = false
        ctx?.let { GeotgiService.gaengsin(it) }
    }

    /** 지팡이 떨림 기록 중에는 손목을 내려도 멈추지 않게 — 걸음 세기는 건드리지 않음 */
    fun kkaeeoBojang(on: Boolean) {
        bojangOn = on
        ctx?.let { GeotgiService.gaengsin(it) }
    }

    /** 손목의 단추 — 켜져 있으면 끄고, 꺼져 있으면 켬 */
    fun kkaeeoDanchu() {
        if (georeumOn) {
            geotgiKkeugi(); speak("걷는 동안 깨어 있기를 껐습니다.")
        } else {
            geotgiKyeogi(); speak("걷는 동안 워치가 깨어 있고, 팔 흔들림으로 걸음을 세어 폰 길눈에 보냅니다.", "start")
        }
    }

    /** 워치 길눈이 화면에 다시 떴을 때 — 폰이 걷는 중인데 깨어 있지 못하면 다시 엶 */
    fun hwamyeonDolawa() {
        if (ponGeotneun && !kkaeeoItda) geotgiKyeogi()
        sangtaeChatgi()
        bonaegi(JSONObject().put("what", "pan").put("pan", WatchActivity.PAN))   // 2.7.0 폰 길눈이 워치 판을 알아 동영상을 보낼지 가림
    }

    /** GeotgiService 가 켜지고 꺼질 때 */
    fun kkaeeoBakkwim(on: Boolean) {
        kkaeeoItda = on
        byeonhwa?.invoke()
    }

    /** 화면이 꺼진 동안 안드로이드가 깨어 있기를 열지 못하게 했을 때 — 화면에 길눈을 띄우시면 다시 열림 */
    fun kkaeeoMotham() {
        kkaeeoItda = false
        byeonhwa?.invoke()
        // 길눈 화면이 떠 있는데도 못 열었을 때만 알림 — 화면이 닫혀 있으면 아이폰처럼 조용히, 화면을 여실 때 다시 엶
        if ((georeumOn || bojangOn) && WatchActivity.boineun?.get() != null) speak("워치 깨어 있기를 열지 못했습니다. 길눈 워치 화면을 한 번 여시면 다시 이어집니다.", "retry")
    }

    /** 걸음 세기를 새로 시작할 때 */
    fun georeumSaero() { majimakBonaen = -1 }

    /** 워치가 센 걸음 누계 — 바뀌었을 때만 폰에 */
    fun georeumBonae(n: Int) {
        if (n == majimakBonaen) return
        majimakBonaen = n
        bonaegi(JSONObject().put("what", "watchGeoreum").put("n", n))
    }

    // MARK: 지팡이 떨림 기록 보내기

    /** 파일 이름 tteollim_<때>_<초>_<손>_<바닥>.bin — 데이터 항목(자산)으로 폰에. 데이터 층에 넘어가면 워치에서 지움 */
    fun tteollimBonae(f: File) {
        val c = ctx ?: return
        val p = f.name.removeSuffix(".bin").split("_", limit = 5)
        if (p.size != 5) { f.delete(); return }
        try {
            val r = PutDataMapRequest.create("$P_TTEOLLIM/${p[1]}")
            r.dataMap.putAsset("bin", Asset.createFromBytes(f.readBytes()))
            r.dataMap.putString("pyo", p[4])
            r.dataMap.putInt("cho", p[2].toIntOrNull() ?: 0)
            r.dataMap.putString("son", p[3])
            r.dataMap.putInt("hz", JipangiTteollim.HZ)
            r.dataMap.putLong("ttae", p[1].toLongOrNull() ?: 0L)
            Wearable.getDataClient(c).putDataItem(r.asPutDataRequest().setUrgent())
                .addOnSuccessListener { f.delete() }
        } catch (e: Exception) {}
    }
}
