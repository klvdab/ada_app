// 안드로이드 길눈 — 현장영상해설 받기(2.7.0 묶음 b6_nanum, 아이폰 길눈 Haeseol.swift 2.14.0과 같은 세 갈래, 같은 말)
// 길눈이 데려다주는 데서 끝내지 않고, 그 자리에서 무엇을 보고 있는지까지 잇습니다.
// ① 지금 바로 현장영상해설사에게 화상통화(앱 자체 긴급통화서비스) ② 협회가 만들어 둔 현장영상해설 코스 듣기 ③ 현장영상해설사 파견 신청
// ②③은 협회 홈페이지 화면을 앱 안에서 엽니다(크롬 맞춤 탭 — 닫기나 폰의 뒤로 동작으로 길눈에 돌아옴. 아이폰 SFSafariViewController 자리).
// 이사장님 지시 — 우리가 다루는 해설은 모두 "현장영상해설", 해설사는 "현장영상해설사"로(긴급통화서비스의 "해설사"만 예외)
package kr.or.ada.app.gilnun

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/** 앱 안에서 웹 화면 열기 — 톡백으로 읽히고, 닫으면 길눈으로 돌아옴. 맞춤 탭을 못 열면 폰의 인터넷 앱으로 */
object HaeseolBogi {
    const val KOSEU = "https://lvd.ada.or.kr/lvdts/lvdts_hyeonjang.html"
    const val PAGYEON = "https://ada.or.kr/dispatch.html"

    fun yeolgi(c: Context, juso: String) {
        val u = Uri.parse(juso)
        try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setUrlBarHidingEnabled(false)
                .build()
                .launchUrl(c, u)
        } catch (e: Exception) {
            try {
                c.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e2: Exception) {
                Girok.namgi("haeseol_yeolgi_oryu", mapOf("e" to (e2.message ?: "").take(60)))
                Sori.mal("화면을 열지 못했습니다. 폰에 인터넷 앱이 있는지 확인해 주십시오.")
            }
        }
    }
}

class HaeseolBatgiHwamyeon : Hwamyeon("현장영상해설 받기") {
    private var ara = false
    override fun chaeugi(t: GilnunActivity) {
        t.danchu("지금 바로 현장영상해설사에게 화상통화 요청 — 현장영상해설사가 눈이 되어 드립니다") { hwasang(t) }
        t.danchu("준비된 현장영상해설 코스 듣기 — 협회가 만들어 둔 현장영상해설") {
            Girok.namgi("haeseol", mapOf("gil" to "koseu"))
            HaeseolBogi.yeolgi(t, HaeseolBogi.KOSEU)
        }
        t.danchu("현장영상해설사 파견 신청하기 — 날을 잡아 함께 다니실 현장영상해설사") {
            Girok.namgi("haeseol", mapOf("gil" to "pagyeon"))
            HaeseolBogi.yeolgi(t, HaeseolBogi.PAGYEON)
        }
        nnAraDul(t, ara, listOf(
            "가신 곳에서 무엇을 보고 계신지 현장영상해설로 이어 드립니다. 지금 바로 현장영상해설사에게 화상통화 요청을 누르시면 협회 현장영상해설사에게 화상통화를 청하고, 현장영상해설사가 폰 카메라로 보며 현장영상해설해 드립니다. 준비된 현장영상해설 코스 듣기는 협회가 만들어 둔 코스의 현장영상해설을 듣는 화면을, 현장영상해설사 파견 신청하기는 날을 잡아 현장영상해설사와 함께 다니시도록 신청하는 화면을 앱 안에서 엽니다. 다 보신 뒤 닫기 단추를 누르시거나 폰의 뒤로 동작을 하시면 길눈으로 돌아옵니다. 말로 하기에서 현장영상해설 받고 싶어라고 하셔도 이 화면이 열립니다."
        )) { ara = !ara }
    }

    companion object {
        /** 화상통화 — 긴급통화서비스로 현장영상해설사에게 요청하고 길 찾기 탭의 통화 화면을 엶(말로 하기에서도 씀) */
        fun hwasang(t: GilnunActivity) {
            Girok.namgi("haeseol", mapOf("gil" to "hwasang"))
            GinGeup.yocheong(GinGeupGalrae.HAESEOLSA)
            t.cheotHwamyeonEuro(GinGeupHwamyeon())
        }
    }
}
