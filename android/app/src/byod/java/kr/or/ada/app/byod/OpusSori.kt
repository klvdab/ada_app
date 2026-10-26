// LVD 송출 안드로이드 — 오푸스 소리(시험) (1.10.0판, 빌드 261026-B20, 새방클, 이사장님 승인 2026-10-26 「해 보자. 다른 기능에 문제를 만들지 않는 선에서」)
// 지금 방식(뮤 8비트, 1초에 16000)은 그대로 두고, 켜 두었을 때만 같은 해설 소리를 오푸스(1초에 48000, 48kbps)로도 만들어
// 이 기기의 /opus 길(아이피타임 방식)이나 플린트 2(BYOD2 꼴)로 함께 내보냄. LVD 방송 2.7.0부터는 오푸스가 있으면 오푸스로 들음.
// 오푸스를 줄이는 기능은 안드로이드 10(API 29)부터 폰에 들어 있음 — 그보다 낮으면 켜도 지금 방식만 나감.
// 오푸스 한 덩이(20밀리초) 앞에 길이 2바이트(큰 자리 먼저)를 붙여 보냄. 길이 0은 「살아 있음」 알림.
package kr.or.ada.app.byod

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import java.nio.ByteOrder

object OpusSori {
    const val RATE = 48000
    /** 더 보기에서 켜고 끔(처음값 꺼짐) — 다음 방송부터 적용 */
    fun on(c: Context): Boolean = c.getSharedPreferences("byod", Context.MODE_PRIVATE).getBoolean("opus", false)
    fun dugi(c: Context, v: Boolean) { c.getSharedPreferences("byod", Context.MODE_PRIVATE).edit().putBoolean("opus", v).apply() }
    /** 이 폰에서 오푸스를 줄일 수 있는가 */
    fun doena(): Boolean = Build.VERSION.SDK_INT >= 29
    /** 지금 방송에서 오푸스가 실제로 나가는가(줄이개가 돌고 있는가) */
    @Volatile var naganda = false
    @Volatile var oryu = ""

    /** 길이 2바이트를 붙인 한 덩이 */
    fun teul(p: ByteArray): ByteArray {
        val o = ByteArray(p.size + 2)
        o[0] = ((p.size shr 8) and 0xFF).toByte(); o[1] = (p.size and 0xFF).toByte()
        System.arraycopy(p, 0, o, 2, p.size)
        return o
    }
    val SALAITTA = byteArrayOf(0, 0)
}

/** 오푸스 줄이개 — 48000 16비트 한 줄기를 받아 20밀리초 덩이마다 batgi 로 넘김. 소리 받는 일꾼 한 줄기에서만 부름 */
class OpusSseugi(private val batgi: (ByteArray) -> Unit) {
    private val mc: MediaCodec = MediaCodec.createEncoderByType("audio/opus")
    private val info = MediaCodec.BufferInfo()
    private var pts = 0L

    init {
        val f = MediaFormat.createAudioFormat("audio/opus", OpusSori.RATE, 1)
        f.setInteger(MediaFormat.KEY_BIT_RATE, 48000)
        f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4800 * 2 * 2)
        f.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
        mc.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mc.start()
    }

    fun neoki(pcm: ShortArray, n: Int) {
        var off = 0
        var sido = 0
        while (off < n && sido < 50) {
            val ii = mc.dequeueInputBuffer(10_000)
            if (ii < 0) { ppaegi(); sido++; continue }
            val ib = mc.getInputBuffer(ii) ?: run { mc.queueInputBuffer(ii, 0, 0, pts, 0); return }
            ib.clear()
            val m = minOf(n - off, ib.remaining() / 2)
            if (m <= 0) { mc.queueInputBuffer(ii, 0, 0, pts, 0); return }
            ib.order(ByteOrder.nativeOrder()).asShortBuffer().put(pcm, off, m)
            mc.queueInputBuffer(ii, 0, m * 2, pts, 0)
            pts += m * 1_000_000L / OpusSori.RATE
            off += m
            ppaegi()
        }
    }

    private fun ppaegi() {
        while (true) {
            val oi = mc.dequeueOutputBuffer(info, 0)
            if (oi < 0) return
            if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0) {
                val ob = mc.getOutputBuffer(oi)
                if (ob != null) {
                    ob.position(info.offset); ob.limit(info.offset + info.size)
                    val b = ByteArray(info.size); ob.get(b)
                    try { batgi(b) } catch (_: Exception) { }
                }
            }
            mc.releaseOutputBuffer(oi, false)
        }
    }

    fun kkeugi() {
        try { mc.stop() } catch (_: Exception) { }
        try { mc.release() } catch (_: Exception) { }
    }
}

/** 48000 → 16000 내림(지금 방식 뮤 줄기용) — 7킬로헤르츠 아래만 남기는 거르개 뒤 세 개에 하나 */
class Naerim {
    private val gyesu: DoubleArray
    private val yet = DoubleArray(GIL)
    private var jari = 0
    companion object { private const val GIL = 31 }
    init {
        val fc = 7000.0 / OpusSori.RATE
        val m = (GIL - 1) / 2.0
        val g = DoubleArray(GIL)
        var hap = 0.0
        for (i in 0 until GIL) {
            val x = i - m
            val sinc = if (x == 0.0) 2 * fc else Math.sin(2 * Math.PI * fc * x) / (Math.PI * x)
            val w = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (GIL - 1))
            g[i] = sinc * w; hap += g[i]
        }
        for (i in 0 until GIL) g[i] /= hap
        gyesu = g
    }
    /** in48 의 n 개(3의 배수)를 걸러 out16 에 n/3 개로 */
    fun ppaegi(in48: ShortArray, n: Int, out16: ShortArray) {
        var k = 0
        for (i in 0 until n) {
            yet[jari] = in48[i].toDouble()
            jari = (jari + 1) % GIL
            if (i % 3 == 2) {
                var s = 0.0
                var p = jari
                for (t in 0 until GIL) { s += yet[p] * gyesu[t]; p = (p + 1) % GIL }
                out16[k++] = s.coerceIn(-32768.0, 32767.0).toInt().toShort()
            }
        }
    }
}
