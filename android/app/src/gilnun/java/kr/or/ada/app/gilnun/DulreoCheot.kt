// 안드로이드 길눈 — 둘러보기 탭 첫 화면(묶음 b4_dulreo, b3 쪽지 7번의 자리표를 채움 — 아이폰 DulreoView.swift DulreoCheot 와 같은 차례, 급한 것부터)
// 겉: 둘레 찾기, 사진 읽어 주기, 즉석 글자 읽기, 가리키고 말하기, 사람 감지, 상품 바코드 읽기, 지폐와 색깔 알아보기, 빛 알아보기,
//     한마디 설명, 안면인식, 현장영상해설 받기(b6). 곁가지(가 볼 곳·축제·무장애 여행·고장 이야기·미리 들어 보기·마실)는 "가는 김에 펼치기" 안.
// 번호 없이, 화면이 열리면 매번 첫 줄(둘레 찾기)로 커서(아이폰 2.12.1).
package kr.or.ada.app.gilnun

class DulreoCheot : Hwamyeon("둘러보기") {
    private var ganeunGim = false
    override fun chaeugi(t: GilnunActivity) {
        val cheot = t.danchu("둘레 찾기 — 식당, 약국, 화장실, 응급실") { t.yeolgi(DulleHwamyeon()) }
        t.chojeomJul(cheot)
        t.danchu("사진 읽어 주기") { t.yeolgi(SajinHwamyeon()) }
        t.danchu("즉석 글자 읽기 — 카메라를 대면 글자를 곧바로 읽어 드립니다") { t.yeolgi(GeulIlgiHwamyeon()) }                 // b3
        t.danchu("가리키고 말하기 — 손가락으로 가리킨 단추의 글자를 읽어 드립니다") { t.yeolgi(GarikiHwamyeon()) }
        t.danchu("사람 감지 — 앞의 사람을 방향과 걸음 수로 알려 드립니다") { t.yeolgi(SaramGamjiHwamyeon()) }                  // b3
        t.danchu("상품 바코드 읽기 — 상품을 카메라에 돌려 대면 이름을 읽어 드립니다") { t.yeolgi(SangpumHwamyeon()) }
        t.danchu("지폐와 색깔 알아보기 — 옷과 물건의 색, 지폐가 얼마짜리인지 알려 드립니다") { t.yeolgi(JipyeSaekHwamyeon()) }
        t.danchu("빛 알아보기 — 불이 켜졌는지, 창이 어느 쪽인지 소리 높낮이로 알려 드립니다") { t.yeolgi(BitAlgiHwamyeon()) }   // b3
        t.danchu("한마디 설명 — 인터넷 없이 카메라 앞을 한마디로 알려 드립니다") { t.yeolgi(HanmadiHwamyeon()) }
        t.danchu("안면인식 — 카메라 앞의 사람을 알려 드립니다") { t.yeolgi(AnmyeonHwamyeon()) }
        t.danchu("현장영상해설 받기 — 현장영상해설사 화상통화, 현장영상해설 코스, 파견 신청") { t.yeolgi(HaeseolBatgiHwamyeon()) }   // b6
        t.pyeolchigi("가는 김에", ganeunGim) { ganeunGim = !ganeunGim }
        if (ganeunGim) {
            t.danchu("가 볼 곳") { t.yeolgi(GotMokrokHwamyeon("gabol")) }
            t.danchu("축제") { t.yeolgi(GotMokrokHwamyeon("chukje")) }
            t.danchu("무장애 여행 정보") { t.yeolgi(GotMokrokHwamyeon("mujangae")) }
            t.danchu("고장 이야기 — 지나는 고장의 먹을 곳·볼 곳") { t.yeolgi(GojangHwamyeon()) }
            t.danchu("어디서 어디로 — 미리 들어 보기") { t.yeolgi(MiriHwamyeon()) }
            t.danchu("마실 — 앉은자리에서 떠나는 여행") { t.yeolgi(MasilHwamyeon()) }
        }
    }
}
