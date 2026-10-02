// 안드로이드 길눈 — 알림(공지)과 긴급 공지(2.7.0 묶음 b6_nanum, 아이폰 길눈 Nanum.swift GongjiEngine 2.12.0·SeoljeongDeo.swift 알림 화면과 같음)
// gongji.php a=mok app=gilnun — 웹·아이폰과 같은 자료 창고. 길눈이 켜져 있는 동안 15분마다, 앱으로 돌아올 때마다 살핌.
// 길눈이 닫혀 있어도 안드로이드 일꾼(WorkManager, 15분 간격 — 언제 깨울지는 안드로이드가 정함)이 살펴 새 긴급 공지면 폰 알림(아이폰 2.11.2 BGAppRefresh 와 같은 뜻).
// 읽지 않은 긴급 공지는 길 찾기 첫 화면 맨 위에 한 줄(gingeupJul) — 두드리면 전문을 읽어 드리고 내림. 읽은 공지·알린 공지는 300개까지 적어 두어 다시 뜨지 않게.
package kr.or.ada.app.gilnun

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.widget.Button
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONArray
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class GongjiGeul(val id: String, val jemok: String, val naeyong: String, val gingeup: Boolean, val nalMal: String) {
    val julMal: String get() = (if (gingeup) "긴급 공지 · " else "") + jemok + (if (nalMal.isEmpty()) "" else " · $nalMal")
}

object GongjiEngine {
    private val main = Handler(Looper.getMainLooper())
    private var ctx: Context? = null

    var mok: List<GongjiGeul> = emptyList()
        private set
    /** 아직 읽지 않은 긴급 공지 — 길 찾기 첫 화면 맨 위 */
    var gingeupSae: GongjiGeul? = null
        private set
    var batneunJung = false
        private set
    var mot = false
        private set
    /** 공지가 바뀌면 — 길 찾기·설정 첫 화면을 다시 그리게(GilnunActivity 가 채움) */
    var byeonhwa: (() -> Unit)? = null
    /** 알림 화면이 보일 때 그 화면이 채움 */
    internal var hwamyeonByeonhwa: (() -> Unit)? = null

    private var sijakham = false
    private val kkeutDeul = ArrayList<(Boolean) -> Unit>()
    private var majimakBatgi = 0L
    private var apHwamyeon = 0          // 앞에 떠 있는 화면 수 — 0 이면 길눈이 뒤에 있음(아이폰 applicationState)
    private var heorakDwi: ((Boolean) -> Unit)? = null
    private var heorakChangTteum = false

    const val HEORAK_BEON = 21           // 폰 알림 허락을 여쭐 때 쓰는 번호(GilnunActivity.onRequestPermissionsResult 에서 가름)
    private const val CHANNEL = "gilnun_gongji"
    private const val IL_IREUM = "gilnun_gongji"

    private val sigye = object : Runnable {
        override fun run() { ctx?.let { batgi(it) }; main.postDelayed(this, 900_000L) }
    }

    /** 길눈을 켤 때 한 번 — 받아 오고, 15분마다, 앱으로 돌아올 때마다. 뒤 일꾼도 맡겨 둠 */
    fun sijak(c: Context) {
        if (sijakham) return
        sijakham = true
        val ac = c.applicationContext
        ctx = ac
        if (c is Activity) apHwamyeon = 1
        (ac as? Application)?.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityResumed(a: Activity) {
                apHwamyeon = 1
                if (heorakDwi != null && heorakChangTteum) heorakGyeolgwa(a)
                if (System.currentTimeMillis() - majimakBatgi > 60_000L) batgi(a)
            }
            override fun onActivityPaused(a: Activity) {
                apHwamyeon = 0
                if (heorakDwi != null) heorakChangTteum = true
            }
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
        main.removeCallbacks(sigye)
        main.post(sigye)
        yeyak(ac)
    }

    /** 뒤 일꾼 맡기기 — 15분 간격, 인터넷이 있을 때(이미 맡겼으면 그대로) */
    fun yeyak(c: Context) {
        try {
            val r = PeriodicWorkRequestBuilder<GongjiWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(c.applicationContext).enqueueUniquePeriodicWork(IL_IREUM, ExistingPeriodicWorkPolicy.KEEP, r)
        } catch (e: Exception) {
            Girok.namgi("gongji_yeyak_oryu", mapOf("e" to (e.message ?: "").take(80)))
        }
    }

    /** 받아 오기 — 받는 중이면 끝날 때 함께 알림(kkeut 은 화면 줄에서) */
    fun batgi(c: Context, kkeut: ((Boolean) -> Unit)? = null) {
        main.post {
            if (ctx == null) ctx = c.applicationContext
            if (kkeut != null) kkeutDeul.add(kkeut)
            if (batneunJung) return@post
            batneunJung = true
            majimakBatgi = System.currentTimeMillis()
            NanumNas.get("gongji.php", listOf("a" to "mok", "app" to "gilnun")) { j ->
                batneunJung = false
                val kd = ArrayList(kkeutDeul)
                kkeutDeul.clear()
                if (j == null || !NanumNas.cham(j, "ok")) {
                    mot = true
                    kd.forEach { it(false) }
                    alligi()
                    return@get
                }
                mot = false
                mok = NanumNas.julDeul(j, "gongji").mapNotNull { g ->
                    val id = NanumNas.gul(g, "id")
                    if (id.isEmpty() || g.optBoolean("garim", false)) null
                    else GongjiGeul(id, NanumNas.gul(g, "jemok"), NanumNas.gul(g, "naeyong"),
                        NanumNas.gul(g, "deunggeup") == "gingeup", NanumNas.gul(g, "nalMal"))
                }
                gingeupBoda(c.applicationContext)
                kd.forEach { it(true) }
                alligi()
            }
        }
    }

    private fun alligi() {
        byeonhwa?.invoke()
        hwamyeonByeonhwa?.invoke()
    }

    // MARK: 읽음·알림 적어 두기(적은 차례대로, 넘치면 가장 오래된 것부터 지움)

    private fun moum(c: Context, ki: String): List<String> {
        val s = NnJeojang.d(c).getString(ki, "") ?: ""
        if (s.isEmpty()) return emptyList()
        return try {
            val a = JSONArray(s)
            (0 until a.length()).map { a.optString(it) }
        } catch (e: Exception) { emptyList() }
    }

    private fun deohagi(c: Context, ki: String, id: String) {
        val l = ArrayList(moum(c, ki))
        if (l.contains(id)) return
        l.add(id)
        while (l.size > 300) l.removeAt(0)
        NnJeojang.d(c).edit().putString(ki, JSONArray(l).toString()).apply()
    }

    private fun gingeupBoda(c: Context) {
        val il = moum(c, "gj.gongjiIlgeum").toSet()
        gingeupSae = mok.firstOrNull { it.gingeup && it.id !in il }
        val g = gingeupSae ?: return
        if (g.id in moum(c, "gj.gongjiAllin")) return
        deohagi(c, "gj.gongjiAllin", g.id)
        if (apHwamyeon > 0) {
            // 말소리를 꺼 두셨으면 Sori 가 톡백으로 한 번 알림
            Sori.mal("긴급 공지. ${g.jemok}. 길 찾기 첫 화면 맨 위에서 들으실 수 있습니다.")
        } else {
            pongAllim(c, g)
        }
        Girok.namgi("gongji_gingeup")
    }

    @android.annotation.SuppressLint("MissingPermission")   // 33 이상은 바로 위에서 허락을 살핌
    private fun pongAllim(c: Context, g: GongjiGeul) {
        try {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "길눈 긴급 공지", NotificationManager.IMPORTANCE_HIGH))
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val yeol = c.packageManager.getLaunchIntentForPackage(c.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val b = NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("길눈 긴급 공지")
                .setContentText(g.jemok)
                .setStyle(NotificationCompat.BigTextStyle().bigText(g.jemok))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
            if (yeol != null) {
                b.setContentIntent(PendingIntent.getActivity(c, 2101, yeol, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            NotificationManagerCompat.from(c).notify(("gongji-" + g.id).hashCode(), b.build())
        } catch (e: Exception) {
            Girok.namgi("gongji_allim_oryu", mapOf("e" to (e.message ?: "").take(80)))
        }
    }

    /** 읽음 — 긴급 공지는 읽으면 첫 화면에서 내려감 */
    fun ilgeumPyosi(c: Context, g: GongjiGeul) {
        deohagi(c, "gj.gongjiIlgeum", g.id)
        val s = moum(c, "gj.gongjiIlgeum").toSet()
        if (gingeupSae?.id == g.id) gingeupSae = mok.firstOrNull { it.gingeup && it.id !in s }
    }

    fun ilgeotna(c: Context, g: GongjiGeul): Boolean = g.id in moum(c, "gj.gongjiIlgeum")

    /** 길 찾기 첫 화면 맨 위 — 읽지 않은 긴급 공지가 있으면 한 줄(안내와 단추를 한 자리에). 없으면 아무것도 그리지 않음 */
    fun gingeupJul(t: GilnunActivity): Button? {
        val g = gingeupSae ?: return null
        return t.danchu("긴급 공지 — ${g.jemok}. 두드리면 읽어 드리고 내립니다") {
            // 길눈 말소리를 꺼 두셨어도 긴급 공지는 톡백으로 읽어 드림(Sori 가 가름)
            Sori.mal("긴급 공지. ${g.jemok}. ${g.naeyong}")
            ilgeumPyosi(t, g)
            t.dasiGeurigi()
        }
    }

    // MARK: 폰 알림 허락 — 알림 화면의 단추에서만 여쭘

    fun allimDoenda(c: Context): Boolean = NotificationManagerCompat.from(c).areNotificationsEnabled()

    fun heorakMutgi(a: Activity, f: (Boolean) -> Unit) {
        if (allimDoenda(a)) { f(true); return }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(a, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            heorakDwi = f
            heorakChangTteum = false
            ActivityCompat.requestPermissions(a, arrayOf(Manifest.permission.POST_NOTIFICATIONS), HEORAK_BEON)
            // 이미 거절하셔서 창이 뜨지 않으면 곧바로 알림 설정 화면으로
            main.postDelayed({ if (heorakDwi != null && !heorakChangTteum) heorakGyeolgwa(a) }, 1500)
            return
        }
        f(false)
        seoljeongYeolgi(a)
    }

    /** 허락 창이 닫혔을 때(GilnunActivity 의 HEORAK_BEON 갈래, 또는 화면이 돌아올 때) */
    fun heorakGyeolgwa(a: Activity) {
        val f = heorakDwi ?: return
        heorakDwi = null
        heorakChangTteum = false
        val ok = allimDoenda(a)
        f(ok)
        if (!ok) seoljeongYeolgi(a)
    }

    private fun seoljeongYeolgi(a: Activity) {
        try {
            val i = if (Build.VERSION.SDK_INT >= 26) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, a.packageName)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.packageName))
            }
            a.startActivity(i)
        } catch (e: Exception) {
            Girok.namgi("gongji_seoljeong_oryu")
        }
    }
}

/** 뒤 일꾼 — 길눈이 닫혀 있어도 안드로이드가 깨우면 공지를 받아, 새 긴급 공지면 폰 알림 */
class GongjiWorker(c: Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result {
        val l = CountDownLatch(1)
        GongjiEngine.batgi(applicationContext) { l.countDown() }
        l.await(25, TimeUnit.SECONDS)
        Girok.namgi("gongji_dwi")
        return Result.success()
    }
}

// MARK: 알림 화면

class GongjiHwamyeon : Hwamyeon("알림") {
    private var gaengsinHal = true    // 처음과, 공지를 읽고 돌아왔을 때 다시 받음(아이폰 onAppear)
    private var naBatneun = false
    private var heorakMal = ""
    private var chojeomHal = true
    private var apCheot = ""
    private val m5 = NnMok5()

    override fun chaeugi(t: GilnunActivity) {
        val e = GongjiEngine
        GongjiEngine.hwamyeonByeonhwa = {
            if (nnBoinda(t, this)) {
                val c = e.mok.firstOrNull()?.id ?: ""
                if (c != apCheot) { apCheot = c; m5.saeMok() }
                t.dasiGeurigi()
            }
        }
        if (gaengsinHal) {
            gaengsinHal = false
            chojeomHal = true
            naBatneun = true
            e.batgi(t) { naBatneun = false }
        }
        t.geul("길눈을 만드는 곳에서 드리는 알림입니다.")
        if (e.mok.isEmpty()) {
            val badneun = e.batneunJung || naBatneun
            val v = t.geul(if (badneun) "알림을 받아 오는 중입니다." else if (e.mot) "알림을 받아 오지 못했습니다. 신호를 확인해 주십시오." else "지금 올라온 알림이 없습니다.", true)
            if (chojeomHal && !badneun) { chojeomHal = false; t.chojeomOmgigi(v) }
        } else {
            apCheot = e.mok.first().id
            if (chojeomHal && !naBatneun) { chojeomHal = false; m5.chojeomHal = true }
            m5.geurigi(t, e.mok) { g ->
                t.danchu(g.julMal + (if (e.ilgeotna(t, g)) "" else " · 새 알림")) {
                    gaengsinHal = true
                    t.yeolgi(GongjiBonHwamyeon(g))
                }
            }
        }
        val hb = t.danchu("긴급 공지를 폰 알림으로 받기") { }
        val hm = t.geul(heorakMal)
        hm.visibility = if (heorakMal.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        hb.setOnClickListener {
            GongjiEngine.heorakMutgi(t) { ok ->
                heorakMal = if (ok) "폰 알림을 켰습니다. 길눈이 닫혀 있거나 화면이 잠겨 있어도 긴급 공지가 오면 폰 알림으로 알려 드립니다."
                else "폰 알림이 꺼져 있습니다. 폰 설정의 앱, 길눈, 알림에서 켜 주십시오. 알림 설정 화면을 열어 드립니다."
                Sori.mal(heorakMal)
                if (nnBoinda(t, this)) { hm.text = heorakMal; hm.visibility = android.view.View.VISIBLE }
            }
        }
    }
}

class GongjiBonHwamyeon(private val g: GongjiGeul) : Hwamyeon("알림") {
    private var cheotBoim = true
    override fun chaeugi(t: GilnunActivity) {
        val m = nnMeori(t, (if (g.gingeup) "긴급 공지 · " else "") + g.jemok)
        m.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f)
        if (cheotBoim) {
            cheotBoim = false
            t.chojeomJul(m)   // 매번 제목으로 커서(아이폰 2.12.1)
            GongjiEngine.ilgeumPyosi(t, g)
        }
        if (g.nalMal.isNotEmpty()) t.geul(g.nalMal).setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        for (p in g.naeyong.split("\n").filter { it.isNotBlank() }) t.geul(p, true)
        t.danchu("알림 목록으로") { t.dwiro() }
    }
}
