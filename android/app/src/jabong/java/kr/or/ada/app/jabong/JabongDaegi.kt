// 안드로이드 자봉 — 긴급통화 받기: 자봉 앱을 길눈님 전화기로 (2.3.0, 빌드 261002-J1 — 아이폰 JabongDaegi.swift 2.1.0과 같은 차례·같은 말·같은 나스 약속)
// ① "함께하겠습니다" 한 번에: 나스 rel.php 에 대기 등록(늘 켜 둠, hangsang=1) + 카메라·마이크·알림 허락(화면이 먼저 여쭘)
// ② 길손님이 도움을 청하면 폰이 일반 전화처럼 "길손님이 도움을 청합니다"를 띄워 울림 — 잠겨 있어도, 다른 앱을 쓰고 있어도. 받으시면 곧바로 통화(JabongTonghwa)
// ③ 다른 길눈님이 먼저 받으면 벨을 멈추고 "다른 분께 연결되었습니다. 감사합니다"
// 대표님 약속: 별명만(실명·전화번호는 화면에 없음), 자원봉사자는 수료 번호로, 해설사는 협회에 등록한 전화번호로 확인만.
//
// ★안드로이드에서 다른 점(한계): 아이폰은 나스 apns.php 가 애플 알림(VoIP)으로 폰을 깨웁니다. 나스에는 아직 구글 알림(FCM) 보내기가 없으므로
//   안드로이드는 알림 칸의 자봉(JabongDaegiService, 앞 서비스)이 떠 있으면서 5초마다 나스 rel.php a=calls&room=all&k=… 로(웹 sw_eyec.js 와 같은 물음, 답은 rows[]: room·taken·gil·where) 나에게 온 부름을 살핍니다
//   (웹 자봉 sw_eyec.js 가 쓰는 것과 같은 물음). 그래서 ①폰을 껐다 켜면 저절로 다시 기다리고(부팅 알림), ②배터리 아끼기에서 자봉을 빼 두셔야
//   화면이 꺼진 뒤에도 늦지 않게 울립니다. 나스에 구글 알림이 붙으면 이 살피기를 걷어 내고 알림으로 바꿉니다.
package kr.or.ada.app.jabong

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import kr.or.ada.app.gilnun.Girok
import kr.or.ada.app.gilnun.Sori
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

object JabongDaegi {
    const val REL = "/eyec/rel.php"
    private val main = Handler(Looper.getMainLooper())
    private val il = Executors.newSingleThreadExecutor()
    private var ctx: Context? = null
    private var d: SharedPreferences? = null

    /** 받을 갈래 — 자원봉사자(haebong), 현장영상해설사(haeseolsa) */
    val kind: String get() = d?.getString("jb.daegiKind", "") ?: ""
    val byeol: String get() = d?.getString("jb.daegiByeol", "") ?: ""
    /** 긴급통화 받기를 켜 둠 */
    val kyeojim: Boolean get() = d?.getBoolean("jb.daegiOn", false) ?: false
    val galraeIreum: String get() = if (kind.isEmpty()) "가족·지인" else if (kind == "haeseolsa") "현장영상해설사" else "자원봉사자"

    // 2.4.0 (261004-G1, 이사장님 승인 2026-10-04) 가족·지인으로 받기(이음 번호)와 긴급통화 받지 않기 — 아이폰 JabongDaegi.swift 2.4.0과 같음
    /** 가족·지인으로 등록된 길눈님 이름들 */
    val gajok: List<String> get() = try { val a = JSONArray(d?.getString("jb.gajok", "[]") ?: "[]"); (0 until a.length()).map { a.optString(it) } } catch (e: Exception) { emptyList() }
    /** 그 길눈님들이 부르실 내 이름 */
    val gajokIreum: String get() = d?.getString("jb.gajokIreum", "") ?: ""
    /** 긴급통화 받지 않기를 고르심 */
    val geobu: Boolean get() = d?.getBoolean("jb.geobu", false) ?: false
    /** 나스 대기에 올릴 갈래와 이름 — 봉사 역할이 없으면 가족·지인(jiin)으로 */
    private val daegiKind: String get() = if (kind.isEmpty()) (if (gajok.isEmpty()) "" else "jiin") else kind
    private val daegiWho: String get() = byeol.ifEmpty { gajokIreum }

    /** 이음 번호로 가족·지인 등록 — 성공하면 null, 안 되면 까닭(카메라·마이크·알림 허락은 화면이 먼저 여쭘) */
    fun gajokDeungrok(beonho: String, ireum: String, kkeut: (String?) -> Unit) {
        val b = beonho.filter { it.isDigit() }
        val nm = ireum.trim()
        if (b.length != 6) { kkeut("이음 번호 여섯 자리를 넣어 주십시오."); return }
        if (nm.isEmpty()) { kkeut("길눈님이 부르실 내 이름을 적어 주십시오."); return }
        val kk = k
        il.execute {
            val t = JbTongsin.getText("/eyec/jiin.php", mapOf("a" to "ieum", "beonho" to b, "name" to nm, "k" to kk))
            val j = try { if (t == null) null else JSONObject(t) } catch (e: Exception) { null }
            main.post {
                if (j == null) { kkeut("통신이 닿지 않았습니다. 잠시 뒤 다시 눌러 주십시오."); return@post }
                if (!j.optBoolean("ok", false)) { kkeut(j.optString("error", "").ifEmpty { "등록을 마치지 못했습니다." }); return@post }
                val who = j.optString("who", "").ifEmpty { "길눈님" }
                val l = gajok.toMutableList(); if (who !in l) l.add(who)
                d?.edit()?.putString("jb.gajok", JSONArray(l).toString())?.putString("jb.gajokIreum", nm)
                    ?.putBoolean("jb.daegiOn", true)?.putBoolean("jb.geobu", false)?.apply()
                daegiAllim(true) {
                    ctx?.let { JabongDaegiService.kyeogi(it) }
                    Girok.namgi("jabong_gajok", mapOf("android" to true))
                    byeonhwa?.invoke()
                    kkeut(null)
                }
            }
        }
    }

    /** 긴급통화 받지 않기 — 대기를 끄고 살피기도 멈춤 */
    fun geobuhagi(kkeut: () -> Unit) {
        daegiAllim(false) {
            d?.edit()?.putBoolean("jb.daegiOn", false)?.putBoolean("jb.geobu", true)?.apply()
            ctx?.let { JabongDaegiService.kkeugi(it) }
            ulimKkeut()
            Girok.namgi("jabong_geobu", mapOf("android" to true))
            byeonhwa?.invoke()
            kkeut()
        }
    }

    /** 다시 받기로 마음을 바꾸심 — 받지 않기만 풀고, 역할이 있으면 다시 켬 */
    fun geobuPulgi(kkeut: () -> Unit) {
        d?.edit()?.putBoolean("jb.geobu", false)?.apply()
        if (daegiKind.isEmpty()) { byeonhwa?.invoke(); kkeut(); return }
        swigi(false, kkeut)
    }

    /** 이 폰의 대기 열쇠(나스 rel.php 의 k) — 처음 한 번 만들어 둠(아이폰과 같은 꼴, jb + 스무 자) */
    val k: String
        get() {
            val p = d ?: return ""
            val v = p.getString("jb.daegiK", null)
            if (!v.isNullOrEmpty()) return v
            val s = "abcdefghijkmnpqrstuvwxyz23456789"
            val n = "jb" + (0 until 20).map { s.random() }.joinToString("")
            p.edit().putString("jb.daegiK", n).apply()
            return n
        }

    /** 지금 울리는 부름 */
    class Ulim(val room: String, val mok: String, val gal: String, val ttae: Long, val who: String = "")
    var ulim: Ulim? = null
        private set
    /** 형편이 바뀌면 화면이 채움(울림 시작·끝, 받기 켜고 끔) */
    var byeonhwa: (() -> Unit)? = null
    /** 울림을 화면에 띄워 달라고(화면이 채움 — 앱이 앞에 있을 때) */
    var ulimHwamyeon: (() -> Unit)? = null

    private val bon = LinkedHashSet<String>()          // 이미 울렸거나 넘긴 방 — 다시 울리지 않음
    private var salpimR: Runnable? = null
    private var ulimR: Runnable? = null
    private var majimakDaegi = 0L

    fun sijak(c: Context) {
        if (ctx != null) return
        ctx = c.applicationContext
        d = c.applicationContext.getSharedPreferences("jabong", Context.MODE_PRIVATE)
        kr.or.ada.app.gilnun.IceJuso.sijak(c)
        kr.or.ada.app.gilnun.IceJuso.gaengsin()
        if (kyeojim) {
            daegiAllim(true)   // 켤 때마다 대기를 새로 알림
            JabongDaegiService.kyeogi(c)
        }
    }

    // MARK: 함께하겠습니다 / 잠시 쉬기

    /** 함께하겠습니다 — 성공하면 null, 안 되면 까닭(카메라·마이크·알림 허락은 화면이 먼저 여쭘) */
    fun hamkke(kind0: String, byeol0: String, hwagin: String, kkeut: (String?) -> Unit) {
        val b = byeol0.trim()
        if (b.isEmpty()) { kkeut("길눈님 별명을 적어 주십시오."); return }
        val q = hashMapOf("a" to "daegi", "k" to k, "on" to "1", "kind" to kind0, "who" to b, "hangsang" to "1")
        if (kind0 == "haeseolsa") q["tel"] = hwagin else q["surye"] = hwagin
        il.execute {
            val t = JbTongsin.getText(REL, q)
            val j = try { if (t == null) null else JSONObject(t) } catch (e: Exception) { null }
            main.post {
                if (j == null) { kkeut("통신이 닿지 않았습니다. 잠시 뒤 다시 눌러 주십시오."); return@post }
                if (!j.optBoolean("ok", false)) { kkeut(j.optString("msg", "").ifEmpty { "함께하기를 마치지 못했습니다." }); return@post }
                d?.edit()?.putString("jb.daegiKind", kind0)?.putString("jb.daegiByeol", b)?.putBoolean("jb.daegiOn", true)?.putBoolean("jb.geobu", false)?.apply()
                majimakDaegi = System.currentTimeMillis()
                ctx?.let { JabongDaegiService.kyeogi(it) }
                Girok.namgi("jabong_hamkke", mapOf("kind" to kind0, "android" to true))
                byeonhwa?.invoke()
                kkeut(null)
            }
        }
    }

    /** 잠시 쉬기(swim=true) / 다시 함께하기(false) */
    fun swigi(swim: Boolean, kkeut: () -> Unit) {
        daegiAllim(!swim) {
            d?.edit()?.putBoolean("jb.daegiOn", !swim)?.apply()
            val c = ctx
            if (c != null) { if (swim) JabongDaegiService.kkeugi(c) else JabongDaegiService.kyeogi(c) }
            if (swim) ulimKkeut()
            byeonhwa?.invoke()
            kkeut()
        }
    }

    private fun daegiAllim(on: Boolean, kkeut: (() -> Unit)? = null) {
        val ki = daegiKind
        val by = daegiWho
        if (ki.isEmpty() || by.isEmpty()) { kkeut?.let { main.post(it) }; return }
        val kk = k
        il.execute {
            JbTongsin.getText(REL, mapOf("a" to "daegi", "k" to kk, "on" to if (on) "1" else "0", "kind" to ki, "who" to by, "hangsang" to "1"))
            if (on) majimakDaegi = System.currentTimeMillis()
            kkeut?.let { main.post(it) }
        }
    }

    // MARK: 부름 살피기 — 알림 칸의 자봉이 5초마다(아이폰은 애플 알림이 깨움)

    @Volatile private var salpineun = false

    fun bureumSalpigi() {
        if (!kyeojim || salpineun) return
        // 10분마다 대기를 새로 알림 — 나스가 나를 잊지 않게
        if (System.currentTimeMillis() - majimakDaegi > 10 * 60 * 1000L) daegiAllim(true)
        salpineun = true
        val kk = k
        il.execute {
            val t = JbTongsin.getText(REL, mapOf("a" to "calls", "room" to "all", "k" to kk), 8000)
            main.post {
                salpineun = false
                if (t != null) bureumBatda(t)
            }
        }
    }

    /** a=calls 의 답 — 줄마다 room·galrae·mok·taken. 답 모양이 바뀌어도 견디게 여러 이름을 살핌 */
    private fun bureumBatda(t: String) {
        val l: JSONArray = try {
            val tt = t.trim()
            if (tt.startsWith("[")) JSONArray(tt) else {
                val o = JSONObject(tt)
                o.optJSONArray("calls") ?: o.optJSONArray("list") ?: o.optJSONArray("rows") ?: o.optJSONArray("items") ?: JSONArray()
            }
        } catch (e: Exception) { return }
        for (i in 0 until l.length()) {
            val x = l.optJSONObject(i) ?: continue
            val room = x.optString("room", "")
            if (room.isEmpty()) continue
            if (chamgap(x.opt("taken")) || chamgap(x.opt("takenK")) || chamgap(x.opt("taken_k"))) continue
            if (room in bon) continue
            if (ulim != null || JabongTonghwa.tonghwaJung) continue
            val mok = if (x.isNull("mok")) (if (x.isNull("gil")) "" else x.optString("gil", "")) else x.optString("mok", "")
            ulimSijak(room, mok, if (x.isNull("galrae")) "" else x.optString("galrae", ""), if (x.isNull("who")) "" else x.optString("who", ""))
        }
        // 오래된 방 이름은 덜어 냄
        while (bon.size > 200) bon.remove(bon.first())
    }

    /** taken 이 비었거나 false·0·"" 이면 아직 아무도 안 받음 */
    private fun chamgap(v: Any?): Boolean = when (v) {
        null, JSONObject.NULL -> false
        is Boolean -> v
        is Number -> v.toDouble() != 0.0
        is String -> v.isNotEmpty() && v != "0" && v != "false" && v != "null"
        else -> true
    }

    // MARK: 울림

    private fun ulimSijak(room: String, mok: String, gal: String, who: String = "") {
        val c = ctx ?: return
        bon.add(room)
        val u = Ulim(room, mok, gal, System.currentTimeMillis(), who)
        kr.or.ada.app.gilnun.IceJuso.gaengsin()   // 2.5.0 받기 전에 영상 다리 주소를 새로
        ulim = u
        Girok.namgi("jabong_ulim", mapOf("gal" to gal, "android" to true))
        JabongUlim.kyeogi(c, gal, who)
        ulimHwamyeon?.invoke()
        byeonhwa?.invoke()
        salpigi(u)
    }

    /** 울리는 동안 2초마다: 다른 분이 받았는지, 길손님이 그만두었는지. 1분 30초 넘게 울리면 멈춤(길손님 쪽 기다림과 같음) */
    private fun salpigi(u: Ulim) {
        salpimR?.let { main.removeCallbacks(it) }
        val r = object : Runnable {
            override fun run() {
                val cur = ulim
                if (cur == null || cur.room != u.room) return
                if (System.currentTimeMillis() - u.ttae > 100000) { ulimKkeut(); return }
                il.execute {
                    val t = JbTongsin.getText(REL, mapOf("a" to "jindo", "room" to u.room), 8000)
                    val j = try { if (t == null) null else JSONObject(t) } catch (e: Exception) { null }
                    main.post {
                        val cc = ulim
                        if (cc == null || cc.room != u.room) return@post
                        if (j != null) {
                            val sal = if (j.has("sal")) j.optBoolean("sal", true) else true
                            val takenK = if (j.isNull("takenK")) "" else j.optString("takenK", "")
                            if (takenK.isNotEmpty() && takenK != k) {
                                ulimKkeut()
                                Sori.mal("다른 분께 연결되었습니다. 감사합니다.")
                                return@post
                            } else if (!sal) {
                                ulimKkeut()
                                return@post
                            }
                        }
                        main.postDelayed(this, 2000)
                    }
                }
            }
        }
        salpimR = r
        main.postDelayed(r, 2000)
    }

    private fun ulimKkeut() {
        salpimR?.let { main.removeCallbacks(it) }
        salpimR = null
        ulim = null
        ctx?.let { JabongUlim.kkeugi(it) }
        byeonhwa?.invoke()
    }

    /** 받기 — 나스에 내가 받았다고(take). 다른 분이 먼저 받으셨으면 그렇다고 */
    fun batgi() {
        val u = ulim ?: return
        salpimR?.let { main.removeCallbacks(it) }
        salpimR = null
        ulim = null
        ctx?.let { JabongUlim.kkeugi(it) }
        val by = daegiWho
        val kk = k
        il.execute {
            val t = JbTongsin.getText(REL, mapOf("a" to "take", "room" to u.room, "who" to by, "k" to kk))
            val j = try { if (t == null) null else JSONObject(t) } catch (e: Exception) { null }
            main.post {
                if (j != null && j.has("ok") && !j.optBoolean("ok", true)) {
                    Sori.mal("다른 분께 연결되었습니다. 감사합니다.")
                    byeonhwa?.invoke()
                    return@post
                }
                val c = ctx
                if (c != null) JabongTonghwa.sijak(c, u.room, u.mok)
                Girok.namgi("jabong_batum", mapOf("android" to true))
                byeonhwa?.invoke()
            }
        }
    }

    /** 거절 — 이 부름은 울리지 않음(아이폰 전화 화면의 거절과 같이 나스에는 알리지 않음, 다른 길눈님은 그대로 울림) */
    fun geojeol() {
        if (ulim == null) return
        Girok.namgi("jabong_geojeol", mapOf("android" to true))
        ulimKkeut()
    }
}

/** 전화처럼 울리기 — 벨소리·진동·잠긴 화면 위에 뜨는 알림(받기·거절 단추). 아이폰 CallKit 자리 */
object JabongUlim {
    private const val CH = "jabong_ulim"
    private const val BEONHO = 31
    const val BATGI = "kr.or.ada.jabong.BATGI"
    const val ULIM = "kr.or.ada.jabong.ULIM"
    const val GEOJEOL = "kr.or.ada.jabong.GEOJEOL"
    private var beul: Ringtone? = null
    private var jindongi: Vibrator? = null

    @Suppress("DEPRECATION")
    /** 2.4.0 울림 제목 — 가족·지인 부름이면 부르신 길눈님 이름 */
    fun jemok(gal: String, who: String): String = when {
        gal == "jiin" -> if (who.isEmpty()) "길눈님이 화상통화를 요청합니다" else "$who 님이 화상통화를 요청합니다"
        gal == "haeseolsa" -> "길손님이 현장영상해설사를 청합니다"
        else -> "길손님이 도움을 청합니다"
    }

    fun kyeogi(c: Context, gal: String, who: String = "") {
        val jemok = jemok(gal, who)
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CH, "자봉 긴급통화 울림", NotificationManager.IMPORTANCE_HIGH)
            ch.setSound(null, null)          // 벨소리는 따로 냄(겹치지 않게)
            ch.enableVibration(false)
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ch)
        }
        val gi = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val ulimI = Intent(c, JabongActivity::class.java).setAction(ULIM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val batgiI = Intent(c, JabongActivity::class.java).setAction(BATGI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val geojeolI = Intent(c, JabongDaegiService::class.java).setAction(GEOJEOL)
        val n = NotificationCompat.Builder(c, CH)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(jemok)
            .setContentText("받기를 누르시면 곧바로 길손님 카메라 화면과 말소리가 이어집니다")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(100000)
            .setContentIntent(PendingIntent.getActivity(c, 1, ulimI, gi))
            .setFullScreenIntent(PendingIntent.getActivity(c, 2, ulimI, gi), true)
            .addAction(android.R.drawable.sym_action_call, "받기", PendingIntent.getActivity(c, 3, batgiI, gi))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "거절", PendingIntent.getService(c, 4, geojeolI, gi))
            .build()
        try { nm.notify(BEONHO, n) } catch (e: Exception) { Girok.namgi("jabong_ulim_oryu", mapOf("e" to (e.message ?: ""))) }
        // 벨소리 — 폰의 전화 벨소리를 전화 소리 자리로 되풀이
        try {
            beul?.stop()
            val r = RingtoneManager.getRingtone(c, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            if (r != null) {
                r.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= 28) r.isLooping = true
                r.play()
            }
            beul = r
        } catch (e: Exception) {}
        // 진동 — 1초 떨고 1초 쉼을 되풀이
        try {
            val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31)
                (c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            else c.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            val gil = longArrayOf(0, 1000, 1000)
            if (Build.VERSION.SDK_INT >= 26) v?.vibrate(VibrationEffect.createWaveform(gil, 0)) else v?.vibrate(gil, 0)
            jindongi = v
        } catch (e: Exception) {}
    }

    fun kkeugi(c: Context) {
        try { beul?.stop() } catch (e: Exception) {}
        beul = null
        try { jindongi?.cancel() } catch (e: Exception) {}
        jindongi = null
        try { (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(BEONHO) } catch (e: Exception) {}
    }
}

/** 긴급통화를 기다리는 알림 칸의 자봉 — 5초마다 나에게 온 부름을 살핌(아이폰은 애플 알림이 깨우므로 이 일이 없음) */
class JabongDaegiService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var jamsoe: PowerManager.WakeLock? = null
    private val salpim = object : Runnable {
        override fun run() {
            if (!JabongDaegi.kyeojim) { stopSelf(); return }
            jamsoeIeum()
            JabongDaegi.bureumSalpigi()
            main.postDelayed(this, 5000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        JabongBonche.sijak(this)
        if (intent?.action == JabongUlim.GEOJEOL) {
            JabongDaegi.geojeol()
            if (!JabongDaegi.kyeojim) { stopSelf(); return START_NOT_STICKY }
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("jabong_daegi", "자봉 긴급통화 받기", NotificationManager.IMPORTANCE_LOW))
        val yeolgi = PendingIntent.getActivity(this, 5, Intent(this, JabongActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "jabong_daegi") else Notification.Builder(this)
        val n = b.setContentTitle("자봉 — 긴급통화를 받고 있습니다")
            .setContentText("길손님이 도움을 청하면 전화처럼 울립니다")
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentIntent(yeolgi).setOngoing(true).build()
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(3, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(3, n)
        } catch (e: Exception) {
            Girok.namgi("jb_daegi_service", mapOf("ok" to false, "e" to (e.message ?: "")))
            stopSelf()
            return START_NOT_STICKY
        }
        if (!JabongDaegi.kyeojim) { stopSelf(); return START_NOT_STICKY }
        main.removeCallbacks(salpim)
        main.post(salpim)
        return START_STICKY
    }

    /** 화면이 꺼져도 살피기가 잠들지 않게 — 10분짜리 잠금을 살필 때마다 이어 붙임 */
    private fun jamsoeIeum() {
        try {
            val j = jamsoe ?: (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jabong:daegi").also { it.setReferenceCounted(false); jamsoe = it }
            j.acquire(10 * 60 * 1000L)
        } catch (e: Exception) {}
    }

    override fun onDestroy() {
        main.removeCallbacks(salpim)
        try { jamsoe?.takeIf { it.isHeld }?.release() } catch (e: Exception) {}
        jamsoe = null
        super.onDestroy()
    }

    companion object {
        fun kyeogi(c: Context) {
            try {
                val i = Intent(c, JabongDaegiService::class.java)
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
            } catch (e: Exception) { Girok.namgi("jb_daegi_service", mapOf("ok" to false, "kyeogi" to (e.message ?: ""))) }
        }
        fun kkeugi(c: Context) { try { c.stopService(Intent(c, JabongDaegiService::class.java)) } catch (e: Exception) {} }
    }
}

/** 폰을 껐다 켜면 — 긴급통화 받기를 켜 두셨으면 알림 칸의 자봉이 다시 기다림 */
class JabongBootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED && i.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        JabongBonche.sijak(c)
    }
}
