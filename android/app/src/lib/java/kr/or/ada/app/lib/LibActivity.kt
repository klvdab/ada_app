// 0.7.0 (261014-L16, 도서관 창 클, 이사장님 승인 2026-10-14 「1」·「2」) 엔터테인먼트 탭 — 나스 AI문화관의 공개 갈래(첫 갈래 화면해설 작품). 탭 번호는 그대로(엔터테인먼트는 3), 놓는 자리만 둘째
// AI점자도서관 안드로이드 — 화면 (0.6.0판, 빌드 261012-L12 — 곱씹어 읽기와 사전) (0.5.2판 — 외국 도서 문, 글 판 4)
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
import org.json.JSONObject

sealed class Hm {
    data class Mun(val mun: String, val t: String) : Hm(); data class Seoga(val mun: String, val k: String, val t: String) : Hm()   // 0.3.0 세 겹의 문
    object Home : Hm(); object Seojae : Hm(); object Seoljeong : Hm()
    data class Gal(val g: String) : Hm(); data class Jakbon(val j: String, val t: String) : Hm(); data class Find(val s: String) : Hm()
    data class Book(val i: Int) : Hm(); data class Reader(val i: Int, val t: String, val k: String) : Hm(); data class Marks(val i: Int) : Hm()
    data class Pureo(val g: String) : Hm(); data class SokChatgi(val i: Int, val t: String) : Hm(); data class Sajeon(val c: String, val m: String) : Hm(); object Jungyo : Hm(); object Nanmal : Hm()   // 0.6.0 곱씹어 읽기와 사전
    object EntHome : Hm(); data class Ent(val j: String, val t: String) : Hm(); data class EntFind(val s: String) : Hm()   // 0.7.0 엔터테인먼트
    object Gesipan : Hm(); data class GsMok(val b: String, val t: String, val w: Boolean) : Hm(); data class GsBogi(val b: String, val t: String, val id: String) : Hm(); data class GsSseugi(val b: String, val t: String) : Hm()   // 0.8.4 게시판
}

class LibActivity : Activity() {
    companion object { const val PAN = "0.8.4"; const val BILDEU = "261019-L23" }   // 0.8.4 게시판 — 공지·묻고 답하기·후기·제작 요청·불편 신고(도클, 대표님 승인 2026-10-19 「그래」)   // 0.8.3 회원 등급 — 설정에 내 등급, 장애 확인 서류 사진 올리기, 잠긴 책 알림(이사장님 승인 2026-10-19 11시 50분 「1」)   // 0.7.1 탭 고르면 첫 줄·책 듣기 멈춤 줄이기   // 0.7.0 엔터테인먼트 탭   // 0.6.2 사전 우리말샘·한국고전종합DB 도움말   // 0.6.1 한자음 읽기(중국 고전을 우리 한자음으로, 간체자도, 원문 함께 보기)   // 0.6.0 곱씹어 읽기(다시 듣기·풀어 읽기·중요 표시·책 속 찾기)와 사전·내 낱말장   // 0.5.2 외국 도서 문(외국 도서 → 언어 → 장르와 주제), 글 판 4(강제 줄바꿈 바로잡은 글·옛 자리 옮기기), 도움말 둘   // 0.5.1 첫 화면 맨 위 이름 줄·바로 아래 찾기, 목록에서 한 번 두드리면 바로 읽기, 넘긴 뒤 커서 첫 줄 다지기(도서관 창 클, 이사장님 승인 「1」). 0.4.4 아이폰과 견주어 네 묶음 고침(도서관 창 클, 이사장님 승인 「1」). 0.4.3 새 판 알림과 업데이트(대장클, 이사장님 지시)
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(3)
    private val stacks = arrayOf(mutableListOf<Hm>(Hm.Home), mutableListOf<Hm>(Hm.Seojae), mutableListOf<Hm>(Hm.Seoljeong), mutableListOf<Hm>(Hm.EntHome))
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
    private var bgDangye = 0; private var bgJ = ""; private var bgR = ""; private var bgN = ""; private var bgNajung = false   // 0.8.2 회원 정보 여쭙기

    override fun onCreate(s: Bundle?) {
        Api.PER = Naeryeo.jul(this)   // 0.3.0 — 목록 줄 수
        super.onCreate(s)
        Api.ctx = applicationContext; Store.load(this); Dokseo.ctx = applicationContext
        Dokseo.bakkwim = { main.post { if (cur() is Hm.Reader) drawReaderState() } }
        Dokseo.allim = { m -> main.post { malhagi(m) } }
        Dokseo.ieoBakkwim = { ni, nt, nk -> main.post {   // 0.8.0 이어 듣기 — 독서기 화면을 새 곡·회로
            val st = stacks[tab]; val top = st.lastOrNull()
            if (top is Hm.Reader && Api.isEnt(top.i)) { st[st.size - 1] = Hm.Reader(ni, nt, nk); draw() }
        } }
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
        LibGirok.sijak(this)   // 0.6.3 이용 기록(도서관 창 클, 이사장님 승인)
        pool.execute { Hoewon.bogangHwakin(this); main.post { if (Hoewon.bogangDoem(this) && !bgNajung) draw() } }   // 0.8.2
        pool.execute { Hoewon.deungHwakin(this); main.post { if (cur() is Hm.Seoljeong) draw() } }   // 0.8.3 켤 때 내 회원 등급
        Ollim.intentBoda(this, intent)
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 44)   // 새 판 알림을 띄우려고 한 번 여쭘
    }
    // ── 0.8.3 (261019-L22) 장애 확인하기 — 복지카드·장애인증명서 사진을 골라 올림
    private fun jeungGoreugi() {
        val ig = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply { type = "image/*"; addCategory(android.content.Intent.CATEGORY_OPENABLE) }
        try { @Suppress("DEPRECATION") startActivityForResult(android.content.Intent.createChooser(ig, "장애 확인 서류 사진 고르기"), 7301) }
        catch (e: Exception) { malhagi("사진을 고를 앱을 열지 못했습니다.") }
    }
    @Deprecated("") override fun onActivityResult(rq: Int, rs: Int, d: android.content.Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(rq, rs, d)
        if (rq != 7301) return
        val u = d?.data
        if (rs != RESULT_OK || u == null) { malhagi("사진을 고르지 않으셨습니다."); return }
        malhagi("올리는 중입니다.")
        pool.execute {
            val jpg = runCatching {
                val raw = contentResolver.openInputStream(u)!!.use { it.readBytes() }
                val bm = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size)!!
                val k = minOf(1f, 2000f / maxOf(bm.width, bm.height))   // 너무 크면 줄여 8메가바이트를 넘지 않게
                val sb = if (k < 1f) android.graphics.Bitmap.createScaledBitmap(bm, (bm.width * k).toInt(), (bm.height * k).toInt(), true) else bm
                java.io.ByteArrayOutputStream().also { sb.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
            }.getOrNull()
            val (e, ok) = if (jpg == null) ("사진을 읽지 못했습니다. 다른 사진을 골라 주십시오." to null) else Hoewon.jeungOllim(this, jpg)
            main.post { jeungMal = e ?: ok ?: ""; malhagi(jeungMal); draw() }
        }
    }
    private var jeungMal = ""

    override fun onResume() { super.onResume(); Ollim.dorawatda(this) }   // 0.4.3 돌아오면 새 판 살피기(1시간에 한 번까지)
    override fun onNewIntent(i: android.content.Intent) { super.onNewIntent(i); setIntent(i); Ollim.intentBoda(this, i) }   // 0.4.3 새 판 알림을 두드렸을 때
    override fun onStop() { LibGirok.jeojang(); LibGirok.bonaegi(); super.onStop() }   // 0.6.3 이용 기록 — 앱이 뒤로 갈 때 폰에 저장하고 보냄
    override fun onDestroy() { Dokseo.bakkwim = null; Dokseo.ieoBakkwim = null; Ollim.byeonhwa = null; Ollim.malhagi = null; super.onDestroy() }

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
        listOf(0 to "도서관", 3 to "엔터테인먼트", 1 to "내 서재", 2 to "설정·도움말").forEach { (k, n) ->   // 0.7.0 엔터테인먼트는 둘째 자리(번호 3)
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
        if (!Hoewon.bogangDoem(this) && !bgNajung) { tabbar.visibility = View.GONE; bogangHwamyeon(); return }   // 0.8.2 등록한 분께 켤 때 한 번 여쭘
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
            is Hm.Seoga -> paged(h.t) { o -> Api.seoga(h.mun, h.k, "all", o) }
            is Hm.Pureo -> pureo(h.g)
            is Hm.SokChatgi -> sokChatgi(h)
            is Hm.Sajeon -> sajeon(h)
            is Hm.Jungyo -> jungyoMoeum()
            is Hm.Nanmal -> nanmalJang()   // 0.5.0 보일 책 거르기를 뺌
            Hm.EntHome -> entHome()
            is Hm.Ent -> paged(h.t, ent = true) { o -> Api.entList(h.j, o) }
            is Hm.EntFind -> paged("「${h.s}」 찾은 작품", ent = true) { o -> Api.entFind(h.s, o) }
            Hm.Gesipan -> gesipan()
            is Hm.GsMok -> gsMok(h)
            is Hm.GsBogi -> gsBogi(h)
            is Hm.GsSseugi -> gsSseugi(h)
        }
    }

    /** 0.8.2 회원 정보 여쭙기 — 한 번에 한 가지씩(장애 유형 → 지역 → 연령대 → 약관), 고르면 다음으로 */
    private fun bogangHwamyeon() {
        val t = geul("회원 정보 여쭙기", jemok = true)
        val (je, mok, gan) = when (bgDangye) {
            0 -> Triple("1. 장애 유형을 골라 주십시오", Hoewon.JANGAE, bgJ)
            1 -> Triple("2. 사시는 지역을 골라 주십시오", Hoewon.JIYEOK, bgR)
            2 -> Triple("3. 연령대를 골라 주십시오", Hoewon.NAI, bgN)
            else -> Triple("4. 이용 약관", emptyList<String>(), "")
        }
        if (bgDangye == 0) geul("도서관 실적을 내려고 세 가지를 여쭙니다. 이름 없이 숫자로만 셉니다. 한 번만 고르시면 다시 묻지 않습니다. 밝히고 싶지 않으면 「밝히지 않음」을 고르십시오.", jakge = true)
        geul(je)
        for (x in mok) danchu((if (x == gan) "고름, " else "") + x) {
            when (bgDangye) { 0 -> bgJ = x; 1 -> bgR = x; else -> bgN = x }
            bgDangye += 1; malhagi(x + ", 골랐습니다."); scroll.scrollTo(0, 0); draw()
        }
        if (bgDangye >= 3) {
            geul(Hoewon.YAKGWAN)
            geul("고르신 것: 장애 유형 $bgJ, 지역 $bgR, 연령대 $bgN", jakge = true)
            val mal = TextView(this)
            danchu("약관에 동의하고 보내기", keun = true) {
                mal.text = "보내는 중입니다."
                pool.execute {
                    val e = Hoewon.bogang(this, bgJ, bgR, bgN)
                    main.post { if (e == null) { malhagi("고맙습니다. 도서관으로 갑니다."); draw() } else { mal.text = e; cheotJul(mal) } }
                }
            }
            body.addView(mal, lp(8)); mal.setTextColor(Saek.geulja(this))
        }
        if (bgDangye > 0) danchu("앞으로, 다시 고르기") { bgDangye -= 1; draw() }
        danchu("다음에 하기") { bgNajung = true; malhagi("다음에 여쭙겠습니다."); draw() }
        cheotJul(t)
    }

    private fun deungrok() {
        geul("AI점자도서관", jemok = true)
        geul("처음 쓰실 때 한 번만 회원 등록을 합니다. 이름과 휴대전화 번호를 적고 등록을 눌러 주십시오. 앱이 잘 듣는지 살피려고 앱 판과 막힌 일(소리를 오래 기다림 등)을 협회 서버에 남깁니다. 이름, 전화번호, 듣는 책은 남기지 않습니다.")
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
        if (cheot == null) cheot = chaj   // 0.7.1 이어 들을 책이 없으면 찾기 칸이 첫 줄(길눈처럼, 이사장님 지시)
        // 0.5.0 고른 문의 서가 목록을 바로(설정의 첫 화면 목록), 다른 두 문은 그 아래 한 줄씩
        val cm = cheotMun()
        geul(MUN.first { it.first == cm }.second, jemok = true)
        val munMal = geul("가져오는 중입니다.", jakge = true)
        val munJari = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(munJari, lp(0))
        for ((k, t) in MUN) if (k != cm) danchu(if (k == "jujae") "주제별로 찾기, 십진분류" else "${t}로 찾기") { go(Hm.Mun(k, t)) }
        danchu("게시판, 공지와 묻고 답하기, 후기, 제작 요청, 불편 신고") { go(Hm.Gesipan) }   // 0.8.4
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
                            setOnClickListener { go(gagi(cm, k, tt)) }
                        }
                        munJari.addView(b, lp(6))
                    }
                }
            }
        }
        cheotJul(cheot)
    }


    // ── 0.8.4 게시판 ──
    private fun gesipan() {
        geul("게시판", jemok = true)
        val mal = geul("가져오는 중입니다.", jakge = true)
        pool.execute {
            val r = runCatching { GsApi.pan() }
            main.post {
                if (cur() != Hm.Gesipan) return@post
                r.onFailure { mal.text = "도서관에 닿지 못했습니다. 인터넷을 확인해 주십시오." }.onSuccess { (pan, _) ->
                    body.removeView(mal); var first: View? = null
                    for (p in pan) { val b = danchu(p.t + ", 글 ${p.su}개" + (if (p.sae > 0) ", 이번 주 새 글 ${p.sae}개" else "")) { go(Hm.GsMok(p.b, p.t, p.sseugi)) }; if (first == null) first = b }
                    cheotJul(first)
                }
            }
        }
    }
    private fun gsMok(h: Hm.GsMok) {
        val jm = geul(h.t, jemok = true)
        if (h.w) danchu(if (h.b == "jejak") "제작 요청하기" else "글쓰기", keun = true) { go(Hm.GsSseugi(h.b, h.t)) }
        val mal = geul("가져오는 중입니다.", jakge = true)
        val o = listO
        pool.execute {
            val r = runCatching { GsApi.mok(h.b, o) }
            main.post {
                if (cur() != h) return@post
                r.onFailure { mal.text = "도서관에 닿지 못했습니다. 인터넷을 확인해 주십시오." }.onSuccess { (modu, mok, makhim) ->
                    if (makhim.isNotEmpty()) { mal.text = makhim; cheotJul(jm); return@post }
                    jm.text = "${h.t}, 글 ${modu}개"
                    if (mok.isEmpty()) { mal.text = "아직 글이 없습니다." + if (h.w) " 위의 ${if (h.b == "jejak") "제작 요청하기" else "글쓰기"}로 첫 글을 올리실 수 있습니다." else ""; cheotJul(jm); return@post }
                    body.removeView(mal); var first: View? = null
                    for (g in mok) { val b = danchu(g.julMal) { go(Hm.GsBogi(h.b, h.t, g.id)) }; if (first == null) first = b }
                    val dam = o + Api.PER < modu
                    if (dam) danchu("다음 ${Api.PER}개") { listO = o + Api.PER; draw() }
                    if (o > 0) danchu("앞 ${Api.PER}개") { listO = (o - Api.PER).coerceAtLeast(0); draw() }
                    neomgigi(o > 0, dam, { listO = o + Api.PER; draw() }, { listO = (o - Api.PER).coerceAtLeast(0); draw() })
                    cheotJul(first)
                }
            }
        }
    }
    private fun gsBogi(h: Hm.GsBogi) {
        val jm = geul("글 가져오는 중", jemok = true)
        pool.execute {
            val r = runCatching { GsApi.bogi(h.b, h.id) }
            main.post {
                if (cur() != h) return@post
                r.onFailure { jm.text = "도서관에 닿지 못했습니다." }.onSuccess { (g, unyeong, makhim) ->
                    if (g == null) { jm.text = makhim; cheotJul(jm); return@post }
                    jm.text = "${g.beon}번. ${g.jemok}"
                    geul(g.ireum + ", " + g.nal + (if (g.bimil) ", 비밀글" else "") + (if (g.dangyeT.isNotEmpty()) ", 지금 단계 ${g.dangyeT}" else ""), jakge = true)
                    geul(g.naeyong)
                    geul("운영자 답 ${g.dap.size}개", jemok = true)
                    if (g.dap.isEmpty()) geul("아직 답이 없습니다.")
                    for (d in g.dap) geul(d.nal + ". " + d.naeyong)
                    if (unyeong) {
                        val e = EditText(this).apply { hint = "답 내용"; setTextColor(Saek.geulja(this@LibActivity)); minLines = 3 }
                        body.addView(e, lp(10))
                        danchu("답 올리기") { val t = e.text.toString(); pool.execute { val (ok, m) = GsApi.bonae(mapOf("a" to "dap", "b" to h.b, "id" to h.id, "naeyong" to t)); main.post { scroll.announceForAccessibility(m); if (ok) draw() } } }
                        if (h.b == "jejak") for ((k, t) in listOf("jeopsu" to "접수", "jejakjung" to "제작 중", "wallyo" to "완료", "bulga" to "제작할 수 없음"))
                            danchu((if (g.dangye == k) "지금 단계, " else "단계를 ") + t + (if (g.dangye == k) "" else "(으)로")) { pool.execute { val (_, m) = GsApi.bonae(mapOf("a" to "dangye", "b" to h.b, "id" to h.id, "dangye" to k)); main.post { scroll.announceForAccessibility(m); draw() } } }
                    }
                    if (g.nae || unyeong) danchu("이 글 지우기") {
                        android.app.AlertDialog.Builder(this).setTitle("이 글을 지울까요?")
                            .setPositiveButton("지우기") { _, _ -> pool.execute { val (ok, m) = GsApi.bonae(mapOf("a" to "jiugi", "b" to h.b, "id" to h.id)); main.post { scroll.announceForAccessibility(m); if (ok) onBackPressed() } } }
                            .setNegativeButton("그만두기", null).show()
                    }
                    cheotJul(jm)
                }
            }
        }
    }
    private fun gsSseugi(h: Hm.GsSseugi) {
        val sp = getSharedPreferences("gesipan", MODE_PRIVATE); val ck = "cho_" + h.b
        val jm = geul(h.t + if (h.b == "jejak") " — 만들어 주셨으면 하는 책" else " 글쓰기", jemok = true)
        if (h.b == "jejak") geul("책 이름과 지은이, 펴낸 곳을 아시는 대로 적어 주십시오. 한 달에 다섯 권까지 청하실 수 있고, 접수, 제작 중, 완료 단계를 목록에서 들으실 수 있습니다.", jakge = true)
        val chogi = runCatching { JSONObject(sp.getString(ck, "{}") ?: "{}") }.getOrNull()
        val je = EditText(this).apply { hint = "제목"; isSingleLine = true; setText(chogi?.optString("j") ?: ""); setTextColor(Saek.geulja(this@LibActivity)) }
        body.addView(je, lp(10))
        val na = EditText(this).apply { hint = "내용"; minLines = 5; setText(chogi?.optString("n") ?: ""); setTextColor(Saek.geulja(this@LibActivity)) }
        body.addView(na, lp(6))
        val nam = object : android.text.TextWatcher {   // 쓰던 글은 폰에 저절로 남김
            override fun afterTextChanged(x: android.text.Editable?) { sp.edit().putString(ck, JSONObject().put("j", je.text.toString()).put("n", na.text.toString()).toString()).apply() }
            override fun beforeTextChanged(x: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(x: CharSequence?, a: Int, b: Int, c: Int) {}
        }
        je.addTextChangedListener(nam); na.addTextChangedListener(nam)
        var bimil = false; var gojeong = false
        if (h.b == "mutdap") { lateinit var bb: Button; bb = danchu("비밀글, 꺼짐") { bimil = !bimil; bb.text = if (bimil) "비밀글, 켜짐 — 나와 운영자만 봅니다" else "비밀글, 꺼짐" } }
        if (h.b == "gongji") { lateinit var gb: Button; gb = danchu("맨 위에 고정, 꺼짐") { gojeong = !gojeong; gb.text = if (gojeong) "맨 위에 고정, 켜짐" else "맨 위에 고정, 꺼짐" } }
        danchu("올리기", keun = true) {
            val q = mapOf("a" to "sseugi", "b" to h.b, "jemok" to je.text.toString(), "naeyong" to na.text.toString(), "bimil" to if (bimil) "1" else "0", "gojeong" to if (gojeong) "1" else "0")
            pool.execute { val (ok, m) = GsApi.bonae(q); main.post { scroll.announceForAccessibility(m); if (ok) { sp.edit().remove(ck).apply(); onBackPressed() } } }
        }
        geul("쓰던 글은 이 폰에 저절로 남아, 화면을 닫아도 다시 이어 쓰실 수 있습니다.", jakge = true)
        cheotJul(jm)
    }

    // 0.7.0 엔터테인먼트 첫 화면 — 이름 줄, 찾기 칸, 이어 듣기, 갈래 목록, 더 보기
    private var entDeo = false
    private fun entHome() {
        meori()
        val chaj = EditText(this).apply {
            hint = "찾을 작품 이름"; inputType = InputType.TYPE_CLASS_TEXT; imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = kadeuBg(); setPadding(dp(14), dp(12), dp(14), dp(12)); setTextColor(Saek.geulja(this@LibActivity))
        }
        val chajgi = { val q = chaj.text.toString().trim(); if (q.isNotEmpty()) go(Hm.EntFind(q)) }
        chaj.setOnEditorActionListener { _, a, _ -> if (a == EditorInfo.IME_ACTION_SEARCH) { chajgi(); true } else false }
        body.addView(chaj, lp(10))
        danchu("찾기") { chajgi() }
        var cheot: View? = null
        Store.reading.firstOrNull { Api.isEnt(it.i) }?.let { l ->
            cheot = chaekJul(l, "이어 듣기, ${l.t}, ${l.wichiMal}부터", "이어 듣기 · ${l.wichiMal}부터", { go(Hm.Reader(l.i, l.t, l.kind)) }, false)
        }
        if (cheot == null) cheot = chaj   // 0.7.1
        geul("갈래", jemok = true)   // 0.7.2 여덟 갈래가 다 보임
        val mal = geul("가져오는 중입니다.", jakge = true)
        val jari = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(jari, lp(0))
        danchu(if (entDeo) "더 보기 접기" else "더 보기") { entDeo = !entDeo; dasi("더 보기") }
        if (entDeo) {
            geul("엔터테인먼트는 협회가 모아 온 영화, 드라마, 다큐 같은 자료를 모은 곳입니다. 영화, 드라마, 다큐·시사·교양, 애니메이션, 라디오·오디오 드라마, 음악, 화면해설 작품, 교육 여덟 갈래가 있습니다. 작품을 한 번 두드리면 바로 틀고, 영상은 화면도 함께 나오며, 듣던 자리를 기억합니다.")
            geul("AI점자도서관 ${PAN}판, 빌드 $BILDEU", jakge = true)
        }
        pool.execute {
            val r = runCatching { Api.entGal() }
            main.post {
                if (cur() != Hm.EntHome) return@post
                r.onFailure { mal.text = "엔터테인먼트에 닿지 못했습니다. 인터넷을 확인한 뒤 설정의 새로고침을 눌러 주십시오." }.onSuccess { gal ->
                    if (gal.isEmpty()) mal.text = "아직 열린 갈래가 없습니다." else body.removeView(mal)
                    for (g in gal) {
                        val b = Button(this).apply {
                            text = "${g.dan}, ${g.n}편"; isAllCaps = false; gravity = Gravity.START or Gravity.CENTER_VERTICAL
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); setTextColor(Saek.geulja(this@LibActivity)); background = kadeuBg()
                            setPadding(dp(16), dp(12), dp(16), dp(12)); minHeight = dp(52)
                            setOnClickListener { go(Hm.Ent(g.g, g.dan)) }
                        }
                        jari.addView(b, lp(6))
                    }
                }
            }
        }
        cheotJul(cheot)
    }

    private fun paged(jemok: String, ent: Boolean = false, loader: (Int) -> ListResp) {
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
                    val b = danchu(it.t) { if (ent) { if (it.i != null) go(Hm.Reader(it.i, it.t, it.k ?: "sori")) else if (it.j != null) go(Hm.Ent(it.j, it.t)) } else if (it.i != null) go(Hm.Reader(it.i, it.t, "")) else if (it.j != null) go(Hm.Jakbon(it.j, it.t)) }   // 0.7.0 엔터테인먼트는 폴더면 그 안으로, 작품이면 소리로 바로   // 0.5.1 한 번 두드리면 독서기가 열려 바로 읽음
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
    private val MUN = listOf("jujae" to "주제별", "jangreu" to "장르별", "tema" to "테마별", "oeguk" to "외국 도서")   // 0.5.2 외국 도서
    // 0.5.2 — 외국 도서 문에서 언어를 고르면 그 언어의 장르와 주제(문 "oe:언어")로, 그 밖은 서가 책 목록으로
    private fun gagi(mun: String, k: String, t: String): Hm = if (mun == "oeguk") Hm.Mun("oe:$k", t) else Hm.Seoga(mun, k, t)

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
    private var readerJam: Button? = null   // 0.8.3
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
        readerJam = danchu("장애 확인하기") { jeungGoreugi() }.apply { visibility = View.GONE }   // 0.8.3 잠긴 책이면 보임
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
        if (Api.isEnt(h.i)) {   // 0.8.0 (261018-L19) 이어 듣기·이어 보기
            danchu(if (h.k == "yeongsang") "다음 회" else "다음 곡") { Dokseo.ieoGa(1) }
            danchu(if (h.k == "yeongsang") "앞 회" else "앞 곡") { Dokseo.ieoGa(-1) }
            danchu("듣는 방식, 지금 " + Store.ieoIreum()) {
                val k = Store.IEO.indexOfFirst { it.first == Store.ieo }
                Store.ieo = Store.IEO[(k + 1) % Store.IEO.size].first
                malhagi(Store.ieoIreum() + "로 듣습니다."); Dokseo.ieoMiri(); dasi("듣는 방식")
            }
        }
        readerMun = if (h.k == "geul") geul("") else null
        danchu(if (readerDeo) "더 보기 접기" else "더 보기, 빠르기·목소리·책갈피·처음부터·책 정보") { readerDeo = !readerDeo; dasi("더 보기") }
        if (readerDeo) {
            if (h.k == "geul") {   // 0.6.0 곱씹어 읽기와 사전
                danchu("이 문단 다시 듣기") { Dokseo.play(Dokseo.pos) }
                danchu("천천히 다시 듣기") { Store.rateIndex = 0; Dokseo.setRate(); malhagi("아주 느리게 다시 읽습니다. 빠르기는 더 빠르게로 되돌릴 수 있습니다."); Dokseo.play(Dokseo.pos) }
                danchu("풀어 읽기, 이 문단을 글자 단위로 짚기") { go(Hm.Pureo(Dokseo.text())) }
                danchu("사전, 이 문단 낱말 뜻 찾기") { sajeonChajeun = ""; sajeonGyeol = emptyList(); gsO = 0; go(Hm.Sajeon(h.t, Dokseo.text())) }
                danchu("중요 표시하기") { jungyoPyosi(h) }
                if (Dokseo.hjgal) danchu("한자음 읽기 바꾸기, 지금은 " + Store.hanjaeumIreum()) { Dokseo.hanjaeumBakkugi { main.post { malhagi("한자음 읽기, " + Store.hanjaeumIreum()); dasi("한자음 읽기 바꾸기, 지금은 " + Store.hanjaeumIreum()) } } }   // 0.6.1 (261012) 한자음 읽기
                danchu("중요 표시에 메모 붙이기") { memoChang(h) }
                danchu("책 속에서 찾기") { sokHits = emptyList(); sokMal = ""; gsO = 0; go(Hm.SokChatgi(h.i, h.t)) }
            }
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
            if (!Api.isEnt(h.i)) danchu("책 정보, 책 소개·지은이·내려받기") { go(Hm.Book(h.i)) }   // 0.5.1 책 정보는 여기 안에(0.7.0 엔터테인먼트 작품은 책 정보가 없음)
        }
        if (h.k == "geul") readerDan?.let { b ->   // 0.6.0 읽기 단추에서 톡백 동작으로 바로
            ViewCompat.addAccessibilityAction(b, "이 문단 다시 듣기") { _, _ -> Dokseo.play(Dokseo.pos); true }
            ViewCompat.addAccessibilityAction(b, "사전") { _, _ -> sajeonChajeun = ""; sajeonGyeol = emptyList(); gsO = 0; go(Hm.Sajeon(h.t, Dokseo.text())); true }
            ViewCompat.addAccessibilityAction(b, "중요 표시하기") { _, _ -> jungyoPyosi(h); true }
        }
        drawReaderState()
        cheotJul(readerDan)
    }
// ── 0.6.0 곱씹어 읽기와 사전(도서관 창 클, 이사장님 승인 2026-10-12 「1」) ──
    private var gsO = 0
    private var sajeonChajeun = ""
    private var sajeonGyeol: List<Gomssip.Item> = emptyList()
    private var sajeonMal = ""
    private var sokHits: List<Pair<Int, String>> = emptyList()
    private var sokMal = ""
    private var sokQ = ""
    private fun ipryeok(hint: String, chogi: String, onGo: (String) -> Unit): EditText {
        val e = EditText(this).apply {
            this.hint = hint; setText(chogi); isSingleLine = true; imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextColor(Saek.geulja(this@LibActivity))
            setOnEditorActionListener { v, _, _ -> onGo(v.text.toString()); true }
        }
        body.addView(e, lp(6)); return e
    }
    private fun jungyoPyosi(h: Hm.Reader) {
        val t = Dokseo.text()
        if (t.isEmpty()) { malhagi("글을 가져온 뒤에 중요 표시를 할 수 있습니다."); return }
        val ok = Gomssip.pyosi(h.i, h.t, Dokseo.pos, t)
        malhagi(if (ok) "${Dokseo.pos + 1}번째 문단에 중요 표시를 했습니다." else "이미 중요 표시가 된 문단입니다.")
    }
    private fun memoChang(h: Hm.Reader) {
        val pos = Dokseo.pos
        val e = EditText(this).apply { hint = "메모, 자판의 마이크로 말로 적을 수 있습니다"; setText(Gomssip.memoOf(h.i, pos)) }
        android.app.AlertDialog.Builder(this).setTitle("중요 표시 메모").setView(e)
            .setPositiveButton("붙이기") { _, _ ->
                if (Gomssip.jungyo.none { it.i == h.i && it.pos == pos }) Gomssip.pyosi(h.i, h.t, pos, Dokseo.text(pos))
                Gomssip.memo(h.i, pos, e.text.toString()); malhagi("메모를 붙였습니다.")
            }
            .setNegativeButton("그만", null).show()
    }
    /** 책의 그 문단으로 가서 바로 읽기 */
    private fun garyeo(i: Int, t: String, o: Int) {
        if (cur() is Hm.SokChatgi) stacks[tab].removeAt(stacks[tab].size - 1)
        val top = cur()
        if (top is Hm.Reader && top.i == i) { Dokseo.open(this, i, t, "geul", o.toDouble()); Dokseo.play(o); draw() }
        else { Store.remember(i, t, "geul", o.toDouble(), Store.rec(i)?.modu ?: 0); Dokseo.open(this, i, t, "geul", o.toDouble()); go(Hm.Reader(i, t, "geul")) }
    }
    private fun pureo(g: String) {
        geul("풀어 읽기", jemok = true)
        geul("톡백의 읽기 단위를 글자나 단어로 놓고 넘기면 한 글자씩, 한 낱말씩 짚어 읽을 수 있습니다.", jakge = true)
        val t = geul(g.ifEmpty { "읽을 글이 없습니다." }).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f); setTextIsSelectable(true) }
        cheotJul(t)
    }
    private fun sokChatgi(h: Hm.SokChatgi) {
        geul("책 속에서 찾기", jemok = true)
        val chatgi = { s: String ->
            val q = s.trim()
            if (q.isNotEmpty()) {
                sokQ = q; sokMal = "찾는 중입니다."; sokHits = emptyList(); gsO = 0; draw()
                pool.execute {
                    val r = runCatching { Gomssip.sok(h.i, q) }
                    main.post {
                        if (cur() != h) return@post
                        r.onSuccess { (n, hs, m) -> sokHits = hs; sokMal = if (hs.isEmpty()) m.ifEmpty { "찾는 말이 이 책에 없습니다." } else "${n}곳을 찾았습니다." }
                            .onFailure { sokMal = "찾지 못했습니다. 인터넷을 확인해 주십시오." }
                        draw()
                    }
                }
            }
        }
        val e = ipryeok("찾을 낱말이나 이름", sokQ) { chatgi(it) }
        danchu("찾기") { chatgi(e.text.toString()) }
        var first: View? = null
        if (sokMal.isNotEmpty()) first = geul(sokMal)
        if (sokHits.isNotEmpty()) {
            if (gsO >= sokHits.size) gsO = 0
            var cheotDan: View? = null
            for ((o, tt) in sokHits.drop(gsO).take(Api.PER)) { val b = danchu("${o + 1}번째 문단, $tt") { garyeo(h.i, h.t, o) }; if (cheotDan == null) cheotDan = b }
            if (gsO + Api.PER < sokHits.size) danchu("더 보기") { gsO += Api.PER; draw() }
            if (gsO > 0) danchu("이전 보기") { gsO = (gsO - Api.PER).coerceAtLeast(0); draw() }
            if (gsO > 0 && cheotDan != null) first = cheotDan   // 넘긴 뒤에는 커서를 새 목록 첫 줄로
        }
        cheotJul(first ?: e)
    }
    private fun sajeon(h: Hm.Sajeon) {
        geul("사전", jemok = true)
        val chatgi = { w0: String ->
            val w = w0.trim()
            if (w.isNotEmpty()) {
                sajeonChajeun = w; sajeonGyeol = emptyList(); sajeonMal = "찾는 중입니다."; draw()
                pool.execute {
                    val r = runCatching { Gomssip.chatgi(w) }
                    main.post {
                        if (cur() != h) return@post
                        r.onSuccess { (its, m) -> sajeonGyeol = its; sajeonMal = m; if (its.isNotEmpty()) Gomssip.damgi(w, its[0], h.m, h.c) }   // 내 낱말장에 저절로
                            .onFailure { sajeonMal = "사전에 닿지 못했습니다. 인터넷을 확인해 주십시오." }
                        draw()
                    }
                }
            }
        }
        if (sajeonChajeun.isNotEmpty()) {
            danchu("다른 낱말 고르기") { sajeonChajeun = ""; sajeonGyeol = emptyList(); sajeonMal = ""; draw() }
            geul("「$sajeonChajeun」 뜻", jakge = true)
            var first: View? = null
            if (sajeonMal.isNotEmpty()) first = geul(sajeonMal)
            for (x in sajeonGyeol) { val v = geul("${x.t}. ${x.ddeut} 출처 ${x.chulcheo}."); if (first == null) first = v }
            cheotJul(first)
            return
        }
        val e = ipryeok("찾을 낱말", "") { chatgi(it) }
        danchu("사전에서 찾기") { chatgi(e.text.toString()) }
        val mal = Gomssip.nanmalDeul(h.m)
        var first: View? = null
        if (mal.isNotEmpty()) {
            geul("지금 문단의 낱말 ${mal.size}개", jakge = true)
            if (gsO >= mal.size) gsO = 0
            for (w in mal.drop(gsO).take(Api.PER)) { val b = danchu(w) { chatgi(w) }; if (first == null) first = b }
            if (gsO + Api.PER < mal.size) danchu("더 보기") { gsO += Api.PER; draw() }
            if (gsO > 0) danchu("이전 보기") { gsO = (gsO - Api.PER).coerceAtLeast(0); draw() }
        }
        cheotJul(first ?: e)
    }
    private fun jungyoMoeum() {
        geul("중요 표시 모음", jemok = true)
        val L = Gomssip.jungyoSun()
        var first: View? = null
        if (L.isEmpty()) first = geul("중요 표시가 없습니다. 책을 읽다가 더 보기의 중요 표시하기를 누르면 여기에 모입니다.")
        else {
            if (gsO >= L.size) gsO = 0
            geul("중요 표시 ${L.size}개 가운데 ${gsO + 1}번부터 ${minOf(gsO + Api.PER, L.size)}번", jakge = true)
            for (j in L.drop(gsO).take(Api.PER)) {
                val b = danchu("${j.t}, ${j.pos + 1}번째 문단. " + (if (j.memo.isNotEmpty()) "메모 ${j.memo}. " else "") + j.geul) { garyeo(j.i, j.t, j.pos) }
                ViewCompat.addAccessibilityAction(b, "중요 표시 지우기") { _, _ -> Gomssip.jiugi(j); malhagi("지웠습니다."); draw(); true }
                b.setOnLongClickListener { Gomssip.jiugi(j); malhagi("지웠습니다."); draw(); true }
                if (first == null) first = b
            }
            if (gsO + Api.PER < L.size) danchu("더 보기") { gsO += Api.PER; draw() }
            if (gsO > 0) danchu("이전 보기") { gsO = (gsO - Api.PER).coerceAtLeast(0); draw() }
        }
        cheotJul(first)
    }
    private fun nanmalJang() {
        geul("내 낱말장", jemok = true)
        val L = Gomssip.nanmalSun()
        var first: View? = null
        if (L.isEmpty()) first = geul("찾아본 낱말이 없습니다. 책을 읽다가 사전에서 낱말을 찾으면 여기에 저절로 모입니다.")
        else {
            if (gsO >= L.size) gsO = 0
            geul("낱말 ${L.size}개 가운데 ${gsO + 1}번부터 ${minOf(gsO + Api.PER, L.size)}번", jakge = true)
            for (n in L.drop(gsO).take(Api.PER)) {
                val v = geul("${n.w}. ${n.ddeut} 출처 ${n.chulcheo}. 책 ${n.chaek}. 문장 ${n.munjang}")
                v.isFocusable = true
                ViewCompat.addAccessibilityAction(v, "낱말장에서 지우기") { _, _ -> Gomssip.jiugi(n); malhagi("지웠습니다."); draw(); true }
                v.setOnLongClickListener { Gomssip.jiugi(n); malhagi("지웠습니다."); draw(); true }
                if (first == null) first = v
            }
            if (gsO + Api.PER < L.size) danchu("더 보기") { gsO += Api.PER; draw() }
            if (gsO > 0) danchu("이전 보기") { gsO = (gsO - Api.PER).coerceAtLeast(0); draw() }
        }
        cheotJul(first)
    }

    private fun drawReaderState() {
        val d = Dokseo
        readerDan?.text = if (d.playing) (if (d.waiting) "목소리를 받는 중입니다, 멈춤" else "멈춤") else "읽기"
        readerJam?.visibility = if (d.jamgeum != null) View.VISIBLE else View.GONE   // 0.8.3
        if (d.jamgeum != null) { readerJul?.text = d.jamgeum; readerDan?.text = "읽기"; return }
        readerJul?.text = if (d.kind == "geul") (if (d.modu > 0) "${d.modu}문단 가운데 ${d.pos + 1}번째" else "글자를 가져오는 중입니다.") else ""
        readerMun?.text = d.text() + d.won().let { if (it.isEmpty()) "" else "\n원문: " + it }   // 0.6.1 음과 원문 함께 보기
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
                    for ((k, tt, n) in all[h.mun].orEmpty()) if (n > 0) { val b = danchu("$tt, ${n}권") { go(gagi(h.mun, k, tt)) }; if (first == null) first = b }
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
        if (ilk.isEmpty()) { val b = geul("읽던 책이 없습니다. 도서관에서 책을 찾아 읽기 시작하면 여기에 저절로 담깁니다."); if (first == null) first = b }   // 0.7.1
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
            danchu("중요 표시 모음, ${Gomssip.jungyoSun().size}개") { gsO = 0; go(Hm.Jungyo) }   // 0.6.0
            danchu("내 낱말장, ${Gomssip.nanmalSun().size}개") { gsO = 0; go(Hm.Nanmal) }
            if (daO >= da.size) daO = 0
            geul("다 읽은 책 ${da.size}권" + (if (da.isNotEmpty()) " 가운데 ${daO + 1}번부터 ${minOf(daO + Api.PER, da.size)}번" else ""), jakge = true)
            for (rc in da.drop(daO).take(Api.PER)) {
                val b = danchu("${rc.t}, 다 읽음") { if (Api.isEnt(rc.i)) go(Hm.Reader(rc.i, rc.t, rc.kind)) else go(Hm.Book(rc.i)) }
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
        danchu("곡이 넘어갈 때 제목 읽기: ${if (Store.gogmal) "켜짐" else "꺼짐"}") {   // 0.8.1 (261018-L20) 듣는 화면에서 설정으로 옮김(이사장님 승인 「3」)
            Store.gogmal = !Store.gogmal; malhagi(if (Store.gogmal) "곡이 넘어갈 때 제목을 읽습니다." else "곡이 넘어갈 때 제목을 읽지 않습니다."); dasi("곡이 넘어갈 때 제목 읽기")
        }
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
        danchu("새로고침") { Dokseo.stop(); for (k in 0..3) while (stacks[k].size > 1) stacks[k].removeAt(stacks[k].size - 1); tab = 0; draw(); malhagi("새로 불러왔습니다.") }
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
        geul("내 회원 등급", jemok = true)   // 0.8.3 (261019-L22) 회원 등급
        geul(Hoewon.deungT(this).ifEmpty { "회원 등급을 확인하는 중입니다" })
        if (Hoewon.ilban(this)) {
            geul("지금은 외국 도서(저작권이 끝난 책)만 들으실 수 있습니다. 장애 확인을 받으시면 모든 책과 엔터테인먼트 작품을 들으십니다. " + Hoewon.jeungIreum(Hoewon.jeung(this)) + ".")
            geul(Hoewon.JEUNG_MAL, jakge = true)
            danchu("장애 확인하기, 사진 골라 올리기") { jeungGoreugi() }
        }
        danchu("확인됐는지 다시 보기") { pool.execute { Hoewon.deungHwakin(this); main.post { jeungMal = "내 회원 등급, " + Hoewon.deungT(this) + ". " + Hoewon.jeungIreum(Hoewon.jeung(this)); malhagi(jeungMal); dasi("확인됐는지 다시 보기") } } }
        if (jeungMal.isNotEmpty()) geul(jeungMal, jakge = true)
        geul("회원: ${Hoewon.ireum(this)}", jakge = true)   // 0.4.4 아이폰과 같게
        geul("AI점자도서관 안드로이드 ${PAN}판, 빌드 $BILDEU", jakge = true)
        geul("주관 사단법인 한국시각장애인현장영상해설협회. 전화 02-363-4455, 메일 ada015@naver.com", jakge = true)
        cheotJul(t)
    }

    // 0.4.4 도움말 — 아이폰 도움말과 같은 가짓수로 맞춤(안드로이드 말로)
    private val DOUM = listOf(
        "탭 고르기" to "아래 탭(도서관, 엔터테인먼트, 내 서재, 설정·도움말)을 고르면 커서가 그 화면 목록의 첫 줄에 놓입니다. 도서관과 엔터테인먼트는 이어 듣기, 이어 들을 것이 없으면 찾기 칸입니다. 내 서재는 읽던 책 첫 권입니다. 지금 탭을 다시 누르면 그 탭 첫 화면으로 돌아가 첫 줄에 놓입니다.",   // 0.7.1
        "이어 듣기" to "읽던 책이 있으면 도서관 첫 화면 찾기 칸 아래에 이어 듣기가 나옵니다. 누르면 독서기가 열리면서 읽던 자리부터 바로 읽고, 커서는 멈춤 단추에 놓입니다.",
        "책 고르면 바로 읽기" to "목록에서 책 이름을 한 번 두드리면 독서기가 열리면서 바로 읽습니다. 읽던 책이면 읽던 자리부터 읽습니다. 소리책과 동영상도 바로 틉니다. 커서는 멈춤 단추에 놓이니 잘못 고르셨으면 바로 멈추십시오. 책 소개, 지은이, 출판사, 내려받기는 독서기 더 보기 안의 책 정보에 있습니다.",
        "책 찾기" to "도서관 첫 화면 맨 위 이름 줄 바로 아래의 찾을 책 이름 칸에 낱말을 적고 찾기를 누릅니다. 찾은 결과만 나오고 커서가 첫 줄에 놓입니다. 다시 찾을 때는 뒤로를 누릅니다.",
        "갈래" to "글자책, 소리책, 대본, 입체낭독, 인터넷소설, 점자책으로 나뉩니다. 대본에는 드라마, 영화, 연극·뮤지컬, 라디오 드라마 대본이 들어 있습니다. 인터넷소설은 한 줄에 작품 하나로 나오고, 누르면 권이 차례로 나옵니다.",
        "책 묶어 보기" to "목록에는 파일 이름이 아니라 책 제목과 권수가 한 줄로 나옵니다. 보기: 야인시대, 전 117회. 그 줄을 누르면 야인시대 1회, 야인시대 2회처럼 제목과 번호가 차례대로 나옵니다. 찾기를 해도 같은 책은 묶음 한 줄로 나옵니다.",
        "첫 화면 목록" to "도서관 첫 화면에는 찾기 칸과 이어 듣기 아래에 설정의 첫 화면 목록에서 고른 목록(주제별, 장르별, 테마별 가운데 하나)의 서가가 바로 나옵니다. 처음 값은 장르별이고, 누를 때마다 바뀝니다. 다른 두 목록은 그 아래 한 줄씩 있습니다.",
        "목록 넘기기" to "목록은 처음에 한 쪽 15줄입니다. 설정의 목록 줄 수를 누를 때마다 5, 10, 15, 20, 30줄로 바뀝니다. 목록 줄에서 톡백의 목록 넘기기 동작(앞으로, 뒤로)을 하면 다음 목록과 앞 목록이 나오고 커서는 그 목록 첫 줄에 놓입니다. 손짓이 안 될 때는 맨 아래 다음 목록을 누릅니다. 내 서재는 더 보기와 이전 보기로 넘깁니다.",
        "세 겹의 문" to "도서관 첫 화면에 주제별, 장르별, 테마별 세 문이 있습니다. 문을 누르면 서가와 책 수가, 서가를 누르면 책 목록이 나옵니다. 주제별은 도서관 십진분류, 장르별은 판타지·무협 같은 갈래, 테마별은 이달의 새 책 같은 모음입니다.",
        "이 문단 다시 듣기" to "책 읽는 화면 더 보기 안에 있습니다. 지금 읽던 문단을 처음부터 다시 읽습니다. 읽기 단추에서 톡백 동작으로 이 문단 다시 듣기를 골라도 됩니다. 천천히 다시 듣기는 빠르기를 아주 느리게로 바꾸어 다시 읽고, 더 빠르게로 되돌릴 수 있습니다.",
        "풀어 읽기" to "책 읽는 화면 더 보기 안에 있습니다. 지금 문단을 한 덩이 글로 띄웁니다. 톡백의 읽기 단위를 글자나 단어로 놓고 넘기면 한 글자씩, 한 낱말씩 짚어 읽을 수 있어 외국어 철자나 한자를 확인할 때 좋습니다.",
        "중요 표시와 메모" to "책 읽는 화면 더 보기 안의 중요 표시하기를 누르면 지금 문단에 중요 표시가 붙습니다. 중요 표시에 메모 붙이기로 짧은 메모를 적을 수 있고, 자판의 마이크를 누르면 말로 적을 수 있습니다. 읽기 단추에서 톡백 동작으로 중요 표시하기를 골라도 됩니다.",
        "중요 표시 모음" to "내 서재의 더 보기 안에 있습니다. 모든 책의 중요 표시가 최근 것부터 모이고, 누르면 그 문단으로 가서 바로 읽습니다. 지우려면 그 줄에서 톡백 동작으로 중요 표시 지우기를 고르거나 길게 누릅니다.",
        "책 속에서 찾기" to "책 읽는 화면 더 보기 안에 있습니다. 낱말이나 이름을 적고 찾기를 누르면 그 말이 나오는 문단이 목록으로 나오고, 누르면 그 문단부터 읽습니다.",
        "사전" to "책 읽는 화면 더 보기 안의 사전을 누르면 지금 문단의 낱말이 목록으로 나오고, 고르면 바로 뜻을 들려줍니다. 낱말을 적어 찾을 수도 있습니다. 우리말은 한국어기초사전과 표준국어대사전에서 찾고, 두 사전에 적게 나오는 새말, 방언, 옛말, 전문어는 우리말샘에서 보충합니다. 영어는 워드넷, 일본어는 JMdict, 중국어는 CC-CEDICT에서 찾고, 한자는 한 글자마다 음과 뜻을 알려 줍니다. 한문 낱말이나 구절은 한국고전종합DB에서 경서 번역과 고전 번역 문장을 함께 들려줍니다. 뜻 끝에는 출처를 붙입니다.",
        "내 낱말장" to "사전에서 찾아본 낱말이 그 문장, 책 이름과 함께 저절로 모입니다. 내 서재의 더 보기 안에 있습니다. 지우려면 그 줄에서 톡백 동작으로 낱말장에서 지우기를 고르거나 길게 누릅니다.",
        "외국 도서" to "외국어로 된 원서는 우리 책과 섞지 않고 외국 도서 문에 따로 모았습니다. 외국 도서를 누르면 영어, 중국어, 일본어, 프랑스어, 독일어 같은 언어와 책 수가 나오고, 언어를 누르면 소설, 시, 역사 같은 장르와 주제가 나옵니다. 영어 소설은 고전 명작, 모험, 추리와 스릴러처럼 한 겹 더 나뉩니다. 첫 화면의 외국 도서로 찾기를 누르거나, 설정의 첫 화면 목록에서 외국 도서를 고르면 첫 화면에 바로 언어 목록이 나옵니다.",
        "띄어 읽기 바로잡기" to "옛 글자책은 한 줄 길이를 정해 강제로 줄을 바꾼 것이 많아, 그렇습니다를 그 렇습니다처럼 낱말 가운데서 끊어 읽는 일이 있었습니다. 이제 도서관이 책마다 끊긴 줄을 알아보고 낱말을 바르게 이어, 문장이 끝나는 곳에서만 쉬며 읽어 드립니다. 읽던 자리와 책갈피는 책을 처음 열 때 새 자리로 맞춰 옮겨 드립니다. 내려받은 책은 받을 때의 글 그대로 읽습니다.",
        "한자음 읽기" to "중국 고전처럼 한자로 된 책은 우리 한자음으로 바꿔 읽어 드립니다. 학이시습지처럼 우리가 한문을 읽는 소리로 듣게 되고, 연보, 노인처럼 두음법칙도 지킵니다. 한문 구절은 한국고전종합DB의 경서 읽는 소리를 따라 불역열호, 불지위불지처럼 읽습니다. 중국 본토 글자(간체자)로 된 책도 먼저 옛 글자로 바꾼 뒤 읽습니다. 독서기 더 보기의 한자음 읽기 바꾸기를 누를 때마다 음만 듣기, 음과 원문 함께 보기, 꺼짐 차례로 바뀝니다. 음과 원문 함께 보기에서는 문단 아래에 원문 한자가 나와 점자나 화면으로 확인하실 수 있습니다. 일본 책은 일본어 그대로 읽습니다. 이 단추는 한자로 된 책을 열었을 때만 나옵니다.",
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
        "소리를 기다릴 때" to "처음 듣는 문단은 도서관 서버가 그 자리에서 목소리를 만듭니다. 여러 분이 함께 들으시면 조금 기다리실 수 있습니다. 10초가 넘으면 소리를 만들고 있다고, 45초가 넘으면 조금 더 걸린다고 한 번씩 알려 드리고, 4분까지 기다립니다. 한 번 만든 소리는 다음 분이 바로 들으십니다. 도서관은 한가할 때 새 책과 읽던 책 앞부분을 미리 만들어 둡니다.",
        "이용 기록" to "앱이 잘 듣는지 살피려고, 앱 판과 안드로이드 판, 책을 연 때, 소리를 오래 기다리거나 만들지 못한 일을 협회 서버에 남깁니다. 이름, 전화번호, 듣는 책 이름과 내용은 남기지 않습니다. 협회는 이 기록으로 막히는 곳을 먼저 찾아 고칩니다. 책을 열면 그 책 번호를, 듣는 동안에는 1분마다 들은 시간을, 끝까지 들으면 다 들었다는 것을 함께 남깁니다(이용 장부). 이 장부는 이름과 전화번호 대신 알아볼 수 없는 가명 번호로만 적고, 도서관 실적(이용자 수, 이용 시간, 많이 읽힌 책)을 낼 때 숫자로만 셉니다. 누가 무슨 책을 읽었는지는 운영하는 사람에게도 보이지 않습니다.",
        "엔터테인먼트" to "아래 탭의 둘째 칸입니다. 협회가 모아 온 영화, 드라마, 다큐 같은 자료를 모은 곳입니다. 영화, 드라마, 다큐·시사·교양, 애니메이션, 라디오·오디오 드라마, 음악, 화면해설 작품, 교육 여덟 갈래가 있습니다. 갈래를 누르면 작품이 나오고, 드라마는 작품을 누르면 회차가 차례로 나옵니다. 회차를 한 번 두드리면 바로 틉니다. 독서기와 같아서 앞으로 30초, 뒤로 30초, 재생 위치, 빠르기, 책갈피를 그대로 쓰고, 듣던 자리를 기억해 엔터테인먼트 첫 화면의 이어 듣기와 내 서재에 나옵니다. 작품 이름을 적어 찾을 수도 있습니다.",
        "음악·드라마 이어 듣기" to "엔터테인먼트에서 음악을 틀면 곡이 끝날 때 다음 곡이 저절로 나오고, 드라마는 다음 회로 이어집니다. 독서기 화면의 듣는 방식 단추를 누를 때마다 앨범·작품 끝까지(여러 장짜리 앨범은 다음 장까지), 갈래 전체 이어서, 섞어 듣기, 한 곡 반복, 한 곡만 차례로 바뀝니다. 고른 것은 기억해 둡니다. 다음 곡·앞 곡 단추(드라마는 다음 회·앞 회)로 넘기고, 이어폰 단추와 잠금 화면의 다음 곡 단추로도 넘깁니다. 넘어갈 때 곡 이름을 한 번 알려 드리며, 설정의 곡이 넘어갈 때 제목 읽기를 끄면 조용히 넘어갑니다.",   // 0.8.0
        "곡이 넘어갈 때 제목 읽기" to "설정·도움말 탭에 있습니다. 켜 두면 음악이 다음 곡으로 넘어갈 때(드라마는 다음 회로 넘어갈 때) 제목을 한 번 읽어 드립니다. 새 앨범으로 넘어가면 앨범 이름도 함께 읽습니다. 끄면 제목을 읽지 않고 다음 곡이 바로 이어집니다. 처음에는 켜져 있습니다. 듣는 화면을 간결하게 두려고 이 설정은 설정·도움말 탭에만 둡니다.",   // 0.8.1
        "영상 보기" to "엔터테인먼트의 영상 작품은 소리와 함께 화면이 나옵니다. 화면은 읽기 단추 위에 있고, 화면을 두드리면 폰이 주는 재생 단추와 크게 보기가 나옵니다. 앞으로 30초, 뒤로 30초, 재생 위치, 책갈피는 소리 작품과 똑같이 씁니다. 영상은 데이터를 많이 쓰니 와이파이에서 보시기를 권합니다.",   // 0.7.2
        "게시판" to "도서관 첫 화면 아래쪽의 게시판 단추를 누르면 공지, 묻고 답하기, 후기, 제작 요청, 불편 신고 다섯 가지가 나옵니다. 목록은 설정의 목록 줄 수만큼(처음 15줄) 나오고 최근 글이 맨 위입니다. 다음 목록과 앞 목록 단추로 넘깁니다.",   // 0.8.4
        "게시판 글쓰기" to "게시판에 들어가 글쓰기를 누르고 제목과 내용을 적은 뒤 올리기를 누릅니다. 공지는 운영자만 씁니다. 쓰던 글은 폰에 저절로 남아 화면을 닫아도 다시 이어 쓸 수 있습니다. 내가 쓴 글은 열어서 이 글 지우기를 누르고 지우기를 한 번 더 고르면 지워집니다.",   // 0.8.4
        "비밀글" to "묻고 답하기에서 글을 쓸 때 비밀글 단추를 눌러 켜면 나와 운영자만 봅니다.",   // 0.8.4
        "제작 요청" to "읽고 싶은 책을 도서관에 만들어 달라고 청하는 곳입니다. 인증 회원부터 한 달에 다섯 권까지 청하실 수 있고, 목록에서 접수, 제작 중, 완료, 제작할 수 없음 단계를 들으실 수 있습니다.",   // 0.8.4
        "회원 등급" to "회원은 일반 회원, 인증 회원, 봉사 회원, 운영자, 최고관리자로 나뉩니다. 처음 등록하면 일반 회원이고, 일반 회원은 외국 도서(저작권이 끝난 책)만 들으실 수 있습니다. 설정·도움말 탭의 내 회원 등급에서 장애 확인하기를 누르고, 장애인 복지카드나 장애인증명서 사진을 골라 올리시면 협회가 확인해 인증 회원으로 바꿔 드립니다. 그러면 모든 책과 엔터테인먼트 작품을 들으실 수 있습니다. 잠긴 책을 열면 인증 회원만 들을 수 있다고 알려 드리고, 그 자리에 장애 확인하기 단추가 나옵니다. 확인됐는지 다시 보기를 누르면 지금 등급을 다시 알려 드립니다.",   // 0.8.3
        "회원 정보 여쭙기" to "처음 등록한 뒤, 이미 등록하신 분은 앱을 켤 때 한 번, 장애 유형, 사시는 지역, 연령대 세 가지를 한 가지씩 여쭙고 이용 약관에 동의를 받습니다. 도서관 실적을 숫자로만 내는 데 씁니다. 밝히고 싶지 않은 것은 「밝히지 않음」을 고르시면 됩니다. 「다음에 하기」를 누르면 앱을 다시 켤 때까지 묻지 않습니다.",   // 0.8.2
        "소리가 멈추면" to "음악이나 소리책이 듣는 중에 8초 넘게 멈추면 앱이 스스로 그 자리부터 다시 이어 틉니다.",   // 0.8.2
        "회원 등록" to "처음 켤 때 이름과 휴대전화 번호로 한 번만 등록합니다. 등록하면 이 폰만의 회원 열쇠가 담겨 도서관과 독서기가 열립니다. 폰을 바꾸면 새 폰에서 같은 번호로 다시 등록합니다. 설정 아래에 회원 이름이 나옵니다.",
        "새로고침" to "앱이 이상하거나 새 책이 안 보일 때 설정의 새로고침을 누르십시오.",
        "업데이트 — 새 판 받기" to "도서관 앱은 켤 때와 하루 두 번쯤 협회 서버에 새 판이 나왔는지 스스로 물어봅니다. 새 판이 있으면 폰 알림으로 AI점자도서관 새 판이 나왔습니다, 두드리면 업데이트합니다라고 알려 드리고, 도서관 첫 화면 머리 바로 아래에도 같은 말과 단추를 한 줄로 띄웁니다. 알림이나 그 줄을 두드리시면 앱이 새 판을 스스로 받아 설치 화면을 엽니다. 설치를 한 번 눌러 주시면 됩니다. 처음 한 번은 이 출처 허용을 켜는 화면이 열리니 켜신 뒤 폰의 뒤로 동작으로 돌아오시면 이어서 업데이트합니다. 새 판으로 바뀌면 바뀌었다고 알림을 드립니다. 설정의 새로고침 바로 아래 업데이트 단추로 언제든 새 판이 있는지 살피실 수 있습니다. 내 서재, 책갈피, 내려받은 책은 그대로 남습니다."
    )
}
