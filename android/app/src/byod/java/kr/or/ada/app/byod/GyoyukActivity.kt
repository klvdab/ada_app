// 1.6.0 (261019-B14, 방클, 이사장님 지시 2026-10-19 「녹화 끊김 막기」) 화면이 녹화 상태를 앱에 알림(LVDSongchul.nokhwa) — 녹화가 멈추거나,
//   화면이 잠기거나 앱이 뒤로 가 녹화 소식이 8초 넘게 끊기면 3초에 한 번 조용한 알림 소리와 떨림(Jikim). 화면 쪽은 녹화 다시 잇기.
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
import android.webkit.JavascriptInterface
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
        // 1.16.5 녹화 화면 이용 기록 — iOS GyoyukGirok.JS 와 같은 것
        const val GIROK_JS = "(function(){if(window.__lvdGirok)return;window.__lvdGirok=1;function s(t){t=String(t||'').replace(/[0-9]+/g,'#').slice(0,200);try{if(window.webkit&&webkit.messageHandlers&&webkit.messageHandlers.lvdGirok)webkit.messageHandlers.lvdGirok.postMessage(t);else if(window.LVDSongchul&&LVDSongchul.girok)LVDSongchul.girok(t);}catch(e){}}var last='';function bogi(){var t=[];document.querySelectorAll('[aria-live],[role=status],[role=alert]').forEach(function(e){var x=(e.innerText||'').trim();if(x)t.push(x);});var j=t.join(' / ').replace(/[0-9]+/g,'#');if(j&&j!==last){last=j;s('화면: '+j);}}new MutationObserver(function(){clearTimeout(window.__lvdT);window.__lvdT=setTimeout(bogi,400);}).observe(document.documentElement,{subtree:true,childList:true,characterData:true});window.addEventListener('error',function(e){s('오류: '+(e.message||''));});window.addEventListener('unhandledrejection',function(e){s('오류: '+((e.reason&&e.reason.message)||e.reason||''));});s('열림 '+location.pathname);})();"
        const val GAMCHUGI = "(function(){var s=document.createElement('style');s.textContent='#btnDwi1,#btnDwi2,.dwiro-btn,#homeBtn{display:none!important}';(document.head||document.documentElement).appendChild(s);})();"
    }

    private lateinit var web: WebView
    // 1.6.0 녹화 지킴이 — 화면(nokhwa_jikim.js)이 1초마다 알려 주는 녹화 상태
    @Volatile private var nokhwaJung = false
    @Volatile private var meomchumBat = false
    @Volatile private var majimak = 0L
    @Volatile private var boinda = true
    private var jikim: Jikim? = null
    @Volatile private var jeonSangtae = ""
    inner class Dari {
        // 1.16.5 (261109, 새방클) 녹화 화면 안내 말·오류를 이용 기록에(숫자는 화면 쪽에서 #로 가림)
        // 1.17.1 (261116, 새방클, 대표님 지시) 화면의 「설정 열기」 단추 → 이 앱의 설정(권한) 화면을 곧장 엶
        @JavascriptInterface fun seoljeong(mu: String) { Girok.namgi("seoljeong_yeolgi", mapOf("mu" to mu.take(10))); runOnUiThread { seoljeongYeolgi() } }
        @JavascriptInterface fun girok(s: String) { Girok.namgi("gyoyuk_hwamyeon", mapOf("mal" to s.take(200))) }
        @JavascriptInterface fun nokhwa(s: String) {
            majimak = System.currentTimeMillis()
            if (s != "salaitda" && s != jeonSangtae) { jeonSangtae = s; Girok.namgi("gyoyuk_nokhwa", mapOf("sangtae" to s)) }
            when (s) {
                "sijak", "salaitda" -> { nokhwaJung = true; meomchumBat = false }
                "meomchum" -> { nokhwaJung = true; meomchumBat = true }
                "kkeut" -> { nokhwaJung = false; meomchumBat = false }
            }
        }
    }
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
                override fun onPageFinished(view: WebView?, url: String?) { view?.evaluateJavascript(GAMCHUGI, null); view?.evaluateJavascript(GIROK_JS, null) }
                override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    if (request?.isForMainFrame == true) Girok.namgi("gyoyuk_hwamyeon", mapOf("mal" to "열지 못함: " + (error?.description ?: "")))
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    runOnUiThread {
                        // 1.17.1 화면이 청한 것(마이크·카메라)만 따져, 허락된 것은 내주고 없는 것만 묻습니다.
                        //   전에는 카메라·마이크 둘 다 있어야만 내주어, 카메라 하나가 꺼져도 「소리만」 녹화까지 막혔음.
                        if (eopneunGeot(request.resources).isEmpty()) request.grant(request.resources)
                        else { gidarim = request; heorakMutgi(request.resources) }
                    }
                }
            }
        }
        web.addJavascriptInterface(Dari(), "LVDSongchul")   // 1.6.0 녹화 상태 받기
        jikim = Jikim(this, {
            nokhwaJung && (meomchumBat || System.currentTimeMillis() - majimak > 8000 || !boinda)
        }) { m -> if (m) runOnUiThread { try { web.announceForAccessibility("녹화가 멈췄을 수 있습니다. 화면을 켜고 녹화를 살펴 주십시오.") } catch (_: Exception) { } } }.also { it.sijak() }
        android.webkit.CookieManager.getInstance().setAcceptCookie(true)
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        mom.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        mom.addView(dwiroDanchu(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(mom)
        if (!heorakIssum()) heorakMutgi(null)
        Girok.namgi("gyoyuk_yeolgi", mapOf("hwamyeon" to jemok))   // 1.16.5
        try { web.clearCache(true) } catch (_: Exception) { }   // 1.16.4 (261116) 폰이 담아 둔 옛 화면 말고 늘 새 화면을 받음
        web.loadUrl(intent.getStringExtra("juso") ?: JUSO)
    }

    private fun itda(p: String): Boolean = Build.VERSION.SDK_INT < 23 || checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    private val kameraPilyo get() = intent.getStringExtra("juso") != TV   // TV해설방송은 소리만
    private fun heorakIssum(): Boolean = itda(Manifest.permission.RECORD_AUDIO) && (!kameraPilyo || itda(Manifest.permission.CAMERA))

    /** 화면이 청한 자원 가운데 앱 허락이 없는 것(안드로이드 권한 이름) */
    private fun eopneunGeot(res: Array<String>?): List<String> {
        val l = mutableListOf<String>()
        val r = res ?: arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE, PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        if (r.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) && !itda(Manifest.permission.RECORD_AUDIO)) l.add(Manifest.permission.RECORD_AUDIO)
        if (r.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) && (res != null || kameraPilyo) && !itda(Manifest.permission.CAMERA)) l.add(Manifest.permission.CAMERA)
        return l
    }

    private fun heorakMutgi(res: Array<String>?) {
        if (Build.VERSION.SDK_INT >= 23) {
            val l = eopneunGeot(res)
            if (l.isNotEmpty()) requestPermissions(l.toTypedArray(), 31)
        }
    }

    private fun seoljeongYeolgi() {
        try {
            startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", packageName, null)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            try { startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS)) } catch (_: Exception) { }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 31) return
        val r = gidarim; gidarim = null
        if (r != null) {
            // 1.17.1 허락된 것만이라도 내줌 — 카메라가 없으면 화면이 소리만으로 이어 감
            val juldeot = r.resources.filter { (it == PermissionRequest.RESOURCE_AUDIO_CAPTURE && itda(Manifest.permission.RECORD_AUDIO)) || (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE && itda(Manifest.permission.CAMERA)) || (it != PermissionRequest.RESOURCE_AUDIO_CAPTURE && it != PermissionRequest.RESOURCE_VIDEO_CAPTURE) }
            if (juldeot.isNotEmpty()) r.grant(juldeot.toTypedArray()) else r.deny()
        }
        val mic = itda(Manifest.permission.RECORD_AUDIO)
        val cam = !kameraPilyo || itda(Manifest.permission.CAMERA)
        Girok.namgi("gyoyuk_heorak", mapOf("mic" to (if (mic) "heorak" else "geojeol"), "cam" to (if (itda(Manifest.permission.CAMERA)) "heorak" else "geojeol")))
        if (!mic || !cam) {
            val kkeojin = listOfNotNull(if (!mic) "마이크" else null, if (!cam) "카메라" else null).joinToString("와 ")
            val mal = if (!mic) "이 폰에서 LVD 송출의 $kkeojin 허락이 꺼져 있어 소리를 받을 수 없습니다. 설정 열기를 누르고 권한에서 ${kkeojin}를 허용한 뒤 돌아와 주십시오."
                      else "이 폰에서 LVD 송출의 카메라 허락이 꺼져 있어 영상은 담기지 않고 소리만 녹음됩니다. 영상도 담으시려면 설정 열기를 누르고 권한에서 카메라를 허용한 뒤 돌아와 주십시오."
            AlertDialog.Builder(this)
                .setTitle("허락이 꺼져 있습니다")
                .setMessage(mal)
                .setPositiveButton("설정 열기") { _, _ -> Girok.namgi("seoljeong_yeolgi", mapOf("mu" to "alrim")); seoljeongYeolgi() }
                .setNegativeButton("그대로 쓰기", null)
                .show()
        }
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

    override fun onStart() { super.onStart(); boinda = true; majimak = System.currentTimeMillis() }
    override fun onStop() { boinda = false; super.onStop() }   // 1.6.0 화면이 잠기거나 앱이 뒤로 가면 녹화가 멈출 수 있어 지킴이가 알림

    override fun onDestroy() {
        Girok.namgi("gyoyuk_datgi", mapOf("hwamyeon" to jemok))   // 1.16.5
        try { jikim?.meomchugi() } catch (_: Exception) { }
        try { web.destroy() } catch (_: Exception) { }
        super.onDestroy()
    }
}
