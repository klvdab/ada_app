// 안드로이드 길눈 — 되짚어 나가기(2.7.0, 묶음 b2 점지도 마저, 대표님 지시). 아이폰 Doe.swift 2.13.0(이사장님 승인 1)과 같은 쓰임·같은 말.
// 병원·관공서처럼 위성이 잡히지 않는 건물 안에서, 들어갈 때 켜 두면 걸음 수와 방향으로 길을 기억했다가
// 나올 때 왔던 길을 거꾸로 알려 드립니다. "몇 걸음 걸으신 뒤 몇 시 방향으로 꺾으십시오"로 말합니다.
// 걸음은 Wichi.georeumSu(몸 센서로 늦음을 메운 걸음), 방향은 Wichi.hapBang(자이로 합성 방향 — 건물 안 쇠붙이 옆에서도 덜 틀어짐).
// 기억한 길은 이 폰에만 남고(다른 사람에게 가지 않음) 지우실 때까지 남습니다. 앱이 꺼졌다 켜져도 이어 기억합니다.
// 안내는 손대지 않아도 걸음마다 스스로 — 걷는 동안 단추를 누르실 일이 없음.
package kr.or.ada.app.gilnun

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** 한 토막 — 같은 쪽으로 곧게 걸은 걸음 수와 그 방향(북쪽 0도) */
data class DoeTomak(var georeum: Int, var bang: Double)

object DoeEngine {
    enum class Sangtae { SWIM, GIEOK, ANNAE }

    var sangtae = Sangtae.SWIM
        private set
    private val tomak = ArrayList<DoeTomak>()   // 기억한 길
    var jul = ""                                // 화면에 보이는 지금 상태 한 줄
        private set
    /** 상태가 바뀌면 한 번(화면이 채움) */
    var byeonhwa: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var dolgo = false
    private var majimakGeoreum = 0
    private val bitgan = ArrayList<Double>()        // 방향이 어긋난 채 걸은 걸음들의 방향
    private val sseulHyang = ArrayList<Double>()    // 지금 토막 걸음들의 방향(평균을 냄)
    private var ilgeum = false

    // 안내 중
    private var gil: List<DoeTomak> = emptyList()   // 거꾸로 뒤집은 길
    private var gi = 0
    private var gugangSijak = 0
    private var yegoHaet = false
    private var majimakBangMal = 0L
    private var majimakNamEum = 0

    private val sigye = object : Runnable {
        override fun run() {
            if (!dolgo) return
            dolgi()
            main.postDelayed(this, 500)
        }
    }

    private fun ilgi() {
        if (ilgeum) return
        ilgeum = true
        val s = JeomSeol.geul("doeGil") ?: return
        try {
            val a = JSONArray(s)
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                tomak.add(DoeTomak(o.optInt("georeum", 0), o.optDouble("bang", 0.0)))
            }
        } catch (e: Exception) {}
    }

    val gieokItda: Boolean get() { ilgi(); return tomak.isNotEmpty() && tomak.sumOf { it.georeum } > 0 }
    val chongGeoreum: Int get() { ilgi(); return tomak.sumOf { it.georeum } }
    val kkeokSu: Int get() { ilgi(); return max(0, tomak.size - 1) }

    private val nachimban: Double get() = Wichi.hapBang

    // MARK: 들어갈 때 — 기억

    fun gieokSijak(c: Context) {
        JeomSeol.sijak(c)
        ilgi()
        Wichi.wiseongDolligi()
        tomak.clear()
        bitgan.clear()
        sseulHyang.clear()
        majimakGeoreum = Wichi.georeumSu
        sangtae = Sangtae.GIEOK
        JeomSeol.geulSseugi("doeGieokJung", "1")
        jeojang()
        jul = "길을 기억하는 중입니다."
        mal("지금부터 걸으신 길을 기억합니다. 화면을 켜 두시고 평소처럼 걸어 들어가십시오. 나오실 때 되짚어 나가기를 누르십시오.")
        Girok.namgi("doe_gieok")
        sigyeKyeogi()
        byeonhwa?.invoke()
    }

    /** 앱이 꺼졌다 켜져도 기억하던 중이면 이어 기억 */
    fun ieoGagi(c: Context) {
        JeomSeol.sijak(c)
        ilgi()
        if (JeomSeol.geul("doeGieokJung") != "1") return
        majimakGeoreum = Wichi.georeumSu
        sangtae = Sangtae.GIEOK
        jul = "길을 기억하는 중입니다. 지금까지 ${chongGeoreum}걸음."
        sigyeKyeogi()
    }

    fun gieokGeuman() {
        if (sangtae != Sangtae.GIEOK) return
        gieokMaechim()
        sangtae = Sangtae.SWIM
        mal(if (gieokItda) "길 기억을 멈췄습니다. 모두 ${chongGeoreum}걸음, ${kkeokSu}번 꺾으셨습니다. 나오실 때 되짚어 나가기를 누르십시오." else "길 기억을 멈췄습니다. 기억한 걸음이 없습니다.")
        byeonhwa?.invoke()
    }

    fun jiugi() {
        sigyeKkeugi()
        tomak.clear()
        sangtae = Sangtae.SWIM
        JeomSeol.geulSseugi("doeGil", null)
        JeomSeol.geulSseugi("doeGieokJung", null)
        jul = ""
        mal("기억한 길을 지웠습니다.")
        byeonhwa?.invoke()
    }

    private fun gieokMaechim() {
        sigyeKkeugi()
        // 어긋난 채 남은 걸음은 지금 토막에 되돌려 넣음
        if (bitgan.isNotEmpty() && tomak.isNotEmpty()) tomak[tomak.size - 1].georeum += bitgan.size
        bitgan.clear()
        tomak.removeAll { it.georeum <= 0 }
        JeomSeol.geulSseugi("doeGieokJung", null)
        jeojang()
        jul = if (gieokItda) "기억한 길 — ${chongGeoreum}걸음, ${kkeokSu}번 꺾음." else ""
    }

    private fun sigyeKyeogi() {
        main.removeCallbacks(sigye)
        dolgo = true
        main.postDelayed(sigye, 500)
    }

    private fun sigyeKkeugi() {
        dolgo = false
        main.removeCallbacks(sigye)
    }

    private fun dolgi() {
        when (sangtae) {
            Sangtae.GIEOK -> gieokDolgi()
            Sangtae.ANNAE -> annaeDolgi()
            Sangtae.SWIM -> {}
        }
    }

    /** 새 걸음마다 그때의 방향을 보아 — 45도 넘게 어긋난 걸음이 셋 이어지면 꺾은 것으로 보고 새 토막을 엶 */
    private fun gieokDolgi() {
        val n = Wichi.georeumSu
        val sae = n - majimakGeoreum
        if (sae <= 0) return
        majimakGeoreum = n
        val h = nachimban
        if (h < 0) {
            if (tomak.isEmpty()) tomak.add(DoeTomak(0, 0.0))
            tomak[tomak.size - 1].georeum += sae
            return
        }
        for (k in 0 until min(sae, 20)) {
            if (tomak.isEmpty()) {
                tomak.add(DoeTomak(1, h))
                sseulHyang.clear(); sseulHyang.add(h)
                continue
            }
            val bon = tomak[tomak.size - 1].bang
            if (abs(chai(h, bon)) > 45) {
                bitgan.add(h)
                if (bitgan.size >= 3) {
                    val saeBang = pyeonggyun(bitgan)
                    tomak.add(DoeTomak(bitgan.size, saeBang))
                    sseulHyang.clear(); sseulHyang.addAll(bitgan)
                    bitgan.clear()
                }
            } else {
                tomak[tomak.size - 1].georeum += 1 + bitgan.size
                bitgan.clear()
                sseulHyang.add(h)
                if (sseulHyang.size > 40) sseulHyang.removeAt(0)
                tomak[tomak.size - 1].bang = pyeonggyun(sseulHyang)
            }
        }
        if (sae > 20) tomak[tomak.size - 1].georeum += sae - 20
        jul = "길을 기억하는 중입니다. 지금까지 ${chongGeoreum}걸음, ${kkeokSu}번 꺾음."
        if (n % 20 == 0) jeojang()
    }

    // MARK: 나올 때 — 되짚어 안내

    fun doejipgi(c: Context) {
        JeomSeol.sijak(c)
        ilgi()
        if (sangtae == Sangtae.GIEOK) gieokMaechim()
        if (!gieokItda) {
            sangtae = Sangtae.SWIM
            mal("기억해 둔 길이 없습니다. 들어가실 때 들어갑니다를 눌러 두십시오.")
            byeonhwa?.invoke()
            return
        }
        Wichi.wiseongDolligi()
        gil = tomak.reversed().map { DoeTomak(it.georeum, (it.bang + 180) % 360) }
        gi = 0
        gugangSijak = Wichi.georeumSu
        yegoHaet = false
        majimakNamEum = gil[0].georeum
        sangtae = Sangtae.ANNAE
        Girok.namgi("doe_annae", mapOf("georeum" to chongGeoreum, "kkeok" to kkeokSu))
        val cheot = gil[0]
        var m = "왔던 길을 되짚어 나갑니다. 모두 ${chongGeoreum}걸음, ${kkeokSu}번 꺾습니다. "
        val h = nachimban
        m += if (h >= 0) {
            val s = Jeomjido.sigyeBanghyang(h, cheot.bang)
            if (s == 12) "앞으로 " else "${s}시 방향으로 돌아 서신 뒤 "
        } else "들어오신 쪽으로 돌아 서신 뒤 "
        m += "${cheot.georeum}걸음 걸으십시오."
        jul = "되짚어 나가는 중입니다. 첫 토막 ${cheot.georeum}걸음."
        mal(m)
        sigyeKyeogi()
        byeonhwa?.invoke()
    }

    fun annaeGeuman() {
        if (sangtae != Sangtae.ANNAE) return
        sigyeKkeugi()
        sangtae = Sangtae.SWIM
        jul = if (gieokItda) "기억한 길 — ${chongGeoreum}걸음, ${kkeokSu}번 꺾음." else ""
        mal("되짚어 나가기를 멈췄습니다. 기억한 길은 그대로 있습니다.")
        byeonhwa?.invoke()
    }

    /** 지금 무엇을 할지 다시 듣기 */
    fun jigeumMal() {
        when (sangtae) {
            Sangtae.SWIM -> mal(if (gieokItda) "기억해 둔 길이 있습니다. ${chongGeoreum}걸음, ${kkeokSu}번 꺾습니다." else "기억해 둔 길이 없습니다.")
            Sangtae.GIEOK -> mal("길을 기억하는 중입니다. 지금까지 ${chongGeoreum}걸음, ${kkeokSu}번 꺾으셨습니다.")
            Sangtae.ANNAE -> {
                if (gi >= gil.size) return
                val nam = max(0, gil[gi].georeum - (Wichi.georeumSu - gugangSijak))
                var m = "이 토막에서 ${nam}걸음 더 걸으십시오."
                m += if (gi + 1 < gil.size) " 그다음 ${kkeokMal(gi)}." else " 그러면 들어오신 곳입니다."
                mal(m)
            }
        }
    }

    private fun kkeokMal(i: Int): String {
        val s = Jeomjido.sigyeBanghyang(gil[i].bang, gil[i + 1].bang)
        return if (s == 12) "그대로 앞으로" else "${s}시 방향으로 꺾으십시오"
    }

    private fun annaeDolgi() {
        if (gi >= gil.size) return
        val geoleun = Wichi.georeumSu - gugangSijak
        val nam = gil[gi].georeum - geoleun
        // 꺾는 곳 다섯 걸음 앞에서 미리
        if (nam in 2..5 && !yegoHaet && gil[gi].georeum > 8) {
            yegoHaet = true
            mal(if (gi + 1 < gil.size) "${nam}걸음 뒤 ${kkeokMal(gi)}." else "${nam}걸음 더 가시면 들어오신 곳입니다.")
        }
        if (nam <= 0) {
            if (gi + 1 >= gil.size) {
                sigyeKkeugi()
                sangtae = Sangtae.SWIM
                jul = "기억한 길 — ${chongGeoreum}걸음, ${kkeokSu}번 꺾음."
                Girok.namgi("doe_kkeut")
                mal("들어오신 곳에 닿았습니다. 되짚어 나가기를 마칩니다. 기억한 길은 지우실 때까지 남습니다.")
                byeonhwa?.invoke()
                return
            }
            val m = "지금 ${kkeokMal(gi)}. 그다음 ${gil[gi + 1].georeum}걸음."
            gi += 1
            gugangSijak = Wichi.georeumSu
            yegoHaet = false
            majimakNamEum = gil[gi].georeum
            majimakBangMal = System.currentTimeMillis()
            jul = "되짚어 나가는 중입니다. ${gi + 1}번째 토막, ${gil[gi].georeum}걸음."
            mal(m)
            return
        }
        // 열 걸음마다 남은 걸음, 방향이 크게 어긋나면 6초에 한 번
        if (majimakNamEum - nam >= 10) {
            majimakNamEum = nam
            jul = "되짚어 나가는 중입니다. 이 토막 ${nam}걸음 남음."
            mal("${nam}걸음 남았습니다.", MalGeup.JEONGBO)
        }
        val h = nachimban
        if (h >= 0 && geoleun >= 2 && abs(chai(h, gil[gi].bang)) > 50 && System.currentTimeMillis() - majimakBangMal > 6000) {
            majimakBangMal = System.currentTimeMillis()
            val s = Jeomjido.sigyeBanghyang(h, gil[gi].bang)
            mal("방향이 어긋났습니다. ${s}시 방향으로 돌아 서십시오.")
        }
    }

    // MARK: 셈

    private fun jeojang() {
        val a = JSONArray()
        for (t in tomak) a.put(JSONObject().put("georeum", t.georeum).put("bang", t.bang))
        JeomSeol.geulSseugi("doeGil", a.toString())
    }

    /** 두 방향의 차이(-180~180, 오른쪽이 +) */
    fun chai(a: Double, b: Double): Double {
        var d = (a - b) % 360
        if (d > 180) d -= 360
        if (d < -180) d += 360
        return d
    }

    /** 방향들의 평균(둥근 평균) */
    fun pyeonggyun(l: List<Double>): Double {
        if (l.isEmpty()) return 0.0
        val r = Math.PI / 180
        val x = l.sumOf { cos(it * r) }
        val y = l.sumOf { sin(it * r) }
        var d = atan2(y, x) / r
        if (d < 0) d += 360
        return d
    }

    private fun mal(t: String, g: MalGeup = MalGeup.ANNAE) { Sori.mal(t, g) }
}

/** 되짚어 나가기 화면 — 들어갈 때 한 번, 나올 때 한 번 누르시면 그 뒤로는 스스로 안내 */
class DoeHwamyeon : Hwamyeon("되짚어 나가기") {
    private var cheot: View? = null
    private var boin: DoeEngine.Sangtae? = null
    private var pyeol = false

    override fun chaeugi(t: GilnunActivity) {
        DoeEngine.byeonhwa = { if (t.wiHwamyeon === this) t.dasiGeurigi() }
        val d = DoeEngine
        cheot = when (d.sangtae) {
            DoeEngine.Sangtae.GIEOK -> {
                val b = t.danchu("나갑니다 — 왔던 길 되짚어 나가기") { d.doejipgi(t) }
                t.danchu("지금 상태 듣기") { d.jigeumMal() }
                t.danchu("길 기억 멈추기 — 기억한 길은 남깁니다") { d.gieokGeuman() }
                b
            }
            DoeEngine.Sangtae.ANNAE -> {
                val b = t.danchu("지금 할 일 다시 듣기") { d.jigeumMal() }
                t.danchu("되짚어 나가기 멈추기") { d.annaeGeuman() }
                b
            }
            DoeEngine.Sangtae.SWIM -> {
                val b = t.danchu("들어갑니다 — 지금부터 길을 기억하기") { d.gieokSijak(t) }
                if (d.gieokItda) t.danchu("나갑니다 — 왔던 길 되짚어 나가기, ${d.chongGeoreum}걸음, ${d.kkeokSu}번 꺾음") { d.doejipgi(t) }
                b
            }
        }
        t.danchu(if (pyeol) "알아 두실 것 접기" else "알아 두실 것 펼치기") { pyeol = !pyeol; t.dasiGeurigi() }
        if (pyeol) {
            t.geul("병원 진료실처럼 위성이 잡히지 않는 건물 안에 들어가실 때 들어갑니다를 누르시고 평소처럼 걸어 들어가십시오. 걸음 수와 방향으로 길을 기억합니다. 나오실 때 나갑니다를 누르시면 먼저 몇 시 방향으로 돌아 서실지 알려 드리고, 몇 걸음 걸으신 뒤 몇 시 방향으로 꺾으실지 차례로 말씀드립니다. 꺾는 곳 다섯 걸음 앞에서 미리, 열 걸음마다 남은 걸음을, 방향이 크게 어긋나면 돌아 서실 쪽을 알려 드립니다. 기억한 길은 이 폰에만 남고 지우실 때까지 남습니다. 어긋날 수 있으니 벽과 손잡이를 함께 짚어 가십시오.")
            if (d.sangtae == DoeEngine.Sangtae.SWIM && d.gieokItda) t.danchu("기억한 길 지우기") { d.jiugi() }
        }
    }

    override fun boilttae(t: GilnunActivity) {
        val s = DoeEngine.sangtae
        if (boin != null && boin != s) cheot?.let { t.chojeomOmgigi(it) }
        boin = s
    }
}
