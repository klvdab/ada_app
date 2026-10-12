// 안드로이드 자봉 2.21.0 (261012-A17, 대장클, 이사장님 승인 2026-10-12 「1」) AI 밑그림과 지도 — 아이폰 JabongMitgeurim.swift 와 같음
// AI 밑그림 = 협회 걷기 길찾기(나스 /jeom/gilchatgi.php)가 짐작한 걸을 수 있는 길. 봉사자 손에만 쥐여 드리는 짐작이고,
//   길눈 이용자에게 점지도로 내놓지 않습니다(이사장님 원칙: 밑그림은 짐작일 뿐, 실제로 걷는 길이 정답).
// 쓰임 넷: 부탁 길 미리 듣기, 걷는 동안 꺾는 곳 15미터 앞 한 번 알림(처음값 켬), 다 걸은 뒤 견주기(막지 않고 까닭을 말로),
//   밑그림 꺾는 곳을 지났는데 꺾임 표시가 없으면 짚어 드림. 견준 것은 올릴 때 협회 점검에 참고로 갑니다.
// 지도: 카카오 지도(자바스크립트)를 협회 나스 jabong/jido.php 에 띄워 앱 안 웹 화면으로 — 열쇠는 나스가 쥠. 톡백에는 가림.
package kr.or.ada.app.jabong

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import kr.or.ada.app.gilnun.Chatgi
import kr.or.ada.app.gilnun.Wichi
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object JbMit {
    const val gyoyuk = "밑그림은 짐작일 뿐, 실제로 걷는 길이 정답입니다."

    private fun pr(c: Context) = c.getSharedPreferences("jabong", Context.MODE_PRIVATE)
    fun allimKyeojim(c: Context): Boolean = pr(c).getBoolean("jb.mit.allim", true)
    fun allimKyeogi(c: Context, v: Boolean) { pr(c).edit().putBoolean("jb.mit.allim", v).apply() }
    fun jidoKyeojim(c: Context): Boolean = pr(c).getBoolean("jb.mit.jido", true)
    fun jidoKyeogi(c: Context, v: Boolean) { pr(c).edit().putBoolean("jb.mit.jido", v).apply() }

    private fun f6(v: Double) = String.format(java.util.Locale.US, "%.6f", v)

    // MARK: 받기 — mit {pts, kkeok[{lat,lon,gak,sigye,ap}], geori, gugan}

    fun batgi(slat: Double, slon: Double, mlat: Double, mlon: Double, kkeut: (JSONObject?) -> Unit) {
        JbTongsin.getJson("gilchatgi.php", mapOf("slat" to f6(slat), "slon" to f6(slon), "mlat" to f6(mlat), "mlon" to f6(mlon))) { o ->
            kkeut(if (o == null || !o.optBoolean("ok", false)) null else mandeulgi(o))
        }
    }

    private fun mandeulgi(o: JSONObject): JSONObject? {
        val raw = o.optJSONArray("pts") ?: return null
        val pts = ArrayList<DoubleArray>()
        for (i in 0 until raw.length()) {
            val r = raw.optJSONArray(i) ?: continue
            if (r.length() >= 2) pts.add(doubleArrayOf(r.optDouble(0), r.optDouble(1)))
        }
        if (pts.size < 2) return null
        val cum = nujeok(pts)
        val kk = JSONArray()
        var apAp = -1000.0
        val an = o.optJSONArray("an") ?: JSONArray()
        for (i in 0 until an.length()) {
            val j = an.optJSONObject(i)?.optJSONArray("jeom") ?: continue
            if (j.length() < 2) continue
            val k = gakkaunI(pts, j.optDouble(0), j.optDouble(1))
            if (k <= 0 || k >= pts.size - 1) continue
            val g = gakDo(pts, k) ?: continue
            if (abs(g) < 30) continue
            if (cum[k] - apAp < 5) continue
            apAp = cum[k]
            kk.put(JSONObject().put("lat", pts[k][0]).put("lon", pts[k][1]).put("gak", Math.round(g * 10) / 10.0).put("sigye", sigye(g)).put("ap", cum[k].toInt()))
        }
        val gg = JSONArray()
        val gu = o.optJSONArray("gugan") ?: JSONArray()
        for (i in 0 until gu.length()) gu.optJSONObject(i)?.optString("jong", "")?.let { if (it.isNotEmpty()) gg.put(it) }
        val pa = JSONArray(); for (p in pts) pa.put(JSONArray().put(p[0]).put(p[1]))
        val m = JSONObject().put("pts", pa).put("kkeok", kk).put("geori", cum.last().toInt())
        if (gg.length() > 0) m.put("gugan", gg)
        return m
    }

    fun ptsOf(m: JSONObject): List<DoubleArray> {
        val a = m.optJSONArray("pts") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i -> a.optJSONArray(i)?.let { doubleArrayOf(it.optDouble(0), it.optDouble(1)) } }
    }

    // MARK: 말

    fun mal(m: JSONObject, banghyang: Double? = null): String {
        val kk = m.optJSONArray("kkeok") ?: JSONArray()
        val geori = m.optInt("geori")
        var t = "AI 밑그림입니다. 실제 길과 다를 수 있습니다. $gyoyuk 모두 약 ${gm(geori)}, 꺾는 곳 ${kk.length()}곳입니다. "
        val pts = ptsOf(m)
        if (banghyang != null && banghyang >= 0 && pts.size >= 2) {
            val k = apJeom(pts, 0, 10.0)
            val bw = bangwi(pts[0][0], pts[0][1], pts[k][0], pts[k][1])
            t += "지금 서 계신 쪽에서 ${sigye12(gakCha(banghyang, bw))}시 방향으로 출발합니다. "
        }
        var ap = 0
        for (i in 0 until kk.length()) {
            val k = kk.getJSONObject(i)
            t += (if (i == 0) "처음 " else "이어 ") + "약 ${gm(k.optInt("ap") - ap)} 가서 ${k.optInt("sigye")}시 방향으로 꺾습니다. "
            ap = k.optInt("ap")
        }
        t += (if (kk.length() == 0) "약 ${gm(geori)} 곧게 가면" else "마지막으로 약 ${gm(geori - ap)} 가면") + " 도착입니다."
        val gg = m.optJSONArray("gugan")
        if (gg != null && gg.length() > 0) {
            val su = sortedMapOf<String, Int>()
            for (i in 0 until gg.length()) su[gg.optString(i)] = (su[gg.optString(i)] ?: 0) + 1
            t += " 길 가운데 " + su.entries.joinToString(", ") { "${it.key} ${it.value}곳" } + "이 있을 수 있습니다."
        }
        return t
    }

    fun gm(m: Int): String = if (m >= 1000) String.format(java.util.Locale.US, "%.1f킬로미터", m / 1000.0) else "${maxOf(0, (m + 2) / 5 * 5)}미터"

    fun sigye12(d: Double): Int {
        var s = (((d + 360) % 360) / 30).roundToInt() % 12
        if (s == 0) s = 12
        return s
    }

    // MARK: 견주기 — 걸은 자리(acc 25 이하, 탈것 빼고)와 밑그림

    fun gyeonjugi(pts: JSONArray, marks: JSONArray, m: JSONObject): Pair<JSONArray, JSONArray> {
        val mp = ptsOf(m)
        val ps = (0 until pts.length()).mapNotNull { pts.optJSONObject(it) }
            .filter { it.has("lat") && it.has("lon") && it.optDouble("acc", 99.0) <= 25 && !it.has("m") }
        val eot = JSONArray()
        val run = ArrayList<Pair<JSONObject, Double>>()
        fun danggi() {
            if (run.size >= 3) {
                var gil = 0.0
                for (i in 1 until run.size) gil += Wichi.geori(run[i - 1].first.optDouble("lat"), run[i - 1].first.optDouble("lon"), run[i].first.optDouble("lat"), run[i].first.optDouble("lon"))
                val jj = run.maxByOrNull { it.second }!!
                eot.put(JSONObject().put("st0", run.first().first.optInt("st")).put("st1", run.last().first.optInt("st"))
                    .put("gil", gil.roundToInt()).put("choedae", jj.second.roundToInt())
                    .put("lat", jj.first.optDouble("lat")).put("lon", jj.first.optDouble("lon")))
            }
            run.clear()
        }
        for (p in ps) {
            val d = seonGeori(p.optDouble("lat"), p.optDouble("lon"), mp)
            if (d > 15) run.add(Pair(p, d)) else danggi()
        }
        danggi()
        val paj = JSONArray()
        val kk = m.optJSONArray("kkeok") ?: JSONArray()
        for (i in 0 until kk.length()) {
            val k = kk.getJSONObject(i)
            if (abs(k.optDouble("gak")) < 45) continue
            val la = k.optDouble("lat"); val lo = k.optDouble("lon")
            val near = ps.minByOrNull { Wichi.geori(it.optDouble("lat"), it.optDouble("lon"), la, lo) } ?: continue
            if (Wichi.geori(near.optDouble("lat"), near.optDouble("lon"), la, lo) > 15) continue
            var itda = false
            for (j in 0 until marks.length()) {
                val mk = marks.optJSONObject(j) ?: continue
                val n = mk.optString("name")
                if (!n.endsWith("꺾임") && n != "되돌아가는 곳") continue
                if (abs(mk.optInt("st") - near.optInt("st")) <= 12) { itda = true; break }
                if (mk.has("lat") && Wichi.geori(mk.optDouble("lat"), mk.optDouble("lon"), la, lo) <= 20) { itda = true; break }
            }
            if (!itda) paj.put(JSONObject().put("st", near.optInt("st")).put("sigye", k.optInt("sigye")).put("lat", la).put("lon", lo))
        }
        return Pair(eot, paj)
    }

    fun eotganMal(e: JSONObject): String {
        val kd = e.optString("kkadak", "")
        return "${e.optInt("st0")}걸음째부터 ${e.optInt("st1")}걸음째까지 약 ${gm(e.optInt("gil"))} 구간이 밑그림과 가장 멀리 ${e.optInt("choedae")}미터 어긋났습니다." + (if (kd.isEmpty()) "" else " 까닭: $kd")
    }
    fun pajimMal(p: JSONObject): String =
        "${p.optInt("st")}걸음째 근처, 밑그림의 ${p.optInt("sigye")}시 방향 꺾는 곳에 꺾임 표시가 없습니다. 실제로 꺾지 않았다면 괜찮습니다."

    fun gyeolgwaMal(e: JSONArray, p: JSONArray): String {
        if (e.length() == 0 && p.length() == 0) return "AI 밑그림과 견주어 보니 크게 어긋난 곳이 없습니다."
        var t = "AI 밑그림과 견주어 보았습니다. "
        if (e.length() > 0) {
            t += "15미터 넘게 어긋난 구간이 ${e.length()}곳입니다. " + (0 until minOf(3, e.length())).joinToString(" ") { eotganMal(e.getJSONObject(it)) }
            t += " 실제로 걸은 길이 정답이니 막지는 않습니다. 왜 다르게 걸으셨는지 그린 길 화면에서 까닭 말로 남기기를 눌러 주시면 협회 점검에 도움이 됩니다. "
        }
        if (p.length() > 0) t += "꺾임 표시가 빠졌을 수 있는 곳이 ${p.length()}곳입니다. " + (0 until minOf(3, p.length())).joinToString(" ") { pajimMal(p.getJSONObject(it)) }
        return t
    }

    // MARK: 작은 셈

    fun bangwi(a1: Double, o1: Double, a2: Double, o2: Double): Double {
        val p = Math.PI / 180
        val y = sin((o2 - o1) * p) * cos(a2 * p)
        val x = cos(a1 * p) * sin(a2 * p) - sin(a1 * p) * cos(a2 * p) * cos((o2 - o1) * p)
        return (atan2(y, x) / p + 360) % 360
    }
    fun gakCha(a: Double, b: Double): Double {
        var d = (b - a) % 360
        if (d > 180) d -= 360
        if (d < -180) d += 360
        return d
    }
    fun sigye(d: Double): Int {
        var s = Math.round(d / 30).toInt()
        if (s == 0) s = if (d > 0) 1 else -1
        if (s >= 6) s = 5 else if (s <= -6) s = -5
        return if (s > 0) s else 12 + s
    }
    private fun nujeok(pts: List<DoubleArray>): DoubleArray {
        val c = DoubleArray(pts.size)
        for (i in 1 until pts.size) c[i] = c[i - 1] + Wichi.geori(pts[i - 1][0], pts[i - 1][1], pts[i][0], pts[i][1])
        return c
    }
    private fun gakkaunI(pts: List<DoubleArray>, la: Double, lo: Double): Int {
        var b = 0; var bd = Double.MAX_VALUE
        for (i in pts.indices) { val d = Wichi.geori(pts[i][0], pts[i][1], la, lo); if (d < bd) { bd = d; b = i } }
        return b
    }
    fun apJeom(pts: List<DoubleArray>, k: Int, m: Double, dwiro: Boolean = false): Int {
        var i = k
        while (true) {
            val n = if (dwiro) i - 1 else i + 1
            if (n < 0 || n >= pts.size) return i
            i = n
            if (Wichi.geori(pts[k][0], pts[k][1], pts[i][0], pts[i][1]) >= m) return i
        }
    }
    private fun gakDo(pts: List<DoubleArray>, k: Int): Double? {
        val a = apJeom(pts, k, 8.0, true); val b = apJeom(pts, k, 8.0)
        if (a == k || b == k) return null
        return gakCha(bangwi(pts[a][0], pts[a][1], pts[k][0], pts[k][1]), bangwi(pts[k][0], pts[k][1], pts[b][0], pts[b][1]))
    }
    fun seonGeori(la: Double, lo: Double, pts: List<DoubleArray>): Double {
        if (pts.size == 1) return Wichi.geori(la, lo, pts[0][0], pts[0][1])
        val ky = 111320.0; val kx = 111320.0 * cos(la * Math.PI / 180)
        var best = Double.MAX_VALUE
        for (i in 0 until pts.size - 1) {
            val ax = (pts[i][1] - lo) * kx; val ay = (pts[i][0] - la) * ky
            val bx = (pts[i + 1][1] - lo) * kx; val by = (pts[i + 1][0] - la) * ky
            val dx = bx - ax; val dy = by - ay
            val l2 = dx * dx + dy * dy
            var t = if (l2 > 0) -(ax * dx + ay * dy) / l2 else 0.0
            t = t.coerceIn(0.0, 1.0)
            val x = ax + t * dx; val y = ay + t * dy
            best = minOf(best, sqrt(x * x + y * y))
        }
        return best
    }

    /** 부탁 상세에서 밑그림 듣기 — 출발지 이름으로 자리를 찾고, 못 찾으면 지금 자리에서 */
    fun deutgi(sin: String, mlat: Double, mlon: Double, kkeut: (String) -> Unit) {
        Chatgi.jangso(sin) { j ->
            val f = j?.firstOrNull()
            val s = if (f != null) Pair(f.lat, f.lon) else Wichi.jigeum?.let { Pair(it.lat, it.lon) }
            if (s == null) { kkeut("AI 밑그림을 받지 못했습니다. 출발지를 찾지 못했습니다."); return@jangso }
            batgi(s.first, s.second, mlat, mlon) { m -> kkeut(if (m == null) "AI 밑그림을 받지 못했습니다. 통신이 어렵습니다." else mal(m)) }
        }
    }

    /** 지도에 보낼 것 — 회색 점선 밑그림, 파란 선 걸은 자취, 점 표시, 주황 어긋난 구간, 빨강 반짝임 빠진 꺾임, 걷는 쪽이 위로 */
    fun jidoGeurim(g: JSONObject?, pts: JSONArray, marks: JSONArray, banghyang: Double?): JSONObject {
        val o = JSONObject()
        if (g == null) return o
        g.optJSONObject("mit")?.optJSONArray("pts")?.let { o.put("mit", it) }
        val gil = JSONArray()
        for (i in 0 until pts.length()) { val p = pts.optJSONObject(i) ?: continue; if (p.has("lat") && p.optDouble("acc", 99.0) <= 30) gil.put(JSONArray().put(p.optDouble("lat")).put(p.optDouble("lon"))) }
        o.put("gil", gil)
        val py = JSONArray()
        for (i in 0 until marks.length()) { val m = marks.optJSONObject(i) ?: continue; if (m.has("lat")) py.put(JSONObject().put("lat", m.optDouble("lat")).put("lon", m.optDouble("lon")).put("name", m.optString("name"))) }
        o.put("pyosi", py)
        val eo = JSONArray()
        val ea = g.optJSONArray("mitEotgan") ?: JSONArray()
        for (i in 0 until ea.length()) {
            val e = ea.optJSONObject(i) ?: continue
            val s = JSONArray()
            for (j in 0 until pts.length()) { val p = pts.optJSONObject(j) ?: continue; val st = p.optInt("st"); if (st >= e.optInt("st0") && st <= e.optInt("st1") && p.has("lat")) s.put(JSONArray().put(p.optDouble("lat")).put(p.optDouble("lon"))) }
            eo.put(s)
        }
        o.put("eotgan", eo)
        val pj = JSONArray()
        val pa = g.optJSONArray("mitPajim") ?: JSONArray()
        for (i in 0 until pa.length()) { val p = pa.optJSONObject(i) ?: continue; pj.put(JSONArray().put(p.optDouble("lat")).put(p.optDouble("lon"))) }
        o.put("pajim", pj)
        if (banghyang != null && banghyang >= 0) o.put("h", banghyang)
        return o
    }
}

/** 지도 한 장 — 화면을 다시 그려도 지도는 다시 불러오지 않게 하나를 돌려 씀. 톡백에는 가림 */
object JbJido {
    @SuppressLint("StaticFieldLeak") private var teul: FrameLayout? = null
    @SuppressLint("StaticFieldLeak") private var web: WebView? = null
    private val main = Handler(Looper.getMainLooper())
    private var geurim: (() -> JSONObject)? = null
    private var dollim: Runnable? = null

    fun tokbaek(c: Context): Boolean = try {
        (c.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager).isTouchExplorationEnabled
    } catch (e: Exception) { false }

    @SuppressLint("SetJavaScriptEnabled")
    fun bogi(t: JabongActivity, g: () -> JSONObject) {
        if (!JbMit.jidoKyeojim(t) || tokbaek(t)) return
        geurim = g
        var f = teul
        if (f == null || f.context !== t) {
            val w = WebView(t)
            w.settings.javaScriptEnabled = true
            w.settings.domStorageEnabled = true
            w.isVerticalScrollBarEnabled = false
            w.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            w.webViewClient = object : WebViewClient() { override fun onPageFinished(v: WebView?, u: String?) { bonaegi() } }
            w.loadUrl("https://lvd.ada.or.kr/jabong/jido.php")
            val h = (t.resources.displayMetrics.heightPixels * 0.45).toInt()
            f = FrameLayout(t).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                minimumHeight = h
                addView(w, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h))
            }
            teul = f; web = w
        }
        (f.parent as? ViewGroup)?.removeView(f)
        t.bogi(f)
        if (dollim == null) {
            val r = object : Runnable { override fun run() {
                if (teul?.parent == null) { dollim = null; return }
                bonaegi(); main.postDelayed(this, 1500)
            } }
            dollim = r; main.postDelayed(r, 1500)
        }
    }

    private fun bonaegi() {
        val w = web ?: return
        val d = try { geurim?.invoke() } catch (e: Exception) { null } ?: return
        w.evaluateJavascript("window.jbGeurim && window.jbGeurim($d)", null)
    }
}
