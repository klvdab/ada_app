// 음성유도기와 승강기 — 앱 2.14.0 (빌드 260929-9, 이사장님 승인). 웹 길눈 yudo.html 0.2판과 같은 자료(yudo.php, 서울교통공사).
// 지금 자리에서 가까운 지하철역 다섯 곳을 찾고, 역을 고르면 음성유도기·엘리베이터·에스컬레이터를 세 갈래로 나누어 알려 드립니다.
// 서울 1호선부터 8호선까지만 자료가 있습니다.
import SwiftUI

struct YudoYeok: Identifiable, Hashable {
    let ireum: String
    let ho: String
    let geori: Int
    var id: String { ireum + ho }
    var julMal: String { "\(ireum)역 \(ho)호선, \(geori)미터" }
}

struct YudoHang: Identifiable, Hashable {
    let id: Int
    let mal: String
}

struct YudoView: View {
    @State private var yeokDeul: [YudoYeok] = []
    @State private var sangtae = 0   // 0 찾는 중, 1 찾음, 2 못 찾음
    @State private var goreun: YudoYeok?
    @State private var yudo: [YudoHang] = []
    @State private var ev: [YudoHang] = []
    @State private var es: [YudoHang] = []
    @State private var yeokMal = ""
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let g = goreun {
                    Text(yeokMal.isEmpty ? "\(g.ireum)역 자료를 받는 중입니다." : yeokMal)
                        .font(.title3)
                        .accessibilityFocused($chojeom)
                    if !yudo.isEmpty {
                        DisclosureGroup("음성유도기 \(yudo.count)곳 펼치기") {
                            Mokrok5(yudo) { h in Text(h.mal).font(.title3) }
                        }
                        .font(.title3)
                    }
                    if !ev.isEmpty {
                        DisclosureGroup("엘리베이터 \(ev.count)대 펼치기") {
                            Mokrok5(ev) { h in Text(h.mal).font(.title3) }
                        }
                        .font(.title3)
                    }
                    if !es.isEmpty {
                        DisclosureGroup("에스컬레이터 \(es.count)대 펼치기") {
                            Mokrok5(es) { h in Text(h.mal).font(.title3) }
                        }
                        .font(.title3)
                    }
                    Button("다른 역 고르기") { goreun = nil; yeokMal = ""; chojeomOmgigi() }.buttonStyle(KeunDanchu())
                } else if sangtae == 0 {
                    Text("가까운 역을 찾고 있습니다.").font(.title3)
                } else if yeokDeul.isEmpty {
                    Button("가까운 역을 찾지 못했습니다 — 다시 찾기") { chatgi() }
                        .buttonStyle(KeunDanchu())
                        .accessibilityFocused($chojeom)
                } else {
                    Mokrok5(yeokDeul) { y in
                        Button(y.julMal) { yeokBoda(y) }.buttonStyle(KeunDanchu())
                    }
                    Button("가까운 역 다시 찾기") { chatgi() }.buttonStyle(KeunDanchu())
                }
                DisclosureGroup("알아 두실 것 펼치기") {
                    Text("지금 계신 자리에서 가까운 지하철역 다섯 곳을 찾습니다. 역을 고르시면 그 역의 음성유도기, 엘리베이터, 에스컬레이터를 세 갈래로 나누어 알려 드립니다. 음성유도기는 출구 계단이나 개찰구, 발매기 같은 곳에 붙어 있는 소리 나는 장치이며 어디에 있는지 그대로 적어 두었습니다. 엘리베이터는 몇 층에서 몇 층까지 다니는지와 몇 번 출구 쪽인지가 나옵니다. 서울교통공사가 내놓은 자료로, 서울 1호선부터 8호선까지만 들어 있습니다. 실제와 다를 수 있으니 다르면 역무원에게 도움을 청하십시오.")
                        .font(.body)
                }
                .font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("음성유도기와 승강기")
        .onAppear { if yeokDeul.isEmpty { chatgi() } }
    }

    private func chojeomOmgigi() {
        chojeom = false
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { chojeom = true }
    }

    private func chatgi() {
        sangtae = 0
        WichiEngine.shared.sijak()
        Task {
            var w = WichiEngine.shared.jigeum
            for _ in 0..<10 where w == nil {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                w = WichiEngine.shared.jigeum
            }
            guard let j = w else {
                await MainActor.run { sangtae = 1; SoriEngine.shared.mal("지금 자리를 알 수 없어 역을 찾지 못했습니다.") }
                return
            }
            let o = await YudoView.mutgi([("a", "gakkaun"), ("la", String(format: "%.6f", j.lat)), ("lo", String(format: "%.6f", j.lon))])
            let ls = ((o?["list"] as? [[String: Any]]) ?? []).compactMap { r -> YudoYeok? in
                guard let nm = r["ireum"] as? String else { return nil }
                return YudoYeok(ireum: nm, ho: (r["ho"] as? String) ?? "", geori: Int(Chatgi.su(r["geori"]) ?? 0))
            }
            await MainActor.run {
                yeokDeul = ls
                sangtae = 1
                SoriEngine.shared.mal(ls.isEmpty ? "가까운 역을 찾지 못했습니다." : "가까운 역 \(ls.count)곳입니다. 가장 가까운 곳은 \(ls[0].julMal)입니다.", .jeongbo)
                chojeomOmgigi()
            }
        }
    }

    private func yeokBoda(_ y: YudoYeok) {
        goreun = y
        yudo = []; ev = []; es = []; yeokMal = ""
        Task {
            async let a = YudoView.mutgi([("a", "yudo"), ("yeok", y.ireum)])
            async let b = YudoView.mutgi([("a", "seunggangi"), ("yeok", y.ireum)])
            let (o1, o2) = await (a, b)
            let yu = ((o1?["list"] as? [String]) ?? []).enumerated().map { YudoHang(id: $0.offset, mal: $0.element) }
            let e1 = ((o2?["elevator"] as? [String]) ?? []).enumerated().map { YudoHang(id: $0.offset, mal: YudoView.dadeum($0.element)) }
            let e2 = ((o2?["escalator"] as? [String]) ?? []).enumerated().map { YudoHang(id: $0.offset, mal: YudoView.dadeum($0.element)) }
            await MainActor.run {
                yudo = yu; ev = e1; es = e2
                var m = "\(y.ireum)역 — "
                if yu.isEmpty && e1.isEmpty && e2.isEmpty { m += "음성유도기와 승강기 자료가 없습니다." }
                else { m += "음성유도기 \(yu.count)곳, 엘리베이터 \(e1.count)대, 에스컬레이터 \(e2.count)대. 펼치기를 여시면 다섯 곳씩 읽어 드립니다." }
                yeokMal = m
                Girok.shared.namgi("yudo", ["yeok": y.ireum])
                SoriEngine.shared.mal(m, .jeongbo)
                chojeomOmgigi()
            }
        }
    }

    /// "승강기)엘리베이터-약수 3번 출구측 외부#1 (B1-1F)" → "3번 출구측 외부 1호, 지하1층에서 1층"
    static func dadeum(_ s: String) -> String {
        var t = s
        if let r = t.range(of: "^승강기\\)[^-]*-", options: .regularExpression) { t.removeSubrange(r) }
        t = t.replacingOccurrences(of: "#", with: " ")
        if let r = t.range(of: "\\(([A-Z0-9-]+)\\)\\s*$", options: .regularExpression) {
            let cheung = String(t[r]).trimmingCharacters(in: CharacterSet(charactersIn: "() "))
            let pul = cheung.components(separatedBy: "-").map { c -> String in
                if c.hasPrefix("BM") { return "지하 중간층" }
                if c.hasPrefix("B") { return "지하\(c.dropFirst())층" }
                if c.hasSuffix("F") { return String(c.dropLast()) + "층" }
                return c
            }
            t = String(t[..<r.lowerBound]).trimmingCharacters(in: .whitespaces) + ", " + (pul.count == 2 ? "\(pul[0])에서 \(pul[1])" : pul.joined(separator: ", ") + " 다님")
        }
        return t
    }

    private static func mutgi(_ q: [(String, String)]) async -> [String: Any]? {
        guard let u = BangsongEngine.juso("/jeom/yudo.php", q), let dr = try? await URLSession.shared.data(from: u) else { return nil }
        return (try? JSONSerialization.jsonObject(with: dr.0)) as? [String: Any]
    }
}
