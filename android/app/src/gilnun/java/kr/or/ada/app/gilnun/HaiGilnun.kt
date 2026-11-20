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

    /** 2.26.0 부름말인가(아이폰 bureumMal 과 같은 뜻) — 받아 적은 말 하나를 통째로 볼 때
     *  2.94.0 (261120-A82, 이사장님 승인 2026-11-20 「1」, 아이폰 2.129.0과 같음) 「하이 길눈」은 어디서든, 「길눈아」는 말머리에서만, 「길눈」 한 마디는 말머리에서 토씨 없이 */
    fun bureumMal(t0: String): Boolean = haiMal(t0) || meoriMal(t0) != null || gilnunHanmadi(t0)

    /** 「하이 길눈」 — 말 어디에 있어도 부름(전과 같음) */
    fun haiMal(t0: String): Boolean {
        val t = t0.replace(" ", "").lowercase()
        return t.contains("하이길눈") || t.contains("하이길") || t.contains("hi길눈") ||
            t.contains("하이기른") || t.contains("아이길눈") || t.contains("하이글눈")
    }

    /** 2.94.0 「길눈」 뒤에 붙으면 부름이 아닌 토씨(길눈이·길눈은·길눈을·길눈에서 …) */
    private val gilnunTossi = listOf("이", "은", "을", "에", "의", "도", "과", "으로", "하고", "한테", "보다", "만", "처럼", "앱", "어플")
    /** 2.94.0 「길눈」 한 마디 바로 뒤에 이어지면 부름으로 받는 명령 머리말(아이폰과 같음) */
    private val gilnunDwiMal = listOf("어디", "지금", "다음", "다시", "소리", "말소리", "멈춰", "그만", "가자", "안내", "여기", "날씨", "몇시",
        "전화", "도와", "도움", "목적지", "바꿔", "변경", "뉴스", "라디오", "음악", "찾아", "읽어", "얼마", "됐어",
        "하던일", "긴급", "신호", "집으로", "길기억", "되짚어", "문찾아", "글자")
    private val gilnunPyogi = listOf("길눈", "길는", "기룬")

    /** 2.94.0 말머리 부름 — 「길눈아」(비슷하게 들린 말 포함, 길눈이·길눈은 등은 아님) 또는 「길눈」 바로 뒤 명령. 무엇으로 불렀는지(gilnuna·gilnun) */
    fun meoriMal(u: String): String? {
        val z = MalSajeon.ttuk(u)
        if (z.length < 3) return null
        val ap3 = z.take(3)
        if (ap3 == "길눈아" || ap3 == "길는아" || ap3 == "기루나" || ap3 == "길누나") return "gilnuna"
        val p = gilnunPyogi.firstOrNull { z.startsWith(it) }
        if (p != null) {
            val dwi = z.drop(p.length)
            if (gilnunDwiMal.any { dwi.startsWith(it) }) return "gilnun"
            return null
        }
        if (ap3 in listOf("길눈이", "길눈은", "길눈을", "길눈에", "길눈의", "길눈도")) return null
        if (MalSajeon.geori(MalSajeon.jamo(ap3), MalSajeon.jamo("길눈아")) <= 1) return "gilnuna"
        return null
    }

    /** 2.94.0 말머리의 「길눈」 한 마디뿐인가 — 뒤에 토씨가 붙는지 쉬는지 보아야 함 */
    fun gilnunHanmadi(u: String): Boolean = MalSajeon.ttuk(u) in gilnunPyogi

    // 2.94.0 말머리 — 0.7초 넘게 쉰 뒤 새로 시작한 말(아이폰 malMeori 와 같음)
    private var meoriJeon = ""
    private var meoriJigeum = ""
    private var meoriT = 0L
    private var beorimJari = -1
    private var beorimT = 0L
    private var beorimMal = ""
    private var swimR: Runnable? = null

    private fun malMeori(f: String): String {
        if (f != meoriJigeum) {
            if (System.currentTimeMillis() - meoriT >= 700) meoriJeon = meoriJigeum
            meoriJigeum = f
            meoriT = System.currentTimeMillis()
        }
        if (f.startsWith(meoriJeon)) return f.drop(meoriJeon.length)
        return if (f.length > meoriJeon.length) f.drop(meoriJeon.length) else ""
    }

    private fun meoriChoGihwa() {
        meoriJeon = ""; meoriJigeum = ""; meoriT = 0L; beorimJari = -1
        swimR?.let { main.removeCallbacks(it) }; swimR = null
    }

    /** 「길눈」이 들렸는데 부름으로 받지 않은 말 — 말머리가 아니거나 토씨가 붙음(같은 자리는 한 번만 남김) */
    private fun beorimGirok() {
        val f = meoriJigeum
        val jari = f.lastIndexOf("길눈")
        if (jari < 0) return
        val dwi = f.length - jari - 2
        if (dwi < 2 || f.length - jari > 12 || jari == beorimJari) return
        beorimJari = jari
        beorimT = System.currentTimeMillis()
        beorimMal = f.substring(jari).take(5)
        Girok.namgi("bureum_beorim", mapOf("mal" to beorimMal, "kkadak" to (if (jari == meoriJeon.length) "tossi" else "meori_ani")))
    }

    /** 부름을 알아들음 — 무엇으로 불렀는지(hai·gilnuna·gilnun) 남김. 버린 부름 뒤 8초 안이면 「다시 부름」(놓친 부름)으로도 남김 */
    private fun bureumBadeum(kind: String) {
        swimR?.let { main.removeCallbacks(it) }; swimR = null
        val g = HashMap<String, Any>()
        g["mal"] = kind
        if (System.currentTimeMillis() - beorimT < 8000) { g["dasi"] = true; Girok.namgi("bureum_nochim", mapOf("mal" to beorimMal)) }
        beorimT = 0L
        datgi()
        dasiTtae = System.currentTimeMillis() + 3000
        Girok.namgi("hai_deureum", g)
        MalHagi.dudeurim()   // 말로 하기 단추를 누른 것처럼 — 「네」 하고 명령을 들음
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
        meoriChoGihwa()   // 2.94.0 새로 열 때마다 말머리를 처음부터
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
        // 2.94.0 (261120-A82, 이사장님 승인 2026-11-20 「1」) 「하이 길눈」은 어디서든, 「길눈아」·「길눈」은 말머리에서만
        val u = malMeori(MalSajeon.ttuk(ls.firstOrNull() ?: ""))
        var kind: String? = if (ls.any { haiMal(it) }) "hai" else meoriMal(u)
        if (kind == null && gilnunHanmadi(u)) {
            if (kkeut) {
                kind = "gilnun"
            } else {
                // 「길눈」 한 마디 — 0.7초 동안 뒤에 말(토씨)이 붙지 않으면 부름
                swimR?.let { main.removeCallbacks(it) }
                val f0 = meoriJigeum
                val rr = Runnable { if (sr === r && meoriJigeum == f0) bureumBadeum("gilnun") }
                swimR = rr
                main.postDelayed(rr, 700)
            }
        }
        if (kind != null) { bureumBadeum(kind); return }
        beorimGirok()
        if (kkeut) { datgi(); dasiTtae = System.currentTimeMillis() + 300 }
    }

    fun datgi() {
        val r = sr ?: return
        sr = null
        try { r.cancel() } catch (e: Exception) {}
        try { r.destroy() } catch (e: Exception) {}
    }
}
