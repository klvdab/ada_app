// 일정 — 폰 달력을 말로 듣고 넣고 고치기, 아침 브리핑, 떠날 시각 알림 (안드로이드 2.81.0, 빌드 261027-A69, 대클)
// 이사장님 승인 2026-10-27 「1번으로 하는데 위치를 음악방송이 아니라 설정탭에 넣도록하자」 — 설계안 다섯 가지(듣기·넣기·떠날 시각·아침 브리핑·고치기와 지우기).
// 아이폰 길눈 2.116.0 Iljeong.swift 와 같은 약속, 같은 말.
// ① 일정은 폰 달력(구글 캘린더)에 담음 — 길눈은 따로 담지 않아 폰을 바꿔도 남음. 길눈이 넣은 일정에는 설명 칸에 표시를 붙여 알아봄
// ② 읽기는 폰의 모든 달력, 고치기·지우기는 길눈이 넣은 일정만(다른 곳에서 넣은 일정은 읽기만)
// ③ 넣기·고치기·지우기는 반드시 한 번 읽어 드리고 「네」를 들은 뒤에. 지운 일정은 하루 동안 되살릴 수 있음
// ④ 일정 내용은 폰 밖으로 보내지 않음 — 이용 기록에는 「썼다」는 것만
// ⑤ 떠날 시각은 지금 자리에서 그곳까지 곧은 거리로 어림함(걸어서 1.5킬로미터 안은 걸음으로, 그 넘으면 탈것으로) — 어림이라고 늘 밝힘
package kr.or.ada.app.gilnun

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.EditText
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.ceil

/** 달력의 일정 하나(되풀이 일정은 그 회 하나) */
class IljeongHang(
    val id: Long,
    val jemok: String,
    val sijak: Long,          // 하루 종일이면 그날 0시(이 폰 시간)
    val kkeut: Long,
    val haruJongil: Boolean,
    val jangso: String,
    val gilnunGeot: Boolean,  // 길눈이 넣은 일정
    val banbok: Boolean,      // 되풀이 일정의 한 회
    val wonSijak: Long        // 달력이 준 그대로의 시작(되풀이 한 회를 고칠 때 씀)
) {
    val ki: String get() = "$id|$sijak"
}

/** 넣을 일정 */
class SaeIljeong(var jemok: String, var jangso: String, var sijak: Long, var kkeut: Long, var haruJongil: Boolean, var banbok: String, var yoil: Int)

/** 길눈이 여쭙고 대답을 기다리는 것 */
sealed class IljeongMureum {
    class Neoki(val s: SaeIljeong) : IljeongMureum()
    class Sigak(val p: IljeongPul) : IljeongMureum()
    class Goreugi(val hubo: List<IljeongHang>, val hal: String, val p: IljeongPul, val modu: Boolean) : IljeongMureum()
    class Bakkugi(val h: IljeongHang, val sijak: Long, val kkeut: Long) : IljeongMureum()
    class Jiugi(val h: IljeongHang, val modu: Boolean) : IljeongMureum()
    class Annae(val jangso: String) : IljeongMureum()
}

/** 떠날 시각 하나 */
class IljeongDdeona(val ki: String, val jemok: String, val jangso: String, val sijak: Long, val ddeona: Long, val bun: Int) {
    fun json(): JSONObject = JSONObject().put("ki", ki).put("jemok", jemok).put("jangso", jangso).put("sijak", sijak).put("ddeona", ddeona).put("bun", bun)
    companion object {
        fun bat(o: JSONObject) = IljeongDdeona(o.optString("ki"), o.optString("jemok"), o.optString("jangso"), o.optLong("sijak"), o.optLong("ddeona"), o.optInt("bun"))
    }
}

object Iljeong {
    const val ACHIM = "kr.or.ada.app.gilnun.ILJEONG_ACHIM"
    const val DDEONA = "kr.or.ada.app.gilnun.ILJEONG_DDEONA"
    const val YEOLGI_ACHIM = "kr.or.ada.app.gilnun.ILJEONG_YEOLGI_ACHIM"
    const val YEOLGI_ANNAE = "kr.or.ada.app.gilnun.ILJEONG_YEOLGI_ANNAE"
    const val HEORAK_BEON = 31
    /** 길눈이 넣은 일정의 설명 칸 표시 */
    const val PYOSI = "(길눈에서 넣은 일정)"
    private const val CHANNEL = "gilnun_iljeong"
    private const val HARU = 86_400_000L

    private var d: SharedPreferences? = null
    private var ctx: Context? = null
    private val main = Handler(Looper.getMainLooper())

    var mureum: IljeongMureum? = null
        private set
    private var mureumTtae = 0L
    /** 여쭐 때마다 하나씩 — 마이크를 열 때 그 물음이 아직 살아 있는지 견줌 */
    var mureumBeon = 0L
        private set
    @Volatile var beuripingJung = false
        private set
    private var beuripingBeon = 0
    var ddeonaMok: List<IljeongDdeona> = emptyList()
        private set
    private var gyesanTtae = 0L
    private var gyesanJari: Pair<Double, Double>? = null
    private var gyesanJung = false
    private var dasiGyesan = false   // 셈하는 중에 일정이 바뀌면 끝난 뒤 한 번 더

    fun junbi(c: Context) {
        if (d != null) return
        ctx = c.applicationContext
        d = c.applicationContext.getSharedPreferences("gilnun_iljeong", Context.MODE_PRIVATE)
        try {
            val a = JSONArray(d?.getString("ddeona", "[]") ?: "[]")
            ddeonaMok = (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let { o -> IljeongDdeona.bat(o) } }
        } catch (_: Exception) {}
    }

    /** 앱이 켜질 때·일정 화면을 열 때 — 알람을 다시 걸고 떠날 시각을 셈 */
    fun sijak(c: Context) {
        junbi(c)
        jaeYeyak()
        ddeonaGyesan(c, false)
    }

    // MARK: 설정

    var achimKyeojim: Boolean
        get() = d?.getBoolean("achim", false) ?: false
        set(v) { d?.edit()?.putBoolean("achim", v)?.apply(); jaeYeyak() }
    var achimSi: Int
        get() = d?.getInt("achimSi", 7) ?: 7
        set(v) { d?.edit()?.putInt("achimSi", v)?.apply(); jaeYeyak() }
    var achimBun: Int
        get() = d?.getInt("achimBun", 0) ?: 0
        set(v) { d?.edit()?.putInt("achimBun", v)?.apply(); jaeYeyak() }
    /** maeil 날마다, pyeongil 평일, jumal 주말 */
    var achimYoil: String
        get() = d?.getString("achimYoil", "maeil") ?: "maeil"
        set(v) { d?.edit()?.putString("achimYoil", v)?.apply(); jaeYeyak() }
    var ddeonaKyeojim: Boolean
        get() = d?.getBoolean("ddeonaKyeojim", true) ?: true
        set(v) { d?.edit()?.putBoolean("ddeonaKyeojim", v)?.apply(); ctx?.let { ddeonaGyesan(it, true) } }
    /** 떠날 시각에 더하는 여유(분) */
    var yeoyu: Int
        get() = d?.getInt("yeoyu", 10) ?: 10
        set(v) { d?.edit()?.putInt("yeoyu", v)?.apply(); ctx?.let { ddeonaGyesan(it, true) } }

    val achimYoilMal: String get() = when (achimYoil) { "pyeongil" -> "평일마다"; "jumal" -> "주말마다"; else -> "날마다" }
    val achimMal: String get() = if (achimKyeojim) "$achimYoilMal ${JeonggiHochul.sigakMal(achimSi, achimBun)}" else "꺼 둠"

    // MARK: 허락

    fun heorak(c: Context): Boolean =
        ContextCompat.checkSelfPermission(c, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun heorakCheong(a: GilnunActivity) {
        Girok.namgi("iljeong_heorak_cheong")
        ActivityCompat.requestPermissions(a, arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR), HEORAK_BEON)
    }

    /** 허락 창의 대답 — GilnunActivity 가 넘겨 줌 */
    fun heorakGyeolgwa(a: GilnunActivity) {
        if (heorak(a)) {
            Sori.mal("달력 허락을 받았습니다. 이제 일정을 듣고 넣으실 수 있습니다.")
            d?.edit()?.remove("dal")?.apply()
            ddeonaGyesan(a, true)
        } else {
            Sori.mal("달력 허락이 없어 일정을 쓸 수 없습니다. 다시 하시려면 일정 화면의 달력 허락하기를 눌러 주십시오.")
        }
        Girok.namgi("iljeong_heorak", mapOf("ok" to heorak(a)))
        a.dasiGeurigi()
    }

    // MARK: 읽기

    private fun utcHaru(t: Long): Long {
        val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = t }
        return Calendar.getInstance().apply { clear(); set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH)) }.timeInMillis
    }

    private fun naeHaruUtc(t: Long): Long {
        val l = Calendar.getInstance().apply { timeInMillis = t }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH)) }.timeInMillis
    }

    fun haru(t: Long, d0: Int = 0): Long {
        val c = Calendar.getInstance().apply { timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        c.add(Calendar.DAY_OF_MONTH, d0)
        return c.timeInMillis
    }

    /** a 부터 b 앞까지의 일정(되풀이는 회마다) — 하루 종일 먼저, 그다음 시각 차례 */
    fun ilgi(c: Context, a: Long, b: Long): List<IljeongHang> {
        if (!heorak(c)) return emptyList()
        val bu = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(bu, a - HARU)
        ContentUris.appendId(bu, b + HARU)
        val proj = arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION, CalendarContract.Instances.RRULE, CalendarContract.Instances.STATUS)
        val l = ArrayList<IljeongHang>()
        try {
            c.contentResolver.query(bu.build(), proj, "${CalendarContract.Instances.VISIBLE}=1", null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cu ->
                while (cu.moveToNext()) {
                    if (!cu.isNull(8) && cu.getInt(8) == CalendarContract.Events.STATUS_CANCELED) continue
                    val jongil = cu.getInt(4) == 1
                    val won = cu.getLong(2)
                    var s = won
                    var e = cu.getLong(3)
                    if (jongil) { s = utcHaru(s); e = utcHaru(e) }
                    if (e <= a || s >= b) continue
                    l.add(IljeongHang(cu.getLong(0), (cu.getString(1) ?: "").ifBlank { "제목 없는 일정" }, s, e, jongil,
                        (cu.getString(5) ?: "").trim(), (cu.getString(6) ?: "").contains(PYOSI), !cu.getString(7).isNullOrEmpty(), won))
                }
            }
        } catch (e: Exception) {
            Girok.namgi("iljeong_oryu", mapOf("dan" to "ilgi", "e" to (e.message ?: "").take(80)))
        }
        return l.distinctBy { it.ki }.sortedWith(compareBy({ haru(it.sijak) }, { !it.haruJongil }, { it.sijak }))
    }

    fun nalIreum(t: Long): String {
        val oneul = haru(System.currentTimeMillis())
        val h = haru(t)
        val ap = when (h) { oneul -> "오늘 "; haru(oneul, 1) -> "내일 "; haru(oneul, 2) -> "모레 "; else -> "" }
        return ap + JeonggiHochul.nalMal(t)
    }

    fun sigakMal(t: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        return JeonggiHochul.sigakMal(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    /** 「낮 2시, 국립극장 회의, 장소 국립극장」 — nal 이면 날짜를 앞에 */
    fun julMal(h: IljeongHang, nal: Boolean): String {
        val sb = StringBuilder()
        if (nal) sb.append(nalIreum(h.sijak)).append(" ")
        sb.append(if (h.haruJongil) "하루 종일" else sigakMal(h.sijak))
        sb.append(", ").append(h.jemok)
        if (h.jangso.isNotBlank()) sb.append(", 장소 ").append(h.jangso)
        return sb.toString()
    }

    /** 범위 말로 — 「오늘 일정은 2개입니다. …」 */
    fun beomwiMal(c: Context, a: Long, b: Long, ireum: String): String {
        if (!heorak(c)) return "일정을 읽으려면 달력 허락이 필요합니다. 설정 탭의 일정에서 달력 허락하기를 눌러 주십시오."
        val l = ilgi(c, a, b)
        val yeoreonal = b - a > HARU + 3_600_000L
        if (l.isEmpty()) return "$ireum${IljeongMal.eun(ireum)} 잡힌 일정이 없습니다."
        val bo = l.take(15)
        var m = "$ireum 일정은 ${l.size}개입니다. " + bo.joinToString(". ") { julMal(it, yeoreonal) } + "."
        if (l.size > bo.size) m += " 그 밖에 ${l.size - bo.size}개가 더 있습니다. 일정 화면의 다가오는 일정 목록에서 보실 수 있습니다."
        return m
    }

    fun oneulMal(c: Context): String { val o = haru(System.currentTimeMillis()); return beomwiMal(c, o, o + HARU, "오늘") }
    fun naeilMal(c: Context): String { val o = haru(System.currentTimeMillis(), 1); return beomwiMal(c, o, o + HARU, "내일") }
    fun ibeonJuMal(c: Context): String {
        val now = System.currentTimeMillis()
        val w = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_WEEK)
        val nameun = (Calendar.SUNDAY - w + 7) % 7 + 1   // 일요일까지
        return beomwiMal(c, haru(now), haru(now, nameun), "이번 주")
    }

    // MARK: 달력 고르기

    /** 길눈이 일정을 넣을 달력 — 구글 기본 달력을 먼저, 없으면 고칠 수 있는 첫 달력, 그도 없으면 이 폰에 「길눈」 달력을 만듦 */
    private fun dalryeokId(c: Context): Long? {
        val jeon = d?.getLong("dal", -1L) ?: -1L
        val proj = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.OWNER_ACCOUNT, CalendarContract.Calendars.IS_PRIMARY, CalendarContract.Calendars.VISIBLE)
        var best: Long? = null
        var bestJeom = -1
        try {
            c.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, proj,
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}", null, null)?.use { cu ->
                while (cu.moveToNext()) {
                    val id = cu.getLong(0)
                    if (id == jeon) return id
                    val google = cu.getString(1) == "com.google"
                    val juin = cu.getString(2) != null && cu.getString(2) == cu.getString(3)
                    val ccheot = !cu.isNull(4) && cu.getInt(4) == 1
                    val boim = cu.getInt(5) == 1
                    val jeom = (if (google && ccheot) 8 else 0) + (if (google && juin) 4 else 0) + (if (ccheot) 2 else 0) + (if (google) 1 else 0) + (if (boim) 1 else 0)
                    if (jeom > bestJeom) { bestJeom = jeom; best = id }
                }
            }
        } catch (e: Exception) {
            Girok.namgi("iljeong_oryu", mapOf("dan" to "dal", "e" to (e.message ?: "").take(80)))
        }
        if (best == null) best = itPonDalryeok(c)
        best?.let { d?.edit()?.putLong("dal", it)?.apply() }
        return best
    }

    private fun itPonDalryeok(c: Context): Long? {
        return try {
            val u = CalendarContract.Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "길눈")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()
            val v = ContentValues().apply {
                put(CalendarContract.Calendars.ACCOUNT_NAME, "길눈")
                put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                put(CalendarContract.Calendars.NAME, "gilnun")
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "길눈")
                put(CalendarContract.Calendars.CALENDAR_COLOR, Color.rgb(8, 30, 70))
                put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
                put(CalendarContract.Calendars.OWNER_ACCOUNT, "길눈")
                put(CalendarContract.Calendars.VISIBLE, 1)
                put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
            }
            c.contentResolver.insert(u, v)?.let { ContentUris.parseId(it) }
        } catch (e: Exception) {
            Girok.namgi("iljeong_oryu", mapOf("dan" to "itpon", "e" to (e.message ?: "").take(80)))
            null
        }
    }

    // MARK: 넣기·고치기·지우기

    private val YOIL_KO = listOf("", "SU", "MO", "TU", "WE", "TH", "FR", "SA")

    private fun rrule(s: SaeIljeong): String? = when (s.banbok) {
        "maeil" -> "FREQ=DAILY"
        "pyeongil" -> "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"
        "jumal" -> "FREQ=WEEKLY;BYDAY=SA,SU"
        "maeju" -> "FREQ=WEEKLY;BYDAY=" + YOIL_KO[if (s.yoil in 1..7) s.yoil else Calendar.getInstance().apply { timeInMillis = s.sijak }.get(Calendar.DAY_OF_WEEK)]
        else -> null
    }

    /** 되풀이의 첫 회가 맞는 요일에 오게 시작을 앞으로 옮김 */
    fun banbokMatchum(s: SaeIljeong) {
        fun majneun(t: Long): Boolean {
            val w = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.DAY_OF_WEEK)
            return when (s.banbok) {
                "pyeongil" -> w != Calendar.SATURDAY && w != Calendar.SUNDAY
                "jumal" -> w == Calendar.SATURDAY || w == Calendar.SUNDAY
                "maeju" -> s.yoil !in 1..7 || w == s.yoil
                else -> true
            }
        }
        var n = 0
        while (!majneun(s.sijak) && n < 7) {
            s.sijak += HARU; s.kkeut += HARU; n++
        }
    }

    /** 달력에 넣기 — 넣은 일정 번호, 못 넣으면 null */
    fun neoki(c: Context, s: SaeIljeong, rruleJikjeop: String? = null): Long? {
        if (!heorak(c)) return null
        val cal = dalryeokId(c) ?: return null
        return try {
            val r = rruleJikjeop ?: rrule(s)
            val v = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, cal)
                put(CalendarContract.Events.TITLE, s.jemok)
                if (s.jangso.isNotBlank()) put(CalendarContract.Events.EVENT_LOCATION, s.jangso)
                put(CalendarContract.Events.DESCRIPTION, PYOSI)
                if (s.haruJongil) {
                    put(CalendarContract.Events.ALL_DAY, 1)
                    put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                    put(CalendarContract.Events.DTSTART, naeHaruUtc(s.sijak))
                    val nalSu = maxOf(1, ((haru(s.kkeut - 1) - haru(s.sijak)) / HARU + 1).toInt())
                    if (r != null) put(CalendarContract.Events.DURATION, "P${nalSu}D")
                    else put(CalendarContract.Events.DTEND, naeHaruUtc(s.sijak) + nalSu * HARU)
                } else {
                    put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                    put(CalendarContract.Events.DTSTART, s.sijak)
                    if (r != null) put(CalendarContract.Events.DURATION, "PT${maxOf(5L, (s.kkeut - s.sijak) / 60_000L)}M")
                    else put(CalendarContract.Events.DTEND, s.kkeut)
                    put(CalendarContract.Events.HAS_ALARM, 1)
                }
                if (r != null) put(CalendarContract.Events.RRULE, r)
            }
            val u = c.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v) ?: return null
            val id = ContentUris.parseId(u)
            if (!s.haruJongil) for (m in listOf(60, 30)) {
                try {
                    c.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply {
                        put(CalendarContract.Reminders.EVENT_ID, id)
                        put(CalendarContract.Reminders.MINUTES, m)
                        put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                    })
                } catch (_: Exception) {}
            }
            Girok.namgi("iljeong_neoki", mapOf("banbok" to s.banbok, "jongil" to s.haruJongil, "jangso" to s.jangso.isNotBlank()))
            ddeonaGyesan(c, true)
            id
        } catch (e: Exception) {
            Girok.namgi("iljeong_oryu", mapOf("dan" to "neoki", "e" to (e.message ?: "").take(80)))
            null
        }
    }

    /** 시각·날 바꾸기 — 되풀이 일정은 이 회만 */
    fun bakkugi(c: Context, h: IljeongHang, sijak: Long, kkeut: Long): Boolean {
        if (!heorak(c) || !h.gilnunGeot) return false
        return try {
            val ok = if (h.banbok) {
                val v = ContentValues().apply {
                    put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, h.wonSijak)
                    if (h.haruJongil) {
                        put(CalendarContract.Events.DTSTART, naeHaruUtc(sijak))
                        put(CalendarContract.Events.DTEND, naeHaruUtc(sijak) + maxOf(HARU, kkeut - sijak))
                    } else {
                        put(CalendarContract.Events.DTSTART, sijak)
                        put(CalendarContract.Events.DTEND, kkeut)
                    }
                    put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
                }
                c.contentResolver.insert(Uri.withAppendedPath(CalendarContract.Events.CONTENT_EXCEPTION_URI, h.id.toString()), v) != null
            } else {
                val v = ContentValues().apply {
                    if (h.haruJongil) {
                        put(CalendarContract.Events.DTSTART, naeHaruUtc(sijak))
                        put(CalendarContract.Events.DTEND, naeHaruUtc(sijak) + maxOf(HARU, kkeut - sijak))
                    } else {
                        put(CalendarContract.Events.DTSTART, sijak)
                        put(CalendarContract.Events.DTEND, kkeut)
                    }
                }
                c.contentResolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, h.id), v, null, null) > 0
            }
            Girok.namgi("iljeong_bakkugi", mapOf("ok" to ok, "banbok" to h.banbok))
            ddeonaGyesan(c, true)
            ok
        } catch (e: Exception) {
            Girok.namgi("iljeong_oryu", mapOf("dan" to "bakkugi", "e" to (e.message ?: "").take(80)))
            false
        }
    }

    private fun rruleIlgi(c: Context, id: Long): String? = try {
        c.contentResolver.query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), arrayOf(CalendarContract.Events.RRULE), null, null, null)?.use { cu ->
            if (cu.moveToFirst()) cu.getString(0) else null
        }
    } catch (_: Exception) { null }

    /** 지우기 — 되풀이 일정은 modu 가 아니면 이 회만. 지운 것은 하루 동안 되살릴 수 있게 적어 둠 */
    fun jiugi(c: Context, h: IljeongHang, modu: Boolean): Boolean {
        if (!heorak(c) || !h.gilnunGeot) return false
        return try {
            val r = if (h.banbok && modu) rruleIlgi(c, h.id) else null
            val ok = if (h.banbok && !modu) {
                val v = ContentValues().apply {
                    put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, h.wonSijak)
                    put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)
                }
                c.contentResolver.insert(Uri.withAppendedPath(CalendarContract.Events.CONTENT_EXCEPTION_URI, h.id.toString()), v) != null
            } else {
                c.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, h.id), null, null) > 0
            }
            if (ok) {
                val o = JSONObject().put("jemok", h.jemok).put("jangso", h.jangso).put("sijak", h.sijak).put("kkeut", h.kkeut)
                    .put("jongil", h.haruJongil).put("rrule", r ?: "").put("ttae", System.currentTimeMillis())
                d?.edit()?.putString("jiun", o.toString())?.apply()
            }
            Girok.namgi("iljeong_jiugi", mapOf("ok" to ok, "modu" to modu))
            ddeonaGyesan(c, true)
            ok
        } catch (e: Exception) {
            Girok.namgi("iljeong_oryu", mapOf("dan" to "jiugi", "e" to (e.message ?: "").take(80)))
            false
        }
    }

    /** 하루 안에 지운 일정(되살릴 것) */
    val jiunGeot: JSONObject?
        get() {
            val o = try { JSONObject(d?.getString("jiun", "") ?: "") } catch (_: Exception) { return null }
            if (System.currentTimeMillis() - o.optLong("ttae", 0L) > HARU) return null
            return o
        }

    val jiunMal: String
        get() {
            val o = jiunGeot ?: return ""
            val h = IljeongHang(0, o.optString("jemok"), o.optLong("sijak"), o.optLong("kkeut"), o.optBoolean("jongil"), o.optString("jangso"), true, false, 0)
            return julMal(h, true) + (if (o.optString("rrule").isNotEmpty()) ", 되풀이 일정 모두" else "")
        }

    fun doesalligi(c: Context): String {
        val o = jiunGeot ?: return "되살릴 일정이 없습니다. 지운 지 하루 안의 일정만 되살릴 수 있습니다."
        val s = SaeIljeong(o.optString("jemok"), o.optString("jangso"), o.optLong("sijak"), o.optLong("kkeut"), o.optBoolean("jongil"), "", 0)
        val r = o.optString("rrule").ifEmpty { null }
        val m = jiunMal
        val id = neoki(c, s, r) ?: return "달력에 다시 넣지 못했습니다."
        d?.edit()?.remove("jiun")?.apply()
        Girok.namgi("iljeong_doesalligi", mapOf("id" to (id > 0)))
        return "되살렸습니다. $m."
    }

    // MARK: 떠날 시각

    /** 곧은 거리로 어림한 걸리는 시간(분) — 1.5킬로미터 안은 걸음(분당 50미터, 길이 굽는 것을 3할 더함), 그 넘으면 탈것(기다림·걷기 15분 + 시속 20킬로미터) */
    fun eorimBun(m: Double): Int =
        if (m <= 1500) maxOf(5, ceil(m * 1.3 / 50.0).toInt()) else ceil(15 + m * 1.4 / 333.0).toInt()

    private fun giok(): JSONObject = try { JSONObject(d?.getString("giok", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }

    /** 장소 이름을 길눈 찾기로 자리로 — 한 번 찾은 것은 적어 둠 */
    private fun jangsoChatgi(mal: String, kkeut: (Pair<Double, Double>?) -> Unit) {
        val g = giok()
        g.optJSONArray(mal)?.let { a -> kkeut(a.optDouble(0) to a.optDouble(1)); return }
        Chatgi.jangso(mal) { l ->
            val j = l?.firstOrNull()
            if (j != null) {
                val g2 = giok()
                g2.put(mal, JSONArray().put(j.lat).put(j.lon))
                if (g2.length() > 60) g2.remove(g2.keys().next())
                d?.edit()?.putString("giok", g2.toString())?.apply()
                kkeut(j.lat to j.lon)
            } else kkeut(null)
        }
    }

    /** 24시간 안의 장소 있는 일정마다 떠날 시각을 셈 — 10분 안이고 500미터 넘게 옮기지 않았으면 다시 셈하지 않음(kkok 이면 늘) */
    fun ddeonaGyesan(c: Context, kkok: Boolean) {
        junbi(c)
        if (!ddeonaKyeojim || !heorak(c)) { ddeonaMok = emptyList(); jeojangDdeona(); jaeYeyak(); return }
        val w = Wichi.jigeum ?: run { jaeYeyak(); return }
        val now = System.currentTimeMillis()
        val j0 = gyesanJari
        if (!kkok && now - gyesanTtae < 600_000L && j0 != null && Wichi.geori(j0.first, j0.second, w.lat, w.lon) < 500) return
        if (gyesanJung) { if (kkok) dasiGyesan = true; return }
        gyesanJung = true
        gyesanTtae = now
        gyesanJari = w.lat to w.lon
        val l = ilgi(c, now, now + HARU).filter { !it.haruJongil && it.jangso.isNotBlank() && it.sijak > now }.take(8)
        val out = ArrayList<IljeongDdeona>()
        fun dari(i: Int) {
            if (i >= l.size) {
                gyesanJung = false
                ddeonaMok = out
                jeojangDdeona()
                jaeYeyak()
                if (dasiGyesan) { dasiGyesan = false; ddeonaGyesan(c, true) }
                return
            }
            val h = l[i]
            jangsoChatgi(h.jangso) { jr ->
                if (jr != null) {
                    val bun = eorimBun(Wichi.geori(w.lat, w.lon, jr.first, jr.second))
                    out.add(IljeongDdeona(h.ki, h.jemok, h.jangso, h.sijak, h.sijak - (bun + yeoyu) * 60_000L, bun))
                } else {
                    Girok.namgi("iljeong_jangso_mot")
                }
                dari(i + 1)
            }
        }
        dari(0)
    }

    private fun jeojangDdeona() {
        val a = JSONArray()
        for (x in ddeonaMok) a.put(x.json())
        d?.edit()?.putString("ddeona", a.toString())?.apply()
    }

    /** 오늘 첫 일정의 떠날 시각 말(없으면 빈 글) */
    fun cheotDdeonaMal(): String {
        val now = System.currentTimeMillis()
        val x = ddeonaMok.filter { it.ddeona > now && haru(it.sijak) == haru(now) }.minByOrNull { it.sijak } ?: return ""
        return "${x.jemok}에 가시려면 어림잡아 ${sigakMal(x.ddeona)}에 떠나시면 됩니다."
    }

    // MARK: 알람

    private fun han(k: String): Boolean {
        val l = (d?.getString("han", "") ?: "").split("\n").filter { it.isNotEmpty() }
        return k in l
    }

    private fun hanNeoki(k: String) {
        val l = (d?.getString("han", "") ?: "").split("\n").filter { it.isNotEmpty() }.toMutableList()
        l.add(k)
        d?.edit()?.putString("han", l.takeLast(40).joinToString("\n"))?.apply()
    }

    private fun achimTtaeDeul(now: Long): Long? {
        if (!achimKyeojim) return null
        for (i in 0..7) {
            val c = Calendar.getInstance().apply { timeInMillis = haru(now, i); set(Calendar.HOUR_OF_DAY, achimSi); set(Calendar.MINUTE, achimBun) }
            val w = c.get(Calendar.DAY_OF_WEEK)
            val majeum = when (achimYoil) { "pyeongil" -> w != Calendar.SATURDAY && w != Calendar.SUNDAY; "jumal" -> w == Calendar.SATURDAY || w == Calendar.SUNDAY; else -> true }
            if (majeum && c.timeInMillis > now + 5_000L) return c.timeInMillis
        }
        return null
    }

    private fun pi(c: Context, beon: Int, a: String): PendingIntent =
        PendingIntent.getBroadcast(c, beon, Intent(c, IljeongReceiver::class.java).setAction(a),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    @android.annotation.SuppressLint("MissingPermission", "ScheduleExactAlarm")
    private fun geolgi(c: Context, am: AlarmManager, t: Long, p: PendingIntent) {
        try {
            if (JeonggiHochul.jeonghwakHeorak(c)) {
                val yeol = PendingIntent.getActivity(c, 7422, Intent(c, GilnunActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                am.setAlarmClock(AlarmManager.AlarmClockInfo(t, yeol), p)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p)
            }
        } catch (e: Exception) {
            try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p) } catch (_: Exception) {}
            Girok.namgi("iljeong_oryu", mapOf("dan" to "allam", "e" to (e.message ?: "").take(80)))
        }
    }

    /** 다음 아침 브리핑, 다음 떠날 시각(15분 전 또는 그때) 하나씩을 걺 */
    fun jaeYeyak() {
        val c = ctx ?: return
        val am = c.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pa = pi(c, 7420, ACHIM)
        val pd = pi(c, 7421, DDEONA)
        am.cancel(pa)
        am.cancel(pd)
        val now = System.currentTimeMillis()
        achimTtaeDeul(now)?.let { geolgi(c, am, it, pa) }
        if (ddeonaKyeojim) {
            val t = ddeonaMok.flatMap { x -> listOf(Triple(x, "a", x.ddeona - 15 * 60_000L), Triple(x, "b", x.ddeona)) }
                .filter { it.third > now + 5_000L && !han(it.first.ki + "|" + it.second) }
                .minByOrNull { it.third }
            if (t != null) geolgi(c, am, t.third, pd)
        }
    }

    // MARK: 알림 칸

    @android.annotation.SuppressLint("MissingPermission")
    private fun allimOllim(c: Context, beon: Int, jemok: String, mal: String, yeolgi: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.getNotificationChannel(CHANNEL) == null)
                    nm.createNotificationChannel(NotificationChannel(CHANNEL, "일정", NotificationManager.IMPORTANCE_HIGH))
            }
            val p = PendingIntent.getActivity(c, beon, yeolgi.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val nb = NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                .setContentTitle(jemok)
                .setContentText(mal)
                .setStyle(NotificationCompat.BigTextStyle().bigText(mal))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(p)
                .setAutoCancel(true)
            NotificationManagerCompat.from(c).notify(beon, nb.build())
        } catch (e: Exception) {
            Girok.namgi("iljeong_oryu", mapOf("dan" to "allim", "e" to (e.message ?: "").take(80)))
        }
    }

    /** 아침 브리핑 알람 때 — IljeongReceiver */
    fun achimTtae(c: Context) {
        junbi(c)
        val k = "achim|" + JeonggiHochul.ymd(System.currentTimeMillis())
        if (achimKyeojim && !han(k)) {
            hanNeoki(k)
            allimOllim(c, 7425, "길눈 아침 브리핑", "두드리시면 오늘 날짜와 날씨, 오늘 일정을 다시 읽어 드립니다.",
                Intent(c, GilnunActivity::class.java).setAction(YEOLGI_ACHIM))
            Sori.sijak(c)
            beuriping(c)
            Girok.namgi("iljeong_achim", mapOf("jadong" to true))
        }
        jaeYeyak()
    }

    /** 떠날 시각 알람 때 — IljeongReceiver */
    fun ddeonaTtae(c: Context) {
        junbi(c)
        val now = System.currentTimeMillis()
        for (x in ddeonaMok) {
            for ((dan, t) in listOf("a" to x.ddeona - 15 * 60_000L, "b" to x.ddeona)) {
                val k = x.ki + "|" + dan
                if (han(k) || now < t - 60_000L || now - t > 5 * 60_000L) continue
                hanNeoki(k)
                ddeonaAllim(c, x, dan == "b")
            }
        }
        jaeYeyak()
    }

    private fun ddeonaAllim(c: Context, x: IljeongDdeona, jigeum: Boolean) {
        Sori.sijak(c)
        val dochak = "${sigakMal(x.sijak)} ${x.jemok}"
        if (!jigeum) {
            val m = "15분 뒤에 떠나셔야 $dochak 시각에 ${x.jangso}에 닿습니다. 지금 자리에서 어림잡은 시각입니다."
            allimOllim(c, 7426, "15분 뒤 떠나실 시각", m, Intent(c, GilnunActivity::class.java))
            Sori.mal(m, MalGeup.ANNAE)
            Girok.namgi("iljeong_ddeona", mapOf("dan" to "a"))
        } else {
            val q = "지금 떠나실 시각입니다. $dochak, ${x.jangso}${IljeongMal.ro(x.jangso)} 길 안내를 시작할까요?"
            allimOllim(c, 7427, "지금 떠나실 시각입니다", "$dochak, ${x.jangso}. 두드리시면 길 안내를 여쭙니다.",
                Intent(c, GilnunActivity::class.java).setAction(YEOLGI_ANNAE).putExtra("jangso", x.jangso))
            Girok.namgi("iljeong_ddeona", mapOf("dan" to "b"))
            val a = MalHagi.hwalseong?.get()
            if (a != null && !a.isFinishing && a.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) annaeMutgi(x.jangso, q)
            else Sori.mal(q.replace("길 안내를 시작할까요?", "알림을 두드리시면 길 안내를 여쭙니다."), MalGeup.ANNAE)
        }
    }

    /** 「지금 떠나실 시각입니다. ○○으로 길 안내를 시작할까요?」 — 여쭙고 마이크를 엶 */
    fun annaeMutgi(jangso: String, q0: String? = null) {
        val q = q0 ?: "${jangso}${IljeongMal.ro(jangso)} 길 안내를 시작할까요?"
        mutgi(IljeongMureum.Annae(jangso))
        MalHagi.iljeongMutgi(q)
    }

    // MARK: 아침 브리핑

    /** 날짜, 날씨, 오늘 일정, 첫 일정 떠날 시각 — 다 말한 뒤 그 무렵 듣기 예약이 있으면 라디오를 틂 */
    fun beuriping(c: Context) {
        junbi(c)
        if (beuripingJung) return
        beuripingJung = true
        val beon = ++beuripingBeon
        val sijakTtae = System.currentTimeMillis()
        main.postDelayed({ if (beuripingBeon == beon) beuripingJung = false }, 180_000L)   // 말이 끝났다는 신호가 안 와도 3분 뒤엔 풂
        Nalssi.mal { nal ->
            val now = System.currentTimeMillis()
            val m = StringBuilder("오늘은 ${JeonggiHochul.nalMal(now)}입니다.")
            if (nal.isNotEmpty()) m.append(" 날씨는 ").append(nal).append(".")
            m.append(" ").append(oneulMal(c))
            val dd = cheotDdeonaMal()
            if (dd.isNotEmpty()) m.append(" ").append(dd)
            Sori.mal(m.toString(), MalGeup.ANNAE) {
                if (beuripingBeon == beon) beuripingJung = false
                radioIeum(c, sijakTtae)
            }
        }
    }

    /** 브리핑 무렵(시작 2분 전부터 지금까지)의 듣기 예약을 이어서 틂 */
    private fun radioIeum(c: Context, t0: Long) {
        DeutgiYeyak.junbi(c)
        val now = System.currentTimeMillis()
        val h = DeutgiYeyak.mok.filter { it.kyeojim }.mapNotNull { x ->
            DeutgiYeyak.geunalTtae(x, now)?.let { t -> if (t >= t0 - 120_000L && t <= now + 60_000L) x to t else null }
        }.minByOrNull { it.second } ?: return
        DeutgiYeyak.teulgi(c, h.first, h.second)
    }

    // MARK: 말로 하기

    private fun mutgi(m: IljeongMureum) {
        mureum = m
        mureumTtae = System.currentTimeMillis()
        mureumBeon++
    }

    fun mureumBiugi() { mureum = null }

    val mureumItda: Boolean get() = mureum != null && System.currentTimeMillis() - mureumTtae < 120_000L

    private val NEOKI = listOf("넣어", "추가", "잡아", "등록", "적어", "기록해", "저장해")
    private val BAKKUGI = listOf("바꿔", "옮겨", "변경", "미뤄", "당겨", "고쳐")
    private val JIUGI = listOf("지워", "삭제", "취소", "빼줘", "없애")
    private val ILGI = listOf("알려", "뭐", "있어", "있나", "있니", "읽어", "들려", "말해", "확인")
    private val NAMEUN_MAL = listOf("라디오", "방송", "에프엠", "fm", "정기호출", "정기콜", "복지콜", "나비콜", "콜택시", "듣기예약", "끄기예약")

    /** 일정 말이면 하고 참 — MalHagi.cheori 가 듣기 예약보다 앞에서 부름 */
    fun malCheori(alts: List<String>, z: String, t: String, dap: (String, Boolean) -> Unit): Boolean {
        val c = ctx ?: return false
        val now = System.currentTimeMillis()
        if (mureum != null && now - mureumTtae > 120_000L) mureum = null
        mureum?.let { if (dapCheori(c, it, alts, z, t, dap)) return true }
        val zl = z.lowercase()
        if (NAMEUN_MAL.any { zl.contains(it) }) return false
        val iljeongMal = z.contains("일정") || z.contains("약속") || z.contains("스케줄") || z.contains("캘린더") || z.contains("달력")
        // 아침 브리핑
        if (z.contains("브리핑")) return beuripingMal(c, z, dap)
        // 되살리기
        if ((z.contains("되살려") || z.contains("살려") || z.contains("복구") || z.contains("되돌려")) && (iljeongMal || z.contains("지운"))) {
            if (!heorakBoda(c, dap)) return true
            dap(doesalligi(c), false)
            return true
        }
        val neoki = NEOKI.any { z.contains(it) }
        val bakkugi = BAKKUGI.any { z.contains(it) }
        val jiugi = JIUGI.any { z.contains(it) }
        val p = IljeongMal.pulgi(t, now, Jeulgyeo.mokrok.take(30).map { it.ireum })
        val nalSi = p.nal != null || p.si != null || p.banbok.isNotEmpty() || p.haruJongil || p.jusu == 3
        if (neoki && !bakkugi && !jiugi && (iljeongMal || nalSi)) {
            if (!heorakBoda(c, dap)) return true
            neokiSijak(p, dap)
            return true
        }
        if (bakkugi && !jiugi && (iljeongMal || nalSi)) {
            if (!heorak(c)) { if (!iljeongMal) return false; heorakBoda(c, dap); return true }
            return chatgoHagi(c, p, "bakkugi", false, iljeongMal, dap)
        }
        if (jiugi && (iljeongMal || nalSi)) {
            if (!heorak(c)) { if (!iljeongMal) return false; heorakBoda(c, dap); return true }
            val modu = z.contains("모두") || z.contains("전부") || z.contains("되풀이") || z.contains("매주")
            return chatgoHagi(c, p, "jiugi", modu, iljeongMal, dap)
        }
        // 듣기
        if (z.contains("다음일정") || z.contains("다음약속")) {
            if (!heorakBoda(c, dap)) return true
            val h = ilgi(c, now, now + 30 * HARU).firstOrNull { it.sijak > now || it.haruJongil && haru(it.sijak) > haru(now) }
            if (h == null) { dap("앞으로 30일 안에 잡힌 일정이 없습니다.", false); return true }
            val dd = ddeonaMok.firstOrNull { it.ki == h.ki }
            dap("다음 일정은 ${julMal(h, true)}입니다." + (if (dd != null) " 어림잡아 ${sigakMal(dd.ddeona)}에 떠나시면 됩니다." else ""), false)
            return true
        }
        val ilgiMal = ILGI.any { z.contains(it) }
        val nalMuneun = p.nal != null && !iljeongMal && (z.contains("뭐있") || z.contains("약속있") || z.contains("뭐해")) && z.length <= 16
        if ((iljeongMal && !neoki && !bakkugi && !jiugi && (ilgiMal || z.length <= 12)) || nalMuneun) {
            if (!heorakBoda(c, dap)) return true
            dap(beomwiDeutgi(c, p, now), false)
            Girok.namgi("iljeong_deutgi", mapOf("jusu" to p.jusu, "nal" to (p.nal != null)))
            return true
        }
        return false
    }

    private fun beomwiDeutgi(c: Context, p: IljeongPul, now: Long): String {
        val oneul = haru(now)
        val w = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_WEEK)
        val woryoil = haru(now, -((w + 5) % 7))
        return when {
            p.jusu == 1 -> ibeonJuMal(c)
            p.jusu == 2 -> beomwiMal(c, woryoil + 7 * HARU, woryoil + 14 * HARU, "다음 주")
            p.jusu == 3 -> {
                val to = if (w == Calendar.SUNDAY) oneul else if (w == Calendar.SATURDAY) oneul else haru(now, (Calendar.SATURDAY - w))
                val kkeut = if (w == Calendar.SUNDAY) oneul + HARU else to + 2 * HARU
                beomwiMal(c, to, kkeut, "이번 주말")
            }
            p.nal != null -> beomwiMal(c, p.nal!!, p.nal!! + HARU, nalIreum(p.nal!!))
            else -> oneulMal(c)
        }
    }

    private fun heorakBoda(c: Context, dap: (String, Boolean) -> Unit): Boolean {
        if (heorak(c)) return true
        val a = MalHagi.hwalseong?.get()
        if (a != null && !a.isFinishing) {
            heorakCheong(a)
            dap("일정을 쓰려면 폰 달력 허락이 필요합니다. 허락 창이 뜨면 허용을 눌러 주신 뒤 다시 말씀해 주십시오.", false)
        } else {
            dap("일정을 쓰려면 폰 달력 허락이 필요합니다. 설정 탭의 일정에서 달력 허락하기를 눌러 주십시오.", false)
        }
        return false
    }

    /** 넣기 — 모자란 것을 여쭙고, 다 되면 한 번 읽어 드리고 여쭘 */
    private fun neokiSijak(p: IljeongPul, dap: (String, Boolean) -> Unit) {
        val now = System.currentTimeMillis()
        var nal = p.nal
        if (nal == null && p.jusu == 3) {
            val w = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_WEEK)
            nal = if (w == Calendar.SATURDAY || w == Calendar.SUNDAY) haru(now) else haru(now, Calendar.SATURDAY - w)
        }
        if (nal == null && p.si != null) {
            val tt = haru(now) + p.si!! * 3_600_000L + p.bun * 60_000L
            nal = if (tt > now) haru(now) else haru(now, 1)
        }
        if (nal == null && p.banbok.isNotEmpty() && p.banbok != "maeju") nal = haru(now)
        if (!p.haruJongil && p.si == null) {
            if (nal != null) p.nal = nal
            mutgi(IljeongMureum.Sigak(p))
            dap(if (nal == null) "언제로 할까요? 내일 오후 2시처럼 말씀해 주십시오." else "${nalIreum(nal)} 몇 시로 할까요? 하루 종일이면 하루 종일이라고 말씀해 주십시오.", true)
            return
        }
        if (nal == null) {
            mutgi(IljeongMureum.Sigak(p))
            dap("어느 날로 할까요? 내일, 금요일, 11월 3일처럼 말씀해 주십시오.", true)
            return
        }
        val s = if (p.haruJongil) SaeIljeong(p.jemok.ifBlank { "일정" }, p.jangso, nal, nal + HARU, true, p.banbok, p.yoil)
        else {
            val st = nal + p.si!! * 3_600_000L + p.bun * 60_000L
            val kk = p.kkeutSi?.let { nal + it * 3_600_000L + p.kkeutBun * 60_000L }?.takeIf { it > st } ?: (st + 3_600_000L)
            SaeIljeong(p.jemok.ifBlank { "일정" }, p.jangso, st, kk, false, p.banbok, p.yoil)
        }
        banbokMatchum(s)
        if (!s.haruJongil && s.banbok.isEmpty() && s.sijak < now) {
            dap("${nalIreum(s.sijak)} ${sigakMal(s.sijak)}는 이미 지난 시각입니다. 날짜나 시각을 다시 말씀해 주십시오.", false)
            return
        }
        mutgi(IljeongMureum.Neoki(s))
        dap(hwaginMal(s) + " 넣을까요?", true)
    }

    fun banbokMal(s: SaeIljeong): String = when (s.banbok) {
        "maeil" -> "날마다 "
        "pyeongil" -> "평일마다 "
        "jumal" -> "주말마다 "
        "maeju" -> "매주 " + JeonggiHochul.yoilIreum[Calendar.getInstance().apply { timeInMillis = s.sijak }.get(Calendar.DAY_OF_WEEK)] + "마다 "
        else -> ""
    }

    fun hwaginMal(s: SaeIljeong): String {
        val sb = StringBuilder()
        if (s.banbok.isNotEmpty()) sb.append(banbokMal(s)).append(nalIreum(s.sijak)).append("부터, ")
        else sb.append(nalIreum(s.sijak)).append(" ")
        if (s.haruJongil) sb.append("하루 종일")
        else {
            sb.append(sigakMal(s.sijak))
            if (s.kkeut - s.sijak != 3_600_000L) sb.append("부터 ").append(sigakMal(s.kkeut)).append("까지")
        }
        sb.append(", ").append(s.jemok)
        if (s.jangso.isNotBlank()) sb.append(", 장소 ").append(s.jangso)
        sb.append(".")
        return sb.toString()
    }

    /** 넣고 알려 드림 — 장소가 있으면 찾아보고 못 찾으면 말씀드림 */
    private fun neokiHagi(c: Context, s: SaeIljeong, dap: (String, Boolean) -> Unit) {
        val id = neoki(c, s)
        if (id == null) { dap("달력에 넣지 못했습니다. 폰에 달력 앱이 있는지, 구글 계정이 들어 있는지 확인해 주십시오.", false); return }
        var m = "넣었습니다."
        if (!s.haruJongil) m += " 한 시간 전과 30분 전에 폰 달력이 알려 드립니다."
        if (s.jangso.isNotBlank() && ddeonaKyeojim) m += " 떠나실 시각도 어림해 미리 알려 드립니다."
        dap(m, false)
        if (s.jangso.isNotBlank() && ddeonaKyeojim) {
            jangsoChatgi(s.jangso) { jr ->
                if (jr == null) Sori.mal("${s.jangso}${IljeongMal.eul(s.jangso)} 길 찾기에서 찾지 못해 떠날 시각 알림은 드리지 못합니다. 일정 알림은 그대로 울립니다.", MalGeup.JEONGBO)
            }
        }
    }

    /** 고치기·지우기 — 맞는 일정을 찾아 하나면 여쭙고, 여럿이면 번호를 여쭘. 못 찾으면 일정이라는 말이 있을 때만 대답 */
    private fun chatgoHagi(c: Context, p: IljeongPul, hal: String, modu: Boolean, iljeongMal: Boolean, dap: (String, Boolean) -> Unit): Boolean {
        val hubo = chatgi(c, p)
        if (hubo.isEmpty()) {
            if (!iljeongMal) return false
            dap("말씀하신 일정을 찾지 못했습니다. 일정 이름과 날짜를 함께 말씀해 주십시오. 예를 들면 내일 회의 3시로 바꿔 줘, 금요일 점심 약속 지워 줘라고 하시면 됩니다.", false)
            return true
        }
        if (hubo.size > 1) {
            val l = hubo.take(5)
            mutgi(IljeongMureum.Goreugi(l, hal, p, modu))
            dap("맞는 일정이 ${hubo.size}개 있습니다. " + l.mapIndexed { i, h -> "${i + 1}번, ${julMal(h, true)}" }.joinToString(". ") + ". 몇 번입니까?", true)
            return true
        }
        if (hal == "bakkugi") bakkugiHwagin(hubo[0], p, dap) else jiugiHwagin(hubo[0], modu, dap)
        return true
    }

    /** 말에 맞는 일정 — 날이 있으면 그날, 없으면 앞으로 30일. 제목 낱말이 가장 많이 맞는 것들 */
    private fun chatgi(c: Context, p: IljeongPul): List<IljeongHang> {
        val now = System.currentTimeMillis()
        val l = if (p.nal != null) ilgi(c, p.nal!!, p.nal!! + HARU) else ilgi(c, now - 2 * 3_600_000L, now + 30 * HARU)
        val siMat = l.filter { h ->
            val si = p.si ?: return@filter true
            if (h.haruJongil) return@filter false
            val cc = Calendar.getInstance().apply { timeInMillis = h.sijak }
            cc.get(Calendar.HOUR_OF_DAY) == si && (p.bun == 0 || cc.get(Calendar.MINUTE) == p.bun)
        }
        val nmal = p.jemokMal.filter { it.length >= 1 && it !in IljeongMal.DONGSA }
        if (nmal.isEmpty()) return if (p.nal != null || p.si != null) siMat else emptyList()
        val jeom = siMat.map { h -> h to nmal.count { w -> IljeongMal.ttuk(h.jemok + h.jangso).contains(w) } }.filter { it.second > 0 }
        val choego = jeom.maxOfOrNull { it.second } ?: return emptyList()
        return jeom.filter { it.second == choego }.map { it.first }
    }

    private fun bakkugiHwagin(h: IljeongHang, p: IljeongPul, dap: (String, Boolean) -> Unit) {
        if (!h.gilnunGeot) {
            dap("${julMal(h, true)}${IljeongMal.eun(h.jemok)} 다른 곳에서 넣은 일정이라 길눈에서는 고치지 않습니다. 폰의 달력 앱에서 고쳐 주십시오.", false)
            return
        }
        val nalRo = p.nalRo
        val siRo = p.sigakRo
        if (nalRo == null && siRo == null) {
            dap("몇 시로, 또는 어느 날로 바꿀지 함께 말씀해 주십시오. 예를 들면 내일 회의 3시로 바꿔 줘, 금요일 회의 월요일로 옮겨 줘라고 하시면 됩니다.", false)
            return
        }
        val gil = h.kkeut - h.sijak
        val geunal = nalRo ?: haru(h.sijak)
        val st = if (h.haruJongil) geunal else {
            val cc = Calendar.getInstance().apply { timeInMillis = h.sijak }
            geunal + (siRo?.first ?: cc.get(Calendar.HOUR_OF_DAY)) * 3_600_000L + (siRo?.second ?: cc.get(Calendar.MINUTE)) * 60_000L
        }
        mutgi(IljeongMureum.Bakkugi(h, st, st + gil))
        val saeMal = if (h.haruJongil) nalIreum(st) + " 하루 종일" else nalIreum(st) + " " + sigakMal(st)
        dap("${julMal(h, true)}${IljeongMal.eul(h.jemok)} ${saeMal}${IljeongMal.ro(saeMal)} 바꿀까요?" +
            (if (h.banbok) " 되풀이 일정이라 이번 한 번만 바꿉니다." else ""), true)
    }

    private fun jiugiHwagin(h: IljeongHang, modu: Boolean, dap: (String, Boolean) -> Unit) {
        if (!h.gilnunGeot) {
            dap("${julMal(h, true)}${IljeongMal.eun(h.jemok)} 다른 곳에서 넣은 일정이라 길눈에서는 지우지 않습니다. 폰의 달력 앱에서 지워 주십시오.", false)
            return
        }
        mutgi(IljeongMureum.Jiugi(h, modu))
        dap("${julMal(h, true)}${IljeongMal.eul(h.jemok)} 지울까요?" +
            (if (h.banbok && !modu) " 되풀이 일정이라 이번 한 번만 지웁니다. 모두 지우시려면 모두 지워 줘라고 말씀해 주십시오." else if (h.banbok) " 되풀이 일정을 모두 지웁니다." else ""), true)
    }

    /** 여쭌 것의 대답 — 받았으면 참 */
    private fun dapCheori(c: Context, m: IljeongMureum, alts: List<String>, z: String, t: String, dap: (String, Boolean) -> Unit): Boolean {
        val hd = MalHagi.hwaginDap(alts)
        val ye = hd == HwaginDap.YE
        val ani = hd == HwaginDap.ANI
        val now = System.currentTimeMillis()
        when (m) {
            is IljeongMureum.Neoki -> {
                if (ye) { mureum = null; neokiHagi(c, m.s, dap); return true }
                if (ani) { mureum = null; dap("넣지 않았습니다.", false); return true }
                // 「아니 3시로」처럼 바로잡으시면 고쳐서 다시 여쭘
                val p2 = IljeongMal.pulgi(t, now)
                val si = p2.sigakRo ?: p2.si?.let { it to p2.bun }
                if (si != null || p2.nal != null || p2.nalRo != null) {
                    val s = m.s
                    val gil = s.kkeut - s.sijak
                    val nal = p2.nalRo ?: p2.nal ?: haru(s.sijak)
                    if (si != null) { s.haruJongil = false; s.sijak = nal + si.first * 3_600_000L + si.second * 60_000L; s.kkeut = s.sijak + (if (gil >= HARU) 3_600_000L else gil) }
                    else { s.sijak = nal + (s.sijak - haru(s.sijak)); s.kkeut = s.sijak + gil }
                    mutgi(IljeongMureum.Neoki(s))
                    dap(hwaginMal(s) + " 넣을까요?", true)
                    return true
                }
                mureum = null
                return false
            }
            is IljeongMureum.Sigak -> {
                if (ani && z.length <= 10) { mureum = null; dap("넣지 않았습니다.", false); return true }
                val p2 = IljeongMal.pulgi(t, now)
                if (p2.si == null && !p2.haruJongil && p2.nal == null) { mureum = null; return false }
                val p = m.p
                if (p2.si != null) { p.si = p2.si; p.bun = p2.bun; p.kkeutSi = p2.kkeutSi; p.kkeutBun = p2.kkeutBun }
                if (p2.haruJongil) p.haruJongil = true
                if (p2.nal != null) p.nal = p2.nal
                mureum = null
                neokiSijak(p, dap)
                return true
            }
            is IljeongMureum.Goreugi -> {
                if (ani && z.length <= 10) { mureum = null; dap("알겠습니다. 그대로 둡니다.", false); return true }
                val b = IljeongMal.beonho(t)
                if (b == null || b < 1 || b > m.hubo.size) {
                    if (z.length <= 8) { mutgi(m); dap("1번부터 ${m.hubo.size}번 가운데 몇 번인지 말씀해 주십시오.", true); return true }
                    mureum = null
                    return false
                }
                mureum = null
                val h = m.hubo[b - 1]
                if (m.hal == "bakkugi") bakkugiHwagin(h, m.p, dap) else jiugiHwagin(h, m.modu, dap)
                return true
            }
            is IljeongMureum.Bakkugi -> {
                if (ye) {
                    mureum = null
                    dap(if (bakkugi(c, m.h, m.sijak, m.kkeut)) "바꿨습니다." else "바꾸지 못했습니다. 폰의 달력 앱에서 고쳐 주십시오.", false)
                    return true
                }
                if (ani) { mureum = null; dap("바꾸지 않았습니다.", false); return true }
                mureum = null
                return false
            }
            is IljeongMureum.Jiugi -> {
                if (ye) {
                    mureum = null
                    dap(if (jiugi(c, m.h, m.modu)) "지웠습니다. 잘못 지우셨으면 하루 안에 방금 지운 일정 되살려 줘라고 말씀해 주십시오." else "지우지 못했습니다. 폰의 달력 앱에서 지워 주십시오.", false)
                    return true
                }
                if (ani) { mureum = null; dap("지우지 않았습니다.", false); return true }
                mureum = null
                return false
            }
            is IljeongMureum.Annae -> {
                if (ye) {
                    mureum = null
                    Girok.namgi("iljeong_annae")
                    MalHagi.cheori(listOf("${m.jangso} 가자"), dap)
                    return true
                }
                if (ani) { mureum = null; dap("알겠습니다. 가실 때 말로 하기에서 ${m.jangso} 가자라고 말씀해 주십시오.", false); return true }
                mureum = null
                return false
            }
        }
        @Suppress("UNREACHABLE_CODE")
        return false
    }

    /** 「아침 브리핑 7시로 해 줘」, 「아침 브리핑 꺼 줘」, 「아침 브리핑 들려 줘」 */
    private fun beuripingMal(c: Context, z: String, dap: (String, Boolean) -> Unit): Boolean {
        if (z.contains("꺼") || z.contains("끄") || z.contains("그만") || z.contains("멈춰")) {
            achimKyeojim = false
            dap("아침 브리핑을 껐습니다.", false)
            return true
        }
        val si = JeonggiHochul.sigakDeul(z).firstOrNull()
        if (si != null) {
            var h = si.first
            if ((z.contains("아침") || z.contains("오전") || z.contains("새벽")) && h == 12) h = 0
            when { z.contains("평일") -> achimYoil = "pyeongil"; z.contains("주말") -> achimYoil = "jumal"; z.contains("매일") || z.contains("날마다") -> achimYoil = "maeil" }
            achimSi = h; achimBun = si.second; achimKyeojim = true
            dap("아침 브리핑을 $achimYoilMal ${JeonggiHochul.sigakMal(h, si.second)}로 정했습니다. 그 시각에 날짜, 날씨, 오늘 일정을 읽어 드립니다.", false)
            return true
        }
        if (z.contains("켜")) {
            achimKyeojim = true
            dap("아침 브리핑을 켰습니다. ${achimMal}에 읽어 드립니다.", false)
            return true
        }
        dap("", false)
        beuriping(c)
        Girok.namgi("iljeong_achim", mapOf("jadong" to false))
        return true
    }
}

/** 아침 브리핑·떠날 시각 알람 때, 폰을 다시 켰을 때, 새 판으로 바뀌었을 때 */
class IljeongReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Iljeong.junbi(c)
        when (i.action) {
            Iljeong.ACHIM, Iljeong.DDEONA -> {
                val p = goAsync()
                try {
                    if (i.action == Iljeong.ACHIM) Iljeong.achimTtae(c) else Iljeong.ddeonaTtae(c)
                } catch (e: Exception) {
                    Girok.namgi("iljeong_oryu", mapOf("dan" to "receiver", "e" to (e.message ?: "").take(80)))
                }
                Handler(Looper.getMainLooper()).postDelayed({ try { p.finish() } catch (_: Exception) {} }, 9000)
            }
            else -> Iljeong.jaeYeyak()
        }
    }
}

// MARK: - 화면

class IljeongHwamyeon : Hwamyeon("일정") {
    private var saePyeol = false
    private var achimPyeol = false
    private var ddeonaPyeol = false
    private var mokPyeol = false
    private var saeJemok = ""
    private var jangso = ""
    private var nalGeul = ""
    private var sigakGeul = ""
    private var jongil = false
    private var gilBun = 60
    private var banbok = ""
    private var achimGeul = ""

    override fun chaeugi(t: GilnunActivity) {
        val y = Iljeong
        y.sijak(t)
        if (!y.heorak(t)) {
            t.chojeomJul(t.geul("일정은 폰 달력에 담깁니다. 길눈이 폰 달력을 읽고 쓰려면 허락이 필요합니다. 아래 단추를 누르고 허용을 눌러 주십시오. 일정 내용은 폰 밖으로 보내지 않습니다.", true))
            t.danchu("달력 허락하기 — 누르면 허락 창이 뜹니다") { y.heorakCheong(t) }
            t.danchu("허락 창이 뜨지 않으면 — 폰 설정의 길눈 화면 열기. 권한, 캘린더에서 허용해 주십시오") {
                try { t.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + t.packageName))) } catch (_: Exception) {}
            }
            doumal(t)
            return
        }
        t.chojeomJul(t.danchu("오늘 일정 듣기") { Sori.mal(y.oneulMal(t)) })
        t.danchu("내일 일정 듣기") { Sori.mal(y.naeilMal(t)) }
        t.danchu("이번 주 일정 듣기") { Sori.mal(y.ibeonJuMal(t)) }
        nnPyeolchigi(t, "일정 넣기 펼치기", "일정 넣기 접기", saePyeol) { saePyeol = !saePyeol }
        if (saePyeol) saeChaeugi(t)
        nnPyeolchigi(t, "다가오는 일정 목록 펼치기 — 앞으로 2주", "다가오는 일정 목록 접기", mokPyeol) { mokPyeol = !mokPyeol }
        if (mokPyeol) {
            val now = System.currentTimeMillis()
            val l = y.ilgi(t, y.haru(now), y.haru(now, 14))
            if (l.isEmpty()) t.geul("앞으로 2주 동안 잡힌 일정이 없습니다.")
            for (h in l.take(40)) t.danchu(y.julMal(h, true) + (if (h.gilnunGeot) "" else " — 다른 곳에서 넣음")) { t.yeolgi(IljeongHangHwamyeon(h)) }
        }
        if (y.jiunGeot != null) t.danchu("방금 지운 일정 되살리기 — ${y.jiunMal}") { Sori.mal(y.doesalligi(t)); t.dasiGeurigi() }
        nnPyeolchigi(t, "아침 브리핑 펼치기 — 지금 ${y.achimMal}", "아침 브리핑 접기", achimPyeol) { achimPyeol = !achimPyeol }
        if (achimPyeol) {
            t.danchu(if (y.achimKyeojim) "아침 브리핑 켜져 있음 — 누르면 끕니다" else "아침 브리핑 꺼져 있음 — 누르면 켭니다") {
                y.achimKyeojim = !y.achimKyeojim
                Sori.mal(if (y.achimKyeojim) "아침 브리핑을 켰습니다. ${y.achimMal}에 읽어 드립니다." else "아침 브리핑을 껐습니다.")
                t.dasiGeurigi()
            }
            val e: EditText = t.ipryeok("브리핑 시각 — 예: 7시, 6시 반, 0700. 지금 ${JeonggiHochul.sigakMal(y.achimSi, y.achimBun)}", false)
            e.setText(achimGeul)
            e.doAfterTextChanged { achimGeul = it?.toString() ?: "" }
            t.danchu("이 시각으로 정하기") {
                val s = sigakPulgi(achimGeul, false)
                if (s == null) Sori.mal("시각을 알아보지 못했습니다. 7시, 6시 반, 0700처럼 넣어 주십시오.")
                else { y.achimSi = s.first; y.achimBun = s.second; y.achimKyeojim = true; achimGeul = ""; Sori.mal("아침 브리핑을 ${y.achimMal}로 정했습니다."); t.dasiGeurigi() }
            }
            for ((k, nm) in listOf("maeil" to "날마다", "pyeongil" to "평일만 — 월요일부터 금요일", "jumal" to "주말만 — 토요일과 일요일")) {
                t.danchu("요일 — $nm" + (if (y.achimYoil == k) " (골랐음)" else "")) { y.achimYoil = k; t.dasiGeurigi() }
            }
            t.danchu("지금 들어 보기") { y.beuriping(t) }
            t.geul("정한 시각이 되면 폰이 길눈을 깨워 오늘 날짜, 날씨, 오늘 일정, 첫 일정 떠날 시각을 읽어 드립니다. 그 무렵에 듣기 예약으로 라디오를 걸어 두셨으면 브리핑을 마친 뒤 이어서 틉니다.")
        }
        nnPyeolchigi(t, "떠날 시각 알림 펼치기 — 지금 " + (if (y.ddeonaKyeojim) "켜져 있음, 여유 ${y.yeoyu}분" else "꺼져 있음"), "떠날 시각 알림 접기", ddeonaPyeol) { ddeonaPyeol = !ddeonaPyeol }
        if (ddeonaPyeol) {
            t.danchu(if (y.ddeonaKyeojim) "떠날 시각 알림 켜져 있음 — 누르면 끕니다" else "떠날 시각 알림 꺼져 있음 — 누르면 켭니다") {
                y.ddeonaKyeojim = !y.ddeonaKyeojim
                Sori.mal(if (y.ddeonaKyeojim) "떠날 시각 알림을 켰습니다." else "떠날 시각 알림을 껐습니다.")
                t.dasiGeurigi()
            }
            for (n in listOf(10, 20, 30)) t.danchu("여유 ${n}분" + (if (y.yeoyu == n) " (골랐음)" else "")) { y.yeoyu = n; t.dasiGeurigi() }
            t.danchu("지금 자리로 다시 셈하기") { y.ddeonaGyesan(t, true); Sori.mal("지금 자리에서 다시 셈합니다."); t.dasiGeurigi() }
            val now = System.currentTimeMillis()
            val l = y.ddeonaMok.filter { it.sijak > now }
            if (l.isEmpty()) t.geul("앞으로 24시간 안에 장소가 있는 일정이 없거나, 아직 지금 자리를 몰라 셈하지 못했습니다.")
            for (x in l) t.geul("${x.jemok}, ${x.jangso} — 걸리는 시간 어림 ${x.bun}분, 떠나실 시각 ${y.nalIreum(x.ddeona)} ${y.sigakMal(x.ddeona)}")
            t.geul("장소가 있는 일정은 지금 자리에서 그곳까지의 곧은 거리로 걸리는 시간을 어림합니다. 1.5킬로미터 안은 걸어서, 그 넘으면 탈것으로 셈하고 여유를 더합니다. 떠날 시각 15분 전에 한 번, 떠날 시각에 한 번 알려 드리고, 떠날 시각에는 길 안내를 시작할지 여쭙니다. 실제 길과 차편에 따라 다를 수 있는 어림입니다.")
        }
        if (!JeonggiHochul.jeonghwakHeorak(t) && (y.achimKyeojim || y.ddeonaKyeojim)) {
            t.danchu("정확한 시각에 알리게 허락하기 — 허락 전에는 몇 분 늦을 수 있습니다") {
                try {
                    if (Build.VERSION.SDK_INT >= 31) t.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + t.packageName)))
                    Sori.mal("알람 및 리마인더 허용을 켜신 뒤 뒤로 돌아오십시오.")
                } catch (e: Exception) { Sori.mal("설정 화면을 열지 못했습니다.") }
            }
        }
        doumal(t)
    }

    private fun doumal(t: GilnunActivity) {
        t.geul("말로도 됩니다. 오늘 일정 알려 줘. 내일 뭐 있어. 이번 주 일정. 다음 일정. 내일 오후 2시 국립극장 회의 넣어 줘. 매주 화요일 오전 10시 회의 넣어 줘. 내일 회의 3시로 바꿔 줘. 금요일 점심 약속 지워 줘. 방금 지운 일정 되살려 줘. 아침 브리핑 7시로 해 줘. 아침 브리핑 들려 줘.")
    }

    /** 「7시」, 「오후 2시 반」, 「1430」 — (시, 분). ojeonOhu 면 오전·오후가 없을 때 1~6시를 오후로 */
    private fun sigakPulgi(g: String, ojeonOhu: Boolean): Pair<Int, Int>? {
        val z = MalSajeon.ttuk(g)
        Regex("^(\\d{1,2})(\\d{2})$").find(z)?.let { m ->
            val h = m.groupValues[1].toInt(); val mm = m.groupValues[2].toInt()
            return if (h in 0..23 && mm in 0..59) h to mm else null
        }
        if (ojeonOhu) {
            val p = IljeongMal.pulgi(g, System.currentTimeMillis())
            return p.si?.let { it to p.bun }
        }
        val s = JeonggiHochul.sigakDeul(z).firstOrNull() ?: return null
        var h = s.first
        if ((z.contains("아침") || z.contains("오전") || z.contains("새벽")) && h == 12) h = 0
        return h to s.second
    }

    private fun saeChaeugi(t: GilnunActivity) {
        val e1: EditText = t.ipryeok("제목 — 예: 국립극장 회의", false)
        e1.setText(saeJemok); e1.doAfterTextChanged { saeJemok = it?.toString() ?: "" }
        val e2: EditText = t.ipryeok("장소 — 비워도 됩니다. 넣으시면 떠날 시각을 알려 드립니다", false)
        e2.setText(jangso); e2.doAfterTextChanged { jangso = it?.toString() ?: "" }
        val e3: EditText = t.ipryeok("날짜 — 예: 오늘, 내일, 금요일, 11월 3일", false)
        e3.setText(nalGeul); e3.doAfterTextChanged { nalGeul = it?.toString() ?: "" }
        t.danchu(if (jongil) "하루 종일 — 골랐음, 누르면 시각을 넣습니다" else "하루 종일로 하기") { jongil = !jongil; t.dasiGeurigi() }
        if (!jongil) {
            val e4: EditText = t.ipryeok("시각 — 예: 오후 2시, 9시 반, 1430. 오전과 오후를 말하지 않으면 1시부터 6시는 오후로 봅니다", false)
            e4.setText(sigakGeul); e4.doAfterTextChanged { sigakGeul = it?.toString() ?: "" }
            for ((k, nm) in listOf(30 to "30분", 60 to "1시간", 120 to "2시간", 180 to "3시간")) {
                t.danchu("길이 — $nm" + (if (gilBun == k) " (골랐음)" else "")) { gilBun = k; t.dasiGeurigi() }
            }
        }
        for ((k, nm) in listOf("" to "되풀이 안 함", "maeil" to "날마다", "pyeongil" to "평일마다", "maeju" to "매주 같은 요일")) {
            t.danchu("되풀이 — $nm" + (if (banbok == k) " (골랐음)" else "")) { banbok = k; t.dasiGeurigi() }
        }
        t.danchu("이대로 넣기") { neoki(t) }
    }

    private fun neoki(t: GilnunActivity) {
        val y = Iljeong
        val now = System.currentTimeMillis()
        if (saeJemok.isBlank()) { Sori.mal("제목을 넣어 주십시오."); return }
        val np = IljeongMal.pulgi(nalGeul, now)
        val nal = np.nal ?: if (nalGeul.isBlank()) y.haru(now) else { Sori.mal("날짜를 알아보지 못했습니다. 내일, 금요일, 11월 3일처럼 넣어 주십시오."); return }
        val s = if (jongil) SaeIljeong(saeJemok.trim(), jangso.trim(), nal, nal + 86_400_000L, true, banbok, 0)
        else {
            val si = sigakPulgi(sigakGeul, true) ?: run { Sori.mal("시각을 알아보지 못했습니다. 오후 2시, 9시 반, 1430처럼 넣어 주십시오."); return }
            val st = nal + si.first * 3_600_000L + si.second * 60_000L
            SaeIljeong(saeJemok.trim(), jangso.trim(), st, st + gilBun * 60_000L, false, banbok, 0)
        }
        if (s.banbok == "maeju") s.yoil = Calendar.getInstance().apply { timeInMillis = s.sijak }.get(Calendar.DAY_OF_WEEK)
        y.banbokMatchum(s)
        if (!s.haruJongil && s.banbok.isEmpty() && s.sijak < now) { Sori.mal("이미 지난 시각입니다. 날짜나 시각을 다시 넣어 주십시오."); return }
        val id = y.neoki(t, s)
        if (id == null) { Sori.mal("달력에 넣지 못했습니다. 폰에 달력 앱이 있는지, 구글 계정이 들어 있는지 확인해 주십시오."); return }
        Sori.mal("넣었습니다. " + y.hwaginMal(s))
        saeJemok = ""; jangso = ""; nalGeul = ""; sigakGeul = ""; jongil = false; banbok = ""; saePyeol = false
        t.dasiGeurigi()
    }
}

/** 일정 하나 — 길 안내, 시각 바꾸기, 지우기(길눈이 넣은 일정만) */
class IljeongHangHwamyeon(private val h: IljeongHang) : Hwamyeon("일정 하나") {
    private var sigakGeul = ""
    private var nalGeul = ""
    private var jiugiHwagin = false

    override fun chaeugi(t: GilnunActivity) {
        val y = Iljeong
        val kkeut = if (h.haruJongil) "" else " 끝나는 시각 " + y.sigakMal(h.kkeut) + "."
        t.chojeomJul(t.geul(y.julMal(h, true) + "." + kkeut + (if (h.banbok) " 되풀이 일정입니다." else ""), true))
        t.geul(if (h.gilnunGeot) "길눈에서 넣은 일정입니다." else "다른 곳에서 넣은 일정이라 길눈에서는 읽기만 합니다. 고치거나 지우시려면 폰의 달력 앱을 써 주십시오.")
        if (h.jangso.isNotBlank()) {
            t.danchu("${h.jangso} — 가는 길 고르기 화면 열기") {
                Sori.mal("${h.jangso}${IljeongMal.eul(h.jangso)} 찾습니다.")
                Chatgi.jangso(h.jangso) { l ->
                    val j = l?.firstOrNull()
                    if (j == null) Sori.mal("${h.jangso}${IljeongMal.eul(h.jangso)} 찾지 못했습니다. 길 찾기 탭에서 이름이나 주소로 찾아 주십시오.")
                    else t.cheotHwamyeonEuro(MokjeokHwamyeon(j))
                }
            }
        }
        if (!h.gilnunGeot) return
        val e1: EditText = t.ipryeok(if (h.haruJongil) "옮길 날짜 — 예: 내일, 금요일, 11월 3일" else "새 시각 — 예: 오후 3시, 9시 반, 1500. 날짜를 바꾸시려면 아래 칸에", false)
        e1.setText(if (h.haruJongil) nalGeul else sigakGeul)
        e1.doAfterTextChanged { if (h.haruJongil) nalGeul = it?.toString() ?: "" else sigakGeul = it?.toString() ?: "" }
        if (!h.haruJongil) {
            val e2: EditText = t.ipryeok("새 날짜 — 비우면 그날 그대로", false)
            e2.setText(nalGeul); e2.doAfterTextChanged { nalGeul = it?.toString() ?: "" }
        }
        t.danchu("이대로 바꾸기" + (if (h.banbok) " — 되풀이 일정은 이번 한 번만" else "")) { bakkugi(t) }
        if (!jiugiHwagin) t.danchu("이 일정 지우기") { jiugiHwagin = true; Sori.mal("정말 지울까요? 아래의 정말 지우기를 누르시면 지웁니다."); t.dasiGeurigi() }
        else {
            t.danchu("정말 지우기" + (if (h.banbok) " — 이번 한 번만" else "")) {
                val ok = y.jiugi(t, h, false)
                Sori.mal(if (ok) "지웠습니다. 일정 화면의 방금 지운 일정 되살리기로 하루 안에 되살릴 수 있습니다." else "지우지 못했습니다.")
                if (ok) t.dwiro()
            }
            if (h.banbok) t.danchu("되풀이 일정 모두 지우기") {
                val ok = y.jiugi(t, h, true)
                Sori.mal(if (ok) "되풀이 일정을 모두 지웠습니다." else "지우지 못했습니다.")
                if (ok) t.dwiro()
            }
            t.danchu("지우지 않고 그만두기") { jiugiHwagin = false; t.dasiGeurigi() }
        }
    }

    private fun bakkugi(t: GilnunActivity) {
        val y = Iljeong
        val now = System.currentTimeMillis()
        val nal = if (nalGeul.isBlank()) y.haru(h.sijak) else IljeongMal.pulgi(nalGeul, now).nal ?: run { Sori.mal("날짜를 알아보지 못했습니다."); return }
        val gil = h.kkeut - h.sijak
        val st = if (h.haruJongil) nal else {
            val c = Calendar.getInstance().apply { timeInMillis = h.sijak }
            var si = c.get(Calendar.HOUR_OF_DAY) to c.get(Calendar.MINUTE)
            if (sigakGeul.isNotBlank()) {
                val z = MalSajeon.ttuk(sigakGeul)
                val m = Regex("^(\\d{1,2})(\\d{2})$").find(z)
                si = if (m != null) m.groupValues[1].toInt() to m.groupValues[2].toInt()
                else {
                    val sp = IljeongMal.pulgi(sigakGeul, now)
                    val sh = sp.si ?: run { Sori.mal("시각을 알아보지 못했습니다."); return }
                    sh to sp.bun
                }
            }
            nal + si.first * 3_600_000L + si.second * 60_000L
        }
        if (y.bakkugi(t, h, st, st + gil)) {
            Sori.mal("바꿨습니다. " + y.julMal(IljeongHang(h.id, h.jemok, st, st + gil, h.haruJongil, h.jangso, true, h.banbok, h.wonSijak), true) + ".")
            t.dwiro()
        } else Sori.mal("바꾸지 못했습니다.")
    }
}
