// 듣기 예약 — 정한 시각에 라디오 틀기, 정한 때에 끄기 (안드로이드 2.80.0, 빌드 261027-A68, 대클)
// 이사장님 승인 2026-10-27 「일단 길눈에서 할 수 있는 최대를 해 놓자」 — 「매일 오전 7시에 MBC 표준FM 틀어 줘」. 아이폰 길눈 2.115.0과 같은 약속.
// ① 안드로이드는 정한 시각에 폰이 길눈을 깨워 손대지 않아도 라디오를 틂(정확한 알람 — 정기 호출과 같은 방식)
// ② 예약마다 「틀고 나서 몇 분 뒤 끄기」, 따로 「몇 분 뒤 끄기」
// ③ 통화·긴급통화 중이면 틀지 않음(통화가 걸리면 하던 소리는 멈춘다는 원칙 그대로)
package kr.or.ada.app.gilnun

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.EditText
import androidx.core.widget.doAfterTextChanged
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DeutgiYeyakHang(
    val id: String,
    var si: Int,
    var bun: Int,
    var banbok: String,        // maeil 날마다, pyeongil 평일, jumal 주말, hanbeon 한 번만
    var nal: String,           // hanbeon 일 때 yyyyMMdd
    var chaeneolId: String,
    var chaeneolIreum: String,
    var kkeulBun: Int,         // 틀고 나서 몇 분 뒤 끌지(0 이면 그대로)
    var kyeojim: Boolean
) {
    fun json(): JSONObject = JSONObject().put("id", id).put("si", si).put("bun", bun).put("banbok", banbok).put("nal", nal)
        .put("ch", chaeneolId).put("chIreum", chaeneolIreum).put("kkeul", kkeulBun).put("kyeojim", kyeojim)

    val banbokMal: String
        get() = when (banbok) {
            "pyeongil" -> "평일마다"
            "jumal" -> "주말마다"
            "hanbeon" -> DeutgiYeyak.nalMalYmd(nal) + " 한 번"
            else -> "날마다"
        }

    val julMal: String
        get() {
            var t = "$banbokMal ${JeonggiHochul.sigakMal(si, bun)}, $chaeneolIreum"
            if (kkeulBun > 0) t += ", ${DeutgiYeyak.bunMal(kkeulBun)} 뒤 끔"
            if (!kyeojim) t += ", 꺼 둠"
            return t
        }

    companion object {
        fun bat(o: JSONObject) = DeutgiYeyakHang(o.optString("id"), o.optInt("si", 7), o.optInt("bun", 0), o.optString("banbok", "maeil"),
            o.optString("nal"), o.optString("ch"), o.optString("chIreum"), o.optInt("kkeul", 0), o.optBoolean("kyeojim", true))
    }
}

object DeutgiYeyak {
    const val YEYAK = "kr.or.ada.app.gilnun.DEUTGI_YEYAK"
    private var d: SharedPreferences? = null
    private var ctx: Context? = null
    private val main = Handler(Looper.getMainLooper())
    private var dolgo = false

    var mok: MutableList<DeutgiYeyakHang> = ArrayList()
        private set
    /** 끄기 예약 — 이때(밀리초) 방송을 끔 */
    var kkeulTtae = 0L
        private set
    private var han: MutableList<String> = ArrayList()

    fun junbi(c: Context) {
        if (d != null) return
        ctx = c.applicationContext
        d = c.applicationContext.getSharedPreferences("gilnun_deutgi", Context.MODE_PRIVATE)
        try {
            val a = JSONArray(d?.getString("mok", "[]") ?: "[]")
            mok = (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let { o -> DeutgiYeyakHang.bat(o) } }.toMutableList()
        } catch (_: Exception) {}
        try {
            val a = JSONArray(d?.getString("han", "[]") ?: "[]")
            han = (0 until a.length()).map { a.optString(it) }.toMutableList()
        } catch (_: Exception) {}
    }

    /** 앱이 켜질 때 — 끄기 지킴이를 세우고 다음 알람을 다시 걺 */
    fun sijak(c: Context) {
        junbi(c)
        jaeYeyak()
        if (dolgo) return
        dolgo = true
        main.postDelayed(jikimi, 15000)
    }

    private val jikimi = object : Runnable {
        override fun run() {
            kkeugiSalpigi()
            main.postDelayed(this, 15000)
        }
    }

    private fun jeojang() {
        val a = JSONArray()
        for (h in mok) a.put(h.json())
        val hn = JSONArray()
        for (k in han.takeLast(60)) hn.put(k)
        d?.edit()?.putString("mok", a.toString())?.putString("han", hn.toString())?.apply()
    }

    // MARK: 정하기

    fun deohagi(h: DeutgiYeyakHang) {
        mok.add(h)
        jeojang()
        jaeYeyak()
        Girok.namgi("deutgi_yeyak", mapOf("si" to h.si, "bun" to h.bun, "banbok" to h.banbok, "ch" to h.chaeneolId, "kkeul" to h.kkeulBun))
    }

    fun jiugi(id: String) {
        mok.removeAll { it.id == id }
        jeojang()
        jaeYeyak()
        Girok.namgi("deutgi_yeyak_jium")
    }

    fun kyeogiKkeugi(id: String) {
        mok.firstOrNull { it.id == id }?.let { it.kyeojim = !it.kyeojim }
        jeojang()
        jaeYeyak()
    }

    fun moduJiugi() {
        mok.clear()
        jeojang()
        jaeYeyak()
    }

    // MARK: 끄기 예약

    fun kkeugiYeyak(bun: Int) {
        kkeulTtae = System.currentTimeMillis() + bun * 60_000L
        Girok.namgi("deutgi_kkeugi_yeyak", mapOf("bun" to bun))
    }

    fun kkeugiChwiso() { kkeulTtae = 0L }

    val kkeugiMal: String
        get() {
            if (kkeulTtae == 0L) return ""
            val n = maxOf(1, ((kkeulTtae - System.currentTimeMillis() + 59_999L) / 60_000L).toInt())
            return "${bunMal(n)} 뒤에 방송을 끕니다."
        }

    private fun kkeugiSalpigi() {
        if (kkeulTtae != 0L && System.currentTimeMillis() >= kkeulTtae) {
            kkeulTtae = 0L
            if (Bangsong.itda) {
                Sori.mal("예약하신 대로 방송을 끕니다.", MalGeup.ANNAE)
                Bangsong.geuman(false)
                Girok.namgi("deutgi_kkeugi")
            }
        }
    }

    // MARK: 알람 — 다음 한 번(가장 이른 예약)을 걺

    /** 이 날 이 예약이 울릴 때(그날이 아니면 null) */
    fun geunalTtae(h: DeutgiYeyakHang, nal: Long): Long? {
        val c = Calendar.getInstance().apply { timeInMillis = nal }
        val w = c.get(Calendar.DAY_OF_WEEK)
        when (h.banbok) {
            "pyeongil" -> if (w == Calendar.SATURDAY || w == Calendar.SUNDAY) return null
            "jumal" -> if (w != Calendar.SATURDAY && w != Calendar.SUNDAY) return null
            "hanbeon" -> if (h.nal != JeonggiHochul.ymd(nal)) return null
        }
        c.set(Calendar.HOUR_OF_DAY, h.si); c.set(Calendar.MINUTE, h.bun); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** 앞으로 가장 이른 때와 그때 울릴 예약들 */
    private fun daeum(): Pair<Long, List<DeutgiYeyakHang>>? {
        val now = System.currentTimeMillis()
        var best = Long.MAX_VALUE
        for (h in mok) {
            if (!h.kyeojim) continue
            for (i in 0 until 8) {
                val t = geunalTtae(h, now + i * 86_400_000L) ?: continue
                if (t > now + 5_000L) { if (t < best) best = t; break }
            }
        }
        if (best == Long.MAX_VALUE) return null
        val hang = mok.filter { it.kyeojim && (0 until 8).any { i -> geunalTtae(it, now + i * 86_400_000L) == best } }
        return best to hang
    }

    val daeumMal: String
        get() {
            val x = daeum() ?: return if (mok.isEmpty()) "듣기 예약이 없습니다." else "켜 둔 예약이 없습니다."
            val h = x.second.firstOrNull() ?: return "듣기 예약이 없습니다."
            val oneul = JeonggiHochul.ymd(System.currentTimeMillis())
            val naeil = JeonggiHochul.ymd(System.currentTimeMillis() + 86_400_000L)
            val k = JeonggiHochul.ymd(x.first)
            val nm = if (k == oneul) "오늘" else if (k == naeil) "내일" else JeonggiHochul.nalMal(x.first)
            return "다음은 $nm ${JeonggiHochul.sigakMal(h.si, h.bun)} ${h.chaeneolIreum}입니다."
        }

    private fun allimPi(c: Context): PendingIntent =
        PendingIntent.getBroadcast(c, 7410, Intent(c, DeutgiReceiver::class.java).setAction(YEYAK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    @android.annotation.SuppressLint("MissingPermission", "ScheduleExactAlarm")
    fun jaeYeyak() {
        val c = ctx ?: return
        val am = c.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = allimPi(c)
        am.cancel(pi)
        val x = daeum() ?: return
        val t = x.first
        try {
            if (JeonggiHochul.jeonghwakHeorak(c)) {
                val yeol = PendingIntent.getActivity(c, 7411, Intent(c, GilnunActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                am.setAlarmClock(AlarmManager.AlarmClockInfo(t, yeol), pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
            }
        } catch (e: Exception) {
            try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi) } catch (_: Exception) {}
            Girok.namgi("deutgi_yeyak_oryu", mapOf("e" to (e.message ?: "").take(80)))
        }
    }

    /** 알람 때 — DeutgiReceiver. 지금(2분 안)이 때인 예약을 틂 */
    fun allimTtae(c: Context) {
        junbi(c)
        val now = System.currentTimeMillis()
        val ttae = mok.filter { it.kyeojim }.mapNotNull { h -> geunalTtae(h, now)?.let { t -> if (now >= t - 30_000L && now - t < 120_000L) h to t else null } }
        val h = ttae.firstOrNull()
        if (h != null) teulgi(c, h.first, h.second)
        jaeYeyak()
    }

    private fun ymdHm(t: Long): String = SimpleDateFormat("yyyyMMddHHmm", Locale.US).format(Date(t))

    fun teulgi(c: Context, h: DeutgiYeyakHang, t: Long) {
        // 2.81.0 (261027-A69) 아침 브리핑을 읽는 중이면 기다림 — 브리핑을 마친 뒤 브리핑이 이 예약을 이어서 틂(말 위에 라디오가 겹치지 않게)
        if (Iljeong.beuripingJung) return
        val k = h.id + "|" + ymdHm(t)
        if (han.contains(k)) return
        han.add(k)
        if (h.banbok == "hanbeon") mok.firstOrNull { it.id == h.id }?.kyeojim = false
        jeojang()
        if (JeonhwaGamsi.jeonhwaJung || GinGeup.sangtae != GinGeupSangtae.EOPSEUM) {
            Girok.namgi("deutgi_yeyak_tonghwa", mapOf("ch" to h.chaeneolId))
            return
        }
        if (Bangsong.jong == BangsongJong.RADIO && Bangsong.jigeumChaeneolId == h.chaeneolId && !Bangsong.meomchum) return
        Sori.sijak(c)
        Bangsong.sijak(c)
        Girok.namgi("deutgi_yeyak_teulgi", mapOf("ch" to h.chaeneolId))
        Sori.mal("${JeonggiHochul.sigakMal(h.si, h.bun)} ${h.chaeneolIreum} 예약입니다. 틉니다.", MalGeup.JEONGBO)
        Bangsong.chaeneolTeulgi(Chaeneol(h.chaeneolId, h.chaeneolIreum, "", "radio"), false)
        if (h.kkeulBun > 0) kkeugiYeyak(h.kkeulBun)
        if (!dolgo) { dolgo = true; main.postDelayed(jikimi, 15000) }
    }

    // MARK: 도움 셈

    fun nalMalYmd(s: String): String {
        if (s.length != 8) return s
        val m = s.substring(4, 6).toIntOrNull() ?: return s
        val dd = s.substring(6, 8).toIntOrNull() ?: return s
        return "${m}월 ${dd}일"
    }

    fun bunMal(n: Int): String = when {
        n >= 60 && n % 60 == 0 -> "${n / 60}시간"
        n > 60 -> "${n / 60}시간 ${n % 60}분"
        else -> "${n}분"
    }

    fun saeId(): String = System.currentTimeMillis().toString()

    /** 「30분 뒤」, 「1시간 뒤」, 「한 시간 있다가」, 「1시간 반 뒤」 — 분으로 */
    fun bunDwi(z0: String): Int? {
        var s = z0
        for ((a, b) in listOf("한시간" to "1시간", "두시간" to "2시간", "세시간" to "3시간", "반시간" to "30분", "이십분" to "20분", "삼십분" to "30분", "십분" to "10분")) s = s.replace(a, b)
        if (!(s.contains("뒤") || s.contains("후") || s.contains("있다가") || s.contains("지나"))) return null
        var hap = 0
        var chatum = false
        var i = 0
        while (i < s.length) {
            if (s[i].isDigit()) {
                var j = i
                while (j < s.length && s[j].isDigit()) j++
                val n = s.substring(i, j).toIntOrNull()
                if (n != null) {
                    if (s.startsWith("시간", j)) {
                        hap += n * 60; chatum = true; j += 2
                        if (j < s.length && s[j] == '반') { hap += 30; j++ }
                    } else if (j < s.length && s[j] == '분') {
                        hap += n; chatum = true; j++
                    }
                }
                i = j
                continue
            }
            i++
        }
        return if (chatum && hap > 0 && hap <= 24 * 60) hap else null
    }

    /** 말 속의 라디오 채널 — 이름이 없으면 지난번 라디오 */
    fun chaeneolChatgi(z: String): Chaeneol? {
        val ls = Bangsong.kindChaeneol("radio")
        if (ls.isEmpty()) return null
        val zz = Bangsong.bangsongPyojun(z)
        val sae = listOf("kbs", "mbc", "ebs", "obs", "tbs", "bbs", "arirang")
        fun sa(s: String): String? = sae.firstOrNull { s.contains(it) }
        class P(val c: Chaeneol, val sa: String?, val bu: String)
        val pul = ls.map { c ->
            val nm = Bangsong.bangsongPyojun(c.name)
            val s0 = sa(nm)
            P(c, s0, if (s0 != null) nm.replace(s0, "") else nm)
        }
        val s1 = sa(zz)
        val hubo = if (s1 == null) pul else pul.filter { it.sa == s1 }
        hubo.firstOrNull { it.bu.isNotEmpty() && zz.contains((it.sa ?: "") + it.bu) }?.let { return it.c }
        val heunhan = setOf("tv", "fm", "라디오", "방송")
        hubo.firstOrNull { it.bu.length >= 2 && !heunhan.contains(it.bu) && zz.contains(it.bu) }?.let { return it.c }
        val jjal = listOf("표준" to "표준fm", "4u" to "fm4u", "해피" to "해피fm", "쿨" to "쿨fm", "클래식" to "클래식fm",
            "사랑의소리" to "3라디오", "3라디오" to "3라디오", "1라디오" to "1라디오", "한민족" to "한민족")
        for ((m, k) in jjal) if (zz.contains(m)) hubo.firstOrNull { it.bu.contains(k) }?.let { return it.c }
        if (s1 != null) {
            val eutteum = mapOf("mbc" to "mbcsfm", "kbs" to "kbs1r", "ebs" to "ebsfm")
            return hubo.firstOrNull { it.c.id == eutteum[s1] }?.c ?: hubo.firstOrNull()?.c
        }
        val id = BangsongSeol.majimakRadio
        return ls.firstOrNull { it.id == id } ?: ls.firstOrNull()
    }

    // MARK: 말로 하기 — 아이폰 DeutgiYeyak.malCheori 와 같은 말

    fun malCheori(z: String, dap: (String, Boolean) -> Unit): Boolean {
        val zl = z.lowercase(Locale.ROOT)
        val pj = Bangsong.bangsongPyojun(z)
        val bangsongMal = z.contains("라디오") || zl.contains("fm") || z.contains("에프엠") || z.contains("방송") ||
            listOf("kbs", "mbc", "ebs", "obs", "tbs", "bbs", "표준fm", "fm4u", "해피fm", "쿨fm", "클래식fm", "사랑의소리", "한민족",
                "sbs", "cbs", "ytn", "와이티엔", "tbn", "교통방송", "국악방송", "극동방송", "불교방송", "국방fm", "경인방송", "ifm", "파워fm", "러브fm", "음악fm", "고릴라디오", "조이포유", "반디").any { pj.contains(it) }   // 2.86.0 새 라디오
        val yeyakMal = z.contains("예약")
        if (z.contains("듣기예약") || (yeyakMal && bangsongMal && !z.contains("시"))) {
            if (z.contains("지워") || z.contains("취소") || z.contains("없애")) {
                if (z.contains("끄기")) { kkeugiChwiso(); dap("끄기 예약을 거두었습니다.", false); return true }
                val n = mok.size
                moduJiugi()
                dap(if (n == 0) "지울 듣기 예약이 없습니다." else "듣기 예약 ${n}개를 모두 지웠습니다.", false)
                return true
            }
            if (z.contains("알려") || z.contains("뭐") || z.contains("무슨") || z.contains("있어") || z.contains("들려")) {
                if (mok.isEmpty()) { dap("듣기 예약이 없습니다.", false); return true }
                dap("듣기 예약 ${mok.size}개입니다. " + mok.joinToString(". ") { it.julMal } + ". " + daeumMal + (if (kkeugiMal.isEmpty()) "" else " $kkeugiMal"), false)
                return true
            }
        }
        val kkeugi = listOf("꺼", "끄", "그만", "멈춰").any { z.contains(it) }
        val n0 = if (kkeugi) bunDwi(z) else null
        if (n0 != null) {
            if (z.contains("취소")) { kkeugiChwiso(); dap("끄기 예약을 거두었습니다.", false); return true }
            kkeugiYeyak(n0)
            dap("${bunMal(n0)} 뒤에 방송을 끄겠습니다." + (if (Bangsong.itda) "" else " 지금은 틀고 있는 방송이 없습니다."), false)
            return true
        }
        if (z.contains("끄기예약") && (z.contains("취소") || z.contains("지워") || z.contains("없애"))) {
            kkeugiChwiso(); dap("끄기 예약을 거두었습니다.", false); return true
        }
        if (!bangsongMal || kkeugi || !(z.contains("틀어") || z.contains("켜") || yeyakMal || z.contains("들려"))) return false
        val banbok = when {
            z.contains("평일") || z.contains("주중") -> "pyeongil"
            z.contains("주말") -> "jumal"
            z.contains("매일") || z.contains("날마다") || z.contains("아침마다") || z.contains("저녁마다") -> "maeil"
            else -> "hanbeon"
        }
        if (banbok == "hanbeon" && !(z.startsWith("오늘") || z.startsWith("내일") || z.startsWith("모레") || yeyakMal)) return false
        val t0 = JeonggiHochul.sigakDeul(z).firstOrNull()
        if (t0 == null) {
            if (yeyakMal || banbok != "hanbeon") {
                dap("몇 시에 틀지 함께 말씀해 주십시오. 예를 들면 매일 아침 7시에 MBC 표준FM 틀어 줘라고 하시면 됩니다.", false)
                return true
            }
            return false
        }
        var si = t0.first
        val bun = t0.second
        if ((z.contains("아침") || z.contains("오전") || z.contains("새벽")) && si == 12) si = 0
        var nal = ""
        if (banbok == "hanbeon") {
            val c = Calendar.getInstance()
            if (z.startsWith("내일")) c.add(Calendar.DAY_OF_MONTH, 1)
            else if (z.startsWith("모레")) c.add(Calendar.DAY_OF_MONTH, 2)
            else if (!z.startsWith("오늘")) {
                val tt = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, si); set(Calendar.MINUTE, bun); set(Calendar.SECOND, 0) }
                if (tt.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_MONTH, 1)
            }
            nal = JeonggiHochul.ymd(c.timeInMillis)
        }
        val dwi = z.split("틀어").drop(1).joinToString("")
        val kkeulBun = bunDwi(dwi) ?: 0
        val hagi = {
            val c = chaeneolChatgi(z)
            if (c == null) {
                val ireum = Bangsong.kindChaeneol("radio").take(6).joinToString(", ") { it.name }
                dap("어느 방송을 틀지 알아듣지 못했습니다. $ireum 가운데 하나를 넣어 다시 말씀해 주십시오.", false)
            } else {
                val h = DeutgiYeyakHang(saeId(), si, bun, banbok, nal, c.id, c.name, kkeulBun, true)
                deohagi(h)
                dap("${h.julMal}로 예약했습니다. 그 시각에 길눈이 라디오를 틉니다.", false)
            }
        }
        if (Bangsong.chaeneolDeul.isEmpty()) Bangsong.chaeneolBatgi { hagi() } else hagi()
        return true
    }
}

/** 알람 때·폰을 다시 켰을 때·새 판으로 바뀌었을 때 */
class DeutgiReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        DeutgiYeyak.junbi(c)
        if (i.action == DeutgiYeyak.YEYAK) {
            val p = goAsync()
            try { DeutgiYeyak.allimTtae(c) } catch (e: Exception) { Girok.namgi("deutgi_yeyak_oryu", mapOf("e" to (e.message ?: "").take(80))) }
            Handler(Looper.getMainLooper()).postDelayed({ try { p.finish() } catch (_: Exception) {} }, 8000)
        } else {
            DeutgiYeyak.jaeYeyak()
        }
    }
}

// MARK: - 화면

class DeutgiYeyakHwamyeon : Hwamyeon("듣기 예약") {
    private var saePyeol = false
    private var sigakGeul = ""
    private var banbok = "maeil"
    private var nalGeul = ""
    private var chaeneolId = ""
    private var kkeulBun = 0
    private var sijak = 0

    override fun chaeugi(t: GilnunActivity) {
        val y = DeutgiYeyak
        y.sijak(t)
        if (Bangsong.chaeneolDeul.isEmpty()) Bangsong.chaeneolBatgi { if (t.wiHwamyeon === this) t.dasiGeurigi() }
        if (chaeneolId.isEmpty()) chaeneolId = BangsongSeol.majimakRadio.ifEmpty { "mbcsfm" }
        t.chojeomJul(t.geul(y.daeumMal))
        if (y.kkeulTtae != 0L) {
            t.geul(y.kkeugiMal)
            t.danchu("끄기 예약 거두기") { y.kkeugiChwiso(); Sori.mal("끄기 예약을 거두었습니다."); t.dasiGeurigi() }
        }
        nnPyeolchigi(t, "새 듣기 예약 만들기 펼치기", "새 듣기 예약 접기", saePyeol) { saePyeol = !saePyeol }
        if (saePyeol) {
            val e1: EditText = t.ipryeok("틀 시각 — 예: 7시, 6시 반, 0730", false)
            e1.setText(sigakGeul)
            e1.doAfterTextChanged { sigakGeul = it?.toString() ?: "" }
            for ((k, nm) in listOf("maeil" to "날마다", "pyeongil" to "평일만 — 월요일부터 금요일", "jumal" to "주말만 — 토요일과 일요일", "hanbeon" to "한 번만")) {
                t.danchu("되풀이 — $nm" + (if (banbok == k) " (골랐음)" else "")) { banbok = k; t.dasiGeurigi() }
            }
            if (banbok == "hanbeon") {
                val e2: EditText = t.ipryeok("날짜 — 예: 내일, 11월 3일. 비우면 오늘(지난 시각이면 내일)", false)
                e2.setText(nalGeul)
                e2.doAfterTextChanged { nalGeul = it?.toString() ?: "" }
            }
            val ls = Bangsong.kindChaeneol("radio")
            val goreun = ls.firstOrNull { it.id == chaeneolId }
            t.geul("방송: " + (goreun?.name ?: "목록을 받는 중입니다"))
            if (ls.isNotEmpty()) {
                val kkeut = minOf(sijak + 5, ls.size)
                for (n in sijak until kkeut) {
                    val c = ls[n]
                    t.danchu("방송 — ${c.julMal}" + (if (c.id == chaeneolId) " (골랐음)" else "")) { chaeneolId = c.id; t.dasiGeurigi() }
                }
                if (kkeut < ls.size) t.danchu("방송 더 보기") { sijak += 5; t.dasiGeurigi() }
                if (sijak > 0) t.danchu("방송 이전 보기") { sijak = maxOf(0, sijak - 5); t.dasiGeurigi() }
            }
            for ((k, nm) in listOf(0 to "끄지 않고 그대로", 30 to "30분 뒤 끄기", 60 to "1시간 뒤 끄기", 120 to "2시간 뒤 끄기")) {
                t.danchu("끌 때 — $nm" + (if (kkeulBun == k) " (골랐음)" else "")) { kkeulBun = k; t.dasiGeurigi() }
            }
            t.danchu("이대로 예약하기") { jeojang(t) }
        }
        if (y.mok.isNotEmpty()) {
            t.geul("정해 둔 예약 ${y.mok.size}개", true)
            for (h in y.mok.toList()) {
                t.danchu(h.julMal + (if (h.kyeojim) " — 누르면 잠시 끔" else " — 누르면 다시 켬")) {
                    y.kyeogiKkeugi(h.id)
                    Sori.mal(if (h.kyeojim) "이 예약을 다시 켰습니다." else "이 예약을 꺼 두었습니다.")
                    t.dasiGeurigi()
                }
                t.danchu("이 예약 지우기 — ${h.julMal}") { y.jiugi(h.id); Sori.mal("예약을 지웠습니다."); t.dasiGeurigi() }
            }
        }
        t.geul("끄기 예약 — 지금 나오는 방송을 정한 때에 끕니다", true)
        for (n in listOf(15, 30, 60, 90)) {
            t.danchu("${DeutgiYeyak.bunMal(n)} 뒤에 끄기") {
                y.kkeugiYeyak(n)
                Sori.mal("${DeutgiYeyak.bunMal(n)} 뒤에 방송을 끕니다." + (if (Bangsong.itda) "" else " 지금은 틀고 있는 방송이 없습니다."))
                t.dasiGeurigi()
            }
        }
        if (y.mok.any { it.kyeojim } && !JeonggiHochul.jeonghwakHeorak(t)) {
            t.danchu("정확한 시각에 틀게 허락하기 — 허락 전에는 몇 분 늦을 수 있습니다") {
                try {
                    if (Build.VERSION.SDK_INT >= 31) t.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + t.packageName)))
                    Sori.mal("알람 및 리마인더 허용을 켜신 뒤 뒤로 돌아오십시오.")
                } catch (e: Exception) { Sori.mal("설정 화면을 열지 못했습니다.") }
            }
        }
        t.geul("말로도 됩니다. 매일 7시에 MBC 표준FM 틀어 줘. 평일 아침 6시 반 KBS 1라디오 예약. 30분 뒤에 라디오 꺼 줘. 듣기 예약 알려 줘.")
    }

    private fun jeojang(t: GilnunActivity) {
        val s = JeonggiHochul.sigakDeul(MalSajeon.ttuk(sigakGeul)).firstOrNull()
        if (s == null) { Sori.mal("틀 시각을 알아보지 못했습니다. 7시, 6시 반, 또는 0730처럼 넣어 주십시오."); return }
        val c = Bangsong.kindChaeneol("radio").firstOrNull { it.id == chaeneolId } ?: Bangsong.kindChaeneol("radio").firstOrNull()
        if (c == null) { Sori.mal("채널 목록을 받지 못했습니다. 통신을 확인하신 뒤 다시 눌러 주십시오."); return }
        var nal = ""
        if (banbok == "hanbeon") {
            val cal = Calendar.getInstance()
            val ng = MalSajeon.ttuk(nalGeul)
            val m = Regex("(\\d{1,2})월(\\d{1,2})일").find(ng)
            when {
                ng.startsWith("내일") -> cal.add(Calendar.DAY_OF_MONTH, 1)
                ng.startsWith("모레") -> cal.add(Calendar.DAY_OF_MONTH, 2)
                m != null -> {
                    val mm = m.groupValues[1].toInt(); val dd = m.groupValues[2].toInt()
                    cal.set(Calendar.MONTH, mm - 1); cal.set(Calendar.DAY_OF_MONTH, dd)
                    if (cal.timeInMillis < System.currentTimeMillis() - 86_400_000L) cal.add(Calendar.YEAR, 1)
                }
                else -> {
                    val tt = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, s.first); set(Calendar.MINUTE, s.second); set(Calendar.SECOND, 0) }
                    if (tt.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_MONTH, 1)
                }
            }
            val tt = (cal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, s.first); set(Calendar.MINUTE, s.second); set(Calendar.SECOND, 0) }
            if (tt.timeInMillis <= System.currentTimeMillis()) { Sori.mal("이미 지난 시각입니다. 날짜나 시각을 다시 넣어 주십시오."); return }
            nal = JeonggiHochul.ymd(cal.timeInMillis)
        }
        val h = DeutgiYeyakHang(DeutgiYeyak.saeId(), s.first, s.second, banbok, nal, c.id, c.name, kkeulBun, true)
        DeutgiYeyak.deohagi(h)
        saePyeol = false
        sigakGeul = ""; nalGeul = ""
        Sori.mal("${h.julMal}로 예약했습니다.")
        t.dasiGeurigi()
    }
}
