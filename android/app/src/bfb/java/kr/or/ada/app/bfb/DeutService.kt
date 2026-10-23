// LVD 방송(듣는 앱) — 지금 현장영상해설 듣기 일꾼 (2.2.0판, 빌드 261018-L3, 방클, 이사장님 승인 「1」 — LVD 방송 정리안)
// 2.2.0: 단추 하나로 — 행사장이면 현장 방송, 아니면 LVD 방송 중계(lvd.ada.or.kr:2053 /moklok)에서 지금 나가는 인터넷 방송을 찾아 /mp3 를 틂.
//   하나면 바로, 여럿이면 화면에 고를 단추(Deut.gorgi), 없으면 알리고 10초마다 살핌. 인터넷으로 듣다가 행사장 와이파이에 붙으면 현장 방송으로.
// (2.0.0판, 빌드 261015-L1, 방송클, 이사장님 승인 「1」)
// 행사장 와이파이로만 현장 방송(192.168.8.2:7880/stream, 뮤 8비트 1초에 16000)을 받아 폰 스피커·이어폰으로 틉니다.
// - 화면을 잠가도 들립니다(알림 줄에 「LVD 방송 듣는 중」 하나, 멈추기 단추).
// - 전화가 오거나 걸면 소리를 멈추고, 통화가 끝나면 저절로 다시 들려줍니다(이사장님 원칙).
// - 행사장을 벗어나면 한 번만 알리고 기다리다가, 다시 들어오면 저절로 이어 들려줍니다.
// - 같은 폰에서 LVD 송출이 방송 중이면, 스피커로는 틀지 않습니다(소리가 마이크로 되돌아 들어가 울리지 않게). 이어폰이면 틉니다.
package kr.or.ada.app.bfb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import android.media.MediaPlayer
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** 듣기 상태 한곳 — 화면과 일꾼이 함께 봄 */
object Deut {
    @Volatile var kyeojim = false         // 듣기 일꾼이 도는가
    @Volatile var deullim = false         // 지금 소리가 나오는가
    @Volatile var jemok = ""              // 행사 이름
    @Volatile var sangtae = "듣기가 꺼져 있습니다."
    @Volatile var songchulJung = false    // 같은 폰의 LVD 송출이 방송 중인가
    @Volatile var sonMeomchum = false     // 이용자가 손으로 멈춤 — 행사장을 벗어날 때까지 저절로 다시 켜지 않음
    data class InteonetBang(val sid: String, val jemok: String, val who: String, val cho: Int)
    @Volatile var gorgi: List<InteonetBang> = emptyList()   // 2.2.0 인터넷 방송이 여럿일 때 고를 것
    @Volatile var goreun = ""                                // 2.2.0 화면에서 고른 방 번호
    const val JUNGYE = "https://lvd.ada.or.kr:2053"
    val gwanchal = mutableListOf<() -> Unit>()
    var mal: ((String) -> Unit)? = null   // 한 번만 알릴 말(화면이 있으면 화면이, 없으면 알림 줄이)
    fun allyeo() { for (f in gwanchal.toList()) try { f() } catch (_: Exception) { } }
    fun bakkum(t: String, malhal: Boolean) {
        if (t == sangtae) return
        sangtae = t
        allyeo()
        if (malhal) mal?.invoke(t)
    }
}

class DeutService : Service() {

    companion object {
        private const val CHANNEL = "lvd_deutgi"
        private const val BEONHO = 21
        private const val KKEUGI = "kr.or.ada.bfb.KKEUGI"
        const val SONGCHUL = "kr.or.ada.songchul.SANGTAE"   // LVD 송출이 방송을 켜고 끌 때 보내는 알림(같은 협회 서명만 받음)

        fun kyeogi(c: Context) {
            val i = Intent(c, DeutService::class.java)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        }

        fun kkeugi(c: Context) { c.stopService(Intent(c, DeutService::class.java)) }
    }

    @Volatile private var dolgo = false
    @Volatile private var jamsi = false          // 전화 따위로 잠시 멈춤
    @Volatile private var focusIssum = true      // 소리 자리를 쥐고 있는가
    @Volatile private var dasiCheong = false     // 통화 때문에 소리 자리를 아주 잃음 — 통화가 끝나면 곧바로 다시 청함
    private var jamsiCho = 0                      // 통화가 끝난 뒤 소리 자리를 기다린 초
    private var il: Thread? = null
    private var kon: HttpURLConnection? = null
    private var gaksi: PowerManager.WakeLock? = null
    private lateinit var am: AudioManager
    private var focusReq: AudioFocusRequest? = null

    private val focusBaram = AudioManager.OnAudioFocusChangeListener { f ->
        when (f) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // 다른 앱(음악 따위)이 소리 자리를 아주 가져감 — 다투지 않고 멈춤. 전화 때문이면 잠시 멈춤으로
                if (tonghwaJung()) { jamsi = true; focusIssum = false; dasiCheong = true; try { kon?.disconnect() } catch (_: Exception) { } }
                else { Deut.sonMeomchum = true; Deut.bakkum("다른 소리가 시작되어 듣기를 멈추었습니다. 다시 들으시려면 듣기 단추를 누르십시오.", true); stopSelf() }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                focusIssum = false
                if (!jamsi) { jamsi = true; Deut.bakkum("전화나 다른 소리 때문에 잠시 멈추었습니다. 끝나면 저절로 다시 들려 드립니다.", false) }
                try { kon?.disconnect() } catch (_: Exception) { }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                focusIssum = true
                if (jamsi) { jamsi = false; Deut.bakkum("다시 들려 드립니다.", true) }
            }
        }
    }

    private val songchulBaram = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            Deut.songchulJung = i.getBooleanExtra("bangsong", false)
            if (Deut.songchulJung) try { kon?.disconnect() } catch (_: Exception) { }
        }
    }

    private val kkeugiBaram = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { Deut.sonMeomchum = true; stopSelf() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        tongjiCheot()
        val f = IntentFilter(SONGCHUL)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(songchulBaram, f, "kr.or.ada.permission.SONGCHUL", null, Context.RECEIVER_EXPORTED)
        else registerReceiver(songchulBaram, f, "kr.or.ada.permission.SONGCHUL", null)
        val k = IntentFilter(KKEUGI)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(kkeugiBaram, k, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(kkeugiBaram, k)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        tongjiCheot()
        if (!dolgo) {
            dolgo = true
            Deut.kyeojim = true
            try {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                gaksi = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lvd:deutgi").apply { setReferenceCounted(false); acquire(8 * 60 * 60 * 1000L) }
            } catch (_: Exception) { }
            focusBatgi()
            Deut.bakkum("현장영상해설을 찾고 있습니다.", false)
            il = Thread({ dolligi() }, "lvd-deutgi").also { it.priority = Thread.MAX_PRIORITY; it.start() }
            Deut.allyeo()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        dolgo = false
        Deut.kyeojim = false
        Deut.deullim = false
        Deut.gorgi = emptyList(); Deut.goreun = ""
        try { kon?.disconnect() } catch (_: Exception) { }
        il?.interrupt()
        try { unregisterReceiver(songchulBaram) } catch (_: Exception) { }
        try { unregisterReceiver(kkeugiBaram) } catch (_: Exception) { }
        try { if (Build.VERSION.SDK_INT >= 26) focusReq?.let { am.abandonAudioFocusRequest(it) } else @Suppress("DEPRECATION") am.abandonAudioFocus(focusBaram) } catch (_: Exception) { }
        try { gaksi?.release() } catch (_: Exception) { }
        Deut.bakkum("듣기를 멈추었습니다.", false)
        super.onDestroy()
    }

    /** 소리 자리를 청함 — 받으면 참 */
    private fun focusBatgi(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 26) {
                val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setOnAudioFocusChangeListener(focusBaram)
                    .setWillPauseWhenDucked(true)
                    .build()
                focusReq = r
                am.requestAudioFocus(r) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(focusBaram, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (_: Exception) { true }
    }

    /** 전화 중인가 — 소리 자리를 다시 받지 못한 채여도 통화 중이면 틀지 않음 */
    private fun tonghwaJung(): Boolean = am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION || am.mode == AudioManager.MODE_RINGTONE

    /** 이어폰(유선·블루투스·USB)으로 나가는가 */
    private fun iyeopon(): Boolean = try {
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET || (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    } catch (_: Exception) { false }

    private val DEC = ShortArray(256).also { t ->
        for (i in 0 until 256) {
            val u = i.inv() and 0xFF
            val s = u and 0x80; val e = (u shr 4) and 7; val m = u and 0x0F
            val x = (((m shl 3) + 0x84) shl e) - 0x84
            t[i] = (if (s != 0) -x else x).toShort()
        }
    }

    private fun dolligi() {
        var beoseonaTtae = 0L          // 행사장 망이 사라진 때
        var beoseonaAllim = false
        var cheotYeongyeol = true
        val minBuf = AudioTrack.getMinBufferSize(16000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        while (dolgo) {
            // 1. 틀 수 없는 때 — 전화 중, 같은 폰 송출 중(스피커)
            if (jamsi || tonghwaJung()) {
                // 잠시 멈춤 — 소리 자리가 돌아오면(AUDIOFOCUS_GAIN) 풀림. 통화가 끝난 뒤 30초가 지나도 안 돌아오면 한 번 청함
                Deut.deullim = false; jamsi = true; swigi(1000)
                if (!tonghwaJung() && dasiCheong) { dasiCheong = false; if (focusBatgi()) focusIssum = true }
                if (!tonghwaJung() && focusIssum) { jamsi = false; jamsiCho = 0; Deut.bakkum("통화가 끝나 다시 들려 드립니다.", true); continue }
                if (tonghwaJung()) jamsiCho = 0 else if (++jamsiCho >= 30) { jamsiCho = 0; if (focusBatgi()) { jamsi = false; Deut.bakkum("다시 들려 드립니다.", true) } }
                continue
            }
            jamsiCho = 0
            if (Deut.songchulJung && !iyeopon()) { Deut.deullim = false; Deut.bakkum("이 폰에서 LVD 송출이 방송 중이라 스피커로는 틀지 않습니다. 이어폰을 꽂으시면 들립니다.", false); swigi(2000); continue }
            // 2. 행사장 망 찾기
            val n = Hyeonjang.mang(this)
            val jm0 = if (n != null) Hyeonjang.salpigi(this) else null   // 2.4.0 아무 와이파이나 붙어 있어도 방송이 답해야 행사장
            if (n == null || jm0 == null) {
                Deut.deullim = false
                val now = System.currentTimeMillis()
                if (beoseonaTtae == 0L) beoseonaTtae = now
                if (!beoseonaAllim && !cheotYeongyeol && now - beoseonaTtae > 8000) {
                    beoseonaAllim = true
                    Deut.bakkum("BYOD 방송에서 벗어났습니다. 다시 들어오시면 저절로 이어서 들려 드립니다. 그사이 인터넷 방송이 있으면 그것을 들려 드립니다.", true)
                }
                // 2.2.0 행사장이 아니면 인터넷 방송
                val s = inteonetGoreugi()
                if (s != null) { inteonetTeulgi(s); continue }
                swigiGoreum(10_000); continue
            }
            // 3. 받아 틀기
            var at: AudioTrack? = null
            try {
                val jm = jm0
                Deut.jemok = jm
                val k = n.openConnection(Hyeonjang.stream()) as HttpURLConnection   // 2.4.0 찾아 둔 자리(플린트 2 또는 아이피타임 방식 송출 폰)
                k.connectTimeout = 4000; k.readTimeout = 8000; k.useCaches = false
                kon = k
                val ins: InputStream = k.inputStream
                at = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(maxOf(minBuf, 16000 * 2 / 2))   // 0.5초 남짓 — 늦음을 적게
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                at.play()
                beoseonaTtae = 0L
                val mal = (if (jm.isNotEmpty()) jm + " 행사 " else "") + "현장영상해설에 연결되었습니다."
                Deut.bakkum(if (beoseonaAllim && !cheotYeongyeol) "다시 연결되었습니다. " + mal else mal, true)
                beoseonaAllim = false
                cheotYeongyeol = false
                Deut.deullim = true
                tongjiGochigi()
                val b = ByteArray(1600)
                val pcm = ShortArray(1600)
                while (dolgo && !jamsi && !(Deut.songchulJung && !iyeopon())) {
                    val r = ins.read(b)
                    if (r < 0) break
                    for (i in 0 until r) pcm[i] = DEC[b[i].toInt() and 0xFF]
                    at.write(pcm, 0, r)
                    if (tonghwaJung()) { jamsi = true; Deut.bakkum("전화 중이라 잠시 멈추었습니다. 통화가 끝나면 저절로 다시 들려 드립니다.", false); break }
                }
            } catch (_: Exception) {
                // 끊김 — 잠깐 쉬고 다시
            } finally {
                Deut.deullim = false
                try { kon?.disconnect() } catch (_: Exception) { }
                kon = null
                try { at?.stop() } catch (_: Exception) { }
                try { at?.release() } catch (_: Exception) { }
            }
            if (dolgo) swigi(1000)
        }
    }

    private fun swigi(ms: Long) { try { Thread.sleep(ms) } catch (_: InterruptedException) { } }

    /** 쉬되, 화면에서 방송을 고르면 곧바로 깸 */
    private fun swigiGoreum(ms: Long) {
        var t = 0L
        while (dolgo && t < ms && Deut.goreun.isEmpty()) { swigi(500); t += 500 }
    }

    // ───── 2.2.0 인터넷 방송 ─────
    private fun jungyeMutgi(gil: String): JSONObject? = try {
        val k = URL(Deut.JUNGYE + gil).openConnection() as HttpURLConnection
        k.connectTimeout = 6000; k.readTimeout = 8000; k.useCaches = false
        k.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) LVDbangsong")
        val t = if (k.responseCode == 200) k.inputStream.bufferedReader().readText() else null
        k.disconnect()
        t?.let { JSONObject(it) }
    } catch (_: Exception) { null }

    /** 들을 인터넷 방송 — 고른 것이 있으면 그것, 하나면 그것, 여럿이면 화면에 고르기를 띄우고 null */
    private fun inteonetGoreugi(): String? {
        val j = jungyeMutgi("/moklok")
        if (j == null) { Deut.bakkum("인터넷에 닿지 않습니다. 행사장에서는 행사장 와이파이에 붙으면 저절로 들립니다.", false); return null }
        val a = j.optJSONArray("rows")
        val l = mutableListOf<Deut.InteonetBang>()
        if (a != null) for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            if (o.optString("jong") != "live" && o.optString("jong") != "bangsong") continue
            if (o.optString("sid").isEmpty()) continue
            l.add(Deut.InteonetBang(o.optString("sid"), o.optString("title"), o.optString("who"), o.optInt("cho")))
        }
        val g = Deut.goreun
        if (g.isNotEmpty()) { Deut.goreun = ""; l.firstOrNull { it.sid == g }?.let { Deut.gorgi = emptyList(); Deut.jemok = it.jemok; inteonetMal(it); return it.sid } }
        when {
            l.isEmpty() -> {
                if (Deut.gorgi.isNotEmpty()) { Deut.gorgi = emptyList(); Deut.allyeo() }
                Deut.bakkum("지금 나가는 현장영상해설이 없습니다. 방송이 열리거나 행사장에 들어서시면 저절로 들려 드립니다.", true)
            }
            l.size == 1 -> { Deut.gorgi = emptyList(); Deut.jemok = l[0].jemok; inteonetMal(l[0]); return l[0].sid }
            else -> if (Deut.gorgi.map { it.sid } != l.map { it.sid }) {
                Deut.gorgi = l
                Deut.bakkum("지금 나가는 현장영상해설이 " + l.size + "개 있습니다. 아래에서 들으실 것을 고르십시오.", true)
                Deut.allyeo()
            }
        }
        return null
    }

    private fun inteonetMal(b: Deut.InteonetBang) {
        Deut.bakkum("인터넷으로 " + (if (b.jemok.isEmpty()) "" else b.jemok + " ") + "현장영상해설을 들려 드립니다." + (if (b.who.isEmpty()) "" else " 현장영상해설사 " + b.who + "."), true)
    }

    /** 인터넷 방송 틀기 — 끝나거나, 멈추거나, 행사장에 들어서거나, 다른 것을 고르면 돌아감 */
    private fun inteonetTeulgi(sid: String) {
        val mp = MediaPlayer()
        val jalmot = java.util.concurrent.atomic.AtomicBoolean(false)
        try {
            mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            mp.setOnErrorListener { _, _, _ -> jalmot.set(true); true }
            mp.setDataSource(Deut.JUNGYE + "/mp3?sid=" + sid + "&_c=" + System.currentTimeMillis())
            val l = java.util.concurrent.CountDownLatch(1)
            mp.setOnPreparedListener { l.countDown() }
            mp.prepareAsync()
            if (!l.await(15, java.util.concurrent.TimeUnit.SECONDS) || jalmot.get()) { swigi(2000); return }
            mp.start()
            Deut.deullim = true
            tongjiGochigi()
            var cho = 0
            var meomchum = 0
            while (dolgo) {
                swigi(1000); cho++
                if (jamsi || tonghwaJung()) break
                if (Deut.songchulJung && !iyeopon()) break
                if (jalmot.get()) break
                if (Deut.goreun.isNotEmpty() && Deut.goreun != sid) break
                meomchum = if (mp.isPlaying) 0 else meomchum + 1
                if (meomchum > 10) break   // 10초 넘게 소리가 없으면 다시 붙음
                if (cho % 10 == 0) {
                    // 행사장에 들어서면 현장 방송으로(더 빠름)
                    if (Hyeonjang.mang(this) != null && Hyeonjang.salpigi(this) != null) { Deut.bakkum("행사장에 들어서서 BYOD 방송으로 바꿉니다.", true); break }
                    val j = jungyeMutgi("/sangtae?sid=$sid")
                    if (j != null && !j.optBoolean("on", true)) { Deut.bakkum("그 현장영상해설 방송이 끝났습니다. 다른 방송을 찾고 있습니다.", true); break }
                }
            }
        } catch (_: Exception) {
            swigi(2000)
        } finally {
            Deut.deullim = false
            try { mp.stop() } catch (_: Exception) { }
            try { mp.release() } catch (_: Exception) { }
        }
    }

    // ───── 알림 줄 — 「LVD 방송 듣는 중」 하나, 멈추기 단추 ─────
    private fun tongji(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "LVD 방송 듣기", NotificationManager.IMPORTANCE_LOW))
        val yeolgi = PendingIntent.getActivity(this, 1, Intent(this, BangsongActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val kkeum = PendingIntent.getBroadcast(this, 2, Intent(KKEUGI).setPackage(packageName), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("LVD 방송 듣는 중")
            .setContentText(if (Deut.jemok.isNotEmpty()) Deut.jemok else "현장영상해설")
            .setContentIntent(yeolgi)
            .addAction(android.R.drawable.ic_media_pause, "멈추기", kkeum)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun tongjiCheot() {
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(BEONHO, tongji(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(BEONHO, tongji())
        } catch (_: Exception) { }
    }

    private fun tongjiGochigi() {
        try { (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(BEONHO, tongji()) } catch (_: Exception) { }
    }
}
