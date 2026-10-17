// 철길 자료와 선로 맞추기 — 2.68.0 (261018-A56, 이사장님 승인 2026-10-17 「1」, 아이폰 2.99.0 Cheolgil.swift 와 같은 셈법)
// 기차 위치를 지도 위의 점으로만 보다가, 바로 옆에 나란히 놓인 다른 노선의 터널(서울역 앞 740미터)을
// 타신 기차의 터널로 알리던 일을 뿌리부터 막음.
//   앱 안에 실은 우리나라 철길 자료(assets/cheolgil.bin, 오픈스트리트맵, tools/cheolgil/mandeulgi.py)로
//   ① 위치를 가장 그럴듯한 철길 선 위에 붙이고(선로 맞추기 — 거리·가는 쪽·앞서 맞춘 선로와 이어지는지)
//   ② 그 선로를 가는 쪽으로 따라가며(갈림길에서는 가장 덜 꺾이는 쪽) 앞에 놓인 터널만 찾음.
// 터널·산골에서는 인터넷이 끊기므로 자료는 앱 안에 둠.
package kr.or.ada.app.gilnun

import kotlin.math.*

class Cheolgil private constructor(b: ByteArray) {
    companion object {
        /** 자료 읽기 — 앱은 GilnunActivity 가 assets 에서, 시험(tests/gicha)은 파일에서 */
        var ilgeum: () -> ByteArray? = { null }
        private var bon: Cheolgil? = null
        private var ilgeotna = false
        /** 처음 부를 때 한 번 읽음(자료가 없으면 null — 그때는 예전 방식으로 안내) */
        val shared: Cheolgil?
            get() {
                if (!ilgeotna) { ilgeotna = true; bon = try { ilgeum()?.let { mandeulgi(it) } } catch (e: Exception) { null } }
                return bon
            }
        private fun mandeulgi(b: ByteArray): Cheolgil? =
            if (b.size > 12 && b[0] == 0x43.toByte() && b[1] == 0x48.toByte() && b[2] == 0x47.toByte() && b[3] == 0x31.toByte()) Cheolgil(b).takeIf { it.jul.isNotEmpty() } else null
        private const val KAN_LA = 0.005
        private const val KAN_LO = 0.006
        private fun kanKi(a: Double, o: Double): Long = (floor(a / KAN_LA).toLong() shl 32) or (floor(o / KAN_LO).toLong() and 0xffffffffL)
        fun m(a1: Double, o1: Double, a2: Double, o2: Double): Double {
            val c = cos((a1 + a2) / 2 * PI / 180)
            return hypot((o2 - o1) * 111320 * c, (a2 - a1) * 110540)
        }
        fun bangwi(a1: Double, o1: Double, a2: Double, o2: Double): Double {
            val c = cos((a1 + a2) / 2 * PI / 180)
            val d = atan2((o2 - o1) * c, a2 - a1) * 180 / PI
            return if (d < 0) d + 360 else d
        }
        fun gakCha(a: Double, b: Double) = abs(((a - b + 540) % 360) - 180)
    }

    class Jul(val pyo: Int, val ireum: String, val jeom: IntArray)
    class Matchum(val jul: Int, val i: Int, val t: Double, val bang: Int, val geori: Double, val lat: Double, val lon: Double)
    class Teoneol(val sijak: Double, val gil: Double, val ireum: String, val jul: List<Int>)

    val la: DoubleArray
    val lo: DoubleArray
    val jul = ArrayList<Jul>()
    private val ieum = HashMap<Int, MutableList<IntArray>>()   // 두 줄 넘게 쓰는 점 → (줄, 자리)
    private val kan = HashMap<Long, MutableList<Long>>()        // 칸 → (줄 shl 20 | 토막)

    init {
        var p = 4
        fun u8(): Int { val v = b[p].toInt() and 0xff; p += 1; return v }
        fun u16(): Int { val v = (b[p].toInt() and 0xff) or ((b[p + 1].toInt() and 0xff) shl 8); p += 2; return v }
        fun i32(): Int { val v = (b[p].toInt() and 0xff) or ((b[p + 1].toInt() and 0xff) shl 8) or ((b[p + 2].toInt() and 0xff) shl 16) or ((b[p + 3].toInt() and 0xff) shl 24); p += 4; return v }
        val nj = i32(); val nl = i32()
        la = DoubleArray(nj); lo = DoubleArray(nj)
        for (k in 0 until nj) { la[k] = i32() / 1e6; lo[k] = i32() / 1e6 }
        val sseum = IntArray(nj)
        for (q in 0 until nl) {
            if (p + 2 > b.size) break
            val pyo = u8(); val nb = u8()
            if (p + nb + 2 > b.size) break
            val ir = String(b, p, nb, Charsets.UTF_8); p += nb
            val n = u16()
            if (p + n * 4 > b.size) break
            val js = ArrayList<Int>(n)
            for (k in 0 until n) { val v = i32(); if (v in 0 until nj) js.add(v) }
            if (js.size < 2) continue
            for (v in js) if (sseum[v] < 2) sseum[v]++
            jul.add(Jul(pyo, ir, js.toIntArray()))
        }
        for ((ji, j) in jul.withIndex()) {
            for ((k, v) in j.jeom.withIndex()) if (sseum[v] > 1 || k == 0 || k == j.jeom.size - 1) ieum.getOrPut(v) { ArrayList() }.add(intArrayOf(ji, k))
            for (i in 0 until j.jeom.size - 1) {
                val a = j.jeom[i]; val c = j.jeom[i + 1]
                val n = max(1, (m(la[a], lo[a], la[c], lo[c]) / 250).toInt())
                val deun = HashSet<Long>()
                for (s in 0..n) { val f = s.toDouble() / n; deun.add(kanKi(la[a] + (la[c] - la[a]) * f, lo[a] + (lo[c] - lo[a]) * f)) }
                for (k in deun) kan.getOrPut(k) { ArrayList() }.add((ji.toLong() shl 20) or i.toLong())
            }
        }
    }

    /** 선로 맞추기 — bang: 가는 쪽, ochae: 위치 오차(미터), ireo: 앞서 맞춘 줄과 그 앞으로 이어질 줄들 */
    fun matchugi(a: Double, o: Double, bang: Double, ochae: Double, ireo: Set<Int>): Matchum? {
        val r = max(35.0, min(ochae, 60.0) + 25)
        val c = cos(a * PI / 180)
        val k0a = floor(a / KAN_LA).toLong(); val k0o = floor(o / KAN_LO).toLong()
        val bon = HashSet<Long>()
        var best: Matchum? = null; var bj = 0.0
        for (da in -1..1) for (dob in -1..1) {
            val l = kan[((k0a + da) shl 32) or ((k0o + dob) and 0xffffffffL)] ?: continue
            for (s in l) {
                if (!bon.add(s)) continue
                val ji = (s shr 20).toInt(); val i = (s and 0xfffff).toInt()
                val j = jul[ji]
                val p1 = j.jeom[i]; val p2 = j.jeom[i + 1]
                val ax = (lo[p1] - o) * 111320 * c; val ay = (la[p1] - a) * 110540
                val bx = (lo[p2] - o) * 111320 * c; val by = (la[p2] - a) * 110540
                val dx = bx - ax; val dy = by - ay; val l2 = dx * dx + dy * dy
                val t = if (l2 == 0.0) 0.0 else max(0.0, min(1.0, -(ax * dx + ay * dy) / l2))
                val g = hypot(ax + t * dx, ay + t * dy)
                if (g > r) continue
                val dd = gakCha(bang, bangwi(la[p1], lo[p1], la[p2], lo[p2]))
                val bg = if (dd <= 90) 1 else -1
                val gak = min(dd, 180 - dd)
                if (gak > 40) continue
                var jeom = g + gak * 1.5
                if (j.pyo and 4 != 0) jeom += 15
                if (ireo.contains(ji)) jeom -= 20
                if (best == null || jeom < bj) {
                    best = Matchum(ji, i, t, bg, g, la[p1] + (la[p2] - la[p1]) * t, lo[p1] + (lo[p2] - lo[p1]) * t); bj = jeom
                }
            }
        }
        return best
    }

    /** 맞춘 자리에서 가는 쪽으로 철길을 따라감 — 앞에 놓인 터널과 지나갈 줄들 */
    fun ttaragagi(mt: Matchum, gil: Double): Pair<List<Teoneol>, Set<Int>> {
        val tn = ArrayList<Teoneol>()
        var jS = 0.0; var jE = 0.0; var jIr = ""; var jJul: MutableList<Int>? = null
        val gan = HashSet<Int>(); gan.add(mt.jul)
        var ji = mt.jul; var d = mt.bang
        var k = if (d > 0) mt.i + 1 else mt.i
        var pa = mt.lat; var po = mt.lon
        var s = 0.0
        for (step in 0 until 400) {
            val j = jul[ji]
            val v = j.jeom[k]
            val l = m(pa, po, la[v], lo[v])
            val h0 = bangwi(pa, po, la[v], lo[v])
            if (j.pyo and 1 != 0) {
                val jj = jJul
                if (jj != null && s - jE < 40) { jE = s + l; if (!jj.contains(ji)) jj.add(ji); if (jIr.isEmpty()) jIr = j.ireum }
                else {
                    if (jj != null) tn.add(Teoneol(jS, jE - jS, jIr, jj))
                    jS = s; jE = s + l; jIr = j.ireum; jJul = mutableListOf(ji)
                }
            }
            s += l
            pa = la[v]; po = lo[v]
            if (s >= gil && (jJul == null || s - jE > 40)) break
            if (s > gil + 30000) break
            // 다음 길 고르기 — 이 점에서 나가는 모든 길 가운데 가장 덜 꺾이는 쪽(35도 안)
            val nae = ArrayList<IntArray>()
            val kk = k + d
            if (kk >= 0 && kk < j.jeom.size) nae.add(intArrayOf(ji, kk, d))
            ieum[v]?.let { gt ->
                for (e in gt) {
                    val oj = e[0]; val ok = e[1]
                    if (oj == ji && ok == k) continue
                    if (ok + 1 < jul[oj].jeom.size) nae.add(intArrayOf(oj, ok + 1, 1))
                    if (ok - 1 >= 0) nae.add(intArrayOf(oj, ok - 1, -1))
                }
            }
            var gol: IntArray? = null; var gGak = 0.0
            for (e in nae) {
                val w = jul[e[0]].jeom[e[1]]
                val gk = gakCha(h0, bangwi(la[v], lo[v], la[w], lo[w]))
                if (gk > 35) continue
                if (gol == null || gk < gGak) { gol = e; gGak = gk }
            }
            val g = gol ?: break
            gan.add(g[0])
            ji = g[0]; k = g[1]; d = g[2]
        }
        jJul?.let { tn.add(Teoneol(jS, jE - jS, jIr, it)) }
        return Pair(tn, gan)
    }
}
