// LVD투어스튜디오 안드로이드 — 디자인 바탕 (1.0.0, 빌드 261012-T1) — 아이폰 Tour/Theme.swift 와 같은 색·로고(AI점자도서관 바탕을 그대로)
package kr.or.ada.app.tour

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

object Saek {
    val namsaek = Color.rgb(20, 33, 61)
    val geum = Color.rgb(247, 231, 180)
    val ppalgang = Color.rgb(215, 22, 24)
    val norang = Color.rgb(244, 199, 32)
    val chorok = Color.rgb(59, 179, 93)
    private val pyojiSaek = intArrayOf(Color.rgb(20, 33, 61), Color.rgb(117, 28, 41), Color.rgb(20, 84, 59), Color.rgb(85, 49, 137),
        Color.rgb(137, 59, 17), Color.rgb(17, 74, 117), Color.rgb(61, 61, 74), Color.rgb(107, 25, 81))
    fun eodum(c: Context) = (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    fun bada(c: Context) = if (eodum(c)) Color.rgb(14, 23, 48) else Color.rgb(248, 245, 239)
    fun kadeu(c: Context) = if (eodum(c)) Color.rgb(28, 42, 74) else Color.WHITE
    fun geulja(c: Context) = if (eodum(c)) Color.rgb(240, 240, 245) else Color.rgb(20, 24, 33)
    fun buGeulja(c: Context) = if (eodum(c)) Color.rgb(190, 196, 210) else Color.rgb(78, 84, 96)
    fun ganjo(c: Context) = if (eodum(c)) geum else namsaek
    fun pyoji(key: String): Int { var h = 0; for (ch in key) h = (h * 31 + ch.code) and 0x7fffffff; return pyojiSaek[h % pyojiSaek.size] }
}

/** 법인 로고 — 점자 lvd + 빨강·노랑·초록 LVD (원본과 같은 자리·크기) */
class LogoView(c: Context, private val jeom: Int = Saek.geum) : View(c) {
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
        pt.color = Saek.ppalgang
        cv.drawPath(Path().apply { moveTo(x(26f), y(80f)); lineTo(x(37f), y(80f)); lineTo(x(37f), y(131f)); lineTo(x(67f), y(131f)); lineTo(x(67f), y(142f)); lineTo(x(26f), y(142f)); close() }, pt)
        pt.color = Saek.norang
        cv.drawPath(Path().apply { moveTo(x(66f), y(80f)); lineTo(x(79f), y(80f)); lineTo(x(94.5f), y(126f)); lineTo(x(111f), y(80f)); lineTo(x(124f), y(80f)); lineTo(x(101f), y(142f)); lineTo(x(88f), y(142f)); close() }, pt)
        pt.color = Saek.chorok
        val d = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            moveTo(x(135f), y(80f)); lineTo(x(155f), y(80f)); arcTo(RectF(x(124f), y(80f), x(186f), y(142f)), -90f, 180f); lineTo(x(135f), y(142f)); close()
            moveTo(x(146f), y(91f)); lineTo(x(155f), y(91f)); arcTo(RectF(x(135f), y(91f), x(175f), y(131f)), -90f, 180f); lineTo(x(146f), y(131f)); close()
        }
        cv.drawPath(d, pt)
    }
}
