// 안드로이드 길눈 — 몸통: 판번호, 설정, 기록, 나스 통신 (2.0.0, 빌드 261001-A1, 대표님 승인)
// 아이폰 길눈(Gilnun 폴더)과 같은 설계도로, 웹 껍데기 없이 앱 속에서 돕니다.
// 2.3.0(빌드 261002-A6, 대표님 지시) 설정에 음향신호기 자동으로 잡기(sinhogiJadong, 처음부터 켜짐)
// 2.4.0(빌드 261002-A7, 대표님 지시) 말로 하기 판 기록
// 2.5.0(빌드 261002-A8, 대표님 지시) 갤럭시 워치 길눈 — 설정에 워치 번호(watchBeonho, 여섯 자리, 한 번 만들면 바뀌지 않음), 판 기록
package kr.or.ada.app.gilnun

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.Executors

/** 판번호와 고친 기록 — 고칠 때마다 맨 위에 더함 */
object Pan {
    const val pan = "2.5.0"
    const val bild = "261002-A8"

    class Gochim(val pan: String, val bild: String, val nal: String, val naeyong: List<String>)

    val girok = listOf(
        Gochim("2.5.0", "261002-A8", "2026-10-02", listOf(
            "갤럭시 워치 길눈(대표님 지시) — 아이폰 길눈의 애플워치 앱을 갤럭시 워치(웨어 OS)로 옮기고, 폰 길눈과 이음(WatchLink). 워치 앱은 폰 길눈과 같은 앱 번호·같은 서명",
            "폰이 워치에 — 길눈이 마지막으로 한 말, 따라 걷는 중의 다음에 무엇, 꺾을 때 방향 진동(왼쪽 짧게 두 번, 오른쪽 길게 한 번), 도착 진동, 음향신호기 결과",
            "점지도 따라 걷기를 시작하면 워치도 깨어 있기를 켜고 팔 흔들림으로 걸음을 세어 폰에 보냄. 폰이 4초 넘게 걸음을 못 세면(가방 속 등) 워치 걸음으로 이어 감(한 번에 열 걸음까지)",
            "손목 가리키기 — 가야 할 쪽(돌아야 할 때는 돌 쪽, 아니면 앞 6미터)을 5도 넘게 바뀌거나 10초마다 워치에 보냄. 가리키기 방향 맞추기에 지금 몸이 향한 방향을 알려 줌",
            "워치에서 부탁 — 다음 갈림길, 내 자리(길 찾기 첫 화면의 지금 내 자리와 같은 말), 마지막 안내, 말로 하기(폰 길눈이 듣기 시작), 음향신호기 위치·신호",
            "지팡이 떨림 기록(연구 1단계) — 워치가 보낸 기록을 받아 협회 나스(연구용)로 아이폰과 같은 꼴로 올림. 못 올리면 들고 있다가 10분 뒤 다시",
            "폰이 곁에 없을 때 워치가 받을 수 있게 마지막 안내를 나스에도 남김(워치 번호)",
            "도움말에 갤럭시 워치 길눈 항목 더함"
        )),
        Gochim("2.4.0", "261002-A7", "2026-10-02", listOf(
            "말로 하기를 안드로이드 길눈에도(대표님 지시) — 아이폰 길눈의 말로 하기를 같은 차례, 같은 말로 옮김. 받아쓰기는 폰의 구글 음성 인식, 알아듣기는 웹·아이폰과 같은 나스 사전",
            "길 찾기 탭 맨 위 첫 줄에 큰 말로 하기 단추. 누르면 「네」 하고 듣고(말소리를 끄셨으면 딩동), 듣는 중에 다시 누르면 그만. 처음 누를 때 마이크 허락을 여쭘",
            "듣는 동안에는 길눈 말을 잠시 맡아 두어 마이크로 들어가지 않게 하고, 다 들으면 이어서 말함. 위험 경고는 듣기를 그만두게 하고 곧바로 말함",
            "대답이 1초 넘게 걸리면 「잠깐만 기다려 주세요」, 못 들으면 「다시 한번 말씀해 주세요」 한 번만. 묻는 말에는 마이크를 한 번만 저절로 엶",
            "할 수 있는 말 — 지금 어디, 가까운 점지도, 따라 걷는 중의 다음에 무엇·다음 갈림길·어디쯤·그만 걷기·되돌아가자, 신호기 울려 줘·신호 알려 줘·신호기 찾아 줘, 날씨, 몇 시, 말 빠르게·느리게, 말소리 켜고 끄기, 다시 말해, 그만, 하던 일 멈춰, 여정 끝, 새로고침, 도움말",
            "가실 곳을 말씀하시면 가까운 점지도의 도착지·출발지·이름에서 찾고, 없으면 나스에서 그곳을 찾아 끝이 그곳 가까이 닿는 점지도를 찾음. 「○○까지 점지도를 따라 걸을까요? 네 또는 아니오로 말씀해 주십시오」 — 네 하시면 곧바로 따라 걷기, 아니오면 다음 길",
            "걸으실 때 한 손에 지팡이 — 길눈 화면이 켜져 있으면 이어폰 재생 단추를 길게(0.6초 넘게) 눌러도 말로 하기가 열림. 짧게 누르면 음악 앱에 그대로",
            "아직 안드로이드에 없는 기능(긴급통화, 음악·방송, 교통편 부르기, 카메라 눈 등)을 말씀하시면 그렇다고 알려 드리고 기록해 둠. 긴급통화는 112나 119를 알려 드림",
            "도움말에 말로 하기, 할 수 있는 말, 가실 곳 말하기, 이어폰 단추 더함"
        )),
        Gochim("2.3.0", "261002-A6", "2026-10-02", listOf(
            "음향신호기를 안드로이드 길눈에도(대표님 지시) — 아이폰 길눈의 음향신호기 엔진을 같은 규격, 같은 잣대, 같은 말로 옮김(SinhogiEngine)",
            "건널목 앞에서 폰을 꺼내지 않아도 되게 — 길눈이 켜져 있으면 화면이 꺼져도 둘레의 블루투스 음향신호기를 늘 살핌. 가까이 잡히면 위치 안내 한 번, 그 앞에 4초 넘게 머무르면 신호 안내 한 번, 같은 신호기에는 3분에 한 번, 이때 길눈은 말하지 않고 짧게 진동만",
            "보행신호 음성안내 장치가 있는 횡단보도 앞이면 5분에 한 번 알림",
            "설정 탭에 음향신호기 자동으로 잡기 — 처음부터 켜짐, 누르면 꺼짐",
            "길 찾기 탭에 음향신호기 펼치기 — 음향신호기 위치 안내 울리기, 음향신호기 신호 안내 울리기, 음향신호기 찾기(가까워질수록 소리가 빨라짐)",
            "블루투스가 꺼져 있으면 블루투스 켜기 창을 열고, 켜지면 하시던 요청을 이어 보냄. 근처 기기 허락이 없으면 그 자리에서 여쭘",
            "남산 현장 확인을 위한 기록 — 신호기나 음성안내 장치가 잡히면 한 기기에 한 번 이름, 세기, 자리를 남김. 기기 주소는 알아볼 수 없게 줄여서만",
            "도움말에 음향신호기 자동으로 잡기, 손으로 울리기, 찾기 더함"
        )),
        Gochim("2.2.0", "261002-A4", "2026-10-02", listOf(
            "점지도 따라 걷기를 안드로이드에도(대표님 지시) — 아이폰 길눈의 따라 걷기 엔진을 같은 잣대, 같은 시간, 같은 말로 옮김",
            "길 찾기 탭에 점지도 따라 걷기 단추. 가까운 점지도를 다섯씩 보여 드리고 아래에 더 보기와 이전 보기. 목록이 나오면 커서를 첫 결과 줄로",
            "길을 고르면 이 길 따라 걷기, 거꾸로 걷기(되돌아가기). 걷는 화면에는 지금 어디쯤인지 듣기, 방금 한 말 다시 듣기, 그만 걷기",
            "점지도 위를 제대로 디디면 걸음마다 맑은 확신음, 반 걸음 비켜나면 한 번 알림, 한 걸음 벗어나면 곧바로 경고음과 몇 시 방향으로 몇 걸음 옮기실지(경고음 2.5초, 말 5초마다), 돌아오시면 돌아옴 소리",
            "꺾이는 곳은 서른 걸음 앞, 열한 걸음 앞, 코앞에서. 계단, 건널목 같은 표시는 서른 걸음 앞, 일곱 미터 앞, 세 미터 앞에서. 10미터마다 제대로 가고 있습니다",
            "걷기 시작 뒤 첫 방향 확인, 도셔야 할 쪽과 반대로 크게 도시면 곧바로 알림(몸 센서 자이로)",
            "위성이 6초 넘게 끊기거나 흐리면 걸음 수와 보폭만큼 점지도 위를 나아간 것으로 셈(점지도의 바탕은 걸음)",
            "끌 수 없는 안전 안내 — 걷기 시작 한마디, 확인 중인 길, 안내가 10초 넘게 끊기면 안내가 끊겼습니다",
            "도착하면 도착 소리와 안내, 같은 자리에 되돌아가기 단추. 알림 소리는 길 안내 소리 자리로 내어 음악을 들으셔도 들림",
            "여러 점지도 이어 걷기, 문까지, 지나는 곳, 함께 시험하기, 워치는 다음 판에 옮김"
        )),
        Gochim("2.1.0", "261001-A3", "2026-10-01", listOf(
            "몸 센서를 길눈에도(대표님 지시) — 자봉이 점지도를 그릴 때와 같은 센서, 같은 셈법으로 걸음과 방향을 잼. 그린 100걸음과 걷는 100걸음이 같은 자로 맞음",
            "걸음: 폰 걸음 센서가 몇 초씩 몰아서 알려 주던 늦음을 몸 센서가 발이 땅에 닿을 때마다 곧바로 세어 메움",
            "방향: 자이로로 몸이 돈 각도를 재고 나침반 쪽으로 천천히 맞춤. 위성이 끊겨 걸음으로 자리를 이어 셀 때도 이 방향을 씀",
            "기초 시험에 몸 센서 듣기 더함. 도움말 몸 센서 항목 더함"
        )),
        Gochim("2.0.0", "261001-A1", "2026-10-01", listOf(
            "안드로이드 길눈을 속까지 앱으로 새로 지음(대표님 승인) — 웹을 띄우던 껍데기 1.2.1을 버리고 아이폰 길눈과 같은 설계도로 기초부터",
            "기초판: 위치(위성과 걸음으로 이어 셈), 방향, 걸음, 길눈 말소리, 기록, 나스 통신을 앱 속에서. 화면이 꺼져도 위치가 이어 돌게 알림 칸에 길눈이 떠 있음",
            "탭 다섯(길 찾기, 둘러보기, 음악·방송, 나눔, 설정)은 속 화면에도 늘 보임. 속 화면의 뒤로 단추는 위에 하나, 폰의 뒤로 동작도 앞 화면으로. 화면이 바뀌면 커서를 첫 줄로",
            "지금 내 자리 다시 듣기, 말소리 켜기 끄기, 말 빠르기 다섯 칸, 내 보폭 재기, 새로고침, 기초 시험, 판 기록, 도움말"
        ))
    )
}

/** 설정 — 폰에 담아 둠 */
object Seoljeong {
    private lateinit var d: SharedPreferences
    fun sijak(ctx: Context) { d = ctx.getSharedPreferences("gilnun", Context.MODE_PRIVATE) }

    var malKyeojim: Boolean
        get() = d.getBoolean("malKyeojim", true)
        set(v) { d.edit().putBoolean("malKyeojim", v).apply() }
    /** 말 빠르기 0 아주 느리게 ~ 4 아주 빠르게 */
    var bbareugiDan: Int
        get() = d.getInt("bbareugiDan", 2)
        set(v) { d.edit().putInt("bbareugiDan", v.coerceIn(0, 4)).apply() }
    /** 보폭(미터) — 재지 않았으면 0.65 로 셈 */
    var bopok: Double
        get() = d.getFloat("bopok", 0.65f).toDouble()
        set(v) { d.edit().putFloat("bopok", v.toFloat()).putBoolean("bopokJaem", true).apply() }
    val bopokJaem: Boolean get() = d.getBoolean("bopokJaem", false)
    /** 2.3.0 음향신호기 자동으로 잡기(처음부터 켜짐) — 끄면 손으로만 울림 */
    var sinhogiJadong: Boolean
        get() = d.getBoolean("sinhogiJadong", true)
        set(v) { d.edit().putBoolean("sinhogiJadong", v).apply() }
    /** 2.5.0 워치 번호(여섯 자리) — 한 번 만들면 바뀌지 않음(폰이 곁에 없을 때 워치가 나스에서 마지막 안내를 받는 열쇠) */
    val watchBeonho: String
        get() {
            val v = d.getString("watchBeonho", null)
            if (v != null && v.length == 6) return v
            val n = (0 until 6).map { "0123456789".random() }.joinToString("")
            d.edit().putString("watchBeonho", n).apply()
            return n
        }
    val dev: String
        get() {
            val v = d.getString("dev", null)
            if (v != null) return v
            val n = UUID.randomUUID().toString().take(8).uppercase()
            d.edit().putString("dev", n).apply()
            return n
        }
    fun georeumGijun(nal: String): Int = if (d.getString("georeumNal", "") == nal) d.getInt("georeumGijun", -1) else -1
    fun georeumGijunNoki(nal: String, v: Int) { d.edit().putString("georeumNal", nal).putInt("georeumGijun", v).apply() }

    val bbareugiIreum = listOf("아주 느리게", "느리게", "보통", "빠르게", "아주 빠르게")
}

/** 나스 통신 — https://lvd.ada.or.kr/jeom/… 로 가는 통로 하나 */
object Tongsin {
    private val il = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    @Volatile var yeongyeol = true
        private set

    fun juso(pail: String, q: Map<String, String>): String {
        val bon = if (pail.startsWith("/")) "https://lvd.ada.or.kr$pail" else "https://lvd.ada.or.kr/jeom/$pail"
        if (q.isEmpty()) return bon
        return bon + "?" + q.entries.joinToString("&") { URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8") }
    }

    /** JSON 하나 받기 — 결과는 화면 줄(main)에서. 못 받으면 null */
    fun json(pail: String, q: Map<String, String>, handO: Int = 8000, kkeut: (JSONObject?) -> Unit) {
        il.execute {
            var o: JSONObject? = null
            try {
                val c = URL(juso(pail, q)).openConnection() as HttpURLConnection
                c.connectTimeout = handO
                c.readTimeout = handO
                c.setRequestProperty("Accept", "application/json")
                if (c.responseCode == 200) {
                    val t = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    o = JSONObject(t)
                }
                c.disconnect()
                yeongyeol = true
            } catch (e: Exception) {
                yeongyeol = false
            }
            val r = o
            main.post { kkeut(r) }
        }
    }

    /** JSON 보내기(POST) — 되었는가만 알림 */
    fun bonaegi(url: String, bonmun: String, kkeut: (Boolean) -> Unit) {
        il.execute {
            var ok = false
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.doOutput = true
                c.connectTimeout = 15000
                c.readTimeout = 15000
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(bonmun.toByteArray(Charsets.UTF_8)) }
                ok = c.responseCode == 200
                c.disconnect()
                yeongyeol = true
            } catch (e: Exception) {
                yeongyeol = false
            }
            kkeut(ok)
        }
    }
}

/** 기록 — 폰이 겪은 일을 나스(applog.php)에 남김. 말씀하신 내용이나 걸으신 길은 남기지 않음. 통신이 끊기면 쌓아 두었다가 보냄 */
object Girok {
    private val jamgeum = Any()
    private var jul = JSONArray()
    private var bonaeneun = false
    private var pail: File? = null
    private val main = Handler(Looper.getMainLooper())
    private const val URL_ = "https://lvd.ada.or.kr/jeom/applog.php"

    fun sijak(ctx: Context) {
        pail = File(ctx.filesDir, "girok_jul.json")
        try { pail?.takeIf { it.exists() }?.let { jul = JSONArray(it.readText()) } } catch (e: Exception) {}
        val r = object : Runnable {
            override fun run() { bonaegi(); main.postDelayed(this, 20000) }
        }
        main.postDelayed(r, 5000)
    }

    fun namgi(e: String, d: Map<String, Any?> = emptyMap()) {
        val o = JSONObject()
        o.put("t", System.currentTimeMillis() / 1000.0)
        o.put("e", e)
        for ((k, v) in d) o.put(k, v ?: JSONObject.NULL)
        synchronized(jamgeum) {
            jul.put(o)
            while (jul.length() > 2000) jul.remove(0)
        }
    }

    fun jeojang() {
        val t = synchronized(jamgeum) { jul.toString() }
        try { pail?.writeText(t) } catch (e: Exception) {}
    }

    fun bonaegi() {
        val mukeum = JSONArray()
        synchronized(jamgeum) {
            if (bonaeneun || jul.length() == 0) return
            for (i in 0 until minOf(100, jul.length())) mukeum.put(jul.get(i))
            bonaeneun = true
        }
        val body = JSONObject()
        body.put("dev", Seoljeong.dev)
        body.put("app", "gilnun-android ${Pan.pan}(${Pan.bild})")
        body.put("ios", "android " + Build.VERSION.RELEASE + " " + Build.MODEL)
        body.put("ev", mukeum)
        Tongsin.bonaegi(URL_, body.toString()) { ok ->
            synchronized(jamgeum) {
                bonaeneun = false
                if (ok) {
                    repeat(minOf(mukeum.length(), jul.length())) { jul.remove(0) }
                }
            }
            if (ok) jeojang()
        }
    }
}
