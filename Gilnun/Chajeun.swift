// 요즘 찾으신 곳·자동 입력·교통편 — 2.93.0 (261017-I47, 이사장님 승인 2026-10-17 「그러자」)
// 웹 길눈 yeojeong.js(2026-09-14 「요즘 찾으신 곳」)·jachu.js 1.1(자동 입력)·gyotong.html 0.2(교통편)를 앱으로 옮김.
//   목적지를 정하실 때마다 이름을 적어 두었다가 「요즘 찾으신 곳」에서 한 번에 다시 가시게 함(서른 곳까지, 이 폰 안에만).
//   찾기 칸에 글자를 치시면 요즘 찾으신 곳과 즐겨찾기에서 맞는 곳을 칸 바로 아래에 세 줄까지(초성만 쳐도 잡힘),
//   맨 위 후보 하나만 0.4초 뒤 한 번 읽고, 엔터를 치면 맨 위 후보로(딱 맞는 이름이 있으면 그것, 역·정류장·터미널로 끝나면 적으신 그대로 찾기).
import SwiftUI

struct ChajeunHang: Codable, Identifiable, Hashable {
    var ireum: String
    var juso: String
    var lat: Double
    var lon: Double
    var ttae: Date
    var beon: Int
    var id: String { ireum }
    var jangso: Jangso { Jangso(ireum: ireum, juso: juso, lat: lat, lon: lon) }
}

final class Chajeun: ObservableObject {
    static let shared = Chajeun()
    @Published private(set) var mokrok: [ChajeunHang] = []
    private let pail: URL

    init() {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("gilnun", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        pail = dir.appendingPathComponent("chajeun.json")
        if let d = try? Data(contentsOf: pail), let l = try? JSONDecoder().decode([ChajeunHang].self, from: d) { mokrok = l }
    }

    func damgi(_ m: Mokjeok) {
        let f = { [weak self] in
            guard let self = self, !m.ireum.isEmpty else { return }
            var beon = 1
            if let i = self.mokrok.firstIndex(where: { $0.ireum == m.ireum }) { beon = self.mokrok[i].beon + 1; self.mokrok.remove(at: i) }
            self.mokrok.insert(ChajeunHang(ireum: m.ireum, juso: m.juso, lat: m.lat, lon: m.lon, ttae: Date(), beon: beon), at: 0)
            if self.mokrok.count > 30 { self.mokrok = Array(self.mokrok.prefix(30)) }
            self.jeojang()
        }
        if Thread.isMainThread { f() } else { DispatchQueue.main.async(execute: f) }
    }

    func jiugi() { mokrok = []; jeojang() }

    private func jeojang() {
        if let d = try? JSONEncoder().encode(mokrok) { try? d.write(to: pail, options: .atomic) }
    }

    // MARK: 자동 입력 — 웹 jachu.js 1.1

    struct Hubo: Identifiable { let ireum: String; let j: Jangso; let jeul: Bool; let jom: Int; let ttae: Date; var id: String { ireum } }

    private static let cho: [Character] = Array("ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ")
    static func chosung(_ s: String) -> String {
        String(s.unicodeScalars.map { u -> Character in
            if u.value >= 0xAC00 && u.value <= 0xD7A3 { return cho[Int((u.value - 0xAC00) / 588)] }
            return Character(u)
        })
    }
    private static func choman(_ s: String) -> Bool { !s.isEmpty && s.unicodeScalars.allSatisfy { $0.value >= 0x3131 && $0.value <= 0x314E } }
    private static func ppae(_ s: String) -> String { s.replacingOccurrences(of: " ", with: "") }

    func goreugi(_ mal: String) -> [Hubo] {
        let q = Chajeun.ppae(mal)
        guard !q.isEmpty else { return [] }
        let cho = Chajeun.choman(q)
        var bon = Set<String>(), out: [Hubo] = []
        func bogi(_ ireum: String, _ geul: String, _ j: Jangso, _ jeul: Bool, _ ttae: Date, _ beon: Int) {
            guard !ireum.isEmpty, !bon.contains(ireum) else { return }
            bon.insert(ireum)
            let s = Chajeun.ppae(geul)
            var jom = -1
            if cho {
                if let r = Chajeun.chosung(s).range(of: q) { let i = Chajeun.chosung(s).distance(from: Chajeun.chosung(s).startIndex, to: r.lowerBound); jom = (i == 0 ? 100 : 60) - i }
            } else if let r = s.range(of: q) {
                let i = s.distance(from: s.startIndex, to: r.lowerBound); jom = (i == 0 ? 100 : 60) - i
            } else if q.count >= 2, let r = Chajeun.chosung(s).range(of: Chajeun.chosung(q)) {
                let i = Chajeun.chosung(s).distance(from: Chajeun.chosung(s).startIndex, to: r.lowerBound); jom = 40 - i
            }
            if jom >= 0 { out.append(Hubo(ireum: ireum, j: j, jeul: jeul, jom: jom + (jeul ? 8 : 0) + min(beon, 5), ttae: ttae)) }
        }
        for c in mokrok { bogi(c.ireum, c.ireum, c.jangso, false, c.ttae, c.beon) }
        for h in Jeulgyeo.shared.mokrok { bogi(h.ireum, h.ireum + " " + h.juso, h.jangso, true, h.ttae, 1) }
        return Array(out.sorted { $0.jom != $1.jom ? $0.jom > $1.jom : $0.ttae > $1.ttae }.prefix(3))
    }

    /// 엔터 때 갈 곳 — 딱 맞는 이름이면 그것, 역·정류장·터미널로 끝나면 적으신 그대로 찾기(nil), 그 밖에는 맨 위 후보
    func wi(_ mal: String) -> Hubo? {
        let l = goreugi(mal)
        guard !l.isEmpty else { return nil }
        let q = Chajeun.ppae(mal)
        if let m = l.first(where: { Chajeun.ppae($0.ireum) == q }) { return m }
        if q.hasSuffix("역") || q.hasSuffix("정류장") || q.hasSuffix("터미널") { return nil }
        return l[0]
    }
}

/// 찾기 칸 바로 아래 후보 세 줄
struct JachuHubo: View {
    let mal: String
    @ObservedObject private var c = Chajeun.shared
    @State private var nogeum = ""
    @State private var malSedae = 0

    var body: some View {
        let l = c.goreugi(mal)
        VStack(alignment: .leading, spacing: 8) {
            ForEach(l) { h in
                NavigationLink(value: GilHwamyeon.mokjeok(h.j)) { Text(h.ireum + (h.jeul ? " — 즐겨찾기" : " — 요즘 찾으신 곳")) }
                    .buttonStyle(KeunDanchu())
            }
        }
        .onChange(of: mal) { v in
            let wi = c.wi(v)?.ireum ?? ""
            guard wi != nogeum else { return }
            nogeum = wi
            malSedae += 1
            let s = malSedae
            if !wi.isEmpty { DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { if s == malSedae { SoriEngine.shared.mal(wi, .jeongbo) } } }
        }
    }
}

/// 요즘 찾으신 곳 — 다섯씩, 몇 번째인지, 이 목록 지우기
struct ChajeunView: View {
    @ObservedObject private var c = Chajeun.shared
    @State private var jiuneun = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if c.mokrok.isEmpty {
                    Text("아직 찾으신 곳이 없습니다. 목적지를 정하실 때마다 여기에 적어 둡니다.").font(.title3)
                } else {
                    Mokrok5(c.mokrok) { h in
                        NavigationLink(value: GilHwamyeon.mokjeok(h.jangso)) {
                            Text("\(h.ireum)" + (h.beon > 1 ? " — \(h.beon)번째" : "") + (h.juso.isEmpty ? "" : " — \(h.juso)"))
                        }
                        .buttonStyle(KeunDanchu())
                    }
                    if jiuneun {
                        Button("예, 모두 지웁니다") { c.jiugi(); jiuneun = false; SoriEngine.shared.mal("요즘 찾으신 곳을 모두 지웠습니다.") }
                            .buttonStyle(KeunDanchu())
                        Button("아니요, 그대로 둡니다") { jiuneun = false }
                            .buttonStyle(KeunDanchu())
                    } else {
                        Button("이 목록 지우기") { jiuneun = true; SoriEngine.shared.mal("요즘 찾으신 곳을 모두 지울까요? 바로 아래에서 고르십시오.") }
                            .buttonStyle(KeunDanchu())
                    }
                }
                Text("서른 곳까지 이 폰 안에만 담기고 밖으로 나가지 않습니다.").font(.body)
            }
            .padding()
        }
        .sokHwamyeon("요즘 찾으신 곳")
    }
}

// MARK: 교통편 — 기차·고속버스·시외버스·여객선의 편(웹 gyotong.html 0.2)

struct GyotongYeok: Identifiable, Hashable { let id: String; let ireum: String; let dosi: String }

struct GyotongView: View {
    private static let gal: [(String, String)] = [("gicha", "기차"), ("gosok", "고속버스"), ("sioe", "시외버스"), ("bae", "여객선")]
    @State private var dan = 0           // 0 갈래 · 1 출발 · 2 도착 · 3 날짜 · 4 편
    @State private var g = ""
    @State private var junbi: [String: Bool] = [:]
    @State private var modu: [String: [GyotongYeok]] = [:]
    @State private var q = ""
    @State private var chatEun: [GyotongYeok] = []
    @State private var chul: GyotongYeok?
    @State private var doe: GyotongYeok?
    @State private var baeDo: [GyotongYeok] = []
    @State private var nal = ""
    @State private var nalMal = ""
    @State private var pyeon: [String] = []
    @State private var allim = "무엇을 타시겠습니까."
    @State private var batneun = false

    private var galIreum: String { GyotongView.gal.first { $0.0 == g }?.1 ?? "" }
    private var chulMal: String { g == "bae" ? "출항지" : (g == "gicha" ? "출발역" : "출발 터미널") }
    private var doMal: String { g == "bae" ? "도착지" : (g == "gicha" ? "도착역" : "도착 터미널") }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text(allim).font(.title3)
                switch dan {
                case 0:
                    ForEach(GyotongView.gal, id: \.0) { e in
                        Button(e.1 + (junbi[e.0] == false ? " (지금은 안 됩니다)" : "") + Seom.ttaeKkori(e.0 == "gosok" ? "sioe" : e.0)) { galGoreugi(e.0) }
                            .buttonStyle(KeunDanchu())
                    }
                    Text("시내버스는 목적지를 고른 화면의 버스로 가기에서 찾습니다.").font(.body)
                case 1, 2:
                    if dan == 2 && g == "bae" {
                        Mokrok5(baeDo) { y in Button(y.ireum) { doe = y; nalGoreugi() }.buttonStyle(KeunDanchu()) }
                    } else {
                        TextField((dan == 1 ? chulMal : doMal) + " 이름으로 찾기 — 예: " + (g == "bae" ? "목포" : (dan == 1 ? "서울" : "부산")), text: $q)
                            .textFieldStyle(.roundedBorder).font(.title3).submitLabel(.search)
                            .onSubmit { ireumChatgi() }
                        Button("이 이름으로 찾기") { ireumChatgi() }.buttonStyle(KeunDanchu())
                        Mokrok5(chatEun) { y in
                            Button(y.ireum + (y.dosi.isEmpty ? "" : " — \(y.dosi)")) {
                                if dan == 1 { chul = y; doGoreugi() } else { doe = y; nalGoreugi() }
                            }
                            .buttonStyle(KeunDanchu())
                        }
                    }
                    if dan == 2 { Button("\(chulMal) 다시 고르기") { chulGoreugi() }.buttonStyle(KeunDanchu()) }
                case 3:
                    ForEach([("오늘", 0), ("내일", 1), ("모레", 2)], id: \.1) { p in
                        Button("\(p.0) \(GyotongView.nalTtae(p.1).1)") {
                            nal = GyotongView.nalTtae(p.1).0; nalMal = "\(p.0) \(GyotongView.nalTtae(p.1).1)"; pyeonBogi()
                        }
                        .buttonStyle(KeunDanchu())
                    }
                    Button("\(doMal) 다시 고르기") { doGoreugi() }.buttonStyle(KeunDanchu())
                default:
                    Mokrok5(pyeon.enumerated().map { GyotongPyeon(id: $0.offset, mal: $0.element) }) { p in
                        Button(p.mal) { SoriEngine.shared.mal(p.mal) }.buttonStyle(KeunDanchu())
                    }
                    yemaeDanchu
                    Button("다른 날 고르기") { nalGoreugi() }.buttonStyle(KeunDanchu())
                }
                if dan > 0 { Button("처음부터 — 탈것 다시 고르기") { cheoeum() }.buttonStyle(KeunDanchu()) }
            }
            .padding()
        }
        .sokHwamyeon("교통편")
        .task { if junbi.isEmpty { sangtaeBatgi() } }
    }

    private struct GyotongPyeon: Identifiable { let id: Int; let mal: String }

    @ViewBuilder private var yemaeDanchu: some View {
        if g == "gicha", let c = chul, let d = doe,
           let u = URL(string: "https://www.korail.com/ticket/search/general?txtGoStart=\(c.ireum.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")&txtGoEnd=\(d.ireum.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")") {
            Link("코레일에서 이 구간 예매하기 — 출발역과 도착역이 채워진 채 열립니다. 날짜와 열차를 고르고 로그인해 결제하십시오", destination: u).buttonStyle(KeunDanchu())
        } else if g == "gosok", let u = URL(string: "https://www.kobus.co.kr/main.do") {
            Link("고속버스 예매 누리집 열기 — 코버스", destination: u).buttonStyle(KeunDanchu())
        } else if g == "sioe", let u = URL(string: "https://www.bustago.or.kr/") {
            Link("시외버스 예매 누리집 열기 — 버스타고", destination: u).buttonStyle(KeunDanchu())
        } else if g == "bae", let u = URL(string: "https://island.haewoon.co.kr/") {
            Link("여객선 예매 누리집 열기 — 가보고싶은섬", destination: u).buttonStyle(KeunDanchu())
        }
    }

    private func mal(_ t: String) { allim = t; SoriEngine.shared.mal(t) }

    private static func nalTtae(_ d: Int) -> (String, String) {
        let t = Calendar.current.date(byAdding: .day, value: d, to: Date()) ?? Date()
        let c = Calendar.current.dateComponents([.year, .month, .day], from: t)
        return (String(format: "%04d%02d%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0), "\(c.month ?? 0)월 \(c.day ?? 0)일")
    }

    private func cheoeum() { dan = 0; g = ""; chul = nil; doe = nil; chatEun = []; q = ""; mal("무엇을 타시겠습니까. 기차, 고속버스, 시외버스, 여객선 가운데 고르십시오.") }

    private func sangtaeBatgi() {
        Task {
            let o = await Chatgi.json("gyotong.php", ["a": "sangtae"])
            await MainActor.run {
                var m: [String: Bool] = [:]
                for r in (o?["rows"] as? [[String: Any]]) ?? [] { if let k = r["gal"] as? String { m[k] = (r["junbi"] as? Bool) ?? ((r["junbi"] as? NSNumber)?.boolValue ?? false) } }
                junbi = m
                mal("무엇을 타시겠습니까. 기차, 고속버스, 시외버스, 여객선 가운데 고르십시오. 시내버스는 버스로 가기에서 찾습니다.")
            }
        }
    }

    private func galGoreugi(_ k: String) {
        if junbi[k] == false { mal("\(GyotongView.gal.first { $0.0 == k }?.1 ?? "")는 지금 창구가 준비되지 않았습니다."); return }
        g = k; chulGoreugi()
    }

    private func moduBatgi(_ kkeut: @escaping ([GyotongYeok]) -> Void) {
        if let m = modu[g] { kkeut(m); return }
        mal("\(galIreum) 역 목록을 받는 중입니다.")
        let gg = g
        Task {
            let o = await Chatgi.json("gyotong.php", ["a": "modu", "gal": gg])
            await MainActor.run {
                let l = ((o?["rows"] as? [[String: Any]]) ?? []).compactMap { r -> GyotongYeok? in
                    guard let id = r["id"] as? String, let n = r["ireum"] as? String else { return nil }
                    return GyotongYeok(id: id, ireum: n, dosi: (r["dosi"] as? String) ?? "")
                }
                if l.isEmpty { mal("목록을 받지 못했습니다. 처음부터 다시 해 주십시오."); return }
                modu[gg] = l
                kkeut(l)
            }
        }
    }

    private func chulGoreugi() {
        dan = 1; q = ""; chatEun = []
        moduBatgi { _ in mal("\(galIreum) \(chulMal) 이름을 적고 엔터를 치십시오.") }
    }

    private func ireumChatgi() {
        let k = q.replacingOccurrences(of: " ", with: "")
        guard !k.isEmpty, let rows = modu[g] else { mal("이름을 적어 주십시오."); return }
        let a = rows.filter { $0.ireum.replacingOccurrences(of: " ", with: "").hasPrefix(k) }
        let b = rows.filter { let n = $0.ireum.replacingOccurrences(of: " ", with: ""); return (!n.hasPrefix(k) && n.contains(k)) || $0.dosi.contains(k) }
        var f = Array((a + b).prefix(40))
        if dan == 2, let c = chul { f.removeAll { $0.id == c.id } }
        chatEun = f
        let m = dan == 1 ? chulMal : doMal
        mal(f.isEmpty ? "그런 이름의 \(m)을 찾지 못했습니다. 다른 이름으로 적어 보십시오." : "\(f.count)곳을 찾았습니다. 이름을 누르시면 \(m)로 정해집니다.")
    }

    private func doGoreugi() {
        dan = 2; q = ""; chatEun = []
        guard let c = chul else { return }
        if g == "bae" {
            mal("\(c.ireum)에서 출항합니다. 도착지 목록을 받는 중입니다.")
            Task {
                let o = await Chatgi.json("gyotong.php", ["a": "yeok", "gal": "bae", "chul": c.id])
                await MainActor.run {
                    baeDo = ((o?["rows"] as? [[String: Any]]) ?? []).compactMap { r -> GyotongYeok? in
                        guard let id = r["id"] as? String, let n = r["ireum"] as? String, id != c.id else { return nil }
                        return GyotongYeok(id: id, ireum: n, dosi: "")
                    }
                    mal(baeDo.isEmpty ? "\(c.ireum)에서 오늘 가는 배가 없습니다." : "\(c.ireum)에서 가는 곳이 \(baeDo.count)곳입니다. 도착지를 누르십시오.")
                }
            }
            return
        }
        mal("\(c.ireum) 출발. 이제 \(doMal) 이름을 적고 엔터를 치십시오.")
    }

    private func nalGoreugi() {
        dan = 3
        mal("\(chul?.ireum ?? "")에서 \(doe?.ireum ?? "")까지. 언제 가십니까.")
    }

    private static func ttaeMal(_ s: String) -> String {
        guard s.count >= 12 else { return "" }
        let a = Array(s)
        let h = Int(String(a[8...9])) ?? 0, m = String(a[10...11])
        return "\(h)시 \(m)분"
    }

    private func pyeonBogi() {
        guard let c = chul, let d = doe, !batneun else { return }
        batneun = true
        mal("\(nalMal) \(c.ireum)에서 \(d.ireum) 가는 \(galIreum) 편을 찾는 중입니다.")
        let gg = g
        Task {
            let o = await Chatgi.json("gyotong.php", ["a": "pyeon", "gal": gg, "chul": c.id, "do": d.id, "nal": nal])
            await MainActor.run {
                batneun = false
                let rows = (o?["rows"] as? [[String: Any]]) ?? []
                func s(_ r: [String: Any], _ k: String) -> String { (r[k] as? String) ?? ((r[k] as? NSNumber)?.stringValue ?? "") }
                pyeon = rows.map { x in
                    if gg == "bae" { return s(x, "mal") }
                    if gg == "gicha" {
                        let ch = s(x, "adultcharge")
                        return "\(GyotongView.ttaeMal(s(x, "depplandtime"))) \(s(x, "traingradename")) \(s(x, "depplacename")) 출발, \(GyotongView.ttaeMal(s(x, "arrplandtime"))) \(s(x, "arrplacename")) 도착" + (ch.isEmpty ? "" : ", 어른 \(ch)원")
                    }
                    let ch = s(x, "charge"), gr = s(x, "gradeNm")
                    return "\(GyotongView.ttaeMal(s(x, "depPlandTime"))) \(s(x, "depPlaceNm")) 출발, \(GyotongView.ttaeMal(s(x, "arrPlandTime"))) \(s(x, "arrPlaceNm")) 도착" + (gr.isEmpty ? "" : ", \(gr)") + (ch.isEmpty ? "" : ", \(ch)원")
                }
                dan = 4
                if pyeon.isEmpty { mal("\(nalMal) \(c.ireum)에서 \(d.ireum) 가는 \(galIreum)가 없습니다.") }
                else { mal("\(nalMal) \(c.ireum)에서 \(d.ireum) 가는 \(galIreum) \(pyeon.count)편. 첫 편은 \(pyeon[0]). 편을 누르시면 다시 읽어 드립니다.") }
            }
        }
    }
}
