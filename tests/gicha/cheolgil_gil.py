# 철길 자료(cheolgil.bin)에서 두 자리 사이 철길을 따라가는 길을 찾음 — 시험 시나리오를 실제 선로 위에 놓으려고 씀
import struct, math, heapq
def ilgi(p):
    b = open(p, 'rb').read()
    assert b[:4] == b'CHG1'
    nj, nl = struct.unpack_from('<II', b, 4); o = 12
    xy = [None]*nj
    for i in range(nj):
        a, c = struct.unpack_from('<ii', b, o); o += 8; xy[i] = (a/1e6, c/1e6)
    jul = []
    for _ in range(nl):
        pyo, nb = struct.unpack_from('<BB', b, o); o += 2
        nm = b[o:o+nb].decode('utf-8'); o += nb
        n, = struct.unpack_from('<H', b, o); o += 2
        js = list(struct.unpack_from('<%dI' % n, b, o)); o += 4*n
        jul.append((pyo, nm, js))
    return xy, jul
def geori(a, b):
    c = math.cos(math.radians((a[0]+b[0])/2))
    return math.hypot((b[1]-a[1])*111320*c, (b[0]-a[0])*110540)
def gil(xy, jul, a, b, pi=lambda pyo: 1.0):
    ip = {}
    for pyo, nm, js in jul:
        w = pi(pyo)
        if w is None: continue
        for i in range(len(js)-1):
            d = geori(xy[js[i]], xy[js[i+1]])*w
            ip.setdefault(js[i], []).append((js[i+1], d)); ip.setdefault(js[i+1], []).append((js[i], d))
    s = min(ip, key=lambda k: geori(xy[k], a)); e = min(ip, key=lambda k: geori(xy[k], b))
    dist = {s: 0}; jeon = {}; q = [(0, s)]
    while q:
        d, u = heapq.heappop(q)
        if u == e: break
        if d > dist[u]: continue
        for v, w in ip[u]:
            if d + w < dist.get(v, 1e18): dist[v] = d + w; jeon[v] = u; heapq.heappush(q, (d+w, v))
    p = [e]
    while p[-1] != s: p.append(jeon[p[-1]])
    return [xy[k] for k in reversed(p)]
