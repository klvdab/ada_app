// 갤럭시 워치 길눈 — 손목 가리키기 (2.5.0판, 빌드 261002-A8, 대표님 지시: 아이폰 길눈 워치 SonmokGariki.swift 2.36.0 을 갤럭시 워치로)
// 지팡이를 쥐지 않은 손에 워치를 차셨을 때, 점지도 따라 걷는 중 그 팔을 손등이 위로 오게 앞으로 뻗어 가리키시면
//   팔이 가야 할 쪽을 가리키는 순간(12도 안) "맞음" 진동(톡 하고 굵게)을 1초마다,
//   어긋나면 팔을 옮길 쪽을 진동으로 0.7초마다 — 오른쪽은 길게 한 번, 왼쪽은 짧게 두 번(길눈 방향 진동과 같은 무늬)
//   팔을 뻗는 순간 한 번만 "맞습니다", "오른쪽으로", "왼쪽으로"라고 말함. 팔을 내리면 조용해짐.
// 가야 할 쪽은 폰 길눈이 보냄(돌아야 할 때는 돌 쪽, 아니면 앞 6미터의 점지도 방향). 팔 방향은 워치의 회전 벡터 센서(나침반)로 잼.
// 팔 방향과 나침반의 어긋남은 손목에 따라 기본값(왼손목 +90도, 오른손목 -90도)을 쓰고, "가리키기 방향 맞추기"로 한 번 바로잡으면 그 값을 씀.
// 안드로이드에 맞춘 것:
//   · 손등이 위(화면이 하늘)는 중력 센서로 — 안드로이드는 화면이 하늘을 보면 z 가 +9.8 이라, 아이폰과 같은 잣대(z < -0.8 g)로 쓰려고 부호를 뒤집어 g 로 바꿈
//   · 회전 벡터 방위각은 자북 기준이라 한국의 편각(약 -8도)을 더해 진북으로 맞춤(점지도 방향은 진북)
package kr.or.ada.app.wear

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import kotlin.math.abs

object SonmokGariki : SensorEventListener {
    private val main = Handler(Looper.getMainLooper())
    private var ctx: Context? = null

    /** 폰이 보낸 가야 할 쪽(진북 기준 도). 없으면 쉼 */
    var mok: Double? = null
        private set
    var kyeojim: Boolean
        get() = WatchModel.prefs.getBoolean("garikiOn", true)
        set(v) { WatchModel.prefs.edit().putBoolean("garikiOn", v).apply(); dasiJeonghagi() }

    private var heading: Double? = null
    private var gz = 0.0                 // 아이폰과 같은 부호(g) — 화면이 하늘을 보면 -1
    private var dolgo = false
    private var majchuneun = false
    private var deutneun = false
    private var ppeotT = 0L              // 팔을 뻗기 시작한 때(0 이면 아님)
    private var ppeotMal = false         // 이번에 뻗은 뒤 말했는가
    private var jindongT = 0L
    private var salpinT = 0L
    private val rm = FloatArray(9)
    private val ori = FloatArray(3)
    private val pyeongak: Double by lazy {
        // 한국(서울) 편각 — 자북을 진북으로
        try { GeomagneticField(37.55f, 126.99f, 50f, System.currentTimeMillis()).declination.toDouble() } catch (e: Exception) { -8.0 }
    }

    fun sijak(c: Context) { if (ctx == null) ctx = c.applicationContext }

    /** 지팡이를 쥐지 않은 손에 차셨는가(묻기에 답하신 것으로) */
    val sseulSuItda: Boolean
        get() {
            val j = WatchModel.jipangiSon
            val w = WatchModel.watchSonmok
            return j.isNotEmpty() && w.isNotEmpty() && j != w
        }

    /** 팔 방향 = 나침반 + 어긋남 */
    private val eogeutnam: Double
        get() {
            val p = WatchModel.prefs
            if (p.contains("garikiPyeon")) return p.getFloat("garikiPyeon", 0f).toDouble()
            return if (WatchModel.watchSonmok == "oreun") -90.0 else 90.0
        }

    private val sm: SensorManager? get() = ctx?.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val sensorItda: Boolean
        get() { val m = sm ?: return false; return m.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null && m.getDefaultSensor(Sensor.TYPE_GRAVITY) != null }

    /** 폰 길눈이 보낸 가야 할 쪽 — null 이면 걷기가 끝난 것 */
    fun mokBatda(b: Double?) {
        mok = b
        dasiJeonghagi()
    }

    private fun dasiJeonghagi() {
        val halil = kyeojim && sseulSuItda && mok != null && sensorItda
        if (halil && !dolgo) kyeogi() else if (!halil && dolgo) kkeugi()
    }

    private fun deutgi(on: Boolean) {
        val m = sm ?: return
        if (on && !deutneun) {
            m.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { m.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME, main) }
            m.getDefaultSensor(Sensor.TYPE_GRAVITY)?.let { m.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME, main) }
            deutneun = true
        } else if (!on && deutneun) {
            m.unregisterListener(this)
            deutneun = false
            heading = null
        }
    }

    private fun kyeogi() {
        dolgo = true
        deutgi(true)
    }

    private fun kkeugi() {
        dolgo = false
        if (!majchuneun) deutgi(false)
        ppeotT = 0L; ppeotMal = false
    }

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val v = if (e.values.size > 4) e.values.copyOf(4) else e.values
                SensorManager.getRotationMatrixFromVector(rm, v)
                SensorManager.getOrientation(rm, ori)
                val az = Math.toDegrees(ori[0].toDouble()) + pyeongak
                heading = (az + 720) % 360
            }
            Sensor.TYPE_GRAVITY -> {
                gz = -e.values[2] / SensorManager.GRAVITY_EARTH.toDouble()
                val now = System.currentTimeMillis()
                if (dolgo && now - salpinT >= 100) { salpinT = now; salpigi() }
            }
        }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    fun chai(a: Double, b: Double): Double {
        var d = (a - b) % 360
        if (d > 180) d -= 360
        if (d < -180) d += 360
        return d
    }

    /** 0.1초마다 — 손등이 위로(화면이 하늘로) 0.4초 넘게 있으면 가리키는 중 */
    private fun salpigi() {
        val now = System.currentTimeMillis()
        if (gz >= -0.8) { ppeotT = 0L; ppeotMal = false; return }
        if (ppeotT == 0L) ppeotT = now
        val m = mok ?: return
        val h = heading ?: return
        if (now - ppeotT < 400) return
        val pal = (h + eogeutnam + 720) % 360
        val d = chai(m, pal)   // + 이면 가야 할 쪽이 팔보다 오른쪽
        val maja = abs(d) <= 12
        if (!ppeotMal) {
            ppeotMal = true
            WatchModel.speak(if (maja) "맞습니다" else (if (d > 0) "3시 쪽으로" else "9시 쪽으로"), "click")
            jindongT = now
            return
        }
        val gan = if (maja) 1000L else 700L
        if (now - jindongT < gan) return
        jindongT = now
        WatchModel.haptic(if (maja) "maja" else if (d > 0) "right" else "left")
    }

    /** 가리키기 방향 맞추기 — 말이 끝나고 곧(6초 뒤) 팔을 몸 정면으로 곧게 뻗은 채로 폰이 잰 몸 방향과 워치 나침반을 맞춤 */
    fun majchugi() {
        if (!sensorItda) { WatchModel.speak("이 워치에서는 방향을 잴 수 없습니다.", "failure"); return }
        WatchModel.speak("이 말이 끝나고 곧 잽니다. 워치 찬 팔을 손등이 위로 오게 몸 정면으로 곧게 뻗고 기다리십시오.", "start")
        majchuneun = true
        deutgi(true)
        main.postDelayed({
            val g = gz
            val h = heading
            majchuneun = false
            if (!dolgo) deutgi(false)
            if (g >= -0.8 || h == null) {
                WatchModel.speak("팔이 곧게 뻗어 있지 않아 재지 못했습니다. 손등이 위로 오게 하고 다시 해 주십시오.", "failure")
                return@postDelayed
            }
            WatchModel.momBangMureum { b ->
                if (b == null) {
                    WatchModel.speak("폰 길눈에서 몸 방향을 받지 못했습니다. 폰 길눈으로 점지도 따라 걷기를 켜고 다시 해 주십시오.", "failure")
                    return@momBangMureum
                }
                WatchModel.prefs.edit().putFloat("garikiPyeon", chai(b, h).toFloat()).apply()
                WatchModel.speak("가리키기 방향을 맞췄습니다.", "success")
            }
        }, 6000)
    }
}
