// AI점자도서관 안드로이드 — 0.8.7 (261121-L26) 소리로 이끄는 카메라 (도클, 이사장님 결정 2026-11-21) — 아이폰 KameraAnnae.swift 와 같은 잣대·같은 말
// 1. 「보이는 것 읽어 주기」: 비추는 글자를 읽고, 글자가 없으면 무엇처럼 보이는지(ML Kit 그림 이름표)
// 2. 복지카드 글자 덩어리가 화면 가운데 오도록 시계 방향으로 이끔(「폰을 9시 방향으로 조금 옮겨 주십시오」)
// 3. 가운데 오면 저절로 찍고, 폰 안에서 글자를 읽어 「복지카드·시각장애·회원 이름」이 맞는지만 돌려줌
//    읽은 글자(주민번호 등)는 폰 밖으로 보내지 않음. 모델은 앱 안에 실린 ML Kit(인터넷 없이 폰 안에서)
package kr.or.ada.app.lib

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

class KameraAnnaeActivity : ComponentActivity() {
    companion object {
        const val IREUM = "ireum"; const val PAIL = "pail"; const val BOKJI = "bokji"; const val SIGAK = "sigak"; const val IREUM_OK = "ireumOk"
        private val URI = mapOf("Paper" to "종이", "Document" to "문서", "Text" to "글자", "Person" to "사람", "Hand" to "손", "Table" to "탁자", "Desk" to "책상", "Book" to "책", "Mobile phone" to "전화기", "Cup" to "컵", "Bottle" to "병", "Food" to "음식", "Dog" to "개", "Cat" to "고양이", "Chair" to "의자", "Door" to "문", "Window" to "창문", "Car" to "자동차", "Computer" to "컴퓨터", "Keyboard" to "자판", "Screen" to "화면", "Wall" to "벽", "Floor" to "바닥", "Sky" to "하늘", "Tree" to "나무", "Plant" to "식물", "Money" to "돈", "Bag" to "가방", "Shoe" to "신발", "Box" to "상자", "Card" to "카드")
    }
    private lateinit var bogi: PreviewView
    private val pool = Executors.newSingleThreadExecutor()
    private val geulja by lazy { TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()) }
    private val ireumpyo by lazy { ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS) }
    private var capture: ImageCapture? = null
    private var tts: TextToSpeech? = null
    @Volatile private var lastBun = 0L
    @Volatile private var bunJung = false
    @Volatile private var jjikJung = false
    @Volatile private var bogiMode = false
    private var majeum = 0
    private var lastMal = ""; private var lastMalT = 0L
    private var ireum = ""

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        ireum = (intent.getStringExtra(IREUM) ?: "").replace(" ", "")
        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) tts?.language = Locale.KOREAN }
        val fr = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        bogi = PreviewView(this); fr.addView(bogi, FrameLayout.LayoutParams(-1, -1))
        val dan = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 60) }
        fun d(t: String, f: () -> Unit) = Button(this).apply { text = t; textSize = 22f; setTextColor(Color.WHITE); setBackgroundColor(Color.argb(160, 0, 0, 0)); setOnClickListener { f() } }.also { dan.addView(it, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 24 }) }
        d("보이는 것 읽어 주기") { bogiMode = true; lastBun = 0 }
        d("지금 바로 찍기") { jjikgi() }
        d("닫기") { setResult(RESULT_CANCELED); finish() }
        fr.addView(dan, FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        setContentView(fr)
        val f = ProcessCameraProvider.getInstance(this)
        f.addListener({
            val cp = runCatching { f.get() }.getOrNull() ?: run { mal("카메라를 열지 못했습니다.", true); return@addListener }
            val pv = Preview.Builder().build().also { it.setSurfaceProvider(bogi.surfaceProvider) }
            val an = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also { it.setAnalyzer(pool) { px -> salpigi(px) } }
            capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
            runCatching { cp.unbindAll(); cp.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, pv, an, capture) }.onFailure { mal("카메라를 열지 못했습니다.", true) }
        }, ContextCompat.getMainExecutor(this))
        mal("카메라가 열렸습니다. 복지카드를 평평한 곳에 놓고, 폰을 카드 위 한 뼘 높이에서 들어 주십시오. 카드가 가운데로 오도록 소리로 알려 드리고, 가운데 오면 저절로 찍습니다.", true)
    }
    override fun onDestroy() { super.onDestroy(); runCatching { tts?.shutdown() }; pool.shutdown() }

    /** 톡백이 켜져 있으면 톡백 알림으로, 꺼져 있으면 폰 목소리로. 같은 말은 3초 안에 되풀이하지 않음 */
    private fun mal(s: String, sege: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!sege && s == lastMal && now - lastMalT < 3000) return
        lastMal = s; lastMalT = now
        runOnUiThread {
            val am = getSystemService(ACCESSIBILITY_SERVICE) as? AccessibilityManager
            if (am?.isTouchExplorationEnabled == true) bogi.announceForAccessibility(s) else tts?.speak(s, TextToSpeech.QUEUE_FLUSH, null, "kamera")
        }
    }

    // 0.7초에 한 번 살핌
    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun salpigi(px: ImageProxy) {
        val now = System.currentTimeMillis()
        val img = px.image
        if (jjikJung || bunJung || now - lastBun < 700 || img == null) { px.close(); return }
        lastBun = now; bunJung = true
        val rot = px.imageInfo.rotationDegrees
        val w = (if (rot == 90 || rot == 270) px.height else px.width).toFloat()
        val h = (if (rot == 90 || rot == 270) px.width else px.height).toFloat()
        val inp = InputImage.fromMediaImage(img, rot)
        if (bogiMode) {
            bogiMode = false
            geulja.process(inp).addOnCompleteListener { t ->
                val lines = t.result?.textBlocks?.flatMap { b -> b.lines.map { it.text } } ?: emptyList()
                if (lines.isNotEmpty()) { mal("보이는 글자입니다. " + lines.take(4).joinToString(". "), true); px.close(); bunJung = false }
                else ireumpyo.process(inp).addOnCompleteListener { l ->
                    val top = l.result?.filter { it.confidence > 0.6f }?.take(2)?.map { URI[it.text] ?: it.text } ?: emptyList()
                    mal(if (top.isEmpty()) "무엇인지 알아보기 어렵습니다. 조금 더 가까이 비추어 주십시오." else "이것처럼 보입니다. " + top.joinToString(", "), true)
                    px.close(); bunJung = false
                }
            }
            return
        }
        geulja.process(inp).addOnCompleteListener { t ->
            try {
                val blocks = t.result?.textBlocks ?: emptyList()
                val all = blocks.joinToString(" ") { it.text }
                if (blocks.isEmpty()) { majeum = 0; mal("카드가 보이지 않습니다. 폰을 카드 바로 위, 한 뼘 높이로 옮겨 주십시오."); return@addOnCompleteListener }
                // 카드 글자 덩어리 전체의 테두리(세운 폰 기준)
                val u = Rect(); blocks.forEach { b -> b.boundingBox?.let { if (u.isEmpty) u.set(it) else u.union(it) } }
                if (u.isEmpty) return@addOnCompleteListener
                val dx = u.exactCenterX() / w - 0.5f; val dy = u.exactCenterY() / h - 0.5f
                val kugi = (u.width() * u.height()) / (w * h)
                val hal = ArrayList<String>()
                fun bang(d: Float, plus: String, minus: String) { val a = kotlin.math.abs(d); if (a < 0.1f) return; hal.add((if (d > 0) plus else minus) + if (a > 0.25f) " 방향으로 많이" else " 방향으로 조금") }
                bang(dx, "3시", "9시")    // 글자가 화면 오른쪽에 있으면 폰을 3시 쪽으로
                bang(dy, "6시", "12시")   // 화면 좌표는 아래로 갈수록 커짐 — 글자가 아래쪽이면 폰을 6시 쪽으로
                if (hal.isNotEmpty()) { majeum = 0; mal("폰을 " + hal.joinToString(", ") + " 옮겨 주십시오."); return@addOnCompleteListener }
                if (kugi < 0.08f) { majeum = 0; mal("가운데입니다. 폰을 카드 쪽으로 조금 내려 주십시오."); return@addOnCompleteListener }
                if (kugi > 0.6f) { majeum = 0; mal("너무 가깝습니다. 폰을 조금 들어 올려 주십시오."); return@addOnCompleteListener }
                if (!(all.contains("복지") || all.contains("장애"))) { majeum = 0; mal("가운데입니다. 복지카드 앞면이 위를 보게 놓아 주십시오."); return@addOnCompleteListener }
                majeum++
                if (majeum == 1) mal("좋습니다. 그대로 멈춰 주십시오.", true)
                if (majeum >= 2) runOnUiThread { jjikgi() }
            } finally { px.close(); bunJung = false }
        }
    }

    private fun jjikgi() {
        val c = capture ?: return
        if (jjikJung) return
        jjikJung = true
        mal("찍습니다.", true)
        val f = File(cacheDir, "jeung").apply { mkdirs() }.let { File(it, "annae_${System.currentTimeMillis()}.jpg") }
        c.takePicture(ImageCapture.OutputFileOptions.Builder(f).build(), ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onError(e: ImageCaptureException) { jjikJung = false; majeum = 0; mal("찍지 못했습니다. 다시 맞춰 주십시오.", true) }
            override fun onImageSaved(o: ImageCapture.OutputFileResults) {
                mal("찍었습니다. 글자를 살피는 중입니다.", true)
                val inp = runCatching { InputImage.fromFilePath(this@KameraAnnaeActivity, android.net.Uri.fromFile(f)) }.getOrNull()
                if (inp == null) { kkeut(f, false, false, false); return }
                geulja.process(inp).addOnCompleteListener { t ->
                    val tb = (t.result?.text ?: "").replace(" ", "").replace("\n", "")
                    kkeut(f, tb.contains("복지카드") || tb.contains("복지") || tb.contains("장애인등록") || tb.contains("장애인증명"), tb.contains("시각"), ireum.length >= 2 && tb.contains(ireum))   // 읽은 글자는 여기서 버림
                }
            }
        })
    }
    private fun kkeut(f: File, bokji: Boolean, sigak: Boolean, ir: Boolean) {
        setResult(RESULT_OK, Intent().putExtra(PAIL, f.absolutePath).putExtra(BOKJI, bokji).putExtra(SIGAK, sigak).putExtra(IREUM_OK, ir))
        finish()
    }
}
