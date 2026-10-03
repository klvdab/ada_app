// 안드로이드 길눈 — 여정 엔진(묶음 b1, 아이폰 YeojeongEngine.swift 와 같은 잣대)
// 목적지, 지금 차례, 탈것을 한 곳에서 다룹니다.
// ① 화면이 바뀌어도, 음악을 틀어도, 앱을 껐다 켜도 여정은 그대로(폰 안 filesDir/yeojeong.json 에 담음)
// ② 빠르기로 탈것을 알아채되, 이용자가 바로잡은 탈것이 가장 앞섬
// ③ 열두 시간 넘게 손대지 않은 여정은 저절로 끝냄
// 차에 타면 지나는 고장 노래(Bangsong.chaTamGojangNorae — 음악·방송 묶음)를 chaTamHook 으로 부름
package kr.or.ada.app.gilnun

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class Talgeot(val ireum: String) {
    GEOREUM("걸어서"), CHA("차"), JIHACHEOL("지하철"), BEOSEU("버스"), GICHA("기차"), GOSOKBEOSEU("고속버스");

    /** 아이폰 rawValue 와 같은 이름(기록·파일에 씀) */
    val raw: String get() = when (this) {
        GEOREUM -> "georeum"; CHA -> "cha"; JIHACHEOL -> "jihacheol"; BEOSEU -> "beoseu"; GICHA -> "gicha"; GOSOKBEOSEU -> "gosokbeoseu"
    }

    companion object {
        fun batgi(s: String): Talgeot = values().firstOrNull { it.raw == s } ?: GEOREUM
    }
}

enum class Danggye(val ireum: String) {
    EOTTEOKE("어떻게 갈지 정할 차례"),
    TANEUN_GOT_KKAJI("타는 곳까지 가는 차례"),
    TANEUN_JUNG("타고 가는 중"),
    NAM_EUN_GIL("남은 길을 걷는 차례"),
    DOCHAK("도착");

    val raw: String get() = when (this) {
        EOTTEOKE -> "eotteoke"; TANEUN_GOT_KKAJI -> "taneunGotKkaji"; TANEUN_JUNG -> "taneunJung"; NAM_EUN_GIL -> "namEunGil"; DOCHAK -> "dochak"
    }

    companion object {
        fun batgi(s: String): Danggye = values().firstOrNull { it.raw == s } ?: EOTTEOKE
    }
}

data class Mokjeok(val ireum: String, val lat: Double, val lon: Double, val juso: String) {
    val jangso: Jangso get() = Jangso(ireum, juso, lat, lon)
}

/** 지하철 한 구간 — 호선, 방면, 지나는 역 */
data class JihaGugan(val hoseon: String, val bangmyeon: String, val jina: List<String>)

/** 지하철로 가는 길 */
data class JihaGil(
    val from: String,
    val to: String,
    val mal: String,
    val bun: Int,
    val jina: List<String>,
    val gugan: List<JihaGugan>,
    val ipgu: Jangso,        // 타는 역에서 들어갈 출구(걸어갈 곳)
    val naeril: String,      // 내린 역에서 목적지에 가장 가까운 출구
    val ipguDochak: Boolean,
    val i: Int,
    val kkeutnam: Boolean
)

/** 버스 정류장 */
data class Jeongryujang(val no: String, val ireum: String, val lat: Double, val lon: Double) {
    val id: String get() = no + ireum
}

/** 버스로 가는 여정이면 그 정류장 */
data class BeoseuGil(val jeongryujang: Jeongryujang, val dochak: Boolean)

data class Yeojeong(
    val mokjeok: Mokjeok,
    val danggye: Danggye,
    val talgeot: Talgeot,
    val barojabeum: Boolean,
    val sijak: Long,
    val gaengsin: Long,
    val jiha: JihaGil? = null,
    val beoseu: BeoseuGil? = null
)

object YeojeongEngine {
    private val main = Handler(Looper.getMainLooper())
    private var pail: File? = null
    private var sijakham = false

    var jigeum: Yeojeong? = null
        private set
    /** 빠르기로 알아챈 탈것(이용자가 바로잡지 않았을 때 씀) */
    var sokdoChujeong: Talgeot = Talgeot.GEOREUM
        private set

    /** 여정이 바뀌면 한 번(화면이 채움 — 길 찾기 첫 화면) */
    var byeonhwa: (() -> Unit)? = null
    /** 차·버스·기차에 타면 참, 내리면 거짓(지나는 고장 노래 — sijak 이 Bangsong 으로 채움) */
    var chaTamHook: ((Boolean) -> Unit)? = null

    private var ppareunTtae = 0L
    private var neurinTtae = 0L
    private var allimDaegi = false

    /** 길눈이 켜질 때 한 번 — 즐겨찾기·안내·지하철 엔진을 함께 세우고 지난 여정을 되살림 */
    fun sijak(c: Context) {
        if (sijakham) return
        sijakham = true
        val dir = File(c.applicationContext.filesDir, "gilnun")
        dir.mkdirs()
        pail = File(dir, "yeojeong.json")
        Jeulgyeo.sijak(c)
        AnnaeEngine.sijak(c)
        JihacheolEngine.sijak(c)
        Wichi.deutgi { w -> sokdoBoda(w) }
        jeomJapgi()
        // 차·버스·기차에 타면 지나는 고장 노래, 내리면 멈춤(지하철은 땅속이라 빼냄 — 아이폰 2.12.7)
        if (chaTamHook == null) chaTamHook = { tam -> if (tam) Bangsong.chaTamGojangNorae() else Bangsong.chaNaerimGojangNorae() }
        bureogi()
        JihacheolEngine.ieoGagi()
    }

    /** 점지도 엔진(묶음 b2)의 잇는 자리를 채움 — 아이폰 JeomEngine 이 YeojeongEngine·AnnaeEngine 을 부르던 곳과 같음 */
    private fun jeomJapgi() {
        val jm = JeomEngine
        // 점지도를 걷기 시작하면(또는 걸을지 여쭐 때) 그 길의 끝(걸어가실 곳)을 목적지로, 걸어서 가는 중으로
        jm.yeojeongJeonghagi = { m ->
            jeonghagi(Mokjeok(m.ireum, m.lat, m.lon, m.juso))
            talgeotJeonghagi(Talgeot.GEOREUM, false)
            danggyeBakkugi(Danggye.NAM_EUN_GIL)
        }
        jm.yeojeongDochak = { danggyeBakkugi(Danggye.DOCHAK) }
        jm.yeojeongKkeut = { kkeut() }
        jm.yeojeongMok = { jigeum?.let { y -> JeomMokjeok(y.mokjeok.ireum, y.mokjeok.juso, y.mokjeok.lat, y.mokjeok.lon) } }
        jm.wiseongGeotgi = { m -> if (m != null) AnnaeEngine.georeoGagi(Jangso(m.ireum, m.juso, m.lat, m.lon)) else AnnaeEngine.georeoGagi() }
        jm.neagoriBakkeseo = { w -> AnnaeEngine.neagoriBakkeseo(w) }
    }

    /** 앱이 켜질 때 지난 여정을 되살림 */
    private fun bureogi() {
        val f = pail ?: return
        try {
            if (!f.exists()) return
            val y = yeojeongBatgi(JSONObject(f.readText())) ?: return
            if (System.currentTimeMillis() - y.gaengsin > 12 * 3600 * 1000L) { kkeut(); return }
            jigeum = y
            allim()
        } catch (e: Exception) {
            Girok.namgi("yeojeong_oryu", mapOf("e" to (e.message ?: "")))
        }
    }

    fun jeonghagi(m: Mokjeok) {
        val now = System.currentTimeMillis()
        jigeum = Yeojeong(m, Danggye.EOTTEOKE, Talgeot.GEOREUM, false, now, now)
        Girok.namgi("yeojeong_sijak", mapOf("mok" to m.ireum))
        jeojang()
    }

    fun danggyeBakkugi(d: Danggye) {
        val y = jigeum ?: return
        jigeum = y.copy(danggye = d, gaengsin = System.currentTimeMillis())
        Girok.namgi("yeojeong_danggye", mapOf("d" to d.raw))
        jeojang()
        val tg = talgeot
        main.post {
            if (d == Danggye.TANEUN_JUNG && tg in listOf(Talgeot.CHA, Talgeot.GICHA, Talgeot.BEOSEU, Talgeot.GOSOKBEOSEU)) chaTamHook?.invoke(true)
            else if (d != Danggye.TANEUN_JUNG) chaTamHook?.invoke(false)
        }
    }

    /** 버스 정류장 담기(없애려면 null) */
    fun beoseuNoki(b: BeoseuGil?) {
        val y = jigeum ?: return
        jigeum = y.copy(beoseu = b, gaengsin = System.currentTimeMillis())
        jeojang()
    }

    /** 지하철 길 담기(없애려면 null) */
    fun jihaNoki(g: JihaGil?) {
        val y = jigeum ?: return
        jigeum = y.copy(jiha = g, gaengsin = System.currentTimeMillis())
        jeojang()
    }

    /** 탈것 정하기 — barojabeum 이 참이면 이용자가 바로잡은 것(가장 앞섬) */
    fun talgeotJeonghagi(t: Talgeot, barojabeum: Boolean) {
        val y = jigeum ?: return
        jigeum = y.copy(talgeot = t, barojabeum = barojabeum, gaengsin = System.currentTimeMillis())
        jeojang()
    }

    /** 이용자가 탈것을 바로잡음 — 가장 앞섬 */
    fun talgeotBarojapgi(t: Talgeot) {
        val y = jigeum ?: return
        jigeum = y.copy(talgeot = t, barojabeum = true, gaengsin = System.currentTimeMillis())
        Girok.namgi("yeojeong_barojapgi", mapOf("t" to t.raw))
        jeojang()
    }

    fun kkeut() {
        if (jigeum != null) Girok.namgi("yeojeong_kkeut")
        main.post { chaTamHook?.invoke(false) }
        jigeum = null
        try { pail?.delete() } catch (e: Exception) {}
        allim()
    }

    /** 지금 탈것 — 바로잡은 것이 가장 앞서고, 그다음이 빠르기로 알아챈 것 */
    val talgeot: Talgeot
        get() {
            val y = jigeum
            if (y != null && y.barojabeum) return y.talgeot
            if (sokdoChujeong != Talgeot.GEOREUM) return sokdoChujeong
            return y?.talgeot ?: Talgeot.GEOREUM
        }

    fun jeojang() {
        allim()
        val y = jigeum ?: return
        val f = pail ?: return
        try {
            val t = File(f.parentFile, f.name + ".tmp")
            t.writeText(yeojeongJson(y).toString())
            if (!t.renameTo(f)) { f.writeText(yeojeongJson(y).toString()); t.delete() }
        } catch (e: Exception) {
            Girok.namgi("yeojeong_oryu", mapOf("e" to (e.message ?: "")))
        }
    }

    /** 바뀐 것을 화면에 한 번만(같은 줄에서 여러 번 바뀌어도) */
    private fun allim() {
        if (allimDaegi) return
        allimDaegi = true
        main.post {
            allimDaegi = false
            byeonhwa?.invoke()
        }
    }

    /** 2.9.0 탈것 알아채기(TalgeotGamji — 걸음 센서·가속도·기압계)가 알아챈 탈것 — 위성 빠르기보다 먼저 */
    fun gamjiBatda(t: Talgeot) {
        if (sokdoChujeong == Talgeot.GICHA && t == Talgeot.CHA) return   // 기차는 빠르기로만
        if (t == sokdoChujeong) return
        sokdoChujeong = t
        AnnaeEngine.talgeotBakkwim(t)
    }

    /** 시속 15킬로미터를 15초 넘게 넘으면 차(시속 150을 넘으면 기차), 시속 8 아래로 3분이면 걸음 */
    private fun sokdoBoda(w: Jari) {
        if (w.georeumChu || w.ochae > 30) return
        val kmh = w.sokdo * 3.6
        val now = System.currentTimeMillis()
        // 2.9.0 걷기·차·버스·지하철은 TalgeotGamji 가 가림 — 여기서는 기차(시속 150 넘게 15초)만
        if (kmh > 150) {
            if (ppareunTtae == 0L) ppareunTtae = now
            if (now - ppareunTtae >= 15000 && sokdoChujeong != Talgeot.GICHA) {
                sokdoChujeong = Talgeot.GICHA
                Girok.namgi("talgeot_chujeong", mapOf("t" to "gicha", "kmh" to kmh.toInt()))
                AnnaeEngine.talgeotBakkwim(Talgeot.GICHA)
            }
        } else {
            ppareunTtae = 0L
            if (sokdoChujeong == Talgeot.GICHA && TalgeotGamji.chujeong == Talgeot.GEOREUM) sokdoChujeong = Talgeot.GEOREUM
        }
        if (true) return
        if (kmh > 15) {
            neurinTtae = 0L
            if (ppareunTtae == 0L) ppareunTtae = now
            if (now - ppareunTtae >= 15000) {
                val sae = if (kmh > 150) Talgeot.GICHA else (if (sokdoChujeong == Talgeot.GICHA) Talgeot.GICHA else Talgeot.CHA)
                if (sae != sokdoChujeong) {
                    sokdoChujeong = sae
                    Girok.namgi("talgeot_chujeong", mapOf("t" to sae.raw, "kmh" to kmh.toInt()))
                    AnnaeEngine.talgeotBakkwim(sae)
                }
            }
        } else if (kmh < 8) {
            ppareunTtae = 0L
            if (neurinTtae == 0L) neurinTtae = now
            if (now - neurinTtae >= 180000 && sokdoChujeong != Talgeot.GEOREUM) {
                sokdoChujeong = Talgeot.GEOREUM
                Girok.namgi("talgeot_chujeong", mapOf("t" to "georeum", "kmh" to kmh.toInt()))
                AnnaeEngine.talgeotBakkwim(Talgeot.GEOREUM)
            }
        }
    }

    // MARK: 파일 꼴(아이폰 Codable 과 같은 이름)

    private fun strList(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        return (0 until a.length()).map { a.optString(it, "") }
    }

    private fun yeojeongJson(y: Yeojeong): JSONObject {
        val o = JSONObject()
        o.put("mokjeok", JSONObject().put("ireum", y.mokjeok.ireum).put("lat", y.mokjeok.lat).put("lon", y.mokjeok.lon).put("juso", y.mokjeok.juso))
        o.put("danggye", y.danggye.raw)
        o.put("talgeot", y.talgeot.raw)
        o.put("barojabeum", y.barojabeum)
        o.put("sijak", y.sijak)
        o.put("gaengsin", y.gaengsin)
        y.jiha?.let { g ->
            val gu = JSONArray()
            for (k in g.gugan) gu.put(JSONObject().put("hoseon", k.hoseon).put("bangmyeon", k.bangmyeon).put("jina", JSONArray(k.jina)))
            o.put("jiha", JSONObject()
                .put("from", g.from).put("to", g.to).put("mal", g.mal).put("bun", g.bun)
                .put("jina", JSONArray(g.jina)).put("gugan", gu).put("ipgu", g.ipgu.json())
                .put("naeril", g.naeril).put("ipguDochak", g.ipguDochak).put("i", g.i).put("kkeutnam", g.kkeutnam))
        }
        y.beoseu?.let { b ->
            val j = b.jeongryujang
            o.put("beoseu", JSONObject()
                .put("jeongryujang", JSONObject().put("no", j.no).put("ireum", j.ireum).put("lat", j.lat).put("lon", j.lon))
                .put("dochak", b.dochak))
        }
        return o
    }

    private fun yeojeongBatgi(o: JSONObject): Yeojeong? {
        val m = o.optJSONObject("mokjeok") ?: return null
        val mla = Jeomjido.su(m, "lat") ?: return null
        val mlo = Jeomjido.su(m, "lon") ?: return null
        var jiha: JihaGil? = null
        o.optJSONObject("jiha")?.let { g ->
            val gu = ArrayList<JihaGugan>()
            val ga = g.optJSONArray("gugan")
            if (ga != null) for (i in 0 until ga.length()) {
                val k = ga.optJSONObject(i) ?: continue
                gu.add(JihaGugan(Jeomjido.gul(k, "hoseon"), Jeomjido.gul(k, "bangmyeon"), strList(k.optJSONArray("jina"))))
            }
            val ip = Jangso.batgi(g.optJSONObject("ipgu"))
            if (ip != null) {
                jiha = JihaGil(Jeomjido.gul(g, "from"), Jeomjido.gul(g, "to"), Jeomjido.gul(g, "mal"), g.optInt("bun", 0),
                    strList(g.optJSONArray("jina")), gu, ip, Jeomjido.gul(g, "naeril"),
                    g.optBoolean("ipguDochak", false), g.optInt("i", -1), g.optBoolean("kkeutnam", false))
            }
        }
        var beoseu: BeoseuGil? = null
        o.optJSONObject("beoseu")?.let { b ->
            val j = b.optJSONObject("jeongryujang")
            if (j != null) {
                val la = Jeomjido.su(j, "lat")
                val lo = Jeomjido.su(j, "lon")
                if (la != null && lo != null) beoseu = BeoseuGil(Jeongryujang(Jeomjido.gul(j, "no"), Jeomjido.gul(j, "ireum"), la, lo), b.optBoolean("dochak", false))
            }
        }
        val now = System.currentTimeMillis()
        return Yeojeong(
            Mokjeok(Jeomjido.gul(m, "ireum"), mla, mlo, Jeomjido.gul(m, "juso")),
            Danggye.batgi(o.optString("danggye", "eotteoke")),
            Talgeot.batgi(o.optString("talgeot", "georeum")),
            o.optBoolean("barojabeum", false),
            o.optLong("sijak", now),
            o.optLong("gaengsin", 0L),
            jiha, beoseu
        )
    }
}
