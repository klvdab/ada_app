// LVD 방송(듣는 앱) — 첫 화면 (2.2.0판, 빌드 261018-L3, 방클, 이사장님 승인 「1」 — LVD 방송 정리안)
// 이사장님 뜻: 듣는 분께는 들을 것만. 방송 방식·공유기 같은 말은 어디에도 보이지 않게.
// 첫 화면 차례(2.2.0): 협회 로고와 이름 → 「지금 현장영상해설 듣기」(큰 단추, 커서, 행사장이면 현장 방송·아니면 인터넷 방송) → 상태 한 줄(바뀔 때만 알림)
//   → (인터넷 방송이 여럿이면 고르기) → TV해설방송 듣기 → 다시 듣기 → 「현장영상해설사 도움 요청」(행사장에서) → 새 판 → (처음 한 번) 저절로 들리게 하기 → 더 보기
// 더 보기 안: 방송 알림 받기, 인터넷 방송 목록(누리집), 그 밖에 들을 거리(TV방송·라디오방송), 저절로 듣기 켜고 끄기, 와이파이 저절로 붙기 다시 하기,
//   프로그램 말소리, 도움말, 새로고침, 업데이트, 판 정보.
// 행사장에 들어서면(행사장 와이파이에 붙으면) 앱을 열기만 해도 곧바로 들려줍니다. 뒤로 가기로 앱 밖에 나가지 않습니다.
package kr.or.ada.app.bfb

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.util.TypedValue
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
import java.util.Locale

class BangsongActivity : Activity() {

    companion object {
        const val PAN = "2.2.0"   // 2.2.0(261018-L3) LVD 방송 정리 — 지금 현장영상해설 듣기 하나로(행사장이면 현장 방송, 아니면 인터넷 방송을 앱 안에서), TV해설방송 듣기·다시 듣기 첫 화면, 방송 알림 받기, 그 밖에 들을 거리(방클, 이사장님 승인 「1」). 2.1.0(261016-L2) 더 보기에 TV해설방송 듣기·TV방송·라디오방송, 「인터넷 생방송 듣기」로 이름 줄임, 앱 안 누리집 화면의 겹친 뒤로 감춤(방클, 이사장님 승인 「1」). 2.0.0(261015-L1) LVD 방송 첫 판(배프 웹 껍데기 1.2.1의 뒤를 이음) — 배프 웹 껍데기를 속까지 앱으로: 행사장 와이파이 저절로 붙기와 저절로 듣기, 도움 요청, 화면 잠가도 듣기, 통화 때 멈춤, 벗어나면 알림, 같은 폰 LVD 송출과 부딪치지 않기, 인터넷 생방송·다시 듣기, 도움말, 새 판 알림(방송클, 이사장님 승인 「1」)
        const val BILD = "261018-L3"
        const val BAEP = "https://lvd.ada.or.kr/bfblive/"
    }

    private object Saek {
        val bada = Color.parseColor("#12346E")
        val geul = Color.WHITE
        val norang = Color.parseColor("#FFCC00")
        val geom = Color.parseColor("#111111")
        val yeonhan = Color.parseColor("#DCE6F5")
    }

    private val h = Handler(Looper.getMainLooper())
    private lateinit var ttung: ScrollView
    private lateinit var mom: LinearLayout
    private lateinit var deutDanchu: Button
    private lateinit var sangtae: TextView
    private lateinit var yocheongAn: LinearLayout
    private lateinit var deoDanchu: Button
    private lateinit var deoAn: LinearLayout
    private lateinit var gorgiAn: LinearLayout   // 2.2.0 인터넷 방송 고르기
    private lateinit var gitaAn: LinearLayout    // 2.2.0 그 밖에 들을 거리
    private var boyeojunGorgi = ""
    private var malsoriDanchu: Button? = null
    private var jeoljeoloDanchu: Button? = null

    private var tts: TextToSpeech? = null
    private var ttsDoem = false
    private var doumalYeollim = false
    private var boyeojunGeul = ""
    private var salpimJung = false

    private val pref by lazy { getSharedPreferences("lvd_bangsong", Context.MODE_PRIVATE) }
    private val malsoriOn get() = pref.getBoolean("malsori", true)
    private val jeoljeoloOn get() = pref.getBoolean("jeoljeolo_deutgi", true)

    private val gwan: () -> Unit = { h.post { sangtaeGochigi() } }
    private val tik = object : Runnable {
        override fun run() {
            // 앱이 열려 있는 동안 3초마다 — 행사장에 들어섰는데 듣기가 꺼져 있으면 저절로 켬
            if (!Deut.kyeojim && jeoljeoloOn && !salpimJung && !doumalYeollim) hyeonjangSalpigi(false)
            h.postDelayed(this, 3000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "LVD 방송"
        window.statusBarColor = Saek.bada
        window.navigationBarColor = Saek.bada
        ttung = ScrollView(this).apply { setBackgroundColor(Saek.bada); isFillViewport = true }
        mom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(24), dp(20), dp(28)) }
        ttung.addView(mom, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(ttung)
        tts = TextToSpeech(this) { st -> if (st == TextToSpeech.SUCCESS) { tts?.language = Locale.KOREAN; ttsDoem = true } }
        Deut.mal = { m -> h.post { allim(m) } }
        cheotHwamyeon()
        Ollim.malhagi = { m -> allim(m) }
        Ollim.makgi = null
        Ollim.byeonhwa = { if (!doumalYeollim) cheotHwamyeon() }
        Ollim.sijak(this)
        Ollim.intentBoda(this, intent)
        if (intent?.getBooleanExtra("baro_deutgi", false) == true) DeutService.kyeogi(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Ollim.intentBoda(this, intent)
        if (intent.getBooleanExtra("baro_deutgi", false)) DeutService.kyeogi(this)
    }

    override fun onResume() {
        super.onResume()
        Deut.gwanchal.add(gwan)
        sangtaeGochigi()
        h.post(tik)
        Ollim.dorawatda(this)
    }

    override fun onPause() {
        Deut.gwanchal.remove(gwan)
        h.removeCallbacks(tik)
        super.onPause()
    }

    override fun onDestroy() {
        try { tts?.shutdown() } catch (_: Exception) { }
        Deut.mal = null
        Ollim.byeonhwa = null; Ollim.malhagi = null
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
            v.requestRectangleOnScreen(android.graphics.Rect(0, 0, v.width, v.height))
        }, 250)
    }

    /** 한 번 알릴 말 — 프로그램 말소리가 켜져 있으면 말로, 아니면 낭독기에 */
    private fun allim(t: String) {
        if (malsoriOn && ttsDoem) { try { tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "lvd") } catch (_: Exception) { } }
        else if (::mom.isInitialized) mom.announceForAccessibility(t)
    }

    // ───── 첫 화면 ─────
    private fun cheotHwamyeon() {
        doumalYeollim = false
        mom.removeAllViews()
        mom.addView(HyeophoeLogo.meori(this, "LVD 방송"))
        deutDanchu = danchu("지금 현장영상해설 듣기", keun = true, norang = true) { deutDanchuNullim() }
        mom.addView(deutDanchu)
        sangtae = geul("", 22f, true)
        sangtae.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_NONE   // 바뀔 때 한 번은 allim 이 맡음
        mom.addView(sangtae)
        gorgiAn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }   // 2.2.0
        mom.addView(gorgiAn)
        boyeojunGorgi = ""
        mom.addView(danchu("TV해설방송 듣기") { webYeolgi("TV해설방송 듣기", "https://lvd.ada.or.kr/lvds/haeseol_deutgi.html") })   // 2.2.0 첫 화면으로
        mom.addView(danchu("다시 듣기") { webYeolgi("다시 듣기", BAEP + "dasi.html") })   // 2.2.0 첫 화면으로

        mom.addView(danchu("현장영상해설사 도움 요청 (행사장에서)") { yocheongPyeolchigi() })
        yocheongAn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        yocheongAn.addView(danchu("도와주세요 (현장영상해설사가 찾아갑니다)") { yocheongBonaegi("dowum") })
        yocheongAn.addView(danchu("소리가 끊겨요") { yocheongBonaegi("kkeunkim") })
        yocheongAn.addView(danchu("궁금한 것이 있어요") { yocheongBonaegi("gungeum") })
        mom.addView(yocheongAn)

        Ollim.julMal(this)?.let { m -> mom.addView(danchu(m) { Ollim.olligi(this) }) }   // 새 판이 있을 때만

        if (!Hyeonjang.deungrokDoem(this)) {   // 처음 한 번 — 안내와 단추를 한 자리에
            mom.addView(danchu("행사장에 들어서면 저절로 들리게 하기 (처음 한 번 허락)") { cheoeumHeorak() })
        }

        deoDanchu = danchu("더 보기") { deoBogi(deoAn.visibility != View.VISIBLE) }
        mom.addView(deoDanchu)
        deoAn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        deoAn.addView(danchu("방송 알림 받기") { webYeolgi("방송 알림 받기", BAEP + "deutgi2.html#allim") })   // 2.2.0
        deoAn.addView(danchu("인터넷 방송 목록 (누리집 화면)") { webYeolgi("인터넷 방송 목록", BAEP + "deutgi2.html") })
        gitaAn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }   // 2.2.0 그 밖에 들을 거리
        var gitaDanchu: Button? = null
        gitaDanchu = danchu("그 밖에 들을 거리 — TV방송과 라디오방송") {
            val yeol = gitaAn.visibility != View.VISIBLE
            gitaAn.visibility = if (yeol) View.VISIBLE else View.GONE
            gitaDanchu?.text = if (yeol) "그 밖에 들을 거리 접기" else "그 밖에 들을 거리 — TV방송과 라디오방송"
            if (yeol) gyeorugi(gitaAn.getChildAt(0))
        }
        deoAn.addView(gitaDanchu)
        gitaAn.addView(danchu("TV방송") { webYeolgi("TV방송", BAEP + "chae.html?kind=tv") })
        gitaAn.addView(danchu("라디오방송") { webYeolgi("라디오방송", BAEP + "chae.html?kind=radio") })
        deoAn.addView(gitaAn)
        jeoljeoloDanchu = danchu(jeoljeoloMal()) { jeoljeoloBakkugi() }
        deoAn.addView(jeoljeoloDanchu)
        deoAn.addView(danchu("행사장 와이파이 저절로 붙기 다시 하기") { cheoeumHeorak() })
        malsoriDanchu = danchu(if (malsoriOn) "프로그램 말소리 끄기" else "프로그램 말소리 켜기") { malsoriBakkugi() }
        deoAn.addView(malsoriDanchu)
        deoAn.addView(danchu("도움말") { doumalHwamyeon() })
        deoAn.addView(danchu("새로고침") { saerogochim() })
        deoAn.addView(danchu(Ollim.seoljeongMal(this)) { Ollim.seoljeongNureum(this) })
        deoAn.addView(geul("LVD 방송 " + PAN + "판, 빌드 " + BILD + ". 사단법인 한국시각장애인현장영상해설협회", 16f).apply { setTextColor(Saek.yeonhan) })
        mom.addView(deoAn)

        boyeojunGeul = ""
        sangtaeGochigi()
        gyeorugi(deutDanchu)
    }

    private fun deoBogi(yeolgi: Boolean) {
        deoAn.visibility = if (yeolgi) View.VISIBLE else View.GONE
        deoDanchu.text = if (yeolgi) "더 보기 접기" else "더 보기"
        if (yeolgi) gyeorugi(deoAn.getChildAt(0))
    }

    private fun sangtaeGochigi() {
        if (doumalYeollim || !::sangtae.isInitialized) return
        deutDanchu.text = if (Deut.kyeojim) "듣기 멈추기" else "지금 현장영상해설 듣기"
        val t = Deut.sangtae
        if (t != boyeojunGeul) { sangtae.text = t; boyeojunGeul = t }
        // 2.2.0 인터넷 방송 고르기 — 목록이 바뀔 때만 다시 그림
        val g = Deut.gorgi
        val gk = g.joinToString(",") { it.sid }
        if (::gorgiAn.isInitialized && gk != boyeojunGorgi) {
            boyeojunGorgi = gk
            gorgiAn.removeAllViews()
            for (b in g) gorgiAn.addView(danchu((if (b.jemok.isEmpty()) "제목 없는 방송" else b.jemok) + (if (b.who.isEmpty()) "" else ", 현장영상해설사 " + b.who) + ", " + maxOf(1, b.cho / 60) + "분째") {
                Deut.goreun = b.sid
                allim("고르셨습니다. 곧 들려 드립니다.")
            })
        }
    }

    // ───── 듣기 ─────
    private fun deutDanchuNullim() {
        if (Deut.kyeojim) { Deut.sonMeomchum = true; DeutService.kkeugi(this); allim("듣기를 멈추었습니다. 다시 들으시려면 같은 단추를 누르십시오."); return }
        Deut.sonMeomchum = false
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 3)
        DeutService.kyeogi(this)
        allim("현장영상해설을 찾고 있습니다. 행사장이면 현장 방송을, 아니면 인터넷 방송을 들려 드립니다.")
    }

    /** 행사장인지 살펴, 행사장이면 듣기를 켬(손으로 누를 때와 같은 일) */
    private fun hyeonjangSalpigi(baro: Boolean) {
        salpimJung = true
        Thread {
            val jm = Hyeonjang.salpigi(applicationContext)
            h.post {
                salpimJung = false
                if (jm == null) Deut.sonMeomchum = false   // 행사장을 벗어나면 「손으로 멈춤」을 잊음
                else if (baro) Deut.sonMeomchum = false
                if (jm != null && !Deut.kyeojim && !Deut.sonMeomchum && (jeoljeoloOn || baro)) DeutService.kyeogi(this)
            }
        }.start()
    }

    // ───── 도움 요청 ─────
    private fun yocheongPyeolchigi() {
        val yeolgi = yocheongAn.visibility != View.VISIBLE
        yocheongAn.visibility = if (yeolgi) View.VISIBLE else View.GONE
        if (yeolgi) gyeorugi(yocheongAn.getChildAt(0))
    }

    private fun yocheongBonaegi(k: String) {
        Thread {
            val ok = Hyeonjang.yocheong(applicationContext, k)
            h.post {
                allim(if (ok) "알렸습니다. 현장영상해설사가 곧 찾아갑니다." else "현장영상해설사에게 알리지 못했습니다. 도움 요청은 행사장 안에서만 됩니다. 잠시 뒤 다시 눌러 주십시오.")
            }
        }.start()
    }

    // ───── 처음 한 번 허락 — 알림·위치(도착 알아채기)·와이파이 저절로 붙기 ─────
    private fun cheoeumHeorak() {
        val l = arrayListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) l.add(Manifest.permission.POST_NOTIFICATIONS)
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) l.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (l.isNotEmpty()) {
            allim("허락을 묻는 창이 뜹니다. 위치는 행사장에 들어선 것을 알아채는 데에만 씁니다. 허용을 눌러 주십시오.")
            requestPermissions(l.toTypedArray(), 5)
        } else deungrokMachim()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 5) deungrokMachim()
    }

    private fun deungrokMachim() {
        val m = Hyeonjang.deungrok(this)
        allim(m)
        if (!doumalYeollim) cheotHwamyeon()
        sangtae.text = m; boyeojunGeul = m
    }

    // ───── 더 보기 ─────
    private fun jeoljeoloMal() = if (jeoljeoloOn) "행사장에 들어서면 저절로 듣기: 켜짐. 누르면 끕니다" else "행사장에 들어서면 저절로 듣기: 꺼짐. 누르면 켭니다"

    private fun jeoljeoloBakkugi() {
        pref.edit().putBoolean("jeoljeolo_deutgi", !jeoljeoloOn).apply()
        jeoljeoloDanchu?.text = jeoljeoloMal()
        allim(if (jeoljeoloOn) "행사장에 들어서면 저절로 들려 드립니다." else "저절로 듣기를 껐습니다. 행사장에서는 듣기 단추를 눌러 주십시오.")
    }

    private fun malsoriBakkugi() {
        val on = !malsoriOn
        pref.edit().putBoolean("malsori", on).apply()
        malsoriDanchu?.text = if (on) "프로그램 말소리 끄기" else "프로그램 말소리 켜기"
        allim(if (on) "프로그램 말소리를 켰습니다." else "프로그램 말소리를 껐습니다.")
    }

    private fun saerogochim() {
        allim("새로고침합니다. 행사장인지 다시 살피고, 새 판이 있는지 살핍니다.")
        Ollim.sijak(this)
        hyeonjangSalpigi(true)
    }

    private fun webYeolgi(jemok: String, juso: String) {
        startActivity(Intent(this, WebHwamyeon::class.java).putExtra("jemok", jemok).putExtra("juso", juso))
    }

    // ───── 도움말 — 찾기 칸, 다섯 개씩, 위아래 뒤로 ─────
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
        chatgiCan.setOnEditorActionListener { v, _, _ -> doumalChatgiMal = v.text.toString(); doumalJjok = 0; doumalMokrok(true); true }
        mom.addView(chatgiCan)
        mom.addView(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; tag = "mok" })
        mom.addView(danchu("뒤로") { cheotHwamyeon() })
        doumalMokrok(false)
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
        val sijak = doumalJjok * 5
        var cheot: View? = null
        for (hang in l.subList(sijak, minOf(sijak + 5, l.size))) {
            val naeyong = geul(hang.naeyong, 19f).apply { visibility = View.GONE }
            val b = danchu(hang.jemok) {
                val yeolgi = naeyong.visibility != View.VISIBLE
                naeyong.visibility = if (yeolgi) View.VISIBLE else View.GONE
                if (yeolgi) gyeorugi(naeyong)
            }
            if (cheot == null) cheot = b
            mok.addView(b); mok.addView(naeyong)
        }
        if (sijak + 5 < l.size) mok.addView(danchu("더 보기") { doumalJjok++; doumalMokrok(true) })
        if (doumalJjok > 0) mok.addView(danchu("이전 보기") { doumalJjok--; doumalMokrok(true) })
        if (gyeolgwa && cheot != null) gyeorugi(cheot)
    }

    // ───── 뒤로 가기 — 앱 밖으로 나가지 않음 ─────
    @Deprecated("옛 뒤로 가기 받기")
    override fun onBackPressed() { dwiro() }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { dwiro(); return true }
        return super.onKeyDown(keyCode, event)
    }

    private fun dwiro() {
        when {
            doumalYeollim -> cheotHwamyeon()
            ::deoAn.isInitialized && deoAn.visibility == View.VISIBLE -> { deoBogi(false); gyeorugi(deoDanchu) }
            ::yocheongAn.isInitialized && yocheongAn.visibility == View.VISIBLE -> { yocheongAn.visibility = View.GONE; gyeorugi(deutDanchu) }
            else -> mom.announceForAccessibility("첫 화면입니다. 앱을 닫아도 듣기는 이어집니다.")
        }
    }
}
