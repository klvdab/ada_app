// 안드로이드 자봉 — 보완 부탁 2.13.0 (빌드 261007-A9, 대장클, 아이폰 JabongBowan.swift 와 같음, 이사장님 지시 2026-10-06·07)
// 올린 점지도가 협회 점검에서 흠이 나오면 「조금만 더 보완해 주세요. 시각장애인이 기다립니다」로 보완을 부탁.
// 3일째 다시 알리고, 7일이 지나도 보완이 없으면 협회 보완팀이 맡음(그린 분 이름은 그대로, 보완한 분을 더함).
// 날짜 셈은 협회 리눅스 서버(lvd-bowan), 나스(bowan.php)는 건네기만. 같은 출발지·도착지로 다시 걸어 점검을 통과하면 보완 완료.
// 고칠 곳은 몇 걸음째·무엇·어떻게를 말과 빛깔(빨강·주황·노랑)로, 빨강은 깜박여 눈에 띄게.
package kr.or.ada.app.jabong

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kr.or.ada.app.gilnun.MalGeup
import kr.or.ada.app.gilnun.Sori
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object JbBowan {
    const val BUTAK = "조금만 더 보완해 주세요. 시각장애인이 기다립니다."
    /** 내 보완 부탁(완료 뺌) — 서버가 단계(보완 대기·다시 알림·보완팀)를 붙여 줌 */
    var mok: List<JSONObject> = emptyList(); private set
    var allim = ""; private set
    private var majimak = 0L
    private var jjok = 0   // 261019 이사장님 승인 「1」 — 보완 부탁 목록 몇째 쪽(다섯 건씩)
    private var neomgim = false   // 더 보기·이전 보기를 누른 뒤에만 커서를 첫 줄로(탭을 열 때마다 끌어가지 않게)

    fun ireum(g: JSONObject): String {
        val ti = g.optString("title")
        if (ti.isNotEmpty()) return ti
        val f = g.optString("from"); val t = g.optString("to")
        return if (f.isEmpty() && t.isEmpty()) "이름 없는 길" else "$f → $t"
    }

    /** 2026-10-13 → 10월 13일 */
    fun nalMal(s: String): String {
        val p = s.split("-").mapNotNull { it.toIntOrNull() }
        return if (p.size == 3) "${p[1]}월 ${p[2]}일" else s
    }

    fun saek(s: String): Int = when (s) {
        "빨강" -> Color.rgb(204, 20, 20)
        "주황" -> Color.rgb(230, 115, 0)
        "노랑" -> Color.rgb(217, 178, 0)
        else -> Color.DKGRAY
    }

    /** 협회에 내 보완 부탁을 물음 — 켤 때와 알림 탭을 열 때(1분에 한 번까지) */
    fun bulleo(c: Context, gangje: Boolean = false, kkeut: (() -> Unit)? = null) {
        if (!gangje && System.currentTimeMillis() - majimak < 60_000) return
        if (!JabongNae.deungrokham || JabongNae.jamGap.isEmpty()) return
        majimak = System.currentTimeMillis()
        val bon = JSONObject().put("beonho", JabongNae.beonho).put("jam", JabongNae.jamGap)
        JbTongsin.postJson("https://lvd.ada.or.kr/jeom/bowan.php", mapOf("a" to "nae"), bon.toString()) { j ->
            if (j == null) allim = "보완 부탁을 살피지 못했습니다. 통신을 확인해 주십시오."
            else if (!j.optBoolean("ok", false)) allim = j.optString("msg", "보완 부탁을 살피지 못했습니다.")
            else {
                allim = ""
                val a = j.optJSONArray("mok")
                mok = (0 until (a?.length() ?: 0)).mapNotNull { a?.optJSONObject(it) }.filter { it.optString("dangye") != "보완 완료" }
                dasiAllim(c)
            }
            kkeut?.invoke()
        }
    }

    /** 3일째(다시 알림)인 길은 하루에 한 번 말로 알려 드림 */
    private fun dasiAllim(c: Context) {
        val oneul = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())
        val d = c.getSharedPreferences("jabong", Context.MODE_PRIVATE)
        val ki = "jb.bowanAllim.$oneul"
        val ap = d.getString(ki, "") ?: ""
        val dasi = mok.filter { it.optString("dangye") == "다시 알림" && !ap.contains(it.optString("id")) }
        if (dasi.isEmpty()) return
        d.edit().putString(ki, ap + dasi.joinToString(",") { it.optString("id") }).apply()
        Sori.mal("$BUTAK 보완 부탁 ${dasi.size}건이 3일을 넘었습니다. 알림·설정 탭 맨 위에서 고칠 곳을 들으실 수 있습니다.", MalGeup.ANNAE)
    }

    /** 고칠 곳 말로 듣기 — 몇 걸음째, 무엇, 어떻게 */
    fun malHagi(g: JSONObject) {
        Sori.meomchugi()
        Sori.mal(ireum(g) + ". " + g.optString("mal", BUTAK))
        val h = g.optJSONArray("heum")
        for (i in 0 until (h?.length() ?: 0)) { val x = h?.optJSONObject(i) ?: continue; Sori.mal("${i + 1}. ${x.optString("saek", "빨강")}. ${x.optString("mal")}") }
    }

    /** 고칠 곳 한 줄 — 빛깔 띠와 「빨강 —」 글자(톡백으로도 빛깔을 들음), 빨강은 깜박임 */
    fun heumJul(t: JabongActivity, sk: String, mal: String) {
        val jul = LinearLayout(t).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(t.dp(10), t.dp(10), t.dp(10), t.dp(10))
            background = GradientDrawable().apply { setColor(Color.argb(30, Color.red(saek(sk)), Color.green(saek(sk)), Color.blue(saek(sk)))); cornerRadius = t.dp(10).toFloat() }
            isFocusable = true
            contentDescription = "$sk — $mal"
        }
        val tti = View(t).apply {
            background = GradientDrawable().apply { setColor(saek(sk)); cornerRadius = t.dp(4).toFloat() }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        jul.addView(tti, LinearLayout.LayoutParams(t.dp(12), LinearLayout.LayoutParams.MATCH_PARENT).apply { marginEnd = t.dp(10) })
        val gl = TextView(t).apply {
            text = "$sk — $mal"; setTextColor(Color.BLACK); setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        jul.addView(gl, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (sk == "빨강" && ValueAnimator.areAnimatorsEnabled()) {
            ObjectAnimator.ofFloat(tti, "alpha", 1f, 0.25f).apply { duration = 700; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE; start() }
        }
        t.bogi(jul)
    }

    /** 알림·설정 탭 맨 위 — 내 보완 부탁(있을 때만) */
    fun kan(t: JabongActivity) {
        bulleo(t) { t.dasiGeurigi() }
        if (allim.isNotEmpty()) t.geul(allim)
        if (mok.isEmpty()) return
        t.keunGeul("보완 부탁 ${mok.size}건 — $BUTAK", 20f, saek("빨강"))
        // 261019 이사장님 승인 「1」 — 다섯 건씩, 그린 길 목록처럼 아래에 더 보기, 그 아래에 이전 보기. 넘기면 커서를 첫 줄로
        if (jjok * 5 >= mok.size) jjok = (mok.size - 1) / 5
        val sijak = jjok * 5
        for ((i, g) in mok.drop(sijak).take(5).withIndex()) {
            val gh = g.optString("gihan")
            val v = t.danchu(ireum(g) + " — " + g.optString("dangye", "보완 대기") + if (gh.isNotEmpty()) ", 기한 " + nalMal(gh) else "") { t.yeolgi(BowanGilHwamyeon(g)) }
            if (i == 0 && neomgim) t.chojeom(v)
        }
        neomgim = false
        if (sijak + 5 < mok.size) t.danchu("더 보기") { jjok += 1; neomgim = true; t.dasiGeurigi() }
        if (jjok > 0) t.danchu("이전 보기") { jjok -= 1; neomgim = true; t.dasiGeurigi() }
    }
}

/** 보완 부탁 한 건 — 무엇을 어떻게 고칠지 */
class BowanGilHwamyeon(private val g: JSONObject) : JbHwamyeon("보완 부탁") {
    override fun chaeugi(t: JabongActivity) {
        // 2.22.0 보완하기(직원 시험 뒤 이사장님 지시 2026-10-14, 아이폰 2.21.0과 같음) — 출발지·도착지를 이 길과 같게 넣고 점지도 그리기로
        val from = g.optString("from"); val to = g.optString("to")
        if (from.isNotEmpty() && to.isNotEmpty()) t.danchu("보완하기 — ${from}에서 ${to}까지 다시 그리기") {
            if (Geurigi.sangtae != Geurigi.Sangtae.SWIM) { Sori.mal("그리는 중인 길이 있습니다. 그 길을 먼저 마치거나 그만두신 뒤 다시 눌러 주십시오."); return@danchu }
            Geurigi.bowanJeonghagi(g.optString("id"), from, to)
            Sori.mal("고맙습니다. 그리기 탭의 점지도 그리기에서 ${from}부터 ${to}까지 다시 걸어 주십시오. 걷기 시작을 누르시면 출발지와 도착지 이름이 이 길과 같게 들어갑니다. 올려서 협회 점검을 통과하면 보완 완료가 됩니다.")
            t.tabGo(0)
        }
        t.danchu("고칠 곳 말로 듣기") { JbBowan.malHagi(g) }
        // 2.23.0 고칠 곳에 맞는 동영상(꺾임·걸음·짝·문·목소리는 2편, 나머지는 4편)
        val hm = g.optJSONArray("heum")
        val pn = JbMadang.doumPyeon((0 until (hm?.length() ?: 0)).map { hm?.optJSONObject(it)?.optString("mal") ?: "" })
        t.danchu("도움 되는 동영상 — ${JbMadang.pyeon(pn)?.ireum ?: "${pn}편"}") { JbMadang.teulgi(t, pn) }
        t.geul(g.optString("mal", JbBowan.BUTAK), true)
        t.geul("보완 부탁 날 " + JbBowan.nalMal(g.optString("ttae")) + if (g.has("nal")) ", ${g.optInt("nal")}일 지남" else "")
        val h = g.optJSONArray("heum")
        for (i in 0 until (h?.length() ?: 0)) { val x = h?.optJSONObject(i) ?: continue; JbBowan.heumJul(t, x.optString("saek", "빨강"), x.optString("mal")) }
        t.geul("고치는 법: 같은 출발지와 도착지로 다시 걸어 그리고 올려 주십시오. 협회 점검을 통과하면 이 부탁은 저절로 보완 완료가 됩니다. 이름만 빠진 것은 그린 길 화면에서 이름을 넣고 다시 올리시면 됩니다.")
    }
}
