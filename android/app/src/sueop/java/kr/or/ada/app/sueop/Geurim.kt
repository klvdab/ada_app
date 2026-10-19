// LVD 수업 안드로이드 — 자료 그림과 빨간 테두리, 크게 보기 (0.1.0판, 빌드 261020-S1, 방클, 이사장님 승인 2026-10-20 「단독으로 가자」)
// 빨간 테두리 자리는 서버 값(그림 가로·세로를 1000으로 본 gx·gy·gw·gh)을 그림이 실제로 그려진 자리에 맞춰 그림(크게 보기에서 키워도 따라감).
package kr.or.ada.app.sueop

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView

/** 자료 그림 — 그 위에 빨간 테두리 */
open class GariImage(c: Context) : ImageView(c) {
    /** 0~1000 자리(가로 x, 세로 y, 너비 w, 높이 h). 없으면 null */
    var gari: RectF? = null
        set(v) { field = v; invalidate() }
    private val pt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.rgb(255, 40, 40)
        strokeWidth = 4f * c.resources.displayMetrics.density
    }

    override fun onDraw(cv: Canvas) {
        super.onDraw(cv)
        val g = gari ?: return
        val d = drawable ?: return
        val iw = d.intrinsicWidth.toFloat(); val ih = d.intrinsicHeight.toFloat()
        if (iw <= 0 || ih <= 0) return
        val r = RectF(g.left * iw / 1000f, g.top * ih / 1000f, (g.left + g.right) * iw / 1000f, (g.top + g.bottom) * ih / 1000f)
        imageMatrix.mapRect(r)
        r.offset(paddingLeft.toFloat(), paddingTop.toFloat())
        cv.drawRect(r, pt)
    }
}

/** 크게 보기 — 두 손가락으로 키우고 줄이기, 두 번 두드리면 두 배·제자리, 끌어서 옮기기 */
class KeugeImage(c: Context) : GariImage(c) {
    private val m = Matrix()
    private var bae = 1f
    private var kiwoom = false

    private val sg = ScaleGestureDetector(c, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val n = (bae * d.scaleFactor).coerceIn(1f, 6f)
            val f = n / bae
            bae = n
            m.postScale(f, f, d.focusX, d.focusY)
            majchugi(); imageMatrix = m; invalidate()
            return true
        }
    })
    private val gd = GestureDetector(c, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (bae > 1.01f) jeJari() else { bae = 2f; m.postScale(2f, 2f, e.x, e.y); majchugi(); imageMatrix = m; invalidate() }
            return true
        }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (bae <= 1.01f) return false
            m.postTranslate(-dx, -dy); majchugi(); imageMatrix = m; invalidate()
            return true
        }
    })

    init { scaleType = ImageView.ScaleType.MATRIX }

    /** 그림이 화면 가운데 꽉 차게(제자리) */
    fun jeJari() {
        val d = drawable ?: return
        val vw = (width - paddingLeft - paddingRight).toFloat(); val vh = (height - paddingTop - paddingBottom).toFloat()
        val iw = d.intrinsicWidth.toFloat(); val ih = d.intrinsicHeight.toFloat()
        if (vw <= 0 || vh <= 0 || iw <= 0 || ih <= 0) return
        val s = minOf(vw / iw, vh / ih)
        m.reset(); m.postScale(s, s); m.postTranslate((vw - iw * s) / 2, (vh - ih * s) / 2)
        bae = 1f
        kiwoom = false   // 새 그림이 오면 다시 제자리로 맞추도록
        imageMatrix = m; invalidate()
    }

    /** 키운 그림이 화면 밖으로 너무 나가지 않게 */
    private fun majchugi() {
        val d = drawable ?: return
        val r = RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat()); m.mapRect(r)
        val vw = width.toFloat(); val vh = height.toFloat()
        var dx = 0f; var dy = 0f
        if (r.width() <= vw) dx = (vw - r.width()) / 2 - r.left else { if (r.left > 0) dx = -r.left; if (r.right < vw) dx = vw - r.right }
        if (r.height() <= vh) dy = (vh - r.height()) / 2 - r.top else { if (r.top > 0) dy = -r.top; if (r.bottom < vh) dy = vh - r.bottom }
        m.postTranslate(dx, dy)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { super.onSizeChanged(w, h, ow, oh); jeJari() }
    override fun setImageBitmap(bm: android.graphics.Bitmap?) { super.setImageBitmap(bm); post { if (!kiwoom) jeJari() } }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        kiwoom = true
        sg.onTouchEvent(e); gd.onTouchEvent(e)
        if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) kiwoom = bae > 1.01f
        return true
    }
}
