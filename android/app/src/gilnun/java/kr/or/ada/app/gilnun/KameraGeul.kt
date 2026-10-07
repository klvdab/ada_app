// 안드로이드 길눈 — 즉석 글자 읽기(묶음 b3_kamera, 아이폰 GeulIlgi.swift 2.16.0 을 같은 잣대·같은 말로 옮김)
// 사진을 찍지 않아도 카메라를 대고 있으면 보이는 글자를 위에서 아래 차례로 곧바로 읽어 드립니다.
// 앱 안에 실린 ML Kit 한글 글자 알아보기(영어도 읽음)만 쓰며, 사진과 글은 어디로도 보내지 않습니다(인터넷 없이 폰 안에서만).
// 두 번 잇달아 보인 줄만 읽어 헛글을 줄이고, 한 번 읽은 줄은 30초 안에 되풀이하지 않습니다.
// 글자가 화면 끝에 걸려 잘리면 어느 쪽으로 옮기실지 알려 드립니다(3초에 한 번까지).
//
// ── 차 안 간판 알림(묶음 b1 GanpanAllim)이 쓰는 법 — 아이폰 GanpanAllim 과 같은 세 가지 ──
//   GeulIlgi.kyeojim                    지금 켜져 있나
//   GeulIlgi.kyeogiChaAn()              시작 말 없이 창밖 간판 읽기를 켬(화면 없이, 길눈 화면이 떠 있어야 카메라가 돎)
//   GeulIlgi.kkeugi(false)              말없이 끔
//  그 밖에 한 장만 읽을 때:
//   GeulIlgi.ilgi(jang) { juldeul, jallim -> }            카메라 눈 한 장(KameraJang)에서 — 다 읽으면 jang.kkeut() 까지 해 줌
//   GeulIlgi.ilgi(bitmap, dolim) { juldeul, jallim -> }   그림 한 장에서(dolim: 바로 세우려면 돌릴 도)
//   GeulIlgi.ilgiTeul(img, garo, sero) { jul -> }         줄마다 자리(상자)까지(문 찾기가 씀)
//   결과는 화면 줄에서. juldeul 은 위에서 아래, 왼쪽에서 오른쪽 차례. jallim 은 화면 끝에 걸린 쪽(왼쪽·오른쪽·위쪽·아래쪽) 또는 null
package kr.or.ada.app.gilnun

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import java.util.Locale

/** 읽은 글자 한 줄과 그 자리(세운 폰 기준 점) */
class KameraGeulJul(val mal: String, val teul: Rect)

object GeulIlgi : KameraNunBupum {
    override val ireum = "즉석 글자 읽기"
    override var kyeojim = false
        private set
    /** 읽은 줄(새것이 위, 100줄까지) */
    val ilgeun = ArrayList<String>()
    var majimak = ""
        private set
    /** 켜짐이 바뀌거나 새 줄을 읽으면(화면이 채움) */
    var byeonhwa: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var allabogi: TextRecognizer? = null
    // 아래는 화면 줄에서만
    private var apJul = HashSet<String>()           // 바로 앞 장에서 본 줄
    private val ilgeunT = HashMap<String, Long>()   // 줄 → 읽은 때
    private var gyeonuT = 0L
    private var saeT = 0L
    private var chaAnJung = false
    private val sigye = object : Runnable {
        override fun run() {
            if (!kyeojim) return
            salpigi()
            if (kyeojim) main.postDelayed(this, 10000)
        }
    }

    init { Kamera.deungrok(this) }

    private fun client(): TextRecognizer =
        allabogi ?: TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()).also { allabogi = it }

    // MARK: 켜기·끄기

    /** 차 안 간판 알림이 켤 때 — 시작 말 없이 창밖 간판을 읽음(아이폰 kyeogiChaAn) */
    fun kyeogiChaAn(a: GilnunActivity? = null) {
        if (kyeojim) return
        kyeogiSok(a, null, true)
    }

    fun kyeogi(a: GilnunActivity? = null, bogi: PreviewView? = null) = kyeogiSok(a, bogi, false)

    private fun kyeogiSok(a: GilnunActivity?, bogi: PreviewView?, chaAn: Boolean) {
        val t = a ?: Kamera.hwalseongEotgi() ?: return
        if (kyeojim) { if (bogi != null) Kamera.bogiDalgi(bogi); return }
        Kamera.heorak(t, this, "글자 읽기를") { sijak(t, bogi, chaAn) }
    }

    private fun sijak(t: GilnunActivity, bogi: PreviewView?, chaAn: Boolean) {
        chaAnJung = chaAn
        val c = client()
        apJul = HashSet()
        Kamera.sijak(t, this, bogi, 1000L, { jang -> bunseok(c, jang) }) { ok ->
            if (ok) {
                kyeojim = true
                saeT = System.currentTimeMillis()
                if (!chaAnJung) mal("글자 읽기를 시작합니다. 폰을 글자 쪽으로 세워 들고 천천히 움직여 주십시오.", sseuGi = true)
                Girok.namgi("geulilgi", mapOf("kyeogi" to true, "chaAn" to chaAnJung))
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
        if (malHagi) Sori.mal("글자 읽기를 멈췄습니다.", MalGeup.JEONGBO)
        byeonhwa?.invoke()
    }

    fun dasiDeutgi() {
        Sori.mal(if (majimak.isEmpty()) "아직 읽은 글자가 없습니다." else majimak)
    }

    /** 3분 동안 새 글이 없으면 카메라를 끔 */
    private fun salpigi() {
        if (!kyeojim || System.currentTimeMillis() - saeT <= 180000) return
        kkeugi(false)
        mal("3분 동안 새 글자가 없어 카메라를 껐습니다. 다시 읽으시려면 이어 읽기를 누르십시오.", sseuGi = true)
    }

    // MARK: 한 장마다 — 1초에 한 번

    private fun bunseok(c: TextRecognizer, jang: KameraJang) {
        ilgiSok(c, jang.inputImage(), jang.garo, jang.sero, { jang.kkeut() }) { juldeul, jallim ->
            // 두 장 잇달아 보인 줄만(헛글 줄이기)
            val keys = juldeul.map { ttuk(it) }
            val hwakjeong = juldeul.filterIndexed { i, _ -> apJul.contains(keys[i]) }
            apJul = keys.toHashSet()
            boda(hwakjeong, jallim)
        }
    }

    // MARK: 알리기(화면 줄)

    private fun boda(juldeul: List<String>, jallim: String?) {
        if (!kyeojim) return
        val now = System.currentTimeMillis()
        val sae = ArrayList<String>()
        for (j in juldeul) {
            val k = ttuk(j)
            val t = ilgeunT[k]
            if (t != null && now - t < 30000) { ilgeunT[k] = now; continue }
            ilgeunT[k] = now
            sae.add(j)
            if (sae.size >= 8) break
        }
        if (ilgeunT.size > 400) ilgeunT.entries.removeAll { now - it.value >= 60000 }
        if (sae.isNotEmpty()) {
            saeT = now
            val m = sae.joinToString(". ")
            majimak = m
            for (s in sae) ilgeun.add(0, s)
            while (ilgeun.size > 100) ilgeun.removeAt(ilgeun.size - 1)
            mal(m)
            byeonhwa?.invoke()
            return
        }
        if (jallim != null && now - gyeonuT >= 3000) {
            gyeonuT = now
            mal("글자가 ${jallim}으로 잘렸습니다. 폰을 조금 ${jallim}으로 옮기십시오.", MalGeup.JEONGBO)
        }
    }

    private fun mal(t: String, g: MalGeup = MalGeup.ANNAE, sseuGi: Boolean = false) = KameraNun.mal(t, g, sseuGi)

    // MARK: 글자 알아보기(폰 안에서만) — 차 안 간판 알림·문 찾기도 씀

    /** 카메라 눈 한 장에서 — 다 읽으면 jang.kkeut() 까지. 결과는 화면 줄 */
    fun ilgi(jang: KameraJang, kkeut: (List<String>, String?) -> Unit) {
        ilgiSok(client(), jang.inputImage(), jang.garo, jang.sero, { jang.kkeut() }, kkeut)
    }

    /** 그림 한 장에서(dolim: 바로 세우려면 시계 방향으로 돌릴 도). 결과는 화면 줄 */
    fun ilgi(bitmap: Bitmap, dolim: Int, kkeut: (List<String>, String?) -> Unit) {
        val garo = if (dolim % 180 == 0) bitmap.width else bitmap.height
        val sero = if (dolim % 180 == 0) bitmap.height else bitmap.width
        ilgiSok(client(), InputImage.fromBitmap(bitmap, dolim), garo, sero, null, kkeut)
    }

    /** 줄마다 자리까지 — 위에서 아래, 왼쪽에서 오른쪽 차례. 결과는 화면 줄(못 읽으면 빈 목록) */
    fun ilgiTeul(img: InputImage, garo: Int, sero: Int, kkeut: (List<KameraGeulJul>) -> Unit) {
        client().process(img)
            .addOnSuccessListener { txt ->
                val hang = ArrayList<KameraGeulJul>()
                for (b in txt.textBlocks) for (l in b.lines) {
                    val s = l.text.trim()
                    val r = l.boundingBox ?: continue
                    if (s.isEmpty()) continue
                    hang.add(KameraGeulJul(s, r))
                }
                kkeut(charye(hang, sero))
            }
            .addOnFailureListener { kkeut(emptyList()) }
    }

    private fun ilgiSok(c: TextRecognizer, img: InputImage, garo: Int, sero: Int, dahaem: (() -> Unit)?,
                        kkeut: (List<String>, String?) -> Unit) {
        c.process(img)
            .addOnSuccessListener { txt ->
                val hang = ArrayList<KameraGeulJul>()
                for (b in txt.textBlocks) for (l in b.lines) {
                    val s = l.text.trim()
                    val r = l.boundingBox ?: continue
                    if (s.length < 2) continue
                    hang.add(KameraGeulJul(s, r))
                }
                val jul = charye(hang, sero)
                var jallim: String? = null
                val w = garo.toDouble()
                val h = sero.toDouble()
                if (w > 0 && h > 0) {
                    for (x in jul) {
                        val b = x.teul
                        if (b.left < w * 0.015) { jallim = "9시 쪽"; break }
                        if (b.right > w * 0.985) { jallim = "3시 쪽"; break }
                        if (b.top < h * 0.015) { jallim = "12시 쪽"; break }
                        if (b.bottom > h * 0.985) { jallim = "6시 쪽"; break }
                    }
                }
                kkeut(jul.map { it.mal }, jallim)
            }
            .addOnFailureListener { kkeut(emptyList(), null) }
            .addOnCompleteListener { dahaem?.invoke() }
    }

    /** 위부터(같은 높이 — 세로 3% 안이면 한 줄로 보고) 왼쪽부터 */
    private fun charye(hang: List<KameraGeulJul>, sero: Int): List<KameraGeulJul> {
        val tol = sero * 0.03
        val wi = hang.sortedBy { it.teul.top }
        val out = ArrayList<KameraGeulJul>()
        var mukeum = ArrayList<KameraGeulJul>()
        var gijun = 0
        for (x in wi) {
            if (mukeum.isEmpty() || x.teul.top - gijun <= tol) {
                if (mukeum.isEmpty()) gijun = x.teul.top
                mukeum.add(x)
            } else {
                out.addAll(mukeum.sortedBy { it.teul.left })
                mukeum = ArrayList()
                mukeum.add(x)
                gijun = x.teul.top
            }
        }
        out.addAll(mukeum.sortedBy { it.teul.left })
        return out
    }

    fun ttuk(s: String): String = s.replace(" ", "").uppercase(Locale.ROOT)
}

/** 즉석 글자 읽기 화면 — 둘러보기 탭 */
class GeulIlgiHwamyeon : KameraHwamyeon("즉석 글자 읽기") {
    override val bupum: KameraNunBupum get() = GeulIlgi
    override fun kyeogi(t: GilnunActivity) = GeulIlgi.kyeogi(t, bogi)

    private var mokPyeol = false
    private var mok: List<String> = emptyList()   // 펼칠 때의 읽은 글(펼친 동안 줄이 흔들리지 않게)
    private var jjok = 0

    override fun juljul(t: GilnunActivity): View {
        val g = GeulIlgi
        val kb = t.danchu(kGeul()) { if (g.kyeojim) g.kkeugi(true) else g.kyeogi(t, bogi) }
        val dd = t.danchu("방금 읽은 것 다시 듣기") { g.dasiDeutgi() }
        bogiNeoki(t, dd)
        lateinit var mb: Button
        mb = t.danchu(mokGeul()) {
            mokPyeol = !mokPyeol
            if (mokPyeol) { mok = ArrayList(g.ilgeun); jjok = 0 }
            t.dasiGeurigi()
        }
        if (mokPyeol) {
            var cheotJul: View? = null
            val kkeut = minOf(mok.size, (jjok + 1) * 5)
            for (i in jjok * 5 until kkeut) {
                val v = t.geul(mok[i], true)
                if (cheotJul == null) cheotJul = v
            }
            if (mok.isEmpty()) cheotJul = t.geul("아직 읽은 글이 없습니다.")
            if (kkeut < mok.size) t.danchu("더 보기") { jjok += 1; t.dasiGeurigi(); jjokChojeom(t) }
            if (jjok > 0) t.danchu("이전 보기") { jjok -= 1; t.dasiGeurigi(); jjokChojeom(t) }
            jjokJul = cheotJul
        }
        g.byeonhwa = {
            if (t.wiHwamyeon === this) {
                val n = kGeul()
                if (kb.text.toString() != n) kb.text = n
                val m = mokGeul()
                if (mb.text.toString() != m) mb.text = m
            }
        }
        malsoriDanchu(t)
        alaDuSil(t, "사진을 찍지 않아도 카메라를 대고 있으면 보이는 글자를 위에서 아래 차례로 곧바로 읽어 드립니다. 화면을 여시면 바로 읽기 시작합니다. 폰을 글자 쪽으로 세워 들고 천천히 움직이십시오. 한 번 읽은 줄은 30초 안에 되풀이하지 않고 새로 보인 글만 읽습니다. 글자가 화면 끝에 걸려 잘리면 폰을 어느 쪽으로 옮기실지 알려 드립니다. 읽은 글은 읽은 글 펼치기에 다섯 줄씩 남습니다. 어두우면 이 안의 손전등을 켜십시오. 카메라 눈 말소리를 끄시면 말 없이 화면 글자로만 남습니다. 3분 동안 새 글이 없거나 이 화면을 떠나시면 카메라를 끕니다. 말로 하기에서 글자 읽어 줘라고 하셔도 열립니다. 사진과 글은 어디로도 보내지 않고 인터넷 없이 폰 안에서만 읽습니다.") {
            bulbitDanchu(t)
        }
        return kb
    }

    private var jjokJul: View? = null
    /** 더 보기·이전 보기 뒤 커서를 그 쪽 첫 줄로 */
    private fun jjokChojeom(t: GilnunActivity) { jjokJul?.let { t.chojeomOmgigi(it) } }

    private fun kGeul() = if (GeulIlgi.kyeojim) "멈추기 — 글자를 읽는 중" else "이어 읽기 — 카메라를 켜고 글자 읽기"
    private fun mokGeul() = if (mokPyeol) "읽은 글 접기" else "읽은 글 펼치기 — ${GeulIlgi.ilgeun.size}줄"
}
