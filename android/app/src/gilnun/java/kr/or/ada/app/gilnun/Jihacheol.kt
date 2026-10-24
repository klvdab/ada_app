// 안드로이드 길눈 — 지하철(묶음 b1, 아이폰 JihacheolEngine.swift·JihacheolView.swift 를 같은 잣대·같은 말로 옮김)
// 타는 역까지 걷기, 열차에 탄 것 알아채기, 지나는 역마다 알리기, 갈아타기, 내릴 역, 나갈 출구.
// 웹 길눈 tamseung.js 1.1(2026-09-18 이사장님 지시 "지하철을 타고 오는데 역 이름을 하나도 말해 주지 않더라")을 앱으로 옮긴 것.
// 지나는 역은 세 겹으로 헤아립니다.
//   ① 서울 실시간 열차 위치(yeok.php a=silsi) — 우리가 탄 열차를 잡아 역마다 알림
//   ② 폰 흔들림(가속도계)으로 섰다 떠나는 것을 세어 몇 번째 역인지 헤아림(통신이 끊겨도 됨)
//   ③ 역 사이 걸리는 시간으로 셈함
// 타는 역 출구에 닿은 뒤 열차가 움직이는데 걸음이 없으면 "탔다"고 보고 저절로 역 알림을 시작합니다(손을 쓰지 않게).
// 나스: yeok.php a=gakkaun(가까운 역), a=gil(역에서 역까지), a=chulgu(가까운 출구), a=silsi(실시간 열차 위치)
// 아이폰과 다른 점: 가속도 값은 안드로이드가 초제곱 미터로 주므로 중력(9.80665)으로 나누어 아이폰과 같은 잣대(흔들림 0.056)로 셈.
//   걸음은 Wichi.georeumSu 를 씀
package kr.or.ada.app.gilnun

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.view.View
import org.json.JSONArray
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object JihacheolEngine : SensorEventListener {
    private val main = Handler(Looper.getMainLooper())
    private var ctx: Context? = null
    private var sm: SensorManager? = null
    private var gasokKyeojim = false

    private val chang = ArrayList<Double>()
    private var dallim = false
    private var seonTtae = 0L
    private var dallimSijak = 0L
    private var dallimGeoreum = 0
    private var yeolcha = ""
    /** 2.9.0 실시간이 내 열차를 마지막으로 확인해 준 때 — 90초가 지나면 다른 셈이 다시 맡음 */
    private var silsiHwagin = 0L
    private var silsiJal: Boolean
        get() = yeolcha.isNotEmpty() && System.currentTimeMillis() - silsiHwagin < 90000
        set(v) { if (!v) silsiHwagin = 0L }
    private var tamTtae = 0L
    private var huboJikyeo = HashMap<String, Int>()
    private var silsiMot = 0
    private var majimak = System.currentTimeMillis()
    private var hwanJa: List<Int> = emptyList()
    private var hoseon = ""
    private var kkeut = ""
    private var from = ""
    var dolgo = false
        private set
    private var tabeumGamsi = false
    private var silsiMutneunJung = false
    private var sedae = 0
    /** 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 앱을 다시 켜서 이어 가는 중(탄 때를 모름) — 땅 위로 나온 것을 15초 걷기로만 봄 */
    private var ieoGamTamMoreum = false

    /** 2.32.0 세대 번호를 올림(탈것을 바로잡으실 때 — 늦게 돌아온 지하철 길 찾기를 버림) */
    fun sedaeOllim() { sedae += 1 }

    private val yj get() = YeojeongEngine
    val gil: JihaGil? get() = yj.jigeum?.jiha

    fun ireum(n: String): String = if (n.endsWith("역")) n.dropLast(1) else n

    fun sijak(c: Context) {
        ctx = c.applicationContext
        sm = c.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        // 2.9.0 탈것이 섰다가 떠남으로도 역을 셈
        TalgeotGamji.seotdaTteonam = { t ->
            if (dolgo && t >= 8 && t <= 120 && !silsiJal && System.currentTimeMillis() - majimak > 40000) hanYeok()
        }
        // 2.9.0 기압으로 땅속에 내려가신 것을 알면 — 타는 역 근처면 역에 닿은 것으로
        TalgeotGamji.jihaJinip = {
            val y = yj.jigeum
            val g = y?.jiha
            if (y != null && g != null && y.danggye == Danggye.TANEUN_GOT_KKAJI && !g.ipguDochak) {
                val w = TalgeotGamji.jisangJari ?: Wichi.jigeum
                if (w == null || Wichi.geori(w.lat, w.lon, g.ipgu.lat, g.ipgu.lon) <= 300) {
                    Girok.namgi("jiha_ipgu_gido")
                    ipguDochak()
                }
            }
        }
    }

    /** 2.9.0 가장 가까운 역까지의 거리(미터) */
    fun gakkaunYeokGeori(lat: Double, lon: Double, kkeut: (Double?) -> Unit) {
        gakkaun(lat, lon) { y -> kkeut(y?.let { Wichi.geori(lat, lon, it.lat, it.lon) }) }
    }

    /** 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 이용자가 지하철로 바로잡으심 — 짐작으로 시작한 안내가 아니므로 땅 위 구간(동호대교 등)에서 차로 되돌리지 않음(아이폰과 같음) */
    fun jadongPulgi() { jungganJadong = false }

    /** 2.9.0 이미 열차를 타고 가는 중에 시작 — 땅속으로 내려가기 전 땅 위 자리에서 가까운 역을 타는 역으로 */
    /** 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) jadong: 저절로 짐작해 시작했는가 — 이용자가 지하철이라고 하신 때(false)는 차로 저절로 되돌리지 않음(아이폰과 같음) */
    /** 2.73.0 (261024-A61, 이사장님 지시 2026-10-24, 아이폰 2.105.0과 같음) 타신 역을 짐작할 믿을 만한 자리가 없어 여쭤야 함 — 이 말로 돌려줌 */
    const val TAN_YEOK_MUREUM = "?tanyeok"
    val TAN_YEOK_MAL: String
        get() = (if (TalgeotGamji.jiha) "땅속이라 위성이 닿지 않아 지금 자리를 확실히 알 수 없습니다." else "지금 자리를 확실히 알 수 없어 타실 역을 정하지 못했습니다.") +
            " 약수역에서 탈 거야처럼 타실 역을 말씀해 주십시오."
    /** 2.73.0 중간에 지하철 길을 찾는 중 */
    var chatneunJung = false
        private set

    /** 2.73.0 (261024-A61, 이사장님 지시 2026-10-24) 「6호선 탔어」처럼 말씀하신 호선 — 타는 역을 고를 때 그 호선 역을 앞에 둠(10분 동안) */
    private var hoseonHint0: String? = null
    private var hoseonHintTtae = 0L
    var hoseonHint: String?
        get() = if (hoseonHintTtae > 0L && System.currentTimeMillis() - hoseonHintTtae < 600_000L) hoseonHint0 else null
        set(v) { hoseonHint0 = v; hoseonHintTtae = if (v == null) 0L else System.currentTimeMillis() }

    /** 2.73.0 (261024-A61) jadong — buteoYeok: 이용자가 말씀하신 타신 역(있으면 그 역에서) */
    fun jungganSijak(mok: Jangso, jadong: Boolean = true, buteoYeok: String? = null, kkeut: (Boolean, String) -> Unit) {
        // 2.73.0 (261024-A61) 땅속으로 내려가기 전 위성이 확실히 잡은 자리(1시간 안)만 타신 역을 짐작하는 데 씀 — 와이파이 짐작 자리로는 짐작하지 않고 여쭘
        val now = System.currentTimeMillis()
        val buteo = listOf(TalgeotGamji.jisangJari, TalgeotGamji.chaSijakJari, Wichi.jigeum)
            .firstOrNull { it != null && !it.jjimjak && now - it.ttae < 3_600_000L && it.ochae <= 65 }
        if (buteoYeok == null && buteo == null) {
            Girok.namgi("jiha_tanyeok_mureum", mapOf("jjimjak" to (Wichi.jigeum?.jjimjak ?: true)))
            kkeut(false, TAN_YEOK_MUREUM)
            return
        }
        val ho = hoseonHint
        chatneunJung = true
        val sd = sedae   // 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 찾는 사이 멈추거나 차로 바로잡으셨으면 버림
        gilChatgi(mok, buteo, buteoYeok, ho) { gg, k ->
            chatneunJung = false
            val y = yj.jigeum
            if (sd != sedae || y == null || (y.barojabeum && y.talgeot != Talgeot.JIHACHEOL)) {
                Girok.namgi("jiha_junggan_beorim")
                return@gilChatgi
            }
            if (gg == null) { kkeut(false, k); return@gilChatgi }
            val g = gg.copy(ipguDochak = true)
            AnnaeEngine.jihacheolGagi(mok, g, malEopsi = true)
            Girok.namgi("jiha_junggan", mapOf("from" to g.from, "to" to g.to))
            kkeut(true, "${g.from}역에서 타신 것으로 보고 ${g.to}역까지 역을 알려 드립니다.")
            tatda(jadong, buteoYeok == null)
            jungganJadong = jadong   // 2.32.0 (261009-A20) 이용자가 바로잡으신 지하철은 false — 땅 위 구간에서 차로 되돌리지 않음(아이폰과 같음)
        }
    }

    // MARK: 길 찾기

    private class Yeok(val yeok: String, val ireum: String, val lat: Double, val lon: Double)

    /** 2.73.0 (261024-A61, 이사장님 지시 2026-10-24) 역 이름 바로잡기 — 「약수역」「6호선 약수」를 지하철 길 자료의 이름(약수)으로. 호선을 주시면 그 호선에 있는 역만 */
    fun yeokIreum(q0: String, hoseon: String? = null, kkeut: (String?) -> Unit) {
        var q = q0.replace(" ", "")
        Regex("^[0-9]{1,2}호선").find(q)?.let { q = q.removeRange(it.range) }
        for (k in listOf("지하철역", "전철역", "역")) if (q.endsWith(k) && q.length > k.length) { q = q.dropLast(k.length); break }
        if (q.isEmpty()) { kkeut(null); return }
        Tongsin.json("yeok.php", mapOf("a" to "chatgi", "q" to q)) { o ->
            val a = o?.optJSONArray("rows")
            if (a == null || a.length() == 0) { kkeut(null); return@json }
            val nms = ArrayList<String>()
            for (i in 0 until a.length()) {
                val r = a.optJSONObject(i) ?: continue
                if (hoseon != null && Jeomjido.gul(r, "hoseon") != hoseon) continue
                if (!r.has("nm") || r.isNull("nm")) continue
                nms.add(Jeomjido.gul(r, "nm"))
            }
            // 이름이 같거나 앞이 같은 역만(엉뚱한 역으로 맞추지 않게)
            kkeut(nms.firstOrNull { it == q } ?: nms.firstOrNull { it.startsWith(q) })
        }
    }

    /** 지금 자리에서 목적지까지 지하철 길 — (길, 못 찾은 까닭). 결과는 화면 줄에서
     *  2.73.0 (261024-A61, 이사장님 지시 2026-10-24, 아이폰 2.105.0과 같음) 가까운 역 하나만 보고 실패하던 것을 바로잡음 — 가까운 역 여럿 가운데 지하철 길 자료에 있는 역을 차례로 대 보고,
     *  말씀하신 호선이 있으면 그 호선 역을 먼저, 타신 역을 말씀하셨으면(buteoYeok) 그 역에서
     *  (6호선 땅속에서 엉뚱하게 잡힌 자리 옆의 경춘선 신내역을 골라 「그 역 이름을 자료에서 찾지 못했습니다」로 하루 내내 역 안내를 못 하던 일) */
    fun gilChatgi(mok: Jangso, buteo: Jari? = null, buteoYeok: String? = null, hoseon: String? = null, kkeut: (JihaGil?, String) -> Unit) {
        if (buteoYeok != null) {
            val by = buteoYeok
            // 그 역의 자리(그 역까지 걷는 안내에 씀) — 못 받으면 0
            val ieo: (String?) -> Unit = { n ->
                if (n == null) kkeut(null, "$by${if (by.endsWith("역")) "" else "역"}을 지하철 역 자료에서 찾지 못했습니다.")
                else Chatgi.jangso(n + "역") { l ->
                    val f = l?.firstOrNull { it.ireum.contains(n) }
                    gilChatgi2(null, mok, listOf(Yeok(n, n + "역", f?.lat ?: 0.0, f?.lon ?: 0.0)), buteoYeok, hoseon, kkeut)
                }
            }
            yeokIreum(by, hoseon) { n0 ->
                if (n0 == null && hoseon != null) yeokIreum(by) { n -> ieo(n) } else ieo(n0)
            }
            return
        }
        val w0 = buteo ?: Wichi.jigeum
        // 2.73.0 (261024-A61) 와이파이로 짐작한 자리나 자리가 없을 때(땅속)는 타실 역을 정하지 않음 — 여쭘
        //   땅 위에서 10분 안에 위성이 확실히 잡은 자리가 있으면 그것을 씀(집 안 와이파이 자리로 묻지 않게, 아이폰과 같음)
        val mm = Wichi.majimakMideum
        val w = if ((w0 == null || w0.jjimjak) && !TalgeotGamji.jiha && mm != null && System.currentTimeMillis() - mm.ttae < 600_000L) mm else w0
        if (w == null || w.jjimjak) {
            Girok.namgi("jiha_tanyeok_mureum", mapOf("gil" to true))
            kkeut(null, TAN_YEOK_MUREUM)
            return
        }
        gakkaunDeul(w.lat, w.lon) { l ->
            var fromDeul = l
            if (hoseon != null) {
                val ap = fromDeul.filter { it.ireum.contains(hoseon) }
                fromDeul = ap + fromDeul.filter { !it.ireum.contains(hoseon) }
            }
            gilChatgi2(w, mok, fromDeul, null, hoseon, kkeut)
        }
    }

    private fun gilChatgi2(w: Jari?, mok: Jangso, fromDeul: List<Yeok>, buteoYeok: String?, hoseon: String?, kkeut: (JihaGil?, String) -> Unit) {
        gakkaunDeul(mok.lat, mok.lon) { tl ->
            val toDeul = tl.take(3)
            if (fromDeul.isEmpty() || toDeul.isEmpty()) { kkeut(null, "가까운 역을 받지 못했습니다. 통신이 끊겼을 수 있습니다."); return@gakkaunDeul }
            val ssang = ArrayList<Pair<Yeok, Yeok>>()
            for (f in fromDeul.take(5)) for (t in toDeul) if (f.yeok != t.yeok) ssang.add(f to t)
            var bon = 0
            fun machim(o: org.json.JSONObject?, fr: Yeok, to: Yeok) {
                Girok.namgi("jiha_gil_chatgi", mapOf("from" to fr.yeok, "to" to to.yeok, "ok" to (o != null), "bon" to bon,
                    "hubo" to fromDeul.take(5).joinToString(",") { it.yeok }, "son" to (buteoYeok != null), "ho" to (hoseon ?: "")))
                if (o == null) {
                    if (fromDeul.size == 1 && toDeul.any { it.yeok == fromDeul[0].yeok }) {
                        kkeut(null, "타실 역과 내리실 역이 같은 ${fromDeul[0].yeok}역입니다. 걸어가시는 편이 낫습니다.")
                    } else {
                        kkeut(null, "${fromDeul[0].yeok}역에서 ${toDeul[0].yeok}역까지 가는 길을 찾지 못했습니다.")
                    }
                    return
                }
                gilMandeulgi(o, if (buteoYeok == null) w else null, mok, fr, to, kkeut)
            }
            fun dae(k: Int) {
                if (k >= ssang.size) { machim(null, fromDeul[0], toDeul[0]); return }
                val (f, t) = ssang[k]
                bon += 1
                Tongsin.json("yeok.php", mapOf("a" to "gil", "from" to f.yeok, "to" to t.yeok)) { o ->
                    if (o != null && o.optBoolean("ok", false)) machim(o, f, t) else dae(k + 1)
                }
            }
            dae(0)
        }
    }

    private fun gilMandeulgi(o: org.json.JSONObject, w: Jari?, mok: Jangso, fr: Yeok, to: Yeok, kkeut: (JihaGil?, String) -> Unit) {
        val jina = strList(o.optJSONArray("jina"))
        val gugan = ArrayList<JihaGugan>()
        val ga = o.optJSONArray("gugan")
        if (ga != null) for (i in 0 until ga.length()) {
            val k = ga.optJSONObject(i) ?: continue
            gugan.add(JihaGugan(Jeomjido.gul(k, "hoseon"), Jeomjido.gul(k, "bangmyeon"), strList(k.optJSONArray("jina"))))
        }
        if (jina.isEmpty()) { kkeut(null, "지나는 역을 받지 못했습니다."); return }
        val bun = (Jeomjido.su(o, "bun") ?: (jina.size * 2).toDouble()).toInt()
        val mal = Jeomjido.gul(o, "mal")
        // 들어갈 출구(지금 자리에서 가장 가까운 출구), 나갈 출구(목적지에서 가장 가까운 출구) — 타신 역을 말씀하셨으면 그 역 이름으로
        val naegi: (Jangso) -> Unit = { ipgu ->
            chulgu(mok.lat, mok.lon) { c2 ->
                val naeril = c2?.ireum ?: ""
                kkeut(JihaGil(fr.yeok, to.yeok, mal, bun, jina, gugan, ipgu,
                    if (naeril.contains(to.yeok)) naeril else "", false, -1, false), "")
            }
        }
        val gibon = Jangso(fr.ireum, "", fr.lat, fr.lon)
        if (w == null) { naegi(gibon); return }
        chulgu(w.lat, w.lon) { c -> naegi(if (c != null && c.ireum.contains(fr.yeok)) c else gibon) }
    }

    /** 2.73.0 가까운 역 여럿(가까운 차례) */
    private fun gakkaunDeul(lat: Double, lon: Double, kkeut: (List<Yeok>) -> Unit) {
        Tongsin.json("yeok.php", mapOf("a" to "gakkaun", "lat" to Chatgi.f6(lat), "lon" to Chatgi.f6(lon))) { o ->
            val a = o?.optJSONArray("rows")
            val l = ArrayList<Yeok>()
            if (a != null) for (i in 0 until a.length()) {
                val r = a.optJSONObject(i) ?: continue
                val la = Jeomjido.su(r, "lat") ?: continue
                val lo = Jeomjido.su(r, "lon") ?: continue
                l.add(Yeok(Jeomjido.gul(r, "yeok"), Jeomjido.gul(r, "ireum"), la, lo))
            }
            kkeut(l)
        }
    }

    private fun strList(a: JSONArray?): List<String> =
        if (a == null) emptyList() else (0 until a.length()).map { a.optString(it, "") }

    private fun gakkaun(lat: Double, lon: Double, kkeut: (Yeok?) -> Unit) {
        Tongsin.json("yeok.php", mapOf("a" to "gakkaun", "lat" to Chatgi.f6(lat), "lon" to Chatgi.f6(lon))) { o ->
            val r = o?.optJSONArray("rows")?.optJSONObject(0)
            val la = r?.let { Jeomjido.su(it, "lat") }
            val lo = r?.let { Jeomjido.su(it, "lon") }
            if (r == null || la == null || lo == null) { kkeut(null); return@json }
            kkeut(Yeok(Jeomjido.gul(r, "yeok"), Jeomjido.gul(r, "ireum"), la, lo))
        }
    }

    private fun chulgu(lat: Double, lon: Double, kkeut: (Jangso?) -> Unit) {
        Tongsin.json("yeok.php", mapOf("a" to "chulgu", "lat" to Chatgi.f6(lat), "lon" to Chatgi.f6(lon))) { o ->
            val r = o?.optJSONArray("rows")?.optJSONObject(0)
            val la = r?.let { Jeomjido.su(it, "lat") }
            val lo = r?.let { Jeomjido.su(it, "lon") }
            if (r == null || la == null || lo == null) { kkeut(null); return@json }
            kkeut(Jangso(Jeomjido.gul(r, "ireum"), "", la, lo))
        }
    }

    // MARK: 타기와 역 알림

    /** 타는 역 출구에 닿음 — 이제 열차가 움직이면 저절로 역 알림을 시작 */
    /** 2.62.0 (261017-A50, 이사장님 승인 2026-10-17) 다음 열차가 몇 분 뒤에 오는지 — 웹 길눈 jicheol.html(빌드 260926-11)에서 옮김(아이폰 2.92.0과 같음).
     *  타는 역의 실시간 도착(yeok.php a=dochak, 서울 열린데이터)에서 내 호선·방면 열차 가운데 가장 먼저 오는 것 */
    fun daeumYeolchaMal(g: JihaGil, kkeut: (String?) -> Unit) {
        val gg = g.gugan.firstOrNull() ?: run { kkeut(null); return }
        val yeok = if (g.from.endsWith("역")) g.from.dropLast(1) else g.from
        Tongsin.json("yeok.php", mapOf("a" to "dochak", "yeok" to yeok)) { o ->
            val rows = o?.optJSONArray("rows")
            if (rows == null || rows.length() == 0) { kkeut(null); return@json }
            val ho = gg.hoseon.replace(" ", "")
            val bang = gg.bangmyeon.replace("방면", "").replace("행", "").trim()
            var best: org.json.JSONObject? = null; var bestCho = 0.0
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val rh = r.optString("hoseon").replace(" ", "")
                if (ho.isNotEmpty() && rh.isNotEmpty() && rh != ho) continue
                val rb = r.optString("bangmyeon")
                if (bang.isNotEmpty() && !rb.contains(bang)) continue
                val cho = r.optDouble("cho", 9999.0)
                if (best == null || cho < bestCho) { best = r; bestCho = cho }
            }
            val b = best ?: run { kkeut(null); return@json }
            val rb = b.optString("bangmyeon")
            val haeng = rb.split(" - ").firstOrNull() ?: rb
            val ml = b.optString("mal")
            val sigan = when {
                ml.contains("전역") -> "전 역을 떠났습니다"
                ml.contains("진입") || ml.contains("도착") -> "곧 들어옵니다"
                bestCho < 9999 -> { val bun = bestCho.toInt() / 60; val c = bestCho.toInt() % 60; (if (bun > 0) "${bun}분" else "") + (if (c > 0) " ${c}초" else "") + " 뒤에 옵니다" }
                else -> ml
            }
            kkeut("다음 $ho $haeng 열차는 ${sigan.trim()}.")
        }
    }

    fun ipguDochak() {
        val g0 = gil ?: return
        val g = g0.copy(ipguDochak = true)
        yj.jihaNoki(g)
        tabeumGamsi = true
        heundeullimSijak()
        val gg = g.gugan.firstOrNull()
        val bang = if ((gg?.bangmyeon ?: "").isEmpty()) "" else " ${gg?.bangmyeon} 방면"
        Sori.mal("${g.ipgu.ireum}입니다. 들어가셔서 ${gg?.hoseon ?: ""}$bang 열차를 타십시오. 열차가 움직이면 저절로 역 알림을 시작합니다.")
        daeumYeolchaMal(g) { m -> if (m != null) Sori.mal(m) }   // 2.62.0 다음 열차가 몇 분 뒤에 오는지 이어서
        Girok.namgi("jiha_ipgu")
    }

    /** 열차에 탔습니다 — 누르셔도 되고, 저절로도 됨 */
    /** 2.31.0 역 입구를 거치지 않고 "이미 타고 가는 중"으로 짐작해 시작한 역 알림인가(아이폰과 같음) */
    var jungganJadong = false
        private set(v) {
            field = v
            // 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 여정 기록에도 담아 앱을 다시 켜도 이어 감
            if ((yj.jigeum?.jungganJadong ?: false) != v) yj.jungganJadongNoki(v)
        }

    fun tatda(jadong: Boolean, tanYeokMal: Boolean = false) {
        jungganJadong = false
        ieoGamTamMoreum = false
        val g0 = gil ?: return
        // 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 이용자가 「지하철 탔어」라 하시거나 「열차에 탔습니다」를 누르신 때는
        //   바로잡기와 같게 — 늦게 돌아온 길 찾기를 버리고 콜 차 못 박기(3시간)를 풂(아이폰과 같음)
        if (!jadong) {
            sedaeOllim()
            ChaBureugi.kolPulgi("barojapgi")
        }
        val g = g0.copy(i = -1, kkeutnam = false, ipguDochak = true)
        yj.jihaNoki(g)
        yj.talgeotJeonghagi(Talgeot.JIHACHEOL, true)
        yj.danggyeBakkugi(Danggye.TANEUN_JUNG)
        junbi(g)
        majimak = System.currentTimeMillis()
        tamTtae = majimak
        val apmal = if (jadong) "열차가 움직이는 것 같습니다. " else ""
        val dwimal = if (jadong) " 지하철이 아니면 택시야라고 말씀해 주십시오." else ""   // 2.31.0 단정하지 않고 바로잡는 말을 함께
        // 2.73.0 (261024-A61, 아이폰 2.105.0과 같음) 타신 역을 짐작했으면 그 역을 밝히고 바로잡는 말을 알려 드림
        val tanMal = if (tanYeokMal) "${g.from}역에서 타신 것으로 압니다. 다르면 약수역에서 탔어처럼 말씀해 주십시오. " else ""
        Sori.mal(apmal + tanMal + "역 알림을 시작합니다. 내리실 역은 ${g.to}역, ${g.jina.size} 정거장 뒤입니다." + (if (g.bun > 0) " 약 ${g.bun}분 걸립니다." else "") + " 지나는 역마다 알려 드립니다." + dwimal)
        Girok.namgi("jiha_tam", mapOf("jadong" to jadong))
        dolligi()
    }

    /** 앱을 껐다 켰을 때 타고 가던 중이면 이어 감 */
    fun ieoGagi() {
        val y = yj.jigeum ?: return
        val g = y.jiha ?: return
        if (y.danggye == Danggye.TANEUN_JUNG && !g.kkeutnam) {
            junbi(g)
            majimak = System.currentTimeMillis()
            dolligi()
            // 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 짐작으로 시작한 안내였는지 되살리고, 탄 때를 모르니 땅 위로 나온 것은 15초 걷기로만 봄
            jungganJadong = y.jungganJadong
            ieoGamTamMoreum = true
        } else if (y.danggye == Danggye.TANEUN_GOT_KKAJI && g.ipguDochak) {
            tabeumGamsi = true
            heundeullimSijak()
        }
    }

    fun meomchugi() {
        jungganJadong = false
        ieoGamTamMoreum = false
        dolgo = false
        tabeumGamsi = false
        sedae += 1
        main.removeCallbacks(poller)
        main.removeCallbacks(ticker)
        gasokKkeugi()
    }

    private fun junbi(g: JihaGil) {
        val hj = ArrayList<Int>()
        var nu = -1
        if (g.gugan.size > 1) {
            for (gi in 0 until g.gugan.size - 1) {
                nu += g.gugan[gi].jina.size
                hj.add(nu)
            }
        }
        hwanJa = hj
        // 이미 지난 역 뒤의 구간으로 호선을 맞춤
        var gu = 0
        for ((k, h) in hwanJa.withIndex()) if (g.i >= h) gu = k + 1
        hoseon = if (gu in g.gugan.indices) g.gugan[gu].hoseon else (g.gugan.firstOrNull()?.hoseon ?: "")
        kkeut = if (gu in g.gugan.indices) g.gugan[gu].bangmyeon else ""
        from = if (g.i >= 0 && g.i < g.jina.size) ireum(g.jina[g.i]) else g.from
        yeolcha = ""
        silsiJal = false
        silsiMot = 0
        huboJikyeo = HashMap()
    }

    private val poller: Runnable = object : Runnable {
        override fun run() { if (!dolgo) return; silsi(); main.postDelayed(this, 20000) }
    }
    private val ticker: Runnable = object : Runnable {
        override fun run() { if (!dolgo) return; sigan(); main.postDelayed(this, 5000) }
    }

    private fun dolligi() {
        dolgo = true
        tabeumGamsi = false
        heundeullimSijak()
        main.removeCallbacks(poller)
        main.removeCallbacks(ticker)
        main.post(poller)
        main.postDelayed(ticker, 5000)
    }

    /** 한 역 지났을 때 */
    private fun hanYeok(malHam: Boolean = true) {
        if (!dolgo) return
        val g0 = gil ?: return
        if (g0.kkeutnam || g0.jina.isEmpty()) return
        val i = min(g0.i + 1, g0.jina.size - 1)
        var kkeutnam = false
        majimak = System.currentTimeMillis()
        val ji = ireum(g0.jina[i])
        val nam = g0.jina.size - 1 - i
        var mal: String
        val hj = hwanJa.indexOf(i)
        val hj2 = hwanJa.indexOf(i + 1)
        if (hj >= 0 && nam > 0 && hj + 1 < g0.gugan.size) {
            val dg = g0.gugan[hj + 1]
            hoseon = dg.hoseon
            kkeut = dg.bangmyeon
            from = ji
            yeolcha = ""
            silsiJal = false
            majimak = System.currentTimeMillis() + 180000   // 갈아타는 3분 동안은 세지 않음
            val bang = if (dg.bangmyeon.isEmpty()) "으로" else " ${dg.bangmyeon} 방면으로"
            mal = "${ji}역입니다. 여기서 내리셔서 ${dg.hoseon}$bang 갈아타십시오. 갈아탄 뒤에도 지나는 역을 이어서 알려 드립니다. 내리실 역까지 $nam 정거장 남았습니다."
        } else if (hj2 >= 0 && nam > 1 && hj2 + 1 < g0.gugan.size) {
            mal = "${ji}역입니다. 다음 ${ireum(g0.jina[i + 1])}역에서 내려 ${g0.gugan[hj2 + 1].hoseon}으로 갈아타십니다. 내리실 준비를 하십시오."
        } else if (nam <= 0) {
            kkeutnam = true
            mal = "${ji}역입니다. 내리십시오."
            if (g0.naeril.isNotEmpty()) mal += " 내리셔서 ${g0.naeril}로 나가시면 목적지가 가장 가깝습니다."
            mal += " 밖으로 나오시면 남은 길을 걸어서 안내합니다."
        } else if (nam == 1) {
            mal = "${ji}역입니다. 다음 역에서 내리십니다. 내리실 준비를 하십시오."
        } else if (nam == 2) {
            mal = "${ji}역입니다. 두 역 뒤에 내리십니다."
        } else {
            mal = "${ji}역입니다. 다음은 ${ireum(g0.jina[i + 1])}역입니다."
        }
        yj.jihaNoki(g0.copy(i = i, kkeutnam = kkeutnam))
        if (malHam) Sori.mal(mal)
        Girok.namgi("jiha_yeok", mapOf("i" to i, "nam" to nam))
        if (kkeutnam) {
            dolgo = false
            main.removeCallbacks(poller)
            main.removeCallbacks(ticker)
            gasokKkeugi()
        }
    }

    /** 2.73.0 (261024-A61, 이사장님 지시 2026-10-24, 아이폰 2.105.0과 같음) 「여기는 ○○역이야」 — 가는 길 위의 역이면 그 역으로 맞춤. 맞췄으면 알려 드릴 말, 길 위에 없으면 null */
    fun yeokMatchugi(ireum0: String): String? {
        val y = yj.jigeum ?: return null
        if (y.danggye != Danggye.TANEUN_JUNG) return null
        val g0 = y.jiha ?: return null
        if (g0.kkeutnam) return null
        val n = ireum(ireum0)
        val idx: Int = if (ireum(g0.from) == n) -1 else {
            val k = g0.jina.indexOfFirst { ireum(it) == n }
            if (k < 0) return null
            k
        }
        var g = g0.copy(i = idx)
        yj.jihaNoki(g)
        junbi(g)
        majimak = System.currentTimeMillis()
        if (!dolgo) dolligi()
        Girok.namgi("jiha_yeok_matchum", mapOf("yeok" to n, "i" to idx))
        val nam = g.jina.size - 1 - idx
        if (nam <= 0) {
            g = g.copy(kkeutnam = true)
            yj.jihaNoki(g)
            return "${n}역으로 맞췄습니다. 내리실 역입니다. 내리십시오."
        }
        return "${n}역으로 맞췄습니다. ${g.to}역까지 $nam 정거장 남았습니다." + " 다음은 ${ireum(g.jina[idx + 1])}역입니다."
    }

    /** 지금 몇 정거장 남았습니까 */
    fun hyeonhwang(): String {
        val y = yj.jigeum ?: return "지금은 지하철로 가고 있지 않습니다."
        val g = y.jiha ?: return "지금은 지하철로 가고 있지 않습니다."
        return when (y.danggye) {
            Danggye.TANEUN_GOT_KKAJI ->
                if (g.ipguDochak) "${g.ipgu.ireum}에 닿았습니다. 열차가 움직이면 저절로 역 알림을 시작합니다. ${g.to}역까지 ${g.jina.size} 정거장입니다."
                else "${g.ipgu.ireum}까지 걸어가는 중입니다. 타신 뒤 ${g.to}역까지 ${g.jina.size} 정거장입니다."
            Danggye.TANEUN_JUNG -> when {
                g.kkeutnam -> "${g.to}역에 닿았습니다. 밖으로 나오시면 남은 길을 걸어서 안내합니다."
                g.i < 0 -> "아직 첫 역을 지나지 않았습니다. ${g.to}역까지 ${g.jina.size} 정거장입니다."
                else -> "${ireum(g.jina[g.i])}역을 지났습니다. ${g.to}역까지 ${g.jina.size - 1 - g.i} 정거장 남았습니다."
            }
            else -> ""
        }
    }

    // ① 실시간 열차 위치
    private fun silsi() {
        if (!dolgo || silsiMutneunJung || majimak > System.currentTimeMillis() || hoseon.isEmpty()) return
        silsiMutneunJung = true
        val sd = sedae
        Tongsin.json("yeok.php", mapOf("a" to "silsi", "hoseon" to hoseon)) { o ->
            silsiMutneunJung = false
            if (sd != sedae) return@json
            val rows = ArrayList<org.json.JSONObject>()
            if (o != null && o.optBoolean("ok", false)) {
                val a = o.optJSONArray("rows")
                if (a != null) for (k in 0 until a.length()) a.optJSONObject(k)?.let { rows.add(it) }
            }
            silsiBatda(rows)
        }
    }

    private fun silsiBatda(rows: List<org.json.JSONObject>) {
        if (!dolgo) return
        val g = gil ?: return
        if (rows.isEmpty()) { silsiMot += 1; silsiJal = false; return }
        silsiMot = 0
        var nae: org.json.JSONObject? = null
        if (yeolcha.isNotEmpty()) {
            nae = rows.firstOrNull { Jeomjido.gul(it, "yeolcha") == yeolcha }
            if (nae == null) { yeolcha = ""; silsiJal = false }   // 붙잡았던 열차가 사라짐
        }
        if (nae == null) {
            // 2.9.0 지금 역(지난 역)과 바로 다음 역에 있는 열차만, 가는 방향이 맞는 것만(아이폰 2.40.0과 같음)
            val chatja = ArrayList<String>()
            chatja.add(ireum(if (g.i >= 0 && g.i < g.jina.size) g.jina[g.i] else from))
            if (g.i + 1 < g.jina.size) chatja.add(ireum(g.jina[g.i + 1]))
            val hubo = rows.filter { chatja.contains(ireum(Jeomjido.gul(it, "yeok"))) }
            if (kkeut.isNotEmpty()) nae = hubo.firstOrNull { ireum(Jeomjido.gul(it, "jong")) == ireum(kkeut) }
            // 방면 이름이 달리 오거나 모를 때 — 후보 열차를 지켜보다가 내 길을 따라 한 역 앞으로 나아간 열차만 붙잡음
            if (nae == null) {
                val sae = HashMap<String, Int>()
                for (r in rows) {
                    val id = Jeomjido.gul(r, "yeolcha")
                    if (id.isEmpty()) continue
                    val y = ireum(Jeomjido.gul(r, "yeok"))
                    val p = if (g.i < 0 && y == ireum(from)) -1 else g.jina.indexOfLast { ireum(it) == y }.let { if (it < 0) null else it } ?: continue
                    sae[id] = p
                    val ap = huboJikyeo[id]
                    if (nae == null && ap != null && p == ap + 1 && p <= g.i + 1) nae = r
                }
                huboJikyeo = sae
            }
            nae?.let {
                yeolcha = Jeomjido.gul(it, "yeolcha")
                Girok.namgi("jiha_yeolcha", mapOf("yeolcha" to yeolcha, "yeok" to Jeomjido.gul(it, "yeok")))
            }
        }
        val n = nae ?: return
        if (yeolcha.isEmpty()) return
        val yeok = ireum(Jeomjido.gul(n, "yeok"))
        val ja = g.jina.indexOfLast { ireum(it) == yeok }
        if (ja < 0) {
            Girok.namgi("jiha_yeolcha_noh", mapOf("kkadak" to "길 밖", "yeok" to yeok))
            yeolcha = ""; silsiJal = false
            return
        }
        // 지나간 시간에 비해 너무 많이 앞서 가면 엉뚱한 열차(역 사이 최소 1분 반)
        val heoyong = 1 + ((System.currentTimeMillis() - majimak) / 90000).toInt()
        if (ja - g.i > heoyong) {
            Girok.namgi("jiha_yeolcha_noh", mapOf("kkadak" to "너무 앞섬", "ap" to (ja - g.i)))
            yeolcha = ""; silsiJal = false
            return
        }
        if (ja >= g.i) silsiHwagin = System.currentTimeMillis()
        var bon = 0
        // 실시간으로 여러 역을 따라잡을 때는 조용히 넘기고 마지막 역(과 갈아타는 역)만 말함
        while ((gil?.i ?: ja) < ja && !(gil?.kkeutnam ?: true) && bon < 12) {
            val daeum = (gil?.i ?: ja) + 1
            val galaTa = hwanJa.contains(daeum)
            hanYeok(daeum >= ja || galaTa)
            bon += 1
            if (galaTa || !dolgo) break
        }
    }

    // ③ 시간으로 셈하기
    private fun sigan() {
        // 2.9.0 땅 위로 나와 걸으시거나 위성이 다시 잡히면 지하철 안내를 마치고 걷는 안내로
        val g0 = gil
        if (dolgo && g0 != null && !g0.kkeutnam && System.currentTimeMillis() - tamTtae > 120000 &&
            !TalgeotGamji.jiha && TalgeotGamji.chujeong == Talgeot.GEOREUM && TalgeotGamji.wiseongJoeum &&
            (!ieoGamTamMoreum || TalgeotGamji.georeum15cho)) {   // 2.32.0 앱을 다시 켜 탄 때를 모르면 위성만으로 마치지 않고 15초 넘게 걸으셔야
            Girok.namgi("jiha_kkeut_jisang", mapOf("i" to g0.i))
            meomchugi()
            yj.jihaNoki(g0.copy(kkeutnam = true))
            AnnaeEngine.naeryeotda(true, "땅 위로 나오신 것 같습니다. 지하철 안내를 마치고 남은 길을 걸어서 안내합니다.")
            return
        }
        if (!dolgo || silsiJal) return
        val g = gil ?: return
        if (g.kkeutnam) return
        val teom = if (g.jina.isEmpty()) 130.0 else max(60.0, (g.bun * 60).toDouble() / g.jina.size)
        if ((System.currentTimeMillis() - majimak) / 1000.0 > teom * 1.35) hanYeok()
    }

    // ② 폰 흔들림 — 섰다 떠나기 세기, 탄 것 알아채기
    private fun heundeullimSijak() {
        if (gasokKyeojim) return
        val s = sm ?: return
        val a = s.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        chang.clear()
        dallim = false
        seonTtae = 0L
        gasokKyeojim = s.registerListener(this, a, 50000, main)   // 0.05초마다(아이폰과 같음)
    }

    private fun gasokKkeugi() {
        if (!gasokKyeojim) return
        sm?.unregisterListener(this)
        gasokKyeojim = false
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    override fun onSensorChanged(e: SensorEvent) {
        if (e.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = e.values[0].toDouble()
        val y = e.values[1].toDouble()
        val z = e.values[2].toDouble()
        heundeullim(sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH)
    }

    private fun heundeullim(k: Double) {
        chang.add(k)
        if (chang.size > 40) chang.removeAt(0)
        if (chang.size < 30) return
        val p = chang.sum() / chang.size
        val pc = sqrt(chang.sumOf { (it - p) * (it - p) } / chang.size)
        val jigeumDallim = pc > 0.056
        val now = System.currentTimeMillis()
        if (dallim && !jigeumDallim) {
            seonTtae = now
            dallim = false
            dallimSijak = 0L
        } else if (!dallim && jigeumDallim) {
            dallim = true
            dallimSijak = now
            dallimGeoreum = Wichi.georeumSu
            if (dolgo && seonTtae > 0) {
                val t = (now - seonTtae) / 1000.0
                if (t > 8 && t < 120 && !silsiJal && now - majimak > 40000) hanYeok()
            }
            seonTtae = 0L
        }
        // 타는 역에 닿은 뒤 — 흔들리며 움직이는데 걸음이 없으면 열차에 탄 것(긴 에스컬레이터를 열차로 잘못 알지 않게 30초)
        if (!dolgo && tabeumGamsi && dallim && dallimSijak > 0 && now - dallimSijak >= 30000 &&
            Wichi.georeumSu - dallimGeoreum <= 3) {
            tabeumGamsi = false
            tatda(true)
        }
    }
}

/** 지하철로 가기 — 가까운 역, 갈아타기, 들어갈 출구와 나갈 출구를 찾아 한 번에 보여 드리고, 누르면 역까지 걷는 안내를 시작 */
class JihacheolGilHwamyeon(private val mok: Jangso) : Hwamyeon("지하철로 가기") {
    private var gil: JihaGil? = null
    private var kkadak = ""
    private var chatneunJung = false
    private var batam = false
    private var t0: GilnunActivity? = null
    private var cheot: View? = null
    private var chojeomHal = false
    private var daeumYeolcha = ""   // 2.62.0 다음 열차
    private var yeolchaDan: android.widget.Button? = null
    private fun yeolchaGeul() = if (daeumYeolcha.isEmpty()) "다음 열차 알아보기" else "$daeumYeolcha 다시 알아보기"

    override fun chaeugi(t: GilnunActivity) {
        t0 = t
        cheot = null
        if (!batam && !chatneunJung) chatgi()
        val g = gil
        if (chatneunJung) {
            t.geul("${mok.ireum}까지 지하철 길을 찾는 중입니다.", true)
        } else if (g != null) {
            cheot = t.danchu("${g.mal} 들어갈 곳은 ${g.ipgu.ireum}입니다 — 이 길로 가기, 역까지 걷는 안내 시작") {
                Jeulgyeo.sseum(mok)
                AnnaeEngine.jihacheolGagi(mok, g)
                t.cheotHwamyeonEuro(null)
            }
            t.danchu("이미 열차에 탔습니다 — 곧장 역 알림 시작") {
                Jeulgyeo.sseum(mok)
                AnnaeEngine.jihacheolGagi(mok, g)
                JihacheolEngine.tatda(false)
                t.cheotHwamyeonEuro(null)
            }
            // 2.62.0 (261017-A50, 이사장님 승인 2026-10-17) 다음 열차가 몇 분 뒤에 오는지(웹 길눈에서 옮김, 아이폰과 같음)
            yeolchaDan = t.danchu(yeolchaGeul()) {
                JihacheolEngine.daeumYeolchaMal(g) { m ->
                    daeumYeolcha = m ?: ""
                    yeolchaDan?.text = yeolchaGeul()
                    Sori.mal(m ?: "지금은 열차 도착 정보를 받지 못했습니다. 서울 지하철만 알려 드릴 수 있습니다.")
                }
            }
        } else {
            cheot = t.danchu("$kkadak — 걸어가기로 안내 시작") {
                AnnaeEngine.georeoGagi(mok)
                t.cheotHwamyeonEuro(null)
            }
            t.danchu("다시 찾기") { chatgi(); t.dasiGeurigi() }
        }
    }

    override fun boilttae(t: GilnunActivity) {
        if (!chojeomHal) return
        chojeomHal = false
        cheot?.let { t.chojeomOmgigi(it) }
    }

    private fun chatgi() {
        chatneunJung = true
        JihacheolEngine.gilChatgi(mok) { g, k ->
            gil = g
            kkadak = if (k == JihacheolEngine.TAN_YEOK_MUREUM) JihacheolEngine.TAN_YEOK_MAL else k   // 2.73.0 (261024-A61)
            chatneunJung = false
            batam = true
            if (g == null) Sori.mal(kkadak)   // 찾은 길은 단추 글자로 읽힘 — 못 찾은 까닭만 한 번 말함
            if (g != null) JihacheolEngine.daeumYeolchaMal(g) { m -> daeumYeolcha = m ?: ""; yeolchaDan?.text = yeolchaGeul() }   // 2.62.0 그 자리에서 글자만 바꿈(커서가 흔들리지 않게)
            val t = t0 ?: return@gilChatgi
            chojeomHal = true
            if (t.wiHwamyeon === this) t.dasiGeurigi()
        }
    }
}
