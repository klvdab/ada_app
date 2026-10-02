// 안드로이드 길눈 — 발 앞 계단·턱 알림(묶음 b3_kamera, 아이폰 TeokAllim.swift 2.24.0 을 같은 셈·같은 말로 옮김) ★관리자 시험 중
// 발 앞 바닥 높이가 갑자기 꺼지거나 솟는 곳을 "두 걸음 앞, 내려가는 턱, 약 15센티미터"처럼 알립니다.
// 아이폰은 라이다 깊이(프로 모델). 안드로이드는 구글 AR(ARCore)의 깊이(Depth API — 카메라 한 대와 움직임으로 깊이를 잼,
//   비행시간 센서가 있는 폰은 더 정확)를 씁니다. 깊이를 지원하는 폰에서만 되고, 구글 플레이 AR 서비스가 있어야 합니다(없으면 설치 화면을 엶).
// 셈은 아이폰과 같음 — 깊이 점들을 세상 좌표(중력 기준)로 옮겨 몸 앞 폭 70센티미터 띠의 바닥 높이를 10센티미터 칸마다 모으고,
//   이어지는 칸의 높이가 6센티미터 넘게 끊기는 첫 곳을 찾음. 세 장(0.6초) 잇달아 같아야 알림.
// 안전 경고라 말소리를 꺼도 늘 알립니다. 오래 시험한 뒤 대표님 승인으로 모든 분께 엽니다(지금은 관리자 열쇠가 있는 폰에서만 보임).
// 특허 확인(아이폰 2026-10-01과 같음): 학습 모델로 계단을 알아보거나 물체를 가르거나 영상 경계선을 세지 않음 — 깊이로 바닥 높이의 끊김만 셈.
// 폰 안에서만 하며, 사진이나 깊이 자료를 담지도 보내지도 않습니다. 카메라 화면을 띄우지 않습니다(아이폰과 같음).
package kr.or.ada.app.gilnun

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.View
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Camera
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object TeokAllim : KameraNunBupum {
    override val ireum = "발 앞 계단·턱 알림"
    @Volatile override var kyeojim = false
        private set
    var sangtae = ""
        private set
    var byeonhwa: (() -> Unit)? = null

    /** 관리자 시험 중 — 관리자 열쇠가 있는 폰에서만 보임 */
    fun boim(c: Context) = KameraGwanli.boim(c)

    private val main = Handler(Looper.getMainLooper())
    private var sedae = 0
    // GL 줄에서만
    @Volatile private var glH: Handler? = null
    private var gl: HandlerThread? = null
    private var session: Session? = null
    private var eglD: EGLDisplay? = null
    private var eglC: EGLContext? = null
    private var eglS: EGLSurface? = null
    private var tex = 0
    private var boT = 0L
    private var apGeot = ""          // 앞 장에서 찾은 것(세 장 잇달아 같아야 알림)
    private var gatSu = 0
    // 아래는 화면 줄에서만
    private var malGeot = ""
    private var malT = 0L
    private var malGeoreum = 99
    private var gyeolT = 0L
    private var saeT = 0L
    private val sigye = object : Runnable {
        override fun run() {
            if (!kyeojim) return
            salpigi()
            if (kyeojim) main.postDelayed(this, 5000)
        }
    }

    init { Kamera.deungrok(this) }

    // MARK: 켜기·끄기

    fun kyeogi(a: GilnunActivity? = null) {
        val t = a ?: Kamera.hwalseongEotgi() ?: return
        if (kyeojim) return
        if (!boim(t)) { Sori.mal("발 앞 계단·턱 알림은 안드로이드 길눈에서 아직 관리자 시험 중입니다."); return }
        val av = try { ArCoreApk.getInstance().checkAvailability(t) } catch (e: Exception) { null }
        if (av == null || av.isUnsupported) {
            Sori.mal("이 폰은 구글 AR 깊이 재기를 지원하지 않아 발 앞 계단·턱 알림을 쓸 수 없습니다.")
            return
        }
        if (av.isTransient || av.isUnknown) {
            Sori.mal("이 폰이 구글 AR 을 쓸 수 있는지 살피는 중입니다. 잠시 뒤 다시 눌러 주십시오.")
            return
        }
        if (av != ArCoreApk.Availability.SUPPORTED_INSTALLED) {
            try {
                val st = ArCoreApk.getInstance().requestInstall(t, true)
                if (st == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                    Sori.mal("구글 플레이 AR 서비스를 깔아야 합니다. 설치 화면을 엽니다. 다 깔고 돌아오시면 다시 눌러 주십시오.")
                    return
                }
            } catch (e: Exception) {
                Girok.namgi("teok_oryu", mapOf("dan" to "install", "e" to (e.message ?: "")))
                Sori.mal("구글 플레이 AR 서비스를 깔지 못해 발 앞 계단·턱 알림을 켜지 못했습니다.")
                return
            }
        }
        Kamera.heorak(t, this, "발 앞 계단·턱 알림을") { sijak(t) }
    }

    private fun sijak(t: GilnunActivity) {
        if (kyeojim) return
        if (GinGeup.sangtae != GinGeupSangtae.EOPSEUM) { Sori.mal("긴급통화 중에는 카메라 눈을 쓸 수 없습니다."); return }
        Kamera.chajiHagi(this)   // 카메라는 한 곳만 — 다른 눈을 끄고 CameraX 를 놓음
        val ctx = t.applicationContext
        sedae += 1
        val my = sedae
        kyeojim = true
        malGeot = ""; malGeoreum = 99
        saeT = System.currentTimeMillis(); gyeolT = saeT
        sangtae = "발 앞을 살피는 중입니다."
        Kamera.hwamyeonKeojiAnke(true)
        val th = HandlerThread("gilnun-teok")
        th.start()
        gl = th
        val h = Handler(th.looper)
        glH = h
        h.post {
            val oryu = glJunbi(ctx)
            main.post {
                if (my != sedae) return@post
                if (oryu != null) {
                    kkeugi(false)
                    Sori.mal(oryu)
                    return@post
                }
                Sori.mal("발 앞 계단·턱 알림을 시작합니다. 폰을 가슴 앞에 들고 카메라가 앞쪽 바닥을 보도록 조금 숙여 주십시오.")
                Girok.namgi("teok", mapOf("kyeogi" to true))
                byeonhwa?.invoke()
            }
            if (oryu == null) h.post(jangRun)
        }
        main.removeCallbacks(sigye)
        main.postDelayed(sigye, 5000)
    }

    override fun kkeugi(malHagi: Boolean) {
        main.removeCallbacks(sigye)
        if (!kyeojim) return
        kyeojim = false
        sedae += 1
        sangtae = ""
        val h = glH
        val th = gl
        glH = null
        gl = null
        h?.post {
            try { session?.pause() } catch (e: Exception) {}
            try { session?.close() } catch (e: Exception) {}
            session = null
            glChiugi()
            th?.quitSafely()
        }
        Kamera.noaJugi(this)
        if (malHagi) Sori.mal("발 앞 계단·턱 알림을 멈췄습니다.", MalGeup.JEONGBO)
        byeonhwa?.invoke()
    }

    fun jigeumMal() {
        Sori.mal(if (kyeojim) (if (sangtae.isEmpty()) "발 앞을 살피는 중입니다." else sangtae) else "발 앞 계단·턱 알림이 꺼져 있습니다.")
    }

    /** 바닥이 15초 넘게 안 보이면 숙이기를 청하고, 20분 동안 쓰지 않으면 끔 */
    private fun salpigi() {
        if (!kyeojim) return
        val now = System.currentTimeMillis()
        if (now - saeT > 1200000) {
            kkeugi(false)
            Sori.mal("20분이 지나 발 앞 계단·턱 알림을 껐습니다.")
            return
        }
        if (now - gyeolT > 15000) {
            gyeolT = now
            Sori.mal("앞쪽 바닥이 잘 보이지 않습니다. 폰을 조금 더 아래로 숙여 주십시오.", MalGeup.JEONGBO)
        }
    }

    // MARK: GL 줄 — 구글 AR 은 그림판(텍스처) 하나가 있어야 장을 내어 줌(화면에는 그리지 않음)

    private fun glJunbi(ctx: Context): String? {
        try {
            val d = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val ver = IntArray(2)
            if (d == EGL14.EGL_NO_DISPLAY || !EGL14.eglInitialize(d, ver, 0, ver, 1)) return "카메라를 열지 못했습니다."
            eglD = d
            val attr = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_NONE
            )
            val cfgs = arrayOfNulls<EGLConfig>(1)
            val n = IntArray(1)
            if (!EGL14.eglChooseConfig(d, attr, 0, cfgs, 0, 1, n, 0) || n[0] < 1) { glChiugi(); return "카메라를 열지 못했습니다." }
            val cfg = cfgs[0]
            val c = EGL14.eglCreateContext(d, cfg, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            if (c == null || c == EGL14.EGL_NO_CONTEXT) { glChiugi(); return "카메라를 열지 못했습니다." }
            eglC = c
            val s = EGL14.eglCreatePbufferSurface(d, cfg, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
            if (s == null || s == EGL14.EGL_NO_SURFACE) { glChiugi(); return "카메라를 열지 못했습니다." }
            eglS = s
            if (!EGL14.eglMakeCurrent(d, s, s, c)) { glChiugi(); return "카메라를 열지 못했습니다." }
            val tx = IntArray(1)
            GLES20.glGenTextures(1, tx, 0)
            tex = tx[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

            val se = Session(ctx)
            if (!se.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                se.close()
                glChiugi()
                return "이 폰은 깊이를 잴 수 없어 발 앞 계단·턱 알림을 쓸 수 없습니다."
            }
            val cf = Config(se)
            cf.setDepthMode(Config.DepthMode.AUTOMATIC)
            cf.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE)
            cf.setFocusMode(Config.FocusMode.AUTO)
            se.configure(cf)
            se.setCameraTextureName(tex)
            se.resume()
            session = se
            boT = 0L
            apGeot = ""
            gatSu = 0
            return null
        } catch (e: Exception) {
            Girok.namgi("teok_oryu", mapOf("dan" to "session", "e" to (e.javaClass.simpleName + " " + (e.message ?: ""))))
            try { session?.close() } catch (e2: Exception) {}
            session = null
            glChiugi()
            return "카메라를 열지 못해 발 앞 계단·턱 알림을 켜지 못했습니다."
        }
    }

    private fun glChiugi() {
        try {
            val d = eglD
            if (d != null) {
                if (tex != 0) { GLES20.glDeleteTextures(1, intArrayOf(tex), 0); tex = 0 }
                EGL14.eglMakeCurrent(d, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                eglS?.let { EGL14.eglDestroySurface(d, it) }
                eglC?.let { EGL14.eglDestroyContext(d, it) }
                EGL14.eglTerminate(d)
            }
        } catch (e: Exception) {}
        eglD = null
        eglC = null
        eglS = null
    }

    /** 장을 받아 0.2초마다 살핌(GL 줄) */
    private val jangRun = object : Runnable {
        override fun run() {
            val s = session ?: return
            val h = glH ?: return
            try {
                hanJang(s)
            } catch (e: Exception) {
                // 카메라를 잃으면 멈춤
                if (e is com.google.ar.core.exceptions.CameraNotAvailableException || e is com.google.ar.core.exceptions.SessionPausedException) {
                    main.post {
                        if (kyeojim) { kkeugi(false); Sori.mal("카메라를 쓰지 못해 발 앞 계단·턱 알림을 멈췄습니다.") }
                    }
                    return
                }
            }
            h.postDelayed(this, 66)
        }
    }

    private fun hanJang(s: Session) {
        val frame = s.update()
        val now = System.currentTimeMillis()
        if (now - boT < 200) return
        boT = now
        val cam = frame.camera
        if (cam.trackingState != TrackingState.TRACKING) return
        val img = try { frame.acquireDepthImage16Bits() } catch (e: NotYetAvailableException) { return }
        val pum: List<FloatArray>? = try { jeomdeul(cam, img) } finally { img.close() }
        val p = if (pum != null) bunseok(pum) else null
        val geot = if (p != null) "${p.naeryeo}|${(p.nopi * 20).roundToInt()}" else ""
        if (geot == apGeot) gatSu += 1 else { apGeot = geot; gatSu = 1 }
        val hwakjeong = gatSu >= 3   // 세 장(0.6초) 잇달아 같아야
        val badak = p != null || (pum != null && pum.count { it[0] < 2.0f } >= 60)
        val pp = if (hwakjeong) p else null
        val eop = hwakjeong && p == null
        main.post { allida(pp, eop, badak) }
    }

    class Teok(val georiM: Double, val nopi: Double, val naeryeo: Boolean, val keun: Boolean)

    /** 발 앞 폭 70센티미터 띠의 바닥 높이를 10센티미터 칸마다 모아, 끊겨 꺼지거나 솟는 첫 곳을 찾음(아이폰 bunseok 과 같음) */
    private fun bunseok(pum: List<FloatArray>): Teok? {
        val kan = Array(27) { ArrayList<Float>() }
        for (q in pum) {
            val i = ((q[0] - 0.3f) / 0.1f).toInt()
            if (q[0] >= 0.3f && i in 0 until 27) kan[i].add(q[1])
        }
        val nopi: List<Float?> = kan.map { k ->
            if (k.size < 4) null else { val s = k.sorted(); s[s.size / 2] }
        }
        // 이어지는 두 칸(사이 빈칸 50센티미터까지)의 높이가 6센티미터 넘게 끊기면 턱
        var apI = -1
        var apH = 0f
        for (i in 0 until 27) {
            val h = nopi[i] ?: continue
            if (apI >= 0 && i - apI <= 6) {
                val cha = h - apH
                if (abs(cha) >= 0.06f) {
                    // 뒤 칸 하나 더로 다짐(잡티 거르기)
                    var dwi = h
                    for (j in (i + 1) until min(27, i + 4)) { val x = nopi[j]; if (x != null) { dwi = x; break } }
                    if (abs(dwi - apH) >= 0.05f && (dwi - apH) * cha > 0) {
                        val geori = 0.3 + apI * 0.1 + 0.05 + (i - apI) * 0.05
                        val n = abs(cha).toDouble()
                        return Teok(geori, n, cha < 0, n >= 0.4)
                    }
                }
            }
            apI = i
            apH = h
        }
        return null
    }

    /** 깊이 점들을 세상 좌표로 옮겨, 몸 앞 띠 안의 (앞으로 거리, 높이)만 모음 */
    private fun jeomdeul(cam: Camera, img: android.media.Image): List<FloatArray>? {
        val w = img.width
        val h = img.height
        if (w < 8 || h < 8) return null
        val pl = img.planes[0]
        val buf = pl.buffer.order(ByteOrder.LITTLE_ENDIAN)
        val rs = pl.rowStride
        val ps = pl.pixelStride
        val k = cam.imageIntrinsics
        val fl = k.focalLength
        val pp = k.principalPoint
        val dim = k.imageDimensions
        if (dim[0] <= 0 || dim[1] <= 0) return null
        val sx = w.toFloat() / dim[0]
        val sy = h.toFloat() / dim[1]
        val fx = fl[0] * sx
        val fy = fl[1] * sy
        val cx = pp[0] * sx
        val cy = pp[1] * sy
        val pose = cam.pose
        val kx = pose.tx()
        val kz = pose.tz()
        // 카메라가 보는 쪽(-z)을 수평으로 눕힌 앞 방향과 오른쪽
        val z = pose.zAxis
        var apx = -z[0]
        var apz = -z[2]
        val gil = sqrt(apx * apx + apz * apz)
        if (gil <= 0.2f) return null   // 폰이 너무 바닥만 보거나 하늘만 보면 쉼
        apx /= gil
        apz /= gil
        val orx = -apz
        val orz = apx
        val geon = max(2, w / 48)
        val out = ArrayList<FloatArray>(1500)
        val pt = FloatArray(3)
        val pw = FloatArray(3)
        var v = geon / 2
        while (v < h) {
            var u = geon / 2
            while (u < w) {
                val idx = v * rs + u * ps
                if (idx + 1 < buf.limit()) {
                    val d = (buf.getShort(idx).toInt() and 0xFFFF) / 1000f
                    if (d > 0.2f && d < 4.0f) {
                        val xc = (u - cx) * d / fx
                        val yc = (v - cy) * d / fy
                        pt[0] = xc; pt[1] = -yc; pt[2] = -d
                        pose.transformPoint(pt, 0, pw, 0)
                        val rx = pw[0] - kx
                        val rz = pw[2] - kz
                        val apGeori = rx * apx + rz * apz
                        val yeop = rx * orx + rz * orz
                        if (abs(yeop) < 0.35f && apGeori > 0.3f && apGeori < 3.0f) out.add(floatArrayOf(apGeori, pw[1]))
                    }
                }
                u += geon
            }
            v += geon
        }
        return out
    }

    // MARK: 알리기(화면 줄) — 안전 경고라 말소리를 꺼도 알림

    private fun allida(t: Teok?, eopseum: Boolean, badak: Boolean) {
        if (!kyeojim) return
        val now = System.currentTimeMillis()
        if (badak) gyeolT = now
        if (t == null) {
            if (eopseum && malGeot.isNotEmpty()) { malGeot = ""; malGeoreum = 99; sangtae = "발 앞이 고릅니다." }
            return
        }
        saeT = now
        val georeum = max(1, (t.georiM / KameraNun.bopok).roundToInt())
        val cm = (t.nopi * 100 / 5).roundToInt() * 5
        val jong = if (t.keun) (if (t.naeryeo) "크게 꺼진 곳" else "앞을 막는 높은 것")
                   else (if (t.naeryeo) "내려가는 턱" else "올라가는 턱")
        val ap = if (t.georiM < 0.55) "바로 발 앞" else "${KameraNun.georeumSu(georeum)} 걸음 앞"
        val m = if (t.keun) "$ap, $jong" else "$ap, $jong, 약 ${cm}센티미터"
        val geot = "$jong|$cm"
        sangtae = "$m."
        // 새 것이거나, 같은 것이라도 한 걸음 넘게 가까워졌고 2초 지났을 때만
        val sae = geot != malGeot
        val gakka = georeum < malGeoreum && now - malT >= 2000
        if (!sae && !gakka) return
        if (!sae && now - malT < 2000) return
        malGeot = geot; malGeoreum = georeum; malT = now
        Eum.naegi(EumJong.GYEONGGO)
        Sori.mal("$m.", MalGeup.GYEONGGO)
        Girok.namgi("teok", mapOf("jong" to jong, "cm" to cm, "georeum" to georeum))
    }
}

/** 발 앞 계단·턱 알림 화면 — 관리자 시험(설정 탭 더 보기의 관리자 시험) */
class TeokAllimHwamyeon : KameraHwamyeon("발 앞 계단·턱 알림") {
    override val bupum: KameraNunBupum get() = TeokAllim
    override fun kyeogi(t: GilnunActivity) = TeokAllim.kyeogi(t)

    override fun juljul(t: GilnunActivity): View {
        val g = TeokAllim
        val kb = t.danchu(kGeul()) { if (g.kyeojim) g.kkeugi(true) else g.kyeogi(t) }
        g.byeonhwa = {
            if (t.wiHwamyeon === this) {
                val n = kGeul()
                if (kb.text.toString() != n) kb.text = n
            }
        }
        t.danchu("지금 발 앞 다시 듣기") { g.jigeumMal() }
        alaDuSil(t, "관리자 시험 중인 기능입니다. 구글 AR 깊이 재기를 지원하는 폰에서만 되며, 구글 플레이 AR 서비스가 없으면 설치 화면을 엽니다. 화면을 여시면 바로 시작합니다. 폰을 가슴 앞에 들고 카메라가 앞쪽 바닥을 보도록 조금 숙여 걸으시면, 발 앞 바닥이 갑자기 꺼지거나 솟는 곳을 두 걸음 앞, 내려가는 턱, 약 15센티미터처럼 경고음과 함께 알려 드립니다. 40센티미터가 넘으면 크게 꺼진 곳, 앞을 막는 높은 것으로 알려 드립니다. 같은 턱은 한 걸음 넘게 가까워질 때만 다시 알립니다. 안전 경고라 말소리를 꺼도 늘 알립니다. 계단이 몇 단인지는 세지 않습니다. 앞쪽 바닥이 15초 넘게 안 보이면 폰을 더 숙여 달라고 말씀드립니다. 20분이 지나거나 화면을 떠나시면 끕니다. 깊이 재기는 폰이 조금 움직여야 자리를 잡으므로 처음 몇 초는 조용할 수 있습니다. 지팡이를 대신하지 않으며 지팡이와 함께 쓰십시오. 폰 안에서만 살피며 사진이나 깊이 자료를 담지도 보내지도 않습니다.")
        return kb
    }

    private fun kGeul() = if (TeokAllim.kyeojim) "멈추기 — 발 앞을 살피는 중" else "이어 살피기 — 발 앞 계단·턱 알림 켜기"
}
