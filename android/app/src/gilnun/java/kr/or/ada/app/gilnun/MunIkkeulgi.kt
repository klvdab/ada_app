// 문까지 이끌기 (2.38.0, 빌드 261013-A26, 이사장님 승인 2026-10-13 「그래」, 아이폰 2.66.0 MunIkkeulgi 와 같음)
// 걸어서 목적지에 닿으면 카메라보다 먼저, 담아 둔 문 자리(내 문 → 문 창고 mun.php)로
// 「문은 1시 방향, 약 15걸음입니다」처럼 시계 방향과 걸음 수로 이끌고, 문 앞 5미터 안에 들면 카메라 문 찾기(문까지)가 거듦.
// 담아 둔 문이 없으면 예전처럼 카메라 문 찾기(dochakHook)를 켬.
package kr.or.ada.app.gilnun

import android.os.Handler
import android.os.Looper

object MunIkkeulgi {
    private val main = Handler(Looper.getMainLooper())

    private class Mun(val lat: Double, val lon: Double, val ireum: String, val saengMal: String)
    private var mun: Mun? = null
    private var beon = 0
    private var sijakT = 0L
    private var majimakMalT = 0L
    private var majimakD = -1.0
    private var majimakSigye = -1
    private var ochaeMal = false
    var ikkeuneunJung = false
        private set
    private var kamera: ((String) -> Unit)? = null

    private val salpim = object : Runnable {
        override fun run() {
            salpigi()
            if (mun != null) main.postDelayed(this, 2000)
        }
    }

    /** 걸어서 닿았을 때 — kam: 카메라 문 찾기를 켜는 일(없으면 null) */
    fun sijak(lat: Double, lon: Double, kam: ((String) -> Unit)?) {
        meomchugi()
        beon += 1
        val b0 = beon
        kamera = kam
        val nae = NaeMun.mokrok.map { it to Wichi.geori(lat, lon, it.lat, it.lon) }.filter { it.second <= 60 }.minByOrNull { it.second }
        if (nae != null) {
            val m = nae.first
            dolligi(Mun(m.lat, m.lon, if (m.ireum.isEmpty()) "담아 두신 문" else m.ireum, ""), "nae")
            return
        }
        Tongsin.json("mun.php", mapOf("a" to "near") + Jeomjido.jari(lat, lon) + mapOf("r" to "60"), 15000) { o ->
            if (b0 != beon) return@json
            val rows = o?.optJSONArray("list")
            var gol: Mun? = null
            var golGap = Double.MAX_VALUE
            if (rows != null) for (i in 0 until rows.length()) {
                val x = rows.optJSONObject(i) ?: continue
                val la = Jeomjido.su(x, "lat") ?: continue
                val lo = Jeomjido.su(x, "lon") ?: continue
                if (la == 0.0) continue
                val nm = Jeomjido.gul(x, "ireum").trim()
                // 대문(doo)으로 표시된 문이 먼저, 그다음 목적지에 가까운 문
                val gap = (if ((Jeomjido.su(x, "doo") ?: 0.0) > 0) 0.0 else 1000.0) + Wichi.geori(lat, lon, la, lo)
                if (gap < golGap) { golGap = gap; gol = Mun(la, lo, if (nm.isEmpty()) "문" else nm, Jeomjido.gul(x, "saengMal")) }
            }
            val g = gol
            if (g != null) dolligi(g, "changgo")
            else {
                Girok.namgi("mun_ikkeul", mapOf("mun" to "eopseum"))
                kamera?.invoke("dochak")
            }
        }
    }

    fun meomchugi() {
        main.removeCallbacks(salpim)
        mun = null
        ikkeuneunJung = false
    }

    private fun dolligi(m: Mun, eodi: String) {
        mun = m
        sijakT = System.currentTimeMillis()
        majimakMalT = 0L
        majimakD = -1.0
        majimakSigye = -1
        ochaeMal = false
        ikkeuneunJung = true
        Girok.namgi("mun_ikkeul", mapOf("mun" to eodi))
        main.removeCallbacks(salpim)
        main.post(salpim)
    }

    private fun salpigi() {
        val m = mun ?: return
        val now = System.currentTimeMillis()
        if (now - sijakT > 180_000L) {
            Girok.namgi("mun_ikkeul_mot")
            meomchugi()
            Sori.mal("문 안내를 마칩니다. 다시 찾으시려면 문 찾아 줘라고 말씀하십시오.")
            return
        }
        val w = Wichi.jigeum ?: return
        val d = Wichi.geori(w.lat, w.lon, m.lat, m.lon)
        if (d <= 5) {
            Girok.namgi("mun_ikkeul_dochak", mapOf("cho" to ((now - sijakT) / 1000).toInt()))
            meomchugi()
            Sori.mal("문 앞 가까이에 닿았습니다. ${m.ireum}입니다." + (if (m.saengMal.isEmpty()) "" else " ${m.saengMal}"))
            val k = kamera
            if (k != null) main.postDelayed({ k("munkkaji") }, 3000)
            return
        }
        val bang = Jeomjido.bangwi(w.lat, w.lon, m.lat, m.lon)
        val apjjok = if (w.banghyang >= 0 && w.sokdo > 0.8) w.banghyang else Wichi.hapBang
        val s = if (apjjok >= 0) Jeomjido.sigyeBanghyang(apjjok, bang) else 0
        val bakkwim = majimakD < 0 || Math.abs(d - majimakD) >= 3 || (s != 0 && s != majimakSigye)
        if (!bakkwim && now - majimakMalT < 12_000L) return
        if (now - majimakMalT < 4_000L) return
        majimakMalT = now
        majimakD = d
        majimakSigye = s
        var t = if (s == 0) "문까지 ${AnnaeEngine.gm(d)}입니다. 한두 걸음 걸으시면 방향을 알려 드립니다."
                else "문은 ${s}시 방향, ${AnnaeEngine.gm(d)}입니다."
        if (!ochaeMal && w.ochae > 15) {
            ochaeMal = true
            t += " 위성 오차가 커서 몇 걸음 어긋날 수 있습니다."
        }
        Sori.mal(t)
    }
}
