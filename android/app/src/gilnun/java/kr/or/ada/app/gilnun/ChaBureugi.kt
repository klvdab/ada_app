// 2.29.0 (261009-A17, 이사장님 승인 2026-10-09 「클이 제안한 방법을 모두 승인한다」) 차 부르기 — 교통약자 묶음(아이폰 ChaBureugi.swift 2.58.0과 같음)
// ① 지역 이용 안내: 나스 jeom/kol_annae.json(전국 조사, 2026-10-08)으로 지금 시·군의 시각장애인 이용 조건과 등록하는 곳을 알려 드림
//    공식 누리집으로 확인한 곳만 조건을 말하고, 보도로만 확인한 곳은 "전화로 먼저 물어보십시오"라고 말함
// ② 복지카드 보내기: 등록이 필요한 곳은 내 서류 보관함의 서류를 그 지역 메일 주소를 채워 보내는 창으로(이용자가 메일 앱을 고르고 보냄)
// ③ 서울은 복지콜을 맨 앞에(서울시설공단 안내: 시각장애인은 원칙상 복지콜)
// ④ 부르기와 타기 돕기: 걸기 전에 상담원께 말할 것을 들려 드리고, 통화 중에도 알림 칸에서 볼 수 있게 남김
// ⑤ 이용 기록: 부를 때 저절로 남기고, 전화하고 돌아오시면 "배차되었습니까" 한마디만 여쭙고, 차에 타면 저절로 탄 시각을 남김 → 나의 이용 성적표
//    기록은 폰 안에만. 협회로 보내기는 동의하신 분만(받는 곳이 준비되면 보냄)
// ⑥ 정기 호출: 정한 요일·시각에 알림으로 깨워 드리고 한 번 두드리면 전화(전화 걸기 허락을 주시면 바로 걸림). 공휴일은 저절로 건너뜀, 그날만 쉬기·바꾸기는 말로
package kr.or.ada.app.gilnun

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.EditText
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.Calendar
import java.util.Locale
import java.util.UUID

// MARK: - 지역 이용 안내

class KolAnnae(
    val sido: String, val sigungu: String, val ireum: String, val iyong: String, val jogeon: String, val hwagin: String,
    val pilyo: String, val bangbeop: String, val seoryu: String, val paekseu: String, val meil: String,
    val unhaeng: String, val beomwi: String, val baucheo: Boolean
) {
    val gongsik: Boolean get() = hwagin == "공식"
    val deungrokPilyo: Boolean get() = pilyo.startsWith("필요")
}

object KolAnnaeJaryo {
    var jul: List<KolAnnae> = emptyList()
        private set
    private var batneun = false

    fun bureogi(kkeut: (() -> Unit)? = null) {
        if (batneun) return
        batneun = true
        Tongsin.json("kol_annae.json", emptyMap(), 12000) { o ->
            batneun = false
            if (o == null) return@json
            try {
                val w = o.optJSONArray("w") ?: JSONArray()
                val r = o.optJSONArray("r") ?: return@json
                fun mal(x: Any?): String {
                    if (x is Number) { val i = x.toInt(); return if (i >= 0 && i < w.length()) w.optString(i) else "" }
                    return (x as? String) ?: ""
                }
                fun geul(x: Any?): String = (x as? String) ?: ""
                val l = ArrayList<KolAnnae>()
                for (i in 0 until r.length()) {
                    val x = r.optJSONArray(i) ?: continue
                    if (x.length() < 14) continue
                    l.add(KolAnnae(geul(x.opt(0)), geul(x.opt(1)), geul(x.opt(2)), geul(x.opt(3)), mal(x.opt(4)), geul(x.opt(5)),
                        mal(x.opt(6)), mal(x.opt(7)), mal(x.opt(8)), geul(x.opt(9)), geul(x.opt(10)),
                        mal(x.opt(11)), mal(x.opt(12)), (x.opt(13) as? Number)?.toInt() == 1))
                }
                jul = l
                kkeut?.invoke()
            } catch (e: Exception) {
                Girok.namgi("kol_annae_oryu", mapOf("e" to (e.message ?: "").take(80)))
            }
        }
    }

    /** 지금 지역의 안내 — 시·군 → (서울은 복지콜) → 시·도 광역, 바우처택시는 뺌 */
    fun jigeum(): KolAnnae? {
        val j = KolJiyeok.jiyeok(NnKol.jaryo) ?: return null
        val l = jul.filter { it.sido == j.first && !it.baucheo }
        l.firstOrNull { a -> a.sigungu.isNotEmpty() && j.second.any { it == a.sigungu || it.startsWith(a.sigungu) || a.sigungu.startsWith(it) } }?.let { return it }
        if (j.first == "서울특별시") l.firstOrNull { it.ireum.contains("복지콜") }?.let { return it }
        return l.firstOrNull { it.sigungu.isEmpty() }
    }

    /** 들려 드릴 이용 조건과 등록하는 곳 */
    fun mal(a: KolAnnae?): String {
        if (a == null) return "이 지역 이용 조건은 아직 확인하지 못했습니다. 전화로 시각장애인도 탈 수 있는지 먼저 물어보십시오."
        if (!a.gongsik) return "${a.ireum}의 이용 조건은 아직 공식 안내로 확인하지 못했습니다. 전화로 시각장애인도 탈 수 있는지 먼저 물어보십시오."
        var t = if (a.iyong == "가능") "시각장애인도 탈 수 있습니다." else "장애 정도가 심한 시각장애인은 미리 등록하고 심사를 받아야 탈 수 있습니다."
        if (a.deungrokPilyo || a.iyong != "가능") {
            val gil = ArrayList<String>()
            if (a.paekseu.isNotEmpty()) gil.add("팩스 ${a.paekseu}")
            if (a.meil.isNotEmpty()) gil.add("메일")
            if (gil.isNotEmpty()) t += " 서류는 " + gil.joinToString("이나 ") + "로 보냅니다."
            else if (a.bangbeop.isNotEmpty() && !a.bangbeop.startsWith("확인 중")) t += " 등록은 이렇게 합니다. " + a.bangbeop
        }
        return t
    }
}

// MARK: - 이용 기록

class KolGirokHang(
    val id: String, val ttae: Long, val ireum: String, val jeonhwa: String, val sido: String, val sigungu: String, val jeonggi: Boolean,
    var gyeolgwa: String? = null,   // baecha 배차됨, gidarim 기다리라 함, andoem 안 된다 함
    var tan: Long = 0L,             // 차에 탄 때(저절로 알아챔)
    var yeojjum: Boolean = false,   // 돌아오신 뒤 여쭈었는지
    var naerim: Long = 0L           // 2.31.0 탄 뒤 걸어서 내린 때(저절로 알아챔)
) {
    fun json(): JSONObject = JSONObject().put("id", id).put("ttae", ttae).put("ireum", ireum).put("jeonhwa", jeonhwa)
        .put("sido", sido).put("sigungu", sigungu).put("jeonggi", jeonggi).put("gyeolgwa", gyeolgwa ?: "")
        .put("tan", tan).put("yeojjum", yeojjum).put("naerim", naerim)

    companion object {
        fun bat(o: JSONObject): KolGirokHang = KolGirokHang(o.optString("id"), o.optLong("ttae"), o.optString("ireum"), o.optString("jeonhwa"),
            o.optString("sido"), o.optString("sigungu"), o.optBoolean("jeonggi"),
            o.optString("gyeolgwa").ifEmpty { null }, o.optLong("tan"), o.optBoolean("yeojjum"), o.optLong("naerim"))
    }
}

// MARK: - 차 부르기 엔진

internal object ChaBureugi {
    private val main = Handler(Looper.getMainLooper())
    private var ctx: Context? = null
    private var d: SharedPreferences? = null
    private var dolgo = false

    val girok = ArrayList<KolGirokHang>()
    /** 돌아오신 뒤 "배차되었습니까"를 여쭌 기록 */
    var mureumId: String? = null
        private set
    private var mureumTtae = 0L
    /** 안 된다고 했을 때 권하는 다음 수단 */
    var daeum: NnKol.Kol? = null
        private set
    private var daeumTtae = 0L
    private var meilMureumTtae = 0L
    var hwalseong: WeakReference<GilnunActivity>? = null
    /** 길눈 화면이 앞에 있음 */
    var apIttda = false
        private set
    private var naganTtae = 0L
    private var jinanCha = false
    /** 차 부르기 화면을 다시 그릴 때 */
    var byeonhwa: (() -> Unit)? = null

    /** 보조견과 함께 타시는지(상담원께 말할 것) */
    var bojogyeon: Boolean
        get() = d?.getBoolean("bojogyeon", false) ?: false
        set(v) { d?.edit()?.putBoolean("bojogyeon", v)?.apply() }
    /** 협회로 이름 없이 보내기 동의 — 0 아직 안 여쭘, 1 동의, 2 동의 안 함 */
    var dongui: Int
        get() = d?.getInt("dongui", 0) ?: 0
        set(v) { d?.edit()?.putInt("dongui", v)?.apply() }

    fun sijak(c: Context) {
        if (dolgo) return
        dolgo = true
        ctx = c.applicationContext
        d = c.applicationContext.getSharedPreferences("gilnun_cha", Context.MODE_PRIVATE)
        try {
            val a = JSONArray(d?.getString("girok", "[]") ?: "[]")
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { girok.add(KolGirokHang.bat(it)) }
        } catch (_: Exception) {}
        KolAnnaeJaryo.bureogi { byeonhwa?.invoke() }
        JeonggiHochul.sijak(c)
        // 차에 탄 것을 저절로 — 탈것 알아채기가 차·버스로 바뀌는 때
        main.postDelayed(object : Runnable {
            override fun run() {
                val cha = TalgeotGamji.chujeong == Talgeot.CHA || TalgeotGamji.chujeong == Talgeot.BEOSEU
                if (cha && !jinanCha) chaTatda()
                if (!cha && TalgeotGamji.chujeong == Talgeot.GEOREUM && TalgeotGamji.jigeumUmjigim == "걸음") chaNaerim()
                jinanCha = cha
                main.postDelayed(this, 10_000)
            }
        }, 10_000)
    }

    private fun jeojang() {
        while (girok.size > 500) girok.removeAt(0)
        val a = JSONArray()
        for (h in girok) a.put(h.json())
        d?.edit()?.putString("girok", a.toString())?.apply()
    }

    private fun gochigi(id: String, f: (KolGirokHang) -> Unit) {
        val h = girok.firstOrNull { it.id == id } ?: return
        f(h)
        jeojang()
    }

    // MARK: 지금 지역의 수단

    /** 지금 지역의 콜 — 서울은 복지콜을 맨 앞에 */
    fun sudanDeul(): List<NnKol.Kol> {
        val l = ArrayList(KolJiyeok.mok(NnKol.jaryo))
        if (KolJiyeok.jiyeok(NnKol.jaryo)?.first == "서울특별시") {
            val i = l.indexOfFirst { it.ireum.contains("복지콜") }
            if (i > 0) { val b = l.removeAt(i); l.add(0, b) }
        }
        return l
    }

    /** 정기 호출의 수단 — bokji, jangaein, nabi */
    fun sudan(jong: String): NnKol.Kol? {
        val l = sudanDeul()
        if (jong == "bokji") l.firstOrNull { it.ireum.contains("복지") }?.let { return it }
        return KolJiyeok.chatgi(NnKol.jaryo, jong) ?: l.firstOrNull()
    }

    // MARK: 걸기

    fun barohalsuMal(c: Context) = ContextCompat.checkSelfPermission(c, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    /** 상담원께 말할 것을 들려 드린 뒤 전화를 겁니다. 말로 하기에서 부르면 dap 으로 말로 하기를 마침 */
    fun geolgi(k: NnKol.Kol, jeonggi: Boolean = false, dap: ((String, Boolean) -> Unit)? = null) {
        val j = KolJiyeok.jiyeok(NnKol.jaryo)
        val h = KolGirokHang(UUID.randomUUID().toString(), System.currentTimeMillis(), k.ireum, k.jeonhwa,
            j?.first ?: "", j?.second?.firstOrNull() ?: "", jeonggi)
        girok.add(h)
        mureumId = null
        daeum = null
        jeojang()
        byeonhwa?.invoke()
        Girok.namgi("kol_georeum", mapOf("ireum" to k.ireum, "jeonggi" to jeonggi))
        dap?.invoke("", false)   // 말로 하기는 여기서 마침(대답 뒤 마이크가 다시 열리지 않게)
        halMal(jeonggi) { hal ->
            val c = ctx
            val baro = c != null && barohalsuMal(c)
            val kkori = (if (baro) " 말이 끝나면 바로 전화가 걸립니다." else " 전화 화면이 열리면 통화 단추를 누르십시오.") +
                (if (hal.isEmpty()) "" else " 통화 중에도 알림 칸에서 이 말을 다시 보실 수 있습니다.")
            val t = "${k.ireum}에 겁니다. " + (if (hal.isEmpty()) "" else "연결되면 이렇게 말씀하시면 됩니다. $hal") + kkori
            halMalAllim(hal)
            var han = false
            val g: () -> Unit = { if (!han) { han = true; jeonhwaGeolgi(k.jeonhwa) } }
            Sori.mal(t, MalGeup.ANNAE) { g() }
            main.postDelayed({ g() }, (2000 + t.length * 200L).coerceAtMost(40_000L))
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun jeonhwaGeolgi(beonho: String) {
        val a = hwalseong?.get()?.takeIf { !it.isFinishing }
        val c: Context = a ?: ctx ?: return
        val i = Intent(if (barohalsuMal(c)) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:$beonho"))
        if (a == null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            c.startActivity(i)
        } catch (e: Exception) {
            try {
                val i2 = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$beonho"))
                if (a == null) i2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                c.startActivity(i2)
            } catch (e2: Exception) {
                Sori.mal("전화 화면을 열지 못했습니다. 번호는 ${MalHagi.beonhoMal(beonho)}입니다.")
            }
        }
    }

    /** 상담원께 말할 것 — 출발 주소와 입구, 도착지, 도착 희망 시각, 보조견 */
    private fun halMal(jeonggi: Boolean, kkeut: (String) -> Unit) {
        var chulbal = ""
        var dochak = ""
        var sigak = ""
        var bj = bojogyeon
        if (jeonggi) {
            val s = JeonggiHochul.oneul()
            chulbal = if (s.chulbalJuso.isEmpty()) s.chulbalIreum else s.chulbalJuso + (if (s.chulbalIreum.isEmpty()) "" else ", ${s.chulbalIreum}")
            dochak = s.dochakIreum + (if (s.dochakJuso.isEmpty() || s.dochakJuso == s.dochakIreum) "" else ", ${s.dochakJuso}")
            sigak = JeonggiHochul.sigakMal(s.dochakSi, s.dochakBun)
            bj = s.bojogyeon
        }
        if (dochak.isEmpty()) YeojeongEngine.jigeum?.let { dochak = it.mokjeok.ireum }
        var han = false
        val majimak: () -> Unit = {
            if (!han) {
                han = true
                val t = ArrayList<String>()
                if (chulbal.isNotEmpty()) t.add("출발은 ${chulbal}입니다.")
                if (dochak.isNotEmpty()) t.add("도착은 $dochak" + (if (sigak.isEmpty()) "입니다." else ", ${sigak}까지 가야 합니다."))
                t.add(if (bj) "보조견과 함께 탑니다." else "보조견은 없습니다.")
                kkeut(t.joinToString(" "))
            }
        }
        val w = Wichi.jigeum
        if (chulbal.isEmpty() && w != null) {
            Chatgi.juso(w.lat, w.lon) { j -> if (!han) chulbal = j ?: ""; majimak() }
            main.postDelayed({ majimak() }, 4000)   // 주소를 4초 안에 못 받으면 주소 없이
        } else {
            majimak()
        }
    }

    /** 통화 중에도 볼 수 있게 할 말을 알림 칸에 남김 */
    @android.annotation.SuppressLint("MissingPermission")
    private fun halMalAllim(hal: String) {
        val c = ctx ?: return
        if (hal.isEmpty()) return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.getNotificationChannel("gilnun_kol") == null)
                    nm.createNotificationChannel(NotificationChannel("gilnun_kol", "상담원께 말씀하실 것", NotificationManager.IMPORTANCE_DEFAULT))
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val b = NotificationCompat.Builder(c, "gilnun_kol")
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle("상담원께 말씀하실 것")
                .setContentText(hal)
                .setStyle(NotificationCompat.BigTextStyle().bigText(hal))
                .setAutoCancel(true)
            NotificationManagerCompat.from(c).notify(7301, b.build())
        } catch (e: Exception) {
            Girok.namgi("kol_halmal_oryu", mapOf("e" to (e.message ?: "").take(80)))
        }
    }

    // MARK: 전화하고 돌아오시면 한마디 여쭙기

    /** 길눈 화면이 앞으로 — GilnunActivity.onResume */
    fun dorawatda(a: GilnunActivity) {
        hwalseong = WeakReference(a)
        apIttda = true
        main.postDelayed({ salpigi() }, 1500)
    }

    /** 길눈 화면이 뒤로 — GilnunActivity.onPause */
    fun naganda() {
        apIttda = false
        naganTtae = System.currentTimeMillis()
    }

    /** 걸고 2시간 안, 아직 결과를 모르는 기록이 있으면 한 번만 여쭘(걸고 나서 길눈 밖에 나갔다 오셨을 때만) */
    fun salpigi() {
        if (!apIttda || mureumId != null) return
        val h = girok.lastOrNull() ?: return
        if (h.gyeolgwa != null || h.yeojjum || h.tan != 0L) return
        val jinan = System.currentTimeMillis() - h.ttae
        if (jinan > 7_200_000L || jinan < 30_000L || naganTtae < h.ttae) return
        gochigi(h.id) { it.yeojjum = true }
        mureumId = h.id
        mureumTtae = System.currentTimeMillis()
        hwamyeonYeolgi()
        Sori.mal("${h.ireum}, 배차되었습니까? 되었다, 기다리라고 했다, 안 된다고 했다 가운데 말씀해 주십시오. 화면의 단추를 누르셔도 됩니다.") {
            if (mureumId != null) MalHagi.dudeurim()
        }
    }

    /** 단추나 말로 받은 대답 */
    fun dapBatgi(g: String) {
        val id = mureumId ?: girok.lastOrNull()?.id ?: return
        mureumId = null
        gochigi(id) { it.gyeolgwa = g }
        Girok.namgi("kol_gyeolgwa", mapOf("g" to g))
        when (g) {
            "baecha" -> Sori.mal("배차되었다고 남겼습니다. 차에 타시면 저절로 알아채 기다리신 시간을 남겨 드립니다.")
            "gidarim" -> Sori.mal("기다리라고 했다고 남겼습니다. 차에 타시면 저절로 알아채 남겨 드립니다.")
            else -> {
                val jigeum = girok.firstOrNull { it.id == id }
                val l = sudanDeul().filter { it.jeonhwa != jigeum?.jeonhwa }
                val n = l.firstOrNull { it.ireum.contains("나비") || it.ireum.contains("바우처") } ?: l.firstOrNull()
                if (n != null) {
                    daeum = n
                    daeumTtae = System.currentTimeMillis()
                    val nm = jigeum?.ireum ?: "그 콜"
                    Sori.mal("${nm}에서 안 된다고 했습니다. ${n.ireum}에 걸까요? 걸어 줘, 또는 아니라고 말씀해 주십시오.") {
                        if (daeum != null) MalHagi.dudeurim()
                    }
                } else {
                    Sori.mal("안 된다고 했다고 남겼습니다. 이 지역에는 다른 수단 번호가 없습니다. 길 찾기에서 대중교통이나 걷기 안내를 받으실 수 있습니다.")
                }
            }
        }
        byeonhwa?.invoke()
    }

    fun daeumGeolgi() {
        val n = daeum ?: return
        daeum = null
        geolgi(n)
    }

    fun daeumGeuman() {
        daeum = null
        Sori.mal("알겠습니다.")
        byeonhwa?.invoke()
    }

    // MARK: 차에 탄 것을 저절로

    private fun chaTatda() {
        val h = girok.lastOrNull() ?: return
        if (h.tan != 0L || h.gyeolgwa == "andoem") return
        val jinan = System.currentTimeMillis() - h.ttae
        if (jinan < 60_000L || jinan > 3 * 3_600_000L) return
        gochigi(h.id) { it.tan = System.currentTimeMillis() }
        if (mureumId == h.id) {
            mureumId = null
            gochigi(h.id) { if (it.gyeolgwa == null) it.gyeolgwa = "baecha" }
        }
        val bun = maxOf(1, Math.round(jinan / 60_000.0).toInt())
        val cal = Calendar.getInstance().apply { timeInMillis = h.ttae }
        Sori.mal("차에 타셨습니다. ${JeonggiHochul.sigakMal(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))}에 부르셔서 ${bun}분 기다리셨습니다.", MalGeup.JEONGBO)
        Girok.namgi("kol_tan", mapOf("bun" to bun))
        byeonhwa?.invoke()
    }

    /** 2.31.0 탄 뒤 걸으심 — 내리신 것으로 남김(차 못 박기를 풂) */
    private fun chaNaerim() {
        val h = girok.lastOrNull() ?: return
        if (h.tan == 0L || h.naerim != 0L) return
        gochigi(h.id) { it.naerim = System.currentTimeMillis() }
        Girok.namgi("kol_naerim", mapOf())
    }

    /** 2.31.0 (261009, 이사장님 승인 — 남산 가실 때 복지콜을 지하철로 안 일, 아이폰과 같은 뜻) 콜을 불러 배차되었거나 탄 뒤 3시간 안이고
     *  아직 걸어서 내리지 않으셨으면 — 탈것을 차로 못 박음(땅속으로 내려간 때만 빼고) */
    val chaGojeong: Boolean
        get() {
            val h = girok.lastOrNull() ?: return false
            if (h.gyeolgwa == "andoem" || h.naerim != 0L) return false
            if (System.currentTimeMillis() - h.ttae > 3 * 3_600_000L) return false
            return h.gyeolgwa == "baecha" || h.gyeolgwa == "gidarim" || h.tan != 0L
        }

    /** 2.31.0 가장 최근 콜(3시간 안, 안 된다고 하지 않은 것) — "복지콜 기다리는 중"처럼 말씀하시면 다시 걸지 않고 형편을 알려 드림 */
    val choegeun: KolGirokHang?
        get() {
            val h = girok.lastOrNull() ?: return null
            if (h.gyeolgwa == "andoem" || h.naerim != 0L || System.currentTimeMillis() - h.ttae > 3 * 3_600_000L) return null
            return h
        }

    // MARK: 나의 이용 성적표

    fun seongjeokpyo(): String {
        val now = Calendar.getInstance()
        val y = now.get(Calendar.YEAR)
        val m = now.get(Calendar.MONTH)
        val l = girok.filter { h -> Calendar.getInstance().apply { timeInMillis = h.ttae }.let { it.get(Calendar.YEAR) == y && it.get(Calendar.MONTH) == m } }
        val dal = m + 1
        if (l.isEmpty()) return "${dal}월에는 아직 부르신 기록이 없습니다."
        val tan = l.filter { it.tan != 0L || it.gyeolgwa == "baecha" }
        var t = "${dal}월에는 콜을 ${l.size}번 부르셨고 ${tan.size}번 타셨습니다."
        val gidarim = l.filter { it.tan != 0L }.map { (it.tan - it.ttae) / 60_000.0 }
        if (gidarim.isNotEmpty()) t += " 평균 ${Math.round(gidarim.average()).toInt()}분 기다리셨습니다."
        val andoem = l.filter { it.gyeolgwa == "andoem" }
        if (andoem.isNotEmpty()) {
            val si = HashMap<Int, Int>()
            for (h in andoem) {
                val s = Calendar.getInstance().apply { timeInMillis = h.ttae }.get(Calendar.HOUR_OF_DAY)
                si[s] = (si[s] ?: 0) + 1
            }
            si.maxByOrNull { it.value }?.let { e ->
                val k = e.key
                t += " " + (if (k < 12) "아침 " else if (k < 18) "낮 " else "저녁 ") + "${if (k > 12) k - 12 else k}시대가 가장 안 잡혔습니다."
            }
        }
        return t
    }

    // MARK: 화면

    fun hwamyeonYeolgi() {
        val a = hwalseong?.get() ?: return
        if (a.isFinishing) return
        if (a.wiHwamyeon is ChaBureugiHwamyeon) a.dasiGeurigi() else a.cheotHwamyeonEuro(ChaBureugiHwamyeon())
    }

    fun seoryuItna(c: Context) = NnSeoryuham.jongryu.any { NnSeoryuham.itna(c, it.first) }

    /** 복지카드를 그 지역 메일로 보낼지 여쭘 */
    fun meilMutgi(a: KolAnnae) {
        val c = ctx ?: return
        if (a.meil.isEmpty() || !seoryuItna(c)) return
        meilMureumTtae = System.currentTimeMillis()
        Sori.mal("내 서류 보관함의 복지카드를 지금 메일로 보낼까요? 보내 줘, 또는 아니라고 말씀해 주십시오.") { MalHagi.dudeurim() }
    }

    /** 복지카드와 적어 둔 것을 그 지역 메일 주소를 채워 보내는 창으로 — 이용자가 메일 앱을 고르고 보내기를 누름 */
    fun meilBonaegi(t: GilnunActivity, an: KolAnnae) {
        val ki = NnSeoryuham.jongryu.map { it.first }.filter { NnSeoryuham.itna(t, it) }
        if (ki.isEmpty()) {
            Sori.mal("내 서류 보관함에 담아 둔 서류가 없습니다. 먼저 복지카드를 담아 주십시오.")
            t.yeolgi(SeoryuhamHwamyeon())
            return
        }
        try {
            val u = ki.map { NnSeoryuham.bonaelUri(t, it) }
            var bonmun = "${an.ireum} 이용 등록을 신청합니다. 시각장애인입니다. 복지카드를 붙입니다."
            val j = NnSeoryuham.jeokeunGeul(t)
            if (j.isNotEmpty()) bonmun += "\n\n" + j
            bonmun += "\n\n길눈 앱에서 보냅니다."
            val i = if (u.size == 1) {
                Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, u[0])
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/jpeg").putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(u))
            }
            i.putExtra(Intent.EXTRA_EMAIL, arrayOf(an.meil))
            i.putExtra(Intent.EXTRA_SUBJECT, "${an.ireum} 이용 등록 서류")
            i.putExtra(Intent.EXTRA_TEXT, bonmun)
            val cd = ClipData.newRawUri(NnSeoryuham.ireum(ki[0]), u[0])
            for (x in u.drop(1)) cd.addItem(ClipData.Item(x))
            i.clipData = cd
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            Sori.mal("보내는 창을 엽니다. 지메일 같은 메일 앱을 고르시면 받는 곳 ${an.meil}과 서류가 채워져 있습니다. 확인하신 뒤 보내기를 눌러 주십시오.")
            t.startActivity(Intent.createChooser(i, "복지카드 메일로 보내기"))
            Girok.namgi("kol_seoryu_meil", mapOf("n" to u.size))
        } catch (e: Exception) {
            Girok.namgi("kol_seoryu_meil_oryu", mapOf("e" to (e.message ?: "").take(80)))
            Sori.mal("보내는 창을 열지 못했습니다.")
        }
    }

    // MARK: 말로 하기

    /** 말로 하기에서 차 부르기·정기 호출·성적표에 해당하면 처리하고 true */
    fun malCheori(alts: List<String>, z: String, dap: (String, Boolean) -> Unit): Boolean {
        val now = System.currentTimeMillis()
        val ye = listOf("응", "네", "예", "그래", "걸어", "보내", "좋아", "부탁").any { z.startsWith(it) || z.contains(it + "줘") }
        val ani = z.startsWith("아니") || z.contains("하지마") || z.contains("됐어그만") || z == "아뇨"
        // 1. 배차되었습니까 — 여쭌 지 10분 안의 말만(그 뒤 다른 말을 대답으로 잘못 듣지 않게. 화면 단추는 그대로)
        if (mureumId != null && now - mureumTtae < 600_000L) {
            if (z.contains("안돼") || z.contains("안된") || z.contains("안됐") || z.contains("안되") || z.contains("없대") || z.contains("없다") || z.contains("못")) {
                dap("", false); dapBatgi("andoem"); return true
            }
            if (z.contains("기다리") || z.contains("대기")) {
                dap("", false); dapBatgi("gidarim"); return true
            }
            if (z.contains("됐") || z.contains("되었") || z.contains("배차") || z.contains("온대") || z.contains("온다")) {
                dap("", false); dapBatgi("baecha"); return true
            }
        }
        // 2. 다음 수단에 걸까요
        if (daeum != null && now - daeumTtae < 600_000L && z.length <= 12) {
            if (ani) { dap("", false); daeumGeuman(); return true }
            if (ye) { dap("", false); daeumGeolgi(); return true }
        }
        // 3. 복지카드 메일로 보낼까요
        if (now - meilMureumTtae < 300_000L && z.length <= 12) {
            if (ani) { meilMureumTtae = 0L; dap("알겠습니다.", false); return true }
            if (ye) {
                meilMureumTtae = 0L
                val an = KolAnnaeJaryo.jigeum()
                val a = hwalseong?.get()
                if (an == null || an.meil.isEmpty() || a == null || a.isFinishing) { dap("이 지역 메일 주소를 찾지 못했습니다. 차 부르기 화면의 그 밖에 펼치기를 보아 주십시오.", false); return true }
                dap("", false)
                hwamyeonYeolgi()
                main.postDelayed({ meilBonaegi(a, an) }, 800)
                return true
            }
        }
        // 4. 성적표
        if (z.contains("성적표") || (z.contains("이용") && z.contains("기록") && (z.contains("콜") || z.contains("차")))) {
            dap(seongjeokpyo(), false); return true
        }
        // 5. 이 지역 이용 조건
        if ((z.contains("콜") || z.contains("교통약자")) && (z.contains("조건") || z.contains("등록") || z.contains("가입"))) {
            if (KolAnnaeJaryo.jul.isEmpty()) KolAnnaeJaryo.bureogi()
            val a = KolAnnaeJaryo.jigeum()
            dap(NnKol.jiyeokMal() + " " + KolAnnaeJaryo.mal(a), false)
            if (a != null && a.gongsik && a.meil.isNotEmpty()) main.postDelayed({ meilMutgi(a) }, 9000)
            return true
        }
        // 6. 정기 호출
        return JeonggiHochul.malCheori(alts, z, dap)
    }
}

// MARK: - 정기 호출

data class JeonggiSeoljeong(
    var kyeojim: Boolean = false,
    var yoil: MutableList<Int> = mutableListOf(2, 3, 4, 5, 6),   // 달력 요일 — 1 일요일, 2 월요일 … 7 토요일
    var si: Int = 7,
    var bun: Int = 30,
    var chulbalIreum: String = "",       // 비우면 부를 때 지금 자리 주소
    var chulbalJuso: String = "",
    var dochakIreum: String = "",
    var dochakJuso: String = "",
    var dochakSi: Int = 9,
    var dochakBun: Int = 0,
    var sudan: String = "bokji",         // bokji 복지콜, jangaein 장애인콜·교통약자 콜, nabi 나비콜·바우처택시
    var bojogyeon: Boolean = false
) {
    fun bokje(): JeonggiSeoljeong = copy(yoil = yoil.toMutableList())

    fun json(): JSONObject = JSONObject().put("kyeojim", kyeojim).put("yoil", JSONArray(yoil)).put("si", si).put("bun", bun)
        .put("chulbalIreum", chulbalIreum).put("chulbalJuso", chulbalJuso).put("dochakIreum", dochakIreum).put("dochakJuso", dochakJuso)
        .put("dochakSi", dochakSi).put("dochakBun", dochakBun).put("sudan", sudan).put("bojogyeon", bojogyeon)

    companion object {
        fun bat(o: JSONObject): JeonggiSeoljeong {
            val a = o.optJSONArray("yoil")
            val y = if (a == null) mutableListOf(2, 3, 4, 5, 6) else MutableList(a.length()) { a.optInt(it) }
            return JeonggiSeoljeong(o.optBoolean("kyeojim"), y, o.optInt("si", 7), o.optInt("bun", 30),
                o.optString("chulbalIreum"), o.optString("chulbalJuso"), o.optString("dochakIreum"), o.optString("dochakJuso"),
                o.optInt("dochakSi", 9), o.optInt("dochakBun", 0), o.optString("sudan", "bokji"), o.optBoolean("bojogyeon"))
        }
    }
}

object JeonggiHochul {
    const val ALLIM = "kr.or.ada.app.gilnun.JEONGGI_ALLIM"
    const val YEOLGI = "kr.or.ada.app.gilnun.JEONGGI"
    const val HEORAK_BEON = 22   // 전화 걸기 허락

    private var ctx: Context? = null
    private var d: SharedPreferences? = null
    private var hyuilBatgo = false

    var s = JeonggiSeoljeong()
        private set
    /** 그날만 바꾸기 — "yyyyMMdd": "swim" 쉬기, "HHmm" 시각, "dochak:이름" 도착 */
    private val bakkum = HashMap<String, String>()
    var daeumMal = "정기 호출이 꺼져 있습니다."
        private set

    /** 한국천문연구원 월력요항(2026-10-09 확인) — 나스 jeom/hyuil.json 이 있으면 그것을 더함 */
    val gibonHyuil: Set<String> = setOf("20261009", "20261225",
        "20270101", "20270206", "20270207", "20270208", "20270209", "20270301", "20270501", "20270503", "20270505",
        "20270513", "20270606", "20270717", "20270719", "20270815", "20270816", "20270914", "20270915", "20270916",
        "20271003", "20271004", "20271009", "20271011", "20271225", "20271227")
    private var hyuil: Set<String> = gibonHyuil   // gibonHyuil 뒤에 두어야 함(초기화 차례)

    val yoilIreum = listOf("", "일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일")

    /** 설정만 읽어 둠(폰을 켰을 때·알림 때에도 씀) */
    fun junbi(c: Context) {
        if (d != null) return
        ctx = c.applicationContext
        d = c.applicationContext.getSharedPreferences("gilnun_jeonggi", Context.MODE_PRIVATE)
        try { d?.getString("s", null)?.let { s = JeonggiSeoljeong.bat(JSONObject(it)) } } catch (_: Exception) {}
        try {
            val o = JSONObject(d?.getString("bakkum", "{}") ?: "{}")
            for (k in o.keys()) bakkum[k] = o.optString(k)
        } catch (_: Exception) {}
        try {
            val a = JSONArray(d?.getString("hyuil", "[]") ?: "[]")
            if (a.length() > 0) hyuil = gibonHyuil + (0 until a.length()).map { a.optString(it) }
        } catch (_: Exception) {}
    }

    fun sijak(c: Context) {
        junbi(c)
        if (!hyuilBatgo) {
            hyuilBatgo = true
            Tongsin.json("hyuil.json", emptyMap()) { o ->
                val a = o?.optJSONArray("nal") ?: return@json
                if (a.length() == 0) return@json
                hyuil = gibonHyuil + (0 until a.length()).map { a.optString(it) }
                d?.edit()?.putString("hyuil", a.toString())?.apply()
                jaeYeyak()
            }
        }
        jaeYeyak()
    }

    private fun jeojang() {
        val oneul = ymd(System.currentTimeMillis())
        val o = JSONObject()
        for ((k, v) in bakkum) if (k >= oneul) o.put(k, v)
        d?.edit()?.putString("s", s.json().toString())?.putString("bakkum", o.toString())?.apply()
    }

    fun ymd(t: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        return String.format(Locale.US, "%04d%02d%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    fun sigakMal(si: Int, bun: Int): String {
        val ap = if (si < 12) "아침 " else if (si < 18) "낮 " else "저녁 "
        val h = if (si > 12) si - 12 else if (si == 0) 12 else si
        return ap + "${h}시" + (if (bun == 0) "" else if (bun == 30) " 반" else " ${bun}분")
    }

    fun nalMal(t: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        return "${c.get(Calendar.MONTH) + 1}월 ${c.get(Calendar.DAY_OF_MONTH)}일 ${yoilIreum[c.get(Calendar.DAY_OF_WEEK)]}"
    }

    val sudanIreum: String
        get() = when (s.sudan) { "jangaein" -> "교통약자 콜"; "nabi" -> "나비콜"; else -> "복지콜" }

    /** 오늘 쓸 설정(그날만 바꾼 도착을 넣어서) */
    fun oneul(): JeonggiSeoljeong {
        val x = s.bokje()
        val b = bakkum[ymd(System.currentTimeMillis())]
        if (b != null && b.startsWith("dochak:")) {
            val nm = b.substring(7)
            x.dochakIreum = nm
            x.dochakJuso = Jeulgyeo.mokrok.firstOrNull { it.ireum == nm }?.juso ?: nm
        }
        return x
    }

    /** 정확한 시각에 알릴 수 있는지(안드로이드 12부터 폰 설정에서 허락) */
    fun jeonghwakHeorak(c: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = c.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return am.canScheduleExactAlarms()
    }

    private fun allimPi(c: Context): PendingIntent =
        PendingIntent.getBroadcast(c, 7310, Intent(c, JeonggiReceiver::class.java).setAction(ALLIM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** 다음 한 번의 알림을 다시 걸기(앞으로 14일 안, 공휴일·쉬는 날 건너뜀) */
    @android.annotation.SuppressLint("MissingPermission", "ScheduleExactAlarm")
    fun jaeYeyak() {
        val c = ctx ?: return
        val am = c.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = allimPi(c)
        am.cancel(pi)
        val t = daeumTtae()
        if (t == null) {
            daeumMal = if (s.kyeojim) "앞으로 14일 안에는 알림이 없습니다." else "정기 호출이 꺼져 있습니다."
            return
        }
        try {
            if (jeonghwakHeorak(c)) {
                val yeol = PendingIntent.getActivity(c, 7311, Intent(c, GilnunActivity::class.java).setAction(YEOLGI)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                am.setAlarmClock(AlarmManager.AlarmClockInfo(t, yeol), pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
            }
        } catch (e: Exception) {
            try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi) } catch (_: Exception) {}
            Girok.namgi("jeonggi_yeyak_oryu", mapOf("e" to (e.message ?: "").take(80)))
        }
        val cal = Calendar.getInstance().apply { timeInMillis = t }
        daeumMal = "다음 알림은 ${nalMal(t)} ${sigakMal(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))}입니다."
    }

    /** 다음에 알릴 때 — 없으면 null */
    private fun daeumTtae(): Long? {
        if (!s.kyeojim) return null
        val now = System.currentTimeMillis()
        val ulrin = d?.getString("ulrin", "") ?: ""
        for (i in 0 until 15) {
            val nal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_MONTH, i)
            }
            val k = ymd(nal.timeInMillis)
            if (k == ulrin) continue
            if (!s.yoil.contains(nal.get(Calendar.DAY_OF_WEEK)) || hyuil.contains(k) || bakkum[k] == "swim") continue
            var si = s.si
            var bun = s.bun
            val b = bakkum[k]
            if (b != null && b.length == 4 && b.all { it.isDigit() }) { val v = b.toInt(); si = v / 100; bun = v % 100 }
            nal.set(Calendar.HOUR_OF_DAY, si); nal.set(Calendar.MINUTE, bun)
            if (nal.timeInMillis > now + 20_000L) return nal.timeInMillis
        }
        return null
    }

    /** 알림 때 — JeonggiReceiver */
    @android.annotation.SuppressLint("MissingPermission")
    fun allimTtae(c: Context) {
        junbi(c)
        val k = ymd(System.currentTimeMillis())
        val ttae = s.kyeojim && bakkum[k] != "swim" && !hyuil.contains(k) && (d?.getString("ulrin", "") ?: "") != k
        if (ttae) {
            d?.edit()?.putString("ulrin", k)?.apply()
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (nm.getNotificationChannel("gilnun_jeonggi") == null)
                        nm.createNotificationChannel(NotificationChannel("gilnun_jeonggi", "정기 호출", NotificationManager.IMPORTANCE_HIGH))
                }
                var dochak = s.dochakIreum
                val b = bakkum[k]
                if (b != null && b.startsWith("dochak:")) dochak = b.substring(7)
                val mal = (if (ChaBureugi.barohalsuMal(c)) "두드리시면 바로 전화가 걸립니다." else "두드리시면 전화 화면이 열립니다.") +
                    (if (dochak.isEmpty()) "" else " ${dochak}까지 ${sigakMal(s.dochakSi, s.dochakBun)}.")
                if (Build.VERSION.SDK_INT < 33 ||
                    ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    val yeol = PendingIntent.getActivity(c, 7312, Intent(c, GilnunActivity::class.java).setAction(YEOLGI)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    val nb = NotificationCompat.Builder(c, "gilnun_jeonggi")
                        .setSmallIcon(android.R.drawable.ic_menu_call)
                        .setContentTitle("$sudanIreum 부를 시간입니다")
                        .setContentText(mal)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(mal))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setContentIntent(yeol)
                        .setAutoCancel(true)
                    NotificationManagerCompat.from(c).notify(7312, nb.build())
                }
                if (ChaBureugi.apIttda) Sori.mal("$sudanIreum 부를 시간입니다. 알림을 두드리시거나 차 부르기에서 정기 호출로 거시면 됩니다.", MalGeup.ANNAE)
                Girok.namgi("jeonggi_allim", mapOf("k" to k))
            } catch (e: Exception) {
                Girok.namgi("jeonggi_allim_oryu", mapOf("e" to (e.message ?: "").take(80)))
            }
        }
        jaeYeyak()
    }

    /** 정한 내용을 들려 드리는 말 */
    fun hwaginMal(): String {
        val yo = when {
            s.yoil.sorted() == listOf(2, 3, 4, 5, 6) -> "월요일부터 금요일까지"
            s.yoil.size == 7 -> "날마다"
            else -> s.yoil.sortedBy { if (it == 1) 8 else it }.joinToString(", ") { yoilIreum[it] } + "마다"
        }
        val chul = s.chulbalIreum.ifEmpty { "부를 때 계신 곳" }
        val dc = s.dochakIreum.ifEmpty { "아직 정하지 않음" }
        return "$yo ${sigakMal(s.si, s.bun)}에 알려 드립니다. 출발은 $chul, 도착은 $dc, ${sigakMal(s.dochakSi, s.dochakBun)}까지입니다. 수단은 ${sudanIreum}입니다. 공휴일은 건너뜁니다."
    }

    fun jeonghagi(x: JeonggiSeoljeong) {
        s = x.bokje()
        d?.edit()?.remove("ulrin")?.apply()
        jeojang()
        jaeYeyak()
        Girok.namgi("jeonggi_jeonghagi", mapOf("kyeojim" to x.kyeojim, "si" to x.si, "bun" to x.bun))
    }

    fun geunalBakkugi(nal: Long, v: String?) {
        val k = ymd(nal)
        if (v != null) bakkum[k] = v else bakkum.remove(k)
        if ((d?.getString("ulrin", "") ?: "") == k && v != "swim") d?.edit()?.remove("ulrin")?.apply()
        jeojang()
        jaeYeyak()
    }

    /** 정기 호출로 걸기 — 콜 번호표가 아직 없으면 받은 뒤(6초까지 기다림) */
    fun geolgi() {
        if (NnKol.jaryo == null) {
            NnKol.batgi {}
            val main = Handler(Looper.getMainLooper())
            var beon = 0
            main.postDelayed(object : Runnable {
                override fun run() {
                    beon += 1
                    if (NnKol.jaryo == null && beon < 12) main.postDelayed(this, 500) else geolgiBaro()
                }
            }, 500)
            return
        }
        geolgiBaro()
    }

    private fun geolgiBaro() {
        val c = ChaBureugi
        val k = c.sudan(s.sudan)
        if (k == null) {
            Sori.mal(NnKol.jiyeokMal())
            return
        }
        c.hwamyeonYeolgi()
        c.geolgi(k, true)
    }

    // MARK: 말로 정하기·바꾸기

    /** "7시 30분", "8시 반", "오후 2시", "0730", "7:30" 처럼 말에서 시각을 모두 찾음 */
    fun sigakDeul(z: String): List<Pair<Int, Int>> {
        val l = ArrayList<Pair<Int, Int>>()
        val ch = z.toCharArray()
        var i = 0
        while (i < ch.size) {
            if (ch[i].isDigit()) {
                var j = i
                val su = StringBuilder()
                while (j < ch.size && ch[j].isDigit()) { su.append(ch[j]); j += 1 }
                val h0 = su.toString().toIntOrNull()
                if (j < ch.size && ch[j] == '시' && h0 != null && h0 <= 24) {
                    var h = h0
                    var m = 0
                    var k = j + 1
                    if (k < ch.size && ch[k] == '반') { m = 30; k += 1 }
                    else {
                        val su2 = StringBuilder()
                        var q = k
                        while (q < ch.size && ch[q].isDigit()) { su2.append(ch[q]); q += 1 }
                        val mm = su2.toString().toIntOrNull()
                        if (q < ch.size && ch[q] == '분' && mm != null && mm < 60) { m = mm; k = q + 1 }
                    }
                    val ap = String(ch, maxOf(0, i - 3), i - maxOf(0, i - 3))
                    if ((ap.contains("오후") || ap.contains("저녁") || ap.contains("밤")) && h < 12) h += 12
                    l.add(h to m)
                    i = k
                    continue
                }
                if (j < ch.size && ch[j] == ':' && h0 != null && h0 <= 24) {
                    var q = j + 1
                    val su2 = StringBuilder()
                    while (q < ch.size && ch[q].isDigit()) { su2.append(ch[q]); q += 1 }
                    val mm = su2.toString().toIntOrNull()
                    if (mm != null && mm < 60 && su2.length == 2) { l.add(h0 to mm); i = q; continue }
                }
                if ((su.length == 3 || su.length == 4) && h0 != null && h0 / 100 <= 23 && h0 % 100 < 60 && !z.contains("시")) {
                    l.add((h0 / 100) to (h0 % 100))
                }
                i = j
                continue
            }
            i += 1
        }
        return l
    }

    fun malCheori(alts: List<String>, z: String, dap: (String, Boolean) -> Unit): Boolean {
        val jeonggiMal = z.contains("정기호출") || z.contains("정기콜")
        val nalMal0 = z.startsWith("내일") || z.startsWith("오늘") || z.startsWith("모레") ||
            yoilIreum.drop(1).any { z.startsWith(it) || z.startsWith(it.take(1) + "요일") }
        fun geunal(): Long? {
            val oneul = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            if (z.startsWith("오늘")) return oneul.timeInMillis
            if (z.startsWith("내일")) return oneul.apply { add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
            if (z.startsWith("모레")) return oneul.apply { add(Calendar.DAY_OF_MONTH, 2) }.timeInMillis
            for (i in 1..7) {
                if (!z.startsWith(yoilIreum[i])) continue
                for (dd in 0..7) {
                    val n = (oneul.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, dd) }
                    if (n.get(Calendar.DAY_OF_WEEK) == i) return n.timeInMillis
                }
            }
            return null
        }
        // 1. 그날만 쉬기·바꾸기 — 정기 호출을 켜 두셨을 때만
        if (s.kyeojim && nalMal0) {
            val nal = geunal()
            if (nal != null) {
                val nm = nalMal(nal)
                if (z.contains("쉬어") || z.contains("쉴게") || z.contains("쉬자") || z.contains("안불러") || z.contains("부르지마")) {
                    geunalBakkugi(nal, "swim")
                    dap("${nm}은 정기 호출을 쉽니다.", false)
                    return true
                }
                if (z.contains("다시불러") || z.contains("원래대로") || z.contains("되돌려")) {
                    geunalBakkugi(nal, null)
                    dap("${nm}은 원래대로 ${sigakMal(s.si, s.bun)}에 알려 드립니다.", false)
                    return true
                }
                if (z.contains("바꿔") || z.contains("로해") || z.contains("시로") || jeonggiMal) {
                    val t = sigakDeul(z).firstOrNull()
                    if (t != null && z.contains("시")) {
                        geunalBakkugi(nal, String.format(Locale.US, "%02d%02d", t.first, t.second))
                        dap("${nm}은 ${sigakMal(t.first, t.second)}에 알려 드립니다.", false)
                        return true
                    }
                    val j = Jeulgyeo.mokrok.firstOrNull { z.contains(MalSajeon.ttuk(it.ireum)) }
                    if (j != null) {
                        geunalBakkugi(nal, "dochak:" + j.ireum)
                        dap("${nm}은 도착을 ${j.ireum}${ro(j.ireum)} 바꿉니다.", false)
                        return true
                    }
                    if (z.contains("바꿔") && !z.contains("시")) {
                        dap("그곳을 즐겨찾기에서 찾지 못했습니다. 먼저 즐겨찾기에 담아 주시면 말로 바꾸실 수 있습니다.", false)
                        return true
                    }
                }
            }
        }
        // 2. 켜기·끄기·알려 주기
        if (jeonggiMal && (z.contains("꺼") || z.contains("끄") || z.contains("멈춰"))) {
            val x = s.bokje(); x.kyeojim = false; jeonghagi(x)
            dap("정기 호출을 껐습니다. 다시 켜시려면 정기 호출 켜 줘라고 말씀해 주십시오.", false)
            return true
        }
        if (jeonggiMal && z.contains("켜")) {
            val x = s.bokje(); x.kyeojim = true; jeonghagi(x)
            dap("정기 호출을 켰습니다. " + hwaginMal(), false)
            return true
        }
        if (jeonggiMal && (z.contains("알려") || z.contains("뭐야") || z.contains("언제") || z.length <= 6)) {
            dap(if (s.kyeojim) hwaginMal() + " " + daeumMal else "정기 호출이 꺼져 있습니다. 차 부르기 화면의 정기 호출에서 정하시거나, 평일마다 아침 7시 30분에 복지콜 불러 줘처럼 말씀해 주십시오.", false)
            return true
        }
        // 3. 말로 정하기 — "평일마다 아침 7시 30분에 복지콜 불러 줘, 사무실까지 9시"
        val maeil = z.contains("평일마다") || z.contains("매일") || z.contains("날마다") || z.contains("주중")
        if ((maeil || jeonggiMal) && (z.contains("콜") || (jeonggiMal && z.contains("불러")))) {
            val t = sigakDeul(z)
            val si = t.firstOrNull()
            if (si == null) {
                dap("몇 시에 알려 드릴까요? 평일마다 아침 7시 30분에 복지콜 불러 줘처럼 시각을 함께 말씀해 주십시오.", false)
                return true
            }
            val x = s.bokje()
            x.kyeojim = true
            x.yoil = if (z.contains("매일") || z.contains("날마다")) mutableListOf(1, 2, 3, 4, 5, 6, 7) else mutableListOf(2, 3, 4, 5, 6)
            x.si = si.first; x.bun = si.second
            if (t.size > 1) { x.dochakSi = t[1].first; x.dochakBun = t[1].second }
            x.sudan = when {
                z.contains("나비") || z.contains("바우처") -> "nabi"
                z.contains("장애인콜") || z.contains("교통약자") || z.contains("이동지원") -> "jangaein"
                else -> "bokji"
            }
            Jeulgyeo.mokrok.firstOrNull { z.contains(MalSajeon.ttuk(it.ireum)) && !it.ireum.contains("집") }?.let { x.dochakIreum = it.ireum; x.dochakJuso = it.juso }
            if (x.chulbalIreum.isEmpty()) Jeulgyeo.mokrok.firstOrNull { it.ireum.contains("집") }?.let { x.chulbalIreum = it.ireum; x.chulbalJuso = it.juso }
            jeonghagi(x)
            var m = hwaginMal()
            if (x.dochakIreum.isEmpty()) m += " 도착할 곳은 차 부르기 화면의 정기 호출에서 정해 주십시오."
            dap(m, false)
            return true
        }
        return false
    }

    /** 받침에 따라 「으로·로」 */
    fun ro(w: String): String {
        val ch = w.lastOrNull() ?: return "로"
        val c = ch.code - 0xAC00
        if (c < 0 || c >= 11172) return "로"
        val j = c % 28
        return if (j == 0 || j == 8) "로" else "으로"
    }
}

/** 정기 호출 알림 때, 폰을 다시 켰을 때, 앱이 새 판으로 바뀌었을 때 — 다음 알림을 다시 걺 */
class JeonggiReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        JeonggiHochul.junbi(c)
        if (i.action == JeonggiHochul.ALLIM) JeonggiHochul.allimTtae(c) else JeonggiHochul.jaeYeyak()
    }
}

// MARK: - 차 부르기 화면

class ChaBureugiHwamyeon : Hwamyeon("차 부르기") {
    private var dareunPyeol = false
    private var geuBakkPyeol = false

    override fun chaeugi(t: GilnunActivity) {
        val c = ChaBureugi
        c.hwalseong = WeakReference(t)
        if (NnKol.jaryo == null) NnKol.batgi { if (nnBoinda(t, this)) t.dasiGeurigi() }
        if (KolAnnaeJaryo.jul.isEmpty()) KolAnnaeJaryo.bureogi { if (nnBoinda(t, this)) t.dasiGeurigi() }
        c.byeonhwa = { if (nnBoinda(t, this)) t.dasiGeurigi() }
        val sudan = c.sudanDeul()
        val an = KolAnnaeJaryo.jigeum()
        val d = c.daeum
        if (c.mureumId != null) {
            t.chojeomJul(nnMeori(t, "배차되었습니까?"))
            t.danchu("되었다 — 배차됨") { c.dapBatgi("baecha") }
            t.danchu("기다리라고 했다") { c.dapBatgi("gidarim") }
            t.danchu("안 된다고 했다") { c.dapBatgi("andoem") }
        } else if (d != null) {
            t.chojeomJul(t.danchu("${d.ireum}에 걸기 — 앞의 콜이 안 된다고 했을 때") { c.daeumGeolgi() })
            t.danchu("다른 수단에 걸지 않기") { c.daeumGeuman() }
        } else {
            t.chojeomJul(t.geul(NnKol.jiyeokMal()))
        }
        sudan.firstOrNull()?.let { k -> t.danchu("${k.ireum} 부르기") { c.geolgi(k) } }
        if (sudan.size > 1) {
            nnPyeolchigi(t, "다른 수단 펼치기 — " + sudan.drop(1).take(3).joinToString(", ") { it.ireum }, "다른 수단 접기", dareunPyeol) { dareunPyeol = !dareunPyeol }
            if (dareunPyeol) for (k in sudan.drop(1)) {
                t.danchu("${k.ireum} 부르기" + (if (k.bigo.isEmpty()) "" else " — ${k.bigo}")) { c.geolgi(k) }
            }
        }
        val j = JeonggiHochul.s
        t.danchu(if (j.kyeojim) "정기 호출 — ${JeonggiHochul.sigakMal(j.si, j.bun)}" + (if (j.dochakIreum.isEmpty()) "" else ", ${j.dochakIreum}")
            else "정기 호출 — 출퇴근처럼 정한 시각에 부르기") { t.yeolgi(JeonggiHochulHwamyeon()) }
        nnPyeolchigi(t, "그 밖에 펼치기 — 이용 조건, 등록, 성적표", "그 밖에 접기", geuBakkPyeol) { geuBakkPyeol = !geuBakkPyeol }
        if (geuBakkPyeol) {
            nnMeori(t, "이 지역 이용 조건")
            t.geul(KolAnnaeJaryo.mal(an))
            if (an != null && an.gongsik) {
                if (an.seoryu.isNotEmpty() && !an.seoryu.startsWith("확인 중")) t.geul("등록 서류: " + an.seoryu)
                if (an.paekseu.isNotEmpty()) t.geul("서류 보내는 팩스: " + an.paekseu)
                if (an.meil.isNotEmpty()) {
                    t.geul("서류 보내는 메일: " + an.meil)
                    t.danchu("복지카드를 이 메일로 보내기 — 내 서류 보관함") { c.meilBonaegi(t, an) }
                }
                if (an.unhaeng.isNotEmpty() && !an.unhaeng.startsWith("확인 중")) t.geul("운행: " + an.unhaeng)
            }
            nnMeori(t, "나의 이용 성적표")
            t.geul(c.seongjeokpyo())
            t.danchu("이름 없이 협회로 보내기 — " + (if (c.dongui == 1) "켜짐 (누르면 끕니다)" else "꺼짐 (누르면 켭니다)") + ". 지역마다 얼마나 잡히는지 알리는 근거가 됩니다") {
                c.dongui = if (c.dongui == 1) 2 else 1
                Sori.mal(if (c.dongui == 1) "협회로 이름 없이 보내기를 켰습니다. 받는 곳이 준비되는 대로 이름과 번호 없이 보냅니다." else "협회로 보내기를 껐습니다.")
                t.dasiGeurigi()
            }
            t.danchu("보조견과 함께 탑니다 — " + (if (c.bojogyeon) "켜짐 (누르면 끕니다)" else "꺼짐 (누르면 켭니다)")) {
                c.bojogyeon = !c.bojogyeon
                Sori.mal(if (c.bojogyeon) "상담원께 보조견과 함께 탄다고 말씀하시도록 넣었습니다." else "보조견은 없다고 넣었습니다.")
                t.dasiGeurigi()
            }
            t.danchu("내 서류 보관함 — 복지카드 담아 두기") { t.yeolgi(SeoryuhamHwamyeon()) }
        }
    }
}

// MARK: - 정기 호출 화면

class JeonggiHochulHwamyeon : Hwamyeon("정기 호출") {
    private var x: JeonggiSeoljeong? = null
    private var buril = ""
    private var dochakSigak = ""
    private var dochakPyeol = false
    private var jasePyeol = false

    override fun chaeugi(t: GilnunActivity) {
        val j = JeonggiHochul
        val v = x ?: j.s.bokje().also { x = it }
        t.chojeomJul(t.geul(if (j.s.kyeojim) j.daeumMal else "정기 호출이 꺼져 있습니다. 아래에서 정하고 정하기를 누르십시오."))
        t.danchu("정기 호출 — " + (if (v.kyeojim) "켜짐 (누르면 끕니다)" else "꺼짐 (누르면 켭니다)")) { v.kyeojim = !v.kyeojim; t.dasiGeurigi() }
        t.geul("부를 시각: " + JeonggiHochul.sigakMal(v.si, v.bun))
        val e1: EditText = t.ipryeok("부를 시각 바꾸기 — 예: 7시 30분, 또는 0730", false)
        e1.setText(buril)
        e1.doAfterTextChanged { buril = it?.toString() ?: "" }
        t.geul("도착: " + v.dochakIreum.ifEmpty { "정하지 않음" })
        nnPyeolchigi(t, "도착 고르기 펼치기 — 즐겨찾기에서", "도착 고르기 접기", dochakPyeol) { dochakPyeol = !dochakPyeol }
        if (dochakPyeol) {
            if (Jeulgyeo.mokrok.isEmpty()) t.geul("도착할 곳은 길 찾기의 즐겨찾기에 먼저 담아 두시면 여기서 고르실 수 있습니다.")
            for (h in Jeulgyeo.mokrok) {
                t.danchu(h.ireum + (if (h.ireum == v.dochakIreum) " — 골랐음" else " — 도착으로 고르기")) {
                    v.dochakIreum = h.ireum; v.dochakJuso = h.juso; dochakPyeol = false
                    Sori.mal("도착을 ${h.ireum}${JeonggiHochul.ro(h.ireum)} 골랐습니다. 정하기를 눌러야 저장됩니다.")
                    t.dasiGeurigi()
                }
            }
            t.danchu("도착 정하지 않음") { v.dochakIreum = ""; v.dochakJuso = ""; dochakPyeol = false; t.dasiGeurigi() }
        }
        t.geul("도착 희망 시각: " + JeonggiHochul.sigakMal(v.dochakSi, v.dochakBun))
        val e2: EditText = t.ipryeok("도착 희망 시각 바꾸기 — 예: 9시, 또는 0900", false)
        e2.setText(dochakSigak)
        e2.doAfterTextChanged { dochakSigak = it?.toString() ?: "" }
        t.danchu("정하기 — 저장하고 알림 걸기") { jeonghagi(t, v) }
        if (v.kyeojim && !JeonggiHochul.jeonghwakHeorak(t)) {
            t.danchu("정확한 시각에 알리게 허락하기 — 허락 전에는 몇 분 늦을 수 있습니다") {
                try {
                    if (Build.VERSION.SDK_INT >= 31) t.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + t.packageName)))
                    Sori.mal("알람 및 리마인더 허용을 켜신 뒤 뒤로 돌아오십시오.")
                } catch (e: Exception) { Sori.mal("설정 화면을 열지 못했습니다.") }
            }
        }
        if (ContextCompat.checkSelfPermission(t, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            t.danchu("알림을 한 번 두드리면 바로 걸리게 — 전화 걸기 허락하기") {
                Sori.mal("전화 걸기를 허락하시면 알림이나 부르기 단추를 누르셨을 때 통화 단추를 다시 누르지 않아도 바로 걸립니다.")
                ActivityCompat.requestPermissions(t, arrayOf(Manifest.permission.CALL_PHONE), JeonggiHochul.HEORAK_BEON)
            }
        }
        nnPyeolchigi(t, "자세히 펼치기 — 요일, 출발, 수단, 보조견, 내일만 바꾸기", "자세히 접기", jasePyeol) { jasePyeol = !jasePyeol }
        if (jasePyeol) {
            for (y in listOf(2, 3, 4, 5, 6, 7, 1)) {
                val ken = v.yoil.contains(y)
                t.danchu(JeonggiHochul.yoilIreum[y] + " — " + (if (ken) "부름 (누르면 뺍니다)" else "안 부름 (누르면 넣습니다)")) {
                    if (ken) v.yoil.remove(y) else v.yoil.add(y)
                    t.dasiGeurigi()
                }
            }
            t.geul("출발: " + v.chulbalIreum.ifEmpty { "부를 때 계신 곳" })
            t.danchu("출발 — 부를 때 계신 곳" + (if (v.chulbalIreum.isEmpty()) " (골랐음)" else "")) { v.chulbalIreum = ""; v.chulbalJuso = ""; t.dasiGeurigi() }
            for (h in Jeulgyeo.mokrok) {
                t.danchu("출발 — ${h.ireum}" + (if (h.ireum == v.chulbalIreum) " (골랐음)" else "")) { v.chulbalIreum = h.ireum; v.chulbalJuso = h.juso; t.dasiGeurigi() }
            }
            for ((k, nm) in listOf("bokji" to "복지콜", "jangaein" to "교통약자 콜", "nabi" to "나비콜·바우처택시")) {
                t.danchu("수단 — $nm" + (if (v.sudan == k) " (골랐음)" else "")) { v.sudan = k; t.dasiGeurigi() }
            }
            t.danchu("보조견과 함께 탑니다 — " + (if (v.bojogyeon) "켜짐 (누르면 끕니다)" else "꺼짐 (누르면 켭니다)")) { v.bojogyeon = !v.bojogyeon; t.dasiGeurigi() }
            t.danchu("내일 하루 쉬기") {
                val n = System.currentTimeMillis() + 86_400_000L
                j.geunalBakkugi(n, "swim")
                Sori.mal("${JeonggiHochul.nalMal(n)}은 정기 호출을 쉽니다.")
                t.dasiGeurigi()
            }
            t.danchu("내일 원래대로") {
                val n = System.currentTimeMillis() + 86_400_000L
                j.geunalBakkugi(n, null)
                Sori.mal("${JeonggiHochul.nalMal(n)}은 원래대로 알려 드립니다.")
                t.dasiGeurigi()
            }
            t.danchu("지금 정기 호출로 걸어 보기") { j.geolgi() }
            t.geul("말로도 됩니다. 평일마다 아침 7시 30분에 복지콜 불러 줘, 사무실까지 9시. 내일은 쉬어. 내일은 8시로 바꿔. 금요일은 병원으로.")
        }
    }

    private fun jeonghagi(t: GilnunActivity, v: JeonggiSeoljeong) {
        if (buril.isNotBlank()) {
            val s = JeonggiHochul.sigakDeul(MalSajeon.ttuk(buril)).firstOrNull()
            if (s == null) { Sori.mal("부를 시각을 알아보지 못했습니다. 7시 30분, 또는 0730처럼 넣어 주십시오."); return }
            v.si = s.first; v.bun = s.second
        }
        if (dochakSigak.isNotBlank()) {
            val s = JeonggiHochul.sigakDeul(MalSajeon.ttuk(dochakSigak)).firstOrNull()
            if (s == null) { Sori.mal("도착 희망 시각을 알아보지 못했습니다. 9시, 또는 0900처럼 넣어 주십시오."); return }
            v.dochakSi = s.first; v.dochakBun = s.second
        }
        if (v.yoil.isEmpty()) v.yoil = mutableListOf(2, 3, 4, 5, 6)
        JeonggiHochul.jeonghagi(v)
        x = JeonggiHochul.s.bokje()
        buril = ""; dochakSigak = ""
        Sori.mal(if (v.kyeojim) JeonggiHochul.hwaginMal() + " " + JeonggiHochul.daeumMal else "정기 호출을 껐습니다.")
        if (v.kyeojim && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(t, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(t, arrayOf(Manifest.permission.POST_NOTIFICATIONS), JeonggiHochul.HEORAK_BEON)
        }
        t.dasiGeurigi()
    }
}
