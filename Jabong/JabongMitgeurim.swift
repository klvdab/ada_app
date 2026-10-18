// 자봉 앱 2.20.0 (261012-I14, 대장클, 이사장님 승인 2026-10-12 「1」) AI 밑그림과 지도
// AI 밑그림 = 협회 걷기 길찾기(나스 /jeom/gilchatgi.php)가 짐작한 걸을 수 있는 길. 봉사자 손에만 쥐여 드리는 짐작이고,
//   길눈 이용자에게 점지도로 내놓지 않습니다(이사장님 원칙: 밑그림은 짐작일 뿐, 실제로 걷는 길이 정답).
// 쓰임 넷: 1) 부탁 길 미리 듣기 2) 걷는 동안 꺾는 곳 15미터 앞 한 번 알림(처음값 켬) 3) 다 걸은 뒤 견주기(15미터 넘게 어긋난 구간 —
//   막지 않고 까닭을 말로 남기시도록 여쭘) 4) 밑그림 꺾는 곳을 지났는데 꺾임 표시가 없으면 짚어 드림. 견준 결과는 올릴 때 협회 점검에도 함께 갑니다.
// 지도: 카카오 지도(자바스크립트)를 협회 나스 jabong/jido.php 에 띄워 앱 안 웹 화면으로 봄 — 앱에 지도 부품을 따로 싣지 않아
//   열쇠는 나스가 쥐고, 카카오 개발자 콘솔에 협회 누리집 주소만 등록되면 바로 보입니다. 화면낭독기에는 지도를 가립니다.
import SwiftUI
import WebKit
import Foundation

struct GrMitKkeok: Codable {
    var lat: Double
    var lon: Double
    var gak: Double      // 돈 각도(오른쪽 +)
    var sigye: Int       // 시계 방향
    var ap: Int          // 출발부터 길 따라 거리(미터)
}

struct GrMit: Codable {
    var pts: [[Double]]
    var kkeok: [GrMitKkeok]
    var geori: Int
    var gugan: [String]? = nil
}

struct GrEotgan: Codable {
    var st0: Int
    var st1: Int
    var gil: Int          // 어긋난 채 걸은 길이(미터)
    var choedae: Int      // 밑그림에서 가장 멀었던 거리(미터)
    var lat: Double
    var lon: Double
    var kkadak: String? = nil
}

struct GrPajim: Codable {
    var st: Int
    var sigye: Int
    var lat: Double
    var lon: Double
}

enum JbMitgeurim {
    static let gyoyuk = "밑그림은 짐작일 뿐, 실제로 걷는 길이 정답입니다."

    /// 걷는 동안 꺾는 곳 15미터 앞 알림 — 처음값 켬
    static var allimKyeojim: Bool {
        get { UserDefaults.standard.object(forKey: "jb.mit.allim") as? Bool ?? true }
        set { UserDefaults.standard.set(newValue, forKey: "jb.mit.allim") }
    }
    /// 지도 보이기 — 처음값 켬(화면낭독기에는 늘 가림)
    static var jidoKyeojim: Bool {
        get { UserDefaults.standard.object(forKey: "jb.mit.jido") as? Bool ?? true }
        set { UserDefaults.standard.set(newValue, forKey: "jb.mit.jido") }
    }

    // MARK: 받기

    static func batgi(_ slat: Double, _ slon: Double, _ mlat: Double, _ mlon: Double) async -> GrMit? {
        let q = ["slat": String(format: "%.6f", slat), "slon": String(format: "%.6f", slon),
                 "mlat": String(format: "%.6f", mlat), "mlon": String(format: "%.6f", mlon)]
        guard let o = await Chatgi.json("gilchatgi.php", q), (o["ok"] as? Bool) == true,
              let raw = o["pts"] as? [[Any]] else { return nil }
        let pts: [[Double]] = raw.compactMap { r in
            guard r.count >= 2, let a = Chatgi.su(r[0]), let b = Chatgi.su(r[1]) else { return nil }
            return [a, b]
        }
        guard pts.count >= 2 else { return nil }
        let cum = nujeok(pts)
        let an = (o["an"] as? [[String: Any]]) ?? []
        var kk: [GrMitKkeok] = []
        for a in an {
            guard let j = a["jeom"] as? [Any], j.count >= 2, let la = Chatgi.su(j[0]), let lo = Chatgi.su(j[1]) else { continue }
            let k = gakkaunI(pts, la, lo)
            guard k > 0, k < pts.count - 1 else { continue }
            guard let g = gakDo(pts, k), abs(g) >= 30 else { continue }
            if let l = kk.last, Double(Int(cum[k])) - Double(l.ap) < 5 { continue }
            kk.append(GrMitKkeok(lat: pts[k][0], lon: pts[k][1], gak: (g * 10).rounded() / 10, sigye: JeomGeurigi.sigye(g), ap: Int(cum[k])))
        }
        var gg: [String] = []
        for g in (o["gugan"] as? [[String: Any]]) ?? [] { if let j = g["jong"] as? String { gg.append(j) } }
        return GrMit(pts: pts, kkeok: kk, geori: Int(cum.last ?? 0), gugan: gg.isEmpty ? nil : gg)
    }

    // MARK: 말

    /// 밑그림 듣기 말 — banghyang 이 있으면(지금 서 있는 쪽) 첫 방향을 시계로
    static func mal(_ m: GrMit, banghyang: Double? = nil) -> String {
        var t = "AI 밑그림입니다. 실제 길과 다를 수 있습니다. \(gyoyuk) 모두 약 \(gm(m.geori)), 꺾는 곳 \(m.kkeok.count)곳입니다. "
        if let b = banghyang, b >= 0, m.pts.count >= 2 {
            let k = apJeom(m.pts, 0, 10)
            let bw = WichiEngine.bangwi(m.pts[0][0], m.pts[0][1], m.pts[k][0], m.pts[k][1])
            t += "지금 서 계신 쪽에서 \(sigye12(JeomGeurigi.gakCha(b, bw)))시 방향으로 출발합니다. "
        }
        var ap = 0
        for (i, k) in m.kkeok.enumerated() {
            t += (i == 0 ? "처음 " : "이어 ") + "약 \(gm(k.ap - ap)) 가서 \(k.sigye)시 방향으로 꺾습니다. "
            ap = k.ap
        }
        t += (m.kkeok.isEmpty ? "약 \(gm(m.geori)) 곧게 가면" : "마지막으로 약 \(gm(m.geori - ap)) 가면") + " 도착입니다."
        if let gg = m.gugan, !gg.isEmpty {
            var su: [String: Int] = [:]
            for g in gg { su[g, default: 0] += 1 }
            t += " 길 가운데 " + su.sorted { $0.key < $1.key }.map { "\($0.key) \($0.value)곳" }.joined(separator: ", ") + "이 있을 수 있습니다."
        }
        return t
    }

    static func gm(_ m: Int) -> String {
        m >= 1000 ? String(format: "%.1f킬로미터", Double(m) / 1000) : "\(max(0, (m + 2) / 5 * 5))미터"
    }

    /// 앞쪽 0도 기준 돈 각도 → 1~12시
    static func sigye12(_ d: Double) -> Int {
        var s = Int((((d + 360).truncatingRemainder(dividingBy: 360)) / 30).rounded()) % 12
        if s == 0 { s = 12 }
        return s
    }

    // MARK: 견주기

    /// 걸은 자리와 밑그림을 견줌 — 15미터 넘게 3자리 넘게 이어지면 어긋난 구간, 꺾는 곳 15미터 안을 지났는데 꺾임 표시가 없으면 빠짐
    static func gyeonjugi(_ g: GrGil, _ m: GrMit) -> ([GrEotgan], [GrPajim]) {
        let ps = g.pts.filter { $0.lat != nil && $0.lon != nil && ($0.acc ?? 99) <= 25 && $0.m == nil }
        var eot: [GrEotgan] = []
        var run: [(GrJari, Double)] = []
        func danggi() {
            if run.count >= 3, let a = run.first?.0, let b = run.last?.0 {
                var gil = 0.0
                for i in 1..<run.count { gil += WichiEngine.geori(run[i - 1].0.lat!, run[i - 1].0.lon!, run[i].0.lat!, run[i].0.lon!) }
                let jj = run.max(by: { $0.1 < $1.1 })!
                eot.append(GrEotgan(st0: a.st, st1: b.st, gil: Int(gil.rounded()), choedae: Int(jj.1.rounded()), lat: jj.0.lat!, lon: jj.0.lon!))
            }
            run = []
        }
        for p in ps {
            let d = seonGeori(p.lat!, p.lon!, m.pts)
            if d > 15 { run.append((p, d)) } else { danggi() }
        }
        danggi()
        var paj: [GrPajim] = []
        for k in m.kkeok where abs(k.gak) >= 45 {
            guard let near = ps.min(by: { WichiEngine.geori($0.lat!, $0.lon!, k.lat, k.lon) < WichiEngine.geori($1.lat!, $1.lon!, k.lat, k.lon) }),
                  WichiEngine.geori(near.lat!, near.lon!, k.lat, k.lon) <= 15 else { continue }
            let itda = g.marks.contains { mk in
                guard mk.name.hasSuffix("꺾임") || mk.name == "되돌아가는 곳" else { return false }
                if abs(mk.st - near.st) <= 12 { return true }
                if let a = mk.lat, let o = mk.lon, WichiEngine.geori(a, o, k.lat, k.lon) <= 20 { return true }
                return false
            }
            if !itda { paj.append(GrPajim(st: near.st, sigye: k.sigye, lat: k.lat, lon: k.lon)) }
        }
        return (eot, paj)
    }

    static func eotganMal(_ e: GrEotgan) -> String {
        "\(e.st0)걸음째부터 \(e.st1)걸음째까지 약 \(gm(e.gil)) 구간이 밑그림과 가장 멀리 \(e.choedae)미터 어긋났습니다." + ((e.kkadak ?? "").isEmpty ? "" : " 까닭: \(e.kkadak!)")
    }
    static func pajimMal(_ p: GrPajim) -> String {
        "\(p.st)걸음째 근처, 밑그림의 \(p.sigye)시 방향 꺾는 곳에 꺾임 표시가 없습니다. 실제로 꺾지 않았다면 괜찮습니다."
    }

    static func gyeolgwaMal(_ e: [GrEotgan], _ p: [GrPajim]) -> String {
        if e.isEmpty && p.isEmpty { return "AI 밑그림과 견주어 보니 크게 어긋난 곳이 없습니다." }
        var t = "AI 밑그림과 견주어 보았습니다. "
        if !e.isEmpty {
            t += "15미터 넘게 어긋난 구간이 \(e.count)곳입니다. " + e.prefix(3).map { eotganMal($0) }.joined(separator: " ")
            t += " 실제로 걸은 길이 정답이니 막지는 않습니다. 왜 다르게 걸으셨는지 그린 길 화면에서 까닭 말로 남기기를 눌러 주시면 협회 점검에 도움이 됩니다. "
        }
        if !p.isEmpty { t += "꺾임 표시가 빠졌을 수 있는 곳이 \(p.count)곳입니다. " + p.prefix(3).map { pajimMal($0) }.joined(separator: " ") }
        return t
    }

    // MARK: 작은 셈

    static func nujeok(_ pts: [[Double]]) -> [Double] {
        var c = [0.0]
        for i in 1..<max(1, pts.count) { c.append(c[i - 1] + WichiEngine.geori(pts[i - 1][0], pts[i - 1][1], pts[i][0], pts[i][1])) }
        return c
    }

    static func gakkaunI(_ pts: [[Double]], _ la: Double, _ lo: Double) -> Int {
        var b = 0, bd = Double.greatestFiniteMagnitude
        for (i, p) in pts.enumerated() {
            let d = WichiEngine.geori(p[0], p[1], la, lo)
            if d < bd { bd = d; b = i }
        }
        return b
    }

    /// k 에서 앞(+)이나 뒤(-)로 m미터 넘게 떨어진 첫 점
    static func apJeom(_ pts: [[Double]], _ k: Int, _ m: Double, dwiro: Bool = false) -> Int {
        var i = k
        while true {
            let n = dwiro ? i - 1 : i + 1
            guard n >= 0, n < pts.count else { return i }
            i = n
            if WichiEngine.geori(pts[k][0], pts[k][1], pts[i][0], pts[i][1]) >= m { return i }
        }
    }

    static func gakDo(_ pts: [[Double]], _ k: Int) -> Double? {
        let a = apJeom(pts, k, 8, dwiro: true), b = apJeom(pts, k, 8)
        guard a != k, b != k else { return nil }
        let b1 = WichiEngine.bangwi(pts[a][0], pts[a][1], pts[k][0], pts[k][1])
        let b2 = WichiEngine.bangwi(pts[k][0], pts[k][1], pts[b][0], pts[b][1])
        return JeomGeurigi.gakCha(b1, b2)
    }

    /// 점에서 선까지 가장 가까운 거리(미터) — 좁은 곳이라 평면으로 셈
    static func seonGeori(_ la: Double, _ lo: Double, _ pts: [[Double]]) -> Double {
        let ky = 111320.0, kx = 111320.0 * cos(la * .pi / 180)
        var best = Double.greatestFiniteMagnitude
        for i in 0..<max(0, pts.count - 1) {
            let ax = (pts[i][1] - lo) * kx, ay = (pts[i][0] - la) * ky
            let bx = (pts[i + 1][1] - lo) * kx, by = (pts[i + 1][0] - la) * ky
            let dx = bx - ax, dy = by - ay
            let l2 = dx * dx + dy * dy
            var t = l2 > 0 ? -(ax * dx + ay * dy) / l2 : 0
            t = min(1, max(0, t))
            let x = ax + t * dx, y = ay + t * dy
            best = min(best, (x * x + y * y).squareRoot())
        }
        return pts.count == 1 ? WichiEngine.geori(la, lo, pts[0][0], pts[0][1]) : best
    }
}

// MARK: 엔진에 붙임

extension JeomGeurigi {
    /// 부탁 길 도착 좌표 — 이 길 그리러 가기에서 함께 정함
    static var butakMok: (Double, Double)? {
        get {
            let d = UserDefaults.standard
            guard d.string(forKey: "jb.butak.id")?.isEmpty == false, d.object(forKey: "jb.butak.mlat") != nil else { return nil }
            let a = d.double(forKey: "jb.butak.mlat"), o = d.double(forKey: "jb.butak.mlon")
            return (a == 0 && o == 0) ? nil : (a, o)
        }
        set {
            let d = UserDefaults.standard
            if let v = newValue { d.set(v.0, forKey: "jb.butak.mlat"); d.set(v.1, forKey: "jb.butak.mlon") }
            else { d.removeObject(forKey: "jb.butak.mlat"); d.removeObject(forKey: "jb.butak.mlon") }
        }
    }

    /// 그린 길 하나의 어긋난 구간에 까닭을 말로 남김
    func kkadakMallo(_ id: String, _ i: Int) {
        guard MalDeutgi.heorakItda else {
            MalDeutgi.heorak { ok in if ok { self.kkadakMallo(id, i) } else { SoriEngine.shared.mal("말로 남기려면 마이크와 음성 인식 허락이 필요합니다.") } }
            return
        }
        SoriEngine.shared.mal("어긋난 까닭을 한두 마디로 말씀해 주십시오. 예를 들어 공사 중이라 돌아갔습니다.", .annae)
        SoriEngine.shared.kkeutnamyeon {
            SoriEngine.shared.sori(.deutgi)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                let ok = MalDeutgi.shared.myeongryeong(gidarim: 8) { alts in
                    MalDeutgi.shared.meomchugi()
                    let t = String((alts.first ?? "").trimmingCharacters(in: .whitespacesAndNewlines).prefix(60))
                    guard !t.isEmpty else { SoriEngine.shared.mal("말씀이 들리지 않았습니다. 다시 눌러 주십시오."); return }
                    self.kkadakJeokgi(id, i, t)
                }
                if !ok { SoriEngine.shared.mal("지금은 마이크를 열지 못했습니다. 잠시 뒤 다시 눌러 주십시오.") }   // 261019 이사장님 승인 「1」 — 글로 적는 칸이 없으므로 다시 누르시게
            }
        }
    }
}

// MARK: 지도 — 카카오 지도를 협회 나스 쪽 웹 화면으로

struct JbJidoView: UIViewRepresentable {
    /// 지도에 그릴 것 — 1초마다 다시 물음
    let geurim: () -> [String: Any]

    func makeCoordinator() -> Coord { Coord(geurim) }

    func makeUIView(context: Context) -> WKWebView {
        let w = WKWebView(frame: .zero, configuration: WKWebViewConfiguration())
        w.isOpaque = false
        w.scrollView.isScrollEnabled = false
        w.accessibilityElementsHidden = true   // 화면낭독기에는 가림
        w.navigationDelegate = context.coordinator
        if let u = URL(string: "https://lvd.ada.or.kr/jabong/jido.php") { w.load(URLRequest(url: u)) }
        context.coordinator.web = w
        return w
    }

    func updateUIView(_ w: WKWebView, context: Context) { context.coordinator.geurim = geurim }

    static func dismantleUIView(_ w: WKWebView, coordinator: Coord) { coordinator.sigye?.invalidate() }

    final class Coord: NSObject, WKNavigationDelegate {
        var geurim: () -> [String: Any]
        weak var web: WKWebView?
        var sigye: Timer?
        init(_ g: @escaping () -> [String: Any]) { geurim = g }
        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            bonaegi()
            sigye?.invalidate()
            sigye = Timer.scheduledTimer(withTimeInterval: 1.5, repeats: true) { [weak self] _ in self?.bonaegi() }
        }
        func bonaegi() {
            guard let w = web, let d = try? JSONSerialization.data(withJSONObject: geurim()), let s = String(data: d, encoding: .utf8) else { return }
            w.evaluateJavaScript("window.jbGeurim && window.jbGeurim(\(s))", completionHandler: nil)
        }
    }
}

extension JeomGeurigi {
    /// 지도에 보낼 것 — 회색 점선 밑그림, 파란 선 걸은 자취, 점 표시, 주황 어긋난 구간, 빨강 반짝임 빠진 꺾임, 걷는 쪽이 위로
    static func jidoGeurim(_ g: GrGil?, banghyang: Double?) -> [String: Any] {
        guard let g = g else { return [:] }
        var o: [String: Any] = [:]
        if let m = g.mit { o["mit"] = m.pts }
        o["gil"] = g.pts.compactMap { p -> [Double]? in
            guard let a = p.lat, let b = p.lon, (p.acc ?? 99) <= 30 else { return nil }
            return [a, b]
        }
        o["pyosi"] = g.marks.compactMap { m -> [String: Any]? in
            guard let a = m.lat, let b = m.lon else { return nil }
            return ["lat": a, "lon": b, "name": m.name]
        }
        o["eotgan"] = (g.mitEotgan ?? []).map { e in
            g.pts.filter { $0.st >= e.st0 && $0.st <= e.st1 && $0.lat != nil }.map { [$0.lat!, $0.lon!] }
        }
        o["pajim"] = (g.mitPajim ?? []).map { [$0.lat, $0.lon] }
        if let b = banghyang, b >= 0 { o["h"] = b }
        return o
    }
}
