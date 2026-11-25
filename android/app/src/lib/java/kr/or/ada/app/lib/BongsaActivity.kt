// AI점자도서관 안드로이드 — 봉사자 방과 카카오 손끝편지 (0.8.9판, 빌드 261125-L32, 도클)
// 이사장님 승인 2026-11-25 21시 14분 「1」: 앱을 떠나지 않고 앱 안에서 봉사자 방(점자 배우기·카카오 손끝편지)을 연다. 아이폰 Lib/Bongsa.swift 와 같음.
//  - 앱의 회원 열쇠를 봉사자 방 저장소(dsg_tk, dsg_ireum)에 넣어, 회원 등록을 다시 하지 않아도 점역과 손끝편지를 쓴다(처음 한 번 넣고 화면을 다시 읽음).
//  - 「카카오톡으로 보내기」가 부르는 intent:·kakaolink: 주소는 앱이 받아 카카오톡을 연다. 카카오톡이 없으면 플레이 스토어의 카카오톡을 연다.
//  - 목소리 녹음은 마이크 허락을 묻고, 막혀 있으면 웹 화면의 「설정 열기」가 이 앱 설정을 연다(다리 LibAppDa.bonae, ham=seoljeong).
//  - 수료증·카드 그림·점자 파일 저장은 그림은 사진의 「AI점자도서관」 앨범, 파일은 내려받기(Download) 폴더로(다리 ham=jeojang).
//  - 받은 손끝편지의 「AI점자도서관 앱으로 열기」(adalib://pyeonji?p=편지번호)는 이 화면으로 곧바로 열린다(매니페스트).
//  - 도서관 주소(ailib.ada.or.kr 의 /doseogwan/·/nas/) 밖은 폰의 브라우저로 연다(카카오톡 선물하기·홈페이지 후원 등).
//  - 뒤로: 봉사자 방 안에서 앞 화면이 있으면 그리로, 없으면 도서관으로(앱 밖으로 나가지 않음).
package kr.or.ada.app.lib

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

class BongsaActivity : Activity() {
    companion object {
        const val GIL = "gil"
        private const val HOST = "ailib.ada.or.kr"
        private const val MIC_YOCHEONG = 7401
        fun yeolgi(a: Activity, gil: String) { a.startActivity(Intent(a, BongsaActivity::class.java).putExtra(GIL, gil)) }
    }
    private lateinit var web: WebView
    private var gidarim: PermissionRequest? = null
    private var juipHam = false

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val gil = deulgil(intent) ?: intent.getStringExtra(GIL) ?: "bongsa.html#cheot"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Saek.bada(this@BongsaActivity)) }
        val wi = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(Saek.namsaek); setPadding(dp(8), dp(6), dp(8), dp(6)) }
        wi.addView(Button(this).apply { text = "뒤로"; isAllCaps = false; setOnClickListener { dwiro() } })
        wi.addView(TextView(this).apply { text = if (gil.startsWith("pyeonji")) "손끝편지" else "봉사자 방"; textSize = 20f; setTextColor(android.graphics.Color.WHITE); setPadding(dp(12), 0, 0, 0) })
        root.addView(wi, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        web = WebView(this)
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        web.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true; mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(false)   // target=_blank 도 아래 길 고르기로
        }
        web.addJavascriptInterface(Dari(), "LibAppDa")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean = gilGoreugi(r.url)
            @Deprecated("") override fun shouldOverrideUrlLoading(v: WebView, u: String): Boolean = gilGoreugi(Uri.parse(u))
            override fun onPageFinished(v: WebView, u: String?) { yeolsoeNeoki() }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(r: PermissionRequest) {
                runOnUiThread {
                    val sori = r.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                    if (!sori || r.origin.host != HOST) { r.deny(); return@runOnUiThread }
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) r.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                    else { gidarim = r; requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MIC_YOCHEONG) }
                }
            }
        }
        web.loadUrl("https://$HOST/doseogwan/$gil")
    }

    /** adalib://pyeonji?p=편지번호 → pyeonji.html?p=… */
    private fun deulgil(i: Intent?): String? {
        val u = i?.data ?: return null
        if (u.scheme?.lowercase() != "adalib" || u.host?.lowercase() != "pyeonji") return null
        val id = (u.getQueryParameter("p") ?: "").lowercase().filter { it in 'a'..'z' || it in '0'..'9' }
        return if (id.length == 10) "pyeonji.html?p=$id" else null
    }
    override fun onNewIntent(i: Intent) { super.onNewIntent(i); setIntent(i); deulgil(i)?.let { web.loadUrl("https://$HOST/doseogwan/$it") } }

    /** 앱의 회원 열쇠를 봉사자 방 저장소에 — 처음 한 번 넣었으면 화면을 다시 읽어 열쇠로 시작하게 */
    private fun yeolsoeNeoki() {
        val tk = Hoewon.yeolsoe(this); if (tk.isEmpty()) return
        val ir = Hoewon.ireum(this)
        val js = "(function(){try{var n=JSON.stringify(${JSONObject.quote(tk)});if(!localStorage.getItem('dsg_ireum')&&${JSONObject.quote(ir)})localStorage.setItem('dsg_ireum',JSON.stringify(${JSONObject.quote(ir)}));" +
            "if(localStorage.getItem('dsg_tk')!==n){localStorage.setItem('dsg_tk',n);return 'dasi'}}catch(e){}return ''})()"
        web.evaluateJavascript(js) { r -> if (r?.contains("dasi") == true && !juipHam) { juipHam = true; web.reload() } }
    }

    private fun anieGil(u: Uri) = u.host?.lowercase() == HOST && ((u.path ?: "").startsWith("/doseogwan/") || (u.path ?: "").startsWith("/nas/"))

    /** 참이면 이 화면에서 열지 않음 */
    private fun gilGoreugi(u: Uri): Boolean {
        val s = (u.scheme ?: "").lowercase()
        if (s == "http" || s == "https") { if (anieGil(u)) return false; bakkeuro(Intent(Intent.ACTION_VIEW, u)); return true }
        if (s == "about" || s == "blob" || s == "data" || s == "javascript") return false
        if (s == "intent") {   // 카카오톡 공유가 부르는 주소
            val ig = runCatching { Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME) }.getOrNull() ?: return true
            ig.addCategory(Intent.CATEGORY_BROWSABLE); ig.component = null; ig.selector = null
            if (!bakkeuro(ig)) {
                val fb = ig.getStringExtra("browser_fallback_url")
                if (fb != null) bakkeuro(Intent(Intent.ACTION_VIEW, Uri.parse(fb)))
                else ig.`package`?.let { p -> bakkeuro(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$p"))) }
            }
            return true
        }
        if (!bakkeuro(Intent(Intent.ACTION_VIEW, u)) && s.startsWith("kakao")) bakkeuro(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.kakao.talk")))
        return true
    }
    private fun bakkeuro(i: Intent): Boolean = runCatching { startActivity(i); true }.getOrDefault(false)

    override fun onRequestPermissionsResult(rq: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(rq, p, g)
        if (rq != MIC_YOCHEONG) return
        val r = gidarim; gidarim = null
        if (g.isNotEmpty() && g[0] == PackageManager.PERMISSION_GRANTED) r?.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) else r?.deny()
    }

    private fun malhagi(t: String) {
        runOnUiThread { web.evaluateJavascript("try{alrim(${JSONObject.quote(t)})}catch(e){}", null); web.announceForAccessibility(t) }
    }

    /** 웹 화면이 부르는 다리 — LibAppDa.bonae(JSON) */
    inner class Dari {
        @JavascriptInterface fun bonae(json: String) {
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return
            when (o.optString("ham")) {
                "seoljeong" -> runOnUiThread { bakkeuro(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
                "jeojang" -> jeojang(o.optString("nm"), o.optString("typ"), o.optString("b64"))
            }
        }
    }

    private fun jeojang(nm0: String, typ: String, b64: String) {
        val data = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull()
        if (data == null || data.isEmpty()) { malhagi("저장하지 못했습니다."); return }
        val nm = nm0.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifEmpty { "AI점자도서관_저장" }
        val geurim = typ.startsWith("image/")
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, nm); put(MediaStore.MediaColumns.MIME_TYPE, typ.ifEmpty { "application/octet-stream" })
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (geurim) "Pictures/AI점자도서관" else "Download")
            }
            val col = if (geurim) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val ok = runCatching { contentResolver.insert(col, cv)!!.let { u -> contentResolver.openOutputStream(u)!!.use { it.write(data) } }; true }.getOrDefault(false)
            malhagi(if (!ok) "저장하지 못했습니다." else if (geurim) "사진의 AI점자도서관 앨범에 저장했습니다." else "내려받기 폴더에 저장했습니다.")
        } else {
            val d = getExternalFilesDir(if (geurim) android.os.Environment.DIRECTORY_PICTURES else android.os.Environment.DIRECTORY_DOWNLOADS)
            val ok = runCatching { java.io.File(d, nm).writeBytes(data); true }.getOrDefault(false)
            malhagi(if (ok) "이 앱의 저장 폴더에 저장했습니다." else "저장하지 못했습니다.")
        }
    }

    private fun dwiro() { if (web.canGoBack()) web.goBack() else finish() }
    @Deprecated("뒤로 가기 — 봉사자 방 앞 화면, 없으면 도서관으로")
    override fun onBackPressed() { dwiro() }
    override fun onDestroy() { runCatching { web.destroy() }; super.onDestroy() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
