// AI점자도서관 안드로이드 — 화면 (0.5.0판, 빌드 261010-L9)
// 0.5.0 (261010-L9, 도서관 창 클, 이사장님 승인 「1」) 첫 화면에 고른 문(주제별·장르별·테마별, 설정의 첫 화면 목록, 처음 값 장르별)의 서가 목록을 바로,
//       형태별 갈래 목록과 「보일 책」 거르기를 뺌(늘 모든 책), 목록은 톡백 목록 넘기기(앞으로·뒤로) 동작으로 넘기고 넘기면 커서는 첫 줄, 맨 아래 「다음 목록」 하나만
// 0.4.4 (261008-L8, 도서관 창 클, 이사장님 승인 「1」 — 아이폰판과 하나하나 견주어 찾은 것을 네 묶음으로 고침)
//   묶음 1 화면을 꺼도 계속 읽기·이어폰 단추·잠금 화면(DokseoService)
//   묶음 2 내 서재가 목록 줄 수를 따름, 책갈피로 다른 책을 열 때 읽던 자리 지켜짐, 내려받은 책 모두 지우기는 한 번 더 물음,
//          들을 수 없는 형식은 알림으로, 회원 등록 화면에서는 탭 바를 감춤
//   묶음 3 설정·독서기 단추를 눌러도 커서가 그 단추에 머묾, 독서기는 단추 먼저·본문 나중, 빠르기·목소리·책갈피·처음부터는 「더 보기」 안에,
//          빠르기는 「더 느리게」·「더 빠르게」 두 단추
//   묶음 4 처음부터, 책갈피 지우기, 도움말 찾기(결과만 남기고 첫 줄에 커서)와 도움말 아이폰만큼, 독서기 안에서 목소리 바꾸기,
//          책 정보의 읽던 자리, 설정의 회원 이름, 다 읽은 책 넘기기, 내 서재에서 지운 책은 10초 뒤 내려받은 것도 지움
// 0.2.0 (261002-L1, 이사장님 승인 "2" — 아이폰 0.2.0 과 동시에) 처음 판
// 아이폰과 같은 차림: 아래 탭 셋(도서관·내 서재·설정·도움말)이 모든 속 화면에 늘 보이고, 뒤로는 위에만.
// 규칙: 한 줄에 이름 하나 단추 하나, 목록은 15줄씩(더 보기·이전 보기), 결과가 나오면 커서를 첫 줄에,
//       겉에는 급한 것만 두고 나머지는 「더 보기」에, 뒤로 가기로 앱 밖에 나가지 않음, 새로고침은 설정에 하나
// 내 서재: 읽기 시작한 책은 저절로, 15권씩, 지우기는 톡백 「동작」 메뉴(줄마다 단추 없음)·길게 누르기, 10초 되돌리기
package kr.or.ada.app.lib

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.media3.ui.PlayerView
import java.util.concurrent.Executors

sealed class Hm {
    data class Mun(val mun: String, val t: String) : Hm(); data class Seoga(val mun: String, val k: String, val t: String) : Hm()   // 0.3.0 세 겹의 문
    object Home : Hm(); object Seojae : Hm(); object Seoljeong : Hm()
    data class Gal(val g: String) : Hm(); data class Jakbon(val j: String, val t: String) : Hm(); data class Find(val s: String) : Hm()
    data class Book(val i: Int) : Hm(); data class Reader(val i: Int, val t: String, val k: String) : Hm(); data class Marks(val i: Int) : Hm()
}

class LibActivity : Activity() {
    companion object { const val PAN = "0.5.1"; const val BILDEU = "261010-L10" }   // 0.5.1 첫 화면 맨 위 이름 줄·바로 아래 찾기, 목록에서 한 번 두드리면 바로 읽기, 넘긴 뒤 커서 첫 줄 다지기(도서관 창 클, 이사장님 승인 「1」). 0.4.4 아이폰과 견주어 네 묶음 고침(도서관 창 클, 이사장님 승인 「1」). 0.4.3 새 판 알림과 업데이트(대장클, 이사장님 지시)
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(3)
    private val stacks = arrayOf(mutableListOf<Hm>(Hm.Home), mutableListOf<Hm>(Hm.Seojae), mutableListOf<Hm>(Hm.Seoljeong))
    private var tab = 0
    private lateinit var scroll: ScrollView
    private lateinit var body: LinearLayout
    private lateinit var tabbar: LinearLayout
    private var playerView: PlayerView? = null
    private var listO = 0
    private var undo: Pair<ReadRec, List<Mark>>? = null
    private val undoEnd = Runnable { undo?.let { Naeryeo.remove(this, it.first.i) /* 0.4.4 되돌리지 않으면 내려받은 것도 지워 공간을 돌려드림 */ }; undo = null; if (cur() is Hm.Seojae) draw() }
    // 0.4.4 화면을 다시 그려도 커서가 누른 단추에 머물게(설정·독서기)
    private var jikiNun: String? = null; private var jikiY = 0
    private fun dasi(nun: String) { jikiNun = nun; jikiY = scroll.scrollY; draw() }
    private fun chatgiView(g: ViewGroup, k: String): View? {
        for (n in 0 until g.childCount) {
            val v = g.getChildAt(n)
            if (v.visibility != View.VISIBLE) continue
            val t = (v as? TextView)?.text?.toString() ?: ""
            if (t.contains(k) || (v.contentDescription?.toString() ?: "").contains(k)) return v
            if (v is ViewGroup) chatgiView(v, k)?.let { return it }
        }
        return null
    }
    private var seojaeDeo = false; private var daO = 0; private var readerDeo = false
    private var moduJiugiMureum = false; private var doumQ: String? = null; private var doumPyeolchim = false

    override fun onCreate(s: Bundle?) {
        Api.PER = Naeryeo.jul(this)   // 0.3.0 — 목록 줄 수
        super.onCreate(s)
        Api.ctx = applicationContext; Store.load(this); Dokseo.ctx = applicationContext
        Dokseo.bakkwim = { main.post { if (cur() is Hm.Reader) drawReaderState() } }
        Dokseo.allim = { m -> main.post { malhagi(m) } }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Saek.bada(this@LibActivity)) }
        scroll = ScrollView(this).apply { isFillViewport = true }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(24)) }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        tabbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Saek.namsaek) }
        root.addView(tabbar, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
        draw()
        // 0.4.3 새 판 알림과 업데이트(이사장님 지시 — 모든 앱에) — 켤 때 살피고, 알림을 두드려 열렸으면 곧바로 업데이트
        Ollim.malhagi = { m -> main.post { scroll.announceForAccessibility(m) } }
        Ollim.byeonhwa = { main.post { if (cur() is Hm.Home || cur() is Hm.Seoljeong) draw() } }
        Ollim.sijak(this)
        Ollim.intentBoda(this, intent)
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 44)   // 새 판 알림을 띄우려고 한 번 여쭘
    }
    override fun onResume() { super.onResume(); Ollim.dorawatda(this) }   // 0.4.3 돌아오면 새 판 살피기(1시간에 한 번까지)
    override fun onNewIntent(i: android.content.Intent) { super.onNewIntent(i); setIntent(i); Ollim.intentBoda(this, i) }   // 0.4.3 새 판 알림을 두드렸을 때
    override fun onDestroy() { Dokseo.bakkwim = null; Ollim.byeonhwa = null; Ollim.malhagi = null; super.onDestroy() }

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
    private var jeojeolloI = -1   // 0.5.1 독서기를 열 때 한 번만 저절로 읽기
    private fun go(h: Hm) { if (h is Hm.Reader) jeojeolloI = h.i; stacks[tab].add(h); listO = 0; draw() }
    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
    private fun malhagi(s: String) { if (Store.speechOn) scroll.announceForAccessibility(s) }
    private fun bg() { pool.execute { } }

    // ── 그리기 부품 ──
    private fun kadeuBg(): GradientDrawable = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Saek.kadeu(this@LibActivity)) }
    private fun danchu(ireum: String, keun: Boolean = false, onClick: () -> Unit): Button {
        val b = Button(this).apply {
            text = ireum; isAllCaps = false; gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (keun) 18f else 16f)
            setTextColor(if (keun) Color.WHITE else Saek.geulja(this@LibActivity))
            background = if (keun) GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Saek.namsaek) } else kadeuBg()
            setPadding(dp(16), dp(12), dp(16), dp(12)); minHeight = dp(52)
            setOnClickListener { onClick() }
        }
        body.addView(b, lp(6)); return b
    }
    private fun geul(t: String, jemok: Boolean = false, jakge: Boolean = false): TextView {
        val v = TextView(this).apply {
            text = t; setTextColor(if (jemok) Saek.ganjo(this@LibActivity) else if (jakge) Saek.buGeulja(this@LibActivity) else Saek.geulja(this@LibActivity))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (jemok) 20f else if (jakge) 14f else 16f)
            if (jemok) { setTypeface(typeface, Typeface.BOLD); ViewCompat.setAccessibilityHeading(this, true) }
        }
        body.addView(v, lp(8)); return v
    }
    private fun lp(top: Int) = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }
    private fun cheotJul(v: View?) {
        val k = jikiNun
        if (k != null) {   // 0.4.4 누른 단추에 커서를 되돌림
            jikiNun = null; val y = jikiY
            val t = chatgiView(body, k) ?: v
            scroll.post { scroll.scrollTo(0, y) }
            t?.postDelayed({ t.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null) }, 350)
            return
        }
        v ?: return; scroll.scrollTo(0, 0); v.postDelayed({ v.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null) }, 350) }

    private fun tabs() {
        tabbar.removeAllViews()
        listOf("도서관", "내 서재", "설정·도움말").forEachIndexed { k, n ->
            val b = Button(this).apply {
                text = n; isAllCaps = false; setTextColor(if (k == tab) Saek.geum else Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); setBackgroundColor(Color.TRANSPARENT)
                contentDescription = if (k == tab) "$n, 선택됨" else n
                setOnClickListener { if (tab == k) { while (stacks[k].size > 1) stacks[k].removeAt(stacks[k].size - 1) }; tab = k; draw() }
            }
            tabbar.addView(b, LinearLayout.LayoutParams(0, dp(56), 1f))
        }
    }

    // ── 화면 ──
    private fun draw() {
        if (cur() !is Hm.Reader && Dokseo.playing) Dokseo.pause()   // 0.3.1 — 독서기 화면을 떠나면 멈춤
        playerView?.player = null; playerView = null
        ViewCompat.setAccessibilityDelegate(body, null)   // 0.5.0 목록 넘기기 대리자는 목록 화면에서만
        body.removeAllViews(); tabs()
        if (!Hoewon.deungrokdoem(this)) { tabbar.visibility = View.GONE; deungrok(); return }   // 0.4.4 등록 전에는 탭 바를 감춤(아이폰과 같게)
        tabbar.visibility = View.VISIBLE
        if (stacks[tab].size > 1) danchu("뒤로") { onBackPressed() }
        when (val h = cur()) {
            Hm.Home -> home()
            Hm.Seojae -> seojae()
            Hm.Seoljeong -> seoljeong()
            is Hm.Gal -> paged("${h.g}") { o -> Api.list(h.g, o) }
            is Hm.Jakbon -> paged(h.t) { o -> Api.jakbon(h.j, o) }
            is Hm.Find -> paged("「${h.s}」 찾은 책") { o -> Api.find(h.s, o) }
            is Hm.Book -> book(h.i)
            is Hm.Reader -> reader(h)
            is Hm.Marks -> marks(h.i)
            is Hm.Mun -> munHwamyeon(h)
            is Hm.Seoga -> paged(h.t) { o -> Api.seoga(h.mun, h.k, "all", o) }   // 0.5.0 보일 책 거르기를 뺌
        }
    }

    private fun deungrok() {
        geul("AI점자도서관", jemok = true)
        geul("처음 쓰실 때 한 번만 회원 등록을 합니다. 이름과 휴대전화 번호를 적고 등록을 눌러 주십시오.")
        val ir = EditText(this).apply { hint = "이름"; inputType = InputType.TYPE_TEXT_VARIATION_PERSON_NAME }
        val jh = EditText(this).apply { hint = "휴대전화 번호"; inputType = InputType.TYPE_CLASS_PHONE }
        body.addView(ir, lp(6)); body.addView(jh, lp(6))
        val mal = TextView(this)
        danchu("등록", keun = true) {
            mal.text = "등록하는 중입니다."
            pool.execute {
                val e = Hoewon.deungrok(this, ir.text.toString(), jh.text.toString())
                main.post { if (e == null) { malhagi("등록되었습니다. AI점자도서관에 오신 것을 환영합니다."); draw() } else { mal.text = e; cheotJul(mal) } }
            }
        }
        body.addView(mal, lp(8)); mal.setTextColor(Saek.geulja(this))
    }

    private fun meori() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Saek.namsaek) }
            contentDescription = "AI점자도서관, 사단법인 한국시각장애인현장영상해설협회"   // 0.5.1 「주관」 뺌
            ViewCompat.setAccessibilityHeading(this, true)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        val logo = LogoView(this).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
        box.addView(logo, LinearLayout.LayoutParams(dp(92), ViewGroup.LayoutParams.WRAP_CONTENT))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
        col.addView(TextView(this).apply { text = "AI점자도서관"; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f); setTypeface(typeface, Typeface.BOLD) })
        col.addView(TextView(this).apply { text = "사단법인 한국시각장애인현장영상해설협회"; setTextColor(Saek.geum); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f) })
        box.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        body.addView(box, lp(0))
    }

    private fun chaekJul(rc: ReadRec, mal: String, bu: String, onClick: () -> Unit, jiugiDoem: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10)); background = kadeuBg()
            isClickable = true; isFocusable = true; contentDescription = mal
            setOnClickListener { onClick() }
        }
        row.addView(CoverView(this, rc.t), LinearLayout.LayoutParams(dp(42), dp(56)))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
        col.addView(TextView(this).apply { text = rc.t; setTextColor(Saek.geulja(this@LibActivity)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); setTypeface(typeface, Typeface.BOLD); maxLines = 2 })
        col.addView(TextView(this).apply { text = bu; setTextColor(Saek.buGeulja(this@LibActivity)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f) })
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (jiugiDoem) {
            ViewCompat.addAccessibilityAction(row, "내 서재에서 지우기") { _, _ -> jiugi(rc); true }
            row.setOnLongClickListener { jiugi(rc); true }
        }
        body.addView(row, lp(6)); return row
    }

    private fun home() {
        meori()
        Ollim.julMal(this)?.let { m -> danchu(m, keun = true) { Ollim.olligi(this) } }   // 0.4.3 새 판이 있을 때만 — 안내와 단추를 한 자리에
        // 0.5.1 이름 줄 바로 아래 찾기 칸(이사장님 지시)
        val chaj = EditText(this).apply {
            hint = "찾을 책 이름"; inputType = InputType.TYPE_CLASS_TEXT; imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = kadeuBg(); setPadding(dp(14), dp(12), dp(14), dp(12)); setTextColor(Saek.geulja(this@LibActivity))
        }
        val chajgi = { val q = chaj.text.toString().trim(); if (q.isNotEmpty()) go(Hm.Find(q)) }
        chaj.setOnEditorActionListener { _, a, _ -> if (a == EditorInfo.IME_ACTION_SEARCH) { chajgi(); true } else false }
        body.addView(chaj, lp(10))
        danchu("찾기") { chajgi() }
        var cheot: View? = null
        Store.last?.let { l ->
            cheot = chaekJul(l, "이어 듣기, ${l.t}, ${l.wichiMal}부터", "이어 듣기 · ${l.wichiMal}부터", { go(Hm.Reader(l.i, l.t, l.kind)) }, false)
        }
        // 0.5.0 고른 문의 서가 목록을 바로(설정의 첫 화면 목록), 다른 두 문은 그 아래 한 줄씩
        val cm = cheotMun()
        geul(MUN.first { it.first == cm }.second, jemok = true)
        val munMal = geul("가져오는 중입니다.", jakge = true)
        val munJari = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(munJari, lp(0))
        for ((k, t) in MUN) if (k != cm) danchu(if (k == "jujae") "주제별로 찾기, 십진분류" else "${t}로 찾기") { go(Hm.Mun(k, t)) }
        pool.execute {
            val r = runCatching { Api.mun("all") }
            main.post {
                if (cur() != Hm.Home) return@post
                r.onFailure { munMal.text = "도서관에 닿지 못했습니다. 인터넷을 확인한 뒤 설정의 새로고침을 눌러 주십시오." }.onSuccess { all ->
                    val sg = all[cm].orEmpty().filter { it.third > 0 }
                    if (sg.isEmpty()) munMal.text = "이 문에는 아직 책이 없습니다." else body.removeView(munMal)
                    for ((k, tt, n) in sg) {
                        val b = Button(this).apply {
                            text = "$tt, ${n}권"; isAllCaps = false; gravity = Gravity.START or Gravity.CENTER_VERTICAL
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); setTextColor(Saek.geulja(this@LibActivity)); background = kadeuBg()
                            setPadding(dp(16), dp(12), dp(16), dp(12)); minHeight = dp(52)
                            setOnClickListener { go(Hm.Seoga(cm, k, tt)) }
                        }
                        munJari.addView(b, lp(6))
                    }
                }
            }
        }
        cheotJul(cheot)
    }

    private fun paged(jemok: String, loader: (Int) -> ListResp) {
        geul(jemok, jemok = true)
        val mal = geul("가져오는 중입니다.", jakge = true)
        val o = listO
        pool.execute {
            val r = runCatching { loader(o) }
            main.post {
                if (r.isFailure) { mal.text = "목록을 가져오지 못했습니다. 인터넷을 확인해 주십시오."; return@post }
                val lr = r.getOrThrow()
                if (lr.items.isEmpty()) { mal.text = "찾은 것이 없습니다."; return@post }
                mal.text = "모두 ${"%,d".format(lr.modu)}개 가운데 ${lr.o + 1}번부터 ${lr.o + lr.items.size}번"
                var first: View? = null
                for (it in lr.items) {
                    val b = danchu(it.t) { if (it.i != null) go(Hm.Reader(it.i, it.t, "")) else if (it.j != null) go(Hm.Jakbon(it.j, it.t)) }   // 0.5.1 한 번 두드리면 독서기가 열려 바로 읽음
                    if (first == null) { first = b; if (lr.o > 0) b.contentDescription = "${lr.o + 1}번부터, ${it.t}" }   // 0.5.0 넘긴 뒤 첫 줄에서 몇 번부터인지 들림
                }
                val dam = lr.o + Api.PER < lr.modu
                if (dam) danchu("다음 목록") { listO = lr.o + Api.PER; draw() }   // 0.5.0 손짓이 안 될 때를 위해 하나만 남김
                neomgigi(lr.o > 0, dam, { listO = lr.o + Api.PER; draw() }, { listO = (lr.o - Api.PER).coerceAtLeast(0); draw() })
                cheotJul(first)
                if (lr.o > 0) first?.postDelayed({ first?.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED); first?.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null) }, 800)   // 0.5.1 넘긴 뒤 커서를 첫 줄에 한 번 더
            }
        }
    }

    // 0.5.0 목록 넘기기 — 톡백의 목록 넘기기(앞으로·뒤로) 동작을 받아 다음·앞 목록을 그림. 목록 줄에 커서가 있을 때 가장 가까운 넘길 수 있는 자리가 이 목록이 되게 함
    private fun neomgigi(ap: Boolean, dwi: Boolean, daeum: () -> Unit, ijeon: () -> Unit) {
        ViewCompat.setAccessibilityDelegate(body, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: androidx.core.view.accessibility.AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.isScrollable = ap || dwi
                if (dwi) info.addAction(androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_FORWARD)
                if (ap) info.addAction(androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_BACKWARD)
            }
            override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) { if (dwi) daeum() else scroll.announceForAccessibility("마지막 목록입니다"); return true }
                if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) { if (ap) ijeon() else scroll.announceForAccessibility("첫 목록입니다"); return true }
                return super.performAccessibilityAction(host, action, args)
            }
        })
    }
    private fun cheotMun(): String = getSharedPreferences("naeryeo", MODE_PRIVATE).getString("cheotMun", "jangreu") ?: "jangreu"
    private val MUN = listOf("jujae" to "주제별", "jangreu" to "장르별", "tema" to "테마별")

    private fun book(i: Int) {
        val mal = geul("책 정보를 가져오는 중입니다.", jakge = true)
        pool.execute {
            val r = runCatching { Api.book(i) }
            main.post {
                r.onFailure { mal.text = "책 정보를 가져오지 못했습니다." }.onSuccess { b ->
                    body.removeView(mal)
                    val t = geul(b.t, jemok = true)
                    geul("갈래 ${b.g}" + (if (b.nal.isNotEmpty()) ", ${b.nal}" else ""), jakge = true)
                    Store.rec(i)?.let { r -> geul(if (r.done) "다 읽은 책입니다." else "읽던 자리: ${r.wichiMal}", jakge = true) }   // 0.4.4
                    if (b.sogae.isNotEmpty()) {   // 0.4.2 — 책 소개(카카오 책 정보), 지은이, 출판사
                        if (b.jieun.isNotEmpty()) geul("지은이 " + b.jieun, jakge = true)
                        if (b.chulpan.isNotEmpty()) geul("출판사 " + b.chulpan, jakge = true)
                        geul("책 소개. " + b.sogae)
                        danchu("책 소개 듣기") { sogaeDeutgi("책 소개. " + b.sogae) }
                    }
                    val ireum = when (b.kind) { "sori" -> "듣기"; "yeongsang" -> "보기"; else -> "읽기" }
                    if (b.kind in setOf("geul", "sori", "yeongsang")) danchu(if (Store.rec(i) != null) "이어서 $ireum" else ireum, keun = true) { go(Hm.Reader(i, b.t, b.kind)) }
                    else geul("이 형식은 아직 독서기로 들을 수 없습니다.")   // 0.4.4 아이폰과 같게
                    if (Store.marksOf(i).isNotEmpty()) danchu("책갈피 ${Store.marksOf(i).size}개") { go(Hm.Marks(i)) }
                    if (b.kind == "geul" || b.kind == "sori") {   // 0.3.0 — 내려받기(인터넷 없이 듣기)
                        if (Naeryeo.isDown(this, i)) danchu("폰에서 지우기(내려받은 것)") { Naeryeo.remove(this, i); malhagi("폰에서 지웠습니다."); draw() }
                        else if (Naeryeo.busy == i) danchu("내려받기 멈추기") { Naeryeo.cancel(); malhagi("내려받기를 멈췄습니다."); draw() }
                        else danchu("폰에 내려받기(인터넷 없이 듣기)") { Naeryeo.download(this, i, b.t, b.kind) { m -> main.post { malhagi(m); if (cur() == Hm.Book(i)) draw() } } }
                    }
                    cheotJul(t)
                }
            }
        }
    }

    // ── 독서기 ──
    private var readerJul: TextView? = null; private var readerMun: TextView? = null; private var readerDan: Button? = null
    private fun reader(h: Hm.Reader) {
        if (h.k.isEmpty()) {   // 0.5.1 목록에서 바로 왔을 때 — 책 형식을 알아내 연다
            geul(h.t, jemok = true)
            val mal = geul("책을 여는 중입니다.", jakge = true)
            pool.execute {
                val r = runCatching { Api.book(h.i) }
                main.post {
                    if (cur() != h) return@post
                    val b = r.getOrNull()
                    if (b != null && b.kind in setOf("geul", "sori", "yeongsang")) { stacks[tab][stacks[tab].size - 1] = Hm.Reader(h.i, b.t.ifEmpty { h.t }, b.kind); draw() }
                    else { mal.text = if (b == null) "책을 가져오지 못했습니다. 인터넷을 확인해 주십시오." else "이 형식은 아직 독서기로 들을 수 없습니다."; danchu("책 정보") { go(Hm.Book(h.i)) }; cheotJul(mal) }
                }
            }
            return
        }
        Dokseo.open(this, h.i, h.t, h.k)
        if (jeojeolloI == h.i) { jeojeolloI = -1; if (!Dokseo.playing) Dokseo.toggle() }   // 0.5.1 열리면 바로 읽음(이사장님 지시 — 한 번 두드리면), 커서는 멈춤 단추에
        geul(h.t, jemok = true)
        readerJul = geul("", jakge = true)
        if (h.k == "yeongsang") {
            val pv = PlayerView(this).apply { player = Dokseo.player; useController = true }
            playerView = pv
            body.addView(pv, LinearLayout.LayoutParams(-1, dp(220)).apply { topMargin = dp(8) })
        }
        // 0.4.4 단추 먼저, 본문 글은 그 아래(본문을 지나야 단추에 닿던 것 고침), 자주 안 쓰는 것은 「더 보기」 안에
        readerDan = danchu("읽기", keun = true) { Dokseo.toggle() }
        danchu("앞으로 30초") { Dokseo.gaCho(30.0) }   // 0.4.0 — 헷갈리지 않는 이름(이사장님)
        danchu("뒤로 30초") { Dokseo.gaCho(-30.0) }
        jaesaengMakdae()   // 0.4.0 — 재생 위치 막대
        readerMun = if (h.k == "geul") geul("") else null
        danchu(if (readerDeo) "더 보기 접기" else "더 보기, 빠르기·목소리·책갈피·처음부터·책 정보") { readerDeo = !readerDeo; dasi("더 보기") }
        if (readerDeo) {
            val rn = Store.rateNames[Store.rateIndex]
            danchu("더 느리게, 지금 $rn") { if (Store.rateIndex > 0) { Store.rateIndex -= 1; Dokseo.setRate() }; malhagi(Store.rateNames[Store.rateIndex]); dasi("더 느리게") }
            danchu("더 빠르게, 지금 $rn") { if (Store.rateIndex < Store.rates.size - 1) { Store.rateIndex += 1; Dokseo.setRate() }; malhagi(Store.rateNames[Store.rateIndex]); dasi("더 빠르게") }
            if (h.k == "geul") danchu("목소리 바꾸기, 지금 " + MOKSORI[Store.voice.coerceIn(0, 9)]) {
                Store.voice = (Store.voice + 1) % 10; malhagi(MOKSORI[Store.voice])
                if (Dokseo.playing) Dokseo.play(Dokseo.pos)   // 바꾼 목소리로 지금 문단부터
                dasi("목소리 바꾸기")
            }
            danchu("이 자리에 책갈피 꽂기") { Dokseo.markHere(); malhagi("책갈피를 꽂았습니다."); dasi("이 자리에 책갈피") }
            if (Store.marksOf(h.i).isNotEmpty()) danchu("책갈피 보기, ${Store.marksOf(h.i).size}개") { go(Hm.Marks(h.i)) }
            danchu("처음부터") { Dokseo.cheoeum(); malhagi("처음으로 갔습니다.") }
            danchu("책 정보, 책 소개·지은이·내려받기") { go(Hm.Book(h.i)) }   // 0.5.1 책 정보는 여기 안에
        }
        drawReaderState()
        cheotJul(readerDan)
    }
    private fun drawReaderState() {
        val d = Dokseo
        readerDan?.text = if (d.playing) (if (d.waiting) "목소리를 받는 중입니다, 멈춤" else "멈춤") else "읽기"
        readerJul?.text = if (d.kind == "geul") (if (d.modu > 0) "${d.modu}문단 가운데 ${d.pos + 1}번째" else "글자를 가져오는 중입니다.") else ""
        readerMun?.text = d.text()
    }

    private fun marks(i: Int) {
        geul("책갈피", jemok = true)
        var first: View? = null
        val ms = Store.marksOf(i)
        if (ms.isEmpty()) first = geul("꽂은 책갈피가 없습니다.")
        for (m in ms) {
            val b = danchu("${m.wichiMal}부터 읽기") { Dokseo.open(this, m.i, m.t, m.kind, m.pos); go(Hm.Reader(m.i, m.t, m.kind)) }
            // 0.4.4 책갈피 지우기 — 톡백 동작 메뉴 또는 길게 누르기(줄마다 단추를 달지 않음)
            ViewCompat.addAccessibilityAction(b, "책갈피 지우기") { _, _ -> Store.removeMark(m); malhagi("책갈피를 지웠습니다."); draw(); true }
            b.setOnLongClickListener { Store.removeMark(m); malhagi("책갈피를 지웠습니다."); draw(); true }
            if (first == null) first = b
        }
        cheotJul(first)
    }

    // ── 내 서재 ──
    // 0.3.0 — 세 겹의 문 안: 서가와 책 수
    private fun munHwamyeon(h: Hm.Mun) {
        val t = geul(h.t, jemok = true)
        val mal = geul("가져오는 중입니다.", jakge = true)
        val hy = "all"   // 0.5.0 보일 책 거르기를 뺌
        pool.execute {
            val r = runCatching { Api.mun(hy) }
            main.post {
                if (cur() != h) return@post
                body.removeView(mal)
                r.onFailure { geul("가져오지 못했습니다. 설정의 새로고침을 눌러 주십시오.") }.onSuccess { all ->
                    var first: View? = null
                    for ((k, tt, n) in all[h.mun].orEmpty()) if (n > 0) { val b = danchu("$tt, ${n}권") { go(Hm.Seoga(h.mun, k, tt)) }; if (first == null) first = b }
                    if (first == null) geul("이 문에는 아직 책이 없습니다.")
                    cheotJul(first ?: t)
                }
            }
        }
    }

    // 0.4.0 — 목소리 열 가지와 미리 듣기, 재생 위치 막대
    private val MOKSORI = listOf("여자 1", "여자 2", "여자 3", "여자 4", "여자 5", "남자 1", "남자 2", "남자 3", "남자 4", "남자 5")
    private fun pctStep(): Int = getSharedPreferences("naeryeo", MODE_PRIVATE).getInt("pctStep", 5)
    private var miriPlayer: android.media.MediaPlayer? = null
    // 0.4.2 — 글을 지금 고른 목소리로 읽어 주기(책 소개 듣기)
    private fun sogaeDeutgi(t: String) {
        Dokseo.pause()
        try { miriPlayer?.release() } catch (e: Exception) {}
        miriPlayer = null
        val v = Store.voice
        pool.execute {
            var d: ByteArray? = null
            runCatching { val (_, hh) = Api.yocheong(t.take(800), v); for (n in 0 until 120) { d = Api.sori(hh); if (d != null) break; Thread.sleep(500) } }
            main.post {
                val b = d ?: run { malhagi("소개를 받지 못했습니다."); return@post }
                runCatching { val f = java.io.File(cacheDir, "sogae.mp3"); f.writeBytes(b); miriPlayer = android.media.MediaPlayer().apply { setDataSource(f.path); prepare(); start() } }
            }
        }
    }
    private fun miriDeutgi(v: Int) {
        Dokseo.pause()
        try { miriPlayer?.release() } catch (e: Exception) {}
        miriPlayer = null
        malhagi(MOKSORI[v] + " 미리 듣기를 준비합니다.")
        pool.execute {
            val t = "안녕하십니까. " + MOKSORI[v] + " 목소리입니다. 이 목소리로 책을 읽어 드립니다."
            var d: ByteArray? = null
            runCatching {
                val (_, hh) = Api.yocheong(t, v)
                for (n in 0 until 80) { d = Api.sori(hh); if (d != null) break; Thread.sleep(500) }
            }
            main.post {
                val b = d
                if (b == null) { malhagi("미리 듣기를 받지 못했습니다. 인터넷을 확인해 주십시오."); return@post }
                runCatching {
                    val f = java.io.File(cacheDir, "miri.mp3"); f.writeBytes(b)
                    miriPlayer = android.media.MediaPlayer().apply { setDataSource(f.path); prepare(); start() }
                }
            }
        }
    }
    private fun jaesaengMakdae() {
        val sb = object : android.widget.SeekBar(this) {
            override fun performAccessibilityAction(action: Int, args: Bundle?): Boolean {
                if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
                    val d = if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) pctStep() else -pctStep()
                    Dokseo.gaPeosenteu(Dokseo.peosenteu() + d)
                    main.postDelayed({ progress = Dokseo.peosenteu().toInt(); if (android.os.Build.VERSION.SDK_INT >= 30) stateDescription = Dokseo.wichiMal(); announceForAccessibility(Dokseo.wichiMal()) }, 400)
                    return true
                }
                return super.performAccessibilityAction(action, args)
            }
        }
        sb.max = 100
        sb.progress = Dokseo.peosenteu().toInt()
        sb.contentDescription = "재생 위치"
        if (android.os.Build.VERSION.SDK_INT >= 30) sb.stateDescription = Dokseo.wichiMal()
        sb.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: android.widget.SeekBar?, p: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(s: android.widget.SeekBar?) { Dokseo.gaPeosenteu((s?.progress ?: 0).toDouble()) }
        })
        body.addView(sb)
    }

    private fun seojae() {
        geul("내 서재", jemok = true)
        Naeryeo.items(this).let { nr -> if (nr.isNotEmpty()) { geul("내려받은 책 ${nr.size}권, ${Naeryeo.meg(nr.sumOf { it.size })}", jakge = true); for (x in nr) danchu(x.t) { go(Hm.Reader(x.id, x.t, x.kind)) } } }   // 0.3.0
        var first: View? = null
        undo?.let { (rc, _) -> first = danchu("되돌리기, 방금 지운 ${rc.t}", keun = true) { doedollrigi() } }
        val ilk = Store.reading
        if (ilk.isEmpty()) geul("읽던 책이 없습니다. 도서관에서 책을 찾아 읽기 시작하면 여기에 저절로 담깁니다.")
        else {
            if (listO >= ilk.size) listO = 0
            geul("읽던 책 ${ilk.size}권 가운데 ${listO + 1}번부터 ${minOf(listO + Api.PER, ilk.size)}번", jakge = true)
            for (rc in ilk.drop(listO).take(Api.PER)) {   // 0.4.4 설정의 목록 줄 수를 따름
                val v = chaekJul(rc, "${rc.t}, ${rc.wichiMal}부터", "${rc.wichiMal}부터", { go(Hm.Reader(rc.i, rc.t, rc.kind)) }, true)
                if (first == null) first = v
            }
            if (listO + Api.PER < ilk.size) danchu("더 보기") { listO += Api.PER; draw() }
            if (listO > 0) danchu("이전 보기") { listO = (listO - Api.PER).coerceAtLeast(0); draw() }
        }
        val da = Store.finished
        // 0.4.4 다 읽은 책·책갈피는 더 보기 안에(펼친 채로 다시 그려도 그대로), 다 읽은 책도 목록 줄 수만큼 넘김
        danchu(if (seojaeDeo) "더 보기 접기" else "더 보기 — 다 읽은 책 ${da.size}권, 책갈피 ${Store.marks.size}개") { seojaeDeo = !seojaeDeo; dasi("더 보기") }
        if (seojaeDeo) {
            if (daO >= da.size) daO = 0
            geul("다 읽은 책 ${da.size}권" + (if (da.isNotEmpty()) " 가운데 ${daO + 1}번부터 ${minOf(daO + Api.PER, da.size)}번" else ""), jakge = true)
            for (rc in da.drop(daO).take(Api.PER)) {
                val b = danchu("${rc.t}, 다 읽음") { go(Hm.Book(rc.i)) }
                ViewCompat.addAccessibilityAction(b, "내 서재에서 지우기") { _, _ -> jiugi(rc); true }
                b.setOnLongClickListener { jiugi(rc); true }
            }
            if (daO + Api.PER < da.size) danchu("다 읽은 책 더 보기") { daO += Api.PER; dasi("다 읽은 책") }
            if (daO > 0) danchu("다 읽은 책 이전 보기") { daO = (daO - Api.PER).coerceAtLeast(0); dasi("다 읽은 책") }
            geul("책갈피 ${Store.marks.size}개", jakge = true)
            for (m in Store.marks.sortedByDescending { it.at }.take(Api.PER)) {
                val b = danchu("책갈피, ${m.t}, ${m.wichiMal}") { Dokseo.open(this, m.i, m.t, m.kind, m.pos); go(Hm.Reader(m.i, m.t, m.kind)) }
                ViewCompat.addAccessibilityAction(b, "책갈피 지우기") { _, _ -> Store.removeMark(m); malhagi("책갈피를 지웠습니다."); dasi("책갈피"); true }
                b.setOnLongClickListener { Store.removeMark(m); malhagi("책갈피를 지웠습니다."); dasi("책갈피"); true }
            }
        }
        cheotJul(first)
    }
    private fun jiugi(rc: ReadRec) {
        val d = Store.jiugi(rc.i) ?: return
        undo = d
        main.removeCallbacks(undoEnd); main.postDelayed(undoEnd, 10_000)
        malhagi("${rc.t}을 내 서재에서 지웠습니다. 10초 안에 되돌리기를 누르시면 되살아납니다.")
        draw()
    }
    private fun doedollrigi() {
        val d = undo ?: return
        main.removeCallbacks(undoEnd); Store.doedollrigi(d.first, d.second); undo = null
        malhagi("${d.first.t}을 되살렸습니다."); draw()
    }

    // ── 설정·도움말 ──
    private fun seoljeong() {
        // 0.4.4 도움말 찾기 결과 — 앞 화면은 감추고 결과만, 커서는 결과 첫 줄에
        doumQ?.let { q ->
            val r = DOUM.filter { it.first.contains(q) || it.second.contains(q) }
            danchu("도움말 찾기 마치기") { doumQ = null; dasi("도움말 찾기") }
            val t = geul("「$q」 도움말 ${r.size}개" + (if (r.isEmpty()) ". 다른 낱말로 찾아 주십시오." else ""), jemok = true)
            for ((x, y) in r) geul("$x. $y")
            cheotJul(t); return
        }
        val t = geul("설정·도움말", jemok = true)
        geul("목소리 고르기, 지금 " + MOKSORI[Store.voice.coerceIn(0, 9)], jakge = true)   // 0.4.0 — 목소리 열 가지
        for (v in 0..9) danchu((if (Store.voice == v) "고름, " else "") + MOKSORI[v] + ", 누르면 고르고 미리 듣기") { Store.voice = v; miriDeutgi(v); dasi(MOKSORI[v] + ", 누르면") }
        val rn = Store.rateNames[Store.rateIndex]   // 0.4.4 빠르기는 두 단추(아주 빠르게 다음에 아주 느리게로 갑자기 돌아가지 않게)
        danchu("더 느리게 읽기, 지금 $rn") { if (Store.rateIndex > 0) { Store.rateIndex -= 1; Dokseo.setRate() }; malhagi(Store.rateNames[Store.rateIndex]); dasi("더 느리게 읽기") }
        danchu("더 빠르게 읽기, 지금 $rn") { if (Store.rateIndex < Store.rates.size - 1) { Store.rateIndex += 1; Dokseo.setRate() }; malhagi(Store.rateNames[Store.rateIndex]); dasi("더 빠르게 읽기") }
        danchu("재생 위치 막대 한 번에: " + pctStep() + "퍼센트") { getSharedPreferences("naeryeo", MODE_PRIVATE).edit().putInt("pctStep", if (pctStep() == 5) 1 else 5).apply(); malhagi("재생 위치 막대 한 번에 " + pctStep() + "퍼센트"); dasi("재생 위치 막대") }
        danchu("앱 안내 말: ${if (Store.speechOn) "켜짐" else "꺼짐"}") { Store.speechOn = !Store.speechOn; dasi("앱 안내 말") }
        danchu("첫 화면 목록: ${MUN.first { it.first == cheotMun() }.second}") {   // 0.5.0 누를 때마다 주제별 → 장르별 → 테마별
            val n = MUN[(MUN.indexOfFirst { it.first == cheotMun() } + 1) % MUN.size]
            getSharedPreferences("naeryeo", MODE_PRIVATE).edit().putString("cheotMun", n.first).apply(); malhagi("첫 화면 목록, ${n.second}"); dasi("첫 화면 목록")
        }
        danchu("목록 줄 수: ${Api.PER}줄") { Naeryeo.nextJul(this); malhagi("목록 줄 수, ${Api.PER}줄"); dasi("목록 줄 수") }   // 0.3.0
        danchu("와이파이에서만 내려받기: ${if (Naeryeo.wifiOnly(this)) "켜짐" else "꺼짐"}") { Naeryeo.setWifiOnly(this, !Naeryeo.wifiOnly(this)); dasi("와이파이에서만") }
        Naeryeo.items(this).let { nr ->
            geul("내려받은 책 ${nr.size}권, ${Naeryeo.meg(nr.sumOf { it.size })}", jakge = true)
            if (nr.isNotEmpty()) {
                if (!moduJiugiMureum) danchu("내려받은 책 모두 지우기") { moduJiugiMureum = true; dasi("정말 모두 지우기") }
                else {   // 0.4.4 한 번 더 물음 — 안내와 단추를 한 자리에
                    danchu("정말 모두 지우기, ${nr.size}권을 지우면 되돌릴 수 없습니다", keun = true) { Naeryeo.removeAll(this); moduJiugiMureum = false; malhagi("내려받은 책을 모두 지웠습니다."); dasi("내려받은 책") }
                    danchu("그만두기") { moduJiugiMureum = false; dasi("내려받은 책 모두 지우기") }
                }
            }
        }
        danchu("새로고침") { Dokseo.stop(); for (k in 0..2) while (stacks[k].size > 1) stacks[k].removeAt(stacks[k].size - 1); tab = 0; draw(); malhagi("새로 불러왔습니다.") }
        danchu(Ollim.seoljeongMal(this)) { Ollim.seoljeongNureum(this) }   // 0.4.3 업데이트 — 새로고침 바로 아래 한 곳
        geul("도움말", jemok = true)
        // 0.4.4 도움말 찾기 — 낱말을 적고 찾기를 누르면 그 낱말이 든 도움말만 나옴
        val chaj = EditText(this).apply {
            hint = "찾을 낱말"; inputType = InputType.TYPE_CLASS_TEXT; imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = kadeuBg(); setPadding(dp(14), dp(12), dp(14), dp(12)); setTextColor(Saek.geulja(this@LibActivity))
        }
        val chajgi = { val q = chaj.text.toString().trim(); if (q.isNotEmpty()) { doumQ = q; scroll.scrollTo(0, 0); draw() } }
        chaj.setOnEditorActionListener { _, a, _ -> if (a == EditorInfo.IME_ACTION_SEARCH) { chajgi(); true } else false }
        body.addView(chaj, lp(8))
        danchu("도움말 찾기") { chajgi() }
        danchu(if (doumPyeolchim) "도움말 모두 접기" else "도움말 모두 펼치기, ${DOUM.size}가지") { doumPyeolchim = !doumPyeolchim; dasi("도움말 모두") }
        if (doumPyeolchim) for ((q, a) in DOUM) geul("$q. $a")
        geul("회원: ${Hoewon.ireum(this)}", jakge = true)   // 0.4.4 아이폰과 같게
        geul("AI점자도서관 안드로이드 ${PAN}판, 빌드 $BILDEU", jakge = true)
        geul("주관 사단법인 한국시각장애인현장영상해설협회. 전화 02-363-4455, 메일 ada015@naver.com", jakge = true)
        cheotJul(t)
    }

    // 0.4.4 도움말 — 아이폰 도움말과 같은 가짓수로 맞춤(안드로이드 말로)
    private val DOUM = listOf(
        "이어 듣기" to "읽던 책이 있으면 도서관 첫 화면 찾기 칸 아래에 이어 듣기가 나옵니다. 누르면 독서기가 열리면서 읽던 자리부터 바로 읽고, 커서는 멈춤 단추에 놓입니다.",
        "책 고르면 바로 읽기" to "목록에서 책 이름을 한 번 두드리면 독서기가 열리면서 바로 읽습니다. 읽던 책이면 읽던 자리부터 읽습니다. 소리책과 동영상도 바로 틉니다. 커서는 멈춤 단추에 놓이니 잘못 고르셨으면 바로 멈추십시오. 책 소개, 지은이, 출판사, 내려받기는 독서기 더 보기 안의 책 정보에 있습니다.",
        "책 찾기" to "도서관 첫 화면 맨 위 이름 줄 바로 아래의 찾을 책 이름 칸에 낱말을 적고 찾기를 누릅니다. 찾은 결과만 나오고 커서가 첫 줄에 놓입니다. 다시 찾을 때는 뒤로를 누릅니다.",
        "갈래" to "글자책, 소리책, 대본, 입체낭독, 인터넷소설, 점자책으로 나뉩니다. 대본에는 드라마, 영화, 연극·뮤지컬, 라디오 드라마 대본이 들어 있습니다. 인터넷소설은 한 줄에 작품 하나로 나오고, 누르면 권이 차례로 나옵니다.",
        "책 묶어 보기" to "목록에는 파일 이름이 아니라 책 제목과 권수가 한 줄로 나옵니다. 보기: 야인시대, 전 117회. 그 줄을 누르면 야인시대 1회, 야인시대 2회처럼 제목과 번호가 차례대로 나옵니다. 찾기를 해도 같은 책은 묶음 한 줄로 나옵니다.",
        "첫 화면 목록" to "도서관 첫 화면에는 찾기 칸과 이어 듣기 아래에 설정의 첫 화면 목록에서 고른 목록(주제별, 장르별, 테마별 가운데 하나)의 서가가 바로 나옵니다. 처음 값은 장르별이고, 누를 때마다 바뀝니다. 다른 두 목록은 그 아래 한 줄씩 있습니다.",
        "목록 넘기기" to "목록은 처음에 한 쪽 15줄입니다. 설정의 목록 줄 수를 누를 때마다 5, 10, 15, 20, 30줄로 바뀝니다. 목록 줄에서 톡백의 목록 넘기기 동작(앞으로, 뒤로)을 하면 다음 목록과 앞 목록이 나오고 커서는 그 목록 첫 줄에 놓입니다. 손짓이 안 될 때는 맨 아래 다음 목록을 누릅니다. 내 서재는 더 보기와 이전 보기로 넘깁니다.",
        "세 겹의 문" to "도서관 첫 화면에 주제별, 장르별, 테마별 세 문이 있습니다. 문을 누르면 서가와 책 수가, 서가를 누르면 책 목록이 나옵니다. 주제별은 도서관 십진분류, 장르별은 판타지·무협 같은 갈래, 테마별은 이달의 새 책 같은 모음입니다.",
        "책 정보" to "독서기 더 보기 안의 책 정보를 누르면 갈래, 들어온 날, 읽던 자리가 나오고, 내려받기도 여기서 합니다. 책 소개가 있으면 지은이, 출판사, 책 소개와 책 소개 듣기가 나옵니다. 독서기로 들을 수 없는 형식이면 그렇다고 알려 드립니다.",
        "독서기" to "글자책은 사람 목소리로 문단마다 읽어 줍니다. 앞 문단을 읽는 동안 뒤 문단을 미리 만들어 둡니다. 겉에는 읽기, 앞으로 30초, 뒤로 30초, 재생 위치가 있고 그 아래에 지금 읽는 글이 나옵니다. 빠르기, 목소리, 책갈피, 처음부터, 책 정보는 더 보기 안에 있습니다.",
        "처음부터" to "독서기의 더 보기 안에 있습니다. 글자책은 첫 문단으로, 소리책과 동영상은 맨 처음으로 갑니다.",
        "앞으로 30초와 뒤로 30초" to "앞으로 30초는 지금 읽는 곳에서 30초 뒤의 내용으로 건너뛰고, 뒤로 30초는 30초 전의 내용으로 되돌아갑니다. 글자책은 읽는 빠르기로 30초 분량의 글만큼 움직입니다.",
        "재생 위치 막대" to "책 읽는 화면의 재생 위치에 커서를 두면 전체 시간과 지금 시간, 퍼센트를 읽어 줍니다. 위로 쓸거나 음량 단추로 앞으로, 아래로 쓸거나 음량 단추로 뒤로 갑니다. 한 번에 움직이는 양은 설정에서 5퍼센트나 1퍼센트로 바꿉니다.",
        "빠르기" to "독서기 더 보기와 설정에 더 느리게, 더 빠르게 두 단추가 있습니다. 아주 느리게, 보통, 조금 빠르게, 빠르게, 아주 빠르게 다섯 가지입니다.",
        "목소리 고르기" to "설정의 목소리 고르기에서 여자 1부터 5, 남자 1부터 5까지 열 가지 가운데 고릅니다. 누르면 그 목소리로 바뀌고 바로 미리 들려 드립니다. 처음 값은 여자 1입니다. 독서기 더 보기의 목소리 바꾸기로도 누를 때마다 다음 목소리로 바뀝니다.",
        "화면을 꺼도 읽기" to "읽는 중에 화면을 끄거나 다른 앱으로 가거나 폰을 주머니에 넣어도 계속 읽습니다. 알림 칸에 AI점자도서관 독서기가 나옵니다. 전화가 오면 멈췄다가 끝나면 이어 읽고, 이어폰을 뽑으면 멈춥니다.",
        "이어폰 단추와 잠금 화면" to "이어폰 단추를 한 번 누르면 멈춤과 읽기가 바뀝니다. 다음과 이전 단추는 글자책이면 다음 문단과 앞 문단, 소리책이면 30초 앞과 뒤로 갑니다. 잠금 화면과 알림 칸에서도 같은 단추를 씁니다.",
        "소리책과 동영상" to "소리책과 동영상도 같은 독서기에서 틉니다. 듣던 자리를 기억해 다음에 그 자리부터 이어 듣습니다.",
        "입체낭독" to "드라마 대본을 인물마다 다른 목소리로 연기하듯 읽은 소리 드라마입니다. 이야기꾼과 주인공이 서로 다른 목소리로 나옵니다. 소리책처럼 독서기에서 틀고, 듣던 자리를 기억합니다.",
        "책갈피" to "독서기 더 보기에서 이 자리에 책갈피 꽂기를 누릅니다. 책갈피 보기나 내 서재 더 보기에서 그 자리부터 읽습니다. 지울 때는 책갈피 줄에서 톡백 동작 메뉴를 열어 책갈피 지우기를 고르거나, 그 줄을 길게 누릅니다.",
        "내 서재" to "읽기 시작한 책은 저절로 내 서재에 담깁니다. 가장 최근에 본 책이 맨 위에 있고 목록 줄 수만큼씩 넘깁니다. 다 읽은 책과 책갈피는 더 보기 안에 있습니다.",
        "내 서재에서 지우기" to "지울 책에서 톡백 동작 메뉴를 열어 내 서재에서 지우기를 고르거나, 그 줄을 길게 누릅니다. 10초 안에 되돌리기를 누르면 되살아납니다. 되돌리지 않으면 폰에 내려받아 둔 그 책도 함께 지워 공간을 돌려드립니다.",
        "내려받기" to "책 정보 화면의 폰에 내려받기를 누르면 책을 폰에 받아 둡니다. 글자책은 글 전체를, 소리책은 소리 파일을 받습니다. 받은 책은 이 앱 안에만 있고 다른 앱에서는 보이지 않습니다. 내 서재 맨 위에 모입니다.",
        "인터넷 없이 듣기" to "인터넷이 끊기거나 데이터가 모자라도 내려받은 책은 들을 수 있습니다. 소리책은 받은 파일 그대로, 글자책은 폰 목소리로 읽습니다. 인터넷이 다시 되면 도서관 목소리로 읽습니다.",
        "와이파이에서만 내려받기" to "설정에서 켜 두면 휴대폰 데이터로는 내려받지 않습니다. 처음에는 켜져 있습니다. 데이터로도 받으려면 끄십시오.",
        "내려받은 책 지우기" to "책 정보 화면의 폰에서 지우기로 한 권씩 지웁니다. 설정의 내려받은 책 모두 지우기를 누르면 한 번 더 묻고, 정말 모두 지우기를 누르면 한꺼번에 지웁니다. 그만두기를 누르면 그대로 둡니다.",
        "탭 다시 누르기" to "지금 보고 있는 탭을 한 번 더 누르면 그 탭의 첫 화면 맨 위로 돌아갑니다.",
        "뒤로" to "속 화면 맨 위에 뒤로가 있습니다. 폰의 뒤로 동작으로도 뒤로 가며, 앱 밖으로 나가지는 않습니다.",
        "도움말 찾기" to "설정·도움말 화면의 찾을 낱말 칸에 낱말을 적고 도움말 찾기를 누르면 그 낱말이 든 도움말만 나오고 커서가 첫 줄에 놓입니다. 도움말 찾기 마치기를 누르면 설정으로 돌아갑니다.",
        "앱 안내 말" to "앱이 스스로 알리는 말(책갈피를 꽂았습니다 등)을 켜고 끕니다. 책 읽는 목소리는 그대로입니다.",
        "회원 등록" to "처음 켤 때 이름과 휴대전화 번호로 한 번만 등록합니다. 등록하면 이 폰만의 회원 열쇠가 담겨 도서관과 독서기가 열립니다. 폰을 바꾸면 새 폰에서 같은 번호로 다시 등록합니다. 설정 아래에 회원 이름이 나옵니다.",
        "새로고침" to "앱이 이상하거나 새 책이 안 보일 때 설정의 새로고침을 누르십시오.",
        "업데이트 — 새 판 받기" to "도서관 앱은 켤 때와 하루 두 번쯤 협회 서버에 새 판이 나왔는지 스스로 물어봅니다. 새 판이 있으면 폰 알림으로 AI점자도서관 새 판이 나왔습니다, 두드리면 업데이트합니다라고 알려 드리고, 도서관 첫 화면 머리 바로 아래에도 같은 말과 단추를 한 줄로 띄웁니다. 알림이나 그 줄을 두드리시면 앱이 새 판을 스스로 받아 설치 화면을 엽니다. 설치를 한 번 눌러 주시면 됩니다. 처음 한 번은 이 출처 허용을 켜는 화면이 열리니 켜신 뒤 폰의 뒤로 동작으로 돌아오시면 이어서 업데이트합니다. 새 판으로 바뀌면 바뀌었다고 알림을 드립니다. 설정의 새로고침 바로 아래 업데이트 단추로 언제든 새 판이 있는지 살피실 수 있습니다. 내 서재, 책갈피, 내려받은 책은 그대로 남습니다."
    )
}
