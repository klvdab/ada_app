// 협회 안드로이드 앱 — 본 화면 (1.0.0판, 빌드 260923-1)
// 나스의 웹을 앱 안에 담고, 웹이 못 하는 손발(위치·알림·진동·뒤로 지킴)을 붙입니다.
// 갈래(flavor)마다 대문 주소가 다릅니다: BuildConfig.ADA_HOME
package kr.or.ada.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var lastBackAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this)
        setContentView(web)

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setGeolocationEnabled(true)
            cacheMode = WebSettings.LOAD_DEFAULT
            javaScriptCanOpenWindowsAutomatically = true
        }
        web.isVerticalScrollBarEnabled = true
        web.addJavascriptInterface(Bridge(), "adaNative")

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView?, req: WebResourceRequest?): Boolean {
                // 우리 주소는 앱 안에서, 바깥 주소도 앱 안에서 열어 앱을 벗어나지 않게 합니다.
                return false
            }

            override fun onPageFinished(v: WebView?, url: String?) {
                // 웹에서 쓰는 다리 이름을 아이폰과 같게 맞춰 줍니다(window.adaApp).
                v?.evaluateJavascript(BRIDGE_JS, null)
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?, callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, true, true)
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }
        }

        // 뒤로 가기로 앱 밖에 나가지 않게 지킵니다.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) {
                    web.goBack()
                    return
                }
                val now = System.currentTimeMillis()
                if (now - lastBackAt < 2500) {
                    finish()
                } else {
                    lastBackAt = now
                    web.announceForAccessibility("첫 화면입니다. 앱을 닫으려면 뒤로를 한 번 더 누르십시오.")
                }
            }
        })

        makeNotiChannel()
        askPermissions()
        web.loadUrl(BuildConfig.ADA_HOME)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    private fun makeNotiChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(NOTI_CH, "안내", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "길눈과 방송 안내"
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    private fun askPermissions() {
        val want = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            want.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            want.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        val need = want.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, need.toTypedArray(), 1001)
        }
    }

    inner class Bridge {

        @JavascriptInterface
        fun jindong(kind: String) {
            val v: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = when (kind) {
                "waen" -> longArrayOf(0, 60, 90, 60)
                "oren" -> longArrayOf(0, 250)
                "dochak" -> longArrayOf(0, 80, 80, 80, 80, 80)
                else -> longArrayOf(0, 100)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, -1)
            }
        }

        @JavascriptInterface
        fun allim(title: String, body: String, tag: String) {
            val n = NotificationCompat.Builder(this@MainActivity, NOTI_CH)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.notify(tag.hashCode(), n)
        }

        @JavascriptInterface
        fun mal(text: String) {
            runOnUiThread { web.announceForAccessibility(text) }
        }

        @JavascriptInterface
        fun pan(): String = "1.0.0 / 260923-1 / android"
    }

    companion object {
        const val NOTI_CH = "ada_annae"

        // 웹 쪽에서 아이폰과 같은 이름으로 쓰게 맞춰 주는 다리
        const val BRIDGE_JS = """
            (function(){
              if (window.adaApp && window.adaApp.__and) return;
              window.adaApp = {
                __and: true,
                platform: 'android',
                jindong: function(k){ try { adaNative.jindong(k||''); } catch(e){} },
                allim: function(t,b,g){ try { adaNative.allim(t||'', b||'', g||'ada'); } catch(e){} },
                mal: function(s){ try { adaNative.mal(s||''); } catch(e){} },
                pan: function(){ try { return adaNative.pan(); } catch(e){ return ''; } }
              };
              document.documentElement.setAttribute('data-ada-app','android');
            })();
        """
    }
}
