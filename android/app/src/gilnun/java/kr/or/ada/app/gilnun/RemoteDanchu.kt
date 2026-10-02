// 안드로이드 길눈 — 이어폰·리모컨 단추(2.7.0, 묶음 b2 점지도 마저, 대표님 지시)
// 아이폰 RemoteDanchu.swift(2.6.0, 이사장님 결정 1번)를 안드로이드 미디어 세션으로 옮겼습니다 — 안내 중에만 길눈이 이어폰 단추를 받음, 설정에서 끌 수 있음.
//   재생(멈춤) 단추: 음향신호기 앞이면 신호 안내, 아니면 마지막 안내 다시 듣기(길게 0.6초 넘게 누르면 말로 하기 — 화면이 꺼져 있어도)
//   다음 단추: 점지도를 걷는 중이면 다음에 무엇이 있는지, 되짚어 나가는 중이면 지금 할 일, 아니면 지금 내 자리
//   이전 단추: 앞 안내(거듭 누르면 한 말씩 더 앞으로, 새 안내가 나오면 처음부터)
// 안드로이드는 "가장 최근에 소리를 낸 앱"의 미디어 세션에 단추를 넘기므로, 안내 중에는 길눈이 소리 없는 소리를 틀어 그 앱이 됩니다.
// 안내(점지도 따라 걷기·되짚어 나가기·말로 그린 길)가 끝나면 세션을 내려놓아 음악 앱이 다시 단추를 받습니다. 긴급통화 중에는 받지 않음.
// 리모컨 단추 익히기(블루투스 페이지 넘김 리모컨 — 글쇠를 보내는 것)는 JeomRimo — 점지도 따라 걷기 화면이 켜져 있을 때 쓰임
package kr.or.ada.app.gilnun

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

object RemoteDanchu {
    private var ctx: Context? = null
    private val main = Handler(Looper.getMainLooper())
    private var sesyeon: MediaSession? = null
    private var sori: AudioTrack? = null
    var kyeojim = false
        private set
    private var dolgo = false
    private var downT = 0L
    private var gilge = false
    // 앞 안내 — 길눈이 한 말을 1초마다 살펴 모아 둠(최근 열 개)
    private val jinan = ArrayList<String>()
    private var apIdx = -1
    private var naegaHan: String? = null
    private const val JEMOK = "길눈 안내 — 재생은 다시 듣기, 다음은 다음 갈림길, 이전은 앞 안내"

    private val sigye = object : Runnable {
        override fun run() {
            if (!dolgo) return
            matchugi()
            if (kyeojim) malSalpigi()
            main.postDelayed(this, 1000)
        }
    }

    /** 앱이 켜질 때 — 1초마다 형편을 보아 켜고 끔 */
    fun sijak(c: Context) {
        ctx = c.applicationContext
        JeomSeol.sijak(c)
        if (dolgo) return
        dolgo = true
        main.postDelayed(sigye, 1000)
    }

    /** 안내 중인가 — 점지도 따라 걷기(도착 전), 되짚어 나가기, 말로 그린 길 */
    private val annaeJung: Boolean
        get() = JeomEngine.georeoJung || JeomEngine.bulleoneun || DoeEngine.sangtae == DoeEngine.Sangtae.ANNAE || MalgilEngine.geotneun

    /** 지금 형편에 맞춰 켜거나 끔 */
    fun matchugi() {
        val mok = annaeJung && JeomSeol.ieoponDanchu && GinGeup.sangtae == GinGeupSangtae.EOPSEUM
        if (mok == kyeojim) {
            if (kyeojim) jikigi()
            return
        }
        if (mok) kyeogi() else kkeugi()
    }

    private fun kyeogi() {
        val c = ctx ?: return
        try {
            val s = sesyeon ?: MediaSession(c, "gilnun_annae").also { sesyeon = it }
            s.setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(i: Intent): Boolean {
                    @Suppress("DEPRECATION")
                    val e: KeyEvent = i.getParcelableExtra(Intent.EXTRA_KEY_EVENT) ?: return false
                    return danchuBatda(e)
                }
                override fun onPlay() { nulleum("play") }
                override fun onPause() { nulleum("play") }
                override fun onStop() { nulleum("play") }
                override fun onSkipToNext() { nulleum("next") }
                override fun onSkipToPrevious() { nulleum("prev") }
                override fun onFastForward() { nulleum("next") }
                override fun onRewind() { nulleum("prev") }
            }, main)
            s.setPlaybackState(PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_STOP or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
                .build())
            s.setMetadata(MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, JEMOK)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "길눈 — 한국시각장애인현장영상해설협회")
                .build())
            s.isActive = true
            soriTeulgi()
            kyeojim = true
            jinan.clear(); apIdx = -1
            Girok.namgi("ieopon_danchu", mapOf("on" to true))
        } catch (e: Exception) {
            Girok.namgi("ieopon_danchu_oryu", mapOf("e" to (e.message ?: "")))
        }
    }

    private fun kkeugi() {
        kyeojim = false
        try {
            sesyeon?.let {
                it.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_STOPPED, 0L, 0f).build())
                it.isActive = false
                it.release()
            }
        } catch (e: Exception) {}
        sesyeon = null
        soriKkeugi()
        Girok.namgi("ieopon_danchu", mapOf("on" to false))
    }

    /** 소리 없는 소리(0.5초를 되풀이) — 미디어 단추가 길눈으로 오게. 음악 앱의 소리 초점은 빼앗지 않음 */
    private fun soriTeulgi() {
        if (sori != null) return
        try {
            val sr = 8000
            val n = sr / 2
            val t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sr)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(n * 2)
                .build()
            t.write(ShortArray(n), 0, n)
            t.setLoopPoints(0, n, -1)
            t.setVolume(0.01f)
            t.play()
            sori = t
        } catch (e: Exception) {
            Girok.namgi("ieopon_sori_oryu", mapOf("e" to (e.message ?: "")))
        }
    }

    private fun soriKkeugi() {
        try { sori?.stop() } catch (e: Exception) {}
        try { sori?.release() } catch (e: Exception) {}
        sori = null
    }

    /** 받아쓰기나 통화로 소리가 멎었으면 다시 틂 */
    private fun jikigi() {
        if (Sori.deutneunJung) return
        val t = sori
        if (t == null || t.playState != AudioTrack.PLAYSTATE_PLAYING) { soriKkeugi(); soriTeulgi() }
        sesyeon?.let { if (!it.isActive) it.isActive = true }
    }

    /** 미디어 단추 — 재생 단추는 뗄 때 길이를 보고 정함(길게면 말로 하기) */
    private fun danchuBatda(e: KeyEvent): Boolean {
        val k = e.keyCode
        val jaesaeng = k == KeyEvent.KEYCODE_HEADSETHOOK || k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
            k == KeyEvent.KEYCODE_MEDIA_PLAY || k == KeyEvent.KEYCODE_MEDIA_PAUSE || k == KeyEvent.KEYCODE_MEDIA_STOP
        val daeum = k == KeyEvent.KEYCODE_MEDIA_NEXT || k == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
        val ijeon = k == KeyEvent.KEYCODE_MEDIA_PREVIOUS || k == KeyEvent.KEYCODE_MEDIA_REWIND
        if (!jaesaeng && !daeum && !ijeon) return false
        if (e.action == KeyEvent.ACTION_DOWN) {
            if (e.repeatCount == 0) { downT = e.downTime; gilge = false }
            // 길게 누름(앞뒤로 감기 포함)은 누르기 시작할 때 한 번만(아이폰 2.12.0)
            if (jaesaeng && !gilge && e.eventTime - e.downTime >= 600) {
                gilge = true
                Girok.namgi("malhagi_iyeopon", mapOf("hwamyeon" to false))
                MalHagi.dudeurim()
            }
            return true
        }
        if (e.action != KeyEvent.ACTION_UP || e.isCanceled) return true
        if (jaesaeng) {
            if (gilge) { gilge = false; return true }
            if (e.eventTime - e.downTime >= 600) {
                Girok.namgi("malhagi_iyeopon", mapOf("hwamyeon" to false))
                MalHagi.dudeurim()
                return true
            }
            nulleum("play")
        } else {
            nulleum(if (daeum) "next" else "prev")
        }
        return true
    }

    private fun nulleum(ireum: String) {
        Girok.namgi("ieopon", mapOf("d" to ireum))
        when (ireum) {
            "play" -> if (SinhogiEngine.apeIssna()) SinhogiEngine.ulligi(2) else Sori.dasiDeutgi()
            "next" -> when {
                JeomEngine.georeoJung -> Sori.mal(JeomEngine.daeumMuotMal(), MalGeup.ANNAE)
                DoeEngine.sangtae == DoeEngine.Sangtae.ANNAE -> DoeEngine.jigeumMal()
                MalgilEngine.geotneun -> MalgilEngine.dasiDeutgi()
                else -> JeomPan.jariMal()
            }
            else -> apDeutgi()
        }
    }

    /** 길눈이 새로 한 말을 모아 둠 — 앞 안내를 들려 드리려고 */
    private fun malSalpigi() {
        val m = Sori.majimak
        if (m.isEmpty()) return
        if (m == naegaHan) return            // 앞 안내로 다시 들려 드린 말은 새 말로 치지 않음
        if (jinan.lastOrNull() == m) return
        naegaHan = null
        jinan.add(m)
        while (jinan.size > 10) jinan.removeAt(0)
        apIdx = -1                           // 새 안내가 나오면 처음부터 다시 셈
    }

    /** 앞 안내 — 거듭 누르면 한 말씩 더 앞으로 */
    private fun apDeutgi() {
        malSalpigi()
        if (jinan.size < 2 && apIdx < 0) { Sori.mal(if (jinan.isEmpty()) "들려 드릴 앞 안내가 없습니다." else "더 앞의 안내가 없습니다."); return }
        val i = if (apIdx < 0) jinan.size - 2 else apIdx - 1
        if (i < 0) { Sori.mal("더 앞의 안내가 없습니다."); return }
        apIdx = i
        val m = jinan[i]
        naegaHan = m
        Sori.mal(m)
    }
}

/** 리모컨 단추 — 블루투스 리모컨이 보내는 글쇠를 받음(화면이 켜져 있을 때만). 아이폰 RimoDanchu·RimoView 와 같은 세 자리 */
object JeomRimo {
    val IREUM = mapOf("1" to "지금 어디입니까", "2" to "다음에 무엇이 있습니까", "3" to "다시 말해 주기")
    /** 익히는 자리("1"~"3") — 익히는 중이 아니면 null */
    var baeuneun: String? = null
    /** 익힌 단추 눌러 보기 중 */
    var siheom = false
    /** 익히기·눌러 보기 결과를 화면에 알림(리모컨 화면이 채움) */
    var allim: ((String) -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())
    private val gidarim = Runnable {
        if (baeuneun != null) {
            baeuneun = null
            allim?.invoke("아무 단추도 들어오지 않았습니다. 이 리모컨은 소리 크기 단추만 보내는 것일 수 있습니다.")
        }
    }

    fun baeugi(jari: String) {
        siheom = false
        baeuneun = jari
        main.removeCallbacks(gidarim)
        main.postDelayed(gidarim, 10000)
    }

    fun geuman() {
        baeuneun = null
        siheom = false
        main.removeCallbacks(gidarim)
    }

    /** 글쇠 이름 — 아이폰 RimoDanchu.ireum 과 같은 이름. 받지 않을 글쇠는 null */
    fun ireum(keyCode: Int, e: KeyEvent): String? = when (keyCode) {
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_STOP, KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT, KeyEvent.KEYCODE_CTRL_LEFT,
        KeyEvent.KEYCODE_CTRL_RIGHT, KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT, KeyEvent.KEYCODE_UNKNOWN -> null
        KeyEvent.KEYCODE_DPAD_UP -> "위 화살표"
        KeyEvent.KEYCODE_DPAD_DOWN -> "아래 화살표"
        KeyEvent.KEYCODE_DPAD_LEFT -> "왼쪽 화살표"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "오른쪽 화살표"
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> "엔터"
        KeyEvent.KEYCODE_SPACE -> "스페이스"
        KeyEvent.KEYCODE_PAGE_UP -> "페이지 업"
        KeyEvent.KEYCODE_PAGE_DOWN -> "페이지 다운"
        KeyEvent.KEYCODE_ESCAPE -> "이에스시"
        else -> {
            val u = e.unicodeChar
            if (u > 32) String(Character.toChars(u)) else "단추 $keyCode"
        }
    }

    /** GilnunActivity.onKeyDown 맨 앞에서 부름 — 받아서 썼으면 참(그 글쇠는 다른 데로 가지 않음) */
    fun keyBatgi(t: GilnunActivity, keyCode: Int, e: KeyEvent): Boolean {
        if (e.repeatCount > 0) return baeuneun != null || siheom
        val nm = ireum(keyCode, e) ?: return false
        val jari0 = baeuneun
        if (jari0 != null && t.wiHwamyeon is RimoHwamyeon) {
            main.removeCallbacks(gidarim)
            val m = HashMap(JeomSeol.rimo)
            val ttan = m.entries.filter { it.value == nm && it.key != jari0 }.map { it.key }
            for (a in ttan) m.remove(a)
            m[jari0] = nm
            JeomSeol.rimo = m
            baeuneun = null
            allim?.invoke("${jari0}번 자리에 $nm 단추를 담았습니다. ${IREUM[jari0] ?: ""}에 쓰입니다.")
            return true
        }
        if (siheom && t.wiHwamyeon is RimoHwamyeon) {
            val jari = JeomSeol.rimo.entries.firstOrNull { it.value == nm }?.key
            Sori.mal(if (jari != null) "${jari}번, ${IREUM[jari] ?: ""} 입니다." else "$nm 단추는 익히지 않은 단추입니다.")
            return true
        }
        // 익힌 단추는 점지도 따라 걷기 화면이 켜져 있을 때 쓰임
        if (t.wiHwamyeon !is JeomGeotgiHwamyeon || JeomEngine.gil == null) return false
        val jari = JeomSeol.rimo.entries.firstOrNull { it.value == nm }?.key ?: return false
        Girok.namgi("rimo", mapOf("jari" to jari))
        when (jari) {
            "1" -> JeomEngine.jigeumEodiDeutgi()
            "2" -> JeomEngine.daeumMuot()
            else -> Sori.dasiDeutgi()
        }
        return true
    }
}
