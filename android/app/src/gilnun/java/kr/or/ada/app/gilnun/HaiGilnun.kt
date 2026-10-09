// 안드로이드 길눈 2.26.0(빌드 261007-A14, 대장클, 이사장님 승인 1 「설정에서 켜는 방식, 처음은 끔」) — 하이 길눈 부르기
// 아이폰 MalDeutgi 의 부름 듣기를 안드로이드로 옮김. 안드로이드 12 이상의 「폰 안 받아쓰기」만 씀(통신 없이, 돈 들지 않음).
//   설정의 말하기 설정에서 켜고 끔(처음은 꺼짐) — 갤럭시는 받아쓰기를 다시 열 때 소리가 날 수 있어 이사장님이 들어 보신 뒤 정하기로 함.
//   길눈 화면이 앞에 있을 때만 들음(안드로이드가 뒤에서 마이크 열기를 막음). 길눈이 말하는 동안·말로 하기·긴급통화 중에는 쉼.
//   "하이 길눈"(하이 길, 길눈아도)을 알아들으면 말로 하기 단추를 누른 것처럼 명령을 들음.
package kr.or.ada.app.gilnun

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat

object HaiGilnun {
    private val main = Handler(Looper.getMainLooper())
    private var ctx: Context? = null
    private var sr: SpeechRecognizer? = null
    private var apE = false
    private var dolgo = false
    private var dasiTtae = 0L          // 다시 열어도 되는 때(잦은 다시 열기 막기)
    private var silpae = 0

    /** 이 폰에서 쓸 수 있는가 — 안드로이드 12 이상, 폰 안 받아쓰기가 있어야 함 */
    fun sseulSuItda(c: Context): Boolean =
        Build.VERSION.SDK_INT >= 31 && try { SpeechRecognizer.isOnDeviceRecognitionAvailable(c) } catch (e: Exception) { false }

    fun sijak(c: Context) {
        ctx = c.applicationContext
        if (dolgo) return
        dolgo = true
        main.postDelayed(salpim, 1500)
    }

    /** 길눈 화면이 앞에 왔는가(onResume 참, onPause 거짓).
     *  2.27.0 「화면이 꺼져도 듣기」를 켜셨고 알림 칸 길눈이 마이크 쓰임을 받았으면 뒤에서도 계속 들음 */
    fun apDanggye(on: Boolean) {
        apE = on || (Seoljeong.hiJamgeum && WichiService.maikJabeum)
        if (!apE) datgi()
    }

    /** 2.26.0 「하이 길눈」으로 들리는 말인가(아이폰 bureumMal 과 같은 뜻) */
    fun bureumMal(t0: String): Boolean {
        val t = t0.replace(" ", "").lowercase()
        return t.contains("하이길눈") || t.contains("하이길") || t.contains("길눈아") || t.contains("hi길눈") ||
            t.contains("하이기른") || t.contains("아이길눈") || t.contains("하이글눈")
    }

    private val salpim = object : Runnable {
        override fun run() {
            salpigi()
            main.postDelayed(this, 700)
        }
    }

    private fun salpigi() {
        val c = ctx ?: return
        val dwi = Seoljeong.hiJamgeum && WichiService.maikJabeum
        val deureoya = Seoljeong.hiGilnun && (apE || dwi) &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            !Sori.malhaneunJung && !Sori.deutneunJung && MalHagi.sangtae == MalSangtae.SWIM &&
            GinGeup.sangtae == GinGeupSangtae.EOPSEUM && !MalDeutgi.dolgoItda &&
            !JeonhwaGamsi.jeonhwaJung &&   // 2.30.0 전화 중에는 듣지 않음(통화 말을 명령으로 알아듣지 않게)
            !MalHagi.pyeonjipJung          // 2.31.0 길 찾기 편집창에 글자를 넣으시는 중에는 듣지 않음
        if (!deureoya) { datgi(); return }
        if (sr == null && System.currentTimeMillis() >= dasiTtae) yeolgi(c)
    }

    private fun yeolgi(c: Context) {
        if (Build.VERSION.SDK_INT < 31) return
        if (!sseulSuItda(c)) return
        val r = try { SpeechRecognizer.createOnDeviceSpeechRecognizer(c) } catch (e: Exception) { null } ?: return
        sr = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { silpae = 0 }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(b: Bundle?) { salpyeo(r, b, false) }
            override fun onResults(b: Bundle?) { salpyeo(r, b, true) }
            override fun onError(error: Int) {
                if (sr !== r) return
                // 말이 없어 끝난 것은 곧 다시 엶, 바쁘거나 다른 잘못이면 조금 쉬었다가
                val swim = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 300L
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> 60000L
                    else -> { silpae += 1; minOf(30000L, 2000L * silpae) }
                }
                if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) Girok.namgi("hai_oryu", mapOf("e" to error))
                datgi()
                dasiTtae = System.currentTimeMillis() + swim
            }
        })
        val sik = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 10000L)
        }
        try {
            r.startListening(sik)
        } catch (e: Exception) {
            datgi()
            dasiTtae = System.currentTimeMillis() + 5000
        }
    }

    private fun salpyeo(r: SpeechRecognizer, b: Bundle?, kkeut: Boolean) {
        if (sr !== r) return
        val ls = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
        if (ls.any { bureumMal(it) }) {
            datgi()
            dasiTtae = System.currentTimeMillis() + 3000
            Girok.namgi("hai_deureum", emptyMap())
            MalHagi.dudeurim()   // 말로 하기 단추를 누른 것처럼 — 「네」 하고 명령을 들음
            return
        }
        if (kkeut) { datgi(); dasiTtae = System.currentTimeMillis() + 300 }
    }

    fun datgi() {
        val r = sr ?: return
        sr = null
        try { r.cancel() } catch (e: Exception) {}
        try { r.destroy() } catch (e: Exception) {}
    }
}
