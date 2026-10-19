// AI점자도서관 안드로이드 — 독서기 (0.4.4판, 빌드 261008-L8)
// 0.8.2 (261019-L21, 이사장님 승인 2026-10-18 「1」) 이용 장부(책 열기·1분마다 들은 시간·끝까지 들음), 8초 넘게 멈추면 스스로 다시 이어 틀기, 멈춤 기록은 한 번에 한 줄
// 0.6.3 (261014-L15, 이사장님 승인) 이용 기록(책 열기·소리를 5초 넘게 기다림·소리를 못 만듦·글자를 못 가져옴, 책 이름·내용은 남기지 않음).
//       미리 받던 문단을 곧바로 읽으려 하면 기다림이 풀리지 않던 것 바로잡음 — 같은 문단을 받는 중이면 기다리는 쪽도 함께 알려 줌(soriGidari)
// 0.4.4 (261008-L8, 이사장님 승인 「1」) 화면을 꺼도 계속 읽기(DokseoService 미디어 세션), 이어폰 단추·잠금 화면, 전화·이어폰 뽑기 때 멈춤 표시 맞춤,
//       책갈피로 다른 책을 열 때 읽던 자리가 지워지던 것 바로잡음(open 의 at), 처음부터(cheoeum), 잠금 화면에 책 이름
// 0.2.0 (261002-L1) 처음 판
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
    private val apPool = Executors.newSingleThreadExecutor()   // 0.7.1 (261014-L17) 미리 받기는 따로 한 일꾼 — 지금 들을 문단이 미리 받기 뒤에 줄 서지 않게(이사장님 지적: 점점 무거워지고 멈춤)
    private val pan4Sido = HashSet<Int>()   // 0.5.2 이번에 자리 옮기기를 해 본 책
    lateinit var ctx: Context
    var player: ExoPlayer? = null
        private set
    var i = -1; var title = ""; var kind = "geul"; var modu = 0; var pos = 0
    var playing = false; var waiting = false
    var bakkwim: (() -> Unit)? = null      // 화면 다시 그리기
    var allim: ((String) -> Unit)? = null  // 톡백 알림
    @Volatile var jamgeum: String? = null  // 0.8.3 일반 회원에게 잠긴 책이면 알릴 말(장애 확인 뒤 들음)
    private val paras = HashMap<Int, String>()
    private val wons = HashMap<Int, String>()   // 0.6.1 (261012) 한자음 읽기 — 문단 원문
    var hjgal = false                           // 0.6.1 이 책이 한자음 읽기 대상인가
    private val gajineun = HashSet<Int>()       // 받고 있는 문단
    private val soriGidari = HashMap<Int, ArrayList<(File?) -> Unit>>()   // 0.6.3 받는 중인 문단을 함께 기다리는 쪽
    private val pageLoading = HashSet<Int>()
    private var token = 0
    private var queued = -1                     // 줄에 넣은 마지막 문단
    // 0.8.0 (261018-L19) 이어 듣기·이어 보기(엔터테인먼트) — 곡·회가 끝나면 듣는 방식대로 다음 것을 저절로(곡 하나 끝나면 멈추던 것, 이사장님 지적·승인 「1」)
    var ieoBakkwim: ((Int, String, String) -> Unit)? = null   // 화면을 새 곡·회로 바꾸기
    var aelbeom = ""
    private var miri: Daeum? = null
    private var miriUi = -1
    private var miriBang = ""
    private var ieoJung = false
    // ── 0.8.2 이용 장부와 멈춤 지킴이 ──
    private var deutNu = 0; private var deutI = -1; private var deutK = ""
    private var meomSijak = 0L; private var meomGirokMaji = 0L; private var meomDasi = 0
    private var jikimiDoem = false
    private fun jangbuK() = if (Api.isEnt(i)) "ent" else kind
    fun deutBonaegi() { if (deutNu >= 5 && deutI >= 0) LibIyong.namgi("deut", deutI, deutK, deutNu); deutNu = 0 }
    private val jikimi = object : Runnable { override fun run() { jikimiTik(); main.postDelayed(this, 3000) } }
    private fun jikimiSijak() { if (!jikimiDoem) { jikimiDoem = true; main.postDelayed(jikimi, 3000) } }
    private fun jikimiTik() {
        val p = player
        val deunneun = if (kind == "geul") playing else (p?.playWhenReady == true)
        if (!deunneun || i < 0) { meomSijak = 0; return }
        if (kind == "geul" && waiting) return   // 글자책은 소리를 굽는 동안 세지 않음
        if (deutI != i) { deutBonaegi(); deutI = i; deutK = jangbuK() }
        if (kind != "geul" && p != null) {
            if (p.playbackState == Player.STATE_BUFFERING) {
                if (meomSijak == 0L) meomSijak = System.currentTimeMillis()
                if (System.currentTimeMillis() - meomSijak >= 8000) {
                    meomSijak = 0; meomDasi += 1
                    LibGirok.namgi("lib_sori_dasi", mapOf("beon" to meomDasi, "ent" to Api.isEnt(i), "cho" to (p.currentPosition / 1000).toInt()))
                    val at = p.currentPosition; p.prepare(); p.seekTo(at); p.play()
                }
                return
            }
            meomSijak = 0
            if (!p.isPlaying) return
        }
        deutNu += 3
        if (deutNu >= 60) deutBonaegi()
    }
    private fun ieoBang(): String { val s = Store.ieo; return if (s == "han" || s == "ban") "ap" else s }
    /** 다음 것을 미리 알아 두어 곡 사이 기다림을 줄임 */
    fun ieoMiri() {
        val ui = i; val b = Store.ieo
        if (!Api.isEnt(ui) || (b != "ap" && b != "gal")) return
        apPool.execute { val d = runCatching { Api.daeum(ui, b, false) }.getOrNull(); main.post { if (d != null && d.ok && i == ui) { miri = d; miriUi = ui; miriBang = b } } }
    }
    /** b = 1 다음, -1 앞. 듣는 방식대로 다음(앞) 곡·회를 열어 바로 틂 */
    fun ieoGa(b: Int) {
        if (!Api.isEnt(i) || ieoJung) return
        ieoJung = true
        val ui = i; val bang = ieoBang()
        val pre = if (b > 0 && miriUi == ui && miriBang == bang) miri else null
        pool.execute {
            val d = pre ?: runCatching { Api.daeum(ui, bang, b < 0) }.getOrNull()
            main.post {
                ieoJung = false
                if (i != ui) return@post
                val ni = d?.i
                if (d == null || !d.ok || ni == null) {
                    if (b > 0 && player?.isPlaying != true) { playing = false; bakkwim?.invoke() }
                    allim?.invoke(d?.msg ?: "다음 것을 받지 못했습니다. 인터넷을 확인해 주십시오."); return@post
                }
                LibGirok.namgi("lib_ieo", mapOf("bang" to bang, "dwi" to (b < 0)))
                val k = if (d.k == "yeongsang") "yeongsang" else "sori"
                aelbeom = d.aelbeom
                open(ctx, ni, d.t, k, 0.0, ieo = true)
                play()
                ieoBakkwim?.invoke(ni, d.t, k)
                if (Store.gogmal) {
                    val ap = if (d.saeAelbeom) (if (b > 0) "다음 앨범, " else "앞 앨범, ") + d.aelbeom + ", " else if (b > 0) "다음, " else "앞, "
                    main.postDelayed({ allim?.invoke(ap + d.t) }, 500)
                }
            }
        }
    }

    private fun cacheDir() = File(ctx.cacheDir, "sori").apply { mkdirs() }
    fun hash(t: String, v: Int): String = MessageDigest.getInstance("SHA-1").digest("st3|$v|$t".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private var sesPlayer: DokseoSessionPlayer? = null
    /** 0.4.4 미디어 세션(DokseoService)에 내어 줄 재생기 */
    internal fun sessionPlayer(c: Context): Player {
        if (!this::ctx.isInitialized) ctx = c.applicationContext
        ensurePlayer(false)
        return sesPlayer!!
    }
    /** 0.4.4 잠금 화면·알림 칸에 책 이름이 나오게 */
    private fun mi(uri: String, id: String): MediaItem = MediaItem.Builder().setUri(uri).setMediaId(id)
        .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title).setArtist("AI점자도서관").setAlbumTitle(aelbeom.ifEmpty { null }).build()).build()

    private fun ensurePlayer(service: Boolean = true): ExoPlayer {
        player?.let { if (service) DokseoService.kyeogi(ctx); return it }
        val ds = DefaultHttpDataSource.Factory().setUserAgent(Api.UA)
        val p = ExoPlayer.Builder(ctx).setMediaSourceFactory(DefaultMediaSourceFactory(ds))
            .setAudioAttributes(androidx.media3.common.AudioAttributes.Builder().setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)   // 0.4.4 전화가 오면 멈췄다가 끝나면 이어 읽음
            .setHandleAudioBecomingNoisy(true)   // 0.4.4 이어폰을 뽑으면 멈춤(소리가 스피커로 새지 않게)
            .build()
        p.setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
        p.addListener(object : Player.Listener {
            // 0.4.4 전화·이어폰 뽑기로 재생기가 스스로 멈추거나 다시 틀 때 「읽기」·「멈춤」 표시를 맞춤
            override fun onPlayWhenReadyChanged(pwr: Boolean, reason: Int) {
                if (kind != "geul" && !pwr && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) LibGirok.namgi("lib_sori_meomchum", mapOf("kka" to "kkeunkim", "ent" to Api.isEnt(i)))   // 0.7.1
                if (kind != "geul") return
                if (!pwr && (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS || reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)) { playing = false; waiting = false; bakkwim?.invoke() }
                else if (pwr && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) { playing = true; bakkwim?.invoke() }
            }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                if (kind != "geul") return
                val o = item?.mediaId?.toIntOrNull() ?: return
                pos = o; Store.remember(i, title, kind, o.toDouble(), modu)
                apseo(); bakkwim?.invoke()
            }
            override fun onPlaybackStateChanged(state: Int) {
                // 0.7.1 소리책·동영상·엔터테인먼트가 틀던 중 소리가 모자라 기다리면 이용 기록에(멈춤 까닭 살피기)
                if (kind != "geul" && state == Player.STATE_BUFFERING && player?.playWhenReady == true && System.currentTimeMillis() - meomGirokMaji > 10000 && run { meomGirokMaji = System.currentTimeMillis(); true }) LibGirok.namgi("lib_sori_meomchum", mapOf("kka" to "moja", "ent" to Api.isEnt(i), "cho" to ((player?.currentPosition ?: 0) / 1000).toInt()))
                if (kind != "geul" && state == Player.STATE_ENDED) {   // 0.8.0 이어 듣기
                    deutBonaegi(); LibIyong.namgi("kkeut", i, jangbuK())   // 0.8.2 이용 장부
                    Store.remember(i, title, kind, 0.0, ((player?.duration ?: 0) / 1000).toInt(), done = true)
                    if (Api.isEnt(i) && Store.ieo != "han") { if (Store.ieo == "ban") { player?.seekTo(0); player?.play() } else ieoGa(1) }
                }
                if (kind == "geul" && state == Player.STATE_ENDED && playing) {
                    if (pos >= modu - 1 && modu > 0) { playing = false; Store.remember(i, title, kind, pos.toDouble(), modu, done = true); deutBonaegi(); LibIyong.namgi("kkeut", i, "geul"); allim?.invoke("책을 끝까지 읽었습니다."); bakkwim?.invoke() }
                    else { waiting = true; bakkwim?.invoke() }    // 다음 문단이 아직 안 구워짐 — 들어오면 이어 틂
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (kind != "geul") { playing = isPlaying; bakkwim?.invoke() } }
        })
        player = p
        sesPlayer = DokseoSessionPlayer(p)
        if (service) DokseoService.kyeogi(ctx)
        return p
    }

    /** at — 책갈피처럼 정해진 자리에서 열 때(0.4.4: 열기 전에 자리를 옮기면 읽던 자리가 첫 문단으로 지워지던 것 바로잡음) */
    fun open(c: Context, bi: Int, bt: String, bk: String, at: Double? = null, ieo: Boolean = false) {
        ctx = c.applicationContext
        if (i == bi) { if (at != null) gaPo(at.toInt()); return }
        // 0.5.2 (261012) 글 판 4 — 처음 여는 글자책은 옛 판 자리·책갈피를 새 판 자리로 옮긴 뒤 엶(내려받은 책은 그대로)
        if (bk == "geul" && !Store.gulPan4Ok(bi) && pan4Sido.add(bi)) {
            val ol = Store.gulPan4Ol(bi)
            if (Naeryeo.isDown(c, bi) || ol.isEmpty()) Store.gulPan4Bakkugi(bi, emptyMap())
            else {
                pool.execute {
                    val j = Api.jari(bi, ol)
                    main.post {
                        val mp = HashMap<Int, Int>()
                        if (j != null) { if (j.size == ol.size) for (k in ol.indices) mp[ol[k]] = j[k]; Store.gulPan4Bakkugi(bi, mp) }
                        open(c, bi, bt, bk, at?.let { a -> (mp[a.toInt()] ?: a.toInt()).toDouble() })
                    }
                }
                return
            }
        }
        stop()
        if (!ieo) aelbeom = ""
        miri = null; miriUi = -1
        jamgeum = null; Api.jamMal.remove("$bi")   // 0.8.3
        i = bi; title = bt; kind = bk; paras.clear(); gajineun.clear(); soriGidari.clear(); pageLoading.clear(); modu = 0; wons.clear(); hjgal = false
        LibGirok.namgi("lib_chaek_yeolgi", mapOf("kind" to bk, "ent" to Api.isEnt(bi), "naeryeo" to (Naeryeo.isDown(c, bi))))   // 0.6.3 이용 기록
        LibIyong.namgi("yeol", bi, if (Api.isEnt(bi)) "ent" else bk); meomDasi = 0; meomSijak = 0; jikimiSijak()   // 0.8.2 이용 장부·멈춤 지킴이
        val saved = Store.rec(bi)
        val p = ensurePlayer()
        p.setPlaybackParameters(PlaybackParameters(Store.rate))
        // 0.7.1 (261014-L17) 드라마가 나오다 멈췄다 함(이사장님 0시 21분) — 「말소리」로 틀면 톡백이 말할 때 플레이어가 멈춤. 소리책·동영상·엔터테인먼트는 「영화 소리」로(글자책 낭독은 그대로)
        p.setAudioAttributes(androidx.media3.common.AudioAttributes.Builder().setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(if (kind == "geul") androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH else androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        if (kind == "geul") {
            pos = at?.toInt() ?: saved?.pos?.toInt() ?: 0
            loadPage(pos) { Store.remember(i, title, kind, pos.toDouble(), modu); bakkwim?.invoke() }
        } else {
            p.setMediaItem(mi(Naeryeo.localMedia(c, bi)?.let { android.net.Uri.fromFile(it).toString() } ?: Api.mediaUrl(bi), "m"))   // 0.3.0 — 내려받은 소리책은 폰 안 파일로
            p.prepare()
            val st = at ?: saved?.pos
            st?.let { p.seekTo((it * 1000).toLong()) }
            Store.remember(i, title, kind, st ?: 0.0, 0)
            if (Api.isEnt(bi)) ieoMiri()   // 0.8.0
            if (Hoewon.ilban(c) && Naeryeo.localMedia(c, bi) == null) pool.execute {   // 0.8.3 잠긴 소리책·영상은 틀지 않고 알림
                val m = Api.jamgeumBogi(bi)
                if (m != null) main.post { if (i == bi) { jamgeum = m; player?.stop(); player?.clearMediaItems(); playing = false; allim?.invoke(m); bakkwim?.invoke() } }
            }
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
                if (book != i || r == null) { if (r == null) { allim?.invoke("글자를 가져오지 못했습니다. 인터넷을 확인해 주십시오."); LibGirok.namgi("lib_geulja_silpae") }; return@post }   // 0.6.3 이용 기록
                Api.jamMal.remove("$book")?.let { m -> jamgeum = m; allim?.invoke(m) }   // 0.8.3 잠긴 글자책
                modu = r.first
                r.second.forEachIndexed { k, s -> paras[pg + k] = s }
                Api.hjWon.remove("$book:$pg")?.forEachIndexed { k, s -> wons[pg + k] = s }; Api.hjGal.remove("$book:$pg")?.let { hjgal = it }   // 0.6.1
                then?.invoke()
            }
        }
    }
    fun text(o: Int = pos) = paras[o] ?: ""
    fun won(o: Int = pos) = wons[o] ?: ""   // 0.6.1

    // 0.6.1 (261012) 한자음 읽기 바꾸기 — 음만 듣기 → 음과 원문 함께 보기 → 꺼짐 → 음만 듣기 (이사장님 승인 10시 53분 「1」)
    fun hanjaeumBakkugi(done: () -> Unit) {
        val sae = when (Store.hanjaeum) { 1 -> 2; 2 -> 0; else -> 1 }
        Store.hanjaeum = sae
        val dasi = playing
        if (dasi) pause()
        paras.clear(); wons.clear(); gajineun.clear(); pageLoading.clear()
        val o = pos
        loadPage(o) { done(); if (dasi) play(o) }
    }

    /** 문단 o 의 소리를 받아 파일로 — 다 되면 main 에서 done(파일 또는 null) */
    private fun soriFile(o: Int, apseo: Boolean = false, done: (File?) -> Unit) {
        val txt = paras[o]
        if (txt == null) { loadPage(o) { soriFile(o, apseo, done) }; return }
        val v = Store.voice
        val cut = txt.take(600)
        val h = hash(cut, v)
        val f = File(cacheDir(), "$h.mp3")
        if (f.exists() && f.length() > 100) { done(f); return }
        if (!gajineun.add(o)) { soriGidari.getOrPut(o) { ArrayList() }.add(done); return }   // 0.6.3 — 받는 중이면 다 받았을 때 함께 알려 줌
        val book = i
        (if (apseo) apPool else pool).execute {
            var out: File? = null
            runCatching {
                val (_, hh0) = Api.yocheong(cut, v)
                val hh = hh0.ifEmpty { h }
                // 0.7.1 묻기 줄이기 — 4분까지 기다리되 처음 여섯 번만 0.7초마다, 그 뒤로는 앞에 기다리는 문단 수만큼 느긋하게(1~3초). 듣는 자리에서 멀어진 미리 받기는 그만둠
                val kkeut = System.currentTimeMillis() + 240_000
                var n = 0
                while (System.currentTimeMillis() < kkeut) {
                    if (book != i) break
                    if (o != pos && (o < pos || o > pos + 4)) break
                    val d = Api.sori(hh)
                    if (d != null) { f.writeBytes(d); out = f; break }
                    Thread.sleep(if (n < 6) 700L else minOf(3000L, 1000L + Api.apSu * 250L))
                    n++
                }
            }
            main.post { gajineun.remove(o); val dul = soriGidari.remove(o); if (book == i) { done(out); dul?.forEach { it(out) } } }
        }
    }

    fun toggle() { jamgeum?.let { m -> allim?.invoke(m); bakkwim?.invoke(); return }; if (playing) pause() else play() }   // 0.8.3 잠긴 책은 틀지 않음

    fun play(at: Int = pos) {
        if (jamgeum != null) { playing = false; return }   // 0.8.3
        val p = ensurePlayer()
        p.setPlaybackParameters(PlaybackParameters(Store.rate))
        if (kind != "geul") { p.play(); playing = true; bakkwim?.invoke(); return }
        token += 1; val tk = token
        pos = at; playing = true; waiting = true; queued = at - 1
        p.stop(); p.clearMediaItems()
        bakkwim?.invoke()
        ctx?.let { cx -> if (!Naeryeo.online(cx)) { speakPhone(at, tk); return } }   // 0.3.0 — 인터넷이 없으면 바로 폰 목소리
        val gidarimSijak = System.currentTimeMillis()   // 0.6.3 이용 기록 — 소리를 기다린 초
        // 0.6.3 기다림 알리기 — 10초와 45초가 지나도 소리가 안 오면 한 번씩만 알림(이사장님 승인)
        for ((cho, mal) in listOf(10 to "소리를 만들고 있습니다. 잠시만 기다려 주십시오.", 45 to "여러 분이 함께 들으셔서 조금 더 걸립니다. 그대로 기다려 주십시오.")) {
            main.postDelayed({ if (tk == token && waiting && playing) { val ap = Api.apSu; allim?.invoke(if (ap > 0) "$mal 앞에 ${ap}문단이 기다리고 있습니다." else mal) } }, cho * 1000L)
        }
        soriFile(at) { f ->
            if (tk != token) return@soriFile
            val gidarimCho = ((System.currentTimeMillis() - gidarimSijak) / 1000).toInt()
            if (f == null) LibGirok.namgi("lib_sori_silpae", mapOf("cho" to gidarimCho, "moksori" to Store.voice, "inteonet" to (ctx?.let { Naeryeo.online(it) } ?: true)))
            else if (gidarimCho >= 5) LibGirok.namgi("lib_sori_gidarim", mapOf("cho" to gidarimCho, "moksori" to Store.voice))
            if (f == null) { ctx?.let { cx -> if (!Naeryeo.online(cx)) { speakPhone(at, tk); return@soriFile } }; playing = false; waiting = false; allim?.invoke("목소리를 받지 못했습니다. 인터넷을 확인한 뒤 다시 읽기를 눌러 주십시오."); bakkwim?.invoke(); return@soriFile }   // 0.3.2 — 폰 목소리는 인터넷이 끊겼을 때만
            p.setMediaItem(mi(android.net.Uri.fromFile(f).toString(), "$at"))
            queued = at; waiting = false
            p.prepare(); p.play()
            apseo(); bakkwim?.invoke()
        }
    }
    /** 앞 네 문단을 미리 받아 차례대로 줄에 붙임 — 문단 사이 틈이 없음 */
    private fun apseo() {
        if (kind != "geul" || !playing) return
        apseoHana(token, 1)
    }
    /** 0.7.1 (261014-L17) 뒤 세 문단을 차례로 하나씩(전에는 여섯 문단을 한꺼번에) — 다른 문단으로 옮기면 그만둠 */
    private fun apseoHana(tk: Int, k: Int) {
        if (k > 3 || tk != token || !playing) return
        val o = pos + k
        if (modu in 1..o) return
        if (o <= queued) { apseoHana(tk, k + 1); return }
        soriFile(o, apseo = true) { f -> if (tk == token && f != null) butigi(o, f); apseoHana(tk, k + 1) }
    }
    private val gidari = HashMap<Int, File>()
    private fun butigi(o: Int, f: File) {
        gidari[o] = f
        val p = player ?: return
        while (gidari.containsKey(queued + 1)) {
            val n = queued + 1
            p.addMediaItem(mi(android.net.Uri.fromFile(gidari.remove(n)!!).toString(), "$n"))
            queued = n
            if (waiting) { waiting = false; if (p.playbackState == Player.STATE_ENDED) { p.seekToNextMediaItem(); p.play() } }
        }
        bakkwim?.invoke()
    }

    fun pause() { tts?.stop();
        deutBonaegi()   // 0.8.2
        token += 1
        player?.pause(); playing = false; waiting = false
        if (kind != "geul") player?.let { Store.remember(i, title, kind, it.currentPosition / 1000.0, 0) }
        bakkwim?.invoke()
    }
    fun stop() { tts?.stop();
        deutBonaegi()   // 0.8.2 들은 시간 마저 보냄
        token += 1
        player?.stop(); player?.clearMediaItems()
        playing = false; waiting = false; gidari.clear()
    }
    fun next() { if (kind == "geul") { if (pos + 1 < modu) play(pos + 1) } else player?.let { it.seekTo(it.currentPosition + 30000) } }
    fun prev() { if (kind == "geul") { if (pos > 0) play(pos - 1) } else player?.let { it.seekTo((it.currentPosition - 30000).coerceAtLeast(0)) } }
    fun gaPo(o: Int) { if (kind == "geul") { pos = o.coerceIn(0, (modu - 1).coerceAtLeast(0)); if (playing) play(pos) else loadPage(pos) { bakkwim?.invoke() } ; Store.remember(i, title, kind, pos.toDouble(), modu) } else player?.seekTo((o * 1000).toLong()) }
    /** 0.4.4 처음부터 — 글자책은 첫 문단, 소리책·동영상은 0초 */
    fun cheoeum() { if (kind == "geul") gaPo(0) else player?.seekTo(0) }
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

    // 0.4.0 — 재생 위치(퍼센트), 앞으로 30초와 뒤로 30초(이사장님 지시)
    private fun choPerMundan(): Double { val ls = paras.values.map { it.length.toDouble() }; val avg = if (ls.isEmpty()) 120.0 else ls.average(); return maxOf(2.0, avg / (7.0 * Store.rate.toDouble())) }
    fun jeonche(): Double = if (kind == "geul") maxOf(modu, 1) * choPerMundan() else (player?.duration ?: 0L).let { if (it > 0) it / 1000.0 else 0.0 }
    fun jigeum(): Double = if (kind == "geul") pos * choPerMundan() else (player?.currentPosition ?: 0L) / 1000.0
    fun peosenteu(): Double { val t = jeonche(); return if (t > 0) (jigeum() / t * 100).coerceIn(0.0, 100.0) else 0.0 }
    fun sigan(s: Double): String { val n = maxOf(0, s.toInt()); val h = n / 3600; val m = (n % 3600) / 60; val c = n % 60; return if (h > 0) "" + h + "시간 " + m + "분" else if (m > 0) "" + m + "분 " + c + "초" else "" + c + "초" }
    fun wichiMal(): String = "전체 " + (if (kind == "geul") "약 " else "") + sigan(jeonche()) + " 가운데 " + sigan(jigeum()) + ", " + Math.round(peosenteu()) + "퍼센트"
    fun gaPeosenteu(p: Double) { val q = p.coerceIn(0.0, 100.0); if (kind == "geul") gaPo((modu * q / 100).toInt()) else player?.let { it.seekTo((jeonche() * q / 100 * 1000).toLong()) } }
    fun gaCho(s: Double) {
        if (kind != "geul") { player?.let { it.seekTo((it.currentPosition + (s * 1000).toLong()).coerceAtLeast(0)) }; return }
        val n = maxOf(1, Math.round(Math.abs(s) / choPerMundan()).toInt())
        gaPo(if (s > 0) pos + n else pos - n)
    }
}
