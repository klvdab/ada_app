// 안드로이드 자봉 — 교육 마당(2.23.0, 빌드 261014-A19, 대장클, 이사장님 승인 2026-10-14 — 아이폰 JabongMadang.swift 2.22.0과 같음)
// ★이사장님: 앱을 깔고 처음에는 가볍게. 처음부터 문제를 풀라 하면 봉사하려는 마음에 찬물을 끼얹는다.
//   조금 해 보게 해서 의욕이 생기게 하고, 더 잘하고 싶으면 「이걸 배워 보세요」로 센스 있게.
// 동영상은 협회 나스(https://lvd.ada.or.kr/jabong/yeongsang/)에서 받아 앱 안 동영상 화면(JbDonghwaActivity)에서 틉니다.
package kr.or.ada.app.jabong

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import kr.or.ada.app.gilnun.Girok
import kr.or.ada.app.gilnun.Jindong
import kr.or.ada.app.gilnun.Seoljeong
import kr.or.ada.app.gilnun.Sori

object JbMadang {
    const val JEOJANGSO = "https://lvd.ada.or.kr/jabong/yeongsang/"
    class Pyeon(val beonho: Int, val jemok: String, val gil: String, val pail: String) {
        val ireum: String get() = "${beonho}편 $jemok"
        val juso: String get() = JEOJANGSO + pail
    }

    /** 앱에 든 목록 — 나스 목록이 안 올 때 */
    val GIBON = listOf(
        Pyeon(1, "설치와 처음 등록", "약 2분", "jabong_1.mp4"),
        Pyeon(2, "점지도 그리기", "약 2분 20초", "jabong_2.mp4"),
        Pyeon(3, "부탁 길과 AI 밑그림", "약 1분 50초", "jabong_3.mp4"),
        Pyeon(4, "점검과 보완", "약 1분 10초", "jabong_4.mp4")
    )
    var mok: List<Pyeon> = GIBON; private set
    private var d: SharedPreferences? = null
    private var bateum = false
    /** 동영상을 다 보고 돌아오면 화면을 다시 그림 */
    var bakkwim = false

    fun sijak(c: Context) {
        if (d == null) d = c.applicationContext.getSharedPreferences("jabong", Context.MODE_PRIVATE)
    }

    val bon: Set<Int> get() = (d?.getString("jb.donghwaBon", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }.toSet()
    val cheotGil: Boolean get() = d?.getBoolean("jb.cheotGil", false) ?: false

    /** 이미 그려 올린 길이 있는 분(예전 판에서 쓰시던 분)은 첫 길을 마치신 것으로 */
    fun cheotGilSalpigi() {
        if (cheotGil) return
        var olin = JabongNae.geurinSu > 0
        val g = Geurigi.geurinGil
        for (i in 0 until g.length()) if (g.optJSONObject(i)?.optBoolean("olim", false) == true) olin = true
        if (olin) d?.edit()?.putBoolean("jb.cheotGil", true)?.apply()
    }

    /** 첫 길을 올리셨을 때 한 번만 참 */
    fun cheotGilMachim(): Boolean {
        if (cheotGil) return false
        d?.edit()?.putBoolean("jb.cheotGil", true)?.apply()
        Girok.namgi("jb_cheotgil")
        return true
    }

    fun boatda(n: Int) {
        if (n in bon) return
        d?.edit()?.putString("jb.donghwaBon", (bon + n).sorted().joinToString(","))?.apply()
        bakkwim = true
    }

    fun pyeon(n: Int): Pyeon? = mok.firstOrNull { it.beonho == n }

    /** 나스 목록 받기 — 한 번 켤 때 한 번 */
    fun mokrokBatgi() {
        if (bateum) return
        bateum = true
        JbTongsin.getJson(JEOJANGSO + "mokrok.json", emptyMap()) { j ->
            val a = j?.optJSONArray("mok")
            if (a == null || a.length() == 0) { bateum = false; return@getJson }
            val m = ArrayList<Pyeon>()
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                if (o.optInt("beonho", 0) > 0 && o.optString("pail").isNotEmpty()) m.add(Pyeon(o.optInt("beonho"), o.optString("jemok"), o.optString("gil"), o.optString("pail")))
            }
            if (m.isNotEmpty()) mok = m.sortedBy { it.beonho }
        }
    }

    /** 받은 배지 — 해 본 만큼 늘어남 */
    val baeji: List<String> get() {
        val b = ArrayList<String>()
        if (cheotGil) b.add("첫 점지도")
        if (JabongNae.gyoyukDoem) b.add("약속 일곱 익힘")
        if (mok.isNotEmpty() && mok.all { it.beonho in bon }) b.add("동영상 다 보심")
        if (JabongNae.geurinSu >= 10) b.add("길 열 개")
        return b
    }

    /** 협회 점검의 고칠 곳 말에 맞는 동영상 — 걸음·꺾임·짝·문·목소리는 2편, 나머지는 4편 */
    fun doumPyeon(heum: List<String>): Int {
        val t = heum.joinToString(" ")
        for (k in listOf("꺾", "걸음", "끊", "보폭", "짝", "끝", "문", "목소리", "토막", "계단", "건널목", "횡단보도")) if (t.contains(k)) return 2
        return 4
    }

    fun teulgi(c: Context, n: Int) {
        Sori.meomchugi()
        c.startActivity(Intent(c, JbDonghwaActivity::class.java).putExtra("beonho", n))
    }

    /** 처음 걷는 분께 세 가지만(문제 없이) */
    val SESGAJI = listOf(
        "하나, 보폭부터" to "내 보폭 재기로 한 번만 재 두시면 됩니다.",
        "둘, 문 앞에서 시작" to "출발하는 건물 문 앞에서 걷기 시작을, 도착한 곳 문 앞에서 다 걸었습니다를 누릅니다.",
        "셋, 꺾이는 그 자리에서" to "3시나 9시 방향으로 꺾는 바로 그 자리에서 꺾임을 누릅니다."
    )
    val cheotMal: String get() = "처음이시군요. 세 가지만 기억하시면 됩니다. " + SESGAJI.joinToString(" ") { "${it.first}. ${it.second}" } + " 나머지는 걸으시면서 폰이 알려 드립니다."

    fun cheotGilKan(t: JabongActivity) {
        t.keunGeul("처음이시군요 — 세 가지만 기억하세요", 24f)
        for ((a, b) in SESGAJI) t.kadeu("$a\n$b")
        t.geul("나머지는 걸으시면서 폰이 알려 드립니다. 서툴러도 괜찮습니다. 협회가 함께 손봅니다.")
        t.danchu("세 가지 말로 듣기") { Sori.meomchugi(); Sori.mal(cheotMal) }
    }

    /** 내 기록 탭의 교육 마당 */
    fun kan(t: JabongActivity) {
        t.keunGeul("교육 마당", 24f)
        t.geul("더 잘 그리고 싶으실 때 편하게 보세요. 한 편이 2분 안팎입니다.")
        val b0 = bon
        for (p in mok) t.danchu("${p.ireum} — ${p.gil}" + if (p.beonho in b0) ", 다 보심" else "") { teulgi(t, p.beonho) }
        t.danchu("점지도 일곱 가지 약속 보기") { t.yeolgi(YaksokHwamyeon()) }
        t.danchu(if (JabongNae.gyoyukDoem) "실력 다지기 — 아홉 문제, 다 맞히셨습니다" else "실력 다지기 — 아홉 문제, 하고 싶으실 때") { t.yeolgi(SilryeokHwamyeon()) }
        val b = baeji
        t.geul(if (b.isEmpty()) "내 배지 — 아직 없습니다. 첫 길을 올리시면 첫 배지를 드립니다." else "내 배지 ${b.size}개 — " + b.joinToString(", "), true)
    }
}

/** 일곱 가지 약속 보기 — 알림·설정에서 교육 마당으로 옮김 */
class YaksokHwamyeon : JbHwamyeon("점지도 일곱 가지 약속") {
    override fun chaeugi(t: JabongActivity) {
        t.danchu("일곱 가지 약속 모두 듣기") { Sori.meomchugi(); for (y in JbGyoyuk.sorijul) Sori.mal(y) }
        t.geul(JbGyoyuk.meorimal, true)
        for (y in JbGyoyuk.yaksok) {
            val k = t.kadeu(y.jemok + "\n" + y.jul.joinToString("\n"))
            k.setOnClickListener { Sori.meomchugi(); Sori.mal(y.mal) }   // 누르면 그 약속만 다시
        }
        t.geul(JbGyoyuk.maejeummal, true)
    }
}

/** 실력 다지기 — 하고 싶을 때만, 부드럽게 */
class SilryeokHwamyeon : JbHwamyeon("실력 다지기") {
    private var mi = 0
    private var allim = ""
    private var machim = false
    private var cheot = true
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private fun munjeMal(ap: String = ""): String {
        val m = JbGyoyuk.munje[minOf(mi, JbGyoyuk.munje.size - 1)]
        var s = ap + "${mi + 1}번 문제. " + m.q
        m.d.forEachIndexed { k, d -> s += " ${JbGyoyuk.beonho[k]}, $d." }
        return s
    }

    private fun allyeo(t: JabongActivity, m: String) { allim = m; t.dasiGeurigi(); Sori.meomchugi(); Sori.mal(m) }

    override fun chaeugi(t: JabongActivity) {
        if (cheot) { cheot = false; main.post { allyeo(t, munjeMal("실력 다지기입니다. 틀려도 괜찮습니다. 풀이를 들려 드리니 편하게 골라 보세요. ")) } }
        if (allim.isNotEmpty()) t.gibon(t.keunGeul(allim, 20f))
        if (machim) {
            t.keunGeul("약속 일곱 익힘 배지를 받으셨습니다.", 24f)
            t.danchu("처음부터 다시 풀기") { mi = 0; machim = false; allyeo(t, munjeMal("다시 풀어 봅니다. ")) }
            return
        }
        val m = JbGyoyuk.munje[minOf(mi, JbGyoyuk.munje.size - 1)]
        t.geul("문제 ${mi + 1} / ${JbGyoyuk.munje.size}")
        t.kadeu(m.q)
        m.d.forEachIndexed { i, d -> t.danchu("${JbGyoyuk.beonho[i]}. $d") { goreum(t, i) } }
        t.danchu("문제 다시 듣기") { Sori.meomchugi(); Sori.mal(munjeMal()) }
        t.danchu("약속 먼저 보기") { t.yeolgi(YaksokHwamyeon()) }
    }

    private fun goreum(t: JabongActivity, i: Int) {
        val m = JbGyoyuk.munje[mi]
        if (i != m.a) { allyeo(t, "아쉽네요. " + m.h + " 한 번 더 골라 보세요."); return }
        Baksu.chigi()
        Jindong.hagi("arrive")
        if (mi + 1 < JbGyoyuk.munje.size) {
            mi += 1
            val ap = listOf("맞습니다! ", "잘하셨습니다! ", "역시! ")[mi % 3]
            allim = ap + JbGyoyuk.munje[mi].q
            t.dasiGeurigi()
            main.postDelayed({ Sori.meomchugi(); Sori.mal(munjeMal(ap)) }, 1000)
            return
        }
        machim = true
        Baksu.chigi(true)
        JabongNae.gyoyukMachim()
        allyeo(t, "아홉 문제를 모두 맞히셨습니다! 점지도 박사님이십니다. 약속 일곱 익힘 배지를 드립니다.")
    }
}

/** 교육 동영상 화면 — 앱 안에서 틂(재생·멈춤, 10초 뒤로·앞으로, 처음부터 다시, 다음 편, 닫기) */
class JbDonghwaActivity : AppCompatActivity() {
    private lateinit var bv: VideoView
    private lateinit var jemokV: TextView
    private lateinit var allimV: TextView
    private lateinit var jaesaengB: Button
    private lateinit var daeumB: Button
    private var beonho = 1

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun danchu(t: String, f: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setTextColor(Color.WHITE); setBackgroundColor(JabongActivity.NAM); setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        minHeight = dp(64); setPadding(dp(18), dp(8), dp(18), dp(8))
        setOnClickListener { f() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        JbMadang.sijak(this)
        beonho = intent.getIntExtra("beonho", 1)
        val bburi = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        val seuk = ScrollView(this)
        val nae = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(24)) }
        seuk.addView(nae, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        bburi.addView(seuk, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        fun deoh(v: View, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT) {
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h); lp.topMargin = dp(10); nae.addView(v, lp)
        }
        deoh(danchu("닫기 — 교육 마당으로") { finish() })
        jemokV = TextView(this).apply { setTextColor(Color.BLACK); setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f); isFocusable = true; if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true }
        deoh(jemokV)
        allimV = TextView(this).apply { setTextColor(JabongActivity.NAM); setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f); isFocusable = true; visibility = View.GONE }
        deoh(allimV)
        bv = VideoView(this).apply { contentDescription = "동영상 화면" }
        val w = resources.displayMetrics.widthPixels - dp(32)
        deoh(bv, w * 9 / 16)
        jaesaengB = danchu("멈춤") { jaesaengMeomchum() }
        deoh(jaesaengB)
        deoh(danchu("10초 뒤로") { olgigi(-10_000) })
        deoh(danchu("10초 앞으로") { olgigi(10_000) })
        deoh(danchu("처음부터 다시 보기") { teulgi() })
        daeumB = danchu("다음 편") { JbMadang.mok.firstOrNull { it.beonho > beonho }?.let { beonho = it.beonho; teulgi() } }
        deoh(daeumB)
        setContentView(bburi)
        // 아래 조작 막대·위 상태 막대에 가리지 않게
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(bburi) { v, ins ->
            val b = ins.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            androidx.core.view.WindowInsetsCompat.CONSUMED
        }
        bv.setOnCompletionListener {
            jaesaengB.text = "재생"
            JbMadang.boatda(beonho)
            Girok.namgi("jb_donghwa_bon", mapOf("pyeon" to beonho))
            allimV.text = "다 보셨습니다. 고맙습니다."; allimV.visibility = View.VISIBLE
            allimV.announceForAccessibility(allimV.text)
        }
        bv.setOnErrorListener { _, _, _ ->
            allimV.text = "동영상을 받지 못했습니다. 통신을 확인하시고 처음부터 다시 보기를 눌러 주십시오."; allimV.visibility = View.VISIBLE
            allimV.announceForAccessibility(allimV.text)
            true
        }
        teulgi()
    }

    private fun teulgi() {
        val p = JbMadang.pyeon(beonho) ?: return
        jemokV.text = p.ireum
        allimV.visibility = View.GONE
        val n = JbMadang.mok.firstOrNull { it.beonho > beonho }
        daeumB.visibility = if (n != null) View.VISIBLE else View.GONE
        daeumB.text = if (n != null) "다음 편 — ${n.ireum}" else "다음 편"
        bv.setVideoURI(Uri.parse(p.juso))
        bv.setOnPreparedListener { mp: MediaPlayer -> mp.start(); jaesaengB.text = "멈춤" }
        Girok.namgi("jb_donghwa_teum", mapOf("pyeon" to beonho))
        jemokV.announceForAccessibility(p.ireum)
    }

    private fun jaesaengMeomchum() {
        if (bv.isPlaying) { bv.pause(); jaesaengB.text = "재생" } else { bv.start(); jaesaengB.text = "멈춤" }
    }

    private fun olgigi(ms: Int) {
        bv.seekTo(maxOf(0, bv.currentPosition + ms))
    }

    override fun onPause() { super.onPause(); if (bv.isPlaying) { bv.pause(); jaesaengB.text = "재생" } }
}
