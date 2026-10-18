// LVD 송출 — 라이브 현장영상해설 방송·현장영상해설교육 녹화 화면 (1.4.0판, 빌드 261016-B12, 방클, 이사장님 지시 「지금 앱으로 완성」 2026-10-16)
// 배프 라이브 방송하기(bang2.html)·교육 녹화(gyoyuk.html) 화면을 앱 안에서 엽니다(이사장님 원칙: 공유기 방송·라이브 방송·교육 녹화를 한 폰, 한 화면에서). 보내기·중계·나스 담기는 그 화면이 그대로 합니다.
// 앱이 하는 일: 카메라·마이크 허락을 받아 화면에 넘겨 줌, 녹화하는 동안 화면이 꺼지지 않게 붙잡음,
//   화면 쪽 뒤로 단추(배프 대문으로 가는 것)를 감추고 앱의 뒤로만 남김, 뒤로 가기는 한 번 더 물어 녹화가 실수로 끊기지 않게 함.
package kr.or.ada.app.byod

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout

class GyoyukActivity : Activity() {
    companion object {
        const val JUSO = "https://lvd.ada.or.kr/bfblive/gyoyuk.html"
        const val LIVE = "https://lvd.ada.or.kr/bfblive/bang2.html"
        const val TV = "https://lvd.ada.or.kr/lvds/lvds_live.html"   // 1.5.0 TV해설방송 하기
        // 화면 쪽 뒤로 단추(배프 대문으로 감)를 감춤 — 낭독기에도 안 읽힘
        const val GAMCHUGI = "(function(){var s=document.createElement('style');s.textContent='#btnDwi1,#btnDwi2,.dwiro-btn,#homeBtn{display:none!important}';(document.head||document.documentElement).appendChild(s);})();"
    }

    private lateinit var web: WebView
    private var gidarim: PermissionRequest? = null
    private var jemok = "현장영상해설교육 녹화"

    private fun dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun dwiroDanchu(): Button = Button(this).apply {
        text = "뒤로"
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#12346E"))
        background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.WHITE); setStroke(dp(3), Color.parseColor("#FFCC00")) }
        minHeight = dp(64)
        setOnClickListener { nagagi() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        jemok = intent.getStringExtra("jemok") ?: "현장영상해설교육 녹화"
        title = jemok
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)   // 녹화하는 동안 화면이 꺼지지 않게
        window.statusBarColor = Color.parseColor("#12346E")
        val mom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#12346E")); setPadding(dp(12), dp(12), dp(12), dp(12)) }
        mom.addView(dwiroDanchu(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) { view?.evaluateJavascript(GAMCHUGI, null) }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    runOnUiThread {
                        if (heorakIssum()) request.grant(request.resources)
                        else { gidarim = request; heorakMutgi() }
                    }
                }
            }
        }
        android.webkit.CookieManager.getInstance().setAcceptCookie(true)
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        mom.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        mom.addView(dwiroDanchu(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(mom)
        if (!heorakIssum()) heorakMutgi()
        web.loadUrl(intent.getStringExtra("juso") ?: JUSO)
    }

    private fun heorakIssum(): Boolean =
        Build.VERSION.SDK_INT < 23 ||
        (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
         checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)

    private fun heorakMutgi() {
        if (Build.VERSION.SDK_INT >= 23) requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 31)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 31) return
        val r = gidarim; gidarim = null
        if (r != null) { if (heorakIssum()) r.grant(r.resources) else r.deny() }
        if (!heorakIssum()) web.announceForAccessibility("카메라와 마이크 허락이 없으면 녹화할 수 없습니다. 폰 설정의 애플리케이션, LVD 송출, 권한에서 카메라와 마이크를 허용해 주십시오.")
    }

    /** 녹화가 실수로 끊기지 않게 한 번 더 물음 */
    private fun nagagi() {
        AlertDialog.Builder(this)
            .setTitle(jemok + "에서 나가기")
            .setMessage("방송이나 녹화 중이면 멈춥니다. 나가시겠습니까?")
            .setPositiveButton("나가기") { _, _ -> finish() }
            .setNegativeButton("머무르기", null)
            .show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { nagagi(); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        try { web.destroy() } catch (_: Exception) { }
        super.onDestroy()
    }
}
