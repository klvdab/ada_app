// 목소리 따라 걷기 — 자봉 목소리 토막 들려 드리기 (안드로이드 길눈 2.23.0, 빌드 261007-A11, 아이폰 TomakDeutgi.swift 와 같음, 이사장님 확정 방식 2026-10-06)
// 점지도를 따라 걸을 때, 표시에 닿기 몇 걸음 앞에서(길눈 안내 말 바로 뒤) 그 자리에 자봉이 남긴 짧은 말을 그대로 들려 드림.
// 걸음 자리에 묶여 듣는 분의 걸음에 맞춤, 지나친 토막은 건너뜀. 협회 서버가 받아쓰기·mp3 바꾸기를 거친 토막만(나스 jeom/sori.php).
// 설정에서 끌 수 있음(기본 켬).
package kr.or.ada.app.gilnun

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object TomakDeutgi {
    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null

    fun kyeojim(c: Context): Boolean = c.getSharedPreferences("gilnun", Context.MODE_PRIVATE).getBoolean("gn.tomakDeutgi", true)
    fun kyeogi(c: Context, v: Boolean) { c.getSharedPreferences("gilnun", Context.MODE_PRIVATE).edit().putBoolean("gn.tomakDeutgi", v).apply() }

    /** pail 은 sori/<길 번호>/<001>.mp3 */
    fun deutgi(c0: Context?, pail: String) {
        val c = c0 ?: return
        if (!kyeojim(c)) return
        val p = pail.removePrefix("sori/")
        val f = File(c.cacheDir, "tomak_" + p.replace("/", "_"))
        Thread {
            try {
                if (!f.exists() || f.length() < 200) {
                    val u = URL("https://lvd.ada.or.kr/jeom/sori.php?p=" + URLEncoder.encode(p, "UTF-8").replace("%2F", "/"))
                    val h = u.openConnection() as HttpURLConnection
                    h.connectTimeout = 8000; h.readTimeout = 15000
                    if (h.responseCode != 200) { h.disconnect(); return@Thread }
                    // 2.25.0 받다가 끊겨 잘린 파일이 남지 않게 — 임시 파일에 다 받은 뒤 이름을 바꿈
                    val tmp = File(f.path + ".tmp")
                    h.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                    h.disconnect()
                    if (tmp.length() < 200 || !tmp.renameTo(f)) { tmp.delete(); return@Thread }
                }
                main.post { gidaryeoTeulgi(f, 0) }
            } catch (e: Exception) { File(f.path + ".tmp").delete() }
        }.start()
    }

    /** 2.25.0 길눈 안내 말이 끝난 뒤에 틂(아이폰과 같음, 겹치지 않게). 6초 넘게 기다리면 그만 */
    private fun gidaryeoTeulgi(f: File, beon: Int) {
        if (Sori.malhaneunJung && beon < 20) { main.postDelayed({ gidaryeoTeulgi(f, beon + 1) }, 300); return }
        teulgi(f)
    }

    /** 2.30.0 전화가 오거나 걸면 곧바로 멈춤 */
    fun meomchugi() {
        main.post {
            val p = player ?: return@post
            player = null
            try { p.stop() } catch (e: Exception) {}
            try { p.release() } catch (e: Exception) {}
            BangsongDuck.malKkeut()
        }
    }

    private fun teulgi(f: File) {
        if (Sori.malAnham) return   // 2.30.0 긴급통화·전화 중에는 틀지 않음
        try {
            player?.release()
            val m = MediaPlayer()
            m.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            m.setDataSource(f.absolutePath)
            m.setOnCompletionListener { it.release(); if (player === it) player = null; BangsongDuck.malKkeut() }
            m.prepare()
            BangsongDuck.malSijak(MalGeup.ANNAE)   // 방송 소리를 잠시 줄임
            m.start()
            player = m
            Girok.namgi("gn_tomak_deutgi", emptyMap())
        } catch (e: Exception) { }
    }
}
