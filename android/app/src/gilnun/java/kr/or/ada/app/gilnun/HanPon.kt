// 2.32.0 (261009-A20, 이사장님 승인 2026-10-09) 한 폰 표(gd) — 같은 폰에 길눈과 자봉 앱을 함께 둔 분이
// 내 길눈의 긴급통화 요청에 내 자봉 앱이 울리지 않게, 두 앱이 같은 값을 냄.
// ANDROID_ID(같은 열쇠로 서명한 앱끼리 한 폰·한 사용자에서 같음)에 "klvdab-gd" 를 섞은 SHA-256 앞 16자.
// 길눈(GinGeup a=call)과 자봉(JabongDaegi a=daegi)이 함께 씀 — 자봉 갈래도 src/gilnun 을 싣습니다.
package kr.or.ada.app.gilnun

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

object HanPon {
    @Volatile private var gap: String? = null

    /** 못 얻으면 빈 글 — 빈 글이면 보내지도 견주지도 않음 */
    fun gd(c: Context?): String {
        gap?.let { return it }
        val cc = c ?: return ""
        val id = try { Settings.Secure.getString(cc.applicationContext.contentResolver, Settings.Secure.ANDROID_ID) ?: "" } catch (e: Exception) { "" }
        if (id.isEmpty()) return ""
        val h = MessageDigest.getInstance("SHA-256").digest(("klvdab-gd$id").toByteArray(Charsets.UTF_8))
        val v = h.joinToString("") { "%02x".format(it) }.take(16)
        gap = v
        return v
    }
}
