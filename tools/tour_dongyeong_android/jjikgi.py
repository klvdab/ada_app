# LVD투어스튜디오 안드로이드 1.4.1판 사용법 동영상 화면 찍기 1.2 (빌드 261119-DA4, 대클 — 안드로이드는 같은 탭을 다시 눌러도 맨 위로 가지 않아 아래로만 찾다 19곳을 놓침: 탭마다 맨 위로 밀고, 못 찾으면 맨 위에서 한 번 더 찾음) 1.1 (빌드 261118-DA2 — 교본이 아닌 코스를 열고, 화면 읽기를 더 끈질기게) 1.0 (빌드 261118-DA1, 새방클 — 대표님 승인 2026-11-18 「1,2,3 모두 꼼꼼하게」)
# 안드로이드 흉내 기계에서 앱을 띄워, 화면에 적힌 단추·제목 글자 그대로를 찾아 누르고 장면마다 찍습니다.
# 아이폰 DY1(TourUITests/DongyeongJjikgi.swift)과 같은 장면 번호·이름을 씁니다. 못 찾은 장면은 막힌장면.txt 에 적고 다음으로 갑니다.
# 앱 코드는 고치지 않습니다. 통행증은 디버그 판의 run-as 로 앱 설정(tour.xml)에 넣습니다.
import os, re, subprocess, sys, time, xml.etree.ElementTree as ET

PKG = "kr.or.ada.tourstudio"
NAE = sys.argv[1] if len(sys.argv) > 1 else "jjik"
os.makedirs(NAE, exist_ok=True)
GIL = []          # 막힌 장면 기록

def adb(*a, inp=None, raw=False):
    r = subprocess.run(["adb"] + list(a), input=inp, capture_output=True, timeout=120)
    return r.stdout if raw else r.stdout.decode("utf-8", "replace")

def sh(c): return adb("shell", c)

W, H = 1080, 2400
def keugi():
    global W, H
    m = re.search(r"(\d+)x(\d+)", sh("wm size"))
    if m: W, H = int(m.group(1)), int(m.group(2))

ANR = ("isn't responding", "응답하지 않습니다", "응답 없음")
def anr_geotgi(t):
    """DA3: 「Pixel Launcher isn't responding」 같은 시스템 창이 덮으면 「Wait/기다리기」를 눌러 걷음. 걷었으면 True"""
    if t is None: return False
    if not any(any(a in (n.get("text") or "") for a in ANR) for n in t.iter("node")): return False
    for n in t.iter("node"):
        if (n.get("text") or "").strip() in ("Wait", "기다리기", "대기"):
            nureugi_n(n); time.sleep(2); return True
    sh("input keyevent 4"); time.sleep(2); return True

def dump():
    for _ in range(10):
        sh("rm -f /sdcard/u.xml; uiautomator dump /sdcard/u.xml >/dev/null 2>&1")
        x = adb("exec-out", "cat", "/sdcard/u.xml")
        if "<hierarchy" in x:
            try:
                t = ET.fromstring(x[x.index("<hierarchy"):])
                if anr_geotgi(t): continue
                return t
            except Exception: pass
        time.sleep(0.7)
    print("화면 읽기 실패")
    return None

def bounds(n):
    a = list(map(int, re.findall(r"\d+", n.get("bounds", "[0,0][0,0]"))))
    return a if len(a) == 4 else [0, 0, 0, 0]

def maja(n, geul, kkok):
    t = (n.get("text") or "").strip(); d = (n.get("content-desc") or "").strip()
    for s in (t, d):
        if not s: continue
        if kkok and s == geul: return True
        if not kkok and (s == geul or s.startswith(geul)): return True
    return False

def chatgi(geul, kkok=False):
    r = dump()
    if r is None: return None
    hu = []
    for n in r.iter("node"):
        if maja(n, geul, kkok):
            b = bounds(n)
            if b[3] > b[1] and b[1] < H - 160:   # 탭 줄 아래 가려진 것은 빼고
                hu.append((0 if n.get("clickable") == "true" else 1, b[1], n))
    if not hu: return None
    hu.sort(key=lambda a: (a[0], a[1]))
    return hu[0][2]

def nureugi_n(n):
    b = bounds(n); sh("input tap %d %d" % ((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)); time.sleep(2)

def ollyeo(dy):   # 화면을 위로 dy 만큼 천천히 밀기(관성 없이)
    x = W // 2; y1 = int(H * 0.78); y2 = max(int(H * 0.12), y1 - dy)
    sh("input swipe %d %d %d %d 900" % (x, y1, x, y2)); time.sleep(1.2)

def jjik(ireum):
    time.sleep(1.5)
    dump()   # DA3: 찍기 전에 응답 없음 창이 있으면 걷음
    png = adb("exec-out", "screencap", "-p", raw=True)
    open(os.path.join(NAE, ireum + ".png"), "wb").write(png)
    print("찍음", ireum, len(png))

def maemwi(beon=10):   # DA4: 화면 맨 위로 — 손가락을 위에서 아래로 빠르게 여러 번 밈
    x = W // 2
    for _ in range(beon):
        sh("input swipe %d %d %d %d 120" % (x, int(H * 0.22), x, int(H * 0.86)))
    time.sleep(1.5)

def tab(ireum):
    n = chatgi(ireum + " 탭")
    if n is None: GIL.append("탭 없음: " + ireum); return False
    nureugi_n(n); n = chatgi(ireum + " 탭")
    if n is not None: nureugi_n(n)       # 같은 탭 한 번 더
    maemwi()                             # DA4: 안드로이드는 다시 눌러도 맨 위로 안 가므로 밀어서 맨 위로
    return True

def naeryeo_chatgi(geul, beon):
    """DA4: 지금 자리에서 내려 가며 찾고, 없으면 맨 위로 가서 한 번 더 내려 가며 찾음"""
    for dol in range(2):
        if dol == 1: maemwi()
        for _ in range(beon):
            n = chatgi(geul)
            if n is not None: return n
            ollyeo(int(H * 0.45))
    return None

def wiro(geul, beon=14):
    """글자를 찾을 때까지 내려 가며, 찾으면 화면 위쪽 1/6 자리로 올려 둠"""
    n = naeryeo_chatgi(geul, beon)
    if n is not None:
        b = bounds(n); mok = int(H * 0.16)
        if b[1] > mok + 40: ollyeo(b[1] - mok)
        return True
    GIL.append("못 찾음: " + geul); return False

def nureugi(geul, beon=14):
    n = naeryeo_chatgi(geul, beon)
    if n is not None: nureugi_n(n); return True
    GIL.append("못 누름: " + geul); return False

def dwiro(): sh("input keyevent 4"); time.sleep(2)

def kyeogi():
    sh("monkey -p %s -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1" % PKG); time.sleep(12)

def gangje():
    sh("am force-stop " + PKG); time.sleep(1)

def jangmyeon(ireum, il):
    try: il()
    except Exception as e: GIL.append("%s 오류: %s" % (ireum, str(e)[:120]))

# DA3: 시스템 오류 창을 숨기고, 켠 뒤 런처가 가라앉을 때까지 기다림
sh("settings put global hide_error_dialogs 1")
for _ in range(60):
    if sh("getprop sys.boot_completed").strip() == "1": break
    time.sleep(2)
time.sleep(40)
sh("input keyevent 3"); time.sleep(5)
keugi()
print("화면 크기", W, H)
for k in ["ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "CAMERA", "RECORD_AUDIO", "ACTIVITY_RECOGNITION", "POST_NOTIFICATIONS", "READ_MEDIA_IMAGES"]:
    sh("pm grant %s android.permission.%s 2>/dev/null" % (PKG, k))

# ── 1편 들어가기 (통행증 없이)
def p1():
    gangje(); sh("run-as %s rm -f shared_prefs/tour.xml" % PKG); kyeogi()
    jjik("101_들어오기화면")
    n = chatgi("아이디")
    if n is not None: nureugi_n(n); jjik("102_아이디칸"); dwiro()
    else: GIL.append("못 찾음: 아이디 칸")
    if nureugi("가입 신청", 3): jjik("103_가입신청하기"); dwiro()
jangmyeon("1편", p1)

# ── 통행증 넣고 다시 켜기
TK = os.environ.get("SAJIN_TK", "")
if TK:
    gangje()
    x = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n<string name=\"token\">%s</string>\n<string name=\"meName\">시험 계정</string>\n</map>\n" % TK
    adb("shell", "run-as %s sh -c 'mkdir -p shared_prefs && cat > shared_prefs/tour.xml'" % PKG, inp=x.encode())
    kyeogi()
else:
    GIL.append("통행증 없음 — 들어간 뒤 화면은 못 찍음")

# 코스 하나 열어 두기(코스 목록 제목 아래 첫 코스)
def kos_yeolgi():
    tab("코스")
    if not wiro("코스 목록"): return
    r = dump(); hd = chatgi("코스 목록")
    if r is None or hd is None: return
    y0 = bounds(hd)[3]
    hu = [n for n in r.iter("node") if n.get("clickable") == "true" and bounds(n)[1] > y0 and (n.get("text") or n.get("content-desc"))
          and "교본" not in (n.get("text") or "") + (n.get("content-desc") or "")]   # DA2: 교본은 읽기만 되어 답사·대본 칸이 숨음 — 내 코스를 엶
    hu.sort(key=lambda n: bounds(n)[1])
    if hu: nureugi_n(hu[0]); time.sleep(3)
    else: GIL.append("코스 목록에 코스 없음")
jangmyeon("코스 열기", kos_yeolgi)

# ── 2편 화면 짜임과 설정
def p2():
    tab("코스"); jjik("201_코스탭_첫화면")
    if wiro("코스 목록"): jjik("202_코스목록")
    tab("코스")
    if nureugi("코스 정보"): jjik("203_코스정보"); ollyeo(int(H * 0.5)); jjik("204_코스정보_아래"); dwiro()
    tab("코스")
    if nureugi("장소 지식 들이기"): jjik("205_장소지식들이기"); dwiro()
    tab("코스")
    if nureugi("기능 찾기"): jjik("206_기능찾기"); dwiro()
    tab("설정"); jjik("207_설정탭")
    if nureugi("무엇이 바뀌었나"): jjik("208_무엇이바뀌었나"); dwiro()
    tab("설정")
    if wiro("도움말 찾기"): jjik("209_도움말찾기")
jangmyeon("2편", p2)

# ── 3편 코스
def p3():
    tab("코스")
    if wiro("지금 연 코스"): jjik("301_지금연코스")
    if wiro("다음 할 일"): jjik("302_다음할일")
    tab("코스")
    if nureugi("대본 살펴보기"):
        jjik("303_대본살펴보기")
        if wiro("원칙 16개"): jjik("304_원칙16개점검표")
        dwiro()
    tab("코스")
    if nureugi("내보내기"):
        jjik("305_내보내기")
        if wiro("협회 최종 승인"): jjik("306_협회최종승인")
        dwiro()
    tab("코스")
    if wiro("새 코스 만들기"): jjik("307_새코스만들기")
    tab("코스")
    if wiro("휴지통"): jjik("308_휴지통")
jangmyeon("3편", p3)

# ── 4편 답사
def p4():
    tab("답사"); jjik("401_답사탭_첫화면")
    for g, i in [("경계 적기", "402_경계적기"), ("사진 찍기", "403_사진찍기"), ("담긴 사진 보기", "404_담긴사진"),
                 ("화면 안 보고 답사하기", "405_이어폰단추"), ("걸으며 저절로 재기", "406_걸으며저절로재기"), ("지점 옮겨 보기", "407_지점옮겨보기")]:
        if wiro(g): jjik(i)
jangmyeon("4편", p4)

# ── 5편 지점
def p5():
    tab("지점"); jjik("501_지점탭_첫화면")
    if wiro("지점 목록"): jjik("502_지점목록")
    n = naeryeo_chatgi("오프닝", 6)   # DA4: 목록 아래쪽에 있으면 내려 가며 찾음
    if n is not None: nureugi_n(n); time.sleep(2); maemwi()
    else: GIL.append("첫 지점 못 엶")
    jjik("503_지점한화면")
    if wiro("경계와 시간"): jjik("504_경계와시간")
    if wiro("말로 대본 쓰기"): jjik("505_말로대본쓰기")
    if wiro("넣을 칸"): jjik("506_넣을칸")
    ollyeo(int(H * 0.45)); jjik("507_대본칸")
    if wiro("방향 도우미"): jjik("508_방향도우미")
    if wiro("현장 메모"): jjik("509_현장메모")
jangmyeon("5편", p5)

# ── 6편 현장영상해설
def p6():
    tab("현장영상해설"); jjik("601_현장영상해설탭")
    if wiro("지점 차례"): jjik("602_지점차례")
    if wiro("현장에서"): jjik("603_현장에서_승인본")
    tab("코스")
    if nureugi("내보내기"):
        if wiro("협회 최종 승인"): jjik("604_협회최종승인요청"); ollyeo(int(H * 0.5)); jjik("605_승인요청_아래")
        dwiro()
jangmyeon("6편", p6)

open(os.path.join(NAE, "막힌장면.txt"), "w", encoding="utf-8").write("\n".join(GIL) + "\n")
print("막힌 장면", len(GIL)); print("\n".join(GIL))
