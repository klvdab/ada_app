// 아이폰 길눈 2.56.0 — 차 안 길 맞춤(대장클, 이사장님 허락 2026-10-07 22:45)
// 웹 길눈 chamat.js 1.0(빌드 261007-3)을 앱으로 옮김(이사장님 지시: 차가 제대로 가는지 알려 주고, 목적지 근처에서
// 「어떤 길, 어떤 건물에서 몇 시 방향으로 몇 미터」를 알려 드려 기사님께 말씀하실 수 있게). 안드로이드 ChaMat.kt 와 같은 잣대·같은 말.
//   안내 정도(말하기 설정 「차 안 안내 정도」): 1 간단 / 2 보통(처음 값) / 3 자세히.
//   길 벗어남 알림과 내리는 곳 안내는 간단에서도 나옴(이사장님 승인).
//   방향은 늘 차가 가는 쪽을 12시로 삼아 시계 방향으로 말함.
//   길(경로)은 협회 서버 gilmat.php(카카오 길찾기)에서 받음. 못 받으면 조용히 기존 남은 거리 안내만 나감.
//   승용차·택시(탈것 「차」)에서만 씀 — 버스·기차·고속버스는 세워 달라 할 수 없으므로 뺌.
import Foundation

final class ChaMat {
    static let shared = ChaMat()

    private struct Gil {
        let pts: [(Double, Double)]
        let gil: [String]
        let guides: [[String: Any]]
        let mun: [String: Any]?
        let mokGil: String
        let mokGeonmul: String
    }

    private var gil: Gil?
    private var mokLat = 0.0, mokLon = 0.0
    private var mokIreum = ""
    private var itda = false
    private var banEum = Date.distantPast
    private var bulTime = Date.distantPast
    private var bulSijak: Date?
    private var bulMal = Date.distantPast
    private var meolSijak: Date?
    private var meolGeori = 0.0
    private var meolMal = Date.distantPast
    private var seoSijak: Date?
    private var seoMal = Date.distantPast
    private var gdMal: [Int: String] = [:]
    private var hacha500 = false, hacha100 = false
    private var batneunJung = false
    private var chaBang = -1.0   // 차가 마지막으로 달리던 쪽(멈춰도 이 쪽을 12시로)

    static let ireum = ["", "간단", "보통", "자세히"]
    /// 안내 정도 1 간단 · 2 보통 · 3 자세히
    var jeongdo: Int {
        get { min(3, max(1, Seoljeong.shared.chaJeongdo)) }
        set { Seoljeong.shared.chaJeongdo = min(3, max(1, newValue)) }
    }

    func gatEun(_ lat: Double, _ lon: Double) -> Bool { itda && abs(mokLat - lat) < 1e-7 && abs(mokLon - lon) < 1e-7 }

    func sijak(_ lat: Double, _ lon: Double, _ ir: String) {
        mokLat = lat; mokLon = lon; mokIreum = ir; itda = true
        gil = nil; bulTime = .distantPast; bulMal = .distantPast; bulSijak = nil; hacha500 = false; hacha100 = false
        gdMal = [:]; meolSijak = nil; seoSijak = nil; batneunJung = false
    }

    func kkeut() { itda = false; gil = nil; chaBang = -1 }

    // MARK: 말 거리
    private func dist(_ a1: Double, _ o1: Double, _ a2: Double, _ o2: Double) -> Double { WichiEngine.geori(a1, o1, a2, o2) }
    private func bearing(_ a1: Double, _ o1: Double, _ a2: Double, _ o2: Double) -> Double { WichiEngine.bangwi(a1, o1, a2, o2) }
    private func sigye(_ head: Double, _ b: Double) -> String {
        if head < 0 { return "" }
        return "\(WichiEngine.sigyeBanghyang(jeongmyeon: head, mokpyo: b))시 방향"
    }
    private func mMal(_ m: Double) -> String {
        if m >= 1000 {
            let k = (m / 100).rounded() / 10
            return k == k.rounded() ? "\(Int(k))킬로미터" : "\(k)킬로미터"
        }
        if m >= 100 { return "\(Int((m / 50).rounded()) * 50)미터" }
        return "\(max(10, Int((m / 10).rounded()) * 10))미터"
    }
    /// 받침에 따라 조사 고르기
    private func bat(_ w: String) -> Int {
        guard let c = w.unicodeScalars.last else { return 0 }
        if c.value < 0xAC00 || c.value > 0xD7A3 { return CharacterSet.decimalDigits.contains(c) ? 1 : 0 }
        return Int((c.value - 0xAC00) % 28)
    }
    private func eun(_ w: String) -> String { w + (bat(w) != 0 ? "은" : "는") }
    private func eul(_ w: String) -> String { w + (bat(w) != 0 ? "을" : "를") }
    private func ro(_ w: String) -> String { let b = bat(w); return w + ((b != 0 && b != 8) ? "으로" : "로") }
    private func wichi(_ head: Double, _ la: Double, _ lo: Double, _ pa: Double, _ po: Double, _ m: Double) -> String {
        let b = sigye(head, bearing(la, lo, pa, po))
        return (b.isEmpty ? "" : b + " ") + mMal(m)
    }
    private func mal(_ x: String, _ kkok: Bool, _ malHagi: (String) -> Void) {
        let t = Date()
        if !kkok && t.timeIntervalSince(banEum) < 6 { return }
        banEum = t
        malHagi(x)
    }
    private var mokNm: String { mokIreum.isEmpty ? "목적지" : mokIreum }
    private func dbl(_ v: Any?) -> Double {
        if let d = v as? Double { return d }
        if let n = v as? NSNumber { return n.doubleValue }
        if let s = v as? String, let d = Double(s) { return d }
        return 0
    }
    private func str(_ v: Any?) -> String { (v as? String) ?? "" }

    // MARK: 길 위치
    /// 점에서 길(선분들)까지 가장 가까운 거리와 그 자리의 순번
    private func gilGeori(_ g: Gil, _ la: Double, _ lo: Double) -> (Double, Int) {
        let p = g.pts
        var best = 1e9, bi = 0
        if p.count < 2 { return (best, 0) }
        for i in 0..<(p.count - 1) {
            let ax = (p[i].1 - lo) * 88000, ay = (p[i].0 - la) * 111000
            let bx = (p[i + 1].1 - lo) * 88000, by = (p[i + 1].0 - la) * 111000
            let dx = bx - ax, dy = by - ay, l = dx * dx + dy * dy
            let tt = l > 0 ? max(0, min(1, -(ax * dx + ay * dy) / l)) : 0
            let cx = ax + tt * dx, cy = ay + tt * dy
            let d = (cx * cx + cy * cy).squareRoot()
            if d < best { best = d; bi = i }
        }
        return (best, bi)
    }
    /// 길을 따라 남은 거리
    private func namGil(_ g: Gil, _ i: Int, _ la: Double, _ lo: Double) -> Double {
        let p = g.pts
        var s = 0.0
        if i + 1 < p.count { s += dist(la, lo, p[i + 1].0, p[i + 1].1) }
        var k = i + 1
        while k + 1 < p.count { s += dist(p[k].0, p[k].1, p[k + 1].0, p[k + 1].1); k += 1 }
        return s
    }

    private func gilBatgi(_ la: Double, _ lo: Double, saero: Bool = false) {
        guard itda, !batneunJung else { return }
        let t = Date()
        if t.timeIntervalSince(bulTime) < 45 && !saero { return }
        bulTime = t
        batneunJung = true
        let q = ["la1": String(format: "%.5f", la), "lo1": String(format: "%.5f", lo),
                 "la2": String(format: "%.5f", mokLat), "lo2": String(format: "%.5f", mokLon)]
        let m0 = (mokLat, mokLon)
        Task {
            var o: [String: Any]?
            if let d = try? await Tongsin.shared.get("gilmat.php", q), !d.badadunGeot {
                o = try? JSONSerialization.jsonObject(with: d.data) as? [String: Any]
            }
            await MainActor.run {
                self.batneunJung = false
                guard let o = o, (o["ok"] as? Bool) == true, self.itda, m0 == (self.mokLat, self.mokLon),
                      let pa = o["pts"] as? [[Any]] else { return }
                let pts = pa.compactMap { a -> (Double, Double)? in a.count >= 2 ? (self.dbl(a[0]), self.dbl(a[1])) : nil }
                if pts.count < 2 { return }
                self.gil = Gil(pts: pts, gil: (o["gil"] as? [Any])?.map { self.str($0) } ?? [],
                               guides: (o["guides"] as? [[String: Any]]) ?? [], mun: o["mun"] as? [String: Any],
                               mokGil: self.str(o["mokGil"]), mokGeonmul: self.str(o["mokGeonmul"]))
                self.gdMal = [:]
                Girok.shared.namgi("chamat_gil", ["m": Int(self.dbl(o["meter"]))])
            }
        }
    }

    /// 꺾는 곳에서 몇 시 방향으로 꺾는지 — 길 모양(들어오는 쪽과 나가는 쪽)으로 셈
    private func kkeokBang(_ g: Gil, _ ga: Double, _ go: Double) -> String {
        let p = g.pts
        var bi = 0, bd = 1e9
        for (i, q) in p.enumerated() { let d = dist(ga, go, q.0, q.1); if d < bd { bd = d; bi = i } }
        var a: (Double, Double)?, b: (Double, Double)?
        var j = bi - 1
        while j >= 0 { if dist(p[j].0, p[j].1, ga, go) >= 25 { a = p[j]; break }; j -= 1 }
        j = bi + 1
        while j < p.count { if dist(p[j].0, p[j].1, ga, go) >= 25 { b = p[j]; break }; j += 1 }
        guard let a = a, let b = b else { return "" }
        return sigye(bearing(a.0, a.1, ga, go), bearing(ga, go, b.0, b.1))
    }

    private func munJari(_ g: Gil) -> (Double, Double) {
        if let m = g.mun, m["lat"] != nil, m["lon"] != nil { return (dbl(m["lat"]), dbl(m["lon"])) }
        return (mokLat, mokLon)
    }

    /// 목적지 근처 내리는 곳 안내 한 덩어리
    private func hachaMal(_ g: Gil, _ la: Double, _ lo: Double, _ head: Double, _ gi: Int) -> String {
        let jigeumGil = gi < g.gil.count ? g.gil[gi] : ""
        let namM = namGil(g, gi, la, lo)
        let mp = munJari(g)
        let bang = sigye(head, bearing(la, lo, mp.0, mp.1))
        var s: [String] = []
        if !jigeumGil.isEmpty { s.append("지금 \(eul(jigeumGil)) 달리고 있습니다.") }
        let nm = mokNm
        let gilBit = (!g.mokGil.isEmpty && g.mokGil != jigeumGil) ? " \(ro(g.mokGil)) 들어가" : " 이 길을 따라"
        s.append("\(eun(nm))\(gilBit) 약 \(mMal(namM)) 가면 있습니다.")
        if !bang.isEmpty { s.append("지금 자리에서 보면 \(bang)입니다.") }
        // 마지막 꺾는 곳이 아직 앞에 있으면 함께 말함
        let k = g.guides.count - 1
        if k >= 0 && gdMal[k] == nil {
            let gd = g.guides[k]
            let ga = dbl(gd["lat"]), go = dbl(gd["lon"])
            let dg = dist(la, lo, ga, go)
            let bb = kkeokBang(g, ga, go)
            if dg < namM && !bb.isEmpty && bb != "12시 방향" {
                gdMal[k] = "h"
                let gm = str(gd["geonmul"])
                s.append("약 \(mMal(dg)) 앞 " + (gm.isEmpty ? "" : "\(gm) 앞 ") + "꺾는 곳에서 \(ro(bb)) 꺾어야 합니다.")
            }
        }
        if !g.mokGeonmul.isEmpty && !g.mokGeonmul.contains(nm) && !nm.contains(g.mokGeonmul) { s.append("건물 이름은 \(g.mokGeonmul)입니다.") }
        let munGil = str(g.mun?["gil"])
        if !munGil.isEmpty { s.append("문은 \(munGil) 쪽에 있습니다.") }
        else if !g.mokGil.isEmpty { s.append("건물은 \(g.mokGil)에 붙어 있습니다.") }
        return s.joined(separator: " ")
    }

    /// 차 안 안내가 위치를 받을 때마다 부름(AnnaeEngine.chaAnnae). 말했으면 참
    func salpim(_ w: Wichi, _ malHagi: (String) -> Void) -> Bool {
        guard itda else { return false }
        let la = w.lat, lo = w.lon, sokM = w.sokdo
        if w.banghyang >= 0 && sokM > 1.5 { chaBang = w.banghyang }
        let head = chaBang
        let lv = jeongdo
        let t = Date()
        let dMok = dist(la, lo, mokLat, mokLon)
        let g = gil
        if g == nil { gilBatgi(la, lo) }

        if let g = g {
            let (gd0, gi) = gilGeori(g, la, lo)
            let namM = namGil(g, gi, la, lo)
            // 내리는 곳 안내 — 모든 단계
            if !hacha500 && namM <= 600 && dMok > 120 { hacha500 = true; mal(hachaMal(g, la, lo, head, gi), true, malHagi); return true }
            if !hacha100 && (namM <= 100 || dMok <= 80) {
                hacha100 = true
                let mp = munJari(g)
                let bang = sigye(head, bearing(la, lo, mp.0, mp.1))
                mal("곧 " + (bang.isEmpty ? "" : "\(bang)에 ") + mokNm + "입니다. 여기서 세워 달라고 하십시오.", true, malHagi)
                return true
            }
            // 길 벗어남 — 모든 단계. 100미터 넘게 15초
            if gd0 > 100 && sokM > 2 {
                if bulSijak == nil { bulSijak = t }
                if let b = bulSijak, t.timeIntervalSince(b) > 15, t.timeIntervalSince(bulMal) > 60 {
                    bulMal = t
                    bulSijak = nil
                    mal("길에서 벗어났습니다. \(eun(mokNm)) 지금 \(wichi(head, la, lo, mokLat, mokLon, dMok))입니다.", true, malHagi)
                    gilBatgi(la, lo, saero: true)
                    return true
                }
            } else { bulSijak = nil }
            // 꺾을 곳 미리 알림 — 보통은 마지막 꺾는 곳만, 자세히는 꺾는 곳마다
            let gds = g.guides
            for k in gds.indices {
                if lv < 2 { break }
                let majimak = (k == gds.count - 1)
                if lv == 2 && !majimak { continue }
                let ga = dbl(gds[k]["lat"]), go = dbl(gds[k]["lon"])
                let dg = dist(la, lo, ga, go)
                let ap = max(120, min(300, (sokM > 0 ? sokM : 10) * 12))
                let st = gdMal[k]
                if dg < ap && dg > 25 && (st == nil || st == "h") && t.timeIntervalSince(banEum) >= 6 {
                    let bb = kkeokBang(g, ga, go)
                    gdMal[k] = "1"
                    if !bb.isEmpty && bb != "12시 방향" {
                        let gm = str(gds[k]["geonmul"]), ir = str(gds[k]["ireum"])
                        let ap2 = !gm.isEmpty ? "\(gm) 앞 " : (!ir.isEmpty ? "\(ir) 쪽 " : "")
                        mal("다음 \(ap2)꺾는 곳에서 \(ro(bb)) 꺾어야 " + (majimak ? "목적지 쪽입니다." : "길대로 갑니다."), false, malHagi)
                        return true
                    }
                }
                if lv == 3 && dg <= 25 && gdMal[k] == "1" && gd0 < 40 { gdMal[k] = "2"; mal("길대로 가고 있습니다.", false, malHagi); return true }
            }
        }

        // 멀어짐 — 보통 이상. 30초 넘게 거리가 200미터 넘게 늘면
        if lv >= 2 {
            if meolSijak == nil || dMok < meolGeori { meolSijak = t; meolGeori = dMok }
            else if let m = meolSijak, t.timeIntervalSince(m) > 30, dMok - meolGeori > 200, t.timeIntervalSince(meolMal) > 90 {
                meolMal = t; meolSijak = t; meolGeori = dMok
                mal("목적지에서 멀어지고 있습니다. \(eun(mokNm)) 지금 \(wichi(head, la, lo, mokLat, mokLon, dMok))입니다.", true, malHagi)
                return true
            }
        }

        // 오래 서 있음 — 자세히만. 꺾는 곳 40미터 안(신호 대기)은 빼고 1분
        if lv == 3 {
            if sokM < 1 {
                if seoSijak == nil { seoSijak = t }
                let sagori = g?.guides.contains { dist(la, lo, dbl($0["lat"]), dbl($0["lon"])) < 40 } ?? false
                if let ss = seoSijak, !sagori, t.timeIntervalSince(ss) > 60, t.timeIntervalSince(seoMal) > 120 {
                    seoMal = t
                    mal("\(Int((t.timeIntervalSince(ss) / 60).rounded()))분째 서 있습니다. \(eun(mokNm)) \(wichi(head, la, lo, mokLat, mokLon, dMok))입니다.", false, malHagi)
                    return true
                }
            } else { seoSijak = nil }
        }
        return false
    }
}
