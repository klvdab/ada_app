// 갤럭시 워치 길눈 — 워치 화면 (2.5.0판, 빌드 261002-A8, 대표님 지시: 아이폰 길눈 워치 WatchView.swift 2.33.0~2.37.0 을 갤럭시 워치로)
// 이름 하나, 단추 하나 — 한 줄에 단추 하나, 크게. 톡백으로 쓰기 좋게.
//   처음 열 때 한 번만 "지팡이를 어느 손으로 쥐십니까", "워치는 어느 손목에 차셨습니까"를 여쭘(맨 아래 손 바꾸기로 다시)
//   워치는 지팡이를 쥐지 않은 손에 차는 것이 기본(손목 가리키기에 알맞음). 지팡이 쥔 손에 차시면 지팡이 떨림 기록을 씀.
//   첫 화면: 다음 갈림길, 내 자리, 마지막 안내, 말로 하기, 긴급통화(2.6.0), 음향신호기 위치, 음향신호기 신호, 걷는 동안 깨어 있기,
//     (지팡이를 쥐지 않은 손) 손목 가리키기 · 가리키기 방향 맞추기 / (지팡이 쥔 손) 지팡이 떨림 기록, 도움말, 손 바꾸기
//   상태가 바뀌면 화면을 다시 그리지 않고 그 줄의 글자만 바꿈(톡백 커서가 흔들리지 않게)
//   속 화면(도움말·지팡이 떨림 기록)은 맨 위에 뒤로. 워치의 뒤로 동작도 첫 화면으로 — 첫 화면에서만 앱을 닫음
//   워치 단추: 앱이 쓸 수 있는 옆 단추(STEM 1·2·3)가 있는 워치만 — 1.5초 안에 누른 횟수 1 다음 갈림길, 2 내 자리, 3 말로 하기.
//     웨어 OS는 애플워치 두 번 집기 같은 손가락 동작을 앱에 내어 주지 않고, 갤럭시 워치의 홈·뒤로 단추는 워치가 씀
// 2.6.0판(빌드 261002-A9, 대표님 지시) 첫 화면 말로 하기 아래에 긴급통화 — 걷다가 잘못 눌리지 않게 5초 안에 한 번 더 눌러야 폰 길눈이 요청함.
//   워치 단추 횟수(1·2·3)에는 넣지 않음(애플워치 두 번 집기에 넣지 않은 것과 같은 뜻)
package kr.or.ada.app.wear

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.lang.ref.WeakReference

class WatchActivity : Activity() {
    companion object {
        const val PAN = "2.7.0"
        const val BILD = "261007-W2"
        /** 지금 보이는 워치 길눈 화면(톡백 알림에 씀) */
        var boineun: WeakReference<WatchActivity>? = null
        private val NAM = Color.rgb(18, 52, 110)
        private val NORANG = Color.rgb(255, 204, 0)
    }

    private lateinit var seuk: ScrollView
    private lateinit var nae: LinearLayout
    private var hwamyeon = "cheot"      // cheot 첫 화면 · doumal 도움말 · tteollim 지팡이 떨림 기록
    private var doumalYeollin = -1
    private var cheotJul: View? = null
    private var chojeomJul: View? = null

    // 그 자리에서 글자만 바꾸는 줄
    private var malView: TextView? = null
    private var dapView: TextView? = null
    private var kkaeeoBtn: Button? = null
    private var garikiBtn: Button? = null
    private var siganView: TextView? = null
    private var gurinDamneun = false
    private var gingeupBtn: Button? = null
    private var gingeupHanbeon = false   // 2.6.0 긴급통화를 한 번 누름 — 5초 안에 한 번 더 누르면 요청
    private val gingeupDoedollim = Runnable {
        gingeupHanbeon = false
        gingeupBtn?.text = "긴급통화"
    }

    private val byeonhwaF: () -> Unit = { gaengsin() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WatchModel.sijak(this)
        seuk = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        val dm = resources.displayMetrics
        val dung = resources.configuration.isScreenRound
        nae = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val yeop = if (dung) (dm.widthPixels * 0.1).toInt() else dp(8)
            setPadding(yeop, if (dung) (dm.heightPixels * 0.14).toInt() else dp(8), yeop, (dm.heightPixels * 0.25).toInt())
        }
        seuk.addView(nae, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(seuk)
        WatchModel.byeonhwa = byeonhwaF
        heorakCheong()
        boyeojugi()
        WatchOllim.salpigi(this) { if (hwamyeon == "cheot") boyeojugi() }   // 2.7.0 새 판이 있으면 첫 화면 맨 위에 한 줄
    }

    override fun onResume() {
        super.onResume()
        boineun = WeakReference(this)
        WatchModel.byeonhwa = byeonhwaF
        WatchModel.hwamyeonDolawa()
        seuk.requestFocus()   // 돌리는 테두리(로터리)로 넘기게
        gaengsin()
    }

    override fun onPause() {
        if (boineun?.get() === this) boineun = null
        super.onPause()
    }

    override fun onDestroy() {
        if (WatchModel.byeonhwa === byeonhwaF) WatchModel.byeonhwa = null
        super.onDestroy()
    }

    // MARK: 워치 단추 — 1.5초 안에 누른 횟수

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_STEM_1 || keyCode == KeyEvent.KEYCODE_STEM_2 || keyCode == KeyEvent.KEYCODE_STEM_3) {
            if (event.repeatCount == 0) WatchModel.jipgi()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("옛 뒤로 — 속 화면이면 첫 화면으로")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (hwamyeon != "cheot") { dwiro(); return }
        super.onBackPressed()
    }

    private fun dwiro() {
        hwamyeon = "cheot"
        boyeojugi()
    }

    // MARK: 허락 — 걸음(몸 활동), 알림, 위치(폰이 곁에 없을 때 내 자리)

    private fun heorakCheong() {
        val p = ArrayList<String>()
        p.add(Manifest.permission.ACTIVITY_RECOGNITION)
        p.add(Manifest.permission.ACCESS_FINE_LOCATION)
        p.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
        val an = p.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (an.isNotEmpty()) requestPermissions(an.toTypedArray(), 7)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        WatchModel.hwamyeonDolawa()
    }

    // MARK: 화면 그리기

    private fun boyeojugi() {
        nae.removeAllViews()
        cheotJul = null
        chojeomJul = null
        malView = null; dapView = null; kkaeeoBtn = null; garikiBtn = null; siganView = null; gingeupBtn = null
        gingeupHanbeon = false
        seuk.removeCallbacks(gingeupDoedollim)
        if (WatchModel.jipangiSon.isEmpty() || WatchModel.watchSonmok.isEmpty()) {
            sonMureum()
        } else when (hwamyeon) {
            "doumal" -> doumal()
            "tteollim" -> tteollim()
            else -> cheot()
        }
        seuk.scrollTo(0, 0)
        val c = chojeomJul ?: cheotJul
        c?.post {
            if (!c.isAttachedToWindow) return@post
            c.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
            c.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
        }
    }

    /** 처음 한 번만 — 답하시면 사라짐 */
    private fun sonMureum() {
        if (WatchModel.jipangiSon.isEmpty()) {
            jemok("지팡이를 어느 손으로 쥐십니까")
            danchu("오른손", true) { WatchModel.jipangiSon = "oreun"; WatchModel.haptic("click"); boyeojugi() }
            danchu("왼손", true) { WatchModel.jipangiSon = "oen"; WatchModel.haptic("click"); boyeojugi() }
        } else {
            jemok("워치는 어느 손목에 차셨습니까")
            danchu("왼쪽 손목", true) { gogeum("oen") }
            danchu("오른쪽 손목", true) { gogeum("oreun") }
        }
    }

    private fun gogeum(v: String) {
        WatchModel.watchSonmok = v
        SonmokGariki.mokBatda(SonmokGariki.mok)   // 손이 바뀌면 가리키기를 다시 정함
        val j = WatchModel.jipangiSon
        val gateum = v == j
        WatchModel.speak("지팡이는 ${WatchModel.sonMal(j)}손, 워치는 ${WatchModel.sonMal(v)}쪽 손목입니다. " + (if (gateum)
            "지팡이를 쥔 손이라 걸음 세기와 지팡이 떨림 읽기에 씁니다."
        else "지팡이를 쥐지 않은 손이라 걸음 세기와 손목으로 방향 가리키기에 알맞습니다."), "success")
        hwamyeon = "cheot"
        boyeojugi()
    }

    private fun cheot() {
        // 2.7.0 새 판이 있을 때만 맨 위에(안내와 단추를 한 자리에)
        WatchOllim.sae?.let { s -> danchu("워치 길눈 새 판 ${s.pan} 받기") { WatchOllim.olligi(this) { m -> WatchModel.speak(m) } } }
        danchu("다음 갈림길", true) { WatchModel.daeumDeutgi() }
        danchu("내 자리") { WatchModel.jariDeutgi() }
        danchu("마지막 안내") { WatchModel.malDeutgi() }
        danchu("말로 하기") { WatchModel.malSijak() }
        dapView = geul("").also { it.visibility = View.GONE }
        gingeupBtn = danchu("긴급통화") { gingeupNureum() }
        danchu("음향신호기 위치") { WatchModel.sinhogi(1) }
        danchu("음향신호기 신호") { WatchModel.sinhogi(2) }
        kkaeeoBtn = danchu("걷는 동안 깨어 있기") { WatchModel.kkaeeoDanchu() }
        malView = geul("")
        if (WatchModel.jipangiSon != WatchModel.watchSonmok) {
            // 지팡이를 쥐지 않은 손에 차셨을 때만
            garikiBtn = danchu("손목 가리키기") {
                SonmokGariki.kyeojim = !SonmokGariki.kyeojim
                WatchModel.speak(if (SonmokGariki.kyeojim) "손목 가리키기를 켰습니다." else "손목 가리키기를 껐습니다.")
                gaengsin()
            }
            danchu("가리키기 방향 맞추기") { SonmokGariki.majchugi() }
        } else {
            // 지팡이 쥔 손에 차셨을 때만
            danchu("지팡이 떨림 기록") { hwamyeon = "tteollim"; boyeojugi() }
        }
        danchu("도움말") { hwamyeon = "doumal"; doumalYeollin = -1; boyeojugi() }
        val b = danchu("지팡이 ${WatchModel.sonMal(WatchModel.jipangiSon)}손, 워치 ${WatchModel.sonMal(WatchModel.watchSonmok)}쪽 손목 — 바꾸기") {
            WatchModel.jipangiSon = ""; WatchModel.watchSonmok = ""
            boyeojugi()
        }
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        gaengsin()
    }

    /** 2.6.0 긴급통화 — 처음 누르면 알리고, 5초 안에 한 번 더 누르면 폰 길눈이 요청 */
    private fun gingeupNureum() {
        val b = gingeupBtn ?: return
        seuk.removeCallbacks(gingeupDoedollim)
        if (!gingeupHanbeon) {
            gingeupHanbeon = true
            b.text = "한 번 더 누르면 긴급통화 요청"
            WatchModel.speak("한 번 더 누르시면 긴급통화를 요청합니다.", "notification")
            seuk.postDelayed(gingeupDoedollim, 5000)
            return
        }
        gingeupHanbeon = false
        b.text = "긴급통화"
        WatchModel.gingeup()
    }

    /** 지팡이 떨림 기록 — 바닥을 고르면 곧바로 담기, 담는 중에는 그만 단추 하나 */
    private fun tteollim() {
        gurinDamneun = JipangiTteollim.damneunJung
        if (JipangiTteollim.damneunJung) {
            danchu("그만 — ${JipangiTteollim.pyo} 기록 보내기", true) { JipangiTteollim.geuman() }
            siganView = geul("").also { it.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
            gaengsin()
        } else {
            danchu("뒤로") { dwiro() }
            jemok("지금 걷는 바닥")
            for (b in JipangiTteollim.badakdeul) danchu(b) { JipangiTteollim.sijakHagi(b) }
        }
    }

    private fun doumal() {
        danchu("뒤로") { dwiro() }
        jemok("도움말")
        DOUMAL.forEachIndexed { i, (jm, ny) ->
            danchu(if (doumalYeollin == i) "$jm 접기" else jm) {
                doumalYeollin = if (doumalYeollin == i) -1 else i
                boyeojugi()
            }
            if (doumalYeollin == i) chojeomJul = geul(ny)
        }
    }

    /** 상태가 바뀌면 — 그 줄의 글자만 바꿈 */
    private fun gaengsin() {
        when (hwamyeon) {
            "tteollim" -> {
                if (gurinDamneun != JipangiTteollim.damneunJung) { boyeojugi(); return }
                val c = JipangiTteollim.cho
                siganView?.text = "${c / 60}분 ${c % 60}초째"
            }
            else -> {
                malView?.let { v ->
                    v.text = WatchModel.mal
                    v.contentDescription = "마지막 안내. ${WatchModel.mal}"
                }
                dapView?.let { v ->
                    val d = WatchModel.dapMal
                    v.text = d
                    v.contentDescription = "길눈의 대답. $d"
                    v.visibility = if (d.isEmpty()) View.GONE else View.VISIBLE
                }
                kkaeeoBtn?.let { b ->
                    val on = WatchModel.georeumOn
                    val t = if (on) "깨어 있기 끄기" else "걷는 동안 깨어 있기"
                    if (b.text != t) b.text = t
                    b.stateDescription = if (on) "켜짐" else "꺼짐"
                }
                garikiBtn?.stateDescription = if (SonmokGariki.kyeojim) "켜짐" else "꺼짐"
            }
        }
    }

    // MARK: 줄 만들기

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun deohagi(v: View) {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(6)
        nae.addView(v, lp)
        if (cheotJul == null) cheotJul = v
    }

    private fun jemok(t: String) {
        val v = TextView(this).apply {
            text = t
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_HORIZONTAL
            isAccessibilityHeading = true
            isFocusable = true
        }
        deohagi(v)
    }

    private fun geul(t: String): TextView {
        val v = TextView(this).apply {
            text = t
            setTextColor(Color.rgb(230, 230, 230))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER_HORIZONTAL
            isFocusable = true
        }
        deohagi(v)
        return v
    }

    private fun danchu(t: String, keun: Boolean = false, f: () -> Unit): Button {
        val b = Button(this).apply {
            text = t
            isAllCaps = false
            setTextColor(if (keun) Color.BLACK else Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            minHeight = dp(52)
            minimumHeight = dp(52)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(26).toFloat()
                setColor(if (keun) NORANG else NAM)
            }
            stateListAnimator = null
            setOnClickListener { f() }
        }
        deohagi(b)
        return b
    }

    private val DOUMAL = listOf(
        "다음 갈림길" to "점지도를 따라 걷는 중 다음에 무엇이 있는지, 몇 걸음 앞에서 어느 쪽으로 꺾는지 폰 길눈에 물어 읽어 드립니다.",
        "내 자리" to "지금 있는 곳의 주소와 폰이 향한 방향을 폰 길눈에 물어 읽어 드립니다. 폰이 곁에 없으면 워치의 위성으로 찾습니다.",
        "마지막 안내" to "폰 길눈이 마지막으로 한 말을 다시 읽어 드립니다. 폰이 곁에 없으면 나스에 남은 마지막 안내를 받아 읽습니다. 폰 길눈이 새 안내를 하면 워치가 한 번 떱니다.",
        "말로 하기" to "폰 길눈이 말로 하기 듣기를 엽니다. 폰의 마이크나 이어폰으로 말씀하시면 폰 길눈이 알아듣고 대답합니다.",
        "긴급통화" to "두드리시면 한 번 더 누르시면 긴급통화를 요청합니다라고 알려 드립니다. 5초 안에 한 번 더 두드리셔야 폰 길눈이 화상통화를 요청합니다. 걷다가 잘못 눌리지 않게 하려는 것이며, 워치 단추 횟수에는 넣지 않았습니다. 마지막으로 요청하신 가족·지인 한 분께 가고, 그런 분이 없으면 자원봉사자에게 갑니다. 받으시면 폰의 뒤 카메라와 마이크가 켜집니다. 끊으실 때는 폰 길눈의 통화 끊기 단추를 누르십시오.",
        "음향신호기" to "음향신호기 위치는 리모컨의 유 단추, 음향신호기 신호는 신 단추와 같습니다. 폰이 블루투스로 가까운 음향신호기를 울리고, 받았는지 워치에도 알려 드립니다.",
        "방향 진동" to "점지도를 따라 걷다 꺾어야 할 때 2시에서 5시 방향은 길게 한 번, 7시에서 10시 방향은 짧게 두 번 떱니다. 도착하면 세 번 떱니다.",
        "걷는 동안 깨어 있기" to "손목을 내려도 워치 길눈이 멈추지 않고, 팔 흔들림으로 걸음을 세어 폰 길눈에 보냅니다. 폰이 가방 속이라 걸음을 못 셀 때 폰 길눈이 워치 걸음으로 이어 갑니다. 폰이 점지도 따라 걷기를 시작하면 저절로 켜집니다. 워치 화면 위에 길눈이 떠 있고, 두드리면 이 화면이 열립니다.",
        "손목 가리키기" to "지팡이를 쥐지 않은 손에 차셨을 때 보입니다. 점지도 따라 걷는 중 이 팔을 손등이 위로 오게 앞으로 뻗으시면, 가야 할 쪽을 가리킬 때 1초마다 굵게 떨고, 어긋나면 옮길 쪽을 3시 쪽은 길게 한 번, 9시 쪽은 짧게 두 번으로 알려 드립니다. 팔을 뻗는 순간 한 번 맞습니다, 3시 쪽으로, 9시 쪽으로라고 말씀드립니다.",
        "가리키기 방향 맞추기" to "폰 길눈으로 걷는 중에 서서, 두드린 뒤 이 팔을 몸 정면으로 곧게 뻗고 기다리시면 한 번 맞춰 둡니다.",
        "지팡이 떨림 기록" to "지팡이를 쥔 손에 차셨을 때 보입니다. 바닥 종류를 고르면 곧바로 기록을 시작합니다. 평소처럼 지팡이를 쓰며 걸으시고 그만을 누르시면 폰 길눈으로 보내 협회 연구용으로 올립니다. 5분이 되면 저절로 멈추고, 5초보다 짧으면 버립니다. 나중에 점자블록을 알려 드리는 데 씁니다.",
        "워치 단추" to "앱이 쓸 수 있는 옆 단추가 있는 워치는 그 단추를 1.5초 안에 한 번 누르시면 다음 갈림길, 두 번이면 내 자리, 세 번이면 말로 하기입니다. 누를 때마다 한 번씩 짧게 떱니다. 갤럭시 워치의 홈 단추와 뒤로 단추는 워치가 쓰므로 길눈이 쓰지 못합니다. 그때는 화면의 단추를 쓰십시오.",
        "말소리" to "워치에서 톡백을 쓰시면 톡백이 읽고, 아니면 워치 목소리로 읽습니다.",
        "손 바꾸기" to "첫 화면 맨 아래의 바꾸기를 두드리면 지팡이 쥔 손과 워치 찬 손목을 다시 여쭙니다.",
        "동영상 틀기" to "카톡이나 문자로 받은 동영상을 폰에서 공유를 눌러 길눈으로 보내시면, 폰 길눈의 음악·방송 탭 동영상 틀기에서 갤럭시 워치에서 틀기를 켜 두셨을 때 워치로 보내 틉니다. 큰 동영상은 몇십 초 걸립니다. 다 받으면 워치에 동영상 화면이 열리고, 열리지 않으면 워치 알림의 길눈 동영상을 두드리십시오. 화면을 두드리면 멈춤과 다시 틀기, 아래 그만으로 닫습니다. 다 틀면 짧게 떨고 닫힙니다. 워치로 보내지 못하면 폰에서 틉니다.",
        "새 판 받기" to "워치 길눈을 열 때 새 판이 있는지 살펴, 있으면 첫 화면 맨 위에 워치 길눈 새 판 받기가 나옵니다. 두드리시면 받아서 설치 화면을 엽니다. 처음 한 번은 워치 설정에서 이 앱의 설치 허용이 필요할 수 있습니다.",
        "판 기록" to "갤럭시 워치 길눈 ${PAN}판, 빌드 ${BILD}. 2.7.0판(2026년 10월 7일, 이사장님 허락)에서 폰에서 보낸 동영상 틀기와 워치 스스로 새 판 받기를 더했습니다. 2026년 10월 2일 대표님 지시로 아이폰 길눈의 애플워치 앱을 갤럭시 워치로 옮겼습니다. 같은 날 2.6.0판에서 대표님 지시로 긴급통화 단추를 더했습니다(두 번 눌러 확인). 폰 길눈 2.5.0판 이상과 이어지며, 긴급통화는 폰 길눈 2.6.0판 이상에서 됩니다."
    )
}
