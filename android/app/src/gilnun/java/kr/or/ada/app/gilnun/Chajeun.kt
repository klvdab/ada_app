// 요즘 찾으신 곳·자동 입력·교통편·섬 — 2.63.0 (261017-A51, 이사장님 승인 2026-10-17 「그러자」, 아이폰 2.93.0 Chajeun.swift·Seom.swift 와 같음)
// 웹 길눈 yeojeong.js(2026-09-14 「요즘 찾으신 곳」)·jachu.js 1.1(자동 입력)·gyotong.html 0.2(교통편)·seom.js 0.7(섬·때·지하철 고장)을 앱으로 옮김.
package kr.or.ada.app.gilnun

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import java.util.Locale

data class ChajeunHang(val ireum: String, val juso: String, val lat: Double, val lon: Double, val ttae: Long, val beon: Int) {
    val jangso: Jangso get() = Jangso(ireum, juso, lat, lon)
}

object Chajeun {
    private var pail: File? = null
    var mokrok: List<ChajeunHang> = emptyList()
        private set

    fun sijak(c: Context) {
        if (pail != null) return
        val f = File(c.applicationContext.filesDir, "chajeun.json")
        pail = f
        try {
            if (f.exists()) {
                val a = JSONArray(f.readText()); val l = ArrayList<ChajeunHang>()
                for (i in 0 until a.length()) {
                    val o = a.optJSONObject(i) ?: continue
                    l.add(ChajeunHang(o.optString("ireum"), o.optString("juso"), o.optDouble("lat"), o.optDouble("lon"), o.optLong("ttae"), o.optInt("beon", 1)))
                }
                mokrok = l
            }
        } catch (e: Exception) {}
    }

    fun damgi(m: Mokjeok) {
        if (m.ireum.isEmpty()) return
        val ijeon = mokrok.firstOrNull { it.ireum == m.ireum }
        val beon = (ijeon?.beon ?: 0) + 1
        mokrok = (listOf(ChajeunHang(m.ireum, m.juso, m.lat, m.lon, System.currentTimeMillis(), beon)) + mokrok.filter { it.ireum != m.ireum }).take(30)
        jeojang()
    }

    fun jiugi() { mokrok = emptyList(); jeojang() }

    private fun jeojang() {
        val f = pail ?: return
        try {
            val a = JSONArray()
            for (h in mokrok) a.put(JSONObject().put("ireum", h.ireum).put("juso", h.juso).put("lat", h.lat).put("lon", h.lon).put("ttae", h.ttae).put("beon", h.beon))
            f.writeText(a.toString())
        } catch (e: Exception) {}
    }

    // MARK: 자동 입력 — 웹 jachu.js 1.1

    data class Hubo(val ireum: String, val j: Jangso, val jeul: Boolean, val jom: Int, val ttae: Long)

    private const val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    fun chosung(s: String): String = buildString { for (c in s) { if (c.code in 0xAC00..0xD7A3) append(CHO[(c.code - 0xAC00) / 588]) else append(c) } }
    private fun choman(s: String) = s.isNotEmpty() && s.all { it.code in 0x3131..0x314E }
    private fun ppae(s: String) = s.replace(" ", "")

    fun goreugi(mal: String): List<Hubo> {
        val q = ppae(mal)
        if (q.isEmpty()) return emptyList()
        val cho = choman(q)
        val bon = HashSet<String>(); val out = ArrayList<Hubo>()
        fun bogi(ireum: String, geul: String, j: Jangso, jeul: Boolean, ttae: Long, beon: Int) {
            if (ireum.isEmpty() || !bon.add(ireum)) return
            val s = ppae(geul)
            var jom = -1
            if (cho) { val i = chosung(s).indexOf(q); if (i >= 0) jom = (if (i == 0) 100 else 60) - i }
            else {
                val i = s.indexOf(q)
                if (i >= 0) jom = (if (i == 0) 100 else 60) - i
                else if (q.length >= 2) { val k = chosung(s).indexOf(chosung(q)); if (k >= 0) jom = 40 - k }
            }
            if (jom >= 0) out.add(Hubo(ireum, j, jeul, jom + (if (jeul) 8 else 0) + Math.min(beon, 5), ttae))
        }
        for (c in mokrok) bogi(c.ireum, c.ireum, c.jangso, false, c.ttae, c.beon)
        for (h in Jeulgyeo.mokrok) bogi(h.ireum, h.ireum + " " + h.juso, h.jangso, true, h.ttae, 1)
        return out.sortedWith(compareByDescending<Hubo> { it.jom }.thenByDescending { it.ttae }).take(3)
    }

    /** 엔터 때 갈 곳 — 딱 맞는 이름이면 그것, 역·정류장·터미널로 끝나면 적으신 그대로 찾기(null), 그 밖에는 맨 위 후보 */
    fun wi(mal: String): Hubo? {
        val l = goreugi(mal)
        if (l.isEmpty()) return null
        val q = ppae(mal)
        l.firstOrNull { ppae(it.ireum) == q }?.let { return it }
        if (q.endsWith("역") || q.endsWith("정류장") || q.endsWith("터미널")) return null
        return l[0]
    }

    /** 찾기 칸 바로 아래 후보 세 줄 — 화면을 다시 그리지 않고 그 자리에서 바꿈(커서가 흔들리지 않게) */
    fun huboDalgi(t: GilnunActivity, e: android.widget.EditText) {
        val dan = List(3) { t.danchu("") {}.apply { visibility = View.GONE } }
        var nogeum = ""
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        var malR: Runnable? = null
        fun saero(v: String) {
            val l = goreugi(v)
            for (i in 0 until 3) {
                val b = dan[i]
                if (i < l.size) {
                    val h = l[i]
                    b.text = h.ireum + if (h.jeul) " — 즐겨찾기" else " — 요즘 찾으신 곳"
                    b.setOnClickListener { t.yeolgi(MokjeokHwamyeon(h.j)) }
                    b.visibility = View.VISIBLE
                } else b.visibility = View.GONE
            }
            val wi = wi(v)?.ireum ?: ""
            if (wi != nogeum) {
                nogeum = wi
                malR?.let { main.removeCallbacks(it) }
                if (wi.isNotEmpty()) { val r = Runnable { Sori.mal(wi, MalGeup.JEONGBO) }; malR = r; main.postDelayed(r, 400) }
            }
        }
        e.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { saero(s?.toString() ?: "") }
        })
        saero(e.text?.toString() ?: "")
    }
}

/** 요즘 찾으신 곳 — 다섯씩, 몇 번째인지, 이 목록 지우기 */
class ChajeunHwamyeon : Hwamyeon("요즘 찾으신 곳") {
    private var sijak = 0
    private var jiuneun = false
    private var chojeomHal = false
    private var cheot: View? = null

    override fun chaeugi(t: GilnunActivity) {
        cheot = null
        val l = Chajeun.mokrok
        if (l.isEmpty()) {
            t.geul("아직 찾으신 곳이 없습니다. 목적지를 정하실 때마다 여기에 적어 둡니다.", true)
        } else {
            val sj = if (sijak >= l.size) ((l.size - 1) / 5) * 5 else sijak
            sijak = sj
            val kkeut = minOf(sj + 5, l.size)
            for (n in sj until kkeut) {
                val h = l[n]
                val b = t.danchu(h.ireum + (if (h.beon > 1) " — ${h.beon}번째" else "") + (if (h.juso.isNotEmpty()) " — ${h.juso}" else "")) { t.yeolgi(MokjeokHwamyeon(h.jangso)) }
                if (cheot == null) cheot = b
            }
            if (kkeut < l.size) t.danchu("더 보기") { sijak = sj + 5; chojeomHal = true; t.dasiGeurigi() }
            if (sj > 0) t.danchu("이전 보기") { sijak = maxOf(0, sj - 5); chojeomHal = true; t.dasiGeurigi() }
            if (jiuneun) {
                t.danchu("예, 모두 지웁니다") { Chajeun.jiugi(); jiuneun = false; Sori.mal("요즘 찾으신 곳을 모두 지웠습니다."); t.dasiGeurigi() }
                t.danchu("아니요, 그대로 둡니다") { jiuneun = false; t.dasiGeurigi() }
            } else t.danchu("이 목록 지우기") { jiuneun = true; Sori.mal("요즘 찾으신 곳을 모두 지울까요? 바로 아래에서 고르십시오."); t.dasiGeurigi() }
        }
        t.geul("서른 곳까지 이 폰 안에만 담기고 밖으로 나가지 않습니다.")
    }

    override fun boilttae(t: GilnunActivity) {
        if (!chojeomHal) return
        chojeomHal = false
        cheot?.let { t.chojeomOmgigi(it) }
    }
}

// MARK: 섬·때·지하철 고장 — 웹 seom.js 0.7

data class SeomGot(val ireum: String, val lat: Double, val lon: Double)
data class SeomJeongbo(val ireum: String, val la1: Double, val la2: Double, val lo1: Double, val lo2: Double, val bihaeng: Boolean, val bae: List<String>, val mal: String,
                       val gyeongyu: String? = null, val baeGeochyeo: List<String>? = null, val malGeochyeo: String? = null)

object Seom {
    val gonghang = listOf(
        SeomGot("인천국제공항", 37.4602, 126.4407), SeomGot("김포공항", 37.5583, 126.7906), SeomGot("김해국제공항", 35.1795, 128.9382),
        SeomGot("대구국제공항", 35.8940, 128.6586), SeomGot("청주국제공항", 36.7166, 127.4991), SeomGot("무안국제공항", 34.9914, 126.3828),
        SeomGot("광주공항", 35.1264, 126.8089), SeomGot("여수공항", 34.8423, 127.6169), SeomGot("사천공항", 35.0886, 128.0704),
        SeomGot("울산공항", 35.5936, 129.3517), SeomGot("포항경주공항", 35.9878, 129.4204), SeomGot("군산공항", 35.9038, 126.6158),
        SeomGot("원주공항", 37.4381, 127.9600), SeomGot("양양국제공항", 38.0613, 128.6690)
    )
    val hanggu = mapOf(
        "incheon" to SeomGot("인천 연안여객터미널", 37.4517, 126.5977), "mokpo" to SeomGot("목포 연안여객선터미널", 34.7856, 126.3822),
        "wando" to SeomGot("완도항 여객선터미널", 34.3117, 126.7550), "yeosu" to SeomGot("여수 연안여객선터미널", 34.7378, 127.7460),
        "nokdong" to SeomGot("고흥 녹동신항 여객선터미널", 34.5289, 127.1289), "busan" to SeomGot("부산항 연안여객터미널", 35.0996, 129.0400),
        "pohang" to SeomGot("포항 여객선터미널", 36.0424, 129.3897), "gangneung" to SeomGot("강릉항 여객터미널", 37.7736, 128.9503),
        "mukho" to SeomGot("묵호항 여객선터미널", 37.5486, 129.1155), "hupo" to SeomGot("후포항 여객선터미널", 36.6790, 129.4576),
        "tongyeong" to SeomGot("통영항 여객선터미널", 34.8412, 128.4266), "gyeokpo" to SeomGot("부안 격포항 여객선터미널", 35.6300, 126.4650),
        "gunsan" to SeomGot("군산 연안여객선터미널", 35.9857, 126.7128), "jejuhang" to SeomGot("제주항 연안여객터미널", 33.5197, 126.5424),
        "uldodong" to SeomGot("울릉 도동항", 37.4870, 130.9046)
    )
    val seomdeul = listOf(
        SeomJeongbo("제주도", 33.05, 33.65, 126.05, 127.00, true, listOf("mokpo", "wando", "yeosu", "nokdong", "busan"), "제주는 육지에서 이어진 길이 없습니다. 비행기나 배로 가셔야 합니다."),
        SeomJeongbo("추자도", 33.88, 34.02, 126.24, 126.42, false, listOf("wando", "jejuhang", "mokpo"), "추자도는 배로만 갈 수 있습니다."),
        SeomJeongbo("울릉도", 37.40, 37.62, 130.70, 131.00, false, listOf("pohang", "gangneung", "mukho", "hupo"), "울릉도는 배로만 갈 수 있습니다. 울릉공항은 아직 문을 열지 않았습니다."),
        SeomJeongbo("독도", 37.20, 37.30, 131.80, 131.95, false, listOf("pohang", "gangneung", "mukho", "hupo"), "독도는 울릉도까지 배로 가신 뒤 다시 배를 타셔야 합니다.",
            "울릉도", listOf("uldodong"), "울릉도에 계십니다. 독도로 가는 배는 도동항에서 뜹니다. 날씨가 궂으면 배가 뜨고도 독도에 내리지 못하는 날이 있습니다."),
        SeomJeongbo("백령도와 대청도", 37.68, 38.02, 124.50, 124.95, false, listOf("incheon"), "백령도와 대청도는 배로만 갈 수 있습니다."),
        SeomJeongbo("연평도", 37.58, 37.70, 125.60, 125.80, false, listOf("incheon"), "연평도는 배로만 갈 수 있습니다."),
        SeomJeongbo("인천 앞바다 섬", 37.00, 37.30, 125.95, 126.35, false, listOf("incheon"), "이곳은 배로만 갈 수 있습니다."),
        SeomJeongbo("흑산도와 홍도", 34.00, 34.80, 125.00, 125.55, false, listOf("mokpo"), "이곳은 배로만 갈 수 있습니다."),
        SeomJeongbo("거문도", 34.00, 34.15, 127.25, 127.40, false, listOf("yeosu"), "거문도는 배로만 갈 수 있습니다."),
        SeomJeongbo("청산도", 34.14, 34.25, 126.78, 126.95, false, listOf("wando"), "청산도는 배로만 갈 수 있습니다."),
        SeomJeongbo("욕지도와 그 둘레 섬", 34.55, 34.72, 128.15, 128.45, false, listOf("tongyeong"), "이곳은 배로만 갈 수 있습니다."),
        SeomJeongbo("위도", 35.55, 35.70, 126.22, 126.38, false, listOf("gyeokpo"), "위도는 배로만 갈 수 있습니다."),
        SeomJeongbo("어청도", 36.00, 36.20, 125.90, 126.15, false, listOf("gunsan"), "어청도는 배로만 갈 수 있습니다.")
    )
    fun jarilo(lat: Double, lon: Double) = seomdeul.firstOrNull { lat >= it.la1 && lat <= it.la2 && lon >= it.lo1 && lon <= it.lo2 }
    fun chatgi(j: Jangso): SeomJeongbo? {
        jarilo(j.lat, j.lon)?.let { return it }
        val t = j.ireum + " " + j.juso
        if (listOf("제주특별자치도", "제주시", "서귀포").any { t.contains(it) }) return seomdeul[0]
        if (listOf("울릉군", "울릉읍", "울릉도").any { t.contains(it) }) return seomdeul[2]
        return null
    }
    fun anIn(s: SeomJeongbo, w: Jari?): Boolean = w != null && jarilo(w.lat, w.lon)?.ireum == s.ireum
    fun geochyeoAnIn(s: SeomJeongbo, w: Jari?): Boolean = s.gyeongyu != null && w != null && jarilo(w.lat, w.lon)?.ireum == s.gyeongyu
    fun malGoreugi(s: SeomJeongbo, w: Jari?) = if (geochyeoAnIn(s, w)) (s.malGeochyeo ?: s.mal) else s.mal
    fun taneungot(s: SeomJeongbo, w: Jari?, bihaeng: Boolean): List<Pair<SeomGot, Double>> {
        val mok = if (bihaeng) (if (s.bihaeng) gonghang else emptyList())
                  else ((if (geochyeoAnIn(s, w)) s.baeGeochyeo else null) ?: s.bae).mapNotNull { hanggu[it] }
        val l = mok.map { g -> Pair(g, if (w != null) Wichi.geori(w.lat, w.lon, g.lat, g.lon) else 0.0) }
        return if (w == null) l else l.sortedBy { it.second }
    }
    fun taneungosinga(ireum: String): String? {
        val t = ireum.trim()
        if (gonghang.any { it.ireum == t } || t == "제주국제공항" || t.endsWith("공항")) return "bihaenggi"
        if (hanggu.values.any { it.ireum == t } || t.contains("여객터미널") || t.contains("여객선터미널") || t.contains("선착장")) return "bae"
        return null
    }
    val chaenggim = mapOf(
        "bihaenggi" to listOf(
            "표를 끊으실 때 항공사에 시각장애가 있다고 미리 알리십시오. 저비용 항공사는 대개 출발 마흔여덟 시간 전까지, 아시아나는 출발 이레 전까지 예약센터로 신청하도록 하고 있습니다. 당일 공항에서 말씀하시면 도움이 늦어질 수 있습니다.",
            "공항에서는 체크인 카운터까지 안내를 받으실 수 있습니다. 카운터에서 탑승구까지는 항공사에 따로 요청하셔야 합니다."),
        "bae" to listOf(
            "표를 끊으실 때 선사에 시각장애가 있다고 미리 알리시는 것이 좋습니다.",
            "배를 타실 때는 승선 신고서를 쓰고 신분증을 보여야 합니다. 신분증을 꼭 챙기십시오.",
            "바람이 세면 배가 뜨지 않습니다. 나서시기 전에 여객터미널이나 선사에 전화로 뜨는지 확인하십시오.")
    )
    const val incheonAnsim = "인천공항은 제1여객터미널 3층 가운데에 장애인 안심여행센터가 있습니다. 나가시는 분만 쓰실 수 있고 복지카드를 지니셔야 합니다. 비행기 뜨기 세 시간 전까지 예약하시면 안심여행센터에서 체크인 카운터까지 전동차로 모셔다 드립니다. 전화는 아침 일곱 시부터 저녁 일곱 시까지 받습니다. 전화번호는 공 삼 이, 칠 사 일, 이 공 팔 이입니다."
    private val jicheol = listOf(
        doubleArrayOf(37.00, 38.20, 126.20, 127.60), doubleArrayOf(36.72, 36.98, 126.85, 127.28), doubleArrayOf(37.78, 37.96, 127.58, 127.86),
        doubleArrayOf(35.03, 35.45, 128.72, 129.32), doubleArrayOf(35.74, 36.06, 128.32, 128.88), doubleArrayOf(35.07, 35.27, 126.72, 126.98), doubleArrayOf(36.23, 36.47, 127.26, 127.52)
    )
    fun jicheolItna(lat: Double, lon: Double) = jicheol.any { lat >= it[0] && lat <= it[1] && lon >= it[2] && lon <= it[3] }
    private val ttae = mapOf("jicheol" to Pair(330, 1440), "beoseu" to Pair(300, 1410), "sioe" to Pair(360, 1350), "gicha" to Pair(300, 1380), "bihaeng" to Pair(360, 1290), "bae" to Pair(420, 1080))
    fun ttaeKkori(jong: String): String {
        val t = ttae[jong] ?: return ""
        val c = Calendar.getInstance()
        val bun = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        if (bun < t.first || bun >= t.second) return " — 지금은 끊겼을 수 있습니다"
        if (t.second - bun <= 60) return " — 막차가 곧 끊깁니다"
        return ""
    }
    /** 그곳 바람(오픈메테오, 열쇠 없음) */
    fun baramMal(lat: Double, lon: Double, kkeut: (String?) -> Unit) {
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        Thread {
            var m: String? = null
            try {
                val u = URL(String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=wind_speed_10m&wind_speed_unit=ms", lat, lon))
                val c = u.openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                val b = Math.round(o.getJSONObject("current").getDouble("wind_speed_10m")).toInt()
                m = when {
                    b >= 12 -> "지금 그곳 바람이 초속 ${b}미터입니다. 배가 뜨지 않을 수 있으니 나서시기 전에 꼭 전화로 확인하십시오."
                    b >= 8 -> "지금 그곳 바람이 초속 ${b}미터입니다. 배가 흔들릴 만한 바람이니 미리 확인하시는 것이 좋습니다."
                    else -> "지금 그곳 바람은 초속 ${b}미터입니다."
                }
            } catch (e: Exception) {}
            val r = m
            main.post { kkeut(r) }
        }.start()
    }
}

// MARK: 교통편 — 웹 gyotong.html 0.2

class GyotongHwamyeon : Hwamyeon("교통편") {
    private data class Yeok(val id: String, val ireum: String, val dosi: String)
    private val gal = listOf("gicha" to "기차", "gosok" to "고속버스", "sioe" to "시외버스", "bae" to "여객선")
    private var dan = 0
    private var g = ""
    private val junbi = HashMap<String, Boolean>()
    private var junbiBat = false
    private val modu = HashMap<String, List<Yeok>>()
    private var q = ""
    private var chatEun: List<Yeok> = emptyList()
    private var chul: Yeok? = null
    private var doe: Yeok? = null
    private var baeDo: List<Yeok> = emptyList()
    private var nal = ""
    private var nalMal = ""
    private var pyeon: List<String> = emptyList()
    private var sijak = 0
    private var allim = "무엇을 타시겠습니까."
    private var t0: GilnunActivity? = null
    private var chojeomHal = false
    private var cheot: View? = null

    private val galIreum get() = gal.firstOrNull { it.first == g }?.second ?: ""
    private val chulMal get() = if (g == "bae") "출항지" else if (g == "gicha") "출발역" else "출발 터미널"
    private val doMal get() = if (g == "bae") "도착지" else if (g == "gicha") "도착역" else "도착 터미널"

    private fun mal(t: String) { allim = t; Sori.mal(t) }
    private fun dasi() { val t = t0 ?: return; chojeomHal = true; if (t.wiHwamyeon === this) t.dasiGeurigi() }
    private fun dan1(t: GilnunActivity, geul: String, f: () -> Unit): Button { val b = t.danchu(geul, f); if (cheot == null) cheot = b; return b }

    override fun chaeugi(t: GilnunActivity) {
        t0 = t; cheot = null
        if (!junbiBat) { junbiBat = true; sangtaeBatgi() }
        t.geul(allim, true)
        when (dan) {
            0 -> {
                for ((k, n) in gal) dan1(t, n + (if (junbi[k] == false) " (지금은 안 됩니다)" else "") + Seom.ttaeKkori(if (k == "gosok") "sioe" else k)) { galGoreugi(k) }
                t.geul("시내버스는 목적지를 고른 화면의 버스로 가기에서 찾습니다.")
            }
            1, 2 -> {
                if (dan == 2 && g == "bae") {
                    mokrok5(t, baeDo.map { it.ireum }) { i -> doe = baeDo[i]; nalGoreugi() }
                } else {
                    val e = t.ipryeok((if (dan == 1) chulMal else doMal) + " 이름으로 찾기 — 예: " + (if (g == "bae") "목포" else if (dan == 1) "서울" else "부산"), false)
                    e.setText(q)
                    if (cheot == null) cheot = e
                    e.imeOptions = EditorInfo.IME_ACTION_SEARCH
                    e.addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun afterTextChanged(s: android.text.Editable?) { q = s?.toString() ?: "" }
                    })
                    e.setOnEditorActionListener { _, _, _ -> ireumChatgi(); true }
                    t.danchu("이 이름으로 찾기") { ireumChatgi() }
                    mokrok5(t, chatEun.map { it.ireum + (if (it.dosi.isNotEmpty()) " — ${it.dosi}" else "") }) { i ->
                        if (dan == 1) { chul = chatEun[i]; doGoreugi() } else { doe = chatEun[i]; nalGoreugi() }
                    }
                }
                if (dan == 2) t.danchu("$chulMal 다시 고르기") { chulGoreugi() }
            }
            3 -> {
                for ((n, d) in listOf("오늘" to 0, "내일" to 1, "모레" to 2)) {
                    val nt = nalTtae(d)
                    dan1(t, "$n ${nt.second}") { nal = nt.first; nalMal = "$n ${nt.second}"; pyeonBogi() }
                }
                t.danchu("$doMal 다시 고르기") { doGoreugi() }
            }
            else -> {
                mokrok5(t, pyeon) { i -> Sori.mal(pyeon[i]) }
                yemae(t)
                t.danchu("다른 날 고르기") { nalGoreugi() }
            }
        }
        if (dan > 0) t.danchu("처음부터 — 탈것 다시 고르기") { dan = 0; g = ""; chul = null; doe = null; chatEun = emptyList(); q = ""; sijak = 0; mal("무엇을 타시겠습니까. 기차, 고속버스, 시외버스, 여객선 가운데 고르십시오."); dasi() }
    }

    override fun boilttae(t: GilnunActivity) {
        if (!chojeomHal) return
        chojeomHal = false
        cheot?.let { t.chojeomOmgigi(it) }
    }

    private fun mokrok5(t: GilnunActivity, l: List<String>, f: (Int) -> Unit) {
        if (l.isEmpty()) return
        val sj = if (sijak >= l.size) ((l.size - 1) / 5) * 5 else sijak
        val kkeut = minOf(sj + 5, l.size)
        for (i in sj until kkeut) dan1(t, l[i]) { f(i) }
        if (kkeut < l.size) t.danchu("더 보기") { sijak = sj + 5; dasi() }
        if (sj > 0) t.danchu("이전 보기") { sijak = maxOf(0, sj - 5); dasi() }
    }

    private fun yemae(t: GilnunActivity) {
        val (geul, u) = when (g) {
            "gicha" -> Pair("코레일에서 이 구간 예매하기 — 출발역과 도착역이 채워진 채 열립니다. 날짜와 열차를 고르고 로그인해 결제하십시오",
                "https://www.korail.com/ticket/search/general?txtGoStart=" + URLEncoder.encode(chul?.ireum ?: "", "UTF-8") + "&txtGoEnd=" + URLEncoder.encode(doe?.ireum ?: "", "UTF-8"))
            "gosok" -> Pair("고속버스 예매 누리집 열기 — 코버스", "https://www.kobus.co.kr/main.do")
            "sioe" -> Pair("시외버스 예매 누리집 열기 — 버스타고", "https://www.bustago.or.kr/")
            "bae" -> Pair("여객선 예매 누리집 열기 — 가보고싶은섬", "https://island.haewoon.co.kr/")
            else -> return
        }
        t.danchu(geul) { try { t.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) } catch (e: Exception) {} }
    }

    private fun nalTtae(d: Int): Pair<String, String> {
        val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_MONTH, d)
        val y = c.get(Calendar.YEAR); val m = c.get(Calendar.MONTH) + 1; val dd = c.get(Calendar.DAY_OF_MONTH)
        return Pair(String.format(Locale.US, "%04d%02d%02d", y, m, dd), "${m}월 ${dd}일")
    }

    private fun sangtaeBatgi() {
        Tongsin.json("gyotong.php", mapOf("a" to "sangtae")) { o ->
            val rows = o?.optJSONArray("rows")
            if (rows != null) for (i in 0 until rows.length()) { val r = rows.optJSONObject(i) ?: continue; junbi[r.optString("gal")] = r.optBoolean("junbi", false) }
            mal("무엇을 타시겠습니까. 기차, 고속버스, 시외버스, 여객선 가운데 고르십시오. 시내버스는 버스로 가기에서 찾습니다.")
            dasi()
        }
    }

    private fun galGoreugi(k: String) {
        if (junbi[k] == false) { mal("${gal.first { it.first == k }.second}는 지금 창구가 준비되지 않았습니다."); return }
        g = k; chulGoreugi()
    }

    private fun moduBatgi(kkeut: (List<Yeok>) -> Unit) {
        modu[g]?.let { kkeut(it); return }
        mal("$galIreum 역 목록을 받는 중입니다.")
        val gg = g
        Tongsin.json("gyotong.php", mapOf("a" to "modu", "gal" to gg), 15000) { o ->
            val rows = o?.optJSONArray("rows"); val l = ArrayList<Yeok>()
            if (rows != null) for (i in 0 until rows.length()) { val r = rows.optJSONObject(i) ?: continue; if (r.optString("id").isNotEmpty()) l.add(Yeok(r.optString("id"), r.optString("ireum"), r.optString("dosi"))) }
            if (l.isEmpty()) { mal("목록을 받지 못했습니다. 처음부터 다시 해 주십시오."); return@json }
            modu[gg] = l
            kkeut(l)
        }
    }

    private fun chulGoreugi() {
        dan = 1; q = ""; chatEun = emptyList(); sijak = 0
        moduBatgi { mal("$galIreum $chulMal 이름을 적고 엔터를 치십시오."); dasi() }
    }

    private fun ireumChatgi() {
        val k = q.replace(" ", "")
        val rows = modu[g]
        if (k.isEmpty() || rows == null) { mal("이름을 적어 주십시오."); return }
        val a = rows.filter { it.ireum.replace(" ", "").startsWith(k) }
        val b = rows.filter { val n = it.ireum.replace(" ", ""); (!n.startsWith(k) && n.contains(k)) || it.dosi.contains(k) }
        var f = (a + b).take(40)
        val c = chul
        if (dan == 2 && c != null) f = f.filter { it.id != c.id }
        chatEun = f; sijak = 0
        val m = if (dan == 1) chulMal else doMal
        mal(if (f.isEmpty()) "그런 이름의 ${m}을 찾지 못했습니다. 다른 이름으로 적어 보십시오." else "${f.size}곳을 찾았습니다. 이름을 누르시면 ${m}로 정해집니다.")
        dasi()
    }

    private fun doGoreugi() {
        dan = 2; q = ""; chatEun = emptyList(); sijak = 0
        val c = chul ?: return
        if (g == "bae") {
            mal("${c.ireum}에서 출항합니다. 도착지 목록을 받는 중입니다.")
            Tongsin.json("gyotong.php", mapOf("a" to "yeok", "gal" to "bae", "chul" to c.id), 15000) { o ->
                val rows = o?.optJSONArray("rows"); val l = ArrayList<Yeok>()
                if (rows != null) for (i in 0 until rows.length()) { val r = rows.optJSONObject(i) ?: continue; if (r.optString("id").isNotEmpty() && r.optString("id") != c.id) l.add(Yeok(r.optString("id"), r.optString("ireum"), "")) }
                baeDo = l
                mal(if (l.isEmpty()) "${c.ireum}에서 오늘 가는 배가 없습니다." else "${c.ireum}에서 가는 곳이 ${l.size}곳입니다. 도착지를 누르십시오.")
                dasi()
            }
            return
        }
        mal("${c.ireum} 출발. 이제 $doMal 이름을 적고 엔터를 치십시오.")
        dasi()
    }

    private fun nalGoreugi() {
        dan = 3
        mal("${chul?.ireum ?: ""}에서 ${doe?.ireum ?: ""}까지. 언제 가십니까.")
        dasi()
    }

    private fun ttaeMal(s: String): String { if (s.length < 12) return ""; return "${s.substring(8, 10).toInt()}시 ${s.substring(10, 12)}분" }

    private fun pyeonBogi() {
        val c = chul ?: return; val d = doe ?: return
        mal("$nalMal ${c.ireum}에서 ${d.ireum} 가는 $galIreum 편을 찾는 중입니다.")
        val gg = g
        Tongsin.json("gyotong.php", mapOf("a" to "pyeon", "gal" to gg, "chul" to c.id, "do" to d.id, "nal" to nal), 15000) { o ->
            val rows = o?.optJSONArray("rows"); val l = ArrayList<String>()
            if (rows != null) for (i in 0 until rows.length()) {
                val x = rows.optJSONObject(i) ?: continue
                l.add(when (gg) {
                    "bae" -> x.optString("mal")
                    "gicha" -> { val ch = x.optString("adultcharge"); "${ttaeMal(x.optString("depplandtime"))} ${x.optString("traingradename")} ${x.optString("depplacename")} 출발, ${ttaeMal(x.optString("arrplandtime"))} ${x.optString("arrplacename")} 도착" + (if (ch.isNotEmpty()) ", 어른 ${ch}원" else "") }
                    else -> { val ch = x.optString("charge"); val gr = x.optString("gradeNm"); "${ttaeMal(x.optString("depPlandTime"))} ${x.optString("depPlaceNm")} 출발, ${ttaeMal(x.optString("arrPlandTime"))} ${x.optString("arrPlaceNm")} 도착" + (if (gr.isNotEmpty()) ", $gr" else "") + (if (ch.isNotEmpty()) ", ${ch}원" else "") }
                })
            }
            pyeon = l; dan = 4; sijak = 0
            mal(if (l.isEmpty()) "$nalMal ${c.ireum}에서 ${d.ireum} 가는 ${galIreum}가 없습니다." else "$nalMal ${c.ireum}에서 ${d.ireum} 가는 $galIreum ${l.size}편. 첫 편은 ${l[0]}. 편을 누르시면 다시 읽어 드립니다.")
            dasi()
        }
    }
}
