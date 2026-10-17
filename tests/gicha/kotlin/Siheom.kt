// 여정 다시 돌려 보기 시험 — 안드로이드 길눈 기차 안내(GichaAnnae.kt)를 시나리오 자리 기록에 그대로 돌려 봄
// 쓰는 법: java … kr.or.ada.app.gilnun.SiheomKt 시나리오.json … — 있어야 할 말이 없거나 없어야 할 말이 나오면 실패(나가는 값 1)
package kr.or.ada.app.gilnun

import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    var silpae = 0
    // 2.68.0 철길 자료 — 앱에 싣는 것과 같은 파일
    Cheolgil.ilgeum = { File("Gilnun/cheolgil.bin").takeIf { it.exists() }?.readBytes() }
    if (Cheolgil.shared == null) { println("철길 자료를 못 읽음"); exitProcess(1) }
    val tz = TimeZone.getTimeZone("Asia/Seoul")
    for (pail in args) {
        val s = JSONObject(File(pail).readText(Charsets.UTF_8))
        val ireum = s.optString("ireum", pail)
        Tongsin.jaryo = s
        Sori.malMok.clear()
        val mk = s.getJSONObject("mokjeok")
        val y = Yeojeong(Mokjeok(mk.optString("ireum"), mk.getDouble("lat"), mk.getDouble("lon")))
        YeojeongEngine.jigeum = y
        val bam = SimpleDateFormat("yyyyMMdd", Locale.KOREA).apply { timeZone = tz }.parse(s.optString("nal", "20261017"))!!.time
        var jigeum = bam
        GichaAnnae.sigye = { jigeum }
        GichaAnnae.kkeut()
        val jr = s.getJSONArray("jari")
        for (i in 0 until jr.length()) {
            val x = jr.getJSONObject(i)
            jigeum = bam + (x.getDouble("t") * 1000).toLong()
            GichaAnnae.wichi(Jari(x.getDouble("lat"), x.getDouble("lon"), x.optDouble("acc", 10.0), x.optDouble("bang", -1.0), x.optDouble("sokdo", -1.0), jigeum, false), y)
        }
        GichaAnnae.kkeut()
        val tf = SimpleDateFormat("HH:mm:ss", Locale.KOREA).apply { timeZone = tz }
        println("── $ireum — 말 ${Sori.malMok.size}마디")
        for ((t, m) in Sori.malMok) println("  ${tf.format(java.util.Date(t))}  $m")
        val modu = Sori.malMok.map { it.second }
        val is_ = s.optJSONArray("isseoya")
        if (is_ != null) for (i in 0 until is_.length()) { val p = Regex(is_.getString(i)); if (modu.none { p.containsMatchIn(it) }) { println("  ✗ 있어야 할 말이 없음: ${p.pattern}"); silpae++ } }
        // 차례 — 이 말들이 이 차례대로 나와야 함
        val ch = s.optJSONArray("charye")
        var jari = 0
        if (ch != null) for (i in 0 until ch.length()) {
            val p = Regex(ch.getString(i))
            var k = -1
            for (j in jari until modu.size) if (p.containsMatchIn(modu[j])) { k = j; break }
            if (k >= 0) jari = k + 1 else { println("  ✗ 차례가 어긋남(이 말이 앞 말 뒤에 없음): ${p.pattern}"); silpae++ }
        }
        val eop = s.optJSONArray("eopseoya")
        if (eop != null) for (i in 0 until eop.length()) { val p = Regex(eop.getString(i)); for (m in modu) if (p.containsMatchIn(m)) { println("  ✗ 없어야 할 말이 나옴(${p.pattern}): $m"); silpae++ } }
    }
    println(if (silpae == 0) "통과" else "실패 ${silpae}건")
    exitProcess(if (silpae == 0) 0 else 1)
}
