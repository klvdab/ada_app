// 동영상 강의 — 길눈 사용법 26편을 앱 안에서 틂 (2.69.0, 빌드 261018-A57, 이사장님 승인 2026-10-18 「1」, 아이폰 2.100.0 과 같음)
// ★이사장님: 시각장애인이 새 홈에 가입하고 몇 단계를 찾아와 듣기는 어렵다. 새 홈에 자리를 잡고, 길눈 앱 안에서 필요한 강의가 나오게.
//   ① 강의 차례는 나스(jeom/gangjwa.php a=mokrok)에서 받음 — 새 홈 온라인강좌의 「길눈 사용법 — 안드로이드」와 같은 차례·같은 영상
//      받은 차례는 폰에 담아 두어 인터넷이 잠깐 끊겨도 차례는 보임
//   ② 도움말 맨 위 찾기 칸 아래에 「동영상 강의」, 도움말 한 항목을 펼치면 그 기능의 강의 한 편으로 가는 단추
//   ③ 조회수 — 틀었다·끝까지 보았다·도중에 닫았다를 이름 없이 폰 번호(Seoljeong.dev)로 나스에 남김
//   틀기는 동영상 틀기 재생기(DongyeongActivity)에 강의 번호를 실어 엶 — 워치로 보내지 않고 폰에서
package kr.or.ada.app.gilnun

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

object Gangjwa {
    /** 새 홈 온라인강좌의 강좌 번호 — 길눈은 길눈 안드로이드 강좌, 자봉(같은 부품을 함께 씀)은 자봉 안드로이드 강좌.
     *  자봉 2.27.0 (261018-A23, 이사장님 승인 2026-10-18 「3」) 앱 이름으로 가림 */
    @Volatile var GJ = "gilnun_a"
        private set
    @Volatile private var apPan = Pan.pan
    private var apGarim = false
    private fun apGarigi(c: Context) {
        if (apGarim) return
        apGarim = true
        if (c.packageName.contains("jabong")) GJ = "jabong_a"
        try { apPan = c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: apPan } catch (e: Exception) {}
    }

    class Pyeon(val id: String, val title: String, val url: String, val gin: List<String>)

    @Volatile var pyeon: List<Pyeon> = emptyList()
        private set
    @Volatile var badeum = false
        private set
    private var damaDum = false
    @Volatile private var batneunJung = false

    private fun pref(c: Context) = c.applicationContext.getSharedPreferences("gilnun", Context.MODE_PRIVATE)

    private fun ilgi(a: JSONArray): List<Pyeon> {
        val l = ArrayList<Pyeon>()
        for (i in 0 until a.length()) {
            val v = a.optJSONObject(i) ?: continue
            val id = v.optString("id", ""); val t = v.optString("title", ""); val u = v.optString("url", "")
            if (id.isEmpty() || t.isEmpty() || u.isEmpty()) continue
            val g = v.optJSONArray("gin")
            val gin = ArrayList<String>()
            if (g != null) for (k in 0 until g.length()) { val s = g.optString(k, ""); if (s.isNotEmpty()) gin.add(s) }
            l.add(Pyeon(id, t, u, gin))
        }
        return l
    }

    /** 폰에 담아 둔 차례를 먼저 꺼냄 */
    fun damunGeot(c: Context) {
        apGarigi(c)
        if (damaDum) return
        damaDum = true
        val s = pref(c).getString("gangjwaPyeon_$GJ", null) ?: return
        try { pyeon = ilgi(JSONArray(s)) } catch (e: Exception) {}
    }

    /** 나스에서 차례를 새로 받음 — 다 되면 화면 줄에서 kkeut */
    fun batgi(c: Context, kkeut: () -> Unit) {
        damunGeot(c)
        if (batneunJung) return
        batneunJung = true
        val app = c.applicationContext
        NanumNas.get("gangjwa.php", listOf("a" to "mokrok", "gj" to GJ)) { o ->
            batneunJung = false
            badeum = true
            val a = o?.optJSONArray("videos")
            if (o != null && o.optBoolean("ok", false) && a != null) {
                pyeon = ilgi(a)
                pref(app).edit().putString("gangjwaPyeon_$GJ", a.toString()).apply()
            }
            kkeut()
        }
    }

    /** 도움말 제목에 맞는 강의 한 편 */
    fun matneun(jemok: String): Pyeon? = pyeon.firstOrNull { p -> p.gin.any { it.isNotEmpty() && jemok.contains(it) } }

    fun chatgi(id: String): Pyeon? = pyeon.firstOrNull { it.id == id }

    fun daeum(id: String): Pyeon? {
        val i = pyeon.indexOfFirst { it.id == id }
        return if (i >= 0 && i + 1 < pyeon.size) pyeon[i + 1] else null
    }

    /** 조회수 남기기 — e: si(틀었다) kkeut(끝까지) dat(도중에 닫음) */
    fun seda(id: String, e: String, cho: Long) {
        Girok.namgi("gangjwa_$e", mapOf("v" to id, "cho" to cho))
        NanumNas.get("gangjwa.php", listOf("a" to "seda", "gj" to GJ, "v" to id, "e" to e, "dev" to Seoljeong.dev,
            "src" to "app", "cho" to cho.toString(), "pan" to apPan, "os" to "a")) { }
    }

    /** 한 편 틀기 — 폰의 동영상 틀기 재생기로(워치로 보내지 않음) */
    fun teulgi(c: Context, p: Pyeon) {
        try {
            val i = Intent(c, DongyeongActivity::class.java)
                .putExtra(DongyeongActivity.JUSO, p.url)
                .putExtra(DongyeongActivity.GANGJWA, p.id)
            if (c !is android.app.Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(i)
        } catch (e: Exception) {
            Girok.namgi("gangjwa_oryu", mapOf("e" to (e.message ?: "")))
        }
    }
}

/** 동영상 강의 차례 — 다섯 편씩 */
class GangjwaHwamyeon : Hwamyeon("동영상 강의") {
    private var sijak = 0
    private var cheotJulRo = false
    private var bureum = false

    override fun chaeugi(t: GilnunActivity) {
        Gangjwa.damunGeot(t)
        if (!bureum) {
            bureum = true
            Gangjwa.batgi(t) { if (t.wiHwamyeon === this) t.dasiGeurigi() }
        }
        t.geul("${if (Gangjwa.GJ.startsWith("jabong")) "자봉" else "길눈"} 사용법 동영상 강의입니다. 편 이름을 누르시면 앱 안에서 바로 틀어 드립니다.")
        val l = Gangjwa.pyeon
        if (l.isEmpty()) {
            t.geul(if (Gangjwa.badeum) "강의 차례를 받지 못했습니다. 인터넷이 이어진 뒤 이 화면을 다시 열어 주십시오." else "강의 차례를 받는 중입니다.")
            return
        }
        if (sijak >= l.size) sijak = 0
        val kkeut = minOf(sijak + 5, l.size)
        var cheot: android.view.View? = null
        for (n in sijak until kkeut) {
            val p = l[n]
            val b = t.danchu(p.title) { Gangjwa.teulgi(t, p) }
            if (cheot == null) cheot = b
        }
        if (kkeut < l.size) t.danchu("더 보기") { sijak += 5; cheotJulRo = true; t.dasiGeurigi() }
        if (sijak > 0) t.danchu("이전 보기") { sijak = maxOf(0, sijak - 5); cheotJulRo = true; t.dasiGeurigi() }
        if (cheotJulRo && cheot != null) { cheotJulRo = false; t.chojeomOmgigi(cheot) }
    }
}
