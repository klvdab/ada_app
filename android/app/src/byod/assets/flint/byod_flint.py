#!/usr/bin/env python3
# BYOD 방송 — 플린트 2(GL-MT6000) 공유기 안 방송 서버
# 판 1.2.0 / 빌드 261014-F1 / 2026-10-14 (이사장님 승인 「1」 — 서버를 공유기 안에 둠, 방송클)
# 판 1.3.0 / 빌드 261014-F2 / 2026-10-14 (이사장님 승인 「1」, 방송클) 행사 녹음을 1분마다 저절로 남기고(전기가 끊겨도 앞부분은 남음),
#   행사 기록(1분마다 듣는 분 수·도움 요청·끊김)과 한 장짜리 결과보고를 만들어, 앱이나 공유기 스스로 배프 다시듣기와 나스에 올림
# 1.4.0 (빌드 261024-F4, 새방송클, 대조표 송출 21·22·23번 — 이사장님이 9월 24·27·28일에 승인하신 노트북판 현장 해설방송 3.3판 기능 옮기기)
#   노트북판 길(SPEC.md 4~17번, /naemic·/saepan_batgi 빼고)을 같은 이름·같은 답으로: 피디·해설사·관계자 화면(pd.html), 해설·무대 카메라 화면(bang.html),
#   입장 번호와 추첨, 도움 요청과 처리, 해설사끼리 글, 순간 표시, 무대 카메라, 관계자 붙이기와 무전, 긴급 안내, 스스로 점검, 결과보고(/bogoseo),
#   1분마다 상황 저장과 다시 켜질 때 이어 받기, 자물쇠 문(TLS 8443 — 폰 마이크·카메라용).
#   새 네 가지: 여러 갈래 해설(최대 3갈래), 구역별·공유기별 인원, 만족도 묻기(소리 나누어 조절은 듣는 화면 안의 일).
#   (판 상수 PAN/BILD 는 마지막에 한꺼번에 바꿈 — 이 판은 1.4.0 / 261024-F4 로 바꿀 것)
#
# 문: 듣기 7880(80번을 방화벽이 돌려줌), 소리 받기 7881, 자물쇠 8443
# 하는 일
#   1. 해설 소리 받기: 방송 폰·태블릿(BYOD 앱)이 소리 한 줄기(뮤 8비트, 1초에 16000)를 7881번 문으로 보냄 — 갈래 1
#      관계자 해설사 폰(bang.html)은 POST /pcm?gal=2(또는 3)로 보냄 — 갈래 2·3
#   2. 나눠 보내기: 듣는 분 폰마다 /stream?gal=n(저지연) 또는 /stream.mp3(갈래 1, 화면을 잠가도 이어짐)
#   3. 듣기 화면 저절로 뜨기: 폰이 와이파이에 붙어 「인터넷 되느냐」고 물으면 듣기 화면으로 보냄
#   4. 도움 요청·듣는 분 수·방송 상태를 앱에 알림(/status, /yocheong_mokrok)
# 길 이름은 노트북판·태블릿판(현장 해설방송 3.3판, BYOD 1.1.x)과 같아 듣는 화면(deut.html)·피디 화면(pd.html)·해설 화면(bang.html)이 그대로 돎.
# 파이썬 3.9(오픈더블유알티 21.02) 이상에서 돎. 바깥 꾸러미 없이 표준 것만 씀(mp3는 lame 이 있을 때만, 자물쇠 문은 ssl 이 있을 때만).

import asyncio, copy, json, os, re, sys, time, shutil, urllib.parse, urllib.request
try:
    import ssl
except Exception:          # python3-openssl 이 없는 공유기 — 자물쇠 문 없이 돎
    ssl = None

PAN = "1.4.0"   # 261024-F4 노트북판 현장 기능 전부·여러 갈래 해설·만족도·구역별 인원·자동 저장·자물쇠 문 8443·갈래 녹음과 기록도 나스로(새방송클). 261015-F3 상태에 행사 이름·현장영상해설사, 듣기 화면에 LVD 방송 앱 받기, 행사 기본 이름 「현장영상해설 방송」
BILD = "261024-F4"

SEOBEO_IP = os.environ.get("BYOD_IP", "192.168.8.2")      # 듣기 화면 주소(공유기에 덧붙인 주소)
WEB_PORT = int(os.environ.get("BYOD_WEB_PORT", "7880"))    # 80번은 방화벽이 이리로 돌려줌
SORI_PORT = int(os.environ.get("BYOD_SORI_PORT", "7881"))  # 방송 앱이 소리를 보내는 문
TLS_PORT = int(os.environ.get("BYOD_TLS_PORT", "8443"))    # 1.4.0 자물쇠 문(폰 마이크·카메라는 https 에서만 열림), 0 이면 열지 않음
TEO = os.environ.get("BYOD_TEO", "/etc/byod")              # 듣는 화면·대기 음악·열쇠를 두는 곳
RATE = 16000
JOGAK = 1600                                               # 0.1초
DEUT_JUL = 40                                              # 듣는 분마다 4초까지 쌓아 둠
MAKHIM = 10.0                                              # 10초 넘게 쓰기가 막히면 끊긴 폰으로 봄
HAENGSA_TEO = os.environ.get("BYOD_HAENGSA", os.path.join(TEO, "haengsa"))   # 행사 녹음과 기록
IEUM = float(os.environ.get("BYOD_IEUM", "300"))         # 방송 기기가 이 초 안에 다시 붙으면 같은 행사(해설사 교대)
JOGAK_CHO = float(os.environ.get("BYOD_JOGAK_CHO", "60")) # 녹음 조각 길이(초)
JADONG_GAN = float(os.environ.get("BYOD_JADONG_GAN", "60"))  # 1.4.0 상황 저절로 저장 간격(초)
APP_IOS = os.environ.get("BYOD_APP_IOS", "https://lvd.ada.or.kr/sihum/")   # 1.3.1 LVD 방송 앱 받는 곳(앱스토어 주소가 정해지면 바꿈)
APP_AOS = os.environ.get("BYOD_APP_AOS", "https://lvd.ada.or.kr/sihum/")
NAS_OLLIGI = os.environ.get("BYOD_NAS", "https://lvd.ada.or.kr/bfblive/byod_olligi.php")
OLLIGI_YEOLSOE = "byod-261014-k7m2"                       # 배프에 올리는 창구 열쇠(나스 byod_olligi.php 와 같음)
JARYO_URL = os.environ.get("BYOD_JARYO", "https://lvd.ada.or.kr/bfblive/hyeonjang/jaryo.json")   # 1.4.0 해설사 화면 행사 자료
GAL_SU = 3                                                 # 1.4.0 여러 갈래 해설 — 최대 3갈래
GAL_GIBON = {1: "주 해설", 2: "갈래 2 해설", 3: "갈래 3 해설"}
MANJOK_GIBON = "오늘 현장영상해설은 어떠셨습니까?"

# ── 뮤 되돌리기(8비트 → 16비트) ──
DEC = []
for i in range(256):
    u = ~i & 0xFF
    s, e, m = u & 0x80, (u >> 4) & 7, u & 0x0F
    x = (((m << 3) + 0x84) << e) - 0x84
    v = -x if s else x
    DEC.append(int(v).to_bytes(2, "little", signed=True))
GOYO = bytes([0xFF]) * JOGAK


def ilkgi(nm, gibon=""):
    try:
        with open(os.path.join(TEO, nm), encoding="utf-8") as f:
            return f.read().strip()
    except Exception:
        return gibon


def pd_beonho_mandeul():
    b = os.environ.get("BYOD_PDKEY", "")
    if len(b) == 4 and b.isdigit():
        return b
    return str(1000 + int.from_bytes(os.urandom(4), "big") % 9000)


class Bang:
    def __init__(self):
        self.yeolsoe = ilkgi("yeolsoe")            # 방송 앱만 소리를 보낼 수 있게
        self.ttuigi = ilkgi("ttuigi", "ttuim")     # ttuim: 듣기 화면 저절로 뜨기 / cheok: 인터넷 있는 척만
        self.tonggwa_ttae = ilkgi("tonggwa", "an")  # deutgi: 듣기를 시작하면 그 폰은 인터넷 있는 척으로 / an: 늘 뜨기 창 그대로(현장 시험으로 정함)
        self.deut = []                              # 저지연 듣는 분(갈래마다 d.gal)
        self.deut_mp3 = []                          # 잠가도 이어지는 듣는 분(갈래 1)
        self.bonaem = None                          # 지금 소리를 보내는 앱(쓰는 쪽)
        self.bonaem_ireum = ""
        self.bonaem_ip = ""                         # 1.4.0 방송 기기 주소 — 이 기기는 노트북판의 「노트북 자신」처럼 다룸
        self.majimak_sori = 0.0                     # 소리가 마지막으로 들어온 때
        self.yocheong = []                          # (때, 번호, 종류) — 앱이 묻는 목록
        self.yseq = 0
        self.tonggwa = set()                        # 듣기 화면을 이미 연 폰(주소)
        self.lame = None
        self.mp3_iss = shutil.which("lame") is not None or bool(os.environ.get("BYOD_LAME"))
        self.sijak_ttae = time.time()
        self.sigan_chai = 0.0                       # 방송 기기 시계와의 차이(공유기는 현장에서 시계를 맞출 길이 없음)
        self.sigan_matchum = False                  # 1.4.0 방송 기기가 시계를 알려 주었는가
        self.haengsa = None                         # 지금 행사
        self.majimak_butum = 0.0                    # 방송 기기가 마지막으로 붙어 있던 때
        # 1.4.0 노트북판에서 옮긴 것
        self.pdkey = pd_beonho_mandeul()            # 피디 번호(네 자리)
        self.tport = 0                              # 자물쇠 문이 열렸으면 그 번호
        self.cue = {1: False, 2: False, 3: False}   # 갈래마다 큐(해설 시작·방송중지)
        self.jeongji1 = False                       # 피디가 방송중지를 눌러 방송 앱 소리(갈래 1)를 막음
        self.mic_ttae = {1: 0.0, 2: 0.0, 3: 0.0}    # 해설 화면이 살아 있다고 알린 때(/mic, /pcm)
        self.pcm_ttae = {1: 0.0, 2: 0.0, 3: 0.0}    # /pcm 소리가 들어온 때
        self.sori_ttae = {1: 0.0, 2: 0.0, 3: 0.0}   # 갈래마다 듣는 분께 실제로 소리를 내보낸 때
        self.goyo_ttae = {1: 0.0, 2: 0.0, 3: 0.0}
        self.nas_mal = ""
        self.mideum = set()                         # 받아 준 폰(열쇠)
        self.gidari = {}                            # 받기를 기다리는 폰 열쇠 -> [이름, 때]
        self.hwakin = 0                             # 인터넷 확인 물음 수
        self.jiyeon = {}                            # 폰 -> [늦음 ms, 받은 때]
        self.jg_jung = False                        # 스스로 점검 중
        self.jg_sijak = 0.0
        self.jg_damgi = None
        self.jadong_mal = ""
        self.jadong_ieo = ""
        self.bulleoom = False                       # 앞서 저장된 상황을 이어 받았는가
        self.boot_bh = set()                        # 켜진 뒤 새로 들어온 입장 번호
        self.nok_olrinun = False
        self.nok_mal = ""
        self.S = sangtae_bin()                      # 저장하는 상황(입장 번호·요청·추첨·관계자·만족도 …)

    @property
    def keu(self):
        return self.bonaem is not None and time.time() - self.majimak_sori < 2.0

    def ppurida(self, b, gal=1):
        for d in self.deut:
            if d.gal == gal:
                d.neoki(b)

    def ppurida_mp3(self, b):
        for d in self.deut_mp3:
            d.neoki(b)


def sangtae_bin():
    """저장하는 상황 — 노트북판 SangtaeJeojang 과 같은 것들에 갈래 이름·만족도를 더함"""
    return {
        "nal": "", "pdkey": "",
        "bh": {}, "bhg": {},                       # 입장 번호 -> [처음, 마지막], 구역
        "dang": [], "dangcheom": "", "dangsik": "", "dangseq": 0, "danggirok": [],
        "yc": [], "ycseq": 0, "ycgeuje": {},       # 요청 [번호, 시각글, 입장 번호, 종류, 처리, 때]
        "mal": [], "malseq": 0,                    # 해설사끼리 글
        "gwan": {}, "gwankey": "",                 # 관계자 열쇠 -> [역할, 이름, 처음, 마지막]
        "mj": [], "mjseq": 0,                      # 무전(저장하지 않음)
        "kam": {},                                 # 무대 카메라(저장하지 않음)
        "deutmok": {},                             # 듣는 폰 -> [때, 끊김, 입장 번호](저장하지 않음)
        "choeda": 0, "choedaT": 0,
        "sunseoI": 0, "sunseoT": 0,
        "gin": "", "ginseq": 0,
        "galnm": {},                               # 갈래 이름(비었으면 기본)
        "mjk": {"seq": 0, "q": "", "on": False, "doe": []},   # 만족도 묻기
        "jeojang": 0,
    }


class Deut:
    """듣는 분 한 사람 — 쌓아 둘 줄과 보내는 일꾼"""
    def __init__(self, w, ip, gal=1, nae=False):
        self.w = w
        self.ip = ip
        self.gal = gal
        self.nae = nae
        self.q = asyncio.Queue(DEUT_JUL)
        self.jugeum = False
        self.sseuneun = 0.0

    def neoki(self, b):
        if self.jugeum:
            return
        if self.q.full():
            try:
                self.q.get_nowait()          # 밀리면 오래된 조각을 버림
            except Exception:
                pass
        try:
            self.q.put_nowait(b)
        except Exception:
            pass


B = Bang()


# ───── 해설 소리 받기(7881) ─────
async def sori_batgi(r, w):
    peer = w.get_extra_info("peername")
    ireum = ""
    try:
        meori = await asyncio.wait_for(r.readline(), 10)
        mjul = meori.decode("utf-8", "replace").rstrip("\r\n")
        jul = mjul.split(" ", 2)
        # BYOD <열쇠> <이름>[\t행사 이름\t현장영상해설사\t기기 시각(초)]  — 1.2.0 앱은 이름까지만 보냄
        if len(jul) < 2 or jul[0] != "BYOD" or (B.yeolsoe and jul[1] != B.yeolsoe):
            w.write(b"NO\n"); await w.drain(); w.close(); return
        kan = (jul[2] if len(jul) > 2 else "").split("\t")
        ireum = kan[0].strip()[:40] or str(peer[0])
        jemok = kan[1].strip()[:80] if len(kan) > 1 else ""
        haeseolsa = kan[2].strip()[:40] if len(kan) > 2 else ""
        if len(kan) > 3:
            try:
                B.sigan_chai = float(kan[3]) - time.time()
                if not B.sigan_matchum:
                    B.sigan_matchum = True
                    sangtae_nal_bogi()       # 1.4.0 시계를 처음 알게 됨 — 이어 받은 상황이 지난 행사 것이면 따로 두고 새로
            except Exception:
                pass
        haengsa_butum(ireum, jemok, haeseolsa)
        # 새로 붙은 쪽이 넘겨받음(해설 교대)
        if B.bonaem is not None:
            try:
                B.bonaem.close()
            except Exception:
                pass
        B.bonaem = w
        B.bonaem_ireum = ireum
        B.bonaem_ip = str(peer[0])
        B.jeongji1 = False               # 1.4.0 방송 기기가 새로 붙으면 피디의 방송중지를 풂
        B.tonggwa.add(peer[0])          # 방송 폰은 「인터넷 되느냐」에 늘 「됨」으로 답해 와이파이를 붙들게
        w.write(("OK " + PAN + " " + B.pdkey + "\n").encode()); await w.drain()   # 1.4.0 피디 번호를 덧붙임(옛 앱은 OK 만 봄)
        while True:
            b = await asyncio.wait_for(r.read(4096), 30)
            if not b:
                break
            B.majimak_sori = time.time()
            B.majimak_butum = B.majimak_sori
            if B.jeongji1:
                continue                 # 피디가 방송중지 — 듣는 분께는 소리 없음(대기 음악)
            gal_naegi(1, b)
    except Exception:
        pass
    finally:
        if B.bonaem is w:
            B.bonaem = None
            B.bonaem_ireum = ""
            B.majimak_butum = time.time()
            if B.haengsa is not None:
                B.haengsa.kkeunkim("kkeunkim", ireum)
        try:
            w.close()
        except Exception:
            pass


def gal_naegi(g, b):
    """1.4.0 갈래 g 로 소리 한 조각을 내보내고 녹음"""
    B.sori_ttae[g] = time.time()
    B.ppurida(b, g)
    if g == 1:
        mp3_neoki(b)
        if not B.mp3_iss and B.haengsa is not None:
            B.haengsa.sseugi(b)          # mp3 를 만들 수 없는 공유기면 뮤 그대로 녹음
    elif B.haengsa is not None:
        B.haengsa.sseugi_gal(g, b)       # 갈래 2·3 은 같은 행사 폴더에 gal2_*.ul 따로


def gal1_sori():
    """갈래 1 소리가 오고 있는가(방송 앱이 붙어 있거나 해설 화면이 4초 안에 보냄)"""
    return B.bonaem is not None or time.time() - B.pcm_ttae[1] < 4


def keu_gal(g):
    """노트북판 keu — 갈래마다. 갈래 1 은 방송 앱 소리가 오고 있어도 켜진 것으로 봄"""
    if B.cue.get(g):
        return True
    return g == 1 and B.keu and not B.jeongji1


def keu_amugeona():
    return any(keu_gal(g) for g in range(1, GAL_SU + 1))


# ───── mp3 (화면을 잠가도 이어지는 길) ─────
def mp3_neoki(mu):
    if B.lame is None or B.lame.stdin is None:
        return
    try:
        B.lame.stdin.write(b"".join(DEC[x] for x in mu))
    except Exception:
        pass


async def mp3_ilkkun():
    if not B.mp3_iss:
        return
    cmd = os.environ.get("BYOD_LAME") or "lame"
    while True:
        try:
            B.lame = await asyncio.create_subprocess_exec(
                cmd, "--quiet", "-r", "-s", "16", "--bitwidth", "16", "--signed", "--little-endian",
                "-m", "m", "-b", "32", "--cbr", "-q", "7", "--flush", "-", "-",
                stdin=asyncio.subprocess.PIPE, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL)
            out = B.lame.stdout
            while True:
                b = await out.read(1024)
                if not b:
                    break
                B.ppurida_mp3(b)
                if B.haengsa is not None and gal1_sori():
                    B.haengsa.sseugi(b)      # 방송 기기(또는 갈래 1 해설 화면)가 붙어 있는 동안만 녹음
        except Exception:
            pass
        B.lame = None
        await asyncio.sleep(2)


async def goyo_ilkkun():
    """해설이 쉬면 소리 없음을 흘려 mp3 가 멈추지 않게, 저지연 쪽은 5초마다 한 조각으로 끊긴 폰을 가려냄"""
    bunTtae = 0.0
    while True:
        await asyncio.sleep(0.1)
        now = time.time()
        h = B.haengsa
        if h is not None:
            if now - bunTtae >= 60:
                bunTtae = now
                h.bun(deutnun_su())
            if B.bonaem is None and now - B.majimak_butum > IEUM:
                haengsa_maegam()
        if now - B.sori_ttae[1] > 0.3:
            mp3_neoki(GOYO)
        for g in range(1, GAL_SU + 1):
            if now - B.sori_ttae[g] > 0.3 and now - B.goyo_ttae[g] > 5:
                B.goyo_ttae[g] = now
                B.ppurida(bytes([0xFF]) * 160, g)
        # 막힌 폰 빼기
        for d in list(B.deut) + list(B.deut_mp3):
            if d.sseuneun and now - d.sseuneun > MAKHIM:
                kkeutnaegi(d)
        if B.jg_jung and now - B.jg_sijak > 300:
            jg_doerigi()                     # 점검이 5분을 넘으면 저절로 되돌림


def kkeutnaegi(d):
    d.jugeum = True
    try:
        d.w.close()
    except Exception:
        pass
    for l in (B.deut, B.deut_mp3):
        if d in l:
            l.remove(d)


def deutnun_su(gal=0):
    """진짜 듣는 분 수(안쪽 다시 받기 nae 는 빼고), gal 0 이면 모두"""
    return sum(1 for d in list(B.deut) + list(B.deut_mp3) if not d.nae and (gal == 0 or d.gal == gal))



# ───── 행사 녹음과 기록 (1.3.0) ─────
YOIL = ["월", "화", "수", "목", "금", "토", "일"]


def jigeum():
    """방송 기기 시계에 맞춘 지금 시각(초)"""
    return time.time() + B.sigan_chai


def kst(t):
    return time.gmtime(t + 9 * 3600)


def sigak_mal(t):
    g = kst(t)
    h = g.tm_hour
    return ("오전 " if h < 12 else "오후 ") + "%d시 %d분" % (h if 1 <= h <= 12 else (12 if h in (0, 12) else h - 12), g.tm_min)


def nal_mal(t):
    g = kst(t)
    return "%d년 %d월 %d일 %s요일" % (g.tm_year, g.tm_mon, g.tm_mday, YOIL[g.tm_wday])


def gilee_mal(cho):
    cho = int(round(cho))
    h, m, s2 = cho // 3600, (cho % 3600) // 60, cho % 60
    if h:
        return "%d시간 %d분" % (h, m)
    if m:
        return "%d분 %d초" % (m, s2) if s2 else "%d분" % m
    return "%d초" % s2


def hms(t=None):
    return time.strftime("%H:%M:%S", kst(jigeum() if t is None else t))


def hm(t):
    return time.strftime("%H:%M", kst(t))


def ymd(t=None):
    return time.strftime("%Y%m%d", kst(jigeum() if t is None else t))


def ts(t=None):
    return time.strftime("%Y-%m-%d %H:%M:%S", kst(jigeum() if t is None else t))


JONGRYU = {"dowum": "도움 요청", "gungeum": "궁금함", "kkeunkim": "소리가 끊김",
           "gunggeum": "무엇이 보이는지 궁금해요", "jakda": "소리가 작아요", "cheoncheon": "조금 천천히"}


class Haengsa:
    """행사 하나 — 녹음 조각(1분마다)과 기록(girok.json)"""
    def __init__(self, sid=None, jemok="", haeseolsa="", ireum="", girok=None):
        if girok is not None:
            self.g = girok
            self.sid = girok["sid"]
        else:
            t = jigeum()
            g = kst(t)
            self.sid = "byod%02d%02d%02d%02d%02d%02d" % (g.tm_year % 100, g.tm_mon, g.tm_mday, g.tm_hour, g.tm_min, g.tm_sec)
            self.g = {"sid": self.sid, "jemok": jemok or ("현장영상해설 방송 " + nal_mal(t) + " " + sigak_mal(t)),
                      "haeseolsa": haeseolsa, "sijak": t, "kkeut": 0, "majimak": t,
                      "bun": [], "yocheong": [], "kkeunkim": [[t, "butum", ireum]], "jogak": [],
                      "sangtae": "jinhaeng", "olim": 0, "hyeongsik": "mp3" if B.mp3_iss else "mulaw",
                      "pan": PAN, "chobeon_deut": 0}
        self.teo = os.path.join(HAENGSA_TEO, self.sid)
        os.makedirs(self.teo, exist_ok=True)
        self.f = None
        self.f_ttae = 0.0
        self.jeojang_ttae = 0.0
        self.gf = {}                 # 1.4.0 갈래 2·3 녹음 파일 -> [파일, 연 때, 쓴 때]
        if girok is None:
            self.jeojang()

    def sseugi(self, b):
        now = time.time()
        if self.g["sangtae"] != "jinhaeng":
            return
        if self.f is None or now - self.f_ttae >= JOGAK_CHO:
            self.jogak_datgi()
            beon = len(self.g["jogak"]) + 1
            nm = "%04d.%s" % (beon, "mp3" if self.g["hyeongsik"] == "mp3" else "ul")
            self.f = open(os.path.join(self.teo, nm), "ab")
            self.f_ttae = now
            self.g["jogak"].append(nm)
            self.jeojang()
        try:
            self.f.write(b)
            if now - self.jeojang_ttae >= 10:    # 10초마다 저장 장치에 확실히 씀(전기가 끊겨도 10초 안쪽만 잃음)
                self.jeojang_ttae = now
                self.f.flush()
                os.fsync(self.f.fileno())
        except Exception:
            pass
        self.g["majimak"] = jigeum()

    def sseugi_gal(self, gal, b):
        """1.4.0 갈래 2·3 녹음 — 뮤 그대로 1분 조각(gal2_0001.ul …)"""
        now = time.time()
        if self.g["sangtae"] != "jinhaeng":
            return
        k = str(gal)
        mok = self.g.setdefault("gal_jogak", {}).setdefault(k, [])
        v = self.gf.get(k)
        try:
            if v is None or now - v[1] >= JOGAK_CHO:
                if v is not None:
                    self.gal_datgi(k)
                nm = "gal%s_%04d.ul" % (k, len(mok) + 1)
                v = [open(os.path.join(self.teo, nm), "ab"), now, 0.0]
                self.gf[k] = v
                mok.append(nm)
                self.jeojang()
            v[0].write(b)
            if now - v[2] >= 10:
                v[2] = now
                v[0].flush()
                os.fsync(v[0].fileno())
        except Exception:
            pass

    def gal_datgi(self, k=None):
        for kk in ([k] if k else list(self.gf.keys())):
            v = self.gf.pop(kk, None)
            if v is None:
                continue
            try:
                v[0].flush()
                os.fsync(v[0].fileno())
                v[0].close()
            except Exception:
                pass

    def jogak_datgi(self):
        if self.f is not None:
            try:
                self.f.flush()
                os.fsync(self.f.fileno())        # 전기가 끊겨도 닫은 조각은 남게
                self.f.close()
            except Exception:
                pass
            self.f = None

    def bun(self, n):
        self.g["bun"].append([jigeum(), n])
        self.jeojang()

    def yocheong(self, k, n):
        self.g["yocheong"].append([jigeum(), k, n])
        self.jeojang()

    def kkeunkim(self, jong, ireum):
        self.g["kkeunkim"].append([jigeum(), jong, ireum])
        self.jeojang()

    def jeojang(self):
        p = os.path.join(self.teo, "girok.json")
        try:
            with open(p + ".tmp", "w", encoding="utf-8") as f:
                json.dump(self.g, f, ensure_ascii=False)
                f.flush()
                os.fsync(f.fileno())
            os.replace(p + ".tmp", p)
        except Exception:
            pass

    def gilee(self):
        """녹음 길이(초) — 조각 크기로 셈(mp3 32kbps = 1초에 4000바이트, 뮤 = 1초에 16000바이트)"""
        hap = 0
        for nm in self.g["jogak"]:
            try:
                hap += os.path.getsize(os.path.join(self.teo, nm))
            except Exception:
                pass
        return hap / (4000.0 if self.g["hyeongsik"] == "mp3" else 16000.0)

    def maegam(self):
        """행사 마감 — 녹음 한 덩어리와 결과보고를 만듦"""
        if self.g["sangtae"] != "jinhaeng":
            return
        self.jogak_datgi()
        self.gal_datgi()
        self.g["kkeut"] = self.g.get("majimak") or jigeum()
        self.g["sangtae"] = "kkeut"
        self.g["gilee"] = self.gilee()
        nm = "nokeum." + ("mp3" if self.g["hyeongsik"] == "mp3" else "ul")
        iieugi(self.teo, self.g["jogak"], nm)
        for k, mok in (self.g.get("gal_jogak") or {}).items():
            iieugi(self.teo, mok, "gal%s_nokeum.ul" % k)    # 1.4.0 갈래 2·3 녹음 한 덩어리
        try:
            t = bogo_mandeul(self.g)
            with open(os.path.join(self.teo, "bogo.txt"), "wb") as f:
                f.write(t.replace("\n", "\r\n").encode("cp949", "replace"))
        except Exception:
            pass
        self.jeojang()


def iieugi(teo, mok, nm):
    try:
        with open(os.path.join(teo, nm + ".tmp"), "wb") as o:
            for j in mok:
                try:
                    with open(os.path.join(teo, j), "rb") as f:
                        shutil.copyfileobj(f, o)
                except Exception:
                    pass
        os.replace(os.path.join(teo, nm + ".tmp"), os.path.join(teo, nm))
    except Exception:
        pass


def bogo_mandeul(g):
    """한 장짜리 결과보고(글) — 낭독기와 점자정보단말기로 읽히게 기호 없이
    1.4.0 노트북판 결과보고 항목(입장 번호 청취, 요청, 추첨, 식순·순간 표시)과 만족도·여러 갈래를 더함"""
    S = B.S
    L = []
    L.append("현장영상해설 방송 결과보고")
    L.append("사단법인 한국시각장애인현장영상해설협회")
    L.append("")
    L.append("1. 행사 개요")
    L.append("행사 이름: " + g["jemok"])
    L.append("날짜: " + nal_mal(g["sijak"]))
    L.append("현장영상해설사: " + (g.get("haeseolsa") or "적지 않음"))
    kkeut = g.get("kkeut") or jigeum()      # 진행 중이면 지금까지
    L.append("시작: " + sigak_mal(g["sijak"]))
    L.append("끝: " + (sigak_mal(kkeut) if g.get("kkeut") else "아직 진행 중"))
    L.append("방송 시간: " + gilee_mal(max(0, kkeut - g["sijak"])))
    L.append("")
    L.append("2. 이용 현황")
    bun = g.get("bun") or []
    if bun:
        mx = max(bun, key=lambda x: x[1])
        pyeong = sum(x[1] for x in bun) / len(bun)
        L.append("듣는 분: 가장 많을 때 %d명(%s), 평균 %d명" % (mx[1], sigak_mal(mx[0]), int(round(pyeong))))
        L.append("10분 간격 듣는 분 수")
        sijak = g["sijak"]
        dah = sijak
        for t, n in bun:
            if t >= dah:
                L.append("  " + sigak_mal(t) + " " + str(n) + "명")
                dah = t + 600
    else:
        L.append("듣는 분: 기록 없음")
    bh = S["bh"]
    su = len(bh)
    L.append("입장 번호로 들으신 분: %d명" % su)
    if su:
        a30 = a60 = a60p = 0
        hap = 0.0
        for v in bh.values():
            m = (v[1] - v[0]) / 60.0
            hap += m
            if m < 30:
                a30 += 1
            elif m < 60:
                a60 += 1
            else:
                a60p += 1
        L.append("한 분이 들으신 시간: 평균 %d분 (30분 안 %d명, 30분~1시간 %d명, 1시간 넘게 %d명)" % (int(round(hap / su)), a30, a60, a60p))
    deut_segi()
    L.append("가장 많이 함께 들은 때: %d명%s" % (S["choeda"], (" (" + hm(S["choedaT"]) + ")") if S["choedaT"] else ""))
    gy = guyeok_mok()
    if gy:
        L.append("구역별 입장 번호:")
        for x in gy:
            L.append("  %s 구역 %d명" % (x["g"], x["modu"]))
    galnun = [k for k in sorted((g.get("gal_jogak") or {}).keys())]
    if galnun:
        L.append("여러 갈래 해설: 갈래 1 " + gal_ireum(1) + ", " + ", ".join("갈래 %s %s" % (k, gal_ireum(int(k))) for k in galnun))
    L.append("")
    L.append("3. 듣는 분 요청")
    yc = S["yc"]
    if yc:
        dw = [y for y in yc if y[3] == "dowum"]
        L.append("도움 요청: %d건 (도와드림 %d건)" % (len(dw), sum(1 for y in dw if y[4] == "1")))
        for y in dw:
            L.append("  " + y[1] + " " + ((y[2] + "번") if y[2] else "번호 없음") + (" " + guyeok_of(y[2]) + " 구역" if guyeok_of(y[2]) else "") + (" (도와드림)" if y[4] == "1" else " (확인 안 됨)"))
        jong = {}
        for y in yc:
            if y[3] != "dowum":
                jong[y[3]] = jong.get(y[3], 0) + 1
        if not jong:
            L.append("현장영상해설사에게 한 요청: 없음")
        else:
            L.append("현장영상해설사에게 한 요청:")
            for k, v in jong.items():
                L.append("  %s %d건" % (yc_ireum(k), v))
    else:
        gyc = g.get("yocheong") or []
        if gyc:
            L.append("도움 요청: 모두 %d번" % len(gyc))
            for t, k, n in gyc:
                L.append("  " + sigak_mal(t) + " " + JONGRYU.get(k, k) + " (" + (n + "번" if n else "번호 없음") + ")")
        else:
            L.append("도움 요청: 없음")
    L.append("")
    L.append("4. 행운권 추첨")
    if S["danggirok"]:
        for t, n, mal in S["danggirok"]:
            L.append("  %s %s번 %s" % (hms(t), n, mal))
    else:
        L.append("추첨 없음")
    L.append("")
    L.append("5. 식순과 중요한 순간 표시")
    jr = jaryo_ilkgi()
    try:
        jo = json.loads(jr) if jr else {}
        cnt = 0
        for x in (jo.get("sunseo") or []):
            cnt += 1
            L.append("  식순 %d. %s" % (cnt, (x.get("n") if isinstance(x, dict) else str(x)) or ""))
    except Exception:
        pass
    py = g.get("pyosi") or []
    for t, cho, ss, nm, tt in py:
        cho = int(cho)
        L.append("  표시 %s (녹음 %d분 %d초) %s %s%s" % (hms(t), cho // 60, cho % 60, ss, tt, (" - " + nm) if nm else ""))
    if not py:
        L.append("  순간 표시 없음")
    L.append("")
    L.append("6. 만족도")
    doe = S["mjk"]["doe"]
    if not doe:
        L.append("만족도 묻기 없음")
    for r in doe:
        dap = list(r["dap"].values())
        L.append("물음: " + r["q"])
        if not dap:
            L.append("  답한 분 없음")
            continue
        gak = [0] * 5
        for x in dap:
            gak[x[1] - 1] += 1
        L.append("  답한 분 %d명, 평균 %.1f점" % (len(dap), sum(x[1] for x in dap) / float(len(dap))))
        L.append("  " + ", ".join("%s %d명" % (MANJOK_MAL[p], gak[p - 1]) for p in (5, 4, 3, 2, 1)))
        mal = [x for x in dap if x[2]]
        if mal:
            L.append("  한마디:")
            for x in mal:
                L.append("    " + ((x[0] + "번") if x[0] else "번호 없음") + " " + str(x[1]) + "점 " + x[2])
    L.append("")
    L.append("7. 녹음과 방송 기기")
    kk = [x for x in (g.get("kkeunkim") or []) if x[1] == "kkeunkim"]
    L.append("방송 기기 끊김: " + ("없음" if not kk else "%d번" % len(kk)))
    jeon = None
    for t, jong2, ir in (g.get("kkeunkim") or []):
        if jong2 == "kkeunkim":
            jeon = t
            L.append("  " + sigak_mal(t) + " 끊김" + (" (" + ir + ")" if ir else ""))
        elif jong2 == "butum" and jeon is not None:
            L.append("  " + sigak_mal(t) + " 다시 붙음" + (" (" + ir + ")" if ir else ""))
    gil = g.get("gilee", 0) if g.get("sangtae") == "kkeut" else (B.haengsa.gilee() if B.haengsa is not None and B.haengsa.sid == g["sid"] else g.get("gilee", 0))
    L.append("녹음: " + g["sid"] + (".mp3" if g.get("hyeongsik") == "mp3" else ".ul") + ", 길이 " + gilee_mal(gil))
    for k in galnun:
        try:
            cho = sum(os.path.getsize(os.path.join(HAENGSA_TEO, g["sid"], nm)) for nm in g["gal_jogak"][k]) / 16000.0
        except Exception:
            cho = 0
        L.append("갈래 %s 녹음: gal%s_nokeum.ul, 길이 %s (공유기에 남음)" % (k, k, gilee_mal(cho)))
    L.append("")
    L.append("참고: 이 보고서는 입장 번호만 모았으며 이름과 전화번호 같은 개인정보는 모으지 않았습니다.")
    L.append("LVD 송출 공유기 서버 " + g.get("pan", PAN) + "판")
    return "\n".join(L) + "\n"


def haengsa_butum(ireum, jemok, haeseolsa):
    """방송 기기가 붙음 — 5분 안이면 같은 행사, 아니면 새 행사"""
    now = time.time()
    h = B.haengsa
    if h is not None and h.g["sangtae"] == "jinhaeng" and now - B.majimak_butum <= IEUM:
        h.kkeunkim("butum", ireum)
        if jemok and (h.g["jemok"].startswith("현장영상해설 방송 ") or h.g["jemok"].startswith("BYOD 현장 방송 ")):
            h.g["jemok"] = jemok
        if haeseolsa and haeseolsa not in (h.g.get("haeseolsa") or ""):
            h.g["haeseolsa"] = (h.g["haeseolsa"] + ", " + haeseolsa) if h.g.get("haeseolsa") else haeseolsa   # 해설사 교대
        h.jeojang()
    else:
        if h is not None:
            h.maegam()
        B.haengsa = Haengsa(jemok=jemok, haeseolsa=haeseolsa, ireum=ireum)
    B.majimak_butum = now


def haengsa_maegam():
    h = B.haengsa
    if h is not None:
        h.maegam()
        B.haengsa = None


def haengsa_mokrok():
    l = []
    try:
        for sid in sorted(os.listdir(HAENGSA_TEO), reverse=True):
            p = os.path.join(HAENGSA_TEO, sid, "girok.json")
            try:
                with open(p, encoding="utf-8") as f:
                    g = json.load(f)
            except Exception:
                continue
            l.append({"sid": sid, "jemok": g.get("jemok", ""), "haeseolsa": g.get("haeseolsa", ""),
                      "sijak": g.get("sijak", 0), "kkeut": g.get("kkeut", 0), "gilee": g.get("gilee", 0),
                      "sangtae": g.get("sangtae", ""), "olim": g.get("olim", 0), "hyeongsik": g.get("hyeongsik", "mp3")})
    except Exception:
        pass
    return l


def haengsa_girok(sid):
    try:
        with open(os.path.join(HAENGSA_TEO, os.path.basename(sid), "girok.json"), encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return None


def haengsa_olim(sid):
    g = haengsa_girok(sid)
    if g is None:
        return False
    g["olim"] = 1
    h = Haengsa(girok=g)
    h.jeojang()
    return True


def haengsa_dasi_yeolgi():
    """서버가 다시 켜졌을 때(전기가 끊겼다 들어옴) — 진행 중이던 행사가 5분 안이면 이어 받고, 아니면 마감"""
    for m in haengsa_mokrok():
        if m["sangtae"] != "jinhaeng":
            continue
        g = haengsa_girok(m["sid"])
        if g is None:
            continue
        h = Haengsa(girok=g)
        h.maegam()          # 공유기가 꺼졌던 동안은 시계를 믿을 수 없어 이어 붙이지 않고 마감(녹음 조각은 모두 남음)


# ───── 공유기 스스로 배프에 올리기(인터넷이 될 때, 5분마다) ─────
def nas_bonaegi(sid, jong, b, deo=""):
    """나스 byod_olligi.php 로 4메가씩 나눠 올림"""
    q0 = "%s?k=%s&sid=%s&jong=%s" % (NAS_OLLIGI, OLLIGI_YEOLSOE, sid, jong)
    KEUGI = 4 * 1024 * 1024
    n = 0
    for i in range(0, max(1, len(b)), KEUGI):
        rq = urllib.request.Request(q0 + "&step=jogak&n=%d" % n, data=b[i:i + KEUGI], method="POST")
        with urllib.request.urlopen(rq, timeout=60) as r:
            if b'"ok":true' not in r.read():
                raise IOError("jogak")
        n += 1
    rq = urllib.request.Request(q0 + "&step=kkeut&su=%d&gil=%d%s" % (n, len(b), deo), data=b"", method="POST")
    with urllib.request.urlopen(rq, timeout=120) as r:
        if b'"ok":true' not in r.read():
            raise IOError("kkeut")


def haengsa_olligi(sid):
    g = haengsa_girok(sid)
    if g is None or g.get("sangtae") != "kkeut" or g.get("olim"):
        return False
    teo = os.path.join(HAENGSA_TEO, sid)
    nm = "nokeum." + ("mp3" if g.get("hyeongsik") == "mp3" else "ul")
    with open(os.path.join(teo, nm), "rb") as f:
        nas_bonaegi(sid, "mp3" if nm.endswith("mp3") else "ul", f.read())
    with open(os.path.join(teo, "bogo.txt"), "rb") as f:
        nas_bonaegi(sid, "bogo", f.read())
    # 1.4.0 (261024) 갈래 2·3 해설 녹음과 현장 기록 파일도 나스로(녹음은 무조건 나스에) — byod_olligi.php 1.5.0 이 받음
    for gg in ("gal2", "gal3"):
        gp = os.path.join(teo, gg + "_nokeum.ul")
        if os.path.isfile(gp) and os.path.getsize(gp) > 0:
            with open(gp, "rb") as f:
                nas_bonaegi(sid, gg, f.read())
    for nm in sorted(os.listdir(teo)):
        if (nm.endswith(".csv") or nm in ("bogoseo.txt", "sangtae.json")) and re.match(r"^[A-Za-z0-9_.\-]{1,80}$", nm):
            with open(os.path.join(teo, nm), "rb") as f:
                nas_bonaegi(sid, "pail", f.read(), "&nm=" + nm)
    nas_bonaegi(sid, "girok", json.dumps(g, ensure_ascii=False).encode("utf-8"))
    return haengsa_olim(sid)


async def olligi_ilkkun():
    gan = float(os.environ.get("BYOD_OLLIGI_GAN", "300"))
    while True:
        await asyncio.sleep(gan)
        if B.nok_olrinun:
            continue
        for m in haengsa_mokrok():
            if m["sangtae"] == "kkeut" and not m["olim"]:
                try:
                    await asyncio.get_event_loop().run_in_executor(None, haengsa_olligi, m["sid"])
                except Exception:
                    break       # 인터넷이 없으면 다음 5분에


async def nok_olligi():
    """1.4.0 /nokeum_olligi — 마친 행사 녹음을 지금 나스에 올림(지금의 다시 듣기 올리기 길)"""
    ok = mot = 0
    try:
        for m in haengsa_mokrok():
            if m["sangtae"] == "kkeut" and not m["olim"]:
                try:
                    if await asyncio.get_event_loop().run_in_executor(None, haengsa_olligi, m["sid"]):
                        ok += 1
                except Exception:
                    mot += 1
        B.nok_mal = "올린 행사 녹음 %d개" % ok + (", 못 올린 행사 녹음 %d개(인터넷 확인)" % mot if mot else "")
    except Exception as e:
        B.nok_mal = "올리기 오류: " + str(e)
    B.nok_olrinun = False


# ───── 1.4.0 노트북판에서 옮긴 것: 상황 다루기 ─────
MANJOK_MAL = {5: "매우 좋음", 4: "좋음", 3: "보통", 2: "아쉬움", 1: "매우 아쉬움"}


def jg_now():
    return B.jg_jung and time.time() - B.jg_sijak < 300


def girok_teo():
    """기록 파일을 두는 곳 — 지금 행사 폴더, 행사가 없으면 그날 폴더"""
    if B.haengsa is not None:
        return B.haengsa.teo
    d = os.path.join(HAENGSA_TEO, "nal_" + ymd())
    os.makedirs(d, exist_ok=True)
    return d


def csv_jul(ireum, jul):
    """노트북판 JgFile.AppendAllText — UTF-8(BOM), 점검 중에는 쓰지 않음"""
    if jg_now():
        return
    try:
        p = os.path.join(girok_teo(), ireum + "_" + ymd() + ".csv")
        sae = not os.path.isfile(p)
        with open(p, "ab") as f:
            if sae:
                f.write(b"\xef\xbb\xbf")
            f.write((jul + "\r\n").encode("utf-8"))
    except Exception:
        pass


def susja(n):
    return 0 < len(n) <= 4 and n.isdigit() and n.isascii()


def yc_ireum(k):
    return {"dowum": "도움이 필요합니다", "jakda": "소리가 작아요", "cheoncheon": "조금 천천히",
            "gunggeum": "무엇이 보이는지 궁금해요", "kkeunkim": "소리가 끊겨요"}.get(k, k)


def gwan_ireum(r):
    return {"haeseol": "해설사", "jinhaeng": "진행요원", "kamera": "무대 카메라", "pd": "피디 보조"}.get(r, r)


def nanseu(n):
    a = "abcdefghjkmnpqrstuvwxyz23456789"
    return "".join(a[x % 31] for x in os.urandom(n))


def gwan_key():
    if not B.S["gwankey"]:
        B.S["gwankey"] = nanseu(8)
    return B.S["gwankey"]


def gwan_ok(q):
    t = gap(q, "gt") or gap(q, "t")
    if len(t) < 10:
        return False
    v = B.S["gwan"].get(t)
    if v is None:
        return False
    v[3] = jigeum()
    return True


def guyeok_of(n):
    return B.S["bhg"].get(n, "") if n else ""


def guyeok_mok():
    """구역별 인원 — 번호 카드의 구역(g)으로"""
    now = jigeum()
    se = {}
    for n, v in B.S["bh"].items():
        g = B.S["bhg"].get(n, "")
        if not g:
            continue
        x = se.setdefault(g, {"g": g, "jigeum": 0, "modu": 0})
        x["modu"] += 1
        if now - v[1] < 75:
            x["jigeum"] += 1
    return [se[k] for k in sorted(se.keys())]


def mang_mok():
    """공유기별 인원 — 듣는 폰이 들어온 주소 묶음(앞 세 마디). 플린트 2 한 대는 192.168.8.0/22 라 네 묶음을 192.168.8 하나로 셈"""
    se = {}
    for d in list(B.deut) + list(B.deut_mp3):
        if d.nae:
            continue
        p = d.ip.split(".")
        if len(p) != 4:
            continue
        m = ".".join(p[:3])
        if p[0] == "192" and p[1] == "168" and SEOBEO_IP.startswith("192.168.8.") and p[2] in ("8", "9", "10", "11"):
            m = "192.168.8"
        se[m] = se.get(m, 0) + 1
    return [{"m": k, "su": se[k]} for k in sorted(se.keys())]


def gal_ireum(g):
    return B.S["galnm"].get(str(g)) or GAL_GIBON.get(g, "갈래 %d 해설" % g)


def gal_mok():
    now = time.time()
    return [{"n": g, "nm": gal_ireum(g), "on": now - B.sori_ttae[g] < 4, "su": deutnun_su(g)} for g in range(1, GAL_SU + 1)]


def deut_segi():
    now = time.time()
    n = sum(1 for v in B.S["deutmok"].values() if now - v[0] < 45)
    if n > B.S["choeda"]:
        B.S["choeda"] = n
        B.S["choedaT"] = jigeum()


def dang_json():
    return list(B.S["dang"])


def jiyeon_mal():
    now = time.time()
    hap = keun = 0.0
    su = 0
    for k in list(B.jiyeon.keys()):
        v = B.jiyeon[k]
        if now - v[1] > 20:
            del B.jiyeon[k]
            continue
        hap += v[0]
        su += 1
        keun = max(keun, v[0])
    if su == 0:
        return ""
    pyeong = hap / su + 200
    return "소리 늦음 — 듣는 폰 %d대 평균 약 %.1f초, 가장 늦은 폰 약 %.1f초." % (su, pyeong / 1000.0, (keun + 200) / 1000.0)


def jaryo_pail():
    return os.path.join(TEO, "jaryo.json")


def jaryo_ilkgi():
    try:
        with open(jaryo_pail(), encoding="utf-8") as f:
            return f.read()
    except Exception:
        return ""


def jaryo_batgi():
    try:
        rq = urllib.request.Request(JARYO_URL + "?z=%d" % int(time.time() * 1000))
        with urllib.request.urlopen(rq, timeout=8) as r:
            t = r.read().decode("utf-8", "replace").strip()
        if not (t.startswith("{") and t.endswith("}")):
            return False
        with open(jaryo_pail() + ".tmp", "w", encoding="utf-8") as f:
            f.write(t)
        os.replace(jaryo_pail() + ".tmp", jaryo_pail())
        return True
    except Exception:
        return False


def manjok_mok():
    m = B.S["mjk"]
    r = m["doe"][-1] if m["doe"] else None
    dap = list(r["dap"].values()) if r else []
    gak = [0] * 5
    for x in dap:
        gak[x[1] - 1] += 1
    pyeong = round(sum(x[1] for x in dap) / float(len(dap)), 1) if dap else 0
    return {"seq": m["seq"], "q": r["q"] if r else "", "on": bool(m["on"]), "su": len(dap), "pyeong": pyeong, "gak": gak,
            "mal": [{"n": x[0], "p": x[1], "t": x[2]} for x in dap if x[2]]}


# ───── 1.4.0 스스로 점검(점검 중에는 파일에 쓰지 않고, 끝나면 되돌림) ─────
def jg_damgi():
    try:
        S = B.S
        bak = {}
        for k in ("bh", "bhg", "dang", "dangcheom", "dangsik", "danggirok", "yc", "ycgeuje", "mal", "gwan", "mj", "kam",
                  "choeda", "choedaT", "sunseoI", "sunseoT", "gin", "galnm", "mjk"):
            bak[k] = copy.deepcopy(S[k])
        B.jg_damgi = bak
        return True
    except Exception:
        return False


def jg_doerigi():
    try:
        S = B.S
        bak = B.jg_damgi or {}
        for k, v in bak.items():
            if k == "gin":
                if S["gin"] != v:
                    S["gin"] = v
                    S["ginseq"] += 1
            elif k == "mjk":
                if S["mjk"]["on"] != v["on"] or S["mjk"]["q"] != v["q"]:
                    v["seq"] = S["mjk"]["seq"] + 1     # 듣는 화면이 점검 물음을 내리게 번호를 올림
                S["mjk"] = v
            else:
                S[k] = v
        for k in [k for k in S["deutmok"] if k.startswith("jg")]:
            del S["deutmok"][k]
        for k in [k for k in B.jiyeon if k.startswith("jg")]:
            del B.jiyeon[k]
    except Exception:
        pass
    B.jg_jung = False
    B.jg_damgi = None


# ───── 1.4.0 1분마다 저절로 저장, 다시 켜지면 이어 받기 ─────
def sangtae_pail():
    return os.path.join(HAENGSA_TEO, "sangtae.json")


def anjeon_sseugi(p, b):
    if jg_now():
        return
    with open(p + ".tmp", "wb") as f:
        f.write(b)
        f.flush()
        os.fsync(f.fileno())
    if os.path.isfile(p):
        try:
            shutil.copyfile(p, p + ".bak")
        except Exception:
            pass
    os.replace(p + ".tmp", p)


def sangtae_jeojang():
    S = B.S
    if not S["nal"]:
        S["nal"] = ymd()
    S["pdkey"] = B.pdkey
    S["jeojang"] = jigeum()
    d = {k: v for k, v in S.items() if k not in ("mj", "kam", "deutmok")}
    b = json.dumps(d, ensure_ascii=False).encode("utf-8")
    anjeon_sseugi(sangtae_pail(), b)
    if B.haengsa is not None:
        anjeon_sseugi(os.path.join(B.haengsa.teo, "sangtae.json"), b)


def sangtae_bulleo():
    p = sangtae_pail()
    d = None
    for x in (p, p + ".bak"):
        try:
            with open(x, encoding="utf-8") as f:
                d = json.load(f)
            break
        except Exception:
            d = None
    if not d:
        return
    S = sangtae_bin()
    for k in S:
        if k in d and k not in ("mj", "kam", "deutmok"):
            S[k] = d[k]
    S["mjk"]["doe"] = [dict(r, dap={i: list(v) for i, v in (r.get("dap") or {}).items()}) for r in (S["mjk"].get("doe") or [])]
    B.S = S
    if len(S.get("pdkey") or "") == 4 and not os.environ.get("BYOD_PDKEY"):
        B.pdkey = S["pdkey"]        # 피디 화면 주소(?k=)가 다시 켜진 뒤에도 그대로 쓰이게
    B.bulleoom = True
    B.jadong_ieo = "앞서 저장된 상황을 이어 받았습니다(입장 번호 %d개)" % len(S["bh"])
    B.jadong_mal = B.jadong_ieo


def sangtae_nal_bogi():
    """방송 기기가 처음 시계를 알려 줌 — 이어 받은 상황이 6시간 넘게 지난 것이면 따로 두고 새로 시작(켜진 뒤 들어온 입장 번호는 남김)"""
    S = B.S
    if not B.bulleoom:
        S["nal"] = ymd()
        return
    if S.get("jeojang") and jigeum() - S["jeojang"] < 6 * 3600:
        return
    try:
        shutil.copyfile(sangtae_pail(), os.path.join(HAENGSA_TEO, "sangtae_%s.json" % (S.get("nal") or "yet")))
    except Exception:
        pass
    sae = sangtae_bin()
    now = jigeum()
    for n in B.boot_bh:
        if n in S["bh"]:
            sae["bh"][n] = [now, now]
            if n in S["bhg"]:
                sae["bhg"][n] = S["bhg"][n]
    sae["nal"] = ymd()
    B.S = sae
    B.bulleoom = False
    B.jadong_ieo = ""


def jadong_jeojang():
    try:
        sangtae_jeojang()
        if B.haengsa is not None:
            t = bogo_mandeul(B.haengsa.g)
            anjeon_sseugi(os.path.join(B.haengsa.teo, "bogoseo.txt"), t.replace("\n", "\r\n").encode("cp949", "replace"))
        B.jadong_mal = ((B.jadong_ieo + ". ") if B.jadong_ieo else "") + "마지막 저절로 저장 " + hms()
        if len(B.jadong_mal) > 120:
            B.jadong_mal = "마지막 저절로 저장 " + hms()
    except Exception as e:
        B.jadong_mal = "저절로 저장 오류: " + str(e)


async def jadong_ilkkun():
    await asyncio.sleep(5)
    while True:
        jadong_jeojang()
        await asyncio.sleep(JADONG_GAN)


# ───── 듣기 화면과 길(7880, 80번은 방화벽이 돌려줌) ─────
CAPTIVE = ("generate_204", "gen_204", "hotspot-detect", "library/test/success", "connecttest.txt",
           "ncsi.txt", "success.txt", "canonical.html", "kindle-wifi", "mobile/status.php", "check_network_status")


def meori_bonae(w, code, mal, chong, gil=0, deo=""):
    h = "HTTP/1.1 %d %s\r\nContent-Type: %s\r\n" % (code, mal, chong)
    if gil >= 0:
        h += "Content-Length: %d\r\n" % gil
    h += "Cache-Control: no-store\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Headers: *\r\n" + deo + "Connection: close\r\n\r\n"
    w.write(h.encode("ascii"))


def geulja(w, code, chong, t, deo=""):
    b = t.encode("utf-8") if isinstance(t, str) else t
    mal = {200: "OK", 204: "No Content", 302: "Found", 400: "Bad Request", 404: "Not Found", 403: "Forbidden"}.get(code, "OK")
    meori_bonae(w, code, mal, chong, len(b), deo)
    if b:
        w.write(b)


def js(w, d, code=200):
    geulja(w, code, "application/json; charset=utf-8", json.dumps(d, ensure_ascii=False, separators=(",", ":")))


def dolligi(w, loc):
    w.write(("HTTP/1.1 302 Found\r\nLocation: " + loc + "\r\nCache-Control: no-store\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").encode("utf-8"))


def gap(q, k):
    """노트북판 Gap — 풀지 않은 값"""
    for p in q.split("&"):
        e = p.find("=")
        if e > 0 and p[:e] == k:
            return p[e + 1:]
        if e < 0 and p == k:
            return ""
    return ""


def pul(s):
    try:
        return urllib.parse.unquote_plus(s)
    except Exception:
        return s


def jjokgeul(w, ireum):
    p = os.path.join(TEO, ireum)
    if os.path.isfile(p) and os.path.getsize(p) > 100:
        with open(p, "rb") as f:
            geulja(w, 200, "text/html; charset=utf-8", f.read())
    else:
        geulja(w, 404, "text/plain; charset=utf-8", ireum + " 파일이 없습니다. LVD 송출 앱 더 보기의 플린트 2 준비하기를 다시 하십시오.")


def deut_hwamyeon():
    p = os.path.join(TEO, "deut.html")
    if os.path.isfile(p) and os.path.getsize(p) > 100:
        with open(p, "rb") as f:
            return f.read()
    return DEUT_HTML.replace("%%APP_IOS%%", APP_IOS).replace("%%APP_AOS%%", APP_AOS).encode("utf-8")


def tls_juso(gil, q):
    return "https://" + SEOBEO_IP + ("" if B.tport == 443 else ":%d" % B.tport) + gil + ("?" + q if q else "")


def sangtae(heoga=False, gal=1):
    S = B.S
    now = time.time()
    d = {
        "hyeonjang": True, "byod": True, "flint": True, "pan": "BYOD " + PAN, "bild": BILD,
        "keu": keu_gal(gal),
        "mic": (gal == 1 and B.bonaem is not None) or now - B.mic_ttae[gal] < 4,
        "bonaem": B.bonaem_ireum,
        "n": deutnun_su(), "n_jeo": sum(1 for x in B.deut if not x.nae), "n_mp3": len(B.deut_mp3),
        "haengsa": B.haengsa.sid if B.haengsa else "",
        "jemok": B.haengsa.g.get("jemok", "") if B.haengsa else "", "haeseolsa": B.haengsa.g.get("haeseolsa", "") if B.haengsa else "",
        "mp3": B.mp3_iss, "ttuigi": B.ttuigi, "tonggwa": B.tonggwa_ttae, "yseq": B.yseq,
        "nas": B.nas_mal,
        "dang": S["dangcheom"], "dseq": S["dangseq"], "dsik": S["dangsik"], "dmok": dang_json(),
        "gin": S["gin"], "gseq": S["ginseq"], "saepan": "",
        "gal": gal_mok(),
        "mj": S["mjk"]["q"] if S["mjk"]["on"] else "", "mjseq": S["mjk"]["seq"],
    }
    if heoga:
        for t in [t for t, v in B.gidari.items() if now - v[1] > 20]:
            del B.gidari[t]
        d.update({"sjuso": tls_juso("/bang", "k=" + B.pdkey) if B.tport else "",
                  "djuso": "http://" + SEOBEO_IP + "/",
                  "gidari": [{"t": t, "nm": v[0]} for t, v in B.gidari.items()],
                  "bangsik": 1 if B.ttuigi == "cheok" else 0, "dns": 0, "hwakin": B.hwakin,
                  "jiyeon": jiyeon_mal(), "siheom": "", "sij": False,
                  "mang": mang_mok(), "tport": B.tport})
    return json.dumps(d, ensure_ascii=False, separators=(",", ":"))


GWAN_ANDOEM = {"ok": False, "mal": "관계자로 붙은 뒤에 쓸 수 있습니다"}


async def web_tls(r, w):
    await web(r, w, True)


async def web(r, w, tls=False):
    peer = w.get_extra_info("peername")
    ip = peer[0] if peer else ""
    if ip.startswith("::ffff:"):
        ip = ip[7:]
    gyesok = False
    try:
        meori = await asyncio.wait_for(r.readuntil(b"\r\n\r\n"), 10)
        jul = meori.decode("latin-1").split("\r\n")
        cheot = jul[0].split(" ")
        if len(cheot) < 2:
            return
        method = cheot[0].upper()
        juso = cheot[1]
        host = ""
        host_jeonche = ""
        ua = ""
        clen = 0
        for l in jul[1:]:
            ll = l.lower()
            if ll.startswith("host:"):
                host_jeonche = l[5:].strip()
                host = host_jeonche.split(":")[0].lower()
            elif ll.startswith("user-agent:"):
                ua = l[11:].strip()
            elif ll.startswith("content-length:"):
                try:
                    clen = int(l[15:].strip())
                except Exception:
                    clen = 0
        if juso.startswith("http://"):
            juso = "/" + juso.split("/", 3)[-1] if juso.count("/") >= 3 else "/"
        gil, _, q = juso.partition("?")
        qs = urllib.parse.parse_qs(q)
        uri = gil.lower()
        uri_host = (not tls) and host not in ("", SEOBEO_IP, "127.0.0.1", "localhost", "byod.lan", "byod")
        body = b""
        if method == "POST" and 0 < clen < 4000000:
            body = await asyncio.wait_for(r.readexactly(clen), 30)

        if method == "OPTIONS":
            geulja(w, 204, "text/plain", "")
            await w.drain()
            return

        # 1) 폰의 「인터넷 되느냐」 물음
        if uri_host or (not tls and any(c in uri for c in CAPTIVE)):
            B.hwakin += 1
        if not tls and (any(c in uri for c in CAPTIVE) or (uri_host and uri in ("/", "/index.html"))):
            if B.ttuigi == "ttuim" and ip not in B.tonggwa:
                geulja(w, 302, "text/html", "", "Location: http://%s/\r\n" % SEOBEO_IP)
            elif "hotspot-detect" in uri or "library/test/success" in uri:
                geulja(w, 200, "text/html", "<HTML><HEAD><TITLE>Success</TITLE></HEAD><BODY>Success</BODY></HTML>")
            elif "connecttest" in uri:
                geulja(w, 200, "text/plain", "Microsoft Connect Test")
            elif "ncsi" in uri:
                geulja(w, 200, "text/plain", "Microsoft NCSI")
            elif "success.txt" in uri:
                geulja(w, 200, "text/plain", "success\n")
            else:
                geulja(w, 204, "text/plain", "")
            await w.drain()
            return
        if uri_host:
            # 다른 누리집 주소를 치면 듣기 화면으로
            geulja(w, 302, "text/html", "", "Location: http://%s/\r\n" % SEOBEO_IP)
            await w.drain()
            return

        # 1.4.0 권한(heoga): 이 기기 자신(루프백·방송 기기) 또는 ?k=피디 번호 또는 받아 준 폰 또는 붙은 관계자
        loop = ip.startswith("127.") or ip == "::1" or (ip != "" and ip == B.bonaem_ip and (B.bonaem is not None or time.time() - B.majimak_butum < IEUM))
        tok = gap(q, "t")
        mid = len(tok) >= 16 and tok in B.mideum
        heoga = loop or gap(q, "k") == B.pdkey or mid or gwan_ok(q)
        try:
            gal = int(gap(q, "gal") or "1")
        except Exception:
            gal = 1
        if gal < 1 or gal > GAL_SU:
            gal = 1
        S = B.S

        if uri in ("/", "/index.html", "/deut.html", "/deut"):
            geulja(w, 200, "text/html; charset=utf-8", deut_hwamyeon())
        elif uri == "/bang" or uri == "/mujeon":
            if not tls and not loop and B.tport:
                dolligi(w, tls_juso(uri, q))      # 폰은 자물쇠 주소로 보내야 마이크·카메라가 열림
            else:
                jjokgeul(w, "bang.html" if uri == "/bang" else "pd.html")
        elif uri in ("/pd", "/hs", "/gwan", "/pd.html"):
            jjokgeul(w, "pd.html")
        elif uri == "/bang.html":
            jjokgeul(w, "bang.html")
        elif uri == "/beonho" and loop and gap(q, "n") == "":
            geulja(w, 200, "text/plain", B.pdkey)
        elif uri == "/deulgi":
            if heoga:
                js(w, {"ok": True})
            elif len(tok) < 16 or len(tok) > 64:
                js(w, {"ok": False})
            elif tok not in B.gidari and len(B.gidari) > 20:
                js(w, {"ok": False})
            else:
                nm = "아이폰" if "iPhone" in ua else "아이패드" if "iPad" in ua else "안드로이드 폰" if "Android" in ua else "컴퓨터"
                B.gidari[tok] = [nm, time.time()]
                js(w, {"ok": False, "daegi": True})
        elif uri == "/batgi" and heoga:
            b = gap(q, "b")
            if b in B.gidari:
                del B.gidari[b]
                B.mideum.add(b)
                if not jg_now():
                    try:
                        with open(os.path.join(TEO, "mideum.txt"), "a", encoding="utf-8") as f:
                            f.write(b + "\r\n")
                    except Exception:
                        pass
            js(w, {"ok": True})
        elif uri == "/status":
            geulja(w, 200, "application/json; charset=utf-8", sangtae(heoga, gal))
        elif uri == "/cue":
            if not heoga:
                js(w, {"ok": False, "msg": "해설사 번호가 맞지 않습니다."}, 403)
            else:
                v = gap(q, "v") == "1"
                B.cue[gal] = v
                if gal == 1:
                    B.jeongji1 = (not v) and B.bonaem is not None
                js(w, {"ok": True, "keu": keu_gal(gal)})
        elif uri == "/mic" and heoga:
            B.mic_ttae[gal] = time.time()
            js(w, {"ok": True, "keu": keu_gal(gal)})
        elif uri == "/nas" and heoga:
            B.nas_mal = pul(gap(q, "t"))
            js(w, {"ok": True})
        elif uri == "/bangsik" and heoga:
            B.ttuigi = "cheok" if gap(q, "v") == "1" else "ttuim"
            try:
                with open(os.path.join(TEO, "ttuigi"), "w") as f:
                    f.write(B.ttuigi + "\n")
            except Exception:
                pass
            js(w, {"ok": True, "bangsik": 1 if B.ttuigi == "cheok" else 0})
        elif uri == "/siheom" and heoga:
            js(w, {"ok": False, "msg": "공유기 안 서버에서는 버티기 시험을 하지 않습니다."})
        elif uri in ("/yocheong_mok", "/yocheong_cheori", "/hs_mal", "/hs_mal_mok", "/kamera_mok", "/kamera_bogi", "/pyosi") and not heoga:
            js(w, GWAN_ANDOEM, 403)
        elif uri == "/sunseo" and gap(q, "i") != "" and not heoga:
            js(w, GWAN_ANDOEM, 403)
        elif uri == "/deutnun":
            i = gap(q, "i")
            n = gap(q, "n")
            if len(i) == 0 or len(i) > 20:
                js(w, {"ok": False}, 400)
            else:
                if not susja(n):
                    n = ""
                S["deutmok"][i] = [time.time(), 1 if gap(q, "s") == "1" else 0, n]
                deut_segi()
                if len(S["deutmok"]) > 3000:
                    now = time.time()
                    for k in [k for k, v in S["deutmok"].items() if now - v[0] > 600]:
                        del S["deutmok"][k]
                js(w, {"ok": True})
        elif uri == "/yocheong":
            # 노트북판(i·n·k 다섯 종류)과 옛 듣기 화면(n·k, 궁금함 gungeum)을 함께 받음
            i = gap(q, "i") or ip
            n = gap(q, "n")
            k = gap(q, "k")
            if k == "gungeum":
                k = "gunggeum"
            if not susja(n):
                n = ""
            if k not in ("dowum", "jakda", "cheoncheon", "gunggeum", "kkeunkim"):
                js(w, {"ok": False}, 400)
            else:
                key = i + "|" + k
                now = jigeum()
                ok = not (key in S["ycgeuje"] and now - S["ycgeuje"][key] < 20)
                if ok:
                    S["ycgeuje"][key] = now
                    S["ycseq"] += 1
                    S["yc"].append([str(S["ycseq"]), hms(now), n, k, "0", now])
                    del S["yc"][:-300]
                    csv_jul("yocheong", hms(now) + "," + n + "," + yc_ireum(k))
                    if not jg_now():
                        ak = {"gunggeum": "gungeum", "jakda": "소리가 작아요", "cheoncheon": "조금 천천히"}.get(k, k)
                        B.yocheong.append((time.time(), n, ak))       # 앱 첫 화면 한 줄 알림
                        del B.yocheong[:-200]
                        B.yseq += 1
                        if B.haengsa is not None:
                            B.haengsa.yocheong(k, n)
                js(w, {"ok": ok})
        elif uri == "/yocheong_cheori":
            i = gap(q, "id")
            for y in S["yc"]:
                if y[0] == i:
                    y[4] = "1"
            csv_jul("yocheong", hms() + ",,도와드림 확인 " + i)
            js(w, {"ok": True})
        elif uri == "/yocheong_mok":
            now = time.time()
            jn = jigeum()
            deut = kkeun = 0
            for v in S["deutmok"].values():
                if now - v[0] < 45:
                    deut += 1
                    if v[1] == 1:
                        kkeun += 1
            dowum = []
            jom = {}
            for y in S["yc"]:
                if y[3] == "dowum":
                    if y[4] == "0":
                        dowum.append({"id": int(y[0]), "t": y[1], "n": y[2], "g": guyeok_of(y[2]), "bun": int((jn - y[5]) // 60)})
                elif jn - y[5] < 120:
                    jom[y[3]] = jom.get(y[3], 0) + 1
            js(w, {"deut": deut, "kkeun": kkeun, "seq": S["ycseq"], "dowum": dowum, "jom": jom})
        elif uri == "/yocheong_mokrok":
            geulja(w, 200, "application/json; charset=utf-8", json.dumps(
                {"yseq": B.yseq, "mokrok": [{"t": int(y[0]), "n": y[1], "k": y[2]} for y in B.yocheong[-20:]]}, ensure_ascii=False))
        elif uri == "/hs_mal":
            nm = pul(gap(q, "nm"))[:20]
            t = pul(gap(q, "t"))
            if len(t) == 0 or len(t) > 300:
                js(w, {"ok": False}, 400)
            else:
                S["malseq"] += 1
                S["mal"].append([str(S["malseq"]), hms(), nm, t])
                del S["mal"][:-50]
                js(w, {"ok": True})
        elif uri == "/hs_mal_mok":
            js(w, {"seq": S["malseq"], "mok": [{"id": int(m[0]), "t": m[1], "nm": m[2], "mal": m[3]} for m in S["mal"]]})
        elif uri == "/pyosi":
            nm = pul(gap(q, "nm"))[:20]
            t = pul(gap(q, "t"))[:100]
            ss = pul(gap(q, "ss"))[:60]
            h = B.haengsa
            if h is not None and h.g["jogak"]:
                pail = h.sid + (".mp3" if h.g["hyeongsik"] == "mp3" else ".ul")
                ofs = int(h.gilee())
            else:
                pail = "(녹음 없음)"
                ofs = 0
            csv_jul("pyosi", ts() + "," + pail + "," + str(ofs) + "," + ss.replace(",", " ") + "," + nm.replace(",", " ") + "," + t.replace(",", " "))
            if h is not None and not jg_now():
                h.g.setdefault("pyosi", []).append([jigeum(), ofs, ss, nm, t])     # 행사 기록에 녹음 자리(분·초)와 함께
                h.jeojang()
            js(w, {"ok": True, "pail": pail, "bun": ofs // 60, "cho": ofs % 60})
        elif uri == "/nokeum_sangtae":
            mok = haengsa_mokrok()
            h = B.haengsa
            js(w, {"su": len(mok), "olrim": sum(1 for m in mok if m["olim"]),
                   "jigeum": h is not None and gal1_sori(), "bun": int(h.gilee() // 60) if h is not None else 0,
                   "olrinun": B.nok_olrinun, "mal": B.nok_mal})
        elif uri == "/nokeum_olligi":
            if not heoga:
                js(w, {"ok": False}, 403)
            else:
                if not B.nok_olrinun:
                    B.nok_olrinun = True
                    B.nok_mal = "올리는 중입니다"
                    asyncio.ensure_future(nok_olligi())
                js(w, {"ok": True})
        elif uri in ("/kamera", "/kamera_zoom", "/kamera_bogi", "/kamera_mok"):
            kamera_cheori(w, uri, q, method, body, heoga)
        elif uri in ("/mj_bonae", "/mj_hochul", "/mj_mok", "/mj_deul", "/mj_saram"):
            mujeon_cheori(w, uri, q, method, body, heoga, loop)
        elif uri == "/gwan_qr":
            if not heoga:
                js(w, {"ok": False}, 403)
            else:
                if gap(q, "sae") == "1":
                    S["gwankey"] = nanseu(8)
                    S["gwan"] = {}
                h = host if tls else host_jeonche
                if h == "" or h.startswith("localhost") or h.startswith("127."):
                    h = SEOBEO_IP
                js(w, {"ok": True, "juso": "http://" + h + "/gwan?gk=" + gwan_key()})
        elif uri == "/gwan_deung":
            gk = gap(q, "gk")
            rr = gap(q, "r")
            nm = pul(gap(q, "nm"))[:20]
            if gk == "" or gk != gwan_key():
                js(w, {"ok": False, "mal": "큐알이 바뀌었습니다. 피디 화면의 큐알을 다시 찍어 주십시오."})
            elif rr not in ("haeseol", "jinhaeng", "kamera", "pd"):
                js(w, {"ok": False, "mal": "역할을 골라 주십시오."})
            elif nm.strip() == "":
                js(w, {"ok": False, "mal": "이름을 적어 주십시오."})
            else:
                t = nanseu(16)
                now = jigeum()
                S["gwan"][t] = [rr, nm.strip(), now, now]
                csv_jul("gwangye", ts() + "," + gwan_ireum(rr) + "," + nm.replace(",", " "))
                js(w, {"ok": True, "tok": t, "r": rr, "yeokhal": gwan_ireum(rr)})
        elif uri == "/gwan_mok":
            if not heoga:
                js(w, {"ok": False}, 403)
            else:
                now = jigeum()
                js(w, {"ok": True, "mok": [{"yeokhal": gwan_ireum(v[0]), "nm": v[1], "cheo": hm(v[2]), "jigeum": now - v[3] < 90}
                                           for v in S["gwan"].values()]})
        elif uri == "/gingeup":
            if not heoga:
                js(w, {"ok": False}, 403)
            else:
                gt = pul(gap(q, "t")).strip()[:200]
                S["gin"] = gt
                S["ginseq"] += 1
                csv_jul("gingeup", ts() + "," + ("긴급 안내 끝" if gt == "" else gt.replace(",", " ").replace("\r", " ").replace("\n", " ")))
                js(w, {"ok": True, "gseq": S["ginseq"]})
        elif uri in ("/jeomgeom_sijak", "/jeomgeom_kkeut"):
            if not loop and gap(q, "k") != B.pdkey:
                js(w, {"ok": False, "mal": "피디 화면에서만 쓸 수 있습니다"}, 403)
            elif uri == "/jeomgeom_sijak":
                if keu_amugeona():
                    js(w, {"ok": False, "mal": "해설이 나가는 중에는 점검할 수 없습니다. 해설을 멈춘 뒤 눌러 주십시오"})
                elif jg_now():
                    js(w, {"ok": False, "mal": "점검이 이미 진행 중입니다"})
                elif not jg_damgi():
                    js(w, {"ok": False, "mal": "지금 상황을 담지 못해 점검을 시작하지 않았습니다"})
                else:
                    B.jg_sijak = time.time()
                    B.jg_jung = True
                    js(w, {"ok": True, "pan": PAN, "bild": BILD})
            else:
                if B.jg_jung:
                    jg_doerigi()
                js(w, {"ok": True})
        elif uri == "/jadong":
            js(w, {"mal": B.jadong_mal, "teo": girok_teo()})
        elif uri in ("/bogoseo", "/bogoseo.txt"):
            bogo_cheori(w, uri, heoga)
        elif uri in ("/jaryo", "/jaryo_badgi"):
            if uri == "/jaryo_badgi" or not os.path.isfile(jaryo_pail()):
                ok = await asyncio.get_event_loop().run_in_executor(None, jaryo_batgi)
                if uri == "/jaryo_badgi":
                    js(w, {"ok": ok})
            if uri == "/jaryo":
                geulja(w, 200, "application/json", jaryo_ilkgi() or "{}")
        elif uri == "/sunseo":
            i = gap(q, "i")
            if 0 < len(i) < 4 and i.isdigit():
                S["sunseoI"] = int(i)
                S["sunseoT"] = jigeum()
            js(w, {"i": S["sunseoI"], "jinan": int(jigeum() - S["sunseoT"]) if S["sunseoT"] else -1})
        elif uri == "/beonho":
            n = gap(q, "n")
            if not susja(n):
                js(w, {"ok": False}, 400)
            else:
                n = n.lstrip("0") or "0"
                gy = pul(gap(q, "g")).strip()[:10]
                if gy:
                    S["bhg"][n] = gy
                now = jigeum()
                if n in S["bh"]:
                    S["bh"][n][1] = now
                else:
                    S["bh"][n] = [now, now]
                    B.boot_bh.add(n)
                    csv_jul("chulseok", ts(now) + "," + n + ",처음 연결")
                js(w, {"ok": True, "n": n})
        elif uri in ("/chulseok", "/chucheom", "/dangcheom"):
            beonho_cheori(w, uri, q, heoga)
        elif uri == "/jiyeon":
            i = gap(q, "i")
            try:
                ms = float(gap(q, "d"))
            except Exception:
                ms = -1
            if 6 <= len(i) <= 40 and 0 <= ms < 60000 and (i in B.jiyeon or len(B.jiyeon) < 3000):
                B.jiyeon[i] = [ms, time.time()]
            js(w, {"ok": True})
        elif uri == "/pcm" and method == "POST":
            if not heoga:
                js(w, {"ok": False}, 403)
            else:
                now = time.time()
                B.mic_ttae[gal] = now
                k = keu_gal(gal)
                if k and body:
                    B.pcm_ttae[gal] = now
                    if B.haengsa is None:
                        nm = gwan_nae_ireum(q, loop)
                        haengsa_butum(nm, "", nm[4:] if nm.startswith("해설사 ") else "")
                    B.majimak_butum = now
                    if gal == 1 and B.keu and not B.jeongji1:
                        pass                         # 갈래 1 은 방송 앱 소리가 먼저(두 소리가 섞이지 않게)
                    else:
                        gal_naegi(gal, body)
                js(w, {"ok": True, "keu": k})
        elif uri == "/gallae_ireum":
            if not heoga:
                js(w, GWAN_ANDOEM, 403)
            else:
                nm = pul(gap(q, "nm")).strip()[:20]
                if nm:
                    S["galnm"][str(gal)] = nm
                else:
                    S["galnm"].pop(str(gal), None)
                js(w, {"ok": True, "gal": gal, "nm": gal_ireum(gal)})
        elif uri == "/manjok_sijak":
            if not heoga:
                js(w, GWAN_ANDOEM, 403)
            else:
                m = S["mjk"]
                if gap(q, "kkeut") == "1":
                    if m["on"]:
                        m["on"] = False
                        m["seq"] += 1
                        if m["doe"]:
                            m["doe"][-1]["kkeut"] = jigeum()
                    js(w, {"ok": True, "seq": m["seq"]})
                else:
                    mq = pul(gap(q, "q")).strip()[:200] or MANJOK_GIBON
                    m["seq"] += 1
                    m["on"] = True
                    m["q"] = mq
                    m["doe"].append({"seq": m["seq"], "q": mq, "sijak": jigeum(), "kkeut": 0, "dap": {}})
                    del m["doe"][:-20]
                    js(w, {"ok": True, "seq": m["seq"], "q": mq})
        elif uri == "/manjok":
            m = S["mjk"]
            i = gap(q, "i")
            n = gap(q, "n")
            try:
                p = int(gap(q, "p"))
            except Exception:
                p = 0
            if not susja(n):
                n = ""
            if not m["doe"] or not (1 <= len(i) <= 40) or not (1 <= p <= 5):
                js(w, {"ok": False})
            else:
                t = pul(gap(q, "t")).strip().replace("\r", " ").replace("\n", " ")[:200]
                r0 = m["doe"][-1]
                if len(r0["dap"]) < 3000 or i in r0["dap"]:
                    r0["dap"][i] = [n, p, t, jigeum()]          # 같은 폰·같은 물음은 덮어씀
                    csv_jul("manjok", ts() + "," + str(r0["seq"]) + "," + n + "," + str(p) + "," + t.replace(",", " "))
                js(w, {"ok": True})
        elif uri == "/manjok_mok":
            if not heoga:
                js(w, GWAN_ANDOEM, 403)
            else:
                js(w, manjok_mok())
        elif uri == "/stream":
            gyesok = True
            await deut_butigi(w, ip, False, gal, loop and gap(q, "nae") == "1")
        elif uri == "/stream.mp3":
            if not B.mp3_iss or gal != 1:
                geulja(w, 404, "text/plain; charset=utf-8", "mp3 길이 없습니다.")
            else:
                gyesok = True
                await deut_butigi(w, ip, True, 1, False)
        elif uri == "/tonggwa":
            if B.tonggwa_ttae == "deutgi":
                B.tonggwa.add(ip)
            geulja(w, 200, "application/json", '{"ok":true}')
        elif uri in ("/daegi.mp3", "/ment.mp3"):
            p = os.path.join(TEO, uri[1:])
            if os.path.isfile(p):
                with open(p, "rb") as f:
                    geulja(w, 200, "audio/mpeg", f.read())
            else:
                geulja(w, 404, "text/plain; charset=utf-8", "없는 소리입니다.")
        elif uri == "/haengsa_mokrok":
            geulja(w, 200, "application/json; charset=utf-8", json.dumps({"mokrok": haengsa_mokrok(),
                   "jigeum": B.haengsa.sid if B.haengsa else ""}, ensure_ascii=False))
        elif uri == "/haengsa_kkeut":
            if B.yeolsoe and (qs.get("k") or [""])[0] != B.yeolsoe:
                geulja(w, 403, "application/json", '{"ok":false}')
            else:
                sid = B.haengsa.sid if B.haengsa else ""
                haengsa_maegam()
                geulja(w, 200, "application/json", json.dumps({"ok": True, "sid": sid}))
        elif uri == "/haengsa_olim":
            ok = haengsa_olim((qs.get("sid") or [""])[0])
            geulja(w, 200, "application/json", json.dumps({"ok": ok}))
        elif uri.startswith("/haengsa/"):
            jul2 = gil.split("/")
            nm = os.path.basename(jul2[3]) if len(jul2) > 3 else ""
            p = os.path.join(HAENGSA_TEO, os.path.basename(jul2[2]) if len(jul2) > 2 else "", nm)
            if (nm in ("nokeum.mp3", "nokeum.ul", "bogo.txt", "girok.json", "bogoseo.txt", "sangtae.json", "gal2_nokeum.ul", "gal3_nokeum.ul")
                    or re.match(r"^(chulseok|yocheong|pyosi|gingeup|gwangye|manjok)_\d{8}\.csv$", nm)) and os.path.isfile(p):
                hwak = nm.rsplit(".", 1)[1]
                chong = {"mp3": "audio/mpeg", "ul": "application/octet-stream", "txt": "text/plain; charset=euc-kr",
                         "json": "application/json; charset=utf-8", "csv": "text/csv; charset=utf-8"}[hwak]
                with open(p, "rb") as f:
                    geulja(w, 200, chong, f.read())
            else:
                geulja(w, 404, "text/plain; charset=utf-8", "없는 녹음입니다.")
        else:
            geulja(w, 404, "text/plain; charset=utf-8", "없는 주소입니다.")
        await w.drain()
    except Exception:
        pass
    finally:
        if not gyesok:
            try:
                w.close()
            except Exception:
                pass


def beonho_cheori(w, uri, q, heoga):
    """노트북판 BeonhoCheori — 참석과 추첨(입장 번호는 /beonho 에서)"""
    S = B.S
    if not heoga:
        js(w, {"ok": False}, 403)
        return
    now = jigeum()
    if uri == "/chulseok":
        mok = []
        jg = 0
        for k in sorted(S["bh"].keys(), key=lambda x: int(x)):
            v = S["bh"][k]
            on = now - v[1] < 75
            if on:
                jg += 1
            mok.append({"n": k, "cheo": hm(v[0]), "majimak": hm(v[1]), "bun": int(round((v[1] - v[0]) / 60.0)), "jigeum": on})
        js(w, {"ok": True, "modu": len(mok), "jigeum": jg, "dang": dang_json(), "mok": mok, "guyeok": guyeok_mok()})
    elif uri == "/chucheom":
        beom = gap(q, "beom")
        try:
            kkeut = int(gap(q, "kkeut"))
        except Exception:
            kkeut = 0
        ppae = gap(q, "ppae") != "0"
        if beom == "modu":
            pool = [str(i) for i in range(1, min(kkeut, 9999) + 1)]
        else:
            pool = [k for k, v in S["bh"].items() if now - v[1] < 75]
        if ppae:
            pool = [x for x in pool if x not in S["dang"]]
        if not pool:
            js(w, {"ok": False, "mal": "뽑을 번호가 없습니다"})
            return
        ppob = pool[int.from_bytes(os.urandom(4), "little") % len(pool)]
        S["dangcheom"] = ppob
        S["dangsik"] = "r"
        S["dang"].append(ppob)
        S["dangseq"] += 1
        mal = "추첨 당첨(" + (("1~%d번" % kkeut) if beom == "modu" else "지금 듣는 번호") + " %d개 중)" % len(pool)
        if not jg_now():
            S["danggirok"].append([now, ppob, mal])
        csv_jul("chulseok", ts(now) + "," + ppob + "," + mal)
        js(w, {"ok": True, "n": ppob, "mo": len(pool), "seq": S["dangseq"]})
    elif uri == "/dangcheom":
        n = gap(q, "n")
        if not susja(n):
            js(w, {"ok": False, "mal": "번호를 숫자로 적어 주십시오"})
            return
        n = n.lstrip("0") or "0"
        if n in S["dang"]:
            js(w, {"ok": False, "mal": n + "번은 이미 당첨된 번호입니다"})
            return
        S["dang"].append(n)
        S["dangcheom"] = n
        S["dangsik"] = "m"
        S["dangseq"] += 1
        if not jg_now():
            S["danggirok"].append([now, n, "직접 호명 당첨"])
        csv_jul("chulseok", ts(now) + "," + n + ",직접 호명 당첨")
        v = S["bh"].get(n)
        js(w, {"ok": True, "n": n, "seq": S["dangseq"], "deunna": v is not None and now - v[1] < 75})


def ds(v):
    """노트북판 Ds — 정수면 정수로"""
    v = round(float(v), 2)
    return int(v) if v == int(v) else v


def sutja(s, d):
    try:
        return float(s)
    except Exception:
        return d


def kamera_cheori(w, uri, q, method, body, heoga):
    S = B.S
    now = time.time()
    c = gap(q, "c")
    if c not in ("1", "2", "3"):
        c = "1"
    if uri == "/kamera":
        if not heoga:
            js(w, {"ok": False}, 403)
        elif method != "POST" or len(body) < 100 or len(body) > 3000000:
            js(w, {"ok": False}, 400)
        else:
            nm = pul(gap(q, "nm"))[:20]
            old = S["kam"].get(c)
            zreq, zseq = (old[6], old[7]) if old else (-1.0, 0)
            S["kam"][c] = [body, now, nm, sutja(gap(q, "zmin"), 0.0), sutja(gap(q, "zmax"), 0.0), sutja(gap(q, "z"), 0.0), zreq, zseq]
            js(w, {"ok": True, "zreq": ds(zreq), "zseq": zseq})
    elif uri == "/kamera_zoom":
        if not heoga:
            js(w, {"ok": False}, 403)
            return
        d = gap(q, "d")
        nz, mx, mn, ok = -1.0, 0.0, 0.0, False
        v = S["kam"].get(c)
        if v is not None and v[4] > v[3]:
            mn, mx = v[3], v[4]
            cur = v[6] if v[6] > 0 else (v[5] if v[5] > 0 else mn)
            nz = cur * 1.5 if d == "in" else (cur / 1.5 if d == "out" else mn)
            nz = max(mn, min(mx, nz))
            v[6] = nz
            v[7] += 1
            ok = True
        js(w, {"ok": ok, "z": ds(nz), "max": ds(mx), "min": ds(mn)})
    elif uri == "/kamera_bogi":
        v = S["kam"].get(c)
        if v is None or now - v[1] >= 10:
            js(w, {"ok": False}, 404)
        else:
            geulja(w, 200, "image/jpeg", v[0])
    else:
        js(w, {"mok": [{"c": k, "nm": v[2], "zmin": ds(v[3]), "zmax": ds(v[4]), "z": ds(v[5])}
                       for k, v in S["kam"].items() if now - v[1] <= 10]})


def gwan_nae_tok(q):
    return gap(q, "gt") or gap(q, "t")


def gwan_nae_ireum(q, loop):
    t = gwan_nae_tok(q)
    v = B.S["gwan"].get(t) if t else None
    if v is not None:
        return gwan_ireum(v[0]) + " " + v[1]
    return "피디(방송 기기)" if loop else "피디"


def mujeon_cheori(w, uri, q, method, body, heoga, loop):
    """노트북판 MujeonCheori — 관계자 무전"""
    S = B.S
    if not heoga:
        js(w, GWAN_ANDOEM, 403)
        return
    nae = gwan_nae_tok(q) or "pd"
    now = jigeum()
    if uri in ("/mj_bonae", "/mj_hochul"):
        to = gap(q, "to") or "ALL"
        mime = pul(gap(q, "mime"))
        if len(mime) > 40 or not mime.startswith("audio"):
            mime = "audio/webm"
        b = None
        if uri == "/mj_bonae":
            if method != "POST" or len(body) < 200 or len(body) > 1500000:
                js(w, {"ok": False}, 400)
                return
            b = body
        S["mjseq"] += 1
        S["mj"].append([S["mjseq"], now, nae, gwan_nae_ireum(q, loop), to, "hochul" if uri == "/mj_hochul" else "mal", mime, b])
        del S["mj"][:-40]
        js(w, {"ok": True, "id": S["mjseq"]})
    elif uri == "/mj_mok":
        try:
            after = int(gap(q, "after"))
        except Exception:
            after = 0
        mok = [{"id": m[0], "t": hms(m[1]), "nm": m[3], "nae": m[2] == nae, "modu": m[4] == "ALL", "jong": m[5]}
               for m in S["mj"] if m[0] > after and (m[4] == "ALL" or m[4] == nae or m[2] == nae)]
        js(w, {"seq": S["mjseq"], "mok": mok})
    elif uri == "/mj_deul":
        try:
            i = int(gap(q, "id"))
        except Exception:
            i = 0
        b = None
        mime = "audio/webm"
        for m in S["mj"]:
            if m[0] == i and (m[4] == "ALL" or m[4] == nae or m[2] == nae):
                b, mime = m[7], m[6]
        if b is None:
            js(w, {"ok": False}, 404)
        else:
            geulja(w, 200, mime, b)
    else:
        mok = [{"tok": k, "nm": gwan_ireum(v[0]) + " " + v[1], "jigeum": now - v[3] < 90} for k, v in S["gwan"].items() if k != nae]
        if nae != "pd":
            mok.append({"tok": "pd", "nm": "피디(방송 기기)", "jigeum": True})
        js(w, {"mok": mok})


def bogo_g():
    """결과보고에 쓸 행사 — 지금 행사, 없으면 가장 최근 행사"""
    if B.haengsa is not None:
        return B.haengsa.g
    for m in haengsa_mokrok():
        g = haengsa_girok(m["sid"])
        if g is not None:
            return g
    t = jigeum()
    return {"sid": "(행사 없음)", "jemok": "(행사 없음)", "haeseolsa": "", "sijak": t, "kkeut": t, "bun": [], "yocheong": [],
            "kkeunkim": [], "jogak": [], "sangtae": "kkeut", "hyeongsik": "mp3" if B.mp3_iss else "mulaw", "gilee": 0}


def bogo_cheori(w, uri, heoga):
    if not heoga:
        geulja(w, 403, "text/plain; charset=utf-8", "피디 화면에서만 열 수 있습니다.")
        return
    t = bogo_mandeul(bogo_g())
    if uri == "/bogoseo.txt":
        geulja(w, 200, "text/plain; charset=euc-kr", t.replace("\n", "\r\n").encode("cp949", "replace"))
        return
    h = ['<!doctype html><html lang="ko"><head><meta charset="utf-8"><title>현장영상해설 방송 결과보고</title><style>body{font-family:\'Malgun Gothic\',sans-serif;max-width:180mm;margin:10mm auto;font-size:11pt;line-height:1.7}h1{font-size:18pt}p{margin:0}@page{size:A4;margin:15mm}@media print{.an{display:none}}</style></head><body><p class="an"><button onclick="print()" style="font-size:18px;padding:12px">인쇄하기</button> <a href="/bogoseo.txt" download="결과보고.txt" style="font-size:18px">텍스트로 저장</a></p>']
    first = True
    for l in t.split("\n"):
        e = l.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        if first:
            h.append("<h1>" + e + "</h1>")
            first = False
        elif len(e) > 2 and e[0].isdigit() and e[1] == ".":
            h.append('<h2 style="font-size:13pt;margin:10pt 0 4pt">' + e + "</h2>")
        elif e == "":
            continue
        else:
            h.append("<p>" + e.replace("  ", "&nbsp;&nbsp;") + "</p>")
    h.append("</body></html>")
    geulja(w, 200, "text/html; charset=utf-8", "".join(h))


async def deut_butigi(w, ip, mp3, gal=1, nae=False):
    """듣는 분 한 사람 붙이기 — 머리를 보낸 뒤 조각이 오는 대로"""
    chong = "audio/mpeg" if mp3 else "application/octet-stream"
    try:
        import socket
        sk = w.get_extra_info("socket")
        sk.setsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF, 32768)   # 나간 폰을 빨리 가려내려고 보낼 칸을 작게
        sk.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    except Exception:
        pass
    meori_bonae(w, 200, "OK", chong, -1, "X-Content-Type-Options: nosniff\r\n")
    if not mp3:
        w.write(bytes([0xFF]) * JOGAK)
    d = Deut(w, ip, gal, nae)
    (B.deut_mp3 if mp3 else B.deut).append(d)
    if B.tonggwa_ttae == "deutgi":
        B.tonggwa.add(ip)                # 듣기를 시작한 폰은 다시 묻지 않게
    try:
        await w.drain()
        while not d.jugeum:
            try:
                b = await asyncio.wait_for(d.q.get(), 5)
            except asyncio.TimeoutError:
                b = b"" if mp3 else bytes([0xFF]) * 160
            if not b:
                continue
            d.sseuneun = time.time()
            w.write(b)
            await w.drain()
            d.sseuneun = 0.0
    except Exception:
        pass
    finally:
        kkeutnaegi(d)


def mideum_ilkgi():
    try:
        with open(os.path.join(TEO, "mideum.txt"), encoding="utf-8") as f:
            for l in f:
                l = l.strip()
                if len(l) >= 16:
                    B.mideum.add(l)
    except Exception:
        pass


async def main():
    s1 = await asyncio.start_server(web, "0.0.0.0", WEB_PORT, backlog=1024, reuse_address=True)
    s2 = await asyncio.start_server(sori_batgi, "0.0.0.0", SORI_PORT, backlog=16, reuse_address=True)
    sv = [s1, s2]
    # 1.4.0 자물쇠 문 — 준비하기가 넣어 둔 인증서(hj.crt·hj.key)가 있을 때만
    lp = asyncio.get_event_loop()
    def jalmot(l, c):                                # 자물쇠 인사가 깨진 폰 하나하나를 적지 않음
        e = c.get("exception")
        if (ssl is not None and isinstance(e, ssl.SSLError)) or "SSL" in str(c.get("message", "")) or isinstance(e, (ConnectionError, OSError)):
            return
        l.default_exception_handler(c)
    lp.set_exception_handler(jalmot)
    crt = os.environ.get("BYOD_TLS_CRT", os.path.join(TEO, "hj.crt"))
    key = os.environ.get("BYOD_TLS_KEY", os.path.join(TEO, "hj.key"))
    if ssl is not None and TLS_PORT > 0 and os.path.isfile(crt) and os.path.isfile(key):
        try:
            cx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
            cx.load_cert_chain(crt, key)
            s3 = await asyncio.start_server(web_tls, "0.0.0.0", TLS_PORT, ssl=cx, backlog=256, reuse_address=True,
                                            ssl_handshake_timeout=10)
            sv.append(s3)
            B.tport = TLS_PORT
        except Exception as e:
            print("자물쇠 문을 열지 못했습니다: %s" % e, flush=True)
    os.makedirs(HAENGSA_TEO, exist_ok=True)
    mideum_ilkgi()
    haengsa_dasi_yeolgi()
    try:
        sangtae_bulleo()
    except Exception:
        pass
    asyncio.ensure_future(goyo_ilkkun())
    asyncio.ensure_future(olligi_ilkkun())
    asyncio.ensure_future(mp3_ilkkun())
    asyncio.ensure_future(jadong_ilkkun())
    print("BYOD 플린트 서버 %s (%s) — 듣기 %d, 소리 %d, 자물쇠 %s, mp3 %s, 피디 번호 %s" % (PAN, BILD, WEB_PORT, SORI_PORT, B.tport or "없음", B.mp3_iss, B.pdkey), flush=True)
    await asyncio.gather(*[s.serve_forever() for s in sv])


# ───── 듣기 화면(공유기에 deut.html 이 없을 때 쓰는 기본 화면) ─────
DEUT_HTML = r"""<!doctype html><html lang="ko"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>현장영상해설 듣기</title><style>
body{font-family:sans-serif;margin:0 auto;max-width:640px;padding:16px;background:#12346E;color:#fff}
button{box-sizing:border-box;display:block;width:100%;padding:28px 16px;font-size:24px;font-weight:700;margin:14px 0;border:3px solid #FFCC00;border-radius:16px;background:#FFCC00;color:#111}
button.jakeun{padding:18px 16px;font-size:20px;background:#fff;color:#12346E}
p{font-size:19px;line-height:1.7}
details summary{font-size:20px;padding:14px 0}
audio{display:none}
</style></head><body>
<button id="b">현장영상해설 듣기</button>
<p id="m" aria-live="polite">단추를 누르면 현장영상해설이 들립니다. 화면을 잠가도 들립니다.</p>
<button class="jakeun" id="app">LVD 방송 앱으로 더 편하게 듣기</button>
<p id="appm" style="display:none">LVD 방송 앱을 받아 두시면 다음 행사부터는 단추를 찾지 않아도 행사장에 들어서기만 하면 저절로 들립니다. 앱은 휴대전화의 모바일 데이터로 받습니다.</p>
<details><summary>도움이 필요하면 펼치기</summary>
<button class="jakeun" data-k="dowum">현장영상해설사 도움 요청</button>
<button class="jakeun" data-k="kkeunkim">소리가 끊겨요</button>
<button class="jakeun" data-k="gungeum">궁금한 것이 있어요</button>
<button class="jakeun" id="bangsik">소리 방식 바꾸기</button>
<p id="bs"></p>
</details>
<audio id="a" preload="none" playsinline></audio>
<script>
var DEC=new Float32Array(256);
for(var i=0;i<256;i++){var u=~i&0xFF,s=u&0x80,e=(u>>4)&7,m=u&0x0F;var x=(((m<<3)+0x84)<<e)-0x84;DEC[i]=(s?-x:x)/32768;}
var ios=/iP(hone|ad|od)/.test(navigator.userAgent)||(navigator.platform==="MacIntel"&&navigator.maxTouchPoints>1);
var mp3=false,on=false,keu=false,ctx=null,next=0,jan=[],janLen=0,rate=16000,ctrl=null;
try{mp3=localStorage.getItem("byod_bangsik")?localStorage.getItem("byod_bangsik")==="mp3":ios;}catch(e){mp3=ios;}
function $(i){return document.getElementById(i);}
function mal(t){$("m").textContent=t;}
function bsMal(){$("bs").textContent=mp3?"지금 방식: 화면을 잠가도 이어지는 방식(조금 늦을 수 있음)":"지금 방식: 늦음이 가장 적은 방식";}
function jalgi(){ctrl=new AbortController();fetch("stream",{cache:"no-store",signal:ctrl.signal}).then(function(r){var rd=r.body.getReader();function ilk(){return rd.read().then(function(x){if(x.done)throw 0;batgi(x.value);return ilk();});}return ilk();}).catch(function(){if(on&&!mp3)setTimeout(jalgi,1000);});}
function batgi(u8){if(!on){return;}jan.push(u8);janLen+=u8.length;if(janLen<1600)return;
var all=new Uint8Array(janLen),o=0;jan.forEach(function(a){all.set(a,o);o+=a.length;});jan=[];janLen=0;
var sr=ctx.sampleRate,n=Math.floor(all.length*sr/rate),ab=ctx.createBuffer(1,n,sr),d=ab.getChannelData(0),st=rate/sr;
for(var k=0;k<n;k++){var p=k*st,i=Math.floor(p),f=p-i,a=DEC[all[i]],b=i+1<all.length?DEC[all[i+1]]:a;d[k]=a+(b-a)*f;}
var now=ctx.currentTime;if(next<now+0.03)next=now+0.25;if(next>now+1.0)next=now+0.25;
var src=ctx.createBufferSource();src.buffer=ab;src.connect(ctx.destination);src.start(next);next+=ab.duration;}
function sangtae(){fetch("status",{cache:"no-store"}).then(function(r){return r.json();}).then(function(j){if(j.keu!==keu){keu=j.keu;if(on)mal(keu?"해설이 들리고 있습니다.":"해설을 준비하고 있습니다. 곧 들립니다.");}}).catch(function(){});}
function meomchum(){on=false;if(ctrl)try{ctrl.abort();}catch(e){}if(ctx)try{ctx.suspend();}catch(e){}var a=$("a");try{a.pause();a.removeAttribute("src");a.load();}catch(e){}$("b").textContent="현장영상해설 듣기";}
function deutgi(){on=true;
if(mp3){var a=$("a");a.src="stream.mp3?t="+Date.now();a.play().catch(function(){mal("다시 한 번 눌러 주십시오.");on=false;$("b").textContent="현장영상해설 듣기";});
if("mediaSession" in navigator){try{navigator.mediaSession.metadata=new MediaMetadata({title:"현장영상해설",artist:"사단법인 한국시각장애인현장영상해설협회"});}catch(e){}}}
else{if(!ctx){ctx=new (window.AudioContext||window.webkitAudioContext)();var bi=ctx.createBufferSource();bi.buffer=ctx.createBuffer(1,1,22050);bi.connect(ctx.destination);bi.start(0);}try{ctx.resume();}catch(e){}next=0;jalgi();}
$("b").textContent="듣기 멈추기";mal(keu?"해설이 들리고 있습니다.":"해설을 준비하고 있습니다. 곧 들립니다.");
try{fetch("tonggwa",{cache:"no-store"});}catch(e){}}
$("b").onclick=function(){if(on){meomchum();mal("멈추었습니다.");return;}deutgi();};
$("a").onerror=function(){if(on&&mp3){setTimeout(function(){if(on)deutgi();},1500);}};
$("a").onended=function(){if(on&&mp3){deutgi();}};
$("bangsik").onclick=function(){var dasi=on;if(on)meomchum();mp3=!mp3;try{localStorage.setItem("byod_bangsik",mp3?"mp3":"jeo");}catch(e){}bsMal();if(dasi)deutgi();mal(mp3?"화면을 잠가도 이어지는 방식으로 바꾸었습니다.":"늦음이 가장 적은 방식으로 바꾸었습니다.");};
Array.prototype.forEach.call(document.querySelectorAll("button[data-k]"),function(b){b.onclick=function(){fetch("yocheong?k="+b.getAttribute("data-k")+"&n=",{cache:"no-store"}).then(function(){mal("알렸습니다. 현장영상해설사가 곧 찾아갑니다.");}).catch(function(){mal("알리지 못했습니다. 다시 눌러 주십시오.");});};});
fetch("status",{cache:"no-store"}).then(function(r){return r.json();}).then(function(j){keu=j.keu;if(!j.mp3){mp3=false;$("bangsik").style.display="none";}bsMal();}).catch(function(){bsMal();});
$("app").onclick=function(){$("appm").style.display="block";var u=ios?"%%APP_IOS%%":"%%APP_AOS%%";setTimeout(function(){location.href=u;},1500);};
setInterval(sangtae,3000);setTimeout(function(){try{$("b").focus();}catch(e){}},300);
</script></body></html>"""

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
