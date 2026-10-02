// 안드로이드 길눈 — 가리키고 말하기(묶음 b4_dulreo, 아이폰 GarikiIlgi.swift 2.17.0 을 같은 셈, 같은 말로 옮김)
// 전자레인지·세탁기·엘리베이터·키오스크처럼 단추가 많은 곳에서, 검지로 가리킨 곳의 글자만 골라 읽어 드립니다.
// 아이폰은 애플 손 모양 알아보기(Vision)를 씀. 안드로이드는 구글 미디어파이프 손 마디 찾기(HandLandmarker, 손끝 8번·그 아래 마디 7번)와
// ML Kit 한글 글자 알아보기(낱말마다 자리)를 씁니다. ML Kit 자세 알아보기의 검지 점은 몸 전체가 보여야 잡혀 단추판 앞 손만으로는
// 쓸 수 없어 미디어파이프를 골랐습니다. 손 마디 모델(hand_landmarker.task, 약 8메가바이트)은 앱 안(assets)에 실려 있으면 그것을,
// 없으면 처음 한 번 구글 공식 주소에서 받아 폰 안(filesDir)에 둡니다. 그 뒤로는 인터넷 없이 폰 안에서만 읽습니다.
// 특허 확인(아이폰 2026-09-30과 같음): 손에 들고 쓰고 서버로 보내지 않으며 폰 안에서 읽어 말로만 알림.
package kr.or.ada.app.gilnun

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import androidx.camera.view.PreviewView
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.sqrt

/** 손 마디 모델 — 앱 안에 있으면 그것을, 없으면 처음 한 번 받아 둠 */
object GarikiModel {
    const val IREUM = "hand_landmarker.task"
    const val JUSO = "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task"
    private val main = Handler(Looper.getMainLooper())
    @Volatile var son: HandLandmarker? = null
        private set
    @Volatile private var junbiJung = false
    private val gidari = ArrayList<(Boolean) -> Unit>()

    private fun pail(c: Context) = File(c.filesDir, IREUM)

    /** 앱 안에 실렸거나 이미 받아 두었나(받지 않아도 되나) */
    fun itda(c: Context): Boolean = asetItda(c) || pail(c).let { it.exists() && it.length() > 1_000_000 }

    private fun asetItda(c: Context): Boolean = try { c.assets.open(IREUM).use { true } } catch (e: Exception) { false }

    /** 손 마디 찾기를 세움(없으면 받기부터). 결과는 화면 줄 */
    fun junbi(c: Context, kkeut: (Boolean) -> Unit) {
        if (son != null) { kkeut(true); return }
        gidari.add(kkeut)
        if (junbiJung) return
        junbiJung = true
        val ac = c.applicationContext
        DrNet.dwiSil {
            var ok = false
            try {
                val aset = asetItda(ac)
                val f = pail(ac)
                if (!aset && !(f.exists() && f.length() > 1_000_000)) badgi(f)
                val bo = if (aset) {
                    BaseOptions.builder().setModelAssetPath(IREUM).build()
                } else {
                    val ch = FileInputStream(f).channel
                    val buf = ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size())
                    BaseOptions.builder().setModelAssetBuffer(buf).build()
                }
                val opt = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(bo)
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumHands(2)
                    .setMinHandDetectionConfidence(0.5f)
                    .setMinHandPresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .build()
                son = HandLandmarker.createFromOptions(ac, opt)
                ok = son != null
            } catch (e: Throwable) {
                Girok.namgi("gariki_model_oryu", mapOf("e" to (e.message ?: e.javaClass.simpleName).take(80)))
                ok = false
            }
            main.post {
                junbiJung = false
                val l = ArrayList(gidari)
                gidari.clear()
                for (f in l) f(ok)
            }
        }
    }

    /** 구글 공식 주소에서 받아 임시 파일로 쓴 뒤 이름을 바꿈(받다 끊기면 반쪽 파일을 남기지 않음) */
    private fun badgi(f: File) {
        val im = File(f.parentFile, "$IREUM.part")
        val c = URL(JUSO).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        try {
            if (c.responseCode != 200) throw IllegalStateException("http ${c.responseCode}")
            c.inputStream.use { i -> im.outputStream().use { o -> i.copyTo(o) } }
        } finally {
            c.disconnect()
        }
        if (im.length() < 1_000_000) { im.delete(); throw IllegalStateException("short") }
        if (f.exists()) f.delete()
        if (!im.renameTo(f)) { im.delete(); throw IllegalStateException("rename") }
        Girok.namgi("gariki_model_badeum", mapOf("kb" to f.length() / 1024))
    }
}

object GarikiIlgi : KameraNunBupum {
    override val ireum = "가리키고 말하기"
    override var kyeojim = false
        private set
    var majimak = ""
        private set
    /** 읽은 글(최근 것이 위, 100개까지) */
    val ilgeun = ArrayList<String>()
    var byeonhwa: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var geulja: TextRecognizer? = null
    private var kyeoneunJung = false
    // 아래는 분석 줄에서만
    @Volatile private var apGeul = ""            // 바로 앞 장에서 가리킨 글(두 번 잇달아 같아야 읽음)
    // 아래는 화면 줄에서만
    private var malHanGeul = ""                  // 마지막으로 읽은 글
    private var sonBoim = false
    private var sonEopT = 0L
    private var sonEopMalSu = 0
    private var sonEopMalT = 0L
    private var geulEopMal = false
    private var sonT = 0L
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
        if (kyeoneunJung) return
        Kamera.heorak(t, this, "가리키고 말하기를") {
            if (GarikiModel.son != null) { sijak(t, bogi); return@heorak }
            kyeoneunJung = true
            if (!GarikiModel.itda(t)) Sori.mal("손가락 찾기 자료를 처음 한 번 받습니다. 약 8메가바이트입니다. 잠시만 기다려 주십시오.", MalGeup.JEONGBO)
            GarikiModel.junbi(t) { ok ->
                kyeoneunJung = false
                if (!ok) {
                    Sori.mal("손가락 찾기 자료를 받지 못해 가리키고 말하기를 켜지 못했습니다. 인터넷에 이어진 곳에서 다시 열어 주십시오. 한 번 받으면 그 뒤로는 인터넷 없이 됩니다.")
                    return@junbi
                }
                // 받는 동안 화면을 떠나셨으면 켜지 않음
                if (t.isFinishing || t.wiHwamyeon !is GarikiHwamyeon) return@junbi
                val h = t.wiHwamyeon as GarikiHwamyeon
                sijak(t, h.bogiBatgi())
            }
        }
    }

    private fun sijak(t: GilnunActivity, bogi: PreviewView?) {
        val g = geulja ?: TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()).also { geulja = it }
        apGeul = ""
        Kamera.sijak(t, this, bogi, 500L, { jang -> bunseok(g, jang) }) { ok ->
            if (ok) {
                kyeojim = true
                malHanGeul = ""
                sonBoim = false
                sonEopT = System.currentTimeMillis()
                sonEopMalSu = 0
                geulEopMal = false
                saeT = System.currentTimeMillis()
                mal("가리키고 말하기를 시작합니다. 한 손으로 폰을 단추판 쪽으로 들고, 다른 손 검지로 단추를 가리키십시오.", sseuGi = true)
                Girok.namgi("gariki", mapOf("kyeogi" to true))
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
        if (malHagi) Sori.mal("가리키고 말하기를 멈췄습니다.", MalGeup.JEONGBO)
        byeonhwa?.invoke()
    }

    fun dasiDeutgi() {
        Sori.mal(if (majimak.isEmpty()) "아직 읽은 글자가 없습니다." else majimak)
    }

    /** 3분 동안 새로 읽은 글이 없으면 카메라를 끔 */
    private fun salpigi() {
        if (!kyeojim || System.currentTimeMillis() - saeT <= 180000) return
        kkeugi(false)
        mal("3분 동안 새로 읽은 글이 없어 카메라를 껐습니다. 다시 쓰시려면 이어 읽기를 누르십시오.", sseuGi = true)
    }

    // MARK: 한 장마다 — 0.5초에 한 번(분석 줄)

    private class Natmal(val w: String, val cx: Double, val cy: Double, val l: Double, val t: Double, val r: Double, val b: Double)

    private fun bunseok(g: TextRecognizer, jang: KameraJang) {
        val sl = GarikiModel.son
        if (sl == null) { jang.kkeut(); return }
        // 손끝과 그 아래 마디 — 바로 세운 그림에서(자리는 0~1, 왼쪽 위가 0)
        var kkeutX = -1.0
        var kkeutY = -1.0
        var bx = 0.0
        var by = -1.0      // 손끝이 가리키는 쪽(모르면 위쪽)
        try {
            val bm: Bitmap = jang.baroBitmap()
            val r = sl.detect(BitmapImageBuilder(bm).build())
            for (hand in r.landmarks()) {
                if (hand.size < 9) continue
                val tip = hand[8]
                val dip = hand[7]
                kkeutX = tip.x().toDouble()
                kkeutY = tip.y().toDouble()
                val dx = (tip.x() - dip.x()).toDouble()
                val dy = (tip.y() - dip.y()).toDouble()
                val l = max(0.0001, sqrt(dx * dx + dy * dy))
                bx = dx / l
                by = dy / l
                break   // 가장 먼저 찾은 손(미디어파이프는 더 뚜렷한 손을 앞에 둠)
            }
        } catch (e: Throwable) {
            Girok.namgi("gariki_oryu", mapOf("e" to (e.message ?: "").take(60)))
        }
        if (kkeutX < 0) {
            apGeul = ""
            jang.kkeut()
            main.post { allida(false, false, null) }
            return
        }
        val w = jang.garo.toDouble()
        val h = jang.sero.toDouble()
        val tx = kkeutX
        val ty = kkeutY
        val dx = bx
        val dy = by
        g.process(jang.inputImage())
            .addOnSuccessListener { txt ->
                // 가리키는 곳 — 손끝에서 가리키는 쪽으로 조금 앞
                val px = tx + dx * 0.03
                val py = ty + dy * 0.03
                var best: String? = null
                var bestD = 0.0
                if (w > 0 && h > 0) {
                    for (bl in txt.textBlocks) for (ln in bl.lines) for (el in ln.elements) {
                        val word = natmalDadeum(el.text)
                        val rc = el.boundingBox ?: continue
                        if (word.isEmpty()) continue
                        val n = Natmal(word, rc.exactCenterX() / w, rc.exactCenterY() / h, rc.left / w, rc.top / h, rc.right / w, rc.bottom / h)
                        // 가리키는 쪽 뒤(손 쪽)에 있는 글은 버림
                        val ap = (n.cx - tx) * dx + (n.cy - ty) * dy
                        if (ap <= -0.01) continue
                        val an = px >= n.l && px <= n.r && py >= n.t && py <= n.b
                        val d = if (an) 0.0 else sqrt((n.cx - px) * (n.cx - px) + (n.cy - py) * (n.cy - py))
                        if (d < 0.12 && (best == null || d < bestD)) { best = n.w; bestD = d }
                    }
                }
                var hwakjeong: String? = null
                val gg = best
                if (gg != null) {
                    if (gg == apGeul) hwakjeong = gg   // 두 장 잇달아 같은 글이면 읽음(손떨림 줄이기)
                    apGeul = gg
                } else {
                    apGeul = ""
                }
                allida(true, gg != null, hwakjeong)
            }
            .addOnFailureListener { allida(true, false, null) }
            .addOnCompleteListener { jang.kkeut() }
    }

    /** 낱말 앞뒤의 문장 부호를 뗌(아이폰 trimmingCharacters(in: .punctuationCharacters)) */
    private fun natmalDadeum(s: String): String = s.trim().trim { ch ->
        val ty = Character.getType(ch)
        ty == Character.CONNECTOR_PUNCTUATION.toInt() || ty == Character.DASH_PUNCTUATION.toInt() ||
            ty == Character.START_PUNCTUATION.toInt() || ty == Character.END_PUNCTUATION.toInt() ||
            ty == Character.INITIAL_QUOTE_PUNCTUATION.toInt() || ty == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
            ty == Character.OTHER_PUNCTUATION.toInt()
    }

    // MARK: 알리기(화면 줄)

    private fun allida(son: Boolean, geulItda: Boolean, geul: String?) {
        if (!kyeojim) return
        val now = System.currentTimeMillis()
        if (!son) {
            if (sonBoim && now - sonT > 1500) { sonBoim = false; sonEopT = now; sonEopMalSu = 0 }
            if (!sonBoim && now - sonEopT > 3000 && sonEopMalSu < 3 && now - sonEopMalT >= 3000) {
                sonEopMalSu += 1
                sonEopMalT = now
                mal("손가락이 보이지 않습니다. 폰을 조금 뒤로 빼 주십시오.", MalGeup.JEONGBO)
            }
            return
        }
        sonT = now
        if (!sonBoim) {
            sonBoim = true
            sonEopMalSu = 0
            Eum.naegi(EumJong.HWAKSIN)
            mal("손가락이 보입니다.", MalGeup.JEONGBO)
        }
        if (geul != null && geul != malHanGeul) {
            malHanGeul = geul
            majimak = geul
            saeT = now
            geulEopMal = false
            ilgeun.add(0, geul)
            while (ilgeun.size > 100) ilgeun.removeAt(ilgeun.size - 1)
            mal(geul)
            byeonhwa?.invoke()
        } else if (!geulItda && !geulEopMal && now - saeT > 6000) {
            geulEopMal = true
            mal("손끝 가까이에 글자가 없습니다.", MalGeup.JEONGBO)
        }
    }

    private fun mal(t: String, g: MalGeup = MalGeup.ANNAE, sseuGi: Boolean = false) = KameraNun.mal(t, g, sseuGi)
}

/** 가리키고 말하기 화면 — 둘러보기 탭 */
class GarikiHwamyeon : KameraHwamyeon("가리키고 말하기") {
    override val bupum: KameraNunBupum get() = GarikiIlgi
    override fun kyeogi(t: GilnunActivity) = GarikiIlgi.kyeogi(t, bogi)

    private var mokPyeol = false
    private var mok: List<String> = emptyList()   // 펼칠 때의 읽은 글(펼친 동안 줄이 흔들리지 않게)
    private val m5 = DrMok5()

    /** 모델을 받은 뒤 켤 때 — 지금 이 화면의 카메라 화면 */
    fun bogiBatgi(): PreviewView? = bogi

    override fun juljul(t: GilnunActivity): View {
        val g = GarikiIlgi
        val kb = t.danchu(kGeul()) { if (g.kyeojim) g.kkeugi(true) else g.kyeogi(t, bogi) }
        val dd = t.danchu("방금 읽은 것 다시 듣기") { g.dasiDeutgi() }
        bogiNeoki(t, dd)
        lateinit var mb: Button
        mb = t.danchu(mokGeul()) {
            mokPyeol = !mokPyeol
            if (mokPyeol) { mok = ArrayList(g.ilgeun); m5.saeMok() }
            t.dasiGeurigi()
        }
        if (mokPyeol) {
            if (mok.isEmpty()) t.geul("아직 읽은 글이 없습니다.")
            else m5.geurigi(t, mok) { s -> t.geul(s, true) }
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
        alaDuSil(t, "전자레인지, 세탁기, 엘리베이터, 키오스크처럼 단추가 많은 곳에서 손가락으로 가리킨 곳의 글자만 골라 읽어 드립니다. 화면을 여시면 바로 시작합니다. 한 손으로 폰을 단추판 쪽으로 들고, 다른 손 검지로 단추를 가리키십시오. 손끝을 찾으면 손가락이 보입니다라고 한 번 알리고, 손끝이 가리키는 쪽 가장 가까운 낱말을 읽어 드립니다. 손가락을 옮기시면 새 글에 닿을 때마다 읽고, 같은 글에 머무시면 되풀이하지 않습니다. 손가락이 화면 밖으로 나가면 폰을 조금 뒤로 빼시라고 알려 드립니다. 어두우면 이 안의 손전등을 켜십시오. 카메라 눈 말소리를 끄시면 화면 글자로만 남습니다. 3분 동안 새로 읽은 글이 없거나 이 화면을 떠나시면 카메라를 끕니다. 말로 하기에서 가리키는 거 읽어 줘라고 하셔도 열립니다. 안드로이드에서는 처음 한 번 손가락 찾기 자료(약 8메가바이트)를 받아야 하며, 그 뒤로는 사진과 글을 어디로도 보내지 않고 인터넷 없이 폰 안에서만 읽습니다.") {
            bulbitDanchu(t)
        }
        return kb
    }

    private fun kGeul() = if (GarikiIlgi.kyeojim) "멈추기 — 가리킨 글을 읽는 중" else "이어 읽기 — 카메라를 켜고 가리킨 글 읽기"
    private fun mokGeul() = if (mokPyeol) "읽은 글 접기" else "읽은 글 펼치기 — ${GarikiIlgi.ilgeun.size}개"
}
