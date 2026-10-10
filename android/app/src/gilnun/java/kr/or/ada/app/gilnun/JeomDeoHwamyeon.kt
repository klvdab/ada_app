// 안드로이드 길눈 — 점지도와 걸음 화면들(2.7.0, 묶음 b2 점지도 마저, 대표님 지시)
// 아이폰 JeomView.swift 의 설정 쪽(JeomSeoljeongView·NaeGilView·NaeGilSangseView·JaegiView·NaeMunView·RimoView)과
// SeoljeongDeo 의 점지도 안내 설정(확신음·제대로 가고 있다는 말·지나는 곳), GigiView 의 안내 중 이어폰 단추 받기를 같은 말로 옮겼습니다.
//   목록은 다섯씩, 아래에 더 보기와 이전 보기. 줄에 번호 없음. 줄마다 단추를 여러 개 달지 않고, 지우기는 줄을 길게 눌러(톡백은 사용자 지정 동작)
//   알릴 말은 화면 맨 위 한 줄에도 남기고 말로 한 번 알림. 단추가 바뀌면 커서를 첫 줄로
package kr.or.ada.app.gilnun

import android.text.InputType
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.widget.doAfterTextChanged
import java.util.Locale
import kotlin.math.abs

/** 다섯씩 보여 주는 목록 — 첫 줄을 돌려줌 */
private fun <T> jeomMok5(t: GilnunActivity, l: List<T>, sijak0: Int, jul: (T) -> String, nureum: (T) -> Unit, omgigi: (Int) -> Unit): View? {
    if (l.isEmpty()) return null
    val sijak = if (sijak0 >= l.size) ((l.size - 1) / 5) * 5 else maxOf(0, sijak0)
    val kkeut = minOf(sijak + 5, l.size)
    var cheot: View? = null
    for (n in sijak until kkeut) {
        val x = l[n]
        val b = t.danchu(jul(x)) { nureum(x) }
        if (cheot == null) cheot = b
    }
    if (kkeut < l.size) t.danchu("더 보기") { omgigi(sijak + 5) }
    if (sijak > 0) t.danchu("이전 보기") { omgigi(maxOf(0, sijak - 5)) }
    return cheot
}

/** 알림 한 줄과 커서 옮기기를 함께 다루는 화면 바탕 */
abstract class JeomAllimHwamyeon(jemok: String) : Hwamyeon(jemok) {
    protected var allim = ""
    protected var chojeomHal = false
    protected var cheot: View? = null
    protected var t0: GilnunActivity? = null

    /** 말로 알리고 화면 맨 위에도 남김 */
    protected fun alrigi(t: String) {
        allim = t
        Sori.mal(t)
        chojeomHal = true
        val a = t0 ?: return
        if (a.wiHwamyeon === this) a.dasiGeurigi()
    }

    protected fun allimJul(t: GilnunActivity) {
        t0 = t
        cheot = null
        if (allim.isNotEmpty()) cheot = t.geul(allim, true)
    }

    override fun boilttae(t: GilnunActivity) {
        if (!chojeomHal) return
        chojeomHal = false
        cheot?.let { t.chojeomOmgigi(it) }
    }
}

/** 지우기처럼 되돌릴 수 없는 일 — 한 번 더 여쭘 */
class JeomHwakinHwamyeon(jemok: String, private val mal: String, private val hagi: (GilnunActivity) -> Unit) : Hwamyeon(jemok) {
    override fun chaeugi(t: GilnunActivity) {
        t.geul(mal, true)
        t.danchu("지우기") { hagi(t) }
        t.danchu("그만두기") { t.dwiro() }
    }
}

// MARK: 점지도와 걸음(설정 탭)

class JeomSeoljeongHwamyeon : Hwamyeon("점지도와 걸음") {
    private var pyeol = false
    override fun chaeugi(t: GilnunActivity) {
        JeomSeol.sijak(t)
        NaeGil.sijak(t)
        t.danchu("나만의 점지도 — 늘 다니는 길을 걸어서 그려 두기") { t.yeolgi(NaeGilHwamyeon()) }
        t.danchu("걸음 오차 재기 — 걸음으로 잰 거리와 실제 거리 견주기") { t.yeolgi(JaegiHwamyeon()) }
        t.danchu("내 문 — 지금 선 자리를 내 문으로 담아 두기") { t.yeolgi(NaeMunHwamyeon()) }
        t.danchu("리모컨 배우기 — 블루투스 리모컨 단추를 익혀 두기") { t.yeolgi(RimoHwamyeon()) }
        t.danchu("흔들면 자리 번호 — 긴급통화 열기 또는 국가지점번호") { t.yeolgi(HeundeulHwamyeon()) }
        t.danchu(if (pyeol) "점지도 안내 설정 접기" else "점지도 안내 설정 펼치기") { pyeol = !pyeol; t.dasiGeurigi() }
        if (pyeol) {
            t.danchu("걸을 때 확신음 — " + (if (JeomSeol.hwaksinEum) "켜져 있음 (누르면 끕니다)" else "꺼져 있음 (누르면 켭니다)")) {
                JeomSeol.hwaksinEum = !JeomSeol.hwaksinEum
                if (JeomSeol.hwaksinEum) Eum.naegi(EumJong.HWAKSIN)
                Sori.mal(if (JeomSeol.hwaksinEum) "확신음을 켭니다. 제대로 가고 계시면 짧은 맑은 소리가 납니다." else "확신음을 끕니다. 방향이 틀어졌을 때의 말은 그대로 나옵니다.")
                t.dasiGeurigi()
            }
            t.danchu("제대로 가고 있다는 말 — 점지도에서 ${JeomSeol.hwaksinGan}미터마다 (누르면 바뀝니다)") {
                val g = JeomSeol.hwaksinGan
                JeomSeol.hwaksinGan = if (g == 5) 10 else (if (g == 10) 20 else 5)
                Sori.mal("점지도를 따라 걸으실 때 ${JeomSeol.hwaksinGan}미터마다 제대로 가고 있다고 말씀드립니다.")
                t.dasiGeurigi()
            }
            t.danchu("자봉 목소리 토막 듣기 — " + (if (TomakDeutgi.kyeojim(t)) "켜져 있음 (누르면 끕니다)" else "꺼져 있음 (누르면 켭니다)")) {   // 2.23.0
                val on = !TomakDeutgi.kyeojim(t)
                TomakDeutgi.kyeogi(t, on)
                Sori.mal(if (on) "표시에 닿기 앞서 그린 분이 그 자리에 남긴 짧은 말을 들려 드립니다." else "그린 분의 목소리 토막을 들려 드리지 않습니다. 길눈 안내 말은 그대로 나옵니다.")
                t.dasiGeurigi()
            }
            t.danchu("지나는 곳 안내 — " + (if (JeomSeol.gilOn) "켜져 있음 (누르면 끕니다)" else "꺼져 있음 (누르면 켭니다)")) {
                JeomSeol.gilOn = !JeomSeol.gilOn
                Sori.mal(if (JeomSeol.gilOn) "지나는 길을 알려 드립니다." else "지나는 길을 알리지 않습니다.")
                t.dasiGeurigi()
            }
            t.danchu("안내 중 이어폰 단추 받기 — " + (if (JeomSeol.ieoponDanchu) "켜져 있음 (누르면 끕니다)" else "꺼져 있음 (누르면 켭니다)")) {
                JeomSeol.ieoponDanchu = !JeomSeol.ieoponDanchu
                RemoteDanchu.matchugi()
                Sori.mal(if (JeomSeol.ieoponDanchu) "안내 중에는 이어폰 단추를 길눈이 받습니다. 재생은 다시 듣기, 다음은 다음 갈림길, 이전은 앞 안내입니다."
                    else "이어폰 단추를 음악 앱에 그대로 둡니다.")
                t.dasiGeurigi()
            }
        }
    }
}

// MARK: 나만의 점지도

class NaeGilHwamyeon : JeomAllimHwamyeon("나만의 점지도") {
    private var ireum = ""
    private var hanmadi = ""
    private var jamgeum = ""
    private var sijak = 0
    private var mokChojeom = false
    private var pyeol = false
    private val PYO = listOf("오름턱" to "오름턱 — 올라서는 턱", "내림턱" to "내림턱 — 내려서는 턱", "계단" to "계단",
        "문·입구" to "문·입구", "조심할 곳" to "조심할 곳")

    override fun chaeugi(t: GilnunActivity) {
        NaeGil.sijak(t)
        allimJul(t)
        val gr = NaeGeurigi
        if (gr.geurineun) {
            val b = t.danchu("걷기 끝 — 그리기 마치기") { gr.kkeut(); chojeomHal = true; t.dasiGeurigi() }
            if (cheot == null) cheot = b
            t.danchu("지금 상태 알려 주기") { gr.sangtae() }
            t.geul("그 자리에 닿았을 때 누르십시오.")
            for ((nm, geul) in PYO) t.danchu(geul) { gr.pyo(nm) }
            val e = t.ipryeok("여기 남길 한마디 — 받아쓰기로 말씀하셔도 됩니다", false)
            e.setText(hanmadi)
            e.doAfterTextChanged { hanmadi = it?.toString() ?: "" }
            t.danchu("여기 한마디 남기기") {
                val m = hanmadi.trim()
                if (m.isEmpty()) { Sori.mal("남길 말을 먼저 적어 주십시오."); return@danchu }
                gr.pyo("한마디", m)
                hanmadi = ""
                t.dasiGeurigi()
            }
        } else if (gr.damgilGeot) {
            val e = t.ipryeok("이 길 이름 — 예: 우리 집에서 마을버스까지", false)
            e.setText(ireum)
            e.doAfterTextChanged { ireum = it?.toString() ?: "" }
            if (cheot == null) cheot = e
            t.danchu("나만 쓰기 — 이 기기에만 담기") {
                val g = gr.gilMandeulgi(ireum.trim())
                NaeGil.damgi(g)
                gr.biugi()
                ireum = ""
                alrigi("담았습니다. ${g.title}, ${g.dist.toInt()}미터입니다. 이 기기 안에 있습니다.")
            }
            t.danchu("모두가 쓰도록 점지도에 올리기") {
                val nm = ireum.trim()
                if (nm.isEmpty()) { alrigi("이 길 이름을 먼저 적어 주십시오."); return@danchu }
                if (gr.pts.size < 5) { alrigi("점이 적어 올릴 수 없습니다. 나만 쓰기로 담아 주십시오."); return@danchu }
                val g = gr.gilMandeulgi(nm)
                alrigi("협회 점지도에 보내는 중입니다.")
                Jeomjido.olligi(g) { ok, m ->
                    if (ok) {
                        NaeGil.damgi(g.copy(ollim = true))
                        gr.biugi()
                        ireum = ""
                        alrigi("보탰습니다. ${nm}에서 ${m}까지 ${g.dist.toInt()}미터입니다. 이제 따라 걷기에서 이 길이 나오고, 되돌아가기도 함께 나옵니다.")
                    } else {
                        alrigi(m)
                    }
                }
            }
            t.danchu("담지 않고 버리기") { gr.biugi(); alrigi("그린 길을 버렸습니다.") }
        } else {
            val b = t.danchu("걷기 시작 — 그리기 시작") { gr.sijak(); chojeomHal = true; t.dasiGeurigi() }
            if (cheot == null) cheot = b
            t.geul("우리 집 앞이나 늘 다니는 길을 손수 그려 두는 자리입니다. 다 걸으신 뒤에 나만 쓸지, 모두가 쓰도록 점지도에 올릴지 고르실 수 있습니다. 올리시면 길과 표시와 길 이름만 올라가고 전화번호 같은 것은 가지 않습니다.")
        }
        val mok = NaeGil.mokrok
        t.jemok("내가 그린 길 — ${mok.size}개")
        if (mok.isEmpty()) {
            t.geul("아직 그려 두신 길이 없습니다.")
        } else {
            val c = jeomMok5(t, mok, sijak, { g ->
                "${g.title} · ${g.dist.toInt()}미터 · 표시 ${g.marks.size}개 · ${g.made}" + (if (g.matgim) " · 맡겨 둠" else " · 이 기기에만") + (if (g.ollim) " · 모두와 나눔" else "")
            }, { g -> t.yeolgi(NaeGilSangseHwamyeon(g.id, g.title)) }) { s -> sijak = s; mokChojeom = true; chojeomHal = true; t.dasiGeurigi() }
            if (mokChojeom && c != null) cheot = c
            mokChojeom = false
        }
        t.danchu(if (pyeol) "맡겨 둔 길 찾아오기 접기" else "맡겨 둔 길 찾아오기 펼치기 — 잠금말") { pyeol = !pyeol; t.dasiGeurigi() }
        if (pyeol) {
            t.geul("맡기기는 잠금말을 걸어 협회 서버에 두는 것입니다. 잠금말은 서버로 가지 않으며, 잊으시면 맡긴 길을 다시 풀 수 없습니다. 폰을 바꾸셨을 때 같은 잠금말로 찾아오십시오.")
            val e = t.ipryeok(if (NaeGil.jamgeum.isEmpty()) "잠금말" else "잠금말 — 정해 두셨습니다. 바꾸시려면 새로 적으십시오", false)
            e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            e.setText(jamgeum)
            e.doAfterTextChanged { jamgeum = it?.toString() ?: "" }
            t.danchu("이 잠금말로 정하기") {
                val j = jamgeum.trim()
                if (j.isEmpty()) { alrigi("잠금말을 먼저 적어 주십시오."); return@danchu }
                NaeGil.jamgeum = j
                jamgeum = ""
                alrigi("잠금말을 정했습니다. 이 폰의 열쇠 칸에만 담깁니다.")
            }
            t.danchu("맡겨 둔 길 찾아오기") {
                val j = jamgeum.trim().ifEmpty { NaeGil.jamgeum }
                if (j.isEmpty()) { alrigi("잠금말을 먼저 적어 주십시오."); return@danchu }
                alrigi("찾아오는 중입니다.")
                NaeGil.chajaogi(j) { r ->
                    if (r == null) { alrigi("찾아오지 못했습니다. 통신을 확인해 주십시오."); return@chajaogi }
                    alrigi("맡겨 둔 길 ${r.first}개를 찾아왔습니다." + (if (r.second > 0) " ${r.second}개는 잠금말이 달라 풀지 못했습니다." else ""))
                }
            }
        }
    }
}

class NaeGilSangseHwamyeon(private val id: String, ireum: String) : Hwamyeon("내가 그린 길 — $ireum") {
    override fun chaeugi(t: GilnunActivity) {
        val g = NaeGil.chatgi(id)
        if (g == null) { t.geul("길을 찾지 못했습니다.", true); return }
        t.danchu("이 길 따라 걷기 — ${g.title}") {
            JeomEngine.sijak(t, g, false)
            t.tabGo(0)
            t.yeolgi(JeomGeotgiHwamyeon())
        }
        t.danchu("되돌아가기로 걷기 — 끝에서 처음으로") {
            JeomEngine.sijak(t, g, true)
            t.tabGo(0)
            t.yeolgi(JeomGeotgiHwamyeon())
        }
        if (g.ollim) {
            t.geul("이미 모두와 나눈 길입니다.", true)
        } else {
            t.danchu("모두가 쓰도록 점지도에 올리기") {
                if (g.pts.size < 5) { Sori.mal("이 길은 자리가 적어 올릴 수 없습니다."); return@danchu }
                Sori.mal("올리는 중입니다.")
                Jeomjido.olligi(g) { ok, m ->
                    if (ok) {
                        NaeGil.damgi(g.copy(ollim = true))
                        Sori.mal("${g.title}${MalHagi.eul(g.title)} 모두가 쓰도록 올렸습니다. 이제 다른 분들도 이 길을 따라 걸을 수 있습니다.")
                        if (t.wiHwamyeon === this) t.dasiGeurigi()
                    } else {
                        Sori.mal(m)
                    }
                }
            }
        }
        t.danchu(if (g.matgim) "다시 맡기기 — 잠금말을 걸어 서버에" else "맡기기 — 잠금말을 걸어 서버에") {
            val lock = NaeGil.jamgeum
            if (lock.isEmpty()) { Sori.mal("잠금말이 없어 맡기지 못했습니다. 나만의 점지도 화면의 맡겨 둔 길 찾아오기 펼치기에서 잠금말을 먼저 정해 주십시오."); return@danchu }
            Sori.mal("맡기는 중입니다.")
            NaeGil.matgigi(g, lock) { ok ->
                Sori.mal(if (ok) "${g.title} 맡겼습니다." else "맡기지 못했습니다. 통신을 확인해 주십시오.")
                if (ok && t.wiHwamyeon === this) t.dasiGeurigi()
            }
        }
        t.danchu("이 길 지우기") {
            t.yeolgi(JeomHwakinHwamyeon("이 길 지우기", "이 길을 지울까요? 맡겨 둔 것도 함께 지웁니다.") { a ->
                NaeGil.matgimJiugi(id)
                Sori.mal("지웠습니다.")
                a.dwiro()   // 묻는 화면 닫기
                a.dwiro()   // 내가 그린 길 화면 닫기 — 나만의 점지도로
            })
        }
    }
}

// MARK: 걸음 오차 재기(jaegi.php)

/** 걸음 오차를 재는 동안 위성으로 잰 거리 */
private object JaegiWiseong {
    var dolgo = false
    var gil = 0.0
    var jeon: Pair<Double, Double>? = null
    private var deutgiDoem = false
    fun sijak() {
        gil = 0.0; jeon = null; dolgo = true
        if (!deutgiDoem) {
            deutgiDoem = true
            Wichi.deutgi { w ->
                if (!dolgo || w.georeumChu || w.ochae >= 25) return@deutgi
                val j = jeon
                if (j != null) {
                    val d = Wichi.geori(j.first, j.second, w.lat, w.lon)
                    if (d > 1.5) { gil += d; jeon = Pair(w.lat, w.lon) }
                } else {
                    jeon = Pair(w.lat, w.lon)
                }
            }
        }
    }
}

class JaegiHwamyeon : JeomAllimHwamyeon("걸음 오차 재기") {
    private var sil = "100"
    private var bopokCm = Jeomjido.bannol(Seoljeong.bopok * 100).toInt().toString()
    private var sokdo = "보통"
    private var jipangi = "씀"
    private var doneun = false
    private var t0Ms = 0L
    private var georeum0 = 0
    private var gijunGeomsa: GeoreumGijun? = null
    private var gyeolgwa: List<String> = emptyList()
    private var mal: List<Pair<String, String>> = emptyList()
    private var mok: List<JaegiJul>? = null
    private var mokSijak = 0
    private var pyeol = false

    override fun chaeugi(t: GilnunActivity) {
        allimJul(t)
        if (doneun) {
            val b = t.danchu("다 걸었습니다") { majim() }
            if (cheot == null) cheot = b
        } else if (gyeolgwa.isNotEmpty()) {
            for (g in gyeolgwa) t.geul(g, true)
            t.danchu("이 결과 담기") {
                val m = mal
                alrigi("담는 중입니다.")
                Jeomjido.jaegiDamgi(m) { r -> alrigi(r); mokrokBatgi() }
            }
            t.danchu("한 번 더 재기") { gyeolgwa = emptyList(); alrigi("다시 잽니다. 준비되시면 재기 시작을 누르십시오.") }
        } else {
            val e1 = t.ipryeok("미리 재 둔 실제 거리 (미터)", true)
            e1.setText(sil)
            e1.doAfterTextChanged { sil = it?.toString() ?: "" }
            val e2 = t.ipryeok("내 보폭 (센티미터)", true)
            e2.setText(bopokCm)
            e2.doAfterTextChanged { bopokCm = it?.toString() ?: "" }
            t.danchu("걷는 빠르기 — $sokdo (누르면 바뀝니다)") {
                sokdo = if (sokdo == "보통") "천천히" else (if (sokdo == "천천히") "빠르게" else "보통")
                Sori.mal(sokdo)
                t.dasiGeurigi()
            }
            t.danchu("흰지팡이 — " + (if (jipangi == "씀") "씁니다" else "안 씁니다") + " (누르면 바뀝니다)") {
                jipangi = if (jipangi == "씀") "안씀" else "씀"
                Sori.mal(if (jipangi == "씀") "씁니다" else "안 씁니다")
                t.dasiGeurigi()
            }
            t.danchu("재기 시작") { sijak() }
        }
        t.danchu(if (pyeol) "지금까지 잰 것 보기 접기" else "지금까지 잰 것 보기 펼치기") {
            pyeol = !pyeol
            if (pyeol && mok == null) mokrokBatgi()
            t.dasiGeurigi()
        }
        if (pyeol) {
            val m = mok
            if (m == null) t.geul("불러오는 중입니다.")
            else if (m.isEmpty()) t.geul("아직 잰 것이 없습니다.")
            else {
                val kkeut = minOf(mokSijak + 5, m.size)
                for (n in mokSijak until kkeut) t.geul(m[n].mal, true)
                if (kkeut < m.size) t.danchu("더 보기") { mokSijak += 5; t.dasiGeurigi() }
                if (mokSijak > 0) t.danchu("이전 보기") { mokSijak = maxOf(0, mokSijak - 5); t.dasiGeurigi() }
            }
        }
    }

    private fun sijak() {
        val s = sil.trim().toDoubleOrNull()
        if (s == null || s <= 0) { alrigi("실제 거리를 넣어 주십시오."); return }
        Wichi.wiseongDolligi()
        JaegiWiseong.sijak()
        georeum0 = Wichi.georeumSu
        gijunGeomsa?.meomchum(); gijunGeomsa = GeoreumGijun().seuseuro()   // 2.34.0 (261010-A22) 시작 직후 몰려 들어오는 헛걸음 빼기
        t0Ms = System.currentTimeMillis()
        doneun = true
        alrigi("재고 있습니다. 끝까지 걸으신 뒤 다 걸었습니다를 누르십시오.")
    }

    private fun majim() {
        JaegiWiseong.dolgo = false
        doneun = false
        val s = sil.trim().toDoubleOrNull() ?: 0.0
        val bp = bopokCm.trim().toDoubleOrNull() ?: 70.0
        val cho = (System.currentTimeMillis() - t0Ms) / 1000.0
        val seol = Wichi.georeumHeorak
        val n = if (seol) (gijunGeomsa?.georeum() ?: (Wichi.georeumSu - georeum0)) else 0
        gijunGeomsa = null
        val chu = n * bp / 100
        val og = if (s > 0) abs(chu - s) / s * 100 else 0.0
        val wiGil = JaegiWiseong.gil
        val r = arrayListOf(
            "걸음 ${n}걸음, 걸린 시간 ${Jeomjido.bannol(cho).toInt()}초. 한 걸음을 ${bp.toInt()}센티미터로 치면 " + String.format(Locale.US, "%.1f", chu) + "미터입니다.",
            "실제 ${s.toInt()}미터와 견주면 걸음 오차는 " + String.format(Locale.US, "%.1f", og) + "퍼센트입니다."
        )
        if (s > 0 && wiGil > 0) r.add("위성으로 잰 거리는 " + String.format(Locale.US, "%.1f", wiGil) + "미터, 오차는 " + String.format(Locale.US, "%.1f", abs(wiGil - s) / s * 100) + "퍼센트입니다.")
        else r.add("위성으로는 재지 못했습니다.")
        if (!seol) r.add(0, "이 폰에서 걸음을 셀 수 없었습니다. 폰 설정의 앱, 길눈, 권한에서 신체 활동을 허용해 주십시오.")
        gyeolgwa = r
        mal = listOf("sil" to s.toString(), "geoleum" to n.toString(), "chujeong" to String.format(Locale.US, "%.2f", chu),
            "wui" to String.format(Locale.US, "%.2f", wiGil), "cho" to String.format(Locale.US, "%.1f", cho), "sokdo" to sokdo, "jipangi" to jipangi)
        alrigi("다 재었습니다. " + r.joinToString(" "))
    }

    private fun mokrokBatgi() {
        Jeomjido.jaegiMokrok { r ->
            mok = r ?: emptyList()
            mokSijak = 0
            val a = t0
            if (a != null && a.wiHwamyeon === this) a.dasiGeurigi()
        }
    }
}

// MARK: 내 문 — 문까지 안내에서 가장 먼저 씀(폰 안에만)

class NaeMunHwamyeon : JeomAllimHwamyeon("내 문") {
    private var ireum = ""
    private var sijak = 0
    private class Cheot(val w: Jari, val bang: Double, val ttae: Long)
    private var cheotJjik: Cheot? = null

    override fun chaeugi(t: GilnunActivity) {
        JeomSeol.sijak(t)
        allimJul(t)
        val e = t.ipryeok("문 이름 — 예: 우리 집 현관, 사무실 정문", false)
        e.setText(ireum)
        e.doAfterTextChanged { ireum = it?.toString() ?: "" }
        // 두 번 찍기(이사장님 약속 — 문 두 걸음 앞에서 한 번, 문을 지나 두 걸음 들어가서 한 번)
        if (cheotJjik == null) {
            val b = t.danchu("문 앞에서 한 번 찍기 — 문 두 걸음 앞에서 문 쪽을 보고") { cheotJjikgi() }
            if (cheot == null) cheot = b
        } else {
            val b = t.danchu("문을 지나 한 번 더 찍기 — 두 걸음 들어가서") { dulJjikgi() }
            if (cheot == null) cheot = b
            t.danchu("찍기 그만두기") {
                cheotJjik = null
                if (JeomMunKamera.gigiGaneung()) JeomMunKamera.jjikgiKkeut()
                alrigi("찍기를 그만두었습니다.")
            }
        }
        t.geul("문 두 걸음 앞에서 한 번, 문을 지나 두 걸음 들어가서 한 번 더 찍으시면 두 자리 사이를 문으로, 들어가신 쪽을 들어가는 쪽으로 담습니다. 문 찾기를 쓸 수 있는 폰은 첫 번째 찍을 때 카메라가 문 둘레 글자(호수, 출입구 같은 것)를 함께 읽어 담아 두었다가, 다음에 찾아가실 때 찍어 두신 문인지, 옆 문인지 알려 드립니다. 사진은 담지 않습니다. 담아 두신 문은 점지도 끝 60미터 안에 있으면 문까지 안내에서 가장 먼저 씁니다. 이 폰 안에만 담깁니다. 지우시려면 그 줄을 길게 누르시거나, 톡백에서 그 줄의 동작 가운데 이 문 지우기를 고르십시오.")
        val mok = NaeMun.mokrok
        if (mok.isNotEmpty()) {
            val sijak1 = if (sijak >= mok.size) ((mok.size - 1) / 5) * 5 else sijak
            val kkeut = minOf(sijak1 + 5, mok.size)
            for (n in sijak1 until kkeut) {
                val h = mok[n]
                val geul = h.geul ?: emptyList()
                val b = t.danchu("${h.ireum} · ${h.made}" + (if (geul.isEmpty()) "" else " · 글자 ${geul.joinToString(", ")}")) {
                    Sori.mal("${h.ireum}, ${h.made}에 ${if (h.jjak == true) "두 번 찍어 " else ""}담은 문입니다." + (if (geul.isEmpty()) "" else " 문 둘레 글자는 ${geul.joinToString(", ")}입니다."))
                }
                val jiugi = {
                    t.yeolgi(JeomHwakinHwamyeon("내 문 지우기", "${h.ireum}, 이 문을 지울까요?") { a ->
                        NaeMun.jiugi(h.id)
                        a.dwiro()
                        alrigi("${h.ireum}을 지웠습니다.")
                    })
                }
                b.setOnLongClickListener { jiugi(); true }
                ViewCompat.addAccessibilityAction(b, "이 문 지우기") { _, _ -> jiugi(); true }
            }
            if (kkeut < mok.size) t.danchu("더 보기") { sijak = sijak1 + 5; chojeomHal = true; t.dasiGeurigi() }
            if (sijak1 > 0) t.danchu("이전 보기") { sijak = maxOf(0, sijak1 - 5); chojeomHal = true; t.dasiGeurigi() }
        }
    }

    /** 첫 번째 찍기 — 문 두 걸음 앞. 문 찾기를 쓸 수 있는 폰은 카메라로 문 둘레 글자를 모으기 시작 */
    private fun cheotJjikgi() {
        Wichi.wiseongDolligi()
        val w = Wichi.jigeum
        if (w == null || System.currentTimeMillis() - w.ttae >= 20000) { alrigi("아직 위치를 잡는 중입니다. 잠시 뒤에 다시 눌러 주십시오."); return }
        if (w.ochae > 20) { alrigi("지금은 위성이 흐려 자리가 ${w.ochae.toInt()}미터쯤 어긋날 수 있습니다. 문 바로 앞 밖에서 다시 눌러 주십시오."); return }
        cheotJjik = Cheot(w, Wichi.hapBang, System.currentTimeMillis())
        var m = "한 번 찍었습니다. 문을 지나 두 걸음 들어가신 뒤 한 번 더 찍어 주십시오."
        if (JeomMunKamera.gigiGaneung()) {
            JeomMunKamera.kyeogi?.invoke("jjikgi", null)
            m += " 그동안 카메라가 문 둘레 글자를 읽습니다. 폰을 문 쪽으로 들어 주십시오."
        }
        alrigi(m)
    }

    /** 두 번째 찍기 — 문을 지나 두 걸음. 두 자리 사이를 문으로 */
    private fun dulJjikgi() {
        val c = cheotJjik ?: return
        val geul = if (JeomMunKamera.gigiGaneung()) JeomMunKamera.jjikgiKkeut() else emptyList()
        cheotJjik = null
        if (System.currentTimeMillis() - c.ttae > 120000) { alrigi("첫 번째 찍은 지 2분이 넘어 다시 찍어야 합니다. 문 두 걸음 앞에서 한 번 찍기부터 다시 해 주십시오."); return }
        val w = Wichi.jigeum
        if (w == null || System.currentTimeMillis() - w.ttae >= 20000) { alrigi("위치를 받지 못해 담지 못했습니다. 다시 찍어 주십시오."); return }
        val d = Wichi.geori(c.w.lat, c.w.lon, w.lat, w.lon)
        if (d > 8) { alrigi("두 자리가 ${d.toInt()}미터나 떨어져 문으로 보기 어렵습니다. 위성이 흔들린 것 같습니다. 다시 찍어 주십시오."); return }
        val n = Wichi.hapBang
        var bang: Double? = if (n >= 0) n else (if (c.bang >= 0) c.bang else null)
        if (bang == null && d >= 1.5) bang = Jeomjido.bangwi(c.w.lat, c.w.lon, w.lat, w.lon)
        val m = NaeMun.jjakDamgi(ireum.trim(), (c.w.lat + w.lat) / 2, (c.w.lon + w.lon) / 2, bang, geul)
        ireum = ""
        Girok.namgi("naemun_jjak", mapOf("geul" to geul.size))
        alrigi(m + (if (bang != null) " 들어가신 쪽을 들어가는 쪽으로 적었습니다." else ""))
    }
}

// MARK: 리모컨 배우기 — 블루투스 리모컨 단추를 세 자리에 익혀 두기

class RimoHwamyeon : JeomAllimHwamyeon("리모컨 배우기") {
    private var pyeol = false

    override fun chaeugi(t: GilnunActivity) {
        JeomSeol.sijak(t)
        allimJul(t)
        JeomRimo.allim = { m -> if (t.wiHwamyeon === this) alrigi(m) else Sori.mal(m) }
        val rimo = JeomSeol.rimo
        for (jari in listOf("1", "2", "3")) {
            val b = t.danchu("${jari}번 자리 익히기 — ${JeomRimo.IREUM[jari] ?: ""}. 지금 " + (rimo[jari]?.let { "$it 단추" } ?: "익히지 않음")) {
                JeomRimo.baeugi(jari)
                alrigi("${jari}번 자리를 익힙니다. 지금 리모컨 단추를 한 번 눌러 주십시오. 십 초 안에 눌러 주십시오.")
            }
            if (cheot == null) cheot = b
        }
        t.danchu(if (JeomRimo.siheom) "눌러 보기 멈추기" else "익힌 단추 눌러 보기") {
            if (JeomRimo.siheom) { JeomRimo.siheom = false; alrigi("눌러 보기를 멈췄습니다."); return@danchu }
            if (JeomSeol.rimo.isEmpty()) { alrigi("아직 익힌 단추가 없습니다."); return@danchu }
            JeomRimo.baeuneun = null
            JeomRimo.siheom = true
            alrigi("이제 리모컨 단추를 눌러 보십시오. 어느 자리인지 말씀드리겠습니다.")
        }
        t.danchu("담긴 것 모두 지우기") { JeomSeol.rimo = emptyMap(); alrigi("담긴 것을 모두 지웠습니다.") }
        t.danchu(if (pyeol) "알아 두실 것 접기" else "알아 두실 것 펼치기") { pyeol = !pyeol; t.dasiGeurigi() }
        if (pyeol) t.geul("리모컨 가운데는 소리 크기 단추만 보내는 것이 있습니다. 그런 리모컨은 아무 반응이 없습니다. 글자판처럼 움직이는 리모컨, 흔히 전자책 페이지 넘김 리모컨이라 부르는 것들이 잘 맞습니다. 익힌 단추는 점지도 따라 걷기 화면이 켜져 있을 때 쓰입니다. 폰이 잠겨 있을 때는 이어폰 단추를 쓰십시오. 톡백이 화살표 단추를 먼저 가져갈 수 있습니다.")
    }
}
