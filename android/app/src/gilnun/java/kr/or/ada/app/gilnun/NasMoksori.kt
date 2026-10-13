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
    /** 2.38.0 뒤에서 받아 담는 일(말하는 줄을 막지 않게 따로) */
    private val dwi = Executors.newSingleThreadExecutor()
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

    // MARK: 2.38.0 (261013-A26, 이사장님 승인 2026-10-13 「그래」, 아이폰 2.66.0과 같음) 자주 하는 말 — 셈해 두었다가 앱을 켤 때 미리 받아 둠
    /** 길눈이 늘 하는 말(셈이 쌓이기 전에도 미리 받아 둠) */
    private val neulMal = listOf("남은 길을 걸어서 안내합니다.", "차 안 안내를 시작합니다.", "다시 한번 말씀해 주세요", "목적지에 도착했습니다.",
        "지금은 마이크를 열지 못했습니다. 잠시 뒤 다시 해 주십시오.", "음악을 껐습니다.", "라디오를 껐습니다.", "TV를 껐습니다.",
        "여정을 끝냈습니다.", "걷는 안내를 시작합니다. 흰지팡이를 꼭 짚어 주십시오.")

    private fun jajuSeogi(t: String) {
        val c = ctx ?: return
        try {
            val sp = c.getSharedPreferences("gilnun_ms", Context.MODE_PRIVATE)
            val o = org.json.JSONObject(sp.getString("jaju", "{}") ?: "{}")
            o.put(t, o.optInt(t, 0) + 1)
            if (o.length() > 300) {
                val ls = o.keys().asSequence().map { it to o.optInt(it) }.sortedBy { it.second }.take(o.length() - 300).toList()
                for ((k, _) in ls) o.remove(k)
            }
            sp.edit().putString("jaju", o.toString()).apply()
        } catch (e: Exception) {}
    }

    /** 두 번 넘게 한 말 가운데 많이 한 것부터 80가지 */
    private fun jajuMal(): List<String> {
        val c = ctx ?: return emptyList()
        return try {
            val o = org.json.JSONObject(c.getSharedPreferences("gilnun_ms", Context.MODE_PRIVATE).getString("jaju", "{}") ?: "{}")
            o.keys().asSequence().map { it to o.optInt(it) }.filter { it.second >= 2 }.sortedByDescending { it.second }.take(80).map { it.first }.toList()
        } catch (e: Exception) { emptyList() }
    }

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
        jajuSeogi(m.t)   // 2.38.0 자주 하는 말 세기
        // 2.31.0 (261009-A19, 이사장님 승인) 긴 말(말벗 대답 등)은 만드는 데 오래 걸림 — 글자 수에 맞춰 더 기다림(한 자에 0.03초, 많아야 8초, 아이폰과 같음)
        // 2.36.0 (261011-A1, 이사장님 승인 2026-10-11 「1」) 기다림을 늘림(처음 4초, 타고 가는 중 1.5초 더, 많아야 10초) — 아이폰과 같음
        val tagoGam = if (YeojeongEngine.jigeum?.danggye == Danggye.TANEUN_JUNG) 1500 else 0
        val handO = minOf(10000, (if (m.geup == MalGeup.JEONGBO) 4500 else 4000) + tagoGam + m.t.length * 40)
        il.execute {
            val ok = batgiDongi(u, pail, handO)
            main.post {
                if (b0 != beon) return@post   // 그사이 멈췄으면 버림
                if (ok && pail != null && pail.exists()) {
                    silpae = 0
                    teulgi(b0, m, pail)
                } else {
                    if (m.t.length <= 60) silpae += 1   // 긴 말을 못 받은 것은 세 번 실패(5분 쉼)에 셈하지 않음
                    if (silpae >= 3) swimTtae = System.currentTimeMillis()
                    Girok.namgi("ms_moksori_mot", mapOf("silpae" to silpae, "geulja" to m.t.length, "ieo" to true))
                    daesinHagi(b0, m)
                    // 2.38.0 폰 목소리로 말하되 뒤에서 30초까지 이어 받아 담아 둠 — 같은 말은 다음부터 기다리지 않고 협회 목소리로
                    if (Tongsin.yeongyeol) dwi.execute {
                        if (batgiDongi(u, pail, 30000)) main.post { silpae = 0; Girok.namgi("ms_moksori_dameum", mapOf("geulja" to m.t.length)) }
                    }
                }
            }
        }
    }

    private fun maldaKkeut(b0: Int, m: Mal) {
        if (b0 != beon || jigeum !== m) return   // 2.25.0 지킴이와 끝남 알림이 둘 다 와도 한 번만
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
            // 2.25.0 (전체 점검) 블루투스가 끊기는 등 끝남 알림이 오지 않아도 줄이 멈추지 않게(아이폰 SoriEngine 과 같음)
            val gil = p.duration
            if (gil > 0) main.postDelayed({ maldaKkeut(b0, m) }, gil + 1500L)
        } catch (e: Exception) {
            pail.delete()   // 깨진 소리 파일이면 다음에 다시 받게
            daesinHagi(b0, m)
        }
    }

    /** 하던 말과 줄에 선 말을 모두 멈춤(경고가 올 때, 말로 하기가 마이크를 열 때, Sori.meomchugi). 기다리던 일은 차례로 부름 */
    fun meomchugi() { main.post { meomchugiBaro() } }

    /** 2.27.0 화면 줄(main)에서 곧바로 멈춤 — 말 자르고 새로 말하기가 새 말을 넣기 전에 */
    fun meomchugiBaro() {
        run {
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
        // 2.38.0 늘 하는 말·자주 하는 말을 하나씩 차례로 받아 담음(나스가 몰리지 않게)
        val ls = (neulMal + jajuMal()).distinct().filter { it !in daehwaMalMok }
        dwi.execute {
            var n = 0
            for (t in ls) {
                val (u, pail) = juso(t)
                if (pail == null || (pail.exists() && pail.length() > 500)) continue
                if (batgiDongi(u, pail, 30000)) n += 1
            }
            if (n > 0) main.post { Girok.namgi("ms_miri_batgi", mapOf("su" to n, "modu" to ls.size)) }
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
