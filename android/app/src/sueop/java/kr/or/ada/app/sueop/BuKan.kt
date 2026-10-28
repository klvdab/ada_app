// LVD 수업 안드로이드 0.2.0 「함께하기 칸」 (빌드 261028-S3, 방클, 이사장님 승인 2026-10-28 「1」 앱 안에 웹 기능 칸 붙이기)
// 수업 화면 안에 웹 「함께하기」(lvd.ada.or.kr/sueop/bu.html)를 끼워, 웹 수업 보기와 똑같은 질문 손들기·예상문제 풀이·검정 시험·화상 수업을 씀.
// 장 그림과 글은 지금처럼 앱이 직접 보여 줌. 웹을 고치면 앱도 함께 따라 고쳐짐(기능이 어긋나지 않게).
// 칸은 하나만 만들어 두고 도움말을 보고 와도 그대로 살려 둠(화상 수업이 끊기지 않게). 수업에서 나가면 닫음.
// 화상 수업의 카메라·마이크는 교육생이 「화상 수업 들어가기」를 누를 때에만 폰에 허락을 여쭘.
package kr.or.ada.app.sueop

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout

class BuKan(private val act: Activity) {
    companion object {
        const val JUSO = "https://lvd.ada.or.kr/sueop/bu.html"
        const val HEOLAK = 61
    }

    private var bang = ""
    private var gidarim: PermissionRequest? = null

    @SuppressLint("SetJavaScriptEnabled")
    val web: WebView = WebView(act).apply {
        setBackgroundColor(Color.TRANSPARENT)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.userAgentString = settings.userAgentString + " LVDSueop-android/" + SueopActivity.PAN
        isVerticalScrollBarEnabled = false
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val u = r.url
                if (u.host == "lvd.ada.or.kr") return false
                try { act.startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (_: Exception) { }
                return true
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(req: PermissionRequest) {
                act.runOnUiThread { heolakBada(req) }
            }
            override fun onPermissionRequestCanceled(req: PermissionRequest) { if (gidarim === req) gidarim = null }
        }
    }

    /** 이 방의 함께하기 칸을 엶(같은 방이면 그대로 둠) */
    fun yeolgi(b: String) {
        if (b == bang) return
        bang = b
        web.loadUrl("$JUSO?bang=$b&app=android")
    }

    /** 수업에서 나가면 닫음 — 화상에 들어가 있었으면 나가기도 웹이 알아서 함(pagehide) */
    fun datgi() {
        if (bang.isEmpty()) return
        bang = ""
        web.loadUrl("about:blank")
    }

    /** 화면에 붙임(도움말을 보고 와도 같은 칸을 다시 붙여 화상이 끊기지 않음) */
    fun buchigi(mom: LinearLayout) {
        (web.parent as? ViewGroup)?.removeView(web)
        mom.addView(web)
    }

    private fun piryo(req: PermissionRequest): Array<String> {
        val l = ArrayList<String>()
        if (req.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) l.add(Manifest.permission.CAMERA)
        if (req.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) l.add(Manifest.permission.RECORD_AUDIO)
        return l.toTypedArray()
    }

    private fun heolakBada(req: PermissionRequest) {
        if (req.origin?.host != "lvd.ada.or.kr") { req.deny(); return }
        val p = piryo(req)
        val eopseum = p.filter { act.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (eopseum.isEmpty()) { jugi(req); return }
        gidarim = req
        act.requestPermissions(eopseum.toTypedArray(), HEOLAK)
    }

    private fun jugi(req: PermissionRequest) {
        val r = ArrayList<String>()
        if (req.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) && act.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) r.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        if (req.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) && act.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) r.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
        if (r.isEmpty()) req.deny() else req.grant(r.toTypedArray())
    }

    /** 액티비티가 허락 결과를 넘겨줌 */
    fun heolakGyeolgwa(code: Int) {
        if (code != HEOLAK) return
        val req = gidarim ?: return
        gidarim = null
        jugi(req)
    }

    fun kkeutnaegi() { try { web.loadUrl("about:blank"); web.destroy() } catch (_: Exception) { } }
}
