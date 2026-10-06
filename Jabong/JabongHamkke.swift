// 함께한 기록판·응원 박수 — 자봉 앱 2.8.0 (261006-I3, 이사장님 승인 2026-10-06)
// 뒷단은 리눅스 서버(lvd-jabong 일꾼), 나스 /jabong/hamkke.php 가 건네줌. 서버가 쉬면 조용히 넘어가고, 기록판은 쉬는 중이라 알림.
import SwiftUI

struct Girokpan {
    let gilModu: Int
    let nasGil: Int
    let geoMo: Int
    let gilJu: Int
    let geoJu: Int
    let top: [(String, Int)]
    let baksuModu: Int
}

enum Hamkke {
    private static func juso(_ a: String, _ q: [(String, String)]) -> URL? {
        var c = URLComponents(string: "https://lvd.ada.or.kr/jabong/hamkke.php")
        c?.queryItems = [URLQueryItem(name: "a", value: a)] + q.map { URLQueryItem(name: $0.0, value: $0.1) }
            + [URLQueryItem(name: "_", value: String(Int(Date().timeIntervalSince1970 * 1000)))]
        return c?.url
    }

    private static func bureugi(_ a: String, _ q: [(String, String)] = [], bon: [String: Any]? = nil) async -> [String: Any]? {
        guard let u = juso(a, q) else { return nil }
        var r = URLRequest(url: u)
        r.timeoutInterval = 10
        r.cachePolicy = .reloadIgnoringLocalCacheData
        if let b = bon {
            r.httpMethod = "POST"
            r.httpBody = try? JSONSerialization.data(withJSONObject: b)
            r.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        guard let dr = try? await URLSession.shared.data(for: r),
              (dr.1 as? HTTPURLResponse)?.statusCode == 200,
              let j = (try? JSONSerialization.jsonObject(with: dr.0)) as? [String: Any],
              (j["ok"] as? Bool) == true else { return nil }
        return j
    }

    static func su(_ v: Any?) -> Int {
        if let n = v as? NSNumber { return n.intValue }
        if let s = v as? String { return Int(s) ?? 0 }
        return 0
    }

    /// 다 그린 길 알리기(기록판에 셈) — 서버가 쉬면 조용히 넘어감
    static func geurimAllim(gil: String, geori: Int) {
        let b = JabongNae.shared.beonho
        guard !b.isEmpty else { return }
        Task { _ = await bureugi("geurim", bon: ["gil": gil, "beonho": b, "byeol": "자봉 \(b)", "geori": geori]) }
    }

    /// 응원 박수 보내기 — (지금 박수 수, 이미 보냈는지). 한 사람이 한 글에 한 번
    static func baksuChigi(_ id: String) async -> (Int, Bool)? {
        let b = JabongNae.shared.beonho
        guard !b.isEmpty, let j = await bureugi("baksu", bon: ["id": id, "gigi": b]) else { return nil }
        return (su(j["su"]), (j["imi"] as? Bool) ?? false)
    }

    static func baksuSu(_ ids: [String]) async -> [String: Int] {
        guard !ids.isEmpty, let j = await bureugi("baksu", [("ids", ids.joined(separator: ","))]),
              let s = j["su"] as? [String: Any] else { return [:] }
        return s.mapValues { su($0) }
    }

    static func girokpan() async -> Girokpan? {
        guard let j = await bureugi("girokpan") else { return nil }
        let top = ((j["ibeonju_jabong"] as? [[String: Any]]) ?? []).map { (Nas.gul($0["byeol"]), su($0["su"])) }
        return Girokpan(gilModu: su(j["gil_modu"]), nasGil: su(j["nas_gil"]), geoMo: su(j["geori_modu"]),
                        gilJu: su(j["gil_ibeonju"]), geoJu: su(j["geori_ibeonju"]), top: top, baksuModu: su(j["baksu_modu"]))
    }

    /// 이번 주 이름표(모든 자봉님께 보내는 주간 박수에 씀)
    static var ibeonju: String {
        let c = Calendar(identifier: .iso8601)
        let d = c.dateComponents([.yearForWeekOfYear, .weekOfYear], from: Date())
        return "modu-\(d.yearForWeekOfYear ?? 0)-\(d.weekOfYear ?? 0)"
    }
}

/// 크게 보이는 기록 한 칸 — 이름표와 숫자를 한 번에 읽음
struct GirokKan: View {
    let ireum: String
    let gap: String
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(ireum).font(.headline).foregroundColor(.secondary)
            Text(gap).font(.title.bold()).foregroundColor(Saek.nam).fixedSize(horizontal: false, vertical: true)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 16).fill(Saek.norang.opacity(0.18)))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Saek.nam, lineWidth: 2))
        .accessibilityElement(children: .combine)
    }
}

// MARK: 함께한 기록판
struct JbGirokpanView: View {
    @State private var g: Girokpan?
    @State private var mot = false
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if let g = g {
                    GirokKan(ireum: "모두 그린 길", gap: "\(g.gilModu + g.nasGil)개").accessibilityFocused($chojeom)
                    GirokKan(ireum: "이번 주 함께 그린 길", gap: "\(g.gilJu)개, 약 \(g.geoJu)미터")
                    GirokKan(ireum: "이번 주 가장 많이 그려 주신 분", gap: topMal(g))
                    GirokKan(ireum: "모두 보낸 응원 박수", gap: "\(g.baksuModu)번")
                    Button("이번 주 모든 자봉님께 응원 박수 보내기") { modu() }.buttonStyle(KeunDanchu())
                } else if mot {
                    Button("기록판이 잠시 쉬고 있습니다 — 다시 불러오기") { bulreogi() }.buttonStyle(KeunDanchu())
                } else {
                    Text("기록판을 불러오고 있습니다.").font(.title2)
                }
            }
            .padding()
        }
        .sokHwamyeon("함께한 기록판")
        .onAppear { bulreogi() }
    }

    private func bulreogi() {
        mot = false
        Task {
            let r = await Hamkke.girokpan()
            await MainActor.run {
                if let r = r {
                    g = r
                    var m = "모두 그린 길 \(r.gilModu + r.nasGil)개. 이번 주 함께 그린 길 \(r.gilJu)개, 약 \(r.geoJu)미터."
                    if let t = r.top.first { m += " 이번 주 가장 많이 그려 주신 분은 \(t.0)님, \(t.1)개입니다." }
                    SoriEngine.shared.mal(m)
                    chojeom = false
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { chojeom = true }
                } else {
                    mot = true
                    SoriEngine.shared.mal("기록판이 잠시 쉬고 있습니다. 조금 뒤에 다시 불러 주십시오.")
                }
            }
        }
    }

    private func topMal(_ g: Girokpan) -> String {
        if g.top.isEmpty { return "이번 주 첫 길의 주인공이 되어 주십시오!" }
        var jul: [String] = []
        for (i, x) in g.top.enumerated() { jul.append("\(i + 1)등 \(x.0), \(x.1)개") }
        return jul.joined(separator: "\n")
    }

    private func modu() {
        Task {
            let r = await Hamkke.baksuChigi(Hamkke.ibeonju)
            await MainActor.run {
                guard let r = r else { SoriEngine.shared.mal("박수를 보내지 못했습니다. 잠시 뒤 다시 눌러 주십시오."); return }
                if r.1 { SoriEngine.shared.mal("이번 주에는 이미 박수를 보내셨습니다. 이번 주 모두 \(r.0)번입니다.") }
                else { Baksu.chigi(keuge: true); SoriEngine.shared.mal("모든 자봉님께 응원 박수를 보냈습니다. 이번 주 모두 \(r.0)번입니다.") }
                bulreogi()
            }
        }
    }
}

// MARK: 걸음 나눔(자봉 앱) — 다섯 개씩, 글마다 응원 박수, 한마디 적기
struct JbGeoreumNanumView: View {
    @ObservedObject private var nae = JabongNae.shared
    @State private var mok: [NanumGeul]?
    @State private var modu = 0
    @State private var bu = 0
    @State private var mot = false
    @State private var baksu: [String: Int] = [:]
    @State private var sseulGeul = ""
    @State private var pyeol = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                DisclosureGroup("한마디 적기 펼치기", isExpanded: $pyeol) {
                    VStack(alignment: .leading, spacing: 10) {
                        TextField("봉사 이야기나 응원 한마디", text: $sseulGeul)
                            .font(.title2).padding(12)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Saek.nam, lineWidth: 2))
                        Button("올리기") { olligi() }.buttonStyle(KeunDanchu())
                    }
                }
                .font(.title3)
                if let l = mok {
                    if l.isEmpty { Text("아직 이야기가 없습니다. 첫 한마디를 남겨 주십시오.").font(.title2) }
                    ForEach(l) { g in geulKadeu(g) }
                    if (bu + 1) * 5 < modu { Button("더 보기") { bu += 1; bulreogi() }.buttonStyle(KeunDanchu()) }
                    if bu > 0 { Button("이전 보기") { bu -= 1; bulreogi() }.buttonStyle(KeunDanchu()) }
                } else if mot {
                    Button("불러오지 못했습니다 — 다시 불러오기") { bulreogi() }.buttonStyle(KeunDanchu())
                } else {
                    Text("이야기를 불러오고 있습니다.").font(.title2)
                }
            }
            .padding()
        }
        .sokHwamyeon("걸음 나눔")
        .onAppear { if mok == nil { bulreogi() } }
    }

    /// 글 한 줄 — 머리, 글, 댓글(컴파일러가 빨리 읽도록 나눠 씀)
    private func julMal(_ g: NanumGeul) -> String {
        var t: String = g.meori + ". " + g.geul
        if !g.dat.isEmpty {
            let d: [String] = g.dat.map { (x: NanumDat) -> String in x.byeol + ", " + x.geul + "." }
            t += " 댓글 \(g.dat.count)개: " + d.joined(separator: " ")
        }
        return t
    }

    private func geulKadeu(_ g: NanumGeul) -> some View {
        let su: Int = baksu["nn:" + g.id] ?? 0
        return VStack(alignment: .leading, spacing: 8) {
            Text(julMal(g)).font(.title3).fixedSize(horizontal: false, vertical: true)
            Button("응원 박수 — 지금 \(su)번") { baksuChigi(g.id) }.buttonStyle(KeunDanchu())
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 16).fill(Saek.norang.opacity(0.18)))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Saek.nam, lineWidth: 2))
    }

    private func bulreogi() {
        mot = false
        Task {
            let r = await GeoreumNanum.mok(bu)
            await MainActor.run {
                if let r = r { mok = r.0; modu = r.1 } else { mok = nil; mot = true; SoriEngine.shared.mal("불러오지 못했습니다.") }
            }
            if let l = r?.0 {
                let s = await Hamkke.baksuSu(l.map { "nn:" + $0.id })
                await MainActor.run { baksu.merge(s) { $1 } }
            }
        }
    }

    private func baksuChigi(_ id: String) {
        Task {
            let r = await Hamkke.baksuChigi("nn:" + id)
            await MainActor.run {
                guard let r = r else { SoriEngine.shared.mal("박수를 보내지 못했습니다. 잠시 뒤 다시 눌러 주십시오."); return }
                baksu["nn:" + id] = r.0
                if r.1 { SoriEngine.shared.mal("이미 박수를 보내셨습니다. 지금 \(r.0)번입니다.") }
                else { Baksu.chigi(); SoriEngine.shared.mal("응원 박수를 보냈습니다. 지금 \(r.0)번입니다.") }
            }
        }
    }

    private func olligi() {
        let g = sseulGeul.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !g.isEmpty else { SoriEngine.shared.mal("한마디를 적어 주십시오."); return }
        Task {
            let j = await Nas.postJson("nanum.php", [("a", "sseugi")], ["byeol": "자봉 \(nae.beonho)", "geul": g, "jam": Yeolsoe.ilgi("jbJam") ?? "", "mun": "jabong"])
            await MainActor.run {
                if (j?["ok"] as? Bool) == true {
                    Baksu.chigi()
                    SoriEngine.shared.mal("올렸습니다. 고맙습니다!")
                    sseulGeul = ""; pyeol = false; bu = 0
                    bulreogi()
                } else {
                    SoriEngine.shared.mal((j?["msg"] as? String) ?? "올리지 못했습니다.")
                }
            }
        }
    }
}
