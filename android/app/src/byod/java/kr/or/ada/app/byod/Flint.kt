// BYOD 방송 — 플린트 2 방식 (1.2.0판, 빌드 261014-B9, 이사장님 승인 「1」 — 방송 서버를 공유기 안에 둠, 방송클)
// 1.3.0 (261014-B10) 소리를 보낼 때 행사 이름·현장영상해설사·이 기기 시각을 함께 보냄(공유기가 행사 녹음과 기록을 남김)
// 플린트 2(GL-MT6000) 공유기 안에서 방송 서버(byod_flint.py)가 돌고, 이 앱은 해설 소리 한 줄기만 공유기로 보냅니다.
//   소리 보내기   192.168.8.2:7881  첫 줄 「BYOD 열쇠 이름」 뒤로 뮤 8비트 1초에 16000
//   상태 묻기     192.168.8.2:7880/status, /yocheong_mokrok
// 듣는 분 수백 명에게 나눠 보내는 일은 공유기가 하므로, 방송 기기는 와이파이로 붙어도 됩니다(랜선·허브 없이).
// 공유기 와이파이에는 인터넷이 없어서, 안드로이드는 인터넷이 되는 모바일 데이터 쪽으로 보내려 함 — 그래서 공유기 쪽 망을 찾아 그 망으로만 보냅니다.
package kr.or.ada.app.byod

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

object Flint {
    const val IP = "192.168.8.2"          // 듣기 화면과 방송 서버 주소(공유기에 덧붙인 주소)
    const val GWANRI = "192.168.8.1"      // 공유기 관리 주소(준비하기 때 씀)
    const val WEB = 7880
    const val SORI = 7881
    const val SSID = "현장영상해설방송"
    const val JUSO = "http://$IP/"

    @Volatile var seobeoPan = ""          // 공유기 안 방송 서버 판
    @Volatile var mp3 = false             // 화면을 잠가도 이어지는 소리 길이 있는가
    @Volatile var ttuigi = ""             // 듣기 화면 저절로 뜨기 방식
    @Volatile var dapTtae = 0L            // 공유기가 마지막으로 답한 때
    @Volatile var bonaenda = false        // 소리를 공유기로 보내고 있는가

    private fun pref(ctx: Context) = ctx.getSharedPreferences("byod", Context.MODE_PRIVATE)

    /** 방송 방식: flint(플린트 2 공유기 안 서버) 또는 taebeullit(아이피타임 방식 — 이 기기가 서버, 아이피타임 공유기에 랜선으로 이음)
     *  1.7.0 (261021-B15, 이사장님 승인) 처음값을 아이피타임으로 — 플린트 2가 오기 전이라 지금 가진 아이피타임 A1004NS 로 방송 */
    fun bangsik(ctx: Context): String = pref(ctx).getString("bangsik", "taebeullit") ?: "taebeullit"
    fun bangsikBakkugi(ctx: Context, b: String) { pref(ctx).edit().putString("bangsik", b).apply() }
    fun flintInga(ctx: Context) = bangsik(ctx) == "flint"
    /** 1.7.0 공유기 이름(첫 화면·도움말·현장 점검에 씀) */
    fun gongyugiIreum(ctx: Context) = if (flintInga(ctx)) "플린트 2" else "아이피타임"
    fun yeolsoe(ctx: Context): String = pref(ctx).getString("flint_yeolsoe", "") ?: ""

    /** 공유기 쪽 망 — 주소가 192.168.8.0~11.255 인 망(와이파이든 랜이든) */
    fun mang(ctx: Context): Network? {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            @Suppress("DEPRECATION")
            for (n in cm.allNetworks) {
                val lp = cm.getLinkProperties(n) ?: continue
                for (la in lp.linkAddresses) {
                    val a = la.address.hostAddress ?: continue
                    if (a.startsWith("192.168.8.") || a.startsWith("192.168.9.") || a.startsWith("192.168.10.") || a.startsWith("192.168.11.")) return n
                }
            }
        } catch (_: Exception) { }
        return null
    }

    private fun yeonggyeol(ctx: Context, host: String, port: Int, ttae: Int): Socket {
        val n = mang(ctx)
        val s = n?.socketFactory?.createSocket() ?: Socket()
        s.connect(InetSocketAddress(host, port), ttae)
        return s
    }

    /** 공유기 안 서버에 묻기 — 공유기 쪽 망으로만 */
    fun mutgi(ctx: Context, gil: String, ttae: Int = 3000): String? = try {
        val n = mang(ctx)
        val u = URL("http://$IP:$WEB$gil")
        val c = (n?.openConnection(u) ?: u.openConnection()) as HttpURLConnection
        c.connectTimeout = ttae; c.readTimeout = ttae; c.useCaches = false
        val t = if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null
        c.disconnect(); t
    } catch (_: Exception) { null }

    /** 해설 소리를 공유기로 보내는 일꾼 — 끊기면 2초마다 다시 붙음 */
    class Bonaegi(private val ctx: Context) {
        private val jul = ArrayBlockingQueue<ByteArray>(50)   // 5초까지 쌓아 둠
        @Volatile private var dolgo = false
        private var il: Thread? = null
        private var salpim: Thread? = null
        private var sk: Socket? = null

        fun neoki(b: ByteArray) { if (!jul.offer(b)) { jul.poll(); jul.offer(b) } }

        fun sijak() {
            dolgo = true
            il = Thread({ dolgi() }, "byod-flint-bonae").apply { isDaemon = true; start() }
            salpim = Thread({ salpigi() }, "byod-flint-salpim").apply { isDaemon = true; start() }
        }

        fun meomchugi() {
            dolgo = false
            try { sk?.close() } catch (_: Exception) { }
            il?.interrupt(); salpim?.interrupt()
            bonaenda = false
        }

        private fun dolgi() {
            var allyeotda = ""
            while (dolgo) {
                try {
                    val s = yeonggyeol(ctx, IP, SORI, 4000)
                    s.tcpNoDelay = true
                    s.soTimeout = 5000
                    sk = s
                    val o: OutputStream = s.getOutputStream()
                    val i: InputStream = s.getInputStream()
                    val ireum = (Build.MODEL ?: "방송기기").replace(" ", "")
                    // 1.3.0 행사 이름·현장영상해설사·이 기기 시각을 함께 보냄(공유기는 현장에서 시계를 맞출 길이 없음)
                    val p = ctx.getSharedPreferences("byod", Context.MODE_PRIVATE)
                    fun kan(k: String) = (p.getString(k, "") ?: "").replace("\t", " ").replace("\n", " ").trim()
                    o.write(("BYOD " + yeolsoe(ctx).ifEmpty { "-" } + " " + ireum + "\t" + kan("haengsa_jemok") + "\t" + kan("haeseolsa") + "\t" +
                        (System.currentTimeMillis() / 1000.0) + "\n").toByteArray(Charsets.UTF_8)); o.flush()
                    val dap = StringBuilder()
                    while (true) { val ch = i.read(); if (ch < 0 || ch == '\n'.code) break; dap.append(ch.toChar()) }
                    if (!dap.startsWith("OK")) throw IllegalStateException("열쇠")
                    jul.clear()
                    bonaenda = true
                    if (Bang.jalmot.startsWith("공유기")) Bang.jalmot = ""
                    allyeotda = ""
                    Bang.allyeo()
                    while (dolgo) {
                        val b = jul.poll(2, TimeUnit.SECONDS) ?: continue
                        o.write(b)
                        o.flush()
                    }
                } catch (_: InterruptedException) {
                } catch (e: Exception) {
                    bonaenda = false
                    val m = if (mang(ctx) == null) "공유기: 플린트 2 와이파이(" + SSID + ")나 랜선에 붙지 않았습니다. 이 기기를 공유기에 붙이십시오."
                            else if (e.message == "열쇠") "공유기: 방송 서버가 이 기기를 받지 않습니다. 더 보기의 플린트 2 준비하기를 다시 하십시오."
                            else "공유기: 방송 서버에 닿지 않습니다. 공유기 전원을 확인하고, 처음 쓰는 공유기면 더 보기의 플린트 2 준비하기를 하십시오."
                    if (m != allyeotda) { allyeotda = m; Bang.jalmot = m; Bang.allyeo() }
                } finally {
                    try { sk?.close() } catch (_: Exception) { }
                }
                if (dolgo) try { Thread.sleep(2000) } catch (_: InterruptedException) { }
            }
        }

        /** 3초마다 듣는 분 수·도움 요청을 공유기에 물음 */
        private fun salpigi() {
            var yseq = -1
            while (dolgo) {
                val t = mutgi(ctx, "/status")
                if (t != null) {
                    try {
                        val j = org.json.JSONObject(t)
                        dapTtae = System.currentTimeMillis()
                        seobeoPan = j.optString("pan"); mp3 = j.optBoolean("mp3"); ttuigi = j.optString("ttuigi")
                        val su = j.optInt("n")
                        if (su != Bang.deutnunSu) { Bang.deutnunSu = su; Bang.allyeo() }
                        val ys = j.optInt("yseq")
                        if (yseq < 0) yseq = ys
                        else if (ys != yseq) {
                            val m = mutgi(ctx, "/yocheong_mokrok")
                            if (m != null) {
                                val l = org.json.JSONObject(m).optJSONArray("mokrok")
                                val sae = ys - yseq
                                if (l != null) for (k in maxOf(0, l.length() - sae) until l.length()) {
                                    val y = l.getJSONObject(k)
                                    Bang.yocheong.add(Bang.Yocheong(y.optLong("t") * 1000, y.optString("n"), y.optString("k")))
                                }
                                while (Bang.yocheong.size > 200) Bang.yocheong.removeAt(0)
                                Bang.yocheongSeq++
                                Bang.allyeo()
                            }
                            yseq = ys
                        }
                    } catch (_: Exception) { }
                }
                try { Thread.sleep(3000) } catch (_: InterruptedException) { return }
            }
        }
    }
}
