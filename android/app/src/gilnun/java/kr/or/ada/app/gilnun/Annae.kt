// 안드로이드 길눈 — 안내 엔진(묶음 b1, 아이폰 AnnaeEngine.swift 를 같은 잣대·같은 시간·같은 말로 옮김)
// 여정에 맞춰 길눈이 스스로 말합니다. 화면이 무엇이든, 폰이 잠겨도(알림 칸의 길눈이 위치를 붙듦), 음악이 나와도 이 엔진이 돕니다.
// 안내는 화면이 아니라 이 엔진이 맡습니다(2026-09-28 아침 길에서 드러난 일을 뿌리부터 막은 아이폰 설계 그대로).
//   걷기: 남은 거리와 시계 방향, 가까워질수록 자주, 방향이 틀어지면 바로, 제대로 가면 확신음, 사거리 알림, 곧 도착, 도착
//   차 안: 남은 거리 눈금(5·3·2·1킬로미터, 500·300·150미터), 지나는 길과 동네, 3분 넘게 말이 없으면 남은 거리
//   저절로 바꾸기: 빠르게 움직이면 곧장 차 안 안내로(묻지 않음), 목적지 가까이서 멈추고 걷기 시작하면 걷는 안내로
// 손을 쓰지 않아도 되게 하는 것이 원칙입니다(한 손에 지팡이, 한 손에 짐).
// 아이폰과 다른 점:
//   - 걸어가기에서 맞는 점지도를 여쭙는 일(아이폰 JeomEngine.georeoGagiBoda·muleum)은 묶음 b2 의 JeomEngine 이 맡고,
//     여정·위성 걷기·사거리 알림은 JeomEngine 의 잇는 자리(yeojeongJeonghagi 등)를 YeojeongEngine.sijak 이 채움
//   - 말하기 설정(얼마나 자세히·되풀이 사이·확신음·꺾이는 곳·지나는 곳·현 위치정보 말할 내용)은 AnnaeSeoljeong 에 아이폰과 같은 이름·처음값으로 둠
//     (설정 화면 SeoljeongDeo 가 같은 열쇠를 씀). 국가지점번호는 Jijeom(Heundeul.kt)
//   - 진동은 Jindong(묶음 b2)
//   - 걸음은 Wichi.georeumSu(앱을 켠 뒤 센 걸음, 몸 센서로 늦음을 메운 값)를 씀(아이폰 oneulGeoreum 자리)
//   - 도착 뒤 카메라 문 찾기·하던 일 멈추기 때 카메라 눈 끄기는 다른 묶음이 dochakHook·meomchumHooks 로 붙임
package kr.or.ada.app.gilnun

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** 거리·시계 방향 말(아이폰 Annae) */
object Annae {
    fun geoMal(d: Double): String = Jeomjido.geoMal(d)
    fun sigyeMal(s: Int): String = if (s == 0) "" else ", ${s}시 방향"
    fun sigyeCha(a: Int, b: Int): Int {
        val c = abs(a - b) % 12
        return min(c, 12 - c)
    }
}

/** 말하기 설정 — 아이폰 Seoljeong 의 같은 이름, 같은 처음값(설정 화면이 생기면 거기서 바꿈). 길눈 설정 파일(gilnun)에 함께 담음 */
object AnnaeSeoljeong {
    private var d: SharedPreferences? = null
    fun sijak(c: Context) { if (d == null) d = c.applicationContext.getSharedPreferences("gilnun", Context.MODE_PRIVATE) }
    private fun i(k: String, m: Int) = d?.getInt(k, m) ?: m
    private fun b(k: String, m: Boolean) = d?.getBoolean(k, m) ?: m
    private fun si(k: String, v: Int) { d?.edit()?.putInt(k, v)?.apply() }
    private fun sb(k: String, v: Boolean) { d?.edit()?.putBoolean(k, v)?.apply() }

    /** 얼마나 자세히 0 짧게 · 1 보통 · 2 자세히 */
    var malSang: Int get() = i("malSang", 2); set(v) = si("malSang", v)
    /** 꺾이는 곳(사거리·갈림길) 알리기 */
    var kkeokOn: Boolean get() = b("kkeokOn", true); set(v) = sb("kkeokOn", v)
    /** 꺾이는 곳을 몇 초 앞에서 */
    var kkeokCho: Int get() = i("kkeokCho", 8); set(v) = si("kkeokCho", v)
    /** 되풀이 사이 시간(초) */
    var doepul: Int get() = i("doepul", 6); set(v) = si("doepul", v)
    /** 차 안 지나는 곳 안내 */
    var gilOn: Boolean get() = b("gilOn", true); set(v) = sb("gilOn", v)
    /** 지나는 곳 말하는 간격(초) */
    var gilGap: Int get() = i("gilGap", 60); set(v) = si("gilGap", v)
    /** 차 안 간판 알림 */
    var ganpanOn: Boolean get() = b("ganpanOn", true); set(v) = sb("ganpanOn", v)
    /** 서 있을 때 창밖 간판 읽기(카메라) */
    var ganpanKamera: Boolean get() = b("ganpanKamera", false); set(v) = sb("ganpanKamera", v)
    /** 걷는 안내 확신음 */
    var hwaksinEum: Boolean get() = b("hwaksinEum", true); set(v) = sb("hwaksinEum", v)
    // 현 위치정보 말할 내용
    var jariJuso: Boolean get() = b("jariJuso", true); set(v) = sb("jariJuso", v)
    var jariGot: Boolean get() = b("jariGot", true); set(v) = sb("jariGot", v)
    var jariJibeon: Boolean get() = b("jariJibeon", false); set(v) = sb("jariJibeon", v)
    var jariJijeom: Boolean get() = b("jariJijeom", true); set(v) = sb("jariJijeom", v)
    var jariOcha: Boolean get() = b("jariOcha", true); set(v) = sb("jariOcha", v)
}

object AnnaeEngine {
    private val main = Handler(Looper.getMainLooper())
    private var ctx: Context? = null
    private var sijakham = false

    var namEunGeori: Double? = null
        private set

    private var majimakMal = 0L
    private var majimakGeoriMal: Double? = null
    private var majimakSigye = 0
    private var gotMal = false
    private val chaGeori = HashSet<Int>()
    var majimakGil = ""
        private set
    private var majimakDong = ""
    private var gilMuleun = 0L
    private var gilMutneunJung = false
    private var neagori: List<Neagori> = emptyList()
    private var neagoriJari: Pair<Double, Double>? = null
    private var neagoriBatneunJung = false
    private val malHanNeagori = HashMap<String, Long>()
    private var neurinSijak = 0L
    private var neurinGeoreum = 0
    private var hwaksinTtae = 0L
    private var naonGijun: Int? = null
    private var gilMalTtae = 0L

    /** 걸어서 닿은 뒤 4초 — 카메라 문 찾기 묶음이 채움(아이폰 MunChatgi.kyeogi("dochak")) */
    var dochakHook: ((String) -> Unit)? = null
    /** 하던 일 멈추기 때 함께 멈출 것(말로 하기의 묻던 말, 카메라 눈 등 — 다른 묶음이 더함) */
    val meomchumHooks = ArrayList<() -> Unit>()

    private val yj get() = YeojeongEngine

    fun sijak(c: Context) {
        if (sijakham) return
        sijakham = true
        ctx = c.applicationContext
        AnnaeSeoljeong.sijak(c)
        Jindong.sijak(c)
        Wichi.deutgi { w -> wichiBatda(w) }
    }

    // MARK: 이용자가 누르는 일

    /** 새 목적지를 정하기 전에 하던 안내를 조용히 모두 끔 — 걷던 점지도, 지하철 안내(이사장님 승인 1) */
    fun saeMokjeokJunbi() {
        jeomKkeut()
        JihacheolEngine.meomchugi()
        namEunGeori = null
    }

    /** 하던 일 멈추기 — 안내, 따라 걷기, 묻던 말, 신호기 찾기, 길눈의 말을 모두 멈추고 첫 화면으로(음악·방송은 그대로) */
    fun haneunIlMeomchum() {
        jeomKkeut()
        JihacheolEngine.meomchugi()
        yj.kkeut()
        namEunGeori = null
        dasiSijak()
        YeojeongMal.mureumChoGihwa()
        if (DoeEngine.sangtae == DoeEngine.Sangtae.ANNAE) DoeEngine.annaeGeuman()   // 되짚어 나가기 안내도 멈춤(기억한 길은 그대로)
        if (SinhogiEngine.chatneunJung) SinhogiEngine.chatgiKkeugi()
        GanpanAllim.kkeut()
        Kamera.modukkeugi(false)   // 문 찾기·글자 읽기·사람 감지 같은 카메라 눈도 멈춤
        for (f in ArrayList(meomchumHooks)) try { f() } catch (e: Exception) {}
        Sori.meomchugi()
        MalHagi.hwalseong?.get()?.cheotHwamyeonEuro(null)
        Girok.namgi("haneunil_meomchum")
        Sori.mal("하던 일을 멈췄습니다. 어디로 가실까요?")
    }

    /** 목적지를 정하고 걸어가기(위성 안내) */
    fun georeoGagi(j: Jangso) {
        saeMokjeokJunbi()
        yj.jeonghagi(Mokjeok(j.ireum, j.lat, j.lon, j.juso))
        georeoGagi()
    }

    /** 목적지를 정하고 차에 탐 */
    fun chaTagi(j: Jangso) {
        saeMokjeokJunbi()
        yj.jeonghagi(Mokjeok(j.ireum, j.lat, j.lon, j.juso))
        chaTatda()
    }

    /** 목적지를 정하고 지하철로 — 먼저 타는 역 출구까지 걷는 안내 */
    fun jihacheolGagi(j: Jangso, g: JihaGil, malEopsi: Boolean = false) {
        jeomKkeut()
        JihacheolEngine.meomchugi()
        yj.jeonghagi(Mokjeok(j.ireum, j.lat, j.lon, j.juso))
        yj.jihaNoki(g)
        yj.talgeotJeonghagi(Talgeot.JIHACHEOL, true)
        yj.danggyeBakkugi(Danggye.TANEUN_GOT_KKAJI)
        dasiSijak()
        if (malEopsi) return
        malHagi("${g.ipgu.ireum}까지 걷는 안내를 시작합니다.")
        jigeumBoda()
    }

    /** 목적지를 정하고 버스로 — 먼저 정류장까지 걷는 안내 */
    fun beoseuGagi(j: Jangso, jr: Jeongryujang) {
        jeomKkeut()
        JihacheolEngine.meomchugi()
        yj.jeonghagi(Mokjeok(j.ireum, j.lat, j.lon, j.juso))
        yj.beoseuNoki(BeoseuGil(jr, false))
        yj.talgeotJeonghagi(Talgeot.BEOSEU, true)
        yj.danggyeBakkugi(Danggye.TANEUN_GOT_KKAJI)
        dasiSijak()
        malHagi("${jr.ireum} 정류장까지 걷는 안내를 시작합니다.")
        jigeumBoda()
    }

    /** 버스에 탔습니다 */
    fun beoseuTatda() {
        if (yj.jigeum == null) return
        yj.talgeotJeonghagi(Talgeot.BEOSEU, true)
        yj.danggyeBakkugi(Danggye.TANEUN_JUNG)
        dasiSijak()
        malHagi("버스 안 안내를 시작합니다.")
        jigeumBoda()
    }

    /** 탈것 바로잡기 — 바로잡은 것이 가장 앞섬 */
    fun talgeotBarojapgi(t: Talgeot) {
        if (yj.jigeum == null) return
        yj.talgeotBarojapgi(t)
        malHagi("${t.ireum}로 알겠습니다.")
    }

    fun georeoGagi() {
        if (yj.jigeum == null) return
        if (JeomEngine.dochakHam) jeomKkeut()   // 점지도로 닿은 뒤 다시 걸으시면 위성 안내로
        yj.talgeotJeonghagi(Talgeot.GEOREUM, false)
        yj.danggyeBakkugi(Danggye.NAM_EUN_GIL)
        dasiSijak()
        // 2.18.0 흰지팡이 당부, 보폭을 재기 전이면 미터로 안내한다고 알림(이사장님 승인)
        malHagi("걷는 안내를 시작합니다. 흰지팡이를 꼭 짚어 주십시오." + (if (Seoljeong.bopokJaem) "" else " 보폭을 아직 재지 않으셔서 걸음 수 대신 미터로 알려 드립니다."))
        jigeumBoda()
    }

    fun chaTatda() {
        if (yj.jigeum == null) return
        jeomKkeut()
        JihacheolEngine.meomchugi()
        yj.jihaNoki(null)
        yj.beoseuNoki(null)
        yj.talgeotJeonghagi(Talgeot.CHA, true)
        yj.danggyeBakkugi(Danggye.TANEUN_JUNG)
        dasiSijak()
        malHagi("차 안 안내를 시작합니다.")
        jigeumBoda()
    }

    fun naeryeotda(jadong: Boolean = false, mal: String? = null) {
        if (yj.jigeum == null) return
        JihacheolEngine.meomchugi()
        GanpanAllim.kkeut()   // 창밖 간판 읽기 카메라 끔
        yj.talgeotJeonghagi(Talgeot.GEOREUM, false)
        yj.danggyeBakkugi(Danggye.NAM_EUN_GIL)
        dasiSijak()
        malHagi(mal ?: (if (jadong) "차에서 내리신 것 같습니다. 남은 길을 걸어서 안내합니다." else "남은 길을 걸어서 안내합니다."))
        Girok.namgi("naerim", mapOf("jadong" to jadong))
        jigeumBoda()
    }

    fun kkeut() {
        jeomKkeut()   // 점지도 따라 걷기도 함께 마침
        JihacheolEngine.meomchugi()
        yj.kkeut()
        namEunGeori = null
        dasiSijak()
        malHagi("여정을 끝냈습니다.")
    }

    /** 지금 어떻게 가고 있습니까 */
    fun hyeonhwang() {
        // 점지도를 따라 걷는 중이면 점지도의 남은 거리
        if (JeomEngine.georeoJung) { JeomEngine.jigeumEodiDeutgi(); return }
        val y = yj.jigeum ?: run { jigeumJari(); return }
        val mok = y.mokjeok.ireum
        if (y.danggye == Danggye.DOCHAK) {
            malHagi("${mok}에 도착했습니다. 여정을 끝내시려면 여정 끝내기를 누르십시오.")
            return
        }
        val b = y.beoseu
        if (b != null && y.danggye == Danggye.TANEUN_GOT_KKAJI) {
            malHagi(if (b.dochak) "${b.jeongryujang.ireum} 정류장에 닿았습니다. 버스에 타시면 저절로 버스 안 안내로 바뀝니다."
                else "${b.jeongryujang.ireum} 정류장까지 걸어가는 중입니다.")
            return
        }
        if (y.jiha != null && (y.danggye == Danggye.TANEUN_GOT_KKAJI || y.danggye == Danggye.TANEUN_JUNG)) {
            malHagi(JihacheolEngine.hyeonhwang())
            return
        }
        val geotna = y.danggye != Danggye.TANEUN_JUNG
        var m = "${mok}까지 " + (if (geotna) "걸어서" else yj.talgeot.ireum + "로") + " 가는 중입니다."
        val w = Wichi.jigeum
        if (w != null) {
            val d = Wichi.geori(w.lat, w.lon, y.mokjeok.lat, y.mokjeok.lon)
            m += " 남은 거리 ${gm(d)}"
            if (geotna) {
                m += Annae.sigyeMal(sigye(w, y.mokjeok.lat, y.mokjeok.lon)) + "."
            } else {
                m += "."
                if (majimakGil.isNotEmpty()) m += " 지금 달리는 길은 ${majimakGil}입니다."
            }
        } else {
            m += " 아직 위치를 잡는 중입니다."
        }
        malHagi(m)
    }

    /** 지금 내 자리 듣기 — 도로명 주소와 가까운 출구·건물, 위성 오차, 날씨 한 마디(아이폰 jigeumJari) */
    fun jigeumJari() {
        val w = Wichi.jigeum
        if (w == null) {
            malHagi(if (!Wichi.heorakItda) "위치 허락이 없어 자리를 알 수 없습니다. 폰 설정의 앱, 길눈, 권한에서 위치를 허용해 주십시오."
                else "아직 위치를 잡는 중입니다. 잡히면 다시 눌러 주십시오.")
            return
        }
        malHagi("지금 자리를 알아보는 중입니다.", MalGeup.JEONGBO)
        Chatgi.jarimalJson(w.lat, w.lon) { o ->
            Nalssi.mal { n ->
                malHagi(jariMalMandeulgi(o, w) + (if (n.isEmpty()) "" else " 날씨는 $n."))
            }
        }
    }

    /** 현 위치정보 말할 내용 — 설정에서 고른 것만(주소, 가까운 곳, 지번, 위성 오차) */
    fun jariMalMandeulgi(o: JSONObject?, w: Jari): String {
        val s = AnnaeSeoljeong
        val t = ArrayList<String>()
        if (o != null) {
            val juso = Jeomjido.gul(o, "juso")
            if (s.jariJuso && juso.isNotEmpty()) t.add(juso)
            val jb = Jeomjido.gul(o, "jibeon")
            if (s.jariJibeon && jb.isNotEmpty()) t.add("지번 $jb")
            if (s.jariGot) {
                val c = o.optJSONObject("chulgu")
                val g = o.optJSONObject("gakkaun")
                val cn = c?.let { Jeomjido.gul(it, "ireum") } ?: ""
                val gn = g?.let { Jeomjido.gul(it, "ireum") } ?: ""
                if (c != null && cn.isNotEmpty()) t.add("${cn}에서 ${(Jeomjido.su(c, "meter") ?: 0.0).toInt()}미터")
                else if (g != null && gn.isNotEmpty()) t.add("${gn}에서 ${(Jeomjido.su(g, "meter") ?: 0.0).toInt()}미터")
            }
            val m = Jeomjido.gul(o, "mal")
            if (t.isEmpty() && !s.jariJijeom && !s.jariOcha && m.isNotEmpty()) t.add(m)
        } else if (!s.jariJijeom) {
            t.add("지금 자리 이름을 받지 못했습니다. 통신이 끊겼을 수 있습니다")
        }
        if (s.jariJijeom) Jijeom.mal(w.lat, w.lon)?.let { t.add(it) }
        if (s.jariOcha) t.add("위성 오차 약 ${max(1, w.ochae.toInt())}미터")
        if (t.isEmpty()) t.add("말할 내용이 모두 꺼져 있습니다. 설정 탭의 현 위치정보 말할 내용에서 켜 주십시오")
        return t.joinToString(". ") + "."
    }

    // MARK: 걸어가기에서 점지도 여쭘(묶음 b2 JeomEngine.georeoGagiBoda·muleum 을 부름)

    /** 맞는 점지도가 있으면 "점지도로 걸을까요, 위성으로 걸을까요" 한 번 여쭘(점지도를 먼저 권함). 없으면 곧장 위성 걷는 안내 */
    fun georeoGagiBoda(j: Jangso, mutgi: (String?) -> Unit) {
        JihacheolEngine.meomchugi()
        val c = ctx
        if (c == null) { mutgi(null); georeoGagi(j); return }
        JeomEngine.georeoGagiBoda(c, JeomMokjeok(j.ireum, j.juso, j.lat, j.lon), mutgi)
    }

    /** 걷던 점지도·불러오기·여쭘을 말없이 끔(아이폰 JeomEngine.yeojeongKkeut) */
    private fun jeomKkeut() {
        JeomEngine.yeojeongKkeutJeom()
    }

    // MARK: 워치·이어폰

    /** 걸어서 가는 중인가 — 목적지까지 걷거나, 타는 곳까지 걷는 차례 */
    val geonneunJung: Boolean
        get() {
            val y = yj.jigeum ?: return false
            return y.danggye == Danggye.NAM_EUN_GIL || y.danggye == Danggye.TANEUN_GOT_KKAJI
        }

    /** 다음 갈림길 — 가는 쪽(앞쪽 10시~2시) 200미터 안에서 가장 가까운 사거리·갈림길 */
    /** 2.10.0 (261003-W1) 워치 두 번 집기 한 번 — 지금 형편에 맞는 한마디(아이폰 2.41.0 watchJigeumMal 과 같음) */
    fun watchJigeumMal(): String {
        if (JeomEngine.georeoJung) return JeomEngine.daeumMuotMal()
        val y = yj.jigeum
        if (y == null) {
            val jy = KolJiyeok.majimak?.let { "지금 계신 곳은 ${it.sido} ${it.sigungu.firstOrNull() ?: ""} 쪽입니다." } ?: ""
            val t = TalgeotGamji.chujeong
            val ta = if (t == Talgeot.GEOREUM) "" else " " + (if (t == Talgeot.JIHACHEOL) "지하철을" else "${t.ireum}를") + " 타고 계신 것으로 보입니다."
            return ("가시는 곳이 아직 없습니다. $jy$ta").trim()
        }
        val g = y.jiha
        if (g != null) {
            if (y.danggye == Danggye.TANEUN_JUNG && (JihacheolEngine.dolgo || g.kkeutnam)) return JihacheolEngine.hyeonhwang()
            if (y.danggye == Danggye.TANEUN_GOT_KKAJI) {
                val w = Wichi.jigeum
                if (w != null && !g.ipguDochak) {
                    val d = Wichi.geori(w.lat, w.lon, g.ipgu.lat, g.ipgu.lon)
                    return "${g.ipgu.ireum}까지 ${gm(d)}${Annae.sigyeMal(sigye(w, g.ipgu.lat, g.ipgu.lon))}."
                }
                return JihacheolEngine.hyeonhwang()
            }
        }
        if (y.danggye == Danggye.TANEUN_JUNG) {
            val w = Wichi.jigeum ?: return "${y.mokjeok.ireum}으로 가는 중입니다. 위치를 다시 잡는 중입니다."
            val d = Wichi.geori(w.lat, w.lon, y.mokjeok.lat, y.mokjeok.lon)
            val gojang = KolJiyeok.majimak?.let { " 지금 ${it.sido} ${it.sigungu.firstOrNull() ?: ""} 쪽을 지나고 있습니다." } ?: ""
            return "${y.mokjeok.ireum}까지 ${gm(d)} 남았습니다.$gojang"
        }
        return daeumGalrimMal()
    }

    fun daeumGalrimMal(): String {
        if (JeomEngine.georeoJung) return JeomEngine.daeumMuotMal()
        val w = Wichi.jigeum ?: return "아직 위치를 잡는 중입니다."
        neagoriBoda(w, false)   // 자료만 받고 따로 말하지 않음(대답과 겹치지 않게)
        var gakka: Pair<Double, Neagori>? = null
        for (n in neagori) {
            if (n.mal.isEmpty()) continue
            val d = Wichi.geori(w.lat, w.lon, n.lat, n.lon)
            if (d <= 8 || d >= 200) continue
            if (w.banghyang >= 0) {
                val s = Jeomjido.sigyeBanghyang(w.banghyang, Jeomjido.bangwi(w.lat, w.lon, n.lat, n.lon))
                if (!(s == 12 || s == 11 || s == 1 || s == 10 || s == 2)) continue
            }
            val gk = gakka
            if (gk == null || d < gk.first) gakka = d to n
        }
        val g = gakka
        if (g != null) return "다음 갈림길. ${gm(g.first)} 앞, ${g.second.mal}입니다."
        val y = yj.jigeum
        if (y != null) {
            val d = Wichi.geori(w.lat, w.lon, y.mokjeok.lat, y.mokjeok.lon)
            return "앞쪽 가까이에는 갈림길 자료가 없습니다. ${y.mokjeok.ireum}까지 ${gm(d)}${Annae.sigyeMal(sigye(w, y.mokjeok.lat, y.mokjeok.lon))}."
        }
        return "앞쪽 가까이에는 갈림길 자료가 없습니다."
    }

    /** 점지도를 따라 걷는 동안에도 사거리·갈림길을 알림(JeomEngine 이 부르면 됨 — 아이폰 neagoriBakkeseo) */
    fun neagoriBakkeseo(w: Jari) { neagoriBoda(w, true) }

    // MARK: 속

    private fun malHagi(t: String, g: MalGeup = MalGeup.ANNAE) {
        Sori.mal(t, g)
        majimakMal = System.currentTimeMillis()
    }

    private fun dasiSijak() {
        ttJiugi()   // 2.15.0
        bgJiugi()   // 2.16.0
        majimakGeoriMal = null
        majimakSigye = 0
        gotMal = false
        chaGeori.clear()
        neurinSijak = 0L
        naonGijun = null
        majimakMal = 0L
        hwaksinTtae = System.currentTimeMillis()
    }

    private fun jigeumBoda() {
        Wichi.wiseongDolligi()
        val w = Wichi.jigeum
        if (w != null) wichiBatda(w)
        else malHagi("위치를 잡는 중입니다. 잡히면 바로 안내합니다.", MalGeup.JEONGBO)
    }

    private fun sigye(w: Jari, lat: Double, lon: Double): Int {
        val bang = Jeomjido.bangwi(w.lat, w.lon, lat, lon)
        val apjjok = if (w.banghyang >= 0 && w.sokdo > 0.8) w.banghyang else Wichi.hapBang
        return if (apjjok >= 0) Jeomjido.sigyeBanghyang(apjjok, bang) else 0
    }

    /** 빠르기로 알아챈 탈것이 바뀜(YeojeongEngine 이 부름) */
    fun talgeotBakkwim(t: Talgeot) {
        val y = yj.jigeum ?: return
        // 2.9.0 탈것 알아채기가 알아챈 지하철·버스도 이어 받음(아이폰 2.40.0과 같음)
        if (t == Talgeot.JIHACHEOL) {
            // 2.25.0 아이폰 2.44.0과 같게 — 기차·고속버스를 타고 가는 중 터널에서 「지하철을 타신 것 같습니다」로 덮지 않음, 차로 바로잡으신 때도 그대로
            if (y.danggye == Danggye.TANEUN_JUNG && (y.talgeot == Talgeot.GICHA || y.talgeot == Talgeot.GOSOKBEOSEU || yj.sokdoChujeong == Talgeot.GICHA)) return
            if (y.barojabeum && y.talgeot == Talgeot.CHA && y.danggye == Danggye.TANEUN_JUNG) return
            if (y.barojabeum && y.talgeot == Talgeot.BEOSEU && !TalgeotGamji.jiha) return
            val g = y.jiha
            if (g != null) {
                if (y.danggye == Danggye.TANEUN_GOT_KKAJI && !JihacheolEngine.dolgo) {
                    if (!g.ipguDochak) JihacheolEngine.ipguDochak()
                    JihacheolEngine.tatda(true)
                }
                return
            }
            if (!(y.danggye == Danggye.NAM_EUN_GIL || y.danggye == Danggye.EOTTEOKE || (y.danggye == Danggye.TANEUN_JUNG && y.talgeot != Talgeot.BEOSEU))) return
            malHagi("지하철을 타신 것 같습니다. 지하철 길을 찾습니다.")
            JihacheolEngine.jungganSijak(y.mokjeok.jangso) { ok, mal ->
                if (ok) Girok.namgi("jadong_jihacheol") else malHagi("$mal 차 안 안내로 잇습니다.")
            }
            return
        }
        if (t == Talgeot.BEOSEU) {
            if (y.beoseu != null || y.jiha != null || (y.barojabeum && y.talgeot != Talgeot.CHA)) return
            if (!(y.danggye == Danggye.NAM_EUN_GIL || y.danggye == Danggye.EOTTEOKE || y.danggye == Danggye.TANEUN_JUNG)) return
            yj.talgeotJeonghagi(Talgeot.BEOSEU, false)
            yj.danggyeBakkugi(Danggye.TANEUN_JUNG)
            dasiSijak()
            malHagi("정류장마다 서는 것을 보니 버스를 타신 것 같습니다. 버스 안 안내로 잇습니다.")
            Girok.namgi("jadong_beoseu", mapOf("gil" to "umjigim"))
            jigeumBoda()
            return
        }
        if (t == Talgeot.GEOREUM) {
            // 2.25.0 아이폰과 같게 — 걸음 15초만으로는 내리신 것으로 보지 않음(KTX·버스 안에서 잠깐 걸어도 차 안 안내가 끝나던 일).
            // 내리심은 목적지 가까이에서 멈췄다 걷는 것을 보는 쪽과 「내렸습니다」 단추가 맡음
            return
        }
        if (t != Talgeot.CHA && t != Talgeot.GICHA) return
        // 버스 정류장에서 기다리다 빠르게 움직이면 버스에 타신 것
        if (y.beoseu != null && y.danggye == Danggye.TANEUN_GOT_KKAJI) {
            yj.talgeotJeonghagi(Talgeot.BEOSEU, true)
            yj.danggyeBakkugi(Danggye.TANEUN_JUNG)
            dasiSijak()
            malHagi("버스가 움직이는 것 같습니다. 버스 안 안내를 시작합니다.")
            Girok.namgi("jadong_beoseu")
            jigeumBoda()
            return
        }
        if (y.danggye != Danggye.NAM_EUN_GIL && y.danggye != Danggye.EOTTEOKE) return
        jeomKkeut()
        JihacheolEngine.meomchugi()
        yj.jihaNoki(null)
        yj.beoseuNoki(null)
        yj.talgeotJeonghagi(t, false)
        yj.danggyeBakkugi(Danggye.TANEUN_JUNG)
        dasiSijak()
        malHagi("빠르게 움직이고 계십니다. ${if (t == Talgeot.GICHA) "기차" else "차"} 안 안내로 바꿉니다.")
        Girok.namgi("jadong_cha", mapOf("t" to t.raw))
        jigeumBoda()
    }

    private fun wichiBatda(w: Jari) {
        val y = yj.jigeum
        if (y == null) { namEunGeori = null; return }
        val d = Wichi.geori(w.lat, w.lon, y.mokjeok.lat, y.mokjeok.lon)
        namEunGeori = d
        // 점지도를 따라 걷는 동안(도착 전)과 점지도로 걸을지 여쭙는 동안에는 점지도 엔진이 안내를 맡음
        // 점지도로 닿은 뒤에는 이 엔진이 다시 맡음(차를 타시거나 다시 걸으실 때 조용하지 않게)
        if (JeomEngine.georeoJung || JeomEngine.muleum != null || JeomEngine.bulleoneun) return
        val b = y.beoseu
        if (b != null && y.danggye == Danggye.TANEUN_GOT_KKAJI) {
            val jr = b.jeongryujang
            if (!b.dochak) georeumAnnae(w, jr.ireum + " 정류장", jr.lat, jr.lon, true, y)
            return
        }
        val g = y.jiha
        if (g != null) {
            when (y.danggye) {
                Danggye.TANEUN_GOT_KKAJI -> if (!g.ipguDochak) georeumAnnae(w, g.ipgu.ireum, g.ipgu.lat, g.ipgu.lon, true, y)
                Danggye.TANEUN_JUNG -> if (g.kkeutnam) naonGeotBoda(w)
                Danggye.NAM_EUN_GIL -> { val t = bgJari(w, y.mokjeok.lat, y.mokjeok.lon, y.mokjeok.ireum); georeumAnnae(w, y.mokjeok.ireum, t.first, t.second, false, y) }   // 2.16.0 볼거리 자리
                else -> {}
            }
            return
        }
        when (y.danggye) {
            Danggye.TANEUN_JUNG -> chaAnnae(w, d, y)
            Danggye.NAM_EUN_GIL -> { val t = bgJari(w, y.mokjeok.lat, y.mokjeok.lon, y.mokjeok.ireum); georeumAnnae(w, y.mokjeok.ireum, t.first, t.second, false, y) }   // 2.16.0 볼거리 자리
            else -> {}
        }
    }

    // MARK: 걷기

    // MARK: 2.16.0 마지막 스무 걸음과 볼거리(이사장님 승인 2026-10-06, 아이폰 길눈 2.48.0과 같음)
    //   목적지 80미터 앞에서 자봉이 남긴 볼거리(서버 lvd-jabong)를 받아, 있으면 그 정확한 자리로 이끎.
    //   스무 미터 안에서는 걸음 수와 시 방향으로 좁혀 말하고, 닿으면 볼거리 이름과 만져지는 것을 알려 드림.
    private class Bolgeori(val ireum: String, val mal: String, val lat: Double, val lon: Double)
    private var bgMok: Pair<Double, Double>? = null
    private var bg: Bolgeori? = null
    private var bgBatneun = false
    private var magakMalTtae = 0L
    private var magakSu = -1
    private var magakSigye = 0

    private fun bgJiugi() { bgMok = null; bg = null; magakSu = -1; magakSigye = 0 }

    /** 최종 목적지의 자리 — 가까이에 볼거리 기록이 있으면 그 자리 */
    private fun bgJari(w: Jari, lat: Double, lon: Double, ireum: String): Pair<Double, Double> {
        val m = bgMok
        if (m != null && (abs(m.first - lat) > 0.000001 || abs(m.second - lon) > 0.000001)) bgJiugi()
        if (bgMok == null && !bgBatneun && Wichi.geori(w.lat, w.lon, lat, lon) <= 80) {
            bgBatneun = true
            bgMok = Pair(lat, lon)
            val f = { v: Double -> String.format(java.util.Locale.US, "%.6f", v) }
            Tongsin.json("/jabong/hamkke.php", mapOf("a" to "bolgeori", "lat" to f(lat), "lon" to f(lon), "r" to "40")) { o ->
                bgBatneun = false
                val rows = o?.optJSONArray("rows")
                val ls = ArrayList<Bolgeori>()
                if (rows != null) for (i in 0 until rows.length()) {
                    val r = rows.optJSONObject(i) ?: continue
                    ls.add(Bolgeori(r.optString("ireum", ""), r.optString("mal", ""), r.optDouble("lat"), r.optDouble("lon")))
                }
                val nm = ireum.replace(" ", "")
                bg = ls.firstOrNull { it.ireum.isNotEmpty() && nm.contains(it.ireum.replace(" ", "")) }
                    ?: ls.firstOrNull { Wichi.geori(lat, lon, it.lat, it.lon) <= 25 }
                bg?.let { Girok.namgi("bolgeori_chajeum", mapOf("ireum" to it.ireum)) }
            }
        }
        val b = bg
        return if (b != null) Pair(b.lat, b.lon) else Pair(lat, lon)
    }

    /** 마지막 스무 미터 — 걸음 수와 시 방향(4초에 한 번, 바뀔 때만). 맡았으면 참 */
    private fun magakAnnae(w: Jari, d: Double, lat: Double, lon: Double, mok: String): Boolean {
        if (d > 20) return false
        // 2.18.0 보폭을 재기 전에는 걸음 수 대신 미터로
        val jaem = Seoljeong.bopokJaem
        val bp = if (jaem && Seoljeong.bopok > 0.3) Seoljeong.bopok else 1.0
        val su = max(1, Math.round(d / bp).toInt())
        val dan = if (jaem) "걸음" else "미터"
        val s = sigye(w, lat, lon)
        val now = System.currentTimeMillis()
        if (now - magakMalTtae < 4000) return true
        if (magakSu < 0) {
            magakMalTtae = now; magakSu = su; magakSigye = s
            malHagi("곧 도착합니다. ${bg?.ireum ?: mok}까지 ${su}$dan" + (if (s == 0) "." else ", ${s}시 방향."))
            if (s != 0) Jindong.banghyang(s)
        } else if (abs(su - magakSu) >= 2 || (s != 0 && s != magakSigye)) {
            magakMalTtae = now; magakSu = su; magakSigye = s
            malHagi("${su}$dan" + (if (s == 0) "." else ", ${s}시 방향."))
            if (s != 0) Jindong.banghyang(s)
        }
        return true
    }

    // MARK: 2.20.0 걷는 중 거리는 걸음 수로, 건널목·계단 미리 알림, 걷는 자리 기록(이사장님 승인 2026-10-06, 아이폰 2.52.0과 같음)
    /** 걷는 중 거리 말 — 보폭을 재 두셨으면 걸음 수로(300미터 넘으면 미터도 함께), 차·버스 안이면 미터 */
    fun gm(d: Double): String {
        val georeum = (YeojeongEngine.jigeum?.talgeot ?: Talgeot.GEOREUM) == Talgeot.GEOREUM
        val bp = Seoljeong.bopok
        if (!georeum || !Seoljeong.bopokJaem || bp <= 0.3) return Annae.geoMal(d)
        val n = max(1, Math.round(d / bp).toInt())
        return if (d >= 300) "약 ${n}걸음, ${Math.round(d / 10).toInt() * 10}미터쯤" else "약 ${n}걸음"
    }

    private class TtGugan(val jong: String, val lat: Double, val lon: Double, val geori: Double)
    private var ttGugan: List<TtGugan> = emptyList()
    private val ttGuganMi = HashSet<Int>()
    private val ttGuganAp = HashSet<Int>()
    private var jariGirokT = 0L

    /** 서버가 아는 건널목·계단·다리·지하도를 열다섯 미터쯤 앞에서 미리, 닿으면 한 번 더. 건널목이면 음향신호기를 저절로 살핌 */
    private fun guganAllim(w: Jari): String? {
        for ((i, g) in ttGugan.withIndex()) {
            if (g.jong !in setOf("건널목", "계단", "다리", "지하도")) continue
            val d = Wichi.geori(w.lat, w.lon, g.lat, g.lon)
            if (i !in ttGuganAp && d <= max(4.0, min(w.ochae, 8.0))) {
                ttGuganAp.add(i); ttGuganMi.add(i)
                Girok.namgi("gugan_ap", mapOf("jong" to g.jong))
                return when (g.jong) {
                    "건널목" -> "건널목 앞입니다. 길이 ${gm(g.geori)}. 신호와 차 소리를 확인하신 뒤 건너십시오."
                    "계단" -> "계단 앞입니다. 지팡이로 첫 계단을 확인하십시오. 길이 ${gm(g.geori)}."
                    "다리" -> "다리에 들어섭니다. 길이 ${gm(g.geori)}."
                    else -> "지하도 입구입니다. 길이 ${gm(g.geori)}."
                }
            }
            if (i !in ttGuganMi && d <= 15) {
                ttGuganMi.add(i)
                val josa = if (g.jong == "다리" || g.jong == "지하도") "가" else "이"
                var m = "${gm(d)} 앞에 ${g.jong}$josa 있습니다."
                if (g.jong == "건널목") {
                    m += " 음향신호기를 살펴 드리겠습니다."
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ SinhogiEngine.juByeonSalpigi() }, 4000)
                }
                return m
            }
        }
        return null
    }

    /** 걷는 동안 10초마다 자리·방향을 기록(관리자 폰만) — 방향이 어긋난 까닭을 나중에 찾기 위해 */
    private fun jariGirok(w: Jari, lat: Double, lon: Double) {
        val now = System.currentTimeMillis()
        if (Yeolsoe.eumakTk().isEmpty() || now - jariGirokT < 10000) return
        jariGirokT = now
        var mk = Pair(lat, lon)
        if (ttAn.isNotEmpty() && ttI < ttAn.size) mk = Pair(ttAn[ttI].lat, ttAn[ttI].lon)
        Girok.namgi("georeum_jari", mapOf(
            "la" to Math.round(w.lat * 1_000_000) / 1_000_000.0, "lo" to Math.round(w.lon * 1_000_000) / 1_000_000.0,
            "oc" to w.ochae.toInt(), "bh" to w.banghyang.toInt(), "nc" to Wichi.nachimban.toInt(),
            "sd" to Math.round(w.sokdo * 10) / 10.0, "s" to sigye(w, mk.first, mk.second),
            "d" to Wichi.geori(w.lat, w.lon, mk.first, mk.second).toInt(), "gc" to w.georeumChu, "ti" to ttI
        ))
    }

    // MARK: 2.15.0 걸을 수 있는 길로 이끌기(이사장님 승인 2026-10-06, 아이폰 길눈 2.47.0과 같음)
    //   리눅스 서버의 걷기 길찾기(lvd-gil, 나스 /jeom/gilchatgi.php)로 걸을 수 있는 길을 받아 「다음 꺾는 곳」을 겨눔.
    //   열 걸음쯤 앞에서 미리, 닿으면 지금 꺾으라고. 크게 벗어나면 다시 찾고, 못 받으면 예전처럼 곧은 방향으로.
    private class TtAn(val sign: Int, val lat: Double, val lon: Double)
    private var ttPts: List<Pair<Double, Double>> = emptyList()
    private var ttAn: List<TtAn> = emptyList()
    private var ttI = 1
    private var ttMok: Pair<Double, Double>? = null
    private var ttBatneun = false
    private var ttMotTtae = 0L
    private var ttBeoseo = 0
    private var ttYego = -1
    private var ttMalTtae = 0L

    private fun ttJiugi() { ttPts = emptyList(); ttAn = emptyList(); ttI = 1; ttMok = null; ttBeoseo = 0; ttYego = -1; ttGugan = emptyList(); ttGuganMi.clear(); ttGuganAp.clear() }

    private fun dolgiMal(sign: Int, jigeum: Boolean): String {
        val k = if (jigeum) "꺾으십시오" else "꺾습니다"
        val g = if (jigeum) "가십시오" else "갑니다"
        return when (sign) {
            -3, -2 -> "9시 방향으로 $k"
            2, 3 -> "3시 방향으로 $k"
            -1 -> "11시 방향으로 비스듬히 $g"
            1 -> "1시 방향으로 비스듬히 $g"
            -7 -> "갈림길에서 9시 방향 길로 $g"
            7 -> "갈림길에서 3시 방향 길로 $g"
            -98, 98 -> "뒤로 돌아 $g"
            6, -6 -> "둥근 길을 따라 $g"
            4 -> if (jigeum) "목적지 가까이입니다" else "목적지에 닿습니다"
            else -> "곧장 $g"
        }
    }

    private fun dolgiSigye(sign: Int): Int = when (sign) { -3, -2, -7 -> 9; 2, 3, 7 -> 3; -1 -> 11; 1 -> 1; -98, 98 -> 6; else -> 12 }

    /** 지금 자리에서 걷는 길까지 몇 미터 떨어졌는지 */
    private fun ttGeori(la: Double, lo: Double): Double {
        if (ttPts.size < 2) return 0.0
        val kx = 111320 * Math.cos(Math.toRadians(la)); val ky = 110540.0
        var m = Double.MAX_VALUE
        for (i in 0 until ttPts.size - 1) {
            val ax = (ttPts[i].second - lo) * kx; val ay = (ttPts[i].first - la) * ky
            val bx = (ttPts[i + 1].second - lo) * kx; val by = (ttPts[i + 1].first - la) * ky
            val dx = bx - ax; val dy = by - ay; val l2 = dx * dx + dy * dy
            var t = if (l2 > 0) -(ax * dx + ay * dy) / l2 else 0.0
            t = max(0.0, min(1.0, t))
            val px = ax + t * dx; val py = ay + t * dy
            m = min(m, Math.sqrt(px * px + py * py))
        }
        return m
    }

    private fun ttDaeumMal(w: Jari): String {
        if (ttI >= ttAn.size) return ""
        val a = ttAn[ttI]
        val d = Wichi.geori(w.lat, w.lon, a.lat, a.lon)
        val s = sigye(w, a.lat, a.lon)
        return "${gm(d)} 앞에서 ${dolgiMal(a.sign, false)}." + (if (s == 0) "" else " 그쪽은 ${s}시 방향입니다.")
    }

    private fun ttBatgi(w: Jari, lat: Double, lon: Double, apMal: String) {
        if (ttBatneun || System.currentTimeMillis() - ttMotTtae < 20000) return
        ttBatneun = true
        val f = { v: Double -> String.format(java.util.Locale.US, "%.6f", v) }
        Tongsin.json("gilchatgi.php", mapOf("slat" to f(w.lat), "slon" to f(w.lon), "mlat" to f(lat), "mlon" to f(lon)), 10000) { o ->
            ttBatneun = false
            val pts = o?.optJSONArray("pts"); val an = o?.optJSONArray("an")
            if (o == null || !o.optBoolean("ok", false) || pts == null || an == null || pts.length() < 2) { ttMotTtae = System.currentTimeMillis(); return@json }
            val p = ArrayList<Pair<Double, Double>>()
            for (i in 0 until pts.length()) { val x = pts.optJSONArray(i) ?: continue; if (x.length() >= 2) p.add(Pair(x.optDouble(0), x.optDouble(1))) }
            val l = ArrayList<TtAn>()
            for (i in 0 until an.length()) { val x = an.optJSONObject(i) ?: continue; val j = x.optJSONArray("jeom") ?: continue; if (j.length() >= 2) l.add(TtAn(x.optInt("sign", 0), j.optDouble(0), j.optDouble(1))) }
            if (l.size < 2) { ttJiugi(); ttMotTtae = System.currentTimeMillis(); return@json }
            // 2.20.0 길 종류 구간(건널목·계단·다리·지하도)
            val gg = ArrayList<TtGugan>()
            val ga = o.optJSONArray("gugan")
            if (ga != null) for (i in 0 until ga.length()) { val x = ga.optJSONObject(i) ?: continue; val j = x.optJSONArray("jeom") ?: continue; if (j.length() >= 2) gg.add(TtGugan(x.optString("jong", ""), j.optDouble(0), j.optDouble(1), x.optDouble("geori", 0.0))) }
            ttGugan = gg; ttGuganMi.clear(); ttGuganAp.clear()
            ttPts = p; ttAn = l; ttI = 1; ttMok = Pair(lat, lon); ttYego = -1; ttBeoseo = 0
            ttMalTtae = System.currentTimeMillis()
            val jeon = o.optDouble("geori", 0.0)
            val w2 = Wichi.jigeum ?: return@json
            malHagi(apMal + "걸을 수 있는 길로 안내합니다. 길 따라 ${gm(jeon)}. " + ttDaeumMal(w2))
            Girok.namgi("gil_ttara", mapOf("m" to jeon.toInt(), "an" to l.size))
        }
    }

    /** 걷는 길을 따라 이끌기 — 맡았으면 참(받기 전·못 받았으면 거짓, 예전 곧은 방향 안내로) */
    private fun ttaraAnnae(w: Jari, lat: Double, lon: Double): Boolean {
        val m = ttMok
        if (m != null && (abs(m.first - lat) > 0.000001 || abs(m.second - lon) > 0.000001)) ttJiugi()
        if (ttAn.isEmpty()) { ttBatgi(w, lat, lon, ""); return false }
        val now = System.currentTimeMillis()
        if (ttGeori(w.lat, w.lon) > max(25.0, w.ochae * 1.5)) ttBeoseo += 1 else ttBeoseo = 0
        if (ttBeoseo >= 3) {
            ttJiugi(); ttMotTtae = 0L
            ttBatgi(w, lat, lon, "길에서 벗어나신 것 같아 지금 자리에서 다시 길을 찾았습니다. ")
            return true
        }
        if (ttI >= ttAn.size) return false
        // 2.20.0 건널목·계단 미리 알림(꺾는 곳보다 먼저)
        val gm0 = guganAllim(w)
        if (gm0 != null) { ttMalTtae = now; malHagi(gm0); return true }
        val a = ttAn[ttI]
        val d = Wichi.geori(w.lat, w.lon, a.lat, a.lon)
        if (d <= max(8.0, min(w.ochae, 15.0)) && ttI < ttAn.size - 1) {
            ttI += 1; ttYego = -1; ttMalTtae = now
            Jindong.banghyang(dolgiSigye(a.sign))
            malHagi("지금 ${dolgiMal(a.sign, true)}. 그다음은 " + ttDaeumMal(w))
            return true
        }
        if (d <= 15 && ttYego != ttI && a.sign != 0 && a.sign != 4) {
            ttYego = ttI; ttMalTtae = now
            malHagi("열 걸음쯤 앞에서 ${dolgiMal(a.sign, false)}.")
            return true
        }
        if (now - ttMalTtae >= 20000 && now - majimakMal >= 6000) {
            ttMalTtae = now
            val s = sigye(w, a.lat, a.lon)
            if (s == 12 || s == 11 || s == 1) {
                if (AnnaeSeoljeong.hwaksinEum) Eum.naegi(EumJong.HWAKSIN)
            } else if (s != 0) {
                malHagi("길은 ${s}시 방향입니다. 다음 꺾는 곳까지 ${gm(d)}.")
                Jindong.banghyang(s)
            }
        }
        return true
    }

    private fun georeumAnnae(w: Jari, mok: String, lat: Double, lon: Double, jungan: Boolean, y: Yeojeong) {
        jariGirok(w, lat, lon)   // 2.20.0 걷는 자리 기록(관리자 폰만)
        val d = Wichi.geori(w.lat, w.lon, lat, lon)
        val beom = if (jungan) max(15.0, min(w.ochae, 30.0)) else if (bg != null) max(5.0, min(w.ochae, 10.0)) else max(12.0, min(w.ochae, 25.0))   // 2.16.0 볼거리면 더 가까이
        if (d <= beom) {
            if (jungan) {
                Eum.naegi(EumJong.DOCHAK)
                dasiSijak()
                if (y.beoseu != null) jeongryujangDochak() else JihacheolEngine.ipguDochak()
            } else {
                dochak(w, d, y)
            }
            return
        }
        // 2.16.0 마지막 스무 미터는 걸음 수와 시 방향으로 좁혀 말함
        if (!jungan && magakAnnae(w, d, lat, lon, mok)) return
        // 2.15.0 걸을 수 있는 길을 받았으면 그 길로 이끎(다음 꺾는 곳을 겨눔)
        if (ttaraAnnae(w, lat, lon)) { neagoriBoda(w, true); return }
        val s = sigye(w, lat, lon)
        val now = System.currentTimeMillis()
        val jinan = (now - majimakMal) / 1000.0
        neagoriBoda(w, true)
        val mg = majimakGeoriMal
        if (mg == null) {
            malHagi("${mok}까지 ${gm(d)}${Annae.sigyeMal(s)}.")
            Jindong.banghyang(s)
            majimakGeoriMal = d
            majimakSigye = s
            return
        }
        if (!gotMal && d <= 40) {
            gotMal = true
            malHagi("곧 도착합니다. ${mok}까지 ${gm(d)}${Annae.sigyeMal(s)}.")
            majimakGeoriMal = d
            majimakSigye = s
            return
        }
        val st = AnnaeSeoljeong
        // 얼마나 자세히 — 자세히 그대로, 보통 1.5배, 짧게 2배 간격으로 / 되풀이 사이 시간(기본 6초)
        val bae = if (st.malSang >= 2) 1.0 else (if (st.malSang == 1) 1.5 else 2.0)
        val gan = (if (d > 300) 100.0 else (if (d > 100) 50.0 else 20.0)) * bae
        val doepul = st.doepul.toDouble()
        if (mg - d >= gan && jinan >= doepul + 4) {
            malHagi("${mok}까지 ${gm(d)}${Annae.sigyeMal(s)}.")
            majimakGeoriMal = d
            majimakSigye = s
            return
        }
        if (d - mg >= 30 && jinan >= doepul + 4) {
            malHagi("목적지에서 멀어지고 있습니다. $mok 쪽은${if (s == 0) "" else " ${s}시 방향"}, ${gm(d)}.", MalGeup.ANNAE)
            Jindong.banghyang(s)
            majimakGeoriMal = d
            majimakSigye = s
            return
        }
        if (s != 0 && majimakSigye != 0 && Annae.sigyeCha(s, majimakSigye) >= 2 && jinan >= doepul + 2) {
            malHagi("$mok 쪽은 ${s}시 방향입니다.")
            Jindong.banghyang(s)
            majimakSigye = s
            return
        }
        // 걷는 중 입 다물지 않기 — 제대로 가면 25초마다 확신음, 틀어졌으면 방향
        if (jinan >= 25 && now - hwaksinTtae >= 25000) {
            hwaksinTtae = now
            if (s == 12 || s == 11 || s == 1) {
                if (st.hwaksinEum) Eum.naegi(EumJong.HWAKSIN)
            } else if (s != 0) {
                malHagi("$mok 쪽은 ${s}시 방향입니다.")
                Jindong.banghyang(s)
                majimakSigye = s
            }
        }
    }

    private fun neagoriBoda(w: Jari, malHam: Boolean) {
        val j = neagoriJari
        val badeulTtae = j == null || Wichi.geori(j.first, j.second, w.lat, w.lon) > 400
        if (badeulTtae && !neagoriBatneunJung) {
            neagoriBatneunJung = true
            neagoriJari = w.lat to w.lon
            Chatgi.neagori(w.lat, w.lon) { r ->
                neagoriBatneunJung = false
                if (r != null) neagori = r
            }
        }
        if (!malHam || w.ochae > 20 || (w.georeumChu && w.ochae > 15)) return
        // 꺾이는 곳 알리기(설정에서 끔), 몇 초 앞에서(걸음 초속 1.3미터로 셈, 기본 8초 → 10미터쯤)
        if (!AnnaeSeoljeong.kkeokOn) return
        val ap = max(6.0, AnnaeSeoljeong.kkeokCho * 1.3)
        val now = System.currentTimeMillis()
        for (n in neagori) {
            if (n.mal.isEmpty() || Wichi.geori(w.lat, w.lon, n.lat, n.lon) > ap) continue
            val k = Chatgi.f5(n.lat) + "," + Chatgi.f5(n.lon)
            val t = malHanNeagori[k]
            if (t == null || now - t > 300000) {
                malHanNeagori[k] = now
                malHagi("${n.mal}입니다.", MalGeup.JEONGBO)
                break
            }
        }
    }

    /** 버스 정류장에 닿음 — 오는 버스를 알려 드리고, 버스가 움직이면 저절로 버스 안 안내로 */
    private fun jeongryujangDochak() {
        val b = yj.jigeum?.beoseu ?: return
        yj.beoseuNoki(b.copy(dochak = true))
        val jr = b.jeongryujang
        malHagi("${jr.ireum} 정류장입니다. 버스에 타시면 저절로 버스 안 안내로 바뀝니다.")
        Girok.namgi("beoseu_jeongryujang")
        Beoseu.douchak(jr) { m -> malHagi(m, MalGeup.JEONGBO) }
    }

    /** 지하철에서 내린 뒤 — 위성이 다시 잡히고 스무 걸음 넘게 걸으셨으면 밖으로 나오신 것 */
    private fun naonGeotBoda(w: Jari) {
        val georeum = Wichi.georeumSu
        val gijun = naonGijun
        if (gijun == null) { naonGijun = georeum; return }
        if (!w.georeumChu && w.ochae <= 30 && georeum - gijun >= 20) {
            naeryeotda(mal = "밖으로 나오신 것 같습니다. 남은 길을 걸어서 안내합니다.")
        }
    }

    private fun dochak(w: Jari, d: Double, y: Yeojeong) {
        yj.danggyeBakkugi(Danggye.DOCHAK)
        Eum.naegi(EumJong.DOCHAK)
        Jindong.dochak()
        val s = sigye(w, y.mokjeok.lat, y.mokjeok.lon)
        val b = bg
        if (b != null) malHagi("도착했습니다. ${b.ireum} 앞입니다." + (if (b.mal.isEmpty()) "" else " ${b.mal}"))   // 2.16.0 볼거리 — 이름과 만져지는 것
        else malHagi("도착했습니다. ${y.mokjeok.ireum}입니다${if (s == 0) "" else ". ${s}시 방향 가까이에 있습니다"}.")
        // 걸어서 닿으면 카메라로 문 찾기(카메라 묶음이 dochakHook 을 채움)
        dochakHook?.let { f -> main.postDelayed({ f("dochak") }, 4000) }
        Girok.namgi("dochak", mapOf("m" to d.toInt(), "ochae" to w.ochae.toInt()))
    }

    // MARK: 차 안

    private fun chaAnnae(w: Jari, d: Double, y: Yeojeong) {
        GanpanAllim.chaAn(w)   // 차 안 간판 알림
        val mok = y.mokjeok.ireum
        val now = System.currentTimeMillis()
        val dan = listOf(5000, 3000, 2000, 1000, 500, 300, 150)
        if (majimakGeoriMal == null) {
            for (g in dan) if (g.toDouble() >= d) chaGeori.add(g)
            malHagi("${mok}까지 ${Annae.geoMal(d)} 남았습니다.")
            majimakGeoriMal = d
        } else {
            val saero = dan.filter { it.toDouble() >= d && !chaGeori.contains(it) }
            val g = saero.minOrNull()
            if (g != null) {
                chaGeori.addAll(saero)
                val beoseu = yj.talgeot == Talgeot.BEOSEU
                when {
                    beoseu && g == 500 -> malHagi("${mok}까지 ${Annae.geoMal(d)} 남았습니다. 버스 안내 방송을 잘 들으시고 내리실 준비를 하십시오.")
                    beoseu && g == 300 -> malHagi("곧 $mok 부근입니다. 다음 정류장에서 내리시면 됩니다. 남은 거리 ${Annae.geoMal(d)}.")
                    g == 300 -> malHagi("곧 $mok 부근입니다. 내리실 준비를 하십시오. 남은 거리 ${Annae.geoMal(d)}.")
                    g == 150 -> malHagi("$mok 부근입니다. 차에서 내려 걸으시면 저절로 걷는 안내로 이어 드립니다.")
                    else -> malHagi("${mok}까지 ${Annae.geoMal(d)} 남았습니다.")
                }
            }
        }
        // 지나는 길과 동네
        if (w.sokdo > 3 && !w.georeumChu && now - gilMuleun >= 20000 && !gilMutneunJung) {
            gilMuleun = now
            gilMutneunJung = true
            Chatgi.gil(w.lat, w.lon) { r ->
                gilMutneunJung = false
                if (r != null) gilBoda(r.first, r.second)
            }
        }
        // 3분 넘게 말이 없으면 남은 거리 한 번
        if (now - majimakMal >= 180000) {
            malHagi("${mok}까지 ${Annae.geoMal(d)} 남았습니다.", MalGeup.JEONGBO)
        }
        // 내림 알아채기 — 목적지 800미터 안에서 40초 넘게 멈추고, 그사이 열다섯 걸음 넘게 걸으셨으면
        if (!w.georeumChu && w.sokdo < 2 && d < 800) {
            if (neurinSijak == 0L) {
                neurinSijak = now
                neurinGeoreum = Wichi.georeumSu
            }
            if (now - neurinSijak >= 40000 && Wichi.georeumSu - neurinGeoreum >= 15) {
                naeryeotda(jadong = true)
            }
        } else if (w.sokdo >= 2) {
            neurinSijak = 0L   // 다시 달리면(초속 2미터 넘게) 내림 셈을 처음부터
        }
    }

    private fun gilBoda(gil: String, dong: String) {
        // 지나는 곳 안내(설정에서 끔), 말하는 간격(기본 60초), 동네는 자세히에서만
        val st = AnnaeSeoljeong
        if (!st.gilOn || System.currentTimeMillis() - gilMalTtae < st.gilGap * 1000L) return
        val jeonGil = majimakGil
        val jeonDong = majimakDong
        if (gil.isNotEmpty() && gil != majimakGil) {
            if (majimakGil.isEmpty()) malHagi("지금 달리는 길은 ${gil}입니다.", MalGeup.JEONGBO)
            else malHagi("이제 ${gil}에 들어섰습니다.", MalGeup.JEONGBO)
            majimakGil = gil
        }
        if (dong.isNotEmpty() && dong != majimakDong) {
            if (majimakDong.isNotEmpty() && st.malSang >= 2) malHagi("${dong}에 들어왔습니다.", MalGeup.JEONGBO)
            majimakDong = dong
        }
        if (majimakGil != jeonGil || majimakDong != jeonDong) gilMalTtae = System.currentTimeMillis()
    }
}
