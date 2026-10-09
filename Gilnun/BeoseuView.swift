// 버스로 가기 — 가까운 정류장, 오는 버스와 몇 분 뒤인지(저상 여부), 정류장까지 걷는 안내, 탄 뒤 차 안 안내.
// 그리고 탈것 바로잡기 — 길눈이 빠르기로 알아챈 탈것이 틀리면 이용자가 바로잡음(바로잡은 것이 가장 앞섬, 2026-09-26 이사장님).
// 나스 자료 창고: beoseu.php a=gakkaun(가까운 정류장), a=douchak(도착 시간, 서울시 정류소정보)
import SwiftUI

struct Jeongryujang: Hashable, Codable, Identifiable {
    var no: String
    var ireum: String
    var lat: Double
    var lon: Double
    var id: String { no + ireum }
}

/// 버스로 가는 여정이면 그 정류장
struct BeoseuGil: Codable {
    var jeongryujang: Jeongryujang
    var dochak: Bool
}

enum Beoseu {
    static func gakkaun(_ lat: Double, _ lon: Double) async -> [Jeongryujang]? {
        guard let o = await Chatgi.json("beoseu.php", ["a": "gakkaun", "lat": String(format: "%.6f", lat), "lon": String(format: "%.6f", lon)]) else { return nil }
        let l = (o["jrs"] as? [[String: Any]]) ?? []
        return l.compactMap { r in
            guard let la = Chatgi.su(r["lat"]), let lo = Chatgi.su(r["lon"]) else { return nil }
            let no = (r["seoul_no"] as? String) ?? (r["beonho"] as? String) ?? ""
            let nm = (r["nm"] as? String) ?? (r["name"] as? String) ?? ""
            return Jeongryujang(no: no, ireum: nm, lat: la, lon: lo)
        }
    }

    /// 오는 버스 — 말로 된 한 덩이(못 받으면 까닭)
    static func douchak(_ j: Jeongryujang) async -> String {
        var q = ["a": "douchak", "lat": String(format: "%.6f", j.lat), "lon": String(format: "%.6f", j.lon), "nm": j.ireum]
        if !j.no.isEmpty { q["no"] = j.no }
        guard let d = try? await Tongsin.shared.get("beoseu.php", q), !d.badadunGeot,
              let o = try? JSONSerialization.jsonObject(with: d.data) as? [String: Any] else {
            return "오는 버스를 받지 못했습니다. 통신이 끊겼을 수 있습니다."
        }
        if (o["ok"] as? Bool) == true, let m = o["mal"] as? String, !m.isEmpty { return m }
        return (o["msg"] as? String) ?? "이 정류장의 도착 정보를 받지 못했습니다."
    }

    /// 목록 한 줄 — 이름, 시계 방향과 거리, 정류장 번호(한 자씩)
    static func julMal(_ j: Jeongryujang) -> String {
        var m = j.ireum + " — " + sigyeMal(j)
        if !j.no.isEmpty {
            let beonho = j.no.map { String($0) }.joined(separator: " ")
            m += ", 정류장 번호 " + beonho
        }
        return m
    }

    static func sigyeMal(_ j: Jeongryujang) -> String {
        guard let w = WichiEngine.shared.jigeum else { return "" }
        let d = WichiEngine.geori(w.lat, w.lon, j.lat, j.lon)
        let apjjok = WichiEngine.shared.nachimban
        let s = apjjok >= 0 ? WichiEngine.sigyeBanghyang(jeongmyeon: apjjok, mokpyo: WichiEngine.bangwi(w.lat, w.lon, j.lat, j.lon)) : 0
        return (s == 0 ? "" : "\(s)시 방향 ") + Annae.geoMal(d)
    }
}

/// 가까운 정류장 목록
struct BeoseuChatgiView: View {
    let mok: Jangso
    @State private var mokrok: [Jeongryujang]?
    @State private var mot = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let l = mokrok {
                    if l.isEmpty {
                        Text("가까운 정류장을 찾지 못했습니다. 뒤로 가셔서 다른 길을 고르십시오.").font(.title3)
                    } else {
                        Mokrok5(l) { j in
                            NavigationLink(value: GilHwamyeon.jeongryujang(mok, j)) {
                                Text(Beoseu.julMal(j))
                            }
                            .buttonStyle(KeunDanchu())
                        }
                    }
                } else if mot {
                    Button("정류장을 받지 못했습니다. 통신이 끊겼을 수 있습니다 — 다시 찾기") { chatgi() }
                        .buttonStyle(KeunDanchu())
                } else {
                    Text("가까운 정류장을 찾는 중입니다.").font(.title3)
                }
            }
            .padding()
        }
        .sokHwamyeon("")
        .task { if mokrok == nil { chatgi() } }
    }

    private func chatgi() {
        mot = false
        guard let w = WichiEngine.shared.jigeum else {
            mot = true
            SoriEngine.shared.mal("아직 위치를 잡는 중입니다. 잠시 뒤 다시 찾기를 눌러 주십시오.")
            return
        }
        Task {
            let r = await Beoseu.gakkaun(w.lat, w.lon)
            await MainActor.run { if let r = r { mokrok = r } else { mot = true } }
        }
    }
}

/// 정류장 하나 — 오는 버스, 걸어가기, 이미 탔습니다
struct JeongryujangView: View {
    let mok: Jangso
    let j: Jeongryujang
    @State private var douchak = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Button("\(j.ireum)까지 걸어가기 — 정류장까지 걷는 안내 시작") {
                    Jeulgyeo.shared.sseum(mok)
                    AnnaeEngine.shared.beoseuGagi(mok, j)
                    GilGil.shared.cheotHwamyeon()
                }
                .buttonStyle(KeunDanchu())
                Button("이미 버스에 탔습니다 — 차 안 안내 시작") {
                    Jeulgyeo.shared.sseum(mok)
                    AnnaeEngine.shared.beoseuGagi(mok, j)
                    AnnaeEngine.shared.beoseuTatda()
                    GilGil.shared.cheotHwamyeon()
                }
                .buttonStyle(KeunDanchu())
                Button("오는 버스 다시 듣기") { deutgi() }
                    .buttonStyle(KeunDanchu())
                Text(douchak.isEmpty ? "오는 버스를 알아보는 중입니다." : douchak).font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("")
        .task { if douchak.isEmpty { deutgi() } }
    }

    private func deutgi() {
        Task {
            let m = await Beoseu.douchak(j)
            await MainActor.run {
                douchak = m
                SoriEngine.shared.mal(m, .jeongbo)
            }
        }
    }
}

/// 탈것 바로잡기 — 바로잡은 것이 가장 앞섭니다
struct TalgeotView: View {
    @Environment(\.dismiss) private var dismiss
    private let goreul: [Talgeot] = [.cha, .beoseu, .jihacheol, .gicha, .gosokbeoseu]   // 2.61.0 (261009-I15, 이사장님 승인 2026-10-09) 지하철 더함

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                ForEach(goreul, id: \.self) { t in
                    Button("\(t.ireum)입니다") {
                        AnnaeEngine.shared.talgeotBarojapgi(t)
                        dismiss()
                    }
                    .buttonStyle(KeunDanchu())
                }
            }
            .padding()
        }
        .sokHwamyeon("지금 타신 것")
    }
}
