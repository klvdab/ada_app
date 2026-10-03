// 안드로이드 길눈 — 긴급통화서비스(2.6.0, 빌드 261002-A9, 대표님 지시: 아이폰 길눈의 긴급통화서비스를 안드로이드에도 똑같이)
// 아이폰 GinGeup.swift(2.4.1~2.12.1)·Jiin.swift 를 같은 차례, 같은 말, 같은 나스 약속으로 옮겼습니다.
//   가족·지인(고르신 한 분), 자원봉사자, 현장영상해설사에게 화상통화를 청합니다.
//   웹 길눈 buleugi.html·domum4.html과 같은 길: 나스 /eyec/rel.php 로 부르고(call·jindo·hangup),
//   받으시면 곧장(누르지 않아도) 뒤 카메라와 마이크로 화상통화(WebRTC, 신호는 rel.php sig_put·sig_get, 나스 턴 서버).
//   받는 분 화면(nun4.html·자봉 앱)은 지금 그대로입니다. 길눈이 먼저 제안(offer)을 보내고 받는 분이 답(answer)을 보냅니다.
//   이사장님 지시: 1분 30초 기다림, 말소리는 요청 시작·받음·연결 실패 때만, 통화 중에는 길눈 말소리를 내지 않음(경고는 길게 진동).
// 안드로이드에서 더한 것:
//   통화 중에는 화면이 꺼지지 않게(카메라가 멈추지 않게). 길눈 화면이 닫히면 통화를 끊고 치움
//   카메라·마이크 허락은 요청을 누를 때 미리 여쭘(받으신 뒤 3초 안에 허락 창을 찾지 않아도 되게)
//   폰의 뒤로 동작을 통화 중에 하시면 통화를 끊고 알려 드림(묻지 않음)
//   갤럭시 워치의 긴급통화(두 번 눌러 확인) — 마지막으로 요청하신 가족·지인 한 분, 없으면 자원봉사자에게
// 가족·지인 명단 — 나스 /eyec/jiin.php(웹·아이폰 길눈과 같은 명부). 서버에는 이름과 열쇠만, 전화번호는 폰 안에만.
package kr.or.ada.app.gilnun

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.audio.JavaAudioDeviceModule
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

/** 2.12.0 (261004-I1, 이사장님 승인) 영상 다리(턴) 주소 — 나스 설정 쪽지 /eyec/ice.json 에서 읽음.
 *  다리를 리눅스 서버로 옮기는 날 앱을 새로 받지 않고 쪽지 한 줄로 넘어가게. 못 읽으면 마지막으로 받은 것, 그것도 없으면 나스 다리(3478).
 *  길눈과 자봉이 함께 씀 */
object IceJuso {
    private val il = Executors.newSingleThreadExecutor()
    private var d: SharedPreferences? = null
    @Volatile private var majimak: String? = null

    fun sijak(c: Context) {
        if (d != null) return
        d = c.applicationContext.getSharedPreferences("gilnun_ice", Context.MODE_PRIVATE)
        majimak = d?.getString("ice", null)
    }

    /** 통화를 청하거나 울릴 때 미리 받아 둠 */
    fun gaengsin() {
        il.execute {
            val t = GinGeup.getText("/eyec/ice.json", emptyMap()) ?: return@execute
            try {
                val a = JSONObject(t).optJSONArray("ice") ?: return@execute
                if (a.length() == 0) return@execute
                majimak = a.toString()
                d?.edit()?.putString("ice", majimak)?.apply()
            } catch (e: Exception) {}
        }
    }

    fun servers(): List<PeerConnection.IceServer> {
        val s = majimak
        if (s != null) {
            try {
                val a = JSONArray(s)
                val l = (0 until a.length()).mapNotNull { i ->
                    val o = a.optJSONObject(i) ?: return@mapNotNull null
                    val u = o.optJSONArray("urls") ?: return@mapNotNull null
                    val urls = (0 until u.length()).map { u.optString(it) }.filter { it.isNotEmpty() }
                    if (urls.isEmpty()) return@mapNotNull null
                    val b = PeerConnection.IceServer.builder(urls)
                    val un = o.optString("username", "")
                    if (un.isNotEmpty()) b.setUsername(un).setPassword(o.optString("credential", ""))
                    b.createIceServer()
                }
                if (l.isNotEmpty()) return l
            } catch (e: Exception) {}
        }
        return listOf(
            PeerConnection.IceServer.builder(listOf("stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302")).createIceServer(),
            PeerConnection.IceServer.builder(listOf("turn:221.146.173.20:3478?transport=udp", "turn:221.146.173.20:3478?transport=tcp"))
                .setUsername("gilnun").setPassword("gilnun-turn-260911-v8k2q").createIceServer()
        )
    }
}

enum class GinGeupGalrae(val kod: String, val ireum: String) {
    JIIN("jiin", "가족·지인"),
    HAEBONG("haebong", "자원봉사자"),
    HAESEOLSA("haeseolsa", "현장영상해설사"),
    /** 2.11.0 가족·지인이 받지 않으셨을 때 — 자원봉사자와 현장영상해설사 함께(나스 rel.php 261004-1 dowum) */
    DOWUM("dowum", "자원봉사자와 현장영상해설사")
}

enum class GinGeupSangtae { EOPSEUM, YOCHEONG, YEONGYEOL, TONGHWA }

object GinGeup {
    private const val REL = "/eyec/rel.php"
    private const val HANDO = 90.0          // 1분 30초 기다림(초)
    private val main = Handler(Looper.getMainLooper())
    private val il = Executors.newSingleThreadExecutor()       // 부르기·진도·끊기
    private val sinhoIl = Executors.newSingleThreadExecutor()  // 신호 보내기 — 차례가 섞이지 않게 하나씩
    private var ctx: Context? = null

    /** 지금 길눈 화면(허락 여쭙기·화면 깨우기에 씀) */
    var hwalseong: WeakReference<GilnunActivity>? = null
    /** 형편이 바뀌면 화면이 채움 — 상태가 바뀌었는지, 글만 바뀌었는지는 화면이 가림 */
    var byeonhwa: (() -> Unit)? = null

    var sangtae = GinGeupSangtae.EOPSEUM
        private set
    var geul = ""
        private set
    /** 한마디 먼저 남기기 — 받는 분 화면에 뜸 */
    var malHan = ""
    /** 2.11.0 (261004-G1, 이사장님 승인) 가족·지인이 받지 않으셨을 때 — 자원봉사자와 현장영상해설사에게 넘길지 여쭘 */
    var neomgilkka = false
    var galrae = GinGeupGalrae.JIIN
        private set
    var saram: JiinSaram? = null
        private set

    private var room = ""
    private var sijakTtae = 0L
    private var sigN = 0
    private var sigMutneun = false
    private var cheot = true
    private var telMal = false
    private var junbiMal = false
    private var dasiHan = false

    private var factory: PeerConnectionFactory? = null
    private var egl: EglBase? = null
    private var pc: PeerConnection? = null
    private var capturer: CameraVideoCapturer? = null
    private var stHelper: SurfaceTextureHelper? = null
    private var vSrc: VideoSource? = null
    private var aSrc: AudioSource? = null
    private var sorijariBakkum = false

    /** 3초마다 진도 묻기 — 요청 중일 때만 이어 돎 */
    private val jindoR = object : Runnable {
        override fun run() {
            if (sangtae != GinGeupSangtae.YOCHEONG || room.isEmpty()) return
            jindo()
            if (sangtae == GinGeupSangtae.YOCHEONG) main.postDelayed(this, 3000)
        }
    }
    /** 1초마다 신호 받기 — 통화를 여는 동안·통화 중에만 이어 돎 */
    private val sigR = object : Runnable {
        override fun run() {
            if (pc == null || room.isEmpty()) return
            sigPoll()
            main.postDelayed(this, 1000)
        }
    }

    fun sijak(c: Context) {
        if (ctx == null) ctx = c.applicationContext
        Jiin.sijak(c)
        IceJuso.sijak(c)
    }

    /** 받는 분 화면에 뜰 내 이름 — 설정 탭에서 */
    var naIrum: String
        get() = Seoljeong.naIrum
        set(v) { Seoljeong.naIrum = v }

    private fun bakkum(s: GinGeupSangtae) {
        sangtae = s
        // 통화 중에는 화면이 꺼지지 않게 — 카메라가 멈추지 않게
        val a = hwalseong?.get()
        if (a != null && !a.isFinishing) {
            if (s == GinGeupSangtae.EOPSEUM) a.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else a.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        byeonhwa?.invoke()
    }

    private fun geulBakkum(t: String) {
        if (geul == t) return
        geul = t
        byeonhwa?.invoke()
    }

    // MARK: 부르기

    fun yocheong(g: GinGeupGalrae, s: JiinSaram? = null) {
        main.post { yocheongSok(g, s) }
    }

    private fun yocheongSok(g: GinGeupGalrae, s: JiinSaram?) {
        if (sangtae != GinGeupSangtae.EOPSEUM) return
        if (ctx == null) return
        IceJuso.gaengsin()
        neomgilkka = false
        galrae = g
        saram = s
        cheot = true
        telMal = false
        junbiMal = false
        dasiHan = false
        room = "j" + UUID.randomUUID().toString().lowercase(Locale.US).filter { it.isLetterOrDigit() }.take(10)
        sijakTtae = System.currentTimeMillis()
        if (g == GinGeupGalrae.JIIN && s != null) Jiin.majimakNoki(s.id)   // 워치 긴급통화가 부를 한 분
        MalHagi.meomchum()   // 긴급통화 중에는 마이크를 통화에 내어 줌
        bakkum(GinGeupSangtae.YOCHEONG)
        var mal: String
        if (g == GinGeupGalrae.JIIN) {
            val nm = s?.name ?: "가족·지인"
            mal = "$nm 님께 화상통화를 요청합니다."
            if (s != null && Jiin.tel(s.id).isNotEmpty()) mal += " 전화 걸기 단추도 있습니다."
        } else {
            mal = "${g.ireum}에게 호출 신호를 보냅니다."
        }
        geul = mal
        byeonhwa?.invoke()
        Sori.mal(mal)
        Girok.namgi("gingeup_yocheong", mapOf("g" to g.kod))
        heorakMiri()

        var where = ""
        val jari = HashMap<String, String>()
        Wichi.jigeum?.let { w ->
            where = String.format(Locale.US, "%.3f,%.3f", w.lat, w.lon)
            jari["lat"] = String.format(Locale.US, "%.6f", w.lat)
            jari["lon"] = String.format(Locale.US, "%.6f", w.lon)
        }
        val meonjeo = if (g == GinGeupGalrae.HAESEOLSA) "haeseolsa" else "jiin"
        val target = if (g == GinGeupGalrae.JIIN) (s?.k ?: "") else ""
        val q = hashMapOf("a" to "call", "room" to room, "who" to naIrum.ifEmpty { "길눈 이용자" },
            "where" to where, "gil" to malHan, "meonjeo" to meonjeo, "galrae" to g.kod, "target" to target)
        // 아이폰은 여정의 목적지를 실음 — 안드로이드는 점지도 따라 걷는 중이면 그 길의 도착지
        val jg = JeomEngine.gil
        if (JeomEngine.georeoJung && jg != null && jg.to.isNotEmpty()) {
            q["mok"] = jg.to
            jg.pts.lastOrNull()?.let { q["mlat"] = it.lat.toString(); q["mlon"] = it.lon.toString() }
        }
        val bureum = hashMapOf("a" to "bureum", "room" to room, "gil" to malHan, "meonjeo" to meonjeo, "galrae" to g.kod, "target" to target)
        for ((k, v) in jari) if (!bureum.containsKey(k)) bureum[k] = v
        val r = room
        val owner = Jiin.owner
        il.execute {
            // 나스 통화 중계(턴) 서버가 꺼져 있으면 켬 — 웹 길눈과 같게
            getText("/jungye/turn_ctl.php", mapOf("k" to "turn-260911-p7wq", "a" to "kyeogi"))
            getText("jeom_db.php", bureum)
            // 지인은 먼저 찍어 두고(누구에게 가는지 서버가 알게) 그다음 부름
            if (g == GinGeupGalrae.JIIN && s != null) {
                getText("/eyec/jiin.php", mapOf("a" to "jjik", "owner" to owner, "id" to s.id, "room" to r))
            }
            val ok = getText(REL, q) != null
            main.post {
                if (sangtae != GinGeupSangtae.YOCHEONG || room != r) return@post
                if (!ok) {
                    kkeut("요청을 보내지 못했습니다. 통신을 확인하시고 다시 눌러 주십시오.")
                    return@post
                }
                main.removeCallbacks(jindoR)
                main.post(jindoR)   // 곧바로 한 번, 그 뒤 3초마다
            }
        }
    }

    /** 요청 그만두기 / 통화 끊기 */
    fun geumanhagi() {
        main.post {
            if (sangtae == GinGeupSangtae.EOPSEUM) return@post
            if (pc != null) sigPut(JSONObject().put("t", "bye"))
            val tonghwa = sangtae == GinGeupSangtae.TONGHWA
            hangup()
            kkeut(if (tonghwa) "통화를 끊었습니다." else "요청을 그만두었습니다.")
        }
    }

    /** 길눈 화면이 닫힐 때 — 받는 분께 끊었다고 알리고 모두 치움 */
    fun dateum() {
        if (sangtae == GinGeupSangtae.EOPSEUM) return
        if (pc != null) sigPut(JSONObject().put("t", "bye"))
        hangup()
        kkeut("통화를 끊었습니다.")
    }

    /** 가족·지인께 전화 걸기 — 전화 앱을 번호를 넣은 채로 엶 */
    fun jeonhwa(a: GilnunActivity) {
        val s = saram ?: return
        val t = Jiin.tel(s.id)
        if (t.isEmpty()) return
        try {
            a.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$t")))
        } catch (e: Exception) {
            Sori.mal("전화 앱을 열지 못했습니다.")
        }
    }

    /** 갤럭시 워치의 긴급통화 — 마지막으로 요청하신 가족·지인 한 분(받겠다고 하신 분이 한 분뿐이면 그분), 없으면 자원봉사자.
     *  명단을 아직 받지 않았으면 먼저 받아 봄(3초 안에). 워치에 돌려줄 말은 dap 으로 */
    fun watchYocheong(dap: (String) -> Unit) {
        if (sangtae != GinGeupSangtae.EOPSEUM) { dap("이미 긴급통화 중입니다. 폰 길눈의 긴급통화서비스 화면에서 끊으실 수 있습니다."); return }
        hwamyeonYeolgi()
        var han = false
        val hagi: () -> Unit = {
            if (!han) {
                han = true
                val s = Jiin.majimak()
                if (s != null) {
                    yocheong(GinGeupGalrae.JIIN, s)
                    dap("폰 길눈이 ${s.name} 님께 화상통화를 요청합니다.")
                } else {
                    yocheong(GinGeupGalrae.HAEBONG)
                    dap("폰 길눈이 자원봉사자에게 호출 신호를 보냅니다.")
                }
            }
        }
        if (Jiin.bulreoom) { hagi(); return }
        Jiin.bureogi { hagi() }
        main.postDelayed({ hagi() }, 3000)
    }

    /** 길 찾기 탭의 긴급통화서비스 화면을 엶(말로 하기·워치에서 요청할 때 — 끊기 단추가 바로 보이게) */
    fun hwamyeonYeolgi() {
        val a = hwalseong?.get() ?: return
        if (a.isFinishing) return
        if (a.wiHwamyeon is GinGeupHwamyeon) return
        a.cheotHwamyeonEuro(GinGeupHwamyeon())
    }

    private fun jindo() {
        if (sangtae != GinGeupSangtae.YOCHEONG || room.isEmpty()) return
        // 나스가 답하지 않아도 폰 시계로 한도를 지킴 — 요청이 끝없이 걸려 있지 않게
        if ((System.currentTimeMillis() - sijakTtae) / 1000.0 >= HANDO + 10) {
            hangup()
            kkeut("연결되지 못했습니다. 통신이 약할 수 있습니다. 다시 요청하시거나 전화를 거실 수 있습니다.")
            return
        }
        val r = room
        il.execute {
            val t = getText(REL, mapOf("a" to "jindo", "room" to r)) ?: return@execute
            val j = try { JSONObject(t) } catch (e: Exception) { return@execute }
            if (!j.optBoolean("ok", false)) return@execute
            main.post { jindoBatda(j, r) }
        }
    }

    private fun jindoBatda(j: JSONObject, r: String) {
        if (sangtae != GinGeupSangtae.YOCHEONG || r != room) return
        val s = Jeomjido.su(j, "secs") ?: ((System.currentTimeMillis() - sijakTtae) / 1000.0)
        val taken = j.optString("taken", "").trim()
        if (!j.isNull("taken") && taken.isNotEmpty()) {
            main.removeCallbacks(jindoR)
            geul = "$taken 님이 받으셨습니다. 잇는 중입니다."
            bakkum(GinGeupSangtae.YEONGYEOL)
            Sori.mal("$taken 님이 받으셨습니다. 카메라와 마이크를 켭니다.")
            Girok.namgi("gingeup_badeum")
            main.postDelayed({ if (room == r) tonghwaSijak() }, 3000)
            // 받으신 뒤 40초 안에 통화가 이어지지 않으면 마침
            main.postDelayed({
                if (sangtae == GinGeupSangtae.YEONGYEOL && room == r) {
                    hangup()
                    kkeut("통화를 잇지 못했습니다. 다시 요청해 주십시오.")
                }
            }, 40000)
            return
        }
        if (galrae == GinGeupGalrae.JIIN) {
            val nm = saram?.name ?: "가족·지인"
            if (s >= 15 && !telMal) {
                telMal = true
                val tel = saram?.let { Jiin.tel(it.id) } ?: ""
                Sori.mal("$nm 님이 아직 받지 않으셨습니다. 계속 요청 중입니다." + (if (tel.isEmpty()) "" else " 전화로 하시려면 전화 걸기를 누르십시오."))
            }
            val targetOn = when (val v = j.opt("targetOn")) {
                is Boolean -> v
                is Number -> v.toInt() != 0
                else -> true
            }
            if (cheot && !targetOn && !junbiMal) {
                junbiMal = true
                Sori.mal("$nm 님은 아직 자봉 앱으로 받기를 켜지 않으셨거나 받지 않기로 해 두셔서 신호가 닿지 않을 수 있습니다.")
            }
            if (s >= HANDO) {
                hangup()
                neomgilkka = true
                kkeut("$nm 님이 받지 않으십니다. 자원봉사자와 현장영상해설사에게 요청할까요?")
                return
            }
        } else {
            val n = (Jeomjido.su(j, "dae") ?: 0.0).toInt()
            val gi = galrae.ireum
            geulBakkum(if (n > 0) "$gi ${n}분께 호출 중입니다." else "지금 받으실 수 있는 ${gi}가 없습니다.")
            if (cheot && n == 0) {
                hangup()
                kkeut("지금 받으실 수 있는 ${gi}가 없습니다. 다른 갈래를 고르시거나 잠시 뒤 다시 호출해 주십시오.")
                return
            }
            if (s >= HANDO) {
                hangup()
                kkeut("${gi}와 연결되지 못했습니다. 다른 갈래를 고르시거나 다시 호출해 주십시오.")
                return
            }
        }
        cheot = false
    }

    private fun hangup() {
        val r = room
        if (r.isEmpty()) return
        il.execute { getText(REL, mapOf("a" to "hangup", "room" to r)) }
    }

    private fun kkeut(mal: String) {
        main.removeCallbacks(jindoR)
        main.removeCallbacks(sigR)
        chiugi()
        room = ""
        sigMutneun = false
        Sori.tonghwaJung = false
        geul = mal
        bakkum(GinGeupSangtae.EOPSEUM)
        Sori.mal(mal)
        Girok.namgi("gingeup_kkeut")
    }

    /** 카메라·통화·소리 자리를 모두 치움 — 화면 줄에서만 부름(WebRTC 의 알림 줄에서 부르면 멈춰 섬) */
    private fun chiugi() {
        try { capturer?.stopCapture() } catch (e: Exception) {}
        try { capturer?.dispose() } catch (e: Exception) {}
        capturer = null
        try { pc?.dispose() } catch (e: Exception) {}   // 보내던 소리·화면 줄도 함께 치움
        pc = null
        try { vSrc?.dispose() } catch (e: Exception) {}
        vSrc = null
        try { aSrc?.dispose() } catch (e: Exception) {}
        aSrc = null
        try { stHelper?.dispose() } catch (e: Exception) {}
        stHelper = null
        sorijariDoedollim()
    }

    // MARK: 허락

    private fun heorakItda(p: String): Boolean {
        val c = ctx ?: return false
        return ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED
    }

    /** 요청을 누를 때 — 카메라·마이크 허락이 없으면 미리 여쭘(통화를 기다리는 동안 답하시게) */
    private fun heorakMiri() {
        if (heorakItda(Manifest.permission.CAMERA) && heorakItda(Manifest.permission.RECORD_AUDIO)) return
        val a = hwalseong?.get() ?: return
        if (a.isFinishing) return
        a.tonghwaHeorak { }
    }

    // MARK: 화상통화

    private fun tonghwaSijak() {
        if (sangtae != GinGeupSangtae.YEONGYEOL || room.isEmpty()) return
        val a = hwalseong?.get()
        if ((!heorakItda(Manifest.permission.CAMERA) || !heorakItda(Manifest.permission.RECORD_AUDIO)) && a != null && !a.isFinishing) {
            a.tonghwaHeorak { main.post { tonghwaIeum() } }
            return
        }
        tonghwaIeum()
    }

    private fun factoryJunbi(c: Context): PeerConnectionFactory? {
        factory?.let { return it }
        return try {
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(c).createInitializationOptions())
            val e = EglBase.create()
            egl = e
            val adm = JavaAudioDeviceModule.builder(c)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule()
            val f = PeerConnectionFactory.builder()
                .setAudioDeviceModule(adm)
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(e.eglBaseContext, true, true))
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(e.eglBaseContext))
                .createPeerConnectionFactory()
            factory = f
            f
        } catch (x: Throwable) {
            Girok.namgi("gingeup_oryu", mapOf("dan" to "factory", "e" to (x.message ?: "")))
            null
        }
    }

    private fun tonghwaIeum() {
        if (sangtae != GinGeupSangtae.YEONGYEOL || pc != null) return
        val c = ctx ?: return
        val f = factoryJunbi(c)
        if (f == null) { hangup(); kkeut("통화를 열지 못했습니다. 다시 요청해 주십시오."); return }
        Sori.tonghwaJung = true
        Sori.meomchugi()
        val ice = IceJuso.servers()   // 2.12.0 나스 설정 쪽지에서
        val cfg = PeerConnection.RTCConfiguration(ice).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val p = try { f.createPeerConnection(cfg, Jikkim()) } catch (x: Throwable) { null }
        if (p == null) {
            hangup()
            kkeut("통화를 열지 못했습니다. 다시 요청해 주십시오.")
            return
        }
        pc = p
        sorijariKyeogi()
        try {
            val a0 = f.createAudioSource(MediaConstraints())
            aSrc = a0
            p.addTrack(f.createAudioTrack("a0", a0), listOf("s0"))
            val v0 = f.createVideoSource(false)
            vSrc = v0
            p.addTrack(f.createVideoTrack("v0", v0), listOf("s0"))
            kameraKyeogi(c, v0)
        } catch (x: Throwable) {
            Girok.namgi("gingeup_oryu", mapOf("dan" to "track", "e" to (x.message ?: "")))
        }
        sigN = 0
        sigMutneun = false
        main.removeCallbacks(sigR)
        main.postDelayed(sigR, 1000)
        offerBonaegi(false)
        geulBakkum("도와주실 분과 잇는 중입니다.")
        Girok.namgi("gingeup_tonghwa_sijak")
    }

    /** 뒤 카메라를 켬 — 가로 1280에 가깝게, 초당 24장(아이폰과 같음). 허락이 없으면 목소리만 */
    private fun kameraKyeogi(c: Context, v0: VideoSource) {
        if (!heorakItda(Manifest.permission.CAMERA)) {
            Girok.namgi("gingeup_oryu", mapOf("dan" to "kamera_heorak"))
            return
        }
        val en: CameraEnumerator = if (Camera2Enumerator.isSupported(c)) Camera2Enumerator(c) else Camera1Enumerator(true)
        val names = en.deviceNames
        val nm = names.firstOrNull { en.isBackFacing(it) } ?: names.firstOrNull() ?: return
        val cap = en.createCapturer(nm, null) ?: return
        val h = SurfaceTextureHelper.create("GinGeupKamera", egl?.eglBaseContext)
        stHelper = h
        cap.initialize(h, c, v0.capturerObserver)
        cap.startCapture(1280, 720, 24)
        capturer = cap
    }

    private fun offerBonaegi(iceRestart: Boolean) {
        val p = pc ?: return
        val m = MediaConstraints()
        m.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        m.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        if (iceRestart) m.mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
        p.createOffer(object : SdpPyeon() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp == null) return
                main.post {
                    if (pc !== p) return@post
                    p.setLocalDescription(object : SdpPyeon() {
                        override fun onSetSuccess() {
                            main.post { if (pc === p) sigPut(JSONObject().put("t", "offer").put("sdp", sdp.description)) }
                        }
                    }, sdp)
                }
            }
        }, m)
    }

    /** 신호 보내기 — rel.php sig_put, 받는 분(helper)께. 본문은 JSON 그대로 */
    private fun sigPut(m: JSONObject) {
        val r = room
        if (r.isEmpty()) return
        val body = m.toString()
        sinhoIl.execute {
            try {
                val c = URL(Tongsin.juso(REL, mapOf("a" to "sig_put", "room" to r, "to" to "helper"))).openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.doOutput = true
                c.useCaches = false
                c.connectTimeout = 10000
                c.readTimeout = 10000
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                c.responseCode
                c.disconnect()
            } catch (e: Exception) {}
        }
    }

    /** 신호 받기 — 1초마다 rel.php sig_get(나에게 온 것, n 번째 다음부터) */
    private fun sigPoll() {
        if (pc == null || sigMutneun || room.isEmpty()) return
        sigMutneun = true
        val r = room
        val n = sigN
        il.execute {
            val t = getText(REL, mapOf("a" to "sig_get", "room" to r, "to" to "caller", "n" to n.toString()))
            main.post {
                sigMutneun = false
                if (t == null || r != room) return@post
                val j = try { JSONObject(t) } catch (e: Exception) { return@post }
                if (!j.optBoolean("ok", false)) return@post
                Jeomjido.su(j, "cur")?.let { sigN = maxOf(sigN, it.toInt()) }
                val l = j.optJSONArray("list") ?: JSONArray()
                for (i in 0 until l.length()) {
                    val m = l.optJSONObject(i)?.optJSONObject("msg") ?: continue
                    sinhoBatda(m)
                    if (pc == null) break   // 끊었다는 신호를 받았으면 그만
                }
            }
        }
    }

    private fun sinhoBatda(m: JSONObject) {
        val p = pc ?: return
        when (m.optString("t", "")) {
            "answer" -> {
                val sdp = m.optString("sdp", "")
                if (sdp.isEmpty()) return
                p.setRemoteDescription(SdpPyeon(), SessionDescription(SessionDescription.Type.ANSWER, sdp))
            }
            "ice" -> {
                val c = m.optJSONObject("c") ?: return
                val cand = c.optString("candidate", "")
                if (cand.isEmpty()) return
                val idx = (Jeomjido.su(c, "sdpMLineIndex") ?: 0.0).toInt()
                val mid = if (c.isNull("sdpMid")) "" else c.optString("sdpMid", "")
                p.addIceCandidate(IceCandidate(mid, idx, cand))
            }
            "bye" -> kkeut("도와주시던 분이 끊었습니다.")
        }
    }

    private fun iuGeotda() {
        if (sangtae == GinGeupSangtae.TONGHWA) return
        geul = "통화 중입니다."
        bakkum(GinGeupSangtae.TONGHWA)
        jindongGilge(false)
        // 통화 중에는 길눈 말소리를 내지 않으므로 톡백으로 한 번만
        Sori.tokbaek?.invoke("연결되었습니다. 말씀하십시오.")
        // 이어폰이 없으면 스피커로 — 귀에 대지 않아도 들리게
        seupikeo()
        Girok.namgi("gingeup_yeongyeol")
    }

    // MARK: 소리 자리 — 통화 모드, 이어폰이 없으면 스피커

    private val am: AudioManager? get() = ctx?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private fun sorijariKyeogi() {
        val a = am ?: return
        try {
            a.mode = AudioManager.MODE_IN_COMMUNICATION
            sorijariBakkum = true
        } catch (e: Exception) {}
    }

    private fun iyeoponItda(a: AudioManager): Boolean {
        val l = a.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return l.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET || (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    }

    @Suppress("DEPRECATION")
    private fun seupikeo() {
        val a = am ?: return
        try {
            if (iyeoponItda(a)) return
            if (Build.VERSION.SDK_INT >= 31) {
                val sp = a.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (sp != null) a.setCommunicationDevice(sp)
            } else {
                a.isSpeakerphoneOn = true
            }
        } catch (e: Exception) {}
    }

    @Suppress("DEPRECATION")
    private fun sorijariDoedollim() {
        if (!sorijariBakkum) return
        sorijariBakkum = false
        val a = am ?: return
        try {
            if (Build.VERSION.SDK_INT >= 31) a.clearCommunicationDevice() else a.isSpeakerphoneOn = false
            a.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {}
    }

    /** 진동 — 연결되면 짧게 두 번, 통화 중 경고는 길게(아이폰 Jindong 과 같음) */
    @Suppress("DEPRECATION")
    fun jindongGilge(gilge: Boolean) {
        val c = ctx ?: return
        try {
            val v: android.os.Vibrator? = if (Build.VERSION.SDK_INT >= 31)
                (c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager)?.defaultVibrator
            else c.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            val gil = if (gilge) longArrayOf(0, 700) else longArrayOf(0, 80, 100, 80)
            if (Build.VERSION.SDK_INT >= 26) v?.vibrate(android.os.VibrationEffect.createWaveform(gil, -1)) else v?.vibrate(gil, -1)
        } catch (e: Exception) {}
    }

    // MARK: 통화 지킴이 — WebRTC 알림은 다른 줄에서 오므로 화면 줄로 넘겨서

    private class Jikkim : PeerConnection.Observer {
        override fun onSignalingChange(s: PeerConnection.SignalingState?) {}
        override fun onIceConnectionReceivingChange(b: Boolean) {}
        override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidatesRemoved(c: Array<out IceCandidate>?) {}
        override fun onAddStream(s: MediaStream?) {}
        override fun onRemoveStream(s: MediaStream?) {}
        override fun onDataChannel(d: DataChannel?) {}
        override fun onRenegotiationNeeded() {}

        override fun onIceCandidate(c: IceCandidate?) {
            if (c == null) return
            val o = JSONObject().put("candidate", c.sdp).put("sdpMid", c.sdpMid ?: "0").put("sdpMLineIndex", c.sdpMLineIndex)
            GinGeup.main.post { GinGeup.iceBonaegi(o) }
        }

        override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {
            GinGeup.main.post { GinGeup.iceSangtae(s) }
        }
    }

    private fun iceBonaegi(o: JSONObject) {
        if (pc != null) sigPut(JSONObject().put("t", "ice").put("c", o))
    }

    private fun iceSangtae(s: PeerConnection.IceConnectionState?) {
        if (pc == null) return
        when (s) {
            PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> iuGeotda()
            PeerConnection.IceConnectionState.FAILED -> {
                if (!dasiHan) {
                    dasiHan = true
                    geulBakkum("연결이 막혀 한 번 더 잇는 중입니다.")
                    offerBonaegi(true)
                } else {
                    hangup()
                    kkeut("연결하지 못했습니다. 다시 요청해 주십시오.")
                }
            }
            PeerConnection.IceConnectionState.DISCONNECTED -> geulBakkum("연결이 잠시 끊겼습니다. 다시 잇는 중입니다.")
            else -> {}
        }
    }

    /** 제안·답 알림의 빈 틀 — 필요한 것만 덮어씀 */
    private open class SdpPyeon : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(e: String?) { Girok.namgi("gingeup_oryu", mapOf("dan" to "sdp_create", "e" to (e ?: ""))) }
        override fun onSetFailure(e: String?) { Girok.namgi("gingeup_oryu", mapOf("dan" to "sdp_set", "e" to (e ?: ""))) }
    }

    // MARK: 나스 — 일하는 줄에서만 부름(기다리며 받음). 200 이면 본문, 아니면 null

    fun getText(pail: String, q: Map<String, String>): String? = try {
        val c = URL(Tongsin.juso(pail, q)).openConnection() as HttpURLConnection
        c.useCaches = false
        c.connectTimeout = 10000
        c.readTimeout = 10000
        val t = if (c.responseCode == 200) c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } else null
        c.disconnect()
        t
    } catch (e: Exception) { null }
}

/** 가족·지인 한 분 */
data class JiinSaram(val id: String, val name: String, val k: String, val state: String) {
    val badeum: Boolean get() = state == "받음"
}

/** 가족·지인 명단 — 나스 /eyec/jiin.php(웹·아이폰 길눈과 같은 명부). 서버에는 이름과 열쇠만, 전화번호는 폰 안에만 */
object Jiin {
    private const val PHP = "/eyec/jiin.php"
    private val main = Handler(Looper.getMainLooper())
    private val il = Executors.newSingleThreadExecutor()
    private var d: SharedPreferences? = null

    var mokrok: List<JiinSaram> = emptyList()
        private set
    var bulreoom = false
        private set

    fun sijak(c: Context) {
        if (d == null) d = c.applicationContext.getSharedPreferences("gilnun_jiin", Context.MODE_PRIVATE)
    }

    /** 주인 열쇠 — 한 번 만들면 바뀌지 않음(아이폰 Yeolsoe.juin 과 같은 꼴, 스물두 자) */
    val owner: String
        get() {
            val p = d ?: return ""
            val v = p.getString("juin", null)
            if (v != null && v.length >= 16) return v
            val ja = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            val n = (0 until 22).map { ja.random() }.joinToString("")
            p.edit().putString("juin", n).apply()
            return n
        }

    /** 받는 분께 보낼 초대 주소 — 한 번 열어 "받겠습니다"를 누르시면 끝 */
    fun chodaeJuso(k: String): String = "https://lvd.ada.or.kr/eyec/badgi.html?k=" + java.net.URLEncoder.encode(k, "UTF-8")

    fun chodaeGeul(s: JiinSaram): String =
        "${s.name}님, 제가 앞이 보이지 않을 때 도움을 청하면 이 주소로 알려 드립니다. 한 번만 열어서 받겠습니다를 눌러 주십시오. " + chodaeJuso(s.k)

    /** 2.11.0 이음 번호(여섯 자리, 30분) — (번호, 못 받은 까닭) */
    fun ieumBeonho(kkeut: (String?, String) -> Unit) {
        val ow = owner
        val nm = GinGeup.naIrum.ifEmpty { "길눈 이용자" }
        il.execute {
            val t = GinGeup.getText(PHP, mapOf("a" to "ieum_man", "owner" to ow, "who" to nm))
            val o = try { if (t == null) null else JSONObject(t) } catch (e: Exception) { null }
            main.post {
                if (o == null) { kkeut(null, "이음 번호를 받지 못했습니다. 통신이 끊겼을 수 있습니다."); return@post }
                val b = o.optString("beonho", "")
                if (!o.optBoolean("ok", false) || b.length != 6) { kkeut(null, o.optString("error", "").ifEmpty { "이음 번호를 받지 못했습니다." }); return@post }
                Girok.namgi("jiin_ieum_man")
                kkeut(b, "")
            }
        }
    }

    /** 명단 받기 — 결과(받았는가)는 화면 줄에서 */
    fun bureogi(kkeut: ((Boolean) -> Unit)? = null) {
        val ow = owner
        il.execute {
            val t = GinGeup.getText(PHP, mapOf("a" to "list", "owner" to ow))
            val o = try { if (t == null) null else JSONObject(t) } catch (e: Exception) { null }
            var l: List<JiinSaram>? = null
            if (o != null && o.optBoolean("ok", false)) {
                val a = o.optJSONArray("jiin") ?: JSONArray()
                l = (0 until a.length()).mapNotNull { i ->
                    val x = a.optJSONObject(i) ?: return@mapNotNull null
                    JiinSaram(x.optString("id", ""), x.optString("name", ""), x.optString("k", ""), x.optString("state", "기다리는 중"))
                }
            }
            main.post {
                if (l != null) { mokrok = l; bulreoom = true }
                kkeut?.invoke(l != null)
            }
        }
    }

    /** 새 사람 만들기 — (사람, 못 한 까닭) */
    fun mandeulgi(name: String, tel: String, kkeut: (JiinSaram?, String) -> Unit) {
        val ow = owner
        il.execute {
            val t = GinGeup.getText(PHP, mapOf("a" to "man", "owner" to ow, "name" to name))
            val o = try { if (t == null) null else JSONObject(t) } catch (e: Exception) { null }
            main.post {
                if (o == null) { kkeut(null, "만들지 못했습니다. 통신이 끊겼을 수 있습니다."); return@post }
                val id = o.optString("id", "")
                val k = o.optString("k", "")
                if (!o.optBoolean("ok", false) || id.isEmpty() || k.isEmpty()) {
                    kkeut(null, o.optString("error", "").ifEmpty { "만들지 못했습니다." })
                    return@post
                }
                if (tel.isNotEmpty()) telNoki(id, tel)
                Girok.namgi("jiin_man")
                val s = JiinSaram(id, name, k, "기다리는 중")
                bureogi { kkeut(s, "") }
            }
        }
    }

    fun jiugi(s: JiinSaram, kkeut: (() -> Unit)? = null) {
        val ow = owner
        telNoki(s.id, "")
        il.execute {
            GinGeup.getText(PHP, mapOf("a" to "jiwoo", "owner" to ow, "id" to s.id))
            main.post { bureogi { kkeut?.invoke() } }
        }
    }

    // MARK: 전화번호 — 폰 안에만

    private fun telMokrok(): JSONObject = try { JSONObject(d?.getString("jiinTel", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }

    fun tel(id: String): String = telMokrok().optString(id, "")

    fun telNoki(id: String, t: String) {
        val m = telMokrok()
        val beon = t.filter { "0123456789+".indexOf(it) >= 0 }
        if (beon.isEmpty()) m.remove(id) else m.put(id, beon)
        d?.edit()?.putString("jiinTel", m.toString())?.apply()
    }

    // MARK: 워치 긴급통화가 부를 한 분 — 마지막으로 요청하신 가족·지인

    fun majimakNoki(id: String) { d?.edit()?.putString("majimak", id)?.apply() }

    /** 마지막으로 요청하신 분이 아직 명단에 있으면 그분, 아니면 받겠다고 하신 분이 한 분뿐일 때 그분 */
    fun majimak(): JiinSaram? {
        val id = d?.getString("majimak", "") ?: ""
        mokrok.firstOrNull { it.id == id && id.isNotEmpty() }?.let { return it }
        val b = mokrok.filter { it.badeum }
        return if (b.size == 1) b[0] else null
    }
}
