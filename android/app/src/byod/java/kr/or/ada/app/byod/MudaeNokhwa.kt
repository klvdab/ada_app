// LVD 송출 — 무대 영상 녹화 (1.9.0판, 빌드 261024-B17, 이사장님 지시 「BYOD 방송도 교육 녹화·인터넷 방송과 똑같이 영상으로 남길 것」)
// 1.9.0 (261024-B17) 무대 영상 녹화
// 첫 화면에서 켜 두면, BYOD 방송을 시작할 때 이 기기 뒤 카메라로 무대를 함께 녹화합니다.
// 방송 일꾼(ByodService, 앞에서 도는 일꾼) 안에서 돌아 화면을 잠가도 이어집니다.
//   1. 영상만 담습니다(소리 장치는 방송이 씀). 소리는 나스에서 행사 녹음을 영상 밑에 붙입니다.
//   2. 1분 조각(v0001.mp4 …). 조각 하나가 끝나면 그 조각은 바로 재생되는 mp4 — 전기가 끊겨도 1분 안쪽만 잃음
//   3. yeongsang.json 에 조각마다 시작·끝 시각(1970년부터 밀리초)을 적어 나스가 행사 녹음과 맞춤(아이폰 LVD 송출과 같은 꼴)
//   4. 카메라가 안 되어도 방송은 그대로 나감. 문제는 munje 한 줄로 두고, 화면은 바뀔 때 한 번만 알림
//   5. 담는 곳: filesDir/byod_yeongsang/y년월일시분초(이 기기 시각, 한국 시간)
//      행사(sid)와의 짝: 아이피타임 방식은 녹음 행사 sid 를 처음부터 앎, 플린트 2 방식은 공유기 /status 의 haengsa 를 받아 적음,
//      둘 다 없으면 올릴 때 시각이 겹치는 행사와 짝지음(Haengsa.yeongsangOlligi)
// 카메라·기록기는 모두 따로 둔 실(byod-yeongsang) 위에서만 만지고 닫습니다.
package kr.or.ada.app.byod

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.display.DisplayManager
import android.media.MediaCodec
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.util.Size
import android.view.Display
import android.view.Surface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SuppressLint("MissingPermission")
object MudaeNokhwa {
    const val KIY = "yeongsang_nokhwa"          // "byod" 설정의 켜짐·꺼짐
    private const val JOGAK_MS = 60_000L        // 조각 하나 1분
    private const val PYOK = 1280
    private const val NOPI = 720
    private const val FPS = 30
    private const val BITRATE = 2_000_000       // 약 2메가비트(1분에 약 15메가바이트)
    private const val GONGGAN_SIJAK = 500L * 1024 * 1024     // 녹화를 시작하려면 남아 있어야 할 저장 공간
    private const val GONGGAN_MEOMCHUM = 200L * 1024 * 1024  // 이보다 적어지면 녹화를 멈춤(방송은 그대로)

    private fun pref(ctx: Context) = ctx.getSharedPreferences("byod", Context.MODE_PRIVATE)
    fun kyeojim(ctx: Context): Boolean = pref(ctx).getBoolean(KIY, false)
    fun kyeojimDugi(ctx: Context, on: Boolean) { pref(ctx).edit().putBoolean(KIY, on).apply() }
    fun heorak(ctx: Context): Boolean = Build.VERSION.SDK_INT < 23 ||
        ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    fun teo(ctx: Context): File = File(ctx.filesDir, "byod_yeongsang").also { it.mkdirs() }

    @Volatile var nokhwaJung = false            // 지금 카메라가 녹화하고 있는가
    @Volatile var munje = ""                    // 녹화의 문제(없으면 빈 글) — 화면이 바뀔 때 한 번 알림
    @Volatile var sijakTtae = 0L                // 이번 녹화를 처음 시작한 때
    @Volatile var jigeumYid = ""                // 지금 녹화 중인 폴더 이름(없으면 빈 글)
    @Volatile var ilkkunMotham = false          // 방송 일꾼에 카메라 종류를 붙이지 못함(ByodService 가 적음)
    @Volatile private var barang = false        // 방송 일꾼이 녹화를 바라는가

    private var il: HandlerThread? = null
    private var hd: Handler? = null
    private var app: Context? = null
    private var bangsik = ""
    private var sidAl = ""

    // 아래는 모두 byod-yeongsang 실에서만 만짐
    private var kamera: CameraDevice? = null
    private var sesyeon: CameraCaptureSession? = null
    private var rec: MediaRecorder? = null
    private var pyomyeon: Surface? = null       // 기록기 앞 판(오래 쓰는 판 — 기록기를 바꿔도 그대로)
    private var teoJigeum: File? = null
    private var g: JSONObject? = null
    private var jigeumPail: File? = null        // 기록기가 지금 쓰거나 곧 쓸 조각
    private var daeumPail: File? = null         // 1분이 다 되어 갈 때 미리 넘겨 준 다음 조각(안드로이드 8부터)
    private var jogakSijakTtae = 0L
    private var kiPyok = PYOK
    private var kiNopi = NOPI
    private var hoejeon = 0
    private var fpsBeomwi: Range<Int>? = null
    private var afYeonsok = false
    private var sedae = 0                       // 다시 켤 때마다 늘어, 지난 카메라의 늦은 알림을 버림

    private fun kst(ms: Long): Calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply { timeInMillis = ms }

    /** 방송을 켤 때 방송 일꾼이 부름(화면 줄). 꺼 두셨으면 아무것도 하지 않음 */
    fun sijak(ctx: Context, bangsik0: String, sid0: String) {
        if (!kyeojim(ctx)) return
        var h: Handler? = null
        synchronized(this) {
            if (!barang) {
                barang = true
                app = ctx.applicationContext; bangsik = bangsik0; sidAl = sid0
                munje = ""; nokhwaJung = false; sijakTtae = 0L
                val t = HandlerThread("byod-yeongsang").also { it.start() }
                il = t; hd = Handler(t.looper); h = hd
            }
        }
        val hh = h ?: return
        if (ilkkunMotham) {
            munjeDugi("이 기기가 화면을 잠갔을 때 카메라를 쓰도록 허락하지 않아 녹화하지 못합니다. 방송은 그대로 나갑니다. 방송을 멈춘 뒤 다시 시작해 보십시오.")
            return
        }
        hh.post { kyeogi() }
    }

    /** 방송을 멈출 때 — 마지막 조각을 마무리할 때까지 5초까지 기다림 */
    fun kkeugi() {
        var h: Handler? = null
        var t: HandlerThread? = null
        synchronized(this) {
            if (barang) { barang = false; h = hd; t = il; hd = null; il = null }
        }
        val hh = h ?: return
        hh.removeCallbacksAndMessages(null)
        val l = CountDownLatch(1)
        hh.post { try { jeongri(true) } catch (_: Exception) { } finally { l.countDown() } }
        try { l.await(5, TimeUnit.SECONDS) } catch (_: InterruptedException) { }
        try { t?.quitSafely() } catch (_: Exception) { }
        munje = ""
        Bang.allyeo()
    }

    /** 첫 화면·현장 점검 한 줄(빈 글이면 보이지 않음) */
    fun jul(ctx: Context): String {
        if (munje.isNotEmpty()) return "무대 영상: " + munje
        if (nokhwaJung && sijakTtae > 0L) return "무대 영상: 녹화 중, 지금까지 " + ((System.currentTimeMillis() - sijakTtae) / 60_000L) + "분"
        if (barang) return "무대 영상: 카메라를 켜고 있습니다."
        return ""
    }

    private fun munjeDugi(t: String) {
        if (munje == t) return
        munje = t
        Bang.allyeo()
    }

    // ───── 카메라 켜기(byod-yeongsang 실) ─────
    private fun kyeogi() {
        if (!barang) return
        if (rec != null || kamera != null || sesyeon != null) return
        val ctx = app ?: return
        val h = hd ?: return
        if (!heorak(ctx)) {
            munjeDugi("카메라 허락이 없어 녹화하지 못합니다. 방송은 그대로 나갑니다. 폰 설정의 애플리케이션, LVD 송출, 권한에서 카메라를 허용해 주십시오.")
            return
        }
        if (teo(ctx).usableSpace < GONGGAN_SIJAK) {
            munjeDugi("저장 공간이 모자라 녹화하지 못합니다. 방송은 그대로 나갑니다. 이 기기에 500메가바이트 넘게 빈자리를 만들어 주십시오.")
            dasiSido()
            return
        }
        try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = dwiKamera(cm)
            if (id == null) {
                munjeDugi("이 기기에서 뒤 카메라를 찾지 못했습니다. 방송은 그대로 나갑니다.")
                return
            }
            val ch = cm.getCameraCharacteristics(id)
            val map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val s = keugiGoreugi(map?.getOutputSizes(MediaRecorder::class.java))
            kiPyok = s.width; kiNopi = s.height
            fpsBeomwi = fpsGoreugi(ch.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES))
            hoejeon = hoejeonGusagi(ctx, ch.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90)
            afYeonsok = (ch.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: IntArray(0)).contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            if (teoJigeum == null || g == null) teoYeolgi()
            val d = teoJigeum ?: throw IllegalStateException("teo")
            val pm = pyomyeon ?: MediaCodec.createPersistentInputSurface().also { pyomyeon = it }
            val f = File(d, pailIreum(jogakSu() + 1))
            jigeumPail = f
            rec = recMandeul(ctx, pm, f)
            val nae = ++sedae
            cm.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(c: CameraDevice) {
                    if (nae != sedae || !barang) { try { c.close() } catch (_: Exception) { }; return }
                    kamera = c
                    sesyeonMandeul(c, nae)
                }
                override fun onDisconnected(c: CameraDevice) {
                    try { c.close() } catch (_: Exception) { }
                    if (nae != sedae) return
                    kamera = null
                    teullim("다른 앱이 카메라를 가져가 녹화가 멈췄습니다")
                }
                override fun onError(c: CameraDevice, error: Int) {
                    try { c.close() } catch (_: Exception) { }
                    if (nae != sedae) return
                    kamera = null
                    teullim(if (error == CameraDevice.StateCallback.ERROR_CAMERA_IN_USE || error == CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE)
                        "카메라를 다른 앱이 쓰고 있어 녹화하지 못합니다" else "카메라에 탈이 나 녹화가 멈췄습니다")
                }
            }, h)
        } catch (e: SecurityException) {
            jeongri(false)
            munjeDugi("카메라 허락이 없어 녹화하지 못합니다. 방송은 그대로 나갑니다. 폰 설정의 애플리케이션, LVD 송출, 권한에서 카메라를 허용해 주십시오.")
        } catch (e: Exception) {
            teullim("카메라를 켜지 못했습니다")
        }
    }

    @Suppress("DEPRECATION")
    private fun sesyeonMandeul(c: CameraDevice, nae: Int) {
        val pm = pyomyeon ?: return
        val h = hd ?: return
        try {
            c.createCaptureSession(listOf(pm), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    if (nae != sedae || !barang) { try { s.close() } catch (_: Exception) { }; return }
                    sesyeon = s
                    try {
                        val q = c.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                        q.addTarget(pm)
                        q.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        if (afYeonsok) q.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                        val fr = fpsBeomwi
                        if (fr != null) q.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fr)
                        s.setRepeatingRequest(q.build(), null, h)
                        val r = rec ?: throw IllegalStateException("rec")
                        r.start()
                        val now = System.currentTimeMillis()
                        jigeumPail?.let { jogakDeungrok(it, now) }
                        if (sijakTtae == 0L) sijakTtae = now
                        nokhwaJung = true
                        munje = ""
                        Bang.allyeo()
                        h.removeCallbacks(bunIl)
                        h.postDelayed(bunIl, 5_000L)
                    } catch (e: Exception) {
                        teullim("녹화를 시작하지 못했습니다")
                    }
                }
                override fun onConfigureFailed(s: CameraCaptureSession) {
                    if (nae != sedae) return
                    teullim("카메라 화면을 꾸리지 못했습니다")
                }
            }, h)
        } catch (e: Exception) {
            teullim("카메라 화면을 꾸리지 못했습니다")
        }
    }

    /** 영상 기록기 — 영상만(소리 없음), H.264, mp4. 안드로이드 8부터는 1분이 다 되어 가면 다음 조각으로 끊김 없이 넘어감 */
    private fun recMandeul(ctx: Context, pm: Surface, f: File): MediaRecorder {
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            r.setVideoSize(kiPyok, kiNopi)
            r.setVideoFrameRate(FPS)
            r.setVideoEncodingBitRate(BITRATE)
            r.setOrientationHint(hoejeon)
            r.setInputSurface(pm)
            if (f.exists()) f.delete()
            r.setOutputFile(f.path)
            if (Build.VERSION.SDK_INT >= 26) r.setMaxDuration(JOGAK_MS.toInt())
            val nae = sedae + 1   // 곧 열 카메라의 세대(kyeogi 에서 ++sedae)
            r.setOnInfoListener { _, what, _ -> try { jeongbo(nae, what) } catch (_: Exception) { } }
            r.setOnErrorListener { _, _, _ -> if (nae == sedae) teullim("영상 기록기에 탈이 나 녹화가 멈췄습니다") }
            r.prepare()
        } catch (e: Exception) {
            try { r.release() } catch (_: Exception) { }
            throw e
        }
        return r
    }

    /** 기록기 알림(기록기를 만든 실 = byod-yeongsang 실에서 옴) */
    private fun jeongbo(nae: Int, what: Int) {
        if (nae != sedae || !barang) return
        val r = rec ?: return
        val d = teoJigeum ?: return
        if (Build.VERSION.SDK_INT >= 26 && what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_APPROACHING) {
            if (daeumPail != null) return
            val f = File(d, pailIreum(jogakSu() + 1))
            try {
                if (f.exists()) f.delete()
                r.setNextOutputFile(f)
                daeumPail = f
            } catch (_: Exception) { daeumPail = null }   // 못 넘기면 1분에 멈춤 → 아래 MAX_DURATION_REACHED 에서 다시 켬
        } else if (Build.VERSION.SDK_INT >= 26 && what == MediaRecorder.MEDIA_RECORDER_INFO_NEXT_OUTPUT_FILE_STARTED) {
            val now = System.currentTimeMillis()
            jogakMaechim(now, true)          // 앞 조각은 이제 다 된 mp4
            val f = daeumPail
            daeumPail = null
            if (f != null) { jigeumPail = f; jogakDeungrok(f, now) }
        } else if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
            // 다음 조각으로 넘어가지 못하고 1분에 멈춤 — 이 조각을 마무리하고 카메라를 다시 켬
            jeongri(false)
            kyeogi()
        }
    }

    /** 5초마다 — 저장 공간, 조각 넘기기(안드로이드 7), 1분이 넘도록 조각이 안 넘어가면 다시 켜기, 기록 저장 */
    private val bunIl: Runnable = object : Runnable {
        override fun run() {
            if (!barang) return
            val ctx = app ?: return
            val h = hd ?: return
            if (rec == null) return
            if (teo(ctx).usableSpace < GONGGAN_MEOMCHUM) {
                jeongri(false)
                munjeDugi("저장 공간이 모자라 무대 영상 녹화를 멈추었습니다. 방송과 녹음은 그대로 됩니다.")
                return
            }
            val now = System.currentTimeMillis()
            val gil = now - jogakSijakTtae
            if (nokhwaJung && ((Build.VERSION.SDK_INT < 26 && gil >= JOGAK_MS) || gil >= JOGAK_MS + 15_000L)) {
                jeongri(false)
                kyeogi()
                return
            }
            sidChaeugi()
            jeojang()
            h.postDelayed(this, 5_000L)
        }
    }

    private val dasiIl = Runnable { kyeogi() }
    private fun dasiSido() {
        val h = hd ?: return
        h.removeCallbacks(dasiIl)
        h.postDelayed(dasiIl, 30_000L)
    }

    /** 카메라가 안 될 때 — 방송은 그대로, 문제를 한 줄로 두고 30초 뒤 다시 해 봄 */
    private fun teullim(m: String) {
        if (!barang) return
        jeongri(false)
        munjeDugi(m + ". 방송은 그대로 나갑니다. 저절로 다시 해 봅니다.")
        dasiSido()
    }

    /** 카메라·기록기를 닫음. majimak 이면 판과 폴더 기록까지 마감 */
    private fun jeongri(majimak: Boolean) {
        sedae++
        hd?.removeCallbacks(bunIl)
        val r = rec
        rec = null
        val dolatda = nokhwaJung
        nokhwaJung = false
        if (r != null) {
            var ok = false
            try { r.stop(); ok = true } catch (_: Exception) { }
            try { r.release() } catch (_: Exception) { }
            if (dolatda) jogakMaechim(System.currentTimeMillis(), ok)
        }
        try { sesyeon?.close() } catch (_: Exception) { }
        sesyeon = null
        try { kamera?.close() } catch (_: Exception) { }
        kamera = null
        daeumPail?.let { f -> if (!jogakIssum(f.name)) try { f.delete() } catch (_: Exception) { } }
        daeumPail = null
        jigeumPail?.let { f -> if (!jogakIssum(f.name)) try { f.delete() } catch (_: Exception) { } }
        jigeumPail = null
        if (majimak) {
            try { pyomyeon?.release() } catch (_: Exception) { }
            pyomyeon = null
            val gg = g
            if (gg != null) {
                sidChaeugi()
                gg.put("kkeut", System.currentTimeMillis() / 1000.0)
                gg.put("sangtae", "kkeut")
                jeojang()
            }
            g = null; teoJigeum = null; jigeumYid = ""
        }
        Bang.allyeo()
    }

    // ───── 폴더와 yeongsang.json ─────
    private fun pailIreum(n: Int) = String.format("v%04d.mp4", n)

    private fun jogakSu(): Int = g?.optJSONArray("jogak")?.length() ?: 0

    private fun jogakIssum(ireum: String): Boolean {
        val l = g?.optJSONArray("jogak") ?: return false
        for (i in 0 until l.length()) if (l.optJSONObject(i)?.optString("pail") == ireum) return true
        return false
    }

    private fun teoYeolgi() {
        val ctx = app ?: return
        val now = System.currentTimeMillis()
        val c = kst(now)
        val yid = String.format("y%02d%02d%02d%02d%02d%02d", c.get(Calendar.YEAR) % 100, c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND))
        val d = File(teo(ctx), yid).also { it.mkdirs() }
        val p = pref(ctx)
        // 아이폰 LVD 송출과 같은 꼴: 맨 위 sijak·kkeut 는 초(1970년부터, 소수), 조각의 sijak·kkeut 는 밀리초
        g = JSONObject().apply {
            put("yid", yid); put("sid", sidAl); put("bangsik", bangsik)
            put("jemok", (p.getString("haengsa_jemok", "") ?: "").trim()); put("haeseolsa", (p.getString("haeseolsa", "") ?: "").trim())
            put("sijak", now / 1000.0); put("kkeut", 0); put("sangtae", "jinhaeng")
            put("hwajil", "${kiNopi}p"); put("pok", kiPyok); put("nopi", kiNopi); put("fps", FPS); put("bitrate", BITRATE); put("hoejeon", hoejeon)
            put("sori", "eopseum"); put("jogak", JSONArray()); put("flint_sid", JSONArray())
            put("pan", "LVD 송출 " + Bang.PAN)
        }
        teoJigeum = d; jigeumYid = yid
        jeojang()
    }

    private fun jogakDeungrok(f: File, ttae: Long) {
        val gg = g ?: return
        if (jogakIssum(f.name)) return
        val l = gg.getJSONArray("jogak")
        l.put(JSONObject().put("n", l.length() + 1).put("pail", f.name).put("sijak", ttae).put("kkeut", 0))
        jogakSijakTtae = ttae
        sidChaeugi()
        jeojang()
    }

    /** 끝나지 않은 마지막 조각을 마무리 — 잘 끝났거나 내용이 있으면 끝 시각을 적고, 빈 조각이면 지움 */
    private fun jogakMaechim(ttae: Long, ok: Boolean) {
        val gg = g ?: return
        val d = teoJigeum ?: return
        val l = gg.getJSONArray("jogak")
        if (l.length() == 0) return
        val i = l.length() - 1
        val j = l.getJSONObject(i)
        if (j.optLong("kkeut", 0L) > 0L) return
        val f = File(d, j.optString("pail"))
        if (ok || (f.isFile && f.length() > 64 * 1024)) {
            j.put("kkeut", ttae)
        } else {
            try { f.delete() } catch (_: Exception) { }
            l.remove(i)
        }
        jeojang()
    }

    /** 플린트 2 방식 — 공유기가 알려 준 행사 이름을 적어 둠(짝짓기에 씀) */
    private fun sidChaeugi() {
        val gg = g ?: return
        if (bangsik != "flint") return
        val s = Flint.haengsaJigeum
        if (s.isEmpty()) return
        val a = gg.getJSONArray("flint_sid")
        var it2 = false
        for (i in 0 until a.length()) if (a.optString(i) == s) it2 = true
        if (!it2) a.put(s)
        if (gg.optString("sid").isEmpty()) gg.put("sid", s)
    }

    private fun jeojang() {
        val gg = g ?: return
        val d = teoJigeum ?: return
        try {
            val tmp = File(d, "yeongsang.json.tmp")
            FileOutputStream(tmp).use { o -> o.write(gg.toString().toByteArray(Charsets.UTF_8)); o.flush(); o.fd.sync() }
            tmp.renameTo(File(d, "yeongsang.json"))
        } catch (_: Exception) { }
    }

    // ───── 고르기 ─────
    private fun dwiKamera(cm: CameraManager): String? {
        var gita: String? = null
        for (id in cm.cameraIdList) {
            val f = try { cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) } catch (_: Exception) { null }
            if (f == CameraCharacteristics.LENS_FACING_BACK) return id
            if (gita == null) gita = id
        }
        return gita
    }

    private fun keugiGoreugi(l: Array<Size>?): Size {
        if (l == null || l.isEmpty()) return Size(PYOK, NOPI)
        l.firstOrNull { it.width == PYOK && it.height == NOPI }?.let { return it }
        val an = l.filter { it.width <= PYOK && it.height <= NOPI }
        val bi = an.filter { Math.abs(it.width * 9 - it.height * 16) <= it.height }   // 16 대 9 가까운 것
        return bi.maxByOrNull { it.width * it.height } ?: an.maxByOrNull { it.width * it.height } ?: l.minByOrNull { it.width * it.height } ?: l[0]
    }

    private fun fpsGoreugi(l: Array<Range<Int>>?): Range<Int>? {
        if (l == null || l.isEmpty()) return null
        val sam = l.filter { it.upper == 30 }
        return sam.filter { it.lower >= 15 }.minByOrNull { it.lower } ?: sam.firstOrNull() ?: l.filter { it.upper in 15..30 }.maxByOrNull { it.upper }
    }

    /** 뒤 카메라 영상이 바로 서게 — (감지기 방향 - 화면 방향) */
    private fun hoejeonGusagi(ctx: Context, gamji: Int): Int {
        val r = try {
            (ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
        } catch (_: Exception) { Surface.ROTATION_0 }
        val deg = when (r) { Surface.ROTATION_90 -> 90; Surface.ROTATION_180 -> 180; Surface.ROTATION_270 -> 270; else -> 0 }
        return (gamji - deg + 360) % 360
    }

    // ───── 올리기 쪽에서 쓰는 것(Haengsa) ─────
    /** 다 된 조각(끝 시각이 적힌 것) 수 */
    fun dadoenSu(y: JSONObject): Int {
        val l = y.optJSONArray("jogak") ?: return 0
        var su = 0
        for (i in 0 until l.length()) if ((l.optJSONObject(i)?.optLong("kkeut", 0L) ?: 0L) > 0L) su++
        return su
    }

    fun girokIlgi(d: File): JSONObject? = try { JSONObject(File(d, "yeongsang.json").readText(Charsets.UTF_8)) } catch (_: Exception) { null }

    /** 아직 올리지 않은 녹화 폴더(지금 녹화 중인 것은 뺌, 다 된 조각이 있는 것만) */
    fun mokrok(ctx: Context): List<File> = (teo(ctx).listFiles() ?: emptyArray())
        .filter { it.isDirectory && it.name != jigeumYid && !File(it, "olim").isFile && File(it, "yeongsang.json").isFile }
        .filter { d -> girokIlgi(d)?.let { dadoenSu(it) > 0 } ?: false }
        .sortedBy { it.name }

    /** 녹화와 짝이 되는 행사 — 적어 둔 sid 가 있으면 그것, 없으면 시각이 가장 많이 겹치는 행사 */
    fun jjak(y: JSONObject, haengsa: List<Haengsa.Jul>): Haengsa.Jul? {
        val s = y.optString("sid")
        if (s.isNotEmpty()) haengsa.firstOrNull { it.sid == s }?.let { return it }
        val fl = y.optJSONArray("flint_sid")
        if (fl != null) for (i in 0 until fl.length()) haengsa.firstOrNull { it.sid == fl.optString(i) }?.let { return it }
        val ys = (y.optDouble("sijak", 0.0) * 1000).toLong()
        var ye = (y.optDouble("kkeut", 0.0) * 1000).toLong()
        val l = y.optJSONArray("jogak")
        if (ye <= 0L && l != null && l.length() > 0) ye = l.optJSONObject(l.length() - 1)?.optLong("kkeut", 0L) ?: 0L
        if (ye <= ys) ye = ys + 60_000L
        var choe: Haengsa.Jul? = null
        var choeGyeop = 0L
        for (e in haengsa) {
            val es = e.sijak * 1000L
            val ee = es + (e.gilee * 1000).toLong()
            val gyeop = minOf(ye, ee + 60_000L) - maxOf(ys, es - 60_000L)
            if (gyeop > choeGyeop) { choeGyeop = gyeop; choe = e }
        }
        return choe
    }

    /** 이틀이 지나도 짝이 될 행사 녹음이 오지 않은 녹화 — 영상만이라도 올림 */
    fun oraedoem(y: JSONObject): Boolean = System.currentTimeMillis() - (y.optDouble("sijak", 0.0) * 1000).toLong() > 2L * 24 * 3600 * 1000

    /** 지금 올릴 수 있는 녹화가 있는가(짝 행사 녹음이 올라갔거나, 이틀이 지난 것) — 1분마다 다시 올리기에 씀 */
    fun nameum(ctx: Context): Boolean {
        val l = mokrok(ctx)
        if (l.isEmpty()) return false
        val hs = Haengsa.mokrok(ctx)
        return l.any { d ->
            val y = girokIlgi(d) ?: return@any false
            val e = jjak(y, hs)
            (e != null && e.olim) || (e == null && oraedoem(y))
        }
    }
}
