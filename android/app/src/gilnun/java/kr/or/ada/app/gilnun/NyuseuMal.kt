// 뉴스·라디오 말 넓히기 — 안드로이드 2.83.0(빌드 261027-A71, 대클, 이사장님 승인 2026-10-27 「1」 공개 자료로 넓히기)
// 공개 자료(아마존 MASSIVE 한국어) 시험에서 「요즘 세상에 무슨 일 있어」, 「선거 소식 업데이트 해 줘」, 「라디오 찾아 줘」,
// 「SBS 틀어 줘」 같은 말이 곳 찾기나 새로고침으로 잘못 갔음(뉴스 709문장 가운데 427, 라디오 401문장 가운데 233만 알아들음).
// 순수 셈이라 따로 시험함. 아이폰 NyuseuMal(MalHagi.swift 끝)과 같은 셈.
package kr.or.ada.app.gilnun

object NyuseuMal {
    private val SESANG = listOf("세상", "세계", "요즘", "요새", "오늘", "최근", "국내", "해외", "나라", "전세계", "지금", "이번주", "어제")
    private val ILEO = listOf("무슨일", "어떤일", "무슨소식", "별일", "어떻게돌아가", "일어나고", "일어난", "벌어지", "벌어진", "돌아가고", "무슨일들")
    private val SINHO = listOf("헤드라인", "리포트", "이슈", "화제", "실검", "실시간검색", "최신소식", "최근소식", "새소식", "논란", "속보", "특보", "뉴스", "소식")
    private val DEOT = listOf("업데이트", "최신", "최근", "결과", "어떻게됐", "요약", "논란", "화제", "소식")
    // 뉴스가 아닌 말(길눈에 없는 기능·음악·셈) — 「검정치마」의 정치, 「더하기 사는」의 기사, 「청소기 사용」의 기사에 걸리지 않게
    private val ANIM = listOf("트위터", "트윗", "페이스북", "인스타", "유튜브", "카톡", "메일", "환율", "주가", "주식", "코인",
        "더하기", "빼기", "곱하기", "나누기", "청소기", "음악", "노래", "앨범", "곡",
        "날씨", "미세먼지", "신호기", "현장영상해설")   // 날씨·신호기·현장영상해설은 두 앱 모두 그 처리로
    private val GAL = listOf(
        "jeongchi" to listOf("선거", "총선", "대선", "대통령", "국회", "정당", "여론조사", "정치", "정부", "장관", "의회", "외교", "시의회"),
        "spo" to listOf("리그", "야구", "축구", "농구", "배구", "골프", "올림픽", "월드컵", "스포츠", "선수", "점수", "연예", "시상식", "경기결과"),
        "gyeongje" to listOf("경제", "물가", "금리", "부동산", "수출", "기업"),
        "gukje" to listOf("세계", "국제", "해외", "미국", "중국", "일본", "유럽", "동남아"),
        "sahoe" to listOf("사회", "사건", "사고", "시위", "화재", "지진", "코로나", "재난")
    )

    /** 뉴스를 청하시는 말인가 — z 는 띄어쓰기를 뺀 말 */
    fun mutneunInga(z0: String): Boolean {
        val z = z0.replace("영화제", "").replace("축제", "")
        if (!z.contains("뉴스") && ANIM.any { z.contains(it) }) return false
        if (Regex("기사(읽|들|보여|알려|있|를|목록|제목|요약|$)").containsMatchIn(z) &&
            !(z.contains("택시기사") || z.contains("기사님") || z.contains("운전기사") || z.contains("버스기사"))) return true
        if (SINHO.any { z.contains(it) }) return true
        // 「오후 두 시 네 시 사이에 무슨 일 있어」는 일정, 「동네에 무슨 일 있어」는 고장 이야기 쪽이라 뉴스로 잡지 않음
        if (ILEO.any { z.contains(it) } && listOf("오전", "오후", "사이에", "동네", "지역", "근처", "여기", "이곳").none { z.contains(it) } && (SESANG.any { z.contains(it) } || Regex("[가-힣]{2,}(에서|에)(는)?(무슨|어떤)일").containsMatchIn(z))) return true
        val g = GAL.any { (_, l) -> l.any { z.contains(it) } } || (z.contains("경기") && !z.contains("경기도"))
        return g && DEOT.any { z.contains(it) }
    }

    /** 뉴스 갈래(정치·스포츠·경제·국제·사회) — 모르면 null */
    fun galrae(z: String): String? = GAL.firstOrNull { (_, l) -> l.any { z.contains(it) } }?.first
        ?: if (z.contains("경기") && !z.contains("경기도")) "spo" else null

    /** 가자는 말(「SBS 방송국 가자」, 「YTN 가 줘」) — 이런 말은 뉴스·라디오로 먼저 잡지 않고 길 안내로 */
    fun gajaMal(z: String): Boolean = Regex("가(자|줘|주|고싶|려|볼래)|갈래|데려").containsMatchIn(z)

    /** 라디오를 청하시는 말(방송국·주파수·SBS·CBS·국악방송 …) */
    fun radioMal(z: String): Boolean {
        val zl = z.lowercase()
        return listOf("라디오", "방송국", "주파수", "sbs", "에스비에스", "cbs", "씨비에스", "tbn", "티비엔", "국악방송", "경인방송", "ytn", "와이티엔").any { zl.contains(it) }
    }
}
