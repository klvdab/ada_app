// 나눔 탭 — 앱 2.9.0 (빌드 260928-11), 이사장님 승인(가1·나1).
// 단추 셋: 나눔 마당, 걸음 나눔과 게시판, 길 부탁하기. 웹 길눈과 같은 자료 창고를 씁니다.
import SwiftUI
import UIKit

enum NanumHwamyeon: Hashable {
    case mulnanum
    case mulMok(String)
    case mulSseugi
    case georeum
    case nanumGeul
    case nanumSangse(String)
    case hamkke
    case butak
    case butakSseugi
    case butakMok
    case butakSangse(String)
}

final class NanumGil: ObservableObject {
    static let shared = NanumGil()
    @Published var path = NavigationPath()
    /// 목록에서 고른 것을 속 화면이 받아 씀
    var nanumGeul: [String: NanumGeul] = [:]
    var butak: [String: Butak] = [:]
}

struct NanumTab: View {
    @ObservedObject private var gil = NanumGil.shared
    var body: some View {
        NavigationStack(path: $gil.path) {
            NanumCheot()
                .navigationDestination(for: NanumHwamyeon.self) { h in
                    switch h {
                    case .mulnanum: MulnanumView()
                    case .mulMok(let j): MulMokView(jong: j)
                    case .mulSseugi: MulSseugiView()
                    case .georeum: GeoreumNanumView()
                    case .nanumGeul: NanumGeulView()
                    case .nanumSangse(let id): NanumSangseView(id: id)
                    case .hamkke: HamkkeView()
                    case .butak: ButakView()
                    case .butakSseugi: ButakSseugiView()
                    case .butakMok: ButakMokView()
                    case .butakSangse(let id): ButakSangseView(id: id)
                    }
                }
        }
    }
}

struct NanumCheot: View {
    @AccessibilityFocusState private var chojeom: Bool
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                NavigationLink(value: NanumHwamyeon.mulnanum) { Text("나눔 마당 — 쓰지 않는 물건 주고받기") }
                    .buttonStyle(KeunDanchu())
                    .accessibilityFocused($chojeom)
                NavigationLink(value: NanumHwamyeon.georeum) { Text("걸음 나눔과 게시판") }.buttonStyle(KeunDanchu())
                NavigationLink(value: NanumHwamyeon.butak) { Text("길 부탁하기 — 그려 주었으면 하는 길 남기기") }.buttonStyle(KeunDanchu())
            }
            .padding()
        }
        .toolbar(.hidden, for: .navigationBar)
        .onAppear { chojeom = false; DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { chojeom = true } }   // 2.12.1 매번 첫 줄로
    }
}

/// 알아 두실 것 — 펼치기 안에 한 줄씩
struct AraDul: View {
    let juldeul: [String]
    var body: some View {
        DisclosureGroup("알아 두실 것 펼치기") {
            VStack(alignment: .leading, spacing: 8) {
                ForEach(Array(juldeul.enumerated()), id: \.offset) { _, t in Text(t).font(.body) }
            }
        }
        .font(.title3)
    }
}

// MARK: 나눔 마당

struct MulnanumView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("쓰지 않는 물건을 주고받는 곳입니다.").font(.body)
                NavigationLink(value: NanumHwamyeon.mulMok("deurim")) { Text("드립니다 — 나눠 주실 물건 보기") }.buttonStyle(KeunDanchu())
                NavigationLink(value: NanumHwamyeon.mulMok("chatgi")) { Text("찾습니다 — 필요하신 분들 보기") }.buttonStyle(KeunDanchu())
                NavigationLink(value: NanumHwamyeon.mulSseugi) { Text("글 올리기") }.buttonStyle(KeunDanchu())
                AraDul(juldeul: [
                    "시각장애인과 자원봉사자가 함께 쓰는 곳입니다. 누구나 드리실 수 있고 누구나 찾으실 수 있습니다.",
                    "협회는 물건을 갖지 않습니다. 드리는 분과 찾는 분을 잇기만 합니다. 주고받는 일은 두 분이 직접 하십니다.",
                    "연락 받으실 방법은 이 마당에 그대로 드러납니다. 남에게 알려도 괜찮은 번호만 적어 주십시오.",
                    "글은 여든 날이 지나면 저절로 내려갑니다. 다 나누셨으면 글 아래 다 나눴습니다를 눌러 주십시오.",
                    "물건값을 주고받는 곳이 아닙니다. 파실 물건은 올리지 말아 주십시오."
                ])
            }
            .padding()
        }
        .sokHwamyeon("나눔 마당")
    }
}

struct MulMokView: View {
    let jong: String
    @State private var mok: [MulGeul]?
    @State private var mot = false
    @State private var naeril: MulGeul?
    private var ireum: String { jong == "deurim" ? "드립니다" : "찾습니다" }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let l = mok, !l.isEmpty {
                    Mokrok5(l) { g in
                        Button(g.julMal) { SoriEngine.shared.mal(g.jaseMal) }
                            .buttonStyle(KeunDanchu())
                            .accessibilityAction(named: g.jeonhwa != nil ? "전화 걸기" : "연락처 듣기") { yeonrak(g) }
                            .accessibilityAction(named: "다 나눴습니다 — 이 글 내리기") { naeril = g }
                            .contextMenu {
                                Button(g.jeonhwa != nil ? "전화 걸기" : "연락처 듣기") { yeonrak(g) }
                                Button("다 나눴습니다 — 이 글 내리기") { naeril = g }
                            }
                    }
                    Text("이름을 두드리시면 자세히 읽어 드립니다. 전화와 글 내리기는 이름에서 위아래로 쓸어 고르십시오.").font(.body)
                } else if mok != nil {
                    Text("\(ireum)에 아직 올라온 글이 없습니다. 첫 글을 올려 주시면 다음 분께서 바로 보십니다.").font(.title3)
                } else if mot {
                    Button("불러오지 못했습니다 — 다시 불러오기") { bulreogi() }.buttonStyle(KeunDanchu())
                } else {
                    Text("\(ireum)을 불러오고 있습니다.").font(.title3)
                }
            }
            .padding()
        }
        .sokHwamyeon(ireum)
        .task { if mok == nil { bulreogi() } }
        .confirmationDialog("이 글을 내릴까요?", isPresented: Binding(get: { naeril != nil }, set: { if !$0 { naeril = nil } }), titleVisibility: .visible) {
            Button("내리기", role: .destructive) {
                guard let g = naeril else { return }
                naeril = nil
                Task {
                    _ = await Mulnanum.naerigi(g.id)
                    await MainActor.run { SoriEngine.shared.mal("글을 내렸습니다."); mok = nil; bulreogi() }
                }
            }
            Button("그만두기", role: .cancel) { naeril = nil }
        }
    }

    private func yeonrak(_ g: MulGeul) {
        if let b = g.jeonhwa, let u = URL(string: "tel:" + b) { UIApplication.shared.open(u) }
        else { SoriEngine.shared.mal("연락처는 \(g.yeon)입니다.") }
    }

    private func bulreogi() {
        mot = false
        Task {
            let r = await Mulnanum.mok(jong)
            await MainActor.run {
                if let r = r {
                    mok = r
                    if !r.isEmpty { SoriEngine.shared.mal("\(ireum) \(r.count)건입니다.", .jeongbo) }
                } else {
                    mot = true
                    SoriEngine.shared.mal("불러오지 못했습니다. 다시 해 보십시오.")
                }
            }
        }
    }
}

struct MulSseugiView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var jong = ""
    @State private var mul = ""
    @State private var mal = ""
    @State private var got = ""
    @State private var nugu = GeoreumNanum.byeol
    @State private var yeon = ""
    @State private var olineun = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Button(jong == "deurim" ? "드립니다 — 골랐음" : "드립니다") { goreugi("deurim") }
                    .buttonStyle(KeunDanchu())
                    .accessibilityAddTraits(jong == "deurim" ? .isSelected : [])
                Button(jong == "chatgi" ? "찾습니다 — 골랐음" : "찾습니다") { goreugi("chatgi") }
                    .buttonStyle(KeunDanchu())
                    .accessibilityAddTraits(jong == "chatgi" ? .isSelected : [])
                kan("무엇입니까", $mul)
                kan("한마디", $mal)
                kan("어느 지역", $got)
                kan("이름 또는 별명", $nugu)
                kan("연락 받으실 방법", $yeon)
                Button(olineun ? "올리는 중입니다" : "올리기") { olligi() }.buttonStyle(KeunDanchu())
                Button("그만두기") { SoriEngine.shared.mal("그만두었습니다."); dismiss() }.buttonStyle(KeunDanchu())
            }
            .padding()
        }
        .sokHwamyeon("글 올리기")
    }

    private func kan(_ nm: String, _ b: Binding<String>) -> some View {
        TextField(nm, text: b).textFieldStyle(.roundedBorder).font(.title3)
    }

    private func goreugi(_ j: String) {
        jong = j
        SoriEngine.shared.mal(j == "deurim" ? "드립니다로 정했습니다. 무엇인지 적어 주십시오." : "찾습니다로 정했습니다. 무엇인지 적어 주십시오.")
    }

    private func olligi() {
        guard !olineun else { return }
        guard !jong.isEmpty else { SoriEngine.shared.mal("드립니다인지 찾습니다인지 골라 주십시오."); return }
        let m = mul.trimmingCharacters(in: .whitespaces), y = yeon.trimmingCharacters(in: .whitespaces)
        guard !m.isEmpty else { SoriEngine.shared.mal("무엇인지 적어 주십시오."); return }
        guard !y.isEmpty else { SoriEngine.shared.mal("연락 받으실 방법을 적어 주십시오."); return }
        olineun = true
        Task {
            let e = await Mulnanum.olligi(jong: jong, mul: m, mal: mal.trimmingCharacters(in: .whitespaces), got: got.trimmingCharacters(in: .whitespaces),
                                          nugu: nugu.trimmingCharacters(in: .whitespaces), yeon: y)
            await MainActor.run {
                olineun = false
                if let e = e { SoriEngine.shared.mal(e); return }
                SoriEngine.shared.mal("올렸습니다. 고맙습니다.")
                dismiss()
            }
        }
    }
}

// MARK: 걸음 나눔과 게시판

struct GeoreumNanumView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("읽고 적는 자리입니다.").font(.body)
                NavigationLink(value: NanumHwamyeon.nanumGeul) { Text("걸음 나눔 — 걸어 보고 한마디 적기") }.buttonStyle(KeunDanchu())
                Text("시각장애인과 자원봉사자가 같은 자리에 적습니다. 별명만 적으시면 되고 실명은 받지 않습니다.").font(.body)
                NavigationLink(value: NanumHwamyeon.hamkke) { Text("함께하기 — 자원봉사자와 제도를 알아보기") }.buttonStyle(KeunDanchu())
                Text("자원봉사 요령과 봉사시간, 관련 제도와 법, 후원과 광고 원칙까지 한자리에 담았습니다.").font(.body)
            }
            .padding()
        }
        .sokHwamyeon("걸음 나눔과 게시판")
    }
}

struct NanumGeulView: View {
    @State private var mok: [NanumGeul] = []
    @State private var modu = 0
    @State private var bu = 0
    @State private var mot = false
    @State private var badeum = false
    @State private var byeol = GeoreumNanum.byeol
    @State private var geul = ""
    @State private var jam = ""
    @State private var olineun = false
    @AccessibilityFocusState private var cheotJul: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                DisclosureGroup("한마디 적기 펼치기") {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("걸으신 이야기, 그려 주신 이야기, 이 프로그램에 바라시는 것을 자유롭게 적는 자리입니다. 전화번호나 주소는 적지 마십시오.").font(.body)
                        TextField("별명 (본명을 적지 않으셔도 됩니다)", text: $byeol).textFieldStyle(.roundedBorder).font(.title3)
                        TextField("하실 말씀", text: $geul, axis: .vertical).textFieldStyle(.roundedBorder).font(.title3).lineLimit(3...8)
                        TextField("지울 때 쓸 네 자리 숫자", text: $jam).textFieldStyle(.roundedBorder).font(.title3).keyboardType(.numberPad)
                        Text("이 네 자리를 기억해 두시면 나중에 그 글을 가리실 수 있습니다. 비밀번호가 아니니 쉬운 숫자로 하셔도 됩니다.").font(.footnote)
                        Button(olineun ? "올리는 중입니다" : "올리기") { olligi() }.buttonStyle(KeunDanchu())
                    }
                }
                .font(.title3)
                if mok.isEmpty {
                    Text(mot ? "글을 불러오지 못했습니다." : (badeum ? "적힌 글이 없습니다." : "불러오는 중입니다.")).font(.title3)
                } else {
                    ForEach(Array(mok.enumerated()), id: \.element.id) { i, g in
                        let jul = NavigationLink(value: NanumHwamyeon.nanumSangse(g.id)) {
                            Text(g.meori + ". " + g.geul + (g.dat.isEmpty ? "" : " · 댓글 \(g.dat.count)개"))
                        }
                        .buttonStyle(KeunDanchu())
                        if i == 0 { jul.accessibilityFocused($cheotJul) } else { jul }
                    }
                    if bu + 5 < modu {
                        Button("더 보기") { bu += 5; bulreogi() }.buttonStyle(KeunDanchu())
                    }
                    if bu > 0 {
                        Button("이전 보기") { bu = max(0, bu - 5); bulreogi() }.buttonStyle(KeunDanchu())
                    }
                }
            }
            .padding()
        }
        .sokHwamyeon("걸음 나눔")
        .onAppear { bulreogi() }
    }

    private func bulreogi() {
        mot = false
        Task {
            let r = await GeoreumNanum.mok(bu)
            await MainActor.run {
                badeum = true
                guard let r = r else { mot = true; SoriEngine.shared.mal("글을 불러오지 못했습니다."); return }
                mok = r.0
                modu = r.1
                for g in r.0 { NanumGil.shared.nanumGeul[g.id] = g }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { cheotJul = true }
            }
        }
    }

    private func olligi() {
        guard !olineun else { return }
        let b = byeol.trimmingCharacters(in: .whitespaces), g = geul.trimmingCharacters(in: .whitespaces)
        let j = jam.filter { $0.isNumber }
        guard !b.isEmpty else { SoriEngine.shared.mal("별명을 적어 주십시오."); return }
        guard !g.isEmpty else { SoriEngine.shared.mal("하실 말씀을 적어 주십시오."); return }
        guard j.count == 4 else { SoriEngine.shared.mal("지울 때 쓸 네 자리 숫자를 적어 주십시오."); return }
        olineun = true
        SoriEngine.shared.mal("올리는 중입니다.", .jeongbo)
        Task {
            let e = await GeoreumNanum.sseugi(byeol: b, geul: g, jam: j)
            await MainActor.run {
                olineun = false
                if let e = e { SoriEngine.shared.mal(e); return }
                GeoreumNanum.byeol = b
                geul = ""
                SoriEngine.shared.mal("올렸습니다. 고맙습니다.")
                bu = 0
                bulreogi()
            }
        }
    }
}

struct NanumSangseView: View {
    let id: String
    @Environment(\.dismiss) private var dismiss
    @State private var byeol = GeoreumNanum.byeol
    @State private var mal = ""
    @State private var jam = ""
    @State private var garimJam = ""
    @State private var olineun = false
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let g = NanumGil.shared.nanumGeul[id] {
                    Text(g.meori).font(.footnote)
                    Text(g.geul).font(.title3).accessibilityFocused($chojeom)
                    if g.dat.isEmpty {
                        Text("댓글이 없습니다.").font(.body)
                    } else {
                        Text("댓글 \(g.dat.count)개").font(.title3.weight(.bold)).accessibilityAddTraits(.isHeader)
                        ForEach(Array(g.dat.enumerated()), id: \.offset) { _, d in
                            Text("\(d.nugu == "길눈" ? "길눈" : d.byeol + " · " + d.nugu) · \(d.nal) — \(d.geul)").font(.body)
                        }
                    }
                    DisclosureGroup("댓글 달기 펼치기") {
                        VStack(alignment: .leading, spacing: 10) {
                            TextField("댓글 다실 분 별명", text: $byeol).textFieldStyle(.roundedBorder).font(.title3)
                            TextField("댓글 내용", text: $mal, axis: .vertical).textFieldStyle(.roundedBorder).font(.title3).lineLimit(2...6)
                            TextField("댓글 지울 때 쓸 네 자리 숫자", text: $jam).textFieldStyle(.roundedBorder).font(.title3).keyboardType(.numberPad)
                            Button(olineun ? "올리는 중입니다" : "이 댓글 올리기") { datOlligi() }.buttonStyle(KeunDanchu())
                        }
                    }
                    .font(.title3)
                    DisclosureGroup("이 글 가리기 펼치기 — 네 자리 숫자") {
                        VStack(alignment: .leading, spacing: 10) {
                            TextField("적으실 때 넣으신 네 자리 숫자", text: $garimJam).textFieldStyle(.roundedBorder).font(.title3).keyboardType(.numberPad)
                            Button("이 글 가리기") { garigi() }.buttonStyle(KeunDanchu())
                        }
                    }
                    .font(.title3)
                } else {
                    Text("글을 찾지 못했습니다.").font(.title3)
                }
            }
            .padding()
        }
        .sokHwamyeon("걸음 나눔 글")
        .onAppear { chojeom = false; DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { chojeom = true } }   // 2.12.1 매번 첫 줄로
    }

    private func datOlligi() {
        guard !olineun else { return }
        let b = byeol.trimmingCharacters(in: .whitespaces), m = mal.trimmingCharacters(in: .whitespaces)
        let j = jam.filter { $0.isNumber }
        guard !b.isEmpty else { SoriEngine.shared.mal("별명을 적어 주십시오."); return }
        guard !m.isEmpty else { SoriEngine.shared.mal("하실 말씀을 적어 주십시오."); return }
        guard j.count == 4 else { SoriEngine.shared.mal("지울 때 쓸 네 자리 숫자를 적어 주십시오."); return }
        olineun = true
        Task {
            let e = await GeoreumNanum.dat(id: id, byeol: b, geul: m, jam: j)
            await MainActor.run {
                olineun = false
                if let e = e { SoriEngine.shared.mal(e); return }
                GeoreumNanum.byeol = b
                SoriEngine.shared.mal("댓글을 올렸습니다. 고맙습니다.")
                dismiss()
            }
        }
    }

    private func garigi() {
        let j = garimJam.filter { $0.isNumber }
        guard !j.isEmpty else { SoriEngine.shared.mal("적으실 때 넣으신 네 자리 숫자를 적어 주십시오."); return }
        Task {
            let r = await GeoreumNanum.garigi(id: id, jam: j)
            await MainActor.run {
                if !r.1.isEmpty { SoriEngine.shared.mal(r.1) }
                if r.0 { dismiss() }
            }
        }
    }
}

/// 함께하기 — 웹 길눈 hamkkehagi.html 의 글을 그대로(마당 제목을 두드리면 펼쳐짐)
struct HamkkeView: View {
    @State private var daIlgeun = UserDefaults.standard.string(forKey: "gn.hamkkeDone") ?? ""

    static let madang: [(String, [String])] = [
        ("하나. 왜 이 일을 하는가", [
            "세상에는 세상을 보고 살 수 없는 사람이 있습니다. 그분들에게 필요한 것은 동정이 아니라 눈입니다.",
            "길눈(시각장애인 점지도 서비스)은 자원봉사자가 길을 걸어 점으로 이어 둔 점지도를 시각장애인이 따라 걷게 하는 일입니다.",
            "점지도는 어느 한 사람을 위한 것이 아닙니다. 국민 모두가 저마다 또 하나의 눈이 되어 주는 일입니다. 한 사람이 자기 동네 한 길을 걸어 그려 두면, 그 길은 그 동네 시각장애인 모두의 길이 됩니다. 한 번 그린 길은 사라지지 않고 남습니다. 그것이 이 일의 힘입니다.",
            "사단법인 한국시각장애인현장영상해설협회는 2015년부터 현장영상해설사를 길러 왔고, 전국 마흔세 곳 지방자치단체에 현장영상해설 조례를 세웠습니다. 점지도는 그 위에 올리는 다음 걸음입니다."
        ]),
        ("둘. 무엇을 하는 일인가", [
            "#길 그리기",
            "앱을 켜고 평소대로 걸으면 걸음과 방향과 자리가 담깁니다. 걷다가 계단이나 횡단보도, 턱을 만나면 그 자리에서 단추를 눌러 남깁니다. 다 걸으면 어디에서 어디까지인지 적어 올립니다.",
            "#도움 연결",
            "길을 걷던 분이 막히면 도움 청하기를 누릅니다. 그러면 카메라가 켜지고, 받은 사람이 그 화면을 보며 말로 짚어 드립니다. 몇십 초면 끝나는 일이 그분에게는 그날의 길을 열어 줍니다.",
            "#내 길",
            "시각장애인 본인도 자기 집 앞 길을 손수 그려 둘 수 있습니다. 자기만 아는 것을 자기 말로 남기는 자리입니다."
        ]),
        ("셋. 시각장애인에게 말하는 법 — 가장 중요한 대목", [
            "이것만 익히셔도 절반은 하신 것입니다. 협회가 십 년 넘게 가르쳐 온 현장영상해설의 기본입니다.",
            "이것, 저것, 여기, 저기라고 하지 않습니다. 보이지 않는 사람에게는 아무 말도 아닙니다.",
            "방향은 시계 방향으로 말합니다. 그분이 보고 계신 쪽이 열두 시입니다. 오른쪽 뒤는 네다섯 시, 왼쪽은 아홉 시입니다.",
            "거리는 눈대중으로 말하지 말고 걸음이나 미터로 말합니다. 조금, 좀 더 가서는 거리가 아닙니다.",
            "전체를 먼저, 그다음 가운데, 그다음 둘레 차례로 말합니다. 눈은 한꺼번에 보지만 귀는 차례로 듣습니다.",
            "만질 수 있는 것은 만질 수 있다고 알려 드립니다. 손잡이, 난간, 점자블록, 벽. 이것이 눈을 대신합니다.",
            "위험한 것을 먼저 말합니다. 앞에 뭐가 예쁘다는 말보다 왼쪽 두 걸음 앞에 자전거가 세워져 있습니다가 먼저입니다.",
            "모르면 모른다고 합니다. 짐작으로 말한 한마디가 사람을 다치게 합니다.",
            "안내하며 걸을 때는 그분의 팔을 끌지 않습니다. 팔꿈치 위쪽을 잡으시게 하고 반걸음 앞에서 걷습니다."
        ]),
        ("넷. 길 그리기 요령", [
            "실제로 걸은 길만 올립니다. 차나 자전거로 지나간 것은 길이 되지 않습니다.",
            "사람이 실제로 걷는 자리로 걷습니다. 지름길이나 위험한 자리로 걸으면 그대로 남습니다.",
            "표시는 그 자리에 닿았을 때 누릅니다. 지나고 나서 누르면 몇 걸음씩 어긋납니다.",
            "커피를 사거나 화장실에 가실 때는 잠깐 멈춤을 누릅니다. 그동안은 담기지 않습니다.",
            "같은 길을 여러 사람이 걸을수록 그 길은 정확해집니다. 이미 있는 길이라고 그리지 않으실 까닭이 없습니다.",
            "보폭은 처음 한 번만 재 두시면 됩니다. 시각장애인은 걸음이 짧으므로, 거리는 미터로 담고 걷는 분의 보폭으로 다시 걸음 수를 냅니다.",
            "걸으면서 화면을 보지 마십시오. 봉사자가 다치면 아무것도 남지 않습니다."
        ]),
        ("다섯. 영상으로 도울 때", [
            "먼저 이름을 밝히고, 지금 무엇이 보이는지 한 문장으로 말합니다.",
            "화면이 흔들리거나 어두우면 그렇다고 말씀드리고 카메라를 어느 쪽으로 돌려 달라 청합니다.",
            "길을 건너는 순간에는 말을 아낍니다. 소리를 들으셔야 합니다.",
            "차가 오거나 위험하면 곧바로 멈추시라고 말합니다. 설명은 그다음입니다.",
            "화면에 남의 얼굴이나 서류가 보여도 그것을 말하거나 남기지 않습니다.",
            "영상은 저장하지 않습니다. 끝나면 사라집니다."
        ]),
        ("여섯. 자원봉사자 운영 — 어떻게 굴러가는가", [
            "처음 한 번만 등록하시면 됩니다. 기기가 기억하므로 다음부터는 아무것도 묻지 않습니다. 폰을 바꾸셨을 때만 다시 하십니다.",
            "도움 연결은 당번으로 돌아갑니다. 부름이 오면 그 시각 당번에게 먼저 울리고, 못 받으면 대기 중인 분들께 넓히고, 그래도 없으면 문자와 협회로 이어집니다.",
            "이용자가 미리 등록해 둔 가족이 있으면 가족에게 먼저 울립니다. 가족이 없거나 비장애인 가족이 없는 분은 해설사와 자봉이 받습니다.",
            "받으실 수 없는 때는 대기를 꺼 두시면 됩니다. 못 받는 것이 잘못이 아니라, 못 받을 때 켜 두는 것이 문제입니다.",
            "몇몇 분께 몰리지 않게 최근에 많이 받으신 분은 뒤로 미룹니다.",
            "실적은 처음부터 쌓입니다. 자원봉사 인정이 필요 없다 하신 분께도 쌓아 두었다가, 뒷날 필요해지시면 그때부터 꺼내 쓰실 수 있습니다."
        ]),
        ("일곱. 봉사시간과 자원봉사증", [
            "봉사한 것이 기록으로 남아야 오래갑니다. 두 갈래로 준비하고 있습니다.",
            "#협회 증서",
            "협회가 내는 봉사 확인서와 수료증입니다. 위조할 수 없는 디지털 증서(국제표준 오픈배지) 방식으로 내어, 어느 나라 어디에서든 진짜인지 확인할 수 있게 합니다.",
            "#국가 봉사시간(1365)",
            "협회가 1365 자원봉사포털에 활동처로 등록하면 봉사시간이 국가 실적으로 인정됩니다. 지금 등록을 준비하고 있습니다. 등록이 끝나면 이 자리에 방법을 적어 두겠습니다.",
            "정직하게 짚어 둡니다. 국가 봉사시간은 활동이 끝난 뒤 정해진 기간 안에 올리는 것이 원칙이라, 아주 오래된 실적은 소급이 어려울 수 있습니다.",
            "#시간은 어떻게 세는가",
            "길 그리기는 실제로 걸은 시간으로 셉니다. 잠깐 멈춤 동안은 세지 않습니다.",
            "도움 연결은 이어진 시간으로 셉니다.",
            "같은 자리를 여러 번 그리는 것은 인정합니다. 겹칠수록 길이 정확해지기 때문입니다. 다만 같은 사람이 같은 날 같은 자리를 되풀이한 것은 한 번으로 봅니다.",
            "실제로 걷지 않은 자취, 차로 지나간 자취는 인정하지 않습니다. 이 기준이 허술하면 지도도 믿을 수 없게 됩니다."
        ]),
        ("여덟. 관련 제도와 법", [
            "자원봉사활동 기본법 — 자원봉사는 대가를 바라지 않는 활동이며, 국가와 지방자치단체가 이를 지원하도록 정하고 있습니다. 봉사실적 인정과 자원봉사센터가 여기에서 나옵니다.",
            "장애인차별금지법과 장애인복지법 — 시각장애인의 이동과 정보 접근은 베푸는 것이 아니라 보장해야 하는 권리입니다.",
            "현장영상해설 조례 — 전국 마흔세 곳 지방자치단체가 조례를 두고 있습니다. 이 조례가 지자체 예산의 근거가 됩니다.",
            "위치정보의 보호 및 이용 등에 관한 법률 — 남의 위치를 모아 안내에 쓰는 일은 신고 대상이 될 수 있습니다. 비영리라고 예외가 아닙니다. 협회가 확인해 갖추고 있습니다.",
            "개인정보 보호법 — 협회는 주민등록번호와 상세주소, 건강정보를 받지 않습니다. 걸어 만든 자취에는 이름이 붙지 않고 번호만 붙습니다.",
            "시각장애인이 안내받은 이력은 서버에 남기지 않습니다. 어느 병원에 언제 갔는지가 남으면 안 되기 때문입니다."
        ]),
        ("아홉. 후원과 광고에 대한 우리 원칙", [
            "이 일이 자리를 잡으면 후원도 광고도 붙습니다. 그때 흔들리지 않도록 원칙을 먼저 못박아 둡니다.",
            "시각장애인이 길 안내를 받는 동안에는 어떤 광고도 소리로 내보내지 않습니다. 주의가 흩어지면 다치는 일입니다.",
            "구간 후원은 그 길을 고르실 때 한 번만 읽어 드립니다. 걷는 도중에는 읽지 않습니다.",
            "봉사자가 걸어 만든 자취를 후원이 붙은 길에 쓰거나 밖에 내줄 때는 미리 그 뜻을 여쭙고, 안 된다 하시면 쓰지 않습니다.",
            "기부금과 광고 수입은 따로 회계를 갈라 담고, 어디에 얼마를 썼는지 공개합니다.",
            "후원금은 길을 늘리는 데 씁니다. 어느 구간에 얼마가 쓰였는지 숫자로 보여 드립니다."
        ]),
        ("열. 함께하시려면", [
            "길 그리기는 지금 바로 하실 수 있습니다. 첫 화면 자원봉사자단에서 길 그리기 시작을 누르십시오.",
            "도움 연결로 받으시려면 대기 화면을 홈 화면에 얹어 두십시오. 화면을 닫아 두셔도 부름이 옵니다.",
            "이 안내를 끝까지 읽으신 것을 아래 단추로 남겨 두시면, 수료와 봉사증 자리가 열릴 때 그대로 이어집니다."
        ])
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("자원봉사 교육과 제도 안내입니다. 마당 제목을 두드리시면 펼쳐집니다.").font(.body)
                ForEach(Array(HamkkeView.madang.enumerated()), id: \.offset) { _, m in
                    DisclosureGroup(m.0) {
                        VStack(alignment: .leading, spacing: 8) {
                            ForEach(Array(m.1.enumerated()), id: \.offset) { _, t in
                                if t.hasPrefix("#") {
                                    Text(String(t.dropFirst())).font(.title3.weight(.bold)).accessibilityAddTraits(.isHeader)
                                } else {
                                    Text(t).font(.body)
                                }
                            }
                        }
                    }
                    .font(.title3)
                }
                Button("이 안내를 다 읽었습니다") {
                    let c = Calendar.current.dateComponents([.year, .month, .day], from: Date())
                    daIlgeun = "\(c.year ?? 0)년 \(c.month ?? 0)월 \(c.day ?? 0)일"
                    UserDefaults.standard.set(daIlgeun, forKey: "gn.hamkkeDone")
                    Girok.shared.namgi("hamkke_done", [:])
                    SoriEngine.shared.mal("다 읽으신 것으로 남겼습니다.")
                }
                .buttonStyle(KeunDanchu())
                if !daIlgeun.isEmpty { Text("\(daIlgeun)에 다 읽으신 것으로 남아 있습니다.").font(.body) }
            }
            .padding()
        }
        .sokHwamyeon("함께하기")
    }
}

// MARK: 길 부탁하기

struct ButakView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("그려 주었으면 하는 길을 남겨 두시면 길눈님이 걸어 드립니다.").font(.body)
                NavigationLink(value: NanumHwamyeon.butakSseugi) { Text("새로 부탁하기") }.buttonStyle(KeunDanchu())
                NavigationLink(value: NanumHwamyeon.butakMok) { Text("부탁해 둔 길 보기") }.buttonStyle(KeunDanchu())
                AraDul(juldeul: [
                    "부탁하신 길은 자원봉사자의 오늘 걸을 길 맨 앞에 놓입니다. 사람이 실제로 기다리는 길이기 때문입니다.",
                    "다 그려지면 알려 드립니다. 더 필요 없어지면 부탁을 내리셔도 됩니다."
                ])
            }
            .padding()
        }
        .sokHwamyeon("길 부탁하기")
    }
}

struct ButakSseugiView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var chulMal = ""
    @State private var mokMal = ""
    @State private var chul: Jangso?
    @State private var mok: Jangso?
    @State private var hubo: [Jangso] = []
    @State private var huboChul = true
    @State private var ttae = ""
    @State private var mal = ""
    @State private var nugu = GeoreumNanum.byeol
    @State private var olineun = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                TextField("어디서 — 적고 엔터", text: $chulMal)
                    .textFieldStyle(.roundedBorder).font(.title3).submitLabel(.search)
                    .onSubmit { chatgi(chulMal, chul: true) }
                Button("지금 내 자리를 출발지로") {
                    guard let w = WichiEngine.shared.jigeum else { SoriEngine.shared.mal("지금 자리를 잡는 중입니다. 잠시 뒤에 다시 눌러 주십시오."); return }
                    chul = Jangso(ireum: "지금 내 자리", juso: "", lat: w.lat, lon: w.lon)
                    chulMal = "지금 내 자리"
                    SoriEngine.shared.mal("출발지를 지금 내 자리로 정했습니다. 이제 어디까지인지 적어 주십시오.")
                }
                .buttonStyle(KeunDanchu())
                TextField("어디까지 — 적고 엔터", text: $mokMal)
                    .textFieldStyle(.roundedBorder).font(.title3).submitLabel(.search)
                    .onSubmit { chatgi(mokMal, chul: false) }
                if !hubo.isEmpty {
                    Text(huboChul ? "출발지 — 비슷한 곳" : "목적지 — 비슷한 곳").font(.title3.weight(.bold)).accessibilityAddTraits(.isHeader)
                    Mokrok5(hubo) { j in
                        Button(j.ireum + (j.juso.isEmpty ? "" : " — " + j.juso)) { goreugi(j) }.buttonStyle(KeunDanchu())
                    }
                    .id(hubo.first?.id ?? "")
                }
                TextField("언제쯤 가셔야 합니까", text: $ttae).textFieldStyle(.roundedBorder).font(.title3)
                TextField("한마디", text: $mal).textFieldStyle(.roundedBorder).font(.title3)
                TextField("별명 — 실명은 적지 마십시오. 비워 두셔도 됩니다", text: $nugu).textFieldStyle(.roundedBorder).font(.title3)
                Button(olineun ? "올리는 중입니다" : "부탁 올리기") { olligi() }.buttonStyle(KeunDanchu())
                Button("그만두기") { SoriEngine.shared.mal("그만두었습니다."); dismiss() }.buttonStyle(KeunDanchu())
            }
            .padding()
        }
        .sokHwamyeon("새로 부탁하기")
    }

    private func chatgi(_ q0: String, chul c: Bool) {
        let q = q0.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { SoriEngine.shared.mal("이름을 적어 주십시오."); return }
        SoriEngine.shared.mal("\(q)\(MalHagi.eul(q)) 찾고 있습니다.", .jeongbo)
        Task {
            let r = await Chatgi.jangso(q)
            await MainActor.run {
                guard let r = r else { SoriEngine.shared.mal("찾는 중에 막혔습니다."); return }
                if r.isEmpty { SoriEngine.shared.mal("그런 이름의 곳을 찾지 못했습니다. 달리 적어 보십시오."); return }
                huboChul = c
                hubo = r
                SoriEngine.shared.mal("비슷한 곳 \(r.count)곳입니다. 고르실 곳을 두드리십시오.", .jeongbo)
            }
        }
    }

    private func goreugi(_ j: Jangso) {
        if huboChul {
            chul = j
            chulMal = j.ireum
            SoriEngine.shared.mal("출발지를 \(j.ireum)\(MalHagi.ro(j.ireum)) 정했습니다. 이제 어디까지인지 적어 주십시오.")
        } else {
            mok = j
            mokMal = j.ireum
            SoriEngine.shared.mal("목적지를 \(j.ireum)\(MalHagi.ro(j.ireum)) 정했습니다. 언제쯤 가셔야 하는지 적어 주십시오.")
        }
        hubo = []
    }

    private func olligi() {
        guard !olineun else { return }
        let s = chulMal.trimmingCharacters(in: .whitespaces), m = mokMal.trimmingCharacters(in: .whitespaces)
        guard !s.isEmpty else { SoriEngine.shared.mal("어디서 출발하시는지 적어 주십시오."); return }
        guard !m.isEmpty else { SoriEngine.shared.mal("어디까지 가시는지 적어 주십시오."); return }
        olineun = true
        let c = (chul?.ireum == s) ? chul : nil
        let mk = (mok?.ireum == m) ? mok : nil
        Task {
            let e = await GilButak.olligi(chul: c, chulMal: s, mok: mk, mokMal: m, ttae: ttae.trimmingCharacters(in: .whitespaces),
                                         mal: mal.trimmingCharacters(in: .whitespaces), nugu: nugu.trimmingCharacters(in: .whitespaces))
            await MainActor.run {
                olineun = false
                if let e = e { SoriEngine.shared.mal(e); return }
                SoriEngine.shared.mal("부탁을 올렸습니다. 자원봉사자의 오늘 걸을 길 맨 앞에 놓입니다. 다 그려지면 알려 드리겠습니다.")
                dismiss()
            }
        }
    }
}

struct ButakMokView: View {
    @State private var mok: [Butak]?
    @State private var mot = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let l = mok, !l.isEmpty {
                    Mokrok5(l) { b in
                        NavigationLink(value: NanumHwamyeon.butakSangse(b.id)) { Text(b.julMal) }.buttonStyle(KeunDanchu())
                    }
                } else if mok != nil {
                    Text("아직 부탁해 둔 길이 없습니다. 첫 부탁을 남겨 주시면 길눈님이 걸어 드립니다.").font(.title3)
                } else if mot {
                    Button("불러오지 못했습니다 — 다시 불러오기") { bulreogi() }.buttonStyle(KeunDanchu())
                } else {
                    Text("부탁해 둔 길을 불러오고 있습니다.").font(.title3)
                }
            }
            .padding()
        }
        .sokHwamyeon("부탁해 둔 길")
        .onAppear { bulreogi() }
    }

    private func bulreogi() {
        mot = false
        Task {
            let r = await GilButak.mok()
            await MainActor.run {
                if let r = r {
                    for b in r { NanumGil.shared.butak[b.id] = b }
                    mok = r
                } else {
                    mot = true
                    SoriEngine.shared.mal("불러오지 못했습니다.")
                }
            }
        }
    }
}

struct ButakSangseView: View {
    let id: String
    @Environment(\.dismiss) private var dismiss
    @State private var datMal = ""
    @State private var datNugu = GeoreumNanum.byeol
    @State private var jiulDat: ButakDat?
    @State private var naerilGeot = false
    @AccessibilityFocusState private var chojeom: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let b = NanumGil.shared.butak[id] {
                    Text(b.jaseMal).font(.title3).accessibilityFocused($chojeom)
                    if b.doen && !b.gil.isEmpty {
                        // 2.10.0 그려진 점지도를 따라 걷기
                        Button("그려졌습니다 — 이 길로 걷기, 점지도 따라 걷기") {
                            JeomEngine.shared.bulleoGeotgi(b.gil, dwit: false, mok: b.mlat != 0 ? Jangso(ireum: b.min, juso: "", lat: b.mlat, lon: b.mlon) : nil)
                        }
                        .buttonStyle(KeunDanchu())
                    }
                    if b.doen && b.mlat != 0 && b.mlon != 0 {
                        Button("그곳까지 안내 — 위성으로 방향 따라") {
                            gotEuroGagi(Jangso(ireum: b.min, juso: "", lat: b.mlat, lon: b.mlon))
                        }
                        .buttonStyle(KeunDanchu())
                    }
                    ForEach(b.daetgeul, id: \.id) { d in
                        Button("댓글 : \(d.mal)" + (d.nugu.isEmpty ? "" : " — \(d.nugu)")) { SoriEngine.shared.mal(d.mal) }
                            .buttonStyle(KeunDanchu())
                            .accessibilityAction(named: "이 댓글 지우기") { jiulDat = d }
                            .contextMenu { Button("이 댓글 지우기") { jiulDat = d } }
                    }
                    DisclosureGroup("댓글 달기 펼치기") {
                        VStack(alignment: .leading, spacing: 10) {
                            TextField("한마디", text: $datMal).textFieldStyle(.roundedBorder).font(.title3)
                            TextField("별명 — 실명은 적지 마십시오. 안 적으셔도 됩니다", text: $datNugu).textFieldStyle(.roundedBorder).font(.title3)
                            Button("댓글 올리기") {
                                let m = datMal.trimmingCharacters(in: .whitespaces)
                                guard !m.isEmpty else { SoriEngine.shared.mal("한마디를 적어 주십시오."); return }
                                hagi("daet", [("mal", m), ("nugu", datNugu.trimmingCharacters(in: .whitespaces))], "댓글을 달았습니다.")
                            }
                            .buttonStyle(KeunDanchu())
                        }
                    }
                    .font(.title3)
                    if !b.doen {
                        Button("다 그렸습니다 표시하기") { hagi("doen", [], "다 그려진 것으로 표시했습니다.") }.buttonStyle(KeunDanchu())
                    }
                    Button("이 부탁 내리기") { naerilGeot = true }.buttonStyle(KeunDanchu())
                } else {
                    Text("부탁을 찾지 못했습니다.").font(.title3)
                }
            }
            .padding()
        }
        .sokHwamyeon("부탁해 둔 길")
        .onAppear { chojeom = false; DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { chojeom = true } }   // 2.12.1 매번 첫 줄로
        .confirmationDialog("이 댓글을 지울까요?", isPresented: Binding(get: { jiulDat != nil }, set: { if !$0 { jiulDat = nil } }), titleVisibility: .visible) {
            Button("지우기", role: .destructive) {
                if let d = jiulDat { hagi("daetjiugi", [("did", d.id)], "댓글을 지웠습니다.") }
                jiulDat = nil
            }
            Button("그만두기", role: .cancel) { jiulDat = nil }
        }
        .confirmationDialog("이 부탁을 내릴까요?", isPresented: $naerilGeot, titleVisibility: .visible) {
            Button("내리기", role: .destructive) { hagi("gam", [], "부탁을 내렸습니다.") }
            Button("그만두기", role: .cancel) {}
        }
    }

    private func hagi(_ a: String, _ deo: [(String, String)], _ mal: String) {
        Task {
            let ok = await GilButak.hagi(a, id, deo)
            await MainActor.run {
                SoriEngine.shared.mal(ok ? mal : "하지 못했습니다. 통신을 확인해 주십시오.")
                if ok { dismiss() }
            }
        }
    }
}
