// 안드로이드 자봉 — 함께한 기록판·응원 박수 (2.8.0, 빌드 261006-A2, 이사장님 승인 2026-10-06, 아이폰 JabongHamkke.swift 와 짝)
// 뒷단은 리눅스 서버(lvd-jabong 일꾼), 나스 /jabong/hamkke.php 가 건네줌. 서버가 쉬면 조용히 넘어가고, 기록판은 쉬는 중이라 알림.
package kr.or.ada.app.jabong

import kr.or.ada.app.gilnun.Sori
import org.json.JSONObject
import java.util.Calendar

object Hamkke {
    private const val PAIL = "/jabong/hamkke.php"

    private fun ok(o: JSONObject?): JSONObject? = if (o != null && o.optBoolean("ok", false)) o else null

    /** 다 그린 길 알리기(기록판에 셈) — 서버가 쉬면 조용히 넘어감 */
    fun geurimAllim(gil: String, geori: Int) {
        val b = JabongNae.beonho
        if (b.isEmpty() || gil.isEmpty()) return
        val bon = JSONObject().put("gil", gil).put("beonho", b).put("byeol", "자봉 $b").put("geori", geori)
        JbTongsin.postJson(PAIL, mapOf("a" to "geurim"), bon.toString(), null)
    }

    /** 응원 박수 보내기 — (지금 박수 수, 이미 보냈는지). 한 사람이 한 글에 한 번. 못 보내면 null */
    fun baksuChigi(id: String, kkeut: (Pair<Int, Boolean>?) -> Unit) {
        val b = JabongNae.beonho
        if (b.isEmpty()) { kkeut(null); return }
        JbTongsin.postJson(PAIL, mapOf("a" to "baksu"), JSONObject().put("id", id).put("gigi", b).toString()) { o ->
            val j = ok(o)
            kkeut(if (j == null) null else Pair(j.optInt("su", 0), j.optBoolean("imi", false)))
        }
    }

    fun baksuSu(ids: List<String>, kkeut: (Map<String, Int>) -> Unit) {
        if (ids.isEmpty()) { kkeut(emptyMap()); return }
        JbTongsin.getJson(PAIL, mapOf("a" to "baksu", "ids" to ids.joinToString(","))) { o ->
            val s = ok(o)?.optJSONObject("su")
            val m = HashMap<String, Int>()
            if (s != null) for (k in s.keys()) m[k] = s.optInt(k, 0)
            kkeut(m)
        }
    }

    fun girokpan(kkeut: (JSONObject?) -> Unit) {
        JbTongsin.getJson(PAIL, mapOf("a" to "girokpan", "_" to System.currentTimeMillis().toString())) { o -> kkeut(ok(o)) }
    }

    /** 이번 주 이름표(모든 자봉님께 보내는 주간 박수에 씀) */
    val ibeonju: String
        get() {
            val c = Calendar.getInstance()
            c.firstDayOfWeek = Calendar.MONDAY
            c.minimalDaysInFirstWeek = 4
            return "modu-${c.getWeekYear()}-${c.get(Calendar.WEEK_OF_YEAR)}"
        }
}

/** 함께한 기록판 */
class GirokpanHwamyeon : JbHwamyeon("함께한 기록판") {
    private var g: JSONObject? = null
    private var mot = false
    private var bureuneun = false

    override fun chaeugi(t: JabongActivity) {
        val o = g
        if (o == null) {
            if (mot) t.danchu("기록판이 잠시 쉬고 있습니다 — 다시 불러오기") { mot = false; bulreogi(t) }
            else { t.geul("기록판을 불러오고 있습니다.", true); if (!bureuneun) bulreogi(t) }
            return
        }
        val modu = o.optInt("gil_modu", 0) + o.optInt("nas_gil", 0)
        t.kadeu("모두 그린 길\n${modu}개")
        t.kadeu("이번 주 함께 그린 길\n${o.optInt("gil_ibeonju", 0)}개, 약 ${o.optInt("geori_ibeonju", 0)}미터")
        val top = o.optJSONArray("ibeonju_jabong")
        val sb = StringBuilder("이번 주 가장 많이 그려 주신 분\n")
        if (top == null || top.length() == 0) sb.append("이번 주 첫 길의 주인공이 되어 주십시오!")
        else for (i in 0 until top.length()) {
            val x = top.optJSONObject(i) ?: continue
            if (i > 0) sb.append("\n")
            sb.append("${i + 1}등 ${x.optString("byeol")}, ${x.optInt("su")}개")
        }
        t.kadeu(sb.toString())
        t.kadeu("모두 보낸 응원 박수\n${o.optInt("baksu_modu", 0)}번")
        t.danchu("이번 주 모든 자봉님께 응원 박수 보내기") {
            Hamkke.baksuChigi(Hamkke.ibeonju) { r ->
                if (r == null) Sori.mal("박수를 보내지 못했습니다. 잠시 뒤 다시 눌러 주십시오.")
                else if (r.second) Sori.mal("이번 주에는 이미 박수를 보내셨습니다. 이번 주 모두 ${r.first}번입니다.")
                else { Baksu.chigi(true); Sori.mal("모든 자봉님께 응원 박수를 보냈습니다. 이번 주 모두 ${r.first}번입니다.") }
                bulreogi(t, false)
            }
        }
    }

    private fun bulreogi(t: JabongActivity, malHagi: Boolean = true) {
        bureuneun = true
        Hamkke.girokpan { o ->
            bureuneun = false
            if (o == null) { mot = true; if (malHagi) Sori.mal("기록판이 잠시 쉬고 있습니다. 조금 뒤에 다시 불러 주십시오.") }
            else {
                g = o
                if (malHagi) {
                    var m = "모두 그린 길 ${o.optInt("gil_modu", 0) + o.optInt("nas_gil", 0)}개. 이번 주 함께 그린 길 ${o.optInt("gil_ibeonju", 0)}개, 약 ${o.optInt("geori_ibeonju", 0)}미터."
                    val top = o.optJSONArray("ibeonju_jabong")?.optJSONObject(0)
                    if (top != null) m += " 이번 주 가장 많이 그려 주신 분은 ${top.optString("byeol")}님, ${top.optInt("su")}개입니다."
                    Sori.mal(m)
                }
            }
            t.dasiGeurigi()
        }
    }
}
