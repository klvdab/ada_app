// LVD 방송(듣는 앱) — 인터넷 생방송·지난 방송 다시 듣기·TV해설방송·TV·라디오 화면 (2.1.0판, 빌드 261016-L2, 방클, 이사장님 승인 「1」)
// 2.1.0: 누리집 쪽 뒤로 단추(대문으로 가는 것)를 감추어 앱의 뒤로만 남김. 뒤로 가기는 늘 앱 첫 화면으로.
// 배리어프리방송국 누리집 화면(생방송 듣기·다시 듣기)을 앱 안에서 엽니다. 맨 위와 맨 아래에 「뒤로」, 뒤로 가기로 앱 밖에 나가지 않습니다.
package kr.or.ada.app.bfb

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout

class WebHwamyeon : Activity() {
    companion object {
        // 누리집 화면이 스스로 붙이는 뒤로·첫 화면 단추(배리어프리방송국 대문으로 감)를 감춤 — 낭독기에도 안 읽힘
        const val GAMCHUGI = "(function(){var s=document.createElement('style');s.textContent='.dwiro-btn,#btnDwi1,#btnDwi2,#homeBtn{display:none!important}';(document.head||document.documentElement).appendChild(s);})();"
    }
    private lateinit var web: WebView

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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = intent.getStringExtra("jemok") ?: "LVD 방송"
        window.statusBarColor = Color.parseColor("#12346E")
        val mom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#12346E")); setPadding(dp(12), dp(12), dp(12), dp(12)) }
        mom.addView(dwiroDanchu(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) { view?.evaluateJavascript(GAMCHUGI, null) }
            }
            webChromeClient = WebChromeClient()
        }
        mom.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        mom.addView(dwiroDanchu(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(mom)
        web.loadUrl(intent.getStringExtra("juso") ?: BangsongActivity.BAEP)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()   // 누리집 안에서 대문으로 거슬러 가지 않고 앱 첫 화면으로
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        try { web.destroy() } catch (_: Exception) { }
        super.onDestroy()
    }
}
