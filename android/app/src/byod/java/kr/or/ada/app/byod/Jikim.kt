// LVD 송출 — 녹화·방송 지킴이 (1.6.0판, 빌드 261019-B14, 방클, 이사장님 지시 2026-10-19 「녹화 끊김 막기」)
// 녹화(교육 녹화·인터넷 방송)나 BYOD 방송이 멈추면 3초에 한 번 조용한 두 음 알림과 떨림으로 알립니다. 급하게 쉼 없이 울리지 않습니다.
// 화면을 잠갔거나 앱이 뒤로 가 있어도 울립니다(앱이 살아 있는 동안). 다시 이어지거나 마치면 멈춥니다. 전화가 오거나 통화 중에는 울리지 않습니다.
package kr.or.ada.app.byod

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator

class Jikim(private val ctx: Context, private val meomchumInga: () -> Boolean, private val bakkwim: (Boolean) -> Unit = {}) {
    private val hd = Handler(Looper.getMainLooper())
    private var ton: ToneGenerator? = null
    private var ullim = false
    private var majimak = 0L
    private var dolgo = false

    private val salpim = object : Runnable {
        override fun run() {
            if (!dolgo) return
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val tonghwa = am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION || am.mode == AudioManager.MODE_RINGTONE
            val m = !tonghwa && (try { meomchumInga() } catch (_: Exception) { false })   // 통화 중에는 울리지 않음(이사장님 원칙)
            if (m != ullim) { ullim = m; try { bakkwim(m) } catch (_: Exception) { } }
            val now = System.currentTimeMillis()
            if (m && now - majimak >= 3000) { majimak = now; ullida() }
            hd.postDelayed(this, 1000)
        }
    }

    fun sijak() { if (dolgo) return; dolgo = true; majimak = 0; hd.postDelayed(salpim, 1000) }
    fun meomchugi() { dolgo = false; ullim = false; hd.removeCallbacks(salpim); try { ton?.release() } catch (_: Exception) { }; ton = null }

    private fun ullida() {
        try {
            if (ton == null) ton = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
            ton?.startTone(ToneGenerator.TONE_PROP_ACK, 300)
        } catch (_: Exception) { }
        try {
            val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") v.vibrate(200)
        } catch (_: Exception) { }
    }
}
