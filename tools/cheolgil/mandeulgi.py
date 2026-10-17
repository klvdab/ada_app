#!/usr/bin/env python3
# 철길 자료 만들기 0.1 (빌드 261017-1, 이사장님 승인 2026-10-17 「1」 — 선로 맞추기)
# 오픈스트리트맵에서 우리나라 기찻길(railway=rail)을 모두 받아, 길눈 앱 안에 싣는 작은 철길 자료(cheolgil.bin)로 만듦.
# 앱은 이 자료로 기차 위치를 실제 철길 선 위에 붙이고(선로 맞추기), 타신 선로 앞쪽의 터널만 미리 알림.
# 앱 안에 싣는 까닭: 터널·산골에서는 인터넷이 끊기므로, 달리는 중에 바깥에서 받지 않음.
#
# 자료 모양(모든 수는 리틀 엔디언)
#   "CHG1"  u32 점 수  u32 줄 수
#   점마다  i32 위도×1e6  i32 경도×1e6
#   줄마다  u8 표시(1 터널, 2 다리, 4 곁가지 선로(service), 8 고속선)  u8 이름 바이트 수  이름(UTF-8, 터널 이름만)
#           u16 점 수  점 번호 u32 …
import json, struct, sys, math, time, urllib.request, urllib.parse

Q = '[out:json][timeout:600];way["railway"="rail"](33.0,124.5,38.7,131.0);out geom;'
SEO = ["https://overpass-api.de/api/interpreter",
       "https://overpass.kumi.systems/api/interpreter",
       "https://maps.mail.ru/osm/tools/overpass/api/interpreter"]

def batgi():
    for u in SEO:
        for _ in range(2):
            try:
                r = urllib.request.Request(u, data=urllib.parse.urlencode({"data": Q}).encode(),
                                           headers={"User-Agent": "gilnun-cheolgil/0.1 (klvdab)"})
                d = urllib.request.urlopen(r, timeout=700).read()
                j = json.loads(d)
                if j.get("elements"):
                    print("받음", u, len(d), "바이트", len(j["elements"]), "줄")
                    return j
            except Exception as e:
                print("실패", u, e); time.sleep(20)
    sys.exit("오픈스트리트맵에서 받지 못함")

def geori(a, b):
    la = math.radians((a[0] + b[0]) / 2)
    dx = (b[1] - a[1]) * 111320 * math.cos(la); dy = (b[0] - a[0]) * 110540
    return math.hypot(dx, dy)

def seon_geori(p, a, b):
    la = math.radians(p[0])
    ax, ay = (a[1] - p[1]) * 111320 * math.cos(la), (a[0] - p[0]) * 110540
    bx, by = (b[1] - p[1]) * 111320 * math.cos(la), (b[0] - p[0]) * 110540
    dx, dy = bx - ax, by - ay
    L = dx * dx + dy * dy
    t = 0 if L == 0 else max(0, min(1, -(ax * dx + ay * dy) / L))
    return math.hypot(ax + t * dx, ay + t * dy)

def dp(pts, keep, tol):
    # 더글러스-포이커, keep 자리는 반드시 남김
    n = len(pts); out = [False] * n
    out[0] = out[-1] = True
    for i in range(n):
        if keep[i]: out[i] = True
    idx = [i for i in range(n) if out[i]]
    st = [(idx[k], idx[k + 1]) for k in range(len(idx) - 1)]
    while st:
        s, e = st.pop()
        if e - s < 2: continue
        best, bi = -1, -1
        for i in range(s + 1, e):
            d = seon_geori(pts[i], pts[s], pts[e])
            if d > best: best, bi = d, i
        if best > tol:
            out[bi] = True; st.append((s, bi)); st.append((bi, e))
    return [i for i in range(n) if out[i]]

def main():
    nae = sys.argv[1] if len(sys.argv) > 1 else "cheolgil.bin"
    j = batgi()
    ways = [e for e in j["elements"] if e.get("type") == "way" and e.get("geometry") and e.get("nodes")]
    sseum = {}
    for w in ways:
        for k, nd in enumerate(w["nodes"]):
            sseum[nd] = sseum.get(nd, 0) + (2 if k in (0, len(w["nodes"]) - 1) else 1)
    jeom = {}; jeomMok = []
    jul = []
    tsu = 0
    for w in ways:
        t = w.get("tags", {})
        pts = [(g["lat"], g["lon"]) for g in w["geometry"]]
        nds = w["nodes"]
        if len(pts) != len(nds) or len(pts) < 2: continue
        keep = [sseum.get(nd, 0) > 1 for nd in nds]
        ss = dp(pts, keep, 4.0)
        pyo = 0
        if t.get("tunnel") in ("yes", "building_passage", "culvert"): pyo |= 1
        if t.get("bridge") not in (None, "no"): pyo |= 2
        if "service" in t: pyo |= 4
        if t.get("highspeed") == "yes": pyo |= 8
        ireum = ""
        if pyo & 1:
            tsu += 1
            ireum = t.get("tunnel:name:ko") or t.get("tunnel:name") or ""
            if not ireum:
                nm = t.get("name:ko") or t.get("name") or ""
                if nm.endswith("터널") or nm.endswith("굴"): ireum = nm
        bun = []
        for i in ss:
            nd = nds[i]
            if nd not in jeom:
                jeom[nd] = len(jeomMok); jeomMok.append(pts[i])
            bun.append(jeom[nd])
        if len(bun) > 65535: continue
        jul.append((pyo, ireum.encode("utf-8")[:255], bun))
    b = bytearray(b"CHG1")
    b += struct.pack("<II", len(jeomMok), len(jul))
    for la, lo in jeomMok:
        b += struct.pack("<ii", round(la * 1e6), round(lo * 1e6))
    for pyo, nm, bun in jul:
        b += struct.pack("<BB", pyo, len(nm)) + nm + struct.pack("<H", len(bun))
        b += struct.pack("<%dI" % len(bun), *bun)
    open(nae, "wb").write(b)
    print("점", len(jeomMok), "줄", len(jul), "터널", tsu, "크기", len(b))
    # 살펴보기 — 서울역 둘레 3킬로미터 터널
    for w in ways:
        t = w.get("tags", {})
        if t.get("tunnel") != "yes": continue
        g = w["geometry"]
        if geori((g[0]["lat"], g[0]["lon"]), (37.5547, 126.9707)) < 3000:
            L = sum(geori((g[i]["lat"], g[i]["lon"]), (g[i+1]["lat"], g[i+1]["lon"])) for i in range(len(g) - 1))
            print("서울역 둘레 터널", w["id"], round(L), t.get("name"), t.get("tunnel:name"), t.get("service"), t.get("usage"))

main()
