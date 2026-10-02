// 안드로이드 길눈 — 길눈 목소리(마이크로소프트 선희, 나스 sori/mal.php) 받아 틀기(2.7.0 묶음 b6_nanum, 아이폰 SoriEngine 2.28.0과 같은 규칙)
// 한 번 받은 말은 폰 캐시에 모아 두어 다음부터 곧바로. 처음 말은 2.5초(알려 드리는 말은 3초) 안에 못 받거나 통신이 끊기면 같은 말을 폰 목소리로 대신.
// 세 번 잇달아 못 받으면 5분 동안 폰 목소리. 경고는 기다리지 않고 늘 폰 목소리로 곧바로(Sori 가 가름). 190자 넘는 말은 폰 목소리.
// 약관 때문에 정식 음성 열쇠 전에는 나스 음악 열쇠가 있는 폰(음악·방송 묶음의 BangsongSeol.eumakTk — 꾸러미 gilnun_bangsong)에서만.
// Sori.mal 이 경고가 아닌 말을 여기 줄(julSeugi)에 세우면, 선희 목소리와 폰 목소리(대신 말하기)를 한 줄로 차례대로 냄 — 겹치지 않음.
// 폰 목소리 고르기(SeoljeongDeo.moksoriIreum)를 Sori 의 TTS 에 입히는 일(moksoriJeogyong)도 여기.
package kr.or.ada.app.gilnun

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.Executors

object NasMoksori {
    private val main = Handler(Looper.getMainLooper())
    private val il = Executors.newSingleThreadExecutor()
    private var ctx: Context? = null

    private class Mal(val t: String, val geup: MalGeup, val kkeut: (() -> Unit)?, val daesin: ((() -> Unit) -> Unit))
    private val jul = ArrayList<Mal>()
    private var jigeum: Mal? = null
    private var player: MediaPlayer? = null
    private var beon = 0
    private var silpae = 0          // 잇달아 못 받은 수 — 셋이면 5분 쉼
    private var swimTtae = 0L
    private var jeogyongIreum = ""

    /** 선희 목소리든 대신 말하는 폰 목소리든, 이 줄이 말하는 중 */
    @Volatile var malhaneunJung = false
        private set

    val daehwaMalMok = listOf("네", "잠깐만 기다려 주세요", "다시 한번 말씀해 주세요")

    /** 길눈을 켤 때 한 번 — 2초 뒤 나스 소리 창고를 깨워 둠(첫 말이 늦지 않게) */
    fun sijak(c: Context) {
        if (ctx != null) return
        ctx = c.applicationContext
        SeoljeongDeo.sijak(c)
        main.postDelayed({ kkaeugi() }, 2000)
    }

    /** 애저 정식 열쇠를 넣기 전에는 나스 음악 열쇠가 있는 폰에서만 */
    fun yeollim(c: Context? = ctx): Boolean {
        val cc = c ?: return false
        return Yeolsoe.eumakTk(cc).isNotEmpty()   // 2.7.0 통합 — 나스 음악 열쇠는 한 도우미로(옛 gilnun_bangsong 도 읽음)
    }

    /** 선희 목소리를 쓰기로 되어 있나 — Sori 가 경고가 아닌 말을 이 줄로 보낼지 가름 */
    val kyeojim: Boolean get() = ctx != null && SeoljeongDeo.msMoksori && yeollim()

    private fun sseum(t: String): Boolean {
        if (!kyeojim || t.length > 190 || !Tongsin.yeongyeol) return false
        if (silpae >= 3) {
            if (System.currentTimeMillis() - swimTtae < 300_000L) return false
            silpae = 0
        }
        return true
    }

    private fun gotgan(): File? {
        val c = ctx ?: return null
        val d = File(c.cacheDir, "gilnun_mal")
        if (!d.exists()) d.mkdirs()
        return d
    }

    /** (나스 주소, 받아 둘 파일) — 빠르기는 말하기 설정의 다섯 칸 그대로 */
    private fun juso(t: String): Pair<String, File?> {
        val r = Seoljeong.bbareugiDan.coerceIn(0, 4)
        val u = "https://lvd.ada.or.kr/jeom/sori/mal.php?q=2&v=0&r=$r&p=1&t=" + URLEncoder.encode(t, "UTF-8").replace("+", "%20")
        val h = MessageDigest.getInstance("SHA-256").digest("0|$r|$t".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return u to gotgan()?.let { File(it, "$h.mp3") }
    }

    private fun batgiDongi(u: String, pail: File?, handO: Int): Boolean {
        return try {
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = handO
            c.readTimeout = handO
            c.useCaches = false
            val ok = if (c.responseCode == 200) {
                val d = c.inputStream.use { it.readBytes() }
                if (d.size > 500 && pail != null) {
                    val im = File(pail.path + ".tmp")
                    im.writeBytes(d)
                    pail.delete()
                    im.renameTo(pail)
                } else d.size > 500
            } else false
            c.disconnect()
            ok
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Sori.mal 이 경고가 아닌 말을 세움. kkeut — 다 말한 뒤 할 일(Sori.mal 의 kkeutnamyeon).
     * daesin — 선희 목소리를 쓸 수 없을 때 같은 말을 폰 목소리로 하는 일(다 말하면 받은 함수를 부를 것).
     */
    fun julSeugi(t: String, geup: MalGeup, kkeut: (() -> Unit)?, daesin: ((() -> Unit) -> Unit)) {
        main.post {
            // 줄이 길면 알려 드리는 말부터 버림(아이폰 SoriEngine 과 같음)
            if (jul.size >= 4) {
                val i = jul.indexOfFirst { it.geup == MalGeup.JEONGBO }
                if (i >= 0) jul.removeAt(i).kkeut?.let { main.post(it) }
            }
            jul.add(Mal(t, geup, kkeut, daesin))
            if (jigeum == null) daeum()
        }
    }

    private fun daeum() {
        if (jul.isEmpty()) { jigeum = null; malhaneunJung = false; return }
        val m = jul.removeAt(0)
        jigeum = m
        malhaneunJung = true
        beon += 1
        val b0 = beon
        if (!sseum(m.t)) { daesinHagi(b0, m); return }
        val (u, pail) = juso(m.t)
        if (pail != null && pail.exists() && pail.length() > 500) { teulgi(b0, m, pail); return }
        val handO = if (m.geup == MalGeup.JEONGBO) 3000 else 2500
        il.execute {
            val ok = batgiDongi(u, pail, handO)
            main.post {
                if (b0 != beon) return@post   // 그사이 멈췄으면 버림
                if (ok && pail != null && pail.exists()) {
                    silpae = 0
                    teulgi(b0, m, pail)
                } else {
                    silpae += 1
                    if (silpae >= 3) swimTtae = System.currentTimeMillis()
                    Girok.namgi("ms_moksori_mot", mapOf("silpae" to silpae))
                    daesinHagi(b0, m)
                }
            }
        }
    }

    private fun maldaKkeut(b0: Int, m: Mal) {
        if (b0 != beon) return
        player?.let { try { it.release() } catch (e: Exception) {} }
        player = null
        jigeum = null
        m.kkeut?.invoke()
        daeum()
    }

    /** 못 받았으면 같은 말을 폰 목소리로 */
    private fun daesinHagi(b0: Int, m: Mal) {
        if (b0 != beon) return
        player?.let { try { it.release() } catch (e: Exception) {} }
        player = null
        try {
            m.daesin { main.post { maldaKkeut(b0, m) } }
        } catch (e: Exception) {
            maldaKkeut(b0, m)
        }
    }

    private fun teulgi(b0: Int, m: Mal, pail: File) {
        try {
            val p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            p.setDataSource(pail.path)
            p.setOnCompletionListener { main.post { maldaKkeut(b0, m) } }
            p.setOnErrorListener { _, _, _ -> main.post { daesinHagi(b0, m) }; true }
            p.prepare()
            p.setVolume(1f, 1f)
            player = p
            p.start()
        } catch (e: Exception) {
            pail.delete()   // 깨진 소리 파일이면 다음에 다시 받게
            daesinHagi(b0, m)
        }
    }

    /** 하던 말과 줄에 선 말을 모두 멈춤(경고가 올 때, 말로 하기가 마이크를 열 때, Sori.meomchugi). 기다리던 일은 차례로 부름 */
    fun meomchugi() {
        main.post {
            beon += 1
            player?.let { try { it.stop() } catch (e: Exception) {}; try { it.release() } catch (e: Exception) {} }
            player = null
            val l = ArrayList<Mal>()
            jigeum?.let { l.add(it) }
            l.addAll(jul)
            jul.clear()
            jigeum = null
            malhaneunJung = false
            for (m in l) m.kkeut?.let { main.post(it) }
        }
    }

    /** 앱을 켤 때·선희 목소리를 켤 때 — 한동안 쉬던 나스 소리 창고를 깨우고 대화 말을 미리 받아 둠 */
    fun kkaeugi() {
        if (!kyeojim || !Tongsin.yeongyeol) return
        il.execute {
            batgiDongi(juso("네").first, null, 4000)
            for (t in daehwaMalMok) {
                val (u, pail) = juso(t)
                if (pail != null && pail.exists() && pail.length() > 500) continue
                batgiDongi(u, pail, 6000)
            }
        }
    }

    /** 받아 둔 대화 말(네·잠깐만 기다려 주세요 등)이 있으면 그 파일 — 말로 하기가 곧바로 틀 때 */
    fun badadunPail(t: String): File? {
        if (!kyeojim) return null
        val p = juso(t).second ?: return null
        return if (p.exists() && p.length() > 500) p else null
    }

    /** 폰 목소리 고르기를 Sori 의 TTS 에 입힘 — 빈 이름이면 그대로(Sori.mal 이 말하기 바로 앞에 부름) */
    fun moksoriJeogyong(tts: TextToSpeech) {
        val n = SeoljeongDeo.moksoriIreum
        if (n.isEmpty() || n == jeogyongIreum) return
        try {
            val v = tts.voices?.firstOrNull { it.name == n } ?: return
            tts.voice = v
            jeogyongIreum = n
        } catch (e: Exception) {
            Girok.namgi("moksori_oryu", mapOf("e" to (e.message ?: "").take(60)))
        }
    }
}
