// 안드로이드 길눈 — 빛 알아보기(묶음 b3_kamera, 아이폰 BitAlgi.swift 2.22.0 을 같은 잣대·같은 말로 옮김)
// 불이 켜져 있는지, 창이 어느 쪽인지를 소리 높낮이로 알려 드립니다. 밝을수록 높은 소리가 납니다.
// 폰을 천천히 돌리면 가장 밝은 쪽에서 "이쪽이 가장 밝습니다"라고 알리고, 불이 켜지거나 꺼지면 알립니다.
// 밝기: 아이폰은 사진마다 붙는 장면 밝기 값(EXIF BrightnessValue)을 씀. 안드로이드는 카메라가 장마다 알려 주는
//   노출 시간·감도·조리개로 같은 셈(APEX 밝기 = 조리개 + 시간 − 감도)을 하고, 그 장의 평균 밝기로 자동 노출이 못 맞춘 만큼을 바로잡음.
//   노출 값을 주지 않는 폰이면 아이폰과 같이 점들의 평균 밝기로 어림(0~1 을 -4~8 로).
// 폰 안에서만 하며, 사진은 담지도 보내지도 않습니다.
package kr.or.ada.app.gilnun

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object BitAlgi : KameraNunBupum {
    override val ireum = "빛 알아보기"
    override var kyeojim = false
        private set
    var sangtae = ""
        private set
    /** 높낮이 소리 켜기·끄기(아이폰과 같이 앱을 다시 열면 켜짐) */
    var nopnaji = true
    var byeonhwa: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    // 아래는 화면 줄에서만
    private val girok = ArrayList<Pair<Long, Double>>()   // 지난 6초의 밝기
    private var apDangye = ""      // 앞 장의 밝기 말(두 장 잇달아 같아야 알림)
    private var malDangye = ""
    private var malT = 0L
    private var gajangT = 0L
    private var kyeogiT = 0L       // 불 켜짐·꺼짐 알림
    private var saeT = 0L
    private var jigeum = 0.0
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
        Kamera.heorak(t, this, "빛 알아보기를") { sijak(t) }
    }

    private fun sijak(t: GilnunActivity) {
        Kamera.sijak(t, this, null, 400L, { jang -> bunseok(jang) }) { ok ->
            if (ok) {
                kyeojim = true
                girok.clear(); apDangye = ""; malDangye = ""
                saeT = System.currentTimeMillis()
                sangtae = "빛을 살피는 중입니다."
                mal("빛 알아보기를 시작합니다. 밝을수록 높은 소리가 납니다. 폰을 천천히 돌려 보십시오.", sseuGi = true)
                Girok.namgi("bit", mapOf("kyeogi" to true))
                main.removeCallbacks(sigye)
                main.postDelayed(sigye, 10000)
                byeonhwa?.invoke()
            }
        }
    }

    override fun kkeugi(malHagi: Boolean) {
        Kamera.kkeugi(this)
        main.removeCallbacks(sigye)
        if (!kyeojim) return
        kyeojim = false
        sangtae = ""
        if (malHagi) Sori.mal("빛 알아보기를 멈췄습니다.", MalGeup.JEONGBO)
        byeonhwa?.invoke()
    }

    fun jigeumMal() {
        Sori.mal(if (kyeojim) (dangye(jigeum) + ".") else "빛 알아보기가 꺼져 있습니다.")
    }

    /** 10분 동안 밝기가 바뀌지 않으면 카메라를 끔(배터리) */
    private fun salpigi() {
        if (!kyeojim || System.currentTimeMillis() - saeT <= 600000) return
        kkeugi(false)
        mal("10분 동안 밝기가 바뀌지 않아 카메라를 껐습니다.", sseuGi = true)
    }

    // MARK: 한 장마다(분석 줄) — 0.4초에 한 번

    private fun bunseok(jang: KameraJang) {
        val b = try { jangmyeonBalgi(jang) } catch (e: Exception) { null }
        jang.kkeut()
        if (b != null) main.post { allida(b) }
    }

    /** 장면 밝기(-4 캄캄 ~ 10 햇빛, 아이폰 밝기 값과 같은 자) */
    fun jangmyeonBalgi(jang: KameraJang): Double {
        val l = jang.balgi()
        val nc = jang.noChul
        if (nc != null && nc.noChulNs > 0 && nc.iso > 0) {
            val av = 2 * log2(max(0.7, nc.jori.toDouble()))
            val tv = log2(1e9 / nc.noChulNs.toDouble())
            val sv = log2(0.32 * nc.iso)
            var bv = av + tv - sv
            // 자동 노출이 가운데 회색(18%)에 못 맞춘 만큼 — 아주 밝거나 아주 어두운 곳
            val seonhyeong = max(0.004, l.pow(2.2))
            bv += log2(seonhyeong / 0.18)
            return bv.coerceIn(-6.0, 12.0)
        }
        return l * 12 - 4   // 0~1 을 -4~8 으로(아이폰 jeomBalgi 와 같음)
    }

    // MARK: 알리기(화면 줄)

    /** 밝기 값(-4 캄캄 ~ 10 햇빛)을 말로 */
    fun dangye(b: Double): String {
        if (b < -2.5) return "캄캄합니다"
        if (b < 0.5) return "어둡습니다"
        if (b < 4) return "실내 불빛 밝기입니다"
        if (b < 7.5) return "밝습니다"
        return "햇빛처럼 아주 밝습니다"
    }

    private fun allida(b: Double) {
        if (!kyeojim) return
        val now = System.currentTimeMillis()
        jigeum = b
        if (nopnaji) ttil(b)
        // 1.5초 전과 견주어 불 켜짐·꺼짐
        val jeon = girok.lastOrNull { now - it.first in 1200L..2000L }?.second
        girok.add(now to b)
        girok.removeAll { now - it.first > 6000 }
        val d = dangye(b)
        sangtae = "$d."
        if (jeon != null && abs(b - jeon) >= 3 && now - kyeogiT > 4000) {
            kyeogiT = now; malT = now; saeT = now
            malDangye = d; apDangye = d
            mal(if (b > jeon) "불이 켜진 듯합니다. $d." else "불이 꺼진 듯합니다. $d.")
            return
        }
        // 폰을 돌리는 동안 가장 밝은 쪽 — 6초 동안 차이가 크고 지금이 가장 밝을 때
        if (girok.size >= 8) {
            var mx = b
            var mn = b
            for (x in girok) { mx = max(mx, x.second); mn = min(mn, x.second) }
            if (mx - mn >= 1.5 && b >= mx - 0.2 && now - gajangT >= 6000) {
                gajangT = now; malT = now; saeT = now
                mal("이쪽이 가장 밝습니다.")
                return
            }
        }
        // 밝기 말은 두 번 잇달아 같고 바뀌었을 때만, 3초에 한 번까지
        val ap = apDangye
        apDangye = d
        if (d != ap || d == malDangye || now - malT < 3000) return
        malT = now; saeT = now
        malDangye = d
        mal("$d.")
    }

    /** 밝을수록 높은 짧은 소리(220~1760헤르츠) */
    private fun ttil(b: Double) {
        val k = min(max((b + 4) / 14, 0.0), 1.0)
        val hz = 220 * 2.0.pow(k * 3)
        KameraNun.ttil(hz, 0.06, 0.6f)
    }

    private fun mal(t: String, g: MalGeup = MalGeup.ANNAE, sseuGi: Boolean = false) = KameraNun.mal(t, g, sseuGi)
}

/** 빛 알아보기 화면 — 둘러보기 탭 */
class BitAlgiHwamyeon : KameraHwamyeon("빛 알아보기") {
    override val bupum: KameraNunBupum get() = BitAlgi
    override fun kyeogi(t: GilnunActivity) = BitAlgi.kyeogi(t)

    override fun juljul(t: GilnunActivity): View {
        val g = BitAlgi
        val kb = t.danchu(kGeul()) { if (g.kyeojim) g.kkeugi(true) else g.kyeogi(t) }
        g.byeonhwa = {
            if (t.wiHwamyeon === this) {
                val n = kGeul()
                if (kb.text.toString() != n) kb.text = n
            }
        }
        t.danchu("지금 밝기 듣기") { g.jigeumMal() }
        lateinit var nb: Button
        nb = t.danchu(nGeul()) {
            g.nopnaji = !g.nopnaji
            nb.text = nGeul()
            nb.announceForAccessibility(if (g.nopnaji) "높낮이 소리를 켰습니다." else "높낮이 소리를 껐습니다.")
        }
        malsoriDanchu(t)
        alaDuSil(t, "화면을 여시면 바로 시작합니다. 폰 뒤쪽 카메라가 보는 쪽이 밝을수록 높은 소리가 납니다. 폰을 천천히 돌리시면 가장 밝은 쪽에서 이쪽이 가장 밝습니다라고 알려 드려, 창이나 켜진 불이 어느 쪽인지 찾으실 수 있습니다. 불이 켜지거나 꺼지면 불이 켜진 듯합니다, 불이 꺼진 듯합니다라고 알려 드립니다. 밝기가 바뀌면 캄캄합니다, 어둡습니다, 실내 불빛 밝기입니다, 밝습니다, 햇빛처럼 아주 밝습니다 가운데 하나로 3초에 한 번까지 알려 드립니다. 지금 밝기 듣기를 누르시면 지금 밝기를 다시 들려 드립니다. 높낮이 소리를 끄시면 말로만, 카메라 눈 말소리를 끄시면 높낮이 소리로만 알려 드립니다. 나가기 전에 불을 껐는지, 방에 불이 켜져 있는지 살피실 때 쓰실 수 있습니다. 10분 동안 밝기가 바뀌지 않거나 이 화면을 떠나시면 카메라를 끕니다. 말로 하기에서 불 켜져 있어, 빛 알려 줘, 밝은 쪽 찾아 줘라고 하셔도 열립니다. 폰 안에서만 살피며 사진은 담지도 보내지도 않습니다.")
        return kb
    }

    private fun kGeul() = if (BitAlgi.kyeojim) "멈추기 — 빛을 살피는 중" else "이어 살피기 — 카메라를 켜고 빛 알아보기"
    private fun nGeul() = "높낮이 소리 — 지금 " + (if (BitAlgi.nopnaji) "켜짐, 누르면 꺼짐" else "꺼짐, 누르면 켜짐")
}
