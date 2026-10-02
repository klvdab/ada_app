// 안드로이드 길눈 — 흔들면 자리 번호, 국가지점번호(2.7.0, 묶음 b2 점지도 마저, 대표님 지시)
// 아이폰 Nanum.swift 의 Jijeom(웹 길눈 jijeom.js 와 같은 셈)과 Heundeul(2.9.0), SeoljeongDeo 의 흔들면 화면을 옮겼습니다.
//   국가지점번호 — 산과 강과 바닷가처럼 도로명주소가 없는 곳에서 119가 쓰는 위치 번호. 폰이 스스로 셈(통신이 끊겨도 됨)
//   흔들면 — 설정에서 켬(처음에는 꺼 둠). 세게 두 번(1.2초 안) 흔드셨을 때만, 6초에 한 번
//     하는 일: 긴급통화 열기(처음) 또는 지금 자리의 국가지점번호 말하기
package kr.or.ada.app.gilnun

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** 국가지점번호 — 폰이 스스로 셈 */
object Jijeom {
    private const val A = 6378137.0
    private const val F = 1 / 298.257222101
    private const val E2 = F * (2 - F)
    private const val EP2 = E2 / (1 - E2)
    private const val JA = "가나다라마바사아자차카타파하"
    private val SUT = listOf("영", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구")

    private fun m(phi: Double): Double {
        val a0 = 1 - E2 / 4 - 3 * E2 * E2 / 64 - 5 * E2 * E2 * E2 / 256
        val a2 = 3.0 / 8 * (E2 + E2 * E2 / 4 + 15 * E2 * E2 * E2 / 128)
        val a4 = 15.0 / 256 * (E2 * E2 + 3 * E2 * E2 * E2 / 4)
        val a6 = 35 * E2 * E2 * E2 / 3072
        return A * (a0 * phi - a2 * sin(2 * phi) + a4 * sin(4 * phi) - a6 * sin(6 * phi))
    }

    private fun utmk(lat: Double, lon: Double): Pair<Double, Double> {
        val k0 = 0.9996
        val lat0 = 38 * Math.PI / 180
        val lon0 = 127.5 * Math.PI / 180
        val phi = lat * Math.PI / 180
        val lam = lon * Math.PI / 180
        val n = A / sqrt(1 - E2 * sin(phi) * sin(phi))
        val t = tan(phi) * tan(phi)
        val c = EP2 * cos(phi) * cos(phi)
        val aa = (lam - lon0) * cos(phi)
        val a2 = aa * aa
        val a3 = a2 * aa
        val a4 = a3 * aa
        val a5 = a4 * aa
        val a6 = a5 * aa
        val x = k0 * n * (aa + (1 - t + c) * a3 / 6 + (5 - 18 * t + t * t + 72 * c - 58 * EP2) * a5 / 120) + 1_000_000
        val y1 = m(phi) - m(lat0)
        val y2 = n * tan(phi) * (a2 / 2 + (5 - t + 9 * c + 4 * c * c) * a4 / 24 + (61 - 58 * t + t * t + 600 * c - 330 * EP2) * a6 / 720)
        return Pair(x, k0 * (y1 + y2) + 2_000_000)
    }

    /** (한글 두 자, 동쪽 넉 자, 북쪽 넉 자) — 우리나라 밖이면 null */
    fun gyesan(lat: Double, lon: Double): Triple<String, String, String>? {
        val p = utmk(lat, lon)
        val e = p.first - 700_000
        val n = p.second - 1_300_000
        if (e < 0 || n < 0) return null
        val ei = (e / 100_000).toInt()
        val ni = (n / 100_000).toInt()
        if (ei >= JA.length || ni >= JA.length) return null
        val e4 = ((e % 100_000) / 10).toInt()
        val n4 = ((n % 100_000) / 10).toInt()
        return Triple("${JA[ei]}${JA[ni]}", String.format(Locale.US, "%04d", e4), String.format(Locale.US, "%04d", n4))
    }

    /** 소리로 — 숫자는 한 자씩(119에 불러 드릴 때 헷갈리지 않게) */
    fun mal(lat: Double, lon: Double): String? {
        val j = gyesan(lat, lon) ?: return null
        fun han(s: String): String = s.mapNotNull { if (it.isDigit()) SUT[it - '0'] else null }.joinToString(" ")
        return "국가지점번호 ${j.first[0]} ${j.first[1]}, ${han(j.second)}, ${han(j.third)}"
    }

    fun geul(lat: Double, lon: Double): String? {
        val j = gyesan(lat, lon) ?: return null
        return "${j.first} ${j.second} ${j.third}"
    }
}

/** 흔들면 — 긴급통화 열기 또는 자리 번호 말하기(설정에서 켬, 처음에는 꺼 둠) */
object Heundeul : SensorEventListener {
    private var ctx: Context? = null
    private var sm: SensorManager? = null
    private var dolgo = false
    private var majimak = 0L
    private var cheotHeundeul = 0L
    private val main = Handler(Looper.getMainLooper())
    /** 긴급통화 열기 — GilnunActivity 가 채움(길 찾기 첫 화면으로 돌린 뒤 긴급통화서비스 화면을 엶) */
    var ginGeupYeolgi: (() -> Unit)? = null

    /** 앱이 켜질 때 — 설정이 켜져 있으면 살핌 */
    fun sijak(c: Context) {
        ctx = c.applicationContext
        JeomSeol.sijak(c)
        matchugi()
    }

    /** 설정에 맞춰 켜거나 끔 */
    fun matchugi() {
        if (JeomSeol.heundeulKyeojim) kyeogi() else kkeugi()
    }

    private fun kyeogi() {
        if (dolgo) return
        val c = ctx ?: return
        val s = c.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val a = s.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sm = s
        dolgo = s.registerListener(this, a, 100_000, main)   // 아이폰과 같이 0.1초마다
    }

    private fun kkeugi() {
        if (!dolgo) return
        sm?.unregisterListener(this)
        dolgo = false
    }

    override fun onSensorChanged(e: SensorEvent) {
        val x = e.values[0] / 9.81
        val y = e.values[1] / 9.81
        val z = e.values[2] / 9.81
        val g = sqrt((x * x + y * y + z * z).toDouble())   // 중력 단위(아이폰 가속도와 같은 잣대)
        if (g <= 2.6) return
        val now = System.currentTimeMillis()
        // 세게 두 번(1.2초 안) 흔드셨을 때만 — 걷다가 한 번 튀는 것은 넘김
        val c = cheotHeundeul
        if (c > 0 && now - c > 250 && now - c < 1200) {
            cheotHeundeul = 0L
            if (now - majimak <= 6000) return
            majimak = now
            hagi()
        } else if (c == 0L || now - c >= 1200) {
            cheotHeundeul = now
        }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    private fun hagi() {
        Girok.namgi("heundeul", mapOf("il" to JeomSeol.heundeulIl))
        if (JeomSeol.heundeulIl == "jari") {
            jariBeonhoMal()
        } else {
            if (GinGeup.sangtae != GinGeupSangtae.EOPSEUM) return
            Sori.mal("흔드셨습니다. 긴급통화서비스를 엽니다.", MalGeup.GYEONGGO)
            val f = ginGeupYeolgi
            if (f != null) f() else MalHagi.hwalseong?.get()?.cheotHwamyeonEuro(GinGeupHwamyeon())
        }
    }

    fun jariBeonhoMal() {
        val w = Wichi.jigeum ?: run { Sori.mal("아직 자리를 잡는 중입니다. 잠시 뒤 다시 흔들어 주십시오."); return }
        val m = Jijeom.mal(w.lat, w.lon) ?: "이곳은 국가지점번호를 셈할 수 없는 곳입니다."
        Sori.mal(m + ". 위성 오차 약 ${w.ochae.toInt()}미터.", MalGeup.GYEONGGO)
    }
}

/** 흔들면 자리 번호 화면(설정 탭) */
class HeundeulHwamyeon : Hwamyeon("흔들면 자리 번호") {
    override fun chaeugi(t: GilnunActivity) {
        JeomSeol.sijak(t)
        val il = if (JeomSeol.heundeulIl == "jari") "내 자리 번호 말하기" else "긴급통화 열기"
        t.danchu("폰 흔들기 — " + (if (JeomSeol.heundeulKyeojim) "켜져 있음 (누르면 끕니다)" else "꺼져 있음 (누르면 켭니다)")) {
            JeomSeol.heundeulKyeojim = !JeomSeol.heundeulKyeojim
            Heundeul.sijak(t)
            Sori.mal(if (JeomSeol.heundeulKyeojim) "폰을 세게 두 번 흔드시면 " + (if (JeomSeol.heundeulIl == "jari") "지금 자리 번호를 말씀드립니다" else "긴급통화서비스가 열립니다") + "."
                else "폰 흔들기를 껐습니다.")
            t.dasiGeurigi()
        }
        t.danchu("흔들면 하는 일 — 지금은 $il (누르면 바뀝니다)") {
            JeomSeol.heundeulIl = if (JeomSeol.heundeulIl == "jari") "gingeup" else "jari"
            Sori.mal(if (JeomSeol.heundeulIl == "jari") "폰을 세게 흔드시면 지금 자리의 국가지점번호를 말해 드립니다."
                else "폰을 세게 흔드시면 긴급통화서비스가 곧바로 열립니다.")
            t.dasiGeurigi()
        }
        t.danchu("지금 자리 번호 듣기") { Heundeul.jariBeonhoMal() }
        t.geul("국가지점번호는 산과 강과 바닷가처럼 도로명주소가 없는 곳에서 119가 쓰는 위치 번호입니다. 폰이 스스로 셈하므로 통신이 끊겨도 됩니다. 걷다가 한 번 튀는 것은 흔들기로 치지 않고, 세게 두 번 흔드셔야 합니다.")
    }
}
