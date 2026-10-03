// 안드로이드 자봉 — 긴급통화 통화: 길눈님(받는 쪽) 화상통화 (2.3.0, 빌드 261002-J1 — 아이폰 JabongTonghwa.swift 2.1.0과 같음)
// 길손님 길눈 앱이 뒤 카메라 영상과 말소리를 보내고(offer), 길눈님은 말소리로 답함(answer).
// 신호는 나스 rel.php sig_put·sig_get, 길은 나스 턴 서버 — 길눈 앱(GinGeup)과 짝.
// 통화 중에는 길눈 말소리를 내지 않음(대표님 지시). 15초 넘는 통화 끝에 고맙다는 말.
// 이어폰이 없으면 스피커로(아이폰이 수화기 대신 스피커로 돌리는 것과 같음). 통화 중에는 화면이 꺼지지 않게(화면이 맡음).
package kr.or.ada.app.jabong

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kr.or.ada.app.gilnun.Girok
import kr.or.ada.app.gilnun.IceJuso
import kr.or.ada.app.gilnun.Jeomjido
import kr.or.ada.app.gilnun.Sori
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.Executors

object JabongTonghwa {
    private const val REL = "/eyec/rel.php"
    private val main = Handler(Looper.getMainLooper())
    private val il = Executors.newSingleThreadExecutor()
    private val sinhoIl = Executors.newSingleThreadExecutor()   // 신호 보내기 — 차례가 섞이지 않게 하나씩

    var tonghwaJung = false; private set
    var iEojim = false; private set
    var geul = ""; private set
    var mok = ""; private set
    /** 형편이 바뀌면 화면이 채움 — 통화 화면 띄우기·닫기 */
    var byeonhwa: (() -> Unit)? = null
    /** 글만 바뀌면(화면 전체를 다시 그리지 않음) */
    var geulByeonhwa: (() -> Unit)? = null

    private var ctx: Context? = null
    private var room = ""
    private var pc: PeerConnection? = null
    private var factory: PeerConnectionFactory? = null
    private var egl: EglBase? = null
    private var aSrc: AudioSource? = null
    private var yeongsang: VideoTrack? = null
    private var hwamyeon: SurfaceViewRenderer? = null
    private var sigN = 0
    private var sigMutneun = false
    private var sijakT = 0L
    private var sorijariBakkum = false

    /** 1초마다 신호 받기 */
    private val sigR = object : Runnable {
        override fun run() {
            if (pc == null || room.isEmpty()) return
            sigPoll()
            main.postDelayed(this, 1000)
        }
    }

    private fun geulBakkum(t: String) {
        geul = t
        geulByeonhwa?.invoke()
    }

    fun sijak(c: Context, room0: String, mok0: String) {
        kkeunki(false)
        ctx = c.applicationContext
        room = room0
        mok = mok0
        tonghwaJung = true
        iEojim = false
        sijakT = System.currentTimeMillis()
        geul = "길손님과 잇는 중입니다."
        Sori.meomchugi()
        Sori.tonghwaJung = true
        val f = factoryJunbi(c.applicationContext)
        if (f == null) { geul = "통화를 열지 못했습니다."; byeonhwa?.invoke(); kkeunki(true); return }
        val ice = IceJuso.servers()   // 자봉 2.5.0 나스 설정 쪽지에서(길눈 GinGeup.kt 의 IceJuso)
        val cfg = PeerConnection.RTCConfiguration(ice).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val p = try { f.createPeerConnection(cfg, Jikkim()) } catch (x: Throwable) { null }
        if (p == null) {
            geul = "통화를 열지 못했습니다."
            byeonhwa?.invoke()
            kkeunki(true)
            return
        }
        pc = p
        sorijariKyeogi()
        try {
            val a0 = f.createAudioSource(MediaConstraints())
            aSrc = a0
            p.addTrack(f.createAudioTrack("a1", a0), listOf("s1"))
        } catch (x: Throwable) {
            Girok.namgi("jabong_tonghwa_oryu", mapOf("dan" to "track", "e" to (x.message ?: "")))
        }
        sigN = 0
        sigMutneun = false
        main.removeCallbacks(sigR)
        main.postDelayed(sigR, 1000)
        byeonhwa?.invoke()
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
            Girok.namgi("jabong_tonghwa_oryu", mapOf("dan" to "factory", "e" to (x.message ?: "")))
            null
        }
    }

    // MARK: 길손님 카메라 화면 — 통화 화면이 붙이고 뗌

    fun hwamyeonBuchigi(v: SurfaceViewRenderer) {
        if (hwamyeon === v) return
        hwamyeonTteoki()
        val e = egl ?: return
        try {
            v.init(e.eglBaseContext, null)
            v.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            v.setEnableHardwareScaler(true)
            hwamyeon = v
            yeongsang?.addSink(v)
        } catch (x: Throwable) {
            Girok.namgi("jabong_tonghwa_oryu", mapOf("dan" to "hwamyeon", "e" to (x.message ?: "")))
        }
    }

    fun hwamyeonTteoki() {
        val v = hwamyeon ?: return
        hwamyeon = null
        try { yeongsang?.removeSink(v) } catch (x: Throwable) {}
        try { v.release() } catch (x: Throwable) {}
    }

    private fun yeongsangBatda(t: VideoTrack) {
        if (yeongsang === t) return
        val h = hwamyeon
        if (h != null) try { yeongsang?.removeSink(h) } catch (x: Throwable) {}
        yeongsang = t
        if (h != null) try { t.addSink(h) } catch (x: Throwable) {}
    }

    /** 통화 끊기(길눈님이 누름) */
    fun kkeutnaegi() { kkeunki(true) }

    /** 통화를 정리. bonaegi 면 길손님 쪽에 끊었다고 알림 */
    fun kkeunki(bonaegi: Boolean) {
        if (!tonghwaJung) return
        if (bonaegi) sigPut(JSONObject().put("t", "bye"))
        val gil = (System.currentTimeMillis() - sijakT) / 1000
        val eojeotna = iEojim
        main.removeCallbacks(sigR)
        hwamyeonTteoki()
        yeongsang = null
        try { pc?.dispose() } catch (x: Throwable) {}
        pc = null
        try { aSrc?.dispose() } catch (x: Throwable) {}
        aSrc = null
        sorijariDoedollim()
        tonghwaJung = false
        iEojim = false
        Sori.tonghwaJung = false
        Girok.namgi("jabong_tonghwa_kkeut", mapOf("chou" to gil.toInt(), "ieojim" to eojeotna))
        if (eojeotna && gil > 15) {
            val b = JabongDaegi.byeol
            Sori.mal("고맙습니다. ${if (b.isEmpty()) "" else "$b 님, "}오늘 덕분에 한 분이 길을 찾았습니다.")
        }
        room = ""
        byeonhwa?.invoke()
    }

    // MARK: 신호

    private fun sigPut(m: JSONObject) {
        val r = room
        if (r.isEmpty()) return
        val body = m.toString()
        sinhoIl.execute { JbTongsin.postText(REL, mapOf("a" to "sig_put", "room" to r, "to" to "caller"), body, 10000) }
    }

    private fun sigPoll() {
        if (pc == null || sigMutneun || room.isEmpty()) return
        sigMutneun = true
        val r = room
        val n = sigN
        il.execute {
            val t = JbTongsin.getText(REL, mapOf("a" to "sig_get", "room" to r, "to" to "helper", "n" to n.toString()))
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
                    if (pc == null) break
                }
            }
        }
    }

    private fun sinhoBatda(m: JSONObject) {
        val p = pc ?: return
        when (m.optString("t", "")) {
            "offer" -> {
                val sdp = m.optString("sdp", "")
                if (sdp.isEmpty()) return
                p.setRemoteDescription(object : SdpPyeon() {
                    override fun onSetSuccess() { main.post { if (pc === p) dapBonaegi(p) } }
                }, SessionDescription(SessionDescription.Type.OFFER, sdp))
            }
            "ice" -> {
                val c = m.optJSONObject("c") ?: return
                val cand = c.optString("candidate", "")
                if (cand.isEmpty()) return
                val idx = (Jeomjido.su(c, "sdpMLineIndex") ?: 0.0).toInt()
                val mid = if (c.isNull("sdpMid")) "" else c.optString("sdpMid", "")
                p.addIceCandidate(IceCandidate(mid, idx, cand))
            }
            "bye" -> {
                geulBakkum("길손님이 통화를 마쳤습니다.")
                kkeutnaegi()
            }
        }
    }

    /** 답(answer) 만들어 보내기 */
    private fun dapBonaegi(p: PeerConnection) {
        p.createAnswer(object : SdpPyeon() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp == null) return
                main.post {
                    if (pc !== p) return@post
                    p.setLocalDescription(object : SdpPyeon() {
                        override fun onSetSuccess() {
                            main.post { if (pc === p) sigPut(JSONObject().put("t", "answer").put("sdp", sdp.description)) }
                        }
                    }, sdp)
                }
            }
        }, MediaConstraints())
    }

    // MARK: 소리 자리 — 통화 모드, 이어폰이 없으면 스피커

    private val am: AudioManager? get() = ctx?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private fun sorijariKyeogi() {
        val a = am ?: return
        try { a.mode = AudioManager.MODE_IN_COMMUNICATION; sorijariBakkum = true } catch (e: Exception) {}
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

    // MARK: 통화 지킴이 — WebRTC 알림은 다른 줄에서 오므로 화면 줄로 넘겨서

    private class Jikkim : PeerConnection.Observer {
        override fun onSignalingChange(s: PeerConnection.SignalingState?) {}
        override fun onIceConnectionReceivingChange(b: Boolean) {}
        override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidatesRemoved(c: Array<out IceCandidate>?) {}
        override fun onAddStream(s: MediaStream?) {
            val v = s?.videoTracks?.firstOrNull() ?: return
            JabongTonghwa.main.post { JabongTonghwa.yeongsangBatda(v) }
        }
        override fun onRemoveStream(s: MediaStream?) {}
        override fun onDataChannel(d: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(r: RtpReceiver?, streams: Array<out MediaStream>?) {
            val v = r?.track() as? VideoTrack ?: return
            JabongTonghwa.main.post { JabongTonghwa.yeongsangBatda(v) }
        }

        override fun onIceCandidate(c: IceCandidate?) {
            if (c == null) return
            val o = JSONObject().put("candidate", c.sdp).put("sdpMid", c.sdpMid ?: "0").put("sdpMLineIndex", c.sdpMLineIndex)
            JabongTonghwa.main.post { if (JabongTonghwa.pc != null) JabongTonghwa.sigPut(JSONObject().put("t", "ice").put("c", o)) }
        }

        override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {
            JabongTonghwa.main.post { JabongTonghwa.iceSangtae(s) }
        }
    }

    private fun iceSangtae(s: PeerConnection.IceConnectionState?) {
        if (pc == null) return
        when (s) {
            PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> {
                if (!iEojim) {
                    iEojim = true
                    geulBakkum("길손님과 이어졌습니다.")
                    seupikeo()
                    // 통화 중에는 길눈 말소리를 내지 않으므로 톡백으로 한 번만
                    Sori.tokbaek?.invoke("길손님과 이어졌습니다. 말씀하십시오.")
                }
            }
            PeerConnection.IceConnectionState.DISCONNECTED -> geulBakkum("연결이 잠시 끊겼습니다. 다시 잇는 중입니다.")
            PeerConnection.IceConnectionState.FAILED -> {
                geulBakkum("연결하지 못했습니다.")
                kkeutnaegi()
            }
            else -> {}
        }
    }

    /** 제안·답 알림의 빈 틀 — 필요한 것만 덮어씀 */
    private open class SdpPyeon : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(e: String?) { Girok.namgi("jabong_tonghwa_oryu", mapOf("dan" to "sdp_create", "e" to (e ?: ""))) }
        override fun onSetFailure(e: String?) { Girok.namgi("jabong_tonghwa_oryu", mapOf("dan" to "sdp_set", "e" to (e ?: ""))) }
    }
}
