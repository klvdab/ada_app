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
    GEOREUM("걸어서"), CHA("차"), JIHACHEOL("지하철"), BEOSEU("버스"), GICHA("기차"), GOSOKBEOSEU("고속버스"),
    /** 2.36.0 (261011-A1, 이사장님 승인 2026-10-11 「1」) 배(여객선·도선) — 아이폰 2.64.0 과 같음 */
    SEONBAK("배");

    /** 아이폰 rawValue 와 같은 이름(기록·파일에 씀) */
    val raw: String get() = when (this) {
        GEOREUM -> "georeum"; CHA -> "cha"; JIHACHEOL -> "jihacheol"; BEOSEU -> "beoseu"; GICHA -> "gicha"; GOSOKBEOSEU -> "gosokbeoseu"; SEONBAK -> "seonbak"
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
    val beoseu: BeoseuGil? = null,
    /** 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 지하철 안내를 역 입구 없이 짐작으로 시작했는가 — 앱을 다시 켜도 잇도록 담음 */
    val jungganJadong: Boolean = false,
    /** 2.36.0 (261011-A1, 이사장님 승인 2026-10-11 「1」) 탈것 지킴(기차·배) — 알아챘거나 「기차입니다」·「배입니다」를 누르시면 바꾸실 때까지 지킴.
     *  기차·배 안에서 걸어 다니셔도 풀지 않음. 다른 탈것으로 바로잡으시거나 내렸습니다·도착·여정 끝에만 풂(아이폰과 같음) */
    val jikim: Talgeot? = null,
    /** 2.62.0 (261017-A50, 이사장님 승인 2026-10-17) 목적지를 정할 때 잰 곧은 거리 — 예상 도착 시간을 어림함(웹 길눈 dangye.js 260927-14, 아이폰과 같음) */
    val yegeori: Double? = null,
    /** 2.62.0 예상 도착 시간이 지나 여쭌 뒤 「이어서 가기」를 누르셨는가 */
    val ieoga: Boolean = false
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
    /** 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 시속 150킬로미터 넘게 달린 마지막 때 — 10분 안이면 기차를 타고 계신 것으로 보고 지하철 짐작을 하지 않음 */
    private var gichaTtae = 0L
    val choegeunGicha: Boolean
        get() = gichaJikimJung || (gichaTtae > 0 && System.currentTimeMillis() - gichaTtae < 600_000L)
    /** 2.36.0 (261011-A1, 이사장님 승인 2026-10-11 「1」) 지키는 탈것(기차·배) */
    val jikimTalgeot: Talgeot? get() = jigeum?.jikim
    val gichaJikimJung: Boolean get() = jikimTalgeot == Talgeot.GICHA
    val jikimJung: Boolean get() = jikimTalgeot != null
    /** 2.36.0 시속 150 넘은 자료 수(20초 안) — 두 번이면 기차 */
    private var gichaSu = 0
    private var gichaSuTtae = 0L
    private var allimDaegi = false

    /** 2.60.0 (261017-A48, 이사장님 승인 2026-10-17) 「탈것 바꾸기」 목록을 펼쳤는가 — 평소에는 한 줄로 접어 두고,
     *  이용자가 부르시거나(누름·「탈것 바꿔」) 길눈이 스스로 탈것을 바꾸었을 때만 펼침. 길눈이 펼친 것은 3분 뒤 저절로 접음 */
    @Volatile var talgeotPyeolchim = false
        private set
    private var pyeolSedae = 0

    fun talgeotMokrokPyeolgi(jadong: Boolean) {
        main.post {
            pyeolSedae += 1
            talgeotPyeolchim = true
            allim()
            if (jadong) {
                val s = pyeolSedae
                main.postDelayed({ if (pyeolSedae == s && talgeotPyeolchim) { talgeotPyeolchim = false; allim() } }, 180_000L)
            }
        }
    }

    fun talgeotMokrokJeopgi() {
        main.post {
            pyeolSedae += 1
            if (talgeotPyeolchim) { talgeotPyeolchim = false; allim() }
        }
    }

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
        jinanDolligi()   // 2.62.0 지난 여정 여쭙기 — 1분마다
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

    /** 2.31.0 (261009-A19, 이사장님 승인 2026-10-09 남산) 새 목적지를 정할 때 — 지금 탈것이 움직이고 있지 않으면 묵은 탈것 판단을 지우고 새로 봄 */
    fun talgeotSaeroBogi() {
        TalgeotGamji.saeYeojeong()
        if (sokdoChujeong != Talgeot.GICHA && sokdoChujeong != TalgeotGamji.chujeong) sokdoChujeong = TalgeotGamji.chujeong
    }

    /** 2.31.0 2분 안에 탈것이 움직였는가 */
    val choegeunTalgeotUmjigim: Boolean
        get() = TalgeotGamji.majimakTalgeot > 0 && System.currentTimeMillis() - TalgeotGamji.majimakTalgeot < 120_000

    fun jeonghagi(m: Mokjeok) {
        // 2.36.0 (261011-A1, 이사장님 승인 2026-10-11 「1」) 기차·배 안에서 목적지를 바꾸셔도 지킴은 그대로
        val jikimIeum: Talgeot? = if (jigeum?.danggye == Danggye.TANEUN_JUNG) jigeum?.jikim else null
        talgeotSaeroBogi()
        val now = System.currentTimeMillis()
        jigeum = Yeojeong(m, Danggye.EOTTEOKE, jikimIeum ?: Talgeot.GEOREUM, false, now, now, jikim = jikimIeum)
        if (jikimIeum != null) {
            if (jikimIeum == Talgeot.GICHA) sokdoChujeong = Talgeot.GICHA
            main.post { AnnaeEngine.jikimRo(jikimIeum, "${jikimIeum.ireum} 안에서 목적지를 바꾸심") }
        }
        Girok.namgi("yeojeong_sijak", mapOf("mok" to m.ireum))
        // 2.62.0 (261017-A50, 이사장님 승인 2026-10-17) 예상 도착 시간을 셈할 곧은 거리를 적어 두고,
        //   가는 길 둘레의 길 자료를 서버가 미리 받아 두게 함(웹 길눈 miribada.php — 걷는 도중 사거리 안내를 기다리지 않게, 아이폰과 같음)
        jinanMureum = false; dochakMureum = false; gakkaiTtae = 0L
        val q = HashMap<String, String>()
        q["mlat"] = String.format(java.util.Locale.US, "%.6f", m.lat); q["mlon"] = String.format(java.util.Locale.US, "%.6f", m.lon)
        Wichi.jigeum?.let { w ->
            jigeum = jigeum?.copy(yegeori = Wichi.geori(w.lat, w.lon, m.lat, m.lon))
            q["slat"] = String.format(java.util.Locale.US, "%.6f", w.lat); q["slon"] = String.format(java.util.Locale.US, "%.6f", w.lon)
        }
        Tongsin.json("miribada.php", q) { }
        jeojang()
    }

    // MARK: 2.62.0 (261017-A50, 이사장님 승인 2026-10-17) 지난 여정·도착 여쭙기 — 웹 길눈 dangye.js 0.4(260927-14)에서 옮김(아이폰 2.92.0과 같음)
    @Volatile var jinanMureum = false
        private set
    @Volatile var dochakMureum = false
        private set
    private var gakkaiTtae = 0L
    private var jinanDolgi = false

    private fun yesangTtae(y: Yeojeong): Long {
        val g = y.yegeori ?: 0.0
        if (g <= 0) return y.sijak + 3_600_000L
        val tago = y.danggye == Danggye.TANEUN_JUNG || y.danggye == Danggye.TANEUN_GOT_KKAJI || y.talgeot != Talgeot.GEOREUM
        val bun = if (tago) (g * 1.4 / 1000) / 25 * 60 else (g * 1.3 / 1000) / 4 * 60
        return y.sijak + ((bun + 30) * 60_000).toLong()
    }

    private fun jinanSalpim() {
        val y = jigeum ?: return
        if (y.danggye == Danggye.DOCHAK || y.ieoga || jinanMureum) return
        if (System.currentTimeMillis() <= yesangTtae(y)) return
        jinanMureum = true
        Girok.namgi("jinan_yeojeong_mureum", mapOf("mok" to y.mokjeok.ireum))
        Sori.mal("${y.mokjeok.ireum}로 가시는 여정이 예상 도착 시간을 30분 넘게 지났습니다. 아직 가시는 중이면 맨 위의 이어서 가기를, 아니면 지난 여정 끝내기를 누르십시오.")
        allim()
    }

    fun ieogaGagi() {
        val y = jigeum ?: return
        jigeum = y.copy(ieoga = true)
        jinanMureum = false
        jeojang()
        Sori.mal("${y.mokjeok.ireum}까지 이어서 안내합니다.")
    }

    /** 목적지 50미터 안에서 2분 — 걸어서 가는 차례는 걷는 안내가 도착을 알리므로 뺌 */
    fun dochakBoda(w: Jari) {
        val y = jigeum
        if (y == null || y.danggye == Danggye.DOCHAK || y.danggye == Danggye.NAM_EUN_GIL || y.jiha != null || dochakMureum) { gakkaiTtae = 0L; return }
        if (Wichi.geori(w.lat, w.lon, y.mokjeok.lat, y.mokjeok.lon) <= 50) {
            val now = System.currentTimeMillis()
            if (gakkaiTtae == 0L) gakkaiTtae = now
            else if (now - gakkaiTtae >= 120_000) {
                dochakMureum = true
                Girok.namgi("dochak_mureum", mapOf("mok" to y.mokjeok.ireum))
                Sori.mal("${y.mokjeok.ireum} 가까이에서 2분째 머물고 계십니다. 도착하셨습니까? 맨 위에 도착했습니다 단추가 있습니다.")
                allim()
            }
        } else gakkaiTtae = 0L
    }

    private fun jinanDolligi() {
        if (jinanDolgi) return
        jinanDolgi = true
        val r = object : Runnable { override fun run() { try { jinanSalpim() } catch (e: Exception) {}; main.postDelayed(this, 60_000L) } }
        main.postDelayed(r, 60_000L)
    }

    fun danggyeBakkugi(d: Danggye) {
        val y = jigeum ?: return
        // 2.36.0 (261011-A1, 이사장님 승인 2026-10-11 「1」) 내리셨거나 도착하면 지킴을 풂
        val jk = if (d == Danggye.NAM_EUN_GIL || d == Danggye.DOCHAK || d == Danggye.EOTTEOKE) null else y.jikim
        jigeum = y.copy(danggye = d, gaengsin = System.currentTimeMillis(), jikim = jk)
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

    /** 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 짐작으로 시작한 지하철 안내인지 담기 */
    fun jungganJadongNoki(b: Boolean) {
        val y = jigeum ?: return
        if (y.jungganJadong == b) return
        jigeum = y.copy(jungganJadong = b)
        jeojang()
    }

    /** 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 여정 끝·하던 일 멈춤 — 빠르기로 알아챈 탈것을 처음부터(기차 기억 10분은 그대로) */
    fun talgeotChoGihwa() {
        ppareunTtae = 0L
        neurinTtae = 0L
        sokdoChujeong = Talgeot.GEOREUM
    }

    /** 탈것 정하기 — barojabeum 이 참이면 이용자가 바로잡은 것(가장 앞섬) */
    fun talgeotJeonghagi(t: Talgeot, barojabeum: Boolean) {
        val y = jigeum ?: return
        // 2.60.0 (261017-A48, 이사장님 승인 2026-10-17) 이용자가 정하시면 탈것 목록을 접고, 길눈이 스스로 바꾸었으면 펼침(처음 걸어가기·타고 가기는 빼고)
        if (barojabeum) talgeotMokrokJeopgi()
        else if (y.talgeot != t && y.danggye != Danggye.EOTTEOKE) talgeotMokrokPyeolgi(true)
        jigeum = y.copy(talgeot = t, barojabeum = barojabeum, gaengsin = System.currentTimeMillis())
        jeojang()
    }

    /** 이용자가 탈것을 바로잡음 — 가장 앞섬 */
    fun talgeotBarojapgi(t: Talgeot) {
        val y = jigeum ?: return
        // 2.36.0 (261011-A1, 이사장님 승인 2026-10-11 「1」) 「기차입니다」·「배입니다」는 바꾸실 때까지 지킴, 다른 탈것으로 바로잡으시면 풂
        val jk = if (t == Talgeot.GICHA || t == Talgeot.SEONBAK) t else null
        if (t != Talgeot.GICHA && sokdoChujeong == Talgeot.GICHA) { gichaTtae = 0L; sokdoChujeong = Talgeot.GEOREUM }
        jigeum = y.copy(talgeot = t, barojabeum = true, gaengsin = System.currentTimeMillis(), jikim = jk)
        talgeotMokrokJeopgi()   // 2.60.0 고르시면 목록을 접음
        Girok.namgi("yeojeong_barojapgi", mapOf("tal" to t.raw))
        jeojang()
    }

    fun kkeut() {
        if (jigeum != null) Girok.namgi("yeojeong_kkeut")
        talgeotMokrokJeopgi()   // 2.60.0
        jinanMureum = false; dochakMureum = false; gakkaiTtae = 0L   // 2.62.0
        main.post { chaTamHook?.invoke(false) }
        jigeum = null
        try { pail?.delete() } catch (e: Exception) {}
        allim()
    }

    /** 2.36.0 (261011-A1, 이사장님 승인 2026-10-11 「1」) 탈것 지킴 시작(기차·배) — 바꾸실 때까지 지킴 */
    fun jikimSijak(t: Talgeot, kkadak: String) {
        val y = jigeum
        if (y == null || y.jikim == t) {
            if (t == Talgeot.GICHA && sokdoChujeong != Talgeot.GICHA) sokdoChujeong = Talgeot.GICHA
            return
        }
        if (y.barojabeum && y.talgeot != t) return   // 이용자가 다른 탈것으로 바로잡으신 것이 가장 앞섬
        jigeum = y.copy(jikim = t, talgeot = t, gaengsin = System.currentTimeMillis())
        jeojang()
        if (t == Talgeot.GICHA) { gichaTtae = System.currentTimeMillis(); sokdoChujeong = Talgeot.GICHA }
        Girok.namgi("jikim", mapOf("tal" to t.raw, "kkadak" to kkadak))
        main.post { AnnaeEngine.jikimRo(t, kkadak) }
    }

    /** 지금 탈것 — 바로잡은 것이 가장 앞서고, 그다음이 기차·배 지킴, 그다음이 빠르기로 알아챈 것 */
    val talgeot: Talgeot
        get() {
            val y = jigeum
            if (y != null && y.barojabeum) return y.talgeot
            jikimTalgeot?.let { return it }
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
        // 2.36.0 (261011-A1, 이사장님 승인 2026-10-11 「1」) 지킴 중에는 움직임 감지기의 판단으로 바꾸지 않음
        if (jikimJung) return
        // 2.36.0 기차역 근처에서 탔고 목적지가 멀리 있는 기차역이면 처음부터 기차(부산역에서 지하철로 알던 일 — 아이폰과 같음)
        val y = jigeum
        if ((t == Talgeot.CHA || t == Talgeot.JIHACHEOL) && (sokdoChujeong == Talgeot.GEOREUM || sokdoChujeong == Talgeot.JIHACHEOL)
            && y != null && (!y.barojabeum || y.talgeot == Talgeot.GICHA)) {
            val w = TalgeotGamji.chaSijakJari ?: Wichi.jigeum
            val yeok = if (w != null) GichaYeok.gichaInga(w.lat, w.lon, y.mokjeok.ireum, y.mokjeok.lat, y.mokjeok.lon) else null
            if (yeok != null) { jikimSijak(Talgeot.GICHA, "${yeok}역에서 탐"); return }
        }
        if (sokdoChujeong == Talgeot.GICHA && t == Talgeot.CHA) return   // 기차는 빠르기로만
        if (sokdoChujeong == Talgeot.GICHA && t == Talgeot.JIHACHEOL && choegeunGicha) return   // 2.32.0 기차 터널을 지하철로 보지 않음
        if (t == sokdoChujeong) return
        sokdoChujeong = t
        AnnaeEngine.talgeotBakkwim(t)
    }

    /** 시속 15킬로미터를 15초 넘게 넘으면 차(시속 150을 넘으면 기차), 시속 8 아래로 3분이면 걸음 */
    private fun sokdoBoda(w: Jari) {
        if (w.georeumChu || w.ochae > 50) return
        val kmh = w.sokdo * 3.6
        val now = System.currentTimeMillis()
        if (kmh > 150) gichaTtae = now   // 2.32.0 기차 기억(잠깐이라도 시속 150 넘으면)
        // 2.36.0 (261011-A1, 이사장님 승인 2026-10-11 「1」) 예전에는 시속 150을 15초 동안 한 번도 끊기지 않고 넘어야 기차였음 —
        //   이제 20초 안에 시속 150 넘은 자료가 두 번이면 기차로 보고 지킴(아이폰과 같음)
        if (kmh > 150 && w.ochae <= 30) {
            if (gichaSuTtae > 0 && now - gichaSuTtae > 20_000) gichaSu = 0
            if (gichaSu == 0) gichaSuTtae = now
            gichaSu += 1
            if (gichaSu >= 2 && !gichaJikimJung && jikimTalgeot != Talgeot.SEONBAK) {
                Girok.namgi("talgeot_chujeong", mapOf("tal" to "gicha", "kmh" to kmh.toInt()))
                jikimSijak(Talgeot.GICHA, "시속 ${kmh.toInt()}킬로미터")
                if (jigeum == null && sokdoChujeong != Talgeot.GICHA) sokdoChujeong = Talgeot.GICHA
            }
        }
        if (jikimJung) return
        // 2.9.0 걷기·차·버스·지하철은 TalgeotGamji 가 가림 — 여기서는 기차(시속 150 넘게 15초)만
        if (kmh > 150) {
            if (ppareunTtae == 0L) ppareunTtae = now
            if (now - ppareunTtae >= 15000 && sokdoChujeong != Talgeot.GICHA) {
                sokdoChujeong = Talgeot.GICHA
                Girok.namgi("talgeot_chujeong", mapOf("tal" to "gicha", "kmh" to kmh.toInt()))
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
                    Girok.namgi("talgeot_chujeong", mapOf("tal" to sae.raw, "kmh" to kmh.toInt()))
                    AnnaeEngine.talgeotBakkwim(sae)
                }
            }
        } else if (kmh < 8) {
            ppareunTtae = 0L
            if (neurinTtae == 0L) neurinTtae = now
            if (now - neurinTtae >= 180000 && sokdoChujeong != Talgeot.GEOREUM) {
                sokdoChujeong = Talgeot.GEOREUM
                Girok.namgi("talgeot_chujeong", mapOf("tal" to "georeum", "kmh" to kmh.toInt()))
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
        if (y.jungganJadong) o.put("jungganJadong", true)   // 2.32.0 아이폰 Codable 과 같은 이름
        y.jikim?.let { o.put("jikim", it.raw) }   // 2.36.0
        y.yegeori?.let { o.put("yegeori", it) }   // 2.62.0
        if (y.ieoga) o.put("ieoga", true)
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
            jiha, beoseu,
            o.optBoolean("jungganJadong", false),
            o.optString("jikim", "").let { if (it.isEmpty()) null else Talgeot.batgi(it) },
            if (o.has("yegeori")) o.optDouble("yegeori") else null,
            o.optBoolean("ieoga", false)
        )
    }
}
