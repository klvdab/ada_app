// 여정 계획 — 안드로이드 길눈 2.107.0 (빌드 261126-A97, 대장클, 이사장님 승인 2026-11-26 「첫단계부터 끝까지 꼼꼼하게 해보자」, 아이폰 2.142.0 GilGyehoek.swift 와 같음)
// 「길눈아, 제주도 우도에 가는 길 알려 줘」처럼 물으시면 지금 자리에서 그곳까지 가장 알맞은 길을 길눈이 스스로 짜서 차례대로 말씀드리고,
//   네 하시면 한 차례가 끝날 때마다 다음 탈것을 그때그때 알려 드리며 그 차례의 안내(걷기·지하철·버스·기차·배)를 시작함.
// 첫 단계: 걸리는 시간은 보통 걸리는 시간으로 어림함(길 막힘·실제 시간표는 넣지 못함).
// 비행기는 빠르기로 알아채지 않고 여정 계획이 스스로 다룸 — 공항에 닿으면 비행기 차례를 알리고, 내리는 공항 둘레에 오시면 다음 차례로.
package kr.or.ada.app.gilnun

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject

/** 한 차례(구간) — tal: georeum·jihacheol·beoseu·cha·gicha·bihaenggi·bae */
data class Gugan(val tal: String, val buteo: String, val mok: String, val lat: Double, val lon: Double, val bun: Int,
                 val deo: String = "", val gidarim: Int = 0, val majimak: Boolean = false)

/** 짠 길 하나 */
data class Gyehoek(val mokjeok: String, val mlat: Double, val mlon: Double, val gugan: List<Gugan>, val i: Int = 0, val sijak: Long = System.currentTimeMillis()) {
    val chong: Int get() = gugan.sumOf { it.bun + it.gidarim }
}

object GilGyehoek {
    /** 지금 따라가는 계획(없으면 null) */
    var jigeum: Gyehoek? = null
        private set
    /** 여쭙는 중인 계획들(가장 알맞은 것이 맨 앞) */
    private var hubo: List<Gyehoek> = emptyList()
    private var huboI = 0
    /** 여쭌 때 — 2분 안의 대답만 받음 */
    var jeanTtae = 0L
        private set
    /** 「○○ 가는 길 알려 줘」처럼 길을 물으신 때 — 2분 안에 정한 곳은 가까워도 여정 계획을 짬 */
    var gilMureumTtae = 0L
    var byeonhwa: (() -> Unit)? = null
    private var ctx: Context? = null
    private val main = Handler(Looper.getMainLooper())
    private var sigyeDolgo = false

    fun junbi(c: Context) {
        ctx = c.applicationContext
        if (jigeum != null) return
        val s = c.getSharedPreferences("gilnun_gyehoek", Context.MODE_PRIVATE).getString("jigeum", null) ?: return
        try {
            val g = gyehoekPulgi(JSONObject(s))
            if (System.currentTimeMillis() - g.sijak < 24 * 3600_000L) { jigeum = g; sigyeKyeogi() }
        } catch (e: Exception) {}
    }

    // MARK: 여쭙기·정하기

    fun jeanHagi(l: List<Gyehoek>) {
        hubo = l
        huboI = 0
        jeanTtae = System.currentTimeMillis()
        Girok.namgi("gyehoek_jean", mapOf("mok" to (l.firstOrNull()?.mokjeok ?: "").take(30), "su" to l.size,
            "gil" to (l.firstOrNull()?.let { galraeMal(it) } ?: ""), "bun" to (l.firstOrNull()?.chong ?: 0)))
    }
    val jeanJung: Gyehoek? get() = if (huboI < hubo.size && System.currentTimeMillis() - jeanTtae < 120_000L) hubo[huboI] else null

    fun dareunGil(): Gyehoek? {
        if (hubo.isEmpty()) return null
        huboI += 1
        jeanTtae = System.currentTimeMillis()
        if (huboI >= hubo.size) return null
        Girok.namgi("gyehoek_dareun", mapOf("gil" to galraeMal(hubo[huboI])))
        return hubo[huboI]
    }

    fun talgeotGil(tal: String): Gyehoek? {
        val k = hubo.indexOfFirst { g -> g.gugan.any { it.tal == tal } }
        if (k < 0) return null
        huboI = k
        jeanTtae = System.currentTimeMillis()
        return hubo[k]
    }

    /** 여쭌 길로 정함 — 첫 차례를 돌려줌 */
    fun hwakjeong(): Gugan? {
        val g0 = jeanJung ?: return null
        val g = g0.copy(i = 0, sijak = System.currentTimeMillis())
        jigeum = g
        hubo = emptyList(); huboI = 0; jeanTtae = 0L
        jeojang()
        sigyeKyeogi()
        Girok.namgi("gyehoek_sijak", mapOf("mok" to g.mokjeok.take(30), "gil" to galraeMal(g), "su" to g.gugan.size))
        byeonhwa?.invoke()
        return g.gugan.firstOrNull()
    }

    fun jeanBiugi() { hubo = emptyList(); huboI = 0; jeanTtae = 0L }

    fun geuman(iyu: String) {
        if (jigeum == null && hubo.isEmpty()) return
        Girok.namgi("gyehoek_geuman", mapOf("iyu" to iyu, "i" to (jigeum?.i ?: -1)))
        jigeum = null
        jeanBiugi()
        ctx?.getSharedPreferences("gilnun_gyehoek", Context.MODE_PRIVATE)?.edit()?.remove("jigeum")?.apply()
        byeonhwa?.invoke()
    }

    /** 다음 차례로 — 넘긴 차례(마지막이었으면 null, 계획을 마침) */
    fun daeum(iyu: String): Gugan? {
        val g = jigeum ?: return null
        if (g.i + 1 >= g.gugan.size) { Girok.namgi("gyehoek_kkeut", mapOf("iyu" to iyu)); geuman("kkeut"); return null }
        val n = g.copy(i = g.i + 1)
        jigeum = n
        jeojang()
        Girok.namgi("gyehoek_daeum", mapOf("i" to n.i, "tal" to n.gugan[n.i].tal, "iyu" to iyu))
        byeonhwa?.invoke()
        return n.gugan[n.i]
    }

    private fun jeojang() {
        val g = jigeum ?: return
        ctx?.getSharedPreferences("gilnun_gyehoek", Context.MODE_PRIVATE)?.edit()?.putString("jigeum", gyehoekDamgi(g).toString())?.apply()
    }
    private fun gyehoekDamgi(g: Gyehoek): JSONObject = JSONObject().apply {
        put("mokjeok", g.mokjeok); put("mlat", g.mlat); put("mlon", g.mlon); put("i", g.i); put("sijak", g.sijak)
        put("gugan", JSONArray().apply {
            g.gugan.forEach { u -> put(JSONObject().apply {
                put("tal", u.tal); put("buteo", u.buteo); put("mok", u.mok); put("lat", u.lat); put("lon", u.lon); put("bun", u.bun)
                put("deo", u.deo); put("gidarim", u.gidarim); put("majimak", u.majimak)
            }) }
        })
    }
    private fun gyehoekPulgi(o: JSONObject): Gyehoek {
        val a = o.getJSONArray("gugan")
        val l = (0 until a.length()).map { k ->
            val u = a.getJSONObject(k)
            Gugan(u.getString("tal"), u.getString("buteo"), u.getString("mok"), u.getDouble("lat"), u.getDouble("lon"), u.getInt("bun"),
                u.optString("deo", ""), u.optInt("gidarim", 0), u.optBoolean("majimak", false))
        }
        return Gyehoek(o.getString("mokjeok"), o.getDouble("mlat"), o.getDouble("mlon"), l, o.optInt("i", 0), o.optLong("sijak", System.currentTimeMillis()))
    }

    // MARK: 차례가 끝났는지 보기(15초마다)

    private fun sigyeKyeogi() {
        if (sigyeDolgo) return
        sigyeDolgo = true
        main.postDelayed(object : Runnable {
            override fun run() {
                if (jigeum == null) { sigyeDolgo = false; return }
                boda()
                main.postDelayed(this, 15_000)
            }
        }, 15_000)
    }

    private fun boda() {
        val g = jigeum ?: return
        if (g.i >= g.gugan.size) return
        val w = Wichi.jigeum ?: return
        val gu = g.gugan[g.i]
        val d = Wichi.geori(w.lat, w.lon, gu.lat, gu.lon)
        if (d > dachamGeori(gu)) return
        if (gu.majimak) { Girok.namgi("gyehoek_kkeut", mapOf("iyu" to "dochak")); geuman("dochak"); return }
        val n = daeum("dachaham") ?: return
        MalHagi.gyehoekGuganSijak(n, "${gu.mok}에 닿으셨습니다. ")
    }

    fun dachamGeori(g: Gugan): Double = when (g.tal) {
        "georeum" -> if (g.majimak) 40.0 else 60.0
        "jihacheol" -> 600.0
        "beoseu", "cha" -> 400.0
        "gicha" -> 1500.0
        "bihaenggi" -> 5000.0
        "bae" -> 1000.0
        else -> 300.0
    }

    // MARK: 말

    fun talMal(t: String): String = when (t) {
        "georeum" -> "걸어서"; "jihacheol" -> "지하철로"; "beoseu" -> "버스로"; "cha" -> "차로"
        "gicha" -> "기차로"; "bihaenggi" -> "비행기로"; "bae" -> "배로"; else -> ""
    }
    fun bunMal(b: Int): String = if (b < 60) "${maxOf(b, 1)}분" else if (b % 60 == 0) "${b / 60}시간" else "${b / 60}시간 ${b % 60}분"
    val chasu = listOf("첫째", "둘째", "셋째", "넷째", "다섯째", "여섯째", "일곱째", "여덟째")
    private fun cha(k: Int) = if (k < chasu.size) chasu[k] else "${k + 1}번째"

    fun galraeMal(g: Gyehoek): String = when {
        g.gugan.any { it.tal == "bihaenggi" } -> "비행기"
        g.gugan.any { it.tal == "bae" } && jejuInga(g.mlat, g.mlon) -> "배"
        g.gugan.any { it.tal == "gicha" } -> "기차"
        g.gugan.all { it.tal == "cha" || it.tal == "georeum" } -> "차"
        else -> "대중교통"
    }

    fun jeanMal(g: Gyehoek, cheot: Boolean): String {
        val b = StringBuilder(if (cheot) "${g.mokjeok}까지 가장 알맞은 길을 짰습니다. " else "다른 길입니다. ")
        b.append(if (g.gugan.size == 1) "한 차례로, 대략 ${bunMal(g.chong)} 걸립니다. " else "모두 ${g.gugan.size}차례, 대략 ${bunMal(g.chong)} 걸립니다. ")
        g.gugan.forEachIndexed { k, u -> b.append("${cha(k)}, ${talMal(u.tal)} ${u.mok}까지 대략 ${bunMal(u.bun)}. ") }
        if (g.gugan.any { it.tal == "bihaenggi" }) b.append("비행기는 뜨기 한 시간 전까지 공항에 닿으셔야 합니다. ")
        if (g.gugan.any { it.tal == "bae" }) b.append("배를 타실 때는 신분증이 꼭 있어야 합니다. ")
        b.append("걸리는 시간은 보통 때의 어림입니다. ")
        return b.toString()
    }

    fun guganMal(u: Gugan, k: Int, su: Int): String {
        val b = StringBuilder(if (su > 1) "${cha(k)} 차례입니다. " else "")
        if (u.tal == "georeum" || k == 0) b.append("${talMal(u.tal)} ${u.mok}까지 갑니다. 대략 ${bunMal(u.bun)} 걸립니다. ")
        else b.append("${u.buteo}에서 ${talMal(u.tal)} ${u.mok}까지 갑니다. 대략 ${bunMal(u.bun)} 걸립니다. ")
        if (u.deo.isNotEmpty()) b.append(u.deo).append(" ")
        return b.toString()
    }

    fun namEunMal(): String {
        val g = jigeum ?: return "따라가는 여정 계획이 없습니다."
        val b = StringBuilder("${g.mokjeok}까지 ${g.gugan.size}차례 가운데 지금 ${cha(g.i)} 차례입니다. ")
        for (k in g.i until g.gugan.size) { val u = g.gugan[k]; b.append("${cha(k)}, ${talMal(u.tal)} ${u.mok}까지 대략 ${bunMal(u.bun)}. ") }
        return b.toString()
    }

    // MARK: 길 묻는 말 알아듣기

    private const val BBAE = " \t\n.,!?~·'\""
    private val pyo = listOf("가는길좀알려", "가는길알려", "가는길가르쳐", "가는방법", "가는법", "가려면어떻게", "가려면뭐타", "가려면뭘타", "가려면무엇을타",
        "어떻게가야", "어떻게가요", "어떻게가니", "어떻게가지", "어떻게갈수", "어떻게가", "뭐타고가", "뭘타고가", "무엇을타고가", "뭐타야",
        "가는교통편", "교통편알려", "교통편좀알려", "교통편은", "교통편", "가는차편", "길알려줘", "가는코스", "코스짜줘", "여정짜줘")

    /** 「우도 가는 길 알려 줘」, 「우도 어떻게 가」, 「우도 가려면 뭐 타야 돼」 — 가실 곳 말(없으면 빈 글). 길 묻는 말이 아니면 null */
    fun gilMureum(t: String): String? {
        val z = t.filter { !BBAE.contains(it) }
        if (z.length < 3 || z.length > 40) return null
        var choe = -1
        for (p in pyo) { val k = z.indexOf(p); if (k >= 0 && (choe < 0 || k < choe)) choe = k }
        if (choe < 0) return null
        var n = 0
        var cut = t.length
        for (i in t.indices) {
            if (n == choe) { cut = i; break }
            if (!BBAE.contains(t[i])) n += 1
        }
        var q = t.substring(0, cut).trim()
        for (a in listOf("길눈아", "길눈", "하이 길눈", "여기서", "지금", "나는", "나", "저는", "제가", "내가")) {
            if (q.startsWith("$a ") || q == a) { q = q.substring(a.length).trim() }
        }
        for (p in listOf("까지는", "까지", "으로", "에는", "에", "로", "은", "는")) {
            if (q.endsWith(p) && q.length > p.length + 1) { q = q.dropLast(p.length).trim(); break }
        }
        return q
    }

    // MARK: 길 짜기 — 자료

    private data class Got(val ireum: String, val lat: Double, val lon: Double)
    private data class JageunSeom(val ireum: String, val la1: Double, val la2: Double, val lo1: Double, val lo2: Double, val tan: Got, val nae: Got, val bun: Int, val deo: String)
    private val jejuGh = Got("제주국제공항", 33.5113, 126.4930)
    private val jageun = listOf(
        JageunSeom("우도", 33.485, 33.530, 126.930, 126.980, Got("성산포항 종합여객터미널", 33.4744, 126.9330), Got("우도 천진항", 33.4964, 126.9545), 15,
            "우도 배는 대략 30분마다 다니고, 막배는 계절에 따라 저녁 대여섯 시쯤 끊깁니다. 신분증을 꼭 챙기시고, 승선신고서에 이름과 생년월일과 전화번호를 적습니다. 바람이 세면 배가 뜨지 않습니다."),
        JageunSeom("마라도", 33.105, 33.125, 126.260, 126.280, Got("운진항", 33.2178, 126.2526), Got("마라도 살레덕선착장", 33.1179, 126.2690), 30,
            "마라도 배는 하루 몇 차례만 다니고 바람이 세면 뜨지 않습니다. 나서시기 전에 꼭 확인하시고 신분증을 챙기십시오."),
        JageunSeom("가파도", 33.158, 33.182, 126.258, 126.288, Got("운진항", 33.2178, 126.2526), Got("가파도 상동포구", 33.1747, 126.2737), 15,
            "가파도 배는 하루 몇 차례만 다니고 바람이 세면 뜨지 않습니다. 나서시기 전에 꼭 확인하시고 신분증을 챙기십시오."),
        JageunSeom("비양도", 33.400, 33.416, 126.218, 126.236, Got("한림항 비양도 도선 대합실", 33.4140, 126.2638), Got("비양도 선착장", 33.4087, 126.2317), 15,
            "비양도 배는 하루 몇 차례만 다닙니다. 나서시기 전에 꼭 확인하시고 신분증을 챙기십시오.")
    )
    private val jejuBae = listOf("mokpo" to 270, "wando" to 170, "nokdong" to 220, "yeosu" to 330, "busan" to 720)
    private val seon = listOf(
        setOf("서울", "용산", "광명", "수서", "행신", "동탄", "평택지제", "수원", "천안아산", "오송", "대전", "김천구미", "동대구", "경주", "울산", "부산", "구포", "밀양", "포항", "창원중앙", "마산", "진주"),
        setOf("서울", "용산", "광명", "수서", "행신", "동탄", "평택지제", "수원", "천안아산", "오송", "공주", "서대전", "익산", "정읍", "광주송정", "목포", "전주", "남원", "순천", "여수엑스포"),
        setOf("서울", "청량리", "원주", "평창", "진부", "강릉", "동해"),
        setOf("청량리", "원주", "제천", "영주", "안동", "경주", "태화강", "부전"),
        setOf("청량리", "춘천")
    )
    private val ktx = setOf("서울", "용산", "수서", "광명", "행신", "청량리", "수원", "평택지제", "동탄", "천안아산", "오송", "대전", "서대전", "김천구미", "동대구", "경주", "울산", "부산", "포항", "창원중앙", "마산", "진주", "밀양", "구포", "광주송정", "목포", "익산", "전주", "정읍", "공주", "남원", "순천", "여수엑스포", "강릉", "원주", "평창", "진부", "안동", "영주", "동해")
    private val juyoYeok: List<GichaYeok.Y> by lazy { val s = seon.flatten().toSet(); GichaYeok.mokrok.filter { s.contains(it.ireum) } }

    private fun geori(a: Double, b: Double, c: Double, d: Double) = Wichi.geori(a, b, c, d)
    fun jejuInga(la: Double, lo: Double) = la >= 33.05 && la <= 33.65 && lo >= 126.05 && lo <= 127.00
    private fun jageunSeom(la: Double, lo: Double) = jageun.firstOrNull { la >= it.la1 && la <= it.la2 && lo >= it.lo1 && lo <= it.lo2 }
    private fun iteona(a: String, b: String) = seon.any { it.contains(a) && it.contains(b) }
    private fun G(tal: String, bu: String, mok: String, la: Double, lo: Double, bun: Double, deo: String = "", gid: Int = 0) =
        Gugan(tal, bu, mok, la, lo, maxOf(1, Math.round(bun).toInt()), deo, gid)
    private fun chong(l: List<Gugan>) = l.sumOf { it.bun + it.gidarim }

    private fun dongne(fn: String, fla: Double, flo: Double, tn: String, tla: Double, tlo: Double, jejuAn: Boolean = false): List<Gugan>? {
        val d = geori(fla, flo, tla, tlo)
        if (d <= 1200) return listOf(G("georeum", fn, tn, tla, tlo, d * 1.3 / 66))
        if (!jejuAn && Seom.jicheolItna(fla, flo) && Seom.jicheolItna(tla, tlo) && d <= 60_000) return listOf(G("jihacheol", fn, tn, tla, tlo, d * 1.25 / 550 + 12))
        if (d <= 45_000 || jejuAn) return listOf(G("beoseu", fn, tn, tla, tlo, d * 1.3 / (if (d > 15_000) 500 else 350) + 15, if (jejuAn) "택시를 부르셔도 됩니다." else ""))
        return null
    }

    private fun gakkaunYeok(la: Double, lo: Double): List<Pair<GichaYeok.Y, Double>> =
        juyoYeok.map { it to geori(la, lo, it.lat, it.lon) }.filter { it.second <= 30_000 }.sortedBy { it.second }.take(3)

    private fun yukji(fn: String, fla: Double, flo: Double, tn: String, tla: Double, tlo: Double): List<List<Gugan>> {
        val d = geori(fla, flo, tla, tlo)
        if (d <= 45_000) dongne(fn, fla, flo, tn, tla, tlo)?.let { return listOf(it) }
        val out = ArrayList<List<Gugan>>()
        var choe: List<Gugan>? = null
        for (a in gakkaunYeok(fla, flo)) for (b in gakkaunYeok(tla, tlo)) {
            val ya = a.first; val yb = b.first
            if (ya.ireum == yb.ireum || geori(ya.lat, ya.lon, yb.lat, yb.lon) < 40_000 || !iteona(ya.ireum, yb.ireum)) continue
            val l1 = dongne(fn, fla, flo, ya.ireum + "역", ya.lat, ya.lon) ?: listOf(G("cha", fn, ya.ireum + "역", ya.lat, ya.lon, a.second * 1.35 / 1000 + 10))
            val gd = geori(ya.lat, ya.lon, yb.lat, yb.lon)
            val sok = if (ktx.contains(ya.ireum) && ktx.contains(yb.ireum)) 2500.0 else 1500.0
            val l2 = listOf(G("gicha", ya.ireum + "역", yb.ireum + "역", yb.lat, yb.lon, gd * 1.25 / sok + 15,
                "표는 코레일톡 앱이나 역 창구에서 끊으시고, 역에서 도움이 필요하시면 역무원에게 말씀하십시오. 미리 신청하시면 타는 곳까지 안내를 받으실 수 있습니다."))
            val l3 = dongne(yb.ireum + "역", yb.lat, yb.lon, tn, tla, tlo) ?: listOf(G("cha", yb.ireum + "역", tn, tla, tlo, b.second * 1.35 / 1000 + 10))
            val l = l1 + l2 + l3
            if (choe == null || chong(l) < chong(choe)) choe = l
        }
        choe?.let { out.add(it) }
        out.add(listOf(G("cha", fn, tn, tla, tlo, d * 1.3 / 1150 + 10, "고속버스나 시외버스로 가셔도 됩니다.")))
        return out
    }

    /** 지금 자리에서 그곳까지 길들 — 가장 알맞은 것이 맨 앞 */
    fun jjagi(fla: Double, flo: Double, mok: Jangso): List<Gyehoek> {
        val tn = mok.ireum; val tla = mok.lat; val tlo = mok.lon
        val fn = "지금 계신 곳"
        val plans = ArrayList<Pair<String, List<Gugan>>>()
        val toJ = jejuInga(tla, tlo); val frJ = jejuInga(fla, flo)
        val tj = jageunSeom(tla, tlo); val fj = jageunSeom(fla, flo)
        val gonghang = Seom.gonghang.filter { it.ireum != "인천국제공항" }
        val seomIreumMan = tn in setOf("제주도", "제주", "제주특별자치도")
        val biDeo = "비행기 뜨기 한 시간 전까지 공항에 닿으셔야 합니다. 표를 끊으실 때 항공사에 시각장애가 있다고 알리시면 공항에서 체크인부터 탑승까지 안내를 받으실 수 있습니다. 내리는 공항 둘레에 오시면 다음 차례를 알려 드립니다."

        fun jejuSok(bn: String, bla: Double, blo: Double): List<Gugan> {
            if (tj != null) {
                val l = ArrayList(dongne(bn, bla, blo, tj.tan.ireum, tj.tan.lat, tj.tan.lon, true) ?: emptyList())
                l.add(G("bae", tj.tan.ireum, tj.nae.ireum, tj.nae.lat, tj.nae.lon, (tj.bun + 15).toDouble(), tj.deo))
                if (tn != tj.ireum && geori(tj.nae.lat, tj.nae.lon, tla, tlo) > 150) l.addAll(dongne(tj.nae.ireum, tj.nae.lat, tj.nae.lon, tn, tla, tlo, true) ?: emptyList())
                return l
            }
            if (seomIreumMan || geori(bla, blo, tla, tlo) < 300) return emptyList()
            return dongne(bn, bla, blo, tn, tla, tlo, true) ?: emptyList()
        }

        if (toJ && !frJ) {
            for (gh in gonghang) for (yl in yukji(fn, fla, flo, gh.ireum, gh.lat, gh.lon)) {
                val fd = geori(gh.lat, gh.lon, jejuGh.lat, jejuGh.lon)
                plans.add("bihaeng" to (yl + G("bihaenggi", gh.ireum, jejuGh.ireum, jejuGh.lat, jejuGh.lon, fd / 8000 + 15, biDeo, 60) + jejuSok(jejuGh.ireum, jejuGh.lat, jejuGh.lon)))
            }
            val jh = Seom.hanggu["jejuhang"]
            if (jh != null) for ((hk, bun) in jejuBae) {
                val h = Seom.hanggu[hk] ?: continue
                for (yl in yukji(fn, fla, flo, h.ireum, h.lat, h.lon)) {
                    plans.add("bae" to (yl + G("bae", h.ireum, jh.ireum, jh.lat, jh.lon, (bun + 40).toDouble(),
                        "제주 가는 배는 하루 몇 차례뿐이니 미리 선사에 확인하시고, 신분증을 꼭 챙기십시오. 바람이 세면 배가 뜨지 않습니다.") + jejuSok(jh.ireum, jh.lat, jh.lon)))
                }
            }
        } else if (frJ && !toJ) {
            val l1 = dongne(fn, fla, flo, jejuGh.ireum, jejuGh.lat, jejuGh.lon, true) ?: emptyList()
            for (gh in gonghang) {
                val fd = geori(gh.lat, gh.lon, jejuGh.lat, jejuGh.lon)
                val b = G("bihaenggi", jejuGh.ireum, gh.ireum, gh.lat, gh.lon, fd / 8000 + 15, biDeo, 60)
                for (yl in yukji(gh.ireum, gh.lat, gh.lon, tn, tla, tlo)) plans.add("bihaeng" to (l1 + b + yl))
            }
        } else if (toJ && frJ && tj != null && tj.ireum != fj?.ireum) {
            val l = ArrayList(dongne(fn, fla, flo, tj.tan.ireum, tj.tan.lat, tj.tan.lon, true) ?: emptyList())
            l.add(G("bae", tj.tan.ireum, tj.nae.ireum, tj.nae.lat, tj.nae.lon, (tj.bun + 15).toDouble(), tj.deo))
            if (tn != tj.ireum && geori(tj.nae.lat, tj.nae.lon, tla, tlo) > 150) l.addAll(dongne(tj.nae.ireum, tj.nae.lat, tj.nae.lon, tn, tla, tlo, true) ?: emptyList())
            plans.add("bae" to l)
        } else {
            val sm = Seom.jarilo(tla, tlo)
            if (sm != null && sm.ireum != "제주도" && Seom.jarilo(fla, flo)?.ireum != sm.ireum) {
                for (hk in sm.bae) {
                    val h = Seom.hanggu[hk] ?: continue
                    for (yl in yukji(fn, fla, flo, h.ireum, h.lat, h.lon)) {
                        val bd = geori(h.lat, h.lon, tla, tlo)
                        plans.add("bae" to (yl + G("bae", h.ireum, tn, tla, tlo, bd * 1.05 / (if (bd > 80_000) 900 else 450) + 30,
                            sm.mal + " 배는 하루 몇 차례뿐이니 미리 선사에 확인하시고, 신분증을 꼭 챙기십시오. 바람이 세면 배가 뜨지 않습니다.")))
                    }
                }
            } else {
                for (yl in yukji(fn, fla, flo, tn, tla, tlo)) plans.add("yukji" to yl)
            }
        }

        val best = LinkedHashMap<String, List<Gugan>>()
        for ((k, gs0) in plans) {
            val gs = ArrayList<Gugan>()
            var pla = fla; var plo = flo
            for (g in gs0) {
                if (g.tal == "georeum" && geori(pla, plo, g.lat, g.lon) < 150) continue
                gs.add(g); pla = g.lat; plo = g.lon
            }
            if (gs.isEmpty()) continue
            val kk = k + (if (gs.all { it.tal == "cha" || it.tal == "georeum" }) "_cha" else "")
            val b0 = best[kk]
            if (b0 == null || chong(gs) < chong(b0)) best[kk] = gs
        }
        val l = best.values.sortedBy { chong(it) }.toMutableList()
        if (l.size >= 2 && l[0].all { it.tal == "cha" || it.tal == "georeum" }) {
            val k = l.indexOfFirst { gs -> !gs.all { it.tal == "cha" || it.tal == "georeum" } }
            if (k > 0 && chong(l[k]) <= chong(l[0]) * 1.5) { val d = l.removeAt(k); l.add(0, d) }
        }
        return l.map { gs ->
            val g2 = gs.toMutableList()
            if (g2.isNotEmpty()) g2[g2.size - 1] = g2.last().copy(majimak = g2.last().mok == tn)
            Gyehoek(tn, tla, tlo, g2)
        }
    }
}
