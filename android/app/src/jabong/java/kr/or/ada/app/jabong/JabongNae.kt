// 안드로이드 자봉 — 등록 정보 (2.3.0, 빌드 261002-J1 — 아이폰 JabongNae.swift 2.0.0과 같음)
// 웹 자봉과 같은 나스 창고(/jabong/deung.php)에 모임. 폰에는 자봉 번호·이름·지역·1365 아이디만 기억하고,
// 네 자리 숫자는 새로고침 때 쓰려고 따로 담아 둠(아이폰 열쇠 곳간 Yeolsoe "jbJam" 자리).
package kr.or.ada.app.jabong

import android.content.Context
import android.content.SharedPreferences
import kr.or.ada.app.gilnun.Girok
import org.json.JSONObject

object JabongNae {
    private var d: SharedPreferences? = null
    private var yeolsoe: SharedPreferences? = null
    /** 등록 정보가 바뀌면 화면이 채움 */
    var byeonhwa: (() -> Unit)? = null

    fun sijak(c: Context) {
        if (d != null) return
        d = c.applicationContext.getSharedPreferences("jabong", Context.MODE_PRIVATE)
        yeolsoe = c.applicationContext.getSharedPreferences("jabong_yeolsoe", Context.MODE_PRIVATE)
    }

    val beonho: String get() = d?.getString("jb.beonho", "") ?: ""
    /** 2.7.0 걸음 나눔에 쓴 글을 나중에 가릴 때 쓰는 네 자리 숫자(등록 때 정하신 것) */
    val jamGap: String get() = yeolsoe?.getString("jbJam", "") ?: ""
    val ireum: String get() = d?.getString("jb.ireum", "") ?: ""
    val jiyeok: String get() = d?.getString("jb.jiyeok", "") ?: ""
    val id1365: String get() = d?.getString("jb.id1365", "") ?: ""
    /** 그려 준 길 수(내 발자취) */
    val geurinSu: Int get() = d?.getInt("jb.geurinSu", 0) ?: 0
    val deungrokham: Boolean get() = beonho.isNotEmpty()

    private fun dameum(b: String, i: String, j: String, id: String) {
        d?.edit()?.putString("jb.beonho", b)?.putString("jb.ireum", i)?.putString("jb.jiyeok", j)?.putString("jb.id1365", id)?.apply()
        byeonhwa?.invoke()
    }

    private fun suNoki(su: Int) { d?.edit()?.putInt("jb.geurinSu", su)?.apply() }

    /** 나스 등록 창고에 물음 */
    private fun mutgi(a: String, bon: JSONObject, kkeut: (JSONObject?) -> Unit) {
        JbTongsin.postJson("https://lvd.ada.or.kr/jabong/deung.php", mapOf("a" to a), bon.toString(), kkeut)
    }

    /** 처음 등록 — 성공하면 null, 안 되면 까닭 */
    fun deungrok(ireum: String, yeonrak: String, jiyeok: String, id1365: String, jam: String, kkeut: (String?) -> Unit) {
        val bon = JSONObject().put("ireum", ireum).put("yeonrak", yeonrak).put("jiyeok", jiyeok).put("id1365", id1365).put("jam", jam).put("gyoyuk", true)
        mutgi("sin", bon) { j ->
            if (j == null) { kkeut("통신이 닿지 않았습니다. 잠시 뒤 다시 눌러 주십시오."); return@mutgi }
            val b = j.optString("beonho", "")
            if (!j.optBoolean("ok", false) || b.isEmpty()) { kkeut(j.optString("msg", "").ifEmpty { "등록하지 못했습니다." }); return@mutgi }
            yeolsoe?.edit()?.putString("jbJam", jam)?.apply()
            dameum(b, ireum, jiyeok, id1365)
            Girok.namgi("jabong_deungrok", mapOf("beonho" to b))
            kkeut(null)
        }
    }

    /** 웹에서 이미 등록하신 분 — 자봉 번호와 네 자리 숫자로 이어서 씀 */
    fun ieoSseugi(beonho0: String, jam: String, kkeut: (String?) -> Unit) {
        val b = beonho0.uppercase().trim()
        mutgi("nae", JSONObject().put("beonho", b).put("jam", jam)) { j ->
            if (j == null) { kkeut("통신이 닿지 않았습니다. 잠시 뒤 다시 눌러 주십시오."); return@mutgi }
            if (!j.optBoolean("ok", false)) { kkeut(j.optString("msg", "").ifEmpty { "찾지 못했습니다." }); return@mutgi }
            val su = j.optJSONArray("girok")?.length() ?: 0
            yeolsoe?.edit()?.putString("jbJam", jam)?.apply()
            suNoki(su)
            dameum(b, j.optString("ireum", ""), j.optString("jiyeok", ""), j.optString("id1365", ""))
            kkeut(null)
        }
    }

    /** 등록 정보를 나스에서 다시 받아 봄(새로고침) — 받았는가 */
    fun dasiBatgi(kkeut: (Boolean) -> Unit) {
        val jam = yeolsoe?.getString("jbJam", null)
        if (!deungrokham || jam.isNullOrEmpty()) { kkeut(false); return }
        mutgi("nae", JSONObject().put("beonho", beonho).put("jam", jam)) { j ->
            if (j == null || !j.optBoolean("ok", false)) { kkeut(false); return@mutgi }
            val su = j.optJSONArray("girok")?.length() ?: geurinSu
            suNoki(su)
            dameum(beonho, j.optString("ireum", "").ifEmpty { ireum }, j.optString("jiyeok", "").ifEmpty { jiyeok }, j.optString("id1365", "").ifEmpty { id1365 })
            kkeut(true)
        }
    }

    /** 이 폰에서 등록을 지움(나스 기록은 그대로) */
    fun ijeugi() {
        dameum("", "", "", "")
    }
}
