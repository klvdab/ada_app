// 안드로이드 길눈 — 말소리(2.0.0, 빌드 261001-A1)
// 길눈이 하는 모든 말은 여기를 거칩니다. 경고는 줄 맨 앞에서 하던 말을 끊고, 같은 말은 3초 안에 되풀이하지 않습니다.
// 말소리를 꺼 두시면 길눈 말은 내지 않고 톡백(화면 낭독)으로 한 번만 알립니다 — 대표님 원칙: 프로그램 말소리는 켜고 끌 수 있게.
// 2.4.0(빌드 261002-A7, 대표님 지시) 말로 하기 — 마이크가 열려 있는 동안(deutneunJung) 길눈 말이 마이크로 들어가지 않게
//   경고가 아닌 말은 잠시 맡아 두었다가 마이크가 닫히면 이어서 냄(15초 넘게 묵은 말은 버림). 경고는 듣기를 그만두게 하고 곧바로 말함
// 2.5.0(빌드 261002-A8, 대표님 지시) 길눈이 한 말을 갤럭시 워치에도 넘김(WatchLink.malBonae — 아이폰 SoriEngine 과 같이 세 글자 이상인 말만)
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
    @Volatile var malhaneunJung = false
        private set
    var majimak = ""
        private set
    /** 말소리를 꺼 두셨을 때 톡백으로 알리는 자리(화면이 채움) */
    var tokbaek: ((String) -> Unit)? = null

    /** 2.4.0 말로 하기가 마이크를 열어 둔 동안 */
    @Volatile var deutneunJung = false
        private set
    /** 2.4.0 듣는 중에 경고가 오면 — 듣기를 그만두게(MalHagi 가 채움) */
    var gyeonggoHook: (() -> Unit)? = null
    private class Matgim(val t: String, val geup: MalGeup, val f: (() -> Unit)?, val ttae: Long)
    private val matgim = ArrayList<Matgim>()

    fun sijak(ctx: Context) {
        if (tts != null) return
        tts = TextToSpeech(ctx.applicationContext) { st ->
            if (st == TextToSpeech.SUCCESS) {
                tts?.language = Locale.KOREAN
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
            override fun onStart(id: String?) { malhaneunJung = true }
            override fun onDone(id: String?) { kkeut(id) }
            @Deprecated("옛 안드로이드")
            override fun onError(id: String?) { kkeut(id) }
            override fun onStop(id: String?, interrupted: Boolean) { kkeut(id) }
        })
    }

    private fun kkeut(id: String?) {
        main.post {
            malhaneunJung = tts?.isSpeaking == true
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
            beon += 1
            val id = "m$beon"
            if (kkeutnamyeon != null) kkeutJul[id] = kkeutnamyeon
            tt.setSpeechRate(ppareugi)
            val q = if (geup == MalGeup.GYEONGGO) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val b = Bundle()
            tt.speak(t, q, b, id)
        }
    }

    /** 방금 한 말 다시 */
    fun dasiDeutgi() { if (majimak.isNotEmpty()) { dunmal.remove(majimak); mal(majimak) } }

    /** 하던 말을 멈춤 */
    fun meomchugi() { main.post { tts?.stop(); malhaneunJung = false } }

    /** 2.4.0 말로 하기가 마이크를 엶 — 하던 말을 멈추고, 닫힐 때까지 경고가 아닌 말은 맡아 둠 */
    fun deutgiSijak() {
        main.post {
            tts?.stop()
            malhaneunJung = false
            deutneunJung = true
        }
    }

    /** 2.4.0 마이크가 닫힘 — 맡아 둔 말을 이어서 냄(15초 넘게 묵은 말은 버림) */
    fun deutgiKkeut() {
        main.post {
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
