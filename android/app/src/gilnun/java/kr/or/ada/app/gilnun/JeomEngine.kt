// 안드로이드 길눈 — 점지도 따라 걷기 엔진(2.2.0, 빌드 261002-A4, 대표님 지시: 점지도 따라 걷기를 안드로이드에도)
// 아이폰 길눈 JeomEngine.swift(2.10.0~2.37.1)의 바탕을 같은 잣대·같은 시간·같은 말로 옮겼습니다.
// ★이사장님 말씀 — 점지도는 직방이어야 한다. 한 걸음만 떨어져도 바로 알려야 점지도다. 따라 걸을 때 입을 다물면 안 된다.
//   ① 점지도 위를 제대로 디디면 걸음마다 맑은 확신음
//   ② 반 걸음 비켜나면 가운데 소리와 "왼쪽으로 조금 비켜나십니다" 한 번
//   ③ 한 걸음 벗어나면 곧바로 경고음과 "왼쪽으로 한 걸음 벗어났습니다. 1시 방향으로 한 걸음 옮기십시오" — 경고음 2.5초, 말 5초마다
//   ④ 돌아오시면 돌아옴 소리와 "점지도 위로 돌아오셨습니다"
//   ⑤ 10미터마다 "제대로 가고 있습니다"
//   ⑥ 제자리·위성 기다림은 5초에 한 번 알림
//   꺾이는 곳은 서른 걸음 앞·열한 걸음 앞·코앞에서, 표시는 서른 걸음 앞·일곱 미터 앞·세 미터 앞에서
//   위성이 6초 넘게 끊기거나 흐리면 걸음 수 × 보폭만큼 점지도 위를 나아간 것으로 셈(점지도의 바탕은 걸음)
//   안전 경고는 끌 수 없음 — 걷기 시작 한마디, 확인 중인 길, 안내 끊김(10초)
// 한 걸음 어긋남 재기 — 위성은 몇 미터씩 흔들리므로, 몸 센서로 발 디딤을 잡고(아이폰 2.32.0과 같음)
// 자이로 합성 방향으로 디딘 방향을 읽어 점지도 구간 방향과 견주어 옆으로 비켜난 거리를 걸음마다 쌓아 셉니다.
// 2.7.0(묶음 b2 점지도 마저, 대표님 지시) 아이폰 2.11.0~2.38.0과 같이
//   여러 점지도 이어 걷기(ieumGeotgi — 한 구간을 마치면 손대지 않고 다음 구간), 문까지 이어 안내(mun.php, 내 문 먼저),
//   지나는 곳 안내(juwi2.php, 25미터 둘레, 뒤쪽은 말하지 않음), 함께 시험하기(hamkke.php), 길목(ppyeodae.php)·가까운 정류장(beoseu.php),
//   여기 걸렸어요, 앱이 꺼졌다 켜져도 이어 걷기(3시간 안), 걸어가기에서 점지도로 걸을지 여쭘(georeoGagiBoda — 20초 뒤 점지도로),
//   꺾는 곳에서 폰·워치 방향 진동과 도착 진동(Jindong), 확신음 끄기·"제대로 가고 있습니다" 간격(JeomSeol)
//   여정·위성 걷기·사거리 알림은 다른 묶음(b1)의 몫 — 아래 "다른 묶음과 잇는 자리"로 이어 붙임
//   카메라 문 찾기는 묶음 3의 몫 — JeomMunKamera 로 이어 붙임
// 2.5.0(빌드 261002-A8, 대표님 지시) 갤럭시 워치 — 아이폰 2.35.0·2.36.0과 같이
//   따라 걷기 시작·그만을 워치에 알림(워치가 깨어 있기와 팔 흔들림 걸음 세기를 켜고 끔), 워치가 센 걸음으로 폰 걸음이 끊긴 때를 메움(watchGeoreum),
//   손목 가리키기에 가야 할 쪽을 보냄(garikiAllim, 5도·10초), 가리키기 방향 맞추기에 몸 방향(momBang),
//   지금 도십시오에 맞춰 워치 방향 진동(오른쪽 길게 한 번, 왼쪽 짧게 두 번), 도착하면 워치 도착 진동
package kr.or.ada.app.gilnun

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object JeomEngine {
    private val main = Handler(Looper.getMainLooper())
    private var ctx: Context? = null
    private var deutgiDoem = false

    /** 따라 걷는 길(되돌아가기면 거꾸로 된 길). 없으면 null */
    var gil: JeomGil? = null
        private set
    /** 되돌아가는 중 */
    var dwit = false
        private set
    /** 목적지에 닿음 */
    var dochakHam = false
        private set
    /** 지금 상태 한 줄(화면에 늘 띄우지는 않음 — 낭독기가 쉬지 않고 떠들지 않게) */
    var sangMal = ""
        private set
    /** 길을 불러오는 중 */
    var bulleoneun = false
        private set
    /** 점지도를 따라 걷는 중(도착 전) */
    val georeoJung: Boolean get() = gil != null && !dochakHam
    /** 시작·도착·그만·불러오기 실패처럼 화면이 바뀌어야 할 때 한 번 부름(화면이 채움) */
    var byeonhwa: (() -> Unit)? = null

    // 2.7.0 다른 묶음과 잇는 자리(채우지 않으면 하지 않음)
    /** 여정 — 이 길의 끝(또는 걸어가실 곳)을 목적지로, 걸어서 가는 중으로(아이폰 YeojeongEngine.jeonghagi·talgeotJeonghagi·danggyeBakkugi) */
    var yeojeongJeonghagi: ((JeomMokjeok) -> Unit)? = null
    /** 여정 — 도착(아이폰 danggyeBakkugi(.dochak)) */
    var yeojeongDochak: (() -> Unit)? = null
    /** 여정 — 그만 걷기로 여정을 끝냄(아이폰 YeojeongEngine.kkeut) */
    var yeojeongKkeut: (() -> Unit)? = null
    /** 지금 여정의 목적지(앱이 다시 켜질 때 옛 길이 새 목적지를 덮어쓰지 않게 견줌) */
    var yeojeongMok: (() -> JeomMokjeok?)? = null
    /** 위성으로 걷기(아이폰 AnnaeEngine.georeoGagi) — 점지도가 없거나 위성을 고르셨을 때 */
    var wiseongGeotgi: ((JeomMokjeok?) -> Unit)? = null
    /** 사거리·갈림길 알림(아이폰 AnnaeEngine.neagoriBakkeseo) — 문까지 가는 동안은 쉼 */
    var neagoriBakkeseo: ((Jari) -> Unit)? = null

    // 2.7.0 여쭘(걸어가기에서 점지도로 걸을까요)
    var muleum: JeomYeojjum? = null
        private set
    private val muleumJakeop = Runnable {
        if (muleum != null) { Sori.mal("고르지 않으셔서 점지도로 걷습니다."); muleumDap(true) }
    }
    // 2.7.0 여러 점지도 이어 걷기
    var ieum: List<JeomGugan> = emptyList()
        private set
    var ieumIdx = 0
        private set
    private var ieumMok: JeomMokjeok? = null
    /** 이어진 길의 가운데 구간을 걷는 중(끝이 목적지가 아님) */
    val jungganGugan: Boolean get() = ieum.isNotEmpty() && ieumIdx < ieum.size - 1
    // 2.7.0 문까지 이어 안내(내 문 먼저)
    private class JeomMun(val lat: Double, val lon: Double, val ireum: String, val saengMal: String, val bang: Double?,
                          val jarye: Int, val d: Double, val geul: List<String> = emptyList())
    var munOn = false
        private set
    var munSu = 0
        private set
    private var munKamera = false
    private var mun: JeomMun? = null
    private var munList: List<JeomMun> = emptyList()
    private var munIdx = 0
    private var munDasi = false
    private var munT = 0L
    private var munGakkaum: Double? = null
    private var munBeon = 0
    private var munSijakT = 0L
    // 2.7.0 지나는 곳 안내(웹 juwi.js)
    private class JuwiJul(val ireum: String, val jong: String, val lat: Double, val lon: Double, val wi: Boolean)
    private var juwiRows: List<JuwiJul> = emptyList()
    private val juwiHan = HashSet<String>()
    private var juwiEonje = 0L
    private var juwiJari: Pair<Double, Double>? = null
    private var juwiCenter: Pair<Double, Double>? = null
    private var juwiBan = 0.0
    private var juwiBadneun = false
    private var juwiBatT = 0L
    // 2.7.0 함께 시험하기
    var hamkkeBunho: String? = null
        private set
    private var hamkkeT = 0L

    // 길
    private var pts: List<JeomJeom> = emptyList()
    private val nu = ArrayList<Double>()
    private class Pyo(val p: JeomPyo, val i: Int) { var said30 = false; var said = false; var near = false }
    private var pyo: List<Pyo> = emptyList()
    private class Kkeok(val i: Int, val d: Double, val lat: Double, val lon: Double)
    private var kkeoks: List<Kkeok> = emptyList()
    private val kkeokHan = HashSet<String>()
    private var gilRaw = ""
    private var wonGil: JeomGil? = null      // 되돌아가기 때 다시 받지 않고 쓰는 원래 길(통신이 끊긴 곳에서도 되돌아가게)
    private var sijakT = 0L

    // 자리
    private var idx = 0
    private var firstFix = true
    private var me: Pair<Double, Double>? = null
    private var acc = 0.0
    private var spd = 0.0
    private var lastLa = 0.0
    private var lastLo = 0.0
    private var lastT = 0L                    // 0 이면 아직 없음
    private var jariTtae = 0L                 // 마지막으로 자리를 받은 때(위성이든 걸음이든)
    private var wiseongTtae = 0L              // 마지막으로 또렷한 위성을 받은 때
    private var geoMode = false               // 걸음으로 이어 가는 중
    private var geoS = 0.0
    private var geoSu = 0
    private var jeop30 = false
    private var jeop20 = false
    private var jeopT = 0L
    private var cheotBang = false
    private class DolgiMok(val k: Int?, var bang: Double?, val t: Long)
    private var dolgiMok: DolgiMok? = null
    private var geonneolOn = false
    private var geonneolI = -1                // 건널목 끝 표시가 없는 점지도 — 건널목 시작 점(-1 이면 없음)
    private var geonneolLen = 0.0
    private var offSu = 0
    private var beoseoSu = 0
    private var makhimT = 0L
    private var geollimJari: Pair<Double, Double>? = null
    private val geollimHan = HashSet<String>()
    private var geollimT = 0L
    private var malT = 0L
    private var sigyeDolgo = false
    private var tikT = 0L
    private var kkeunMalT = 0L
    private var sedae = 0                     // 그만두면 늦게 돌아온 일(불러오기 등)을 버리려는 세대 번호
    private var geoAcc0 = 5.0
    private var geoS0 = 0.0
    private var dwiNeolge = false
    // 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 탈것 구간 — 점지도 안의 「지하철 탐 … 지하철 내림」, 「버스 탐 … 버스 내림」(또는 탈것 점)을
    //   걸음으로 안내하지 않음("직진 3킬로미터" 같은 헛말을 막음). 타는 곳에서 한 번 알리고, 내리실 곳 30미터 안이나 「내렸습니다」에서 다시 걸음 안내(아이폰과 같음)
    private class Tagi(val i0: Int, val i1: Int, val kind: String, val naerimMal: String, val lat: Double, val lon: Double)
    private var tagiGugan: List<Tagi> = emptyList()
    private val tagiHan = HashSet<Int>()
    private var tagiJung: Tagi? = null
    /** 탈것 구간을 지나는 중(걸음 안내를 쉬는 중) */
    val tagoGaneunJung: Boolean get() = tagiJung != null
    /** 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 이번 점지도 걷기에서 "차를 타셨으면 택시야라고 말씀해 주십시오."를 했는가(걷기마다 한 번, 아이폰과 같음) */
    var chaMalHam = false
    /** 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 걷는 빠르기보다 빠르게(시속 15킬로미터 넘게) 움직이기 시작한 때와 마지막으로 그렇게 잡힌 때(아이폰과 같음) */
    private var bareunSijak = 0L
    private var bareunMajimak = 0L
    /** 시속 15킬로미터 넘게 10초 넘게 움직이는 중 — 점지도 벗어남 경고를 쉼(차 안에서 5초마다 되풀이하지 않게) */
    private val chaBareum: Boolean
        get() {
            val now = System.currentTimeMillis()
            return bareunSijak != 0L && now - bareunMajimak < 10_000 && now - bareunSijak >= 10_000
        }
    private var ponGeoreumT = 0L              // 2.5.0 폰이 마지막으로 걸음을 센 때(워치 걸음으로 메울지 가림)
    private var watchN0: Int? = null          // 2.5.0 워치가 보낸 걸음 누계(지난번)
    private var garikiBonaen: Double? = null  // 2.5.0 손목 가리키기에 마지막으로 보낸 쪽
    private var garikiT = 0L

    // 확신음(웹 hwaksin.js 의 S)
    private class HS {
        var gidarim = false
        var gidarimSu = 0
        var dolgiKkaji = 0L
        var gyeol = ""                 // "", baro, heundeul, beoseo
        var yeop = 0.0                 // 오른쪽이 +
        var segBang: Double? = null
        var segKijun: Double? = null
        var jin: Double? = null
        var jinSijak: Double? = null
        var rest: Double? = null
        var stepT = 0L
        var stepSu = 0
        var offSu = 0
        var offT = 0L
        var offMalT = 0L
        var sinho = System.currentTimeMillis()
        var wdMal = 0L
        var hwakMalT = 0L
        var gamyeon = false
        var kijun: Double? = null
        var malKijun: Double? = null
        var jumeoni = false
        var jumeoniMal = false
        var dallaSu = 0
        var majaSu = 0
        var heundeulSu = 0
        var dolrim = 0.0
        val gyeolgwa = ArrayList<Pair<Double, Double?>>()   // (나아갈 쪽과 디딘 쪽의 차이, 그때 나아간 거리)
        var gpsNeomSu = 0
        var pokgiMal = false
        var sijakT = System.currentTimeMillis()
    }
    private var S = HS()

    // 아이폰 2.32.0과 같이 걸음 감지는 몸 센서(자봉이 점지도를 그릴 때와 같은 센서·같은 셈법). 자이로가 없는 폰만 옛 잣대로
    // 옛 잣대(웹과 같음 — 중력을 뺀 흔들림이 1.3을 넘으면 한 걸음, 0.3초 안에는 다시 세지 않음)
    private var momNaega = false        // 몸 센서를 따라 걷기가 켰는가(그만둘 때 끄려고)
    private var momSseum = false        // 걸음과 방향을 몸 센서로 받는 중
    private var apGeoreum: (() -> Unit)? = null   // 따라 걷기 전에 몸 센서 걸음을 받던 곳(위치 엔진) — 함께 부르고, 그만두면 되돌림
    private val naeGeoreum: () -> Unit = {
        apGeoreum?.invoke()
        ponGeoreumT = System.currentTimeMillis()
        if (S.gidarim) { S.gidarimSu += 1; if (S.gidarimSu >= 3) S.gidarim = false }
        georeum()
    }
    private var dolgiDg0: Double? = null       // 도시라고 말씀드린 때 몸이 돈 각도의 누계
    private var dolgiWant: Double? = null      // 돌아야 할 쪽과 크기(오른쪽 +)
    private var bandaeMal = false
    private var gasokSm: SensorManager? = null
    private var moG = 9.8
    private var moWi = false
    private var moT = 0L

    private const val RAD = PI / 180
    /** "제대로 가고 있습니다" 간격(미터) — 2.7.0 설정(JeomSeol.hwaksinGan 5·10·20, 처음 10) */
    private val HWAKSIN_GAN: Double get() = JeomSeol.hwaksinGan.toDouble()
    private val bocok: Double get() { val b = Seoljeong.bopok; return if (b > 0.3 && b < 1.2) b else 0.7 }

    private fun junbi(c: Context) {
        ctx = c.applicationContext
        JeomSeol.sijak(c)
        NaeGil.sijak(c)
        Jindong.sijak(c)
        if (deutgiDoem) return
        deutgiDoem = true
        Wichi.deutgi { w -> wichiBatda(w) }
    }

    private val sigye = object : Runnable {
        override fun run() {
            if (!sigyeDolgo) return
            jikim()
            main.postDelayed(this, 1000)
        }
    }

    // MARK: 시작과 끝

    /** 2.7.0 앱이 켜질 때 한 번(GilnunActivity.onCreate, 여정 엔진을 세운 뒤) — 점지도 묶음의 부품을 켜고,
     *  되짚어 나가기를 이어 기억하고, 3시간 안에 걷던 점지도가 있으면 이어 걸음. 이어 걸으면 참(걷는 화면을 열도록) */
    fun appSijak(c: Context): Boolean {
        junbi(c)
        MalgilEngine.sijak(c)
        Heundeul.sijak(c)
        RemoteDanchu.sijak(c)
        DoeEngine.ieoGagi(c)
        return ieoGagi(c)
    }

    // MARK: 2.7.0 걸어가기 — 점지도가 있으면 한 번 여쭘(이사장님 결정 나2, 점지도를 먼저 권함)

    /** 걸어가실 곳에 맞는 점지도가 있으면 여쭙고(mutgi 에 여쭐 말), 없으면 곧장 위성으로 걷는 안내(mutgi 에 null) */
    fun georeoGagiBoda(c: Context, j: JeomMokjeok, mutgi: (String?) -> Unit) {
        junbi(c)
        // 새 목적지면 걷던 점지도를 먼저 끔 — 옛 길이 계속 말하고 새 목적지를 덮어쓰던 것(아이폰 2.12.6)
        yeojeongKkeutJeom()
        val sd = sedae
        val w = Wichi.jigeum
        if (w == null) { mutgi(null); wiseongGeotgi?.invoke(j); return }
        Jeomjido.matneunGil(w.lat, w.lon, j) { m ->
            if (sd != sedae) return@matneunGil
            if (m == null) { mutgi(null); wiseongGeotgi?.invoke(j); return@matneunGil }
            yeojeongJeonghagi?.invoke(j)
            muleum = JeomYeojjum(j, m.first, m.second)
            byeonhwa?.invoke()
            mutgi("이 길에는 ${m.first}가 있습니다. 점지도로 걸을까요, 위성으로 걸을까요? 점지도가 더 정확합니다. 네 하시면 점지도로 걷습니다. 20초 안에 고르지 않으시면 점지도로 걷습니다.")
            main.removeCallbacks(muleumJakeop)
            main.postDelayed(muleumJakeop, 20000)
        }
    }

    /** 여쭌 말의 대답 — 참이면 점지도, 거짓이면 위성 */
    fun muleumDap(jeom: Boolean) {
        val m = muleum ?: return
        muleumJiugi()
        val c = ctx
        if (jeom && c != null) ieumGeotgi(c, m.gugan, m.mok) else wiseongGeotgi?.invoke(m.mok)
        byeonhwa?.invoke()
    }

    fun muleumJiugi() {
        sedae += 1
        main.removeCallbacks(muleumJakeop)
        muleum = null
    }

    // MARK: 시작과 끝

    /** 2.7.0 여러 점지도를 차례로 이어 걷기 — 구간이 하나면 그냥 따라 걷기 */
    fun ieumGeotgi(c: Context, g: List<JeomGugan>, mok: JeomMokjeok?) {
        val cheot = g.firstOrNull() ?: return
        ieum = if (g.size > 1) g else emptyList()
        ieumIdx = 0
        ieumMok = mok
        bulleoGeotgi(c, cheot.id, cheot.dwit, mok, true)
    }

    /** 점지도를 불러 따라 걷기 시작(dw 참이면 되돌아가기 — 끝에서 처음으로) */
    fun bulleoGeotgi(c: Context, id: String, dw: Boolean, mok: JeomMokjeok? = null, ieumYuji: Boolean = false) {
        junbi(c)
        muleumJiugi()
        val sd = sedae
        bulleoneun = true
        byeonhwa?.invoke()
        mal(if (dw) "되돌아가는 길을 불러오는 중입니다." else "길을 불러오는 중입니다.", MalGeup.JEONGBO)
        Jeomjido.bulleoogi(id) { g ->
            if (sd != sedae) return@bulleoogi   // 그사이 그만두셨으면 버림
            bulleoneun = false
            if (g == null) {
                // 아이폰 2.12.0 이어 걷던 길을 못 불러오면 멈추지 않고 위성 안내로 이어 감
                val ws = wiseongGeotgi
                if (ieumYuji && ws != null) {
                    val mk = ieumMok ?: mok
                    ieum = emptyList(); ieumIdx = 0; ieumMok = null
                    ieogaJiugi()
                    mal("길을 불러오지 못했습니다. 위성 안내로 이어 갑니다.")
                    ws(mk)
                } else {
                    mal("길을 불러오지 못했습니다. 통신을 확인해 주십시오.")
                }
                byeonhwa?.invoke()
                return@bulleoogi
            }
            sijak(c, g, dw, mok, ieumYuji)
        }
    }

    fun sijak(c: Context, g0: JeomGil, dw: Boolean, mok: JeomMokjeok? = null, ieumYuji: Boolean = false) {
        junbi(c)
        muleumJiugi()
        chaMalHam = false; bareunSijak = 0L   // 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 걷기마다 새로(아이폰과 같음)
        val sd = sedae
        bulleoneun = false
        geumanSok(false)
        if (!ieumYuji) { ieum = emptyList(); ieumIdx = 0; ieumMok = null }
        val g = if (dw) g0.dwit() else g0
        val p = g.pts.filter { it.lat != 0.0 && it.lon != 0.0 }
        if (p.size < 3) { ieogaJiugi(); mal("이 길에는 점이 너무 적습니다."); byeonhwa?.invoke(); return }
        pts = p
        nu.clear()
        nu.add(0.0)
        for (i in 1 until p.size) nu.add(nu[i - 1] + Wichi.geori(p[i - 1].lat, p[i - 1].lon, p[i].lat, p[i].lon))
        pyo = g.marks.mapNotNull { m ->
            val la = m.lat
            val lo = m.lon
            if (la == null || lo == null || la == 0.0) null else Pyo(m, gakkaunJeom(la, lo))
        }
        kkeoks = kkeokChatgi(p)
        kkeokHan.clear()
        // 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 탈것 구간을 찾고, 그 안의 꺾임은 걸음 안내에서 뺌
        tagiGugan = tagiChatgi()
        tagiHan.clear()
        tagiJung = null
        if (tagiGugan.isNotEmpty()) kkeoks = kkeoks.filter { k -> tagiGugan.none { k.i > it.i0 && k.i < it.i1 } }
        gilRaw = g0.id + (if (dw) "|r" else "")
        wonGil = g0
        dwit = dw
        dochakHam = false
        idx = 0; firstFix = true; me = null; spd = 0.0; lastT = 0L; offSu = 0; beoseoSu = 0
        jeop30 = false; jeop20 = false; cheotBang = false; dolgiMok = null; geonneolOn = false; geonneolI = -1
        geoMode = false; dwiNeolge = false; geollimJari = null; geollimHan.clear()
        munOn = false; mun = null; munList = emptyList(); munSu = 0; munIdx = 0; munDasi = false; munGakkaum = null; munKamera = false
        juwiRows = emptyList(); juwiHan.clear(); juwiEonje = 0L; juwiJari = null; juwiCenter = null; juwiBatT = 0L
        val now = System.currentTimeMillis()
        sijakT = now
        jariTtae = now; wiseongTtae = now
        S = HS()
        gil = g
        // 여정 — 이 길의 끝을 목적지로(차를 타셔도 목적지가 이어지게, 웹 260910-9와 같음)
        val e = p[p.size - 1]
        val doIreum = if (mok == null || mok.ireum.isEmpty()) (if (g.to.isEmpty()) g.title else g.to) else mok.ireum
        yeojeongJeonghagi?.invoke(JeomMokjeok(doIreum, mok?.juso ?: "", mok?.lat ?: e.lat, mok?.lon ?: e.lon))
        if (!g0.id.startsWith("nae_")) Tongsin.json("ttara.php", mapOf("a" to "put", "id" to g0.id)) { }
        Girok.namgi("jeom_sijak", mapOf("id" to g0.id, "dwit" to dw, "m" to (nu.lastOrNull() ?: 0.0).toInt()))
        // 안전 경고 — 끌 수 없음
        hwakinDoen(g0.id) { hwakin ->
            if (sd != sedae || gil == null) return@hwakinDoen
            var t = if (ieumIdx > 0) "" else "길눈은 보조 안내입니다. 단독보행을 하실 때는 반드시 흰지팡이를 짚고, 주변 소리를 먼저 확인하십시오."   // 2.18.0 흰지팡이 강조
            if (!hwakin) t += " 이 점지도는 아직 확인 중인 길입니다. 조심해서 걸으십시오."
            if (t.isNotEmpty()) mal(t.trim(), MalGeup.GYEONGGO)
            var m = if (ieum.isEmpty()) "" else "이어진 길 ${ieum.size}구간 가운데 ${ieumIdx + 1}번째 구간입니다. "
            m += (if (dw) "되돌아가기를 시작합니다. " else "따라 걷기를 시작합니다. ") + "모두 ${Jeomjido.bannol(nu.lastOrNull() ?: 0.0).toInt()}미터입니다."
            m += if (Seoljeong.bopokJaem) " 걸음 수는 ${Seoljeong.bopokModeIreum()} 보폭으로 알려 드립니다." else " 보폭을 아직 재지 않으셔서 걸음 수 대신 미터로 알려 드립니다."   // 2.18.0
            if (kkeoks.isNotEmpty()) m += " 이 길에 꺾이는 자리가 ${kkeoks.size}곳 있습니다. 미리 알려 드리겠습니다."
            mal(m)
        }
        ieogaJeojang(g0.id, dw, mok)
        munJunbi()
        umjikSijak()
        tikT = now
        sigyeDolgo = true
        main.removeCallbacks(sigye)
        main.postDelayed(sigye, 1000)
        byeonhwa?.invoke()
        val w = Wichi.jigeum
        if (w != null && now - w.ttae < 5000 && !w.georeumChu) wichiBatda(w)
    }

    /** 그만 걷기 */
    fun geuman() {
        val bulleo = bulleoneun
        sedae += 1
        bulleoneun = false
        if (gil == null) {
            if (bulleo) { ieum = emptyList(); ieumIdx = 0; ieumMok = null; ieogaJiugi(); mal("따라 걷기를 그만두었습니다.") }
            byeonhwa?.invoke()
            return
        }
        ieum = emptyList(); ieumIdx = 0; ieumMok = null
        ieogaJiugi()
        hamkkeBunho?.let { hamkkeKkeut(it); hamkkeBunho = null }
        val dochak = dochakHam
        geumanSok(!dochak)
        yeojeongKkeut?.invoke()
        if (dochak) mal(if (yeojeongKkeut != null) "여정을 끝냈습니다." else "따라 걷기를 마쳤습니다.")
        byeonhwa?.invoke()
    }

    /** 2.7.0 여정을 끝낼 때 — 다른 묶음(여정·안내)이 부름. 말없이 점지도만 닫음 */
    fun yeojeongKkeutJeom() {
        muleumJiugi()
        bulleoneun = false
        ieum = emptyList(); ieumIdx = 0; ieumMok = null
        ieogaJiugi()
        hamkkeBunho?.let { hamkkeKkeut(it); hamkkeBunho = null }
        if (gil != null) geumanSok(false)
        byeonhwa?.invoke()
    }

    private fun geumanSok(malHam: Boolean) {
        val itdeon = gil != null
        if (itdeon && !dochakHam) sseumNamgigi(false)
        sigyeDolgo = false
        main.removeCallbacks(sigye)
        gasokKkeugi()
        if (MomSensor.gilnunGeoreum === naeGeoreum) MomSensor.gilnunGeoreum = apGeoreum
        apGeoreum = null
        WatchLink.geotgiAllim(false)   // 2.5.0
        watchN0 = null
        if (garikiBonaen != null) { WatchLink.garikiBonae(null); garikiBonaen = null }
        if (momNaega) { MomSensor.kkeugi(); momNaega = false }
        momSseum = false
        munOn = false; mun = null; munList = emptyList(); munSu = 0; munKamera = false
        tagiJung = null; tagiGugan = emptyList(); tagiHan.clear()   // 2.32.0 (261009-A20, 이사장님 승인 2026-10-09)
        gil = null
        dochakHam = false
        sangMal = ""
        if (itdeon && malHam) mal("따라 걷기를 그만두었습니다.")
    }

    /** 도착한 뒤 — 되돌아가기(같은 길을 거꾸로) */
    fun doedoragagi() {
        if (gil == null) return
        val c = ctx ?: return
        val id = gilRaw.replace("|r", "")
        val dw = !dwit
        // 아이폰 2.12.0 이어진 길을 다 걸은 뒤에는 이어진 길 전체를 거꾸로
        if (ieum.size > 1) {
            val r = ieum.reversed().map { JeomGugan(it.id, !it.dwit) }
            ieumGeotgi(c, r, null)
            return
        }
        ieum = emptyList(); ieumIdx = 0; ieumMok = null
        if (id.startsWith("nae_")) { val n = NaeGil.chatgi(id); if (n != null) { sijak(c, n, dw); return } }
        val w = wonGil
        if (w != null && w.id == id) { sijak(c, w, dw); return }
        bulleoGeotgi(c, id, dw)
    }

    // MARK: 단추 — 지금 어디쯤

    /** 지금 어디쯤인지 — 남은 거리, 오차, 다음에 있는 것 */
    fun jigeumEodiDeutgi() {
        if (gil == null) return
        if (me == null) { mal("지금 자리를 잡는 중입니다. 잠시만 기다려 주십시오."); return }
        mal(jigeumEodiMal() + " " + daeumMuotMal())
    }

    private fun jigeumEodiMal(): String =
        "남은 거리는 ${georiMal(namEun)}입니다. 위치 오차는 약 ${max(1, Jeomjido.bannol(acc).toInt())}미터입니다." +
            (if (geoMode) " 지금은 걸음으로 이어 셈하고 있습니다." else "")

    /** 다음에 무엇이 있습니까(말로) */
    fun daeumMuot() { mal(daeumMuotMal()) }

    /** 다음에 무엇이 있습니까 */
    fun daeumMuotMal(): String {
        if (gil == null) return ""
        if (me == null) return "지금 자리를 잡는 중입니다. 잠시만 기다려 주십시오."
        var near: Pyo? = null
        var nd = 1e9
        for (p in pyo) {
            if (p.i < idx) continue
            val d = ap(idx, p.i)
            if (d < nd) { nd = d; near = p }
        }
        var kkD = 1e9
        var kk: Kkeok? = null
        for (k in kkeoks) {
            if (k.i <= idx) continue
            val d = ap(idx, k.i)
            if (kk == null || d < kkD) { kkD = d; kk = k }
        }
        if (kk != null && kkD < nd) {
            return "다음은 꺾이는 곳이고, ${georeum(kkD)} 앞에서 ${sigyeGak(kk.d)}으로 꺾습니다."
        }
        val n = near ?: run {
            if (pyo.isEmpty() && kkeoks.isEmpty()) return "이 길에는 표시가 없습니다. 목적지까지 남은 거리 ${georiMal(namEun)}입니다."
            return "앞에는 더 표시가 없습니다. 목적지까지 곧장 가시면 됩니다. 남은 거리 ${georiMal(namEun)}입니다."
        }
        val nm = n.p.ireum
        val cnt = n.p.cnt ?: 0
        val extra = if (cnt > 0 && nm.contains("계단")) " ${cnt}칸입니다." else ""
        return "다음은 ${nm}이고, " + (if (nd < 3) "바로 앞입니다." else "${georeum(nd)} 앞입니다.") + extra
    }

    /** 남은 거리(미터) */
    val namEun: Double
        get() = if (nu.isEmpty()) 0.0 else nu[nu.size - 1] - nu[min(idx, nu.size - 1)]

    // MARK: 자리 받기

    private fun wichiBatda(w: Jari) {
        if (gil == null || dochakHam) return
        if (w.georeumChu) return   // 위치 엔진이 곧게 이어 셈한 자리는 쓰지 않고, 점지도 위로 걸음을 셈(아래 jikim)
        // 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 걷는 빠르기보다 빠른가(시속 15킬로미터 넘게, 위성 오차 30미터 안, 아이폰과 같음)
        if (w.ochae <= 30 && w.sokdo * 3.6 > 15) {
            val nw = System.currentTimeMillis()
            if (bareunSijak == 0L) bareunSijak = nw
            bareunMajimak = nw
        } else {
            bareunSijak = 0L
        }
        if (geoMode && w.ochae > 25) return   // 걸음으로 가는 동안 흐린 위성은 받지 않음
        if (w.ochae <= 25) wiseongTtae = System.currentTimeMillis()
        if (geoMode) dwiNeolge = true   // 걸음으로 가다 위성이 돌아온 첫 자리 — 뒤로도 넓게 찾음
        geoMode = false
        onMove(w.lat, w.lon, w.ochae)
        // 사거리·갈림길 알림 — 문까지 가는 동안은 쉼(다른 묶음이 채움)
        if (gil != null && !dochakHam && !munOn && tagiJung == null) neagoriBakkeseo?.invoke(w)   // 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 탈것 구간에서는 쉼
    }

    private fun onMove(la: Double, lo: Double, ac: Double) {
        if (gil == null) return
        val now = System.currentTimeMillis()
        me = Pair(la, lo)
        acc = ac
        jariTtae = now
        if (lastT > 0) {
            val dt = (now - lastT) / 1000.0
            if (dt > 0.7) {
                val v = Wichi.geori(lastLa, lastLo, la, lo) / dt
                if (v >= 0.2 && v <= 2.5) spd = if (spd > 0) spd * 0.7 + v * 0.3 else v
                lastLa = la; lastLo = lo; lastT = now
            }
        } else {
            lastLa = la; lastLo = lo; lastT = now
        }
        // 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 탈것을 타고 가는 동안 — 내리실 곳 30미터 안에 오시면 다시 걸음 안내, 그 전에는 아무 걸음 안내도 하지 않음
        val tj = tagiJung
        if (tj != null) {
            val d = Wichi.geori(la, lo, tj.lat, tj.lon)
            if (d <= 30 && ac <= 30) {
                tagiKkeut()
            } else {
                sangMal = "${tj.kind} 타고 가는 중. 내리실 곳까지 약 ${tagiGeori(d)}."
                return
            }
        }
        // 길 위에서 가장 가까운 점(처음에는 길 전체에서, 그 뒤로는 앞쪽만)
        var best = idx
        var bestD = 1e9
        val jeon = idx
        val k0 = if (firstFix) 0 else max(0, idx - (if (dwiNeolge) 40 else 3))
        val k1 = if (firstFix) pts.size else min(pts.size, idx + 40)
        for (k in k0 until k1) {
            val d = Wichi.geori(la, lo, pts[k].lat, pts[k].lon)
            if (d < bestD) { bestD = d; best = k }
        }
        dwiNeolge = false
        if (firstFix) {
            // 되돌아오는 길(시작과 끝이 붙은 길)에서 처음 자리를 끝으로 잡지 않게 — 비슷하게 가까우면 앞쪽 점
            for (k in pts.indices) {
                if (Wichi.geori(la, lo, pts[k].lat, pts[k].lon) <= bestD + 5) { best = k; break }
            }
        } else if (ac > 15 && best > jeon + 15) {
            best = jeon + 15   // 흐린 위성이 크게 뛰면 한 번에 멀리 건너뛰지 않음(건널목·꺾임 알림을 놓치지 않게)
        }
        idx = best
        if (firstFix) {
            firstFix = false
            for (q in pyo) if (q.i < idx) { q.said = true; q.near = true; q.said30 = true }
        }
        // 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 타는 곳에 닿으면(3미터 안이거나 지나쳤으면) 탈것 구간으로
        val tt = tagiGugan.firstOrNull { !tagiHan.contains(it.i0) && idx < it.i1 && (idx >= it.i0 || ap(idx, it.i0) <= 3) }
        if (tt != null) { tagiSijak(tt); return }
        hamkkeBonaegi()
        val rest = namEun
        sangMal = "남은 거리 ${Jeomjido.bannol(rest).toInt()}미터, 위치 오차 약 ${Jeomjido.bannol(ac).toInt()}미터."
        hwaksinGil(bestD, ac, rest)
        bangHwagin()
        // 도착 접근 안내 — 30미터쯤 한 번, 20미터 안에서 한 번, 그 뒤 2.5초마다 남은 거리와 시 방향
        val e = pts[pts.size - 1]
        val kkeutMal = if (jungganGugan) "이 구간 끝" else "목적지"
        if (rest >= 5 && rest < 32 && mun == null) {
            if (rest > 20) {
                if (!jeop30) { jeop30 = true; mal("${kkeutMal}까지 ${Jeomjido.bannol(rest).toInt()}미터입니다.") }
            } else if (!jeop20) {
                jeop20 = true; jeopT = now
                mal("$kkeutMal ${Jeomjido.bannol(rest).toInt()}미터 앞${siMal(e.lat, e.lon)}입니다.")
            } else if (now - jeopT > 2500) {
                jeopT = now
                mal("남은 거리 ${georiMal(rest)}${siMal(e.lat, e.lon)}입니다.")
            }
        }
        // 2.7.0 문까지 — 길 끝 50미터 안에서 문을 다시 찾고, 30미터 안에서 문으로 이끌고, 문 5미터 안에서 도착
        if (rest < 50 && !munDasi && !munOn) { munDasi = true; munJunbi() }
        val MU = mun
        if (MU != null) {
            val dm = Wichi.geori(la, lo, MU.lat, MU.lon)
            if (!munOn && rest < 30) {
                munOn = true; munT = now; munSijakT = now; munGakkaum = dm
                S.gyeol = ""; S.yeop = 0.0
                mal("문까지 이어 안내합니다. 여기서부터 위성 안내입니다. " + munMal(MU) + (if (munList.size > 1) " 문이 ${munList.size}곳 있습니다." else ""))
                byeonhwa?.invoke()
                return
            }
            if (munOn) {
                // 문 10미터 안 — 카메라 문 찾기를 저절로(묶음 3이 이어 붙임). 위성 안내는 그대로 이어 감
                if (dm < 10 && !munKamera) {
                    munKamera = true
                    if (JeomMunKamera.gigiGaneung()) JeomMunKamera.kyeogi?.invoke("munkkaji", if (MU.geul.isEmpty()) null else Pair(MU.ireum, MU.geul))
                }
                val g = munGakkaum
                if (g != null) {
                    if (g - dm >= 1.5) { munGakkaum = dm; eum(EumJong.HWAKSIN) } else if (dm > g + 3) munGakkaum = dm
                }
                if (dm < max(5.0, min(acc, 10.0))) {
                    var t = "문 앞입니다. " + munMal(MU)
                    if (MU.saengMal.isNotEmpty()) t += " 문은 ${MU.saengMal}입니다."
                    dochak(t)
                    return
                }
                if (now - munT > 2500 && !JeomMunKamera.munBoim()) { munT = now; mal(munMal(MU)) }   // 카메라가 문을 보고 있으면 카메라 말에 맡김
                return
            }
        }
        // 2.7.0 지나는 곳 안내 — 목적지 20미터 안에서는 문 찾기에 집중하도록 쉼
        if (rest >= 20) juwiMalhagi(la, lo)
        geollimSalpigi(la, lo)
        kkeokBoda(la, lo)
        // 길에서 크게 벗어남 — 말은 확신음 쪽이 맡고, 여기서는 걸린 자리로 남김
        if (bestD > max(12.0, ac * 1.5)) {
            offSu += 1
            if (offSu >= 2) makhimNamgigi(la, lo, bestD)
            return
        }
        offSu = 0
        if (rest < 5) { dochak(); return }
        pyoBoda()
        // 건널목 끝 표시가 없는 점지도 — 건널목 길이만큼 지나면 다 건넜다고
        if (geonneolI >= 0 && ap(geonneolI, idx) >= geonneolLen + 2) {
            geonneolI = -1; geonneolOn = false; S.gidarim = false
            mal("다 건넜습니다. " + daeumMalGil())
        }
    }

    // MARK: 1초마다 지킴

    private fun jikim() {
        if (gil == null || dochakHam) return
        val now = System.currentTimeMillis()
        // 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 탈것을 타고 가는 동안은 끊김·제자리·벗어남 말을 하지 않음
        if (tagiJung != null) { tikT = now; return }
        // 폰이 멈췄다 깨어나 시계가 10초 넘게 건너뛰면 — 안내가 끊겼던 것(끌 수 없음)
        if (now - tikT > 10000 && now - kkeunMalT >= 10000) {
            kkeunMalT = now
            mal("안내가 끊겼습니다. 멈추고 주변을 확인하십시오.", MalGeup.GYEONGGO)
            Girok.namgi("jeom_kkeunkim")
        }
        tikT = now
        garikiAllim(now)   // 2.5.0
        // 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 시속 15킬로미터 넘게 10초 넘게 움직이는 중(차를 타신 듯)에는 벗어남·확인 어려움 경고를 되풀이하지 않음(아이폰과 같음)
        if (chaBareum) return
        // 위성이 6초 넘게 끊기면 걸음으로 점지도 위를 나아감(georeum_iego.js)
        if (now - wiseongTtae > 6000 && S.stepSu > 0) {
            if (!geoMode) {
                geoMode = true
                geoSu = S.stepSu
                geoS = S.jin ?: (if (nu.isEmpty()) 0.0 else nu[min(idx, nu.size - 1)])
                geoS0 = geoS
                geoAcc0 = max(3.0, acc)
            } else if (S.stepSu > geoSu) {
                geoS += (S.stepSu - geoSu) * bocok
                geoSu = S.stepSu
                val q = jeomAt(geoS)
                onMove(q.first, q.second, geoAcc0 + (geoS - geoS0) * 0.1)   // 걸을수록 오차가 커짐
                if (gil == null || dochakHam) return
            }
        }
        // 2.7.0 문을 90초 넘게 찾으면 — 끝없이 말하지 않고 마침(아이폰 2.12.0)
        val MU = mun
        if (munOn && MU != null && now - munSijakT > 90000) {
            dochak("문을 찾는 시간이 길어져 안내를 마칩니다. " + munMal(MU))
            return
        }
        // 점지도를 확신할 수 없는 동안(폰 방향이 들쭉날쭉) 5초마다
        if (S.jumeoni && !munOn && !S.pokgiMal && now - S.hwakMalT >= 5000) {
            S.hwakMalT = now
            mal("점지도 확인이 어렵습니다. 멈추고 주변을 확인하십시오.", MalGeup.GYEONGGO)
        }
        if (now - jariTtae > 5000 && !georeumSalanna) {
            if (now - S.wdMal >= 5000) {
                S.wdMal = now; S.sinho = now
                mal("점지도 확인이 어렵습니다. 멈추고 주변을 확인하십시오.", MalGeup.GYEONGGO)
            }
            return
        }
        if (S.gyeol == "beoseo") {
            if (now - S.offT >= 2500) { S.offT = now; eum(EumJong.BEOSEO); S.sinho = now }
            if (now - S.offMalT >= 5000) { S.offMalT = now; beoseoMal(false) }
            return
        }
        if (S.gidarim) return
        if (now < S.dolgiKkaji) return
        if (now - malT < 5000) { S.sinho = max(S.sinho, malT); return }
        if (now - S.sinho >= 5000 && now - S.wdMal >= 5000) {
            S.wdMal = now; S.sinho = now
            val r = S.rest
            val t = if (r != null) "남은 거리 ${georiMal(r)}." else sangMal
            mal((if (S.gamyeon) "안내 중입니다. " else "제자리에 계십니다. ") + t, MalGeup.JEONGBO)
            S.gamyeon = false
        }
    }

    // MARK: 걸음

    private fun umjikSijak() {
        WatchLink.geotgiAllim(true)   // 2.5.0 워치도 깨어 걸음을 셈
        val c = ctx ?: return
        val sm = c.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        // 아이폰 2.32.0 몸 센서 — 1초에 50번, 발이 땅에 닿을 때마다 한 걸음, 자이로로 돈 각도
        if (sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null && sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            if (Wichi.nachimban >= 0) MomSensor.nachimbanNeogi(Wichi.nachimban)
            if (!MomSensor.dollyeo) { MomSensor.kyeogi(c); momNaega = true }
            momSseum = true
            if (MomSensor.gilnunGeoreum !== naeGeoreum) {
                apGeoreum = MomSensor.gilnunGeoreum   // 위치 엔진의 걸음 받기는 그대로 함께 부름
                MomSensor.gilnunGeoreum = naeGeoreum
            }
            Girok.namgi("jeom_momsensor", mapOf("on" to true))
            return
        }
        val a = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        moG = 9.8; moWi = false
        gasokSm = sm
        sm.registerListener(gasok, a, 1_000_000 / 50, main)
        Girok.namgi("jeom_momsensor", mapOf("on" to false))
    }

    private fun gasokKkeugi() {
        gasokSm?.unregisterListener(gasok)
        gasokSm = null
    }

    private val gasok = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val x = e.values[0].toDouble()
            val y = e.values[1].toDouble()
            val z = e.values[2].toDouble()
            val m = sqrt(x * x + y * y + z * z)   // 안드로이드 값은 이미 초제곱 미터
            moG = moG * 0.95 + m * 0.05
            val df = m - moG
            val now = System.currentTimeMillis()
            if (!moWi && df > 1.3 && now - moT > 300) {
                moWi = true
                moT = now
                ponGeoreumT = now
                if (S.gidarim) { S.gidarimSu += 1; if (S.gidarimSu >= 3) S.gidarim = false }
                georeum()
            } else if (moWi && df < 0.3) {
                moWi = false
            }
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    private val nachimban: Double?
        get() {
            // 아이폰 2.32.0 몸 센서를 쓰는 동안은 자이로 합성 방향 — 쇠붙이·건물 옆에서 나침반이 흔들려도 틀어지지 않음
            if (momSseum && MomSensor.dollyeo) { val h = MomSensor.hapseong; if (h != null) return h }
            val n = Wichi.nachimban
            return if (n >= 0) n else null
        }

    private val georeumSalanna: Boolean
        get() = System.currentTimeMillis() - S.stepT < 120_000 && nachimban != null && !S.jumeoni

    /** 발을 한 번 디딜 때마다 */
    private fun georeum() {
        if (gil == null || dochakHam || tagiJung != null) return   // 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 탈것 구간에서는 걸음으로 셈하지 않음
        val now = System.currentTimeMillis()
        if (now - S.stepT > 10000) {
            S.yeop = 0.0
            if (S.gyeol == "beoseo" || S.gyeol == "heundeul") S.gyeol = ""
            S.offSu = 0; S.gpsNeomSu = 0
        }
        S.stepT = now
        S.stepSu += 1
        val seg = S.segBang ?: return
        if (now < S.dolgiKkaji) { S.gyeolgwa.clear(); S.dallaSu = 0; S.majaSu = 0; return }
        val h = nachimban ?: return
        val nal = chai(h, seg)
        S.gyeolgwa.add(Pair(nal, S.jin))
        if (S.gyeolgwa.size > 8) S.gyeolgwa.removeAt(0)
        val df = chai(h - S.dolrim, seg)
        if (abs(df) > 70) {
            S.dallaSu += 1; S.majaSu = 0
            if (S.dallaSu >= 6) {
                // 폰을 거꾸로(옆으로) 넣으셨는지 — 늘 같은 쪽으로 어긋나며 앞으로 나아가면 그만큼 돌려 읽음
                val g = S.gyeolgwa.takeLast(6)
                var sx = 0.0
                var sy = 0.0
                for (x in g) { sx += sin(x.first * RAD); sy += cos(x.first * RAD) }
                val goreum = sqrt(sx * sx + sy * sy) / g.size
                val pyeong = atan2(sx, sy) / RAD
                var nagam = 0.0
                val a = g.lastOrNull()?.second
                val b = g.firstOrNull()?.second
                if (a != null && b != null) nagam = a - b
                if (goreum > 0.8 && nagam >= 2) {
                    val dol = (Jeomjido.bannol(pyeong / 90) * 90 + 360) % 360
                    if (dol != 0.0) {
                        S.dolrim = dol; S.dallaSu = 0; S.majaSu = 0; S.jumeoni = false; S.yeop = 0.0; S.gyeolgwa.clear()
                        if (S.gyeol == "beoseo" || S.gyeol == "heundeul") S.gyeol = ""
                        mal(if (dol == 180.0) "폰이 거꾸로 들어 있어 맞춰 읽겠습니다." else "폰이 옆으로 들어 있어 맞춰 읽겠습니다.")
                        return
                    }
                }
                if (!S.jumeoni) {
                    S.jumeoni = true
                    if (!S.jumeoniMal) {
                        S.jumeoniMal = true; S.hwakMalT = System.currentTimeMillis()
                        mal("점지도 확인이 어렵습니다. 멈추고 주변을 확인하십시오. 폰이 향한 쪽이 자꾸 바뀝니다. 가슴 주머니에 세워 넣거나 가슴 앞에 들어 주십시오.", MalGeup.GYEONGGO)
                    }
                }
            }
            return
        }
        if (abs(df) < 30) { S.majaSu += 1; if (S.majaSu >= 3) { S.dallaSu = 0; if (S.jumeoni) { S.jumeoni = false; S.yeop = 0.0 } } }
        if (S.jumeoni) return
        // 폰이 흔들려 방향이 들쭉날쭉한 걸음은 셈에 넣지 않음(헛경고를 막음)
        val gg = S.gyeolgwa
        val n3 = gg.size
        if (n3 >= 3) {
            val p1 = gg[n3 - 1].first
            val p2 = gg[n3 - 2].first
            val p3 = gg[n3 - 3].first
            val d12 = chai(p1, p2)
            val d23 = chai(p2, p3)
            if ((abs(d12) > 35 && abs(d23) > 35 && d12 * d23 < 0 && abs(chai(p1, p3)) > 20) || abs(chai(p1, p3)) > 100 || abs(d12) > 100) {
                S.heundeulSu += 1
                if (S.heundeulSu >= 6 && !S.jumeoniMal) {
                    S.jumeoniMal = true; S.hwakMalT = System.currentTimeMillis()
                    mal("점지도 확인이 어렵습니다. 멈추고 주변을 확인하십시오. 폰이 향한 쪽이 자꾸 바뀝니다. 가슴 주머니에 세워 넣거나 가슴 앞에 들어 주십시오.", MalGeup.GYEONGGO)
                }
                return
            }
        }
        S.heundeulSu = 0
        S.yeop += bocok * sin(df * RAD)
        pandan()
    }

    private fun pandan() {
        val b = bocok
        val a = abs(S.yeop)
        val now = System.currentTimeMillis()
        if (a >= b) {
            if (S.gyeol != "beoseo") {
                S.gyeol = "beoseo"; S.offT = now; S.offMalT = now; beoseoSu += 1
                eum(EumJong.BEOSEO); beoseoMal(true)
                me?.let { makhimNamgigi(it.first, it.second, a) }
            }
            S.sinho = now
            return
        }
        if (S.gyeol == "beoseo") {
            if (a > b * 0.45) { S.sinho = now; return }
            S.gyeol = "baro"; eum(EumJong.DORAOM); mal("점지도 위로 돌아오셨습니다."); S.sinho = now
            return
        }
        if (a >= b * 0.5 || (S.gyeol == "heundeul" && a > b * 0.3)) {
            eum(EumJong.BIKYEO)
            if (S.gyeol != "heundeul") { S.gyeol = "heundeul"; mal((if (S.yeop > 0) "3시" else "9시") + " 방향으로 조금 비켜나십니다.") }
            S.sinho = now
            return
        }
        S.gyeol = "baro"; eum(EumJong.JEOMOK); S.sinho = now; S.gamyeon = true
    }

    private fun beoseoMal(cheot: Boolean) {
        if (chaBareum) return   // 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 차 빠르기로 움직이는 중에는 벗어남 말을 하지 않음(아이폰과 같음)
        val apm = if (cheot) "점지도에서 벗어났습니다. 멈추고 방향을 다시 잡으십시오. " else ""
        val oreun = S.yeop > 0
        // 아이폰 2.12.0 돌아갈 방향은 몸이 향한 쪽 기준 — 점지도 방향과 벗어난 거리로 셈(모르면 11시·1시)
        var bangM = if (oreun) "11시 방향" else "1시 방향"
        val seg = S.segBang
        val h = jigeumHead
        if (seg != null && h != null) {
            val mok = seg + atan2(-S.yeop, 3.0) / RAD
            bangM = sigyeGak(chai(mok, h))
        }
        if (!georeumSalanna) {
            mal(apm + (if (oreun) "3시 방향으로 " else "9시 방향으로 ") + "약 ${max(1, Jeomjido.bannol(abs(S.yeop)).toInt())}미터 벗어났습니다. " + bangM + "으로 돌아가십시오.", MalGeup.GYEONGGO)
            return
        }
        val n = max(1, Jeomjido.bannol(abs(S.yeop) / bocok).toInt())
        val gm = georeumSu(n)
        mal(apm + (if (oreun) "3시 방향으로 " else "9시 방향으로 ") + gm + " 벗어났습니다. " + bangM + "으로 " + gm + " 옮기십시오.", MalGeup.GYEONGGO)
    }

    /** 자리가 올 때마다(위성이든 걸음이든) — 점지도 선 위에 앉혀 옆 거리와 나아간 거리를 봄 */
    private fun hwaksinGil(g0: Double, ac: Double, rest: Double) {
        val m = me ?: return
        val now = System.currentTimeMillis()
        var g = g0
        var jin = -rest
        var gy: Double? = null
        S.rest = rest
        val an = anchigi(m.first, m.second)
        val s = an.s
        if (s != null) {
            jin = s; g = min(g, an.d); gy = an.y; S.jin = s
            if (S.jinSijak == null) S.jinSijak = s
            // 아이폰 2.12.0 구간 방향은 앞쪽 8미터로(꺾이는 곳에서 끊음), 꺾이는 곳이 2미터 안이면 뒤쪽 8미터로
            var s0 = s
            var s1 = s + 8
            val nk = kkeoks.firstOrNull { it.i < nu.size && nu[it.i] > s }
            if (nk != null) s1 = min(s1, nu[nk.i])
            if (s1 - s < 2) {
                s0 = s - 8; s1 = s
                val pk = kkeoks.lastOrNull { it.i < nu.size && nu[it.i] <= s }
                if (pk != null) s0 = max(s0, nu[pk.i])
                if (s1 - s0 < 1) s0 = s - 2
            }
            val a = jeomAt(s0)
            val b = jeomAt(s1)
            val bg = Jeomjido.bangwi(a.first, a.second, b.first, b.second)
            S.segBang = bg
            val k = S.segKijun
            if (k != null) {
                if (abs(chai(bg, k)) > 30) { S.segKijun = bg; if (S.gyeol != "beoseo") S.yeop = 0.0 }
            } else {
                S.segKijun = bg
            }
        }
        if (georeumSalanna) {
            if (gy != null && ac > 0 && ac <= 6 && now - S.stepT < 2000) S.yeop = S.yeop * 0.97 + gy * 0.03
            if (!S.gidarim && gy != null && abs(gy) > max(5.0, ac * 1.2)) {
                S.gpsNeomSu += 1
                if (S.gpsNeomSu >= 3) { S.yeop = gy; pandan() }
            } else {
                S.gpsNeomSu = 0
            }
        } else {
            // 걸음 감지·나침반을 못 쓸 때 — 되는 척하지 않고 분명히 알린 뒤, 위성 오차에 맞춘 잣대로
            val j = S.jin
            val j0 = S.jinSijak
            if (!S.pokgiMal && now - S.sijakT > 15000 && j != null && j0 != null && abs(j - j0) >= 8) {
                S.pokgiMal = true
                val kkadak = if (S.jumeoni) "폰이 향한 쪽이 자꾸 바뀌어" else (if (S.stepSu == 0) "걸음 감지를 쓸 수 없어" else (if (nachimban == null) "나침반을 쓸 수 없어" else "걸음 감지가 끊겨"))
                mal("$kkadak 한 걸음 안내가 되지 않습니다. 지금부터 위성 안내입니다. 몇 미터 오차가 있을 수 있습니다.", MalGeup.GYEONGGO)
                Girok.namgi("jeom_wiseongsem", mapOf("k" to kkadak))
            }
            val beoseo = g > max(8.0, ac * 1.2)
            val heundeul = !beoseo && g > max(5.0, ac * 0.8)
            if (gy != null) S.yeop = (if (gy >= 0) 1.0 else -1.0) * max(g, 0.0)
            if (beoseo && !S.gidarim) {
                S.offSu += 1
                if (S.offSu >= 2) {
                    if (S.gyeol != "beoseo") {
                        S.gyeol = "beoseo"; S.offT = now; S.offMalT = now; beoseoSu += 1
                        eum(EumJong.BEOSEO); beoseoMal(true)
                        makhimNamgigi(m.first, m.second, g)
                    }
                    S.sinho = now
                }
            } else {
                S.offSu = 0
                if (S.gyeol == "beoseo") { S.gyeol = "baro"; eum(EumJong.DORAOM); mal("점지도 위로 돌아오셨습니다."); S.sinho = now; S.kijun = jin }
                if (S.kijun == null) S.kijun = jin
                val k = S.kijun
                if (k != null) {
                    if (jin - k >= 0.8) {
                        S.kijun = jin; S.gamyeon = true; S.sinho = now
                        if (heundeul) {
                            eum(EumJong.BIKYEO)
                            if (S.gyeol != "heundeul") { S.gyeol = "heundeul"; mal((if (S.yeop > 0) "3시" else "9시") + " 방향으로 조금 비켜나십니다.") }
                        } else {
                            eum(EumJong.JEOMOK); S.gyeol = "baro"
                        }
                    } else if (jin < k - 3) {
                        S.kijun = jin
                    }
                }
            }
        }
        // 정한 거리마다 "제대로 가고 있습니다"
        if (S.malKijun == null) S.malKijun = jin
        val mk = S.malKijun
        if (mk != null) {
            if (jin - mk >= HWAKSIN_GAN) {
                S.malKijun = jin
                if (S.gyeol != "beoseo" && rest >= 32 && now - malT >= 4000) {
                    val jm = daeumMal()
                    if (jm != null) {
                        mal("제대로 가고 있습니다. " + (if (jm.isEmpty()) "남은 거리 ${georiMal(rest)}." else jm))
                        S.sinho = now
                    }
                }
            } else if (jin < mk - 5) {
                S.malKijun = jin
            }
        }
    }

    private class Anchim(val s: Double?, val d: Double, val y: Double)

    /** 점지도 선 위에 지금 자리를 앉힘 — s: 처음부터 몇 미터, d: 떨어진 거리, y: 오른쪽이 + 인 옆 거리 */
    private fun anchigi(la: Double, lo: Double): Anchim {
        val r = 6371000.0
        val cs = cos(la * RAD)
        val k0 = max(0, idx - 8)
        val k1 = min(pts.size - 2, idx + 15)
        var best = 1e9
        var s: Double? = null
        var y = 0.0
        if (k0 > k1) return Anchim(null, best, 0.0)
        for (k in k0..k1) {
            val ax = (pts[k].lon - lo) * RAD * cs * r
            val ay = (pts[k].lat - la) * RAD * r
            val bx = (pts[k + 1].lon - lo) * RAD * cs * r
            val by = (pts[k + 1].lat - la) * RAD * r
            val dx = bx - ax
            val dy = by - ay
            val l2 = dx * dx + dy * dy
            var t = if (l2 > 0) -(ax * dx + ay * dy) / l2 else 0.0
            t = max(0.0, min(1.0, t))
            val px = ax + t * dx
            val py = ay + t * dy
            val d = sqrt(px * px + py * py)
            if (d < best) {
                best = d
                s = nu[k] + t * (nu[k + 1] - nu[k])
                val l = if (sqrt(l2) > 0) sqrt(l2) else 1.0
                y = (dx * py - dy * px) / l
            }
        }
        return Anchim(s, best, y)
    }

    /** 처음부터 s미터 되는 점지도 위의 자리 */
    private fun jeomAt(s: Double): Pair<Double, Double> {
        if (nu.isEmpty() || pts.isEmpty()) return Pair(0.0, 0.0)
        val last = nu[nu.size - 1]
        if (s <= 0) return Pair(pts[0].lat, pts[0].lon)
        if (s >= last) return Pair(pts[pts.size - 1].lat, pts[pts.size - 1].lon)
        var lo = 0
        var hi = nu.size - 1
        while (hi - lo > 1) { val mid = (lo + hi) / 2; if (nu[mid] <= s) lo = mid else hi = mid }
        val f = if (nu[hi] - nu[lo] > 0) (s - nu[lo]) / (nu[hi] - nu[lo]) else 0.0
        return Pair(pts[lo].lat + (pts[hi].lat - pts[lo].lat) * f, pts[lo].lon + (pts[hi].lon - pts[lo].lon) * f)
    }

    private fun dolgi(cho: Double) {
        S.dolgiKkaji = System.currentTimeMillis() + (cho * 1000).toLong(); S.yeop = 0.0
        if (S.gyeol == "heundeul") S.gyeol = ""
    }

    private fun dolgiKkeut() { S.dolgiKkaji = 0L; S.yeop = 0.0; S.gyeolgwa.clear() }

    // MARK: 꺾이는 곳(kkeokim.js)

    private fun kkeokChatgi(p: List<JeomJeom>): List<Kkeok> {
        val out = ArrayList<Kkeok>()
        if (p.size < 5) return out
        fun g(a: Int, b: Int): Double = Wichi.geori(p[a].lat, p[a].lon, p[b].lat, p[b].lon)
        fun meol(i: Int, dir: Int): Int {
            var j = i
            var m = 0.0
            while (true) {
                val k = j + dir
                if (k < 0 || k >= p.size) break
                m += g(j, k)
                j = k
                if (m >= 10) break
            }
            return j
        }
        for (i in 2 until p.size - 2) {
            val a = meol(i, -1)
            val b = meol(i, 1)
            if (a == i || b == i) continue
            if (g(a, i) < 6 || g(i, b) < 6) continue
            val b1 = Jeomjido.bangwi(p[a].lat, p[a].lon, p[i].lat, p[i].lon)
            val b2 = Jeomjido.bangwi(p[i].lat, p[i].lon, p[b].lat, p[b].lon)
            val d = (b2 - b1 + 540) % 360 - 180
            if (!d.isFinite() || abs(d) < 35) continue
            val jeon = out.lastOrNull()
            if (jeon != null && g(jeon.i, i) < 12 && jeon.d * d > 0) {
                if (abs(d) > abs(jeon.d)) out[out.size - 1] = Kkeok(i, d, p[i].lat, p[i].lon)
                continue
            }
            out.add(Kkeok(i, d, p[i].lat, p[i].lon))
        }
        // 위성이 흔들린 자국을 모퉁이로 잘못 읽지 않게 — 서른 미터 안에 붙은 꺾임은 한 덩이로, 틀어진 만큼(합)만
        if (out.size < 2) return out
        val res = ArrayList<Kkeok>()
        var mung = arrayListOf(out[0])
        fun mudda(gg: List<Kkeok>) {
            if (gg.size == 1) { res.add(gg[0]); return }
            val hap = gg.sumOf { it.d }
            if (abs(hap) < 35) return
            val j = gg[gg.size / 2]
            res.add(Kkeok(j.i, hap, j.lat, j.lon))
        }
        for (n in 1 until out.size) {
            val jeon = mung[mung.size - 1]
            val now = out[n]
            // 아이폰 2.12.0 같은 쪽으로 꺾인 것끼리만 한 덩이로(왼쪽 뒤 오른쪽처럼 반대로 꺾인 두 모퉁이가 지워지지 않게)
            if (Wichi.geori(jeon.lat, jeon.lon, now.lat, now.lon) < 30 && jeon.d * now.d > 0) mung.add(now) else { mudda(mung); mung = arrayListOf(now) }
        }
        mudda(mung)
        // 15미터 안에서 반대로 살짝 꺾였다 돌아오는 것은 위성이 흔들린 자국으로 보아 뺌
        var n2 = 0
        val gyeol = ArrayList<Kkeok>()
        while (n2 < res.size) {
            if (n2 + 1 < res.size) {
                val a = res[n2]
                val b = res[n2 + 1]
                if (a.d * b.d < 0 && abs(a.d) < 60 && abs(b.d) < 60 && Wichi.geori(a.lat, a.lon, b.lat, b.lon) < 15) { n2 += 2; continue }
            }
            gyeol.add(res[n2]); n2 += 1
        }
        return gyeol
    }

    private fun kkeokBoda(la: Double, lo: Double) {
        // 아이폰 2.12.0 위성이 뛰어 모퉁이를 지나쳐도 가까이(오차만큼) 있으면 놓치지 않고 알림
        val yeoyu = max(8.0, acc)
        val dk = kkeoks.firstOrNull { k -> !kkeokHan.contains("k${k.i}b") && (k.i >= idx - 1 || ap(k.i, idx) <= yeoyu) } ?: return
        val mi = Wichi.geori(la, lo, dk.lat, dk.lon)
        val key = "k${dk.i}"
        if (dk.i < idx - 1) {
            kkeokHan.add(key + "a"); kkeokHan.add(key + "c"); kkeokHan.add(key + "b")
            mal("지금 ${sigyeGak(dk.d)}으로 도십시오.")
            watchBang(dk.d)
            dolgi(8.0)
            dolgiMok = DolgiMok(dk.i, null, System.currentTimeMillis()); dolgiGijun()
            return
        }
        val su = bocokSu(mi)
        if (su <= 30 && su > 13 && !kkeokHan.contains(key + "a")) {
            kkeokHan.add(key + "a")
            if (System.currentTimeMillis() - malT >= 3000) mal("직진 ${georeum(mi)} 뒤 ${sigyeGak(dk.d)}으로 꺾습니다.")
        } else if (su <= 11 && mi > 5 && !kkeokHan.contains(key + "c")) {
            kkeokHan.add(key + "c"); kkeokHan.add(key + "a")
            mal("${georeum(mi)} 앞에서 ${sigyeGak(dk.d)}으로 꺾습니다.")
        } else if (mi <= 5 && !kkeokHan.contains(key + "b")) {
            kkeokHan.add(key + "b")
            mal("지금 ${sigyeGak(dk.d)}으로 도십시오.")
            watchBang(dk.d)
            dolgi(8.0)
            dolgiMok = DolgiMok(dk.i, null, System.currentTimeMillis()); dolgiGijun()
        }
    }

    /** 2.5.0 워치 방향 진동 — 2.7.0 아이폰 2.38.0과 같이 폰과 워치에 같은 무늬(Jindong.banghyang: 2~5시 오른쪽, 7~10시 왼쪽, 6시 길게) */
    private fun watchBang(d: Double) {
        Jindong.banghyang(sigyeSu(d))
    }

    /** 2.5.0 (아이폰 2.36.0, 대표님 승인) 손목 가리키기 — 가야 할 쪽(돌아야 할 때는 돌 쪽, 아니면 앞 6미터)을 워치에
     *  5도 넘게 바뀌거나 10초가 지나면 다시 보냄 */
    private fun garikiAllim(now: Long) {
        val b = dolgiMok?.bang ?: gilBang(idx, 6.0) ?: return
        val o = garikiBonaen
        if (o != null && abs(chai(b, o)) < 5 && now - garikiT < 10000) return
        garikiBonaen = b; garikiT = now
        WatchLink.garikiBonae(b)
    }

    /** 2.5.0 가리키기 방향 맞추기 — 지금 몸이 향한 방향(따라 걷는 중일 때만) */
    val momBang: Double? get() = if (gil != null) jigeumHead else null

    /** 2.5.0 (아이폰 2.35.0, 대표님 승인) 워치가 팔 흔들림으로 센 걸음 누계 — 폰이 4초 넘게 걸음을 못 셀 때만, 한 번에 열 걸음까지 */
    fun watchGeoreum(n: Int) {
        if (gil == null) { watchN0 = null; return }
        val n0 = watchN0
        watchN0 = n
        if (n0 == null || n <= n0) return
        if (System.currentTimeMillis() - ponGeoreumT <= 4000) return
        repeat(min(n - n0, 10)) {
            if (S.gidarim) { S.gidarimSu += 1; if (S.gidarimSu >= 3) S.gidarim = false }
            georeum()
        }
        Girok.namgi("jeom_watch_georeum", mapOf("n" to n - n0))
    }

    private fun kkeokGakkai(mi: Int): Boolean = kkeoks.any { abs(ap(min(it.i, mi), max(it.i, mi))) <= 15 }

    // MARK: 출발 방향·돈 뒤 방향 확인(현장영상해설 말법)

    private fun gilBang(from: Int, m: Double): Double? {
        var s = 0.0
        var k = from
        while (k < pts.size - 1 && s < m) {
            s += Wichi.geori(pts[k].lat, pts[k].lon, pts[k + 1].lat, pts[k + 1].lon)
            k += 1
        }
        return if (k > from) Jeomjido.bangwi(pts[from].lat, pts[from].lon, pts[k].lat, pts[k].lon) else null
    }

    private val jigeumHead: Double?
        get() {
            val n = nachimban
            if (n != null) return (n - S.dolrim + 720) % 360   // 폰을 돌려 넣으신 만큼 바로잡음
            val w = Wichi.jigeum
            if (w != null && w.banghyang >= 0 && w.sokdo > 0.3) return w.banghyang
            return null
        }

    /** 아이폰 2.32.0 도시라고 말씀드린 순간의 몸 돈 각도를 기준으로 잡음 */
    private fun dolgiGijun() {
        dolgiDg0 = if (momSseum && MomSensor.dollyeo) MomSensor.nujeokDol else null
        dolgiWant = null
        bandaeMal = false
    }

    private fun bangHwagin() {
        val hh = jigeumHead
        val now = System.currentTimeMillis()
        if (!cheotBang) {
            if (now - sijakT < 5000) return   // 첫 안전 경고가 끝난 뒤에
            cheotBang = true
            val gb = gilBang(idx, 6.0) ?: return
            if (hh == null) { mal(daeumMalGil()); return }
            val df = chai(gb, hh)
            if (abs(df) > 25) {
                mal("지금 몸을 ${sigyeGak(df)}으로 돌리십시오.")
                dolgi(8.0)
                dolgiMok = DolgiMok(null, gb, now); dolgiGijun()
            } else {
                mal("12시 방향 맞습니다. " + daeumMalGil())
            }
            return
        }
        val dm = dolgiMok ?: return
        if (now - dm.t > 20000) { dolgiMok = null; return }
        val dk = dm.k
        if (dm.bang == null && dk != null) {
            val b = gilBang(dk, 6.0)
            if (b == null) { dolgiMok = null; return }
            dm.bang = b
        }
        val mok = dm.bang ?: return
        // 아이폰 2.32.0 몸 센서 — 도셔야 할 쪽과 반대로 크게 도시면 곧바로 알려 드림(자이로로 잰 돈 각도)
        val d0 = dolgiDg0
        if (hh != null && momSseum && MomSensor.dollyeo && d0 != null && !bandaeMal) {
            if (dolgiWant == null) dolgiWant = chai(mok, hh)
            val w = dolgiWant
            if (w != null && abs(w) >= 45) {
                val dd = MomSensor.nujeokDol - d0
                if (dd * w < 0 && abs(dd) >= 45) {
                    bandaeMal = true
                    mal("반대쪽으로 도셨습니다. 몸을 ${sigyeGak(chai(mok, hh))}으로 돌리십시오.")
                    Girok.namgi("jeom_bandae", mapOf("dd" to dd.toInt(), "w" to w.toInt()))
                }
            }
        }
        if (hh != null) {
            if (now - dm.t > 1500 && abs(chai(mok, hh)) <= 25) {
                dolgiMok = null
                dolgiKkeut()
                mal("12시 방향 맞습니다. " + daeumMalGil())
            }
        } else if (dk != null && idx > dk && ap(dk, idx) >= 4) {
            dolgiMok = null
            mal("직진입니다. " + daeumMalGil())
        }
    }

    // MARK: 표시

    private fun ilMal(nm: String): String {
        if (nm.contains("꺾임") || nm.contains("끝")) return ""
        if (nm.contains("신호등 없는 횡단보도")) return "신호등 없는 건널목입니다"
        if (nm.contains("횡단보도") || nm.contains("건널목")) return "건널목입니다"
        if (nm.contains("계단")) return "계단입니다"
        if (nm.contains("오름턱")) return "올라서는 턱입니다"
        if (nm.contains("내림턱")) return "내려서는 턱입니다"
        if (nm.contains("점자블록 끊김")) return "점자블록이 끊깁니다"
        if (nm.contains("조심할 곳")) return "조심할 곳입니다"
        return nm + "입니다"
    }

    /** 2.21.0 꺾임 표시 이름 → 시계 방향(새 이름 「3시 방향으로 꺾임」과 옛 이름 「오른쪽으로 꺾임」 모두 읽음) */
    private fun jjok(nm: String): String = Regex("(1[0-2]|[1-9])시").find(nm)?.value
        ?: if (nm.contains("오른") || nm.contains("우회전")) "3시" else "9시"

    private fun gyedanKan(p: Pyo): String {
        val c = p.p.cnt ?: 0
        return if (c > 0 && p.p.ireum.contains("계단")) " ${c}칸입니다." else ""
    }

    private fun yego(p: Pyo, ahead: Double, seoreun: Boolean): String {
        val nm = p.p.ireum
        val apm = if (seoreun) "직진 ${georeum(ahead)} 뒤 " else (if (ahead < 3) "바로 앞 " else "${georeum(ahead)} 앞 ")
        if (nm.contains("꺾임")) {
            if (kkeokGakkai(p.i)) return ""
            return if (seoreun) "직진 ${georeum(ahead)} 뒤 ${jjok(nm)} 방향으로 꺾습니다." else "${georeum(ahead)} 앞에서 ${jjok(nm)} 방향으로 꺾습니다."
        }
        if (nm.contains("끝")) return ""
        val m = ilMal(nm)
        if (m.isEmpty()) return ""
        var t = apm + m + "." + gyedanKan(p)
        val h = p.p.mal
        if (!h.isNullOrEmpty() && !seoreun) t += " " + h
        return t
    }

    private fun jigeumPyoMal(p: Pyo): String {
        val nm = p.p.ireum
        if (nm.contains("꺾임")) {
            if (kkeokGakkai(p.i)) return ""
            dolgi(8.0)
            return "지금 ${jjok(nm)} 방향으로 도십시오."
        }
        if (nm.contains("건너기 끝") || nm.contains("횡단보도 끝") || nm.contains("건널목 끝")) {
            if (!geonneolOn) return ""
            geonneolOn = false; geonneolI = -1; S.gidarim = false
            return "다 건넜습니다. " + daeumMalGil()
        }
        if (nm.contains("계단 끝")) return "계단 끝입니다. " + daeumMalGil()
        if (nm.contains("횡단보도") || nm.contains("건널목")) {
            var kkeut: Pyo? = null
            for (q in pyo) {
                val n2 = q.p.ireum
                if (!(n2.contains("건너기 끝") || n2.contains("횡단보도 끝") || n2.contains("건널목 끝"))) continue
                val kk = kkeut
                if (q.i > p.i && ap(p.i, q.i) <= 60 && (kk == null || q.i < kk.i)) kkeut = q
            }
            val kt = kkeut
            val gil2 = if (kt != null) ap(p.i, kt.i) else (p.p.dist ?: 0.0)
            var bangM = ""
            val h = jigeumHead
            val la = p.p.lat
            val lo = p.p.lon
            if (kt != null && h != null && la != null && lo != null) {
                val kla = kt.p.lat
                val klo = kt.p.lon
                if (kla != null && klo != null) bangM = sigyeGak(chai(Jeomjido.bangwi(la, lo, kla, klo), h)) + "으로 "
            }
            S.gidarim = true; S.gidarimSu = 0
            geonneolOn = true
            if (kt == null) { geonneolI = p.i; geonneolLen = if (gil2 > 0) gil2 else 12.0 }
            val jeol = if (nm.contains("신호등 없는")) "신호등 없는 건널목입니다. 멈추십시오. 차 소리를 잘 들으십시오. "
                else "건널목입니다. 멈추십시오. 신호등 상황을 확인하십시오. 길눈은 신호 색을 알지 못합니다. "
            val dwi = if (gil2 > 0) "건널목은 $bangM${georeum(gil2)}입니다." else "다 건너시면 알려 드립니다."
            mal(jeol + dwi, MalGeup.GYEONGGO)   // 건널목은 끌 수 없는 경고로
            return ""
        }
        if (nm.contains("계단")) { val c = p.p.cnt ?: 0; return "계단 시작입니다." + (if (c > 0) " ${c}칸입니다." else "") }
        if (nm.contains("오름턱")) return "지금 올라서는 턱입니다."
        if (nm.contains("내림턱")) return "지금 내려서는 턱입니다."
        if (nm.contains("점자블록 끊김")) return "여기서 점자블록이 끊깁니다."
        if (nm.contains("조심할 곳")) return "지금 조심할 곳입니다. 천천히 가십시오."
        if (nm.contains("꺾임")) return "지금 ${jjok(nm)} 방향으로 꺾으십시오."
        val h = p.p.mal ?: ""
        return "지금 ${nm}입니다." + (if (h.isEmpty()) "" else " $h")
    }

    // MARK: 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 탈것 구간

    /** 표시 짝(탐·내림)으로, 없으면 자봉이 탈것 이름(m)을 단 점이 이어진 곳으로 구간을 찾음 */
    private fun tagiChatgi(): List<Tagi> {
        val out = ArrayList<Tagi>()
        for (kind in listOf("지하철", "버스")) {
            val sseun = HashSet<Int>()
            for ((a, t) in pyo.withIndex()) {
                if (!t.p.ireum.contains("$kind 탐")) continue
                var e: Int? = null
                for ((b, q) in pyo.withIndex()) {
                    if (b == a || sseun.contains(b) || q.i < t.i || !q.p.ireum.contains("$kind 내림")) continue
                    val ee = e
                    if (ee == null || q.i < pyo[ee].i) e = b
                }
                val eb = e ?: continue
                sseun.add(eb)
                val n = pyo[eb]
                out.add(Tagi(t.i, n.i, kind, (n.p.mal ?: "").trim(), n.p.lat ?: pts[n.i].lat, n.p.lon ?: pts[n.i].lon))
            }
        }
        // 표시 짝이 없는 탈것 점 — 이어진 동안을 한 구간으로(에스컬레이터는 짧아 그대로 걸음 안내)
        var k = 0
        while (k < pts.size) {
            val m = pts[k].m ?: ""
            if (m != "지하철" && m != "버스") { k += 1; continue }
            var e = k
            while (e + 1 < pts.size && (pts[e + 1].m ?: "") == m) e += 1
            val i0 = max(0, k - 1)
            val i1 = min(pts.size - 1, e + 1)
            if (i1 > i0 && out.none { i0 <= it.i1 && i1 >= it.i0 }) out.add(Tagi(i0, i1, m, "", pts[i1].lat, pts[i1].lon))
            k = e + 1
        }
        return out.sortedBy { it.i0 }
    }

    private fun tagiGeori(m: Double): String =
        if (m >= 1000) String.format(Locale.US, "%.1f킬로미터", m / 1000) else "${max(1, Jeomjido.bannol(m).toInt())}미터"

    private fun tagiSijak(t: Tagi) {
        tagiHan.add(t.i0)
        tagiJung = t
        // 구간 안의 표시는 걸음 안내로 말하지 않음(내림 표시까지)
        val a0 = min(t.i0, idx)
        for (q in pyo) if (q.i <= t.i1 && q.i >= a0) { q.said = true; q.near = true; q.said30 = true }
        S.gyeol = ""; S.yeop = 0.0; S.gidarim = false; S.offSu = 0; S.gpsNeomSu = 0
        geonneolOn = false; geonneolI = -1; geoMode = false; offSu = 0
        dolgiKkeut()
        val naerim = if (t.naerimMal.isEmpty()) "여기서 약 ${tagiGeori(ap(t.i0, t.i1))} 떨어진 ${t.kind} 내림 자리" else t.naerimMal
        sangMal = "${t.kind} 타고 가는 중."
        mal("${t.kind} 타는 곳입니다. 타신 뒤 내리실 곳은 ${naerim}입니다. 타고 가시는 동안은 걸음 안내를 쉽니다. 내리실 곳 가까이 오시면 다시 안내합니다.")
        Girok.namgi("jeom_tagi_sijak", mapOf("kind" to t.kind, "m" to ap(t.i0, t.i1).toInt()))
        byeonhwa?.invoke()
    }

    /** 내리실 곳에 닿았거나 「내렸습니다」 — 내림 자리부터 다시 걸음 안내 */
    private fun tagiKkeut() {
        val t = tagiJung ?: return
        tagiJung = null
        idx = t.i1
        firstFix = false
        dwiNeolge = true
        lastT = 0L; spd = 0.0; offSu = 0
        geoMode = false
        val now = System.currentTimeMillis()
        wiseongTtae = now; jariTtae = now; tikT = now
        S.gyeol = ""; S.yeop = 0.0; S.jin = null; S.jinSijak = null; S.kijun = null; S.malKijun = null
        S.segKijun = null; S.offSu = 0; S.gpsNeomSu = 0; S.sinho = now; S.wdMal = now
        for (q in pyo) if (q.i <= t.i1) { q.said = true; q.near = true; q.said30 = true }
        mal("${t.kind}에서 내리실 곳입니다. 여기서부터 다시 걸음으로 안내합니다. " + daeumMalGil())
        Girok.namgi("jeom_tagi_kkeut", mapOf("kind" to t.kind))
        byeonhwa?.invoke()
    }

    /** 「내렸습니다」 — 탈것 구간을 지나는 중이면 내림 자리부터 다시 걸음 안내. 탈것 구간이 아니면 false */
    fun naeryeotda(): Boolean {
        if (tagiJung == null) return false
        tagiKkeut()
        return true
    }

    private fun pyoBoda() {
        for (p in pyo) {
            // 지나온 것은 말하지 않음 — 위성이 앞질러 표시를 지나쳐도 8미터 안이면 지금 말함
            if (p.i < idx - 1) {
                if (!p.near && ap(p.i, idx) <= max(8.0, acc)) { p.said = true } else { p.said = true; p.near = true; continue }
            }
            val ahead = ap(idx, p.i)
            if (!p.said30 && !p.said && ahead > 0.7 * 13 && bocokSu(ahead) <= 30) {
                p.said30 = true
                val y = yego(p, ahead, true)
                if (y.isNotEmpty() && System.currentTimeMillis() - malT >= 3000) mal(y)
            }
            if (!p.said && ahead <= 7.7) {
                p.said = true
                val y = yego(p, ahead, false)
                val so = p.p.sori   // 2.23.0 목소리 따라 걷기 — 닿기 몇 걸음 앞, 길눈 안내 말 바로 뒤에 자봉 목소리 토막 한 번
                if (y.isNotEmpty()) {
                    if (so != null) { malT = System.currentTimeMillis(); Sori.mal(y, MalGeup.ANNAE) { TomakDeutgi.deutgi(ctx, so) } } else mal(y)
                } else if (so != null) TomakDeutgi.deutgi(ctx, so)
            }
            if (!p.near && ahead <= 3) {
                p.near = true
                val j = jigeumPyoMal(p)
                if (j.isNotEmpty()) mal(j)
            }
        }
    }

    // MARK: 다음 일

    private fun daeumIl(): Pair<Double, String>? {
        var bestA = 1e9
        var bestM: String? = null
        for (k in kkeoks) {
            if (k.i <= idx || k.i == dolgiMok?.k) continue
            val a = ap(idx, k.i)
            if (a < 2) continue
            if (bestM == null || a < bestA) { bestA = a; bestM = "${sigyeGak(k.d)}으로 꺾습니다" }
        }
        for (p in pyo) {
            if (p.i <= idx) continue
            val a = ap(idx, p.i)
            if (a < 2) continue
            val nm = p.p.ireum
            var m = ilMal(nm)
            if (m.isEmpty() && nm.contains("꺾임") && !kkeokGakkai(p.i)) m = "${jjok(nm)} 방향으로 꺾습니다"
            if (m.isEmpty()) continue
            if (bestM == null || a < bestA) { bestA = a; bestM = m }
        }
        val bm = bestM ?: return null
        return Pair(bestA, bm)
    }

    /** "제대로 가고 있습니다" 뒤에 붙는 말 — 서른 걸음 안에 할 일이 있으면 null(그때는 예고가 걸음 수로 말함) */
    private fun daeumMal(): String? {
        val b = daeumIl() ?: return if (bocokSu(namEun) > 30) "계속 직진하세요." else null
        if (bocokSu(b.first) <= 30) return null
        return "계속 직진하세요."
    }

    private fun daeumMalGil(): String {
        val b = daeumIl() ?: return if (bocokSu(namEun) > 30) "계속 직진하세요." else "직진 ${georeum(namEun)} 가시면 목적지입니다."
        if (b.first < 4) return "이어서 ${b.second}."
        if (bocokSu(b.first) > 30) return "계속 직진하세요."
        return "직진 ${georeum(b.first)} 뒤 ${b.second}."
    }

    // MARK: 도착

    private fun dochak(munAp: String? = null) {
        if (dochakHam) return
        // 2.7.0 이어진 길의 가운데 구간 끝 — 알리고 곧장 다음 구간으로(손을 쓰지 않게)
        val c = ctx
        if (jungganGugan && c != null) {
            sseumNamgigi(true)
            gilRaw = ""   // 다음 구간을 부르며 이 구간의 쓰임을 두 번 남기지 않게
            val nam = ieum.size - ieumIdx - 1
            ieumIdx += 1
            val da = ieum[ieumIdx]
            geumanSok(false)   // 이 구간을 닫아 다음 구간을 부르는 동안 헛도착·건너뜀이 없게
            Eum.naegi(EumJong.DORAOM)
            mal("한 구간을 마쳤습니다. 남은 구간 ${nam}개, 이어서 안내합니다.")
            Girok.namgi("jeom_ieum", mapOf("id" to da.id, "idx" to ieumIdx))
            bulleoGeotgi(c, da.id, da.dwit, ieumMok, true)
            return
        }
        dochakHam = true
        if (garikiBonaen != null) { WatchLink.garikiBonae(null); garikiBonaen = null }   // 2.5.0 도착하면 손목 가리키기 쉼
        munOn = false
        ieogaJiugi()
        sseumNamgigi(true)
        sigyeDolgo = false
        main.removeCallbacks(sigye)
        gasokKkeugi()
        Eum.naegi(EumJong.DOCHAK)
        Jindong.dochak()   // 2.7.0 폰과 워치 도착 진동(세 번)
        yeojeongDochak?.invoke()
        sangMal = if (munAp == null) "목적지에 닿았습니다." else "문 앞에 닿았습니다."
        val ap = if (ieum.isNotEmpty()) "이어진 길을 모두 걸었습니다. " else ""
        if (munAp != null) mal(ap + munAp + " 따라 걷기를 마칩니다. 되돌아가시려면 되돌아가기 단추를 누르십시오.")
        else mal(ap + "목적지에 닿았습니다. 따라 걷기를 마칩니다. 되돌아가시려면 되돌아가기 단추를 누르십시오.")
        Girok.namgi("jeom_dochak", mapOf("id" to gilRaw, "mun" to (munAp != null)))
        byeonhwa?.invoke()
    }

    // MARK: 2.7.0 문까지(웹 munJunbi — 내 문 → 여러 번 확인된 문 → 한 번 찍힌 문)

    private fun munJunbi() {
        munList = emptyList(); munSu = 0
        if (!munOn) { mun = null; munIdx = 0 }
        if (pts.size < 2 || jungganGugan) return
        val kk = pts[pts.size - 1]
        val hubo = NaeMun.mokrok.mapNotNull { m ->
            val d = Wichi.geori(kk.lat, kk.lon, m.lat, m.lon)
            if (d > 60) null else JeomMun(m.lat, m.lon, if (m.ireum.isEmpty()) "내 문" else m.ireum, "", m.bang, 0, d, m.geul ?: emptyList())
        }
        munSeugi(hubo)
        munBeon += 1
        val beon = munBeon
        Tongsin.json("mun.php", mapOf("a" to "near") + Jeomjido.jari(kk.lat, kk.lon) + mapOf("r" to "60"), 20000) { o ->
            if (o == null || beon != munBeon || gil == null) return@json
            val rows = o.optJSONArray("list") ?: o.optJSONArray("rows") ?: return@json
            val h = ArrayList(hubo)
            for (i in 0 until rows.length()) {
                val x = rows.optJSONObject(i) ?: continue
                val la = Jeomjido.su(x, "lat") ?: continue
                val lo = Jeomjido.su(x, "lon") ?: continue
                if (la == 0.0) continue
                if (h.any { Wichi.geori(it.lat, it.lon, la, lo) < 3 }) continue
                val nm = Jeomjido.gul(x, "ireum").trim()
                h.add(JeomMun(la, lo, if (nm.isEmpty()) "문" else nm, Jeomjido.gul(x, "saengMal"), Jeomjido.su(x, "bang"),
                    if ((Jeomjido.su(x, "doo") ?: 0.0) > 0) 1 else 2, Wichi.geori(kk.lat, kk.lon, la, lo)))
            }
            munSeugi(h)
        }
    }

    private fun munSeugi(h: List<JeomMun>) {
        val s = h.sortedBy { it.jarye * 1000.0 + it.d }
        munList = s
        munSu = s.size
        if (!munOn) { munIdx = 0; mun = s.firstOrNull() }
    }

    /** 문은 몇 시 방향, 몇 걸음 — 방향을 모르면 걸어 보시라고 */
    private fun munMal(MU: JeomMun): String {
        val m = me ?: return ""
        val nm = MU.ireum
        val d = Wichi.geori(m.first, m.second, MU.lat, MU.lon)
        val h = jigeumHead ?: return "${nm}까지 ${georeum(d)}입니다. 폰을 앞으로 든 채 한두 걸음 걸으시면 방향을 알려 드립니다."
        var t = "$nm${MalHagi.eun(nm)} ${sigyeGak(chai(Jeomjido.bangwi(m.first, m.second, MU.lat, MU.lon), h))}, ${georeum(d)}입니다."
        val b = MU.bang
        if (b != null) t += " 들어가는 쪽은 ${sigyeGak(chai(b, h))}입니다."
        return t
    }

    /** 다른 문으로 */
    fun dareunMun() {
        if (!munOn || munList.size <= 1) { mal("다른 문이 없습니다."); return }
        munIdx = (munIdx + 1) % munList.size
        val MU = munList[munIdx]
        mun = MU
        munGakkaum = null
        mal("다른 문으로 바꿉니다. " + munMal(MU))
    }

    // MARK: 2.7.0 이 길목은 어떻게 생겼습니까, 가까운 버스 정류장, 지나는 곳

    fun gilmok() {
        val jj = jigeumJari() ?: run { mal("지금 자리를 잡는 중입니다. 잠시만 기다려 주십시오."); return }
        mal("길목을 살펴보는 중입니다.", MalGeup.JEONGBO)
        val h = Jeomjido.bannol(jigeumHead ?: 0.0).toInt()
        Tongsin.json("ppyeodae.php", mapOf("a" to "gakkaun") + Jeomjido.jari(jj.first, jj.second) + mapOf("head" to h.toString()), 20000) { o ->
            if (o == null || !o.optBoolean("ok", false)) { mal("길목을 살펴보지 못했습니다."); return@json }
            mal(Jeomjido.gul(o, "mal") + " " + Jeomjido.gul(o, "aljjik"))
        }
    }

    fun beoseuJeongryujang() {
        val jj = jigeumJari() ?: run { mal("지금 자리를 잡는 중입니다. 잠시만 기다려 주십시오."); return }
        mal("가까운 버스 정류장을 찾는 중입니다.", MalGeup.JEONGBO)
        val h = Jeomjido.bannol(jigeumHead ?: 0.0).toInt()
        Tongsin.json("beoseu.php", mapOf("a" to "gakkaun") + Jeomjido.jari(jj.first, jj.second) + mapOf("head" to h.toString(), "myeot" to "3"), 20000) { o ->
            if (o == null || !o.optBoolean("ok", false)) { mal("정류장을 찾지 못했습니다."); return@json }
            mal(Jeomjido.gul(o, "mal") + " " + Jeomjido.gul(o, "aljjik"))
        }
    }

    private fun jigeumJari(): Pair<Double, Double>? {
        me?.let { return it }
        val w = Wichi.jigeum ?: return null
        return Pair(w.lat, w.lon)
    }

    private fun juwiMalhagi(la: Double, lo: Double) {
        if (!JeomSeol.gilOn) return
        val now = System.currentTimeMillis()
        val cc = juwiCenter
        val pilyo = if (cc == null) true else Wichi.geori(cc.first, cc.second, la, lo) > juwiBan * 0.6
        if ((pilyo || juwiRows.isEmpty()) && !juwiBadneun && now - juwiBatT > 15000) {
            juwiBadneun = true
            juwiBatT = now
            var c = Pair(la, lo)
            var r = 300.0
            val e = pts.lastOrNull()
            if (e != null) {
                c = Pair((la + e.lat) / 2, (lo + e.lon) / 2)
                r = min(900.0, max(150.0, Jeomjido.bannol(Wichi.geori(la, lo, e.lat, e.lon) / 2) + 150))
            }
            juwiCenter = c
            juwiBan = r
            val sd = sedae
            Tongsin.json("juwi2.php", Jeomjido.jari(c.first, c.second) + mapOf("ban" to r.toInt().toString()), 20000) { o ->
                juwiBadneun = false
                if (sd != sedae) return@json
                val rows = o?.optJSONArray("rows") ?: return@json
                val l = ArrayList<JuwiJul>()
                for (i in 0 until rows.length()) {
                    val x = rows.optJSONObject(i) ?: continue
                    val a = Jeomjido.su(x, "lat") ?: continue
                    val b = Jeomjido.su(x, "lon") ?: continue
                    val wv = x.opt("wi")
                    val wi = if (wv is Boolean) wv else ((Jeomjido.su(x, "wi") ?: 0.0) > 0)
                    l.add(JuwiJul(Jeomjido.gul(x, "ireum"), Jeomjido.gul(x, "jong"), a, b, wi))
                }
                juwiRows = l
            }
        }
        if (juwiRows.isEmpty() || now - juwiEonje < 8000 || now - malT < 3000) return
        val jj = juwiJari
        if (jj != null && Wichi.geori(jj.first, jj.second, la, lo) < 15) return
        val head = jigeumHead
        val wi = ArrayList<Pair<String, String>>()
        val ap = ArrayList<Pair<String, String>>()
        val oreun = ArrayList<Pair<String, String>>()
        val oen = ArrayList<Pair<String, String>>()
        val gakkai = ArrayList<Pair<String, String>>()
        for (r in juwiRows) {
            val key = "${r.ireum}|${r.lat}|${r.lon}"
            if (juwiHan.contains(key)) continue
            if (Wichi.geori(la, lo, r.lat, r.lon) > 25) continue
            var rel: Double? = null
            if (head != null) {
                val x = chai(Jeomjido.bangwi(la, lo, r.lat, r.lon), head)
                if (abs(x) > 150) continue   // 뒤에 있는 것은 말하지 않음
                rel = x
            }
            juwiHan.add(key)
            val nm = juwiIreum(r.ireum)
            val rx = rel
            if (r.wi) wi.add(Pair(nm, r.jong))
            else if (rx != null) { if (abs(rx) <= 30) ap.add(Pair(nm, r.jong)) else if (rx > 0) oreun.add(Pair(nm, r.jong)) else oen.add(Pair(nm, r.jong)) }
            else gakkai.add(Pair(nm, r.jong))
        }
        val jul = ArrayList<String>()
        if (wi.isNotEmpty()) jul.add("조심하십시오. " + juwiMukkgi(wi) + "입니다.")
        if (oreun.isNotEmpty()) jul.add("3시 방향에 " + juwiMukkgi(oreun) + "입니다.")
        if (oen.isNotEmpty()) jul.add("9시 방향에 " + juwiMukkgi(oen) + "입니다.")
        if (ap.isNotEmpty()) jul.add("앞에 " + juwiMukkgi(ap) + "입니다.")
        if (gakkai.isNotEmpty()) jul.add("가까이 " + juwiMukkgi(gakkai) + "입니다.")
        if (jul.isEmpty()) return
        juwiEonje = now
        juwiJari = Pair(la, lo)
        mal(jul.joinToString(" "), MalGeup.JEONGBO)
    }

    /** 한글 이름 뒤에 붙은 영문 이름은 뗌 */
    fun juwiIreum(nm: String): String {
        val t = nm.trim()
        if (!Regex("[가-힣]").containsMatchIn(t)) return t
        val x = t.replace(Regex("\\s+[A-Za-z][A-Za-z0-9 .,'&\\-]*$"), "")
        return if (x.isEmpty()) t else x
    }

    /** 같은 갈래끼리 묶어 — "식당 3곳입니다. 가, 나, 다" */
    fun juwiMukkgi(l: List<Pair<String, String>>): String {
        val cha = ArrayList<String>()
        val moum = HashMap<String, ArrayList<String>>()
        for ((nm, jong) in l) {
            if (!moum.containsKey(jong)) { cha.add(jong); moum[jong] = ArrayList() }
            moum[jong]?.add(nm)
        }
        return cha.joinToString(". ") { k ->
            val nn = moum[k] ?: arrayListOf()
            if (nn.size == 1) (if (k.isEmpty()) nn[0] else k + " " + nn[0])
            else (if (k.isEmpty()) "" else "$k ") + "${nn.size}곳입니다. " + nn.joinToString(", ")
        }
    }

    // MARK: 2.7.0 앱이 꺼졌다 켜져도 이어 걷기 — 걷던 길을 폰에 적어 둠(3시간 안이면 이어 감)

    private fun ieogaJeojang(id: String, dw: Boolean, mok: JeomMokjeok?) {
        val o = JSONObject().put("id", id).put("dwit", dw).put("ieumIdx", ieumIdx).put("ttae", System.currentTimeMillis())
        (mok ?: ieumMok)?.let { o.put("mok", it.json()) }
        val a = JSONArray()
        for (g in ieum) a.put(g.json())
        o.put("ieum", a)
        JeomSeol.geulSseugi("jeomIeoga", o.toString())
    }

    private fun ieogaJiugi() { JeomSeol.geulSseugi("jeomIeoga", null) }

    /** 앱이 켜질 때 — 3시간 안에 걷던 점지도가 있으면 그 길로 이어 감. 이어 가면 참(화면이 걷는 화면을 열도록) */
    fun ieoGagi(c: Context): Boolean {
        junbi(c)
        val s = JeomSeol.geul("jeomIeoga") ?: return false
        val o = try { JSONObject(s) } catch (e: Exception) { ieogaJiugi(); return false }
        val id = Jeomjido.gul(o, "id")
        if (id.isEmpty() || System.currentTimeMillis() - o.optLong("ttae", 0L) >= 3 * 3600 * 1000L) { ieogaJiugi(); return false }
        val mok = JeomMokjeok.batgi(o.optJSONObject("mok"))
        // 아이폰 2.12.6 걷던 점지도의 목적지가 지금 여정의 목적지와 다르면 잇지 않음(옛 길이 새 목적지를 덮어쓰던 것)
        val y = yeojeongMok?.invoke()
        if (mok != null && y != null && Wichi.geori(mok.lat, mok.lon, y.lat, y.lon) > 50) {
            ieogaJiugi()
            Girok.namgi("jeom_ieoga_an", mapOf("id" to id))
            return false
        }
        val l = ArrayList<JeomGugan>()
        val a = o.optJSONArray("ieum")
        if (a != null) for (i in 0 until a.length()) JeomGugan.batgi(a.optJSONObject(i))?.let { l.add(it) }
        ieum = l
        ieumIdx = min(o.optInt("ieumIdx", 0), max(0, l.size - 1))
        ieumMok = mok
        Girok.namgi("jeom_ieoga", mapOf("id" to id))
        Sori.mal("하던 점지도 따라 걷기를 이어 갑니다.")
        bulleoGeotgi(c, id, o.optBoolean("dwit", false), mok, true)
        return true
    }

    // MARK: 2.7.0 함께 시험하기(곁의 자봉이 번호로 따라 봄)

    fun hamkkeNureum() {
        val b0 = hamkkeBunho
        if (b0 != null) {
            hamkkeKkeut(b0)
            hamkkeBunho = null
            mal("함께 시험을 마쳤습니다.")
            byeonhwa?.invoke()
            return
        }
        val g = gil ?: run { mal("먼저 걸으실 길을 골라 따라 걷기를 시작해 주십시오. 그 뒤에 번호를 받으실 수 있습니다."); return }
        val id = gilRaw.replace("|r", "")
        val mok = yeojeongMok?.invoke()?.ireum ?: g.to
        val body = JSONObject().put("gil", id).put("dwit", if (dwit) 1 else 0).put("mok", mok)
        val sd = sedae
        Jeomjido.postJson("hamkke.php", mapOf("a" to "yeol"), body) { o ->
            if (sd != sedae || gil == null) {   // 그사이 그만두셨으면 받은 번호를 닫음
                if (o != null && o.optBoolean("ok", false)) hamkkeKkeut(Jeomjido.gul(o, "bunho"))
                return@postJson
            }
            if (o == null || !o.optBoolean("ok", false)) { mal("번호를 받지 못했습니다. 잠시 뒤에 다시 눌러 주십시오."); return@postJson }
            val b = Jeomjido.gul(o, "bunho")
            hamkkeBunho = b
            hamkkeT = 0L
            mal("함께 시험 번호는 ${b.toList().joinToString(" ")}입니다. 곁의 자봉께 알려 주십시오.")
            byeonhwa?.invoke()
        }
    }

    private fun hamkkeKkeut(b: String) {
        Jeomjido.postJson("hamkke.php", mapOf("a" to "kkeut"), JSONObject().put("bunho", b)) { }
    }

    private fun hamkkeBonaegi() {
        val b = hamkkeBunho ?: return
        val now = System.currentTimeMillis()
        if (now - hamkkeT < 2000 || pts.isEmpty()) return
        hamkkeT = now
        var mi = -1
        var mn = ""
        for (p in pyo) if (p.i >= idx && (mi < 0 || p.i < mi)) { mi = p.i; mn = p.p.ireum }
        if (mi < 0) { mi = pts.size - 1; mn = "도착" }
        val body = JSONObject().put("bunho", b).put("tc", now).put("idx", idx).put("mi", mi).put("mn", mn)
            .put("ws", ap(idx, mi)).put("su", S.stepSu).put("bo", bocok).put("acc", acc)
            .put("mal", Sori.majimak.take(100))
        Jeomjido.postJson("hamkke.php", mapOf("a" to "sang"), body) { }
    }

    // MARK: 걸린 자리(geollim.js) — 다른 분이 벗어났던 자리를 미리 알림

    private fun geollimSalpigi(la: Double, lo: Double) {
        val j = geollimJari
        if (j != null && Wichi.geori(j.first, j.second, la, lo) < 30) return
        geollimJari = Pair(la, lo)
        val sd = sedae
        Tongsin.json("makhim.php", Jeomjido.jari(la, lo) + mapOf("ban" to "120")) { o ->
            val x = o?.optJSONArray("rows")?.optJSONObject(0) ?: return@json
            val m = (Jeomjido.su(x, "meter") ?: 999.0).toInt()
            if (m > 60) return@json
            val key = String.format(Locale.US, "%.4f_%.4f", Jeomjido.su(x, "lat") ?: 0.0, Jeomjido.su(x, "lon") ?: 0.0)
            if (sd != sedae || gil == null || dochakHam || geollimHan.contains(key) || System.currentTimeMillis() - geollimT < 15000) return@json
            geollimHan.add(key)
            geollimT = System.currentTimeMillis()
            mal("${m}미터 앞. " + Jeomjido.gul(x, "mal"))
        }
    }

    /** 2.7.0 여기 걸렸어요 — 다음 분께 알려 주기 */
    fun geollimNamgigi() {
        val w = Wichi.jigeum ?: run { mal("지금 자리를 잡는 중입니다. 잠시 뒤에 다시 눌러 주십시오."); return }
        Tongsin.json("jeom_db.php", mapOf("a" to "makhim", "kind" to "beoseonam") + Jeomjido.jari(w.lat, w.lon), 20000) { o ->
            if (o != null && o.optBoolean("ok", false)) mal("여기가 걸리는 자리라고 남겼습니다. 다음에 오시는 분께 미리 알려 드리겠습니다. 고맙습니다.")
            else mal("남기지 못했습니다. 잠시 뒤에 다시 해 주십시오.")
        }
    }

    private fun makhimNamgigi(la: Double, lo: Double, d: Double) {
        val now = System.currentTimeMillis()
        if (now - makhimT < 30000) return
        makhimT = now
        Tongsin.json("jeom_db.php", mapOf("a" to "makhim", "kind" to "beoseonam") + Jeomjido.jari(la, lo) + mapOf("dist" to Jeomjido.bannol(d).toInt().toString())) { }
    }

    private fun sseumNamgigi(kkeut: Boolean) {
        if (gilRaw.isEmpty() || gilRaw.startsWith("nae_")) return
        val secs = ((System.currentTimeMillis() - sijakT) / 1000).toInt()
        var q = mapOf("a" to "sseum", "gil" to gilRaw, "kind" to "sigak", "secs" to secs.toString(),
            "off" to beoseoSu.toString(), "kkeut" to (if (kkeut) "1" else "0"))
        me?.let { q = q + Jeomjido.jari(it.first, it.second) }
        Tongsin.json("jeom_db.php", q) { }
    }

    /** 함께 시험을 마쳐 확인된 점지도인가(hwakin_gil.json) — 못 받으면 확인 중인 길로 봄 */
    private fun hwakinDoen(id: String, kkeut: (Boolean) -> Unit) {
        Tongsin.json("hwakin_gil.json", emptyMap(), 4000) { o ->
            val a = o?.optJSONArray("gil")
            var doem = false
            if (a != null) for (i in 0 until a.length()) {
                val s = when (val v = a.opt(i)) { is String -> v; is Number -> v.toString(); else -> "" }
                if (s == id) { doem = true; break }
            }
            kkeut(doem)
        }
    }

    // MARK: 셈과 말

    private fun mal(t: String, g: MalGeup = MalGeup.ANNAE) {
        Sori.mal(t, g)
        malT = System.currentTimeMillis()
    }

    /** 확신음 — 말하는 동안에는 쉬어 말과 겹치지 않게(벗어남 경고음은 늘). 2.7.0 아이폰과 같이 확신음 끄기(JeomSeol.hwaksinEum) */
    private fun eum(j: EumJong) {
        if (!JeomSeol.hwaksinEum) return
        if (j != EumJong.BEOSEO && Sori.malhaneunJung) return
        Eum.naegi(j)
    }

    private fun chai(a: Double, b: Double): Double = (a - b + 540) % 360 - 180

    private fun gakkaunJeom(la: Double, lo: Double): Int {
        var b = 0
        var bd = 1e9
        for ((k, p) in pts.withIndex()) {
            val d = Wichi.geori(la, lo, p.lat, p.lon)
            if (d < bd) { bd = d; b = k }
        }
        return b
    }

    /** 점지도를 따라 앞으로 몇 미터(from 점에서 to 점까지) */
    private fun ap(from: Int, to: Int): Double {
        if (to <= from || from < 0 || to >= nu.size) return 0.0
        return nu[to] - nu[from]
    }

    private fun bocokSu(m: Double): Int = max(1, Jeomjido.bannol(m / bocok).toInt())

    /** 미터 → "스무 걸음", 아흔아홉 걸음이 넘으면 "약 80미터" */
    private fun georeum(m: Double): String {
        // 2.18.0 보폭을 재기 전에는 걸음 수 대신 미터로(틀린 걸음 수보다 안전, 이사장님 승인)
        if (!Seoljeong.bopokJaem) return "약 ${max(1, Math.round(m).toInt())}미터"
        val n = bocokSu(m)
        val g = goyu(n)
        if (g != null) return "$g 걸음"
        return "약 ${Jeomjido.bannol(m).toInt()}미터"
    }

    fun goyu(n0: Int): String? {
        val n = max(1, n0)
        if (n > 99) return null
        val il = listOf("", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉")
        val sip = listOf("", "열", "스물", "서른", "마흔", "쉰", "예순", "일흔", "여든", "아흔")
        val s = n / 10
        val i = n % 10
        if (s == 2 && i == 0) return "스무"
        return sip[s] + il[i]
    }

    fun georeumSu(n: Int): String = if (n <= 10) (goyu(n) ?: "$n") + " 걸음" else "${n}걸음"

    private fun georiMal(m: Double): String {
        if (m < 3) return "바로 앞"
        return "${Jeomjido.bannol(m).toInt()}미터, 약 ${bocokSu(m)}걸음"
    }

    /** 2.7.0 돌 쪽 각도를 시계 숫자로(진동 무늬 고르기, 아이폰 2.38.0 sigyeSu) */
    private fun sigyeSu(d: Double): Int {
        var h = Jeomjido.bannol(d / 30).toInt()
        if (h <= 0) h += 12
        if (h > 12) h -= 12
        if (abs(d) > 150) h = 6
        return h
    }

    /** 꺾는 각도(오른쪽이 +) → "3시 방향" */
    private fun sigyeGak(d: Double): String {
        var h = Jeomjido.bannol(d / 30).toInt()
        if (h <= 0) h += 12
        if (h > 12) h -= 12
        if (abs(d) > 150) h = 6
        return "${h}시 방향"
    }

    private fun siMal(la: Double, lo: Double): String {
        val h = jigeumHead ?: return ""
        val m = me ?: return ""
        return ", ${Jeomjido.sigyeBanghyang(h, Jeomjido.bangwi(m.first, m.second, la, lo))}시 방향"
    }
}
