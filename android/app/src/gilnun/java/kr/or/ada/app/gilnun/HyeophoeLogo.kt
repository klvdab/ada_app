// 협회 로고 — 길눈·자봉이 함께 씀 (판 1.0.0, 빌드 261007-A4, 대장클)
// 이사장님 지시 2026-10-07 「우리가 작성하는 모든 프로그램에 그 로고를 꼭 넣어서」 — AI점자도서관 앱(lib/Kkumim.kt LogoView)과 같은 그림.
// 점자 lvd(위 줄) + 빨강 L·노랑 V·초록 D(아래 줄). 원본과 같은 자리·크기로 그림.
package kr.or.ada.app.gilnun

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

class HyeophoeLogo(c: Context, private val jeom: Int = Color.rgb(247, 231, 180)) : View(c) {
    private val pt = Paint(Paint.ANTI_ALIAS_FLAG)
    init { contentDescription = "사단법인 한국시각장애인현장영상해설협회 로고, 점자 lvd와 영문 LVD" }
    override fun onMeasure(w: Int, h: Int) { val W = MeasureSpec.getSize(w); setMeasuredDimension(W, (W * 162f / 205f).toInt()) }
    override fun onDraw(cv: Canvas) {
        val s = minOf(width / 205f, height / 162f)
        val ox = (width - 205 * s) / 2; val oy = (height - 162 * s) / 2
        fun x(v: Float): Float = ox + v * s
        fun y(v: Float): Float = oy + v * s
        pt.color = jeom
        for ((a, b) in listOf(33.9f to 28.5f, 33.9f to 46.7f, 33.9f to 65f, 88.8f to 28.5f, 88.8f to 46.7f, 88.7f to 65f, 107.9f to 65f, 148.6f to 28.6f, 167.6f to 28.5f, 167.6f to 46.8f))
            cv.drawCircle(x(a), y(b), 8.4f * s, pt)
        pt.color = Color.rgb(215, 22, 24)
        cv.drawPath(Path().apply { moveTo(x(26f), y(80f)); lineTo(x(37f), y(80f)); lineTo(x(37f), y(131f)); lineTo(x(67f), y(131f)); lineTo(x(67f), y(142f)); lineTo(x(26f), y(142f)); close() }, pt)
        pt.color = Color.rgb(244, 199, 32)
        cv.drawPath(Path().apply { moveTo(x(66f), y(80f)); lineTo(x(79f), y(80f)); lineTo(x(94.5f), y(126f)); lineTo(x(111f), y(80f)); lineTo(x(124f), y(80f)); lineTo(x(101f), y(142f)); lineTo(x(88f), y(142f)); close() }, pt)
        pt.color = Color.rgb(59, 179, 93)
        val d = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            moveTo(x(135f), y(80f)); lineTo(x(155f), y(80f)); arcTo(RectF(x(124f), y(80f), x(186f), y(142f)), -90f, 180f); lineTo(x(135f), y(142f)); close()
            moveTo(x(146f), y(91f)); lineTo(x(155f), y(91f)); arcTo(RectF(x(135f), y(91f), x(175f), y(131f)), -90f, 180f); lineTo(x(146f), y(131f)); close()
        }
        cv.drawPath(d, pt)
    }

    companion object {
        /** 첫 화면 머리 — 남색 띠 위에 로고와 앱 이름(도서관 앱 머리와 같은 꼴). 톡백은 한 줄로 읽음 */
        fun meori(c: Context, ireum: String, nam: Int = Color.rgb(18, 52, 110)): LinearLayout {
            fun dp(v: Int) = (v * c.resources.displayMetrics.density).toInt()
            val box = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = GradientDrawable().apply { setColor(nam); cornerRadius = dp(18).toFloat() }
                contentDescription = "$ireum. 주관 사단법인 한국시각장애인현장영상해설협회"
                isFocusable = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }
            val logo = HyeophoeLogo(c).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
            box.addView(logo, LinearLayout.LayoutParams(dp(84), LinearLayout.LayoutParams.WRAP_CONTENT))
            val geul = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
            geul.addView(TextView(c).apply { text = ireum; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f); typeface = Typeface.DEFAULT_BOLD })
            geul.addView(TextView(c).apply { text = "주관 사단법인 한국시각장애인현장영상해설협회"; setTextColor(Color.rgb(247, 231, 180)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f) })
            box.addView(geul, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            return box
        }
    }
}
