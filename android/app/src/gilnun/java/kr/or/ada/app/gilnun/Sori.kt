// 안드로이드 길눈 — 말소리(2.0.0, 빌드 261001-A1)
// 길눈이 하는 모든 말은 여기를 거칩니다. 경고는 줄 맨 앞에서 하던 말을 끊고, 같은 말은 3초 안에 되풀이하지 않습니다.
// 말소리를 꺼 두시면 길눈 말은 내지 않고 톡백(화면 낭독)으로 한 번만 알립니다 — 대표님 원칙: 프로그램 말소리는 켜고 끌 수 있게.
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
            majimak = t
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
}
