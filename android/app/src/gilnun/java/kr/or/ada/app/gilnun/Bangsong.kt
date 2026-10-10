// 안드로이드 길눈 — 음악·방송 엔진(b5_bangsong, 아이폰 Bangsong.swift 2.8.0~2.30.0과 같은 설계도, 같은 말, 같은 나스 창고)
// 길 위의 음악(나스 음악 eumak.php·누구나 음악 nuguna), 라디오·TV(bfblive/sori.php), 지금 세상 이야기(sesang.php 원문 전체 읽기)를 한 곳에서 틉니다.
// ① 화면을 옮겨도, 폰을 잠가도, 길 찾기 탭에서 안내를 받아도 끊기지 않음 — 재생기(미디어3 ExoPlayer)는 이 엔진이 쥐고,
//    알림 칸의 길눈 방송(BangsongService, 미디어 세션 포그라운드 서비스)이 화면이 꺼져도 살려 둠
// ② 길눈 안내 말이 나오면 방송 소리를 작게 줄였다가 끝나면 되돌림, 경고만 멈췄다가 말하고 다시 틂 — Sori 가 BangsongDuck 을 부름
// ③ 말로 하기가 명령을 듣는 동안과 긴급통화 동안에는 잠시 멈춤(0.5초마다 살핌 + Sori 갈고리)
// ④ 끊기거나 16초 넘게 소리가 나지 않으면 저절로 다시 이음(라디오·TV는 새 주소를 받아). 여섯 번 잇달아 안 되면 멈추고 한 번만 알림
// ⑤ 이어폰·알림 칸의 재생·다음·이전 단추가 방송을 다룸(미디어 세션 — 아이폰 RemoteDanchu 방송 갈래와 같음)
// ⑥ TV 영상은 버리지 않음(대표님 원칙) — 영상 끄기를 켜실 때만 가장 낮은 화질로 받고 영상 길을 끔
package kr.or.ada.app.gilnun

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

enum class BangsongJong(val ireum: String) {
    EOPSEUM("eopseum"), EUMAK("eumak"), NUGUNA("nuguna"), RADIO("radio"), TV("tv"), GISA("gisa")
}

class EumakGok(val f: String, val ireum: String, val s: String = "")

class NugunaGok(val sn: String, val ireum: String, val jakgok: String, val jeojakja: String, val jogeon: String, val bun: String, val mp3: String) {
    val pyosi: String
        get() = "저작자 $jeojakja" + (if (jakgok.isEmpty()) "" else ", 원곡 $jakgok") + ", 출처 공유마당(한국저작권위원회), 이용 조건 $jogeon"
}

class Chaeneol(val id: String, val name: String, val freq: String, val kind: String) {
    val julMal: String get() = if (freq.isEmpty()) name else "$name $freq"
}

class Gisa(val saem: String, val jemok: String, val yoyak: String, val juso: String, val ttaeMal: String, val sokbo: Boolean) {
    val julMal: String get() = jemok + (if (ttaeMal.isEmpty()) "" else ", $saem $ttaeMal")
}

/** 2.13.0 기분과 날씨에 맞춰 틀기 — 아이폰 BangsongEngine.Gibun 과 같은 일곱 갈래, 같은 말 */
enum class BangsongGibun(val danchu: String, val apMal: String) {
    JEONHWAN("기분 전환 — 가라앉을 때 신나는 곡", "기분이 가라앉으셨군요. 기분을 바꿔 줄 신나는 곡으로 골라 드리겠습니다."),
    HEUNG("흥겹게 — 기분 좋을 때", "기분이 좋으시군요. 흥을 돋울 곡으로 골라 드리겠습니다."),
    CHABUN("차분하게 — 쉬고 싶을 때", "차분하게 쉬고 싶으시군요. 잔잔한 곡으로 골라 드리겠습니다."),
    JAM("잠들기 전에 — 밤에 어울리는 곡", "편히 주무시도록 밤에 어울리는 곡으로 골라 드리겠습니다."),
    SSEULSSEUL("마음을 달래 — 쓸쓸할 때", "마음이 쓸쓸하시군요. 마음을 달래 줄 곡으로 골라 드리겠습니다."),
    SEOLLEM("분위기 있게 — 설렐 때", "설레는 기분이시군요. 분위기 있는 곡으로 골라 드리겠습니다."),
    NALSSI("지금 날씨에 맞게", "");

    /** 열쇠가 없을 때 누구나 음악의 어느 쪽 */
    val nugunaBun: String get() = if (this == JEONHWAN || this == HEUNG) "bal" else "jan"
}

/** 음악·방송 설정 — 폰에 담아 둠(아이폰 Seoljeong 의 방송 갈래와 Yeolsoe eumakTk 자리) */
object BangsongSeol {
    private var d: SharedPreferences? = null
    private var gilnunD: SharedPreferences? = null   // 길눈 설정 꾸러미 "gilnun" — 다른 묶음(카메라 관리자·말로 그린 길)도 eumakTk 를 여기서 봄
    fun sijak(ctx: Context) {
        if (d == null) d = ctx.applicationContext.getSharedPreferences("gilnun_bangsong", Context.MODE_PRIVATE)
        if (gilnunD == null) gilnunD = ctx.applicationContext.getSharedPreferences("gilnun", Context.MODE_PRIVATE)
        Yeolsoe.sijak(ctx)
        // 2.33.0 (261010-A21, 이사장님 승인 2026-10-10) 예전에 켜짐으로 저장된 폰이 그대로 켜져 있던 일 — 이 판을 처음 열 때 한 번만
        //   차에 타면 고장 노래 저절로 틀기를 끔으로 되돌림(그 뒤 켜시는 것은 지킴, 아이폰과 같음)
        val dd = d
        if (dd != null && !dd.getBoolean("gojangNorae_dolim261010", false)) {
            dd.edit().putBoolean("gojangNorae_dolim261010", true).putBoolean("gojangNorae", false).apply()
        }
    }

    private fun b(k: String, gibon: Boolean) = d?.getBoolean(k, gibon) ?: gibon
    private fun s(k: String) = d?.getString(k, "") ?: ""
    private fun bNoki(k: String, v: Boolean) { d?.edit()?.putBoolean(k, v)?.apply() }
    private fun sNoki(k: String, v: String) { d?.edit()?.putString(k, v)?.apply() }

    /** TV 영상 끄기 — 소리만 듣고 데이터 아끼기(처음엔 꺼짐: 영상이 나옴) */
    var yeongsangKkeum: Boolean
        get() = b("yeongsangKkeum", false)
        set(v) = bNoki("yeongsangKkeum", v)
    /** 2.12.7 차에 타면 지나는 고장 노래 저절로(처음부터 켜짐) */
    var gojangNorae: Boolean
        get() = b("gojangNorae", false)   // 2.28.0 처음값 끔(이사장님 지시, 아이폰 2.57.0과 같음)
        set(v) = bNoki("gojangNorae", v)
    /** 2.30.0 통화가 끝난 뒤 방송을 저절로 이어 들음(처음엔 꺼짐 — 맨 위 「방송 이어 듣기」 단추로 이어 들으심, 이사장님 승인) */
    var tonghwaDwiIeum: Boolean
        get() = b("tonghwaDwiIeum", false)
        set(v) = bNoki("tonghwaDwiIeum", v)
    /** 2.34.0 워치가 있으면 워치에서 동영상 틀기(처음부터 켜짐) */
    var dongyeongWatch: Boolean
        get() = b("dongyeongWatch", true)
        set(v) = bNoki("dongyeongWatch", v)
    /** 기사 읽어 줄 목소리 이름(빈 글이면 폰 기본 한국어 목소리) */
    var gisaMoksori: String
        get() = s("gisaMoksori")
        set(v) = sNoki("gisaMoksori", v)
    /** 기사 말하는 빠르기 0~4 */
    var gisaBbareugiDan: Int
        get() = (d?.getInt("gisaBbareugi", 2) ?: 2).coerceIn(0, 4)
        set(v) { d?.edit()?.putInt("gisaBbareugi", v.coerceIn(0, 4))?.apply() }
    /** 나스 음악 열쇠(표) — 아이폰 Yeolsoe eumakTk 하나와 같게, 두 꾸러미에 함께 적고 어느 쪽에 있든 읽음 */
    var eumakTk: String
        get() = Yeolsoe.eumakTk()   // 2.7.0 통합 — 한 도우미로("gilnun" 먼저, 옛 "gilnun_bangsong"도)
        set(v) = Yeolsoe.eumakTkNoki(null, v)
    var majimakRadio: String
        get() = s("majimakRadio")
        set(v) = sNoki("majimakRadio", v)
    var majimakTv: String
        get() = s("majimakTv")
        set(v) = sNoki("majimakTv", v)
    /** 2.34.0 마지막으로 받은 동영상(파일 길이나 주소) */
    var dongyeongMajimak: String
        get() = s("dongyeongMajimak")
        set(v) = sNoki("dongyeongMajimak", v)
}

/** 길눈 말소리와 방송을 잇는 갈고리 — Sori.kt 가 부름(통합 쪽지 b5_bangsong.md) */
object BangsongDuck {
    private val main = Handler(Looper.getMainLooper())
    private fun mainEseo(f: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) f() else main.post(f) }

    // 2.27.0 다른 앱 음악(멜론·유튜브 뮤직 등)을 들으며 쓸 때 — 길눈이 말하는 동안만 그 소리를 작게 했다가 말이 끝나면 되돌림(아이폰과 같음).
    //   길눈 제 방송(길 위의 음악·라디오·TV)이 나오는 동안에는 지금처럼 Bangsong 이 맡고 여기서는 손대지 않음(2.24.0에 고친 것을 흔들지 않게)
    private var ctx: android.content.Context? = null
    private var focus: android.media.AudioFocusRequest? = null
    private var jamgimR: Runnable? = null
    fun sijak(c: android.content.Context) { ctx = c.applicationContext }
    private fun dareunAppJurigi() {
        if (Bangsong.itda || focus != null) return
        jamgimR?.let { main.removeCallbacks(it) }
        jamgimR = null
        if (android.os.Build.VERSION.SDK_INT < 26) return
        val am = ctx?.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
        try {
            val r = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener { }
                .build()
            if (am.requestAudioFocus(r) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { focus = r; main.postDelayed(jikimi, 1500) }
        } catch (e: Exception) {}
    }
    /** 선희 목소리처럼 끝 알림이 오지 않는 말도 있어 — 1.5초마다 살펴 조용하면 되돌림 */
    private val jikimi: Runnable = object : Runnable {
        override fun run() {
            if (focus == null) return
            if (Sori.malhaneunJung) { main.postDelayed(this, 1500); return }
            dareunAppDollyeojugi()
        }
    }
    private fun dareunAppDollyeojugi() {
        if (focus == null) return
        jamgimR?.let { main.removeCallbacks(it) }
        val r = Runnable {
            jamgimR = null
            if (Sori.malhaneunJung) return@Runnable   // 이어 말할 것이 있음
            val f = focus ?: return@Runnable
            focus = null
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                val am = ctx?.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
                try { am?.abandonAudioFocusRequest(f) } catch (e: Exception) {}
            }
        }
        jamgimR = r
        main.postDelayed(r, 500)
    }

    /** 길눈이 말을 시작함(tts.speak 바로 앞) — 보통 말은 방송을 작게, 경고는 멈춤, 기사 읽기는 쉼 */
    fun malSijak(geup: MalGeup) = mainEseo { Bangsong.malSijak(geup); dareunAppJurigi() }
    /** 길눈 말이 끝났을 수 있음(Sori 의 kkeut·meomchugi) — 0.35초 뒤 정말 조용하면 되돌림 */
    fun malKkeut() = mainEseo { Bangsong.malKkeutYeyak(); dareunAppDollyeojugi() }
    /** 말로 하기가 마이크를 열고 닫음(Sori.deutgiSijak·deutgiKkeut) */
    fun deutgi(on: Boolean) = mainEseo { Bangsong.deutgiMeomchum(on) }
    /** 부름("하이 길눈")을 들은 때부터 명령을 마칠 때까지(안드로이드 길눈에 부름이 들어오면) */
    fun bureum(on: Boolean) = mainEseo { Bangsong.bureumMeomchum(on) }
}

/** 미디어 세션이 받는 단추(이어폰·알림 칸·워치의 미디어 조절)를 엔진의 뜻으로 바꿈 */
@OptIn(UnstableApi::class)
internal class BangsongSessionPlayer(p: Player) : ForwardingPlayer(p) {
    override fun play() { Bangsong.danchuJaesaeng(true) }
    override fun pause() { Bangsong.danchuJaesaeng(false) }
    override fun setPlayWhenReady(playWhenReady: Boolean) { Bangsong.danchuJaesaeng(playWhenReady) }
    override fun seekToNext() { Bangsong.daeum() }
    override fun seekToNextMediaItem() { Bangsong.daeum() }
    override fun seekToPrevious() { Bangsong.ijeon() }
    override fun seekToPreviousMediaItem() { Bangsong.ijeon() }
    override fun stop() { Bangsong.geuman(false) }

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .addAll(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, Player.COMMAND_STOP)
            .build()

    override fun isCommandAvailable(command: Int): Boolean = when (command) {
        Player.COMMAND_PLAY_PAUSE, Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, Player.COMMAND_STOP -> true
        else -> super.isCommandAvailable(command)
    }
}

private fun JSONObject.bsGeul(k: String): String = if (isNull(k)) "" else optString(k, "")
private fun JSONObject.bsGeulMok(k: String): List<String> {
    val a = optJSONArray(k) ?: return emptyList()
    return (0 until a.length()).mapNotNull { i -> if (a.isNull(i)) null else a.optString(i, "").takeIf { it.isNotEmpty() } }
}
private fun bsBatchim(w: String): Boolean {
    val ch = w.lastOrNull() ?: return false
    val c = ch.code - 0xAC00
    if (c < 0 || c >= 11172) return false
    return c % 28 != 0
}
/** 이/가 */
internal fun bsI(w: String) = if (bsBatchim(w)) "이" else "가"

@OptIn(UnstableApi::class)
object Bangsong {
    const val PPURI = "https://lvd.ada.or.kr"
    /** 2.24.0(261007-A12, 이사장님 지적 — 안드로이드에서 방송·음악이 하나도 안 나옴) 우리 서버 문지기를 통과하는 이름표.
     *  도서관 앱·배프 BYOD 앱과 같이 브라우저 이름표를 답니다(재생기·나스 묻기 모두) */
    const val UA = "Mozilla/5.0 (Linux; Android) Gilnun/2.27.0"

    /** 2.14.0 꼭 맞는 곡이 없을 때 권한 비슷한 곡(말로 하기에서 "네" 하시면 틂, 아이폰 2.46.0과 같음) */
    @Volatile var biseutQ: String? = null
    @Volatile var biseutTtae = 0L

    private val main = Handler(Looper.getMainLooper())
    private val il = Executors.newFixedThreadPool(3)
    private var ac: Context? = null

    // MARK: 화면이 보는 것

    var jong = BangsongJong.EOPSEUM
        private set
    var jemok = ""
        private set
    var sangtaeMal = ""
        private set
    var meomchum = false
        private set
    var eumakDeureom = false
        private set
    var cheoumIra = false
        private set
    var galraeDeul: List<String> = emptyList()
        private set
    var gokMok: List<EumakGok> = emptyList()
        private set
    var gokI = -1
        private set
    var jadoKyeojim = false
        private set
    var jadoJul = ""
        private set
    var nugunaPyosi = ""
        private set
    var chaeneolDeul: List<Chaeneol> = emptyList()
        private set
    var chaeneolNote = ""
        private set
    var gisaMok: List<Gisa> = emptyList()
        private set
    var gisaI = -1
        private set
    var gisaMeori = ""
        private set
    var gisaBon: List<String> = emptyList()
        private set
    var gisaIlkneun = false
        private set

    /** 보이는 방송 화면이 채움 — 엔진의 형편이 바뀌면 부름(화면은 그린 것과 달라졌을 때만 다시 그림) */
    var byeonhwa: (() -> Unit)? = null
    /** 기사 화면이 채움 — 기사 화면이 지금 맨 위에 보이는가(떠나시면 읽기를 멈추려고, 아이폰 2.12.1) */
    var gisaHwamyeonBoim: (() -> Boolean)? = null

    var yeongsangKkeum: Boolean
        get() = BangsongSeol.yeongsangKkeum
        set(v) { BangsongSeol.yeongsangKkeum = v; hwajilMatchugi(); allim() }

    /** 재생기 — TV 화면(PlayerView)이 이것을 붙임 */
    var exo: ExoPlayer? = null
        private set
    internal var sessionPlayer: BangsongSessionPlayer? = null
        private set

    private val naebuMeomchum = HashSet<String>()   // gyeonggo, deutgi, bureum, tonghwa
    private var malJung = false
    private var malSijakTtae = 0L
    private var malKkeutR: Runnable? = null
    private var jigeumChaeneol: Chaeneol? = null
    private var nuguna: List<NugunaGok> = emptyList()
    private var nugunaJul: List<NugunaGok> = emptyList()
    private var nugunaI = -1
    private var jadoKey = ""
    private var jadoChaRo = false
    private var jadoR: Runnable? = null
    private var majimakJari = -1.0
    private var majimakUmjik = System.currentTimeMillis()
    private var naoneunSijak = 0L
    private var ieumSu = 0
    private var meomchunTtae = 0L
    private var seekDaegi = 0.0
    private var teulgiBeon = 0
    private var bureumIl = false
    private var gisaTteonamTtae = 0L
    // 2.24.0 소리 자리를 잃은 때·길눈 제 말 때문인가·틀기 시작한 때·소리 안 남 알림
    private var jariIlheum = 0L
    private var jariJeMal = false
    private var teulgiTtae = 0L
    private var anNaomAllim = false
    private var majimakJindan = 0L
    private var anNaomMalTtae = 0L

    private val tk: String get() = BangsongSeol.eumakTk

    /** 지금 소리가 나고 있거나 멈춰 둔 방송이 있음 */
    val itda: Boolean get() = jong != BangsongJong.EOPSEUM
    /** 지금 실제로 소리를 내는 중 */
    val naneunJung: Boolean get() = jong != BangsongJong.EOPSEUM && !meomchum && naebuMeomchum.isEmpty()

    private fun allim() { byeonhwa?.invoke() }

    // MARK: 세우기

    fun sijak(ctx: Context) {
        if (ac != null) return
        ac = ctx.applicationContext
        BangsongSeol.sijak(ctx)
        main.postDelayed(jikimi, 4000)
        main.postDelayed(salpim, 500)
    }

    private val jikimi = object : Runnable {
        override fun run() { jikigi(); main.postDelayed(this, 4000) }
    }
    private val salpim = object : Runnable {
        override fun run() { salpigi(); main.postDelayed(this, 500) }
    }

    /** 재생기를 세우고 알림 칸의 길눈 방송을 켬 */
    private fun pyeonJunbi(): ExoPlayer? {
        val c = ac ?: return null
        var p = exo
        if (p == null) {
            val ds = DefaultHttpDataSource.Factory()
                .setUserAgent(UA)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)
            p = ExoPlayer.Builder(c)
                .setMediaSourceFactory(DefaultMediaSourceFactory(c).setDataSourceFactory(ds))
                .setAudioAttributes(
                    androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(), true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build()
            p.addListener(plDeutgi)
            exo = p
            sessionPlayer = BangsongSessionPlayer(p)
        }
        BangsongService.kyeogi(c)
        return p
    }

    private val plDeutgi = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                val sk = seekDaegi
                seekDaegi = 0.0
                if (sk > 1) exo?.seekTo((sk * 1000).toLong())
                if (sangtaeMal.isNotEmpty()) { sangtaeMal = ""; allim() }
            } else if (playbackState == Player.STATE_ENDED) {
                kkeunnam()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val beon = teulgiBeon
            Girok.namgi("bangsong_oryu", mapOf("dan" to "item", "jong" to jong.ireum, "code" to error.errorCode))
            main.postDelayed({ if (beon == teulgiBeon) dasiIeum() }, 2000)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady || !itda || meomchum) return
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) {
                // 이어폰이 빠짐 — 남에게 소리가 새지 않게 멈춘 채로(이용자가 다시 틀기)
                meomchum = true
                meomchunTtae = System.currentTimeMillis()
                allim()
            } else if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
                // 2.24.0 고침(이사장님 지적) — 갤럭시는 길눈 제 목소리(「○○에 잇는 중입니다」 등)가 소리 자리를 통째로 가져가
                // 방송이 멈춘 채로 묶였음(오류도 다시 잇기도 없이 「나온다」고만 함). 이제 길눈 제 말 때문이면 말이 끝난 뒤 되찾고,
                // 정말 다른 앱(전화·다른 음악)이 3초 넘게 가져간 때에만 멈춘 채로 둡니다.
                jariIlheum = System.currentTimeMillis()
                jariJeMal = Sori.malhaneunJung || malJung || System.currentTimeMillis() - teulgiTtae < 15000
                Girok.namgi("bangsong_jari", mapOf("jong" to jong.ireum, "jemal" to jariJeMal))
            }
        }
    }

    // MARK: 길눈 말과 함께(가1)

    internal fun malSijak(geup: MalGeup) {
        if (!itda) return
        malKkeutR?.let { main.removeCallbacks(it) }
        malKkeutR = null
        malJung = true
        malSijakTtae = System.currentTimeMillis()
        if (geup == MalGeup.GYEONGGO) {
            naebu("gyeonggo", true)
        } else if (jong == BangsongJong.GISA) {
            gisaSwigi()
        } else {
            exo?.volume = 0.2f
        }
    }

    internal fun malKkeutYeyak() {
        if (!malJung) return
        malKkeutR?.let { main.removeCallbacks(it) }
        val r = Runnable {
            malKkeutR = null
            if (Sori.malhaneunJung) return@Runnable   // 이어 말할 것이 있음 — 그 말이 끝나면 다시 옴
            malKkeut()
        }
        malKkeutR = r
        main.postDelayed(r, 350)
    }

    private fun malKkeut() {
        if (!malJung) return
        malJung = false
        naebu("gyeonggo", false)
        exo?.volume = 1f
        if (jong == BangsongJong.GISA && gisaIlkneun && !meomchum && naebuMeomchum.isEmpty()) gisaIeoIlkgi()
    }

    /** 말로 하기가 명령을 듣는 동안 멈춤 */
    fun deutgiMeomchum(t: Boolean) = naebu("deutgi", t)

    // MARK: 2.30.0 전화(이사장님 지시 2026-10-09) — 전화 중에는 멈추고, 끝나면 저절로 틀지 않고 「방송 이어 듣기」 단추

    /** 통화로 멈춘 방송이 있음 — 길 찾기 첫 화면·음악·방송 화면 맨 위에 「방송 이어 듣기」 */
    var jeonhwaDwi = false
        private set
    private var jeonhwaJeonNaneun = false

    fun jeonhwa(on: Boolean) = mainEseo2 {
        if (on) {
            jeonhwaJeonNaneun = naneunJung
            naebu("jeonhwa", true)
            return@mainEseo2
        }
        if (!naebuMeomchum.contains("jeonhwa")) return@mainEseo2
        if (!itda || !jeonhwaJeonNaneun || BangsongSeol.tonghwaDwiIeum) { naebu("jeonhwa", false); return@mainEseo2 }
        // 저절로 다시 틀지 않음 — 이용자 멈춤으로 돌려 두고 단추를 보임
        meomchum = true
        if (meomchunTtae == 0L) meomchunTtae = System.currentTimeMillis()
        naebuMeomchum.remove("jeonhwa")
        jeonhwaDwi = true
        Girok.namgi("jeonhwa_dwi", mapOf("jong" to jong.ireum))
        allim()
    }

    /** 「방송 이어 듣기」 — 통화 전에 듣던 것을 다시 */
    fun ieoDeutgi() {
        jeonhwaDwi = false
        if (itda && meomchum) meomchumTogeul() else allim()
    }

    private fun mainEseo2(f: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) f() else main.post(f) }
    /** 2.12.2 부름을 들은 때부터 명령을 마칠 때까지 멈춤 */
    fun bureumMeomchum(t: Boolean) { bureumIl = t; naebu("bureum", t) }

    private fun naebu(k: String, t: Boolean) {
        val jeon = naebuMeomchum.isEmpty()
        if (t) naebuMeomchum.add(k) else naebuMeomchum.remove(k)
        val hu = naebuMeomchum.isEmpty()
        if (!itda || meomchum || jeon == hu) return
        if (!hu) {
            meomchunTtae = System.currentTimeMillis()
            if (jong == BangsongJong.GISA) gisaSwigi() else exo?.playWhenReady = false
        } else {
            dasiTeulgi()
        }
    }

    /** 멈췄던 것을 다시 — 생방송을 1분 넘게 멈췄으면 새로 이음 */
    private fun dasiTeulgi() {
        val oraeMeomchum = meomchunTtae > 0 && System.currentTimeMillis() - meomchunTtae > 60000
        meomchunTtae = 0
        majimakUmjik = System.currentTimeMillis()
        when (jong) {
            BangsongJong.GISA -> gisaIeoIlkgi()
            BangsongJong.RADIO, BangsongJong.TV -> {
                val c = jigeumChaeneol
                if (oraeMeomchum && c != null) chaeneolTeulgi(c, false) else exoTeulgi()
            }
            BangsongJong.EUMAK, BangsongJong.NUGUNA -> exoTeulgi()
            BangsongJong.EOPSEUM -> {}
        }
        allim()
    }

    private fun exoTeulgi() {
        val p = pyeonJunbi() ?: return
        teulgiTtae = System.currentTimeMillis()   // 2.25.0 다시 틀 때도 소리 안 남 살핌을 새로
        anNaomAllim = false
        if (p.playbackState == Player.STATE_IDLE) p.prepare()
        p.playWhenReady = true
    }

    // MARK: 이용자 단추

    /** 멈춤과 다시 틀기 */
    fun meomchumTogeul() {
        if (!itda) return
        jeonhwaDwi = false   // 2.30.0 멈춤·이어서 틀기를 손수 누르시면 이어 듣기 단추는 거둠
        if (meomchum) {
            meomchum = false
            if (naebuMeomchum.isEmpty()) dasiTeulgi() else allim()
        } else {
            meomchum = true
            meomchunTtae = System.currentTimeMillis()
            if (jong == BangsongJong.GISA) gisaSwigi() else exo?.playWhenReady = false
            allim()
        }
    }

    /** 미디어 세션 단추 — 재생(참)·멈춤(거짓) */
    internal fun danchuJaesaeng(teulgi: Boolean) {
        if (!itda) return
        if (jong == BangsongJong.GISA || naebuMeomchum.isNotEmpty()) { meomchumTogeul(); return }
        if (teulgi == meomchum) meomchumTogeul()
    }

    /** 다음 곡·다음 채널·다음 기사 */
    fun daeum() {
        when (jong) {
            BangsongJong.EUMAK -> gokTeulgi(gokI + 1)
            BangsongJong.NUGUNA -> nugunaDaeum()
            BangsongJong.RADIO, BangsongJong.TV -> chaeneolBakkugi(1)
            BangsongJong.GISA -> gisaYeolgi(gisaI + 1)
            BangsongJong.EOPSEUM -> Sori.mal("지금 틀고 있는 것이 없습니다.", MalGeup.JEONGBO)
        }
    }

    /** 이전 곡·앞 채널·기사 처음부터 */
    fun ijeon() {
        when (jong) {
            BangsongJong.EUMAK -> gokTeulgi(maxOf(0, gokI - 1))
            BangsongJong.NUGUNA -> { nugunaI = maxOf(0, nugunaI - 2); nugunaDaeum() }
            BangsongJong.RADIO, BangsongJong.TV -> chaeneolBakkugi(-1)
            BangsongJong.GISA -> gisaCheoeumButeo()
            BangsongJong.EOPSEUM -> {}
        }
    }

    /** 모두 그만 */
    fun geuman(malHagi: Boolean = true) {
        if (!itda) return
        val jeon = jong
        jadoKkeugi(false)
        jong = BangsongJong.EOPSEUM
        teulgiBeon += 1
        exo?.let { it.stop(); it.clearMediaItems() }
        gisaGeuman()
        jemok = ""
        sangtaeMal = ""
        meomchum = false
        nugunaPyosi = ""
        gisaHwamyeonBoim = null
        BangsongService.kkeugi()
        if (malHagi) {
            val m = when (jeon) {
                BangsongJong.RADIO -> "라디오를 껐습니다."
                BangsongJong.TV -> "TV를 껐습니다."
                BangsongJong.GISA -> "기사 읽기를 그만둡니다."
                else -> "음악을 껐습니다."
            }
            Sori.mal(m, MalGeup.JEONGBO)
        }
        Girok.namgi("bangsong_geuman", mapOf("jong" to jeon.ireum))
        allim()
    }

    /** 지금 무엇이 나오는가 */
    val jigeumMal: String
        get() = when (jong) {
            BangsongJong.EOPSEUM -> "지금 틀고 있는 것이 없습니다."
            BangsongJong.EUMAK, BangsongJong.NUGUNA -> "지금 곡은 ${jemok}입니다."
            BangsongJong.RADIO -> "지금 ${jemok}을 듣고 계십니다."
            BangsongJong.TV -> "지금 ${jemok}을 틀어 두셨습니다."
            BangsongJong.GISA -> "지금 읽는 기사는 ${jemok}입니다."
        }

    // MARK: 재생 속

    private fun buMal(j: BangsongJong) = when (j) {
        BangsongJong.RADIO -> "길눈 — 라디오 듣기"
        BangsongJong.TV -> "길눈 — TV 보기"
        BangsongJong.GISA -> "길눈 — 지금 세상 이야기"
        else -> "길눈 — 길 위의 음악"
    }

    private fun teulgi(url: String, j: BangsongJong, ireum: String, seek: Double = 0.0) {
        if (jong == BangsongJong.GISA && j != BangsongJong.GISA) gisaGeuman()
        val p = pyeonJunbi() ?: return
        teulgiBeon += 1
        val md = MediaMetadata.Builder().setTitle(ireum).setArtist(buMal(j)).setDisplayTitle(ireum).build()
        val b = MediaItem.Builder().setUri(url).setMediaMetadata(md)
        if (url.lowercase(Locale.ROOT).contains("m3u8")) b.setMimeType(MimeTypes.APPLICATION_M3U8)
        jong = j
        jemok = ireum
        meomchum = false
        naebuMeomchum.remove("gyeonggo")
        majimakJari = -1.0
        majimakUmjik = System.currentTimeMillis()
        naoneunSijak = 0L
        seekDaegi = seek
        teulgiTtae = System.currentTimeMillis()
        anNaomAllim = false
        jariIlheum = 0L
        p.setMediaItem(b.build())
        hwajilMatchugi()
        p.prepare()
        p.volume = if (malJung) 0.2f else 1f
        p.playWhenReady = naebuMeomchum.isEmpty()
        allim()
    }

    private fun kkeunnam() {
        when (jong) {
            BangsongJong.EUMAK -> {
                if (gokI + 1 < gokMok.size) {
                    gokTeulgi(gokI + 1, false)
                } else {
                    Sori.mal("목록의 마지막 곡까지 들으셨습니다.", MalGeup.JEONGBO)
                    geuman(false)
                }
            }
            BangsongJong.NUGUNA -> nugunaDaeum(false)
            BangsongJong.TV, BangsongJong.RADIO -> {
                // MBC 듣는방송24처럼 프로그램 단위로 나오는 채널은 끝나면 다음 프로그램으로
                val c = jigeumChaeneol
                if (c != null) main.postDelayed({ if (jigeumChaeneol === c && itda) chaeneolTeulgi(c, false) }, 1500)
            }
            else -> {}
        }
    }

    /** 2.24.0 소리 자리를 잃었을 때 — 길눈 제 말 때문이면 말이 끝나고 0.8초 뒤 되찾음, 다른 앱이면 3초 뒤 멈춘 채로 */
    private fun jariSalpigi(now: Long) {
        if (jariIlheum == 0L) return
        val p = exo
        if (p == null || !itda || meomchum || p.playWhenReady) { jariIlheum = 0L; return }
        if (naebuMeomchum.isNotEmpty() || GinGeup.sangtae != GinGeupSangtae.EOPSEUM || Sori.deutneunJung) return   // 그쪽이 끝나면 dasiTeulgi 가 되돌림
        if (jariJeMal || Sori.malhaneunJung) {
            jariJeMal = true
            if (Sori.malhaneunJung) { jariIlheum = now; return }
            if (now - jariIlheum < 800) return
            jariIlheum = 0L
            Girok.namgi("bangsong_jari_doechatgi", mapOf("jong" to jong.ireum))
            p.playWhenReady = true   // 재생기가 소리 자리를 다시 청함
            allim()
        } else if (now - jariIlheum >= 3000) {
            jariIlheum = 0L
            meomchum = true
            meomchunTtae = now
            Girok.namgi("bangsong_jari_meomchum", mapOf("jong" to jong.ireum))
            allim()
        }
    }

    /** 2.24.0 틀었는데 15초가 지나도 소리가 안 나면 — 까닭을 기록에 남기고 한 번 알림(「나온다」고만 하고 안 나오는 일 막기) */
    private fun anNaomSalpigi(p: ExoPlayer, now: Long) {
        if (teulgiTtae == 0L || p.isPlaying) { if (p.isPlaying) teulgiTtae = 0L; return }
        if (meomchum || naebuMeomchum.isNotEmpty()) { teulgiTtae = now; return }   // 2.25.0 멈춰 두신 동안은 재지 않음(기록이 쏟아지지 않게)
        if (now - teulgiTtae < 15000) return
        if (now - majimakJindan > 8000) {
            majimakJindan = now
            Girok.namgi("bangsong_annaom", mapOf("jong" to jong.ireum, "st" to p.playbackState, "pwr" to p.playWhenReady,
                "sup" to p.playbackSuppressionReason, "vol" to p.volume.toDouble(), "naebu" to naebuMeomchum.joinToString(","),
                "meomchum" to meomchum, "maljung" to malJung, "mal" to Sori.malhaneunJung, "oryu" to (p.playerError?.errorCodeName ?: "")))
        }
        if (!anNaomAllim && !meomchum && naebuMeomchum.isEmpty() && GinGeup.sangtae == GinGeupSangtae.EOPSEUM &&
            !Sori.deutneunJung && now - anNaomMalTtae > 60000) {
            anNaomAllim = true
            anNaomMalTtae = now
            val ireum = when (jong) { BangsongJong.RADIO -> "라디오"; BangsongJong.TV -> "TV"; else -> "음악" }
            Sori.mal("$ireum 소리가 아직 나오지 않습니다. 다시 잇겠습니다.", MalGeup.JEONGBO)
            dasiIeum()
        }
    }

    /** 0.5초마다 — 말로 하기·긴급통화 살피기, 기사 화면을 떠나셨는지, 줄인 소리가 남았는지 */
    private fun salpigi() {
        naebu("deutgi", Sori.deutneunJung)
        naebu("tonghwa", GinGeup.sangtae != GinGeupSangtae.EOPSEUM)
        val now = System.currentTimeMillis()
        jariSalpigi(now)
        if (malJung && malKkeutR == null && !Sori.malhaneunJung && now - malSijakTtae > 2500) malKkeut()
        if (jong == BangsongJong.GISA) {
            val b = gisaHwamyeonBoim
            if (b != null && !b()) {
                if (gisaTteonamTtae == 0L) gisaTteonamTtae = now
                else if (now - gisaTteonamTtae >= 800) { gisaTteonamTtae = 0L; geuman(false) }
            } else {
                gisaTteonamTtae = 0L
            }
        }
    }

    /** 4초마다 — 멈춤 지킴이. 생방송은 재생 자리가 늘 비슷하게 보이므로 「소리가 나고 있는가」로 봄 */
    private fun jikigi() {
        val p = exo
        val now = System.currentTimeMillis()
        if (p != null && (jong == BangsongJong.RADIO || jong == BangsongJong.TV || jong == BangsongJong.EUMAK || jong == BangsongJong.NUGUNA)) anNaomSalpigi(p, now)
        if (p == null || jong == BangsongJong.EOPSEUM || jong == BangsongJong.GISA || meomchum || naebuMeomchum.isNotEmpty()) {
            majimakUmjik = now
            naoneunSijak = 0L
            return
        }
        if (p.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE || !p.playWhenReady) {
            majimakUmjik = now   // 다른 앱이 잠시 소리 자리를 쓰는 중
            return
        }
        if (p.isPlaying) {
            if (jong == BangsongJong.EUMAK || jong == BangsongJong.NUGUNA) majimakJari = p.currentPosition / 1000.0
            majimakUmjik = now
            if (naoneunSijak == 0L) naoneunSijak = now
            if (now - naoneunSijak >= 8000) ieumSu = 0   // 2.12.0 새로 이은 흐름이 막 시작한 것은 움직임으로 치지 않음
            return
        }
        naoneunSijak = 0L
        if (now - majimakUmjik > 16000) dasiIeum()
    }

    /** 다시 잇기 — 음악은 멈춘 자리부터, 라디오·TV는 새 주소로 */
    private fun dasiIeum() {
        if (jong == BangsongJong.EOPSEUM || jong == BangsongJong.GISA) return
        ieumSu += 1
        majimakUmjik = System.currentTimeMillis()
        Girok.namgi("bangsong_ieum", mapOf("jong" to jong.ireum, "su" to ieumSu))
        // 2.30.0 헛돌기 막기 — 여섯 번 잇달아 잇지 못하면 멈추고 한 번만 알림
        if (ieumSu > 5) {
            Girok.namgi("bangsong_meomchum", mapOf("jong" to jong.ireum, "su" to ieumSu))
            val ireum = when (jong) {
                BangsongJong.RADIO -> "라디오"
                BangsongJong.TV -> "TV"
                else -> "길 위의 음악"
            }
            ieumSu = 0
            sangtaeMal = ""
            geuman(false)
            Sori.mal("$ireum 연결이 거듭 끊겨 멈췄습니다. 잠시 뒤 다시 틀어 주십시오.", MalGeup.ANNAE)
            return
        }
        if (ieumSu == 2) {
            sangtaeMal = "끊겨서 다시 잇는 중입니다."
            Sori.mal("끊겨서 다시 잇는 중입니다.", MalGeup.JEONGBO)
            allim()
        }
        when (jong) {
            BangsongJong.RADIO, BangsongJong.TV -> jigeumChaeneol?.let { chaeneolTeulgi(it, false) }
            BangsongJong.EUMAK -> {
                val jari = maxOf(0.0, majimakJari)
                if (gokI >= 0 && gokI < gokMok.size) teulgi(gokJuso(gokMok[gokI]), BangsongJong.EUMAK, gokMok[gokI].ireum, jari)
            }
            BangsongJong.NUGUNA -> {
                val jari = maxOf(0.0, majimakJari)
                if (nugunaI >= 0 && nugunaI < nugunaJul.size) teulgi(nugunaJuso(nugunaJul[nugunaI]), BangsongJong.NUGUNA, nugunaJul[nugunaI].ireum, jari)
            }
            else -> {}
        }
    }

    /** TV 영상 끄기 — 가장 낮은 화질을 고르고 영상 길을 꺼 데이터를 아낌(끄시지 않으면 영상은 늘 나옴) */
    private fun hwajilMatchugi() {
        val p = exo ?: return
        val kkeum = jong == BangsongJong.TV && BangsongSeol.yeongsangKkeum
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setForceLowestBitrate(kkeum)
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, kkeum)
            .build()
    }

    // MARK: 나스에 묻기

    /** 값은 모두 영문·숫자·-._~ 밖을 퍼센트로(파일 이름의 +·& 도 안전하게 — 아이폰 juso 와 같음) */
    private fun pyo(v: String): String {
        val hx = "0123456789ABCDEF"
        val sb = StringBuilder()
        for (by in v.toByteArray(Charsets.UTF_8)) {
            val c = by.toInt() and 0xff
            val ch = c.toChar()
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '.' || ch == '_' || ch == '~') sb.append(ch)
            else { sb.append('%'); sb.append(hx[c shr 4]); sb.append(hx[c and 15]) }
        }
        return sb.toString()
    }

    fun juso(pail: String, q: List<Pair<String, String>>): String {
        val s = q.joinToString("&") { it.first + "=" + pyo(it.second) }
        return PPURI + pail + (if (s.isEmpty()) "" else "?$s")
    }

    private fun gatgi(url: String, handO: Int = 20000): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = handO
        c.readTimeout = handO
        c.useCaches = false
        c.setRequestProperty("User-Agent", UA)
        val r = if (c.responseCode == 200) c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } else null
        c.disconnect()
        r
    } catch (e: Exception) { null }

    /** JSON 하나 묻기 — 결과는 화면 줄에서. 못 받으면 null */
    private fun mutgi(pail: String, q: List<Pair<String, String>>, kkeut: (JSONObject?) -> Unit) {
        val u = juso(pail, q + ("_" to System.currentTimeMillis().toString()))
        il.execute {
            val t = gatgi(u)
            val o = try { if (t != null) JSONObject(t) else null } catch (e: Exception) { null }
            main.post { kkeut(o) }
        }
    }

    private fun eumakMutgi(a: String, q: List<Pair<String, String>> = emptyList(), kkeut: (JSONObject?) -> Unit) {
        val qq = ArrayList<Pair<String, String>>()
        qq.add("a" to a)
        if (tk.isNotEmpty()) qq.add("tk" to tk)
        mutgi("/jeom/eumak.php", qq + q, kkeut)
    }

    private fun gokDeul(o: JSONObject?): List<EumakGok> {
        val a = o?.optJSONArray("rows") ?: return emptyList()
        val l = ArrayList<EumakGok>()
        for (i in 0 until a.length()) {
            val r = a.optJSONObject(i) ?: continue
            val f = r.bsGeul("f")
            if (f.isEmpty()) continue
            l.add(EumakGok(f, r.bsGeul("ireum").ifEmpty { f }, r.bsGeul("s")))
        }
        return l
    }

    // MARK: 길 위의 음악 — 나스 음악(열쇠)

    /** 화면이 열릴 때 — 열쇠가 있으면 갈래를 받아 둠(2.19.0 음악 전체는 열쇠, 고장 노래만 모든 분께) */
    fun eumakJunbi(kkeut: () -> Unit = {}) {
        eumakMutgi("sangtae") { s ->
            cheoumIra = s != null && s.has("jeonghaessna") && !s.optBoolean("jeonghaessna", true)
            if (tk.isEmpty()) {
                eumakDeureom = false
                allim()
                kkeut()
            } else {
                eumakMutgi("galrae") { g ->
                    val ok = g?.optBoolean("ok", false) == true
                    eumakDeureom = ok
                    if (ok) galraeDeul = g?.bsGeulMok("rows") ?: emptyList()
                    allim()
                    kkeut()
                }
            }
        }
    }

    /** 열쇠 넣기 — 처음이면 그 열쇠로 정함. 돌려주는 것: 잘못된 까닭(되었으면 null) */
    fun yeolsoeNeoki(pw: String, kkeut: (String?) -> Unit) {
        mutgi("/jeom/eumak.php", listOf("a" to "sangtae")) { s ->
            if (s == null) { kkeut("자료 창고에 닿지 못했습니다. 통신을 확인해 주십시오."); return@mutgi }
            val a = if (s.optBoolean("jeonghaessna", false)) "deulgi" else "pwset"
            mutgi("/jeom/eumak.php", listOf("a" to a, "pw" to pw)) { k ->
                val t = k?.bsGeul("tk") ?: ""
                if (k?.optBoolean("ok", false) != true || t.isEmpty()) {
                    val e = k?.bsGeul("error") ?: ""
                    kkeut(e.ifEmpty { "들어가지 못했습니다. 열쇠를 다시 넣어 주십시오." })
                } else {
                    BangsongSeol.eumakTk = t
                    eumakJunbi { kkeut(null) }
                }
            }
        }
    }

    fun temaDeul(g: String, kkeut: (List<String>) -> Unit) {
        eumakMutgi("tema", listOf("g" to g)) { j -> kkeut(j?.bsGeulMok("rows") ?: emptyList()) }
    }

    /** 갈래·테마로 이어서 틀기 */
    fun galraeTeulgi(g: String, t: String) {
        Sori.mal("노래를 고르고 있습니다.", MalGeup.JEONGBO)
        val q = ArrayList<Pair<String, String>>()
        q.add("g" to g)
        if (t.isNotEmpty()) q.add("t" to t)
        eumakMutgi("gok", q) { j -> mokBadeum(j, "") }
    }

    /** 기분에 맞춰 틀기 — 돌려주는 말(빈 글이면 이미 말함) */
    fun gibunTeulgi(g: BangsongGibun, kkeut: (String) -> Unit) {
        Girok.namgi("gibun_eumak", mapOf("g" to g.name.lowercase(Locale.ROOT)))
        if (g == BangsongGibun.NALSSI) {
            nalssiJuje { juje, haneul ->
                val ap = "지금 날씨는 ${haneul}입니다. ${juje}에 어울리는 곡으로 골라 드리겠습니다."
                gibunTeulgiSok(g, juje, ap, kkeut)
            }
        } else {
            gibunTeulgiSok(g, "", g.apMal, kkeut)
        }
    }

    private fun gibunTeulgiSok(g: BangsongGibun, juje0: String, ap: String, kkeut: (String) -> Unit) {
        if (tk.isEmpty()) {
            nugunaTeulgi(g.nugunaBun, ap + " 나스 음악 열쇠가 없어 누구나 음악의 ${if (g.nugunaBun == "bal") "밝은" else "잔잔한"} 곡을 틉니다.")
            kkeut("")
            return
        }
        val dwi = {
            when (g) {
                BangsongGibun.SSEULSSEUL -> { galraeTeulgi("가곡·성악·합창", ""); kkeut(ap) }
                BangsongGibun.SEOLLEM -> { galraeTeulgi("샹송", ""); kkeut(ap) }
                else -> {
                    val juje = when (g) {
                        BangsongGibun.JEONHWAN, BangsongGibun.HEUNG -> "신나는 댄스"
                        BangsongGibun.CHABUN -> "잔잔한 음악"
                        BangsongGibun.JAM -> "밤"
                        else -> juje0
                    }
                    malChatgi(juje) { m -> kkeut("$ap $m") }
                }
            }
        }
        if (!eumakDeureom) eumakJunbi { dwi() } else dwi()
    }

    /** 2.13.0 날씨에 어울리는 음악 주제(아이폰 Nalssi.eumakJuje 와 같은 잣대) — 오픈메테오 하늘 번호 */
    private fun nalssiJuje(kkeut: (String, String) -> Unit) {
        val w = Wichi.jigeum
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (w == null) {
            if (h >= 21 || h < 5) kkeut("밤", "밤") else kkeut("맑은 날", "맑음")
            return
        }
        val la = String.format(Locale.US, "%.3f", w.lat)
        val lo = String.format(Locale.US, "%.3f", w.lon)
        val u = "https://api.open-meteo.com/v1/forecast?latitude=$la&longitude=$lo&current=weather_code&timezone=Asia%2FSeoul"
        il.execute {
            val t = gatgi(u, 12000)
            val code: Int? = try {
                val c = if (t != null) JSONObject(t).optJSONObject("current") else null
                if (c != null && c.has("weather_code") && !c.isNull("weather_code")) c.optDouble("weather_code").toInt() else null
            } catch (e: Exception) { null }
            val haneul = haneulMal(code)
            val r: Pair<String, String> = when {
                haneul.contains("비") || haneul.contains("소나기") || haneul.contains("천둥") -> "비 오는 날" to haneul
                haneul.contains("눈") -> "눈 오는 날" to haneul
                haneul.contains("안개") -> "안개 낀 날" to haneul
                h >= 21 || h < 5 -> "밤" to (if (haneul.isEmpty()) "밤" else "$haneul, 밤")
                haneul.contains("흐림") -> "흐린 날" to haneul
                else -> "맑은 날" to (if (haneul.isEmpty()) "맑음" else haneul)
            }
            main.post { kkeut(r.first, r.second) }
        }
    }

    private fun haneulMal(c: Int?): String {
        if (c == null) return ""
        return when {
            c == 0 -> "맑음"
            c <= 2 -> "구름 조금"
            c == 3 -> "흐림"
            c == 45 || c == 48 -> "안개"
            c in 51..57 -> "이슬비"
            c in 61..65 -> "비"
            c in 66..67 -> "얼어붙는 비"
            c in 71..77 -> "눈"
            c in 80..82 -> "소나기"
            c in 85..86 -> "눈 소나기"
            c >= 95 -> "천둥 번개"
            else -> ""
        }
    }

    /** 말 속의 기분(띄어쓰기를 뗀 말) — 없으면 null */
    fun gibunChatgi(z: String): BangsongGibun? {
        val pyo: List<Pair<BangsongGibun, List<String>>> = listOf(
            BangsongGibun.NALSSI to listOf("날씨에맞", "날씨에어울", "날씨따라", "날씨맞춰", "날씨에따라"),
            BangsongGibun.JAM to listOf("잠이안", "잠들", "잠잘", "자기전", "잘때"),
            BangsongGibun.JEONHWAN to listOf("꿀꿀", "우울", "처지", "처져", "기운없", "힘들", "지쳐", "지친", "짜증", "답답", "기분전환", "기분바꿔"),
            BangsongGibun.HEUNG to listOf("신나", "신난", "즐거", "흥겨", "흥나", "기분좋"),
            BangsongGibun.CHABUN to listOf("차분", "잔잔", "편안", "쉬고싶", "조용한", "쉬고파"),
            BangsongGibun.SSEULSSEUL to listOf("슬퍼", "슬프", "외로", "쓸쓸", "그리워", "그립", "울적"),
            BangsongGibun.SEOLLEM to listOf("설레", "낭만", "분위기있", "분위기좋")
        )
        for ((g, l) in pyo) if (l.any { z.contains(it) }) return g
        return null
    }

    /** 고장 이름으로 찾기 */
    fun gojangChatgi(q: String) {
        Sori.mal("$q 노래를 찾고 있습니다.", MalGeup.JEONGBO)
        eumakMutgi("gojang", listOf("q" to q)) { j ->
            val ls = gokDeul(j)
            if (ls.isEmpty()) Sori.mal("$q${bsI(q)} 든 노래를 찾지 못했습니다.")
            else mokBadeum(j, "$q 노래 ${ls.size}곡을 찾았습니다. ")
        }
    }

    /** 말로 찾기 — 가수·곡 이름·주제(웹 길눈과 같은 서버 찾기). 돌려주는 말은 kkeut 으로 */
    fun malChatgi(q: String, kkeut: (String) -> Unit) {
        eumakMutgi("chatgi", listOf("q" to q)) { j ->
            if (j != null && j.optBoolean("gallim", false)) {
                kkeut(j.bsGeul("mal").ifEmpty { "어느 쪽으로 틀까요? 다시 말씀해 주십시오." })
                return@eumakMutgi
            }
            val ls = gokDeul(j)
            // 2.14.0 꼭 맞는 곡이 없으면 나스가 권한 비슷한 제목·가수를 여쭘(이사장님 승인)
            val bs = j?.optJSONObject("biseut")
            if (ls.isEmpty() && bs != null && bs.bsGeul("q").isNotEmpty()) {
                biseutQ = bs.bsGeul("q")
                biseutTtae = System.currentTimeMillis()
                ChaBureugi.mureumBiugi()   // 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 새로 여쭈었으니 차 부르기의 묵은 물음을 거둠(아이폰과 같음)
                kkeut(bs.bsGeul("mal").ifEmpty { "비슷한 곡이 있습니다. 틀까요?" })
                return@eumakMutgi
            }
            if (ls.isEmpty()) { kkeut("찾는 곡이 없습니다. 가수나 곡 이름을 다시 말씀해 주십시오."); return@eumakMutgi }
            val n = if (j != null && j.has("su") && !j.isNull("su")) j.optInt("su", ls.size) else ls.size
            val ir = (j?.bsGeul("ireum") ?: "").ifEmpty { q }
            var m = when (j?.bsGeul("kind") ?: "") {
                "주제" -> ir + (if (ir.endsWith("음악")) "" else " 음악") + " ${n}곡을 찾았습니다. 섞어서 틉니다."
                "가수" -> "$ir 노래 ${n}곡을 찾았습니다. 섞어서 틉니다."
                "작곡가" -> "$ir 작품 ${n}곡을 찾았습니다. 섞어서 틉니다."
                "제목" -> "$ir, ${n}곡을 찾았습니다. 같은 제목의 곡부터 틉니다."
                else -> "$ir, ${n}곡을 찾았습니다. 섞어서 틉니다."
            }
            m += " 첫 곡은 ${ls[0].ireum}입니다."
            gokMok = ls
            gokTeulgi(0, false)
            kkeut(m)
        }
    }

    private fun mokBadeum(j: JSONObject?, apMal: String) {
        if (j?.optBoolean("ok", false) != true) {
            val er = j?.bsGeul("error") ?: ""
            if (er.contains("열쇠")) { BangsongSeol.eumakTk = ""; eumakDeureom = false; allim() }
            Sori.mal(er.ifEmpty { "불러오지 못했습니다. 통신을 확인해 주십시오." })
            return
        }
        val ls = gokDeul(j)
        if (ls.isEmpty()) { Sori.mal("그 자리에 노래가 없습니다."); return }
        gokMok = ls
        gokTeulgi(0, false)
        Sori.mal(apMal + "첫 곡은 ${ls[0].ireum}입니다.", MalGeup.JEONGBO)
    }

    private fun gokJuso(g: EumakGok): String {
        batadun(g.f)?.let { return Uri.fromFile(it).toString() }
        return juso("/jeom/eumak.php", listOf("a" to "teul", "tk" to tk, "f" to g.f, "q" to "g", "s" to g.s))
    }

    fun gokTeulgi(i: Int, malHagi: Boolean = true) {
        if (i < 0 || i >= gokMok.size) {
            Sori.mal("마지막 곡입니다. 목록의 끝입니다.", MalGeup.JEONGBO)
            return
        }
        gokI = i
        teulgi(gokJuso(gokMok[i]), BangsongJong.EUMAK, gokMok[i].ireum)
        if (malHagi) Sori.mal(gokMok[i].ireum, MalGeup.JEONGBO)
        // 다음 곡을 나스가 줄여 두게 하고, 폰에도 받아 둠(굴속에서도 이어지게)
        val nx = i + 1
        if (nx < gokMok.size) {
            val g = gokMok[nx]
            val tk0 = tk
            il.execute {
                gatgi(juso("/jeom/eumak.php", listOf("a" to "junbi", "tk" to tk0, "f" to g.f, "s" to g.s)), 30000)
                badaduki(g.f, tk0, g.s)
            }
        }
    }

    // 폰에 받아 둔 곡 — 세 곡까지만
    private fun batadunGot(): File? {
        val c = ac ?: return null
        val d = File(c.cacheDir, "gilnun_eumak")
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun batadunIreum(f: String): String = f.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").takeLast(80) + ".mp3"

    private fun batadun(f: String): File? {
        val d = batadunGot() ?: return null
        val u = File(d, batadunIreum(f))
        return if (u.exists() && u.length() > 0) u else null
    }

    /** 바탕 줄에서 부름 */
    private fun badaduki(f: String, tk: String, s: String) {
        if (batadun(f) != null || (tk.isEmpty() && s.isEmpty())) return
        val d = batadunGot() ?: return
        val mok = File(d, batadunIreum(f))
        val tmp = File(d, mok.name + ".bat")
        try {
            val c = URL(juso("/jeom/eumak.php", listOf("a" to "teul", "tk" to tk, "f" to f, "q" to "g", "s" to s))).openConnection() as HttpURLConnection
            c.connectTimeout = 20000
            c.readTimeout = 60000
            if (c.responseCode == 200) {
                c.inputStream.use { inp -> tmp.outputStream().use { out -> inp.copyTo(out) } }
                if (tmp.length() > 0) tmp.renameTo(mok)
            }
            c.disconnect()
        } catch (e: Exception) {
        } finally {
            if (tmp.exists()) tmp.delete()
        }
        // 오래된 것부터 지워 세 곡만 남김
        val ls = d.listFiles()?.filter { it.name.endsWith(".mp3") }?.sortedByDescending { it.lastModified() } ?: return
        for (x in ls.drop(3)) x.delete()
    }

    // MARK: 지나는 고장 노래 저절로(1분마다)

    /** 2.19.0 고장 노래는 열쇠 없이 모든 분께(모든 시각장애인에게 드리는 선물) */
    fun jadoKyeogi() {
        jadoKyeojim = true
        jadoKey = ""
        Sori.mal("지나는 고장 노래를 저절로 틀어 드립니다.")
        jadoBoda()
        jadoR?.let { main.removeCallbacks(it) }
        val r = object : Runnable {
            override fun run() { jadoBoda(); main.postDelayed(this, 60000) }
        }
        jadoR = r
        main.postDelayed(r, 60000)
        allim()
    }

    /** 2.12.7 차에 타면 지나는 고장 노래를 저절로(설정에서 끔) — 라디오·TV·기사를 듣고 계시면 건드리지 않음. 여정(차 안) 쪽이 부름 */
    fun chaTamGojangNorae() {
        if (!BangsongSeol.gojangNorae || jadoKyeojim) return
        if (jong != BangsongJong.EOPSEUM && jong != BangsongJong.EUMAK) return
        jadoChaRo = true
        jadoKyeogi()
        jadoChaRo = true
        Girok.namgi("gojang_norae_cha")
    }

    /** 차에서 내리시거나 여정을 마치시면 고장 따라 바꾸기만 멈춤(듣던 노래는 그대로) */
    fun chaNaerimGojangNorae() {
        if (!jadoKyeojim || !jadoChaRo) return
        jadoChaRo = false
        jadoKkeugi(false)
    }

    fun jadoKkeugi(malHagi: Boolean = true) {
        if (!jadoKyeojim) return
        jadoChaRo = false
        jadoKyeojim = false
        jadoR?.let { main.removeCallbacks(it) }
        jadoR = null
        jadoJul = ""
        if (malHagi) Sori.mal("저절로 틀기를 그만둡니다. 듣던 노래는 그대로 이어집니다.")
        allim()
    }

    private fun jadoBoda() {
        if (!jadoKyeojim) return
        val w = Wichi.jigeum ?: return
        mutgi("/jeom/gojang.php", listOf("lat" to String.format(Locale.US, "%.6f", w.lat), "lon" to String.format(Locale.US, "%.6f", w.lon))) { j ->
            if (j == null || !j.optBoolean("ok", false)) return@mutgi
            val key = j.bsGeul("key")
            if (key.isEmpty() || key == jadoKey) return@mutgi
            jadoKey = key
            val si0 = j.bsGeul("si")
            val gu0 = j.bsGeul("gu")
            val gj = j.bsGeul("gojang")
            // 시·군 이름을 먼저, 구 이름은 쓰지 않음(웹 길눈 260911 고침과 같게)
            var nm: String
            val mat = Regex("^[^\\s]+?(시|군)(\\s|$)").find(gu0)
            if (mat != null) {
                nm = mat.value.trim()
                if (nm.endsWith("시") || nm.endsWith("군")) nm = nm.dropLast(1)
            } else {
                nm = si0.replace(Regex("(특별시|광역시|특별자치시|특별자치도|통합특별시|도)$"), "")
            }
            if (nm.isEmpty()) return@mutgi
            jadoJul = "지금 $gj — $nm 노래를 찾습니다."
            allim()
            eumakMutgi("gojang", listOf("q" to nm)) { k ->
                val si = si0.replace(Regex("(특별시|광역시|특별자치시|특별자치도|도)$"), "")
                if (gokDeul(k).isEmpty() && si.isNotEmpty() && si != nm) {
                    eumakMutgi("gojang", listOf("q" to si)) { k2 -> jadoBadeum(gokDeul(k2), si, gj) }
                } else {
                    jadoBadeum(gokDeul(k), nm, gj)
                }
            }
        }
    }

    private fun jadoBadeum(ls: List<EumakGok>, ireum: String, gj: String) {
        if (!jadoKyeojim) return
        if (ls.isEmpty()) {
            jadoJul = "지금 $gj — 이 고장 노래를 찾지 못해 앞서 듣던 것을 이어 갑니다."
            allim()
            return
        }
        gokMok = ls
        gokTeulgi(0, false)
        jadoJul = "지금 $gj — $ireum 노래 ${ls.size}곡"
        Sori.mal("$ireum 노래 ${ls.size}곡으로 바꿉니다.", MalGeup.JEONGBO)
        allim()
    }

    // MARK: 누구나 음악(열쇠 없이, 공유마당 자유이용 곡)

    private fun nugunaJuso(g: NugunaGok): String {
        val p = if (g.mp3.contains("%")) g.mp3 else Uri.encode(g.mp3, "/-_.~()")
        return "$PPURI/jeom/nuguna/$p"
    }

    fun nugunaTeulgi(bun: String, mal: String) {
        val dwi = {
            val jul = nuguna.filter { bun.isEmpty() || it.bun == bun }.shuffled()
            if (jul.isEmpty()) {
                Sori.mal("누구나 음악 목록을 받지 못했습니다. 잠시 뒤 다시 해 주십시오.")
            } else {
                nugunaJul = jul
                nugunaI = -1
                nugunaDaeum(false)
                Sori.mal(mal + " 첫 곡은 ${jul[0].ireum}입니다.", MalGeup.JEONGBO)
            }
        }
        if (nuguna.isNotEmpty()) { dwi(); return }
        val u = "$PPURI/jeom/nuguna/mok.json?_=${System.currentTimeMillis() / 1000}"
        il.execute {
            val t = gatgi(u)
            val ls = ArrayList<NugunaGok>()
            try {
                val a = if (t != null) JSONArray(t) else JSONArray()
                for (i in 0 until a.length()) {
                    val r = a.optJSONObject(i) ?: continue
                    val mp3 = r.bsGeul("mp3")
                    if (mp3.isEmpty()) continue
                    val sn = if (r.isNull("sn")) mp3 else r.opt("sn").toString()
                    ls.add(NugunaGok(sn, r.bsGeul("ireum"), r.bsGeul("jakgok"), r.bsGeul("jeojakja"), r.bsGeul("jogeon"), r.bsGeul("bun"), mp3))
                }
            } catch (e: Exception) {}
            main.post {
                if (ls.isNotEmpty()) nuguna = ls
                dwi()
            }
        }
    }

    private fun nugunaDaeum(malHagi: Boolean = true) {
        if (nugunaJul.isEmpty()) return
        nugunaI = (nugunaI + 1) % nugunaJul.size
        val g = nugunaJul[nugunaI]
        nugunaPyosi = g.pyosi
        teulgi(nugunaJuso(g), BangsongJong.NUGUNA, g.ireum)
        if (malHagi) Sori.mal(g.ireum, MalGeup.JEONGBO)
    }

    // MARK: 라디오·TV

    fun chaeneolBatgi(kkeut: () -> Unit = {}) {
        mutgi("/bfblive/sori.php", listOf("a" to "list")) { j ->
            val a = j?.optJSONArray("list")
            val ls = ArrayList<Chaeneol>()
            if (a != null) for (i in 0 until a.length()) {
                val r = a.optJSONObject(i) ?: continue
                val id = r.bsGeul("id")
                val nm = r.bsGeul("name")
                if (id.isEmpty() || nm.isEmpty()) continue
                ls.add(Chaeneol(id, nm, r.bsGeul("freq"), r.bsGeul("kind").ifEmpty { "radio" }))
            }
            if (ls.isNotEmpty()) chaeneolDeul = ls
            allim()
            kkeut()
        }
    }

    fun kindChaeneol(kind: String): List<Chaeneol> = chaeneolDeul.filter { it.kind == kind }

    fun chaeneolTeulgi(c: Chaeneol, malHagi: Boolean = true) {
        jigeumChaeneol = c
        if (malHagi) {
            sangtaeMal = "${c.name}에 잇는 중입니다."
            Sori.mal("${c.name}에 잇는 중입니다.", MalGeup.JEONGBO)
            allim()
        }
        if (c.kind == "radio") BangsongSeol.majimakRadio = c.id else BangsongSeol.majimakTv = c.id
        Girok.namgi("bangsong", mapOf("id" to c.id))
        mutgi("/bfblive/sori.php", listOf("a" to "url", "id" to c.id)) { j ->
            if (jigeumChaeneol?.id != c.id) return@mutgi
            val s = j?.bsGeul("url") ?: ""
            if (j == null || !j.optBoolean("ok", false) || s.isEmpty()) {
                sangtaeMal = (j?.bsGeul("msg") ?: "").ifEmpty { "${c.name}에 잇지 못했습니다." }
                Sori.mal(sangtaeMal)
                allim()
                return@mutgi
            }
            val url = if (s.startsWith("http")) s else "$PPURI/bfblive/$s"
            val seek = if (j.has("seek") && !j.isNull("seek")) j.optDouble("seek", 0.0) else 0.0
            chaeneolNote = j.bsGeul("note")
            teulgi(url, if (c.kind == "tv") BangsongJong.TV else BangsongJong.RADIO, c.name, if (seek.isNaN()) 0.0 else seek)
        }
    }

    /** 2.12.7 방송 이름을 한 가지 꼴로 — 영문·한글·띄어쓰기·말 순서가 달라도 같게(MBC·엠비씨·엠비시 → mbc) */
    fun bangsongPyojun(t: String): String {
        var s = MalSajeon.ttuk(t).lowercase(Locale.ROOT)
        val bakkum = listOf(
            "엠비씨" to "mbc", "엠비시" to "mbc", "앰비씨" to "mbc", "앰비시" to "mbc", "엠비" to "mbc", "문화방송" to "mbc",
            "케이비에스" to "kbs", "케이비애스" to "kbs", "캐이비에스" to "kbs", "한국방송" to "kbs",
            "이비에스" to "ebs", "이비애스" to "ebs", "교육방송" to "ebs",
            "오비에스" to "obs", "티비에스" to "tbs", "비비에스" to "bbs", "불교방송" to "bbs", "아리랑" to "arirang",
            "에프엠" to "fm", "애프엠" to "fm", "포유" to "4u",
            "티브이" to "tv", "티비" to "tv", "텔레비전" to "tv",
            "일라디오" to "1라디오", "원라디오" to "1라디오", "삼라디오" to "3라디오",
            "일tv" to "1tv", "이tv" to "2tv", "원tv" to "1tv", "투tv" to "2tv"
        )
        for ((a, b) in bakkum) s = s.replace(a, b)
        return s
    }

    /** 말 속의 방송사 */
    private fun bangsongsa(s: String): String? {
        for (b in listOf("kbs", "mbc", "ebs", "obs", "tbs", "bbs", "arirang")) if (s.contains(b)) return b
        return null
    }

    /** 말로 — "MBC 라디오 틀어 줘", "라디오 엠비씨", "표준FM", "라디오 틀어 줘"(지난번 채널)
     *  돌려주는 것: (할 말, 되묻는 말인지). 모르는 이름이면 아무것도 틀지 않고 여쭘(2.12.7) */
    fun chaeneolMalro(z: String, kind: String, kkeut: (String, Boolean) -> Unit) {
        if (chaeneolDeul.isEmpty()) {
            chaeneolBatgi { chaeneolMalroSok(z, kind, kkeut) }
        } else {
            chaeneolMalroSok(z, kind, kkeut)
        }
    }

    private class Pul(val c: Chaeneol, val sa: String?, val bu: String)

    private fun chaeneolMalroSok(z: String, kind: String, kkeut: (String, Boolean) -> Unit) {
        val ls = kindChaeneol(kind)
        if (ls.isEmpty()) { kkeut("채널 목록을 받지 못했습니다. 통신을 확인해 주십시오.", false); return }
        val zz = bangsongPyojun(z)
        val pul = ls.map { c ->
            val nm = bangsongPyojun(c.name)
            val sa = bangsongsa(nm)
            var bu = nm
            if (sa != null) bu = bu.replace(sa, "")
            bu = bu.replace("(화면해설)", "")
            Pul(c, sa, bu)
        }
        val sa = bangsongsa(zz)
        val hubo = if (sa == null) pul else pul.filter { it.sa == sa }
        // 1) 이름 통째로
        var gorun: Chaeneol? = hubo.firstOrNull { it.bu.isNotEmpty() && zz.contains((it.sa ?: "") + it.bu) }?.c
        // 2) 방송사를 뺀 나머지(표준fm, fm4u, 1라디오, 해피fm …)
        val heunhan = setOf("tv", "fm", "라디오", "방송")
        if (gorun == null) gorun = hubo.firstOrNull { it.bu.length >= 2 && !heunhan.contains(it.bu) && zz.contains(it.bu) }?.c
        // 3) 줄여 부른 말
        if (gorun == null) {
            val jjal = listOf("표준" to "표준fm", "4u" to "fm4u", "해피" to "해피fm", "쿨" to "쿨fm", "클래식" to "클래식fm",
                "사랑의소리" to "3라디오", "3라디오" to "3라디오", "1라디오" to "1라디오", "한민족" to "한민족",
                "듣는방송" to "듣는방송", "화면해설" to "듣는방송", "1tv" to "1tv", "2tv" to "2tv", "플러스" to "플러스")
            for ((m, k) in jjal) {
                if (!zz.contains(m)) continue
                gorun = hubo.firstOrNull { it.bu.contains(k) }?.c
                if (gorun != null) break
            }
        }
        // 4) 방송사만 말씀하시면 그 방송사의 으뜸 채널(MBC → MBC 표준FM, KBS → KBS 1라디오)
        if (gorun == null && sa != null && hubo.isNotEmpty()) {
            val eutteum = if (kind == "radio") mapOf("mbc" to "mbcsfm", "kbs" to "kbs1r", "ebs" to "ebsfm")
            else mapOf("mbc" to "mbcdeut", "kbs" to "kbs1tv", "ebs" to "ebs1tv")
            gorun = hubo.firstOrNull { it.c.id == eutteum[sa] }?.c ?: hubo.first().c
        }
        // 5) 아무 이름도 없이 "라디오 틀어 줘"면 지난번 채널
        if (gorun == null) {
            var namun = zz
            for (w in listOf("라디오", "tv", "fm", "방송", "채널", "틀어", "틀자", "켜", "들려", "듣자", "들을래", "보자", "볼래", "주세요", "줘", "좀", "다시", "지금", "을", "를")) {
                namun = namun.replace(w, "")
            }
            if (namun.isEmpty()) {
                val id = if (kind == "radio") BangsongSeol.majimakRadio else BangsongSeol.majimakTv
                gorun = ls.firstOrNull { it.id == id } ?: ls.firstOrNull()
            }
        }
        val c = gorun
        if (c == null) {
            Girok.namgi("bangsong_moreum", mapOf("mal" to z.take(40)))
            val ireum = ls.take(8).joinToString(", ") { it.name }
            kkeut("어느 방송을 ${if (kind == "radio") "들으실까요" else "보실까요"}? $ireum 가운데 말씀해 주십시오.", true)
            return
        }
        chaeneolTeulgi(c, false)
        kkeut("${c.name}을 틉니다.", false)
    }

    private fun chaeneolBakkugi(d: Int) {
        val c = jigeumChaeneol ?: return
        val ls = kindChaeneol(c.kind)
        val i = ls.indexOfFirst { it.id == c.id }
        if (i < 0 || ls.isEmpty()) return
        chaeneolTeulgi(ls[((i + d) % ls.size + ls.size) % ls.size])
    }

    // MARK: 지금 세상 이야기

    val garae: List<Pair<String, String>> = listOf("sokbo" to "속보·특보", "all" to "두루 소식", "jangae" to "장애·복지", "jeongchi" to "정치",
        "gyeongje" to "경제", "gukje" to "국제", "sahoe" to "사회", "spo" to "스포츠·연예")

    private fun gisaDeul(j: JSONObject?): List<Gisa> {
        val a = j?.optJSONArray("rows") ?: return emptyList()
        val l = ArrayList<Gisa>()
        for (i in 0 until a.length()) {
            val r = a.optJSONObject(i) ?: continue
            if (r.isNull("jemok") || r.isNull("juso")) continue
            l.add(Gisa(r.bsGeul("saem"), r.bsGeul("jemok"), r.bsGeul("yoyak"), r.bsGeul("juso"), r.bsGeul("ttaeMal"), r.optBoolean("sokbo", false)))
        }
        return l
    }

    /** 갈래의 기사 — null 은 받지 못함 */
    fun gisaBatgi(g: String, sae: Boolean = false, kkeut: (List<Gisa>?) -> Unit) {
        val q = ArrayList<Pair<String, String>>()
        q.add("garae" to g)
        if (sae) q.add("sae" to "1")
        mutgi("/jeom/sesang.php", q) { j ->
            if (j == null || !j.optBoolean("ok", false)) { kkeut(null); return@mutgi }
            val ls = gisaDeul(j)
            gisaMok = ls
            kkeut(ls)
        }
    }

    fun gisaChatgi(q: String, kkeut: (List<Gisa>?) -> Unit) {
        mutgi("/jeom/sesang.php", listOf("a" to "chatgi", "q" to q)) { j ->
            if (j == null || !j.optBoolean("ok", false)) { kkeut(null); return@mutgi }
            val ls = gisaDeul(j)
            gisaMok = ls
            kkeut(ls)
        }
    }

    /** 기사 열기 — 원문 전체를 받아 읽음(말소리를 꺼 두셨으면 화면에만) */
    fun gisaYeolgi(i: Int) {
        if (i < 0 || i >= gisaMok.size) { Sori.mal("더 들려 드릴 기사가 없습니다."); return }
        val r = gisaMok[i]
        teulgiBeon += 1
        exo?.let { it.stop(); it.clearMediaItems() }
        BangsongService.kkeugi()   // 기사는 길눈 말소리로 읽으므로 알림 칸 재생기는 내려 둠
        jadoKkeugi(false)
        gisaGeuman()
        gisaI = i
        jong = BangsongJong.GISA
        jemok = r.jemok
        meomchum = false
        gisaBon = emptyList()
        gisaMeori = r.saem + (if (r.ttaeMal.isEmpty()) "" else " · " + r.ttaeMal) + " · 원문을 여는 중입니다"
        allim()
        mutgi("/jeom/sesang.php", listOf("a" to "bonmun", "u" to r.juso)) { j ->
            if (gisaI != i || jong != BangsongJong.GISA) return@mutgi
            val mundan: List<String>
            val meori: String
            if (j != null && j.optBoolean("ok", false)) {
                mundan = j.bsGeulMok("mundan")
                val tm = j.bsGeul("ttaeMal").ifEmpty { r.ttaeMal }
                val gija = j.bsGeul("gija")
                meori = j.bsGeul("saem").ifEmpty { r.saem } + (if (tm.isEmpty()) "" else " · $tm") + (if (gija.isEmpty()) "" else " · $gija") + " · 원문 전체"
            } else {
                val msg = (j?.bsGeul("msg") ?: "").ifEmpty { "원문을 열지 못했습니다." }
                mundan = if (r.yoyak.isNotEmpty() && !msg.contains("속보는")) listOf(r.yoyak) else emptyList()
                meori = r.saem + (if (r.ttaeMal.isEmpty()) "" else " · " + r.ttaeMal) + " · " + msg + (if (mundan.isEmpty()) "" else " 요약을 읽어 드립니다.")
            }
            gisaMeori = meori
            gisaBon = mundan
            gisaIlkgi()
            allim()
        }
    }

    fun gisaCheoeumButeo() {
        if (jong != BangsongJong.GISA) return
        meomchum = false
        gisaIlkgi()
        allim()
    }

    // MARK: 기사 읽기 목소리(길눈 안내 목소리와 따로) — 안드로이드 말소리는 쉬었다 잇기가 없어 문단 처음부터 이어 읽음

    private var gtts: TextToSpeech? = null
    private var gttsJunbi = false
    private var gisaJul: List<String> = emptyList()
    private var gisaJari = 0
    private var gisaSedae = 0
    private var gisaSwineun = false
    private var gisaDaegi = false
    private var deureoboki = false

    private fun gttsSewugi() {
        if (gtts != null) return
        val c = ac ?: return
        gtts = TextToSpeech(c) { st ->
            main.post {
                if (st == TextToSpeech.SUCCESS) {
                    gtts?.language = Locale.KOREAN
                    gtts?.setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    gttsJunbi = true
                    moksoriMatchugi()
                    if (deureoboki) { deureoboki = false; moksoriDeureoboki() }
                    if (gisaDaegi) { gisaDaegi = false; if (gisaIlkneun && !gisaSwineun) malhagiButeo(gisaJari) }
                } else {
                    Girok.namgi("gisa_moksori_oryu", mapOf("st" to st))
                }
            }
        }
        gtts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { main.post { gisaJariBatgi(utteranceId) } }
            override fun onDone(utteranceId: String?) { main.post { gisaDone(utteranceId) } }
            @Deprecated("옛 안드로이드")
            override fun onError(utteranceId: String?) { main.post { gisaDone(utteranceId) } }
        })
    }

    private fun gisaJariBatgi(id: String?) {
        val p = id?.split("_") ?: return
        if (p.size != 3 || p[0] != "g" || p[1] != gisaSedae.toString()) return
        gisaJari = p[2].toIntOrNull() ?: gisaJari
    }

    private fun gisaDone(id: String?) {
        val p = id?.split("_") ?: return
        if (p.size != 3 || p[0] != "g" || p[1] != gisaSedae.toString()) return
        val n = p[2].toIntOrNull() ?: return
        if (n >= gisaJul.size - 1) {
            gisaIlkneun = false
            gisaSwineun = false
            allim()
        }
    }

    /** 폰에 있는 한국어 목소리들 */
    fun hangukMoksori(): List<Voice> {
        val t = gtts ?: return emptyList()
        val v: Set<Voice> = try { t.voices ?: emptySet() } catch (e: Exception) { emptySet() }
        return v.filter { it.locale.language == "ko" }.sortedBy { it.name }
    }

    private fun moksoriMatchugi() {
        val t = gtts ?: return
        val nm = BangsongSeol.gisaMoksori
        if (nm.isEmpty()) return
        val v = hangukMoksori().firstOrNull { it.name == nm } ?: return
        try { t.setVoice(v) } catch (e: Exception) {}
    }

    /** 지금 기사 목소리 이름(화면 단추에) */
    fun moksoriIreum(): String {
        gttsSewugi()
        val ls = hangukMoksori()
        val nm = BangsongSeol.gisaMoksori
        val i = ls.indexOfFirst { it.name == nm }
        if (nm.isEmpty() || i < 0) return "폰 기본 목소리"
        return "목소리 ${i + 1}" + (if (ls[i].isNetworkConnectionRequired) ", 인터넷 목소리" else "")
    }

    /** 다음 목소리로 바꾸고 들어 보기 */
    fun daeumMoksori() {
        gttsSewugi()
        val ls = hangukMoksori()
        if (ls.isEmpty()) { Sori.mal("고를 수 있는 한국어 목소리가 없습니다."); return }
        val i = ls.indexOfFirst { it.name == BangsongSeol.gisaMoksori }
        BangsongSeol.gisaMoksori = ls[(i + 1) % ls.size].name
        moksoriMatchugi()
        moksoriDeureoboki()
    }

    private val gisaPpareugi: Float get() = listOf(0.6f, 0.8f, 1.0f, 1.25f, 1.5f)[BangsongSeol.gisaBbareugiDan]

    /** 목소리 들어 보기 */
    fun moksoriDeureoboki() {
        if (jong == BangsongJong.GISA && gisaIlkneun && !gisaSwineun) return
        gttsSewugi()
        val t = gtts
        if (t == null || !gttsJunbi) { deureoboki = true; return }
        t.setSpeechRate(gisaPpareugi)
        t.speak("이 목소리로 기사를 읽어 드립니다.", TextToSpeech.QUEUE_FLUSH, null, "deureoboki")
    }

    /** 제목부터 끝까지 읽기 */
    private fun gisaIlkgi() {
        if (jong != BangsongJong.GISA || gisaI < 0 || gisaI >= gisaMok.size) return
        gisaSedae += 1
        gtts?.stop()
        if (!Seoljeong.malKyeojim) { gisaIlkneun = false; return }   // 톡백으로 들으시는 분은 화면 글로
        val ilk = ArrayList<String>()
        ilk.add(gisaMok[gisaI].jemok + ".")
        ilk.add(gisaMeori.replace(" · ", ", ") + ".")
        ilk.addAll(gisaBon)
        ilk.add("기사 끝입니다. 다음 기사를 들으시려면 다음 기사를 누르십시오.")
        // 긴 문단은 말소리가 받는 길이 안으로 나눔
        val jul = ArrayList<String>()
        for (s in ilk) {
            val tt = s.trim()
            if (tt.isEmpty()) continue
            var nam = tt
            while (nam.length > 3500) {
                var k = nam.lastIndexOf(". ", 3500)
                if (k < 500) k = 3500 else k += 1
                jul.add(nam.substring(0, k).trim())
                nam = nam.substring(k).trim()
            }
            if (nam.isNotEmpty()) jul.add(nam)
        }
        gisaJul = jul
        gisaJari = 0
        gisaIlkneun = true
        if (meomchum || naebuMeomchum.isNotEmpty() || malJung) { gisaSwineun = true; return }
        gisaSwineun = false
        malhagiButeo(0)
    }

    private fun malhagiButeo(n: Int) {
        gttsSewugi()
        val t = gtts
        if (t == null || !gttsJunbi) { gisaDaegi = true; return }
        gisaSedae += 1
        val sd = gisaSedae
        t.setSpeechRate(gisaPpareugi)
        var cheot = true
        for (i in n until gisaJul.size) {
            t.speak(gisaJul[i], if (cheot) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "g_${sd}_$i")
            t.playSilentUtterance(250, TextToSpeech.QUEUE_ADD, "s_${sd}_$i")
            cheot = false
        }
        if (cheot) { gisaIlkneun = false; allim() }
    }

    /** 기사 읽기를 잠시 쉼(지금 문단 처음부터 이어 읽음) */
    private fun gisaSwigi() {
        if (!gisaIlkneun || gisaSwineun) return
        gisaSwineun = true
        gisaDaegi = false
        gisaSedae += 1
        gtts?.stop()
    }

    private fun gisaIeoIlkgi() {
        if (!gisaIlkneun || !gisaSwineun) return
        if (meomchum || naebuMeomchum.isNotEmpty() || malJung) return
        gisaSwineun = false
        malhagiButeo(gisaJari)
    }

    private fun gisaGeuman() {
        gisaSedae += 1
        gisaDaegi = false
        gisaSwineun = false
        gisaIlkneun = false
        gtts?.stop()
    }
}

/** 말로 하기의 음악·방송 명령(아이폰 MalHagi bangsongMyeongryeong·기분 음악과 같은 말) — MalHagi.kt 가 부름(통합 쪽지) */
object BangsongMal {
    /** 2.13.0 기분과 날씨에 맞춰 음악 — "기분이 꿀꿀해", "잔잔한 음악 틀어 줘", "날씨에 맞게 틀어 줘". 하면 참 */
    fun gibun(z: String, dap: (String, Boolean) -> Unit): Boolean {
        val g = Bangsong.gibunChatgi(z) ?: return false
        if (!(g == BangsongGibun.NALSSI || listOf("음악", "노래", "틀어", "틀자", "들려", "추천", "곡", "기분").any { z.contains(it) })) return false
        if (listOf("그만", "꺼", "끄기", "멈춰", "중지").any { z.contains(it) }) return false
        Bangsong.gibunTeulgi(g) { m -> dap(m, false) }
        return true
    }

    /** 2.8.0 음악·방송 명령 — 하면 참. t 는 알아들은 말 그대로, z 는 띄어쓰기를 뗀 말 */
    fun myeongryeong(alts: List<String>, t: String, z: String, dap: (String, Boolean) -> Unit): Boolean {
        val s = MalSajeon
        val b = Bangsong
        val zl = z.lowercase(Locale.ROOT)
        if (s.itda(alts, "gok_daeum")) {
            if (b.itda) { dap("", false); b.daeum() } else dap("지금 틀고 있는 것이 없습니다.", false)
            return true
        }
        if (s.itda(alts, "gok_ijeon")) {
            if (b.itda) { dap("", false); b.ijeon() } else dap("지금 틀고 있는 것이 없습니다.", false)
            return true
        }
        if (s.itda(alts, "musun_gok")) {
            dap(b.jigeumMal, false)
            return true
        }
        if (s.itda(alts, "dasiteul") && b.itda && b.meomchum) {
            dap("", false)
            b.meomchumTogeul()
            return true
        }
        val kkeugi = listOf("그만", "꺼", "끄기", "끄자", "멈춰", "중지").any { z.contains(it) }
        // 2.12.7 방송사 이름만 말씀하셔도(MBC 틀어 줘, 엠비시) 라디오로 — 음악 찾기로 새지 않게
        val pj = b.bangsongPyojun(z)
        val bangsongsaMal = listOf("kbs", "mbc", "ebs", "obs", "tbs", "bbs", "arirang", "표준fm", "fm4u", "4u", "해피fm", "쿨fm", "클래식fm", "사랑의소리", "한민족").any { pj.contains(it) }
        val radio = z.contains("라디오") || zl.contains("fm") || z.contains("에프엠") || bangsongsaMal
        val tv = zl.contains("tv") || z.contains("티비") || z.contains("티브이") || z.contains("텔레비전") || z.contains("듣는방송")
        val nyuseu = s.itda(alts, "nyuseu") || s.itda(alts, "jangae") || z.contains("뉴스") || z.contains("세상이야기") || z.contains("속보") || z.contains("기사읽") || z.contains("기사들려")
        val eumak = s.itda(alts, "eumak") || z.contains("노래") || z.contains("음악") || z.contains("틀어")
        // 2.12.7 지나는 고장 노래 — "고장 노래 틀어 줘", "지나는 고장 노래 꺼"
        if (z.contains("고장노래") || z.contains("고장음악") || (z.contains("지나는고장") && (z.contains("노래") || z.contains("음악")))) {
            if (kkeugi) {
                if (b.jadoKyeojim) { dap("", false); b.jadoKkeugi() } else dap("지나는 고장 노래는 켜져 있지 않습니다.", false)
                return true
            }
            if (b.jadoKyeojim) { dap("지나는 고장 노래가 이미 켜져 있습니다.", false); return true }
            dap("", false)   // 2.19.0 고장 노래는 열쇠 없이 모든 분께
            b.jadoKyeogi()
            return true
        }
        if (kkeugi) {
            if (!b.itda || !(radio || tv || nyuseu || eumak || z.contains("방송"))) return false
            dap("", false)
            b.geuman()
            return true
        }
        if (nyuseu) {
            val g = when {
                z.contains("속보") || z.contains("특보") -> "sokbo"
                z.contains("장애") || z.contains("복지") -> "jangae"
                z.contains("정치") -> "jeongchi"
                z.contains("경제") -> "gyeongje"
                z.contains("국제") || z.contains("세계") -> "gukje"
                z.contains("사회") -> "sahoe"
                z.contains("스포츠") || z.contains("연예") -> "spo"
                else -> "all"
            }
            val nm = b.garae.firstOrNull { it.first == g }?.second ?: "두루 소식"
            b.gisaBatgi(g) { ls ->
                if (ls == null) { dap("소식을 받아 오지 못했습니다. 통신을 확인해 주십시오.", false); return@gisaBatgi }
                if (ls.isEmpty()) { dap("지금 ${nm}에는 새 기사가 없습니다.", false); return@gisaBatgi }
                b.gisaYeolgi(0)
                // 음악·방송 탭 첫 화면에서 지금 세상 이야기 → 기사 화면을 엶(아이폰 TabGil 2, BangsongGil)
                MalHagi.hwalseong?.get()?.let { a ->
                    if (!a.isFinishing) {
                        a.tabGo(2)
                        a.tabGo(2)   // 두 번째 누름은 그 탭의 첫 화면으로
                        a.yeolgi(SesangHwamyeon())
                        a.yeolgi(GisaHwamyeon())
                    }
                }
                dap("$nm, 최신 기사부터 읽어 드립니다. 다음 기사라고 하시면 넘어갑니다.", false)
            }
            return true
        }
        if (tv || radio) {
            b.chaeneolMalro(z, if (tv) "tv" else "radio") { m, mutneun -> dap(m, mutneun) }
            return true
        }
        if (eumak) {
            var q = t
            for (w in listOf("길 위의", "길위의", "길 위", "길거리", "틀어 주세요", "틀어 줘", "틀어줘", "틀어 봐", "틀어봐", "틀어", "들려 줘", "들려줘", "듣고 싶어", "듣자", "들을래",
                "음악", "노래", "좀", "곡", "줘")) {
                q = q.replace(w, " ")
            }
            q = q.trim { it.isWhitespace() || ".,!?~·'\"".indexOf(it) >= 0 }
            for (p in listOf("을", "를")) if (q.endsWith(p) && q.length > 2) q = q.dropLast(1).trim()
            if (BangsongSeol.eumakTk.isEmpty()) {   // 2.19.0 음악 전체는 다시 열쇠
                b.nugunaTeulgi("", "나스 음악 열쇠를 아직 넣지 않으셔서 누구나 음악을 틉니다. 지나는 고장 노래는 열쇠 없이 고장 노래 틀어 줘라고 하시면 됩니다.")
                dap("", false)
                return true
            }
            if (q.isEmpty()) {
                if (b.itda && b.meomchum) { dap("", false); b.meomchumTogeul(); return true }
                b.galraeTeulgi("가요", "")
                dap("", false)
                return true
            }
            b.biseutQ = null
            b.malChatgi(q) { m -> dap(m, b.biseutQ != null) }   // 2.14.0 비슷한 곡을 여쭈었으면 대답을 기다림
            return true
        }
        return false
    }
}
