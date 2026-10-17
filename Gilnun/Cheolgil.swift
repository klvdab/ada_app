// 철길 자료와 선로 맞추기 — 2.99.0 (261018-I53, 이사장님 승인 2026-10-17 「1」)
// 기차 위치를 지도 위의 점으로만 보다가, 바로 옆에 나란히 놓인 다른 노선의 터널(서울역 앞 740미터)을
// 타신 기차의 터널로 알리던 일을 뿌리부터 막음.
//   앱 안에 실은 우리나라 철길 자료(cheolgil.bin, 오픈스트리트맵, tools/cheolgil/mandeulgi.py)로
//   ① 위치를 가장 그럴듯한 철길 선 위에 붙이고(선로 맞추기 — 거리·가는 쪽·앞서 맞춘 선로와 이어지는지)
//   ② 그 선로를 가는 쪽으로 따라가며(갈림길에서는 가장 덜 꺾이는 쪽) 앞에 놓인 터널만 찾음.
// 터널·산골에서는 인터넷이 끊기므로 자료는 앱 안에 둠. 안드로이드 Cheolgil.kt 와 같은 셈법.
import Foundation

final class Cheolgil {
    /// 자료 읽기 — 시험(tests/gicha)에서 파일을 넣으려고 바꿀 수 있게 함
    static var ilgeum: () -> Data? = {
        guard let u = Bundle.main.url(forResource: "cheolgil", withExtension: "bin") else { return nil }
        return try? Data(contentsOf: u)
    }
    private static var bon: Cheolgil?
    private static var ilgeotna = false
    /// 처음 부를 때 한 번 읽음(자료가 없으면 nil — 그때는 예전 방식으로 안내)
    static var shared: Cheolgil? {
        if !ilgeotna { ilgeotna = true; if let d = ilgeum() { bon = Cheolgil(d) } }
        return bon
    }

    struct Jul { let pyo: UInt8; let ireum: String; let jeom: [Int32] }
    struct Matchum { let jul: Int; let i: Int; let t: Double; let bang: Int; let geori: Double; let lat: Double; let lon: Double }
    struct Teoneol { let sijak: Double; let gil: Double; let ireum: String; let jul: [Int] }

    private(set) var la: [Double] = []
    private(set) var lo: [Double] = []
    private(set) var jul: [Jul] = []
    private var ieum: [Int32: [(Int, Int)]] = [:]     // 두 줄 넘게 쓰는 점 → (줄, 자리)
    private var kan: [Int64: [Int64]] = [:]           // 칸 → (줄<<20 | 토막)
    private static let kanLa = 0.005, kanLo = 0.006

    init?(_ d: Data) {
        let b = [UInt8](d)
        guard b.count > 12, b[0] == 0x43, b[1] == 0x48, b[2] == 0x47, b[3] == 0x31 else { return nil }
        var p = 4
        func u8() -> Int { let v = Int(b[p]); p += 1; return v }
        func u16() -> Int { let v = Int(b[p]) | Int(b[p + 1]) << 8; p += 2; return v }
        func u32() -> UInt32 { let v = UInt32(b[p]) | UInt32(b[p + 1]) << 8 | UInt32(b[p + 2]) << 16 | UInt32(b[p + 3]) << 24; p += 4; return v }
        let nj = Int(u32()), nl = Int(u32())
        guard b.count >= 12 + nj * 8 else { return nil }
        la.reserveCapacity(nj); lo.reserveCapacity(nj)
        for _ in 0..<nj {
            la.append(Double(Int32(bitPattern: u32())) / 1e6)
            lo.append(Double(Int32(bitPattern: u32())) / 1e6)
        }
        var sseum = [UInt8](repeating: 0, count: nj)
        jul.reserveCapacity(nl)
        for _ in 0..<nl {
            guard p + 2 <= b.count else { return nil }
            let pyo = UInt8(u8()); let nb = u8()
            guard p + nb + 2 <= b.count else { return nil }
            let ir = String(decoding: b[p..<(p + nb)], as: UTF8.self); p += nb
            let n = u16()
            guard p + n * 4 <= b.count else { return nil }
            var js: [Int32] = []; js.reserveCapacity(n)
            for _ in 0..<n { let v = Int32(bitPattern: u32()); if Int(v) < nj { js.append(v) } }
            if js.count < 2 { continue }
            for v in js where sseum[Int(v)] < 2 { sseum[Int(v)] += 1 }
            jul.append(Jul(pyo: pyo, ireum: ir, jeom: js))
        }
        for (ji, j) in jul.enumerated() {
            for (k, v) in j.jeom.enumerated() where sseum[Int(v)] > 1 || k == 0 || k == j.jeom.count - 1 {
                ieum[v, default: []].append((ji, k))
            }
            for i in 0..<(j.jeom.count - 1) {
                let a = Int(j.jeom[i]), c = Int(j.jeom[i + 1])
                let L = Cheolgil.m(la[a], lo[a], la[c], lo[c])
                let n = max(1, Int(L / 250))
                var deun: Set<Int64> = []
                for s in 0...n {
                    let f = Double(s) / Double(n)
                    deun.insert(Cheolgil.kanKi(la[a] + (la[c] - la[a]) * f, lo[a] + (lo[c] - lo[a]) * f))
                }
                for k in deun { kan[k, default: []].append(Int64(ji) << 20 | Int64(i)) }
            }
        }
    }

    private static func kanKi(_ a: Double, _ o: Double) -> Int64 {
        Int64(floor(a / kanLa)) << 32 | (Int64(floor(o / kanLo)) & 0xffffffff)
    }

    static func m(_ a1: Double, _ o1: Double, _ a2: Double, _ o2: Double) -> Double {
        let c = cos((a1 + a2) / 2 * .pi / 180)
        return hypot((o2 - o1) * 111320 * c, (a2 - a1) * 110540)
    }
    static func bangwi(_ a1: Double, _ o1: Double, _ a2: Double, _ o2: Double) -> Double {
        let c = cos((a1 + a2) / 2 * .pi / 180)
        let d = atan2((o2 - o1) * c, a2 - a1) * 180 / .pi
        return d < 0 ? d + 360 : d
    }
    static func gakCha(_ a: Double, _ b: Double) -> Double { abs((a - b + 540).truncatingRemainder(dividingBy: 360) - 180) }

    /// 선로 맞추기 — bang: 가는 쪽(모르면 nil), ochae: 위치 오차(미터), ireo: 앞서 맞춘 줄과 그 앞으로 이어질 줄들
    func matchugi(_ a: Double, _ o: Double, bang: Double?, ochae: Double, ireo: Set<Int>) -> Matchum? {
        let r = max(35, min(ochae, 60) + 25)
        let c = cos(a * .pi / 180)
        let k0a = Int64(floor(a / Cheolgil.kanLa)), k0o = Int64(floor(o / Cheolgil.kanLo))
        var bon: Set<Int64> = []
        var best: (Matchum, Double)?
        for da in -1...1 { for dob in -1...1 {
            guard let l = kan[(k0a + Int64(da)) << 32 | ((k0o + Int64(dob)) & 0xffffffff)] else { continue }
            for s in l where !bon.contains(s) {
                bon.insert(s)
                let ji = Int(s >> 20), i = Int(s & 0xfffff)
                let j = jul[ji]
                let p1 = Int(j.jeom[i]), p2 = Int(j.jeom[i + 1])
                let ax = (lo[p1] - o) * 111320 * c, ay = (la[p1] - a) * 110540
                let bx = (lo[p2] - o) * 111320 * c, by = (la[p2] - a) * 110540
                let dx = bx - ax, dy = by - ay, L2 = dx * dx + dy * dy
                let t = L2 == 0 ? 0 : max(0, min(1, -(ax * dx + ay * dy) / L2))
                let g = hypot(ax + t * dx, ay + t * dy)
                if g > r { continue }
                var bg = 1, gak = 0.0
                if let h = bang {
                    let sb = Cheolgil.bangwi(la[p1], lo[p1], la[p2], lo[p2])
                    let dd = Cheolgil.gakCha(h, sb)
                    bg = dd <= 90 ? 1 : -1
                    gak = min(dd, 180 - dd)
                    if gak > 40 { continue }
                }
                var jeom = g + gak * 1.5
                if j.pyo & 4 != 0 { jeom += 15 }
                if ireo.contains(ji) { jeom -= 20 }
                let mt = Matchum(jul: ji, i: i, t: t, bang: bg, geori: g,
                                 lat: la[p1] + (la[p2] - la[p1]) * t, lon: lo[p1] + (lo[p2] - lo[p1]) * t)
                if best == nil || jeom < best!.1 { best = (mt, jeom) }
            }
        } }
        return best?.0
    }

    /// 맞춘 자리에서 가는 쪽으로 철길을 따라감 — 앞에 놓인 터널과 지나갈 줄들
    func ttaragagi(_ m: Matchum, gil: Double) -> (teoneol: [Teoneol], jul: Set<Int>) {
        var tn: [Teoneol] = []
        var jeon: (Double, Double, String, [Int])?    // 지금 이어 재는 터널(시작, 끝, 이름, 줄)
        var gan: Set<Int> = [m.jul]
        var ji = m.jul, d = m.bang
        var k = d > 0 ? m.i + 1 : m.i              // 다음에 닿을 점 자리
        var pa = m.lat, po = m.lon
        var s = 0.0
        for _ in 0..<400 {
            let j = jul[ji]
            let v = Int(j.jeom[k])
            let L = Cheolgil.m(pa, po, la[v], lo[v])
            let h0 = Cheolgil.bangwi(pa, po, la[v], lo[v])
            if j.pyo & 1 != 0 {
                if var t = jeon, s - t.1 < 40 { t.1 = s + L; if !t.3.contains(ji) { t.3.append(ji) }; if t.2.isEmpty { t.2 = j.ireum }; jeon = t }
                else { if let t = jeon { tn.append(Teoneol(sijak: t.0, gil: t.1 - t.0, ireum: t.2, jul: t.3)) }; jeon = (s, s + L, j.ireum, [ji]) }
            }
            s += L
            pa = la[v]; po = lo[v]
            if s >= gil, jeon == nil || s - jeon!.1 > 40 { break }
            if s > gil + 30000 { break }
            // 다음 길 고르기 — 이 점에서 나가는 모든 길 가운데 가장 덜 꺾이는 쪽(35도 안)
            var nae: [(Int, Int, Int)] = []        // (줄, 다음 점 자리, 방향)
            let kk = k + d
            if kk >= 0, kk < j.jeom.count { nae.append((ji, kk, d)) }
            if let gt = ieum[Int32(v)] {
                for (oj, ok) in gt where !(oj == ji && ok == k) {
                    let oj2 = jul[oj]
                    if ok + 1 < oj2.jeom.count { nae.append((oj, ok + 1, 1)) }
                    if ok - 1 >= 0 { nae.append((oj, ok - 1, -1)) }
                }
            }
            var gol: (Int, Int, Int, Double)?
            for (oj, ok, od) in nae {
                let w = Int(jul[oj].jeom[ok])
                let gk = Cheolgil.gakCha(h0, Cheolgil.bangwi(la[v], lo[v], la[w], lo[w]))
                if gk > 35 { continue }
                if gol == nil || gk < gol!.3 { gol = (oj, ok, od, gk) }
            }
            guard let g = gol else { break }
            gan.insert(g.0)
            ji = g.0; k = g.1; d = g.2
        }
        if let t = jeon { tn.append(Teoneol(sijak: t.0, gil: t.1 - t.0, ireum: t.2, jul: t.3)) }
        return (tn, gan)
    }
}
