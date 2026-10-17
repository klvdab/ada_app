// LVD투어스튜디오 안드로이드 — 1.4.0판 (빌드 261018-T6, 방클, 이사장님 「1」 — 앱만 할 수 있는 것 다음 차례 첫째)
// 말로 대본 쓰기 — 받아쓰기를 바로 대본으로
//   지점 한 화면에서 말하는 대로 글이 되어 고른 칸(대본·이동 구간 안내·만져 볼 것·조심할 것·현장영상해설사 위치) 끝에 붙음
//   「마침표」「쉼표」「물음표」「느낌표」「줄 바꿔」는 부호로, 「열두 시 방향」은 「12시 방향」으로 적음
//   이어폰 단추 한 번 누름을 「대본 받아쓰기」로 고르면 화면을 안 보고도 씀
//   받아쓰는 동안 30초마다 폰에 남겨 두어, 앱이 꺼져도 다음에 넣을 수 있음
// 아이폰 Tour/MalDaebon140.swift 와 같은 잣대.
package kr.or.ada.app.tour

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.TextView
import org.json.JSONObject

object MalDaebon {
    val KAN = listOf("text" to "대본", "access" to "이동 구간 안내", "touch" to "만져 볼 것", "safe" to "조심할 것", "gpos" to "현장영상해설사 위치")
    fun ireum(k: String) = KAN.firstOrNull { it.first == k }?.second ?: "대본"
    private val SU = listOf("열두" to 12, "열하나" to 11, "열한" to 11, "열" to 10, "아홉" to 9, "여덟" to 8, "일곱" to 7, "여섯" to 6,
        "다섯" to 5, "네" to 4, "넷" to 4, "세" to 3, "셋" to 3, "두" to 2, "둘" to 2, "한" to 1, "하나" to 1)
    private val BUHO = listOf("줄 바꿔" to "\n", "줄바꿔" to "\n", "줄 바꾸기" to "\n", "줄바꾸기" to "\n", "줄바꿈" to "\n", "다음 줄" to "\n",
        "마침표" to ".", "쉼표" to ",", "물음표" to "?", "느낌표" to "!")
    /** 말한 글 다듬기 — 부호 말을 부호로, 시계 방향을 숫자로 */
    fun dadeum(t0: String): String {
        var t = t0
        for ((w, n) in SU) t = Regex("(^|[^가-힣])$w ?시 ?(방향|쪽)").replace(t) { m -> m.groupValues[1] + "${n}시 " + m.groupValues[2] }
        t = Regex("(\\d{1,2}) *시 *(방향|쪽)").replace(t, "$1시 $2")
        for ((w, b) in BUHO) t = t.replace(w, b)
        t = Regex(" +([.,?!])").replace(t, "$1")
        t = Regex("([.,?!])([^ \\n.,?!0-9])").replace(t, "$1 $2")
        t = Regex("[ \\t]*\\n[ \\t]*").replace(t, "\n")
        t = Regex(" {2,}").replace(t, " ")
        return t.trim(' ')
    }
    fun jwau(t: String) = Regex("왼쪽|오른쪽|왼편|오른편").containsMatchIn(t)
    fun ipgi(o: String, t: String): String = if (o.isBlank()) t else o + (if (o.endsWith(" ") || o.endsWith("\n")) "" else " ") + t
}

class S140 {
    var jung = false; var dan = ""; var jigeum = ""; var spotId = ""; var heotdoem = 0
    var sr: SpeechRecognizer? = null; var namgiRun: Runnable? = null; var sedae = 0
    val modu: String get() = MalDaebon.dadeum("$dan $jigeum")
}

internal var TourActivity.malKan: String
    get() = sp.getString("malKan", "text") ?: "text"
    set(v) { sp.edit().putString("malKan", v).apply() }
internal val TourActivity.danchuHanbeon: String get() = sp.getString("danchuHanbeon", "memo") ?: "memo"

private fun TourActivity.malJulGo() { body.findViewWithTag<TextView>("malJul")?.text = s14.modu.ifEmpty { "듣는 중입니다. 말씀하십시오." } }

/** 받아쓰기 시작 */
internal fun TourActivity.malSijak(s: Spot, mal: String = "말씀하십시오. 다 하시면 끝 단추를 누르십시오.") {
    if (s14.jung) { malhagi("이미 받아쓰는 중입니다."); return }
    if (Nokeumgi.jung) { malhae("녹음 중입니다. 먼저 끝내 주십시오."); return }
    val k = kos
    if (k == null || k.isGyobon) { malhagi("먼저 내 코스를 열어 주십시오."); return }
    micHeorak {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { malhae("이 폰에서는 받아쓰기를 쓸 수 없습니다. 현장 메모 녹음을 쓰십시오."); return@micHeorak }
        tts?.stop(); deutgiMeomchum()
        s14.dan = ""; s14.jigeum = ""; s14.spotId = s.id; s14.heotdoem = 0
        s14.jung = true
        malTomak()
        val r = object : Runnable { override fun run() { if (!s14.jung) return; malNamgi(); main.postDelayed(this, 30000) } }
        s14.namgiRun = r; main.postDelayed(r, 30000)
        malhagi(mal)
        draw()
    }
}

/** 한 토막 듣기 — 끝나면 이어서 새로 엶 */
private fun TourActivity.malTomak() {
    if (!s14.jung) return
    s14.sedae += 1
    val my = s14.sedae
    val sr = s14.sr ?: SpeechRecognizer.createSpeechRecognizer(this).also { s14.sr = it }
    sr.setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(v: Float) {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(t: Int, p: Bundle?) {}
        override fun onPartialResults(p: Bundle?) {
            if (!s14.jung || my != s14.sedae) return
            p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { s14.jigeum = it; malJulGo() }
        }
        override fun onResults(p: Bundle?) {
            if (!s14.jung || my != s14.sedae) return
            p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { if (it.isNotBlank()) s14.jigeum = it }
            malTomakKkeut()
        }
        override fun onError(e: Int) {
            if (!s14.jung || my != s14.sedae) return
            if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { malKkeut(true, "마이크를 허락하지 않으셔서 받아쓰기를 멈추었습니다."); return }
            malTomakKkeut()
        }
    })
    val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 60000L)
    try { sr.startListening(i) } catch (e: Exception) { main.postDelayed({ malTomakKkeut() }, 500) }
}

private fun TourActivity.malTomakKkeut() {
    if (s14.jigeum.isNotBlank()) { s14.dan = (if (s14.dan.isEmpty()) "" else s14.dan + " ") + s14.jigeum; s14.heotdoem = 0 } else s14.heotdoem += 1
    s14.jigeum = ""
    malJulGo()
    if (s14.heotdoem >= 8) { malKkeut(true, "한동안 알아들은 말이 없어 받아쓰기를 멈추었습니다."); return }
    val my = s14.sedae
    main.postDelayed({ if (s14.jung && my == s14.sedae) malTomak() }, 300)
}

/** 받아쓰던 글을 폰에 남겨 둠 (앱이 꺼져도 다음에 넣게) */
private fun TourActivity.malNamgi() {
    val t = s14.modu.trim(); val k = kos ?: return
    if (t.isEmpty()) return
    sp.edit().putString("malNameun", JSONObject().put("kos", k.id).put("spot", s14.spotId).put("kan", malKan).put("t", t).toString()).apply()
}
private fun TourActivity.malNameun(): JSONObject? = try { sp.getString("malNameun", null)?.let { JSONObject(it) } } catch (e: Exception) { null }

/** 끝 — neoki 면 고른 칸 끝에 붙임 */
internal fun TourActivity.malKkeut(neoki: Boolean, kkalm: String = "") {
    if (!s14.jung) return
    val t = s14.modu.trim()
    s14.jung = false; s14.sedae += 1
    s14.namgiRun?.let { main.removeCallbacks(it) }; s14.namgiRun = null
    try { s14.sr?.cancel(); s14.sr?.destroy() } catch (e: Exception) { }
    s14.sr = null
    s14.dan = ""; s14.jigeum = ""
    sp.edit().remove("malNameun").apply()
    main.postDelayed({ danchuBuchigi() }, 800)
    if (!neoki) { malhae("받아쓰기를 그만두었습니다. 넣지 않았습니다."); draw(); return }
    val k = kos; val s = k?.spot(s14.spotId)
    if (k == null || s == null) { draw(); return }
    if (t.isEmpty()) { malhae((if (kkalm.isEmpty()) "" else "$kkalm ") + "알아들은 말이 없어 넣지 않았습니다."); draw(); return }
    malNeoki(k, s, malKan, t, kkalm)
}

private fun TourActivity.malNeoki(k: Kos, s: Spot, kn: String, t: String, kkalm: String = "") {
    if (kn == "text") k.setSpotText(s, MalDaebon.ipgi(k.spotText(s), t)) else s.d.put(kn, MalDaebon.ipgi(s.s(kn), t))
    var m = (if (kkalm.isEmpty()) "" else "$kkalm ") + k.spotLab(s) + " " + MalDaebon.ireum(kn) + " 칸에 넣었습니다."
    if (MalDaebon.jwau(t)) m += " 왼쪽이나 오른쪽이라는 말이 들어 있습니다. 시계 방향으로 고쳐 주십시오."
    malhae(m)
    allim = "$m 넣은 글: $t"
    bakkwim()
    draw()
}

/** 이어폰 단추 한 번 — 대본 받아쓰기로 고른 경우 */
internal fun TourActivity.danchuMalDaebon(k: Kos, s: Spot) {
    if (s14.jung) { ppi(); malKkeut(true); return }
    if (Nokeumgi.jung) { malhae("녹음 중입니다. 먼저 끝내 주십시오."); return }
    malhae(k.spotLab(s) + " " + MalDaebon.ireum(malKan) + " 칸에 말로 씁니다. 삐 소리 뒤에 말씀하십시오.")
    main.postDelayed({ ppi(); main.postDelayed({ malSijak(s, "받아쓰는 중입니다. 다 말씀하시면 이어폰 단추를 한 번 누르십시오.") }, 400) }, 3000)
}

/** 지점 한 화면에 붙는 1.4.0 묶음 — 말로 대본 쓰기 */
internal fun TourActivity.malDaebon140(k: Kos, s: Spot) {
    geul("말로 대본 쓰기 — 말하는 대로 고른 칸 끝에", jemok = true)
    danchu("넣을 칸 — " + MalDaebon.ireum(malKan) + ". 누르면 고릅니다") {
        android.app.AlertDialog.Builder(this).setTitle("넣을 칸 고르기").setItems(MalDaebon.KAN.map { it.second }.toTypedArray()) { _, i ->
            malKan = MalDaebon.KAN[i].first; malhagi(MalDaebon.KAN[i].second + " 칸에 넣습니다."); draw()
        }.setNegativeButton("그만두기", null).show()
    }
    if (s14.jung && s14.spotId == s.id) {
        geul(s14.modu.ifEmpty { "듣는 중입니다. 말씀하십시오." }).tag = "malJul"
        danchu("끝 — " + MalDaebon.ireum(malKan) + " 칸에 넣기", keun = true) { malKkeut(true) }
        danchu("그만두기 (넣지 않음)") { malKkeut(false) }
    } else if (s14.jung) {
        geul("다른 지점에서 받아쓰는 중입니다.")
        danchu("그 받아쓰기 끝내고 넣기") { malKkeut(true) }
    } else {
        danchu("말로 쓰기 시작", keun = true) { malSijak(s) }
    }
    val n = malNameun()
    if (!s14.jung && n != null && n.optString("kos") == k.id) {
        geul("지난번 받아쓰다 끊긴 글이 있습니다 (" + MalDaebon.ireum(n.optString("kan", "text")) + " 칸): " + n.optString("t"))
        danchu("끊긴 글 넣기") {
            val sp2 = k.spot(n.optString("spot"))
            sp.edit().remove("malNameun").apply()
            if (sp2 != null) malNeoki(k, sp2, n.optString("kan", "text"), n.optString("t")) else { malhagi("그 지점이 없어 넣지 못했습니다."); draw() }
        }
        danchu("끊긴 글 버리기") { sp.edit().remove("malNameun").apply(); malhagi("끊긴 글을 버렸습니다."); draw() }
    }
    geul("말하는 대로 글이 되어 고른 칸 끝에 붙습니다. 「마침표」, 「쉼표」, 「물음표」, 「느낌표」라고 말하면 부호가, 「줄 바꿔」라고 말하면 새 줄이 들어갑니다. 「열두 시 방향」이라고 말하면 「12시 방향」으로 적습니다. 오래 말해도 이어서 받아쓰고, 30초마다 폰에 남겨 두어 앱이 꺼져도 다음에 넣을 수 있습니다. 넣은 글은 바로 위 칸에서 고칠 수 있습니다.", jakge = true)
}
