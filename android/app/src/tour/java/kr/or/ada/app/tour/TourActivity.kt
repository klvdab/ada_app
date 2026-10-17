// LVD투어스튜디오 안드로이드 — 화면 (1.1.0판, 빌드 261017-T3, 2026-10-17 방클, 이사장님 승인 「첫단계부터 시작해」 — 아이폰 1.1.0 과 같은 판)
// 1.1.0: 웹에 있던 것 열네 가지 — 사진 크게 보기·지우기·다시 찍기, 지점 지우기, 현장 메모(말·글)와 듣기, 읽으며 녹음과 되돌아보기(mp3),
//        못 올린 것 다시 올리기, 봐 달라고 알리기, 판 되돌리기, 코스 지우기·휴지통, 더 적을 것. 맞대기 표: 저장소 matdae/tour_web_app.txt
// 1.0.1: 사진 안내를 시계 방향으로, 네 개씩 목록의 더 보기·이전 보기를 「넷」으로. 1.0.0(261012-T1, 2026-10-12): 첫 판
// 웹 투어스튜디오 1.12판을 속까지 앱으로. 같은 서버(lvdts_api.php)·같은 코스 — 웹과 앱 어느 쪽에서 고쳐도 같습니다.
// 아래 탭 다섯: 코스·답사·지점·현장영상해설·설정. 탭 바는 모든 속 화면에서 늘 보이고 뒤로 단추는 위에만.
// 규칙: 목록은 다섯 줄씩(더 보기·이전 보기), 넘기면 커서는 첫 줄, 고치면 2초 뒤 저절로 저장(못 하면 폰에 남김),
//       새로고침은 설정에 하나, 앱 안내 말소리 켜고 끄기, 뒤로 가기로 앱 밖에 나가지 않음, 앱 안의 말은 모두 「현장영상해설」.
package kr.or.ada.app.tour

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationManager
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

sealed class Hw {
    object Kos : Hw(); object KosJeongbo : Hw(); object Jeomgeom : Hw()
    object Dapsa : Hw()
    object Jijeom : Hw(); data class Spot(val id: String) : Hw()
    object Haeseol : Hw()
    object Seoljeong : Hw(); data class Doum(val i: Int) : Hw(); object Bakkwin : Hw()
    data class Sajin(val spotId: String, val idx: Int) : Hw(); data class Memos(val spotId: String) : Hw()
    object PanDoerigi : Hw(); object Hyujitong : Hw()
}

class TourActivity : Activity() {
    companion object {
        const val PAN = "1.1.0"; const val BILDEU = "261017-T3"
        const val PAN_IREUM = "LVD투어스튜디오 ${PAN}판 (빌드 ${BILDEU})"
        const val SAJIN = 71; const val HEORAK_KAMERA = 72; const val HEORAK_JARI = 73; const val HEORAK_MIC = 74
        val BUNYA = listOf("", "문화유산 분야", "박물관·전시 분야", "자연·경관 분야", "공연·행사 분야", "스포츠 관람 분야", "교육·체험 분야", "실시간 퍼포먼스 분야", "테마투어 분야")
        val JIYEOK = listOf("", "서울", "경기", "인천", "부산", "대구", "광주", "대전", "강원", "충청", "전라", "경상", "제주")
        val NE = listOf("12시 방향", "3시 방향", "6시 방향", "9시 방향")
        val BANGHYANG = listOf("12시 방향") + (1..11).map { "${it}시 방향" }
    }
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(3)
    private val stacks = arrayOf(mutableListOf<Hw>(Hw.Kos), mutableListOf<Hw>(Hw.Dapsa), mutableListOf<Hw>(Hw.Jijeom), mutableListOf<Hw>(Hw.Haeseol), mutableListOf<Hw>(Hw.Seoljeong))
    private var tab = 0
    private lateinit var scroll: ScrollView
    private lateinit var body: LinearLayout
    private lateinit var tabbar: LinearLayout

    // ── 앱 살림 ──
    private var me: JSONObject? = null
    private var sijakham = false
    private var courses = listOf<JSONObject>()
    private var kos: Kos? = null
    private var curId = "sOpen"
    private var base = ""
    private var lastSaved = ""
    private var saving = false; private var saveAgain = false
    private var saveMark = ""
    private var allim = ""
    private val jang = HashMap<String, Int>()   // 목록마다 지금 쪽
    private var tts: TextToSpeech? = null; private var ttsOk = false
    private var sajinBang = "12시 방향"; private var sajinFile: File? = null
    private val saveRun = Runnable { save(true) }
    // 1.1.0
    private var lastShot: Pair<String, Int>? = null      // 방금 찍은 사진(지점 번호, 몇째)
    private var memoKan = "access"
    private var memoGeul = ""
    private var micGidarim: (() -> Unit)? = null
    private var player: MediaPlayer? = null; private var deunneun = ""
    private var panMok: List<JSONObject>? = null; private var hyujiMok: List<JSONObject>? = null
    private val sp get() = getSharedPreferences("tour", MODE_PRIVATE)
    private val malsori get() = sp.getBoolean("malsori", true)

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        Api.ctx = applicationContext
        Api.naga = { m -> main.post { naga(m) } }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Saek.bada(this@TourActivity)) }
        scroll = ScrollView(this).apply { isFillViewport = true }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(24)) }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        tabbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Saek.namsaek) }
        root.addView(tabbar, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
        draw()
        Ollim.malhagi = { m -> main.post { scroll.announceForAccessibility(m) } }
        Ollim.byeonhwa = { main.post { if (cur() is Hw.Kos || cur() is Hw.Seoljeong) draw() } }
        Ollim.sijak(this)
        Ollim.intentBoda(this, intent)
        tts = TextToSpeech(this) { st -> ttsOk = st == TextToSpeech.SUCCESS; if (ttsOk) tts?.language = Locale.KOREAN }
        if (Api.token.isEmpty()) { sijakham = true; draw() }
        else pool.execute {
            val j = Api.call("me")
            main.post {
                when {
                    Api.ok(j) -> { me = j.optJSONObject("me") ?: JSONObject(); loadCourses() }
                    j.optBoolean("offline", false) -> me = JSONObject().put("name", sp.getString("meName", "") ?: "")
                    else -> Api.token = ""
                }
                sijakham = true; draw()
            }
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 44)
    }
    override fun onResume() { super.onResume(); Ollim.dorawatda(this) }
    override fun onPause() { super.onPause(); if (kos != null) { main.removeCallbacks(saveRun); save(true) } }
    override fun onNewIntent(i: Intent) { super.onNewIntent(i); setIntent(i); Ollim.intentBoda(this, i) }
    override fun onDestroy() { Ollim.byeonhwa = null; Ollim.malhagi = null; Api.naga = null; tts?.shutdown(); deutgiMeomchum(); super.onDestroy() }

    @Deprecated("뒤로 가기 — 앱 밖으로 나가지 않음")
    override fun onBackPressed() {
        val st = stacks[tab]
        when {
            st.size > 1 -> { st.removeAt(st.size - 1); draw() }
            tab != 0 -> { tab = 0; draw() }
            else -> malhagi("첫 화면입니다.")
        }
    }

    private fun cur() = stacks[tab].last()
    private fun go(h: Hw) { stacks[tab].add(h); draw() }
    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
    private fun malhagi(s: String) { allim = s; if (malsori) scroll.announceForAccessibility(s) }
    private fun spot(): Spot? = kos?.spot(curId)

    // ── 그리기 부품 ──
    private fun kadeuBg(): GradientDrawable = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Saek.kadeu(this@TourActivity)) }
    private fun danchu(ireum: String, keun: Boolean = false, onClick: () -> Unit): Button {
        val b = Button(this).apply {
            text = ireum; isAllCaps = false; gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (keun) 18f else 16f)
            setTextColor(if (keun) Color.WHITE else Saek.geulja(this@TourActivity))
            background = if (keun) GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Saek.namsaek) } else kadeuBg()
            setPadding(dp(16), dp(12), dp(16), dp(12)); minHeight = dp(52)
            setOnClickListener { onClick() }
        }
        body.addView(b, lp(6)); return b
    }
    private fun geul(t: String, jemok: Boolean = false, jakge: Boolean = false): TextView {
        val v = TextView(this).apply {
            text = t; setTextColor(if (jemok) Saek.ganjo(this@TourActivity) else if (jakge) Saek.buGeulja(this@TourActivity) else Saek.geulja(this@TourActivity))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (jemok) 20f else if (jakge) 14f else 16f)
            if (jemok) { setTypeface(typeface, Typeface.BOLD); ViewCompat.setAccessibilityHeading(this, true) }
        }
        body.addView(v, lp(8)); return v
    }
    private fun lp(top: Int) = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }
    private fun cheotJul(v: View?) { v ?: return; v.postDelayed({ v.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null) }, 400) }
    /** 이름표가 붙은 글 칸 — 고치면 바로 자료에 넣고 2초 뒤 저장 */
    private fun kan(ireum: String, gap: String, yeoreo: Boolean = true, susja: Boolean = false, neoki: (String) -> Unit): EditText {
        geul(ireum, jakge = true).importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        val e = EditText(this).apply {
            setText(gap); contentDescription = null; hint = ireum
            inputType = when { susja -> InputType.TYPE_CLASS_NUMBER; yeoreo -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; else -> InputType.TYPE_CLASS_TEXT }
            if (yeoreo && !susja) { minLines = 2; isSingleLine = false }
            background = kadeuBg(); setPadding(dp(14), dp(12), dp(14), dp(12)); setTextColor(Saek.geulja(this@TourActivity))
            ViewCompat.setAccessibilityDelegate(this, object : androidx.core.view.AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: androidx.core.view.accessibility.AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info); info.hintText = ireum
                }
            })
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { neoki(s?.toString() ?: ""); this@TourActivity.bakkwim() }
            })
        }
        body.addView(e, lp(4)); return e
    }
    /** 다섯 줄씩 — 더 보기·이전 보기, 넘기면 커서는 첫 줄 */
    private fun <T> jangMok(key: String, items: List<T>, per: Int = 5, row: (T) -> View) {
        val n = maxOf(1, (items.size + per - 1) / per)
        val p = minOf(jang[key] ?: 0, n - 1)
        var cheot: View? = null
        items.drop(p * per).take(per).forEach { val v = row(it); if (cheot == null) cheot = v }
        if (p + 1 < n) danchu("더 보기 (다음 ${seum(per)}, ${p + 2}/$n)") { jang[key] = p + 1; draw(); cheotJul(jangCheotJari[key]) }
        if (p > 0) danchu("이전 보기 (앞의 ${seum(per)})") { jang[key] = p - 1; draw(); cheotJul(jangCheotJari[key]) }
        jangCheotJari[key] = cheot
    }
    private val jangCheotJari = HashMap<String, View?>()
    private fun seum(k: Int) = when (k) { 4 -> "넷"; 5 -> "다섯"; else -> "${k}개" }

    private fun tabs() {
        tabbar.removeAllViews()
        listOf("코스", "답사", "지점", "현장영상해설", "설정").forEachIndexed { k, n ->
            val b = Button(this).apply {
                text = n; isAllCaps = false; setTextColor(if (k == tab) Saek.geum else Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setBackgroundColor(Color.TRANSPARENT); setPadding(dp(2), 0, dp(2), 0)
                contentDescription = if (k == tab) "$n 탭, 선택됨" else "$n 탭"
                setOnClickListener { if (tab == k) { while (stacks[k].size > 1) stacks[k].removeAt(stacks[k].size - 1) }; tab = k; draw(); cheotJul(body.getChildAt(0)) }
            }
            tabbar.addView(b, LinearLayout.LayoutParams(0, dp(56), 1f))
        }
    }

    // ── 화면 ──
    private fun draw() {
        if (cur() !is Hw.Haeseol) tts?.stop()
        body.removeAllViews(); tabs(); jangCheotJari.clear()
        if (me == null || Api.token.isEmpty()) {
            tabbar.visibility = View.GONE
            if (!sijakham) geul("들어오는 중입니다.") else login()
            return
        }
        tabbar.visibility = View.VISIBLE
        if (stacks[tab].size > 1) danchu("뒤로") { onBackPressed() }
        when (val h = cur()) {
            Hw.Kos -> kosHwamyeon()
            Hw.KosJeongbo -> kosJeongbo()
            Hw.Jeomgeom -> jeomgeom()
            Hw.Dapsa -> dapsa()
            Hw.Jijeom -> jijeom()
            is Hw.Spot -> spotHwamyeon(h.id)
            Hw.Haeseol -> haeseol()
            Hw.Seoljeong -> seoljeong()
            is Hw.Doum -> doum(h.i)
            Hw.Bakkwin -> bakkwin()
            is Hw.Sajin -> sajinHwamyeon(h.spotId, h.idx)
            is Hw.Memos -> memoHwamyeon(h.spotId)
            Hw.PanDoerigi -> panDoerigi()
            Hw.Hyujitong -> hyujitong()
        }
    }

    private fun meori() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Saek.namsaek) }
            contentDescription = "LVD투어스튜디오, 현장 투어 코스 만들기, 사단법인 한국시각장애인현장영상해설협회"
            ViewCompat.setAccessibilityHeading(this, true)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        val logo = LogoView(this).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
        box.addView(logo, LinearLayout.LayoutParams(dp(92), ViewGroup.LayoutParams.WRAP_CONTENT))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
        col.addView(TextView(this).apply { text = "LVD투어스튜디오"; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f); setTypeface(typeface, Typeface.BOLD) })
        col.addView(TextView(this).apply { text = "현장 투어 코스 만들기 · 사단법인 한국시각장애인현장영상해설협회"; setTextColor(Saek.geum); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f) })
        box.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        body.addView(box, lp(0))
    }
    private fun allimJul() { if (allim.isNotEmpty()) geul(allim, jakge = true) }
    private fun gyeonggye() { geul(RULE_TXT).apply { setTypeface(typeface, Typeface.BOLD); setTextColor(Saek.ganjo(this@TourActivity)) } }
    private fun kosEopseum() {
        geul("아직 연 코스가 없습니다. 코스 탭에서 코스를 열어 주십시오.")
        danchu("코스 탭으로 가기") { tab = 0; draw() }
    }

    // ── 들어오기 ──
    private fun login() {
        meori()
        geul("웹 투어스튜디오와 같은 아이디와 비밀번호로 들어오십시오. 아이디가 없으시면 웹 투어스튜디오에서 가입을 신청해 주십시오.")
        val id = EditText(this).apply { hint = "아이디"; inputType = InputType.TYPE_CLASS_TEXT; background = kadeuBg(); setPadding(dp(14), dp(12), dp(14), dp(12)) }
        val pw = EditText(this).apply { hint = "비밀번호"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; background = kadeuBg(); setPadding(dp(14), dp(12), dp(14), dp(12)) }
        body.addView(id, lp(8)); body.addView(pw, lp(6))
        val mal = TextView(this).apply { setTextColor(Saek.geulja(this@TourActivity)) }
        danchu("들어가기", keun = true) {
            val i = id.text.toString().trim(); val p = pw.text.toString()
            if (i.isEmpty() || p.isEmpty()) { mal.text = "아이디와 비밀번호를 적어 주십시오."; cheotJul(mal); return@danchu }
            mal.text = "들어가는 중입니다."
            pool.execute {
                val j = Api.call("login", JSONObject().put("id", i).put("pw", p))
                main.post {
                    pw.setText("")
                    if (Api.ok(j) && j.geul("token").isNotEmpty()) {
                        Api.token = j.geul("token")
                        me = j.optJSONObject("me") ?: JSONObject().put("id", i).put("name", j.geul("name")).put("role", j.geul("role"))
                        sp.edit().putString("meName", me?.geul("name") ?: "").apply()
                        draw(); loadCourses()
                    } else { mal.text = Api.err(j); cheotJul(mal) }
                }
            }
        }
        body.addView(mal, lp(8))
        danchu("웹 투어스튜디오 열기 (가입 신청)") { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://lvd.ada.or.kr/lvdts/lvdts.html"))) }
        geul(PAN_IREUM, jakge = true)
    }
    private fun naga(m: String) {
        val tk = Api.token
        if (tk.isNotEmpty()) pool.execute { Api.call("logout", tk = tk) }
        Api.token = ""; me = null; kos = null; courses = listOf()
        for (st in stacks) while (st.size > 1) st.removeAt(st.size - 1)
        tab = 0; malhagi(m); draw()
    }

    // ── 코스 ──
    private fun loadCourses(kkeut: (() -> Unit)? = null) {
        pool.execute {
            val j = Api.call("list_courses")
            main.post {
                if (Api.ok(j)) { val a = j.optJSONArray("courses") ?: JSONArray(); courses = (0 until a.length()).mapNotNull { a.optJSONObject(it) } }
                else malhagi(Api.err(j))
                kkeut?.invoke(); if (cur() is Hw.Kos) draw()
            }
        }
    }
    private fun openKos(id: String, kkeut: ((Boolean) -> Unit)? = null) {
        pool.execute {
            val j = Api.call("get_course", JSONObject().put("id", id))
            main.post {
                val c = j.optJSONObject("course")
                if (!Api.ok(j) || c == null) {
                    val dr = draft(id)
                    if (dr != null) { setKos(Kos(dr)); malhagi("인터넷이 없어 폰에 남겨 둔 코스를 열었습니다. 인터넷이 되면 지금 저장을 눌러 주십시오."); draw(); kkeut?.invoke(true) }
                    else { malhagi(Api.err(j)); kkeut?.invoke(false) }
                    return@post
                }
                base = c.geul("updated")
                setKos(Kos(c))
                lastSaved = kos!!.json().toString()
                draft(id)?.let { dr -> dr.remove("savedAt"); if (Kos(dr).json().toString() != lastSaved) malhagi("폰에 아직 올리지 못한 고친 것이 있습니다. 설정의 폰에 남은 것 올리기로 올리실 수 있습니다.") else draftDrop(id) }
                sp.edit().putString("lastCourse", id).apply()
                draw(); kkeut?.invoke(true)
                if (Daegi.mokrok(this).any { it.geul("c") == id }) daegiOllim(true)
            }
        }
    }
    private fun setKos(k: Kos) { kos = k; curId = "sOpen"; jang.clear(); lastShot = null }

    private fun kosHwamyeon() {
        meori()
        Ollim.julMal(this)?.let { m -> danchu(m, keun = true) { Ollim.olligi(this) } }
        allimJul()
        val k = kos
        if (k != null) {
            geul("지금 연 코스", jemok = true)
            geul(k.name + " — 지점 ${k.spots.size}곳과 오프닝·클로징 지점, 모두 " + fmtMin(k.courseMin()))
            if (saveMark.isNotEmpty()) geul(saveMark, jakge = true)
            if (k.isGyobon) {
                geul("교본은 읽기만 됩니다.")
                danchu("교본을 내 코스로 복사") {
                    pool.execute { val j = Api.call("copy_gyobon"); main.post { if (Api.ok(j)) { malhagi("교본을 내 코스로 복사했습니다."); loadCourses(); openKos(j.geul("id")) } else malhagi(Api.err(j)) } }
                }
            }
            danchu("코스 정보 (이름·장소·집합 장소·비 올 때)") { go(Hw.KosJeongbo) }
            danchu("원칙 16개 점검 (작성원칙 통합본)") { go(Hw.Jeomgeom) }
            danchu("지점 탭으로 가기") { tab = 2; draw() }
            if (!k.isGyobon) {
                geul("협회 승인 요청", jemok = true)
                val msg = EditText(this).apply { hint = "승인 요청에 붙일 말 (없어도 됩니다)"; background = kadeuBg(); setPadding(dp(14), dp(12), dp(14), dp(12)) }
                body.addView(msg, lp(4))
                danchu("협회 승인 요청 보내기") {
                    save(true)
                    val m = msg.text.toString()
                    pool.execute {
                        val j = Api.call("submit_final", JSONObject().put("c", k.id).put("msg", m))
                        main.post { if (Api.ok(j)) { msg.setText(""); malhagi("협회 승인 요청을 보냈습니다. 승인되면 코스 목록에 표시됩니다.") } else malhagi(Api.err(j)); draw() }
                    }
                }
                kosGwanri(k)
            }
        }
        geul("코스 목록 (${courses.size}개)", jemok = true)
        if (courses.isEmpty()) geul("코스가 없거나 불러오는 중입니다. 아래에서 새 코스를 만드실 수 있습니다.")
        jangMok("kos", courses) { c ->
            val id = c.geul("id"); val gy = c.optBoolean("gyobon", false) || id == "GYOBON"
            var t = (if (gy) "교본 — " else "") + c.geul("name")
            c.geul("place").let { if (it.isNotEmpty()) t += ", $it" }
            c.su("spots")?.let { t += ", 지점 ${it.toInt()}곳" }
            c.su("total")?.let { if (it > 0) t += ", " + fmtMin(it) }
            c.geul("final").let { if (it.isNotEmpty()) t += ", 승인 $it" }
            if (kos?.id == id) t += " (열려 있음)"
            danchu(t) { openKos(id) { ok -> if (ok) malhagi(c.geul("name") + " 코스를 열었습니다. 지점 ${kos?.spots?.size ?: 0}곳입니다.") } }
        }
        geul("새 코스 만들기", jemok = true)
        val ir = EditText(this).apply { hint = "코스 이름"; background = kadeuBg(); setPadding(dp(14), dp(12), dp(14), dp(12)) }
        val jg = EditText(this).apply { hint = "장소 (예: 부산 용두산공원)"; background = kadeuBg(); setPadding(dp(14), dp(12), dp(14), dp(12)) }
        body.addView(ir, lp(4)); body.addView(jg, lp(4))
        danchu("새 코스 만들기") {
            val n = ir.text.toString().trim(); val p = jg.text.toString().trim()
            if (n.isEmpty()) { malhagi("코스 이름을 적어 주십시오."); return@danchu }
            pool.execute {
                val j = Api.call("new_course", JSONObject().put("name", n).put("place", p))
                main.post {
                    if (!Api.ok(j)) { malhagi(Api.err(j)); return@post }
                    val id = j.optJSONObject("course")?.geul("id")?.ifEmpty { null } ?: j.geul("id")
                    loadCourses(); openKos(id) { ok -> if (ok) malhagi("$n 코스를 만들고 열었습니다. 오프닝 지점과 클로징 지점이 들어 있습니다.") }
                }
            }
        }
        geul("휴지통", jemok = true)
        danchu("휴지통 — 지운 코스 되살리기") { hyujiMok = null; jang.remove("hj"); go(Hw.Hyujitong) }
    }

    private fun kosJeongbo() {
        val k = kos ?: return kosEopseum()
        geul("코스 정보", jemok = true)
        val ro = k.isGyobon
        fun kk(key: String, nm: String) { kan(nm, k.s(key)) { k.d.put(key, it) }.isEnabled = !ro }
        kk("name", "코스 이름"); kk("place", "장소"); kk("meet", "집합 장소 (첫 만남 자리 — 오프닝 지점이 여기서 시작)"); kk("rain", "비 올 때"); kk("note", "메모")
        geul("더 적을 것 (분야·지역·인원)", jemok = true)
        danchu("분야 — " + k.s("field").ifEmpty { "고르지 않음" } + ". 누르면 고릅니다") {
            if (!ro) goreugi("분야", BUNYA) { v -> k.d.put("field", v); bakkwim(); malhagi("분야를 " + v.ifEmpty { "고르지 않음" } + "으로 했습니다."); draw() }
        }
        danchu("지역 — " + k.s("region").ifEmpty { "고르지 않음" } + ". 누르면 고릅니다") {
            if (!ro) goreugi("지역", JIYEOK) { v -> k.d.put("region", v); bakkwim(); malhagi("지역을 " + v.ifEmpty { "고르지 않음" } + "으로 했습니다."); draw() }
        }
        kan("참가 인원 (명)", (k.d.su("people") ?: 0.0).toInt().toString(), susja = true) { k.d.put("people", maxOf(0, it.toIntOrNull() ?: 0)) }.isEnabled = !ro
        kan("현장영상해설사 수 (명)", maxOf(1, (k.d.su("guides") ?: 1.0).toInt()).toString(), susja = true) { k.d.put("guides", maxOf(1, it.toIntOrNull() ?: 1)) }.isEnabled = !ro
        geul("앞머리 (작성원칙 통합본 1·3·5·6·16번)", jemok = true)
        kk("first", "1번 · 첫 만남 인사말"); kk("history", "3번 · 장소 소개 — 역사"); kk("access", "3번 · 장소 소개 — 위치와 찾아오는 길")
        kk("scale", "3번 · 장소 소개 — 규모와 형태"); kk("points", "3번 · 장소 소개 — 전체 포인트")
        kk("tactile", "5번 · 촉각지도·손바닥 지도 안내"); kk("safety", "6번 · 출발 전 안전 고지"); kk("refs", "16번 · 참고문헌")
    }

    private fun jeomgeom() {
        val k = kos ?: return kosEopseum()
        val L = Jeomgeom.lines(k)
        geul("원칙 16개 점검", jemok = true)
        geul(if (L.isEmpty()) "앱이 살필 수 있는 것은 모두 갖추었습니다. 나머지 원칙은 웹 투어스튜디오의 원칙 16개 점검표에서 확인하십시오." else "고칠 곳 ${L.size}가지입니다.")
        jangMok("jg", L) { geul(it) }
    }

    // ── 지점 ──
    private fun jijeom() {
        gyeonggye(); allimJul()
        val k = kos ?: return kosEopseum()
        geul("지점 목록 — " + k.name + ", 모두 " + fmtMin(k.courseMin()), jemok = true)
        jangMok("jj", k.allSpots) { s ->
            danchu(k.spotFull(s) + (if (s.id == curId) " (지금 지점)" else "") + "\n" + k.spotMinTxt(s) + ", 사진 ${s.shots.length()}장" + (if (k.spotText(s).isEmpty()) ", 대본 없음" else "")) {
                curId = s.id; go(Hw.Spot(s.id))
            }
        }
        if (!k.isGyobon) {
            geul("지점 넣기", jemok = true)
            geul("지금 지점(" + k.spotLab(spot()) + ") 바로 뒤에 넣습니다. 현장에서 경계 자리까지 적으시려면 답사 탭의 「여기서부터 새 지점 시작」을 쓰십시오.", jakge = true)
            val ir = EditText(this).apply { hint = "새 지점 이름"; background = kadeuBg(); setPadding(dp(14), dp(12), dp(14), dp(12)) }
            body.addView(ir, lp(4))
            danchu("지점 넣기 (지금 지점 바로 뒤에)") {
                val s = k.addSpot(spot(), ir.text.toString().trim()); k.syncMove(); curId = s.id
                malhagi(k.spotFull(s) + "을 넣었습니다. 지점은 모두 ${k.spots.size}곳입니다."); bakkwim(); draw()
            }
        }
    }

    private fun spotHwamyeon(id: String) {
        val k = kos ?: return kosEopseum()
        val s = k.spot(id) ?: return kosEopseum()
        curId = s.id
        val ro = k.isGyobon
        val t0 = geul(k.spotFull(s), jemok = true)
        geul("어디서부터: " + k.spotFrom(s))
        geul("어디까지: " + s.s("to").trim().ifEmpty { "아직 적지 않음 (아래 어디까지 칸에 적으십시오)" })
        val sigan = geul("시간: " + k.spotMinTxt(s))
        allimJul()
        geul("경계와 시간", jemok = true)
        if (s.kind.isEmpty()) kan("지점 이름", s.name, yeoreo = false) { s.name = it }.isEnabled = !ro
        kan(k.spotToName(s), s.s("to")) { s.d.put("to", it) }.isEnabled = !ro
        kan("이동 시간(분) — " + (if (s.kind == "open") "첫 만남 자리에서 오프닝 자리까지" else "앞 지점을 떠나 여기까지"), (s.n("walkIn") ?: 0.0).toInt().toString(), susja = true) {
            s.d.put("walkIn", maxOf(0, it.toIntOrNull() ?: 0)); k.syncMove(); sigan.text = "시간: " + k.spotMinTxt(s)
        }.isEnabled = !ro
        kan("정지 현장영상해설 시간(분)" + (if (s.kind.isEmpty()) "" else " — 0이면 대본 읽는 시간으로 셉니다"), (s.n("stay") ?: 0.0).toInt().toString(), susja = true) {
            s.d.put("stay", maxOf(0, it.toIntOrNull() ?: 0)); sigan.text = "시간: " + k.spotMinTxt(s)
        }.isEnabled = !ro
        geul("현장 자료", jemok = true)
        kan("이동 구간 안내 (계단·경사·점자블록·엘리베이터)", s.s("access")) { s.d.put("access", it) }.isEnabled = !ro
        kan("만져 볼 수 있는 것", s.s("touch")) { s.d.put("touch", it) }.isEnabled = !ro
        kan("조심할 것 · 화장실 · 쉼터", s.s("safe")) { s.d.put("safe", it) }.isEnabled = !ro
        kan("현장영상해설사 위치·현장영상해설 순서 (8번)", s.s("gpos")) { s.d.put("gpos", it) }.isEnabled = !ro
        geul(if (s.kind == "open") "오프닝 지점 대본" else if (s.kind == "close") "클로징 지점 대본" else "이 지점 현장영상해설 글 (대본)", jemok = true)
        var fb: TextView? = null
        val daebon = kan("대본", k.spotText(s)) { k.setSpotText(s, it); fb?.text = "${it.count { c -> !c.isWhitespace() }}자, 읽는 데 ${k.readSec(it)}초쯤" }
        daebon.minLines = 6; daebon.isEnabled = !ro
        val t = k.spotText(s)
        fb = geul("${t.count { !it.isWhitespace() }}자, 읽는 데 ${k.readSec(t)}초쯤", jakge = true)
        if (!ro) {
            geul("방향 도우미 (대본 끝에 붙습니다)", jemok = true)
            jangMok("bh", BANGHYANG, per = 4) { w ->
                danchu("$w 붙이기") {
                    val x = k.spotText(s); val y = x + (if (x.isEmpty() || x.endsWith(" ")) "" else " ") + w
                    daebon.setText(y); daebon.setSelection(y.length); malhagi("${w}을 붙였습니다.")
                }
            }
        }
        geul("사진 ${s.shots.length()}장", jemok = true)
        if (s.shots.length() == 0) geul("아직 사진이 없습니다. 답사 탭에서 찍으십시오.")
        val shots = (0 until s.shots.length()).mapNotNull { s.shots.optJSONObject(it) }
        jangMok("sj", shots.withIndex().toList()) { (i, o) -> sajinJul(k.id, i + 1, o).apply { setOnClickListener { go(Hw.Sajin(s.id, i)) } } }
        geul("현장 메모", jemok = true)
        danchu("담긴 메모 ${s.d.optJSONArray("memos")?.length() ?: 0}개 보기와 듣기") { jang.remove("mm"); go(Hw.Memos(s.id)) }
        geul("옮기기", jemok = true)
        k.spotPrev(s)?.let { p -> danchu("앞 지점 — " + k.spotFull(p)) { olgim(p.id) } }
        k.spotNext(s)?.let { n -> danchu("다음 지점 — " + k.spotFull(n)) { olgim(n.id) } }
        if (s.kind.isEmpty() && !ro) {
            danchu("이 지점을 한 칸 앞으로") { if (k.moveSpot(s, -1)) { bakkwim(); malhagi(k.spotFull(s) + "이 되었습니다."); draw() } }
            danchu("이 지점을 한 칸 뒤로") { if (k.moveSpot(s, 1)) { bakkwim(); malhagi(k.spotFull(s) + "이 되었습니다."); draw() } }
        }
        danchu("지금 저장") { save(false) }
        if (s.kind.isEmpty() && !ro) {
            danchu("이 지점 지우기") {
                mutgi(s.name + "을(를) 지울까요? 담은 사진 기록도 함께 지워집니다.", "지우기") {
                    val pv = k.spotPrev(s); deleteSpot(s)
                    val st = stacks[tab]; if (pv != null) st[st.size - 1] = Hw.Spot(pv.id) else st.removeAt(st.size - 1)
                    draw()
                }
            }
        }
        t0.tag = "meori"
    }
    private fun olgim(id: String) {
        save(true)
        val st = stacks[tab]; st[st.size - 1] = Hw.Spot(id); curId = id; jang.remove("sj"); jang.remove("bh")
        kos?.let { k -> k.spot(id)?.let { malhagi(k.spotFull(it) + "입니다.") } }
        draw(); scroll.scrollTo(0, 0); cheotJul(body.findViewWithTag<View>("meori"))
    }
    private fun sajinJul(kosId: String, beon: Int, o: JSONObject): View {
        val f = o.geul("f"); val dir = o.geul("dir")
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)); background = kadeuBg()
            isFocusable = true; isClickable = true; contentDescription = "${beon}번째 사진" + (if (dir.isEmpty()) "" else ", $dir") + (if (f.isEmpty()) " (아직 못 올림)" else "") + ". 두드리면 크게 보기·지우기·다시 찍기"
        }
        val iv = ImageView(this).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO; scaleType = ImageView.ScaleType.CENTER_CROP }
        row.addView(iv, LinearLayout.LayoutParams(dp(88), dp(66)))
        row.addView(TextView(this).apply { text = row.contentDescription; setPadding(dp(12), 0, 0, 0); setTextColor(Saek.geulja(this@TourActivity)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO })
        body.addView(row, lp(6))
        if (f.isNotEmpty()) pool.execute {
            try {
                val u = Api.fileUrl("photo", kosId, f, Api.needTicket())
                val bm = URL(u).openStream().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 }) }
                main.post { iv.setImageBitmap(bm) }
            } catch (e: Exception) { }
        }
        return row
    }

    // ── 답사 ──
    private fun dapsa() {
        gyeonggye(); allimJul()
        val k = kos ?: return kosEopseum()
        val s = spot() ?: return kosEopseum()
        val t0 = geul(k.spotFull(s), jemok = true); t0.tag = "meori"
        geul("어디서부터: " + k.spotFrom(s))
        geul("사진 ${s.shots.length()}장" + if (s.lat != null) ", 자리 적힘" else ", 자리 아직 없음")
        if (!k.isGyobon) {
            geul("경계 적기", jemok = true)
            val nx = k.spotNext(s)
            if (nx != null) danchu("여기서부터 " + k.spotFull(nx) + " 시작", keun = true) { boundGo() }
            else geul("클로징 지점이 마지막입니다. 해산하는 자리를 아래 어디까지 칸에 적으십시오.")
            kan(k.spotLab(s) + " " + k.spotToName(s), s.s("to")) { s.d.put("to", it) }
            val ir = EditText(this).apply { hint = "새 지점 이름 (없어도 됩니다)"; background = kadeuBg(); setPadding(dp(14), dp(12), dp(14), dp(12)) }
            body.addView(ir, lp(4))
            danchu("여기서부터 새 지점 시작 (" + (if (s.kind == "close") "클로징 지점 바로 앞에" else k.spotLab(s) + " 바로 뒤에") + " 넣기)") { addHere(ir.text.toString().trim()) }
            geul("사진 찍기 — 시계 방향으로 네 쪽", jemok = true)
            geul("정지 현장영상해설 자리에 서서 참여자가 바라볼 쪽을 12시로 두고 찍으십시오. 첫 사진을 찍을 때 이 지점의 자리가 적히고, 앞 지점에서 걸어온 거리로 이동 시간을 잡습니다.", jakge = true)
            val jjigeun = (0 until s.shots.length()).map { s.shots.optJSONObject(it)?.geul("dir") ?: "" }.toSet()
            NE.forEach { b -> danchu(b + " 사진 찍기" + if (jjigeun.contains(b)) " (찍음)" else "", keun = b == sajinBang) { kamera(b) } }
            val nameun = NE.filter { !jjigeun.contains(it) }
            geul(if (nameun.isEmpty()) "네 방향을 다 담았습니다. 다음 지점으로 옮기셔도 됩니다." else "아직 담지 않은 방향: " + nameun.joinToString(", "))
            bangeumSajin(k)
            geul("담긴 사진 보기 — ${s.shots.length()}장 (두드리면 크게 보기·지우기·다시 찍기)", jemok = true)
            val shots = (0 until s.shots.length()).mapNotNull { s.shots.optJSONObject(it) }
            jangMok("dsj", shots.withIndex().toList()) { (i, o) -> sajinJul(k.id, i + 1, o).apply { setOnClickListener { go(Hw.Sajin(s.id, i)) } } }
            memoSection(k, s)
        }
        geul("지점 옮겨 보기", jemok = true)
        k.spotPrev(s)?.let { p -> danchu("앞 지점 — " + k.spotFull(p)) { curId = p.id; draw(); cheotJul(body.findViewWithTag<View>("meori")) } }
        k.spotNext(s)?.let { n -> danchu("다음 지점 보기 — " + k.spotFull(n) + " (경계는 적지 않음)") { curId = n.id; draw(); cheotJul(body.findViewWithTag<View>("meori")) } }
    }
    private fun nowIso(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
    private fun r6(x: Double) = Math.round(x * 1e6) / 1e6
    private fun boundGo() {
        val k = kos ?: return; val c = spot() ?: return
        val nx = k.spotNext(c) ?: run { malhagi("클로징 지점이 마지막입니다. 클로징 지점의 어디까지 칸에 해산하는 자리를 적어 주십시오."); return }
        markBound(c, nx); curId = nx.id
        malhagi("여기서부터 " + k.spotFull(nx) + "입니다. " + k.spotFull(c) + "은 여기까지입니다." +
            (if (c.s("to").isBlank()) " " + k.spotLab(c) + "의 어디까지 칸에 이 자리를 적어 두십시오." else "") +
            (if (nx.kind == "close") " 해산하는 자리에서 클로징을 마치십시오." else " 정지 현장영상해설 자리에 서시면 사진 찍기를 누르십시오."))
        bakkwim(); draw(); cheotJul(body.findViewWithTag<View>("meori"))
    }
    private fun addHere(name: String) {
        val k = kos ?: return
        val s = k.addSpot(spot(), name); val pv = k.spotPrev(s)
        markBound(pv, s); k.syncMove(); curId = s.id
        malhagi("여기서부터 " + k.spotFull(s) + "입니다. " + (if (pv != null) k.spotLab(pv) + "은 여기까지입니다. " else "") + "지점은 모두 ${k.spots.size}곳입니다." +
            (if (name.isEmpty()) " 이름은 지점 탭에서 적으십시오." else ""))
        bakkwim(); draw(); cheotJul(body.findViewWithTag<View>("meori"))
    }
    private fun markBound(p: Spot?, q: Spot?) {
        val at = nowIso()
        p?.d?.put("endAt", at); q?.d?.put("startAt", at)
        jari { l -> if (l != null) { val pos = JSONObject().put("lat", r6(l.latitude)).put("lng", r6(l.longitude)).put("at", at); p?.d?.put("endPos", pos); q?.d?.put("startPos", JSONObject(pos.toString())); bakkwim() } }
    }

    // ── 자리(위치) ──
    private var jariGidarim: ((Location?) -> Unit)? = null
    @Suppress("DEPRECATION")
    private fun jari(kkeut: (Location?) -> Unit) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            jariGidarim = kkeut; requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), HEORAK_JARI); return
        }
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val pr = if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
        var kkeutnam = false
        val han: (Location?) -> Unit = { l -> if (!kkeutnam) { kkeutnam = true; main.post { kkeut(l) } } }
        try {
            if (Build.VERSION.SDK_INT >= 30) lm.getCurrentLocation(pr, null, mainExecutor) { han(it) }
            else lm.requestSingleUpdate(pr, { l: Location -> han(l) }, Looper.getMainLooper())
            main.postDelayed({ han(try { lm.getLastKnownLocation(pr) } catch (e: SecurityException) { null }) }, 12000)
        } catch (e: SecurityException) { han(null) } catch (e: Exception) { han(null) }
    }
    private fun spotPosHere(s: Spot, l: Location?) {
        val k = kos ?: return
        if (l == null || s.lat != null) return
        s.d.put("lat", r6(l.latitude)).put("lng", r6(l.longitude))
        val p = k.spotPrev(s)
        metersBetween(k.posOf(p), k.posOf(s))?.let { m ->
            val w = k.walkMinutes(m); s.d.put("distIn", m).put("walkIn", w); k.syncMove()
            malhagi(k.spotLab(p) + "에서 ${m.toInt()}미터 걸어오셨습니다. " + k.spotLab(s) + "의 이동 시간을 ${w}분으로 잡았습니다.")
        }
        bakkwim()
    }

    // ── 사진 ──
    private fun kamera(b: String) {
        sajinBang = b
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.CAMERA), HEORAK_KAMERA); return }
        try {
            val dir = File(cacheDir, "sajin").apply { mkdirs() }
            val f = File(dir, "jjikgi.jpg"); if (f.exists()) f.delete()
            sajinFile = f
            val u = FileProvider.getUriForFile(this, "$packageName.sajin", f)
            val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, u).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivityForResult(i, SAJIN)
        } catch (e: Exception) { malhagi("카메라를 열지 못했습니다.") }
    }
    override fun onRequestPermissionsResult(rq: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(rq, p, g)
        val ok = g.isNotEmpty() && g[0] == PackageManager.PERMISSION_GRANTED
        when (rq) {
            HEORAK_KAMERA -> if (ok) kamera(sajinBang) else malhagi("카메라를 허락하지 않으셔서 사진을 찍을 수 없습니다.")
            HEORAK_JARI -> { val k = jariGidarim; jariGidarim = null; if (ok && k != null) jari(k) else k?.invoke(null) }
            HEORAK_MIC -> { val k = micGidarim; micGidarim = null; if (ok) k?.invoke() else malhagi("마이크를 허락하지 않으셔서 녹음할 수 없습니다. 폰 설정의 투어스튜디오에서 마이크를 켜 주십시오.") }
        }
    }
    @Deprecated("사진 받기")
    override fun onActivityResult(rq: Int, rs: Int, data: Intent?) {
        super.onActivityResult(rq, rs, data)
        if (rq != SAJIN) return
        val f = sajinFile
        if (rs != RESULT_OK || f == null || !f.exists() || f.length() < 1000) { malhagi("사진을 찍지 않았습니다."); return }
        val k = kos ?: return; val s = spot() ?: return
        val dir = sajinBang
        malhagi("사진을 올립니다.")
        jari { l ->
            spotPosHere(s, l)
            pool.execute {
                val jpg = jureugi(f)
                if (jpg == null) { main.post { malhagi("사진을 읽지 못했습니다.") }; return@execute }
                val j = Api.call("save_photo", JSONObject().put("c", k.id).put("s", s.id).put("ext", "jpg").put("data", Base64.encodeToString(jpg.first, Base64.NO_WRAP)), timeoutMs = 90000)
                main.post {
                    if (Api.ok(j)) {
                        val shot = JSONObject().put("f", j.geul("file")).put("dir", dir).put("garo", jpg.second).put("sero", jpg.third).put("at", nowIso()).put("zoom", 1).put("app", "android")
                        val la = s.lat; val lo = s.lng
                        if (la != null && lo != null) shot.put("lat", la).put("lng", lo) else shot.put("lat", JSONObject.NULL).put("lng", JSONObject.NULL)
                        s.shots.put(shot); lastShot = s.id to (s.shots.length() - 1)
                        val jjigeun = (0 until s.shots.length()).map { s.shots.optJSONObject(it)?.geul("dir") ?: "" }.toSet()
                        val i = NE.indexOf(dir)
                        val da = (NE.drop(i + 1) + NE.take(maxOf(0, i))).firstOrNull { !jjigeun.contains(it) }
                        if (da != null) sajinBang = da
                        malhagi(k.spotLab(s) + " 사진 ${s.shots.length()}번째, $dir 올렸습니다." + if (da != null) " 몸을 시계 방향으로 돌리신 뒤 찍으십시오. 다음은 ${da}입니다." else " 네 방향을 다 담았습니다.")
                        bakkwim(); draw()
                    } else {
                        // 못 올린 사진은 폰에 남겨 두고, 코스에는 「아직 못 올림」으로 적어 둠(웹 qref 와 같음)
                        val ref = Daegi.saeRef("photo")
                        Daegi.neoki(this, JSONObject().put("ref", ref).put("jong", "photo").put("c", k.id).put("s", s.id).put("ext", "jpg").put("kind", "").put("at", nowIso()), jpg.first)
                        val shot = JSONObject().put("f", "").put("qref", ref).put("dir", dir).put("garo", jpg.second).put("sero", jpg.third).put("at", nowIso()).put("zoom", 1).put("app", "android")
                        val la = s.lat; val lo = s.lng
                        if (la != null && lo != null) shot.put("lat", la).put("lng", lo) else shot.put("lat", JSONObject.NULL).put("lng", JSONObject.NULL)
                        s.shots.put(shot); lastShot = s.id to (s.shots.length() - 1)
                        malhagi("사진을 올리지 못해 폰에 남겨 두었습니다. 인터넷이 되면 설정의 못 올린 것 지금 올리기로 올리십시오. 이 코스를 다시 열 때도 저절로 올립니다. " + Api.err(j))
                        bakkwim(); draw()
                    }
                }
            }
        }
    }
    /** 긴 쪽을 1600점으로 줄이고 바로 세움 — (jpg, 가로, 세로) */
    private fun jureugi(f: File): Triple<ByteArray, Int, Int>? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var smp = 1; while (maxOf(o.outWidth, o.outHeight) / (smp * 2) >= 1600) smp *= 2
        var bm: Bitmap = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = smp })
        val rot = when (ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f; ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f
        }
        val m = maxOf(bm.width, bm.height)
        val r = if (m > 1600) 1600f / m else 1f
        if (rot != 0f || r < 1f) {
            val mx = Matrix().apply { postScale(r, r); postRotate(rot) }
            bm = Bitmap.createBitmap(bm, 0, 0, bm.width, bm.height, mx, true)
        }
        val out = ByteArrayOutputStream(); bm.compress(Bitmap.CompressFormat.JPEG, 82, out)
        Triple(out.toByteArray(), bm.width, bm.height)
    } catch (e: Exception) { null }

    // ── 현장영상해설 ──
    private fun haeseol() {
        allimJul()
        val k = kos ?: return kosEopseum()
        val s = spot() ?: return kosEopseum()
        val t0 = geul(k.spotFull(s), jemok = true); t0.tag = "meori"
        geul("어디서부터: " + k.spotFrom(s))
        geul("어디까지: " + s.s("to").ifEmpty { "적지 않음" })
        geul("시간: " + k.spotMinTxt(s))
        if (s.s("gpos").isNotEmpty()) geul("현장영상해설사 위치: " + s.s("gpos"))
        if (s.s("access").isNotEmpty()) geul("이동 구간 안내: " + s.s("access"))
        if (s.s("safe").isNotEmpty()) geul("조심할 것: " + s.s("safe"))
        geul("대본", jemok = true)
        val t = k.spotText(s)
        geul(t.ifEmpty { "대본이 아직 없습니다." }).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f); setTextIsSelectable(true) }
        if (t.isNotEmpty()) {
            danchu("폰 목소리로 듣기 (연습용)") { if (ttsOk) tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "daebon") else malhagi("폰 목소리를 쓸 수 없습니다.") }
            danchu("멈추기") { tts?.stop() }
        }
        yeonseupSection(k, s)
        k.spotNext(s)?.let { n -> danchu("다음 지점 — " + k.spotFull(n), keun = true) { tts?.stop(); curId = n.id; draw(); cheotJul(body.findViewWithTag<View>("meori")) } }
        k.spotPrev(s)?.let { p -> danchu("앞 지점 — " + k.spotFull(p)) { tts?.stop(); curId = p.id; draw(); cheotJul(body.findViewWithTag<View>("meori")) } }
        geul("지점 차례", jemok = true)
        jangMok("hs", k.allSpots) { x ->
            danchu(k.spotFull(x) + ", " + k.spotMinTxt(x) + if (x.id == s.id) " (지금)" else "") { tts?.stop(); curId = x.id; draw(); cheotJul(body.findViewWithTag<View>("meori")) }
        }
    }

    // ── 설정 ──
    private fun seoljeong() {
        geul("설정", jemok = true)
        danchu("새로고침") {
            loadCourses { kos?.id?.let { openKos(it) }; malhagi("새로 받았습니다. 코스 ${courses.size}개입니다.") }
        }
        danchu(Ollim.seoljeongMal(this)) { Ollim.seoljeongNureum(this) }
        if (Ollim.allim.isNotEmpty()) geul(Ollim.allim, jakge = true)
        danchu(if (malsori) "앱 안내 말소리 — 켜짐. 누르면 끕니다" else "앱 안내 말소리 — 꺼짐. 누르면 켭니다") {
            sp.edit().putBoolean("malsori", !malsori).apply(); draw(); scroll.announceForAccessibility(if (malsori) "앱 안내 말소리를 켰습니다." else "앱 안내 말소리를 껐습니다.")
        }
        if (kos != null) danchu("폰에 남은 것 올리기") { draftOllim() }
        val ds = Daegi.su(this)
        danchu(if (ds > 0) "못 올린 것 ${ds}개 — 지금 올리기" else "못 올린 것이 없습니다 — 살펴보기") { daegiOllim(false) }
        allimJul()
        geul("도움말 찾기", jemok = true)
        val q = EditText(this).apply { hint = "찾을 낱말 (예: 경계, 사진, 저장)"; setText(doumQ); background = kadeuBg(); setPadding(dp(14), dp(12), dp(14), dp(12)) }
        body.addView(q, lp(4))
        danchu("찾기") { doumQ = q.text.toString().trim(); jang.remove("dm"); draw(); cheotJul(jangCheotJari["dm"]) }
        val L = DOUM.withIndex().filter { doumQ.isEmpty() || (it.value.first + it.value.second).contains(doumQ) }
        if (L.isEmpty()) geul("찾지 못했습니다. 다른 낱말로 찾아 보십시오.")
        jangMok("dm", L) { x -> danchu(x.value.first) { go(Hw.Doum(x.index)) } }
        geul("이 앱", jemok = true)
        val nm = me?.geul("name") ?: ""
        geul(if (nm.isEmpty()) "들어온 사람 없음" else "$nm 님이 들어와 있습니다" + if (me?.geul("role") == "master") " (최고관리자)" else "")
        danchu("웹 투어스튜디오 열기") { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://lvd.ada.or.kr/lvdts/lvdts.html"))) }
        danchu("무엇이 바뀌었나") { go(Hw.Bakkwin) }
        geul(PAN_IREUM, jakge = true)
        danchu("나가기") { naga("나갔습니다.") }
        body.addView(LogoView(this), LinearLayout.LayoutParams(dp(120), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16); gravity = Gravity.CENTER_HORIZONTAL })
    }
    private var doumQ = ""
    private fun doum(i: Int) { val d = DOUM.getOrNull(i) ?: return; geul(d.first, jemok = true); geul(d.second) }
    private fun bakkwin() {
        geul("무엇이 바뀌었나", jemok = true)
        geul("1.1.0판 (빌드 261017-T3, 2026-10-17)", jemok = true)
        listOf(
            "웹 투어스튜디오에 있는데 앱에 없던 것 가운데 현장에 꼭 필요한 열네 가지를 넣었습니다(이사장님 승인). 아이폰 1.1.0판과 같습니다.",
            "사진: 방금 찍은 사진과 담긴 사진을 크게 보기, 지우기, 같은 방향 다시 찍기.",
            "지점: 이 지점 지우기.",
            "현장 메모: 말로 적기(녹음과 받아쓰기), 글로 적기, 담긴 메모 보기와 듣기.",
            "연습: 지점별 읽으며 녹음, 걸린 시간·빠르기·시각 전제 표현 되돌아보기, 연습 녹음 듣기. 녹음은 mp3로 올립니다.",
            "못 올린 사진과 소리를 폰에 남겨 두었다가 다시 올리기.",
            "코스: 관리자에게 봐 달라고 알리기, 판 되돌리기, 이 코스 지우기, 휴지통에서 되살리기, 분야·지역·참가 인원·현장영상해설사 수.",
            "도움말 열세 가지를 더했습니다."
        ).forEach { geul(it) }
        geul("1.0.1판 (빌드 261014-T2, 2026-10-14)", jemok = true)
        listOf(
            "사진을 올린 뒤 안내를 시계 방향으로 바로잡았습니다. 몸을 시계 방향으로 돌리신 뒤 찍으십시오(이사장님 승인).",
            "방향 도우미처럼 네 개씩 보이는 목록의 더 보기와 이전 보기를 「넷」으로 바로잡았습니다.",
            "원칙 16개 점검의 마침 안내를 「점검표에서 확인하십시오」로 바로잡았습니다(이사장님 승인)."
        ).forEach { geul(it) }
        geul("1.0.0판 (빌드 261012-T1, 2026-10-12)", jemok = true)
        listOf(
            "웹 투어스튜디오 1.12판을 안드로이드 앱으로 처음 지었습니다(이사장님 승인). 아이폰 1.0.0판과 같습니다.",
            "탭 다섯: 코스, 답사, 지점, 현장영상해설, 설정.",
            "지점은 어디서부터 어디까지로 정합니다. 오프닝 지점과 클로징 지점도 지점이고, 지점 사이의 이동은 다음 지점의 몫입니다.",
            "답사: 여기서부터 다음 지점 시작, 여기서부터 새 지점 시작, 시계 방향 네 쪽 사진.",
            "지점 한 화면, 방향 도우미, 원칙 16개 점검(작성원칙 통합본), 협회 승인 요청, 저절로 저장과 폰에 남겨 두기.",
            "앱 안의 말은 모두 현장영상해설과 현장영상해설사로 맞췄습니다."
        ).forEach { geul(it) }
    }

    // ================= 1.1.0판 (261017-T3) — 웹에 있던 것을 앱에도 (아이폰 Sae110Views·TourStore 와 같음) =================
    /** 하나 고르기 — 번호 목록 대화창 */
    private fun goreugi(jemok: String, mok: List<String>, kkeut: (String) -> Unit) {
        AlertDialog.Builder(this).setTitle(jemok + " 고르기")
            .setItems(mok.map { it.ifEmpty { "고르지 않음" } }.toTypedArray()) { _, i -> kkeut(mok[i]) }
            .setNegativeButton("그만두기", null).show()
    }
    /** 한 번 묻기 — 지우기·되돌리기처럼 되돌리기 어려운 일 */
    private fun mutgi(mal: String, ye: String, kkeut: () -> Unit) {
        AlertDialog.Builder(this).setMessage(mal).setPositiveButton(ye) { _, _ -> kkeut() }.setNegativeButton("그만두기", null).show()
    }
    private fun shotsOf(s: Spot) = (0 until s.shots.length()).mapNotNull { s.shots.optJSONObject(it) }
    private fun memosOf(s: Spot): List<JSONObject> { val a = s.d.optJSONArray("memos") ?: return listOf(); return (0 until a.length()).mapNotNull { a.optJSONObject(it) } }

    /** 사진 지우기 — 웹 deleteShot 과 같음(되돌릴 수 없음) */
    private fun deletePhoto(s: Spot, i: Int) {
        val k = kos ?: return
        if (k.isGyobon) { malhagi("교본의 사진은 지울 수 없습니다."); return }
        val a = shotsOf(s); if (i < 0 || i >= a.size) return
        val shot = a[i]
        s.d.put("shots", JSONArray().apply { a.forEachIndexed { j, o -> if (j != i) put(o) } })
        val f = shot.geul("f"); val q = shot.geul("qref"); val dir = shot.geul("dir")
        if (f.isNotEmpty()) pool.execute { Api.call("delete_photo", JSONObject().put("c", k.id).put("f", f)) }
        if (q.isNotEmpty()) Daegi.ppaegi(this, q)
        lastShot?.let { if (it.first == s.id && it.second == i) lastShot = null }
        malhagi((if (dir.isEmpty()) "" else "$dir ") + "사진을 지웠습니다. " + s.name + " 지점에 ${a.size - 1}장 남았습니다.")
        bakkwim()
    }
    /** 이 지점 지우기 — 웹 spotDel 과 같음(오프닝·클로징 지점은 지우지 않음) */
    private fun deleteSpot(s: Spot) {
        val k = kos ?: return
        if (k.isGyobon || s.kind.isNotEmpty()) return
        val pv = k.spotPrev(s); val nm = s.name
        k.spots.remove(s); k.syncMove()
        if (curId == s.id && pv != null) curId = pv.id
        lastShot?.let { if (it.first == s.id) lastShot = null }
        jang.remove("jj")
        malhagi(nm + "을(를) 지웠습니다. 지점은 모두 ${k.spots.size}곳입니다.")
        bakkwim()
    }

    // ---- 사진 크게 보기 ----
    private fun sajinHwamyeon(spotId: String, idx: Int) {
        val k = kos ?: return kosEopseum()
        val s = k.spot(spotId)
        val a = s?.let { shotsOf(it) } ?: listOf()
        if (s == null || idx >= a.size) { geul("이 사진은 지워졌습니다. 뒤로 가십시오."); return }
        val p = a[idx]; val dir = p.geul("dir"); val f = p.geul("f")
        val t0 = geul(k.spotFull(s) + ", ${a.size}장 가운데 ${idx + 1}번째, " + dir.ifEmpty { "방위 모름" } + if (f.isEmpty()) ", 아직 나스에 올리지 못함" else "", jemok = true)
        allimJul()
        if (f.isNotEmpty()) {
            val iv = ImageView(this).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = k.spotLab(s) + " " + dir + " 사진, 함께 가는 분이 보실 수 있습니다" }
            body.addView(iv, lp(8))
            pool.execute {
                try {
                    val u = Api.fileUrl("photo", k.id, f, Api.needTicket())
                    val bm = URL(u).openStream().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 }) }
                    main.post { iv.setImageBitmap(bm) }
                } catch (e: Exception) { main.post { malhagi("사진을 불러오지 못했습니다.") } }
            }
        } else geul("이 사진은 폰에 남아 있고 아직 나스에 올라가지 않았습니다. 설정의 못 올린 것 지금 올리기로 올리십시오.")
        if (!k.isGyobon) {
            danchu("이 사진 지우기") {
                mutgi("사진을 지웁니다. 되돌릴 수 없습니다. 지울까요?", "지우기") { deletePhoto(s, idx); onBackPressed() }
            }
            if (dir.isNotEmpty()) danchu("이 방향 다시 찍기 ($dir)") { dasiJjikgi(s, idx, dir) }
        }
        t0.tag = "meori"; cheotJul(t0)
    }
    /** 같은 방향 다시 찍기 — 이 사진을 지우고 같은 방향으로 카메라를 엶(웹 redoShot 과 같음) */
    private fun dasiJjikgi(s: Spot, idx: Int, dir: String) {
        deletePhoto(s, idx); curId = s.id
        val st = stacks[tab]; if (st.size > 1 && st.last() is Hw.Sajin) st.removeAt(st.size - 1)
        draw()
        malhagi("같은 방향으로 다시 찍습니다. ${dir}을 바라보고 찍으십시오.")
        main.postDelayed({ kamera(dir) }, 800)
    }
    /** 방금 찍은 사진 (답사 탭) */
    private fun bangeumSajin(k: Kos) {
        val l = lastShot ?: return
        val s = k.spot(l.first) ?: return
        val a = shotsOf(s); if (l.second >= a.size) return
        val dir = a[l.second].geul("dir")
        geul("방금 찍은 사진 — " + k.spotLab(s) + " " + dir, jemok = true)
        danchu("방금 찍은 사진 크게 보기") { go(Hw.Sajin(s.id, l.second)) }
        danchu("이 사진 지우기") { mutgi("사진을 지웁니다. 되돌릴 수 없습니다. 지울까요?", "지우기") { deletePhoto(s, l.second); draw() } }
        if (dir.isNotEmpty()) danchu("이 방향 다시 찍기 ($dir)") { dasiJjikgi(s, l.second, dir) }
    }

    // ---- 녹음 (현장 메모·연습) ----
    private fun micHeorak(kkeut: () -> Unit) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { kkeut(); return }
        micGidarim = kkeut; requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), HEORAK_MIC)
    }
    private fun nokeumSijak(galle: String, s: Spot, where_: String, mal: String) {
        micHeorak {
            tts?.stop()
            val e = Nokeumgi.start(this, galle, s.id, where_)
            if (e != null) malhagi(e) else malhagi(mal)
            draw()
        }
    }
    /** 녹음 끝 — 담은 소리를 mp3 로 바꾸고 올림. 못 올리면 폰에 남김 */
    private fun nokeumKkeut() {
        val galle = Nokeumgi.galle; val sid = Nokeumgi.spotId; val w = Nokeumgi.where_
        malhagi("녹음을 끝냈습니다. 담은 소리를 mp3로 바꾸고 있습니다.")
        pool.execute {
            val r = Nokeumgi.stop()
            main.post { draw() }
            if (r == null) return@execute
            val (wav, sec, heard) = r
            val k = kos; val s = k?.spot(sid)
            if (k == null || s == null || k.isGyobon) { wav.delete(); return@execute }
            if (sec < 1) { wav.delete(); main.post { malhagi("담긴 소리가 없습니다.") }; return@execute }
            val mp3 = Mp3Gi.bakkugi(this, wav)
            val data = mp3 ?: wav.readBytes(); val ext = if (mp3 != null) "mp3" else "wav"
            wav.delete()
            val kind = if (galle == "prac") "prac" else "memo"
            val j = Api.call("save_audio", JSONObject().put("c", k.id).put("s", s.id).put("kind", kind).put("ext", ext).put("data", Base64.encodeToString(data, Base64.NO_WRAP)), timeoutMs = 120000)
            var f = ""; var q = ""
            if (Api.ok(j)) f = j.geul("file")
            else { q = Daegi.saeRef("audio"); Daegi.neoki(this, JSONObject().put("ref", q).put("jong", "audio").put("c", k.id).put("s", s.id).put("ext", ext).put("kind", kind).put("at", nowIso()), data) }
            main.post {
                if (kind == "memo") memoMal(k, s, w, f, q, sec, heard) else yeonseupDam(k, s, f, q, sec, heard)
                draw()
            }
        }
    }
    private fun kanBuchigi(k: Kos, s: Spot, where_: String, t: String) {
        if (where_ == "text") { val o = k.spotText(s); k.setSpotText(s, if (o.isEmpty()) t else "$o $t") }
        else { val o = s.s(where_); s.d.put(where_, if (o.isEmpty()) t else "$o $t") }
    }
    /** 현장 메모 말로 적기 — 웹 afterAudio memo 와 같음 */
    private fun memoMal(k: Kos, s: Spot, where_: String, f: String, q: String, sec: Int, heard: String) {
        val a = s.d.optJSONArray("memos") ?: JSONArray().also { s.d.put("memos", it) }
        a.put(JSONObject().put("f", f).put("qref", q).put("sec", sec).put("text", heard).put("where", where_).put("at", nowIso()))
        if (heard.isNotEmpty()) kanBuchigi(k, s, where_, heard)
        var m = if (heard.isEmpty()) "${sec}초 담았습니다. 받아쓰기가 되지 않아 소리로만 남겼습니다. 나중에 들어 보실 수 있습니다."
                else "${sec}초 말씀하셨습니다. 글로 옮겨 " + MemoKan.ireum(where_) + " 칸에 넣었습니다. " + heard
        if (q.isNotEmpty()) m += " 소리는 아직 올리지 못해 폰에 남겨 두었습니다."
        malhagi(m); bakkwim()
    }
    /** 현장 메모 글로 적기 — 웹 memoTextPut 과 같음 */
    private fun memoGeulDam(k: Kos, s: Spot, where_: String, text: String) {
        val tx = text.trim()
        if (tx.isEmpty()) { malhagi("적으신 글이 없습니다. 글 칸에 적고 다시 누르십시오."); return }
        val a = s.d.optJSONArray("memos") ?: JSONArray().also { s.d.put("memos", it) }
        a.put(JSONObject().put("f", "").put("qref", "").put("sec", 0).put("text", tx).put("where", where_).put("at", nowIso()).put("typed", 1))
        kanBuchigi(k, s, where_, tx)
        memoGeul = ""
        malhagi("글로 담았습니다. " + MemoKan.ireum(where_) + " 칸에 넣었습니다."); bakkwim()
    }
    /** 지점별 읽으며 녹음 — 웹 pracReview 와 같음 */
    private fun yeonseupDam(k: Kos, s: Spot, f: String, q: String, sec: Int, heard: String) {
        s.d.put("real", sec).put("pracFile", f)
        if (q.isNotEmpty()) s.d.put("pracQref", q) else s.d.remove("pracQref")
        k.d.put("practiced", true)
        s.d.put("prac", Yeonseup.review(k, s, sec, heard, nowIso()))
        malhagi(Yeonseup.mal(k, s) + if (q.isEmpty()) "" else " 소리는 아직 올리지 못해 폰에 남겨 두었습니다.")
        bakkwim()
    }
    /** 현장 메모 칸 (답사 탭) */
    private fun memoSection(k: Kos, s: Spot) {
        if (k.isGyobon) return
        geul("현장 메모 — " + k.spotLab(s) + " (말로 적기 · 글로 적기)", jemok = true)
        danchu("어디에 담을지 — " + MemoKan.ireum(memoKan) + ". 누르면 고릅니다") {
            AlertDialog.Builder(this).setTitle("어디에 담을지 고르기")
                .setItems(MemoKan.mok.map { it.second }.toTypedArray()) { _, i -> memoKan = MemoKan.mok[i].first; malhagi(MemoKan.mok[i].second + " 칸에 담습니다."); draw() }
                .setNegativeButton("그만두기", null).show()
        }
        if (Nokeumgi.jung && Nokeumgi.galle == "memo") danchu("다 말했습니다 (녹음 끝)", keun = true) { nokeumKkeut() }
        else danchu("말로 적기 시작") {
            if (Nokeumgi.jung) malhagi("이미 녹음 중입니다. 먼저 끝내 주십시오.")
            else nokeumSijak("memo", s, memoKan, "녹음을 시작했습니다. 말씀하신 뒤 다 말했습니다를 누르십시오.")
        }
        val e = EditText(this).apply {
            hint = "글로 적기 (" + MemoKan.ireum(memoKan) + " 칸에 붙습니다)"; setText(memoGeul)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; minLines = 2; isSingleLine = false
            background = kadeuBg(); setPadding(dp(14), dp(12), dp(14), dp(12)); setTextColor(Saek.geulja(this@TourActivity))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(x: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(x: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(x: Editable?) { memoGeul = x?.toString() ?: "" }
            })
        }
        body.addView(e, lp(4))
        danchu("글로 담기") { memoGeulDam(k, s, memoKan, memoGeul); draw() }
        danchu("담긴 메모 ${memosOf(s).size}개 보기와 듣기") { jang.remove("mm"); go(Hw.Memos(s.id)) }
    }
    /** 담긴 메모 — 새것부터 다섯 개씩 */
    private fun memoHwamyeon(spotId: String) {
        val k = kos ?: return kosEopseum()
        val s = k.spot(spotId) ?: return kosEopseum()
        val m = memosOf(s)
        val t0 = geul(k.spotFull(s) + " — 담긴 메모 ${m.size}개", jemok = true)
        allimJul()
        if (m.isEmpty()) geul("아직 담긴 메모가 없습니다.")
        jangMok("mm", m.reversed()) { one ->
            val f = one.geul("f"); val sec = (one.su("sec") ?: 0.0).toInt(); val tx = one.geul("text")
            val at = one.geul("at").replace("T", " ").drop(5).take(11)
            val v = geul(MemoKan.ireum(one.geul("where")) + ", " + at + (if (sec > 0) ", ${sec}초 녹음" else ", 글로 담음") + "\n" + tx.ifEmpty { "(받아쓰지 못함 — 소리로만 남음)" })
            if (f.isNotEmpty()) danchu(if (deunneun == f) "듣기 멈춤" else "이 메모 듣기") { deutgi(k.id, f) }
            else if (sec > 0) geul("소리는 아직 나스에 올리지 못해 폰에 남아 있습니다.", jakge = true)
            v
        }
        t0.tag = "meori"
    }
    /** 연습 — 읽으며 녹음 (현장영상해설 탭) */
    private fun yeonseupSection(k: Kos, s: Spot) {
        geul("연습 — 읽으며 녹음", jemok = true)
        val real = (s.n("real") ?: 0.0).toInt()
        geul("어림 ${k.readSec(k.spotText(s))}초" + if (real > 0) " / 실제 ${real}초" else " / 아직 재지 않았습니다")
        val m = Yeonseup.mal(k, s)
        if (m.isNotEmpty()) geul("되돌아보기: $m")
        s.d.optJSONObject("prac")?.geul("heard")?.let { h -> if (h.isNotEmpty()) geul("받아쓴 말 (참고): $h", jakge = true) }
        if (!k.isGyobon) {
            if (Nokeumgi.jung && Nokeumgi.galle == "prac") danchu("다 읽었습니다 (녹음 끝)", keun = true) { nokeumKkeut() }
            else danchu("읽으며 녹음 시작") {
                if (Nokeumgi.jung) malhagi("이미 녹음 중입니다. 먼저 끝내 주십시오.")
                else nokeumSijak("prac", s, "", "녹음을 시작했습니다. 대본을 실제처럼 읽으신 뒤 다 읽었습니다를 누르십시오.")
            }
        }
        val pf = s.s("pracFile")
        if (pf.isNotEmpty()) danchu(if (deunneun == pf) "듣기 멈춤" else "연습 녹음 듣기") { deutgi(k.id, pf) }
        else if (s.s("pracQref").isNotEmpty()) geul("연습 녹음은 아직 나스에 올리지 못해 폰에 남아 있습니다.", jakge = true)
    }
    /** 나스의 소리 듣기 — 같은 것을 다시 누르면 멈춤 */
    private fun deutgi(kosId: String, f: String) {
        if (deunneun == f) { deutgiMeomchum(); malhagi("듣기를 멈췄습니다."); draw(); return }
        deutgiMeomchum(); tts?.stop()
        deunneun = f; draw()
        pool.execute {
            val u = Api.fileUrl("audio", kosId, f, Api.needTicket())
            main.post {
                if (deunneun != f) return@post
                try {
                    val p = MediaPlayer()
                    p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    p.setDataSource(u)
                    p.setOnPreparedListener { it.start() }
                    p.setOnCompletionListener { deutgiMeomchum(); malhagi("다 들었습니다."); draw() }
                    p.setOnErrorListener { _, _, _ -> deutgiMeomchum(); malhagi("소리를 틀지 못했습니다."); draw(); true }
                    player = p; p.prepareAsync()
                } catch (e: Exception) { deutgiMeomchum(); malhagi("소리를 틀지 못했습니다."); draw() }
            }
        }
    }
    private fun deutgiMeomchum() { try { player?.stop() } catch (e: Exception) { }; try { player?.release() } catch (e: Exception) { }; player = null; deunneun = "" }

    // ---- 코스 관리 (봐 달라고 알리기·판 되돌리기·코스 지우기) ----
    private fun kosGwanri(k: Kos) {
        if (k.isGyobon) return
        geul("관리자에게 봐 달라고 알리기", jemok = true)
        val msg = EditText(this).apply { hint = "함께 전할 말 (없어도 됩니다)"; background = kadeuBg(); setPadding(dp(14), dp(12), dp(14), dp(12)) }
        body.addView(msg, lp(4))
        danchu("관리자에게 봐 달라고 알리기") {
            save(true)
            val m = msg.text.toString().trim()
            pool.execute {
                val j = Api.call("ask_review", JSONObject().put("c", k.id).put("msg", m))
                main.post { if (Api.ok(j)) { msg.setText(""); malhagi("관리자에게 봐 달라고 알렸습니다.") } else malhagi(Api.err(j)); draw() }
            }
        }
        geul("이 코스", jemok = true)
        danchu("판 되돌리기 (예전에 저장된 판으로)") { panMok = null; jang.remove("pd"); go(Hw.PanDoerigi) }
        danchu("이 코스 지우기 (휴지통으로)") {
            mutgi(k.name + " 코스를 지울까요? 휴지통으로 가므로 필요하면 되살릴 수 있습니다.", "지우기") {
                val id = k.id; val nm = k.name
                pool.execute {
                    val j = Api.call("delete_course", JSONObject().put("id", id))
                    main.post {
                        if (!Api.ok(j)) { malhagi(Api.err(j)); return@post }
                        if (kos?.id == id) { main.removeCallbacks(saveRun); kos = null; draftDrop(id) }
                        loadCourses { malhagi("$nm 코스를 지웠습니다. 휴지통으로 갔습니다.") }
                        draw()
                    }
                }
            }
        }
    }
    private fun panDoerigi() {
        val k = kos ?: return kosEopseum()
        val t0 = geul("판 되돌리기", jemok = true)
        allimJul()
        geul("저장할 때마다 앞의 것이 판으로 남습니다. 되돌려도 지금 것이 다시 판으로 남으므로 잃지 않습니다.", jakge = true)
        danchu("다시 훑기") { panMok = null; draw() }
        val L = panMok
        if (L == null) {
            geul("훑는 중입니다.")
            pool.execute {
                val j = Api.call("list_versions", JSONObject().put("c", k.id))
                main.post {
                    if (Api.ok(j)) { val a = j.optJSONArray("list") ?: JSONArray(); panMok = (0 until a.length()).mapNotNull { a.optJSONObject(it) } }
                    else { panMok = listOf(); malhagi(Api.err(j)) }
                    if (cur() is Hw.PanDoerigi) draw()
                }
            }
        } else {
            geul(if (L.isEmpty()) "아직 남은 판이 없습니다." else "남은 판 ${L.size}개 — 새것부터", jemok = true)
            jangMok("pd", L) { v ->
                danchu(v.geul("at").replace("T", " ").drop(5).take(11) + ", 지점 ${(v.su("spots") ?: 0.0).toInt()}곳, 대본 ${(v.su("texts") ?: 0.0).toInt()}곳, ${(v.su("chars") ?: 0.0).toInt()}자 — 이 판으로 되돌리기") {
                    mutgi("이 판으로 되돌립니다. 지금 것도 판으로 남습니다. 되돌릴까요?", "되돌리기") {
                        val ver = v.geul("v")
                        main.removeCallbacks(saveRun)
                        pool.execute {
                            val j = Api.call("restore_version", JSONObject().put("c", k.id).put("v", ver))
                            main.post {
                                val c = j.optJSONObject("course")
                                if (!Api.ok(j) || c == null) { malhagi(Api.err(j)); return@post }
                                base = j.geul("updated"); setKos(Kos(c)); lastSaved = kos!!.json().toString(); draftDrop(k.id)
                                malhagi("되돌렸습니다. 지점 ${kos?.spots?.size ?: 0}곳입니다.")
                                onBackPressed()
                            }
                        }
                    }
                }
            }
        }
        t0.tag = "meori"
    }
    private fun hyujitong() {
        val t0 = geul("휴지통", jemok = true)
        allimJul()
        val L = hyujiMok
        if (L == null) {
            geul("불러오는 중입니다.")
            pool.execute {
                val j = Api.call("list_trash")
                main.post {
                    if (Api.ok(j)) { val a = j.optJSONArray("list") ?: JSONArray(); hyujiMok = (0 until a.length()).mapNotNull { a.optJSONObject(it) } }
                    else { hyujiMok = listOf(); malhagi(Api.err(j)) }
                    if (cur() is Hw.Hyujitong) draw()
                }
            }
        } else {
            geul(if (L.isEmpty()) "휴지통이 비어 있습니다." else "휴지통에 지운 코스 ${L.size}개", jemok = true)
            jangMok("hj", L) { v ->
                val nm = v.geul("name"); val pl = v.geul("place")
                danchu("되살리기 — " + nm + (if (pl.isEmpty()) "" else ", $pl") + ", 지점 ${(v.su("spots") ?: 0.0).toInt()}곳, 지운 때 " + v.geul("at")) {
                    mutgi("$nm 코스를 되살릴까요? 코스 목록에 다시 들어갑니다.", "되살리기") {
                        pool.execute {
                            val j = Api.call("restore_trash", JSONObject().put("f", v.geul("f")))
                            main.post {
                                if (!Api.ok(j)) { malhagi(Api.err(j)); return@post }
                                hyujiMok = null
                                loadCourses()
                                malhagi("$nm 코스를 되살렸습니다." + (if ((j.su("renum") ?: 0.0) > 0) " 같은 번호의 코스가 그새 생겨 새 번호로 되살렸습니다." else "") + " 코스 목록에 들어갔습니다.")
                                draw()
                            }
                        }
                    }
                }
            }
        }
        t0.tag = "meori"
    }

    /** 못 올린 것 올리기 — 지금 연 코스의 것부터. 다른 코스의 것은 그 코스를 열 때 올림 */
    private fun daegiOllim(quiet: Boolean) {
        val L = Daegi.mokrok(this)
        if (L.isEmpty()) { if (!quiet) { malhagi("못 올린 것이 없습니다."); draw() }; return }
        val k = kos ?: run { if (!quiet) { malhagi("못 올린 것 ${L.size}개가 있습니다. 그 코스를 열면 올립니다."); draw() }; return }
        if (!quiet) malhagi("못 올린 것을 올리고 있습니다.")
        pool.execute {
            val dun = mutableListOf<Pair<String, String>>(); var mot = 0
            for (g in L) {
                if (g.geul("c") != k.id) continue
                val ref = g.geul("ref")
                val data = Daegi.data(this, ref) ?: run { Daegi.ppaegi(this, ref); null } ?: continue
                val b64 = Base64.encodeToString(data, Base64.NO_WRAP)
                val j = if (g.geul("jong") == "photo") Api.call("save_photo", JSONObject().put("c", g.geul("c")).put("s", g.geul("s")).put("ext", g.geul("ext")).put("data", b64), timeoutMs = 90000)
                        else Api.call("save_audio", JSONObject().put("c", g.geul("c")).put("s", g.geul("s")).put("kind", g.geul("kind")).put("ext", g.geul("ext")).put("data", b64), timeoutMs = 120000)
                if (!Api.ok(j)) { mot++; continue }
                dun.add(ref to j.geul("file"))
            }
            main.post {
                val kk = kos
                if (kk != null && kk.id == k.id) for ((ref, f) in dun) {
                    for (s in kk.allSpots) {
                        val a = s.shots
                        for (i in 0 until a.length()) a.optJSONObject(i)?.let { o -> if (o.geul("qref") == ref) { o.put("f", f); o.remove("qref") } }
                        s.d.optJSONArray("memos")?.let { m -> for (i in 0 until m.length()) m.optJSONObject(i)?.let { o -> if (o.geul("qref") == ref) o.put("f", f).put("qref", "") } }
                        if (s.s("pracQref") == ref) { s.d.put("pracFile", f); s.d.remove("pracQref") }
                    }
                    Daegi.ppaegi(this, ref)
                }
                val ollin = dun.size
                if (ollin > 0) bakkwim()
                val namun = Daegi.su(this)
                if (!quiet || ollin > 0) {
                    var m = if (ollin > 0) "못 올린 것 ${ollin}개를 올렸습니다." else ""
                    if (mot > 0) m += " ${mot}개는 아직 못 올렸습니다. 인터넷을 확인해 주십시오."
                    if (namun - mot > 0) m += " 다른 코스의 것 ${namun - mot}개는 그 코스를 열면 올립니다."
                    if (m.isEmpty()) m = "이 코스에는 못 올린 것이 없습니다."
                    malhagi(m.trim())
                }
                draw()
            }
        }
    }

    // ── 저장 ──
    private fun bakkwim() { main.removeCallbacks(saveRun); main.postDelayed(saveRun, 2000) }
    private fun save(quiet: Boolean) {
        val k = kos ?: return
        if (k.isGyobon) { if (!quiet) malhagi("교본은 읽기만 됩니다. 코스 탭에서 내 코스로 복사한 뒤 쓰십시오."); return }
        val snap = k.json().toString()
        draftKeep(k.id, snap)
        if (quiet && snap == lastSaved) return
        if (saving) { saveAgain = true; return }
        saving = true
        val b0 = base
        pool.execute {
            var j = Api.call("save_course", JSONObject().put("course", JSONObject(snap)).put("base", b0))
            var b = b0
            if (j.optBoolean("conflict", false)) {
                b = j.geul("updated").ifEmpty { b0 }
                j = Api.call("save_course", JSONObject().put("course", JSONObject(snap)).put("base", b).put("force", true))
            }
            main.post {
                saving = false
                if (Api.ok(j)) {
                    base = j.geul("updated"); lastSaved = snap; draftDrop(k.id)
                    saveMark = "저장함 " + base.replace("T", " ").drop(11).take(5)
                    if (!quiet) malhagi("저장했습니다.")
                } else {
                    saveMark = "저장 못 함 — 폰에 남겨 두었습니다"
                    malhagi("저장하지 못했습니다. 고친 것은 폰에 남겨 두었습니다. " + Api.err(j))
                }
                if (saveAgain) { saveAgain = false; save(true) }
            }
        }
    }
    private fun draftFile(id: String) = File(File(filesDir, "draft").apply { mkdirs() }, id.replace("/", "_") + ".json")
    private fun draftKeep(id: String, snap: String) { try { draftFile(id).writeText(JSONObject(snap).put("savedAt", nowIso()).toString()) } catch (e: Exception) { } }
    private fun draftDrop(id: String) { draftFile(id).delete() }
    private fun draft(id: String): JSONObject? = try { val f = draftFile(id); if (f.exists()) JSONObject(f.readText()) else null } catch (e: Exception) { null }
    private fun draftOllim() {
        val k = kos ?: return
        val dr = draft(k.id) ?: run { malhagi("폰에 남은 것이 없습니다."); return }
        dr.remove("savedAt"); setKos(Kos(dr)); lastSaved = ""
        save(false); draw()
    }
}

/** 도움말 — 아이폰 Tour/SeoljeongViews.swift 의 DoumMok 과 같은 글 */
val DOUM: List<Pair<String, String>> = listOf(
    "지점 경계 규칙" to "$RULE_TXT 지점 사이를 걷는 이동 시간은 다음 지점의 몫입니다. 작성원칙 통합본 7번입니다.",
    "탭 다섯" to "아래 탭은 코스, 답사, 지점, 현장영상해설, 설정 다섯입니다. 지금 탭을 한 번 더 누르면 그 탭 첫 화면 맨 위로 갑니다. 뒤로 단추는 화면 맨 위에 있고, 폰의 뒤로 동작으로도 뒤로 갑니다.",
    "코스 열기와 새 코스" to "코스 탭의 코스 목록에서 코스 이름을 두드리면 열립니다. 목록은 다섯 줄씩 보이고 더 보기와 이전 보기로 넘깁니다. 새 코스는 맨 아래 새 코스 만들기에서 이름과 장소를 적고 만듭니다. 새 코스에는 오프닝 지점과 클로징 지점이 들어 있습니다.",
    "교본" to "교본은 읽기만 됩니다. 코스 탭의 교본을 내 코스로 복사를 누르면 내 코스가 되어 고칠 수 있습니다.",
    "답사 — 여기서부터 다음 지점 시작" to "현장에서 앞 지점의 현장영상해설을 마치고 출발하는 자리에 서면 답사 탭의 여기서부터 다음 지점 시작을 누르십시오. 앞 지점의 끝과 다음 지점의 시작이 이 자리와 이 시각으로 적힙니다. 앞 지점의 어디까지 칸에 그 자리를 말로 적어 두십시오.",
    "답사 — 여기서부터 새 지점 시작" to "아직 없는 지점을 현장에서 만들 때 씁니다. 지금 지점 바로 뒤에 새 지점이 들어가고, 이 자리가 앞 지점의 끝이자 새 지점의 시작이 됩니다.",
    "답사 — 사진 찍기" to "정지 현장영상해설 자리에서 참여자가 바라볼 쪽을 12시로 두고, 12시, 3시, 6시, 9시 방향 차례로 찍습니다. 한 장을 올리면 다음 방향을 알려 드립니다. 첫 사진 때 지점 자리가 적히고, 앞 지점에서 걸어온 거리로 이동 시간을 잡습니다.",
    "지점 한 화면" to "지점 탭에서 지점을 두드리면 그 지점의 어디서부터, 어디까지, 시간, 이동 구간 안내, 만져 볼 수 있는 것, 조심할 것, 현장영상해설사 위치, 대본을 한 화면에서 고칩니다. 맨 아래에 앞 지점, 다음 지점, 한 칸 앞으로, 한 칸 뒤로, 지금 저장이 있습니다.",
    "오프닝 지점과 클로징 지점의 대본" to "오프닝 지점의 대본은 웹 4단계의 오프닝 칸과 같은 글이고, 클로징 지점의 대본은 클로징 칸과 같은 글입니다. 정지 현장영상해설 시간을 0으로 두면 대본 읽는 시간으로 셉니다.",
    "방향 도우미" to "지점 한 화면의 방향 도우미에서 12시 방향부터 11시 방향까지 골라 대본 끝에 붙입니다. 방향은 늘 시계 방향으로 말합니다.",
    "저장" to "고친 뒤 2초가 지나면 저절로 저장합니다. 인터넷이 끊겨 저장하지 못하면 폰에 남겨 두고, 설정의 폰에 남은 것 올리기로 올립니다. 다른 기기에서 먼저 저장된 경우에는 지금 고친 것으로 맞추고, 앞 것은 웹의 판 되돌리기에 남습니다.",
    "원칙 16개 점검" to "코스 탭의 원칙 16개 점검에서 어디까지를 안 적은 지점, 현장영상해설사 위치가 없는 지점, 시계 방향으로 짚지 않은 지점, 시각 전제 표현 같은 고칠 곳을 다섯 줄씩 듣습니다. 번호는 작성원칙 통합본 16개의 번호입니다.",
    "현장영상해설 탭" to "현장에서 지금 지점의 대본을 크게 보여 줍니다. 다음 지점 단추로 차례대로 넘기고, 연습할 때는 폰 목소리로 듣기를 씁니다.",
    "협회 승인 요청" to "코스 탭의 협회 승인 요청 보내기를 누르면 지금 코스를 저장하고 협회에 승인을 요청합니다. 승인되면 코스 목록에 표시됩니다.",
    "새로고침과 업데이트" to "설정 맨 위의 새로고침은 코스 목록과 지금 코스를 서버에서 다시 받습니다. 그 아래 업데이트는 새 판이 있는지 살피고, 있으면 받아서 설치 화면을 엽니다.",
    "말소리" to "설정의 앱 안내 말소리를 끄면 앱이 스스로 말하는 안내가 꺼지고, 안내는 화면에 글로 남습니다.",
    "웹 투어스튜디오와 함께 쓰기" to "앱과 웹은 같은 서버의 같은 코스를 씁니다. 대본집 내려받기, 워드 내려받기, 관리자 일은 아직 웹 투어스튜디오에서 하십시오. 설정에 웹 투어스튜디오 열기가 있습니다.",
    "사진 크게 보기" to "답사 탭의 방금 찍은 사진 크게 보기, 또는 답사 탭과 지점 한 화면의 담긴 사진 목록에서 사진을 두드리면 크게 봅니다. 함께 가는 분이 사진을 보실 수 있습니다. 톡백은 지점과 몇째 사진인지, 방향을 읽어 줍니다.",
    "사진 지우기" to "사진 크게 보기 화면의 이 사진 지우기, 또는 답사 탭의 방금 찍은 사진 아래 이 사진 지우기를 누릅니다. 사진을 지웁니다, 되돌릴 수 없습니다, 지울까요 하고 한 번 묻습니다. 지우기를 누르면 지워집니다. 교본의 사진은 지울 수 없습니다.",
    "같은 방향 다시 찍기" to "사진 크게 보기 화면이나 답사 탭의 방금 찍은 사진 아래 이 방향 다시 찍기를 누르면 그 사진을 지우고 같은 방향으로 카메라가 열립니다. 같은 방향을 바라보고 다시 찍으십시오.",
    "이 지점 지우기" to "지점 탭에서 지점을 두드려 지점 한 화면을 열고, 맨 아래의 이 지점 지우기를 누릅니다. 지점 이름을 지울까요, 담은 사진 기록도 함께 지워집니다 하고 묻습니다. 지우기를 누르면 지워지고 저절로 저장됩니다. 오프닝 지점과 클로징 지점은 지울 수 없습니다. 차례는 같은 화면의 한 칸 앞으로, 한 칸 뒤로로 바꿉니다.",
    "현장 메모 말로 적기" to "답사 탭의 현장 메모에서 어디에 담을지(이동 구간 안내, 만져 볼 것, 조심할 것, 현장영상해설 밑글)를 고르고 말로 적기 시작을 누릅니다. 말씀하신 뒤 다 말했습니다를 누르면 소리를 mp3로 바꿔 올리고, 받아쓴 말을 고른 칸에 붙입니다. 받아쓰기는 안드로이드 13 이상에서 되며, 안 되면 소리로만 남깁니다. 처음 한 번 마이크를 허락해 주십시오.",
    "현장 메모 글로 적기" to "답사 탭의 현장 메모에서 어디에 담을지를 고르고, 글로 적기 칸에 적은 뒤 글로 담기를 누릅니다. 고른 칸에 붙고 메모 기록에도 남습니다.",
    "담긴 메모 보기와 듣기" to "답사 탭이나 지점 한 화면의 담긴 메모 보기와 듣기를 누르면 새것부터 다섯 개씩 나옵니다. 말로 담은 메모는 이 메모 듣기로 들을 수 있습니다.",
    "읽으며 녹음 (연습)" to "현장영상해설 탭의 연습에서 읽으며 녹음 시작을 누르고 대본을 실제처럼 읽은 뒤 다 읽었습니다를 누릅니다. 걸린 시간, 머무는 시간보다 긴지, 1분에 몇 자 빠르기인지, 말씀 가운데 시각 전제 표현이 있었는지를 되돌아보기로 알려 드립니다. 연습 녹음 듣기로 다시 들을 수 있습니다. 녹음은 mp3로 올라갑니다.",
    "못 올린 사진과 소리" to "인터넷이 끊겨 사진이나 소리를 올리지 못하면 폰에 남겨 두고, 코스에는 아직 못 올림으로 적어 둡니다. 설정의 못 올린 것 지금 올리기를 누르거나 그 코스를 다시 열면 저절로 올립니다.",
    "판 되돌리기" to "코스 탭의 판 되돌리기에서 예전에 저장된 판이 새것부터 나옵니다. 고르면 이 판으로 되돌립니다, 지금 것도 판으로 남습니다 하고 묻습니다. 되돌려도 지금 것이 판으로 남으므로 잃지 않습니다.",
    "코스 지우기와 휴지통" to "코스 탭의 이 코스 지우기를 누르면 한 번 묻고 휴지통으로 보냅니다. 코스 탭 맨 아래 휴지통에서 지운 코스를 골라 되살리면 코스 목록에 다시 들어갑니다. 교본은 지울 수 없습니다.",
    "관리자에게 봐 달라고 알리기" to "코스 탭의 관리자에게 봐 달라고 알리기에 함께 전할 말을 적고(없어도 됩니다) 누르면, 지금 코스를 저장하고 관리자에게 봐 달라고 알립니다.",
    "분야·지역·참가 인원·현장영상해설사 수" to "코스 탭의 코스 정보에서 더 적을 것의 분야와 지역 단추를 눌러 고르고, 참가 인원과 현장영상해설사 수를 숫자로 적습니다. 웹 1단계의 더 적을 것과 같은 칸입니다."
)
