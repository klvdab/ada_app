// 안드로이드 길눈 — 둘러보기 탭의 자료(묶음 b4_dulreo, 아이폰 Dulreo.swift 2.7.0 을 같은 나스 약속으로 옮김)
// 웹 길눈·아이폰 길눈과 같은 나스 자료 창고를 그대로 씁니다.
//   dulle.php a=<종류>·a=chatgi&q=<낱말>   둘레 찾기(가까운 곳부터, 전화번호 함께)
//   matjip.php a=galae · a=chatgi&g=<갈래>  밥집 갈래와 밥집
//   gabolgot.php a=gabol|chukje|mujangae   가는 김에 — 가 볼 곳, 축제, 무장애 여행 정보
//   gojang.php                              고장 이야기(지나는 고장의 먹을 곳·볼 곳·축제)
//   miri.php a=yeojeong                     어디서 어디로 미리 들어 보기
//   sajin.php (POST, multipart)             사진 읽어 주기
//   /masil/masil_*.js                       마실 이야기 — 아이폰은 JavaScriptCore 로 돌림.
//     안드로이드는 androidx.javascriptengine(웹뷰 없는 자바스크립트 칸)으로 같은 파일을 그대로 돌리고,
//     그 칸을 못 쓰는 폰(웹뷰가 오래된 폰)에서만 보이지 않는 웹뷰 하나로 같은 일을 함. 나스 자료는 그대로.
package kr.or.ada.app.gilnun

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.javascriptengine.JavaScriptSandbox
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max

/** 둘레의 한 곳(아이폰 Got) */
data class DrGot(
    val ireum: String,
    val juso: String,
    val jeonhwa: String,
    val galae: String,
    val lat: Double,
    val lon: Double,
    val meter: Double?,
    val cheo: String,
    val kkeut: String
) {
    val id: String get() = "$ireum|$lat|$lon"
    val jangso: Jangso get() = Jangso(ireum, juso, lat, lon)

    /** 지금 자리에서 몇 미터 */
    val geori: Double?
        get() {
            if (meter != null) return meter
            val w = Wichi.jigeum ?: return null
            if (lat == 0.0) return null
            return Wichi.geori(w.lat, w.lon, lat, lon)
        }

    /** 목록 한 줄 — 이름, 거리, 갈래(마지막 한 마디), 축제는 여는 날 */
    val julMal: String
        get() {
            var m = ireum
            geori?.let { m += " — " + Annae.geoMal(it) }
            val g = galae.split(">").last().trim()
            if (g.isNotEmpty()) m += ", $g"
            if (cheo.isNotEmpty()) m += ", " + nalMal(cheo, kkeut)
            return m
        }

    companion object {
        /** 20261022, 20261025 → "10월 22일부터 25일까지" */
        fun nalMal(a: String, b: String): String {
            fun md(s: String): Pair<Int, Int>? {
                if (s.length < 8) return null
                val m = s.substring(4, 6).toIntOrNull() ?: return null
                val d = s.substring(6, 8).toIntOrNull() ?: return null
                return m to d
            }
            val x = md(a) ?: return ""
            val y = md(b)
            if (y == null || (x.first == y.first && x.second == y.second)) return "${x.first}월 ${x.second}일"
            return if (x.first == y.first) "${x.first}월 ${x.second}일부터 ${y.second}일까지"
                   else "${x.first}월 ${x.second}일부터 ${y.first}월 ${y.second}일까지"
        }

        fun batgi(r: JSONObject): DrGot? {
            val la = Jeomjido.su(r, "lat") ?: return null
            val lo = Jeomjido.su(r, "lon") ?: return null
            return DrGot(
                Jeomjido.gul(r, "ireum"), Jeomjido.gul(r, "juso"), Jeomjido.gul(r, "jeonhwa"), Jeomjido.gul(r, "galae"),
                la, lo, Jeomjido.su(r, "meter") ?: Jeomjido.su(r, "m"),
                Jeomjido.gul(r, "cheo"), Jeomjido.gul(r, "kkeut")
            )
        }
    }
}

/** 둘레 찾기 종류 — 웹 길눈 dulle.js 와 같은 차례(아이폰 DulleJong) */
data class DrDulleJong(
    val id: String,       // dulle.php a= 값(없으면 낱말로 찾음)
    val ireum: String,
    val natmal: String,   // a=chatgi&q= 로 찾을 낱말
    val geot: Boolean     // 겉에 두는가
) {
    /** 화면 제목·말에 쓰는 짧은 이름("식당 — 밥집 갈래를 고르십니다" → "식당") */
    val jjalbeun: String get() = ireum.split(" — ").first()

    companion object {
        val modu: List<DrDulleJong> = listOf(
            DrDulleJong("bapjip", "식당 — 밥집 갈래를 고르십니다", "", true),
            DrDulleJong("cafe", "카페", "", true),
            DrDulleJong("byeongwon", "병원", "", true),
            DrDulleJong("yakguk", "약국", "", true),
            DrDulleJong("pyeonui", "편의점", "", true),
            DrDulleJong("", "화장실", "공중화장실", true),
            DrDulleJong("jeongryu", "버스정류장 — 가까운 정류장과 정류장 번호", "", true),
            DrDulleJong("eungeup", "응급실 — 가까운 응급의료기관", "", true),
            DrDulleJong("daepiso", "대피소 — 지진·민방위·무더위·한파", "", true),
            DrDulleJong("eunhaeng", "은행", "", false),
            DrDulleJong("mart", "대형마트", "", false),
            DrDulleJong("juchajang", "주차장", "", false),
            DrDulleJong("juyuso", "주유소", "", false),
            DrDulleJong("gwangwang", "관광명소", "", false),
            DrDulleJong("munhwa", "문화시설", "", false),
            DrDulleJong("gonggong", "공공기관", "", false),
            DrDulleJong("sukbak", "숙박", "", false)
        )

        val daepi: List<Pair<String, String>> = listOf(
            "지진 대피소" to "지진옥외대피소", "민방위 대피소" to "민방위대피소",
            "무더위 쉼터" to "무더위쉼터", "한파 쉼터" to "한파쉼터"
        )

        /** 말로 하기에서 — "근처 약국" 같은 말의 종류 찾기(z 는 띄어쓰기를 뺀 말) */
        fun malEseo(z: String): DrDulleJong? {
            val pyo = listOf(
                "약국" to "yakguk", "병원" to "byeongwon", "편의점" to "pyeonui", "카페" to "cafe",
                "커피" to "cafe", "화장실" to "", "응급실" to "eungeup", "은행" to "eunhaeng",
                "마트" to "mart", "주차장" to "juchajang", "주유소" to "juyuso", "숙박" to "sukbak",
                "모텔" to "sukbak", "호텔" to "sukbak", "식당" to "sikdang", "밥집" to "sikdang",
                "정류장" to "jeongryu"
            )
            for ((m, id) in pyo) {
                if (!z.contains(m)) continue
                if (id.isEmpty()) return modu.firstOrNull { it.natmal == "공중화장실" }
                if (id == "sikdang") return DrDulleJong("sikdang", "식당", "", true)
                return modu.firstOrNull { it.id == id }
            }
            return null
        }
    }
}

/** 밥집 갈래 */
data class DrBapjipGalae(val id: String, val ireum: String)

/** 둘러보기 설정(아이폰 Seoljeong.gojangJadong — 같은 이름, 처음엔 켜짐) */
object DulreoSeol {
    private fun d(): android.content.SharedPreferences? =
        (Kamera.hwalseongEotgi() ?: MalHagi.hwalseong?.get())?.applicationContext?.getSharedPreferences("gilnun", Context.MODE_PRIVATE)
    private var pp: android.content.SharedPreferences? = null

    fun sijak(c: Context) {
        if (pp == null) pp = c.applicationContext.getSharedPreferences("gilnun", Context.MODE_PRIVATE)
    }

    /** 차 안에서 고장이 바뀌면 들려 주기 */
    var gojangJadong: Boolean
        get() = (pp ?: d())?.getBoolean("gojangJadong", true) ?: true
        set(v) { (pp ?: d())?.edit()?.putBoolean("gojangJadong", v)?.apply() }
}

/** 나스·바깥 통신 중 Tongsin.json 으로 안 되는 것(글 그대로 받기, 사진 보내기) — 결과는 화면 줄 */
object DrNet {
    private val il = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    fun dwiSil(f: () -> Unit) { il.execute(f) }

    /** 글 그대로 받기(바탕 줄에서 부름 — 결과를 곧바로 돌려줌). 못 받으면 null */
    fun geulBaro(url: String, ua: String? = null, handO: Int = 10000): String? {
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = handO
            c.readTimeout = handO
            if (ua != null) c.setRequestProperty("User-Agent", ua)
            val t = if (c.responseCode == 200) c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } else null
            c.disconnect()
            t
        } catch (e: Exception) {
            null
        }
    }

    /** 글 받기 — 결과는 화면 줄 */
    fun geul(url: String, ua: String? = null, handO: Int = 10000, kkeut: (String?) -> Unit) {
        il.execute {
            val t = geulBaro(url, ua, handO)
            main.post { kkeut(t) }
        }
    }

    /** 사진 보내기(multipart) — 결과 JSON 은 화면 줄. 못 받으면 null */
    fun sajinBonaegi(url: String, mode: String, jpeg: ByteArray, kkeut: (JSONObject?) -> Unit) {
        il.execute {
            var o: JSONObject? = null
            try {
                val gyeong = "gilnun-" + UUID.randomUUID().toString()
                val b = ByteArrayOutputStream()
                fun s(t: String) { b.write(t.toByteArray(Charsets.UTF_8)) }
                s("--$gyeong\r\nContent-Disposition: form-data; name=\"mode\"\r\n\r\n$mode\r\n")
                s("--$gyeong\r\nContent-Disposition: form-data; name=\"file\"; filename=\"sajin.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n")
                b.write(jpeg)
                s("\r\n--$gyeong--\r\n")
                val bonmun = b.toByteArray()
                val c = URL(url).openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.doOutput = true
                c.connectTimeout = 15000
                c.readTimeout = 90000
                c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$gyeong")
                c.setFixedLengthStreamingMode(bonmun.size)
                c.outputStream.use { it.write(bonmun) }
                if (c.responseCode == 200) {
                    val t = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    o = JSONObject(t)
                }
                c.disconnect()
            } catch (e: Exception) {
                o = null
            }
            val r = o
            main.post { kkeut(r) }
        }
    }
}

/** 둘러보기 자료 받기 — 결과는 모두 화면 줄(아이폰 enum Dulreo) */
object Dulreo {
    private fun jari(): HashMap<String, String>? {
        val w = Wichi.jigeum ?: return null
        return hashMapOf("lat" to Chatgi.f6(w.lat), "lon" to Chatgi.f6(w.lon))
    }

    /** 목록 — 못 받으면 null, ok 가 아니면 빈 목록 */
    private fun rows(pail: String, q0: Map<String, String>, kkeut: (List<DrGot>?) -> Unit) {
        val q = jari()
        if (q == null) { kkeut(null); return }
        q.putAll(q0)
        Tongsin.json(pail, q, 10000) { o ->
            if (o == null) { kkeut(null); return@json }
            if (!o.optBoolean("ok", false)) { kkeut(emptyList()); return@json }
            val out = ArrayList<DrGot>()
            val r = o.optJSONArray("rows")
            if (r != null) for (i in 0 until r.length()) {
                val x = r.optJSONObject(i) ?: continue
                DrGot.batgi(x)?.let { out.add(it) }
            }
            kkeut(out)
        }
    }

    /** 둘레 찾기 — 종류로, 또는 낱말로(1킬로미터 안) */
    fun dulle(j: DrDulleJong, kkeut: (List<DrGot>?) -> Unit) {
        if (j.natmal.isNotEmpty()) rows("dulle.php", mapOf("a" to "chatgi", "q" to j.natmal, "m" to "1000"), kkeut)
        else rows("dulle.php", mapOf("a" to j.id, "m" to "1000"), kkeut)
    }

    fun daepiso(natmal: String, kkeut: (List<DrGot>?) -> Unit) =
        rows("dulle.php", mapOf("a" to "chatgi", "q" to natmal, "m" to "3000"), kkeut)

    fun bapjipGalae(kkeut: (List<DrBapjipGalae>?) -> Unit) {
        Tongsin.json("matjip.php", mapOf("a" to "galae")) { o ->
            if (o == null || !o.optBoolean("ok", false)) { kkeut(null); return@json }
            val out = ArrayList<DrBapjipGalae>()
            val r = o.optJSONArray("rows")
            if (r != null) for (i in 0 until r.length()) {
                val x = r.optJSONObject(i) ?: continue
                val id = x.opt("id") as? String ?: continue
                val nm = x.opt("ireum") as? String ?: continue
                out.add(DrBapjipGalae(id, nm))
            }
            kkeut(out)
        }
    }

    fun bapjip(g: String, kkeut: (List<DrGot>?) -> Unit) =
        rows("matjip.php", mapOf("a" to "chatgi", "g" to g, "m" to (if (g == "sogae") "5000" else "1000")), kkeut)

    /** 가는 김에 — gabol(가 볼 곳), chukje(축제), mujangae(무장애 여행 정보), 10킬로미터 안 */
    fun gabol(a: String, kkeut: (List<DrGot>?) -> Unit) = rows("gabolgot.php", mapOf("a" to a, "m" to "10000"), kkeut)

    /** 고장 이야기 — (말, 고장 열쇠). 못 받으면 null */
    fun gojang(kkeut: (Pair<String, String>?) -> Unit) {
        val q = jari()
        if (q == null) { kkeut(null); return }
        Tongsin.json("gojang.php", q, 10000) { o ->
            if (o == null) { kkeut(null); return@json }
            if (!o.optBoolean("ok", false)) {
                val e = Jeomjido.gul(o, "error")
                kkeut((if (e.isEmpty()) "이 고장 이야기를 알아내지 못했습니다." else e) to "")
                return@json
            }
            kkeut(Jeomjido.gul(o, "mal") to Jeomjido.gul(o, "key"))
        }
    }

    private fun f6(x: Double) = String.format(Locale.US, "%.6f", x)

    private fun julDeul(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        val l = ArrayList<String>()
        for (i in 0 until a.length()) {
            val s = a.opt(i) as? String ?: continue
            if (s.isNotEmpty()) l.add(s)
        }
        return l
    }

    /** 어디서 어디로 미리 들어 보기 — 출발지와 목적지 사이를 한 덩이 말로. 못 받으면 null */
    fun miri(chul: Jangso, chulNae: Boolean, mok: Jangso, kkeut: (String?) -> Unit) {
        val q = hashMapOf(
            "a" to "yeojeong", "slat" to f6(chul.lat), "slon" to f6(chul.lon),
            "mlat" to f6(mok.lat), "mlon" to f6(mok.lon), "ireum" to mok.ireum,
            "bopok" to String.format(Locale.US, "%.2f", Seoljeong.bopok)
        )
        Tongsin.json("miri.php", q, 15000) { o ->
            if (o == null || !o.optBoolean("ok", false)) { kkeut(null); return@json }
            fun ieo(apMal: String?) {
                val t = ArrayList<String>()
                if (chulNae) {
                    if (apMal != null) t.add("지금 ${apMal}에 계십니다.")
                } else {
                    t.add("${chul.ireum}에서 떠나십니다.")
                }
                t.add("가시려는 곳은 ${mok.ireum}입니다.")
                val c = o.optJSONObject("cha")
                val cm = c?.let { Jeomjido.su(it, "m") }
                if (c != null && cm != null) {
                    t.add("차로 가시면 ${Annae.geoMal(cm)}를 달려 ${Jeomjido.gul(c, "sigan")} 걸립니다.")
                    if (chulNae) Jeomjido.geulOrNull(c, "ttae")?.let { t.add("지금 떠나시면 ${it}쯤 닿습니다.") }
                    val dr = julDeul(c.optJSONArray("doro"))
                    if (dr.isNotEmpty()) t.add("지나는 길은 " + dr.joinToString(", ") + "입니다.")
                    val jn = julDeul(c.optJSONArray("jinam"))
                    if (jn.isNotEmpty()) t.add("거치는 자리는 " + jn.joinToString(", ") + "입니다.")
                } else {
                    Jeomjido.su(o, "jik")?.let { t.add("차로 가는 길은 지금 받지 못했습니다. 곧장 재면 약 ${Annae.geoMal(it)}입니다.") }
                }
                val g = o.optJSONObject("geot")
                val gm = g?.let { Jeomjido.su(it, "m") }
                if (g != null && gm != null && gm <= 3000) {
                    var s = "걸어가시면 약 ${Annae.geoMal(gm)}, ${Jeomjido.gul(g, "sigan")} 걸립니다."
                    val georeum = (gm / max(0.3, Seoljeong.bopok)).toInt()
                    s += " 걸음으로는 ${georeum}걸음쯤입니다."
                    t.add(s)
                }
                kkeut(t.joinToString(" "))
            }
            if (chulNae) Chatgi.juso(chul.lat, chul.lon) { s -> ieo(s) } else ieo(null)
        }
    }

    /** 사진 읽어 주기 — mode: boki(보기), jasehi(자세히). (되었나, 말) */
    fun sajin(jpeg: ByteArray, mode: String, kkeut: (Boolean, String) -> Unit) {
        DrNet.sajinBonaegi("https://lvd.ada.or.kr/jeom/sajin.php", mode, jpeg) { o ->
            if (o == null) { kkeut(false, "사진을 보내지 못했습니다. 통신을 확인하시고 다시 해 보십시오."); return@sajinBonaegi }
            val m = Jeomjido.gul(o, "mal")
            kkeut(o.optBoolean("ok", false), if (m.isEmpty()) "읽지 못했습니다." else m)
        }
    }
}

// MARK: 고장 이야기 — 차 안에서 고장이 바뀌면 한 번(설정에서 끔, 아이폰 GojangEngine)

object GojangEngine {
    private val main = Handler(Looper.getMainLooper())
    private var majimakKey = ""
    private var majimakTtae = 0L
    private var mutneunJung = false
    private var sijakham = false

    private val sigye = object : Runnable {
        override fun run() {
            boda()
            main.postDelayed(this, 60000)
        }
    }

    fun sijak(c: Context) {
        DulreoSeol.sijak(c)
        if (sijakham) return
        sijakham = true
        main.removeCallbacks(sigye)
        main.postDelayed(sigye, 60000)
    }

    private fun boda() {
        if (!DulreoSeol.gojangJadong || mutneunJung) return
        val y = YeojeongEngine.jigeum ?: return
        if (y.danggye != Danggye.TANEUN_JUNG) return
        if (YeojeongEngine.talgeot == Talgeot.JIHACHEOL) return
        mutneunJung = true
        Dulreo.gojang { r ->
            mutneunJung = false
            if (r == null) return@gojang
            val mal = r.first
            val key = r.second
            if (key.isEmpty() || mal.isEmpty()) return@gojang
            // 같은 고장은 한 번만, 다만 열다섯 분이 지나면 한 번 더(웹 길눈과 같게)
            val now = System.currentTimeMillis()
            if (key == majimakKey && now - majimakTtae < 900000) return@gojang
            majimakKey = key
            majimakTtae = now
            Sori.mal(mal, MalGeup.JEONGBO)
            Girok.namgi("gojang_jadong")
        }
    }
}

// MARK: 마실 — 나스의 마실 이야기(masil_*.js)를 앱 안의 자바스크립트 칸으로 읽음(웹과 같은 자료, 나스는 그대로)

class MasilMadi(
    val t: String,
    val mal: String,
    val gyeokda: String,
    val mut: String,
    val bogi: List<String>,
    val dap: Int,
    val matda: String,
    val teulida: String
) {
    val quiz: Boolean get() = mut.isNotEmpty()
}

class MasilGojang(val id: String, val ireum: String, val jjalb: String, val han: String, val madi: List<MasilMadi>)

object Masil {
    private val main = Handler(Looper.getMainLooper())
    var gojang: List<MasilGojang> = emptyList()
        private set
    var batneunJung = false
        private set
    private var gyeokda: Map<String, List<String>> = emptyMap()
    private val gidari = ArrayList<(Boolean) -> Unit>()

    /** 마실 이야기 받기 — 결과(되었나)는 화면 줄 */
    fun bureogi(c: Context, kkeut: (Boolean) -> Unit) {
        if (gojang.isNotEmpty()) { kkeut(true); return }
        gidari.add(kkeut)
        if (batneunJung) return
        batneunJung = true
        val ac = c.applicationContext
        DrNet.dwiSil {
            val html = DrNet.geulBaro("https://lvd.ada.or.kr/masil/masil.html")
            if (html == null) { main.post { kkeutNaegi(null, "html") }; return@dwiSil }
            val pail = ArrayList<String>()
            for (m in Regex("masil_[a-z_]+\\.js").findAll(html)) if (!pail.contains(m.value)) pail.add(m.value)
            val js = ArrayList<String>()
            for (p in pail) DrNet.geulBaro("https://lvd.ada.or.kr/masil/$p")?.let { js.add(it) }
            val s = sandbox(ac, js)
            if (s != null) { main.post { kkeutNaegi(s, "sandbox") }; return@dwiSil }
            // 자바스크립트 칸을 못 쓰는 폰 — 보이지 않는 웹뷰 하나로(화면 줄에서)
            main.post { webview(ac, js) { r -> kkeutNaegi(r, "webview") } }
        }
    }

    /** androidx.javascriptengine — 웹뷰 없이 자바스크립트만 돌리는 칸(아이폰 JSContext 와 같은 일). 바탕 줄에서 */
    private fun sandbox(c: Context, js: List<String>): String? {
        return try {
            if (android.os.Build.VERSION.SDK_INT < 26) return null   // 2.7.0 통합 — 자바스크립트 칸은 안드로이드 8 이상에서만(매니페스트 overrideLibrary 와 짝), 아래면 웹뷰로
            if (!JavaScriptSandbox.isSupported()) return null
            val sb = JavaScriptSandbox.createConnectedInstanceAsync(c).get(15, TimeUnit.SECONDS)
            try {
                val iso = sb.createIsolate()
                try {
                    iso.evaluateJavaScriptAsync("var window = this; 0").get(10, TimeUnit.SECONDS)
                    for (s in js) {
                        try { iso.evaluateJavaScriptAsync("$s\n;0").get(15, TimeUnit.SECONDS) } catch (e: Exception) {}
                    }
                    iso.evaluateJavaScriptAsync("JSON.stringify(window.MASIL_DATA || {gojang:[], gyeokda:{}})").get(15, TimeUnit.SECONDS)
                } finally {
                    iso.close()
                }
            } finally {
                sb.close()
            }
        } catch (e: Exception) {
            Girok.namgi("masil_sandbox_oryu", mapOf("e" to (e.message ?: "").take(80)))
            null
        }
    }

    /** 보이지 않는 웹뷰로 같은 파일을 차례로 돌림(화면 줄에서) — 결과 JSON 글, 못 하면 null */
    @SuppressLint("SetJavaScriptEnabled")
    private fun webview(c: Context, js: List<String>, kkeut: (String?) -> Unit) {
        val w = try { WebView(c) } catch (e: Exception) { null }
        if (w == null) { kkeut(null); return }
        var kkeunna = false
        fun majim(r: String?) {
            if (kkeunna) return
            kkeunna = true
            main.post { try { w.destroy() } catch (e: Exception) {} }
            kkeut(r)
        }
        w.settings.javaScriptEnabled = true
        w.setWebViewClient(object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (kkeunna) return
                fun ieo(i: Int) {
                    if (i < js.size) {
                        view.evaluateJavascript(js[i] + "\n;0") { ieo(i + 1) }
                        return
                    }
                    view.evaluateJavascript("JSON.stringify(window.MASIL_DATA || {gojang:[], gyeokda:{}})") { r ->
                        // r 은 JSON 글 하나를 다시 JSON 으로 감싼 것("{\"gojang\":…}") — 한 겹 벗김
                        val s = try { if (r == null || r == "null") null else JSONArray("[$r]").getString(0) } catch (e: Exception) { null }
                        majim(s)
                    }
                }
                ieo(0)
            }
        })
        main.postDelayed({ majim(null) }, 30000)
        w.loadDataWithBaseURL("https://lvd.ada.or.kr/masil/", "<!doctype html><html><head><meta charset=\"utf-8\"></head><body></body></html>", "text/html", "utf-8", null)
    }

    private fun kkeutNaegi(s: String?, gil: String) {
        var ok = false
        if (s != null) {
            try {
                val o = JSONObject(s)
                val gl = ArrayList<MasilGojang>()
                val ga = o.optJSONArray("gojang")
                if (ga != null) for (i in 0 until ga.length()) {
                    val g = ga.optJSONObject(i) ?: continue
                    val md = ArrayList<MasilMadi>()
                    val ma = g.optJSONArray("madi")
                    if (ma != null) for (k in 0 until ma.length()) {
                        val m = ma.optJSONObject(k) ?: continue
                        val q = m.optJSONObject("quiz") ?: JSONObject()
                        val bogi = ArrayList<String>()
                        val ba = q.optJSONArray("bogi")
                        if (ba != null) for (b in 0 until ba.length()) bogi.add(ba.optString(b, ""))
                        md.add(MasilMadi(
                            Jeomjido.gul(m, "t"), Jeomjido.gul(m, "mal"), Jeomjido.gul(m, "gyeokda"),
                            Jeomjido.gul(q, "mut"), bogi, (Jeomjido.su(q, "dap") ?: -1.0).toInt(),
                            Jeomjido.gul(q, "matda"), Jeomjido.gul(q, "teulida")
                        ))
                    }
                    val id = Jeomjido.gul(g, "id")
                    gl.add(MasilGojang(if (id.isEmpty()) UUID.randomUUID().toString() else id,
                        Jeomjido.gul(g, "ireum"), Jeomjido.gul(g, "jjalb"), Jeomjido.gul(g, "han"), md))
                }
                val gy = HashMap<String, List<String>>()
                val go = o.optJSONObject("gyeokda")
                if (go != null) {
                    val ks = go.keys()
                    while (ks.hasNext()) {
                        val k = ks.next()
                        val a = go.optJSONArray(k) ?: continue
                        val l = ArrayList<String>()
                        for (i in 0 until a.length()) (a.opt(i) as? String)?.let { l.add(it) }
                        gy[k] = l
                    }
                }
                gojang = gl
                gyeokda = gy
                ok = gl.isNotEmpty()
                Girok.namgi("masil_bureogi", mapOf("su" to gl.size, "gil" to gil))
            } catch (e: Exception) {
                ok = false
            }
        }
        batneunJung = false
        val l = ArrayList(gidari)
        gidari.clear()
        for (f in l) f(ok)
    }

    /** 겪는 일 한 조각 — 갈래(kind)에서 아무것이나 */
    fun hanjogak(kind: String): String {
        if (kind.isEmpty()) return ""
        val l = gyeokda[kind] ?: return ""
        return if (l.isEmpty()) "" else l.random()
    }
}
