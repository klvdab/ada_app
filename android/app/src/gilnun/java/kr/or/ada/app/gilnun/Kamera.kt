// 안드로이드 길눈 — 카메라 눈 틀(묶음 b3_kamera, 아이폰 길눈 카메라 눈 묶음을 옮기는 바탕)
// 아이폰은 카메라 눈마다 AVCaptureSession 을 따로 두고 "카메라는 한 곳만"(켤 때 다른 눈을 모두 끔)을 지킵니다.
// 안드로이드는 카메라 하나(CameraX)를 이 틀이 쥐고, 카메라 눈(부품)이 빌려 씁니다. 한 번에 한 부품만.
// 사진과 글은 어디로도 보내지 않습니다(폰 안에서만 — ML Kit 모델은 앱 안에 실려 있어 인터넷 없이 돎).
//
// ── 다른 묶음(b4 등)이 쓰는 법 ───────────────────────────────────────────────────────────
//  1) 부품(엔진) — object 로 만들고 KameraNunBupum 을 이어받음. init { Kamera.deungrok(this) } 로 등록
//       object SangpumIlgi : KameraNunBupum {
//           override val ireum = "상품 바코드 읽기"
//           override var kyeojim = false; private set
//           init { Kamera.deungrok(this) }
//           fun kyeogi(a: GilnunActivity? = null, bogi: PreviewView? = null) {
//               val t = a ?: Kamera.hwalseongEotgi() ?: return
//               Kamera.heorak(t, this, "상품 바코드 읽기를") {            // 카메라 허락(없으면 여쭘, 요청 번호 11)
//                   Kamera.sijak(t, this, bogi, 500L, { jang -> bunseok(jang) }) { ok -> if (ok) { kyeojim = true; … } }
//               }
//           }
//           private fun bunseok(jang: KameraJang) {                   // 분석 줄(화면 줄 아님)에서 불림
//               client.process(jang.inputImage())                      // ML Kit 에 곧바로
//                   .addOnSuccessListener { … }                       // 결과는 화면 줄
//                   .addOnCompleteListener { jang.kkeut() }            // ★다 쓰면 꼭 kkeut — 그래야 다음 장이 옴
//           }
//           override fun kkeugi(malHagi: Boolean) { Kamera.kkeugi(this); if (!kyeojim) return; kyeojim = false; … }
//       }
//  2) 화면 — KameraHwamyeon 을 이어받음. 화면을 떠나면(뒤로·다른 탭) 부품을 말없이 끄고, 다시 보이면 저절로 켬(아이폰 onAppear/onDisappear)
//       class SangpumHwamyeon : KameraHwamyeon("상품 바코드 읽기") {
//           override val bupum: KameraNunBupum get() = SangpumIlgi
//           override fun kyeogi(t: GilnunActivity) = SangpumIlgi.kyeogi(t, bogi)
//           override fun juljul(t: GilnunActivity): View {
//               val b = t.danchu(…) { … }      // 첫 줄(커서가 감)
//               bogiNeoki(t, b)                // 카메라 화면(보지 않는 화면이면 빼도 됨 — 사람 감지처럼)
//               malsoriDanchu(t)               // 카메라 눈 말소리 켜기·끄기
//               return b
//           }
//       }
//  3) 그 밖에
//       KameraJang  — bitmap(누운 그대로), dolim(바로 세우려면 돌릴 도), garo·sero(세운 폰 기준 크기), inputImage(), baroBitmap(), balgi(0~1), noChul(노출)
//       Kamera.bulbit(true/false) 손전등, Kamera.bulbitItda, Kamera.garoSijya(ctx) 카메라 가로(누운 쪽) 시야 도
//       Kamera.modukkeugi(malHagi) 모든 카메라 눈 끄기(말로 하기 "그만"), Kamera.chajiHagi/noaJugi — CameraX 밖에서 카메라를 쓰는 부품(ARCore)
//       KameraNun.mal(t, geup, sseuGi) 카메라 눈 말소리 설정을 따르는 말, KameraNun.sigye(도) 시계 방향, KameraNun.jindong("arrive"), KameraNun.ttil(헤르츠, 초, 크기)
//       KameraNun.dulreoYeolgi(a, 화면) 둘러보기 탭 첫 화면 위에 화면을 엶(말로 하기에서)
// ─────────────────────────────────────────────────────────────────────────────────────────
package kr.or.ada.app.gilnun

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.mlkit.vision.common.InputImage
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** 카메라 눈 부품 하나 — 한 번에 하나만 켜짐 */
interface KameraNunBupum {
    /** 부품 이름(기록·말) */
    val ireum: String
    val kyeojim: Boolean
    /** 끄기 — malHagi 가 거짓이면 말없이 */
    fun kkeugi(malHagi: Boolean)
}

/** 카메라 노출(그 장을 찍을 때) — 빛 알아보기의 장면 밝기 셈 */
class KameraNochul(val noChulNs: Long, val iso: Int, val jori: Float)

/** 카메라 한 장 — 분석 줄에서 넘어옴. 다 쓰면 kkeut() 을 꼭 불러야 다음 장이 옴 */
class KameraJang internal constructor(
    /** 카메라가 준 그대로(누운) 그림 — RGBA */
    val bitmap: Bitmap,
    /** 바로 세우려면 시계 방향으로 돌릴 도(0·90·180·270). 세운 폰이면 보통 90 */
    val dolim: Int,
    val ttae: Long,
    /** 이 장 무렵의 노출(못 받는 폰이면 null) */
    val noChul: KameraNochul?,
    private val kkeutFn: () -> Unit
) {
    @Volatile private var kkeunna = false

    /** 세운 폰 기준 가로·세로(점) */
    val garo: Int get() = if (dolim % 180 == 0) bitmap.width else bitmap.height
    val sero: Int get() = if (dolim % 180 == 0) bitmap.height else bitmap.width

    /** ML Kit 에 넣을 그림 — 결과의 자리(상자)는 세운 폰 기준(garo·sero) */
    fun inputImage(): InputImage = InputImage.fromBitmap(bitmap, dolim)

    /** 바로 세운 그림(새로 만듦) */
    fun baroBitmap(): Bitmap {
        if (dolim == 0) return bitmap
        val m = Matrix()
        m.postRotate(dolim.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    /** 고르게 16×16 점을 찍어 본 평균 밝기(0 캄캄 ~ 1 하양, 화면 값 그대로) */
    fun balgi(): Double {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 32 || h < 32) return 0.0
        var hap = 0.0
        for (iy in 0 until 16) for (ix in 0 until 16) {
            val c = bitmap.getPixel(w * (2 * ix + 1) / 32, h * (2 * iy + 1) / 32)
            hap += (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0
        }
        return hap / 256.0
    }

    /** 이 장을 다 씀 — 다음 장을 받음(여러 번 불러도 됨) */
    fun kkeut() {
        if (kkeunna) return
        kkeunna = true
        kkeutFn()
    }
}

/** 카메라 하나 — CameraX 를 쥐고 부품에게 빌려 줌 */
object Kamera {
    private val main = Handler(Looper.getMainLooper())
    private val bunseokJul: ExecutorService = Executors.newSingleThreadExecutor()

    /** 길눈 화면 — 화면이 없을 때(말로 하기·간판 알림이 켤 때) 씀. 비어 있으면 말로 하기·긴급통화가 쥔 화면 */
    var hwalseong: WeakReference<GilnunActivity>? = null
    fun hwalseongEotgi(): GilnunActivity? = hwalseong?.get() ?: MalHagi.hwalseong?.get() ?: GinGeup.hwalseong?.get()

    private val bupumDeul = LinkedHashSet<KameraNunBupum>()
    /** 지금 카메라를 쥔 부품 */
    var juin: KameraNunBupum? = null
        private set

    private var saeng: ProcessCameraProvider? = null
    private var kamera: Camera? = null
    private var bogiUse: Preview? = null
    private var bunseokUse: ImageAnalysis? = null
    private var sedae = 0
    private var gwanchalHwamyeon: WeakReference<GilnunActivity>? = null
    private var hwamyeonKyeon = false

    // 분석 줄과 함께 씀
    @Volatile private var bunseokFn: ((KameraJang) -> Unit)? = null
    @Volatile private var bappeum = false
    @Volatile private var bappeumT = 0L
    @Volatile private var majimakT = 0L
    @Volatile private var gan = 400L
    @Volatile var noChul: KameraNochul? = null
        private set

    // 허락
    private var heorakDwi: ((Boolean) -> Unit)? = null
    private var heorakJuin: KameraNunBupum? = null
    private var heorakSu = 0

    private var sijyaGap = -1.0

    /** 부품 등록 — 모두 끄기·한 곳만 지키기에 씀 */
    fun deungrok(b: KameraNunBupum) {
        if (Looper.myLooper() == Looper.getMainLooper()) bupumDeul.add(b) else main.post { bupumDeul.add(b) }
    }

    /** 모든 카메라 눈 끄기 — 말로 하기 "그만", 긴급통화가 시작될 때 */
    fun modukkeugi(malHagi: Boolean) {
        for (b in bupumDeul.toList()) if (b.kyeojim) b.kkeugi(malHagi)
        juin?.let { if (it.kyeojim) it.kkeugi(malHagi) }
    }

    /** 다른 카메라 눈을 모두 끔(카메라는 한 곳만) */
    fun hanGotman(b: KameraNunBupum) {
        for (x in bupumDeul.toList()) if (x !== b && x.kyeojim) x.kkeugi(false)
        val j = juin
        if (j != null && j !== b) {
            if (j.kyeojim) j.kkeugi(false)
            if (juin === j) noaJugiSok()
        }
    }

    /** CameraX 밖에서 카메라를 쓰는 부품(ARCore 등)이 카메라를 쥠 — 다른 눈을 끄고 CameraX 를 놓음 */
    fun chajiHagi(b: KameraNunBupum) {
        hanGotman(b)
        unbind()
        juin = b
        sedae += 1
        bunseokFn = null
        gwanchal()
    }

    /** chajiHagi 로 쥔 카메라를 놓음 */
    fun noaJugi(b: KameraNunBupum) {
        if (juin !== b) return
        noaJugiSok()
    }

    private fun noaJugiSok() {
        juin = null
        sedae += 1
        bunseokFn = null
        unbind()
        hwamyeonKeojiAnke(false)
        main.removeCallbacks(tonghwaSalpim)
    }

    // MARK: 허락(요청 번호 11)

    fun heorakItda(c: Context) = ContextCompat.checkSelfPermission(c, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /** 카메라 허락 — 있으면 곧바로 f, 없으면 여쭙고 허락하시면 f. 안 되면 그렇다고 말씀드림(ireumReul 예: "QR 찾기를") */
    fun heorak(a: GilnunActivity, b: KameraNunBupum, ireumReul: String, f: () -> Unit) {
        if (heorakItda(a)) { f(); return }
        val p = a.getSharedPreferences("gilnun", Context.MODE_PRIVATE)
        val mureotda = p.getBoolean("kameraMureum", false)
        if (mureotda && !ActivityCompat.shouldShowRequestPermissionRationale(a, Manifest.permission.CAMERA)) {
            Sori.mal("카메라 허락이 꺼져 있습니다. 폰 설정의 앱, 길눈, 권한에서 카메라를 허용해 주십시오.")
            return
        }
        p.edit().putBoolean("kameraMureum", true).apply()
        heorakJuin = b
        heorakDwi = { ok -> if (ok) f() else Sori.mal("카메라를 쓸 수 없어 $ireumReul 켜지 못했습니다.") }
        Sori.mal("카메라 허락이 필요합니다. 허용을 눌러 주십시오.")
        ActivityCompat.requestPermissions(a, arrayOf(Manifest.permission.CAMERA), 11)
        // 길눈 화면이 답(요청 번호 11)을 넘겨주지 않아도 이어 가게 — 1초마다 1분 동안 살핌
        heorakSu += 1
        val n = heorakSu
        var beon = 0
        val r = object : Runnable {
            override fun run() {
                if (n != heorakSu || heorakDwi == null) return
                beon += 1
                if (heorakItda(a)) { heorakDap(a); return }
                if (beon < 60) main.postDelayed(this, 1000)
            }
        }
        main.postDelayed(r, 1000)
    }

    /** 길눈 화면의 onRequestPermissionsResult(요청 번호 11)에서 부름 */
    fun heorakDap(c: Context) {
        val f = heorakDwi ?: return
        heorakDwi = null
        heorakJuin = null
        heorakSu += 1
        f(heorakItda(c))
    }

    // MARK: 켜기·끄기(CameraX)

    /**
     * 카메라 켜기 — 다른 눈을 끄고 b 가 쥠. bogi 가 있으면 화면에도 보임.
     * ganMs: 분석 장 사이 가장 짧은 틈(밀리초). bunseok: 분석 줄에서 한 장씩(다 쓰면 jang.kkeut()).
     * dwe(되었나)는 화면 줄에서. 허락은 미리 heorak 으로 받을 것
     */
    fun sijak(a: GilnunActivity, b: KameraNunBupum, bogi: PreviewView?, ganMs: Long,
              bunseok: (KameraJang) -> Unit, dwe: (Boolean) -> Unit) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { sijak(a, b, bogi, ganMs, bunseok, dwe) }; return }
        if (GinGeup.sangtae != GinGeupSangtae.EOPSEUM) {
            Sori.mal("긴급통화 중에는 카메라 눈을 쓸 수 없습니다.")
            dwe(false)
            return
        }
        if (!heorakItda(a)) { dwe(false); return }
        hanGotman(b)
        unbind()
        juin = b
        sedae += 1
        val my = sedae
        bunseokFn = bunseok
        gan = ganMs
        bappeum = false
        majimakT = 0L
        noChul = null
        if (sijyaGap < 0) garoSijya(a)
        val f = try { ProcessCameraProvider.getInstance(a) } catch (e: Exception) { null }
        if (f == null) { silpae(b, my, "provider", dwe); return }
        f.addListener({
            if (my != sedae || juin !== b) return@addListener
            val p = try { f.get() } catch (e: Exception) { null }
            if (p == null) { silpae(b, my, "provider_get", dwe); return@addListener }
            saeng = p
            try {
                p.unbindAll()
                val ia = bunseokMandeulgi()
                val pv = if (bogi != null) Preview.Builder().build().also { it.setSurfaceProvider(bogi.surfaceProvider) } else null
                kamera = if (pv != null) p.bindToLifecycle(a, CameraSelector.DEFAULT_BACK_CAMERA, pv, ia)
                         else p.bindToLifecycle(a, CameraSelector.DEFAULT_BACK_CAMERA, ia)
                bogiUse = pv
                bunseokUse = ia
            } catch (e: Exception) {
                Girok.namgi("kamera_oryu", mapOf("dan" to "bind", "e" to (e.message ?: "")))
                silpae(b, my, "bind", dwe)
                return@addListener
            }
            hwamyeonKeojiAnke(true)
            gwanchal()
            Girok.namgi("kamera_kyeom", mapOf("bupum" to b.ireum, "bogi" to (bogi != null)))
            dwe(true)
        }, ContextCompat.getMainExecutor(a))
    }

    private fun silpae(b: KameraNunBupum, my: Int, dan: String, dwe: (Boolean) -> Unit) {
        Girok.namgi("kamera_oryu", mapOf("dan" to dan))
        if (my == sedae && juin === b) noaJugiSok()
        Sori.mal("카메라를 열지 못했습니다.")
        dwe(false)
    }

    /** 끄기 — b 가 쥐고 있을 때만(여쭙던 허락도 거둠) */
    fun kkeugi(b: KameraNunBupum) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { kkeugi(b) }; return }
        if (heorakJuin === b) { heorakDwi = null; heorakJuin = null; heorakSu += 1 }
        if (juin !== b) return
        noaJugiSok()
    }

    /** 화면에 보이는 카메라 — 화면을 다시 그리면 새 보기로 갈아 끼움(null 이면 보이지 않게) */
    fun bogiDalgi(v: PreviewView?) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { bogiDalgi(v) }; return }
        val j = juin ?: return
        val pv = bogiUse
        if (pv != null) { pv.setSurfaceProvider(v?.surfaceProvider); return }
        if (v == null) return
        // 화면 없이 켜 두었던 것을 화면이 열림 — 보기를 더해 다시 묶음
        val p = saeng ?: return
        val ia = bunseokUse ?: return
        val a = hwalseongEotgi() ?: return
        try {
            p.unbindAll()
            val np = Preview.Builder().build()
            np.setSurfaceProvider(v.surfaceProvider)
            kamera = p.bindToLifecycle(a, CameraSelector.DEFAULT_BACK_CAMERA, np, ia)
            bogiUse = np
        } catch (e: Exception) {
            Girok.namgi("kamera_oryu", mapOf("dan" to "bogi", "bupum" to j.ireum, "e" to (e.message ?: "")))
        }
    }

    /** 카메라 화면(미리 보기)이 묶여 있나 */
    val bogiItda: Boolean get() = bogiUse != null

    private fun unbind() {
        try { saeng?.unbindAll() } catch (e: Exception) {}
        kamera = null
        bogiUse = null
        bunseokUse?.clearAnalyzer()
        bunseokUse = null
        bulbitKyeojim = false
    }

    @androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
    private fun bunseokMandeulgi(): ImageAnalysis {
        val rs = ResolutionSelector.Builder()
            .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
            .build()
        val bd = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setResolutionSelector(rs)
        // 노출 값(빛 알아보기) — 장마다 카메라가 알려 주는 노출 시간·감도·조리개
        Camera2Interop.Extender(bd).setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                val t = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
                val s = result.get(CaptureResult.SENSOR_SENSITIVITY)
                val ap = result.get(CaptureResult.LENS_APERTURE)
                noChul = if (t != null && s != null && t > 0L && s > 0) KameraNochul(t, s, ap ?: 1.8f) else null
            }
        })
        val ia = bd.build()
        ia.setAnalyzer(bunseokJul) { px -> hanJang(px) }
        return ia
    }

    /** 분석 줄 — 틈과 바쁨을 보고 한 장을 부품에 넘김 */
    private fun hanJang(px: ImageProxy) {
        val fn = bunseokFn
        val now = System.currentTimeMillis()
        if (fn == null || (bappeum && now - bappeumT < 8000) || now - majimakT < gan) { px.close(); return }
        val bm: Bitmap? = try { px.toBitmap() } catch (e: Exception) { null }
        val dolim = px.imageInfo.rotationDegrees
        px.close()
        if (bm == null) return
        majimakT = now
        bappeum = true
        bappeumT = now
        val jang = KameraJang(bm, dolim, now, noChul) { bappeum = false }
        try {
            fn(jang)
        } catch (e: Exception) {
            bappeum = false
            Girok.namgi("kamera_oryu", mapOf("dan" to "bunseok", "e" to (e.message ?: "")))
        }
    }

    // MARK: 손전등

    val bulbitItda: Boolean get() = kamera?.cameraInfo?.hasFlashUnit() == true
    var bulbitKyeojim = false
        private set

    /** 손전등 켜기·끄기 — 카메라가 켜져 있고 손전등이 있어야 함 */
    fun bulbit(kyeogi: Boolean): Boolean {
        val k = kamera ?: return false
        if (!k.cameraInfo.hasFlashUnit()) return false
        k.cameraControl.enableTorch(kyeogi)
        bulbitKyeojim = kyeogi
        return true
    }

    // MARK: 시야

    /** 뒤 카메라의 가로(누운 쪽, 센서 긴 쪽) 시야 도 — 폰을 세우면 세로 시야. 모르면 63 */
    fun garoSijya(c: Context): Double {
        if (sijyaGap > 0) return sijyaGap
        var r = 63.0
        try {
            val cm = c.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            for (id in cm.cameraIdList) {
                val ch = cm.getCameraCharacteristics(id)
                if (ch.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) continue
                val fl = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                val sz = ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                if (fl != null && fl.isNotEmpty() && sz != null && fl[0] > 0f) {
                    r = 2 * atan(sz.width / (2.0 * fl[0])) * 180 / PI
                }
                break
            }
        } catch (e: Exception) {}
        if (r < 30 || r > 120) r = 63.0
        sijyaGap = r
        return r
    }

    // MARK: 화면 지킴

    /** 카메라 눈을 쓰는 동안 화면이 꺼지지 않게 */
    fun hwamyeonKeojiAnke(on: Boolean) {
        if (on == hwamyeonKyeon) return
        val w = hwalseongEotgi()?.window ?: return
        hwamyeonKyeon = on
        if (on) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /** 길눈 화면의 생애를 지켜봄 — 닫히면 모두 끄고, 가려지면 CameraX 밖 부품(ARCore)을 끔. 긴급통화가 시작되면 카메라를 내어 줌 */
    private fun gwanchal() {
        main.removeCallbacks(tonghwaSalpim)
        main.postDelayed(tonghwaSalpim, 1000)
        val a = hwalseongEotgi() ?: return
        if (gwanchalHwamyeon?.get() === a) return
        gwanchalHwamyeon = WeakReference(a)
        a.lifecycle.addObserver(LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) {
                val j = juin
                if (j is TeokAllim && j.kyeojim) j.kkeugi(false)
            } else if (e == Lifecycle.Event.ON_DESTROY) {
                modukkeugi(false)
                hwamyeonKyeon = false
            }
        })
    }

    /** 긴급통화가 카메라를 써야 하므로 — 요청이 시작되면 카메라 눈을 말없이 끔 */
    private val tonghwaSalpim = object : Runnable {
        override fun run() {
            val j = juin ?: return
            if (GinGeup.sangtae != GinGeupSangtae.EOPSEUM) {
                Girok.namgi("kamera_tonghwa_kkeum", mapOf("bupum" to j.ireum))
                modukkeugi(false)
                if (juin != null) noaJugiSok()
                return
            }
            main.postDelayed(this, 1000)
        }
    }
}

/** 카메라 눈 설정과 함께 쓰는 것 */
object KameraNun {
    private var d: SharedPreferences? = null
    private val main = Handler(Looper.getMainLooper())

    fun sijak(c: Context) {
        if (d == null) d = c.applicationContext.getSharedPreferences("gilnun", Context.MODE_PRIVATE)
    }

    /** 카메라 눈 말소리(아이폰 gn.kameraMal, 처음엔 켜짐) — 끄면 소리·화면 글자로만 */
    var kameraMal: Boolean
        get() = d?.getBoolean("kameraMal", true) ?: true
        set(v) { d?.edit()?.putBoolean("kameraMal", v)?.apply() }

    /** 카메라 눈의 말 — 말소리를 끄셨으면 sseuGi(꼭 할 말)만 */
    fun mal(t: String, g: MalGeup = MalGeup.ANNAE, sseuGi: Boolean = false) {
        if (!kameraMal && !sseuGi) return
        Sori.mal(t, g)
    }

    /** 보폭(미터) — 재지 않았으면 0.65 */
    val bopok: Double get() = try { if (Seoljeong.bopok > 0.2) Seoljeong.bopok else 0.65 } catch (e: Exception) { 0.65 }

    /** 몸 기준 도(오른쪽 +) → 1~12시(아이폰 MunChatgi.sigye) */
    fun sigye(gak: Double): Int {
        var s = (gak / 30).roundToInt()
        s = ((s % 12) + 12) % 12
        return if (s == 0) 12 else s
    }

    /** 걸음 수를 우리말로(한 걸음, 두 걸음 …, 아이폰 GeoreumSu) */
    fun georeumSu(n: Int): String {
        val ir = listOf("", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열")
        return if (n in 1..10) ir[n] else "$n"
    }

    /** 진동 — short 짧게 · arrive 세 번(찾음·도착) · long 길게 */
    @Suppress("DEPRECATION")
    fun jindong(jong: String) {
        val c = Kamera.hwalseongEotgi() ?: return
        try {
            val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31)
                (c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            else c.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            val gil = when (jong) {
                "short" -> longArrayOf(0, 70)
                "arrive" -> longArrayOf(0, 90, 90, 90, 90, 200)
                else -> longArrayOf(0, 450)
            }
            if (Build.VERSION.SDK_INT >= 26) v?.vibrate(VibrationEffect.createWaveform(gil, -1)) else v?.vibrate(gil, -1)
        } catch (e: Exception) {}
    }

    /** 짧은 소리 하나(높이 헤르츠, 길이 초, 크기 0~1) — 길 안내 소리 자리(음악을 들으셔도 들림) */
    fun ttil(hz: Double, gilCho: Double, keugi: Float) {
        main.post {
            if (Sori.malAnham) return@post
            if (Sori.deutneunJung) return@post
            val sr = 22050
            val n = (sr * gilCho).toInt()
            if (n <= 0) return@post
            val s = ShortArray(n)
            for (i in 0 until n) {
                val env = min(1.0, min(i, n - i).toDouble() / (sr * 0.008))
                s[i] = (sin(2 * PI * hz * i / sr) * 0.5 * env * 32767).toInt().toShort()
            }
            try {
                val t = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sr)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(n * 2)
                    .build()
                t.write(s, 0, n)
                t.setVolume(keugi)
                t.play()
                main.postDelayed({
                    try { t.stop() } catch (e: Exception) {}
                    t.release()
                }, n * 1000L / sr + 150)
            } catch (e: Exception) {}
        }
    }

    /** 둘러보기 탭 첫 화면 위에 h 를 엶(말로 하기 — 아이폰 TabGil.tab = 1, DulreoGil.path = [h]) */
    fun dulreoYeolgi(a: GilnunActivity, h: Hwamyeon) {
        a.tabGo(1)
        a.tabGo(1)   // 같은 탭을 다시 누르면 그 탭의 첫 화면으로
        a.yeolgi(h)
    }

    /** 모든 카메라 눈 끄기(말로 하기 "그만"·"하던 일 멈춰") */
    fun modukkeugi(malHagi: Boolean) = Kamera.modukkeugi(malHagi)
}

/**
 * 카메라 눈 화면 — 화면을 열면 부품을 저절로 켜고(jadong), 떠나면(뒤로·다른 탭) 말없이 끔.
 * 다른 탭에 갔다 돌아오면 다시 켬(아이폰 onAppear·onDisappear). 첫 줄로 커서가 감
 */
abstract class KameraHwamyeon(jemok: String) : Hwamyeon(jemok) {
    private var cheotBoim = true
    private var sumgyeojim = false
    /** 카메라 화면(bogiNeoki 로 넣었을 때) */
    protected var bogi: PreviewView? = null
        private set
    protected var alaPyeol = false
    private var cheotJulView: View? = null

    protected abstract val bupum: KameraNunBupum
    /** 화면을 열면 저절로 켬(문 찾기처럼 손으로 켜는 화면은 거짓) */
    protected open val jadong: Boolean = true
    /** 부품 켜기 — bogi 를 함께 넘김 */
    protected abstract fun kyeogi(t: GilnunActivity)
    /** 줄을 채우고 커서가 갈 첫 줄을 돌려줌 */
    protected abstract fun juljul(t: GilnunActivity): View

    final override fun chaeugi(t: GilnunActivity) {
        KameraNun.sijak(t)
        Kamera.hwalseong = WeakReference(t)
        bogi = null
        val v = juljul(t)
        cheotJulView = v
        t.chojeomJul(v)
        jikigi(t, v)
        if (bogi == null && bupum.kyeojim) Kamera.bogiDalgi(null)
    }

    override fun boilttae(t: GilnunActivity) {
        if (jadong && (cheotBoim || sumgyeojim) && !bupum.kyeojim) kyeogi(t)
        cheotBoim = false
        sumgyeojim = false
    }

    /** 상태가 바뀌어 줄이 달라졌을 때 — 다시 그리고 커서를 첫 줄로(이 화면이 보일 때만) */
    protected fun dasiGeurigoChojeom(t: GilnunActivity) {
        if (t.wiHwamyeon !== this) return
        t.dasiGeurigi()
        cheotJulView?.let { t.chojeomOmgigi(it) }
    }

    /** 화면을 떠나면 부품을 말없이 끔 — 같은 화면을 다시 그릴 때는 그대로 */
    private fun jikigi(t: GilnunActivity, v: View) {
        v.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(x: View) {}
            override fun onViewDetachedFromWindow(x: View) {
                x.removeOnAttachStateChangeListener(this)
                Handler(Looper.getMainLooper()).post {
                    val tteonam = t.isFinishing || t.isDestroyed || t.wiHwamyeon !== this@KameraHwamyeon
                    if (tteonam) {
                        sumgyeojim = true
                        if (bupum.kyeojim) bupum.kkeugi(false) else Kamera.kkeugi(bupum)
                    }
                }
            }
        })
    }

    /** 카메라 화면을 지금 자리(gijun 과 같은 줄)에 넣음 — 톡백은 건너뜀 */
    protected fun bogiNeoki(t: GilnunActivity, gijun: View) {
        val nae = gijun.parent as? ViewGroup ?: return
        val pv = PreviewView(t)
        pv.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        pv.scaleType = PreviewView.ScaleType.FILL_CENTER
        pv.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        pv.setBackgroundColor(Color.BLACK)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (t.resources.displayMetrics.heightPixels * 0.42).toInt())
        lp.topMargin = t.dp(10)
        nae.addView(pv, lp)
        bogi = pv
        if (bupum.kyeojim) Kamera.bogiDalgi(pv)
    }

    /** 화면 없이 켜진 부품(말로 켠 문 찾기 등)에 이 화면의 카메라 화면을 붙임 — 이미 붙어 있으면 그대로 */
    protected fun bogiIeum() {
        val v = bogi ?: return
        if (bupum.kyeojim && !Kamera.bogiItda) Kamera.bogiDalgi(v)
    }

    /** 카메라 눈 말소리 켜기·끄기(아이폰 Toggle "카메라 눈 말소리") — 글자는 그 자리에서만 바꿈 */
    protected fun malsoriDanchu(t: GilnunActivity): Button {
        lateinit var b: Button
        b = t.danchu(malsoriGeul()) {
            KameraNun.kameraMal = !KameraNun.kameraMal
            b.text = malsoriGeul()
            b.announceForAccessibility(if (KameraNun.kameraMal) "카메라 눈 말소리를 켰습니다." else "카메라 눈 말소리를 껐습니다.")
        }
        return b
    }

    private fun malsoriGeul() = "카메라 눈 말소리 — 지금 " + (if (KameraNun.kameraMal) "켜짐, 누르면 꺼짐" else "꺼짐, 누르면 켜짐")

    /** 손전등 켜기·끄기(카메라가 켜져 있을 때만 됨) */
    protected fun bulbitDanchu(t: GilnunActivity): Button {
        lateinit var b: Button
        b = t.danchu(bulbitGeul()) {
            if (!bupum.kyeojim) { Sori.mal("카메라가 꺼져 있어 손전등을 켤 수 없습니다."); return@danchu }
            val on = !Kamera.bulbitKyeojim
            if (!Kamera.bulbit(on)) { Sori.mal("이 폰은 손전등을 쓸 수 없습니다."); return@danchu }
            b.text = bulbitGeul()
            b.announceForAccessibility(if (on) "손전등을 켰습니다." else "손전등을 껐습니다.")
        }
        return b
    }

    private fun bulbitGeul() = "손전등 — 지금 " + (if (Kamera.bulbitKyeojim) "켜짐, 누르면 꺼짐" else "꺼짐, 누르면 켜짐")

    /** 알아 두실 것 펼치기 — 펼치면 sok(더 넣을 줄)과 글 */
    protected fun alaDuSil(t: GilnunActivity, geul: String, sok: (() -> Unit)? = null) {
        t.pyeolchigi("알아 두실 것", alaPyeol) { alaPyeol = !alaPyeol }
        if (alaPyeol) {
            sok?.invoke()
            t.geul(geul)
        }
    }
}
