# 하이 길눈 감지기 기르기 — 연습 소리 만들기 (판 1.0, 빌드 261001-H1, 대표님 승인 2026-10-01)
# "하이 길눈" 한 마디만 알아듣는 작은 감지기를 기르려면, 여러 목소리가 여러 빠르기와 높이로 "하이 길눈"을 말한 소리(맞는 소리)와
# 비슷하지만 다른 말·평소 말(틀린 소리)이 많이 있어야 합니다. 깃허브가 빌려 주는 리눅스에서 마이크로소프트 무료 목소리로 만듭니다.
# 만든 소리는 16킬로헤르츠 한 채널 wav 로 바꾸어 묶은 뒤 hai-data 갈래에 올립니다(앱 소스와 섞이지 않게 따로).
import asyncio, json, os, random, subprocess, sys
import edge_tts

OUT = sys.argv[1] if len(sys.argv) > 1 else "hai_sori"
random.seed(261001)

MAJA = ["하이 길눈", "하이, 길눈", "하이 길눈.", "하이길눈", "하이 길눈!", "하이 길눈?", "하이~ 길눈", "하이 길눈 하이 길눈"]

# 틀린 소리 — 비슷하게 들리는 말(가장 중요)과 길에서 흔히 들리는 말
BISUT = ["하이", "길눈", "하이킹", "하이 기분", "하이 기름", "하이 기린", "하이 누나", "하이 그늘", "하이 길동", "하이 김군",
         "아이 기분 좋아", "아이 귀여워", "해 길어", "흰 눈", "길눈이 밝다", "길 눈치", "기름 넣어", "기분이 좋네", "하이파이브",
         "하이마트", "하이볼", "하이힐", "하이라이트", "하이브리드", "헤이 구글", "하이 빅스비", "시리야", "헤이 카카오", "지니야",
         "아리아", "오케이 구글", "하이 클로바", "하이 브라우니", "길 건너", "길 잃었어", "이 길이 맞아", "길눈 켜", "어 길눈",
         "가자 길눈", "하이 길", "하이, 기", "하이 길이", "하이 기운", "하이 기능", "하이 길목", "하이 기념", "하이고 귀찮아", "아이고 길이 멀다"]
PYEONGSO = ["안녕하세요", "다음 역은 제기동역입니다", "내리실 문은 오른쪽입니다", "문이 닫힙니다", "잠시만요", "감사합니다",
            "지금 몇 시예요", "버스 왔어요", "어디 가세요", "조심하세요", "횡단보도를 건너세요", "초록불입니다", "빨간불입니다",
            "이번 정류장은 안암역입니다", "여기 앉으세요", "카드를 대 주세요", "환승입니다", "어서 오세요", "주문하시겠어요",
            "계단 조심하세요", "엘리베이터 어디 있어요", "출구는 3번입니다", "약수역 5번 출구", "오늘 날씨 좋네요", "비가 오네요",
            "점심 뭐 먹을까", "전화 왔어요", "여보세요", "네 알겠습니다", "아니요 괜찮아요", "천천히 가세요", "길 좀 물어볼게요",
            "이쪽으로 오세요", "저쪽이에요", "앞으로 쭉 가세요", "왼쪽으로 도세요", "오른쪽으로 도세요", "다 왔습니다", "도착했습니다",
            "음악 틀어 줘", "라디오 꺼 줘", "뉴스 들려줘", "날씨 알려 줘", "집으로 가자", "택시 불러 줘", "복지콜에 전화해 줘",
            "지하철 몇 분 남았어", "버스 언제 와", "편의점 어디야", "화장실 어디예요", "하나 둘 셋", "그래 그래", "알았어",
            "이사장님 안녕하세요", "대표님 회의 들어가세요", "선생님 이쪽입니다", "현장영상해설사입니다", "시각장애인 안내",
            "지팡이 조심", "개가 짖어요", "차가 와요", "자전거 지나갑니다", "실례합니다", "죄송합니다", "괜찮으세요"]

RATE = ["-30%", "-20%", "-10%", "+0%", "+10%", "+20%", "+30%"]
PITCH = ["-25Hz", "-15Hz", "-5Hz", "+0Hz", "+5Hz", "+15Hz", "+25Hz"]


async def moksori():
    vs = await edge_tts.list_voices()
    ko = [v["ShortName"] for v in vs if v["Locale"] == "ko-KR"]
    mul = [v["ShortName"] for v in vs if "Multilingual" in v["ShortName"] and v["ShortName"] not in ko]
    return ko, mul


async def hana(sem, txt, v, rate, pitch, path):
    async with sem:
        for _ in range(3):
            try:
                c = edge_tts.Communicate(txt, v, rate=rate, pitch=pitch)
                await c.save(path + ".mp3")
                subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path + ".mp3", "-ac", "1", "-ar", "16000", path + ".wav"], check=True)
                os.remove(path + ".mp3")
                return True
            except Exception as e:
                await asyncio.sleep(2)
        return False


async def main():
    ko, mul = await moksori()
    print("한국어 목소리", len(ko), ko)
    print("여러 말 목소리", len(mul))
    os.makedirs(OUT + "/maja", exist_ok=True)
    os.makedirs(OUT + "/teullim", exist_ok=True)
    il = []
    n = 0
    # 맞는 소리: 한국어 목소리는 목소리마다 24개, 여러 말 목소리는 8개
    for v in ko + mul:
        k = 24 if v in ko else 8
        for _ in range(k):
            t = random.choice(MAJA); r = random.choice(RATE); p = random.choice(PITCH)
            il.append(("maja", t, v, r, p, f"{OUT}/maja/{n:05d}")); n += 1
    # 틀린 소리: 비슷한 말은 목소리 여섯, 평소 말은 목소리 셋
    for t in BISUT:
        for v in random.sample(ko, min(4, len(ko))) + random.sample(mul, min(2, len(mul))):
            il.append(("teullim", t, v, random.choice(RATE), random.choice(PITCH), f"{OUT}/teullim/{n:05d}")); n += 1
    for t in PYEONGSO:
        for v in random.sample(ko, min(2, len(ko))) + random.sample(mul, 1):
            il.append(("teullim", t, v, random.choice(RATE), random.choice(PITCH), f"{OUT}/teullim/{n:05d}")); n += 1
    print("만들 소리", len(il))
    sem = asyncio.Semaphore(6)
    res = await asyncio.gather(*[hana(sem, t, v, r, p, path) for (_, t, v, r, p, path) in il])
    mok = [dict(gal=g, t=t, v=v, r=r, p=p, f=os.path.basename(path) + ".wav") for (g, t, v, r, p, path), ok in zip(il, res) if ok]
    json.dump(mok, open(OUT + "/mokrok.json", "w"), ensure_ascii=False, indent=0)
    print("만든 소리", len(mok), "실패", res.count(False))


asyncio.run(main())
