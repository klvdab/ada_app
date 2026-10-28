// LVD 방송(듣는 앱) — 입장 번호와 행운권 추첨 당첨 알림 (2.8.0판, 빌드 261028-L12, 새방클, 이사장님 승인 2026-10-28 「1」)
// 노트북판 현장 해설방송(9월 27일)의 듣기 화면(deut.html)이 하던 일을 앱에도 넣음 — 지금까지는 큐알로 여는 폰 인터넷 듣기 화면에서만 됐음.
// 1. 입장 번호: 이용자가 첫 화면 「내 입장 번호」에 넣거나, 큐알·태그(lvdbangsong://deut?…&n=12&g=가)로 받음. 폰에 기억.
// 2. 행사장(BYOD 방송)에서 듣는 동안 30초마다 그 번호를 방송 서버에 알림(/beonho?n=&g=&i=) — 피디 화면 「참석과 추첨」의 「지금 듣는 번호」에 잡힘.
// 3. 3초마다 방송 상태(/status)의 당첨 번호(dang)와 추첨 차례(dseq)를 봄. 새 추첨이 내 번호면
//    진동(길게 세 번) + 알림 줄(소리) + 첫 화면 말 「축하합니다. 12번, 당첨되셨습니다. 진행요원이 찾아갑니다.」를 한 번만.
//    같은 추첨을 두 번 알리지 않게 행사장 자리와 차례를 기억함. 내 번호가 아니면 아무 말도 하지 않음(떠들지 않게).
// 방송 서버 세 가지(안드로이드 송출 폰, 아이폰 송출 폰, 플린트 2 공유기)는 모두 같은 길·같은 칸을 씀(노트북판 SPEC 그대로).
package kr.or.ada.app.bfb

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.net.URLEncoder

object Chucheom {
    private const val CHANNEL = "lvd_dangcheom"
    private const val ALLIM_BEONHO = 31
    private const val BONAEGI_GANGYEOK = 30_000L
    private const val BOGI_GANGYEOK = 3_000L

    private fun pref(c: Context) = c.getSharedPreferences("lvd_bangsong", Context.MODE_PRIVATE)

    /** 내 입장 번호(숫자 1~4자리, 앞 0 뺌) — 없으면 빈 글 */
    fun beonho(c: Context): String = pref(c).getString("ipjang_beonho", "") ?: ""
    /** 구역(번호 카드에 구역이 있을 때만, 10자 안) */
    fun guyeok(c: Context): String = pref(c).getString("ipjang_guyeok", "") ?: ""

    /** 번호를 다듬음 — 숫자 1~4자리가 아니면 null, 빈 글이면 빈 글(지우기) */
    fun dadeum(t: String): String? {
        val s = t.trim().filter { !it.isWhitespace() }.removeSuffix("번")
        if (s.isEmpty()) return ""
        if (s.length > 4 || !s.all { it in '0'..'9' }) return null
        return s.trimStart('0').ifEmpty { "0" }
    }

    /** 번호를 기억함 — 빈 글이면 지움. 바뀌면 다음 차례에 곧바로 서버에 알리게 함 */
    fun dugi(c: Context, n: String, g: String = "") {
        pref(c).edit().putString("ipjang_beonho", n).putString("ipjang_guyeok", if (n.isEmpty()) "" else g.trim().take(10)).apply()
        bonaenTtae = 0L
        try { Girok.namgi("ipjang", mapOf("issum" to (n.isNotEmpty()).toString())) } catch (_: Exception) { }   // 번호 자체는 남기지 않음
    }

    /** 첫 화면 단추 글 */
    fun danchuMal(c: Context): String {
        val n = beonho(c); val g = guyeok(c)
        return if (n.isEmpty()) "내 입장 번호: 없음. 행운권 추첨이 있으면 누르고 번호를 넣습니다"
        else "내 입장 번호: " + (if (g.isEmpty()) "" else "$g 구역 ") + n + "번. 누르면 바꿉니다"
    }

    /** 이 폰의 이름표(무작위, 폰에 기억) — 노트북판 JID 와 같은 일 */
    private fun jid(c: Context): String {
        var j = pref(c).getString("jid", "") ?: ""
        if (j.length < 8) {
            val a = "0123456789abcdefghijklmnopqrstuvwxyz"
            j = (1..16).map { a[java.security.SecureRandom().nextInt(a.length)] }.joinToString("")
            pref(c).edit().putString("jid", j).apply()
        }
        return j
    }

    @Volatile private var bonaenTtae = 0L
    @Volatile private var bonTtae = 0L

    /** 듣기 일꾼이 행사장 듣는 동안 자주 부름 — 때가 된 일만 함(번호 알리기 30초, 당첨 보기 3초). 느린 일이라 듣기 줄기와 다른 일꾼에서 부를 것 */
    fun salpigi(c: Context) {
        val now = System.currentTimeMillis()
        val n = beonho(c)
        if (n.isNotEmpty() && now - bonaenTtae >= BONAEGI_GANGYEOK) {
            bonaenTtae = now
            val g = guyeok(c)
            val gil = "/beonho?n=" + n + (if (g.isEmpty()) "" else "&g=" + URLEncoder.encode(g, "UTF-8")) + "&i=" + jid(c)
            if (Hyeonjang.mutgi(c, gil, 2500) == null) bonaenTtae = now - BONAEGI_GANGYEOK + 5_000L   // 못 알렸으면 5초 뒤 다시
        }
        if (now - bonTtae < BOGI_GANGYEOK) return
        bonTtae = now
        if (n.isEmpty()) return
        val t = Hyeonjang.mutgi(c, "/status", 2500) ?: return
        val j = try { JSONObject(t) } catch (_: Exception) { return }
        val seq = j.optInt("dseq", 0)
        val dang = j.optString("dang", "")
        if (seq <= 0 || dang.isEmpty()) return
        val ki = Hyeonjang.jari + "#" + seq
        if (pref(c).getString("dang_bon", "") == ki) return
        pref(c).edit().putString("dang_bon", ki).apply()
        if (dang == n) allim(c, n)
    }

    /** 당첨 — 진동, 알림 줄(소리), 첫 화면 말 한 번 */
    private fun allim(c: Context, n: String) {
        val g = guyeok(c)
        val mal = "축하합니다. " + (if (g.isEmpty()) "" else "$g 구역 ") + n + "번, 당첨되셨습니다. 진행요원이 찾아갑니다."
        try { Girok.namgi("dangcheom", mapOf("ok" to "1")) } catch (_: Exception) { }
        jindong(c)
        try {
            if (Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= 26) {
                    val ch = NotificationChannel(CHANNEL, "행운권 당첨", NotificationManager.IMPORTANCE_HIGH)
                    ch.enableVibration(true)
                    ch.vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                    ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
                    nm.createNotificationChannel(ch)
                }
                val i = Intent(c, BangsongActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                val pi = PendingIntent.getActivity(c, 31, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val nb = NotificationCompat.Builder(c, CHANNEL)
                    .setSmallIcon(android.R.drawable.star_on)
                    .setContentTitle("행운권 당첨")
                    .setContentText(mal)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(mal))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_EVENT)
                    .setDefaults(NotificationCompat.DEFAULT_SOUND)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                nm.notify(ALLIM_BEONHO, nb.build())
            }
        } catch (_: Exception) { }
        Deut.bakkum(mal, true)   // 첫 화면 상태 줄 + 한 번 말함(화면이 없으면 알림 줄이 맡음)
    }

    private fun jindong(c: Context) {
        try {
            val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) (c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else @Suppress("DEPRECATION") (c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
            val p = longArrayOf(0, 500, 200, 500, 200, 500)
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(p, -1)) else @Suppress("DEPRECATION") v.vibrate(p, -1)
        } catch (_: Exception) { }
    }
}
