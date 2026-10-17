# 여정 다시 돌려 보기 시험 — 시나리오 1 만들기 (2026-10-17 이사장님 KTX 64 부산→서울, 광명에서 서울까지)
# 그날 겪은 오류 셋을 되풀이해 막는지 봄: 영등포·용산에서 「내리십시오」, 서지 않는 역 가까이 멈춤을 도착으로, 다른 노선 9.2·5.6킬로미터 터널
import json, math, random
random.seed(7)
R = 6371000.0
def geori(a, b):
    p = math.pi / 180
    da = (b[0]-a[0])*p; do = (b[1]-a[1])*p
    h = math.sin(da/2)**2 + math.cos(a[0]*p)*math.cos(b[0]*p)*math.sin(do/2)**2
    return 2*R*math.asin(min(1, math.sqrt(h)))
def bangwi(a, b):
    p = math.pi / 180
    y = math.sin((b[1]-a[1])*p)*math.cos(b[0]*p)
    x = math.cos(a[0]*p)*math.sin(b[0]*p) - math.sin(a[0]*p)*math.cos(b[0]*p)*math.cos((b[1]-a[1])*p)
    return (math.atan2(y, x)/p + 360) % 360
# 2.99.0 선로 — 앱에 싣는 철길 자료(Gilnun/cheolgil.bin)에서 광명 남쪽 3킬로미터부터 서울역까지 실제 철길을 따라감
#   (예전에는 어림 선이라 선로 맞추기를 시험할 수 없었음). 긴 터널 안에서는 위성이 끊기므로 위치를 넣지 않음
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from cheolgil_gil import ilgi, gil as cheolgil_gil
_xy, _jul = ilgi(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'Gilnun', 'cheolgil.bin'))
gil = cheolgil_gil(_xy, _jul, (37.3900, 126.8833), (37.5541, 126.9707), lambda p: 4.0 if p & 4 else 1.0)
_teo = set()
for _pyo, _nm, _js in _jul:
    if _pyo & 1:
        _L = sum(geori(_xy[_js[k]], _xy[_js[k+1]]) for k in range(len(_js)-1))
        if _L >= 300:
            for k in range(len(_js)-1): _teo.add(frozenset((_xy[_js[k]], _xy[_js[k+1]])))
nu = [0.0]
for i in range(1, len(gil)): nu.append(nu[-1] + geori(gil[i-1], gil[i]))
def jari(s):
    s = max(0, min(s, nu[-1]))
    for i in range(1, len(nu)):
        if s <= nu[i]:
            f = (s - nu[i-1]) / max(1e-9, nu[i]-nu[i-1])
            a, b = gil[i-1], gil[i]
            return (a[0]+(b[0]-a[0])*f, a[1]+(b[1]-a[1])*f), bangwi(a, b)
    return gil[-1], bangwi(gil[-2], gil[-1])
def _s(pt):
    best = None
    for i in range(1, len(gil)):
        for f in [k/20 for k in range(21)]:
            q = (gil[i-1][0]+(gil[i][0]-gil[i-1][0])*f, gil[i-1][1]+(gil[i][1]-gil[i-1][1])*f)
            d = geori(q, pt)
            if best is None or d < best[0]: best = (d, nu[i-1] + (nu[i]-nu[i-1])*f)
    return best[1]
s_gm = _s((37.416196, 126.884681))   # 광명
s_yd = _s((37.515683, 126.907878))   # 영등포
s_ys = _s((37.530076, 126.964824))   # 용산
s_se = _s((37.554069, 126.970703))   # 서울
def _teoneol_an(s):
    for i in range(1, len(nu)):
        if s <= nu[i]: return frozenset((gil[i-1], gil[i])) in _teo
    return False
# 빠르기 계획(구간 끝 자리, 그 구간 빠르기 m/s) — 서기는 (자리, 0, 초)
t0 = 21*3600 + 3*60               # 21시 3분에 광명 남쪽
fixes = []
t = t0; s = 0.0; v = 25.0
def chuga(t, s, v):
    if _teoneol_an(s): return            # 긴 터널 안 — 위성 끊김
    (la, lo), h = jari(s)
    n = 5.0 / 111000
    fixes.append({"t": t, "lat": round(la + random.uniform(-n, n), 6), "lon": round(lo + random.uniform(-n, n), 6),
                  "acc": 10, "sokdo": round(v, 1), "bang": round(h, 1) if v > 1 else -1})
def gagi(kkeut, v_mok, seom=False, gasok=0.6):
    """kkeut 까지 v_mok 빠르기로. seom 이면 kkeut 에 서도록 미리 줄임"""
    global t, s, v
    while s < kkeut - 0.5:
        if v < v_mok: v = min(v_mok, v + gasok)
        elif v > v_mok: v = max(v_mok, v - gasok)
        mam = kkeut - s
        if seom and mam < (v*v)/(2*gasok) + 5: v = max(0.8, min(v, math.sqrt(2*gasok*max(mam, 0.4))))
        s = min(kkeut, s + v); t += 1; chuga(t, s, v)
    s = kkeut
def seogi(cho):
    global t, v
    v = 0
    for _ in range(cho): t += 1; chuga(t, s, 0)
gagi(s_gm, 25, True); seogi(120)            # 광명 도착, 21시 7분쯤 떠남
gagi(s_gm + 2500, 22)                # 떠나서 빨라짐
gagi(s_yd - 400, 22, True); seogi(45)       # 영등포 400미터 앞에서 신호 기다림(서지 않는 역)
gagi(s_yd + 300, 10)                 # 영등포를 천천히 지남
gagi(s_ys - 1500, 20)
gagi(s_ys + 300, 12)                 # 용산을 천천히 지남(서지 않는 역)
gagi(s_se, 18, True); seogi(120)            # 서울역 도착
pix = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'pyeonmul_s1.json'), encoding='utf-8'))
out = {
  "ireum": "2026-10-17 KTX 64 광명에서 서울(영등포·용산 지나침)",
  "mokjeok": {"ireum": "서울역 1호선", "lat": 37.5559, "lon": 126.9723},
  "nal": "20261017",
  "jari": fixes,
  "dulle": pix["dulle"], "teoneol": pix["teoneol"], "modu": pix["modu"], "pyeon": pix["pyeon"],
  "isseoya": ["곧 광명터널입니다", "타신 열차는 KTX 64호로 보입니다", "영등포역에 서지 않고", "용산역에 서지 않고", "서울역에 도착했습니다. 여기서 내리십시오"],
  "charye": ["타신 열차는 KTX 64호", "영등포역 가까이에서 잠시 멈췄습니다", "지금 영등포역을 지나고", "지금 용산역을 지나고", "곧 서울역에 도착합니다", "서울역에 도착했습니다"],
  "eopseoya": ["지금 광명역을 지나고", "영등포역에 도착했습니다", "곧 영등포역", "용산역에 도착했습니다", "곧 용산역",
               "영등포역[^.]*내리십", "용산역[^.]*내리십", "9.2킬로미터", "5.6킬로미터", "9킬로미터", "740미터", "730미터", "720미터", "32킬로미터"]
}
json.dump(out, open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 's1_gwangmyeong_seoul.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=0)
print(len(fixes), 'fixes', (fixes[-1]['t']-t0)//60, 'min')
