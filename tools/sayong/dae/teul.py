# 길눈 사용법 동영상 — 모든 편에 같은 틀로 넣는 여는 말과 맺는 말 (2026-10-18 이사장님 확정)
# 방송·아나운싱 기본: 인사 → 협회 소개 → 앱 소개 → 강의 시작 → 판과 목소리 → 차례 / 마칠 때 → 다음 시간 → 협회 → 인사
HYEOPHOE = "사단법인 한국시각장애인현장영상해설협회"


def eul(t):
    c = t.rstrip()[-1]
    if "가" <= c <= "힣":
        return "을" if (ord(c) - 0xAC00) % 28 else "를"
    return "을" if c in "013678" else "를"


def yeoneun(pan, je, naedogi, meori, bunho, jemok, charye):
    """pan: 아이폰/안드로이드, je: 판 번호, bunho: '1-1', jemok: '1편 길 찾기 탭, 1-1 길 찾기 첫 화면', charye: '가, 나, 다' (없으면 빈 글)"""
    mal = [
        ("N", "안녕하십니까."),
        ("N", f"{HYEOPHOE}입니다."),
        ("N", "우리 협회는 2015년부터 시각장애인이 보지 못하는 것을 현장에서 말로 풀어 드리는 현장영상해설사를 길러 내고 파견해 왔습니다."),
        ("N", "길눈은 우리 협회가 만든 앱입니다."),
        ("N", "시각장애인이 혼자서도 목적지를 찾아 걸어가고, 차, 지하철, 버스, 기차, 배를 타고 갈 수 있도록 안내합니다."),
        ("N", f"지금부터 길눈 {pan} 사용법, {jemok} 강의를 시작하겠습니다."),
    ]
    mal2 = [
        ("N", f"이 강의는 {pan} 길눈 {je}판을 따릅니다."),
        ("N", f"남자 목소리는 안내, 빠른 여자 목소리는 {naedogi}가 읽는 소리, 또 다른 여자 목소리는 길눈이 직접 하는 말입니다."),
        ("N", "화면에 나오는 이름과 숫자는 보기로 보여 드리는 것이라 실제와 다를 수 있습니다."),
    ]
    if charye:
        mal2.append(("N", f"오늘 배울 차례는 이렇습니다. {charye}입니다."))
    return [{"pyoji": meori, "mal": mal}, {"pyoji": meori, "mal": mal2}]


def matneun(pan, meori, bunho, daeum):
    """daeum: '1-2 목적지 찾기' (마지막 편이면 빈 글)"""
    mal = [("N", f"이것으로 길눈 {pan} 사용법 {bunho} 강의를 마치겠습니다.")]
    if daeum:
        mal.append(("N", f"다음 시간에는 {daeum}{eul(daeum)} 배우겠습니다."))
    mal += [("N", f"지금까지 {HYEOPHOE}였습니다."), ("N", "들어 주셔서 고맙습니다.")]
    return {"pyoji": meori, "mal": mal}
