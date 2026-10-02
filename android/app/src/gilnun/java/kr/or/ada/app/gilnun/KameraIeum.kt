// 안드로이드 길눈 — 카메라 눈을 다른 묶음에 이어 붙임(묶음 b3_kamera)
// 다른 묶음이 남겨 둔 이음 자리를 여기서만 채웁니다(그 묶음을 싣지 않으면 이 파일만 빼면 됨).
//   JeomMunKamera(NaeGil.kt, 묶음 b2) — 점지도 문까지 안내("munkkaji")·내 문 두 번 찍기("jjikgi")에서 카메라 문 찾기
//   AnnaeEngine.dochakHook(Annae.kt, 묶음 b1) — 걸어서 닿은 뒤 4초에 문 찾기("dochak", 아이폰 MunChatgi.kyeogi("dochak"))
//   NaeMun(NaeGil.kt) — 찍어 두신 문 견주기·문을 지나면 두 번 찍은 것으로 담기
// 안드로이드 문 찾기는 관리자 시험 중이라 MunChatgi.gigiGaneung() 이 거짓이면(관리자 열쇠·카메라 허락이 없으면) 저절로 켜지지 않습니다.
// 길눈 화면 onCreate 에서 KameraIeum.dalgi() 한 번.
package kr.or.ada.app.gilnun

object KameraIeum {
    private var dalm = false

    fun dalgi() {
        if (dalm) return
        dalm = true
        JeomMunKamera.gigiGaneung = { MunChatgi.gigiGaneung() }
        JeomMunKamera.kyeogi = { e, gd -> MunChatgi.kyeogi(e, null, null, gd) }
        JeomMunKamera.munBoim = { MunChatgi.kyeojim && MunChatgi.munBoim }
        JeomMunKamera.jjikgiKkeut = { MunChatgi.jjikgiKkeut() }
        AnnaeEngine.dochakHook = { e -> if (MunChatgi.gigiGaneung()) MunChatgi.kyeogi(e) }
        MunChatgi.gidaeChatgi = { la, lo, r -> NaeMun.geulMun(la, lo, r)?.let { Pair(it.ireum, it.geul ?: emptyList()) } }
        MunChatgi.jjakDamgi = { la, lo, b, g -> NaeMun.jjakDamgi("", la, lo, b, g) }
    }
}
