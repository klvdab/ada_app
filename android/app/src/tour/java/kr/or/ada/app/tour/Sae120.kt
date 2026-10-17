// LVD투어스튜디오 안드로이드 — 1.2.0판 화면 (빌드 261017-T4, 방클, 이사장님 지시 「웹에 있는 모든 기능을 겉과 속이 같게」)
// 아이폰 Tour/Gwanri120Views·Jageop120Views·Kamera120·Gwan 과 같은 일을 같은 말로.
// 들어오기: 가입 신청, 비밀번호 바꾸기(처음 들어오면 먼저)
// 관리(최고관리자): 알림, 누구의 자리, 가입 신청 함, 명부, 봐 달라는 코스, 협회 승인 보관소(승인·돌려보내기·공개·내려받기·판 비교), 반, 수업 진행판, 수업 채점표, 클에게 대본 맡기기
// 작업: 내보내기·협회 최종 승인, 대본 살펴보기·원칙 16개, 장소 평면도, 답사 지도, 걷는 시간 어림, 테마투어 느낌 칸, 방향 도우미 스무 낱말,
//       용어 뜻 찾기·우리 용어 사전, 장소 지식 들이기, 우리가 확인한 것, 승인본 현장영상해설 화면, 점검, 기능 찾기
// 답사: 몸을 돌리면 저절로 찍기, 담는 모양, 사진 밝기·또렷함, 담을 때 소리, 폰 사진 넣기, 코스 벗어남 알림
package kr.or.ada.app.tour

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ExifInterface
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.util.Base64
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** 1.2.0 화면들이 다시 그려도 잊지 않게 쥐고 있는 것 */
class S120 {
    var sinCheong = false
    // 관리
    var asWhoName = ""
    var users: List<JSONObject>? = null
    var alerts = JSONObject()
    var classes = listOf<JSONObject>(); var curClass = ""
    var signups: List<JSONObject>? = null; var algil = ""; var algilWho = ""; var neolBan = ""
    var reviews: List<JSONObject>? = null
    var finals: List<JSONObject>? = null; var finalsMaster = false; var mukgi = ""; var mukgiBusy = false
    var compare: List<Array<String>>? = null; var compareMal = ""; var compareKey = ""
    var boardCls = "today"; var boardRows: List<JSONObject>? = null; var boardSum = ""; var boardLast = ""; var boardAuto = false
    var grade: List<Triple<String, List<String>, Array<String>>>? = null; var gradeGeul = ""; var gradeMal = "훑기를 누르면 학생마다 코스를 열어 검사합니다."
    var claudeA = 1; var claudeB = 1; var claudeMal = ""; var claudeBuchyeo = ""; var claudeChogan: JSONObject? = null
    // 작업
    var naeboFile: File? = null; var mapMal = ""
    var dictQ = ""; var dictList = listOf<JSONObject>(); var dictMal = ""; var terms: List<JSONObject>? = null
    var tMal = ""; var tDdeut = ""; var tChul = ""
    var jisikQ = ""; var tema = false; var mok = "110"; var jisik: JSONObject? = null; var jisikJy = listOf<String>()
    var order = mutableListOf<Int>(); var jisikMal = "장소 이름을 넣고 코스 뼈대 짜기를 누르십시오."; var dolnaem = false
    var uriTel = ""; var uriQ = ""; var uriOpen = false; var uriV = HashMap<String, String>(); var uriMal = ""
    var gineungQ = ""
    var diag: List<String>? = null
    var dirKan = "text"
    // 현장영상해설 화면
    var hjKey = ""; var hjC: JSONObject? = null; var hjEntry = JSONObject(); var hjIdx = -1; var hjMal = "여는 중입니다."; var hjTk = ""
    var hjLeft = 0; var hjTimer = false; var hjKkeojim = false; var hjIlkgi = false; var hjTtara = false; var hjTtaraJul = ""
    var hjLastM: Int? = null; var hjLastSaid = 0L
    var hjLm: LocationListener? = null
    // 코스 벗어남
    var offLm: LocationListener? = null; var offSince = 0L; var offOut = false; var offSaid = 0L; var offTti = ""
    // 수업 진행판으로 보내는 지금 상태
    var fails = 0; var err = ""
    // 저절로 찍기
    @Suppress("DEPRECATION") var cam: android.hardware.Camera? = null
    var camAuto = false; var heading: Double? = null; var gz: Double? = null; var base: Double? = null
    val got = HashSet<Int>(); var busy = false; var guide = false; var idx = 0; var lastBk: Int? = null; var lastTl = ""; var saidAt = 0L
    var camJul = "카메라를 여는 중입니다."
    var sensor: SensorEventListener? = null
}

// ================= 작은 도우미 =================
internal fun TourActivity.api(action: String, body: JSONObject = JSONObject(), timeoutMs: Int = 40000, cb: (JSONObject) -> Unit) {
    pool.execute { val j = Api.call(action, body, timeoutMs); main.post { cb(j) } }
}
internal fun TourActivity.dasiGeuryeo(h: Hw) { if (cur() == h) draw() }
internal fun jsonList(a: JSONArray?): List<JSONObject> = if (a == null) listOf() else (0 until a.length()).mapNotNull { a.optJSONObject(it) }
internal fun ttae(s: String, n: Int = 16) = s.replace("T", " ").take(n)
internal fun JSONObject.ip(k: String) = (su(k) ?: 0.0).toInt()
/** 글 칸 — 코스 저장과 따로(찾기 낱말·비밀번호 같은 것) */
internal fun TourActivity.ipryeok(ireum: String, gap: String, yeoreo: Boolean = false, susja: Boolean = false, bimil: Boolean = false, neoki: (String) -> Unit): EditText {
    val e = EditText(this).apply {
        setText(gap); hint = ireum
        inputType = when { bimil -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; susja -> InputType.TYPE_CLASS_NUMBER
            yeoreo -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; else -> InputType.TYPE_CLASS_TEXT }
        if (yeoreo) { minLines = 2; isSingleLine = false }
        background = kadeuBg(); setPadding(dp(14), dp(12), dp(14), dp(12)); setTextColor(Saek.geulja(this@ipryeok))
        addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { neoki(s?.toString() ?: "") }
        })
    }
    body.addView(e, lp(4)); return e
}
internal fun TourActivity.isMaster() = me?.geul("role") == "master"
internal fun TourActivity.meName() = me?.geul("name") ?: ""
internal fun httpGet(u: String, timeoutMs: Int = 120000): String? = try {
    val c = URL(u).openConnection() as HttpURLConnection
    c.connectTimeout = 15000; c.readTimeout = timeoutMs; c.useCaches = false
    c.setRequestProperty("User-Agent", "LVDTourStudio/1.2 (lvd.ada.or.kr)")
    val t = (if (c.responseCode in 200..299) c.inputStream else (c.errorStream ?: c.inputStream)).bufferedReader(Charsets.UTF_8).readText()
    c.disconnect(); t
} catch (e: Exception) { null }
internal fun httpPostForm(u: String, form: Map<String, String>): String? = try {
    val c = URL(u).openConnection() as HttpURLConnection
    c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 15000; c.readTimeout = 60000
    c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
    val b = form.entries.joinToString("&") { it.key + "=" + URLEncoder.encode(it.value, "UTF-8") }
    c.outputStream.use { it.write(b.toByteArray(Charsets.UTF_8)) }
    val t = (if (c.responseCode in 200..299) c.inputStream else (c.errorStream ?: c.inputStream)).bufferedReader(Charsets.UTF_8).readText()
    c.disconnect(); t
} catch (e: Exception) { null }
internal fun jsonOr(t: String?): JSONObject = try { JSONObject(t ?: "") } catch (e: Exception) { JSONObject().put("ok", false).put("err", if (t == null) "닿지 못했습니다. 인터넷을 확인해 주십시오." else "이상한 대답을 받았습니다.") }
internal fun bakkat(path: String, q: Map<String, String>): JSONObject {
    val u = Uri.parse("https://lvd.ada.or.kr/lvdts/$path").buildUpon().apply { q.forEach { (k, v) -> appendQueryParameter(k, v) } }.build().toString()
    return jsonOr(httpGet(u))
}
internal fun apiUrl(q: Map<String, String>): String = Uri.parse(Api.BASE).buildUpon().apply { q.forEach { (k, v) -> appendQueryParameter(k, v) } }.build().toString()
/** 주소의 파일을 이 폰에 받아 두기 — 보내기·저장하기는 nanugi 로 */
internal fun TourActivity.naeryeogi(u: String, ireum: String): File? = try {
    val dir = File(cacheDir, "sajin/naeryeo").apply { mkdirs() }
    val f = File(dir, ireum.replace("/", "_"))
    val c = URL(u).openConnection() as HttpURLConnection
    c.connectTimeout = 15000; c.readTimeout = 300000
    if (c.responseCode >= 400) null else { c.inputStream.use { inp -> f.outputStream().use { inp.copyTo(it) } }; f }
} catch (e: Exception) { null }
internal fun TourActivity.nanugi(f: File) {
    try {
        val u = FileProvider.getUriForFile(this, "$packageName.sajin", f)
        val t = when (f.extension.lowercase()) { "txt" -> "text/plain"; "json" -> "application/json"; "zip" -> "application/zip"; "mp3" -> "audio/mpeg"; "wav" -> "audio/wav"; else -> "application/octet-stream" }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(t).putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "보내기·저장하기 — " + f.name))
    } catch (e: Exception) { malhagi("보내기 화면을 열지 못했습니다.") }
}
internal fun TourActivity.ppi() { try { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).startTone(ToneGenerator.TONE_PROP_BEEP, 150) } catch (e: Exception) { } }
@Suppress("DEPRECATION")
internal fun TourActivity.deureul() {
    try { val v = getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator; if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE)) else v.vibrate(400) } catch (e: Exception) { }
}
internal fun TourActivity.mutgi2(mal: String, ye: String, kkeut: () -> Unit) {
    android.app.AlertDialog.Builder(this).setMessage(mal).setPositiveButton(ye) { _, _ -> kkeut() }.setNegativeButton("그만두기", null).show()
}
/** 글을 적어 받는 묻기 창 — 돌려보내는 까닭·새 비밀번호 */
internal fun TourActivity.mutgiGeul(mal: String, gap: String, ye: String, bimil: Boolean = false, kkeut: (String) -> Unit) {
    val e = EditText(this).apply { setText(gap); inputType = if (bimil) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }
    android.app.AlertDialog.Builder(this).setMessage(mal).setView(e).setPositiveButton(ye) { _, _ -> kkeut(e.text.toString()) }.setNegativeButton("그만두기", null).show()
}

// ================= 지도 (북쪽이 위) — 글로 된 방향 안내가 늘 함께 있음 =================
@SuppressLint("SetJavaScriptEnabled")
internal fun TourActivity.jido(pts: List<Triple<Int, String, Pair<Double, Double>>>, gijun: Pair<Double, Double>? = null, seon: List<Pair<Pair<Double, Double>, Pair<Double, Double>>> = listOf()) {
    if (pts.isEmpty() && gijun == null) return
    val P = JSONArray(); pts.forEach { P.put(JSONArray().put(it.first).put(it.second).put(it.third.first).put(it.third.second)) }
    val S = JSONArray(); seon.forEach { S.put(JSONArray().put(it.first.first).put(it.first.second).put(it.second.first).put(it.second.second)) }
    val G = if (gijun == null) "null" else "[${gijun.first},${gijun.second}]"
    val html = "<!doctype html><html><head><meta name=viewport content='width=device-width,initial-scale=1'><link rel=stylesheet href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'>" +
        "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script><style>html,body,#m{margin:0;height:100%}.n{background:#2e5c4f;color:#fff;border-radius:50%;text-align:center;font:bold 13px sans-serif;line-height:24px}</style></head><body><div id=m></div><script>" +
        "var P=$P,S=$S,G=$G;var m=L.map('m');L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(m);var b=[];" +
        "P.forEach(function(p){L.marker([p[2],p[3]],{icon:L.divIcon({className:'n',html:''+p[0],iconSize:[24,24]})}).addTo(m).bindTooltip(p[1]);b.push([p[2],p[3]]);});" +
        "if(P.length>1)L.polyline(P.map(function(p){return[p[2],p[3]]}),{color:'#2e5c4f',dashArray:'6 6'}).addTo(m);" +
        "S.forEach(function(s){L.polyline([[s[0],s[1]],[s[2],s[3]]],{color:'#8c3b2e',weight:3}).addTo(m);});" +
        "if(G){L.circleMarker(G,{radius:8,color:'#8c3b2e'}).addTo(m).bindTooltip('기준 자리');b.push(G);}" +
        "if(b.length>1)m.fitBounds(b,{padding:[24,24]});else m.setView(b[0],17);</script></body></html>"
    val w = WebView(this).apply {
        settings.javaScriptEnabled = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        loadDataWithBaseURL("https://lvd.ada.or.kr/", html, "text/html", "utf-8", null)
    }
    body.addView(w, LinearLayout.LayoutParams(-1, dp(320)).apply { topMargin = dp(6) })
}
internal fun Pair<Double, Double>.ll() = this

// ================= 들어오기: 가입 신청·비밀번호 =================
internal fun TourActivity.sinCheongHwamyeon() {
    meori()
    geul("가입 신청", jemok = true)
    geul("이름, 전화번호, 비밀번호를 적어 보내시면 협회에서 확인한 뒤 열어 드립니다. 전화번호가 곧 아이디가 됩니다.")
    var name = ""; var tel = ""; var pw = ""; var pw2 = ""; var note = ""
    ipryeok("이름", "") { name = it }
    ipryeok("전화번호 (아이디가 됩니다)", "", susja = true) { tel = it }
    ipryeok("비밀번호 (일곱 글자 이상)", "", bimil = true) { pw = it }
    ipryeok("비밀번호 한 번 더", "", bimil = true) { pw2 = it }
    ipryeok("협회에 전할 말 (없어도 됩니다)", "", yeoreo = true) { note = it }
    val mal = geul("")
    fun alri(t: String) { mal.text = t; cheotJul(mal) }
    danchu("가입 신청 보내기", keun = true) {
        val t = tel.filter { it.isDigit() }
        when {
            name.isBlank() -> alri("이름을 적어 주십시오.")
            t.length < 9 || t.length > 12 -> alri("전화번호를 정확히 적어 주십시오.")
            pw.length < 7 -> alri("비밀번호는 여섯 글자보다 길게 정해 주십시오.")
            pw != pw2 -> alri("비밀번호 두 칸이 서로 다릅니다.")
            else -> api("signup", JSONObject().put("id", t).put("name", name.trim()).put("pw", pw).put("tel", tel.trim()).put("note", note)) { j ->
                if (Api.ok(j)) alri("신청을 보냈습니다. 협회에서 확인한 뒤 열어 드립니다." + (if (j.optBoolean("again")) " 앞서 넣으신 신청은 이번 것으로 바뀝니다." else "") + " 열리면 아이디 $t 로 들어오십시오.")
                else alri(Api.err(j))
            }
        }
    }
    danchu("들어오기 화면으로") { s12.sinCheong = false; draw() }
}
internal fun TourActivity.pwHwamyeon(gangje: Boolean) {
    geul("비밀번호 바꾸기", jemok = true)
    if (gangje) geul("처음 들어오셨습니다. 쓰실 비밀번호로 바꿔 주십시오.")
    var old = ""; var n1 = ""; var n2 = ""
    ipryeok("지금 비밀번호", "", bimil = true) { old = it }
    ipryeok("새 비밀번호 (일곱 글자 이상)", "", bimil = true) { n1 = it }
    ipryeok("새 비밀번호 한 번 더", "", bimil = true) { n2 = it }
    danchu("비밀번호 바꾸기", keun = true) {
        if (n1 != n2) { malhagi("새 비밀번호 두 칸이 서로 다릅니다."); draw(); return@danchu }
        api("change_pw", JSONObject().put("old", old).put("new", n1)) { j ->
            if (Api.ok(j)) { Api.token = j.geul("token"); me?.put("mustChange", false); malhagi("비밀번호를 바꿨습니다."); if (!gangje) onBackPressed() else draw() }
            else { malhagi(Api.err(j)); draw() }
        }
    }
    allimJul()
}

// ================= 코스 탭에 더하는 것 =================
internal fun TourActivity.kosTab120(k: Kos?) {
    if (k != null) {
        geul("다음 할 일", jemok = true)
        geul(Wonchik.nextTodo(k, meName()))
        danchu("대본 살펴보기·원칙 16개 점검표") { go(Hw.Jeomgeom) }
        danchu("내보내기·협회 최종 승인 요청 — " + Wonchik.finalStatus(k.d.optJSONObject("final"))) { go(Hw.Naebo) }
        if (isMaster() && !k.isGyobon) danchu("클에게 대본 맡기기") { s12.claudeChogan = null; s12.claudeMal = ""; go(Hw.Claude) }
        danchu("기능 찾기") { go(Hw.Gineung) }
    }
}
internal fun TourActivity.gwanriSection() {
    if (isMaster()) {
        val a = s12.alerts
        val su = a.ip("signups") + a.ip("reviews") + a.ip("finals") + a.ip("claude")
        val L = mutableListOf<String>()
        if (a.ip("signups") > 0) L.add("기다리는 가입 신청 ${a.ip("signups")}건")
        if (a.ip("reviews") > 0) L.add("봐 달라는 코스 ${a.ip("reviews")}건")
        if (a.ip("finals") > 0) L.add("승인 기다리는 시나리오 ${a.ip("finals")}건")
        if (a.ip("claude") > 0) L.add("클 초안이 온 요청 ${a.ip("claude")}건 (코스를 열어 클에게 대본 맡기기에서 받으십시오)")
        geul("알림 — " + if (su == 0) "기다리는 것이 없습니다." else L.joinToString(", ") + "입니다.", jemok = true)
        if (a.ip("signups") > 0) danchu("기다리는 가입 신청 ${a.ip("signups")}건 보기") { s12.signups = null; go(Hw.Signups) }
        if (a.ip("reviews") > 0) danchu("봐 달라는 코스 ${a.ip("reviews")}건 보기") { s12.reviews = null; go(Hw.Reviews) }
        if (a.ip("finals") > 0) danchu("승인 기다리는 시나리오 ${a.ip("finals")}건 보기") { s12.finals = null; go(Hw.Finals(false)) }
        danchu("알림 다시 살피기") { allimSalpigi(true) }
        geul("누구의 자리를 볼지", jemok = true)
        if (Api.asWho.isNotEmpty()) { geul("지금 " + s12.asWhoName + " 님 자리를 보고 있습니다."); danchu("내 자리로") { boda("", "") } }
        danchu("다른 사람 자리 고르기") { s12.users = null; go(Hw.AsPick) }
        geul("최고관리자 도구", jemok = true)
        danchu("가입 신청 함") { s12.signups = null; go(Hw.Signups) }
        danchu("명부 관리") { s12.users = null; go(Hw.Users) }
        danchu("반 관리") { go(Hw.Classes) }
        danchu("수업 진행판") { s12.boardRows = null; go(Hw.Board) }
        danchu("수업 채점표") { go(Hw.Grade) }
        danchu("협회 승인 보관소") { s12.finals = null; go(Hw.Finals(false)) }
        danchu("봐 달라는 코스") { s12.reviews = null; go(Hw.Reviews) }
    }
    geul("도구", jemok = true)
    danchu("장소 지식 들이기 (코스 뼈대 짜기)") { go(Hw.Jisik) }
    danchu("우리가 확인한 것 채워 넣기") { s12.uriOpen = false; s12.uriQ = kos?.s("place") ?: ""; go(Hw.Urijaryo) }
    danchu("현장영상해설 화면 (협회 승인본)") { s12.finals = null; go(Hw.Finals(true)) }
    danchu("비밀번호 바꾸기") { go(Hw.Pw) }
    danchu("점검 (카메라·위치·마이크·나스)") { s12.diag = null; go(Hw.Diag) }
}
internal fun TourActivity.ssiatSection() {
    geul("이 코스로 시작하기 (씨앗 코스 — 지점이 미리 들어 있음)", jemok = true)
    danchu("씨앗 코스 고르기") {
        android.app.AlertDialog.Builder(this).setTitle("씨앗 코스 고르기")
            .setItems(Wonchik.SEEDS.map { it.name + " — " + it.place }.toTypedArray()) { _, i -> ssiatSijak(Wonchik.SEEDS[i]) }
            .setNegativeButton("그만두기", null).show()
    }
    danchu("장소 지식 들이기 (장소 이름으로 코스 뼈대 짜기)") { go(Hw.Jisik) }
}
/** 씨앗 코스 — 웹 newCourse(seed) 와 같음 */
internal fun TourActivity.ssiatSijak(x: Wonchik.Seed) {
    api("new_course", JSONObject().put("name", x.name).put("place", x.place)) { j ->
        if (!Api.ok(j)) { malhagi(Api.err(j)); draw(); return@api }
        val id = j.optJSONObject("course")?.geul("id")?.ifEmpty { null } ?: j.geul("id")
        loadCourses()
        openKos(id) { ok ->
            val k = kos
            if (ok && k != null) {
                k.d.put("field", x.field).put("meet", x.meet).put("note", x.note)
                var cur: Spot? = k.openSpot
                for (n in x.spots) { val s = k.addSpot(cur, n); s.d.put("walkIn", 5); cur = s }
                k.syncMove()
                save(true)
                malhagi(x.name + " 을(를) 바탕으로 시작했습니다. 지점 ${k.spots.size}곳이 미리 들어갔습니다."); draw()
            }
        }
    }
}
internal fun TourActivity.allimSalpigi(tell: Boolean) {
    if (!isMaster()) return
    api("alerts") { j -> if (Api.ok(j)) { s12.alerts = j; val su = j.ip("signups") + j.ip("reviews") + j.ip("finals") + j.ip("claude"); if (tell && su > 0) malhagi("기다리는 것이 ${su}건 있습니다. 코스 탭 알림 줄에서 골라 주십시오."); dasiGeuryeo(Hw.Kos) } }
}
internal fun TourActivity.boda(id: String, name: String) {
    Api.asWho = id; s12.asWhoName = name
    kos = null; for (st in stacks) while (st.size > 1) st.removeAt(st.size - 1)
    tab = 0
    loadCourses { malhagi(if (id.isEmpty()) "내 자리로 돌아왔습니다. 코스 ${courses.size}개입니다." else "$name 님 자리를 봅니다. 코스 ${courses.size}개입니다.") }
    draw()
}

// ================= 관리 화면 =================
internal fun TourActivity.asPick() {
    geul("누구의 자리를 볼지", jemok = true)
    val L = s12.users
    if (L == null) { geul("불러오는 중입니다."); api("list_users") { j -> s12.users = jsonList(j.optJSONArray("users")); dasiGeuryeo(Hw.AsPick) }; return }
    danchu("내 자리") { boda("", "") }
    jangMok("asp", L) { u -> val id = u.geul("id"); danchu(u.geul("name") + " ($id)" + if (id == Api.asWho) " — 지금 보는 자리" else "") { boda(id, u.geul("name")) } }
}
internal fun TourActivity.signupsHwamyeon() {
    geul("가입 신청 함", jemok = true); allimJul()
    if (s12.algil.isNotEmpty()) {
        geul("그분께 알릴 말 — " + s12.algilWho, jemok = true)
        geul(s12.algil).setTextIsSelectable(true)
        danchu("글 복사하기") { (getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("알림", s12.algil)); malhagi("복사했습니다. 문자나 카카오톡에 붙여 보내십시오.") }
        danchu("문자·카톡으로 보내기") { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, s12.algil), "보내기")) }
    }
    val L = s12.signups
    if (L == null) {
        geul("훑는 중입니다.")
        api("list_classes") { c -> if (Api.ok(c)) { s12.classes = jsonList(c.optJSONArray("list")); s12.curClass = c.geul("cur") } }
        api("list_signups") { j -> s12.signups = if (Api.ok(j)) jsonList(j.optJSONArray("list")) else listOf(); if (!Api.ok(j)) malhagi(Api.err(j)); dasiGeuryeo(Hw.Signups) }
        return
    }
    geul(if (L.isEmpty()) "기다리는 신청이 없습니다." else "기다리는 신청 ${L.size}건입니다.")
    if (L.isEmpty()) return
    val banNm = s12.classes.firstOrNull { it.geul("id") == s12.neolBan }?.geul("name") ?: "넣지 않음"
    danchu("받아들이면서 넣을 반 — $banNm. 누르면 고릅니다") {
        val mok = listOf("" to "넣지 않음") + s12.classes.map { it.geul("id") to it.geul("name") }
        android.app.AlertDialog.Builder(this).setTitle("넣을 반 고르기").setItems(mok.map { it.second }.toTypedArray()) { _, i -> s12.neolBan = mok[i].first; draw() }.setNegativeButton("그만두기", null).show()
    }
    danchu("기다리는 신청 ${L.size}건 모두 받아들이기") {
        pool.execute {
            var ok = 0
            for (w in L) { val j = Api.call("approve_signup", JSONObject().put("id", w.geul("id"))); if (Api.ok(j)) { ok++; if (s12.neolBan.isNotEmpty()) Api.call("save_class", JSONObject().put("id", s12.neolBan).put("add", JSONArray().put(w.geul("id")))) } }
            main.post { malhagi("${ok}건을 받아들였습니다."); s12.signups = null; allimSalpigi(false); draw() }
        }
    }
    jangMok("su", L) { x ->
        val id = x.geul("id")
        val v = geul(x.geul("name") + " ($id)\n신청 " + ttae(x.geul("at")) + (if (x.geul("tel").isEmpty()) "" else " / 연락처 " + x.geul("tel")) + (if (x.geul("note").isEmpty()) "" else "\n" + x.geul("note")))
        danchu("받아들이기 — " + x.geul("name")) { sinCheongCheori("approve_signup", x) }
        danchu("거절하기 — " + x.geul("name")) { mutgi2(x.geul("name") + " 님의 신청을 거절할까요?", "거절하기") { sinCheongCheori("reject_signup", x) } }
        v
    }
}
internal fun tellText(who: JSONObject, yes: Boolean): String {
    val name = who.geul("name").ifEmpty { who.geul("id") } + " 님"
    return if (yes) "$name, 안녕하세요. 사단법인 한국시각장애인현장영상해설협회입니다.\n신청하신 LVD투어스튜디오 가입이 열렸습니다.\n\n주소: https://lvd.ada.or.kr/lvdts/lvdts.html\n아이디: " + who.geul("id") +
        "\n비밀번호: 신청하실 때 정하신 그대로입니다.\n\n휴대전화로 여시면 현장에서 사진을 담으실 수 있습니다. 앱(투어스튜디오)으로도 같은 아이디로 들어오실 수 있습니다.\n처음이시면 도움말을 먼저 보십시오."
    else "$name, 안녕하세요. 사단법인 한국시각장애인현장영상해설협회입니다.\n신청해 주셔서 고맙습니다. 다만 이번에는 가입을 열어 드리지 못했습니다.\n자세한 사정은 협회로 연락 주시면 말씀드리겠습니다."
}
internal fun TourActivity.sinCheongCheori(a: String, who: JSONObject) {
    val id = who.geul("id")
    api(a, JSONObject().put("id", id)) { j ->
        if (!Api.ok(j)) { malhagi(Api.err(j)); draw(); return@api }
        if (a == "approve_signup" && s12.neolBan.isNotEmpty()) pool.execute { Api.call("save_class", JSONObject().put("id", s12.neolBan).put("add", JSONArray().put(id))) }
        s12.algil = tellText(who, a == "approve_signup")
        s12.algilWho = who.geul("name") + " ($id)" + if (who.geul("tel").isEmpty()) " / 연락처가 적혀 있지 않습니다" else " / 연락처 " + who.geul("tel")
        malhagi((if (a == "approve_signup") "받아들였습니다. " else "거절했습니다. ") + "그분께 보낼 글을 만들어 두었습니다.")
        s12.signups = null; allimSalpigi(false); draw()
    }
}
internal fun TourActivity.usersHwamyeon() {
    geul("명부 관리", jemok = true); allimJul()
    geul("사람 넣기", jemok = true)
    var sid = ""; var snm = ""; var spw = ""
    ipryeok("아이디 (영문 소문자·숫자 또는 전화번호)", "") { sid = it }
    ipryeok("이름", "") { snm = it }
    ipryeok("처음 비밀번호 (여섯 글자보다 길게)", "", bimil = true) { spw = it }
    danchu("명부에 넣기") {
        api("add_user", JSONObject().put("id", sid.trim()).put("name", snm.trim()).put("pw", spw)) { j ->
            if (Api.ok(j)) { malhagi("$snm 님을 넣었습니다. 처음 들어오면 비밀번호를 바꾸게 됩니다."); s12.users = null } else malhagi(Api.err(j)); draw()
        }
    }
    val L = s12.users
    if (L == null) { geul("불러오는 중입니다."); api("list_users") { j -> s12.users = jsonList(j.optJSONArray("users")); dasiGeuryeo(Hw.Users) }; return }
    geul("모두 ${L.size}명", jemok = true)
    jangMok("us", L) { u ->
        val id = u.geul("id"); val locked = u.optBoolean("locked")
        val v = geul(u.geul("name") + " ($id) — " + (if (u.geul("role") == "master") "최고관리자" else "현장영상해설사") + if (locked) " / 막힘" else "")
        danchu("비밀번호 다시 주기 — " + u.geul("name")) {
            mutgiGeul(u.geul("name") + " 님의 새 비밀번호 (여섯 글자보다 길게)", "", "주기", bimil = true) { pw ->
                api("reset_pw", JSONObject().put("id", id).put("pw", pw)) { j -> malhagi(if (Api.ok(j)) "새 비밀번호를 주었습니다. 그분은 첫 로그인 때 다시 바꾸게 됩니다." else Api.err(j)); draw() }
            }
        }
        danchu((if (locked) "다시 열기 — " else "못 들어오게 막기 — ") + u.geul("name")) {
            api("set_lock", JSONObject().put("id", id).put("lock", !locked)) { j -> malhagi(if (Api.ok(j)) "바꿨습니다." else Api.err(j)); s12.users = null; draw() }
        }
        v
    }
}
internal fun TourActivity.reviewsHwamyeon() {
    geul("봐 달라는 코스", jemok = true); allimJul()
    val L = s12.reviews
    if (L == null) { geul("훑는 중입니다."); api("list_reviews") { j -> s12.reviews = if (Api.ok(j)) jsonList(j.optJSONArray("list")) else listOf(); dasiGeuryeo(Hw.Reviews) }; return }
    geul(if (L.isEmpty()) "봐 달라는 코스가 없습니다." else "봐 달라는 코스 ${L.size}건입니다.")
    jangMok("rv", L) { x ->
        val nugu = x.geul("ownerName").ifEmpty { x.geul("owner") }
        val v = geul(nugu + " — " + x.geul("name") + "\n" + x.geul("place") + " / 지점 ${x.ip("spots")}곳, 대본 ${x.ip("texts")}곳 / " + ttae(x.geul("at")) + (if (x.geul("msg").isEmpty()) "" else "\n" + x.geul("msg")))
        danchu("이 코스 열어 보기 — " + x.geul("name")) {
            Api.asWho = x.geul("owner"); s12.asWhoName = nugu
            openKos(x.geul("id")) { ok -> if (ok) { malhagi(x.geul("name") + " 코스를 열었습니다. $nugu 님 자리입니다."); tab = 2; draw() } }
        }
        danchu("확인함 (내리기) — " + x.geul("name")) {
            api("drop_review", JSONObject().put("owner", x.geul("owner")).put("c", x.geul("id"))) { j -> malhagi(if (Api.ok(j)) "내렸습니다." else Api.err(j)); s12.reviews = null; allimSalpigi(false); draw() }
        }
        v
    }
}
internal fun finalSt(x: JSONObject): String = when (x.geul("status")) {
    "approved" -> "승인 완료 · " + x.geul("no") + if (x.optBoolean("public")) " · 현장영상해설사 모두에게 공개" else " · 작성자만"
    "pending" -> "승인 기다리는 중"
    "returned" -> "돌려보냄 — " + x.geul("reason")
    "superseded" -> "새 판으로 대체됨 (" + x.geul("no") + ")"
    else -> "앞 요청 (새 판으로 대체됨)"
}
internal fun fq(x: JSONObject) = JSONObject().put("owner", x.geul("owner")).put("c", x.geul("cid")).put("v", x.ip("ver"))
internal fun TourActivity.finalsHwamyeon(hj: Boolean) {
    val h = Hw.Finals(hj)
    geul(if (hj) "현장영상해설 화면 — 협회 승인본 고르기" else "협회 승인 보관소", jemok = true); allimJul()
    val L0 = s12.finals
    if (L0 == null) {
        geul("훑는 중입니다.")
        api("list_final") { j -> s12.finals = if (Api.ok(j)) jsonList(j.optJSONArray("list")) else listOf(); s12.finalsMaster = j.optBoolean("master"); if (!Api.ok(j)) malhagi(Api.err(j)); dasiGeuryeo(h) }
        return
    }
    val master = s12.finalsMaster
    s12.naeboFile?.let { f -> danchu("받은 파일 보내기·저장하기 — " + f.name, keun = true) { nanugi(f) } }
    if (master && !hj) {
        geul("제출된 최종본 모두 내려받기", jemok = true)
        danchu("제출된 최종본 모두 내려받기 (워드와 소리 묶음)") { moduNaeryeo(h) }
        if (s12.mukgi.isNotEmpty()) geul(s12.mukgi)
    }
    val rows = if (hj) L0.filter { it.geul("status") == "approved" } else L0
    geul(if (rows.isEmpty()) (if (hj) "아직 현장영상해설에 쓸 수 있는 승인본이 없습니다." else "협회 DB에 저장된 판이 없습니다.") else if (hj) "승인본 ${rows.size}건 — 고르시면 현장영상해설 화면이 열립니다" else "협회 DB에 저장된 판 ${rows.size}건", jemok = true)
    jangMok(if (hj) "fh" else "fn", rows) { x ->
        val v = x.ip("ver")
        val prev = L0.filter { it.geul("owner") == x.geul("owner") && it.geul("cid") == x.geul("cid") && it.ip("ver") < v }.maxOfOrNull { it.ip("ver") }
        val nugu = x.geul("ownerName").ifEmpty { x.geul("owner") }
        val t = geul(nugu + " — " + x.geul("name") + " (${v}판)\n" + x.geul("place") + " / 지점 ${x.ip("spots")}곳, 대본 ${x.ip("texts")}곳 / 사진 ${x.ip("photos")}장, 소리 ${x.ip("audio")}개 / 요청 " + ttae(x.geul("requestedAt")) + "\n" + finalSt(x) + (if (x.geul("msg").isEmpty()) "" else "\n전한 말: " + x.geul("msg")))
        danchu("이 판 현장영상해설 화면으로 보기") { s12.hjKey = ""; go(Hw.Hyeonjang(x.toString())) }
        if (master && !hj) {
            danchu("이 시나리오 내려받기 (워드와 소리 묶음)") {
                malhagi("내려받는 중입니다. 소리가 많으면 몇 분 걸릴 수 있습니다.")
                pool.execute {
                    val u = apiUrl(mapOf("action" to "final_down", "tk" to Api.needTicket(), "o" to x.geul("owner"), "c" to x.geul("cid"), "v" to v.toString()))
                    val f = naeryeogi(u, nugu + "_" + x.geul("name") + "_" + v + "판.zip")
                    main.post { if (f == null) malhagi("내려받지 못했습니다. 다시 눌러 주십시오.") else { s12.naeboFile = f; malhagi("받았습니다. 압축 파일 안에 워드 파일(대본과 사진)과 소리 폴더가 있습니다. 위의 받은 파일 보내기·저장하기를 누르십시오.") }; dasiGeuryeo(h) }
                }
            }
            if (prev != null) danchu("앞 판(${prev}판)과 비교") { s12.compare = null; go(Hw.Compare(x.geul("owner"), x.geul("cid"), prev, v)) }
            if (x.geul("status") == "pending") {
                danchu("최종 승인") {
                    mutgi2("이 판을 협회의 최종 승인본으로 확정합니다. 승인번호가 붙고 작성자에게 알려집니다. 승인할까요?", "최종 승인") {
                        api("approve_final", fq(x)) { j -> malhagi(if (Api.ok(j)) "최종 승인했습니다. 승인번호 " + j.geul("no") + "." else Api.err(j)); s12.finals = null; allimSalpigi(false); loadCourses(); draw() }
                    }
                }
                danchu("돌려보내기 (까닭 적기)") {
                    api("final_get", fq(x)) { g ->
                        var kk = ""
                        g.optJSONObject("course")?.let { c -> kk = Wonchik.issueLines(Wonchik.scriptIssues(Kos(c), g.optJSONObject("entry")?.geul("ownerName") ?: "")).joinToString(" / ") }
                        mutgiGeul("돌려보내는 까닭 — 작성자에게 그대로 보입니다. 검사에서 걸린 것을 미리 넣어 두었습니다.", kk, "돌려보내기") { r ->
                            api("return_final", fq(x).put("reason", r)) { j -> malhagi(if (Api.ok(j)) "돌려보냈습니다." else Api.err(j)); s12.finals = null; allimSalpigi(false); draw() }
                        }
                    }
                }
            }
            if (x.geul("status") == "approved") {
                val pub = x.optBoolean("public")
                danchu(if (pub) "공개 거두기" else "현장영상해설사 모두에게 공개") {
                    api("set_final_public", fq(x).put("on", if (pub) 0 else 1)) { j -> malhagi(if (Api.ok(j)) (if (pub) "공개를 거두었습니다." else "현장영상해설사 모두에게 공개했습니다.") else Api.err(j)); s12.finals = null; draw() }
                }
            }
        } else if (!master && prev != null && !hj) danchu("앞 판(${prev}판)과 비교") { s12.compare = null; go(Hw.Compare(x.geul("owner"), x.geul("cid"), prev, v)) }
        t
    }
}
internal fun TourActivity.moduNaeryeo(h: Hw) {
    if (s12.mukgiBusy) { malhagi("이미 묶는 중입니다."); return }
    s12.mukgiBusy = true
    malhagi("제출된 최종본을 모두 묶기 시작합니다. 몇 분 걸립니다. 이 화면을 그대로 두십시오.")
    pool.execute {
        var said = -1
        while (true) {
            val r = Api.call("final_pack", JSONObject(), 120000)
            if (!Api.ok(r)) { main.post { s12.mukgi = Api.err(r); s12.mukgiBusy = false; malhagi(s12.mukgi); dasiGeuryeo(h) }; return@execute }
            if (r.optBoolean("ready")) {
                main.post { s12.mukgi = "다 묶었습니다. 코스 ${r.ip("n")}건, " + r.geul("mb") + "MB. 이 폰으로 받는 중입니다."; malhagi(s12.mukgi); dasiGeuryeo(h) }
                val u = apiUrl(mapOf("action" to "final_pack_get", "tk" to Api.needTicket(), "key" to r.geul("key")))
                val f = naeryeogi(u, r.geul("name").ifEmpty { "제출최종본.zip" })
                main.post {
                    s12.mukgiBusy = false
                    if (f == null) s12.mukgi = "묶었으나 받지 못했습니다." else { s12.naeboFile = f; s12.mukgi = "받았습니다. 위의 받은 파일 보내기·저장하기를 누르십시오. 목록은 00_목록.txt 에 있습니다." }
                    malhagi(s12.mukgi); dasiGeuryeo(h)
                }
                return@execute
            }
            val done = r.ip("done")
            main.post { s12.mukgi = "${r.ip("n")}건 가운데 ${done}건 묶었습니다."; if (done >= said + 5 && done > 0) { said = done; malhagi(s12.mukgi) }; dasiGeuryeo(h) }
            Thread.sleep(4000)
        }
    }
}
internal fun TourActivity.compareHwamyeon(h: Hw.Compare) {
    geul("판 비교 — ${h.a}판 → ${h.b}판", jemok = true)
    val key = h.owner + h.cid + h.a + h.b
    val out = s12.compare
    if (out == null || s12.compareKey != key) {
        geul("두 판을 견주는 중입니다.")
        s12.compareKey = key
        pool.execute {
            val ra = Api.call("final_get", JSONObject().put("owner", h.owner).put("c", h.cid).put("v", h.a))
            val rb = Api.call("final_get", JSONObject().put("owner", h.owner).put("c", h.cid).put("v", h.b))
            val A = ra.optJSONObject("course"); val B = rb.optJSONObject("course")
            val o = mutableListOf<Array<String>>()
            if (A != null && B != null) {
                fun cmp(label: String, a: String, b: String) { if (a == b) return; val num = a.toDoubleOrNull() != null && b.toDoubleOrNull() != null
                    o.add(arrayOf(label, a, b, if (a.isEmpty()) "새로 들어감" else if (b.isEmpty()) "지워짐" else if (num) "바뀜" else "바뀜 (${a.length}자 → ${b.length}자)")) }
                for ((k, nm) in listOf("name" to "코스 이름", "place" to "장소", "meet" to "집합 장소", "note" to "코스 소개", "rain" to "비 올 때 동선", "opening" to "오프닝", "first" to "1번 첫 만남 인사말", "history" to "3번 장소 역사", "access" to "3번 위치와 접근 교통편", "scale" to "3번 규모와 형태", "points" to "3번 전체 포인트", "tactile" to "5번 촉각지도", "safety" to "6번 안전 고지", "refs" to "16번 참고문헌", "closing" to "클로징")) cmp(nm, A.geul(k), B.geul(k))
                val sa = jsonList(A.optJSONArray("spots")); val sb = jsonList(B.optJSONArray("spots"))
                for (i in 0 until max(sa.size, sb.size)) {
                    val x = sa.getOrNull(i); val y = sb.getOrNull(i)
                    val nm = "${i + 1}번 " + (y?.geul("name")?.ifEmpty { null } ?: x?.geul("name") ?: "")
                    if (x == null) { o.add(arrayOf(nm, "", y?.geul("text") ?: "", "지점이 새로 생김")); continue }
                    if (y == null) { o.add(arrayOf(nm, x.geul("text"), "", "지점이 없어짐")); continue }
                    for ((k, l) in listOf("name" to " 이름", "stay" to " 머무는 시간", "move" to " 걷는 시간", "text" to " 대본", "to" to " 어디까지", "access" to " 이동 구간 안내", "touch" to " 만져 볼 것", "safe" to " 조심할 것", "gpos" to " 현장영상해설사 위치")) cmp(nm + l, x.geul(k), y.geul(k))
                    val na = x.optJSONArray("shots")?.length() ?: 0; val nb = y.optJSONArray("shots")?.length() ?: 0
                    if (na != nb) o.add(arrayOf("$nm 사진", "${na}장", "${nb}장", "장수 바뀜"))
                }
            }
            main.post {
                s12.compare = o
                s12.compareMal = if (A == null || B == null) Api.err(if (Api.ok(ra)) rb else ra) else "${h.a}판과 ${h.b}판을 견주었습니다. 바뀐 것 ${o.size}가지입니다."
                malhagi(s12.compareMal + if (o.isEmpty()) "" else " " + o.take(5).joinToString(", ") { it[0] + " " + it[3] } + if (o.size > 5) " 등" else "")
                dasiGeuryeo(h)
            }
        }
        return
    }
    geul(s12.compareMal)
    jangMok("cp", out.withIndex().toList()) { (i, o) ->
        val e = { s: String -> s.ifEmpty { "(없음)" } }
        geul("${i + 1}. " + o[0] + " — " + o[3] + "\n${h.a}판: " + e(o[1]) + "\n${h.b}판: " + e(o[2]))
    }
}
internal fun TourActivity.banMok(kkeut: () -> Unit) {
    api("list_classes") { c -> if (Api.ok(c)) { s12.classes = jsonList(c.optJSONArray("list")); s12.curClass = c.geul("cur") }; kkeut() }
}
internal fun TourActivity.classesHwamyeon() {
    geul("반 관리", jemok = true); allimJul()
    geul("새 반", jemok = true)
    var sae = ""
    ipryeok("새 반 이름", "") { sae = it }
    danchu("새 반 만들기") {
        api("save_class", JSONObject().put("name", sae.trim()).put("cur", if (s12.classes.isEmpty()) 1 else 0)) { j ->
            if (Api.ok(j)) { malhagi("$sae 반을 만들었습니다. 반을 열어 학생을 넣으십시오."); banMok { draw() } } else { malhagi(Api.err(j)); draw() }
        }
    }
    val cur = s12.classes.firstOrNull { it.geul("id") == s12.curClass }
    geul("반 ${s12.classes.size}개" + if (cur != null) ", 지금 수업 반은 " + cur.geul("name") else ", 지금 수업 반은 아직 없습니다", jemok = true)
    danchu("반 목록 다시 받기") { banMok { draw() } }
    jangMok("cl", s12.classes) { c ->
        val n = c.optJSONArray("members")?.length() ?: 0
        danchu(c.geul("name") + " — ${n}명" + if (c.geul("id") == s12.curClass) ", 지금 수업 반" else "") { s12.users = null; go(Hw.ClassOne(c.geul("id"))) }
    }
    if (s12.classes.isEmpty()) banMok { dasiGeuryeo(Hw.Classes) }
}
internal fun TourActivity.classOne(id: String) {
    val c = s12.classes.firstOrNull { it.geul("id") == id } ?: JSONObject()
    val memA = c.optJSONArray("members"); val mem = (0 until (memA?.length() ?: 0)).map { memA!!.optString(it) }
    geul("반 — " + c.geul("name"), jemok = true); allimJul()
    val U = s12.users
    if (U == null) { geul("명부를 불러오는 중입니다."); api("list_users") { j -> s12.users = jsonList(j.optJSONArray("users")); dasiGeuryeo(Hw.ClassOne(id)) }; return }
    val users = U.filter { it.geul("role") != "master" }.sortedWith(compareBy({ if (mem.contains(it.geul("id"))) 0 else 1 }, { it.geul("name") }))
    geul("학생 ${mem.size}명이 이 반에 있습니다. 명부 ${users.size}명 가운데 반에 있는 사람이 먼저 옵니다.")
    danchu(if (id == s12.curClass) "지금 수업 반입니다 (누르면 풀기)" else "이 반을 지금 수업 반으로") {
        val on = id != s12.curClass
        api("save_class", JSONObject().put("id", id).put("cur", if (on) 1 else 0)) { j -> if (Api.ok(j)) { malhagi(if (on) c.geul("name") + " 반이 지금 수업 반이 되었습니다. 진행판과 채점표가 이 반만 봅니다." else "지금 수업 반을 풀었습니다."); banMok { draw() } } else { malhagi(Api.err(j)); draw() } }
    }
    var irum = c.geul("name")
    ipryeok("반 이름 바꾸기", irum) { irum = it }
    danchu("이름 바꾸기") { api("save_class", JSONObject().put("id", id).put("name", irum)) { j -> malhagi(if (Api.ok(j)) "이름을 바꿨습니다." else Api.err(j)); banMok { draw() } } }
    danchu("이 반 가리기 (지우지 않고 목록에서만 감춤)") {
        mutgi2(c.geul("name") + " 반을 가릴까요? 지우는 것이 아니라 목록에서만 감춥니다.", "가리기") {
            api("save_class", JSONObject().put("id", id).put("on", 0)) { j -> if (Api.ok(j)) { malhagi("가렸습니다."); banMok { onBackPressed() } } }
        }
    }
    geul("학생 넣고 빼기", jemok = true)
    jangMok("co", users) { u ->
        val uid = u.geul("id"); val inC = mem.contains(uid)
        danchu(u.geul("name") + " ($uid) — " + if (inC) "이 반에 있음, 누르면 빼기" else "반에 없음, 누르면 넣기") {
            api("save_class", JSONObject().put("id", id).put(if (inC) "remove" else "add", JSONArray().put(uid))) { j ->
                if (Api.ok(j)) banMok { val n = s12.classes.firstOrNull { it.geul("id") == id }?.optJSONArray("members")?.length() ?: 0; malhagi((if (inC) "뺐습니다. " else "넣었습니다. ") + "이 반 ${n}명."); draw() }
                else { malhagi(Api.err(j)); draw() }
            }
        }
    }
}
internal val SCNAME = mapOf("scLogin" to "들어오기", "scList" to "코스 목록", "scWork" to "코스", "scVer" to "판 되돌리기", "scDiag" to "점검", "scHelp" to "도움말", "scSignup" to "가입 신청", "scSignups" to "가입 신청 함", "scUsers" to "명부 관리", "scPw" to "비밀번호 바꾸기", "scBoard" to "수업 진행판", "scGrade" to "수업 채점표", "scClass" to "반 관리")
internal fun agoMal(s: Int) = if (s < 0) "아직 소식 없음" else if (s < 60) "방금" else if (s < 3600) "${s / 60}분 전" else "${s / 3600}시간 전"
internal fun boardW(x: JSONObject) = (if ((x.su("help") ?: 0.0) > 0) 4 else 0) + (if ((x.su("fails") ?: 0.0) > 0) 2 else 0) + (if ((x.su("ago") ?: -1.0) >= 300) 1 else 0)
internal fun boardLine(r: JSONObject): String {
    val p = mutableListOf<String>(); val ag = (r.su("ago") ?: -1.0).toInt()
    if ((r.su("help") ?: 0.0) > 0) p.add("★도움 요청" + if (r.geul("helpMsg").isEmpty()) "" else "(" + r.geul("helpMsg") + ")")
    if ((r.su("fails") ?: 0.0) > 0) p.add("★저장 실패 ${r.ip("fails")}회")
    if (ag >= 300) p.add("★멈춤 " + agoMal(ag))
    val where0 = if (ag < 0) "아직 들어오지 않음" else if (r.geul("sc") == "scWork") "${r.ip("stage")}단계" else (SCNAME[r.geul("sc")] ?: "다른 화면")
    var s = r.geul("name") + " — " + where0
    if (r.geul("cname").isNotEmpty()) s += ", " + r.geul("cname")
    if (r.geul("cid").isNotEmpty()) s += ", 지점 ${r.ip("spots")}곳 대본 ${r.ip("texts")}곳 사진 ${r.ip("photos")}장"
    if (r.geul("last").isNotEmpty() && ag >= 0) s += ", 마지막: " + r.geul("last") + " (" + agoMal(ag) + ")"
    if (r.geul("err").isNotEmpty()) s += ", 오류: " + r.geul("err")
    return (if (p.isEmpty()) "" else p.joinToString(" ") + " ") + s
}
internal fun TourActivity.boardLoad(sayIt: Boolean) {
    api("class_board", JSONObject().put("cls", s12.boardCls)) { j ->
        if (!Api.ok(j)) { s12.boardSum = Api.err(j); dasiGeuryeo(Hw.Board); return@api }
        val rows = jsonList(j.optJSONArray("rows")).sortedWith(compareByDescending<JSONObject> { boardW(it) }.thenBy { val a = (it.su("ago") ?: -1.0).toInt(); if (a < 0) 1_000_000_000 else a })
        s12.boardRows = rows
        val act = rows.count { val a = (it.su("ago") ?: -1.0); a >= 0 && a < 300 }
        val help = rows.count { (it.su("help") ?: 0.0) > 0 }; val fail = rows.count { (it.su("fails") ?: 0.0) > 0 }; val idle = rows.count { (it.su("ago") ?: -1.0) >= 300 }
        val s = "학생 ${rows.size}명 가운데 5분 안에 움직인 사람 ${act}명" + (if (help > 0) ", 도움 요청 ${help}명" else "") + (if (fail > 0) ", 저장 실패 ${fail}명" else "") + (if (idle > 0) ", 5분 넘게 멈춤 ${idle}명" else "") + "."
        val changed = s != s12.boardLast; s12.boardLast = s
        s12.boardSum = s + " " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.KOREA).format(java.util.Date()) + " 기준."
        if (sayIt || changed) malhagi(s)
        dasiGeuryeo(Hw.Board)
    }
}
internal val boardRun = HashMap<TourActivity, Runnable>()
internal fun TourActivity.boardHwamyeon() {
    geul("수업 진행판", jemok = true); allimJul()
    val cur = s12.classes.firstOrNull { it.geul("id") == s12.curClass }
    val nm = when (s12.boardCls) { "today" -> "오늘 들어온 사람만"; "all" -> "명부 모두"; else -> (s12.classes.firstOrNull { it.geul("id") == s12.boardCls }?.geul("name") ?: "") + " 반" }
    danchu("누구를 볼지 — $nm. 누르면 고릅니다") {
        val mok = mutableListOf<Pair<String, String>>()
        if (cur != null) mok.add(s12.curClass to "지금 수업 반 — " + cur.geul("name"))
        mok.add("today" to "오늘 들어온 사람만")
        s12.classes.filter { it.geul("id") != s12.curClass }.forEach { mok.add(it.geul("id") to it.geul("name") + " 반") }
        mok.add("all" to "명부 모두")
        android.app.AlertDialog.Builder(this).setTitle("누구를 볼지").setItems(mok.map { it.second }.toTypedArray()) { _, i -> s12.boardCls = mok[i].first; boardLoad(true) }.setNegativeButton("그만두기", null).show()
    }
    danchu("다시 살피기") { boardLoad(true) }
    danchu(if (s12.boardAuto) "저절로 살피기 끄기 (30초마다, 바뀌었을 때만 읽음)" else "저절로 살피기 켜기 (30초마다, 바뀌었을 때만 읽음)") {
        s12.boardAuto = !s12.boardAuto
        boardRun[this]?.let { main.removeCallbacks(it) }
        if (s12.boardAuto) { val r = object : Runnable { override fun run() { if (s12.boardAuto && cur() is Hw.Board) { boardLoad(false); main.postDelayed(this, 30000) } else s12.boardAuto = false } }; boardRun[this] = r; main.postDelayed(r, 30000) }
        draw()
    }
    geul(s12.boardSum)
    val rows = s12.boardRows
    if (rows == null) { banMok { if (s12.curClass.isNotEmpty() && s12.boardCls == "today") s12.boardCls = s12.curClass; boardLoad(true) }; return }
    geul("학생 ${rows.size}명 — 줄을 누르면 그 학생 코스가 열립니다", jemok = true)
    jangMok("bd", rows) { r ->
        danchu(boardLine(r)) {
            val c = r.geul("cid")
            if (c.isEmpty()) { malhagi("이 학생은 아직 연 코스가 없습니다."); return@danchu }
            s12.boardAuto = false
            Api.asWho = r.geul("id"); s12.asWhoName = r.geul("name")
            openKos(c) { ok -> if (ok) { malhagi(r.geul("name") + " 님의 " + (kos?.name ?: "") + " 코스를 열었습니다."); tab = 2; draw() } }
        }
    }
}
internal fun gradeStat(f: JSONObject?): String = if (f == null) "요청 전" else when (f.geul("status")) { "approved" -> "승인 " + f.geul("no"); "pending" -> "승인 기다림"; "returned" -> "돌려보냄"; else -> "" }
internal fun TourActivity.gradeHwamyeon() {
    geul("수업 채점표", jemok = true); allimJul()
    danchu("학생 코스 모두 훑기", keun = true) {
        s12.grade = null; s12.gradeGeul = ""; s12.gradeMal = "학생 명부를 읽는 중입니다."; draw()
        pool.execute {
            val cl = Api.call("list_classes"); val classes = jsonList(cl.optJSONArray("list")); val curC = cl.geul("cur")
            var users = jsonList(Api.call("list_users").optJSONArray("users")).filter { it.geul("role") != "master" }
            classes.firstOrNull { it.geul("id") == curC }?.let { cc -> val m = cc.optJSONArray("members"); val ms = (0 until (m?.length() ?: 0)).map { m!!.optString(it) }; users = users.filter { ms.contains(it.geul("id")) } }
            val out = mutableListOf<Triple<String, List<String>, Array<String>>>(); val T = mutableListOf<String>()
            users.forEachIndexed { n, u ->
                val uid = u.geul("id"); val un = u.geul("name")
                main.post { s12.gradeMal = "${n + 1} / ${users.size} — $un 훑는 중"; dasiGeuryeo(Hw.Grade) }
                val cs = jsonList(Api.call("list_courses", JSONObject().put("as", uid)).optJSONArray("courses")).filter { !it.optBoolean("gyobon") && it.geul("id") != "GYOBON" }
                if (cs.isEmpty()) { out.add(Triple("$un — 만든 코스 없음", listOf(), arrayOf(uid, un, "", ""))); T.add("$un — 만든 코스 없음") }
                for (x in cs) {
                    val gc = Api.call("get_course", JSONObject().put("id", x.geul("id")).put("as", uid))
                    val c = gc.optJSONObject("course")
                    if (!Api.ok(gc) || c == null) { out.add(Triple(un + " — " + x.geul("name") + " 읽지 못함", listOf(), arrayOf(uid, un, "", ""))); continue }
                    val k = Kos(c); val L = Wonchik.issueLines(Wonchik.scriptIssues(k, un))
                    val noText = k.spots.count { Wonchik.bin(it.s("text")) }
                    val head = un + " — " + k.name + " / 지점 ${k.spots.size}곳, 대본 ${k.spots.size - noText}곳 / 걸린 것 ${L.size}가지 / " + gradeStat(c.optJSONObject("final"))
                    out.add(Triple(head, L, arrayOf(uid, un, k.id, k.name))); T.add(head); T.addAll(L.map { " · $it" })
                }
            }
            main.post {
                s12.grade = out
                s12.gradeGeul = "수업 채점표 " + java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.KOREA).format(java.util.Date()) + "\n" + T.joinToString("\n")
                s12.gradeMal = "학생 ${users.size}명, 코스 ${out.count { it.third[2].isNotEmpty() }}개를 훑었습니다."
                malhagi(s12.gradeMal); dasiGeuryeo(Hw.Grade)
            }
        }
    }
    geul(s12.gradeMal)
    if (s12.gradeGeul.isNotEmpty()) {
        danchu("결과를 글자로 복사하기") { (getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("채점표", s12.gradeGeul)); malhagi("복사했습니다.") }
        danchu("결과 보내기·저장하기") { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, s12.gradeGeul), "보내기")) }
    }
    val G = s12.grade ?: return
    geul("결과 — 사람이 읽어야 아는 8·9·14·15번의 판단은 기계가 세지 않습니다", jemok = true)
    jangMok("gr", G) { r ->
        val v = geul(r.first + if (r.second.isEmpty()) "" else "\n걸린 것: " + r.second.joinToString(" / "))
        if (r.third[2].isNotEmpty()) danchu("이 코스 열어 보기 — " + r.third[3]) {
            Api.asWho = r.third[0]; s12.asWhoName = r.third[1]
            openKos(r.third[2]) { ok -> if (ok) { malhagi(r.third[1] + " 님의 " + r.third[3] + " 코스를 열었습니다."); tab = 2; draw() } }
        }
        v
    }
}
internal fun askText(k: Kos, a: Int, b: Int): String {
    val cps = (k.d.su("cps") ?: 330.0).toInt()
    val L = mutableListOf("현장 투어 현장영상해설 대본을 부탁드립니다.", "코스: " + k.name + " / 장소: " + k.s("place") + " / 분야: " + k.s("field").ifEmpty { "적지 않음" }, "집합 장소: " + k.s("meet"))
    if (k.s("note").isNotEmpty()) L.add("코스 소개: " + k.s("note"))
    L.addAll(listOf("", "지켜 주실 것", "1. 시각장애인을 위한 현장영상해설입니다. 방향은 시계 방향으로 짚어 주십시오.", "2. 눈으로 보는 것을 전제한 말(보인다·보시면 등)은 쓰지 마십시오. 놓여 있다·자리한다·이어진다처럼 쓰십시오.",
        "3. 전체를 먼저 말한 뒤 부분으로 좁혀 가십시오.", "4. 지점마다 머무는 시간이 있습니다. 그 시간 안에 읽을 길이로 써 주십시오(1분에 ${cps}자쯤).", "5. 답은 아래 형식으로만 주십시오.", " 1번", " 현장영상해설: (현장영상해설 글)", "",
        "지점 ${a}번부터 ${b}번까지입니다. 사진 파일 이름 앞의 번호가 지점 번호이고, 뒤에 몇 시 방향인지 적혀 있습니다."))
    for (i in (a - 1)..(b - 1)) {
        val s = k.spots.getOrNull(i) ?: continue; val st = s.n("stay") ?: 0.0
        var line = "${i + 1}번 / " + s.name + " / 머무는 시간 ${st.toInt()}분 / 넣을 수 있는 글자 수 ${(st * cps).roundToInt()}자"
        if (s.shots.length() > 0) line += " / 사진 " + (0 until s.shots.length()).joinToString(", ") { s.shots.optJSONObject(it)?.geul("dir") ?: "" }
        if (s.s("access").isNotEmpty()) line += "\n 이동 구간 안내: " + s.s("access")
        if (s.s("touch").isNotEmpty()) line += "\n 만져 볼 것: " + s.s("touch")
        val mm = jsonList(s.d.optJSONArray("memos")).map { it.geul("text") }.filter { it.isNotEmpty() }.joinToString(" ")
        if (mm.isNotEmpty()) line += "\n 현장 메모: $mm"
        val tl = Wonchik.ttLines(s); if (tl.isNotEmpty()) line += "\n 느낌·기억(테마투어): " + tl.joinToString(" / ")
        if (!Wonchik.bin(s.s("text"))) line += "\n 지금 써 둔 글: " + s.s("text")
        L.add(line)
    }
    return L.joinToString("\n")
}
internal fun TourActivity.claudeHwamyeon() {
    val k = kos ?: return kosEopseum()
    geul("클에게 대본 맡기기", jemok = true); allimJul()
    val n = max(1, k.spots.size)
    s12.claudeA = s12.claudeA.coerceIn(1, n); s12.claudeB = s12.claudeB.coerceIn(1, n)
    geul("이 코스의 지점은 1번부터 ${k.spots.size}번까지 모두 ${k.spots.size}곳입니다. 대본이 빈 곳은 ${k.spots.count { Wonchik.bin(it.s("text")) }}곳입니다. 고른 범위는 ${minOf(s12.claudeA, s12.claudeB)}번부터 ${max(s12.claudeA, s12.claudeB)}번까지입니다.")
    ipryeok("몇 번부터", s12.claudeA.toString(), susja = true) { s12.claudeA = (it.toIntOrNull() ?: 1).coerceIn(1, n) }
    ipryeok("몇 번까지", s12.claudeB.toString(), susja = true) { s12.claudeB = (it.toIntOrNull() ?: 1).coerceIn(1, n) }
    danchu("전체로") { s12.claudeA = 1; s12.claudeB = n; draw() }
    geul("나스로 클에게 맡기기 (내려받기 없이)", jemok = true)
    danchu("나스로 클에게 맡기기") {
        if (k.spots.isEmpty()) { malhagi("먼저 지점을 잡아 주십시오."); return@danchu }
        val lo = minOf(s12.claudeA, s12.claudeB); val hi = max(s12.claudeA, s12.claudeB)
        val shots = JSONArray()
        for (i in (lo - 1)..(hi - 1)) { val s = k.spots.getOrNull(i) ?: continue; for (j in 0 until s.shots.length()) s.shots.optJSONObject(j)?.let { p -> if (p.geul("f").isNotEmpty()) shots.put(JSONObject().put("n", i + 1).put("f", p.geul("f")).put("dir", p.geul("dir"))) } }
        save(true)
        api("ask_claude", JSONObject().put("c", k.id).put("a", lo).put("b", hi).put("ask", askText(k, lo, hi)).put("shots", shots)) { z ->
            s12.claudeMal = if (Api.ok(z)) "나스 요청함에 남겼습니다. 지점 ${lo}번부터 ${hi}번까지, 사진 ${z.ip("shots")}장. 이제 대화창의 클에게 「투어스튜디오 요청함을 봐 달라」고 한마디만 하시면 클이 나스에서 열어 초안을 써 둡니다. 초안이 오면 클 초안 왔는지 보기를 누르십시오." else Api.err(z)
            malhagi(s12.claudeMal); draw()
        }
    }
    danchu("클 초안 왔는지 보기") { claudeBogi(k, true) }
    s12.claudeChogan?.let { c ->
        danchu("클 초안 넣기", keun = true) { claudeNeoki(k, s12.claudeBuchyeo, true); pool.execute { Api.call("drop_claude", JSONObject().put("id", c.geul("id"))) }; s12.claudeChogan = null; draw() }
        danchu("이 요청 내리기") { pool.execute { Api.call("drop_claude", JSONObject().put("id", c.geul("id"))) }; s12.claudeChogan = null; malhagi("내렸습니다."); draw() }
    }
    if (s12.claudeMal.isNotEmpty()) geul(s12.claudeMal)
    geul("받은 대본 붙여넣기 — 「1번」 줄 다음에 그 지점 글", jemok = true)
    ipryeok("붙여넣기", s12.claudeBuchyeo, yeoreo = true) { s12.claudeBuchyeo = it }.minLines = 6
    danchu("넣기 (금지어가 있는 지점은 넣지 않음)") { claudeNeoki(k, s12.claudeBuchyeo, false); draw() }
    if (s12.claudeMal.isEmpty() && s12.claudeChogan == null) claudeBogi(k, false)
}
internal fun TourActivity.claudeBogi(k: Kos, tell: Boolean) {
    api("list_claude", JSONObject().put("c", k.id)) { z ->
        if (!Api.ok(z)) { if (tell) malhagi(Api.err(z)); return@api }
        val L = jsonList(z.optJSONArray("list"))
        if (L.isEmpty()) { if (tell) { malhagi("이 코스로 남긴 요청이 없습니다."); draw() }; return@api }
        val d = L.firstOrNull { it.geul("status") == "done" }
        if (d != null) { s12.claudeBuchyeo = d.geul("answer"); s12.claudeChogan = d; s12.claudeMal = "클 초안이 와 있습니다 (${d.ip("from")}번부터 ${d.ip("to")}번까지, " + ttae(d.geul("answeredAt")) + "). 붙여넣기 칸에 넣어 두었으니 클 초안 넣기를 누르십시오."; malhagi(s12.claudeMal) }
        else { val w = L[0]; s12.claudeMal = "요청이 나스 요청함에 있습니다 (${w.ip("from")}번부터 ${w.ip("to")}번까지, " + ttae(w.geul("at")) + "). 아직 클 초안이 오지 않았습니다. 대화창의 클에게 요청함을 봐 달라고 하십시오."; if (tell) malhagi(s12.claudeMal) }
        dasiGeuryeo(Hw.Claude)
    }
}
/** 웹 applyScript — 「1번」 줄 아래 글을 그 지점 대본으로. 금지어가 있는 지점은 넣지 않음 */
internal fun TourActivity.claudeNeoki(k: Kos, txt: String, fromAI: Boolean) {
    var cur = -1; val buf = sortedMapOf<Int, String>()
    for (ln in txt.split("\n")) {
        val m = Regex("^\\s*(\\d+)\\s*번").find(ln)
        if (m != null) { cur = (m.groupValues[1].toIntOrNull() ?: 0) - 1; buf.putIfAbsent(cur, ""); continue }
        val t = ln.replace(Regex("^\\s*(현장영상해설|설명)\\s*[:：]\\s*"), "").trim()
        if (cur >= 0 && t.isNotEmpty()) buf[cur] = (buf[cur]?.let { if (it.isEmpty()) "" else "$it " } ?: "") + t
    }
    var got = 0; val bad = mutableListOf<String>()
    for ((i, t) in buf) {
        if (i < 0 || i >= k.spots.size) continue
        val b0 = Wonchik.findBanned(t)
        if (b0.isNotEmpty()) { bad.add("${i + 1}번(" + b0.joinToString(", ") + ")"); continue }
        k.spots[i].d.put("text", t); if (fromAI) k.spots[i].d.put("draft", true); got++
    }
    if (bad.isNotEmpty()) malhagi("금지어가 있는 지점은 넣지 않았습니다. " + bad.joinToString(" / "))
    else { malhagi("${got}곳에 대본을 넣었습니다." + if (fromAI) " 초안으로 표시해 두었으니 살펴보시고 손보십시오." else ""); if (fromAI) s12.claudeBuchyeo = "" }
    bakkwim()
}

// ================= 내보내기·협회 최종 승인 =================
internal val NAEBO = listOf(Triple("guide", "대본집.txt", "현장영상해설사용 대본집"), Triple("braille", "점자대본.txt", "점자 대본"), Triple("notice", "참가자안내.txt", "참가자 안내문"), Triple("plan", "운영표.txt", "파견 운영표"), Triple("mapd", "지도자료.json", "지도 앱에 넣을 자료"))
internal fun TourActivity.naeboHwamyeon() {
    val k = kos ?: return kosEopseum()
    geul("내보내기·협회 승인", jemok = true); allimJul()
    geul("협회 최종 승인", jemok = true)
    geul(Wonchik.finalStatus(k.d.optJSONObject("final")))
    if (!k.isGyobon) {
        var msg = ""
        ipryeok("협회에 전할 말 (없어도 됩니다)", "", yeoreo = true) { msg = it }
        danchu(if (k.d.optJSONObject("final")?.geul("status") == "approved") "새 판으로 다시 승인 요청하기 — 협회 DB에 저장됩니다" else "협회에 최종 승인 요청하기 — 협회 DB에 저장됩니다", keun = true) {
            if (k.spots.isEmpty()) { malhagi("지점이 없는 코스는 요청할 수 없습니다."); return@danchu }
            val noT = k.spots.count { Wonchik.bin(it.s("text")) }
            if (noT == k.spots.size) { malhagi("대본이 한 곳도 없어 승인을 요청할 수 없습니다. 대본을 쓴 뒤 다시 요청해 주십시오."); return@danchu }
            val L = Wonchik.issueLines(Wonchik.scriptIssues(k, meName()))
            val warn = (if (noT > 0) "대본이 없는 지점이 ${noT}곳 있습니다. " else "") + (if (L.isEmpty()) "" else "살펴볼 것이 ${L.size}가지 남아 있습니다. ") + (if (noT > 0 || L.isNotEmpty()) "그래도 요청하시겠습니까? " else "")
            mutgi2(warn + "지금 이 코스의 시나리오 전체와 답사 사진·소리가 협회 DB에 원본으로 저장되고, 협회의 최종 승인을 기다리게 됩니다. 저장된 판은 고칠 수 없습니다. 보낼까요?", "보내기") {
                malhagi("협회 DB에 저장하는 중입니다."); save(true)
                api("submit_final", JSONObject().put("c", k.id).put("msg", msg), 300000) { r ->
                    val f = r.optJSONObject("final")
                    if (!Api.ok(r) || f == null) { malhagi(Api.err(r)); draw(); return@api }
                    k.d.put("final", f)
                    val m = "협회 DB에 전송 완료. ${f.ip("ver")}판, 사진 ${r.ip("photos")}장, 소리 ${r.ip("audio")}개가 함께 보관되었습니다. 협회의 최종 승인을 기다립니다."
                    malhagi(m); draw()
                    android.app.AlertDialog.Builder(this).setMessage(m).setPositiveButton("확인", null).show()
                }
            }
        }
    }
    geul("지금 상태", jemok = true)
    val s = k.spots
    geul("지점 ${s.size}곳 / 대본 쓴 곳 ${s.count { !Wonchik.bin(it.s("text")) }}곳 / 사진 없는 곳 ${s.count { it.shots.length() == 0 }}곳 / 오프닝 " + (if (Wonchik.bin(k.s("opening"))) "없음" else "있음") + " / 클로징 " + (if (Wonchik.bin(k.s("closing"))) "없음" else "있음") + " / 모두 " + fmtMin(k.courseMin()))
    danchu("대본 살펴보기·원칙 16개 점검표") { go(Hw.Jeomgeom) }
    geul("내보내기 — 만들어 이 폰에 받은 뒤 파일 앱·문자·카톡으로 보냅니다", jemok = true)
    s12.naeboFile?.let { f -> danchu("받은 파일 보내기·저장하기 — " + f.name, keun = true) { nanugi(f) } }
    NAEBO.forEach { m ->
        danchu(m.third + " 만들기") {
            val t = when (m.first) { "notice" -> Wonchik.noticeText(k); "plan" -> Wonchik.planText(k); "mapd" -> Wonchik.mapJson(k); else -> Wonchik.guideText(k) }
            malhagi(m.third + "을(를) 만드는 중입니다.")
            pool.execute {
                val j = Api.call("save_text", JSONObject().put("c", k.id).put("f", m.second).put("content", t).put("utf8", if (m.first == "mapd") 1 else 0))
                if (!Api.ok(j)) { main.post { malhagi(Api.err(j)); draw() }; return@execute }
                val u = apiUrl(mapOf("action" to "get_export", "c" to k.id, "f" to m.second, "dl" to "1", "tk" to Api.needTicket()))
                val f = naeryeogi(u, k.name + "_" + m.second)
                main.post {
                    k.d.put("exported", true); bakkwim()
                    if (f == null) malhagi("만들었으나 이 폰으로 받지 못했습니다. 다시 눌러 주십시오.")
                    else { s12.naeboFile = f; malhagi(m.third + "을(를) 만들어 이 폰에 받았습니다. 위의 받은 파일 보내기·저장하기를 누르면 파일 앱에 저장하거나 문자·카톡으로 보내실 수 있습니다.") }
                    draw()
                }
            }
        }
    }
    danchu("이 코스 통째로 내려받기 (코스기록·답사사진·소리·내보낸것)") {
        malhagi("코스를 통째로 묶는 중입니다. 사진이 많으면 조금 걸립니다.")
        pool.execute {
            val j = Api.call("make_backup", JSONObject().put("c", k.id), 300000)
            if (!Api.ok(j)) { main.post { malhagi(Api.err(j)); draw() }; return@execute }
            val fn = j.geul("file")
            val f = naeryeogi(apiUrl(mapOf("action" to "get_export", "c" to k.id, "f" to fn, "dl" to "1", "tk" to Api.needTicket())), fn)
            main.post { if (f == null) malhagi("묶었으나 이 폰으로 받지 못했습니다.") else { s12.naeboFile = f; malhagi("파일 ${j.ip("count")}개, " + j.geul("mb") + "MB 로 묶어 이 폰에 받았습니다. 위의 받은 파일 보내기·저장하기를 누르십시오.") }; draw() }
        }
    }
    geul("점자 대본과 대본집·안내문·운영표는 점자정보단말기에서 바로 읽히는 한글 글자판(CP949)으로 저장됩니다.", jakge = true)
    geul("참가자 안내문 소리 판", jemok = true)
    danchu("안내문 폰 음성으로 미리 듣기") { tts?.speak(Wonchik.noticeText(k), TextToSpeech.QUEUE_FLUSH, null, "notice") }
    danchu("듣기 멈춤") { tts?.stop(); deutgiMeomchum() }
    if (!k.isGyobon) {
        if (Nokeumgi.jung && Nokeumgi.galle == "notice") danchu("다 읽었습니다 (녹음 끝)", keun = true) { annaeNokeumKkeut(k) }
        else danchu("현장영상해설사 목소리로 안내문 읽어 녹음하기") {
            micHeorak { tts?.stop(); val e = Nokeumgi.start(this, "notice", "sOpen", ""); malhagi(e ?: "녹음을 시작했습니다. 안내문을 읽으신 뒤 다 읽었습니다를 누르십시오."); draw() }
        }
    }
    val na = k.d.optJSONObject("noticeAudio")
    if (na == null) geul("아직 녹음한 안내문이 없습니다.", jakge = true)
    else {
        val f = na.geul("f")
        if (f.isEmpty()) geul("녹음은 되었으나 아직 나스에 올리지 못했습니다(${na.ip("sec")}초).", jakge = true)
        else {
            geul("${na.ip("sec")}초, " + ttae(na.geul("at")), jakge = true)
            danchu(if (deunneun == f) "듣기 멈춤" else "녹음 들어보기") { deutgi(k.id, f) }
            danchu("소리 파일 내려받기") {
                pool.execute {
                    val ff = naeryeogi(Api.fileUrl("audio", k.id, f, Api.needTicket()), k.name + "_참가자안내문." + f.substringAfterLast('.', "mp3"))
                    main.post { if (ff == null) malhagi("받지 못했습니다.") else { s12.naeboFile = ff; malhagi("받았습니다. 위의 받은 파일 보내기·저장하기로 문자나 카톡에 붙여 보내십시오.") }; draw() }
                }
            }
        }
    }
    if (isMaster()) {
        geul("지도 앱에 올리기 (협회 승인을 받은 코스만)", jemok = true)
        geul("올라가는 것은 코스 이름·장소·집합 자리·걸리는 시간·들르는 자리·이동 구간 안내·만져 볼 것·비 올 때입니다. 지점별 대본과 답사 사진은 올라가지 않습니다. 1단계 지역을 골라 두십시오.", jakge = true)
        listOf(true, false).forEach { on ->
            danchu(if (on) "지도 앱에 올리기" else "지도 앱에서 내리기") {
                save(true)
                api(if (on) "publish_map" else "unpublish_map", JSONObject().put("c", k.id)) { r ->
                    s12.mapMal = if (!Api.ok(r)) Api.err(r) else if (on) "지도에 올렸습니다. 지금 지도에 올라간 코스는 모두 ${r.ip("count")}개입니다." else "지도에서 내렸습니다. 남은 코스는 ${r.ip("count")}개입니다."
                    if (Api.ok(r)) { k.d.put("onMap", on); bakkwim() }
                    malhagi(s12.mapMal); draw()
                }
            }
        }
        if (s12.mapMal.isNotEmpty()) geul(s12.mapMal)
    }
    geul("전체 시간", jemok = true)
    Wonchik.timeSum(k).forEach { geul(it) }
}
internal fun TourActivity.annaeNokeumKkeut(k: Kos) {
    malhagi("녹음을 끝냈습니다. 담은 소리를 mp3로 바꾸고 있습니다.")
    pool.execute {
        val r = Nokeumgi.stop()
        main.post { draw() }
        if (r == null) return@execute
        val (wav, sec, _) = r
        if (sec < 1) { wav.delete(); main.post { malhagi("담긴 소리가 없습니다.") }; return@execute }
        val mp3 = Mp3Gi.bakkugi(this, wav)
        val data = mp3 ?: wav.readBytes(); val ext = if (mp3 != null) "mp3" else "wav"; wav.delete()
        val j = Api.call("save_audio", JSONObject().put("c", k.id).put("s", "sOpen").put("kind", "notice").put("ext", ext).put("data", Base64.encodeToString(data, Base64.NO_WRAP)), timeoutMs = 120000)
        var f = ""; var q = ""
        if (Api.ok(j)) f = j.geul("file")
        else { q = Daegi.saeRef("audio"); Daegi.neoki(this, JSONObject().put("ref", q).put("jong", "audio").put("c", k.id).put("s", "sOpen").put("ext", ext).put("kind", "notice").put("at", nowIso()), data) }
        main.post {
            k.d.put("noticeAudio", JSONObject().put("f", f).put("qref", q).put("sec", sec).put("at", nowIso()))
            malhagi("${sec}초 녹음했습니다." + if (q.isEmpty()) " 녹음 들어보기와 소리 파일 내려받기가 생겼습니다." else " 아직 올리지 못해 폰에 남겨 두었습니다.")
            bakkwim(); draw()
        }
    }
}

// ================= 대본 살펴보기·원칙 16개 점검표 =================
internal fun TourActivity.wonchik16() {
    val k = kos ?: return kosEopseum()
    val iss = Wonchik.scriptIssues(k, meName()); val L = Wonchik.issueLines(iss); val pt = Wonchik.principleTable(k, iss); val sm = Wonchik.summary(pt)
    geul("대본 살펴보기·원칙 16개", jemok = true)
    geul("다음 할 일", jemok = true); geul(Wonchik.nextTodo(k, meName()))
    geul(if (L.isEmpty()) "기계가 잡아낼 것은 없습니다" else "살펴볼 것 ${L.size}가지", jemok = true)
    jangMok("jg", L) { geul(it) }
    geul("원칙 16개(통합본) 차례로 — 됨 ${sm.first}, 손볼 것 ${sm.second}, 사람이 판단 ${sm.third}", jemok = true)
    pt.forEach { geul(it.first) }
    geul("사람이 읽어야 아는 것", jemok = true)
    Wonchik.askLines.forEach { geul(it) }
}

// ================= 장소 평면도·답사 지도·걷는 시간·느낌 칸·방향 도우미 =================
internal fun TourActivity.pyeongmyeon(k: Kos) {
    geul("장소 평면도 (방향 잡기)", jemok = true)
    fun set(la: Double, lo: Double, nm: String) {
        k.d.put("pos", JSONObject().put("la", Math.round(la * 1e6) / 1e6).put("lo", Math.round(lo * 1e6) / 1e6)).put("posName", nm)
        bakkwim(); malhagi(Wonchik.pmText(k)); draw()
    }
    if (!k.isGyobon) {
        danchu("장소 자리 찾기 (장소 이름으로)") {
            var q = k.s("jisikQ").ifEmpty { k.s("place").ifEmpty { k.name } }
            q = q.replace(Regex("\\([^)]*\\)"), " ").replace("코스", " ").trim()
            val sp = Wonchik.spotPts(k)
            if (q.isEmpty() && sp.isEmpty()) { malhagi("장소 이름을 먼저 적어 주십시오."); return@danchu }
            malhagi("자리를 찾고 있습니다.")
            pool.execute {
                val t = httpGet("https://nominatim.openstreetmap.org/search?format=json&limit=1&accept-language=ko&q=" + URLEncoder.encode(q, "UTF-8"))
                val a = try { JSONArray(t ?: "[]") } catch (e: Exception) { JSONArray() }
                val f = a.optJSONObject(0)
                main.post {
                    val la = f?.su("lat"); val lo = f?.su("lon")
                    when {
                        la != null && lo != null -> set(la, lo, f.geul("display_name").split(",").first().trim().ifEmpty { q })
                        sp.isNotEmpty() -> set(sp[0].p.first, sp[0].p.second, sp[0].s.name)
                        else -> malhagi("\"$q\"의 자리를 찾지 못했습니다. 장소를 더 알려진 이름으로 적거나, 현장에서 지금 자리로 잡기를 눌러 주십시오.")
                    }
                }
            }
        }
        danchu("지금 자리로 잡기 (위치)") { malhagi("지금 자리를 재고 있습니다."); jari { l -> if (l == null) malhagi("위치를 받지 못했습니다. 위치 허락을 확인해 주십시오.") else set(l.latitude, l.longitude, "지금 자리 (오차 ${l.accuracy.toInt()}미터)") } }
    }
    geul(Wonchik.pmText(k))
    jido(Wonchik.spotPts(k).map { Triple(it.i + 1, it.s.name, it.p) }, Wonchik.pos(k))
}
internal fun seonOf(k: Kos): List<Pair<Pair<Double, Double>, Pair<Double, Double>>> {
    val out = mutableListOf<Pair<Pair<Double, Double>, Pair<Double, Double>>>()
    for (p in Wonchik.spotPts(k)) for (j in 0 until p.s.shots.length()) {
        val sh = p.s.shots.optJSONObject(j) ?: continue
        var hd = sh.su("head")
        if (hd == null) Regex("^\\d+").find(sh.geul("dir"))?.let { hd = ((it.value.toIntOrNull() ?: 0) % 12 * 30).toDouble() }
        val h = hd ?: continue
        val rr = 12.0 / 111320; val rad = Math.toRadians(h)
        out.add(p.p to (p.p.first + rr * Math.cos(rad) to p.p.second + rr * Math.sin(rad) / Math.cos(Math.toRadians(p.p.first))))
    }
    return out
}
internal fun TourActivity.surveyMap() {
    val k = kos ?: return kosEopseum()
    val sp = Wonchik.spotPts(k); val seon = seonOf(k)
    geul("답사 지도", jemok = true)
    geul("지점 ${k.spots.size}곳 가운데 위치 있는 ${sp.size}곳, 사진 방향 ${seon.size}개", jemok = true)
    if (sp.isEmpty() && Wonchik.pos(k) == null) geul("위치가 적힌 지점이 없어 지도를 그리지 못합니다. 현장에서 지점에 사진을 담으면 위치가 함께 적힙니다.")
    else jido(sp.map { Triple(it.i + 1, it.s.name, it.p) }, Wonchik.pos(k), seon)
    geul("지점마다", jemok = true)
    Wonchik.surveyText(k).forEach { geul(it) }
}
internal fun TourActivity.walkSection(k: Kos) {
    geul("걷는 시간 어림", jemok = true)
    if (!k.isGyobon) {
        kan("1분에 걷는 거리 (미터, 20~100, 서고 모이는 1분을 늘 더함)", (k.d.su("walk") ?: 40.0).toInt().toString(), susja = true) { k.d.put("walk", (it.toIntOrNull() ?: 40).coerceIn(20, 100)) }
        danchu("위치로 걷는 시간 다시 어림") { val m = Wonchik.calcWalk(k); bakkwim(); malhagi(m); draw() }
    }
    danchu("답사 지도 보기") { go(Hw.SurveyMap) }
    danchu("용어 뜻 찾기·우리 용어 사전") { s12.terms = null; go(Hw.Dict) }
}
internal fun TourActivity.ttSection(s: Spot, ro: Boolean) {
    geul("테마투어 — 느낌으로 짓는 현장영상해설 (비어 있는 칸은 두셔도 됩니다)", jemok = true)
    Wonchik.TTKAN.forEach { kk ->
        kan(kk.nm + " — " + kk.ex, s.d.optJSONObject("tt")?.geul(kk.id) ?: "") { v -> val tt = s.d.optJSONObject("tt") ?: JSONObject().also { s.d.put("tt", it) }; tt.put(kk.id, v) }.isEnabled = !ro
    }
}
internal val DIRKAN = listOf("text" to "대본", "to" to "어디까지", "access" to "이동 구간 안내", "touch" to "만져 볼 것", "safe" to "조심할 것", "gpos" to "현장영상해설사 위치")
internal fun TourActivity.dirSection(k: Kos, s: Spot, daebon: EditText) {
    geul("방향 도우미 (고른 칸 끝에 붙습니다)", jemok = true)
    val kanNm = DIRKAN.firstOrNull { it.first == s12.dirKan }?.second ?: "대본"
    danchu("붙일 칸 — $kanNm. 누르면 고릅니다") {
        android.app.AlertDialog.Builder(this).setTitle("붙일 칸 고르기").setItems(DIRKAN.map { it.second }.toTypedArray()) { _, i -> s12.dirKan = DIRKAN[i].first; malhagi(DIRKAN[i].second + " 칸에 붙입니다."); draw() }.setNegativeButton("그만두기", null).show()
    }
    jangMok("bh", Wonchik.DIRWORDS, per = 4) { w ->
        danchu("$w 붙이기") {
            if (s12.dirKan == "text") { val x = k.spotText(s); val y = x + (if (x.isEmpty() || x.endsWith(" ")) "" else " ") + w; daebon.setText(y); daebon.setSelection(y.length) }
            else { val x = s.s(s12.dirKan); s.d.put(s12.dirKan, x + (if (x.isEmpty() || x.endsWith(" ")) "" else " ") + w); bakkwim(); draw() }
            malhagi("${w}을(를) $kanNm 칸에 붙였습니다.")
        }
    }
}

// ================= 용어 뜻 찾기·우리 용어 사전 =================
internal fun TourActivity.dictHwamyeon() {
    geul("용어 뜻 찾기·우리 사전", jemok = true); allimJul()
    geul("용어 뜻 찾기 (국립국어원 한국어기초사전·표준국어대사전)", jemok = true)
    ipryeok("찾을 낱말", s12.dictQ) { s12.dictQ = it }
    danchu("찾기") {
        val w = s12.dictQ.trim()
        if (w.isEmpty()) { malhagi("찾으실 낱말을 넣어 주십시오."); return@danchu }
        s12.dictMal = "$w 찾는 중입니다."; draw()
        pool.execute {
            val j = bakkat("sajeon.php", mapOf("q" to w))
            main.post {
                s12.dictList = jsonList(j.optJSONArray("list"))
                s12.dictMal = if (!j.optBoolean("ok")) j.geul("error").ifEmpty { j.geul("err") } else if (s12.dictList.isEmpty()) j.geul("malsseum").ifEmpty { "두 사전에 그 말이 없습니다." } else "${s12.dictList.size}가지 뜻이 있습니다."
                malhagi(s12.dictMal + " " + s12.dictList.joinToString(" ") { it.geul("mal") + ". " + it.geul("ddeut") }); draw()
            }
        }
    }
    if (s12.dictMal.isNotEmpty()) geul(s12.dictMal)
    s12.dictList.forEachIndexed { i, x -> geul("${i + 1}. " + x.geul("mal") + (if (x.geul("pum").isEmpty()) "" else " (" + x.geul("pum") + ")") + " — " + x.geul("ddeut") + " [" + x.geul("sajeon") + "]") }
    s12.dictList.firstOrNull()?.let { f -> danchu("찾은 뜻 우리 사전에 담아 두기 (아래 칸에 올림)") { s12.tMal = f.geul("mal"); s12.tDdeut = f.geul("ddeut"); s12.tChul = f.geul("sajeon"); malhagi("아래 칸에 올렸습니다. 시각장애인이 소리로 듣고 바로 그려지도록 우리 말로 고쳐 담으십시오."); draw() } }
    geul("우리 용어 사전 — 모든 코스와 모든 현장영상해설사가 함께 씀", jemok = true)
    ipryeok("낱말", s12.tMal) { s12.tMal = it }
    ipryeok("뜻 (우리 말로)", s12.tDdeut, yeoreo = true) { s12.tDdeut = it }
    ipryeok("출처 (없어도 됩니다)", s12.tChul) { s12.tChul = it }
    danchu("우리 사전에 담기") {
        if (Wonchik.bin(s12.tMal) || Wonchik.bin(s12.tDdeut)) { malhagi("낱말과 뜻을 모두 넣어 주십시오."); return@danchu }
        val m = s12.tMal.trim()
        api("add_term", JSONObject().put("mal", m).put("ddeut", s12.tDdeut.trim()).put("chulcheo", s12.tChul)) { r ->
            if (Api.ok(r)) { malhagi(m + (if (r.optBoolean("gochim")) "의 뜻을 고쳐 담았습니다." else "을(를) 우리 사전에 담았습니다.") + " 모두 ${r.ip("count")}개입니다."); s12.tMal = ""; s12.tDdeut = ""; s12.tChul = ""; s12.terms = null }
            else malhagi(Api.err(r)); draw()
        }
    }
    val T = s12.terms
    if (T == null) { api("list_terms") { r -> s12.terms = if (Api.ok(r)) jsonList(r.optJSONArray("list")) else listOf(); dasiGeuryeo(Hw.Dict) }; return }
    geul("담아 둔 말 ${T.size}개", jemok = true)
    jangMok("tm", T) { t ->
        val v = geul(t.geul("mal") + " — " + t.geul("ddeut"))
        danchu("지우기 — " + t.geul("mal")) { mutgi2(t.geul("mal") + "을(를) 우리 사전에서 지울까요?", "지우기") { api("drop_term", JSONObject().put("mal", t.geul("mal"))) { malhagi(t.geul("mal") + "을(를) 지웠습니다."); s12.terms = null; draw() } } }
        v
    }
}

// ================= 장소 지식 들이기 =================
internal fun jisikList(j: JSONObject?) = jsonList(j?.optJSONArray("list"))
internal fun apGeori(r: JSONObject): String {
    val g = r.geul("geori"); if (g.isEmpty()) return ""
    return "앞 지점에서 " + (if (r.geul("bang").isEmpty()) "" else r.geul("bang") + "쪽으로 ") + g + "미터" + if (r.geul("sigye").isEmpty()) "" else ", " + r.geul("sigye")
}
internal fun TourActivity.jisikHwamyeon() {
    geul("장소 지식 들이기", jemok = true); allimJul()
    geul("장소 이름을 넣으면 국가유산청 자료에서 그 안에 무엇이 있는지, 어디에 있는지, 무슨 이야기가 있는지를 받아 와 코스 뼈대를 짜 드립니다. 테마투어를 고르면 시장·골목·마을길도 한국관광공사·카카오·오픈스트리트맵을 겹쳐 짭니다.", jakge = true)
    if (s12.jisikQ.isEmpty()) kos?.let { s12.jisikQ = it.s("jisikQ").ifEmpty { it.s("place") }; s12.tema = Wonchik.isTheme(it) }
    ipryeok("장소 이름 (예: 신륵사, 경복궁)", s12.jisikQ) { s12.jisikQ = it }
    danchu("갈래 — " + (if (s12.tema) "테마투어 — 시장·골목·마을길·주제 코스" else "국가유산·관광지 — 나라 자료로 짜기") + ". 누르면 바꿉니다") { s12.tema = !s12.tema; draw() }
    danchu("목표 시간 — " + mapOf("90" to "1시간 30분", "110" to "1시간 50분", "120" to "2시간")[s12.mok] + ". 누르면 고릅니다") {
        val mk = listOf("90" to "1시간 30분", "110" to "1시간 50분", "120" to "2시간")
        android.app.AlertDialog.Builder(this).setTitle("목표 시간").setItems(mk.map { it.second }.toTypedArray()) { _, i -> s12.mok = mk[i].first; draw() }.setNegativeButton("그만두기", null).show()
    }
    danchu("코스 뼈대 짜기", keun = true) { jisikRun(null) }
    geul(s12.jisikMal)
    if (s12.jisikJy.isNotEmpty()) { geul("같은 이름이 여러 곳에 있습니다. 어느 곳입니까", jemok = true); s12.jisikJy.forEach { x -> danchu(x) { jisikRun(x) } } }
    val j = s12.jisik ?: return
    val list = jisikList(j); val order = s12.order
    val hap = 10 + order.sumOf { list[it].ip("meomu") + list[it].ip("geot") }; val mk = (j.su("mok") ?: 110.0).toInt()
    geul((if (j.geul("goreun").isEmpty()) "" else j.geul("goreun") + " ") + j.geul("q") + " 코스 뼈대", jemok = true)
    geul("들르는 곳 ${order.size}곳, 모두 " + fmtMin(hap.toDouble()) + "입니다. 목표는 ${mk}분입니다. " + if (hap > mk) "${hap - mk}분 넘칩니다." else "목표 안에 들어옵니다.")
    danchu("모두 차례대로 넣기") { val ppoA = j.optJSONArray("ppo"); val ppo = (0 until (ppoA?.length() ?: 0)).map { ppoA!!.optString(it) }; order.clear(); order.addAll(list.indices.filter { !ppo.contains(list[it].geul("ireum")) }); malhagi("목록 차례대로 ${order.size}곳을 모두 넣었습니다."); draw() }
    danchu("모두 빼기 (다시 고르기)") { order.clear(); malhagi("모두 뺐습니다. 가실 차례대로 누르십시오."); draw() }
    geul(jisikJidoGeul(list, order), jakge = true)
    jido(order.mapIndexedNotNull { n, i -> val la = list[i].su("la"); val lo = list[i].su("lo"); if (la != null && lo != null && la != 0.0) Triple(n + 1, list[i].geul("ireum"), la to lo) else null })
    j.optJSONObject("gw")?.let { g -> if (g.optBoolean("found")) {
        geul("관람 안내와 무장애 정보", jemok = true)
        val ln = listOf("여는 시간" to "sigan", "쉬는 날" to "hyugwan", "물어보실 곳" to "munui", "주차" to "jucha").mapNotNull { if (g.geul(it.second).isEmpty()) null else it.first + " : " + g.geul(it.second) }
        if (ln.isNotEmpty()) geul(ln.joinToString(" / "))
        g.optJSONArray("uri")?.let { u -> if (u.length() > 0) geul("협회가 확인한 것 — " + (0 until u.length()).joinToString(" / ") { u.optString(it) }) }
        val m = g.optJSONArray("mujangae")
        if (m != null && m.length() > 0) geul("무장애 정보 ${m.length()}가지 — " + (0 until m.length()).joinToString(" / ") { m.optString(it) }) else geul("이곳은 무장애 정보가 아직 등록되어 있지 않습니다.", jakge = true)
    } }
    geul("들를 만한 곳 ${list.size}곳 — 가실 차례대로 누르십시오. 누르는 차례가 곧 코스 번호입니다", jemok = true)
    jangMok("js", list.withIndex().toList()) { (i, r) ->
        val od = order.indexOf(i).takeIf { it >= 0 }
        val nj = r.optJSONArray("sajins")?.length() ?: 0
        val b = danchu((if (od != null) "${od + 1}번째 · " else "") + r.geul("ireum") + " · " + r.geul("jong") + " · 머무는 시간 ${r.ip("meomu")}분" + (if (nj > 0) " · 사진 ${nj}장" else "") + (if (r.geul("sori").isEmpty()) "" else " · 음성 해설 있음") + " · " + if (od != null) "넣음, 누르면 뺍니다" else "누르면 ${order.size + 1}번째로 넣습니다") {
            val k = order.indexOf(i)
            if (k >= 0) { order.removeAt(k); malhagi(r.geul("ireum") + " 을(를) 뺐습니다. 뒤 번호가 당겨졌습니다.") } else { order.add(i); malhagi(r.geul("ireum") + " 을(를) ${order.size}번째로 넣었습니다.") }
            draw()
        }
        geul(listOf(r.geul("sidae"), r.geul("juso"), apGeori(r), if (r.geul("chulcheo").isEmpty()) "" else "출처 " + r.geul("chulcheo")).filter { it.isNotEmpty() }.joinToString(" · "), jakge = true)
        danchu("현장영상해설 밑글 보기 — " + r.geul("ireum")) {
            android.app.AlertDialog.Builder(this).setTitle(r.geul("ireum")).setMessage(r.geul("geul").ifEmpty { "(밑글이 없습니다)" } + if (r.geul("gunggeul").isEmpty()) "" else "\n\n[궁궐·종묘 자료] " + r.geul("gunggeul")).setPositiveButton("닫기", null).show()
        }
        if (r.geul("sori").isNotEmpty()) danchu("국가유산청 음성 해설 들어 보기 (국가유산청 제공, 참고용)") { deutgiUrl(r.geul("sori")) }
        b
    }
    j.optJSONArray("deo")?.let { d -> if (d.length() > 0) {
        geul("이 안에서 더 볼 것 ${d.length()}곳 — 위치가 안 나와 지점으로는 못 넣고 밑글로만 담깁니다", jemok = true)
        jsonList(d).forEach { x -> danchu(x.geul("ireum")) { android.app.AlertDialog.Builder(this).setTitle(x.geul("ireum")).setMessage(x.geul("geul")).setPositiveButton("닫기", null).show() } }
    } }
    danchu("이 뼈대로 코스 만들기", keun = true) {
        if (order.isEmpty()) { malhagi("넣은 곳이 하나도 없습니다. 가실 차례대로 목록의 이름을 눌러 넣어 주십시오."); return@danchu }
        val k = kos
        if (k != null && !k.isGyobon) {
            if (k.spots.isEmpty()) jisikMandeulgi(true)
            else android.app.AlertDialog.Builder(this).setMessage("지금 작업 중인 코스에 지점이 이미 있습니다. 어떻게 할까요?")
                .setPositiveButton("지금 코스에 이 뼈대로 바꿔 넣기") { _, _ -> jisikMandeulgi(true) }
                .setNeutralButton("새 코스를 따로 만들기") { _, _ -> jisikMandeulgi(false) }
                .setNegativeButton("그만두기", null).show()
        } else jisikMandeulgi(false)
    }
    danchu("우리가 확인한 것 채워 넣기") { s12.uriQ = j.geul("q"); s12.uriOpen = false; go(Hw.Urijaryo) }
}
internal fun TourActivity.deutgiUrl(u: String) {
    deutgiMeomchum(); tts?.stop()
    try {
        val p = MediaPlayer(); p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        p.setDataSource(u); p.setOnPreparedListener { it.start() }; p.setOnCompletionListener { deutgiMeomchum() }
        p.setOnErrorListener { _, _, _ -> deutgiMeomchum(); malhagi("소리를 틀지 못했습니다."); true }
        player = p; deunneun = u; p.prepareAsync()
    } catch (e: Exception) { malhagi("소리를 틀지 못했습니다.") }
}
internal fun jjul(s0: String): String {
    val s = s0.replace(Regex("\\([^)]*\\)"), " ").replace("코스", " ").replace(Regex("[/,·]"), " ")
    val w = s.split(" ").filter { x -> x.isNotEmpty() && !Regex("[0-9]").containsMatchIn(x) && !(x.length <= 4 && Regex("(도|시|군|구|동|읍|면|리)$").containsMatchIn(x)) }
    return w.lastOrNull() ?: s
}
internal fun TourActivity.jisikRun(jy0: String?) {
    val q0 = s12.jisikQ.trim()
    if (q0.isEmpty()) { malhagi("장소 이름을 넣어 주십시오."); return }
    s12.jisikMal = if (s12.tema) "한국관광공사·카카오·오픈스트리트맵 자료를 겹쳐 받아 오고 있습니다. 처음에는 조금 걸립니다." else "국가유산청 자료를 받아 오고 있습니다. 처음에는 조금 걸립니다."
    malhagi(s12.jisikMal); s12.jisik = null; s12.jisikJy = listOf(); draw()
    val p = mutableMapOf("a" to (if (s12.tema) "tema" else "chatgi"), "q" to q0, "mok" to s12.mok); if (jy0 != null) p["jy"] = jy0
    pool.execute {
        val j = bakkat("jisik.php", p)
        main.post {
            if (!j.optBoolean("ok")) {
                val q2 = jjul(q0)
                if (jy0 == null && !s12.dolnaem && q2.isNotEmpty() && q2 != q0) { s12.dolnaem = true; s12.jisikQ = q2; malhagi("\"$q0\"(으)로는 찾지 못해 \"$q2\"(으)로 다시 찾습니다."); jisikRun(null); return@post }
                s12.jisikMal = j.geul("err").ifEmpty { "받아 오지 못했습니다." }; malhagi(s12.jisikMal); draw(); return@post
            }
            s12.dolnaem = false
            val jyA = j.optJSONArray("jiyeoks"); val jys = (0 until (jyA?.length() ?: 0)).map { jyA!!.optString(it) }
            if (jy0 == null && jys.size > 1) { s12.jisikJy = jys; s12.jisikMal = "같은 이름이 여러 곳에 있습니다. 어느 곳인지 골라 주십시오."; malhagi(s12.jisikMal); draw(); return@post }
            s12.jisik = j; s12.order.clear(); jang.remove("js")
            s12.jisikMal = "들를 만한 곳 ${jisikList(j).size}곳을 찾았습니다. 가실 차례대로 하나씩 누르십시오. 한꺼번에 넣으시려면 모두 차례대로 넣기를 누르십시오."
            malhagi(s12.jisikMal); draw()
        }
    }
}
internal fun jisikJidoGeul(list: List<JSONObject>, order: List<Int>): String {
    val pts = list.indices.mapNotNull { i -> val la = list[i].su("la"); val lo = list[i].su("lo"); if (la != null && lo != null && la != 0.0) i to (la to lo) else null }
    if (pts.isEmpty()) return "위치가 있는 곳이 없어 지도를 그리지 못합니다."
    val baseI = order.firstOrNull { i -> pts.any { it.first == i } } ?: pts[0].first
    val base = pts.first { it.first == baseI }.second
    val lines = pts.map { p -> val od = order.indexOf(p.first); (if (od >= 0) "${od + 1}번째 " else "") + list[p.first].geul("ireum") + " — " + if (p.first == baseI) "기준 자리" else Wonchik.clock(Wonchik.brg(base, p.second)) + " 방향 ${Wonchik.meter(base, p.second)}미터" }
    val warn = mutableListOf<String>()
    val op = order.mapNotNull { i -> pts.firstOrNull { it.first == i }?.let { i to it.second } }
    for (k in 2 until op.size) {
        val a = op[k - 2].second; val b = op[k - 1].second; val c = op[k].second
        val t = abs(((Wonchik.brg(b, c) - Wonchik.brg(a, b)) + 540) % 360 - 180)
        if (t > 150) warn.add("${k}번째 " + list[op[k - 1].first].geul("ireum") + "에서 ${k + 1}번째 " + list[op[k].first].geul("ireum") + "(으)로 가는 길은 왔던 방향으로 되돌아갑니다.")
    }
    return (if (order.isEmpty()) "아직 고르지 않으셨습니다. " else "첫 번째 지점에서 본 자리입니다. ") + "북쪽을 12시로 봅니다. " + lines.joinToString(" / ") + (if (warn.isEmpty()) "" else " ★ " + warn.joinToString(" ")) + " (지도: 오픈스트리트맵 기여자, ODbL)"
}
internal fun TourActivity.jisikMandeulgi(into: Boolean) {
    val j = s12.jisik ?: return
    val list = jisikList(j); val golf = s12.order.map { list[it] }
    val irum = (if (j.geul("goreun").isEmpty()) "" else j.geul("goreun") + " ") + j.geul("q") + " 코스"
    pool.execute {
        val c: JSONObject
        val k0 = kos
        if (into && k0 != null) c = JSONObject(k0.json().toString())
        else {
            val a = Api.call("new_course", JSONObject().put("name", irum))
            val cc = a.optJSONObject("course")
            if (!Api.ok(a) || cc == null) { main.post { malhagi(Api.err(a)); draw() }; return@execute }
            c = cc
        }
        if (Wonchik.bin(c.geul("name"))) c.put("name", irum)
        if (Wonchik.bin(c.geul("place"))) c.put("place", golf[0].geul("juso").ifEmpty { j.geul("goreun") + " " + j.geul("q") })
        c.put("jisikQ", j.geul("q")).put("jisikJy", j.geul("goreun"))
        if (!c.has("pos")) { val la = golf[0].su("la"); val lo = golf[0].su("lo"); if (la != null && lo != null && la != 0.0) c.put("pos", JSONObject().put("la", la).put("lo", lo)).put("posName", golf[0].geul("ireum")) }
        val isTema = (j.su("tema") ?: 0.0) > 0
        c.put("note", if (isTema) "나라 자료가 없는 곳이라 한국관광공사·카카오·오픈스트리트맵을 겹쳐 짠 테마투어 뼈대입니다. 느낌 칸의 짐작은 반드시 현장에서 확인해 고쳐 쓰고, 계단과 방향과 만져 볼 것도 현장에서 채워 주십시오." else "나라가 열어 둔 자료로 짠 뼈대입니다. 계단과 방향과 만져 볼 것은 현장에서 채워 주십시오.")
        if (isTema) c.put("field", "테마투어 분야")
        val acc = StringBuilder()
        j.optJSONObject("gw")?.let { g -> if (g.optBoolean("found")) {
            for ((nm, key) in listOf("여는 시간" to "sigan", "쉬는 날" to "hyugwan", "물어보실 곳" to "munui", "주차" to "jucha")) if (g.geul(key).isNotEmpty()) acc.append(nm + " : " + g.geul(key) + "\n")
            g.optJSONArray("uri")?.let { u -> if (u.length() > 0) { acc.append("\n[협회가 확인한 것]\n"); for (i in 0 until u.length()) acc.append("· " + u.optString(i) + "\n") } }
            g.optJSONArray("mujangae")?.let { m -> if (m.length() > 0) { acc.append("\n[무장애 정보 — 한국관광공사]\n"); for (i in 0 until m.length()) acc.append("· " + m.optString(i) + "\n") } }
        } }
        acc.append("\n[실측 확인] 오시는 길, 대중교통, 매표소까지의 동선, 계단 칸 수는 현장에서 채워 주십시오.\n")
        c.put("access", acc.toString())
        val his = StringBuilder()
        golf.forEachIndexed { k, r ->
            his.append("${k + 1}. " + r.geul("ireum") + " (" + r.geul("jong") + (if (r.geul("sidae").isEmpty()) "" else " · " + r.geul("sidae")) + ")\n")
            his.append(r.geul("geul").ifEmpty { "(밑글이 없습니다)" } + "\n")
            if (r.geul("gunggeul").isNotEmpty()) his.append("[궁궐·종묘 자료] " + r.geul("gunggeul") + "\n")
            val sj = jsonList(r.optJSONArray("sajins")); if (sj.isNotEmpty()) his.append("[사진 ${sj.size}장] " + sj.joinToString(" / ") { it.geul("seol").ifEmpty { "설명 없음" } } + "\n")
            if (r.geul("sori").isNotEmpty()) his.append("[국가유산청 음성 해설] " + r.geul("sori") + "\n")
            his.append("\n")
        }
        val deo = jsonList(j.optJSONArray("deo"))
        if (deo.isNotEmpty()) { his.append("\n[이 안에서 더 볼 것 — 위치가 안 나와 지점으로 넣지 못한 곳]\n\n"); deo.forEach { his.append("· " + it.geul("ireum") + "\n" + it.geul("geul") + "\n\n") } }
        c.put("history", (if (isTema) "[테마투어 — 한국관광공사·카카오·오픈스트리트맵 자료로 짠 뼈대입니다. 자료가 없는 곳이니 현장의 소리·냄새·발밑·손·볕과 바람·기척과 현장영상해설사의 기억으로 짓고, 아래는 참고로만 쓰십시오.]\n\n" else "[국가유산청 자료 — 참고 밑글입니다. 그대로 읽지 마시고 우리 원칙으로 다시 쓰십시오.]\n\n") + his)
        c.put("refs", "국가유산청 국가유산포털(www.khs.go.kr) 국가유산 정보·해설문·궁궐 정보, 공공누리 제1유형(출처 표시) / 한국관광공사 관광정보(api.visitkorea.or.kr) 국문 관광정보·무장애 여행 정보, 공공누리 제1유형(출처 표시) / 위치: 오픈스트리트맵 기여자(ODbL) (받은 날 " + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.KOREA).format(java.util.Date()) + ")")
        val t0 = java.lang.Long.toString(System.currentTimeMillis() / 1000, 36)
        val sp = JSONArray()
        golf.forEachIndexed { k, r ->
            val o = JSONObject().put("id", "js$t0$k").put("name", r.geul("ireum")).put("stay", (r.su("meomu") ?: 10.0).toInt()).put("move", (r.su("geot") ?: 0.0).toInt())
                .put("access", apGeori(r).let { if (it.isEmpty()) "" else "$it. " } + "[실측 확인] 계단·경사·바닥 상태와 걸음 수를 현장에서 적으십시오.")
                .put("touch", "").put("safe", "").put("gpos", "").put("text", "").put("lat", r.su("la") ?: 0.0).put("lon", r.su("lo") ?: 0.0).put("photos", JSONArray()).put("shots", JSONArray())
            r.optJSONObject("tt")?.let { if (it.length() > 0) o.put("tt", it) }
            sp.put(o)
        }
        c.put("spots", sp); c.remove("rangeV")
        val b = Api.call("save_course", JSONObject().put("course", c).put("base", c.geul("updated")).put("force", 1))
        main.post {
            if (!Api.ok(b)) { malhagi(Api.err(b)); draw(); return@post }
            loadCourses()
            openKos(c.geul("id")) { ok -> if (ok) { malhagi((if (into) "지금 코스에 뼈대를 채워 넣었습니다." else c.geul("name") + " 이(가) 만들어졌습니다.") + " 들르는 곳 ${golf.size}곳, 고르신 차례대로 번호를 붙였습니다. 지점 탭에서 보십시오."); tab = 2; draw() } }
        }
    }
}

// ================= 우리가 확인한 것 채워 넣기 =================
internal val URIKAN = listOf("sigan" to "여는 시간", "hyugwan" to "쉬는 날", "munui" to "물어보실 곳", "jucha" to "주차", "gil" to "오시는 길", "gyedan" to "계단과 턱", "jeomja" to "점자블록", "eumseong" to "음성 안내", "manjyeo" to "만져 볼 것", "josim" to "조심할 것", "etc" to "그 밖에")
internal fun TourActivity.urijaryoHwamyeon() {
    geul("우리가 확인한 것", jemok = true); allimJul()
    geul("나라 자료에 비어 있는 칸을 협회가 채워 쌓는 자리입니다. 여기 채운 것은 나라 자료보다 앞서 나오고, 투어스튜디오와 더뷰 두 곳에서 함께 쓰입니다. 협회 현장영상해설사 명단에 있는 전화번호라야 저장됩니다.", jakge = true)
    if (s12.uriTel.isEmpty()) s12.uriTel = sp.getString("bfbPhone", "") ?: ""
    ipryeok("현장영상해설사 전화번호", s12.uriTel, susja = true) { s12.uriTel = it }
    ipryeok("장소 이름 (투어스튜디오·더뷰에서 쓰는 이름과 똑같이)", s12.uriQ) { s12.uriQ = it }
    danchu("열기") {
        val t = s12.uriTel.filter { it.isDigit() }
        if (t.length < 9) { s12.uriMal = "전화번호를 다시 넣어 주십시오."; malhagi(s12.uriMal); draw(); return@danchu }
        if (Wonchik.bin(s12.uriQ)) { s12.uriMal = "장소 이름을 넣어 주십시오."; malhagi(s12.uriMal); draw(); return@danchu }
        sp.edit().putString("bfbPhone", t).apply()
        pool.execute {
            val j = bakkat("urijaryo.php", mapOf("a" to "get", "jangso" to s12.uriQ.trim()))
            main.post {
                s12.uriV.clear()
                val jr = j.optJSONObject("jaryo")
                if (j.optBoolean("itda") && jr != null) { URIKAN.forEach { s12.uriV[it.first] = jr.geul(it.first) }; s12.uriMal = "이미 채워 둔 것이 있어 불러왔습니다. 적은 이는 " + jr.geul("jeokeun") + ", " + jr.geul("ttae") + "입니다." }
                else s12.uriMal = "아직 채운 것이 없습니다. 아는 것부터 적어 주십시오."
                s12.uriOpen = true; malhagi(s12.uriMal); draw()
            }
        }
    }
    if (s12.uriMal.isNotEmpty()) geul(s12.uriMal)
    if (!s12.uriOpen) return
    geul(s12.uriQ + " — 아는 것만 채우십시오. 빈칸은 나라 자료가 그대로 나옵니다", jemok = true)
    URIKAN.forEach { k -> ipryeok(k.second, s12.uriV[k.first] ?: "", yeoreo = true) { s12.uriV[k.first] = it } }
    danchu("이대로 저장하기", keun = true) {
        val f = HashMap<String, String>(); f["tel"] = s12.uriTel.filter { it.isDigit() }; f["jangso"] = s12.uriQ.trim()
        URIKAN.forEach { f[it.first] = (s12.uriV[it.first] ?: "").trim() }
        pool.execute {
            val j = jsonOr(httpPostForm("https://lvd.ada.or.kr/lvdts/urijaryo.php?a=set", f))
            main.post { s12.uriMal = if (j.optBoolean("ok")) s12.uriQ + " 자료를 저장했습니다. 이제 투어스튜디오와 더뷰에서 이 내용이 먼저 나옵니다." else j.geul("err").ifEmpty { "저장하지 못했습니다." }; malhagi(s12.uriMal); draw() }
        }
    }
    danchu("다른 장소") { s12.uriOpen = false; s12.uriQ = ""; s12.uriMal = "다른 장소 이름을 넣어 주십시오."; draw() }
}

// ================= 승인본 현장영상해설 화면 =================
internal fun hjSpots(c: JSONObject?) = jsonList(c?.optJSONArray("spots"))
internal fun spotLL(s: JSONObject): Pair<Double, Double>? { val la = s.su("lat") ?: return null; val lo = s.su("lng") ?: s.su("lon") ?: return null; return la to lo }
internal val hjRun = HashMap<TourActivity, Runnable>()
internal fun TourActivity.hyeonjangHwamyeon(h: Hw.Hyeonjang) {
    val entry = try { JSONObject(h.entry) } catch (e: Exception) { JSONObject() }
    val owner = entry.geul("owner"); val cid = entry.geul("cid"); val ver = entry.geul("ver")
    val key = "$owner|$cid|$ver"
    if (s12.hjKey != key) {
        hjDatgi(); s12.hjKey = key; s12.hjC = null; s12.hjIdx = -1; s12.hjMal = "여는 중입니다."; s12.hjEntry = entry
        val cache = File(File(filesDir, "hyeonjang").apply { mkdirs() }, owner + "_" + cid + "_" + ver + ".json")
        pool.execute {
            val tk = Api.needTicket()
            val r = Api.call("final_get", JSONObject().put("owner", owner).put("c", cid).put("v", entry.ip("ver")))
            val cc = r.optJSONObject("course")
            main.post {
                s12.hjTk = tk
                if (Api.ok(r) && cc != null) { s12.hjC = cc; try { cache.writeText(cc.toString()) } catch (e: Exception) { }; malhagi(cc.geul("name") + "을(를) 열었습니다. 지점 ${hjSpots(cc).size}곳입니다. 앞머리부터 보십시오.") }
                else if (cache.exists()) { s12.hjC = try { JSONObject(cache.readText()) } catch (e: Exception) { null }; malhagi("통신이 안 되어 이 폰에 담아 둔 것으로 엽니다.") }
                else { s12.hjMal = Api.err(r); malhagi(s12.hjMal) }
                dasiGeuryeo(h)
            }
        }
    }
    geul("현장영상해설 화면", jemok = true); allimJul()
    val c = s12.hjC ?: run { geul(s12.hjMal); return }
    val spots = hjSpots(c)
    geul(c.geul("name"), jemok = true)
    geul(c.geul("place") + " / 집합 " + c.geul("meet") + " / 승인번호 " + entry.geul("no") + " / 지점 ${spots.size}곳", jakge = true)
    geul("현장에서 쓰는 단추", jemok = true)
    danchu(if (s12.hjKkeojim) "화면 안 꺼지게 하기 — 켜짐. 누르면 끕니다" else "화면 안 꺼지게 하기 — 꺼짐. 누르면 켭니다") {
        s12.hjKkeojim = !s12.hjKkeojim
        if (s12.hjKkeojim) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        malhagi(if (s12.hjKkeojim) "현장영상해설하는 동안 화면이 꺼지지 않습니다." else "화면 켜 두기를 껐습니다."); draw()
    }
    danchu(if (s12.hjIlkgi) "대본 읽어 주기 — 켜짐. 누르면 끕니다" else "대본 읽어 주기 (지점을 옮길 때마다) — 꺼짐. 누르면 켭니다") {
        s12.hjIlkgi = !s12.hjIlkgi
        if (s12.hjIlkgi) hjIlgeojugi(c) else tts?.stop()
        malhagi(if (s12.hjIlkgi) "대본을 읽어 드립니다. 지점을 옮길 때마다 읽습니다." else "읽어 주기를 껐습니다."); draw()
    }
    danchu(if (s12.hjTtara) "지금 자리로 따라가기 — 켜짐. 누르면 끕니다" else "지금 자리로 따라가기 (다음 지점까지 거리·방향, 5미터 안에 들면 저절로 넘어감) — 꺼짐. 누르면 켭니다") { hjTtaraOn(h, !s12.hjTtara) }
    if (s12.hjTtaraJul.isNotEmpty()) geul(s12.hjTtaraJul)
    danchu(if (sp.getBoolean("offOn", true)) "코스 벗어남 알림 (따라가기를 켰을 때) — 켜짐. 누르면 끕니다" else "코스 벗어남 알림 (따라가기를 켰을 때) — 꺼짐. 누르면 켭니다") { sp.edit().putBoolean("offOn", !sp.getBoolean("offOn", true)).apply(); draw() }
    hjOmgim(h, spots)
    val idx = s12.hjIdx
    if (idx < 0) {
        geul("앞머리 — 오프닝", jemok = true)
        for ((nm, k) in listOf("1번 첫 만남 인사말" to "first", "오프닝" to "opening", "3번 장소 역사" to "history", "3번 위치와 찾아오는 길" to "access", "3번 규모와 형태" to "scale", "3번 전체 포인트" to "points", "5번 촉각지도 안내" to "tactile", "6번 출발 전 안전 고지" to "safety"))
            if (!Wonchik.bin(c.geul(k))) { geul(nm, jemok = true); geul(c.geul(k)).apply { textSize = 20f; setTextIsSelectable(true) } }
    } else if (idx < spots.size) {
        val s = spots[idx]
        val t0 = geul("${idx + 1}지점 " + s.geul("name") + " (${idx + 1}/${spots.size})", jemok = true); t0.tag = "meori"
        val fr = if (idx == 0) { val pt = c.optJSONObject("openSpot")?.geul("to")?.trim() ?: ""; "오프닝 지점의 현장영상해설을 마치고 출발하는 자리" + if (pt.isEmpty()) "" else " — $pt" }
                 else { val p = spots[idx - 1]; val pt = p.geul("to").trim(); "${idx}지점 " + p.geul("name") + "의 현장영상해설을 마치고 출발하는 자리" + if (pt.isEmpty()) "" else " — $pt" }
        geul("어디서부터: $fr / 어디까지: " + s.geul("to").trim().ifEmpty { "적지 않음" } + " / 머무는 시간 ${s.ip("stay")}분" + (if (s.ip("move") > 0) " / 다음 지점까지 걷기 ${s.ip("move")}분" else "") + if (s.geul("gpos").isEmpty()) "" else " / 현장영상해설사 위치: " + s.geul("gpos"), jakge = true)
        geul(if (Wonchik.bin(s.geul("text"))) "(이 지점의 대본이 비어 있습니다.)" else s.geul("text")).apply { textSize = 20f; setTextIsSelectable(true) }
        danchu(if (s12.hjTimer) "시간 재기 멈춤 — 남은 시간 ${s12.hjLeft / 60}분 ${s12.hjLeft % 60}초" else "머무는 시간 재기 시작") { if (s12.hjTimer) hjTimerMeom(true) else hjTimerSijak(h, s) }
        for ((nm, k) in listOf("이동 구간 안내" to "access", "만져 볼 것" to "touch", "조심할 것" to "safe", "현장영상해설사 위치" to "gpos")) if (!Wonchik.bin(s.geul(k))) geul("$nm: " + s.geul(k))
        s.optJSONObject("tt")?.let { tt -> Wonchik.TTKAN.forEach { kk -> if (tt.geul(kk.id).isNotEmpty()) geul(kk.nm + ": " + tt.geul(kk.id)) } }
        val shots = jsonList(s.optJSONArray("shots")).filter { it.geul("f").isNotEmpty() }
        if (shots.isNotEmpty()) {
            geul("답사 사진 ${shots.size}장", jemok = true)
            shots.forEachIndexed { i, p ->
                val iv = ImageView(this).apply { adjustViewBounds = true; contentDescription = "${i + 1}번째 사진, " + p.geul("dir") }
                body.addView(iv, lp(6))
                pool.execute {
                    try {
                        val u = apiUrl(mapOf("action" to "final_photo", "o" to owner, "c" to cid, "v" to ver, "f" to p.geul("f"), "tk" to s12.hjTk.ifEmpty { Api.needTicket() }))
                        val bm = URL(u).openStream().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 }) }
                        main.post { iv.setImageBitmap(bm) }
                    } catch (e: Exception) { }
                }
            }
        }
        jsonList(s.optJSONArray("memos")).filter { it.geul("f").isNotEmpty() }.forEachIndexed { i, m ->
            danchu("${i + 1}번 현장 소리 듣기 — ${m.ip("sec")}초") { deutgiUrl(apiUrl(mapOf("action" to "final_audio", "o" to owner, "c" to cid, "v" to ver, "f" to m.geul("f"), "tk" to s12.hjTk))) }
        }
        cheotJul(t0)
    } else {
        geul("클로징", jemok = true)
        geul(if (Wonchik.bin(c.geul("closing"))) "(클로징이 비어 있습니다.)" else c.geul("closing")).apply { textSize = 20f }
    }
    hjOmgim(h, spots)
}
internal fun TourActivity.hjOmgim(h: Hw.Hyeonjang, spots: List<JSONObject>) {
    val idx = s12.hjIdx
    if (idx >= 0) danchu(if (idx == 0) "앞머리로" else "앞 지점") { hjBoyeo(h, idx - 1) }
    if (idx < spots.size) danchu(if (idx + 1 < spots.size) "다음 지점 — ${idx + 2}지점 " + spots[idx + 1].geul("name") else if (idx + 1 == spots.size) "클로징으로" else "1지점으로", keun = true) { hjBoyeo(h, idx + 1) }
}
internal fun TourActivity.hjBoyeo(h: Hw.Hyeonjang, i: Int) {
    val c = s12.hjC ?: return; val spots = hjSpots(c)
    if (i < -1 || i > spots.size) return
    s12.hjIdx = i; hjTimerMeom(false)
    malhagi(if (i >= 0 && i < spots.size) "${i + 1}지점 " + spots[i].geul("name") + "입니다." else if (i == spots.size) "클로징입니다." else "앞머리입니다.")
    if (s12.hjIlkgi) hjIlgeojugi(c)
    dasiGeuryeo(h); scroll.scrollTo(0, 0)
}
internal fun TourActivity.hjIlgeojugi(c: JSONObject) {
    val spots = hjSpots(c); val i = s12.hjIdx
    val t = if (i >= 0 && i < spots.size) spots[i].geul("text") else if (i == spots.size) c.geul("closing") else c.geul("opening")
    if (ttsOk) tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "hj")
}
internal fun TourActivity.hjTimerSijak(h: Hw.Hyeonjang, s: JSONObject) {
    s12.hjLeft = max(1, ((s.su("stay") ?: 1.0) * 60).roundToInt()); s12.hjTimer = true
    malhagi("시간을 재기 시작합니다. ${s12.hjLeft / 60}분입니다.")
    val r = object : Runnable { override fun run() {
        if (!s12.hjTimer) return
        s12.hjLeft--
        if (s12.hjLeft == 60) malhagi("1분 남았습니다.")
        if (s12.hjLeft <= 0) { hjTimerMeom(false); deureul(); ppi(); malhagi("머무는 시간이 다 되었습니다. 다음 지점으로 옮기십시오."); dasiGeuryeo(h); return }
        if (s12.hjLeft % 15 == 0) dasiGeuryeo(h)
        main.postDelayed(this, 1000)
    } }
    hjRun[this]?.let { main.removeCallbacks(it) }; hjRun[this] = r; main.postDelayed(r, 1000); draw()
}
internal fun TourActivity.hjTimerMeom(tell: Boolean) { s12.hjTimer = false; hjRun[this]?.let { main.removeCallbacks(it) }; if (tell) { malhagi("시간 재기를 멈췄습니다."); draw() } }
@SuppressLint("MissingPermission")
internal fun TourActivity.hjTtaraOn(h: Hw.Hyeonjang, on: Boolean) {
    val lm = getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
    s12.hjLm?.let { try { lm.removeUpdates(it) } catch (e: Exception) { } }; s12.hjLm = null
    if (!on) { s12.hjTtara = false; s12.hjTtaraJul = ""; draw(); return }
    val spots = hjSpots(s12.hjC)
    if (spots.none { spotLL(it) != null }) { malhagi("위치가 적힌 지점이 없어 따라갈 수 없습니다."); return }
    if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { jari { hjTtaraOn(h, true) }; return }
    s12.hjTtara = true
    val l = LocationListener { loc -> main.post { hjJari(h, loc) } }
    s12.hjLm = l
    try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, l, android.os.Looper.getMainLooper()) } catch (e: Exception) { }
    malhagi("지금 자리로 따라갑니다. 다음 지점까지 거리와 방향을 말씀드립니다."); draw()
}
internal fun dirWord(brg: Double, heading: Double?): String {
    if (heading != null) { var hr = ((((brg - heading) + 360) % 360) / 30).roundToInt() % 12; if (hr == 0) hr = 12; return "${hr}시 방향" }
    return "북쪽을 12시로 " + Wonchik.clock(brg) + " 방향"
}
internal fun TourActivity.hjJari(h: Hw.Hyeonjang, l: Location) {
    if (!s12.hjTtara) return
    val spots = hjSpots(s12.hjC)
    val me = l.latitude to l.longitude; val acc = l.accuracy.toInt()
    val heading: Double? = if (l.hasBearing() && l.hasSpeed() && l.speed > 0.4f) l.bearing.toDouble() else null
    var k = s12.hjIdx + 1; while (k < spots.size && spotLL(spots[k]) == null) k++
    if (sp.getBoolean("offOn", true)) {
        val pts = spots.mapNotNull { spotLL(it) }
        if (pts.isNotEmpty()) {
            val (d, near) = Wonchik.offDist(me, pts)
            if (d > sp.getInt("offM", 40) + minOf(max(acc, 0), 25)) {
                if (s12.offSince == 0L) s12.offSince = System.currentTimeMillis()
                if (System.currentTimeMillis() - s12.offSince >= sp.getInt("offS", 20) * 1000L && (!s12.offOut || System.currentTimeMillis() - s12.offSaid > 60000)) {
                    s12.offOut = true; s12.offSaid = System.currentTimeMillis()
                    var t = "코스에서 ${d}미터 벗어났습니다."
                    if (near != null) t += " 가장 가까운 지점은 ${near + 1}번, ${Wonchik.meter(me, pts[near])}미터 " + dirWord(Wonchik.brg(me, pts[near]), heading) + "."
                    malhagi(t); deureul(); ppi()
                }
            } else { s12.offSince = 0L; if (s12.offOut) { s12.offOut = false; malhagi("코스로 돌아왔습니다.") } }
        }
    }
    val tg = if (k < spots.size) spotLL(spots[k]) else null
    if (tg == null) { s12.hjTtaraJul = "더 갈 지점이 없습니다."; return }
    val m = Wonchik.meter(me, tg); val brg = Wonchik.brg(me, tg)
    val line = "다음 ${k + 1}번 " + spots[k].geul("name") + "까지 ${m}미터, " + dirWord(brg, heading) + if (acc > 20) " (위치 오차 ${acc}미터)" else ""
    s12.hjTtaraJul = line
    val lastM = s12.hjLastM
    if (m <= 5 || (m <= 12 && acc <= 8 && lastM != null && m >= lastM)) {
        deureul(); ppi(); hjBoyeo(h, k)
        malhagi("${k + 1}번 " + spots[k].geul("name") + "에 도착했습니다. 대본을 띄웠습니다."); s12.hjLastM = null; s12.hjLastSaid = System.currentTimeMillis(); return
    }
    val now = System.currentTimeMillis()
    if (now - s12.hjLastSaid > 20000 || (lastM != null && abs(lastM - m) >= 15) || (m <= 20 && now - s12.hjLastSaid > 6000)) { malhagi(line); s12.hjLastSaid = now; dasiGeuryeo(h) }
    s12.hjLastM = m
}
internal fun TourActivity.hjDatgi() {
    hjTimerMeom(false)
    val lm = getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
    s12.hjLm?.let { try { lm.removeUpdates(it) } catch (e: Exception) { } }; s12.hjLm = null; s12.hjTtara = false; s12.hjTtaraJul = ""
    if (s12.hjKkeojim) { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); s12.hjKkeojim = false }
}

// ================= 점검 =================
internal fun TourActivity.diagHwamyeon() {
    geul("점검", jemok = true)
    geul("현장에 나가시기 전에 한 번 눌러 보십시오", jemok = true)
    danchu("다시 살피기") { s12.diag = null; draw() }
    val D = s12.diag
    if (D != null) { D.forEach { geul(it) }; return }
    geul("살피는 중입니다.")
    fun hr(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    val R = mutableListOf<String>()
    R.add("카메라: " + if (hr(Manifest.permission.CAMERA)) "됨" else "아직 허락하지 않음 (처음 찍을 때 허락해 주십시오)")
    R.add("위치: " + if (hr(Manifest.permission.ACCESS_FINE_LOCATION)) "됨" else "아직 허락하지 않음 — 처음 쓸 때 허락해 주십시오")
    val sm = getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager
    R.add("나침반: " + if (sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null || sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null) "됨" else "이 기기는 방위를 읽지 못합니다")
    R.add("마이크: " + if (hr(Manifest.permission.RECORD_AUDIO)) "됨" else "아직 허락하지 않음 — 처음 녹음할 때 허락해 주십시오")
    R.add("받아쓰기: " + if (Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isRecognitionAvailable(this)) "됨" else "이 폰에서는 소리로만 담깁니다 (안드로이드 13 이상에서 됨)")
    R.add("못 올린 사진·소리: ${Daegi.su(this)}개")
    pool.execute {
        val j = Api.call("selfcheck")
        if (Api.ok(j)) {
            R.add("나스 연결: 됨 (코스 ${j.ip("courses")}개, 남은 자리 " + j.geul("freegb") + "GB)")
            R.add("나스 저장: " + if (j.optBoolean("mine_writable")) "됨" else "안 됨 — 협회에 알려 주십시오")
            R.add("코스 통째로 묶기(zip): " + if (j.optBoolean("zip")) "됨" else "안 됨")
        } else R.add("나스 연결: 안 됨 — " + Api.err(j))
        R.add(TourActivity.PAN_IREUM)
        main.post { s12.diag = R; malhagi("점검했습니다. " + R.take(6).joinToString(". ")); dasiGeuryeo(Hw.Diag) }
    }
}

// ================= 기능 찾기 =================
internal data class Gi(val id: String, val k: String, val tab: Int?, val master: Boolean, val ga: Hw?)
internal val GINEUNG = listOf(
    Gi("코스 정보 (이름·장소·집합 장소·분야·지역·인원·앞머리·장소 평면도)", "1단계 코스 정하기 이름 장소 집합 분야 지역 인원 앞머리 평면도 방향 잡기", null, false, Hw.KosJeongbo),
    Gi("대본 살펴보기·원칙 16개 점검표", "원칙 점검 검사 금지어 방향 대본 살펴보기 16개", null, false, Hw.Jeomgeom),
    Gi("내보내기·협회 최종 승인 요청", "6단계 내보내기 대본집 점자 안내문 운영표 지도 승인 요청 협회 DB 통째로 백업 안내문 소리", null, false, Hw.Naebo),
    Gi("장소 지식 들이기 (뼈대 짜기)", "장소 지식 뼈대 자동 국가유산 관광 테마투어", null, false, Hw.Jisik),
    Gi("우리가 확인한 것 채워 넣기", "우리자료 협회자료 계단 점자 채우기", null, false, Hw.Urijaryo),
    Gi("용어 뜻 찾기·우리 용어 사전", "뜻 낱말 사전 용어 우리사전", null, false, Hw.Dict),
    Gi("답사 지도", "지도 답사 사진 방향 위치", null, false, Hw.SurveyMap),
    Gi("현장영상해설 화면 (협회 승인본)", "현장영상해설 승인본 시간 재기 따라가기 화면 안 꺼짐 읽어 주기", null, false, Hw.Finals(true)),
    Gi("판 되돌리기", "되돌리기 판 복구 실수", null, false, Hw.PanDoerigi),
    Gi("휴지통 — 지운 코스 되살리기", "휴지통 되살리기 복구 지운 코스", null, false, Hw.Hyujitong),
    Gi("비밀번호 바꾸기", "비밀번호 바꾸기 암호", null, false, Hw.Pw),
    Gi("점검", "점검 카메라 나침반 위치 나스 마이크", null, false, Hw.Diag),
    Gi("답사 탭 — 사진 찍기·저절로 찍기·폰 사진 넣기·현장 메모·코스 벗어남", "2단계 답사 사진 카메라 저절로 찍기 담는 모양 폰 사진 메모 벗어남 경계", 1, false, null),
    Gi("지점 탭 — 지점 목록·지점 한 화면·걷는 시간 어림", "3단계 4단계 지점 목록 한 화면 대본 걷는 시간 방향 도우미 테마투어 느낌", 2, false, null),
    Gi("현장영상해설 탭 — 대본 보기·읽으며 녹음·전체 시간", "5단계 연습 녹음 시간 읽기", 3, false, null),
    Gi("클에게 대본 맡기기", "클 초안 대본 맡기기 요청함", null, true, Hw.Claude),
    Gi("가입 신청 함", "가입 신청 승인", null, true, Hw.Signups),
    Gi("명부 관리", "명부 사람 넣기 비밀번호 다시 막기", null, true, Hw.Users),
    Gi("반 관리", "반 수업 모둠 학생", null, true, Hw.Classes),
    Gi("수업 진행판", "진행판 수업 학생 상태", null, true, Hw.Board),
    Gi("수업 채점표", "채점 채점표 학생", null, true, Hw.Grade),
    Gi("협회 승인 보관소", "보관소 승인 돌려보내기 공개 내려받기 비교", null, true, Hw.Finals(false)),
    Gi("봐 달라는 코스", "봐달라 알림 확인", null, true, Hw.Reviews))
internal fun TourActivity.gineungHwamyeon() {
    geul("기능 찾기", jemok = true)
    ipryeok("찾을 낱말 (예: 사진, 대본, 승인, 지도)", s12.gineungQ) { s12.gineungQ = it }
    danchu("찾기") { jang.remove("gn"); draw(); cheotJul(jangCheotJari["gn"]) }
    val q = s12.gineungQ.trim()
    val hits = GINEUNG.filter { (!it.master || isMaster()) && (q.isEmpty() || (it.id + " " + it.k).contains(q)) }
    geul(if (hits.isEmpty()) "찾지 못했습니다. 다른 낱말로 찾아 보십시오." else "${hits.size}가지", jemok = true)
    jangMok("gn", hits) { g ->
        danchu(g.id) {
            val t = g.tab
            if (t != null) { tab = t; draw() }
            else g.ga?.let { s12.finals = null; s12.signups = null; s12.users = null; s12.reviews = null; s12.boardRows = null; s12.diag = null; s12.terms = null; if (it is Hw.Urijaryo) { s12.uriOpen = false; s12.uriQ = kos?.s("place") ?: "" }; go(it) }
        }
    }
}

// ================= 사진 — 담는 모양·밝기·폰 사진 넣기·저절로 찍기 =================
/** 사진 파일을 바로 세운 그림으로 (긴 쪽 2400점 안쪽) */
internal fun bitmapBaro(f: File): Bitmap? = try {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, o)
    var smp = 1; while (max(o.outWidth, o.outHeight) / (smp * 2) >= 1600) smp *= 2
    var bm: Bitmap = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = smp })
    val rot = when (ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f; ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f }
    if (rot != 0f) bm = Bitmap.createBitmap(bm, 0, 0, bm.width, bm.height, Matrix().apply { postRotate(rot) }, true)
    bm
} catch (e: Exception) { null }
/** 사진 올리기 — 담는 모양으로 자르고 1600점으로 줄여, 밝기·또렷함을 재어 함께 적음(웹 shoot 과 같음) */
internal fun TourActivity.sajinOllim(bm0: Bitmap, dir: String, extra: JSONObject, kkeut: (() -> Unit)? = null) {
    val k = kos ?: return; val s = spot() ?: return
    if (k.isGyobon) { malhagi("교본은 읽기만 됩니다."); return }
    var bm = Wonchik.jareugi(bm0, sp.getString("damneunMoyang", "won") ?: "won")
    val m = max(bm.width, bm.height); if (m > 1600) { val r = 1600f / m; bm = Bitmap.createScaledBitmap(bm, (bm.width * r).toInt(), (bm.height * r).toInt(), true) }
    val q = Wonchik.photoQuality(bm); val qWarn = q.geul("warn")
    val moyang = Wonchik.moyangMal(bm.width, bm.height)
    val out = ByteArrayOutputStream(); bm.compress(Bitmap.CompressFormat.JPEG, 82, out); val jpg = out.toByteArray()
    val w = bm.width; val h = bm.height
    malhagi("$moyang 사진을 올립니다.")
    jari { l ->
        spotPosHere(s, l)
        pool.execute {
            val j = Api.call("save_photo", JSONObject().put("c", k.id).put("s", s.id).put("ext", "jpg").put("data", Base64.encodeToString(jpg, Base64.NO_WRAP)), timeoutMs = 90000)
            main.post {
                val shot = JSONObject().put("f", "").put("dir", dir).put("garo", w).put("sero", h).put("at", nowIso()).put("zoom", 1).put("app", "android").put("q", q)
                extra.keys().forEach { kk -> shot.put(kk, extra.get(kk)) }
                val la = s.lat; val lo = s.lng
                if (la != null && lo != null) shot.put("lat", la).put("lng", lo) else shot.put("lat", JSONObject.NULL).put("lng", JSONObject.NULL)
                if (Api.ok(j)) {
                    shot.put("f", j.geul("file")); s.shots.put(shot); lastShot = s.id to (s.shots.length() - 1)
                    val jjigeun = (0 until s.shots.length()).map { s.shots.optJSONObject(it)?.geul("dir") ?: "" }.toSet()
                    val i = TourActivity.NE.indexOf(dir)
                    val da = if (i < 0) null else (TourActivity.NE.drop(i + 1) + TourActivity.NE.take(i)).firstOrNull { !jjigeun.contains(it) }
                    if (da != null) sajinBang = da
                    malhagi("$moyang " + k.spotLab(s) + " 사진 ${s.shots.length()}번째, $dir 올렸습니다." + (if (qWarn.isEmpty()) "" else " $qWarn 다시 담으시려면 같은 방향으로 한 장 더 찍으십시오.") +
                        if (extra.has("auto") || i < 0) "" else if (da != null) " 몸을 시계 방향으로 돌리신 뒤 찍으십시오. 다음은 ${da}입니다." else " 네 방향을 다 담았습니다.")
                } else {
                    val ref = Daegi.saeRef("photo")
                    Daegi.neoki(this, JSONObject().put("ref", ref).put("jong", "photo").put("c", k.id).put("s", s.id).put("ext", "jpg").put("kind", "").put("at", nowIso()), jpg)
                    shot.put("qref", ref); s.shots.put(shot); lastShot = s.id to (s.shots.length() - 1)
                    malhagi("사진을 올리지 못해 폰에 남겨 두었습니다. 인터넷이 되면 설정의 못 올린 것 지금 올리기로 올리십시오. 이 코스를 다시 열 때도 저절로 올립니다. " + Api.err(j))
                }
                bakkwim(); kkeut?.invoke(); if (cur() !is Hw.Jeoljeoro) draw()
            }
        }
    }
}
internal fun TourActivity.sajinGajyeogi() {
    try { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), TourActivity.SAJIN_GAJYEO) }
    catch (e: Exception) { malhagi("사진 고르는 화면을 열지 못했습니다.") }
}
internal fun TourActivity.sajinGajyeoBatgi(data: Intent?) {
    val u = data?.data ?: run { malhagi("사진을 고르지 않았습니다."); return }
    pool.execute {
        val f = File(File(cacheDir, "sajin").apply { mkdirs() }, "gajyeo.jpg")
        try { contentResolver.openInputStream(u)?.use { inp -> f.outputStream().use { inp.copyTo(it) } } } catch (e: Exception) { }
        val bm = bitmapBaro(f)
        main.post { if (bm == null) malhagi("사진을 읽지 못했습니다.") else sajinOllim(bm, "방위 모름", JSONObject().put("from", "file")) }
    }
}
/** 사진 앱에 저장하기 */
@Suppress("DEPRECATION")
internal fun TourActivity.sajinJeojang(bm: Bitmap, ireum: String) {
    try {
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, ireum); put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg"); put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/투어스튜디오") }
            val u = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: throw Exception()
            contentResolver.openOutputStream(u)?.use { bm.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        } else MediaStore.Images.Media.insertImage(contentResolver, bm, ireum, "LVD투어스튜디오")
        malhagi("사진 앱에 저장했습니다. 파일 이름은 ${ireum}입니다.")
    } catch (e: Exception) { malhagi("사진을 저장하지 못했습니다. 잠시 뒤 다시 눌러 주십시오.") }
}
/** 답사 탭에 붙는 카메라 묶음 */
internal fun TourActivity.kamera120(k: Kos) {
    geul("카메라 — 몸을 돌리면 저절로 찍기·폰 사진 넣기", jemok = true)
    val auto = sp.getBoolean("camAuto", false)
    danchu("찍는 방식 — " + (if (auto) "몸을 돌리면 저절로 찍기 (12시·3시·6시·9시)" else "손으로 한 장씩 찍기") + ". 누르면 바꿉니다") { sp.edit().putBoolean("camAuto", !auto).apply(); draw() }
    danchu(if (auto) "저절로 찍기 카메라 열기" else "나침반 카메라 열기 (한 장씩)", keun = true) { if (spot() == null) malhagi("먼저 지점을 골라 주십시오.") else go(Hw.Jeoljeoro) }
    val mo = sp.getString("damneunMoyang", "won") ?: "won"
    danchu("담는 모양 — " + (Wonchik.MOYANG.firstOrNull { it.first == mo }?.second ?: "원본 그대로") + ". 누르면 고릅니다") {
        android.app.AlertDialog.Builder(this).setTitle("담는 모양").setItems(Wonchik.MOYANG.map { it.second }.toTypedArray()) { _, i -> sp.edit().putString("damneunMoyang", Wonchik.MOYANG[i].first).apply(); malhagi("담는 모양을 " + Wonchik.MOYANG[i].second + "(으)로 정했습니다."); draw() }.setNegativeButton("그만두기", null).show()
    }
    val so = sp.getBoolean("damSori", true)
    danchu(if (so) "담을 때 삐 소리 — 켜짐. 누르면 끕니다" else "담을 때 삐 소리 — 꺼짐. 누르면 켭니다") { sp.edit().putBoolean("damSori", !so).apply(); draw() }
    danchu("폰에 담긴 사진 넣기 (방위 모름으로 담김)") { sajinGajyeogi() }
    geul("코스 벗어남 알림", jemok = true)
    val on = sp.getBoolean("offOn", true)
    danchu(if (on) "코스 벗어남 알림 — 켜짐. 누르면 끕니다" else "코스 벗어남 알림 — 꺼짐. 누르면 켭니다") {
        sp.edit().putBoolean("offOn", !on).apply(); if (!on) offSijak() else offMeom(); malhagi(if (!on) "코스 벗어남 알림을 켰습니다." else "코스 벗어남 알림을 껐습니다."); draw()
    }
    kan("벗어남 기준 (미터)", sp.getInt("offM", 40).toString(), susja = true) { sp.edit().putInt("offM", (it.toIntOrNull() ?: 40).coerceIn(10, 300)).apply() }
    kan("기다리는 시간 (초)", sp.getInt("offS", 20).toString(), susja = true) { sp.edit().putInt("offS", (it.toIntOrNull() ?: 20).coerceIn(5, 120)).apply() }
    if (s12.offTti.isNotEmpty()) geul(s12.offTti)
    offSijak()
}
@SuppressLint("MissingPermission")
internal fun TourActivity.offSijak() {
    if (!sp.getBoolean("offOn", true) || s12.offLm != null) return
    val k = kos ?: return
    if (Wonchik.spotPts(k).isEmpty() && Wonchik.pos(k) == null) return
    if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
    val lm = getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
    val l = LocationListener { loc -> main.post { offBwa(loc) } }
    s12.offLm = l; s12.offSince = 0L; s12.offOut = false; s12.offTti = ""
    try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 2f, l, android.os.Looper.getMainLooper()) } catch (e: Exception) { }
}
internal fun TourActivity.offMeom() {
    val lm = getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
    s12.offLm?.let { try { lm.removeUpdates(it) } catch (e: Exception) { } }; s12.offLm = null; s12.offTti = ""
}
internal fun TourActivity.offBwa(l: Location) {
    val k = kos ?: return
    if (!sp.getBoolean("offOn", true)) return
    val acc = l.accuracy.toDouble(); if (acc > 60) return
    val me = l.latitude to l.longitude
    var pts = Wonchik.spotPts(k).map { it.p }; var nm = Wonchik.spotPts(k).map { "${it.i + 1}번 " + it.s.name }
    if (pts.isEmpty()) { val p = Wonchik.pos(k) ?: return; pts = listOf(p); nm = listOf(k.s("posName").ifEmpty { "기준 자리" }) }
    val (d, near) = Wonchik.offDist(me, pts)
    if (d > sp.getInt("offM", 40) + minOf(acc, 25.0)) {
        if (s12.offSince == 0L) s12.offSince = System.currentTimeMillis()
        if (System.currentTimeMillis() - s12.offSince >= sp.getInt("offS", 20) * 1000L && (!s12.offOut || System.currentTimeMillis() - s12.offSaid > 60000)) {
            s12.offOut = true; s12.offSaid = System.currentTimeMillis()
            var t = "코스에서 ${d}미터 벗어났습니다."
            if (near != null) t += " 가장 가까운 지점은 " + nm[near] + ", 북쪽을 12시로 " + Wonchik.clock(Wonchik.brg(me, pts[near])) + " 방향 ${Wonchik.meter(me, pts[near])}미터입니다."
            s12.offTti = t; malhagi(t); deureul(); ppi(); if (cur() is Hw.Dapsa) draw()
        }
    } else { s12.offSince = 0L; if (s12.offOut) { s12.offOut = false; s12.offTti = ""; malhagi("코스로 돌아왔습니다."); if (cur() is Hw.Dapsa) draw() } }
}

// ---- 저절로 찍기 (나침반 카메라) ----
@Suppress("DEPRECATION")
internal fun TourActivity.jeoljeoroHwamyeon() {
    val k = kos ?: return kosEopseum()
    val s = spot() ?: return kosEopseum()
    val auto = sp.getBoolean("camAuto", false)
    val t0 = geul(s12.camJul, jemok = true); t0.tag = "camJul"
    geul(listOf(12, 3, 6, 9).joinToString("  ") { (if (s12.got.contains(it)) "● " else "○ ") + "${it}시" }).apply {
        contentDescription = "담은 방향 " + listOf(12, 3, 6, 9).filter { s12.got.contains(it) }.joinToString(", ") { "${it}시" }.ifEmpty { "없음" }; tag = "camGot"
    }
    geul(k.spotFull(s) + " — 사진 ${s.shots.length()}장", jakge = true)
    danchu("한 장 찍기", keun = true) { camJjikgi(0) }
    danchu("닫기") { kameraDatgi(); onBackPressed() }
    if (s12.cam == null) {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.CAMERA), TourActivity.HEORAK_KAMERA); geul("카메라를 허락해 주신 뒤 다시 열어 주십시오."); return }
        s12.camAuto = auto; s12.got.clear(); s12.base = null; s12.idx = 0; s12.busy = false; s12.guide = false; s12.lastBk = null; s12.heading = null
        val sv = SurfaceView(this).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        body.addView(sv, LinearLayout.LayoutParams(-1, dp(360)).apply { topMargin = dp(8) })
        sv.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(h: SurfaceHolder) {
                try {
                    val c = android.hardware.Camera.open()
                    c.setDisplayOrientation(90)
                    val p = c.parameters; p.setRotation(90)
                    if (p.supportedFocusModes?.contains(android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) == true) p.focusMode = android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                    p.supportedPictureSizes?.filter { it.width <= 2400 }?.maxByOrNull { it.width * it.height }?.let { p.setPictureSize(it.width, it.height) }
                    c.parameters = p
                    c.setPreviewDisplay(h); c.startPreview(); s12.cam = c
                    camSensorSijak()
                    camMal("카메라를 열었습니다. " + if (auto) "앞을 향하고 잠시 서 계시면 12시부터 담고, 시계 방향으로 천천히 도시면 3시·6시·9시가 차례로 담깁니다." else "방향을 잡고 한 장 찍기를 누르십시오.")
                    main.postDelayed({ if (s12.cam != null && s12.heading == null) { s12.guide = true; val nx = listOf(12, 3, 6, 9)[s12.idx % 4]; camMal("나침반이 잡히지 않습니다. 몸을 시계 방향으로 돌리신 뒤 한 장 찍기를 누르시면 ${nx}시 방향으로 담습니다.") } }, 3500)
                    if (auto) main.postDelayed({ if (s12.cam != null && s12.base == null) camJjikgi(12) }, 2500)
                } catch (e: Exception) { camMal("카메라를 열지 못했습니다.") }
            }
            override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) {}
            override fun surfaceDestroyed(h: SurfaceHolder) { kameraDatgi() }
        })
    }
    cheotJul(t0)
}
internal fun TourActivity.camMal(t: String) { s12.camJul = t; body.findViewWithTag<android.widget.TextView>("camJul")?.text = t; malhagi(t) }
internal fun TourActivity.camSensorSijak() {
    val sm = getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager
    val rv = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR); val ac = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    val R = FloatArray(9); val R2 = FloatArray(9); val o = FloatArray(3)
    val l = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            if (e.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                SensorManager.getRotationMatrixFromVector(R, e.values)
                SensorManager.remapCoordinateSystem(R, SensorManager.AXIS_X, SensorManager.AXIS_Z, R2)
                SensorManager.getOrientation(R2, o)
                s12.heading = (Math.toDegrees(o[0].toDouble()) + 360) % 360
            } else if (e.sensor.type == Sensor.TYPE_ACCELEROMETER) s12.gz = (e.values[2] / 9.81).toDouble()
            camTick()
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }
    s12.sensor = l
    rv?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_UI) }
    ac?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_UI) }
}
internal fun angDiff(a: Double, b: Double): Double { val d = abs(a - b) % 360; return if (d > 180) 360 - d else d }
internal fun TourActivity.camTick() {
    val h = s12.heading ?: return
    val now = System.currentTimeMillis()
    val b = s12.base
    val bk = if (b == null) 12 else listOf(12, 3, 6, 9)[((((h - b) + 360) % 360) / 90).roundToInt() % 4]
    if (bk != s12.lastBk) { if (s12.lastBk != null && !s12.camAuto && now - s12.saidAt > 1500) { malhagi("${bk}시 방향으로 잡습니다."); s12.saidAt = now }; s12.lastBk = bk
        if (!s12.busy) { s12.camJul = "${bk}시 방향으로 잡습니다."; body.findViewWithTag<android.widget.TextView>("camJul")?.text = s12.camJul } }
    s12.gz?.let { z ->
        val tl = if (z > 0.5) "up" else if (z < -0.26) "down" else "ok"
        if (tl != s12.lastTl) { if (tl != "ok" && now - s12.saidAt > 3000) { malhagi(if (tl == "down") "폰을 조금 내려 주십시오." else "폰을 조금 올려 주십시오."); s12.saidAt = now }; s12.lastTl = tl }
    }
    if (s12.camAuto && b != null && !s12.busy) {
        val rel = ((h - b) + 360) % 360
        for ((kk, a) in listOf(12 to 0.0, 3 to 90.0, 6 to 180.0, 9 to 270.0)) if (!s12.got.contains(kk) && angDiff(rel, a) < 12) { camJjikgi(kk); break }
    }
}
@Suppress("DEPRECATION")
internal fun TourActivity.camJjikgi(clock0: Int) {
    if (s12.busy) return
    val c = s12.cam ?: run { malhagi("카메라가 아직 열리지 않았습니다. 잠시 뒤 다시 눌러 주십시오."); return }
    var clock = clock0
    val h = s12.heading
    if (s12.base == null && h != null) s12.base = h
    if (clock == 0) {
        val b = s12.base
        clock = if (h == null) { val x = listOf(12, 3, 6, 9)[s12.idx % 4]; s12.idx++; x } else if (b != null) listOf(12, 3, 6, 9)[((((h - b) + 360) % 360) / 90).roundToInt() % 4] else 12
    } else if (clock == 12 && h == null) s12.idx = 1
    s12.busy = true
    if (sp.getBoolean("damSori", true)) ppi()
    val hd = h
    try {
        c.takePicture(null, null) { data, cam ->
            try { cam.startPreview() } catch (e: Exception) { }
            val bm = try { BitmapFactory.decodeByteArray(data, 0, data.size) } catch (e: Exception) { null }
            if (bm == null) { s12.busy = false; malhagi("사진을 담지 못했습니다. 다시 찍어 주십시오."); return@takePicture }
            val f = File(File(cacheDir, "sajin").apply { mkdirs() }, "jeoljeoro.jpg"); try { f.writeBytes(data) } catch (e: Exception) { }
            val baro = bitmapBaro(f) ?: bm
            val ex = JSONObject().put("auto", s12.camAuto).put("head", if (hd != null) hd.roundToInt() else JSONObject.NULL)
            sajinOllim(baro, "${clock}시 방향", ex) {
                s12.got.add(clock); s12.busy = false
                val left = listOf(12, 3, 6, 9).filter { !s12.got.contains(it) }
                val m = if (left.isEmpty()) "네 방향을 다 담았습니다. 다음 지점으로 옮기셔도 됩니다." else "다음은 ${left[0]}시 방향입니다." + if (s12.camAuto && !s12.guide) " 시계 방향으로 천천히 도십시오." else " 몸을 시계 방향으로 돌리신 뒤 한 장 찍기를 누르십시오."
                s12.camJul = m
                body.findViewWithTag<android.widget.TextView>("camJul")?.text = m
                body.findViewWithTag<android.widget.TextView>("camGot")?.text = listOf(12, 3, 6, 9).joinToString("  ") { (if (s12.got.contains(it)) "● " else "○ ") + "${it}시" }
                main.postDelayed({ malhagi(m) }, 2500)
            }
        }
    } catch (e: Exception) { s12.busy = false; malhagi("사진을 담지 못했습니다. 다시 찍어 주십시오.") }
}
@Suppress("DEPRECATION")
internal fun TourActivity.kameraDatgi() {
    s12.sensor?.let { (getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager).unregisterListener(it) }; s12.sensor = null
    s12.cam?.let { try { it.stopPreview() } catch (e: Exception) { }; try { it.release() } catch (e: Exception) { } }; s12.cam = null
}

// ================= 수업 진행판으로 보내는 지금 상태 (웹 lvPing) =================
internal fun TourActivity.ping() {
    if (me == null || Api.token.isEmpty() || isMaster()) return
    val k = kos
    val stage = when (tab) { 1 -> 2; 2 -> 3; 3 -> 5; else -> if (k == null) 0 else 1 }
    val sc = if (tab == 4) "scHelp" else if (k == null) "scList" else "scWork"
    val b = JSONObject().put("sc", sc).put("stage", if (sc == "scWork") stage else 0).put("cid", k?.id ?: "").put("cname", k?.name ?: "")
        .put("last", "앱 " + allim.take(50)).put("fails", s12.fails).put("err", s12.err)
    pool.execute { Api.call("ping", b) }
}
internal fun TourActivity.pingSijak() {
    val r = object : Runnable { override fun run() { ping(); main.postDelayed(this, 30000) } }
    main.postDelayed(r, 3000)
}

// ================= 사진 크게 보기에 더하는 것 =================
internal fun TourActivity.sajin120(k: Kos, s: Spot, idx: Int) {
    val a = shotsOf(s); val p = a.getOrNull(idx) ?: return
    val q = p.optJSONObject("q")
    geul((q?.geul("warn")?.ifEmpty { null } ?: "밝기와 또렷함 좋음") + if (q != null && (q.su("bright") ?: -1.0) >= 0) " (밝기 ${q.ip("bright")}, 또렷함 " + q.geul("sharp") + (if (p.su("head") != null) ", 찍을 때 나침반 ${p.ip("head")}도" else "") + ")" else "", jakge = true)
    if (idx > 0) danchu("앞 사진") { val st = stacks[tab]; st[st.size - 1] = Hw.Sajin(s.id, idx - 1); draw() }
    if (idx + 1 < a.size) danchu("다음 사진") { val st = stacks[tab]; st[st.size - 1] = Hw.Sajin(s.id, idx + 1); draw() }
    val f = p.geul("f")
    if (f.isNotEmpty()) danchu("이 사진 폰에 저장하기 (사진 앱)") {
        malhagi("사진을 폰에 저장합니다.")
        pool.execute {
            val bm = try { URL(Api.fileUrl("photo", k.id, f, Api.needTicket())).openStream().use { BitmapFactory.decodeStream(it) } } catch (e: Exception) { null }
            main.post { if (bm == null) malhagi("사진을 저장하지 못했습니다. 잠시 뒤 다시 눌러 주십시오.") else sajinJeojang(bm, (k.name + "_" + s.name + "_" + (idx + 1) + "번째_" + p.geul("dir").ifEmpty { "방위모름" } + ".jpg").replace(Regex("[\\\\/:*?\"<>|]"), "_")) }
        }
    }
    if (k.isGyobon) return
    geul("방향 고치기", jemok = true)
    TourActivity.NE.forEach { d -> danchu(d + "으로 적기" + if (d == p.geul("dir")) " (지금)" else "") { p.put("dir", d); bakkwim(); malhagi("이 사진 방향을 ${d}으로 적었습니다."); draw() } }
    geul("다른 지점으로 옮기기", jemok = true)
    danchu("옮길 지점 고르기") {
        val mok = k.allSpots.filter { it.id != s.id }
        android.app.AlertDialog.Builder(this).setTitle("옮길 지점").setItems(mok.map { k.spotFull(it) }.toTypedArray()) { _, i ->
            val t = mok[i]
            mutgi2("이 사진을 " + t.name + " 지점으로 옮깁니다. 옮길까요?", "옮기기") {
                val b = shotsOf(s).toMutableList(); if (idx >= b.size) return@mutgi2
                val x = b.removeAt(idx); s.d.put("shots", JSONArray().apply { b.forEach { put(it) } }); t.shots.put(x)
                lastShot?.let { if (it.first == s.id && it.second == idx) lastShot = t.id to (t.shots.length() - 1) }
                bakkwim(); malhagi("사진을 " + t.name + " 지점으로 옮겼습니다. " + s.name + " 지점에 ${b.size}장, " + t.name + " 지점에 ${t.shots.length()}장입니다.")
                onBackPressed()
            }
        }.setNegativeButton("그만두기", null).show()
    }
}

// ================= 1.2.0 화면 나누기 =================
internal fun TourActivity.sae120(h: Hw) {
    when (h) {
        Hw.Naebo -> naeboHwamyeon()
        Hw.Jisik -> jisikHwamyeon()
        Hw.Urijaryo -> urijaryoHwamyeon()
        Hw.Dict -> dictHwamyeon()
        Hw.SurveyMap -> surveyMap()
        is Hw.Finals -> finalsHwamyeon(h.hj)
        is Hw.Hyeonjang -> hyeonjangHwamyeon(h)
        is Hw.Compare -> compareHwamyeon(h)
        Hw.Diag -> diagHwamyeon()
        Hw.Gineung -> gineungHwamyeon()
        Hw.Pw -> pwHwamyeon(false)
        Hw.AsPick -> asPick()
        Hw.Signups -> signupsHwamyeon()
        Hw.Users -> usersHwamyeon()
        Hw.Reviews -> reviewsHwamyeon()
        Hw.Classes -> classesHwamyeon()
        is Hw.ClassOne -> classOne(h.id)
        Hw.Board -> boardHwamyeon()
        Hw.Grade -> gradeHwamyeon()
        Hw.Claude -> claudeHwamyeon()
        Hw.Jeoljeoro -> jeoljeoroHwamyeon()
        else -> {}
    }
}
