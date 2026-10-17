// LVD투어스튜디오 안드로이드 — 녹음·mp3·받아쓰기·못 올린 것 (1.1.0판, 빌드 261017-T3, 방클, 이사장님 승인 「첫단계부터 시작해」)
// 아이폰 Tour/Nokeum.swift 와 같은 일:
//   현장 메모 말로 적기(memo) · 지점별 읽으며 녹음(prac) — 담은 소리는 폰 안에서 mp3 로 바꿔 올림(이사장님 원칙 「영상 없는 녹음은 mp3」, 웹과 같은 lamejs)
//   mp3 로 못 바꾸면 담긴 그대로(wav) 올림 — 웹과 같음
//   받아쓰기는 안드로이드 13 이상에서 폰의 받아쓰기에 녹음과 같은 소리를 함께 흘려 넣음. 안 되면 소리로만 남김 — 웹과 같은 안내
//   못 올린 사진·소리는 폰에 남겨 두었다가 다시 올림
package kr.or.ada.app.tour

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.webkit.WebView
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

// ── 녹음기 (16kHz 한 줄 소리) ──
object Nokeumgi {
    const val RATE = 16000
    @Volatile var jung = false; private set
    var galle = ""; private set
    var spotId = ""; private set
    var where_ = ""; private set
    private var th: Thread? = null
    private var pcm: File? = null
    private var sijak = 0L
    private val main = Handler(Looper.getMainLooper())
    // 받아쓰기 (안드로이드 13 이상)
    private var sr: SpeechRecognizer? = null
    @Volatile private var pipeOut: OutputStream? = null
    private val deureun = StringBuilder()
    @Volatile private var srKkeut = true
    private var ctx: Context? = null

    @SuppressLint("MissingPermission")
    fun start(c: Context, galle: String, spotId: String, where_: String): String? {
        if (jung) return "이미 녹음 중입니다. 먼저 끝내 주십시오."
        val buf = maxOf(AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), RATE)
        val rec = try { AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buf) } catch (e: Exception) { null }
        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) { rec?.release(); return "녹음을 시작하지 못했습니다." }
        val f = File(c.cacheDir, "nokeum_${System.currentTimeMillis()}.pcm")
        pcm = f; ctx = c.applicationContext
        this.galle = galle; this.spotId = spotId; this.where_ = where_
        deureun.setLength(0)
        jung = true; sijak = System.currentTimeMillis()
        if (Build.VERSION.SDK_INT >= 33) main.post { badaSijak() }
        th = Thread {
            val b = ByteArray(buf)
            try {
                rec.startRecording()
                FileOutputStream(f).use { out ->
                    while (jung) {
                        val n = rec.read(b, 0, b.size)
                        if (n > 0) {
                            out.write(b, 0, n)
                            try { pipeOut?.write(b, 0, n) } catch (e: Exception) { }
                        }
                    }
                }
            } catch (e: Exception) { } finally { try { rec.stop() } catch (e: Exception) { }; rec.release() }
        }.apply { start() }
        return null
    }

    /** 녹음 끝 — 뒤 일꾼에서 부름. (wav 파일, 초, 받아쓴 말) */
    fun stop(): Triple<File, Int, String>? {
        if (!jung) return null
        jung = false
        th?.join(3000); th = null
        val sec = ((System.currentTimeMillis() - sijak) / 1000.0).roundToInt()
        try { pipeOut?.close() } catch (e: Exception) { }
        pipeOut = null
        // 받아쓰기가 끝나기를 잠깐 기다림
        val dl = System.currentTimeMillis() + 8000
        while (!srKkeut && System.currentTimeMillis() < dl) Thread.sleep(200)
        main.post { try { sr?.destroy() } catch (e: Exception) { }; sr = null }
        val p = pcm ?: return null
        val wav = File(p.parentFile, p.nameWithoutExtension + ".wav")
        wavMandeulgi(p, wav); p.delete()
        return Triple(wav, sec, deureun.toString().trim())
    }

    private fun badaSijak() {
        val c = ctx ?: return
        if (!jung || Build.VERSION.SDK_INT < 33) return
        if (!SpeechRecognizer.isRecognitionAvailable(c)) { srKkeut = true; return }
        try {
            val pp = ParcelFileDescriptor.createPipe()
            val s = sr ?: SpeechRecognizer.createSpeechRecognizer(c).also { sr = it }
            srKkeut = false
            s.setRecognitionListener(object : RecognitionListener {
                override fun onResults(r: Bundle?) {
                    r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { if (it.isNotBlank()) deureun.append(it).append(' ') }
                    dasi()
                }
                override fun onError(e: Int) { dasi() }
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(t: Int, p: Bundle?) {}
            })
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pp[0])
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, RATE)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
            pipeOut = ParcelFileDescriptor.AutoCloseOutputStream(pp[1])
            s.startListening(i)
        } catch (e: Exception) { srKkeut = true }
    }
    /** 받아쓰기 한 토막이 끝남 — 아직 녹음 중이면 이어서 받아씀 */
    private fun dasi() {
        main.post {
            val old = pipeOut; pipeOut = null
            try { old?.close() } catch (e: Exception) { }
            if (jung) badaSijak() else srKkeut = true
        }
    }

    private fun wavMandeulgi(pcm: File, wav: File) {
        val n = pcm.length().toInt()
        RandomAccessFile(wav, "rw").use { w ->
            w.setLength(0)
            fun s(t: String) = w.write(t.toByteArray(Charsets.US_ASCII))
            fun i32(v: Int) = w.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
            fun i16(v: Int) = w.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
            s("RIFF"); i32(36 + n); s("WAVE"); s("fmt "); i32(16); i16(1); i16(1); i32(RATE); i32(RATE * 2); i16(2); i16(16); s("data"); i32(n)
            pcm.inputStream().use { inp -> val b = ByteArray(65536); while (true) { val r = inp.read(b); if (r <= 0) break; w.write(b, 0, r) } }
        }
    }
}

// ── mp3 바꾸기 (보이지 않는 웹 칸에서 lamejs) ──
object Mp3Gi {
    private var web: WebView? = null
    private val main = Handler(Looper.getMainLooper())

    private fun jsRun(js: String, timeoutS: Long = 60): String? {
        val l = CountDownLatch(1); var r: String? = null
        main.post { val w = web; if (w == null) l.countDown() else w.evaluateJavascript(js) { v -> r = v; l.countDown() } }
        return if (l.await(timeoutS, TimeUnit.SECONDS)) r else null
    }
    private fun junbi(c: Context): Boolean {
        if (web != null && jsRun("typeof mpStart")?.contains("function") == true) return true
        val l = CountDownLatch(1)
        main.post {
            val w = WebView(c.applicationContext)
            w.settings.javaScriptEnabled = true
            w.webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(v: WebView?, u: String?) { l.countDown() }
            }
            web = w
            w.loadDataWithBaseURL("file:///android_asset/", "<!doctype html><html><head><meta charset='utf-8'><script src='mp3enc.js'></script></head><body></body></html>", "text/html", "utf-8", null)
        }
        l.await(20, TimeUnit.SECONDS)
        return jsRun("typeof mpStart")?.contains("function") == true
    }
    /** wav 를 mp3 로 — 뒤 일꾼에서 부름. 못 바꾸면 null */
    fun bakkugi(c: Context, wav: File): ByteArray? = try {
        val d = wav.readBytes()
        if (d.size <= 44 + 3200 || !junbi(c)) null
        else {
            jsRun("mpStart(${Nokeumgi.RATE})")
            var i = 44; val CH = 96000
            while (i < d.size) {
                val e = minOf(d.size, i + CH); val len = (e - i) and 1.inv()
                if (len <= 0) break
                jsRun("mpPush('" + android.util.Base64.encodeToString(d, i, len, android.util.Base64.NO_WRAP) + "')")
                i += len
            }
            val r = jsRun("mpEnd()", 120)?.trim('"')
            val mp3 = if (r.isNullOrEmpty() || r == "null") null else android.util.Base64.decode(r, android.util.Base64.DEFAULT)
            if (mp3 != null && mp3.size > 100) mp3 else null
        }
    } catch (e: Exception) { null }
}

// ── 못 올린 것 (폰에 남겨 두기) ──
object Daegi {
    private fun teo(c: Context) = File(c.filesDir, "daegi").apply { mkdirs() }
    fun neoki(c: Context, g: JSONObject, data: ByteArray) {
        val ref = g.geul("ref")
        File(teo(c), "$ref.bin").writeBytes(data)
        File(teo(c), "$ref.json").writeText(g.toString())
    }
    fun mokrok(c: Context): List<JSONObject> = (teo(c).listFiles() ?: arrayOf()).filter { it.extension == "json" }
        .mapNotNull { try { JSONObject(it.readText()) } catch (e: Exception) { null } }.sortedBy { it.geul("at") }
    fun data(c: Context, ref: String): ByteArray? = File(teo(c), "$ref.bin").takeIf { it.exists() }?.readBytes()
    fun ppaegi(c: Context, ref: String) { File(teo(c), "$ref.bin").delete(); File(teo(c), "$ref.json").delete() }
    fun su(c: Context) = mokrok(c).size
    fun saeRef(jong: String) = (if (jong == "photo") "p" else "a") + System.currentTimeMillis()
}

// ── 연습 되돌아보기 (웹 pracReview·pracSay 와 같음) ──
object Yeonseup {
    val BANNED = listOf("보인다", "보임", "보이는", "보여", "보시면", "보세요", "바라본", "바라보", "내려다본", "보는 바와 같이", "눈에 들어오", "눈앞에", "한눈에", "보입니다", "보이고", "보이며", "보였", "보이지")
    fun geumji(t: String): List<String> {
        val s0 = t.replace(Regex("만져\\s*보(시면|세요|십시오|십시요|시고|시길|실|셔서)"), "만져[촉각]")
        return BANNED.filter { s0.contains(it) }
    }
    fun review(k: Kos, s: Spot, sec: Int, heard: String, at: String): JSONObject {
        val chars = k.spotText(s).count { !it.isWhitespace() }
        val cpm = if (sec > 0) (chars / (sec / 60.0)).roundToInt() else 0
        val room = (k.stayOf(s) * 60).toInt()
        val over = if (room > 0) sec - room else 0
        return JSONObject().put("sec", sec).put("cpm", cpm).put("over", over).put("heard", heard)
            .put("ban", org.json.JSONArray(if (heard.isEmpty()) listOf<String>() else geumji(heard))).put("at", at)
    }
    fun mal(k: Kos, s: Spot): String {
        val p = s.d.optJSONObject("prac") ?: run { val r = (s.n("real") ?: 0.0).toInt(); return if (r > 0) "${r}초 걸렸습니다." else "" }
        val base = (k.d.su("cps") ?: 330.0).toInt()
        val sec = (p.su("sec") ?: 0.0).toInt(); val over = (p.su("over") ?: 0.0).toInt(); val cpm = (p.su("cpm") ?: 0.0).toInt()
        val heard = p.geul("heard"); val ba = p.optJSONArray("ban"); val ban = (0 until (ba?.length() ?: 0)).map { ba!!.optString(it) }
        val m = StringBuilder("${sec}초 걸렸습니다.")
        if (over > 0) m.append(" 머무는 시간보다 ${over}초 깁니다.") else if (k.stayOf(s) > 0) m.append(" 머무는 시간 안에 들어갑니다.")
        if (cpm > 0) {
            m.append(" 1분에 ${cpm}자 빠르기로, 기준 ${base}자보다 ")
            m.append(if (cpm > base * 1.15) "빠릅니다. 조금 천천히 읽으셔도 됩니다." else if (cpm < base * 0.8) "느립니다." else "알맞습니다.")
        }
        if (heard.isNotEmpty()) m.append(if (ban.isEmpty()) " 말씀에서 시각 전제 표현은 들리지 않았습니다(참고)." else " 말씀 가운데 시각 전제 표현이 들렸습니다(참고): " + ban.joinToString(", ") + ".")
        else m.append(" 이 폰은 받아쓰기가 안 되어 말한 낱말은 살피지 못했습니다.")
        return m.append(" 녹음도 남겨 두었습니다.").toString()
    }
}

/** 메모를 담을 칸 — 웹 memoWhere 와 같은 넷 */
object MemoKan {
    val mok = listOf("access" to "이동 구간 안내", "touch" to "만져 볼 것", "safe" to "조심할 것", "text" to "현장영상해설 밑글")
    fun ireum(k: String) = mok.firstOrNull { it.first == k }?.second ?: k
}
