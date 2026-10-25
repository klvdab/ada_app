// 미리 걸어 보기 — 2.65.0 (261017-A53, 이사장님 승인 2026-10-17 「그러자」, 웹 길눈 mirigeot.html 1.0에서 옮김, 아이폰 2.95.0과 같음)
// 점지도를 나서기 전에 한 마디씩 들어 봅니다(가상체험, 2026-09-08 이사장님 발안).
//   ① 저장된 길의 표시를 걸음 마디로 풀어 「곧게 몇 걸음 갑니다」, 「3시 방향으로 꺾습니다」처럼 한 마디씩
//   ② 거꾸로(돌아오는 길로) 걸어 보기 — 따라 걷기와 같은 뜻 뒤집기(JeomGil.dwit)
//   ③ 요약만 듣기 — 꺾임·계단·건널목·턱·승강기·문만
//   방향은 늘 시계 방향으로 말함. 걸음 수는 내 보폭으로(재지 않으셨으면 미터로)
package kr.or.ada.app.gilnun

import android.os.Handler
import android.os.Looper
import android.view.View

class MiriMadi(val jong: String, val geul: String, val m: Double, val jungyo: Boolean)

object MiriGeotgi {
    /** 내 보폭 — 지금 쓰는 쪽, 없으면 다른 쪽. 재지 않으셨으면 null */
    val bopok: Double?
        get() {
            val s = Seoljeong
            val a = if (s.bopokMode == "dongban") s.bopokDongban else s.bopokHonja
            val b = if (s.bopokMode == "dongban") s.bopokHonja else s.bopokDongban
            val v = if (a > 0.2) a else b
            return if (v > 0.2 && v < 1.5) v else null
        }

    fun georiMal(m: Double): String {
        val b = bopok
        val mt = maxOf(1, Math.round(m).toInt())
        return if (b != null) "${maxOf(1, Math.round(m / b).toInt())}걸음, 약 ${mt}미터" else "약 ${mt}미터"
    }

    fun jjok(nm: String): String {
        Regex("(1[0-2]|[1-9])시").find(nm)?.let { return it.value }
        return if (nm.contains("오른") || nm.contains("우회전")) "3시" else "9시"
    }

    fun jungyo(nm: String): Boolean =
        Regex("꺾임|굽은 길 시작|계단|횡단보도|건널목|오름턱|내림턱|엘리베이터|에스컬레이터|문|입구|개찰구|되돌아가는 곳|낙상").containsMatchIn(nm)

    fun pyoMal(p: JeomPyo, kilyi: Double?): String {
        val nm = p.ireum
        val kkori = if ((kilyi ?: 0.0) > 1) " ${georiMal(kilyi!!)}입니다." else ""
        val cnt = if ((p.cnt ?: 0) > 0) " ${p.cnt}칸입니다." else ""
        val mal = (p.mal ?: "").trim()
        return when {
            nm.contains("꺾임") -> "${jjok(nm)} 방향으로 꺾습니다."
            // 2.76.0 (261025-A64) 자봉의 새 표시 굽은 길
            nm.contains("굽은 길 시작") -> (Regex("(1[0-2]|[1-9])시").find(nm)?.value?.let { "$it 방향으로 " } ?: "") + "천천히 굽은 길입니다." + (if ((p.cnt ?: 0) > 0) " 약 ${p.cnt}걸음 이어집니다." else "")
            nm == "굽은 길 끝" -> "굽은 길이 끝납니다."
            nm.contains("올라가는 계단") -> "계단을 올라갑니다.$cnt"
            nm.contains("내려가는 계단") -> "계단을 내려갑니다.$cnt"
            nm.contains("계단") -> "계단입니다.$cnt"
            nm.contains("신호등 없는 횡단보도") -> "신호등 없는 건널목을 건넙니다.$kkori"
            nm.contains("횡단보도") || nm.contains("건널목") -> "건널목을 건넙니다.$kkori"
            nm.contains("낙상 주의") -> {
                val k = Regex("(1[0-2]|[1-9])시").find(nm)?.let { " ${it.value} 쪽을 조심하십시오." } ?: ""
                "낙상 주의 구간입니다.$k$kkori"
            }
            nm.contains("오름턱") -> "올라서는 턱입니다. 발을 올리십시오."
            nm.contains("내림턱") -> "내려서는 턱입니다. 발을 내리십시오."
            nm.contains("점자블록 끊김") -> "여기서 점자블록이 끊깁니다."
            nm.contains("점자블록 있음") -> "여기서부터 점자블록이 있습니다."
            nm.contains("점자블록 다시 이어짐") -> "여기서 점자블록이 다시 이어집니다."
            nm.contains("조심할 곳") -> "조심하실 곳입니다." + (if (mal.isEmpty()) "" else " $mal")
            nm == "길 폭 좁음" -> "여기부터 길 폭이 좁습니다."
            nm == "길 폭 보통" -> "여기부터 길 폭이 보통입니다."
            nm == "길 폭 넓음" -> "여기부터 길 폭이 넓습니다."
            nm == "되돌아가는 곳" -> "6시 방향으로 돌아섭니다."
            nm.contains("엘리베이터 올라감") -> "엘리베이터를 타고 올라갑니다."
            nm.contains("엘리베이터 내려감") -> "엘리베이터를 타고 내려갑니다."
            nm.contains("엘리베이터") -> "엘리베이터입니다."
            nm.contains("에스컬레이터 올라감") -> "에스컬레이터로 올라갑니다."
            nm.contains("에스컬레이터 내려감") -> "에스컬레이터로 내려갑니다."
            nm.contains("에스컬레이터") -> "에스컬레이터입니다."
            nm.contains("버스 정류장") -> "버스 정류장을 지납니다."
            nm.contains("개찰구") -> "지하철 개찰구입니다."
            nm.startsWith("지하철 탐") -> "지하철을 탑니다."
            nm.startsWith("지하철 내림") -> "지하철에서 내립니다."
            nm.startsWith("버스 탐") -> "버스를 탑니다."
            nm.startsWith("버스 내림") -> "버스에서 내립니다."
            nm.startsWith("문") || nm.contains("출입문") || nm.contains("입구") -> "문입니다."
            else -> "${nm}입니다." + (if (mal.isEmpty()) "" else " $mal")
        }
    }

    /** 저장된 길을 걸음 마디로 풂 — 자리점이 너무 적으면 null */
    fun madi(g: JeomGil): List<MiriMadi>? {
        val pts = g.pts
        if (pts.size < 3) return null
        class Pi(val p: JeomPyo, val i: Int)
        val marks = g.marks.mapNotNull { m ->
            val la = m.lat; val lo = m.lon
            if (la == null || lo == null || la == 0.0 || lo == 0.0) return@mapNotNull null
            var b = 0; var bd = 1e9
            for (k in pts.indices) {
                val d = Wichi.geori(la, lo, pts[k].lat, pts[k].lon)
                if (d < bd) { bd = d; b = k }
            }
            Pi(m, b)
        }.sortedBy { it.i }
        fun sai(a: Int, b: Int): Double {
            var s = 0.0; var k = a
            while (k < b && k < pts.size - 1) { s += Wichi.geori(pts[k].lat, pts[k].lon, pts[k + 1].lat, pts[k + 1].lon); k++ }
            return s
        }
        val out = ArrayList<MiriMadi>()
        var jeon = 0; var i = 0
        while (i < marks.size) {
            val m = marks[i]
            var kkeutI = m.i
            var kilyi: Double? = null
            if (m.p.ireum.contains("시작")) {
                // 같은 갈래의 끝(「횡단보도 건너기 시작」 ↔ 「횡단보도 건너기 끝」)을 찾음
                val meori = m.p.ireum.substringBefore("시작").trim()
                var j = i + 1
                while (j < marks.size) {
                    val n2 = marks[j].p.ireum
                    if (n2.contains("끝") && (meori.isEmpty() || n2.startsWith(meori))) {
                        kkeutI = marks[j].i
                        kilyi = if ((m.p.dist ?: 0.0) > 0) m.p.dist else sai(m.i, kkeutI)
                        i = j
                        break
                    }
                    j++
                }
            } else if (m.p.ireum.contains("끝")) {
                // 짝 없는 끝은 따로 말하지 않음
                i++
                continue
            }
            val gan = sai(jeon, m.i)
            if (gan >= 2) out.add(MiriMadi("gan", "곧게 ${georiMal(gan)} 갑니다.", gan, false))
            out.add(MiriMadi("pyo", pyoMal(m.p, kilyi), 0.0, jungyo(m.p.ireum)))
            jeon = kkeutI
            i++
        }
        val kkeutGan = sai(jeon, pts.size - 1)
        if (kkeutGan >= 2) out.add(MiriMadi("gan", "곧게 ${georiMal(kkeutGan)} 갑니다.", kkeutGan, false))
        out.add(MiriMadi("kkeut", "다 오셨습니다. ${g.to.ifEmpty { "도착지" }}입니다.", 0.0, false))
        return out
    }

    fun yoyak(madi: List<MiriMadi>): List<String> {
        var nuk = 0.0
        var jul = ArrayList<String>()
        for (x in madi) {
            if (x.jong == "gan") { nuk += x.m; continue }
            if (x.jong == "kkeut") {
                jul.add(if (nuk < 1.5) "그리고 곧 다 오십니다." else "그리고 ${georiMal(nuk)} 더 가시면 다 오십니다.")
                continue
            }
            if (!x.jungyo) continue
            jul.add((if (nuk < 1.5) "곧바로 " else "${georiMal(nuk)} 뒤 ") + x.geul)
            nuk = 0.0
        }
        if (jul.size > 8) jul = ArrayList(jul.take(7) + "그 뒤로도 몇 가지가 더 있습니다. 미리 걸어 보기로 하나씩 들어 보십시오.")
        return jul
    }
}

/** 미리 걸어 보기 화면 — 길 고르기에서 들어옴 */
class MiriGeotgiHwamyeon(private val j: JeomMok, private var dwit: Boolean) : Hwamyeon("미리 걸어 보기") {
    private var gil: JeomGil? = null
    private var madi: List<MiriMadi> = emptyList()
    private var georum = -1          // -1 채비, 0~ 마디, -2 요약
    private var mot = ""
    private var bulleoneun = false
    private var deoPyeol = false
    private val main = Handler(Looper.getMainLooper())
    private var jadong = false
    private var tt: GilnunActivity? = null
    private val jadongR = object : Runnable {
        override fun run() {
            val t = tt ?: return
            if (!jadong || t.wiHwamyeon !== this@MiriGeotgiHwamyeon) { jadong = false; return }
            if (georum >= 0 && georum < madi.size - 1) { ganda(t, georum + 1); main.postDelayed(this, 5000) } else jadong = false
        }
    }

    private val gilIreum: String get() = if (dwit) "${j.to}에서 ${j.from}까지" else "${j.from}에서 ${j.to}까지"

    private fun moduGeori(): Double {
        val g = gil ?: return 0.0
        return if (g.dist > 0) g.dist else madi.filter { it.jong == "gan" }.sumOf { it.m }
    }

    private fun jjalbeunMal(): String {
        val pyo = madi.count { it.jong == "pyo" }
        return "$gilIreum. 모두 ${MiriGeotgi.georiMal(moduGeori())}입니다. 길에서 만나실 것이 ${pyo}가지입니다." + (if (dwit) " 돌아오는 길입니다." else "")
    }

    override fun chaeugi(t: GilnunActivity) {
        tt = t
        var cheot: View? = null
        when {
            mot.isNotEmpty() -> { cheot = t.geul(mot, true) }
            gil == null -> {
                cheot = t.geul("길을 불러오는 중입니다.", true)
                if (!bulleoneun) bulleo(t)
            }
            georum == -2 -> {
                cheot = t.danchu("한 마디씩 미리 걸어 보기") { ganda(t, 0) }
                t.geul("요약. $gilIreum", true)
                MiriGeotgi.yoyak(madi).forEach { t.geul(it) }
            }
            georum >= 0 && georum < madi.size -> {
                val x = madi[georum]
                if (x.jong == "kkeut") {
                    cheot = t.danchu("처음부터 다시 걸어 보기") { ganda(t, 0) }
                    t.geul(x.geul, true)
                    t.danchu(if (dwit) "가는 길로 미리 걸어 보기" else "돌아오는 길로 미리 걸어 보기") { dwit = !dwit; bulleo(t) }
                    t.danchu("요약만 듣기") { yoyakBoigi(t) }
                } else {
                    cheot = t.danchu("다음 — ${georum + 2}번째 마디로") { ganda(t, georum + 1) }
                    t.geul("${georum + 1}번째 마디. ${x.geul}", true)
                    t.danchu("방금 마디 다시 듣기") { Sori.mal(x.geul, MalGeup.JEONGBO) }
                    if (georum > 0) t.danchu("한 마디 뒤로") { ganda(t, georum - 1) }
                    t.pyeolchigi("그 밖에", deoPyeol) { deoPyeol = !deoPyeol }
                    if (deoPyeol) {
                        t.danchu(if (jadong) "저절로 넘기기 멈추기" else "저절로 넘기기 — 5초마다") { jadongBakkugi(t) }
                        t.danchu("처음부터 다시") { ganda(t, 0) }
                        t.danchu("요약만 듣기") { yoyakBoigi(t) }
                    }
                }
            }
            else -> {
                cheot = t.danchu("미리 걸어 보기 시작 — 한 마디씩") { ganda(t, 0) }
                t.geul(jjalbeunMal(), true)
                t.danchu("요약만 듣기 — 꺾임과 계단, 건널목만") { yoyakBoigi(t) }
                t.danchu(if (dwit) "가는 길로 미리 걸어 보기 — ${j.from}에서 ${j.to}까지" else "돌아오는 길로 미리 걸어 보기 — ${j.to}에서 ${j.from}까지") {
                    dwit = !dwit; bulleo(t)
                }
                t.geul("실제로 나서실 때는 공사나 세워 둔 물건으로 달라진 곳이 있을 수 있습니다.")
            }
        }
        cheot?.let { t.chojeomJul(it) }
    }

    private fun bulleo(t: GilnunActivity) {
        jadongKkeugi()
        bulleoneun = true
        gil = null; mot = ""; georum = -1
        Jeomjido.bulleoogi(j.id) { g0 ->
            main.post {
                bulleoneun = false
                if (g0 == null) {
                    mot = "길을 불러오지 못했습니다. 통신을 확인해 주십시오."
                    Sori.mal(mot)
                } else {
                    val g = if (dwit) g0.dwit() else g0
                    val md = MiriGeotgi.madi(g)
                    if (md == null) {
                        mot = "이 길에는 자리점이 너무 적어 미리 걸어 볼 수 없습니다."
                        Sori.mal(mot)
                    } else {
                        gil = g; madi = md; georum = -1
                        Sori.mal(jjalbeunMal(), MalGeup.JEONGBO)
                        Girok.namgi("mirigeot", mapOf("id" to j.id, "dwit" to dwit))
                    }
                }
                if (t.wiHwamyeon === this) t.dasiGeurigi()
            }
        }
        if (t.wiHwamyeon === this) t.dasiGeurigi()
    }

    private fun ganda(t: GilnunActivity, n: Int) {
        if (n < 0 || n >= madi.size) { jadongKkeugi(); return }
        georum = n
        val x = madi[n]
        Sori.mal(if (x.jong == "kkeut") "${x.geul} 모두 ${MiriGeotgi.georiMal(moduGeori())}입니다." else x.geul, MalGeup.JEONGBO)
        if (x.jong == "kkeut") jadongKkeugi()
        if (t.wiHwamyeon === this) t.dasiGeurigi()
    }

    private fun yoyakBoigi(t: GilnunActivity) {
        jadongKkeugi()
        georum = -2
        Sori.mal("요약입니다. " + MiriGeotgi.yoyak(madi).joinToString(" "), MalGeup.JEONGBO)
        t.dasiGeurigi()
    }

    private fun jadongBakkugi(t: GilnunActivity) {
        if (jadong) {
            jadongKkeugi()
            Sori.mal("저절로 넘기기를 멈췄습니다.", MalGeup.JEONGBO)
        } else {
            jadong = true
            Sori.mal("저절로 넘깁니다.", MalGeup.JEONGBO)
            main.postDelayed(jadongR, 5000)
        }
        t.dasiGeurigi()
    }

    private fun jadongKkeugi() {
        jadong = false
        main.removeCallbacks(jadongR)
    }
}
