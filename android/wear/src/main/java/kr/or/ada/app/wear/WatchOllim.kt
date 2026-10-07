// 갤럭시 워치 길눈 2.7.0(빌드 261007-W2, 대장클, 이사장님 허락 2026-10-07 22:45) — 워치 스스로 새 판 받기
// 폰 길눈의 업데이트(Ollim.kt)와 같은 판 표(https://lvd.ada.or.kr/sihum/pan.json)의 "wear" 칸을 봄.
//   워치 길눈을 열 때 살펴 새 판이 있으면 첫 화면 맨 위에 「새 판 받기」 한 줄. 누르면 받아서 설치 확인 화면을 엶.
//   처음 한 번은 워치 설정에서 이 앱의 설치 허용이 필요할 수 있음 — 못 열면 그렇다고 알림.
package kr.or.ada.app.wear

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object WatchOllim {
    private const val JULI = "https://lvd.ada.or.kr/sihum/pan.json"
    private const val GYEOLGWA = "kr.or.ada.wear.OLLIM_GYEOLGWA"
    private val main = Handler(Looper.getMainLooper())

    data class SaePan(val pan: String, val apk: String)
    var sae: SaePan? = null
        private set
    var jinhaengJung = false
        private set
    private var majimakSalpim = 0L

    fun jigeumPan(c: Context): String = try {
        @Suppress("DEPRECATION")
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: ""
    } catch (e: Exception) { "" }

    /** 2.6.1 과 2.7.0 처럼 점으로 나뉜 판 번호를 견줌 — a 가 b 보다 새것이면 참 */
    private fun deoSae(a: String, b: String): Boolean {
        val x = a.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val y = b.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrElse(i) { 0 }; val q = y.getOrElse(i) { 0 }
            if (p != q) return p > q
        }
        return false
    }

    /** 열 때마다(1시간에 한 번까지) — 새 판이 생기면 kkeut 을 부름 */
    fun salpigi(c: Context, kkeut: () -> Unit) {
        if (System.currentTimeMillis() - majimakSalpim < 3600000) return
        majimakSalpim = System.currentTimeMillis()
        val ac = c.applicationContext
        Thread {
            var chat: SaePan? = null
            try {
                val u = URL("$JULI?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
                u.connectTimeout = 10000; u.readTimeout = 15000; u.useCaches = false
                if (u.responseCode == 200) {
                    val a = JSONObject(u.inputStream.bufferedReader().readText()).optJSONObject("wear")?.optJSONObject("android")
                    val pan = a?.optString("pan", "") ?: ""
                    val apk = a?.optString("apk", "") ?: ""
                    if (pan.isNotEmpty() && apk.startsWith("https://") && deoSae(pan, jigeumPan(ac))) chat = SaePan(pan, apk)
                }
                u.disconnect()
            } catch (e: Exception) {}
            main.post {
                val ap = sae
                sae = chat
                if (chat != null && ap?.pan != chat.pan) kkeut()
            }
        }.start()
    }

    /** 새 판 받기를 누름 */
    fun olligi(a: Activity, mal: (String) -> Unit) {
        val s = sae ?: return
        if (jinhaengJung) { mal("새 판을 받는 중입니다. 잠시만 기다려 주십시오."); return }
        if (Build.VERSION.SDK_INT >= 26 && !a.packageManager.canRequestPackageInstalls()) {
            mal("처음 한 번만 워치 설정에서 이 앱의 설치 허용이 필요합니다.")
            try {
                a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.packageName)))
            } catch (e: Exception) {
                mal("이 워치에서는 설치 허용 화면을 열 수 없습니다. 협회에 워치 길눈 새 판 설치를 청해 주십시오.")
            }
            return
        }
        jinhaengJung = true
        mal("워치 길눈 새 판 ${s.pan}을 받습니다. 다 받으면 설치 화면이 열립니다.")
        val ac = a.applicationContext
        Thread {
            val f = File(ac.cacheDir, "ollim").apply { mkdirs() }.let { File(it, "wear.apk") }
            var ok = false
            try {
                val u = URL(s.apk).openConnection() as HttpURLConnection
                u.connectTimeout = 15000; u.readTimeout = 30000; u.instanceFollowRedirects = true
                if (u.responseCode == 200) {
                    val modu = u.contentLengthLong
                    var batun = 0L
                    u.inputStream.use { ip -> f.outputStream().use { op ->
                        val b = ByteArray(65536)
                        while (true) { val n = ip.read(b); if (n < 0) break; op.write(b, 0, n); batun += n }
                    } }
                    ok = f.length() > 50_000 && (modu <= 0 || batun == modu)
                }
                u.disconnect()
            } catch (e: Exception) {}
            if (!ok) {
                main.post { jinhaengJung = false; mal("새 판을 받지 못했습니다. 폰과 이어져 있는지 확인하시고 다시 눌러 주십시오.") }
                return@Thread
            }
            try {
                val pi = ac.packageManager.packageInstaller
                val p = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                p.setAppPackageName(ac.packageName)
                val id = pi.createSession(p)
                pi.openSession(id).use { se ->
                    se.openWrite("wear.apk", 0, f.length()).use { op -> f.inputStream().use { it.copyTo(op) }; se.fsync(op) }
                    val i = Intent(ac, WatchOllimReceiver::class.java).setAction(GYEOLGWA)
                    val fl = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    se.commit(PendingIntent.getBroadcast(ac, id, i, fl).intentSender)
                }
            } catch (e: Exception) {
                main.post { jinhaengJung = false; mal("설치를 시작하지 못했습니다.") }
            }
        }.start()
    }

    internal fun gyeolgwa(c: Context, i: Intent) {
        when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val h = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (h != null) try { c.startActivity(h.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
            }
            PackageInstaller.STATUS_SUCCESS -> jinhaengJung = false
            else -> {
                jinhaengJung = false
                WatchModel.speak("워치 길눈 새 판을 설치하지 못했습니다.", "failure")
            }
        }
    }
}

class WatchOllimReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { WatchOllim.gyeolgwa(c, i) }
}
