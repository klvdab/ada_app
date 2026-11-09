// 안드로이드 길눈 — 소리 기록 2.91.0 (261109-A79, 대클, 이사장님 지시 2026-11-09 「사용 기록이 없는 것부터 고쳐야」·「모두 승인」, 아이폰 2.126.0 SoriGirok.swift와 같음)
// 음악이 이어지는 동안·볼륨 단추·소리 나가는 곳·앱 앞뒤·비정상으로 꺼진 것을 길눈이 적지 않던 빈자리를 채웁니다.
//   1. 볼륨 단추(음악 소리 크기)가 바뀔 때마다 — 얼마에서 얼마로, 방송 형편
//   2. 소리 나가는 곳이 바뀔 때(이어폰 꽂음·뺌·블루투스)
//   3. 음악·방송이 나오는 동안 10분마다 「아직 나오는 중」 — 무엇을, 폰 소리 크기, 방송 자체 크기(0.2 = 길눈이 말하느라 줄인 것)
//   4. 앱이 앞으로 나오고 뒤로 갈 때
//   5. 앱이 다시 켜질 때 마지막으로 살아 있던 시각과 그때 방송 형편
//   6. 안드로이드가 알려 주는 지난 종료 까닭(사용자가 닫음·강제 종료·멈춤·메모리 부족 등, 안드로이드 11 이상) — 정상으로 끝났는지는 이것으로 가림
// 위치 좌표는 이 부품에서 남기지 않습니다.
package kr.or.ada.app.gilnun

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.database.ContentObserver
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object SoriGirok {
    private var sijakham = false
    private val main = Handler(Looper.getMainLooper())
    private var am: AudioManager? = null
    private var ud: SharedPreferences? = null
    private var jinanBollyum = -1
    private var saraitdaTtae = 0L
    private var naoneunGirokTtae = 0L
    private var apSu = 0

    fun sijak(c: Context) {
        if (sijakham) return
        sijakham = true
        val app = c.applicationContext
        am = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        ud = app.getSharedPreferences("gilnun_sorigirok", Context.MODE_PRIVATE)
        jinanKkeutSalpigi(app)
        jinanBollyum = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: -1
        Girok.namgi("sori_cheo", sangtae())
        // 1. 볼륨 단추
        try {
            app.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, object : ContentObserver(main) {
                override fun onChange(selfChange: Boolean) { bollyumBakkwim() }
            })
        } catch (e: Exception) {}
        // 2. 나가는 곳
        try {
            am?.registerAudioDeviceCallback(object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(d: Array<out AudioDeviceInfo>?) {
                    val m = sangtae().toMutableMap(); m["deureoom"] = (d ?: emptyArray()).joinToString(",") { it.type.toString() }
                    Girok.namgi("sori_gil", m)
                }
                override fun onAudioDevicesRemoved(d: Array<out AudioDeviceInfo>?) {
                    val m = sangtae().toMutableMap(); m["ppajim"] = (d ?: emptyArray()).joinToString(",") { it.type.toString() }
                    Girok.namgi("sori_gil", m)
                }
            }, main)
        } catch (e: Exception) {}
        // 4. 앞·뒤
        (app as? Application)?.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: Activity) {
                apSu += 1
                if (apSu == 1) { ud?.edit()?.putString("apdwi", "ap")?.apply(); Girok.namgi("app_ap", sangtae()) }
            }
            override fun onActivityStopped(a: Activity) {
                apSu = maxOf(0, apSu - 1)
                if (apSu == 0) { ud?.edit()?.putString("apdwi", "dwi")?.apply(); Girok.namgi("app_dwi", sangtae()); saraitda(); Girok.jeojang() }
            }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
        saraitda()
        naoneunGirokTtae = System.currentTimeMillis()
        // 3. 1초마다 살피기
        val r = object : Runnable {
            override fun run() { salpigi(); main.postDelayed(this, 1000) }
        }
        main.postDelayed(r, 1000)
    }

    private fun bollyumBakkwim() {
        val v = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: return
        if (v == jinanBollyum) return
        val m = sangtae().toMutableMap()
        m["jeon"] = jinanBollyum
        m["hu"] = v
        jinanBollyum = v
        Girok.namgi("sori_keugi", m)
    }

    private fun salpigi() {
        val jigeum = System.currentTimeMillis()
        if (jigeum - saraitdaTtae >= 60_000) saraitda()
        if (Bangsong.naneunJung && jigeum - naoneunGirokTtae >= 600_000) {
            naoneunGirokTtae = jigeum
            Girok.namgi("bangsong_naoneunjung", sangtae())
        }
    }

    private fun saraitda() {
        saraitdaTtae = System.currentTimeMillis()
        ud?.edit()
            ?.putLong("saraitda", saraitdaTtae)
            ?.putString("hyeongpyeon", "bangsong=${Bangsong.jong.ireum} naonda=${Bangsong.naneunJung} ap=${apSu > 0}")
            ?.apply()
    }

    /** 5·6. 지난번 끝 */
    private fun jinanKkeutSalpigi(c: Context) {
        val u = ud ?: return
        val t = u.getLong("saraitda", 0L)
        if (t > 0L) {
            val f = SimpleDateFormat("MM-dd HH:mm:ss", Locale.KOREA).apply { timeZone = TimeZone.getTimeZone("Asia/Seoul") }
            Girok.namgi("jinan_kkeut", mapOf("majimak" to f.format(Date(t)), "hyeongpyeon" to (u.getString("hyeongpyeon", "") ?: ""),
                "apdwi" to (u.getString("apdwi", "") ?: "")))
        }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val a = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val bon = u.getLong("kkeutKkadakBon", 0L)
                var sae = bon
                val f = SimpleDateFormat("MM-dd HH:mm:ss", Locale.KOREA).apply { timeZone = TimeZone.getTimeZone("Asia/Seoul") }
                for (x in a.getHistoricalProcessExitReasons(c.packageName, 0, 5)) {
                    if (x.timestamp <= bon) continue
                    if (x.timestamp > sae) sae = x.timestamp
                    Girok.namgi("gojang_bogo", mapOf("jong" to "kkeutnam", "kkadak" to x.reason, "seolmyeong" to (x.description ?: "").take(80),
                        "jungyo" to x.importance, "ttae" to f.format(Date(x.timestamp))))
                }
                if (sae > bon) u.edit().putLong("kkeutKkadakBon", sae).apply()
            } catch (e: Exception) {}
        }
    }

    private fun sangtae(): Map<String, Any?> {
        val a = am
        return mapOf(
            "bollyum" to (a?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: -1),
            "choedae" to (a?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: -1),
            "bangsong" to Bangsong.jong.ireum,
            "naonda" to Bangsong.naneunJung,
            "bangsongKeugi" to (Bangsong.exo?.volume?.toDouble() ?: -1.0),
            "malhaneunjung" to Sori.malhaneunJung,
            "ap" to (apSu > 0)
        )
    }
}
