package kr.or.ada.app.gilnun

// 2.8.0 (261003-K1, 이사장님 지시 "지방에서 복지콜·이동약자 차량 부르기 확인") 콜 번호 지역 알아보기 — 아이폰 KolJiyeok.swift 와 같은 셈
// 예전: 시·도마다 네모 테두리로 잘라 처음 맞는 시·도를 골랐음 — 김해가 부산, 가평이 강원, 일산이 서울로 잡히는 등 지방에서 틀림
// 이제: ① 폰의 주소 찾기(Geocoder, 무료·열쇠 없음)로 시·도와 시·군·구
//       ② 나스 call.json 2.0 의 "시군"(전국 138개 시·군 센터)에서 그 시·군 센터 → 시·도 광역센터 → 전국
//       ③ 주소를 못 찾으면 마지막으로 알아낸 지역, 없으면 15킬로미터 안의 가장 가까운 센터, 그다음 가장 작은 네모
//       ④ 위치를 한 번도 못 잡았으면 서울 번호를 몰래 내놓지 않고 위치를 잡는 중이라고 알림
// 2026년 7월 출범한 전남광주통합특별시 — 광주 다섯 구는 광주 센터, 나머지는 전남

import android.content.Context
import android.content.SharedPreferences
import android.location.Geocoder
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal object KolJiyeok {
    class Jiyeok(val sido: String, val sigungu: List<String>, val lat: Double, val lon: Double, val ttae: Long)

    private var ctx: Context? = null
    private var d: SharedPreferences? = null
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var chatneunJung = false
    var majimak: Jiyeok? = null
        private set

    private val gwangjuGu = listOf("동구", "서구", "남구", "북구", "광산구")
    private val byeolching = mapOf(
        "서울" to "서울특별시", "서울시" to "서울특별시",
        "부산" to "부산광역시", "대구" to "대구광역시", "인천" to "인천광역시", "광주" to "광주광역시",
        "대전" to "대전광역시", "울산" to "울산광역시", "세종" to "세종특별자치시", "세종시" to "세종특별자치시",
        "경기" to "경기도", "강원" to "강원특별자치도", "강원도" to "강원특별자치도",
        "충북" to "충청북도", "충남" to "충청남도",
        "전북" to "전북특별자치도", "전라북도" to "전북특별자치도",
        "전남" to "전라남도", "경북" to "경상북도", "경남" to "경상남도",
        "제주" to "제주특별자치도", "제주도" to "제주특별자치도"
    )

    fun sijak(c: Context) {
        if (ctx != null) return
        ctx = c.applicationContext
        d = c.applicationContext.getSharedPreferences("gilnun_kol", Context.MODE_PRIVATE)
        d?.getString("majimak", null)?.let {
            try {
                val o = JSONObject(it)
                val a = o.optJSONArray("sg") ?: JSONArray()
                majimak = Jiyeok(o.optString("sido"), (0 until a.length()).map { i -> a.optString(i) },
                    o.optDouble("lat"), o.optDouble("lon"), o.optLong("ttae"))
            } catch (_: Exception) {}
        }
        // 1분마다 지금 자리로 시·도와 시·군을 확인해 둠
        val dolgi = object : Runnable {
            override fun run() { gaengsin(Wichi.jigeum); main.postDelayed(this, 60_000) }
        }
        main.postDelayed(dolgi, 3_000)
    }

    fun sidoJeongni(s: String, sg: List<String>): String {
        val t = s.trim()
        if (t.contains("전남광주") || t.contains("광주전남")) return if (sg.any { it in gwangjuGu }) "광주광역시" else "전라남도"
        return byeolching[t] ?: t
    }

    /** 지난번과 800미터 넘게 떨어졌거나 10분이 지났을 때만 주소를 다시 찾음 */
    fun gaengsin(w: Jari?) {
        val c = ctx ?: return
        if (w == null || chatneunJung || !Geocoder.isPresent()) return
        val m = majimak
        if (m != null && Wichi.geori(m.lat, m.lon, w.lat, w.lon) < 800 && System.currentTimeMillis() - m.ttae < 600_000) return
        chatneunJung = true
        Thread {
            try {
                @Suppress("DEPRECATION")
                val l = Geocoder(c, Locale.KOREA).getFromLocation(w.lat, w.lon, 1)
                val p = l?.firstOrNull()
                if (p != null) {
                    val hubo = listOfNotNull(p.adminArea, p.locality, p.subAdminArea)
                    val sd = hubo.firstOrNull { it.isNotEmpty() && (it == p.adminArea || it.endsWith("특별시") || it.endsWith("광역시") || it.endsWith("특별자치시")) }
                    if (sd != null) {
                        val sg = ArrayList<String>()
                        for (x in listOf(p.locality, p.subAdminArea, p.subLocality)) if (!x.isNullOrEmpty() && x != sd && x !in sg) sg.add(x)
                        val j = Jiyeok(sidoJeongni(sd, sg), sg, w.lat, w.lon, System.currentTimeMillis())
                        main.post {
                            majimak = j
                            d?.edit()?.putString("majimak", JSONObject().put("sido", j.sido).put("sg", JSONArray(j.sigungu))
                                .put("lat", j.lat).put("lon", j.lon).put("ttae", j.ttae).toString())?.apply()
                            Girok.namgi("kol_jiyeok", mapOf("sido" to j.sido, "sg" to j.sigungu.joinToString(",")))
                        }
                    }
                }
            } catch (_: Exception) {
            } finally { chatneunJung = false }
        }.start()
    }

    /** 콜 번호를 고를 지역 — (시·도, 시·군·구 후보, 어떻게 알았는지) */
    fun jiyeok(o: JSONObject?): Triple<String, List<String>, String>? {
        val w = Wichi.jigeum
        gaengsin(w)
        val m = majimak
        if (m != null && (w == null || Wichi.geori(m.lat, m.lon, w.lat, w.lon) <= 3000)) {
            return Triple(m.sido, m.sigungu, if (w == null) "마지막" else "주소")
        }
        if (w == null) return null
        val sg = o?.optJSONArray("시군")
        if (sg != null) {
            var bd = Double.MAX_VALUE; var bg: JSONObject? = null
            for (i in 0 until sg.length()) {
                val g = sg.optJSONObject(i) ?: continue
                val a = g.optDouble("위도", Double.NaN); val b = g.optDouble("경도", Double.NaN)
                if (a.isNaN() || b.isNaN()) continue
                val dd = Wichi.geori(w.lat, w.lon, a, b)
                if (dd < bd) { bd = dd; bg = g }
            }
            if (bg != null && bd < 15000) return Triple(bg.optString("시도"), bg.optString("시군구").let { if (it.isEmpty()) emptyList() else listOf(it) }, "가까운 센터")
        }
        val jy = o?.optJSONArray("지역")
        if (jy != null) {
            var bn = Double.MAX_VALUE; var bnm: String? = null
            for (i in 0 until jy.length()) {
                val g = jy.optJSONObject(i) ?: continue
                val s = g.optJSONArray("상자") ?: continue
                if (s.length() != 4) continue
                val a0 = s.optDouble(0); val a1 = s.optDouble(1); val o0 = s.optDouble(2); val o1 = s.optDouble(3)
                if (w.lat in a0..a1 && w.lon in o0..o1) {
                    val n = (a1 - a0) * (o1 - o0)
                    if (n < bn) { bn = n; bnm = g.optString("이름") }
                }
            }
            if (bnm != null) return Triple(bnm, emptyList(), "네모")
        }
        return null
    }

    fun mal(o: JSONObject?): String {
        val j = jiyeok(o) ?: return "위치를 아직 잡지 못했습니다. 잠시 뒤 다시 열어 주십시오."
        val gu = j.second.firstOrNull()?.let { " $it" } ?: ""
        return when (j.third) {
            "마지막" -> "위치를 새로 잡지 못해 마지막으로 확인한 ${j.first}$gu의 번호입니다."
            "가까운 센터" -> "주소를 찾지 못해 가장 가까운 ${j.first}$gu 센터 기준 번호입니다."
            "네모" -> "주소를 찾지 못해 대략 ${j.first} 번호입니다."
            else -> "지금 계신 곳은 ${j.first}$gu입니다."
        }
    }

    /** 시·군 센터 → 시·도 광역센터 → 전국 (같은 번호는 한 번만) */
    fun mok(o: JSONObject?): List<NnKol.Kol> {
        val l = ArrayList<NnKol.Kol>()
        val bon = HashSet<String>()
        fun neoki(nm: String, tel: String, bigo: String) {
            val t = tel.filter { it.isDigit() }
            if (nm.isEmpty() || t.isEmpty() || t in bon) return
            bon.add(t); l.add(NnKol.Kol(nm, t, bigo))
        }
        if (o == null) {
            l.add(NnKol.Kol("서울 복지콜", "0220920000", "번호표를 아직 받지 못해 서울 번호입니다"))
            l.add(NnKol.Kol("서울 장애인콜택시", "15884388", "서울시설공단"))
            l.add(NnKol.Kol("서울 나비콜(바우처택시)", "18001133", "바우처택시 이용등록을 마친 뒤 이용"))
            return l
        }
        val j = jiyeok(o) ?: return l
        val sg = o.optJSONArray("시군")
        if (sg != null) {
            for (i in 0 until sg.length()) {
                val g = sg.optJSONObject(i) ?: continue
                if (g.optString("시도") != j.first) continue
                val gu = g.optString("시군구")
                if (gu.isEmpty() || j.second.none { it == gu || it.startsWith(gu) }) continue
                val nm = g.optString("이름").ifEmpty { "$gu 교통약자 이동지원센터" }
                val bigo = listOf(g.optString("비고"), g.optString("앱").let { if (it.isEmpty()) "" else "앱 $it" }).filter { it.isNotEmpty() }.joinToString(", ")
                neoki(nm, g.optString("전화"), bigo)
                g.optJSONArray("다른전화")?.let { a -> for (k in 0 until a.length()) neoki("$nm 다른 번호", a.optString(k), "") }
                break
            }
        }
        val jy = o.optJSONArray("지역")
        if (jy != null) {
            for (i in 0 until jy.length()) {
                val g = jy.optJSONObject(i) ?: continue
                if (g.optString("이름") != j.first) continue
                g.optJSONArray("콜")?.let { a -> for (k in 0 until a.length()) a.optJSONObject(k)?.let { neoki(it.optString("이름"), it.optString("전화"), it.optString("비고")) } }
            }
        }
        o.optJSONArray("전국")?.let { a -> for (k in 0 until a.length()) a.optJSONObject(k)?.let { neoki(it.optString("이름"), it.optString("전화"), it.optString("비고")) } }
        return l
    }

    /** 복지콜·장애인콜은 지역마다 이름이 달라 맞는 이름이 없으면 그 지역 첫 번호(시·군 센터) */
    fun chatgi(o: JSONObject?, jong: String): NnKol.Kol? {
        val l = mok(o)
        return when (jong) {
            "bokji" -> l.firstOrNull { it.ireum.contains("복지") } ?: l.firstOrNull()
            "jangaein" -> l.firstOrNull { it.ireum.contains("장애인") || it.ireum.contains("교통약자") || it.ireum.contains("이동지원") } ?: l.firstOrNull()
            else -> l.firstOrNull { it.ireum.contains("나비") || it.ireum.contains("바우처") }
        }
    }

    private val ppaem = setOf("교통약자", "이동지원센터", "광역이동지원센터", "다른", "번호", "센터", "광역", "콜택시")

    /** 말 속에 지역 콜 이름(두리발, 나드리콜, 새빛콜 같은)이 들어 있으면 그 번호 */
    fun ireumChatgi(o: JSONObject?, alts: List<String>): NnKol.Kol? {
        val mal = alts.map { MalSajeon.ttuk(it) }
        for (k in mok(o)) {
            val ws = k.ireum.replace("(", " ").replace(")", " ").split(" ").filter { it.length >= 2 && it !in ppaem }
            if (ws.any { w -> mal.any { it.contains(w) } }) return k
        }
        return null
    }
}
