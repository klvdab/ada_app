// 안드로이드 길눈 — 차 안 간판 알림(묶음 b1, 아이폰 GanpanAllim.swift 2.25.0 의 위치 부분을 같은 잣대로 옮김)
// 뜻: 걸을 때는 카메라가 앞장서고, 차 안에서는 지도가 앞장서고 카메라가 거듭니다.
// ① 달릴 때: 카메라 대신 위치로 — 받아 둔 카카오 열쇠(나스 ganpan.php)로 지나는 자리 둘레의 가게·건물을 모아
//    달리는 방향으로 왼쪽 오른쪽을 가려 "오른쪽에 GS25, 편의점"처럼 알림. 차 안 안내의 지나는 길 안내에 붙음.
// ② 서 있거나 천천히 갈 때: 창밖 간판 읽기를 켜 두셨으면 카메라(즉석 글자 읽기)가 창밖 간판을 읽어 보탬.
//    카메라 글자 읽기는 GanpanAllim.kamera(GanpanKamera — 처음엔 묶음 b3 의 GeulIlgi.kyeogiChaAn·kkeugi·kyeojim)로 부름.
//    여기서는 켜고 끄는 때만 정함. kamera 가 null 이면 ② 는 조용히 건너뜀.
// ③ 너무 잦게 떠들지 않게 간격을 두고, 한 번 알린 곳은 30분 안에 되풀이하지 않음. 켜기 끄기는 AnnaeSeoljeong(ganpanOn·ganpanKamera).
package kr.or.ada.app.gilnun

import kotlin.math.abs
import kotlin.math.max

/** 창밖 간판을 카메라로 읽어 줄 부품(글자 읽기 묶음이 채움 — 아이폰 GeulIlgi.kyeogiChaAn·kkeugi·kyeojim) */
interface GanpanKamera {
    /** 지금 카메라 글자 읽기가 켜져 있는가 */
    val kyeojim: Boolean
    /** 차 안 창밖 간판 읽기로 켬(뒤 카메라) */
    fun kyeogiChaAn()
    /** 끔(말하지 않고) */
    fun kkeugi()
}

object GanpanAllim {
    /** 창밖 간판을 읽을 카메라 — 처음엔 즉석 글자 읽기(GeulIlgi, 묶음 b3)에 이음. 다른 부품으로 바꾸려면 이 자리에 넣음 */
    var kamera: GanpanKamera? = object : GanpanKamera {
        override val kyeojim: Boolean get() = GeulIlgi.kyeojim
        override fun kyeogiChaAn() { GeulIlgi.kyeogiChaAn(MalHagi.hwalseong?.get()) }
        override fun kkeugi() { GeulIlgi.kkeugi(false) }
    }

    private var mutneunJung = false
    private var mureunT = 0L
    private var mureunJari: Pair<Double, Double>? = null
    private var malT = 0L
    private val malhan = HashMap<String, Long>()   // 한 번 알린 곳(30분)
    private var neurinSijak = 0L
    private var kameraKyeon = false                 // 이 부품이 카메라를 켰는가

    /** 우선 차례 — 앞일수록 먼저 알림(식당·카페처럼 흔한 곳은 뒤로) */
    private val chare = listOf("지하철역", "공공기관", "관광명소", "문화시설", "마트", "은행", "약국", "병원", "주유소", "숙박", "편의점", "카페", "식당")

    /** 차 안 안내가 자리를 받을 때마다 부름(화면 줄) */
    fun chaAn(w: Jari) {
        val st = AnnaeSeoljeong
        val now = System.currentTimeMillis()
        // ② 서 있거나 천천히 — 창밖 간판 읽기(카메라)
        val k = kamera
        if (st.ganpanKamera && k != null && !w.georeumChu && w.sokdo < 3) {
            if (neurinSijak == 0L) neurinSijak = now
            if (now - neurinSijak >= 3000 && !kameraKyeon && !k.kyeojim) {
                kameraKyeon = true
                k.kyeogiChaAn()
            }
        } else {
            neurinSijak = 0L
            if (kameraKyeon && w.sokdo >= 5) kameraKkeugi()
        }
        // ① 달릴 때 — 위치로
        if (!st.ganpanOn || w.georeumChu || w.sokdo < 4 || w.banghyang < 0 || mutneunJung) return
        val gan = max(15, st.gilGap / 3) * 1000L          // 지나는 곳 간격의 3분의 1, 적어도 15초
        if (now - malT < gan || now - mureunT < 8000) return
        val j = mureunJari
        if (j != null && Wichi.geori(j.first, j.second, w.lat, w.lon) < 60) return
        mutneunJung = true
        mureunT = now
        mureunJari = w.lat to w.lon
        val m = if (w.sokdo > 15) "250" else "150"
        val bang = w.banghyang
        val sokdo = w.sokdo
        Tongsin.json("ganpan.php", mapOf("lat" to Chatgi.f6(w.lat), "lon" to Chatgi.f6(w.lon), "m" to m)) { o ->
            mutneunJung = false
            val rows = ArrayList<org.json.JSONObject>()
            if (o != null && o.optBoolean("ok", false)) {
                val a = o.optJSONArray("rows")
                if (a != null) for (i in 0 until a.length()) a.optJSONObject(i)?.let { rows.add(it) }
            }
            goreugi(rows, w.lat, w.lon, bang, sokdo)
        }
    }

    /** 차에서 내리거나 안내가 끝나면 */
    fun kkeut() {
        neurinSijak = 0L
        if (kameraKyeon) kameraKkeugi()
    }

    private fun kameraKkeugi() {
        kameraKyeon = false
        val k = kamera ?: return
        if (k.kyeojim) k.kkeugi()
    }

    /** 앞으로 곧 지날 왼쪽·오른쪽 곳 하나를 골라 알림 */
    private fun goreugi(rows: List<org.json.JSONObject>, la: Double, lo: Double, bang: Double, sokdo: Double) {
        val now = System.currentTimeMillis()
        malhan.entries.removeAll { now - it.value >= 1800000 }
        class Hubo(val ireum: String, val gal: String, val pyeon: String, val geori: Double, val cha: Int)
        val hubo = ArrayList<Hubo>()
        for (r in rows) {
            val ir = Jeomjido.geulOrNull(r, "ireum") ?: continue
            val gal = Jeomjido.geulOrNull(r, "gal") ?: continue
            val pla = Jeomjido.su(r, "lat") ?: continue
            val plo = Jeomjido.su(r, "lon") ?: continue
            if (malhan.containsKey(ir)) continue
            val g = Wichi.geori(la, lo, pla, plo)
            if (g < 15) continue
            var gak = Jeomjido.bangwi(la, lo, pla, plo) - bang
            while (gak > 180) gak -= 360
            while (gak < -180) gak += 360
            // 앞쪽 비스듬히(20~110도) — 곧 옆을 지날 곳
            val a = abs(gak)
            if (a < 20 || a > 110) continue
            val cha = chare.indexOf(gal).let { if (it < 0) 99 else it }
            hubo.add(Hubo(ir, gal, if (gak > 0) "오른쪽" else "왼쪽", g, cha))
        }
        val ga = hubo.sortedWith(compareBy<Hubo>({ it.cha }, { it.geori })).firstOrNull() ?: return
        malhan[ga.ireum] = now
        malT = now
        val ireum = if (ga.ireum.contains(ga.gal)) ga.ireum else "${ga.ireum}, ${ga.gal}"
        Sori.mal("${ga.pyeon}에 $ireum.", MalGeup.JEONGBO)
        Girok.namgi("ganpan", mapOf("gal" to ga.gal, "pyeon" to ga.pyeon, "sokdo" to (sokdo * 3.6).toInt()))
    }
}
