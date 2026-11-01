// 걸을 때 지도 알림 — 2.123.0 (261101-I77, 이사장님 지시 2026-11-01 「할 수 있는거라면 다 넣고 싶어 … 알아서 해」) — 시험 중
// 오픈스트리트맵(협회 서버 jido.php gal=geot, 400미터 칸으로 받아 30일 갈무리)에서 걸을 때 쓸모 있는 것을 알려 드림.
//   길: 계단(오르는지 내리는지·계단 수·손잡이 쪽)·육교·지하도·경사로·보도 없는 길
//   건너기: 횡단보도(신호등·음향신호기·점자블록·턱 높이·가운데 섬)
//   시설: 엘리베이터·지하철 출입구(번호·엘리베이터 출구)·화장실(장애인 화장실)·버스 정류장(지붕·의자)·건물 출입구,
//         얼마나 자세히가 「자세히」이면 음수대·의자도
// 방향은 늘 시계 방향, 뒤에 있는 것은 말하지 않고, 한 번 알린 것은 다시 말하지 않음. 말과 말 사이는 3초 넘게.
// 위성 오차로 잘못 말할 수 있어 설정 탭 말하기 설정의 「걸을 때 지도 알림(시험 중)」을 켠 폰에서만 돎(처음 꺼짐).
// 안드로이드 JidoGeot.kt 와 같은 셈법.
import Foundation

final class JidoGeot {
    static let shared = JidoGeot()

    private struct Seon { let t: String; let n: String; let i: String; let su: Int; let son: String; let m: Double; let pt: [(Double, Double)] }
    private struct Jeom { let t: String; let n: String; let la: Double; let lo: Double; let o: [String: Any] }

    private var seon: [Seon] = []
    private var jeom: [Jeom] = []
    private var jari: (Double, Double)?
    private var batneun = false
    private var batTtae = Date.distantPast
    private var deo = false
    private var han: Set<String> = []
    private var malTtae = Date.distantPast
    private var head: Double?
    private var headFrom: (Double, Double)?

    func kkeut() { seon = []; jeom = []; jari = nil; han = []; deo = false; head = nil; headFrom = nil }

    /// 걸을 때 자리마다 부름. bang = 앞쪽(도), 모르면 nil
    func wichi(_ la: Double, _ lo: Double, bang: Double?) {
        guard Seoljeong.shared.jidoGeotOn else { return }
        if let b = bang { head = b; headFrom = (la, lo) }
        else if let f = headFrom { if WichiEngine.geori(f.0, f.1, la, lo) >= 6 { head = WichiEngine.bangwi(f.0, f.1, la, lo); headFrom = (la, lo) } }
        else { headFrom = (la, lo) }
        if (seon.isEmpty && jeom.isEmpty) || deo || jari == nil || WichiEngine.geori(jari!.0, jari!.1, la, lo) > 150 { batgi(la, lo) }
        salpigi(la, lo)
    }

    private func batgi(_ la: Double, _ lo: Double) {
        guard !batneun, Date().timeIntervalSince(batTtae) >= 20 else { return }
        batneun = true; batTtae = Date()
        let q = ["gal": "geot", "lat": String(format: "%.5f", la), "lon": String(format: "%.5f", lo), "ban": "300"]
        Task { [weak self] in
            let d = try? await Tongsin.shared.getSae("jido.php", q)
            await MainActor.run {
                guard let self = self else { return }
                self.batneun = false
                guard let d = d, let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any] else { return }
                self.seon = ((o["seon"] as? [[String: Any]]) ?? []).compactMap { r in
                    let pt: [(Double, Double)] = ((r["p"] as? [Any]) ?? []).compactMap { p in
                        guard let a = p as? [Any], a.count >= 2, let x = Chatgi.su(a[0]), let y = Chatgi.su(a[1]) else { return nil }
                        return (x, y)
                    }
                    guard pt.count >= 2 else { return nil }
                    return Seon(t: (r["t"] as? String) ?? "", n: (r["n"] as? String) ?? "", i: (r["i"] as? String) ?? "",
                                su: Int(Chatgi.su(r["su"]) ?? 0), son: (r["son"] as? String) ?? "", m: Chatgi.su(r["m"]) ?? 0, pt: pt)
                }
                self.jeom = ((o["jeom"] as? [[String: Any]]) ?? []).compactMap { r in
                    guard let la = Chatgi.su(r["a"]), let lo = Chatgi.su(r["o"]) else { return nil }
                    return Jeom(t: (r["t"] as? String) ?? "", n: (r["n"] as? String) ?? "", la: la, lo: lo, o: r)
                }
                self.deo = Chatgi.su(o["deo"]) == 1
                self.jari = (la, lo)
            }
        }
    }

    private func chai(_ a: Double, _ b: Double) -> Double { var x = (a - b).truncatingRemainder(dividingBy: 360); if x > 180 { x -= 360 }; if x < -180 { x += 360 }; return x }
    /// 앞쪽 기준 상대 각도 → 시계 방향(12·1·…·11)
    static func sigyeSi(_ rel: Double) -> Int { var s = Int((rel / 30).rounded()); s = ((s % 12) + 12) % 12; return s == 0 ? 12 : s }
    private func geoMal(_ d: Double) -> String { d < 3 ? "바로 앞" : "\(max(1, Int((d / 5).rounded()) * 5))미터" }

    private func tomak(_ la: Double, _ lo: Double, _ p1: (Double, Double), _ p2: (Double, Double)) -> (Double, (Double, Double)) {
        let K = 111320.0, co = cos(la * .pi / 180)
        let x = (lo - p1.1) * K * co, y = (la - p1.0) * K
        let bx = (p2.1 - p1.1) * K * co, by = (p2.0 - p1.0) * K
        let nn = bx * bx + by * by
        let t = nn > 0 ? max(0, min(1, (x * bx + y * by) / nn)) : 0
        let dx = x - bx * t, dy = y - by * t
        return ((dx * dx + dy * dy).squareRoot(), (p1.0 + (p2.0 - p1.0) * t, p1.1 + (p2.1 - p1.1) * t))
    }
    private func gakkaun(_ la: Double, _ lo: Double, _ pt: [(Double, Double)]) -> (Double, (Double, Double)) {
        var best = (1e9, pt[0])
        for i in 1..<pt.count { let r = tomak(la, lo, pt[i - 1], pt[i]); if r.0 < best.0 { best = r } }
        return best
    }

    private func salpigi(_ la: Double, _ lo: Double) {
        guard Date().timeIntervalSince(malTtae) >= 3 else { return }
        let jasehi = Seoljeong.shared.malSang >= 2
        var hubo: [(d: Double, key: String, mal: String)] = []
        // 길
        for s in seon {
            let key = "s|\(s.t)|\(s.pt[0].0),\(s.pt[0].1)"
            if han.contains(key) { continue }
            let (d, p) = gakkaun(la, lo, s.pt)
            if s.t == "bodo_eopseum" {
                if d <= 8 { hubo.append((d, key, "보도가 없는 길입니다. 차를 조심하십시오.")) }
                continue
            }
            if d > 20 { continue }
            var si = 0
            if let h = head, d >= 3 {
                let rel = chai(WichiEngine.bangwi(la, lo, p.0, p.1), h)
                if abs(rel) > 60 { continue }
                si = Self.sigyeSi(rel)
            }
            let ap = si > 0 ? "\(si)시 방향 \(geoMal(d)) 앞에 " : "앞에 "
            switch s.t {
            case "gyedan":
                // 가까운 끝이 처음 점이면 그린 차례대로 지나감
                let a = s.pt[0], b = s.pt[s.pt.count - 1]
                let apeuro = WichiEngine.geori(la, lo, a.0, a.1) <= WichiEngine.geori(la, lo, b.0, b.1)
                var oreum = ""
                if s.i == "up" { oreum = apeuro ? "오르는 " : "내려가는 " } else if s.i == "down" { oreum = apeuro ? "내려가는 " : "오르는 " }
                var t = ap + oreum + "계단입니다."
                if s.su > 0 { t += " 계단은 \(s.su)개입니다." }
                t += Self.sonMal(s.son, apeuro: apeuro)
                hubo.append((d, key, t))
            case "yukgyo": hubo.append((d, key, ap + (s.n.isEmpty ? "" : "\(s.n) ") + "육교입니다." + (s.m >= 15 ? " 길이는 약 \(Int((s.m / 5).rounded()) * 5)미터입니다." : "")))
            case "jihado": hubo.append((d, key, ap + (s.n.isEmpty ? "" : "\(s.n) ") + "지하도입니다."))
            case "gyeongsa":
                let a = s.pt[0], b = s.pt[s.pt.count - 1]
                let apeuro = WichiEngine.geori(la, lo, a.0, a.1) <= WichiEngine.geori(la, lo, b.0, b.1)
                let oreum = s.i == "up" ? (apeuro ? "오르막" : "내리막") : (s.i == "down" ? (apeuro ? "내리막" : "오르막") : "비탈")
                hubo.append((d, key, ap + oreum + " 경사로입니다."))
            default: break
            }
        }
        // 점
        for j in jeom {
            let key = "j|\(j.t)|\(j.la),\(j.lo)"
            if han.contains(key) { continue }
            let d = WichiEngine.geori(la, lo, j.la, j.lo)
            let beom: Double = j.t == "geonneol" ? 15 : 25
            if d > beom { continue }
            var si = 0
            if let h = head, d >= 3 {
                let rel = chai(WichiEngine.bangwi(la, lo, j.la, j.lo), h)
                if abs(rel) > (j.t == "geonneol" ? 50 : 110) { continue }   // 뒤에 있는 것은 말하지 않음
                si = Self.sigyeSi(rel)
            }
            let ap = si > 0 ? "\(si)시 방향 \(geoMal(d))에 " : "가까이 "
            if let t = Self.jeomMal(j.t, j.n, j.o, jasehi: jasehi) { hubo.append((d, key, ap + t)) }
        }
        guard let g = hubo.min(by: { $0.d < $1.d }) else { return }
        han.insert(g.key)
        malTtae = Date()
        SoriEngine.shared.mal(g.mal, .jeongbo)
        Girok.shared.namgi("jido_geot_mal", ["mal": g.mal])
    }

    /// 손잡이 — 그린 쪽 기준 왼쪽·오른쪽을 걷는 쪽으로 돌려 시계 방향으로
    static func sonMal(_ son: String, apeuro: Bool) -> String {
        guard !son.isEmpty else { return "" }
        let oen = son.contains("handrail:left"), oreun = son.contains("handrail:right"), gaunde = son.contains("handrail:center")
        if (oen && oreun) || son == "handrail" { return " 손잡이는 양쪽에 있습니다." }
        var t: [String] = []
        if oen { t.append(apeuro ? "9시 방향" : "3시 방향") }
        if oreun { t.append(apeuro ? "3시 방향" : "9시 방향") }
        if gaunde { t.append("가운데") }
        return t.isEmpty ? "" : " 손잡이는 \(t.joined(separator: "과 "))에 있습니다."
    }

    static func jeomMal(_ t: String, _ n: String, _ o: [String: Any], jasehi: Bool) -> String? {
        func su(_ k: String) -> Int { Int(Chatgi.su(o[k]) ?? 0) }
        switch t {
        case "geonneol":
            // 2.123.0 (261102, 이사장님 승인 「1」) 40미터 안 공식 교차로 이름(국토교통부 표준노드링크)을 붙임
            let gy = ((o["gyo"] as? String) ?? "").trimmingCharacters(in: .whitespaces)
            var m = gy.isEmpty ? "횡단보도입니다." : "\(gy) 횡단보도입니다."
            if su("sinho") == 1 { m += " 신호등이 있습니다." } else if su("sinho") == -1 { m += " 신호등이 없습니다." }
            if su("sori") == 1 { m += " 음향신호기가 있습니다." } else if su("sori") == -1 { m += " 음향신호기는 없습니다." }
            if su("jeomja") == 1 { m += " 점자블록이 있습니다." }
            switch (o["teok"] as? String) ?? "" {
            case "lowered": m += " 턱이 낮습니다."
            case "raised": m += " 턱이 높습니다."
            case "flush": m += " 턱이 없습니다."
            default: break
            }
            if su("seom") == 1 { m += " 가운데에 쉬어 가는 섬이 있습니다." }
            return m
        case "elev": return "엘리베이터가 있습니다."
        case "chulgu":
            let r = (o["r"] as? String) ?? ""
            var m = (n.isEmpty ? "지하철" : n) + (r.isEmpty ? " 출입구" : " \(r)번 출구") + "입니다."
            if su("elev") == 1 { m += " 엘리베이터로 오르내리는 출구입니다." }
            return m
        case "hwajangsil": return su("jang") == 1 ? "장애인 화장실이 있는 화장실입니다." : "화장실이 있습니다."
        case "jeongryu":
            var m = (n.isEmpty ? "버스 정류장" : "\(n) 버스 정류장") + "입니다."
            if su("jibung") == 1 && su("uija") == 1 { m += " 지붕과 의자가 있습니다." }
            else if su("jibung") == 1 { m += " 지붕이 있습니다." } else if su("uija") == 1 { m += " 의자가 있습니다." }
            if su("jeomja") == 1 { m += " 점자블록이 있습니다." }
            return m
        case "mun":
            let mj = (o["mj"] as? String) ?? ""
            if mj == "emergency" || mj == "exit" { return nil }
            var m = (n.isEmpty ? "" : "\(n) ") + (mj == "main" ? "주출입구" : (mj == "service" ? "뒷문" : "출입구")) + "입니다."
            switch (o["door"] as? String) ?? "" {
            case "automatic": m += " 자동문입니다."
            case "revolving": m += " 회전문입니다."
            case "sliding": m += " 미닫이문입니다."
            case "hinged": m += " 여닫이문입니다."
            default: break
            }
            if su("jang") == 1 { m += " 휠체어로 드나들 수 있습니다." }
            return m
        case "eumsu": return jasehi ? "음수대가 있습니다." : nil
        case "swim": return jasehi ? "앉아 쉴 의자가 있습니다." : nil
        default: return nil
        }
    }
}
