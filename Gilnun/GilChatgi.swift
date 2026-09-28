// 길 찾기 탭 — 2단계 첫 묶음(목적지 찾기, 즐겨찾기, 걷는 안내, 차 안 안내, 도착)
// 화면 원칙: 여정이 있으면 맨 위 한 줄에 "지금 무슨 차례인지"와 그 차례의 단추를 한 자리에.
// 안내는 화면이 아니라 안내 엔진이 맡으므로, 화면을 떠나도, 음악을 틀어도, 폰이 잠겨도 이어집니다.
import SwiftUI

enum GilHwamyeon: Hashable {
    case gyeolgwa(String)
    case mokjeok(Jangso)
    case jeulgyeo
    case gicho
}

/// 길 찾기 탭의 길(화면 쌓임) — 목적지를 정하면 첫 화면으로 곧장 돌아가게
final class GilGil: ObservableObject {
    static let shared = GilGil()
    @Published var path = NavigationPath()
    func cheotHwamyeon() { path = NavigationPath() }
}

struct GilChatgiTab: View {
    @ObservedObject private var gil = GilGil.shared
    var body: some View {
        NavigationStack(path: $gil.path) {
            GilChatgiView()
                .navigationDestination(for: GilHwamyeon.self) { h in
                    switch h {
                    case .gyeolgwa(let q): ChatgiGyeolgwaView(mal: q)
                    case .mokjeok(let j): MokjeokView(j: j)
                    case .jeulgyeo: JeulgyeoView()
                    case .gicho: GichoSiheomView()
                    }
                }
        }
    }
}

struct GilChatgiView: View {
    @ObservedObject private var y = YeojeongEngine.shared
    @State private var mal = ""
    @AccessibilityFocusState private var meoriChojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                if let yj = y.jigeum {
                    YeojeongPan(yj: yj, chojeom: $meoriChojeom)
                } else {
                    TextField("어디로 가실까요 — 이름이나 주소를 넣고 엔터", text: $mal)
                        .textFieldStyle(.roundedBorder)
                        .font(.title3)
                        .submitLabel(.search)
                        .accessibilityFocused($meoriChojeom)
                        .onSubmit {
                            let q = mal.trimmingCharacters(in: .whitespaces)
                            if !q.isEmpty { GilGil.shared.path.append(GilHwamyeon.gyeolgwa(q)) }
                        }
                    NavigationLink(value: GilHwamyeon.jeulgyeo) { Text("즐겨찾기 — 자주 가는 곳") }
                        .buttonStyle(KeunDanchu())
                    Button("지금 내 자리 듣기") { AnnaeEngine.shared.jigeumJari() }
                        .buttonStyle(KeunDanchu())
                }
                DisclosureGroup("그 밖에 펼치기") {
                    VStack(alignment: .leading, spacing: 10) {
                        NavigationLink(value: GilHwamyeon.gicho) { Text("기초 시험") }
                            .buttonStyle(KeunDanchu())
                    }
                }
                .font(.title3)
            }
            .padding()
        }
        .toolbar(.hidden, for: .navigationBar)
        .onAppear {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { meoriChojeom = true }
        }
    }
}

/// 여정 머리 — 지금 차례 한 줄과 그 차례의 큰 단추를 한 자리에
struct YeojeongPan: View {
    let yj: Yeojeong
    var chojeom: AccessibilityFocusState<Bool>.Binding
    @ObservedObject private var a = AnnaeEngine.shared

    private var mok: String { yj.mokjeok.ireum }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            switch yj.danggye {
            case .namEunGil:
                Button("\(mok)까지 걸어가는 중 — 지금 어떻게 가고 있습니까") { a.hyeonhwang() }
                    .buttonStyle(KeunDanchu())
                    .accessibilityFocused(chojeom)
            case .taneunJung:
                Button("\(mok)까지 \(YeojeongEngine.shared.talgeot.ireum)로 가는 중 — 내렸습니다, 남은 길 걸어서 안내") {
                    a.naeryeotda()
                }
                .buttonStyle(KeunDanchu())
                .accessibilityFocused(chojeom)
                Button("지금 어떻게 가고 있습니까") { a.hyeonhwang() }
                    .buttonStyle(KeunDanchu())
            case .dochak:
                Button("\(mok)에 도착했습니다 — 여정 끝내기") { a.kkeut() }
                    .buttonStyle(KeunDanchu())
                    .accessibilityFocused(chojeom)
            default:
                Button("\(mok)까지 — 걸어가기, 걷는 안내 시작") { a.georeoGagi() }
                    .buttonStyle(KeunDanchu())
                    .accessibilityFocused(chojeom)
                Button("차에 탔습니다 — 차 안 안내 시작") { a.chaTatda() }
                    .buttonStyle(KeunDanchu())
            }
            if yj.danggye != .dochak {
                DisclosureGroup("여정 다른 할 일 펼치기") {
                    VStack(alignment: .leading, spacing: 10) {
                        if yj.danggye == .namEunGil {
                            Button("차에 탔습니다 — 차 안 안내로") { a.chaTatda() }.buttonStyle(KeunDanchu())
                        }
                        Button("지금 내 자리 듣기") { a.jigeumJari() }.buttonStyle(KeunDanchu())
                        Button("여정 끝내기 — 목적지를 바꾸실 때도") { a.kkeut() }.buttonStyle(KeunDanchu())
                    }
                }
                .font(.title3)
            }
        }
    }
}

/// 찾은 곳 목록 — 결과만 남기고 초점을 첫 줄로
struct ChatgiGyeolgwaView: View {
    let mal: String
    @State private var gyeolgwa: [Jangso]?
    @State private var mothbadeum = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let g = gyeolgwa {
                    if g.isEmpty {
                        Text("\(mal) — 찾지 못했습니다. 뒤로 가셔서 달리 적어 보십시오.").font(.title3)
                    } else {
                        Mokrok5(g) { j in
                            NavigationLink(value: GilHwamyeon.mokjeok(j)) {
                                Text("\(j.ireum) — \(j.juso)\(geoMal(j))")
                            }
                            .buttonStyle(KeunDanchu())
                        }
                    }
                } else if mothbadeum {
                    Button("찾기를 받지 못했습니다. 통신이 끊겼을 수 있습니다 — 다시 찾기") { chatgi() }
                        .buttonStyle(KeunDanchu())
                } else {
                    Text("\(mal) 찾는 중입니다.").font(.title3)
                }
            }
            .padding()
        }
        .sokHwamyeon("")
        .task { if gyeolgwa == nil { chatgi() } }
    }

    private func geoMal(_ j: Jangso) -> String {
        guard let w = WichiEngine.shared.jigeum else { return "" }
        return ", " + Annae.geoMal(WichiEngine.geori(w.lat, w.lon, j.lat, j.lon))
    }

    private func chatgi() {
        mothbadeum = false
        Task {
            let r = await Chatgi.jangso(mal)
            await MainActor.run {
                if let r = r { gyeolgwa = r } else { mothbadeum = true }
            }
        }
    }
}

/// 정한 곳 — 걸어가기, 차로 가기, 즐겨찾기에 담기
struct MokjeokView: View {
    let j: Jangso
    @ObservedObject private var jeul = Jeulgyeo.shared
    @State private var damam = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Button("\(j.ireum)\(geoMal) — 걸어가기, 걷는 안내 시작") {
                    jeul.sseum(j)
                    AnnaeEngine.shared.georeoGagi(j)
                    GilGil.shared.cheotHwamyeon()
                }
                .buttonStyle(KeunDanchu())
                Button("차로 가기 — 차에 탔거나 곧 탑니다, 차 안 안내 시작") {
                    jeul.sseum(j)
                    AnnaeEngine.shared.chaTagi(j)
                    GilGil.shared.cheotHwamyeon()
                }
                .buttonStyle(KeunDanchu())
                if damam || jeul.itna(j) {
                    Text("즐겨찾기에 담겨 있습니다.").font(.title3)
                } else {
                    Button("즐겨찾기에 담기") {
                        jeul.damgi(j)
                        damam = true
                        SoriEngine.shared.mal("즐겨찾기에 담았습니다.")
                    }
                    .buttonStyle(KeunDanchu())
                }
                Text("주소 — \(j.juso)").font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("")
    }

    private var geoMal: String {
        guard let w = WichiEngine.shared.jigeum else { return "" }
        return ", 여기서 " + Annae.geoMal(WichiEngine.geori(w.lat, w.lon, j.lat, j.lon))
    }
}

/// 즐겨찾기 — 줄에는 이름 하나, 지우기는 보이스오버 동작(위아래로 쓸기)으로
struct JeulgyeoView: View {
    @ObservedObject private var jeul = Jeulgyeo.shared
    @State private var ireum = ""
    @State private var allim = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if jeul.mokrok.isEmpty {
                    Text("아직 담은 곳이 없습니다. 목적지를 찾아 고르신 뒤 즐겨찾기에 담기를 누르시면 여기에 모입니다. 아래 펼치기에서 지금 자리를 담으실 수도 있습니다.")
                        .font(.title3)
                } else {
                    Mokrok5(jeul.mokrok) { h in
                        NavigationLink(value: GilHwamyeon.mokjeok(h.jangso)) {
                            Text("\(h.ireum) — \(h.juso)")
                        }
                        .buttonStyle(KeunDanchu())
                        .accessibilityAction(named: "즐겨찾기에서 지우기") {
                            jeul.jiugi(h)
                            SoriEngine.shared.mal("\(h.ireum), 즐겨찾기에서 지웠습니다.")
                        }
                        .contextMenu {
                            Button("즐겨찾기에서 지우기", role: .destructive) { jeul.jiugi(h) }
                        }
                    }
                }
                DisclosureGroup("지금 자리를 즐겨찾기에 담기 펼치기") {
                    VStack(alignment: .leading, spacing: 10) {
                        TextField("이름 — 예: 집, 사무실", text: $ireum)
                            .textFieldStyle(.roundedBorder)
                            .font(.title3)
                        Button("지금 자리를 이 이름으로 담기") { jigeumDamgi() }
                            .buttonStyle(KeunDanchu())
                        if !allim.isEmpty { Text(allim) }
                    }
                }
                .font(.title3)
            }
            .padding()
        }
        .sokHwamyeon("즐겨찾기")
    }

    private func jigeumDamgi() {
        let nm = ireum.trimmingCharacters(in: .whitespaces)
        guard !nm.isEmpty else { allim = "이름을 먼저 적어 주십시오."; SoriEngine.shared.mal(allim); return }
        guard let w = WichiEngine.shared.jigeum else { allim = "아직 위치를 잡는 중입니다."; SoriEngine.shared.mal(allim); return }
        if w.ochae > 30 {
            allim = "지금은 위성이 흐려 자리가 \(Int(w.ochae))미터쯤 어긋날 수 있습니다. 밖으로 나가 다시 담아 주십시오."
            SoriEngine.shared.mal(allim)
            return
        }
        allim = "주소를 알아보는 중입니다."
        Task {
            let juso = await Chatgi.juso(w.lat, w.lon)
            await MainActor.run {
                guard let juso = juso else {
                    allim = "주소를 받지 못했습니다. 통신을 확인하신 뒤 다시 눌러 주십시오."
                    SoriEngine.shared.mal(allim)
                    return
                }
                if jeul.damgi(Jangso(ireum: nm, juso: juso, lat: w.lat, lon: w.lon)) {
                    allim = "\(nm) — \(juso), 즐겨찾기에 담았습니다."
                    ireum = ""
                } else {
                    allim = "같은 이름이나 같은 자리가 이미 담겨 있습니다."
                }
                SoriEngine.shared.mal(allim)
            }
        }
    }
}
