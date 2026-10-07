// 안드로이드 길눈 — 말소리(2.0.0, 빌드 261001-A1)
// 길눈이 하는 모든 말은 여기를 거칩니다. 경고는 줄 맨 앞에서 하던 말을 끊고, 같은 말은 3초 안에 되풀이하지 않습니다.
// 말소리를 꺼 두시면 길눈 말은 내지 않고 톡백(화면 낭독)으로 한 번만 알립니다 — 대표님 원칙: 프로그램 말소리는 켜고 끌 수 있게.
// 2.4.0(빌드 261002-A7, 대표님 지시) 말로 하기 — 마이크가 열려 있는 동안(deutneunJung) 길눈 말이 마이크로 들어가지 않게
//   경고가 아닌 말은 잠시 맡아 두었다가 마이크가 닫히면 이어서 냄(15초 넘게 묵은 말은 버림). 경고는 듣기를 그만두게 하고 곧바로 말함
// 2.5.0(빌드 261002-A8, 대표님 지시) 길눈이 한 말을 갤럭시 워치에도 넘김(WatchLink.malBonae — 아이폰 SoriEngine 과 같이 세 글자 이상인 말만)
// 2.6.0(빌드 261002-A9, 대표님 지시) 긴급통화 중(tonghwaJung)에는 길눈 말소리를 내지 않음 — 말은 마지막 말로만 간직하고, 경고는 길게 진동(아이폰 SoriEngine 2.12.0과 같음)
// 2.7.0(빌드 261002-B1, 대표님 지시) 묶음 b5 — 길눈이 말하면 방송 소리를 줄이고(경고는 멈춤, 기사 읽기는 쉼) 말이 끝나면 되돌림(BangsongDuck),
//   말로 하기가 듣는 동안은 방송 멈춤. 묶음 b6 — 길눈 목소리(선희, NasMoksori)와 목소리 고르기. 경고는 늘 폰 목소리로 곧바로.
//   말하는 중(malhaneunJung)은 폰 목소리와 선희 목소리를 함께 봄(확신음·신호기 소리가 선희 목소리와 겹치지 않게)
package kr.or.ada.app.gilnun

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

enum class MalGeup { JEONGBO, ANNAE, GYEONGGO }

object Sori {
    private var tts: TextToSpeech? = null
    private var junbi = false
    private val main = Handler(Looper.getMainLooper())
    private val dunmal = HashMap<String, Long>()
    private var beon = 0
    private val kkeutJul = HashMap<String, () -> Unit>()
    @Volatile private var ttsMalhaneun = false
    /** 길눈이 말하는 중 — 폰 목소리 또는 선희 목소리(2.7.0 b6) */
    val malhaneunJung: Boolean get() = ttsMalhaneun || NasMoksori.malhaneunJung
    var majimak = ""
        private set
    /** 말소리를 꺼 두셨을 때 톡백으로 알리는 자리(화면이 채움) */
    var tokbaek: ((String) -> Unit)? = null

    /** 2.6.0 긴급통화 중 — 길눈 말소리를 내지 않음(GinGeup 이 켜고 끔) */
    @Volatile var tonghwaJung = false

    /** 2.4.0 말로 하기가 마이크를 열어 둔 동안 */
    @Volatile var deutneunJung = false
        private set
    /** 2.4.0 듣는 중에 경고가 오면 — 듣기를 그만두게(MalHagi 가 채움) */
    var gyeonggoHook: (() -> Unit)? = null
    private class Matgim(val t: String, val geup: MalGeup, val f: (() -> Unit)?, val ttae: Long)
    private val matgim = ArrayList<Matgim>()

    private var ctxJeojang: Context? = null

    fun sijak(ctx: Context) {
        ctxJeojang = ctx.applicationContext
        if (tts != null) return
        tts = TextToSpeech(ctx.applicationContext) { st ->
            if (st == TextToSpeech.SUCCESS) {
                // 2.25.0 한국어 목소리 자료가 없으면 알고 기록(그래도 말은 시도함)
                val ra = tts?.setLanguage(Locale.KOREAN) ?: TextToSpeech.LANG_NOT_SUPPORTED
                if (ra < 0) Girok.namgi("sori_eoneo", mapOf("r" to ra))
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                junbi = true
                Girok.namgi("sori_junbi", mapOf("ok" to true))
            } else {
                Girok.namgi("sori_junbi", mapOf("ok" to false, "st" to st))
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { ttsMalhaneun = true }
            override fun onDone(id: String?) { kkeut(id) }
            @Deprecated("옛 안드로이드")
            override fun onError(id: String?) { kkeut(id) }
            override fun onStop(id: String?, interrupted: Boolean) { kkeut(id) }
        })
    }

    private fun kkeut(id: String?) {
        main.post {
            ttsMalhaneun = tts?.isSpeaking == true
            if (!malhaneunJung) BangsongDuck.malKkeut()   // 2.7.0 b5 0.35초 뒤에도 조용하면 방송을 되돌림
            val f = id?.let { kkeutJul.remove(it) }
            f?.invoke()
        }
    }

    private val ppareugi: Float get() = listOf(0.6f, 0.8f, 1.0f, 1.25f, 1.5f)[Seoljeong.bbareugiDan.coerceIn(0, 4)]

    /** 길눈이 말하기. 다 말한 뒤 할 일이 있으면 kkeutnamyeon 에 */
    fun mal(t0: String, geup: MalGeup = MalGeup.ANNAE, kkeutnamyeon: (() -> Unit)? = null) {
        val t = t0.trim()
        if (t.isEmpty()) { kkeutnamyeon?.let { main.post(it) }; return }
        main.post {
            // 2.6.0 긴급통화 중 — 말하지 않고, 경고는 길게 진동
            if (tonghwaJung) {
                majimak = t
                if (geup == MalGeup.GYEONGGO) GinGeup.jindongGilge(true)
                kkeutnamyeon?.let { main.post(it) }
                return@post
            }
            // 2.4.0 마이크가 열려 있으면 — 경고는 듣기를 그만두게 하고 곧바로, 다른 말은 맡아 둠
            if (deutneunJung) {
                if (geup == MalGeup.GYEONGGO) {
                    deutneunJung = false
                    gyeonggoHook?.invoke()
                } else {
                    matgim.add(Matgim(t, geup, kkeutnamyeon, System.currentTimeMillis()))
                    while (matgim.size > 4) matgim.removeAt(0).f?.let { main.post(it) }
                    return@post
                }
            }
            majimak = t
            if (t.length > 2) WatchLink.malBonae(t)   // 2.5.0 워치에 마지막 안내로
            if (geup != MalGeup.GYEONGGO && !Seoljeong.malKyeojim) {
                tokbaek?.invoke(t)
                kkeutnamyeon?.let { main.postDelayed(it, (800 + t.length * 90L).coerceAtMost(8000)) }
                return@post
            }
            val now = System.currentTimeMillis()
            val ap = dunmal[t]
            if (ap != null && now - ap < 3000 && kkeutnamyeon == null) return@post
            dunmal[t] = now
            if (dunmal.size > 60) dunmal.entries.removeAll { now - it.value > 10000 }
            val tt = tts
            if (tt == null || !junbi) {
                tokbaek?.invoke(t)
                kkeutnamyeon?.let { main.postDelayed(it, 1500) }
                return@post
            }
            NasMoksori.moksoriJeogyong(tt)   // 2.7.0 b6 목소리 고르기(설정 — 말하기 설정)
            BangsongDuck.malSijak(geup)      // 2.7.0 b5 방송 소리를 작게(경고는 멈춤, 기사 읽기는 쉼)
            // 2.7.0 b6 길눈 목소리(선희, 나스 sori/mal.php) — 경고가 아니면 NasMoksori 줄로. 선희 소리와(못 받으면) 폰 목소리 대신 말하기가 한 줄로 차례대로
            if (geup != MalGeup.GYEONGGO && NasMoksori.kyeojim) {
                NasMoksori.julSeugi(t, geup, kkeutnamyeon) { daesinKkeut ->
                    beon += 1
                    val id2 = "m$beon"
                    kkeutJul[id2] = daesinKkeut
                    tt.setSpeechRate(ppareugi)
                    if (tt.speak(t, TextToSpeech.QUEUE_ADD, Bundle(), id2) != TextToSpeech.SUCCESS) malMotham(id2, t) else jikimi(id2, t, 0)
                }
                return@post
            }
            if (geup == MalGeup.GYEONGGO) NasMoksori.meomchugi()   // 경고는 기다리지 않고 폰 목소리로 곧바로
            beon += 1
            val id = "m$beon"
            if (kkeutnamyeon != null) kkeutJul[id] = kkeutnamyeon
            tt.setSpeechRate(ppareugi)
            val q = if (geup == MalGeup.GYEONGGO) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val b = Bundle()
            if (tt.speak(t, q, b, id) != TextToSpeech.SUCCESS) malMotham(id, t) else jikimi(id, t, 0)
        }
    }

    /** 2.25.0 (전체 점검) 폰 목소리 엔진이 말을 받지 못함 — 기다리는 일을 풀고 엔진을 새로 세움(그대로 두면 「말하는 중」이 남아 안내·방송이 모두 멈춤) */
    private fun malMotham(id: String, t: String) {
        Girok.namgi("sori_motham", mapOf("t" to t.take(20)))
        ttsMalhaneun = false
        kkeutJul.remove(id)?.let { main.post(it) }
        BangsongDuck.malKkeut()
        tokbaek?.invoke(t)
        val c = ctxJeojang
        try { tts?.shutdown() } catch (e: Exception) {}
        tts = null
        junbi = false
        if (c != null) main.postDelayed({ sijak(c) }, 500)
    }

    /** 2.25.0 끝남 알림이 오지 않아도 말 줄이 멈추지 않게 — 넉넉히 기다린 뒤에도 조용하면 끝난 것으로 봄 */
    private fun jikimi(id: String, t: String, beonjjae: Int) {
        main.postDelayed({
            if (!kkeutJul.containsKey(id) && !ttsMalhaneun) return@postDelayed
            if (tts?.isSpeaking == true && beonjjae < 4) { jikimi(id, t, beonjjae + 1); return@postDelayed }
            Girok.namgi("sori_jikimi", mapOf("beon" to beonjjae))
            kkeut(id)
        }, 4000L + t.length * 250L)
    }

    /** 방금 한 말 다시 */
    fun dasiDeutgi() { if (majimak.isNotEmpty()) { dunmal.remove(majimak); mal(majimak) } }

    /** 하던 말을 멈춤 */
    fun meomchugi() { main.post { tts?.stop(); ttsMalhaneun = false; NasMoksori.meomchugi(); BangsongDuck.malKkeut() } }   // 2.7.0 b5·b6

    /** 2.4.0 말로 하기가 마이크를 엶 — 하던 말을 멈추고, 닫힐 때까지 경고가 아닌 말은 맡아 둠 */
    fun deutgiSijak() {
        main.post {
            BangsongDuck.deutgi(true)    // 2.7.0 b5 말로 하기가 듣는 동안 방송 멈춤
            tts?.stop()
            NasMoksori.meomchugi()       // 2.7.0 b6 마이크가 열리면 선희 목소리도 멈춤
            ttsMalhaneun = false
            deutneunJung = true
        }
    }

    /** 2.4.0 마이크가 닫힘 — 맡아 둔 말을 이어서 냄(15초 넘게 묵은 말은 버림) */
    fun deutgiKkeut() {
        main.post {
            BangsongDuck.deutgi(false)   // 2.7.0 b5 다시 틂
            deutneunJung = false
            if (matgim.isEmpty()) return@post
            val l = ArrayList(matgim)
            matgim.clear()
            val now = System.currentTimeMillis()
            for (m in l) {
                if (now - m.ttae > 15000) { m.f?.let { main.post(it) }; continue }
                mal(m.t, m.geup, m.f)
            }
        }
    }
}
