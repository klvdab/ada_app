// LVD 송출 안드로이드 1.9.0(빌드 261024-B17) — 현장 기능(노트북판 현장 해설방송 3.3판 서버를 옮김)
// 새방송클, 대조표 송출 21·22·23번 — 이사장님이 9월 24·27·28일에 승인하신 노트북판 기능을 아이피타임 방식(이 기기가 서버)에 옮김.
// 노트북판 hyeonjang.cs 와 같은 길·인자·답(JSON 칸 이름)·시간 규칙·403 문구라, 노트북판 화면(deut.html·pd.html·bang.html)이 고치지 않고 돕니다.
// 뺀 것: /naemic(윈도우 마이크), /saepan_batgi(노트북 새 판), DNS 53번(안드로이드 앱은 못 함), /siheom(버티기 시험 — 안 한다고 답함).
// 권한(heoga): 이 기기 자신(루프백) 또는 ?k=피디 번호(켤 때 정하는 네 자리) 또는 받아들인 폰(mideum) 또는 붙은 관계자(gwanMok).
// 기록(csv)은 지금 행사 폴더(GigiNokeum 의 행사 폴더)에, 상황 저장은 1분마다 행사 폴더와 고정 자리(byod_hyeonjang) 두 곳에(임시로 쓴 뒤 이름 바꾸기).
// 다시 켜지면 3시간 안에 저장된 오늘 상황을 이어 받음(전기가 끊기거나 앱이 다시 켜져도 입장 번호·요청·추첨·관계자·만족도가 남음).
// 새로 만든 네 가지(9월 28일 「지금 하기로」 하신 것, 노트북판에도 아직 없음):
//   가. 여러 갈래 해설 — 갈래 1 = 이 기기 사운드카드, 갈래 2·3 = POST /pcm?gal=n. /stream?gal=n, /gallae_ireum, /status 의 gal
//   나. 소리 나누어 조절 — 듣는 화면(deut.html) 안에서만(서버 일 없음)
//   다. 구역별·공유기별 인원 — /chulseok 의 guyeok, /status(heoga) 의 mang
//   라. 만족도 묻기 — /manjok_sijak /manjok /manjok_mok, /status 의 mj·mjseq, 결과보고와 자동 저장에 넣음, manjok_YYYYMMDD.csv
// 여러 일꾼(실)이 함께 부르므로 모든 목록은 garak 하나로 지킴(노트북판 bhGarak), 갈래 녹음은 nokGarak(노트북판 nokGarak).
package kr.or.ada.app.byod

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.net.URLDecoder
import java.security.SecureRandom
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** 한 물음(요청) — Seobeo 가 읽어 넘김 */
class Mureum(val method: String, val gil: String, val q: String, val host: String, val ua: String,
             val body: ByteArray, val loop: Boolean, val tls: Boolean, val ponIp: String)

/** 한 답 — loc 이 있으면 302 돌리기 */
class Dap(val code: Int, val type: String, val mom: ByteArray, val loc: String = "") {
    companion object {
        fun json(t: String, code: Int = 200) = Dap(code, "application/json", t.toByteArray(Charsets.UTF_8))
        fun geul(code: Int, type: String, t: String) = Dap(code, type, t.toByteArray(Charsets.UTF_8))
    }
}

/** 관계자 붙이기 큐알 그림 — 접속 도구(JeopsokActivity)와 같은 zxing 부품, 같은 방식(흰 여백 4칸, 칸마다 정수 점) */
fun hjQrGeurim(nae: String, keugi: Int): Bitmap {
    val m = com.google.zxing.qrcode.QRCodeWriter().encode(nae, com.google.zxing.BarcodeFormat.QR_CODE, keugi, keugi,
        mapOf(com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8", com.google.zxing.EncodeHintType.MARGIN to 4,
            com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M))
    val px = IntArray(keugi * keugi)
    for (y in 0 until keugi) for (x in 0 until keugi) px[y * keugi + x] = if (m.get(x, y)) Color.BLACK else Color.WHITE
    return Bitmap.createBitmap(px, keugi, keugi, Bitmap.Config.RGB_565)
}

object HyeonjangSeobeo {
    private const val JARYO_URL = "https://lvd.ada.or.kr/bfblive/hyeonjang/jaryo.json"
    private const val NOK_URL = "https://lvd.ada.or.kr/bfblive/hyeonjang/nokeum.php?k=lvdnok2026"
    private const val GWAN_403 = "{\"ok\":false,\"mal\":\"관계자로 붙은 뒤에 쓸 수 있습니다\"}"
    private const val MANJOK_GIBON = "오늘 현장영상해설은 어떠셨습니까?"
    private const val IEUM_HANDO = 3L * 3600_000L      // 다시 켰을 때 이어 받는 한도(같은 날, 3시간 안에 저장된 것)
    private val YC_JONG = setOf("dowum", "jakda", "cheoncheon", "gunggeum", "kkeunkim")
    private val GAL_GIBON = arrayOf("", "주 해설", "갈래 2 해설", "갈래 3 해설")

    private val garak = Any()          // 노트북판 bhGarak(+garak) — 모든 목록
    private val nokGarak = Any()       // 갈래 2·3 녹음
    private val rnd = SecureRandom()
    @Volatile private var app: Context? = null
    @Volatile var seobeo: Seobeo? = null

    // ───── 때(한국 시간) ─────
    private val KST: TimeZone = TimeZone.getTimeZone("Asia/Seoul")
    fun pyo(f: String, ms: Long): String = SimpleDateFormat(f, Locale.US).apply { timeZone = KST }.format(Date(ms))
    private fun now() = System.currentTimeMillis()
    private fun nal() = pyo("yyyyMMdd", now())
    private fun ts(ms: Long = now()) = pyo("yyyy-MM-dd HH:mm:ss", ms)

    // ───── 글 다루기(노트북판 Gap·Pul·Jsn·Jsn2·Susja·Ds) ─────
    fun gap(q: String, k: String): String {
        for (p in q.split('&')) {
            val e = p.indexOf('=')
            if (e > 0 && p.substring(0, e) == k) return p.substring(e + 1)
            if (e < 0 && p == k) return ""
        }
        return ""
    }
    fun pul(s: String): String = try { URLDecoder.decode(s, "UTF-8") } catch (_: Exception) { s }
    private fun jsn(t: String): String = t.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", " ").replace("\n", " ")
    private fun jsn2(s: String): String {
        val b = StringBuilder(s.length + 8)
        for (c in s) { if (c == '"' || c == '\\') b.append('\\').append(c) else if (c < ' ') b.append(' ') else b.append(c) }
        return b.toString()
    }
    private fun susja(n: String): Boolean = n.isNotEmpty() && n.length <= 4 && n.all { it in '0'..'9' }
    private fun ds(v: Double): String = DecimalFormat("0.##", DecimalFormatSymbols(Locale.US)).format(v)
    private fun jar(s: String, n: Int): String = if (s.length > n) s.substring(0, n) else s
    private fun ppae(s: String): String = s.replace("|", " ").replace("\r", " ").replace("\n", " ")   // 저장 줄에 넣을 글
    private fun nanseu(n: Int): String {
        val ab = "abcdefghjkmnpqrstuvwxyz23456789"
        val b = ByteArray(n); rnd.nextBytes(b)
        val s = StringBuilder(n)
        for (x in b) s.append(ab[(x.toInt() and 0xFF) % 31])
        return s.toString()
    }

    // ───── 자리(폴더) ─────
    private fun gojeongTeo(): File? = app?.let { File(it.filesDir, "byod_hyeonjang").also { d -> d.mkdirs() } }
    /** 지금 행사 폴더(행사 녹음 중이면 그 폴더, 아니면 고정 자리) */
    fun haengsaTeo(): File? = GigiNokeum.teoJigeum() ?: gojeongTeo()

    // ───── 스스로 점검(노트북판 2.5판) — 점검 중 5분까지는 기록을 쓰지 않고, 끝나면 되돌림 ─────
    @Volatile private var jgJung = false
    @Volatile private var jgSijakTtae = 0L
    private fun jgNow() = jgJung && now() - jgSijakTtae < 5 * 60_000L
    private var jgBon: Bon? = null

    /** 기록 한 줄 덧붙이기(UTF-8, 처음이면 BOM) — 점검 중에는 쓰지 않음(노트북판 JgFile) */
    private fun dotgi(ireum: String, jul: String) {
        if (jgNow()) return
        try {
            val d = haengsaTeo() ?: return
            val f = File(d, ireum)
            val sae = !f.isFile || f.length() == 0L
            FileOutputStream(f, true).use { o ->
                if (sae) o.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                o.write(jul.toByteArray(Charsets.UTF_8))
            }
        } catch (_: Exception) { }
    }

    // ───── 상태 ─────
    private val mideum = HashMap<String, Boolean>()            // 받아 준 폰
    private val gidari = LinkedHashMap<String, String>()       // 받기를 기다리는 폰(열쇠 -> 이름)
    private val gidariTtae = HashMap<String, Long>()
    @Volatile private var micTtae = 0L
    @Volatile private var sorikadeuTtae = 0L                   // 갈래 1(사운드카드) 소리가 마지막으로 온 때
    @Volatile private var nasMal = ""
    @Volatile private var bangsik = 0
    private val hwakinSu = AtomicInteger(0)
    private val jiyeon = HashMap<String, DoubleArray>()        // 폰 -> {늦음ms, 받은때ms}
    @Volatile private var ponIp = ""
    @Volatile private var ponTtae = 0L

    private val bhMok = HashMap<String, LongArray>()           // 입장 번호 -> {처음, 마지막}
    private val bhGuyeok = HashMap<String, String>()           // 입장 번호 -> 구역
    private val dangMok = ArrayList<String>()
    private var dangcheom = ""
    private var dangSeq = 0
    private var dangSik = ""
    private val dangGirok = ArrayList<String>()                // 결과보고용 당첨 줄
    private var sunseoI = 0
    private var sunseoT = 0L
    private class DeutJul(val ttae: Long, val kkeun: Int, val n: String)
    private val deutMok = HashMap<String, DeutJul>()
    private val ycMok = ArrayList<Array<String>>()             // [seq, HH:mm:ss, 번호, 종류, 처리(0/1), 때ms]
    private var ycSeq = 0
    private val ycGeuje = HashMap<String, Long>()
    private val malMok = ArrayList<Array<String>>()            // [seq, HH:mm:ss, 이름, 글]
    private var malSeq = 0
    private class Kamera(val jpeg: ByteArray, val ttae: Long, val nm: String, val zmin: Double, val zmax: Double, val z: Double, var zreq: Double, var zseq: Int) {
        fun bok() = Kamera(jpeg, ttae, nm, zmin, zmax, z, zreq, zseq)
    }
    private val kamMok = HashMap<String, Kamera>()
    private var deutChoeda = 0
    private var deutChoedaT = 0L
    private var gwanKey = ""
    private val gwanMok = HashMap<String, Array<String>>()     // 열쇠 -> [역할, 이름, 처음ms, 마지막ms]
    private class Mj(val id: Int, val ttae: Long, val nae: String, val nm: String, val to: String, val jong: String, val mime: String, val b: ByteArray?)
    private val mjMok = ArrayList<Mj>()
    private var mjSeq = 0
    private val ginGirok = ArrayList<String>()                 // 결과보고용 긴급 안내 줄
    private val pyosiMok = ArrayList<String>()                 // 결과보고용 순간 표시 줄
    // 가. 여러 갈래
    private val galIreum = GAL_GIBON.copyOf()
    private val galTtae = LongArray(4)
    private val galChoeda = IntArray(4)
    private val galBaiteu = LongArray(4)
    // 라. 만족도
    private var manjokQ = ""
    private var manjokSeq = 0
    private val manjokMuleum = LinkedHashMap<Int, String>()
    private class Manjok(val seq: Int, val n: String, val p: Int, val t: String, val ttae: Long)
    private val manjokDap = LinkedHashMap<String, Manjok>()    // "seq|폰" -> 답
    // 자동 저장
    @Volatile private var jadongMal = ""
    @Volatile private var jadongIeo = ""
    @Volatile private var jadongSeq = 0
    @Volatile private var majimakJeojang = 0L
    @Volatile private var nokOlrimMal = ""

    // ───── 켜고 끄기 ─────
    /** 서버를 켤 때(Seobeo.sijak) — 피디 번호, 받아 준 폰, 이어 받기, 1분마다 저장 */
    fun sijak(ctx: Context, s: Seobeo) {
        app = ctx.applicationContext
        seobeo = s
        Bang.pdKey = (1000 + rnd.nextInt(8999)).toString()   // 노트북판 Random.Next(1000, 9999)
        GigiNokeum.deohal = { bogoDeohal() }                 // 행사 마감 결과보고에 현장 기능 칸을 더함
        try {
            val gd = gojeongTeo()
            if (gd != null) {
                val mf = File(gd, "mideum.txt")
                if (mf.isFile) synchronized(garak) { for (l in mf.readLines()) { val t = l.trim(); if (t.length >= 16) mideum[t] = true } }
                val bf = File(gd, "bangsik.txt")
                if (bf.isFile && bf.readText().trim() == "1") bangsik = 1
            }
        } catch (_: Exception) { }
        // 오래된(3시간 넘게 지난) 상황이 메모리에 남아 있으면 새 행사로 보고 비움
        if (majimakJeojang > 0 && now() - majimakJeojang > IEUM_HANDO) bium()
        try { sangtaeBulleo() } catch (_: Exception) { }
        jadongSijak()
    }

    /** 서버를 끌 때(Seobeo.meomchugi) — 마지막으로 한 번 저장 */
    fun kkeut() {
        jadongSeq++
        try { jadongJeojang() } catch (_: Exception) { }
        synchronized(nokGarak) { for (g in 2..3) galDatgi(g) }
        seobeo = null
        Bang.pdKey = ""
    }

    private fun bium() {
        synchronized(garak) {
            bhMok.clear(); bhGuyeok.clear(); dangMok.clear(); dangcheom = ""; dangSeq = 0; dangSik = ""; dangGirok.clear()
            sunseoI = 0; sunseoT = 0L; deutMok.clear(); ycMok.clear(); ycSeq = 0; ycGeuje.clear(); malMok.clear(); malSeq = 0
            kamMok.clear(); deutChoeda = 0; deutChoedaT = 0L; gwanKey = ""; gwanMok.clear(); mjMok.clear(); mjSeq = 0
            ginGirok.clear(); pyosiMok.clear(); Bang.ginMal = ""
            for (g in 0..3) { galIreum[g] = GAL_GIBON[g]; galChoeda[g] = 0; galBaiteu[g] = 0L }
            manjokQ = ""; manjokSeq = 0; manjokMuleum.clear(); manjokDap.clear()
        }
        jadongIeo = ""
    }

    // ───── Seobeo 가 부르는 작은 것들 ─────
    fun sorikadeuOm() { val t = now(); sorikadeuTtae = t; galTtae[1] = t; galBaiteu[1] += Sori.JOGAK.toLong() }
    fun galDeutnun(g: Int, su: Int) { if (g in 1..3) synchronized(garak) { if (su > galChoeda[g]) galChoeda[g] = su } }
    fun hwakinSegi() { hwakinSu.incrementAndGet() }
    fun hostMan(host: String): String {
        if (host.startsWith("[")) return host.substringBefore(']').removePrefix("[")
        val c = host.lastIndexOf(':')
        return if (c > 0) host.substring(0, c) else host
    }
    private fun naeJuso(): List<String> {
        val l = ArrayList<String>()
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp) continue
                for (a in ni.inetAddresses) {
                    if (a !is Inet4Address) continue
                    val s = a.hostAddress ?: continue
                    if (s.startsWith("127.") || s.startsWith("169.254.")) continue
                    l.add(s)
                }
            }
        } catch (_: Exception) { }
        return l
    }
    fun naeJusoInga(ho: String): Boolean = naeJuso().contains(ho)
    /** 폰이 실제로 들어오는 쪽의 이 기기 주소(노트북판 PonJuso) */
    fun ponJuso(): String {
        val nae = naeJuso()
        val p = ponIp
        if (p.isNotEmpty() && p.indexOf('.') > 0 && now() - ponTtae < 12 * 3600_000L) {
            val pre = p.substring(0, p.lastIndexOf('.') + 1)
            for (a in nae) if (a.startsWith(pre)) return a
        }
        val j = ByodService.jusoChatgi().removePrefix("http://").substringBefore(":")
        if (j.isNotEmpty()) return j
        for (a in nae) if (a.startsWith("192.168.")) return a
        return "192.168.0.2"
    }
    /** 관계자 붙이기 큐알 주소(첫 화면의 큐알 보기) */
    fun gwanQrJuso(): String = "http://" + ponJuso() + ":" + Bang.PORT + "/gwan?gk=" + gwanKeyGet()
    private fun gwanKeyGet(): String = synchronized(garak) { if (gwanKey.isEmpty()) gwanKey = nanseu(8); gwanKey }
    private fun galOn(g: Int) = now() - galTtae[g] < 4000

    private fun gwanOk(q: String): Boolean {
        var t = gap(q, "gt"); if (t.isEmpty()) t = gap(q, "t")
        if (t.length < 10) return false
        return synchronized(garak) { val v = gwanMok[t]; if (v != null) v[3] = now().toString(); v != null }
    }

    // ───── 물음 하나 다루기(노트북판 Batgi 의 길 나누기와 같은 차례) ─────
    fun cheori(m: Mureum): Dap {
        val q = m.q
        val gil = m.gil
        val tok = gap(q, "t")
        val mid = synchronized(garak) { tok.length >= 16 && mideum.containsKey(tok) }
        val pk = Bang.pdKey
        val kOk = pk.isNotEmpty() && gap(q, "k") == pk
        val heoga = m.loop || kOk || mid || gwanOk(q)
        if (!m.loop && m.ponIp.isNotEmpty()) { ponIp = m.ponIp; ponTtae = now() }   // 2.8판: 폰이 들어오는 쪽을 기억

        if (gil == "/beonho" && m.loop && gap(q, "n").isEmpty()) return Dap.geul(200, "text/plain", pk)
        if (gil == "/deulgi") return deulgi(heoga, tok, m.ua)
        if (gil == "/batgi" && heoga) {
            val b = gap(q, "b")
            synchronized(garak) {
                if (gidari.containsKey(b)) {
                    gidari.remove(b); gidariTtae.remove(b); mideum[b] = true
                    if (!jgNow()) try { gojeongTeo()?.let { File(it, "mideum.txt").appendText(b + "\r\n") } } catch (_: Exception) { }
                }
            }
            return Dap.json("{\"ok\":true}")
        }
        if (gil == "/status") return sangtae(heoga)
        if (gil == "/cue") {
            if (!heoga) return Dap.json("{\"ok\":false,\"msg\":\"해설사 번호가 맞지 않습니다.\"}", 403)
            Bang.keu = gap(q, "v") == "1"
            Bang.allyeo()
            return Dap.json("{\"ok\":true,\"keu\":" + Bang.keu + "}")
        }
        if (gil == "/mic" && heoga) { micTtae = now(); return Dap.json("{\"ok\":true,\"keu\":" + Bang.keu + "}") }
        if (gil == "/nas" && heoga) { nasMal = pul(gap(q, "t")); return Dap.json("{\"ok\":true}") }
        if (gil == "/bangsik" && heoga) {
            bangsik = if (gap(q, "v") == "1") 1 else 0
            try { gojeongTeo()?.let { File(it, "bangsik.txt").writeText(bangsik.toString()) } } catch (_: Exception) { }
            return Dap.json("{\"ok\":true,\"bangsik\":$bangsik}")
        }
        if (gil == "/siheom" && heoga) return Dap.json("{\"ok\":false,\"msg\":\"LVD 송출(태블릿)에서는 버티기 시험을 하지 않습니다.\"}")
        if ((gil == "/yocheong_mok" || gil == "/yocheong_cheori" || gil == "/hs_mal" || gil == "/hs_mal_mok" || gil == "/kamera_mok" || gil == "/kamera_bogi" || gil == "/pyosi" ||
                (gil == "/sunseo" && gap(q, "i").isNotEmpty())) && !heoga) return Dap.json(GWAN_403, 403)
        when (gil) {
            "/deutnun", "/yocheong", "/yocheong_cheori", "/yocheong_mok" -> return yocheong(gil, q)
            "/hs_mal", "/hs_mal_mok" -> return malCheori(gil, q)
            "/pyosi", "/nokeum_sangtae", "/nokeum_olligi" -> return pyosiCheori(gil, q, heoga)
            "/kamera", "/kamera_bogi", "/kamera_mok", "/kamera_zoom" -> return kamera(gil, q, m.method, m.body, heoga)
            "/mj_bonae", "/mj_hochul", "/mj_mok", "/mj_deul", "/mj_saram" -> return mujeon(gil, q, m.method, m.body, heoga, m.loop)
            "/gwan_qr", "/gwan_deung", "/gwan_mok" -> return gwan(gil, q, heoga, m.host)
            "/gingeup" -> {
                if (!heoga) return Dap.json("{\"ok\":false}", 403)
                val gt = jar(pul(gap(q, "t")).trim(), 200)
                var gs = 0
                synchronized(garak) {
                    Bang.ginMal = gt; Bang.ginSeq++; gs = Bang.ginSeq
                    if (!jgNow()) ginGirok.add(pyo("HH:mm:ss", now()) + " " + (if (gt.isEmpty()) "긴급 안내 끝" else gt))
                }
                dotgi("gingeup_" + nal() + ".csv", ts() + "," + (if (gt.isEmpty()) "긴급 안내 끝" else gt.replace(",", " ").replace("\r", " ").replace("\n", " ")) + "\r\n")
                Bang.allyeo()
                return Dap.json("{\"ok\":true,\"gseq\":$gs}")
            }
            "/jeomgeom_sijak", "/jeomgeom_kkeut" -> {
                if (!m.loop && !kOk) return Dap.json("{\"ok\":false,\"mal\":\"피디 화면에서만 쓸 수 있습니다\"}", 403)
                if (gil == "/jeomgeom_sijak") {
                    if (Bang.keu) return Dap.json("{\"ok\":false,\"mal\":\"해설이 나가는 중에는 점검할 수 없습니다. 해설을 멈춘 뒤 눌러 주십시오\"}")
                    if (jgNow()) return Dap.json("{\"ok\":false,\"mal\":\"점검이 이미 진행 중입니다\"}")
                    if (!jgDamgi()) return Dap.json("{\"ok\":false,\"mal\":\"지금 상황을 담지 못해 점검을 시작하지 않았습니다\"}")
                    jgSijakTtae = now(); jgJung = true
                    return Dap.json("{\"ok\":true,\"pan\":\"BYOD " + Bang.PAN + "\",\"bild\":\"" + Bang.BILD + "\"}")
                }
                if (jgJung) { jgDoerigi(); jgJung = false }
                return Dap.json("{\"ok\":true}")
            }
            "/jadong" -> return Dap.json("{\"mal\":\"" + jsn2(jadongMal) + "\",\"teo\":\"" + jsn2(haengsaTeo()?.path ?: "") + "\"}")
            "/bogoseo", "/bogoseo.txt" -> return bogoCheori(gil, heoga)
            "/jaryo", "/jaryo_badgi", "/sunseo" -> return haeseol(gil, q)
            "/beonho", "/chulseok", "/chucheom", "/dangcheom" -> return beonho(gil, q, heoga)
            "/jiyeon" -> {
                val id = gap(q, "i")
                val ms = gap(q, "d").toDoubleOrNull()
                if (id.length in 6..40 && ms != null && ms >= 0 && ms < 60000)
                    synchronized(garak) { if (jiyeon.containsKey(id) || jiyeon.size < 3000) jiyeon[id] = doubleArrayOf(ms, now().toDouble()) }
                return Dap.json("{\"ok\":true}")
            }
            "/gallae_ireum" -> {
                if (!heoga) return Dap.json(GWAN_403, 403)
                val g = gap(q, "gal").toIntOrNull()
                if (g == null || g !in 1..3) return Dap.json("{\"ok\":false}", 400)
                val nm = jar(pul(gap(q, "nm")).trim(), 20).ifEmpty { GAL_GIBON[g] }
                synchronized(garak) { galIreum[g] = nm }
                return Dap.json("{\"ok\":true,\"gal\":$g,\"nm\":\"" + jsn2(nm) + "\"}")
            }
            "/manjok_sijak", "/manjok", "/manjok_mok" -> return manjok(gil, q, heoga)
        }
        if (gil == "/pcm" && m.method == "POST") {
            if (!heoga) return Dap.json("{\"ok\":false}", 403)
            micTtae = now()
            val g = gap(q, "gal").toIntOrNull()?.takeIf { it in 1..3 } ?: 1
            val b = m.body
            if (Bang.keu && b.isNotEmpty()) {
                val s = seobeo
                if (g == 1) {
                    // 갈래 1 은 이 기기 사운드카드 해설 — 사운드카드 소리가 2초 넘게 없을 때만 폰 해설로 메움(두 소리가 겹치지 않게)
                    if (now() - sorikadeuTtae > 2000) { galTtae[1] = now(); galBaiteu[1] += b.size.toLong(); s?.ppuridaGal(1, b); GigiNokeum.sseugi(b) }
                } else {
                    galTtae[g] = now(); galBaiteu[g] += b.size.toLong()
                    s?.ppuridaGal(g, b)
                    galSseugi(g, b)
                }
            }
            return Dap.json("{\"ok\":true,\"keu\":" + Bang.keu + ",\"gal\":$g}")
        }
        return Dap.geul(404, "text/plain; charset=utf-8", "없는 주소입니다.")
    }

    // ───── 들어가기 청하기(노트북판 /deulgi) ─────
    private fun deulgi(heoga: Boolean, tok: String, ua: String): Dap {
        if (heoga) return Dap.json("{\"ok\":true}")
        if (tok.length < 16 || tok.length > 64) return Dap.json("{\"ok\":false}")
        val nm = when { ua.contains("iPhone") -> "아이폰"; ua.contains("iPad") -> "아이패드"; ua.contains("Android") -> "안드로이드 폰"; else -> "컴퓨터" }
        synchronized(garak) {
            if (!gidari.containsKey(tok) && gidari.size > 20) return Dap.json("{\"ok\":false}")
            gidari[tok] = nm; gidariTtae[tok] = now()
        }
        return Dap.json("{\"ok\":false,\"daegi\":true}")
    }

    // ───── 상태(노트북판 /status + 새 칸 gal·mj·mjseq·mang) ─────
    private fun sangtae(heoga: Boolean): Dap {
        val s = seobeo
        val n = s?.su() ?: 0
        val mic = now() - micTtae < 4000 || galOn(1) || galOn(2) || galOn(3)
        val sb = StringBuilder(1024)
        sb.append("{\"hyeonjang\":true,\"byod\":true,\"keu\":").append(Bang.keu).append(",\"mic\":").append(mic)
            .append(",\"n\":").append(n).append(",\"pan\":\"BYOD ").append(Bang.PAN).append("\",\"bild\":\"").append(Bang.BILD)
            .append("\",\"nas\":\"").append(jsn(nasMal)).append('"')
        if (heoga) {
            val gb = StringBuilder()
            synchronized(garak) {
                val t = now()
                val ol = gidariTtae.filter { t - it.value > 20_000 }.keys.toList()
                for (x in ol) { gidari.remove(x); gidariTtae.remove(x) }
                for ((k, v) in gidari) gb.append(if (gb.isNotEmpty()) "," else "").append("{\"t\":\"").append(jsn(k)).append("\",\"nm\":\"").append(v).append("\"}")
            }
            val ip = ponJuso()
            val tp = s?.tport ?: 0
            sb.append(",\"sjuso\":\"").append(if (tp > 0) "https://$ip:$tp/bang?k=" + Bang.pdKey else "")
                .append("\",\"djuso\":\"http://").append(ip).append(':').append(Bang.PORT).append("/\",\"gidari\":[").append(gb).append(']')
                .append(",\"bangsik\":").append(bangsik).append(",\"dns\":0,\"hwakin\":").append(hwakinSu.get())
                .append(",\"jiyeon\":\"").append(jsn(jiyeonMal())).append("\",\"siheom\":\"\",\"sij\":false")
            // 다. 공유기별 인원
            sb.append(",\"mang\":[")
            var i = 0
            for ((mk, su) in s?.mangSu() ?: emptyMap()) { if (i++ > 0) sb.append(','); sb.append("{\"m\":\"").append(jsn2(mk)).append("\",\"su\":").append(su).append('}') }
            sb.append(']')
        }
        synchronized(garak) {
            sb.append(", \"dang\":\"").append(dangcheom).append("\", \"dseq\":").append(dangSeq).append(", \"dsik\":\"").append(dangSik)
                .append("\", \"dmok\":[").append(dangJson()).append("], \"gin\":\"").append(jsn(Bang.ginMal)).append("\", \"gseq\":").append(Bang.ginSeq)
                .append(", \"saepan\":\"\"")
            // 가. 여러 갈래
            sb.append(",\"gal\":[")
            for (g in 1..3) {
                if (g > 1) sb.append(',')
                sb.append("{\"n\":").append(g).append(",\"nm\":\"").append(jsn2(galIreum[g])).append("\",\"on\":").append(galOn(g)).append(",\"su\":").append(s?.galSu(g) ?: 0).append('}')
            }
            sb.append(']')
            // 라. 만족도
            sb.append(",\"mj\":\"").append(jsn2(manjokQ)).append("\",\"mjseq\":").append(manjokSeq)
        }
        sb.append('}')
        return Dap.json(sb.toString())
    }

    private fun dangJson(): String = dangMok.joinToString(",") { "\"" + it + "\"" }   // garak 안에서 부름

    private fun jiyeonMal(): String {
        var hap = 0.0; var keun = 0.0; var su = 0
        synchronized(garak) {
            val t = now().toDouble()
            val nal = ArrayList<String>()
            for ((k, v) in jiyeon) {
                if (t - v[1] > 20_000) { nal.add(k); continue }
                hap += v[0]; su++; if (v[0] > keun) keun = v[0]
            }
            for (x in nal) jiyeon.remove(x)
        }
        if (su == 0) return ""
        val pyeong = hap / su + 200   // 해설사 쪽에서 모아 보내는 0.2초를 더함
        return "소리 늦음 — 듣는 폰 " + su + "대 평균 약 " + String.format(Locale.US, "%.1f", pyeong / 1000.0) + "초, 가장 늦은 폰 약 " + String.format(Locale.US, "%.1f", (keun + 200) / 1000.0) + "초."
    }

    // ───── 입장 번호·참석·추첨(노트북판 BeonhoCheori) + 다. 구역별 ─────
    private fun beonho(gil: String, q: String, heoga: Boolean): Dap {
        val t = now()
        if (gil == "/beonho") {
            var n = gap(q, "n")
            if (!susja(n)) return Dap.json("{\"ok\":false}", 400)
            n = n.trimStart('0').ifEmpty { "0" }
            val gyk = jar(pul(gap(q, "g")).trim(), 10)
            var sae = false
            synchronized(garak) {
                if (gyk.isNotEmpty()) bhGuyeok[n] = gyk
                val v = bhMok[n]
                if (v != null) v[1] = t else { bhMok[n] = longArrayOf(t, t); sae = true }
            }
            if (sae) dotgi("chulseok_" + nal() + ".csv", ts(t) + "," + n + ",처음 연결\r\n")
            return Dap.json("{\"ok\":true,\"n\":\"$n\"}")
        }
        if (!heoga) return Dap.json("{\"ok\":false}", 403)
        if (gil == "/chulseok") {
            val sb = StringBuilder(); var modu = 0; var jigeum = 0
            val gy = java.util.TreeMap<String, IntArray>()   // 구역 -> {지금, 모두}
            var dang = ""
            synchronized(garak) {
                val ks = bhMok.keys.sortedBy { it.toIntOrNull() ?: 0 }
                for (k in ks) {
                    val v = bhMok[k] ?: continue
                    val on = t - v[1] < 75_000
                    modu++; if (on) jigeum++
                    if (sb.isNotEmpty()) sb.append(",")
                    sb.append("{\"n\":\"").append(k).append("\",\"cheo\":\"").append(pyo("HH:mm", v[0])).append("\",\"majimak\":\"").append(pyo("HH:mm", v[1]))
                        .append("\",\"bun\":").append(Math.round((v[1] - v[0]) / 60_000.0)).append(",\"jigeum\":").append(on).append('}')
                    val g = bhGuyeok[k]
                    if (!g.isNullOrEmpty()) { val a = gy.getOrPut(g) { IntArray(2) }; a[1]++; if (on) a[0]++ }
                }
                dang = dangJson()
            }
            val gs = StringBuilder()
            for ((g, a) in gy) { if (gs.isNotEmpty()) gs.append(','); gs.append("{\"g\":\"").append(jsn2(g)).append("\",\"jigeum\":").append(a[0]).append(",\"modu\":").append(a[1]).append('}') }
            return Dap.json("{\"ok\":true,\"modu\":$modu,\"jigeum\":$jigeum,\"dang\":[$dang],\"mok\":[$sb],\"guyeok\":[$gs]}")
        }
        if (gil == "/chucheom") {
            val beom = gap(q, "beom")
            val kkeut = gap(q, "kkeut").toIntOrNull() ?: 0
            val ppaegi = gap(q, "ppae") != "0"
            val pool = ArrayList<String>()
            var ppob = ""
            var seq = 0
            synchronized(garak) {
                if (beom == "modu") { var i = 1; while (i <= kkeut && i <= 9999) { pool.add(i.toString()); i++ } }
                else for ((k, v) in bhMok) if (t - v[1] < 75_000) pool.add(k)
                if (ppaegi) pool.removeAll { dangMok.contains(it) }
                if (pool.isNotEmpty()) {
                    val idx = ((rnd.nextInt().toLong() and 0xFFFFFFFFL) % pool.size).toInt()
                    ppob = pool[idx]; dangcheom = ppob; dangSik = "r"; dangMok.add(ppob); dangSeq++; seq = dangSeq
                    dangGirok.add(pyo("HH:mm:ss", t) + " " + ppob + "번 추첨 당첨(" + (if (beom == "modu") "1~" + kkeut + "번" else "지금 듣는 번호") + " " + pool.size + "개 중)")
                }
            }
            if (ppob.isEmpty()) return Dap.json("{\"ok\":false,\"mal\":\"뽑을 번호가 없습니다\"}")
            dotgi("chulseok_" + nal() + ".csv", ts(t) + "," + ppob + ",추첨 당첨(" + (if (beom == "modu") "1~" + kkeut + "번" else "지금 듣는 번호") + " " + pool.size + "개 중)\r\n")
            return Dap.json("{\"ok\":true,\"n\":\"$ppob\",\"mo\":" + pool.size + ",\"seq\":$seq}")
        }
        if (gil == "/dangcheom") {
            var n = gap(q, "n")
            if (!susja(n)) return Dap.json("{\"ok\":false,\"mal\":\"번호를 숫자로 적어 주십시오\"}")
            n = n.trimStart('0').ifEmpty { "0" }
            var itda = false
            var seq = 0
            var deunna = false
            synchronized(garak) {
                if (dangMok.contains(n)) itda = true
                else { dangMok.add(n); dangcheom = n; dangSik = "m"; dangSeq++; dangGirok.add(pyo("HH:mm:ss", t) + " " + n + "번 직접 호명 당첨") }
                seq = dangSeq
                val v = bhMok[n]
                deunna = v != null && t - v[1] < 75_000
            }
            if (itda) return Dap.json("{\"ok\":false,\"mal\":\"" + n + "번은 이미 당첨된 번호입니다\"}")
            dotgi("chulseok_" + nal() + ".csv", ts(t) + "," + n + ",직접 호명 당첨\r\n")
            return Dap.json("{\"ok\":true,\"n\":\"$n\",\"seq\":$seq,\"deunna\":$deunna}")
        }
        return Dap.json("{\"ok\":false}", 404)
    }

    // ───── 해설사 화면 자료와 순서(노트북판 HaeseolCheori) ─────
    private fun jaryoPail(): File? = gojeongTeo()?.let { File(it, "jaryo.json") }
    private fun jaryoBatgi(): Boolean = try {
        val c = URL(JARYO_URL + "?z=" + now()).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000; c.useCaches = false
        val t = if (c.responseCode == 200) c.inputStream.bufferedReader(Charsets.UTF_8).readText().trim() else ""
        c.disconnect()
        val f = jaryoPail()
        if (f != null && t.startsWith("{") && t.endsWith("}")) { f.writeText(t, Charsets.UTF_8); true } else false
    } catch (_: Exception) { false }

    private fun haeseol(gil: String, q: String): Dap {
        if (gil == "/jaryo_badgi") return Dap.json("{\"ok\":" + jaryoBatgi() + "}")
        if (gil == "/jaryo") {
            val f = jaryoPail()
            if (f != null && !f.isFile) jaryoBatgi()
            val t = try { if (f != null && f.isFile) f.readText(Charsets.UTF_8) else "{}" } catch (_: Exception) { "{}" }
            return Dap.json(t)
        }
        // /sunseo
        val i = gap(q, "i")
        val n = i.toIntOrNull()
        if (i.isNotEmpty() && i.length < 4 && n != null && n >= 0) synchronized(garak) { sunseoI = n; sunseoT = now() }
        var si = 0; var jn = -1L
        synchronized(garak) { si = sunseoI; jn = if (sunseoT == 0L) -1L else (now() - sunseoT) / 1000 }
        return Dap.json("{\"i\":$si,\"jinan\":$jn}")
    }

    // ───── 듣는 분 상태·요청(노트북판 YocheongCheori) ─────
    private fun ycIreum(k: String) = when (k) {
        "dowum" -> "도움이 필요합니다"; "jakda" -> "소리가 작아요"; "cheoncheon" -> "조금 천천히"
        "gunggeum" -> "무엇이 보이는지 궁금해요"; "kkeunkim" -> "소리가 끊겨요"; else -> k
    }
    private fun ycGirok(jul: String) = dotgi("yocheong_" + nal() + ".csv", jul + "\r\n")
    private fun guyeokOf(n: String): String = if (n.isEmpty()) "" else bhGuyeok[n] ?: ""   // garak 안에서 부름
    private fun deutSegi() {   // garak 안에서 부름
        val t = now(); var n = 0
        for (v in deutMok.values) if (t - v.ttae < 45_000) n++
        if (n > deutChoeda) { deutChoeda = n; deutChoedaT = t }
    }

    private fun yocheong(gil: String, q: String): Dap {
        val t = now()
        if (gil == "/deutnun") {
            val i = gap(q, "i"); val s = gap(q, "s"); var n = gap(q, "n")
            if (i.isEmpty() || i.length > 20) return Dap.json("{\"ok\":false}", 400)
            if (!susja(n)) n = ""
            synchronized(garak) {
                deutMok[i] = DeutJul(t, if (s == "1") 1 else 0, n); deutSegi()
                if (deutMok.size > 3000) { val ol = deutMok.filter { t - it.value.ttae > 600_000 }.keys.toList(); for (k in ol) deutMok.remove(k) }
            }
            return Dap.json("{\"ok\":true}")
        }
        if (gil == "/yocheong") {
            val i = gap(q, "i"); var n = gap(q, "n"); val k = gap(q, "k")
            if (!susja(n)) n = ""
            if (k !in YC_JONG) return Dap.json("{\"ok\":false}", 400)
            val key = "$i|$k"
            var neomu = false
            synchronized(garak) {
                val d = ycGeuje[key]
                if (d != null && t - d < 20_000) neomu = true
                else {
                    ycGeuje[key] = t; ycSeq++
                    ycMok.add(arrayOf(ycSeq.toString(), pyo("HH:mm:ss", t), n, k, "0", t.toString())); if (ycMok.size > 300) ycMok.removeAt(0)
                    if (ycGeuje.size > 3000) { val ol = ycGeuje.filter { t - it.value > 60_000 }.keys.toList(); for (x in ol) ycGeuje.remove(x) }
                    // 첫 화면에 한 번 알리고 행사 기록에 남김(점검 중이 아닐 때)
                    if (!jgNow()) {
                        Bang.yocheong.add(Bang.Yocheong(t, n, k))
                        while (Bang.yocheong.size > 200) Bang.yocheong.removeAt(0)
                        Bang.yocheongSeq++
                    }
                }
            }
            if (!neomu) { ycGirok(pyo("HH:mm:ss", t) + "," + n + "," + ycIreum(k)); Bang.allyeo() }
            return Dap.json("{\"ok\":" + !neomu + "}")
        }
        if (gil == "/yocheong_cheori") {
            val id = gap(q, "id")
            synchronized(garak) { for (y in ycMok) if (y[0] == id) y[4] = "1" }
            ycGirok(pyo("HH:mm:ss", t) + ",,도와드림 확인 " + id)
            return Dap.json("{\"ok\":true}")
        }
        if (gil == "/yocheong_mok") {
            var deut = 0; var kkeun = 0; val hp = StringBuilder(); val jom = LinkedHashMap<String, Int>(); var maji = 0
            synchronized(garak) {
                for (v in deutMok.values) if (t - v.ttae < 45_000) { deut++; if (v.kkeun == 1) kkeun++ }
                for (y in ycMok) {
                    val yt = y[5].toLongOrNull() ?: 0L
                    if (y[3] == "dowum") {
                        if (y[4] == "0") {
                            if (hp.isNotEmpty()) hp.append(",")
                            hp.append("{\"id\":").append(y[0]).append(",\"t\":\"").append(y[1]).append("\",\"n\":\"").append(jsn2(y[2])).append("\",\"g\":\"")
                                .append(jsn2(guyeokOf(y[2]))).append("\",\"bun\":").append((t - yt) / 60_000).append('}')
                        }
                    } else if (t - yt < 120_000) jom[y[3]] = (jom[y[3]] ?: 0) + 1
                }
                maji = ycSeq
            }
            val js = jom.entries.joinToString(",") { "\"" + it.key + "\":" + it.value }
            return Dap.json("{\"deut\":$deut,\"kkeun\":$kkeun,\"seq\":$maji,\"dowum\":[$hp],\"jom\":{$js}}")
        }
        return Dap.json("{\"ok\":false}", 404)
    }

    // ───── 해설사끼리 글(노트북판 MalCheori) ─────
    private fun malCheori(gil: String, q: String): Dap {
        if (gil == "/hs_mal") {
            val nm = jar(pul(gap(q, "nm")), 20); val t = pul(gap(q, "t"))
            if (t.isEmpty() || t.length > 300) return Dap.json("{\"ok\":false}", 400)
            synchronized(garak) { malSeq++; malMok.add(arrayOf(malSeq.toString(), pyo("HH:mm:ss", now()), nm, t)); if (malMok.size > 50) malMok.removeAt(0) }
            return Dap.json("{\"ok\":true}")
        }
        val sb = StringBuilder(); var maji = 0
        synchronized(garak) {
            for (m in malMok) { if (sb.isNotEmpty()) sb.append(","); sb.append("{\"id\":").append(m[0]).append(",\"t\":\"").append(m[1]).append("\",\"nm\":\"").append(jsn2(m[2])).append("\",\"mal\":\"").append(jsn2(m[3])).append("\"}") }
            maji = malSeq
        }
        return Dap.json("{\"seq\":$maji,\"mok\":[$sb]}")
    }

    // ───── 순간 표시·녹음 상태·올리기(노트북판 PyosiCheori) — 녹음은 행사 녹음(GigiNokeum)에 맞춤 ─────
    private fun pyosiCheori(gil: String, q: String, heoga: Boolean): Dap {
        val c = app
        if (gil == "/pyosi") {
            val nm = jar(pul(gap(q, "nm")), 20); val t = jar(pul(gap(q, "t")), 100); val ss = jar(pul(gap(q, "ss")), 60)
            val sid = GigiNokeum.sid
            val pail = if (sid.isNotEmpty()) "$sid.ul" else "(녹음 없음)"
            val ofs = if (sid.isNotEmpty()) GigiNokeum.sseunBaiteu / 16000 else 0L
            val jt = now()
            dotgi("pyosi_" + nal() + ".csv", ts(jt) + "," + pail + "," + ofs + "," + ss.replace(",", " ") + "," + nm.replace(",", " ") + "," + t.replace(",", " ") + "\r\n")
            if (!jgNow()) {
                synchronized(garak) { pyosiMok.add(pyo("HH:mm:ss", jt) + " (녹음 " + ofs / 60 + "분 " + ofs % 60 + "초) " + ss + " " + t + (if (nm.isEmpty()) "" else " - $nm")) }
                GigiNokeum.pyosi(jt, ofs, ss, nm, t)
            }
            return Dap.json("{\"ok\":true,\"pail\":\"" + jsn2(pail) + "\",\"bun\":" + ofs / 60 + ",\"cho\":" + ofs % 60 + "}")
        }
        if (gil == "/nokeum_sangtae") {
            var su = 0; var ol = 0
            try { if (c != null) for (h in Haengsa.mokrok(c)) { su++; if (h.olim) ol++ } } catch (_: Exception) { }
            val jigeum = GigiNokeum.sid.isNotEmpty()
            if (jigeum) su++
            val bun = if (jigeum) GigiNokeum.sseunBaiteu / 16000 / 60 else 0L
            val mal = nokOlrimMal.ifEmpty { Haengsa.majimakMal }
            return Dap.json("{\"su\":$su,\"olrim\":$ol,\"jigeum\":$jigeum,\"bun\":$bun,\"olrinun\":" + (Haengsa.dolgo || galOlrinun) + ",\"mal\":\"" + jsn2(mal) + "\"}")
        }
        if (gil == "/nokeum_olligi") {
            if (!heoga) return Dap.json("{\"ok\":false}", 403)
            if (c != null) {
                nokOlrimMal = if (Bang.kyeojim) "지금 행사 녹음은 방송을 멈추면 LVD 방송 다시 듣기에 저절로 올립니다. 앞서 담아 둔 것은 지금 올립니다."
                    else "다시 듣기에 올리고 있습니다."
                Haengsa.sijak(c, false)
                galOlligiDwi(c)
            }
            return Dap.json("{\"ok\":true}")
        }
        return Dap.json("{\"ok\":false}", 404)
    }

    // ───── 무대 카메라와 줌(노트북판 KameraCheori) ─────
    private fun sutja(s: String, d: Double): Double = s.toDoubleOrNull() ?: d
    private fun kamera(gil: String, q: String, method: String, body: ByteArray, heoga: Boolean): Dap {
        val t = now()
        val c = gap(q, "c").let { if (it == "1" || it == "2" || it == "3") it else "1" }
        if (gil == "/kamera") {
            if (!heoga) return Dap.json("{\"ok\":false}", 403)
            if (method != "POST" || body.size < 100 || body.size > 3_000_000) return Dap.json("{\"ok\":false}", 400)
            val nm = jar(pul(gap(q, "nm")), 20)
            val zmin = sutja(gap(q, "zmin"), 0.0); val zmax = sutja(gap(q, "zmax"), 0.0); val z = sutja(gap(q, "z"), 0.0)
            var zreq = -1.0; var zseq = 0
            synchronized(garak) {
                val old = kamMok[c]
                if (old != null) { zreq = old.zreq; zseq = old.zseq }
                kamMok[c] = Kamera(body, t, nm, zmin, zmax, z, zreq, zseq)
            }
            return Dap.json("{\"ok\":true,\"zreq\":" + ds(zreq) + ",\"zseq\":$zseq}")
        }
        if (gil == "/kamera_zoom") {
            if (!heoga) return Dap.json("{\"ok\":false}", 403)
            val d = gap(q, "d"); var nz = -1.0; var mx = 0.0; var mn = 0.0; var ok = false
            synchronized(garak) {
                val v = kamMok[c]
                if (v != null && v.zmax > v.zmin) {
                    mn = v.zmin; mx = v.zmax
                    val cur = if (v.zreq > 0) v.zreq else if (v.z > 0) v.z else mn
                    nz = when (d) { "in" -> cur * 1.5; "out" -> cur / 1.5; else -> mn }
                    if (nz > mx) nz = mx; if (nz < mn) nz = mn
                    v.zreq = nz; v.zseq++; ok = true
                }
            }
            return Dap.json("{\"ok\":$ok,\"z\":" + ds(nz) + ",\"max\":" + ds(mx) + ",\"min\":" + ds(mn) + "}")
        }
        if (gil == "/kamera_bogi") {
            val img = synchronized(garak) { kamMok[c]?.takeIf { t - it.ttae < 10_000 }?.jpeg }
            return if (img == null) Dap.json("{\"ok\":false}", 404) else Dap(200, "image/jpeg", img)
        }
        if (gil == "/kamera_mok") {
            val sb = StringBuilder()
            synchronized(garak) {
                for ((k, v) in kamMok) {
                    if (t - v.ttae > 10_000) continue
                    if (sb.isNotEmpty()) sb.append(",")
                    sb.append("{\"c\":\"").append(k).append("\",\"nm\":\"").append(jsn2(v.nm)).append("\",\"zmin\":").append(ds(v.zmin)).append(",\"zmax\":").append(ds(v.zmax)).append(",\"z\":").append(ds(v.z)).append('}')
                }
            }
            return Dap.json("{\"mok\":[$sb]}")
        }
        return Dap.json("{\"ok\":false}", 404)
    }

    // ───── 관계자 붙이기(노트북판 GwanCheori) ─────
    private fun gwanIreum(r: String) = when (r) { "haeseol" -> "해설사"; "jinhaeng" -> "진행요원"; "kamera" -> "무대 카메라"; "pd" -> "피디 보조"; else -> r }
    private fun gwan(gil: String, q: String, heoga: Boolean, host: String): Dap {
        if (gil == "/gwan_qr") {
            if (!heoga) return Dap.json("{\"ok\":false}", 403)
            if (gap(q, "sae") == "1") synchronized(garak) { gwanKey = nanseu(8); gwanMok.clear() }
            // 그냥 문(8080)의 주소로 — 자물쇠 문으로 물어 와도 붙는 화면은 그냥 문에서 엶
            var h = hostMan(host)
            if (h.isEmpty() || h.startsWith("localhost") || h.startsWith("127.")) h = ponJuso()
            return Dap.json("{\"ok\":true,\"juso\":\"http://" + jsn2(h) + ":" + Bang.PORT + "/gwan?gk=" + gwanKeyGet() + "\"}")
        }
        if (gil == "/gwan_deung") {
            val gk = gap(q, "gk"); val r = gap(q, "r"); val nm = jar(pul(gap(q, "nm")), 20)
            if (gk.isEmpty() || gk != gwanKeyGet()) return Dap.json("{\"ok\":false,\"mal\":\"큐알이 바뀌었습니다. 피디 화면의 큐알을 다시 찍어 주십시오.\"}")
            if (r != "haeseol" && r != "jinhaeng" && r != "kamera" && r != "pd") return Dap.json("{\"ok\":false,\"mal\":\"역할을 골라 주십시오.\"}")
            if (nm.trim().isEmpty()) return Dap.json("{\"ok\":false,\"mal\":\"이름을 적어 주십시오.\"}")
            val tok = nanseu(16); val t = now().toString()
            synchronized(garak) { gwanMok[tok] = arrayOf(r, nm.trim(), t, t) }
            dotgi("gwangye_" + nal() + ".csv", ts() + "," + gwanIreum(r) + "," + nm.replace(",", " ") + "\r\n")
            return Dap.json("{\"ok\":true,\"tok\":\"$tok\",\"r\":\"$r\",\"yeokhal\":\"" + gwanIreum(r) + "\"}")
        }
        if (gil == "/gwan_mok") {
            if (!heoga) return Dap.json("{\"ok\":false}", 403)
            val sb = StringBuilder(); val t = now()
            synchronized(garak) {
                for (v in gwanMok.values) {
                    val c = v[2].toLongOrNull() ?: 0L; val m = v[3].toLongOrNull() ?: 0L
                    if (sb.isNotEmpty()) sb.append(",")
                    sb.append("{\"yeokhal\":\"").append(gwanIreum(v[0])).append("\",\"nm\":\"").append(jsn2(v[1])).append("\",\"cheo\":\"").append(pyo("HH:mm", c)).append("\",\"jigeum\":").append(t - m < 90_000).append('}')
                }
            }
            return Dap.json("{\"ok\":true,\"mok\":[$sb]}")
        }
        return Dap.json("{\"ok\":false}", 404)
    }

    // ───── 관계자 무전(노트북판 MujeonCheori) ─────
    private fun gwanNaeTok(q: String): String { val t = gap(q, "gt"); return if (t.isEmpty()) gap(q, "t") else t }
    private fun gwanNaeIreum(q: String, loop: Boolean): String {
        val t = gwanNaeTok(q)
        synchronized(garak) { val v = if (t.isNotEmpty()) gwanMok[t] else null; if (v != null) return gwanIreum(v[0]) + " " + v[1] }
        return if (loop) "피디(송출 기기)" else "피디"
    }
    private fun mujeon(gil: String, q: String, method: String, body: ByteArray, heoga: Boolean, loop: Boolean): Dap {
        if (!heoga) return Dap.json(GWAN_403, 403)
        val nae = gwanNaeTok(q).ifEmpty { "pd" }
        val t = now()
        if (gil == "/mj_bonae" || gil == "/mj_hochul") {
            val to = gap(q, "to").ifEmpty { "ALL" }
            var mime = pul(gap(q, "mime")); if (mime.length > 40 || !mime.startsWith("audio")) mime = "audio/webm"
            var b: ByteArray? = null
            if (gil == "/mj_bonae") {
                if (method != "POST" || body.size < 200 || body.size > 1_500_000) return Dap.json("{\"ok\":false}", 400)
                b = body
            }
            val nm = gwanNaeIreum(q, loop)
            var id = 0
            synchronized(garak) { mjSeq++; id = mjSeq; mjMok.add(Mj(id, t, nae, nm, to, if (gil == "/mj_hochul") "hochul" else "mal", mime, b)); if (mjMok.size > 40) mjMok.removeAt(0) }
            return Dap.json("{\"ok\":true,\"id\":$id}")
        }
        if (gil == "/mj_mok") {
            val after = gap(q, "after").toIntOrNull() ?: 0
            val sb = StringBuilder(); var maji = 0
            synchronized(garak) {
                for (m in mjMok) {
                    if (m.id <= after) continue
                    if (m.to != "ALL" && m.to != nae && m.nae != nae) continue
                    if (sb.isNotEmpty()) sb.append(",")
                    sb.append("{\"id\":").append(m.id).append(",\"t\":\"").append(pyo("HH:mm:ss", m.ttae)).append("\",\"nm\":\"").append(jsn2(m.nm)).append("\",\"nae\":").append(m.nae == nae)
                        .append(",\"modu\":").append(m.to == "ALL").append(",\"jong\":\"").append(m.jong).append("\"}")
                }
                maji = mjSeq
            }
            return Dap.json("{\"seq\":$maji,\"mok\":[$sb]}")
        }
        if (gil == "/mj_deul") {
            val id = gap(q, "id").toIntOrNull() ?: 0
            var b: ByteArray? = null; var mime = "audio/webm"
            synchronized(garak) { for (m in mjMok) if (m.id == id && (m.to == "ALL" || m.to == nae || m.nae == nae)) { b = m.b; mime = m.mime } }
            val bb = b ?: return Dap.json("{\"ok\":false}", 404)
            return Dap(200, mime, bb)
        }
        if (gil == "/mj_saram") {
            val sb = StringBuilder()
            synchronized(garak) {
                for ((k, v) in gwanMok) {
                    if (k == nae) continue
                    if (sb.isNotEmpty()) sb.append(",")
                    sb.append("{\"tok\":\"").append(k).append("\",\"nm\":\"").append(jsn2(gwanIreum(v[0]) + " " + v[1])).append("\",\"jigeum\":").append(t - (v[3].toLongOrNull() ?: 0L) < 90_000).append('}')
                }
            }
            if (nae != "pd") { if (sb.isNotEmpty()) sb.append(","); sb.append("{\"tok\":\"pd\",\"nm\":\"피디(송출 기기)\",\"jigeum\":true}") }
            return Dap.json("{\"mok\":[$sb]}")
        }
        return Dap.json("{\"ok\":false}", 404)
    }

    // ───── 라. 만족도 묻기 ─────
    private fun manjok(gil: String, q: String, heoga: Boolean): Dap {
        if (gil == "/manjok") {
            val i = gap(q, "i")
            if (i.isEmpty() || i.length > 40) return Dap.json("{\"ok\":false}", 400)
            val p = gap(q, "p").toIntOrNull()
            if (p == null || p !in 1..5) return Dap.json("{\"ok\":false}", 400)
            var n = gap(q, "n"); if (!susja(n)) n = "" else n = n.trimStart('0').ifEmpty { "0" }
            val t = jar(pul(gap(q, "t")).trim(), 200)
            val jt = now()
            var seq = 0
            synchronized(garak) {
                seq = manjokSeq
                if (seq == 0) return Dap.json("{\"ok\":false}")
                manjokDap["$seq|$i"] = Manjok(seq, n, p, t, jt)   // 같은 폰·같은 물음은 덮어씀
                if (manjokDap.size > 6000) { val ite = manjokDap.keys.iterator(); ite.next(); ite.remove() }
            }
            dotgi("manjok_" + nal() + ".csv", ts(jt) + "," + seq + "," + n + "," + p + "," + t.replace(",", " ").replace("\r", " ").replace("\n", " ") + "\r\n")
            return Dap.json("{\"ok\":true}")
        }
        if (!heoga) return Dap.json(GWAN_403, 403)
        if (gil == "/manjok_sijak") {
            var seq = 0; var mq = ""
            synchronized(garak) {
                if (gap(q, "kkeut") == "1") manjokQ = ""
                else {
                    manjokQ = jar(pul(gap(q, "q")).trim(), 100).ifEmpty { MANJOK_GIBON }
                    manjokSeq++
                    manjokMuleum[manjokSeq] = manjokQ
                }
                seq = manjokSeq; mq = manjokQ
            }
            return Dap.json("{\"ok\":true,\"seq\":$seq,\"q\":\"" + jsn2(mq) + "\"}")
        }
        // /manjok_mok — 지금(또는 마지막) 물음의 결과
        val sb = StringBuilder()
        synchronized(garak) {
            val seq = manjokSeq
            val gak = IntArray(5); var su = 0; var hap = 0
            val mal = StringBuilder()
            for (d in manjokDap.values) {
                if (d.seq != seq) continue
                su++; hap += d.p; gak[d.p - 1]++
                if (d.t.isNotEmpty()) { if (mal.isNotEmpty()) mal.append(','); mal.append("{\"n\":\"").append(jsn2(d.n)).append("\",\"p\":").append(d.p).append(",\"t\":\"").append(jsn2(d.t)).append("\"}") }
            }
            val pyeong = if (su > 0) String.format(Locale.US, "%.1f", hap.toDouble() / su) else "0"
            sb.append("{\"seq\":").append(seq).append(",\"q\":\"").append(jsn2(manjokMuleum[seq] ?: manjokQ)).append("\",\"su\":").append(su)
                .append(",\"pyeong\":").append(pyeong).append(",\"gak\":[").append(gak.joinToString(",")).append("],\"mal\":[").append(mal).append("]}")
        }
        return Dap.json(sb.toString())
    }

    // ───── 결과보고(노트북판 BogoJul·BogoCheori) — 지금 앱의 결과보고(bogo.txt)에 현장 기능 칸을 더함 ─────
    private fun jaryoGap(json: String, key: String): String {
        val i = json.indexOf("\"" + key + "\""); if (i < 0) return ""
        val a = json.indexOf('"', json.indexOf(':', i) + 1); if (a < 0) return ""
        val b = StringBuilder()
        var k = a + 1
        while (k < json.length) {
            val c = json[k]
            if (c == '\\' && k + 1 < json.length) { k++; b.append(if (json[k] == 'n') '\n' else json[k]); k++; continue }
            if (c == '"') break
            b.append(c); k++
        }
        return b.toString()
    }

    /** 현장 기능 칸(입장 번호 청취·요청·추첨·식순과 순간 표시·여러 갈래·구역별·만족도·긴급 안내·관계자) — 줄마다 \n */
    fun bogoDeohal(): String {
        val L = ArrayList<String>()
        val t = now()
        val jaryo = try { jaryoPail()?.takeIf { it.isFile }?.readText(Charsets.UTF_8) ?: "" } catch (_: Exception) { "" }
        synchronized(garak) {
            L.add("")
            L.add("현장 기능 결과")
            val jm = jaryoGap(jaryo, "jemok")
            if (jm.isNotEmpty()) L.add("해설 자료의 행사 제목: $jm")
            L.add("보고서 만든 때: " + pyo("yyyy년 M월 d일 HH:mm", t))
            L.add("")
            L.add("1. 입장 번호로 들으신 분")
            var su = 0; var hap = 0.0; var a30 = 0; var a60 = 0; var a60p = 0
            for (v in bhMok.values) { su++; val m = (v[1] - v[0]) / 60_000.0; hap += m; if (m < 30) a30++ else if (m < 60) a60++ else a60p++ }
            deutSegi()
            L.add("입장 번호로 들으신 분: " + su + "명")
            if (su > 0) L.add("한 분이 들으신 시간: 평균 " + Math.round(hap / su) + "분 (30분 안 " + a30 + "명, 30분~1시간 " + a60 + "명, 1시간 넘게 " + a60p + "명)")
            L.add("가장 많이 함께 들은 때: " + deutChoeda + "명" + (if (deutChoedaT == 0L) "" else " (" + pyo("HH:mm", deutChoedaT) + ")"))
            L.add("")
            L.add("2. 구역별 인원")
            val gy = java.util.TreeMap<String, Int>()
            for (k in bhMok.keys) { val g = bhGuyeok[k]; if (!g.isNullOrEmpty()) gy[g] = (gy[g] ?: 0) + 1 }
            if (gy.isEmpty()) L.add("구역이 적힌 번호 카드 없음") else for ((g, n) in gy) L.add("  " + g + " 구역 " + n + "명")
            L.add("")
            L.add("3. 듣는 분 요청")
            var dw = 0; var dwc = 0; val jong = LinkedHashMap<String, Int>(); val dwMok = ArrayList<String>()
            for (y in ycMok) {
                if (y[3] == "dowum") { dw++; if (y[4] == "1") dwc++; dwMok.add(y[1] + " " + (if (y[2].isEmpty()) "번호 없음" else y[2] + "번") + (if (y[4] == "1") " (도와드림)" else " (확인 안 됨)")) }
                else jong[y[3]] = (jong[y[3]] ?: 0) + 1
            }
            L.add("도움 요청: " + dw + "건 (도와드림 " + dwc + "건)")
            for (d in dwMok) L.add("  $d")
            if (jong.isEmpty()) L.add("현장영상해설사에게 한 요청: 없음")
            else { L.add("현장영상해설사에게 한 요청:"); for ((k, v) in jong) L.add("  " + ycIreum(k) + " " + v + "건") }
            L.add("")
            L.add("4. 행운권 추첨")
            if (dangGirok.isEmpty()) L.add("추첨 없음") else for (d in dangGirok) L.add("  $d")
            L.add("")
            L.add("5. 식순과 중요한 순간 표시")
            val si = jaryo.indexOf("\"sunseo\""); val sj = if (si >= 0) jaryo.indexOf(']', si) else -1
            if (si >= 0 && sj > si) {
                val part = jaryo.substring(si, sj)
                var k = 0; var cnt = 0
                while (true) { k = part.indexOf("\"n\"", k); if (k < 0) break; cnt++; L.add("  식순 " + cnt + ". " + jaryoGap(part.substring(k), "n")); k += 3 }
            }
            if (pyosiMok.isEmpty()) L.add("  순간 표시 없음") else for (p in pyosiMok) L.add("  표시 $p")
            L.add("")
            L.add("6. 여러 갈래 현장영상해설")
            for (g in 1..3) {
                val b = galBaiteu[g]
                if (g > 1 && b == 0L) continue
                L.add("  갈래 " + g + " " + galIreum[g] + ": 해설 " + (b / 16000 / 60) + "분 " + (b / 16000 % 60) + "초, 가장 많이 들은 때 " + galChoeda[g] + "명" + (if (g == 1) " (이 기기 사운드카드)" else ""))
            }
            L.add("")
            L.add("7. 만족도")
            if (manjokMuleum.isEmpty()) L.add("묻지 않음")
            for ((seq, mq) in manjokMuleum) {
                val gak = IntArray(5); var n = 0; var h = 0; val mal = ArrayList<String>()
                for (d in manjokDap.values) if (d.seq == seq) { n++; h += d.p; gak[d.p - 1]++; if (d.t.isNotEmpty()) mal.add((if (d.n.isEmpty()) "번호 없음" else d.n + "번") + " " + d.p + "점: " + d.t) }
                L.add("물음 $seq: $mq")
                L.add("  답한 분 " + n + "명" + (if (n > 0) ", 평균 " + String.format(Locale.US, "%.1f", h.toDouble() / n) + "점" else ""))
                if (n > 0) L.add("  매우 좋음(5점) " + gak[4] + "명, 4점 " + gak[3] + "명, 3점 " + gak[2] + "명, 2점 " + gak[1] + "명, 매우 아쉬움(1점) " + gak[0] + "명")
                if (mal.isNotEmpty()) { L.add("  한마디:"); for (x in mal) L.add("    $x") }
            }
            L.add("")
            L.add("8. 긴급 안내")
            if (ginGirok.isEmpty()) L.add("없음") else for (x in ginGirok) L.add("  $x")
            L.add("")
            L.add("9. 관계자")
            if (gwanMok.isEmpty()) L.add("붙은 관계자 없음")
            else for (v in gwanMok.values) L.add("  " + gwanIreum(v[0]) + " " + v[1] + " (" + pyo("HH:mm", v[2].toLongOrNull() ?: 0L) + "부터)")
        }
        L.add("")
        L.add("참고: 이 보고서는 입장 번호만 모았으며 이름과 전화번호 같은 개인정보는 모으지 않았습니다.")
        return L.joinToString("\n") + "\n"
    }

    /** 지금 결과보고 전체 줄(앞: 지금 앱의 결과보고, 뒤: 현장 기능 칸) */
    private fun bogoJul(): List<String> {
        val ap = GigiNokeum.bogoJigeum().ifEmpty { "현장영상해설 방송 결과보고\n사단법인 한국시각장애인현장영상해설협회\n\n행사 녹음: 지금 녹음 중인 행사가 없습니다.\n" }
        return (ap.trimEnd('\n') + "\n" + bogoDeohal()).trimEnd('\n').split("\n")
    }

    private fun bogoCheori(gil: String, heoga: Boolean): Dap {
        if (!heoga) return Dap.geul(403, "text/plain; charset=utf-8", "피디 화면에서만 열 수 있습니다.")
        val L = bogoJul()
        if (gil == "/bogoseo.txt") return Dap(200, "text/plain; charset=euc-kr", euckr(L.joinToString("\r\n") + "\r\n"))
        val h = StringBuilder()
        h.append("<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>현장영상해설 방송 결과보고</title><style>body{font-family:'Malgun Gothic',sans-serif;max-width:180mm;margin:10mm auto;padding:0 12px;font-size:11pt;line-height:1.7}h1{font-size:18pt}p{margin:0}@page{size:A4;margin:15mm}@media print{.an{display:none}}</style></head><body><p class=\"an\"><button onclick=\"print()\" style=\"font-size:18px;padding:12px\">인쇄하기</button> <a href=\"/bogoseo.txt\" download=\"결과보고.txt\" style=\"font-size:18px\">텍스트로 저장</a></p>")
        var first = true
        for (l in L) {
            val e = l.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            if (first) { h.append("<h1>").append(e).append("</h1>"); first = false; continue }
            if (e.length > 2 && e[0].isDigit() && e[1] == '.') h.append("<h2 style=\"font-size:13pt;margin:10pt 0 4pt\">").append(e).append("</h2>")
            else if (e == "현장 기능 결과") h.append("<h2 style=\"font-size:15pt;margin:14pt 0 4pt\">").append(e).append("</h2>")
            else if (e.isEmpty()) continue
            else h.append("<p>").append(e.replace("  ", "&nbsp;&nbsp;")).append("</p>")
        }
        h.append("</body></html>")
        return Dap.geul(200, "text/html; charset=utf-8", h.toString())
    }

    private fun euckr(t: String): ByteArray = try { t.toByteArray(charset("EUC-KR")) } catch (_: Exception) { byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + t.toByteArray(Charsets.UTF_8) }

    // ───── 1분마다 저절로 저장(노트북판 2.0판) — 임시로 쓴 뒤 이름 바꾸기, 앞 것은 .bak ─────
    private fun anjeonSseugi(f: File, b: ByteArray) {
        if (jgNow()) return   // 스스로 점검 중에는 저장하지 않음
        val tmp = File(f.path + ".tmp")
        FileOutputStream(tmp).use { o -> o.write(b); o.flush(); o.fd.sync() }
        if (f.isFile) { val bak = File(f.path + ".bak"); bak.delete(); f.renameTo(bak) }
        if (!tmp.renameTo(f)) { tmp.copyTo(f, overwrite = true); tmp.delete() }
    }

    private fun sangtaePail(d: File) = File(d, "sangtae_" + nal() + ".txt")

    private fun sangtaeGeul(): String {
        val sb = StringBuilder()
        synchronized(garak) {
            sb.append("V|1\n").append("T|").append(now()).append('\n')
            for ((k, v) in bhMok) sb.append("B|").append(k).append('|').append(v[0]).append('|').append(v[1]).append('\n')
            for ((k, v) in bhGuyeok) sb.append("Z|").append(k).append('|').append(ppae(v)).append('\n')
            for (y in ycMok) sb.append("Y|").append(y[0]).append('|').append(y[1]).append('|').append(y[2]).append('|').append(y[3]).append('|').append(y[4]).append('|').append(y[5]).append('\n')
            for (d in dangMok) sb.append("D|").append(d).append('\n')
            for (d in dangGirok) sb.append("L|").append(ppae(d)).append('\n')
            for ((k, v) in gwanMok) sb.append("G|").append(k).append('|').append(v[0]).append('|').append(ppae(v[1])).append('|').append(v[2]).append('|').append(v[3]).append('\n')
            sb.append("K|").append(gwanKey).append('\n')
            sb.append("S|").append(dangSeq).append('|').append(dangcheom).append('|').append(dangSik).append('\n')
            sb.append("C|").append(deutChoeda).append('|').append(deutChoedaT).append('\n')
            sb.append("Q|").append(ycSeq).append('\n')
            sb.append("O|").append(sunseoI).append('|').append(sunseoT).append('\n')
            for (x in ginGirok) sb.append("F|").append(ppae(x)).append('\n')
            for (x in pyosiMok) sb.append("P|").append(ppae(x)).append('\n')
            for (g in 1..3) sb.append("N|").append(g).append('|').append(ppae(galIreum[g])).append('|').append(galChoeda[g]).append('|').append(galBaiteu[g]).append('\n')
            sb.append("M|").append(manjokSeq).append('|').append(ppae(manjokQ)).append('\n')
            for ((s, mq) in manjokMuleum) sb.append("W|").append(s).append('|').append(ppae(mq)).append('\n')
            for ((k, d) in manjokDap) sb.append("A|").append(d.seq).append('|').append(ppae(k.substringAfter('|'))).append('|').append(d.n).append('|').append(d.p).append('|').append(d.ttae).append('|').append(ppae(d.t)).append('\n')
        }
        return sb.toString()
    }

    private fun sangtaeBulleo() {
        val d = gojeongTeo() ?: return
        var p = sangtaePail(d)
        if (!p.isFile) { p = File(p.path + ".bak"); if (!p.isFile) return }
        val L = p.readLines(Charsets.UTF_8)
        val saved = L.firstOrNull { it.startsWith("T|") }?.substring(2)?.toLongOrNull() ?: p.lastModified()
        if (now() - saved > IEUM_HANDO) return   // 3시간 넘게 지난 것은 다른 행사로 봄
        var n = 0
        synchronized(garak) {
            if (bhMok.isNotEmpty() || ycMok.isNotEmpty() || dangMok.isNotEmpty() || manjokSeq > 0) return
            for (l in L) {
                val f = l.split('|')
                if (f.size < 2) continue
                try {
                    when (f[0]) {
                        "B" -> if (f.size >= 4) { bhMok[f[1]] = longArrayOf(f[2].toLong(), f[3].toLong()); n++ }
                        "Z" -> if (f.size >= 3) bhGuyeok[f[1]] = f[2]
                        "Y" -> if (f.size >= 7) ycMok.add(arrayOf(f[1], f[2], f[3], f[4], f[5], f[6]))
                        "D" -> dangMok.add(f[1])
                        "L" -> dangGirok.add(f[1])
                        "G" -> if (f.size >= 6) gwanMok[f[1]] = arrayOf(f[2], f[3], f[4], f[5])
                        "K" -> gwanKey = f[1]
                        "S" -> if (f.size >= 4) { dangSeq = f[1].toInt(); dangcheom = f[2]; dangSik = f[3] }
                        "C" -> if (f.size >= 3) { deutChoeda = f[1].toInt(); deutChoedaT = f[2].toLong() }
                        "Q" -> ycSeq = f[1].toInt()
                        "O" -> if (f.size >= 3) { sunseoI = f[1].toInt(); sunseoT = f[2].toLong() }
                        "F" -> ginGirok.add(f[1])
                        "P" -> pyosiMok.add(f[1])
                        "N" -> if (f.size >= 5) { val g = f[1].toInt(); if (g in 1..3) { galIreum[g] = f[2]; galChoeda[g] = f[3].toInt(); galBaiteu[g] = f[4].toLong() } }
                        "M" -> if (f.size >= 3) { manjokSeq = f[1].toInt(); manjokQ = f[2] }
                        "W" -> if (f.size >= 3) manjokMuleum[f[1].toInt()] = f[2]
                        "A" -> if (f.size >= 7) { val s = f[1].toInt(); manjokDap["$s|" + f[2]] = Manjok(s, f[3], f[4].toInt(), f[6], f[5].toLong()) }
                    }
                } catch (_: Exception) { }
            }
        }
        jadongIeo = "앞서 저장된 상황을 이어 받았습니다(입장 번호 " + n + "개)"
        jadongMal = jadongIeo
        majimakJeojang = saved
    }

    private fun jadongJeojang() {
        if (jgNow()) return
        try {
            val b = sangtaeGeul().toByteArray(Charsets.UTF_8)
            gojeongTeo()?.let { anjeonSseugi(sangtaePail(it), b) }
            val hd = GigiNokeum.teoJigeum()
            if (hd != null) {
                anjeonSseugi(sangtaePail(hd), b)
                anjeonSseugi(File(hd, "결과보고_" + nal() + ".txt"), euckr(bogoJul().joinToString("\r\n") + "\r\n"))
            }
            majimakJeojang = now()
            jadongMal = (if (jadongIeo.isNotEmpty()) "$jadongIeo. " else "") + "마지막 저절로 저장 " + pyo("HH:mm:ss", now())
            if (jadongMal.length > 120) jadongMal = "마지막 저절로 저장 " + pyo("HH:mm:ss", now())
        } catch (e: Exception) { jadongMal = "저절로 저장 오류: " + (e.message ?: "") }
    }

    private fun jadongSijak() {
        val nae = ++jadongSeq
        Thread({
            try { Thread.sleep(5000) } catch (_: InterruptedException) { }
            while (nae == jadongSeq) {
                jadongJeojang()
                try { Thread.sleep(60_000) } catch (_: InterruptedException) { }
            }
        }, "byod-jadong").apply { isDaemon = true }.start()
    }

    // ───── 스스로 점검 담기와 되돌리기(노트북판 JgDamgi·JgDoerigi) ─────
    private class Bon(
        val dangMok: List<String>, val dangcheom: String, val dangSik: String, val dangSeq: Int, val dangGirok: List<String>,
        val sunseoI: Int, val sunseoT: Long, val ycMok: List<Array<String>>, val ycSeq: Int, val malMok: List<Array<String>>,
        val ycGeuje: Map<String, Long>, val kamMok: Map<String, Kamera>, val gwanMok: Map<String, Array<String>>, val mjMok: List<Mj>,
        val bhMok: Map<String, LongArray>, val bhGuyeok: Map<String, String>, val deutChoeda: Int, val deutChoedaT: Long, val ginMal: String,
        val ginGirok: List<String>, val pyosiMok: List<String>, val galIreum: Array<String>,
        val manjokQ: String, val manjokSeq: Int, val manjokMuleum: Map<Int, String>, val manjokDap: Map<String, Manjok>)

    private fun jgDamgi(): Boolean = try {
        synchronized(garak) {
            jgBon = Bon(ArrayList(dangMok), dangcheom, dangSik, dangSeq, ArrayList(dangGirok), sunseoI, sunseoT,
                ycMok.map { it.copyOf() }, ycSeq, malMok.map { it.copyOf() }, HashMap(ycGeuje),
                kamMok.mapValues { it.value.bok() }, gwanMok.mapValues { it.value.copyOf() }, ArrayList(mjMok),
                bhMok.mapValues { it.value.copyOf() }, HashMap(bhGuyeok), deutChoeda, deutChoedaT, Bang.ginMal,
                ArrayList(ginGirok), ArrayList(pyosiMok), galIreum.copyOf(),
                manjokQ, manjokSeq, LinkedHashMap(manjokMuleum), LinkedHashMap(manjokDap))
        }
        true
    } catch (_: Exception) { false }

    private fun jgDoerigi() {
        try {
            synchronized(garak) {
                val b = jgBon ?: return
                dangMok.clear(); dangMok.addAll(b.dangMok); dangcheom = b.dangcheom; dangSik = b.dangSik
                // 점검 중에 늘어난 당첨 순번은 그대로 둠(듣는 화면이 같은 순번을 다시 보지 않게 — 노트북판도 dangSeq 를 되돌리지 않음)
                dangGirok.clear(); dangGirok.addAll(b.dangGirok)
                sunseoI = b.sunseoI; sunseoT = b.sunseoT
                ycMok.clear(); ycMok.addAll(b.ycMok)
                malMok.clear(); malMok.addAll(b.malMok)
                ycGeuje.clear(); ycGeuje.putAll(b.ycGeuje)
                kamMok.clear(); kamMok.putAll(b.kamMok)
                gwanMok.clear(); gwanMok.putAll(b.gwanMok)
                mjMok.clear(); mjMok.addAll(b.mjMok)
                bhMok.clear(); bhMok.putAll(b.bhMok)
                bhGuyeok.clear(); bhGuyeok.putAll(b.bhGuyeok)
                deutChoeda = b.deutChoeda; deutChoedaT = b.deutChoedaT
                if (Bang.ginMal != b.ginMal) { Bang.ginMal = b.ginMal; Bang.ginSeq++ }
                ginGirok.clear(); ginGirok.addAll(b.ginGirok)
                pyosiMok.clear(); pyosiMok.addAll(b.pyosiMok)
                for (g in 0..3) galIreum[g] = b.galIreum[g]
                manjokQ = b.manjokQ   // 만족도 순번은 그대로 둠(듣는 폰이 점검 물음에 답한 기억과 겹치지 않게)
                manjokMuleum.clear(); manjokMuleum.putAll(b.manjokMuleum)
                manjokDap.clear(); manjokDap.putAll(b.manjokDap)
                val ol = deutMok.keys.filter { it.startsWith("jg") }; for (k in ol) deutMok.remove(k)
                val oj = jiyeon.keys.filter { it.startsWith("jg") }; for (k in oj) jiyeon.remove(k)
                jgBon = null
            }
            Bang.allyeo()
        } catch (_: Exception) { }
    }

    // ───── 가. 갈래 2·3 녹음 — 같은 행사 폴더에 gal2_0001.ul …(1분 조각, 10초마다 저장 장치에 확실히 씀) ─────
    private class GalNok { var f: FileOutputStream? = null; var sid = ""; var su = 0; var fTtae = 0L; var syncTtae = 0L }
    private val galNok = arrayOf(GalNok(), GalNok(), GalNok(), GalNok())

    private fun galDatgi(g: Int) {   // nokGarak 안에서 부름
        val x = galNok[g]
        val f = x.f ?: return
        try { f.flush(); f.fd.sync(); f.close() } catch (_: Exception) { }
        x.f = null
    }

    private fun galSseugi(g: Int, b: ByteArray) {
        if (jgNow()) return
        val d = GigiNokeum.teoJigeum() ?: return   // 행사 녹음이 열려 있을 때만(같은 행사 폴더에)
        synchronized(nokGarak) {
            val x = galNok[g]
            val t = now()
            try {
                if (x.f == null || x.sid != d.name || t - x.fTtae >= 60_000) {
                    galDatgi(g)
                    if (x.sid != d.name) {
                        x.sid = d.name
                        x.su = (d.listFiles() ?: emptyArray()).count { it.name.startsWith("gal${g}_") && it.name.endsWith(".ul") }
                        val sj = File(d, "gal${g}_sijak.txt")
                        if (!sj.isFile) sj.writeText(t.toString())
                    }
                    x.su++
                    x.f = FileOutputStream(File(d, String.format(Locale.US, "gal%d_%04d.ul", g, x.su)), true)
                    x.fTtae = t
                }
                x.f?.write(b)
                if (t - x.syncTtae >= 10_000) { x.syncTtae = t; x.f?.flush(); x.f?.fd?.sync() }
            } catch (_: Exception) { }
        }
    }

    // ───── 갈래 2·3 녹음과 기록을 협회 나스에 올리기(노트북판 녹음 올리기 길 nokeum.php 와 같은 꼴) ─────
    @Volatile private var galOlrinun = false

    /** 아직 나스에 못 올린 갈래 녹음이 있는가(방송 중인 행사는 뺌) */
    // 새방송클 261024: 갈래 녹음·기록은 행사 올리기(Haengsa.galPailOlligi, byod_olligi.php)로 함께 올리므로 이 길은 쓰지 않음(아이폰·플린트 2와 같게)
    fun galNameum(ctx: Context): Boolean = false
    private fun galNameumYet(ctx: Context): Boolean = try {
        (Haengsa.teo(ctx).listFiles() ?: emptyArray()).any { d ->
            d.isDirectory && d.name != GigiNokeum.sid && (2..3).any { g -> !File(d, "gal$g.olrim").isFile && (d.listFiles() ?: emptyArray()).any { it.name.startsWith("gal${g}_") && it.name.endsWith(".ul") } }
        }
    } catch (_: Exception) { false }

    /** 뒤에서 올리기(한 번에 하나만) */
    @Suppress("UNUSED_PARAMETER")
    fun galOlligiDwi(ctx: Context) { }   // 쓰지 않음(위 말 참고) — 옛 길(nokeum.php)은 galOlligiYet 로 남겨 둠

    @Suppress("unused")
    private fun galOlligiYet(ctx: Context) {
        if (galOlrinun) return
        galOlrinun = true
        val c = ctx.applicationContext
        Thread({ try { galOlligi(c) } catch (_: Exception) { } finally { galOlrinun = false } }, "byod-gal-olligi").start()
    }

    private fun galOlligi(ctx: Context) {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        var mang: Network? = null
        val l = CountDownLatch(1)
        val cb = object : ConnectivityManager.NetworkCallback() { override fun onAvailable(network: Network) { mang = network; l.countDown() } }
        try {
            cm.requestNetwork(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), cb)
            l.await(8, TimeUnit.SECONDS)
        } catch (_: Exception) { }
        var ok = 0; var mot = 0
        try {
            for (d in Haengsa.teo(ctx).listFiles() ?: emptyArray()) {
                if (!d.isDirectory || d.name == GigiNokeum.sid) continue
                val fs = (d.listFiles() ?: emptyArray()).sortedBy { it.name }
                for (g in 2..3) {
                    val olrimPyo = File(d, "gal$g.olrim")
                    val jogak = fs.filter { it.name.startsWith("gal${g}_") && it.name.endsWith(".ul") }
                    if (olrimPyo.isFile || jogak.isEmpty()) continue
                    val sijak = File(d, "gal${g}_sijak.txt").takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull() ?: jogak.first().lastModified()
                    val w = File(ctx.cacheDir, "nokeum_" + pyo("yyyyMMdd_HHmmss", sijak) + ".wav")
                    try {
                        wavMandeul(jogak, w)
                        if (nokPost(mang, w)) { olrimPyo.writeText(ts()); ok++ } else mot++
                    } catch (_: Exception) { mot++ } finally { w.delete() }
                }
                // 노트북판처럼 요청·순간 표시·만족도 기록(csv)도
                for (f in fs) {
                    val nm = f.name
                    if (!nm.endsWith(".csv") || !(nm.startsWith("yocheong_") || nm.startsWith("pyosi_") || nm.startsWith("manjok_"))) continue
                    val pf = File(d, "$nm.olrim")
                    if (pf.isFile) continue
                    try { if (nokPost(mang, f)) { pf.writeText(ts()); ok++ } else mot++ } catch (_: Exception) { mot++ }
                }
            }
        } finally {
            try { cm.unregisterNetworkCallback(cb) } catch (_: Exception) { }
        }
        if (ok > 0 || mot > 0) nokOlrimMal = "여러 갈래 녹음과 기록: 올린 파일 " + ok + "개" + (if (mot > 0) ", 못 올린 파일 " + mot + "개(인터넷 확인, 1분마다 다시 해 봄)" else "")
    }

    /** 뮤 조각들을 이어 노트북판과 같은 58바이트 머리(fact 칸 포함)의 웨이브로 */
    private fun wavMandeul(jogak: List<File>, w: File) {
        var n = 0L
        for (j in jogak) n += j.length()
        val d = n.toInt()
        val h = java.nio.ByteBuffer.allocate(58).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()); h.putInt(50 + d); h.put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()); h.putInt(18); h.putShort(7); h.putShort(1); h.putInt(16000); h.putInt(16000); h.putShort(1); h.putShort(8); h.putShort(0)
        h.put("fact".toByteArray()); h.putInt(4); h.putInt(d)
        h.put("data".toByteArray()); h.putInt(d)
        FileOutputStream(w).use { o -> o.write(h.array()); for (j in jogak) j.inputStream().use { it.copyTo(o, 65536) } }
    }

    /** 노트북판 WebClient.UploadFile 과 같은 꼴(multipart, 칸 이름 file)로 nokeum.php 에 올림 */
    private fun nokPost(mang: Network?, f: File): Boolean {
        val u = URL(NOK_URL)
        val c = (mang?.openConnection(u) ?: u.openConnection()) as HttpURLConnection
        val bd = "----------------lvd" + System.currentTimeMillis()
        val meori = ("--" + bd + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + f.name + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").toByteArray(Charsets.UTF_8)
        val kkori = ("\r\n--" + bd + "--\r\n").toByteArray(Charsets.UTF_8)
        c.connectTimeout = 10000; c.readTimeout = 180000; c.requestMethod = "POST"; c.doOutput = true; c.useCaches = false
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$bd")
        c.setFixedLengthStreamingMode(meori.size.toLong() + f.length() + kkori.size.toLong())
        c.outputStream.use { o -> o.write(meori); f.inputStream().use { it.copyTo(o, 65536) }; o.write(kkori) }
        val code = c.responseCode
        val t = try { c.inputStream.bufferedReader().readText() } catch (_: Exception) { "" }
        c.disconnect()
        return code in 200..299 && !t.contains("\"ok\":false")
    }

    // ───── 현장 점검(Hyeonjang.kt)과 첫 화면이 보는 것 ─────
    fun tlsMal(): String {
        val s = seobeo ?: return ""
        return if (s.tport > 0) "" else s.tlsOryu.ifEmpty { "열지 못함" }
    }
}
