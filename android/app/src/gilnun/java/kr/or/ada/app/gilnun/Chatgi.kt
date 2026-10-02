// 안드로이드 길눈 — 찾기(묶음 b1 목적지와 여정, 아이폰 Chatgi.swift 를 같은 나스 약속으로 옮김)
// 나스 자료 창고에 묻는 일을 한 곳에 모읍니다(웹 길눈·아이폰 길눈이 쓰는 자료 창고를 그대로 씀).
//   jeom.php a=jangso      이름·주소로 곳 찾기(가까운 곳부터)
//   jeom.php a=jimyeong    좌표를 주소로
//   jarimal.php            지금 내 자리를 한 문장으로(가까운 출구·건물까지)
//   chatta.php a=gil       지금 달리는 길 이름과 동네
//   neagori.php            둘레의 사거리·갈림길
// 결과는 모두 화면 줄(main)에서 돌려줍니다(Tongsin.json 과 같음).
package kr.or.ada.app.gilnun

import org.json.JSONObject
import java.util.Locale

/** 곳 하나 — 이름, 주소, 자리(아이폰 Jangso 와 같음). 다른 묶음도 이 꼴을 함께 씀 */
data class Jangso(val ireum: String, val juso: String, val lat: Double, val lon: Double) {
    val id: String get() = "$ireum|$lat|$lon"

    fun json(): JSONObject = JSONObject().put("ireum", ireum).put("juso", juso).put("lat", lat).put("lon", lon)

    companion object {
        fun batgi(o: JSONObject?): Jangso? {
            if (o == null) return null
            val la = Jeomjido.su(o, "lat") ?: return null
            val lo = Jeomjido.su(o, "lon") ?: return null
            return Jangso(Jeomjido.gul(o, "ireum"), Jeomjido.gul(o, "juso"), la, lo)
        }
    }
}

/** 둘레의 사거리·갈림길 하나 */
data class Neagori(val lat: Double, val lon: Double, val mal: String)

object Chatgi {
    fun f6(x: Double): String = String.format(Locale.US, "%.6f", x)
    fun f5(x: Double): String = String.format(Locale.US, "%.5f", x)

    private fun jari(q: HashMap<String, String>) {
        val w = Wichi.jigeum ?: return
        q["lat"] = f6(w.lat)
        q["lon"] = f6(w.lon)
    }

    /** 이름이나 주소로 곳 찾기 — 못 받으면 null, 없으면 빈 목록 */
    fun jangso(mal: String, kkeut: (List<Jangso>?) -> Unit) {
        val q = hashMapOf("a" to "jangso", "q" to mal)
        jari(q)
        Tongsin.json("jeom.php", q) { o ->
            if (o == null) { kkeut(null); return@json }
            val out = ArrayList<Jangso>()
            val rows = o.optJSONArray("rows")
            if (rows != null) for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val la = Jeomjido.su(r, "lat") ?: continue
                val lo = Jeomjido.su(r, "lon") ?: continue
                out.add(Jangso(Jeomjido.gul(r, "ireum"), Jeomjido.gul(r, "juso"), la, lo))
            }
            kkeut(out)
        }
    }

    /** 좌표를 주소로 — 못 받거나 비면 null */
    fun juso(lat: Double, lon: Double, kkeut: (String?) -> Unit) {
        Tongsin.json("jeom.php", mapOf("a" to "jimyeong", "lat" to f6(lat), "lon" to f6(lon))) { o ->
            val j = o?.let { Jeomjido.gul(it, "juso") } ?: ""
            kkeut(if (j.isEmpty()) null else j)
        }
    }

    /** 지금 내 자리 자료(jarimal.php) 통째로 — 못 받으면 null */
    fun jarimalJson(lat: Double, lon: Double, kkeut: (JSONObject?) -> Unit) {
        Tongsin.json("jarimal.php", mapOf("lat" to f6(lat), "lon" to f6(lon)), 10000, kkeut)
    }

    /** 지금 달리는 길 이름과 동네 — 못 받으면 null */
    fun gil(lat: Double, lon: Double, kkeut: (Pair<String, String>?) -> Unit) {
        Tongsin.json("chatta.php", mapOf("a" to "gil", "lat" to f5(lat), "lon" to f5(lon))) { o ->
            if (o == null) { kkeut(null); return@json }
            kkeut(Jeomjido.gul(o, "gil") to Jeomjido.gul(o, "dong"))
        }
    }

    /** 둘레 1킬로미터의 사거리·갈림길 — 못 받으면 null */
    fun neagori(lat: Double, lon: Double, kkeut: (List<Neagori>?) -> Unit) {
        Tongsin.json("neagori.php", mapOf("lat" to f5(lat), "lon" to f5(lon), "ban" to "1000")) { o ->
            if (o == null) { kkeut(null); return@json }
            val out = ArrayList<Neagori>()
            val rows = o.optJSONArray("rows")
            if (rows != null) for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val la = Jeomjido.su(r, "lat") ?: continue
                val lo = Jeomjido.su(r, "lon") ?: continue
                out.add(Neagori(la, lo, Jeomjido.gul(r, "mal")))
            }
            kkeut(out)
        }
    }
}
