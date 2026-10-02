// 안드로이드 길눈 — 진동 무늬(2.7.0, 묶음 b2 점지도 마저, 대표님 지시)
// 아이폰 Jindong.swift(2.6.0, 2.38.0)와 같은 무늬 — 왼쪽은 짧게 두 번, 오른쪽은 길게 한 번, 도착은 세 번, 뒤쪽은 길게, 그 밖은 짧게 한 번.
// 폰과 갤럭시 워치가 같은 무늬로 울림(워치는 WatchLink.jindongBonae). 긴급통화 중에도 진동은 냄(말소리만 쉼).
package kr.or.ada.app.gilnun

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object Jindong {
    private var ctx: Context? = null
    private val main = Handler(Looper.getMainLooper())

    fun sijak(c: Context) { if (ctx == null) ctx = c.applicationContext }

    /** 시계 방향으로 — 2~5시는 오른쪽, 7~10시는 왼쪽, 6시는 뒤, 12시(와 11·1시)는 곧게라 울리지 않음 */
    fun banghyang(s: Int) {
        val mu = when (s) {
            in 2..5 -> "right"
            in 7..10 -> "left"
            6 -> "long"
            else -> return
        }
        hagi(mu)
        WatchLink.jindongBonae(mu)
    }

    /** 도착 — 폰과 워치 함께 */
    fun dochak() {
        hagi("arrive")
        WatchLink.jindongBonae("arrive")
    }

    /** 폰만 울림 — mu: left, right, arrive, long, 그 밖은 짧게 한 번 */
    @Suppress("DEPRECATION")
    fun hagi(mu: String) {
        main.post {
            val c = ctx ?: return@post
            // (쉼, 울림) 차례 — 아이폰 (시작, 길이)를 안드로이드 물결 꼴로
            val gil: LongArray = when (mu) {
                "left" -> longArrayOf(0, 120, 130, 120)
                "right" -> longArrayOf(0, 600)
                "arrive" -> longArrayOf(0, 150, 150, 150, 150, 150)
                "long" -> longArrayOf(0, 800)
                else -> longArrayOf(0, 120)
            }
            try {
                val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31)
                    (c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
                else c.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (v == null || !v.hasVibrator()) return@post
                if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(gil, -1))
                else v.vibrate(gil, -1)
            } catch (e: Exception) {
                Girok.namgi("jindong_oryu", mapOf("e" to (e.message ?: "")))
            }
        }
    }
}
