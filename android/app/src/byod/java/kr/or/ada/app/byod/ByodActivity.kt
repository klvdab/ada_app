// BYOD 방송 — 첫 화면 (1.0.0판, 빌드 261003-B1, 이사장님 승인 2026-10-03)
// 첫 화면(1.5.0 차례): 행사 이름·현장영상해설사 이름·전화번호, "현장 방송 시작"(인터넷 방송도 함께), 상태 줄·인터넷 줄, 현장 점검, 인터넷 방송만 하기, TV해설방송 하기, 교육 녹화, 더 보기. 그 아래 상태 글은 바뀔 때만 한 번 알립니다(듣는 분 수는 10초에 한 번까지).
// 나머지(새로고침·프로그램 말소리·도움말·판 정보)는 "더 보기" 안에 둡니다.
// 1.2.0 (261014-B9, 이사장님 승인 「1」, 방송클) 플린트 2 방식이 기본 — 더 보기에 플린트 2 준비하기와 방송 방식 바꾸기
// 1.3.0 (261014-B10, 이사장님 승인 「1」, 방송클) 행사 이름·현장영상해설사 칸, 방송을 멈추면 녹음과 결과보고를 받아 배프에 올림, 더 보기에 지난 행사 기록
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
    private lateinit var bangsikDanchu: Button   // 1.2.0
    private lateinit var inteonetGeul: TextView   // 1.5.0 함께 나가는 인터넷 방송 한 줄
    private lateinit var hamkkeDanchu: Button    // 1.5.0
    private lateinit var keugiDanchu: Button     // 1.9.2 소리 자동 맞춤
    private lateinit var opusDanchu: Button      // 1.10.0 소리 방식 오푸스(시험)
    private var boyeojunInteonet = ""
    // 1.9.0 (261024-B17) 무대 영상 녹화 — 켜고 끄는 단추 하나와 상태 한 줄
    private lateinit var yeongsangDanchu: Button
    private lateinit var yeongsangGeul: TextView
    private var boyeojunYeongsang = "\u0000"
    private var boyeojunYeongsangMunje = ""
    // 1.9.0 (261024-B17) 현장 기능 — 긴급 안내 한 줄(바뀔 때만 한 번 알림), 더 보기 안의 피디 번호 한 줄
    private lateinit var ginGeul: TextView
    private lateinit var pdGeul: TextView
    private var boyeojunGin = -1
    private var boyeojunPd = "\u0000"

    private var tts: TextToSpeech? = null
    private var ttsDoem = false
    private var doumalYeollim = false
    private var meomchumDaegi = 0L          // 멈추기 한 번 누른 때
    private var boyeojunGeul = ""
    private var boyeojunTtae = 0L
    private var boyeojunKyeojim = false
    private var boyeojunSojae = ""
    private var boyeojunJalmot = ""
    private var boyeojunTonghwa = false   // 1.3.1 통화 중 알림을 바뀔 때 한 번
    private var boyeojunYocheong = -1
    private var sijakGidarim = false

    private val pref by lazy { getSharedPreferences("byod", Context.MODE_PRIVATE) }
    private val malsoriOn get() = pref.getBoolean("malsori", true)

    private val gwan: () -> Unit = { h.post { sangtaeGochigi(false) } }
    private var tikSu = 0
    private val tik = object : Runnable {
        override fun run() {
            sangtaeGochigi(false)
            // 1.3.0 배프에 아직 못 올린 행사가 있으면 1분마다 다시 올려 봄
            if (++tikSu % 60 == 0 && !Bang.kyeojim && !Haengsa.dolgo) Thread { if (Haengsa.nameum(this@ByodActivity) || GamchumDaegi.mokrok(this@ByodActivity).isNotEmpty() || MudaeNokhwa.nameum(this@ByodActivity)) Haengsa.sijak(this@ByodActivity, false) }.start()   // 1.5.0 감출 인터넷 녹음도, 1.9.0 (261024-B17) 무대 영상 녹화 조각도
            if (tikSu % 60 == 30 && !Bang.kyeojim) { val app = applicationContext; Thread { if (HyeonjangSeobeo.galNameum(app)) HyeonjangSeobeo.galOlligiDwi(app) }.start() }   // 1.9.0 (261024-B17) 갈래 2·3 녹음도 1분마다 다시 올려 봄
            h.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Girok.sijak(applicationContext)   // 1.8.0 이용 기록
        title = "LVD 송출"
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
        if (Jaryo.pail(this, "deut.html") == null || Jaryo.pail(this, "pd.html") == null || Jaryo.pail(this, "bang.html") == null) Thread { Jaryo.batgi(applicationContext) }.start()   // 1.9.0 피디·해설 화면도
        // 1.1.2 새 판 알림과 업데이트(이사장님 지시 — 모든 앱에). 방송 중에는 설치하면 방송이 끊기므로 막음
        Ollim.malhagi = { m -> if (malsoriOn && ttsDoem) malhagi(m) else mom.announceForAccessibility(m) }
        Ollim.makgi = { if (Bang.kyeojim) "방송 중에는 업데이트하지 않습니다. 방송을 멈춘 뒤 업데이트를 눌러 주십시오." else null }
        Ollim.byeonhwa = { if (!doumalYeollim) cheotHwamyeon() }
        Ollim.sijak(this)
        Ollim.intentBoda(this, intent)
        Haengsa.allim = { m -> h.post { if (malsoriOn && ttsDoem) malhagi(m) else mom.announceForAccessibility(m) } }   // 1.3.0
    }

    override fun onNewIntent(intent: Intent) {   // 1.1.2 새 판 알림을 두드렸을 때
        super.onNewIntent(intent)
        setIntent(intent)
        Ollim.intentBoda(this, intent)
    }

    override fun onResume() {
        super.onResume()
        Bang.gwanchal.add(gwan)
        h.post(tik)
        sangtaeGochigi(true)
        Ollim.dorawatda(this)   // 1.1.2 돌아오면 새 판 살피기(1시간에 한 번까지)
    }

    override fun onPause() {
        Bang.gwanchal.remove(gwan)
        h.removeCallbacks(tik)
        super.onPause()
    }

    override fun onDestroy() {
        try { tts?.shutdown() } catch (_: Exception) { }
        Ollim.byeonhwa = null; Ollim.malhagi = null; Ollim.makgi = null
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
        mom.addView(HyeophoeLogo.meori(this, "LVD 송출"))   // 1.9.0 (261024-B17) 협회 로고(대조표 송출 17번)
        // 1.3.0 행사 이름과 현장영상해설사(녹음 이름·다시듣기 제목·결과보고에 쓰임, 한 번 넣으면 기억), 1.5.0 두 방식 모두·전화번호(인터넷 방송)
        mom.addView(kanMandeul("행사 이름 (비워 두면 날짜와 시각)", "haengsa_jemok"))
        mom.addView(kanMandeul("현장영상해설사 이름", "haeseolsa"))
        mom.addView(kanMandeul("현장영상해설사 전화번호 (인터넷 방송에 꼭 필요합니다)", "haeseolsa_phone").apply { inputType = InputType.TYPE_CLASS_PHONE })
        // 1.9.0 (261024-B17) 공유기는 방송을 시작할 때 앱이 스스로 알아냄 — 고르기 단추는 더 보기로(직접 고를 때만)
        bangsikDanchu = danchu(bangsikMal()) { bangsikBakkugi() }
        bangDanchu = danchu("BYOD 방송 시작", keun = true, norang = true) { bangDanchuNullim() }
        yeongsangDanchu = danchu(yeongsangMal()) { yeongsangBakkugi() }   // 1.9.0 (261024-B17) 무대 영상 녹화 — 방송 시작 바로 위
        mom.addView(yeongsangDanchu)
        mom.addView(bangDanchu)

        sangtae = geul("", 22f, true)
        sangtae.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        mom.addView(sangtae)
        inteonetGeul = geul("", 20f)   // 1.5.0 — 바뀔 때만 한 번 알림(sangtaeGochigi)
        inteonetGeul.visibility = View.GONE
        mom.addView(inteonetGeul)
        yeongsangGeul = geul("", 20f)   // 1.9.0 (261024-B17) 무대 영상 녹화 한 줄 — 분 수는 알리지 않고, 문제만 바뀔 때 한 번 알림(sangtaeGochigi)
        yeongsangGeul.visibility = View.GONE
        mom.addView(yeongsangGeul)
        boyeojunYeongsang = "\u0000"

        mom.addView(danchu("현장 점검") { hyeonjangJeomgeom() })   // 1.1.5 (261009-B7) 방송 전 살필 것을 단추 하나로
        mom.addView(danchu("피디 화면 열기") { pdYeolgi() })   // 1.9.0 (261024-B17) 노트북판 피디 화면을 이 기기 브라우저로
        mom.addView(danchu("노트북에서 피디 화면 열기 큐알") { pdQrBogi() })   // 1.11.0 (261028-B21) 대형 화면을 띄울 노트북이 같은 공유기에서 피디 화면을 엶
        mom.addView(danchu("관계자 붙이기 큐알 보기") { gwanQrBogi() })   // 1.9.0 진행요원·현장영상해설사·무대 카메라가 찍고 붙음
        mom.addView(danchu("인터넷 방송만 하기 (공유기 없이)") { startActivity(Intent(this, GyoyukActivity::class.java).putExtra("juso", GyoyukActivity.LIVE).putExtra("jemok", "인터넷 방송")) })   // 1.4.0 (261016-B12) 한 폰 한 화면에서, 1.5.0 이름
        mom.addView(danchu("TV해설방송 하기") { startActivity(Intent(this, GyoyukActivity::class.java).putExtra("juso", GyoyukActivity.TV).putExtra("jemok", "TV해설방송")) })   // 1.5.0
        mom.addView(danchu("현장영상해설교육 녹화") { startActivity(Intent(this, GyoyukActivity::class.java)) })   // 1.4.0 (261016-B12) 교육 녹화를 앱 안에서
        Ollim.julMal(this)?.let { m -> mom.addView(danchu(m) { Ollim.olligi(this) }) }   // 1.1.2 새 판이 있을 때만 — 안내와 단추를 한 자리에

        jusoJemok = geul("듣기 주소", 18f)
        juso = geul("", 22f, true).apply { setTextIsSelectable(true); setTextColor(Saek.norang) }
        mom.addView(jusoJemok)
        mom.addView(juso)

        yocheongGeul = geul("", 20f, true).apply { setTextColor(Saek.norang) }
        yocheongGeul.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        yocheongGeul.visibility = View.GONE
        mom.addView(yocheongGeul)
        ginGeul = geul("", 20f, true).apply { setTextColor(Saek.norang) }   // 1.9.0 긴급 안내 — 바뀔 때만 한 번 알림
        ginGeul.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        ginGeul.visibility = View.GONE
        mom.addView(ginGeul)

        deoDanchu = danchu("더 보기") { deoBogi(deoAn.visibility != View.VISIBLE) }
        mom.addView(deoDanchu)

        deoAn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        deoAn.addView(bangsikDanchu)   // 1.9.0 공유기 직접 고르기(보통은 앱이 스스로 알아냄)
        deoAn.addView(danchu("접속 도구: 엔에프시 태그와 큐알코드") { startActivity(Intent(this, JeopsokActivity::class.java)) })   // 1.1.0 (261006-B2)
        deoAn.addView(danchu("지난 행사 기록 (녹음과 결과보고)") { haengsaHwamyeon() })   // 1.3.0
        deoAn.addView(danchu("플린트 2 준비하기 (공유기 처음 한 번)") { startActivity(Intent(this, FlintJunbiActivity::class.java)) })   // 1.2.0
        hamkkeDanchu = danchu(hamkkeMal()) { hamkkeBakkugi() }   // 1.5.0
        deoAn.addView(hamkkeDanchu)
        keugiDanchu = danchu(keugiMal()) { keugiBakkugi() }   // 1.9.2
        deoAn.addView(keugiDanchu)
        opusDanchu = danchu(opusMal()) { opusBakkugi() }   // 1.10.0 (261026-B20)
        deoAn.addView(opusDanchu)
        deoAn.addView(danchu("기기 점검: 소리 장치와 연결 보기") { val t = gigiJeomgeom(this); android.app.AlertDialog.Builder(this).setTitle("기기 점검").setMessage(t).setPositiveButton("닫기", null).setNeutralButton("결과 복사") { _, _ -> try { (getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("기기 점검", t)); malhagi("기기 점검 결과를 복사했습니다.") } catch (_: Exception) { } }.show() })   // 1.1.3 (261007-B5), 1.1.4 결과 복사
        deoAn.addView(danchu("새로고침") { saerogochim() })
        deoAn.addView(danchu(Ollim.seoljeongMal(this)) { Ollim.seoljeongNureum(this) })   // 1.1.2 업데이트 — 새로고침 바로 아래 한 곳
        malsoriDanchu = danchu(if (malsoriOn) "프로그램 말소리 끄기" else "프로그램 말소리 켜기") { malsoriBakkugi() }
        deoAn.addView(malsoriDanchu)
        deoAn.addView(danchu("도움말") { doumalHwamyeon() })
        pdGeul = geul(pdMal(), 18f).apply { setTextColor(Saek.yeonhan) }   // 1.9.0 피디 번호 한 줄
        deoAn.addView(pdGeul)
        deoAn.addView(geul("LVD 송출 " + Bang.PAN + "판, 빌드 " + Bang.BILD, 16f).apply { setTextColor(Saek.yeonhan) })
        deoAn.addView(geul(Jaryo.damgimMal(this), 16f).apply { setTextColor(Saek.yeonhan) })
        mom.addView(deoAn)

        boyeojunGeul = ""
        boyeojunYocheong = Bang.yocheongSeq
        boyeojunGin = Bang.ginSeq   // 1.9.0 첫 화면을 다시 그릴 때는 알리지 않고 지금 상태만 보임
        boyeojunPd = Bang.pdKey
        if (Bang.ginMal.isNotEmpty() && Bang.kyeojim) { ginGeul.text = "긴급 안내 중: " + Bang.ginMal; ginGeul.visibility = View.VISIBLE }
        sangtaeGochigi(true)
        gyeorugi(bangDanchu)
    }

    // ───── 1.9.0 (261024-B17) 현장 기능 — 피디 화면 열기, 관계자 붙이기 큐알, 피디 번호 ─────
    private fun pdMal(): String = if (Bang.pdKey.isNotEmpty()) "피디 번호 " + Bang.pdKey else "피디 번호: 아이피타임 방식으로 BYOD 방송을 시작하면 정해집니다"

    private fun allimMal(t: String) { if (malsoriOn && ttsDoem) malhagi(t) else mom.announceForAccessibility(t) }

    private fun pdYeolgi() {
        val pk = Bang.pdKey
        if (!Bang.kyeojim || Flint.flintInga(this) || pk.isEmpty()) {
            allimMal("피디 화면은 아이피타임 방식으로 BYOD 방송을 하는 동안 이 기기에서 열립니다. 먼저 BYOD 방송 시작을 누르십시오.")
            return
        }
        if (Jaryo.pail(this, "pd.html") == null) {
            allimMal("피디 화면 파일이 아직 없습니다. 인터넷이 되는 곳에서 더 보기의 새로고침을 눌러 받으십시오.")
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:" + Bang.PORT + "/pd?k=" + pk)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            allimMal("피디 화면을 브라우저로 엽니다. 피디 번호는 " + pk.toCharArray().joinToString(" ") + " 입니다.")
        } catch (_: Exception) {
            allimMal("이 기기에서 웹 브라우저를 찾지 못했습니다. 크롬이나 삼성 인터넷을 설치한 뒤 다시 누르십시오.")
        }
    }

    /** 1.11.0 (261028-B21, 새방클, 이사장님 승인 2026-10-28) 노트북에서 피디 화면 열기 큐알 — 아이피타임 방식은 이 기기 주소, 플린트 2는 공유기 주소(피디 번호를 공유기에 물음) */
    private fun pdQrBogi() {
        if (!Bang.kyeojim) { allimMal("노트북에서 피디 화면 열기는 BYOD 방송을 하는 동안 쓸 수 있습니다. 먼저 BYOD 방송 시작을 누르십시오."); return }
        if (!Flint.flintInga(this)) {
            if (Bang.pdKey.isEmpty()) { allimMal("피디 번호가 아직 정해지지 않았습니다. 잠시 뒤 다시 누르십시오."); return }
            pdQrChang(HyeonjangSeobeo.pdQrJuso(), Bang.pdKey); return
        }
        allimMal("플린트 2 공유기에 피디 번호를 묻고 있습니다.")
        Thread {
            val k = (Flint.mutgi(this, "/beonho") ?: "").trim()
            runOnUiThread {
                if (k.length != 4 || !k.all { it in '0'..'9' }) allimMal("플린트 2 공유기에서 피디 번호를 받지 못했습니다. 방송 공유기에 붙어 소리를 보내는 중인지 확인하고 다시 누르십시오.")
                else pdQrChang("http://" + Flint.IP + "/pd?k=" + k, k)
            }
        }.start()
    }

    private fun pdQrChang(j: String, pk: String) {
        val bm = try { hjQrGeurim(j, 800) } catch (_: Exception) { null }
        if (bm == null) { allimMal("큐알을 만들지 못했습니다. 주소는 " + j + " 입니다."); return }
        val iv = android.widget.ImageView(this).apply {
            setImageBitmap(bm); adjustViewBounds = true
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.WHITE)
            contentDescription = "노트북에서 피디 화면 열기 큐알코드"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        val an = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        an.addView(iv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        an.addView(TextView(this).apply {
            text = "1. 노트북을 방송 공유기(" + Flint.gongyugiIreum(this@ByodActivity) + ")에 무선(와이파이)이나 유선(랜선)으로 붙입니다. 무선이면 그동안 노트북에서 인터넷이 되지 않고, 유선이면 노트북 와이파이로 인터넷을 그대로 씁니다. 랜선은 공유기 뒷면의 따로 떨어진 인터넷 구멍을 피해 나란히 붙은 구멍에 꽂습니다.\n" +
                "2. 노트북 카메라 앱으로 이 큐알을 비추거나, 크롬 주소 칸에 아래 주소를 칩니다.\n" +
                "3. 피디 화면이 열리면 참석과 추첨에서 명단을 넣고, 행사장 대형 화면 열기를 눌러 그 창을 빔 쪽으로 옮깁니다.\n" +
                "방송은 이 기기가 맡으므로 노트북이 꺼져도 방송은 이어집니다.\n주소: " + j
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextIsSelectable(true)
        })
        android.app.AlertDialog.Builder(this).setTitle("노트북에서 피디 화면 열기").setView(ScrollView(this).apply { addView(an) })
            .setPositiveButton("닫기", null).show()
        allimMal("노트북에서 피디 화면 열기 큐알을 띄웠습니다. 할 일과 주소가 큐알 아래에 있습니다. 피디 번호는 " + pk.toCharArray().joinToString(" ") + " 입니다.")
    }

    private fun gwanQrBogi() {
        if (!Bang.kyeojim || Flint.flintInga(this) || Bang.pdKey.isEmpty()) {
            allimMal("관계자 붙이기 큐알은 아이피타임 방식으로 BYOD 방송을 하는 동안 만들 수 있습니다. 먼저 BYOD 방송 시작을 누르십시오.")
            return
        }
        val j = HyeonjangSeobeo.gwanQrJuso()
        val bm = try { hjQrGeurim(j, 800) } catch (_: Exception) { null }
        if (bm == null) { allimMal("큐알을 만들지 못했습니다. 주소는 " + j + " 입니다."); return }
        val iv = android.widget.ImageView(this).apply {
            setImageBitmap(bm)
            adjustViewBounds = true
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.WHITE)
            contentDescription = "관계자 붙이기 큐알코드"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        val an = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        an.addView(iv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        an.addView(TextView(this).apply {
            text = "진행요원, 현장영상해설사, 무대 카메라 폰이 이 큐알을 찍고 역할과 이름을 골라 붙습니다. 폰은 먼저 방송 공유기 와이파이에 붙어 있어야 합니다.\n주소: " + j
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextIsSelectable(true)
        })
        android.app.AlertDialog.Builder(this).setTitle("관계자 붙이기 큐알").setView(ScrollView(this).apply { addView(an) })
            .setPositiveButton("닫기", null).show()
        allimMal("관계자 붙이기 큐알을 크게 띄웠습니다. 관계자 폰으로 찍게 하십시오.")
    }

    // 1.1.5 (261009-B7) 현장 점검 — 맨 앞 판단 한 줄을 말로 알리고, 확인할 것부터 보여 줌
    private fun hyeonjangJeomgeom() {
        val t = Hyeonjang.geul(this)
        malhagi(t.lineSequence().first())
        android.app.AlertDialog.Builder(this).setTitle("현장 점검").setMessage(t)
            .setPositiveButton("닫기", null)
            .setNegativeButton("다시 점검") { _, _ -> h.postDelayed({ hyeonjangJeomgeom() }, 300) }
            .setNeutralButton("결과 복사") { _, _ ->
                try { (getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("현장 점검", t)); malhagi("현장 점검 결과를 복사했습니다.") } catch (_: Exception) { }
            }.show()
    }

    private fun deoBogi(yeolgi: Boolean) {
        deoAn.visibility = if (yeolgi) View.VISIBLE else View.GONE
        deoDanchu.text = if (yeolgi) "더 보기 접기" else "더 보기"
        if (yeolgi) gyeorugi(deoAn.getChildAt(0))
    }

    // 1.2.0 방송 방식 — 플린트 2(공유기 안 서버) 또는 이 기기가 서버
    // 1.7.0 (261021-B15, 이사장님 승인) 「공유기: 아이피타임」과 「공유기: 플린트 2」를 엔터 한 번에 번갈아 고름, 처음값은 아이피타임
    private fun bangsikMal(): String = if (Flint.flintInga(this)) "공유기 직접 고르기: 지금 플린트 2. 누르면 아이피타임으로 바꿉니다(보통은 방송을 시작할 때 앱이 스스로 알아냅니다)"
        else "공유기 직접 고르기: 지금 아이피타임. 누르면 플린트 2로 바꿉니다(보통은 방송을 시작할 때 앱이 스스로 알아냅니다)"

    private fun bangsikBakkugi() {
        if (Bang.kyeojim) { val t = "방송 중에는 공유기를 바꾸지 않습니다. 방송을 멈춘 뒤 눌러 주십시오."; bangsikDanchu.announceForAccessibility(t); malhagi(t); return }
        Flint.bangsikBakkugi(this, if (Flint.flintInga(this)) "taebeullit" else "flint")
        bangsikDanchu.text = bangsikMal()
        val t = if (Flint.flintInga(this)) "플린트 2로 바꾸었습니다. 처음 쓰는 플린트 2면 더 보기의 플린트 2 준비하기를 먼저 하십시오."
            else "아이피타임으로 바꾸었습니다. 이 기기를 허브 랜선으로 아이피타임 공유기에 이으십시오. 듣는 분은 와이파이에 붙은 뒤 듣기 주소 엔에프시 태그나 큐알로 듣습니다."
        bangsikDanchu.announceForAccessibility(t); malhagi(t)
        sangtaeGochigi(true)
    }

    // 1.5.0 현장 방송과 함께 인터넷 방송 켜고 끄기 — 다음 방송부터
    private fun hamkkeMal() = if (Inteonet.hamkke(this)) "BYOD 방송과 함께 인터넷 방송: 켜짐. 누르면 끕니다" else "BYOD 방송과 함께 인터넷 방송: 꺼짐. 누르면 켭니다"
    // 1.9.2 (261026-B19) 소리 자동 맞춤 켜고 끄기 — 방송 중에도 곧바로 적용
    private fun keugiMal() = if (pref.getBoolean("jadong_keugi", true)) "소리 자동 맞춤(작은 해설 소리 키우기): 켜짐. 누르면 끕니다" else "소리 자동 맞춤(작은 해설 소리 키우기): 꺼짐. 누르면 켭니다"
    private fun keugiBakkugi() {
        val on = !pref.getBoolean("jadong_keugi", true)
        pref.edit().putBoolean("jadong_keugi", on).apply()
        JadongKeugi.on = on; JadongKeugi.cheoeum()
        keugiDanchu.text = keugiMal()
        val t = if (on) "소리 자동 맞춤을 켰습니다. 들어오는 해설 소리가 작으면 앱이 최대 8배까지 키워 내보냅니다." else "소리 자동 맞춤을 껐습니다. 들어오는 소리를 그대로 내보냅니다."
        keugiDanchu.announceForAccessibility(t); malhagi(t)
    }

    // 1.10.0 (261026-B20) 소리 방식 오푸스(시험) 켜고 끄기 — 처음값 꺼짐, 다음 방송부터 적용. 지금 방식(뮤)은 늘 함께 나감
    private fun opusMal() = if (OpusSori.on(this)) "소리 방식 오푸스(시험): 켜짐. 누르면 끕니다" else "소리 방식 오푸스(시험): 꺼짐. 누르면 켭니다"
    private fun opusBakkugi() {
        val on = !OpusSori.on(this)
        OpusSori.dugi(this, on)
        opusDanchu.text = opusMal()
        val t = if (!on) "오푸스(시험)를 껐습니다. 다음 방송부터 지금 방식으로만 내보냅니다."
            else if (!OpusSori.doena()) "오푸스(시험)를 켰지만, 이 기기는 안드로이드 10보다 낮아 오푸스를 만들 수 없습니다. 지금 방식으로만 나갑니다."
            else "오푸스(시험)를 켰습니다. 다음 방송부터 지금 방식과 함께 더 맑은 오푸스 소리도 내보냅니다. 엘브이디 방송 2.7.0 이상은 저절로 오푸스로 듣습니다." +
                (if (Bang.kyeojim) " 지금 방송은 그대로이니, 방송을 끝내고 다시 시작하십시오." else "")
        opusDanchu.announceForAccessibility(t); malhagi(t)
    }

    private fun hamkkeBakkugi() {
        val on = !Inteonet.hamkke(this)
        Inteonet.hamkkeDugi(this, on)
        hamkkeDanchu.text = hamkkeMal()
        val t = (if (on) "BYOD 방송을 켜면 인터넷 방송도 함께 나갑니다." else "BYOD 방송만 나갑니다. 인터넷 방송은 함께 나가지 않습니다.") + (if (Bang.kyeojim) " 다음 방송부터 적용됩니다." else "")
        hamkkeDanchu.announceForAccessibility(t); malhagi(t)
    }

    // 1.9.0 (261024-B17) 무대 영상 녹화 — 켜 두면 BYOD 방송을 시작할 때 뒤 카메라로 무대를 함께 녹화(영상만, 소리는 나스에서 붙임)
    private fun yeongsangMal() = if (MudaeNokhwa.kyeojim(this)) "무대 영상 녹화: 켜짐. 누르면 끕니다" else "무대 영상 녹화: 꺼짐. 누르면 켭니다"
    private fun yeongsangBakkugi() {
        val on = !MudaeNokhwa.kyeojim(this)
        MudaeNokhwa.kyeojimDugi(this, on)
        yeongsangDanchu.text = yeongsangMal()
        val t = if (on) "무대 영상 녹화를 켰습니다. BYOD 방송을 시작하면 이 기기 뒤 카메라로 무대를 함께 녹화합니다. 뒤 카메라가 무대를 향하게 세우고 충전기를 꽂아 두십시오." +
                (if (Bang.kyeojim) " 지금 방송부터 녹화합니다." else "")
            else "무대 영상 녹화를 껐습니다. 소리만 녹음합니다." + (if (Bang.kyeojim) " 하던 녹화는 여기까지 담아 두었다가 올립니다." else "")
        yeongsangDanchu.announceForAccessibility(t); malhagi(t)
        if (on) {
            if (!MudaeNokhwa.heorak(this)) requestPermissions(arrayOf(Manifest.permission.CAMERA), 8)
            else yeongsangJigeumKyeogi()
        } else if (Bang.kyeojim) Thread { MudaeNokhwa.kkeugi() }.start()
    }

    /** 방송 중에 켰을 때 — 방송 일꾼에 카메라 종류를 더하고(방송은 그대로) 녹화를 시작 */
    private fun yeongsangJigeumKyeogi() {
        if (!Bang.kyeojim || !MudaeNokhwa.kyeojim(this) || !MudaeNokhwa.heorak(this)) return
        ByodService.kyeogi(this)
        h.postDelayed({
            if (Bang.kyeojim && MudaeNokhwa.kyeojim(this)) MudaeNokhwa.sijak(applicationContext, if (Flint.flintInga(this)) "flint" else "iptime", GigiNokeum.sid)
        }, 800)
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
        if (!heorakDoem() || (MudaeNokhwa.kyeojim(this) && !MudaeNokhwa.heorak(this))) { sijakGidarim = true; heorakMutgi(); return }   // 1.9.0 무대 영상 녹화를 켜 두셨으면 카메라 허락도
        gongyugiSalpigo()
    }

    // 1.9.0 (261024-B17, 이사장님 지시 2026-10-23 「직원이 헷갈려 한다, 더 간단하게」)
    // 방송을 시작하기 전에 ① 붙은 공유기를 앱이 스스로 알아내고(플린트 2 방송 서버가 답하면 플린트 2, 아니면 아이피타임)
    // ② 이 기기가 방송 공유기가 아닌 와이파이(사무실 와이파이 등)에 붙어 있으면 분명히 알리고 묻습니다(10월 23일 앞 30분이 이 경우).
    // ③ 전화번호가 비어 인터넷 방송이 함께 나가지 않으면 시작 전에 한 번 알립니다.
    private fun gongyugiSalpigo() {
        bangDanchu.isEnabled = false
        sangtae.text = "붙은 공유기를 살피고 있습니다."
        Thread {
            val flintDap = Flint.mang(this) != null && Flint.mutgi(this, "/status", 1500) != null
            val juso = ByodService.jusoChatgi()
            h.post {
                bangDanchu.isEnabled = true
                if (doumalYeollim) return@post
                if (flintDap) { if (!Flint.flintInga(this)) Flint.bangsikBakkugi(this, "flint") }
                else if (Flint.flintInga(this)) Flint.bangsikBakkugi(this, "taebeullit")
                bangsikDanchu.text = bangsikMal()
                val host = juso.removePrefix("http://").substringBefore(":")
                val munje: String? = when {
                    flintDap -> null
                    juso.isEmpty() -> "이 기기가 어느 공유기에도 붙어 있지 않습니다. 방송 공유기의 와이파이에 붙이거나 허브 랜선을 공유기에 이으십시오."
                    host.startsWith("192.168.8.") -> "플린트 2 공유기로 보이는데 방송 서버가 답하지 않습니다. 처음 쓰는 플린트 2면 더 보기의 플린트 2 준비하기를 먼저 하십시오."
                    !host.startsWith("192.168.") -> "이 기기가 방송 공유기가 아닌 와이파이에 붙어 있는 것 같습니다. 지금 주소는 " + host + " 입니다. 아이피타임 방송 공유기에 붙으면 주소가 192.168 로 시작합니다. 이대로 시작하면 듣는 분이 방송을 찾지 못합니다."
                    else -> null
                }
                if (munje == null) { sijakhagi(); return@post }
                malhagi(munje)
                android.app.AlertDialog.Builder(this).setTitle("공유기 확인").setMessage(munje)
                    .setPositiveButton("와이파이 설정 열기") { _, _ -> try { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) } catch (_: Exception) { } }
                    .setNegativeButton("그래도 시작") { _, _ -> sijakhagi() }
                    .setNeutralButton("닫기", null).show()
            }
        }.start()
    }

    private fun sijakhagi() {
        sijakGidarim = false
        baeteoriMutgi()
        ByodService.kyeogi(this)
        val itJul = if (Inteonet.hamkke(this) && Inteonet.jeonhwa(this).length < 9) " 다만 현장영상해설사 전화번호가 비어 있어 인터넷 방송은 나가지 않습니다." else ""
        malhagi("BYOD 방송을 시작했습니다. 공유기: " + Flint.gongyugiIreum(this) + "." + itJul)
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
        if (MudaeNokhwa.kyeojim(this) && !MudaeNokhwa.heorak(this)) l.add(Manifest.permission.CAMERA)   // 1.9.0 (261024-B17) 무대 영상 녹화
        requestPermissions(l.toTypedArray(), 7)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 8) {   // 1.9.0 (261024-B17) 무대 영상 녹화를 켤 때 카메라 허락
            val t = if (MudaeNokhwa.heorak(this)) { yeongsangJigeumKyeogi(); "카메라를 허락하셨습니다. 무대 영상을 녹화할 수 있습니다." }
                else "카메라 허락이 없으면 무대 영상을 녹화하지 못합니다. 방송과 녹음은 그대로 됩니다. 폰 설정의 애플리케이션, LVD 송출, 권한에서 카메라를 허용해 주십시오."
            if (malsoriOn && ttsDoem) malhagi(t) else mom.announceForAccessibility(t)
            return
        }
        if (requestCode != 7) return
        if (heorakDoem()) {
            if (sijakGidarim) { sijakGidarim = false; gongyugiSalpigo() }
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
            "BYOD 방송이 꺼져 있습니다." + " 공유기: " + Flint.gongyugiIreum(this) + "." + (if (jm.isNotEmpty()) " " + jm else "")
        } else {
            (if (Bang.tonghwaJung) "통화 중입니다. 듣는 분께 잠시 기다려 달라는 안내를 보내고 있습니다. 통화가 끝나면 저절로 이어 보냅니다. " else "") +
            (if (!Bang.keu) "피디 화면에서 현장영상해설을 멈추어 두어 듣는 분께 대기 음악이 나갑니다. " else "") +   // 1.9.0 (261024-B17)
            "BYOD 방송 중입니다. 듣는 분 " + Bang.deutnunSu + "명. 소리 받는 곳: " + so + "." +
                (if (Flint.flintInga(this)) (if (Flint.bonaenda) " 공유기로 보내고 있습니다." else "") else "") + (if (jm.isNotEmpty()) " " + jm else "")
        }
        val keunBakkwim = k != boyeojunKyeojim || so != boyeojunSojae || jm != boyeojunJalmot || Bang.tonghwaJung != boyeojunTonghwa
        if (t != boyeojunGeul && (baro || keunBakkwim || now - boyeojunTtae >= 10_000)) {
            sangtae.text = t
            boyeojunGeul = t
            boyeojunTtae = now
            if (keunBakkwim && !baro && jm.isNotEmpty() && jm != boyeojunJalmot) malhagi(jm)
            boyeojunKyeojim = k
            boyeojunSojae = so
            boyeojunJalmot = jm
            boyeojunTonghwa = Bang.tonghwaJung
        }
        // 1.5.0 인터넷 방송 한 줄 — 바뀔 때만 한 번
        // 1.9.0 방송 전에도 전화번호가 비어 있으면 첫 화면에 한 줄로 분명히(대조표 송출 3번)
        val ij = if (k || Inteonet.jul.isNotEmpty()) Inteonet.jul
            else if (Inteonet.hamkke(this) && Inteonet.jeonhwa(this).length < 9) "인터넷 방송: 현장영상해설사 전화번호가 비어 있어, 이대로면 BYOD 방송만 나가고 인터넷 방송은 나가지 않습니다. 위 전화번호 칸에 협회 명단의 번호를 넣으십시오."
            else ""
        if (ij != boyeojunInteonet) {
            boyeojunInteonet = ij
            inteonetGeul.text = ij
            inteonetGeul.visibility = if (ij.isEmpty()) View.GONE else View.VISIBLE
            if (ij.isNotEmpty() && !baro) { if (malsoriOn && ttsDoem) malhagi(ij) else inteonetGeul.announceForAccessibility(ij) }
        }
        // 1.9.0 (261024-B17) 무대 영상 녹화 한 줄 — 글은 바뀌면 고치되(분 수) 알리지 않고, 문제는 새로 생길 때 한 번만 알림
        val yj = MudaeNokhwa.jul(this)
        if (yj != boyeojunYeongsang) {
            boyeojunYeongsang = yj
            yeongsangGeul.text = yj
            yeongsangGeul.visibility = if (yj.isEmpty()) View.GONE else View.VISIBLE
        }
        val ym = MudaeNokhwa.munje
        if (ym != boyeojunYeongsangMunje) {
            boyeojunYeongsangMunje = ym
            if (ym.isNotEmpty() && !baro) { if (malsoriOn && ttsDoem) malhagi("무대 영상: " + ym) else yeongsangGeul.announceForAccessibility("무대 영상: " + ym) }
        }
        // 듣기 주소 — 방송 중에만
        if (k) {
            val j = Bang.juso.ifEmpty { ByodService.jusoChatgi() }
            if (j.isEmpty()) {
                jusoJemok.visibility = View.VISIBLE
                if (juso.text.toString() != "공유기에 붙지 않았습니다. 와이파이나 랜선을 확인하십시오.") juso.text = "공유기에 붙지 않았습니다. 와이파이나 랜선을 확인하십시오."
            } else {
                Bang.juso = j
                val jj = if (Flint.flintInga(this)) "듣기 화면(와이파이에 붙으면 저절로 뜸)" else "듣기 주소"
                if (jusoJemok.text.toString() != jj) jusoJemok.text = jj
                jusoJemok.visibility = View.VISIBLE
                if (juso.text.toString() != j) juso.text = j
            }
            juso.visibility = View.VISIBLE
        } else {
            jusoJemok.visibility = View.GONE
            juso.visibility = View.GONE
        }
        // 1.9.0 (261024-B17) 피디 번호 한 줄 — 바뀔 때만 고쳐 씀(알리지 않음)
        if (Bang.pdKey != boyeojunPd) { boyeojunPd = Bang.pdKey; pdGeul.text = pdMal() }
        // 1.9.0 긴급 안내 — 내보내거나 끝낼 때만 한 번
        val gs = Bang.ginSeq
        if (gs != boyeojunGin) {
            boyeojunGin = gs
            val gm = Bang.ginMal
            if (gm.isNotEmpty() && k) {
                ginGeul.text = "긴급 안내 중: " + gm
                ginGeul.visibility = View.VISIBLE
                malhagi("긴급 안내를 내보냈습니다. " + gm)
            } else if (ginGeul.visibility == View.VISIBLE) {
                ginGeul.visibility = View.GONE
                malhagi("긴급 안내를 끝냈습니다.")
            }
        }
        if (!k && ginGeul.visibility == View.VISIBLE) ginGeul.visibility = View.GONE
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

    // ───── 1.3.0 입력 칸(바꾸면 바로 기억) ─────
    private fun kanMandeul(ireum: String, kiy: String): EditText {
        val e = EditText(this).apply {
            hint = ireum
            contentDescription = ireum
            setHintTextColor(Saek.yeonhan)
            setTextColor(Saek.geul)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            inputType = InputType.TYPE_CLASS_TEXT
            setText(pref.getString(kiy, ""))
        }
        e.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
            override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
            override fun afterTextChanged(a: android.text.Editable?) { pref.edit().putString(kiy, a?.toString() ?: "").apply() }
        })
        return e
    }

    // ───── 1.3.0 지난 행사 기록 — 다섯 개씩, 이름을 누르면 결과보고와 녹음 듣기 ─────
    private var haengsaJjok = 0
    private var deutgiPlayer: android.media.MediaPlayer? = null

    private fun deutgiMeomchum() { try { deutgiPlayer?.release() } catch (_: Exception) { }; deutgiPlayer = null }

    private fun haengsaHwamyeon() {
        doumalYeollim = true
        mom.removeAllViews()
        val wi = danchu("뒤로") { deutgiMeomchum(); cheotHwamyeon() }
        mom.addView(wi)
        val l = Haengsa.mokrok(this)
        var cheot: View? = null
        if (l.isEmpty()) {
            mom.addView(geul("아직 담아 둔 행사가 없습니다. 방송을 멈추면 녹음과 결과보고가 여기에 담깁니다.", 20f))
        } else {
            if (l.any { !it.olim }) mom.addView(danchu("다시 듣기에 아직 못 올린 것 지금 올리기") { Haengsa.sijak(this, false); malhagi("다시 듣기에 올리고 있습니다.") })
            val sijak = haengsaJjok * 5
            for (x in l.subList(minOf(sijak, l.size), minOf(sijak + 5, l.size))) {
                val nal = SimpleDateFormat("M월 d일 a h시 m분", Locale.KOREAN).format(Date(x.sijak * 1000))
                val gil = x.gilee.toInt().let { if (it >= 60) "${it / 60}분" else "${it}초" }
                val an = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
                an.addView(geul(Haengsa.bogo(this, x.sid), 18f))
                // 1.7.0 아이피타임 방식 녹음(뮤 .ul)도 들을 수 있게 — 듣기 때만 웨이브 머리를 붙인 사본을 만들어 틂
                val f = java.io.File(java.io.File(Haengsa.teo(this), x.sid), "nokeum.mp3").takeIf { it.isFile } ?: Haengsa.deutgiWav(this, x.sid)
                if (f != null && f.isFile) an.addView(danchu("녹음 듣기") {
                    if (deutgiPlayer != null) { deutgiMeomchum(); malhagi("녹음을 멈추었습니다.") }
                    else try { deutgiPlayer = android.media.MediaPlayer().apply { setDataSource(f.path); prepare(); start(); setOnCompletionListener { deutgiMeomchum() } } } catch (_: Exception) { malhagi("녹음을 틀지 못했습니다.") }
                })
                val b = danchu(x.jemok + ", " + nal + ", " + gil + (if (x.olim) ", 다시 듣기에 올림" else ", 아직 못 올림")) {
                    val yeol = an.visibility != View.VISIBLE
                    an.visibility = if (yeol) View.VISIBLE else View.GONE
                    if (yeol) gyeorugi(an.getChildAt(0))
                }
                if (cheot == null) cheot = b
                mom.addView(b); mom.addView(an)
            }
            if (sijak + 5 < l.size) mom.addView(danchu("더 보기") { haengsaJjok++; haengsaHwamyeon() })
            if (haengsaJjok > 0) mom.addView(danchu("이전 보기") { haengsaJjok--; haengsaHwamyeon() })
        }
        mom.addView(danchu("뒤로") { deutgiMeomchum(); cheotHwamyeon() })
        gyeorugi(cheot ?: wi)
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
            doumalYeollim -> { deutgiMeomchum(); cheotHwamyeon() }
            ::deoAn.isInitialized && deoAn.visibility == View.VISIBLE -> { deoBogi(false); gyeorugi(deoDanchu) }
            else -> {
                bangDanchu.announceForAccessibility("첫 화면입니다. 앱을 닫아도 방송은 이어집니다.")
            }
        }
    }
}
