// 갤럭시 워치 길눈 2.7.0(빌드 261007-W2, 대장클, 이사장님 허락 2026-10-07 22:45) — 폰 길눈이 보낸 동영상 틀기
// 아이폰 길눈의 애플워치 동영상 틀기와 같은 뜻: 카톡·문자로 받은 동영상을 폰 길눈에 공유하면 워치에서 틂.
//   파일은 폰이 채널로 통째로 보내고(GilnunWearService 가 받아 둠), 주소는 메시지로 와서 워치가 바로 받아 틂.
//   화면을 두드리면 멈춤과 다시 틀기, 아래 그만 단추. 다 틀면 짧게 떨고 닫힘. 트는 동안 화면이 꺼지지 않게.
package kr.or.ada.app.wear

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.VideoView
import java.io.File

class DongyeongWatchActivity : Activity() {
    companion object {
        const val JUSO = "juso"
        const val ALLIM_BEON = 27
    }

    private var vv: VideoView? = null
    private var meomchum = false
    private lateinit var danchu: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        try { (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager).cancel(ALLIM_BEON) } catch (e: Exception) {}
        val juso = intent?.getStringExtra(JUSO) ?: ""
        val bburi = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val v = VideoView(this)
        bburi.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        // 화면 전체가 멈춤·다시 틀기 단추(톡백으로도 한 번에)
        danchu = Button(this).apply {
            text = "멈춤"
            setTextColor(Color.TRANSPARENT)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "동영상 멈춤"
            setOnClickListener { meomchugiDasi() }
        }
        bburi.addView(danchu, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val geuman = Button(this).apply {
            text = "그만"
            textSize = 16f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.rgb(255, 204, 0))
            setOnClickListener { finish() }
        }
        bburi.addView(geuman, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = 12 })
        setContentView(bburi)
        vv = v
        if (juso.isEmpty() || (!juso.startsWith("http") && !File(juso).exists())) { finish(); return }
        v.setVideoURI(if (juso.startsWith("http")) Uri.parse(juso) else Uri.fromFile(File(juso)))
        v.setOnPreparedListener { it.start() }
        v.setOnCompletionListener {
            tteolgi()
            finish()
        }
        v.setOnErrorListener { _, _, _ ->
            danchu.contentDescription = "이 동영상은 워치에서 틀 수 없습니다. 그만을 누르십시오."
            danchu.announceForAccessibility("이 동영상은 워치에서 틀 수 없습니다. 폰에서 틀어 주십시오.")
            true
        }
    }

    private fun meomchugiDasi() {
        val v = vv ?: return
        if (meomchum) { v.start(); meomchum = false; danchu.contentDescription = "동영상 멈춤" }
        else { v.pause(); meomchum = true; danchu.contentDescription = "동영상 다시 틀기" }
    }

    private fun tteolgi() {
        try {
            val vb = getSystemService(VIBRATOR_SERVICE) as Vibrator
            vb.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        vv?.pause()
        meomchum = true
        danchu.contentDescription = "동영상 다시 틀기"
    }
}
