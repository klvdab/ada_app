package kr.or.ada.app.gilnun

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import java.lang.ref.WeakReference

/**
 * 2.77.0 (261025-A65, 이사장님 승인 2026-10-25 「1」, 아이폰 2.110.0과 같음) 위치·마이크 허락 알림과 설정 단추.
 *
 * 폰은 허락을 사람만 바꿀 수 있게 막아 두어, 길눈이 스스로 켤 수 없습니다.
 * 그래서 길눈이 먼저 알려 드리고, 단추 하나로 폰 설정의 길눈 화면을 바로 엽니다.
 *  - 처음 여실 때 위치 허락이 모자라면 한 번 알려 드림
 *  - 그 뒤로는 허락이 나빠졌을 때만 한 번(같은 상태로 열 때마다 되풀이하지 않음)
 *  - 설정 탭 맨 위에 지금 상태를 이름에 담은 단추 두 개(위치, 마이크)
 *  - 위치가 꺼져 있으면 길 안내를 시작하지 않음
 * (10-25 저녁 아이폰에서 위치가 「거절됨」인 채 한 시간 길 안내를 하신 일)
 */
object HeorakAllim {
    var hwalseong: WeakReference<GilnunActivity>? = null
    private const val JEON_KI = "gn.wichiHeorakJeon"
    private const val CHEONG_KI = "gn.wichiHeorakCheongHam"

    /** 위치 허락을 말로 */
    fun wichiMal(ctx: Context): String {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm != null && !LocationManagerCompat.isLocationEnabled(lm)) return "폰 위치 꺼짐"
        return when {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> "허락됨"
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED -> "대략적 위치만 허락"
            ctx.getSharedPreferences("gilnun", Context.MODE_PRIVATE).getBoolean(CHEONG_KI, false) -> "거절됨"
            else -> "아직 묻지 않음"
        }
    }

    fun maikMal(ctx: Context): String =
        "마이크 " + (if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) "켜짐" else "꺼짐")

    /** 높을수록 좋음 — 허락됨 3, 대략적 위치만 2, 아직 묻지 않음 1, 거절·꺼짐 0 */
    private fun deunggeup(m: String): Int = when (m) {
        "허락됨" -> 3
        "대략적 위치만 허락" -> 2
        "아직 묻지 않음" -> 1
        else -> 0
    }

    /** 화면이 앞으로 올 때마다 — 알릴 일이 있으면 한 번 알림 */
    fun salpigi(a: GilnunActivity) {
        hwalseong = WeakReference(a)
        val jigeum = wichiMal(a)
        if (jigeum == "아직 묻지 않음") return   // 처음 허락 창이 곧 뜸
        val sp = a.getSharedPreferences("gilnun", Context.MODE_PRIVATE)
        val jeon = sp.getString(JEON_KI, null)
        sp.edit().putString(JEON_KI, jigeum).apply()
        val d = deunggeup(jigeum)
        if (d >= 3) return
        if (jeon != null && deunggeup(jeon) <= d) return
        val geul = when (jigeum) {
            "폰 위치 꺼짐" -> "폰의 위치가 꺼져 있습니다. 이대로는 지금 자리를 알 수 없어 길 안내를 할 수 없습니다. 설정 열기를 누르시면 폰의 위치 설정 화면이 바로 열립니다. 위치를 켜 주십시오."
            "대략적 위치만 허락" -> "길눈의 위치 허락이 대략적 위치로만 되어 있습니다. 걸어서 길 안내를 하려면 정확한 위치가 필요합니다. 설정 열기를 누르시면 폰 설정의 길눈 화면이 바로 열립니다. 권한, 위치에서 정확한 위치 사용을 켜 주십시오."
            else -> "길눈의 위치 허락이 꺼져 있습니다. 이대로는 지금 자리를 알 수 없어 길 안내를 할 수 없습니다. 설정 열기를 누르시면 폰 설정의 길눈 화면이 바로 열립니다. 권한, 위치에서 앱 사용 중에만 허용을 골라 주십시오."
        }
        Girok.namgi("heorak_allim", mapOf("wichi" to jigeum, "jeon" to (jeon ?: "")))
        allimChang(a, geul)
    }

    private fun allimChang(a: GilnunActivity, geul: String) {
        val tokbaek = (a.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)?.isTouchExplorationEnabled == true
        if (!tokbaek) Sori.mal(geul)   // 톡백이 창을 읽으므로 두 번 듣지 않게
        try {
            AlertDialog.Builder(a)
                .setTitle("위치 허락")
                .setMessage(geul)
                .setPositiveButton("설정 열기") { _, _ -> wichiSeoljeongYeolgi(a) }
                .setNegativeButton("나중에", null)
                .show()
        } catch (e: Exception) {
            Girok.namgi("heorak_allim_oryu", mapOf("e" to (e.message ?: "")))
        }
    }

    /** 위치가 꺼져 있으면 참 — 까닭과 설정 단추를 알리고 길 안내를 시작하지 않음 */
    fun annaeMakgi(): Boolean {
        val a = hwalseong?.get() ?: return false
        val m = wichiMal(a)
        if (deunggeup(m) > 1) return false   // 허락이 없으면(거절·꺼짐·아직 못 받음) 시작하지 않음
        Girok.namgi("annae_wichi_eopseum", mapOf("heorak" to m))
        allimChang(a, "위치가 꺼져 있어 길 안내를 시작하지 않았습니다. 설정 열기를 누르시면 " +
            (if (m == "폰 위치 꺼짐") "폰의 위치 설정 화면이 바로 열립니다. 위치를 켜 주십시오." else "폰 설정의 길눈 화면이 바로 열립니다. 권한, 위치에서 허용해 주십시오."))
        return true
    }

    /** 위치 설정 열기 — 아직 한 번도 여쭙지 않았으면 허락 창을 먼저, 폰 위치가 꺼졌으면 위치 설정 화면, 그 밖에는 앱 설정 화면 */
    fun wichiSeoljeongYeolgi(a: GilnunActivity) {
        val m = wichiMal(a)
        Girok.namgi("heorak_seoljeong", mapOf("jong" to "wichi", "jigeum" to m))
        val sp = a.getSharedPreferences("gilnun", Context.MODE_PRIVATE)
        if (m == "아직 묻지 않음") {
            sp.edit().putBoolean(CHEONG_KI, true).apply()
            a.wichiHeorakCheong()
            return
        }
        try {
            if (m == "폰 위치 꺼짐") a.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            else appSeoljeongYeolgi(a)
        } catch (e: Exception) {
            appSeoljeongYeolgi(a)
        }
    }

    fun maikSeoljeongYeolgi(a: GilnunActivity) {
        Girok.namgi("heorak_seoljeong", mapOf("jong" to "maik", "jigeum" to maikMal(a)))
        appSeoljeongYeolgi(a)
    }

    fun appSeoljeongYeolgi(a: Context) {
        try {
            a.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Girok.namgi("heorak_seoljeong_oryu", mapOf("e" to (e.message ?: "")))
        }
    }
}
