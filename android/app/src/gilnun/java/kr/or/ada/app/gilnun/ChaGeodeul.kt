// 차 안 거들기 — 2.62.0 (261017-A50, 이사장님 승인 2026-10-17 「그러자」, 아이폰 2.92.0 ChaGeodeul.swift 와 같음)
// 웹 길눈(chatta.html·teoneol.js·mumchum.js)에 있었는데 앱으로 옮기지 못했던 차 안 안내 세 가지를 옮김.
//   ① 터널 미리 알림(2026-09-14 이사장님 지시) — 15초 뒤에 닿을 자리의 터널을 이름·길이와 함께. 이름이 없으면 고장 이름을 붙임.
//   ② 차가 크게 돌면(빌드 260906-70) 「방금 3시 방향으로 돌았습니다. ○○까지 ○ 남았습니다」, 조금 뒤 「여기는 ○○입니다」.
//   ③ 차가 멈춰 있을 때(2026-09-27 이사장님 지시) — 까닭을 확실히 알 때만, 모르면 1·2·3·5·10분…째, 다시 움직이면 알림.
// 승용차·택시·콜·버스·고속버스 안에서만 돕니다(기차는 GichaAnnae, 배는 TalgeotJikimi).
package kr.or.ada.app.gilnun

import android.os.Handler
import android.os.Looper
import java.util.Locale

object ChaGeodeul {
    private val main = Handler(Looper.getMainLooper())
    var kyeojim = false
        private set

    fun kkeut() {
        if (!kyeojim) return
        kyeojim = false
        tDat = emptyList(); tJari = null; tHan.clear(); tDeun = null; tWiseongMal = false; tGose.clear()
        pFrom = null; head = null; hSaem.clear(); anjeongBang = null
        mJul.clear(); mum = false; mBunMal.clear()
        main.removeCallbacks(tJikimi); tJikimiDolgo = false
    }

    /** AnnaeEngine.chaAnnae 가 자리마다 부름 */
    fun wichi(w: Jari, y: Yeojeong) {
        kyeojim = true
        gyoJinam(w)
        jinaGyo(w)
        if (mumchumBogo(w, y)) return   // 멈춘 동안은 위성 흔들림을 움직임으로 치지 않음
        doramBoda(w, y)
        teoneolSalpigi(w)
    }

    private fun mal(t: String) {
        Sori.mal(t, MalGeup.ANNAE)
        Girok.namgi("chageodeul_mal", mapOf("mal" to t))
    }

    private fun geori(a1: Double, o1: Double, a2: Double, o2: Double) = Wichi.geori(a1, o1, a2, o2)
    private fun gakCha(a: Double, b: Double): Double { val d = Math.abs(a - b) % 360; return if (d > 180) 360 - d else d }
    private fun f5(v: Double) = String.format(Locale.US, "%.5f", v)
    private fun f6(v: Double) = String.format(Locale.US, "%.6f", v)

    // MARK: ① 터널 미리 알림 — 웹 teoneol.js 1.7

    private data class Teoneol(val ireum: String, val gil: String, val m: Double, val pt: List<Pair<Double, Double>>)
    private var tDat: List<Teoneol> = emptyList()
    private var tJari: Pair<Double, Double>? = null
    private var tBatneun = false
    private var tBatTtae = 0L
    private val tHan = HashSet<String>()
    private var tDeun: Pair<String, Teoneol>? = null
    private var tDeunTtae = 0L
    private var tWiseongMal = false
    private var tMajimak = 0L
    private var tJikimiDolgo = false
    private val tGose = HashMap<String, String>()
    private var ijeonMal = ""
    private var ijeonTtae = 0L

    private val tJikimi: Runnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (tDeun != null && !tWiseongMal && now - tMajimak > 10_000 && now - tDeunTtae < 600_000) {
                tWiseongMal = true
                mal("터널 안이라 위성이 잠시 끊깁니다.")
            }
            if (tJikimiDolgo) main.postDelayed(this, 3000)
        }
    }

    private fun tomakGeori(la: Double, lo: Double, p1: Pair<Double, Double>, p2: Pair<Double, Double>): Double {
        val k = 111320.0; val co = Math.cos(la * Math.PI / 180)
        val x = (lo - p1.second) * k * co; val y = (la - p1.first) * k
        val bx = (p2.second - p1.second) * k * co; val by = (p2.first - p1.first) * k
        val nn = bx * bx + by * by
        val t = if (nn > 0) Math.max(0.0, Math.min(1.0, (x * bx + y * by) / nn)) else 0.0
        val dx = x - bx * t; val dy = y - by * t
        return Math.sqrt(dx * dx + dy * dy)
    }
    private fun julGeori(la: Double, lo: Double, pt: List<Pair<Double, Double>>): Double {
        var m = 1e9
        for (i in 1 until pt.size) m = Math.min(m, tomakGeori(la, lo, pt[i - 1], pt[i]))
        return m
    }
    /** 2.78.0 가장 가까운 토막까지의 거리와 그 토막의 방향 */
    private fun julGeoriBang(la: Double, lo: Double, pt: List<Pair<Double, Double>>): Pair<Double, Double> {
        var m = 1e9; var bang = 0.0
        for (i in 1 until pt.size) {
            val g = tomakGeori(la, lo, pt[i - 1], pt[i])
            if (g < m) { m = g; bang = Jeomjido.bangwi(pt[i - 1].first, pt[i - 1].second, pt[i].first, pt[i].second) }
        }
        return Pair(m, bang)
    }
    private fun giriMal(m: Double): String {
        if (m >= 1000) { val k = Math.round(m / 100) / 10.0; return "길이는 약 ${if (k == Math.floor(k)) k.toInt().toString() else String.format(Locale.US, "%.1f", k)}킬로미터입니다." }
        return "길이는 약 ${Math.round(m / 10) * 10}미터입니다."
    }

    private fun teoneolBatgi(la: Double, lo: Double) {
        val now = System.currentTimeMillis()
        if (tBatneun || now - tBatTtae < 20_000) return
        tBatneun = true; tBatTtae = now
        Tongsin.json("teoneol.php", mapOf("lat" to f5(la), "lon" to f5(lo), "ban" to "3000"), 15000) { o ->
            tBatneun = false
            val rows = o?.optJSONArray("rows") ?: return@json
            val l = ArrayList<Teoneol>()
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val pa = r.optJSONArray("pt") ?: continue
                val pt = ArrayList<Pair<Double, Double>>()
                for (j in 0 until pa.length()) { val p = pa.optJSONArray(j) ?: continue; if (p.length() >= 2) pt.add(Pair(p.optDouble(0), p.optDouble(1))) }
                if (pt.size < 2) continue
                l.add(Teoneol(r.optString("ireum").replace(Regex("\\s*\\(.*\\)\\s*$"), ""), r.optString("gil"), r.optDouble("m", 0.0), pt))
            }
            tDat = l; tJari = Pair(la, lo)
        }
    }

    private fun teoneolSalpigi(w: Jari) {
        tMajimak = System.currentTimeMillis()
        if (!tJikimiDolgo) { tJikimiDolgo = true; main.postDelayed(tJikimi, 3000) }
        val tj = tJari
        if (tDat.isEmpty() || tj == null || geori(tj.first, tj.second, w.lat, w.lon) > 1500) teoneolBatgi(w.lat, w.lon)
        if (tDat.isEmpty()) return
        val s = Math.max(0.0, w.sokdo)
        var munteok = s * 15
        munteok = when {
            s < 3 -> Math.max(40.0, Math.min(150.0, if (munteok > 0) munteok else 40.0))
            s > 42 -> Math.max(700.0, Math.min(2500.0, munteok))
            else -> Math.max(150.0, Math.min(700.0, munteok))
        }
        data class Ap(val t: Teoneol, val de: Double, val key: String)
        val ap = ArrayList<Ap>()
        for (r in tDat) {
            val (jul, julBang) = julGeoriBang(w.lat, w.lon, r.pt)
            val a = r.pt[0]; val b = r.pt[r.pt.size - 1]
            val da = geori(w.lat, w.lon, a.first, a.second); val db = geori(w.lat, w.lon, b.first, b.second)
            val mun = if (da <= db) a else b; val de = Math.min(da, db)
            val key = "${r.ireum}_${String.format(Locale.US, "%.4f", mun.first)}"
            if (jul < 25) {
                // 2.78.0 (261026-A66) 터널 길과 같은 쪽으로 달릴 때만 터널 안으로 침 — 위나 옆을 가로지를 때는 아님
                val hh = head
                if (hh != null && gakCha(hh, julBang) > 40 && gakCha(hh, julBang + 180) > 40) continue
                if (tDeun?.first != key) { tDeun = Pair(key, r); tDeunTtae = System.currentTimeMillis(); tWiseongMal = false }
                continue
            }
            // 2.78.0 (261026-A66, 이사장님 승인 2026-10-26 「1」, 아이폰 2.113.0과 같음) 차가 그 터널 길 위를 터널 쪽으로 달릴 때만 미리 알림 —
            //   터널 입구에서 안쪽으로 향하는 방향과 차의 진행 방향이 40도 안이고, 입구에서 뻗은 터널 줄에서 옆으로 40미터 안일 때
            val h = head ?: continue
            if (gakCha(h, Jeomjido.bangwi(w.lat, w.lon, mun.first, mun.second)) >= 65) continue
            val an = if (da <= db) r.pt[1] else r.pt[r.pt.size - 2]
            val anjjok = Jeomjido.bangwi(mun.first, mun.second, an.first, an.second)
            if (gakCha(h, anjjok) > 40) continue
            val yeop = de * Math.sin(gakCha(Jeomjido.bangwi(w.lat, w.lon, mun.first, mun.second), anjjok) * Math.PI / 180)
            if (yeop > 40) continue
            if (de > munteok) continue
            ap.add(Ap(r, de, key))
        }
        val dn = tDeun
        if (dn != null && julGeori(w.lat, w.lon, dn.second.pt) > 80) {
            tDeun = null; tWiseongMal = false
            if (System.currentTimeMillis() - tDeunTtae > 3000) mal("터널을 지났습니다.")
        }
        if (ap.isEmpty()) return
        ap.sortBy { it.de }
        val ga = ap[0]
        if (tHan.contains(ga.key)) return
        var iut = 0
        for (k in ap.drop(1)) {
            val teum = k.de - ga.de
            if (teum < 150) { tHan.add(k.key); continue }
            if (teum < 900) { iut++; tHan.add(k.key) }
        }
        tHan.add(ga.key)
        val ttn = ga.t.ireum; val gl = ga.t.gil; val m = ga.t.m
        val bureum = if (ttn.isNotEmpty()) ttn else if (gl.isNotEmpty()) "$gl 터널" else "이름 없는 터널"
        val t = when {
            iut > 0 -> "곧 ${bureum}입니다. 터널이 ${iut + 1}개 이어집니다."
            m < 300 -> "곧 $bureum, 짧습니다."
            else -> "곧 ${bureum}입니다. ${giriMal(m)}"
        }
        if (ttn.isEmpty() && m >= 200 && !tGose.containsKey(ga.key)) {
            tGose[ga.key] = ""
            val p0 = ga.t.pt[0]
            Tongsin.json("jeom.php", mapOf("a" to "jimyeong", "lat" to f6(p0.first), "lon" to f6(p0.second))) { o ->
                val got = gotChatgi(o?.optString("juso")?.takeIf { it.isNotEmpty() } ?: o?.optString("jibeon") ?: "")
                tGose[ga.key] = got
                malHaji(if (got.isEmpty()) t else "곧 ${bureum}입니다. ${got}입니다. " + (if (m < 300) "짧습니다." else giriMal(m)))
            }
            return
        }
        malHaji(t)
    }

    private fun malHaji(t: String) {
        val now = System.currentTimeMillis()
        if (t == ijeonMal && now - ijeonTtae < 3000) return
        ijeonMal = t; ijeonTtae = now
        mal(t)
    }

    private fun gotChatgi(juso: String): String {
        var si = ""; var eup = ""
        for (j in juso.split(Regex("\\s+"))) {
            if ((j.endsWith("시") || j.endsWith("군")) && !(j.endsWith("특별시") || j.endsWith("광역시") || j.endsWith("특별자치시"))) si = j
            else if (si.isNotEmpty() && eup.isEmpty() && (j.endsWith("읍") || j.endsWith("면") || j.endsWith("동"))) eup = j
        }
        return if (si.isNotEmpty() && eup.isNotEmpty()) "$si $eup" else si
    }

    // MARK: ② 차가 크게 돌면 — 웹 chatta.html 빌드 260906-70·260910-2

    // 2.78.0 (261026-A66, 이사장님 승인 2026-10-26 「1」, 아이폰 2.113.0과 같음) 돈 방향은 폰이 재 주는 차의 진행 방향으로 셈하고(오차 20미터 안 자리만),
    //   돈 뒤 방향이 3초 가까이 한결같을 때만 「돌기 전」 방향과 견주어 말함. 오락가락하면 말하지 않음.
    //   (10-26 시내에서 2시 방향으로 꺾었는데 11시 방향이라 하고 오락가락하던 일 — 오차 50미터 자리를 15미터마다 이어 방향을 재고, 흔들림 가운데 더 크게 돈 쪽을 골랐던 탓)
    private var pFrom: Pair<Double, Double>? = null
    private var head: Double? = null
    private val hSaem = ArrayList<Pair<Long, Double>>()
    private var anjeongBang: Double? = null
    private var doraTtae = 0L

    private fun wonPyeong(l: List<Double>): Double {
        var x = 0.0; var y = 0.0
        for (h in l) { x += Math.cos(h * Math.PI / 180); y += Math.sin(h * Math.PI / 180) }
        val d = Math.atan2(y, x) * 180 / Math.PI
        return if (d < 0) d + 360 else d
    }

    private fun doramBoda(w: Jari, y: Yeojeong) {
        if (w.ochae <= 0 || w.ochae > 20 || w.georeumChu || w.jjimjak) return
        val ije = System.currentTimeMillis()
        var bang: Double? = null
        var bangsik = "jinhaeng"
        val pf = pFrom
        if (w.sokdo > 3 && w.jinhaeng >= 0) {
            bang = w.jinhaeng           // 폰이 재 주는 진행 방향(빠르기로 잼 — 건물 사이에서도 덜 흔들림)
            pFrom = Pair(w.lat, w.lon)
        } else if (pf != null) {
            if (geori(pf.first, pf.second, w.lat, w.lon) >= 40) {   // 진행 방향이 없으면 40미터 넘게 움직인 두 자리로
                bang = Jeomjido.bangwi(pf.first, pf.second, w.lat, w.lon); bangsik = "jari"
                pFrom = Pair(w.lat, w.lon)
            }
        } else {
            pFrom = Pair(w.lat, w.lon)
        }
        val b = bang ?: return
        head = b   // 터널 미리 알림도 이 방향을 씀
        hSaem.add(Pair(ije, b))
        hSaem.removeAll { ije - it.first > 4000 }
        val cheot = hSaem.firstOrNull() ?: return
        if (ije - cheot.first < 2500 || hSaem.size < 3) return
        val pyeong = wonPyeong(hSaem.map { it.second })
        if (!hSaem.all { gakCha(it.second, pyeong) <= 15 }) return   // 아직 도는 중이거나 흔들림
        val jeon = anjeongBang
        if (jeon == null) { anjeongBang = pyeong; return }
        val deul = ((pyeong - jeon + 540) % 360) - 180
        anjeongBang = pyeong   // 천천히 굽는 길은 따라감
        if (Math.abs(deul) < 35 || ije - doraTtae <= 10_000) return
        doraTtae = ije
        hSaem.clear()
        var sh = (Math.round((((deul % 360) + 360) % 360) / 30).toInt()) % 12
        if (sh == 0) sh = 12
        val d = geori(w.lat, w.lon, y.mokjeok.lat, y.mokjeok.lon)
        Girok.namgi("cha_doram", mapOf("jeon" to jeon.toInt(), "hu" to pyeong.toInt(), "deul" to deul.toInt(), "si" to sh,
            "ochae" to w.ochae.toInt(), "sokdo" to (w.sokdo * 3.6).toInt(), "bangsik" to bangsik))
        mal("방금 " + (if (Math.abs(deul) > 120) "크게 돌아 " else "") + "${sh}시 방향으로 돌았습니다. ${y.mokjeok.ireum}까지 ${Annae.geoMal(d)} 남았습니다.")
        Tongsin.json("jarimal.php", mapOf("lat" to f6(w.lat), "lon" to f6(w.lon))) { o ->
            val m = o?.optString("mal") ?: ""
            if (m.isNotEmpty()) mal("여기는 " + m + (if (m.endsWith("다.") || m.endsWith("다")) "" else "입니다."))
        }
    }

    // MARK: ③ 차가 멈춰 있을 때 — 웹 mumchum.js 1.0

    private data class Mj(val lat: Double, val lon: Double, val t: Long, val sp: Double?)
    private val mJul = ArrayList<Mj>()
    private var mum = false
    private var mumT = 0L
    private var dasiSu = 0
    private var kkadakMal = false
    private var kkadakCh = false
    private val mBunMal = HashSet<Int>()
    // 2.88.0 (261102, 이사장님 지시 2026-11-02 「교차로에서 멈추면 그 명칭도」) 멈춘 까닭 가리기(아이폰 2.123.0과 같음)
    private var sinhoMum = false
    private var oraeMal = false
    private class Gyo(val la: Double, val lo: Double, val ireum: String, var gakkai: Boolean)
    private var gyoGiok: Gyo? = null
    private val mumGirok = ArrayList<Long>()
    private class Jin(val la: Double, val lo: Double, val t: Long)
    private val jinJul = ArrayList<Jin>()
    private var makimMalT = 0L

    private fun mumchumBogo(w: Jari, y: Yeojeong): Boolean {
        val ije = System.currentTimeMillis()
        val acc = if (w.ochae > 0) w.ochae else 10.0
        val sp: Double? = if (w.sokdo >= 0) w.sokdo else null
        mJul.add(Mj(w.lat, w.lon, ije, sp))
        mJul.removeAll { ije - it.t > 15_000 }
        if (!mum) {
            val gidal = if (sp != null) 4000L else 8000L
            val sij = mJul.filter { ije - it.t <= gidal + 2000 }
            if (sij.size >= 3 && ije - sij[0].t >= gidal) {
                val f = sij[0]
                val seoNa = if (sp != null) sij.all { (it.sp ?: 99.0) < 0.8 } else sij.all { geori(it.lat, it.lon, f.lat, f.lon) <= Math.max(8.0, acc) }
                if (seoNa) { mum = true; mumT = f.t; dasiSu = 0; kkadakMal = false; kkadakCh = false; mBunMal.clear(); sinhoMum = false; oraeMal = false; mumGirok.add(f.t); makimBoda(ije) }
            }
            return false
        }
        val cheot = mJul.firstOrNull { it.t >= mumT } ?: Mj(w.lat, w.lon, ije, sp)
        val umjik = (sp ?: 0.0) > 2 || geori(w.lat, w.lon, cheot.lat, cheot.lon) > Math.max(20.0, acc * 1.5)
        dasiSu = if (umjik) dasiSu + 1 else 0
        if (dasiSu >= 2) {
            val gil = ije - mumT
            mum = false; mJul.clear()
            if (gil >= 20_000) mal("다시 움직입니다.")
            sinhoMum = false
            return false
        }
        if (ije - mumT >= 10_000) kkadakChatgi(w.lat, w.lon, y.talgeot == Talgeot.BEOSEU)
        if (sinhoMum && !oraeMal && ije - mumT >= 180_000) { oraeMal = true; mal("신호보다 오래 서 있습니다. 차가 밀리는 것 같습니다.") }
        val bun = ((ije - mumT) / 60_000).toInt()
        // 자세히(차 길 맞춤)에서는 차 길 맞춤이 「○분째 서 있습니다」를 말하므로 까닭만 말함
        val chaMatMal = y.talgeot == Talgeot.CHA && ChaMat.jeongdo >= 3
        if (!chaMatMal && !kkadakMal && bun >= 1 && (bun <= 3 || bun % 5 == 0) && !mBunMal.contains(bun)) {
            mBunMal.add(bun)
            mal("${bun}분째 멈춰 있습니다.")
        }
        return true
    }

    private fun kkadakChatgi(la: Double, lo: Double, beoseu: Boolean) {
        if (kkadakCh) return
        kkadakCh = true
        val h = head
        // 2.88.0 (261102, 이사장님 승인 「2」) 서울은 실시간 도로 속도로 막힘을 가림(서울 열린데이터 TOPIS) — 먼저 속도를 묻고 까닭을 셈
        var gilMal = ""
        val deo = { if (gilMal.isEmpty()) "" else " $gilMal" }
        val sagoriBoda = {
            Tongsin.json("neagori.php", mapOf("lat" to f5(la), "lon" to f5(lo), "ban" to "80")) { o ->
                val r = o?.optJSONArray("rows")?.optJSONObject(0)
                var sagori = false
                if (r != null) {
                    sagori = if (r.has("meter")) r.optDouble("meter", 999.0) <= 30 else geori(la, lo, r.optDouble("lat"), r.optDouble("lon")) <= 30
                }
                if (mum && !kkadakMal && sagori) { kkadakMal = true; mal("사거리 앞에 멈춰 있습니다." + deo()) }
                else if (mum && !kkadakMal && gilMal.isNotEmpty()) { kkadakMal = true; mal("멈춰 있습니다." + deo()) }
            }
        }
        // 2.88.0 (261102, 이사장님 지시) 교차로 이름·몇 갈래·신호등 — 오픈스트리트맵(jido.php gal=gyo), 자료가 아직 없으면 예전대로
        val gyoBoda = {
            if (!AnnaeSeoljeong.jidoOn) sagoriBoda()
            else Tongsin.json("jido.php", mapOf("gal" to "gyo", "lat" to f5(la), "lon" to f5(lo), "ban" to "90"), 15000) { o ->
                if (o == null) { sagoriBoda(); return@json }
                val jaryo = o.optInt("deo", 0) != 1
                var best: org.json.JSONObject? = null; var bestD = 1e9
                val arr = o.optJSONArray("gyo")
                if (arr != null) for (i in 0 until arr.length()) {
                    val r = arr.optJSONObject(i) ?: continue
                    val d = geori(la, lo, r.optDouble("a"), r.optDouble("o"))
                    if (d > 70) continue
                    if (h != null && d > 25) { val c = Math.abs(((Jeomjido.bangwi(la, lo, r.optDouble("a"), r.optDouble("o")) - h + 540) % 360) - 180); if (c > 70) continue }
                    if (d < bestD) { best = r; bestD = d }
                }
                val b = best
                if (b != null) {
                    val ireum = gyoIreum(b.optString("n"), b.optInt("gal", 0))
                    val sinho = b.optInt("s", 0) == 1
                    if (mum && !kkadakMal) {
                        kkadakMal = true; sinhoMum = sinho
                        mal((if (sinho) "$ireum 앞에서 신호를 기다리는 것 같습니다." else "$ireum 앞에 멈춰 있습니다.") + deo())
                        gyoGiok = Gyo(b.optDouble("a"), b.optDouble("o"), ireum, false)
                    }
                } else if (jaryo) {
                    if (mum && !kkadakMal) { kkadakMal = true; mal(if (gilMal.isEmpty()) "교차로나 정류장이 아닌 곳입니다. 차가 밀려 멈춘 것 같습니다." else "교차로나 정류장이 아닌 곳에 멈춰 있습니다." + deo()) }
                } else sagoriBoda()
            }
        }
        val sijak = {
            if (!beoseu) gyoBoda()
            else Beoseu.gakkaun(la, lo) { l ->
                val j0 = l?.firstOrNull()
                if (j0 != null && j0.ireum.isNotEmpty() && geori(la, lo, j0.lat, j0.lon) <= 30) {
                    if (mum && !kkadakMal) { kkadakMal = true; mal("${j0.ireum} 정류장에 멈춰 있습니다." + deo()) }
                } else gyoBoda()
            }
        }
        if (!AnnaeSeoljeong.jidoOn) { sijak(); return }
        Tongsin.json("jido.php", mapOf("gal" to "sokdo", "lat" to f5(la), "lon" to f5(lo), "bang" to (h?.toInt()?.toString() ?: "")), 10000) { so ->
            if (so != null && so.optInt("eopseum", 0) != 1 && so.has("sokdo")) gilMal = sokdoMal(so.optInt("sokdo"), so.optString("gil"), so.optString("e"))
            sijak()
        }
    }

    /** 서울 실시간 속도 말 — 시속 15 아래 막힘, 25 아래 천천히, 그 위 잘 빠짐 */
    fun sokdoMal(sd: Int, gil: String, e: String): String {
        val jeongdo = if (sd < 15) "막히고 있습니다" else if (sd < 25) "천천히 가고 있습니다" else "잘 빠지고 있습니다"
        val eodi = if (gil.isEmpty()) "이 길" else if (e.isEmpty()) gil else "$gil $e 쪽"
        return "${JidoAllim.eun(eodi)} 지금 시속 ${sd}킬로미터로 $jeongdo."
    }

    /** 「서울역교차로」·「강남역사거리」는 그대로, 이름이 없으면 몇 갈래인지로 */
    fun gyoIreum(n: String, gal: Int): String {
        val moyang = when (gal) { 3 -> "삼거리"; 4 -> "사거리"; 5 -> "오거리"; 6 -> "육거리"; else -> "교차로" }
        val nm = n.trim()
        if (nm.isEmpty()) return "이름 없는 $moyang"
        if (gyoIreumIm(nm)) return nm
        return "$nm $moyang"
    }
    /** 이름 자체가 교차로 이름인지(○○사거리·○○교차로·○○네거리·○○나들목·○○IC 등) — 2.89.0 */
    fun gyoIreumIm(nm: String): Boolean = listOf("거리", "교차로", "나들목", "분기점", "로터리", "IC", "JC", "요금소").any { nm.endsWith(it) }

    // 2.89.0 (261102, 이사장님 승인 「1」 4번, 아이폰 2.124.0과 같음) 지나는 교차로 — 이름이 교차로 이름인 공식 교차로만,
    //   30미터 안에 들었다가 45미터 넘게 멀어지면 「○○를 지났습니다. 다음은 ○○입니다」 한 번(다음은 가는 쪽 20도 안, 700미터 안).
    //   말과 말 사이 15초 넘게, 같은 교차로는 10분 안에 다시 말하지 않음. 멈췄던 교차로는 gyoJinam 이 말함.
    private class Jg(val la: Double, val lo: Double, val n: String)
    private var jgGyo: List<Jg> = emptyList()
    private var jgJari: Pair<Double, Double>? = null
    private var jgBatTtae = 0L
    private var jgBatneun = false
    private val jgHan = HashMap<String, Long>()
    private var jgGakkai: Jg? = null
    private var jgMalTtae = 0L

    private fun jinaGyo(w: Jari) {
        if (!AnnaeSeoljeong.jidoOn || !AnnaeSeoljeong.gyoJinaOn || mum) return
        if (w.sokdo >= 0 && w.sokdo < 2) return
        val jr = jgJari
        if (jr == null || geori(w.lat, w.lon, jr.first, jr.second) > 250) jgBatgi(w.lat, w.lon)
        val ije = System.currentTimeMillis()
        jgHan.entries.removeAll { ije - it.value > 600_000 }
        val g = jgGakkai
        if (g != null) {
            if (geori(w.lat, w.lon, g.la, g.lo) <= 45) return
            jgGakkai = null
            val key = "${g.n}|${(g.la * 1000).toInt()}|${(g.lo * 1000).toInt()}"
            if (jgHan.containsKey(key)) return
            jgHan[key] = ije
            if (gyoGiok?.ireum == g.n) return
            if (ije - jgMalTtae < 15_000) return
            var t = "${JidoAllim.eul(g.n)} 지났습니다."
            val h = head
            if (h != null) {
                var best: Jg? = null; var bd = 1e9
                for (x in jgGyo) {
                    if (x.n == g.n) continue
                    val d = geori(w.lat, w.lon, x.la, x.lo)
                    if (d < 60 || d > 700) continue
                    val c = Math.abs(((Jeomjido.bangwi(w.lat, w.lon, x.la, x.lo) - h + 540) % 360) - 180)
                    if (c > 20) continue
                    if (d < bd) { bd = d; best = x }
                }
                if (best != null) t += " 다음은 ${best.n}입니다."
            }
            jgMalTtae = ije
            mal(t)
            return
        }
        for (x in jgGyo) {
            if (geori(w.lat, w.lon, x.la, x.lo) >= 30) continue
            val key = "${x.n}|${(x.la * 1000).toInt()}|${(x.lo * 1000).toInt()}"
            if (!jgHan.containsKey(key)) { jgGakkai = x; break }
        }
    }

    private fun jgBatgi(la: Double, lo: Double) {
        val ije = System.currentTimeMillis()
        if (jgBatneun || ije - jgBatTtae < 20_000) return
        jgBatneun = true; jgBatTtae = ije
        Tongsin.json("jido.php", mapOf("gal" to "gyo", "lat" to f5(la), "lon" to f5(lo), "ban" to "500"), 15000) { o ->
            jgBatneun = false
            if (o == null) return@json
            val l = ArrayList<Jg>()
            val arr = o.optJSONArray("gyo")
            if (arr != null) for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                val n = r.optString("n").trim()
                if (r.optString("geungeo") != "pyojun" || !gyoIreumIm(n)) continue
                l.add(Jg(r.optDouble("a"), r.optDouble("o"), n))
            }
            jgGyo = l
            if (o.optInt("deo", 0) != 1) jgJari = Pair(la, lo)
        }
    }

    /** 멈췄던 교차로를 지나면 한 번 알림 */
    private fun gyoJinam(w: Jari) {
        val ije = System.currentTimeMillis()
        jinJul.add(Jin(w.lat, w.lon, ije)); jinJul.removeAll { ije - it.t > 300_000 }
        val g = gyoGiok ?: return
        if (mum) return
        val d = geori(w.lat, w.lon, g.la, g.lo)
        if (d < 30) { g.gakkai = true; return }
        if (g.gakkai && d > 45) { gyoGiok = null; mal("${JidoAllim.eul(g.ireum)} 지났습니다."); return }
        if (d > 400) gyoGiok = null
    }

    /** 5분 안에 세 번 넘게 서고 800미터도 못 갔으면 길이 막히는 것 */
    private fun makimBoda(ije: Long) {
        mumGirok.removeAll { ije - it > 300_000 }
        val f = jinJul.firstOrNull() ?: return
        if (mumGirok.size < 3 || ije - makimMalT < 900_000 || ije - f.t < 240_000) return
        var m = 0.0
        for (i in 1 until jinJul.size) m += geori(jinJul[i - 1].la, jinJul[i - 1].lo, jinJul[i].la, jinJul[i].lo)
        if (m < 800) { makimMalT = ije; mal("길이 막히고 있습니다. 5분 동안 ${mumGirok.size}번 섰습니다.") }
    }
}
