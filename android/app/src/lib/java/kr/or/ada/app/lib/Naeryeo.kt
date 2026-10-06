package kr.or.ada.app.lib

// AI점자도서관 안드로이드 — 내려받기(인터넷 없이 읽기) (판 0.3.0, 빌드 261006-L1)
// 이사장님 지시(2026-10-06): 인터넷이 안 되거나 데이터가 모자라도 독서가 끊기지 않게.
// 글자책은 글 전체만 받아 두고, 인터넷이 없으면 폰 목소리(TextToSpeech)로 읽는다. 소리책은 소리 파일을 통째로 받는다.
// 받은 것은 앱 전용 저장소(filesDir/books)에 둔다 — 다른 앱과 파일 탐색기에서 보이지 않고, 폰 암호화로 잠긴다.

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object Naeryeo {
    data class Item(val id: Int, val t: String, val kind: String, val size: Long, val nal: String)
    @Volatile var busy = -1
    @Volatile private var meomchum = false
    private val pool = Executors.newSingleThreadExecutor()

    private fun pref(c: Context) = c.getSharedPreferences("naeryeo", Context.MODE_PRIVATE)
    fun wifiOnly(c: Context) = pref(c).getBoolean("wifiOnly", true)
    fun setWifiOnly(c: Context, v: Boolean) = pref(c).edit().putBoolean("wifiOnly", v).apply()
    fun jul(c: Context): Int { val n = pref(c).getInt("julsu", 15); return if (n in listOf(5, 10, 15, 20, 30)) n else 15 }
    fun nextJul(c: Context) { val l = listOf(5, 10, 15, 20, 30); val n = l[(l.indexOf(jul(c)) + 1) % l.size]; pref(c).edit().putInt("julsu", n).apply(); Api.PER = n }
    fun hyeongtae(c: Context) = pref(c).getString("hyeongtae", "all") ?: "all"
    fun hyeongtaeMal(c: Context) = when (hyeongtae(c)) { "sori" -> "소리로 듣는 책만"; "jeom" -> "점자책만"; else -> "모든 책" }
    fun nextHyeongtae(c: Context) { val n = when (hyeongtae(c)) { "all" -> "sori"; "sori" -> "jeom"; else -> "all" }; pref(c).edit().putString("hyeongtae", n).apply() }

    fun online(c: Context): Boolean {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork ?: return false
        val cap = cm.getNetworkCapabilities(n) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    private fun cheap(c: Context): Boolean {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork ?: return false
        val cap = cm.getNetworkCapabilities(n) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun root(c: Context) = File(c.filesDir, "books").apply { mkdirs() }
    fun dir(c: Context, i: Int) = File(root(c), "$i")
    fun paras(c: Context, i: Int): List<String>? = runCatching {
        val f = File(dir(c, i), "paras.json")
        if (!f.exists()) null else { val a = JSONArray(f.readText(Charsets.UTF_8)); List(a.length()) { a.optString(it) } }
    }.getOrNull()?.takeIf { it.isNotEmpty() }
    fun localMedia(c: Context, i: Int): File? = dir(c, i).listFiles()?.firstOrNull { it.name.startsWith("sori.") && it.length() > 1000 }
    fun isDown(c: Context, i: Int) = File(dir(c, i), "meta.json").exists()
    fun items(c: Context): List<Item> = (root(c).listFiles() ?: emptyArray()).mapNotNull { d ->
        runCatching { val j = JSONObject(File(d, "meta.json").readText(Charsets.UTF_8)); Item(j.getInt("id"), j.getString("t"), j.getString("kind"), d.walkTopDown().filter { it.isFile }.sumOf { it.length() }, j.optString("nal")) }.getOrNull()
    }.sortedByDescending { it.nal }
    fun meg(b: Long) = String.format(Locale.KOREA, "%.1f메가", b / 1048576.0)

    fun download(c: Context, i: Int, title: String, kind: String, mal: (String) -> Unit) {
        if (busy >= 0) { mal("다른 책을 내려받는 중입니다."); return }
        if (!online(c)) { mal("인터넷이 연결되지 않아 내려받을 수 없습니다."); return }
        if (wifiOnly(c) && !cheap(c)) { mal("와이파이에서만 내려받기로 정해 두셨습니다. 와이파이에 연결한 뒤 다시 눌러 주십시오. 설정에서 바꿀 수 있습니다."); return }
        busy = i; meomchum = false
        mal("내려받기를 시작합니다.")
        val ac = c.applicationContext
        pool.execute {
            val d = dir(ac, i); d.mkdirs()
            val ok = runCatching {
                if (kind == "geul") {
                    val j = Api.getJson("dokseo.php", mapOf("m" to "gul", "i" to "$i", "all" to "1"))
                    val a = j.optJSONArray("mun")
                    if (j.optBoolean("ok") && a != null && a.length() > 0) { File(d, "paras.json").writeText(a.toString(), Charsets.UTF_8); true } else false
                } else Api.naeryeo(Api.mediaUrl(i), File(d, "sori.mp3")) { meomchum }
            }.getOrDefault(false)
            if (ok && !meomchum) {
                File(d, "meta.json").writeText(JSONObject().put("id", i).put("t", title).put("kind", kind).put("nal", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).format(Date())).toString(), Charsets.UTF_8)
                busy = -1; mal("내려받기를 마쳤습니다. 이제 인터넷이 없어도 들을 수 있습니다.")
            } else {
                d.deleteRecursively(); busy = -1
                if (!meomchum) mal("내려받지 못했습니다. 인터넷을 확인하고 다시 눌러 주십시오.")
            }
        }
    }
    fun cancel() { meomchum = true; busy = -1 }
    fun remove(c: Context, i: Int) { dir(c, i).deleteRecursively() }
    fun removeAll(c: Context) { root(c).deleteRecursively() }
}
