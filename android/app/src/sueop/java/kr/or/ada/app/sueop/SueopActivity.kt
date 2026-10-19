// LVD 수업 안드로이드 — 첫 화면과 수업 화면 (0.1.0판, 빌드 261020-S1, 방클, 이사장님 승인 2026-10-20 「단독으로 가자」)
// 이사장님이 노트북의 「수업 이끌기」(lvd.ada.or.kr/sueop/ikkeulgi.html)에서 넘기는 자료를 교육생 폰에서 그대로 보는 앱. 아이폰 LVD 수업 0.1.0 과 같은 기능.
// 웹 「수업 보기」(sueop/index.html 0.2판)의 기능을 하나도 빼지 않고 담고, 앱이라 더 좋게 한 것을 더함.
//   웹과 같은 것: 방 번호 네 자리 넣기, 지금 장의 그림과 글, 빨간 테두리 가리키기, 프로그램 화면, 협회 로고, 화면 꺼짐 막기, 뒤로 가기 지킴(나가기는 한 번 더 여쭘), 수업 마침 알림, 끊김 알림과 다시 잇기
//   앱에서 더한 것: 바뀐 일만 한 번 알림(톡백·말소리), 가리키는 곳의 말, 그림 크게 보기(손가락으로 키우기), 글자 크기, 지난번 방으로 다시 들어가기, 도움말 찾기, 새 판 알림, 이용 기록
// 서버는 웹과 같은 sueop.php(a=bogi)를 1.5초마다 살펴, 바뀐 것만 고쳐 그림(화면을 통째로 다시 그리지 않아 톡백 자리가 흔들리지 않음).
package kr.or.ada.app.sueop

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class SueopActivity : Activity() {

    companion object {
        const val PAN = "0.1.0"   // 0.1.0(261020-S1) LVD 수업 안드로이드 첫 판 — 웹 수업 보기 0.2판의 기능 모두와 바뀐 일만 알림·그림 크게 보기·글자 크기·지난번 방·도움말·새 판 알림·이용 기록(방클, 이사장님 승인 2026-10-20 「단독으로 가자」)
        const val BILD = "261020-S1"
        const val API = "https://lvd.ada.or.kr/sueop/sueop.php"
        const val BAN = "https://lvd.ada.or.kr/sueop/bang/"
    }

    private object Saek {
        val bada = Color.parseColor("#12346E")
        val nam = Color.parseColor("#0B214C")
        val geul = Color.WHITE
        val norang = Color.parseColor("#FFCC00")
        val geom = Color.parseColor("#111111")
        val yeonhan = Color.parseColor("#DCE6F5")
    }

    /** 서버가 주는 수업 형편 — 웹 수업 보기와 같은 값 */
    data class Sangtae(
        val ireum: String = "", val jang: Int = 0, val su: Int = 0, val mode: Int = 3, val geul: String = "",
        val kkeut: Int = 0, val ver: Int = -1, val hw: Int = 0, val gv: Int = 0,
        val gon: Int = 0, val gx: Int = 0, val gy: Int = 0, val gw: Int = 0, val gh: Int = 0, val gmal: String = ""
    ) {
        val geurimKi: String get() = when {
            kkeut != 0 || jang <= 0 -> ""
            mode == 4 -> "hw$hw"
            mode == 2 -> ""
            else -> "p${jang}v$gv"
        }
        val bogiKi: String get() = if (mode == 4) "hw" else geurimKi
        val gariBoim: Boolean get() = gon != 0 && mode != 2 && mode != 4 && gw > 0 && gh > 0 && kkeut == 0 && jang > 0
        companion object {
            fun bada(j: JSONObject): Sangtae {
                fun n(k: String) = j.optString(k, "0").toDoubleOrNull()?.toInt() ?: (if (j.optBoolean(k, false)) 1 else 0)
                var m = n("mode"); if (m < 1 || m > 4) m = 3
                return Sangtae(j.optString("ireum", ""), n("jang"), n("su"), m, j.optString("geul", ""), n("kkeut"), n("ver"), n("hw"), n("gv"),
                    n("gon"), n("gx"), n("gy"), n("gw"), n("gh"), j.optString("gmal", ""))
            }
        }
    }

    private val h = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(2)
    private lateinit var ttung: ScrollView
    private lateinit var mom: LinearLayout
    private var tts: TextToSpeech? = null
    private var ttsDoem = false
    private val pref by lazy { getSharedPreferences("lvd_sueop", Context.MODE_PRIVATE) }
    private val malsoriOn get() = pref.getBoolean("malsori", false)   // 교실에서 소리가 나지 않게 처음엔 꺼 둠

    private enum class Hwamyeon { CHEOT, SUEOP, DOUMAL }
    private var hwamyeon = Hwamyeon.CHEOT
    private var doumalDwi = Hwamyeon.CHEOT

    // 수업 형편
    private var beonho = ""
    private var deureoom = false
    private var cheotBogi = true
    private var s = Sangtae()
    private var bogiJung = false
    private var mot = 0
    private var kkeunkim = false
    private var chatMal = ""
    private var geurim: Bitmap? = null
    private var geurimKi = ""
    private var geurimBatneunKi = ""
    private var geurimIreum = ""
    private var jeonJang = -1; private var jeonMode = -1; private var jeonGmal = ""; private var jeonGon = 0
    private var sallim = false
    private var cheotMal = ""          // 들어온 첫 알림 — 첫 장 알림과 합쳐 한 번에
    private var beonhoGeul = ""        // 첫 화면을 다시 그릴 때 치던 방 번호를 지킴

    // 수업 화면 부품(바뀐 것만 고침)
    private var meoriIreum: TextView? = null
    private var meoriJang: TextView? = null
    private var meoriBox: LinearLayout? = null
    private var kkeunkimJul: TextView? = null
    private var allimJul: TextView? = null
    private var geurimView: GariImage? = null
    private var geurimDanchu: Button? = null
    private var geurimGidarim: TextView? = null
    private var gmalJul: TextView? = null
    private var geulKan: TextView? = null
    private var geulDanchu: LinearLayout? = null
    private var keugeDialog: Dialog? = null
    private var keugeView: KeugeImage? = null
    private var keugeJul: TextView? = null

    private val tik = object : Runnable {
        override fun run() { if (sallim) { bogi(); h.postDelayed(this, 1500) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "LVD 수업"
        window.statusBarColor = Saek.bada
        window.navigationBarColor = Saek.bada
        ttung = ScrollView(this).apply { setBackgroundColor(Saek.bada); isFillViewport = true }
        mom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(20), dp(18), dp(28)) }
        ttung.addView(mom, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(ttung)
        // 안드로이드 15 는 화면을 가장자리까지 펼치므로 상태 표시줄·아래 단추 줄만큼 띄움
        ttung.setOnApplyWindowInsetsListener { v, ins ->
            if (Build.VERSION.SDK_INT >= 30) {
                val g = ins.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(g.left, g.top, g.right, g.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(ins.systemWindowInsetLeft, ins.systemWindowInsetTop, ins.systemWindowInsetRight, ins.systemWindowInsetBottom)
            }
            ins
        }
        // 안드로이드 13 이상 — 새 판 알림을 띄우려면 알림 허락이 필요(처음 한 번 여쭘)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 44)
        tts = TextToSpeech(this) { st -> if (st == TextToSpeech.SUCCESS) { tts?.language = Locale.KOREAN; ttsDoem = true } }
        Girok.sijak(this)
        Ollim.malhagi = { m -> allim(m) }
        Ollim.makgi = null
        Ollim.byeonhwa = { if (hwamyeon == Hwamyeon.CHEOT) cheotHwamyeon() }
        Ollim.sijak(this)
        Ollim.intentBoda(this, intent)
        cheotHwamyeon()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Ollim.intentBoda(this, intent)
    }

    override fun onResume() {
        super.onResume()
        Ollim.dorawatda(this)
        if (sallim) { h.removeCallbacks(tik); h.post(tik) }
    }

    override fun onPause() {
        h.removeCallbacks(tik)   // 앱을 벗어나면 살피기를 쉼(돌아오면 onResume 이 다시 시작)
        super.onPause()
    }

    override fun onDestroy() {
        sallim = false
        h.removeCallbacksAndMessages(null)
        try { tts?.shutdown() } catch (_: Exception) { }
        Ollim.byeonhwa = null; Ollim.malhagi = null
        Girok.jeojang()
        super.onDestroy()
    }

    override fun onStop() { Girok.jeojang(); super.onStop() }

    // ───── 꾸밈 ─────
    private fun dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun danchu(geul: String, keun: Boolean = false, norang: Boolean = false, f: () -> Unit): Button {
        val b = Button(this)
        b.text = geul
        b.isAllCaps = false
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (keun) 26f else 20f)
        b.typeface = Typeface.DEFAULT_BOLD
        b.setTextColor(if (norang) Saek.geom else Saek.bada)
        b.background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(if (norang) Saek.norang else Saek.geul)
            setStroke(dp(3), if (norang) Saek.geom else Saek.norang)
        }
        val p = if (keun) dp(26) else dp(16)
        b.setPadding(dp(16), p, dp(16), p)
        b.minHeight = dp(if (keun) 100 else 60)
        b.setOnClickListener { f() }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(10); lp.bottomMargin = dp(4)
        b.layoutParams = lp
        return b
    }

    private fun geul(t: String, keugi: Float = 20f, gulgeum: Boolean = false, saek: Int = Saek.geul): TextView {
        val v = TextView(this)
        v.text = t
        v.setTextColor(saek)
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, keugi)
        v.setLineSpacing(0f, 1.35f)
        if (gulgeum) v.typeface = Typeface.DEFAULT_BOLD
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(10)
        v.layoutParams = lp
        return v
    }

    private fun gyeorugi(v: View) {
        h.postDelayed({
            v.requestFocus()
            v.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
            v.requestRectangleOnScreen(android.graphics.Rect(0, 0, v.width, v.height))
        }, 300)
    }

    /** 한 번 알릴 말 — 프로그램 말소리가 켜져 있으면 말로, 아니면 톡백에(두 번 겹쳐 듣지 않게) */
    private fun allim(t: String) {
        if (malsoriOn && ttsDoem) { try { tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "lvd") } catch (_: Exception) { } }
        else if (::mom.isInitialized) mom.announceForAccessibility(t)
    }

    private fun jinanBeonho(): String? {
        val b = pref.getString("jinan", null) ?: return null
        val t = pref.getLong("jinan_t", 0L)
        return if (b.length == 4 && System.currentTimeMillis() - t < 14L * 3600 * 1000) b else null
    }

    // ───── 첫 화면 ─────
    private fun cheotHwamyeon() {
        hwamyeon = Hwamyeon.CHEOT
        bueumBiugi()
        mom.removeAllViews()
        val meori = HyeophoeLogo.meori(this, "LVD 수업")
        mom.addView(meori)
        Ollim.julMal(this)?.let { m ->
            mom.addView(geul(m, 20f, true, Saek.norang))
            mom.addView(danchu("새 판 받기", norang = true) { Ollim.olligi(this) })
        }
        mom.addView(geul("강사님이 알려 준 방 번호 네 자리를 넣어 주십시오.", 21f, true))
        val kan = EditText(this).apply {
            hint = "방 번호 네 자리"
            setHintTextColor(Color.GRAY)
            setTextColor(Saek.geom)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 36f)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_GO
            filters = arrayOf(InputFilter.LengthFilter(4))
            contentDescription = "방 번호 네 자리"
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.WHITE) }
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }
        }
        if (beonhoGeul.isNotEmpty() && beonhoGeul.length < 4) { kan.setText(beonhoGeul); kan.setSelection(beonhoGeul.length) }
        kan.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
            override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
            override fun afterTextChanged(e: Editable?) {
                val n = (e?.toString() ?: "").filter { it.isDigit() }
                beonhoGeul = if (n.length == 4) "" else n
                if (n.length == 4) { sumgiKey(kan); deureogagi(n) }
            }
        })
        kan.setOnEditorActionListener { _, a, _ ->
            if (a == EditorInfo.IME_ACTION_GO || a == EditorInfo.IME_ACTION_DONE) { sumgiKey(kan); deureogagi(kan.text.toString()); true } else false
        }
        mom.addView(kan)
        mom.addView(danchu("들어가기", keun = true, norang = true) { sumgiKey(kan); deureogagi(kan.text.toString()) })
        jinanBeonho()?.let { j -> mom.addView(danchu("지난번 방($j)으로 다시 들어가기") { deureogagi(j) }) }
        allimJul = geul(chatMal, 20f, true, Saek.norang).apply { visibility = if (chatMal.isEmpty()) View.GONE else View.VISIBLE }
        mom.addView(allimJul)
        mom.addView(danchu("도움말") { doumalHwamyeon() })
        mom.addView(danchu(if (malsoriOn) "프로그램 말소리 — 켜짐. 누르면 끕니다" else "프로그램 말소리 — 꺼짐. 누르면 켭니다") {
            val on = !malsoriOn
            pref.edit().putBoolean("malsori", on).apply()
            cheotHwamyeon()
            allim(if (on) "프로그램 말소리를 켰습니다." else "프로그램 말소리를 껐습니다.")
        })
        mom.addView(danchu(Ollim.seoljeongMal(this)) { Ollim.seoljeongNureum(this) })
        mom.addView(geul("LVD 수업 안드로이드 ${PAN}판, 빌드 ${BILD}. 사단법인 한국시각장애인현장영상해설협회", 16f, false, Saek.yeonhan))
        ttung.scrollTo(0, 0)
        gyeorugi(meori)
    }

    private fun sumgiKey(v: View) {
        try { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0) } catch (_: Exception) { }
    }

    private fun chatMalBakkugi(m: String, malHagi: Boolean = true) {
        chatMal = m
        allimJul?.let { it.text = m; it.visibility = if (m.isEmpty()) View.GONE else View.VISIBLE }
        if (malHagi && m.isNotEmpty()) allim(m)
    }

    // ───── 들어가기·나가기 ─────
    private fun deureogagi(b0: String) {
        val b = b0.filter { it.isDigit() }
        if (b.length != 4) { chatMalBakkugi("방 번호 네 자리를 넣어 주십시오."); return }
        if (sallim && b == beonho) return
        nagagi(false)
        beonhoGeul = ""; cheotMal = ""
        beonho = b; s = Sangtae(); geurim = null; geurimKi = ""; geurimBatneunKi = ""; geurimIreum = ""
        mot = 0; kkeunkim = false; cheotBogi = true; jeonJang = -1; jeonMode = -1; jeonGmal = ""; jeonGon = 0
        chatMalBakkugi("방 번호 $b 수업에 들어가고 있습니다.", false)
        Girok.namgi("sueop_deureom", mapOf("bang" to b))
        sallim = true
        h.removeCallbacks(tik); h.post(tik)
    }

    private fun nagagi(malHagi: Boolean = true) {
        sallim = false
        h.removeCallbacks(tik)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        keugeDialog?.dismiss(); keugeDialog = null
        if (deureoom) Girok.namgi("sueop_nagam", mapOf("bang" to beonho, "jang" to s.jang))
        val doem = deureoom
        deureoom = false
        if (doem && malHagi) {
            chatMal = "수업에서 나왔습니다. 같은 방 번호로 언제든 다시 들어오실 수 있습니다."
            cheotHwamyeon()
            allim(chatMal)
        }
    }

    private fun nagalkka() {
        AlertDialog.Builder(this)
            .setTitle("수업에서 나갈까요?")
            .setMessage("나가셔도 같은 방 번호로 언제든 다시 들어오실 수 있습니다.")
            .setPositiveButton("나가기") { _, _ -> nagagi() }
            .setNegativeButton("수업에 남기", null)
            .show()
    }

    // ───── 살피기 ─────
    private fun bogi() {
        if (bogiJung || beonho.isEmpty()) return
        bogiJung = true
        val b = beonho
        pool.execute {
            var j: JSONObject? = null
            try {
                val c = URL("$API?a=bogi&bang=$b&_=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
                c.connectTimeout = 7000; c.readTimeout = 8000; c.useCaches = false
                c.setRequestProperty("User-Agent", "LVDSueop-android/$PAN")
                if (c.responseCode == 200) j = JSONObject(c.inputStream.bufferedReader().readText())
                c.disconnect()
            } catch (_: Exception) { }
            h.post {
                bogiJung = false
                if (beonho != b || !sallim) return@post
                val jj = j
                if (jj == null) { motBatgi(); return@post }
                if (kkeunkim) { kkeunkim = false; kkeunkimBoigi(); allim("다시 이어졌습니다.") }
                mot = 0
                if (!jj.optBoolean("ok", false)) {
                    if (cheotBogi || jj.optString("why") == "bang") {
                        nagagi(false)
                        chatMal = "방 번호 ${b}인 수업을 찾지 못했습니다. 강사님이 알려 준 번호를 다시 넣어 주십시오."
                        Girok.namgi("sueop_mot_chatgi", mapOf("bang" to b))
                        cheotHwamyeon()
                        allim(chatMal)
                    }
                    return@post
                }
                val ns = Sangtae.bada(jj)
                if (cheotBogi) {
                    cheotBogi = false
                    deureoom = true
                    chatMal = ""
                    pref.edit().putString("jinan", b).putLong("jinan_t", System.currentTimeMillis()).apply()
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    s = ns.copy(ver = -2)
                    sueopHwamyeon()
                    cheotMal = "수업에 들어왔습니다." + if (ns.ireum.isNotEmpty()) " ${ns.ireum}." else ""
                }
                if (ns != s) bakkwim(ns)
                if (cheotMal.isNotEmpty()) { val m = cheotMal; cheotMal = ""; allim(m) }   // 장 알림이 없었으면 들어왔다는 말만
                else if (s.geurimKi.isNotEmpty() && s.geurimKi != geurimKi) geurimBatgi(s.geurimKi, s)   // 그림을 못 받았으면 다시
            }
        }
    }

    private fun motBatgi() {
        mot += 1
        if (mot == 5 && !kkeunkim) {
            kkeunkim = true
            kkeunkimBoigi()
            allim("수업 화면과 잠시 끊겼습니다. 저절로 다시 잇습니다.")
            Girok.namgi("sueop_kkeunkim", mapOf("bang" to beonho))
        }
    }

    private fun kkeunkimBoigi() { kkeunkimJul?.visibility = if (kkeunkim) View.VISIBLE else View.GONE }

    /** 수업 중 알림 — 들어온 첫 알림이 남아 있으면 앞에 붙여 한 번에(앞말이 끊기지 않게) */
    private fun sueopMal(m: String) {
        val a = if (cheotMal.isNotEmpty()) "$cheotMal $m" else m
        cheotMal = ""
        allim(a)
    }

    /** 바뀐 것만 알리고 고침 */
    private fun bakkwim(ns: Sangtae) {
        val jeon = s
        s = ns
        if (ns.kkeut != 0) {
            if (jeon.kkeut == 0) { sueopMal("수업을 마쳤습니다. 수고하셨습니다."); Girok.namgi("sueop_kkeut", mapOf("bang" to beonho)) }
            geurim = null; geurimKi = ""
            sueopGochigi()
            return
        }
        if (ns.jang > 0) {
            if (ns.mode == 4 && jeonMode != 4) sueopMal("강사님이 프로그램 화면을 보여 주십니다.")
            else if (ns.mode != 4 && (ns.jang != jeonJang || jeonMode == 4)) {
                var m = "${ns.jang}장"
                if (ns.su > 0) m += ", 모두 ${ns.su}장"
                if (ns.mode == 2) m += ". 글만 보여 주십니다"
                sueopMal(m)
            } else if (ns.gariBoim && ns.gmal.isNotEmpty() && (ns.gmal != jeonGmal || jeonGon == 0)) sueopMal("가리키는 곳: ${ns.gmal}")
            jeonJang = ns.jang; jeonMode = ns.mode
        }
        jeonGmal = ns.gmal; jeonGon = ns.gon
        val ki = ns.geurimKi
        if (ki.isEmpty()) { geurim = null; geurimKi = "" }
        else if (ki != geurimKi) geurimBatgi(ki, ns)
        if (jeon.bogiKi != ns.bogiKi) keugeView?.post { keugeView?.jeJari() }
        sueopGochigi()
    }

    private fun geurimBatgi(ki: String, ns: Sangtae) {
        if (geurimBatneunKi == ki) return
        geurimBatneunKi = ki
        val b = beonho
        val juso = if (ns.mode == 4) "$BAN$b/hw.jpg?h=${ns.hw}" else "$BAN$b/p${ns.jang}.jpg?v=${ns.gv}"
        pool.execute {
            var bm: Bitmap? = null
            try {
                val c = URL(juso).openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 15000; c.useCaches = false
                if (c.responseCode == 200) {
                    val d = c.inputStream.readBytes()
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(d, 0, d.size, o)
                    var sample = 1
                    while (maxOf(o.outWidth, o.outHeight) / sample > 2400) sample *= 2
                    bm = BitmapFactory.decodeByteArray(d, 0, d.size, BitmapFactory.Options().apply { inSampleSize = sample })
                }
                c.disconnect()
            } catch (_: Exception) { } catch (_: OutOfMemoryError) { }
            h.post {
                if (beonho != b) return@post
                if (geurimBatneunKi == ki) geurimBatneunKi = ""
                if (s.geurimKi != ki) return@post   // 받는 사이 또 바뀌었으면 버림
                val g = bm ?: return@post
                geurim = g; geurimKi = ki
                geurimIreum = if (ns.mode == 4) "강사님이 보여 주는 프로그램 화면" else "${ns.jang}장 자료"
                sueopGochigi()
            }
        }
    }

    // ───── 수업 화면 ─────
    private fun bueumBiugi() {
        meoriIreum = null; meoriJang = null; meoriBox = null; kkeunkimJul = null; geurimView = null; geurimDanchu = null
        geurimGidarim = null; gmalJul = null; geulKan = null; geulDanchu = null; allimJul = null
    }

    private fun jangGeul(): String = when {
        s.kkeut != 0 -> "수업을 마쳤습니다"
        s.jang <= 0 -> "곧 시작합니다"
        s.mode == 4 -> "프로그램 화면"
        s.su > 0 -> "${s.jang}장 / ${s.su}장"
        else -> "${s.jang}장"
    }

    private fun sueopHwamyeon() {
        hwamyeon = Hwamyeon.SUEOP
        bueumBiugi()
        mom.removeAllViews()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply { setColor(Saek.nam); cornerRadius = dp(16).toFloat() }
            isFocusable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        box.addView(HyeophoeLogo(this).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(56), LinearLayout.LayoutParams.WRAP_CONTENT))
        val gl = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
        meoriIreum = TextView(this).apply { setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f); typeface = Typeface.DEFAULT_BOLD }
        meoriJang = TextView(this).apply { setTextColor(Saek.norang); setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f); typeface = Typeface.DEFAULT_BOLD }
        gl.addView(meoriIreum); gl.addView(meoriJang)
        box.addView(gl, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        meoriBox = box
        mom.addView(box)
        kkeunkimJul = geul("수업 화면과 잠시 끊겼습니다. 저절로 다시 잇습니다.", 19f, true, Saek.norang).apply { visibility = View.GONE }
        mom.addView(kkeunkimJul)
        allimJul = geul("", 21f, true, Saek.norang)
        mom.addView(allimJul)
        geurimView = GariImage(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }
            visibility = View.GONE
            isFocusable = true
            setOnClickListener { keugeBogi() }
        }
        mom.addView(geurimView)
        geurimGidarim = geul("자료 그림을 받고 있습니다.", 18f, false, Saek.yeonhan).apply { visibility = View.GONE }
        mom.addView(geurimGidarim)
        geurimDanchu = danchu("그림 크게 보기") { keugeBogi() }.apply { visibility = View.GONE }
        mom.addView(geurimDanchu)
        gmalJul = geul("", 20f, true, Saek.norang).apply { visibility = View.GONE }
        mom.addView(gmalJul)
        geulKan = TextView(this).apply {
            setTextColor(Saek.geom)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, pref.getFloat("geul_keugi", 22f))
            setLineSpacing(0f, 1.4f)
            setTextIsSelectable(true)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(12).toFloat() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }
            visibility = View.GONE
        }
        mom.addView(geulKan)
        geulDanchu = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; visibility = View.GONE }
        val kb = danchu("글자 크게") { geulBakkugi(3f) }; val jb = danchu("글자 작게") { geulBakkugi(-3f) }
        geulDanchu?.addView(kb, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6); topMargin = dp(10) })
        geulDanchu?.addView(jb, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6); topMargin = dp(10) })
        mom.addView(geulDanchu)
        mom.addView(danchu("도움말") { doumalHwamyeon() })
        mom.addView(danchu("나가기") { nagalkka() })
        ttung.scrollTo(0, 0)
        sueopGochigi()
        gyeorugi(box)
    }

    /** 지금 형편대로 수업 화면 부품을 고침(새로 만들지 않음) */
    private fun sueopGochigi() {
        if (hwamyeon != Hwamyeon.SUEOP) { keugeGochigi(); return }
        val ir = if (s.ireum.isEmpty()) "LVD 수업" else s.ireum
        meoriIreum?.text = ir
        meoriJang?.text = "방 $beonho · ${jangGeul()}"
        meoriBox?.contentDescription = "$ir. 방 $beonho. ${jangGeul()}"
        kkeunkimBoigi()
        val al = when {
            s.kkeut != 0 -> "수업을 마쳤습니다. 수고하셨습니다."
            s.jang <= 0 -> "수업이 곧 시작됩니다. 잠시 기다려 주십시오."
            else -> ""
        }
        allimJul?.let { it.text = al; it.visibility = if (al.isEmpty()) View.GONE else View.VISIBLE }
        val boil = s.kkeut == 0 && s.jang > 0
        val geurimBoil = boil && s.mode != 2
        val g = geurim
        geurimView?.let { v ->
            if (geurimBoil && g != null) {
                if (v.tag !== g) { v.setImageBitmap(g); v.tag = g }
                v.gari = if (s.gariBoim) RectF(s.gx.toFloat(), s.gy.toFloat(), s.gw.toFloat(), s.gh.toFloat()) else null
                v.contentDescription = geurimIreum + (if (s.gariBoim) ", 빨간 테두리로 가리키는 곳이 있습니다" else "") + ". 두드리면 크게 봅니다"
                v.visibility = View.VISIBLE
            } else v.visibility = View.GONE
        }
        geurimGidarim?.visibility = if (geurimBoil && g == null) View.VISIBLE else View.GONE
        geurimDanchu?.visibility = if (geurimBoil && g != null) View.VISIBLE else View.GONE
        gmalJul?.let { it.text = "가리키는 곳: ${s.gmal}"; it.visibility = if (boil && s.gariBoim && s.gmal.isNotEmpty()) View.VISIBLE else View.GONE }
        val geulBoil = boil && s.mode != 1 && s.mode != 4 && s.geul.isNotEmpty()
        geulKan?.let { if (it.text.toString() != s.geul) it.text = s.geul; it.visibility = if (geulBoil) View.VISIBLE else View.GONE }
        geulDanchu?.visibility = if (geulBoil) View.VISIBLE else View.GONE
        keugeGochigi()
    }

    private fun geulBakkugi(d: Float) {
        val n = (pref.getFloat("geul_keugi", 22f) + d).coerceIn(16f, 44f)
        pref.edit().putFloat("geul_keugi", n).apply()
        geulKan?.setTextSize(TypedValue.COMPLEX_UNIT_SP, n)
        allim(if (d > 0) "글자를 키웠습니다." else "글자를 줄였습니다.")
    }

    // ───── 그림 크게 보기 ─────
    private fun keugeBogi() {
        if (geurim == null) return
        val dl = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val tl = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val iv = KeugeImage(this).apply { contentDescription = "$geurimIreum, 크게 보기" }
        tl.addView(iv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val wi = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10)) }
        val jul = TextView(this).apply { setTextColor(Saek.norang); setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f); typeface = Typeface.DEFAULT_BOLD }
        wi.addView(jul, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val dat = Button(this).apply {
            text = "닫기"; isAllCaps = false; setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f); typeface = Typeface.DEFAULT_BOLD; setTextColor(Saek.geom)
            background = GradientDrawable().apply { setColor(Saek.norang); cornerRadius = dp(12).toFloat() }
            setPadding(dp(22), dp(10), dp(22), dp(10))
            setOnClickListener { dl.dismiss() }
        }
        wi.addView(dat)
        tl.addView(wi, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        dl.setContentView(tl)
        dl.setOnDismissListener { keugeDialog = null; keugeView = null; keugeJul = null; geurimView?.let { gyeorugi(it) } }
        dl.setOnKeyListener { d, k, e -> if (k == KeyEvent.KEYCODE_BACK && e.action == KeyEvent.ACTION_UP) { d.dismiss(); true } else false }
        keugeDialog = dl; keugeView = iv; keugeJul = jul
        keugeGochigi()
        dl.show()
        dl.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        gyeorugi(dat)
    }

    private fun keugeGochigi() {
        val iv = keugeView ?: return
        val g = geurim
        if (g != null && iv.tag !== g) { iv.setImageBitmap(g); iv.tag = g }
        iv.gari = if (s.gariBoim) RectF(s.gx.toFloat(), s.gy.toFloat(), s.gw.toFloat(), s.gh.toFloat()) else null
        iv.contentDescription = "$geurimIreum, 크게 보기"
        keugeJul?.text = if (s.mode == 4) "프로그램 화면" else if (s.jang > 0) "${s.jang}장" else ""
        if (!deureoom) keugeDialog?.dismiss()
    }

    // ───── 도움말 — 찾기 칸, 다섯 개씩, 맨 위와 맨 아래 뒤로 단추 ─────
    private var doumalChatgiMal = ""
    private var doumalJjok = 0

    private fun doumalHwamyeon() {
        if (hwamyeon != Hwamyeon.DOUMAL) doumalDwi = hwamyeon
        hwamyeon = Hwamyeon.DOUMAL
        bueumBiugi()
        mom.removeAllViews()
        val wiDwiro = danchu("뒤로") { doumalNagagi() }
        mom.addView(wiDwiro)
        val chatgiCan = EditText(this).apply {
            hint = "도움말 찾기 (낱말을 넣고 엔터)"
            setHintTextColor(Saek.yeonhan)
            setTextColor(Saek.geul)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setText(doumalChatgiMal)
            contentDescription = "도움말 찾기"
        }
        chatgiCan.setOnEditorActionListener { v, _, _ -> doumalChatgiMal = v.text.toString(); doumalJjok = 0; sumgiKey(v); doumalMokrok(true); true }
        mom.addView(chatgiCan)
        mom.addView(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; tag = "mok" })
        mom.addView(danchu("뒤로") { doumalNagagi() })
        doumalMokrok(false)
        ttung.scrollTo(0, 0)
        gyeorugi(wiDwiro)
    }

    private fun doumalMokrok(gyeolgwa: Boolean) {
        val mok = mom.findViewWithTag<LinearLayout>("mok") ?: return
        mok.removeAllViews()
        val l = Doumal.chatgi(doumalChatgiMal)
        if (l.isEmpty()) {
            val t = geul("찾는 낱말이 든 도움말이 없습니다. 다른 낱말로 찾아보십시오.", 20f)
            mok.addView(t); if (gyeolgwa) gyeorugi(t); return
        }
        val sijak = minOf(doumalJjok * 5, (l.size - 1) / 5 * 5)
        var cheot: View? = null
        for (hang in l.subList(sijak, minOf(sijak + 5, l.size))) {
            val naeyong = geul(hang.naeyong, 19f).apply { visibility = View.GONE }
            val b = danchu(hang.jemok) {
                val yeol = naeyong.visibility != View.VISIBLE
                naeyong.visibility = if (yeol) View.VISIBLE else View.GONE
                if (yeol) gyeorugi(naeyong)
            }
            if (cheot == null) cheot = b
            mok.addView(b); mok.addView(naeyong)
        }
        if (sijak + 5 < l.size) mok.addView(danchu("더 보기") { doumalJjok += 1; doumalMokrok(true) })
        if (sijak > 0) mok.addView(danchu("이전 보기") { doumalJjok -= 1; doumalMokrok(true) })
        if (gyeolgwa) cheot?.let { gyeorugi(it) }
    }

    private fun doumalNagagi() {
        if (doumalDwi == Hwamyeon.SUEOP && deureoom) sueopHwamyeon() else cheotHwamyeon()
    }

    // ───── 뒤로 가기 — 앱 밖으로 나가지 않음 ─────
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { dwiro() }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { dwiro(); return true }
        return super.onKeyDown(keyCode, event)
    }

    private fun dwiro() {
        when (hwamyeon) {
            Hwamyeon.DOUMAL -> doumalNagagi()
            Hwamyeon.SUEOP -> nagalkka()
            Hwamyeon.CHEOT -> mom.announceForAccessibility("LVD 수업 첫 화면입니다. 앱을 닫으시려면 홈 단추를 누르십시오.")
        }
    }
}
