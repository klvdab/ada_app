// 갤럭시 워치 길눈 — 지팡이 떨림 기록 (2.5.0판, 빌드 261002-A8, 대표님 지시: 아이폰 길눈 워치 JipangiTteollim.swift 2.37.0 을 갤럭시 워치로) — 연구 1단계(기록 모으기)
// 워치를 지팡이 쥔 손에 차셨을 때, 지팡이 끝이 바닥을 쓸고 두드리는 떨림이 손목까지 옵니다.
// 그 떨림으로 점자블록·보도블록·아스팔트 같은 바닥을 가려낼 수 있는지 알아보려고, 먼저 바닥 이름을 붙인 기록을 모읍니다.
//   1) 워치에서 바닥 종류를 고르면 1초에 100번 손목 흔들림(중력을 뺀 가속도 세 축 + 돌림 세 축)을 담기 시작
//   2) 그만을 누르거나 5분이 되면 멈추고, 기록을 폰 길눈으로 보냄 → 폰이 협회 나스(연구용)로 올림. 5초보다 짧으면 버림
//   폰이 곁에 없어도 데이터 층이 들고 있다가 이어지면 넘김. 데이터 층에 넘기기 전에 꺼지면 다음에 켤 때 다시 보냄
// 한 줄 = float32 일곱 개(28바이트, 리틀 엔디언) [t 초, ax, ay, az, gx, gy, gz] — 아이폰과 같은 꼴
//   가속도는 아이폰과 같은 단위(g)로 바꿔 담음(안드로이드는 초제곱 미터). 돌림은 둘 다 초당 라디안. 축의 부호는 기기마다 다를 수 있어
//   폰이 올릴 때 판 이름 끝에 -android 를 붙여 나스에서 가려 보게 함
package kr.or.ada.app.wear

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object JipangiTteollim : SensorEventListener {
    val badakdeul = listOf("점자블록", "보도블록", "아스팔트", "흙길", "그 밖의 바닥")
    const val HZ = 100
    const val CHOEDAE = 300   // 한 번에 5분까지

    private val main = Handler(Looper.getMainLooper())
    private val jamgeum = Any()
    private var ctx: Context? = null

    @Volatile var damneunJung = false
        private set
    var pyo = ""
        private set
    var cho = 0
        private set
    private var beop: ByteBuffer? = null
    private var sijakMs = 0L
    private var t0Ns = 0L
    private var majimakNs = 0L
    private val gyro = FloatArray(3)
    private var sil: HandlerThread? = null
    private val sigye = object : Runnable {
        override fun run() {
            if (!damneunJung) return
            cho = ((System.currentTimeMillis() - sijakMs) / 1000).toInt()
            WatchModel.byeonhwa?.invoke()
            if (cho >= CHOEDAE) { geuman(); return }
            main.postDelayed(this, 1000)
        }
    }

    fun sijak(c: Context) { if (ctx == null) ctx = c.applicationContext }

    private val ham: File?
        get() {
            val c = ctx ?: return null
            val d = File(c.filesDir, "tteollim")
            if (!d.exists()) d.mkdirs()
            return d
        }

    /** 바닥을 고르면 곧바로 담기 시작 */
    fun sijakHagi(badak: String) {
        if (damneunJung) return
        val sm = ctx?.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val la = sm?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val gy = sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (sm == null || la == null || gy == null) {
            WatchModel.speak("이 워치에서는 떨림을 잴 수 없습니다.", "failure"); return
        }
        pyo = badak; cho = 0
        synchronized(jamgeum) {
            beop = ByteBuffer.allocate((HZ * CHOEDAE + 500) * 28).order(ByteOrder.LITTLE_ENDIAN)
            t0Ns = 0L; majimakNs = 0L
            gyro.fill(0f)
        }
        sijakMs = System.currentTimeMillis()
        damneunJung = true
        WatchModel.kkaeeoBojang(true)   // 손목을 내려도 멈추지 않게
        WatchModel.speak("$badak 기록을 시작합니다. 평소처럼 지팡이를 쓰며 걸으십시오.", "start")
        val t = HandlerThread("tteollim").also { it.start() }
        sil = t
        val h = Handler(t.looper)
        sm.registerListener(this, gy, 1_000_000 / HZ, h)
        sm.registerListener(this, la, 1_000_000 / HZ, h)
        main.removeCallbacks(sigye)
        main.postDelayed(sigye, 1000)
        WatchModel.byeonhwa?.invoke()
    }

    override fun onSensorChanged(e: SensorEvent) {
        synchronized(jamgeum) {
            if (!damneunJung) return
            if (e.sensor.type == Sensor.TYPE_GYROSCOPE) {
                gyro[0] = e.values[0]; gyro[1] = e.values[1]; gyro[2] = e.values[2]
                return
            }
            if (e.sensor.type != Sensor.TYPE_LINEAR_ACCELERATION) return
            // 1초에 100줄 — 센서가 더 빨리 주면 솎음
            if (majimakNs != 0L && e.timestamp - majimakNs < 9_000_000L) return
            majimakNs = e.timestamp
            if (t0Ns == 0L) t0Ns = e.timestamp
            val b = beop ?: return
            if (b.remaining() < 28) return
            val g = SensorManager.GRAVITY_EARTH
            b.putFloat((e.timestamp - t0Ns) / 1e9f)
            b.putFloat(e.values[0] / g); b.putFloat(e.values[1] / g); b.putFloat(e.values[2] / g)
            b.putFloat(gyro[0]); b.putFloat(gyro[1]); b.putFloat(gyro[2])
        }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    /** 그만 — 담은 것을 폰 길눈으로 */
    fun geuman() {
        if (!damneunJung) return
        val bytes: ByteArray = synchronized(jamgeum) {
            damneunJung = false
            val b = beop
            beop = null
            if (b != null) b.array().copyOf(b.position()) else ByteArray(0)
        }
        try { (ctx?.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)?.unregisterListener(this) } catch (e: Exception) {}
        sil?.quitSafely(); sil = null
        main.removeCallbacks(sigye)
        WatchModel.kkaeeoBojang(false)
        WatchModel.byeonhwa?.invoke()
        val gil = ((System.currentTimeMillis() - sijakMs) / 1000).toInt()
        if (gil < 5 || bytes.size <= 28 * 100) {
            WatchModel.speak("기록이 너무 짧아 버렸습니다. 5초 넘게 걸어 주십시오.", "failure"); return
        }
        val d = ham ?: return
        val son = WatchModel.watchSonmok
        val f = File(d, "tteollim_${sijakMs / 1000}_${gil}_${son}_${pyo.replace("_", " ")}.bin")
        try { f.writeBytes(bytes) } catch (e: Exception) {
            WatchModel.speak("기록을 담지 못했습니다.", "failure"); return
        }
        WatchModel.tteollimBonae(f)
        WatchModel.speak("$pyo 기록 ${gil / 60}분 ${gil % 60}초를 담아 폰 길눈으로 보냅니다.", "success")
    }

    /** 데이터 층에 넘기지 못하고 남은 기록 — 켤 때 다시 보냄 */
    fun namuenGeotBonaegi() {
        val d = ham ?: return
        for (f in d.listFiles() ?: emptyArray()) if (f.name.endsWith(".bin")) WatchModel.tteollimBonae(f)
    }
}
