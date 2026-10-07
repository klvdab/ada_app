// BYOD 방송 — 해설 소리 받기 (1.0.0판, 빌드 261003-B1, 이사장님 승인 2026-10-03)
// 외장 USB 사운드카드(송신기와 수신기 선)가 꽂혀 있으면 그쪽을 저절로 고르고, 없으면 태블릿 마이크로 받습니다.
// 방송 중에 사운드카드를 꽂거나 빼면 저절로 다시 고릅니다.
// 1초에 16000번, 0.1초(1600개)씩 읽어 뮤로 줄여 넘깁니다 — 노트북판과 같은 방식.
package kr.or.ada.app.byod

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper

class Sori(private val ctx: Context, private val batgi: (ByteArray) -> Unit) {

    companion object {
        const val RATE = 16000
        const val JOGAK = 1600   // 0.1초
    }

    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    @Volatile private var dolgo = false
    @Volatile private var dasiGoreugi = false
    private var il: Thread? = null

    private val gigiBaram = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
            if (added.any { it.isSource && usbInga(it) }) dasiGoreugi = true
        }
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
            if (removed.any { it.isSource && usbInga(it) }) dasiGoreugi = true
        }
    }

    private fun usbInga(d: AudioDeviceInfo): Boolean =
        d.type == AudioDeviceInfo.TYPE_USB_DEVICE || d.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            d.type == AudioDeviceInfo.TYPE_USB_ACCESSORY

    // 꽂힌 USB 소리 장치(받는 쪽)가 있으면 그것, 없으면 null(태블릿 마이크)
    private fun usbGigi(): AudioDeviceInfo? =
        am.getDevices(AudioManager.GET_DEVICES_INPUTS).let { ds -> ds.firstOrNull { usbInga(it) } ?: ds.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_LINE_ANALOG || it.type == AudioDeviceInfo.TYPE_LINE_DIGITAL } }   // 1.1.3 — USB가 없으면 3.5밀리 마이크·라인 입력도

    fun sijak() {
        if (dolgo) return
        dolgo = true
        am.registerAudioDeviceCallback(gigiBaram, Handler(Looper.getMainLooper()))
        il = Thread({ dolligi() }, "byod-sori").also { it.priority = Thread.MAX_PRIORITY; it.start() }
    }

    fun meomchugi() {
        dolgo = false
        try { am.unregisterAudioDeviceCallback(gigiBaram) } catch (_: Exception) { }
        il?.interrupt()
        il = null
    }

    private fun wonbon(): Int {
        val unp = am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
        return if (unp == "true") MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
    }

    @SuppressLint("MissingPermission")
    private fun dolligi() {
        val buf = ShortArray(JOGAK)
        while (dolgo) {
            var rec: AudioRecord? = null
            try {
                dasiGoreugi = false
                val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val bufBytes = maxOf(minBuf, JOGAK * 2 * 4)
                rec = AudioRecord(wonbon(), RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufBytes)
                if (rec.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("마이크를 열지 못했습니다")
                val usb = usbGigi()
                if (usb != null) {
                    rec.preferredDevice = usb
                    val nm = usb.productName?.toString()?.trim().orEmpty()
                    Bang.sojae = if (nm.isEmpty()) "USB 소리 장치" else "USB 소리 장치($nm)"
                } else {
                    Bang.sojae = "태블릿 마이크"
                }
                rec.startRecording()
                if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) throw IllegalStateException("마이크가 다른 앱에 잡혀 있습니다")
                if (Bang.jalmot.startsWith("마이크")) Bang.jalmot = ""
                Bang.allyeo()
                while (dolgo && !dasiGoreugi) {
                    var chaeum = 0
                    while (chaeum < JOGAK && dolgo) {
                        val n = rec.read(buf, chaeum, JOGAK - chaeum)
                        if (n < 0) throw IllegalStateException("마이크 읽기 오류 $n")
                        chaeum += n
                    }
                    if (chaeum == JOGAK && Bang.keu) batgi(MuLaw.jurigi(buf, JOGAK))
                }
            } catch (e: Exception) {
                if (dolgo) {
                    Bang.jalmot = "마이크: " + (e.message ?: "알 수 없는 문제")
                    Bang.allyeo()
                    try { Thread.sleep(1000) } catch (_: InterruptedException) { }
                }
            } finally {
                try { rec?.stop() } catch (_: Exception) { }
                try { rec?.release() } catch (_: Exception) { }
            }
        }
    }
}

// 1.1.3(261007-B5) 기기 점검 — 이사장님 허브(코엠에스 CT118) 시험에서 사운드카드가 안 잡혀 「태블릿 마이크」로만 나옴. 태블릿이 무엇을 보는지 한 줄씩 알려 줌
fun gigiJeomgeom(ctx: Context): String {
    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    fun jongryu(t: Int): String = when (t) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "태블릿 마이크"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "태블릿 스피커"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB 소리 장치"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB 헤드셋"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB 액세서리"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "3.5밀리 마이크 달린 이어폰"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "3.5밀리 이어폰"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> "라인 단자"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "블루투스"
        else -> "기타 " + t
    }
    val sb = StringBuilder()
    sb.append("소리 받는 장치:\n")
    for (d in am.getDevices(AudioManager.GET_DEVICES_INPUTS)) sb.append("  ").append(jongryu(d.type)).append(", ").append(d.productName).append("\n")
    sb.append("소리 내는 장치:\n")
    for (d in am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) sb.append("  ").append(jongryu(d.type)).append(", ").append(d.productName).append("\n")
    val ul = runCatching { (ctx.getSystemService(Context.USB_SERVICE) as android.hardware.usb.UsbManager).deviceList.values.toList() }.getOrDefault(emptyList())
    sb.append("USB로 잡힌 장치 ").append(ul.size).append("개:\n")
    for (u in ul) sb.append("  ").append(runCatching { u.productName }.getOrNull() ?: "이름 없음").append(", 만든 곳 ").append(runCatching { u.manufacturerName }.getOrNull() ?: "모름").append(", 번호 ").append(u.vendorId).append("-").append(u.productId).append("\n")
    sb.append("연결:\n")
    try {
        for (ni in java.net.NetworkInterface.getNetworkInterfaces()) {
            if (!ni.isUp || ni.isLoopback) continue
            for (a in ni.inetAddresses) if (a is java.net.Inet4Address) sb.append("  ").append(if (ni.name.startsWith("eth")) "유선 랜" else if (ni.name.startsWith("wlan")) "와이파이" else ni.name).append(" ").append(a.hostAddress).append("\n")
        }
    } catch (e: Exception) { }
    return sb.toString()
}
