// AI점자도서관 안드로이드 — 독서기 (0.2.0, 빌드 261002-L1)
// 글자책: 문단마다 서버 목소리(수퍼톤)를 받아 틈 없이 이어 틂(ExoPlayer 줄 세우기) — 앞 네 문단을 미리 받아 둠
// 소리책·동영상: 나스 주소를 그대로 틂. 듣던 자리는 내 서재에 저절로 남음
package kr.or.ada.app.lib

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.io.File
import java.util.Locale
import java.security.MessageDigest
import java.util.concurrent.Executors

object Dokseo {
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(3)
    lateinit var ctx: Context
    var player: ExoPlayer? = null
        private set
    var i = -1; var title = ""; var kind = "geul"; var modu = 0; var pos = 0
    var playing = false; var waiting = false
    var bakkwim: (() -> Unit)? = null      // 화면 다시 그리기
    var allim: ((String) -> Unit)? = null  // 톡백 알림
    private val paras = HashMap<Int, String>()
    private val gajineun = HashSet<Int>()       // 받고 있는 문단
    private val pageLoading = HashSet<Int>()
    private var token = 0
    private var queued = -1                     // 줄에 넣은 마지막 문단

    private fun cacheDir() = File(ctx.cacheDir, "sori").apply { mkdirs() }
    fun hash(t: String, v: Int): String = MessageDigest.getInstance("SHA-1").digest("st3|$v|$t".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val ds = DefaultHttpDataSource.Factory().setUserAgent(Api.UA)
        val p = ExoPlayer.Builder(ctx).setMediaSourceFactory(DefaultMediaSourceFactory(ds)).build()
        p.setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
        p.addListener(object : Player.Listener {
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                if (kind != "geul") return
                val o = item?.mediaId?.toIntOrNull() ?: return
                pos = o; Store.remember(i, title, kind, o.toDouble(), modu)
                apseo(); bakkwim?.invoke()
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (kind == "geul" && state == Player.STATE_ENDED && playing) {
                    if (pos >= modu - 1 && modu > 0) { playing = false; Store.remember(i, title, kind, pos.toDouble(), modu, done = true); allim?.invoke("책을 끝까지 읽었습니다."); bakkwim?.invoke() }
                    else { waiting = true; bakkwim?.invoke() }    // 다음 문단이 아직 안 구워짐 — 들어오면 이어 틂
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (kind != "geul") { playing = isPlaying; bakkwim?.invoke() } }
        })
        player = p
        return p
    }

    fun open(c: Context, bi: Int, bt: String, bk: String) {
        ctx = c.applicationContext
        if (i == bi) return
        stop()
        i = bi; title = bt; kind = bk; paras.clear(); gajineun.clear(); pageLoading.clear(); modu = 0
        val saved = Store.rec(bi)
        val p = ensurePlayer()
        p.setPlaybackParameters(PlaybackParameters(Store.rate))
        if (kind == "geul") {
            pos = saved?.pos?.toInt() ?: 0
            loadPage(pos) { Store.remember(i, title, kind, pos.toDouble(), modu); bakkwim?.invoke() }
        } else {
            p.setMediaItem(MediaItem.Builder().setUri(Naeryeo.localMedia(c, bi)?.let { android.net.Uri.fromFile(it).toString() } ?: Api.mediaUrl(bi)).setMediaId("m").build())   // 0.3.0 — 내려받은 소리책은 폰 안 파일로
            p.prepare()
            saved?.let { p.seekTo((it.pos * 1000).toLong()) }
            Store.remember(i, title, kind, saved?.pos ?: 0.0, 0)
        }
    }

    private fun loadPage(o: Int, then: (() -> Unit)? = null) {
        val pg = (o / 60) * 60
        if (paras.containsKey(o)) { then?.let { main.post(it) }; return }
        ctx?.let { cx -> Naeryeo.paras(cx, i)?.let { all -> modu = all.size; all.forEachIndexed { k, s -> paras[k] = s }; then?.let { main.post(it) }; return } }   // 0.3.0 — 내려받은 글이 있으면 인터넷 없이
        if (!pageLoading.add(pg)) return
        val book = i
        pool.execute {
            val r = runCatching { Api.gul(book, pg) }.getOrNull()
            main.post {
                pageLoading.remove(pg)
                if (book != i || r == null) { if (r == null) allim?.invoke("글자를 가져오지 못했습니다. 인터넷을 확인해 주십시오."); return@post }
                modu = r.first
                r.second.forEachIndexed { k, s -> paras[pg + k] = s }
                then?.invoke()
            }
        }
    }
    fun text(o: Int = pos) = paras[o] ?: ""

    /** 문단 o 의 소리를 받아 파일로 — 다 되면 main 에서 done(파일 또는 null) */
    private fun soriFile(o: Int, done: (File?) -> Unit) {
        val txt = paras[o]
        if (txt == null) { loadPage(o) { soriFile(o, done) }; return }
        val v = Store.voice
        val cut = txt.take(600)
        val h = hash(cut, v)
        val f = File(cacheDir(), "$h.mp3")
        if (f.exists() && f.length() > 100) { done(f); return }
        if (!gajineun.add(o)) return
        val book = i
        pool.execute {
            var out: File? = null
            runCatching {
                val (_, hh0) = Api.yocheong(cut, v)
                val hh = hh0.ifEmpty { h }
                for (n in 0 until 90) {
                    if (book != i) break
                    val d = Api.sori(hh)
                    if (d != null) { f.writeBytes(d); out = f; break }
                    Thread.sleep(if (n < 10) 700 else 1000)
                }
            }
            main.post { gajineun.remove(o); if (book == i) done(out) }
        }
    }

    fun toggle() { if (playing) pause() else play() }

    fun play(at: Int = pos) {
        val p = ensurePlayer()
        p.setPlaybackParameters(PlaybackParameters(Store.rate))
        if (kind != "geul") { p.play(); playing = true; bakkwim?.invoke(); return }
        token += 1; val tk = token
        pos = at; playing = true; waiting = true; queued = at - 1
        p.stop(); p.clearMediaItems()
        bakkwim?.invoke()
        ctx?.let { cx -> if (!Naeryeo.online(cx)) { speakPhone(at, tk); return } }   // 0.3.0 — 인터넷이 없으면 바로 폰 목소리
        soriFile(at) { f ->
            if (tk != token) return@soriFile
            if (f == null) { speakPhone(at, tk); return@soriFile }   // 0.3.0 — 서버 목소리를 못 받으면 폰 목소리로
            p.setMediaItem(MediaItem.Builder().setUri(android.net.Uri.fromFile(f)).setMediaId("$at").build())
            queued = at; waiting = false
            p.prepare(); p.play()
            apseo(); bakkwim?.invoke()
        }
    }
    /** 앞 네 문단을 미리 받아 차례대로 줄에 붙임 — 문단 사이 틈이 없음 */
    private fun apseo() {
        if (kind != "geul" || !playing) return
        val tk = token
        for (k in 1..4) {
            val o = pos + k
            if (modu in 1..o) break
            if (o <= queued) continue
            soriFile(o) { f -> if (tk == token && f != null) butigi(o, f) }
        }
    }
    private val gidari = HashMap<Int, File>()
    private fun butigi(o: Int, f: File) {
        gidari[o] = f
        val p = player ?: return
        while (gidari.containsKey(queued + 1)) {
            val n = queued + 1
            p.addMediaItem(MediaItem.Builder().setUri(android.net.Uri.fromFile(gidari.remove(n)!!)).setMediaId("$n").build())
            queued = n
            if (waiting) { waiting = false; if (p.playbackState == Player.STATE_ENDED) { p.seekToNextMediaItem(); p.play() } }
        }
        bakkwim?.invoke()
    }

    fun pause() { tts?.stop();
        token += 1
        player?.pause(); playing = false; waiting = false
        if (kind != "geul") player?.let { Store.remember(i, title, kind, it.currentPosition / 1000.0, 0) }
        bakkwim?.invoke()
    }
    fun stop() { tts?.stop();
        token += 1
        player?.stop(); player?.clearMediaItems()
        playing = false; waiting = false; gidari.clear()
    }
    fun next() { if (kind == "geul") { if (pos + 1 < modu) play(pos + 1) } else player?.let { it.seekTo(it.currentPosition + 30000) } }
    fun prev() { if (kind == "geul") { if (pos > 0) play(pos - 1) } else player?.let { it.seekTo((it.currentPosition - 30000).coerceAtLeast(0)) } }
    fun gaPo(o: Int) { if (kind == "geul") { pos = o.coerceIn(0, (modu - 1).coerceAtLeast(0)); if (playing) play(pos) else loadPage(pos) { bakkwim?.invoke() } ; Store.remember(i, title, kind, pos.toDouble(), modu) } else player?.seekTo((o * 1000).toLong()) }
    fun setRate() { player?.setPlaybackParameters(PlaybackParameters(Store.rate)) }
    fun markHere() {
        val ps = if (kind == "geul") pos.toDouble() else (player?.currentPosition ?: 0) / 1000.0
        Store.addMark(i, title, kind, ps)
    }

    // 0.3.0 — 인터넷이 없거나 서버 목소리를 못 받으면 폰 목소리(TextToSpeech)로 읽음
    private var tts: android.speech.tts.TextToSpeech? = null
    private var ttsOk = false
    private var saidPhone = false
    private fun speakPhone(at: Int, tk: Int) {
        val t = paras[at]
        if (t == null) { loadPage(at) { if (tk == token) speakPhone(at, tk) }; return }
        val cx = ctx ?: return
        val go: () -> Unit = {
            val e = tts
            if (e == null || !ttsOk) { playing = false; waiting = false; allim?.invoke("폰 목소리를 쓸 수 없습니다. 설정의 텍스트 음성 변환을 확인해 주십시오."); bakkwim?.invoke() }
            else {
                if (!Naeryeo.online(cx) && !saidPhone) { saidPhone = true; allim?.invoke("인터넷이 없어 폰 목소리로 읽습니다.") }
                if (Naeryeo.online(cx)) saidPhone = false
                e.setSpeechRate(Store.rate)
                waiting = false; pos = at
                e.speak(if (t.isEmpty()) " " else t, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "p$tk-$at")
                Store.remember(i, title, kind, at.toDouble(), modu)
                bakkwim?.invoke()
            }
        }
        if (tts == null) {
            tts = android.speech.tts.TextToSpeech(cx) { st ->
                ttsOk = st == android.speech.tts.TextToSpeech.SUCCESS
                if (ttsOk) {
                    tts?.language = Locale.KOREAN
                    tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(id: String?) {}
                        @Deprecated("") override fun onError(id: String?) {}
                        override fun onDone(id: String?) {
                            main.post {
                                val tkn = id?.removePrefix("p")?.substringBefore("-")?.toIntOrNull() ?: -1
                                if (tkn != token || !playing) return@post
                                if (pos + 1 < modu) play(pos + 1)
                                else { playing = false; allim?.invoke("책을 끝까지 읽었습니다."); bakkwim?.invoke() }
                            }
                        }
                    })
                }
                main.post { go() }
            }
        } else go()
    }
}
