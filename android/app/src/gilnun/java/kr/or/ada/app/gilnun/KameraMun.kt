// 안드로이드 길눈 — 문 찾기(묶음 b3_kamera) ★관리자 시험 중
// 아이폰 MunChatgi.swift 2.15.0 은 애플 ARKit 의 "벽·문·창 알아보기"(평면 분류, 라이다 폰은 공간 그물 분류)로 문 자체를 찾습니다.
// 안드로이드에는 문을 알아보는 기능이 없습니다(구글 AR 의 장면 알아보기는 하늘·건물·길 같은 바깥 것만, ML Kit 물체 알아보기도 문 갈래가 없음).
// 그래서 안드로이드 판은 아이폰 문 찾기가 함께 쓰는 "문 둘레 글자"(호수·출입구·PUSH·PULL 같은 짧은 글, 아이폰 geulIlgi 와 같은 잣대)를
// 카메라로 찾아 몇 시 방향인지 알리고, 글자 크기로 거리를 어림합니다(글자 높이 6센티미터로 셈 — 어림이라 시험 중).
// 문 자체를 보는 것이 아니라 오래 시험한 뒤 대표님 승인으로 열기로 하고, 지금은 관리자 열쇠가 있는 폰에서만 씁니다.
// 같은 잣대: 3초에 한 번쯤 바뀔 때만 말, 처음 보이면 확신음, 가까워지면 확신음, 바로 앞(0.9미터)이면 도착 소리와 진동 뒤 끔,
//   10초·35초에 돌려 보시라는 말, 90초 안에 못 찾으면 끔, 3분 넘게 닿지 못하면 끔. 카메라 눈 말소리를 따름.
// 내 문(아이폰 두 번 찍기와 합침): ① 두 번 찍기("jjikgi")에는 말 없이 문 둘레 글자만 모아 jjikgiKkeut 로 넘김
//   ② 찾아가는 문의 글자(gidae)가 있으면 "찍어 두신 문"인지 옆 문인지 가림 ③ 문 바로 앞을 알린 뒤 두 걸음 지나시면 두 번 찍은 것으로 내 문에 담음.
//   내 문 자료는 다른 묶음(NaeMun)의 것이라 KameraIeum.kt 가 gidaeChatgi·jjakDamgi 에 이어 붙임(비어 있으면 ②③은 쉼).
package kr.or.ada.app.gilnun

import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.camera.view.PreviewView
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

object MunChatgi : KameraNunBupum {
    override val ireum = "문 찾기"
    override var kyeojim = false
        private set
    var munBoim = false
        private set
    var sangtae = ""
        private set
    var byeonhwa: (() -> Unit)? = null

    /** 찾아가는 문의 이름과 담아 둔 글자 — 있으면 읽은 글자와 견줌 */
    var gidae: Pair<String, List<String>>? = null
    /** 지금 자리 둘레(미터)의 글자를 담아 둔 내 문 — (이름, 글자). KameraIeum 이 채움 */
    var gidaeChatgi: ((lat: Double, lon: Double, r: Double) -> Pair<String, List<String>>?)? = null
    /** 두 번 찍은 것으로 내 문에 담기 — 알릴 말을 돌려줌. KameraIeum 이 채움 */
    var jjakDamgi: ((lat: Double, lon: Double, bang: Double?, geul: List<String>) -> String)? = null

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var sijya = 63.0
    private var eodiseo = ""
    private var sijakT = 0L
    private var malT = 0L
    private var majimakSi = 0
    private var majimakGeoreum = 0
    private var gakkaum = 99.0
    private var chatneunMalSu = 0
    private var boinT = 0L
    private var ireotdaMal = false
    private var majatda = false
    private val ilgeunGeul = HashSet<String>()
    private val geulSun = ArrayList<String>()   // 읽은 차례대로
    private var apGeul = HashSet<String>()      // 앞 장에서 본 문 글자(두 장 잇달아 보여야)

    private val mungeul = listOf("호", "실", "입구", "출입", "출구", "문", "화장실", "사무", "센터", "PUSH", "PULL", "당기", "미세")
    private val sutjaRe = Regex("[0-9]")

    init { Kamera.deungrok(this) }

    /** 이 폰에서 문 찾기를 쓸 수 있나 — 관리자 시험 중이라 관리자 열쇠가 있고, 카메라 허락이 이미 있을 때(저절로 켤 때 허락 창을 띄우지 않게) */
    fun gigiGaneung(): Boolean {
        val a = Kamera.hwalseongEotgi() ?: return false
        return KameraGwanli.boim(a) && Kamera.heorakItda(a)
    }

    /** 켜기 — eodiseo: "hwamyeon" 문 찾기 화면, "malhagi" 말로, "munkkaji" 점지도 문까지, "dochak" 걸어서 도착, "jjikgi" 내 문 두 번 찍기(아이폰과 같은 이름) */
    fun kyeogi(eodiseo: String, a: GilnunActivity? = null, bogi: PreviewView? = null, gd: Pair<String, List<String>>? = null) {
        val t = a ?: Kamera.hwalseongEotgi() ?: return
        if (kyeojim) { if (bogi != null) Kamera.bogiDalgi(bogi); return }
        if (!KameraGwanli.boim(t)) {
            // 저절로 켜질 때(문까지·도착·두 번 찍기)는 말없이 — 손으로·말로 켜실 때만 알림
            if (eodiseo == "hwamyeon" || eodiseo == "malhagi") Sori.mal("문 찾기는 안드로이드 길눈에서 아직 관리자 시험 중입니다.")
            return
        }
        if (gd != null) gidae = gd
        Kamera.heorak(t, this, "문 찾기를") { sijak(t, eodiseo, bogi) }
    }

    private fun sijak(t: GilnunActivity, e: String, bogi: PreviewView?) {
        sijya = Kamera.garoSijya(t)
        val jjikgi = e == "jjikgi"
        Kamera.sijak(t, this, bogi, if (jjikgi) 500L else 700L, { jang -> bunseok(jang) }) { ok ->
            if (ok) {
                eodiseo = e
                if (gidae == null && !jjikgi) {
                    val w = Wichi.jigeum
                    if (w != null) gidae = gidaeChatgi?.invoke(w.lat, w.lon, 40.0)
                }
                kyeojim = true
                munBoim = false
                sijakT = System.currentTimeMillis()
                malT = 0L
                majimakSi = 0; majimakGeoreum = 0; gakkaum = 99.0
                chatneunMalSu = 0; ireotdaMal = false; majatda = false
                ilgeunGeul.clear(); geulSun.clear(); apGeul = HashSet()
                sangtae = "문을 찾는 중입니다."
                Girok.namgi("munchatgi", mapOf("eodi" to e, "android" to "geul"))
                if (!jjikgi) {   // 두 번 찍기 — 말 없이 글자만 모음
                    val ap = if (e == "munkkaji" || e == "dochak") "카메라로 문을 찾습니다. " else ""
                    mal(ap + "폰을 가슴 앞에 세워 들고 천천히 걸으십시오.")
                }
                byeonhwa?.invoke()
            }
        }
    }

    override fun kkeugi(malHagi: Boolean) {
        Kamera.kkeugi(this)
        if (!kyeojim) return
        kyeojim = false
        munBoim = false
        sangtae = ""
        gidae = null
        if (malHagi) mal("문 찾기를 마칩니다.")
        byeonhwa?.invoke()
    }

    /** 두 번 찍기 — 첫 번째 찍을 때 켜 두었던 카메라가 읽은 글자를 넘기고 끔 */
    fun jjikgiKkeut(): List<String> {
        val g = ArrayList(geulSun)
        if (kyeojim && eodiseo == "jjikgi") kkeugi(false)
        return g
    }

    // MARK: 한 장마다 — 0.7초(두 번 찍기는 0.5초)에 한 번

    private class MunHubo(val geul: String, val georiM: Double, val gak: Double)

    private fun bunseok(jang: KameraJang) {
        val w = jang.garo.toDouble()
        val h = jang.sero.toDouble()
        GeulIlgi.ilgiTeul(jang.inputImage(), jang.garo, jang.sero) { jul ->
            jang.kkeut()
            if (!kyeojim || w <= 0 || h <= 0) return@ilgiTeul
            val seroRad = sijya * PI / 180
            val garoRad = 2 * atan(tan(seroRad / 2) * (w / max(1.0, h)))
            val fy = (h / 2) / tan(seroRad / 2)
            val hubo = ArrayList<MunHubo>()
            for (x in jul) {
                val s = x.mal
                if (s.isEmpty() || s.length > 14) continue
                val jeokhap = sutjaRe.containsMatchIn(s) || mungeul.any { s.uppercase().contains(it) }
                if (!jeokhap) continue
                val nopi = x.teul.height().toDouble()
                if (nopi < 4) continue
                val d = 0.06 * fy / nopi
                val gak = (x.teul.exactCenterX() / w - 0.5) * garoRad * 180 / PI
                hubo.add(MunHubo(s, d, gak))
            }
            // 두 장 잇달아 보인 글자만(헛글 줄이기)
            val keys = hubo.map { GeulIlgi.ttuk(it.geul) }
            val hwakjeong = hubo.filterIndexed { i, _ -> apGeul.contains(keys[i]) }
            apGeul = keys.toHashSet()
            val best = hwakjeong.minByOrNull { it.georiM }
            boda(best, hwakjeong.map { it.geul }.distinct().take(2))
        }
    }

    // MARK: 알리기(화면 줄) — 아이폰 boda 와 같은 차례

    private fun boda(best: MunHubo?, geul: List<String>) {
        if (!kyeojim) return
        val now = System.currentTimeMillis()
        val el = (now - sijakT) / 1000.0
        if (eodiseo == "jjikgi") {   // 두 번 찍기 — 글자만 모으고 말하지 않음
            for (g in geul) if (ilgeunGeul.add(g)) geulSun.add(g)
            if (el > 90) kkeugi(false)
            return
        }
        if (best == null) {
            if (munBoim && now - boinT > 6000 && !ireotdaMal) {
                ireotdaMal = true
                munBoim = false
                sangtae = "문을 다시 찾는 중입니다."
                mal("문이 카메라에서 벗어났습니다. 폰을 천천히 좌우로 돌려 주십시오.")
            }
            if (!munBoim && ((chatneunMalSu == 0 && el > 10) || (chatneunMalSu == 1 && el > 35))) {
                chatneunMalSu += 1
                mal("문을 찾고 있습니다. 폰을 가슴 앞에 세워 들고 천천히 좌우로 돌려 주십시오.")
            }
            if (el > 90) {
                kkeugi(false)
                mal("문을 찾지 못해 카메라를 끕니다.")
            }
            return
        }
        if (el > 180) {   // 문은 보았으나 3분 넘게 닿지 못함 — 카메라를 오래 켜 두지 않음
            kkeugi(false)
            mal("문 찾기를 오래 켜 두어 카메라를 끕니다. 다시 쓰시려면 문 찾아 줘라고 말씀하십시오.")
            return
        }
        boinT = now
        ireotdaMal = false
        val georeum = max(1, (best.georiM / KameraNun.bopok).roundToInt())
        val si = KameraNun.sigye(best.gak)
        if (best.georiM <= 0.9) {
            for (g in geul) if (ilgeunGeul.add(g)) geulSun.add(g)
            sangtae = "문 바로 앞입니다."
            Eum.naegi(EumJong.DOCHAK)
            KameraNun.jindong("arrive")
            mal("문 바로 앞입니다. ${si}시 방향입니다.", sseuGi = true)
            Girok.namgi("munchatgi_dochak", mapOf("eodi" to eodiseo, "cho" to el.toInt()))
            val moum = ArrayList(geulSun)
            kkeugi(false)
            jadongJjikgi(moum)
            return
        }
        val cheoeum = !munBoim
        munBoim = true
        if (best.georiM < gakkaum - 0.5) { gakkaum = best.georiM; if (!cheoeum) Eum.naegi(EumJong.HWAKSIN) }
        val mm = "${si}시 방향 문, 약 ${georeum}걸음"
        sangtae = mm
        if (cheoeum) {
            Eum.naegi(EumJong.HWAKSIN)
            mal("문이 보입니다. ${mm}입니다.")
            malT = now; majimakSi = si; majimakGeoreum = georeum
        } else if (now - malT >= 3000 && (si != majimakSi || georeum != majimakGeoreum)) {
            mal("$mm.")
            malT = now; majimakSi = si; majimakGeoreum = georeum
        }
        val sae = geul.filter { !ilgeunGeul.contains(it) }
        if (sae.isNotEmpty()) {
            for (g in sae) { ilgeunGeul.add(g); geulSun.add(g) }
            geulMalhagi(sae)
        }
        byeonhwa?.invoke()
    }

    /** 읽은 글자 알리기 — 찍어 두신 문의 글자가 있으면 견주어 맞는 문인지, 옆 문인지(아이폰과 같음) */
    private fun geulMalhagi(sae: List<String>) {
        val gd = gidae
        if (gd == null || gd.second.isEmpty() || majatda) {
            mal("문 둘레 글자, " + sae.joinToString(", ") + ".", MalGeup.JEONGBO)
            return
        }
        val gz = gd.second.map { GeulIlgi.ttuk(it) }.filter { it.isNotEmpty() }
        val matda = sae.any { s ->
            val t = GeulIlgi.ttuk(s)
            gz.any { it == t || (it.length >= 2 && t.length >= 2 && (it.contains(t) || t.contains(it))) }
        }
        val sutja = { x: String -> sutjaRe.containsMatchIn(x) }
        val saeSutja = sae.firstOrNull(sutja)
        if (matda) {
            majatda = true
            Eum.naegi(EumJong.DORAOM)
            mal("찍어 두신 문, ${gd.first}입니다.")
        } else if (saeSutja != null && gd.second.any(sutja)) {
            mal("이 문 글자는 ${saeSutja}입니다. 찍어 두신 ${gd.second.firstOrNull(sutja) ?: ""}과 다릅니다. 옆 문일 수 있습니다.")
        } else {
            mal("문 둘레 글자, " + sae.joinToString(", ") + ".", MalGeup.JEONGBO)
        }
    }

    /** 문 바로 앞을 알린 뒤 두 걸음 지나시면 — 손대지 않고 두 번 찍은 것으로 내 문에 담음(폰 안에만, 아이폰과 같음) */
    private fun jadongJjikgi(geul: List<String>) {
        val damgi = jjakDamgi ?: return
        val w = Wichi.jigeum ?: return
        if (System.currentTimeMillis() - w.ttae >= 30000) return
        val head = Wichi.nachimban
        val gijun = Wichi.georeumSu
        var n = 0
        val r = object : Runnable {
            override fun run() {
                n += 1
                if (n > 25) return
                if (Wichi.georeumSu - gijun < 2) { main.postDelayed(this, 1000); return }
                if (w.ochae > 20) {
                    mal("문을 지나셨습니다. 위성이 흐려 이 문은 내 문에 담지 않았습니다.", MalGeup.JEONGBO, sseuGi = true)
                    return
                }
                var lat = w.lat
                var lon = w.lon
                var bang: Double? = if (head >= 0) head else null
                val w2 = Wichi.jigeum
                if (w2 != null && w2.ochae <= 20 && System.currentTimeMillis() - w2.ttae < 10000) {
                    lat = (w.lat + w2.lat) / 2
                    lon = (w.lon + w2.lon) / 2
                    if (bang == null && Wichi.geori(w.lat, w.lon, w2.lat, w2.lon) >= 1.5) bang = bangwi(w.lat, w.lon, w2.lat, w2.lon)
                }
                val m = try { damgi(lat, lon, bang, geul) } catch (e: Exception) { "" }
                if (m.isNotEmpty()) mal("문을 지나셨습니다. $m", MalGeup.JEONGBO, sseuGi = true)
                Girok.namgi("munchatgi_jjak", mapOf("geul" to geul.size))
            }
        }
        main.postDelayed(r, 1000)
    }

    /** 북쪽 기준 도(0~360) */
    private fun bangwi(a1: Double, o1: Double, a2: Double, o2: Double): Double {
        val p1 = a1 * PI / 180
        val p2 = a2 * PI / 180
        val dl = (o2 - o1) * PI / 180
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        val b = atan2(y, x) * 180 / PI
        return if (b < 0) b + 360 else b
    }

    private fun mal(t: String, g: MalGeup = MalGeup.ANNAE, sseuGi: Boolean = false) {
        if (!KameraNun.kameraMal && !sseuGi && kyeojim) return   // 카메라 눈 말소리 끔 — 소리만(아이폰과 같음)
        Sori.mal(t, g)
    }
}

/** 문 찾기 화면 — 관리자 시험(설정 탭 더 보기의 관리자 시험, 관리자 폰이면 길 찾기 탭 그 밖에 펼치기에도) */
class MunChatgiHwamyeon : KameraHwamyeon("문 찾기") {
    override val bupum: KameraNunBupum get() = MunChatgi
    override val jadong: Boolean = false   // 아이폰과 같이 손으로 켬
    override fun kyeogi(t: GilnunActivity) = MunChatgi.kyeogi("hwamyeon", t, bogi)

    override fun juljul(t: GilnunActivity): View {
        val m = MunChatgi
        val kb = t.danchu(kGeul()) { if (m.kyeojim) m.kkeugi(true) else m.kyeogi("hwamyeon", t, bogi) }
        m.byeonhwa = {
            if (t.wiHwamyeon === this) {
                val n = kGeul()
                if (kb.text.toString() != n) kb.text = n
                bogiIeum()   // 말로 켰을 때(화면 없이 켜짐) 카메라 화면을 붙임
            }
        }
        bogiNeoki(t, kb)
        malsoriDanchu(t)
        alaDuSil(t, "관리자 시험 중인 기능입니다. 안드로이드에는 문 자체를 알아보는 기능이 없어, 문 위나 옆의 호수, 출입구, 화장실, 당기세요 같은 짧은 글자를 카메라로 찾아 몇 시 방향 문, 약 몇 걸음인지 알려 드립니다. 걸음은 글자 크기로 어림한 것이라 틀릴 수 있습니다. 폰을 가슴 앞에 세워 들고 천천히 걸으시면 됩니다. 문 글자가 보이면 확신음과 함께 알려 드리고, 가까워질수록 확신음이 납니다. 3초에 한 번쯤, 방향이나 걸음이 바뀔 때만 말씀드립니다. 내 문에 글자를 담아 두신 문이면 찍어 두신 문인지, 옆 문인지도 알려 드립니다. 문 바로 앞에 이르면 도착 소리와 함께 알려 드리고 카메라를 끕니다. 그 뒤 문을 지나 두 걸음 들어가시면 손대지 않아도 그 문을 내 문에 담습니다. 90초 안에 문을 찾지 못하면 저절로 끕니다. 카메라 눈 말소리를 끄시면 말은 하지 않고 소리로만 알립니다. 인터넷 없이 폰 안에서만 돕니다. 글자가 없는 문은 찾지 못하니 지팡이로 꼭 함께 확인하십시오.")
        return kb
    }

    private fun kGeul() = if (MunChatgi.kyeojim) "문 찾기 끄기 — ${MunChatgi.sangtae}" else "문 찾기 켜기 — 폰을 가슴 앞에 세워 들고 걸으십시오"
}
