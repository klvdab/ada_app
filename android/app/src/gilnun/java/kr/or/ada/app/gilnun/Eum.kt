// 안드로이드 길눈 — 알림 소리(2.2.0, 빌드 261002-A4, 대표님 지시: 점지도 따라 걷기를 안드로이드에도)
// 아이폰 SoriEngine.sori 와 같은 높이·길이의 짧은 소리를 그 자리에서 만들어 냅니다(소리 파일 없음).
// 길 안내 소리 자리(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)로 내므로 음악을 들으셔도 함께 들립니다.
//   확신 880 · 경고 440 두 번 · 도착 660-880-1100
//   점지도 위를 제대로 디딤 1320 짧게 · 반 걸음 비켜남 700 · 한 걸음 벗어남 330-247 낮은 두 소리 · 돌아옴 880-1320 오르는 두 소리
// 2.4.0(빌드 261002-A7, 대표님 지시) 말로 하기 — 딩동 1318.5-1046.5(이제 말씀하십시오), 땡 1320(다 들었음). 아이폰과 같은 값.
//   마이크가 열려 있는 동안은 경고음·벗어남·딩동·땡만 내고 나머지는 쉼(아이폰 myeongryeongDeutneunJung 과 같음)
// 2.6.0(빌드 261002-A9, 대표님 지시) 긴급통화 중에는 알림 소리를 내지 않음(아이폰 SoriEngine.sori 와 같음)
package kr.or.ada.app.gilnun

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

enum class EumJong { HWAKSIN, GYEONGGO, DOCHAK, JEOMOK, BIKYEO, BEOSEO, DORAOM, DINGDONG, TTAENG }

object Eum {
    private const val SR = 22050
    private val main = Handler(Looper.getMainLooper())
    private val gotgan = HashMap<EumJong, ShortArray>()

    /** (높이 Hz, 길이 초) 조각 — 높이 0 은 쉼. 아이폰과 같은 값 */
    private fun jogak(j: EumJong): List<Pair<Double, Double>> = when (j) {
        EumJong.HWAKSIN -> listOf(880.0 to 0.08)
        EumJong.GYEONGGO -> listOf(440.0 to 0.15, 0.0 to 0.08, 440.0 to 0.15)
        EumJong.DOCHAK -> listOf(660.0 to 0.12, 0.0 to 0.04, 880.0 to 0.12, 0.0 to 0.04, 1100.0 to 0.22)
        EumJong.JEOMOK -> listOf(1320.0 to 0.07)
        EumJong.BIKYEO -> listOf(700.0 to 0.12)
        EumJong.BEOSEO -> listOf(330.0 to 0.2, 0.0 to 0.07, 247.0 to 0.26)
        EumJong.DORAOM -> listOf(880.0 to 0.08, 0.0 to 0.04, 1320.0 to 0.1)
        EumJong.DINGDONG -> listOf(1318.5 to 0.14, 0.0 to 0.03, 1046.5 to 0.3)
        EumJong.TTAENG -> listOf(1320.0 to 0.2)
    }

    private fun mandeulgi(jg: List<Pair<Double, Double>>): ShortArray {
        var modu = 0
        for ((_, len) in jg) modu += (SR * len).toInt()
        val out = ShortArray(modu)
        var w = 0
        for ((f, len) in jg) {
            val n = (SR * len).toInt()
            for (i in 0 until n) {
                if (f == 0.0) { out[w++] = 0; continue }
                val gajangjari = min(i, n - i).toDouble() / (SR * 0.01)   // 앞뒤 0.01초는 부드럽게(딸깍 소리 막음)
                val env = min(1.0, gajangjari)
                val v = sin(2 * PI * f * i / SR) * 0.5 * env
                out[w++] = (v * 32767).toInt().toShort()
            }
        }
        return out
    }

    /** 소리 하나 내기 — 겹쳐도 되고, 다 나면 스스로 치움 */
    fun naegi(j: EumJong) {
        main.post {
            if (Sori.malAnham) return@post   // 2.6.0 긴급통화 중에는 알림 소리도 내지 않음(아이폰과 같음)
            if (Sori.deutneunJung && j != EumJong.GYEONGGO && j != EumJong.BEOSEO && j != EumJong.DINGDONG && j != EumJong.TTAENG) return@post
            val s = gotgan.getOrPut(j) { mandeulgi(jogak(j)) }
            if (s.isEmpty()) return@post
            try {
                val t = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SR)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(s.size * 2)
                    .build()
                t.write(s, 0, s.size)
                t.setVolume(0.8f)
                t.play()
                val gil = s.size * 1000L / SR + 200
                main.postDelayed({
                    try { t.stop() } catch (e: Exception) {}
                    t.release()
                }, gil)
            } catch (e: Exception) {
                Girok.namgi("eum_oryu", mapOf("e" to (e.message ?: "")))
            }
        }
    }
}
