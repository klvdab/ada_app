// 여정 다시 돌려 보기 시험 — 안드로이드 길눈 GichaAnnae.kt 바깥 부품 흉내(시험에서만 씀, 앱에 들어가지 않음)
// 앱의 셈(거리·방위·거리 말)은 그대로 옮겨 적고, 소리·기록·통신만 시험용으로 바꿈
package kr.or.ada.app.gilnun

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.*

data class Jari(val lat: Double, val lon: Double, val ochae: Double, val banghyang: Double, val sokdo: Double, val ttae: Long, val georeumChu: Boolean)
data class Mokjeok(val ireum: String, val lat: Double, val lon: Double)
data class Yeojeong(val mokjeok: Mokjeok)
object YeojeongEngine { var jigeum: Yeojeong? = null }

object Wichi {
    fun geori(a1: Double, o1: Double, a2: Double, o2: Double): Double {
        val r = 6371000.0
        val p = Math.PI / 180
        val dla = (a2 - a1) * p
        val dlo = (o2 - o1) * p
        val x = sin(dla / 2) * sin(dla / 2) + cos(a1 * p) * cos(a2 * p) * sin(dlo / 2) * sin(dlo / 2)
        return 2 * r * atan2(sqrt(x), sqrt(1 - x))
    }
}

object Jeomjido {
    fun bannol(x: Double): Double = if (x >= 0) floor(x + 0.5) else -floor(-x + 0.5)
    fun geoMal(d: Double): String {
        if (d >= 995) {
            val k = bannol(d / 100) / 10
            return if (k == bannol(k)) "${k.toInt()}킬로미터" else String.format(Locale.US, "%.1f킬로미터", k)
        }
        if (d >= 100) return "${bannol(d / 10).toInt() * 10}미터"
        return "${max(1, bannol(d / 5).toInt() * 5)}미터"
    }
    fun bangwi(a1: Double, o1: Double, a2: Double, o2: Double): Double {
        val p = Math.PI / 180
        val y = sin((o2 - o1) * p) * cos(a2 * p)
        val x = cos(a1 * p) * sin(a2 * p) - sin(a1 * p) * cos(a2 * p) * cos((o2 - o1) * p)
        val b = atan2(y, x) / p
        return (b + 360) % 360
    }
}

object Annae { fun geoMal(d: Double): String = Jeomjido.geoMal(d) }

enum class MalGeup { JEONGBO, ANNAE, GYEONGGO }

object Sori {
    val malMok = ArrayList<Pair<Long, String>>()
    fun mal(t0: String, geup: MalGeup = MalGeup.ANNAE, kkeutnamyeon: (() -> Unit)? = null) { malMok.add(Pair(GichaAnnae.sigye(), t0)) }
}

object Girok { fun namgi(e: String, d: Map<String, Any?> = emptyMap()) {} }

/** 통신 — 시나리오 파일의 받은 자료를 그대로(곧바로) 돌려줌 */
object Tongsin {
    var jaryo = JSONObject()
    fun json(pail: String, q: Map<String, String>, handO: Int = 8000, kkeut: (JSONObject?) -> Unit) {
        val o = JSONObject().put("ok", true)
        when {
            pail.startsWith("dulle") -> o.put("rows", jaryo.optJSONArray("dulle") ?: JSONArray())
            pail.startsWith("cheolteoneol") -> o.put("rows", jaryo.optJSONArray("teoneol") ?: JSONArray())
            pail.startsWith("gyotong") -> {
                val modu = jaryo.optJSONArray("modu") ?: JSONArray()
                if (q["a"] == "modu") o.put("rows", modu)
                else {
                    fun ireum(id: String?): String { for (i in 0 until modu.length()) { val r = modu.getJSONObject(i); if (r.optString("id") == id) return r.optString("ireum") }; return "" }
                    val k = ireum(q["chul"]) + "-" + ireum(q["do"])
                    o.put("rows", jaryo.optJSONObject("pyeon")?.optJSONArray(k) ?: JSONArray())
                }
            }
        }
        kkeut(o)
    }
}
