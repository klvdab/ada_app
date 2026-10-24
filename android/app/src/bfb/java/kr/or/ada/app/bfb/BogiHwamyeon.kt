// LVD 방송(듣는 앱) — 영상으로 보기 (2.5.0판, 빌드 261024-L7)
// 지금 나가는 인터넷 방송에 영상이 실려 있으면, 중계가 만들어 두는 HLS(조각 영상 목록, /hls/<sid>/index.m3u8)를
// 안드로이드 기본 VideoView 로 틉니다. 늦게 들어오셔도 영상이 나옵니다(새 라이브러리 없음).
// - 맨 위와 맨 아래에 「뒤로」, 뒤로 가기도 앱 첫 화면으로 — 앱 밖으로 나가지 않습니다.
// - 영상을 보는 동안에는 듣기 일꾼(DeutService)이 소리를 쉬어 소리가 겹치지 않습니다(Deut.bogiJung).
//   나오면 같은 방송을 소리로 이어 들려 드립니다.
// - 전화가 울리거나 통화 중이면 영상을 멈추고, 통화가 끝나면 지금 나가는 자리부터 다시 틉니다.
package kr.or.ada.app.bfb

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView

class BogiHwamyeon : Activity() {

    private val hd = Handler(Looper.getMainLooper())
    private lateinit var bv: VideoView
    private lateinit var sang: TextView
    private lateinit var dasiDanchu: Button
    private var sid = ""
    private var jemok = ""
    private var tonghwa = false      // 통화 때문에 멈춤
    private var teulgo = false       // 틀고 있는가(준비 중 포함)
    private var dasiSu = 0           // 끊겨서 저절로 다시 붙은 수
    private var boyeojunMal = ""

    private fun dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun danchu(geul: String, f: () -> Unit): Button = Button(this).apply {
        text = geul
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#12346E"))
        background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.WHITE); setStroke(dp(3), Color.parseColor("#FFCC00")) }
        minHeight = dp(64)
        setOnClickListener { f() }
    }

    /** 바뀔 때만 한 번 알림 — 같은 말을 되풀이하지 않음 */
    private fun allim(t: String) {
        if (t == boyeojunMal) return
        boyeojunMal = t
        sang.text = t
        sang.announceForAccessibility(t)
    }

    private fun tonghwaJung(): Boolean {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION || am.mode == AudioManager.MODE_RINGTONE
    }

    /** 1초마다 — 전화가 울리거나 통화 중이면 영상을 멈추고, 끝나면 지금 자리부터 다시 틂 */
    private val salpim = object : Runnable {
        override fun run() {
            val jung = tonghwaJung()
            if (jung && !tonghwa) {
                tonghwa = true
                meomchugi()
                allim("전화 중이라 영상을 멈추었습니다. 통화가 끝나면 저절로 다시 틀어 드립니다.")
            } else if (!jung && tonghwa) {
                tonghwa = false
                allim("통화가 끝나 영상을 다시 틀어 드립니다.")
                teulgi()
            }
            hd.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Girok.sijak(applicationContext)
        sid = intent.getStringExtra("sid") ?: ""
        jemok = intent.getStringExtra("jemok") ?: ""
        title = "영상으로 보기"
        window.statusBarColor = Color.parseColor("#12346E")
        window.navigationBarColor = Color.parseColor("#12346E")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)   // 보는 동안 화면이 꺼지지 않게
        volumeControlStream = AudioManager.STREAM_MUSIC

        val mom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#12346E")); setPadding(dp(12), dp(12), dp(12), dp(12)) }
        val wiDwiro = danchu("뒤로") { dwiro() }
        mom.addView(wiDwiro, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val jemokGeul = TextView(this).apply {
            text = "영상으로 보기" + (if (jemok.isEmpty()) "" else " — " + jemok)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(4))
        }
        mom.addView(jemokGeul, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        sang = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(0, dp(4), 0, dp(8))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_NONE   // 바뀔 때 한 번은 allim 이 맡음
        }
        mom.addView(sang, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        // 영상 칸 — 검은 바탕 가운데에 비율을 지켜 놓음
        val teul = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        bv = VideoView(this).apply { contentDescription = "현장영상해설 방송 영상" }
        teul.addView(bv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        mom.addView(teul, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        dasiDanchu = danchu("영상 다시 이어 보기") { dasiSu = 0; teulgi() }
        dasiDanchu.visibility = View.GONE
        mom.addView(dasiDanchu, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        mom.addView(danchu("뒤로") { dwiro() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(mom)

        // 영상이 소리 자리(오디오 포커스)를 따로 쥐지 않게 — 듣기 일꾼이 「다른 소리가 시작되었다」며 아주 멈추지 않도록.
        // 전화는 위 salpim 이 1초마다 살펴 멈춤
        if (Build.VERSION.SDK_INT >= 26) { try { bv.setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE) } catch (_: Exception) { } }
        bv.setOnPreparedListener { mp ->
            dasiSu = 0
            dasiDanchu.visibility = View.GONE
            try { mp.setVolume(1f, 1f) } catch (_: Exception) { }
            bv.start()
            allim("영상으로 이어졌습니다.")
            try { Girok.namgi("bogi", mapOf("mal" to "영상 이어짐", "sid" to sid)) } catch (_: Exception) { }
        }
        bv.setOnErrorListener { _, what, extra ->
            try { Girok.namgi("bogi", mapOf("mal" to "영상 오류", "sid" to sid, "what" to what, "extra" to extra)) } catch (_: Exception) { }
            teulgo = false
            if (tonghwa) return@setOnErrorListener true
            if (dasiSu < 3) {   // 잠깐 끊긴 것이면 저절로 다시 붙음(세 번까지)
                dasiSu++
                allim("영상이 끊겨 다시 잇고 있습니다.")
                hd.postDelayed({ if (!tonghwa && !isFinishing) teulgi() }, 3000)
            } else {
                allim("영상을 잇지 못했습니다. 방송이 끝났거나 연결이 약합니다. 아래 영상 다시 이어 보기를 누르시거나 뒤로 가서 소리로 들으십시오.")
                dasiDanchu.visibility = View.VISIBLE
            }
            true
        }
        bv.setOnCompletionListener {
            // 생방송 목록이 끝났다고 알려 옴 — 방송이 끝났거나 보내는 쪽이 멈춤
            teulgo = false
            allim("영상 방송이 끝났습니다. 뒤로 가시면 첫 화면으로 돌아갑니다.")
            dasiDanchu.visibility = View.VISIBLE
        }

        Deut.bogiJung = true   // 듣기 일꾼은 소리를 쉼
        if (sid.isEmpty()) { allim("볼 방송을 찾지 못했습니다. 뒤로 가서 다시 골라 주십시오."); return }
        allim("영상을 불러오고 있습니다.")
        if (!tonghwaJung()) teulgi() else { tonghwa = true; allim("전화 중이라 통화가 끝나면 영상을 틀어 드립니다.") }
        hd.postDelayed(salpim, 1000)
        hd.postDelayed({ wiDwiro.requestFocus() }, 250)
    }

    /** 지금 나가는 자리부터 틂 — 생방송이라 처음부터 목록을 다시 받음 */
    private fun teulgi() {
        if (sid.isEmpty()) return
        try {
            bv.stopPlayback()
            bv.setVideoURI(Uri.parse(Deut.JUNGYE + "/hls/" + Uri.encode(sid) + "/index.m3u8"))
            teulgo = true
        } catch (_: Exception) {
            teulgo = false
            allim("영상을 틀지 못했습니다. 아래 영상 다시 이어 보기를 눌러 주십시오.")
            dasiDanchu.visibility = View.VISIBLE
        }
    }

    private fun meomchugi() {
        try { bv.stopPlayback() } catch (_: Exception) { }
        teulgo = false
    }

    /** 뒤로 — 늘 앱 첫 화면으로(앱 밖으로 나가지 않음). 같은 방송을 소리로 이어 들려 드림 */
    private fun dwiro() {
        meomchugi()
        Deut.bogiJung = false
        if (sid.isNotEmpty() && Deut.kyeojim) Deut.goreun = sid
        startActivity(Intent(this, BangsongActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    @Deprecated("옛 뒤로 가기 받기")
    override fun onBackPressed() { dwiro() }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { dwiro(); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        hd.removeCallbacksAndMessages(null)
        meomchugi()
        Deut.bogiJung = false
        super.onDestroy()
    }
}
