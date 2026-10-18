#!/usr/bin/env python3
# 길눈 사용법 동영상 만들기 1.0 (빌드 261018-1, 이사장님 승인 2026-10-18 「1」)
# 지난 동영상(2026-10-15~17, 1920×1080)과 같은 짜임으로 다시 만듦.
#   왼쪽: 폰 화면(지금 줄에 주황 테두리와 손가락), 오른쪽: 절 제목·지금 줄 크게, 아래: [안내]/[보이스오버]/[톡백]/[길눈] 자막
#   목소리 셋(수퍼톤 2, 비용 없음): 안내는 남자 목소리, 보이스오버·톡백은 더 빠른 여자 목소리, 길눈이 하는 말은 또 다른 여자 목소리
#   보이스오버·톡백이 줄을 읽기 전에는 따닥 소리, 두 번 두드리면 두 번
# 쓰는 법: python3 make.py 대본.py 나올파일.mp4  (나올 파일 이름이 i로 시작하면 아이폰, a로 시작하면 안드로이드)
import sys, os, re, json, wave, struct, math, subprocess, importlib.util, tempfile, shutil, html
import numpy as np, soundfile as sf

HERE = os.path.dirname(os.path.abspath(__file__))
MODEL = os.environ.get("SUPERTONIC", "/home/claude/tts/sherpa-onnx-supertonic-tts-int8-2026-03-06/")
SR = 44100
# (목소리 번호, 만들 때 빠르기, 늘이기 빠르기) — 2026-10-18 이사장님: 약 50% 빠르게, 보이스오버 발음 또렷하게
# 만들 때 빠르기를 올리면 말이 뭉개져(받아쓰기 정확도 0.65~0.84), 보통 빠르기로 만든 뒤 소리 높이는 그대로 두고 길이만 줄임(0.97~0.99)
MOKSORI = {"N": (8, 1.0, 1.2), "V": (1, 1.1, 1.28), "G": (2, 1.05, 1.16)}   # 안내(남자), 낭독기(여자, 더 빠르게), 길눈(다른 여자)
TAG = {"N": "안내", "G": "길눈"}

_tts = None
def tts():
    global _tts
    if _tts is None:
        import sherpa_onnx as s
        d = MODEL
        c = s.OfflineTtsConfig(model=s.OfflineTtsModelConfig(supertonic=s.OfflineTtsSupertonicModelConfig(
            duration_predictor=d + "duration_predictor.int8.onnx", text_encoder=d + "text_encoder.int8.onnx",
            vector_estimator=d + "vector_estimator.int8.onnx", vocoder=d + "vocoder.int8.onnx",
            tts_json=d + "tts.json", unicode_indexer=d + "unicode_indexer.bin", voice_style=d + "voice.bin"), num_threads=4))
        _tts = s.OfflineTts(c)
    return _tts

# 읽기 좋게 — 대본 글자는 그대로 두고 소리로만 바꿈
SU = "영 일 이 삼 사 오 육 칠 팔 구".split()
def hanja_su(n):
    n = int(n)
    if n == 0: return "영"
    out = ""
    for v, w in ((10000, "만"), (1000, "천"), (100, "백"), (10, "십")):
        q, n = divmod(n, v)
        if q: out += ("" if q == 1 else SU[q]) + w
    return out + (SU[n] if n else "")
# 소리 사전 — 잘 뭉개지는 낱말을 소리로 낼 때만 끊어 읽기 좋게(화면 글자는 그대로). 2026-10-18 이사장님: 협회 이름이 뭉개짐
SORI = [
    ("사단법인 한국시각장애인현장영상해설협회", "사단법인, 한국시각장애인, 현장 영상 해설, 협회"),   # 이사장님(15:46): 사단법인 / 한국시각장애인 / 현장영상해설협회로 끊기. 해설이 뭉개져 해설 뒤에 아주 짧은 쉼
    ("현장영상해설사", "현장 영상 해설사"), ("현장영상해설", "현장 영상 해설"),
    ("긴급통화서비스", "긴급 통화 서비스"), ("긴급통화", "긴급 통화"),
    ("보이스오버", "보이스 오버"), ("음향신호기", "음향 신호기"), ("음성유도기", "음성 유도기"),
    ("국가지점번호", "국가 지점 번호"), ("교통약자", "교통 약자"), ("시각장애인", "시각 장애인"),
]
def norm(t):
    for a, b in SORI:
        t = t.replace(a, b)
    t = t.replace("—", ",").replace(" - ", ", ").replace("「", "").replace("」", "").replace("·", ", ")
    t = re.sub(r"(\d+)\.(\d+)\.(\d+)판", lambda m: f"{hanja_su(m[1])} 쩜 {hanja_su(m[2])}{' 쩜 ' + hanja_su(m[3]) if m[3] != '0' else ''} 판", t)
    t = re.sub(r"(\d+)-(\d+)편", lambda m: f"{hanja_su(m[1])} 다시 {hanja_su(m[2])} 편", t)
    t = re.sub(r"(\d+)-(\d+)", lambda m: f"{hanja_su(m[1])} 다시 {hanja_su(m[2])}", t)
    t = re.sub(r"(\d+)\.(\d)킬로미터", lambda m: f"{hanja_su(m[1])} 쩜 {hanja_su(m[2])} 킬로미터", t)
    t = re.sub(r"(\d+)\.(\d+)(?=[초배])", lambda m: f"{hanja_su(m[1])} 쩜 {' '.join(SU[int(c)] for c in m[2])} ", t)   # 2.5초, 1.5배, 0.8초
    t = t.replace("TV", "티브이").replace("QR", "큐알").replace("iOS", "아이오에스")
    t = re.sub(r"(?<![\d.])(\d)((?: \d)+)(?!\d|\.\d)", lambda m: " ".join("공" if c == "0" else SU[int(c)] for c in (m[1] + m[2]).split()), t)   # 정류장 번호·함께 시험 번호는 한 자씩
    HOE = ["", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열", "열한", "열두"]
    t = re.sub(r"(?<![\d.])(1[0-2]|[1-9])시(?=[ ,.에는부까쯤]|$)", lambda m: HOE[int(m[1])] + " 시", t)   # 시계 방향은 한 시, 열두 시
    t = re.sub(r"(\d+)(정거장|곳|분께|분이|분까지|대|칸|가지|구간|번째)", lambda m: (("첫" if m[1] == "1" and m[2] == "번째" else HOE[int(m[1])]) if int(m[1]) <= 12 else hanja_su(m[1])) + " " + m[2], t)
    t = re.sub(r"(\d+)(걸음|도|호선|호터널|센티|배|킬로미터|퍼센트|시간|단계|년)", lambda m: hanja_su(m[1]) + ("" if m[2] in ("호선", "호터널") else " ") + m[2], t)
    t = re.sub(r"(\d+)(미터|킬로미터|분|초|번째|번|층|개|명|시)", lambda m: (hanja_su(m[1]) if m[2] in ("미터", "킬로미터", "분", "초", "층") else m[1]) + " " + m[2], t)
    return t

def _hanbeon(kind, sori):
    sid, sp, neul = MOKSORI[kind]
    import sherpa_onnx as s
    g = s.GenerationConfig(); g.sid = sid; g.speed = sp; g.extra = {"lang": "ko"}
    a = tts().generate(sori, g)
    x = np.array(a.samples, dtype=np.float32)
    if a.sample_rate != SR:
        import librosa
        x = librosa.resample(x, orig_sr=a.sample_rate, target_sr=SR)
    if neul != 1.0:
        r = subprocess.run(["ffmpeg", "-loglevel", "error", "-f", "f32le", "-ar", str(SR), "-ac", "1", "-i", "pipe:0",
                            "-af", f"atempo={neul}", "-f", "f32le", "pipe:1"], input=x.tobytes(), capture_output=True, check=True)
        x = np.frombuffer(r.stdout, dtype=np.float32).copy()
    return x

# 말소리 점검 — 문장마다 받아쓰기로 다시 들어 보고, 뭉개졌으면 다시 만들어 가장 또렷한 것을 씀(2026-10-18 이사장님 승인 「1」)
ASR = os.environ.get("SENSEVOICE", "/home/claude/tts/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/")
_asr = None
JEOMSU = []          # (종류, 글, 점수, 받아쓴 것)
def _deutgi(x):
    global _asr
    if _asr is None:
        import sherpa_onnx as s
        _asr = s.OfflineRecognizer.from_sense_voice(model=ASR + "model.int8.onnx", tokens=ASR + "tokens.txt", language="ko", use_itn=False)
    import librosa
    y = librosa.resample(x, orig_sr=SR, target_sr=16000)
    st = _asr.create_stream(); st.accept_waveform(16000, y); _asr.decode_stream(st)
    return st.result.text
def _hangeul(t): return re.sub(r"[^가-힣]", "", t)
def _jeom(sori, x):
    import difflib
    h = _deutgi(x)
    a, b = _hangeul(sori), _hangeul(h)
    sc = difflib.SequenceMatcher(None, a, b).ratio() if a else 1.0
    deol = lambda t: len(re.findall(r"(?=(.)\1)", t)) + len(re.findall(r"(?=(.).\1)", t))   # 「장애인애인」, 「협회회」처럼 말이 되풀이되어 더듬은 곳(원래 글보다 많은 만큼만 깎음)
    sc -= 0.05 * max(0, deol(b) - deol(a))
    for k in ("협회", "해설", "장애인", "우리"):   # 잘 뭉개지는 낱말이 빠졌으면 크게 깎음(「우리」는 2026-10-18 여는 말에서 빠져 더함)
        sc -= 0.1 * max(0, a.count(k) - b.count(k))
    return sc, h
def malsori(kind, text):
    sori = norm(text)
    if not os.path.exists(ASR + "model.int8.onnx") or os.environ.get("JEOMGEOM") == "0":
        return _hanbeon(kind, sori)
    best = None
    himdeun = "해설, 협회" in sori           # 협회 이름: 받아쓰기가 「현장영상해설협회」를 온전히 알아들을 때까지 많아야 열두 번
    eoryeoun = any(k in sori for k in ("협회", "해설"))   # 잘 뭉개지는 낱말이 든 문장은 여덟 번까지
    for n in range(12 if himdeun else 8 if eoryeoun else 4):    # 그 밖에는 많아야 네 번 만들어 봄
        x = _hanbeon(kind, sori); sc, h = _jeom(sori, x)
        if himdeun:
            hh = _hangeul(h)
            if not all(k in hh for k in ("사단법인", "한국시각장애인", "현장영상해설협회")): sc -= 0.2
            if "협회회" in hh or "장애인애인" in hh: sc -= 0.1
        if best is None or sc > best[0]: best = (sc, x, h)
        if sc >= 0.96 and (not himdeun or sc >= 0.99): break
    if not himdeun and best[0] < 0.95 and ", " in sori:   # 그래도 뭉개지면 쉼표에서 나누어 토막마다 골라 이어 붙임
        tok = []
        for p in [q for q in sori.split(", ") if q.strip()]:
            bb = None
            for n in range(3):
                y = _hanbeon(kind, p); sc, h = _jeom(p, y)
                if bb is None or sc > bb[0]: bb = (sc, y)
                if sc >= 0.96: break
            tok += [bb[1], np.zeros(int(SR * 0.12), dtype=np.float32)]
        x = np.concatenate(tok[:-1]); sc, h = _jeom(sori, x)
        if best is None or sc > best[0]: best = (sc, x, h)
    JEOMSU.append((kind, text, round(best[0], 3), best[2]))
    return best[1]

def ttak(beon=1):
    # 보이스오버·톡백이 줄로 옮길 때의 짧은 딸깍 소리, 두 번 두드리면 두 번
    n = int(SR * 0.035)
    t = np.arange(n) / SR
    one = (np.sin(2 * math.pi * 2200 * t) * np.exp(-t * 160) * 0.5).astype(np.float32)
    gap = np.zeros(int(SR * 0.08), dtype=np.float32)
    out = []
    for i in range(beon):
        out += [one, gap]
    return np.concatenate(out)

def swim(s):
    return np.zeros(int(SR * s), dtype=np.float32)

# ── 화면 그리기 ──
LOGO = ('<svg viewBox="0 0 205 162" width="{w}"><g fill="#f7e7b4"><circle cx="33.9" cy="28.5" r="8.4"/><circle cx="33.9" cy="46.7" r="8.4"/>'
        '<circle cx="33.9" cy="65" r="8.4"/><circle cx="88.8" cy="28.5" r="8.4"/><circle cx="88.8" cy="46.7" r="8.4"/><circle cx="88.7" cy="65" r="8.4"/>'
        '<circle cx="107.9" cy="65" r="8.4"/><circle cx="148.6" cy="28.6" r="8.4"/><circle cx="167.6" cy="28.5" r="8.4"/><circle cx="167.6" cy="46.8" r="8.4"/></g>'
        '<path fill="#d71618" d="M26 80H37V131H67V142H26Z"/><path fill="#f4c720" d="M66 80H79L94.5 126 111 80H124L101 142H88Z"/>'
        '<path fill="#3bb35d" fill-rule="evenodd" d="M135 80H155A31 31 0 0 1 155 142H135ZM146 91H155A20 20 0 0 1 155 131H146Z"/></svg>')
CSS = """
*{box-sizing:border-box;margin:0;padding:0}body{width:1920px;height:1080px;font-family:'Noto Sans CJK KR','Noto Sans KR',sans-serif;background:#f4f1e6;overflow:hidden;position:relative}
.top{height:96px;background:#12346e;color:#fff;display:flex;align-items:center;padding:0 40px;gap:24px}.top h1{font-size:40px;font-weight:700}.top .r{margin-left:auto;font-size:26px;opacity:.85}
.phone{position:absolute;left:120px;top:126px;width:470px;height:920px;border-radius:56px;background:#111;padding:16px}
.scr{width:100%;height:100%;border-radius:42px;background:#fbfaf6;overflow:hidden;position:relative}
.st{font-size:20px;font-weight:700;padding:14px 32px 4px}.hd{display:flex;align-items:baseline;gap:14px;padding:4px 24px 10px}.hd .b{font-size:20px;color:#12346e}.hd .t{font-size:26px;font-weight:800}
.rows{padding:0 16px;display:flex;flex-direction:column;gap:10px}
.row{background:#12346e;color:#fff;border-radius:12px;padding:12px 14px 12px 22px;font-size:19px;font-weight:600;line-height:1.35;position:relative}
.row:before{content:'';position:absolute;left:8px;top:10px;bottom:10px;width:5px;border-radius:3px;background:#f4c720}
.row.in{background:#fff;color:#666;border:2px solid #bbb;font-weight:400}.row.in:before{display:none}.row.tx{background:none;color:#333;font-weight:400;padding-left:6px}.row.tx:before{display:none}
.row.on{outline:5px solid #f5a623;outline-offset:2px}
.hand{position:absolute;width:44px;height:52px}
.tabs{position:absolute;bottom:0;left:0;right:0;height:78px;border-top:1px solid #ddd;display:flex;justify-content:space-around;align-items:center;font-size:17px;color:#888;background:#fff}
.tabs .on{color:#12346e;font-weight:800;border-top:4px solid #f4c720;padding-top:6px}
.sec{position:absolute;left:690px;top:150px;font-size:54px;font-weight:800;color:#12346e;width:1150px}
.big{position:absolute;left:690px;top:380px;width:1100px;font-size:46px;line-height:1.45;font-weight:700;border-radius:20px;padding:28px 120px 28px 50px;background:#12346e;color:#fff;outline:6px solid #f5a623;outline-offset:4px}
.big:before{content:'';position:absolute;left:18px;top:24px;bottom:24px;width:10px;border-radius:5px;background:#f4c720}
.big.in{background:#fff;color:#333;outline:3px solid #12346e}.big.in:before{display:none}.big.tx{background:none;color:#333;outline:none}.big.tx:before{display:none}
.big .dot{position:absolute;right:36px;top:50%;margin-top:-34px;width:68px;height:68px;border-radius:50%;background:rgba(120,90,60,.75);border:4px solid #f5a623}
.cap{position:absolute;left:690px;right:80px;bottom:60px;background:#1d2533;color:#fff;font-size:44px;font-weight:700;line-height:1.45;border-radius:18px;padding:26px 36px}
.title{position:absolute;inset:0;background:#12346e;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:30px;color:#fff}
.title h2{font-size:76px}.title p{font-size:34px;opacity:.85}.title .cap{left:180px;right:180px}
"""
HAND = ('<svg class="hand" style="left:{x}px;top:{y}px" viewBox="0 0 44 52"><path d="M6 2 L6 38 L15 30 L22 47 L29 44 L22 28 L34 28 Z" '
        'fill="#fff" stroke="#111" stroke-width="3" stroke-linejoin="round"/></svg>')

def gurim_html(s, meori, pan_ireum):
    e = html.escape
    if s.get("pyoji"):
        return (f'<html><head><style>{CSS}</style></head><body><div class="title">{LOGO.format(w=260)}'
                f'<h2>{e(s["pyoji"])}</h2><p>사단법인 한국시각장애인현장영상해설협회</p>'
                f'<div class="cap">{e(s["cap"])}</div></div></body></html>')
    h = [f'<html><head><style>{CSS}</style></head><body><div class="top">{LOGO.format(w=70)}<h1>{e(meori)}</h1><div class="r">길눈 사용법</div></div>']
    sc = s.get("hwamyeon") or {}
    rows = sc.get("jul", [])
    on = s.get("on")
    h.append('<div class="phone"><div class="scr"><div class="st">9:41</div>')
    if sc.get("jemok"):
        dw = "‹ 뒤로" if pan_ireum == "보이스오버" else "‹ 뒤로 - " + sc.get("ap", "길 찾기")
        h.append(f'<div class="hd">{"<span class=b>" + e(dw) + "</span>" if sc.get("dwiro", True) else ""}<span class="t">{e(sc["jemok"])}</span></div>')
    else:
        h.append('<div style="height:14px"></div>')
    h.append('<div class="rows">')
    for i, r in enumerate(rows):
        kind = "in" if r.startswith("칸:") else ("tx" if r.startswith("글:") else "")
        txt = r.split(":", 1)[1] if kind else r
        h.append(f'<div class="row {kind}{" on" if i == on else ""}" id="r{i}">{e(txt)}</div>')
    h.append('</div>')
    tab = sc.get("tab", "길 찾기")
    h.append('<div class="tabs">' + "".join(f'<span class="{"on" if t == tab else ""}">{t}</span>' for t in ["길 찾기", "둘러보기", "음악·방송", "나눔", "설정"]) + '</div>')
    h.append('<div id="handslot"></div></div></div>')
    if s.get("jeol"):
        h.append(f'<div class="sec">{e(s["jeol"])}</div>')
    if on is not None and on < len(rows):
        r = rows[on]
        kind = "in" if r.startswith("칸:") else ("tx" if r.startswith("글:") else "")
        txt = r.split(":", 1)[1] if kind else r
        h.append(f'<div class="big {kind}">{e(txt)}{"<span class=dot></span>" if s.get("dudeurim") else ""}</div>')
    if s.get("cap"):
        h.append(f'<div class="cap">{e(s["cap"])}</div>')
    h.append('<script>var r=document.querySelector(".row.on");if(r){var b=r.getBoundingClientRect(),p=document.querySelector(".scr").getBoundingClientRect();'
             'document.getElementById("handslot").outerHTML=\'' + HAND.replace("{x}", "'+(b.right-p.left-40)+'").replace("{y}", "'+(b.top-p.top+b.height/2-6)+'") + '\';}</script>')
    h.append('</body></html>')
    return "".join(h)

def main():
    dae, nae = sys.argv[1], sys.argv[2]
    # 한 대본으로 두 판: python3 make.py dae/c1.py i1.mp4 (PAN=i 아이폰, PAN=a 안드로이드 — 나올 파일 이름 첫 글자로 정함)
    os.environ.setdefault("PAN", os.path.basename(nae)[0])
    sys.path.insert(0, os.path.dirname(os.path.abspath(dae)))   # 대본 곁의 teul.py(여는 말·맺는 말)
    spec = importlib.util.spec_from_file_location("dae", dae); m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
    meori = m.MEORI
    nr = m.NAEDOGI   # "보이스오버" 또는 "톡백"
    tmp = tempfile.mkdtemp()
    from playwright.sync_api import sync_playwright
    pw = sync_playwright().start()
    br = pw.chromium.launch()
    pg = br.new_page(viewport={"width": 1920, "height": 1080})
    jul = []          # (그림, 길이)
    sori = []
    seuksi = 0
    hwamyeon = None; jeol = None; on = None
    for k, st in enumerate(m.CHARYE):
        # st: dict(hwamyeon=..., jeol=..., on=..., mal=[(kind, text), ...], pyoji=...)
        if "hwamyeon" in st: hwamyeon = st["hwamyeon"]
        if "jeol" in st: jeol = st["jeol"]
        if "on" in st: on = st["on"]
        for j, (kind, text) in enumerate(st["mal"]):
            if kind in ("V", "VV"):
                tag = nr
            else:
                tag = TAG[kind]
            s = {"hwamyeon": hwamyeon, "jeol": jeol, "on": on, "cap": f"[{tag}] {text}", "dudeurim": kind == "VV", "pyoji": st.get("pyoji")}
            png = os.path.join(tmp, f"f{k:03d}_{j:02d}.png")
            pg.set_content(gurim_html(s, meori, nr)); pg.screenshot(path=png)
            a = []
            if kind == "V": a.append(ttak(1))
            if kind == "VV": a.append(ttak(2))
            a.append(malsori("V" if kind in ("V", "VV") else kind, text))
            a.append(swim(0.4 if kind == "N" else 0.3))
            x = np.concatenate(a)
            sori.append(x)
            jul.append((png, len(x) / SR))
    br.close(); pw.stop()
    wav = os.path.join(tmp, "sori.wav")
    sf.write(wav, np.concatenate(sori), SR)
    lst = os.path.join(tmp, "jul.txt")
    with open(lst, "w") as f:
        for p, d in jul: f.write(f"file '{p}'\nduration {d:.3f}\n")
        f.write(f"file '{jul[-1][0]}'\n")
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-f", "concat", "-safe", "0", "-i", lst, "-i", wav,
                    "-vf", "fps=10,format=yuv420p", "-c:v", "libx264", "-preset", "veryfast", "-tune", "stillimage", "-crf", "30",
                    "-c:a", "aac", "-ac", "1", "-b:a", "64k", "-shortest", "-movflags", "+faststart", nae], check=True)
    shutil.rmtree(tmp)
    if JEOMSU:
        with open(nae + ".jeomsu.tsv", "w") as f:
            for k, t, sc, h in sorted(JEOMSU, key=lambda r: r[2]):
                f.write(f"{sc}\t{k}\t{t}\t{h}\n")
        print("말소리 점수 가장 낮은 것", min(r[2] for r in JEOMSU), "평균", round(sum(r[2] for r in JEOMSU) / len(JEOMSU), 3))
    print(nae, round(sum(d for _, d in jul)), "초")

if __name__ == "__main__":
    main()
