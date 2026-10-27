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
//   둘러보기 탭 DulreoCheot(카메라 눈·둘레 찾기·사진 읽어 주기 — 현장영상해설 받기 줄은 2.58.0부터 감춤), 음악·방송 탭 BangsongCheot, 나눔 탭 NanumCheotHwamyeon
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
        System.setProperty("http.agent", Bangsong.UA)   // 2.25.0 모든 통신에 같은 이름표(서버 문지기, 이사장님 지적 뒤 전체 점검)
        Seoljeong.sijak(this)
        HaiGilnun.sijak(this)   // 2.26.0 하이 길눈 부르기(설정에서 켜야 들음)
        Girok.sijak(this)
        // 2.68.0 선로 맞추기 — 앱 안의 철길 자료(assets/cheolgil.bin)를 기차 안내가 처음 쓸 때 읽음
        val ac = applicationContext
        Cheolgil.ilgeum = { try { ac.assets.open("cheolgil.bin").use { it.readBytes() } } catch (e: Exception) { null } }
        Sori.sijak(this)
        Wichi.sijak(this)
        TalgeotGamji.sijak(this)   // 2.9.0 탈것 저절로 알아채기(걸음 센서·가속도·기압계, 땅속에서도)
        KolJiyeok.sijak(this)   // 2.8.0 콜 번호 지역 알아보기
        NnKol.batgi {}          // 2.8.0 콜 번호표(나스 call.json)를 미리 받아 둠 — 말로 부를 때 바로 쓰게
        ChaBureugi.sijak(this)  // 2.29.0 차 부르기 — 지역 이용 안내·이용 기록·차에 탄 것 알아채기·정기 호출(이사장님 승인)
        SinhogiEngine.sijak(this)   // 2.3.0 음향신호기 자동으로 잡기(설정에서 끔)
        MalHagi.sijak(this)         // 2.4.0 말로 하기 — 나스 알아듣기 사전을 받아 둠
        MalHagi.hwalseong = WeakReference(this)
        WatchLink.sijak(this)       // 2.5.0 갤럭시 워치와 잇기
        Dongyeong.watchBonae = { u, pail -> WatchLink.dongyeongBonae(u, pail) }   // 2.27.0 받은 동영상을 갤럭시 워치에서 틀기(워치 길눈 2.7.0)
        GinGeup.sijak(this)         // 2.6.0 긴급통화서비스
        GinGeup.hwalseong = WeakReference(this)
        // 2.7.0 통합(묶음 b1~b6) — 엔진 세우기. 설정·열쇠를 먼저, 여정 엔진은 점지도 이어 걷기보다 앞에(여정 잇는 자리를 채운 뒤 견줌)
        Yeolsoe.sijak(this)         // 나스 음악 열쇠 한 자리
        SeoljeongDeo.sijak(this)    // b6 설정에 더한 값(현 위치정보 말할 내용, 목소리, 선희 목소리)
        BangsongDuck.sijak(this)    // 2.27.0 다른 앱 음악을 길눈 말 동안만 작게
        JeonhwaGamsi.sijak(this)    // 2.30.0 전화가 오거나 걸면 길눈의 모든 소리를 멈춤(이사장님 지시)
        Bangsong.sijak(this)        // b5 음악·방송 — 멈춤 지킴이, 말로 하기·긴급통화 중 멈춤(재생기는 처음 틀 때 세움)
        DeutgiYeyak.sijak(this)     // 2.80.0 듣기 예약 — 정한 시각에 라디오 틀기·끄기(이사장님 승인 2026-10-27)
        Iljeong.sijak(this)         // 2.81.0 일정 — 아침 브리핑·떠날 시각 알람을 다시 걺(이사장님 승인 2026-10-27)
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
        // 2.38.0 (261013-A26, 이사장님 승인 2026-10-13, 아이폰 2.66.0과 같음) 누가 어떤 판을 쓰는지 — 설정의 내 이름(긴급통화 때 보이는 이름)과 호칭을 함께 남김
        Girok.namgi("app_sijak", mapOf("pan" to Pan.pan, "bild" to Pan.bild, "android" to Build.VERSION.SDK_INT,
            "ireum" to Seoljeong.naIrum, "hoching" to Seoljeong.hoching))
        JeomPan.jariMalHagi = { AnnaeEngine.jigeumJari() }   // 2.27.0 이어폰 다음 단추·따라 걷는 화면의 지금 내 자리 듣기도 길 찾기 첫 화면과 같은 말(가까운 출구·건물·날씨, 현 위치정보 말할 내용 설정)
        Nalssi.cheotMal()           // 2.27.0 앱을 켠 뒤 날씨 한 번(아이폰 2.13.0·웹 길눈과 같이)

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
        if (jeomIeum) cheotHwamyeonEuro(null)   // 2.7.0 b2 이어 걷기 — 2.49.0 걷는 동안의 단추는 길 찾기 첫 화면에
        // 2.20.0 처음 안내는 첫 화면을 바꾸지 않고 길 찾기 첫 화면 안 한 줄로(이사장님 승인)
        Heundeul.ginGeupYeolgi = { cheotHwamyeonEuro(GinGeupHwamyeon()) }   // 2.7.0 b2 흔들면 긴급통화 열기
        heorakCheong()
        // 2.22.0 새 판 알림과 업데이트(이사장님 승인) — 켤 때 살피고, 알림을 두드려 열렸으면 곧바로 업데이트
        Ollim.byeonhwa = {
            val h = wiHwamyeon
            if (h is GilChatgiSae || h is SeoljeongCheot) dasiGeurigi()
        }
        Ollim.sijak(this)
        Ollim.intentBoda(this, intent)
        barogagi(intent)
    }

    /** 2.27.0 앱 아이콘 바로가기(말로 하기·지금 내 자리 듣기) — 화면이 다 선 뒤 1.2초에 */
    private fun barogagi(i: Intent?) {
        val a = i?.action ?: return
        if (a != "kr.or.ada.app.gilnun.MALHAGI" && a != "kr.or.ada.app.gilnun.JARI" && a != JeonggiHochul.YEOLGI &&
            a != Iljeong.YEOLGI_ACHIM && a != Iljeong.YEOLGI_ANNAE) return
        val iljeongJangso = i.getStringExtra("jangso") ?: ""
        i.action = null   // 화면을 돌려도 다시 하지 않게
        Girok.namgi("barogagi", mapOf("a" to a.substringAfterLast('.')))
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            when {
                a == JeonggiHochul.YEOLGI -> JeonggiHochul.geolgi()   // 2.29.0 정기 호출 알림을 두드리셨을 때 — 바로 그 콜에
                a == Iljeong.YEOLGI_ACHIM -> Iljeong.beuriping(this)   // 2.81.0 아침 브리핑 알림을 두드리셨을 때 — 다시 읽어 드림
                a == Iljeong.YEOLGI_ANNAE -> { if (iljeongJangso.isNotBlank()) Iljeong.annaeMutgi(iljeongJangso) }   // 2.81.0 떠날 시각 알림 — 길 안내를 여쭘
                a.endsWith("MALHAGI") -> MalHagi.dudeurim()
                else -> AnnaeEngine.jigeumJari()
            }
        }, 1200)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Ollim.intentBoda(this, intent)   // 2.22.0 새 판 알림을 두드렸을 때
        barogagi(intent)
    }

    override fun onResume() {
        super.onResume()
        Ollim.hwakinIeogi(this)   // 2.25.0 화면이 꺼진 동안 못 연 설치 확인 화면을 이어 엶
        HaiGilnun.apDanggye(true)   // 2.26.0 하이 길눈 — 길눈이 앞에 있을 때만 들음
        if (Seoljeong.hiGilnun && Seoljeong.hiJamgeum) WichiService.kyeogi(this)   // 2.27.0 화면이 꺼져도 듣기 — 앞에 있을 때 마이크 쓰임을 알림 칸에 다시 올림
        Wichi.wiseongDolligi()
        Ollim.dorawatda(this)   // 2.22.0 앱으로 돌아오면 새 판 살피기(1시간에 한 번까지), 설치 허용을 켜고 오셨으면 업데이트 이어 하기
        ChaBureugi.dorawatda(this)   // 2.29.0 콜에 전화하고 돌아오시면 배차되었습니까 한 번
        Iljeong.ddeonaGyesan(this, false)   // 2.81.0 일정 — 떠날 시각을 지금 자리로(10분 안이고 500미터 안이면 그대로)
        // 2.77.0 (261025-A65, 이사장님 승인 2026-10-25) 위치 허락이 모자라면 처음과 나빠졌을 때 한 번 알리고 설정을 바로 여는 단추
        window.decorView.postDelayed({ if (!isFinishing) HeorakAllim.salpigi(this) }, 2500)
    }

    override fun onPause() {
        super.onPause()
        HaiGilnun.apDanggye(false)
        ChaBureugi.naganda()
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

    /** 2.27.0 기초 시험 화면의 위치 허락 단추 */
    fun wichiHeorakCheong() = heorakCheong()

    private fun heorakCheong() {
        val p = ArrayList<String>()
        p.add(Manifest.permission.ACCESS_FINE_LOCATION)
        p.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 29) p.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
        if (Seoljeong.sinhogiJadong) for (b in SinhogiEngine.pilyoHeorak()) if (b !in p) p.add(b)   // 2.3.0 음향신호기(근처 기기)
        val an = p.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (an.isEmpty()) { WichiService.kyeogi(this); return }
        getSharedPreferences("gilnun", MODE_PRIVATE).edit().putBoolean("gn.wichiHeorakCheongHam", true).apply()   // 2.77.0 한 번 여쭘 — 그 뒤 없으면 「거절됨」
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
        if (requestCode == Iljeong.HEORAK_BEON) {   // 2.81.0 일정 — 달력 읽기·쓰기 허락
            Iljeong.heorakGyeolgwa(this)
            return
        }
        if (requestCode == JeonggiHochul.HEORAK_BEON) {   // 2.29.0 정기 호출 — 전화 걸기·알림 허락
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED &&
                permissions.contains(Manifest.permission.CALL_PHONE)) Sori.mal("전화 걸기를 허락하셨습니다. 이제 콜을 부를 때 네 하시면 바로 걸립니다.")
            dasiGeurigi()
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
        MalHagi.pyeonjipKkeut()   // 2.31.0 화면을 새로 채우면 편집창도 새로 생기므로 편집 중 상태를 풂(하이 길눈이 다시 들음)
        nae.removeAllViews()
        cheotJul = null
        val st = gil[tab]
        val h = st.last()
        title = h.jemok
        if (st.size > 1) {
            val ap = st[st.size - 2].jemok
            danchu("뒤로 — $ap") { dwiro() }
        }
        // 2.44.0 (이사장님 승인 2026-10-14) 아이폰과 같게 — 길 찾기·둘러보기·음악·방송·나눔 탭 첫 화면에는 제목 줄을 두지 않음(설정은 아이폰처럼 둠)
        if (!(st.size == 1 && tab in 0..3)) jemok(h.jemok)
        cheotJul = null   // 2.46.0 (이사장님 승인 2026-10-15) 아이폰과 같게 — 속 화면이 열리면 커서를 뒤로 단추가 아니라 내용 첫 줄로
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
            hint = t   // 2.26.0 설명(contentDescription)은 달지 않음 — 톡백이 적어 둔 글 대신 설명을 읽던 일
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
        // 2.85.0 / 2.55.0 (261016-I39 / 261016-A43, 이사장님 승인 2026-10-16) 옛 첫 화면의 말로 하기 단추를 걷어 냄 —
        //   이 화면은 2.7.0부터 열리지 않고(첫 화면은 GilChatgiSae), 말로 하기 단추는 GilChatgiHwamyeon.kt 한 곳에만 둠
        // 2.86.0 / 2.56.0 (261016-I40 / 261016-A44, 이사장님 승인 2026-10-16) 옛 첫 화면의 긴급통화 단추(옛 글자 「긴급통화서비스 — 화상통화 요청」)를 걷어 냄 —
        //   이 화면은 2.7.0부터 열리지 않고, 긴급통화서비스 단추는 GilChatgiHwamyeon.kt 한 곳에만 둠
        // 2.2.0 따라 걷는 중이면 걷는 화면으로 가는 단추를 맨 위에
        if (JeomEngine.gil != null || JeomEngine.bulleoneun) {
            t.danchu(if (JeomEngine.dochakHam) "목적지에 닿았습니다 — 길 찾기 첫 화면으로" else "점지도 따라 걷는 중 — 길 찾기 첫 화면으로") { t.cheotHwamyeonEuro(null) }
        }
        t.danchu("지금 내 자리 다시 듣기") { jariMal() }
        t.danchu("점지도 따라 걷기") { t.yeolgi(JeomMokrokHwamyeon()) }
        t.pyeolchigi("음향신호기", sinhogiPyeol) { sinhogiPyeol = !sinhogiPyeol }
        if (sinhogiPyeol) {
            t.danchu("주변 신호기 살피기 — 있는지, 블루투스로 울릴 수 있는지") { t.sinhogiHagi { SinhogiEngine.juByeonSalpigi() } }   // 2.13.0
            t.danchu("음향신호기 위치 안내 울리기") { t.sinhogiHagi { SinhogiEngine.ulligi(1) } }
            t.danchu("음향신호기 신호 안내 울리기") { t.sinhogiHagi { SinhogiEngine.ulligi(2) } }
            val chatgiDanchu = t.danchu(SinhogiEngine.chatgiGeul) {
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
            // 2.87.0 / 2.57.0 (261016-I41 / 261016-A45, 이사장님 승인 2026-10-16) 찾기가 저절로 끝나면 글자만 그 자리에서 되돌림(길 찾기 첫 화면과 같은 방식, 자리마다 따로 받음)
            SinhogiEngine.byeonhwaDalgi("gilChatgiCheot") {
                if (t.wiHwamyeon === this) {
                    val n = SinhogiEngine.chatgiGeul
                    if (chatgiDanchu.text.toString() != n) chatgiDanchu.text = n
                }
            }
        }
        t.geul("목적지 찾기, 걸어갈까요, 차로갈까요, 지하철이 아이폰 길눈에서 묶음별로 옮겨 옵니다.")
    }


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
        // 2.77.0 (261025-A65, 이사장님 승인 2026-10-25, 아이폰 2.110.0과 같음) 위치·마이크 허락 — 지금 상태를 이름에 담고, 누르면 폰 설정의 길눈 화면이 바로 열림
        t.danchu("위치 허락 — ${HeorakAllim.wichiMal(t)}. 누르면 폰 설정의 길눈 화면을 엽니다. 권한, 위치에서 허용해 주십시오") { HeorakAllim.wichiSeoljeongYeolgi(t) }
        t.danchu("마이크 허락 — ${HeorakAllim.maikMal(t)}. 누르면 폰 설정의 길눈 화면을 엽니다") { HeorakAllim.maikSeoljeongYeolgi(t) }
        // 2.81.0 (261027-A69, 이사장님 승인 2026-10-27 「위치를 음악방송이 아니라 설정탭에」, 아이폰 2.116.0과 같음) 일정 — 폰 달력을 말로 듣고 넣고 고치기
        t.danchu("일정 — 일정 듣기와 넣기, 아침 브리핑, 떠날 시각 알림") { t.yeolgi(IljeongHwamyeon()) }
        // 2.42.0 (261014-A30, 이사장님 승인 2026-10-14 「1」) 설정 순서를 한 잣대로 — 자주 손대는 것부터, 시험용은 맨 뒤 더 보기 안에. 아이폰과 같은 순서·같은 이름
        //   말소리·말 빠르기는 말하기 설정 맨 위로, 보폭 재기는 점지도와 걸음 맨 위로, 내 이름 칸은 가족·지인 명단 안으로, 판 기록은 겉으로(길눈 정보)
        t.danchu("말하기 설정 — 켜기와 끄기, 빠르기, 목소리, 얼마나 자세히, 무엇을 말할지") { t.yeolgi(MalSeolHwamyeon()) }
        t.danchu("말로 하기 설정 — 하이 길눈, 호칭") { t.yeolgi(MalHagiSeolHwamyeon()) }
        t.danchu("점지도와 걸음 — 보폭 재기, 나만의 점지도, 걸음 오차 재기, 내 문, 점지도 안내 설정") { t.yeolgi(JeomSeoljeongHwamyeon()) }
        t.danchu("기기 설정 — 음향신호기, 이어폰 단추, 워치, 리모컨") { t.yeolgi(GigiSeoljeongHwamyeon()) }
        t.danchu("가족·지인 명단 — 이음 번호로 등록, 받는 분 화면에 뜰 내 이름") { t.yeolgi(JiinMyeongdanHwamyeon()) }
        t.danchu("내 서류 보관함 — 복지카드와 신분증 담아 두기, 복지콜 등록") { t.yeolgi(SeoryuhamHwamyeon()) }
        t.pyeolchigi("폰 — 흔들면 자리 번호, 현 위치정보 말할 내용", pon) { pon = !pon }   // 아이폰 "폰 펼치기 — 흔들면 자리 번호, 현 위치정보 말할 내용"
        if (pon) {
            t.danchu("흔들면 자리 번호 — 긴급통화 열기 또는 국가지점번호") { t.yeolgi(HeundeulHwamyeon()) }
            t.danchu("현 위치정보 말할 내용") { t.yeolgi(JariMalSeoljeongHwamyeon()) }
        }
        t.danchu("도움말") { t.yeolgi(DoumalHwamyeon()) }
        t.danchu("여기서 점검 — 지금 이 자리에서 무엇이 막혔는지 알아보기") { t.yeolgi(YeogiJeomgeomHwamyeon()) }
        t.danchu("새로고침 — 받아 둔 자료를 다시 받습니다. 내 설정과 여정은 그대로입니다") {
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
        t.danchu(Ollim.seoljeongMal(t)) { Ollim.seoljeongNureum(t) }   // 2.22.0 업데이트 — 새로고침 바로 아래 한 곳
        t.danchu("길눈 정보 — 판과 빌드, 고친 기록") { t.yeolgi(PanHwamyeon()) }
        t.pyeolchigi("더 보기 — 기초 시험, 아침 기록, 관리자 시험", deo) { deo = !deo }
        if (deo) {
            t.danchu("기초 시험 — 위치, 방향, 걸음, 말소리가 도는지 듣기") { t.yeolgi(GichoSiheom()) }
            t.danchu("아침 기록 — 지난 24시간 길눈과 자봉 쓰임, 오류") { t.yeolgi(AchimGirokHwamyeon()) }   // 2.30.0 관리자(이사장님 승인)
            t.danchu("관리자 시험 — 카메라 눈(문 찾기, 발 앞 계단·턱 알림)") { t.yeolgi(KameraGwanliHwamyeon()) }   // 2.7.0 b3 — 열쇠가 없으면 열쇠 넣기
            t.danchu("관리자 시험 — 말벗 견주기, 서버 대답 빠르기") { t.yeolgi(MalbeotGyeonjugiHwamyeon()) }   // 2.19.0
        }
    }
}

/** 2.27.0 기기 설정 — 음향신호기, 이어폰 단추, 워치, 리모컨을 한곳에(아이폰 GigiSeoljeongView 와 같은 차례, 같은 말) */
class GigiSeoljeongHwamyeon : Hwamyeon("기기 설정") {
    override fun chaeugi(t: GilnunActivity) {
        t.danchu("음향신호기 자동 울리기 — " + (if (Seoljeong.sinhogiJadong) "켜져 있음 (누르면 끕니다)" else "꺼져 있음 (누르면 켭니다)")) {
            val on = !Seoljeong.sinhogiJadong
            Seoljeong.sinhogiJadong = on
            SinhogiEngine.jadongKyeogi(on)
            Sori.mal(if (on) "음향신호기 자동으로 잡기를 켰습니다. 신호기가 가까이 잡히면 길눈이 스스로 울립니다."
                else "음향신호기 자동으로 잡기를 껐습니다. 길 찾기 탭의 그 밖에 펼치기에서 손으로 울리실 수 있습니다.")
            t.dasiGeurigi()
            if (on) t.sinhogiHagi { }   // 허락이 없거나 블루투스가 꺼져 있으면 그 자리에서
        }
        t.danchu("안내 중 이어폰 단추 받기 — " + (if (JeomSeol.ieoponDanchu) "켜져 있음 (누르면 끕니다)" else "꺼져 있음 (누르면 켭니다)")) {
            JeomSeol.ieoponDanchu = !JeomSeol.ieoponDanchu
            RemoteDanchu.matchugi()   // 2.42.0 점지도 안내 설정에 있던 것과 같게
            Sori.mal(if (JeomSeol.ieoponDanchu) "안내 중에는 이어폰 단추를 길눈이 받습니다. 재생은 다시 듣기, 다음은 다음 갈림길, 이전은 앞 안내입니다."
                else "이어폰 단추를 음악 앱이 받습니다.")
            t.dasiGeurigi()
        }
        t.danchu("주변 신호기 살피기 — 있는지, 블루투스로 울릴 수 있는지") { t.sinhogiHagi { SinhogiEngine.juByeonSalpigi() } }
        val chatgiDanchu = t.danchu(SinhogiEngine.chatgiGeul) {
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
        // 2.87.0 / 2.57.0 (261016-I41 / 261016-A45, 이사장님 승인 2026-10-16) 찾기가 저절로 끝나면 글자만 그 자리에서 되돌림(길 찾기 첫 화면과 같은 방식, 자리마다 따로 받음)
        SinhogiEngine.byeonhwaDalgi("gigiSeoljeong") {
            if (t.wiHwamyeon === this) {
                val n = SinhogiEngine.chatgiGeul
                if (chatgiDanchu.text.toString() != n) chatgiDanchu.text = n
            }
        }
        t.geul("음향신호기 자동 울리기를 켜 두시면 블루투스 음향신호기가 가까이 잡힐 때 위치 안내를, 그 앞에 머무르시면 신호 안내를 스스로 울립니다. 이어폰 단추는 안내 중에만 길눈이 받습니다. 재생은 다시 듣기(신호기 앞이면 신호 안내), 다음은 다음 갈림길, 이전은 앞 안내입니다.")
        t.danchu("리모컨 배우기 — 블루투스 리모컨 단추를 익혀 두기") { t.yeolgi(RimoHwamyeon()) }
        val wb = Seoljeong.watchBeonho.toList().joinToString(" ")
        t.geul("갤럭시 워치는 폰과 저절로 이어집니다. 워치 번호 $wb")
    }
}

/** 2.18.0 처음 안내 — 흰지팡이와 보폭 재기(이사장님 승인 2026-10-06, 아이폰 2.50.0과 같음). 보폭을 재 두신 분께는 띄우지 않음 */
class CheotAnnaeHwamyeon : Hwamyeon("길눈에 오신 것을 환영합니다") {
    private var malHam = false
    override fun chaeugi(t: GilnunActivity) {
        if (Seoljeong.bopokJaem) {
            // 보폭 재기에서 돌아오심 — 다 됐다고 알리고 길 찾기 첫 화면으로
            Seoljeong.cheotAnnae = true
            Girok.namgi("cheot_bopok")
            Sori.mal("보폭을 쟀습니다. 이제 걸음 수로 안내합니다. 길을 나서실 때는 꼭 흰지팡이를 짚어 주십시오.")
            t.cheotHwamyeonEuro(null)
            return
        }
        if (!malHam) {
            malHam = true
            Sori.mal("길눈에 오신 것을 환영합니다. 단독보행을 하실 때는 반드시 흰지팡이를 짚어 주십시오. 처음 한 번 보폭을 재 주십시오.")
        }
        t.geul("길눈은 혼자 걷기를 돕는 도구입니다. 단독보행을 하실 때는 반드시 흰지팡이를 짚어 주십시오. 길눈의 안내보다 흰지팡이와 주변 소리를 먼저 믿으십시오.")
        t.geul("길눈은 걸음 수로 길을 안내합니다. 걸음 수가 정확하려면 처음 한 번 내 보폭을 재야 합니다. 거리를 미리 아는 곳에서 10미터쯤 평소대로 걸으시면 됩니다.")
        t.danchu("보폭 재기 — 처음 한 번, 10미터쯤 걸어 재기") { t.yeolgi(BopokHwamyeon()) }
        t.danchu("나중에 재기 — 그때까지는 미터로 안내") {
            Seoljeong.cheotAnnae = true
            Girok.namgi("cheot_najunge")
            Sori.mal("알겠습니다. 보폭을 재시기 전까지는 걸음 수 대신 미터로 안내합니다. 보폭은 설정에서 언제든 재실 수 있습니다.")
            t.cheotHwamyeonEuro(null)
        }
    }
}

/** 2.19.0 말벗 견주기 — 관리자 시험(이사장님 승인 2026-10-06, 아이폰 2.51.0과 같음). 안드로이드는 서버 대답만 잼 */
class MalbeotGyeonjugiHwamyeon : Hwamyeon("관리자 시험 — 말벗 견주기") {
    private val MUREUM = listOf("현장영상해설이 뭐야", "길눈은 누가 만들었어", "비 오는 날 걸을 때 조심할 게 뭐야", "오늘 기분이 좀 우울해", "흰지팡이는 왜 짚어야 해")
    private val jul = ArrayList<String>()
    private var doneun = false
    override fun chaeugi(t: GilnunActivity) {
        t.danchu(if (doneun) "견주는 중 — 질문 다섯 가지를 차례로 묻습니다" else "견주기 시작 — 같은 질문 다섯 가지를 서버에 묻기") { if (!doneun) sijak(t) }
        if (jul.isNotEmpty()) {
            val v = t.geul("결과")
            if (!doneun) t.chojeomJul(v)
            for (s in jul) t.geul(s)
        }
    }

    private fun sijak(t: GilnunActivity) {
        doneun = true
        jul.clear()
        jul.add("안드로이드에서는 폰 안 인공지능 견주기를 할 수 없어 서버 대답만 잽니다.")
        Sori.mal("견주기를 시작합니다. 질문 다섯 가지를 차례로 묻습니다. 한 질문에 반 분쯤 걸릴 수 있습니다.")
        t.dasiGeurigi()
        mutgi(t, 0)
    }

    private fun mutgi(t: GilnunActivity, i: Int) {
        if (i >= MUREUM.size) {
            doneun = false
            Sori.mal("견주기를 마쳤습니다. 결과를 아래에 적었습니다.")
            Girok.namgi("malbeot_gyeonju", mapOf("pon" to false))
            t.dasiGeurigi()
            return
        }
        val q = MUREUM[i]
        val t0 = System.currentTimeMillis()
        Tongsin.json("malbeot.php", mapOf("mal" to q), 60000) { o ->
            val cho = String.format(java.util.Locale.US, "%.1f", (System.currentTimeMillis() - t0) / 1000.0)
            val d = o?.optString("dap", "")?.takeIf { it.isNotEmpty() } ?: o?.optString("msg", "") ?: ""
            jul.add("${i + 1}. $q\n서버 ${cho}초: " + (if (d.isEmpty()) "대답 없음" else d))
            t.dasiGeurigi()
            mutgi(t, i + 1)
        }
    }
}

class BopokHwamyeon : Hwamyeon("내 보폭 재기") {
    private var sijakSu = -1
    private var jaelGeori = 20.0
    private var gijunGeomsa: GeoreumGijun? = null   // 2.34.0 (261010-A22, 이사장님 승인 2026-10-10) 시작 직후 몰려 들어오는 헛걸음 빼기
    override fun chaeugi(t: GilnunActivity) {
        // 2.27.0 혼자 걸을 때와 동반자와 걸을 때를 따로 재 둠(아이폰 BopokView 와 같음)
        val mi = Seoljeong.bopokModeIreum()
        t.geul(if (Seoljeong.bopokJaem) "$mi 보폭은 ${(Seoljeong.bopok * 100).toInt()}센티미터입니다. 백 걸음이면 ${(Seoljeong.bopok * 100).toInt()}미터입니다."
            else "$mi 보폭은 아직 재지 않았습니다. 지금은 65센티미터로 셉니다.")
        t.danchu("재는 쪽 — 지금 $mi (누르면 바뀝니다)") {
            Seoljeong.bopokMode = if (Seoljeong.bopokMode == "honja") "dongban" else "honja"
            Sori.mal("${Seoljeong.bopokModeIreum()} 보폭으로 바꾸었습니다." + (if (Seoljeong.bopokJaem) "" else " 이쪽은 아직 재지 않으셨습니다."))
            t.dasiGeurigi()
        }
        if (sijakSu < 0) {
            val e = t.ipryeok("잴 거리(미터) — 스무 걸음 넘게 걸을 만한 거리", true)
            e.setText(jaelGeori.toInt().toString())
            t.danchu("보폭 재기 시작") {
                val m = e.text.toString().trim().toDoubleOrNull()
                if (m == null || m < 3) { Sori.mal("잴 거리를 미터로 적어 주십시오. 스무 걸음 넘게 걸을 만한 거리가 좋습니다."); return@danchu }
                if (!Wichi.georeumHeorak) { Sori.mal("걸음을 세려면 신체 활동 허락이 필요합니다. 폰 설정의 앱, 길눈, 권한에서 신체 활동을 허용해 주십시오."); return@danchu }
                jaelGeori = m
                sijakSu = Wichi.georeumSu
                gijunGeomsa?.meomchum(); gijunGeomsa = GeoreumGijun().seuseuro()
                Sori.mal("${m.toInt()}미터를 평소대로 걸으신 뒤 다 걸었습니다를 눌러 주십시오. 지금부터 셉니다.")
                t.dasiGeurigi()
            }
        } else {
            t.danchu("다 걸었습니다") {
                val n = gijunGeomsa?.georeum() ?: (Wichi.georeumSu - sijakSu)
                gijunGeomsa = null
                sijakSu = -1
                if (n < 5) { Sori.mal("걸음이 ${n}밖에 잡히지 않았습니다. 폰을 손에 들거나 주머니에 넣고 다시 해 주십시오."); t.dasiGeurigi(); return@danchu }
                val b = jaelGeori / n
                if (b < 0.2 || b > 1.5) { Sori.mal("보폭이 " + "%.2f".format(b) + "미터로 나와 이상합니다. 거리를 다시 확인하고 한 번 더 해 주십시오."); t.dasiGeurigi(); return@danchu }
                Seoljeong.bopok = b
                Girok.namgi("bopok", mapOf("m" to jaelGeori, "n" to n, "mo" to Seoljeong.bopokMode))
                Jeomjido.bopokNamgigi(jaelGeori, n, b, Seoljeong.bopokMode)   // 2.27.0 아이폰처럼 협회 서버에도 남김
                Sori.mal("${jaelGeori.toInt()}미터를 ${n}걸음에 걸으셨습니다. 보폭은 ${(b * 100).toInt()}센티미터입니다. 이 폰이 기억합니다.")
                t.dasiGeurigi()
            }
        }
        t.geul("한 번만 재 두면 이 폰이 기억합니다. 혼자 걸을 때와 동반자와 걸을 때를 따로 재 둘 수 있습니다. 점지도를 따라 걸을 때 걸음 수를 이 보폭으로 셉니다.")
    }
}

class GichoSiheom : Hwamyeon("기초 시험") {
    private var bangbeop = false
    private var hanbeon = false
    override fun chaeugi(t: GilnunActivity) {
        // 2.27.0 아이폰 기초 시험과 같이 — 30분 동안 1분마다 말하고 나스에 남김(긴급한 것부터 맨 위에)
        val g = GichoSiheomEngine
        g.byeonhwa = { if (t.wiHwamyeon === this) t.dasiGeurigi() }
        if (!Wichi.heorakItda) t.danchu("위치 허락이 없습니다 — 허락하기") { t.wichiHeorakCheong() }
        if (g.doneunJung) {
            t.danchu("기초 시험 중 — 그만하기") { g.machim(true) }   // 2.87.0 / 2.57.0 (261016-I41 / 261016-A45, 이사장님 승인 2026-10-16) 1분마다 바뀌던 「N분째」를 뺌(아이폰과 같게)
        } else {
            t.danchu("기초 시험 시작 — 30분 동안 1분마다 말씀드립니다") { g.sijak(t) }
            if (g.majimakGyeolgwa.isNotEmpty()) t.geul("지난 결과 — " + g.majimakGyeolgwa)
        }
        t.pyeolchigi("시험하는 법", bangbeop) { bangbeop = !bangbeop }
        if (bangbeop) {
            t.geul("시작을 누르신 뒤 아래 다섯 가지를 차례로 해 보십시오. 1분마다 길눈이 말하면 이어지고 있는 것입니다.")
            t.geul("화면을 끄고 잠그십시오.")
            t.geul("폰을 주머니에 넣으십시오.")
            t.geul("음악이나 라디오를 트십시오. 길눈이 말할 때만 음악 소리가 잠시 작아집니다.")
            t.geul("비행기 모드를 켜서 통신을 끊어 보십시오. 위치는 계속 받고, 기록은 폰에 쌓였다가 통신이 이어지면 나스로 갑니다.")
            t.geul("톡백을 켠 채로 해 보십시오.")
        }
        t.pyeolchigi("하나씩 듣기", hanbeon) { hanbeon = !hanbeon }
        if (!hanbeon) return
        t.danchu("허락 상태 듣기") {
            Sori.mal("위치 허락 " + (if (Wichi.heorakItda) "있음" else "없음") + ", 신체 활동 허락 " + (if (Wichi.georeumHeorak) "있음" else "없음") + ".")
        }
        t.danchu("위성 듣기") {
            val j = Wichi.jigeum
            Sori.mal(if (j == null) "아직 위치를 받지 못했습니다. 받은 횟수 ${Wichi.batunSu}번." else "오차 ${j.ochae.toInt()}미터, " + (if (j.georeumChu) "걸음으로 이어 센 자리" else "위성 자리") + ", 받은 횟수 ${Wichi.batunSu}번.")
        }
        t.danchu("방향 듣기") { Sori.mal(if (Wichi.nachimban >= 0) "폰 방향은 ${Wichi.nachimban.toInt()}도입니다." else "방향을 아직 모릅니다.") }
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
    // 2.26.0(261007-A14, 이사장님 승인 「그래」) 아이폰 도움말처럼 찾기 칸(낱말을 넣고 엔터)과 다섯 개씩 보기·더 보기·이전 보기.
    // 찾으면 결과 첫 줄로 커서를 옮김. 항목을 누르면 바로 아래에 내용이 펼쳐지고 다시 누르면 접힘
    private var yeollin = -1
    private var mal = ""
    private var chajeun = ""
    private var sijak = 0
    private var cheotJulRo = false
    private var gangjwaBureum = false   // 2.69.0 동영상 강의 차례를 한 번 받음

    private fun ttuk(s: String) = s.replace(" ", "").lowercase()

    override fun chaeugi(t: GilnunActivity) {
        val e = t.ipryeok("도움말 찾기 — 낱말을 넣고 엔터", false)
        e.setText(mal)
        e.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        e.doAfterTextChanged { mal = it?.toString() ?: "" }
        e.setOnEditorActionListener { _, id, ev ->
            val enter = id == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH || id == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                id == android.view.inputmethod.EditorInfo.IME_ACTION_GO ||
                (ev != null && ev.keyCode == android.view.KeyEvent.KEYCODE_ENTER && ev.action == android.view.KeyEvent.ACTION_DOWN)
            if (enter) { chajeun = mal.trim(); sijak = 0; yeollin = -1; cheotJulRo = true; t.dasiGeurigi() }
            enter
        }
        // 2.69.0 (261018-A57, 이사장님 승인 2026-10-18 「1」) 동영상 강의 — 길눈 사용법 26편, 항목마다 그 기능의 강의
        t.danchu("동영상 강의 — 길눈 사용법 26편") { t.yeolgi(GangjwaHwamyeon()) }
        Gangjwa.damunGeot(t)
        if (!gangjwaBureum) {
            gangjwaBureum = true
            val bieoitda = Gangjwa.pyeon.isEmpty()
            Gangjwa.batgi(t) { if (bieoitda && Gangjwa.pyeon.isNotEmpty() && yeollin >= 0 && t.wiHwamyeon === this) t.dasiGeurigi() }
        }
        val q = ttuk(chajeun)
        val l = DOUMAL.withIndex().filter { q.isEmpty() || ttuk(it.value.first).contains(q) || ttuk(it.value.second).contains(q) }
        if (q.isNotEmpty()) {
            if (l.isEmpty()) { val g = t.geul("「$chajeun」이 든 도움말이 없습니다. 다른 낱말로 찾아 주십시오."); if (cheotJulRo) { cheotJulRo = false; t.chojeomOmgigi(g) }; return }
            t.danchu("찾기 지우기 — 도움말 전체 보기") { mal = ""; chajeun = ""; sijak = 0; yeollin = -1; t.dasiGeurigi() }
        }
        if (sijak >= l.size) sijak = 0
        val kkeut = minOf(sijak + 5, l.size)
        var cheot: android.view.View? = null
        for (n in sijak until kkeut) {
            val (i, jn) = l[n]
            val (jemok, naeyong) = jn
            val b = t.danchu(if (yeollin == i) "$jemok 접기" else jemok) {
                yeollin = if (yeollin == i) -1 else i
                t.dasiGeurigi()
            }
            if (cheot == null) cheot = b
            if (yeollin == i) {
                t.geul(naeyong)
                Gangjwa.matneun(jemok)?.let { p -> t.danchu("이 기능 동영상 강의 보기 — ${p.title}") { Gangjwa.teulgi(t, p) } }   // 2.69.0
            }
        }
        if (kkeut < l.size) t.danchu("더 보기") { sijak += 5; yeollin = -1; cheotJulRo = true; t.dasiGeurigi() }
        if (sijak > 0) t.danchu("이전 보기") { sijak = maxOf(0, sijak - 5); yeollin = -1; cheotJulRo = true; t.dasiGeurigi() }
        if (cheotJulRo && cheot != null) { cheotJulRo = false; t.chojeomOmgigi(cheot) }
    }

    companion object {
        val DOUMAL = listOf(
            // 2.69.0 (261018-A57, 이사장님 승인 2026-10-18 「1」, 아이폰 2.100.0과 같음) 동영상 강의
            "동영상 강의 — 길눈 사용법 26편" to "설정 탭의 도움말을 여시면 맨 위 찾기 칸 바로 아래에 동영상 강의가 있습니다. 누르시면 길눈 안드로이드 사용법 26편의 차례가 다섯 편씩 나오고, 편 이름을 누르시면 앱 안에서 바로 틀어 드립니다. 로그인이나 가입은 필요 없습니다. 틀면 뒤로, 재생과 멈춤, 10초 뒤로, 10초 앞으로, 다음 편 단추가 있습니다. 한 편을 다 보시면 다음 편 이름을 알려 드립니다. 도움말 한 항목을 펼치시면 내용 아래에 그 기능의 동영상 강의 보기 단추가 있는 것도 있습니다. 전화가 오거나 걸면 강의는 멈추고, 재생 단추로 다시 트십시오. 같은 강의는 협회 홈페이지 온라인강좌에서도 누구나 보실 수 있습니다. 어느 편을 몇 번 틀었는지는 이름 없이 협회로 보내 강의를 고치는 데 씁니다.",
            "길 찾기 첫 화면 차례" to "길 찾기 탭 첫 화면은 아이폰과 안드로이드가 같은 차례입니다. 하나, 하던 일 멈추기. 둘, 어디로 가실까요 칸 — 이름이나 주소를 넣고 엔터. 셋, 즐겨찾기. 넷, 차 부르기. 다섯, 지금 내 자리 듣기. 여섯, 말로 하기. 일곱, 긴급통화서비스. 여덟, 그 밖에 펼치기. 읽지 않은 긴급 공지, 새 판 알림, 통화로 멈춘 방송 이어 듣기가 있으면 그 줄이 맨 위에 생기고, 보폭을 아직 재지 않으셨으면 하던 일 멈추기 아래에 처음 안내가 생깁니다. 걷거나 차를 타고 가시는 중에는 어디로 가실까요 칸부터 지금 내 자리 듣기까지 대신 지금 여정의 차례와 단추가 나옵니다.",   // 2.43.0 (이사장님 승인 2026-10-14)
            "설정 탭 차례 — 어디에 무엇이 있는지" to "설정 탭은 아이폰과 안드로이드가 같은 차례, 같은 이름입니다. 자주 손대는 것이 위에, 시험용은 맨 아래 펼치기 안에 있습니다. 하나, 알림(읽지 않은 긴급 공지가 있으면 이 줄에 함께 나옵니다). 둘, 위치 허락과 마이크 허락 — 지금 상태를 듣고 폰 설정을 엽니다. 셋, 일정 — 일정 듣기와 넣기, 아침 브리핑, 떠날 시각 알림. 넷, 말하기 설정 — 맨 위에 길눈 말소리 켜기와 끄기, 빠르기, 그 아래 목소리, 얼마나 자세히, 무엇을 말할지. 다섯, 말로 하기 설정 — 하이 길눈, 길눈이 부르는 내 호칭. 여섯, 점지도와 걸음 — 맨 위에 보폭 재기, 그 아래 나만의 점지도, 걸음 오차 재기, 내 문. 일곱, 기기 설정 — 음향신호기, 이어폰 단추, 워치, 리모컨 배우기. 여덟, 가족·지인 명단 — 이음 번호 받기와 받는 분 화면에 뜰 내 이름. 아홉, 내 서류 보관함. 열, 폰 펼치기 — 흔들면 자리 번호, 현 위치정보 말할 내용. 열하나, 도움말. 열둘, 여기서 점검. 열셋, 새로고침. 열넷, 업데이트. 열다섯, 길눈 정보 — 판과 빌드, 고친 기록. 열여섯, 더 보기 펼치기 — 기초 시험, 그리고 관리자 폰에서만 보이는 아침 기록과 관리자 시험.",   // 2.42.0 설정 탭 차례(이사장님 승인 2026-10-14)
            // 2.29.0 차 부르기(이사장님 승인 2026-10-09, 아이폰 2.58.0과 같음)
            "차 부르기 — 복지콜, 교통약자 콜, 지역 이용 조건" to "길 찾기 탭의 즐겨찾기 바로 아래에 차 부르기가 있습니다. 열면 지금 계신 지역을 알려 드리고, 맨 위의 부르기 단추를 누르시면 「○○에 전화할까요?」라고 여쭙고 마이크를 엽니다. 네 하시면 길눈이 그 지역 첫째 수단에 전화를 겁니다. 서울에서는 복지콜이 맨 앞입니다. 다른 수단 펼치기에는 나비콜, 장애인콜택시 같은 나머지 번호가 있습니다. 걸기 전에 상담원께 말할 것, 곧 출발 주소, 타는 곳(가까운 지하철 출구와 버스정류장 이름·정류장 번호), 도착지, 보조견과 함께 타시는지를 들려 드리고, 통화 중에도 알림 칸에서 다시 보실 수 있게 남겨 둡니다. 차 부르기를 처음 여실 때 전화 걸기 허락을 한 번 여쭙니다. 허락하시면 네 한마디로 말이 끝난 뒤 바로 걸리고, 허락하지 않으시면 전화 화면에서 통화 단추를 한 번 누르시면 됩니다. 그 밖에 펼치기에는 이 지역 이용 조건이 있습니다. 시각장애인도 탈 수 있는지, 미리 등록해야 하는지, 서류를 보내는 팩스와 메일을 알려 드립니다. 공식 누리집으로 확인한 곳만 조건을 말씀드리고, 확인하지 못한 곳은 전화로 먼저 물어보시라고 말씀드립니다. 메일로 등록받는 곳이면 복지카드를 이 메일로 보내기를 누르십시오. 보내는 창에서 지메일 같은 메일 앱을 고르시면 받는 곳과 내 서류 보관함의 복지카드가 채워져 있고, 확인하신 뒤 보내기를 누르시면 됩니다. 말로 하기에서 복지콜 불러 줘, 콜 이용 조건 알려 줘라고 하셔도 됩니다. 전화할까요에는 네뿐 아니라 오케이 알았어 네처럼 말끝에 네를 붙이셔도, 복지콜에 전화해 줘나 통화라고 하셔도 거는 것으로 받습니다. 말로 하기에서 마이크에 소리가 하나도 들어오지 않는 일이 두 번 잇달면 까닭을 한 번 말씀드리고 저절로 듣기를 쉽니다. 이어폰이나 블루투스를 살피신 뒤 말로 하기 단추를 누르시면 다시 듣습니다.",   // 2.71.0 (261021-A59)
            "정기 호출 — 출퇴근처럼 정한 시각에 콜 요청" to "차 부르기의 정기 호출에서 켜기, 부를 시각, 도착할 곳, 도착 희망 시각을 정하고 정하기를 누르십시오. 시각은 7시 30분, 또는 0730처럼 넣으시면 됩니다. 처음에는 월요일부터 금요일까지입니다. 정한 시각이 되면 알림과 소리로 깨워 드리고, 알림을 한 번 두드리시면 그 콜에 전화가 걸립니다. 전화 걸기 허락하기를 누르고 허락하시면 통화 단추를 다시 누르지 않아도 바로 걸립니다. 폰이 정확한 시각에 알리게 허락하기 단추가 보이면 눌러서 알람 및 리마인더를 허용해 주십시오. 허용하지 않으시면 몇 분 늦게 알릴 수 있습니다. 공휴일은 저절로 건너뜁니다. 그날만 바꾸실 때는 말로 하기에서 내일은 쉬어, 내일은 8시로 바꿔, 금요일은 병원으로라고 말씀하십시오. 병원처럼 도착을 바꾸실 곳은 즐겨찾기에 담아 두셔야 합니다. 처음 정하실 때도 말로 됩니다. 평일마다 아침 7시 30분에 복지콜 불러 줘, 사무실까지 9시. 정기 호출 꺼 줘, 정기 호출 켜 줘, 정기 호출 알려 줘도 됩니다. 요일, 출발, 수단, 보조견, 내일 하루 쉬기는 자세히 펼치기에 있습니다.",   // 2.88.0 / 2.58.0 (261016-I42 / 261016-A46, 이사장님 승인 2026-10-16) 제목 부르기 → 요청
            "나의 이용 성적표 — 콜이 얼마나 잡히는지" to "길눈으로 콜을 부르시면 몇 시에 어디로 걸었는지 저절로 남깁니다. 통화를 마치고 길눈으로 돌아오시면 배차되었습니까 하고 한 번만 여쭙니다. 되었다, 기다리라고 했다, 안 된다고 했다 가운데 말씀하시거나 화면 단추를 누르십시오. 안 된다고 하면 나비콜처럼 다음 수단에 걸지 여쭙니다. 차에 타시면 길눈이 저절로 알아채 몇 분 기다리셨는지 남깁니다. 차 부르기의 그 밖에 펼치기에서 나의 이용 성적표를 보시거나, 말로 하기에서 콜 성적표 알려 줘라고 하시면 이번 달 부른 횟수, 탄 횟수, 평균 기다린 시간, 가장 안 잡힌 시간대를 알려 드립니다. 기록은 이 폰 안에만 있습니다. 이름 없이 협회로 보내기를 켜신 분의 기록만, 받는 곳이 준비되는 대로 이름과 번호 없이 협회로 모아 지역마다 얼마나 잡히는지 알리는 근거로 씁니다.",
            // 2.85.0 / 2.55.0 (261016-I39 / 261016-A43, 이사장님 승인 2026-10-16) 자리는 말로 하기 설정, 안드로이드 12 이상
            "하이 길눈 부르기 — 설정에서 켜기" to "설정 탭의 말로 하기 설정에 있는 「하이 길눈 부르기」를 켜시면, 길눈 화면이 켜져 있는 동안 하이 길눈이라고 부르시면 말로 하기 단추를 누른 것처럼 네 하고 명령을 듣습니다. 처음에는 꺼져 있습니다. 폰 안 받아쓰기로 들어 통신이 없어도 되고, 안드로이드 12 이상이면서 폰 안 받아쓰기가 있는 폰에서만 됩니다. 길눈이 말하는 동안, 말로 하기 중, 긴급통화 중에는 듣지 않습니다. 처음에는 길눈 화면이 켜져 있을 때만 듣고, 바로 아래 화면이 꺼져도 하이 길눈 듣기를 켜시면 화면이 꺼지거나 다른 앱을 쓰실 때도 알림 칸에 길눈이 떠 있는 동안 듣습니다. 안드로이드 12 이상에서 되고, 배터리를 조금 더 씁니다. 폰에 따라 듣기를 다시 열 때 작은 소리가 날 수 있습니다. 거슬리시면 끄시고 말로 하기 단추나 이어폰 단추 길게 누르기를 쓰십시오.",
            "도움말 찾기 — 낱말을 넣고 엔터" to "도움말 맨 위 찾기 칸에 찾고 싶은 낱말(예: 방송, 지하철, 문)을 넣고 엔터를 치시면 그 낱말이 든 도움말만 다섯 개씩 나오고 커서가 첫 결과로 갑니다. 아래의 더 보기와 이전 보기로 넘기시고, 찾기 지우기를 누르시면 도움말 전체로 돌아갑니다. 항목을 누르면 바로 아래에 내용이 펼쳐지고 다시 누르면 접힙니다.",
            "걷는 중 안내 — 하던 일 멈추기, 걸음 수, 건널목, 문 찾기" to "길 찾기 첫 화면 맨 위에는 하던 일 멈추기가 늘 있습니다. 무엇을 하고 있든 누르시면 길 안내, 문 찾기, 신호기 찾기 같은 모든 일을 멈추고 목적지 적는 칸으로 갑니다. 걸어서 가실 때 보폭을 재 두셨으면 걷는 동안 말씀드리는 모든 거리를 걸음 수로 말씀드리고, 300미터가 넘으면 미터도 함께 말씀드립니다. 협회 서버가 아는 건널목, 계단, 다리, 지하도는 열다섯 미터쯤 앞에서 몇 걸음 앞에 건널목이 있다고 미리 알리고, 닿으면 건널목 앞입니다, 신호와 차 소리를 확인하신 뒤 건너십시오라고 한 번 더 말씀드립니다. 건널목이 가까워지면 음향신호기를 저절로 8초 살펴 울릴 수 있는 신호기가 있는지 알려 드립니다. 도착한 뒤 카메라 문 찾기는 안드로이드 길눈에서 아직 관리자 시험 중이라 나스 음악 열쇠를 넣은 폰에서만 켜집니다. 그 폰에서는 8초마다 지금 문을 찾고 있다고 알리고, 30초 안에 문을 찾지 못하면 바로 말씀드린 뒤 사진 읽어 주기나 긴급통화를 권합니다. 다른 폰에서는 도착을 알린 뒤 문은 사진 읽어 주기나 긴급통화로 찾아 주십시오. 사거리는 아직 서버 자료에 없어 따로 알리지 못합니다.",   // 2.87.0 / 2.57.0 (261016-I41 / 261016-A45, 이사장님 승인 2026-10-16) 문 찾기가 보이는 폰 바로잡음
            "안전 수칙 — 흰지팡이와 보폭" to "길눈은 혼자 걷기를 돕는 도구입니다. 단독보행을 하실 때는 반드시 흰지팡이를 짚어 주십시오. 길눈의 안내보다 흰지팡이와 주변 소리를 먼저 믿으십시오. 길눈을 처음 여시면 이 당부와 함께 보폭 재기 화면이 먼저 나옵니다. 걸음 수가 정확하려면 처음 한 번 보폭을 재셔야 하며, 급하실 때는 나중에 재기를 누르실 수 있습니다. 보폭을 재시기 전까지는 걸음 수 대신 미터로 안내합니다. 걷는 안내와 점지도 따라 걷기를 시작할 때마다 흰지팡이를 꼭 짚어 주십시오라고 한 번 말씀드립니다. 업데이트를 해도 재 두신 보폭은 그대로 남지만, 앱을 지웠다가 새로 까시면 다시 재셔야 합니다.",
            "탭 다섯" to "화면 아래에 길 찾기, 둘러보기, 음악·방송, 나눔, 설정 탭이 있고, 속 화면에서도 늘 보입니다. 속 화면이 열리면 커서가 그 화면 내용의 첫 줄로 갑니다. 속 화면에서는 맨 위의 뒤로 단추나 폰의 뒤로 동작으로 앞 화면에 갑니다. 뒤로 단추는 내용 첫 줄에서 9시 방향으로 쓸어 올라가면 제목 다음에 닿습니다. 첫 화면에서 뒤로 하시면 여기가 첫 화면이라고 알려 드리고 앱 밖으로 나가지 않습니다. 같은 탭을 한 번 더 누르시면 그 탭의 첫 화면으로 갑니다.",
            "지금 내 자리 듣기" to "길 찾기 탭에서 아무 여정이 없을 때 차 부르기 아래에 있고, 여정 중에는 여정 다른 할 일 펼치기 안에 있습니다. 지금 계신 곳의 도로명 주소와 가까운 지하철 출구나 건물을 거리와 시계 방향과 함께, 위성 오차, 국가지점번호, 날씨를 말씀드립니다. 출구는 호선 없이 「오금역 1번 출구」처럼 번호만 말씀드리고, 가까운 두 출구가 위성 오차 안에 있으면 「1번 출구와 2번 출구 사이, 1번 출구 쪽」처럼 둘 다 말씀드립니다. 150미터 안에 버스정류장이 있으면 정류장 이름과 정류장 번호(한 자씩), 시계 방향과 거리도 말씀드립니다. 콜을 부르실 때 기사님께 알려 드리기 좋습니다. 12초 안에 다 받지 못하면 받은 것만 말씀드립니다. 말로 하기에서 여기가 어디야, 지금 어디야, 내 자리라고 하셔도 됩니다. 점지도를 따라 걷는 중에 지금 어디야라고 하시면 점지도에서 남은 거리를 말씀드립니다. 무엇을 말할지는 설정 탭의 폰 펼치기 안, 현 위치정보 말할 내용에서 고르십니다.",
            "콜 부를 때 타는 곳 알려 드리기" to "차 부르기로 콜에 거실 때 상담원께 말씀하실 것에 타는 곳을 넣어 드립니다. 가까운 지하철 출구(두 출구 사이면 둘 다)와 버스정류장 이름, 정류장 번호입니다. 배차되었다고 답하시면 기사님이 전화하실 때 알려 드릴 타는 곳을 한 번 더 읽어 드립니다. 정류장 번호가 있으면 기사님이 지도에서 바로 찾으실 수 있습니다.",
            "화면이 꺼져도" to "길눈을 켜면 알림 칸에 길눈이 떠 있습니다. 화면이 꺼지거나 다른 앱을 쓰셔도 길눈이 위치를 이어 봅니다. 위성이 6초 넘게 끊기거나 흐리면 걸음 수와 보폭, 방향으로 자리를 이어 셉니다.",
            "바로가기 — 앱 아이콘을 길게 눌러 말로 하기" to "폰 첫 화면에서 길눈 아이콘을 길게 누르시면 말로 하기와 지금 내 자리 듣기 바로가기가 나옵니다. 말로 하기를 고르시면 길눈이 열리자마자 네 하고 명령을 듣고, 지금 내 자리 듣기를 고르시면 곧바로 자리를 말씀드립니다. 바로가기를 길게 눌러 첫 화면에 따로 놓아 두실 수도 있습니다.",
            "기기 설정 — 음향신호기, 이어폰 단추, 워치, 리모컨" to "설정 탭의 기기 설정에 기기에 관한 것을 한곳에 모았습니다. 음향신호기 자동 울리기, 안내 중 이어폰 단추 받기, 주변 신호기 살피기, 음향신호기 찾기, 리모컨 배우기가 있고, 맨 아래에서 갤럭시 워치 번호를 들으실 수 있습니다.",
            "말 자르고 새로 말하기" to "설정 탭의 말하기 설정에 있습니다. 처음에는 꺼져 있습니다. 켜시면 길눈이 말하는 중에 새 안내가 생겼을 때 하던 말을 끊고 새 안내부터 말씀드립니다. 끄시면 하던 말을 다 마친 뒤에 새 안내가 나옵니다. 경고는 끊지 않습니다.",
            "날씨 듣기" to "지금 내 자리 듣기를 누르시면 자리 말 끝에 날씨와 미세먼지를 한 마디 붙여 드립니다. 앱을 켜시면 자리가 잡힌 뒤 날씨를 한 번 알려 드립니다. 말로 하기에서 날씨 어때, 미세먼지 어때라고 하셔도 됩니다. 자료는 열쇠가 필요 없는 오픈메테오에서 받고, 10분 안에는 다시 묻지 않습니다.",
            "주소로 목적지 말하기" to "동호로 7길 14처럼 도로명 주소로 말씀하시면 그 주소로 안내하고, 목적지 이름도 주소로 불러 드립니다. 그 건물 안에 있는 가게 이름으로 바꿔 부르지 않습니다. 신당동 432처럼 지번으로 말씀하셔도 됩니다.",
            "말로 역 찾기 — 줄인 이름과 출구 번호" to "말로 하기에서 역으로 가실 때는 약수역 5번 출구로 가자처럼 말씀하시면 됩니다. 제기역처럼 줄여 부르시거나 제기 전철역, 제기 지하철역이라고 하셔도 지하철역 목록에서 바른 이름인 제기동역을 찾아 드립니다. 받아쓰기가 번호를 오 번, 이번처럼 글자로 적어도 5번, 2번으로 알아듣습니다. 말씀하신 번호의 출구가 없으면 그 역의 가까운 다른 출구를 몇 번 출구로 안내해 드릴까요 하고 여쭙니다. 안드로이드 13 이상 폰에서는 명령을 들을 때 지금 계신 곳 가까운 역 이름과 즐겨찾기 이름을 받아쓰기에 미리 알려 두어 잘못 적는 일을 줄입니다.",
            "화면 안 꺼지게는 왜 없습니까" to "웹 길눈에는 걷는 동안 화면 안 꺼지게가 있었습니다. 웹은 화면이 꺼지면 안내가 멈추었기 때문입니다. 앱 길눈은 화면이 꺼지고 잠겨도, 주머니에 넣어도 알림 칸의 길눈이 안내를 이어 가므로 이 단추가 필요 없습니다. 길 안내나 따라 걷기 중에는 화면이 꺼져도 폰이 잠들지 않게 붙듭니다. 화면을 켜 두면 배터리만 닳습니다.",
            "음악을 들으며 쓰기" to "다른 앱에서 음악이나 라디오를 틀어 두셔도 됩니다. 길눈이 말할 때만 그 소리가 잠시 작아졌다가, 말이 끝나면 되돌아옵니다. 길눈에서 튼 음악과 방송은 방송 들으며 길눈 쓰기 도움말을 보십시오.",
            "통신이 끊겼을 때" to "통신이 끊겨도 위치와 말소리는 그대로 돕니다. 협회 서버에 물을 때 끊기면 잠깐 쉬었다가 두 번 더 해 봅니다. 기록은 폰에 쌓아 두었다가 통신이 이어지면 보냅니다.",
            "저절로 저장" to "길눈은 여정이 바뀔 때마다 여정을 폰에 저장하고, 기록은 1분마다와 앱이 뒤로 갈 때 폰에 저장합니다. 갑자기 전원이 꺼져도 다시 켜면 이어집니다.",
            "말소리" to "설정 탭의 말하기 설정 맨 위에서 길눈 말소리를 켜고 끌 수 있습니다. 끄시면 길눈 말 대신 톡백으로 한 번만 알려 드리고, 경고는 그대로 말씀드립니다. 말 빠르기는 다섯 칸에서 고르십니다.",
            "내 보폭 재기" to "설정 탭에서 엽니다. 혼자 걸을 때와 동반자와 걸을 때를 따로 재 두실 수 있고, 재는 쪽 단추를 누를 때마다 바뀌며 그 보폭을 씁니다. 잴 거리를 미터로 적고 보폭 재기 시작을 누른 뒤 평소대로 걸으시고, 다 걸었습니다를 누르시면 보폭을 셈해 폰이 기억하고 협회 서버에도 남깁니다. 시작을 누르신 뒤 처음 15초 동안 폰이 그 전에 걸은 걸음을 한꺼번에 보내 걸음이 갑자기 크게 늘면 그만큼은 저절로 뺍니다. 점지도를 따라 걸을 때 걸음 수와 위성이 끊겼을 때의 이어 셈에 쓰입니다. 전에 재 두신 보폭은 혼자 걸을 때 보폭으로 옮겨 둡니다.",
            "새로고침" to "설정 탭에 하나 있습니다. 위치와 걸음을 다시 열고, 쌓인 기록을 나스로 보내고, 지금 판번호를 말씀드립니다.",
            "업데이트 — 새 판 받기" to "길눈은 켤 때와 하루 두 번쯤 협회 서버에 새 판이 나왔는지 스스로 물어봅니다. 새 판이 있으면 폰 알림으로 길눈 새 판이 나왔습니다, 두드리면 업데이트합니다라고 알려 드리고, 길 찾기 첫 화면 맨 위에도 같은 말과 단추를 한 줄로 띄웁니다. 알림이나 그 줄을 두드리시면 길눈이 새 판을 스스로 받아 설치 화면을 엽니다. 설치를 한 번 눌러 주시면 됩니다. 처음 한 번은 이 출처 허용을 켜는 화면이 열리니 켜신 뒤 폰의 뒤로 동작으로 돌아오시면 이어서 업데이트합니다. 한 번 스스로 업데이트한 뒤로는 폰에 따라 설치를 누르지 않아도 깔립니다. 새 판으로 바뀌면 길눈이 새 판으로 바뀌었다고 알림을 드립니다. 설정 탭의 새로고침 바로 아래 업데이트 단추로 언제든 새 판이 있는지 살피실 수 있습니다. 쓰시던 설정, 보폭, 즐겨찾기는 그대로 남습니다.",
            "기초 시험" to "설정 탭의 더 보기 안에 있습니다. 기초 시험 시작을 누르시면 30분 동안 1분마다 길눈이 위성 오차, 오늘 걸음, 통신 상태를 말씀드리고 나스에 남깁니다. 그동안 화면을 끄고 잠그고, 주머니에 넣고, 음악을 틀고, 비행기 모드로 통신을 끊고, 톡백을 켠 채로 해 보십시오. 1분마다 말하면 이어지고 있는 것입니다. 위치가 1분 넘게 들어오지 않으면 경고로 알려 드리고, 통신이 끊기거나 이어지면 알려 드립니다. 마치면 위치 받은 횟수, 길눈이 말한 횟수, 걸음, 통신 끊김 횟수를 말씀드립니다. 하나씩 듣기 펼치기에서는 허락 상태, 위성, 방향, 몸 센서, 걸음, 말소리를 하나씩 들어 보실 수 있고, 기록 보내기로 쌓인 기록을 바로 나스로 보내실 수 있습니다. 중간에 그만두시려면 기초 시험 중 — 그만하기를 누르십시오.",   // 2.87.0 / 2.57.0 (261016-I41 / 261016-A45, 이사장님 승인 2026-10-16) 기초 시험은 설정 탭 더 보기에만, 기록 보내기
            "판 기록" to "설정 탭의 더 보기 안에 있습니다. 판마다 무엇을 고쳤는지 적어 둡니다.",
            "말벗 견주기(관리자 시험)" to "관리자 시험입니다. 설정의 더 보기 안 관리자 시험 — 말벗 견주기에서 견주기 시작을 누르시면, 같은 질문 다섯 가지(현장영상해설이 뭐야, 길눈은 누가 만들었어, 비 오는 날 걸을 때 조심할 게 뭐야, 오늘 기분이 좀 우울해, 흰지팡이는 왜 짚어야 해)를 협회 서버 말벗에 차례로 묻고, 걸린 초와 대답을 적어 드립니다. 폰 안 인공지능 견주기는 아이폰 15 프로 이후 폰에서만 되어, 안드로이드에서는 서버 대답만 잽니다. 한 질문에 반 분쯤 걸릴 수 있습니다.",
            "말로 하기 — 이어서 묻고 답하기" to "말로 하기에서 길눈이 대답을 마치면, 단추를 다시 누르지 않으셔도 네 소리 뒤(말소리를 꺼 두셨으면 딩동 소리 뒤) 10초 동안 이어서 들습니다. 그동안 말씀하시면 계속 주고받고, 말씀이 없으면 조용히 닫힙니다. 다시 말씀하시려면 말로 하기 단추나 이어폰 단추를 길게 눌러 주십시오. 됐어, 고마워라고 하시면 그칩니다. 길 안내나 음악처럼 길눈이 아는 명령이 아니고 곳 이름도 아닌 질문(현장영상해설이 뭐야, 오늘 기분이 좀 그래 같은 말)은 협회 리눅스 서버의 인공지능이 지금 계신 곳과 가시는 곳, 앞에 주고받은 말을 헤아려 대답합니다. 날씨, 버스 도착, 영업 여부처럼 실시간으로 알 수 없는 것은 지어내지 않고 모른다고 말씀드린 뒤 다른 기능을 권하며, 길을 건너도 되는지 같은 안전 판단은 대신하지 않습니다. 같은 말씀을 세 번 잇달아 하시면 다른 말로 여쭤 보시거나 긴급통화를 권해 드립니다. 대답이 1초 넘게 걸리면 잠깐만 기다려 주세요라고 말씀드립니다.",
            "마지막 스무 걸음과 볼거리" to "목적지 스무 미터 앞부터는 거리 대신 걸음 수로 좁혀, 곧 도착합니다, 다섯 걸음, 1시 방향처럼 말씀드리고 폰과 워치가 그쪽으로 떨립니다. 걸음 수가 두 걸음 넘게 줄거나 방향이 바뀔 때만, 4초에 한 번 넘지 않게 말합니다. 목적지 가까이에 자원봉사자가 남긴 볼거리(팽나무, 동상, 안내판 같은 것) 기록이 있으면 그 정확한 자리로 이끌고, 닿으면 팽나무 앞입니다, 오른손을 뻗으시면 줄기가 닿습니다처럼 남겨 둔 말을 들려 드립니다. 볼거리 기록이 있을 때는 더 가까이 가서 도착을 알립니다.",
            "걸을 수 있는 길로 이끌기" to "점지도가 없는 곳을 위성으로 걸어가실 때, 길눈이 목적지까지 곧은 방향만 말하지 않고 협회 서버의 걷기 길찾기로 걸을 수 있는 길(인도, 공원 산책로, 계단, 건널목)을 받아 그 길로 이끕니다. 처음에 길 따라 얼마인지와 첫 꺾는 곳까지의 거리와 방향을 말씀드리고, 꺾는 곳 열 걸음쯤 앞에서 3시 방향으로 꺾습니다처럼 미리 알려 드리며, 닿으면 지금 꺾으십시오라고 하고 폰과 워치가 그쪽으로 떨립니다. 걷는 동안에는 목적지가 아니라 다음 꺾는 곳을 겨누어 방향을 걸음마다 다시 셈하고, 맞게 가시면 20초에 한 번 확신음, 틀어지면 길은 몇 시 방향이라고 알려 드립니다. 길에서 크게 벗어나시면 그 자리에서 다시 길을 찾습니다. 길찾기 서버가 쉬고 있으면 예전처럼 목적지 쪽 방향과 거리로 안내합니다.",
            "비슷한 곡 권하기" to "말로 하기에서 가수와 곡 이름으로 찾으셨는데 꼭 맞는 곡이 없으면, 나스 음악 가운데 이름이 비슷한 곡을 찾아 여쭙니다. 보기를 들면 이문세 첫사랑을 찾으시면, 이문세 노래 가운데 첫사랑은 없습니다, 비슷한 제목으로 옛사랑이 있습니다, 틀까요라고 여쭙고, 네라고 하시면 바로 틉니다. 아니라고 하시면 그만둡니다. 가수 이름을 조금 틀리게 말씀하셔도 비슷한 이름의 가수를 찾아 여쭙니다.",
            "주변 신호기 살피기" to "길 찾기 탭의 그 밖에 펼치기와 설정 탭의 기기 설정에 주변 신호기 살피기가 있습니다. 말로 하기에서 주변에 신호기 있어, 신호기 살펴 줘, 블루투스 신호기 있나라고 하셔도 됩니다. 8초 동안 둘레를 살펴, 블루투스로 울릴 수 있는 음향신호기가 몇 대 있는지, 가장 가까운 것이 바로 앞인지 가까이인지 조금 떨어진 곳인지 알려 드리고, 보행신호 음성안내 장치가 있으면 그것도 알려 드립니다. 블루투스 신호기가 잡히지 않으면, 그 근처 신호기가 리모컨 전용이거나 신호기가 없을 수 있다고 알려 드립니다. 리모컨 전용 신호기는 폰이 스스로 알아낼 수 없어, 지자체 설치 자료와 자원봉사자 표시를 붙여 차례로 알려 드릴 예정입니다. 블루투스가 꺼져 있으면 켜 달라고 알려 드립니다.",
            "점지도 따라 걷기" to "길 찾기 탭의 그 밖에 펼치기 안, 가까운 점지도에 있습니다. 목적지를 정하고 걸어가기를 누르셨을 때 맞는 점지도가 있으면 점지도로 걸을지 먼저 여쭙니다. 누르시면 가까운 점지도를 다섯씩 보여 드립니다. 줄마다 길 이름, 길이, 여기서 얼마나 떨어졌는지, 표시 수, 그린 분을 말씀드립니다. 다섯 줄 아래의 더 보기로 다음 다섯을, 이전 보기로 앞의 다섯을 봅니다. 길을 누르시면 이 길 따라 걷기와 거꾸로 걷기(되돌아가기) 두 단추가 나옵니다. 지금 자리에서 가까운 끝에서 떠나는 쪽이 위에 있고 커서도 그 단추에 섭니다. 고르시면 길 찾기 첫 화면으로 돌아가고, 걷는 동안의 단추는 그 맨 위에 나옵니다.",
            "거꾸로 걷기(되돌아가기)" to "같은 점지도를 끝에서 처음으로 걷습니다. 9시 방향 꺾임은 3시 방향 꺾임으로, 오름턱은 내림턱으로, 계단과 건널목은 시작과 끝을 바꾸어 알려 드립니다. 목적지에 닿은 뒤에도 길 찾기 탭 맨 위의 되돌아가기 단추로 온 길을 그대로 되돌아가실 수 있습니다.",
            "점지도 따라 걷기 — 단추와 말" to "따라 걷는 동안 길 찾기 탭 맨 위에 지금 어디입니까가 있습니다. 누르시면 남은 거리, 위치 오차, 다음에 있는 꺾이는 곳이나 표시를 말씀드립니다. 문까지 안내 중에 문이 여럿이면 바로 아래에 다른 문으로가 나옵니다. 이어서 다음에 무엇이 있습니까, 여기 문제 있어요, 도움 청하기가 있고, 따라 걷기 다른 할 일 펼치기에 여기 걸렸어요, 탈것에서 내렸습니다, 지금 내 자리 듣기, 방금 한 말 다시 듣기, 이 길목은 어떻게 생겼습니까, 가까운 버스 정류장, 함께 시험, 그만 걷기가 차례로 있습니다. 점지도를 불러오는 동안에는 그만두기 단추가 나옵니다. 뒤로 가셔도 안내는 이어집니다.",
            "점지도 따라 걷기 — 걸음 바탕, 보폭 맞추기, 돌아섬" to "점지도 거리는 그린 분의 걸음 수에 그린 분의 보폭을 곱해 셉니다. 걷기를 시작할 때 그린 분 보폭과 내 보폭을 센티로 알려 드리고, 남은 걸음과 표시까지의 걸음은 내 보폭으로 맞춰 셉니다. 걸음 감지와 방향이 되는 동안에는 한 걸음마다 내 보폭만큼 점지도 위를 나아가고, 좌우 한 걸음(약 1미터) 벗어남도 걸음과 방향으로 잽니다. 위성은 몇 미터씩 흔들리므로, 걸음 셈과 10미터 넘게 어긋나는 일이 네 번 거듭될 때만 위성 자리로 바로잡습니다. 지상 갈림길 알림은 그대로 드립니다. 뒤로 돌아서서 걸으시면 돌아서셨습니다, 출발 쪽으로 가고 계십니다라고 알려 드리고, 걸을수록 목적지까지 남은 걸음이 늘어납니다. 돌아서 걷는 동안에는 15초마다 한 번 더 알려 드립니다. 돌아서 걸으시는 동안에는 이미 지나온 표시를 다시 알려 드리지 않으니 지팡이로 살펴 주십시오. 다시 목적지 쪽으로 돌아서시면 알려 드리고, 앞으로 올 표시는 다시 알려 드립니다. 폰이 흔들려 한 걸음 안내를 할 수 없을 때는 그렇다고 알려 드리고, 위성으로 크게 벗어날 때만 15초마다 말씀드립니다. 폰을 가슴 앞에 세워 드시면 다시 한 걸음 안내로 돌아갔다고 알려 드립니다. 따라 걷는 동안 10초마다 자리와 들려 드린 말을 앱 기록에 남겨, 다음 시험 때 함께 살핍니다.",   // 2.35.0 (261010-A23, 이사장님 승인 2026-10-10)
            "점지도 따라 걷기 — 낙상 주의, 길 폭, 되돌아가는 곳" to "자원봉사자가 점지도에 낙상 주의 구간을 남긴 길에서는, 구간 열 걸음쯤 앞에서 몇 걸음 앞부터 몇 걸음 동안 몇 시 쪽에 낙상 위험이 있다고 미리 알려 드립니다. 구간에 들어서면 한 번 더 알리고, 구간 안에서는 위험한 쪽으로 반 걸음만 비켜나도 3초마다 경고음과 함께 몇 시 방향으로 반 걸음 옮기시라고 말씀드립니다. 이 경고는 끌 수 없습니다. 구간을 벗어나면 알려 드립니다. 한 걸음 안내가 되지 않는 때에는 정확히 살필 수 없다고 솔직히 말씀드리니 지팡이로 살피며 천천히 걸으십시오. 길 폭이 바뀌는 곳은 몇 걸음 앞에서 길 폭이 좁아집니다, 넓어집니다처럼 알려 드립니다. 길이 6시 방향으로 되돌아가는 곳은 미리 알리고, 그 자리에서 지금 6시 방향으로 돌아서십시오라고 말씀드립니다. 이 자리에서 돌아서시는 것은 길대로 가시는 것이라 돌아섰다는 알림은 하지 않습니다.",   // 2.35.0 (261010-A23, 이사장님 승인 2026-10-10)
            "확신음과 벗어남 경고" to "점지도 위를 제대로 디디시면 걸음마다 맑은 높은 소리가 납니다. 반 걸음 비켜나시면 가운데 소리와 함께 3시 방향이나 9시 방향으로 조금 비켜나신다고 한 번 알려 드립니다. 한 걸음 벗어나시면 곧바로 낮은 두 소리와 함께 몇 시 방향으로 몇 걸음 옮기실지 알려 드리고, 돌아오실 때까지 경고음은 2.5초, 말은 5초마다 되풀이합니다. 돌아오시면 오르는 두 소리와 점지도 위로 돌아오셨다고 알려 드립니다. 정한 거리마다 제대로 가고 있다고 말씀드리고(설정 탭 점지도와 걸음의 점지도 안내 설정 펼치기에서 5미터, 10미터, 20미터 가운데 고르십니다), 서 계시면 5초에 한 번 알려 드립니다. 폰은 가슴 주머니에 세워 넣거나 가슴 앞에 들어 주십시오.",
            "꺾이는 곳과 표시" to "꺾이는 곳은 서른 걸음 앞, 열한 걸음 앞, 그 자리에서 몇 시 방향으로 꺾는지 알려 드립니다. 계단, 건널목, 턱 같은 표시는 서른 걸음 앞, 일곱 미터 앞, 세 미터 앞에서 알려 드립니다. 건널목에서는 멈추시라고 경고하고, 다 건너시면 알려 드립니다. 길눈은 신호 색을 알지 못합니다. 걷기를 시작하면 첫 방향이 맞는지 확인해 드리고, 도셔야 할 쪽과 반대로 크게 도시면 곧바로 알려 드립니다.",
            "위성이 끊길 때 — 걸음으로 이어 셈" to "점지도의 바탕은 걸음입니다. 위성이 6초 넘게 끊기거나 흐리면 걸으신 걸음 수에 보폭을 곱해 점지도 위를 그만큼 나아가신 것으로 셉니다. 설정 탭에서 보폭을 재 두시면 걸음 수가 더 정확해집니다.",
            "끌 수 없는 안전 안내" to "따라 걷기를 시작할 때 길눈은 보조 안내이니 지팡이와 주변 소리를 먼저 확인하시라고 말씀드립니다. 아직 확인 중인 점지도면 조심해서 걸으시라고 덧붙입니다. 폰이 멈췄다 깨어나 안내가 10초 넘게 끊기면 안내가 끊겼다고 알려 드립니다. 이 안내들은 말소리를 꺼 두셔도 말씀드립니다.",
            "도착" to "목적지 30미터쯤과 20미터 안에서 남은 거리를 알려 드리고, 닿으면 도착 소리와 함께 알려 드립니다. 길 찾기 탭 맨 위 첫 줄이 목적지에 닿았습니다, 되돌아가기 단추로 바뀝니다.",
            "몸 센서 — 걸음과 방향을 더 정확하게" to "길눈을 켜 두시는 동안 폰의 가속도계와 자이로를 1초에 50번 읽어 걸음과 방향을 잽니다. 자봉 앱이 점지도를 그릴 때와 같은 센서, 같은 셈법이라 그린 분의 걸음과 걸으시는 분의 걸음이 같은 자로 맞습니다. 안드로이드 폰의 걸음 센서는 걸음을 몇 초씩 몰아서 알려 주는 일이 많은데, 몸 센서가 발이 땅에 닿을 때마다 곧바로 세어 그 늦음을 메웁니다. 방향은 몸이 몇 도 돌았는지 자이로로 재고 나침반 쪽으로 천천히 맞추므로 쇠붙이나 건물 옆에서도 틀어지지 않습니다. 위성이 끊겨 걸음으로 자리를 이어 셀 때도 이 방향을 씁니다. 설정 탭 더 보기 안의 기초 시험에서 몸 센서 듣기로 몸 센서가 센 걸음과 방향을 들어 보실 수 있습니다. 따로 켜실 것은 없습니다.",
            "음향신호기 — 자동으로 잡기" to "건널목 앞에서 폰을 꺼내실 필요가 없습니다. 길눈이 켜져 있으면 화면이 꺼져 있어도 둘레의 블루투스 음향신호기를 늘 살핍니다. 신호기가 가까이 잡히면 위치 안내를 스스로 한 번 울리고, 그 앞에 4초 넘게 머무르시면 신호 안내를 한 번 울립니다. 같은 신호기에는 3분에 한 번만 보내며, 이때 길눈은 말하지 않고 짧게 진동만 합니다. 신호기가 소리를 냅니다. 보행신호 음성안내 장치가 있는 횡단보도 앞이면 5분에 한 번 알려 드립니다. 처음부터 켜져 있고, 설정 탭의 기기 설정에 있는 음향신호기 자동 울리기에서 끄실 수 있습니다. 폰의 블루투스와 근처 기기 허락이 있어야 합니다. 화면이 꺼져 있을 때는 공용 번호나 정해진 이름을 내보내는 신호기만 잡힙니다.",
            "음향신호기 — 손으로 울리기" to "길 찾기 탭의 그 밖에 펼치기 안에 음향신호기 울리기 — 신호 안내와 음향신호기 울리기 — 위치 안내가 있습니다. 누르시면 둘레를 2.5초 살펴 가장 가까운 신호기에 요청을 보내고, 신호기가 받았는지 말씀드립니다. 받으면 세 번, 안 되면 길게 진동합니다. 가까이에 블루투스 음향신호기가 없으면 그렇게 알려 드립니다. 리모컨으로만 울리는 신호기도 있습니다. 블루투스가 꺼져 있으면 블루투스 켜기 창을 열어 드리고, 허용을 누르시면 하시던 요청을 이어서 보냅니다. 근처 기기 허락이 없으면 그 자리에서 여쭙니다.",
            "말로 하기" to "길 찾기 탭 첫 화면의 지금 내 자리 듣기 바로 아래가 말로 하기 단추이고, 그 아래가 긴급통화서비스입니다(아이폰과 같은 차례). 누르시면 길눈이 네 하고 말씀을 기다립니다. 말소리를 꺼 두셨으면 딩동 소리 뒤에 말씀하십시오. 듣는 중에 다시 누르시면 그만둡니다. 듣는 동안에는 길눈이 하던 말을 멈추고 안내를 잠시 맡아 두었다가 말씀이 끝나면 이어서 드립니다. 위험 경고는 기다리지 않고 곧바로 말씀드립니다. 대답을 찾는 데 1초 넘게 걸리면 잠깐만 기다려 주세요라고 알려 드리고, 못 알아들으면 다시 한번 말씀해 주세요라고 한 번 더 여쭙니다. 묻는 말에는 마이크를 한 번 저절로 엽니다. 처음 쓰실 때 마이크 허락을 여쭙니다. 받아쓰기는 폰의 구글 음성 인식을 씁니다.",
            // 2.85.0 / 2.55.0 (261016-I39 / 261016-A43, 이사장님 승인 2026-10-16) 해설사 불러 줘 → 해설사 요청해 줘
            "말로 하기 — 할 수 있는 말" to "지금 어디야. 약수역 가자처럼 가실 곳. 가까운 점지도 찾아 줘. 점지도를 따라 걸을 때는 다음에 무엇, 다음 갈림길, 어디쯤이야, 그만 걷기, 도착하면 되돌아가자. 신호기 울려 줘, 신호 알려 줘, 신호기 찾아 줘. 도와줘, 긴급통화, 해설사 요청해 줘, 또는 가족 이름과 화상통화. 날씨 어때. 몇 시야. 말 빠르게, 말 느리게. 말소리 꺼, 말소리 켜. 다시 말해. 그만. 하던 일 멈춰. 여정 끝. 새로고침. 걸어서 가자, 차로 가자, 지하철로 가자, 버스로 가자. 차에 탔어, 내렸어. 자세히 안내해, 간단히 안내해, 보통으로 안내해. 얼마나 걸려, 지금 가는 길. 도착했어. 즐겨찾기 목록, 즐겨찾기에 담아 줘. 점지도로, 위성으로. 점지도를 따라 걸을 때는 여기 문제 있어, 여기 걸렸어, 길목, 정류장, 다른 문. 길 기억해 줘. 되짚어 나가자. 말로 그린 길. 음성유도기 어디 있어. QR 찾아 줘. 글자 읽어 줘. 사람 있어. 빛 알려 줘. 바코드 읽어 줘. 무슨 색이야. 얼마짜리야. 이게 뭐야. 가리키는 거 읽어 줘. 근처 약국. 축제 알려 줘. 고장 이야기. 마실 가자. 사진 읽어 줘. 안면인식. 음악 틀어 줘, 트롯 틀어 줘, 또는 가수나 곡 이름. 다음 곡, 이전 곡. 무슨 곡이야. 이어서 틀어. 고장 노래 틀어 줘. 라디오 틀어 줘. KBS 1라디오 틀어 줘. TV 틀어 줘. 뉴스 들려줘. 장애 소식, 속보, 경제 뉴스. 기분이 꿀꿀해. 음악 꺼, 라디오 꺼. 목소리 바꿔. 호칭 바꿔. 현장영상해설 받고 싶어. 도움말이라고 하시면 이 말들을 읽어 드립니다. 모르는 말씀은 그렇다고 알려 드리고 기록해 두었다가 알아듣는 말을 키웁니다.",
            "호칭 — 길눈이 부르는 내 이름" to "길눈은 처음에 길손님이라고 부릅니다. 말로 하기에서 호칭 바꿔라고 하시고 불리고 싶은 이름을 말씀하시면 바뀝니다. 설정 탭의 말로 하기 설정에 있는 길눈이 부르는 내 호칭 칸에 적고 엔터나 호칭 저장을 누르셔도 됩니다. 칸을 비우고 저장하시면 다시 길손님으로 부릅니다.",
            "말로 하기 — 가실 곳 말하기" to "약수역 가자처럼 가실 곳을 말씀하시면 가까운 점지도 가운데 그곳으로 가는 길을 찾습니다. 점지도의 도착지나 이름이 맞으면 약수역까지 점지도를 따라 걸을까요라고 여쭙고, 출발지가 맞으면 거꾸로 걷는 되돌아가는 점지도로 여쭙니다. 시작점이 떨어져 있으면 몇 시 방향, 얼마나 떨어졌는지 함께 알려 드립니다. 네라고 하시면 곧바로 따라 걷기를 시작하고 길 찾기 첫 화면으로 돌아갑니다. 아니오라고 하시면 다음 길을 말씀드립니다. 이름으로 맞는 점지도가 없으면 나스에서 그곳을 찾아 몇 시 방향, 얼마나 떨어졌는지 알려 드리고, 끝이 그곳 가까이 닿는 점지도가 있으면 여쭙니다.",
            "말로 하기 — 이어폰 단추" to "걸으실 때는 한 손에 지팡이를 드시므로, 길눈 화면이 켜져 있으면 이어폰의 재생 단추를 길게, 0.6초 넘게 누르셔도 말로 하기가 열립니다. 짧게 누르시면 평소처럼 음악이 멈추고 다시 나옵니다. 점지도를 따라 걷거나 되짚어 나가거나 말로 그린 길을 걷는 동안에는 화면이 꺼져 있어도 재생 단추를 길게 누르시면 말로 하기가 열립니다.",
            "긴급통화서비스" to "길 찾기 탭 첫 화면의 말로 하기 바로 아래에 긴급통화서비스 단추가 있습니다. 세 갈래로 화상통화를 청하실 수 있습니다. 가족·지인은 고르신 한 분께만, 자원봉사자와 현장영상해설사는 지금 받으실 수 있는 모든 분께 신호가 갑니다. 먼저 한마디를 적어 두시면 받는 분 화면에 뜹니다. 가족·지인 명단과 내 이름은 설정 탭에서 다룹니다. 지금 계신 곳과, 점지도를 따라 걷는 중이면 그 길의 도착지도 함께 갑니다. 받으시면 누르지 않으셔도 뒤 카메라와 마이크가 켜지고 통화가 이어지며, 이어폰이 없으면 스피커로 들립니다. 가족·지인은 40초, 자원봉사자와 현장영상해설사는 1분 30초 동안 기다리며, 자원봉사자와 현장영상해설사는 30초쯤에 몇 분께 계속 요청 중인지 한 번 알려 드리고, 받을 분이 없거나 받지 않으시면 알려 드립니다. 밤 열 시부터 아침 일곱 시까지는 자봉 앱에서 밤에도 받기를 켜 두신 자원봉사자께만 울리므로, 그 시간에 받을 분이 없으면 그렇다고 함께 알려 드립니다. 가족·지인이 받지 않으시면 자원봉사자와 현장영상해설사에게 요청할까요 하고 여쭙고, 바로 그 자리의 예, 요청합니다 단추 한 번이면 두 갈래에 함께 호출합니다. 이 물음은 2분이 지나면 거둡니다. 받는 분은 모두 자봉 앱으로 받으시며, 자원봉사자와 현장영상해설사는 기회가 고르게 가도록 처음 15초는 최근에 덜 받으신 다섯 분께 먼저, 그다음 모든 분께 울립니다. 통화료는 들지 않고 데이터만 씁니다. 곧바로 잇지 못할 때 거치는 영상 다리 주소는 나스에서 받아 쓰므로, 다리를 옮겨도 앱을 새로 받으실 필요가 없습니다. 통화 중에는 길눈이 말하지 않고 화면이 꺼지지 않습니다. 끊으실 때는 맨 위의 통화 끊기 단추를 누르시거나 폰의 뒤로 동작을 하십시오. 처음 쓰실 때 카메라와 마이크 허락을 여쭙니다. 요청을 시작하거나 통화가 열리면 하던 길눈 말과 방송을 멈춥니다. 말로 하기에서 도와줘, 해설사 요청해 줘, 또는 가족 이름이나 별명과 화상통화라고 하시면 곧바로 요청하고 이 화면을 엽니다. 이름이 조금 다르게 들려도 비슷하면 알아듣고, 같은 이름이나 별명이 두 분 넘게 맞으면 누구에게 요청할지 한 번 여쭙니다. 별명으로 부르시면 큰딸, 홍길동 님께 화상통화를 요청합니다처럼 별명을 먼저 말씀드립니다. 가족 가운데 누구냐고 여쭐 때는 열 분까지 이름과 첫 별명을 읽어 드립니다.",   // 2.86.0 / 2.56.0 (261016-I40 / 261016-A44, 이사장님 승인 2026-10-16)
            "가족·지인 명단 — 이음 번호로 등록" to "설정 탭의 가족·지인 명단을 여시고 이음 번호 받기를 누르십시오. 세 자리 숫자를 읽어 드립니다. 이 번호를 전화로 가족이나 지인에게 불러 주십시오. 번호는 30분 동안 한 번만 쓰입니다. 그분이 자봉 앱을 깔고 그리기 탭, 긴급통화 받기, 가족·지인으로 받기에서 이음 번호와 부르실 이름을 넣으시면 곧바로 명단에 들어오고, 길눈이 그분이 등록하셨다고 알려 드립니다. 번호를 다시 들으시려면 같은 자리의 다시 듣기를 누르십시오. 주소나 문자를 보내실 일은 없습니다. 등록된 분 몇 분, 열 분까지라고 명단 위에 나옵니다. 등록된 분의 이름을 누르시면 부르는 이름, 곧 별명을 적는 칸과 명단에서 빼기가 있습니다. 별명은 큰딸, 우리 딸처럼 쉼표로 나누어 여럿 적으실 수 있고, 별명 저장을 누르시면 말로 하기에서 그 별명으로 부르셔도 그분께 요청합니다. 별명은 이 폰에만 담기고 나스에는 보내지 않습니다. 받는 분 화면에 뜰 내 이름은 설정 탭의 가족·지인 명단 바로 아래 칸에 적으십시오. 웹 길눈, 아이폰 길눈과 같은 명부를 씁니다. 명단과 명단 열쇠는 이 폰에 담깁니다. 길눈을 지웠다가 다시 까셔도, 폰 설정의 구글 백업이 켜져 있으면 같은 구글 계정에서 되살아날 수 있습니다. 되살아나지 않으면 다시 이음 번호로 등록해 주셔야 합니다. 가족·지인은 열 분까지 등록하실 수 있습니다. 열 분이 차면 이음 번호 받기를 누르셔도 번호를 받지 않고, 한 분을 빼셔야 새로 등록된다고 알려 드립니다. 여러 분을 등록하실 때는 한 분마다 새 이음 번호를 받아 주십시오. 번호를 받은 뒤 명단 화면을 떠나셔도 30분 동안은 등록하시면 알려 드립니다.",   // 2.86.0 / 2.56.0 (261016-I40 / 261016-A44, 이사장님 승인 2026-10-16)
            "긴급통화가 이어지지 않을 때" to "도움 요청이 통신이 약해 나스의 답을 받지 못해도 가족·지인은 50초, 자원봉사자와 현장영상해설사는 100초가 지나면 요청을 마치고 알려 드립니다. 상대가 받으신 뒤 40초 안에 통화가 이어지지 않아도 마치고 다시 요청하시라고 알려 드립니다. 연결이 막히면 한 번 더 이어 보고, 그래도 안 되면 알려 드립니다. 통화 중에는 길눈 말소리를 내지 않는 대신 경고가 생기면 길게 진동합니다. 길눈 화면을 닫으시면 통화도 끊깁니다.",   // 2.86.0 / 2.56.0 (261016-I40 / 261016-A44, 이사장님 승인 2026-10-16)
            "갤럭시 워치 길눈 — 긴급통화" to "워치 첫 화면의 긴급통화를 두드리시면 한 번 더 누르시면 긴급통화를 요청합니다라고 알려 드립니다. 5초 안에 한 번 더 두드리셔야 폰 길눈이 요청합니다. 걷다가 잘못 눌리지 않게 하려는 것이며, 워치 단추 횟수에는 넣지 않았습니다. 마지막으로 요청하신 가족·지인 한 분께 갑니다. 그런 분이 없을 때 받겠다고 하신 분이 한 분뿐이면 그분께, 아니면 자원봉사자에게 갑니다. 폰 길눈의 긴급통화서비스 화면이 함께 열리며, 끊으실 때는 폰에서 끊으십시오.",
            "음향신호기 찾기" to "길 찾기 탭의 그 밖에 펼치기 안에 있습니다. 켜시면 신호기에 가까워질수록 확신음이 빨라지고, 바로 앞이면 음향신호기 바로 앞입니다라고 알려 드린 뒤 위치 안내를 울립니다. 6초 넘게 잡히지 않으면 천천히 둘러보시라고 한 번 알려 드리고, 1분이 지나면 저절로 마칩니다. 다시 누르시면 멈춥니다.",
            "갤럭시 워치 길눈 — 잇기와 첫 화면" to "갤럭시 워치에 워치 길눈을 깔면 폰 길눈과 저절로 이어집니다. 폰 길눈이 켜져 있어야 합니다. 처음 여실 때 한 번만 지팡이를 어느 손으로 쥐시는지, 워치를 어느 손목에 차셨는지 여쭙니다. 워치 화면에는 다음 갈림길, 내 자리, 마지막 안내, 말로 하기, 긴급통화, 음향신호기 위치, 음향신호기 신호, 걷는 동안 깨어 있기가 한 줄에 하나씩 있고, 맨 아래에 도움말과 손 바꾸기가 있습니다.",
            "갤럭시 워치 길눈 — 안내와 진동" to "폰 길눈이 한 말은 워치의 마지막 안내로 넘어갑니다. 다음 갈림길은 점지도를 따라 걷는 중 다음에 무엇이 있는지 읽어 드립니다. 꺾어야 할 때 워치가 2시에서 5시 방향은 길게 한 번, 7시에서 10시 방향은 짧게 두 번 떨고, 도착하면 세 번 떱니다. 워치에서 톡백을 쓰시면 톡백이 읽고, 아니면 워치 목소리로 읽습니다. 폰이 곁에 없으면 나스에 남은 마지막 안내를 받아 읽고, 내 자리는 워치의 위성으로 찾습니다.",
            "갤럭시 워치 길눈 — 걷는 동안 깨어 있기" to "폰 길눈으로 점지도 따라 걷기를 시작하면 워치도 저절로 깨어 있기를 켭니다. 워치 화면 위에 길눈이 떠 있고, 손목을 내려도 꺼지지 않으며, 팔 흔들림으로 걸음을 세어 폰에 보냅니다. 폰이 가방 속에 있어 4초 넘게 걸음을 못 세면 폰 길눈이 워치 걸음으로 이어 셉니다. 워치 화면의 걷는 동안 깨어 있기 단추로 손수 켜고 끌 수도 있습니다.",
            "갤럭시 워치 길눈 — 손목 가리키기" to "지팡이를 쥐지 않은 손에 워치를 차셨을 때 씁니다. 점지도를 따라 걷는 중 워치 찬 팔을 손등이 위로 오게 앞으로 뻗으시면, 가야 할 쪽을 가리킬 때 1초마다 굵게 떨고, 어긋나면 팔을 옮길 쪽을 3시 쪽은 길게 한 번, 9시 쪽은 짧게 두 번으로 알려 드립니다. 팔을 뻗는 순간 한 번 맞습니다, 3시 쪽으로, 9시 쪽으로라고 말씀드립니다. 어긋나게 느껴지시면 따라 걷는 중에 서서 가리키기 방향 맞추기를 누르고 팔을 몸 정면으로 곧게 뻗어 기다리십시오.",
            "갤럭시 워치 길눈 — 지팡이 떨림 기록" to "지팡이를 쥔 손에 워치를 차셨을 때 씁니다. 연구 1단계로, 바닥 종류를 고르면 손목 떨림을 1초에 100번 담습니다. 평소처럼 지팡이를 쓰며 걸으시고 그만을 누르시면 폰 길눈으로 보내고, 폰 길눈이 협회 나스로 올립니다. 5분이 되면 저절로 멈추고, 5초보다 짧으면 버립니다. 나중에 점자블록을 알려 드리는 데 씁니다.",
            "갤럭시 워치 길눈 — 워치 단추" to "워치 옆에 앱이 쓸 수 있는 단추가 있는 워치는 그 단추를 1.5초 안에 한 번 누르시면 다음 갈림길, 두 번이면 내 자리, 세 번이면 말로 하기입니다. 누를 때마다 한 번씩 짧게 떱니다. 갤럭시 워치의 홈 단추와 뒤로 단추는 워치가 쓰므로 길눈이 쓰지 못합니다. 그런 워치에서는 화면의 단추를 쓰십시오. 애플워치의 두 번 집기 같은 손가락 동작은 웨어 OS가 앱에 내어 주지 않아 아직 없습니다.",
            // 2.7.0 통합(묶음 b1~b6) — 아이폰 길눈 도움말과 같은 말(안드로이드에 맞게 손본 곳만 다름)
            "목적지 찾기" to "길 찾기 탭의 어디로 가실까요 칸(말로 하기와 긴급통화서비스 단추 아래)에 가실 곳의 이름이나 주소를 적고 엔터(키보드의 검색)를 누르십시오. 가까운 곳부터 다섯 곳씩 나오고 커서가 첫 줄로 갑니다. 더 보시려면 더 보기를 누르십시오. 고르시면 걸어가기, 차로 가기, 기차로 가기, 배로 가기, 지하철로 가기, 버스로 가기, 즐겨찾기에 담기 단추가 나옵니다. 글자를 치는 동안 요즘 찾으신 곳과 즐겨찾기에서 맞는 곳이 칸 아래에 세 줄까지 나옵니다. 목적지가 먼 기차역이면 기차로 가기가, 항구나 여객터미널이면 배로 가기가 맨 위에 옵니다. 받아쓰기(키보드의 마이크)로 말씀하셔도 됩니다.",
            "하던 일 멈추기" to "길 찾기 탭 첫 화면 맨 위에는 하던 일 멈추기 단추가 늘 있습니다. 누르시면 걷는 안내와 차 안 안내, 점지도 따라 걷기, 지하철 안내, 되짚어 나가기의 나가는 길 안내, 말로 그린 길 따라 걷기, 묻던 말, 음향신호기 찾기, 차 안 간판 알림, 문 찾기와 문까지 이끌기, 카메라로 하는 기능, 길눈의 말을 모두 멈추고, 콜 차를 탄 것으로 정해 둔 것과 지금 탄 것에 대한 판단도 처음부터 다시 살핍니다. 되짚어 나가기에 기억해 둔 길은 지우지 않습니다. 그리고 하던 일을 멈췄습니다, 어디로 가실까요 하고 한 번 말씀드린 뒤 커서를 어디로 가실까요 칸으로 옮깁니다. 음악과 방송은 멈추지 않습니다. 말로 하기에서는 하던 일 멈춰, 다 멈춰, 모두 멈춰라고 말씀하십시오. 새 목적지를 고르시거나 말씀하시면 하던 안내는 저절로 멈추고 새 목적지로 바뀝니다.",
            "즐겨찾기 — 담기와 지우기" to "목적지를 고른 화면에서 즐겨찾기에 담기를 누르시면 담깁니다. 지금 계신 자리를 담으시려면 즐겨찾기 화면의 펼치기에서 이름을 적고 담기를 누르십시오. 주소와 함께 담기고, 폰 안에만 있습니다. 지우실 때는 그 줄에서 톡백 동작 메뉴(위로 쓸었다 오른쪽, 또는 세 손가락 두드리기)를 열어 즐겨찾기에서 지우기를 고르시거나, 그 줄을 길게 누르십시오. 요즘 가신 곳이 맨 위로 옵니다.",
            "걷는 안내" to "걸어가기를 누르시면 목적지까지 남은 거리와 시계 방향을 말씀드립니다. 가까워질수록 더 자주 말하고, 방향이 틀어지면 바로 알려 드리며 폰과 워치가 돌 쪽으로 떨립니다. 제대로 가고 계시면 25초마다 짧은 확신음이 납니다. 사거리와 갈림길에 닿으면 어느 길과 어느 길이 만나는 곳인지 알려 드립니다. 40미터 안에 들면 곧 도착을, 닿으면 도착 소리와 함께 도착했습니다를 말씀드립니다. 맞는 점지도가 있으면 점지도로 걸을지 먼저 여쭙고, 20초 안에 고르지 않으시면 점지도로 걷습니다.",
            "차 안 안내 — 바른 길로 가는지, 내리는 곳" to "택시나 남의 차로 가실 때 차가 제대로 가는지 알려 드립니다. 협회 서버가 목적지까지 차 길을 받아, 차가 그 길에서 100미터 넘게 15초 벗어나면 길에서 벗어났습니다, 목적지는 지금 몇 시 방향 몇 미터입니다라고 알려 드리고 길을 새로 받습니다. 길을 따라 600미터쯤 남으면 지금 달리는 길, 목적지가 어느 길로 들어가 몇 미터 가면 있는지, 몇 시 방향인지, 마지막으로 꺾는 곳, 건물 이름과 문이 있는 길을 한 번에 말씀드려 기사님께 그대로 전하실 수 있게 합니다. 100미터 안에 들면 곧 몇 시 방향에 목적지입니다, 내리실 준비를 하십시오라고, 30미터 안에 들면 목적지 앞입니다, 내리셔도 좋습니다, 내리시면 점지도가 있는지 확인해 드리겠습니다라고 알려 드립니다. 방향은 늘 차가 가는 쪽을 12시로 삼습니다. 버스, 기차, 고속버스에서는 쓰지 않습니다.",
            "차 안 안내 정도 — 간단, 보통, 자세히" to "설정 탭의 말하기 설정에 있는 차 안 안내 정도를 누를 때마다 간단, 보통, 자세히로 바뀝니다. 처음은 보통입니다. 간단은 남은 거리, 길에서 벗어났을 때, 내리는 곳만 말씀드립니다. 보통은 여기에 마지막 꺾는 곳 미리 알림과, 30초 넘게 목적지에서 200미터 넘게 멀어질 때를 더합니다. 자세히는 꺾는 곳마다 미리 알리고, 꺾은 뒤 길대로 가고 있다고 말씀드리며, 신호 대기가 아닌데 1분 넘게 서 있으면 알려 드립니다. 길에서 벗어났을 때와 내리는 곳 안내는 어느 정도에서나 나옵니다. 말로 하기에서 자세히 안내해, 간단히 안내해, 보통으로 안내해라고 하셔도 바뀝니다.",
            "차 안 안내" to "차로 가기나 차에 탔습니다를 누르시면 차 안 안내가 시작됩니다. 목적지까지 5킬로미터, 3킬로미터, 2킬로미터, 1킬로미터, 500미터에서 남은 거리를 말씀드리고, 승용차·택시·콜은 100미터에서 내리실 준비를, 30미터에서 내리셔도 좋다고 알려 드립니다. 버스·기차·고속버스는 아무 데서나 세울 수 없어 300미터에서 내리실 준비를, 150미터에서 목적지 부근임을 알려 드립니다. 차 부르기로 부른 콜에 타셨으면 차 대신 복지콜, 나비콜처럼 그 콜 이름으로 말씀드립니다. 지나는 길 이름과 동네가 바뀌면 알려 드리고, 3분 넘게 조용하면 남은 거리를 한 번 말씀드립니다.",
            "저절로 바뀌는 안내 — 차에 타고 내릴 때" to "걸어가는 중에 빠르게 움직이기 시작하면 묻지 않고 곧장 차 안 안내로 바꿉니다. 목적지 800미터 안에서 차가 멈추고 걸으시기 시작하면 저절로 걷는 안내로 이어 드립니다. 내리시면 먼저 목적지까지 가는 점지도가 있는지 살펴, 있으면 점지도가 있습니다, 바로 문까지 안내하겠습니다라고 하고 곧장 점지도로 걷고, 없으면 점지도가 아직 없어 방향으로 안내해 드리겠습니다라고 하고 방향으로 안내합니다. 단추를 누르지 않으셔도 됩니다. 직접 바꾸시려면, 걷는 중에는 첫 화면 맨 위 단추 바로 아래 탈것 바꾸기를 누르신 뒤 차를 타고 있습니다를, 차 안에서는 맨 위 단추 내렸습니다를 누르십시오.",
            "지하철로 가기" to "목적지를 고른 화면에서 지하철로 가기를 누르시면, 지금 자리에서 가까운 역과 목적지에서 가까운 역, 갈아타는 곳, 걸리는 시간, 들어갈 출구를 한 번에 말씀드립니다. 이 길로 가기를 누르시면 타는 역 출구까지 걷는 안내가 시작됩니다. 출구에 닿으면 어느 호선 어느 방면을 타실지 알려 드립니다. 이미 열차 안이시면 이미 열차에 탔습니다를 누르십시오.",
            "지하철에서 지나는 역 듣기와 갈아타기" to "출구에 닿은 뒤 열차가 움직이는데 걸음이 없으면 열차에 타신 것으로 알고 저절로 역 알림을 시작합니다. 누르지 않으셔도 됩니다. 지나는 역마다 역 이름을 말씀드리고, 갈아탈 역은 한 역 전에 미리, 그 역에서는 어느 호선 어느 방면으로 갈아타실지 알려 드립니다. 내리실 역은 두 역 전과 한 역 전에 알려 드리고, 닿으면 목적지에 가장 가까운 출구를 말씀드립니다. 밖으로 나오셔서 걸으시면 저절로 남은 길 걷는 안내로 이어집니다. 지하에서는 위성이 없으므로 서울 실시간 열차 위치와 폰 흔들림, 역 사이 시간으로 헤아립니다.",
            "버스로 가기" to "목적지를 고른 화면에서 버스로 가기를 누르시면 가까운 정류장이 시계 방향과 거리, 정류장 번호와 함께 다섯씩 나옵니다. 정류장을 고르시면 그 정류장에 오는 버스가 몇 분 뒤에 오는지, 저상버스인지 말씀드립니다. 정류장까지 걸어가기를 누르시면 정류장까지 걷는 안내가 시작되고, 닿으면 오는 버스를 다시 알려 드립니다. 버스에 타셔서 버스가 움직이면 저절로 버스 안 안내로 바뀝니다. 버스 안에서는 목적지 500미터 앞에서 내리실 준비를, 300미터 앞에서 다음 정류장에서 내리시라고 알려 드립니다. 지금은 서울 정류장의 도착 정보가 가장 잘 나옵니다.",
            // 2.65.0 (261017-A53, 이사장님 승인 2026-10-17) 웹 길눈에서 옮긴 것 4단계
            "미리 걸어 보기" to "점지도를 나서기 전에 한 마디씩 들어 보는 가상체험입니다. 가까운 점지도에서 길을 고르시면 이 길 따라 걷기와 거꾸로 걷기 아래에 미리 걸어 보기가 있습니다. 누르시면 모두 몇 걸음인지, 길에서 만나실 것이 몇 가지인지 먼저 말씀드립니다. 미리 걸어 보기 시작을 누르시면 곧게 몇 걸음 갑니다, 3시 방향으로 꺾습니다, 건널목을 건넙니다처럼 한 마디씩 들려 드리고, 다음 단추로 넘기십니다. 한 마디 뒤로, 방금 마디 다시 듣기가 있고, 그 밖에 펼치기에서 5초마다 저절로 넘기기와 처음부터 다시를 고르실 수 있습니다. 요약만 듣기는 꺾임, 계단, 건널목, 턱, 승강기, 문만 몇 걸음 뒤에 무엇이 나오는지 한 번에 들려 드립니다. 돌아오는 길로 미리 걸어 보기를 누르시면 꺾는 방향과 계단 오르내림까지 거꾸로 바꾸어 들려 드립니다. 걸음 수는 재 두신 보폭으로 셈하고, 보폭을 재지 않으셨으면 미터로 말씀드립니다. 실제로 나서실 때는 공사나 세워 둔 물건으로 달라진 곳이 있을 수 있습니다.",
            // 2.73.0 (261024-A61, 이사장님 지시 2026-10-24, 아이폰 2.105.0과 같음) 틀렸어·타신 역·여기는 ○○역
            "틀렸어 — 앞 단계로 되돌리기" to "길눈이 잘못 알아들었거나 잘못 바꾸었을 때 말로 하기에서 틀렸어라고 말씀하시면, 무엇을 하고 있든 알겠습니다라고 대답하고 바로 앞 단계로 되돌립니다. 목적지를 잘못 정했으면 앞의 목적지로, 탈것을 잘못 바꾸었으면 앞의 탈것으로, 여정을 잘못 끝냈으면 그 여정으로 돌아갑니다. 길눈이 저절로 바꾼 것도 되돌립니다. 한 번 더 틀렸어라고 하시면 그 앞 단계로 갑니다. 되돌린 탈것은 길눈이 다시 저절로 바꾸지 않습니다.",
            "지하철 — 타신 역 알려 주기" to "지하철 역 안은 위성이 닿지 않아, 폰이 짐작한 자리가 크게 틀릴 수 있습니다. 그래서 길눈은 땅속에서 짐작한 자리로는 타신 역을 정하지 않고 어느 역에서 타셨는지 여쭙니다. 약수역처럼 대답하시거나, 언제든 약수역에서 탔어라고 말씀하시면 그 역에서 목적지까지 지나는 역을 하나하나 알려 드립니다. 6호선 탔어처럼 호선을 말씀하시면 그 호선 역으로 찾습니다. 역 안내를 시작하지 못하는 동안에는 차 안 안내를 하지 않고, 3분마다 타신 역을 여쭙니다.",
            "여기는 ○○역이야 — 지금 자리 바로잡기" to "길눈이 말하는 자리가 틀렸으면 여기는 삼각지역이야처럼 말씀해 주십시오. 지하철을 타고 가시는 중이면 그 역으로 맞추어 남은 정거장을 다시 셉니다. 가시는 길에 없는 역이면 그 역에서 다시 길을 찾습니다. 걷는 중이면 그 역의 자리를 지금 자리로 삼고, 위성이 다시 확실히 잡히면 위성 자리로 이어 갑니다. 지하철 안에서 여기가 어디야라고 물으시면 위성 대신 지나온 역으로 대답합니다. 위성이 닿지 않아 짐작한 자리일 때는 그렇다고 먼저 말씀드립니다.",
            // 2.64.0 (261017-A52, 이사장님 승인 2026-10-17) 웹 길눈에서 옮긴 것 3단계
            "긴급통화 — 카메라 영상" to "긴급통화가 이어지면 길눈이 뒤쪽 카메라를 켜서 받는 분께 지금 앞에 보이는 모습을 보내 드립니다. 카메라가 실제로 켜져 영상이 나가기 시작하면 연결 알림과 함께 「카메라가 켜졌습니다. 뒤쪽 카메라로 비추고 있습니다.」라고 알려 드리고, 통화 화면에도 「뒤쪽 카메라 영상이 가고 있습니다.」가 보입니다. 카메라가 켜지지 않거나 4초 동안 영상이 한 장도 찍히지 않으면 화질을 낮춰 다시 켜 보고, 그래도 안 되면 앞쪽 카메라로 켭니다. 끝내 켜지지 않으면 「카메라를 켜지 못해 영상이 가지 않습니다. 말소리만 이어집니다.」라고 한 번 알려 드립니다. 길눈의 카메라 허락이 꺼져 있으면 요청을 시작할 때 미리 말씀드리고, 통화 화면 글과 톡백으로 알려 드립니다. 폰 설정의 애플리케이션, 길눈, 권한에서 카메라를 허용해 주십시오. 카메라를 한 번도 허락하지 않으셨으면 요청할 때 허락 창이 뜹니다. 받는 자봉 앱도 영상이 실제로 오는지 살피고, 오지 않으면 받는 분 화면에 「길손님 카메라 영상은 아직 오지 않았습니다. 말소리는 이어져 있습니다.」라고 알려 드립니다.",   // 2.72.0 (261024-A60, 이사장님 지시 2026-10-24)
            "긴급통화 — 내 자리 보내기" to "긴급통화를 요청하시는 동안과 통화하시는 동안 지금 내 자리를 8초마다 받는 분께 보내 드립니다. 받는 분 화면에는 주소, 국가지점번호, 지도 위쪽을 12시로 둔 바라보시는 쪽, 움직이시는 빠르기나 멈춰 계신지, 위성 오차, 몇 초 전에 받은 자리인지가 나오고, 지도로 열기를 누르면 카카오맵에 길손이로 표시됩니다. 요청 그만두기나 통화 끊기 단추 바로 아래 내 자리 보내는 중 — 그만 보내기를 누르시면 보내지 않고, 내 자리 보내기 — 다시 보내기를 누르시면 다시 보냅니다. 요청이나 통화를 마치시면 저절로 그칩니다.",
            "말로 그린 길 — 집에 가자, 사무실 가자" to "말로 그린 길이 있는 폰에서는 말로 하기에서 집에 가자, 사무실 가자처럼 그 길의 도착지 이름과 가자를 말씀하시면 단추를 누르지 않으셔도 곧바로 말로 그린 길 안내를 시작합니다. 사무실에서 집으로 말로 그린 길 안내를 시작합니다라고 말씀드린 뒤 첫 안내가 나옵니다. 집으로 가 줘, 사무실까지 안내해처럼 말씀하셔도 됩니다. 길 목록은 폰에 남겨 두므로 그 밖에 펼치기를 열지 않으셔도 됩니다. 나스 음악 열쇠를 넣은 폰에서만 됩니다.",
            // 2.63.0 (261017-A51, 이사장님 승인 2026-10-17) 웹 길눈에서 옮긴 것 2단계
            "요즘 찾으신 곳" to "목적지를 정하실 때마다 길눈이 그 이름을 적어 둡니다. 길 찾기 첫 화면의 요즘 찾으신 곳을 누르시면 최근 것부터 다섯씩 나오고, 이름을 누르시면 그곳으로 바로 가십니다. 같은 곳을 여러 번 찾으셨으면 몇 번째인지도 함께 알려 드립니다. 서른 곳까지 이 폰 안에만 담기고 밖으로 나가지 않습니다. 이 목록 지우기로 언제든 비우실 수 있습니다.",
            "찾기 칸 자동 입력" to "어디로 가실까요 칸에 글자를 치시면 요즘 찾으신 곳과 즐겨찾기에서 맞는 곳을 칸 바로 아래에 세 줄까지 내놓습니다. 지 한 글자만 치셔도, ㅈㄹㅅ처럼 초성만 치셔도 잡힙니다. 즐겨찾기는 주소로도 찾습니다. 소리는 맨 위 후보 하나만 한 번 읽어 드립니다. 후보 줄을 누르시거나, 칸에서 그냥 엔터를 치시면 맨 위 후보로 곧바로 갑니다. 적으신 이름과 딱 맞는 곳이 있으면 그곳으로 가고, 역이나 정류장, 터미널로 끝나게 적으시면 후보로 끌려가지 않고 적으신 그대로 찾습니다.",
            "교통편 — 기차, 고속버스, 시외버스, 여객선" to "길 찾기 첫 화면의 교통편을 누르시면 기차, 고속버스, 시외버스, 여객선 가운데 고르십니다. 출발지 이름을 적고 엔터를 치시면 맞는 역이나 터미널이 다섯씩 나오고, 이름을 누르시면 출발지로 정해집니다. 도착지도 같은 방식으로 정합니다. 여객선은 그 출항지에서 가는 곳만 목록으로 나옵니다. 오늘, 내일, 모레 가운데 고르시면 그날 편이 출발 시각 차례로 다섯씩 나옵니다. 기차는 출발 시각, 열차 종류, 도착 시각, 어른 요금을, 고속버스와 시외버스는 출발과 도착 시각, 등급, 요금을 읽어 드립니다. 편을 누르시면 다시 읽어 드립니다. 편 목록 아래 예매하기를 누르시면 기차는 코레일 누리집이 출발역과 도착역이 채워진 채 열리고, 고속버스는 코버스, 시외버스는 버스타고, 여객선은 가보고싶은섬이 열립니다. 결제는 직접 하시며 길눈은 카드 정보를 다루지 않습니다. 목적지가 25킬로미터 넘게 멀면 목적지 화면에도 교통편 단추가 나옵니다. 자료는 국토교통부와 한국해양교통안전공단의 공공데이터입니다.",
            "섬으로 가기와 타기 전에 챙길 것" to "목적지가 제주도, 울릉도, 독도, 백령도처럼 육지 길로 이어지지 않는 섬이면 걸어가기와 차로 가기를 내놓지 않고, 비행기로 가실 공항과 배로 가실 항구를 가까운 차례로 알려 드립니다. 독도는 울릉도까지 배로 가신 뒤 다시 배를 타셔야 하며, 울릉도에 계시면 도동항을 알려 드립니다. 목적지가 공항이나 항구이면 목적지 화면 아래에 타기 전에 챙길 것이 생깁니다. 펼치시면 미리 신청하실 것과 챙기실 것을 알려 드리고, 항구는 그곳 바람 알아보기로 지금 바람이 얼마나 센지 알려 드립니다.",
            "지금 시각과 지하철이 없는 고장" to "목적지 화면에서 지금 몇 시인지를 함께 봅니다. 이미 끊겼을 만한 것은 이름 뒤에 지금은 끊겼을 수 있습니다가, 막차가 한 시간 안에 끊기는 것은 막차가 곧 끊깁니다가 붙습니다. 어림잡은 다니는 때는 지하철 다섯 시 반부터 자정까지, 버스 다섯 시부터 밤 열한 시 반까지입니다. 시간표는 노선마다 다르니 알려 드리고 고르시는 것은 직접 하십니다. 지하철이 다니는 고장은 수도권, 천안과 아산, 춘천, 부산과 김해와 양산, 대구와 경산, 광주, 대전입니다. 지금 자리와 목적지가 모두 그 밖이면 지하철로 가기를 내놓지 않습니다.",
            // 2.62.0 (261017-A50, 이사장님 승인 2026-10-17) 웹 길눈에서 옮긴 것
            "차 안 터널 미리 알림" to "차나 버스로 가시는 동안 터널에 들어가기 전에 곧 무슨 터널입니다, 길이는 약 몇 킬로미터입니다라고 미리 알려 드립니다. 터널 안에서는 위성이 끊겨 그때 가서는 알 수 없기 때문입니다. 지금 빠르기로 15초 뒤에 닿을 자리의 터널을 알리고, 300미터가 안 되는 터널은 짧습니다라고만 합니다. 이름이 없는 터널은 어느 고장인지를 붙여 드리고, 터널이 잇따라 붙어 있으면 몇 개 이어진다고 한 번에 말씀드립니다. 터널 안에서 위성이 끊기면 터널 안이라 위성이 잠시 끊깁니다라고 한 번 알리고, 빠져나오면 터널을 지났습니다라고 합니다. 기차의 터널은 기차 타고 가기에 있습니다. 찻길 터널만 알려 드립니다. 지하상가, 지하보도, 건물 밑 통로는 터널로 치지 않고, 이름 없는 350미터 아래 터널도 알리지 않습니다. 차가 그 터널 길 위를 터널 쪽으로 달릴 때만 알리고, 옆길이나 위를 지날 때는 알리지 않습니다.",
            "듣기 예약 — 정한 시각에 라디오 틀기" to "음악·방송 첫 화면의 듣기 예약에서 정한 시각에 라디오를 틀게 하실 수 있습니다. 새 듣기 예약 만들기 펼치기를 누르시고 틀 시각(7시, 6시 반, 0730처럼), 되풀이(날마다, 평일만, 주말만, 한 번만), 방송, 끌 때(그대로, 30분 뒤, 1시간 뒤, 2시간 뒤)를 고른 뒤 이대로 예약하기를 누르십시오. 그 시각이 되면 폰이 길눈을 깨워 손대지 않아도 그 방송이 나옵니다. 정확한 시각에 틀게 허락하기 단추가 보이면 눌러 알람 및 리마인더를 허용해 주십시오. 허락 전에는 몇 분 늦을 수 있습니다. 예약 줄을 누르면 잠시 끄거나 다시 켜고, 이 예약 지우기로 지웁니다. 끄기 예약은 지금 나오는 방송을 15분, 30분, 1시간, 1시간 반 뒤에 끕니다. 말로 하기에서 매일 7시에 MBC 표준FM 틀어 줘, 평일 아침 6시 반 KBS 1라디오 예약, 내일 6시에 라디오 틀어 줘, 30분 뒤에 라디오 꺼 줘, 듣기 예약 알려 줘, 듣기 예약 지워 줘라고 하셔도 됩니다. 통화 중이면 틀지 않습니다.",   // 2.80.0 (261027-A68, 이사장님 승인 2026-10-27)
            "일정 — 듣기와 넣기, 고치기, 아침 브리핑, 떠날 시각" to "설정 탭의 일정에서 폰 달력의 일정을 듣고 넣으실 수 있습니다. 일정은 길눈이 따로 담지 않고 폰 달력(구글 캘린더)에 담기므로 폰을 바꾸셔도 남고, 폰 달력에 들어 있는 다른 일정도 함께 들으실 수 있습니다. 처음 한 번 달력 허락하기를 누르고 허용을 눌러 주십시오. 일정 내용은 폰 밖으로 보내지 않습니다. 오늘 일정 듣기, 내일 일정 듣기, 이번 주 일정 듣기 단추가 있고, 일정 넣기 펼치기에서 제목, 장소, 날짜, 시각(또는 하루 종일), 길이, 되풀이를 넣고 이대로 넣기를 누르시면 됩니다. 길눈이 넣은 일정은 한 시간 전과 30분 전에 폰 달력이 알려 드립니다. 다가오는 일정 목록에서 일정을 누르시면 그곳으로 가는 길 고르기 화면을 열거나, 시각과 날짜를 바꾸거나, 지우실 수 있습니다. 고치기와 지우기는 길눈에서 넣은 일정만 되고, 다른 곳에서 넣은 일정은 읽기만 합니다. 지운 일정은 하루 안에 방금 지운 일정 되살리기로 되살립니다. 말로 하기에서 오늘 일정 알려 줘, 내일 뭐 있어, 이번 주 일정, 다음 주 일정, 다음 일정, 내일 오후 2시 국립극장 회의 넣어 줘, 매주 화요일 오전 10시 회의 넣어 줘, 내일 회의 3시로 바꿔 줘, 금요일 회의 월요일로 옮겨 줘, 금요일 점심 약속 지워 줘, 방금 지운 일정 되살려 줘라고 하셔도 됩니다. 넣기, 바꾸기, 지우기는 반드시 한 번 읽어 드리고 네를 들은 뒤에 합니다. 오전과 오후를 말하지 않으시면 1시부터 6시는 오후로 알아듣고, 읽어 드릴 때 바로잡으실 수 있습니다. 맞는 일정이 여럿이면 번호를 붙여 읽고 몇 번인지 여쭙니다. 아침 브리핑을 켜 두시면 정한 시각(날마다, 평일만, 주말만)에 폰이 길눈을 깨워 오늘 날짜, 날씨, 오늘 일정, 첫 일정 떠날 시각을 읽어 드리고, 그 무렵에 듣기 예약으로 라디오를 걸어 두셨으면 이어서 틉니다. 아침 브리핑 7시로 해 줘, 아침 브리핑 꺼 줘, 아침 브리핑 들려 줘라고 하셔도 됩니다. 떠날 시각 알림을 켜 두시면 앞으로 24시간 안의 장소가 있는 일정마다 지금 자리에서 그곳까지 걸리는 시간을 어림해, 떠나실 시각 15분 전과 떠나실 시각에 알려 드리고, 떠나실 시각에는 길 안내를 시작할지 여쭙니다. 네 하시면 그곳으로 가는 안내를 시작합니다. 어림은 곧은 거리로 셈하므로 실제 길과 차편에 따라 다를 수 있고, 여유는 10분, 20분, 30분 가운데 고르십니다. 장소 이름을 길 찾기에서 찾지 못하면 그렇다고 말씀드리고 일정 알림만 드립니다. 정확한 시각에 알리게 허락하기 단추가 보이면 눌러 알람 및 리마인더를 허용해 주십시오. 일정이라는 말이 붙은 말과 날짜·시각이 붙은 넣기·바꾸기·지우기 말만 일정으로 알아듣고, 라디오, 방송, 정기 호출, 콜이 들어간 말은 일정으로 알아듣지 않습니다.",   // 2.81.0 (261027-A69, 이사장님 승인 2026-10-27)
            "점지도 등급 — A급, B급, C급" to "협회는 자원봉사자가 그린 점지도를 점검해 A급, B급, C급으로 매깁니다. A급은 점검을 통과해 안심하고 따라가셔도 되는 길입니다. B급은 고칠 곳이 몇 곳 있는 길로, 그 자리 15미터 앞에서 곧 이 점지도의 고칠 곳입니다라고 미리 알려 드리니 그 자리에서는 더 조심하십시오. C급은 아직 그리는 중인 길이라 참고만 하시고 주변을 더 살피며 걸으십시오. 점지도를 고를 때 목록 줄 끝에 몇 급인지 붙고, 따라 걷기를 시작할 때 끌 수 없는 말로 한 번 알려 드립니다. B급과 C급은 그린 분이나 다른 봉사자 누구든 보완할 수 있고, 보완해서 기준을 넘으면 등급이 저절로 오릅니다.",   // 2.79.0 (261026-A67)
            "가까운 자동심장충격기, 안전비상벨, 지하철 엘리베이터" to "둘러보기 탭의 둘레 찾기에 자동심장충격기가 겉에 있고, 안전비상벨과 지하철 엘리베이터·출입구 리프트는 그 밖에 펼치기 안에 있습니다. 자동심장충격기와 안전비상벨은 공공데이터포털 자료로, 지하철 엘리베이터와 출입구 리프트는 서울 열린데이터광장 자료로 1킬로미터 안에서 가까운 차례로 알려 드립니다. 엘리베이터와 리프트는 서울 자료만 있습니다. 말로 하기에서 근처 심장충격기, 근처 비상벨, 근처 엘리베이터라고 말씀하셔도 됩니다. 점지도를 따라 걸으실 때 지나는 곳 안내가 켜져 있으면 25미터 안 앞쪽의 지하철 엘리베이터와 리프트도 지나는 곳으로 알려 드립니다. 또 점지도 없이 걸을 수 있는 길로 안내받으실 때, 서울에서는 서울시 대로변 횡단보도 자료로 길이 큰길을 건너는 자리를 더 찾아 15미터 앞에서 건널목이 있다고 미리 알려 드리고 음향신호기를 살핍니다.",   // 2.79.0 (261026-A67, 이사장님 승인 2026-10-26 「1」 공공데이터 적용)
            "차가 크게 돌 때" to "차를 타고 가시다 차가 크게 돌면 방금 3시 방향으로 돌았습니다, 목적지까지 얼마 남았습니다라고 알려 드리고, 조금 뒤 여기는 어디입니다라고 그 자리 이름을 붙여 드립니다. 몸으로 느끼신 그 순간에 여기가 어디인지 아시도록 하려는 것입니다. 120도 넘게 돌면 크게 돌아라고 덧붙입니다. 도는 방향은 폰이 재 주는 차의 진행 방향으로 셈하고, 돈 뒤 방향이 3초쯤 한결같을 때만 돌기 전 방향과 견주어 말씀드립니다. 건물 사이에서 위성이 흔들려 방향이 오락가락하면 말하지 않습니다.",
            "차가 멈춰 있을 때" to "차가 멈춰 있는 동안에는 위성이 조금씩 흔들려도 움직이는 것으로 치지 않아 헛되이 말하지 않습니다. 멈춘 까닭을 확실히 알 때만 한 번 말씀드립니다. 버스를 타고 계시고 30미터 안에 정류장이 있으면 무슨 정류장에 멈춰 있습니다, 30미터 안에 사거리가 있으면 사거리 앞에 멈춰 있습니다라고 합니다. 신호 때문인지 막혀서인지는 짐작하지 않습니다. 까닭을 모르면 1분, 2분, 3분, 5분, 10분째에 몇 분째 멈춰 있습니다라고만 하고, 20초 넘게 섰다가 다시 움직이면 다시 움직입니다라고 합니다.",
            "지하철 다음 열차" to "지하철로 가기에서 길을 찾으면 타실 호선과 방면의 다음 열차가 몇 분 뒤에 오는지 알아 두고, 다음 열차 다시 알아보기 단추에 적어 둡니다. 단추를 누르시면 새로 알아보고 말씀드립니다. 타는 역 출구에 닿아 열차를 타시라고 할 때도 다음 열차가 몇 분 뒤에 오는지 이어서 말씀드립니다. 자료는 서울 열린데이터광장의 실시간 도착 정보라 서울 지하철만 됩니다.",
            "지난 여정과 도착 여쭙기" to "목적지를 정할 때 거리로 도착 시간을 어림해 둡니다. 걸으시면 시속 4킬로미터, 타고 가시면 시속 25킬로미터로 셈하고 길이 굽은 만큼 더합니다. 그 시간에서 30분이 지나도 여정이 남아 있으면 여쭙고, 맨 위에 지난 여정 끝내기와 이어서 가기 단추를 드립니다. 어림하지 못한 여정은 1시간 뒤에 여쭙니다. 목적지 50미터 안에서 2분 넘게 머무시면 도착하셨습니까라고 여쭙고, 맨 위에 도착했습니다 단추를 드립니다. 걸어서 가는 차례에는 걷는 안내가 도착을 알려 드리므로 여쭙지 않습니다.",
            "얼마나 걸리는지 묻기" to "말로 하기에서 얼마나 걸려, 언제 도착해라고 물으시면 남은 시간을 말씀드립니다. 차나 버스로 가시는 중이면 지금 길 상황을 넣어 차로 가는 길의 거리와 걸리는 시간을 말씀드립니다. 버스는 정류장마다 서므로 이보다 더 걸린다고 덧붙입니다. 기차는 타신 열차를 맞췄으면 내리실 역 도착 예정 시각을 말씀드립니다. 길 상황을 받지 못하면 거리로 어림해 말씀드립니다.",
            "탈것 바로잡기" to "길눈이 탈것을 잘못 알고 있거나, 타고 있는 것이 바뀌었는데 길눈이 알아채지 못하면, 첫 화면 맨 위 큰 단추 바로 아래의 탈것 바꾸기를 누르십시오. 지금은 무엇으로 알고 있습니다라는 말이 함께 나옵니다. 누르시면 그 자리에 목록이 펼쳐집니다. 차를 타고 있습니다, 걷고 있습니다, 기차를 타고 있습니다, 버스를 타고 있습니다, 지하철을 타고 있습니다, 배를 타고 있습니다 가운데 지금 알고 있는 것을 뺀 것이 나옵니다. 하나를 고르시면 목록은 다시 접히고 한 줄로 돌아옵니다. 말로 하기에서 탈것 바꿔라고 하셔도 목록이 펼쳐집니다. 길눈이 스스로 탈것을 바꾸었을 때, 내리셨는지 여쭐 때, 목적지에서 멀어진다고 알릴 때는 목록을 저절로 펼쳐 두고, 3분이 지나면 접습니다. 이 한 줄은 타고 가는 중에도, 남은 길을 걷는 차례에도 늘 있습니다. 차가 막혀 서 있어 길눈이 걷는다고 잘못 알면 차를 타고 있습니다를, 내려서 걷는데 아직 차로 알면 걷고 있습니다를 누르십시오. 고속버스는 여정 다른 할 일 펼치기의 그 밖의 탈것 바로잡기에 있습니다. 바로잡으신 것이 가장 앞서며, 길눈이 빠르기로 다르게 짐작해도 바꾸지 않습니다. 차, 기차, 고속버스로 바로잡으시면 돌던 지하철이나 버스 안내를 멈추고 곧바로 그 탈것 안 안내로 넘어갑니다. 기차나 고속버스를 타고 가는 동안에는 터널에서 위성과 통신이 끊겨도 지하철로 보지 않습니다. 말로 하기에서 기차 탔어, 기차야, 케이티엑스, 고속버스야, 택시 탔어, 배 탔어, 여객선이라고 말씀하셔도 똑같이 바로잡습니다. 기차와 배는 한 번 알면 바꾸실 때까지 지킵니다. 기차나 배 안에서 걸어 다니셔도 풀지 않고, 다른 탈것으로 바로잡으시거나 내렸습니다, 도착, 여정 끝내기 때만 풉니다. 역이나 항구 가까이에서 2분 넘게 걸으며 멀어지시면 내리셨습니까라고 한 번 여쭙고, 아직 타고 있습니다 단추를 그 자리에 둡니다. 차로 가기를 고르셨는데 기차역 근처에서 먼 기차역으로 가시면, 기차를 타셨습니까라고 한 번 여쭙고 목록을 펼쳐 둡니다. 차 부르기로 부른 콜에 타고 계시면 차 대신 복지콜을 타고 있습니다처럼 그 콜 이름으로 보입니다.",
            "기차 타고 가기 — 기차를 알아보는 법" to "부산역, 서울역 같은 기차역 근처에서 타시고 목적지가 멀리 있는 기차역이면 처음부터 기차로 압니다. 시속 150킬로미터를 넘게 달려도 기차로 압니다. 기차로 알면 기차를 타신 것으로 압니다, 기차가 아니면 바로 아래 탈것 목록에서 바로잡으십시오라고 알려 드리고, 그 자리 맨 위 큰 단추 아래에 탈것 목록을 3분 펼쳐 둡니다. 처음부터 기차를 타실 때는 목적지 화면에서 기차로 가기를 누르십시오. 기차로 안 뒤에는 바꾸실 때까지 기차로 지킵니다. 기차 안에서는 간판 알림, 길 벗어남, 세워 달라는 말을 하지 않고, 터널을 땅속으로 보지 않습니다. 기차 안에서는 역에 다가가면 곧 무슨 역에 도착합니다, 역에 서면 무슨 역에 도착했습니다, 내리실 역이 아니니 그대로 계십시오, 떠나면 무슨 역을 떠났습니다, 가는 쪽으로 다음 역은 무슨 역입니다, 서지 않고 지나치면 지금 무슨 역을 지나고 있습니다라고 알려 드립니다. 내리실 역에 다가가면 여기서 내리십니다, 내릴 준비를 하십시오라고 합니다. 터널에 들어가기 전에는 곧 무슨 터널입니다, 길이와 터널 안에 있는 시간을 미리 알려 드립니다. 터널 안에서는 위성이 끊기기 때문입니다. 역을 떠나면 열차 시간표로 타신 열차를 맞춰, 타신 열차는 KTX 몇 호로 보입니다, 내리실 역 도착 예정은 몇 시 몇 분입니다라고 알려 드리고, 다음 역 도착 예정 시각과 서지 않는 역도 알려 드립니다. 기차 안에서는 찻길 이름은 말하지 않고 동네 이름만 말합니다. 터널과 다음 역은 앱 안에 담은 우리나라 철길 자료로, 타신 선로 위에서만 찾습니다. 그래서 바로 옆에 나란히 놓인 다른 노선의 터널이나 역은 말하지 않고, 다음 역까지의 거리도 철길을 따라 잰 거리입니다. 철길 자료는 앱 안에 있어 인터넷이 끊겨도 됩니다. 역에 서려고 속도를 줄이는 동안에는 터널을 말하지 않고, 역을 떠나며 알려 드립니다. 타신 열차를 맞추면 내리실 역까지 그 열차가 서는 큰 역을 시간표로 모두 찾아, 타신 열차는 내리실 역까지 가는 동안 어느 역, 어느 역에 섭니다라고 한 번에 알려 드립니다.",
            "배 타고 가기 — 바다 위 지금 자리" to "여객선 터미널이나 항구에서 떠나 물 위를 가면 배로 압니다. 배입니다 단추나 말로 하기의 배 탔어, 여객선으로 알려 주셔도 됩니다. 배 안에서는 지금 어느 바다인지, 떠난 항구에서 몇 킬로미터 나왔는지, 목적지까지 몇 킬로미터와 몇 분 남았는지, 배가 가는 쪽을 12시로 삼아 가까운 섬이나 등대가 몇 시 방향 몇 킬로미터에 있는지를 한 번에 말씀드립니다. 섬이나 등대를 2킬로미터 안으로 지날 때 한 번, 설정의 배 안에서 지금 자리 말하는 간격(처음 10분, 5분, 20분, 30분, 끔)마다, 지금 어떻게 가고 있습니까나 지금 내 자리를 누르실 때 말씀드립니다. 목적지 1킬로미터 앞에서는 곧 닿는다고 알려 드립니다. 배 안에서는 간판 알림, 사거리, 길 벗어남을 말하지 않습니다.",
            "지금 어떻게 가고 있습니까" to "여정 중에는 길 찾기 탭의 지금 차례 첫 단추를 누르시면 지금 어디로 무엇을 타고 가는 중인지, 남은 거리와 방향, 차 안이면 지금 달리는 길 이름, 지하철이면 몇 정거장 남았는지 말씀드립니다.",
            "여정 끝내기와 목적지 바꾸기" to "도착하면 첫 화면의 지금 차례가 도착했습니다, 여정 끝내기로 바뀝니다. 가는 중에 목적지를 바꾸시려면 여정 다른 할 일 펼치기에서 여정 끝내기를 누른 뒤 새로 찾으시면 됩니다. 여정은 폰 안에 담겨 길눈을 껐다 켜도 이어지고, 열두 시간 넘게 손대지 않으면 저절로 끝납니다. 안내는 화면을 떠나도, 음악을 틀어도, 폰을 잠가도 이어집니다.",
            "도착했을 때 말로 마치기" to "가시던 곳에 닿으시면 말로 하기를 누르신 뒤 도착, 도착했어, 다 왔어라고 말씀하십시오. 도착하셨습니다 하고 여정을 마칩니다. 여정 끝이라고 하셔도 같습니다.",
            // 2.76.0 (261025-A64, 이사장님 승인 2026-10-25, 아이폰 2.109.0과 같음)
            "위치·마이크 허락 켜기" to "길눈은 폰의 허락을 스스로 켜거나 바꿀 수 없습니다. 폰이 허락은 사람만 바꾸게 막아 두었기 때문입니다. 그래서 길눈이 먼저 알려 드리고 설정 화면을 바로 열어 드립니다. 처음 여실 때 위치 허락이 모자라면 한 번, 그 뒤로는 허락이 나빠졌을 때 한 번 알려 드립니다. 알림 창의 설정 열기를 누르시면 폰 설정의 길눈 화면이 바로 열립니다. 안드로이드에서는 권한, 위치에서 앱 사용 중에만 허용을 고르시고 정확한 위치 사용을 켜 주십시오. 폰 위치 자체가 꺼져 있으면 위치 설정 화면이 열리니 위치를 켜 주십시오. 언제든 설정 탭 맨 위의 위치 허락 단추와 마이크 허락 단추로 지금 상태를 들으시고 설정을 여실 수 있습니다. 위치가 꺼져 있으면 길 안내를 시작하지 않습니다.",
            "점자블록 — 있는지 없는지 알려 드립니다" to "점자블록은 이어졌다 끊어졌다 하는 곳이 많아, 점지도는 점자블록을 따라 돌아가는 길이 아니라 가깝고 안전한 길로 그립니다. 그 대신 길 위에 점자블록이 있는지 없는지를 알려 드립니다. 점자블록이 시작되는 곳에서는 여기서부터 점자블록이 있습니다, 끊기는 곳에서는 여기서 점자블록이 끊깁니다, 지팡이로 길을 확인하며 가십시오, 다시 나오는 곳에서는 점자블록이 다시 이어집니다라고 말씀드립니다. 서른 걸음쯤 앞에서 미리 한 번, 닿으시면 한 번 더 알려 드립니다. 자원봉사자가 끊긴 뒤 따라갈 것을 목소리로 남겼으면 그 목소리도 들려 드립니다. 거꾸로 걸으실 때는 끊김과 이어짐을 바꿔 말씀드립니다.",
            "굽은 길 — 둥글게 돌아가는 길" to "공원 산책로나 건물을 끼고 도는 길처럼 몇 걸음에 걸쳐 둥글게 돌아가는 곳은, 자원봉사자가 점지도를 그릴 때 굽은 길로 남깁니다. 따라 걸으실 때 서른 걸음쯤 앞에서 3시 방향으로 천천히 굽은 길입니다, 약 15걸음 이어집니다처럼 미리 알려 드리고, 닿으시면 여기서부터 3시 방향으로 천천히 굽습니다, 길을 따라 조금씩 도십시오라고 말씀드립니다. 한 번에 크게 꺾는 꺾임과 달리 길을 따라 조금씩 도시면 됩니다. 굽은 길이 끝나면 굽은 길이 끝납니다라고 알려 드립니다. 거꾸로 걸으실 때는 방향을 거울처럼 바꿔 말씀드립니다.",
            "목소리 따라 걷기 — 자봉 목소리 토막" to "점지도를 그린 자원봉사자가 계단, 건널목, 턱 같은 표시를 남길 때 그 자리에서 짧게 말해 둔 목소리가 있으면, 따라 걸으실 때 그 표시 일곱 미터 앞에서 길눈 안내 말 바로 뒤에 그 목소리를 그대로 들려 드립니다. 목소리는 걸음 자리에 묶여 있어 걸으시는 빠르기에 맞고, 이미 지나친 자리의 목소리는 건너뜁니다. 협회 서버가 받아쓰기로 말과 표시가 맞는지 살핀 토막만 들려 드립니다. 설정 탭 점지도와 걸음의 점지도 안내 설정 펼치기에서 끄실 수 있습니다.",
            "방향은 시계 방향으로" to "길눈은 모든 방향을 시계 방향으로 말씀드립니다. 지금 얼굴과 몸이 향한 쪽이 12시, 바로 옆 오른손 쪽이 3시, 뒤가 6시, 왼손 쪽이 9시입니다. 꺾을 때는 지금 3시 방향으로 꺾으십시오, 지나는 곳은 2시 방향에 편의점입니다처럼 알려 드립니다. 자원봉사자가 예전에 오른쪽, 왼쪽으로 남긴 점지도도 길눈이 시계 방향으로 바꿔 읽어 드립니다. 카메라로 QR이나 글자를 찾을 때는 카메라 화면의 가운데에서 본 쪽을 위가 12시인 시계로 알려 드립니다.",   // 2.87.0 / 2.57.0 (261016-I41 / 261016-A45, 이사장님 승인 2026-10-16) 큐알을 QR로 한 이름
            "차 안 간판 알림 — 지나는 가게와 건물을 시계 방향으로" to "차 안 안내를 받으실 때 저절로 돕니다. 차가 초속 4미터 넘게 달리면, 지나는 자리 둘레의 가게와 건물을 카카오 지도 자료에서 찾아 달리는 방향을 열두 시로 놓고 시계 방향으로 가려 2시 방향에 GS25, 편의점처럼 알려 드립니다. 곧 옆을 지날 곳 하나만 고르며, 지하철역, 공공기관, 관광명소, 문화시설, 마트, 은행, 약국, 병원을 식당이나 카페보다 먼저 알려 드립니다. 한 번 알린 곳은 30분 안에 다시 말하지 않고, 지나는 곳 말하는 간격의 3분의 1, 적어도 15초에 한 번까지만 말씀드립니다. 서 있을 때 창밖 간판 읽기를 켜 두시면, 차가 서 있거나 천천히 갈 때 카메라가 창밖 간판을 읽어 보탭니다. 이때는 폰 뒤쪽 카메라를 창밖으로 향해 주십시오. 다시 달리면 카메라는 저절로 꺼집니다. 두 가지 모두 설정 탭의 말하기 설정에서 켜고 끄실 수 있습니다. 가게 이름을 찾을 때 지금 자리만 협회 나스를 거쳐 카카오에 묻고, 사진은 보내지 않습니다.",
            "여러 점지도 이어 걷기" to "한 점지도로 닿지 않는 곳이라도 협회가 이어 둔 길로 닿으면, 걸어가기를 누르셨을 때 이어진 점지도로 걸을지 여쭙니다. 가까운 점지도 화면의 이어서 갈 수 있는 곳 펼치기에서 직접 고르실 수도 있습니다. 한 구간을 마치면 남은 구간 수를 알리고 곧장 다음 구간으로 이어 안내하므로 단추를 누르지 않으셔도 됩니다. 문까지 안내는 마지막 구간에서만 합니다. 이어진 길의 다음 구간을 불러오지 못하면 멈추지 않고 위성 안내로 이어 갑니다. 이어진 길을 다 걸은 뒤 되돌아가기를 누르시면 이어진 길 전체를 거꾸로 걷습니다.",
            "걸어서 도착한 뒤 문까지 — 시계 방향과 걸음 수" to "점지도를 따라 걷기 시작한 뒤 여섯 걸음 넘게 걸으셔야 도착으로 칩니다(걸음 센서를 쓸 수 없으면 20초가 지난 뒤). 차에서 내리자마자 위성 오차 때문에 도착했다고 하지 않게 하려는 것입니다. 위성으로 문 가까이에 들었어도 문까지 3미터 넘게 남았으면 문 앞이 아니라 문 가까이 왔습니다라고 알립니다. 문까지 이어 안내를 시작할 때 담아 둔 문 생김새가 있으면 미리 말씀드립니다. 걸어서 목적지에 닿으면 길눈이 내가 담아 둔 문이나 협회 문 창고의 문 자리를 찾아, 문은 1시 방향 약 15걸음입니다처럼 시계 방향과 걸음 수로 문까지 이끕니다. 거리나 방향이 바뀌면 다시 알려 드리고, 문 앞 5미터 안에 들면 문 앞 가까이에 닿았다고 알린 뒤 카메라 문 찾기가 마지막 몇 걸음을 거듭니다. 위성 오차가 크면 몇 걸음 어긋날 수 있다고 한 번 알려 드립니다. 담아 둔 문이 없는 곳에서는 예전처럼 카메라 문 찾기가 켜집니다. 3분 안에 닿지 못하면 안내를 마치며, 그만이나 하던 일 멈추기로 언제든 멈추실 수 있습니다. 자주 가시는 곳의 문은 설정 탭 점지도와 걸음의 내 문 담기로 한 번 담아 두시면 다음부터 그 문으로 이끕니다.",   // 2.38.0 (261013-A26)
            "말로 하기를 못 알아들을 때" to "말씀은 들어왔는데 듣는 시간이 다 지나도록 글자가 하나도 나오지 않으면, 길눈이 받아쓰기가 막힌 것으로 보고 받아쓰기를 새로 세운 뒤 다시 한번 말씀해 주세요라고 하고 한 번 더 듣습니다. 시끄러운 길이나 역에서 말씀하시기도 전에 끊지 않도록, 듣는 시간은 끝까지 기다립니다. 전화를 끊은 뒤 처음 말로 하기를 쓰실 때도 받아쓰기를 새로 세워, 통화가 남긴 마이크 막힘을 풉니다.",   // 2.38.0 (261013-A26)
            "전철역만 말씀하실 때 — 어디까지 가십니까" to "말로 하기에서 역 이름 없이 전철역, 가까운 전철역, 지하철역 가자처럼만 말씀하시면, 길눈이 가까운 역을 알려 드리고 어디까지 가십니까라고 여쭙니다. 가실 곳을 이름이나 북아현로 26-10 같은 주소로 말씀하시면, 타실 역과 방면, 갈아타실 역, 내리실 역까지 지하철 길로 안내합니다. 역까지만 가시려면 역 이름을 말씀하시면 그 역까지 걸어서 이끕니다.",   // 2.39.0 (261013-A27)
            "걷는 중 목적지에서 멀어질 때" to "걷는 안내 중에 목적지에서 가장 가까웠던 때보다 1킬로미터씩 멀어질 때마다 한 번 알려 드립니다. 걸음은 거의 늘지 않았는데 멀어졌으면 탈것을 타신 것으로 봅니다. 목적지가 역이면 열차를 타신 것 같습니다, 어디까지 가십니까라고 여쭙고 마이크를 엽니다. 가실 곳을 말씀하시면 열차 안에서 곧장 지나는 역과 내리실 역을 알려 드립니다.",   // 2.39.0 (261013-A27)
            "통화 뒤 묻던 말 다시 여쭙기" to "목적지를 바꿀지, 어디로 가실지, 어떻게 가실지를 여쭙던 중에 전화가 오면, 통화가 끝난 뒤 통화 전에 여쭙던 것을 다시 여쭙니다라고 하고 그 물음을 다시 여쭙습니다. 통화가 15분 넘게 이어졌으면 다시 여쭙지 않습니다.",   // 2.39.0 (261013-A27)
            "문까지 이어 안내" to "점지도 끝 30미터 앞에서 협회가 모아 둔 문을 찾아 문까지 이어 안내합니다. 여기서부터는 위성 안내라 몇 미터 오차가 있을 수 있습니다. 문까지 몇 시 방향, 몇 걸음인지 자주 말씀드리고, 문 5미터 안에서 문 앞이라고 알리며 들어가는 쪽이 몇 시 방향인지 알려 드립니다. 위성이 흐리면 문 앞을 10미터까지 넓게 보고, 문을 90초 넘게 찾으면 문 쪽과 거리를 알려 드린 뒤 마칩니다. 문이 여럿이면 길 찾기 탭 맨 위에 다른 문으로 단추가 나옵니다. 말로 하기에서 다른 문이라고 하셔도 됩니다.",
            "내 문 담기" to "설정 탭의 점지도와 걸음에서 내 문을 여십시오. 문 두 걸음 앞에서 문 쪽을 보고 문 앞에서 한 번 찍기를 누르시고, 문을 지나 두 걸음 들어가서 한 번 더 찍기를 누르십시오. 두 자리 사이가 문으로, 들어가신 쪽이 들어가는 쪽으로 이 폰에 담깁니다. 문 찾기를 쓸 수 있는 폰은 첫 번째 찍을 때 카메라가 문 둘레 글자(호수, 출입구 같은 것)를 함께 읽어 담습니다. 사진은 담지 않습니다. 문까지 안내에서 내 문을 가장 먼저 씁니다. 이름을 적어 두시면 그 이름으로 말씀드립니다. 지우시려면 그 줄을 길게 누르시거나 톡백 동작에서 이 문 지우기를 고르십시오.",
            "함께 시험하기" to "점지도를 따라 걷는 중에 따라 걷기 다른 할 일 펼치기에서 함께 시험 번호 받기를 누르시면 네 자리 번호를 말씀드립니다. 곁의 자봉께 번호를 알려 주시면 자봉이 지금 자리와 다음 표시를 함께 따라 보며 점지도를 확인합니다. 마치실 때는 같은 자리의 함께 시험 끝내기를 누르십시오. 그만 걷기를 하셔도 함께 끝납니다.",
            "따라 걷는 동안 길목과 버스 정류장" to "점지도를 따라 걷는 중에 따라 걷기 다른 할 일 펼치기에서 이 길목은 어떻게 생겼습니까를 누르시면 지금 선 곳이 어떤 길목인지, 몇 시 방향으로 어느 길이 있는지 말씀드립니다. 지도에 그려진 얼개일 뿐이니 발밑은 지팡이로 살펴 주십시오. 가까운 버스 정류장을 누르시면 가까운 정류장 세 곳을 시 방향과 거리로 알려 드립니다. 말로 하기에서 길목, 정류장이라고 하셔도 됩니다.",
            "따라 걷는 동안 지나는 곳" to "점지도를 따라 걸으실 때 25미터 둘레의 가게와 조심할 곳을 3시 방향, 9시 방향, 앞으로 나누어 알려 드립니다. 뒤에 있는 것은 말하지 않습니다. 목적지 20미터 안에서는 문을 찾는 데 집중하도록 쉽니다. 끄고 켜기는 설정 탭 점지도와 걸음의 점지도 안내 설정 펼치기에서 지나는 곳 안내로 합니다.",
            "앱이 꺼졌다 켜져도 점지도로 이어 걷기" to "점지도를 따라 걷다가 앱이 꺼지거나 폰을 다시 켜셔도, 3시간 안이면 앱이 켜질 때 하던 점지도 따라 걷기를 이어 가고 길 찾기 첫 화면을 엽니다. 이어진 길을 걷던 중이었으면 걷던 구간부터 잇습니다. 그만 걷기나 여정 끝내기를 하시면 이어 가지 않습니다.",
            "여기 문제 있어요, 여기 걸렸어요" to "따라 걷다가 점자블록이 없어졌거나 공사 중이거나 무엇이 길을 막고 있으면 길 찾기 탭 맨 위의 여기 문제 있어요를 누르고 여섯 가지 가운데 고르십시오. 한마디를 적으셔도 되고 안 적으셔도 됩니다. 지금 자리와 함께 협회에 남습니다. 여기 걸렸어요는 이 자리가 걸리는 곳이라고 남겨 다음에 오시는 분께 60미터 앞에서 미리 알려 드리게 합니다.",
            "나만의 점지도" to "설정 탭의 점지도와 걸음에서 나만의 점지도를 여십시오. 걷기 시작을 누르고 평소대로 걸으시면 길을 그립니다. 오름턱, 내림턱, 계단, 문·입구, 조심할 곳에 닿으면 그 단추를 누르시고, 한마디를 적어 남기실 수도 있습니다. 걷기 끝을 누르신 뒤 이름을 적고 나만 쓰기 또는 모두가 쓰도록 점지도에 올리기를 고르십시오. 나만 쓰기는 이 폰에만 담기고, 올리시면 다른 시각장애인도 따라 걸을 수 있습니다. 내가 그린 길을 누르시면 따라 걷기, 되돌아가기, 올리기, 맡기기, 지우기가 있습니다. 맡기기는 잠금말을 걸어 협회 서버에 두는 것이며 잠금말은 서버로 가지 않습니다. 폰을 바꾸시면 같은 잠금말로 찾아오십시오. 아이폰 길눈이나 웹 길눈에서 맡긴 길도 같은 잠금말로 찾아옵니다.",
            "걸음 오차 재기" to "설정 탭의 점지도와 걸음에서 걸음 오차 재기를 여십시오. 미리 재 둔 실제 거리와 내 보폭, 걷는 빠르기, 흰지팡이를 쓰시는지 고르고 재기 시작을 누르십시오. 끝까지 걸으신 뒤 다 걸었습니다를 누르시면 걸음으로 잰 거리와 위성으로 잰 거리가 실제 거리와 몇 퍼센트 다른지 알려 드립니다. 이 결과 담기를 누르시면 협회에 남아 점지도를 바로잡는 데 쓰입니다.",
            "점지도 안내 설정" to "설정 탭의 점지도와 걸음에서 점지도 안내 설정 펼치기를 여십시오. 걸을 때 확신음을 끄고 켜실 수 있고, 끄셔도 방향이 틀어졌을 때의 말은 그대로 나옵니다. 제대로 가고 있다는 말은 5미터, 10미터, 20미터 가운데 고르십니다. 자봉 목소리 토막 듣기와 지나는 곳 안내도 여기서 끄고 켭니다. 말하기 설정에서 바꾸셔도 같은 값이 바뀝니다.",
            "꺾는 곳 진동" to "점지도를 따라 걷다가 지금 도십시오라고 말씀드릴 때 폰과 갤럭시 워치가 함께 떱니다. 2시에서 5시 방향으로 도실 때는 길게 한 번, 7시에서 10시 방향은 짧게 두 번, 6시 방향으로 도실 때는 아주 길게 한 번입니다. 곧게 가시는 곳에서는 떨지 않습니다. 도착하면 세 번 떱니다.",
            "리모컨 배우기" to "설정 탭의 기기 설정에서 리모컨 배우기를 여십시오. 블루투스 리모컨을 폰과 먼저 연결한 뒤, 1번 자리 익히기를 누르고 리모컨 단추를 한 번 누르시면 그 단추가 지금 어디입니까에 담깁니다. 2번은 다음에 무엇이 있습니까, 3번은 다시 말해 주기입니다. 익힌 단추는 점지도를 따라 걷는 동안 길 찾기 첫 화면이 켜져 있을 때 쓰입니다. 폰이 잠겨 있을 때는 이어폰 단추를 쓰십시오. 소리 크기 단추만 보내는 리모컨은 쓸 수 없고, 전자책 페이지 넘김 리모컨이 잘 맞습니다.",
            "이어폰·리모컨 단추" to "점지도를 따라 걷거나 되짚어 나가거나 말로 그린 길을 걷는 동안에는 이어폰이나 리모컨의 단추를 길눈이 받습니다. 재생 단추는 마지막 안내를 다시 들려 드리고, 음향신호기 앞이면 신호 안내를 울립니다. 재생 단추를 0.6초 넘게 길게 누르시면 화면이 꺼져 있어도 말로 하기가 열립니다. 다음 단추는 걷는 중이면 다음에 무엇이 있는지, 되짚어 나가는 중이면 지금 할 일을, 아니면 지금 내 자리를 알려 드립니다. 이전 단추는 앞 안내를 다시 들려 드립니다. 안드로이드는 마지막으로 소리를 낸 앱에 단추를 넘겨주므로 안내 중에는 길눈이 소리 없는 소리를 틀어 단추를 받습니다. 안내가 끝나면 음악 앱이 다시 단추를 받습니다. 설정 탭의 기기 설정에서 안내 중 이어폰 단추 받기를 끄시면 음악 앱이 단추를 받습니다.",
            "이어폰 앞 안내 거듭 듣기" to "이어폰의 이전 단추를 누르시면 방금 한 말의 앞 말을 다시 들려 드리고, 거듭 누르시면 한 말씩 더 앞으로 갑니다. 새 안내가 나오면 처음부터 다시 셉니다.",
            "되짚어 나가기 — 들어온 길로 혼자 나오기" to "길 찾기 탭의 그 밖에 펼치기 안에 있습니다. 병원 진료실처럼 건물 안에 들어가실 때 들어갑니다 — 지금부터 길을 기억하기를 누르시고 평소처럼 걸어 들어가십시오. 걸음 수와 방향으로 길을 기억합니다. 기억하는 동안에는 나갑니다 — 왔던 길 되짚어 나가기, 지금 상태 듣기, 길 기억 멈추기 — 기억한 길은 남깁니다가 있습니다. 나오실 때 나갑니다 — 왔던 길 되짚어 나가기를 누르시거나, 기억을 멈추신 뒤라면 첫 줄의 되짚어 나가기 — 기억한 몇 걸음, 몇 번 꺾은 길로 나가기를 누르시면, 먼저 몇 시 방향으로 돌아 서실지 알려 드리고, 몇 걸음 걸으신 뒤 몇 시 방향으로 꺾으실지 차례로 말씀드립니다. 꺾는 곳 다섯 걸음 앞에서 미리, 열 걸음마다 남은 걸음을, 방향이 크게 어긋나면 돌아 서실 쪽을 알려 드립니다. 안내하는 동안에는 지금 할 일 다시 듣기와 되짚어 나가기 멈추기가 있습니다. 기억한 길이 있을 때 새로 들어갑니다 — 다시 기억하기를 누르시면 기억한 길을 지우고 새로 기억할지 한 번 여쭙고, 10초 안에 한 번 더 누르셔야 새로 기억합니다. 그 전에는 아무것도 지우지 않습니다. 기억한 길 지우기를 누르시면 바로 지웁니다. 기억하거나 안내하는 동안에는 길 찾기 탭 위쪽, 긴급통화서비스 단추 아래에 되짚어 나가기 한 줄이 나옵니다. 말로 하기에서 길 기억해 줘, 되짚어 나가자라고 하셔도 됩니다. 기억한 길은 이 폰에만 남고 지우실 때까지 남습니다. 어긋날 수 있으니 벽과 손잡이를 함께 짚어 가십시오.",   // 2.87.0 / 2.57.0 (261016-I41 / 261016-A45, 이사장님 승인 2026-10-16) 되짚어 나가기 단추를 두 앱 같게
            "말로 그린 길 — 실내 길 따라 걷기" to "길 찾기 탭의 그 밖에 펼치기 안에 있습니다. 몸으로 익혀 말로 적어 둔 실내 길을 손대지 않고 따라 걷게 해 드립니다. 가는 길 따라 걷기나 오는 길 따라 걷기를 한 번 누르시면, 그 뒤로는 폰이 걸음을 세고 방향을 보며 정해진 걸음에 닿으면 다음 안내를 스스로 말씀드립니다. 세 걸음 앞에서 다음 할 일을 미리 알려 드리고, 방향이 크게 틀어지면 멈추시라고 하며 몇 시 방향으로 돌아 서실지를, 예상보다 많이 걸으시면 확인하시라고 알려 드립니다. 엘리베이터는 멈췄다가 다시 걷기 시작하시면 내리신 것으로 봅니다. 걷는 동안 길 찾기 탭 위쪽, 긴급통화서비스 단추 아래에 한 줄이 나옵니다. 말로 하기에서 다시라고 하시면 지금 안내를 다시, 그만이라고 하시면 멈춥니다. 나스 음악 열쇠를 넣은 폰에서만 쓰실 수 있습니다.",   // 2.87.0 / 2.57.0 (261016-I41 / 261016-A45, 이사장님 승인 2026-10-16) 말로 그린 길 방향 안내
            "음성유도기와 승강기" to "길 찾기 탭의 그 밖에 펼치기 안에 있습니다. 여시면 지금 자리에서 가까운 지하철역 다섯 곳을 찾습니다. 역을 누르시면 음성유도기가 몇 곳, 엘리베이터와 에스컬레이터가 몇 대인지 알려 드리고, 펼치기를 여시면 어디에 있는지와 몇 층에서 몇 층까지 다니는지를 다섯 곳씩 읽어 드립니다. 서울교통공사 자료로 서울 1호선부터 8호선까지만 있고, 실제와 다를 수 있습니다. 말로 하기에서 음성유도기 어디 있어, 역 엘리베이터 어디 있어라고 하셔도 됩니다.",
            "흔들면 자리 번호 — 긴급통화 열기 또는 국가지점번호" to "설정 탭의 폰 펼치기 안의 흔들면 자리 번호에서 폰 흔들기를 켜십시오. 처음에는 꺼져 있습니다. 켜 두시면 폰을 세게 두 번 흔드실 때 긴급통화서비스가 곧바로 열립니다. 흔들면 하는 일을 누르시면 지금 자리의 국가지점번호 말하기로 바뀝니다. 국가지점번호는 산과 강과 바닷가처럼 도로명주소가 없는 곳에서 119가 쓰는 위치 번호이며, 폰이 스스로 셈하므로 통신이 끊겨도 됩니다. 숫자는 119에 불러 드리기 좋게 한 자씩 끊어 읽습니다. 걷다가 한 번 튀는 것은 흔들기로 치지 않습니다.",
            "QR 찾기" to "길 찾기 탭의 그 밖에 펼치기 안에 있습니다. 여시면 카메라가 켜지고 둘레의 QR을 저절로 찾습니다. 폰을 가슴 높이로 세워 들고 천천히 움직이십시오. 잡히면 삐 소리와 진동이 나고, 무엇이 담긴 QR인지와 카메라의 어느 쪽에 있는지를 알려 드립니다. 주소면 이 주소 열기, 전화번호면 이 번호로 전화 걸기가 나오고, 글 복사하기와 다른 QR 찾기 — 계속이 있습니다. 1분 동안 잡히지 않으면 찾는 중이라고 알려 드립니다. 어두우면 알아 두실 것 펼치기 안의 손전등을 켜십시오. 카메라 사진은 어디로도 보내지 않고 폰 안에서만 읽으며, 화면을 떠나면 카메라는 저절로 꺼집니다. 말로 하기에서 QR 찾아 줘라고 하셔도 됩니다.",
            // 2.88.0 / 2.58.0 (261016-I42 / 261016-A46, 이사장님 승인 2026-10-16) 겉 단추를 실제대로(두 단추만이 아님), 펼치면 수를 한 번 알림
            "즉석 글자 읽기 — 카메라를 대면 곧바로" to "둘러보기 탭에 있습니다. 말로 하기에서 글자 읽어 줘라고 하셔도 열립니다. 화면을 여시면 바로 읽기 시작합니다. 사진을 찍지 않아도 폰을 글자 쪽으로 세워 들고 천천히 움직이시면, 보이는 글자를 위에서 아래 차례로 곧바로 읽어 드립니다. 한 번 읽은 줄은 30초 안에 되풀이하지 않고 새로 보인 글만 읽습니다. 글자가 화면 끝에 걸려 잘리면 폰을 어느 쪽으로 옮기실지 3초에 한 번까지 알려 드립니다. 겉에는 멈추기와 이어 읽기, 방금 읽은 것 다시 듣기, 읽은 글 펼치기, 카메라 눈 말소리 켜기와 끄기, 알아 두실 것 펼치기가 차례로 있습니다. 읽은 글은 읽은 글 펼치기에 다섯 줄씩 남고, 펼치시면 모두 몇 줄인지 한 번 알려 드립니다. 어두우면 알아 두실 것 펼치기 안의 손전등을 켜십시오. 카메라 눈 말소리를 끄시면 말 없이 화면 글자로만 남습니다. 3분 동안 새 글이 없거나 화면을 떠나시면 카메라를 끕니다. 그만이라고 하셔도 멈춥니다. 사진과 글은 어디로도 보내지 않고 인터넷 없이 폰 안에서만 읽습니다.",
            "사람 감지 — 앞의 사람을 방향과 걸음 수로" to "둘러보기 탭에 있습니다. 말로 하기에서 사람 있어라고 하셔도 열립니다. 화면을 여시면 바로 시작합니다. 폰을 가슴 앞에 세워 들거나 목걸이로 걸고 걸으시면, 앞에 사람이 있을 때 1시 방향 사람, 3걸음처럼 알려 드립니다. 안드로이드에서는 한 번에 가장 뚜렷한 한 사람을 알려 드립니다. 3미터 안으로 가까워지면 확신음이 1초마다, 1.5미터 안이면 0.5초마다 울립니다. 말은 3초에 한 번쯤 바뀔 때만 하고, 사람이 사라지면 앞에 사람이 없습니다라고 한 번 알립니다. 지금 앞 다시 듣기를 누르시면 지금 상태를 다시 들려 드립니다. 거리는 사람 몸통 길이를 기준으로 어림한 것이라 앉은 사람이나 아이는 실제보다 멀게 들릴 수 있습니다. 카메라 눈 말소리를 끄시면 말 없이 확신음으로만 알립니다. 10분 동안 사람이 보이지 않거나 화면을 떠나시면 카메라를 끕니다. 그만이라고 하셔도 멈춥니다. 얼굴을 알아보거나 사진을 담지 않으며, 인터넷 없이 폰 안에서만 돕니다.",
            "빛 알아보기 — 불이 켜졌는지, 창이 어느 쪽인지" to "둘러보기 탭에 있습니다. 말로 하기에서 불 켜져 있어, 빛 알려 줘, 밝은 쪽 찾아 줘라고 하셔도 열립니다. 화면을 여시면 바로 시작합니다. 폰 뒤쪽 카메라가 보는 쪽이 밝을수록 높은 소리가 납니다. 폰을 천천히 돌리시면 가장 밝은 쪽에서 이쪽이 가장 밝습니다라고 알려 드려, 창이나 켜진 불이 어느 쪽인지 찾으실 수 있습니다. 불이 켜지거나 꺼지면 불이 켜진 듯합니다, 불이 꺼진 듯합니다라고 알려 드립니다. 밝기가 바뀌면 캄캄합니다, 어둡습니다, 실내 불빛 밝기입니다, 밝습니다, 햇빛처럼 아주 밝습니다 가운데 하나로 3초에 한 번까지 알려 드립니다. 지금 밝기 듣기를 누르시면 지금 밝기를 다시 들려 드립니다. 높낮이 소리를 끄시면 말로만, 카메라 눈 말소리를 끄시면 높낮이 소리로만 알려 드립니다. 10분 동안 밝기가 바뀌지 않거나 화면을 떠나시면 카메라를 끕니다. 그만이라고 하셔도 멈춥니다. 폰 안에서만 살피며 사진은 담지도 보내지도 않습니다.",
            // 2.88.0 / 2.58.0 (261016-I42 / 261016-A46, 이사장님 승인 2026-10-16) 카메라 눈 화면을 모두 적고, 단추가 없는 화면을 밝힘
            "카메라 눈 말소리" to "설정 탭의 말하기 설정과 카메라 눈 화면마다 있습니다. 카메라 눈 화면은 즉석 글자 읽기, 가리키고 말하기, 사람 감지, 상품 바코드 읽기, 지폐와 색깔 알아보기, 빛 알아보기, 한마디 설명, 문 찾기입니다. 처음에는 켜져 있습니다. 끄시면 카메라 눈이 말 없이 확신음이나 높낮이 소리, 화면 글자로만 알립니다. 다만 상품 이름은 꺼 두셔도 읽어 드립니다. 켜고 끈 것과 끌 수 없는 안전 경고(발 앞 계단·턱 알림)는 그대로 말씀드립니다. 사진 읽어 주기와 안면인식에는 이 단추가 없고 늘 말씀드립니다.",
            "문 찾기 — 관리자 시험 중" to "안드로이드에서는 관리자 시험 중이라 나스 음악 열쇠를 넣은 폰의 설정 탭 더 보기, 관리자 시험 안과 길 찾기 탭 그 밖에 펼치기에만 보입니다. 안드로이드에는 문 자체를 알아보는 기능이 없어, 문 위나 옆의 호수, 출입구, 화장실, 당기세요 같은 짧은 글자를 카메라로 찾아 몇 시 방향 문, 약 몇 걸음인지 알려 드립니다. 걸음은 글자 크기로 어림한 것입니다. 문 찾기 켜기를 누르시고 폰을 가슴 앞에 세워 들고 천천히 걸으십시오. 문 글자가 보이면 확신음과 함께 알려 드리고, 3초에 한 번쯤 바뀔 때만 말씀드립니다. 문 바로 앞에 이르면 도착 소리와 함께 알리고 카메라를 끕니다. 문이 보이지 않는 동안에는 8초마다 문을 찾고 있다고 알려 드리고, 30초가 지나도록 문이 한 번도 보이지 않으면 카메라를 끈 뒤 사진 읽어 주기로 문 앞을 찍어 보시거나 긴급통화로 현장영상해설사에게 요청하시기를 권해 드립니다. 점지도 문까지 안내에서는 90초까지 찾고, 문이 보인 뒤 3분 넘게 닿지 못하면 끕니다. 나스 음악 열쇠를 넣은 폰에서는 점지도 문까지 안내에서 문 10미터 안에 드시거나 걸어서 목적지에 닿으시면 저절로 켜지고, 내 문 두 번 찍기 때 문 둘레 글자를 함께 담습니다. 내 문에 글자를 담아 두신 문을 찾아가실 때는 찍어 두신 문인지, 옆 문인지도 알려 드리고, 문 바로 앞을 알린 뒤 문을 지나 두 걸음 들어가시면 손대지 않아도 두 번 찍은 것으로 그 문을 내 문에 담습니다. 글자가 없는 문은 찾지 못하니 지팡이로 꼭 함께 확인하십시오. 그만이라고 하시면 멈춥니다.",   // 2.87.0 / 2.57.0 (261016-I41 / 261016-A45, 이사장님 승인 2026-10-16) 문 찾기 30초 규칙을 실제대로
            "발 앞 계단·턱 알림 — 관리자 시험 중" to "관리자 시험 중인 기능이라 관리자 열쇠가 있는 폰의 설정 탭 더 보기, 관리자 시험 안에만 보입니다. 구글 AR 깊이 재기를 지원하는 폰에서만 되며, 구글 플레이 AR 서비스가 없으면 설치 화면을 엽니다. 화면을 여시면 바로 시작합니다. 폰을 가슴 앞에 들고 카메라가 앞쪽 바닥을 보도록 조금 숙여 걸으시면, 발 앞 바닥이 갑자기 꺼지거나 솟는 곳을 두 걸음 앞, 내려가는 턱, 약 15센티미터처럼 경고음과 함께 알려 드립니다. 40센티미터가 넘으면 크게 꺼진 곳, 앞을 막는 높은 것으로 알려 드립니다. 같은 턱은 한 걸음 넘게 가까워질 때만 다시 알립니다. 안전 경고라 말소리를 꺼도 늘 알립니다. 계단이 몇 단인지는 세지 않습니다. 앞쪽 바닥이 15초 넘게 안 보이면 폰을 더 숙여 달라고 말씀드립니다. 20분이 지나거나 화면을 떠나시면 끕니다. 그만이라고 하셔도 멈춥니다. 지팡이를 대신하지 않으며 지팡이와 함께 쓰십시오. 폰 안에서만 살피며 사진이나 깊이 자료를 담지도 보내지도 않습니다.",
            "한마디 설명 — 인터넷 없이 카메라 앞을 한마디로" to "둘러보기 탭의 빛 알아보기 바로 아래에 있습니다. 말로 하기에서 이게 뭐야, 뭐가 보여라고 하셔도 열립니다. 화면을 여시면 바로 시작합니다. 폰 뒤쪽 카메라를 궁금한 쪽으로 향하시면 보이는 것을 컵, 책상인 듯합니다처럼 대강 한마디로 알려 드립니다. 얼굴이 보이는 사람이 있으면 몇 명인지, 글자가 있으면 글자가 보입니다라고 덧붙입니다. 보이는 것이 바뀔 때만, 4초에 한 번까지 말씀드립니다. 인터넷 없이 폰 안에서만 짐작하는 것이라 틀릴 수 있으니 참고로만 들으십시오. 자세한 설명은 사진 읽어 주기를, 글자를 읽으시려면 즉석 글자 읽기를 쓰십시오. 카메라 눈 말소리를 끄시면 말 없이 화면 글자로만 남습니다. 방금 한마디 다시 듣기를 누르시면 마지막 한마디를 다시 들려 드립니다. 5분 동안 새로 알릴 것이 없거나 화면을 떠나시면 카메라를 끕니다. 그만이라고 하셔도 멈춥니다. 사진은 담지도 보내지도 않습니다.",
            "지폐와 색깔 알아보기 — 옷과 물건의 색, 지폐" to "둘러보기 탭의 상품 바코드 읽기 바로 아래에 있습니다. 말로 하기에서 무슨 색이야, 색깔 알려 줘, 지폐 알려 줘, 얼마짜리야라고 하셔도 열립니다. 화면을 여시면 바로 시작합니다. 폰 뒤쪽 카메라를 옷이나 물건에 한 뼘쯤 떨어뜨려 대시면 가운데 색을 진한 남색, 연한 하늘색, 밝은 빨강처럼 늘 쓰는 색 이름으로 알려 드립니다. 무늬가 있으면 흰색 바탕에 검정 무늬처럼 바탕색과 무늬 색을 나누어 알려 드립니다. 색은 바뀔 때만, 3초에 한 번까지 말씀드립니다. 지폐는 숫자가 보이는 쪽을 카메라로 향해 주시면 천 원, 오천 원, 만 원, 오만 원을 확신음 한 번과 함께 알려 드리고, 같은 지폐는 10초 안에 되풀이하지 않습니다. 너무 어두우면 조금 더 밝은 곳에서 대 주십시오라고 먼저 알려 드립니다. 색은 불빛에 따라 달라 보여 노란 전등 아래에서는 조금 다르게 들릴 수 있습니다. 옷 맞춰 입기, 양말 짝 맞추기, 과일 익은 정도 보기에 쓰실 수 있습니다. 카메라 눈 말소리를 끄시면 색과 지폐는 말 없이 화면 글자로만 남고(지폐는 확신음이 울립니다), 너무 어둡다는 알림만 말씀드립니다. 방금 것 다시 듣기를 누르시면 마지막으로 알린 것을 다시 들려 드립니다. 5분 동안 새로 알릴 것이 없거나 화면을 떠나시면 카메라를 끕니다. 그만이라고 하셔도 멈춥니다. 폰 안에서만 살피며 사진은 담지도 보내지도 않습니다.",
            "상품 바코드 읽기 — 상품 이름을 읽어 드림" to "둘러보기 탭의 사람 감지 바로 아래에 있습니다. 말로 하기에서 바코드 읽어 줘, 이 상품 뭐야라고 하셔도 열립니다. 화면을 여시면 바로 시작합니다. 폰을 한 손에 들고 상품을 폰 뒤쪽 카메라에서 한 뼘쯤 떨어뜨려 천천히 돌려 주십시오. 바코드를 찾으면 확신음이 한 번 울리고 상품 이름을 읽어 드립니다. 이름을 찾지 못하면 우리나라 상품입니다처럼 어느 나라 상품인지와 바코드 번호를 한 자씩 읽어 드립니다. 같은 상품은 10초 안에 되풀이해 읽지 않습니다. 바코드는 보통 뒷면이나 옆면 아래쪽에 있으며, 20초 동안 찾지 못하면 찾는 법을 한 번 알려 드립니다. 방금 상품 다시 듣기를 누르시면 마지막 상품을 다시 들려 드리고, 이번에 읽은 상품 펼치기에서 앞서 읽은 것들을 다섯 개씩 보실 수 있습니다. 카메라 눈 말소리를 끄시면 찾는 법 알림은 하지 않고 상품 이름만 읽어 드립니다. 10분 동안 바코드를 찾지 못하거나 화면을 떠나시면 카메라를 끕니다. 그만이라고 하셔도 멈춥니다. 바코드 알아보기는 폰 안에서 하고 사진은 담지도 보내지도 않습니다. 상품 이름을 찾을 때만 바코드 번호를 무료 공개 상품 자료에 물어봅니다. 공개 자료에 없는 우리나라 상품은 이름이 나오지 않을 수 있습니다.",
            // 2.88.0 / 2.58.0 (261016-I42 / 261016-A46, 이사장님 승인 2026-10-16) 겉 단추를 실제대로(두 단추만이 아님), 펼치면 수를 한 번 알림
            "가리키고 말하기 — 손가락으로 가리킨 단추 읽기" to "둘러보기 탭의 즉석 글자 읽기 바로 아래에 있습니다. 말로 하기에서 가리키는 거 읽어 줘라고 하셔도 열립니다. 전자레인지, 세탁기, 엘리베이터, 키오스크처럼 단추가 많은 곳에서 쓰십시오. 화면을 여시면 바로 시작합니다. 한 손으로 폰을 단추판 쪽으로 들고, 다른 손 검지로 단추를 가리키시면 손끝이 가리키는 쪽 가장 가까운 낱말을 읽어 드립니다. 손끝을 처음 찾으면 확신음과 함께 손가락이 보입니다라고 알립니다. 손가락을 옮기시면 새 글에 닿을 때마다 읽고, 같은 글에 머무시면 되풀이하지 않습니다. 손가락이 화면 밖으로 나가면 폰을 조금 뒤로 빼시라고 세 번까지 알려 드립니다. 겉에는 멈추기와 이어 읽기, 방금 읽은 것 다시 듣기, 읽은 글 펼치기, 카메라 눈 말소리 켜기와 끄기, 알아 두실 것 펼치기가 차례로 있습니다. 읽은 글은 읽은 글 펼치기에 다섯 개씩 남고, 펼치시면 모두 몇 개인지 한 번 알려 드립니다. 카메라 눈 말소리를 끄시면 화면 글자로만 남습니다. 3분 동안 새로 읽은 글이 없거나 화면을 떠나시면 카메라를 끕니다. 그만이라고 하셔도 멈춥니다. 안드로이드에서는 처음 한 번 손가락 찾기 자료(약 8메가바이트)를 인터넷으로 받아야 합니다. 그 뒤로는 사진과 글을 어디로도 보내지 않고 인터넷 없이 폰 안에서만 읽습니다.",
            // 2.88.0 / 2.58.0 (261016-I42 / 261016-A46, 이사장님 승인 2026-10-16) 둘러보기 첫 화면 차례
            "둘러보기 첫 화면 차례" to "둘러보기 탭 첫 화면은 아이폰과 안드로이드가 같은 차례입니다. 하나, 둘레 찾기. 둘, 사진 읽어 주기. 셋, 즉석 글자 읽기. 넷, 가리키고 말하기. 다섯, 사람 감지. 여섯, 상품 바코드 읽기. 일곱, 지폐와 색깔 알아보기. 여덟, 빛 알아보기. 아홉, 한마디 설명. 열, 안면인식. 열하나, 가는 김에 펼치기. 가는 김에 펼치기 안에는 가 볼 곳, 축제, 무장애 여행 정보, 고장 이야기, 어디서 어디로, 마실이 차례로 있습니다. 탭을 고르실 때마다 커서가 첫 줄 둘레 찾기로 갑니다. 찾은 곳 같은 목록은 다섯 개씩 나오고, 다섯 줄 아래의 더 보기로 다음 다섯을, 이전 보기로 앞의 다섯을 봅니다.",
            // 2.88.0 / 2.58.0 (261016-I42 / 261016-A46, 이사장님 승인 2026-10-16) 대피소 3킬로미터를 적음
            "둘레 찾기 — 식당, 약국, 화장실, 응급실" to "둘러보기 탭 맨 위의 둘레 찾기를 누르십시오. 식당, 카페, 병원, 약국, 편의점, 화장실, 버스정류장, 응급실, 대피소가 겉에 있고, 은행, 대형마트, 주차장, 주유소, 관광명소, 문화시설, 공공기관, 숙박은 그 밖에 펼치기 안에 있습니다. 고르시면 1킬로미터 안에서, 대피소는 3킬로미터 안에서 가까운 차례로 다섯 곳씩 나오고, 나오면 첫 줄로 초점이 갑니다. 다섯 줄 아래의 더 보기로 다음 다섯을, 이전 보기로 앞의 다섯을 봅니다. 이름을 두드리시면 그곳까지 안내합니다. 2킬로미터 안이면 곧장 걷는 안내를, 멀면 길 찾기 탭에서 어떻게 가실지 고르시는 화면을 엽니다. 전화 걸기와 주소 듣기는 이름을 길게 누르시거나 톡백 동작 메뉴에서 고르십시오. 식당은 밥집 갈래를 먼저 고르시고, 대피소는 지진, 민방위, 무더위, 한파 가운데 고르십시오. 말로 하기에서 근처 약국처럼 말씀하시면 가까운 세 곳을 들려 드리고 둘러보기 탭에 목록을 열어 드립니다.",
            "가는 김에 — 가 볼 곳, 축제, 무장애 여행 정보" to "둘러보기 탭의 가는 김에 펼치기 안에 있습니다. 가 볼 곳, 축제, 무장애 여행 정보를 지금 계신 곳에서 10킬로미터 안으로 가까운 차례로 다섯 곳씩 알려 드립니다. 축제는 여는 날과 닫는 날도 말씀드립니다. 이름을 두드리시면 그곳으로 안내하고, 전화와 주소는 이름을 길게 누르시거나 톡백 동작 메뉴에서 고르십시오. 말로 하기에서 축제 알려 줘라고 하셔도 가까운 세 곳을 말씀드립니다.",
            "고장 이야기 — 지나는 고장의 먹을 곳·볼 곳" to "둘러보기 탭 가는 김에 펼치기의 고장 이야기에서 지금 이 고장 이야기 듣기를 누르시면, 지금 계신 고장의 먹을 곳, 볼 곳, 축제를 짧게 들려 드립니다. 같은 화면의 차 안에서 고장이 바뀌면 들려 주기를 켜 두시면, 차나 버스, 기차로 가시는 동안 고장이 바뀔 때 한 번씩 저절로 들려 드립니다. 지하철 안에서는 들려 드리지 않습니다. 같은 고장은 15분에 한 번만 말씀드립니다. 처음에는 켜져 있습니다. 말로 하기에서 고장 이야기라고 하셔도 됩니다.",
            "사진 읽어 주기" to "둘러보기 탭의 사진 읽어 주기를 여시면 폰 뒤쪽 카메라가 켜집니다. 읽을 것을 비추고 사진 찍어 읽어 주기를 누르시면 그 자리에서 한 장을 찍어 무엇이 보이는지 짧게 읽어 드리고, 글이 화면에 나오며 초점이 그리로 갑니다. 사진기 앱의 셔터 단추를 찾지 않으셔도 됩니다. 더 자세히 읽어 주기를 누르시면 길게 풀어 말씀드립니다. 이 두 가지는 사진을 협회 나스로 보내 읽습니다. 글씨만 읽어 주기는 사진을 어디로도 보내지 않고 폰 안에서 글씨만 읽습니다. 잘 모르겠으면 사람에게 물어보기를 누르시면 긴급통화서비스 화면이 열립니다. 5분 동안 찍지 않거나 화면을 떠나시면 카메라를 끕니다. 말로 하기에서 사진 읽어 줘라고 하셔도 이 화면이 열립니다.",
            "안면인식 — 카메라 앞의 사람" to "둘러보기 탭의 안면인식에서 안면인식 켜기를 누르시고, 폰을 세워 든 채 뒤쪽 카메라를 앞으로 향하십시오. 몇 명이 있는지, 몇 시 방향인지, 대략 몇 미터인지, 이쪽을 보는지를 말씀드립니다. 웃는 듯하면 웃는 듯합니다라고 조심스럽게 짐작해 드립니다. 거리와 웃음은 짐작이니 참고로만 들으십시오. 보이는 것이 바뀔 때만, 3초에 한 번까지 말씀드립니다. 사진은 어디로도 보내지 않고 폰 안에서만 봅니다. 아는 사람 등록 펼치기에서 이름을 적고 그분 얼굴이 카메라에 정면으로 보일 때 지금 앞의 얼굴로 등록을 누르시면, 다음부터 그분인 듯하면 이름을 말씀드립니다. 안드로이드에서는 얼굴 생김새와 얼굴 밝기 무늬로 어림하는 것이라 아이폰보다 훨씬 자주 틀리거나 못 알아봅니다. 등록한 얼굴 값은 폰 안에만 보관하고 폰 백업에도 넣지 않습니다. 지우실 때는 이름을 길게 누르시거나 톡백 동작 메뉴에서 지우기를 고르십시오. 화면을 떠나면 카메라는 저절로 꺼집니다. 말로 하기에서 안면인식이라고 하셔도 켜집니다.",
            "어디서 어디로 — 미리 들어 보기" to "둘러보기 탭 가는 김에 펼치기의 어디서 어디로에서 가실 곳을 적고 엔터를 치신 뒤 고르시면, 지금 자리에서 그곳까지 차로 가면 몇 킬로미터에 몇 분, 지금 떠나면 몇 시쯤 닿는지, 지나는 길 이름과 거치는 자리를 한 덩이로 들려 드립니다. 3킬로미터 안이면 걸어서 몇 분, 몇 걸음쯤인지도 말씀드립니다. 출발지 바꾸기 펼치기에서 출발지를 다른 곳으로 바꾸실 수도 있습니다. 다시 듣기로 한 번 더 들으시고, 이 길로 가기를 누르시면 곧바로 안내가 시작됩니다.",
            "마실 — 앉은자리에서 떠나는 여행" to "둘러보기 탭 가는 김에 펼치기의 마실에서 떠나실 고장을 고르십시오. 가는 길과 그 고장의 것을 한 마디씩 들려 드리고, 다음으로를 누르시면 다음 마디로 넘어갑니다. 가끔 길에서 겪는 일도 만나시고, 여쭈어 보는 마디에서는 답을 고르시면 맞았는지 말씀드립니다. 마지막 마디를 들으시면 다녀왔습니다로 마무리합니다. 같은 곳을 다시 떠나셔도 겪는 일이 달라집니다. 웹 길눈의 마실과 같은 이야기입니다. 말로 하기에서 마실 가자라고 하셔도 이 화면이 열립니다.",
            "길 위의 음악" to "음악·방송 탭의 길 위의 음악을 여십시오. 지나는 고장 노래(저절로 틀기, 차에 타면 저절로, 고장 이름으로 찾기)는 모든 시각장애인에게 드리는 협회의 선물이라 열쇠 없이 누구나 들으십니다. 갈래와 테마 틀기, 가수나 곡 이름으로 찾기 같은 나스 음악 전체는 웹 길눈과 같은 열쇠로 들어가며, 나스 음악 열쇠는 협회에서 받으십시오. 한 번 넣으시면 폰에 담아 두어 다시 묻지 않습니다. 갈래 단추와 테마 단추를 누르면 다섯 개씩 나오는 목록에서 고르시고, 이어서 틀기를 누르시면 이어서 틀어 드립니다. 그 아래 가수나 곡 이름으로 찾기 칸에 이름을 적고 찾기를 누르시면 찾은 곡을 곧바로 틉니다. 지나는 고장 노래 저절로 틀기를 켜시면 1분마다 지나는 고장을 보아, 고장이 바뀌면 그 고장 노래로 갈아 틀어 드립니다. 그 아래 차에 타면 지나는 고장 노래 저절로 틀기를 켜 두시면(처음에는 꺼져 있습니다. 원하시면 켜십시오) 차, 버스, 기차에 타실 때 저절로 켜지고 내리시면 고장 따라 바꾸기만 멈춥니다. 라디오, TV, 기사를 듣고 계실 때는 건드리지 않습니다. 말로 하기에서 고장 노래 틀어 줘, 고장 노래 꺼라고 하셔도 됩니다. 고장 이름으로 찾기도 있습니다. 곡이 나오는 동안에는 화면 맨 위에 지금 곡과 다음 곡, 이전 곡, 멈춤, 그만 듣기가 있습니다. 지금 목록 보기 펼치기에서 곡을 골라 트실 수도 있습니다. 다음 곡은 미리 폰에 받아 두어 굴속에서도 이어지고, 끊기면 멈춘 자리부터 다시 이어 받습니다. 열쇠가 없으셔도 누구나 음악 펼치기에서 잔잔한 음악, 밝은 음악, 모두 섞어 틀기를 쓰실 수 있습니다. 누구나 음악은 한 바퀴 다 들으시면 멈추고 알려 드립니다. 누구나 음악은 공유마당의 자유이용 곡이라 곡마다 저작자 표시가 화면에 나옵니다. 기분과 날씨로 틀기 펼치기에서는 기분이나 지금 날씨에 맞는 곡을 골라 틉니다. 곡이나 방송을 불러오지 못해 여섯 번 잇달아 다시 이어도 안 되면, 헛돌지 않게 멈추고 한 번만 알려 드립니다. 잠시 뒤 다시 틀어 주십시오. 곡을 받지 못하면 곧바로 그만두지 않고 2초 뒤 세 번, 30초 뒤, 그 뒤로 1분 뒤에 다시 이어 봅니다. 그래도 안 되면 멈추고 한 번 알려 드립니다.",
            "기분과 날씨에 맞춰 음악 틀기" to "음악·방송 탭 길 위의 음악의 기분과 날씨로 틀기 펼치기 안에 기분 전환, 흥겹게, 차분하게, 잠들기 전에, 마음을 달래, 분위기 있게, 지금 날씨에 맞게가 있습니다. 누르시면 그 기분에 맞는 곡을 골라 섞어 틉니다. 지금 날씨에 맞게는 비 오는 날, 눈 오는 날, 안개 낀 날, 흐린 날, 맑은 날, 밤 가운데 지금에 맞는 곡을 틉니다. 말로 하기에서 기분이 꿀꿀해, 신나는 노래 틀어 줘, 잔잔한 음악 틀어 줘, 잠이 안 와 음악 틀어 줘, 날씨에 맞게 틀어 줘라고 하셔도 됩니다. 나스 음악 열쇠를 넣지 않으셨으면 누구나 음악의 밝은 곡이나 잔잔한 곡으로 틉니다.",
            "라디오 듣기" to "음악·방송 탭의 라디오 듣기에서 채널 이름을 누르시면 곧바로 나옵니다. 다섯 채널씩 보여 드리고 더 보기로 넘기십니다. KBS와 MBC 라디오는 협회 나스가 이어 주어 몇 분 만에 끊기지 않습니다. 말로 하기에서 MBC 라디오, KBS 클래식FM처럼 말씀하셔도 됩니다. 채널 목록을 받지 못하면 다시 받기 단추가 나오니 누르십시오. 끊기거나 16초 넘게 소리가 나지 않으면 저절로 새 주소를 받아 다시 잇습니다. 듣는 동안 화면 맨 위에 멈춤과 라디오 그만 듣기가 있습니다.",
            "TV 보기" to "음악·방송 탭의 TV 보기에서 채널을 고르십시오. MBC 듣는방송24는 화면해설이 들어간 채널이며, 지금 나가는 자리부터 틉니다. 영상은 화면에 나오고, 폰을 잠그거나 다른 화면으로 가시면 소리만 이어집니다. TV 화면 바로 아래 전체화면으로 보기를 누르시면 영상이 옆으로 돌아 폰 화면 긴 쪽 가득 크게 나오니 폰을 눕혀서 보십시오. 맨 아래에는 전체화면 닫기 단추 하나만 있습니다. 뒤로 가기를 누르셔도 닫힙니다. 전체화면을 열고 닫아도 소리와 방송은 끊기지 않습니다. 영상 끄기를 켜시면 영상을 화면에서 빼고 가장 낮은 화질로 받아 데이터를 아낍니다. 끄시면 영상이 다시 나옵니다. 소리와 영상이 한 줄기인 채널은 그렇다고 알려 드립니다. 끊기면 저절로 다시 잇습니다. 채널 목록을 받지 못하면 다시 받기 단추가 나오니 누르십시오.",
            "라디오와 TV를 말로 틀기" to "말로 하기 단추를 누르신 뒤 MBC 라디오, 엠비씨 표준FM, KBS 클래식FM, 라디오 MBC처럼 말씀하십시오. 방송사 이름은 영문으로 들어오든 한글로 들어오든, 말 순서가 바뀌어도 알아듣습니다. 방송사만 말씀하시면 그 방송사의 으뜸 채널을 틉니다. MBC는 표준FM, KBS는 1라디오, EBS는 EBS FM입니다. 라디오 틀어 줘처럼 이름 없이 말씀하시면 지난번에 들으신 방송을 틉니다. 알아듣지 못한 이름이면 아무것도 틀지 않고 어느 방송을 들으실지 여쭙니다. TV는 KBS1 TV, MBC 듣는방송처럼 말씀하십시오.",   // 2.88.0 / 2.58.0 (261016-I42 / 261016-A46, 이사장님 승인 2026-10-16) 제목 부르기 → 틀기
            "지금 세상 이야기" to "음악·방송 탭의 지금 세상 이야기에서 갈래를 고르시면 최신 순으로 다섯 건씩 나옵니다. 갈래는 속보·특보, 두루 소식, 장애·복지, 정치, 경제, 국제, 사회, 스포츠·연예입니다. 기사를 고르시면 원문 전체를 읽어 드립니다. 읽는 동안 읽기 멈춤, 다음 기사, 처음부터 다시 읽기, 기사 목록으로가 제목 바로 아래에 있고, 본문은 그 아래에 글로도 나옵니다. 읽기를 멈췄다 이어 읽으시면 읽던 문단의 처음부터 이어 읽습니다. 기사 찾기에 낱말을 넣으시면 모든 갈래에서 찾습니다. 갈래 아래의 소식 새로 받아 오기를 누르시면 두루 소식을 새로 받아 옵니다. 기사를 읽는 동안에는 이 화면 맨 위에 읽고 있는 기사로가 나와, 누르시면 읽던 기사 화면으로 돌아갑니다. 목소리 고르기 펼치기에서 기사를 읽어 줄 목소리와 빠르기를 길 안내 목소리와 따로 정하실 수 있습니다. 길눈 말소리를 꺼 두셨으면 읽어 드리지 않고 글로만 띄워 톡백으로 들으시게 합니다. 말로 하기에서 뉴스 들려줘, 장애 소식, 속보라고 하셔도 됩니다.",
            "아침 기록 — 관리자" to "설정 탭 맨 아래 더 보기를 펼치시면 아침 기록 단추가 있습니다. 누르시면 지난 24시간 동안 길눈과 자봉 앱이 어떻게 쓰였는지를 한 줄씩 들려 드립니다. 맨 첫 줄은 요약으로, 아무 일 없었으면 밤새 별일 없었습니다, 살펴볼 일이 있으면 몇 가지인지 말씀드리고 그 아래에 먼저 적습니다. 살펴볼 일에는 옛 판을 쓰는 기기, 한 기기에서 같은 일이 백 번 넘게 되풀이된 것, 다섯 번 넘게 난 오류가 들어갑니다. 그 아래로 쓴 기기 수, 많이 쓴 기능, 길눈에 실린 점지도와 보완을 기다리는 길, 오류가 이어집니다. 자리나 말씀하신 내용처럼 사람을 알아볼 수 있는 것은 넣지 않고, 기기는 번호 앞 네 글자로만 적습니다. 같은 글이 매일 아침 이사장님 메일로도 갑니다.",
            // 2.85.0 / 2.55.0 (261016-I39 / 261016-A43, 이사장님 승인 2026-10-16) 콜 부르기 → 콜 요청
            "말로 목적지 바꾸기와 콜 요청 — 바꾸기 전에 꼭 여쭘" to "목적지를 정해 가시는 중에는 어떤 곳 이름이 들려도 곧바로 바꾸지 않고, 지금은 어디로 가시는 중인데 목적지를 새 곳으로 변경하실 건가요라고 먼저 여쭙니다. 네라고 하셔야 바뀝니다. 목적지를 바꾸시려면 목적지 바꿔, 또는 변경이라고 하시면 어디로 바꿀지 여쭙니다. 가자라는 말 없이 곳 이름만 말씀하시면 찾은 곳의 방향과 거리를 알려 드리고 여기로 안내할까요라고 한 번 여쭙니다. 그럼, 그래, 응 같은 한 마디는 곳 이름으로 찾지 않습니다. 복지콜 같은 교통편은 복지콜에 전화해 줘처럼 부르는 말이 있을 때만, 복지콜에 전화할까요라고 여쭌 뒤 네라고 하시면 겁니다. 복지콜 기다리는 중처럼 말씀하시면 다시 걸지 않고, 언제 전화하셨고 배차되었는지만 알려 드립니다. 앱으로 부른 콜이 배차되면, 차에 타셨다가 걸어서 내리실 때까지 지하철로 잘못 알아채지 않습니다. 새 목적지를 정하실 때는 앞서 알아챈 탈것을 지우고 지금 상태로 새로 봅니다. 남산 산책로 A코스(국립극장 쪽 세거리, 화장실 앞)와 B코스(남산케이블카 쪽 소파로 횡단보도 입구)는 이름으로 찾으실 수 있습니다. 곳 이름 같은 말을 찾지 못하면 앞 낱말로 다시 찾아 비슷한 곳을 여쭙니다. 말씀을 마치신 뒤 3초가 지나도 대답이 없으면 알아보는 중입니다라고 한 번 알려 드리고, 말벗에게 물은 대답이 10초가 넘으면 그만두고 다시 말씀해 주십사 여쭙니다. 길 찾기 첫 화면의 목적지 적는 칸에 글자를 넣으시는 동안에는 하이 길눈과 말로 하기 듣기를 쉬고, 화면의 줄이 생기거나 사라지지 않게 굳혀 둡니다. 5분이 지나면 저절로 다시 듣습니다. 긴 이름은 옆으로 밀리지 않고 아래로 줄을 바꾸며, 엔터를 누르시면 찾습니다. 차를 타고 가실 때 지하철이나 버스로 잘못 알아챘으면 택시야라고 말씀해 주십시오. 곧바로 차 안 안내로 바꿉니다. 짐작으로 시작한 지하철 안내 중에 땅 위를 차 빠르기로 30초 넘게 달리면 저절로 차 안 안내로 바꿉니다. 차 안의 남은 거리와 곧 부근입니다는 실제 갈 길의 거리로 셈합니다. 네와 아니오는 말 전체가 대답일 때만 받습니다. 네, 예, 응, 그래, 좋아요, 맞아요, 그렇게 해 줘, 가자, 안내해 줘, 걸어 줘, 전화해 줘, 부탁해, 오케이가 네이고, 아니, 아니요, 아뇨, 됐어, 안 해, 싫어, 그만, 하지 마가 아니오입니다. 응급실, 예술의전당, 길 안내해 줘처럼 앞 글자만 같은 말은 네로 듣지 않습니다. 여쭌 물음이 여럿이면 마지막에 여쭌 물음에만 대답으로 씁니다. 여쭌 지 2분이 지나면 잊습니다. 점지도로 걸을까요는 1분입니다. 됐어, 그만이라고 하시면 여쭈던 목적지 바꾸기와 콜 걸기도 거둡니다. 가시는 중에 목적지를 바꾸실 건가요에 아니라고 하시면 지금 목적지로 계속 안내하고, 다른 곳이라고 하시면 다음 후보를 말씀드립니다. 그럼 가자, 어 가자처럼 가자 앞에 곳 이름이 없으면 찾지 않고 가실 곳의 이름을 여쭙니다. 그럼, 응 같은 한 마디에는 한 번만 대답하고, 20초 안에 또 들리면 조용히 듣기를 그칩니다. 차에 탔어, 택시 탔어는 지하철 길이 있어도 늘 차 안 안내로 받고, 지하철 역 알림은 지하철 탔어, 열차 탔어, 전철 탔어라고 하실 때만 시작합니다. 땅속 승강장에 서서 지하철로 가자고 하시면 곧장 역 알림을 시작하지 않고, 열차가 움직이면 저절로 시작합니다. 여정이 끝난 뒤에 택시 탔어, 걸어가자라고 하시면 옛 목적지로 가지 않고 어디로 가실지 여쭙니다. 콜 취소, 콜 취소했어, 택시 취소라고 하시면 콜을 취소하신 것으로 남깁니다. 전화가 오거나 통화하시는 동안에는 말로 하기 단추를 누르셔도, 하이 길눈이라고 부르셔도 마이크를 열지 않고, 묻던 말과 알아보던 명령도 거둡니다. 타고 가는 중이나 지하철 안내 중에는 여정 다른 할 일을 펼치면 택시·차입니다, 버스입니다, 지하철입니다, 기차입니다 단추로 탈것을 바로잡을 수 있습니다. 지하철 안내 중이면 밖으로 나왔습니다 단추도 있습니다. 지하철로 바로잡으시면 지하철 길을 찾아 지나는 역을 알려 드리고, 버스로 바로잡으시면 버스 안 안내로 바뀝니다. 승강기로 빠르게 내려간 것은 땅속으로 보지 않고, 계단이나 에스컬레이터로 내려가실 때 땅속으로 봅니다. 콜 차로 알고 있던 것은 땅속에서 열차가 실제로 움직이거나, 지하철·버스·기차로 바로잡으시거나, 내렸습니다·여정 끝내기·하던 일 멈추기·콜 취소를 하실 때 풉니다. 지하철로 바로잡으신 안내는 땅 위 다리 구간에서도 차로 바뀌지 않습니다. 밖으로 나왔어, 나왔어처럼 말 전체로 하시면 내리신 것으로 받고(소리가 안 나왔어처럼 안이나 못이 붙으면 받지 않음), 됐어는 아니오로 받으며, 차 부르기의 묵은 물음은 마지막에 여쭌 물음이 아니면 대답으로 쓰지 않습니다. 점지도의 탈것 구간을 지나는 중에는 점지도 안내를 끊지 않고, 점지도를 걷다 차를 타시면 확실할 때 차 안 안내로 바꾸고 아니면 택시야라고 말씀해 주십사 한 번 여쭙니다. 배차됐어는 대화 끝이 아니라 배차 대답으로 받습니다. 기차나 고속버스에서는 길에서 벗어났다는 말과 세워 달라는 말을 하지 않습니다. 기차로 빠르게 달린 뒤 10분 동안은 터널에서도 지하철로 잘못 알지 않습니다. 시속 120킬로미터가 넘거나 기차·고속버스로 알고 있을 때는 길에서 벗어났다는 말을 하지 않습니다.",   // 2.31.0 (261009-A19, 이사장님 승인), 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 엄격한 네·아니오, 그럼 가자, 차에 탔어·지하철 탔어, 콜 취소, 전화 중 마이크, 탈것 바로잡기 단추를 더함
            "전화가 오거나 걸 때 — 모든 소리 멈춤, 방송 이어 듣기" to "전화 벨이 울리거나, 전화를 거시거나, 통화하시는 동안, 그리고 카카오톡 보이스톡 같은 다른 앱의 인터넷 통화 동안에는 길눈이 내는 모든 소리를 멈춥니다. 음악, 라디오, TV, 기사 읽기, 동영상, 길눈 안내 말, 알림 소리, 자원봉사자 목소리 토막이 모두 멈추고, 하이 길눈도 듣지 않습니다. 길 안내는 멈추지 않고 조용히 이어지며, 걷는 중 위험 경고만은 말 대신 길게 진동으로 알려 드립니다. 통화가 끝나면 방송은 저절로 다시 틀지 않습니다. 길 찾기 첫 화면과 음악·방송 첫 화면 맨 위에 방송 이어 듣기 단추가 나오니 한 번 누르시면 통화 전에 듣던 것이 이어집니다. 통화가 끝나면 저절로 이어 듣고 싶으시면 음악·방송 첫 화면 맨 아래의 통화 뒤 방송 저절로 이어 듣기를 켜 주십시오. 처음에는 꺼져 있습니다.",
            "방송 들으며 길눈 쓰기" to "길눈에서 튼 음악, 라디오, TV는 화면을 옮겨도, 폰을 잠가도, 길 찾기 탭에서 안내를 받으셔도 끊기지 않습니다. 틀고 있는 동안 알림 칸에 길눈 방송이 떠 있고, 거기서도 멈춤과 다음, 이전을 쓰실 수 있습니다. 기사 읽기는 기사 화면을 떠나시면 멈춥니다. 기사 목록으로, 뒤로, 다른 탭으로 가시면 떠나시는 것이고, 폰을 잠그시는 것은 떠나시는 것이 아니어서 계속 읽습니다. 길눈 안내 말이 나올 때는 방송 소리를 작게 줄였다가 말이 끝나면 되돌립니다. 경고만은 방송을 멈추고 바로 말한 뒤 다시 틉니다. 기사 읽기는 안내 말 동안 쉬었다가 이어 읽습니다. 말로 하기가 명령을 듣는 동안과 긴급통화 동안에는 방송을 잠시 멈춥니다. 이어폰 단추는 길눈 방송이 나오는 동안 방송을 다룹니다. 재생 단추를 짧게 누르시면 멈춤과 다시 틀기, 다음 단추는 다음 곡이나 다음 채널, 이전 단추는 이전 곡이나 앞 채널입니다. 기사 읽기는 알림 칸과 이어폰 단추로 다루지 않고 기사 화면의 읽기 멈춤, 다음 기사, 처음부터 다시 읽기로 다룹니다. 재생 단추를 길게 누르시면 지금처럼 말로 하기가 열립니다. 이어폰을 빼시면 소리가 새지 않게 멈추고, 다른 앱이 소리를 가져가도 멈춥니다. 다시 틀기를 누르시면 이어집니다. 어느 탭에서나 음악·방송 탭 첫 화면 맨 위의 그만 듣기로 끄실 수 있습니다.",
            "동영상 틀기 — 카톡이나 문자로 받은 동영상을 폰에서" to "카톡, 문자, 갤러리에서 동영상을 열고 공유 단추를 누른 뒤 앱 목록에서 길눈을 고르십시오. 목록에 길눈이 없으면 목록 끝의 더 보기에서 찾으실 수 있습니다. 길눈이 꺼져 있어도 저절로 켜져 받습니다. 받은 동영상은 온 화면으로 틉니다. 맨 위에 뒤로 — 동영상 닫기, 아래에 재생과 멈춤, 10초 뒤로, 10초 앞으로가 있습니다. 폰을 잠그시면 소리는 이어지고, 홈이나 다른 앱으로 가시면 멈춥니다. 동영상 주소가 왔을 때는 주소를 길게 눌러 복사한 뒤 음악·방송 탭의 동영상 틀기에서 복사한 동영상 주소 틀기를 누르십시오. 마지막으로 받은 동영상은 같은 화면의 받은 동영상 다시 틀기로 다시 트실 수 있습니다. 유튜브 주소는 유튜브 약관에 따라 유튜브 앱으로 열어 드립니다. 갤럭시 워치가 이어져 있으면 동영상 틀기 화면의 갤럭시 워치가 있으면 워치에서 틀기를 켜 두셨을 때(처음부터 켜짐) 워치로 보내 워치에서 틉니다. 워치 길눈 2.7.0판 이상이 있어야 하고, 폰 길눈을 켠 뒤 워치 길눈을 한 번 여셔야 워치를 알아봅니다. 큰 동영상은 몇십 초 걸립니다. 워치로 보내지 못하면 폰에서 틉니다. 폰에서 트시려면 그 단추를 끄십시오. 길눈을 고르지 않으시면 폰이 원래대로 처리합니다.",
            "나눔 마당 — 쓰지 않는 물건 주고받기" to "나눔 탭의 나눔 마당에서 드립니다와 찾습니다를 다섯 건씩 보실 수 있습니다. 이름을 두드리시면 자세히 읽어 드리고, 전화 걸기와 다 나눴습니다 — 이 글 내리기는 이름을 길게 누르시거나 톡백 동작 메뉴에서 고르십니다. 글 올리기에서 드립니다나 찾습니다를 고르고 무엇인지, 한마디, 어느 지역, 이름 또는 별명, 연락 받으실 방법을 적어 올리십시오. 연락 방법은 마당에 그대로 드러나니 남에게 알려도 괜찮은 번호만 적으십시오. 글은 여든 날이 지나면 저절로 내려가고, 물건값을 주고받는 곳이 아닙니다.",
            "걸음 나눔과 게시판, 함께하기" to "나눔 탭의 걸음 나눔과 게시판에서 걸음 나눔을 여시면 적힌 글이 다섯 개씩 나옵니다. 더 보기와 이전 보기로 넘기십니다. 글을 두드리시면 글과 댓글을 읽고, 댓글을 달거나 네 자리 숫자로 그 글을 가리실 수 있습니다. 한마디 적기 펼치기에서 별명, 하실 말씀, 지울 때 쓸 네 자리 숫자를 적어 올리십시오. 별명은 폰이 기억해 다음에 다시 적지 않으셔도 됩니다. 함께하기에는 자원봉사 요령, 시각장애인에게 말하는 법, 봉사시간, 관련 제도와 법, 후원과 광고 원칙이 마당별로 담겨 있습니다. 마당 제목을 두드리시면 펼쳐집니다.",
            "길 부탁하기" to "나눔 탭의 길 부탁하기에서 새로 부탁하기를 누르십시오. 어디서에 이름을 적고 엔터를 치시면 비슷한 곳이 다섯 곳씩 나오고, 고르시면 출발지가 됩니다. 내 곁 건물·가게 이름 찾기를 누르시면 지금 자리 곁의 이름 있는 곳이 나오니 골라 출발지로 삼으셔도 됩니다. 출발지와 도착지는 찾아서 고른 건물이나 가게라야 올라갑니다. 자봉이 정확히 어디서 어디까지 걸을지 알아야 하기 때문입니다. 어디까지도 같은 방법으로 고르시고, 언제쯤, 한마디, 별명을 적어 부탁 올리기를 누르십시오. 부탁하신 길은 자원봉사자의 오늘 걸을 길 맨 앞에 놓입니다. 부탁해 둔 길 보기에서 길을 두드리시면 자세히 듣고, 댓글을 달거나, 다 그렸습니다 표시를 하거나, 부탁을 내리실 수 있습니다. 댓글을 지우실 때는 댓글을 길게 누르십시오. 다 그려진 길은 그려졌습니다 — 이 길로 걷기를 누르시면 점지도 따라 걷기가 시작됩니다.",
            "알림 — 길눈을 만드는 곳에서 드리는 공지" to "설정 탭 맨 위의 알림에서 공지 목록을 다섯 건씩 보실 수 있습니다. 새 알림에는 새 알림이라고 붙습니다. 긴급 공지가 오면 길 찾기 첫 화면 맨 위에 긴급 공지 한 줄이 뜨고 읽어 드리며, 두드리시면 전문을 읽어 드리고 내립니다. 길눈은 켜져 있는 동안 15분마다, 그리고 길눈으로 돌아오실 때마다 새 공지를 살핍니다. 알림 화면의 긴급 공지를 폰 알림으로 받기를 누르고 허락하시면 폰 알림으로도 알려 드립니다.",
            "말소리를 꺼 두었을 때 긴급 공지" to "길눈 말소리를 꺼 두셨어도 긴급 공지는 톡백으로 읽어 드립니다. 읽은 공지는 다시 뜨지 않습니다.",
            "앱을 닫아 두어도 긴급 공지 받기" to "길눈이 켜져 있지 않을 때도 안드로이드가 틈틈이 길눈을 깨워 새 긴급 공지가 있는지 살피고, 있으면 폰 알림으로 알려 드립니다. 언제 깨울지는 안드로이드가 정하므로 15분에서 몇 시간 늦을 수 있습니다. 설정 탭의 알림 화면에서 폰 알림을 허락해 두셔야 하며, 폰 설정에서 길눈을 강제 중지하시거나 배터리 절약으로 막아 두시면 깨우지 못합니다.",
            "현 위치정보 말할 내용" to "설정 탭의 폰 펼치기 안에 있습니다. 지금 내 자리 듣기에서 도로명주소, 가까운 곳 이름, 지번주소, 국가지점번호, 위성 오차 가운데 무엇을 말할지 켜고 끄십니다. 처음에는 지번주소만 꺼져 있습니다. 지금 설정으로 내 자리 듣기로 곧바로 들어 보실 수 있습니다.",
            "여기서 점검" to "설정 탭의 여기서 점검에서 점검 시작을 누르시면 지금 이 자리에서 인터넷, 위치, 지금 자리 이름, 목적지 찾기, 가까운 점지도, 지하철 역 찾기, 둘레 찾기, 가 볼 곳, 말로 하기 허락, 말소리, 폰 알림을 하나씩 살펴 읽어 드립니다. 끝나면 걸린 곳만 모아 말씀드립니다. 점검 결과 클에게 보내기를 누르시면 결과가 나스에 남아 클이 그대로 읽습니다.",
            "탈것 저절로 알아채기와 지하철 역 알림" to "길눈은 폰의 걸음 센서와 흔들림, 기압계로 걷는지, 차를 탔는지, 버스인지, 지하철인지를 저절로 알아챕니다. 땅속에서도 됩니다. 걸음이 15초 이어지면 걷는 것으로, 걸음 없이 20초 넘게 움직이면 탈것으로 봅니다. 탈것이 땅속이거나 역 근처에서 탔으면 지하철, 정류장마다 서면 버스, 아니면 차입니다. 지하철로 가실 때는 먼저 가까운 역 출구까지 걷는 안내를 하고, 계단이나 에스컬레이터로 내려가시면 역에 들어오신 것을 알아채 타실 열차를 알려 드립니다. 열차가 움직이면 손대지 않으셔도 역 알림이 시작되고, 지나는 역마다 이름과 남은 정거장을 알려 드립니다. 이미 열차를 타고 계실 때는 지하철로 가자, 또는 전철로 가고 있어라고 말씀하시면 땅속으로 내려가시기 전 자리에서 가까운 역을 타신 역으로 잡아 곧장 역 알림을 시작합니다. 내리신 뒤 땅 위로 나와 걸으시면 지하철 안내를 마치고 남은 길을 걸어서 안내합니다. 몇 정거장 남았어라고 물으시면 지금 자리를 알려 드립니다.",
            "지역에 맞는 복지콜·교통약자 콜 요청" to "길눈은 지금 계신 곳의 시·도와 시·군을 알아내 그 지역 교통약자 이동지원센터 번호를 먼저 알려 드립니다. 시·군 센터가 따로 없으면 도 광역센터 번호가 나옵니다. 말로 하실 때는 복지콜에 전화해 줘, 또는 장애인콜택시 불러 줘라고 하시면 어느 지역에서든 그 지역 센터로 전화 화면을 엽니다. 두리발, 나드리콜, 새빛콜, 반디콜처럼 그 지역 콜 이름에 콜이나 전화를 붙여 말씀하셔도 됩니다. 콜 번호 알려 줘라고 하시면 지금 계신 지역 이름과 번호를 읽어 드립니다. 화면에서는 설정의 내 서류 보관함, 복지콜·교통약자 콜 번호 펼치기에서 맨 위에 지금 계신 지역이 나오고 그 아래 번호마다 전화 걸기 단추가 있습니다. 처음 쓰시는 지역이면 이용 등록을 먼저 하라고 할 수 있으니, 그때는 같은 화면에 담아 둔 서류를 보내십시오. 위치를 아직 잡지 못했으면 그렇다고 알려 드리니 잠시 뒤 다시 해 주십시오.",   // 2.88.0 / 2.58.0 (261016-I42 / 261016-A46, 이사장님 승인 2026-10-16) 제목 부르기 → 요청
            "내 서류 보관함과 복지콜 등록" to "설정 탭의 내 서류 보관함에서 복지카드 앞면과 뒷면, 신분증, 장애정도 결정 통지서, 그 밖의 서류를 사진으로 담아 두십니다. 서류 담기 펼치기에서 어떤 서류인지 고르고 사진 찍기나 이미 찍어 둔 사진에서 고르기를 누르십시오. 적어 두기 펼치기에는 이름, 생년월일, 연락처, 주소를 적어 둡니다. 모두 이 폰 안에만 있고 서버로 가지 않으며 폰 백업에도 넣지 않습니다. 콜에 가입할 때 담아 둔 서류의 보내기를 누르시면 문자나 메일로 보내는 창이 열리고, 받는 곳은 직접 고르십니다. 서류를 빼실 때는 서류 이름을 길게 누르시거나 톡백 동작 메뉴에서 이 서류 빼기를 고르십시오. 지금 계신 지역의 복지콜과 교통약자 콜 번호도 이 화면의 펼치기 안에서 바로 거실 수 있습니다.",
            "말하기 설정 — 목소리" to "설정 탭의 말하기 설정에서 목소리를 누르실 때마다 폰에 있는 다음 한국어 목소리로 넘어갑니다. 더 좋은 목소리는 폰 설정의 텍스트 음성 변환 출력에서 한국어 음성 데이터를 내려받으시면 나타납니다. 말로 하기에서 목소리 바꿔라고 하셔도 됩니다. 경고는 안전을 위해 말소리를 꺼도 늘 말씀드립니다.",
            "말하기 설정 — 얼마나 자세히, 무엇을 말할지" to "설정 탭의 말하기 설정에서 고르십니다. 얼마나 자세히는 자세히, 보통, 짧게가 있고, 보통과 짧게에서는 남은 거리를 덜 자주 말씀드립니다. 꺾이는 곳 알리기를 켜 두시면 사거리와 갈림길을 알려 드리고, 몇 초 앞에서 알릴지는 5초, 8초, 12초 가운데 고르십니다. 되풀이 사이 시간은 한 번 말한 뒤 다시 말하기까지 쉬는 시간입니다. 지나는 곳 안내를 켜 두시면 차 안에서 지금 달리는 길을 알려 드리고, 말하는 간격은 30초, 60초, 120초 가운데 고르십니다. 걸을 때 확신음을 끄시면 짧은 맑은 소리가 나지 않고, 방향이 틀어졌을 때의 말은 그대로 나옵니다. 지금 설정으로 들어 보기로 들어 보실 수 있습니다. 경고는 어떤 설정에서도 말씀드립니다.",
            "길눈 목소리 — 마이크로소프트 선희" to "나스 음악 열쇠가 있는 폰에서는 길눈이 마이크로소프트의 선희 목소리로 말합니다. 웹 길눈에서 쓰시던 그 목소리입니다. 설정 탭의 말하기 설정에서 길눈 목소리를 끄시면 폰 목소리로 돌아갑니다. 빠르기는 설정 탭 말하기 설정의 빠르기로 함께 조절됩니다. 한 번 들은 말은 폰에 모아 두어 다음부터는 곧바로 나옵니다. 처음 듣는 말은 나스에서 받아 오느라 1초 남짓 걸리고, 2초 넘게 받지 못하거나 통신이 끊기면 같은 말을 폰 목소리로 곧바로 대신합니다. 경고처럼 한순간이 급한 말은 기다리지 않고 늘 폰 목소리로 곧바로 말합니다. 정식 음성 열쇠를 넣으면 모든 분의 폰에서 이 목소리가 나오게 됩니다. 길눈이 자주 하는 말은 앱을 켤 때 미리 받아 폰에 담아 두어, 기다리지 않고 협회 목소리로 나갑니다. 처음 하는 말을 제때 받지 못하면 그 자리는 폰 목소리로 말하되, 뒤에서 이어 받아 담아 두어 다음부터는 협회 목소리로 나갑니다.",
            // 2.88.0 / 2.58.0 (261016-I42 / 261016-A46, 이사장님 승인 2026-10-16) 현장영상해설 받기 화면은 둘러보기에서 감춤 — 말로 하면 긴급통화서비스로 요청
            "현장영상해설 받고 싶을 때" to "말로 하기에서 현장영상해설 받고 싶어라고 하시면, 해설사 요청해 줘라고 하실 때와 똑같이 긴급통화서비스로 협회 현장영상해설사에게 화상통화를 요청하고 길 찾기 탭의 긴급통화서비스 화면을 엽니다. 현장영상해설사가 폰 카메라로 보며 현장영상해설해 드립니다.",
            // 2.32.0 (261009-A20, 이사장님 승인 2026-10-09)
            "긴급통화 — 거절과 같은 폰" to "가족이나 지인 한 분께 화상통화를 요청했는데 그분이 거절하시면 기다리지 않고 곧바로 끝내며, 그분 이름과 함께 지금 받기 어렵다고 하셨다고 알려 드리고, 자원봉사자와 현장영상해설사에게 요청할까요라고 여쭙니다. 네라고 하시면 이어서 요청합니다. 여러 분께 함께 요청했으면 모두 거절하셨을 때 알려 드립니다. 길눈과 자봉을 한 폰에 함께 깔아 두셨으면, 내가 보낸 긴급통화 요청이 그 폰의 자봉으로는 울리지 않습니다. 가족·지인이 받지 않으시거나 지금 닿지 않으면 자원봉사자와 현장영상해설사에게 요청할까요라고 여쭙니다. 단추를 누르셔도 되고 네나 아니요로 말씀하셔도 됩니다. 가족·지인 폰은 40초까지 울립니다. 받는 분이 자봉 앱으로 받기를 켜 두지 않으셨거나 같은 폰의 자봉 앱이면 40초를 기다리지 않고 10초 뒤에 여쭙니다. 통화를 마치시면 받은 분 화면도 곧바로 닫힙니다.",
            "점지도 — 탈것 구간과 에스컬레이터" to "점지도 안에 지하철이나 버스를 타는 구간이 있으면, 타는 곳에서 한 번 알려 드리고 타고 가시는 동안에는 걸음 안내를 쉽니다. 내리실 곳 30미터 안에 닿거나, 내렸어라고 말씀하시거나, 따라 걷기 다른 할 일 펼치기의 탈것에서 내렸습니다를 누르시면 그 자리부터 다시 걸음으로 안내합니다. 되돌아가기로 거꾸로 걸으실 때는 올라가는 에스컬레이터를 내려가는 에스컬레이터로, 내려가는 것을 올라가는 것으로 바꿔 알려 드립니다."
        )
    }
}
