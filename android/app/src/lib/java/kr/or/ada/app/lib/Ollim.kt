// 협회 안드로이드 앱 공통 — 새 판 알림과 업데이트(1.1.0판, 빌드 261007-U2, 이사장님 승인 2026-10-07 「1」)
// 1.1.0(261007-U2) 막기(makgi) — 앱이 「지금은 안 됨」을 알려 주면 업데이트하지 않음(자봉 점지도 그리는 중, BYOD 방송 중 — 설치하면 앱이 꺼지므로)
// AI점자도서관 갈래 사본 — 길눈 부품(src/gilnun/.../Ollim.kt)과 같은 것을 lib 패키지에 옮김(이사장님 지시 「차별하지 말고 모두」). 기록은 안드로이드 로그로, 말은 앱이 채운 malhagi 로.
// 하는 일:
//   1. 앱을 켤 때와 앱으로 돌아올 때(1시간에 한 번까지), 그리고 앱이 닫혀 있어도 하루 두 번쯤(안드로이드 일꾼)
//      협회 판 번호 한 장(https://lvd.ada.or.kr/sihum/pan.json)을 받아 이 앱의 지금 판과 견줌
//   2. 새 판이 있으면 폰 알림 「길눈 새 판이 나왔습니다. 두드리면 업데이트합니다」(같은 판은 한 번만 알림)
//   3. 알림을 두드리거나 앱 안의 업데이트 단추를 누르면 새 판을 스스로 받아 안드로이드 설치 화면을 엶 — 이용자는 설치 한 번
//      (앱이 한 번 스스로 업데이트한 뒤로는 안드로이드 12 이상에서 설치 확인 없이 깔릴 수 있음 — 안드로이드가 정함)
//   4. 새 판으로 바뀌면 폰 알림 「길눈이 새 판 ○○으로 바뀌었습니다」
// 처음 한 번은 「이 앱에서 설치 허용」을 켜야 함 — 꺼져 있으면 그 설정 화면을 바로 열어 드림.
package kr.or.ada.app.lib

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object Ollim {
    const val JULI = "https://lvd.ada.or.kr/sihum/pan.json"
    const val YEOLGI = "kr.or.ada.ollim.YEOLGI"          // 알림을 두드려 앱이 열리면 곧바로 업데이트
    private const val CHANNEL = "ada_ollim"
    private const val IL_IREUM = "ada_ollim"
    private const val SESSION_GYEOLGWA = "kr.or.ada.ollim.GYEOLGWA"

    private val main = Handler(Looper.getMainLooper())
    private var ctx: Context? = null

    /** 새 판(없으면 null) — 판 번호, 받을 주소 */
    data class SaePan(val pan: String, val apk: String)
    var sae: SaePan? = null
        private set
    /** 받는 중·설치 중 */
    var jinhaengJung = false
        private set
    /** 받는 중 퍼센트(화면을 다시 그릴 때만 읽음 — 낭독기가 쉬지 않고 떠들지 않게 숫자로는 알리지 않음) */
    var peosenteu = 0
        private set
    /** 마지막 알릴 말(실패·설치 허용 필요 등) */
    var allim = ""
        private set
    /** 새 판이 생기거나 받기가 끝나는 등 단계가 바뀔 때만 — 화면을 다시 그리게(앱마다 채움) */
    var byeonhwa: (() -> Unit)? = null
    /** 말할 곳(앱마다 채움 — 길눈은 Sori.mal) */
    var malhagi: ((String) -> Unit)? = null
    /** 지금 업데이트하면 안 되는 까닭(없으면 null) — 앱마다 채움. 설치하면 앱이 꺼지므로 그리는 중·방송 중에는 막음 */
    var makgi: (() -> String?)? = null

    private fun girok(e: String, d: Map<String, Any?> = emptyMap()) { android.util.Log.i("ada_ollim", "$e $d") }

    private var majimakSalpim = 0L
    private var heorakGidarim = false

    fun aeBeonho(c: Context): String = when (c.packageName) {
        "kr.or.ada.app" -> "gilnun"
        "kr.or.ada.jabong" -> "jabong"
        "kr.or.ada.byod" -> "byod"
        "kr.or.ada.lib" -> "lib"
        else -> ""
    }

    fun aeIreum(c: Context): String = when (aeBeonho(c)) {
        "gilnun" -> "길눈"
        "jabong" -> "자봉"
        "byod" -> "BYOD 방송"
        "lib" -> "AI점자도서관"
        else -> "앱"
    }

    fun jigeumPan(c: Context): String = try {
        @Suppress("DEPRECATION")
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: ""
    } catch (e: Exception) { "" }

    /** 2.21.0 과 2.22.0 처럼 점으로 나뉜 판 번호를 견줌 — a 가 b 보다 새것이면 참 */
    fun deoSae(a: String, b: String): Boolean {
        val x = a.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val y = b.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrElse(i) { 0 }; val q = y.getOrElse(i) { 0 }
            if (p != q) return p > q
        }
        return false
    }

    /** 앱을 켤 때 한 번 — 곧바로 살피고, 뒤 일꾼을 맡겨 둠 */
    fun sijak(c: Context) {
        ctx = c.applicationContext
        salpigi(c)
        try {
            val r = PeriodicWorkRequestBuilder<OllimWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(c.applicationContext).enqueueUniquePeriodicWork(IL_IREUM, ExistingPeriodicWorkPolicy.KEEP, r)
        } catch (e: Exception) {
            girok("ollim_yeyak_oryu", mapOf("e" to (e.message ?: "").take(80)))
        }
    }

    /** 앱으로 돌아올 때 — 1시간에 한 번까지만 살핌 */
    fun dorawatda(a: Activity) {
        if (heorakGidarim) { heorakGidarim = false; if (sae != null && seolchiHeorak(a)) olligi(a); return }
        if (System.currentTimeMillis() - majimakSalpim > 3_600_000L) salpigi(a)
    }

    /** 판 번호 한 장을 받아 견줌. kkeut(새 판이 있는가) — 손으로 누른 「새 판 살피기」에서 씀 */
    fun salpigi(c: Context, kkeut: ((Boolean?) -> Unit)? = null) {
        val ac = c.applicationContext
        majimakSalpim = System.currentTimeMillis()
        Thread {
            var chat: SaePan? = null
            var bateum = false
            try {
                val u = URL("$JULI?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
                u.connectTimeout = 10000; u.readTimeout = 15000; u.useCaches = false
                if (u.responseCode == 200) {
                    val j = JSONObject(u.inputStream.bufferedReader().readText())
                    val a = j.optJSONObject(aeBeonho(ac))?.optJSONObject("android")
                    val pan = a?.optString("pan", "") ?: ""
                    val apk = a?.optString("apk", "") ?: ""
                    bateum = true
                    if (pan.isNotEmpty() && apk.startsWith("https://") && deoSae(pan, jigeumPan(ac))) chat = SaePan(pan, apk)
                }
                u.disconnect()
            } catch (e: Exception) {
                girok("ollim_salpim_oryu", mapOf("e" to (e.message ?: "").take(80)))
            }
            main.post {
                val ap = sae
                val ch = chat
                if (bateum) sae = ch
                if (ch != null && ap?.pan != ch.pan) { allimHagi(ac, ch); byeonhwa?.invoke() }
                if (ch == null && ap != null && bateum) byeonhwa?.invoke()
                kkeut?.invoke(if (bateum) ch != null else null)
            }
        }.start()
    }

    // MARK: 화면 줄 — 안내와 단추를 한 자리에(첫 화면 맨 위, 설정)

    /** 첫 화면 맨 위 한 줄 — 새 판이 있을 때만 */
    fun julMal(c: Context): String? {
        val s = sae ?: return null
        if (jinhaengJung) return "${aeIreum(c)} 새 판 ${s.pan}을 받는 중입니다. 지금 ${peosenteu}퍼센트. 다 받으면 설치 화면이 열립니다"
        return "${aeIreum(c)} 새 판 ${s.pan}이 나왔습니다. 두드리면 업데이트합니다"
    }

    /** 설정 안 단추 글 — 늘 있음 */
    fun seoljeongMal(c: Context): String {
        val s = sae
        return when {
            s != null && jinhaengJung -> "업데이트 — 새 판 ${s.pan}을 받는 중입니다"
            s != null -> "업데이트 — 새 판 ${s.pan}이 있습니다. 누르면 업데이트합니다(지금 ${jigeumPan(c)}판)"
            else -> "업데이트 — 지금 ${jigeumPan(c)}판. 누르면 새 판이 있는지 살핍니다"
        }
    }

    /** 설정 단추를 누르면 — 새 판이 있으면 업데이트, 없으면 살펴서 알려 드림 */
    fun seoljeongNureum(a: Activity) {
        if (sae != null) { olligi(a); return }
        mal("새 판이 있는지 살핍니다.")
        salpigi(a) { r ->
            when (r) {
                true -> { mal("${aeIreum(a)} 새 판 ${sae?.pan}이 있습니다. 바로 업데이트합니다."); olligi(a) }
                false -> mal("지금 ${jigeumPan(a)}판이 가장 새 판입니다.")
                null -> mal("새 판을 살피지 못했습니다. 인터넷을 확인하시고 다시 눌러 주십시오.")
            }
        }
    }

    private fun mal(t: String) {
        allim = t
        malhagi?.invoke(t)
    }

    // MARK: 받아서 설치

    /** 알림으로 열렸으면 곧바로 업데이트 */
    fun intentBoda(a: Activity, i: Intent?) {
        if (i == null || !i.getBooleanExtra(YEOLGI, false)) return
        i.removeExtra(YEOLGI)
        if (sae != null) olligi(a) else salpigi(a) { r -> if (r == true) olligi(a) }
    }

    /** 이 앱이 다른 앱을 설치해도 되는가 — 처음 한 번 설정에서 켜야 함 */
    private fun seolchiHeorak(c: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || c.packageManager.canRequestPackageInstalls()

    fun olligi(a: Activity) {
        val s = sae ?: return
        makgi?.invoke()?.let { mal(it); return }
        if (jinhaengJung) { mal("새 판을 받는 중입니다. 잠시만 기다려 주십시오."); return }
        if (!seolchiHeorak(a)) {
            heorakGidarim = true
            mal("처음 한 번만 허락이 필요합니다. 이어서 열리는 화면에서 이 출처 허용을 켜신 뒤, 폰의 뒤로 동작으로 돌아오시면 업데이트를 이어 합니다.")
            main.postDelayed({
                try {
                    a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.packageName)))
                } catch (e: Exception) {
                    heorakGidarim = false
                    mal("설정 화면을 열지 못했습니다. 시험판 받기 화면에서 새 판을 받아 주십시오.")
                }
            }, 2500)
            return
        }
        jinhaengJung = true
        peosenteu = 0
        mal("${aeIreum(a)} 새 판 ${s.pan}을 받습니다. 다 받으면 설치 화면이 열립니다. 설치를 눌러 주십시오.")
        byeonhwa?.invoke()
        val ac = a.applicationContext
        Thread {
            val f = File(ac.cacheDir, "ollim").apply { mkdirs() }.let { File(it, "sae.apk") }
            var ok = false
            try {
                val u = URL(s.apk).openConnection() as HttpURLConnection
                u.connectTimeout = 15000; u.readTimeout = 30000; u.instanceFollowRedirects = true
                if (u.responseCode == 200) {
                    val modu = u.contentLengthLong
                    var batun = 0L
                    u.inputStream.use { ip -> f.outputStream().use { op ->
                        val b = ByteArray(65536)
                        while (true) {
                            val n = ip.read(b); if (n < 0) break
                            op.write(b, 0, n); batun += n
                            if (modu > 0) peosenteu = ((batun * 100) / modu).toInt()
                        }
                    } }
                    ok = f.length() > 100_000 && (modu <= 0 || batun == modu)
                }
                u.disconnect()
            } catch (e: Exception) {
                girok("ollim_batgi_oryu", mapOf("e" to (e.message ?: "").take(80)))
            }
            main.post {
                if (!ok) {
                    jinhaengJung = false
                    mal("새 판을 받지 못했습니다. 와이파이나 데이터를 확인하시고 다시 눌러 주십시오.")
                    byeonhwa?.invoke()
                } else seolchi(ac, f, s)
            }
        }.start()
    }

    private fun seolchi(c: Context, f: File, s: SaePan) {
        try {
            val pi = c.packageManager.packageInstaller
            val p = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            p.setAppPackageName(c.packageName)
            if (Build.VERSION.SDK_INT >= 31) p.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            val id = pi.createSession(p)
            pi.openSession(id).use { se ->
                se.openWrite("sae.apk", 0, f.length()).use { op -> f.inputStream().use { it.copyTo(op) }; se.fsync(op) }
                val i = Intent(c, OllimReceiver::class.java).setAction(SESSION_GYEOLGWA)
                val fl = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                se.commit(PendingIntent.getBroadcast(c, id, i, fl).intentSender)
            }
            girok("ollim_seolchi", mapOf("pan" to s.pan))
        } catch (e: Exception) {
            jinhaengJung = false
            girok("ollim_seolchi_oryu", mapOf("e" to (e.message ?: "").take(80)))
            mal("설치를 시작하지 못했습니다. 시험판 받기 화면에서 새 판을 받아 주십시오.")
            byeonhwa?.invoke()
        }
    }

    /** 설치 결과(OllimReceiver 가 부름) */
    internal fun gyeolgwa(c: Context, i: Intent) {
        when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val h = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (h != null) {
                    h.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { c.startActivity(h) } catch (e: Exception) { girok("ollim_hwakin_oryu") }
                }
                main.post { mal("설치 화면에서 설치를 눌러 주십시오.") }
            }
            PackageInstaller.STATUS_SUCCESS -> main.post { jinhaengJung = false }
            else -> main.post {
                jinhaengJung = false
                val m = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
                girok("ollim_silpae", mapOf("m" to m.take(80)))
                mal("설치하지 않았습니다. 다시 하시려면 업데이트를 한 번 더 눌러 주십시오.")
                byeonhwa?.invoke()
            }
        }
    }

    // MARK: 폰 알림

    private fun tongro(c: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL) == null)
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "앱 새 판 알림", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun ttuiugi(c: Context, beon: Int, jemok: String, mal: String, yeolgiOllim: Boolean) {
        try {
            tongro(c)
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val yeol = c.packageManager.getLaunchIntentForPackage(c.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                if (yeolgiOllim) putExtra(YEOLGI, true)
            }
            val b = NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(jemok)
                .setContentText(mal)
                .setStyle(NotificationCompat.BigTextStyle().bigText(mal))
                .setAutoCancel(true)
            if (yeol != null) b.setContentIntent(PendingIntent.getActivity(c, beon, yeol, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            NotificationManagerCompat.from(c).notify(beon, b.build())
        } catch (e: Exception) {
            girok("ollim_allim_oryu", mapOf("e" to (e.message ?: "").take(80)))
        }
    }

    /** 새 판 알림 — 같은 판은 한 번만 */
    private fun allimHagi(c: Context, s: SaePan) {
        val sp = c.getSharedPreferences("ada_ollim", Context.MODE_PRIVATE)
        if (sp.getString("allinPan", "") == s.pan) return
        sp.edit().putString("allinPan", s.pan).apply()
        val ir = aeIreum(c)
        ttuiugi(c, 4401, "$ir 새 판 ${s.pan}", "$ir 새 판이 나왔습니다. 두드리면 업데이트합니다.", true)
        girok("ollim_sae", mapOf("pan" to s.pan))
    }

    /** 새 판으로 바뀐 뒤(OllimReceiver 의 MY_PACKAGE_REPLACED) */
    internal fun bakkwieotda(c: Context) {
        val ir = aeIreum(c)
        val p = jigeumPan(c)
        NotificationManagerCompat.from(c).cancel(4401)
        ttuiugi(c, 4402, "$ir 업데이트 마침", "$ir 앱이 새 판 ${p}으로 바뀌었습니다. 두드리면 엽니다.", false)
    }
}

/** 설치 결과 — 이 앱 안에서만 받음(밖으로 열지 않음) */
class OllimReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { Ollim.gyeolgwa(c, i) }
}

/** 「새 판으로 바뀜」 — 안드로이드가 이 앱에만 보내는 알림(MY_PACKAGE_REPLACED). 알림을 띄우는 일만 함 */
class OllimBakkwimReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_MY_PACKAGE_REPLACED) Ollim.bakkwieotda(c)
    }
}

/** 뒤 일꾼 — 앱이 닫혀 있어도 안드로이드가 하루 두 번쯤 깨우면 새 판을 살펴 알림 */
class OllimWorker(c: Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result {
        val l = CountDownLatch(1)
        Ollim.salpigi(applicationContext) { l.countDown() }
        l.await(30, TimeUnit.SECONDS)
        return Result.success()
    }
}
