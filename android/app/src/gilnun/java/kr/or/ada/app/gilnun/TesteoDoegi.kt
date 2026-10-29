package kr.or.ada.app.gilnun

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * 2.87.0 (261029-A75, 이사장님 승인 2026-10-29 「1」) 구글 플레이 시험 참가(테스터 되기) 여쭙기 — 길눈·자봉이 함께 씀.
 *
 * 구글은 「테스터 되기」를 누른 분만 시험 참가자로 세어, 12명이 14일을 채워야 정식 출시를 허락합니다.
 * 협회 홈페이지의 앱 파일(apk)로 깔면 「테스터 되기」를 거치지 않아 셈에 들지 않습니다.
 *  - 구글 플레이에서 깔린 앱이면(이미 테스터) 묻지 않음
 *  - 그 밖이면 앱을 켤 때 한 번 여쭘. 「네」면 구글 시험 참가 화면을 엶 — 「테스터 되기」는 구글 화면에서 그분이 눌러야 함(앱이 대신 누를 수 없음, 구글 규정)
 *  - 「나중에」면 사흘 뒤 한 번 더, 그 뒤로는 묻지 않음(자주 귀찮게 하지 않게)
 */
object TesteoDoegi {
    private const val SP = "testeo_doegi"
    private const val K_NE = "ne"
    private const val K_NAJUNG_TTAE = "najung_ttae"
    private const val K_NAJUNG_SU = "najung_su"
    private const val SAHEUL = 3L * 24 * 3600 * 1000
    private var ibeonMureum = false

    /** 구글 플레이(또는 구글이 넣은 설치)로 깔렸으면 참 */
    @Suppress("DEPRECATION")
    fun peulleiEseo(ctx: Context): Boolean = try {
        val p = ctx.packageName
        val gun: String? = if (Build.VERSION.SDK_INT >= 30) ctx.packageManager.getInstallSourceInfo(p).installingPackageName
                           else ctx.packageManager.getInstallerPackageName(p)
        gun == "com.android.vending" || gun == "com.google.android.feedback"
    } catch (e: Exception) { false }

    /** 이 앱의 구글 시험 참가 화면 주소 */
    fun juso(ctx: Context): String = "https://play.google.com/apps/testing/" + ctx.packageName

    /** 앱이 앞에 올 때 — 여쭐 때가 되었으면 한 번 여쭘. makgi 가 참이면(보기: 점지도 그리는 중) 이번에는 묻지 않음 */
    fun salpigi(a: Activity, appIreum: String, makgi: () -> Boolean = { false }) {
        if (ibeonMureum || a.isFinishing) return
        if (peulleiEseo(a)) return
        val sp = a.getSharedPreferences(SP, Context.MODE_PRIVATE)
        if (sp.getBoolean(K_NE, false)) return
        val su = sp.getInt(K_NAJUNG_SU, 0)
        if (su >= 2) return
        if (su == 1 && System.currentTimeMillis() - sp.getLong(K_NAJUNG_TTAE, 0L) < SAHEUL) return
        if (makgi()) return
        ibeonMureum = true
        val geul = appIreum + " 앱은 지금 구글 정식 출시 전 시험 중입니다. 시험 참가(테스터 되기)에 동의하십니까?\n\n" +
            "네를 누르시면 구글의 시험 참가 화면이 열립니다. 거기서 「테스터 되기」를 한 번 눌러 주시면 됩니다. 앱은 이미 깔려 있으니 다시 깔지 않으셔도 됩니다.\n\n" +
            "폰의 플레이 스토어가 신청하신 구글 메일로 로그인되어 있어야 「테스터 되기」가 보입니다."
        Girok.namgi("testeo_mureum", mapOf("beonjjae" to su + 1))
        try {
            AlertDialog.Builder(a)
                .setTitle("구글 시험 참가")
                .setMessage(geul)
                .setPositiveButton("네") { _, _ ->
                    sp.edit().putBoolean(K_NE, true).apply()
                    Girok.namgi("testeo_ne")
                    yeolgi(a)
                }
                .setNegativeButton("나중에") { _, _ ->
                    sp.edit().putInt(K_NAJUNG_SU, su + 1).putLong(K_NAJUNG_TTAE, System.currentTimeMillis()).apply()
                    Girok.namgi("testeo_najung", mapOf("su" to su + 1))
                }
                .setCancelable(false)
                .show()
        } catch (e: Exception) {
            ibeonMureum = false
            Girok.namgi("testeo_oryu", mapOf("e" to (e.message ?: "")))
        }
    }

    /** 구글 시험 참가 화면 열기 */
    fun yeolgi(a: Activity) {
        try {
            a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(juso(a))))
        } catch (e: Exception) {
            Girok.namgi("testeo_yeolgi_oryu", mapOf("e" to (e.message ?: "")))
        }
    }
}
