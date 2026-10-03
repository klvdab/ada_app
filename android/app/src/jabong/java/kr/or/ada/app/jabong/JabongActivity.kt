// 안드로이드 자봉 — 화면 (2.3.0, 빌드 261002-J1, 대표님 지시: 아이폰 자봉 JabongRoot.swift 와 똑같이)
// 261002-J1 웹 자봉으로 넘기던 등록·첫 화면을 걷어 내고 아이폰과 같은 틀로 다시 짬:
//   처음 등록(자원봉사 등록) → 탭 넷(봉사, 나눔, 내 기록, 알림·설정). 탭 바는 모든 속 화면에 늘, 뒤로 단추는 위에 하나,
//   폰의 뒤로 동작도 앞 화면으로(앱 밖으로 튀지 않음), 화면이 바뀌면 커서를 첫 줄로, 목록은 다섯 개씩, 자주 쓰는 것만 겉에 두고 나머지는 펼치기 안에.
//   긴급통화가 울리면 받기·거절 화면이, 받으면 통화 화면이 탭 위를 덮음(아이폰 CallKit·fullScreenCover 자리). 잠긴 화면 위에도 뜸.
// 새 기능마다 도움말(아이폰 JabongDoumalView 와 같은 항목).
package kr.or.ada.app.jabong

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import kr.or.ada.app.MainActivity
import kr.or.ada.app.gilnun.Girok
import kr.or.ada.app.gilnun.MalGeup
import kr.or.ada.app.gilnun.MomSensor
import kr.or.ada.app.gilnun.Seoljeong
import kr.or.ada.app.gilnun.Sori
import kr.or.ada.app.gilnun.Wichi
import org.webrtc.SurfaceViewRenderer
import kotlin.math.roundToInt

/** 화면 하나 — 제목과 줄들 */
abstract class JbHwamyeon(val jemok: String) {
    abstract fun chaeugi(t: JabongActivity)
}

class JabongActivity : AppCompatActivity() {
    private lateinit var bburi: FrameLayout
    private lateinit var bon: LinearLayout
    private lateinit var nae: LinearLayout
    private lateinit var seuk: ScrollView
    private lateinit var tabJul: LinearLayout
    private lateinit var deopgae: LinearLayout          // 울림·통화 화면(탭 위를 덮음)
    private val tabIreum = listOf("봉사", "나눔", "내 기록", "알림·설정")
    private val gil = List(4) { ArrayList<JbHwamyeon>() }
    private var tab = 0
    private val deungrok = DeungrokHwamyeon()
    private var cheotJul: View? = null
    private var chojeomJul: View? = null                 // 다시 그린 뒤 커서를 둘 줄(물음·새 알림)
    private var gibonJul: View? = null                   // 누른 단추가 사라졌을 때 커서를 둘 줄(바뀐 알림)
    private var nureunGeul: String? = null               // 방금 누른 단추 글 — 다시 그려도 그 단추에 커서를 둠
    private var deopgaeJong = ""                          // "", "ulim", "tonghwa"
    private var tonghwaGeul: TextView? = null
    private var tonghwaDwi: (() -> Unit)? = null
    private var maikDwi: ((Boolean) -> Unit)? = null

    companion object {
        val NAM: Int = Color.rgb(18, 52, 110)
        val NOKSAEK: Int = Color.rgb(255, 204, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        JabongBonche.sijak(this)
        Wichi.sijak(this)

        bburi = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        bon = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        seuk = ScrollView(this)
        nae = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(24)) }
        seuk.addView(nae, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        bon.addView(seuk, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        tabJul = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(NAM) }
        tabIreum.forEachIndexed { i, ir ->
            val b = Button(this).apply {
                text = ir; isAllCaps = false
                setTextColor(Color.WHITE); setBackgroundColor(NAM)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                minHeight = dp(60); setPadding(dp(2), 0, dp(2), 0)
                setOnClickListener { tabGo(i) }
            }
            tabJul.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        bon.addView(tabJul, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        deopgae = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE)
            setPadding(dp(16), dp(16), dp(16), dp(16)); visibility = View.GONE
            isClickable = true   // 아래 탭 화면이 눌리지 않게
        }
        bburi.addView(bon, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        bburi.addView(deopgae, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(bburi)

        Sori.tokbaek = { t -> bburi.announceForAccessibility(t) }
        gil[0].add(BongsaTab())
        gil[1].add(NanumTab())
        gil[2].add(NaeGirokTab())
        gil[3].add(AllimTab())
        if (Geurigi.sangtae != Geurigi.Sangtae.SWIM && JabongNae.deungrokham) gil[0].add(GeurigiHwamyeon())   // 그리던 길이 있으면 곧바로 그리기 화면

        // 알림 글만 바뀌었으면 그 줄만 고쳐 씀(커서가 누르신 단추에 그대로 머묾), 단추가 바뀌면 다시 그림
        Geurigi.bakkwim = {
            runOnUiThread {
                val h = wiHwamyeon
                if (h is GeurigiHwamyeon && deopgaeJong.isEmpty()) { if (h.geulMan()) nureunGeul = null else boyeojugi(false) }
            }
        }
        JabongNae.byeonhwa = { runOnUiThread { boyeojugi() } }
        JabongDaegi.byeonhwa = { runOnUiThread { deopgaeMatchugi() } }
        JabongDaegi.ulimHwamyeon = { runOnUiThread { deopgaeMatchugi() } }
        JabongTonghwa.byeonhwa = { runOnUiThread { deopgaeMatchugi() } }
        JabongTonghwa.geulByeonhwa = { runOnUiThread { tonghwaGeul?.text = JabongTonghwa.geul } }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 통화 중 폰의 뒤로 동작 — 길눈 앱과 같이 묻지 않고 통화를 끊음
                if (JabongTonghwa.tonghwaJung) { JabongTonghwa.kkeutnaegi(); return }
                if (JabongDaegi.ulim != null) { Sori.tokbaek?.invoke("받기나 거절을 눌러 주십시오."); return }
                dwiro()
            }
        })

        boyeojugi()
        deopgaeMatchugi()
        val ac = intent?.action
        if (ac != JabongUlim.BATGI && ac != JabongUlim.ULIM) heorakCheong()   // 울림·받기로 열렸으면 허락 창을 띄우지 않음(받기·통화 화면을 가리지 않게)
        jeonhwaGyeolgwa(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        jeonhwaGyeolgwa(intent)
    }

    /** 울림 알림의 받기·화면 열기 */
    private fun jeonhwaGyeolgwa(i: Intent?) {
        when (i?.action) {
            JabongUlim.BATGI -> JabongDaegi.batgi()   // 카메라·마이크 허락은 함께하겠습니다 때 받아 둠(아이폰과 같이 받는 순간에는 묻지 않음)
            JabongUlim.ULIM -> deopgaeMatchugi()
        }
    }

    override fun onResume() { super.onResume(); Wichi.wiseongDolligi(); deopgaeMatchugi() }
    override fun onPause() { super.onPause(); JabongBonche.jeojang() }

    override fun onDestroy() {
        Geurigi.bakkwim = null
        JabongNae.byeonhwa = null
        JabongDaegi.byeonhwa = null
        JabongDaegi.ulimHwamyeon = null
        JabongTonghwa.byeonhwa = null
        JabongTonghwa.geulByeonhwa = null
        // 자봉 화면이 닫히면 통화를 끊고 카메라·마이크를 치움(길눈 앱과 같음)
        if (JabongTonghwa.tonghwaJung) JabongTonghwa.kkeunki(true)
        JabongTonghwa.hwamyeonTteoki()
        super.onDestroy()
    }

    // MARK: 허락

    private fun heorakItda(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun heorakCheong() {
        val p = arrayListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 29) p.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
        val an = p.filter { !heorakItda(it) }
        if (an.isNotEmpty()) ActivityCompat.requestPermissions(this, an.toTypedArray(), 7)
    }

    /** 긴급통화 — 카메라·마이크(·알림) 허락. 있으면 곧바로 f, 없으면 여쭙고 답이 오면 f(허락하지 않으셔도 이어 감) */
    fun tonghwaHeorak(f: () -> Unit) {
        val p = arrayListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
        val an = p.filter { !heorakItda(it) }
        if (an.isEmpty()) { f(); return }
        val ap = tonghwaDwi
        if (ap != null) { tonghwaDwi = { ap(); f() }; return }
        tonghwaDwi = f
        ActivityCompat.requestPermissions(this, an.toTypedArray(), 10)
    }

    /** 말로 표시 — 마이크 허락 */
    fun maikHeorak(f: (Boolean) -> Unit) {
        if (heorakItda(Manifest.permission.RECORD_AUDIO)) { f(true); return }
        maikDwi = f
        Sori.mal("말로 표시하려면 마이크 허락이 필요합니다. 허용을 눌러 주십시오.")
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 9)
    }

    /** 배터리 아끼기에서 자봉을 빼 달라고 한 번 여쭘 — 화면이 꺼져도 긴급통화가 늦지 않게 울리도록 */
    fun baeteoriYeojjum() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) return
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            Girok.namgi("jb_baeteori", mapOf("e" to (e.message ?: "")))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val gyeolgwa = permissions.indices.associate { permissions[it].substringAfterLast('.') to (grantResults.getOrNull(it) == PackageManager.PERMISSION_GRANTED) }
        Girok.namgi("jb_heorak", gyeolgwa)
        if (requestCode == 10) { val f = tonghwaDwi; tonghwaDwi = null; f?.invoke(); return }
        if (requestCode == 9) { val f = maikDwi; maikDwi = null; f?.invoke(heorakItda(Manifest.permission.RECORD_AUDIO)); return }
        Wichi.wiseongDolligi()
        if (!Wichi.heorakItda) Sori.mal("점지도를 그리려면 위치 허락이 필요합니다. 폰 설정의 앱, 자봉, 권한에서 위치를 허용해 주십시오.")
        if (!Wichi.georeumHeorak) Sori.mal("걸음을 세려면 신체 활동 허락이 필요합니다. 폰 설정의 앱, 자봉, 권한에서 신체 활동을 허용해 주십시오.")
    }

    // MARK: 화면 옮기기

    fun tabGo(i: Int) {
        if (i == tab && gil[i].size > 1) while (gil[i].size > 1) gil[i].removeAt(gil[i].size - 1)   // 같은 탭을 다시 누르면 그 탭의 첫 화면으로
        tab = i
        boyeojugi()
    }

    fun yeolgi(h: JbHwamyeon) { gil[tab].add(h); boyeojugi() }

    fun dwiro() {
        if (!JabongNae.deungrokham) { deungrok.dwiro(this); return }
        if (gil[tab].size > 1) { gil[tab].removeAt(gil[tab].size - 1); boyeojugi() }
        else Sori.mal("여기가 첫 화면입니다.", MalGeup.JEONGBO)
    }

    /** 지금 화면을 다시 그림(커서는 방금 누른 단추나 새 알림 줄에) */
    fun dasiGeurigi() { boyeojugi(false) }

    val wiHwamyeon: JbHwamyeon get() = if (!JabongNae.deungrokham) deungrok else gil[tab].last()

    /** 다시 그린 뒤 커서를 이 줄에 */
    fun chojeom(v: View) { chojeomJul = v }

    /** 방금 누른 단추가 다시 그린 화면에 없으면 커서를 이 줄에(바뀐 알림 줄) */
    fun gibon(v: View) { gibonJul = v }

    private fun omgigi(v: View) {
        v.post {
            if (!v.isAttachedToWindow) return@post
            v.requestFocus()
            v.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
            v.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
        }
    }

    private fun boyeojugi(chojeomCheot: Boolean = true) {
        nae.removeAllViews()
        cheotJul = null
        chojeomJul = null
        gibonJul = null
        val deung = JabongNae.deungrokham
        tabJul.visibility = if (deung) View.VISIBLE else View.GONE
        val h = wiHwamyeon
        title = h.jemok
        if (deung && gil[tab].size > 1) {
            val ap = gil[tab][gil[tab].size - 2].jemok
            danchu("뒤로 — $ap") { dwiro() }
        }
        jemok(h.jemok)
        val nureun = nureunGeul
        nureunGeul = null
        h.chaeugi(this)
        if (deung) {
            for (i in 0 until tabJul.childCount) {
                val b = tabJul.getChildAt(i) as Button
                val seon = i == tab
                b.setBackgroundColor(if (seon) Color.rgb(8, 30, 70) else NAM)
                b.setTextColor(if (seon) NOKSAEK else Color.WHITE)
                b.contentDescription = tabIreum[i] + if (seon) ", 선택됨" else ""
            }
        }
        if (chojeomCheot) seuk.scrollTo(0, 0)
        if (deopgaeJong.isNotEmpty()) return   // 울림·통화 화면이 덮고 있으면 그쪽에 커서
        val c: View? = when {
            chojeomJul != null -> chojeomJul
            chojeomCheot -> cheotJul
            nureun != null -> danchuChatgi(nureun) ?: gibonJul ?: cheotJul
            else -> gibonJul
        }
        c?.let { omgigi(it) }
    }

    /** 방금 누른 단추 찾기 — 글이 같으면 그 단추, 없으면 " — " 앞이 같은 단추(말로 표시 — 듣는 중처럼 형편만 바뀐 단추) */
    private fun danchuChatgi(t: String): View? {
        val ap = t.substringBefore(" — ")
        var bit: View? = null
        for (i in 0 until nae.childCount) {
            val v = nae.getChildAt(i)
            if (v !is Button) continue
            val g = v.text.toString()
            if (g == t) return v
            if (bit == null && g.substringBefore(" — ") == ap) bit = v
        }
        return bit
    }

    // MARK: 울림·통화 화면(탭 위를 덮음)

    private fun jamgeumWi(kyeom: Boolean) {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(kyeom)
            setTurnScreenOn(kyeom)
        } else {
            @Suppress("DEPRECATION")
            val f = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            if (kyeom) window.addFlags(f) else window.clearFlags(f)
        }
        if (kyeom) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun deopgaeMatchugi() {
        val jong = when {
            JabongTonghwa.tonghwaJung -> "tonghwa"
            JabongDaegi.ulim != null -> "ulim"
            else -> ""
        }
        if (jong == deopgaeJong && jong != "ulim") return
        if (deopgaeJong == "tonghwa" && jong != "tonghwa") JabongTonghwa.hwamyeonTteoki()
        val ap = deopgaeJong
        deopgaeJong = jong
        deopgae.removeAllViews()
        tonghwaGeul = null
        if (jong.isEmpty()) {
            deopgae.visibility = View.GONE
            bon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            jamgeumWi(false)
            if (ap.isNotEmpty()) boyeojugi()
            return
        }
        deopgae.visibility = View.VISIBLE
        bon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        jamgeumWi(true)
        var cheot: View? = null
        fun deoh(v: View, lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) {
            lp.topMargin = dp(10)
            deopgae.addView(v, lp)
            if (cheot == null) cheot = v
        }
        if (jong == "ulim") {
            val u = JabongDaegi.ulim ?: return
            deoh(jemokView(JabongUlim.jemok(u.gal, u.who)))
            if (u.mok.isNotEmpty()) deoh(geulView("길손님의 목적지 — ${u.mok}", false))
            deoh(danchuView("받기 — 곧바로 길손님 카메라 화면과 말소리가 이어집니다") { JabongDaegi.batgi() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(110)))
            deoh(danchuView("거절 — 이번 부름은 받지 않기") { JabongDaegi.geojeol() })
        } else {
            val g = geulView(JabongTonghwa.geul, true)
            tonghwaGeul = g
            deoh(g)
            if (JabongTonghwa.mok.isNotEmpty()) deoh(geulView("길손님의 목적지 — ${JabongTonghwa.mok}", false))
            val v = SurfaceViewRenderer(this).apply {
                contentDescription = "길손님 카메라 화면"
                setBackgroundColor(Color.BLACK)
            }
            deoh(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            JabongTonghwa.hwamyeonBuchigi(v)
            deoh(danchuView("통화 마치기") { JabongTonghwa.kkeutnaegi() })
        }
        cheot?.let { omgigi(it) }
    }

    // MARK: 줄 만들기

    fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun deohagi(v: View) {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(10)
        nae.addView(v, lp)
        if (cheotJul == null) cheotJul = v
    }

    private fun jemokView(t: String) = TextView(this).apply {
        text = t; setTextColor(Color.BLACK); setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f); setTypeface(typeface, Typeface.BOLD)
        if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        isFocusable = true
    }

    private fun geulView(t: String, keuge: Boolean) = TextView(this).apply {
        text = t; setTextColor(Color.BLACK); setTextSize(TypedValue.COMPLEX_UNIT_SP, if (keuge) 21f else 18f)
        setLineSpacing(0f, 1.15f); isFocusable = true
    }

    private fun danchuView(t: String, f: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setTextColor(Color.WHITE); setBackgroundColor(NAM); setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        minHeight = dp(64); setPadding(dp(18), dp(8), dp(18), dp(8))
        setOnClickListener { nureunGeul = t; f() }
    }

    fun jemok(t: String) { deohagi(jemokView(t)) }
    fun geul(t: String, keuge: Boolean = false): TextView { val v = geulView(t, keuge); deohagi(v); return v }
    fun danchu(t: String, f: () -> Unit): Button { val b = danchuView(t, f); deohagi(b); return b }

    /** 적는 칸 — 이름은 힌트로(톡백이 빈 칸에서 읽음), 적은 글은 바뀔 때마다 dameum 에 */
    fun ipryeok(t: String, gap: String, jong: Int, dameum: (String) -> Unit): EditText {
        val e = EditText(this).apply {
            hint = t
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            inputType = jong
            minHeight = dp(60)
            setText(gap)
            doAfterTextChanged { dameum(it?.toString() ?: "") }
        }
        deohagi(e)
        return e
    }

    /** 펼치기 — 누르면 안의 줄들이 보이고 다시 누르면 접힘 */
    fun pyeolchigi(t: String, pyeolchim: Boolean, toggle: () -> Unit) {
        danchu(if (pyeolchim) "$t 접기" else "$t 펼치기") { toggle(); dasiGeurigi() }
    }

    fun webJabong() { startActivity(Intent(this, MainActivity::class.java)) }

    fun hwagin(mal: String, ye: String, f: () -> Unit) {
        AlertDialog.Builder(this).setMessage(mal).setPositiveButton(ye) { _, _ -> f() }.setNegativeButton("그만두기", null).show()
    }
}

// MARK: 봉사 탭

class BongsaTab : JbHwamyeon("봉사") {
    override fun chaeugi(t: JabongActivity) {
        t.danchu(if (JabongDaegi.geobu) "긴급통화 받기 — 받지 않기로 하심" else if (JabongDaegi.kyeojim) "긴급통화 받기 — 받고 있음" else "긴급통화 받기 — 길눈님이 도움을 청하면 전화처럼 울립니다") { t.yeolgi(HamkkeHwamyeon()) }
        t.danchu(when (Geurigi.sangtae) {
            Geurigi.Sangtae.GEOREUM -> "점지도 그리기 — 그리는 중"
            Geurigi.Sangtae.MEOMCHUM -> "점지도 그리기 — 잠깐 멈춤, 이어 그리기"
            else -> "점지도 그리기"
        }) { t.yeolgi(GeurigiHwamyeon()) }
        t.danchu("지금 상태 듣기") { Sori.mal(jigeumSangtae()) }
        t.danchu(if (Seoljeong.bopokJaem && Seoljeong.bopok > 0.2) "내 보폭 다시 재기 — 지금 ${(Seoljeong.bopok * 100).roundToInt()}센티미터" else "내 보폭 재기 — 점지도를 그리기 전에 한 번") { t.yeolgi(BopokHwamyeon()) }
        t.geul("오늘 걸을 길과 함께 걷기는 이 탭에 차례로 들어섭니다.")
    }

    companion object {
        fun jigeumSangtae(): String {
            var m = "자봉 번호 ${JabongNae.beonho}, ${JabongNae.ireum}님."
            m += if (JabongDaegi.geobu) " 긴급통화는 받지 않기로 하셨습니다." else if (JabongDaegi.kyeojim) " 긴급통화를 받고 있습니다." else " 긴급통화는 받지 않는 중입니다."
            if (Geurigi.sangtae != Geurigi.Sangtae.SWIM) m += " " + Geurigi.sangtaeMal()
            m += if (Seoljeong.bopokJaem && Seoljeong.bopok > 0.2) " 보폭은 ${(Seoljeong.bopok * 100).roundToInt()}센티미터입니다." else " 아직 보폭을 재지 않으셨습니다. 점지도를 그리기 전에 한 번 재 주십시오."
            val w = Wichi.jigeum
            m += if (w != null) { if (w.ochae <= 15) " 위성이 잘 잡혀 있습니다." else " 위성이 아직 흐립니다. 하늘이 트인 곳에서 잠시 기다려 주십시오." } else " 아직 위치를 받지 못했습니다."
            return m
        }
    }
}

// MARK: 긴급통화 받기 — 아이폰 JbHamkkeView 와 같음 (2.4.0 역할 셋과 긴급통화 받지 않기)

class HamkkeHwamyeon : JbHwamyeon("긴급통화 받기") {
    private var kind = "haebong"
    private var byeol = ""
    private var hwagin = ""
    private var beonho = ""
    private var gajokIreum = ""
    private var allim = ""
    private var allimBeon = 0
    private var boinBeon = 0
    private var hal = false
    private var gochim = false
    private var deo = false

    private fun badeumMal(): String {
        val g = JabongDaegi
        var m = "긴급통화를 받고 있습니다."
        if (g.kind.isNotEmpty()) m += " ${if (g.kind == "haeseolsa") "현장영상해설사" else "자원봉사자"}, 별명 ${g.byeol}."
        if (g.gajok.isNotEmpty()) m += " 가족·지인으로 등록된 곳은 " + g.gajok.joinToString(", ") { "$it 님" } + "입니다."
        return m
    }

    override fun chaeugi(t: JabongActivity) {
        if (allim.isNotEmpty()) {
            val v = t.geul(allim, true)
            if (allimBeon != boinBeon) t.chojeom(v)   // 새 알림일 때만 커서를 그 줄로
        }
        boinBeon = allimBeon
        val g = JabongDaegi
        if (g.geobu && !gochim) {
            t.geul("긴급통화를 받지 않기로 하셨습니다. 어떤 요청도 울리지 않습니다. 점지도 그리기는 그대로 쓰실 수 있습니다.", true)
            t.danchu("마음이 바뀌면 — 긴급통화 받기 시작") { geobuPulgi(t) }
        } else if (g.kyeojim && !gochim) {
            t.geul(badeumMal(), true)
            t.geul("요청이 오면 일반 전화처럼 울립니다. 받으시면 곧바로 길눈님 카메라 화면과 말소리가 이어집니다.")
            t.danchu("잠시 쉬기 — 긴급통화를 받지 않음") { swigi(t, true) }
            t.pyeolchigi("더 보기", deo) { deo = !deo }
            if (deo) {
                t.danchu("가족·지인으로 받기 — 이음 번호 넣기") { kind = "gajok"; beonho = ""; gajokIreum = g.gajokIreum; gochim = true; allim = ""; t.dasiGeurigi() }
                t.danchu("역할이나 별명 고치기") { kind = g.kind.ifEmpty { "haebong" }; byeol = g.byeol; gochim = true; allim = ""; t.dasiGeurigi() }
                t.danchu("긴급통화 받지 않기 — 점지도만 그립니다") { geobu(t) }
                t.danchu("배터리 아끼기에서 자봉 빼기 — 화면이 꺼져도 늦지 않게 울리도록") { t.baeteoriYeojjum() }
            }
        } else if ((g.kind.isNotEmpty() || g.gajok.isNotEmpty()) && !g.kyeojim && !gochim) {
            t.geul("지금은 쉬는 중입니다. 긴급통화가 울리지 않습니다.", true)
            t.danchu("다시 함께하기 — 긴급통화 받기") { swigi(t, false) }
            t.danchu("가족·지인으로 받기 — 이음 번호 넣기") { kind = "gajok"; beonho = ""; gajokIreum = g.gajokIreum; gochim = true; allim = ""; t.dasiGeurigi() }
            t.danchu("역할이나 별명 고치기") { kind = g.kind.ifEmpty { "haebong" }; byeol = g.byeol; gochim = true; allim = ""; t.dasiGeurigi() }
            t.danchu("긴급통화 받지 않기 — 점지도만 그립니다") { geobu(t) }
        } else {
            t.geul("길눈님이 도움을 청할 때 받으실 역할을 고르십시오. 실명과 전화번호는 화면에 나오지 않습니다.")
            t.danchu((if (kind == "haebong") "고름 — " else "") + "자원봉사자로 받기 — 교육을 마치신 분") { kind = "haebong"; hwagin = ""; t.dasiGeurigi() }
            t.danchu((if (kind == "haeseolsa") "고름 — " else "") + "현장영상해설사로 받기 — 협회 해설사") { kind = "haeseolsa"; hwagin = ""; t.dasiGeurigi() }
            t.danchu((if (kind == "gajok") "고름 — " else "") + "가족·지인으로 받기 — 길눈님께 이음 번호를 받으신 분") { kind = "gajok"; t.dasiGeurigi() }
            if (kind == "gajok") {
                t.ipryeok("이음 번호 여섯 자리 — 길눈님께 받으신 번호", beonho, InputType.TYPE_CLASS_NUMBER) { beonho = it }
                t.ipryeok("길눈님이 부르실 내 이름 — 보기: 큰딸, 김철수", gajokIreum, InputType.TYPE_CLASS_TEXT) { gajokIreum = it }
                t.danchu("가족·지인으로 등록하기") { gajokDeungrok(t) }
            } else {
                t.ipryeok("길눈님 별명", byeol, InputType.TYPE_CLASS_TEXT) { byeol = it }
                if (kind == "haeseolsa") t.ipryeok("협회에 등록하신 전화번호 — 확인에만 씁니다", hwagin, InputType.TYPE_CLASS_PHONE) { hwagin = it }
                else t.ipryeok("수료 번호 — 협회에서 받으신 번호", hwagin, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) { hwagin = it }
                t.danchu("함께하겠습니다") { hamkke(t) }
            }
            if (gochim) t.danchu("그만두기") { gochim = false; allim = ""; t.dasiGeurigi() }
            else t.danchu("긴급통화는 받지 않겠습니다 — 점지도만 그립니다") { geobu(t) }
        }
    }

    private fun allyeo(t: JabongActivity, m: String) { allim = m; allimBeon += 1; t.dasiGeurigi() }

    private fun hamkke(t: JabongActivity) {
        if (hal) return
        hal = true
        allyeo(t, "잠시만요, 자리를 마련하고 있습니다.")
        // 카메라·마이크·알림 허락을 지금 받아 둠 — 통화 때 허용 창을 찾는 일이 없게
        t.tonghwaHeorak {
            JabongDaegi.hamkke(kind, byeol, hwagin) { why ->
                hal = false
                if (why != null) {
                    allyeo(t, why)
                } else {
                    gochim = false
                    allyeo(t, "고맙습니다. 이제 함께하는 눈이 되셨습니다. 길눈님이 도움을 청하면 전화처럼 울립니다.")
                    t.baeteoriYeojjum()
                }
            }
        }
    }

    private fun gajokDeungrok(t: JabongActivity) {
        if (hal) return
        hal = true
        allyeo(t, "잠시만요, 등록하고 있습니다.")
        t.tonghwaHeorak {
            JabongDaegi.gajokDeungrok(beonho, gajokIreum) { why ->
                hal = false
                if (why != null) {
                    allyeo(t, why)
                } else {
                    gochim = false
                    allyeo(t, "등록되었습니다. ${JabongDaegi.gajok.lastOrNull() ?: "길눈"} 님이 화상통화를 요청하시면 이 폰이 전화처럼 울립니다.")
                    t.baeteoriYeojjum()
                }
            }
        }
    }

    private fun swigi(t: JabongActivity, s: Boolean) {
        if (hal) return
        hal = true
        JabongDaegi.swigi(s) {
            hal = false
            allyeo(t, if (s) "잠시 쉽니다. 긴급통화가 울리지 않습니다." else "다시 함께합니다. 긴급통화가 울립니다.")
        }
    }

    private fun geobu(t: JabongActivity) {
        if (hal) return
        hal = true
        JabongDaegi.geobuhagi {
            hal = false
            gochim = false
            allyeo(t, "긴급통화를 받지 않습니다. 점지도 그리기는 그대로 쓰실 수 있습니다.")
        }
    }

    private fun geobuPulgi(t: JabongActivity) {
        if (hal) return
        hal = true
        JabongDaegi.geobuPulgi {
            hal = false
            if (JabongDaegi.kyeojim) allyeo(t, "다시 긴급통화를 받습니다.") else { gochim = true; allyeo(t, "받으실 역할을 고르십시오.") }
        }
    }
}

// MARK: 점지도 그리기 — 아이폰 GeurigiView 와 같음

class GeurigiHwamyeon : JbHwamyeon("점지도 그리기") {
    private var pyeolchim = false
    private var gilPyeol = false
    private var gilJjok = 0
    private var gyedanSu = ""
    private var apMureum: Geurigi.Mureum? = null
    private var apAllim = ""
    private var allimJul: TextView? = null
    private var teul = ""

    /** 지금 화면의 틀(단추가 달라지는지 가리는 열쇠) */
    private fun teulNow(): String = "${Geurigi.sangtae}|${System.identityHashCode(Geurigi.mureum)}|${Geurigi.malDeutneun}|${Geurigi.majimakGyedan != null}|${Geurigi.geurinGil.length()}|${Geurigi.allim.isEmpty()}"

    /** 틀이 그대로이고 알림 글만 바뀌었으면 그 줄만 고쳐 쓰고 참 */
    fun geulMan(): Boolean {
        val v = allimJul ?: return false
        if (teulNow() != teul || !v.isAttachedToWindow) return false
        v.text = Geurigi.allim
        apAllim = Geurigi.allim
        return true
    }

    override fun chaeugi(t: JabongActivity) {
        teul = teulNow()
        allimJul = null
        // 폰이 여쭙는 말과 답하는 단추는 한 자리에
        val m = Geurigi.mureum
        if (m != null) {
            val v = t.geul(m.mal, true)
            if (m !== apMureum) t.chojeom(v)
            t.danchu(m.danchu) { Geurigi.dap(true) }
            t.danchu("아니오 — 남기지 않기") { Geurigi.dap(false) }
        }
        apMureum = m
        if (Geurigi.allim.isNotEmpty()) {
            val v = t.geul(Geurigi.allim, true)
            allimJul = v
            if (Geurigi.allim != apAllim) t.gibon(v)   // 누른 단추가 사라졌거나 저절로 바뀐 알림이면 커서를 이 줄로
        }
        apAllim = Geurigi.allim
        when (Geurigi.sangtae) {
            Geurigi.Sangtae.SWIM -> sijakJeon(t)
            Geurigi.Sangtae.GEOREUM -> georeumJung(t)
            Geurigi.Sangtae.MEOMCHUM -> {
                t.danchu("다시 걷기") { Geurigi.dasiGeotgi() }
                t.danchu("다 걸었습니다 — 여기까지로 마치기") { Geurigi.kkeut() }
                t.danchu("지금 상태 듣기") { Sori.mal(Geurigi.sangtaeMal()) }
            }
        }
    }

    private fun sijakJeon(t: JabongActivity) {
        if (Seoljeong.bopokJaem && Seoljeong.bopok > 0.2) t.danchu("걷기 시작 — 출발 자리 주소는 저절로 적습니다") { Geurigi.sijak() }
        else t.danchu("먼저 내 보폭 재기 — 보폭이 있어야 그릴 수 있습니다") { t.yeolgi(BopokHwamyeon()) }
        t.danchu("지금 상태 듣기") { Sori.mal(Geurigi.sangtaeMal()) }
        val mok = Geurigi.geurinGil
        if (mok.length() > 0) {
            t.danchu(if (gilPyeol) "그린 길 ${mok.length()}개 접기" else "그린 길 ${mok.length()}개 펼치기") { gilPyeol = !gilPyeol; gilJjok = 0; t.dasiGeurigi() }
            if (gilPyeol) {
                // 다섯 개씩 — 아래에 더 보기, 그 아래에 이전 보기. 펼치면 커서를 첫 줄로
                val sijak = gilJjok * 5
                for (i in sijak until minOf(sijak + 5, mok.length())) {
                    val o = mok.optJSONObject(i) ?: continue
                    val v = t.geul(Geurigi.gilJul(o))
                    if (i == sijak) t.chojeom(v)
                }
                if (sijak + 5 < mok.length()) t.danchu("더 보기") { gilJjok += 1; t.dasiGeurigi() }
                if (gilJjok > 0) t.danchu("이전 보기") { gilJjok -= 1; t.dasiGeurigi() }
            }
        }
        t.geul("꺾이는 곳, 계단, 건널목, 문에 닿는 순간 표시를 남기시면 됩니다. 폰이 방향이나 높이가 바뀐 것을 알아채면 먼저 여쭙니다. 네라고 말씀하시거나 네 단추를 누르셔야 표시가 됩니다.")
    }

    private fun georeumJung(t: JabongActivity) {
        for (n in Geurigi.JAJU) t.danchu(n) { Geurigi.pyosi(n) }
        t.danchu(if (Geurigi.malDeutneun) "말로 표시 — 듣는 중" else "말로 표시 — 누르고 계단 시작처럼 말씀하십시오") {
            t.maikHeorak { ok -> if (ok) Geurigi.malloPyosi() else Sori.mal("말로 표시하려면 마이크와 음성 인식 허락이 필요합니다.") }
        }
        t.danchu("다 걸었습니다") { Geurigi.kkeut() }
        t.pyeolchigi("다른 표시와 도구", pyeolchim) { pyeolchim = !pyeolchim }
        if (pyeolchim) {
            for (n in Geurigi.MARKS.filter { it !in Geurigi.JAJU }) t.danchu(n) { Geurigi.pyosi(n) }
            t.danchu("지금 상태 듣기") { Sori.mal(Geurigi.sangtaeMal()) }
            t.danchu("잠깐 멈춤") { Geurigi.jamkkan() }
            val n = Geurigi.majimakGyedan
            if (n != null) {
                t.ipryeok("계단 칸수 — 지금 ${n}칸", gyedanSu, InputType.TYPE_CLASS_NUMBER) { gyedanSu = it }
                t.danchu("계단 칸수 고치기") {
                    val k = gyedanSu.trim().toIntOrNull()
                    if (k != null && k > 0) { gyedanSu = ""; Geurigi.gyedanGochigi(k) }
                    else Sori.mal("계단 칸수를 숫자로 적어 주십시오.")
                }
            }
        }
    }
}

// MARK: 내 보폭 재기

class BopokHwamyeon : JbHwamyeon("내 보폭 재기") {
    private var bopokSijak = -1
    private var gap = ""
    override fun chaeugi(t: JabongActivity) {
        t.geul("두 가지 가운데 고르십시오. 10미터 걸어 재기는 줄자나 바닥 표시로 10미터를 미리 재 둔 곳에서 합니다.")
        if (bopokSijak < 0) {
            t.danchu("10미터 걸어 재기 — 출발점에 서서 누르기") {
                if (!MomSensor.dollyeo) MomSensor.kyeogi(t)
                bopokSijak = MomSensor.georeumSu
                Sori.mal("평소 걸음으로 10미터를 걸으시고, 도착하시면 다 걸었습니다를 눌러 주십시오.")
                t.dasiGeurigi()
            }
        } else {
            t.danchu("10미터 다 걸었습니다") {
                val n = MomSensor.georeumSu - bopokSijak
                if (Geurigi.sangtae == Geurigi.Sangtae.SWIM) MomSensor.kkeugi()
                bopokSijak = -1
                if (n < 8 || n > 30) { Sori.mal("${n}걸음으로 셌습니다. 10미터 걸음으로 보기 어렵습니다. 다시 재 주십시오."); t.dasiGeurigi(); return@danchu }
                Seoljeong.bopok = 10.0 / n
                Girok.namgi("jb_bopok", mapOf("georeum" to n, "bopok" to Seoljeong.bopok))
                Sori.mal("${n}걸음이었습니다. 보폭을 ${(Seoljeong.bopok * 100).roundToInt()}센티미터로 담았습니다.")
                t.dasiGeurigi()
            }
            t.danchu("그만두기") { bopokSijak = -1; if (Geurigi.sangtae == Geurigi.Sangtae.SWIM) MomSensor.kkeugi(); t.dasiGeurigi() }
        }
        t.ipryeok("보폭을 센티미터로 넣기(예: 65)", gap, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL) { gap = it }
        t.danchu("넣은 값으로 담기") {
            val v = gap.trim().toDoubleOrNull()
            if (v == null || v < 30 || v > 110) { Sori.mal("30에서 110 사이의 센티미터로 넣어 주십시오."); return@danchu }
            Seoljeong.bopok = v / 100.0
            gap = ""
            Sori.mal("보폭을 ${v.roundToInt()}센티미터로 담았습니다.")
            t.dasiGeurigi()
        }
    }
}

// MARK: 나눔·내 기록 탭

class NanumTab : JbHwamyeon("나눔") {
    override fun chaeugi(t: JabongActivity) {
        t.geul("그려주세요 게시판, 걸음 나눔 게시판, 물품 나눔 마당이 이 탭에 들어섭니다. 그동안은 웹 자봉에서 쓰시던 글이 그대로 남아 있고, 앱이 채워지면 같은 글을 앱에서 보시게 됩니다.")
        t.danchu("웹 자봉 게시판 열기 — 앱으로 옮겨 오기 전까지") { t.webJabong() }
    }
}

class NaeGirokTab : JbHwamyeon("내 기록") {
    override fun chaeugi(t: JabongActivity) {
        t.geul("자봉 번호 ${JabongNae.beonho}", true)
        t.geul("이름 ${JabongNae.ireum}")
        if (JabongNae.jiyeok.isNotEmpty()) t.geul("활동 지역 ${JabongNae.jiyeok}")
        t.geul(if (JabongNae.id1365.isEmpty()) "1365 아이디는 아직 넣지 않으셨습니다." else "1365 아이디 ${JabongNae.id1365}")
        t.geul("그려 주신 길 ${JabongNae.geurinSu}개")
        t.geul("내 발자취와 교육 마당이 이 탭에 들어섭니다.")
    }
}

// MARK: 알림·설정 탭

class AllimTab : JbHwamyeon("알림·설정") {
    private var deo = false
    override fun chaeugi(t: JabongActivity) {
        t.danchu("말소리 — 지금 " + (if (Seoljeong.malKyeojim) "켜짐, 누르면 꺼짐" else "꺼짐, 누르면 켜짐")) {
            Seoljeong.malKyeojim = !Seoljeong.malKyeojim
            t.dasiGeurigi()
            if (Seoljeong.malKyeojim) Sori.mal("말소리를 켰습니다.") else Sori.tokbaek?.invoke("말소리를 껐습니다.")
        }
        t.danchu("빠르기 — 지금 ${Seoljeong.bbareugiIreum[Seoljeong.bbareugiDan.coerceIn(0, 4)]}") {
            Seoljeong.bbareugiDan = (Seoljeong.bbareugiDan + 1) % 5
            t.dasiGeurigi()
            Sori.mal("이 빠르기로 말씀드립니다.")
        }
        t.danchu("새로고침 — 등록 정보를 나스에서 다시 받기") {
            JabongNae.dasiBatgi { ok ->
                Girok.bonaegi()
                Sori.mal(if (ok) "새로 받았습니다. 그려 주신 길은 ${JabongNae.geurinSu}개입니다." else "새로 받지 못했습니다. 통신을 확인하시고 다시 눌러 주십시오.")
            }
        }
        t.danchu("도움말") { t.yeolgi(DoumalHwamyeon()) }
        t.pyeolchigi("더 보기", deo) { deo = !deo }
        if (deo) {
            t.danchu("판 기록 — 자봉 앱 ${JabongPan.pan}") { t.yeolgi(PanHwamyeon()) }
            t.danchu("이 폰에서 등록 지우기 — 나스 기록은 그대로") {
                t.hwagin("이 폰에서 등록을 지울까요? 자봉 번호와 네 자리 숫자로 언제든 다시 이어 쓰실 수 있습니다.", "지우기") { JabongNae.ijeugi() }
            }
        }
    }
}

class PanHwamyeon : JbHwamyeon("판 기록") {
    override fun chaeugi(t: JabongActivity) {
        t.geul("자봉 앱 ${JabongPan.pan} (빌드 ${JabongPan.bild}, 앱 짓기 번호 ${apBild(t)})", true)
        for (g in JabongPan.girok) {
            t.geul("${g.pan}판, 빌드 ${g.bild}, ${g.nal}", true)
            for (n in g.naeyong) t.geul(n)
        }
    }

    /** 앱 짓기 번호(아이폰 CFBundleVersion 자리) */
    @Suppress("DEPRECATION")
    private fun apBild(t: JabongActivity): String = try {
        val pi = t.packageManager.getPackageInfo(t.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toString() else pi.versionCode.toString()
    } catch (e: Exception) { "" }
}

class DoumalHwamyeon : JbHwamyeon("도움말") {
    private var yeollin = -1
    override fun chaeugi(t: JabongActivity) {
        DOUMAL.forEachIndexed { i, (jemok, naeyong) ->
            t.danchu(if (yeollin == i) "$jemok 접기" else jemok) { yeollin = if (yeollin == i) -1 else i; t.dasiGeurigi() }
            if (yeollin == i) t.geul(naeyong)
        }
    }

    companion object {
        /** 아이폰 JabongDoumalView 와 같은 항목 — 안드로이드에서 다른 대목(뒤로 동작, 알림 칸, 배터리)만 고쳐 씀 */
        val DOUMAL = listOf(
            "처음 등록" to "자봉 앱을 처음 여시면 한 번만 등록합니다. 이름, 연락처, 주로 활동하실 지역, 네 자리 숫자를 적고, 1365 아이디는 비워 두었다가 나중에 넣으셔도 됩니다. 점지도 그리기 요령 다섯 가지를 듣고 확인 문제 세 개를 풀면 자봉 번호가 나옵니다. 웹 자봉에서 이미 등록하셨으면 자봉 번호와 네 자리 숫자로 이어서 쓰십시오.",
            "탭 넷" to "화면 아래에 봉사, 나눔, 내 기록, 알림·설정 탭이 있고, 속 화면에서도 늘 보입니다. 속 화면의 뒤로 단추는 위에 하나 있고, 폰의 뒤로 동작을 하셔도 앞 화면으로 갑니다. 첫 화면에서는 앱 밖으로 나가지 않고 여기가 첫 화면이라고 알려 드립니다. 같은 탭을 한 번 더 누르시면 그 탭의 첫 화면으로 갑니다.",
            "긴급통화 받기" to "봉사 탭 맨 위에 있습니다. 자원봉사자나 현장영상해설사 가운데 받으실 역할을 고르고, 별명과 수료 번호(해설사는 협회에 등록한 전화번호)를 적은 뒤 함께하겠습니다를 한 번 누르시면 됩니다. 이때 카메라와 마이크, 알림 허락도 한 번에 받아 두고, 배터리 아끼기에서 자봉을 빼 달라고 한 번 여쭙니다. 그 뒤로는 길손님이 도움을 청하면 폰이 잠겨 있어도 벨소리와 진동이 울리고 받기와 거절이 뜹니다. 받기를 누르시면 곧바로 길손님 카메라 화면과 말소리가 이어집니다. 다른 길눈님이 먼저 받으시면 벨이 멈추고 다른 분께 연결되었다고 알려 드립니다. 실명과 전화번호는 화면에 나오지 않고 별명만 씁니다. 잠시 쉬기를 누르시면 울리지 않습니다. 긴급통화는 이 자봉 앱으로만 받습니다. 자원봉사자와 현장영상해설사는 누구를 고를 수 없게 되어 있고, 받을 수 있는 분 가운데 먼저 받는 분이 연결됩니다. 기회가 고르게 가도록 처음 15초는 최근에 덜 받으신 다섯 분께 먼저 울리고, 그래도 아무도 안 받으면 모든 분께 울립니다. 통화료는 들지 않고 데이터만 씁니다(와이파이에서는 따로 드는 돈이 없음).",
            "가족·지인으로 받기 — 이음 번호" to "길눈을 쓰시는 가족이나 지인이 나를 콕 집어 화상통화를 요청하실 수 있게 등록합니다. 먼저 길눈님이 길눈 설정 탭의 가족·지인 명단에서 이음 번호 받기를 누르면 여섯 자리 숫자가 나옵니다. 이 번호를 전화로 불러 받으십시오. 번호는 30분 동안만 쓰입니다. 자봉 앱 봉사 탭, 긴급통화 받기에서 가족·지인으로 받기를 고르고, 이음 번호와 길눈님이 부르실 내 이름(보기: 큰딸)을 넣고 가족·지인으로 등록하기를 누르시면 끝입니다. 그 뒤로 그 길눈님이 나를 고르시면 이 폰만 벨소리와 진동이 울리고 화면에 그분 이름이 뜹니다. 자원봉사자로도 함께하시는 분은 두 가지가 다 됩니다.",
            "긴급통화 받지 않기" to "점지도만 그려 주시고 통화는 원치 않으시면, 긴급통화 받기 화면의 긴급통화 받지 않기(처음이면 긴급통화는 받지 않겠습니다)를 누르십시오. 어떤 요청도 울리지 않고, 알림 칸의 자봉도 살피기를 멈춥니다. 점지도 그리기는 그대로 쓰십니다. 마음이 바뀌시면 같은 화면의 긴급통화 받기 시작을 누르시면 됩니다.",
            "긴급통화 받기 — 알림 칸의 자봉" to "안드로이드에서는 긴급통화를 받는 동안 알림 칸에 자봉 — 긴급통화를 받고 있습니다가 떠 있습니다. 이것이 5초마다 나스에 길손님의 부름이 왔는지 살핍니다. 알림 칸의 자봉을 지우거나 배터리 아끼기가 자봉을 재우면 늦게 울리거나 울리지 않을 수 있으니, 함께하겠습니다 때 여쭙는 배터리 창에서 허용을 눌러 주십시오. 다시 여시려면 긴급통화 받기 화면의 더 보기 안에 배터리 아끼기에서 자봉 빼기가 있습니다. 폰을 껐다 켜셔도 저절로 다시 기다립니다.",
            "통화 화면" to "받으시면 탭 위에 통화 화면이 덮입니다. 맨 위에 지금 형편, 그 아래 길손님의 목적지, 길손님 카메라 화면, 맨 아래에 통화 마치기 단추가 있습니다. 이어지면 톡백으로 한 번 알려 드리고, 통화 중에는 자봉 말소리를 내지 않습니다. 이어폰이 없으면 스피커로 들립니다. 폰의 뒤로 동작을 하셔도 통화를 마칩니다. 15초 넘게 도와주시면 마칠 때 고맙다는 말씀을 드립니다.",
            "지금 상태 듣기" to "봉사 탭에서 누르시면 자봉 번호, 긴급통화를 받는지, 보폭, 위성이 잘 잡혔는지를 말씀드립니다.",
            "내 보폭 재기" to "봉사 탭에서 엽니다. 10미터를 미리 재 둔 곳에서 10미터 걸어 재기를 누르고 평소 걸음으로 걸은 뒤 다 걸었습니다를 누르시면 보폭을 셈해 폰이 기억합니다. 아시는 보폭을 센티미터로 넣으셔도 됩니다. 한 번 재면 다시 재지 않아도 되고, 원하실 때 다시 잴 수 있습니다.",
            "새로고침" to "알림·설정 탭에서 누르시면 등록 정보와 그려 주신 길 수를 나스에서 다시 받습니다.",
            "저절로 저장" to "현장에서는 늘 의외의 일이 생기므로 1분마다 저절로 저장합니다. 점지도를 그리는 중에는 1분마다, 표시를 남길 때마다, 앱이 뒤로 갈 때 그리던 길을 저장하고, 앱이 꺼졌다 켜지면 그리던 길을 잠깐 멈춤으로 되살려 이어 그리실 수 있습니다.",
            "몸 센서 — 걸음과 방향을 더 정확하게" to "점지도를 그리시는 동안 폰의 가속도계, 자이로, 나침반을 1초에 50번 읽습니다. 발이 땅에 닿을 때마다 한 걸음을 바로 세고, 몸이 몇 도 돌았는지 자이로로 재어 쇠붙이나 건물 옆에서도 방향이 틀어지지 않게 합니다. 걸음마다 시각, 방향, 돈 각도, 위아래 충격, 높이, 멈춤과 걷기 상태를 한 줄씩 남깁니다. 꺾이셨습니까 물음도 이 각도로 가려 다 도신 뒤에 여쭙니다. 다 걸었습니다를 누르시면 폰 걸음 센서로 센 걸음과 몸 센서로 센 걸음을 견주어, 차이가 크면 알려 드립니다. 폰을 손에 드셔도 주머니에 넣으셔도 됩니다. 기록 모양은 아이폰 자봉 앱과 똑같아 어느 폰으로 그린 점지도든 함께 쓰입니다.",
            "점지도 그리기" to "봉사 탭에서 엽니다. 보폭을 먼저 재 두셔야 시작할 수 있습니다. 걷기 시작을 누르시면 출발한 자리 주소를 저절로 적고, 걸음 수와 방향, 위성 자리, 높이를 1초마다 폰 안에 기록합니다. 화면이 꺼지거나 다른 앱을 쓰셔도 알림 칸의 자봉이 붙들어 이어 갑니다. 길을 접어드시면 무슨 길에 접어드셨는지 알려 드립니다. 다 걸었습니다를 누르시면 걸음과 거리, 표시 수를 말씀드리고 도착한 자리 주소를 적어 그린 길로 폰에 담아 둡니다. 그린 길은 같은 화면의 그린 길 펼치기에서 다섯 개씩 보시고, 다섯 줄 아래의 더 보기로 다음 다섯을, 이전 보기로 앞의 다섯을 봅니다. 올리기 전 점검과 올리기는 다음 판에 들어섭니다.",
            "표시 남기기" to "그리는 중 화면 겉에 자주 쓰는 여덟 가지(왼쪽으로 꺾임, 오른쪽으로 꺾임, 올라가는 계단 시작, 내려가는 계단 시작, 계단 끝, 횡단보도 건너기 시작과 끝, 문)가 크게 있고, 다른 표시와 도구 펼치기 안에 나머지 열네 가지와 잠깐 멈춤, 계단 칸수 고치기가 있습니다. 계단과 횡단보도는 시작을 찍으면 끝도 꼭 찍으셔야 하며, 그 사이 칸수와 걸음을 셈해 알려 드립니다. 에스컬레이터, 지하철, 버스는 탈 때와 내릴 때를 찍으시면 그 사이는 걸음으로 재지 않습니다. 문은 딱 찍고 두 걸음 앞에서 한 번 더 찍으셔야 확실한 문이 됩니다.",
            "말로 표시" to "손이 바쁘실 때 말로 표시 단추를 누르고 계단 시작, 왼쪽, 횡단보도 끝, 문처럼 말씀하시면 단추를 누른 그 자리에 표시를 남깁니다. 좌회전, 우회전, 건널목, 승강기 같은 말도 알아듣습니다. 딩동 소리 뒤에 말씀하십시오. 처음 쓰실 때 마이크 허락을 여쭙니다. 받아쓰기는 폰의 구글 음성 인식을 씁니다.",
            "폰이 먼저 여쭘" to "그리는 중에 방향이 크게 바뀌면 왼쪽으로 꺾이셨습니까, 높이가 바뀌면 올라가는 계단입니까처럼 먼저 여쭙고, 계단 중에 높이가 그대로이면 계단이 끝났습니까 하고 여쭙니다. 네라고 말씀하시거나 화면 맨 위에 나오는 네 단추를 누르셔야 표시가 되며, 바뀐 것을 알아챈 그 자리에 남깁니다. 아니오면 남기지 않습니다. 20초 동안 답이 없으면 물음을 거둡니다. 말로 답하시려면 마이크 허락이 있어야 합니다.",
            "나눔 탭" to "그려주세요 게시판, 걸음 나눔 게시판, 물품 나눔 마당이 앱으로 들어서기 전까지는 나눔 탭의 웹 자봉 게시판 열기로 웹 자봉에서 쓰시던 글을 보실 수 있습니다.",
            "판 기록" to "알림·설정 탭의 더 보기 안에 있습니다. 판마다 무엇을 고쳤는지 적어 둡니다."
        )
    }
}

// MARK: 처음 등록 — 아이폰 DeungrokView 와 같음

class DeungrokHwamyeon : JbHwamyeon("자원봉사 등록") {
    private var dangye = 0          // 0 첫 화면, 1 적기, 2 요령, 3 문제, 4 이어 쓰기
    private var ireum = ""
    private var yeonrak = ""
    private var jiyeok = ""
    private var id1365 = ""
    private var jam = ""
    private var beonhoIeo = ""
    private var mi = 0
    private var allim = ""
    private var allimBeon = 0
    private var boinBeon = 0
    private var boneunJung = false

    companion object {
        val YORYEONG = listOf(
            "하나. 처음 그리시기 전에 보폭을 한 번 잽니다. 보폭이 있어야 걸음 수가 정확해집니다.",
            "둘. 출발 전에 하늘이 트인 곳에서 잠시 기다려 위성이 잡히게 합니다. 출발지와 도착지 주소는 길눈이 저절로 적습니다.",
            "셋. 폰은 손에 들거나 주머니에 넣고, 평소 걸음으로 걷습니다. 가방 깊숙이 넣으면 걸음이 잡히지 않습니다.",
            "넷. 꺾이는 곳, 건널목, 턱, 계단, 점자블록이 끊기는 곳은 그 자리에 닿는 순간 표시를 찍습니다. 시작을 찍었으면 끝도 꼭 찍습니다.",
            "다섯. 올리기 전 점검에서 흠이 나오면 그 구간을 다시 걷습니다. 시각장애인의 안전이 이 한 줄에 달려 있습니다."
        )
        class Munje(val q: String, val d: List<String>, val a: Int, val h: String)
        val MUNJE = listOf(
            Munje("첫째 문제. 꺾이는 곳 표시는 언제 찍습니까?", listOf("꺾이는 곳에 닿는 순간 찍습니다", "다 걸은 뒤 한꺼번에 찍습니다"), 0, "꺾이는 곳에 닿는 바로 그 순간 찍어야 시각장애인이 정확한 자리에서 꺾을 수 있습니다."),
            Munje("둘째 문제. 건널목 건너기 시작을 찍었으면 어떻게 합니까?", listOf("시작만 찍어도 됩니다", "다 건너서 끝도 꼭 찍습니다"), 1, "시작과 끝이 짝을 이루어야 건널목의 길이를 알려 드릴 수 있습니다."),
            Munje("셋째 문제. 그리는 동안 폰은 어디에 둡니까?", listOf("손에 들거나 주머니에 넣습니다", "가방 깊숙이 넣습니다"), 0, "가방 깊숙이 넣으면 걸음이 잡히지 않아 걸음 수가 틀립니다.")
        )
    }

    private fun allyeo(t: JabongActivity, m: String) { allim = m; allimBeon += 1; t.dasiGeurigi() }

    /** 폰의 뒤로 동작 — 등록 단계 안에서 앞 단계로 */
    fun dwiro(t: JabongActivity) {
        when (dangye) {
            0 -> Sori.mal("여기가 첫 화면입니다.", MalGeup.JEONGBO)
            2 -> { dangye = 1; allim = ""; t.dasiGeurigi() }
            3 -> { dangye = 2; allyeo(t, "점지도 그리기 요령 다섯 가지입니다.") }
            else -> { dangye = 0; allim = ""; t.dasiGeurigi() }
        }
    }

    override fun chaeugi(t: JabongActivity) {
        if (allim.isNotEmpty()) {
            val v = t.geul(allim, true)
            if (allimBeon != boinBeon) t.chojeom(v)   // 새 알림일 때만 커서를 그 줄로
        }
        boinBeon = allimBeon
        when (dangye) {
            0 -> {
                t.geul("점지도 자원봉사를 시작하기 전에 처음 한 번만 등록합니다. 5분쯤 걸리며, 이 폰이 기억하므로 다시 하지 않으셔도 됩니다.", true)
                t.danchu("등록 시작하기") { dangye = 1; allyeo(t, "이름부터 적어 주십시오.") }
                t.danchu("웹 자봉에서 이미 등록했습니다 — 자봉 번호로 이어 쓰기") { dangye = 4; allyeo(t, "자봉 번호와 네 자리 숫자를 적어 주십시오.") }
            }
            1 -> {
                t.ipryeok("이름", ireum, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME) { ireum = it }
                t.ipryeok("연락처 — 보완 요청과 회신을 드릴 때 씁니다", yeonrak, InputType.TYPE_CLASS_PHONE) { yeonrak = it }
                t.ipryeok("주로 활동하실 지역 — 예를 들어 서울 동대문구", jiyeok, InputType.TYPE_CLASS_TEXT) { jiyeok = it }
                t.ipryeok("1365 아이디 — 비워 두고 나중에 넣으셔도 됩니다", id1365, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) { id1365 = it }
                t.ipryeok("네 자리 숫자 — 나중에 등록 정보를 고치실 때 씁니다", jam, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD) { jam = it }
                t.danchu("다음 — 짧은 교육 듣기") {
                    if (ireum.trim().isEmpty()) { allyeo(t, "이름을 적어 주십시오."); return@danchu }
                    if (yeonrak.count { it.isDigit() } < 9) { allyeo(t, "연락처를 적어 주십시오."); return@danchu }
                    if (jam.count { it.isDigit() } != 4) { allyeo(t, "네 자리 숫자를 정해 주십시오."); return@danchu }
                    dangye = 2
                    allyeo(t, "점지도 그리기 요령 다섯 가지입니다.")
                }
                t.danchu("뒤로 — 등록 첫 화면으로") { dangye = 0; allim = ""; t.dasiGeurigi() }
            }
            2 -> {
                for (y in YORYEONG) t.geul(y, true)
                t.danchu("다 들었습니다 — 확인 문제 풀기") { mi = 0; dangye = 3; allyeo(t, MUNJE[0].q) }
                t.danchu("뒤로 — 적은 것 고치기") { dangye = 1; allim = ""; t.dasiGeurigi() }
            }
            3 -> {
                val m = MUNJE[minOf(mi, MUNJE.size - 1)]
                m.d.forEachIndexed { i, d -> t.danchu(d) { goreum(t, i) } }
                t.danchu("뒤로 — 요령 다시 듣기") { dangye = 2; allyeo(t, "점지도 그리기 요령 다섯 가지입니다.") }
            }
            else -> {
                t.ipryeok("자봉 번호 — 예를 들어 J0001", beonhoIeo, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) { beonhoIeo = it }
                t.ipryeok("네 자리 숫자", jam, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD) { jam = it }
                t.danchu("이어 쓰기") {
                    if (boneunJung) return@danchu
                    boneunJung = true
                    allyeo(t, "찾는 중입니다.")
                    JabongNae.ieoSseugi(beonhoIeo, jam) { why ->
                        boneunJung = false
                        if (why != null) allyeo(t, why)
                        else { allim = ""; Sori.mal("${JabongNae.ireum}님, 이어서 쓰십니다. 자봉 번호는 ${JabongNae.beonho}입니다.") }
                    }
                }
                t.danchu("뒤로 — 등록 첫 화면으로") { dangye = 0; allim = ""; t.dasiGeurigi() }
            }
        }
    }

    private fun goreum(t: JabongActivity, i: Int) {
        if (boneunJung) return
        val m = MUNJE[mi]
        if (i != m.a) { allyeo(t, "다시 생각해 보십시오. " + m.h + " " + m.q); return }
        if (mi + 1 < MUNJE.size) { mi += 1; allyeo(t, "맞습니다. " + MUNJE[mi].q); return }
        boneunJung = true
        allyeo(t, "맞습니다. 등록하는 중입니다.")
        JabongNae.deungrok(ireum.trim(), yeonrak, jiyeok.trim(), id1365.trim(), jam) { why ->
            boneunJung = false
            if (why != null) allyeo(t, why)
            else { allim = ""; dangye = 0; Sori.mal("등록을 마쳤습니다. 자봉 번호는 ${JabongNae.beonho}입니다. 봉사 탭에서 보폭을 먼저 재 주십시오.") }
        }
    }
}
