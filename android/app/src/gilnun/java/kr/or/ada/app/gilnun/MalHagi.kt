// 안드로이드 길눈 — 말로 하기(2.4.0, 빌드 261002-A7, 대표님 지시: 아이폰 길눈의 말로 하기를 안드로이드에도)
// 아이폰 MalHagi.swift(2.5.0~2.31.0)·MalDeutgi.swift·MalSajeon.swift 의 바탕을 같은 차례, 같은 말로 옮겼습니다.
//   부르기: 길 찾기 탭 맨 위의 말로 하기 단추. 길눈 화면이 켜져 있으면 이어폰 재생 단추를 길게(0.6초 넘게) 눌러도 엶
//     (걸으실 때 한 손에 지팡이 — 손을 덜 쓰게. 짧게 누르면 음악 앱에 그대로 돌려줌)
//   알아듣기: 안드로이드 자체 받아쓰기(SpeechRecognizer, ko-KR) + 웹·아이폰과 같은 나스 알아듣기 사전(jeom/malsajeon.json)
//   마이크가 열려 있는 동안은 길눈 말을 맡아 두어 마이크로 들어가지 않게(Sori.deutneunJung). 경고는 듣기를 그만두게 하고 곧바로
//   말소리를 켜 두셨으면 또렷한 「네」 뒤에 듣고, 끄셨으면 딩동 뒤에. 대답이 1초 넘게 걸리면 「잠깐만 기다려 주세요」
//   못 들으면 「다시 한번 말씀해 주세요」 한 번만, 그래도 못 들으면 조용히 물러남. 묻는 말에는 마이크를 한 번만 저절로 엶
//   명령 먼저: 묻는 중이라도 네·아니오가 아니면 새 명령을 따름. 모르는 말은 사과하고 기록해 두었다가 사전을 키움
//   가실 곳: 가까운 점지도의 출발지·도착지·이름에서 찾고, 없으면 나스(jeom.php a=jangso)에서 그곳을 찾아
//     끝이 그곳 80미터 안에 닿는 점지도를 찾음(아이폰 Jeomjido.matneunGil 과 같은 잣대). 찾으면 「○○까지 점지도를 따라 걸을까요?」
// 아이폰과 다른 점:
//   아이폰 2.29.0 말뜻 풀이는 폰 안의 애플 인공지능이라 안드로이드에 없음 — 사전으로 못 알아들은 말은 나스의 곳 찾기(a=jangso)로 풀어 봄
//   TODO(아이폰 하이 길눈·시리) 부르는 말로 깨우기, 화면이 꺼진 채 이어폰 단추로 열기(MediaSession 은 음악 앱의 단추를 빼앗아 이번 판에 넣지 않음)
//   TODO(아이폰 목적지 위성 안내·차·지하철·버스·콜·긴급통화·즐겨찾기·음악·방송·카메라 눈) 안드로이드에 기능이 옮겨 오면 말로도 되게
package kr.or.ada.app.gilnun

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class MalSangtae { SWIM, DEUTNEUN, ARABONEUN }

object MalHagi {
    private val main = Handler(Looper.getMainLooper())
    private var ctx: Context? = null
    private var sijakham = false

    /** 지금 길눈 화면(허락 여쭙기·화면 열기에 씀) */
    var hwalseong: WeakReference<GilnunActivity>? = null
    /** 상태가 바뀌면 화면의 단추 글자를 바꿈(화면이 채움) — 화면을 다시 그리지 않아 톡백 커서가 흔들리지 않음 */
    var byeonhwa: (() -> Unit)? = null

    var sangtae = MalSangtae.SWIM
        private set
    var deureunMal = ""
        private set
    var dapMal = ""
        private set

    /** 새 명령마다 하나씩 — 늦게 온 대답은 버림 */
    private var sedae = 0
    private var jadongYeolim = 0
    private var dasiHanbeon = false
    private var ijeonMal = ""
    private var motBeon = 0
    private var motTtae = 0L

    /** 점지도를 따라 걸을지 여쭌 길들(아이폰 hubo) */
    private class Hubo(val mok: JeomMok, val dw: Boolean)
    private var hubo: List<Hubo> = emptyList()
    private var huboI = 0
    private var mutneunJung = false
    private var mureumTtae = 0L

    private fun bakkum(s: MalSangtae) {
        sangtae = s
        byeonhwa?.invoke()
    }

    /** 단추 글자 — 아이폰 MalHagiDanchu 와 같음 */
    val danchuGeul: String
        get() = when (sangtae) {
            MalSangtae.SWIM -> "말로 하기 — 누르고 말씀하십시오"
            MalSangtae.DEUTNEUN -> "듣고 있습니다 — 누르면 그만"
            MalSangtae.ARABONEUN -> "알아보는 중입니다"
        }

    // MARK: 세우기

    fun sijak(c: Context) {
        ctx = c.applicationContext
        if (sijakham) return
        sijakham = true
        MalSajeon.bureogi()
        Sori.gyeonggoHook = { gyeonggoOm() }
    }

    // MARK: 부르기

    /** 말로 하기 단추·이어폰 단추 길게 */
    fun dudeurim() {
        main.post {
            if (ctx == null) return@post
            if (sangtae == MalSangtae.DEUTNEUN) {
                chwiso()
                Eum.naegi(EumJong.TTAENG)
                return@post
            }
            jadongYeolim = 0
            dasiHanbeon = false
            ijeonMal = Sori.majimak
            yeolgi(true)
        }
    }

    /** 화면이 가려지면 마이크를 닫음(안드로이드는 뒤에서 마이크를 못 씀) */
    fun meomchum() {
        if (sangtae == MalSangtae.DEUTNEUN) chwiso()
    }

    private fun yeolgi(sori: Boolean) {
        val c = ctx ?: return
        if (!MalDeutgi.heorakItda(c)) {
            val a = hwalseong?.get()
            if (a == null || a.isFinishing) {
                malHam("말로 하기에는 마이크 허락이 필요합니다. 폰 설정의 앱, 길눈, 권한에서 마이크를 허용해 주십시오.")
                return
            }
            a.maikHeorak { ok ->
                if (ok) yeolgi(sori)
                else malHam("말로 하기에는 마이크 허락이 필요합니다. 폰 설정의 앱, 길눈, 권한에서 마이크를 허용해 주십시오.")
            }
            return
        }
        if (!MalDeutgi.sseulSuItda(c)) {
            Girok.namgi("myeong_yeolgi", mapOf("ok" to false, "why" to "eopseum"))
            malHam("이 폰에서는 받아쓰기를 쓸 수 없습니다. 구글 음성 인식이 켜져 있는지 확인해 주십시오.")
            return
        }
        Sori.meomchugi()   // 명령 먼저 — 하던 말을 멈춤
        sedae += 1
        val sd = sedae
        bakkum(MalSangtae.DEUTNEUN)
        val yeol: () -> Unit = { if (sd == sedae && sangtae == MalSangtae.DEUTNEUN) maikYeolgi(sd) }
        if (sori && Seoljeong.malKyeojim && jadongYeolim == 0) {
            malHuHagi("네", yeol)   // 아이폰 2.30.0 딩동 대신 또렷한 「네」 — 다 말한 뒤 곧바로 엶
        } else if (sori) {
            Eum.naegi(EumJong.DINGDONG)   // 이제 말씀하십시오(말소리를 끈 분, 되물은 뒤)
            main.postDelayed({ yeol() }, 550)
        } else {
            main.postDelayed({ yeol() }, 150)
        }
    }

    private fun maikYeolgi(sd: Int) {
        val c = ctx ?: return
        Sori.deutgiSijak()
        val gidarim = if (jadongYeolim > 0) 10 else 6   // 되물은 뒤에는 10초 기다림
        val ok = MalDeutgi.deutgi(c, gidarim) { alts, why -> deureum(sd, alts, why) }
        Girok.namgi("myeong_yeolgi", mapOf("ok" to ok))
        if (!ok) {
            Sori.deutgiKkeut()
            bakkum(MalSangtae.SWIM)
            malHam("지금은 마이크를 열지 못했습니다. 잠시 뒤 다시 해 주십시오.")
        }
    }

    private fun chwiso() {
        MalDeutgi.meomchugi()
        Sori.deutgiKkeut()
        sedae += 1
        jadongYeolim = 0
        bakkum(MalSangtae.SWIM)
    }

    /** 듣는 중 경고가 옴 — 경고부터(아이폰과 같음) */
    private fun gyeonggoOm() {
        if (sangtae != MalSangtae.DEUTNEUN) return
        Girok.namgi("myeong_gyeonggo")
        chwiso()
    }

    /** 명령을 다 들었음 */
    private fun deureum(sd: Int, alts0: List<String>, why: String?) {
        Sori.deutgiKkeut()
        Girok.namgi("myeong_deureum", mapOf("su" to alts0.size, "why" to why))
        if (sd != sedae || sangtae != MalSangtae.DEUTNEUN) return
        val alts = alts0.map { it.trim() }.filter { it.isNotEmpty() }
        if (why != null) {
            bakkum(MalSangtae.SWIM)
            jadongYeolim = 0
            malHam(when (why) {
                "heorak" -> "말로 하기에는 마이크 허락이 필요합니다. 폰 설정의 앱, 길눈, 권한에서 마이크를 허용해 주십시오."
                "tongsin" -> "받아쓰기 서비스에 연결하지 못했습니다. 통신을 확인하시고 다시 해 주십시오."
                else -> "지금은 마이크를 열지 못했습니다. 잠시 뒤 다시 해 주십시오."
            })
            return
        }
        if (alts.isEmpty()) {
            Girok.namgi("maik_makhim", mapOf("dan" to "myeongryeong", "dasi" to dasiHanbeon))
            bakkum(MalSangtae.SWIM)
            if (jadongYeolim > 0) {
                // 저절로 연 마이크에 말씀이 없으면 조용히 닫음
                jadongYeolim = 0
                if (!Seoljeong.malKyeojim) Eum.naegi(EumJong.TTAENG)
                return
            }
            if (!dasiHanbeon) {
                // 아이폰 2.30.0 한 번만 「다시 한번 말씀해 주세요」 하고 한 번 더 들음
                dasiHanbeon = true
                if (Seoljeong.malKyeojim) {
                    bakkum(MalSangtae.DEUTNEUN)
                    malHuHagi("다시 한번 말씀해 주세요") { if (sd == sedae && sangtae == MalSangtae.DEUTNEUN) maikYeolgi(sd) }
                } else {
                    yeolgi(true)   // 말소리를 끄셨으면 딩동으로 다시 말씀하실 차례를 알림
                }
                return
            }
            // 그래도 못 들으면 조용히 물러남
            dasiHanbeon = false
            if (!Seoljeong.malKyeojim) Eum.naegi(EumJong.TTAENG)
            Girok.namgi("dap_kkeut", mapOf("mureoNam" to true))
            return
        }
        dasiHanbeon = false
        deureunMal = alts[0]
        bakkum(MalSangtae.ARABONEUN)
        if (Seoljeong.malKyeojim) {
            // 아이폰 2.31.0 결과가 1초 안에 나오면 곧바로, 1초 넘게 걸릴 때만 「잠깐만 기다려 주세요」
            var malSijak = false
            var malKkeut = false
            var dap: Pair<String, Boolean>? = null
            main.postDelayed({
                if (sd == sedae && dap == null) {
                    malSijak = true
                    malHuHagi("잠깐만 기다려 주세요") {
                        malKkeut = true
                        dap?.let { dapHagi(sd, it.first, it.second) }
                    }
                }
            }, 1000)
            cheori(alts) { t, mutneun ->
                if (sd == sedae && dap == null) {
                    dap = t to mutneun
                    if (!malSijak || malKkeut) dapHagi(sd, t, mutneun)
                }
            }
            return
        }
        // 말소리를 끄신 분 — 다 들으면 땡, 그 뒤에 톡백으로 결과
        Eum.naegi(EumJong.TTAENG)
        var han = false
        cheori(alts) { t, mutneun ->
            if (!han) {
                han = true
                main.postDelayed({ dapHagi(sd, t, mutneun) }, 350)
            }
        }
    }

    /** 대답하고, 묻는 말이면 마이크를 한 번만 저절로 엶 */
    private fun dapHagi(sd: Int, t: String, mutneun: Boolean) {
        if (sd != sedae) return
        bakkum(MalSangtae.SWIM)
        if (t.isNotEmpty()) { dapMal = t; byeonhwa?.invoke() }
        if (t.isEmpty()) { jadongYeolim = 0; Girok.namgi("dap_kkeut"); return }
        if (mutneun && jadongYeolim < 1) {
            malHuHagi(t) {
                if (sd == sedae && sangtae == MalSangtae.SWIM) {
                    jadongYeolim += 1
                    yeolgi(true)
                }
            }
        } else {
            jadongYeolim = 0
            Sori.mal(t)
            Girok.namgi("dap_kkeut")
        }
    }

    /** 길눈이 말하기(말소리를 끄셨으면 Sori 가 톡백으로) */
    private fun malHam(t: String) { Sori.mal(t) }

    /** 말하고 다 말한 뒤 f — 말이 끝났다는 신호가 안 와도 말 길이만큼만 기다림(아이폰 2.31.0 daehwaMal) */
    private fun malHuHagi(t: String, f: () -> Unit) {
        var han = false
        val g: () -> Unit = { if (!han) { han = true; f() } }
        Sori.mal(t, MalGeup.ANNAE, g)
        val bae = listOf(1.6, 1.3, 1.0, 0.85, 0.75)[Seoljeong.bbareugiDan.coerceIn(0, 4)]
        main.postDelayed({ g() }, (500 + t.length * 170 * bae + 900).toLong())
    }

    // MARK: 알아듣고 하기

    /** 알아들은 말들로 할 일을 하고, 대답(dap)을 꼭 한 번 부름 — (할 말, 묻는 말인가) */
    fun cheori(alts0: List<String>, dap: (String, Boolean) -> Unit) {
        val alts = alts0.map { it.trim() }.filter { it.isNotEmpty() }
        val t = alts.firstOrNull()
        if (t == null) { dap("말씀이 들리지 않았습니다.", false); return }
        val s = MalSajeon
        val z = MalSajeon.ttuk(t)
        val jm = JeomEngine
        val now = System.currentTimeMillis()
        if (now - mureumTtae > 60000) mutneunJung = false   // 여쭌 지 1분이 지나면 잊음(아니라고 듣고 걷기 시작하지 않게)
        Girok.namgi("malhagi", mapOf("gil" to z.length))

        // 0. 하던 일 멈추기 — 따라 걷기·신호기 찾기·묻던 말을 모두 멈춤
        if (s.itda(alts, "hadeon_meomchum") && z.length <= 10) {
            mureumChoGihwa()
            Sori.meomchugi()
            if (SinhogiEngine.chatneunJung) SinhogiEngine.chatgiKkeugi()
            if (jm.gil != null || jm.bulleoneun) { dap("", false); jm.geuman() }
            else dap("하던 일을 멈췄습니다.", false)
            return
        }
        // 1. 여정 끝·도착 — "도착", "다 왔어"를 가실 곳 이름으로 찾지 않고 따라 걷기를 마침(아이폰 2.12.7)
        val dochakMal = setOf("도착", "도착했어", "도착했다", "도착했어요", "도착했습니다", "도착이야", "도착했네", "다왔어", "다왔다", "다왔어요", "다왔습니다", "다왔네", "도착완료", "여기도착")
        val dochak = dochakMal.contains(z) || (z.startsWith("도착") && z.length <= 6 && !z.contains("까지") && !z.contains("시간"))
        if (s.itda(alts, "yeojeong_kkeut") || dochak) {
            mureumChoGihwa()
            if (jm.gil != null || jm.bulleoneun) {
                dap("", false)
                jm.geuman()
            } else {
                dap(if (dochak) "지금 가시는 길이 없습니다. 가실 곳을 말씀하시려면 어디로 가자라고 해 주십시오." else "지금 따라 걷는 길이 없습니다.", false)
            }
            return
        }
        // 2. 점지도를 따라 걸을지 여쭌 말의 대답
        if (mutneunJung && huboI < hubo.size) {
            val ye = s.tteut(alts, "ye") != null
            val ani = s.tteut(alts, "ani") != null
            if (ye && !ani && z.length <= 10) {
                val h = hubo[huboI]
                mureumChoGihwa()
                dap("", false)
                geotgiSijak(h)
                return
            }
            if ((ani && z.length <= 10) || z.contains("다른") || z in setOf("다음", "다음거", "다음것", "다음길")) {
                daeumHubo(dap)
                return
            }
        }
        // 3. 점지도를 따라 걷는 중의 명령("그만 걷기"는 아래 "그만"보다 먼저)
        if (jm.gil != null) {
            if (z.contains("그만걷") || z.contains("따라걷기그만") || z.contains("따라걷기끝") || z.contains("걷기그만")) {
                dap("", false)
                jm.geuman()
                return
            }
            if (z.contains("다음에무엇") || z.contains("다음에뭐") || z.contains("다음은뭐") || z.contains("다음표시") || z.contains("앞에뭐") ||
                z.contains("갈림길") || z.contains("다음꺾") || z.contains("다음에꺾") || z.contains("꺾는곳")) {
                dap(jm.daeumMuotMal(), false)
                return
            }
            if (jm.dochakHam && z.contains("되돌아")) {
                dap("", false)
                jm.doedoragagi()
                return
            }
            if (s.itda(alts, "eodi") || z.contains("어디쯤") || s.itda(alts, "sigan") || s.itda(alts, "jigeum_gil") || s.itda(alts, "charye")) {
                dap("", false)
                jm.jigeumEodiDeutgi()
                return
            }
        }
        if ((z.contains("점지도") && (z.contains("가까운") || z.contains("근처") || z.contains("목록") || z.contains("찾아"))) ||
            z.contains("가까운길") || z.contains("근처길")) {
            dap("가까운 점지도를 엽니다. 골라서 누르시면 따라 걷습니다.", false)
            hwalseong?.get()?.cheotHwamyeonEuro(JeomMokrokHwamyeon())
            return
        }
        // 4. 그만 — 어디서나(따라 걷기는 "그만 걷기"로)
        if (s.itda(alts, "geuman") && z.length <= 8) {
            mureumChoGihwa()
            Sori.meomchugi()
            if (SinhogiEngine.chatneunJung) SinhogiEngine.chatgiKkeugi()
            dap("", false)
            return
        }
        // 5. 긴급통화 — 가장 급한 일. 안드로이드에는 아직 없으므로 모르는 척하지 않고 길을 알려 드림
        if (s.itda(alts, "doum") || z.contains("화상통화") || z.contains("영상통화") || z.contains("긴급통화")) {
            Girok.namgi("malhagi_eopneun", mapOf("k" to "gingeup"))
            dap("죄송합니다. 긴급통화서비스는 아직 안드로이드 길눈에 넣지 못했습니다. 급하시면 112나 119에 전화해 주십시오. 이 말씀은 기록해 두었습니다.", false)
            return
        }

        if (meonjeoMyeongryeong(z, dap)) return

        // 6. 가실 곳 말을 떼어 보고, 가자는 말이 없으면 명령부터 살핌
        val (q0, gagiMal, roTtem) = s.mokjeokMal(t)
        var q = talgeotTteokgi(q0)
        val (q2, g2, _) = s.mokjeokMal(q)
        if (g2 && MalSajeon.ttuk(q2).length >= 2) q = q2
        if ((!gagiMal || MalSajeon.ttuk(q).length <= 2) && myeongryeong(alts, z, dap)) return

        // 7. 가실 곳 — 이름 끝의 로(종로·을지로)는 되살려 한 번 더 찾아봄
        var qB: String? = if (roTtem) q + "로" else null
        for (p in listOf("으로", "까지", "에게", "한테", "로", "에")) {
            if (q.endsWith(p) && q.length > p.length + 1) {
                if (p == "로") qB = q
                q = q.dropLast(p.length).trim()
                break
            }
        }
        if (q.isEmpty()) {
            dap("어디로 가실까요?", true)
            return
        }
        mokjeokChatgi(t, q, qB, gagiMal, dap)
    }

    /** 가실 곳보다 먼저 볼 명령 — "신호기 찾아 줘"가 가실 곳(신호기)으로 새지 않게. 하면 참 */
    private fun meonjeoMyeongryeong(z: String, dap: (String, Boolean) -> Unit): Boolean {
        // 음향신호기 — "신호기 울려 줘", "신호 알려 줘", "신호기 어디", "신호기 찾아 줘"(아이폰 2.6.0)
        if (z.contains("신호기") || z.contains("신호알려") || z.contains("신호안내") || z.contains("신호등")) {
            val a = hwalseong?.get()
            if (z.contains("찾")) {
                dap("음향신호기 찾기를 켭니다. 가까워질수록 소리가 빨라집니다. 그만이라고 하시면 멈춥니다.", false)
                val il = { SinhogiEngine.chatgiKyeogi() }
                if (a != null) a.sinhogiHagi(il) else il()
            } else {
                val cmd = if (z.contains("어디") || z.contains("위치")) 1 else 2
                dap("", false)
                val il = { SinhogiEngine.ulligi(cmd) }
                if (a != null) a.sinhogiHagi(il) else il()
            }
            return true
        }
        // 날씨 — "날씨 어때", "오늘 날씨"(아이폰 2.13.0, 웹 길눈과 같은 자료)
        if (z.contains("날씨") || z.contains("미세먼지")) {
            Nalssi.mal { n -> dap(if (n.isEmpty()) "날씨를 받아 오지 못했습니다. 통신과 위치를 확인해 주십시오." else "날씨는 $n.", false) }
            return true
        }
        return false
    }

    /** 명령 — 하면 참 */
    private fun myeongryeong(alts: List<String>, z: String, dap: (String, Boolean) -> Unit): Boolean {
        val s = MalSajeon
        if (s.itda(alts, "doumal")) {
            dap(DOUMAL_MAL, false)
            return true
        }
        if (s.itda(alts, "dasi") && z.length <= 10) {
            dap(if (ijeonMal.isEmpty()) "다시 들려 드릴 말이 없습니다." else ijeonMal, false)
            return true
        }
        if (s.itda(alts, "cheoeum") && z.length <= 8) {
            mureumChoGihwa()
            dap("처음부터 하겠습니다. 어디로 가실까요?", true)
            return true
        }
        if (s.itda(alts, "sigan") || s.itda(alts, "jigeum_gil") || s.itda(alts, "charye")) {
            dap("지금 가시는 길이 없습니다. 어디로 가실까요?", true)
            return true
        }
        if (s.itda(alts, "eodi")) {
            dap("", false)
            GilChatgiCheot.jariMal()
            return true
        }
        if (s.itda(alts, "sigan_now")) {
            dap("지금은 " + SimpleDateFormat("M월 d일 EEEE a h시 m분", Locale.KOREAN).format(Date()) + "입니다.", false)
            return true
        }
        if (s.itda(alts, "bareuge")) {
            val d = min(Seoljeong.bbareugiIreum.size - 1, Seoljeong.bbareugiDan + 1)
            Seoljeong.bbareugiDan = d
            dap("${Seoljeong.bbareugiIreum[d]} 말씀드립니다.", false)
            return true
        }
        if (s.itda(alts, "neurige")) {
            val d = max(0, Seoljeong.bbareugiDan - 1)
            Seoljeong.bbareugiDan = d
            dap("${Seoljeong.bbareugiIreum[d]} 말씀드립니다.", false)
            return true
        }
        if (s.itda(alts, "annae_kkeum")) {
            Seoljeong.malKyeojim = false
            dap("길눈 말소리를 껐습니다. 경고는 그대로 말씀드리고, 말로 하기의 대답은 톡백으로 드립니다.", false)
            return true
        }
        if (s.itda(alts, "annae_kyeom")) {
            Seoljeong.malKyeojim = true
            dap("길눈 말소리를 켰습니다.", false)
            return true
        }
        if (s.itda(alts, "saerogochim")) {
            MalSajeon.bureogi()
            SinhogiEngine.saerogochim()
            Wichi.wiseongDolligi()
            dap("새로고침을 마쳤습니다. 나스에서 새 자료를 받습니다.", false)
            return true
        }
        // 아직 안드로이드에 넣지 못한 기능 — 모르는 척하지 않고, 기록해 두었다가 그 기능을 넣을 때 말로도 되게(아이폰 aJik 과 같음)
        val aJik: List<Pair<String, Boolean>> = listOf(
            "음악·방송" to (listOf("음악", "노래", "라디오", "뉴스", "티비", "티브이", "방송", "틀어", "다음곡", "무슨곡").any { z.contains(it) } || z.lowercase().contains("tv")),
            "교통편 부르기" to (s.itda(alts, "kol_bokji") || s.itda(alts, "kol_jangaein") || s.itda(alts, "kol_nabi") || s.itda(alts, "kol_beonho") || z.contains("콜택시")),
            "차 안 안내" to (s.itda(alts, "tatda") || s.itda(alts, "naerim")),
            "즐겨찾기" to z.contains("즐겨찾기"),
            "카메라 눈" to listOf("문찾", "문어디", "글자읽", "글읽어", "글씨읽", "바코드", "무슨색", "색깔", "지폐", "얼마짜리", "사람있", "사람감지", "이게뭐", "이거뭐", "뭐가보여", "불켜", "빛알", "밝은쪽", "가리키", "안면", "얼굴인식").any { z.contains(it) },
            "되짚어 나가기" to (z.contains("되짚") || z.contains("길기억") || z.contains("길을기억")),
            "음성유도기와 승강기 찾기" to (z.contains("유도기") || z.contains("승강기")),
            "호칭과 목소리 바꾸기" to (s.itda(alts, "hoching") || s.itda(alts, "mok_bakkum"))
        )
        for ((nm, mat) in aJik) {
            if (!mat) continue
            Girok.namgi("malhagi_eopneun", mapOf("k" to nm))
            dap("죄송합니다. $nm${eun(nm)} 아직 안드로이드 길눈에 넣지 못했습니다. 그 기능을 넣을 때 말로도 되게 하겠습니다. 이 말씀은 기록해 두었습니다.", false)
            return true
        }
        // 네·아니오만
        if (s.tteut(alts, "ye") == TteutGyeol.GATDA && z.length <= 4) {
            dap("네. 어디로 가실까요?", true)
            return true
        }
        if (s.tteut(alts, "ani") == TteutGyeol.GATDA && z.length <= 4) {
            dap("알겠습니다.", false)
            return true
        }
        return false
    }

    // MARK: 가실 곳 — 점지도 찾기

    private fun mokjeokChatgi(t: String, q: String, qB: String?, gagiMal: Boolean, dap: (String, Boolean) -> Unit) {
        val w = Wichi.jigeum
        if (w == null) {
            dap("아직 위치를 잡는 중이라 가까운 점지도를 찾지 못했습니다. 잠시 뒤 다시 말씀해 주십시오.", false)
            return
        }
        Jeomjido.gakkaun(w.lat, w.lon) { r ->
            if (r == null) {
                dap("찾는 중에 연결이 끊겼습니다. 통신을 확인하시고 다시 말씀해 주십시오.", false)
                return@gakkaun
            }
            // 이름으로 — 되살린 이름(qB)이 맞으면 그것을 먼저
            val ireumMat = (if (qB != null) ireumMatchugi(r, qB, w) else emptyList()).ifEmpty { ireumMatchugi(r, q, w) }
            if (ireumMat.isNotEmpty()) {
                hubo = ireumMat
                huboI = 0
                Girok.namgi("malhagi_jeom", mapOf("dan" to "ireum", "su" to ireumMat.size))
                huboMutgi(dap, "네, ")
                return@gakkaun
            }
            // 나스에서 그곳을 찾아 끝이 그곳 가까이 닿는 점지도(아이폰 Chatgi.jangso + Jeomjido.matneunGil)
            val jq = mapOf("a" to "jangso", "q" to q,
                "lat" to String.format(Locale.US, "%.6f", w.lat), "lon" to String.format(Locale.US, "%.6f", w.lon))
            Tongsin.json("jeom.php", jq) { o ->
                if (o == null) {
                    dap("찾는 중에 연결이 끊겼습니다. 통신을 확인하시고 다시 말씀해 주십시오.", false)
                    return@json
                }
                val rows = o.optJSONArray("rows")
                var jg: Triple<String, Double, Double>? = null
                if (rows != null) for (i in 0 until rows.length()) {
                    val rr: JSONObject = rows.optJSONObject(i) ?: continue
                    val la = Jeomjido.su(rr, "lat") ?: continue
                    val lo = Jeomjido.su(rr, "lon") ?: continue
                    jg = Triple(Jeomjido.gul(rr, "ireum").ifEmpty { q }, la, lo)
                    break
                }
                val j = jg
                if (j == null) {
                    motChatgiDap(t, q, gagiMal, dap)
                    return@json
                }
                val w2 = Wichi.jigeum ?: w
                val d = Wichi.geori(w2.lat, w2.lon, j.second, j.third)
                val jariMal = if (d < 30) "네, ${j.first}${eun(j.first)} 지금 계신 곳 바로 가까이에 있습니다. "
                    else "네, ${j.first}${eun(j.first)} ${bangMal(w2, j.second, j.third)}${Jeomjido.geoMal(d)}에 있습니다. "
                val jm = jariMatchugi(r, j.second, j.third, w2)
                Girok.namgi("malhagi_jeom", mapOf("dan" to "jangso", "su" to jm.size))
                if (jm.isNotEmpty()) {
                    hubo = jm
                    huboI = 0
                    huboMutgi(dap, jariMal)
                } else {
                    dap(jariMal + "가까운 점지도 가운데 그곳으로 가는 길은 아직 없습니다.", false)
                }
            }
        }
    }

    /** 점지도의 도착지·이름이 맞으면 따라 걷기, 출발지가 맞으면 거꾸로 걷기 — 맞는 정도와 가까운 차례로 셋까지 */
    private fun ireumMatchugi(r: List<JeomMok>, q: String, w: Jari): List<Hubo> {
        val qz = MalSajeon.ttuk(q)
        if (qz.length < 2) return emptyList()
        fun jeomsu(nm: String): Int {
            val nz = MalSajeon.ttuk(nm)
            if (nz.isEmpty()) return 0
            if (nz == qz) return 3
            if (nz.length >= 2 && (nz.contains(qz) || qz.contains(nz))) return 2
            return 0
        }
        val l = ArrayList<Triple<Hubo, Int, Double>>()
        for (m in r) {
            val eo = if (m.near >= 0) m.near else Wichi.geori(w.lat, w.lon, m.slat, m.slon)
            if (eo > 1000) continue
            val ap = max(jeomsu(m.to), if (MalSajeon.ttuk(m.title).contains(qz)) 2 else 0)
            val dw = jeomsu(m.from)
            if (ap >= 2 && ap >= dw) l.add(Triple(Hubo(m, false), ap, eo))
            else if (dw >= 2) l.add(Triple(Hubo(m, true), dw, eo))
        }
        return l.sortedWith(compareBy<Triple<Hubo, Int, Double>>({ -it.second }, { it.third })).take(3).map { it.first }
    }

    /** 끝이 그곳 80미터 안에 닿고 시작이 여기서 50미터 안인 점지도(아이폰 Jeomjido.matneunGil 과 같은 잣대) */
    private fun jariMatchugi(r: List<JeomMok>, la: Double, lo: Double, w: Jari): List<Hubo> {
        val l = ArrayList<Pair<Hubo, Double>>()
        for (m in r) {
            val eo = if (m.near >= 0) m.near else Wichi.geori(w.lat, w.lon, m.slat, m.slon)
            if (eo > 50) continue
            val ap = Wichi.geori(la, lo, m.elat, m.elon)
            val dw = Wichi.geori(la, lo, m.slat, m.slon)
            if (ap <= 80 && ap <= dw) l.add(Hubo(m, false) to ap)
            else if (dw <= 80) l.add(Hubo(m, true) to dw)
        }
        return l.sortedBy { it.second }.take(3).map { it.first }
    }

    /** "○○까지 점지도를 따라 걸을까요? 네 또는 아니오로 말씀해 주십시오." */
    private fun huboMutgi(dap: (String, Boolean) -> Unit, apMal: String) {
        val h = hubo.getOrNull(huboI) ?: return
        val m = h.mok
        val kkaji = (if (h.dw) m.from else m.to).ifEmpty { m.ireum }
        var mal = apMal + "$kkaji${kkajiTo(kkaji)} " + (if (h.dw) "되돌아가는 " else "") + "점지도를 따라 걸을까요?"
        val w = Wichi.jigeum
        if (w != null) {
            val sla = if (h.dw) m.elat else m.slat
            val slo = if (h.dw) m.elon else m.slon
            val d = if (sla != 0.0) Wichi.geori(w.lat, w.lon, sla, slo) else -1.0
            if (d >= 30) mal += " 시작점은 ${bangMal(w, sla, slo)}${Jeomjido.geoMal(d)}에 있습니다."
        }
        mal += " 네 또는 아니오로 말씀해 주십시오."
        if (huboI + 1 < hubo.size) mal += " 아니오라고 하시면 다음 길을 말씀드립니다."
        mutneunJung = true
        mureumTtae = System.currentTimeMillis()
        dap(mal, true)
    }

    private fun daeumHubo(dap: (String, Boolean) -> Unit) {
        huboI += 1
        if (huboI < hubo.size) {
            huboMutgi(dap, "다음은 ")
        } else {
            mureumChoGihwa()
            dap("알겠습니다.", false)
        }
    }

    /** 고른 점지도를 따라 걷기 시작하고 걷는 화면을 엶(JeomGilGoreugi 와 같음) */
    private fun geotgiSijak(h: Hubo) {
        val c = ctx ?: return
        Girok.namgi("malhagi_jeom", mapOf("dan" to "sijak", "dwit" to h.dw))
        JeomEngine.bulleoGeotgi(c, h.mok.id, h.dw)
        hwalseong?.get()?.cheotHwamyeonEuro(JeomGeotgiHwamyeon())
    }

    private fun mureumChoGihwa() {
        mutneunJung = false
        hubo = emptyList()
        huboI = 0
    }

    /** 찾는 곳을 못 찾았을 때의 대답 */
    private fun motChatgiDap(t: String, q: String, gagiMal: Boolean, dap: (String, Boolean) -> Unit) {
        Girok.namgi("mal_motaradeureum", mapOf("mal" to t.take(60)))   // 모르는 말은 기록해 두었다가 사전을 키움(아이폰과 같음)
        if (gagiMal) {
            dap("죄송합니다. $q${eul(q)} 찾지 못했습니다. 다른 이름으로 말씀해 주십시오.", true)
        } else {
            val now = System.currentTimeMillis()
            motBeon = if (now - motTtae < 120000) motBeon + 1 else 1
            motTtae = now
            dap(if (motBeon < 2) "죄송합니다. 제가 잘 알아듣지 못했습니다. 다시 말씀해 주십시오."
                else "죄송합니다. 이 말씀은 아직 배우지 못했습니다. 기록해 두었으니 다음 업그레이드에 넣겠습니다. 다시 말씀해 주십시오.", true)
        }
    }

    /** 몇 시 방향 — 방향을 모르면 빈 말 */
    private fun bangMal(w: Jari, la: Double, lo: Double): String {
        val b = Wichi.hapBang
        if (b < 0) return ""
        return "${Jeomjido.sigyeBanghyang(b, Jeomjido.bangwi(w.lat, w.lon, la, lo))}시 방향, "
    }

    // MARK: 탈것 말(아이폰 talgeotTteokgi) — "약수역 걸어서 가자" → "약수역 가자"

    private val TALGEOT_MAL = setOf("걸어서", "걸어", "도보로", "도보", "차로", "차", "택시로", "택시", "콜택시로",
        "지하철로", "지하철", "전철로", "전철", "버스로", "버스", "타고", "차타고", "대중교통으로")

    private fun talgeotTteokgi(q: String): String =
        q.split(" ").filter { it.isNotEmpty() && !TALGEOT_MAL.contains(MalSajeon.ttuk(it)) }.joinToString(" ").trim()

    // MARK: 토씨

    private fun batchim(w: String): Pair<Boolean, Boolean> {
        val ch = w.lastOrNull() ?: return false to false
        val c = ch.code - 0xAC00
        if (c < 0 || c >= 11172) return false to false
        val j = c % 28
        return (j != 0) to (j == 8)
    }
    fun eul(w: String) = if (batchim(w).first) "을" else "를"
    fun eun(w: String) = if (batchim(w).first) "은" else "는"
    /** 까지 앞 — 토씨 없이 */
    private fun kkajiTo(w: String) = if (w.endsWith("까지")) "" else "까지"

    /** 말로 하는 도움말 — 안드로이드 길눈에서 되는 말만 */
    const val DOUMAL_MAL = "이렇게 말씀하시면 됩니다. 지금 어디야. 약수역 가자. 가까운 점지도 찾아 줘. 점지도를 따라 걸을 때는 다음에 무엇, 다음 갈림길, 어디쯤이야, 그만 걷기, 도착하면 되돌아가자. 신호기 울려 줘. 신호 알려 줘. 신호기 찾아 줘. 날씨 어때. 몇 시야. 말 빠르게, 말 느리게. 말소리 꺼. 다시 말해. 그만. 하던 일 멈춰. 새로고침."
}

// MARK: 받아쓰기 — 안드로이드 자체 SpeechRecognizer(아이폰 MalDeutgi)

object MalDeutgi {
    private val main = Handler(Looper.getMainLooper())
    private var sr: SpeechRecognizer? = null
    private var beon = 0
    private var jigeumBeon = 0
    private var jikimi: Runnable? = null

    val dolgoItda: Boolean get() = jigeumBeon != 0

    fun heorakItda(c: Context): Boolean =
        ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun sseulSuItda(c: Context): Boolean = try { SpeechRecognizer.isRecognitionAvailable(c) } catch (e: Exception) { false }

    /** 명령 하나 듣기 — 다 들으면 kkeut(알아들은 말들, 잘못 까닭). 말이 없으면 빈 목록. 열지 못하면 거짓. 화면 줄(main)에서 부를 것 */
    fun deutgi(c: Context, gidarimCho: Int, kkeut: (List<String>, String?) -> Unit): Boolean {
        meomchugi()
        val r = try { SpeechRecognizer.createSpeechRecognizer(c.applicationContext) } catch (e: Exception) { null } ?: return false
        sr = r
        beon += 1
        val b = beon
        jigeumBeon = b
        var han = false
        var malSijak = false
        fun machim(alts: List<String>, why: String?) {
            if (han || b != jigeumBeon) return
            han = true
            jigeumBeon = 0
            jikimi?.let { main.removeCallbacks(it) }
            jikimi = null
            val rr = sr
            sr = null
            main.post { try { rr?.destroy() } catch (e: Exception) {} }
            kkeut(alts, why)
        }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() { malSijak = true }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onResults(results: Bundle?) {
                val alts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.filter { it.isNotBlank() }?.take(5) ?: emptyList()
                machim(alts, null)
            }
            override fun onError(error: Int) {
                val why: String? = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_CLIENT -> null
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "heorak"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> "tongsin"
                    SpeechRecognizer.ERROR_AUDIO -> "maik"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "bappeum"
                    else -> "e$error"
                }
                Girok.namgi("maldeutgi_oryu", mapOf("e" to error))
                machim(emptyList(), why)
            }
        })
        val sik = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, c.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
        }
        try {
            r.startListening(sik)
        } catch (e: Exception) {
            jigeumBeon = 0
            sr = null
            try { r.destroy() } catch (e2: Exception) {}
            return false
        }
        // 지킴이 — 말씀이 시작되지 않으면 기다린 시간 뒤에, 시작되었으면 12초 뒤에 닫음(받아쓰기가 끝을 못 잡아 멈추지 않게)
        val jk = object : Runnable {
            var dan = 0
            override fun run() {
                if (b != jigeumBeon) return
                if (dan == 0 && malSijak) {
                    dan = 1
                    main.postDelayed(this, 12000)
                    return
                }
                if (dan <= 1 && malSijak) {
                    dan = 2
                    try { sr?.stopListening() } catch (e: Exception) {}
                    main.postDelayed(this, 4000)
                    return
                }
                try { sr?.cancel() } catch (e: Exception) {}
                machim(emptyList(), null)
            }
        }
        jikimi = jk
        main.postDelayed(jk, gidarimCho * 1000L + 1500)
        return true
    }

    /** 듣기를 그만둠 — 대답(kkeut)은 부르지 않음 */
    fun meomchugi() {
        jigeumBeon = 0
        jikimi?.let { main.removeCallbacks(it) }
        jikimi = null
        val r = sr ?: return
        sr = null
        try { r.cancel() } catch (e: Exception) {}
        try { r.destroy() } catch (e: Exception) {}
    }
}

// MARK: 알아듣기 사전 — 웹·아이폰 길눈과 같은 나스 사전(jeom/malsajeon.json)(아이폰 MalSajeon)

enum class TteutGyeol { GATDA, BITSEUT }

object MalSajeon {
    // MARK: 앱 안 기본 말투(나스 사전을 한 번도 못 받았을 때) — 아이폰과 같음

    private val GIBON_PPAEGI = listOf("오늘은", "지금", "길눈아", "길눈", "헤이", "하이", "좀", "우리", "저기", "음", "어")

    private val GIBON_KKEUNMAL = listOf("으로 가자", "로 가자", "에 가자", "까지 가자", "으로 가 줘", "로 가 줘", "가자", "가 줘", "갈래",
        "가고 싶어", "데려다 줘", "안내해 줘", "가는 길 알려 줘", "가는 길", "찾아 줘", "까지", "으로", "로", "에")

    private val GIBON_TTEUT: Map<String, List<String>> = mapOf(
        "ye" to listOf("네", "예", "응", "그래", "좋아", "맞아", "맞습니다", "그렇게 해", "부탁해", "오케이", "해 줘"),
        "ani" to listOf("아니", "아니요", "아니오", "아뇨", "아냐", "싫어", "됐어", "괜찮아", "말고", "다른 곳", "다른 데", "틀렸어"),
        "geotgi" to listOf("걸어", "걷자", "걸어가자", "걸어서", "도보"),
        "cha" to listOf("차로", "차 타고", "택시", "콜택시", "타고 가자", "차로 가자"),
        "daejung" to listOf("지하철", "전철", "버스", "대중교통", "기차", "열차"),
        "kol_bokji" to listOf("복지콜", "복지 콜"),
        "kol_jangaein" to listOf("장애인콜", "장애인 콜택시", "장애인택시", "교통약자"),
        "kol_nabi" to listOf("나비콜", "나비 콜", "바우처택시", "바우처 택시"),
        "tatda" to listOf("탔어", "탔습니다", "차에 탔어", "승차", "타고 있어"),
        "naerim" to listOf("내렸어", "내렸습니다", "하차", "차에서 내렸"),
        "jigeum_gil" to listOf("지금 가는 길", "경로 알려", "남은 길", "어떻게 가야"),
        "sigan" to listOf("얼마나 걸려", "얼마나 걸리", "얼마나 남았", "언제 도착", "몇 분"),
        "eodi" to listOf("여기가 어디", "지금 어디", "내 위치", "어디쯤"),
        "cheoeum" to listOf("처음으로", "처음부터", "다시 시작"),
        "dasi" to listOf("다시 말해", "뭐라고", "한 번 더", "못 들었어"),
        "geuman" to listOf("그만", "취소", "멈춰", "조용히 해"),
        "doum" to listOf("도와줘", "도와주세요", "도움 요청", "긴급통화", "사람 불러", "해설사 요청", "호출해 줘", "살려 줘"),
        "doumal" to listOf("도움말", "무슨 말 할 수", "할 수 있는 말"),
        "kol_beonho" to listOf("전화번호", "번호 알려", "연락처"),
        "sigan_now" to listOf("몇 시", "지금 시간", "오늘 며칠", "무슨 요일", "날짜"),
        "charye" to listOf("할 차례", "이제 뭐 해", "다음 할 일"),
        "hadeon_meomchum" to listOf("하던 일 멈춰", "하던 일 멈추", "하던 거 멈춰", "하던 것 멈춰", "다 멈춰", "모두 멈춰", "전부 멈춰", "하던 일 그만"),
        "yeojeong_kkeut" to listOf("여정 끝", "안내 끝", "길 안내 그만", "목적지 취소", "여정 취소"),
        "bareuge" to listOf("빠르게", "빨리 말해"),
        "neurige" to listOf("느리게", "천천히 말해"),
        "annae_kkeum" to listOf("말소리 꺼", "안내 음성 꺼"),
        "annae_kyeom" to listOf("말소리 켜", "안내 음성 켜"),
        "mok_bakkum" to listOf("목소리 바꿔", "다른 목소리"),
        "hoching" to listOf("호칭 바꿔", "부르는 이름", "나를 뭐라고"),
        "saerogochim" to listOf("새로고침", "새로 고침"),
        "jeulgyeo_dam" to listOf("즐겨찾기에 넣어", "즐겨찾기 담아", "담아 줘", "저장해 줘"),
        "jeulgyeo_mok" to listOf("즐겨찾기 목록", "즐겨찾기 알려", "담아 둔 곳")
    )

    // 기본 말투가 먼저 서야 함(오브젝트는 적힌 차례로 채워짐)
    var mokrok: Map<String, List<String>> = GIBON_TTEUT
        private set
    var kkeunmal: List<String> = GIBON_KKEUNMAL
        private set
    var ppaegi: List<String> = GIBON_PPAEGI
        private set
    var pan = "앱 안 기본"
        private set

    /** 나스에서 사전을 받음 — 못 받으면 받아 둔 것이나 앱 안 기본 말투로 */
    fun bureogi() {
        Tongsin.json("malsajeon.json", emptyMap()) { o -> if (o != null) batda(o) }
    }

    private fun batda(o: JSONObject) {
        o.optJSONObject("tteut")?.let { t ->
            val m = HashMap(GIBON_TTEUT)
            val ks = t.keys()
            while (ks.hasNext()) {
                val k = ks.next()
                val a = t.optJSONArray(k) ?: continue
                val l = (0 until a.length()).mapNotNull { a.optString(it, "").takeIf { s -> s.isNotEmpty() } }
                if (l.isNotEmpty()) m[k] = l
            }
            mokrok = m
        }
        o.optJSONArray("kkeunmal")?.let { a ->
            val l = (0 until a.length()).mapNotNull { a.optString(it, "").takeIf { s -> s.isNotEmpty() } }
            if (l.isNotEmpty()) kkeunmal = l
        }
        o.optJSONArray("ppaegi")?.let { a ->
            val l = (0 until a.length()).mapNotNull { a.optString(it, "").takeIf { s -> s.isNotEmpty() } }
            if (l.isNotEmpty()) ppaegi = l
        }
        pan = o.optString("pan", pan)
        Girok.namgi("malsajeon", mapOf("pan" to pan))
    }

    /** 띄어쓰기와 문장부호를 뺀 말 */
    fun ttuk(t: String): String = t.filter { " \t\n.,!?~·'\"".indexOf(it) < 0 }

    private val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ".toList()
    private val JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ".toList()
    private val JONG = " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ".toList()

    /** 한글을 자모로 풀기 — 발음이 비슷한 말을 알아듣는 데 씀 */
    fun jamo(t: String): List<Char> {
        val o = ArrayList<Char>()
        for (ch in t) {
            val c = ch.code - 0xAC00
            if (c in 0 until 11172) {
                o.add(CHO[c / 588])
                o.add(JUNG[(c % 588) / 28])
                if (c % 28 != 0) o.add(JONG[c % 28])
            } else {
                o.add(ch)
            }
        }
        return o
    }

    /** 두 자모 줄 사이의 거리(몇 자를 고치면 같아지는가) */
    fun geori(a: List<Char>, b: List<Char>): Int {
        if (a.isEmpty()) return b.size
        if (b.isEmpty()) return a.size
        var ap = IntArray(b.size + 1) { it }
        for (i in 1..a.size) {
            val jg = IntArray(b.size + 1)
            jg[0] = i
            for (j in 1..b.size) {
                val bakkum = ap[j - 1] + (if (a[i - 1] == b[j - 1]) 0 else 1)
                jg[j] = minOf(ap[j] + 1, jg[j - 1] + 1, bakkum)
            }
            ap = jg
        }
        return ap[b.size]
    }

    /** 알아들은 말들(alts) 가운데 뜻 k 의 말투가 있으면 그대로(GATDA) 또는 발음이 비슷(BITSEUT) */
    fun tteut(alts: List<String>, k: String): TteutGyeol? {
        val l = mokrok[k] ?: emptyList()
        for (a in alts) {
            val tx = ttuk(a)
            if (tx.isEmpty()) continue
            for (p in l) {
                val pp = ttuk(p)
                if (pp.isNotEmpty() && tx.contains(pp)) return TteutGyeol.GATDA
            }
        }
        for (a in alts) {
            val jt = jamo(ttuk(a))
            if (jt.isEmpty()) continue
            for (p in l) {
                val jp = jamo(ttuk(p))
                if (jp.size < 6) continue
                val heo = jp.size / 6
                var s = 0
                while (s < jt.size && s + jp.size - 1 <= jt.size + 1) {
                    val e = min(jt.size, s + jp.size)
                    if (geori(jt.subList(s, e), jp) <= heo) return TteutGyeol.BITSEUT
                    s += 1
                }
            }
        }
        return null
    }

    /** 그대로 들어 있을 때만 */
    fun itda(alts: List<String>, k: String): Boolean = tteut(alts, k) == TteutGyeol.GATDA

    /** "오늘은 집으로 가자" → ("집", 가자는 말이 붙어 있었는가, 뗀 말이 "로"로 시작했는가) */
    fun mokjeokMal(t: String): Triple<String, Boolean, Boolean> {
        val pp = ppaegi.map { ttuk(it) }.toSet()
        val ws = t.split(" ").map { it.trim('.', ',', '!', '?', '~') }.filter { it.isNotEmpty() && !pp.contains(ttuk(it)) }
        var q = ws.joinToString(" ")
        var gagi = false
        var ro = false
        val kl = kkeunmal.sortedByDescending { ttuk(it).length }
        for (km in kl) {
            val kz = ttuk(km)
            val qz = ttuk(q)
            if (kz.isEmpty() || qz.length <= kz.length || !qz.endsWith(kz)) continue
            var n = kz.length
            val qq = StringBuilder(q)
            while (n > 0 && qq.isNotEmpty()) {
                if (qq[qq.length - 1] != ' ') n -= 1
                qq.deleteCharAt(qq.length - 1)
            }
            q = qq.toString().trim()
            gagi = kz.length >= 2
            ro = kz.startsWith("로")
            break
        }
        return Triple(q, gagi, ro)
    }

}

// MARK: 날씨 — 아이폰 Nalssi.swift(2.13.0)와 같은 자료(오픈메테오, 열쇠 없음), 같은 말

object Nalssi {
    private val il = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var jangdok = ""
    private var ttae = 0L
    private var ttaeJari: Pair<Double, Double>? = null

    /** 날씨 한 줄 — 받지 못하면 빈 글(결과는 화면 줄에서) */
    fun mal(kkeut: (String) -> Unit) {
        val w = Wichi.jigeum
        if (w == null) { kkeut(jangdok); return }
        val j = ttaeJari
        if (jangdok.isNotEmpty() && System.currentTimeMillis() - ttae < 600000 && j != null && Wichi.geori(j.first, j.second, w.lat, w.lon) < 3000) {
            kkeut(jangdok); return
        }
        val la = String.format(Locale.US, "%.3f", w.lat)
        val lo = String.format(Locale.US, "%.3f", w.lon)
        val a = "https://api.open-meteo.com/v1/forecast?latitude=$la&longitude=$lo&current=temperature_2m,weather_code,wind_speed_10m&wind_speed_unit=ms&timezone=Asia%2FSeoul"
        val b = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$la&longitude=$lo&current=pm10,pm2_5&timezone=Asia%2FSeoul"
        il.execute {
            val n = batgi(a)?.optJSONObject("current")
            val m = batgi(b)?.optJSONObject("current")
            val t = if (n == null) "" else joripda(su(n, "temperature_2m"), su(n, "weather_code")?.toInt(), su(n, "wind_speed_10m"), su(m, "pm10"), su(m, "pm2_5"))
            main.post {
                if (t.isNotEmpty()) {
                    jangdok = t
                    ttae = System.currentTimeMillis()
                    ttaeJari = w.lat to w.lon
                    Girok.namgi("nalssi")
                }
                kkeut(jangdok)
            }
        }
    }

    private fun su(o: JSONObject?, k: String): Double? = if (o == null) null else Jeomjido.su(o, k)

    private fun batgi(s: String): JSONObject? = try {
        val c = URL(s).openConnection() as HttpURLConnection
        c.connectTimeout = 12000
        c.readTimeout = 12000
        val r = if (c.responseCode == 200) JSONObject(c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }) else null
        c.disconnect()
        r
    } catch (e: Exception) { null }

    private fun haneulMal(c: Int?): String {
        if (c == null) return ""
        return when {
            c == 0 -> "맑음"
            c <= 2 -> "구름 조금"
            c == 3 -> "흐림"
            c == 45 || c == 48 -> "안개"
            c in 51..57 -> "이슬비"
            c in 61..65 -> "비"
            c in 66..67 -> "얼어붙는 비"
            c in 71..77 -> "눈"
            c in 80..82 -> "소나기"
            c in 85..86 -> "눈 소나기"
            c >= 95 -> "천둥 번개"
            else -> ""
        }
    }

    private fun meonjiMal(pm10: Double?, pm25: Double?): Pair<String, Boolean> {
        if (pm10 == null && pm25 == null) return "" to false
        fun deung(v: Double?, a: Double, b: Double, c: Double): Int = when {
            v == null -> -1
            v <= a -> 0
            v <= b -> 1
            v <= c -> 2
            else -> 3
        }
        val d = max(deung(pm10, 30.0, 80.0, 150.0), deung(pm25, 15.0, 35.0, 75.0))
        if (d < 0) return "" to false
        var s = "미세먼지 " + listOf("좋음", "보통", "나쁨", "매우 나쁨")[d]
        if (pm10 != null) s += ", 농도 ${pm10.roundToInt()}, 초미세먼지 " + (pm25?.roundToInt()?.toString() ?: "모름")
        if (d >= 2) s += ". 마스크를 쓰시는 것이 좋습니다"
        return s to (d >= 2)
    }

    /** 걸음에 영향을 주는 것을 앞에 둡니다 */
    private fun joripda(on: Double?, code: Int?, baram: Double?, pm10: Double?, pm25: Double?): String {
        val ap = ArrayList<String>()
        val dwi = ArrayList<String>()
        val h = haneulMal(code)
        val jeojeun = listOf("비", "눈", "소나기", "천둥", "안개").any { h.contains(it) }
        if (jeojeun) ap.add(h + "입니다. 바닥이 미끄러울 수 있습니다")
        if (baram != null && baram >= 7) ap.add("바람이 셉니다, 초속 ${baram.roundToInt()}미터")
        if (on != null && on <= 0) ap.add("영하 ${abs(on).roundToInt()}도, 얼어붙은 곳을 조심하십시오")
        val (m, nappeum) = meonjiMal(pm10, pm25)
        if (m.isNotEmpty()) { if (nappeum) ap.add(m) else dwi.add(m) }
        if (on != null) dwi.add("기온 ${on.roundToInt()}도")
        if (h.isNotEmpty() && !jeojeun) dwi.add(h)
        return (ap + dwi).joinToString(". ")
    }
}
