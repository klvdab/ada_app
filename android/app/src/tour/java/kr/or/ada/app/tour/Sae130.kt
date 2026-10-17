// LVD투어스튜디오 안드로이드 — 1.3.0판 (빌드 261018-T5, 방클, 이사장님 승인 「앱만 할 수 있는 것」 1·2·8번)
// ① 화면을 안 보고 답사하기 — 이어폰 단추(한 번: 현장 메모 녹음 시작·끝, 두 번: 사진, 세 번: 이동 구간 재기 시작·끝),
//    답사 탭과 나침반 카메라에서는 음량 단추로도(크게: 사진, 작게: 메모 녹음 시작·끝)
// ② 걸으며 저절로 재기 — 걸음 수·꺾은 방향(시계 방향)·계단 칸 수·오르막과 내리막을 재어 이동 구간 안내 초안을 씀
// ⑧ 판 기록 — 이 폰의 판·빌드·기기를 협회에 알리고, 최고관리자는 누가 어떤 판을 쓰는지 봄
// 아이폰 Tour/Danchu130.swift 와 같은 잣대.
package kr.or.ada.app.tour

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.speech.tts.TextToSpeech
import android.view.KeyEvent
import android.view.accessibility.AccessibilityManager
import android.widget.TextView
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** 걸음 재기 셈 — 아이폰 GeoleumSem 과 같음 */
class GeoleumSem {
    class Gul(var dir: Double?, val startS: Int, var startAlt: Double?) { var endS = 0; var endAlt: Double? = null; var turn = 0 }
    val guls = mutableListOf(Gul(null, 0, null))
    private var candH: Double? = null; private var candS = 0; private var candAlt: Double? = null
    var lastS = 0; private set
    var lastAlt: Double? = null; private set
    val sijak = System.currentTimeMillis()
    companion object {
        fun cha(a: Double, b: Double): Double { var d = (a - b) % 360; if (d > 180) d -= 360; if (d < -180) d += 360; return d }
        fun sigak(d: Double): Int { var c = (d / 30).roundToInt(); if (c <= 0) c += 12; return if (c > 12) c - 12 else c }
        fun nopiMal(a: Double?, b: Double?, n: Int): String {
            if (a == null || b == null) return ""
            val dz = b - a
            if (abs(dz) < 1.0) return ""
            if (abs(dz) / max(n, 1) >= 0.08) return ", 그 사이 계단 약 ${(abs(dz) / 0.16).roundToInt()}칸 " + if (dz > 0) "오름" else "내림"
            return ", " + (if (dz > 0) "오르막" else "내리막") + " 약 ${abs(dz).roundToInt()}미터"
        }
    }
    fun geoleum(s: Int) { if (s > lastS) lastS = s }
    fun nopi(a: Double) { lastAlt = a; if (guls.last().startAlt == null) guls.last().startAlt = a }
    fun bang(h: Double) {
        val g = guls.last()
        val d0 = g.dir ?: run { g.dir = h; return }
        val d = cha(h, d0)
        if (abs(d) < 50) { candH = null; g.dir = (d0 + d * 0.1 + 360) % 360; return }
        val ch = candH ?: run { candH = h; candS = lastS; candAlt = lastAlt; return }
        if (abs(cha(h, ch)) >= 40) { candH = h; candS = lastS; candAlt = lastAlt; return }
        if (lastS - candS >= 4) {
            g.endS = candS; g.endAlt = candAlt
            val n = Gul(h, candS, candAlt); n.turn = sigak(cha(h, d0))
            guls.add(n); candH = null
        }
    }
    val bun: Int get() = max(1, ((System.currentTimeMillis() - sijak) / 60000.0).roundToInt())
    fun geul(): String {
        val L = mutableListOf<String>()
        guls.forEachIndexed { i, g ->
            val e = if (i == guls.size - 1) lastS else g.endS
            val ea = if (i == guls.size - 1) lastAlt else g.endAlt
            val n = max(0, e - g.startS)
            var t = when { i == 0 -> "출발할 때 바라본 쪽(12시 방향)으로 약 ${n}걸음"; g.turn == 6 -> "6시 방향으로 되돌아서 약 ${n}걸음"; else -> "${g.turn}시 방향으로 꺾어 약 ${n}걸음" }
            t += nopiMal(g.startAlt, ea, n)
            L.add(t)
        }
        return "[걸으며 잰 초안 — 걸음과 계단 칸 수는 어림이니 현장에서 확인해 고치십시오] " + L.joinToString(". ") + ". 모두 약 ${lastS}걸음, ${bun}분 걸림."
    }
}

/** 1.3.0 화면들이 쥐고 있는 것 */
class S130 {
    // 걸으며 재기
    var jaegi = false; var jaegiSpot = ""; var sem = GeoleumSem(); var jaegiSensor: SensorEventListener? = null; var jaegiJul = ""
    // 이어폰 단추
    var session: MediaSession? = null; var track: AudioTrack? = null; var nureum = 0; var nureumRun: Runnable? = null
    // 판 기록
    var panRows: List<String>? = null; var panYoyak = ""
}

// ================= ② 걸으며 저절로 재기 =================
internal val TourActivity.jaegiJeoljeoro: Boolean get() = sp.getBoolean("geoleumAuto", true)
internal fun TourActivity.jaegiSijak(s: Spot) {
    if (s13.jaegi) jaegiKkeut(true)
    if (Build.VERSION.SDK_INT >= 29 && checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), TourActivity.HEORAK_GEOLEUM)
        malhae("걸음을 세려면 신체 활동 허락이 필요합니다. 허락해 주신 뒤 다시 재기를 눌러 주십시오."); return
    }
    val sm = getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager
    val st = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) ?: run { malhae("이 폰은 걸음을 셀 수 없습니다."); return }
    val pr = sm.getDefaultSensor(Sensor.TYPE_PRESSURE); val rv = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    s13.sem = GeoleumSem(); s13.jaegiSpot = s.id; s13.jaegi = true; s13.jaegiJul = "재는 중 — 0걸음"
    var georeum = 0; var p0: Double? = null; var hAt = 0L
    val R = FloatArray(9); val o = FloatArray(3)
    val l = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            when (e.sensor.type) {
                Sensor.TYPE_STEP_DETECTOR -> {
                    georeum++; s13.sem.geoleum(georeum)
                    s13.jaegiJul = "재는 중 — 약 ${georeum}걸음, 꺾은 곳 ${s13.sem.guls.size - 1}군데"
                    body.findViewWithTag<TextView>("jaegiJul")?.text = s13.jaegiJul
                }
                Sensor.TYPE_PRESSURE -> {
                    val a = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, e.values[0]).toDouble()
                    val base = p0 ?: a.also { p0 = it }
                    s13.sem.nopi(a - base)
                }
                Sensor.TYPE_ROTATION_VECTOR -> {
                    val now = System.currentTimeMillis(); if (now - hAt < 200) return; hAt = now
                    SensorManager.getRotationMatrixFromVector(R, e.values); SensorManager.getOrientation(R, o)
                    s13.sem.bang((Math.toDegrees(o[0].toDouble()) + 360) % 360)
                }
            }
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }
    s13.jaegiSensor = l
    sm.registerListener(l, st, SensorManager.SENSOR_DELAY_NORMAL)
    pr?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_NORMAL) }
    rv?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_UI) }
    malhae((kos?.spotLab(s) ?: "") + " 이동 구간을 재기 시작합니다. 폰을 손에 들고 걸으십시오.")
    if (cur() is Hw.Dapsa) draw()
}
/** 재기 끝 — neoki 면 잰 지점의 이동 구간 안내 칸 끝에 초안을 붙임 */
internal fun TourActivity.jaegiKkeut(neoki: Boolean) {
    if (!s13.jaegi) return
    s13.jaegiSensor?.let { (getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager).unregisterListener(it) }
    s13.jaegiSensor = null; s13.jaegi = false; s13.jaegiJul = ""
    if (!neoki) { malhagi("재기를 그만두었습니다."); if (cur() is Hw.Dapsa) draw(); return }
    val k = kos ?: return; val s = k.spot(s13.jaegiSpot) ?: return
    val sem = s13.sem
    if (sem.lastS < 5) { malhae("걸음이 거의 없어 넣지 않았습니다."); if (cur() is Hw.Dapsa) draw(); return }
    val t = sem.geul()
    val x = s.s("access")
    s.d.put("access", if (x.isBlank()) t else x + "\n" + t)
    if ((s.n("walkIn") ?: 0.0).toInt() == 0) { s.d.put("walkIn", sem.bun); k.syncMove() }
    bakkwim()
    malhae(k.spotLab(s) + " 이동 구간 안내에 잰 것을 넣었습니다. 약 ${sem.lastS}걸음, ${sem.bun}분입니다.")
    allim = k.spotLab(s) + " 이동 구간 안내에 넣었습니다. " + t
    if (cur() is Hw.Dapsa) draw()
}
/** 경계를 적으면 새 지점의 이동 구간을 저절로 재기 시작 */
internal fun TourActivity.jaegiGyeonggye() { if (jaegiJeoljeoro) spot()?.let { jaegiSijak(it) } }

// ================= ① 화면을 안 보고 답사하기 =================
internal val TourActivity.danchuOn: Boolean get() = sp.getBoolean("danchuOn", false)
/** 화면을 안 보고 쓸 때는 폰이 소리 내어 알림(말소리를 켜 둔 경우, 톡백이 꺼져 있을 때) */
internal fun TourActivity.malhae(t: String) {
    malhagi(t)
    val am = getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    if (danchuOn && malsori && !Nokeumgi.jung && !am.isTouchExplorationEnabled && ttsOk) tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "danchu")
}
internal fun TourActivity.danchuKyeogi(v: Boolean) {
    sp.edit().putBoolean("danchuOn", v).apply()
    if (v) { danchuBuchigi(); malhae("이어폰 단추를 켰습니다. 한 번은 메모 녹음, 두 번은 사진, 세 번은 이동 구간 재기입니다. 음량 크게 단추는 사진, 작게 단추는 메모 녹음입니다.") }
    else { danchuTteigi(); malhagi("이어폰 단추를 껐습니다.") }
    draw()
}
/** 이어폰 단추가 이 앱으로 오게 — 미디어 세션을 열고 소리 없는 소리를 되풀이해 틂 */
internal fun TourActivity.danchuBuchigi() {
    if (!danchuOn) return
    if (s13.track == null) try {
        val n = 8000
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(8000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(n * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
        t.write(ShortArray(n), 0, n); t.setLoopPoints(0, n, -1); t.setVolume(0.01f); t.play()
        s13.track = t
    } catch (e: Exception) { }
    if (s13.session == null) {
        val ms = MediaSession(this, "tourDapsa")
        ms.setCallback(object : MediaSession.Callback() {
            override fun onMediaButtonEvent(i: Intent): Boolean {
                @Suppress("DEPRECATION") val ev = i.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
                if (ev.action != KeyEvent.ACTION_UP) return true
                when (ev.keyCode) {
                    KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> main.post { danchuNulleum(1) }
                    KeyEvent.KEYCODE_MEDIA_NEXT -> main.post { danchuHagi(2) }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> main.post { danchuHagi(3) }
                    else -> return false
                }
                return true
            }
        })
        s13.session = ms
    }
    s13.session?.setPlaybackState(PlaybackState.Builder()
        .setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
        .setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
    s13.session?.isActive = true
}
internal fun TourActivity.danchuTteigi() {
    try { s13.track?.stop(); s13.track?.release() } catch (e: Exception) { }; s13.track = null
    s13.session?.let { it.isActive = false; it.release() }; s13.session = null
}
/** 한 번 누름을 0.6초 기다려 모음 */
internal fun TourActivity.danchuNulleum(n: Int) {
    s13.nureum += n
    s13.nureumRun?.let { main.removeCallbacks(it) }
    val r = Runnable { val m = s13.nureum; s13.nureum = 0; danchuHagi(minOf(m, 3)) }
    s13.nureumRun = r; main.postDelayed(r, 600)
}
/** 1: 메모 녹음 시작·끝, 2: 사진, 3: 이동 구간 재기 시작·끝 */
internal fun TourActivity.danchuHagi(n: Int) {
    val k = kos; val s = spot()
    if (k == null || s == null || k.isGyobon) { malhae("먼저 코스를 열고 지점을 골라 주십시오."); return }
    when (n) {
        2 -> if (cur() is Hw.Jeoljeoro && s12.cam != null) camJjikgi(0) else { malhae("나침반 카메라를 엽니다. " + k.spotLab(s) + "입니다."); tab = 1; go(Hw.Jeoljeoro) }
        3 -> if (s13.jaegi) jaegiKkeut(true) else jaegiSijak(s)
        else -> danchuMemo(k, s)
    }
}
internal fun TourActivity.danchuMemo(k: Kos, s: Spot) {
    if (Nokeumgi.jung && Nokeumgi.galle == "memo") { ppi(); deureul(); nokeumKkeut(); main.postDelayed({ danchuBuchigi() }, 1500); return }
    if (Nokeumgi.jung) { malhae("연습 녹음 중입니다. 먼저 끝내 주십시오."); return }
    malhae(k.spotLab(s) + " " + MemoKan.ireum(memoKan) + ". 삐 소리 뒤에 말씀하십시오.")
    main.postDelayed({ ppi(); nokeumSijak("memo", s, memoKan, "녹음 중입니다. 다 말씀하시면 이어폰 단추를 한 번 누르십시오.") }, 2600)
}
/** 음량 단추 — 답사 탭과 나침반 카메라에서만. 크게: 사진, 작게: 메모 녹음 시작·끝 */
internal fun TourActivity.danchuKey(code: Int): Boolean {
    if (!danchuOn || me == null) return false
    val dapsa = tab == 1 && (cur() is Hw.Dapsa || cur() is Hw.Jeoljeoro)
    return when (code) {
        KeyEvent.KEYCODE_VOLUME_UP -> { if (!dapsa) false else { danchuHagi(2); true } }
        KeyEvent.KEYCODE_VOLUME_DOWN -> { if (!dapsa) false else { danchuHagi(1); true } }
        KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { danchuNulleum(1); true }
        else -> false
    }
}

/** 답사 탭에 붙는 1.3.0 묶음 */
internal fun TourActivity.danchu130(k: Kos) {
    geul("화면 안 보고 답사하기 — 이어폰 단추·음량 단추", jemok = true)
    danchu(if (danchuOn) "이어폰 단추로 답사하기 — 켜짐. 누르면 끕니다" else "이어폰 단추로 답사하기 — 꺼짐. 누르면 켭니다") { danchuKyeogi(!danchuOn) }
    geul("이어폰 단추를 한 번 누르면 현장 메모 녹음을 시작하고, 다시 한 번 누르면 끝납니다. 두 번 누르면 나침반 카메라가 열리고, 열린 뒤 두 번 누르면 찍습니다. 세 번 누르면 이동 구간 재기를 시작하거나 끝냅니다. 답사 탭과 나침반 카메라에서는 음량 크게 단추로 사진, 작게 단추로 메모 녹음을 합니다. 켜 두는 동안 다른 앱의 음악은 멈출 수 있습니다.", jakge = true)
    geul("걸으며 저절로 재기 — 이동 구간 안내 초안", jemok = true)
    danchu(if (jaegiJeoljeoro) "경계를 적으면 저절로 재기 시작 — 켜짐. 누르면 끕니다" else "경계를 적으면 저절로 재기 시작 — 꺼짐. 누르면 켭니다") { sp.edit().putBoolean("geoleumAuto", !jaegiJeoljeoro).apply(); draw() }
    if (s13.jaegi) {
        geul(s13.jaegiJul).tag = "jaegiJul"
        danchu("도착 — 잰 것을 이동 구간 안내에 넣기", keun = true) { jaegiKkeut(true) }
        danchu("재기 그만두기 (넣지 않음)") { jaegiKkeut(false) }
    } else spot()?.let { s -> if (!k.isGyobon) danchu("여기부터 " + k.spotLab(s) + " 이동 구간 재기 시작") { jaegiSijak(s) } }
    geul("걸음 수, 꺾은 방향(출발할 때 바라본 쪽을 12시로), 계단 칸 수, 오르막과 내리막을 재어 지금 지점의 이동 구간 안내 칸 끝에 초안으로 붙입니다. 폰을 손에 들고 걸으십시오. 걸음과 계단은 어림이니 현장에서 확인해 고치십시오.", jakge = true)
}

// ================= ⑧ 판 기록 =================
internal fun TourActivity.panAlrigi() {
    if (me == null || Api.token.isEmpty()) return
    val oryu = sp.getString("oryuJul", "") ?: ""
    val b = JSONObject().put("app", "android").put("pan", TourActivity.PAN).put("bildeu", TourActivity.BILDEU)
        .put("os", "Android " + Build.VERSION.RELEASE).put("gigi", (Build.MANUFACTURER + " " + Build.MODEL).trim()).put("oryu", oryu)
    pool.execute { val j = Api.call("pan_ping", b); if (Api.ok(j) && oryu.isNotEmpty()) sp.edit().remove("oryuJul").apply() }
}
internal fun deoSae(a: String, b: String): Boolean {
    val x = a.split(".").map { it.toIntOrNull() ?: 0 }; val y = b.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until max(x.size, y.size)) { val p = x.getOrElse(i) { 0 }; val q = y.getOrElse(i) { 0 }; if (p != q) return p > q }
    return false
}
internal fun TourActivity.panGirokHwamyeon() {
    geul("판 기록 — 누가 어떤 판을 쓰나", jemok = true); allimJul()
    danchu("다시 받기") { s13.panRows = null; draw() }
    val rows = s13.panRows
    if (rows == null) {
        geul("불러오는 중입니다.")
        api("pan_list") { j ->
            if (!Api.ok(j)) { malhagi(Api.err(j)); s13.panRows = listOf(); dasiGeuryeo(Hw.PanGirok); return@api }
            val L = jsonList(j.optJSONArray("list"))
            val choesin = HashMap<String, String>()
            for (o in L) { val a = o.optJSONObject("apps") ?: continue; for (key in a.keys()) { val p = a.optJSONObject(key)?.geul("pan") ?: ""; if (deoSae(p, choesin[key] ?: "0")) choesin[key] = p } }
            val out = mutableListOf<Pair<String, String>>(); var yet = 0; var ios = 0; var an = 0
            for (o in L) {
                val nm = o.geul("name").ifEmpty { o.geul("id") }; val a = o.optJSONObject("apps") ?: continue
                for (key in a.keys()) {
                    val x = a.optJSONObject(key) ?: continue; val p = x.geul("pan"); val old = deoSae(choesin[key] ?: "0", p)
                    if (old) yet++; if (key == "ios") ios++ else if (key == "android") an++
                    var t = nm + " — " + (if (key == "ios") "아이폰" else if (key == "android") "안드로이드" else "웹") + " " + p + "판 (빌드 " + x.geul("bildeu") + ")"
                    if (old) t += " ★옛 판"
                    t += ", " + x.geul("os") + ", " + x.geul("gigi") + ", 마지막 " + ttae(x.geul("last")).drop(5)
                    if (x.geul("oryu").isNotEmpty()) t += "\n최근 오류: " + x.geul("oryu")
                    out.add(x.geul("last") to t)
                }
            }
            s13.panRows = out.sortedByDescending { it.first }.map { it.second }
            s13.panYoyak = "아이폰 ${ios}명, 안드로이드 ${an}명" + if (yet > 0) ", 옛 판을 쓰는 분 ${yet}명" else ", 모두 새 판"
            malhagi(s13.panYoyak + "입니다."); dasiGeuryeo(Hw.PanGirok)
        }
        return
    }
    geul(s13.panYoyak, jemok = true)
    if (rows.isEmpty()) geul("아직 판을 알린 사람이 없습니다. 1.3.0판부터 들어오면 저절로 알립니다.")
    jangMok("pg", rows) { geul(it) }
}
