// 안드로이드 길눈 2.27.0(빌드 261007-A15, 대장클, 이사장님 허락 2026-10-07 22:45) — 기초 시험(아이폰 GichoSiheom.swift 와 같은 잣대·같은 말)
// 30분 동안 1분마다 지금 상태를 말하고 나스에 남깁니다.
// 폰을 잠근 채, 주머니 속에서, 통신을 끊은 채, 음악을 튼 채, 톡백을 켠 채 안내가 이어지는지 봅니다.
// 기록에는 그때 화면이 꺼졌는지, 폰이 잠겼는지, 음악이 나오고 있었는지, 톡백이 켜졌는지를 함께 남겨 클이 실물로 확인합니다.
// 시험하는 동안에는 위치 지킴이(WichiService)가 폰을 깨워 둡니다(화면이 꺼져도 1분 말이 멈추지 않게).
package kr.or.ada.app.gilnun

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityManager

object GichoSiheomEngine {
    const val CHONG_BUN = 30
    private val main = Handler(Looper.getMainLooper())
    private var ctx: Context? = null

    var doneunJung = false
        private set
    var bun = 0
        private set
    var majimakGyeolgwa = ""
        private set
    /** 화면이 바뀌어야 할 때(시작·끝) */
    var byeonhwa: (() -> Unit)? = null

    private var wichiGyeonggoTtae = 0L
    private var sijakBatun = 0
    private var sijakMal = 0
    private var sijakGeoreum = 0
    private var kkeunkimSu = 0
    private var yeongyeolJeon = true

    fun sijak(c: Context) {
        if (doneunJung) return
        ctx = c.applicationContext
        Wichi.sijak(c)
        doneunJung = true
        bun = 0
        sijakBatun = Wichi.batunSu
        sijakMal = Sori.malHaneunSu
        sijakGeoreum = Wichi.oneulGeoreum
        kkeunkimSu = 0
        yeongyeolJeon = Tongsin.yeongyeol
        wichiGyeonggoTtae = 0L
        Sori.mal("기초 시험을 시작합니다. 30분 동안 1분마다 말씀드립니다. 화면을 잠그고 주머니에 넣으셔도 됩니다.")
        Girok.namgi("gicho_sijak", sangtae())
        main.removeCallbacks(hanBun)
        main.removeCallbacks(salpim)
        main.postDelayed(hanBun, 60000)
        main.postDelayed(salpim, 10000)
        byeonhwa?.invoke()
    }

    private val hanBun = object : Runnable {
        override fun run() {
            if (!doneunJung) return
            bun += 1
            var mal = "기초 시험 ${bun}분째. "
            val j = Wichi.jigeum
            mal += if (j == null) "아직 위치를 받지 못했습니다. "
                else if (j.georeumChu) "위성이 흐려 걸음으로 이어 셈하는 중입니다. "
                else "위성 오차 ${Math.round(j.ochae)}미터. "
            mal += "오늘 걸음 ${Wichi.oneulGeoreum}. "
            mal += if (Tongsin.yeongyeol) "통신 됨." else "통신 끊김."
            Sori.mal(mal)
            Girok.namgi("gicho_bun", sangtae() + mapOf("bun" to bun))
            byeonhwa?.invoke()
            if (bun >= CHONG_BUN) { machim(false); return }
            main.postDelayed(this, 60000)
        }
    }

    /** 10초마다 — 통신이 끊기고 이어지는 것을 알리고, 위치가 1분 넘게 안 들어오면 경고(5분에 한 번) */
    private val salpim = object : Runnable {
        override fun run() {
            if (!doneunJung) return
            val ok = Tongsin.yeongyeol
            if (ok != yeongyeolJeon) {
                yeongyeolJeon = ok
                if (!ok) kkeunkimSu += 1
                Sori.mal(if (ok) "통신이 다시 이어졌습니다. 쌓아 둔 기록을 보냅니다." else "통신이 끊겼습니다. 기록은 폰에 쌓아 두었다가 이어지면 보냅니다.")
            }
            val j = Wichi.jigeum
            val cho = if (j == null) 999L else (System.currentTimeMillis() - j.ttae) / 1000
            if (cho > 60) {
                val now = System.currentTimeMillis()
                if (wichiGyeonggoTtae == 0L || now - wichiGyeonggoTtae > 300000) {
                    wichiGyeonggoTtae = now
                    Sori.mal("위치가 1분 넘게 들어오지 않습니다.", MalGeup.GYEONGGO)
                    Girok.namgi("gicho_wichi_meomchum", mapOf("cho" to cho))
                }
            }
            main.postDelayed(this, 10000)
        }
    }

    fun machim(jungdan: Boolean) {
        if (!doneunJung) return
        main.removeCallbacks(hanBun)
        main.removeCallbacks(salpim)
        doneunJung = false
        val batun = Wichi.batunSu - sijakBatun
        val mal = Sori.malHaneunSu - sijakMal
        val georeum = maxOf(0, Wichi.oneulGeoreum - sijakGeoreum)
        majimakGyeolgwa = (if (jungdan) "중간에 멈춘" else "마친") + " 기초 시험 ${bun}분. 위치 받은 횟수 $batun, 길눈이 말한 횟수 $mal, 걸음 $georeum, 통신 끊김 ${kkeunkimSu}번."
        Sori.mal(majimakGyeolgwa)
        Girok.namgi("gicho_kkeut", sangtae() + mapOf("bun" to bun, "jungdan" to jungdan, "batunSu" to batun, "malSu" to mal, "kkeunkimSu" to kkeunkimSu))
        Girok.jeojang()
        Girok.bonaegi()
        byeonhwa?.invoke()
        byeonhwa = null   // 화면을 붙잡아 두지 않게(화면이 다시 열리면 다시 채움)
    }

    private fun sangtae(): Map<String, Any?> {
        val c = ctx
        val d = HashMap<String, Any?>()
        if (c != null) {
            try {
                val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
                d["hwamyeonKkeojim"] = !pm.isInteractive
                val km = c.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                d["jamgim"] = km.isKeyguardLocked
                val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                d["eumak"] = am.isMusicActive
                val acc = c.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
                d["tokbaek"] = acc.isTouchExplorationEnabled
            } catch (e: Exception) {}
        }
        d["tongsin"] = Tongsin.yeongyeol
        d["heorak"] = Wichi.heorakItda
        d["georeum"] = Wichi.oneulGeoreum
        d["batun"] = Wichi.batunSu
        val j = Wichi.jigeum
        if (j != null) {
            d["ochae"] = j.ochae.toInt()
            d["chu"] = j.georeumChu
            d["wichiCho"] = (System.currentTimeMillis() - j.ttae) / 1000
        }
        return d
    }
}
