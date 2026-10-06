// AI점자도서관 안드로이드 — 화면 (0.2.0, 빌드 261002-L1, 이사장님 승인 "2" — 아이폰 0.2.0 과 동시에)
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
    companion object { const val PAN = "0.2.0"; const val BILDEU = "261002-L1" }
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
    private val undoEnd = Runnable { undo?.let { /* 되돌리지 않으면 그대로 지워짐 */ }; undo = null; if (cur() is Hm.Seojae) draw() }

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
    }
    override fun onDestroy() { Dokseo.bakkwim = null; super.onDestroy() }

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
    private fun go(h: Hm) { stacks[tab].add(h); listO = 0; draw() }
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
    private fun cheotJul(v: View?) { v ?: return; scroll.scrollTo(0, 0); v.postDelayed({ v.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null) }, 350) }

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
        playerView?.player = null; playerView = null
        body.removeAllViews(); tabs()
        if (!Hoewon.deungrokdoem(this)) { deungrok(); return }
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
            is Hm.Seoga -> paged(h.t) { o -> Api.seoga(h.mun, h.k, Naeryeo.hyeongtae(this), o) }
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
            contentDescription = "AI점자도서관. 주관 사단법인 한국시각장애인현장영상해설협회"
            ViewCompat.setAccessibilityHeading(this, true)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        val logo = LogoView(this).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
        box.addView(logo, LinearLayout.LayoutParams(dp(92), ViewGroup.LayoutParams.WRAP_CONTENT))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
        col.addView(TextView(this).apply { text = "AI점자도서관"; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f); setTypeface(typeface, Typeface.BOLD) })
        col.addView(TextView(this).apply { text = "주관 사단법인 한국시각장애인현장영상해설협회"; setTextColor(Saek.geum); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f) })
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
        var cheot: View? = null
        Store.last?.let { l ->
            cheot = chaekJul(l, "이어 듣기, ${l.t}, ${l.wichiMal}부터", "이어 듣기 · ${l.wichiMal}부터", { go(Hm.Reader(l.i, l.t, l.kind)) }, false)
        }
        geul("세 겹의 문", jemok = true)   // 0.3.0
        danchu("주제별로 찾기, 십진분류") { go(Hm.Mun("jujae", "주제별")) }
        danchu("장르별로 찾기") { go(Hm.Mun("jangreu", "장르별")) }
        danchu("테마별로 찾기") { go(Hm.Mun("tema", "테마별")) }
        danchu("보일 책: ${Naeryeo.hyeongtaeMal(this)}") { Naeryeo.nextHyeongtae(this); malhagi("보일 책, ${Naeryeo.hyeongtaeMal(this)}"); draw() }
        val chaj = EditText(this).apply {
            hint = "찾을 책 이름"; inputType = InputType.TYPE_CLASS_TEXT; imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = kadeuBg(); setPadding(dp(14), dp(12), dp(14), dp(12)); setTextColor(Saek.geulja(this@LibActivity))
        }
        val chajgi = { val q = chaj.text.toString().trim(); if (q.isNotEmpty()) go(Hm.Find(q)) }
        chaj.setOnEditorActionListener { _, a, _ -> if (a == EditorInfo.IME_ACTION_SEARCH) { chajgi(); true } else false }
        body.addView(chaj, lp(10))
        danchu("찾기") { chajgi() }
        geul("갈래", jemok = true)
        val galMal = geul("가져오는 중입니다.", jakge = true)
        pool.execute {
            val g = runCatching { Api.gal() }
            main.post {
                if (cur() != Hm.Home) return@post
                g.onSuccess { list ->
                    body.removeView(galMal)
                    for (x in list) {
                        val b = danchu("${x.g} ${"%,d".format(x.n)}${x.dan}") { go(Hm.Gal(x.g)) }
                        b.setCompoundDrawablesRelativeWithIntrinsicBounds(GradientDrawable().apply { setColor(Saek.pyoji(x.g)); cornerRadius = dp(3).toFloat(); setSize(dp(8), dp(28)) }, null, null, null)
                        b.compoundDrawablePadding = dp(12)
                    }
                }.onFailure { galMal.text = "도서관에 닿지 못했습니다. 인터넷을 확인한 뒤 설정의 새로고침을 눌러 주십시오." }
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
                    val b = danchu(it.t) { if (it.i != null) go(Hm.Book(it.i)) else if (it.j != null) go(Hm.Jakbon(it.j, it.t)) }
                    if (first == null) first = b
                }
                if (lr.o + Api.PER < lr.modu) danchu("더 보기") { listO = lr.o + Api.PER; draw() }
                if (lr.o > 0) danchu("이전 보기") { listO = (lr.o - Api.PER).coerceAtLeast(0); draw() }
                cheotJul(first)
            }
        }
    }

    private fun book(i: Int) {
        val mal = geul("책 정보를 가져오는 중입니다.", jakge = true)
        pool.execute {
            val r = runCatching { Api.book(i) }
            main.post {
                r.onFailure { mal.text = "책 정보를 가져오지 못했습니다." }.onSuccess { b ->
                    body.removeView(mal)
                    val t = geul(b.t, jemok = true)
                    geul("갈래 ${b.g}" + (if (b.nal.isNotEmpty()) ", ${b.nal}" else ""), jakge = true)
                    val ireum = when (b.kind) { "sori" -> "듣기"; "yeongsang" -> "보기"; else -> "읽기" }
                    danchu(if (Store.rec(i) != null) "이어서 $ireum" else ireum, keun = true) { go(Hm.Reader(i, b.t, b.kind)) }
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
        Dokseo.open(this, h.i, h.t, h.k)
        geul(h.t, jemok = true)
        readerJul = geul("", jakge = true)
        if (h.k == "yeongsang") {
            val pv = PlayerView(this).apply { player = Dokseo.player; useController = true }
            playerView = pv
            body.addView(pv, LinearLayout.LayoutParams(-1, dp(220)).apply { topMargin = dp(8) })
        }
        readerDan = danchu("읽기", keun = true) { Dokseo.toggle() }
        if (h.k == "geul") readerMun = geul("")
        val more = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        danchu(if (h.k == "geul") "다음 문단" else "30초 앞으로") { Dokseo.next() }
        danchu(if (h.k == "geul") "앞 문단" else "30초 뒤로") { Dokseo.prev() }
        danchu("이 자리에 책갈피 꽂기") { Dokseo.markHere(); malhagi("책갈피를 꽂았습니다.") }
        danchu("빠르기: ${Store.rateNames[Store.rateIndex]}") { Store.rateIndex = (Store.rateIndex + 1) % Store.rates.size; Dokseo.setRate(); draw(); malhagi("빠르기 ${Store.rateNames[Store.rateIndex]}") }
        if (Store.marksOf(h.i).isNotEmpty()) danchu("책갈피 보기") { go(Hm.Marks(h.i)) }
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
        for (m in Store.marksOf(i)) { val b = danchu("${m.t}, ${m.wichiMal}") { Dokseo.open(this, m.i, m.t, m.kind); Dokseo.gaPo(m.pos.toInt()); go(Hm.Reader(m.i, m.t, m.kind)) }; if (first == null) first = b }
        cheotJul(first)
    }

    // ── 내 서재 ──
    // 0.3.0 — 세 겹의 문 안: 서가와 책 수
    private fun munHwamyeon(h: Hm.Mun) {
        val t = geul(h.t, jemok = true)
        val mal = geul("가져오는 중입니다.", jakge = true)
        val hy = Naeryeo.hyeongtae(this)
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
            for (rc in ilk.drop(listO).take(15)) {
                val v = chaekJul(rc, "${rc.t}, ${rc.wichiMal}부터", "${rc.wichiMal}부터", { go(Hm.Reader(rc.i, rc.t, rc.kind)) }, true)
                if (first == null) first = v
            }
            if (listO + Api.PER < ilk.size) danchu("더 보기") { listO += Api.PER; draw() }
            if (listO > 0) danchu("이전 보기") { listO = (listO - Api.PER).coerceAtLeast(0); draw() }
        }
        val da = Store.finished
        var pyeolchim = false
        lateinit var deo: Button
        val dabox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        deo = danchu("더 보기 — 다 읽은 책 ${da.size}권, 책갈피 ${Store.marks.size}개") {
            pyeolchim = !pyeolchim; dabox.visibility = if (pyeolchim) View.VISIBLE else View.GONE
        }
        body.addView(dabox)
        // 다 읽은 책과 책갈피는 더 보기 안에
        for (rc in da.take(15)) {
            val b = Button(this).apply { text = "${rc.t}, 다 읽음"; isAllCaps = false; background = kadeuBg(); setTextColor(Saek.geulja(this@LibActivity)); setOnClickListener { go(Hm.Book(rc.i)) } }
            ViewCompat.addAccessibilityAction(b, "내 서재에서 지우기") { _, _ -> jiugi(rc); true }
            dabox.addView(b, lp(6))
        }
        for (m in Store.marks.sortedByDescending { it.at }.take(15)) {
            val b = Button(this).apply { text = "책갈피, ${m.t}, ${m.wichiMal}"; isAllCaps = false; background = kadeuBg(); setTextColor(Saek.geulja(this@LibActivity)); setOnClickListener { Dokseo.open(this@LibActivity, m.i, m.t, m.kind); Dokseo.gaPo(m.pos.toInt()); go(Hm.Reader(m.i, m.t, m.kind)) } }
            dabox.addView(b, lp(6))
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
        val t = geul("설정·도움말", jemok = true)
        danchu("읽기 빠르기: ${Store.rateNames[Store.rateIndex]}") { Store.rateIndex = (Store.rateIndex + 1) % Store.rates.size; Dokseo.setRate(); draw() }
        danchu("목소리: ${if (Store.voice == 0) "1번 여자 목소리" else "2번 여자 목소리"}") { Store.voice = if (Store.voice == 0) 1 else 0; draw() }
        danchu("앱 안내 말: ${if (Store.speechOn) "켜짐" else "꺼짐"}") { Store.speechOn = !Store.speechOn; draw() }
        danchu("목록 줄 수: ${Api.PER}줄") { Naeryeo.nextJul(this); malhagi("목록 줄 수, ${Api.PER}줄"); draw() }   // 0.3.0
        danchu("와이파이에서만 내려받기: ${if (Naeryeo.wifiOnly(this)) "켜짐" else "꺼짐"}") { Naeryeo.setWifiOnly(this, !Naeryeo.wifiOnly(this)); draw() }
        Naeryeo.items(this).let { nr -> geul("내려받은 책 ${nr.size}권, ${Naeryeo.meg(nr.sumOf { it.size })}", jakge = true); if (nr.isNotEmpty()) danchu("내려받은 책 모두 지우기") { Naeryeo.removeAll(this); malhagi("내려받은 책을 모두 지웠습니다."); draw() } }
        danchu("새로고침") { Dokseo.stop(); for (k in 0..2) while (stacks[k].size > 1) stacks[k].removeAt(stacks[k].size - 1); tab = 0; draw(); malhagi("새로 불러왔습니다.") }
        geul("도움말", jemok = true)
        for ((q, a) in listOf(
            "책 찾기" to "도서관 첫 화면의 찾을 책 이름 칸에 이름을 적고 찾기를 누르면 찾은 책이 15권씩 나옵니다.",
            "이어 듣기" to "첫 화면 맨 위 이어 듣기를 누르면 바로 전에 듣던 책을 듣던 자리부터 이어 듣습니다.",
            "책 묶어 보기" to "목록에는 파일 이름이 아니라 책 제목과 권수가 한 줄로 나옵니다. 보기: 야인시대, 전 117회. 그 줄을 누르면 야인시대 1회, 야인시대 2회처럼 제목과 번호가 차례대로 나옵니다. 찾기를 해도 같은 책은 묶음 한 줄로 나옵니다.",
            "입체낭독" to "드라마 대본을 인물마다 다른 목소리로 연기하듯 읽은 소리 드라마입니다. 이야기꾼과 주인공이 서로 다른 목소리로 나옵니다. 지금은 야인시대가 날마다 몇 회씩 늘어납니다. 소리책처럼 독서기에서 틀고, 듣던 자리를 기억합니다.",
            "내 서재" to "읽기 시작한 책은 저절로 내 서재에 담깁니다. 가장 최근에 본 책이 맨 위에 있고 15권씩 넘깁니다.",
            "내 서재에서 지우기" to "지울 책에서 톡백 동작 메뉴를 열어 내 서재에서 지우기를 고르거나, 그 줄을 길게 누릅니다. 10초 안에 되돌리기를 누르면 되살아납니다.",
            "책갈피" to "독서기에서 이 자리에 책갈피 꽂기를 누르면 그 자리가 남습니다. 내 서재의 더 보기에서 찾을 수 있습니다.",
            "목록 넘기기" to "목록은 처음에 한 쪽 15줄입니다. 설정의 목록 줄 수를 누를 때마다 5, 10, 15, 20, 30줄로 바뀝니다.",
            "세 겹의 문" to "도서관 첫 화면에 주제별, 장르별, 테마별 세 문이 있습니다. 문을 누르면 서가와 책 수가, 서가를 누르면 책 목록이 나옵니다.",
            "보일 책" to "첫 화면의 보일 책을 누를 때마다 모든 책, 소리로 듣는 책만, 점자책만으로 바뀌고 문과 서가에 그 책만 나옵니다.",
            "내려받기" to "책 정보 화면의 폰에 내려받기를 누르면 책을 폰에 받아 둡니다. 글자책은 글 전체를, 소리책은 소리 파일을 받습니다. 받은 책은 이 앱 안에만 있고 다른 앱에서는 보이지 않습니다. 내 서재 맨 위에 모입니다.",
            "인터넷 없이 듣기" to "인터넷이 끊기거나 데이터가 모자라도 내려받은 책은 들을 수 있습니다. 소리책은 받은 파일 그대로, 글자책은 폰 목소리로 읽습니다.",
            "와이파이에서만 내려받기" to "설정에서 켜 두면 휴대폰 데이터로는 내려받지 않습니다. 처음에는 켜져 있습니다.",
            "내려받은 책 지우기" to "책 정보 화면의 폰에서 지우기로 한 권씩, 설정의 내려받은 책 모두 지우기로 한꺼번에 지웁니다.",
            "새로고침" to "앱이 이상하거나 새 판이 나왔을 때 설정의 새로고침을 누르십시오."
        )) geul("$q. $a")
        geul("AI점자도서관 안드로이드 ${PAN}판, 빌드 $BILDEU", jakge = true)
        geul("주관 사단법인 한국시각장애인현장영상해설협회. 전화 02-363-4455, 메일 ada015@naver.com", jakge = true)
        cheotJul(t)
    }
}
