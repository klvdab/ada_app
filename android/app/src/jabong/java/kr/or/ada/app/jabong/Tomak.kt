// 안드로이드 자봉 — 표시마다 짧게 말로 남기기(목소리 토막) 2.10.0 (빌드 261007-A1, 아이폰 JabongTomak.swift 와 같음)
// 표시를 남기고 그 안내 말이 끝나면 짧게 귀를 엶. 말이 멈추면(1.5초 조용) 끊고, 길어도 10초. 4초 안에 말이 없으면 토막 없음.
// 토막은 녹음한 시간이 아니라 그 표시의 걸음 자리(st)에 묶임. 폰에는 m4a(AAC), 올릴 때 서버가 받아쓰기·mp3 바꾸기·견주기.
package kr.or.ada.app.jabong

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import kr.or.ada.app.gilnun.Girok
import org.json.JSONObject
import java.io.File

object Tomak {
    private const val CHOEDAE = 10_000L   // 길어도 10초
    private const val JOYONG = 1_500L     // 이만큼 조용하면 말이 멈춘 것
    private const val GIDARIM = 4_000L    // 이 안에 말이 없으면 토막 없음
    private const val MALSORI = 1800      // 이보다 크면 말소리(최대 진폭)

    private val main = Handler(Looper.getMainLooper())
    private var rec: MediaRecorder? = null
    private var pail: File? = null
    private var sijak = 0L
    private var malSijak = 0L
    private var majimakSori = 0L
    private var jun: JSONObject? = null
    private var kkeut: ((JSONObject?) -> Unit)? = null
    private var salpim: Runnable? = null
    var nokeumJung = false; private set

    /** 켜고 끄기 — 기본은 켬(교육 약속 일곱) */
    fun kyeojim(c: Context): Boolean = c.getSharedPreferences("jabong", Context.MODE_PRIVATE).getBoolean("jb.tomak", true)
    fun kyeogi(c: Context, v: Boolean) { c.getSharedPreferences("jabong", Context.MODE_PRIVATE).edit().putBoolean("jb.tomak", v).apply(); if (!v) dakgi() }

    fun pyeolDae(c: Context): File = File(c.filesDir, "tomak").apply { mkdirs() }

    /** 짧게 귀를 엶 — 끝나면 토막(없으면 null)을 돌려줌 */
    fun yeolgi(c: Context, st: Int, pyosi: String, gilId: String, t: Int, f: (JSONObject?) -> Unit) {
        if (!kyeojim(c) || nokeumJung) { f(null); return }
        if (c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { f(null); return }
        val ireum = "${gilId}_${st}_${(System.currentTimeMillis() / 1000) % 100000}.m4a"
        val u = File(pyeolDae(c), ireum)
        try {
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(c) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(22050)
            r.setAudioChannels(1)
            r.setAudioEncodingBitRate(48000)
            r.setOutputFile(u.absolutePath)
            r.prepare()
            r.start()
            rec = r
        } catch (e: Exception) {
            Girok.namgi("jb_tomak_oryu", mapOf("e" to (e.message ?: "").take(60)))
            try { rec?.release() } catch (_: Exception) {}
            rec = null; u.delete(); f(null); return
        }
        pail = u
        nokeumJung = true
        kkeut = f
        jun = JSONObject().put("st", st).put("pyosi", pyosi).put("pail", ireum).put("t", t)
        sijak = System.currentTimeMillis(); malSijak = 0L; majimakSori = sijak
        val s = object : Runnable { override fun run() { salpigi(); if (nokeumJung) main.postDelayed(this, 100) } }
        salpim = s
        main.postDelayed(s, 100)
    }

    private fun salpigi() {
        val r = rec ?: return
        val now = System.currentTimeMillis()
        val a = try { r.maxAmplitude } catch (_: Exception) { 0 }
        if (a > MALSORI) { if (malSijak == 0L) malSijak = now; majimakSori = now }
        when {
            malSijak == 0L && now - sijak >= GIDARIM -> matchigi(true)
            malSijak != 0L && now - majimakSori >= JOYONG -> matchigi(false)
            now - sijak >= CHOEDAE -> matchigi(false)
        }
    }

    /** 걷기를 멈추거나 다른 표시·말로 표시를 하면 바로 닫음 */
    fun dakgi() { if (nokeumJung) matchigi(malSijak == 0L) }

    private fun matchigi(beorim: Boolean) {
        salpim?.let { main.removeCallbacks(it) }; salpim = null
        val cho = (System.currentTimeMillis() - sijak) / 1000.0
        try { rec?.stop() } catch (_: Exception) {}
        try { rec?.release() } catch (_: Exception) {}
        rec = null
        nokeumJung = false
        val f = kkeut; kkeut = null
        val j = jun; jun = null
        val u = pail; pail = null
        if (j == null || beorim || cho < 0.8) { u?.delete(); f?.invoke(null); return }
        j.put("cho", Math.round(minOf(cho, CHOEDAE / 1000.0) * 10) / 10.0)
        f?.invoke(j)
    }

    /** 길을 지우면 그 길의 토막도 지움 */
    fun gilJiugi(c: Context, gilId: String) {
        pyeolDae(c).listFiles()?.filter { it.name.startsWith(gilId + "_") }?.forEach { it.delete() }
    }
}
