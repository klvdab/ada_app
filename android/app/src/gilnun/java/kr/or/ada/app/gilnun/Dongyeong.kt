// 안드로이드 길눈 — 동영상 받아 틀기(b5_bangsong, 아이폰 Dongyeong.swift 2.34.0과 같은 뜻, 같은 말)
// ★대표님: 유튜브를 비롯해 동영상이 수시로 오는데 워치에는 동영상 재생 기능이 없어 불편하다. 길눈에서 틀어 달라.
//   ① 카톡·문자·갤러리에서 받은 동영상 파일을 "공유 → 길눈"으로 넘기면 길눈이 받아 틂(DongyeongActivity — 온 화면 재생기)
//      워치로 보내는 길(watchBonae)이 채워져 있고 「워치에서 틀기」를 켜 두셨으면 워치로 보냄
//      — 지금 갤럭시 워치 길눈(:wear)에는 동영상 재생 화면이 없어 이 길은 비어 있음(통합 쪽지 b5_bangsong.md)
//   ② 동영상 주소는 복사한 뒤 음악·방송 탭의 "동영상 틀기"에서 "복사한 동영상 주소 틀기"로 틂
//   ③ 유튜브 주소는 약관상 길눈이 빼내 틀지 않고 유튜브 앱으로 열어 줌
//   길눈을 고르지 않으시면 폰이 원래대로 처리합니다.
// 재생기는 방송 엔진과 따로(아이폰도 따로) — 폰을 잠가도 소리는 이어지고, 홈으로 나가시면 멈춤.
package kr.or.ada.app.gilnun

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Patterns
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.MimeTypeMap
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

object Dongyeong {
    /** 워치로 보내기 — (주소, 파일인가) → 보냈으면 참. 갤럭시 워치 길눈에 동영상 재생이 생기면 WatchLink 가 채움(통합 쪽지) */
    var watchBonae: ((Uri, Boolean) -> Boolean)? = null

    private val il = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** 마지막으로 받은 것 — 다시 틀기 */
    val majimak: String get() = BangsongSeol.dongyeongMajimak

    fun bogwanHam(c: Context): File {
        val d = File(c.filesDir, "dongyeong")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun yutyubeuInga(u: Uri): Boolean {
        val h = (u.host ?: "").lowercase(Locale.ROOT)
        return h.endsWith("youtube.com") || h == "youtu.be" || h.endsWith("youtube-nocookie.com")
    }

    /** 다른 앱이 "공유 → 길눈"으로 넘긴 파일을 길눈 보관함으로 옮김(앞서 받은 것은 하나만 남김) — 결과는 화면 줄에서, 못 받으면 null */
    fun batda(c: Context, u: Uri, mime: String?, kkeut: (File?) -> Unit) {
        val ac = c.applicationContext
        il.execute {
            var mok: File? = null
            try {
                val d = bogwanHam(ac)
                d.listFiles()?.forEach { it.delete() }
                var ext = if (mime != null) MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) else null
                if (ext.isNullOrEmpty()) ext = (u.lastPathSegment ?: "").substringAfterLast('.', "").lowercase(Locale.ROOT).takeIf { it.length in 2..5 }
                if (ext.isNullOrEmpty()) ext = "mp4"
                val f = File(d, "badeun_" + (System.currentTimeMillis() / 1000) + "." + ext)
                val inp = ac.contentResolver.openInputStream(u)
                if (inp != null) {
                    inp.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                    if (f.length() > 0) mok = f else f.delete()
                }
            } catch (e: Exception) {
                Girok.namgi("dongyeong_oryu", mapOf("e" to (e.message ?: "")))
            }
            val r = mok
            main.post {
                if (r == null) {
                    Sori.mal("동영상을 받지 못했습니다. 한 번 더 길눈으로 보내 주십시오.")
                } else {
                    Girok.namgi("dongyeong_batda", mapOf("kind" to "file", "ext" to r.extension))
                }
                kkeut(r)
            }
        }
    }

    /** 복사해 둔 동영상 주소 틀기 */
    fun boksaJusoTeulgi(c: Context) {
        var url: String? = null
        try {
            val cm = c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val it0 = clip.getItemAt(0)
                val s = it0.uri?.toString() ?: it0.coerceToText(c)?.toString() ?: ""
                // 글 속에 주소가 섞여 있어도 첫 주소를 찾음
                val m = Patterns.WEB_URL.matcher(s)
                while (m.find()) {
                    val x = m.group()
                    if (x.startsWith("http://", true) || x.startsWith("https://", true)) { url = x; break }
                }
            }
        } catch (e: Exception) {}
        val u = url
        if (u == null) {
            Sori.mal("복사해 둔 동영상 주소가 없습니다. 카톡이나 문자에서 동영상 주소를 길게 눌러 복사한 뒤 다시 눌러 주십시오.")
            return
        }
        Girok.namgi("dongyeong_batda", mapOf("kind" to "juso"))
        teulgi(c, u)
    }

    fun dasiTeulgi(c: Context) {
        val m = majimak
        if (m.isEmpty() || (!m.startsWith("http") && !File(m).exists())) { Sori.mal("아직 받은 동영상이 없습니다."); return }
        teulgi(c, m)
    }

    /** 어디서 틀지 정해 틂 — 파일이면 그 길, 아니면 주소. 폰에서 틀면 참(부른 화면이 재생기를 열거나 이미 연 재생기가 틂) */
    fun eodiseo(c: Context, juso: String): Boolean {
        BangsongSeol.dongyeongMajimak = juso
        val pail = !juso.startsWith("http")
        val u = if (pail) Uri.fromFile(File(juso)) else Uri.parse(juso)
        if (!pail && yutyubeuInga(u)) {
            // 유튜브 앱이 있으면 그 앱으로, 없으면 인터넷 앱으로 — 폰이 알아서 고름
            try {
                c.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Sori.mal("유튜브 앱에서 엽니다. 알림 칸이나 갤럭시 워치의 미디어 조절에서 멈춤과 소리 크기를 조절하실 수 있습니다.")
            } catch (e: Exception) {
                Sori.mal("유튜브를 열 앱이 없습니다.")
            }
            Girok.namgi("dongyeong_teulgi", mapOf("eodi" to "youtube"))
            return false
        }
        val w = watchBonae
        if (w != null && BangsongSeol.dongyeongWatch && w(u, pail)) {
            Sori.mal(if (pail) "동영상을 워치로 보냅니다. 받는 대로 워치에서 틀어 드립니다. 큰 동영상은 몇십 초 걸릴 수 있습니다."
                else "동영상 주소를 워치로 보냅니다. 워치에서 곧 틀어 드립니다.")
            Girok.namgi("dongyeong_teulgi", mapOf("eodi" to "watch", "file" to pail))
            return false
        }
        Girok.namgi("dongyeong_teulgi", mapOf("eodi" to "pon", "file" to pail))
        return true
    }

    /** 2.27.0 워치로 보내지 못했을 때 — 워치로 가지 않고 곧장 폰에서 */
    fun ponEseoTeulgi(c: Context, juso: String) {
        if (juso.isEmpty()) return
        try {
            c.startActivity(Intent(c, DongyeongActivity::class.java).putExtra(DongyeongActivity.JUSO, juso).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Girok.namgi("dongyeong_oryu", mapOf("e" to (e.message ?: "")))
        }
    }

    /** 틀기 — 폰에서 틀 것이면 온 화면 재생기를 엶 */
    fun teulgi(c: Context, juso: String) {
        if (!eodiseo(c, juso)) return
        try {
            val i = Intent(c, DongyeongActivity::class.java).putExtra(DongyeongActivity.JUSO, juso)
            if (c !is android.app.Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(i)
        } catch (e: Exception) {
            Girok.namgi("dongyeong_oryu", mapOf("e" to (e.message ?: "")))
        }
    }
}

/** 폰에서 틀기 — 온 화면 재생기. "공유 → 길눈"으로 받는 문이기도 함(동영상 파일) */
@OptIn(UnstableApi::class)
class DongyeongActivity : AppCompatActivity() {
    companion object { const val JUSO = "dongyeong_juso" }

    private var player: ExoPlayer? = null
    private lateinit var pv: PlayerView
    private lateinit var jaesaengB: Button
    private lateinit var sangtaeG: TextView
    private lateinit var dwiroB: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 길눈 화면 없이 곧바로 열릴 수 있으므로 몸통을 세움(두 번 불러도 됨)
        Seoljeong.sijak(this)
        Sori.sijak(this)
        BangsongSeol.sijak(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val bburi = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        dwiroB = keunDanchu("뒤로 — 동영상 닫기") { player?.pause(); finish() }
        bburi.addView(dwiroB, jul())
        sangtaeG = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            visibility = View.GONE
        }
        bburi.addView(sangtaeG, jul())
        pv = PlayerView(this).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(Color.BLACK)
            contentDescription = "동영상 화면"
            isFocusable = true
        }
        bburi.addView(pv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(12) })
        jaesaengB = keunDanchu("재생") { jaesaengMeomchum() }
        bburi.addView(jaesaengB, jul())
        val jul2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val dwi10 = keunDanchu("10초 뒤로") { olgigi(-10000) }
        val ap10 = keunDanchu("10초 앞으로") { olgigi(10000) }
        jul2.addView(dwi10, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
        jul2.addView(ap10, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6) })
        bburi.addView(jul2, jul())
        setContentView(bburi)
        title = "동영상"

        batgi(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        batgi(intent)
    }

    private fun batgi(i: Intent?) {
        if (i == null) { finish(); return }
        val juso = i.getStringExtra(JUSO)
        if (!juso.isNullOrEmpty()) { teulgi(juso); return }
        if (i.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            val u: Uri? = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableExtra(Intent.EXTRA_STREAM)
            if (u != null) {
                sangtae("동영상을 받는 중입니다.")
                Dongyeong.batda(this, u, i.type) { f ->
                    if (f == null) { finish(); return@batda }
                    if (Dongyeong.eodiseo(this, f.absolutePath)) teulgi(f.absolutePath) else finish()
                }
                return
            }
            // 글로 온 주소(동영상 주소를 공유하셨을 때)
            val t = i.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            val m = Patterns.WEB_URL.matcher(t)
            var url: String? = null
            while (m.find()) { val x = m.group(); if (x.startsWith("http", true)) { url = x; break } }
            val uu = url
            if (uu != null) {
                Girok.namgi("dongyeong_batda", mapOf("kind" to "juso"))
                if (Dongyeong.eodiseo(this, uu)) teulgi(uu) else finish()
                return
            }
            Sori.mal("받은 것에 동영상이 없습니다.")
            finish()
            return
        }
        if (i.action == Intent.ACTION_VIEW && i.data != null) {
            val u = i.data!!
            if (u.scheme == "http" || u.scheme == "https") {
                if (Dongyeong.eodiseo(this, u.toString())) teulgi(u.toString()) else finish()
            } else {
                sangtae("동영상을 받는 중입니다.")
                Dongyeong.batda(this, u, i.type) { f ->
                    if (f == null) { finish(); return@batda }
                    if (Dongyeong.eodiseo(this, f.absolutePath)) teulgi(f.absolutePath) else finish()
                }
            }
            return
        }
        finish()
    }

    private fun teulgi(juso: String) {
        sangtae("")
        val p = player ?: ExoPlayer.Builder(this)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build().also { np ->
                player = np
                pv.player = np
                np.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) { jaesaengGeul() }
                    override fun onPlayerError(error: PlaybackException) {
                        Girok.namgi("dongyeong_oryu", mapOf("code" to error.errorCode))
                        Sori.mal("동영상을 틀지 못했습니다. 주소나 파일을 확인해 주십시오.")
                        jaesaengGeul()
                    }
                })
            }
        val uri = if (juso.startsWith("http")) Uri.parse(juso) else Uri.fromFile(File(juso))
        val b = MediaItem.Builder().setUri(uri)
        if (juso.lowercase(Locale.ROOT).contains("m3u8")) b.setMimeType(MimeTypes.APPLICATION_M3U8)
        p.setMediaItem(b.build())
        p.prepare()
        p.playWhenReady = true
        jaesaengGeul()
        dwiroB.post {
            dwiroB.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
        }
    }

    private fun sangtae(t: String) {
        sangtaeG.text = t
        sangtaeG.visibility = if (t.isEmpty()) View.GONE else View.VISIBLE
        if (t.isNotEmpty()) sangtaeG.announceForAccessibility(t)
    }

    private fun jaesaengGeul() {
        val p = player
        val n = p != null && p.playWhenReady
        val g = if (n) "멈춤" else "재생"
        if (jaesaengB.text.toString() != g) jaesaengB.text = g
    }

    private fun jaesaengMeomchum() {
        val p = player ?: return
        if (p.playWhenReady) p.pause() else {
            if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
            if (p.playbackState == Player.STATE_IDLE) p.prepare()
            p.play()
        }
        jaesaengGeul()
    }

    private fun olgigi(ms: Long) {
        val p = player ?: return
        val d = p.duration
        var t = maxOf(0L, p.currentPosition + ms)
        if (d > 0) t = minOf(t, d)
        p.seekTo(t)
    }

    override fun onStop() {
        super.onStop()
        // 폰을 잠그신 것이면 소리는 이어 가고, 홈이나 다른 앱으로 가신 것이면 멈춤
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isInteractive && !isChangingConfigurations) player?.pause()
    }

    override fun onDestroy() {
        pv.player = null
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun jul() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }

    private fun keunDanchu(t: String, f: () -> Unit): Button = Button(this).apply {
        text = t
        isAllCaps = false
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setBackgroundColor(GilnunActivity.NAM)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        minHeight = dp(64)
        setOnClickListener { f() }
    }
}

/** 음악·방송 탭의 "동영상 틀기" 화면 */
class DongyeongHwamyeon : BangsongBada("동영상 틀기") {
    override fun moyang() = Dongyeong.majimak
    override fun geurigi(t: GilnunActivity) {
        danchu(t, "boksa", "복사한 동영상 주소 틀기") { Dongyeong.boksaJusoTeulgi(t) }
        if (Dongyeong.majimak.isNotEmpty()) {
            danchu(t, "dasi", "받은 동영상 다시 틀기") { Dongyeong.dasiTeulgi(t) }
        }
        if (Dongyeong.watchBonae != null) {
            danchu(t, "watch", "갤럭시 워치가 있으면 워치에서 틀기 — 지금 " + (if (BangsongSeol.dongyeongWatch) "켜짐, 누르면 꺼짐" else "꺼짐, 누르면 켜짐")) {
                BangsongSeol.dongyeongWatch = !BangsongSeol.dongyeongWatch
                t.dasiGeurigi()
            }
        }
        t.geul("카톡이나 문자, 갤러리에서 받은 동영상은 공유를 누른 뒤 길눈을 고르시면 길눈이 받아 틉니다. 유튜브는 유튜브 앱에서 열어 드립니다.")
    }
}
