// 지도 알림 — 2.123.0 (261101-I77, 이사장님 지시 2026-11-01 「할 수 있는거라면 다 넣고 싶어 … 알아서 해」)
// 오픈스트리트맵(협회 서버 jido.php, 전국을 나스에 미리 받아 둠)에서 차·버스·기차·지하철 안에서 쓸모 있는 것을 알려 드림.
//   ① 다리 — 들어가기 전에 「곧 한강대교입니다. 한강을 건넙니다. 길이는 약 1.1킬로미터입니다」, 다 건너면 「다리를 건넜습니다」.
//      찻길에서는 찻길 다리만, 기차·지하철에서는 기찻길 다리만. 이름도 물길도 없는 짧은 다리(고가 밑 작은 다리 따위)는 말하지 않음.
//      터널(ChaGeodeul)처럼 그 다리 길 위를 같은 방향으로 갈 때만 알림 — 옆길·다리 밑을 지날 때 잘못 말하지 않게.
//   ② 고속도로 곁 — 휴게소·졸음쉼터·나들목·분기점·요금소를 앞에 닿기 전에 한 번. 빠르게 달릴 때(초속 14미터, 시속 50킬로미터 넘게)만.
//   ③ 고장 경계 — 시·군·구가 바뀌면 「경기도 여주시에 들어섰습니다」 한 번.
// 설정 탭 말하기 설정의 「지도 알림」으로 끄고 켬(처음 켜짐). 안드로이드 JidoAllim.kt 와 같은 셈법.
import Foundation

final class JidoAllim {
    static let shared = JidoAllim()

    private struct Dari { let ireum: String; let gil: String; let cheol: Bool; let m: Double; let mul: String; let goga: Bool; let pt: [(Double, Double)] }
    private struct Sim { let t: String; let n: String; let la: Double; let lo: Double }

    private(set) var kyeojim = false
    private var dari: [Dari] = []
    private var sim: [Sim] = []
    private var jari: (Double, Double)?
    private var batneun = false
    private var batTtae = Date.distantPast
    private var han: [String: Date] = [:]          // 이미 알린 것(다시 말하지 않음)
    private var deun: (key: String, d: Dari, ttae: Date)?
    private var head: Double?
    private var headFrom: (Double, Double)?
    private var gogang = ""
    private var gogangTtae = Date.distantPast
    private var gogangMutneun = false

    func kkeut() {
        guard kyeojim else { return }
        kyeojim = false
        dari = []; sim = []; jari = nil; han = [:]; deun = nil; head = nil; headFrom = nil
        gogang = ""; gogangTtae = .distantPast
    }

    /// AnnaeEngine.chaAnnae 가 자리마다 부름. cheol = 기차·지하철
    func wichi(_ w: Wichi, cheol: Bool) {
        guard Seoljeong.shared.jidoOn, !w.jjimjak else { return }
        kyeojim = true
        bangBoda(w)
        if (dari.isEmpty && sim.isEmpty) || jari == nil || geori(jari!.0, jari!.1, w.lat, w.lon) > 1500 { batgi(w.lat, w.lon) }
        dariSalpigi(w, cheol: cheol)
        if !cheol { simSalpigi(w) }
        gogangSalpigi(w)
    }

    private func mal(_ t: String) {
        SoriEngine.shared.mal(t, .annae)
        Girok.shared.namgi("jido_mal", ["mal": t])
    }
    private func geori(_ a1: Double, _ o1: Double, _ a2: Double, _ o2: Double) -> Double { WichiEngine.geori(a1, o1, a2, o2) }
    private func gakCha(_ a: Double, _ b: Double) -> Double { let d = abs(a - b).truncatingRemainder(dividingBy: 360); return d > 180 ? 360 - d : d }

    /// 진행 방향 — 폰이 재 준 것이 있으면 그것, 없으면 20미터 넘게 옮긴 두 자리로
    private func bangBoda(_ w: Wichi) {
        if w.jinhaeng >= 0 && w.sokdo >= 3 { head = w.jinhaeng; headFrom = (w.lat, w.lon); return }
        guard let f = headFrom else { headFrom = (w.lat, w.lon); return }
        if geori(f.0, f.1, w.lat, w.lon) >= 20 { head = WichiEngine.bangwi(f.0, f.1, w.lat, w.lon); headFrom = (w.lat, w.lon) }
    }

    private func batgi(_ la: Double, _ lo: Double) {
        guard !batneun, Date().timeIntervalSince(batTtae) >= 20 else { return }
        batneun = true; batTtae = Date()
        let q = ["gal": "cha", "lat": String(format: "%.5f", la), "lon": String(format: "%.5f", lo), "ban": "3000"]
        Task { [weak self] in
            let d = try? await Tongsin.shared.getSae("jido.php", q)
            await MainActor.run {
                guard let self = self else { return }
                self.batneun = false
                guard let d = d, let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any] else { return }
                let rows = (o["dari"] as? [[String: Any]]) ?? []
                self.dari = rows.compactMap { r in
                    let pt: [(Double, Double)] = ((r["p"] as? [Any]) ?? []).compactMap { p in
                        guard let a = p as? [Any], a.count >= 2, let x = Chatgi.su(a[0]), let y = Chatgi.su(a[1]) else { return nil }
                        return (x, y)
                    }
                    guard pt.count >= 2 else { return nil }
                    return Dari(ireum: (r["n"] as? String) ?? "", gil: (r["g"] as? String) ?? "", cheol: (r["k"] as? String) == "cheol",
                                m: max(Chatgi.su(r["m"]) ?? 0, Chatgi.su(r["mm"]) ?? 0), mul: (r["mul"] as? String) ?? "", goga: Chatgi.su(r["v"]) == 1, pt: pt)
                }
                self.sim = ((o["sim"] as? [[String: Any]]) ?? []).compactMap { s in
                    guard let la = Chatgi.su(s["a"]), let lo = Chatgi.su(s["o"]) else { return nil }
                    return Sim(t: (s["t"] as? String) ?? "", n: (s["n"] as? String) ?? "", la: la, lo: lo)
                }
                self.jari = (la, lo)
            }
        }
    }

    // MARK: ① 다리

    private func tomak(_ la: Double, _ lo: Double, _ p1: (Double, Double), _ p2: (Double, Double)) -> Double {
        let K = 111320.0, co = cos(la * .pi / 180)
        let x = (lo - p1.1) * K * co, y = (la - p1.0) * K
        let bx = (p2.1 - p1.1) * K * co, by = (p2.0 - p1.0) * K
        let nn = bx * bx + by * by
        let t = nn > 0 ? max(0, min(1, (x * bx + y * by) / nn)) : 0
        let dx = x - bx * t, dy = y - by * t
        return (dx * dx + dy * dy).squareRoot()
    }
    private func julBang(_ la: Double, _ lo: Double, _ pt: [(Double, Double)]) -> (Double, Double) {
        var m = 1e9, b = 0.0
        for i in 1..<pt.count {
            let g = tomak(la, lo, pt[i - 1], pt[i])
            if g < m { m = g; b = WichiEngine.bangwi(pt[i - 1].0, pt[i - 1].1, pt[i].0, pt[i].1) }
        }
        return (m, b)
    }
    static func giriMal(_ m: Double) -> String {
        if m >= 1000 { let k = (m / 100).rounded() / 10; return "길이는 약 \(k == k.rounded() ? String(Int(k)) : String(format: "%.1f", k))킬로미터입니다." }
        return "길이는 약 \(max(10, Int((m / 10).rounded()) * 10))미터입니다."
    }
    /// 받침이 있으면 「은」, 없으면 「는」
    static func eun(_ s: String) -> String {
        guard let c = s.unicodeScalars.last, c.value >= 0xAC00, c.value <= 0xD7A3 else { return s + "은" }
        return s + ((c.value - 0xAC00) % 28 == 0 ? "는" : "은")
    }
    /// 받침이 있으면 「을」, 없으면 「를」
    static func eul(_ s: String) -> String {
        guard let c = s.unicodeScalars.last, c.value >= 0xAC00, c.value <= 0xD7A3 else { return s + "을" }
        return s + ((c.value - 0xAC00) % 28 == 0 ? "를" : "을")
    }
    /// 말할 만한 다리인지 — 이름이 있거나, 물길을 건너거나, 300미터 넘는 긴 다리
    private func malHalMan(_ d: Dari) -> Bool {
        if !d.ireum.isEmpty { return d.m >= 30 }
        if !d.mul.isEmpty { return d.m >= 20 }
        return d.m >= 300
    }

    private func dariSalpigi(_ w: Wichi, cheol: Bool) {
        guard !dari.isEmpty else { return }
        let s = max(0, w.sokdo)
        let munteok = s < 3 ? 80.0 : min(1500, max(150, s * 15))
        var ap: [(d: Dari, de: Double, key: String)] = []
        for r in dari where r.cheol == cheol && malHalMan(r) {
            let (jul, jb) = julBang(w.lat, w.lon, r.pt)
            let a = r.pt[0], b = r.pt[r.pt.count - 1]
            let da = geori(w.lat, w.lon, a.0, a.1), db = geori(w.lat, w.lon, b.0, b.1)
            let mun = da <= db ? a : b, de = min(da, db)
            let key = "\(r.ireum)|\(r.mul)|\(String(format: "%.4f,%.4f", mun.0, mun.1))"
            if jul < 20 {
                // 다리 길과 같은 쪽으로 갈 때만 다리 위로 침 — 다리 밑·옆을 가로지를 때는 아님
                if let h = head, gakCha(h, jb) > 40 && gakCha(h, jb + 180) > 40 { continue }
                if deun == nil || (deun!.key != key && (r.ireum.isEmpty || deun!.d.ireum != r.ireum)) { deun = (key, r, Date()) }
                continue
            }
            guard let h = head else { continue }
            if gakCha(h, WichiEngine.bangwi(w.lat, w.lon, mun.0, mun.1)) >= 60 { continue }
            let an = da <= db ? r.pt[1] : r.pt[r.pt.count - 2]
            let anjjok = WichiEngine.bangwi(mun.0, mun.1, an.0, an.1)
            if gakCha(h, anjjok) > 40 { continue }
            let yeop = de * sin(gakCha(WichiEngine.bangwi(w.lat, w.lon, mun.0, mun.1), anjjok) * .pi / 180)
            if yeop > 35 { continue }
            if de > munteok { continue }
            ap.append((r, de, key))
        }
        if let dn = deun {
            let (jul, _) = julBang(w.lat, w.lon, dn.d.pt)
            if jul > 60 {
                deun = nil
                if Date().timeIntervalSince(dn.ttae) > 4 && dn.d.m >= 60 {
                    mal(dn.d.mul.isEmpty ? "다리를 건넜습니다." : "\(Self.eul(dn.d.mul)) 건넜습니다.")
                }
            }
        }
        guard !ap.isEmpty else { return }
        ap.sort { $0.de < $1.de }
        let ga = ap[0]
        if han[ga.key] != nil { return }
        // 섬·이음매로 끊긴 한 다리를 두 번 알리지 않게 — 같은 이름은 3분 안에 다시 말하지 않음
        if !ga.d.ireum.isEmpty, let t = han["ireum|" + ga.d.ireum], Date().timeIntervalSince(t) < 180 { han[ga.key] = Date(); return }
        for k in ap where k.d.ireum == ga.d.ireum && k.d.mul == ga.d.mul && k.de - ga.de < 200 { han[k.key] = Date() }   // 오가는 두 다리는 하나로
        han[ga.key] = Date()
        if !ga.d.ireum.isEmpty { han["ireum|" + ga.d.ireum] = Date() }
        mal(Self.dariMal(ga.d, cheol: cheol))
    }

    static func dariMal(ireum: String, mul: String, m: Double, cheol: Bool, goga: Bool) -> String {
        var t: String
        if !ireum.isEmpty { t = "곧 \(ireum)입니다." }
        else if !mul.isEmpty { t = cheol ? "곧 철교입니다." : "곧 다리입니다." }
        else { t = goga ? "곧 긴 고가 다리입니다." : (cheol ? "곧 긴 철교입니다." : "곧 긴 다리입니다.") }
        if !mul.isEmpty { t += " \(eul(mul)) 건넙니다." }
        return t + " " + giriMal(m)
    }
    private static func dariMal(_ d: Dari, cheol: Bool) -> String { dariMal(ireum: d.ireum, mul: d.mul, m: d.m, cheol: cheol, goga: d.goga) }

    // MARK: ② 고속도로 곁 — 휴게소·졸음쉼터·나들목·분기점·요금소

    static func simIreum(_ t: String, _ n0: String) -> String {
        var n = n0.trimmingCharacters(in: .whitespaces)
        func bakkum(_ a: String, _ b: String) { if n.uppercased().hasSuffix(a) { n = String(n.dropLast(a.count)).trimmingCharacters(in: .whitespaces) + " " + b } }
        switch t {
        case "hyu":
            if !n.contains("휴게소") { n += " 휴게소" }
        case "swim":
            if n.isEmpty { n = "졸음쉼터" } else if !n.contains("쉼터") && !n.contains("휴게") { n += " 쉼터" }
        case "nadeul":
            bakkum("IC", "나들목"); bakkum("JC", "분기점"); bakkum("JCT", "분기점")
            if !n.contains("나들목") && !n.contains("분기점") { n += " 나들목" }
        case "yogeum":
            bakkum("TG", "요금소")
            if !n.contains("요금소") && !n.contains("톨게이트") { n += " 요금소" }
        default: break
        }
        return n
    }

    private func simSalpigi(_ w: Wichi) {
        guard w.sokdo >= 14, let h = head, !sim.isEmpty else { return }
        let s = w.sokdo
        var gaJang: (Sim, Double, String)?
        for p in sim {
            let de = geori(w.lat, w.lon, p.la, p.lo)
            let ap: Double
            switch p.t {
            case "hyu", "swim": ap = min(2000, max(1000, s * 60))
            default: ap = min(1500, max(600, s * 30))
            }
            if de > ap || de < 60 { continue }
            if gakCha(h, WichiEngine.bangwi(w.lat, w.lon, p.la, p.lo)) > 25 { continue }
            let ireum = Self.simIreum(p.t, p.n)
            let key = p.t + "|" + ireum
            if let t = han[key], Date().timeIntervalSince(t) < 900 { continue }
            if gaJang == nil || de < gaJang!.1 { gaJang = (p, de, key) }
        }
        guard let g = gaJang else { return }
        let p = g.0, de = g.1
        han[g.2] = Date()
        let ireum = Self.simIreum(p.t, p.n)
        let gm = de >= 1000 ? String(format: "%.1f킬로미터", (de / 100).rounded() / 10).replacingOccurrences(of: ".0킬", with: "킬") : "\(Int((de / 100).rounded()) * 100)미터"
        switch p.t {
        case "hyu", "swim": mal("\(gm) 앞에 \(ireum)가 있습니다.")
        default: mal("곧 \(ireum)입니다.")
        }
    }

    // MARK: ③ 고장 경계

    private func gogangSalpigi(_ w: Wichi) {
        guard !gogangMutneun, Date().timeIntervalSince(gogangTtae) >= 45 else { return }
        gogangMutneun = true; gogangTtae = Date()
        Task { [weak self] in
            let o = await Chatgi.json("chatta.php", ["a": "gil", "lat": String(format: "%.5f", w.lat), "lon": String(format: "%.5f", w.lon)])
            await MainActor.run {
                guard let self = self else { return }
                self.gogangMutneun = false
                let si = ((o?["si"] as? String) ?? "").trimmingCharacters(in: .whitespaces)
                let gu = ((o?["gu"] as? String) ?? "").trimmingCharacters(in: .whitespaces)
                let ireum = Self.gogangIreum(si, gu)
                guard !ireum.isEmpty else { return }
                if !self.gogang.isEmpty && ireum != self.gogang { self.mal("\(ireum)에 들어섰습니다.") }
                self.gogang = ireum
            }
        }
    }
    /// 「서울특별시」·「경기도」 + 「강남구」·「여주시」 → 「서울 강남구」, 「경기도 여주시」
    static func gogangIreum(_ si: String, _ gu: String) -> String {
        var a = si
        // 카카오는 「서울」·「경기」·「충북」처럼 줄여 줌 — 말로 들을 때 알기 쉬운 이름으로
        let jul: [String: String] = ["경기": "경기도", "강원": "강원도", "강원특별자치도": "강원도", "충북": "충청북도", "충남": "충청남도",
                                     "전북": "전라북도", "전북특별자치도": "전라북도", "전남": "전라남도", "경북": "경상북도", "경남": "경상남도",
                                     "제주": "제주도", "제주특별자치도": "제주도", "서울특별시": "서울", "부산광역시": "부산", "대구광역시": "대구",
                                     "인천광역시": "인천", "광주광역시": "광주", "대전광역시": "대전", "울산광역시": "울산", "세종특별자치시": "세종"]
        a = jul[a] ?? a
        if gu.isEmpty { return a }
        if a.isEmpty { return gu }
        return a == gu ? a : "\(a) \(gu)"
    }
}
