// BYOD 방송 — 행사 녹음과 결과보고 받아 올리기 (1.3.0판, 빌드 261014-B10, 이사장님 승인 「1」, 방송클)
// 플린트 2 공유기가 행사 녹음(1분 조각)과 기록을 남깁니다. 방송을 멈추면 이 앱이
//   1. 공유기에 행사 마감을 알리고(/haengsa_kkeut)
//   2. 녹음·결과보고·기록을 공유기 와이파이로 받아 이 기기에 담고(지난 행사 기록에서 들음)
//   3. 모바일 데이터로 배프 다시듣기(나스 byod_olligi.php)에 올립니다.
// 모바일 데이터가 안 되면 담아 둔 채 1분마다 다시 올려 봅니다. 공유기도 인터넷이 생기면 스스로 올립니다(둘 중 먼저 된 쪽).
package kr.or.ada.app.byod

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object Haengsa {
    const val NAS = "https://lvd.ada.or.kr/bfblive/byod_olligi.php"
    const val YEOLSOE = "byod-261014-k7m2"     // 나스 byod_olligi.php·공유기 서버와 같은 열쇠
    @Volatile var dolgo = false
    @Volatile var majimakMal = ""
    private var mangCb: ConnectivityManager.NetworkCallback? = null
    var allim: ((String) -> Unit)? = null      // 화면에 알릴 곳

    fun teo(ctx: Context): File = File(ctx.filesDir, "byod_haengsa").also { it.mkdirs() }

    data class Jul(val sid: String, val jemok: String, val haeseolsa: String, val sijak: Long, val gilee: Double, val olim: Boolean)

    /** 이 기기에 담아 둔 행사(새것 먼저) */
    fun mokrok(ctx: Context): List<Jul> = (teo(ctx).listFiles() ?: emptyArray()).filter { File(it, "girok.json").isFile }
        .mapNotNull { d ->
            try {
                val g = org.json.JSONObject(File(d, "girok.json").readText())
                Jul(d.name, g.optString("jemok"), g.optString("haeseolsa"), g.optDouble("sijak", 0.0).toLong(), g.optDouble("gilee", 0.0), File(d, "olim").isFile)
            } catch (_: Exception) { null }
        }.sortedByDescending { it.sid }

    fun bogo(ctx: Context, sid: String): String = try {
        String(File(File(teo(ctx), sid), "bogo.txt").readBytes(), charset("EUC-KR")).replace("\r\n", "\n")
    } catch (_: Exception) { "결과보고가 아직 없습니다." }

    private fun mal(t: String) { majimakMal = t; allim?.invoke(t) }

    /** 공유기 쪽에서 받기 — 공유기 망으로만 */
    private fun batgi(ctx: Context, gil: String): ByteArray? = try {
        val n = Flint.mang(ctx)
        val u = URL("http://${Flint.IP}:${Flint.WEB}$gil")
        val c = (n?.openConnection(u) ?: u.openConnection()) as HttpURLConnection
        c.connectTimeout = 4000; c.readTimeout = 60000; c.useCaches = false
        val b = if (c.responseCode == 200) c.inputStream.readBytes() else null
        c.disconnect(); b
    } catch (_: Exception) { null }

    /** 인터넷이 되는 망 — 모바일 데이터를 먼저 청하고(공유기 와이파이에는 인터넷이 없음), 안 되면 기본 망 */
    private fun inteonet(ctx: Context): Network? {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        var jabeun: Network? = null
        val l = CountDownLatch(1)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { jabeun = network; l.countDown() }
        }
        try {
            cm.requestNetwork(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), cb)
            l.await(8, TimeUnit.SECONDS)
        } catch (_: Exception) { }
        if (jabeun == null) try { cm.unregisterNetworkCallback(cb) } catch (_: Exception) { } else mangCb = cb
        return jabeun
    }

    /** 나스로 4메가씩 나눠 올림 */
    private fun nasBonaegi(n: Network?, sid: String, jong: String, b: ByteArray, deo: String = "") {   // 1.9.0 deo — pail 의 &nm=이름
        val q0 = "$NAS?k=$YEOLSOE&sid=$sid&jong=$jong"
        fun bonae(q: String, mom: ByteArray) {
            val u = URL(q)
            val c = (n?.openConnection(u) ?: u.openConnection()) as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 120000; c.requestMethod = "POST"; c.doOutput = true
            c.setFixedLengthStreamingMode(mom.size)
            c.outputStream.use { it.write(mom) }
            val t = c.inputStream.bufferedReader().readText()
            c.disconnect()
            if (!t.contains("\"ok\":true")) throw IllegalStateException(t)
        }
        val K = 4 * 1024 * 1024
        var su = 0
        var i = 0
        while (i < maxOf(1, b.size)) { bonae("$q0&step=jogak&n=$su", b.copyOfRange(i, minOf(b.size, i + K))); su++; i += K }
        bonae("$q0&step=kkeut&su=$su&gil=${b.size}$deo", ByteArray(0))
    }

    /** 1.9.0 (261024-B17) 갈래 2·3 해설 녹음(gal2_*.ul 을 이름 차례로 이어 하나로)과 현장 기록 파일(csv 등)도 나스로 — 녹음은 무조건 나스에.
     *  아이폰·플린트 2와 같은 길: byod_olligi.php 1.5.0 의 jong=gal2|gal3, jong=pail&nm=이름. 못 올리면 예외(다음에 다시) */
    private fun galPailOlligi(n: Network?, sid: String, d: File) {
        val fs = (d.listFiles() ?: emptyArray()).filter { it.isFile }.sortedBy { it.name }
        for (g in listOf("gal2", "gal3")) {
            val jogak = fs.filter { it.name.startsWith(g + "_") && it.name.endsWith(".ul") }
            if (jogak.isEmpty()) continue
            val bo = java.io.ByteArrayOutputStream()
            for (j in jogak) bo.write(j.readBytes())
            if (bo.size() > 0) nasBonaegi(n, sid, g, bo.toByteArray())
        }
        for (f in fs) {
            val nm = f.name
            if (!(nm.endsWith(".csv") || nm == "bogoseo.txt" || nm == "sangtae.json") || !Regex("^[A-Za-z0-9_.\\-]{1,80}$").matches(nm) || f.length() == 0L) continue
            nasBonaegi(n, sid, "pail", f.readBytes(), "&nm=" + nm)
        }
    }

    /** 1.7.0 뮤 녹음(nokeum.ul)을 이 기기에서 들을 수 있게 웨이브 머리를 붙인 사본(임시 폴더) — 원본은 그대로 둠 */
    fun deutgiWav(ctx: Context, sid: String): File? {
        val ul = File(File(teo(ctx), sid), "nokeum.ul")
        if (!ul.isFile) return null
        val w = File(ctx.cacheDir, "deutgi_$sid.wav")
        if (w.isFile && w.length() == ul.length() + 44) return w
        return try {
            val n = ul.length().toInt()
            val h = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray()); h.putInt(36 + n); h.put("WAVE".toByteArray())
            h.put("fmt ".toByteArray()); h.putInt(16); h.putShort(7); h.putShort(1)   // 7 = 뮤(mu-law), 한 줄
            h.putInt(16000); h.putInt(16000); h.putShort(1); h.putShort(8)
            h.put("data".toByteArray()); h.putInt(n)
            java.io.FileOutputStream(w).use { o -> o.write(h.array()); ul.inputStream().use { it.copyTo(o, 65536) } }
            w
        } catch (_: Exception) { null }
    }

    /** 아직 배프에 올리지 못한 것이 있는가 */
    fun nameum(ctx: Context): Boolean = mokrok(ctx).any { !it.olim }

    /** 방송을 멈춘 뒤 부름 — 마감·받기·올리기를 뒤에서 */
    fun sijak(ctx: Context, maegam: Boolean) {
        if (dolgo) return
        dolgo = true
        val app = ctx.applicationContext
        Thread({
            try { dolgi(app, maegam) } catch (_: Exception) { } finally { dolgo = false }
        }, "byod-haengsa").start()
    }

    private fun dolgi(ctx: Context, maegam: Boolean) {
        GigiNokeum.namunGeotMaegam(ctx)   // 1.7.0 방송 중 꺼진 아이피타임 방식 녹음을 먼저 마감
        // 1. 공유기에 붙어 있으면 마감하고 받기
        if (Flint.mang(ctx) != null) {
            if (maegam) batgi(ctx, "/haengsa_kkeut?k=" + Flint.yeolsoe(ctx))
            val m = batgi(ctx, "/haengsa_mokrok")
            if (m != null) {
                val l = org.json.JSONObject(String(m, Charsets.UTF_8)).optJSONArray("mokrok")
                if (l != null) for (k in 0 until l.length()) {
                    val j = l.getJSONObject(k)
                    val sid = j.optString("sid")
                    if (j.optString("sangtae") != "kkeut" || !sid.matches(Regex("byod[0-9]{12}"))) continue
                    val d = File(teo(ctx), sid)
                    if (File(d, "girok.json").isFile) { if (j.optInt("olim") == 0 && File(d, "olim").isFile) batgi(ctx, "/haengsa_olim?sid=$sid"); continue }
                    d.mkdirs()
                    val ext = if (j.optString("hyeongsik") == "mp3") "mp3" else "ul"
                    val nok = batgi(ctx, "/haengsa/$sid/nokeum.$ext") ?: continue
                    val bogo = batgi(ctx, "/haengsa/$sid/bogo.txt") ?: continue
                    val gir = batgi(ctx, "/haengsa/$sid/girok.json") ?: continue
                    File(d, "nokeum.$ext").writeBytes(nok); File(d, "bogo.txt").writeBytes(bogo)
                    File(d, "girok.json").writeBytes(gir)   // 마지막에 써서, 다 받은 것만 목록에 나오게
                    if (j.optInt("olim") == 1) File(d, "olim").writeText("1")
                    mal(j.optString("jemok") + " 녹음과 결과보고를 이 기기에 담았습니다. 더 보기의 지난 행사 기록에서 들으실 수 있습니다.")
                }
            }
        }
        // 2. 아직 올리지 않은 것을 배프에 올리기
        val an = mokrok(ctx).filter { !it.olim }
        val yeong = an.isNotEmpty() || MudaeNokhwa.nameum(ctx)   // 1.9.0 (261024-B17) 무대 영상 녹화 — 올릴 영상이 있으면 그것도
        if (an.isEmpty() && GamchumDaegi.mokrok(ctx).isEmpty() && !yeong) return   // 1.5.0 감출 인터넷 녹음이 있으면 그것도
        val n = inteonet(ctx)
        try {
            if (an.isNotEmpty()) olligi(ctx, an, n); gamchum(ctx, n)
            if (yeong) try { yeongsangOlligi(ctx, n) } catch (_: Exception) { }   // 1.9.0 소리 다음에. 영상이 안 되어도 소리 올리기는 막지 않음
        } finally {
            mangCb?.let { cb -> try { (ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(cb) } catch (_: Exception) { } }
            mangCb = null
        }
    }

    /** 1.5.0 공유기 녹음이 다시 듣기에 올라갔으면, 같은 때 인터넷으로 나간 녹음은 목록에서 감춤(지우지 않음) */
    private fun gamchum(ctx: Context, n: Network?) {
        val olin = mokrok(ctx).filter { it.olim }
        for ((s, ttae) in GamchumDaegi.mokrok(ctx)) {
            // 인터넷 방을 연 때가 올라간 공유기 녹음의 시간 안(앞뒤 2분 여유)에 들 때만
            if (olin.none { ttae - it.sijak > -120 && ttae - it.sijak < it.gilee + 120 }) continue
            try {
                val u = URL("$NAS?k=$YEOLSOE&step=gamchum&inteonet=$s")
                val c = (n?.openConnection(u) ?: u.openConnection()) as HttpURLConnection
                c.connectTimeout = 10000; c.readTimeout = 20000
                val t = c.inputStream.bufferedReader().readText()
                c.disconnect()
                if (t.contains("\"ok\":true")) GamchumDaegi.ppaegi(ctx, s) else break
            } catch (_: Exception) { break }
        }
    }

    private fun olligi(ctx: Context, an: List<Jul>, n: Network?) {
        for (h in an) {
            val d = File(teo(ctx), h.sid)
            try {
                val nok = File(d, "nokeum.mp3").takeIf { it.isFile } ?: File(d, "nokeum.ul")
                nasBonaegi(n, h.sid, if (nok.name.endsWith("mp3")) "mp3" else "ul", nok.readBytes())
                nasBonaegi(n, h.sid, "bogo", File(d, "bogo.txt").readBytes())
                galPailOlligi(n, h.sid, d)   // 1.9.0 갈래 2·3 녹음과 현장 기록 파일
                nasBonaegi(n, h.sid, "girok", yeongsangSuNeoki(ctx, h, File(d, "girok.json").readBytes()))   // 1.9.0 무대 영상 조각 수를 함께
                File(d, "olim").writeText("1")
                if (Flint.mang(ctx) != null) batgi(ctx, "/haengsa_olim?sid=" + h.sid)
                mal(h.jemok + " 녹음과 결과보고를 LVD 방송 다시 듣기에 올렸습니다.")
            } catch (_: Exception) {
                mal("LVD 방송 다시 듣기에 아직 올리지 못했습니다. 인터넷이 되면 저절로 다시 올립니다.")
                break
            }
        }
    }

    // ───── 1.9.0 (261024-B17) 무대 영상 녹화 — 영상 조각 올리기 ─────
    // 소리(행사 녹음)를 다시 듣기에 올린 행사의 영상 조각(v0001.mp4 …)을 같은 길(byod_olligi.php, 같은 열쇠, 같은 망)로 뒤이어 올림.
    // 아이폰 LVD 송출(261024-BI11)과 같은 요청이라 나스는 하나로 받음(/home/claude/gochim/byod_olligi_yeongsang_SPEC.txt):
    //   조각마다: step=yeongsang&n=조각번호&ext=mp4&sijak=조각 시작 밀리초&kkeut=조각 끝 밀리초&gil=바이트 (몸통 = 조각 파일 통째로, 파일에서 흘려 보냄)
    //   다 올리면: step=yeongsang_girok (몸통 = yeongsang.json) — 나스가 이것을 받으면 소리와 합침
    //   모든 요청에 sid_ap=이 기기의 녹화 폴더 이름(y년월일시분초)
    //   올린 조각은 폴더에 olim_v0001.mp4 표를 남겨, 끊겨도 다음에 이어 올림. 다 올리면 폴더에 olim.
    @Volatile private var yeongsangSilpaeAllim = false

    /** 행사 기록(girok.json)에 짝이 되는 무대 영상 조각 수를 적어 보냄(없으면 그대로) */
    private fun yeongsangSuNeoki(ctx: Context, h: Jul, b: ByteArray): ByteArray = try {
        val hs = mokrok(ctx)
        var su = 0
        for (d in MudaeNokhwa.mokrok(ctx)) {
            val y = MudaeNokhwa.girokIlgi(d) ?: continue
            if (MudaeNokhwa.jjak(y, hs)?.sid == h.sid) su += MudaeNokhwa.dadoenSu(y)
        }
        if (su > 0) org.json.JSONObject(String(b, Charsets.UTF_8)).put("yeongsang", su).toString().toByteArray(Charsets.UTF_8) else b
    } catch (_: Exception) { b }

    /** 나스로 한 번에 올림 — 몸통은 파일(f)에서 흘려 보내거나(큰 영상 조각), 바이트(mom) */
    private fun nasPost(n: Network?, q: String, f: File?, mom: ByteArray) {
        val u = URL(q)
        val c = (n?.openConnection(u) ?: u.openConnection()) as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 180000; c.requestMethod = "POST"; c.doOutput = true
        if (f != null) {
            c.setFixedLengthStreamingMode(f.length())
            c.outputStream.use { o -> f.inputStream().use { it.copyTo(o, 65536) } }
        } else {
            c.setFixedLengthStreamingMode(mom.size)
            c.outputStream.use { it.write(mom) }
        }
        val t = c.inputStream.bufferedReader().readText()
        c.disconnect()
        if (!t.contains("\"ok\":true")) throw IllegalStateException(t)
    }

    private fun yeongsangOlligi(ctx: Context, n: Network?) {
        val hs = mokrok(ctx)
        for (d in MudaeNokhwa.mokrok(ctx)) {
            val y = MudaeNokhwa.girokIlgi(d) ?: continue
            val e = MudaeNokhwa.jjak(y, hs)
            val sid = when {
                e != null && e.olim -> e.sid                                     // 짝 행사의 소리가 올라간 뒤에
                e == null && MudaeNokhwa.oraedoem(y) -> "byod" + d.name.removePrefix("y")   // 이틀이 지나도 짝이 없으면 영상만이라도(나스가 sijak 으로 맞춤)
                else -> continue
            }
            if (!sid.matches(Regex("byod[0-9]{12}"))) continue
            val q0 = "$NAS?k=$YEOLSOE&sid=$sid&sid_ap=${d.name}"
            try {
                val l = y.optJSONArray("jogak") ?: org.json.JSONArray()
                var su = 0
                for (i in 0 until l.length()) {
                    val j = l.optJSONObject(i) ?: continue
                    val kkeut = j.optLong("kkeut", 0L)
                    if (kkeut <= 0L) continue                                    // 기기가 꺼져 끝나지 못한 조각은 재생되지 않아 올리지 않음
                    val f = File(d, j.optString("pail"))
                    if (!f.isFile || f.length() <= 0L) continue
                    su++
                    val pyo = File(d, "olim_" + f.name)
                    if (pyo.isFile) continue
                    nasPost(n, "$q0&step=yeongsang&n=${j.optInt("n", su)}&ext=mp4&sijak=${j.optLong("sijak", 0L)}&kkeut=$kkeut&gil=${f.length()}", f, ByteArray(0))
                    pyo.writeText("1")
                }
                y.put("sid", sid); y.put("su", su)
                nasPost(n, "$q0&step=yeongsang_girok&su=$su", null, y.toString().toByteArray(Charsets.UTF_8))
                File(d, "olim").writeText(sid)
                yeongsangSilpaeAllim = false
                mal((e?.jemok ?: y.optString("jemok").ifEmpty { "행사" }) + " 무대 영상을 LVD 방송 다시 듣기에 올렸습니다. 소리는 나스에서 붙입니다.")
            } catch (_: Exception) {
                if (!yeongsangSilpaeAllim) { yeongsangSilpaeAllim = true; mal("무대 영상을 아직 다 올리지 못했습니다. 인터넷이 되면 저절로 이어 올립니다.") }   // 한 번만 알림
                break
            }
        }
    }
}
