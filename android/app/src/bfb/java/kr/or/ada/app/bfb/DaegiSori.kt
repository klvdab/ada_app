// 2.11.0(빌드 261105-L15, 새방클, 이사장님 승인 2026-11-05 「1」) 대기 안내와 대기 음악
// 웹 듣기 화면(deut.html)과 같게 — 행사장에 붙었는데 해설이 아직 없으면 음악을 깔고 60초마다 안내말, 음악이 없으면 안내말을 25초마다.
// 해설이 시작되면 1초 동안 줄여 끕니다. 소리 파일은 방송(플린트 2 또는 송출 폰)의 /daegi.mp3 /ment.mp3 를 씁니다.
// 전화 중, 영상 보기 중, 점검 듣기 중에는 틀지 않습니다. 이 일꾼에 문제가 나도 해설 듣기는 멈추지 않습니다.
package kr.or.ada.app.bfb

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class DaegiSori(private val c: Context, private val dolgo: () -> Boolean, private val teulsu: () -> Boolean) {
    private var eumak: MediaPlayer? = null
    private var ment: MediaPlayer? = null
    private var mentTtae = 0L
    private var daegiJung = false
    private var silpae = 0
    private var batneun = 0L

    private fun swigi(ms: Long) { try { Thread.sleep(ms) } catch (_: InterruptedException) { } }

    /** 방송에서 소리 파일을 받아 둠. 이번 듣기에서 한 번만 새로 받음 */
    private fun pail(ireum: String, sae: Boolean): File? {
        val f = File(c.cacheDir, ireum)
        if (!sae && f.length() > 0) return f
        val n = Hyeonjang.mang(c) ?: return if (f.length() > 0) f else null
        return try {
            val k = n.openConnection(URL(Hyeonjang.jari + "/" + ireum)) as HttpURLConnection
            k.connectTimeout = 3000; k.readTimeout = 8000; k.useCaches = false
            if (k.responseCode == 200) {
                val t = File(c.cacheDir, "$ireum.bat")
                k.inputStream.use { i -> t.outputStream().use { o -> i.copyTo(o) } }
                if (t.length() > 0) t.renameTo(f)
            }
            k.disconnect()
            if (f.length() > 0) f else null
        } catch (_: Exception) { if (f.length() > 0) f else null }
    }

    private fun mandeul(f: File, loop: Boolean, keugi: Float): MediaPlayer? = try {
        MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            setDataSource(f.path)
            isLooping = loop
            setVolume(keugi, keugi)
            prepare()
            start()
        }
    } catch (_: Exception) { null }

    private fun mentHagi(f: File?) {
        if (f == null) return
        try { ment?.release() } catch (_: Exception) { }
        ment = mandeul(f, false, 1f)
        mentTtae = System.currentTimeMillis()
    }

    private fun kkeugi(cheoncheon: Boolean) {
        val e = eumak
        if (e != null && cheoncheon) for (i in 9 downTo 1) {
            try { val v = 0.45f * i / 10f; e.setVolume(v, v) } catch (_: Exception) { }
            swigi(100)
        }
        try { e?.stop() } catch (_: Exception) { }
        try { e?.release() } catch (_: Exception) { }
        try { ment?.stop() } catch (_: Exception) { }
        try { ment?.release() } catch (_: Exception) { }
        eumak = null; ment = null; daegiJung = false; mentTtae = 0L
    }

    /** 2.11.0 해설 고르기와 만족도 물음을 화면에 알림 */
    private fun sangtaeBoda(j: JSONObject) {
        var bakkwim = false
        val l = j.optJSONArray("gal")
        if (l != null) {
            val m = ArrayList<Deut.Gal>()
            for (i in 0 until l.length()) { val o = l.optJSONObject(i) ?: continue; m.add(Deut.Gal(o.optInt("n"), o.optString("nm"), o.optBoolean("on"))) }
            if (m != Deut.galMok) { Deut.galMok = m; bakkwim = true }
        }
        val q = j.optString("mj", ""); val sq = j.optInt("mjseq", -1)
        if (q != Deut.mjQ || sq != Deut.mjSeq) { Deut.mjQ = q; Deut.mjSeq = sq; bakkwim = true }
        if (bakkwim) Deut.allyeo()
    }

    fun dolligi() {
        val sijak = System.currentTimeMillis()
        batneun = 0L
        var jyTtae = 0L
        while (dolgo()) {
            try {
                var keu: Boolean? = true
                if (Deut.hyeonjangJung) {
                    val t0 = System.currentTimeMillis()
                    val t = Hyeonjang.mutgi(c, "/status", 1500)
                    val rtt = System.currentTimeMillis() - t0
                    val j = if (t != null) try { JSONObject(t) } catch (_: Exception) { null } else null
                    val k = j?.optBoolean("keu", true)
                    if (j != null) sangtaeBoda(j)
                    keu = if (teulsu()) k else true
                    // 소리 늦음 알리기(피디 화면이 봄) — 웹과 같이 5초에 한 번
                    if (k == true && Deut.deullim && System.currentTimeMillis() - jyTtae >= 5000L) {
                        jyTtae = System.currentTimeMillis()
                        Hyeonjang.mutgi(c, "/jiyeon?i=" + Chucheom.jid(c) + "&d=" + (600L + rtt / 2), 1500)
                    }
                } else if (Deut.galMok.isNotEmpty() || Deut.mjQ.isNotEmpty()) { Deut.galMok = emptyList(); Deut.mjQ = ""; Deut.allyeo() }
                if (keu == null) { if (++silpae >= 4 && daegiJung) kkeugi(false) } else silpae = 0
                if (keu == false) {
                    val mv = Deut.KG[Deut.kgM].toFloat()
                    try { eumak?.setVolume(0.45f * mv, 0.45f * mv); ment?.setVolume(mv, mv) } catch (_: Exception) { }
                    val sae = batneun == 0L
                    if (!daegiJung) {
                        val m = pail("daegi.mp3", sae)
                        val a = pail("ment.mp3", sae)
                        batneun = sijak
                        daegiJung = true
                        if (m != null) eumak = mandeul(m, true, 0.45f)
                        Deut.bakkum("해설을 준비하고 있습니다. 음악과 안내가 나갑니다.", false)
                        mentHagi(a)
                    } else if (System.currentTimeMillis() - mentTtae >= (if (eumak != null) 60_000L else 25_000L)) {
                        mentHagi(pail("ment.mp3", false))
                    }
                } else if (keu == true && daegiJung) {
                    kkeugi(true)
                    if (teulsu()) Deut.bakkum("해설이 시작되었습니다.", true)
                }
            } catch (_: Exception) { }
            swigi(1000)
        }
        kkeugi(false)
    }
}
