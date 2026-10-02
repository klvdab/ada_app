// 안드로이드 길눈 — 즐겨찾기(묶음 b1, 아이폰 Jeulgyeo.swift 와 같음)
// 자주 가는 곳. 폰 안(filesDir/jeulgyeo.json)에 담고, 요즘 쓴 곳이 맨 위로 옵니다. 나스로 보내지 않습니다.
// 담기는 것은 반드시 주소와 함께(2026-09-11 이사장님 지시).
package kr.or.ada.app.gilnun

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

data class JeulgyeoHang(val ireum: String, val juso: String, val lat: Double, val lon: Double, val ttae: Long) {
    val id: String get() = "$ireum|$lat|$lon"
    val jangso: Jangso get() = Jangso(ireum, juso, lat, lon)
}

object Jeulgyeo {
    private var pail: File? = null
    var mokrok: List<JeulgyeoHang> = emptyList()
        private set

    fun sijak(c: Context) {
        if (pail != null) return
        val f = File(c.applicationContext.filesDir, "jeulgyeo.json")
        pail = f
        try {
            if (f.exists()) {
                val a = JSONArray(f.readText())
                val l = ArrayList<JeulgyeoHang>()
                for (i in 0 until a.length()) {
                    val o = a.optJSONObject(i) ?: continue
                    val la = Jeomjido.su(o, "lat") ?: continue
                    val lo = Jeomjido.su(o, "lon") ?: continue
                    l.add(JeulgyeoHang(Jeomjido.gul(o, "ireum"), Jeomjido.gul(o, "juso"), la, lo, o.optLong("ttae", 0L)))
                }
                mokrok = l
            }
        } catch (e: Exception) {
            Girok.namgi("jeulgyeo_oryu", mapOf("e" to (e.message ?: "")))
        }
    }

    fun itna(j: Jangso): Boolean =
        mokrok.any { it.ireum == j.ireum || (abs(it.lat - j.lat) < 0.00005 && abs(it.lon - j.lon) < 0.00005) }

    /** 담기 — 같은 이름이나 같은 자리가 이미 있으면 거짓 */
    fun damgi(j: Jangso): Boolean {
        if (itna(j)) return false
        mokrok = listOf(JeulgyeoHang(j.ireum, j.juso, j.lat, j.lon, System.currentTimeMillis())) + mokrok
        jeojang()
        Girok.namgi("jeulgyeo_damgi")
        return true
    }

    fun jiugi(h: JeulgyeoHang) {
        mokrok = mokrok.filter { it.id != h.id }
        jeojang()
    }

    /** 쓴 곳을 맨 위로 */
    fun sseum(j: Jangso) {
        val i = mokrok.indexOfFirst { it.ireum == j.ireum }
        if (i < 0) return
        val h = mokrok[i].copy(ttae = System.currentTimeMillis())
        mokrok = listOf(h) + mokrok.filterIndexed { k, _ -> k != i }
        jeojang()
    }

    private fun jeojang() {
        val f = pail ?: return
        val a = JSONArray()
        for (h in mokrok) {
            a.put(JSONObject().put("ireum", h.ireum).put("juso", h.juso).put("lat", h.lat).put("lon", h.lon).put("ttae", h.ttae))
        }
        try {
            val t = File(f.parentFile, f.name + ".tmp")
            t.writeText(a.toString())
            if (!t.renameTo(f)) { f.writeText(a.toString()); t.delete() }
        } catch (e: Exception) {
            Girok.namgi("jeulgyeo_oryu", mapOf("e" to (e.message ?: "")))
        }
    }
}
