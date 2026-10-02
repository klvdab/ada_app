// 안드로이드 길눈 — 사진 읽어 주기(묶음 b4_dulreo, 아이폰 DulreoView.swift SajinView 2.7.0 을 같은 말로 옮김)
// 사진 찍어 읽어 주기 — 찍으시면 무엇이 보이는지 짧게(boki), 더 자세히 읽어 주기는 길게(jasehi). 이 두 가지는 사진을 협회 나스(sajin.php)로 보내 읽습니다.
// 글씨만 읽어 주기는 사진을 어디로도 보내지 않고 폰 안에서(b3 GeulIlgi 의 한글 글자 알아보기) 글씨만 읽습니다.
// 아이폰은 사진기 화면(UIImagePickerController)을 따로 엶. 안드로이드는 화면을 여는 순간 카메라 눈 틀(Kamera)로 카메라를 켜 두고,
// 사진 찍어 읽어 주기를 누르면 그때의 한 장을 그대로 씀 — 사진기 앱의 셔터 단추를 찾지 않아도 되게(톡백).
// 찍은 한 장은 긴 쪽 1600점 안으로 줄여(카메라 눈 틀이 주는 장은 1280점 안팎) JPEG 70 으로 보냅니다. 폰에는 남기지 않습니다.
package kr.or.ada.app.gilnun

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.camera.view.PreviewView
import java.io.ByteArrayOutputStream

object SajinIlgi : KameraNunBupum {
    override val ireum = "사진 읽어 주기"
    override var kyeojim = false
        private set
    var byeonhwa: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var jjikgiDwi: ((Bitmap) -> Unit)? = null
    private var saeT = 0L
    private val sigye = object : Runnable {
        override fun run() {
            if (!kyeojim) return
            // 5분 동안 찍지 않으면 카메라를 끔(배터리) — 다시 찍으시면 저절로 켬
            if (System.currentTimeMillis() - saeT > 300000) { kkeugi(false); return }
            main.postDelayed(this, 30000)
        }
    }

    init { Kamera.deungrok(this) }

    fun kyeogi(a: GilnunActivity? = null, bogi: PreviewView? = null, dwe: (() -> Unit)? = null) {
        val t = a ?: Kamera.hwalseongEotgi() ?: return
        if (kyeojim) { if (bogi != null) Kamera.bogiDalgi(bogi); dwe?.invoke(); return }
        Kamera.heorak(t, this, "사진 읽어 주기를") {
            Kamera.sijak(t, this, bogi, 200L, { jang -> bunseok(jang) }) { ok ->
                if (ok) {
                    kyeojim = true
                    saeT = System.currentTimeMillis()
                    Girok.namgi("sajin_kamera", mapOf("kyeogi" to true))
                    main.removeCallbacks(sigye)
                    main.postDelayed(sigye, 30000)
                    byeonhwa?.invoke()
                    dwe?.invoke()
                }
            }
        }
    }

    override fun kkeugi(malHagi: Boolean) {
        Kamera.kkeugi(this)
        main.removeCallbacks(sigye)
        jjikgiDwi = null
        if (!kyeojim) return
        kyeojim = false
        if (malHagi) Sori.mal("사진 읽어 주기 카메라를 껐습니다.", MalGeup.JEONGBO)
        byeonhwa?.invoke()
    }

    /** 한 장 찍기 — 카메라가 꺼져 있으면 켠 뒤 찍음. 결과(바로 세운 그림)는 화면 줄 */
    fun jjikgi(t: GilnunActivity, bogi: PreviewView?, kkeut: (Bitmap) -> Unit) {
        saeT = System.currentTimeMillis()
        val f: (Bitmap) -> Unit = { bm -> main.post { kkeut(bm) } }
        if (kyeojim) { jjikgiDwi = f; return }
        kyeogi(t, bogi) {
            // 켜자마자는 노출·초점이 덜 맞으므로 0.8초 뒤의 장을 씀
            main.postDelayed({ if (kyeojim) jjikgiDwi = f }, 800)
        }
    }

    /** 분석 줄 — 찍기를 기다리는 동안만 한 장을 꺼내고, 나머지는 곧바로 놓음 */
    private fun bunseok(jang: KameraJang) {
        val f = jjikgiDwi
        if (f == null) { jang.kkeut(); return }
        jjikgiDwi = null
        val bm = try { jang.baroBitmap() } catch (e: Exception) { null }
        jang.kkeut()
        if (bm != null) {
            Eum.naegi(EumJong.HWAKSIN)
            f(bm)
        }
    }

    /** 긴 쪽 1600점으로 줄여 JPEG(아이폰 SajinView.jureogi) */
    fun jureogi(bm: Bitmap): ByteArray? {
        return try {
            val k = 1600.0 / maxOf(bm.width, bm.height)
            val b = if (k < 1) Bitmap.createScaledBitmap(bm, (bm.width * k).toInt(), (bm.height * k).toInt(), true) else bm
            val o = ByteArrayOutputStream()
            b.compress(Bitmap.CompressFormat.JPEG, 70, o)
            o.toByteArray()
        } catch (e: Exception) {
            null
        }
    }
}

/** 사진 읽어 주기 화면 — 둘러보기 탭 */
class SajinHwamyeon : KameraHwamyeon("사진 읽어 주기") {
    override val bupum: KameraNunBupum get() = SajinIlgi
    override fun kyeogi(t: GilnunActivity) = SajinIlgi.kyeogi(t, bogi)

    private var sajin: Bitmap? = null
    private var geul = ""
    private var ilneun = false
    private var geulChojeom = false
    private var geulView: TextView? = null

    override fun juljul(t: GilnunActivity): View {
        val jb = t.danchu(if (ilneun) "사진을 읽고 있습니다" else (if (sajin == null) "사진 찍어 읽어 주기" else "다시 찍기")) {
            if (ilneun) return@danchu
            if (!t.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)) {
                Sori.mal("이 기기에서는 카메라를 쓸 수 없습니다.")
                return@danchu
            }
            SajinIlgi.jjikgi(t, bogi) { bm ->
                sajin = bm
                bonaegi(t, "boki")
            }
        }
        bogiNeoki(t, jb)
        geulView = null
        if (geul.isNotEmpty()) geulView = t.geul(geul, true)
        if (sajin != null && !ilneun) {
            t.danchu("더 자세히 읽어 주기") { bonaegi(t, "jasehi") }
            t.danchu("글씨만 읽어 주기 — 폰 안에서") { geulssi(t) }
        }
        t.danchu("사람에게 물어보기 — 긴급통화서비스") { t.cheotHwamyeonEuro(GinGeupHwamyeon()) }
        SajinIlgi.byeonhwa = null
        val gv = geulView
        if (geulChojeom && gv != null) {
            geulChojeom = false
            t.chojeomOmgigi(gv)
        }
        return jb
    }

    private fun allim(t: GilnunActivity, s: String) {
        geul = s
        Sori.mal(s)
        geulChojeom = true
        if (t.wiHwamyeon === this) t.dasiGeurigi()
    }

    private fun bonaegi(t: GilnunActivity, mode: String) {
        val bm = sajin ?: return
        ilneun = true
        if (t.wiHwamyeon === this) t.dasiGeurigi()
        Sori.mal("사진을 읽고 있습니다. 잠시만 기다려 주십시오.", MalGeup.JEONGBO)
        Girok.namgi("sajin", mapOf("mode" to mode))
        DrNet.dwiSil {
            val jpeg = SajinIlgi.jureogi(bm)
            Handler(Looper.getMainLooper()).post {
                if (jpeg == null) { ilneun = false; allim(t, "사진을 줄이지 못했습니다. 다시 찍어 주십시오."); return@post }
                Dulreo.sajin(jpeg, mode) { _, mal ->
                    ilneun = false
                    allim(t, mal)
                }
            }
        }
    }

    /** 글씨만 — 폰 안 글자 알아보기(통신 없이) */
    private fun geulssi(t: GilnunActivity) {
        val bm = sajin ?: return
        ilneun = true
        if (t.wiHwamyeon === this) t.dasiGeurigi()
        GeulIlgi.ilgi(bm, 0) { juldeul, _ ->
            ilneun = false
            allim(t, if (juldeul.isEmpty()) "사진에서 글씨를 찾지 못했습니다. 글씨에 더 가까이 대고 다시 찍어 보십시오." else juldeul.joinToString(" "))
        }
        Girok.namgi("sajin", mapOf("mode" to "geulja_pon"))
    }
}
