package kr.or.ada.app.gilnun

// 2.9.0 (261003-T1, 이사장님 지시 "지하철을 타면 자동으로 걷는지 버스인지 자동차인지 지하철인지 알아챌 수 있는 능력은 되는 거 아니니")
// 탈것 저절로 알아채기 — 아이폰 TalgeotGamji.swift 와 같은 판단. 위성 빠르기에만 기대지 않음
//   걸음 센서: 15초 동안 걸음이 이어지면 걸음(묵은 "차" 판단을 바로 지움)
//   가속도: 걸음 없이 흔들리며 움직임이 20초 이어지면 탈것
//   기압계: 90초 안에 3.5미터 넘게 내려가면 땅속(계단·에스컬레이터)
//   탈것이면 — 땅속이거나 위성이 30초 넘게 끊겼거나 역 200미터 안에서 탔으면 지하철, 버스 정류장 25미터 안에서 두 번 넘게 섰다 떠나면 버스, 아니면 차

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import kotlin.math.sqrt

object TalgeotGamji : SensorEventListener {
    private val main = Handler(Looper.getMainLooper())
    private var sm: SensorManager? = null
    private var dolgo = false

    var chujeong: Talgeot = Talgeot.GEOREUM
        private set
    /** 땅속에 있음(기압으로 내려간 뒤 아직 땅 위로 나오지 않음) */
    var jiha = false
        private set
    /** 땅속으로 내려가기 직전의 땅 위 자리 */
    var jisangJari: Jari? = null
        private set
    var chaSijakJari: Jari? = null
        private set
    /** 지금 움직임: 걸음, 탈것, 멈춤, 모름 */
    var jigeumUmjigim = "모름"
        private set
    /** 탈것 안에서 섰다 떠남(초) — 지하철 역 세기 */
    var seotdaTteonam: ((Double) -> Unit)? = null
    /** 땅속에 들어감 */
    var jihaJinip: (() -> Unit)? = null

    private val chang = ArrayList<Double>()
    private var heundeulim = false
    private val georeumGirok = ArrayList<Pair<Long, Int>>()   // (때, 걸음 누적)
    private var georeumSijak = 0L
    private var chaSijak = 0L
    private var meomchumSijak = 0L
    private var yeokGeuncheo = false
    private var jeongryujangSeom = 0
    private val gido = ArrayList<Pair<Long, Double>>()
    private var jihaMin = 0.0

    val wiseongJoeum: Boolean
        get() {
            val t = Wichi.majimakWiseongTtae
            return t > 0 && System.currentTimeMillis() - t < 15000 && (Wichi.jigeum?.ochae ?: 999.0) <= 30
        }
    private val wiseongEopseum: Boolean
        get() { val t = Wichi.majimakWiseongTtae; return t == 0L || System.currentTimeMillis() - t > 30000 }

    fun sijak(c: Context) {
        if (dolgo) return
        dolgo = true
        val s = c.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        sm = s
        s.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { s.registerListener(this, it, 100000, main) }   // 0.1초
        s.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let { s.registerListener(this, it, 1000000, main) }       // 1초
        main.postDelayed(tik, 5000)
    }

    private val tik = object : Runnable {
        override fun run() { pandan(); main.postDelayed(this, 5000) }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = e.values[0].toDouble(); val y = e.values[1].toDouble(); val z = e.values[2].toDouble()
                chang.add(sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH)
                if (chang.size > 30) chang.removeAt(0)
                if (chang.size >= 20) {
                    val p = chang.sum() / chang.size
                    heundeulim = sqrt(chang.sumOf { (it - p) * (it - p) } / chang.size) > 0.04
                }
            }
            Sensor.TYPE_PRESSURE -> gidoBatda(e.values[0].toDouble())
        }
    }

    // MARK: 5초마다 판단

    private fun pandan() {
        val now = System.currentTimeMillis()
        if (!jiha && wiseongJoeum) jisangJari = Wichi.jigeum
        georeumGirok.add(now to Wichi.georeumSu)
        georeumGirok.removeAll { now - it.first > 25000 }
        val georeum15 = georeumGirok.lastOrNull()!!.second - (georeumGirok.firstOrNull { now - it.first <= 16000 }?.second ?: Wichi.georeumSu)
        val georeum20 = georeumGirok.lastOrNull()!!.second - (georeumGirok.firstOrNull()?.second ?: Wichi.georeumSu)
        val w = Wichi.jigeum
        val ppareum = w != null && wiseongJoeum && w.sokdo * 3.6 > 15
        val neurim = w != null && wiseongJoeum && w.sokdo * 3.6 < 5

        if (georeum15 >= 12) {
            jigeumUmjigim = "걸음"
            chaSijak = 0L; meomchumSijak = 0L
            if (georeumSijak == 0L) georeumSijak = now
            if (now - georeumSijak >= 15000 && chujeong != Talgeot.GEOREUM) {
                jeongryujangSeom = 0; yeokGeuncheo = false
                bakkugi(Talgeot.GEOREUM, "걷기 15초")
            }
            jihaHwagin()
        } else if (georeum20 <= 3 && (ppareum || (heundeulim && !neurim))) {
            jigeumUmjigim = "탈것"
            georeumSijak = 0L
            if (meomchumSijak > 0) {
                val t = (now - meomchumSijak) / 1000.0
                meomchumSijak = 0L
                if (t in 5.0..120.0) meomchumKkeut(t)
            }
            if (chaSijak == 0L) { chaSijak = now; chaSijakJari = Wichi.jigeum; yeokGeuncheoBoda() }
            if (now - chaSijak >= 20000) chongPandan("탈것 20초")
        } else if (georeum20 <= 3) {
            jigeumUmjigim = "멈춤"
            // 2.25.0 오래(1분 넘게) 멈춰 있으면 띄엄띄엄 만지작거린 흔들림이 탈것 20초로 쌓이지 않게 처음부터
            if (chaSijak > 0 && meomchumSijak > 0 && now - meomchumSijak > 60000 && chujeong == Talgeot.GEOREUM) chaSijak = 0L
            if ((chujeong != Talgeot.GEOREUM || chaSijak > 0) && meomchumSijak == 0L) meomchumSijak = now
        }
    }

    private fun meomchumKkeut(t: Double) {
        seotdaTteonam?.invoke(t)
        if (chujeong == Talgeot.JIHACHEOL || jiha || !wiseongJoeum) return
        val w = Wichi.jigeum ?: return
        Beoseu.gakkaun(w.lat, w.lon) { l ->
            if (l != null && l.any { Wichi.geori(w.lat, w.lon, it.lat, it.lon) <= 25 }) jeongryujangSeom += 1
            if (chujeong == Talgeot.CHA && jeongryujangSeom >= 2) bakkugi(Talgeot.BEOSEU, "정류장 ${jeongryujangSeom}번 섬")
        }
    }

    private fun yeokGeuncheoBoda() {
        yeokGeuncheo = false
        val w = (if (jiha) jisangJari else null) ?: chaSijakJari ?: jisangJari ?: return
        JihacheolEngine.gakkaunYeokGeori(w.lat, w.lon) { d -> yeokGeuncheo = (d ?: 9999.0) <= 200 }
    }

    private fun chongPandan(kkadak: String) {
        val t = when {
            jiha || wiseongEopseum || yeokGeuncheo -> Talgeot.JIHACHEOL
            jeongryujangSeom >= 2 || chujeong == Talgeot.BEOSEU -> Talgeot.BEOSEU
            else -> Talgeot.CHA
        }
        if (t != chujeong) bakkugi(t, kkadak + (if (jiha) ", 땅속" else "") + (if (yeokGeuncheo) ", 역 근처에서 탐" else ""))
    }

    private fun bakkugi(t: Talgeot, kkadak: String) {
        chujeong = t
        Girok.namgi("talgeot_gamji", mapOf("t" to t.raw, "kkadak" to kkadak))
        YeojeongEngine.gamjiBatda(t)
    }

    // MARK: 기압 — 1헥토파스칼 ≈ 8.3미터

    // 2.25.0 (전체 점검) 갤럭시 탭 기압계가 잘게 흔들려 가만히 둔 폰이 밤새 「땅속 들어감·나옴」을 백 번 넘게 되풀이함(나스 기록).
    // 아이폰 고도계처럼 10초 평균으로 고르고, 내려간 채로 10초 넘게 머물러야 땅속으로 봄
    private val gidoNal = ArrayList<Pair<Long, Double>>()
    private var naeryeogaTtae = 0L

    private fun gidoBatda(hpa: Double) {
        val now = System.currentTimeMillis()
        gidoNal.add(now to (-hpa * 8.3))
        gidoNal.removeAll { now - it.first > 10000 }
        val h = gidoNal.sumOf { it.second } / gidoNal.size   // 높을수록 큰 값(상대 높이), 10초 평균
        gido.add(now to h)
        gido.removeAll { now - it.first > 120000 }
        if (!jiha) {
            val jeonMax = gido.filter { now - it.first <= 90000 }.maxOfOrNull { it.second } ?: h
            if (jeonMax - h >= 3.5 && !wiseongJoeum) {
                if (naeryeogaTtae == 0L) naeryeogaTtae = now
                if (now - naeryeogaTtae < 10000) return
                naeryeogaTtae = 0L
                jiha = true
                jihaMin = h
                Girok.namgi("jiha_jinip", mapOf("naeryeogam" to ((jeonMax - h) * 10).toInt()))
                jihaJinip?.invoke()
            } else naeryeogaTtae = 0L
        } else {
            if (h < jihaMin) jihaMin = h
            jihaHwagin()
        }
    }

    /** 땅 위로 나왔는가 — 3.5미터 넘게 올라오고 걷거나, 위성이 다시 잡혀야 함 */
    private fun jihaHwagin() {
        if (!jiha) return
        val h = gido.lastOrNull()?.second ?: jihaMin
        if (wiseongJoeum || (h - jihaMin >= 3.5 && jigeumUmjigim == "걸음")) {
            jiha = false
            jisangJari = Wichi.jigeum
            Girok.namgi("jiha_naom", mapOf("wiseong" to wiseongJoeum))
        }
    }
}
