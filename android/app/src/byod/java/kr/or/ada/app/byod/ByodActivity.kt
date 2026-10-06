// BYOD 방송 — 첫 화면 (1.0.0판, 빌드 261003-B1, 이사장님 승인 2026-10-03)
// 첫 화면은 "BYOD 방송 시작" 단추 하나. 그 아래 상태 글은 바뀔 때만 한 번 알립니다(듣는 분 수는 10초에 한 번까지).
// 나머지(새로고침·프로그램 말소리·도움말·판 정보)는 "더 보기" 안에 둡니다.
// 뒤로 가기로 앱 밖에 나가지 않습니다. 도움말 화면은 맨 위와 맨 아래에 뒤로 단추를 둡니다.
package kr.or.ada.app.byod

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ByodActivity : Activity() {

    private object Saek {
        val bada = Color.parseColor("#12346E")   // 법인 짙은 남색
        val geul = Color.WHITE
        val norang = Color.parseColor("#FFCC00")
        val geom = Color.parseColor("#111111")
        val yeonhan = Color.parseColor("#DCE6F5")
    }

    private val h = Handler(Looper.getMainLooper())
    private lateinit var ttung: ScrollView
    private lateinit var mom: LinearLayout

    // 첫 화면 부품
    private lateinit var bangDanchu: Button
    private lateinit var sangtae: TextView
    private lateinit var jusoJemok: TextView
    private lateinit var juso: TextView
    private lateinit var yocheongGeul: TextView
    private lateinit var deoDanchu: Button
    private lateinit var deoAn: LinearLayout
    private lateinit var malsoriDanchu: Button

    private var tts: TextToSpeech? = null
    private var ttsDoem = false
    private var doumalYeollim = false
    private var meomchumDaegi = 0L          // 멈추기 한 번 누른 때
    private var boyeojunGeul = ""
    private var boyeojunTtae = 0L
    private var boyeojunKyeojim = false
    private var boyeojunSojae = ""
    private var boyeojunJalmot = ""
    private var boyeojunYocheong = -1
    private var sijakGidarim = false

    private val pref by lazy { getSharedPreferences("byod", Context.MODE_PRIVATE) }
    private val malsoriOn get() = pref.getBoolean("malsori", true)

    private val gwan: () -> Unit = { h.post { sangtaeGochigi(false) } }
    private val tik = object : Runnable {
        override fun run() { sangtaeGochigi(false); h.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "BYOD 방송"
        window.statusBarColor = Saek.bada
        window.navigationBarColor = Saek.bada
        ttung = ScrollView(this).apply { setBackgroundColor(Saek.bada); isFillViewport = true }
        mom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(20)
            setPadding(p, dp(28), p, dp(28))
        }
        ttung.addView(mom, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(ttung)
        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) { tts?.language = Locale.KOREAN; ttsDoem = true }
        }
        cheotHwamyeon()
        // 듣는 화면을 한 번도 받지 못했으면 조용히 받아 둠(인터넷이 될 때)
        if (Jaryo.pail(this, "deut.html") == null) Thread { Jaryo.batgi(applicationContext) }.start()
    }

    override fun onResume() {
        super.onResume()
        Bang.gwanchal.add(gwan)
        h.post(tik)
        sangtaeGochigi(true)
    }

    override fun onPause() {
        Bang.gwanchal.remove(gwan)
        h.removeCallbacks(tik)
        super.onPause()
    }

    override fun onDestroy() {
        try { tts?.shutdown() } catch (_: Exception) { }
        super.onDestroy()
    }

    // ───── 꾸밈 ─────
    private fun dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun danchu(geul: String, keun: Boolean = false, norang: Boolean = false, f: () -> Unit): Button {
        val b = Button(this)
        b.text = geul
        b.isAllCaps = false
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (keun) 28f else 20f)
        b.typeface = Typeface.DEFAULT_BOLD
        b.setTextColor(if (norang) Saek.geom else Saek.bada)
        b.background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(if (norang) Saek.norang else Saek.geul)
            setStroke(dp(3), if (norang) Saek.geom else Saek.norang)
        }
        val p = if (keun) dp(34) else dp(18)
        b.setPadding(dp(16), p, dp(16), p)
        b.minHeight = dp(if (keun) 140 else 64)
        b.setOnClickListener { f() }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(12); lp.bottomMargin = dp(6)
        b.layoutParams = lp
        return b
    }

    private fun geul(t: String, keugi: Float = 20f, gulgeum: Boolean = false): TextView {
        val v = TextView(this)
        v.text = t
        v.setTextColor(Saek.geul)
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
            if (v === bangDanchu || mom.indexOfChild(v) == 0) ttung.smoothScrollTo(0, 0)
            else v.requestRectangleOnScreen(android.graphics.Rect(0, 0, v.width, v.height))
        }, 250)
    }

    private fun malhagi(t: String) {
        if (!malsoriOn || !ttsDoem) return
        try { tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "byod") } catch (_: Exception) { }
    }

    // ───── 첫 화면 ─────
    private fun cheotHwamyeon() {
        doumalYeollim = false
        mom.removeAllViews()
        bangDanchu = danchu("BYOD 방송 시작", keun = true, norang = true) { bangDanchuNullim() }
        mom.addView(bangDanchu)

        sangtae = geul("", 22f, true)
        sangtae.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        mom.addView(sangtae)

        jusoJemok = geul("듣기 주소", 18f)
        juso = geul("", 22f, true).apply { setTextIsSelectable(true); setTextColor(Saek.norang) }
        mom.addView(jusoJemok)
        mom.addView(juso)

        yocheongGeul = geul("", 20f, true).apply { setTextColor(Saek.norang) }
        yocheongGeul.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        yocheongGeul.visibility = View.GONE
        mom.addView(yocheongGeul)

        deoDanchu = danchu("더 보기") { deoBogi(deoAn.visibility != View.VISIBLE) }
        mom.addView(deoDanchu)

        deoAn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        deoAn.addView(danchu("접속 도구: 엔에프시 스티커와 큐알코드") { startActivity(Intent(this, JeopsokActivity::class.java)) })   // 1.1.0 (261006-B2)
        deoAn.addView(danchu("새로고침") { saerogochim() })
        malsoriDanchu = danchu(if (malsoriOn) "프로그램 말소리 끄기" else "프로그램 말소리 켜기") { malsoriBakkugi() }
        deoAn.addView(malsoriDanchu)
        deoAn.addView(danchu("도움말") { doumalHwamyeon() })
        deoAn.addView(geul("BYOD 방송 " + Bang.PAN + "판, 빌드 " + Bang.BILD, 16f).apply { setTextColor(Saek.yeonhan) })
        deoAn.addView(geul(Jaryo.damgimMal(this), 16f).apply { setTextColor(Saek.yeonhan) })
        mom.addView(deoAn)

        boyeojunGeul = ""
        boyeojunYocheong = Bang.yocheongSeq
        sangtaeGochigi(true)
        gyeorugi(bangDanchu)
    }

    private fun deoBogi(yeolgi: Boolean) {
        deoAn.visibility = if (yeolgi) View.VISIBLE else View.GONE
        deoDanchu.text = if (yeolgi) "더 보기 접기" else "더 보기"
        if (yeolgi) gyeorugi(deoAn.getChildAt(0))
    }

    private fun malsoriBakkugi() {
        val on = !malsoriOn
        pref.edit().putBoolean("malsori", on).apply()
        malsoriDanchu.text = if (on) "프로그램 말소리 끄기" else "프로그램 말소리 켜기"
        malsoriDanchu.announceForAccessibility(if (on) "프로그램 말소리를 켰습니다." else "프로그램 말소리를 껐습니다.")
        if (on) malhagi("프로그램 말소리를 켰습니다.")
    }

    private fun saerogochim() {
        sangtae.text = "나스에서 듣는 화면과 대기 음악을 받고 있습니다."
        Thread {
            val r = Jaryo.batgi(applicationContext)
            h.post {
                if (!doumalYeollim) {
                    sangtae.text = r
                    boyeojunGeul = r
                    boyeojunTtae = System.currentTimeMillis()
                    malhagi(r)
                    // 담아 둔 상태 글 다시
                    val damgim = deoAn.getChildAt(deoAn.childCount - 1) as? TextView
                    damgim?.text = Jaryo.damgimMal(this)
                }
            }
        }.start()
    }

    // ───── 방송 단추 ─────
    private fun bangDanchuNullim() {
        if (Bang.kyeojim) {
            val now = System.currentTimeMillis()
            if (now - meomchumDaegi > 5000) {
                meomchumDaegi = now
                bangDanchu.text = "한 번 더 누르면 방송을 멈춥니다"
                bangDanchu.announceForAccessibility("한 번 더 누르면 방송을 멈춥니다.")
                malhagi("한 번 더 누르면 방송을 멈춥니다.")
                h.postDelayed({ if (Bang.kyeojim) bangDanchuGeul() }, 5000)
                return
            }
            meomchumDaegi = 0L
            ByodService.kkeugi(this)
            malhagi("방송을 멈추었습니다.")
            h.postDelayed({ sangtaeGochigi(true) }, 300)
            return
        }
        if (!heorakDoem()) { sijakGidarim = true; heorakMutgi(); return }
        sijakhagi()
    }

    private fun sijakhagi() {
        sijakGidarim = false
        baeteoriMutgi()
        ByodService.kyeogi(this)
        malhagi("방송을 시작했습니다.")
        h.postDelayed({ sangtaeGochigi(true) }, 600)
    }

    private fun bangDanchuGeul() {
        bangDanchu.text = if (Bang.kyeojim) "BYOD 방송 멈추기" else "BYOD 방송 시작"
    }

    private fun heorakDoem(): Boolean {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
        return true
    }

    private fun heorakMutgi() {
        val l = arrayListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(l.toTypedArray(), 7)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 7) return
        if (heorakDoem()) {
            if (sijakGidarim) sijakhagi()
        } else {
            sijakGidarim = false
            val t = "마이크 허락이 없어 방송을 시작하지 못했습니다. 다시 누르고 허용을 고르십시오."
            sangtae.text = t
            boyeojunGeul = t
            boyeojunTtae = System.currentTimeMillis()
            malhagi(t)
        }
    }

    // 배터리 아끼기가 방송을 잠재우지 않게 — 처음 한 번만 묻습니다
    private fun baeteoriMutgi() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) return
            if (pref.getBoolean("baeteori_mureum", false)) return
            pref.edit().putBoolean("baeteori_mureum", true).apply()
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (_: Exception) { }
    }

    // ───── 상태 글 — 바뀔 때만, 듣는 분 수만 바뀐 때는 10초에 한 번까지 ─────
    private fun sangtaeGochigi(baro: Boolean) {
        if (doumalYeollim) return
        val now = System.currentTimeMillis()
        if (now - meomchumDaegi > 5000) bangDanchuGeul()
        val k = Bang.kyeojim
        val so = Bang.sojae
        val jm = Bang.jalmot
        val t = if (!k) {
            "방송이 꺼져 있습니다." + (if (jm.isNotEmpty()) " " + jm else "")
        } else {
            "방송 중입니다. 듣는 분 " + Bang.deutnunSu + "명. 소리 받는 곳: " + so + "." + (if (jm.isNotEmpty()) " " + jm else "")
        }
        val keunBakkwim = k != boyeojunKyeojim || so != boyeojunSojae || jm != boyeojunJalmot
        if (t != boyeojunGeul && (baro || keunBakkwim || now - boyeojunTtae >= 10_000)) {
            sangtae.text = t
            boyeojunGeul = t
            boyeojunTtae = now
            if (keunBakkwim && !baro && jm.isNotEmpty() && jm != boyeojunJalmot) malhagi(jm)
            boyeojunKyeojim = k
            boyeojunSojae = so
            boyeojunJalmot = jm
        }
        // 듣기 주소 — 방송 중에만
        if (k) {
            val j = Bang.juso.ifEmpty { ByodService.jusoChatgi() }
            if (j.isEmpty()) {
                jusoJemok.visibility = View.VISIBLE
                if (juso.text.toString() != "공유기에 붙지 않았습니다. 와이파이나 랜선을 확인하십시오.") juso.text = "공유기에 붙지 않았습니다. 와이파이나 랜선을 확인하십시오."
            } else {
                Bang.juso = j
                jusoJemok.visibility = View.VISIBLE
                if (juso.text.toString() != j) juso.text = j
            }
            juso.visibility = View.VISIBLE
        } else {
            jusoJemok.visibility = View.GONE
            juso.visibility = View.GONE
        }
        // 도움 요청 — 새로 온 것만 한 번
        val seq = Bang.yocheongSeq
        if (seq != boyeojunYocheong) {
            boyeojunYocheong = seq
            val y = Bang.yocheong.lastOrNull()
            if (y != null && k) {
                val si = SimpleDateFormat("a h시 m분", Locale.KOREAN).format(Date(y.ttae))
                val bh = if (y.beonho.isEmpty()) "번호 없음" else y.beonho + "번"
                val g = "새 알림: " + bh + ", " + Bang.jongryuMal(y.jongryu) + " (" + si + ")"
                yocheongGeul.text = g
                yocheongGeul.visibility = View.VISIBLE
                malhagi(g)
            }
        }
    }

    // ───── 도움말 화면 ─────
    private var doumalChatgiMal = ""
    private var doumalJjok = 0

    private fun doumalHwamyeon() {
        doumalYeollim = true
        mom.removeAllViews()
        val wiDwiro = danchu("뒤로") { cheotHwamyeon() }
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
        chatgiCan.setOnEditorActionListener { v, _, _ ->
            doumalChatgiMal = v.text.toString()
            doumalJjok = 0
            doumalMokrok(true)
            true
        }
        mom.addView(chatgiCan)
        val mok = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; tag = "mok" }
        mom.addView(mok)
        mom.addView(danchu("뒤로") { cheotHwamyeon() })
        doumalMokrok(false)
        gyeorugi(wiDwiro)
    }

    // 다섯 개씩 — 아래에 더 보기, 그 아래 이전 보기
    private fun doumalMokrok(gyeolgwa: Boolean) {
        val mok = mom.findViewWithTag<LinearLayout>("mok") ?: return
        mok.removeAllViews()
        val l = Doumal.chatgi(doumalChatgiMal)
        if (l.isEmpty()) {
            val t = geul("찾는 낱말이 든 도움말이 없습니다. 다른 낱말로 찾아보십시오.", 20f)
            mok.addView(t)
            if (gyeolgwa) gyeorugi(t)
            return
        }
        val sijak = doumalJjok * 5
        val jjok = l.subList(sijak, minOf(sijak + 5, l.size))
        var cheot: View? = null
        for (hang in jjok) {
            val naeyong = geul(hang.naeyong, 19f).apply { visibility = View.GONE }
            val b = danchu(hang.jemok) {
                val yeolgi = naeyong.visibility != View.VISIBLE
                naeyong.visibility = if (yeolgi) View.VISIBLE else View.GONE
                if (yeolgi) gyeorugi(naeyong)
            }
            if (cheot == null) cheot = b
            mok.addView(b)
            mok.addView(naeyong)
        }
        if (sijak + 5 < l.size) mok.addView(danchu("더 보기") { doumalJjok++; doumalMokrok(true) })
        if (doumalJjok > 0) mok.addView(danchu("이전 보기") { doumalJjok--; doumalMokrok(true) })
        if (gyeolgwa && cheot != null) gyeorugi(cheot)
    }

    // ───── 뒤로 가기 — 앱 밖으로 나가지 않음 ─────
    @Deprecated("옛 뒤로 가기 받기")
    override fun onBackPressed() {
        dwiro()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { dwiro(); return true }
        return super.onKeyDown(keyCode, event)
    }

    private fun dwiro() {
        when {
            doumalYeollim -> cheotHwamyeon()
            ::deoAn.isInitialized && deoAn.visibility == View.VISIBLE -> { deoBogi(false); gyeorugi(deoDanchu) }
            else -> {
                bangDanchu.announceForAccessibility("첫 화면입니다. 앱을 닫아도 방송은 이어집니다.")
            }
        }
    }
}
