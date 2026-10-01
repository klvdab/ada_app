// 안드로이드 자봉 — 첫 화면과 점지도 그리기 화면 (2.3.0, 빌드 261001-A2, 대표님 지시 2026-10-01)
// 점지도 그리기는 웹을 걷어 내고 속까지 앱으로. 등록·게시판 같은 나머지는 다음 판까지 웹 자봉(MainActivity)으로 이어 줌.
// 화면 원칙: 뒤로 단추는 위에 하나, 폰의 뒤로 동작도 앞 화면으로(앱 밖으로 튀지 않음), 화면이 바뀌면 커서를 첫 줄로,
// 자주 쓰는 것만 겉에 두고 나머지는 펼치기 안에, 새 기능마다 도움말.
package kr.or.ada.app.jabong

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kr.or.ada.app.MainActivity
import kr.or.ada.app.gilnun.Girok
import kr.or.ada.app.gilnun.MalGeup
import kr.or.ada.app.gilnun.MomSensor
import kr.or.ada.app.gilnun.Seoljeong
import kr.or.ada.app.gilnun.Sori
import kr.or.ada.app.gilnun.Wichi
import kotlin.math.roundToInt

/** 판번호와 고친 기록 — 고칠 때마다 맨 위에 더함 */
object JabongPan {
    const val pan = "2.3.0"
    const val bild = "261001-A2"
    val girok = listOf(
        Triple("2.3.0", "261001-A2", listOf(
            "점지도 그리기를 속까지 앱으로(대표님 지시 — 아이폰만이 아니라 안드로이드도, 가장 중요한 것은 걸으면서 찍는 점지도)",
            "몸 센서 극대화: 선형 가속도·중력·자이로·회전 벡터·걸음 감지·기압 센서를 1초에 50번 읽어 걸음마다 한 줄. 아이폰과 같은 센서 기록 규격 1.0",
            "꺾이셨습니까는 자이로 각도로, 계단입니까는 기압 높이로 먼저 여쭘. 네·아니오 단추로 답함",
            "1분마다·표시마다 저절로 저장, 앱이 꺼졌다 켜지면 멈춤으로 되살림. 화면이 꺼져도 알림 칸의 자봉이 붙들어 이어 그림",
            "보폭 재기(10미터 걸어 재기, 센티미터로 넣기). 등록·게시판 등 나머지는 다음 판까지 웹 자봉으로 이어 감. 도움말 더함"
        ))
    )
}

class JabongActivity : AppCompatActivity() {
    private lateinit var nae: LinearLayout
    private lateinit var seuk: ScrollView
    private var cheotJul: View? = null
    private val gil = ArrayList<String>()       // 화면 이름 쌓기: cheot, geurigi, bopok, pan, doumal
    private var pyeolchim = false
    private var bopokSijak = -1

    companion object { val NAM: Int = Color.rgb(18, 52, 110) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Seoljeong.sijak(this)
        Girok.sijak(this)
        Sori.sijak(this)
        Wichi.sijak(this)
        Geurigi.junbi(this)
        Girok.namgi("jb_app_sijak", mapOf("pan" to JabongPan.pan, "bild" to JabongPan.bild, "android" to Build.VERSION.SDK_INT))
        seuk = ScrollView(this).apply { setBackgroundColor(Color.WHITE) }
        nae = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(24)) }
        seuk.addView(nae, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(seuk)
        Sori.tokbaek = { t -> seuk.announceForAccessibility(t) }
        Geurigi.bakkwim = { runOnUiThread { if (gil.lastOrNull() == "geurigi") boyeojugi(false) } }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { dwiro() } })
        gil.add("cheot")
        if (Geurigi.sangtae != Geurigi.Sangtae.SWIM) gil.add("geurigi")
        boyeojugi()
        heorakCheong()
    }

    override fun onResume() { super.onResume(); Wichi.wiseongDolligi() }
    override fun onPause() { super.onPause(); Girok.jeojang(); Geurigi.jeojang() }

    private fun heorakCheong() {
        val p = arrayListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 29) p.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
        val an = p.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (an.isNotEmpty()) ActivityCompat.requestPermissions(this, an.toTypedArray(), 7)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Wichi.wiseongDolligi()
        if (!Wichi.heorakItda) Sori.mal("점지도를 그리려면 위치 허락이 필요합니다. 폰 설정의 앱, 자봉, 권한에서 위치를 허용해 주십시오.")
        if (!Wichi.georeumHeorak) Sori.mal("걸음을 세려면 신체 활동 허락이 필요합니다. 폰 설정의 앱, 자봉, 권한에서 신체 활동을 허용해 주십시오.")
    }

    private fun yeolgi(h: String) { gil.add(h); pyeolchim = false; boyeojugi() }
    private fun dwiro() {
        if (gil.size > 1) { gil.removeAt(gil.size - 1); boyeojugi() }
        else Sori.mal("첫 화면입니다.", MalGeup.JEONGBO)
    }

    private fun boyeojugi(chojeom: Boolean = true) {
        nae.removeAllViews(); cheotJul = null
        val h = gil.last()
        if (gil.size > 1) danchu("뒤로") { dwiro() }
        when (h) {
            "cheot" -> cheot()
            "geurigi" -> geurigi()
            "bopok" -> bopok()
            "pan" -> panHwamyeon()
            "doumal" -> doumal()
        }
        if (chojeom) cheotJul?.let { v -> v.post { v.requestFocus(); v.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED) } }
    }

    // MARK: 화면들

    private fun cheot() {
        jemok("자봉 — 점지도 그리기")
        val sang = when (Geurigi.sangtae) { Geurigi.Sangtae.GEOREUM -> " — 그리는 중"; Geurigi.Sangtae.MEOMCHUM -> " — 잠깐 멈춤, 이어 그리기"; else -> "" }
        danchu("점지도 그리기$sang") { yeolgi("geurigi") }
        danchu(if (Seoljeong.bopokJaem) "내 보폭 다시 재기 — 지금 ${(Seoljeong.bopok * 100).roundToInt()}센티미터" else "내 보폭 재기 — 그리기 전에 꼭") { yeolgi("bopok") }
        danchu("등록·게시판·나눔 — 웹 자봉으로") { startActivity(Intent(this, MainActivity::class.java)) }
        danchu("도움말") { yeolgi("doumal") }
        danchu("자봉 ${JabongPan.pan}판 (빌드 ${JabongPan.bild}) — 무엇이 바뀌었는지") { yeolgi("pan") }
    }

    private fun geurigi() {
        jemok("점지도 그리기")
        val m = Geurigi.mureum
        if (m != null) {
            geul(m.mal, true)
            danchu("네 — ${m.ne}") { Geurigi.dap(true); boyeojugi() }
            danchu("아니오") { Geurigi.dap(false); boyeojugi() }
        }
        when (Geurigi.sangtae) {
            Geurigi.Sangtae.SWIM -> {
                danchu("걷기 시작") { Geurigi.sijak(); boyeojugi() }
                geul(Geurigi.sangtaeMal())
            }
            Geurigi.Sangtae.GEOREUM -> {
                for (n in Geurigi.JAJU) danchu(n) { Geurigi.pyosi(n); boyeojugi(false) }
                danchu(if (pyeolchim) "다른 표시 접기" else "다른 표시 펼치기") { pyeolchim = !pyeolchim; boyeojugi(false) }
                if (pyeolchim) for (n in Geurigi.MARKS.filter { it !in Geurigi.JAJU }) danchu(n) { Geurigi.pyosi(n); boyeojugi(false) }
                danchu("지금 상태 듣기") { Sori.mal(Geurigi.sangtaeMal()) }
                danchu("잠깐 멈춤") { Geurigi.jamkkan(); boyeojugi() }
                danchu("다 걸었습니다") { Geurigi.kkeut(); boyeojugi() }
            }
            Geurigi.Sangtae.MEOMCHUM -> {
                danchu("다시 걷기") { Geurigi.dasiGeotgi(); boyeojugi() }
                danchu("다 걸었습니다") { Geurigi.kkeut(); boyeojugi() }
                danchu("지금 상태 듣기") { Sori.mal(Geurigi.sangtaeMal()) }
            }
        }
        if (Geurigi.allim.isNotEmpty()) geul(Geurigi.allim)
    }

    private fun bopok() {
        jemok("내 보폭 재기")
        geul("두 가지 가운데 고르십시오. 10미터 걸어 재기는 줄자나 바닥 표시로 10미터를 미리 재 둔 곳에서 합니다.")
        if (bopokSijak < 0) {
            danchu("10미터 걸어 재기 — 출발점에 서서 누르기") {
                bopokSijak = MomSensor.georeumSu
                if (!MomSensor.dollyeo) MomSensor.kyeogi(this)
                bopokSijak = MomSensor.georeumSu
                Sori.mal("평소 걸음으로 10미터를 걸으시고, 도착하시면 다 걸었습니다를 눌러 주십시오.")
                boyeojugi()
            }
        } else {
            danchu("10미터 다 걸었습니다") {
                val n = MomSensor.georeumSu - bopokSijak
                if (Geurigi.sangtae == Geurigi.Sangtae.SWIM) MomSensor.kkeugi()
                bopokSijak = -1
                if (n < 8 || n > 30) { Sori.mal("${n}걸음으로 셌습니다. 10미터 걸음으로 보기 어렵습니다. 다시 재 주십시오."); boyeojugi(); return@danchu }
                Seoljeong.bopok = 10.0 / n
                Girok.namgi("jb_bopok", mapOf("georeum" to n, "bopok" to Seoljeong.bopok))
                Sori.mal("${n}걸음이었습니다. 보폭을 ${(Seoljeong.bopok * 100).roundToInt()}센티미터로 담았습니다.")
                boyeojugi()
            }
            danchu("그만두기") { bopokSijak = -1; if (Geurigi.sangtae == Geurigi.Sangtae.SWIM) MomSensor.kkeugi(); boyeojugi() }
        }
        val e = ipryeok("보폭을 센티미터로 넣기(예: 65)")
        danchu("넣은 값으로 담기") {
            val v = e.text.toString().trim().toDoubleOrNull()
            if (v == null || v < 30 || v > 110) { Sori.mal("30에서 110 사이의 센티미터로 넣어 주십시오."); return@danchu }
            Seoljeong.bopok = v / 100.0
            Sori.mal("보폭을 ${v.roundToInt()}센티미터로 담았습니다.")
            boyeojugi()
        }
    }

    private fun panHwamyeon() {
        jemok("판 기록")
        for ((p, b, naeyong) in JabongPan.girok) {
            geul("$p 판, 빌드 $b", true)
            for (t in naeyong) geul(t)
        }
    }

    private fun doumal() {
        jemok("도움말")
        geul("점지도 그리기", true)
        geul("첫 화면의 점지도 그리기에서 엽니다. 보폭을 먼저 재 두셔야 시작할 수 있습니다. 걷기 시작을 누르시면 걸음 수, 방향, 위성 자리, 높이를 1초마다 폰 안에 기록합니다. 화면이 꺼져도 알림 칸의 자봉이 붙들어 이어 그립니다. 꺾이는 곳, 계단, 건널목, 문에 닿는 순간 그 표시를 누르십시오. 다른 표시는 다른 표시 펼치기 안에 있습니다. 다 걸었습니다를 누르시면 걸음과 거리, 표시 수를 말씀드리고 폰에 담아 둡니다.")
        geul("몸 센서 — 걸음과 방향을 더 정확하게", true)
        geul("그리시는 동안 폰의 선형 가속도, 중력, 자이로, 회전 벡터, 걸음 감지, 기압 센서를 1초에 50번 읽습니다. 발이 땅에 닿을 때마다 한 걸음을 바로 세고, 몸이 몇 도 돌았는지 자이로로 재어 쇠붙이나 건물 옆에서도 방향이 틀어지지 않게 합니다. 걸음마다 시각, 방향, 돈 각도, 위아래 충격, 높이를 한 줄씩 남깁니다. 기록 모양은 아이폰 자봉과 똑같아 어느 폰으로 그린 점지도든 함께 쓰입니다. 다 걸었습니다에서 두 가지로 센 걸음을 견주어 차이가 크면 알려 드립니다.")
        geul("폰이 먼저 여쭘", true)
        geul("몸이 크게 돌면 꺾이셨습니까, 높이가 바뀌면 계단입니까, 계단이 끝났습니까를 여쭙습니다. 네 단추를 누르셔야 표시가 됩니다. 20초 안에 답하지 않으시면 물음은 거둡니다.")
        geul("보폭 재기", true)
        geul("10미터를 미리 재 둔 곳에서 10미터 걸어 재기를 누르고 평소 걸음으로 걸은 뒤 다 걸었습니다를 누르시면 보폭을 셉니다. 아시는 보폭을 센티미터로 넣으셔도 됩니다.")
        geul("저절로 저장", true)
        geul("1분마다, 표시를 남길 때마다 그리던 길을 저장합니다. 앱이 꺼졌다 켜지면 그리던 길을 잠깐 멈춤으로 되살려 이어 그리실 수 있습니다.")
        geul("웹 자봉", true)
        geul("등록, 그려주세요 게시판, 걸음 나눔, 물품 나눔은 다음 판에 앱으로 옮겨 오기 전까지 웹 자봉으로 이어 드립니다.")
    }

    // MARK: 줄 만들기

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun deohagi(v: View) {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(10)
        nae.addView(v, lp)
        if (cheotJul == null) cheotJul = v
    }
    private fun jemok(t: String) {
        deohagi(TextView(this).apply {
            text = t; setTextColor(Color.BLACK); setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f); setTypeface(typeface, Typeface.BOLD)
            if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
            isFocusable = true
        })
    }
    private fun geul(t: String, keuge: Boolean = false) {
        deohagi(TextView(this).apply { text = t; setTextColor(Color.BLACK); setTextSize(TypedValue.COMPLEX_UNIT_SP, if (keuge) 21f else 18f); isFocusable = true })
    }
    private fun danchu(t: String, f: () -> Unit) {
        deohagi(Button(this).apply {
            text = t; isAllCaps = false; gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTextColor(Color.WHITE); setBackgroundColor(NAM); setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            minHeight = dp(64); setPadding(dp(18), dp(8), dp(18), dp(8))
            setOnClickListener { f() }
        })
    }
    private fun ipryeok(t: String): EditText {
        val e = EditText(this).apply {
            hint = t; contentDescription = t; setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; minHeight = dp(60)
        }
        deohagi(e)
        return e
    }
}

/** 화면이 꺼져도 점지도 그리기를 붙들어 두는 알림 칸의 자봉 — 위치와 몸 센서가 잠들지 않게 */
class JabongService : Service() {
    private var jamsoe: PowerManager.WakeLock? = null
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Seoljeong.sijak(this)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("jabong", "자봉 점지도 그리기", NotificationManager.IMPORTANCE_LOW))
        val yeolgi = PendingIntent.getActivity(this, 0, Intent(this, JabongActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "jabong") else Notification.Builder(this)
        val n = b.setContentTitle("자봉").setContentText("점지도를 그리는 중입니다. 화면이 꺼져도 이어 그립니다")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentIntent(yeolgi).setOngoing(true).build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) else startForeground(2, n)
        } catch (e: Exception) {
            Girok.namgi("jb_service", mapOf("ok" to false, "e" to (e.message ?: "")))
            stopSelf(); return START_NOT_STICKY
        }
        if (jamsoe == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            jamsoe = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jabong:geurigi").apply { acquire(3 * 60 * 60 * 1000L) }
        }
        Wichi.sijak(this)
        return START_STICKY
    }

    override fun onDestroy() {
        try { jamsoe?.takeIf { it.isHeld }?.release() } catch (e: Exception) {}
        jamsoe = null
        super.onDestroy()
    }

    companion object {
        fun kyeogi(c: Context) {
            if (!Wichi.heorakItda) return
            try {
                val i = Intent(c, JabongService::class.java)
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
            } catch (e: Exception) { Girok.namgi("jb_service", mapOf("ok" to false, "kyeogi" to (e.message ?: ""))) }
        }
        fun kkeugi(c: Context?) { c?.let { try { it.stopService(Intent(it, JabongService::class.java)) } catch (e: Exception) {} } }
    }
}
