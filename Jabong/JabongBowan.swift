// 자봉 앱 — 보완 부탁(2.13.0, 빌드 261007-I7, 대장클, 이사장님 지시 2026-10-06·07)
// 올린 점지도가 협회 점검에서 흠이 나오면 「조금만 더 보완해 주세요. 시각장애인이 기다립니다」로 보완을 부탁합니다.
// 3일째 다시 알리고, 7일이 지나도 보완이 없으면 협회 보완팀이 맡습니다(그린 분 이름은 그대로, 보완한 분을 더함).
// 날짜 셈은 협회 리눅스 서버(lvd-bowan), 나스(bowan.php)는 건네기만. 같은 출발지·도착지로 다시 걸어 점검을 통과하면 보완 완료.
// 고칠 곳은 몇 걸음째·무엇·어떻게를 말과 빛깔(빨강·주황·노랑)로, 빨강은 깜박여 눈에 띄게(움직임 줄이기를 켜셨으면 깜박이지 않음).
import SwiftUI

struct BwHeum: Codable, Hashable {
    var st: Int
    var jong: String
    var mal: String
    var saek: String
}

struct BwGil: Decodable, Identifiable {
    var id: String
    var title: String
    var from: String
    var to: String
    var geurin: String
    var ttae: String
    var dangye: String?
    var mal: String?
    var nal: Int?
    var gihan: String?
    var heum: [BwHeum]
    var ireum: String { !title.isEmpty ? title : ((from.isEmpty && to.isEmpty) ? "이름 없는 길" : "\(from) → \(to)") }
}

private struct BwDap: Decodable { var ok: Bool; var msg: String?; var mok: [BwGil]? }

final class JbBowan: ObservableObject {
    static let shared = JbBowan()
    static let butak = "조금만 더 보완해 주세요. 시각장애인이 기다립니다."
    @Published private(set) var mok: [BwGil] = []
    @Published private(set) var allim = ""
    private var majimak = Date.distantPast

    var namun: [BwGil] { mok.filter { $0.dangye != "보완 완료" } }

    /// 협회에 내 보완 부탁을 물음 — 켤 때와 알림 탭을 열 때(1분에 한 번까지)
    @MainActor func bulleo(gangje: Bool = false) async {
        guard gangje || Date().timeIntervalSince(majimak) > 60 else { return }
        let nae = JabongNae.shared
        guard nae.deungrokham, let jam = Yeolsoe.ilgi("jbJam"),
              let u = URL(string: "https://lvd.ada.or.kr/jeom/bowan.php?a=nae") else { return }
        majimak = Date()
        var r = URLRequest(url: u, timeoutInterval: 15)
        r.httpMethod = "POST"
        r.setValue("application/json", forHTTPHeaderField: "Content-Type")
        r.httpBody = try? JSONSerialization.data(withJSONObject: ["beonho": nae.beonho, "jam": jam])
        guard let dr = try? await URLSession.shared.data(for: r), let j = try? JSONDecoder().decode(BwDap.self, from: dr.0) else {
            allim = "보완 부탁을 살피지 못했습니다. 통신을 확인해 주십시오."; return
        }
        guard j.ok else { allim = j.msg ?? "보완 부탁을 살피지 못했습니다."; return }
        mok = j.mok ?? []
        allim = ""
        dasiAllim()
    }

    /// 3일째(다시 알림)인 길은 하루에 한 번 말로 알려 드림
    private func dasiAllim() {
        let oneul = String(ISO8601DateFormatter().string(from: Date()).prefix(10))
        let ki = "jb.bowanAllim." + oneul
        let ap = UserDefaults.standard.string(forKey: ki) ?? ""
        let dasi = namun.filter { $0.dangye == "다시 알림" && !ap.contains($0.id) }
        guard !dasi.isEmpty else { return }
        UserDefaults.standard.set(ap + dasi.map { $0.id }.joined(separator: ","), forKey: ki)
        SoriEngine.shared.mal(JbBowan.butak + " 보완 부탁 \(dasi.count)건이 3일을 넘었습니다. 알림·설정 탭 맨 위에서 고칠 곳을 들으실 수 있습니다.", .annae)
    }

    /// 고칠 곳 말로 듣기 — 몇 걸음째, 무엇, 어떻게
    static func malHagi(_ g: BwGil) {
        SoriEngine.shared.modu_geodugi()
        SoriEngine.shared.mal("\(g.ireum). " + (g.mal ?? JbBowan.butak))
        for (i, h) in g.heum.enumerated() { SoriEngine.shared.mal("\(i + 1). \(h.saek). \(h.mal)") }
    }

    /// 2026-10-13 → 10월 13일
    static func nalMal(_ s: String) -> String {
        let p = s.split(separator: "-").compactMap { Int($0) }
        return p.count == 3 ? "\(p[1])월 \(p[2])일" : s
    }

    static func saek(_ s: String) -> Color {
        switch s {
        case "빨강": return Color(red: 0.80, green: 0.08, blue: 0.08)
        case "주황": return Color(red: 0.90, green: 0.45, blue: 0.0)
        case "노랑": return Color(red: 0.85, green: 0.70, blue: 0.0)
        default: return .primary
        }
    }
}

/// 고칠 곳 한 줄 — 빛깔 띠와 「빨강 —」 글자(낭독기로도 빛깔을 들음), 빨강은 깜박임
struct HeumJul: View {
    let saek: String
    let mal: String
    @Environment(\.accessibilityReduceMotion) private var jurim
    @State private var kkam = false
    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            RoundedRectangle(cornerRadius: 4).fill(JbBowan.saek(saek)).frame(width: 12)
                .opacity(saek == "빨강" && kkam ? 0.25 : 1).accessibilityHidden(true)
            Text("\(saek) — \(mal)").font(.title3).foregroundColor(.primary).fixedSize(horizontal: false, vertical: true)
        }
        .padding(10)
        .background(RoundedRectangle(cornerRadius: 10).fill(JbBowan.saek(saek).opacity(0.12)))
        .accessibilityElement(children: .combine)
        .onAppear {
            guard saek == "빨강", !jurim else { return }
            withAnimation(.easeInOut(duration: 0.7).repeatForever(autoreverses: true)) { kkam = true }
        }
    }
}

/// 알림·설정 탭 맨 위 — 내 보완 부탁(있을 때만)
struct BowanKan: View {
    @ObservedObject private var b = JbBowan.shared
    var body: some View {
        Group {
            if !b.allim.isEmpty { Text(b.allim).font(.body) }
            if !b.namun.isEmpty {
                Text("보완 부탁 \(b.namun.count)건 — \(JbBowan.butak)").font(.title3.bold()).foregroundColor(JbBowan.saek("빨강"))
                Mokrok5(b.namun) { g in
                    NavigationLink { BowanGilView(gil: g) } label: {
                        Text("\(g.ireum) — \(g.dangye ?? "보완 대기")" + (g.gihan.map { ", 기한 " + JbBowan.nalMal($0) } ?? ""))
                    }.buttonStyle(KeunDanchu())
                }
            }
        }
        .task { await b.bulleo() }
    }
}

/// 보완 부탁 한 건 — 무엇을 어떻게 고칠지
struct BowanGilView: View {
    let gil: BwGil
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Button("고칠 곳 말로 듣기") { JbBowan.malHagi(gil) }.buttonStyle(KeunDanchu())
                Text(gil.mal ?? JbBowan.butak).font(.title3.bold())
                Text("보완 부탁 날 " + JbBowan.nalMal(gil.ttae) + (gil.nal.map { ", \($0)일 지남" } ?? "")).font(.body)
                ForEach(Array(gil.heum.enumerated()), id: \.offset) { _, h in HeumJul(saek: h.saek, mal: h.mal) }
                Text("고치는 법: 같은 출발지와 도착지로 다시 걸어 그리고 올려 주십시오. 협회 점검을 통과하면 이 부탁은 저절로 보완 완료가 됩니다. 이름만 빠진 것은 그린 길 화면에서 이름을 넣고 다시 올리시면 됩니다.")
                    .font(.body).fixedSize(horizontal: false, vertical: true)
            }
            .padding()
        }
        .sokHwamyeon("보완 부탁")
    }
}

extension Array {
    /// 없는 자리를 물으면 nil — 고칠 곳 글과 빛깔 줄 수가 어긋나도 멈추지 않게
    subscript(safe i: Int) -> Element? { indices.contains(i) ? self[i] : nil }
}
