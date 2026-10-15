#!/usr/bin/env python3
# BYOD 방송 — 플린트 2(GL-MT6000) 공유기 안 방송 서버
# 판 1.2.0 / 빌드 261014-F1 / 2026-10-14 (이사장님 승인 「1」 — 서버를 공유기 안에 둠, 방송클)
# 판 1.3.0 / 빌드 261014-F2 / 2026-10-14 (이사장님 승인 「1」, 방송클) 행사 녹음을 1분마다 저절로 남기고(전기가 끊겨도 앞부분은 남음),
#   행사 기록(1분마다 듣는 분 수·도움 요청·끊김)과 한 장짜리 결과보고를 만들어, 앱이나 공유기 스스로 배프 다시듣기와 나스에 올림
#
# 문: 듣기 7880(80번을 방화벽이 돌려줌), 소리 받기 7881
# 하는 일
#   1. 해설 소리 받기: 방송 폰·태블릿(BYOD 앱)이 소리 한 줄기(뮤 8비트, 1초에 16000)를 7881번 문으로 보냄
#   2. 나눠 보내기: 듣는 분 폰마다 /stream(저지연) 또는 /stream.mp3(화면을 잠가도 이어짐)
#   3. 듣기 화면 저절로 뜨기: 폰이 와이파이에 붙어 「인터넷 되느냐」고 물으면 듣기 화면으로 보냄
#   4. 도움 요청·듣는 분 수·방송 상태를 앱에 알림(/status, /yocheong_mokrok)
# 길 이름은 노트북판·태블릿판(현장 해설방송 3.3판, BYOD 1.1.x)과 같아 듣는 화면(deut.html)이 그대로 돎.
# 파이썬 3.9(오픈더블유알티 21.02) 이상에서 돎. 바깥 꾸러미 없이 표준 것만 씀(mp3는 lame 이 있을 때만).

import asyncio, json, os, sys, time, shutil, urllib.parse, urllib.request

PAN = "1.3.1"   # 261015-F3 상태에 행사 이름·현장영상해설사, 듣기 화면에 LVD 방송 앱 받기, 행사 기본 이름 「현장영상해설 방송」
BILD = "261015-F3"

SEOBEO_IP = os.environ.get("BYOD_IP", "192.168.8.2")      # 듣기 화면 주소(공유기에 덧붙인 주소)
WEB_PORT = int(os.environ.get("BYOD_WEB_PORT", "7880"))    # 80번은 방화벽이 이리로 돌려줌
SORI_PORT = int(os.environ.get("BYOD_SORI_PORT", "7881"))  # 방송 앱이 소리를 보내는 문
TEO = os.environ.get("BYOD_TEO", "/etc/byod")              # 듣는 화면·대기 음악·열쇠를 두는 곳
RATE = 16000
JOGAK = 1600                                               # 0.1초
DEUT_JUL = 40                                              # 듣는 분마다 4초까지 쌓아 둠
MAKHIM = 10.0                                              # 10초 넘게 쓰기가 막히면 끊긴 폰으로 봄
HAENGSA_TEO = os.environ.get("BYOD_HAENGSA", os.path.join(TEO, "haengsa"))   # 행사 녹음과 기록
IEUM = float(os.environ.get("BYOD_IEUM", "300"))         # 방송 기기가 이 초 안에 다시 붙으면 같은 행사(해설사 교대)
JOGAK_CHO = float(os.environ.get("BYOD_JOGAK_CHO", "60")) # 녹음 조각 길이(초)
APP_IOS = os.environ.get("BYOD_APP_IOS", "https://lvd.ada.or.kr/sihum/")   # 1.3.1 LVD 방송 앱 받는 곳(앱스토어 주소가 정해지면 바꿈)
APP_AOS = os.environ.get("BYOD_APP_AOS", "https://lvd.ada.or.kr/sihum/")
NAS_OLLIGI = os.environ.get("BYOD_NAS", "https://lvd.ada.or.kr/bfblive/byod_olligi.php")
OLLIGI_YEOLSOE = "byod-261014-k7m2"                       # 배프에 올리는 창구 열쇠(나스 byod_olligi.php 와 같음)

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


class Bang:
    def __init__(self):
        self.yeolsoe = ilkgi("yeolsoe")            # 방송 앱만 소리를 보낼 수 있게
        self.ttuigi = ilkgi("ttuigi", "ttuim")     # ttuim: 듣기 화면 저절로 뜨기 / cheok: 인터넷 있는 척만
        self.tonggwa_ttae = ilkgi("tonggwa", "an")  # deutgi: 듣기를 시작하면 그 폰은 인터넷 있는 척으로 / an: 늘 뜨기 창 그대로(현장 시험으로 정함)
        self.deut = []                              # 저지연 듣는 분
        self.deut_mp3 = []                          # 잠가도 이어지는 듣는 분
        self.bonaem = None                          # 지금 소리를 보내는 앱(쓰는 쪽)
        self.bonaem_ireum = ""
        self.majimak_sori = 0.0                     # 소리가 마지막으로 들어온 때
        self.yocheong = []                          # (때, 번호, 종류)
        self.yseq = 0
        self.tonggwa = set()                        # 듣기 화면을 이미 연 폰(주소)
        self.lame = None
        self.mp3_iss = shutil.which("lame") is not None or bool(os.environ.get("BYOD_LAME"))
        self.sijak_ttae = time.time()
        self.sigan_chai = 0.0                       # 방송 기기 시계와의 차이(공유기는 현장에서 시계를 맞출 길이 없음)
        self.haengsa = None                         # 지금 행사
        self.majimak_butum = 0.0                    # 방송 기기가 마지막으로 붙어 있던 때

    @property
    def keu(self):
        return self.bonaem is not None and time.time() - self.majimak_sori < 2.0

    def ppurida(self, b):
        for d in self.deut:
            d.neoki(b)

    def ppurida_mp3(self, b):
        for d in self.deut_mp3:
            d.neoki(b)


class Deut:
    """듣는 분 한 사람 — 쌓아 둘 줄과 보내는 일꾼"""
    def __init__(self, w, ip):
        self.w = w
        self.ip = ip
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
        B.tonggwa.add(peer[0])          # 방송 폰은 「인터넷 되느냐」에 늘 「됨」으로 답해 와이파이를 붙들게
        w.write(("OK " + PAN + "\n").encode()); await w.drain()
        while True:
            b = await asyncio.wait_for(r.read(4096), 30)
            if not b:
                break
            B.majimak_sori = time.time()
            B.majimak_butum = B.majimak_sori
            B.ppurida(b)
            mp3_neoki(b)
            if not B.mp3_iss and B.haengsa is not None:
                B.haengsa.sseugi(b)          # mp3 를 만들 수 없는 공유기면 뮤 그대로 녹음
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
                if B.haengsa is not None and B.bonaem is not None:
                    B.haengsa.sseugi(b)      # 방송 기기가 붙어 있는 동안만 녹음
        except Exception:
            pass
        B.lame = None
        await asyncio.sleep(2)


async def goyo_ilkkun():
    """해설이 쉬면 소리 없음을 흘려 mp3 가 멈추지 않게, 저지연 쪽은 5초마다 한 조각으로 끊긴 폰을 가려냄"""
    jeonbeon = 0.0
    bunTtae = 0.0
    while True:
        await asyncio.sleep(0.1)
        now = time.time()
        h = B.haengsa
        if h is not None:
            if now - bunTtae >= 60:
                bunTtae = now
                h.bun(len(B.deut) + len(B.deut_mp3))
            if B.bonaem is None and now - B.majimak_butum > IEUM:
                haengsa_maegam()
        if now - B.majimak_sori > 0.3:
            mp3_neoki(GOYO)
            if now - jeonbeon > 5:
                jeonbeon = now
                B.ppurida(bytes([0xFF]) * 160)
        # 막힌 폰 빼기
        for d in list(B.deut) + list(B.deut_mp3):
            if d.sseuneun and now - d.sseuneun > MAKHIM:
                kkeutnaegi(d)


def kkeutnaegi(d):
    d.jugeum = True
    try:
        d.w.close()
    except Exception:
        pass
    for l in (B.deut, B.deut_mp3):
        if d in l:
            l.remove(d)



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


JONGRYU = {"dowum": "도움 요청", "gungeum": "궁금함", "kkeunkim": "소리가 끊김"}


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
        self.g["kkeut"] = self.g.get("majimak") or jigeum()
        self.g["sangtae"] = "kkeut"
        self.g["gilee"] = self.gilee()
        nm = "nokeum." + ("mp3" if self.g["hyeongsik"] == "mp3" else "ul")
        try:
            with open(os.path.join(self.teo, nm + ".tmp"), "wb") as o:
                for j in self.g["jogak"]:
                    try:
                        with open(os.path.join(self.teo, j), "rb") as f:
                            shutil.copyfileobj(f, o)
                    except Exception:
                        pass
            os.replace(os.path.join(self.teo, nm + ".tmp"), os.path.join(self.teo, nm))
        except Exception:
            pass
        try:
            t = bogo_mandeul(self.g)
            with open(os.path.join(self.teo, "bogo.txt"), "wb") as f:
                f.write(t.replace("\n", "\r\n").encode("cp949", "replace"))
        except Exception:
            pass
        self.jeojang()


def bogo_mandeul(g):
    """한 장짜리 결과보고(글) — 낭독기와 점자정보단말기로 읽히게 기호 없이"""
    L = []
    L.append("현장영상해설 방송 결과보고")
    L.append("사단법인 한국시각장애인현장영상해설협회")
    L.append("")
    L.append("행사 이름: " + g["jemok"])
    L.append("날짜: " + nal_mal(g["sijak"]))
    L.append("현장영상해설사: " + (g.get("haeseolsa") or "적지 않음"))
    L.append("시작: " + sigak_mal(g["sijak"]))
    L.append("끝: " + sigak_mal(g["kkeut"]))
    L.append("방송 시간: " + gilee_mal(max(0, g["kkeut"] - g["sijak"])))
    bun = g.get("bun") or []
    if bun:
        mx = max(bun, key=lambda x: x[1])
        pyeong = sum(x[1] for x in bun) / len(bun)
        L.append("듣는 분: 가장 많을 때 %d명(%s), 평균 %d명" % (mx[1], sigak_mal(mx[0]), int(round(pyeong))))
        L.append("")
        L.append("10분 간격 듣는 분 수")
        sijak = g["sijak"]
        dah = sijak
        for t, n in bun:
            if t >= dah:
                L.append(sigak_mal(t) + " " + str(n) + "명")
                dah = t + 600
    else:
        L.append("듣는 분: 기록 없음")
    yc = g.get("yocheong") or []
    L.append("")
    if yc:
        se = {}
        for _, k, _n in yc:
            se[k] = se.get(k, 0) + 1
        L.append("도움 요청: 모두 %d번 (%s)" % (len(yc), ", ".join("%s %d번" % (JONGRYU.get(k, k), v) for k, v in se.items())))
        for t, k, n in yc:
            L.append(sigak_mal(t) + " " + JONGRYU.get(k, k) + " (" + (n + "번" if n else "번호 없음") + ")")
    else:
        L.append("도움 요청: 없음")
    kk = [x for x in (g.get("kkeunkim") or []) if x[1] == "kkeunkim"]
    L.append("")
    L.append("방송 기기 끊김: " + ("없음" if not kk else "%d번" % len(kk)))
    jeon = None
    for t, jong, ir in (g.get("kkeunkim") or []):
        if jong == "kkeunkim":
            jeon = t
            L.append(sigak_mal(t) + " 끊김" + (" (" + ir + ")" if ir else ""))
        elif jong == "butum" and jeon is not None:
            L.append(sigak_mal(t) + " 다시 붙음" + (" (" + ir + ")" if ir else ""))
    L.append("")
    L.append("녹음: " + g["sid"] + (".mp3" if g.get("hyeongsik") == "mp3" else ".ul") + ", 길이 " + gilee_mal(g.get("gilee", 0)))
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
    nas_bonaegi(sid, "girok", json.dumps(g, ensure_ascii=False).encode("utf-8"))
    return haengsa_olim(sid)


async def olligi_ilkkun():
    gan = float(os.environ.get("BYOD_OLLIGI_GAN", "300"))
    while True:
        await asyncio.sleep(gan)
        for m in haengsa_mokrok():
            if m["sangtae"] == "kkeut" and not m["olim"]:
                try:
                    await asyncio.get_event_loop().run_in_executor(None, haengsa_olligi, m["sid"])
                except Exception:
                    break       # 인터넷이 없으면 다음 5분에

# ───── 듣기 화면과 길(7880, 80번은 방화벽이 돌려줌) ─────
CAPTIVE = ("generate_204", "gen_204", "hotspot-detect", "library/test/success", "connecttest.txt",
           "ncsi.txt", "success.txt", "canonical.html", "kindle-wifi", "mobile/status.php", "check_network_status")


def meori_bonae(w, code, mal, chong, gil=0, deo=""):
    h = "HTTP/1.1 %d %s\r\nContent-Type: %s\r\n" % (code, mal, chong)
    if gil >= 0:
        h += "Content-Length: %d\r\n" % gil
    h += "Cache-Control: no-store\r\nAccess-Control-Allow-Origin: *\r\n" + deo + "Connection: close\r\n\r\n"
    w.write(h.encode("ascii"))


def geulja(w, code, chong, t, deo=""):
    b = t.encode("utf-8") if isinstance(t, str) else t
    mal = {200: "OK", 204: "No Content", 302: "Found", 404: "Not Found", 403: "Forbidden"}.get(code, "OK")
    meori_bonae(w, code, mal, chong, len(b), deo)
    if b:
        w.write(b)


def deut_hwamyeon():
    p = os.path.join(TEO, "deut.html")
    if os.path.isfile(p) and os.path.getsize(p) > 100:
        with open(p, "rb") as f:
            return f.read()
    return DEUT_HTML.replace("%%APP_IOS%%", APP_IOS).replace("%%APP_AOS%%", APP_AOS).encode("utf-8")


def sangtae():
    return json.dumps({
        "hyeonjang": True, "byod": True, "flint": True, "pan": "BYOD " + PAN, "bild": BILD,
        "keu": B.keu, "mic": B.bonaem is not None, "bonaem": B.bonaem_ireum,
        "n": len(B.deut) + len(B.deut_mp3), "n_jeo": len(B.deut), "n_mp3": len(B.deut_mp3),
        "haengsa": B.haengsa.sid if B.haengsa else "",
        "jemok": B.haengsa.g.get("jemok", "") if B.haengsa else "", "haeseolsa": B.haengsa.g.get("haeseolsa", "") if B.haengsa else "",
        "mp3": B.mp3_iss, "ttuigi": B.ttuigi, "tonggwa": B.tonggwa_ttae, "yseq": B.yseq,
        "nas": "", "dang": "", "dseq": 0, "dsik": "", "dmok": [], "gin": "", "gseq": 0, "saepan": ""
    }, ensure_ascii=False)


async def web(r, w):
    peer = w.get_extra_info("peername")
    ip = peer[0] if peer else ""
    gyesok = False
    try:
        meori = await asyncio.wait_for(r.readuntil(b"\r\n\r\n"), 10)
        jul = meori.decode("latin-1").split("\r\n")
        cheot = jul[0].split(" ")
        if len(cheot) < 2:
            return
        juso = cheot[1]
        host = ""
        for l in jul[1:]:
            if l.lower().startswith("host:"):
                host = l[5:].strip().split(":")[0].lower()
        if juso.startswith("http://"):
            juso = "/" + juso.split("/", 3)[-1] if juso.count("/") >= 3 else "/"
        gil, _, q = juso.partition("?")
        qs = urllib.parse.parse_qs(q)
        uri = gil.lower()
        uri_host = host not in ("", SEOBEO_IP, "127.0.0.1", "localhost", "byod.lan", "byod")

        # 1) 폰의 「인터넷 되느냐」 물음
        if any(c in uri for c in CAPTIVE) or (uri_host and uri in ("/", "/index.html")):
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
            return
        if uri_host:
            # 다른 누리집 주소를 치면 듣기 화면으로
            geulja(w, 302, "text/html", "", "Location: http://%s/\r\n" % SEOBEO_IP)
            return

        if uri in ("/", "/index.html", "/deut.html", "/deut"):
            geulja(w, 200, "text/html; charset=utf-8", deut_hwamyeon())
        elif uri == "/status":
            geulja(w, 200, "application/json; charset=utf-8", sangtae())
        elif uri == "/stream":
            gyesok = True
            await deut_butigi(w, ip, False)
        elif uri == "/stream.mp3":
            if not B.mp3_iss:
                geulja(w, 404, "text/plain; charset=utf-8", "mp3 길이 없습니다.")
            else:
                gyesok = True
                await deut_butigi(w, ip, True)
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
        elif uri == "/yocheong":
            k = (qs.get("k") or [""])[0][:20]
            n = (qs.get("n") or [""])[0][:10]
            now = time.time()
            jeon = [y for y in B.yocheong if y[1] == n and y[2] == k]
            ok = (not jeon) or now - jeon[-1][0] > 60 or not n
            if ok:
                B.yocheong.append((now, n, k))
                del B.yocheong[:-200]
                B.yseq += 1
                if B.haengsa is not None:
                    B.haengsa.yocheong(k, n)
            geulja(w, 200, "application/json", json.dumps({"ok": ok}))
        elif uri == "/yocheong_mokrok":
            geulja(w, 200, "application/json; charset=utf-8", json.dumps(
                {"yseq": B.yseq, "mokrok": [{"t": int(y[0]), "n": y[1], "k": y[2]} for y in B.yocheong[-20:]]}, ensure_ascii=False))
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
            nm = jul2[3] if len(jul2) > 3 else ""
            p = os.path.join(HAENGSA_TEO, os.path.basename(jul2[2]) if len(jul2) > 2 else "", os.path.basename(nm))
            if nm in ("nokeum.mp3", "nokeum.ul", "bogo.txt", "girok.json") and os.path.isfile(p):
                chong = {"mp3": "audio/mpeg", "ul": "application/octet-stream", "txt": "text/plain; charset=euc-kr", "json": "application/json; charset=utf-8"}[nm.rsplit(".", 1)[1]]
                with open(p, "rb") as f:
                    geulja(w, 200, chong, f.read())
            else:
                geulja(w, 404, "text/plain; charset=utf-8", "없는 녹음입니다.")
        elif uri in ("/beonho", "/deutnun", "/jiyeon"):
            geulja(w, 200, "application/json", '{"ok":true}')
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


async def deut_butigi(w, ip, mp3):
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
    d = Deut(w, ip)
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


async def main():
    s1 = await asyncio.start_server(web, "0.0.0.0", WEB_PORT, backlog=1024, reuse_address=True)
    s2 = await asyncio.start_server(sori_batgi, "0.0.0.0", SORI_PORT, backlog=16, reuse_address=True)
    os.makedirs(HAENGSA_TEO, exist_ok=True)
    haengsa_dasi_yeolgi()
    asyncio.ensure_future(goyo_ilkkun())
    asyncio.ensure_future(olligi_ilkkun())
    asyncio.ensure_future(mp3_ilkkun())
    print("BYOD 플린트 서버 %s (%s) — 듣기 %d, 소리 %d, mp3 %s" % (PAN, BILD, WEB_PORT, SORI_PORT, B.mp3_iss), flush=True)
    async with s1, s2:
        await asyncio.gather(s1.serve_forever(), s2.serve_forever())


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
