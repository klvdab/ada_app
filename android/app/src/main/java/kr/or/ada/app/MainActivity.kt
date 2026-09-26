// 협회 안드로이드 앱 — 본 화면 (1.1.0판, 빌드 260926-1)
// ★1.1.0 (2026-09-26 이사장님 승인) — 앱 받아쓰기: 안드로이드 앱 안의 웹에는 음성 인식이 없어 말로 시키기·말로 넣기가 안 되던 것을
//   안드로이드 자체 받아쓰기(SpeechRecognizer)로 잇습니다. 다리(BRIDGE_JS)를 문서 맨 처음에 심어 웹의 SpeechRecognition 과 같은 모양으로 내줍니다.
// 나스의 웹을 앱 안에 담고, 웹이 못 하는 손발(위치·알림·진동·뒤로 지킴)을 붙입니다.
// 갈래(flavor)마다 대문 주소가 다릅니다: BuildConfig.ADA_HOME
package kr.or.ada.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var lastBackAt = 0L
    private var sr: SpeechRecognizer? = null
    private var srId = ""

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
        // 다리를 문서 맨 처음(길눈 부품들보다 먼저)에 심습니다 — 그래야 길눈이 받아쓰기가 있다고 알아봅니다
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, BRIDGE_JS,
                setOf("https://lvd.ada.or.kr", "https://ada.or.kr", "https://www.ada.or.kr"))
        }

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

    override fun onDestroy() {
        try { sr?.destroy() } catch (e: Exception) {}
        sr = null
        super.onDestroy()
    }

    // ── 앱 받아쓰기 ──
    private fun sttSend(name: String, obj: JSONObject) {
        val js = "window.adaApp && window.adaApp.batda('" + name + "', " + obj.toString() + ");"
        runOnUiThread { web.evaluateJavascript(js, null) }
    }

    private fun sttFinish(id: String, err: String?) {
        if (id.isEmpty() || id != srId) return
        srId = ""
        if (err != null && err != "aborted") sttSend("deutgiOryu", JSONObject().put("id", id).put("error", err))
        sttSend("deutgiKkeut", JSONObject().put("id", id))
    }

    private fun sttStart(id: String, lang: String) {
        if (srId.isNotEmpty()) {
            try { sr?.cancel() } catch (e: Exception) {}
            sttFinish(srId, null)
        }
        srId = id
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1002)
            sttFinish(id, "not-allowed"); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { sttFinish(id, "service-not-allowed"); return }
        val r = sr ?: SpeechRecognizer.createSpeechRecognizer(this).also { sr = it }
        var bonNal = false
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (id == srId) sttSend("deutgiSijak", JSONObject().put("id", id))
            }
            override fun onPartialResults(b: Bundle?) {
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                if (id == srId && t.isNotEmpty()) sttSend("deutgiGyeolgwa", JSONObject().put("id", id).put("t", t).put("final", false))
            }
            override fun onResults(b: Bundle?) {
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                if (id != srId) return
                if (t.isNotEmpty()) { bonNal = true; sttSend("deutgiGyeolgwa", JSONObject().put("id", id).put("t", t).put("final", true)) }
                sttFinish(id, if (bonNal) null else "no-speech")
            }
            override fun onError(code: Int) {
                val why = when (code) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no-speech"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "not-allowed"
                    SpeechRecognizer.ERROR_AUDIO -> "audio-capture"
                    SpeechRecognizer.ERROR_CLIENT -> "aborted"
                    else -> "network"
                }
                sttFinish(id, why)
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val sik = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (lang.isEmpty()) "ko-KR" else lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try { r.startListening(sik) } catch (e: Exception) { sttFinish(id, "audio-capture") }
    }

    private fun sttStop(id: String, abort: Boolean) {
        if (srId.isEmpty() || (id.isNotEmpty() && id != srId)) return
        if (abort) {
            try { sr?.cancel() } catch (e: Exception) {}
            sttFinish(srId, "aborted")
        } else {
            try { sr?.stopListening() } catch (e: Exception) {}
        }
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
        fun pan(): String = "1.1.0 / 260926-1 / android"

        // 웹 → 앱 (아이폰의 window.webkit.messageHandlers.ada 와 같은 자리). 받아쓰기는 여기로 옵니다
        @JavascriptInterface
        fun bureugi(json: String) {
            val m = try { JSONObject(json) } catch (e: Exception) { return }
            when (m.optString("a")) {
                "deutgiSijak" -> runOnUiThread { sttStart(m.optString("id"), m.optString("lang", "ko-KR")) }
                "deutgiMeom" -> runOnUiThread { sttStop(m.optString("id"), m.optBoolean("abort", false)) }
                "jindong" -> jindong(m.optString("mu", "short"))
                "allim" -> allim(m.optString("title", "길눈"), m.optString("body"), m.optString("tag", "annae"))
            }
        }
    }

    companion object {
        const val NOTI_CH = "ada_annae"

        // 웹 쪽에서 아이폰과 같은 이름으로 쓰게 맞춰 주는 다리
        const val BRIDGE_JS = """
            (function(){
              if (window.adaApp && window.adaApp.__and) return;
              var deul = {};
              window.adaApp = {
                __and: true,
                isApp: true,
                platform: 'android',
                jindong: function(k){ try { adaNative.jindong(k||''); } catch(e){} },
                allim: function(t,b,g){ try { adaNative.allim(t||'', b||'', g||'ada'); } catch(e){} },
                mal: function(s){ try { adaNative.mal(s||''); } catch(e){} },
                pan: function(){ try { return adaNative.pan(); } catch(e){ return ''; } },
                /* 웹 → 앱 (아이폰과 같은 이름) */
                bureugi: function(a, m){ try { var o = m || {}; o.a = a; adaNative.bureugi(JSON.stringify(o)); } catch(e){} },
                /* 앱 → 웹 */
                deutgi: function(name, fn){ (deul[name] = deul[name] || []).push(fn); },
                batda: function(name, data){ var fs = deul[name] || []; for (var i = 0; i < fs.length; i++) { try { fs[i](data); } catch(e){} } }
              };
              try { document.documentElement.setAttribute('data-ada-app','android'); } catch(e){}
  /* ── 앱 받아쓰기 ── */
  var TOP = window;
  try { if (window.top && window.top.adaApp) TOP = window.top; } catch(e){ TOP = window; }
  var REG = TOP.__adaStt || (TOP.__adaStt = {});
  if (TOP === window && !window.__adaSttDal) {
    window.__adaSttDal = 1;
    ["deutgiSijak", "deutgiGyeolgwa", "deutgiOryu", "deutgiKkeut"].forEach(function(n){
      window.adaApp.deutgi(n, function(d){
        try { var who = d && REG[d.id]; if (who) who._batda(n, d); } catch(e){}
      });
    });
  }
  var sun = 0;
  function AppSR(){
    this.lang = "ko-KR"; this.continuous = false; this.interimResults = false; this.maxAlternatives = 1;
    this.grammars = null;
    this._H = {}; this._L = {}; this._id = ""; this._on = false; this._mal = false;
  }
  AppSR.isApp = true;
  /* onresult 같은 손잡이는 웹 것처럼 속 칸(_H)에 둡니다 — 길눈 app.js 가 손잡이를 덮어 감싸도 두 번 불리지 않게 */
  ["start", "end", "result", "error", "nomatch", "audiostart", "audioend", "soundstart", "soundend", "speechstart", "speechend"].forEach(function(n){
    Object.defineProperty(AppSR.prototype, "on" + n, {
      configurable: true,
      get: function(){ return (this._H && this._H[n]) || null; },
      set: function(f){ if (!this._H) this._H = {}; this._H[n] = (typeof f === "function") ? f : null; }
    });
  });
  AppSR.prototype.addEventListener = function(t, f){ (this._L[t] = this._L[t] || []).push(f); };
  AppSR.prototype.removeEventListener = function(t, f){ var a = this._L[t] || []; var i = a.indexOf(f); if (i >= 0) a.splice(i, 1); };
  AppSR.prototype._ssoda = function(t, ev){
    ev = ev || {}; ev.type = t; ev.target = this; ev.currentTarget = this; ev.timeStamp = Date.now();
    var h = this._H && this._H[t];
    try { if (typeof h === "function") h.call(this, ev); } catch(e){ setTimeout(function(){ throw e; }, 0); }
    var a = (this._L[t] || []).slice();
    for (var i = 0; i < a.length; i++) { try { a[i].call(this, ev); } catch(e2){ (function(x){ setTimeout(function(){ throw x; }, 0); })(e2); } }
  };
  AppSR.prototype.start = function(){
    if (this._on) { var er = new Error("이미 듣는 중입니다"); er.name = "InvalidStateError"; throw er; }
    this._on = true; this._mal = false;
    this._id = "s" + Date.now() + "_" + (++sun) + "_" + Math.floor(Math.random() * 1e6);
    REG[this._id] = this;
    window.adaApp.bureugi("deutgiSijak", { id: this._id, lang: this.lang || "ko-KR", continuous: !!this.continuous });
  };
  AppSR.prototype.stop = function(){ if (this._on) window.adaApp.bureugi("deutgiMeom", { id: this._id, abort: false }); };
  AppSR.prototype.abort = function(){
    if (!this._on) return;
    window.adaApp.bureugi("deutgiMeom", { id: this._id, abort: true });
    this._ssoda("error", { error: "aborted", message: "aborted" });
  };
  AppSR.prototype._batda = function(n, d){
    if (n === "deutgiSijak") { this._ssoda("start"); this._ssoda("audiostart"); return; }
    if (n === "deutgiGyeolgwa") {
      var fin = !!d.final;
      if (!this._mal) { this._mal = true; this._ssoda("soundstart"); this._ssoda("speechstart"); }
      if (!fin && !this.interimResults) return;
      var alt = { transcript: String(d.t || ""), confidence: fin ? 0.9 : 0.5 };
      var res = [alt]; res.isFinal = fin; res.item = function(i){ return this[i]; };
      var all = [res]; all.item = function(i){ return this[i]; };
      this._ssoda("result", { resultIndex: 0, results: all });
      if (fin) { this._ssoda("speechend"); this._ssoda("soundend"); }
      return;
    }
    if (n === "deutgiOryu") { this._ssoda("error", { error: String(d.error || "network"), message: String(d.error || "") }); return; }
    if (n === "deutgiKkeut") {
      this._on = false; try { delete REG[this._id]; } catch(e){}
      this._ssoda("audioend"); this._ssoda("end");
    }
  };
  try { window.SpeechRecognition = AppSR; } catch(e){}
  try { window.webkitSpeechRecognition = AppSR; } catch(e){}
            })();
        """
    }
}
