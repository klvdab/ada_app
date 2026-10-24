// BYOD 방송 — 듣는 분께 소리를 나눠 보내는 작은 서버 (1.0.0판, 빌드 261003-B1, 이사장님 승인 2026-10-03)
// 노트북판(현장 해설방송 3.3판)과 길 이름을 똑같이 맞춰, 노트북판 듣는 화면(deut.html)이 그대로 돕니다.
//   /            듣는 화면(deut.html)
//   /status      방송 상태(keu·n·pan·gin·gseq·dang·dseq)
//   /stream      해설 소리 한 줄기(뮤 8비트, 1초에 16000)
//   /daegi.mp3 /ment.mp3  대기 음악·안내말
//   /beonho /deutnun /jiyeon /yocheong  듣는 분 쪽 알림(도움 요청은 태블릿 화면에 한 번 알림)
// 안드로이드 앱은 80번 문을 열 수 없어 8080번 문을 씁니다.
// 듣는 분 한 사람마다 작은 일꾼이 붙어, 느린 폰 하나가 다른 분들을 붙잡지 않게 합니다(밀리면 오래된 조각을 버림).
// LVD 송출 안드로이드 1.9.0(빌드 261024-B17, 새방송클, 대조표 송출 21·22·23번 — 이사장님 9월 24·27·28일 승인 노트북판 현장 기능 옮기기)
//   노트북판 서버(hyeonjang.cs 3.3판)의 길을 모두 옮김(/naemic·/saepan_batgi·DNS 빼고) — 길 하나하나의 일은 HyeonjangSeobeo.kt.
//   이 파일은 문 열기(8080 그냥 문, 8443 자물쇠 문), 물음 읽기(머리·몸통), 화면 파일(deut·pd·bang), 소리 나눠 보내기(갈래 1·2·3)를 맡음.
//   /pd /hs /gwan → pd.html, /bang → bang.html, /mujeon → pd.html. 폰이 그냥 문으로 /bang·/mujeon 을 열면 자물쇠 문(https)으로 돌림(마이크·카메라가 열리게).
//   /stream?gal=2 처럼 갈래를 골라 듣고, 갈래마다 따로 나눠 보냄. 갈래 1 = 이 기기 사운드카드 해설, 갈래 2·3 = 관계자 폰이 POST /pcm?gal=n 으로 보낸 해설.
package kr.or.ada.app.byod

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket

class Seobeo(private val ctx: Context) {

    // 1.9.0 gal = 듣는 갈래(1·2·3), mang = 폰이 들어온 주소 묶음(앞 세 마디), nae = 이 기기 안에서 붙은 것(듣는 분 수에 안 셈)
    private class Deut(val s: Socket, val out: OutputStream, val gal: Int, val mang: String, val nae: Boolean) {
        val jul = ArrayBlockingQueue<ByteArray>(40)   // 4초까지 쌓아 둠
        @Volatile var jugeum = false
        @Volatile var sseuneunTtae = 0L   // 1.1.5 쓰기를 시작한 때(쓰는 중이 아니면 0)
    }

    private var ss: ServerSocket? = null
    private var tss: ServerSocket? = null      // 1.9.0 자물쇠 문(https)
    @Volatile private var dolgo = false
    private val deutneun = CopyOnWriteArrayList<Deut>()
    @Volatile var tport = 0                    // 1.9.0 자물쇠 문 번호(못 열었으면 0)
    @Volatile var tlsOryu = ""                 // 1.9.0 자물쇠 문을 못 연 까닭

    fun su(): Int = deutneun.count { !it.nae }
    fun galSu(g: Int): Int = deutneun.count { !it.nae && it.gal == g }   // 1.9.0
    /** 1.9.0 공유기별 인원 — 주소 묶음(앞 세 마디)마다 듣는 분 수 */
    fun mangSu(): Map<String, Int> {
        val m = java.util.TreeMap<String, Int>()
        for (d in deutneun) if (!d.nae && d.mang.isNotEmpty()) m[d.mang] = (m[d.mang] ?: 0) + 1
        return m
    }

    companion object {
        const val MAKHIM = 10_000L   // 1.1.5 쓰기가 이만큼(밀리초) 막히면 끊긴 폰으로 봄
        const val TPORT = 8443       // 1.9.0 자물쇠 문(노트북판 443 대신 — 안드로이드 앱은 1024 아래 문을 못 엶)
        private const val AMHO = "lvdhj2026"
        private const val INJEUNG = "hj_lvd_3des.p12"
    }

    fun sijak() {
        if (dolgo) return
        val s = ServerSocket()
        s.reuseAddress = true
        s.bind(InetSocketAddress(Bang.PORT), 512)
        ss = s
        dolgo = true
        Thread({ batneunIl(s, false) }, "byod-mun").start()
        // 1.9.0 자물쇠 문 — 못 열어도 방송은 그대로(무대 카메라·관계자 무전만 못 씀)
        val t = tlsMunYeolgi()
        if (t != null) { tss = t; Thread({ batneunIl(t, true) }, "byod-mun-tls").apply { isDaemon = true }.start() }
        HyeonjangSeobeo.sijak(ctx, this)   // 1.9.0 현장 기능 상태(피디 번호·이어 받기·1분마다 저장)
        // 1.1.5 (261009-B7) 와이파이 밖으로 나가 버린 폰은 쓰기가 막힌 채 남아 "듣는 분"으로 세어졌음 — 10초 넘게 막히면 끊고 빼고 셈
        Thread({
            while (dolgo) {
                try { Thread.sleep(2000) } catch (_: InterruptedException) { }
                val now = System.currentTimeMillis()
                for (d in deutneun) { val tt = d.sseuneunTtae; if (tt > 0 && now - tt > MAKHIM) kkeutnaegi(d) }
            }
        }, "byod-salpim").apply { isDaemon = true }.start()
    }

    // 1.9.0 앱에 넣은 자체 인증서(옛 꼴 PKCS12, 3DES)로 8443(안 되면 9443) 문을 엶
    private fun tlsMunYeolgi(): ServerSocket? {
        try {
            val ks = KeyStore.getInstance("PKCS12")
            ctx.assets.open(INJEUNG).use { ks.load(it, AMHO.toCharArray()) }
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(ks, AMHO.toCharArray())
            val sc = SSLContext.getInstance("TLS")
            sc.init(kmf.keyManagers, null, null)
            val f = sc.serverSocketFactory
            for (p in intArrayOf(TPORT, 9443)) {
                var x: ServerSocket? = null
                try {
                    try {
                        val u = f.createServerSocket()
                        x = u
                        u.reuseAddress = true
                        u.bind(InetSocketAddress(p), 200)
                    } catch (e: Exception) {
                        try { x?.close() } catch (_: Exception) { }
                        x = f.createServerSocket(p, 200)
                    }
                    (x as? SSLServerSocket)?.needClientAuth = false
                    tport = p
                    tlsOryu = ""
                    return x
                } catch (e: Exception) {
                    try { x?.close() } catch (_: Exception) { }
                    tlsOryu = e.message ?: "문을 열지 못함"
                }
            }
        } catch (e: Exception) {
            tlsOryu = e.message ?: "인증서를 읽지 못함"
        }
        tport = 0
        return null
    }

    fun meomchugi() {
        dolgo = false
        try { ss?.close() } catch (_: Exception) { }
        try { tss?.close() } catch (_: Exception) { }
        ss = null
        tss = null
        tport = 0
        for (d in deutneun) kkeutnaegi(d)
        deutneun.clear()
        Bang.deutnunSu = 0
        HyeonjangSeobeo.kkeut()   // 1.9.0 마지막으로 한 번 저장
    }

    // 해설 소리 한 조각을 듣는 분 모두에게 — 1.9.0 이 기기 사운드카드 해설 = 갈래 1
    fun ppurida(b: ByteArray) {
        HyeonjangSeobeo.sorikadeuOm()
        ppuridaGal(1, b)
    }

    // 1.9.0 한 갈래의 듣는 분에게만
    fun ppuridaGal(g: Int, b: ByteArray) {
        for (d in deutneun) {
            if (d.jugeum || d.gal != g) continue
            if (!d.jul.offer(b)) { d.jul.poll(); d.jul.offer(b) }
        }
    }

    private fun batneunIl(s: ServerSocket, tls: Boolean) {
        while (dolgo) {
            val c = try { s.accept() } catch (_: Exception) { if (!dolgo) return else { try { Thread.sleep(50) } catch (_: InterruptedException) { }; continue } }
            val t = Thread(null, { mutgi(c, tls) }, if (tls) "byod-son-tls" else "byod-son", 512 * 1024)
            t.isDaemon = true
            t.start()
        }
    }

    private fun kkeutnaegi(d: Deut) {
        d.jugeum = true
        try { d.s.close() } catch (_: Exception) { }
        if (deutneun.remove(d)) { Bang.deutnunSu = su(); Bang.allyeo() }
    }

    // 1.9.0 머리를 빈 줄(CRLF 두 번)까지 읽음(64킬로바이트까지) — 글자는 ISO-8859-1 로(주소는 %로 적혀 오므로)
    private fun meoriIlkgi(inp: InputStream): String? {
        val bo = ByteArrayOutputStream(1024)
        var w = 0
        while (bo.size() < 65536) {
            val ch = inp.read()
            if (ch < 0) return null
            bo.write(ch)
            w = (w shl 8) or ch
            if (w == 0x0D0A0D0A || (w and 0xFFFF) == 0x0A0A) return String(bo.toByteArray(), Charsets.ISO_8859_1)
        }
        return null
    }

    private fun gap(q: String, k: String): String = HyeonjangSeobeo.gap(q, k)

    private fun mutgi(c: Socket, tls: Boolean) {
        var keep = false
        try {
            c.soTimeout = 10000
            c.tcpNoDelay = true
            val inp = java.io.BufferedInputStream(c.getInputStream(), 4096)
            val out = c.getOutputStream()
            val head = meoriIlkgi(inp) ?: return
            val lines = head.split("\n").map { it.trimEnd('\r') }
            val first = lines.firstOrNull()?.split(" ") ?: return
            if (first.size < 2) return
            val method = first[0].uppercase()
            val juso = first[1]
            var host = ""; var ua = ""; var clen = 0
            for (i in 1 until lines.size) {
                val l = lines[i]
                val ci = l.indexOf(':')
                if (ci < 0) continue
                val k = l.substring(0, ci).trim().lowercase()
                val v = l.substring(ci + 1).trim()
                when (k) {
                    "host" -> host = v
                    "user-agent" -> ua = String(v.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
                    "content-length" -> clen = v.toIntOrNull() ?: 0
                }
            }
            val qi = juso.indexOf('?')
            val gil = (if (qi >= 0) juso.substring(0, qi) else juso)
            val q = if (qi >= 0) juso.substring(qi + 1) else ""
            val adr = c.inetAddress
            val loop = adr?.isLoopbackAddress == true
            val ponIp = (adr?.hostAddress ?: "").removePrefix("::ffff:").let { if (it.contains(':')) "" else it }

            var body = ByteArray(0)
            if (method == "POST" && clen > 0 && clen < 4_000_000) {
                val b = ByteArray(clen)
                var have = 0
                while (have < clen) { val n = inp.read(b, have, clen - have); if (n <= 0) break; have += n }
                body = if (have == clen) b else b.copyOf(have)
            }

            if (method == "OPTIONS") { bonae(out, 204, "text/plain", ByteArray(0)); return }
            // 1.9.0 다른 누리집 주소로 물어 온 횟수(노트북판 hwakin) — 이 기기는 주소 묻기(DNS)를 하지 않으므로 돌리지 않고 세기만
            run {
                val ho = HyeonjangSeobeo.hostMan(host)
                if (!tls && ho.isNotEmpty() && ho != "localhost" && ho != "127.0.0.1" && !HyeonjangSeobeo.naeJusoInga(ho)) HyeonjangSeobeo.hwakinSegi()
            }
            if (inteonetCheok(out, gil)) return
            when (gil) {
                "/", "/index.html", "/deut.html", "/deut" -> {
                    val f = Jaryo.pail(ctx, "deut.html")
                    if (f != null) pail(out, f, "text/html; charset=utf-8") else geulja(out, 200, "text/html; charset=utf-8", Jaryo.GANDAN)
                }
                "/stream" -> {
                    if (tls) { geulja(out, 404, "text/plain; charset=utf-8", "없는 주소입니다."); return }   // 노트북판과 같이 소리 줄기는 그냥 문으로만
                    val g = gap(q, "gal").toIntOrNull()?.takeIf { it in 1..3 } ?: 1
                    val nae = loop && (gap(q, "nae") == "1" || gap(q, "gajja") == "1")
                    keep = true
                    deutgi(c, out, g, mangMal(ponIp), nae)
                }
                "/daegi.mp3", "/ment.mp3" -> {
                    val f = Jaryo.pail(ctx, gil.substring(1))
                    if (f != null) pail(out, f, "audio/mpeg") else geulja(out, 404, "text/plain; charset=utf-8", "없는 소리입니다.")
                }
                "/pd", "/hs", "/gwan" -> jjokgeul(out, "pd.html")
                "/bang", "/mujeon" -> {
                    // 폰은 자물쇠 주소로 보내야 마이크·카메라가 열림(노트북판과 같음)
                    if (!tls && !loop && tport > 0) {
                        val ip = HyeonjangSeobeo.hostMan(host).takeIf { it.isNotEmpty() && it != "localhost" && !it.startsWith("127.") } ?: HyeonjangSeobeo.ponJuso()
                        dolligi(out, "https://" + ip + ":" + tport + gil + (if (q.isEmpty()) "" else "?$q"))
                    } else jjokgeul(out, if (gil == "/bang") "bang.html" else "pd.html")
                }
                else -> {
                    val d = HyeonjangSeobeo.cheori(Mureum(method, gil, q, host, ua, body, loop, tls, ponIp))
                    if (d.loc.isNotEmpty()) dolligi(out, d.loc) else bonae(out, d.code, d.type, d.mom)
                }
            }
        } catch (_: Exception) {
        } finally {
            if (!keep) try { c.close() } catch (_: Exception) { }
        }
    }

    private fun mangMal(ip: String): String {
        val i = ip.lastIndexOf('.')
        return if (i > 0) ip.substring(0, i) else ""
    }

    // 1.9.0 화면 파일 — 나스에서 받아 담아 둔 것(Jaryo)
    private fun jjokgeul(out: OutputStream, nm: String) {
        val f = Jaryo.pail(ctx, nm)
        if (f != null) pail(out, f, "text/html; charset=utf-8")
        else geulja(out, 404, "text/plain; charset=utf-8", "$nm 파일이 없습니다. 인터넷이 되는 곳에서 LVD 송출 첫 화면의 더 보기, 새로고침을 눌러 받으십시오.")
    }

    // 듣는 분 한 사람 붙이기 — 머리를 보낸 뒤 소리 없음 0.1초를 먼저 보내고, 그 뒤로는 조각이 오는 대로
    private fun deutgi(c: Socket, out: OutputStream, gal: Int, mang: String, nae: Boolean) {
        c.soTimeout = 0
        try { c.sendBufferSize = 65536 } catch (_: Exception) { }
        val h = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nCache-Control: no-store\r\n" +
            "Access-Control-Allow-Origin: *\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n"
        out.write(h.toByteArray(Charsets.US_ASCII))
        out.write(MuLaw.goyo(Sori.JOGAK))
        out.flush()
        val d = Deut(c, out, gal, mang, nae)
        deutneun.add(d)
        Bang.deutnunSu = su()
        HyeonjangSeobeo.galDeutnun(gal, galSu(gal))
        Bang.allyeo()
        val t = Thread(null, {
            try {
                while (dolgo && !d.jugeum) {
                    val b = d.jul.poll(5, TimeUnit.SECONDS)
                    // 5초 넘게 조각이 없으면(해설 쉬는 중) 소리 없음 한 조각을 보내 끊긴 폰을 가려냄
                    d.sseuneunTtae = System.currentTimeMillis()
                    out.write(b ?: MuLaw.goyo(160))
                    out.flush()
                    d.sseuneunTtae = 0L
                }
            } catch (_: Exception) {
            } finally {
                kkeutnaegi(d)
            }
        }, "byod-deut", 256 * 1024)
        t.isDaemon = true
        t.start()
    }

    // 1.9.0 노트북판과 같은 머리(403 Forbidden, Access-Control-Allow-Headers)
    private fun bonae(out: OutputStream, code: Int, type: String, body: ByteArray) {
        val mal = when (code) { 200 -> "OK"; 204 -> "No Content"; 403 -> "Forbidden"; else -> "Not Found" }
        val h = "HTTP/1.1 $code $mal\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Headers: *\r\nConnection: close\r\n\r\n"
        out.write(h.toByteArray(Charsets.US_ASCII))
        if (body.isNotEmpty()) out.write(body)
        out.flush()
    }

    private fun dolligi(out: OutputStream, loc: String) {
        val h = "HTTP/1.1 302 Found\r\nLocation: $loc\r\nCache-Control: no-store\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        out.write(h.toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun geulja(out: OutputStream, code: Int, type: String, t: String) = bonae(out, code, type, t.toByteArray(Charsets.UTF_8))

    private fun pail(out: OutputStream, f: File, type: String) {
        val len = f.length()
        val h = "HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: $len\r\n" +
            "Cache-Control: no-store\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Headers: *\r\nConnection: close\r\n\r\n"
        out.write(h.toByteArray(Charsets.US_ASCII))
        f.inputStream().use { it.copyTo(out, 32768) }
        out.flush()
    }

    // 인터넷 있는 척 — 폰이 인터넷을 확인하는 주소에 "됨"이라고 답해 와이파이가 끊기지 않게(노트북판과 같음)
    private fun inteonetCheok(out: OutputStream, gil: String): Boolean {
        val g = gil.lowercase()
        if (g.contains("generate_204") || g.contains("gen_204")) { bonae(out, 204, "text/plain", ByteArray(0)); return true }
        if (g.contains("hotspot-detect") || g.contains("library/test/success")) {
            geulja(out, 200, "text/html", "<HTML><HEAD><TITLE>Success</TITLE></HEAD><BODY>Success</BODY></HTML>"); return true
        }
        if (g.contains("connecttest.txt")) { geulja(out, 200, "text/plain", "Microsoft Connect Test"); return true }
        if (g.contains("ncsi.txt")) { geulja(out, 200, "text/plain", "Microsoft NCSI"); return true }
        return false
    }
}
