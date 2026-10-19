// LVD 방송(듣는 앱) — 인터넷 생방송·지난 방송 다시 듣기·TV해설방송·TV·라디오 화면 (2.3.0판, 빌드 261019-L4, 방클, 이사장님 결정 2026-10-19)
// 2.3.0: ① 다시 듣기의 내려받기(소리 mp3·받아쓰기 글)를 앱 안에서 받음 — 폰의 「다운로드」 폴더에 저장, 와이파이가 아니면 「데이터로 ○○메가를 받습니다. 받을까요?」를 먼저 물음
//        ② 화면을 잠가도 다시 듣기가 이어짐(누리집 화면을 멈추지 않음), 전화가 오면 멈추고 끝나면 이어 들음
//        ③ 누리집이 앱을 알아보도록 「LVDBangsong」 표지를 붙임 — 앱에서는 영상 내려받기 대신 「영상은 컴퓨터의 웹에서 받으실 수 있습니다」
// 2.1.0: 누리집 쪽 뒤로 단추(대문으로 가는 것)를 감추어 앱의 뒤로만 남김. 뒤로 가기는 늘 앱 첫 화면으로.
// LVD 방송 누리집 화면(생방송 듣기·다시 듣기)을 앱 안에서 엽니다. 맨 위와 맨 아래에 「뒤로」, 뒤로 가기로 앱 밖에 나가지 않습니다.
package kr.or.ada.app.bfb

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import java.net.URLDecoder

class WebHwamyeon : Activity() {
    companion object {
        // 누리집 화면이 스스로 붙이는 뒤로·첫 화면 단추(누리집 대문으로 감)를 감춤 — 낭독기에도 안 읽힘
        const val GAMCHUGI = "(function(){var s=document.createElement('style');s.textContent='.dwiro-btn,#btnDwi1,#btnDwi2,#homeBtn{display:none!important}';(document.head||document.documentElement).appendChild(s);})();"
        // 전화가 오면 누리집의 소리를 멈추고, 끝나면 멈춘 것만 다시 틂
        const val MEOMCHUM = "(function(){window._lvdMeom=[];document.querySelectorAll('audio,video').forEach(function(m){if(!m.paused){m.pause();window._lvdMeom.push(m);}});})();"
        const val ieum = "(function(){(window._lvdMeom||[]).forEach(function(m){try{m.play();}catch(e){}});window._lvdMeom=[];})();"
    }
    private lateinit var web: WebView
    private val hd = Handler(Looper.getMainLooper())
    private var tonghwa = false
    private var gidari: (() -> Unit)? = null   // 저장 허락을 받은 뒤 할 내려받기(옛 안드로이드)

    private fun dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun dwiroDanchu(): Button = Button(this).apply {
        text = "뒤로"
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#12346E"))
        background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.WHITE); setStroke(dp(3), Color.parseColor("#FFCC00")) }
        minHeight = dp(64)
        setOnClickListener { finish() }
    }

    /** 와이파이(요금 없는 망)인가 */
    private fun waipai(): Boolean = try {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val c = cm.getNetworkCapabilities(cm.activeNetwork)
        c != null && (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) &&
            c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    } catch (_: Exception) { false }

    private fun irum(url: String, cd: String?, mime: String?): String {
        if (cd != null) {
            Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(cd)?.let {
                return try { URLDecoder.decode(it.groupValues[1].trim(), "UTF-8") } catch (_: Exception) { it.groupValues[1] }
            }
        }
        return URLUtil.guessFileName(url, cd, mime)
    }

    private fun batgi(url: String, cd: String?, mime: String?) {
        val nm = irum(url, cd, mime)
        try {
            val rq = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(nm)
                setDescription("LVD 방송 다시 듣기")
                setMimeType(mime)
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                addRequestHeader("User-Agent", web.settings.userAgentString)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nm)
            }
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(rq)
            Toast.makeText(this, "내려받기를 시작합니다. 다 받으면 폰의 다운로드 폴더에 「$nm」으로 남습니다.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "내려받기를 시작하지 못했습니다. 다시 눌러 주십시오.", Toast.LENGTH_LONG).show()
        }
    }

    private fun mutgoBatgi(url: String, cd: String?, mime: String?, n: Long) {
        val hagi = {
            if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                gidari = { batgi(url, cd, mime) }
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 41)
            } else batgi(url, cd, mime)
        }
        if (waipai()) { hagi(); return }
        val meg = if (n > 0) maxOf(1L, Math.round(n / 1048576.0)) else 0L
        AlertDialog.Builder(this)
            .setTitle("데이터로 받기")
            .setMessage(if (meg > 0) "데이터로 ${meg}메가를 받습니다. 받을까요?" else "데이터로 받습니다. 받을까요?")
            .setPositiveButton("받기") { _, _ -> hagi() }
            .setNegativeButton("그만두기", null)
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) gidari?.invoke()
            else Toast.makeText(this, "저장을 허락하셔야 받을 수 있습니다.", Toast.LENGTH_LONG).show()
            gidari = null
        }
    }

    /** 1초마다 — 전화가 울리거나 통화 중이면 소리를 멈추고, 끝나면 이어 틂 */
    private val salpim = object : Runnable {
        override fun run() {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val jung = am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION || am.mode == AudioManager.MODE_RINGTONE
            if (jung && !tonghwa) { tonghwa = true; try { web.evaluateJavascript(MEOMCHUM, null) } catch (_: Exception) { } }
            else if (!jung && tonghwa) { tonghwa = false; try { web.evaluateJavascript(ieum, null) } catch (_: Exception) { } }
            hd.postDelayed(this, 1000)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = intent.getStringExtra("jemok") ?: "LVD 방송"
        window.statusBarColor = Color.parseColor("#12346E")
        volumeControlStream = AudioManager.STREAM_MUSIC
        val mom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#12346E")); setPadding(dp(12), dp(12), dp(12), dp(12)) }
        mom.addView(dwiroDanchu(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = settings.userAgentString + " LVDBangsong/2.3.0"
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) { view?.evaluateJavascript(GAMCHUGI, null) }
            }
            webChromeClient = WebChromeClient()
            setDownloadListener { url, _, cd, mime, n -> mutgoBatgi(url, cd, mime, n) }
        }
        mom.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        mom.addView(dwiroDanchu(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(mom)
        web.loadUrl(intent.getStringExtra("juso") ?: BangsongActivity.BAEP)
        hd.postDelayed(salpim, 1000)
    }

    // 화면을 잠가도 다시 듣기가 이어지도록 누리집 화면을 멈추지 않음(web.onPause 를 부르지 않음)

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()   // 누리집 안에서 대문으로 거슬러 가지 않고 앱 첫 화면으로
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        hd.removeCallbacksAndMessages(null)
        try { web.destroy() } catch (_: Exception) { }
        super.onDestroy()
    }
}
