// BYOD 방송 — 듣는 분께 소리를 나눠 보내는 작은 서버 (1.0.0판, 빌드 261003-B1, 이사장님 승인 2026-10-03)
// 노트북판(현장 해설방송 3.3판)과 길 이름을 똑같이 맞춰, 노트북판 듣는 화면(deut.html)이 그대로 돕니다.
//   /            듣는 화면(deut.html)
//   /status      방송 상태(keu·n·pan·gin·gseq·dang·dseq)
//   /stream      해설 소리 한 줄기(뮤 8비트, 1초에 16000)
//   /daegi.mp3 /ment.mp3  대기 음악·안내말
//   /beonho /deutnun /jiyeon /yocheong  듣는 분 쪽 알림(도움 요청은 태블릿 화면에 한 번 알림)
// 안드로이드 앱은 80번 문을 열 수 없어 8080번 문을 씁니다.
// 듣는 분 한 사람마다 작은 일꾼이 붙어, 느린 폰 하나가 다른 분들을 붙잡지 않게 합니다(밀리면 오래된 조각을 버림).
package kr.or.ada.app.byod

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class Seobeo(private val ctx: Context) {

    private class Deut(val s: Socket, val out: OutputStream) {
        val jul = ArrayBlockingQueue<ByteArray>(40)   // 4초까지 쌓아 둠
        @Volatile var jugeum = false
    }

    private var ss: ServerSocket? = null
    @Volatile private var dolgo = false
    private val deutneun = CopyOnWriteArrayList<Deut>()

    fun su(): Int = deutneun.size

    fun sijak() {
        if (dolgo) return
        val s = ServerSocket()
        s.reuseAddress = true
        s.bind(InetSocketAddress(Bang.PORT), 512)
        ss = s
        dolgo = true
        Thread({ batneunIl(s) }, "byod-mun").start()
    }

    fun meomchugi() {
        dolgo = false
        try { ss?.close() } catch (_: Exception) { }
        ss = null
        for (d in deutneun) kkeutnaegi(d)
        deutneun.clear()
        Bang.deutnunSu = 0
    }

    // 해설 소리 한 조각을 듣는 분 모두에게
    fun ppurida(b: ByteArray) {
        for (d in deutneun) {
            if (d.jugeum) continue
            if (!d.jul.offer(b)) { d.jul.poll(); d.jul.offer(b) }
        }
    }

    private fun batneunIl(s: ServerSocket) {
        while (dolgo) {
            val c = try { s.accept() } catch (_: Exception) { if (!dolgo) return else continue }
            val t = Thread(null, { mutgi(c) }, "byod-son", 512 * 1024)
            t.isDaemon = true
            t.start()
        }
    }

    private fun kkeutnaegi(d: Deut) {
        d.jugeum = true
        try { d.s.close() } catch (_: Exception) { }
        if (deutneun.remove(d)) { Bang.deutnunSu = deutneun.size; Bang.allyeo() }
    }

    // 머리 줄을 읽어 길과 물음을 돌려줌
    private fun meori(inp: InputStream): String? {
        val sb = StringBuilder()
        var nl = 0
        while (sb.length < 8192) {
            val ch = inp.read()
            if (ch < 0) return null
            val c = ch.toChar()
            sb.append(c)
            if (c == '\n') { nl++; if (sb.endsWith("\r\n\r\n") || sb.endsWith("\n\n")) break }
        }
        val first = sb.lineSequence().firstOrNull() ?: return null
        val parts = first.split(" ")
        return if (parts.size >= 2) parts[1] else null
    }

    private fun gap(q: String, k: String): String {
        for (kv in q.split("&")) {
            val i = kv.indexOf('=')
            if (i > 0 && kv.substring(0, i) == k) return try { URLDecoder.decode(kv.substring(i + 1), "UTF-8") } catch (_: Exception) { "" }
        }
        return ""
    }

    private fun mutgi(c: Socket) {
        var keep = false
        try {
            c.soTimeout = 10000
            c.tcpNoDelay = true
            val inp = java.io.BufferedInputStream(c.getInputStream(), 4096)
            val out = c.getOutputStream()
            val juso = meori(inp) ?: return
            val qi = juso.indexOf('?')
            val gil = (if (qi >= 0) juso.substring(0, qi) else juso)
            val q = if (qi >= 0) juso.substring(qi + 1) else ""

            if (inteonetCheok(out, gil)) return
            when (gil) {
                "/", "/index.html", "/deut.html", "/deut" -> {
                    val f = Jaryo.pail(ctx, "deut.html")
                    if (f != null) pail(out, f, "text/html; charset=utf-8") else geulja(out, 200, "text/html; charset=utf-8", Jaryo.GANDAN)
                }
                "/status" -> geulja(out, 200, "application/json", sangtaeJson())
                "/stream" -> { keep = true; deutgi(c, out) }
                "/daegi.mp3", "/ment.mp3" -> {
                    val f = Jaryo.pail(ctx, gil.substring(1))
                    if (f != null) pail(out, f, "audio/mpeg") else geulja(out, 404, "text/plain; charset=utf-8", "없는 소리입니다.")
                }
                "/yocheong" -> {
                    val k = gap(q, "k")
                    val n = gap(q, "n")
                    val majimak = Bang.yocheong.lastOrNull { it.beonho == n && it.jongryu == k }
                    val ok = majimak == null || System.currentTimeMillis() - majimak.ttae > 60_000 || n.isEmpty()
                    if (ok) {
                        Bang.yocheong.add(Bang.Yocheong(System.currentTimeMillis(), n, k))
                        while (Bang.yocheong.size > 200) Bang.yocheong.removeAt(0)
                        Bang.yocheongSeq++
                        Bang.allyeo()
                    }
                    geulja(out, 200, "application/json", "{\"ok\":" + ok + "}")
                }
                "/beonho", "/deutnun", "/jiyeon" -> geulja(out, 200, "application/json", "{\"ok\":true}")
                "/bang", "/pd" -> geulja(out, 200, "text/html; charset=utf-8",
                    "<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>BYOD 방송</title></head>" +
                        "<body style=\"font-family:sans-serif;font-size:20px;padding:16px\"><p>태블릿 BYOD 방송에서는 이 화면을 아직 옮기는 중입니다. 방송은 태블릿의 BYOD 방송 앱에서 다룹니다.</p></body></html>")
                else -> geulja(out, 404, "text/plain; charset=utf-8", "없는 주소입니다.")
            }
        } catch (_: Exception) {
        } finally {
            if (!keep) try { c.close() } catch (_: Exception) { }
        }
    }

    private fun sangtaeJson(): String {
        return "{\"hyeonjang\":true,\"byod\":true,\"keu\":" + Bang.keu + ",\"mic\":" + Bang.kyeojim +
            ",\"n\":" + deutneun.size + ",\"pan\":\"BYOD " + Bang.PAN + "\",\"bild\":\"" + Bang.BILD +
            "\",\"nas\":\"\",\"dang\":\"\",\"dseq\":0,\"dsik\":\"\",\"dmok\":[],\"gin\":\"\",\"gseq\":0,\"saepan\":\"\"}"
    }

    // 듣는 분 한 사람 붙이기 — 머리를 보낸 뒤 소리 없음 0.1초를 먼저 보내고, 그 뒤로는 조각이 오는 대로
    private fun deutgi(c: Socket, out: OutputStream) {
        c.soTimeout = 0
        try { c.sendBufferSize = 65536 } catch (_: Exception) { }
        val h = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nCache-Control: no-store\r\n" +
            "Access-Control-Allow-Origin: *\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n"
        out.write(h.toByteArray(Charsets.US_ASCII))
        out.write(MuLaw.goyo(Sori.JOGAK))
        out.flush()
        val d = Deut(c, out)
        deutneun.add(d)
        Bang.deutnunSu = deutneun.size
        Bang.allyeo()
        val t = Thread(null, {
            try {
                while (dolgo && !d.jugeum) {
                    val b = d.jul.poll(5, TimeUnit.SECONDS)
                    // 5초 넘게 조각이 없으면(해설 쉬는 중) 소리 없음 한 조각을 보내 끊긴 폰을 가려냄
                    out.write(b ?: MuLaw.goyo(160))
                    out.flush()
                }
            } catch (_: Exception) {
            } finally {
                kkeutnaegi(d)
            }
        }, "byod-deut", 256 * 1024)
        t.isDaemon = true
        t.start()
    }

    private fun bonae(out: OutputStream, code: Int, type: String, body: ByteArray) {
        val mal = when (code) { 200 -> "OK"; 204 -> "No Content"; 404 -> "Not Found"; else -> "OK" }
        val h = "HTTP/1.1 $code $mal\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n"
        out.write(h.toByteArray(Charsets.US_ASCII))
        if (body.isNotEmpty()) out.write(body)
        out.flush()
    }

    private fun geulja(out: OutputStream, code: Int, type: String, t: String) = bonae(out, code, type, t.toByteArray(Charsets.UTF_8))

    private fun pail(out: OutputStream, f: File, type: String) {
        val len = f.length()
        val h = "HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: $len\r\n" +
            "Cache-Control: no-store\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n"
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
