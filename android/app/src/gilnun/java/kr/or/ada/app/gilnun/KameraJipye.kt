// 안드로이드 길눈 — 지폐와 색깔 알아보기(묶음 b4_dulreo, 아이폰 JipyeSaek.swift 2.21.0 을 같은 셈, 같은 말로 옮김)
// 카메라에 댄 것의 가운데 색을 사람들이 늘 쓰는 색 이름으로 알리고(진한 남색, 연한 하늘색, 밝은 빨강),
// 무늬가 있으면 바탕색과 무늬 색을 나누어 알립니다(흰색 바탕에 검정 무늬).
// 우리나라 지폐(천 원, 오천 원, 만 원, 오만 원)는 지폐에 적힌 숫자와 글자를 폰 안에서 읽어 알아봅니다(b3 GeulIlgi 의 한글 글자 알아보기).
// 모두 폰 안에서만 하며, 사진은 담지도 보내지도 않습니다.
package kr.or.ada.app.gilnun

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.camera.view.PreviewView
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object JipyeSaek : KameraNunBupum {
    override val ireum = "지폐와 색깔 알아보기"
    override var kyeojim = false
        private set
    var sangtae = ""
        private set
    /** 방금 알린 것(다시 듣기용) */
    var majimak = ""
        private set
    var byeonhwa: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    // 아래는 분석 줄에서만
    @Volatile private var beon = 0
    @Volatile private var apDon = ""                 // 앞 장에서 읽은 지폐(두 장 잇달아 같아야 알림)
    // 아래는 화면 줄에서만
    private var apSaek = ""                          // 앞 장의 색(두 장 잇달아 같아야 알림)
    private var malSaek = ""                         // 마지막으로 말한 색
    private var saekT = 0L
    private val donT = HashMap<String, Long>()       // 같은 지폐 10초 안 되풀이 않음
    private var donMalT = 0L                         // 지폐를 말한 뒤 3초는 색을 말하지 않음
    private var eodumT = 0L
    private var saeT = 0L
    private val sigye = object : Runnable {
        override fun run() {
            if (!kyeojim) return
            salpigi()
            if (kyeojim) main.postDelayed(this, 10000)
        }
    }

    init { Kamera.deungrok(this) }

    // MARK: 켜기·끄기

    fun kyeogi(a: GilnunActivity? = null, bogi: PreviewView? = null) {
        val t = a ?: Kamera.hwalseongEotgi() ?: return
        if (kyeojim) { if (bogi != null) Kamera.bogiDalgi(bogi); return }
        Kamera.heorak(t, this, "지폐와 색깔 알아보기를") { sijak(t, bogi) }
    }

    private fun sijak(t: GilnunActivity, bogi: PreviewView?) {
        apDon = ""
        beon = 0
        Kamera.sijak(t, this, bogi, 500L, { jang -> bunseok(jang) }) { ok ->
            if (ok) {
                kyeojim = true
                apSaek = ""
                malSaek = ""
                saeT = System.currentTimeMillis()
                sangtae = "살피는 중입니다."
                mal("지폐와 색깔 알아보기를 시작합니다. 폰 뒤쪽 카메라를 옷이나 물건, 지폐에 한 뼘쯤 떨어뜨려 대 주십시오.", sseuGi = true)
                Girok.namgi("jipyesaek", mapOf("kyeogi" to true))
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
        if (malHagi) Sori.mal("지폐와 색깔 알아보기를 멈췄습니다.", MalGeup.JEONGBO)
        byeonhwa?.invoke()
    }

    fun dasiDeutgi() {
        if (majimak.isNotEmpty()) Sori.mal(majimak)
        else Sori.mal(if (kyeojim) "살피는 중입니다." else "지폐와 색깔 알아보기가 꺼져 있습니다.")
    }

    /** 5분 동안 새로 알린 것이 없으면 카메라를 끔(배터리) */
    private fun salpigi() {
        if (!kyeojim || System.currentTimeMillis() - saeT <= 300000) return
        kkeugi(false)
        mal("5분 동안 새로 알려 드릴 것이 없어 카메라를 껐습니다.", sseuGi = true)
    }

    // MARK: 한 장마다 — 0.5초에 한 번 색, 1초에 한 번 지폐

    private fun bunseok(jang: KameraJang) {
        beon += 1
        val saek = saekSem(jang)
        if (beon % 2 != 0) {
            jang.kkeut()
            main.post { allida(saek, null) }
            return
        }
        // 지폐 — 글자 알아보기(결과는 화면 줄). 다 읽으면 장을 놓음
        GeulIlgi.ilgi(jang) { juldeul, _ ->
            val d = jipyeGalla(juldeul)
            // 두 번 잇달아 같은 지폐로 읽혀야 알림(가격표 숫자 헛읽기 줄이기)
            val don = if (d != null && d == apDon) d else null
            apDon = d ?: ""
            allida(saek, don)
        }
    }

    // MARK: 알리기(화면 줄)

    private fun allida(saek: Pair<String, Boolean>?, don: String?) {
        if (!kyeojim) return
        val now = System.currentTimeMillis()
        if (don != null) {
            val t = donT[don]
            if (t == null || now - t >= 10000) {
                donT[don] = now
                donMalT = now
                saeT = now
                Eum.naegi(EumJong.HWAKSIN)
                val m = "$don 지폐입니다."
                majimak = m
                sangtae = m
                Sori.mal(m, MalGeup.ANNAE)   // 기다리는 대답이므로 말소리를 꺼도 알림
                Girok.namgi("jipyesaek", mapOf("jipye" to don))
                byeonhwa?.invoke()
                return
            }
        }
        val sk = saek ?: return
        val ireum = sk.first
        if (sk.second) {
            apSaek = ""
            if (now - eodumT > 10000) {
                eodumT = now
                sangtae = "너무 어둡습니다."
                mal("너무 어둡습니다. 조금 더 밝은 곳에서 대 주십시오.", MalGeup.JEONGBO, sseuGi = true)
            }
            return
        }
        // 두 장 잇달아 같고, 바뀌었을 때만, 3초에 한 번까지. 지폐를 말한 뒤 3초는 쉼
        val ap = apSaek
        apSaek = ireum
        if (ireum != ap || ireum == malSaek || now - saekT < 3000 || now - donMalT < 3000) return
        saekT = now
        malSaek = ireum
        saeT = now
        val m = "$ireum."
        majimak = m
        sangtae = m
        Sori.mal(m, MalGeup.ANNAE)
        byeonhwa?.invoke()
    }

    private fun mal(t: String, g: MalGeup = MalGeup.ANNAE, sseuGi: Boolean = false) = KameraNun.mal(t, g, sseuGi)

    // MARK: 지폐 가르기 — 지폐에 적힌 숫자·글자로(오만 원을 만 원보다, 오천 원을 천 원보다 먼저 봄)

    fun jipyeGalla(juldeul: List<String>): String? {
        val t = juldeul.joinToString(" ").replace(" ", "").replace(",", "").replace(".", "")
        if (t.contains("50000") || t.contains("오만원")) return "오만 원"
        if (t.contains("10000") || t.contains("만원")) return "만 원"
        if (t.contains("5000") || t.contains("오천원")) return "오천 원"
        if (t.contains("1000") || t.contains("천원")) return "천 원"
        return null
    }

    // MARK: 색 셈 — 가운데 점들을 모아 바탕색과 무늬 색을 가름(아이폰과 같은 셈)

    private class Jeom(val r: Double, val g: Double, val b: Double) {
        val hap: Double get() = r + g + b
    }

    private fun geori(a: Jeom, b: Jeom): Double {
        val d0 = a.r - b.r
        val d1 = a.g - b.g
        val d2 = a.b - b.b
        return sqrt(d0 * d0 + d1 * d1 + d2 * d2)
    }

    /** (색 이름, 너무 어두움) — 가운데 가로·세로 40% 안은 돌려도 같은 자리라 누운 그림 그대로 셈 */
    fun saekSem(jang: KameraJang): Pair<String, Boolean>? {
        val bm = jang.bitmap
        val w = bm.width
        val h = bm.height
        if (w < 24 || h < 24) return null
        val n = 24
        val jeom = ArrayList<Jeom>(n * n)
        for (iy in 0 until n) for (ix in 0 until n) {
            val x = (w * (0.3 + 0.4 * (ix + 0.5) / n)).toInt().coerceIn(0, w - 1)
            val y = (h * (0.3 + 0.4 * (iy + 0.5) / n)).toInt().coerceIn(0, h - 1)
            val c = bm.getPixel(x, y)
            jeom.add(Jeom(Color.red(c) / 255.0, Color.green(c) / 255.0, Color.blue(c) / 255.0))
        }
        val balgi = jeom.sumOf { max(it.r, max(it.g, it.b)) } / jeom.size
        if (balgi < 0.12) return "" to true
        // 두 무리로 나누기(k-평균 두 개, 여섯 번)
        val sorted = jeom.sortedBy { it.hap }
        var c1 = sorted[sorted.size / 4]
        var c2 = sorted[sorted.size * 3 / 4]
        val gat = IntArray(jeom.size)
        repeat(6) {
            var s1r = 0.0; var s1g = 0.0; var s1b = 0.0; var n1 = 0
            var s2r = 0.0; var s2g = 0.0; var s2b = 0.0; var n2 = 0
            for ((i, q) in jeom.withIndex()) {
                if (geori(q, c1) <= geori(q, c2)) { gat[i] = 0; s1r += q.r; s1g += q.g; s1b += q.b; n1 += 1 }
                else { gat[i] = 1; s2r += q.r; s2g += q.g; s2b += q.b; n2 += 1 }
            }
            if (n1 > 0) c1 = Jeom(s1r / n1, s1g / n1, s1b / n1)
            if (n2 > 0) c2 = Jeom(s2r / n2, s2g / n2, s2b / n2)
        }
        val n2 = gat.count { it == 1 }
        val n1 = jeom.size - n2
        val badak: Jeom
        val mu: Jeom
        val muBi: Double
        if (n1 >= n2) { badak = c1; mu = c2; muBi = n2.toDouble() / jeom.size }
        else { badak = c2; mu = c1; muBi = n1.toDouble() / jeom.size }
        val badakIreum = saekIreum(badak.r, badak.g, badak.b)
        if (muBi >= 0.15 && geori(badak, mu) > 0.25) {
            val muIreum = saekIreum(mu.r, mu.g, mu.b)
            if (muIreum != badakIreum) return "$badakIreum 바탕에 $muIreum 무늬" to false
        }
        return badakIreum to false
    }

    /** 사람들이 늘 쓰는 색 이름 */
    fun saekIreum(r: Double, g: Double, b: Double): String {
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val v = mx
        val s = if (mx > 0) (mx - mn) / mx else 0.0
        var hu = 0.0
        if (mx != mn) {
            hu = when (mx) {
                r -> 60 * ((g - b) / (mx - mn))
                g -> 60 * ((b - r) / (mx - mn) + 2)
                else -> 60 * ((r - g) / (mx - mn) + 4)
            }
            if (hu < 0) hu += 360
        }
        // 무채색
        if (v < 0.18) return "검정"
        if (s < 0.13) {
            if (v > 0.85) return "흰색"
            if (v > 0.62) return "연한 회색"
            if (v > 0.38) return "회색"
            return "진한 회색"
        }
        val ireum: String = when {
            hu < 12 || hu >= 345 -> if (s < 0.5 && v > 0.7) "분홍" else "빨강"
            hu < 40 -> if (v < 0.55) "갈색" else (if (s < 0.45 && v > 0.7) "살구색" else "주황")
            hu < 66 -> if (v < 0.55) "황토색" else (if (s < 0.35) "베이지" else "노랑")
            hu < 95 -> if (v < 0.5) "올리브색" else "연두"
            hu < 165 -> "초록"
            hu < 195 -> "청록"
            hu < 232 -> if (s < 0.5 && v > 0.65) "하늘색" else "파랑"
            hu < 258 -> if (v < 0.55) "남색" else "파랑"
            hu < 292 -> "보라"
            else -> if (s < 0.5 && v > 0.7) "분홍" else "자주"
        }
        // 짙고 옅음
        val bunhong = ireum in listOf("분홍", "하늘색", "살구색", "베이지")
        if (v < 0.4 && ireum !in listOf("갈색", "올리브색", "황토색")) return "진한 $ireum"
        if (ireum == "갈색" && v < 0.3) return "진한 갈색"
        if (!bunhong && s < 0.4 && v > 0.75) return "연한 $ireum"
        if (s > 0.75 && v > 0.85) return "밝은 $ireum"
        return ireum
    }
}

/** 지폐와 색깔 알아보기 화면 — 둘러보기 탭 */
class JipyeSaekHwamyeon : KameraHwamyeon("지폐와 색깔 알아보기") {
    override val bupum: KameraNunBupum get() = JipyeSaek
    override fun kyeogi(t: GilnunActivity) = JipyeSaek.kyeogi(t, bogi)

    override fun juljul(t: GilnunActivity): View {
        val g = JipyeSaek
        val kb = t.danchu(kGeul()) { if (g.kyeojim) g.kkeugi(true) else g.kyeogi(t, bogi) }
        g.byeonhwa = {
            if (t.wiHwamyeon === this) {
                val n = kGeul()
                if (kb.text.toString() != n) kb.text = n
            }
        }
        val dd = t.danchu("방금 것 다시 듣기") { g.dasiDeutgi() }
        bogiNeoki(t, dd)
        malsoriDanchu(t)
        alaDuSil(t, "화면을 여시면 바로 시작합니다. 폰 뒤쪽 카메라를 옷이나 물건에 한 뼘쯤 떨어뜨려 대시면, 가운데 색을 진한 남색, 연한 하늘색, 밝은 빨강처럼 늘 쓰는 색 이름으로 알려 드립니다. 무늬가 있으면 흰색 바탕에 검정 무늬처럼 바탕색과 무늬 색을 나누어 알려 드립니다. 색은 바뀔 때만, 3초에 한 번까지 말씀드립니다. 지폐를 대시면 숫자가 보이는 쪽을 카메라로 향해 주십시오. 천 원, 오천 원, 만 원, 오만 원을 확신음 한 번과 함께 알려 드리고, 같은 지폐는 10초 안에 되풀이하지 않습니다. 너무 어두우면 조금 더 밝은 곳에서 대 주십시오라고 먼저 알려 드립니다. 색은 불빛에 따라 달라 보여 노란 전등 아래에서는 조금 다르게 들릴 수 있습니다. 손전등은 색을 바꾸어 보이게 하므로 지폐를 읽을 때만 켜십시오. 옷 맞춰 입기, 양말 짝 맞추기, 과일 익은 정도 보기에 쓰실 수 있습니다. 방금 것 다시 듣기를 누르시면 마지막으로 알린 것을 다시 들려 드립니다. 5분 동안 새로 알릴 것이 없거나 이 화면을 떠나시면 카메라를 끕니다. 말로 하기에서 무슨 색이야, 지폐 알려 줘, 얼마짜리야라고 하셔도 열립니다. 폰 안에서만 살피며 사진은 담지도 보내지도 않습니다.") {
            bulbitDanchu(t)
        }
        return kb
    }

    private fun kGeul() = if (JipyeSaek.kyeojim) "멈추기 — 지폐와 색깔을 살피는 중" else "이어 살피기 — 카메라를 켜고 지폐와 색깔 알아보기"
}
