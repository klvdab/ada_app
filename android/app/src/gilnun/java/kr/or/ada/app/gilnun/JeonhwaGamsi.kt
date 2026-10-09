package kr.or.ada.app.gilnun

// 2.30.0 (261009-A18, 이사장님 지시·승인 2026-10-09) 전화가 오거나 걸거나 통화하는 동안 길눈의 모든 소리를 멈춤
// 이사장님 말씀: "tv를 보든 라디오를 듣든 어떤 일을 하다가도 전화 통화가 연결되면 그 하던 건 멈추는 게 맞아" —
//   JTV를 틀어 둔 채 전화를 걸어 통화하는데 방송 소리가 계속 겹쳐 큰 문제가 생길 뻔하심(아이폰).
// 아이폰 JeonhwaGamsi.swift 와 같은 뜻:
//   ① 벨이 울릴 때, 전화를 걸 때, 통화하는 동안, 다른 앱의 인터넷 통화(카카오톡 보이스톡 등) 동안 → 전화 중
//   ② 전화 중에는 방송·음악·기사 읽기 멈춤, 길눈 안내 말소리·알림 소리·목소리 토막 멈춤, 하이 길눈 듣기 멈춤
//      다만 걷는 중 위험 경고는 말 대신 길게 진동(Sori 의 긴급통화 때와 같은 길)
//   ③ 통화가 끝나면 방송은 저절로 다시 틀지 않고 길 찾기 첫 화면과 음악·방송 화면 맨 위에 「방송 이어 듣기」 단추 하나
//      (설정의 「통화 뒤 방송 저절로 이어 듣기」를 켜시면 저절로 이어짐)
// 권한이 필요 없는 소리 모드(AudioManager.mode)로 알아챔 — 벨(RINGTONE)·통화(IN_CALL)·인터넷 통화(IN_COMMUNICATION)
// 길눈 긴급통화서비스도 인터넷 통화 모드를 쓰지만 그것은 GinGeup 이 따로 맡으므로 여기서는 셈하지 않음

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

object JeonhwaGamsi {
    private val main = Handler(Looper.getMainLooper())
    private var am: AudioManager? = null
    private var dolgo = false

    /** 지금 전화 중(벨·걸기·통화·다른 앱 인터넷 통화) */
    @Volatile var jeonhwaJung = false
        private set

    /** 전화 중이 바뀌면 부름(동영상 화면 등이 붙음) */
    val deullim = ArrayList<(Boolean) -> Unit>()

    fun sijak(c: Context) {
        if (dolgo) return
        dolgo = true
        am = c.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (Build.VERSION.SDK_INT >= 31) {
            try { am?.addOnModeChangedListener({ main.post(it) }) { salpigi() } } catch (e: Exception) {}
        }
        main.post(dolgi)
    }

    // 모드 바뀜 알림을 못 받는 폰도 있어 1초마다 한 번 더 살핌(가벼운 값 읽기 하나)
    private val dolgi = object : Runnable {
        override fun run() { salpigi(); main.postDelayed(this, 1000) }
    }

    private fun salpigi() {
        val m = try { am?.mode ?: AudioManager.MODE_NORMAL } catch (e: Exception) { AudioManager.MODE_NORMAL }
        val uriGinGeup = GinGeup.sangtae != GinGeupSangtae.EOPSEUM
        val j = m == AudioManager.MODE_RINGTONE || m == AudioManager.MODE_IN_CALL ||
            (m == AudioManager.MODE_IN_COMMUNICATION && !uriGinGeup) ||
            (Build.VERSION.SDK_INT >= 30 && m == AudioManager.MODE_CALL_SCREENING)
        if (j == jeonhwaJung) return
        jeonhwaJung = j
        bakkwim(j, m)
    }

    private fun bakkwim(j: Boolean, m: Int) {
        Girok.namgi("jeonhwa", mapOf("on" to j, "mode" to m))
        Sori.jeonhwaJung = j
        if (j) {
            Sori.meomchugi()          // 하던 말을 곧바로 끊음
            TomakDeutgi.meomchugi()   // 목소리 토막
            if (MalHagi.sangtae != MalSangtae.SWIM) MalHagi.meomchum()   // 묻던 말(마이크)을 닫음
        }
        Bangsong.jeonhwa(j)
        for (f in deullim.toList()) try { f(j) } catch (e: Exception) {}
    }
}
