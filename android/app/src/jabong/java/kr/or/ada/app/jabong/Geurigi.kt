// 안드로이드 자봉 — 점지도 그리기 엔진 (2.3.0, 빌드 261001-A2, 대표님 지시 2026-10-01)
// 아이폰 자봉 JabongGeurigi.swift 와 같은 기록 모양(1초 줄 pts, 표시 marks, 걸음 줄 gs, 기기 정보 gigi)으로 그립니다.
// 웹 jeom_rec.js 를 걷어 내고 속까지 앱 — 화면이 꺼져도 알림 칸의 자봉(JabongService)이 붙들어 이어 그림.
// 1분마다, 표시를 남길 때마다 저절로 저장. 앱이 꺼졌다 켜지면 그리던 길을 잠깐 멈춤으로 되살림.
package kr.or.ada.app.jabong

import android.content.Context
import android.os.Handler
import android.os.Looper
import kr.or.ada.app.gilnun.Girok
import kr.or.ada.app.gilnun.MalGeup
import kr.or.ada.app.gilnun.MomSensor
import kr.or.ada.app.gilnun.Seoljeong
import kr.or.ada.app.gilnun.Sori
import kr.or.ada.app.gilnun.Wichi
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

object Geurigi {
    enum class Sangtae { SWIM, GEOREUM, MEOMCHUM }

    /** 표시 스물두 가지 — 아이폰·웹과 같음 */
    val MARKS = listOf("올라가는 계단 시작", "내려가는 계단 시작", "계단 끝", "오름턱", "내림턱", "횡단보도 건너기 시작", "횡단보도 건너기 끝",
        "왼쪽으로 꺾임", "오른쪽으로 꺾임", "점자블록 끊김", "문", "엘리베이터", "버스 정류장", "지하철 개찰구", "조심할 곳",
        "에스컬레이터 올라감", "에스컬레이터 내려감", "에스컬레이터 내림", "지하철 탐", "지하철 내림", "버스 탐", "버스 내림")
    val JAJU = listOf("왼쪽으로 꺾임", "오른쪽으로 꺾임", "올라가는 계단 시작", "내려가는 계단 시작", "계단 끝", "횡단보도 건너기 시작", "횡단보도 건너기 끝", "문")
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

    class Mureum(val mal: String, val ne: String, val jariI: Int, val ttae: Long)

    var sangtae = Sangtae.SWIM; private set
    var mureum: Mureum? = null; private set
    var allim = ""; private set
    /** 화면을 다시 그릴 곳 */
    var bakkwim: (() -> Unit)? = null

    private var ctx: Context? = null
    private var gil: JSONObject? = null
    private var pts = JSONArray()
    private var marks = JSONArray()
    private var gs = JSONArray()
    private var sijakMs = 0L
    private var stGijun = 0
    private var meomchumSt0 = 0
    private var openPair: JSONObject? = null   // name, st, lat, lon, idx, t
    private var rideMode = ""
    private var majimakMureum = 0L
    private var majimakKkeokim = 0L
    private val main = Handler(Looper.getMainLooper())
    private var tickR: Runnable? = null
    private var jeojangR: Runnable? = null

    private fun jigeumPail(): File? = ctx?.let { File(it.filesDir, "jabong_geurigi_jigeum.json") }
    private fun mokPail(): File? = ctx?.let { File(it.filesDir, "jabong_geurin_gil.json") }

    /** 앱이 켜질 때 — 그리다 꺼졌으면 멈춤으로 되살림 */
    fun junbi(c: Context) {
        if (ctx != null) return
        ctx = c.applicationContext
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

    fun sijak() {
        val c = ctx ?: return
        if (!Seoljeong.bopokJaem) {
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
            put("beonho", ""); put("georeum", 0); put("olim", false); put("meomchum", false)
            put("gigi", MomSensor.gigiJeongbo(c))
        }
        stGijun = Wichi.georeumSu
        openPair = null; rideMode = ""; mureum = null
        sangtae = Sangtae.GEOREUM
        dolligi()
        momKyeogi()
        Girok.namgi("jb_geurigi_sijak", mapOf("id" to gil?.optString("id")))
        alrigi("걷기 시작했습니다. 평소 걸음으로 걸으시고, 꺾이는 곳과 계단, 건널목, 문에 닿는 순간 표시를 남겨 주십시오.")
        jeojang()
    }

    fun jamkkan() {
        if (sangtae != Sangtae.GEOREUM) return
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
        stGijun = if (meomchumSt0 > 0) stGijun + maxOf(0, Wichi.georeumSu - meomchumSt0) else Wichi.georeumSu - (gil?.optInt("georeum", 0) ?: 0)
        meomchumSt0 = 0
        sangtae = Sangtae.GEOREUM
        gil?.put("meomchum", false)
        val p = jigeumJari(); p.put("cut", 1)
        pts.put(p)
        dolligi()
        momKyeogi()
        alrigi("다시 걷습니다. 지금까지 ${georeum}걸음입니다.")
    }

    fun kkeut() {
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
        val geori = (gr * Seoljeong.bopok).roundToInt()
        var mal = "걷기를 마쳤습니다. ${gr}걸음, 약 ${geori}미터, 표시 ${marks.length()}개입니다."
        val n2 = if (gs.length() > 0) gs.getJSONObject(gs.length() - 1).optInt("n", 0) else 0
        if (n2 > 0 && gr > 20) {
            val cha = abs(n2 - gr).toDouble() / gr
            mal += if (cha <= 0.1) " 몸 센서로 센 걸음도 ${n2}걸음으로 잘 맞습니다." else " 몸 센서로 센 걸음은 ${n2}걸음이라 차이가 큽니다. 올리기 전 점검에서 살펴보겠습니다."
        }
        openPair?.let { o -> mal += " ${o.optString("name")}의 짝인 ${PAIR[o.optString("name")]?.end ?: "끝"} 표시가 없습니다. 올리기 전 점검에서 다시 여쭙겠습니다." }
        mal += " 그린 길은 폰에 담아 두었습니다. 올리기 전 점검과 올리기는 다음 판에 들어섭니다."
        openPair = null; rideMode = ""
        try {
            val mok = mokPail()?.takeIf { it.exists() }?.readText()?.let { JSONArray(it) } ?: JSONArray()
            val sae = JSONArray(); sae.put(g); for (i in 0 until mok.length()) sae.put(mok.get(i))
            mokPail()?.writeText(sae.toString())
            jigeumPail()?.delete()
        } catch (e: Exception) {}
        Girok.namgi("jb_geurigi_kkeut", mapOf("id" to g.optString("id"), "georeum" to gr, "georeum2" to n2, "pyosi" to marks.length(), "jari" to pts.length()))
        gil = null
        alrigi(mal)
    }

    fun geurinGilSu(): Int = try { mokPail()?.takeIf { it.exists() }?.readText()?.let { JSONArray(it).length() } ?: 0 } catch (e: Exception) { 0 }

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

    private fun tick() {
        if (sangtae != Sangtae.GEOREUM || gil == null) return
        MomSensor.nachimbanNeogi(Wichi.nachimban)
        MomSensor.sangtaeBoda()
        val p = jigeumJari()
        pts.put(p)
        gil?.put("georeum", p.optInt("st"))
        mureum?.let { if (System.currentTimeMillis() - it.ttae > 20000) { mureum = null; bakkwim?.invoke() } }
        if (rideMode.isEmpty()) { kkeokimBoda(); gyedanBoda() }
    }

    // MARK: 폰이 먼저 여쭘 — 아이폰과 같음(꺾임은 자이로 각도로)

    private fun kkeokimBoda() {
        val n = pts.length()
        val now = System.currentTimeMillis()
        if (n < 10 || mureum != null || now - majimakMureum < 15000 || now - majimakKkeokim < 10000) return
        val a = pts.getJSONObject(n - 10); val b = pts.getJSONObject(n - 1)
        if (b.optInt("st") - a.optInt("st") < 5) return
        val p0 = pts.getJSONObject(n - 8); val pm = pts.getJSONObject(n - 4)
        if (!p0.has("dg") || !b.has("dg") || !pm.has("dg")) return
        val d = b.getDouble("dg") - p0.getDouble("dg")
        if (abs(b.getDouble("dg") - pm.getDouble("dg")) >= abs(d) * 0.7) return
        if (abs(d) < 55) return
        yeojjum(if (d > 0) "오른쪽으로 꺾이셨습니까?" else "왼쪽으로 꺾이셨습니까?", if (d > 0) "오른쪽으로 꺾임" else "왼쪽으로 꺾임", n - 6)
    }

    private fun gyedanBoda() {
        val n = pts.length()
        if (n < 9 || mureum != null || System.currentTimeMillis() - majimakMureum < 15000) return
        val a = pts.getJSONObject(n - 9); val b = pts.getJSONObject(n - 1)
        if (!a.has("ralt") || !b.has("ralt")) return
        val o = openPair
        if (o != null && PAIR[o.optString("name")]?.kind == "계단") {
            val c = pts.getJSONObject(n - 5)
            if (!c.has("ralt") || abs(b.getDouble("ralt") - c.getDouble("ralt")) >= 0.25 || b.optInt("st") - c.optInt("st") < 3 || n - 5 <= o.optInt("pi")) return
            yeojjum("계단이 끝났습니까?", "계단 끝", n - 5); return
        }
        if (o != null || b.optInt("st") - a.optInt("st") < 4) return
        val cha = b.getDouble("ralt") - a.getDouble("ralt")
        if (abs(cha) < 1.2) return
        yeojjum(if (cha > 0) "높이가 올라갑니다. 올라가는 계단입니까?" else "높이가 내려갑니다. 내려가는 계단입니까?",
            if (cha > 0) "올라가는 계단 시작" else "내려가는 계단 시작", n - 9)
    }

    private fun yeojjum(mal: String, ne: String, jariI: Int) {
        majimakMureum = System.currentTimeMillis()
        mureum = Mureum(mal, ne, maxOf(0, jariI), majimakMureum)
        Girok.namgi("jb_mureum", mapOf("ne" to ne))
        Sori.mal(mal, MalGeup.ANNAE)
        bakkwim?.invoke()
    }

    fun dap(ne: Boolean) {
        val m = mureum ?: return
        mureum = null
        if (ne) {
            val jari = if (m.jariI < pts.length()) pts.getJSONObject(m.jariI) else null
            pyosi(m.ne, jari, mu = true)
        } else {
            Girok.namgi("jb_mureum_ani", mapOf("ne" to m.ne))
            alrigi("알겠습니다. 남기지 않았습니다.")
        }
    }

    // MARK: 표시

    fun pyosi(name: String, jari: JSONObject? = null, mu: Boolean = false) {
        if (gil == null) { alrigi("걷기를 시작한 뒤에 눌러 주십시오."); return }
        if (sangtae != Sangtae.GEOREUM) { alrigi("지금은 잠깐 멈춤입니다. 다시 걷기를 먼저 눌러 주십시오."); return }
        val p = jari ?: jigeumJari()
        val m = JSONObject()
        m.put("t", p.optInt("t")); m.put("st", p.optInt("st")); m.put("name", name)
        for (k in listOf("h", "lat", "lon", "acc", "hy", "st2", "dg", "ralt")) if (p.has(k)) m.put(k, p.get(k))
        if (mu) m.put("mureum", true)
        if (name.endsWith("꺾임")) majimakKkeokim = System.currentTimeMillis()
        mureum = null
        val st = m.optInt("st")
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
        if (name == "에스컬레이터 내림" || name == "지하철 내림" || name == "버스 내림") {
            if (o != null && PAIR[o.optString("name")]?.ride == true) {
                val secs = m.optInt("t") - o.optInt("t")
                val dd = geori(o, m)
                marks.optJSONObject(o.optInt("idx"))?.apply { put("secs", secs); put("dist", dd); put("ride", true) }
                m.put("pairOf", o.optString("name")); m.put("ride", true)
                marks.put(m); rideMode = ""; openPair = null; jeojang()
                alrigi("${PAIR[o.optString("name")]?.kind ?: ""}에서 내리셨습니다. ${if (secs >= 60) "${secs / 60}분 ${secs % 60}초" else "${secs}초"} 타셨고, ${dd}미터 오셨습니다. 이 구간은 걸음으로 재지 않고 그대로 적었습니다.")
                return
            }
            rideMode = ""; marks.put(m); jeojang()
            alrigi(name + "을 눌렀으나 탄 자리가 없습니다. 그냥 표시로만 남깁니다."); return
        }
        if (name == "계단 끝" || name == "횡단보도 건너기 끝") {
            if (o != null && PAIR[o.optString("name")]?.ride != true) {
                val n = st - o.optInt("st")
                val d = geori(o, m)
                marks.optJSONObject(o.optInt("idx"))?.apply { put("cnt", n); put("dist", d) }
                m.put("pairOf", o.optString("name"))
                marks.put(m); openPair = null; jeojang()
                alrigi(if (PAIR[o.optString("name")]?.kind == "계단") "${PAIR[o.optString("name")]?.up ?: ""} 계단이 ${n}칸입니다. 이대로 적습니다." else "횡단보도를 ${n}걸음, 약 ${d}미터 건너셨습니다. 그대로 적었습니다.")
                return
            }
            marks.put(m); jeojang()
            alrigi(name + "을 눌렀으나 시작 표시가 없습니다. 그냥 표시로만 남깁니다."); return
        }
        marks.put(m); jeojang()
        if (name == "문") { alrigi("문을 남겼습니다. 곧바로 두 걸음 앞으로 가서 문을 한 번 더 눌러 주십시오. 딱 찍고 두 걸음 뒤에 또 찍으셔야 문이 됩니다."); return }
        alrigi("${name}을 남겼습니다. 지금까지 ${st}걸음, 표시 ${marks.length()}개입니다.")
    }

    private fun geori(a: JSONObject, b: JSONObject): Int {
        if (!a.has("lat") || !b.has("lat")) return 0
        return Wichi.geori(a.getDouble("lat"), a.getDouble("lon"), b.getDouble("lat"), b.getDouble("lon")).roundToInt()
    }

    // MARK: 지금 상태

    fun sangtaeMal(): String = when (sangtae) {
        Sangtae.SWIM -> geurinGilSu().let { if (it == 0) "아직 그린 길이 없습니다." else "그린 길이 ${it}개 폰에 담겨 있습니다." }
        Sangtae.MEOMCHUM -> "잠깐 멈춤입니다. 지금까지 ${gil?.optInt("georeum", 0) ?: 0}걸음, 표시 ${marks.length()}개입니다."
        Sangtae.GEOREUM -> {
            var m = "그리는 중입니다. ${chobun / 60}분 ${chobun % 60}초 동안 ${georeum}걸음, 약 ${(georeum * Seoljeong.bopok).roundToInt()}미터, 표시 ${marks.length()}개입니다."
            openPair?.let { m += " ${it.optString("name")} 뒤에 ${PAIR[it.optString("name")]?.end ?: "끝"}을 아직 남기지 않으셨습니다." }
            if (MomSensor.dollyeo) m += " 몸 센서로 센 걸음은 ${MomSensor.georeumSu}걸음입니다."
            m
        }
    }

    // MARK: 저장

    fun jeojang() {
        val g = gil ?: return
        try {
            g.put("pts", pts); g.put("marks", marks); g.put("gs", gs)
            jigeumPail()?.writeText(g.toString())
        } catch (e: Exception) {}
    }

    private fun alrigi(t: String) {
        allim = t
        Sori.mal(t, MalGeup.ANNAE)
        bakkwim?.invoke()
    }
}
