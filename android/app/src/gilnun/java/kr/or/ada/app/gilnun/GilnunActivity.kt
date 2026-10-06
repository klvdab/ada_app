// 안드로이드 길눈 — 첫 틀과 화면(2.0.0, 빌드 261001-A1, 대표님 승인: 껍데기 앱이 아니라 속까지 앱)
// 아이폰 길눈과 같은 화면 원칙:
//   탭 다섯(길 찾기, 둘러보기, 음악·방송, 나눔, 설정)은 속 화면에도 늘 보임. 탭마다 따로 길을 쌓음
//   속 화면의 뒤로 단추는 위에 하나(탭 바가 아래에 있으므로). 폰의 뒤로 동작도 앞 화면으로 — 앱 밖으로 튀어 나가지 않음
//   화면이 바뀌면 커서를 첫 줄로. 자주 쓰는 것만 겉에 두고 나머지는 펼치기 안에. 새로고침은 설정에 하나
// 2.2.0(빌드 261002-A4, 대표님 지시) 길 찾기 탭에 점지도 따라 걷기(JeomHwamyeon.kt). 결과 목록은 첫 결과 줄로 커서를 옮김(chojeomOmgigi)
// 2.3.0(빌드 261002-A6, 대표님 지시) 음향신호기(SinhogiEngine.kt) — 길 찾기 탭에 음향신호기 펼치기(위치 안내·신호 안내 울리기, 찾기),
//   설정에 음향신호기 자동으로 잡기(처음부터 켜짐). 근처 기기 허락은 처음 켤 때 함께 여쭙고, 손으로 울릴 때 없으면 그 자리에서 다시 여쭘
// 2.4.0(빌드 261002-A7, 대표님 지시) 말로 하기(MalHagi.kt) — 길 찾기 탭 맨 위 첫 줄에 큰 말로 하기 단추. 마이크 허락은 처음 누를 때 여쭘.
//   걸으실 때 한 손에 지팡이 — 길눈 화면이 켜져 있으면 이어폰 재생 단추를 길게(0.6초 넘게) 눌러도 열림. 짧게 누르면 음악 앱에 그대로 돌려줌.
//   단추 글자는 화면을 다시 그리지 않고 그 자리에서만 바꿈(톡백 커서가 흔들리지 않게)
// 2.5.0(빌드 261002-A8, 대표님 지시) 갤럭시 워치와 잇기(WatchLink.sijak). 지금 내 자리 한 줄을 워치와 함께 씀(jariMunjang). 도움말에 갤럭시 워치 길눈
// 2.6.0(빌드 261002-A9, 대표님 지시) 긴급통화서비스(GinGeup.kt·GinGeupHwamyeon.kt) — 길 찾기 탭 말로 하기 바로 아래에 긴급통화서비스 단추(급한 일이라 겉에).
//   설정 탭에 가족·지인 명단과 받는 분 화면에 뜰 내 이름. 카메라·마이크 허락은 요청을 누를 때 여쭘(tonghwaHeorak).
//   통화 중 폰의 뒤로 동작은 묻지 않고 통화를 끊고 알려 드림(앱 밖으로 말없이 나가지 않음). 길눈 화면이 닫히면 통화를 끊고 치움
// 2.7.0(빌드 261002-B1, 대표님 지시 「안드로이드에서도 이 원칙 지켜서 동일하게」) 아이폰 길눈의 나머지 기능을 한꺼번에 옮김(묶음 b1~b6)
//   길 찾기 탭 첫 화면을 GilChatgiSae(GilChatgiHwamyeon.kt)로 — 목적지·여정·안내·지하철·버스(YeojeongEngine.sijak). 말로 하기는 2.4.0에 승인된 대로 맨 위 첫 줄
//     (읽지 않은 긴급 공지가 있을 때만 그 한 줄이 위에). 점지도 마저(JeomEngine.appSijak — 이어 걷기, 흔들면, 이어폰·리모컨 단추)
//   둘러보기 탭 DulreoCheot(카메라 눈·둘레 찾기·사진 읽어 주기·현장영상해설 받기), 음악·방송 탭 BangsongCheot, 나눔 탭 NanumCheotHwamyeon
//   설정 탭에 알림, 말하기 설정, 점지도와 걸음, 여기서 점검, 내 서류 보관함, 폰 펼치기(흔들면 자리 번호·현 위치정보 말할 내용), 관리자 시험 — 카메라 눈
//   허락 번호: 7 처음, 8 음향신호기, 9 마이크, 10 화상통화, 11 카메라 눈, 21 폰 알림(GongjiEngine.HEORAK_BEON)
package kr.or.ada.app.gilnun

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
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
import androidx.core.widget.doAfterTextChanged
import java.lang.ref.WeakReference

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
    private var maikDwi: ((Boolean) -> Unit)? = null   // 2.4.0 마이크 허락을 받으면 이어 할 일
    private var tonghwaDwi: (() -> Unit)? = null      // 2.6.0 카메라·마이크 허락을 여쭌 뒤 이어 할 일(허락하지 않으셔도 이어 감)

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
        TalgeotGamji.sijak(this)   // 2.9.0 탈것 저절로 알아채기(걸음 센서·가속도·기압계, 땅속에서도)
        KolJiyeok.sijak(this)   // 2.8.0 콜 번호 지역 알아보기
        NnKol.batgi {}          // 2.8.0 콜 번호표(나스 call.json)를 미리 받아 둠 — 말로 부를 때 바로 쓰게
        SinhogiEngine.sijak(this)   // 2.3.0 음향신호기 자동으로 잡기(설정에서 끔)
        MalHagi.sijak(this)         // 2.4.0 말로 하기 — 나스 알아듣기 사전을 받아 둠
        MalHagi.hwalseong = WeakReference(this)
        WatchLink.sijak(this)       // 2.5.0 갤럭시 워치와 잇기
        GinGeup.sijak(this)         // 2.6.0 긴급통화서비스
        GinGeup.hwalseong = WeakReference(this)
        // 2.7.0 통합(묶음 b1~b6) — 엔진 세우기. 설정·열쇠를 먼저, 여정 엔진은 점지도 이어 걷기보다 앞에(여정 잇는 자리를 채운 뒤 견줌)
        Yeolsoe.sijak(this)         // 나스 음악 열쇠 한 자리
        SeoljeongDeo.sijak(this)    // b6 설정에 더한 값(현 위치정보 말할 내용, 목소리, 선희 목소리)
        Bangsong.sijak(this)        // b5 음악·방송 — 멈춤 지킴이, 말로 하기·긴급통화 중 멈춤(재생기는 처음 틀 때 세움)
        NasMoksori.sijak(this)      // b6 길눈 목소리(선희) — 나스 소리 창고를 미리 깨움
        YeojeongEngine.sijak(this)  // b1 즐겨찾기·안내·지하철 엔진을 세우고 지난 여정을 되살림, 점지도 엔진의 여정 잇는 자리를 채움
        val jeomIeum = JeomEngine.appSijak(this)   // b2 흔들면·이어폰 단추·말로 그린 길을 켜고, 되짚어 나가기를 이어 기억하고, 3시간 안에 걷던 점지도를 이어 걸음
        KameraNun.sijak(this)       // b3 카메라 눈 말소리 설정
        Kamera.hwalseong = WeakReference(this)
        KameraIeum.dalgi()          // b3 카메라 문 찾기를 점지도 문까지(JeomMunKamera)·걸어서 도착(AnnaeEngine.dochakHook)·내 문(NaeMun)에 이음
        DulreoSeol.sijak(this)      // b4 둘러보기 설정(차 안에서 고장이 바뀌면 들려 주기 — 처음엔 켜짐)
        GojangEngine.sijak(this)    // b4 차 안 고장 이야기 — 1분마다, 여정이 타고 가는 중일 때만(지하철 제외), 같은 고장 15분에 한 번
        AnmyeonEngine.sijak(this)   // b4 안면인식 — 등록한 분 목록을 폰 안에서 읽어 둠
        NnButakSangseHwamyeon.wiseongAnnae = { a, ireum, lat, lon -> gotEuroGagi(a, Jangso(ireum, "", lat, lon)) }   // b6 길 부탁하기의 그곳까지 안내 — 둘러보기와 같은 길
        GongjiEngine.sijak(this)    // b6 알림 — 15분마다·앱으로 돌아올 때, 닫혀 있어도 뒤 일꾼이 긴급 공지를 살핌
        GongjiEngine.byeonhwa = {
            val h = wiHwamyeon
            if (h is GilChatgiSae || h is SeoljeongCheot) dasiGeurigi()   // 긴급 공지 한 줄·알림 단추 글자 바꿈
        }
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

        gil[0].add(GilChatgiSae())          // 2.7.0 b1 — 아이폰 GilChatgiView 와 같은 차례(긴급성 순), 말로 하기는 맨 위 첫 줄
        gil[1].add(DulreoCheot())           // 2.7.0 b3·b4 둘러보기 — 아이폰과 같은 차례(급한 것부터), 곁가지는 가는 김에 펼치기 안
        gil[2].add(BangsongCheot())         // 2.7.0 b5 음악·방송 — 길 위의 음악, 라디오 듣기, TV 보기, 지금 세상 이야기, 동영상 틀기
        gil[3].add(NanumCheotHwamyeon())    // 2.7.0 b6 나눔 마당, 걸음 나눔과 게시판, 길 부탁하기
        gil[4].add(SeoljeongCheot())

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 2.6.0 긴급통화 중 폰의 뒤로 동작 — 묻지 않고 통화를 끊고 알려 드림. 통화 화면이면 앞 화면으로도
                if (GinGeup.sangtae != GinGeupSangtae.EOPSEUM) {
                    GinGeup.geumanhagi()
                    if (wiHwamyeon is GinGeupHwamyeon && gil[tab].size > 1) {
                        gil[tab].removeAt(gil[tab].size - 1)
                        boyeojugi()
                    }
                    return
                }
                dwiro()
            }
        })

        boyeojugi()
        if (jeomIeum) cheotHwamyeonEuro(JeomGeotgiHwamyeon())   // 2.7.0 b2 이어 걷기 — 걷는 화면을 엶
        Heundeul.ginGeupYeolgi = { cheotHwamyeonEuro(GinGeupHwamyeon()) }   // 2.7.0 b2 흔들면 긴급통화 열기
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
        MalHagi.meomchum()   // 2.4.0 화면이 가려지면 마이크를 닫음
    }

    override fun onDestroy() {
        if (MalHagi.hwalseong?.get() === this) {
            MalHagi.hwalseong = null
            MalHagi.byeonhwa = null
        }
        // 2.6.0 길눈 화면이 닫히면 통화를 끊고 카메라·마이크를 치움
        if (GinGeup.hwalseong?.get() === this) {
            GinGeup.byeonhwa = null
            GinGeup.dateum()
            GinGeup.hwalseong = null
        }
        // 2.7.0 b3 길눈 화면이 닫히면 카메라 눈을 모두 끔
        if (Kamera.hwalseong?.get() === this) {
            Kamera.modukkeugi(false)
            Kamera.hwalseong = null
        }
        Bangsong.byeonhwa = null          // 2.7.0 b5 — 닫힌 화면을 잡고 있지 않게(소리는 알림 칸의 길눈 방송이 이어 감)
        Bangsong.gisaHwamyeonBoim = null
        super.onDestroy()
    }

    // MARK: 이어폰 단추(2.4.0) — 길게 누르면 말로 하기, 짧게 누르면 음악 앱에 그대로

    private fun iyeopon(keyCode: Int) = keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (JeomRimo.keyBatgi(this, keyCode, event)) return true   // 2.7.0 b2 리모컨 — 익힌 단추는 점지도 걷는 화면에서, 익히기·눌러 보기는 리모컨 배우기 화면에서
        if (iyeopon(keyCode)) return true   // 뗄 때 길이를 보고 정함
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!iyeopon(keyCode)) return super.onKeyUp(keyCode, event)
        if (event.isCanceled) return true
        if (event.eventTime - event.downTime >= 600) {
            Girok.namgi("malhagi_iyeopon")
            MalHagi.dudeurim()
        } else {
            // 짧게 누름 — 받은 단추를 소리 관리자에게 돌려주어 음악 앱이 평소대로 멈추고 다시 틀게
            try {
                val am = getSystemService(AUDIO_SERVICE) as AudioManager
                am.dispatchMediaKeyEvent(KeyEvent(event.downTime, event.downTime, KeyEvent.ACTION_DOWN, keyCode, 0))
                am.dispatchMediaKeyEvent(KeyEvent(event.downTime, event.eventTime, KeyEvent.ACTION_UP, keyCode, 0))
            } catch (e: Exception) {
                Girok.namgi("iyeopon_oryu", mapOf("e" to (e.message ?: "")))
            }
        }
        return true
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
        if (requestCode == GongjiEngine.HEORAK_BEON) {   // 2.7.0 b6 폰 알림 허락(알림 화면의 단추)
            GongjiEngine.heorakGyeolgwa(this)
            return
        }
        if (requestCode == 11) {
            // 2.7.0 b3 카메라 눈에 여쭌 카메라 허락(허락하시면 하시던 카메라 눈을 이어 켬)
            Kamera.heorakDap(this)
            return
        }
        if (requestCode == 10) {
            // 2.6.0 긴급통화에 여쭌 카메라·마이크 허락 — 허락하지 않으셔도 통화는 이어 감(카메라가 없으면 목소리만)
            val f = tonghwaDwi
            tonghwaDwi = null
            f?.invoke()
            return
        }
        if (requestCode == 9) {
            // 2.4.0 말로 하기에 여쭌 마이크 허락
            val f = maikDwi
            maikDwi = null
            f?.invoke(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            return
        }
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

    // MARK: 말로 하기(2.4.0)

    /** 마이크 허락 — 있으면 곧바로 f(참), 없으면 여쭙고 답에 따라 f */
    fun maikHeorak(f: (Boolean) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { f(true); return }
        maikDwi = f
        Sori.mal("말로 하기에는 마이크 허락이 필요합니다. 허용을 눌러 주십시오.")
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 9)
    }

    /** 2.6.0 긴급통화 카메라·마이크 허락 — 있으면 곧바로 f, 없으면 여쭙고 답이 오면 f(여쭙는 중이면 이어 붙임) */
    fun tonghwaHeorak(f: () -> Unit) {
        val an = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (an.isEmpty()) { f(); return }
        val ap = tonghwaDwi
        if (ap != null) { tonghwaDwi = { ap(); f() }; return }
        tonghwaDwi = f
        Sori.mal("화상통화에는 카메라와 마이크 허락이 필요합니다. 허용을 눌러 주십시오.")
        ActivityCompat.requestPermissions(this, an.toTypedArray(), 10)
    }

    /** 2.7.0 b6 i 탭의 첫 화면으로 돌린 뒤 h 를 엶(말로 하기가 다른 탭의 화면을 열 때) */
    fun tabCheotEuro(i: Int, h: Hwamyeon?) {
        tab = i
        while (gil[i].size > 1) gil[i].removeAt(gil[i].size - 1)
        if (h != null) gil[i].add(h)
        boyeojugi()
    }

    /** 길 찾기 탭 첫 화면으로 돌린 뒤 h 를 엶(h 가 없으면 첫 화면만) — 말로 하기가 화면을 열 때(아이폰 GilGil.cheotHwamyeon) */
    fun cheotHwamyeonEuro(h: Hwamyeon?) {
        tab = 0
        while (gil[0].size > 1) gil[0].removeAt(gil[0].size - 1)
        if (h != null) gil[0].add(h)
        boyeojugi()
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

    /** 2.6.0 화면을 그린 뒤 커서가 갈 첫 줄을 이 줄로(뒤로 단추 대신 — 통화 끊기 단추 등) */
    fun chojeomJul(v: View) { cheotJul = v }

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

/** 2.4.0~2.6.0 의 길 찾기 첫 화면 — 2.7.0부터 첫 화면은 GilChatgiSae(GilChatgiHwamyeon.kt). companion 의 jariMal·jariMunjang 은 워치가 씀 */
class GilChatgiCheot : Hwamyeon("길 찾기") {
    private var sinhogiPyeol = false   // 2.3.0 음향신호기 펼치기 — 자동으로 잡기는 접혀 있어도 늘 돎
    override fun chaeugi(t: GilnunActivity) {
        // 2.4.0 첫 줄은 큰 말로 하기 단추 — 듣는 중에 다시 누르면 그만. 글자는 그 자리에서만 바꿈(화면을 다시 그리지 않음)
        val mb = t.danchu(MalHagi.danchuGeul) { MalHagi.dudeurim() }
        mb.minHeight = t.dp(96)
        mb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        mb.contentDescription = "말로 하기 — 누르고 말씀하십시오"
        val dv = t.geul(malDapGeul())
        dv.visibility = if (MalHagi.dapMal.isEmpty()) View.GONE else View.VISIBLE
        MalHagi.byeonhwa = {
            if (t.wiHwamyeon === this) {
                mb.text = MalHagi.danchuGeul
                dv.text = malDapGeul()
                dv.visibility = if (MalHagi.dapMal.isEmpty()) View.GONE else View.VISIBLE
            }
        }
        // 2.6.0 긴급통화서비스 — 급한 일이라 말로 하기 바로 아래 겉에. 요청 중·통화 중이면 통화 화면으로 가는 단추(글자만 그 자리에서 바꿈)
        val gb = t.danchu(ginGeupGeul()) { t.yeolgi(GinGeupHwamyeon()) }
        GinGeup.byeonhwa = {
            if (t.wiHwamyeon === this) {
                val n = ginGeupGeul()
                if (gb.text.toString() != n) gb.text = n
            }
        }
        // 2.2.0 따라 걷는 중이면 걷는 화면으로 가는 단추를 맨 위에
        if (JeomEngine.gil != null || JeomEngine.bulleoneun) {
            t.danchu(if (JeomEngine.dochakHam) "목적지에 닿았습니다 — 따라 걷기 화면으로" else "점지도 따라 걷는 중 — 걷는 화면으로") { t.yeolgi(JeomGeotgiHwamyeon()) }
        }
        t.danchu("지금 내 자리 다시 듣기") { jariMal() }
        t.danchu("점지도 따라 걷기") { t.yeolgi(JeomMokrokHwamyeon()) }
        t.pyeolchigi("음향신호기", sinhogiPyeol) { sinhogiPyeol = !sinhogiPyeol }
        if (sinhogiPyeol) {
            t.danchu("주변 신호기 살피기 — 있는지, 블루투스로 울릴 수 있는지") { t.sinhogiHagi { SinhogiEngine.juByeonSalpigi() } }   // 2.13.0
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
        t.geul("목적지 찾기, 걸어갈까요, 차로갈까요, 지하철이 아이폰 길눈에서 묶음별로 옮겨 옵니다.")
    }

    private fun ginGeupGeul() = when (GinGeup.sangtae) {
        GinGeupSangtae.EOPSEUM -> "긴급통화서비스 — 화상통화 요청"
        GinGeupSangtae.YOCHEONG -> "긴급통화 요청 중 — 통화 화면으로"
        else -> "긴급통화 중 — 통화 화면으로"
    }

    private fun malDapGeul() = "들은 말 — ${MalHagi.deureunMal}. 길눈 — ${MalHagi.dapMal}"

    companion object {
        fun jariMal() {
            jariMunjang({ Sori.mal("자리를 찾는 중입니다.", MalGeup.JEONGBO) }) { m -> Sori.mal(m) }
        }

        /** 2.5.0 지금 내 자리 한 줄을 만듦 — 폰이 말할 때와 갤럭시 워치가 물을 때 같은 말(찾기 시작할 때 할 일은 sijakMal) */
        fun jariMunjang(sijakMal: (() -> Unit)? = null, kkeut: (String) -> Unit) {
            val j = Wichi.jigeum
            if (!Wichi.heorakItda) { kkeut("위치 허락이 없어 자리를 알 수 없습니다. 폰 설정의 앱, 길눈, 권한에서 위치를 허용해 주십시오."); return }
            if (j == null) { kkeut("아직 위치를 받지 못했습니다. 하늘이 트인 곳에서 잠시 기다려 주십시오."); return }
            sijakMal?.invoke()
            Tongsin.json("jeom.php", mapOf("a" to "jimyeong", "lat" to String.format(java.util.Locale.US, "%.6f", j.lat), "lon" to String.format(java.util.Locale.US, "%.6f", j.lon))) { o ->
                val juso = o?.optString("juso", "") ?: ""
                var m = if (juso.isNotEmpty()) "지금 $juso 근처입니다." else "주소를 받지 못했습니다."
                m += " 폰은 " + Wichi.bangwiMal(Wichi.nachimban) + (if (Wichi.nachimban >= 0) "을 향하고 있습니다." else ".")
                m += if (j.georeumChu) " 위성이 흐려 걸음으로 이어 셉니다." else if (j.ochae <= 15) " 위성이 잘 잡혀 있습니다." else " 위성 오차가 ${j.ochae.toInt()}미터입니다."
                if (Wichi.georeumHeorak) m += " 오늘 ${Wichi.oneulGeoreum}걸음 걸으셨습니다."
                Girok.namgi("jari_mal", mapOf("juso" to juso.isNotEmpty(), "ochae" to j.ochae.toInt()))
                kkeut(m)
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
    private var pon = false   // 2.7.0 b6 폰 펼치기
    override fun chaeugi(t: GilnunActivity) {
        // 2.7.0 b6 알림 — 맨 위(아이폰과 같음)
        t.danchu("알림" + (if (GongjiEngine.gingeupSae != null) " — 읽지 않은 긴급 공지가 있습니다" else "")) { t.yeolgi(GongjiHwamyeon()) }
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
        t.danchu("말하기 설정 — 목소리, 얼마나 자세히, 무엇을 말할지") { t.yeolgi(MalSeolHwamyeon()) }   // 2.7.0 b6(카메라 눈 말소리도 이 안에)
        t.danchu(if (Seoljeong.bopokJaem) "내 보폭 다시 재기 — 지금 ${(Seoljeong.bopok * 100).toInt()}센티미터" else "내 보폭 재기 — 한 번 재 두면 걸음 수가 정확해집니다") {
            t.yeolgi(BopokHwamyeon())
        }
        t.danchu("점지도와 걸음 — 나만의 점지도, 걸음 오차, 내 문, 리모컨, 점지도 안내 설정") { t.yeolgi(JeomSeoljeongHwamyeon()) }   // 2.7.0 b2
        t.danchu("음향신호기 자동으로 잡기 — 지금 " + (if (Seoljeong.sinhogiJadong) "켜짐, 누르면 꺼짐" else "꺼짐, 누르면 켜짐")) {
            val on = !Seoljeong.sinhogiJadong
            Seoljeong.sinhogiJadong = on
            SinhogiEngine.jadongKyeogi(on)
            Sori.mal(if (on) "음향신호기 자동으로 잡기를 켰습니다. 신호기가 가까이 잡히면 길눈이 스스로 울립니다."
                else "음향신호기 자동으로 잡기를 껐습니다. 길 찾기 탭의 그 밖에 펼치기에서 손으로 울리실 수 있습니다.")
            t.dasiGeurigi()
            if (on) t.sinhogiHagi { }   // 허락이 없거나 블루투스가 꺼져 있으면 그 자리에서
        }
        // 2.6.0 긴급통화서비스 — 가족·지인 명단과 받는 분 화면에 뜰 내 이름(아이폰 2.4.1 설계도대로 설정 탭에)
        t.danchu("가족·지인 명단 — 등록하고 초대 주소 보내기") { t.yeolgi(JiinMyeongdanHwamyeon()) }
        val ne = t.ipryeok("받는 분 화면에 뜰 내 이름 — 긴급통화 때 보입니다", false)
        ne.setText(GinGeup.naIrum)
        ne.doAfterTextChanged { GinGeup.naIrum = it?.toString() ?: "" }
        t.danchu("새로고침") {
            SinhogiEngine.saerogochim()
            Wichi.wiseongDolligi()
            WichiService.kyeogi(t)
            Girok.bonaegi()
            GongjiEngine.batgi(t)          // 2.7.0 b6 공지를 다시 받음
            Kamera.modukkeugi(false)       // 2.7.0 b3 켜 둔 카메라 눈을 끔
            MalSajeon.bureogi()            // 2.7.0 나스 알아듣기 사전도 새로
            t.dasiGeurigi()
            Sori.mal("새로고침했습니다. 안드로이드 길눈 ${Pan.pan}판입니다.")
        }
        t.danchu("여기서 점검 — 지금 이 자리에서 무엇이 막혔는지 알아보기") { t.yeolgi(YeogiJeomgeomHwamyeon()) }   // 2.7.0 b6
        t.danchu("내 서류 보관함 — 복지카드와 신분증 담아 두기, 복지콜 등록") { t.yeolgi(SeoryuhamHwamyeon()) }    // 2.7.0 b6
        t.pyeolchigi("폰", pon) { pon = !pon }   // 2.7.0 b6 아이폰 "폰 펼치기 — 흔들면 자리 번호, 현 위치정보 말할 내용"
        if (pon) {
            t.danchu("흔들면 자리 번호 — 긴급통화 열기 또는 국가지점번호") { t.yeolgi(HeundeulHwamyeon()) }   // 2.7.0 b2
            t.danchu("현 위치정보 말할 내용") { t.yeolgi(JariMalSeoljeongHwamyeon()) }
        }
        t.danchu("도움말") { t.yeolgi(DoumalHwamyeon()) }
        t.pyeolchigi("더 보기", deo) { deo = !deo }
        if (deo) {
            t.danchu("기초 시험 — 위치, 방향, 걸음, 말소리가 도는지 듣기") { t.yeolgi(GichoSiheom()) }
            t.danchu("관리자 시험 — 카메라 눈(문 찾기, 발 앞 계단·턱 알림)") { t.yeolgi(KameraGwanliHwamyeon()) }   // 2.7.0 b3 — 열쇠가 없으면 열쇠 넣기
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
            "지금 내 자리 듣기" to "길 찾기 탭에서 아무 여정이 없을 때 즐겨찾기 아래에 있고, 여정 중에는 여정 다른 할 일 펼치기 안에 있습니다. 지금 계신 곳의 도로명 주소와 가까운 지하철 출구나 건물을 거리와 함께, 위성 오차, 국가지점번호, 날씨를 말씀드립니다. 무엇을 말할지는 설정 탭의 현 위치정보 말할 내용에서 고르십니다.",
            "화면이 꺼져도" to "길눈을 켜면 알림 칸에 길눈이 떠 있습니다. 화면이 꺼지거나 다른 앱을 쓰셔도 길눈이 위치를 이어 봅니다. 위성이 6초 넘게 끊기거나 흐리면 걸음 수와 보폭, 방향으로 자리를 이어 셉니다.",
            "말소리" to "설정 탭에서 길눈 말소리를 켜고 끌 수 있습니다. 끄시면 길눈 말 대신 톡백으로 한 번만 알려 드리고, 경고는 그대로 말씀드립니다. 말 빠르기는 다섯 칸에서 고르십니다.",
            "내 보폭 재기" to "설정 탭에서 엽니다. 잴 거리를 미터로 적고 보폭 재기 시작을 누른 뒤 평소대로 걸으시고, 다 걸었습니다를 누르시면 보폭을 셈해 폰이 기억합니다.",
            "새로고침" to "설정 탭에 하나 있습니다. 위치와 걸음을 다시 열고, 쌓인 기록을 나스로 보내고, 지금 판번호를 말씀드립니다.",
            "기초 시험" to "설정 탭의 더 보기 안에 있습니다. 허락 상태, 위성, 방향, 걸음, 말소리가 제대로 도는지 하나씩 들어 보실 수 있습니다.",
            "판 기록" to "설정 탭의 더 보기 안에 있습니다. 판마다 무엇을 고쳤는지 적어 둡니다.",
            "비슷한 곡 권하기" to "말로 하기에서 가수와 곡 이름으로 찾으셨는데 꼭 맞는 곡이 없으면, 나스 음악 가운데 이름이 비슷한 곡을 찾아 여쭙니다. 보기를 들면 이문세 첫사랑을 찾으시면, 이문세 노래 가운데 첫사랑은 없습니다, 비슷한 제목으로 옛사랑이 있습니다, 틀까요라고 여쭙고, 네라고 하시면 바로 틉니다. 아니라고 하시면 그만둡니다. 가수 이름을 조금 틀리게 말씀하셔도 비슷한 이름의 가수를 찾아 여쭙니다.",
            "주변 신호기 살피기" to "길 찾기 탭의 그 밖에 펼치기와 설정의 음향신호기 펼치기에 주변 신호기 살피기가 있습니다. 말로 하기에서 주변에 신호기 있어, 신호기 살펴 줘, 블루투스 신호기 있나라고 하셔도 됩니다. 8초 동안 둘레를 살펴, 블루투스로 울릴 수 있는 음향신호기가 몇 대 있는지, 가장 가까운 것이 바로 앞인지 가까이인지 조금 떨어진 곳인지 알려 드리고, 보행신호 음성안내 장치가 있으면 그것도 알려 드립니다. 블루투스 신호기가 잡히지 않으면, 그 근처 신호기가 리모컨 전용이거나 신호기가 없을 수 있다고 알려 드립니다. 리모컨 전용 신호기는 폰이 스스로 알아낼 수 없어, 지자체 설치 자료와 자원봉사자 표시를 붙여 차례로 알려 드릴 예정입니다. 블루투스가 꺼져 있으면 켜 달라고 알려 드립니다.",
            "점지도 따라 걷기" to "길 찾기 탭의 그 밖에 펼치기 안, 가까운 점지도에 있습니다. 목적지를 정하고 걸어가기를 누르셨을 때 맞는 점지도가 있으면 점지도로 걸을지 먼저 여쭙니다. 누르시면 가까운 점지도를 다섯씩 보여 드립니다. 줄마다 길 이름, 길이, 여기서 얼마나 떨어졌는지, 표시 수, 그린 분을 말씀드립니다. 다섯 줄 아래의 더 보기로 다음 다섯을, 이전 보기로 앞의 다섯을 봅니다. 길을 누르시면 이 길 따라 걷기와 거꾸로 걷기를 고르십니다.",
            "거꾸로 걷기(되돌아가기)" to "같은 점지도를 끝에서 처음으로 걷습니다. 왼쪽 꺾임은 오른쪽 꺾임으로, 오름턱은 내림턱으로, 계단과 건널목은 시작과 끝을 바꾸어 알려 드립니다. 목적지에 닿은 뒤에도 걷는 화면의 되돌아가기 단추로 온 길을 그대로 되돌아가실 수 있습니다.",
            "걷는 화면" to "따라 걷기를 시작하면 걷는 화면이 열립니다. 지금 어디쯤인지 듣기는 남은 거리, 위치 오차, 다음에 있는 꺾이는 곳이나 표시를 말씀드립니다. 다음에 무엇이 있습니까, 방금 한 말 다시 듣기, 여기 문제 있어요, 도움 청하기가 있고, 문까지 안내 중에 문이 여럿이면 다른 문으로가 나옵니다. 따라 걷기 다른 할 일 펼치기에 지금 내 자리 듣기, 여기 걸렸어요, 이 길목은 어떻게 생겼습니까, 가까운 버스 정류장, 함께 시험, 그만 걷기가 있습니다. 뒤로 가셔도 안내는 이어지고, 길 찾기 탭의 지금 차례 단추로 다시 오십니다.",
            "확신음과 벗어남 경고" to "점지도 위를 제대로 디디시면 걸음마다 맑은 높은 소리가 납니다. 반 걸음 비켜나시면 가운데 소리와 함께 왼쪽이나 오른쪽으로 조금 비켜나신다고 한 번 알려 드립니다. 한 걸음 벗어나시면 곧바로 낮은 두 소리와 함께 몇 시 방향으로 몇 걸음 옮기실지 알려 드리고, 돌아오실 때까지 경고음은 2.5초, 말은 5초마다 되풀이합니다. 돌아오시면 오르는 두 소리와 점지도 위로 돌아오셨다고 알려 드립니다. 정한 거리마다 제대로 가고 있다고 말씀드리고(설정 탭 점지도와 걸음의 점지도 안내 설정 펼치기에서 5미터, 10미터, 20미터 가운데 고르십니다), 서 계시면 5초에 한 번 알려 드립니다. 폰은 가슴 주머니에 세워 넣거나 가슴 앞에 들어 주십시오.",
            "꺾이는 곳과 표시" to "꺾이는 곳은 서른 걸음 앞, 열한 걸음 앞, 그 자리에서 몇 시 방향으로 꺾는지 알려 드립니다. 계단, 건널목, 턱 같은 표시는 서른 걸음 앞, 일곱 미터 앞, 세 미터 앞에서 알려 드립니다. 건널목에서는 멈추시라고 경고하고, 다 건너시면 알려 드립니다. 길눈은 신호 색을 알지 못합니다. 걷기를 시작하면 첫 방향이 맞는지 확인해 드리고, 도셔야 할 쪽과 반대로 크게 도시면 곧바로 알려 드립니다.",
            "위성이 끊길 때 — 걸음으로 이어 셈" to "점지도의 바탕은 걸음입니다. 위성이 6초 넘게 끊기거나 흐리면 걸으신 걸음 수에 보폭을 곱해 점지도 위를 그만큼 나아가신 것으로 셉니다. 설정 탭에서 보폭을 재 두시면 걸음 수가 더 정확해집니다.",
            "끌 수 없는 안전 안내" to "따라 걷기를 시작할 때 길눈은 보조 안내이니 지팡이와 주변 소리를 먼저 확인하시라고 말씀드립니다. 아직 확인 중인 점지도면 조심해서 걸으시라고 덧붙입니다. 폰이 멈췄다 깨어나 안내가 10초 넘게 끊기면 안내가 끊겼다고 알려 드립니다. 이 안내들은 말소리를 꺼 두셔도 말씀드립니다.",
            "도착" to "목적지 30미터쯤과 20미터 안에서 남은 거리를 알려 드리고, 닿으면 도착 소리와 함께 알려 드립니다. 걷는 화면의 첫 줄이 목적지에 닿았습니다, 되돌아가기 단추로 바뀝니다.",
            "몸 센서 — 걸음과 방향을 더 정확하게" to "길눈을 켜 두시는 동안 폰의 가속도계와 자이로를 1초에 50번 읽어 걸음과 방향을 잽니다. 자봉 앱이 점지도를 그릴 때와 같은 센서, 같은 셈법이라 그린 분의 걸음과 걸으시는 분의 걸음이 같은 자로 맞습니다. 안드로이드 폰의 걸음 센서는 걸음을 몇 초씩 몰아서 알려 주는 일이 많은데, 몸 센서가 발이 땅에 닿을 때마다 곧바로 세어 그 늦음을 메웁니다. 방향은 몸이 몇 도 돌았는지 자이로로 재고 나침반 쪽으로 천천히 맞추므로 쇠붙이나 건물 옆에서도 틀어지지 않습니다. 위성이 끊겨 걸음으로 자리를 이어 셀 때도 이 방향을 씁니다. 설정 탭 더 보기 안의 기초 시험에서 몸 센서 듣기로 몸 센서가 센 걸음과 방향을 들어 보실 수 있습니다. 따로 켜실 것은 없습니다.",
            "음향신호기 — 자동으로 잡기" to "건널목 앞에서 폰을 꺼내실 필요가 없습니다. 길눈이 켜져 있으면 화면이 꺼져 있어도 둘레의 블루투스 음향신호기를 늘 살핍니다. 신호기가 가까이 잡히면 위치 안내를 스스로 한 번 울리고, 그 앞에 4초 넘게 머무르시면 신호 안내를 한 번 울립니다. 같은 신호기에는 3분에 한 번만 보내며, 이때 길눈은 말하지 않고 짧게 진동만 합니다. 신호기가 소리를 냅니다. 보행신호 음성안내 장치가 있는 횡단보도 앞이면 5분에 한 번 알려 드립니다. 처음부터 켜져 있고, 설정 탭의 음향신호기 자동으로 잡기에서 끄실 수 있습니다. 폰의 블루투스와 근처 기기 허락이 있어야 합니다. 화면이 꺼져 있을 때는 공용 번호나 정해진 이름을 내보내는 신호기만 잡힙니다.",
            "음향신호기 — 손으로 울리기" to "길 찾기 탭의 그 밖에 펼치기 안에 음향신호기 위치 안내 울리기와 음향신호기 신호 안내 울리기가 있습니다. 누르시면 둘레를 2.5초 살펴 가장 가까운 신호기에 요청을 보내고, 신호기가 받았는지 말씀드립니다. 받으면 세 번, 안 되면 길게 진동합니다. 가까이에 블루투스 음향신호기가 없으면 그렇게 알려 드립니다. 리모컨으로만 울리는 신호기도 있습니다. 블루투스가 꺼져 있으면 블루투스 켜기 창을 열어 드리고, 허용을 누르시면 하시던 요청을 이어서 보냅니다. 근처 기기 허락이 없으면 그 자리에서 여쭙니다.",
            "말로 하기" to "길 찾기 탭 맨 위의 첫 줄이 말로 하기 단추입니다. 읽지 않은 긴급 공지가 있을 때만 그 한 줄이 위에 옵니다. 누르시면 길눈이 네 하고 말씀을 기다립니다. 말소리를 꺼 두셨으면 딩동 소리 뒤에 말씀하십시오. 듣는 중에 다시 누르시면 그만둡니다. 듣는 동안에는 길눈이 하던 말을 멈추고 안내를 잠시 맡아 두었다가 말씀이 끝나면 이어서 드립니다. 위험 경고는 기다리지 않고 곧바로 말씀드립니다. 대답을 찾는 데 1초 넘게 걸리면 잠깐만 기다려 주세요라고 알려 드리고, 못 알아들으면 다시 한번 말씀해 주세요라고 한 번 더 여쭙니다. 묻는 말에는 마이크를 한 번 저절로 엽니다. 처음 쓰실 때 마이크 허락을 여쭙니다. 받아쓰기는 폰의 구글 음성 인식을 씁니다.",
            "말로 하기 — 할 수 있는 말" to "지금 어디야. 약수역 가자처럼 가실 곳. 가까운 점지도 찾아 줘. 점지도를 따라 걸을 때는 다음에 무엇, 다음 갈림길, 어디쯤이야, 그만 걷기, 도착하면 되돌아가자. 신호기 울려 줘, 신호 알려 줘, 신호기 찾아 줘. 도와줘, 긴급통화, 해설사 불러 줘, 또는 가족 이름과 화상통화. 날씨 어때. 몇 시야. 말 빠르게, 말 느리게. 말소리 꺼, 말소리 켜. 다시 말해. 그만. 하던 일 멈춰. 여정 끝. 새로고침. 걸어서 가자, 차로 가자, 지하철로 가자, 버스로 가자. 차에 탔어, 내렸어. 얼마나 걸려, 지금 가는 길. 도착했어. 즐겨찾기 목록, 즐겨찾기에 담아 줘. 점지도로, 위성으로. 점지도를 따라 걸을 때는 여기 문제 있어, 여기 걸렸어, 길목, 정류장, 다른 문. 길 기억해 줘. 되짚어 나가자. 말로 그린 길. 음성유도기 어디 있어. QR 찾아 줘. 글자 읽어 줘. 사람 있어. 빛 알려 줘. 바코드 읽어 줘. 무슨 색이야. 얼마짜리야. 이게 뭐야. 가리키는 거 읽어 줘. 근처 약국. 축제 알려 줘. 고장 이야기. 마실 가자. 사진 읽어 줘. 안면인식. 음악 틀어 줘, 트롯 틀어 줘, 또는 가수나 곡 이름. 다음 곡, 이전 곡. 무슨 곡이야. 이어서 틀어. 고장 노래 틀어 줘. 라디오 틀어 줘. KBS 1라디오 틀어 줘. TV 틀어 줘. 뉴스 들려줘. 장애 소식, 속보, 경제 뉴스. 기분이 꿀꿀해. 음악 꺼, 라디오 꺼. 목소리 바꿔. 현장영상해설 받고 싶어. 도움말이라고 하시면 이 말들을 읽어 드립니다. 아직 안드로이드 길눈에 없는 기능을 말씀하시면 그렇다고 알려 드리고 기록해 둡니다.",
            "말로 하기 — 가실 곳 말하기" to "약수역 가자처럼 가실 곳을 말씀하시면 가까운 점지도 가운데 그곳으로 가는 길을 찾습니다. 점지도의 도착지나 이름이 맞으면 약수역까지 점지도를 따라 걸을까요라고 여쭙고, 출발지가 맞으면 거꾸로 걷는 되돌아가는 점지도로 여쭙니다. 시작점이 떨어져 있으면 몇 시 방향, 얼마나 떨어졌는지 함께 알려 드립니다. 네라고 하시면 곧바로 따라 걷기를 시작하고 걷는 화면을 엽니다. 아니오라고 하시면 다음 길을 말씀드립니다. 이름으로 맞는 점지도가 없으면 나스에서 그곳을 찾아 몇 시 방향, 얼마나 떨어졌는지 알려 드리고, 끝이 그곳 가까이 닿는 점지도가 있으면 여쭙니다.",
            "말로 하기 — 이어폰 단추" to "걸으실 때는 한 손에 지팡이를 드시므로, 길눈 화면이 켜져 있으면 이어폰의 재생 단추를 길게, 0.6초 넘게 누르셔도 말로 하기가 열립니다. 짧게 누르시면 평소처럼 음악이 멈추고 다시 나옵니다. 점지도를 따라 걷거나 되짚어 나가거나 말로 그린 길을 걷는 동안에는 화면이 꺼져 있어도 재생 단추를 길게 누르시면 말로 하기가 열립니다.",
            "긴급통화서비스" to "길 찾기 탭 첫 화면의 말로 하기 바로 아래에 긴급통화서비스 단추가 있습니다. 세 갈래로 화상통화를 청하실 수 있습니다. 가족·지인은 고르신 한 분께만, 자원봉사자와 현장영상해설사는 지금 받으실 수 있는 모든 분께 신호가 갑니다. 먼저 한마디를 적어 두시면 받는 분 화면에 뜹니다. 가족·지인 명단과 내 이름은 설정 탭에서 다룹니다. 지금 계신 곳과, 점지도를 따라 걷는 중이면 그 길의 도착지도 함께 갑니다. 받으시면 누르지 않으셔도 뒤 카메라와 마이크가 켜지고 통화가 이어지며, 이어폰이 없으면 스피커로 들립니다. 1분 30초 동안 기다리며, 받을 분이 없거나 받지 않으시면 알려 드립니다. 가족·지인이 받지 않으시면 자원봉사자와 현장영상해설사에게 요청할까요 하고 여쭙고, 바로 그 자리의 예, 요청합니다 단추 한 번이면 두 갈래에 함께 호출합니다. 받는 분은 모두 자봉 앱으로 받으시며, 자원봉사자와 현장영상해설사는 기회가 고르게 가도록 처음 15초는 최근에 덜 받으신 다섯 분께 먼저, 그다음 모든 분께 울립니다. 통화료는 들지 않고 데이터만 씁니다. 곧바로 잇지 못할 때 거치는 영상 다리 주소는 나스에서 받아 쓰므로, 다리를 옮겨도 앱을 새로 받으실 필요가 없습니다. 통화 중에는 길눈이 말하지 않고 화면이 꺼지지 않습니다. 끊으실 때는 맨 위의 통화 끊기 단추를 누르시거나 폰의 뒤로 동작을 하십시오. 처음 쓰실 때 카메라와 마이크 허락을 여쭙니다.",
            "가족·지인 명단 — 이음 번호로 등록" to "설정 탭의 가족·지인 명단을 여시고 이음 번호 받기를 누르십시오. 여섯 자리 숫자를 읽어 드립니다. 이 번호를 전화로 가족이나 지인에게 불러 주십시오. 번호는 30분 동안 한 번만 쓰입니다. 그분이 자봉 앱을 깔고 봉사 탭, 긴급통화 받기, 가족·지인으로 받기에서 이음 번호와 부르실 이름을 넣으시면 곧바로 명단에 들어오고, 길눈이 그분이 등록하셨다고 알려 드립니다. 번호를 다시 들으시려면 같은 자리의 다시 듣기를 누르십시오. 주소나 문자를 보내실 일은 없습니다. 등록된 분의 이름을 누르시면 명단에서 빼기가 있습니다. 받는 분 화면에 뜰 내 이름은 설정 탭의 가족·지인 명단 바로 아래 칸에 적으십시오. 웹 길눈, 아이폰 길눈과 같은 명부를 씁니다.",
            "긴급통화가 이어지지 않을 때" to "도움 요청이 통신이 약해 나스의 답을 받지 못해도 100초가 지나면 요청을 마치고 알려 드립니다. 상대가 받으신 뒤 40초 안에 통화가 이어지지 않아도 마치고 다시 요청하시라고 알려 드립니다. 연결이 막히면 한 번 더 이어 보고, 그래도 안 되면 알려 드립니다. 통화 중에는 길눈 말소리를 내지 않는 대신 경고가 생기면 길게 진동합니다. 길눈 화면을 닫으시면 통화도 끊깁니다.",
            "갤럭시 워치 길눈 — 긴급통화" to "워치 첫 화면의 긴급통화를 두드리시면 한 번 더 누르시면 긴급통화를 요청합니다라고 알려 드립니다. 5초 안에 한 번 더 두드리셔야 폰 길눈이 요청합니다. 걷다가 잘못 눌리지 않게 하려는 것이며, 워치 단추 횟수에는 넣지 않았습니다. 마지막으로 요청하신 가족·지인 한 분께 갑니다. 그런 분이 없을 때 받겠다고 하신 분이 한 분뿐이면 그분께, 아니면 자원봉사자에게 갑니다. 폰 길눈의 긴급통화서비스 화면이 함께 열리며, 끊으실 때는 폰에서 끊으십시오.",
            "음향신호기 찾기" to "길 찾기 탭의 그 밖에 펼치기 안에 있습니다. 켜시면 신호기에 가까워질수록 확신음이 빨라지고, 바로 앞이면 음향신호기 바로 앞입니다라고 알려 드린 뒤 위치 안내를 울립니다. 6초 넘게 잡히지 않으면 천천히 둘러보시라고 한 번 알려 드리고, 1분이 지나면 저절로 마칩니다. 다시 누르시면 멈춥니다.",
            "갤럭시 워치 길눈 — 잇기와 첫 화면" to "갤럭시 워치에 워치 길눈을 깔면 폰 길눈과 저절로 이어집니다. 폰 길눈이 켜져 있어야 합니다. 처음 여실 때 한 번만 지팡이를 어느 손으로 쥐시는지, 워치를 어느 손목에 차셨는지 여쭙니다. 워치 화면에는 다음 갈림길, 내 자리, 마지막 안내, 말로 하기, 긴급통화, 음향신호기 위치, 음향신호기 신호, 걷는 동안 깨어 있기가 한 줄에 하나씩 있고, 맨 아래에 도움말과 손 바꾸기가 있습니다.",
            "갤럭시 워치 길눈 — 안내와 진동" to "폰 길눈이 한 말은 워치의 마지막 안내로 넘어갑니다. 다음 갈림길은 점지도를 따라 걷는 중 다음에 무엇이 있는지 읽어 드립니다. 꺾어야 할 때 워치가 오른쪽은 길게 한 번, 왼쪽은 짧게 두 번 떨고, 도착하면 세 번 떱니다. 워치에서 톡백을 쓰시면 톡백이 읽고, 아니면 워치 목소리로 읽습니다. 폰이 곁에 없으면 나스에 남은 마지막 안내를 받아 읽고, 내 자리는 워치의 위성으로 찾습니다.",
            "갤럭시 워치 길눈 — 걷는 동안 깨어 있기" to "폰 길눈으로 점지도 따라 걷기를 시작하면 워치도 저절로 깨어 있기를 켭니다. 워치 화면 위에 길눈이 떠 있고, 손목을 내려도 꺼지지 않으며, 팔 흔들림으로 걸음을 세어 폰에 보냅니다. 폰이 가방 속에 있어 4초 넘게 걸음을 못 세면 폰 길눈이 워치 걸음으로 이어 셉니다. 워치 화면의 걷는 동안 깨어 있기 단추로 손수 켜고 끌 수도 있습니다.",
            "갤럭시 워치 길눈 — 손목 가리키기" to "지팡이를 쥐지 않은 손에 워치를 차셨을 때 씁니다. 점지도를 따라 걷는 중 워치 찬 팔을 손등이 위로 오게 앞으로 뻗으시면, 가야 할 쪽을 가리킬 때 1초마다 굵게 떨고, 어긋나면 팔을 옮길 쪽을 오른쪽은 길게 한 번, 왼쪽은 짧게 두 번으로 알려 드립니다. 팔을 뻗는 순간 한 번 맞습니다, 오른쪽으로, 왼쪽으로라고 말씀드립니다. 어긋나게 느껴지시면 따라 걷는 중에 서서 가리키기 방향 맞추기를 누르고 팔을 몸 정면으로 곧게 뻗어 기다리십시오.",
            "갤럭시 워치 길눈 — 지팡이 떨림 기록" to "지팡이를 쥔 손에 워치를 차셨을 때 씁니다. 연구 1단계로, 바닥 종류를 고르면 손목 떨림을 1초에 100번 담습니다. 평소처럼 지팡이를 쓰며 걸으시고 그만을 누르시면 폰 길눈으로 보내고, 폰 길눈이 협회 나스로 올립니다. 5분이 되면 저절로 멈추고, 5초보다 짧으면 버립니다. 나중에 점자블록을 알려 드리는 데 씁니다.",
            "갤럭시 워치 길눈 — 워치 단추" to "워치 옆에 앱이 쓸 수 있는 단추가 있는 워치는 그 단추를 1.5초 안에 한 번 누르시면 다음 갈림길, 두 번이면 내 자리, 세 번이면 말로 하기입니다. 누를 때마다 한 번씩 짧게 떱니다. 갤럭시 워치의 홈 단추와 뒤로 단추는 워치가 쓰므로 길눈이 쓰지 못합니다. 그런 워치에서는 화면의 단추를 쓰십시오. 애플워치의 두 번 집기 같은 손가락 동작은 웨어 OS가 앱에 내어 주지 않아 아직 없습니다.",
            // 2.7.0 통합(묶음 b1~b6) — 아이폰 길눈 도움말과 같은 말(안드로이드에 맞게 손본 곳만 다름)
            "목적지 찾기" to "길 찾기 탭의 어디로 가실까요 칸(말로 하기와 긴급통화서비스 단추 아래)에 가실 곳의 이름이나 주소를 적고 엔터(키보드의 검색)를 누르십시오. 가까운 곳부터 다섯 곳씩 나오고 커서가 첫 줄로 갑니다. 더 보시려면 더 보기를 누르십시오. 고르시면 걸어가기, 차로 가기, 지하철로 가기, 버스로 가기, 즐겨찾기에 담기 단추가 나옵니다. 받아쓰기(키보드의 마이크)로 말씀하셔도 됩니다.",
            "하던 일 멈추기" to "길 찾기 탭에서 안내나 점지도 따라 걷기, 묻던 말 같은 하던 일이 있으면 말로 하기와 긴급통화서비스 단추 바로 아래에 하던 일 멈추기 단추가 나옵니다. 누르시면 길 안내, 따라 걷기, 지하철 안내, 되짚어 나가기 안내, 묻던 말, 음향신호기 찾기, 카메라 눈, 길눈의 말을 모두 멈추고 하던 일을 멈췄습니다, 어디로 가실까요 하고 한 번 말씀드립니다. 음악과 방송은 멈추지 않습니다. 말로 하기에서는 하던 일 멈춰, 다 멈춰, 모두 멈춰라고 말씀하십시오. 새 목적지를 고르시거나 말씀하시면 하던 안내는 저절로 멈추고 새 목적지로 바뀝니다.",
            "즐겨찾기 — 담기와 지우기" to "목적지를 고른 화면에서 즐겨찾기에 담기를 누르시면 담깁니다. 지금 계신 자리를 담으시려면 즐겨찾기 화면의 펼치기에서 이름을 적고 담기를 누르십시오. 주소와 함께 담기고, 폰 안에만 있습니다. 지우실 때는 그 줄에서 톡백 동작 메뉴(위로 쓸었다 오른쪽, 또는 세 손가락 두드리기)를 열어 즐겨찾기에서 지우기를 고르시거나, 그 줄을 길게 누르십시오. 요즘 가신 곳이 맨 위로 옵니다.",
            "걷는 안내" to "걸어가기를 누르시면 목적지까지 남은 거리와 시계 방향을 말씀드립니다. 가까워질수록 더 자주 말하고, 방향이 틀어지면 바로 알려 드리며 폰과 워치가 돌 쪽으로 떨립니다. 제대로 가고 계시면 25초마다 짧은 확신음이 납니다. 사거리와 갈림길에 닿으면 어느 길과 어느 길이 만나는 곳인지 알려 드립니다. 40미터 안에 들면 곧 도착을, 닿으면 도착 소리와 함께 도착했습니다를 말씀드립니다. 맞는 점지도가 있으면 점지도로 걸을지 먼저 여쭙고, 20초 안에 고르지 않으시면 점지도로 걷습니다.",
            "차 안 안내" to "차로 가기나 차에 탔습니다를 누르시면 차 안 안내가 시작됩니다. 목적지까지 5킬로미터, 3킬로미터, 2킬로미터, 1킬로미터, 500미터에서 남은 거리를 말씀드리고, 300미터에서 내리실 준비를, 150미터에서 목적지 부근임을 알려 드립니다. 지나는 길 이름과 동네가 바뀌면 알려 드리고, 3분 넘게 조용하면 남은 거리를 한 번 말씀드립니다.",
            "저절로 바뀌는 안내 — 차에 타고 내릴 때" to "걸어가는 중에 빠르게 움직이기 시작하면 묻지 않고 곧장 차 안 안내로 바꿉니다. 목적지 800미터 안에서 차가 멈추고 걸으시기 시작하면 저절로 걷는 안내로 이어 드립니다. 단추를 누르지 않으셔도 됩니다. 직접 바꾸시려면 첫 화면의 내렸습니다, 또는 여정 다른 할 일 펼치기의 차에 탔습니다를 누르십시오.",
            "지하철로 가기" to "목적지를 고른 화면에서 지하철로 가기를 누르시면, 지금 자리에서 가까운 역과 목적지에서 가까운 역, 갈아타는 곳, 걸리는 시간, 들어갈 출구를 한 번에 말씀드립니다. 이 길로 가기를 누르시면 타는 역 출구까지 걷는 안내가 시작됩니다. 출구에 닿으면 어느 호선 어느 방면을 타실지 알려 드립니다. 이미 열차 안이시면 이미 열차에 탔습니다를 누르십시오.",
            "지하철에서 지나는 역 듣기와 갈아타기" to "출구에 닿은 뒤 열차가 움직이는데 걸음이 없으면 열차에 타신 것으로 알고 저절로 역 알림을 시작합니다. 누르지 않으셔도 됩니다. 지나는 역마다 역 이름을 말씀드리고, 갈아탈 역은 한 역 전에 미리, 그 역에서는 어느 호선 어느 방면으로 갈아타실지 알려 드립니다. 내리실 역은 두 역 전과 한 역 전에 알려 드리고, 닿으면 목적지에 가장 가까운 출구를 말씀드립니다. 밖으로 나오셔서 걸으시면 저절로 남은 길 걷는 안내로 이어집니다. 지하에서는 위성이 없으므로 서울 실시간 열차 위치와 폰 흔들림, 역 사이 시간으로 헤아립니다.",
            "버스로 가기" to "목적지를 고른 화면에서 버스로 가기를 누르시면 가까운 정류장이 시계 방향과 거리, 정류장 번호와 함께 다섯씩 나옵니다. 정류장을 고르시면 그 정류장에 오는 버스가 몇 분 뒤에 오는지, 저상버스인지 말씀드립니다. 정류장까지 걸어가기를 누르시면 정류장까지 걷는 안내가 시작되고, 닿으면 오는 버스를 다시 알려 드립니다. 버스에 타셔서 버스가 움직이면 저절로 버스 안 안내로 바뀝니다. 버스 안에서는 목적지 500미터 앞에서 내리실 준비를, 300미터 앞에서 다음 정류장에서 내리시라고 알려 드립니다. 지금은 서울 정류장의 도착 정보가 가장 잘 나옵니다.",
            "탈것 바로잡기" to "차나 버스, 기차를 타고 가는 중에 길눈이 탈것을 잘못 알고 있으면, 첫 화면의 여정 다른 할 일 펼치기에서 탈것 바로잡기를 누르고 차, 버스, 기차, 고속버스 가운데 고르십시오. 바로잡으신 것이 가장 앞서며, 길눈이 빠르기로 다르게 짐작해도 바꾸지 않습니다.",
            "지금 어떻게 가고 있습니까" to "여정 중에는 길 찾기 탭의 지금 차례 첫 단추를 누르시면 지금 어디로 무엇을 타고 가는 중인지, 남은 거리와 방향, 차 안이면 지금 달리는 길 이름, 지하철이면 몇 정거장 남았는지 말씀드립니다.",
            "여정 끝내기와 목적지 바꾸기" to "도착하면 첫 화면의 지금 차례가 도착했습니다, 여정 끝내기로 바뀝니다. 가는 중에 목적지를 바꾸시려면 여정 다른 할 일 펼치기에서 여정 끝내기를 누른 뒤 새로 찾으시면 됩니다. 여정은 폰 안에 담겨 길눈을 껐다 켜도 이어지고, 열두 시간 넘게 손대지 않으면 저절로 끝납니다. 안내는 화면을 떠나도, 음악을 틀어도, 폰을 잠가도 이어집니다.",
            "도착했을 때 말로 마치기" to "가시던 곳에 닿으시면 말로 하기를 누르신 뒤 도착, 도착했어, 다 왔어라고 말씀하십시오. 도착하셨습니다 하고 여정을 마칩니다. 여정 끝이라고 하셔도 같습니다.",
            "차 안 간판 알림 — 지나는 가게와 건물을 왼쪽 오른쪽으로" to "차 안 안내를 받으실 때 저절로 돕니다. 차가 초속 4미터 넘게 달리면, 지나는 자리 둘레의 가게와 건물을 카카오 지도 자료에서 찾아 달리는 방향으로 왼쪽 오른쪽을 가려 오른쪽에 GS25, 편의점처럼 알려 드립니다. 곧 옆을 지날 곳 하나만 고르며, 지하철역, 공공기관, 관광명소, 문화시설, 마트, 은행, 약국, 병원을 식당이나 카페보다 먼저 알려 드립니다. 한 번 알린 곳은 30분 안에 다시 말하지 않고, 지나는 곳 말하는 간격의 3분의 1, 적어도 15초에 한 번까지만 말씀드립니다. 서 있을 때 창밖 간판 읽기를 켜 두시면, 차가 서 있거나 천천히 갈 때 카메라가 창밖 간판을 읽어 보탭니다. 이때는 폰 뒤쪽 카메라를 창밖으로 향해 주십시오. 다시 달리면 카메라는 저절로 꺼집니다. 두 가지 모두 설정 탭의 말하기 설정에서 켜고 끄실 수 있습니다. 가게 이름을 찾을 때 지금 자리만 협회 나스를 거쳐 카카오에 묻고, 사진은 보내지 않습니다.",
            "여러 점지도 이어 걷기" to "한 점지도로 닿지 않는 곳이라도 협회가 이어 둔 길로 닿으면, 걸어가기를 누르셨을 때 이어진 점지도로 걸을지 여쭙니다. 가까운 점지도 화면의 이어서 갈 수 있는 곳 펼치기에서 직접 고르실 수도 있습니다. 한 구간을 마치면 남은 구간 수를 알리고 곧장 다음 구간으로 이어 안내하므로 단추를 누르지 않으셔도 됩니다. 문까지 안내는 마지막 구간에서만 합니다. 이어진 길의 다음 구간을 불러오지 못하면 멈추지 않고 위성 안내로 이어 갑니다. 이어진 길을 다 걸은 뒤 되돌아가기를 누르시면 이어진 길 전체를 거꾸로 걷습니다.",
            "문까지 이어 안내" to "점지도 끝 30미터 앞에서 협회가 모아 둔 문을 찾아 문까지 이어 안내합니다. 여기서부터는 위성 안내라 몇 미터 오차가 있을 수 있습니다. 문까지 몇 시 방향, 몇 걸음인지 자주 말씀드리고, 문 5미터 안에서 문 앞이라고 알리며 들어가는 쪽이 몇 시 방향인지 알려 드립니다. 위성이 흐리면 문 앞을 10미터까지 넓게 보고, 문을 90초 넘게 찾으면 문 쪽과 거리를 알려 드린 뒤 마칩니다. 문이 여럿이면 걷는 화면에 다른 문으로 단추가 나옵니다. 말로 하기에서 다른 문이라고 하셔도 됩니다.",
            "내 문 담기" to "설정 탭의 점지도와 걸음에서 내 문을 여십시오. 문 두 걸음 앞에서 문 쪽을 보고 문 앞에서 한 번 찍기를 누르시고, 문을 지나 두 걸음 들어가서 한 번 더 찍기를 누르십시오. 두 자리 사이가 문으로, 들어가신 쪽이 들어가는 쪽으로 이 폰에 담깁니다. 문 찾기를 쓸 수 있는 폰은 첫 번째 찍을 때 카메라가 문 둘레 글자(호수, 출입구 같은 것)를 함께 읽어 담습니다. 사진은 담지 않습니다. 문까지 안내에서 내 문을 가장 먼저 씁니다. 이름을 적어 두시면 그 이름으로 말씀드립니다. 지우시려면 그 줄을 길게 누르시거나 톡백 동작에서 이 문 지우기를 고르십시오.",
            "함께 시험하기" to "점지도를 따라 걷는 중에 걷는 화면의 따라 걷기 다른 할 일 펼치기에서 함께 시험 번호 받기를 누르시면 네 자리 번호를 말씀드립니다. 곁의 자봉께 번호를 알려 주시면 자봉이 지금 자리와 다음 표시를 함께 따라 보며 점지도를 확인합니다. 마치실 때는 같은 자리의 함께 시험 끝내기를 누르십시오. 그만 걷기를 하셔도 함께 끝납니다.",
            "따라 걷는 동안 길목과 버스 정류장" to "점지도를 따라 걷는 중에 따라 걷기 다른 할 일 펼치기에서 이 길목은 어떻게 생겼습니까를 누르시면 지금 선 곳이 어떤 길목인지, 몇 시 방향으로 어느 길이 있는지 말씀드립니다. 지도에 그려진 얼개일 뿐이니 발밑은 지팡이로 살펴 주십시오. 가까운 버스 정류장을 누르시면 가까운 정류장 세 곳을 시 방향과 거리로 알려 드립니다. 말로 하기에서 길목, 정류장이라고 하셔도 됩니다.",
            "따라 걷는 동안 지나는 곳" to "점지도를 따라 걸으실 때 25미터 둘레의 가게와 조심할 곳을 오른쪽, 왼쪽, 앞으로 나누어 알려 드립니다. 뒤에 있는 것은 말하지 않습니다. 목적지 20미터 안에서는 문을 찾는 데 집중하도록 쉽니다. 끄고 켜기는 설정 탭 점지도와 걸음의 점지도 안내 설정 펼치기에서 지나는 곳 안내로 합니다.",
            "앱이 꺼졌다 켜져도 점지도로 이어 걷기" to "점지도를 따라 걷다가 앱이 꺼지거나 폰을 다시 켜셔도, 3시간 안이면 앱이 켜질 때 하던 점지도 따라 걷기를 이어 가고 걷는 화면을 엽니다. 이어진 길을 걷던 중이었으면 걷던 구간부터 잇습니다. 그만 걷기나 여정 끝내기를 하시면 이어 가지 않습니다.",
            "여기 문제 있어요, 여기 걸렸어요" to "따라 걷다가 점자블록이 없어졌거나 공사 중이거나 무엇이 길을 막고 있으면 걷는 화면의 여기 문제 있어요를 누르고 여섯 가지 가운데 고르십시오. 한마디를 적으셔도 되고 안 적으셔도 됩니다. 지금 자리와 함께 협회에 남습니다. 여기 걸렸어요는 이 자리가 걸리는 곳이라고 남겨 다음에 오시는 분께 60미터 앞에서 미리 알려 드리게 합니다.",
            "나만의 점지도" to "설정 탭의 점지도와 걸음에서 나만의 점지도를 여십시오. 걷기 시작을 누르고 평소대로 걸으시면 길을 그립니다. 오름턱, 내림턱, 계단, 문·입구, 조심할 곳에 닿으면 그 단추를 누르시고, 한마디를 적어 남기실 수도 있습니다. 걷기 끝을 누르신 뒤 이름을 적고 나만 쓰기 또는 모두가 쓰도록 점지도에 올리기를 고르십시오. 나만 쓰기는 이 폰에만 담기고, 올리시면 다른 시각장애인도 따라 걸을 수 있습니다. 내가 그린 길을 누르시면 따라 걷기, 되돌아가기, 올리기, 맡기기, 지우기가 있습니다. 맡기기는 잠금말을 걸어 협회 서버에 두는 것이며 잠금말은 서버로 가지 않습니다. 폰을 바꾸시면 같은 잠금말로 찾아오십시오. 아이폰 길눈이나 웹 길눈에서 맡긴 길도 같은 잠금말로 찾아옵니다.",
            "걸음 오차 재기" to "설정 탭의 점지도와 걸음에서 걸음 오차 재기를 여십시오. 미리 재 둔 실제 거리와 내 보폭, 걷는 빠르기, 흰지팡이를 쓰시는지 고르고 재기 시작을 누르십시오. 끝까지 걸으신 뒤 다 걸었습니다를 누르시면 걸음으로 잰 거리와 위성으로 잰 거리가 실제 거리와 몇 퍼센트 다른지 알려 드립니다. 이 결과 담기를 누르시면 협회에 남아 점지도를 바로잡는 데 쓰입니다.",
            "점지도 안내 설정" to "설정 탭의 점지도와 걸음에서 점지도 안내 설정 펼치기를 여십시오. 걸을 때 확신음을 끄고 켜실 수 있고, 끄셔도 방향이 틀어졌을 때의 말은 그대로 나옵니다. 제대로 가고 있다는 말은 5미터, 10미터, 20미터 가운데 고르십니다. 지나는 곳 안내와 안내 중 이어폰 단추 받기도 여기서 끄고 켭니다.",
            "꺾는 곳 진동" to "점지도를 따라 걷다가 지금 도십시오라고 말씀드릴 때 폰과 갤럭시 워치가 함께 떱니다. 오른쪽으로 도실 때는 길게 한 번, 왼쪽은 짧게 두 번, 뒤로 도실 때는 아주 길게 한 번입니다. 곧게 가시는 곳에서는 떨지 않습니다. 도착하면 세 번 떱니다.",
            "리모컨 배우기" to "설정 탭의 점지도와 걸음에서 리모컨 배우기를 여십시오. 블루투스 리모컨을 폰과 먼저 연결한 뒤, 1번 자리 익히기를 누르고 리모컨 단추를 한 번 누르시면 그 단추가 지금 어디입니까에 담깁니다. 2번은 다음에 무엇이 있습니까, 3번은 다시 말해 주기입니다. 익힌 단추는 점지도 따라 걷기 화면이 켜져 있을 때 쓰입니다. 폰이 잠겨 있을 때는 이어폰 단추를 쓰십시오. 소리 크기 단추만 보내는 리모컨은 쓸 수 없고, 전자책 페이지 넘김 리모컨이 잘 맞습니다.",
            "이어폰·리모컨 단추" to "점지도를 따라 걷거나 되짚어 나가거나 말로 그린 길을 걷는 동안에는 이어폰이나 리모컨의 단추를 길눈이 받습니다. 재생 단추는 마지막 안내를 다시 들려 드리고, 음향신호기 앞이면 신호 안내를 울립니다. 재생 단추를 0.6초 넘게 길게 누르시면 화면이 꺼져 있어도 말로 하기가 열립니다. 다음 단추는 걷는 중이면 다음에 무엇이 있는지, 되짚어 나가는 중이면 지금 할 일을, 아니면 지금 내 자리를 알려 드립니다. 이전 단추는 앞 안내를 다시 들려 드립니다. 안드로이드는 마지막으로 소리를 낸 앱에 단추를 넘겨주므로 안내 중에는 길눈이 소리 없는 소리를 틀어 단추를 받습니다. 안내가 끝나면 음악 앱이 다시 단추를 받습니다. 설정 탭 점지도와 걸음의 점지도 안내 설정 펼치기에서 안내 중 이어폰 단추 받기를 끄시면 음악 앱이 단추를 받습니다.",
            "이어폰 앞 안내 거듭 듣기" to "이어폰의 이전 단추를 누르시면 방금 한 말의 앞 말을 다시 들려 드리고, 거듭 누르시면 한 말씩 더 앞으로 갑니다. 새 안내가 나오면 처음부터 다시 셉니다.",
            "되짚어 나가기 — 들어온 길로 혼자 나오기" to "길 찾기 탭의 그 밖에 펼치기 안에 있습니다. 병원 진료실처럼 건물 안에 들어가실 때 들어갑니다 — 지금부터 길을 기억하기를 누르시고 평소처럼 걸어 들어가십시오. 걸음 수와 방향으로 길을 기억합니다. 나오실 때 나갑니다 — 왔던 길 되짚어 나가기를 누르시면, 먼저 몇 시 방향으로 돌아 서실지 알려 드리고, 몇 걸음 걸으신 뒤 몇 시 방향으로 꺾으실지 차례로 말씀드립니다. 꺾는 곳 다섯 걸음 앞에서 미리, 열 걸음마다 남은 걸음을, 방향이 크게 어긋나면 돌아 서실 쪽을 알려 드립니다. 기억하거나 안내하는 동안에는 길 찾기 탭 위쪽, 긴급통화서비스 단추 아래에 되짚어 나가기 한 줄이 나옵니다. 말로 하기에서 길 기억해 줘, 되짚어 나가자라고 하셔도 됩니다. 기억한 길은 이 폰에만 남고 지우실 때까지 남습니다. 어긋날 수 있으니 벽과 손잡이를 함께 짚어 가십시오.",
            "말로 그린 길 — 실내 길 따라 걷기" to "길 찾기 탭의 그 밖에 펼치기 안에 있습니다. 몸으로 익혀 말로 적어 둔 실내 길을 손대지 않고 따라 걷게 해 드립니다. 가는 길 따라 걷기나 오는 길 따라 걷기를 한 번 누르시면, 그 뒤로는 폰이 걸음을 세고 방향을 보며 정해진 걸음에 닿으면 다음 안내를 스스로 말씀드립니다. 세 걸음 앞에서 다음 할 일을 미리 알려 드리고, 방향이 크게 틀어지면 멈추시라고, 예상보다 많이 걸으시면 확인하시라고 알려 드립니다. 엘리베이터는 멈췄다가 다시 걷기 시작하시면 내리신 것으로 봅니다. 걷는 동안 길 찾기 탭 위쪽, 긴급통화서비스 단추 아래에 한 줄이 나옵니다. 말로 하기에서 다시라고 하시면 지금 안내를 다시, 그만이라고 하시면 멈춥니다. 나스 음악 열쇠를 넣은 폰에서만 쓰실 수 있습니다.",
            "음성유도기와 승강기" to "길 찾기 탭의 그 밖에 펼치기 안에 있습니다. 여시면 지금 자리에서 가까운 지하철역 다섯 곳을 찾습니다. 역을 누르시면 음성유도기가 몇 곳, 엘리베이터와 에스컬레이터가 몇 대인지 알려 드리고, 펼치기를 여시면 어디에 있는지와 몇 층에서 몇 층까지 다니는지를 다섯 곳씩 읽어 드립니다. 서울교통공사 자료로 서울 1호선부터 8호선까지만 있고, 실제와 다를 수 있습니다. 말로 하기에서 음성유도기 어디 있어, 역 엘리베이터 어디 있어라고 하셔도 됩니다.",
            "흔들면 자리 번호 — 긴급통화 열기 또는 국가지점번호" to "설정 탭의 폰 펼치기 안의 흔들면 자리 번호에서 폰 흔들기를 켜십시오. 처음에는 꺼져 있습니다. 켜 두시면 폰을 세게 두 번 흔드실 때 긴급통화서비스가 곧바로 열립니다. 흔들면 하는 일을 누르시면 지금 자리의 국가지점번호 말하기로 바뀝니다. 국가지점번호는 산과 강과 바닷가처럼 도로명주소가 없는 곳에서 119가 쓰는 위치 번호이며, 폰이 스스로 셈하므로 통신이 끊겨도 됩니다. 숫자는 119에 불러 드리기 좋게 한 자씩 끊어 읽습니다. 걷다가 한 번 튀는 것은 흔들기로 치지 않습니다.",
            "QR 찾기" to "길 찾기 탭의 그 밖에 펼치기 안에 있습니다. 여시면 카메라가 켜지고 둘레의 QR을 저절로 찾습니다. 폰을 가슴 높이로 세워 들고 천천히 움직이십시오. 잡히면 삐 소리와 진동이 나고, 무엇이 담긴 QR인지와 카메라의 어느 쪽에 있는지를 알려 드립니다. 주소면 이 주소 열기, 전화번호면 이 번호로 전화 걸기가 나오고, 글 복사하기와 다른 QR 찾기 — 계속이 있습니다. 1분 동안 잡히지 않으면 찾는 중이라고 알려 드립니다. 어두우면 알아 두실 것 펼치기 안의 손전등을 켜십시오. 카메라 사진은 어디로도 보내지 않고 폰 안에서만 읽으며, 화면을 떠나면 카메라는 저절로 꺼집니다. 말로 하기에서 QR 찾아 줘라고 하셔도 됩니다.",
            "즉석 글자 읽기 — 카메라를 대면 곧바로" to "둘러보기 탭에 있습니다. 말로 하기에서 글자 읽어 줘라고 하셔도 열립니다. 화면을 여시면 바로 읽기 시작합니다. 사진을 찍지 않아도 폰을 글자 쪽으로 세워 들고 천천히 움직이시면, 보이는 글자를 위에서 아래 차례로 곧바로 읽어 드립니다. 한 번 읽은 줄은 30초 안에 되풀이하지 않고 새로 보인 글만 읽습니다. 글자가 화면 끝에 걸려 잘리면 폰을 어느 쪽으로 옮기실지 3초에 한 번까지 알려 드립니다. 겉에는 멈추기와 이어 읽기, 방금 읽은 것 다시 듣기 두 단추만 있고, 읽은 글은 읽은 글 펼치기에 다섯 줄씩 남습니다. 어두우면 알아 두실 것 펼치기 안의 손전등을 켜십시오. 카메라 눈 말소리를 끄시면 말 없이 화면 글자로만 남습니다. 3분 동안 새 글이 없거나 화면을 떠나시면 카메라를 끕니다. 그만이라고 하셔도 멈춥니다. 사진과 글은 어디로도 보내지 않고 인터넷 없이 폰 안에서만 읽습니다.",
            "사람 감지 — 앞의 사람을 방향과 걸음 수로" to "둘러보기 탭에 있습니다. 말로 하기에서 사람 있어라고 하셔도 열립니다. 화면을 여시면 바로 시작합니다. 폰을 가슴 앞에 세워 들거나 목걸이로 걸고 걸으시면, 앞에 사람이 있을 때 1시 방향 사람, 3걸음처럼 알려 드립니다. 안드로이드에서는 한 번에 가장 뚜렷한 한 사람을 알려 드립니다. 3미터 안으로 가까워지면 확신음이 1초마다, 1.5미터 안이면 0.5초마다 울립니다. 말은 3초에 한 번쯤 바뀔 때만 하고, 사람이 사라지면 앞에 사람이 없습니다라고 한 번 알립니다. 지금 앞 다시 듣기를 누르시면 지금 상태를 다시 들려 드립니다. 거리는 사람 몸통 길이를 기준으로 어림한 것이라 앉은 사람이나 아이는 실제보다 멀게 들릴 수 있습니다. 카메라 눈 말소리를 끄시면 말 없이 확신음으로만 알립니다. 10분 동안 사람이 보이지 않거나 화면을 떠나시면 카메라를 끕니다. 그만이라고 하셔도 멈춥니다. 얼굴을 알아보거나 사진을 담지 않으며, 인터넷 없이 폰 안에서만 돕니다.",
            "빛 알아보기 — 불이 켜졌는지, 창이 어느 쪽인지" to "둘러보기 탭에 있습니다. 말로 하기에서 불 켜져 있어, 빛 알려 줘, 밝은 쪽 찾아 줘라고 하셔도 열립니다. 화면을 여시면 바로 시작합니다. 폰 뒤쪽 카메라가 보는 쪽이 밝을수록 높은 소리가 납니다. 폰을 천천히 돌리시면 가장 밝은 쪽에서 이쪽이 가장 밝습니다라고 알려 드려, 창이나 켜진 불이 어느 쪽인지 찾으실 수 있습니다. 불이 켜지거나 꺼지면 불이 켜진 듯합니다, 불이 꺼진 듯합니다라고 알려 드립니다. 밝기가 바뀌면 캄캄합니다, 어둡습니다, 실내 불빛 밝기입니다, 밝습니다, 햇빛처럼 아주 밝습니다 가운데 하나로 3초에 한 번까지 알려 드립니다. 지금 밝기 듣기를 누르시면 지금 밝기를 다시 들려 드립니다. 높낮이 소리를 끄시면 말로만, 카메라 눈 말소리를 끄시면 높낮이 소리로만 알려 드립니다. 10분 동안 밝기가 바뀌지 않거나 화면을 떠나시면 카메라를 끕니다. 그만이라고 하셔도 멈춥니다. 폰 안에서만 살피며 사진은 담지도 보내지도 않습니다.",
            "카메라 눈 말소리" to "설정 탭의 말하기 설정과 카메라 눈 화면마다 있습니다. 끄시면 카메라 눈(즉석 글자 읽기, 사람 감지, 빛 알아보기, 문 찾기)이 말 없이 확신음이나 높낮이 소리, 화면 글자로만 알립니다. 켜고 끈 것과 끌 수 없는 안전 경고(발 앞 계단·턱 알림)는 그대로 말씀드립니다.",
            "문 찾기 — 관리자 시험 중" to "안드로이드에서는 관리자 시험 중이라 관리자 열쇠가 있는 폰의 설정 탭 더 보기, 관리자 시험 안과 길 찾기 탭 그 밖에 펼치기에만 보입니다. 안드로이드에는 문 자체를 알아보는 기능이 없어, 문 위나 옆의 호수, 출입구, 화장실, 당기세요 같은 짧은 글자를 카메라로 찾아 몇 시 방향 문, 약 몇 걸음인지 알려 드립니다. 걸음은 글자 크기로 어림한 것입니다. 문 찾기 켜기를 누르시고 폰을 가슴 앞에 세워 들고 천천히 걸으십시오. 문 글자가 보이면 확신음과 함께 알려 드리고, 3초에 한 번쯤 바뀔 때만 말씀드립니다. 문 바로 앞에 이르면 도착 소리와 함께 알리고 카메라를 끕니다. 90초 안에 문을 찾지 못하면 저절로 끕니다. 관리자 폰에서는 점지도 문까지 안내에서 문 10미터 안에 드시거나 걸어서 목적지에 닿으시면 저절로 켜지고, 내 문 두 번 찍기 때 문 둘레 글자를 함께 담습니다. 내 문에 글자를 담아 두신 문을 찾아가실 때는 찍어 두신 문인지, 옆 문인지도 알려 드리고, 문 바로 앞을 알린 뒤 문을 지나 두 걸음 들어가시면 손대지 않아도 두 번 찍은 것으로 그 문을 내 문에 담습니다. 글자가 없는 문은 찾지 못하니 지팡이로 꼭 함께 확인하십시오. 그만이라고 하시면 멈춥니다.",
            "발 앞 계단·턱 알림 — 관리자 시험 중" to "관리자 시험 중인 기능이라 관리자 열쇠가 있는 폰의 설정 탭 더 보기, 관리자 시험 안에만 보입니다. 구글 AR 깊이 재기를 지원하는 폰에서만 되며, 구글 플레이 AR 서비스가 없으면 설치 화면을 엽니다. 화면을 여시면 바로 시작합니다. 폰을 가슴 앞에 들고 카메라가 앞쪽 바닥을 보도록 조금 숙여 걸으시면, 발 앞 바닥이 갑자기 꺼지거나 솟는 곳을 두 걸음 앞, 내려가는 턱, 약 15센티미터처럼 경고음과 함께 알려 드립니다. 40센티미터가 넘으면 크게 꺼진 곳, 앞을 막는 높은 것으로 알려 드립니다. 같은 턱은 한 걸음 넘게 가까워질 때만 다시 알립니다. 안전 경고라 말소리를 꺼도 늘 알립니다. 계단이 몇 단인지는 세지 않습니다. 앞쪽 바닥이 15초 넘게 안 보이면 폰을 더 숙여 달라고 말씀드립니다. 20분이 지나거나 화면을 떠나시면 끕니다. 그만이라고 하셔도 멈춥니다. 지팡이를 대신하지 않으며 지팡이와 함께 쓰십시오. 폰 안에서만 살피며 사진이나 깊이 자료를 담지도 보내지도 않습니다.",
            "한마디 설명 — 인터넷 없이 카메라 앞을 한마디로" to "둘러보기 탭의 빛 알아보기 바로 아래에 있습니다. 말로 하기에서 이게 뭐야, 뭐가 보여라고 하셔도 열립니다. 화면을 여시면 바로 시작합니다. 폰 뒤쪽 카메라를 궁금한 쪽으로 향하시면 보이는 것을 컵, 책상인 듯합니다처럼 대강 한마디로 알려 드립니다. 얼굴이 보이는 사람이 있으면 몇 명인지, 글자가 있으면 글자가 보입니다라고 덧붙입니다. 보이는 것이 바뀔 때만, 4초에 한 번까지 말씀드립니다. 인터넷 없이 폰 안에서만 짐작하는 것이라 틀릴 수 있으니 참고로만 들으십시오. 자세한 설명은 사진 읽어 주기를, 글자를 읽으시려면 즉석 글자 읽기를 쓰십시오. 방금 한마디 다시 듣기를 누르시면 마지막 한마디를 다시 들려 드립니다. 5분 동안 새로 알릴 것이 없거나 화면을 떠나시면 카메라를 끕니다. 그만이라고 하셔도 멈춥니다. 사진은 담지도 보내지도 않습니다.",
            "지폐와 색깔 알아보기 — 옷과 물건의 색, 지폐" to "둘러보기 탭의 상품 바코드 읽기 바로 아래에 있습니다. 말로 하기에서 무슨 색이야, 색깔 알려 줘, 지폐 알려 줘, 얼마짜리야라고 하셔도 열립니다. 화면을 여시면 바로 시작합니다. 폰 뒤쪽 카메라를 옷이나 물건에 한 뼘쯤 떨어뜨려 대시면 가운데 색을 진한 남색, 연한 하늘색, 밝은 빨강처럼 늘 쓰는 색 이름으로 알려 드립니다. 무늬가 있으면 흰색 바탕에 검정 무늬처럼 바탕색과 무늬 색을 나누어 알려 드립니다. 색은 바뀔 때만, 3초에 한 번까지 말씀드립니다. 지폐는 숫자가 보이는 쪽을 카메라로 향해 주시면 천 원, 오천 원, 만 원, 오만 원을 확신음 한 번과 함께 알려 드리고, 같은 지폐는 10초 안에 되풀이하지 않습니다. 너무 어두우면 조금 더 밝은 곳에서 대 주십시오라고 먼저 알려 드립니다. 색은 불빛에 따라 달라 보여 노란 전등 아래에서는 조금 다르게 들릴 수 있습니다. 옷 맞춰 입기, 양말 짝 맞추기, 과일 익은 정도 보기에 쓰실 수 있습니다. 방금 것 다시 듣기를 누르시면 마지막으로 알린 것을 다시 들려 드립니다. 5분 동안 새로 알릴 것이 없거나 화면을 떠나시면 카메라를 끕니다. 그만이라고 하셔도 멈춥니다. 폰 안에서만 살피며 사진은 담지도 보내지도 않습니다.",
            "상품 바코드 읽기 — 상품 이름을 읽어 드림" to "둘러보기 탭의 사람 감지 바로 아래에 있습니다. 말로 하기에서 바코드 읽어 줘, 이 상품 뭐야라고 하셔도 열립니다. 화면을 여시면 바로 시작합니다. 폰을 한 손에 들고 상품을 폰 뒤쪽 카메라에서 한 뼘쯤 떨어뜨려 천천히 돌려 주십시오. 바코드를 찾으면 확신음이 한 번 울리고 상품 이름을 읽어 드립니다. 이름을 찾지 못하면 우리나라 상품입니다처럼 어느 나라 상품인지와 바코드 번호를 한 자씩 읽어 드립니다. 같은 상품은 10초 안에 되풀이해 읽지 않습니다. 바코드는 보통 뒷면이나 옆면 아래쪽에 있으며, 20초 동안 찾지 못하면 찾는 법을 한 번 알려 드립니다. 방금 상품 다시 듣기를 누르시면 마지막 상품을 다시 들려 드리고, 이번에 읽은 상품 펼치기에서 앞서 읽은 것들을 다섯 개씩 보실 수 있습니다. 카메라 눈 말소리를 끄시면 찾는 법 알림은 하지 않고 상품 이름만 읽어 드립니다. 10분 동안 바코드를 찾지 못하거나 화면을 떠나시면 카메라를 끕니다. 그만이라고 하셔도 멈춥니다. 바코드 알아보기는 폰 안에서 하고 사진은 담지도 보내지도 않습니다. 상품 이름을 찾을 때만 바코드 번호를 무료 공개 상품 자료에 물어봅니다. 공개 자료에 없는 우리나라 상품은 이름이 나오지 않을 수 있습니다.",
            "가리키고 말하기 — 손가락으로 가리킨 단추 읽기" to "둘러보기 탭의 즉석 글자 읽기 바로 아래에 있습니다. 말로 하기에서 가리키는 거 읽어 줘라고 하셔도 열립니다. 전자레인지, 세탁기, 엘리베이터, 키오스크처럼 단추가 많은 곳에서 쓰십시오. 화면을 여시면 바로 시작합니다. 한 손으로 폰을 단추판 쪽으로 들고, 다른 손 검지로 단추를 가리키시면 손끝이 가리키는 쪽 가장 가까운 낱말을 읽어 드립니다. 손끝을 처음 찾으면 확신음과 함께 손가락이 보입니다라고 알립니다. 손가락을 옮기시면 새 글에 닿을 때마다 읽고, 같은 글에 머무시면 되풀이하지 않습니다. 손가락이 화면 밖으로 나가면 폰을 조금 뒤로 빼시라고 세 번까지 알려 드립니다. 겉에는 멈추기와 이어 읽기, 방금 읽은 것 다시 듣기 두 단추만 있고, 읽은 글은 읽은 글 펼치기에 다섯 개씩 남습니다. 카메라 눈 말소리를 끄시면 화면 글자로만 남습니다. 3분 동안 새로 읽은 글이 없거나 화면을 떠나시면 카메라를 끕니다. 그만이라고 하셔도 멈춥니다. 안드로이드에서는 처음 한 번 손가락 찾기 자료(약 8메가바이트)를 인터넷으로 받아야 합니다. 그 뒤로는 사진과 글을 어디로도 보내지 않고 인터넷 없이 폰 안에서만 읽습니다.",
            "둘레 찾기 — 식당, 약국, 화장실, 응급실" to "둘러보기 탭 맨 위의 둘레 찾기를 누르십시오. 식당, 카페, 병원, 약국, 편의점, 화장실, 버스정류장, 응급실, 대피소가 겉에 있고, 은행, 대형마트, 주차장, 주유소, 관광명소, 문화시설, 공공기관, 숙박은 그 밖에 펼치기 안에 있습니다. 고르시면 1킬로미터 안에서 가까운 차례로 다섯 곳씩 나오고, 나오면 첫 줄로 초점이 갑니다. 다섯 줄 아래의 더 보기로 다음 다섯을, 이전 보기로 앞의 다섯을 봅니다. 이름을 두드리시면 그곳까지 안내합니다. 2킬로미터 안이면 곧장 걷는 안내를, 멀면 길 찾기 탭에서 어떻게 가실지 고르시는 화면을 엽니다. 전화 걸기와 주소 듣기는 이름을 길게 누르시거나 톡백 동작 메뉴에서 고르십시오. 식당은 밥집 갈래를 먼저 고르시고, 대피소는 지진, 민방위, 무더위, 한파 가운데 고르십시오. 말로 하기에서 근처 약국처럼 말씀하시면 가까운 세 곳을 들려 드리고 둘러보기 탭에 목록을 열어 드립니다.",
            "가는 김에 — 가 볼 곳, 축제, 무장애 여행 정보" to "둘러보기 탭의 가는 김에 펼치기 안에 있습니다. 가 볼 곳, 축제, 무장애 여행 정보를 지금 계신 곳에서 10킬로미터 안으로 가까운 차례로 다섯 곳씩 알려 드립니다. 축제는 여는 날과 닫는 날도 말씀드립니다. 이름을 두드리시면 그곳으로 안내하고, 전화와 주소는 이름을 길게 누르시거나 톡백 동작 메뉴에서 고르십시오. 말로 하기에서 축제 알려 줘라고 하셔도 가까운 세 곳을 말씀드립니다.",
            "고장 이야기 — 지나는 고장의 먹을 곳·볼 곳" to "둘러보기 탭 가는 김에 펼치기의 고장 이야기에서 지금 이 고장 이야기 듣기를 누르시면, 지금 계신 고장의 먹을 곳, 볼 곳, 축제를 짧게 들려 드립니다. 같은 화면의 차 안에서 고장이 바뀌면 들려 주기를 켜 두시면, 차나 버스, 기차로 가시는 동안 고장이 바뀔 때 한 번씩 저절로 들려 드립니다. 지하철 안에서는 들려 드리지 않습니다. 같은 고장은 15분에 한 번만 말씀드립니다. 처음에는 켜져 있습니다. 말로 하기에서 고장 이야기라고 하셔도 됩니다.",
            "사진 읽어 주기" to "둘러보기 탭의 사진 읽어 주기를 여시면 폰 뒤쪽 카메라가 켜집니다. 읽을 것을 비추고 사진 찍어 읽어 주기를 누르시면 그 자리에서 한 장을 찍어 무엇이 보이는지 짧게 읽어 드리고, 글이 화면에 나오며 초점이 그리로 갑니다. 사진기 앱의 셔터 단추를 찾지 않으셔도 됩니다. 더 자세히 읽어 주기를 누르시면 길게 풀어 말씀드립니다. 이 두 가지는 사진을 협회 나스로 보내 읽습니다. 글씨만 읽어 주기는 사진을 어디로도 보내지 않고 폰 안에서 글씨만 읽습니다. 잘 모르겠으면 사람에게 물어보기를 누르시면 긴급통화서비스 화면이 열립니다. 5분 동안 찍지 않거나 화면을 떠나시면 카메라를 끕니다. 말로 하기에서 사진 읽어 줘라고 하셔도 이 화면이 열립니다.",
            "안면인식 — 카메라 앞의 사람" to "둘러보기 탭의 안면인식에서 안면인식 켜기를 누르시고, 폰을 세워 든 채 뒤쪽 카메라를 앞으로 향하십시오. 몇 명이 있는지, 몇 시 방향인지, 대략 몇 미터인지, 이쪽을 보는지를 말씀드립니다. 웃는 듯하면 웃는 듯합니다라고 조심스럽게 짐작해 드립니다. 거리와 웃음은 짐작이니 참고로만 들으십시오. 보이는 것이 바뀔 때만, 3초에 한 번까지 말씀드립니다. 사진은 어디로도 보내지 않고 폰 안에서만 봅니다. 아는 사람 등록 펼치기에서 이름을 적고 그분 얼굴이 카메라에 정면으로 보일 때 지금 앞의 얼굴로 등록을 누르시면, 다음부터 그분인 듯하면 이름을 말씀드립니다. 안드로이드에서는 얼굴 생김새와 얼굴 밝기 무늬로 어림하는 것이라 아이폰보다 훨씬 자주 틀리거나 못 알아봅니다. 등록한 얼굴 값은 폰 안에만 보관하고 폰 백업에도 넣지 않습니다. 지우실 때는 이름을 길게 누르시거나 톡백 동작 메뉴에서 지우기를 고르십시오. 화면을 떠나면 카메라는 저절로 꺼집니다. 말로 하기에서 안면인식이라고 하셔도 켜집니다.",
            "어디서 어디로 — 미리 들어 보기" to "둘러보기 탭 가는 김에 펼치기의 어디서 어디로에서 가실 곳을 적고 엔터를 치신 뒤 고르시면, 지금 자리에서 그곳까지 차로 가면 몇 킬로미터에 몇 분, 지금 떠나면 몇 시쯤 닿는지, 지나는 길 이름과 거치는 자리를 한 덩이로 들려 드립니다. 3킬로미터 안이면 걸어서 몇 분, 몇 걸음쯤인지도 말씀드립니다. 출발지 바꾸기 펼치기에서 출발지를 다른 곳으로 바꾸실 수도 있습니다. 다시 듣기로 한 번 더 들으시고, 이 길로 가기를 누르시면 곧바로 안내가 시작됩니다.",
            "마실 — 앉은자리에서 떠나는 여행" to "둘러보기 탭 가는 김에 펼치기의 마실에서 떠나실 고장을 고르십시오. 가는 길과 그 고장의 것을 한 마디씩 들려 드리고, 다음으로를 누르시면 다음 마디로 넘어갑니다. 가끔 길에서 겪는 일도 만나시고, 여쭈어 보는 마디에서는 답을 고르시면 맞았는지 말씀드립니다. 마지막 마디를 들으시면 다녀왔습니다로 마무리합니다. 같은 곳을 다시 떠나셔도 겪는 일이 달라집니다. 웹 길눈의 마실과 같은 이야기입니다. 말로 하기에서 마실 가자라고 하셔도 이 화면이 열립니다.",
            "길 위의 음악" to "음악·방송 탭의 길 위의 음악을 여십시오. 지나는 고장 노래(저절로 틀기, 차에 타면 저절로, 고장 이름으로 찾기)는 모든 시각장애인에게 드리는 협회의 선물이라 열쇠 없이 누구나 들으십니다. 갈래와 테마 틀기, 가수와 곡 찾기 같은 나스 음악 전체는 웹 길눈과 같은 열쇠로 들어가며, 한 번 넣으시면 폰에 담아 두어 다시 묻지 않습니다. 갈래와 테마 단추를 눌러 고르고 이어서 틀기를 누르시면 이어서 틀어 드립니다. 지나는 고장 노래 저절로 틀기를 켜시면 1분마다 지나는 고장을 보아, 고장이 바뀌면 그 고장 노래로 갈아 틀어 드립니다. 그 아래 차에 타면 지나는 고장 노래 저절로 틀기를 켜 두시면(처음부터 켜져 있습니다) 차, 버스, 기차에 타실 때 저절로 켜지고 내리시면 고장 따라 바꾸기만 멈춥니다. 라디오, TV, 기사를 듣고 계실 때는 건드리지 않습니다. 말로 하기에서 고장 노래 틀어 줘, 고장 노래 꺼라고 하셔도 됩니다. 고장 이름으로 찾기도 있습니다. 곡이 나오는 동안에는 화면 맨 위에 지금 곡과 다음 곡, 이전 곡, 멈춤, 그만 듣기가 있습니다. 지금 목록 보기 펼치기에서 곡을 골라 트실 수도 있습니다. 다음 곡은 미리 폰에 받아 두어 굴속에서도 이어지고, 끊기면 멈춘 자리부터 다시 이어 받습니다. 열쇠가 없으셔도 누구나 음악 펼치기에서 잔잔한 음악, 밝은 음악, 모두 섞어 틀기를 쓰실 수 있습니다. 누구나 음악은 공유마당의 자유이용 곡이라 곡마다 저작자 표시가 화면에 나옵니다. 기분과 날씨로 틀기 펼치기에서는 기분이나 지금 날씨에 맞는 곡을 골라 틉니다. 곡이나 방송을 불러오지 못해 여섯 번 잇달아 다시 이어도 안 되면, 헛돌지 않게 멈추고 한 번만 알려 드립니다. 잠시 뒤 다시 틀어 주십시오.",
            "기분과 날씨에 맞춰 음악 틀기" to "음악·방송 탭 길 위의 음악의 기분과 날씨로 틀기 펼치기 안에 기분 전환, 흥겹게, 차분하게, 잠들기 전에, 마음을 달래, 분위기 있게, 지금 날씨에 맞게가 있습니다. 누르시면 그 기분에 맞는 곡을 골라 섞어 틉니다. 지금 날씨에 맞게는 비 오는 날, 눈 오는 날, 안개 낀 날, 흐린 날, 맑은 날, 밤 가운데 지금에 맞는 곡을 틉니다. 말로 하기에서 기분이 꿀꿀해, 신나는 노래 틀어 줘, 잔잔한 음악 틀어 줘, 잠이 안 와 음악 틀어 줘, 날씨에 맞게 틀어 줘라고 하셔도 됩니다. 나스 음악 열쇠를 넣지 않으셨으면 누구나 음악의 밝은 곡이나 잔잔한 곡으로 틉니다.",
            "라디오 듣기" to "음악·방송 탭의 라디오 듣기에서 채널 이름을 누르시면 곧바로 나옵니다. 다섯 채널씩 보여 드리고 더 보기로 넘기십니다. KBS와 MBC 라디오는 협회 나스가 이어 주어 몇 분 만에 끊기지 않습니다. 말로 하기에서 MBC 라디오, KBS 클래식FM처럼 부르셔도 됩니다. 끊기거나 16초 넘게 소리가 나지 않으면 저절로 새 주소를 받아 다시 잇습니다. 듣는 동안 화면 맨 위에 멈춤과 라디오 그만 듣기가 있습니다.",
            "TV 보기" to "음악·방송 탭의 TV 보기에서 채널을 고르십시오. MBC 듣는방송24는 화면해설이 들어간 채널이며, 지금 나가는 자리부터 틉니다. 영상은 화면에 나오고, 폰을 잠그거나 다른 화면으로 가시면 소리만 이어집니다. 영상 끄기를 켜시면 영상을 화면에서 빼고 가장 낮은 화질로 받아 데이터를 아낍니다. 끄시면 영상이 다시 나옵니다. 끊기면 저절로 다시 잇습니다.",
            "라디오와 TV를 말로 부르기" to "말로 하기 단추를 누르신 뒤 MBC 라디오, 엠비씨 표준FM, KBS 클래식FM, 라디오 MBC처럼 말씀하십시오. 방송사 이름은 영문으로 들어오든 한글로 들어오든, 말 순서가 바뀌어도 알아듣습니다. 방송사만 말씀하시면 그 방송사의 으뜸 채널을 틉니다. MBC는 표준FM, KBS는 1라디오, EBS는 EBS FM입니다. 라디오 틀어 줘처럼 이름 없이 말씀하시면 지난번에 들으신 방송을 틉니다. 알아듣지 못한 이름이면 아무것도 틀지 않고 어느 방송을 들으실지 여쭙니다. TV는 KBS1 TV, MBC 듣는방송처럼 말씀하십시오.",
            "지금 세상 이야기" to "음악·방송 탭의 지금 세상 이야기에서 갈래를 고르시면 최신 순으로 다섯 건씩 나옵니다. 갈래는 속보·특보, 두루 소식, 장애·복지, 정치, 경제, 국제, 사회, 스포츠·연예입니다. 기사를 고르시면 원문 전체를 읽어 드립니다. 읽는 동안 읽기 멈춤, 다음 기사, 처음부터 다시 읽기, 기사 목록으로가 제목 바로 아래에 있고, 본문은 그 아래에 글로도 나옵니다. 읽기를 멈췄다 이어 읽으시면 읽던 문단의 처음부터 이어 읽습니다. 기사 찾기에 낱말을 넣으시면 모든 갈래에서 찾습니다. 목소리 고르기 펼치기에서 기사를 읽어 줄 목소리와 빠르기를 길 안내 목소리와 따로 정하실 수 있습니다. 길눈 말소리를 꺼 두셨으면 읽어 드리지 않고 글로만 띄워 톡백으로 들으시게 합니다. 말로 하기에서 뉴스 들려줘, 장애 소식, 속보라고 하셔도 됩니다.",
            "방송 들으며 길눈 쓰기" to "길눈에서 튼 음악, 라디오, TV는 화면을 옮겨도, 폰을 잠가도, 길 찾기 탭에서 안내를 받으셔도 끊기지 않습니다. 틀고 있는 동안 알림 칸에 길눈 방송이 떠 있고, 거기서도 멈춤과 다음, 이전을 쓰실 수 있습니다. 기사 읽기는 기사 화면을 떠나시면 멈춥니다. 기사 목록으로, 뒤로, 다른 탭으로 가시면 떠나시는 것이고, 폰을 잠그시는 것은 떠나시는 것이 아니어서 계속 읽습니다. 길눈 안내 말이 나올 때는 방송 소리를 작게 줄였다가 말이 끝나면 되돌립니다. 경고만은 방송을 멈추고 바로 말한 뒤 다시 틉니다. 기사 읽기는 안내 말 동안 쉬었다가 이어 읽습니다. 말로 하기가 명령을 듣는 동안과 긴급통화 동안에는 방송을 잠시 멈춥니다. 이어폰 단추는 길눈 방송이 나오는 동안 방송을 다룹니다. 재생 단추를 짧게 누르시면 멈춤과 다시 틀기, 다음 단추는 다음 곡이나 다음 채널이나 다음 기사, 이전 단추는 이전 곡이나 앞 채널이나 기사 처음부터입니다. 재생 단추를 길게 누르시면 지금처럼 말로 하기가 열립니다. 이어폰을 빼시면 소리가 새지 않게 멈추고, 다른 앱이 소리를 가져가도 멈춥니다. 다시 틀기를 누르시면 이어집니다. 어느 탭에서나 음악·방송 탭 첫 화면 맨 위의 그만 듣기로 끄실 수 있습니다.",
            "동영상 틀기 — 카톡이나 문자로 받은 동영상을 폰에서" to "카톡, 문자, 갤러리에서 동영상을 열고 공유 단추를 누른 뒤 앱 목록에서 길눈을 고르십시오. 목록에 길눈이 없으면 목록 끝의 더 보기에서 찾으실 수 있습니다. 길눈이 꺼져 있어도 저절로 켜져 받습니다. 받은 동영상은 온 화면으로 틉니다. 맨 위에 뒤로 — 동영상 닫기, 아래에 재생과 멈춤, 10초 뒤로, 10초 앞으로가 있습니다. 폰을 잠그시면 소리는 이어지고, 홈이나 다른 앱으로 가시면 멈춥니다. 동영상 주소가 왔을 때는 주소를 길게 눌러 복사한 뒤 음악·방송 탭의 동영상 틀기에서 복사한 동영상 주소 틀기를 누르십시오. 마지막으로 받은 동영상은 같은 화면의 받은 동영상 다시 틀기로 다시 트실 수 있습니다. 유튜브 주소는 유튜브 약관에 따라 유튜브 앱으로 열어 드립니다. 갤럭시 워치에서 동영상을 트는 것은 워치 길눈에 재생 화면이 생기면 옮겨 옵니다. 길눈을 고르지 않으시면 폰이 원래대로 처리합니다.",
            "나눔 마당 — 쓰지 않는 물건 주고받기" to "나눔 탭의 나눔 마당에서 드립니다와 찾습니다를 다섯 건씩 보실 수 있습니다. 이름을 두드리시면 자세히 읽어 드리고, 전화 걸기와 다 나눴습니다 — 이 글 내리기는 이름을 길게 누르시거나 톡백 동작 메뉴에서 고르십니다. 글 올리기에서 드립니다나 찾습니다를 고르고 무엇인지, 한마디, 어느 지역, 이름 또는 별명, 연락 받으실 방법을 적어 올리십시오. 연락 방법은 마당에 그대로 드러나니 남에게 알려도 괜찮은 번호만 적으십시오. 글은 여든 날이 지나면 저절로 내려가고, 물건값을 주고받는 곳이 아닙니다.",
            "걸음 나눔과 게시판, 함께하기" to "나눔 탭의 걸음 나눔과 게시판에서 걸음 나눔을 여시면 적힌 글이 다섯 개씩 나옵니다. 더 보기와 이전 보기로 넘기십니다. 글을 두드리시면 글과 댓글을 읽고, 댓글을 달거나 네 자리 숫자로 그 글을 가리실 수 있습니다. 한마디 적기 펼치기에서 별명, 하실 말씀, 지울 때 쓸 네 자리 숫자를 적어 올리십시오. 별명은 폰이 기억해 다음에 다시 적지 않으셔도 됩니다. 함께하기에는 자원봉사 요령, 시각장애인에게 말하는 법, 봉사시간, 관련 제도와 법, 후원과 광고 원칙이 마당별로 담겨 있습니다. 마당 제목을 두드리시면 펼쳐집니다.",
            "길 부탁하기" to "나눔 탭의 길 부탁하기에서 새로 부탁하기를 누르십시오. 어디서에 이름을 적고 엔터를 치시면 비슷한 곳이 다섯 곳씩 나오고, 고르시면 출발지가 됩니다. 지금 내 자리를 출발지로 단추도 있습니다. 어디까지도 같은 방법으로 고르시고, 언제쯤, 한마디, 별명을 적어 부탁 올리기를 누르십시오. 부탁하신 길은 자원봉사자의 오늘 걸을 길 맨 앞에 놓입니다. 부탁해 둔 길 보기에서 길을 두드리시면 자세히 듣고, 댓글을 달거나, 다 그렸습니다 표시를 하거나, 부탁을 내리실 수 있습니다. 댓글을 지우실 때는 댓글을 길게 누르십시오. 다 그려진 길은 그려졌습니다 — 이 길로 걷기를 누르시면 점지도 따라 걷기가 시작됩니다.",
            "알림 — 길눈을 만드는 곳에서 드리는 공지" to "설정 탭 맨 위의 알림에서 공지 목록을 다섯 건씩 보실 수 있습니다. 새 알림에는 새 알림이라고 붙습니다. 긴급 공지가 오면 길 찾기 첫 화면 맨 위에 긴급 공지 한 줄이 뜨고 읽어 드리며, 두드리시면 전문을 읽어 드리고 내립니다. 길눈은 켜져 있는 동안 15분마다, 그리고 길눈으로 돌아오실 때마다 새 공지를 살핍니다. 알림 화면의 긴급 공지를 폰 알림으로 받기를 누르고 허락하시면 폰 알림으로도 알려 드립니다.",
            "말소리를 꺼 두었을 때 긴급 공지" to "길눈 말소리를 꺼 두셨어도 긴급 공지는 톡백으로 읽어 드립니다. 읽은 공지는 다시 뜨지 않습니다.",
            "앱을 닫아 두어도 긴급 공지 받기" to "길눈이 켜져 있지 않을 때도 안드로이드가 틈틈이 길눈을 깨워 새 긴급 공지가 있는지 살피고, 있으면 폰 알림으로 알려 드립니다. 언제 깨울지는 안드로이드가 정하므로 15분에서 몇 시간 늦을 수 있습니다. 설정 탭의 알림 화면에서 폰 알림을 허락해 두셔야 하며, 폰 설정에서 길눈을 강제 중지하시거나 배터리 절약으로 막아 두시면 깨우지 못합니다.",
            "현 위치정보 말할 내용" to "설정 탭의 폰 펼치기 안에 있습니다. 지금 내 자리 듣기에서 도로명주소, 가까운 곳 이름, 지번주소, 국가지점번호, 위성 오차 가운데 무엇을 말할지 켜고 끄십니다. 처음에는 지번주소만 꺼져 있습니다. 지금 설정으로 내 자리 듣기로 곧바로 들어 보실 수 있습니다.",
            "여기서 점검" to "설정 탭의 여기서 점검에서 점검 시작을 누르시면 지금 이 자리에서 인터넷, 위치, 지금 자리 이름, 목적지 찾기, 가까운 점지도, 지하철 역 찾기, 둘레 찾기, 가 볼 곳, 말로 하기 허락, 말소리, 폰 알림을 하나씩 살펴 읽어 드립니다. 끝나면 걸린 곳만 모아 말씀드립니다. 점검 결과 클에게 보내기를 누르시면 결과가 나스에 남아 클이 그대로 읽습니다.",
            "탈것 저절로 알아채기와 지하철 역 알림" to "길눈은 폰의 걸음 센서와 흔들림, 기압계로 걷는지, 차를 탔는지, 버스인지, 지하철인지를 저절로 알아챕니다. 땅속에서도 됩니다. 걸음이 15초 이어지면 걷는 것으로, 걸음 없이 20초 넘게 움직이면 탈것으로 봅니다. 탈것이 땅속이거나 역 근처에서 탔으면 지하철, 정류장마다 서면 버스, 아니면 차입니다. 지하철로 가실 때는 먼저 가까운 역 출구까지 걷는 안내를 하고, 계단이나 에스컬레이터로 내려가시면 역에 들어오신 것을 알아채 타실 열차를 알려 드립니다. 열차가 움직이면 손대지 않으셔도 역 알림이 시작되고, 지나는 역마다 이름과 남은 정거장을 알려 드립니다. 이미 열차를 타고 계실 때는 지하철로 가자, 또는 전철로 가고 있어라고 말씀하시면 땅속으로 내려가시기 전 자리에서 가까운 역을 타신 역으로 잡아 곧장 역 알림을 시작합니다. 내리신 뒤 땅 위로 나와 걸으시면 지하철 안내를 마치고 남은 길을 걸어서 안내합니다. 몇 정거장 남았어라고 물으시면 지금 자리를 알려 드립니다.",
            "지역에 맞는 복지콜·교통약자 콜 부르기" to "길눈은 지금 계신 곳의 시·도와 시·군을 알아내 그 지역 교통약자 이동지원센터 번호를 먼저 알려 드립니다. 시·군 센터가 따로 없으면 도 광역센터 번호가 나옵니다. 말로 하실 때는 복지콜에 전화해 줘, 또는 장애인콜택시 불러 줘라고 하시면 어느 지역에서든 그 지역 센터로 전화 화면을 엽니다. 두리발, 나드리콜, 새빛콜, 반디콜처럼 그 지역 콜 이름에 콜이나 전화를 붙여 말씀하셔도 됩니다. 콜 번호 알려 줘라고 하시면 지금 계신 지역 이름과 번호를 읽어 드립니다. 화면에서는 설정의 내 서류 보관함, 복지콜·교통약자 콜 번호 펼치기에서 맨 위에 지금 계신 지역이 나오고 그 아래 번호마다 전화 걸기 단추가 있습니다. 처음 쓰시는 지역이면 이용 등록을 먼저 하라고 할 수 있으니, 그때는 같은 화면에 담아 둔 서류를 보내십시오. 위치를 아직 잡지 못했으면 그렇다고 알려 드리니 잠시 뒤 다시 해 주십시오.",
            "내 서류 보관함과 복지콜 등록" to "설정 탭의 내 서류 보관함에서 복지카드 앞면과 뒷면, 신분증, 장애정도 결정 통지서, 그 밖의 서류를 사진으로 담아 두십니다. 서류 담기 펼치기에서 어떤 서류인지 고르고 사진 찍기나 이미 찍어 둔 사진에서 고르기를 누르십시오. 적어 두기 펼치기에는 이름, 생년월일, 연락처, 주소를 적어 둡니다. 모두 이 폰 안에만 있고 서버로 가지 않으며 폰 백업에도 넣지 않습니다. 콜에 가입할 때 담아 둔 서류의 보내기를 누르시면 문자나 메일로 보내는 창이 열리고, 받는 곳은 직접 고르십니다. 서류를 빼실 때는 서류 이름을 길게 누르시거나 톡백 동작 메뉴에서 이 서류 빼기를 고르십시오. 지금 계신 지역의 복지콜과 교통약자 콜 번호도 이 화면의 펼치기 안에서 바로 거실 수 있습니다.",
            "말하기 설정 — 목소리" to "설정 탭의 말하기 설정에서 목소리를 누르실 때마다 폰에 있는 다음 한국어 목소리로 넘어갑니다. 더 좋은 목소리는 폰 설정의 텍스트 음성 변환 출력에서 한국어 음성 데이터를 내려받으시면 나타납니다. 말로 하기에서 목소리 바꿔라고 하셔도 됩니다. 경고는 안전을 위해 말소리를 꺼도 늘 말씀드립니다.",
            "말하기 설정 — 얼마나 자세히, 무엇을 말할지" to "설정 탭의 말하기 설정에서 고르십니다. 얼마나 자세히는 자세히, 보통, 짧게가 있고, 보통과 짧게에서는 남은 거리를 덜 자주 말씀드립니다. 꺾이는 곳 알리기를 켜 두시면 사거리와 갈림길을 알려 드리고, 몇 초 앞에서 알릴지는 5초, 8초, 12초 가운데 고르십니다. 되풀이 사이 시간은 한 번 말한 뒤 다시 말하기까지 쉬는 시간입니다. 지나는 곳 안내를 켜 두시면 차 안에서 지금 달리는 길을 알려 드리고, 말하는 간격은 30초, 60초, 120초 가운데 고르십니다. 걸을 때 확신음을 끄시면 짧은 맑은 소리가 나지 않고, 방향이 틀어졌을 때의 말은 그대로 나옵니다. 지금 설정으로 들어 보기로 들어 보실 수 있습니다. 경고는 어떤 설정에서도 말씀드립니다.",
            "길눈 목소리 — 마이크로소프트 선희" to "나스 음악 열쇠가 있는 폰에서는 길눈이 마이크로소프트의 선희 목소리로 말합니다. 웹 길눈에서 쓰시던 그 목소리입니다. 설정 탭의 말하기 설정에서 길눈 목소리를 끄시면 폰 목소리로 돌아갑니다. 빠르기는 설정 탭의 말 빠르기로 함께 조절됩니다. 한 번 들은 말은 폰에 모아 두어 다음부터는 곧바로 나옵니다. 처음 듣는 말은 나스에서 받아 오느라 1초 남짓 걸리고, 2초 넘게 받지 못하거나 통신이 끊기면 같은 말을 폰 목소리로 곧바로 대신합니다. 경고처럼 한순간이 급한 말은 기다리지 않고 늘 폰 목소리로 곧바로 말합니다. 정식 음성 열쇠를 넣으면 모든 분의 폰에서 이 목소리가 나오게 됩니다.",
            "현장영상해설 받기" to "둘러보기 탭의 현장영상해설 받기에서 세 가지 가운데 고르십시오. 지금 바로 현장영상해설사에게 화상통화 요청을 누르시면 협회 현장영상해설사에게 화상통화를 청하고 길 찾기 탭의 긴급통화서비스 화면이 열립니다. 준비된 현장영상해설 코스 듣기는 협회가 만들어 둔 코스의 현장영상해설을, 현장영상해설사 파견 신청하기는 날을 잡아 현장영상해설사와 함께 다니시도록 신청하는 화면을 앱 안에서 엽니다. 다 보신 뒤 닫기 단추를 누르시거나 폰의 뒤로 동작을 하시면 길눈으로 돌아옵니다. 말로 하기에서 현장영상해설 받고 싶어라고 하셔도 됩니다."
        )
    }
}
