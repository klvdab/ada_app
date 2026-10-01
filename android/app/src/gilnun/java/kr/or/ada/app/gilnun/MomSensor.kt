// 안드로이드 몸 센서 (자봉 2.3.0 빌드 261001-A2, 길눈 2.1.0 빌드 261001-A3부터 길눈과 함께 씀, 대표님 지시 2026-10-01)
// ★길눈 2.1.0: 그리는 자봉과 걷는 길눈이 같은 센서·같은 셈법으로 걸음과 방향을 재야 그린 100걸음과 걷는 100걸음이 맞음.
//   그래서 이 파일을 src/jabong 에서 src/gilnun 으로 옮겨 두 앱이 함께 씁니다(자봉 갈래는 src/gilnun 을 함께 싣습니다).
// ★대표님: 가장 중요한 것은 걸으면서 찍어 주는 점지도. 몸이 움직이는 상황을 담는 센서를 최대한 끌어내 쓸 것. 아이폰만이 아니라 안드로이드도.
// 아이폰 자봉 MomSensor.swift 와 똑같은 "센서 기록 규격 1.0"으로 걸음마다 한 줄씩 남깁니다.
//   선형 가속도·중력 센서 — 발이 땅에 닿는 순간마다 한 걸음(1초에 50번 읽음, 아이폰과 같은 셈법)
//   자이로 — 몸이 돈 각도(오른쪽 +). 쇠붙이·건물 옆에서도 틀어지지 않음
//   회전 벡터(나침반 합성) — 자이로가 오래 지나 밀리는 것을 천천히 바로잡음
//   걸음 감지 센서 — 안드로이드가 걸음마다 바로 알려 주는 값(대조용)
//   기압 센서 — 오르내린 높이(있는 기종만)
// 모든 값은 기기 시계(부팅 뒤 흐른 시간, 천분의 1초)로 같은 줄 위에 맞춥니다.
package kr.or.ada.app.gilnun

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** 걸음마다 한 줄 — 센서 기록 규격 1.0 (아이폰 GrGeoreum 과 같은 이름) */
data class Georeum(val n: Int, val ms: Long, val hy: Double?, val dol: Double, val chung: Double, var ralt: Double?, val sa: String?) {
    fun json(): JSONObject = JSONObject().apply {
        put("n", n); put("ms", ms); hy?.let { put("hy", it) }; put("dol", dol); put("chung", chung)
        ralt?.let { put("ralt", it) }; sa?.let { put("sa", it) }
    }
}

object MomSensor : SensorEventListener {
    const val HZ = 50
    const val GYUGYEOK = "센서기록규격 1.0 (261001)"

    private var sm: SensorManager? = null
    private var jul: HandlerThread? = null
    private val main = Handler(Looper.getMainLooper())
    private val jamgeum = Any()

    @Volatile var dollyeo = false; private set
    @Volatile var georeumSu = 0; private set          // 가속도로 센 걸음
    @Volatile var gamjiSu = 0; private set            // 안드로이드 걸음 감지 센서가 센 걸음(대조용)
    @Volatile var hapseong: Double? = null; private set
    @Volatile var nujeokDol = 0.0; private set
    @Volatile var sangtae: String? = null; private set
    @Volatile var nopi: Double? = null; private set   // 기압으로 잰 상대 높이(미터)
    @Volatile private var nachimban = -1.0

    /** 걸음 한 줄이 생길 때마다(화면 줄에서) */
    var georeumNal: ((Georeum) -> Unit)? = null
    /** 길눈 2.1.0 — 길눈 위치 엔진이 걸음마다 받는 곳(자봉의 georeumNal 과 따로 둠) */
    var gilnunGeoreum: (() -> Unit)? = null

    fun nachimbanNeogi(v: Double) { nachimban = v }

    // 셈
    private var sijakUptime = 0L
    private val gr = FloatArray(3)          // 중력(위쪽을 가리킴)
    private var grItda = false
    private val gaJik = FloatArray(3)       // 선형 가속도가 없을 때 가속도에서 중력을 걸러 냄
    private var seonhyeongEopda = false
    private var lp = 0.0; private var lpAp = 0.0; private var lpApAp = 0.0
    private var gotgolMin = 0.0
    private var majimakGeoreum = 0L
    private val bongDeul = ArrayList<Double>()
    private val saiDeul = ArrayList<Double>()
    private var chungMax = 0.0
    private var dolGeoreumSai = 0.0
    private var majimakGyro = 0L
    private var nopi0: Double? = null

    fun kyeogi(ctx: Context, n0: Int = 0, dol0: Double = 0.0, heureunCho: Double = 0.0) {
        synchronized(jamgeum) {
            if (dollyeo) return
            dollyeo = true
        }
        val s = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        sm = s
        georeumSu = n0; nujeokDol = dol0; gamjiSu = 0
        sijakUptime = SystemClock.elapsedRealtimeNanos() - (max(0.0, heureunCho) * 1e9).toLong()
        lp = 0.0; lpAp = 0.0; lpApAp = 0.0; gotgolMin = 0.0; majimakGeoreum = 0L
        bongDeul.clear(); saiDeul.clear(); chungMax = 0.0; dolGeoreumSai = 0.0; majimakGyro = 0L
        grItda = false
        val t = HandlerThread("jabong-momsensor").also { it.start() }
        jul = t
        val h = Handler(t.looper)
        val us = 1_000_000 / HZ
        val seon = s.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val jung = s.getDefaultSensor(Sensor.TYPE_GRAVITY)
        seonhyeongEopda = seon == null || jung == null
        if (!seonhyeongEopda) {
            s.registerListener(this, seon, us, h)
            s.registerListener(this, jung, us, h)
        } else {
            s.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { s.registerListener(this, it, us, h) }
        }
        s.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { s.registerListener(this, it, us, h) }
        s.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let { s.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST, h) }
        s.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let { s.registerListener(this, it, 200_000, h) }
    }

    fun kkeugi() {
        synchronized(jamgeum) {
            if (!dollyeo) return
            dollyeo = false
        }
        sm?.unregisterListener(this)
        jul?.quitSafely(); jul = null
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_GRAVITY -> { gr[0] = e.values[0]; gr[1] = e.values[1]; gr[2] = e.values[2]; grItda = true }
            Sensor.TYPE_LINEAR_ACCELERATION -> if (grItda) wiArae(e.values[0], e.values[1], e.values[2], e.timestamp)
            Sensor.TYPE_ACCELEROMETER -> {
                // 선형 가속도 센서가 없는 기종 — 천천히 따라가는 값을 중력으로 보고 나머지를 몸의 움직임으로
                for (i in 0..2) gaJik[i] += (e.values[i] - gaJik[i]) * 0.1f
                gr[0] = gaJik[0]; gr[1] = gaJik[1]; gr[2] = gaJik[2]; grItda = true
                wiArae(e.values[0] - gaJik[0], e.values[1] - gaJik[1], e.values[2] - gaJik[2], e.timestamp)
            }
            Sensor.TYPE_GYROSCOPE -> {
                if (!grItda) return
                val dt = if (majimakGyro > 0) min(0.1, max(0.0, (e.timestamp - majimakGyro) / 1e9)) else 1.0 / HZ
                majimakGyro = e.timestamp
                val gk = max(0.0001, sqrt((gr[0] * gr[0] + gr[1] * gr[1] + gr[2] * gr[2]).toDouble()))
                // 안드로이드 중력 값은 위쪽을 가리킴 — 위쪽 축으로 도는 것은 위에서 보아 왼쪽(반시계)이 + 이므로 부호를 바꿔 오른쪽을 +
                val wiChuk = (e.values[0] * gr[0] + e.values[1] * gr[1] + e.values[2] * gr[2]) / gk
                val dolDo = -wiChuk * dt * 180.0 / Math.PI
                nujeokDol += dolDo
                dolGeoreumSai += dolDo
                var h = hapseong
                if (h != null) {
                    h = ((h + dolDo) % 360 + 360) % 360
                    val nc = nachimban
                    if (nc >= 0) {
                        var cha = (nc - h) % 360
                        if (cha > 180) cha -= 360; if (cha < -180) cha += 360
                        if (abs(cha) < 60) h += cha * 0.01
                        h = (h % 360 + 360) % 360
                    }
                    hapseong = h
                } else if (nachimban >= 0) {
                    hapseong = nachimban
                }
            }
            Sensor.TYPE_STEP_DETECTOR -> gamjiSu += 1
            Sensor.TYPE_PRESSURE -> {
                val a = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, e.values[0]).toDouble()
                val n0 = nopi0
                if (n0 == null) { nopi0 = a; nopi = 0.0 }
                else { val bon = nopi ?: 0.0; nopi = bon + ((a - n0) - bon) * 0.2 }
            }
        }
    }

    /** 위아래 가속도로 걸음 찾기 — 아이폰과 같은 셈(문턱을 스스로 맞추고, 한 걸음에 봉우리 둘을 막음) */
    private fun wiArae(ax: Float, ay: Float, az: Float, ts: Long) {
        val gk = max(0.0001, sqrt((gr[0] * gr[0] + gr[1] * gr[1] + gr[2] * gr[2]).toDouble()))
        val w = ((ax * gr[0] + ay * gr[1] + az * gr[2]) / gk) / 9.80665      // 위쪽 +, 중력 단위
        chungMax = max(chungMax, abs(w))
        lpApAp = lpAp; lpAp = lp
        lp += (w - lp) * 0.3
        gotgolMin = min(gotgolMin, lp)
        val pyeong = if (bongDeul.isEmpty()) 0.12 else bongDeul.average()
        val munteok = max(0.03, pyeong * 0.4)
        val bongwuri = lpAp > lpApAp && lpAp >= lp && lpAp > munteok
        val sai = (ts - majimakGeoreum) / 1e9
        var swim = 0.28
        if (saiDeul.size >= 4) { val j = saiDeul.sorted(); swim = max(0.28, min(1.0, j[j.size / 2] * 0.55)) }
        if (!bongwuri || (majimakGeoreum != 0L && sai < swim)) return
        if (lpAp - gotgolMin <= max(0.05, pyeong * 0.5)) return
        if (majimakGeoreum != 0L && sai < 2.0) { saiDeul.add(sai); if (saiDeul.size > 8) saiDeul.removeAt(0) }
        majimakGeoreum = ts
        gotgolMin = lpAp
        bongDeul.add(lpAp); if (bongDeul.size > 12) bongDeul.removeAt(0)
        georeumSu += 1
        sangtae = "걷기"
        val g = Georeum(georeumSu, (ts - sijakUptime) / 1_000_000,
            hapseong?.let { Math.round(it * 10) / 10.0 },
            Math.round(dolGeoreumSai * 10) / 10.0,
            Math.round(chungMax * 1000) / 1000.0,
            null, sangtae)
        dolGeoreumSai = 0.0
        chungMax = 0.0
        main.post {
            g.ralt = nopi?.let { Math.round(it * 10) / 10.0 }
            georeumNal?.invoke(g)
            gilnunGeoreum?.invoke()
        }
    }

    /** 1초마다 화면 쪽에서 부름 — 3초 넘게 걸음이 없으면 멈춤 */
    fun sangtaeBoda() {
        if (majimakGeoreum == 0L || (SystemClock.elapsedRealtimeNanos() - majimakGeoreum) / 1e9 > 3.0) sangtae = "멈춤"
    }

    /** 기기 정보 — 기종마다 센서가 다르므로 길마다 함께 남김 */
    fun gigiJeongbo(ctx: Context): JSONObject {
        val s = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        fun itna(t: Int) = s?.getDefaultSensor(t) != null
        val se = JSONObject().apply {
            put("가속도", itna(Sensor.TYPE_ACCELEROMETER)); put("선형가속도", itna(Sensor.TYPE_LINEAR_ACCELERATION)); put("중력", itna(Sensor.TYPE_GRAVITY))
            put("자이로", itna(Sensor.TYPE_GYROSCOPE)); put("자력계", itna(Sensor.TYPE_MAGNETIC_FIELD)); put("합성방향", itna(Sensor.TYPE_ROTATION_VECTOR))
            put("걸음감지", itna(Sensor.TYPE_STEP_DETECTOR)); put("만보기", itna(Sensor.TYPE_STEP_COUNTER)); put("기압계", itna(Sensor.TYPE_PRESSURE))
            put("움직임상태", false)   // 이 판은 걸음 유무로 멈춤만 가림(구글 활동 인식은 다음 판)
        }
        return JSONObject().apply {
            put("momo", Build.MANUFACTURER + " " + Build.MODEL)
            put("os", "Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")")
            put("sensor", se); put("hz", HZ); put("tteul", GYUGYEOK)
        }
    }
}
