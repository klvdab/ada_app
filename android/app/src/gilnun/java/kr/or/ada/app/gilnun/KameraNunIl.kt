// 2.84.0 (261027-A72, 이사장님 승인 2026-10-27 「1」, 아이폰 2.119.0과 같음) 카메라 눈은 그 일 하나에만
// 기능을 세 갈래로 나눔 —
//   틀어 두는 소리(음악·라디오·TV·방송): 계속 틀어 두고 다른 안내가 말할 때만 소리를 낮춤(지금 그대로)
//   카메라 눈(지폐와 색깔·글자·가리키기·사람·바코드·빛·한마디·문 찾기·QR): 다른 일을 시키시면 끄고 한 마디만 알림
//   길 안내·한 번 답하기(길 안내·역·날씨·일정·시간): 시작하면 카메라 눈은 끄고 틀어 두는 소리는 그대로
// 발 앞 계단·턱 알림은 걷는 동안 지키는 안전 경고라 끄지 않음
package kr.or.ada.app.gilnun

object KameraNunIl {
    /** 켜져 있는 카메라 눈 이름(없으면 null) */
    fun kyeojinIreum(): String? = Kamera.dareunIlKkeulBupum()?.ireum

    /** 켜진 카메라 눈을 말없이 끄고, 알릴 한 마디를 돌려줌(켜진 것이 없으면 null) */
    fun kkeugi(): String? {
        val n = kyeojinIreum() ?: return null
        var b = Kamera.dareunIlKkeulBupum()
        var k = 0
        while (b != null && k < 12) { b.kkeugi(false); b = Kamera.dareunIlKkeulBupum(); k++ }
        Girok.namgi("kameranun_dareunil", mapOf("ireum" to n))
        return n + MalHagi.eul(n) + " 껐습니다."
    }

    /** 카메라 눈을 그대로 둘 말 — 카메라 눈을 여는 말과, 지금 쓰시는 기능을 다루는 말(다시 말해·말 빠르게·말 느리게·말소리 꺼). z 는 띄어쓰기를 뺀 말 */
    fun jagiMal(z: String): Boolean {
        val dareum = listOf("다시말", "다시들려", "방금", "말빠르", "말느리", "빠르게", "느리게", "말소리", "소리크", "소리작", "크게말", "작게말",
            "무슨색", "색깔", "색알려", "색이뭐", "지폐", "얼마짜리", "바코드", "상품뭐", "무슨상품", "상품이름", "상품읽",
            "글자읽", "글씨읽", "가리키", "사람있", "사람감지", "앞에사람", "사람찾", "빛알", "밝은쪽", "불켜져",
            "이게뭐", "이거뭐", "뭐가보여", "무엇이보여", "한마디", "qr", "QR", "큐알", "문찾", "손전등")
        return dareum.any { z.contains(it) }
    }
}
