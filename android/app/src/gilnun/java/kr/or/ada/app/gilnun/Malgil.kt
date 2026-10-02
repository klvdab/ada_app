// 안드로이드 길눈 — 말로 그린 길(실내, 2.7.0, 묶음 b2 점지도 마저, 대표님 지시). 아이폰 Malgil.swift 2.14.0(이사장님 승인)과 같은 자료, 같은 흐름.
// 이사장님이 몸으로 익혀 말로 적어 두신 실내 길(보기: 사무실 ↔ 집)을 손대지 않고 따라 걷게 합니다.
// ① 폰이 걸음을 세고(가속도, 곧바로) 방향을 보며(자이로 합성 방향) 정해진 걸음에 닿으면 다음 안내를 스스로 말함
// ② 세 걸음 앞에서 다음 할 일을 미리, 방향이 75도 넘게 틀어지면 멈추라고, 예상보다 많이 걸으면 확인하라고 알림
// ③ 엘리베이터는 8초 넘게 멈췄다가 다시 두 걸음 걸으면 내린 것으로 봄. 문처럼 걸음 수가 없는 대목은 네 걸음 걸으면 다음으로
// ④ 나스 음악 열쇠가 있는 폰에만 내줌(/jeom/malgil.php, 지금은 이사장님 한 분). 열쇠는 음악 묶음이 넣는 자리에서 읽음(tkGajyeogi)
package kr.or.ada.app.gilnun

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.view.View
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.sqrt

data class MalgilMok(val id: String, val ireum: String, val gagiSu: Int, val ogiSu: Int)

object MalgilEngine : SensorEventListener {
    private class Dangye(val mal: String, val juui: String, var n: Int, var jong: String, val gak: Double?, var tan: Boolean = false)
    // jong: georeum, gyedan, elev, meomchum, dochak, georeum_dochak

    var mok: List<MalgilMok> = emptyList()
        private set
    var geotneun = false
        private set
    var jemok = ""
        private set
    var jul = ""
        private set
    /** 상태가 바뀌면 한 번(화면이 채움) */
    var byeonhwa: (() -> Unit)? = null

    private var S: List<Dangye> = emptyList()
    private var si = -1
    private var geol = 0
    private var ddo = false
    private var gidaeHeading: Double? = null
    private var bangMal = 0L
    private var majimak = ""

    private var ctx: Context? = null
    private var smgr: SensorManager? = null
    private var sm = 9.8
    private var base = 9.8
    private var wi = false
    private var lastStep = 0L
    private var meomchumSijak: Long? = null
    private val main = Handler(Looper.getMainLooper())
    private var dolgo = false
    private val sigye = object : Runnable {
        override fun run() {
            if (!dolgo) return
            dolgi()
            main.postDelayed(this, 1000)
        }
    }

    /** 나스 음악 열쇠 — 음악 묶음이 열쇠를 다른 곳에 두면 이 자리를 바꿔 채움 */
    var tkGajyeogi: () -> String = {
        val c = ctx
        Yeolsoe.eumakTk(c)   // 2.7.0 통합 — 나스 음악 열쇠는 한 도우미로
    }
    private val tk: String get() = tkGajyeogi()
    val yeolsoeItda: Boolean get() = tk.isNotEmpty()

    fun sijak(c: Context) {
        if (ctx == null) ctx = c.applicationContext
        JeomSeol.sijak(c)
    }

    // MARK: 받기

    /** 목록 — 거짓이면 열쇠가 없거나 닿지 못함 */
    fun mokBatgi(kkeut: (Boolean) -> Unit) {
        if (!yeolsoeItda) { kkeut(false); return }
        Tongsin.json("/jeom/malgil.php", mapOf("a" to "mok", "tk" to tk), 20000) { o ->
            if (o == null || !o.optBoolean("ok", false)) { kkeut(false); return@json }
            val l = ArrayList<MalgilMok>()
            val a = o.optJSONArray("list")
            if (a != null) for (i in 0 until a.length()) {
                val r = a.optJSONObject(i) ?: continue
                val id = r.opt("id") as? String ?: continue
                l.add(MalgilMok(id, (r.opt("ireum") as? String) ?: id, (Jeomjido.su(r, "gagi") ?: 0.0).toInt(), (Jeomjido.su(r, "ogi") ?: 0.0).toInt()))
            }
            mok = l
            kkeut(true)
        }
    }

    // MARK: 걷기

    fun sijak(c: Context, m: MalgilMok, ogi: Boolean) {
        sijak(c)
        Tongsin.json("/jeom/malgil.php", mapOf("a" to "get", "id" to m.id, "tk" to tk), 20000) { o ->
            val dan = o?.optJSONObject("gil")?.optJSONArray(if (ogi) "ogi" else "gagi")
            if (dan == null || dan.length() == 0) {
                Sori.mal("길을 받아 오지 못했습니다. 통신과 나스 음악 열쇠를 확인해 주십시오.")
                return@json
            }
            val l = ArrayList<Dangye>()
            for (i in 0 until dan.length()) l.add(pulgi(dan.optJSONObject(i) ?: JSONObject()))
            S = l
            jemok = m.ireum + (if (ogi) " — 오는 길" else " — 가는 길")
            geotneun = true
            Wichi.wiseongDolligi()
            umjikKyeogi()
            Girok.namgi("malgil", mapOf("id" to m.id, "ogi" to ogi))
            Sori.mal("$jemok, 모두 ${S.size}단계로 안내합니다. 이제 손대실 일이 없습니다. 다시라고 하시면 지금 안내를 다시 들려 드립니다.")
            main.postDelayed({ dangye(0) }, 1500)
            byeonhwa?.invoke()
        }
    }

    fun geuman(malHagi: Boolean = true) {
        if (!geotneun) return
        umjikKkeugi()
        geotneun = false
        si = -1
        jul = ""
        if (malHagi) Sori.mal("말로 그린 길 안내를 그만둡니다.")
        byeonhwa?.invoke()
    }

    fun dasiDeutgi() {
        Sori.mal(if (majimak.isEmpty()) "들려 드릴 안내가 없습니다." else majimak)
    }

    // MARK: 단계 풀기(웹과 같은 규칙)

    private fun pulgi(s: JSONObject): Dangye {
        val t = (s.opt("mal") as? String) ?: ""
        val d = Dangye(t, (s.opt("juui") as? String) ?: "", 0, "georeum", sigak(t))
        val b0 = Seoljeong.bopok
        val bocok = if (b0 > 0.3 && b0 < 1.2) b0 else 0.7
        val g = Jeomjido.su(s, "georeum")
        val m = Jeomjido.su(s, "meter")
        if (g != null && g > 0) d.n = g.toInt()
        else if (m != null && m > 0) d.n = Jeomjido.bannol(m / bocok).toInt()
        if (t.contains("계단") && d.n == 0) { d.n = 3; d.jong = "gyedan" }
        val cheung = Regex("층\\s*$|층 단추|단추로\\s*\\d+층|지하\\s*1층").containsMatchIn(t)
        if (cheung && (t.contains("엘리베이터") || t.contains("단추")) && g == null) d.jong = "elev"
        else if (d.n == 0) d.jong = "meomchum"
        if ((s.opt("pyosi") as? String) == "도착") d.jong = if (d.n > 0) "georeum_dochak" else "dochak"
        return d
    }

    /** "9시 방향", "10시 30분 방향" → 몸 기준 도(오른쪽 +) */
    fun sigak(t: String): Double? {
        val r = Regex("(\\d{1,2})\\s*시(\\s*30\\s*분)?\\s*방향").find(t) ?: return null
        val sv = r.value
        val h = sv.takeWhile { it.isDigit() }.toDoubleOrNull() ?: 12.0
        val ban = if (sv.contains("30")) 0.5 else 0.0
        val d = ((h % 12) + ban) * 30
        return if (d > 180) d - 360 else d
    }

    // MARK: 흐름

    private fun dangye(i: Int) {
        if (!geotneun || i >= S.size) return
        si = i; geol = 0; ddo = false
        val k = S[i]
        var tt = (if (k.juui.isEmpty()) "" else k.juui.trim('.', ' ') + ". ") + k.mal
        if (k.jong == "elev") tt += ". 엘리베이터에서 내리셔서 걷기 시작하시면 다음을 알려 드리겠습니다."
        mal(tt)
        jul = "${i + 1}단계 — ${k.mal}"
        val h = Wichi.hapBang
        val g = k.gak
        if (g != null && h >= 0) gidaeHeading = (h + g + 360) % 360
        else if (g == null) gidaeHeading = null
        if (k.jong == "dochak") { kkeutnaegi(); return }
        meomchumSijak = null
        byeonhwa?.invoke()
    }

    private fun daeum() { if (si + 1 < S.size) dangye(si + 1) else kkeutnaegi() }

    private fun kkeutnaegi() {
        umjikKkeugi()
        geotneun = false
        jul = ""
        Girok.namgi("malgil_kkeut")
        main.postDelayed({ Sori.mal("닿으셨습니다. 말로 그린 길 안내를 마칩니다.") }, 2500)
        byeonhwa?.invoke()
    }

    private fun georeum() {
        if (!geotneun || si < 0 || si >= S.size) return
        val k = S[si]
        geol += 1
        if (k.jong == "georeum" || k.jong == "gyedan" || k.jong == "georeum_dochak") {
            if (k.n >= 6 && geol == k.n - 3 && si + 1 < S.size) {
                val dm = S[si + 1].mal.split(",")[0].split(".")[0]
                mal("세 걸음 뒤, $dm")
            }
            val h = Wichi.hapBang
            val gd = gidaeHeading
            if (gd != null && h >= 0 && geol >= 3 && geol % 2 == 1 && abs(DoeEngine.chai(h, gd)) > 75 &&
                System.currentTimeMillis() - bangMal > 8000) {
                bangMal = System.currentTimeMillis()
                mal("방향이 다릅니다. 멈추십시오. 가실 쪽을 다시 잡아 주십시오.")
            }
            if (geol >= k.n) {
                if (k.jong == "georeum_dochak") { kkeutnaegi(); return }
                daeum()
            } else if (geol.toDouble() > k.n * 1.5 + 3 && !ddo) {
                ddo = true
                mal("예상보다 많이 걸으셨습니다. 멈추고 확인하십시오.")
            }
        } else if (k.jong == "meomchum") {
            if (geol >= 4) daeum()
        }
    }

    /** 엘리베이터처럼 멈췄다가 다시 걷는 대목 — 1초마다 */
    private fun dolgi() {
        if (!geotneun || si < 0 || si >= S.size) return
        val k = S[si]
        if (k.jong == "elev") {
            val m = meomchumSijak
            if (!k.tan && m != null && System.currentTimeMillis() - m > 8000) { k.tan = true; geol = 0 }
            if (k.tan && geol >= 2) daeum()
        }
    }

    // MARK: 걸음 감지 — 가속도(웹·아이폰과 같은 셈, 곧바로). 안드로이드 값은 이미 초제곱 미터

    override fun onSensorChanged(e: SensorEvent) {
        val x = e.values[0].toDouble()
        val y = e.values[1].toDouble()
        val z = e.values[2].toDouble()
        val m = sqrt(x * x + y * y + z * z)
        val now = System.currentTimeMillis()
        sm = sm * 0.75 + m * 0.25
        base = base * 0.98 + m * 0.02
        if (!wi && sm > base + 1.1 && now - lastStep > 330) {
            wi = true; lastStep = now; georeum()
        } else if (wi && sm < base + 0.3) wi = false
        if (abs(sm - base) < 0.35) {
            if (meomchumSijak == null) meomchumSijak = now
        } else if (now - lastStep < 1500) meomchumSijak = null
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    private fun umjikKyeogi() {
        sm = 9.8; base = 9.8; wi = false; meomchumSijak = null
        val c = ctx
        val s = c?.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        smgr = s
        s?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { s.registerListener(this, it, 1_000_000 / 50, main) }
        main.removeCallbacks(sigye)
        dolgo = true
        main.postDelayed(sigye, 1000)
    }

    private fun umjikKkeugi() {
        smgr?.unregisterListener(this)
        smgr = null
        dolgo = false
        main.removeCallbacks(sigye)
    }

    private fun mal(t: String) {
        majimak = t
        Sori.mal(t)
    }
}

/** 말로 그린 길 화면 */
class MalgilHwamyeon : Hwamyeon("말로 그린 길") {
    private var sangtae = if (MalgilEngine.mok.isNotEmpty()) 1 else 0   // 0 받는 중, 1 받음
    private var cheot: View? = null
    private var chojeomHal = true
    private var pyeol = false
    private var batneun = false

    override fun chaeugi(t: GilnunActivity) {
        val e = MalgilEngine
        e.sijak(t)
        e.byeonhwa = { if (t.wiHwamyeon === this) { chojeomHal = true; t.dasiGeurigi() } }
        cheot = null
        if (!e.geotneun && e.yeolsoeItda && e.mok.isEmpty() && sangtae == 0 && !batneun) batgi(t)
        if (e.geotneun) {
            cheot = t.danchu("다시 듣기 — ${e.jul}") { e.dasiDeutgi() }
            t.danchu("안내 그만 — ${e.jemok}") { e.geuman() }
        } else if (!e.yeolsoeItda) {
            cheot = t.geul("말로 그린 길은 나스 열쇠를 넣은 폰에서만 쓰실 수 있습니다. 음악·방송 탭의 길 위의 음악에서 열쇠를 한 번 넣어 주십시오.")
        } else if (sangtae == 0) {
            cheot = t.geul("말로 그린 길을 받는 중입니다.")
        } else if (e.mok.isEmpty()) {
            cheot = t.danchu("말로 그린 길을 받지 못했습니다 — 다시 받기") { sangtae = 0; batgi(t); t.dasiGeurigi() }
        } else {
            for (m in e.mok) {
                val v = t.danchu("가는 길 따라 걷기 — ${m.ireum}, ${m.gagiSu}단계") { e.sijak(t, m, false) }
                if (cheot == null) cheot = v
                if (m.ogiSu > 0) t.danchu("오는 길 따라 걷기 — ${m.ireum}, ${m.ogiSu}단계") { e.sijak(t, m, true) }
            }
        }
        t.danchu(if (pyeol) "알아 두실 것 접기" else "알아 두실 것 펼치기") { pyeol = !pyeol; t.dasiGeurigi() }
        if (pyeol) t.geul("몸으로 익혀 말로 적어 둔 실내 길을 손대지 않고 따라 걷게 해 드립니다. 한 번 두드리시면 그 뒤로는 폰이 걸음을 세고 방향을 보며, 정해진 걸음에 닿으면 다음 안내를 스스로 말씀드립니다. 세 걸음 앞에서 다음 할 일을 미리 알려 드리고, 방향이 크게 틀어지면 멈추시라고, 예상보다 많이 걸으시면 확인하시라고 알려 드립니다. 엘리베이터는 멈췄다가 다시 걷기 시작하시면 내리신 것으로 봅니다. 말로 하기에서 다시라고 하시면 지금 안내를 다시, 그만이라고 하시면 멈춥니다. 폰을 몸에 지니고 평소처럼 걸으십시오.")
    }

    override fun boilttae(t: GilnunActivity) {
        if (!chojeomHal) return
        chojeomHal = false
        cheot?.let { t.chojeomOmgigi(it) }
    }

    private fun batgi(t: GilnunActivity) {
        batneun = true
        MalgilEngine.mokBatgi {
            batneun = false
            sangtae = 1
            chojeomHal = true
            if (t.wiHwamyeon === this) t.dasiGeurigi()
        }
    }
}
