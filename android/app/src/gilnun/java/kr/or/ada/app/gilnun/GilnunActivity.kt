// 안드로이드 길눈 — 첫 틀과 화면(2.0.0, 빌드 261001-A1, 대표님 승인: 껍데기 앱이 아니라 속까지 앱)
// 아이폰 길눈과 같은 화면 원칙:
//   탭 다섯(길 찾기, 둘러보기, 음악·방송, 나눔, 설정)은 속 화면에도 늘 보임. 탭마다 따로 길을 쌓음
//   속 화면의 뒤로 단추는 위에 하나(탭 바가 아래에 있으므로). 폰의 뒤로 동작도 앞 화면으로 — 앱 밖으로 튀어 나가지 않음
//   화면이 바뀌면 커서를 첫 줄로. 자주 쓰는 것만 겉에 두고 나머지는 펼치기 안에. 새로고침은 설정에 하나
// 2.2.0(빌드 261002-A4, 대표님 지시) 길 찾기 탭에 점지도 따라 걷기(JeomHwamyeon.kt). 결과 목록은 첫 결과 줄로 커서를 옮김(chojeomOmgigi)
// 2.3.0(빌드 261002-A6, 대표님 지시) 음향신호기(SinhogiEngine.kt) — 길 찾기 탭에 음향신호기 펼치기(위치 안내·신호 안내 울리기, 찾기),
//   설정에 음향신호기 자동으로 잡기(처음부터 켜짐). 근처 기기 허락은 처음 켤 때 함께 여쭙고, 손으로 울릴 때 없으면 그 자리에서 다시 여쭘
package kr.or.ada.app.gilnun

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
    private var heorakDwi: (() -> Unit)? = null   // 2.3.0 근처 기기 허락을 받으면 이어 할 일

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
        SinhogiEngine.sijak(this)   // 2.3.0 음향신호기 자동으로 잡기(설정에서 끔)
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

    // 2.3.0 화면이 꺼지면 음향신호기는 공용 번호·정해진 이름으로만 살핌(안드로이드가 거르개 없는 훑기를 막음)
    override fun onStart() {
        super.onStart()
        SinhogiEngine.dwiKyeogi(false)
    }

    override fun onStop() {
        super.onStop()
        SinhogiEngine.dwiKyeogi(true)
    }

    // MARK: 허락

    private fun heorakCheong() {
        val p = ArrayList<String>()
        p.add(Manifest.permission.ACCESS_FINE_LOCATION)
        p.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 29) p.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
        if (Seoljeong.sinhogiJadong) for (b in SinhogiEngine.pilyoHeorak()) if (b !in p) p.add(b)   // 2.3.0 음향신호기(근처 기기)
        val an = p.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (an.isEmpty()) { WichiService.kyeogi(this); return }
        ActivityCompat.requestPermissions(this, an.toTypedArray(), 7)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val gyeolgwa = permissions.indices.associate { permissions[it] to (grantResults.getOrNull(it) == PackageManager.PERMISSION_GRANTED) }
        Girok.namgi("heorak", gyeolgwa.mapKeys { it.key.substringAfterLast('.') })
        SinhogiEngine.saerogochim()
        if (requestCode == 8) {
            // 2.3.0 음향신호기를 손으로 울리려다 여쭌 허락
            val f = heorakDwi
            heorakDwi = null
            Wichi.wiseongDolligi()
            WichiService.kyeogi(this)
            if (SinhogiEngine.heorakItda) { if (f != null) sinhogiHagi(f) } else Sori.mal(SinhogiEngine.heorakMal)   // 블루투스가 꺼져 있으면 켜기 창까지
            return
        }
        Wichi.wiseongDolligi()
        WichiService.kyeogi(this)
        if (!Wichi.heorakItda) Sori.mal("길눈이 길을 안내하려면 위치 허락이 필요합니다. 폰 설정의 앱, 길눈, 권한에서 위치를 허용해 주십시오.")
    }

    // MARK: 음향신호기(2.3.0)

    /** 음향신호기를 손으로 쓰기 전에 — 허락이 없으면 여쭙고, 블루투스가 꺼져 있으면 켜기 창을 엶. 다 되면 il */
    fun sinhogiHagi(il: () -> Unit) {
        if (!SinhogiEngine.giginItda) { Sori.mal("이 폰은 블루투스를 쓸 수 없습니다."); return }
        if (!SinhogiEngine.heorakItda) {
            val an = SinhogiEngine.pilyoHeorak().filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
            if (an.isNotEmpty()) {
                heorakDwi = il
                Sori.mal("음향신호기를 찾으려면 " + (if (Build.VERSION.SDK_INT >= 31) "근처 기기" else "위치") + " 허락이 필요합니다. 허용을 눌러 주십시오.")
                ActivityCompat.requestPermissions(this, an.toTypedArray(), 8)
                return
            }
        }
        if (!SinhogiEngine.kyeojim) {
            Sori.mal("폰의 블루투스가 꺼져 있습니다. 블루투스 켜기 창을 엽니다. 허용을 누르시면 이어서 보내 드립니다.")
            SinhogiEngine.kyeojimyeonHagi(il)
            bluetoothKyeogi()
            return
        }
        il()
    }

    /** 블루투스 켜기 창 — 안 되면 블루투스 설정 화면 */
    @android.annotation.SuppressLint("MissingPermission")
    private fun bluetoothKyeogi() {
        try {
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } catch (e2: Exception) {
                Sori.mal("블루투스 설정을 열지 못했습니다. 화면 위에서 아래로 쓸어내려 빠른 설정에서 블루투스를 켜 주십시오.")
            }
        }
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

    /** 지금 보이는 화면(2.2.0 — 늦게 온 결과로 다시 그릴 때, 그 화면이 아직 보이는지 가림) */
    val wiHwamyeon: Hwamyeon get() = gil[tab].last()

    /** 이 줄로 커서를 옮김(2.2.0 — 결과 목록이 나오면 첫 결과 줄로) */
    fun chojeomOmgigi(v: View) {
        v.post {
            if (!v.isAttachedToWindow) return@post
            v.requestFocus()
            v.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
            v.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
        }
    }

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
    private var sinhogiPyeol = false   // 2.3.0 음향신호기 펼치기 — 자동으로 잡기는 접혀 있어도 늘 돎
    override fun chaeugi(t: GilnunActivity) {
        // 2.2.0 따라 걷는 중이면 걷는 화면으로 가는 단추를 맨 위에
        if (JeomEngine.gil != null || JeomEngine.bulleoneun) {
            t.danchu(if (JeomEngine.dochakHam) "목적지에 닿았습니다 — 따라 걷기 화면으로" else "점지도 따라 걷는 중 — 걷는 화면으로") { t.yeolgi(JeomGeotgiHwamyeon()) }
        }
        t.danchu("지금 내 자리 다시 듣기") { jariMal() }
        t.danchu("점지도 따라 걷기") { t.yeolgi(JeomMokrokHwamyeon()) }
        t.pyeolchigi("음향신호기", sinhogiPyeol) { sinhogiPyeol = !sinhogiPyeol }
        if (sinhogiPyeol) {
            t.danchu("음향신호기 위치 안내 울리기") { t.sinhogiHagi { SinhogiEngine.ulligi(1) } }
            t.danchu("음향신호기 신호 안내 울리기") { t.sinhogiHagi { SinhogiEngine.ulligi(2) } }
            t.danchu(if (SinhogiEngine.chatneunJung) "음향신호기 찾기 멈추기" else "음향신호기 찾기 — 가까워질수록 소리가 빨라집니다") {
                if (SinhogiEngine.chatneunJung) {
                    SinhogiEngine.chatgiKkeugi("음향신호기 찾기를 멈췄습니다.")
                    t.dasiGeurigi()
                } else {
                    t.sinhogiHagi {
                        SinhogiEngine.chatgiKyeogi()
                        Sori.mal("음향신호기 찾기를 켭니다. 가까워질수록 소리가 빨라집니다. 다시 누르시면 멈춥니다.")
                        if (t.wiHwamyeon === this) t.dasiGeurigi()
                    }
                }
            }
        }
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
        t.danchu("음향신호기 자동으로 잡기 — 지금 " + (if (Seoljeong.sinhogiJadong) "켜짐, 누르면 꺼짐" else "꺼짐, 누르면 켜짐")) {
            val on = !Seoljeong.sinhogiJadong
            Seoljeong.sinhogiJadong = on
            SinhogiEngine.jadongKyeogi(on)
            Sori.mal(if (on) "음향신호기 자동으로 잡기를 켰습니다. 신호기가 가까이 잡히면 길눈이 스스로 울립니다."
                else "음향신호기 자동으로 잡기를 껐습니다. 길 찾기 탭의 음향신호기 펼치기에서 손으로 울리실 수 있습니다.")
            t.dasiGeurigi()
            if (on) t.sinhogiHagi { }   // 허락이 없거나 블루투스가 꺼져 있으면 그 자리에서
        }
        t.danchu("새로고침") {
            SinhogiEngine.saerogochim()
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
            "점지도 따라 걷기" to "길 찾기 탭에 있습니다. 누르시면 가까운 점지도를 다섯씩 보여 드립니다. 줄마다 길 이름, 길이, 여기서 얼마나 떨어졌는지, 표시 수, 그린 분을 말씀드립니다. 다섯 줄 아래의 더 보기로 다음 다섯을, 이전 보기로 앞의 다섯을 봅니다. 길을 누르시면 이 길 따라 걷기와 거꾸로 걷기를 고르십니다.",
            "거꾸로 걷기(되돌아가기)" to "같은 점지도를 끝에서 처음으로 걷습니다. 왼쪽 꺾임은 오른쪽 꺾임으로, 오름턱은 내림턱으로, 계단과 건널목은 시작과 끝을 바꾸어 알려 드립니다. 목적지에 닿은 뒤에도 걷는 화면의 되돌아가기 단추로 온 길을 그대로 되돌아가실 수 있습니다.",
            "걷는 화면" to "따라 걷기를 시작하면 걷는 화면이 열립니다. 지금 어디쯤인지 듣기는 남은 거리, 위치 오차, 다음에 있는 꺾이는 곳이나 표시를 말씀드립니다. 방금 한 말 다시 듣기, 그만 걷기가 있습니다. 뒤로 가셔도 안내는 이어지고, 길 찾기 탭 맨 위의 걷는 화면으로 단추로 다시 오십니다.",
            "확신음과 벗어남 경고" to "점지도 위를 제대로 디디시면 걸음마다 맑은 높은 소리가 납니다. 반 걸음 비켜나시면 가운데 소리와 함께 왼쪽이나 오른쪽으로 조금 비켜나신다고 한 번 알려 드립니다. 한 걸음 벗어나시면 곧바로 낮은 두 소리와 함께 몇 시 방향으로 몇 걸음 옮기실지 알려 드리고, 돌아오실 때까지 경고음은 2.5초, 말은 5초마다 되풀이합니다. 돌아오시면 오르는 두 소리와 점지도 위로 돌아오셨다고 알려 드립니다. 10미터마다 제대로 가고 있다고 말씀드리고, 서 계시면 5초에 한 번 알려 드립니다. 폰은 가슴 주머니에 세워 넣거나 가슴 앞에 들어 주십시오.",
            "꺾이는 곳과 표시" to "꺾이는 곳은 서른 걸음 앞, 열한 걸음 앞, 그 자리에서 몇 시 방향으로 꺾는지 알려 드립니다. 계단, 건널목, 턱 같은 표시는 서른 걸음 앞, 일곱 미터 앞, 세 미터 앞에서 알려 드립니다. 건널목에서는 멈추시라고 경고하고, 다 건너시면 알려 드립니다. 길눈은 신호 색을 알지 못합니다. 걷기를 시작하면 첫 방향이 맞는지 확인해 드리고, 도셔야 할 쪽과 반대로 크게 도시면 곧바로 알려 드립니다.",
            "위성이 끊길 때 — 걸음으로 이어 셈" to "점지도의 바탕은 걸음입니다. 위성이 6초 넘게 끊기거나 흐리면 걸으신 걸음 수에 보폭을 곱해 점지도 위를 그만큼 나아가신 것으로 셉니다. 설정 탭에서 보폭을 재 두시면 걸음 수가 더 정확해집니다.",
            "끌 수 없는 안전 안내" to "따라 걷기를 시작할 때 길눈은 보조 안내이니 지팡이와 주변 소리를 먼저 확인하시라고 말씀드립니다. 아직 확인 중인 점지도면 조심해서 걸으시라고 덧붙입니다. 폰이 멈췄다 깨어나 안내가 10초 넘게 끊기면 안내가 끊겼다고 알려 드립니다. 이 안내들은 말소리를 꺼 두셔도 말씀드립니다.",
            "도착" to "목적지 30미터쯤과 20미터 안에서 남은 거리를 알려 드리고, 닿으면 도착 소리와 함께 알려 드립니다. 걷는 화면의 첫 줄이 목적지에 닿았습니다, 되돌아가기 단추로 바뀝니다.",
            "몸 센서 — 걸음과 방향을 더 정확하게" to "길눈을 켜 두시는 동안 폰의 가속도계와 자이로를 1초에 50번 읽어 걸음과 방향을 잽니다. 자봉 앱이 점지도를 그릴 때와 같은 센서, 같은 셈법이라 그린 분의 걸음과 걸으시는 분의 걸음이 같은 자로 맞습니다. 안드로이드 폰의 걸음 센서는 걸음을 몇 초씩 몰아서 알려 주는 일이 많은데, 몸 센서가 발이 땅에 닿을 때마다 곧바로 세어 그 늦음을 메웁니다. 방향은 몸이 몇 도 돌았는지 자이로로 재고 나침반 쪽으로 천천히 맞추므로 쇠붙이나 건물 옆에서도 틀어지지 않습니다. 위성이 끊겨 걸음으로 자리를 이어 셀 때도 이 방향을 씁니다. 설정 탭 더 보기 안의 기초 시험에서 몸 센서 듣기로 몸 센서가 센 걸음과 방향을 들어 보실 수 있습니다. 따로 켜실 것은 없습니다.",
            "음향신호기 — 자동으로 잡기" to "건널목 앞에서 폰을 꺼내실 필요가 없습니다. 길눈이 켜져 있으면 화면이 꺼져 있어도 둘레의 블루투스 음향신호기를 늘 살핍니다. 신호기가 가까이 잡히면 위치 안내를 스스로 한 번 울리고, 그 앞에 4초 넘게 머무르시면 신호 안내를 한 번 울립니다. 같은 신호기에는 3분에 한 번만 보내며, 이때 길눈은 말하지 않고 짧게 진동만 합니다. 신호기가 소리를 냅니다. 보행신호 음성안내 장치가 있는 횡단보도 앞이면 5분에 한 번 알려 드립니다. 처음부터 켜져 있고, 설정 탭의 음향신호기 자동으로 잡기에서 끄실 수 있습니다. 폰의 블루투스와 근처 기기 허락이 있어야 합니다. 화면이 꺼져 있을 때는 공용 번호나 정해진 이름을 내보내는 신호기만 잡힙니다.",
            "음향신호기 — 손으로 울리기" to "길 찾기 탭의 음향신호기 펼치기 안에 음향신호기 위치 안내 울리기와 음향신호기 신호 안내 울리기가 있습니다. 누르시면 둘레를 2.5초 살펴 가장 가까운 신호기에 요청을 보내고, 신호기가 받았는지 말씀드립니다. 받으면 세 번, 안 되면 길게 진동합니다. 가까이에 블루투스 음향신호기가 없으면 그렇게 알려 드립니다. 리모컨으로만 울리는 신호기도 있습니다. 블루투스가 꺼져 있으면 블루투스 켜기 창을 열어 드리고, 허용을 누르시면 하시던 요청을 이어서 보냅니다. 근처 기기 허락이 없으면 그 자리에서 여쭙니다.",
            "음향신호기 찾기" to "길 찾기 탭의 음향신호기 펼치기 안에 있습니다. 켜시면 신호기에 가까워질수록 확신음이 빨라지고, 바로 앞이면 음향신호기 바로 앞입니다라고 알려 드린 뒤 위치 안내를 울립니다. 6초 넘게 잡히지 않으면 천천히 둘러보시라고 한 번 알려 드리고, 1분이 지나면 저절로 마칩니다. 다시 누르시면 멈춥니다."
        )
    }
}
