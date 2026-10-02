// 안드로이드 길눈 — 사람 감지(묶음 b3_kamera, 아이폰 SaramGamji.swift 2.18.0 을 같은 잣대·같은 말로 옮김)
// 걸을 때 앞에 사람이 있으면 "1시 방향 사람, 3걸음"처럼 알리고, 가까워질수록 확신음이 빨라집니다.
// 아이폰은 애플 사람 알아보기(Vision)를 쓰고, 안드로이드는 앱 안에 실린 ML Kit 자세 알아보기(사람 몸의 어깨·엉덩이 자리)를 씁니다.
//   거리: 어깨 가운데~엉덩이 가운데(몸통) 0.5미터를 기준 삼아 카메라 한 대로 어림(아이폰은 키 1.7미터 기준). 몸통이 안 보이면 어깨 너비 0.4미터로.
//   다름: ML Kit 자세 알아보기는 한 번에 가장 뚜렷한 한 사람만 잡으므로 "앞에 사람 몇 명"은 말하지 않음(아이폰은 여러 사람을 셈).
// 얼굴을 알아보거나 사진을 담지 않고, 인터넷 없이 폰 안에서만 돕니다. 특허를 비켜 가려고 화면에 카메라 영상을 띄우지 않습니다(아이폰과 같음).
package kr.or.ada.app.gilnun

import android.os.Handler
import android.os.Looper
import android.view.View
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.tan

object SaramGamji : KameraNunBupum {
    override val ireum = "사람 감지"
    override var kyeojim = false
        private set
    var sangtae = ""
        private set
    var byeonhwa: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var tamji: PoseDetector? = null
    @Volatile private var sijya = 63.0   // 카메라 가로(누운 쪽) 시야 도
    // 아래는 화면 줄에서만
    private var malT = 0L
    private var majimakMal = ""
    private var boim = false
    private var eopT = 0L
    private var eopMal = true
    private var saeT = 0L
    private var georiNow = 99.0
    private var ttakGan = 0L
    private val ttak = object : Runnable {
        override fun run() {
            if (ttakGan <= 0) return
            Eum.naegi(EumJong.HWAKSIN)
            main.postDelayed(this, ttakGan)
        }
    }
    private val sigye = object : Runnable {
        override fun run() {
            if (!kyeojim) return
            salpigi()
            if (kyeojim) main.postDelayed(this, 10000)
        }
    }

    init { Kamera.deungrok(this) }

    // MARK: 켜기·끄기

    fun kyeogi(a: GilnunActivity? = null) {
        val t = a ?: Kamera.hwalseongEotgi() ?: return
        if (kyeojim) return
        Kamera.heorak(t, this, "사람 감지를") { sijak(t) }
    }

    private fun sijak(t: GilnunActivity) {
        sijya = Kamera.garoSijya(t)
        val d = tamji ?: PoseDetection.getClient(
            PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build()
        ).also { tamji = it }
        // 화면 영상 없이(bogi = null)
        Kamera.sijak(t, this, null, 400L, { jang -> bunseok(d, jang) }) { ok ->
            if (ok) {
                kyeojim = true
                boim = false; eopMal = true; majimakMal = ""; georiNow = 99.0
                saeT = System.currentTimeMillis()
                sangtae = "사람을 살피는 중입니다."
                mal("사람 감지를 시작합니다. 폰을 가슴 앞에 세워 들고 걸으십시오.", sseuGi = true)
                Girok.namgi("saram", mapOf("kyeogi" to true))
                main.removeCallbacks(sigye)
                main.postDelayed(sigye, 10000)
                byeonhwa?.invoke()
            }
        }
    }

    override fun kkeugi(malHagi: Boolean) {
        Kamera.kkeugi(this)
        main.removeCallbacks(sigye)
        ttakMeomchum()
        if (!kyeojim) return
        kyeojim = false
        sangtae = ""
        if (malHagi) Sori.mal("사람 감지를 멈췄습니다.", MalGeup.JEONGBO)
        byeonhwa?.invoke()
    }

    fun jigeumMal() {
        Sori.mal(if (kyeojim) (if (sangtae.isEmpty()) "사람을 살피는 중입니다." else sangtae) else "사람 감지가 꺼져 있습니다.")
    }

    /** 10분 동안 사람을 한 번도 못 보면 카메라를 끔(배터리) */
    private fun salpigi() {
        if (!kyeojim || System.currentTimeMillis() - saeT <= 600000) return
        kkeugi(false)
        mal("10분 동안 사람이 보이지 않아 카메라를 껐습니다.", sseuGi = true)
    }

    // MARK: 한 장마다(분석 줄) — 0.4초에 한 번

    private fun bunseok(d: PoseDetector, jang: KameraJang) {
        val w = jang.garo.toDouble()
        val h = jang.sero.toDouble()
        d.process(jang.inputImage())
            .addOnSuccessListener { pose -> allida(saramChatgi(pose, w, h)) }
            .addOnCompleteListener { jang.kkeut() }
    }

    /** (거리 미터, 몸 기준 도 — 오른쪽 +) 가까운 차례 */
    private fun saramChatgi(pose: Pose, w: Double, h: Double): List<Pair<Double, Double>> {
        if (pose.allPoseLandmarks.isEmpty() || w <= 0 || h <= 0) return emptyList()
        fun jeom(ty: Int): PoseLandmark? = pose.getPoseLandmark(ty)?.takeIf { it.inFrameLikelihood >= 0.5f }
        val ls = jeom(PoseLandmark.LEFT_SHOULDER)
        val rs = jeom(PoseLandmark.RIGHT_SHOULDER)
        val lh = jeom(PoseLandmark.LEFT_HIP)
        val rh = jeom(PoseLandmark.RIGHT_HIP)
        // 세운 폰 기준 시야 — 세로 시야는 카메라 가로 시야, 가로 시야는 비율로 좁힘(아이폰과 같음)
        val seroRad = sijya * PI / 180
        val garoRad = 2 * atan(tan(seroRad / 2) * (w / max(1.0, h)))
        val fy = (h / 2) / tan(seroRad / 2)
        val fx = (w / 2) / tan(garoRad / 2)
        var d: Double? = null
        var cx = 0.0
        if (ls != null && rs != null && lh != null && rh != null) {
            val sx = (ls.position.x + rs.position.x) / 2.0
            val sy = (ls.position.y + rs.position.y) / 2.0
            val hx = (lh.position.x + rh.position.x) / 2.0
            val hy = (lh.position.y + rh.position.y) / 2.0
            val mom = hypot(sx - hx, sy - hy)
            if (mom > h * 0.02) d = 0.5 * fy / mom
            cx = (sx + hx) / 2
        } else if (ls != null && rs != null) {
            val eokkae = hypot((ls.position.x - rs.position.x).toDouble(), (ls.position.y - rs.position.y).toDouble())
            if (eokkae > w * 0.02) d = 0.4 * fx / eokkae
            cx = (ls.position.x + rs.position.x) / 2.0
        }
        val g = d ?: return emptyList()
        val gak = (cx / w - 0.5) * garoRad * 180 / PI
        return listOf(g to gak)
    }

    // MARK: 알리기(화면 줄)

    private fun allida(saram: List<Pair<Double, Double>>) {
        if (!kyeojim) return
        val now = System.currentTimeMillis()
        val ga = saram.firstOrNull()
        if (ga == null || ga.first > 8) {
            if (boim && now - eopT > 2500) {
                boim = false
                ttakMeomchum()
                georiNow = 99.0
                sangtae = "앞에 사람이 없습니다."
                if (!eopMal) { eopMal = true; mal("앞에 사람이 없습니다.", MalGeup.JEONGBO) }
            }
            return
        }
        eopT = now
        saeT = now
        val georeum = max(1, (ga.first / KameraNun.bopok).roundToInt())
        val si = KameraNun.sigye(ga.second)
        val su = saram.count { it.first <= 8 }
        val m = (if (su > 1) "앞에 사람 ${su}명, 가장 가까운 사람 " else "") + "${si}시 방향 사람, ${georeum}걸음"
        sangtae = m
        georiNow = ga.first
        ttakMatchum(ga.first)
        val cheoeum = !boim
        boim = true
        eopMal = false
        if (cheoeum || (now - malT >= 3000 && m != majimakMal)) {
            malT = now
            majimakMal = m
            mal("$m.")
        }
    }

    /** 가까워질수록 빨라지는 확신음 — 1.5미터 안 0.5초, 3미터 안 1초, 그 밖엔 울리지 않음 */
    private fun ttakMatchum(d: Double) {
        val g = if (d < 1.5) 500L else if (d < 3) 1000L else 0L
        if (g == ttakGan) return
        ttakGan = g
        main.removeCallbacks(ttak)
        if (g > 0) main.postDelayed(ttak, g)
    }

    private fun ttakMeomchum() {
        main.removeCallbacks(ttak)
        ttakGan = 0
    }

    private fun mal(t: String, g: MalGeup = MalGeup.ANNAE, sseuGi: Boolean = false) = KameraNun.mal(t, g, sseuGi)
}

/** 사람 감지 화면 — 둘러보기 탭 */
class SaramGamjiHwamyeon : KameraHwamyeon("사람 감지") {
    override val bupum: KameraNunBupum get() = SaramGamji
    override fun kyeogi(t: GilnunActivity) = SaramGamji.kyeogi(t)

    override fun juljul(t: GilnunActivity): View {
        val g = SaramGamji
        val kb = t.danchu(kGeul()) { if (g.kyeojim) g.kkeugi(true) else g.kyeogi(t) }
        g.byeonhwa = {
            if (t.wiHwamyeon === this) {
                val n = kGeul()
                if (kb.text.toString() != n) kb.text = n
            }
        }
        t.danchu("지금 앞 다시 듣기") { g.jigeumMal() }
        malsoriDanchu(t)
        alaDuSil(t, "걸으실 때 앞에 사람이 있으면 몇 시 방향, 몇 걸음인지 알려 드립니다. 3미터 안으로 가까워지면 확신음이 1초마다, 1.5미터 안이면 0.5초마다 울립니다. 말은 3초에 한 번쯤 바뀔 때만 합니다. 사람이 사라지면 앞에 사람이 없습니다라고 한 번 알립니다. 화면을 여시면 바로 시작합니다. 폰을 가슴 앞에 세워 들거나 목걸이로 걸고 걸으십시오. 안드로이드에서는 한 번에 가장 가까이 뚜렷한 한 사람을 알려 드립니다. 거리는 사람 몸통 길이를 기준으로 어림한 것이라 앉은 사람이나 아이는 실제보다 멀게 들릴 수 있습니다. 카메라 눈 말소리를 끄시면 말 없이 확신음으로만 알립니다. 10분 동안 사람이 보이지 않거나 이 화면을 떠나시면 카메라를 끕니다. 말로 하기에서 사람 있어라고 하셔도 열립니다. 얼굴을 알아보거나 사진을 담지 않으며, 인터넷 없이 폰 안에서만 돕니다.")
        return kb
    }

    private fun kGeul() = if (SaramGamji.kyeojim) "멈추기 — 사람을 살피는 중" else "이어 살피기 — 카메라를 켜고 사람 감지"
}
