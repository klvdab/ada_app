// LVD 송출 안드로이드 — 현장 방송을 인터넷 방송으로도 함께 내보내기 (1.5.0판, 빌드 261018-B13, 방클, 이사장님 승인 「1」 — LVD 방송 정리안)
// 아이폰 Byod/Inteonet.swift 와 같은 잣대입니다.
//   1. 현장 방송을 켜면, 같은 해설 소리(뮤 8비트, 1초에 16000)를 LVD 방송 중계(lvd.ada.or.kr:2053)로도 보냄
//      방을 열 때 현장영상해설사 전화번호를 협회 명단에 대조(인터넷 방송 하기 화면과 같음)
//   2. 2초마다 한 덩이, 첫 덩이 앞에 웨이브 머리(audio/wav) — 중계가 듣는 분께 mp3 로 바꿔 흘려 줌
//   3. 플린트 2 공유기 와이파이에는 인터넷이 없으므로 모바일 데이터(셀룰러) 망을 청해 그리로 보냄, 없으면 기본 망
//   4. 인터넷이 안 되어도 현장 방송은 그대로. 상태는 첫 화면 한 줄로 바뀔 때만
//   5. 끝나면 방을 닫고, 플린트 2 방식이면 공유기 녹음이 올라간 뒤 같은 때 인터넷 녹음은 다시 듣기 목록에서 감춤(지우지 않음)
package kr.or.ada.app.byod

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.HandlerThread
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Inteonet {
    const val JUNGYE = "https://lvd.ada.or.kr:2053"
    const val RATE = 16000

    private fun pref(ctx: Context) = ctx.getSharedPreferences("byod", Context.MODE_PRIVATE)
    fun hamkke(ctx: Context): Boolean = pref(ctx).getBoolean("inteonet_hamkke", true)
    fun hamkkeDugi(ctx: Context, on: Boolean) { pref(ctx).edit().putBoolean("inteonet_hamkke", on).apply() }
    fun jeonhwa(ctx: Context): String = (pref(ctx).getString("haeseolsa_phone", "") ?: "").filter { it.isDigit() }

    /** 첫 화면 한 줄(없으면 빈 글) */
    @Volatile var jul = ""

    private var th: HandlerThread? = null
    private var h: Handler? = null
    // 아래는 h 줄기에서만 만짐
    private var app: Context? = null
    private var dolgo = false
    private var yeollim = false
    private var geobu = false
    private var sid = ""
    private var tok = ""
    private val moum = ByteArrayOutputStream()
    private var cheot = true
    private var flintBangsik = false
    private var silpae = 0
    private var yeolgiTtae = 0L
    private var yeollinTtae = 0L
    private var bonaenCho = 0.0
    private var jemok = ""
    private var who = ""
    private var phone = ""
    private var mang: Network? = null
    private var mangCb: ConnectivityManager.NetworkCallback? = null

    private fun allim(t: String) { if (jul != t) { jul = t; Bang.allyeo() } }
    private fun mal(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun nanSu(n: Int): String { val g = "abcdefghijklmnopqrstuvwxyz0123456789"; return (1..n).map { g.random() }.joinToString("") }

    fun sijak(ctx: Context, jm: String, wh: String) {
        val c = ctx.applicationContext
        if (!hamkke(c)) { allim("인터넷 방송: 꺼 두셨습니다. 현장 방송만 나갑니다. 더 보기에서 켤 수 있습니다."); return }
        val p = jeonhwa(c)
        if (p.length < 9) { allim("인터넷 방송: 첫 화면에 현장영상해설사 전화번호를 넣으시면 현장 방송과 함께 인터넷으로도 나갑니다."); return }
        val t = th ?: HandlerThread("byod-inteonet").also { it.start(); th = it }
        val hh = h ?: Handler(t.looper).also { h = it }
        hh.post {
            if (dolgo) return@post
            app = c; dolgo = true; yeollim = false; geobu = false
            jemok = jm; who = wh; phone = p
            flintBangsik = Flint.flintInga(c)
            silpae = 0; bonaenCho = 0.0; moum.reset(); cheot = true; yeolgiTtae = 0L
            mangChonggi(c)
            allim("인터넷 방송: 중계에 방을 열고 있습니다.")
            yeolgi()
            hh.postDelayed(dolgiRun, 2000)
        }
    }

    fun neoki(b: ByteArray) {
        val hh = h ?: return
        hh.post {
            if (!dolgo || geobu) return@post
            moum.write(b)
            val mak = RATE * 10   // 10초 넘게 밀리면 오래된 것을 버림
            if (moum.size() > mak) { val a = moum.toByteArray(); moum.reset(); moum.write(a, a.size - mak, mak) }
        }
    }

    fun meomchugi() {
        val hh = h ?: return
        hh.post {
            if (!dolgo) return@post
            dolgo = false
            hh.removeCallbacks(dolgiRun)
            val s = sid; val t = tok; val yeol = yeollim
            val nameun = moum.toByteArray(); val cheo = cheot
            moum.reset(); yeollim = false
            if (yeol) {
                if (nameun.isNotEmpty()) yocheong("POST", "/jogak?sid=$s&tok=$t", if (cheo) wavMeori() + nameun else nameun)
                yocheong("GET", "/datgi?sid=$s&tok=$t", null)
                if (flintBangsik && bonaenCho > 5) app?.let { GamchumDaegi.deohagi(it, s, yeollinTtae) }
            }
            mangPulgi()
            allim("")
        }
    }

    private val dolgiRun = object : Runnable {
        override fun run() {
            val hh = h ?: return
            if (!dolgo) return
            try { dolgi() } catch (_: Exception) { }
            if (dolgo) hh.postDelayed(this, 2000)
        }
    }

    private fun yeolgi() {
        if (!dolgo || geobu || System.currentTimeMillis() - yeolgiTtae < 12_000) return
        yeolgiTtae = System.currentTimeMillis()
        val s = "lvd" + nanSu(9); val tk = nanSu(10)
        val (code, mom) = yocheong("GET", "/yeolgi?sid=$s&tok=$tk&phone=$phone&mime=" + mal("audio/wav") + "&jong=live&title=" + mal(jemok) + "&who=" + mal(who), null)
        val j = try { mom?.let { JSONObject(String(it, Charsets.UTF_8)) } } catch (_: Exception) { null }
        if (code == 200 && j?.optBoolean("ok") == true) {
            sid = j.optString("sid", s).ifEmpty { s }; tok = j.optString("tok", tk).ifEmpty { tk }
            yeollim = true; cheot = true; silpae = 0; yeollinTtae = System.currentTimeMillis()
            val a = moum.toByteArray(); moum.reset(); val n = minOf(a.size, RATE); moum.write(a, a.size - n, n)   // 밀린 것은 1초만
            allim("인터넷 방송: 함께 나가고 있습니다. LVD 방송 앱과 누리집의 지금 현장영상해설 듣기에서 들립니다.")
        } else if (code == 403) {
            geobu = true; moum.reset()
            allim("인터넷 방송: 이 전화번호가 협회 현장영상해설사 명단에 없어 인터넷으로는 나가지 않습니다. 현장 방송은 그대로 나갑니다.")
        } else {
            silpae++
            if (silpae >= 2) { silpae = 0; app?.let { mangChonggi(it) } }
            allim("인터넷 방송: 인터넷에 닿지 않습니다. 저절로 다시 시도합니다. 현장 방송은 그대로 나갑니다.")
        }
    }

    private fun dolgi() {
        if (!dolgo || geobu) return
        if (!yeollim) { yeolgi(); return }
        if (moum.size() == 0) return
        val meori = cheot
        val so = moum.toByteArray(); moum.reset()
        val cho = so.size.toDouble() / RATE
        val s = sid
        val (code, _) = yocheong("POST", "/jogak?sid=$sid&tok=$tok", if (meori) wavMeori() + so else so)
        if (!dolgo || s != sid) return
        when (code) {
            200 -> {
                if (meori) cheot = false
                silpae = 0; bonaenCho += cho
                allim("인터넷 방송: 함께 나가고 있습니다. LVD 방송 앱과 누리집의 지금 현장영상해설 듣기에서 들립니다.")
            }
            404, 403 -> {   // 중계가 다시 켜져 방이 없어짐 — 새로 엶
                if (flintBangsik && bonaenCho > 5) app?.let { GamchumDaegi.deohagi(it, s, yeollinTtae) }
                yeollim = false; bonaenCho = 0.0; yeolgiTtae = 0L
                allim("인터넷 방송: 중계가 다시 켜져 방을 새로 엽니다.")
            }
            else -> {
                silpae++
                if (silpae >= 3) { silpae = 0; app?.let { mangChonggi(it) } }
                allim("인터넷 방송: 인터넷에 닿지 않습니다. 저절로 다시 시도합니다. 현장 방송은 그대로 나갑니다.")
            }
        }
    }

    /** 모바일 데이터 망을 청함(이미 있으면 그대로) — 플린트 2 와이파이에는 인터넷이 없음 */
    private fun mangChonggi(ctx: Context) {
        if (mang != null) return
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { h?.post { mang = network } }
            override fun onLost(network: Network) { h?.post { if (mang == network) mang = null } }
        }
        try {
            mangCb?.let { try { cm.unregisterNetworkCallback(it) } catch (_: Exception) { } }
            cm.requestNetwork(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), cb)
            mangCb = cb
        } catch (_: Exception) { }
    }

    private fun mangPulgi() {
        val c = app ?: return
        mangCb?.let { try { (c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(it) } catch (_: Exception) { } }
        mangCb = null; mang = null
    }

    /** 짧은 https 요청 — 모바일 데이터 망이 있으면 그 망으로, 없으면 기본 망. (상태 번호, 몸) */
    private fun yocheong(m: String, gil: String, mom: ByteArray?): Pair<Int, ByteArray?> = try {
        val u = URL(JUNGYE + gil)
        val c = (mang?.openConnection(u) ?: u.openConnection()) as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 10000; c.useCaches = false; c.requestMethod = m
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) LVDsongchul/" + Bang.PAN)
        if (mom != null) {
            c.doOutput = true; c.setRequestProperty("Content-Type", "application/octet-stream")
            c.setFixedLengthStreamingMode(mom.size)
            c.outputStream.use { it.write(mom) }
        }
        val code = c.responseCode
        val b = try { (if (code < 400) c.inputStream else c.errorStream)?.readBytes() } catch (_: Exception) { null }
        c.disconnect()
        Pair(code, b)
    } catch (_: Exception) { Pair(0, null) }

    /** 뮤 8비트 한 줄기 1초에 16000 웨이브 머리(길이는 모름 — 흘려보내는 방식) */
    fun wavMeori(): ByteArray {
        val o = ByteArrayOutputStream()
        fun u32(v: Long) { for (i in 0..3) o.write(((v shr (8 * i)) and 0xFF).toInt()) }
        fun u16(v: Int) { o.write(v and 0xFF); o.write((v shr 8) and 0xFF) }
        o.write("RIFF".toByteArray()); u32(0xFFFFFFFFL); o.write("WAVE".toByteArray())
        o.write("fmt ".toByteArray()); u32(16); u16(7); u16(1); u32(RATE.toLong()); u32(RATE.toLong()); u16(1); u16(8)
        o.write("data".toByteArray()); u32(0xFFFFFFFFL)
        return o.toByteArray()
    }
}

/** 공유기 녹음이 다시 듣기에 올라간 뒤 감출 인터넷 녹음 — 한 줄 「방 번호|연 때(초)」 */
object GamchumDaegi {
    private fun pref(ctx: Context) = ctx.getSharedPreferences("byod", Context.MODE_PRIVATE)
    fun mokrok(ctx: Context): List<Pair<String, Long>> = (pref(ctx).getString("gamchum_daegi", "") ?: "").split("\n").mapNotNull { j ->
        val p = j.split("|"); if (p.size == 2) p[1].toLongOrNull()?.let { Pair(p[0], it) } else null
    }
    @Synchronized fun deohagi(ctx: Context, sid: String, ttaeMs: Long) {
        val l = mokrok(ctx).toMutableList()
        if (l.none { it.first == sid }) l.add(Pair(sid, ttaeMs / 1000))
        pref(ctx).edit().putString("gamchum_daegi", l.takeLast(50).joinToString("\n") { it.first + "|" + it.second }).apply()
    }
    @Synchronized fun ppaegi(ctx: Context, sid: String) {
        pref(ctx).edit().putString("gamchum_daegi", mokrok(ctx).filter { it.first != sid }.joinToString("\n") { it.first + "|" + it.second }).apply()
    }
}
