// 안드로이드 자봉 — 점지도 그리기 엔진 (2.3.0, 빌드 261002-J1, 대표님 지시: 아이폰 자봉과 똑같이)
// 아이폰 자봉 JabongGeurigi.swift(2.2.0~2.3.0)와 같은 기록 모양(1초 줄 pts, 표시 marks, 걸음 줄 gs, 기기 정보 gigi)으로 그립니다.
// 웹 jeom_rec.js 를 걷어 내고 속까지 앱 — 화면이 꺼져도 알림 칸의 자봉(JabongService)이 붙들어 이어 그림.
// 1분마다, 표시를 남길 때마다, 앱이 뒤로 갈 때 저절로 저장. 앱이 꺼졌다 켜지면 그리던 길을 잠깐 멈춤으로 되살림.
// 261002-J1 아이폰과 맞춘 것: 자봉 번호를 길에 적음, 출발·도착 자리 주소를 저절로 적음(jeom.php a=jimyeong),
//   길을 접어들면 무슨 길인지 알려 드림(chatta.php a=gil, 15초마다·40미터 넘게 옮겼을 때), 말로 표시(받아쓰기), 폰이 여쭌 뒤 네·아니오를 말로도 들음,
//   문은 두 걸음 사이 두 번 찍어야 확실한 문, 계단 칸수 고치기, 탈것 거리를 킬로미터로, 그린 길 목록(다섯 개씩 보기)을 폰에 담음,
//   꺾임 여쭙기는 자이로가 없으면 나침반으로, 1초 줄에 걸음 빠르기(cad — 폰 걸음 센서로 최근 4초 셈), 지금 상태에 위성 형편.
// 2.11.0 봉사자 요청(이사장님 승인, 아이폰 자봉 2.11.0과 같음) — 계단 여쭙기 없앰, 꺾임 여쭙기 기본 끔, 단추에 없는 말도 말로 표시,
//   그린 길 올리기(jeom_olligi.php — 서버 점검 뒤 점지도 창고)와 지우기, 출발지·도착지 이름 직접 넣기, 안내 말소리 끄기를 그리기 화면에.
package kr.or.ada.app.jabong

import android.content.Context
import android.os.Handler
import android.os.Looper
import kr.or.ada.app.gilnun.EumJong
import kr.or.ada.app.gilnun.Eum
import kr.or.ada.app.gilnun.Girok
import kr.or.ada.app.gilnun.Jindong
import kr.or.ada.app.gilnun.MalDeutgi
import kr.or.ada.app.gilnun.MalGeup
import kr.or.ada.app.gilnun.MalSajeon
import kr.or.ada.app.gilnun.MomSensor
import kr.or.ada.app.gilnun.Seoljeong
import kr.or.ada.app.gilnun.Sori
import kr.or.ada.app.gilnun.Wichi
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object Geurigi {
    enum class Sangtae { SWIM, GEOREUM, MEOMCHUM }

    /** 표시 스물두 가지 — 아이폰·웹과 같음 */
    val MARKS = listOf("올라가는 계단 시작", "내려가는 계단 시작", "계단 끝", "오름턱", "내림턱", "횡단보도 건너기 시작", "횡단보도 건너기 끝",
        "9시 방향으로 꺾임", "3시 방향으로 꺾임", "점자블록 끊김", "문", "엘리베이터", "버스 정류장", "지하철 개찰구", "조심할 곳",
        "에스컬레이터 올라감", "에스컬레이터 내려감", "에스컬레이터 내림", "지하철 탐", "지하철 내림", "버스 탐", "버스 내림")
    /** 자주 쓰는 표시 — 겉에 크게 */
    val JAJU = listOf("9시 방향으로 꺾임", "3시 방향으로 꺾임", "올라가는 계단 시작", "내려가는 계단 시작", "계단 끝", "횡단보도 건너기 시작", "횡단보도 건너기 끝", "문")
    class Jjak(val end: String, val kind: String, val up: String, val ride: Boolean)
    val PAIR = mapOf(
        "올라가는 계단 시작" to Jjak("계단 끝", "계단", "오르막", false),
        "내려가는 계단 시작" to Jjak("계단 끝", "계단", "내리막", false),
        "횡단보도 건너기 시작" to Jjak("횡단보도 건너기 끝", "횡단보도", "", false),
        "에스컬레이터 올라감" to Jjak("에스컬레이터 내림", "에스컬레이터", "오르막", true),
        "에스컬레이터 내려감" to Jjak("에스컬레이터 내림", "에스컬레이터", "내리막", true),
        "지하철 탐" to Jjak("지하철 내림", "지하철", "", true),
        "버스 탐" to Jjak("버스 내림", "버스", "", true)
    )
    /** 말로 남길 때 알아듣는 다른 말 — 아이폰 BYEOLCHING(웹 BYEOLCHING)과 같음 */
    val BYEOLCHING = mapOf(
        "우회전" to "3시 방향으로 꺾임", "오른쪽" to "3시 방향으로 꺾임", "오른편" to "3시 방향으로 꺾임", "오른쪽으로" to "3시 방향으로 꺾임",
        "좌회전" to "9시 방향으로 꺾임", "왼쪽" to "9시 방향으로 꺾임", "왼편" to "9시 방향으로 꺾임", "왼쪽으로" to "9시 방향으로 꺾임",
        // 2.10.1 방향은 시계 방향으로(이사장님 지시) — 「3시」, 「3시 방향」, 「3시 방향으로」 모두 알아들음
        "1시" to "1시 방향으로 꺾임", "2시" to "2시 방향으로 꺾임", "3시" to "3시 방향으로 꺾임", "4시" to "4시 방향으로 꺾임", "5시" to "5시 방향으로 꺾임",
        "7시" to "7시 방향으로 꺾임", "8시" to "8시 방향으로 꺾임", "9시" to "9시 방향으로 꺾임", "10시" to "10시 방향으로 꺾임", "11시" to "11시 방향으로 꺾임",
        "1시방향" to "1시 방향으로 꺾임", "2시방향" to "2시 방향으로 꺾임", "3시방향" to "3시 방향으로 꺾임", "4시방향" to "4시 방향으로 꺾임", "5시방향" to "5시 방향으로 꺾임",
        "7시방향" to "7시 방향으로 꺾임", "8시방향" to "8시 방향으로 꺾임", "9시방향" to "9시 방향으로 꺾임", "10시방향" to "10시 방향으로 꺾임", "11시방향" to "11시 방향으로 꺾임",
        "1시방향으로" to "1시 방향으로 꺾임", "2시방향으로" to "2시 방향으로 꺾임", "3시방향으로" to "3시 방향으로 꺾임", "4시방향으로" to "4시 방향으로 꺾임", "5시방향으로" to "5시 방향으로 꺾임",
        "7시방향으로" to "7시 방향으로 꺾임", "8시방향으로" to "8시 방향으로 꺾임", "9시방향으로" to "9시 방향으로 꺾임", "10시방향으로" to "10시 방향으로 꺾임", "11시방향으로" to "11시 방향으로 꺾임",
        "올라가는계단" to "올라가는 계단 시작", "오르막계단" to "올라가는 계단 시작", "계단올라감" to "올라가는 계단 시작", "계단시작" to "올라가는 계단 시작",
        "내려가는계단" to "내려가는 계단 시작", "내리막계단" to "내려가는 계단 시작", "계단내려감" to "내려가는 계단 시작",
        "계단끝" to "계단 끝", "계단끝남" to "계단 끝", "계단다" to "계단 끝",
        "턱" to "오름턱", "오르막턱" to "오름턱", "올라가는턱" to "오름턱", "내리막턱" to "내림턱", "내려가는턱" to "내림턱",
        "횡단보도" to "횡단보도 건너기 시작", "건널목" to "횡단보도 건너기 시작", "횡단보도시작" to "횡단보도 건너기 시작", "건너기시작" to "횡단보도 건너기 시작",
        "횡단보도끝" to "횡단보도 건너기 끝", "건널목끝" to "횡단보도 건너기 끝", "다건넜어" to "횡단보도 건너기 끝", "건너기끝" to "횡단보도 건너기 끝",
        "점자블록" to "점자블록 끊김", "블록끊김" to "점자블록 끊김", "점자블록끊김" to "점자블록 끊김",
        "문" to "문", "출입문" to "문", "입구" to "문",
        "엘리베이터" to "엘리베이터", "승강기" to "엘리베이터",
        "정류장" to "버스 정류장", "버스정류장" to "버스 정류장",
        "개찰구" to "지하철 개찰구", "조심" to "조심할 곳", "위험" to "조심할 곳", "조심할곳" to "조심할 곳",
        "에스컬레이터올라감" to "에스컬레이터 올라감", "에스컬레이터내려감" to "에스컬레이터 내려감", "에스컬레이터내림" to "에스컬레이터 내림",
        "지하철탐" to "지하철 탐", "지하철탔어" to "지하철 탐", "지하철내림" to "지하철 내림", "지하철내렸어" to "지하철 내림",
        "버스탐" to "버스 탐", "버스탔어" to "버스 탐", "버스내림" to "버스 내림", "버스내렸어" to "버스 내림"
    )

    /** 2.10.0 jari·malo — 말로 찍은 표시를 되물을 때 말한 순간의 자리 */
    class Mureum(val mal: String, val ne: String, val jariI: Int, val ttae: Long, val jari: JSONObject? = null, val malo: Boolean = false) {
        val danchu: String get() = "네 — $ne"
    }

    var sangtae = Sangtae.SWIM; private set
    var mureum: Mureum? = null; private set
    var allim = ""; private set
    var malDeutneun = false; private set
    /** 2.11.0 올리는 중 */
    var olliJung = false; private set
    /** 2.11.0 방금 마친 길(올리기 단추를 안내 바로 아래에) */
    var majimakId: String? = null; private set
    /** 2.11.0 꺾임 여쭙기 — 봉사자 요청으로 기본 끔 */
    fun kkeokMutgi(c: Context): Boolean = c.getSharedPreferences("jabong", Context.MODE_PRIVATE).getBoolean("jb.kkeokMutgi", false)
    fun kkeokMutgiKyeogi(c: Context, v: Boolean) { c.getSharedPreferences("jabong", Context.MODE_PRIVATE).edit().putBoolean("jb.kkeokMutgi", v).apply() }
    /** 화면을 다시 그릴 곳 */
    var bakkwim: (() -> Unit)? = null

    private var ctx: Context? = null
    private var gil: JSONObject? = null
    private var pts = JSONArray()
    private var marks = JSONArray()
    private var gs = JSONArray()
    /** 그린 길(새것이 앞) — 폰에 담아 둠 */
    var geurinGil = JSONArray(); private set
    private var sijakMs = 0L
    private var stGijun = 0
    private var meomchumSt0 = 0
    private var openPair: JSONObject? = null   // name, st, lat, lon, idx, t, pi
    private var rideMode = ""
    private var majimakMureum = 0L
    private var majimakKkeokim = 0L
    private var gilJari: Pair<Double, Double>? = null
    private var gilIreum = ""
    private var gilMutneunJung = false
    private val main = Handler(Looper.getMainLooper())
    private var tickR: Runnable? = null
    private var jeojangR: Runnable? = null

    private fun jigeumPail(): File? = ctx?.let { File(it.filesDir, "jabong_geurigi_jigeum.json") }
    private fun mokPail(): File? = ctx?.let { File(it.filesDir, "jabong_geurin_gil.json") }

    /** 앱이 켜질 때 — 그린 길 목록을 읽고, 그리다 꺼졌으면 멈춤으로 되살림 */
    fun junbi(c: Context) {
        if (ctx != null) return
        ctx = c.applicationContext
        try { mokPail()?.takeIf { it.exists() }?.let { geurinGil = JSONArray(it.readText()) } } catch (e: Exception) {}
        try {
            val f = jigeumPail()
            if (f != null && f.exists()) {
                val g = JSONObject(f.readText())
                if (!g.has("kkeut")) {
                    gil = g; pts = g.optJSONArray("pts") ?: JSONArray(); marks = g.optJSONArray("marks") ?: JSONArray(); gs = g.optJSONArray("gs") ?: JSONArray()
                    sijakMs = (g.optDouble("sijak_unix", System.currentTimeMillis() / 1000.0) * 1000).toLong()
                    sangtae = Sangtae.MEOMCHUM
                    allim = "그리던 길이 남아 있습니다. 다시 걷기를 누르시면 이어 그리고, 다 걸었습니다를 누르시면 여기까지로 마칩니다."
                }
            }
        } catch (e: Exception) {}
    }

    // MARK: 셈

    private val georeum: Int get() = maxOf(0, Wichi.georeumSu - stGijun)
    private val chobun: Int get() = ((System.currentTimeMillis() - sijakMs) / 1000).toInt()
    private fun r1(v: Double) = Math.round(v * 10) / 10.0

    private fun jigeumJari(): JSONObject {
        val p = JSONObject()
        p.put("t", chobun); p.put("st", georeum)
        if (Wichi.nachimban >= 0) p.put("h", r1(Wichi.nachimban))
        Wichi.jigeum?.let { w ->
            if (System.currentTimeMillis() - w.ttae < 15000) { p.put("lat", w.lat); p.put("lon", w.lon); p.put("acc", r1(w.ochae)) }
        }
        MomSensor.nopi?.let { p.put("ralt", r1(it)) }
        if (rideMode.isNotEmpty()) p.put("m", rideMode)
        if (MomSensor.dollyeo) {
            MomSensor.hapseong?.let { p.put("hy", r1(it)) }
            p.put("st2", MomSensor.georeumSu)
            p.put("st3", MomSensor.gamjiSu)
            p.put("dg", r1(MomSensor.nujeokDol))
            MomSensor.sangtae?.let { p.put("sa", it) }
        }
        // 걸음 빠르기(1초에 몇 걸음) — 아이폰은 만보기가 알려 주고, 안드로이드는 폰 걸음 센서로 최근 4초를 셈
        val n = pts.length()
        if (n >= 4) {
            val ap = pts.optJSONObject(n - 4)
            if (ap != null && !ap.has("cut")) {
                val sai = chobun - ap.optInt("t")
                if (sai > 0) p.put("cad", Math.round((georeum - ap.optInt("st")).toDouble() / sai * 100) / 100.0)
            }
        }
        return p
    }

    private fun momKyeogi() {
        val c = ctx ?: return
        MomSensor.georeumNal = { g -> if (sangtae == Sangtae.GEOREUM) gs.put(g.json()) }
        MomSensor.nachimbanNeogi(Wichi.nachimban)
        val n0 = if (gs.length() > 0) gs.getJSONObject(gs.length() - 1).optInt("n", 0) else 0
        var dol0 = 0.0
        for (i in pts.length() - 1 downTo 0) { val o = pts.getJSONObject(i); if (o.has("dg")) { dol0 = o.getDouble("dg"); break } }
        MomSensor.kyeogi(c, n0, dol0, (System.currentTimeMillis() - sijakMs) / 1000.0)
        JabongService.kyeogi(c)
    }

    // MARK: 시작·멈춤·끝

    /** 그리기 시작 — 보폭이 있어야 함 */
    fun sijak(chulbal: String = "") {
        val c = ctx ?: return
        if (!Seoljeong.bopokJaem || Seoljeong.bopok <= 0.2) {
            alrigi("먼저 보폭을 재 주십시오. 점지도의 걸음 수가 정확하려면 그리시는 분의 보폭이 꼭 있어야 합니다."); return
        }
        sijakMs = System.currentTimeMillis()
        pts = JSONArray(); marks = JSONArray(); gs = JSONArray()
        gil = JSONObject().apply {
            put("id", "JA" + sijakMs / 1000)
            put("sijak", sijakMs / 1000.0 - 978307200.0)      // 아이폰 기록과 같은 기준(2001년 1월 1일부터 초)
            put("sijak_unix", sijakMs / 1000.0)
            put("from", ""); put("to", "")
            put("bopok", Seoljeong.bopok); put("bopokMode", "jaem")
            put("beonho", JabongNae.beonho); put("georeum", 0); put("olim", false); put("meomchum", false)
            put("gigi", MomSensor.gigiJeongbo(c))
        }
        stGijun = Wichi.georeumSu
        openPair = null; rideMode = ""; mureum = null
        gilJari = null; gilIreum = ""
        majimakId = null
        val cb = chulbal.trim().take(40)
        if (cb.isNotEmpty()) gil?.put("from", cb)   // 2.11.0 봉사자가 넣은 출발지 이름이 먼저
        sangtae = Sangtae.GEOREUM
        dolligi()
        momKyeogi()
        Girok.namgi("jb_geurigi_sijak", mapOf("id" to gil?.optString("id")))
        alrigi("걷기 시작했습니다. 평소 걸음으로 걸으시고, 꺾이는 곳과 계단, 건널목, 문에 닿는 순간 표시를 남겨 주십시오.")
        // 출발한 자리 주소를 저절로 적음 — 2.11.0 봉사자가 이름을 넣었으면 그대로 둠
        val w = Wichi.jigeum
        if (w != null && cb.isEmpty()) {
            val id = gil?.optString("id") ?: ""
            JbTongsin.juso(w.lat, w.lon) { j ->
                if (j == null || gil?.optString("id") != id || (gil?.optString("from", "") ?: "").isNotEmpty()) return@juso
                gil?.put("from", j)
                jeojang()
                Sori.mal("출발한 자리는 ${j}입니다.", MalGeup.JEONGBO)
            }
        }
        jeojang()
    }

    fun jamkkan() {
        if (sangtae != Sangtae.GEOREUM) return
        Tomak.dakgi(); tomakDaegi = null
        sangtae = Sangtae.MEOMCHUM
        meomchumSt0 = Wichi.georeumSu
        mureum = null
        meomchugi()
        MomSensor.kkeugi()
        gil?.put("meomchum", true)
        jeojang()
        alrigi("잠깐 멈췄습니다. 이어 걸으실 때 다시 걷기를 눌러 주십시오. 멈춘 동안의 걸음은 세지 않습니다.")
    }

    fun dasiGeotgi() {
        if (sangtae != Sangtae.MEOMCHUM || gil == null) return
        // 멈춘 동안 센 걸음은 빼고, 앱이 다시 켜졌으면 저장해 둔 걸음에서 이어 셈
        stGijun = if (meomchumSt0 > 0) stGijun + maxOf(0, Wichi.georeumSu - meomchumSt0) else Wichi.georeumSu - (gil?.optInt("georeum", 0) ?: 0)
        meomchumSt0 = 0
        sangtae = Sangtae.GEOREUM
        gil?.put("meomchum", false)
        val p = jigeumJari(); p.put("cut", 1)   // 멈췄다 이은 자리 — 이 사이는 이어 그리지 않음(웹과 같음)
        pts.put(p)
        dolligi()
        momKyeogi()
        alrigi("다시 걷습니다. 지금까지 ${georeum}걸음입니다.")
    }

    /** 다 걸었습니다 */
    fun kkeut() {
        Tomak.dakgi(); tomakDaegi = null
        val g = gil ?: return
        meomchugi()
        if (sangtae == Sangtae.GEOREUM) { pts.put(jigeumJari()); g.put("georeum", georeum) }
        MomSensor.kkeugi()
        JabongService.kkeugi(ctx)
        g.put("kkeut", System.currentTimeMillis() / 1000.0 - 978307200.0)
        g.put("meomchum", false)
        g.put("pts", pts); g.put("marks", marks); g.put("gs", gs)
        sangtae = Sangtae.SWIM
        mureum = null
        val gr = g.optInt("georeum", 0)
        val geori = (gr * g.optDouble("bopok", Seoljeong.bopok)).toInt()
        var mal = "걷기를 마쳤습니다. ${gr}걸음, 약 ${geori}미터, 표시 ${marks.length()}개입니다."
        // 두 걸음 견주기 — 폰 걸음 센서 걸음과 몸 센서(가속도) 걸음이 많이 다르면 알림(1미터 원칙)
        val n2 = if (gs.length() > 0) gs.getJSONObject(gs.length() - 1).optInt("n", 0) else 0
        if (n2 > 0 && gr > 20) {
            val cha = abs(n2 - gr).toDouble() / gr
            mal += if (cha <= 0.1) " 몸 센서로 센 걸음도 ${n2}걸음으로 잘 맞습니다." else " 몸 센서로 센 걸음은 ${n2}걸음이라 차이가 큽니다. 올리기 전 점검에서 살펴보겠습니다."
        }
        openPair?.let { o -> mal += " ${o.optString("name")}의 짝인 ${PAIR[o.optString("name")]?.end ?: "끝"} 표시가 없습니다. 올리기 전 점검에서 다시 여쭙겠습니다." }
        mal += " 그린 길은 폰에 담아 두었습니다. 바로 아래 방금 그린 길 올리기를 누르시면 협회 점검을 거쳐 길눈에 실립니다."
        openPair = null; rideMode = ""
        val sae = JSONArray(); sae.put(g); for (i in 0 until geurinGil.length()) sae.put(geurinGil.get(i))
        geurinGil = sae
        mokJeojang()
        majimakId = g.optString("id")   // 2.11.0 함께한 기록판은 올린 길만 셈 — 올리기가 되면 그때 알림
        try { jigeumPail()?.delete() } catch (e: Exception) {}
        Girok.namgi("jb_geurigi_kkeut", mapOf("id" to g.optString("id"), "georeum" to gr, "georeum2" to n2, "pyosi" to marks.length(), "jari" to pts.length()))
        gil = null
        alrigi(mal)
        // 도착한 자리 주소를 저절로 적음
        val w = Wichi.jigeum
        if (w != null) {
            val id = g.optString("id")
            JbTongsin.juso(w.lat, w.lon) { j ->
                if (j == null) return@juso
                for (i in 0 until geurinGil.length()) {
                    val o = geurinGil.optJSONObject(i) ?: continue
                    if (o.optString("id") == id) { if (o.optString("to", "").isEmpty()) o.put("to", j); break }
                }
                mokJeojang()
                Sori.mal("도착한 자리는 ${j}입니다.", MalGeup.JEONGBO)
                bakkwim?.invoke()
            }
        }
    }

    fun geurinGilSu(): Int = geurinGil.length()

    // MARK: 돌리기

    private fun dolligi() {
        meomchugi()
        val t = object : Runnable { override fun run() { tick(); main.postDelayed(this, 1000) } }
        tickR = t; main.postDelayed(t, 1000)
        val j = object : Runnable { override fun run() { jeojang(); main.postDelayed(this, 60000) } }
        jeojangR = j; main.postDelayed(j, 60000)
    }

    private fun meomchugi() {
        tickR?.let { main.removeCallbacks(it) }; tickR = null
        jeojangR?.let { main.removeCallbacks(it) }; jeojangR = null
    }

    /** 1초마다 한 자리 */
    private fun tick() {
        if (sangtae != Sangtae.GEOREUM || gil == null) return
        MomSensor.nachimbanNeogi(Wichi.nachimban)
        MomSensor.sangtaeBoda()
        val p = jigeumJari()
        pts.put(p)
        gil?.put("georeum", p.optInt("st"))
        // 물음은 20초 지나면 거둠
        mureum?.let { if (System.currentTimeMillis() - it.ttae > 20000) { mureum = null; bakkwim?.invoke() } }
        // 2.11.0 계단 여쭙기 없앰, 꺾임 여쭙기는 켰을 때만
        if (rideMode.isEmpty() && ctx?.let { kkeokMutgi(it) } == true) kkeokimBoda()
        if (pts.length() % 15 == 0) gilBoda()
    }

    // MARK: 폰이 먼저 여쭘 — 아이폰과 같음(꺾임은 자이로 각도로, 자이로가 없으면 나침반으로)

    /** 방향이 크게 바뀌면 — 꺾이셨습니까 */
    private fun kkeokimBoda() {
        val n = pts.length()
        val now = System.currentTimeMillis()
        if (n < 10 || mureum != null || now - majimakMureum < 15000 || now - majimakKkeokim < 10000) return
        val a = pts.getJSONObject(n - 10); val b = pts.getJSONObject(n - 1)
        if (b.optInt("st") - a.optInt("st") < 5) return
        val p0 = pts.getJSONObject(n - 8); val pm = pts.getJSONObject(n - 4)
        val d: Double
        if (p0.has("dg") && b.has("dg") && pm.has("dg")) {
            d = b.getDouble("dg") - p0.getDouble("dg")
            // 이미 돌고 난 뒤 4초 동안 또 돌고 있으면 아직 도는 중 — 다 돈 뒤에 여쭘
            if (abs(b.getDouble("dg") - pm.getDouble("dg")) >= abs(d) * 0.7) return
        } else {
            val ap = (n - 10 until n - 6).mapNotNull { i -> pts.getJSONObject(i).let { if (it.has("h")) it.getDouble("h") else null } }
            val dwi = (n - 3 until n).mapNotNull { i -> pts.getJSONObject(i).let { if (it.has("h")) it.getDouble("h") else null } }
            if (ap.size < 3 || dwi.size < 2) return
            d = gakCha(pyeonggyun(ap), pyeonggyun(dwi))
        }
        if (abs(d) < 55) return
        yeojjum("${sigye(d)}시 방향으로 꺾이셨습니까?", "${sigye(d)}시 방향으로 꺾임", n - 6)
    }

    /** 여쭙고, 말소리가 끝나면 네·아니오를 한 번 들음 — 단추로도 답할 수 있음 */
    private fun yeojjum(mal: String, ne: String, jariI: Int, jari: JSONObject? = null, malo: Boolean = false) {
        majimakMureum = System.currentTimeMillis()
        Tomak.dakgi()   // 2.10.0 토막을 듣던 귀는 닫고 여쭘
        tomakDaegi = null
        mureum = Mureum(mal, ne, maxOf(0, jariI), majimakMureum, jari, malo)
        Girok.namgi("jb_mureum", mapOf("ne" to ne))
        bakkwim?.invoke()
        Sori.mal(mal, MalGeup.ANNAE) { neDeutgi() }
    }

    private fun neDeutgi() {
        val c = ctx ?: return
        if (mureum == null || sangtae != Sangtae.GEOREUM || malDeutneun || Tomak.nokeumJung) return
        if (!MalDeutgi.heorakItda(c) || !MalDeutgi.sseulSuItda(c)) return
        malDeutneun = true
        Sori.deutgiSijak()
        val ok = MalDeutgi.deutgi(c, 5) { alts, _ ->
            malDeutneun = false
            Sori.deutgiKkeut()
            if (mureum == null) return@deutgi
            val z = alts.map { MalSajeon.ttuk(it) }
            if (z.any { it.startsWith("아니") || it.startsWith("아뇨") || it == "no" }) { dap(false); return@deutgi }
            if (z.any { it.startsWith("네") || it.startsWith("예") || it.startsWith("응") || it.startsWith("맞") || it.startsWith("그래") }) { dap(true); return@deutgi }
            bakkwim?.invoke()
        }
        if (!ok) { malDeutneun = false; Sori.deutgiKkeut() }
    }

    /** 물음에 답함 */
    fun dap(ne: Boolean) {
        val m = mureum ?: return
        mureum = null
        if (malDeutneun) { MalDeutgi.meomchugi(); malDeutneun = false; Sori.deutgiKkeut() }
        if (ne) {
            val jari = m.jari ?: if (m.jariI < pts.length()) pts.getJSONObject(m.jariI) else null
            pyosi(m.ne, jari, malo = m.malo, mu = !m.malo)
        } else {
            Girok.namgi("jb_mureum_ani", mapOf("ne" to m.ne))
            alrigi("알겠습니다. 남기지 않았습니다.")
        }
    }

    // MARK: 표시

    /** 표시 남기기 — jari 가 있으면 그 자리(물음·말로 표시), 없으면 지금 자리 */
    fun pyosi(name: String, jari: JSONObject? = null, malo: Boolean = false, mu: Boolean = false) {
        if (gil == null) { alrigi("걷기를 시작한 뒤에 눌러 주십시오."); return }
        if (sangtae != Sangtae.GEOREUM) { alrigi("지금은 잠깐 멈춤입니다. 다시 걷기를 먼저 눌러 주십시오."); return }
        val p = jari ?: jigeumJari()
        val m = JSONObject()
        m.put("t", p.optInt("t")); m.put("st", p.optInt("st")); m.put("name", name)
        for (k in listOf("h", "lat", "lon", "acc", "hy", "st2", "dg", "ralt")) if (p.has(k)) m.put(k, p.get(k))
        if (malo) m.put("malo", true)
        if (mu) m.put("mureum", true)
        if (name.endsWith("꺾임")) majimakKkeokim = System.currentTimeMillis()
        mureum = null
        val st = m.optInt("st")
        Tomak.dakgi()                 // 2.10.0 앞 표시의 토막을 듣는 중이면 닫음
        tomakDaegi = name to st       // 안내 말이 끝나면 짧게 귀를 엶(alrigi 가 이어 받음)

        // 시작 표시면 짝을 열어 둠
        val jj = PAIR[name]
        if (jj != null) {
            openPair = JSONObject().apply {
                put("name", name); put("st", st); put("t", m.optInt("t")); put("idx", marks.length()); put("pi", pts.length())
                if (m.has("lat")) { put("lat", m.get("lat")); put("lon", m.get("lon")) }
            }
            m.put("kind", jj.kind); if (jj.up.isNotEmpty()) m.put("up", jj.up)
            if (jj.ride) { m.put("ride", true); rideMode = jj.kind }
            marks.put(m); jeojang()
            alrigi(name + "을 남겼습니다. " + if (jj.ride) "내리실 때 ${jj.end}을 눌러 주십시오. 타고 가시는 동안은 걸음으로 재지 않습니다." else "끝나는 곳에서 ${jj.end}을 눌러 주십시오.")
            return
        }
        val o = openPair
        // 탈것에서 내림
        if (name == "에스컬레이터 내림" || name == "지하철 내림" || name == "버스 내림") {
            if (o != null && PAIR[o.optString("name")]?.ride == true) {
                val kk = PAIR[o.optString("name")]?.kind ?: ""
                val secs = m.optInt("t") - o.optInt("t")
                val dd = geori(o, m)
                marks.optJSONObject(o.optInt("idx"))?.apply { put("secs", secs); put("dist", dd); put("ride", true) }
                m.put("pairOf", o.optString("name")); m.put("ride", true)
                marks.put(m); rideMode = ""; openPair = null; jeojang()
                var mal = kk + "에서 내리셨습니다. "
                mal += if (secs >= 60) "${secs / 60}분 ${secs % 60}초" else "${secs}초"
                mal += " 타셨고, " + (if (dd >= 1000) String.format(Locale.US, "%.1f킬로미터", dd / 1000.0) else "${dd}미터")
                mal += " 오셨습니다. 이 구간은 걸음으로 재지 않고 그대로 적었습니다."
                alrigi(mal)
                return
            }
            rideMode = ""; marks.put(m); jeojang()
            alrigi(name + "을 눌렀으나 탄 자리가 없습니다. 그냥 표시로만 남깁니다."); return
        }
        // 끝 표시면 그 사이 걸음과 거리를 셈해 시작 표시에 적음
        if (name == "계단 끝" || name == "횡단보도 건너기 끝") {
            if (o != null && PAIR[o.optString("name")]?.ride != true) {
                val n = st - o.optInt("st")
                val d = geori(o, m)
                marks.optJSONObject(o.optInt("idx"))?.apply { put("cnt", n); put("dist", d) }
                m.put("pairOf", o.optString("name"))
                marks.put(m); openPair = null; jeojang()
                alrigi(if (PAIR[o.optString("name")]?.kind == "계단") "${PAIR[o.optString("name")]?.up ?: ""} 계단이 ${n}칸입니다. 이대로 적습니다. 틀리면 계단 칸수 고치기를 눌러 주십시오."
                       else "횡단보도를 ${n}걸음, 약 ${d}미터 건너셨습니다. 그대로 적었습니다.")
                return
            }
            marks.put(m); jeojang()
            alrigi(name + "을 눌렀으나 시작 표시가 없습니다. 그냥 표시로만 남깁니다."); return
        }
        marks.put(m); jeojang()
        // 문은 딱 찍고 두 걸음 앞에서 한 번 더 — 걸음과 거리로 가림(웹과 같음)
        if (name == "문") {
            var jjak: JSONObject? = null
            for (i in marks.length() - 2 downTo 0) { val x = marks.optJSONObject(i) ?: continue; if (x.optString("name") == "문") { jjak = x; break } }
            var doem = false
            val jk = jjak
            if (jk != null && m.has("lat") && jk.has("lat")) {
                val dd2 = Wichi.geori(m.getDouble("lat"), m.getDouble("lon"), jk.getDouble("lat"), jk.getDouble("lon"))
                if (dd2 >= 0.3 && dd2 <= 3.5 && (st - jk.optInt("st")) <= 5 && (m.optInt("t") - jk.optInt("t")) <= 20) doem = true
            } else if (jk != null && (st - jk.optInt("st")) >= 1 && (st - jk.optInt("st")) <= 5 && (m.optInt("t") - jk.optInt("t")) <= 20) {
                doem = true   // 위성이 없는 안쪽 — 걸음으로만 가림
            }
            if (doem) {
                alrigi(if ((jk?.optInt("st") ?: 0) <= 10) "나오시는 문을 두 번 찍으셨습니다. 되돌아오실 때 이 문 앞으로 안내됩니다."
                       else "도착하시는 문을 두 번 찍으셨습니다. 이 문은 확실한 문으로 남고, 들어가는 방향까지 함께 남습니다.")
            } else {
                tomakDaegi = null   // 문은 두 번째로 찍었을 때만 귀를 엶
                alrigi("문을 남겼습니다. 곧바로 두 걸음 앞으로 가서 문을 한 번 더 눌러 주십시오. 딱 찍고 두 걸음 뒤에 또 찍으셔야 문이 됩니다.")
            }
            return
        }
        alrigi("${name}을 남겼습니다. 지금까지 ${st}걸음, 표시 ${marks.length()}개입니다.")
    }

    /** 마지막 계단의 칸수(없으면 null) */
    val majimakGyedan: Int?
        get() {
            for (i in marks.length() - 1 downTo 0) {
                val x = marks.optJSONObject(i) ?: continue
                if (x.optString("kind") == "계단" && x.has("cnt")) return x.optInt("cnt")
            }
            return null
        }

    /** 계단 칸수 고치기 — 마지막 계단의 칸수를 바꿈 */
    fun gyedanGochigi(n: Int) {
        for (i in marks.length() - 1 downTo 0) {
            val x = marks.optJSONObject(i) ?: continue
            if (x.optString("kind") == "계단" && x.has("cnt")) {
                x.put("cnt", n)
                jeojang()
                alrigi("계단을 ${n}칸으로 고쳤습니다.")
                return
            }
        }
        alrigi("고칠 계단이 없습니다.")
    }

    /** 말로 표시 — 누른 순간의 자리를 잡고, 말씀이 끝나면 이름을 붙임(마이크 허락은 화면이 먼저 여쭘) */
    fun malloPyosi() {
        val c = ctx ?: return
        if (sangtae != Sangtae.GEOREUM) { alrigi("걷기를 시작한 뒤에 말씀해 주십시오."); return }
        if (!MalDeutgi.heorakItda(c)) { alrigi("말로 표시하려면 마이크와 음성 인식 허락이 필요합니다."); return }
        if (malDeutneun) return
        Tomak.dakgi(); tomakDaegi = null
        val jari = jigeumJari()
        malDeutneun = true
        bakkwim?.invoke()
        Sori.deutgiSijak()
        Eum.naegi(EumJong.DINGDONG)
        main.postDelayed({
            val ok = MalDeutgi.deutgi(c, 6) { alts, _ ->
                malDeutneun = false
                Sori.deutgiKkeut()
                val ireum = malChatgi(alts)
                if (ireum != null) {
                    // 2.10.0 말로 찍은 표시는 되물어 네라고 하셔야 찍힘(받아쓰기가 틀릴 수 있어서)
                    yeojjum("$ireum, 이대로 남길까요?", ireum, 0, jari, malo = true)
                } else {
                    // 2.11.0 단추에 없는 말도 말한 그대로 표시로 — 되물어 네라고 하셔야 남음
                    val t = (alts.firstOrNull() ?: "").trim().take(20)
                    Girok.namgi("jb_malpyosi_jayu", mapOf("mal" to t))
                    if (t.isEmpty()) alrigi("말씀이 들리지 않았습니다. 다시 말로 표시를 눌러 주십시오.")
                    else yeojjum("$t, 이대로 남길까요?", t, 0, jari, malo = true)
                }
            }
            if (!ok) { malDeutneun = false; Sori.deutgiKkeut(); alrigi("지금은 마이크를 열지 못했습니다. 단추로 남겨 주십시오.") }
        }, 350)
    }

    fun malChatgi(alts: List<String>): String? {
        for (a in alts) {
            val z = MalSajeon.ttuk(a)
            if (z.isEmpty()) continue
            MARKS.firstOrNull { MalSajeon.ttuk(it) == z }?.let { return it }
            BYEOLCHING[z]?.let { return it }
            // 긴 말 안에 든 것 — 긴 이름부터
            for (k in BYEOLCHING.keys.sortedByDescending { it.length }) if (k.length >= 2 && z.contains(k)) return BYEOLCHING[k]
        }
        return null
    }

    // MARK: 지금 어디쯤 — 길을 접어들 때

    private fun gilBoda() {
        val w = Wichi.jigeum ?: return
        if (gilMutneunJung || w.ochae > 30) return
        val j = gilJari
        if (j != null && Wichi.geori(j.first, j.second, w.lat, w.lon) < 40) return
        gilJari = Pair(w.lat, w.lon)
        gilMutneunJung = true
        JbTongsin.gil(w.lat, w.lon) { r ->
            gilMutneunJung = false
            if (sangtae != Sangtae.GEOREUM || r == null || r.first.isEmpty() || r.first == gilIreum) return@gil
            val cheot = gilIreum.isEmpty()
            gilIreum = r.first
            Sori.mal(if (cheot) "지금 ${r.second} ${r.first}입니다." else "${r.first}에 접어드셨습니다.", MalGeup.JEONGBO)
        }
    }

    // MARK: 지금 상태 듣기

    /** 2.11.0 말소리를 꺼 두셔도 화면 글자로(톡백이 읽음) */
    fun sangtaeAllim() { alrigi(sangtaeMal()) }

    fun sangtaeMal(): String = when (sangtae) {
        Sangtae.SWIM -> if (geurinGil.length() == 0) "아직 그린 길이 없습니다." else "그린 길이 ${geurinGil.length()}개 폰에 담겨 있습니다."
        Sangtae.MEOMCHUM -> "잠깐 멈춤입니다. 지금까지 ${gil?.optInt("georeum", 0) ?: 0}걸음, 표시 ${marks.length()}개입니다."
        Sangtae.GEOREUM -> {
            var m = "그리는 중입니다. ${chobun / 60}분 ${chobun % 60}초 동안 ${georeum}걸음, 약 ${(georeum * Seoljeong.bopok).toInt()}미터, 표시 ${marks.length()}개입니다."
            openPair?.let { m += " ${it.optString("name")} 뒤에 ${PAIR[it.optString("name")]?.end ?: "끝"}을 아직 남기지 않으셨습니다." }
            Wichi.jigeum?.let { w -> m += if (w.ochae <= 15) " 위성이 잘 잡혀 있습니다." else " 위성이 흐려 걸음으로 이어 셉니다." }
            if (MomSensor.dollyeo) m += " 몸 센서로 센 걸음은 ${MomSensor.georeumSu}걸음입니다."
            m
        }
    }

    /** 그린 길 한 줄 — 아이폰 gilJul 과 같은 말 */
    fun gilJul(g: JSONObject): String {
        val sijakMs = if (g.has("sijak_unix")) (g.optDouble("sijak_unix") * 1000).toLong() else ((g.optDouble("sijak") + 978307200.0) * 1000).toLong()
        val f = SimpleDateFormat("M월 d일 H시 m분", Locale.KOREA)
        val gr = g.optInt("georeum", 0)
        val geori = (gr * g.optDouble("bopok", Seoljeong.bopok)).toInt()
        val from = g.optString("from", ""); val to = g.optString("to", "")
        val eodi = if (from.isEmpty()) "" else " ${from}에서" + (if (to.isEmpty()) "" else " ${to}까지")
        val ms = g.optJSONArray("marks")?.length() ?: 0
        val sang = if (g.optBoolean("olim", false)) ", 올림" else if ((g.optJSONArray("heum")?.length() ?: 0) > 0) ", 고칠 곳 있음" else ", 올리기 전"
        return "${f.format(Date(sijakMs))}$eodi, ${gr}걸음 약 ${geori}미터, 표시 ${ms}개" + sang
    }

    // MARK: 2.11.0 올리기·지우기·이름 고치기

    fun gilChatgi(id: String): JSONObject? {
        for (i in 0 until geurinGil.length()) { val o = geurinGil.optJSONObject(i) ?: continue; if (o.optString("id") == id) return o }
        return null
    }

    /** 출발지·도착지 이름 고치기 — id 가 없으면 그리는 중인 길 */
    fun ireumGochigi(id: String?, chulbal: String, dochak: String) {
        val c = chulbal.trim().take(40); val d = dochak.trim().take(40)
        if (id != null) {
            val o = gilChatgi(id) ?: return
            if (c.isNotEmpty()) o.put("from", c)
            if (d.isNotEmpty()) o.put("to", d)
            mokJeojang()
        } else {
            val g = gil ?: return
            if (c.isNotEmpty()) g.put("from", c)
            if (d.isNotEmpty()) g.put("to", d)
            jeojang()
        }
        alrigi("이름을 적었습니다." + (if (c.isEmpty()) "" else " 출발지 ${c}.") + (if (d.isEmpty()) "" else " 도착지 ${d}."))
    }

    /** 그리는 중인 길의 출발지 이름 */
    val gilFrom: String get() = gil?.optString("from", "") ?: ""

    /** 그린 길 지우기 — 폰 안의 길과 그 길의 목소리 토막만(올린 길은 협회 창고에 남음) */
    fun jiugi(id: String) {
        val c = ctx ?: return
        val sae = JSONArray(); var jium: JSONObject? = null
        for (i in 0 until geurinGil.length()) { val o = geurinGil.optJSONObject(i) ?: continue; if (o.optString("id") == id) jium = o else sae.put(o) }
        val j = jium ?: return
        geurinGil = sae
        Tomak.gilJiugi(c, id)
        if (majimakId == id) majimakId = null
        mokJeojang()
        Girok.namgi("jb_gil_jium", mapOf("id" to id, "olim" to j.optBoolean("olim", false)))
        alrigi("그린 길을 지웠습니다." + if (j.optBoolean("olim", false)) " 협회에 올린 길은 협회 창고에 그대로 남습니다." else "")
    }

    /** 그린 길 올리기 — 협회 서버가 걸음 잣대로 점검해 통과한 길만 점지도 창고에 넣음 */
    fun olligi(id: String) {
        val c = ctx ?: return
        if (olliJung) return
        val g = gilChatgi(id) ?: return
        if (g.optBoolean("olim", false)) { alrigi("이미 올린 길입니다. 길눈에 실려 있습니다."); return }
        olliJung = true
        alrigi("길을 협회로 올립니다. 협회 점검까지 잠시 걸립니다.")
        val bon = bonmun(c, g)
        Thread {
            val t = JbTongsin.postText("jeom_olligi.php", emptyMap(), bon, 90000)
            val r = try { if (t == null) null else JSONObject(t) } catch (e: Exception) { null }
            main.post { olligiDap(id, g, r) }
        }.start()
    }

    private fun olligiDap(id: String, g: JSONObject, r: JSONObject?) {
        olliJung = false
        val o = gilChatgi(id) ?: run { bakkwim?.invoke(); return }
        if (r == null) { alrigi("올리지 못했습니다. 통신을 확인하시고 잠시 뒤 다시 올려 주십시오. 그린 길은 폰에 그대로 있습니다."); return }
        if (!r.optBoolean("ok", false)) { alrigi(r.optString("msg", "올리지 못했습니다.") + " 그린 길은 폰에 그대로 있습니다."); return }
        val heum = r.optJSONArray("heum") ?: JSONArray()
        val mals = JSONArray(); var ppal = 0
        for (i in 0 until heum.length()) { val h = heum.optJSONObject(i) ?: continue; val m = h.optString("mal"); if (m.isNotEmpty()) mals.put(m); if (h.optString("saek") == "빨강") ppal++ }
        val malMok = (0 until mals.length()).joinToString(" ") { mals.optString(it) }
        if (r.optBoolean("olim", false)) {
            o.put("olim", true); o.put("seobeoId", r.optString("id")); if (mals.length() > 0) o.put("heum", mals) else o.remove("heum")
            mokJeojang()
            if (majimakId == id) majimakId = null
            Hamkke.geurimAllim(g.optString("id"), (g.optInt("georeum", 0) * g.optDouble("bopok", Seoljeong.bopok)).toInt())   // 함께한 기록판은 올린 길만 셈
            Girok.namgi("jb_olim", mapOf("id" to id, "seobeo" to r.optString("id")))
            alrigi("올렸습니다. 협회 점검을 통과해 길눈에 실렸습니다. 고맙습니다." + if (mals.length() > 0) " 다음에 손보시면 좋을 곳도 알려 드립니다. $malMok" else "")
        } else {
            o.put("heum", mals); mokJeojang()
            Girok.namgi("jb_olim_heum", mapOf("id" to id, "su" to ppal))
            alrigi("협회 점검에서 고칠 곳이 ${maxOf(ppal, 1)}가지 나와 아직 올리지 않았습니다. $malMok 이름이 빠진 것은 이 화면에서 바로 넣고 다시 올리시면 되고, 걸음이나 표시가 빠진 구간은 다시 걸어 새로 그려 주십시오.")
        }
    }

    /** 올리기 본문 — 웹 길 보관소(jeom.php save)와 같은 모양에 목소리 토막(소리 b64)·몸 센서 기록을 붙임 */
    private fun bonmun(c: Context, g: JSONObject): String {
        val sori = JSONArray()
        val sr = g.optJSONArray("sori") ?: JSONArray()
        for (i in 0 until sr.length()) {
            val tm = sr.optJSONObject(i) ?: continue
            val o = JSONObject().put("st", tm.optInt("st")).put("cho", tm.optDouble("cho")).put("pyosi", tm.optString("pyosi")).put("t", tm.optInt("t")).put("pail", tm.optString("pail"))
            try {
                val f = File(Tomak.pyeolDae(c), tm.optString("pail"))
                if (f.exists()) o.put("b64", android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP))
            } catch (e: Exception) {}
            sori.put(o)
        }
        val sijak = if (g.has("sijak_unix")) g.optDouble("sijak_unix") else g.optDouble("sijak") + 978307200.0
        val kkeut = if (g.has("kkeut")) g.optDouble("kkeut") + 978307200.0 else sijak
        val from = g.optString("from", ""); val to = g.optString("to", "")
        val b = JSONObject()
        b.put("app_id", g.optString("id"))
        b.put("title", if (from.isEmpty() || to.isEmpty()) "" else "$from → $to")
        b.put("from", from); b.put("to", to)
        b.put("who", JabongNae.ireum); b.put("who_kind", "jabong"); b.put("jabong", g.optString("beonho"))
        b.put("secs", (kkeut - sijak).toInt()); b.put("steps", g.optInt("georeum", 0)); b.put("stride", g.optDouble("bopok", Seoljeong.bopok))
        b.put("bopokMode", g.optString("bopokMode", "jaem"))
        b.put("pts", g.optJSONArray("pts") ?: JSONArray()); b.put("marks", g.optJSONArray("marks") ?: JSONArray())
        b.put("gs", g.optJSONArray("gs") ?: JSONArray()); b.put("sori", sori)
        b.put("app", "android-jabong-" + JabongPan.pan)
        return b.toString()
    }

    // MARK: 저장

    fun jeojang() {
        val g = gil ?: return
        try {
            g.put("pts", pts); g.put("marks", marks); g.put("gs", gs)
            jigeumPail()?.writeText(g.toString())
        } catch (e: Exception) {}
    }

    private fun mokJeojang() {
        try { mokPail()?.writeText(geurinGil.toString()) } catch (e: Exception) {}
    }

    private fun alrigi(t: String) {
        allim = t
        val td = tomakDaegi
        tomakDaegi = null
        if (td != null) Sori.mal(t, MalGeup.ANNAE) { tomakYeolgi(td.first, td.second) } else Sori.mal(t, MalGeup.ANNAE)
        bakkwim?.invoke()
    }

    /** 2.10.0 표시를 남긴 뒤 짧게 담을 차례(표시 이름, 걸음 자리) */
    private var tomakDaegi: Pair<String, Int>? = null

    /** 2.10.0 표시 안내 말이 끝나면 짧게 귀를 열어 그 자리 모습을 담음(약속 일곱) */
    private fun tomakYeolgi(name: String, st: Int) {
        val c = ctx ?: return
        val id = gil?.optString("id") ?: return
        if (!Tomak.kyeojim(c) || sangtae != Sangtae.GEOREUM || mureum != null || malDeutneun) return
        val majimak = if (marks.length() > 0) marks.optJSONObject(marks.length() - 1) else null
        if (majimak?.optInt("st") != st) return
        val t = chobun
        Eum.naegi(EumJong.DINGDONG)
        main.postDelayed({
            Tomak.yeolgi(c, st, name, id, t) { tm ->
                if (tm == null || gil?.optString("id") != id) return@yeolgi
                val g = gil ?: return@yeolgi
                val arr = g.optJSONArray("sori") ?: org.json.JSONArray().also { g.put("sori", it) }
                arr.put(tm)
                jeojang()
                Jindong.hagi("arrive")
                Girok.namgi("jb_tomak", mapOf("st" to st, "cho" to tm.optDouble("cho")))
            }
        }, 450)
    }

    // MARK: 작은 셈

    private fun geori(a: JSONObject, b: JSONObject): Int {
        if (!a.has("lat") || !b.has("lat")) return 0
        return Wichi.geori(a.getDouble("lat"), a.getDouble("lon"), b.getDouble("lat"), b.getDouble("lon")).roundToInt()
    }

    /** 각도들의 평균(0~360, 북쪽 근처에서 섞여도 바르게) */
    private fun pyeonggyun(gs: List<Double>): Double {
        val p = Math.PI / 180
        val x = gs.sumOf { cos(it * p) }
        val y = gs.sumOf { sin(it * p) }
        var d = atan2(y, x) / p
        if (d < 0) d += 360.0
        return d
    }

    /** a 에서 b 로 돈 각도(-180~180, 오른쪽이 +) */
    /** 2.10.1 돈 각도(오른쪽 +) → 시계 방향 시(1~11, 12와 6은 피함 — 꺾임이므로) */
    private fun sigye(d: Double): Int {
        var s = Math.round(d / 30).toInt()
        if (s == 0) s = if (d > 0) 1 else -1
        if (s >= 6) s = 5 else if (s <= -6) s = -5
        return if (s > 0) s else 12 + s
    }

    private fun gakCha(a: Double, b: Double): Double {
        var d = (b - a) % 360
        if (d > 180) d -= 360
        if (d < -180) d += 360
        return d
    }
}
