// 안드로이드 길눈 — 설정 탭에 더한 것(2.7.0 묶음 b6_nanum, 아이폰 길눈 SeoljeongDeo.swift·SeoljeongView.swift 2.9.0~2.28.0과 같은 차례, 같은 말)
// 말하기 설정(목소리 고르기·길눈 목소리 선희·얼마나 자세히·무엇을 말할지), 현 위치정보 말할 내용, 여기서 점검, 내 서류 보관함(콜 번호).
// 설정 값은 길눈 설정과 같은 꾸러미(gilnun)에 아이폰과 같은 이름으로 담음 — 안내 묶음(AnnaeSeoljeong)과 같은 열쇠 이름이라 따로 맞출 것이 없음.
// 내 서류 보관함은 폰 안에만(앱 백업에서 빠지는 no_backup 폴더). 보낼 때만 잠깐 캐시에 베껴 문자·메일 창으로 넘김.
package kr.or.ada.app.gilnun

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.view.View
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** 설정에 더한 값 — 아이폰 Seoljeong 과 같은 이름, 같은 처음 값 */
object SeoljeongDeo {
    private var d: SharedPreferences? = null
    private var ctx: Context? = null
    fun sijak(c: Context) {
        if (d == null) {
            ctx = c.applicationContext
            d = c.applicationContext.getSharedPreferences("gilnun", Context.MODE_PRIVATE)
        }
    }
    private fun b(k: String, m: Boolean) = d?.getBoolean(k, m) ?: m
    private fun i(k: String, m: Int) = d?.getInt(k, m) ?: m
    private fun sb(k: String, v: Boolean) { d?.edit()?.putBoolean(k, v)?.apply() }
    private fun si(k: String, v: Int) { d?.edit()?.putInt(k, v)?.apply() }

    // 현 위치정보 말할 내용(안내 묶음 AnnaeSeoljeong 과 같은 열쇠) — 처음에는 지번만 꺼 둠
    var jariJuso: Boolean get() = b("jariJuso", true); set(v) = sb("jariJuso", v)
    var jariGot: Boolean get() = b("jariGot", true); set(v) = sb("jariGot", v)
    var jariJibeon: Boolean get() = b("jariJibeon", false); set(v) = sb("jariJibeon", v)
    var jariJijeom: Boolean get() = b("jariJijeom", true); set(v) = sb("jariJijeom", v)
    var jariOcha: Boolean get() = b("jariOcha", true); set(v) = sb("jariOcha", v)

    // 말하기 설정 — 얼마나, 무엇을 말할지(안내 묶음 AnnaeSeoljeong 과 같은 열쇠, 웹 길눈과 같은 처음 값)
    var malSang: Int get() = i("malSang", 2); set(v) = si("malSang", v)
    var kkeokOn: Boolean get() = b("kkeokOn", true); set(v) = sb("kkeokOn", v)
    var kkeokCho: Int get() = i("kkeokCho", 8); set(v) = si("kkeokCho", v)
    var doepul: Int get() = i("doepul", 6); set(v) = si("doepul", v)
    var gilOn: Boolean get() = b("gilOn", true); set(v) = sb("gilOn", v)
    var gilGap: Int get() = i("gilGap", 60); set(v) = si("gilGap", v)
    var ganpanOn: Boolean get() = b("ganpanOn", true); set(v) = sb("ganpanOn", v)
    var ganpanKamera: Boolean get() = b("ganpanKamera", false); set(v) = sb("ganpanKamera", v)
    var hwaksinEum: Boolean get() = b("hwaksinEum", true); set(v) = sb("hwaksinEum", v)

    /** 길눈 목소리(폰 TTS 목소리 이름) — 빈 글이면 폰의 가장 좋은 한국어 목소리 */
    var moksoriIreum: String
        get() = d?.getString("moksoriIreum", "") ?: ""
        set(v) { d?.edit()?.putString("moksoriIreum", v)?.apply() }
    /** 길눈 목소리 — 마이크로소프트 선희(나스 sori/mal.php). 처음부터 켜짐(열쇠가 있는 폰에서만 씀) */
    var msMoksori: Boolean get() = b("msMoksori", true); set(v) = sb("msMoksori", v)

    val sangIreum = listOf("짧게", "보통", "자세히")

    // MARK: 지금 내 자리 — 설정에서 고른 것만(아이폰 AnnaeEngine.jigeumJari·jariMalMandeulgi)

    fun jariMal() {
        val w = Wichi.jigeum
        if (w == null) {
            Sori.mal(if (!Wichi.heorakItda) "위치 허락이 없어 자리를 알 수 없습니다. 폰 설정의 앱, 길눈, 권한에서 위치를 허용해 주십시오."
            else "아직 위치를 잡는 중입니다. 잡히면 다시 눌러 주십시오.")
            return
        }
        Sori.mal("지금 자리를 알아보는 중입니다.", MalGeup.JEONGBO)
        Tongsin.json("jarimal.php", mapOf("lat" to NanumNas.sosu6(w.lat), "lon" to NanumNas.sosu6(w.lon)), 10000) { o ->
            Nalssi.mal { n -> Sori.mal(jariMalMandeulgi(o, w) + (if (n.isEmpty()) "" else " 날씨는 $n.")) }
        }
    }

    fun jariMalMandeulgi(o: JSONObject?, w: Jari): String {
        val t = ArrayList<String>()
        if (o != null) {
            val juso = NanumNas.gul(o, "juso")
            if (jariJuso && juso.isNotEmpty()) t.add(juso)
            val jb = NanumNas.gul(o, "jibeon")
            if (jariJibeon && jb.isNotEmpty()) t.add("지번 $jb")
            if (jariGot) {
                val c = o.optJSONObject("chulgu")
                val g = o.optJSONObject("gakkaun")
                val cn = NanumNas.gul(c, "ireum")
                val gn = NanumNas.gul(g, "ireum")
                if (c != null && cn.isNotEmpty()) t.add("${cn}에서 ${(NanumNas.su(c, "meter") ?: 0.0).toInt()}미터")
                else if (g != null && gn.isNotEmpty()) t.add("${gn}에서 ${(NanumNas.su(g, "meter") ?: 0.0).toInt()}미터")
            }
            val m = NanumNas.gul(o, "mal")
            if (t.isEmpty() && !jariJijeom && !jariOcha && m.isNotEmpty()) t.add(m)
        } else if (!jariJijeom) {
            t.add("지금 자리 이름을 받지 못했습니다. 통신이 끊겼을 수 있습니다")
        }
        if (jariJijeom) Jijeom.mal(w.lat, w.lon)?.let { t.add(it) }
        if (jariOcha) t.add("위성 오차 약 ${max(1, w.ochae.toInt())}미터")
        if (t.isEmpty()) t.add("말할 내용이 모두 꺼져 있습니다. 설정 탭의 현 위치정보 말할 내용에서 켜 주십시오")
        return t.joinToString(". ") + "."
    }

    // MARK: 목소리 — 폰의 한국어 TTS 목소리 목록(목록만 보려고 따로 하나 엶)

    private var mokTts: TextToSpeech? = null
    private var mokJunbi = false
    private val mokDwi = ArrayList<(List<Voice>) -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    /** 한국어 목소리 — 좋은 음질부터, 폰에 받아 둔 것만 */
    fun hangukMoksori(c: Context, kkeut: (List<Voice>) -> Unit) {
        if (mokJunbi) { kkeut(mokrok()); return }
        mokDwi.add(kkeut)
        if (mokTts != null) return
        mokTts = TextToSpeech(c.applicationContext) { st ->
            main.post {
                mokJunbi = st == TextToSpeech.SUCCESS
                val l = if (mokJunbi) mokrok() else emptyList()
                val f = ArrayList(mokDwi)
                mokDwi.clear()
                f.forEach { it(l) }
                if (!mokJunbi) { mokTts?.shutdown(); mokTts = null }
            }
        }
    }

    private fun mokrok(): List<Voice> {
        val v = try { mokTts?.voices } catch (e: Exception) { null } ?: return emptyList()
        return v.filter { it.locale?.language == "ko" && it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true }
            .sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.isNetworkConnectionRequired }.thenBy { it.name })
    }

    fun moksoriMal(l: List<Voice>, v: Voice?): String {
        if (v == null) return "기본 목소리"
        val i = l.indexOfFirst { it.name == v.name }
        var t = "한국어 목소리 " + (if (i >= 0) "${i + 1}" else "")
        t = t.trim()
        if (v.quality >= Voice.QUALITY_VERY_HIGH) t += " (가장 좋은 음질)" else if (v.quality >= Voice.QUALITY_HIGH) t += " (좋은 음질)"
        if (v.isNetworkConnectionRequired) t += ", 인터넷이 있어야 함"
        return t
    }

    /** 지금 고른 목소리 — 고르지 않았거나 폰에서 사라졌으면 null(폰 기본 목소리) */
    fun goreunMoksori(l: List<Voice>): Voice? {
        val n = moksoriIreum
        return if (n.isEmpty()) null else l.firstOrNull { it.name == n }
    }

    /** 다음 목소리로(말로 하기 「목소리 바꿔」에서도 씀) — 결과 말을 돌려줌 */
    fun moksoriBakkugi(c: Context, kkeut: (String) -> Unit) {
        hangukMoksori(c) { l ->
            if (l.size < 2) {
                kkeut("이 폰에는 한국어 목소리가 하나뿐입니다. 폰 설정의 텍스트 음성 변환 출력에서 한국어 음성 데이터를 더 받으시면 바꿀 수 있습니다.")
                return@hangukMoksori
            }
            val jigeum = goreunMoksori(l)?.name ?: ""
            val i = l.indexOfFirst { it.name == jigeum }
            moksoriIreum = l[(i + 1) % l.size].name
            kkeut("이 목소리로 말씀드립니다.")
        }
    }
}

// 2.7.0 통합 — 국가지점번호는 묶음 b2 의 Jijeom(Heundeul.kt, 같은 셈·같은 말)을 씀(따로 두던 NnJijeom 은 걷어냄)

// MARK: 말하기 설정 — 목소리와 얼마나, 무엇을 말할지

class MalSeolHwamyeon : Hwamyeon("말하기 설정") {
    private var mok: List<Voice>? = null

    override fun chaeugi(t: GilnunActivity) {
        SeoljeongDeo.sijak(t)
        val s = SeoljeongDeo
        if (NasMoksori.yeollim(t)) {   // 아이폰 2.28.0 — 정식 음성 열쇠 전에는 나스 음악 열쇠가 있는 폰에서만
            kyeogi(t, "길눈 목소리 — 마이크로소프트 선희", { s.msMoksori }, { s.msMoksori = it }) { on ->
                if (on) NasMoksori.kkaeugi()
                if (on) "길눈 목소리를 선희 목소리로 바꿨습니다." else "길눈 목소리를 폰 목소리로 바꿨습니다."
            }
        }
        // 2.7.0 묶음 b3 카메라 눈 말소리(아이폰은 길눈 말소리 바로 다음)
        kyeogi(t, "카메라 눈 말소리", { KameraNun.kameraMal }, { KameraNun.kameraMal = it }) {
            if (it) "카메라 눈 말소리를 켰습니다." else "카메라 눈 말소리를 껐습니다. 카메라 눈은 소리로만 알립니다."
        }
        val mb = t.danchu(moksoriGeul()) { }
        mb.setOnClickListener {
            s.moksoriBakkugi(t) { m ->
                Sori.mal(m)
                SeoljeongDeo.hangukMoksori(t) { l -> mok = l; if (nnBoinda(t, this)) mb.text = moksoriGeul() }
            }
        }
        if (mok == null) SeoljeongDeo.hangukMoksori(t) { l -> mok = l; if (nnBoinda(t, this)) mb.text = moksoriGeul() }

        // 얼마나 자세히, 무엇을 말할지(아이폰 MalSeolDeoView)
        val sb = t.danchu("") { }
        fun sangGeul() { sb.text = "얼마나 자세히 — 지금 ${s.sangIreum[s.malSang.coerceIn(0, 2)]} (누르면 바뀝니다)" }
        sangGeul()
        sb.setOnClickListener {
            s.malSang = if (s.malSang >= 2) 1 else if (s.malSang == 1) 0 else 2
            sangGeul()
            Sori.mal("이제 ${s.sangIreum[s.malSang]}로 말씀드립니다.")
        }
        kyeogi(t, "꺾이는 곳 알리기", { s.kkeokOn }, { s.kkeokOn = it }) { if (it) "꺾이는 곳을 알려 드립니다." else "꺾이는 곳을 알리지 않습니다." }
        dolligi(t, { "몇 초 앞에서 알릴지 — 지금 ${s.kkeokCho}초 (누르면 바뀝니다)" }) {
            s.kkeokCho = if (s.kkeokCho == 5) 8 else if (s.kkeokCho == 8) 12 else 5
            "${s.kkeokCho}초 앞에서 알려 드립니다. 걸으실 때는 ${Math.round(s.kkeokCho * 1.3)}미터쯤 앞입니다."
        }
        dolligi(t, { "되풀이 사이 시간 — 지금 ${s.doepul}초 (누르면 바뀝니다)" }) {
            s.doepul = if (s.doepul == 4) 6 else if (s.doepul == 6) 10 else 4
            "한 번 말한 뒤 ${s.doepul}초 동안은 다시 말하지 않습니다."
        }
        // 2.27.0 차 안 안내 정도(웹 길눈 0.84.0과 같음) — 바른 길로 가는지 알림과 내리는 곳 안내는 간단에서도 나옴
        dolligi(t, { "차 안 안내 정도 — 지금 ${ChaMat.ireum[ChaMat.jeongdo]} (누르면 바뀝니다)" }) {
            ChaMat.jeongdo = if (ChaMat.jeongdo >= 3) 1 else ChaMat.jeongdo + 1
            when (ChaMat.jeongdo) {
                1 -> "차 안 안내를 간단히 합니다. 남은 거리, 길에서 벗어났을 때, 내리는 곳만 말씀드립니다."
                2 -> "차 안 안내를 보통으로 합니다. 마지막 꺾는 곳과 목적지에서 멀어질 때도 말씀드립니다."
                else -> "차 안 안내를 자세히 합니다. 꺾는 곳마다, 길대로 가는지, 오래 서 있을 때도 말씀드립니다."
            }
        }
        kyeogi(t, "지나는 곳 안내", { s.gilOn }, { s.gilOn = it }) { if (it) "지나는 길을 알려 드립니다." else "지나는 길을 알리지 않습니다." }
        dolligi(t, { "차 안에서 지나는 곳 말하는 간격 — 지금 ${s.gilGap}초 (누르면 바뀝니다)" }) {
            s.gilGap = if (s.gilGap == 30) 60 else if (s.gilGap == 60) 120 else 30
            "${s.gilGap}초에 한 번까지 지금 달리는 길을 말씀드립니다."
        }
        kyeogi(t, "차 안 간판 알림", { s.ganpanOn }, { s.ganpanOn = it }) {
            if (it) "달릴 때 지나는 가게와 건물을 시계 방향으로 알려 드립니다." else "지나는 가게와 건물을 알리지 않습니다."
        }
        kyeogi(t, "서 있을 때 창밖 간판 읽기(카메라)", { s.ganpanKamera }, { s.ganpanKamera = it }) {
            if (it) "차가 서 있거나 천천히 갈 때 카메라로 창밖 간판을 읽어 드립니다. 폰 뒤쪽 카메라를 창밖으로 향해 주십시오." else "창밖 간판을 카메라로 읽지 않습니다."
        }
        // 2.27.0 말 자르고 새로 말하기(아이폰 SeoljeongDeo 와 같은 말)
        kyeogi(t, "말 자르고 새로 말하기", { Seoljeong.malJaru }, { Seoljeong.malJaru = it }) {
            if (it) "새 안내가 하던 말을 끊고 바로 나옵니다." else "하던 말을 다 마친 뒤에 새 안내가 나옵니다."
        }
        kyeogi(t, "걸을 때 확신음", { s.hwaksinEum }, { s.hwaksinEum = it }) {
            if (it) "확신음을 켭니다. 제대로 가고 계시면 짧은 맑은 소리가 납니다." else "확신음을 끕니다. 방향이 틀어졌을 때의 말은 그대로 나옵니다."
        }
        // 2.26.0 하이 길눈 부르기(처음은 꺼짐, 이사장님 승인)
        run {
            val hb = t.danchu("") { }
            fun hgeul() { hb.text = "하이 길눈 부르기 — " + (if (Seoljeong.hiGilnun) "켜져 있음 (누르면 끕니다)" else "꺼져 있음 (누르면 켭니다)") }
            hgeul()
            hb.setOnClickListener {
                if (Seoljeong.hiGilnun) {
                    Seoljeong.hiGilnun = false; HaiGilnun.datgi(); hgeul(); WichiService.kyeogi(t); t.dasiGeurigi()
                    Sori.mal("하이 길눈 부르기를 끕니다. 말로 하기 단추나 이어폰 단추 길게 누르기를 쓰십시오.")
                } else if (!HaiGilnun.sseulSuItda(t)) {
                    Sori.mal("이 폰에서는 폰 안 받아쓰기가 없어 하이 길눈 부르기를 쓸 수 없습니다. 말로 하기 단추를 써 주십시오.")
                } else {
                    t.maikHeorak { ok ->
                        if (ok) {
                            Seoljeong.hiGilnun = true; hgeul()
                            Sori.mal("하이 길눈 부르기를 켭니다. 길눈 화면이 켜져 있는 동안 하이 길눈이라고 불러 주십시오.")
                            t.dasiGeurigi()
                        } else Sori.mal("마이크 허락이 없어 켜지 못했습니다.")
                    }
                }
            }
            // 2.27.0 화면이 꺼져도 하이 길눈 듣기(하이 길눈을 켜신 때만 보임, 처음은 꺼짐)
            if (Seoljeong.hiGilnun) {
                val jb = t.danchu("") { }
                fun jgeul() { jb.text = "화면이 꺼져도 하이 길눈 듣기 — " + (if (Seoljeong.hiJamgeum) "켜져 있음 (누르면 끕니다)" else "꺼져 있음 (누르면 켭니다)") }
                jgeul()
                jb.setOnClickListener {
                    Seoljeong.hiJamgeum = !Seoljeong.hiJamgeum
                    jgeul()
                    WichiService.kyeogi(t)   // 알림 칸 길눈을 마이크 쓰임과 함께(또는 빼고) 다시 올림
                    Sori.mal(if (Seoljeong.hiJamgeum) "화면이 꺼지거나 다른 앱을 쓰실 때도 하이 길눈을 듣습니다. 알림 칸에 길눈이 떠 있는 동안입니다. 배터리를 조금 더 씁니다."
                        else "하이 길눈은 길눈 화면이 켜져 있을 때만 듣습니다.")
                }
            }
        }
        // 2.27.0 길눈이 부르는 내 호칭(아이폰 말로 하기 설정의 호칭 칸과 같음, 처음은 길손님)
        run {
            val e = t.ipryeok("길눈이 부르는 내 호칭 — 지금 ${Seoljeong.ho}", false)
            e.setText(Seoljeong.hoching)
            e.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            fun jeojang() {
                val h = e.text?.toString()?.trim() ?: ""
                Seoljeong.hoching = h
                e.hint = "길눈이 부르는 내 호칭 — 지금 ${Seoljeong.ho}"
                Sori.mal("이제 ${Seoljeong.ho}${MalHagi.irago(Seoljeong.ho)} 부르겠습니다.")
            }
            e.setOnEditorActionListener { _, id, ev ->
                val enter = id == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                    (ev != null && ev.keyCode == android.view.KeyEvent.KEYCODE_ENTER && ev.action == android.view.KeyEvent.ACTION_DOWN)
                if (enter) jeojang()
                enter
            }
            t.danchu("호칭 저장") { jeojang() }
        }
        t.danchu("지금 설정으로 들어 보기") {
            val m = when (s.malSang) {
                0 -> "사거리 백삼십 미터. 두 시 방향 왕산로."   // 2.26.0 보기 문장도 시계 방향만(우회전·좌회전 뺌)
                1 -> "백삼십 미터 앞 사거리. 곧장 다산로. 두 시 방향 왕산로, 열 시 방향 정릉천동로."
                else -> "백삼십 미터 앞이 사거리입니다. 곧장 가면 다산로입니다. 두 시 방향은 왕산로, 열 시 방향은 정릉천동로입니다."
            }
            Sori.mal("이렇게 들으십니다. $m")
        }
        t.geul("경고는 안전을 위해 말소리를 꺼도 늘 말씀드립니다. 더 좋은 목소리는 폰 설정의 접근성이나 일반 관리 안에 있는 텍스트 음성 변환 출력에서 한국어 음성 데이터를 내려받으시면 여기에 나타납니다.")
    }

    private fun moksoriGeul(): String {
        val l = mok ?: return "목소리 — 목소리 목록을 받는 중입니다"
        return "목소리 — ${SeoljeongDeo.moksoriMal(l, SeoljeongDeo.goreunMoksori(l))}. 누르면 바꿉니다"
    }

    /** 켜고 끄는 단추 — 글자는 그 자리에서만 바꿈(톡백 커서가 흔들리지 않게) */
    private fun kyeogi(t: GilnunActivity, ireum: String, ilgi: () -> Boolean, sseugi: (Boolean) -> Unit, mal: (Boolean) -> String) {
        val b = t.danchu("") { }
        fun geul() { b.text = "$ireum — " + (if (ilgi()) "켜져 있음 (누르면 끕니다)" else "꺼져 있음 (누르면 켭니다)") }
        geul()
        b.setOnClickListener {
            val on = !ilgi()
            sseugi(on)
            geul()
            Sori.mal(mal(on))
        }
    }

    private fun dolligi(t: GilnunActivity, geul: () -> String, f: () -> String) {
        val b = t.danchu(geul()) { }
        b.setOnClickListener {
            val m = f()
            b.text = geul()
            Sori.mal(m)
        }
    }
}

// MARK: 현 위치정보 말할 내용

class JariMalSeoljeongHwamyeon : Hwamyeon("현 위치정보 말할 내용") {
    override fun chaeugi(t: GilnunActivity) {
        SeoljeongDeo.sijak(t)
        val s = SeoljeongDeo
        t.geul("지금 내 자리 듣기에서 무엇을 말할지 정합니다.")
        jul(t, "도로명주소 말하기", { s.jariJuso }, { s.jariJuso = it })
        jul(t, "가까운 곳 이름 말하기", { s.jariGot }, { s.jariGot = it })
        jul(t, "지번주소 말하기", { s.jariJibeon }, { s.jariJibeon = it })
        jul(t, "국가지점번호 말하기", { s.jariJijeom }, { s.jariJijeom = it })
        jul(t, "위성 오차 말하기", { s.jariOcha }, { s.jariOcha = it })
        t.danchu("지금 설정으로 내 자리 듣기") { SeoljeongDeo.jariMal() }
        t.geul("도심에서는 위성 오차가 5미터에서 20미터쯤 됩니다. 건물 이름이 옆 건물로 나올 수 있어 오차를 함께 말씀드립니다.")
    }

    private fun jul(t: GilnunActivity, ireum: String, ilgi: () -> Boolean, sseugi: (Boolean) -> Unit) {
        val b = t.danchu("") { }
        fun geul() { b.text = "$ireum — " + (if (ilgi()) "켜짐 (누르면 끕니다)" else "꺼짐 (누르면 켭니다)") }
        geul()
        b.setOnClickListener {
            val on = !ilgi()
            sseugi(on)
            geul()
            Sori.mal(ireum + "를 " + (if (on) "켰습니다." else "껐습니다."))
        }
    }
}

// MARK: 여기서 점검

class YeogiJeomgeomHwamyeon : Hwamyeon("여기서 점검") {
    private val jul = ArrayList<String>()
    private var doneun = false
    private var kkeutna = false
    private var bonaen = ""
    private var chojeomHal = false
    private val main = Handler(Looper.getMainLooper())

    override fun chaeugi(t: GilnunActivity) {
        t.geul("아래 단추를 한 번 누르시면 지금 이 자리에서 길눈이 제대로 도는지 하나하나 살펴 읽어 드립니다.")
        val sb = t.danchu(if (doneun) "점검하는 중입니다" else "점검 시작") { }
        sb.setOnClickListener { jeomgeom(t, sb) }
        if (kkeutna) {
            val bb = t.danchu("점검 결과 클에게 보내기") { }
            val bm = t.geul(bonaen)
            bm.visibility = if (bonaen.isEmpty()) View.GONE else View.VISIBLE
            bb.setOnClickListener { bonaegi(t, bm) }
        }
        var majimak: TextView? = null
        jul.forEachIndexed { i, s -> majimak = t.geul(s, i == jul.size - 1 && kkeutna) }
        val m = majimak
        if (chojeomHal && m != null) { chojeomHal = false; t.chojeomOmgigi(m) }
    }

    private fun jeogi(t: GilnunActivity, s: String) {
        main.post {
            jul.add(s)
            if (nnBoinda(t, this) && doneun) t.geul(s)   // 다시 그리지 않고 아래에 한 줄씩 더함(커서가 흔들리지 않게)
        }
    }

    private fun seobeo(pail: String, q: List<Pair<String, String>>, ireum: String): String {
        val t0 = System.currentTimeMillis()
        val r = NanumNas.getDongi(pail, q)
        val j = r.o ?: return "$ireum 닿지 않음"
        if (j.has("ok") && !j.optBoolean("ok", true)) return "$ireum 실패, " + NanumNas.gul(j, "error").ifEmpty { "까닭 모름" }
        return "$ireum 정상, ${System.currentTimeMillis() - t0}밀리초"
    }

    private fun jeomgeom(t: GilnunActivity, sb: TextView) {
        if (doneun) return
        doneun = true
        kkeutna = false
        jul.clear()
        bonaen = ""
        t.dasiGeurigi()
        Sori.mal("점검을 시작합니다.", MalGeup.JEONGBO)
        val ac = t.applicationContext
        NanumNas.dwiSil {
            jeogi(t, "안드로이드 길눈 ${Pan.pan}판, 빌드 ${Pan.bild}")
            jeogi(t, if (Tongsin.yeongyeol) "인터넷 이어져 있음" else "인터넷이 끊겨 있습니다")
            val w = Wichi.jigeum
            if (w != null) jeogi(t, "위치 잡힘, 오차 약 ${w.ochae.toInt()}미터" + (if (w.georeumChu) ", 위성이 흐려 걸음으로 이어 셈하는 중" else ""))
            else jeogi(t, "위치를 아직 잡지 못했습니다. 하늘이 트인 곳으로 나가 보십시오")
            val la = NanumNas.sosu6(w?.lat ?: 37.5665)
            val lo = NanumNas.sosu6(w?.lon ?: 126.9780)
            jeogi(t, seobeo("jeom.php", listOf("a" to "jimyeong", "lat" to la, "lon" to lo), "지금 자리 이름"))
            jeogi(t, seobeo("jeom.php", listOf("a" to "jangso", "q" to "서울역", "lat" to la, "lon" to lo), "목적지 찾기"))
            jeogi(t, seobeo("jeom.php", listOf("a" to "find", "lat" to la, "lon" to lo), "가까운 점지도"))
            jeogi(t, seobeo("jic.php", listOf("a" to "yeok", "lat" to la, "lon" to lo), "지하철 역 찾기"))
            jeogi(t, seobeo("dulle.php", listOf("a" to "yakguk", "lat" to la, "lon" to lo), "둘레 찾기"))
            jeogi(t, seobeo("gabolgot.php", listOf("a" to "gabol", "lat" to la, "lon" to lo), "가 볼 곳"))
            val malOk = ContextCompat.checkSelfPermission(ac, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            jeogi(t, if (malOk) "말로 하기 허락 받음" else "말로 하기 허락이 없습니다. 말로 하기를 한 번 두드려 허락해 주십시오")
            jeogi(t, if (Seoljeong.malKyeojim) "길눈 말소리 켜짐" else "길눈 말소리가 꺼져 있습니다. 경고만 말합니다")
            jeogi(t, if (GongjiEngine.allimDoenda(ac)) "폰 알림 허락 받음" else "폰 알림 허락이 없습니다. 알림 화면에서 켜실 수 있습니다")
            main.post {
                val mot = jul.filter { s -> listOf("막힘", "실패", "닿지", "없습니다", "끊겨", "못").any { s.contains(it) } }
                val kkeut = if (mot.isEmpty()) "점검 끝. 모두 정상입니다." else "점검 끝. 걸린 곳이 ${mot.size}군데입니다. " + mot.joinToString(". ")
                jul.add(kkeut)
                Sori.mal(kkeut)
                Girok.namgi("yeogi_jeomgeom", mapOf("mot" to mot.size))
                doneun = false
                kkeutna = true
                chojeomHal = true
                if (nnBoinda(t, this)) t.dasiGeurigi()
            }
        }
    }

    private fun bonaegi(t: GilnunActivity, bm: TextView) {
        val b = JSONObject()
        b.put("jul", org.json.JSONArray(jul))
        b.put("gigi", "안드로이드 길눈 앱 ${Pan.pan} (${Pan.bild}) Android ${Build.VERSION.RELEASE} ${Build.MODEL}")
        b.put("jjok", "app")
        NanumNas.postJson("jeomgeom.php", listOf("a" to "put"), b) { j ->
            bonaen = if (j != null) "보냈습니다. 클이 나스에서 그대로 읽습니다." else "보내지 못했습니다. 신호를 확인해 주십시오."
            Sori.mal(bonaen)
            if (nnBoinda(t, this)) { bm.text = bonaen; bm.visibility = View.VISIBLE }
        }
    }
}

// MARK: 내 서류 보관함 — 복지카드와 신분증, 콜 등록(폰 안에만, 앱 백업에서 뺌)

internal object NnSeoryuham {
    val jongryu: List<Pair<String, String>> = listOf("bokji_ap" to "복지카드 앞면", "bokji_dwi" to "복지카드 뒷면", "sinbun" to "신분증",
        "jangae" to "장애정도 결정 통지서", "geubak" to "그 밖의 서류")
    val jeokgi: List<Pair<String, String>> = listOf("ireum" to "이름", "saengil" to "생년월일 (여덟 자리)", "yeonrak" to "연락처", "juso" to "주소")

    fun got(c: Context): File {
        val f = File(c.noBackupFilesDir, "gilnun_seoryu")
        if (!f.exists()) f.mkdirs()
        return f
    }

    fun pail(c: Context, k: String) = File(got(c), "$k.jpg")
    fun itna(c: Context, k: String) = pail(c, k).exists()
    fun jiugi(c: Context, k: String) { pail(c, k).delete() }
    fun ireum(k: String) = jongryu.firstOrNull { it.first == k }?.second ?: "서류"

    /** 사진을 1600 화소 안으로 줄이고 바로 세워 JPEG 로 담음 — 화면 줄이 아닌 곳에서 부를 것 */
    fun damgi(c: Context, k: String, uri: Uri): Boolean {
        return try {
            val cr = c.contentResolver
            val op = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, op) }
            val w = op.outWidth
            val h = op.outHeight
            if (w <= 0 || h <= 0) return false
            var s = 1
            while (max(w, h) / (s * 2) >= 1600) s *= 2
            val bm0 = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
                ?: return false
            val dol = try {
                cr.openInputStream(uri)?.use {
                    when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            } catch (e: Exception) { 0f }
            val kk = 1600f / max(bm0.width, bm0.height)
            val mx = Matrix()
            if (kk < 1f) mx.postScale(kk, kk)
            if (dol != 0f) mx.postRotate(dol)
            val bm = if (kk < 1f || dol != 0f) Bitmap.createBitmap(bm0, 0, 0, bm0.width, bm0.height, mx, true) else bm0
            val im = File(got(c), "$k.jpg.tmp")
            FileOutputStream(im).use { bm.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            val kkeut = pail(c, k)
            kkeut.delete()
            im.renameTo(kkeut)
        } catch (e: Exception) {
            Girok.namgi("seoryu_damgi_oryu", mapOf("e" to (e.message ?: "").take(80)))
            false
        }
    }

    private fun jeokgiPail(c: Context) = File(got(c), "jeokgi.json")

    fun jeokeun(c: Context, k: String): String = try {
        val f = jeokgiPail(c)
        if (f.exists()) JSONObject(f.readText()).optString(k, "") else ""
    } catch (e: Exception) { "" }

    fun jeokgiNoki(c: Context, gap: Map<String, String>) {
        val o = JSONObject()
        for ((k, v) in gap) o.put(k, v)
        try { jeokgiPail(c).writeText(o.toString()) } catch (e: Exception) {}
    }

    fun jeokeunGeul(c: Context): String =
        jeokgi.mapNotNull { x -> val v = jeokeun(c, x.first); if (v.isEmpty()) null else "${x.second.substringBefore(" (")}: $v" }.joinToString("\n")

    // 보내기 — 캐시에 베껴(받는 앱이 읽을 수 있게) 파일 제공자로 넘김. 다음에 보관함을 열 때 지움
    const val BONAEGI = "seoryu_bonaegi"
    const val JJIKGI = "seoryu_jjikgi"
    fun gwonhan(c: Context) = c.packageName + ".b6seoryu"

    fun bonaegiChiugi(c: Context) {
        File(c.cacheDir, BONAEGI).listFiles()?.forEach { it.delete() }
        File(c.cacheDir, JJIKGI).listFiles()?.forEach { it.delete() }
    }

    fun bonaelUri(c: Context, k: String): Uri {
        val d = File(c.cacheDir, BONAEGI)
        d.mkdirs()
        val f = File(d, ireum(k).replace(" ", "_") + ".jpg")
        pail(c, k).copyTo(f, overwrite = true)
        return FileProvider.getUriForFile(c, gwonhan(c), f)
    }
}

/** 내 서류 보관함 전용 파일 제공자 — 다른 묶음의 파일 제공자와 얼개(manifest)에서 부딪치지 않게 따로 이름을 둠 */
class NnSeoryuProvider : FileProvider()

/** 복지콜·교통약자 콜 번호 — 나스 call.json(2.0부터 시군 목록 포함). 지역 고르기는 2.8.0부터 KolJiyeok.kt(아이폰 KolJiyeok.swift 와 같음) */
internal object NnKol {
    class Kol(val ireum: String, val jeonhwa: String, val bigo: String)
    var jaryo: JSONObject? = null
        private set
    private var batneun = false

    fun batgi(kkeut: () -> Unit) {
        if (jaryo != null) { kkeut(); return }
        if (batneun) return
        batneun = true
        Tongsin.json("call.json", emptyMap()) { r -> batneun = false; if (r != null) { jaryo = r; kkeut() } }
    }

    fun mok(): List<Kol> = KolJiyeok.mok(jaryo)
    fun jiyeokMal(): String = KolJiyeok.mal(jaryo)
}

class SeoryuhamHwamyeon : Hwamyeon("내 서류 보관함") {
    private var goreun = NnSeoryuham.jongryu[0].first
    private var kolPyeol = false
    private var damgiPyeol = false
    private var jeokPyeol = false
    private val jeok = HashMap<String, String>()
    private var cheotBoim = true

    override fun chaeugi(t: GilnunActivity) {
        if (cheotBoim) {
            cheotBoim = false
            NnSeoryuham.bonaegiChiugi(t)
            for ((k, _) in NnSeoryuham.jeokgi) jeok[k] = NnSeoryuham.jeokeun(t, k)
        }
        t.geul("여기에 담긴 것은 이 폰 안에만 있습니다. 서버로 올라가지 않고 폰 백업에도 넣지 않습니다. 폰을 바꾸시면 다시 담으셔야 합니다.")
        nnPyeolchigi(t, "복지콜·교통약자 콜 번호 펼치기 — 지금 계신 지역", "복지콜·교통약자 콜 번호 접기", kolPyeol) { kolPyeol = !kolPyeol }
        if (kolPyeol) {
            if (NnKol.jaryo == null) NnKol.batgi { if (nnBoinda(t, this) && kolPyeol) t.dasiGeurigi() }
            t.geul(NnKol.jiyeokMal())
            for (k in NnKol.mok()) {
                t.danchu("${k.ireum} 전화 걸기" + (if (k.bigo.isEmpty()) "" else " — ${k.bigo}")) { nnJeonhwa(t, k.jeonhwa) }
            }
            t.geul("가입할 때 서류를 내라고 하면 아래 담아 둔 서류에서 보내기를 누르십시오. 문자나 메일로 보내는 창이 열리고 받는 곳은 직접 고르십니다.")
        }
        nnMeori(t, "담아 둔 서류")
        val damgin = NnSeoryuham.jongryu.filter { NnSeoryuham.itna(t, it.first) }
        if (damgin.isEmpty()) {
            t.geul("아직 담아 둔 서류가 없습니다.")
        } else {
            for ((k, nm) in damgin) {
                val b = t.danchu("$nm 보내기") { bonaegi(t, listOf(k)) }
                nnDongjak(t, b, listOf("이 서류 빼기" to {
                    nnMureum(t, "이 서류를 뺄까요?", "빼기") {
                        NnSeoryuham.jiugi(t, k)
                        Sori.mal("서류를 뺐습니다.")
                        if (nnBoinda(t, this)) t.dasiGeurigi()
                    }
                }))
            }
            if (damgin.size > 1) t.danchu("담아 둔 서류 모두 보내기") { bonaegi(t, damgin.map { it.first }) }
            t.geul("서류를 빼실 때는 서류 이름을 길게 누르시거나 톡백 동작 메뉴에서 이 서류 빼기를 고르십시오.")
        }
        nnPyeolchigi(t, "서류 담기 펼치기 — 사진 찍기, 사진에서 고르기", "서류 담기 접기", damgiPyeol) { damgiPyeol = !damgiPyeol }
        if (damgiPyeol) {
            val gb = t.danchu("") { }
            fun goreumGeul() { gb.text = "어떤 서류입니까 — 지금 ${NnSeoryuham.ireum(goreun)} (누르면 바뀝니다)" }
            goreumGeul()
            gb.setOnClickListener {
                val l = NnSeoryuham.jongryu
                val i = l.indexOfFirst { it.first == goreun }
                goreun = l[(i + 1) % l.size].first
                goreumGeul()
                Sori.mal("${NnSeoryuham.ireum(goreun)}${NnTossi.ro(NnSeoryuham.ireum(goreun))} 정했습니다.")
            }
            t.danchu("사진 찍기") { jjikgi(t) }
            t.danchu("이미 찍어 둔 사진에서 고르기") { goreugi(t) }
        }
        nnPyeolchigi(t, "적어 두기 펼치기 — 이름, 생년월일, 연락처, 주소", "적어 두기 접기", jeokPyeol) { jeokPyeol = !jeokPyeol }
        if (jeokPyeol) {
            t.geul("가입신청서에 늘 적게 되는 것들입니다. 한 번 적어 두시면 다음부터 다시 적지 않으셔도 됩니다.")
            for ((k, nm) in NnSeoryuham.jeokgi) nnKan(t, nm, jeok[k] ?: "", sutja = k == "saengil") { jeok[k] = it }
            t.danchu("적은 것 담기") {
                NnSeoryuham.jeokgiNoki(t, NnSeoryuham.jeokgi.associate { (k, _) -> k to (jeok[k] ?: "").trim() })
                Sori.mal("적은 것을 담았습니다. 이 폰 안에만 있습니다.")
                t.dasiGeurigi()
            }
            if (NnSeoryuham.jeokeunGeul(t).isNotEmpty()) {
                t.danchu("적어 둔 것 보내기") {
                    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, NnSeoryuham.jeokeunGeul(t))
                    try { t.startActivity(Intent.createChooser(i, "적어 둔 것 보내기")) } catch (e: Exception) { Sori.mal("보낼 앱을 찾지 못했습니다.") }
                }
            }
        }
    }

    private fun bonaegi(t: GilnunActivity, ki: List<String>) {
        try {
            val u = ki.map { NnSeoryuham.bonaelUri(t, it) }
            val i = if (u.size == 1) {
                Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, u[0])
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/jpeg").putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(u))
            }
            val cd = ClipData.newRawUri(NnSeoryuham.ireum(ki[0]), u[0])
            for (x in u.drop(1)) cd.addItem(ClipData.Item(x))
            i.clipData = cd
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            t.startActivity(Intent.createChooser(i, if (u.size == 1) "${NnSeoryuham.ireum(ki[0])} 보내기" else "담아 둔 서류 모두 보내기"))
        } catch (e: Exception) {
            Girok.namgi("seoryu_bonaegi_oryu", mapOf("e" to (e.message ?: "").take(80)))
            Sori.mal("보내는 창을 열지 못했습니다.")
        }
    }

    // 사진 고르기·찍기 — 화면이 언제 열리든 쓸 수 있게 결과 받는 자리를 그때그때 열고, 받으면 닫음
    private var goreugiL: ActivityResultLauncher<PickVisualMediaRequest>? = null
    private var jjikgiL: ActivityResultLauncher<Uri>? = null
    private var heorakL: ActivityResultLauncher<String>? = null
    private var jjikgiUri: Uri? = null

    private fun goreugi(t: GilnunActivity) {
        goreugiL?.unregister()
        val l = t.activityResultRegistry.register("b6_seoryu_goreugi", ActivityResultContracts.PickVisualMedia()) { uri ->
            goreugiL?.unregister()
            goreugiL = null
            if (uri != null) damgi(t, uri)
        }
        goreugiL = l
        try {
            l.launch(PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly).build())
        } catch (e: Exception) {
            l.unregister(); goreugiL = null
            Sori.mal("사진을 고르는 창을 열지 못했습니다.")
        }
    }

    private fun jjikgi(t: GilnunActivity) {
        if (!t.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) { Sori.mal("이 기기에서는 카메라를 쓸 수 없습니다."); return }
        // 카메라 허락을 적어 둔 앱은 허락 없이 사진기를 부르면 안드로이드가 막으므로 먼저 여쭘
        if (ContextCompat.checkSelfPermission(t, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            heorakL?.unregister()
            val hl = t.activityResultRegistry.register("b6_seoryu_heorak", ActivityResultContracts.RequestPermission()) { ok ->
                heorakL?.unregister()
                heorakL = null
                if (ok) jjikgiSijak(t) else Sori.mal("카메라 허락이 없어 사진을 찍지 못합니다. 이미 찍어 둔 사진에서 고르기를 쓰시거나 폰 설정의 앱, 길눈, 권한에서 카메라를 허용해 주십시오.")
            }
            heorakL = hl
            Sori.mal("사진을 찍으려면 카메라 허락이 필요합니다. 허용을 눌러 주십시오.")
            hl.launch(Manifest.permission.CAMERA)
            return
        }
        jjikgiSijak(t)
    }

    private fun jjikgiSijak(t: GilnunActivity) {
        try {
            val d = File(t.cacheDir, NnSeoryuham.JJIKGI)
            d.mkdirs()
            val f = File(d, "jjikgi_${System.currentTimeMillis()}.jpg")
            val u = FileProvider.getUriForFile(t, NnSeoryuham.gwonhan(t), f)
            jjikgiUri = u
            jjikgiL?.unregister()
            val l = t.activityResultRegistry.register("b6_seoryu_jjikgi", ActivityResultContracts.TakePicture()) { ok ->
                jjikgiL?.unregister()
                jjikgiL = null
                val uu = jjikgiUri
                jjikgiUri = null
                if (ok && uu != null) damgi(t, uu, f) else f.delete()
            }
            jjikgiL = l
            l.launch(u)
        } catch (e: Exception) {
            Girok.namgi("seoryu_jjikgi_oryu", mapOf("e" to (e.message ?: "").take(80)))
            Sori.mal("사진기를 열지 못했습니다.")
        }
    }

    private fun damgi(t: GilnunActivity, uri: Uri, imsi: File? = null) {
        val k = goreun
        val nm = NnSeoryuham.ireum(k)
        val ac = t.applicationContext
        val main = Handler(Looper.getMainLooper())
        NanumNas.dwiSil {
            val ok = NnSeoryuham.damgi(ac, k, uri)
            imsi?.delete()
            main.post {
                if (ok) {
                    Sori.mal("$nm${NnTossi.eul(nm)} 담았습니다. 이 폰 안에만 있습니다.")
                    Girok.namgi("seoryu_damgi")
                } else {
                    Sori.mal("담지 못했습니다. 다시 해 주십시오.")
                }
                if (nnBoinda(t, this)) t.dasiGeurigi()
            }
        }
    }
}
