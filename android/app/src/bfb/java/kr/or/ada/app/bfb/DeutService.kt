// LVD 방송(듣는 앱) — 현장영상해설 듣기 일꾼 (2.0.0판, 빌드 261015-L1, 방송클, 이사장님 승인 「1」)
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
            if (n == null) {
                Deut.deullim = false
                val now = System.currentTimeMillis()
                if (beoseonaTtae == 0L) beoseonaTtae = now
                if (!beoseonaAllim && now - beoseonaTtae > 8000) {
                    beoseonaAllim = true
                    Deut.bakkum(if (cheotYeongyeol) "행사장 와이파이를 찾고 있습니다. 행사장에 들어서면 저절로 들립니다." else "현장 방송에서 벗어났습니다. 다시 들어오시면 저절로 이어서 들려 드립니다.", true)
                }
                swigi(2000); continue
            }
            // 3. 받아 틀기
            var at: AudioTrack? = null
            try {
                val jm = Hyeonjang.salpigi(this)
                if (jm == null) { swigi(2000); continue }
                Deut.jemok = jm
                val k = n.openConnection(URL("http://${Hyeonjang.IP}:${Hyeonjang.WEB}/stream")) as HttpURLConnection
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
