// 안드로이드 길눈 — 음성유도기와 승강기(2.7.0, 묶음 b2 점지도 마저, 대표님 지시). 아이폰 Yudo.swift 2.14.0(이사장님 승인)과 같은 자료(/jeom/yudo.php, 서울교통공사).
// 지금 자리에서 가까운 지하철역 다섯 곳을 찾고, 역을 고르면 음성유도기·엘리베이터·에스컬레이터를 세 갈래로 나누어 알려 드립니다.
// 서울 1호선부터 8호선까지만 자료가 있습니다. 목록은 다섯씩, 줄에 번호 없음, 결과가 나오면 커서를 첫 줄로.
package kr.or.ada.app.gilnun

import android.os.Handler
import android.os.Looper
import android.view.View
import org.json.JSONObject
import java.util.Locale

data class YudoYeok(val ireum: String, val ho: String, val geori: Int) {
    val julMal: String get() = "${ireum}역 ${ho}호선, ${geori}미터"
}

class YudoHwamyeon : Hwamyeon("음성유도기와 승강기") {
    private var yeokDeul: List<YudoYeok> = emptyList()
    private var sangtae = 0   // 0 찾는 중, 1 찾음
    private var goreun: YudoYeok? = null
    private var yudo: List<String> = emptyList()
    private var ev: List<String> = emptyList()
    private var es: List<String> = emptyList()
    private var yeokMal = ""
    private var yeokSijak = 0
    private val pyeol = intArrayOf(-1, 0, 0, 0)   // 갈래별 펼침(-1 이면 닫힘), 값은 보이는 첫 줄
    private var pyeolGalrae = 0                   // 지금 펼친 갈래(1 음성유도기, 2 엘리베이터, 3 에스컬레이터, 0 없음)
    private var alrim = false
    private var chojeomHal = false
    private var cheot: View? = null
    private var t0: GilnunActivity? = null
    private var sedae = 0
    private val main = Handler(Looper.getMainLooper())

    override fun chaeugi(t: GilnunActivity) {
        t0 = t
        cheot = null
        if (!alrim) { alrim = true; chatgi() }
        val g = goreun
        if (g != null) {
            cheot = t.geul(if (yeokMal.isEmpty()) "${g.ireum}역 자료를 받는 중입니다." else yeokMal)
            galrae(t, 1, "음성유도기 ${yudo.size}곳", yudo)
            galrae(t, 2, "엘리베이터 ${ev.size}대", ev)
            galrae(t, 3, "에스컬레이터 ${es.size}대", es)
            t.danchu("다른 역 고르기") { goreun = null; yeokMal = ""; pyeolGalrae = 0; chojeomHal = true; t.dasiGeurigi() }
        } else if (sangtae == 0) {
            cheot = t.geul("가까운 역을 찾고 있습니다.")
        } else if (yeokDeul.isEmpty()) {
            cheot = t.danchu("가까운 역을 찾지 못했습니다 — 다시 찾기") { chatgi(); t.dasiGeurigi() }
        } else {
            val kkeut = minOf(yeokSijak + 5, yeokDeul.size)
            for (n in yeokSijak until kkeut) {
                val y = yeokDeul[n]
                val b = t.danchu(y.julMal) { yeokBoda(y) }
                if (n == yeokSijak) cheot = b
            }
            if (kkeut < yeokDeul.size) t.danchu("더 보기") { yeokSijak += 5; chojeomHal = true; t.dasiGeurigi() }
            if (yeokSijak > 0) t.danchu("이전 보기") { yeokSijak = maxOf(0, yeokSijak - 5); chojeomHal = true; t.dasiGeurigi() }
            t.danchu("가까운 역 다시 찾기") { chatgi(); t.dasiGeurigi() }
        }
        t.danchu(if (pyeolGalrae == 9) "알아 두실 것 접기" else "알아 두실 것 펼치기") { pyeolGalrae = if (pyeolGalrae == 9) 0 else 9; t.dasiGeurigi() }
        if (pyeolGalrae == 9) t.geul("지금 계신 자리에서 가까운 지하철역 다섯 곳을 찾습니다. 역을 고르시면 그 역의 음성유도기, 엘리베이터, 에스컬레이터를 세 갈래로 나누어 알려 드립니다. 음성유도기는 출구 계단이나 개찰구, 발매기 같은 곳에 붙어 있는 소리 나는 장치이며 어디에 있는지 그대로 적어 두었습니다. 엘리베이터는 몇 층에서 몇 층까지 다니는지와 몇 번 출구 쪽인지가 나옵니다. 서울교통공사가 내놓은 자료로, 서울 1호선부터 8호선까지만 들어 있습니다. 실제와 다를 수 있으니 다르면 역무원에게 도움을 청하십시오.")
    }

    /** 한 갈래 — 펼치면 다섯씩 */
    private fun galrae(t: GilnunActivity, k: Int, ireum: String, l: List<String>) {
        if (l.isEmpty()) return
        val yeollim = pyeolGalrae == k
        t.danchu(if (yeollim) "$ireum 접기" else "$ireum 펼치기") {
            pyeolGalrae = if (yeollim) 0 else k
            pyeol[k] = 0
            chojeomHal = !yeollim
            t.dasiGeurigi()
        }
        if (!yeollim) return
        val s = pyeol[k].coerceAtLeast(0)
        val kkeut = minOf(s + 5, l.size)
        for (n in s until kkeut) {
            val v = t.geul(l[n], true)
            if (n == s && chojeomHal) cheot = v
        }
        if (kkeut < l.size) t.danchu("$ireum 더 보기") { pyeol[k] = s + 5; chojeomHal = true; t.dasiGeurigi() }
        if (s > 0) t.danchu("$ireum 이전 보기") { pyeol[k] = maxOf(0, s - 5); chojeomHal = true; t.dasiGeurigi() }
    }

    override fun boilttae(t: GilnunActivity) {
        if (!chojeomHal) return
        chojeomHal = false
        cheot?.let { t.chojeomOmgigi(it) }
    }

    private fun dasi() {
        val t = t0 ?: return
        chojeomHal = true
        if (t.wiHwamyeon === this) t.dasiGeurigi()
    }

    private fun chatgi() {
        sangtae = 0
        sedae += 1
        val sd = sedae
        Wichi.wiseongDolligi()
        gidarigi(sd, 0)
    }

    /** 자리를 아직 못 받았으면 1초씩 열 번까지 기다림(아이폰과 같음) */
    private fun gidarigi(sd: Int, beon: Int) {
        if (sd != sedae) return
        val j = Wichi.jigeum
        if (j == null) {
            if (beon >= 10) {
                sangtae = 1
                Sori.mal("지금 자리를 알 수 없어 역을 찾지 못했습니다.")
                dasi()
                return
            }
            main.postDelayed({ gidarigi(sd, beon + 1) }, 1000)
            return
        }
        mutgi(mapOf("a" to "gakkaun", "la" to String.format(Locale.US, "%.6f", j.lat), "lo" to String.format(Locale.US, "%.6f", j.lon))) { o ->
            if (sd != sedae) return@mutgi
            val l = ArrayList<YudoYeok>()
            val a = o?.optJSONArray("list")
            if (a != null) for (i in 0 until a.length()) {
                val r = a.optJSONObject(i) ?: continue
                val nm = r.opt("ireum") as? String ?: continue
                l.add(YudoYeok(nm, (r.opt("ho") as? String) ?: "", (Jeomjido.su(r, "geori") ?: 0.0).toInt()))
            }
            yeokDeul = l
            yeokSijak = 0
            sangtae = 1
            Sori.mal(if (l.isEmpty()) "가까운 역을 찾지 못했습니다." else "가까운 역 ${l.size}곳입니다. 가장 가까운 곳은 ${l[0].julMal}입니다.", MalGeup.JEONGBO)
            dasi()
        }
    }

    private fun yeokBoda(y: YudoYeok) {
        goreun = y
        yudo = emptyList(); ev = emptyList(); es = emptyList(); yeokMal = ""; pyeolGalrae = 0
        chojeomHal = true
        t0?.dasiGeurigi()
        var o1: JSONObject? = null
        var o2: JSONObject? = null
        var dap = 0
        fun majim() {
            if (goreun !== y) return
            val yu = strs(o1?.optJSONArray("list"))
            val e1 = strs(o2?.optJSONArray("elevator")).map { dadeum(it) }
            val e2 = strs(o2?.optJSONArray("escalator")).map { dadeum(it) }
            yudo = yu; ev = e1; es = e2
            var m = "${y.ireum}역 — "
            m += if (yu.isEmpty() && e1.isEmpty() && e2.isEmpty()) "음성유도기와 승강기 자료가 없습니다."
                else "음성유도기 ${yu.size}곳, 엘리베이터 ${e1.size}대, 에스컬레이터 ${e2.size}대. 펼치기를 여시면 다섯 곳씩 읽어 드립니다."
            yeokMal = m
            Girok.namgi("yudo", mapOf("yeok" to y.ireum))
            Sori.mal(m, MalGeup.JEONGBO)
            dasi()
        }
        mutgi(mapOf("a" to "yudo", "yeok" to y.ireum)) { o -> o1 = o; dap += 1; if (dap == 2) majim() }
        mutgi(mapOf("a" to "seunggangi", "yeok" to y.ireum)) { o -> o2 = o; dap += 1; if (dap == 2) majim() }
    }

    private fun strs(a: org.json.JSONArray?): List<String> {
        if (a == null) return emptyList()
        val l = ArrayList<String>()
        for (i in 0 until a.length()) (a.opt(i) as? String)?.let { l.add(it) }
        return l
    }

    private fun mutgi(q: Map<String, String>, kkeut: (JSONObject?) -> Unit) {
        Tongsin.json("/jeom/yudo.php", q, 20000, kkeut)
    }

    companion object {
        /** "승강기)엘리베이터-약수 3번 출구측 외부#1 (B1-1F)" → "3번 출구측 외부 1호, 지하1층에서 1층" */
        fun dadeum(s: String): String {
            var t = s.replace(Regex("^승강기\\)[^-]*-"), "")
            t = t.replace("#", " ")
            val r = Regex("\\(([A-Z0-9-]+)\\)\\s*$").find(t)
            if (r != null) {
                val cheung = r.value.trim('(', ')', ' ')
                val pul = cheung.split("-").map { c ->
                    when {
                        c.startsWith("BM") -> "지하 중간층"
                        c.startsWith("B") -> "지하${c.drop(1)}층"
                        c.endsWith("F") -> c.dropLast(1) + "층"
                        else -> c
                    }
                }
                t = t.substring(0, r.range.first).trim() + ", " + (if (pul.size == 2) "${pul[0]}에서 ${pul[1]}" else pul.joinToString(", ") + " 다님")
            }
            return t
        }
    }
}
