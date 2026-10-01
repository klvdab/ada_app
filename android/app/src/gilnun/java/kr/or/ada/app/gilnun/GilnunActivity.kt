// 안드로이드 길눈 — 첫 틀과 화면(2.0.0, 빌드 261001-A1, 대표님 승인: 껍데기 앱이 아니라 속까지 앱)
// 아이폰 길눈과 같은 화면 원칙:
//   탭 다섯(길 찾기, 둘러보기, 음악·방송, 나눔, 설정)은 속 화면에도 늘 보임. 탭마다 따로 길을 쌓음
//   속 화면의 뒤로 단추는 위에 하나(탭 바가 아래에 있으므로). 폰의 뒤로 동작도 앞 화면으로 — 앱 밖으로 튀어 나가지 않음
//   화면이 바뀌면 커서를 첫 줄로. 자주 쓰는 것만 겉에 두고 나머지는 펼치기 안에. 새로고침은 설정에 하나
package kr.or.ada.app.gilnun

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** 화면 하나 — 제목과 줄들 */
abstract class Hwamyeon(val jemok: String) {
    abstract fun chaeugi(t: GilnunActivity)
    open fun boilttae(t: GilnunActivity) {}
}

class GilnunActivity : AppCompatActivity() {

    private lateinit var nae: LinearLayout
    private lateinit var seuk: ScrollView
    private lateinit var tabJul: LinearLayout
    private val tabIreum = listOf("길 찾기", "둘러보기", "음악·방송", "나눔", "설정")
    private val gil = List(5) { ArrayList<Hwamyeon>() }
    private var tab = 0
    private var cheotJul: View? = null

    companion object {
        val NAM = Color.rgb(18, 52, 110)
        val NOKSAEK = Color.rgb(255, 204, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Seoljeong.sijak(this)
        Girok.sijak(this)
        Sori.sijak(this)
        Wichi.sijak(this)
        Girok.namgi("app_sijak", mapOf("pan" to Pan.pan, "bild" to Pan.bild, "android" to Build.VERSION.SDK_INT))

        val bburi = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        seuk = ScrollView(this)
        nae = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        seuk.addView(nae, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        bburi.addView(seuk, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        tabJul = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(NAM)
        }
        tabIreum.forEachIndexed { i, ir ->
            val b = Button(this).apply {
                text = ir
                isAllCaps = false
                setTextColor(Color.WHITE)
                setBackgroundColor(NAM)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                minHeight = dp(60)
                setPadding(dp(2), 0, dp(2), 0)
                setOnClickListener { tabGo(i) }
            }
            tabJul.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        bburi.addView(tabJul, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(bburi)

        Sori.tokbaek = { t -> bburi.announceForAccessibility(t) }

        gil[0].add(GilChatgiCheot())
        gil[1].add(JunbiTab("둘러보기", "둘러보기에는 둘레 찾기, 가 볼 곳, 사진 읽어 주기, 카메라 눈이 아이폰 길눈에서 차례로 옮겨 옵니다."))
        gil[2].add(JunbiTab("음악·방송", "음악·방송에는 길 위의 음악, 라디오, TV, 지금 세상 이야기가 아이폰 길눈에서 차례로 옮겨 옵니다."))
        gil[3].add(JunbiTab("나눔", "나눔에는 걸음 나눔과 그려주세요 게시판이 아이폰 길눈에서 차례로 옮겨 옵니다."))
        gil[4].add(SeoljeongCheot())

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { dwiro() }
        })

        boyeojugi()
        heorakCheong()
    }

    override fun onResume() {
        super.onResume()
        Wichi.wiseongDolligi()
    }

    override fun onPause() {
        super.onPause()
        Girok.jeojang()
    }

    // MARK: 허락

    private fun heorakCheong() {
        val p = ArrayList<String>()
        p.add(Manifest.permission.ACCESS_FINE_LOCATION)
        p.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 29) p.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
        val an = p.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (an.isEmpty()) { WichiService.kyeogi(this); return }
        ActivityCompat.requestPermissions(this, an.toTypedArray(), 7)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val gyeolgwa = permissions.indices.associate { permissions[it] to (grantResults.getOrNull(it) == PackageManager.PERMISSION_GRANTED) }
        Girok.namgi("heorak", gyeolgwa.mapKeys { it.key.substringAfterLast('.') })
        Wichi.wiseongDolligi()
        WichiService.kyeogi(this)
        if (!Wichi.heorakItda) Sori.mal("길눈이 길을 안내하려면 위치 허락이 필요합니다. 폰 설정의 앱, 길눈, 권한에서 위치를 허용해 주십시오.")
    }

    // MARK: 화면 옮기기

    fun tabGo(i: Int) {
        if (i == tab && gil[i].size > 1) {
            // 같은 탭을 다시 누르면 그 탭의 첫 화면으로
            while (gil[i].size > 1) gil[i].removeAt(gil[i].size - 1)
        }
        tab = i
        boyeojugi()
    }

    fun yeolgi(h: Hwamyeon) {
        gil[tab].add(h)
        boyeojugi()
    }

    fun dwiro() {
        if (gil[tab].size > 1) {
            gil[tab].removeAt(gil[tab].size - 1)
            boyeojugi()
        } else {
            Sori.mal("여기가 첫 화면입니다.")
        }
    }

    /** 지금 화면을 다시 그림(새로고침·상태가 바뀌었을 때) */
    fun dasiGeurigi() { boyeojugi(false) }

    private fun boyeojugi(chojeom: Boolean = true) {
        nae.removeAllViews()
        cheotJul = null
        val st = gil[tab]
        val h = st.last()
        title = h.jemok
        if (st.size > 1) {
            val ap = st[st.size - 2].jemok
            danchu("뒤로 — $ap") { dwiro() }
        }
        jemok(h.jemok)
        h.chaeugi(this)
        for (i in 0 until tabJul.childCount) {
            val b = tabJul.getChildAt(i) as Button
            val seon = i == tab
            b.setBackgroundColor(if (seon) Color.rgb(8, 30, 70) else NAM)
            b.setTextColor(if (seon) NOKSAEK else Color.WHITE)
            b.contentDescription = tabIreum[i] + if (seon) ", 선택됨" else ""
        }
        seuk.scrollTo(0, 0)
        h.boilttae(this)
        if (chojeom) {
            val c = cheotJul
            c?.post {
                c.requestFocus()
                c.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
                c.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
            }
        }
    }

    // MARK: 줄 만들기

    fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun deohagi(v: View, wi: Int = 0) {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(if (wi == 0) 10 else wi)
        nae.addView(v, lp)
        if (cheotJul == null) cheotJul = v
    }

    fun jemok(t: String) {
        val v = TextView(this).apply {
            text = t
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setTypeface(typeface, Typeface.BOLD)
            if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
            isFocusable = true
        }
        deohagi(v, 4)
    }

    fun geul(t: String, keuge: Boolean = false): TextView {
        val v = TextView(this).apply {
            text = t
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (keuge) 21f else 18f)
            setLineSpacing(0f, 1.15f)
            isFocusable = true
        }
        deohagi(v)
        return v
    }

    fun danchu(t: String, f: () -> Unit): Button {
        val b = Button(this).apply {
            text = t
            isAllCaps = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTextColor(Color.WHITE)
            setBackgroundColor(NAM)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            minHeight = dp(64)
            setPadding(dp(18), dp(8), dp(18), dp(8))
            setOnClickListener { f() }
        }
        deohagi(b)
        return b
    }

    fun ipryeok(t: String, sutja: Boolean): EditText {
        val e = EditText(this).apply {
            hint = t
            contentDescription = t
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            inputType = if (sutja) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_TEXT
            minHeight = dp(60)
        }
        deohagi(e)
        return e
    }

    /** 펼치기 — 누르면 안의 줄들이 보이고 다시 누르면 접힘 */
    fun pyeolchigi(t: String, pyeolchim: Boolean, toggle: () -> Unit) {
        danchu(if (pyeolchim) "$t 접기" else "$t 펼치기") { toggle(); dasiGeurigi() }
    }
}

// MARK: 길 찾기 탭

class GilChatgiCheot : Hwamyeon("길 찾기") {
    override fun chaeugi(t: GilnunActivity) {
        t.danchu("지금 내 자리 다시 듣기") { jariMal() }
        t.geul("목적지 찾기, 걸어갈까요, 차로갈까요, 지하철, 긴급통화서비스가 아이폰 길눈에서 묶음별로 옮겨 옵니다.")
    }

    companion object {
        fun jariMal() {
            val j = Wichi.jigeum
            if (!Wichi.heorakItda) { Sori.mal("위치 허락이 없어 자리를 알 수 없습니다. 폰 설정의 앱, 길눈, 권한에서 위치를 허용해 주십시오."); return }
            if (j == null) { Sori.mal("아직 위치를 받지 못했습니다. 하늘이 트인 곳에서 잠시 기다려 주십시오."); return }
            Sori.mal("자리를 찾는 중입니다.", MalGeup.JEONGBO)
            Tongsin.json("jeom.php", mapOf("a" to "jimyeong", "lat" to String.format(java.util.Locale.US, "%.6f", j.lat), "lon" to String.format(java.util.Locale.US, "%.6f", j.lon))) { o ->
                val juso = o?.optString("juso", "") ?: ""
                var m = if (juso.isNotEmpty()) "지금 $juso 근처입니다." else "주소를 받지 못했습니다."
                m += " 폰은 " + Wichi.bangwiMal(Wichi.nachimban) + (if (Wichi.nachimban >= 0) "을 향하고 있습니다." else ".")
                m += if (j.georeumChu) " 위성이 흐려 걸음으로 이어 셉니다." else if (j.ochae <= 15) " 위성이 잘 잡혀 있습니다." else " 위성 오차가 ${j.ochae.toInt()}미터입니다."
                if (Wichi.georeumHeorak) m += " 오늘 ${Wichi.oneulGeoreum}걸음 걸으셨습니다."
                Girok.namgi("jari_mal", mapOf("juso" to juso.isNotEmpty(), "ochae" to j.ochae.toInt()))
                Sori.mal(m)
            }
        }
    }
}

/** 아직 옮겨 오는 중인 탭 — 한 줄 안내 */
class JunbiTab(jemok: String, private val mal: String) : Hwamyeon(jemok) {
    override fun chaeugi(t: GilnunActivity) { t.geul(mal, true) }
}

// MARK: 설정 탭

class SeoljeongCheot : Hwamyeon("설정") {
    private var deo = false
    override fun chaeugi(t: GilnunActivity) {
        t.danchu("말소리 — 지금 " + (if (Seoljeong.malKyeojim) "켜짐, 누르면 꺼짐" else "꺼짐, 누르면 켜짐")) {
            Seoljeong.malKyeojim = !Seoljeong.malKyeojim
            Sori.mal(if (Seoljeong.malKyeojim) "길눈 말소리를 켰습니다." else "길눈 말소리를 껐습니다. 경고는 그대로 말씀드립니다.", MalGeup.GYEONGGO)
            t.dasiGeurigi()
        }
        t.danchu("말 빠르기 — 지금 " + Seoljeong.bbareugiIreum[Seoljeong.bbareugiDan] + ", 누르면 바뀜") {
            Seoljeong.bbareugiDan = (Seoljeong.bbareugiDan + 1) % 5
            Sori.mal("이 빠르기로 말씀드립니다.", MalGeup.GYEONGGO)
            t.dasiGeurigi()
        }
        t.danchu(if (Seoljeong.bopokJaem) "내 보폭 다시 재기 — 지금 ${(Seoljeong.bopok * 100).toInt()}센티미터" else "내 보폭 재기 — 한 번 재 두면 걸음 수가 정확해집니다") {
            t.yeolgi(BopokHwamyeon())
        }
        t.danchu("새로고침") {
            Wichi.wiseongDolligi()
            WichiService.kyeogi(t)
            Girok.bonaegi()
            t.dasiGeurigi()
            Sori.mal("새로고침했습니다. 안드로이드 길눈 ${Pan.pan}판입니다.")
        }
        t.danchu("도움말") { t.yeolgi(DoumalHwamyeon()) }
        t.pyeolchigi("더 보기", deo) { deo = !deo }
        if (deo) {
            t.danchu("기초 시험 — 위치, 방향, 걸음, 말소리가 도는지 듣기") { t.yeolgi(GichoSiheom()) }
            t.danchu("판 기록 — 안드로이드 길눈 ${Pan.pan}") { t.yeolgi(PanHwamyeon()) }
        }
    }
}

class BopokHwamyeon : Hwamyeon("내 보폭 재기") {
    private var sijakSu = -1
    private var jaelGeori = 20.0
    override fun chaeugi(t: GilnunActivity) {
        t.geul(if (Seoljeong.bopokJaem) "지금 보폭은 ${(Seoljeong.bopok * 100).toInt()}센티미터입니다." else "보폭은 아직 재지 않았습니다. 지금은 65센티미터로 셉니다.")
        if (sijakSu < 0) {
            val e = t.ipryeok("잴 거리(미터) — 스무 걸음 넘게 걸을 만한 거리", true)
            e.setText(jaelGeori.toInt().toString())
            t.danchu("보폭 재기 시작") {
                val m = e.text.toString().trim().toDoubleOrNull()
                if (m == null || m < 3) { Sori.mal("잴 거리를 미터로 적어 주십시오. 스무 걸음 넘게 걸을 만한 거리가 좋습니다."); return@danchu }
                if (!Wichi.georeumHeorak) { Sori.mal("걸음을 세려면 신체 활동 허락이 필요합니다. 폰 설정의 앱, 길눈, 권한에서 신체 활동을 허용해 주십시오."); return@danchu }
                jaelGeori = m
                sijakSu = Wichi.georeumSu
                Sori.mal("${m.toInt()}미터를 평소대로 걸으신 뒤 다 걸었습니다를 눌러 주십시오. 지금부터 셉니다.")
                t.dasiGeurigi()
            }
        } else {
            t.danchu("다 걸었습니다") {
                val n = Wichi.georeumSu - sijakSu
                sijakSu = -1
                if (n < 5) { Sori.mal("걸음이 ${n}밖에 잡히지 않았습니다. 폰을 손에 들거나 주머니에 넣고 다시 해 주십시오."); t.dasiGeurigi(); return@danchu }
                val b = jaelGeori / n
                if (b < 0.2 || b > 1.5) { Sori.mal("보폭이 " + "%.2f".format(b) + "미터로 나와 이상합니다. 거리를 다시 확인하고 한 번 더 해 주십시오."); t.dasiGeurigi(); return@danchu }
                Seoljeong.bopok = b
                Girok.namgi("bopok", mapOf("m" to jaelGeori, "n" to n))
                Sori.mal("${jaelGeori.toInt()}미터를 ${n}걸음에 걸으셨습니다. 보폭은 ${(b * 100).toInt()}센티미터입니다. 이 폰이 기억합니다.")
                t.dasiGeurigi()
            }
        }
        t.geul("한 번만 재 두면 이 폰이 기억합니다. 점지도를 따라 걸을 때 걸음 수를 이 보폭으로 셉니다.")
    }
}

class GichoSiheom : Hwamyeon("기초 시험") {
    override fun chaeugi(t: GilnunActivity) {
        t.danchu("허락 상태 듣기") {
            Sori.mal("위치 허락 " + (if (Wichi.heorakItda) "있음" else "없음") + ", 신체 활동 허락 " + (if (Wichi.georeumHeorak) "있음" else "없음") + ".")
        }
        t.danchu("위성 듣기") {
            val j = Wichi.jigeum
            Sori.mal(if (j == null) "아직 위치를 받지 못했습니다. 받은 횟수 ${Wichi.batunSu}번." else "오차 ${j.ochae.toInt()}미터, " + (if (j.georeumChu) "걸음으로 이어 센 자리" else "위성 자리") + ", 받은 횟수 ${Wichi.batunSu}번.")
        }
        t.danchu("방향 듣기") { Sori.mal("폰은 " + Wichi.bangwiMal(Wichi.nachimban) + (if (Wichi.nachimban >= 0) ", ${Wichi.nachimban.toInt()}도를 향합니다." else ".")) }
        t.danchu("몸 센서 듣기") {
            if (!MomSensor.dollyeo) { Sori.mal("몸 센서가 꺼져 있습니다."); return@danchu }
            val h = MomSensor.hapseong
            Sori.mal("몸 센서로 센 걸음 ${MomSensor.georeumSu}걸음, 안드로이드 걸음 감지 ${MomSensor.gamjiSu}걸음, 몸이 돈 각도 누계 ${MomSensor.nujeokDol.toInt()}도" + (if (h != null) ", 합성 방향 ${h.toInt()}도입니다." else "입니다."))
        }
        t.danchu("걸음 듣기") { Sori.mal("앱을 켠 뒤 ${Wichi.georeumSu}걸음, 오늘 ${Wichi.oneulGeoreum}걸음입니다.") }
        t.danchu("말소리 시험") { Sori.mal("길눈 말소리 시험입니다. 이 말이 들리면 말소리가 잘 됩니다.") }
        t.danchu("기록 보내기") { Girok.bonaegi(); Sori.mal("기록을 나스로 보냅니다.") }
    }
}

class PanHwamyeon : Hwamyeon("판 기록") {
    override fun chaeugi(t: GilnunActivity) {
        t.geul("안드로이드 길눈 ${Pan.pan}판, 빌드 ${Pan.bild}", true)
        for (g in Pan.girok) {
            t.geul("${g.pan}판, ${g.nal}", true)
            for (n in g.naeyong) t.geul(n)
        }
    }
}

class DoumalHwamyeon : Hwamyeon("도움말") {
    private var yeollin = -1
    override fun chaeugi(t: GilnunActivity) {
        DOUMAL.forEachIndexed { i, (jemok, naeyong) ->
            t.danchu(if (yeollin == i) "$jemok 접기" else jemok) {
                yeollin = if (yeollin == i) -1 else i
                t.dasiGeurigi()
            }
            if (yeollin == i) t.geul(naeyong)
        }
    }

    companion object {
        val DOUMAL = listOf(
            "탭 다섯" to "화면 아래에 길 찾기, 둘러보기, 음악·방송, 나눔, 설정 탭이 있고, 속 화면에서도 늘 보입니다. 속 화면에서는 맨 위의 뒤로 단추나 폰의 뒤로 동작으로 앞 화면에 갑니다. 첫 화면에서 뒤로 하시면 여기가 첫 화면이라고 알려 드리고 앱 밖으로 나가지 않습니다. 같은 탭을 한 번 더 누르시면 그 탭의 첫 화면으로 갑니다.",
            "지금 내 자리 다시 듣기" to "길 찾기 탭 맨 위에 있습니다. 누르시면 지금 있는 곳의 주소, 폰이 향한 방향, 위성이 잘 잡혔는지, 오늘 걸으신 걸음을 말씀드립니다.",
            "화면이 꺼져도" to "길눈을 켜면 알림 칸에 길눈이 떠 있습니다. 화면이 꺼지거나 다른 앱을 쓰셔도 길눈이 위치를 이어 봅니다. 위성이 6초 넘게 끊기거나 흐리면 걸음 수와 보폭, 방향으로 자리를 이어 셉니다.",
            "말소리" to "설정 탭에서 길눈 말소리를 켜고 끌 수 있습니다. 끄시면 길눈 말 대신 톡백으로 한 번만 알려 드리고, 경고는 그대로 말씀드립니다. 말 빠르기는 다섯 칸에서 고르십니다.",
            "내 보폭 재기" to "설정 탭에서 엽니다. 잴 거리를 미터로 적고 보폭 재기 시작을 누른 뒤 평소대로 걸으시고, 다 걸었습니다를 누르시면 보폭을 셈해 폰이 기억합니다.",
            "새로고침" to "설정 탭에 하나 있습니다. 위치와 걸음을 다시 열고, 쌓인 기록을 나스로 보내고, 지금 판번호를 말씀드립니다.",
            "기초 시험" to "설정 탭의 더 보기 안에 있습니다. 허락 상태, 위성, 방향, 걸음, 말소리가 제대로 도는지 하나씩 들어 보실 수 있습니다.",
            "판 기록" to "설정 탭의 더 보기 안에 있습니다. 판마다 무엇을 고쳤는지 적어 둡니다.",
            "몸 센서 — 걸음과 방향을 더 정확하게" to "길눈을 켜 두시는 동안 폰의 가속도계와 자이로를 1초에 50번 읽어 걸음과 방향을 잽니다. 자봉 앱이 점지도를 그릴 때와 같은 센서, 같은 셈법이라 그린 분의 걸음과 걸으시는 분의 걸음이 같은 자로 맞습니다. 안드로이드 폰의 걸음 센서는 걸음을 몇 초씩 몰아서 알려 주는 일이 많은데, 몸 센서가 발이 땅에 닿을 때마다 곧바로 세어 그 늦음을 메웁니다. 방향은 몸이 몇 도 돌았는지 자이로로 재고 나침반 쪽으로 천천히 맞추므로 쇠붙이나 건물 옆에서도 틀어지지 않습니다. 위성이 끊겨 걸음으로 자리를 이어 셀 때도 이 방향을 씁니다. 설정 탭 더 보기 안의 기초 시험에서 몸 센서 듣기로 몸 센서가 센 걸음과 방향을 들어 보실 수 있습니다. 따로 켜실 것은 없습니다."
        )
    }
}
